// GET /api/health/deep: a cheap, real check of the dependencies a player's request needs, for external probes.
//   supabase     one row from `puzzles`
//   firestore    one document read
//   forge_worker the game-build worker's /health on the Hetzner box (GAME_WORKER_URL)
// Answers 200 {ok:true} or 503 {ok:false}; the probe (app-failure-reporter Worker, or the harness canary) only has to read
// the status. It does NOT call an LLM and does NOT write anything (contrast /api/health/comprehensive, which generates
// puzzles and calls two AI providers, so it must never be probed). Failure details are generic on purpose (no URLs,
// hostnames or driver messages leave the server). Result is cached 30 s so a probe storm cannot hammer Firestore/Supabase.
const withTimeout = (p, ms, label) => Promise.race([
  Promise.resolve(p),
  new Promise((_, rej) => setTimeout(() => rej(Object.assign(new Error(`${label} timed out`), { timeout: true })), ms).unref?.()),
]);

/** workerUrl may be the bare origin or end in /game (the VibeBuild-style mount); /health lives on the origin. */
export const workerBase = (url) => String(url ?? '').replace(/\/+$/, '').replace(/\/game$/, '');

export function buildChecks({ supabase, getDb, workerUrl, fetchFn = fetch }) {
  return {
    supabase: async () => {
      if (!supabase) throw new Error('supabase client missing');
      const { error } = await supabase.from('puzzles').select('puzzleid').limit(1);
      if (error) throw new Error('supabase query failed');
    },
    firestore: async () => {
      const db = await getDb();
      if (!db) throw new Error('firestore not initialised');
      await db.collection('health_check').doc('test').get();
    },
    forge_worker: async () => {
      const base = workerBase(workerUrl);
      if (!base) throw new Error('GAME_WORKER_URL not set');
      const res = await fetchFn(`${base}/health`, { signal: AbortSignal.timeout(5000) });
      if (res.status !== 200) throw new Error(`forge worker answered HTTP ${res.status}`);
      let body = null; try { body = await res.json(); } catch { /* shallow workers may not return JSON */ }
      return { activeGenerations: typeof body?.activeGenerations === 'number' ? body.activeGenerations : undefined };
    },
  };
}

/** -> {ok, checks:{name:{ok, ms, detail?}}, warnings:[...], timestamp}. Never throws. */
export async function runDeepHealth(checks, { timeoutMs = 8000, now = Date.now } = {}) {
  const out = { ok: true, checks: {}, warnings: [], timestamp: new Date(now()).toISOString() };
  await Promise.all(Object.entries(checks).map(async ([name, fn]) => {
    const t0 = now();
    try {
      const extra = await withTimeout(fn(), timeoutMs, name);
      out.checks[name] = { ok: true, ms: now() - t0 };
      if (name === 'forge_worker' && typeof extra?.activeGenerations === 'number' && extra.activeGenerations < 0)
        out.warnings.push(`forge_worker activeGenerations is ${extra.activeGenerations} (slot counter bug in the deployed worker; not a failure)`);
    } catch (e) {
      out.ok = false;
      out.checks[name] = { ok: false, ms: now() - t0, detail: e?.timeout ? 'timeout' : /HTTP \d+/.test(e?.message ?? '') ? e.message.match(/HTTP \d+/)[0] : /not set|missing|not initialised/.test(e?.message ?? '') ? 'not configured' : 'error' };
    }
  }));
  return out;
}

export function createDeepHealthHandler({ checks, cacheMs = 30_000, now = Date.now, timeoutMs } = {}) {
  let cached = null, inflight = null;
  return async (req, res) => {
    try {
      if (!(cached && now() - cached.at < cacheMs)) {
        inflight ??= runDeepHealth(checks(), { timeoutMs, now }).then((r) => { cached = { at: now(), result: r }; return r; }).finally(() => { inflight = null; });
        await inflight;
      }
      res.status(cached.result.ok ? 200 : 503).json(cached.result);
    } catch {
      res.status(503).json({ ok: false, checks: {}, warnings: [], timestamp: new Date(now()).toISOString() });
    }
  };
}
