import test from 'node:test';
import assert from 'node:assert/strict';
import { createTelegramApi, TelegramError, escapeHtml } from '../services/telegram/telegramApi.js';
import { TOKEN } from './telegramHelpers.js';

const okFetch = (result, calls = []) => async (url, init) => { calls.push({ url, init }); return { ok: true, status: 200, json: async () => ({ ok: true, result }) }; };

test('sendMessage posts HTML parse mode with link previews off, to the right URL', async () => {
    const calls = [];
    const api = createTelegramApi({ token: TOKEN, fetchImpl: okFetch({ message_id: 1 }, calls) });
    await api.sendMessage(5, 'hi <b>x</b>', { reply_markup: { inline_keyboard: [] } });
    assert.equal(calls[0].url, `https://api.telegram.org/bot${TOKEN}/sendMessage`);
    const body = JSON.parse(calls[0].init.body);
    assert.equal(body.parse_mode, 'HTML');
    assert.equal(body.chat_id, 5);
    assert.equal(body.link_preview_options.is_disabled, true);
    assert.deepEqual(body.reply_markup, { inline_keyboard: [] });
});

test('API errors become TelegramError with the code; 403 and "chat not found" count as gone; retry_after is kept', async () => {
    const mk = (status, body) => createTelegramApi({ token: TOKEN, fetchImpl: async () => ({ ok: false, status, json: async () => body }) });
    await assert.rejects(mk(403, { ok: false, error_code: 403, description: 'Forbidden: bot was blocked by the user' }).sendMessage(1, 'x'), (e) => e instanceof TelegramError && e.isGone);
    await assert.rejects(mk(400, { ok: false, error_code: 400, description: 'Bad Request: chat not found' }).sendMessage(1, 'x'), (e) => e.isGone);
    await assert.rejects(mk(429, { ok: false, error_code: 429, description: 'Too Many Requests', parameters: { retry_after: 7 } }).sendMessage(1, 'x'), (e) => e.retryAfter === 7 && !e.isGone);
});

test('network failures and non-JSON replies never leak the bot token', async () => {
    const boom = createTelegramApi({ token: TOKEN, fetchImpl: async () => { throw new Error(`connect ECONNREFUSED https://api.telegram.org/bot${TOKEN}/sendMessage`); } });
    await assert.rejects(boom.sendMessage(1, 'x'), (e) => !e.message.includes(TOKEN));
    const html = createTelegramApi({ token: TOKEN, fetchImpl: async () => ({ ok: false, status: 502, json: async () => { throw new Error('not json'); } }) });
    await assert.rejects(html.sendMessage(1, 'x'), (e) => e instanceof TelegramError && !e.message.includes(TOKEN));
});

test('a missing token is refused at construction', () => {
    assert.throws(() => createTelegramApi({ token: '' }));
});

test('escapeHtml neutralises markup and handles null', () => {
    assert.equal(escapeHtml('<a href="x">&</a>'), '&lt;a href="x"&gt;&amp;&lt;/a&gt;');
    assert.equal(escapeHtml(null), '');
});
