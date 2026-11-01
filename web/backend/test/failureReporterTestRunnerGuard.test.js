import { test } from 'node:test';
import assert from 'node:assert/strict';

// Regression: the reporter has a built-in production URL and key, and plain `node --test` sets neither NODE_ENV=test nor
// FAILURE_REPORTER_DISABLED, so a stray report call in any test once emailed the owner a fake error.
test('under node --test the reporter never posts to the production endpoint', async () => {
  assert.ok(process.env.NODE_TEST_CONTEXT, 'sanity: running under the node test runner');
  delete process.env.NODE_ENV;
  delete process.env.FAILURE_REPORTER_DISABLED;
  const calls = [];
  const real = globalThis.fetch;
  globalThis.fetch = async (...a) => { calls.push(a); return { ok: true }; };
  try {
    const fr = await import('../utils/failureReporter.js');
    fr.reportFailure('x', new Error('must not leave the process'));
    fr.reportBackendError({ method: 'GET', path: '/x' }, new Error('nor this'), 500);
    await fr.reportCrash('x', new Error('nor this either'));
    await new Promise((r) => setTimeout(r, 20));
  } finally { globalThis.fetch = real; }
  assert.equal(calls.length, 0, 'no network call to the reporter');
});
