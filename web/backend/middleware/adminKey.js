import { createHash, timingSafeEqual } from 'node:crypto';

/**
 * Admin key check that FAILS CLOSED.
 *
 * The old inline check was `if (adminKey !== process.env.ADMIN_KEY) reject`. When ADMIN_KEY is not set (it is not set on the
 * Fly app) and the caller sends no key, that is `undefined !== undefined`, i.e. false, so the request was ALLOWED:
 * /api/admin/*, /api/daily-puzzle-cache/admin/* (cache wipe, LLM-backed refresh) and /api/ltv/* (audience export) were open.
 *
 * Here an unset/empty ADMIN_KEY rejects everything, a missing or non-string key rejects, and the compare is constant time.
 */
export function isAdminKey(provided, expected = process.env.ADMIN_KEY) {
    if (typeof expected !== 'string' || expected.length === 0) return false;
    if (typeof provided !== 'string' || provided.length === 0) return false;
    const a = createHash('sha256').update(provided).digest();
    const b = createHash('sha256').update(expected).digest();
    return timingSafeEqual(a, b);
}
