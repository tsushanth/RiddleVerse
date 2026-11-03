// Guard for POST /generate-riddle, which spends LLM/image money per request.
//
// Shipped Android/iOS clients send NO credential (only topic/format/numPuzzles/userId in the JSON body), so
// authentication is OPTIONAL by default:
//   - Authorization: Bearer <Firebase or Supabase token> present  -> must verify (else 401); the verified uid is
//     the per-user rate-limit key.
//   - no Authorization header -> allowed (legacy clients) unless GENERATE_RIDDLE_REQUIRE_AUTH=true, then 401.
// Always on, regardless of auth: input size limits (400/413), per-IP and per-user hourly limits, and a global
// daily cap on accepted generations (429).
//
// Counters are in memory, per Fly machine, and reset on restart. Worst case spend is therefore bounded by
// (running machines) x (daily cap) per UTC day, not by the cap alone.
//
// Env (all optional, defaults below):
//   GENERATE_RIDDLE_REQUIRE_AUTH   'true' to reject requests without a valid bearer token (default off)
//   GENERATE_RIDDLE_IP_PER_HOUR    default 10
//   GENERATE_RIDDLE_USER_PER_HOUR  default 5
//   GENERATE_RIDDLE_DAILY_CAP      default 100

export const MAX_BODY_BYTES = 8 * 1024;
export const MAX_TOPIC_LEN = 100;
export const MAX_FORMAT_LEN = 60;
export const MAX_USERID_LEN = 128;
export const MAX_NUM_PUZZLES = 10; // the apps cap at 10 (3 for image, 5 for music)

const HOUR_MS = 60 * 60 * 1000;
const MAX_TRACKED_KEYS = 20000;

function intEnv(env, name, dflt) {
    const n = Number.parseInt(env[name], 10);
    return Number.isFinite(n) && n > 0 ? n : dflt;
}

export function readLimits(env = process.env) {
    return {
        requireAuth: String(env.GENERATE_RIDDLE_REQUIRE_AUTH || '').toLowerCase() === 'true',
        ipPerHour: intEnv(env, 'GENERATE_RIDDLE_IP_PER_HOUR', 10),
        userPerHour: intEnv(env, 'GENERATE_RIDDLE_USER_PER_HOUR', 5),
        dailyCap: intEnv(env, 'GENERATE_RIDDLE_DAILY_CAP', 100),
    };
}

/** Fixed-window-per-key hit log (timestamps in the last hour). */
class HourlyLimiter {
    constructor(now) {
        this.now = now;
        this.hits = new Map(); // key -> number[]
    }
    _recent(key) {
        const cutoff = this.now() - HOUR_MS;
        const arr = (this.hits.get(key) || []).filter((t) => t > cutoff);
        if (arr.length) this.hits.set(key, arr); else this.hits.delete(key);
        return arr;
    }
    /** Returns 0 if another hit is allowed, else the seconds until the oldest hit expires. */
    retryAfter(key, limit) {
        const arr = this._recent(key);
        if (arr.length < limit) return 0;
        return Math.max(1, Math.ceil((arr[0] + HOUR_MS - this.now()) / 1000));
    }
    record(key) {
        if (this.hits.size >= MAX_TRACKED_KEYS) {
            for (const k of [...this.hits.keys()]) this._recent(k); // prune expired
            if (this.hits.size >= MAX_TRACKED_KEYS) this.hits.clear(); // still full: fail toward resetting, never toward OOM
        }
        const arr = this._recent(key);
        arr.push(this.now());
        this.hits.set(key, arr);
    }
}

function utcDay(ms) {
    return new Date(ms).toISOString().slice(0, 10);
}

function bearer(header) {
    if (typeof header !== 'string' || !header.startsWith('Bearer ')) return null;
    const t = header.slice(7).trim();
    return t || null;
}

async function defaultVerify(token) {
    const { verifyToken } = await import('../auth/verifyIdentity.js');
    return verifyToken(token);
}

export function createGenerateRiddleGuard({ env = process.env, now = Date.now, verify = defaultVerify, onReject = null } = {}) {
    const ipLimiter = new HourlyLimiter(now);
    const userLimiter = new HourlyLimiter(now);
    let day = utcDay(now());
    let dayCount = 0;

    return async function generateRiddleGuard(req, res, next) {
        const limits = readLimits(env); // re-read so a Fly env change needs no code change
        const reject = (status, error, extra = {}, headers = {}) => {
            if (onReject) onReject({ status, error });
            for (const [k, v] of Object.entries(headers)) res.setHeader(k, v);
            return res.status(status).json({ error, ...extra });
        };

        // 1. Authentication (optional unless GENERATE_RIDDLE_REQUIRE_AUTH=true)
        const header = req.headers?.authorization;
        const token = bearer(header);
        let verifiedUid = null;
        if (token) {
            try {
                const ident = await verify(token);
                verifiedUid = ident && ident.uid ? String(ident.uid) : null;
            } catch {
                verifiedUid = null;
            }
            if (!verifiedUid) return reject(401, 'Invalid or expired credentials.');
        } else if (limits.requireAuth) {
            return reject(401, 'Authentication required.');
        }

        // 2. Input limits (checked before any quota is consumed)
        const declared = Number.parseInt(req.headers?.['content-length'], 10);
        if (Number.isFinite(declared) && declared > MAX_BODY_BYTES) return reject(413, 'Request too large.');
        const body = req.body && typeof req.body === 'object' ? req.body : {};
        const { topic, format, numPuzzles, userId } = body;
        if (topic !== undefined && (typeof topic !== 'string' || topic.length > MAX_TOPIC_LEN)) {
            return reject(400, `Topic must be text of at most ${MAX_TOPIC_LEN} characters.`);
        }
        if (format !== undefined && (typeof format !== 'string' || format.length > MAX_FORMAT_LEN)) {
            return reject(400, 'Invalid format.');
        }
        if (userId !== undefined && (typeof userId !== 'string' || userId.length > MAX_USERID_LEN)) {
            return reject(400, 'Invalid userId.');
        }
        if (numPuzzles !== undefined) {
            const n = typeof numPuzzles === 'number' ? numPuzzles : Number(numPuzzles);
            if (!Number.isInteger(n) || n < 1 || n > MAX_NUM_PUZZLES) {
                return reject(400, `numPuzzles must be a whole number from 1 to ${MAX_NUM_PUZZLES}.`);
            }
            req.body.numPuzzles = n;
        }

        // 3. Rate limits. Missing-parameter requests fall through to the handler's own 400 and use no quota.
        if (!topic || !format || !numPuzzles || !userId) return next();

        const ipKey = String(req.ip || req.socket?.remoteAddress || 'unknown');
        const userKey = verifiedUid ? `uid:${verifiedUid}` : `body:${String(userId).toLowerCase()}`;

        const today = utcDay(now());
        if (today !== day) { day = today; dayCount = 0; }

        if (dayCount >= limits.dailyCap) {
            const secs = Math.max(1, Math.ceil((Date.parse(`${day}T00:00:00Z`) + 24 * HOUR_MS - now()) / 1000));
            return reject(429, 'Puzzle creation is very busy today. Please try again tomorrow.', {}, { 'Retry-After': String(secs) });
        }
        const ipWait = ipLimiter.retryAfter(ipKey, limits.ipPerHour);
        if (ipWait) return reject(429, 'Too many puzzle requests from your network. Please try again later.', {}, { 'Retry-After': String(ipWait) });
        const userWait = userLimiter.retryAfter(userKey, limits.userPerHour);
        if (userWait) return reject(429, 'You have created several puzzles recently. Please try again in a while.', {}, { 'Retry-After': String(userWait) });

        ipLimiter.record(ipKey);
        userLimiter.record(userKey);
        dayCount += 1;
        req.generateRiddleUid = verifiedUid;
        return next();
    };
}
