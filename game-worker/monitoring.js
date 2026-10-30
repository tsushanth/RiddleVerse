// Failure signals for the forge game-worker (runs on the Hetzner box under pm2 vibecoder-worker, port 3456).
// Sends to the central app-failure-reporter (app "riddleverse"): fire-and-forget, never throws,
// silent in tests (NODE_ENV=test or inside `node --test`) or with FAILURE_REPORTER_DISABLED=1.
// Messages are fixed strings per class; context is a fixed label. Never pass prompts, ids or output text.
import fs from 'node:fs';

const ENDPOINT = process.env.FAILURE_REPORTER_URL || 'https://app-failure-reporter.t-sushanth.workers.dev/v1/report';
// Same per-app key the riddleverse backend ships with (an identifier, not a secret).
const KEY = process.env.FAILURE_REPORTER_KEY || 'afr_2380403b6807a34c833fa412468daee3';
const DISABLED = () => process.env.FAILURE_REPORTER_DISABLED === '1' || process.env.NODE_ENV === 'test' || !!process.env.NODE_TEST_CONTEXT;

async function post(kind, flow, message, context, timeoutMs, fetchImpl = fetch) {
  if (DISABLED()) return;
  try {
    await fetchImpl(ENDPOINT, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', 'X-Report-Key': KEY },
      body: JSON.stringify({ kind, platform: 'backend', version: process.env.npm_package_version || 'game-worker', flow, message, stack: '', context }),
      signal: AbortSignal.timeout(timeoutMs),
    });
  } catch { /* reporting must never cause a failure of its own */ }
}
export const reportWorkerFailure = (flow, message, context = {}) => { void post('failure', flow, message, context, 5000); };
export const reportWorkerCrash = (flow, err) => post('crash', flow, String(err?.message || err).slice(0, 200), {}, 2500);

/** Classifies Claude CLI output/stderr. Returns 'quota' | 'auth' | 'enospc' | null. */
export function classifyCliFailure(text) {
  const t = String(text || '').toLowerCase();
  if (!t) return null;
  if (t.includes('enospc') || t.includes('no space left on device')) return 'enospc';
  if (/invalid api key|not logged in|please run \/login|authentication_error|oauth token (has )?expired|401|unauthorized|invalid x-api-key|credit balance is too low|could not resolve authentication/.test(t)) return 'auth';
  if (/out of extra usage|usage limit|rate limit exceeded|quota exceeded|resets at|resets 8am/.test(t)) return 'quota';
  return null;
}

const MESSAGES = {
  quota: 'Forge worker: Claude CLI account is out of usage/quota; game generation is refused until it resets',
  auth: 'Forge worker: Claude CLI authentication failed (broker account login expired or invalid); generation will keep failing',
  enospc: 'Forge worker: disk is full (ENOSPC); builds and pm2 logs cannot be written',
};
const lastSent = new Map();
/** Reports a classified CLI failure, at most once per 15 min per class per process. */
export function reportCliFailure(cls, now = Date.now(), send = reportWorkerFailure) {
  if (!MESSAGES[cls]) return false;
  if (now - (lastSent.get(cls) || 0) < 15 * 60 * 1000) return false;
  lastSent.set(cls, now);
  send(`forge.worker.${cls}`, MESSAGES[cls], { class: cls });
  return true;
}
export function _resetForTests() { lastSent.clear(); }

/**
 * Concurrency slots. release() is idempotent per acquire(): the old code did `activeGenerations--` on both an
 * early-return path and in `finally`, which drove the counter negative (-17 seen) and let more than
 * MAX_CONCURRENT builds run at once.
 */
export function createSlotTracker(max, onAnomaly = () => {}) {
  let active = 0;
  return {
    get active() { return active; },
    max,
    full: () => active >= max,
    acquire() {
      active++;
      let released = false;
      return function release() {
        if (released) { onAnomaly('double_release'); return; }
        released = true;
        active--;
        if (active < 0) { active = 0; onAnomaly('negative'); }
      };
    },
  };
}

/** Free bytes on the projects volume, or null if unreadable. */
export function freeDiskBytes(dir, statfs = fs.statfsSync) {
  try { const s = statfs(dir); return Number(s.bavail) * Number(s.bsize); } catch { return null; }
}
export const LOW_DISK_BYTES = 1.5 * 1024 ** 3;

/**
 * /health decision. Returns { ok, reasons[] }: not ok when the CLI is missing, quota is exhausted,
 * the slot counter is corrupt, or the disk is nearly full. Caller answers 503 when !ok so the
 * central probe (which treats non-2xx as down) alerts.
 */
export function evaluateHealth({ cliAvailable, quotaExhausted, activeGenerations, freeBytes }) {
  const reasons = [];
  if (!cliAvailable) reasons.push('cli_missing');
  if (quotaExhausted) reasons.push('quota_exhausted');
  if (activeGenerations < 0) reasons.push('negative_active_generations');
  if (freeBytes !== null && freeBytes !== undefined && freeBytes < LOW_DISK_BYTES) reasons.push('disk_low');
  return { ok: reasons.length === 0, reasons };
}
