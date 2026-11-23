import test from 'node:test';
import assert from 'node:assert/strict';
import { build, msg, fakeRes, fakeFirebaseAuth, TOKEN, CONFIG } from './telegramHelpers.js';
import { signInitDataForTest, signContext, newLinkCode, hashLinkCode } from '../services/telegram/security.js';
import { gameUuid } from '../services/telegram/scoring.js';
import { TelegramError } from '../services/telegram/telegramApi.js';

const NOW = Date.UTC(2026, 10, 22, 12, 0, 0);
const initData = (user = { id: 42, first_name: 'Ana', username: 'ana' }, over = {}) =>
    signInitDataForTest({ auth_date: String(Math.floor(NOW / 1000) - 30), query_id: 'Q', user: JSON.stringify(user), ...over }, TOKEN);
const post = (body, headers = {}) => ({ body, headers, ip: '1.1.1.1', query: {} });

// ------------------------------------------------------------------ webhook
test('webhook: no secret configured fails closed (503); wrong or missing secret is 401; nothing is processed', async () => {
    const t = build();
    const h503 = (await import('../services/telegram/handlers.js')).createTelegramHandlers({ ...t, config: { ...CONFIG, webhookSecret: '' }, log: { warn() {}, error() {} } });
    let res = fakeRes(); await h503.webhook(post(msg('/start'), {}), res);
    assert.equal(res.statusCode, 503);
    res = fakeRes(); await t.handlers.webhook(post(msg('/start'), {}), res);
    assert.equal(res.statusCode, 401);
    res = fakeRes(); await t.handlers.webhook(post(msg('/start'), { 'x-telegram-bot-api-secret-token': 'whsec-nope' }), res);
    assert.equal(res.statusCode, 401);
    assert.equal(t.api.sent().length, 0);
});

test('webhook: valid secret acks 200 first, then handles the update in the background', async () => {
    const t = build();
    const res = fakeRes();
    const { done } = await t.handlers.webhook(post(msg('/start'), { 'x-telegram-bot-api-secret-token': 'whsec' }), res);
    assert.equal(res.statusCode, 200);
    assert.equal(res.sent, true);
    await done;
    assert.equal(t.api.sent().length, 1);
});

test('webhook: the same update_id delivered twice is handled once', async () => {
    const t = build();
    const update = msg('/start', { id: 555 });
    for (let i = 0; i < 3; i++) {
        const { done } = await t.handlers.webhook(post(update, { 'x-telegram-bot-api-secret-token': 'whsec' }), fakeRes());
        await done;
    }
    assert.equal(t.api.sent().length, 1);
});

test('webhook: malformed bodies are 400', async () => {
    const t = build();
    for (const body of [undefined, null, 'x', {}, { update_id: 'a' }, { update_id: 1.5 }]) {
        const res = fakeRes();
        await t.handlers.webhook(post(body, { 'x-telegram-bot-api-secret-token': 'whsec' }), res);
        assert.equal(res.statusCode, 400, JSON.stringify(body));
    }
});

test('webhook: a crash inside the bot does not fail the ack and does not throw', async () => {
    const t = build();
    t.api.failNextWith(new Error('boom'));
    const res = fakeRes();
    const { done } = await t.handlers.webhook(post(msg('/help', { id: 777 }), { 'x-telegram-bot-api-secret-token': 'whsec' }), res);
    assert.equal(res.statusCode, 200);
    await done;
});

// ------------------------------------------------------------------ score
test('score: a valid Mini App submission is stored in chat_scores + game_sessions (source telegram) and the player is told in chat', async () => {
    const t = build({ now: () => NOW });
    const res = fakeRes();
    await t.handlers.score(post({ initData: initData(), slug: 'sudoku', difficulty: 'hard', durationMs: 90000 }), res);
    assert.equal(res.statusCode, 200);
    assert.equal(res.body.ok, true);
    assert.equal(res.body.score, 300 + Math.round(100 * (1 - 90000 / 600000)));
    assert.equal(t.store._sessions.length, 1);
    const s = t.store._sessions[0];
    assert.equal(s.source, 'telegram');
    assert.equal(s.user_id, 'tg-42');
    assert.equal(s.game_id, 'sudoku');
    assert.equal(s.chat_id, '42');
    assert.equal(s.is_billable, true);
    assert.equal(s.score_source, 'reported');
    const top = await t.store.topScores({ gameId: gameUuid('sudoku'), chatId: '42' });
    assert.deepEqual(top.map((r) => [r.userId, r.username, r.score]), [['tg-42', '@ana', res.body.score]]);
    await new Promise((r) => setImmediate(r));
    const m = t.api.sent()[0];
    assert.equal(m.chatId, '42');
    assert.match(m.text, /@ana scored/);
});

test('score: a group context from the bot puts the score on that group\'s leaderboard and announces there with the top 5', async () => {
    const t = build({ now: () => NOW });
    const ctx = signContext({ kind: 'p', slug: 'wordhive', chatId: -100777 }, TOKEN);
    const res = fakeRes();
    await t.handlers.score(post({ initData: initData(), slug: 'wordhive', difficulty: 'easy', durationMs: 30000, ctx }), res);
    assert.equal(res.statusCode, 200);
    assert.equal((await t.store.topScores({ gameId: gameUuid('wordhive'), chatId: '-100777' })).length, 1);
    await new Promise((r) => setImmediate(r));
    assert.equal(t.api.sent()[0].chatId, '-100777');
    assert.match(t.api.sent()[0].text, /🥇/);
});

test('score: a forged or mismatched context is ignored (score goes to the private chat, nothing is posted to the target)', async () => {
    const t = build({ now: () => NOW });
    const forged = signContext({ kind: 'p', slug: 'wordhive', chatId: -100777 }, 'other:token');
    const wrongGame = signContext({ kind: 'p', slug: 'sudoku', chatId: -100777 }, TOKEN);
    for (const ctx of [forged, wrongGame, 'p_wordhive_m100777_00000000000000000000']) {
        const res = fakeRes();
        await t.handlers.score(post({ initData: initData({ id: 50 + Math.random() * 1000 | 0 }), slug: 'wordhive', difficulty: 'easy', durationMs: 30000, ctx }), res);
        assert.equal(res.statusCode, 200);
    }
    await new Promise((r) => setImmediate(r));
    assert.equal((await t.store.topScores({ gameId: gameUuid('wordhive'), chatId: '-100777' })).length, 0);
    assert.ok(t.api.sent().every((m) => m.chatId !== '-100777' && m.chatId !== -100777));
});

test('score: replay with a lower score keeps the best and announces nothing new; a higher one improves', async () => {
    let clock = NOW;
    const t = build({ now: () => clock });
    const submit = async (durationMs) => { clock += 10000; const res = fakeRes(); await t.handlers.score(post({ initData: initData(undefined, { auth_date: String(Math.floor(clock / 1000) - 5) }), slug: 'sudoku', difficulty: 'medium', durationMs }), res); return res; };
    const a = await submit(60000);
    const b = await submit(500000); // slower => lower
    const c = await submit(20000); // faster => higher
    assert.equal(a.body.improved, true);
    assert.equal(b.body.improved, false);
    assert.equal(b.body.best, a.body.score);
    assert.equal(c.body.improved, true);
    assert.ok(c.body.score > a.body.score);
});

test('score: rejects bad initData (401), unknown game, bad difficulty, bad duration; clamps absurd durations; throttles rapid repeats', async () => {
    const t = build({ now: () => NOW });
    const run = async (over, id = initData()) => { const res = fakeRes(); await t.handlers.score(post({ initData: id, slug: 'sudoku', difficulty: 'easy', durationMs: 60000, ...over }), res); return res; };
    assert.equal((await run({}, 'hash=bad&auth_date=1')).statusCode, 401);
    assert.equal((await run({ slug: 'nope' })).statusCode, 400);
    assert.equal((await run({ difficulty: 'godmode' })).statusCode, 400);
    assert.equal((await run({ durationMs: 'x' })).statusCode, 400);
    assert.equal((await run({ durationMs: -5 })).statusCode, 400);
    const fast = await run({ durationMs: 1 });
    assert.equal(fast.statusCode, 200);
    assert.ok(fast.body.score <= 400);
    assert.equal((await run({ durationMs: 60000 })).statusCode, 429, 'second submit within 5 s');
});

test('score: initData signed with another bot token is rejected', async () => {
    const t = build({ now: () => NOW });
    const res = fakeRes();
    await t.handlers.score(post({ initData: signInitDataForTest({ auth_date: String(Math.floor(NOW / 1000)), user: JSON.stringify({ id: 1 }) }, 'other:token'), slug: 'sudoku', durationMs: 60000 }), res);
    assert.equal(res.statusCode, 401);
    assert.equal(t.store._sessions.length, 0);
});

test('score: a failing store answers 500 without leaking the error', async () => {
    const t = build({ now: () => NOW });
    t.store.upsertScore = async () => { throw new Error('db down: secret-detail'); };
    const res = fakeRes();
    await t.handlers.score(post({ initData: initData(), slug: 'sudoku', durationMs: 60000 }), res);
    assert.equal(res.statusCode, 500);
    assert.ok(!JSON.stringify(res.body).includes('secret-detail'));
});

// ------------------------------------------------------------------ redeem (magic link)
async function issueFor(t, tgId = 42) {
    await t.store.upsertUser({ tgId, chatId: tgId, firstName: 'Ana', language: 'en' });
    return (await t.linkService.issue(tgId)).code;
}

test('redeem: signed-out app gets a Firebase custom token for the Telegram-bound account (tg_<id>), claims say telegram', async () => {
    const t = build({ now: () => NOW });
    const code = await issueFor(t);
    const res = fakeRes();
    await t.handlers.redeem(post({ code }), res);
    assert.equal(res.statusCode, 200);
    assert.equal(res.body.mode, 'signin');
    assert.equal(res.body.uid, 'tg_42');
    assert.equal(res.body.customToken, 'custom-for-tg_42');
    assert.deepEqual(t.auth.minted, [{ uid: 'tg_42', claims: { provider: 'telegram' } }]);
    assert.equal((await t.store.getUser(42)).firebase_uid, 'tg_42');
    assert.equal(res.headers['Cache-Control'], 'no-store');
});

test('redeem: a code works exactly once (replay is 410), even when two requests race', async () => {
    const t = build({ now: () => NOW });
    const code = await issueFor(t);
    const [a, b, c] = await Promise.all([1, 2, 3].map(async () => { const r = fakeRes(); await t.handlers.redeem(post({ code }), r); return r; }));
    const ok = [a, b, c].filter((r) => r.statusCode === 200);
    const gone = [a, b, c].filter((r) => r.statusCode === 410);
    assert.equal(ok.length, 1);
    assert.equal(gone.length, 2);
    assert.equal(t.auth.minted.length, 1);
    const again = fakeRes(); await t.handlers.redeem(post({ code }), again);
    assert.equal(again.statusCode, 410);
});

test('redeem: expired after 10 minutes, valid just before', async () => {
    let clock = NOW;
    const t = build({ now: () => clock });
    const early = await issueFor(t);
    clock = NOW + 9 * 60e3 + 59e3;
    let res = fakeRes(); await t.handlers.redeem(post({ code: early }), res);
    assert.equal(res.statusCode, 200);
    const late = (await t.linkService.issue(42)).code;
    clock += 10 * 60e3 + 1;
    res = fakeRes(); await t.handlers.redeem(post({ code: late }), res);
    assert.equal(res.statusCode, 410);
    assert.equal(t.auth.minted.length, 1);
});

test('redeem: a newer /link invalidates the older unused code', async () => {
    const t = build({ now: () => NOW });
    const first = await issueFor(t);
    const second = (await t.linkService.issue(42)).code;
    let res = fakeRes(); await t.handlers.redeem(post({ code: first }), res);
    assert.equal(res.statusCode, 410);
    res = fakeRes(); await t.handlers.redeem(post({ code: second }), res);
    assert.equal(res.statusCode, 200);
});

test('redeem: malformed, unknown and guessed codes are rejected without touching Firebase', async () => {
    const t = build({ now: () => NOW });
    for (const code of [undefined, '', 'short', 'x'.repeat(33), '../../etc/passwd'.padEnd(32, 'a'), newLinkCode()]) {
        const res = fakeRes();
        await t.handlers.redeem(post({ code }), res);
        assert.ok([400, 410].includes(res.statusCode), String(code));
    }
    assert.equal(t.auth.minted.length, 0);
});

test('redeem: the database only ever holds a hash of the code', async () => {
    const t = build({ now: () => NOW });
    const code = await issueFor(t);
    assert.ok(!t.store._codes.has(code));
    assert.ok(t.store._codes.has(hashLinkCode(code)));
});

test('redeem: a signed-in app (Bearer ID token) links Telegram to that account instead of switching accounts', async () => {
    const auth = fakeFirebaseAuth({ validTokens: { 'id-token-1': 'uid-app-1' } });
    const t = build({ now: () => NOW, auth });
    const code = await issueFor(t);
    const res = fakeRes();
    await t.handlers.redeem(post({ code }, { authorization: 'Bearer id-token-1' }), res);
    assert.equal(res.statusCode, 200);
    assert.equal(res.body.mode, 'linked');
    assert.equal(res.body.uid, 'uid-app-1');
    assert.equal(res.body.customToken, undefined);
    assert.equal((await t.store.getUser(42)).firebase_uid, 'uid-app-1');
    assert.equal(auth.minted.length, 0);
});

test('redeem: an invalid ID token fails BEFORE the one-time code is spent', async () => {
    const auth = fakeFirebaseAuth({ validTokens: { good: 'uid-1' } });
    const t = build({ now: () => NOW, auth });
    const code = await issueFor(t);
    let res = fakeRes(); await t.handlers.redeem(post({ code }, { authorization: 'Bearer forged' }), res);
    assert.equal(res.statusCode, 401);
    res = fakeRes(); await t.handlers.redeem(post({ code }, { authorization: 'Bearer good' }), res);
    assert.equal(res.statusCode, 200);
});

test('redeem: conflicts are 409 (Telegram already linked elsewhere; app account already linked to another Telegram user)', async () => {
    const auth = fakeFirebaseAuth({ validTokens: { a: 'uid-a', b: 'uid-b' } });
    const t = build({ now: () => NOW, auth });
    let res = fakeRes(); await t.handlers.redeem(post({ code: await issueFor(t, 42) }, { authorization: 'Bearer a' }), res);
    assert.equal(res.statusCode, 200);
    // same Telegram user, a different app account
    res = fakeRes(); await t.handlers.redeem(post({ code: (await t.linkService.issue(42)).code }, { authorization: 'Bearer b' }), res);
    assert.equal(res.statusCode, 409);
    assert.equal(res.body.error, 'telegram_already_linked');
    // another Telegram user, the already-linked app account
    res = fakeRes(); await t.handlers.redeem(post({ code: await issueFor(t, 99) }, { authorization: 'Bearer a' }), res);
    assert.equal(res.statusCode, 409);
    assert.equal(res.body.error, 'account_already_linked');
});

test('redeem: after linking, signing in from a new device returns a token for the SAME account', async () => {
    const auth = fakeFirebaseAuth({ validTokens: { a: 'uid-a' } });
    const t = build({ now: () => NOW, auth });
    await t.handlers.redeem(post({ code: await issueFor(t) }, { authorization: 'Bearer a' }), fakeRes());
    const res = fakeRes();
    await t.handlers.redeem(post({ code: (await t.linkService.issue(42)).code }), res);
    assert.equal(res.body.mode, 'signin');
    assert.equal(res.body.uid, 'uid-a');
});

test('redeem: Firebase unavailable is 503 and the code is NOT spent', async () => {
    const t = build({ now: () => NOW });
    const code = await issueFor(t);
    const broken = (await import('../services/telegram/linkService.js')).createLinkService({ store: t.store, getFirebaseAuth: async () => { throw new Error('no creds'); }, now: () => NOW });
    assert.equal((await broken.redeem({ code })).status, 503);
    assert.equal((await t.linkService.redeem({ code })).ok, true);
});

test('redeem: the endpoint is rate limited per IP', async () => {
    const t = build({ now: () => NOW });
    let last;
    for (let i = 0; i < 25; i++) { last = fakeRes(); await t.handlers.redeem(post({ code: 'a'.repeat(32) }), last); }
    assert.equal(last.statusCode, 429);
});

// ------------------------------------------------------------------ daily push
test('daily push: needs the cron secret; sends once per user per day; marks users who blocked the bot', async () => {
    const t = build({ now: () => NOW });
    await t.store.upsertUser({ tgId: 1, chatId: 1, firstName: 'A', language: 'de' });
    await t.store.upsertUser({ tgId: 2, chatId: 2, firstName: 'B', language: 'en' });
    await t.store.upsertUser({ tgId: 3, chatId: 3, firstName: 'C', language: 'en' });
    for (const id of [1, 2]) await t.store.setReminder(id, true);
    let res = fakeRes(); await t.handlers.dailyPush({ headers: {}, query: {} }, res);
    assert.equal(res.statusCode, 401);
    res = fakeRes(); await t.handlers.dailyPush({ headers: { 'x-cron-secret': 'wrong' }, query: {} }, res);
    assert.equal(res.statusCode, 401);
    res = fakeRes(); await t.handlers.dailyPush({ headers: { 'x-cron-secret': 'cronsec' }, query: {} }, res);
    assert.equal(res.body.sent, 2);
    assert.deepEqual(t.api.sent().map((m) => m.chatId).sort(), [1, 2]);
    assert.ok(t.api.sent().every((m) => m.extra.reply_markup.inline_keyboard[0][0].web_app));
    res = fakeRes(); await t.handlers.dailyPush({ headers: { 'x-cron-secret': 'cronsec' }, query: {} }, res);
    assert.equal(res.body.sent, 0, 'idempotent within the same day');
    // next day, user 2 blocked the bot
    t.api.calls.length = 0;
    t.api.failNextWith(new TelegramError('sendMessage', 403, { error_code: 403, description: 'Forbidden: bot was blocked by the user' }));
    const next = (await import('../services/telegram/handlers.js')).createTelegramHandlers({ ...t, config: CONFIG, now: () => NOW + 86400e3, log: { warn() {}, error() {} }, sleepFn: async () => {} });
    res = fakeRes(); await next.dailyPush({ headers: { 'x-cron-secret': 'cronsec' }, query: {} }, res);
    assert.equal(res.body.gone, 2);
    assert.equal((await t.store.getUser(1)).blocked, true);
});

test('daily push: not configured means 503, never open', async () => {
    const t = build({ now: () => NOW });
    const h = (await import('../services/telegram/handlers.js')).createTelegramHandlers({ ...t, config: { ...CONFIG, cronSecret: '' }, log: { warn() {}, error() {} } });
    const res = fakeRes(); await h.dailyPush({ headers: { 'x-cron-secret': '' }, query: {} }, res);
    assert.equal(res.statusCode, 503);
});

// ------------------------------------------------------------------ https link page
test('link page: Android gets an intent link with a Play fallback, no caching, no referrer; bad codes are 400', async () => {
    const t = build();
    const code = newLinkCode();
    const res = fakeRes();
    t.handlers.linkPage({ query: { code }, headers: { 'user-agent': 'Mozilla/5.0 (Linux; Android 15)', 'accept-language': 'de-DE,de;q=0.9' } }, res);
    assert.match(res.body, /intent:\/\/auth\?code=/);
    assert.match(res.body, /package=com\.kreativekoala\.riddleverse/);
    assert.match(res.body, /S\.browser_fallback_url=/);
    assert.match(res.body, /lang="de"/);
    assert.equal(res.headers['Cache-Control'], 'no-store');
    assert.equal(res.headers['Referrer-Policy'], 'no-referrer');
    const bad = fakeRes(); t.handlers.linkPage({ query: { code: '"><script>' }, headers: {} }, bad);
    assert.equal(bad.statusCode, 400);
});
