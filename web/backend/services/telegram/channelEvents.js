// Usage events for the non-app channels (today: the Telegram bot), written to public.rv_channel_events.
//
// Why a separate table: public.puzzle_events is the APP analytics table (its DAU, funnel and retention queries read every
// row that matches their event types / platform). Bot interactions must not leak into those numbers, so they live here.
//
// Privacy: no message text, no names, no usernames, no raw Telegram ids. chat_ref / tg_user_ref are keyed hashes (HMAC-SHA256
// with a server-side key, 24 hex chars): stable, so one person can be followed across events, but not reversible without
// the key. They are pseudonymous (not anonymous): treat them as personal data, keep the key secret, purge old rows.
// props are a small whitelist-sanitised object of labels and counters (see cleanProps).
//
// Reliability: emit() NEVER throws and NEVER awaits. The insert runs in the background; any failure (table missing, DB down)
// switches emission off for a while (a missing table: 10 minutes, anything else: 30 seconds) so a reply is never slowed or
// broken, and the log line is written once per back-off, not once per event.
import crypto from 'crypto';

export const CHANNEL = 'telegram';

/** Every event name the bot may write. Anything else is dropped (and counted in stats().dropped). */
export const EVENTS = Object.freeze([
    'start',            // /start (props: src, action, new_user, chat_type)
    'help',
    'daily_open',       // the daily puzzle card was shown (props: via = command|button|start|reminder)
    'game_open',        // a game card was shown (props: game, via)
    'game_menu',        // the game picker was shown
    'leaderboard_view', // props: game ('daily' or a slug)
    'language_change',  // props: to, from
    'remind_on', 'remind_off',
    'link_issued',      // /link produced a one-time code
    'link_redeemed',    // the app redeemed it (props: mode = signin|linked)
    'link_redeem_failed', // props: error
    'app_cmd', 'create_cmd', // /app, /create (the install pointer)
    'text_message',     // plain text to the bot in a private chat (the text itself is never stored)
    'unknown_command',
    'game_finished',    // a verified Mini App score (props: game, kind, difficulty, score, improved, chat_type, duration_s)
    'bot_blocked', 'bot_unblocked',
    'group_added', 'group_removed',
    'daily_push',       // one per reminder run (props: candidates, sent, gone, failed); no user refs
    'rate_limited',
    'error',            // props: where, code
]);
const EVENT_SET = new Set(EVENTS);

/** Events that mean "a person did something" (used for D1/D7 return). The SQL in docs/channels-metrics.md lists the same set. */
export const USER_ACTION_EVENTS = Object.freeze([
    'start', 'help', 'daily_open', 'game_open', 'game_menu', 'leaderboard_view', 'language_change', 'remind_on', 'remind_off',
    'link_issued', 'app_cmd', 'create_cmd', 'text_message', 'unknown_command', 'game_finished',
]);

const KEY_RE = /^[a-z][a-z0-9_]{0,23}$/;
const MAX_KEYS = 12;
const MAX_STR = 40;

/** Keeps only short scalar values under short snake_case keys. Strings are cut at 40 characters. Always returns a plain object. */
export function cleanProps(props) {
    const out = {};
    if (!props || typeof props !== 'object' || Array.isArray(props)) return out;
    let n = 0;
    for (const [k, v] of Object.entries(props)) {
        if (n >= MAX_KEYS) break;
        if (!KEY_RE.test(k) || v === undefined || v === null) continue;
        if (typeof v === 'string') out[k] = v.slice(0, MAX_STR);
        else if (typeof v === 'boolean') out[k] = v;
        else if (typeof v === 'number' && Number.isFinite(v)) out[k] = Math.round(v * 1000) / 1000;
        else continue;
        n++;
    }
    return out;
}

export const noopChannelEvents = Object.freeze({
    enabled: false, ref: () => null, emit() {}, flush: async () => {}, stats: () => ({ written: 0, dropped: 0, failed: 0 }),
});

const isMissingTable = (e) => {
    const msg = String(e?.message || '');
    return e?.code === '42P01' || e?.code === 'PGRST205' || e?.code === 'PGRST204'
        || (/rv_channel_events/i.test(msg) && /does not exist|schema cache|could not find|not found/i.test(msg));
};

/**
 * @param {object} o
 * @param {{insertChannelEvent:(row:object)=>Promise<void>}} o.store
 * @param {string} o.refKey  secret key for the id hashes (no key: events are off)
 */
export function createChannelEvents({
    store, refKey, now = () => Date.now(), log = console, channel = CHANNEL,
    maxPending = 100, maxPerMinute = 1200, missingTableBackoffMs = 600e3, errorBackoffMs = 30e3,
} = {}) {
    if (!store || typeof store.insertChannelEvent !== 'function' || !refKey) return noopChannelEvents;
    const pending = new Set();
    let offUntil = 0;
    let windowStart = 0;
    let windowCount = 0;
    const counters = { written: 0, dropped: 0, failed: 0 };

    const ref = (id) => {
        if (id === null || id === undefined || id === '') return null;
        return crypto.createHmac('sha256', refKey).update(`${channel}:${String(id)}`).digest('hex').slice(0, 24);
    };

    function emit(event, { chatId, userId, language, props } = {}) {
        try {
            if (!EVENT_SET.has(event)) { counters.dropped++; return; }
            const t = now();
            if (t < offUntil) { counters.dropped++; return; }
            if (pending.size >= maxPending) { counters.dropped++; return; }
            if (t - windowStart >= 60e3) { windowStart = t; windowCount = 0; }
            if (++windowCount > maxPerMinute) { counters.dropped++; return; }
            const row = {
                ts: new Date(t).toISOString(), channel, event,
                language: typeof language === 'string' && /^[A-Za-z-]{2,10}$/.test(language) ? language : null,
                chat_ref: ref(chatId), tg_user_ref: ref(userId), props: cleanProps(props),
            };
            const p = Promise.resolve().then(() => store.insertChannelEvent(row)).then(() => { counters.written++; }, (e) => {
                counters.failed++;
                const missing = isMissingTable(e);
                offUntil = now() + (missing ? missingTableBackoffMs : errorBackoffMs);
                log.warn?.(missing
                    ? '[channel-events] rv_channel_events is missing (apply its migration); events paused for 10 minutes'
                    : `[channel-events] insert failed, paused for 30 seconds: ${String(e?.message || e).slice(0, 160)}`);
            }).finally(() => pending.delete(p));
            pending.add(p);
        } catch { /* measuring must never break a reply */ }
    }

    return { enabled: true, ref, emit, flush: async () => { while (pending.size) await Promise.all([...pending]); }, stats: () => ({ ...counters }) };
}
