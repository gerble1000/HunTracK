#!/usr/bin/env node
/**
 * Puts a freshly uploaded build in front of testers.
 *
 * `altool --upload-app` only delivers the binary to App Store Connect. External
 * testers then see nothing until the build is assigned to their beta group and
 * submitted for Beta App Review. Internal group access must be verified too;
 * automatic distribution is a per-group setting, not a universal guarantee.
 *
 * No third-party dependencies: the ES256 JWT is signed with node:crypto and
 * the REST calls use the built-in fetch (Node 18+).
 *
 * Diagnostics are emitted as workflow annotations (`::notice::` / `::error::`)
 * rather than plain logs: this repository's job logs are not readable without
 * admin rights, while check-run annotations are.
 *
 * App Store Connect also rejects some query parameters per endpoint (the
 * `/v1/apps/{id}/builds` relationship refuses `sort`), so each listing tries the
 * documented forms in order instead of trusting any single one.
 *
 * Required environment:
 *   ASC_KEY_ID     App Store Connect API key ID
 *   ASC_ISSUER_ID  App Store Connect issuer ID
 *   ASC_KEY_PATH   path to AuthKey_<ASC_KEY_ID>.p8
 *   BUNDLE_ID      app bundle identifier, e.g. uk.hunttrack.app
 *   BUILD_NUMBER   CFBundleVersion of the build just uploaded
 */

import crypto from 'node:crypto';
import fs from 'node:fs';
import { pathToFileURL } from 'node:url';

const API_BASE = 'https://api.appstoreconnect.apple.com';
const PROCESSING_TIMEOUT_MS = 30 * 60 * 1000;
const POLL_INTERVAL_MS = 30 * 1000;
/// Build-to-group relationships can lag minutes behind a VALID build.
const INTERNAL_ACCESS_TIMEOUT_MS = 5 * 60 * 1000;

/** Escapes a workflow-command payload the way GitHub requires. */
const escapeData = (value) =>
  String(value).replace(/%/g, '%25').replace(/\r/g, '%0D').replace(/\n/g, '%0A');

/** Surfaces a message as a check-run annotation so it is readable without logs. */
export function annotate(level, title, message) {
  console.log(`::${level} title=${escapeData(title)}::${escapeData(message)}`);
}

/**
 * Builds a signed App Store Connect JWT.
 *
 * The only subtle part is dsaEncoding: a JWT ES256 signature is the raw
 * R||S pair (64 bytes), while node:crypto defaults to DER, which App Store
 * Connect rejects with a bare 401.
 */
export function createJwt({ keyId, issuerId, privateKeyPem, now = Math.floor(Date.now() / 1000) }) {
  const header = { alg: 'ES256', kid: keyId, typ: 'JWT' };
  const payload = { iss: issuerId, iat: now, exp: now + 15 * 60, aud: 'appstoreconnect-v1' };
  const encode = (value) => Buffer.from(JSON.stringify(value)).toString('base64url');
  const signingInput = `${encode(header)}.${encode(payload)}`;
  const signature = crypto
    .createSign('SHA256')
    .update(signingInput)
    .sign({ key: privateKeyPem, dsaEncoding: 'ieee-p1363' });
  return `${signingInput}.${signature.toString('base64url')}`;
}

function requireEnv(name) {
  const value = process.env[name];
  if (!value || !value.trim()) {
    throw new Error(`Missing required environment variable ${name}`);
  }
  return value.trim();
}

const sleep = (ms) => new Promise((resolve) => setTimeout(resolve, ms));

async function request(token, path, { method = 'GET', body } = {}) {
  let response;
  try {
    response = await fetch(`${API_BASE}${path}`, {
      method,
      headers: {
        Authorization: `Bearer ${typeof token === 'function' ? token() : token}`,
        'Content-Type': 'application/json',
      },
      body: body === undefined ? undefined : JSON.stringify(body),
    });
  } catch (cause) {
    // A transport failure is not an HTTP status, so name the path explicitly.
    throw new Error(`${method} ${path} could not be reached: ${cause.message}`);
  }

  const text = await response.text();
  let json = null;
  if (text) {
    try {
      json = JSON.parse(text);
    } catch {
      json = null;
    }
  }
  return { ok: response.ok, status: response.status, json, text };
}

function errorDetail(response) {
  return (
    response.json?.errors
      ?.map((error) => `${error.title ?? 'error'}: ${error.detail ?? error.code ?? ''}`)
      .join('; ') ||
    response.text?.slice(0, 600) ||
    '(no response body)'
  );
}

function apiError(label, path, response) {
  return new Error(`${label} failed — ${path} → HTTP ${response.status}: ${errorDetail(response)}`);
}

/**
 * Tries each candidate query until one is accepted. App Store Connect refuses
 * specific parameters per endpoint, and its error names the parameter but not
 * the working alternative.
 */
async function resolveListing(token, paths) {
  const tried = [];
  for (const path of paths) {
    const response = await request(token, path);
    if (response.ok) return { path, response, tried };
    tried.push(`${path} → HTTP ${response.status}`);
  }
  return { path: null, response: null, tried };
}

async function findAppId(token, bundleId) {
  const path = `/v1/apps?filter[bundleId]=${encodeURIComponent(bundleId)}&limit=1`;
  const response = await request(token, path);
  if (!response.ok) throw apiError('App lookup', path, response);
  const app = response.json?.data?.[0];
  if (!app) throw new Error(`No App Store Connect app found for bundle id ${bundleId}`);
  annotate('notice', 'App Store Connect', `app ${bundleId} resolved to id ${app.id}`);
  return app.id;
}

async function waitForProcessedBuild(token, appId, buildNumber) {
  const deadline = Date.now() + PROCESSING_TIMEOUT_MS;
  const candidates = [
    `/v1/builds?filter[app]=${appId}&sort=-uploadedDate&limit=20`,
    `/v1/builds?filter[app]=${appId}&limit=50`,
    `/v1/apps/${appId}/builds?limit=50`,
  ];

  let listingPath = null;
  let state = 'not returned by the API yet';

  while (Date.now() < deadline) {
    let response;
    if (listingPath) {
      response = await request(token, listingPath);
      if (!response.ok) throw apiError('Build lookup', listingPath, response);
    } else {
      const resolved = await resolveListing(token, candidates);
      if (!resolved.path) {
        throw new Error(`Could not list builds for app ${appId}. Attempts: ${resolved.tried.join(' | ')}`);
      }
      listingPath = resolved.path;
      response = resolved.response;
      annotate('notice', 'Build listing', `using ${listingPath}`);
    }

    // Match on the version string rather than trusting ordering alone, so a
    // build uploaded between our pages can never be mistaken for this one.
    const build = (response.json?.data ?? []).find(
      (candidate) => String(candidate.attributes?.version) === buildNumber,
    );

    if (build) {
      state = build.attributes?.processingState ?? 'unknown';
      if (state === 'VALID') {
        annotate('notice', 'TestFlight build', `build ${buildNumber} is VALID (id ${build.id})`);
        return build;
      }
      if (state === 'FAILED' || state === 'INVALID') {
        throw new Error(`Build ${buildNumber} finished processing as ${state}`);
      }
    }

    console.log(`  waiting for build ${buildNumber} to finish processing (state: ${state})…`);
    await sleep(POLL_INTERVAL_MS);
  }

  throw new Error(
    `Timed out after ${PROCESSING_TIMEOUT_MS / 60000} minutes waiting for build ` +
      `${buildNumber} to finish processing (last state: ${state})`,
  );
}

async function listBetaGroups(token, appId) {
  const resolved = await resolveListing(token, [
    `/v1/betaGroups?filter[app]=${appId}&limit=200`,
    `/v1/apps/${appId}/betaGroups?limit=200`,
  ]);
  if (!resolved.path) {
    throw new Error(`Could not list beta groups for app ${appId}. Attempts: ${resolved.tried.join(' | ')}`);
  }
  return resolved.response.json?.data ?? [];
}

/** Adds a build to a beta group (internal or external). */
async function assignBuildToGroup(token, group, buildId, { tolerateConflict = false } = {}) {
  const path = `/v1/betaGroups/${group.id}/relationships/builds`;
  const response = await request(token, path, {
    method: 'POST',
    body: { data: [{ type: 'builds', id: buildId }] },
  });
  if (response.ok) return 'assigned'; // 204 No Content on success
  // 409 means the relationship already exists, which is the desired end state.
  if (tolerateConflict && response.status === 409) return 'already assigned';
  throw apiError(`Assigning build to group "${group.attributes?.name ?? group.id}"`, path, response);
}

export async function groupHasBuild(token, groupId, buildId) {
  let path = `/v1/betaGroups/${groupId}/relationships/builds?limit=200`;
  const seen = new Set();
  while (path && !seen.has(path)) {
    seen.add(path);
    const response = await request(token, path);
    if (!response.ok) throw apiError('Verifying beta group build access', path, response);
    if ((response.json?.data ?? []).some((build) => build.id === buildId)) return true;
    const next = response.json?.links?.next;
    if (!next) return false;
    const url = new URL(next, API_BASE);
    if (url.origin !== API_BASE) throw new Error('Unexpected App Store Connect pagination origin');
    path = `${url.pathname}${url.search}`;
  }
  return false;
}

/**
 * Guarantees an internal group lists the build.
 *
 * Automatic internal distribution is a per-group setting and, even when it is on,
 * App Store Connect can take a while to add a freshly processed build. Checking
 * once immediately after processing therefore reports a false failure (build 40
 * did exactly that), so assign explicitly and poll before giving up.
 */
export async function ensureInternalGroupAccess(
  token,
  group,
  buildId,
  { timeoutMs = INTERNAL_ACCESS_TIMEOUT_MS, intervalMs = POLL_INTERVAL_MS } = {},
) {
  if (await groupHasBuild(token, group.id, buildId)) return 'already present';

  const outcome = await assignBuildToGroup(token, group, buildId, { tolerateConflict: true });

  const deadline = Date.now() + timeoutMs;
  while (true) {
    if (await groupHasBuild(token, group.id, buildId)) return outcome;
    if (Date.now() >= deadline) break;
    await sleep(Math.min(intervalMs, Math.max(0, deadline - Date.now())));
  }

  throw new Error(
    `Internal group "${group.attributes?.name ?? group.id}" still does not list build ${buildId} ` +
      `after assignment and ${Math.round(timeoutMs / 1000)}s. Check the group in App Store Connect; ` +
      'internal tester access has not been verified.',
  );
}

async function submitForBetaAppReview(token, buildId) {
  const path = '/v1/betaAppReviewSubmissions';
  const response = await request(token, path, {
    method: 'POST',
    body: {
      data: {
        type: 'betaAppReviewSubmissions',
        relationships: { build: { data: { type: 'builds', id: buildId } } },
      },
    },
  });
  if (response.ok) return 'submitted';
  // A submission for this build already exists — exactly the state we want,
  // and re-runs of the pipeline must not fail because of it.
  if (response.status === 409) return 'already submitted';
  throw apiError('Submitting build for Beta App Review', path, response);
}

async function main() {
  const nodeMajor = Number(process.versions.node.split('.')[0]);
  if (nodeMajor < 18) {
    throw new Error(`Node 18 or newer is required for global fetch (found ${process.versions.node})`);
  }

  const keyId = requireEnv('ASC_KEY_ID');
  const issuerId = requireEnv('ASC_ISSUER_ID');
  const keyPath = requireEnv('ASC_KEY_PATH');
  const bundleId = requireEnv('BUNDLE_ID');
  const buildNumber = requireEnv('BUILD_NUMBER');

  // Processing can take 30 minutes, longer than a single 15-minute JWT.
  // Sign on demand so polling and subsequent distribution never use an expired token.
  const privateKeyPem = fs.readFileSync(keyPath, 'utf8');
  const token = () => createJwt({ keyId, issuerId, privateKeyPem });
  console.log(`App Store Connect: distributing build ${buildNumber} of ${bundleId}`);

  const appId = await findAppId(token, bundleId);
  const build = await waitForProcessedBuild(token, appId, buildNumber);

  const groups = await listBetaGroups(token, appId);
  if (groups.length === 0) {
    throw new Error(
      'This app has no TestFlight beta groups, so the build cannot be distributed. ' +
        'Create a group in App Store Connect first.',
    );
  }

  const externalGroups = groups.filter((group) => group.attributes?.isInternalGroup === false);
  const internalGroups = groups.filter((group) => group.attributes?.isInternalGroup !== false);

  annotate(
    'notice',
    'TestFlight groups',
    `${groups.length} group(s): ` +
      groups
        .map(
          (group) =>
            `${group.attributes?.name ?? group.id}` +
            `${group.attributes?.isInternalGroup ? ' [internal]' : ' [external]'}`,
        )
        .join(', '),
  );

  for (const group of internalGroups) {
    const outcome = await ensureInternalGroupAccess(token, group, build.id);
    console.log(`  "${group.attributes?.name ?? group.id}" (internal): build access verified (${outcome})`);
  }

  const outcomes = [];
  for (const group of externalGroups) {
    const outcome = await assignBuildToGroup(token, group, build.id);
    if (!(await groupHasBuild(token, group.id, build.id))) {
      throw new Error(`Build ${buildNumber} was not found in external group "${group.attributes?.name ?? group.id}" after assignment`);
    }
    outcomes.push(`${group.attributes?.name ?? group.id}: ${outcome}`);
    console.log(`  "${group.attributes?.name ?? group.id}" (external): ${outcome}`);
  }

  annotate(
    'notice',
    'TestFlight distribution',
    `build ${buildNumber} — internal: ${internalGroups.length} group(s) access verified` +
      (externalGroups.length ? `; external: ${outcomes.join(', ')}` : '; no external groups'),
  );

  if (externalGroups.length === 0) {
    annotate('notice', 'Beta App Review', 'no external groups — internal group build access verified');
    return;
  }

  const state = await submitForBetaAppReview(token, build.id);
  annotate(
    'notice',
    'Beta App Review',
    `build ${buildNumber}: ${state} (${externalGroups.length} external group(s))`,
  );
}

const invokedDirectly =
  process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href;

if (invokedDirectly) {
  main().catch((error) => {
    const message = error?.message ?? String(error);
    console.error(`\nTestFlight distribution failed: ${message}`);
    annotate('error', 'TestFlight distribution failed', message);
    process.exit(1);
  });
}
