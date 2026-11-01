// Monitoring-gap tests: Firestore quota detector + guard, provider failure classes, forge invariant
// (dead-man / completion rate), unreported-5xx safety net. Fakes only; no network.
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { EventEmitter } from 'node:events';

process.env.DOCKER_BUILD = 'true';
const fr = await import('../utils/failureReporter.js');
const { installFirestoreQuotaGuard } = await import('../utils/firestoreQuotaGuard.js');
const { checkForgeInvariant } = await import('../services/forgeSessionService.js');
const { createUnhandled5xxReporter } = await import('../middleware/errorHandler.js');

test('isFirestoreQuotaError: code 8, RESOURCE_EXHAUSTED text, and not other errors', () => {
  assert.ok(fr.isFirestoreQuotaError(Object.assign(new Error('x'), { code: 8 })));
  assert.ok(fr.isFirestoreQuotaError(new Error('8 RESOURCE_EXHAUSTED: Quota exceeded.')));
  assert.ok(!fr.isFirestoreQuotaError(new Error('permission denied')));
  assert.ok(!fr.isFirestoreQuotaError(null));
});

test('reportFirestoreQuota: one stable message, throttled to one per 10 minutes', () => {
  fr._resetFirestoreQuotaThrottleForTests();
  const calls = [];
  const rep = (flow, err, ctx) => calls.push({ flow, msg: err.message, ctx });
  assert.equal(fr.reportFirestoreQuota('a', rep, 1_000_000), true);
  assert.equal(fr.reportFirestoreQuota('b', rep, 1_000_000 + 60_000), false);
  assert.equal(fr.reportFirestoreQuota('c', rep, 1_000_000 + 11 * 60_000), true);
  assert.equal(calls.length, 2);
  assert.equal(calls[0].flow, 'firestore.quota');
  assert.equal(calls[0].msg, calls[1].msg);
  assert.deepEqual(Object.keys(calls[0].ctx), ['source']);
});

test('guard: observes a swallowed quota rejection, rethrows unchanged, ignores other errors and sync results', async () => {
  const quota = Object.assign(new Error('8 RESOURCE_EXHAUSTED'), { code: 8 });
  class DocumentReference { get() { return Promise.reject(quota); } set() { return Promise.reject(new Error('other')); } }
  class WriteBatch { commit() { return Promise.resolve('ok'); } }
  const seen = [];
  const n = installFirestoreQuotaGuard({ DocumentReference, WriteBatch }, (flow) => seen.push(flow));
  assert.equal(n, 3);
  await assert.rejects(new DocumentReference().get(), quota);
  await assert.rejects(new DocumentReference().set(), /other/);
  assert.equal(await new WriteBatch().commit(), 'ok');
  await new Promise((r) => setImmediate(r));
  assert.deepEqual(seen, ['DocumentReference.get']);
  assert.equal(installFirestoreQuotaGuard({ DocumentReference }, () => {}), 0, 'idempotent');
});

test('classifyProviderError: auth, credit/quota, plain rate limit is not reported', () => {
  assert.equal(fr.classifyProviderError({ response: { status: 401, data: {} } }), 'auth');
  assert.equal(fr.classifyProviderError({ response: { status: 400, data: { error: { message: 'Your credit balance is too low' } } } }), 'quota');
  assert.equal(fr.classifyProviderError({ response: { status: 429, data: { error: { code: 'insufficient_quota' } } } }), 'quota');
  assert.equal(fr.classifyProviderError({ response: { status: 402 } }), 'quota');
  assert.equal(fr.classifyProviderError({ response: { status: 429, data: { error: { message: 'slow down' } } } }), null);
  assert.equal(fr.classifyProviderError(new Error('timeout')), null);
});

test('reportProviderError: stable message, provider label only, no body text', () => {
  const calls = [];
  const rep = (flow, err, ctx) => calls.push({ flow, msg: err.message, ctx });
  const secretBody = { error: { message: 'credit balance too low for user@example.com' } };
  assert.equal(fr.reportProviderError('claude-3-sonnet', { response: { status: 400, data: secretBody } }, rep), 'quota');
  assert.equal(fr.reportProviderError('deepseek', { response: { status: 500 } }, rep), null);
  assert.equal(calls.length, 1);
  assert.equal(calls[0].flow, 'provider.claude-3-sonnet');
  assert.ok(!JSON.stringify(calls[0]).includes('example.com'));
  assert.deepEqual(calls[0].ctx, { provider: 'claude-3-sonnet', class: 'quota' });
});

// forge_sessions count() stand-in: head-count queries filtered by created_at >= x and optional status
function fakeForge(rows, { fail = false } = {}) {
  return {
    from(t) {
      assert.equal(t, 'forge_sessions');
      const f = { since: null, status: null };
      const b = {
        select() { return b; },
        gte(_c, v) { f.since = v; return b; },
        eq(_c, v) { f.status = v; return b; },
        then(res) {
          if (fail) return res({ count: null, error: { message: 'db down' } });
          const n = rows.filter((r) => r.created_at >= f.since && (!f.status || r.status === f.status)).length;
          return res({ count: n, error: null });
        },
      };
      return b;
    },
  };
}
const NOW = Date.parse('2026-10-30T12:00:00Z');
const row = (minsAgo, status) => ({ created_at: new Date(NOW - minsAgo * 60000).toISOString(), status });

test('forge invariant: dead-man fires when attempts exist and nothing completed in 3 h', async () => {
  const calls = [];
  const out = await checkForgeInvariant({ supabase: fakeForge([row(10, 'failed'), row(40, 'generating'), row(90, 'failed')]), now: NOW, report: (f, e, c) => calls.push({ f, m: e.message, c }), log: { warn() {} } });
  assert.deepEqual(out.alerts, ['deadman']);
  assert.equal(calls[0].f, 'forge.invariant');
  assert.deepEqual(calls[0].c, { rule: 'no_completion_3h' });
});

test('forge invariant: quiet when a session completed recently, or too few attempts', async () => {
  const rep = () => assert.fail('should not report');
  let out = await checkForgeInvariant({ supabase: fakeForge([row(10, 'failed'), row(40, 'completed'), row(90, 'failed')]), now: NOW, report: rep, log: { warn() {} } });
  assert.deepEqual(out.alerts, []);
  out = await checkForgeInvariant({ supabase: fakeForge([row(10, 'failed'), row(40, 'failed')]), now: NOW, report: rep, log: { warn() {} } });
  assert.deepEqual(out.alerts, []);
});

test('forge invariant: completion rate below 20% over 24 h fires, 25% does not', async () => {
  const mk = (completed, total) => Array.from({ length: total }, (_, i) => row(5 * 60 + i, i < completed ? 'completed' : 'failed'));
  const calls = [];
  let out = await checkForgeInvariant({ supabase: fakeForge(mk(1, 10)), now: NOW, report: (f, e, c) => calls.push(c), log: { warn() {} } });
  assert.deepEqual(out.alerts, ['rate']);
  assert.deepEqual(calls, [{ rule: 'low_completion_24h' }]);
  out = await checkForgeInvariant({ supabase: fakeForge(mk(3, 12)), now: NOW, report: () => assert.fail('no'), log: { warn() {} } });
  assert.deepEqual(out.alerts, []);
});

test('forge invariant: a failing query is reported, never thrown', async () => {
  const calls = [];
  const out = await checkForgeInvariant({ supabase: fakeForge([], { fail: true }), now: NOW, report: (f, e, c) => calls.push(c), log: { warn() {} } });
  assert.deepEqual(out.alerts, []);
  assert.deepEqual(calls, [{ rule: 'check_failed' }]);
});

test('reportUnhandled5xx: reports a swallowed 500 once, skips 4xx, already-reported and health', () => {
  const calls = [];
  const mw = createUnhandled5xxReporter((req, err, status) => calls.push({ req, msg: err.message, status }));
  const run = (statusCode, { locals = {}, originalUrl = '/api/x', route = { path: '/x/:id' }, baseUrl = '/api' } = {}) => {
    const res = new EventEmitter(); res.statusCode = statusCode; res.locals = locals;
    mw({ method: 'POST', originalUrl, route, baseUrl, path: '/x/123' }, res, () => {});
    res.emit('finish');
  };
  run(500);
  run(404);
  run(500, { locals: { failureReported: true } });
  run(500, { originalUrl: '/api/health/deep' });
  assert.equal(calls.length, 1);
  assert.equal(calls[0].req.path, '/api/x/:id', 'route pattern, never the raw url or ids');
  assert.equal(calls[0].status, 500);
});
