// When the free read quota is used up, Firestore reads hang for many seconds before failing. Optional reads must give
// up quickly, stop being attempted for a while, and cost little when they work. Nothing real is contacted.
import { test, beforeEach } from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';

console.warn = () => {};
const { firestoreRead, ensureUserDoc, isFirestoreUnavailable, SKIP_AFTER_QUOTA_MS } = await import('../utils/firestoreBestEffort.js');
const { installFirestoreQuotaGuard, _resetReadQuotaForTests } = await import('../utils/firestoreQuotaGuard.js');
const { buildChecks, runDeepHealth } = await import('../services/deepHealth.js');

beforeEach(() => _resetReadQuotaForTests());

const quota = () => Object.assign(new Error('8 RESOURCE_EXHAUSTED: Quota exceeded.'), { code: 8 });

/** A guarded document class whose get/set fail with the quota error. */
function refusedRef() {
  class Ref { async get() { throw quota(); } async set() { throw quota(); } }
  installFirestoreQuotaGuard({ DocumentReference: Ref }, () => {});
  return new Ref();
}

test('a read that does not answer is given up at the deadline', async () => {
  await assert.rejects(firestoreRead(() => new Promise(() => {}), { timeoutMs: 20 }), (e) => e.timeout && isFirestoreUnavailable(e));
});

test('after a read is refused for quota, optional reads are skipped for a while, then tried again', async () => {
  await assert.rejects(refusedRef().get());
  let calls = 0;
  const read = async () => { calls += 1; return 'ok'; };
  await assert.rejects(firestoreRead(read), (e) => e.quotaSkip === true);
  assert.equal(calls, 0);
  assert.equal(await firestoreRead(read, { now: () => Date.now() + SKIP_AFTER_QUOTA_MS + 1 }), 'ok');
});

test('a refused write does not stop reads: the two quotas are separate', async () => {
  await assert.rejects(refusedRef().set({}));
  assert.equal(await firestoreRead(async () => 'ok'), 'ok');
});

test('a player document is read once per process, created only when missing, and retried after a failure', async () => {
  let reads = 0, writes = 0, fail = true;
  const ref = { get: async () => { reads += 1; if (fail) throw new Error('x'); return { exists: false }; }, set: async () => { writes += 1; } };
  await assert.rejects(ensureUserDoc(ref, 'player-1', {}));
  fail = false;
  for (let i = 0; i < 5; i++) await ensureUserDoc(ref, 'player-1', {});
  assert.equal(reads, 2);
  assert.equal(writes, 1);
});

const supabase = { from: () => ({ select: () => ({ limit: async () => ({ error: null }) }) }) };
const fetchFn = async () => new Response('{}', { status: 200 });
const deep = (get, o = {}) => {
  const db = { collection: () => ({ doc: () => ({ get }) }) };
  return buildChecks({ supabase, getDb: async () => db, workerUrl: 'http://h', fetchFn, ...o });
};

test('deep health: used-up quota or a slow read is a warning, not an outage', async () => {
  for (const [get, detail] of [[async () => { throw quota(); }, 'quota'], [() => new Promise(() => {}), 'timeout']]) {
    const r = await runDeepHealth(deep(get, { firestoreTimeoutMs: 20 }));
    assert.equal(r.ok, true, detail);
    assert.deepEqual([r.checks.firestore.ok, r.checks.firestore.detail], [false, detail]);
    assert.match(r.warnings[0], /^firestore unavailable/);
  }
});

test('deep health: any other Firestore error still fails', async () => {
  const r = await runDeepHealth(deep(async () => { throw Object.assign(new Error('7 PERMISSION_DENIED'), { code: 7 }); }));
  assert.equal(r.ok, false);
});

test('deep health: a working Firestore is read at most once per 15 minutes; a failure is rechecked', async () => {
  let reads = 0, t = 1_000_000, fail = true;
  const checks = deep(async () => { reads += 1; if (fail) throw new Error('x'); }, { now: () => t });
  await runDeepHealth(checks); await runDeepHealth(checks);
  assert.equal(reads, 2);
  fail = false;
  await runDeepHealth(checks); t += 5 * 60_000; await runDeepHealth(checks);
  assert.equal(reads, 3);
  t += 11 * 60_000; await runDeepHealth(checks);
  assert.equal(reads, 4);
});

test('starting the cache scheduler reads nothing from Firestore', () => {
  const src = fs.readFileSync(new URL('../services/cacheScheduler.js', import.meta.url), 'utf8');
  assert.doesNotMatch(src, /\.get\(\)|system_config/);
});
