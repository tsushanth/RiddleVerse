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
