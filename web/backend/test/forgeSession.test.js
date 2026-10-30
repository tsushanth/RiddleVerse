// Tests for forge session bookkeeping: close-once semantics, failure reporting without content,
// and the stuck-session sweeper (cutoff, 100-row cap, no overwrite of completed rows, flag).
// Fakes only: no network, no Supabase.
import { test } from 'node:test';
import assert from 'node:assert/strict';

process.env.DOCKER_BUILD = 'true';
const {
  createForgeSessionCloser,
  sweepStuckForgeSessions,
  startForgeSessionSweeper,
  stopForgeSessionSweeper,
  isForgeSweeperEnabled,
  FORGE_STUCK_AFTER_MS,
  FORGE_SWEEP_BATCH_LIMIT,
  FORGE_FAILURE_REASONS,
} = await import('../services/forgeSessionService.js');

const silent = { log() {}, warn() {} };

// Minimal in-memory stand-in for the supabase-js query builder used by the sweeper.
function fakeSupabase(rows, { failSelect = false, completeBeforeUpdate = new Set() } = {}) {
  const calls = { selectLimit: null, updates: [] };
  return {
    calls,
    from(table) {
      assert.equal(table, 'forge_sessions');
      const f = { status: null, updatedBefore: null, id: null, limit: null, patch: null, mode: null };
      const b = {
        select(cols) {
          if (f.mode === 'update') return b;
          f.mode = 'select';
          return b;
        },
        update(patch) { f.mode = 'update'; f.patch = patch; return b; },
        eq(col, val) { if (col === 'status') f.status = val; if (col === 'id') f.id = val; return b; },
        lt(col, val) { f.updatedBefore = val; return b; },
        order() { return b; },
        limit(n) { f.limit = n; calls.selectLimit = n; return b; },
        then(resolve) {
          if (f.mode === 'select') {
            if (failSelect) return resolve({ data: null, error: { message: 'boom' } });
            const out = rows
              .filter(r => r.status === f.status && r.updated_at < f.updatedBefore)
              .slice(0, f.limit)
              .map(r => ({ id: r.id, messages: r.messages }));
            return resolve({ data: out, error: null });
          }
          // update
          const row = rows.find(r => r.id === f.id);
          if (row && completeBeforeUpdate.has(row.id)) row.status = 'completed'; // lost the race
          if (row && row.status === f.status) {
            Object.assign(row, f.patch);
            calls.updates.push(row.id);
            return resolve({ data: [{ id: row.id }], error: null });
          }
          return resolve({ data: [], error: null });
        },
      };
      return b;
    },
  };
}

const NOW = Date.parse('2026-10-29T12:00:00Z');
const old = new Date(NOW - FORGE_STUCK_AFTER_MS - 60_000).toISOString();
const fresh = new Date(NOW - 5 * 60_000).toISOString();

test('closer: first close wins, later closes are no-ops', () => {
  const finished = [];
  const events = [];
  const reports = [];
  const close = createForgeSessionCloser({
    sessionId: 's1',
    finish: (...a) => finished.push(a),
    recordEvent: (...a) => events.push(a),
    report: (...a) => reports.push(a),
  });
  assert.equal(close('failed', 'worker_error'), true);
  assert.equal(close('completed', null, 'g1'), false);
  assert.equal(close('failed', 'timeout'), false);
  assert.deepEqual(finished, [['s1', 'failed', null]]);
  assert.equal(events.length, 1);
  assert.equal(reports.length, 1);
});

test('closer: completed does not report a failure', () => {
  const reports = [];
  const events = [];
  const close = createForgeSessionCloser({
    sessionId: 's1', finish() {}, recordEvent: (...a) => events.push(a), report: (...a) => reports.push(a),
  });
  close('completed', null, 'g1');
  assert.equal(reports.length, 0);
  assert.equal(events.length, 0);
});

test('closer: failure report has a stable message, fixed-string context, no ids', () => {
  const reports = [];
  const close = createForgeSessionCloser({
    sessionId: 'abc-session-id', finish() {}, recordEvent() {}, report: (...a) => reports.push(a),
  });
  close('failed', 'worker_unreachable');
  const [flow, err, ctx] = reports[0];
  assert.equal(flow, 'forge.generate');
  assert.equal(err.message, 'Forge generation failed: worker_unreachable');
  assert.deepEqual(ctx, { reason: 'worker_unreachable' });
  assert.ok(!JSON.stringify([err.message, ctx]).includes('abc-session-id'));
});

test('closer: unknown reason collapses to server_error; no session id means no-op', () => {
  const reports = [];
  const close = createForgeSessionCloser({
    sessionId: 's', finish() {}, recordEvent() {}, report: (...a) => reports.push(a),
  });
  close('failed', 'some free text with a prompt in it');
  assert.equal(reports[0][1].message, 'Forge generation failed: server_error');
  const noop = createForgeSessionCloser({ sessionId: null, finish() { throw new Error('x'); }, recordEvent() {}, report() {} });
  assert.equal(noop('failed', 'timeout'), false);
});

test('every failure reason has a user-readable sentence', () => {
  for (const [k, v] of Object.entries(FORGE_FAILURE_REASONS)) {
    assert.ok(typeof v === 'string' && v.length > 10 && v.endsWith('.'), k);
  }
});

test('stuck threshold is above the slowest completed build (593 s) and the 600 s worker timeout', () => {
  assert.ok(FORGE_STUCK_AFTER_MS > 600_000);
  assert.equal(FORGE_STUCK_AFTER_MS, 15 * 60_000);
});

test('sweeper: fails only old generating rows, leaves fresh/completed alone, adds a readable note', async () => {
  const rows = [
    { id: 'a', status: 'generating', updated_at: old, messages: [{ role: 'user', content: 'x' }] },
    { id: 'b', status: 'generating', updated_at: fresh, messages: [] },
    { id: 'c', status: 'completed', updated_at: old, messages: [] },
    { id: 'd', status: 'failed', updated_at: old, messages: [] },
  ];
  const reports = [];
  const r = await sweepStuckForgeSessions({ supabase: fakeSupabase(rows), now: NOW, report: (...a) => reports.push(a), log: silent });
  assert.deepEqual(r, { swept: 1, found: 1 });
  assert.equal(rows[0].status, 'failed');
  assert.equal(rows[0].messages.length, 2);
  assert.equal(rows[0].messages[1].role, 'system');
  assert.match(rows[0].messages[1].content, /did not finish in time/);
  assert.equal(rows[1].status, 'generating');
  assert.equal(rows[2].status, 'completed');
  assert.equal(reports.length, 1);
  assert.deepEqual(reports[0][2], { reason: 'sweeper_timeout', batch: 'partial' });
});

test('sweeper: at most 100 rows per pass', async () => {
  const rows = Array.from({ length: 250 }, (_, i) => ({
    id: `r${i}`, status: 'generating', updated_at: old, messages: [],
  }));
  const sb = fakeSupabase(rows);
  const r = await sweepStuckForgeSessions({ supabase: sb, now: NOW, report() {}, log: silent });
  assert.equal(sb.calls.selectLimit, FORGE_SWEEP_BATCH_LIMIT);
  assert.equal(r.swept, 100);
  assert.equal(rows.filter(x => x.status === 'failed').length, 100);
  assert.equal(rows.filter(x => x.status === 'generating').length, 150);
});

test('sweeper: never overwrites a row that completed after it was selected', async () => {
  const rows = [{ id: 'a', status: 'generating', updated_at: old, messages: [] }];
  const r = await sweepStuckForgeSessions({
    supabase: fakeSupabase(rows, { completeBeforeUpdate: new Set(['a']) }),
    now: NOW, report() {}, log: silent,
  });
  assert.equal(r.swept, 0);
  assert.equal(rows[0].status, 'completed');
});

test('sweeper: database error is swallowed and reported as zero', async () => {
  const r = await sweepStuckForgeSessions({ supabase: fakeSupabase([], { failSelect: true }), now: NOW, report() {}, log: silent });
  assert.deepEqual(r, { swept: 0, found: 0 });
});

test('sweeper: nothing stuck means no report', async () => {
  const reports = [];
  const r = await sweepStuckForgeSessions({ supabase: fakeSupabase([]), now: NOW, report: (...a) => reports.push(a), log: silent });
  assert.deepEqual(r, { swept: 0, found: 0 });
  assert.equal(reports.length, 0);
});

test('flag: off unless FORGE_SESSION_SWEEPER_ENABLED=true; start is a no-op when off', () => {
  assert.equal(isForgeSweeperEnabled({}), false);
  assert.equal(isForgeSweeperEnabled({ FORGE_SESSION_SWEEPER_ENABLED: '1' }), false);
  assert.equal(isForgeSweeperEnabled({ FORGE_SESSION_SWEEPER_ENABLED: 'true' }), true);
  const orig = console.log; console.log = () => {};
  try {
    assert.equal(startForgeSessionSweeper({ supabase: null, env: {} }), false);
    assert.equal(startForgeSessionSweeper({ supabase: fakeSupabase([]), env: { FORGE_SESSION_SWEEPER_ENABLED: 'true' }, intervalMs: 3_600_000, firstRunDelayMs: 3_600_000 }), true);
  } finally {
    stopForgeSessionSweeper();
    console.log = orig;
  }
});
