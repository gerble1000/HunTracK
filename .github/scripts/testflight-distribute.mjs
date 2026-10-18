#!/usr/bin/env node
/**
 * Puts a freshly uploaded build in front of testers.
 *
 * `altool --upload-app` only delivers the binary to App Store Connect; the
 * build then sits there until it is assigned to a beta group, and external
 * groups additionally need a Beta App Review submission. This script does
 * both, using the same App Store Connect API key the workflow already
 * installs for validation and upload.
 *
 * No third-party dependencies: the ES256 JWT is signed with node:crypto and
 * the REST calls use the built-in fetch (Node 18+).
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
const POLL_INTERVAL_MS = 15 * 1000;

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
  const response = await fetch(`${API_BASE}${path}`, {
    method,
    headers: {
      Authorization: `Bearer ${token}`,
      'Content-Type': 'application/json',
    },
    body: body === undefined ? undefined : JSON.stringify(body),
  });
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

function apiError(label, response) {
  const detail =
    response.json?.errors?.map((e) => `${e.title}: ${e.detail}`).join('; ') ||
    response.text?.slice(0, 600) ||
    '(no response body)';
  return new Error(`${label} failed with HTTP ${response.status} — ${detail}`);
}

async function findAppId(token, bundleId) {
  const response = await request(
    token,
    `/v1/apps?filter[bundleId]=${encodeURIComponent(bundleId)}&limit=1`,
  );
  if (!response.ok) throw apiError('App lookup', response);
  const app = response.json?.data?.[0];
  if (!app) throw new Error(`No App Store Connect app found for bundle id ${bundleId}`);
  return app.id;
}

async function waitForProcessedBuild(token, appId, buildNumber) {
  const deadline = Date.now() + PROCESSING_TIMEOUT_MS;
  let state = 'not visible to the API yet';

  while (Date.now() < deadline) {
    const response = await request(
      token,
      `/v1/builds?filter[app]=${appId}&sort=-uploadedDate&limit=20`,
    );
    if (!response.ok) throw apiError('Build lookup', response);

    // Match on the version string rather than trusting ordering alone, so a
    // build uploaded between our pages can never be mistaken for this one.
    const build = (response.json?.data ?? []).find(
      (candidate) => String(candidate.attributes?.version) === buildNumber,
    );

    if (build) {
      state = build.attributes?.processingState ?? 'unknown';
      if (state === 'VALID') return build;
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
  const response = await request(token, `/v1/betaGroups?filter[app]=${appId}&limit=200`);
  if (!response.ok) throw apiError('Beta group lookup', response);
  return response.json?.data ?? [];
}

async function assignBuildToGroup(token, group, buildId) {
  const response = await request(token, `/v1/betaGroups/${group.id}/relationships/builds`, {
    method: 'POST',
    body: { data: [{ type: 'builds', id: buildId }] },
  });
  if (response.ok) return; // 204 No Content on success
  throw apiError(`Assigning build to group "${group.attributes?.name ?? group.id}"`, response);
}

async function submitForBetaAppReview(token, buildId) {
  const response = await request(token, '/v1/betaAppReviewSubmissions', {
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
  throw apiError('Submitting build for Beta App Review', response);
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

  const token = createJwt({ keyId, issuerId, privateKeyPem: fs.readFileSync(keyPath, 'utf8') });
  console.log(`App Store Connect: distributing build ${buildNumber} of ${bundleId}`);

  const appId = await findAppId(token, bundleId);
  const build = await waitForProcessedBuild(token, appId, buildNumber);
  console.log(`Build ${buildNumber} is processed and valid (build id ${build.id})`);

  const groups = await listBetaGroups(token, appId);
  if (groups.length === 0) {
    throw new Error(
      'This app has no TestFlight beta groups, so the build cannot be distributed. ' +
        'Create a group in App Store Connect first.',
    );
  }

  for (const group of groups) {
    await assignBuildToGroup(token, group, build.id);
    const kind = group.attributes?.isInternalGroup ? 'internal' : 'external';
    console.log(`  assigned to "${group.attributes?.name ?? group.id}" (${kind})`);
  }

  const externalGroups = groups.filter((group) => group.attributes?.isInternalGroup === false);
  if (externalGroups.length === 0) {
    console.log('No external groups: internal testers can install the build now. Skipping Beta App Review.');
    return;
  }

  const state = await submitForBetaAppReview(token, build.id);
  console.log(
    `Beta App Review for build ${buildNumber}: ${state}. ` +
      'External testers are notified once Apple approves it.',
  );
}

const invokedDirectly =
  process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href;

if (invokedDirectly) {
  main().catch((error) => {
    console.error(`\nTestFlight distribution failed: ${error.message}`);
    process.exit(1);
  });
}
