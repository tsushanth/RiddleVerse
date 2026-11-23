// Wires the Telegram integration from the environment. Everything is lazy: with no bot token the routes answer 503 and the
// rest of the backend is unaffected.
import { loadCatalog } from '../playCatalog.js';
import { createI18n } from './i18n.js';
import { createTelegramApi } from './telegramApi.js';
import { createSupabaseStore } from './store.js';
import { createLinkService } from './linkService.js';
import { createBot } from './bot.js';
import { createTelegramHandlers } from './handlers.js';
import { createLimiter } from '../puzzleMonitoring.js';
import { createChannelEvents } from './channelEvents.js';

export const PLAY_STORE_BASE = 'https://play.google.com/store/apps/details?id=com.kreativekoala.riddleverse';

export function loadConfig(env = process.env) {
    return {
        botToken: env.RIDDLEVERSE_BOT_TOKEN || env.TELEGRAM_BOT_TOKEN || '',
        webhookSecret: env.TELEGRAM_WEBHOOK_SECRET || '',
        cronSecret: env.TELEGRAM_CRON_SECRET || '',
        // Key for the chat_ref / tg_user_ref hashes in rv_channel_events. Set RV_CHANNEL_REF_KEY to keep refs stable across a bot token rotation.
        refKey: env.RV_CHANNEL_REF_KEY || (env.RIDDLEVERSE_BOT_TOKEN || env.TELEGRAM_BOT_TOKEN ? `rv-ref:${env.RIDDLEVERSE_BOT_TOKEN || env.TELEGRAM_BOT_TOKEN}` : ''),
        username: env.TELEGRAM_BOT_USERNAME || 'Riddleverse_bot',
        miniAppShortName: env.TELEGRAM_MINIAPP_SHORTNAME || '',
        origin: env.TELEGRAM_SITE_ORIGIN || 'https://puzzleverseai.com',
        playStoreBase: PLAY_STORE_BASE,
    };
}

export function createTelegramIntegration({ env = process.env, supabase, getFirebaseAuth, fetchImpl, now, log } = {}) {
    const config = loadConfig(env);
    const i18n = createI18n();
    const catalog = loadCatalog();
    const limiter = createLimiter();
    const store = createSupabaseStore(supabase);
    const api = config.botToken ? createTelegramApi({ token: config.botToken, fetchImpl }) : null;
    const linkService = createLinkService({ store, getFirebaseAuth, now });
    const events = createChannelEvents({ store, refKey: config.refKey, now, log });
    const bot = api ? createBot({ api, store, i18n, linkService, catalog, config, now, limiter, log, events }) : null;
    const handlers = createTelegramHandlers({ bot, api, store, i18n, linkService, catalog, config, now, limiter, log, events });
    return { config, i18n, catalog, store, api, linkService, bot, handlers, limiter, events };
}
