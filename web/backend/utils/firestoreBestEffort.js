// Firestore only holds bookkeeping and small per-player documents; puzzles are served without it. When the free daily
// quota is used up the SDK keeps retrying a rejected read for many seconds, longer than the app waits, so a request
// that can do without the answer gets a deadline, and after a quota rejection such reads are skipped for a while.
import { readQuotaRejectedAt } from './firestoreQuotaGuard.js';
import { isFirestoreQuotaError } from './failureReporter.js';

export const READ_DEADLINE_MS = 4000;
export const SKIP_AFTER_QUOTA_MS = 60_000;

const unavailable = (message, extra) => Object.assign(new Error(message), extra);

/** Runs read() with a deadline. Rejects at once, without calling it, shortly after a read was refused for quota. */
export async function firestoreRead(read, { timeoutMs = READ_DEADLINE_MS, now = Date.now } = {}) {
    if (now() - readQuotaRejectedAt() < SKIP_AFTER_QUOTA_MS) {
        throw unavailable('Firestore read skipped: quota used up', { quotaSkip: true });
    }
    let timer;
    try {
        return await Promise.race([
            read(),
            new Promise((_, reject) => { timer = setTimeout(() => reject(unavailable('Firestore read timed out', { timeout: true })), timeoutMs); }),
        ]);
    } finally {
        clearTimeout(timer);
    }
}

export const isFirestoreUnavailable = (e) => Boolean(e?.quotaSkip || e?.timeout || isFirestoreQuotaError(e));

const seen = new Set();
const MAX_SEEN = 5000;

/** Creates a player's bookkeeping document if it is missing. Reads it at most once per process. */
export async function ensureUserDoc(ref, id, defaults, opts) {
    if (seen.has(id)) return;
    const doc = await firestoreRead(() => ref.get(), opts);
    if (!doc.exists) await ref.set(defaults);
    if (seen.size >= MAX_SEEN) seen.clear();
    seen.add(id);
}
