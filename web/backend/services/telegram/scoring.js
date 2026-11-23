// Score rules for games played inside Telegram. The page reports only what happened (game, difficulty, time); the score is
// computed here, so a tampered client cannot post an arbitrary number. Bounds: 1..400.
import crypto from 'crypto';

const BASE = { easy: 100, medium: 200, hard: 300 };
const MIN_MS = 3000;          // faster than this is not a real solve
const MAX_MS = 3600 * 1000;
export const MAX_SCORE = 400;

export function computeScore({ difficulty, durationMs }) {
    const d = Number(durationMs);
    if (!Number.isFinite(d) || d < 0) return null;
    const ms = Math.min(Math.max(d, MIN_MS), MAX_MS);
    const base = BASE[difficulty] ?? 150;
    const bonus = Math.round(100 * Math.max(0, 1 - ms / 600000)); // full bonus at 0 s, none after 10 minutes
    return Math.min(MAX_SCORE, base + bonus);
}

// chat_scores.game_id is a UUID, but web games are identified by a slug: derive a stable UUIDv5 from it.
const NAMESPACE = Buffer.from('b6a3c2f0d4e14b0c9a7e5f3d21c8e9a1', 'hex');
export function gameUuid(key) {
    const h = crypto.createHash('sha1').update(NAMESPACE).update(`rv-web-play:${key}`).digest();
    h[6] = (h[6] & 0x0f) | 0x50;
    h[8] = (h[8] & 0x3f) | 0x80;
    const x = h.subarray(0, 16).toString('hex');
    return `${x.slice(0, 8)}-${x.slice(8, 12)}-${x.slice(12, 16)}-${x.slice(16, 20)}-${x.slice(20, 32)}`;
}

/** The leaderboard key: a normal game, or "daily:<number>" so each day has its own board. */
export function boardKey(kind, slug, dailyNumber) {
    return kind === 'd' ? `daily:${dailyNumber}` : slug;
}
