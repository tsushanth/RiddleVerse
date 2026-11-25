// Several tables use the account email as the user id (leaderboard, custom_leaderboard, puzzle_sets.creator).
// Anything a response shows to someone other than that account goes through here.
//
//   publicUserId(v)  ids: an email becomes a stable opaque id; Firebase uids and other ids pass through unchanged, so
//                    clients that compare uids keep highlighting the viewer's own row.
//   publicName(v)    display text: an email-shaped part becomes the masked form ("john***").
import crypto from 'crypto';
import { verifiedCaller } from '../middleware/callerIdentity.js';

const EMAIL_SHAPED = /[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}/g;

// Keyed, so an id cannot be confirmed by hashing a guessed address. Derived from an existing stable server secret with its
// own label; without one (local runs, tests) a per-process key is used.
const key = process.env.RV_CHANNEL_REF_KEY || crypto.randomBytes(32);

export function publicUserId(value) {
    if (typeof value !== 'string' || !value.includes('@')) return value;
    const digest = crypto.createHmac('sha256', key).update(`public-user-id:v1:${value.trim().toLowerCase()}`).digest('hex');
    return `u_${digest.slice(0, 24)}`;
}

function mask(email) {
    const local = email.split('@')[0];
    return `${local.slice(0, local.length <= 3 ? 1 : 4)}***`;
}

export function publicName(value) {
    if (typeof value !== 'string' || !value.includes('@')) return value;
    const masked = value.replace(EMAIL_SHAPED, mask);
    // Anything with an "@" that still looks like an address (unusual characters, no TLD) is cut at the "@".
    return /\S@\S/.test(masked) ? mask(masked) : masked;
}

/**
 * For lists that mix accounts: returns a function that yields { isCurrentUser: true } for the row that belongs to the
 * signed-in caller (verified bearer token only; without one nothing is marked).
 */
export async function ownRowMarker(req, verify) {
    const caller = await verifiedCaller(req, verify);
    return (id) => {
        if (!caller || typeof id !== 'string') return {};
        const mine = id === caller.uid || Boolean(caller.email && id.trim().toLowerCase() === caller.email);
        return mine ? { isCurrentUser: true } : {};
    };
}
