// /api/health/deep: pass/fail semantics, generic failure details, no LLM/writes, caching, URL handling. Fakes only.
import { test } from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const { buildChecks, runDeepHealth, createDeepHealthHandler, workerBase } = await import('../services/deepHealth.js');

const okSupabase = { from: () => ({ select: () => ({ limit: async () => ({ data: [{ puzzleid: 1 }], error: null }) }) }) };
const okDb = { collection: () => ({ doc: () => ({ get: async () => ({ exists: false }) }) }) };
const okFetch = (body = { healthy: true, activeGenerations: 0 }) => async () => new Response(JSON.stringify(body), { status: 200 });
const deps = (o = {}) => ({ supabase: okSupabase, getDb: async () => okDb, workerUrl: 'http://178.156.192.31:3456', fetchFn: okFetch(), ...o });
const res = () => { const r = { code: null, body: null, status(c) { r.code = c; return r; }, json(b) { r.body = b; return r; } }; return r; };

test('all three dependencies healthy: ok true, every check listed with timing', async () => {
  const r = await runDeepHealth(buildChecks(deps()));
  assert.equal(r.ok, true); assert.deepEqual(Object.keys(r.checks).sort(), ['firestore', 'forge_worker', 'supabase']);
  assert.ok(Object.values(r.checks).every((c) => c.ok && typeof c.ms === 'number')); assert.deepEqual(r.warnings, []);
});

test('each dependency can fail the check on its own, with a generic detail that leaks nothing', async () => {
  const bad = await runDeepHealth(buildChecks(deps({
    supabase: { from: () => ({ select: () => ({ limit: async () => ({ error: { message: 'connection to db.secret-host.supabase.co refused' } }) }) }) },
    getDb: async () => ({ collection: () => ({ doc: () => ({ get: async () => { throw new Error('8 RESOURCE_EXHAUSTED: Quota exceeded for project foo'); } }) }) }),
    fetchFn: async () => new Response('down', { status: 503 }),
  })));
  assert.equal(bad.ok, false);
  assert.deepEqual(Object.values(bad.checks).map((c) => c.ok), [false, false, false]);
  assert.equal(bad.checks.forge_worker.detail, 'HTTP 503');
  assert.equal(bad.checks.supabase.detail, 'error');
  const text = JSON.stringify(bad);
  for (const leak of ['secret-host', 'RESOURCE_EXHAUSTED', 'project foo', '178.156']) assert.ok(!text.includes(leak), `leaked ${leak}`);
  for (const only of ['supabase', 'firestore', 'forge_worker']) {
    const one = await runDeepHealth({ ...buildChecks(deps()), [only]: async () => { throw new Error('x'); } });
    assert.equal(one.ok, false, only); assert.equal(Object.values(one.checks).filter((c) => !c.ok).length, 1, only);
  }
});

test('a hung dependency times out instead of hanging the probe', async () => {
  const r = await runDeepHealth({ supabase: () => new Promise(() => {}) }, { timeoutMs: 20 });
  assert.equal(r.ok, false); assert.equal(r.checks.supabase.detail, 'timeout');
});

test('forge worker URL: bare origin and the /game mount both resolve to <origin>/health; unset is "not configured"', async () => {
  assert.equal(workerBase('http://h:3456/game/'), 'http://h:3456'); assert.equal(workerBase('http://h:3456'), 'http://h:3456');
  const seen = []; const fetchFn = async (u) => (seen.push(u), new Response('{}', { status: 200 }));
  await buildChecks(deps({ workerUrl: 'http://h:3456/game', fetchFn })).forge_worker();
  assert.deepEqual(seen, ['http://h:3456/health']);
  const r = await runDeepHealth(buildChecks(deps({ workerUrl: '' }))); assert.equal(r.checks.forge_worker.detail, 'not configured');
});

test('a negative activeGenerations on the deployed worker is a WARNING, not a failure (it would page the moment this ships)', async () => {
  const r = await runDeepHealth(buildChecks(deps({ fetchFn: okFetch({ healthy: true, activeGenerations: -18 }) })));
  assert.equal(r.ok, true); assert.match(r.warnings[0], /activeGenerations is -18/);
});

test('handler: 200 when ok, 503 when not; result cached 30 s, concurrent requests share one run', async () => {
  let runs = 0, t = 1_000_000; let fail = false;
  const handler = createDeepHealthHandler({ now: () => t, checks: () => ({ supabase: async () => { runs++; await new Promise((r) => setTimeout(r, 5)); if (fail) throw new Error('x'); } }) });
  const [a, b] = [res(), res()]; await Promise.all([handler({}, a), handler({}, b)]);
  assert.equal(runs, 1); assert.equal(a.code, 200); assert.equal(b.body.ok, true);
  fail = true; t += 10_000; const c = res(); await handler({}, c); assert.equal(c.code, 200, 'still cached'); assert.equal(runs, 1);
  t += 25_000; const d = res(); await handler({}, d); assert.equal(d.code, 503); assert.equal(d.body.ok, false); assert.equal(runs, 2);
});

test('the route is wired in server.js and the deep check makes no LLM call and no write', () => {
  const dir = path.join(path.dirname(fileURLToPath(import.meta.url)), '..');
  const server = fs.readFileSync(path.join(dir, 'server.js'), 'utf8');
  assert.match(server, /app\.get\('\/api\/health\/deep'/);
  const svc = fs.readFileSync(path.join(dir, 'services/deepHealth.js'), 'utf8');
  assert.ok(!/callAI|anthropic|openai|\.insert\(|\.update\(|\.upsert\(|\.set\(|\.delete\(/i.test(svc));
});
