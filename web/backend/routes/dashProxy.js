// GET /api/dash/overview: verifies the caller is the allowlisted Google account (Firebase ID token),
// then proxies the central app-failure-reporter Worker with a server-side secret.
// Requires env: ADMIN_DASH_SECRET (same value as the Worker's DASH_SECRET). Optional: ADMIN_EMAILS.
import express from 'express';
import { admin } from '../config/firebaseAdmin.js';

const router = express.Router();
const APP = 'riddleverse';
const WORKER = 'https://app-failure-reporter.t-sushanth.workers.dev';

/** True only for an allowlisted, verified email that signed in with Google. */
export async function isAdminRequest(req) {
    const header = req.headers.authorization || '';
    if (!header.startsWith('Bearer ')) return false;
    try {
        const decoded = await admin.auth().verifyIdToken(header.slice(7));
        if (!decoded.email || decoded.email_verified !== true) return false;
        if (decoded.firebase?.sign_in_provider !== 'google.com') return false;
        const allowed = (process.env.ADMIN_EMAILS || 't.sushanth@gmail.com').split(',').map((e) => e.trim().toLowerCase());
        return allowed.includes(decoded.email.toLowerCase());
    } catch {
        return false;
    }
}

router.get('/overview', async (req, res) => {
    if (!(await isAdminRequest(req))) return res.status(404).send('Not found');
    const secret = process.env.ADMIN_DASH_SECRET;
    if (!secret) return res.status(500).json({ error: 'ADMIN_DASH_SECRET not configured' });

    const hours = Math.min(Math.max(Number(req.query.hours) || 24, 1), 720);
    const sc = req.query.scope === 'app' || req.query.scope === 'api' ? `&scope=${req.query.scope}` : '';
    try {
        const upstream = await fetch(`${WORKER}/admin/api/overview?app=${APP}&hours=${hours}${sc}`, {
            headers: { 'X-Dash-Secret': secret },
            signal: AbortSignal.timeout(15000),
        });
        if (!upstream.ok) return res.status(502).json({ error: `upstream ${upstream.status}` });
        res.set('Cache-Control', 'no-store').json(await upstream.json());
    } catch {
        res.status(502).json({ error: 'upstream unreachable' });
    }
});

export default router;
