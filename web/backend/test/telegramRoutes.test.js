// The Express wiring only: real HTTP, fake bot internals, no network.
import test from 'node:test';
import assert from 'node:assert/strict';
import http from 'node:http';
import express from 'express';
import { createTelegramRouter } from '../routes/telegram.routes.js';
import { build, msg, TOKEN } from './telegramHelpers.js';
import { signInitDataForTest, newLinkCode } from '../services/telegram/security.js';

async function serve(integration) {
    const app = express();
    app.use(express.json({ limit: '50mb' })); // same global parser as server.js
    app.use('/', createTelegramRouter({ getIntegration: async () => integration }));
    const server = http.createServer(app);
    await new Promise((r) => server.listen(0, '127.0.0.1', r));
    const base = `http://127.0.0.1:${server.address().port}`;
    return { base, close: () => new Promise((r) => server.close(r)) };
}
const J = (body, headers = {}) => ({ method: 'POST', headers: { 'content-type': 'application/json', ...headers }, body: JSON.stringify(body) });

test('webhook over HTTP: 401 without the secret header, 200 with it, update handled', async () => {
    const t = build();
    const s = await serve(t);
    try {
        assert.equal((await fetch(`${s.base}/api/telegram/webhook`, J(msg('/start')))).status, 401);
        const ok = await fetch(`${s.base}/api/telegram/webhook`, J(msg('/start', { id: 4242 }), { 'x-telegram-bot-api-secret-token': 'whsec' }));
        assert.equal(ok.status, 200);
        await new Promise((r) => setTimeout(r, 30));
        assert.equal(t.api.sent().length, 1);
    } finally { await s.close(); }
});

test('score + redeem + link page over HTTP', async () => {
    const t = build({ now: () => Date.now() });
    const s = await serve(t);
    try {
        const initData = signInitDataForTest({ auth_date: String(Math.floor(Date.now() / 1000)), user: JSON.stringify({ id: 7, first_name: 'Z' }) }, TOKEN);
        const sc = await fetch(`${s.base}/api/telegram/score`, J({ initData, slug: 'sudoku', difficulty: 'easy', durationMs: 50000 }));
        assert.equal(sc.status, 200);
        assert.equal((await sc.json()).ok, true);
        const bad = await fetch(`${s.base}/api/telegram/link/redeem`, J({ code: newLinkCode() }));
        assert.equal(bad.status, 410);
        const page = await fetch(`${s.base}/app/link?code=${newLinkCode()}`, { headers: { 'user-agent': 'Mozilla/5.0 (Android 15)' } });
        assert.equal(page.status, 200);
        assert.match(await page.text(), /intent:\/\//);
    } finally { await s.close(); }
});

test('without a bot token every Telegram route answers 503 instead of crashing', async () => {
    const s = await serve({ config: { botToken: '' }, handlers: {} });
    try {
        assert.equal((await fetch(`${s.base}/api/telegram/webhook`, J({ update_id: 1 }))).status, 503);
        assert.equal((await fetch(`${s.base}/app/link?code=x`)).status, 503);
    } finally { await s.close(); }
});
