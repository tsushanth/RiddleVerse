// Telegram bot + app account linking. Mounted at '/' (the paths are absolute, one of them is outside /api).
//   POST /api/telegram/webhook      Telegram -> us (secret token header, ack first, work in the background)
//   POST /api/telegram/score        Mini App / play page -> us (initData verified)
//   POST /api/telegram/link/redeem  the Android app -> us (one-time code, optional Firebase bearer)
//   POST /api/telegram/daily-push   external scheduler -> us (shared secret header)
//   GET  /app/link?code=...         https link the bot sends; opens the app or Google Play
import express from 'express';
import { createTelegramIntegration } from '../services/telegram/index.js';

/** deps.getIntegration is injectable for tests; the default builds the real integration lazily on the first request. */
export function createTelegramRouter({ getIntegration } = {}) {
    const router = express.Router();
    let integration = null;

    const get = getIntegration || (async () => {
        if (integration) return integration;
        // Lazy imports keep a missing secret from breaking boot and keep smoke tests (no env) working.
        const { supabase } = await import('../config/database.js');
        integration = createTelegramIntegration({
            supabase,
            getFirebaseAuth: async () => (await import('../config/firebaseAdmin.js')).admin.auth(),
        });
        return integration;
    });

    const wrap = (name) => async (req, res) => {
        try {
            const t = await get();
            if (!t.config.botToken) return res.status(503).json({ error: 'bot_not_configured' });
            await t.handlers[name](req, res);
        } catch (e) {
            console.error(`[telegram] ${name} failed:`, e?.message);
            if (!res.headersSent) res.status(500).json({ error: 'server_error' });
        }
    };

    router.post('/api/telegram/webhook', express.json({ limit: '256kb' }), wrap('webhook'));
    router.post('/api/telegram/score', express.json({ limit: '8kb' }), wrap('score'));
    router.post('/api/telegram/link/redeem', express.json({ limit: '2kb' }), wrap('redeem'));
    router.post('/api/telegram/daily-push', wrap('dailyPush'));
    router.get('/app/link', wrap('linkPage'));
    return router;
}

export default createTelegramRouter();
