import { isAdminKey } from './adminKey.js';

/**
 * Express middleware: the x-admin-key header must carry ADMIN_KEY (fails closed). For routes that scan large Firestore
 * collections: the project is on the free daily read quota, and an anonymous caller must not be able to use it up.
 */
export function requireAdminKey(req, res, next) {
    if (!isAdminKey(req.headers?.['x-admin-key'])) {
        return res.status(401).json({ success: false, error: 'Unauthorized' });
    }
    next();
}
