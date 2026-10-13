// Puzzle monitoring: event validation, rate limiting, and admin aggregation (pure, testable).

export const NEW_WAVE_TYPES = [
    'sudoku', 'binarygrid', 'cagemath', 'signgrid', 'tilemerge', 'slidetiles', 'lightflip',
    'safesweep', 'disctower', 'wordguess', 'wordladder', 'letterballoons', 'wordhive',
    'codecracker', 'pixellogic', 'tubetint', 'piecepack', 'crateshift', 'conduit',
];
const NEW_WAVE = new Set(NEW_WAVE_TYPES);

export const EVENT_TYPES = new Set(['start', 'complete', 'fail', 'abandon', 'hint', 'undo', 'error']);
export const PLATFORMS = new Set(['android', 'web']);
const PUZZLE_RE = /^[a-z0-9_]{1,40}$/;
const DAY = 24 * 3600 * 1000;
const INT_MAX = 2147483647;

const str = (v, max) => (typeof v === 'string' && v.length > 0 ? v.slice(0, max) : null);
function int(v) {
    if (typeof v !== 'number' || !Number.isFinite(v)) return null;
    const n = Math.trunc(v);
    return n < 0 || n > INT_MAX ? null : n;
}

/** Returns { installId, platform, appVersion, events:[row fragments] } or null when malformed. */
export function validatePayload(body, now = Date.now()) {
    if (!body || typeof body !== 'object' || Array.isArray(body)) return null;
    const installId = typeof body.installId === 'string' ? body.installId : '';
    if (installId.length < 1 || installId.length > 64) return null;
    if (!PLATFORMS.has(body.platform)) return null;
    if (!Array.isArray(body.events) || body.events.length < 1 || body.events.length > 50) return null;
    const events = [];
    for (const e of body.events) {
        if (!e || typeof e !== 'object' || Array.isArray(e)) return null;
        if (!EVENT_TYPES.has(e.type)) return null;
        if (typeof e.puzzleType !== 'string' || !PUZZLE_RE.test(e.puzzleType)) return null;
        let eventTs = null;
        if (typeof e.ts === 'number' && Number.isFinite(e.ts)) {
            eventTs = new Date(Math.min(Math.max(e.ts, now - DAY), now + DAY)).toISOString();
        }
        events.push({
            event_ts: eventTs,
            event_type: e.type,
            puzzle_type: e.puzzleType,
            difficulty: str(e.difficulty, 20),
            session_id: str(e.sessionId, 64),
            duration_ms: int(e.durationMs),
            score: int(e.score),
            moves: int(e.moves),
            hints_used: int(e.hintsUsed),
            mistakes: int(e.mistakes),
            error_message: str(e.errorMessage, 300),
        });
    }
    return { installId, platform: body.platform, appVersion: str(body.appVersion, 32), events };
}

/** In-memory sliding-window limiter (single Fly machine scale; resets on restart). */
export function createLimiter({ now = () => Date.now(), maxKeys = 50000 } = {}) {
    const hits = new Map(); // key -> [{t, n}]
    return {
        /** Consume n units for key; false (and nothing consumed) if it would exceed limit in windowMs. */
        take(key, n, limit, windowMs) {
            const t = now();
            let arr = (hits.get(key) || []).filter((h) => t - h.t < windowMs);
            const used = arr.reduce((s, h) => s + h.n, 0);
            if (used + n > limit) { hits.set(key, arr); return false; }
            arr.push({ t, n });
            hits.set(key, arr);
            if (hits.size > maxKeys) {
                for (const [k, v] of hits) { if (!v.length || t - v[v.length - 1].t > 3600e3) hits.delete(k); }
            }
            return true;
        },
        size: () => hits.size,
    };
}

export const IP_LIMIT = { limit: 60, windowMs: 60e3 };
export const INSTALL_LIMIT = { limit: 300, windowMs: 3600e3 };

export function toRows(v, userId) {
    return v.events.map((e) => ({
        user_id: userId || null,
        install_id: v.installId,
        platform: v.platform,
        app_version: v.appVersion,
        ...e,
    }));
}

const rate = (num, den) => (den > 0 ? num / den : null);

/** Turn the puzzle_monitoring_summary RPC payload into the admin response. */
export function buildAdminPuzzles(raw, { days, platform }, now = Date.now()) {
    const rows = new Map((raw?.puzzles || []).map((p) => [p.puzzleType, p]));
    for (const t of NEW_WAVE_TYPES) if (!rows.has(t)) rows.set(t, { puzzleType: t });
    const byPuzzle = [...rows.values()].map((p) => {
        const starts = Number(p.starts) || 0, completes = Number(p.completes) || 0;
        const errors = Number(p.errors) || 0, hints = Number(p.hints) || 0;
        return {
            puzzleType: p.puzzleType,
            isNewWave: NEW_WAVE.has(p.puzzleType),
            starts, completes,
            fails: Number(p.fails) || 0,
            abandons: Number(p.abandons) || 0,
            hints, errors,
            completionRate: rate(completes, starts),
            medianDurationMs: p.medianDurationMs == null ? null : Math.round(Number(p.medianDurationMs)),
            avgHintsPerPlay: rate(hints, starts),
            errorRate: rate(errors, starts),
            platforms: { android: Number(p.android) || 0, web: Number(p.web) || 0 },
            lastSeen: p.lastSeen || null,
        };
    }).sort((a, b) => b.starts - a.starts || a.puzzleType.localeCompare(b.puzzleType));
    const sum = (k) => byPuzzle.reduce((s, p) => s + p[k], 0);
    return {
        generatedAt: new Date(now).toISOString(),
        days, platform,
        totals: {
            starts: sum('starts'), completes: sum('completes'), fails: sum('fails'),
            abandons: sum('abandons'), errors: sum('errors'),
            activeInstalls: Number(raw?.activeInstalls) || 0,
        },
        byPuzzle,
        daily: raw?.daily || [],
        topErrors: raw?.topErrors || [],
        alerts: computeAlerts(byPuzzle, days, platform),
    };
}

export function computeAlerts(byPuzzle, days = 7, platform = 'all') {
    const alerts = [];
    for (const p of byPuzzle) {
        if (p.starts >= 20) {
            if (p.errorRate != null && p.errorRate > 0.05) {
                alerts.push({ severity: 'critical', puzzleType: p.puzzleType, message: `Error rate ${(p.errorRate * 100).toFixed(1)}% (${p.errors} errors / ${p.starts} starts)` });
            }
            if (p.completionRate != null && p.completionRate < 0.2) {
                alerts.push({ severity: 'warning', puzzleType: p.puzzleType, message: `Completion rate ${(p.completionRate * 100).toFixed(1)}% (${p.completes} / ${p.starts} starts)` });
            }
        }
        // "0 starts in 7 days" is only meaningful when the window covers 7 days.
        if (p.isNewWave && p.starts === 0 && days >= 7) {
            alerts.push({ severity: 'info', puzzleType: p.puzzleType, message: `No starts in the last ${days} days${platform === 'all' ? '' : ` on ${platform}`}` });
        }
    }
    const order = { critical: 0, warning: 1, info: 2 };
    return alerts.sort((a, b) => order[a.severity] - order[b.severity]);
}
