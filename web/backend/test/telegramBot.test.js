import test from 'node:test';
import assert from 'node:assert/strict';
import { build, msg, cb, CONFIG, TOKEN } from './telegramHelpers.js';
import { gameUuid } from '../services/telegram/scoring.js';
import { verifyContext } from '../services/telegram/security.js';
import { TelegramError } from '../services/telegram/telegramApi.js';

const buttons = (m) => (m.extra.reply_markup?.inline_keyboard || []).flat();

test('/start greets in the Telegram language, with the menu and the player name', async () => {
    const t = build();
    await t.bot.handleUpdate(msg('/start', { from: { language_code: 'de', first_name: 'Jörg' } }));
    const [m] = t.api.sent();
    assert.match(m.text, /Jörg/);
    assert.match(m.text, /Willkommen/);
    assert.equal(m.extra.parse_mode, undefined); // parse mode is added by the API client, not the bot
    const labels = buttons(m).map((b) => b.text).join('|');
    assert.match(labels, /Tägliches|Rätsel|Täglich/i);
});

test('unknown or missing language falls back to English', async () => {
    const t = build();
    await t.bot.handleUpdate(msg('/start', { from: { language_code: 'xx' } }));
    assert.match(t.api.sent()[0].text, /Welcome to RiddleVerse/);
});

test('/language shows every language and choosing one persists it for later replies', async () => {
    const t = build();
    await t.bot.handleUpdate(msg('/language'));
    const kb = buttons(t.api.sent()[0]);
    assert.equal(kb.length, 30);
    await t.bot.handleUpdate(cb('lang:ja'));
    assert.equal((await t.store.getUser(42)).language, 'ja');
    t.api.calls.length = 0;
    await t.bot.handleUpdate(msg('/help', { from: { language_code: 'en' } }));
    assert.match(t.api.sent()[0].text, /RiddleVerse/);
    assert.ok(!/RiddleVerse bot commands/.test(t.api.sent()[0].text), 'help must now be Japanese, not English');
});

test('/daily names the same game as the Android schedule (Daily #22 on 2026-11-22) and builds a Mini App button in a private chat', async () => {
    const t = build();
    await t.bot.handleUpdate(msg('/daily'));
    const m = t.api.sent()[0];
    assert.match(m.text, /#22/);
    const play = buttons(m).find((b) => b.web_app);
    assert.ok(play, 'private chats get a web_app button');
    const url = new URL(play.web_app.url);
    assert.equal(url.origin + url.pathname.replace(/[^/]+$/, ''), 'https://puzzleverseai.com/play/');
    assert.equal(url.searchParams.get('src'), 'telegram');
    const ctx = verifyContext(url.searchParams.get('tgctx'), TOKEN);
    assert.equal(ctx.kind, 'd');
    assert.equal(ctx.chatId, '42');
    assert.equal(ctx.slug, url.pathname.split('/').pop());
});

test('in a group the play button is a plain link (web_app buttons are private-only), signed Mini App link when one is configured', async () => {
    const t = build();
    await t.bot.handleUpdate(msg('/play sudoku', { chat: { id: -1001, type: 'supergroup' } }));
    let play = buttons(t.api.sent()[0])[0];
    assert.equal(play.web_app, undefined);
    assert.match(play.url, /^https:\/\/puzzleverseai\.com\/play\/sudoku\?src=telegram/);

    const t2 = build();
    const bot2 = (await import('../services/telegram/bot.js')).createBot({ ...t2, catalog: t2.catalog, config: { ...CONFIG, miniAppShortName: 'play' }, linkService: t2.linkService, log: { warn() {}, error() {} } });
    await bot2.handleUpdate(msg('/play sudoku', { chat: { id: -1001, type: 'supergroup' } }));
    play = buttons(t2.api.sent()[0])[0];
    assert.match(play.url, /^https:\/\/t\.me\/Riddleverse_bot\/play\?startapp=p_sudoku_m1001_[0-9a-f]{20}$/);
});

test('/play with a game name (any language) opens that game; unknown names list the games', async () => {
    const t = build();
    await t.bot.handleUpdate(msg('/play Word Ladder'));
    assert.match(buttons(t.api.sent()[0]).find((b) => b.web_app).web_app.url, /\/play\/wordladder\?/);
    await t.bot.handleUpdate(msg('/play Wortleiter'));
    assert.match(buttons(t.api.sent()[1]).find((b) => b.web_app).web_app.url, /\/play\/wordladder\?/);
    await t.bot.handleUpdate(msg('/play nonsense'));
    assert.equal(buttons(t.api.sent()[2]).length, 23);
});

test('callback buttons: game pick, leaderboard pick, daily; every callback is acknowledged', async () => {
    const t = build();
    await t.bot.handleUpdate(cb('g:sudoku'));
    await t.bot.handleUpdate(cb('lb:sudoku'));
    await t.bot.handleUpdate(cb('lb:daily'));
    await t.bot.handleUpdate(cb('d'));
    await t.bot.handleUpdate(cb('garbage<script>'));
    assert.equal(t.api.calls.filter((c) => c.method === 'answerCallbackQuery').length, 5);
    assert.equal(t.api.sent().length, 4);
});

test('leaderboard is per chat and escapes player names', async () => {
    const t = build();
    await t.store.upsertScore({ gameId: gameUuid('sudoku'), chatId: '-1001', userId: 'tg-1', username: '<b>Evil</b> & co', score: 350 });
    await t.store.upsertScore({ gameId: gameUuid('sudoku'), chatId: '-1001', userId: 'tg-2', username: 'Bea', score: 200 });
    await t.store.upsertScore({ gameId: gameUuid('sudoku'), chatId: '-2002', userId: 'tg-3', username: 'Other chat', score: 999 });
    await t.bot.handleUpdate(msg('/leaderboard sudoku', { chat: { id: -1001, type: 'group' } }));
    const text = t.api.sent()[0].text;
    assert.match(text, /&lt;b&gt;Evil&lt;\/b&gt; &amp; co/);
    assert.ok(text.indexOf('Evil') < text.indexOf('Bea'));
    assert.ok(!text.includes('Other chat'));
    await t.bot.handleUpdate(msg('/leaderboard wordhive', { chat: { id: -1001, type: 'group' } }));
    assert.match(t.api.sent()[1].text, /No scores yet/);
});

test('/link: private only, issues a one-time https link with a 32-char code, never in a group', async () => {
    const t = build();
    await t.bot.handleUpdate(msg('/link', { chat: { id: -5, type: 'group' } }));
    assert.match(t.api.sent()[0].text, /private chat/);
    assert.equal(t.store._codes.size, 0);
    await t.bot.handleUpdate(msg('/link'));
    const m = t.api.sent()[1];
    const url = new URL(buttons(m)[0].url);
    assert.equal(url.origin + url.pathname, 'https://puzzleverseai.com/app/link');
    assert.match(url.searchParams.get('code'), /^[A-Za-z0-9_-]{32}$/);
    assert.equal(m.extra.protect_content, true);
    assert.equal(t.store._codes.size, 1);
});

test('/link is rate limited to 5 per hour per user', async () => {
    const t = build();
    for (let i = 0; i < 7; i++) await t.bot.handleUpdate(msg('/link'));
    const texts = t.api.sent().map((m) => m.text);
    assert.equal(texts.filter((x) => /expires in/.test(x)).length, 5);
    assert.ok(texts.some((x) => /Slow down/.test(x)));
});

test('/remind toggles and is private only', async () => {
    const t = build();
    await t.bot.handleUpdate(msg('/remind'));
    assert.equal((await t.store.getUser(42)).remind_daily, true);
    await t.bot.handleUpdate(msg('/remind'));
    assert.equal((await t.store.getUser(42)).remind_daily, false);
    await t.bot.handleUpdate(msg('/remind', { chat: { id: -5, type: 'group' } }));
    assert.match(t.api.sent().at(-1).text, /private|directly/i);
});

test('commands for another bot are ignored; unknown commands only answer in private; groups never get plain-text replies', async () => {
    const t = build();
    await t.bot.handleUpdate(msg('/play@OtherBot sudoku', { chat: { id: -9, type: 'group' } }));
    await t.bot.handleUpdate(msg('/nonsense', { chat: { id: -9, type: 'group' } }));
    await t.bot.handleUpdate(msg('hello there', { chat: { id: -9, type: 'group' } }));
    assert.equal(t.api.sent().length, 0);
    await t.bot.handleUpdate(msg('/nonsense'));
    assert.match(t.api.sent()[0].text, /\/help/);
    await t.bot.handleUpdate(msg('/play@Riddleverse_bot sudoku', { chat: { id: -9, type: 'group' } }));
    assert.equal(t.api.sent().length, 2);
});

test('start payloads: g_<slug> opens a game, link issues a code, junk shows the menu', async () => {
    const t = build();
    await t.bot.handleUpdate(msg('/start g_crowns'));
    assert.match(buttons(t.api.sent()[0]).find((b) => b.web_app).web_app.url, /\/play\/crowns\?/);
    await t.bot.handleUpdate(msg('/start link'));
    assert.match(buttons(t.api.sent()[1])[0].url, /\/app\/link\?code=/);
    await t.bot.handleUpdate(msg('/start g_notagame'));
    assert.match(t.api.sent()[2].text, /Welcome/);
});

test('per-user rate limit: 20 commands a minute, one warning, then silence', async () => {
    const t = build();
    for (let i = 0; i < 30; i++) await t.bot.handleUpdate(msg('/help'));
    const sent = t.api.sent();
    assert.equal(sent.length, 21);
    assert.match(sent[20].text, /Slow down/);
});

test('a user who blocks the bot is marked blocked and loses reminders; unblocking restores', async () => {
    const t = build();
    await t.bot.handleUpdate(msg('/remind'));
    await t.bot.handleUpdate({ update_id: 9001, my_chat_member: { chat: { id: 42, type: 'private' }, from: { id: 42 }, new_chat_member: { status: 'kicked' } } });
    let u = await t.store.getUser(42);
    assert.equal(u.blocked, true);
    assert.equal(u.remind_daily, false);
    await t.bot.handleUpdate({ update_id: 9002, my_chat_member: { chat: { id: 42, type: 'private' }, from: { id: 42 }, new_chat_member: { status: 'member' } } });
    assert.equal((await t.store.getUser(42)).blocked, false);
});

test('bot added to a group posts a localized welcome', async () => {
    const t = build();
    await t.bot.handleUpdate({ update_id: 9003, my_chat_member: { chat: { id: -77, type: 'supergroup' }, from: { id: 5, language_code: 'es' }, new_chat_member: { status: 'member' } } });
    assert.equal(t.api.sent()[0].chatId, -77);
    assert.match(t.api.sent()[0].text, /RiddleVerse/);
});

test('a Telegram "gone" error (403) while replying marks the user blocked instead of throwing', async () => {
    const t = build();
    await t.bot.handleUpdate(msg('/help')); // creates the user
    t.api.failNextWith(new TelegramError('sendMessage', 403, { error_code: 403, description: 'Forbidden: bot was blocked by the user' }));
    await t.bot.handleUpdate(msg('/help'));
    assert.equal((await t.store.getUser(42)).blocked, true);
});

test('messages from bots are ignored', async () => {
    const t = build();
    await t.bot.handleUpdate(msg('/start', { from: { is_bot: true } }));
    assert.equal(t.api.sent().length, 0);
});

test('/create points to the app instead of spending generation budget in chat', async () => {
    const t = build();
    await t.bot.handleUpdate(msg('/create a snake game'));
    assert.match(t.api.sent()[0].text, /app/i);
    assert.match(buttons(t.api.sent()[0])[0].url, /utm_source=telegram/);
});
