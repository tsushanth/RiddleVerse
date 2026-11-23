// Usage events for the bot (rv_channel_events): what is written, what never is, and that a failure never touches a reply.
import test from 'node:test';
import assert from 'node:assert/strict';
import { build, msg, cb, fakeRes, eventsOf, TOKEN } from './telegramHelpers.js';
import { createChannelEvents, cleanProps, EVENTS, USER_ACTION_EVENTS, noopChannelEvents } from '../services/telegram/channelEvents.js';
import { parseStartPayload } from '../services/telegram/bot.js';
import { signInitDataForTest, newLinkCode } from '../services/telegram/security.js';
import { createSupabaseStore } from '../services/telegram/store.js';
import { loadConfig } from '../services/telegram/index.js';

const NOW = Date.UTC(2026, 10, 22, 12, 0, 0);
const BIG = 987654321; // a recognisable raw Telegram id: it must never appear in a stored row
const names = (rows) => rows.map((r) => r.event);
const post = (body, headers = {}) => ({ body, headers, ip: '1.1.1.1', query: {} });
const quiet = () => { const logs = []; return { logs, warn: (...a) => logs.push(a.join(' ')), error() {}, log() {} }; };

// ------------------------------------------------------------------ the writer
test('writer: only allow-listed events are written; refs are keyed hashes, never the raw id; props are sanitised', async () => {
    const store = { rows: [], async insertChannelEvent(r) { this.rows.push(r); } };
    const ev = createChannelEvents({ store, refKey: 'k1', now: () => NOW });
    ev.emit('start', { chatId: BIG, userId: BIG, language: 'de', props: { src: 'web_game', 'Bad Key': 'x', nested: { a: 1 }, list: [1], n: 3, ok: true, long: 'y'.repeat(200) } });
    ev.emit('not_an_event', { chatId: 1 });
    ev.emit('help', { chatId: -100123, userId: BIG, language: 'not a language!' });
    await ev.flush();
    assert.deepEqual(names(store.rows), ['start', 'help']);
    const [a, b] = store.rows;
    assert.equal(a.channel, 'telegram');
    assert.equal(a.ts, new Date(NOW).toISOString());
    assert.equal(a.language, 'de');
    assert.match(a.chat_ref, /^[0-9a-f]{24}$/);
    assert.equal(a.chat_ref, a.tg_user_ref); // private chat: same id, same hash
    assert.notEqual(b.chat_ref, b.tg_user_ref); // group: chat and person differ
    assert.equal(b.tg_user_ref, a.tg_user_ref); // same person, same ref across events
    assert.equal(b.language, null);
    assert.deepEqual(Object.keys(a.props).sort(), ['long', 'n', 'ok', 'src']);
    assert.equal(a.props.long.length, 40);
    assert.ok(!JSON.stringify(store.rows).includes(String(BIG)));
    assert.deepEqual(ev.stats(), { written: 2, dropped: 1, failed: 0 });
    // another key gives other refs (the hash is keyed)
    const other = createChannelEvents({ store, refKey: 'k2', now: () => NOW });
    assert.notEqual(other.ref(BIG), ev.ref(BIG));
});

test('writer: no key or no store means no-op (events off), and the allow-list and the SQL active list agree with the code', () => {
    assert.equal(createChannelEvents({ store: { insertChannelEvent() {} }, refKey: '' }), noopChannelEvents);
    assert.equal(createChannelEvents({ store: {}, refKey: 'k' }), noopChannelEvents);
    for (const e of USER_ACTION_EVENTS) assert.ok(EVENTS.includes(e), e);
    assert.deepEqual(cleanProps(null), {});
    assert.deepEqual(cleanProps([1, 2]), {});
});

test('writer: emit never blocks and never throws, even when the insert hangs or the store throws synchronously', () => {
    const hang = createChannelEvents({ store: { insertChannelEvent: () => new Promise(() => {}) }, refKey: 'k', now: () => NOW });
    const t0 = Date.now();
    for (let i = 0; i < 500; i++) hang.emit('help', { chatId: i, userId: i });
    assert.ok(Date.now() - t0 < 200);
    assert.ok(hang.stats().dropped > 0); // bounded: at most 100 in flight
    const bad = createChannelEvents({ store: { insertChannelEvent() { throw new Error('boom'); } }, refKey: 'k', now: () => NOW });
    assert.doesNotThrow(() => bad.emit('help', { chatId: 1 }));
});

test('writer: a missing table pauses writes for 10 minutes with ONE warning; other errors pause 30 s; then it resumes', async () => {
    let clock = NOW;
    const log = quiet();
    let mode = 'missing';
    const rows = [];
    const store = { async insertChannelEvent(r) {
        if (mode === 'missing') { const e = new Error('relation "public.rv_channel_events" does not exist'); e.code = '42P01'; throw e; }
        if (mode === 'down') throw new Error('connection reset');
        rows.push(r);
    } };
    const ev = createChannelEvents({ store, refKey: 'k', now: () => clock, log });
    ev.emit('help', { chatId: 1 });
    await ev.flush();
    for (let i = 0; i < 20; i++) ev.emit('help', { chatId: 1 }); // paused: nothing is attempted, nothing is logged
    await ev.flush();
    assert.equal(log.logs.length, 1);
    assert.match(log.logs[0], /missing/);
    assert.equal(ev.stats().failed, 1);
    clock += 601e3; mode = 'down';
    ev.emit('help', { chatId: 1 }); await ev.flush();
    assert.equal(log.logs.length, 2);
    clock += 31e3; mode = 'ok';
    ev.emit('help', { chatId: 1 }); await ev.flush();
    assert.equal(rows.length, 1);
});

test('writer: PostgREST "table not in schema cache" is also treated as a missing table', async () => {
    const log = quiet();
    const store = { async insertChannelEvent() { const e = new Error("Could not find the table 'public.rv_channel_events' in the schema cache"); e.code = 'PGRST205'; throw e; } };
    const ev = createChannelEvents({ store, refKey: 'k', now: () => NOW, log });
    ev.emit('help', { chatId: 1 }); await ev.flush();
    assert.match(log.logs[0], /missing/);
});

test('supabase store: insertChannelEvent targets rv_channel_events and surfaces the error to the writer', async () => {
    const calls = [];
    const sb = { from: (t) => ({ insert: async (row) => { calls.push([t, row]); return { error: { code: '42P01', message: 'nope' } }; } }) };
    const s = createSupabaseStore(sb);
    await assert.rejects(() => s.insertChannelEvent({ event: 'help' }), (e) => e.code === '42P01');
    assert.deepEqual(calls[0], ['rv_channel_events', { event: 'help' }]);
});

test('config: the ref key comes from RV_CHANNEL_REF_KEY, else is derived from the bot token, else events are off', () => {
    assert.equal(loadConfig({ RV_CHANNEL_REF_KEY: 'abc', RIDDLEVERSE_BOT_TOKEN: 't' }).refKey, 'abc');
    assert.match(loadConfig({ RIDDLEVERSE_BOT_TOKEN: 't' }).refKey, /^rv-ref:/);
    assert.equal(loadConfig({}).refKey, '');
});

// ------------------------------------------------------------------ /start payloads
test('start payload grammar: action, optional -src, bare src, junk', () => {
    const slugs = new Set(['sudoku', 'crowns']);
    const p = (x) => parseStartPayload(x, slugs);
    assert.deepEqual(p(''), { action: 'menu', slug: null, src: 'none' });
    assert.deepEqual(p(undefined), { action: 'menu', slug: null, src: 'none' });
    assert.deepEqual(p('g_sudoku'), { action: 'game', slug: 'sudoku', src: 'none' });
    assert.deepEqual(p('g_sudoku-web_game'), { action: 'game', slug: 'sudoku', src: 'web_game' });
    assert.deepEqual(p('g_sudoku-WEB_Share'), { action: 'game', slug: 'sudoku', src: 'web_share' });
    assert.deepEqual(p('daily'), { action: 'daily', slug: null, src: 'none' });
    assert.deepEqual(p('daily-app_card'), { action: 'daily', slug: null, src: 'app_card' });
    assert.deepEqual(p('link-app_settings'), { action: 'link', slug: null, src: 'app_settings' });
    assert.deepEqual(p('web_home'), { action: 'menu', slug: null, src: 'web_home' });
    assert.deepEqual(p('app'), { action: 'menu', slug: null, src: 'app' });
    assert.deepEqual(p('g_notagame-web_game'), { action: 'menu', slug: null, src: 'web_game' });
    assert.deepEqual(p('g_notagame'), { action: 'menu', slug: null, src: 'g_notagame' }); // unknown game: menu, as before
    assert.deepEqual(p('g_sudoku-' + 'x'.repeat(30)), { action: 'game', slug: 'sudoku', src: 'other' });
    assert.deepEqual(p('a b; drop table'), { action: 'menu', slug: null, src: 'other' });
    assert.equal(p('x'.repeat(65)).src, 'other');
});

// ------------------------------------------------------------------ bot events
test('/start records src, action, game and new_user (first contact only), with the language and chat type', async () => {
    const t = build({ now: () => NOW });
    await t.bot.handleUpdate(msg('/start g_sudoku-web_share', { from: { id: BIG, language_code: 'de' }, chat: { id: BIG } }));
    await t.bot.handleUpdate(msg('/start', { from: { id: BIG, language_code: 'de' }, chat: { id: BIG } }));
    const rows = await eventsOf(t);
    const starts = rows.filter((r) => r.event === 'start');
    assert.deepEqual(starts[0].props, { chat_type: 'private', new_user: true, src: 'web_share', action: 'game', game: 'sudoku' });
    assert.deepEqual(starts[1].props, { chat_type: 'private', src: 'none', action: 'menu' });
    assert.equal(starts[0].language, 'de');
    assert.ok(rows.some((r) => r.event === 'game_open' && r.props.via === 'start' && r.props.game === 'sudoku'));
    assert.ok(!JSON.stringify(rows).includes(String(BIG)));
});

test('daily, play, leaderboard, language, remind, link, install pointers and text each write one event, and no text or name is stored', async () => {
    const t = build({ now: () => NOW });
    const from = { id: BIG, first_name: 'Zelda', username: 'zelda_secret' };
    const chat = { id: BIG };
    await t.bot.handleUpdate(msg('/daily', { from, chat }));
    await t.bot.handleUpdate(cb('d', { from, chat }));
    await t.bot.handleUpdate(cb('play', { from, chat }));
    await t.bot.handleUpdate(cb('g:crowns', { from, chat }));
    await t.bot.handleUpdate(cb('lb:daily', { from, chat }));
    await t.bot.handleUpdate(cb('lb:sudoku', { from, chat }));
    await t.bot.handleUpdate(cb('lang:de', { from, chat }));
    await t.bot.handleUpdate(msg('/remind', { from, chat }));
    await t.bot.handleUpdate(msg('/remind', { from, chat }));
    await t.bot.handleUpdate(msg('/link', { from, chat }));
    await t.bot.handleUpdate(msg('/app', { from, chat }));
    await t.bot.handleUpdate(msg('/create', { from, chat }));
    await t.bot.handleUpdate(msg('/nonsense', { from, chat }));
    await t.bot.handleUpdate(msg('my private thought: hunter2', { from, chat }));
    const rows = await eventsOf(t);
    assert.deepEqual(names(rows).filter((e) => e !== 'daily_open' && e !== 'game_open' && e !== 'game_menu'), [
        'leaderboard_view', 'leaderboard_view', 'language_change', 'remind_on', 'remind_off', 'link_issued', 'app_cmd', 'create_cmd', 'unknown_command', 'text_message']);
    assert.deepEqual(rows.filter((r) => r.event === 'daily_open').map((r) => r.props.via), ['command', 'button']);
    assert.equal(rows.find((r) => r.event === 'game_menu').props.via, 'menu');
    assert.equal(rows.find((r) => r.event === 'game_open').props.game, 'crowns');
    assert.deepEqual(rows.filter((r) => r.event === 'leaderboard_view').map((r) => r.props.game), ['daily', 'sudoku']);
    const lc = rows.find((r) => r.event === 'language_change');
    assert.equal(lc.props.to, 'de'); assert.equal(lc.props.from, 'en'); assert.equal(lc.language, 'de');
    const blob = JSON.stringify(rows);
    for (const secret of [String(BIG), 'Zelda', 'zelda_secret', 'hunter2', 'private thought']) assert.ok(!blob.includes(secret), secret);
});

test('rate limits: /link beyond 5 an hour and a command flood each write a rate_limited event', async () => {
    const t = build({ now: () => NOW });
    for (let i = 0; i < 7; i++) await t.bot.handleUpdate(msg('/link'));
    for (let i = 0; i < 30; i++) await t.bot.handleUpdate(msg('/help'));
    const rows = await eventsOf(t);
    assert.deepEqual(rows.filter((r) => r.event === 'rate_limited').map((r) => r.props.where).sort(), ['commands', 'link', 'link']);
});

test('group use: a command in a group carries chat_type and a group chat_ref different from the person ref; joining and leaving are events', async () => {
    const t = build({ now: () => NOW });
    await t.bot.handleUpdate(msg('/play@Riddleverse_bot sudoku', { chat: { id: -1001, type: 'supergroup' } }));
    await t.bot.handleUpdate({ update_id: 7001, my_chat_member: { chat: { id: -1001, type: 'supergroup' }, from: { id: 42, language_code: 'en' }, new_chat_member: { status: 'member' } } });
    await t.bot.handleUpdate({ update_id: 7002, my_chat_member: { chat: { id: -1001, type: 'supergroup' }, from: { id: 42 }, new_chat_member: { status: 'kicked' } } });
    const rows = await eventsOf(t);
    const open = rows.find((r) => r.event === 'game_open');
    assert.equal(open.props.chat_type, 'supergroup');
    assert.notEqual(open.chat_ref, open.tg_user_ref);
    assert.deepEqual(names(rows).slice(-2), ['group_added', 'group_removed']);
});

test('blocking and unblocking the bot are events, from my_chat_member and from a failed send', async () => {
    const t = build({ now: () => NOW });
    await t.bot.handleUpdate(msg('/help'));
    await t.bot.handleUpdate({ update_id: 9101, my_chat_member: { chat: { id: 42, type: 'private' }, from: { id: 42 }, new_chat_member: { status: 'kicked' } } });
    await t.bot.handleUpdate({ update_id: 9102, my_chat_member: { chat: { id: 42, type: 'private' }, from: { id: 42 }, new_chat_member: { status: 'member' } } });
    const { TelegramError } = await import('../services/telegram/telegramApi.js');
    t.api.failNextWith(new TelegramError('sendMessage', 403, { error_code: 403, description: 'Forbidden: bot was blocked by the user' }));
    await t.bot.handleUpdate(msg('/help', { id: 9103 }));
    const rows = await eventsOf(t);
    assert.deepEqual(rows.filter((r) => /blocked/.test(r.event)).map((r) => [r.event, r.props.via]), [['bot_blocked', 'my_chat_member'], ['bot_unblocked', undefined], ['bot_blocked', 'send_failed']]);
});

test('a failing update writes one error event (no message, no text) and the bot still answers other updates', async () => {
    const t = build({ now: () => NOW });
    t.api.failNextWith(Object.assign(new Error('secret failure detail'), { errorCode: 500 }));
    await t.bot.handleUpdate(msg('/help'));
    await t.bot.handleUpdate(msg('/help'));
    const rows = await eventsOf(t);
    const err = rows.find((r) => r.event === 'error');
    assert.deepEqual(err.props, { where: 'message', code: 500 });
    assert.ok(!JSON.stringify(rows).includes('secret failure detail'));
});

test('measuring never touches a reply: a store that rejects every event, or hangs, leaves the bot answering exactly as before', async () => {
    const t = build({ now: () => NOW });
    const log = quiet();
    t.store.insertChannelEvent = async () => { throw new Error('table gone'); };
    const bot = (await import('../services/telegram/bot.js')).createBot({
        api: t.api, store: t.store, i18n: t.i18n, linkService: t.linkService, catalog: t.catalog, config: t.config, now: t.now, limiter: t.limiter, log,
        events: createChannelEvents({ store: t.store, refKey: 'k', now: t.now, log }),
    });
    await bot.handleUpdate(msg('/start g_crowns-web_game'));
    await bot.handleUpdate(msg('/daily'));
    assert.equal(t.api.sent().length, 2);
    const hanging = (await import('../services/telegram/bot.js')).createBot({
        api: t.api, store: t.store, i18n: t.i18n, linkService: t.linkService, catalog: t.catalog, config: t.config, now: t.now, limiter: t.limiter, log,
        events: createChannelEvents({ store: { insertChannelEvent: () => new Promise(() => {}) }, refKey: 'k', now: t.now, log }),
    });
    await hanging.handleUpdate(msg('/help', { from: { id: 77 } }));
    assert.equal(t.api.sent().length, 3);
});

// ------------------------------------------------------------------ handler events
test('score: a verified finished round writes game_finished with game, kind, difficulty, score and where; a rejected one writes nothing', async () => {
    const t = build({ now: () => NOW });
    const init = (user) => signInitDataForTest({ auth_date: String(Math.floor(NOW / 1000) - 30), user: JSON.stringify(user) }, TOKEN);
    let res = fakeRes();
    await t.handlers.score(post({ initData: init({ id: BIG, first_name: 'Zelda', language_code: 'fr' }), slug: 'sudoku', difficulty: 'easy', durationMs: 60000 }), res);
    assert.equal(res.statusCode, 200);
    res = fakeRes();
    await t.handlers.score(post({ initData: 'forged', slug: 'sudoku', durationMs: 60000 }), res);
    assert.equal(res.statusCode, 401);
    const rows = await eventsOf(t);
    assert.equal(rows.length, 1);
    assert.equal(rows[0].event, 'game_finished');
    assert.equal(rows[0].language, 'fr');
    assert.deepEqual({ ...rows[0].props, score: typeof rows[0].props.score }, { game: 'sudoku', kind: 'p', difficulty: 'easy', score: 'number', improved: true, duration_s: 60, chat_type: 'private' });
    assert.ok(!JSON.stringify(rows).includes(String(BIG)));
});

test('redeem: success writes link_redeemed with the mode for the Telegram user; a bad code writes link_redeem_failed with the reason only', async () => {
    const t = build({ now: () => NOW });
    await t.store.upsertUser({ tgId: BIG, chatId: BIG, firstName: 'Ana', language: 'en' });
    const code = (await t.linkService.issue(BIG)).code;
    let res = fakeRes(); await t.handlers.redeem(post({ code }), res);
    assert.equal(res.statusCode, 200);
    res = fakeRes(); await t.handlers.redeem(post({ code }), res);
    assert.equal(res.statusCode, 410);
    res = fakeRes(); await t.handlers.redeem(post({ code: newLinkCode() }), res);
    const rows = await eventsOf(t);
    assert.deepEqual(rows.map((r) => [r.event, r.props.mode ?? r.props.error]), [['link_redeemed', 'signin'], ['link_redeem_failed', 'expired_or_used'], ['link_redeem_failed', 'expired_or_used']]);
    assert.ok(rows[0].tg_user_ref && rows[1].tg_user_ref === null);
    assert.ok(!JSON.stringify(rows).includes(code));
});

test('daily push: one daily_push event with the counts and no person; a blocked user writes bot_blocked', async () => {
    const t = build({ now: () => NOW });
    await t.store.upsertUser({ tgId: 1, chatId: 1, firstName: 'A', language: 'en' });
    await t.store.upsertUser({ tgId: 2, chatId: 2, firstName: 'B', language: 'de' });
    await t.store.setReminder(1, true); await t.store.setReminder(2, true);
    const { TelegramError } = await import('../services/telegram/telegramApi.js');
    const real = t.api.sendMessage;
    t.api.sendMessage = async (chatId, ...a) => { if (chatId === 2) throw new TelegramError('sendMessage', 403, { error_code: 403, description: 'Forbidden: bot was blocked by the user' }); return real(chatId, ...a); };
    const res = fakeRes();
    await t.handlers.dailyPush({ headers: { 'x-cron-secret': 'cronsec' }, query: {} }, res);
    const rows = await eventsOf(t);
    const run = rows.find((r) => r.event === 'daily_push');
    assert.deepEqual(run.props, { candidates: 2, sent: 1, gone: 1, failed: 0 });
    assert.equal(run.chat_ref, null); assert.equal(run.tg_user_ref, null);
    const blocked = rows.find((r) => r.event === 'bot_blocked');
    assert.deepEqual([blocked.props.via, blocked.language], ['daily_push', 'de']);
});

// ------------------------------------------------------------------ stats endpoint
test('stats: needs the cron secret (503 unset, 401 wrong, the forge worker secret is not accepted); clamps days; returns the store document', async () => {
    const t = build({ now: () => NOW });
    const get = (headers, query = {}) => { const res = fakeRes(); return t.handlers.stats({ headers, query }, res).then(() => res); };
    let res = await get({});
    assert.equal(res.statusCode, 401);
    res = await get({ 'x-worker-secret': 'game-worker-secret-2024' });
    assert.equal(res.statusCode, 401);
    res = await get({ 'x-cron-secret': 'cronsec' }, { days: '30' });
    assert.equal(res.statusCode, 200);
    assert.equal(res.body.days, 30);
    assert.equal(res.body.generatedAt, new Date(NOW).toISOString());
    assert.equal(res.headers['Cache-Control'], 'no-store');
    assert.equal((await get({ 'x-cron-secret': 'cronsec' }, { days: '9999' })).body.days, 90);
    assert.equal((await get({ 'x-cron-secret': 'cronsec' }, { days: 'abc' })).body.days, 7);
    assert.equal((await get({ 'x-cron-secret': 'cronsec' }, { days: '0' })).body.days, 7);
    const off = (await import('../services/telegram/handlers.js')).createTelegramHandlers({ ...t, config: { ...t.config, cronSecret: '' }, log: quiet() });
    res = fakeRes(); await off.stats({ headers: { 'x-cron-secret': '' }, query: {} }, res);
    assert.equal(res.statusCode, 503);
});

test('stats: an unapplied migration is a clear 503, any other failure a 500 that leaks nothing', async () => {
    const t = build({ now: () => NOW });
    const hit = async (err) => {
        t.store.channelStats = async () => { throw err; };
        const res = fakeRes();
        await t.handlers.stats({ headers: { 'x-cron-secret': 'cronsec' }, query: {} }, res);
        return res;
    };
    let res = await hit(Object.assign(new Error('Could not find the function public.rv_channel_stats(p_days) in the schema cache'), { code: 'PGRST202' }));
    assert.deepEqual([res.statusCode, res.body], [503, { error: 'channel_events_not_migrated' }]);
    res = await hit(new Error('password authentication failed for user postgres'));
    assert.deepEqual([res.statusCode, res.body], [500, { error: 'stats_failed' }]);
});

test('stats: the supabase store calls the rv_channel_stats function with the day count', async () => {
    const calls = [];
    const s = createSupabaseStore({ rpc: async (fn, args) => { calls.push([fn, args]); return { data: { overview: {} }, error: null }; } });
    assert.deepEqual(await s.channelStats(14), { overview: {} });
    assert.deepEqual(calls, [['rv_channel_stats', { p_days: 14 }]]);
});

test('stats over HTTP: GET /api/telegram/stats is routed and protected', async () => {
    const { default: express } = await import('express');
    const http = await import('node:http');
    const { createTelegramRouter } = await import('../routes/telegram.routes.js');
    const t = build({ now: () => NOW });
    const app = express();
    app.use('/', createTelegramRouter({ getIntegration: async () => t }));
    const server = http.createServer(app);
    await new Promise((r) => server.listen(0, '127.0.0.1', r));
    const base = `http://127.0.0.1:${server.address().port}`;
    try {
        assert.equal((await fetch(`${base}/api/telegram/stats?days=7`)).status, 401);
        const ok = await fetch(`${base}/api/telegram/stats?days=3`, { headers: { 'x-cron-secret': 'cronsec' } });
        assert.equal(ok.status, 200);
        assert.equal((await ok.json()).days, 3);
    } finally { await new Promise((r) => server.close(r)); }
});
