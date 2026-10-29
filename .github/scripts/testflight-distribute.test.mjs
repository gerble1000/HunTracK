import { test } from 'node:test';
import assert from 'node:assert/strict';
import crypto from 'node:crypto';
import { createJwt, ensureInternalGroupAccess, groupHasBuild } from './testflight-distribute.mjs';

async function withFetch(fetcher, action) {
  const original = globalThis.fetch;
  globalThis.fetch = fetcher;
  try { await action(); } finally { globalThis.fetch = original; }
}

const response = (data, next = null) => new Response(JSON.stringify({ data, links: { next } }), {
  headers: { 'Content-Type': 'application/json' },
});

test('finds the build on a later page and refreshes authorization for each request', async () => {
  const requests = [];
  let tokens = 0;
  await withFetch(async (url, options) => {
    requests.push({ url, authorization: options.headers.Authorization });
    return requests.length === 1
      ? response([{ type: 'builds', id: 'older' }], 'https://api.appstoreconnect.apple.com/v1/betaGroups/group/relationships/builds?cursor=next')
      : response([{ type: 'builds', id: 'new' }]);
  }, async () => {
    assert.equal(await groupHasBuild(() => `token-${++tokens}`, 'group', 'new'), true);
  });
  assert.equal(requests.length, 2);
  assert.deepEqual(requests.map((r) => r.authorization), ['Bearer token-1', 'Bearer token-2']);
});

test('assigns then verifies an internal group that has not received the build yet', async () => {
  const calls = [];
  await withFetch(async (url, options) => {
    calls.push(`${options?.method ?? 'GET'} ${url}`);
    if (options?.method === 'POST') return new Response(null, { status: 204 });
    // Missing on the first read, present after the assignment, like the real API.
    return calls.filter((call) => call.startsWith('GET')).length === 1
      ? response([])
      : response([{ type: 'builds', id: 'new' }]);
  }, async () => {
    const outcome = await ensureInternalGroupAccess(
      'token',
      { id: 'group', attributes: { name: 'Staff', isInternalGroup: true } },
      'new',
      { timeoutMs: 1000, intervalMs: 5 },
    );
    assert.equal(outcome, 'assigned');
  });
  assert.ok(calls.some((call) => call.startsWith('POST')), 'expected an explicit group assignment');
});

test('accepts an already-present internal build without calling the assignment endpoint', async () => {
  const methods = [];
  await withFetch(async (url, options) => {
    methods.push(options?.method ?? 'GET');
    return response([{ type: 'builds', id: 'new' }]);
  }, async () => {
    const outcome = await ensureInternalGroupAccess('token', { id: 'group' }, 'new', { timeoutMs: 1000, intervalMs: 5 });
    assert.equal(outcome, 'already present');
  });
  assert.deepEqual(methods, ['GET']);
});

test('tolerates an existing relationship and still confirms access', async () => {
  let reads = 0;
  await withFetch(async (url, options) => {
    if (options?.method === 'POST') return new Response(null, { status: 409 });
    reads += 1;
    return reads === 1 ? response([]) : response([{ type: 'builds', id: 'new' }]);
  }, async () => {
    const outcome = await ensureInternalGroupAccess('token', { id: 'group' }, 'new', { timeoutMs: 1000, intervalMs: 5 });
    assert.equal(outcome, 'already assigned');
  });
});

test('fails clearly when the group still never lists the build', async () => {
  await withFetch(async (url, options) =>
    options?.method === 'POST' ? new Response(null, { status: 204 }) : response([]), async () => {
    await assert.rejects(
      ensureInternalGroupAccess('token', { id: 'group', attributes: { name: 'Staff' } }, 'new', {
        timeoutMs: 60,
        intervalMs: 5,
      }),
      /internal tester access has not been verified/,
    );
  });
});

test('never sends authorization to a foreign pagination origin', async () => {
  let count = 0;
  await withFetch(async () => {
    count++;
    return response([], 'https://example.invalid/steal');
  }, async () => {
    await assert.rejects(groupHasBuild('token', 'group', 'new'), /pagination origin/);
  });
  assert.equal(count, 1);
});

test('reports API failures rather than treating them as missing or distributed builds', async () => {
  await withFetch(async () => new Response(JSON.stringify({ errors: [{ title: 'Unauthorized' }] }), { status: 401 }), async () => {
    await assert.rejects(groupHasBuild('token', 'group', 'new'), /HTTP 401/);
  });
});

test('signs App Store Connect JWTs with the required raw ES256 signature', () => {
  const { privateKey, publicKey } = crypto.generateKeyPairSync('ec', { namedCurve: 'prime256v1' });
  const token = createJwt({ keyId: 'key', issuerId: 'issuer', privateKeyPem: privateKey, now: 1000 });
  const [header, payload, signature] = token.split('.');
  assert.equal(Buffer.from(signature, 'base64url').length, 64);
  assert.deepEqual(JSON.parse(Buffer.from(payload, 'base64url')), {
    iss: 'issuer', iat: 1000, exp: 1900, aud: 'appstoreconnect-v1',
  });
  assert.equal(crypto.verify('SHA256', Buffer.from(`${header}.${payload}`), {
    key: publicKey, dsaEncoding: 'ieee-p1363',
  }, Buffer.from(signature, 'base64url')), true);
});
