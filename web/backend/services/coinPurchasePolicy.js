// Policy for POST /api/coins/purchase: which mode we are in, and the safeguards that apply in every mode.
//
// Modes (env COIN_PURCHASE_VERIFY):
//   enforce  (DEFAULT, also for any unknown value) - coins are credited only for a Play order that the Android Publisher API
//            confirms is PROCESSED and contains the claimed product. If the verifier cannot decide (no credential, API error)
//            the request fails CLOSED with 503 {retryable: true}: no coins, nothing recorded.
//   interim  - explicit opt-in while the verifier credential does not exist yet. No receipt check, but: platform must be
//            android, the transaction id must look like a Play order id, per-account daily limits, a per-IP hourly limit and a
//            global daily ceiling apply, and every claim is logged. This is a stop-gap, not a defence against a determined
//            attacker (an order id of the right shape can be invented); it exists so legitimate purchases keep working.
// The per-account, per-IP and global limits below apply in BOTH modes as a second layer.
//
// Counters kept in memory are per Fly machine and reset on restart; the per-account limits read the coin_transactions ledger.

const DAY_MS = 24 * 60 * 60 * 1000;
const HOUR_MS = 60 * 60 * 1000;
const MAX_TRACKED = 20000;

function intEnv(env, name, dflt) {
    const n = Number.parseInt(env[name], 10);
    return Number.isFinite(n) && n > 0 ? n : dflt;
}

export function getMode(env = process.env) {
    return String(env.COIN_PURCHASE_VERIFY || '').trim().toLowerCase() === 'interim' ? 'interim' : 'enforce';
}

export function readPolicy(env = process.env) {
    return {
        mode: getMode(env),
        accountPurchasesPerDay: intEnv(env, 'COIN_PURCHASE_MAX_PER_DAY', 4),
        accountCoinsPerDay: intEnv(env, 'COIN_PURCHASE_MAX_COINS_PER_DAY', 3000),
        ipClaimsPerHour: intEnv(env, 'COIN_PURCHASE_IP_PER_HOUR', 20),
        globalCoinsPerDay: intEnv(env, 'COIN_PURCHASE_GLOBAL_COINS_PER_DAY', 30000),
        maxOrderAgeMs: intEnv(env, 'COIN_PURCHASE_MAX_ORDER_AGE_HOURS', 24 * 14) * HOUR_MS,
    };
}

/** Structured, greppable anomaly line. Never logs tokens or emails; user ids are truncated. */
export function logAnomaly(kind, fields = {}, log = console.warn) {
    const safe = {};
    for (const [k, v] of Object.entries(fields)) {
        safe[k] = k === 'userId' && typeof v === 'string' ? `${v.slice(0, 6)}…` : v;
    }
    log(`[Coins][ANOMALY] ${kind} ${JSON.stringify(safe)}`);
}

/** In-memory sliding counters: per-IP claims per hour and global coins per UTC day. */
export function createCounters(now = Date.now) {
    const ipHits = new Map(); // ip -> number[]
    let day = new Date(now()).toISOString().slice(0, 10);
    let globalCoins = 0;

    function rollDay() {
        const d = new Date(now()).toISOString().slice(0, 10);
        if (d !== day) { day = d; globalCoins = 0; }
    }

    return {
        ipOverLimit(ip, limit) {
            const cutoff = now() - HOUR_MS;
            const arr = (ipHits.get(ip) || []).filter((t) => t > cutoff);
            if (arr.length) ipHits.set(ip, arr); else ipHits.delete(ip);
            return arr.length >= limit;
        },
        recordIp(ip) {
            if (ipHits.size >= MAX_TRACKED) ipHits.clear();
            const cutoff = now() - HOUR_MS;
            const arr = (ipHits.get(ip) || []).filter((t) => t > cutoff);
            arr.push(now());
            ipHits.set(ip, arr);
        },
        globalWouldExceed(coins, limit) { rollDay(); return globalCoins + coins > limit; },
        recordGlobal(coins) { rollDay(); globalCoins += coins; },
        snapshot() { rollDay(); return { day, globalCoins }; },
    };
}

/**
 * Per-account limit from the ledger: purchases and coins credited to this user in the last 24 h (award rows excluded).
 * `supabase` is the shared server client. Returns { count, coins } or null when the ledger could not be read.
 */
export async function recentPurchases(supabase, userId, now = Date.now) {
    try {
        const since = new Date(now() - DAY_MS).toISOString();
        const { data, error } = await supabase
            .from('coin_transactions')
            .select('amount')
            .eq('user_id', userId)
            .eq('type', 'purchase')
            .like('reason', 'com.kreativekoala.riddleverse.coins.%')
            .gte('created_at', since)
            .limit(200);
        if (error || !Array.isArray(data)) return null;
        return { count: data.length, coins: data.reduce((s, r) => s + (Number(r.amount) || 0), 0) };
    } catch {
        return null;
    }
}
