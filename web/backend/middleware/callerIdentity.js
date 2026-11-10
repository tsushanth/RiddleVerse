import { verifyToken } from '../auth/verifyIdentity.js';
import { hasNotificationAdminKey } from './notificationAuth.js';

function bearerToken(req) {
    const header = req.headers?.authorization;
    if (typeof header !== 'string' || !header.startsWith('Bearer ')) return null;
    const token = header.slice(7).trim();
    return token || null;
}

/**
 * The caller's verified identity from `Authorization: Bearer <Firebase or Supabase token>`, or null.
 *
 * Never throws: a missing, malformed, expired or unlinked token is simply "no verified identity", so callers decide
 * what an anonymous request may do. An email is only reported when the identity provider vouches for it
 * (Firebase `email_verified`, Supabase confirmed address); an unverified address proves nothing about ownership.
 */
export async function verifiedCaller(req, verify = verifyToken) {
    const token = bearerToken(req);
    if (!token) return null;
    try {
        const identity = await verify(token);
        if (!identity || !identity.uid) return null;
        const raw = identity.raw || {};
        const vouched = identity.provider === 'supabase'
            ? Boolean(raw.email_confirmed_at || raw.confirmed_at)
            : raw.email_verified === true;
        const email = vouched && typeof raw.email === 'string' ? raw.email.trim().toLowerCase() : null;
        return { uid: String(identity.uid), provider: identity.provider, email };
    } catch {
        return null;
    }
}

/** True when the request is made by a verified user whose own email is `email` (case-insensitive). */
export async function callerOwnsEmail(req, email, verify) {
    if (typeof email !== 'string' || !email) return false;
    const caller = await verifiedCaller(req, verify);
    return Boolean(caller && caller.email && caller.email === email.trim().toLowerCase());
}

/** True when the request is made by the verified user with Firebase uid `uid`. */
export async function callerIsUid(req, uid, verify) {
    if (typeof uid !== 'string' || !uid) return false;
    const caller = await verifiedCaller(req, verify);
    return Boolean(caller && caller.uid === uid);
}

/** Express middleware: let the request through only with the notification/ops admin key (alias of requireNotificationAdmin semantics). */
export { requireNotificationAdmin as requireOpsAdmin } from './notificationAuth.js';

export { hasNotificationAdminKey };
