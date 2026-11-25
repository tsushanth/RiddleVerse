// POST /share-riddle sends mail from our address on behalf of a caller the shipped apps do not authenticate. These keep it
// to one invitation per request, with nothing of the caller's choosing in it beyond two short plain-text names.
import rateLimit from 'express-rate-limit';

const ADDRESS = /^[A-Za-z0-9._%+-]{1,64}@[A-Za-z0-9](?:[A-Za-z0-9-]*[A-Za-z0-9])?(?:\.[A-Za-z0-9-]+)+$/;

/** Exactly one address (no lists, display names or separators), or null. */
export function singleRecipient(value) {
    if (typeof value !== 'string') return null;
    const v = value.trim();
    return v.length <= 254 && ADDRESS.test(v) ? v : null;
}

const HTML = { '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' };

/** A short name for the mail body: links and addresses removed, HTML escaped. */
export function mailName(value, fallback) {
    const plain = String(typeof value === 'string' ? value : '')
        .replace(/(?:[a-z][a-z0-9+.-]*:\/\/|www\.)\S*/gi, '')
        .replace(/\S+@\S+/g, '')
        .replace(/\b[\w-]+\.(?:com|net|org|io|co|app|ly|me|info|xyz|top|ru|cn|in|uk)\b(?:\/\S*)?/gi, '')
        .replace(/[\u0000-\u001f\u007f]+/g, ' ')
        .replace(/\s+/g, ' ')
        .trim()
        .slice(0, 40);
    return (plain || fallback).replace(/[&<>"']/g, (c) => HTML[c]);
}

const refused = { error: 'Too many share emails. Please try again later.' };
const limiter = (windowMs, max, keyGenerator) => rateLimit({
    windowMs, max, keyGenerator, standardHeaders: false, legacyHeaders: false, message: refused, validate: false,
});

/** Per machine: 10 an hour per network address, 3 a day to one recipient, 100 a day in total. */
export function shareLimiters() {
    const HOUR = 60 * 60 * 1000;
    return [
        limiter(HOUR, 10, (req) => `ip:${req.ip}`),
        limiter(24 * HOUR, 3, (req) => `to:${String(req.body?.recipientEmail || '').trim().toLowerCase()}`),
        limiter(24 * HOUR, 100, () => 'all'),
    ];
}
