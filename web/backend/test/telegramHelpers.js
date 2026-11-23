// Shared fakes for the Telegram tests: no network, no database.
import { createI18n } from '../services/telegram/i18n.js';
import { loadCatalog } from '../services/playCatalog.js';
import { createMemoryStore } from '../services/telegram/store.js';
import { createLinkService } from '../services/telegram/linkService.js';
import { createBot } from '../services/telegram/bot.js';
import { createTelegramHandlers } from '../services/telegram/handlers.js';
import { createLimiter } from '../services/puzzleMonitoring.js';

export const TOKEN = '111111:TEST-TOKEN-not-real';
export const CONFIG = {
    botToken: TOKEN, webhookSecret: 'whsec', cronSecret: 'cronsec', username: 'Riddleverse_bot', miniAppShortName: '',
    origin: 'https://puzzleverseai.com', playStoreBase: 'https://play.google.com/store/apps/details?id=com.kreativekoala.riddleverse',
};

export function fakeApi() {
    const calls = [];
    let failWith = null;
    const rec = (method) => async (...args) => {
        calls.push({ method, args });
        if (failWith) throw failWith;
        return { message_id: calls.length };
    };
    return {
        calls,
        failNextWith(e) { failWith = e; },
        sendMessage: rec('sendMessage'), editMessageText: rec('editMessageText'), answerCallbackQuery: rec('answerCallbackQuery'),
        sent: () => calls.filter((c) => c.method === 'sendMessage').map((c) => ({ chatId: c.args[0], text: c.args[1], extra: c.args[2] || {} })),
    };
}

export function fakeFirebaseAuth({ validTokens = {} } = {}) {
    const minted = [];
    return {
        minted,
        verifyIdToken: async (t) => { if (!validTokens[t]) throw new Error('bad token'); return { uid: validTokens[t] }; },
        createCustomToken: async (uid, claims) => { minted.push({ uid, claims }); return `custom-for-${uid}`; },
    };
}

export function build({ now = () => Date.UTC(2026, 10, 22, 12, 0, 0), auth = fakeFirebaseAuth() } = {}) {
    const i18n = createI18n();
    const catalog = loadCatalog();
    const store = createMemoryStore({ now });
    const api = fakeApi();
    const limiter = createLimiter({ now });
    const linkService = createLinkService({ store, getFirebaseAuth: async () => auth, now });
    const quiet = { warn() {}, error() {}, log() {} };
    const bot = createBot({ api, store, i18n, linkService, catalog, config: CONFIG, now, limiter, log: quiet });
    const handlers = createTelegramHandlers({ bot, api, store, i18n, linkService, catalog, config: CONFIG, now, limiter, log: quiet, sleepFn: async () => {} });
    return { i18n, catalog, store, api, limiter, linkService, bot, handlers, auth, now, config: CONFIG };
}

let uid = 1000;
export const msg = (text, { from = {}, chat = {}, id = ++uid } = {}) => ({
    update_id: id,
    message: { message_id: id, text, from: { id: 42, first_name: 'Ana', language_code: 'en', is_bot: false, ...from }, chat: { id: 42, type: 'private', ...chat } },
});
export const cb = (data, { from = {}, chat = {}, id = ++uid } = {}) => ({
    update_id: id,
    callback_query: { id: `cq${id}`, data, from: { id: 42, first_name: 'Ana', language_code: 'en', is_bot: false, ...from }, message: { message_id: 7, chat: { id: 42, type: 'private', ...chat } } },
});

export function fakeRes() {
    const res = { statusCode: 200, body: undefined, headers: {}, sent: false };
    res.status = (c) => { res.statusCode = c; return res; };
    res.json = (b) => { res.body = b; res.sent = true; return res; };
    res.type = () => res;
    res.send = (b) => { res.body = b; res.sent = true; return res; };
    res.set = (k, v) => { res.headers[k] = v; return res; };
    return res;
}
