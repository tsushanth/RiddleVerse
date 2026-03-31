// payoutAuth.js - Firebase token verification for payout routes
import { admin } from '../config/firebaseAdmin.js';

/**
 * Middleware that verifies the Firebase ID token and ensures
 * the authenticated user matches the creatorId in the request.
 * Applied to payout routes that move real money.
 */
export async function verifyCreator(req, res, next) {
    const authHeader = req.headers.authorization;
    if (!authHeader || !authHeader.startsWith('Bearer ')) {
        return res.status(401).json({ error: 'Missing authorization token' });
    }

    const idToken = authHeader.split('Bearer ')[1];
    if (!idToken) {
        return res.status(401).json({ error: 'Invalid authorization header' });
    }

    try {
        const decoded = await admin.auth().verifyIdToken(idToken);
        const creatorId = req.body.creatorId || req.query.creatorId;

        if (!creatorId) {
            return res.status(400).json({ error: 'creatorId is required' });
        }

        if (decoded.uid !== creatorId) {
            console.warn(`[PayoutAuth] UID mismatch: token=${decoded.uid}, creatorId=${creatorId}`);
            return res.status(403).json({ error: 'Creator ID does not match authenticated user' });
        }

        req.verifiedUserId = decoded.uid;
        next();
    } catch (error) {
        console.error('[PayoutAuth] Token verification failed:', error.message);
        return res.status(401).json({ error: 'Invalid or expired token' });
    }
}
