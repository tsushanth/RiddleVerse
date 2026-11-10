import { isAdminKey } from './adminKey.js';

export const NOTIFICATION_KEY_HEADER = 'x-notification-admin-key';

/**
 * True when the request carries the notification admin key.
 *
 * Uses a dedicated secret (NOTIFICATION_ADMIN_KEY), not ADMIN_KEY, so that holding one of them never grants the other.
 * Fails closed: if NOTIFICATION_ADMIN_KEY is unset or empty nothing is authorised. The compare is constant time.
 */
export function hasNotificationAdminKey(req) {
    return isAdminKey(req.headers?.[NOTIFICATION_KEY_HEADER], process.env.NOTIFICATION_ADMIN_KEY);
}

/** Express middleware: reject with 401 unless the notification admin key is presented. */
export function requireNotificationAdmin(req, res, next) {
    if (!hasNotificationAdminKey(req)) {
        return res.status(401).json({ success: false, error: 'Unauthorized' });
    }
    next();
}
