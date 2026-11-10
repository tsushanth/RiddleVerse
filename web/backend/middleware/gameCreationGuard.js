// Abuse limits for the unauthenticated game-creation, games, leaderboard and score routes.
//
// Shipped clients send NO credential and no install header on these routes, so by default authentication is OPTIONAL:
//   - Authorization: Bearer <Firebase or Supabase ID token> present -> must verify (401 otherwise); if the body names a userId it
//     must be the verified uid (403 otherwise). The verified uid becomes the rate-limit key.
//   - no Authorization header -> allowed, unless GAME_CREATION_REQUIRE_AUTH=true (the "enforce" flag), then 401.
//   - optional X-Install-Id header (a random per-install id, next app release) is the key when there is no verified uid.
// Limits are always on and config-driven (env, re-read on every request so a Fly env change needs no code change):
//   GAME_CREATION_IP_PER_HOUR        accepted spend-bearing requests per IP per hour            default 40
//   GAME_CREATION_KEY_PER_HOUR       per verified uid / install id / body userId per hour       default 20
//   GAME_CREATION_DAILY_BUDGET_USD   global cap on the ESTIMATED LLM spend per UTC day          default 25
//   GAME_CREATION_COST_ESTIMATES     JSON {kind: usd} overriding the per-request estimates      default below
//   GAME_CREATION_REQUIRE_AUTH       'true' to reject requests without a valid bearer token     default off
//   WRITE_RATE_PER_MINUTE            writes (POST/PUT/PATCH/DELETE) per IP per minute on the games/leaderboard/score routes  default 120
// Counters are in memory per Fly machine and reset on restart, so the worst case is (running machines) x (daily budget).
// The budget is an estimate (requests x configured cost per kind), not metered billing.

export const DEFAULT_COSTS = { generate: 0.8, remix: 0.8, tweak: 0.4, customize: 0.4, difficulty: 0.4, nextlevel: 0.4, save: 0 };
const HOUR_MS = 60 * 60 * 1000;
const MINUTE_MS = 60 * 1000;
const MAX_KEYS = 20000;

function numEnv(env, name, dflt) {
    const n = Number(env[name]);
    return Number.isFinite(n) && n > 0 ? n : dflt;
}

export function readConfig(env = process.env) {
    let costs = DEFAULT_COSTS;
    if (env.GAME_CREATION_COST_ESTIMATES) {
        try {
            const j = JSON.parse(env.GAME_CREATION_COST_ESTIMATES);
            if (j && typeof j === 'object') costs = { ...DEFAULT_COSTS, ...Object.fromEntries(Object.entries(j).filter(([, v]) => Number.isFinite(v) && v >= 0)) };
        } catch { /* keep defaults */ }
    }
    return {
        requireAuth: String(env.GAME_CREATION_REQUIRE_AUTH || '').toLowerCase() === 'true',
        ipPerHour: numEnv(env, 'GAME_CREATION_IP_PER_HOUR', 40),
        keyPerHour: numEnv(env, 'GAME_CREATION_KEY_PER_HOUR', 20),
        dailyBudgetUsd: numEnv(env, 'GAME_CREATION_DAILY_BUDGET_USD', 25),
        writePerMinute: numEnv(env, 'WRITE_RATE_PER_MINUTE', 120),
        costs,
    };
}

/** Which spend-bearing action a request to /api/game-creation is, or null for reads and unknown routes. Path is router-relative. */
export function classify(method, path) {
    if (method !== 'POST') return null;
    const p = String(path || '').split('?')[0].replace(/\/+$/, '');
    if (p === '/generate') return 'generate';
    if (p === '/remix') return 'remix';
    if (p === '/save') return 'save';
    if (/^\/[^/]+\/tweak$/.test(p)) return 'tweak';
    if (/^\/[^/]+\/customize$/.test(p)) return 'customize';
    if (/^\/[^/]+\/difficulty\/[^/]+\/generate$/.test(p)) return 'difficulty';
    if (/^\/[^/]+\/next-level\/generate$/.test(p)) return 'nextlevel';
    return null;
}

class WindowCounter {
    constructor(now, windowMs) { this.now = now; this.windowMs = windowMs; this.hits = new Map(); }
    _recent(key) {
        const cutoff = this.now() - this.windowMs;
        const arr = (this.hits.get(key) || []).filter((t) => t > cutoff);
        if (arr.length) this.hits.set(key, arr); else this.hits.delete(key);
        return arr;
    }
    /** 0 if another hit is allowed, else seconds until one frees up. */
    retryAfter(key, limit) {
        const arr = this._recent(key);
        if (arr.length < limit) return 0;
        return Math.max(1, Math.ceil((arr[0] + this.windowMs - this.now()) / 1000));
    }
    record(key) {
        if (this.hits.size >= MAX_KEYS) { for (const k of [...this.hits.keys()]) this._recent(k); if (this.hits.size >= MAX_KEYS) this.hits.clear(); }
        const arr = this._recent(key); arr.push(this.now()); this.hits.set(key, arr);
    }
}

const utcDay = (ms) => new Date(ms).toISOString().slice(0, 10);
const clientIp = (req) => String(req.ip || req.socket?.remoteAddress || 'unknown');

function bearer(req) {
    const h = req.headers?.authorization;
    if (typeof h !== 'string' || !h.startsWith('Bearer ')) return null;
    return h.slice(7).trim() || null;
}

async function defaultVerify(token) {
    const { verifyToken } = await import('../auth/verifyIdentity.js');
    return verifyToken(token);
}

export function createGameCreationGuard({ env = process.env, now = Date.now, verify = defaultVerify, log = console.warn } = {}) {
    const ipCounter = new WindowCounter(now, HOUR_MS);
    const keyCounter = new WindowCounter(now, HOUR_MS);
    let day = utcDay(now());
    let spentUsd = 0;

    return async function gameCreationGuard(req, res, next) {
        const cfg = readConfig(env);
        const kind = classify(req.method, req.path);
        const reject = (status, error, extra = {}, headers = {}) => {
            for (const [k, v] of Object.entries(headers)) res.setHeader(k, v);
            return res.status(status).json({ error, ...extra });
        };

        // Identity (applies to every request on this router, reads included, when a token is sent or auth is required)
        const token = bearer(req);
        let uid = null;
        if (token) {
            try { const id = await verify(token); uid = id && id.uid ? String(id.uid) : null; } catch { uid = null; }
            if (!uid) return reject(401, 'Invalid or expired credentials.');
            const bodyUser = req.body && typeof req.body === 'object' ? req.body.userId : undefined;
            if (typeof bodyUser === 'string' && bodyUser && bodyUser !== uid) return reject(403, 'Credentials do not match userId.');
        } else if (cfg.requireAuth && kind) {
            return reject(401, 'Authentication required.');
        }

        if (!kind) return next(); // reads and unknown routes: nothing to meter here
        // Free actions (save) are not metered: the cost-bearing request that preceded them already was.
        const metered = (Number(cfg.costs[kind]) || 0) > 0;

        const body = req.body && typeof req.body === 'object' ? req.body : {};
        if (body.userId !== undefined && (typeof body.userId !== 'string' || body.userId.length > 128)) return reject(400, 'Invalid userId.');
        const installId = typeof req.headers?.['x-install-id'] === 'string' ? req.headers['x-install-id'].slice(0, 64) : '';
        const key = uid ? `uid:${uid}` : installId ? `install:${installId}` : body.userId ? `body:${String(body.userId).toLowerCase()}` : null;
        const ip = clientIp(req);
        const cost = Number(cfg.costs[kind]) || 0;

        const today = utcDay(now());
        if (today !== day) { day = today; spentUsd = 0; }

        if (cost > 0 && spentUsd + cost > cfg.dailyBudgetUsd) {
            log(`[GameGuard] daily budget reached (${spentUsd.toFixed(2)} of ${cfg.dailyBudgetUsd} USD estimated) kind=${kind}`);
            const secs = Math.max(1, Math.ceil((Date.parse(`${day}T00:00:00Z`) + 24 * HOUR_MS - now()) / 1000));
            return reject(429, 'Game creation is very busy today. Please try again tomorrow.', {}, { 'Retry-After': String(secs) });
        }
        const ipWait = metered ? ipCounter.retryAfter(ip, cfg.ipPerHour) : 0;
        if (ipWait) {
            log(`[GameGuard] ip limit kind=${kind}`);
            return reject(429, 'Too many requests from your network. Please try again later.', {}, { 'Retry-After': String(ipWait) });
        }
        if (key && metered) {
            const keyWait = keyCounter.retryAfter(key, cfg.keyPerHour);
            if (keyWait) {
                log(`[GameGuard] key limit kind=${kind} keyType=${key.split(':')[0]}`);
                return reject(429, 'You have made several requests recently. Please try again in a while.', {}, { 'Retry-After': String(keyWait) });
            }
        }

        if (metered) {
            ipCounter.record(ip);
            if (key) keyCounter.record(key);
            spentUsd += cost;
        }
        req.gameCreationUid = uid;
        return next();
    };
}

/** Per-IP write limiter for the games / leaderboard / score routes (cheap, no LLM): POST/PUT/PATCH/DELETE only. */
export function createWriteRateLimit({ env = process.env, now = Date.now, log = console.warn } = {}) {
    const counter = new WindowCounter(now, MINUTE_MS);
    return function writeRateLimit(req, res, next) {
        if (!['POST', 'PUT', 'PATCH', 'DELETE'].includes(req.method)) return next();
        const cfg = readConfig(env);
        const ip = clientIp(req);
        const wait = counter.retryAfter(ip, cfg.writePerMinute);
        if (wait) {
            log('[WriteLimit] ip over limit');
            res.setHeader('Retry-After', String(wait));
            return res.status(429).json({ error: 'Too many requests. Please slow down.' });
        }
        counter.record(ip);
        return next();
    };
}
