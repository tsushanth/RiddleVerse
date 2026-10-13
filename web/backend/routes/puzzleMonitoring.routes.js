// POST /api/puzzle-events  (public ingest, mounted BEFORE the global 50mb json parser)
// GET  /api/dash/puzzles   (admin; same gate as /api/dash/overview: isAdminRequest, 404 otherwise)
import express from 'express';
import { verifyBearerToken, serviceClient } from '../auth/verifyIdentity.js';
import { isAdminRequest } from './dashProxy.js';
import {
    validatePayload, createLimiter, toRows, buildAdminPuzzles, IP_LIMIT, INSTALL_LIMIT,
} from '../services/puzzleMonitoring.js';

const MAX_BODY = '64kb';

/** deps are injectable for tests. */
export function createPuzzleMonitoringRouters(deps = {}) {
    const d = {
        verifyBearerToken, getClient: serviceClient, isAdmin: isAdminRequest,
        limiter: createLimiter(), now: () => Date.now(), ...deps,
    };

    const ingest = express.Router();
    ingest.use(express.json({ limit: MAX_BODY }));
    ingest.post('/', async (req, res) => {
        const ip = req.ip || 'unknown';
        if (!d.limiter.take(`ip:${ip}`, 1, IP_LIMIT.limit, IP_LIMIT.windowMs)) {
            return res.status(429).json({ error: 'rate_limited' });
        }
        const v = validatePayload(req.body, d.now());
        if (!v) return res.status(400).json({ error: 'bad_request' });
        if (!d.limiter.take(`inst:${v.installId}`, v.events.length, INSTALL_LIMIT.limit, INSTALL_LIMIT.windowMs)) {
            return res.status(429).json({ error: 'rate_limited' });
        }
        let userId = null;
        if (req.headers.authorization) {
            try { userId = (await d.verifyBearerToken(req.headers.authorization))?.uid || null; } catch { userId = null; }
        }
        try {
            const { error } = await d.getClient().from('puzzle_events').insert(toRows(v, userId));
            if (error) throw error;
        } catch (e) {
            console.error('puzzle-events insert failed:', e?.message || e);
            return res.status(500).json({ error: 'server_error' });
        }
        res.status(202).json({ accepted: v.events.length });
    });
    // Body-parser errors -> contract statuses.
    ingest.use((err, req, res, next) => {
        if (err?.type === 'entity.too.large') return res.status(413).json({ error: 'payload_too_large' });
        if (err?.type === 'entity.parse.failed' || err instanceof SyntaxError) return res.status(400).json({ error: 'bad_request' });
        next(err);
    });

    const admin = express.Router();
    admin.get('/puzzles', async (req, res) => {
        if (!(await d.isAdmin(req))) return res.status(404).send('Not found');
        const days = Math.min(Math.max(parseInt(req.query.days, 10) || 7, 1), 90);
        const platform = ['android', 'web'].includes(req.query.platform) ? req.query.platform : 'all';
        try {
            const { data, error } = await d.getClient().rpc('puzzle_monitoring_summary', { p_days: days, p_platform: platform });
            if (error) throw error;
            res.set('Cache-Control', 'no-store').json(buildAdminPuzzles(data, { days, platform }, d.now()));
        } catch (e) {
            console.error('puzzle summary failed:', e?.message || e);
            res.status(500).json({ error: 'server_error' });
        }
    });
    return { ingest, admin };
}

const { ingest, admin } = createPuzzleMonitoringRouters();
export const puzzleEventsRouter = ingest;
export const puzzleAdminRouter = admin;
