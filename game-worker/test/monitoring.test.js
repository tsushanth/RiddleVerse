import { test } from 'node:test';
import assert from 'node:assert/strict';
import { classifyCliFailure, reportCliFailure, _resetForTests, createSlotTracker, evaluateHealth, freeDiskBytes, LOW_DISK_BYTES } from '../monitoring.js';

test('classifyCliFailure: quota, auth, disk full, and unrelated text', () => {
  assert.equal(classifyCliFailure("You're out of extra usage. resets 8am"), 'quota');
  assert.equal(classifyCliFailure('Invalid API key. Please run /login'), 'auth');
  assert.equal(classifyCliFailure('API Error: 401 authentication_error'), 'auth');
  assert.equal(classifyCliFailure('Error: ENOSPC: no space left on device, write'), 'enospc');
  assert.equal(classifyCliFailure('Write(index.html)'), null);
  assert.equal(classifyCliFailure(''), null);
});

test('reportCliFailure: one report per class per 15 min, fixed messages only', () => {
  _resetForTests();
  const T = 10_000_000;
  const sent = [];
  const send = (flow, msg, ctx) => sent.push({ flow, msg, ctx });
  assert.ok(reportCliFailure('auth', T, send));
  assert.ok(!reportCliFailure('auth', T + 1000, send));
  assert.ok(reportCliFailure('enospc', T + 1000, send));
  assert.ok(reportCliFailure('auth', T + 16 * 60000, send));
  assert.ok(!reportCliFailure('nope', 0, send));
  assert.equal(sent.length, 3);
  assert.equal(sent[0].flow, 'forge.worker.auth');
  assert.deepEqual(sent[0].ctx, { class: 'auth' });
});

test('slot tracker: double release never drives the counter negative or frees another build slot', () => {
  const anomalies = [];
  const t = createSlotTracker(2, (k) => anomalies.push(k));
  const a = t.acquire(); const b = t.acquire();
  assert.ok(t.full());
  a(); a();                       // early-return path + finally, the bug that produced -17
  assert.equal(t.active, 1, 'second release of the same slot is a no-op');
  assert.deepEqual(anomalies, ['double_release']);
  assert.ok(!t.full());
  b();
  assert.equal(t.active, 0);
});

test('evaluateHealth: 503 reasons for missing CLI, quota, negative counter, low disk', () => {
  const ok = { cliAvailable: true, quotaExhausted: false, activeGenerations: 0, freeBytes: 50 * 1024 ** 3 };
  assert.deepEqual(evaluateHealth(ok), { ok: true, reasons: [] });
  assert.deepEqual(evaluateHealth({ ...ok, cliAvailable: false }).reasons, ['cli_missing']);
  assert.deepEqual(evaluateHealth({ ...ok, quotaExhausted: true }).reasons, ['quota_exhausted']);
  assert.deepEqual(evaluateHealth({ ...ok, activeGenerations: -17 }).reasons, ['negative_active_generations']);
  assert.deepEqual(evaluateHealth({ ...ok, freeBytes: LOW_DISK_BYTES - 1 }).reasons, ['disk_low']);
  assert.equal(evaluateHealth({ ...ok, freeBytes: null }).ok, true, 'unknown disk is not a failure');
});

test('freeDiskBytes: computes from statfs, null on error', () => {
  assert.equal(freeDiskBytes('/x', () => ({ bavail: 10, bsize: 4096 })), 40960);
  assert.equal(freeDiskBytes('/x', () => { throw new Error('no'); }), null);
});

import { summarizeCliResult, createUsageTally } from '../monitoring.js';

test('summarizeCliResult keeps only numbers from the CLI result event, null when there are none', () => {
  const r = summarizeCliResult({ type: 'result', total_cost_usd: 0.42, duration_ms: 90000, num_turns: 7, result: 'SECRET OUTPUT TEXT',
    usage: { input_tokens: 1200, output_tokens: 3400, cache_read_input_tokens: 5000, cache_creation_input_tokens: 800 } });
  assert.deepEqual(r, { costUsd: 0.42, inputTokens: 1200, outputTokens: 3400, cacheReadTokens: 5000, cacheWriteTokens: 800, durationMs: 90000, turns: 7 });
  assert.ok(!JSON.stringify(r).includes('SECRET'));
  assert.equal(summarizeCliResult({ type: 'assistant' }), null);
  assert.equal(summarizeCliResult({ type: 'result' }), null);
  assert.equal(summarizeCliResult(null), null);
  assert.equal(summarizeCliResult({ type: 'result', total_cost_usd: -1, usage: { input_tokens: 'x' } }), null);
});

test('usage tally sums per UTC day, counts failures and sessions without usage, keeps two weeks', () => {
  let t = Date.UTC(2026, 10, 22, 12);
  const tally = createUsageTally(() => t);
  tally.record({ costUsd: 0.5, inputTokens: 10, outputTokens: 20 }, true);
  tally.record(null, false);
  t += 86400e3;
  tally.record({ costUsd: 0.25 }, true);
  const snap = tally.snapshot();
  assert.equal(snap['2026-11-22'].sessions, 2);
  assert.equal(snap['2026-11-22'].failed, 1);
  assert.equal(snap['2026-11-22'].withUsage, 1);
  assert.equal(snap['2026-11-22'].costUsd, 0.5);
  assert.equal(snap['2026-11-23'].costUsd, 0.25);
  for (let i = 0; i < 20; i++) { t += 86400e3; tally.record(null, true); }
  assert.equal(Object.keys(tally.snapshot()).length, 14);
});
