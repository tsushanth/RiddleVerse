// Forge (AI game generation) session bookkeeping.
//
// Why this exists: before this change a forge_sessions row was opened as 'generating' and only
// closed on two paths (game saved, or an exception). Every other way a build can end (worker
// replied 5xx, worker sent an error event, the stream dropped, the worker finished without a
// result) left the row 'generating' for ever: 83 of 1,257 rows in prod were stuck like that.
//
// Two parts:
//   1. createForgeSessionCloser: closes a session exactly once, whichever path gets there first.
//   2. sweepStuckForgeSessions / startForgeSessionSweeper: marks rows that are still 'generating'
//      after FORGE_STUCK_AFTER_MS as failed (covers process crashes and deploys mid-build).
//
// Privacy: failure reports carry fixed strings only. Never pass prompts, ids or emails.
import { reportFailure as defaultReportFailure } from '../utils/failureReporter.js';

// Completed sessions in prod: median ~195 s, p90 ~258 s, max 593 s. The backend aborts the worker
// request at 600 s. 15 minutes is therefore safely beyond any build that can still succeed.
export const FORGE_STUCK_AFTER_MS = 15 * 60 * 1000;
export const FORGE_SWEEP_BATCH_LIMIT = 100;
export const FORGE_SWEEP_INTERVAL_MS = 10 * 60 * 1000;

export const FORGE_FAILURE_REASONS = Object.freeze({
  worker_unreachable: 'The build server could not be reached.',
  worker_http_error: 'The build server returned an error.',
  worker_quota: 'The build server is out of AI capacity right now.',
  worker_error: 'The build server reported an error while building.',
  stream_lost: 'The connection to the build server dropped before the game was ready.',
  stream_ended_without_result: 'The build server stopped before the game was ready.',
  timeout: 'The build took too long and was stopped.',
  db_save_error: 'The game was built but could not be saved.',
  sweeper_timeout: 'The build did not finish in time and was marked as failed.',
  server_error: 'Something went wrong on the server.',
});

export function isForgeSweeperEnabled(env = process.env) {
  return env.FORGE_SESSION_SWEEPER_ENABLED === 'true';
}

export function reasonMessage(reason) {
  return FORGE_FAILURE_REASONS[reason] || FORGE_FAILURE_REASONS.server_error;
}

/**
 * Returns close(status, reason, savedGameId): the first call finishes the session, later calls are
 * no-ops. A failed close also fires the analytics event and the central failure report.
 * deps: finish(sessionId, status, savedGameId), recordEvent(name, params), report(flow, err, ctx)
 */
export function createForgeSessionCloser({ sessionId, finish, recordEvent, report = defaultReportFailure }) {
  let closed = false;
  return function close(status, reason = null, savedGameId = null) {
    if (closed || !sessionId) return false;
    closed = true;
    finish(sessionId, status, savedGameId);
    if (status === 'failed') {
      const known = FORGE_FAILURE_REASONS[reason] ? reason : 'server_error';
      recordEvent?.('forge_session_failed', { session_id: sessionId, reason: known });
      // Stable message per class so the reporter can dedupe; context is non-content strings only.
      report('forge.generate', new Error(`Forge generation failed: ${known}`), { reason: known });
    }
    return true;
  };
}

/**
 * One sweep pass. Sessions still 'generating' whose last update is older than stuckAfterMs become
 * 'failed'. At most `limit` rows per pass. Never throws.
 * Returns { swept, found }.
 */
export async function sweepStuckForgeSessions({
  supabase,
  now = Date.now(),
  stuckAfterMs = FORGE_STUCK_AFTER_MS,
  limit = FORGE_SWEEP_BATCH_LIMIT,
  report = defaultReportFailure,
  log = console,
} = {}) {
  try {
    const cutoff = new Date(now - stuckAfterMs).toISOString();
    const { data: rows, error } = await supabase
      .from('forge_sessions')
      .select('id, messages')
      .eq('status', 'generating')
      .lt('updated_at', cutoff)
      .order('updated_at', { ascending: true })
      .limit(limit);
    if (error) {
      log.warn?.('[forge-sweeper] select failed:', error.message);
      report('forge.sweeper', new Error('Forge sweeper could not read forge_sessions (database error)'), { reason: 'sweeper_db_error' });
      return { swept: 0, found: 0 };
    }
    if (!rows || rows.length === 0) return { swept: 0, found: 0 };

    const note = { role: 'system', content: reasonMessage('sweeper_timeout') };
    const stamp = new Date(now).toISOString();
    let swept = 0;
    for (const row of rows.slice(0, limit)) {
      const messages = Array.isArray(row.messages) ? [...row.messages, note] : [note];
      // Re-check status in the UPDATE so a build that completes right now is never overwritten.
      const { data: updated, error: upErr } = await supabase
        .from('forge_sessions')
        .update({ status: 'failed', messages, updated_at: stamp })
        .eq('id', row.id)
        .eq('status', 'generating')
        .select('id');
      if (upErr) {
        log.warn?.('[forge-sweeper] update failed:', upErr.message);
        continue;
      }
      if (updated && updated.length > 0) swept++;
    }
    if (swept > 0) {
      log.log?.(`[forge-sweeper] marked ${swept} stuck session(s) failed (older than ${Math.round(stuckAfterMs / 60000)} min)`);
      // One report per pass, not one per row.
      report('forge.sweeper', new Error('Forge sessions stuck in generating were marked failed'), {
        reason: 'sweeper_timeout',
        batch: swept >= limit ? 'limit_reached' : 'partial',
      });
    }
    return { swept, found: rows.length };
  } catch (e) {
    log.warn?.('[forge-sweeper] pass threw:', e?.message || e);
    report('forge.sweeper', new Error('Forge sweeper pass threw an exception'), { reason: 'sweeper_exception' });
    return { swept: 0, found: 0 };
  }
}

export const FORGE_DEADMAN_WINDOW_MS = 3 * 60 * 60 * 1000;
export const FORGE_DEADMAN_MIN_ATTEMPTS = 3;
export const FORGE_RATE_WINDOW_MS = 24 * 60 * 60 * 1000;
export const FORGE_RATE_MIN_ATTEMPTS = 10;
export const FORGE_RATE_MIN_COMPLETION = 0.2;

/**
 * End-to-end invariant: forge sessions created vs completed.
 *  - dead-man: >= 3 sessions created in the last 3 h and none completed in that window
 *  - rate: >= 10 sessions created in the last 24 h and fewer than 20% completed
 * One report per rule per pass (the reporter dedupes to one email per hour). Never throws.
 * Returns { created3h, completed3h, created24h, completed24h, alerts: string[] }.
 */
export async function checkForgeInvariant({ supabase, now = Date.now(), report = defaultReportFailure, log = console } = {}) {
  const out = { created3h: 0, completed3h: 0, created24h: 0, completed24h: 0, alerts: [] };
  try {
    const count = async (windowMs, completedOnly) => {
      let q = supabase.from('forge_sessions').select('id', { count: 'exact', head: true }).gte('created_at', new Date(now - windowMs).toISOString());
      if (completedOnly) q = q.eq('status', 'completed');
      const { count: n, error } = await q;
      if (error) throw new Error(error.message || 'count failed');
      return n || 0;
    };
    out.created3h = await count(FORGE_DEADMAN_WINDOW_MS, false);
    out.completed3h = await count(FORGE_DEADMAN_WINDOW_MS, true);
    out.created24h = await count(FORGE_RATE_WINDOW_MS, false);
    out.completed24h = await count(FORGE_RATE_WINDOW_MS, true);
    if (out.created3h >= FORGE_DEADMAN_MIN_ATTEMPTS && out.completed3h === 0) {
      out.alerts.push('deadman');
      report('forge.invariant', new Error('Forge dead-man: sessions were started in the last 3 hours but none completed'), { rule: 'no_completion_3h' });
    }
    if (out.created24h >= FORGE_RATE_MIN_ATTEMPTS && out.completed24h / out.created24h < FORGE_RATE_MIN_COMPLETION) {
      out.alerts.push('rate');
      report('forge.invariant', new Error('Forge completion rate is below 20 percent over the last 24 hours'), { rule: 'low_completion_24h' });
    }
  } catch (e) {
    log.warn?.('[forge-invariant] check failed:', e?.message || e);
    report('forge.invariant', new Error('Forge invariant check could not query forge_sessions'), { rule: 'check_failed' });
  }
  return out;
}

let timer = null;

/** Starts the periodic sweeper when FORGE_SESSION_SWEEPER_ENABLED=true. Idempotent. */
export function startForgeSessionSweeper({ supabase, env = process.env, intervalMs = FORGE_SWEEP_INTERVAL_MS, firstRunDelayMs = 60 * 1000 } = {}) {
  if (!isForgeSweeperEnabled(env)) {
    console.log('[forge-sweeper] disabled (set FORGE_SESSION_SWEEPER_ENABLED=true to enable)');
    return false;
  }
  if (timer) return true;
  const run = () => { void sweepStuckForgeSessions({ supabase }); };
  const first = setTimeout(run, firstRunDelayMs);
  first.unref?.();
  timer = setInterval(run, intervalMs);
  timer.unref?.();
  console.log(`[forge-sweeper] started: every ${Math.round(intervalMs / 60000)} min, stuck after ${Math.round(FORGE_STUCK_AFTER_MS / 60000)} min, max ${FORGE_SWEEP_BATCH_LIMIT} rows per pass`);
  return true;
}

export function stopForgeSessionSweeper() {
  if (timer) { clearInterval(timer); timer = null; }
}

let invariantTimer = null;
export const FORGE_INVARIANT_INTERVAL_MS = 30 * 60 * 1000;
/** Read-only dead-man / completion-rate monitor. ON by default; FORGE_INVARIANT_MONITOR=false turns it off. */
export function startForgeInvariantMonitor({ supabase, env = process.env, intervalMs = FORGE_INVARIANT_INTERVAL_MS, firstRunDelayMs = 5 * 60 * 1000 } = {}) {
  if (env.FORGE_INVARIANT_MONITOR === 'false') return false;
  if (invariantTimer) return true;
  const run = () => { void checkForgeInvariant({ supabase }); };
  const first = setTimeout(run, firstRunDelayMs);
  first.unref?.();
  invariantTimer = setInterval(run, intervalMs);
  invariantTimer.unref?.();
  return true;
}
export function stopForgeInvariantMonitor() {
  if (invariantTimer) { clearInterval(invariantTimer); invariantTimer = null; }
}
