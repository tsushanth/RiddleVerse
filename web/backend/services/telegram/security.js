// Security primitives for the Telegram integration (no network, no database; all unit tested).
import crypto from 'crypto';

/** Constant-time string compare (digests first, so the length of the secret is not leaked either). */
export function safeEqual(a, b) {
    if (typeof a !== 'string' || typeof b !== 'string') return false;
    const ha = crypto.createHash('sha256').update(a).digest();
    const hb = crypto.createHash('sha256').update(b).digest();
    return crypto.timingSafeEqual(ha, hb) && a.length === b.length;
}

/**
 * Validates Telegram Mini App initData (the signed query string window.Telegram.WebApp.initData).
 * Returns { user, startParam, authDate } or null. Algorithm: HMAC_SHA256(key = HMAC_SHA256("WebAppData", botToken)) over the
 * sorted "key=value" lines of every field except `hash`. Rejects data older than maxAgeSec (default 24 h) or from the future.
 */
export function verifyInitData(initData, botToken, { now = () => Date.now(), maxAgeSec = 86400 } = {}) {
    try {
        if (typeof initData !== 'string' || !initData || initData.length > 4096 || !botToken) return null;
        const params = new URLSearchParams(initData);
        const hash = params.get('hash');
        if (!hash || !/^[0-9a-f]{64}$/i.test(hash)) return null;
        const lines = [];
        for (const [k, v] of params.entries()) if (k !== 'hash') lines.push(`${k}=${v}`);
        lines.sort();
        const secret = crypto.createHmac('sha256', 'WebAppData').update(botToken).digest();
        const expected = crypto.createHmac('sha256', secret).update(lines.join('\n')).digest('hex');
        if (!safeEqual(expected, hash.toLowerCase())) return null;
        const authDate = Number(params.get('auth_date'));
        const nowSec = Math.floor(now() / 1000);
        if (!Number.isFinite(authDate) || authDate <= 0 || nowSec - authDate > maxAgeSec || authDate - nowSec > 300) return null;
        const user = JSON.parse(params.get('user') || 'null');
        if (!user || !Number.isSafeInteger(user.id) || user.id <= 0) return null;
        return { user, startParam: params.get('start_param') || null, authDate };
    } catch {
        return null;
    }
}

/** Test helper and reference implementation of what Telegram does when it signs initData. */
export function signInitDataForTest(fields, botToken) {
    const lines = Object.entries(fields).map(([k, v]) => `${k}=${v}`).sort();
    const secret = crypto.createHmac('sha256', 'WebAppData').update(botToken).digest();
    const hash = crypto.createHmac('sha256', secret).update(lines.join('\n')).digest('hex');
    const p = new URLSearchParams();
    for (const [k, v] of Object.entries(fields)) p.set(k, v);
    p.set('hash', hash);
    return p.toString();
}

const CTX_KINDS = new Set(['p', 'd']); // p = play, d = daily
const SLUG_RE = /^[a-z]{2,24}$/;
const CHAT_RE = /^m?\d{1,20}$/;

function ctxKey(botToken) {
    return crypto.createHash('sha256').update('rv-ctx:' + botToken).digest();
}

/**
 * Signed game context carried in the Mini App start_param / page URL: "<kind>_<slug>_<chat>_<sig>". Chat ids may be
 * negative (groups): '-' becomes 'm' because start_param only allows [A-Za-z0-9_-]. The signature stops a player from
 * pointing score announcements at a chat the bot never offered the game in.
 */
export function signContext({ kind, slug, chatId }, botToken) {
    if (!CTX_KINDS.has(kind) || !SLUG_RE.test(slug)) throw new Error('bad context');
    const chat = String(chatId).replace('-', 'm');
    if (!CHAT_RE.test(chat)) throw new Error('bad chat id');
    const sig = crypto.createHmac('sha256', ctxKey(botToken)).update(`${kind}|${slug}|${chat}`).digest('hex').slice(0, 20);
    return `${kind}_${slug}_${chat}_${sig}`;
}

/** Returns { kind, slug, chatId } (chatId as a string, with its sign restored) or null. */
export function verifyContext(token, botToken) {
    if (typeof token !== 'string' || token.length > 120) return null;
    const parts = token.split('_');
    if (parts.length !== 4) return null;
    const [kind, slug, chat, sig] = parts;
    if (!CTX_KINDS.has(kind) || !SLUG_RE.test(slug) || !CHAT_RE.test(chat) || !/^[0-9a-f]{20}$/.test(sig)) return null;
    const expected = crypto.createHmac('sha256', ctxKey(botToken)).update(`${kind}|${slug}|${chat}`).digest('hex').slice(0, 20);
    if (!safeEqual(expected, sig)) return null;
    return { kind, slug, chatId: chat.replace('m', '-') };
}

/** One-time link code: 192 random bits, URL-safe. Only its SHA-256 is stored. */
export function newLinkCode() {
    return crypto.randomBytes(24).toString('base64url');
}
export function hashLinkCode(code) {
    return crypto.createHash('sha256').update(String(code)).digest('hex');
}
export const LINK_CODE_RE = /^[A-Za-z0-9_-]{32}$/;
