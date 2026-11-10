// Authentication on the notification send routes, and the FCM token registration alias. No real FCM or Supabase: fetch and the
// Firebase messaging entry point are replaced, and every denied request is asserted to reach neither.
import test from 'node:test';
import assert from 'node:assert/strict';
import http from 'node:http';

process.env.DOCKER_BUILD = 'true'; // config/firebaseAdmin.js skips initialisation (no credentials in tests)
process.env.SUPABASE_URL = 'http://supabase.test';
process.env.SUPABASE_ANON_KEY = 'test-anon-key';
process.env.SUPABASE_SERVICE_KEY = 'test-service-key';
const KEY = 'unit-test-notification-key-0123456789';

const supabaseCalls = [];
let supabaseReply = { status: 200, body: [] };
const realFetch = globalThis.fetch;
globalThis.fetch = async (url, init) => {
    if (String(url).startsWith('http://supabase.test')) {
        supabaseCalls.push({ url: String(url), method: init?.method || 'GET', body: init?.body });
        return new Response(JSON.stringify(supabaseReply.body), { status: supabaseReply.status, headers: { 'content-type': 'application/json' } });
    }
    return realFetch(url, init);
};

const realSetInterval = globalThis.setInterval;
globalThis.setInterval = (...a) => realSetInterval(...a).unref();

const { default: express } = await import('express');
const { admin } = await import('../config/firebaseAdmin.js');
const { requireNotificationAdmin, hasNotificationAdminKey } = await import('../middleware/notificationAuth.js');
const { default: notificationRouter, allowAnonymousTestNotification } = await import('../routes/notificationConfig.routes.js');
const { default: streakRouter } = await import('../routes/streakNotifications.routes.js');
const { default: dailyPuzzlesRouter, registerFcmTokenHandler } = await import('../routes/dailyPuzzles.routes.js');
const { fcmTokenRegisterLimiter } = await import('../config/middleware.js');
globalThis.setInterval = realSetInterval;

// Replace the Firebase sender with a recorder: nothing in this file can reach FCM.
const sent = [];
const fakeMessaging = () => ({
    sendEachForMulticast: async (m) => { sent.push(m); return { successCount: m.tokens.length, failureCount: 0, responses: [] }; },
    sendMulticast: async (m) => { sent.push(m); return { successCount: m.tokens.length, failureCount: 0, responses: [] }; },
});
Object.defineProperty(admin, 'messaging', { value: fakeMessaging, configurable: true, writable: true });

const app = express();
app.use(express.json());
app.use('/api/streak-notifications', streakRouter);
app.use('/api/notifications', notificationRouter);
app.use('/', notificationRouter);
app.use('/api/daily-puzzles', dailyPuzzlesRouter);
app.post('/register-fcm-token', fcmTokenRegisterLimiter, registerFcmTokenHandler);
const server = http.createServer(app);
await new Promise((r) => server.listen(0, r));
const base = `http://127.0.0.1:${server.address().port}`;
test.after(() => { server.close(); globalThis.fetch = realFetch; });

const post = async (path, body, headers = {}) => {
    const r = await realFetch(base + path, { method: 'POST', headers: { 'content-type': 'application/json', ...headers }, body: JSON.stringify(body) });
    let json = null;
    try { json = await r.json(); } catch { /* empty */ }
    return { status: r.status, body: json };
};
const reset = () => { supabaseCalls.length = 0; sent.length = 0; supabaseReply = { status: 200, body: [] }; };

test('middleware fails closed when NOTIFICATION_ADMIN_KEY is unset, even for an empty or matching-looking header', () => {
    delete process.env.NOTIFICATION_ADMIN_KEY;
    assert.equal(hasNotificationAdminKey({ headers: {} }), false);
    assert.equal(hasNotificationAdminKey({ headers: { 'x-notification-admin-key': '' } }), false);
    assert.equal(hasNotificationAdminKey({ headers: { 'x-notification-admin-key': 'undefined' } }), false);
    process.env.NOTIFICATION_ADMIN_KEY = '';
    assert.equal(hasNotificationAdminKey({ headers: { 'x-notification-admin-key': '' } }), false);
});

test('middleware rejects missing, wrong, prefix and non-string keys; accepts the exact key', () => {
    process.env.NOTIFICATION_ADMIN_KEY = KEY;
    assert.equal(hasNotificationAdminKey({ headers: {} }), false);
    assert.equal(hasNotificationAdminKey({ headers: { 'x-notification-admin-key': KEY.slice(0, -1) } }), false);
    assert.equal(hasNotificationAdminKey({ headers: { 'x-notification-admin-key': KEY + 'x' } }), false);
    assert.equal(hasNotificationAdminKey({ headers: { 'x-notification-admin-key': [KEY] } }), false);
    // The general ADMIN_KEY header is not accepted here (separate secret, no cross-grant).
    process.env.ADMIN_KEY = 'other-admin-key';
    assert.equal(hasNotificationAdminKey({ headers: { 'x-admin-key': 'other-admin-key' } }), false);
    assert.equal(hasNotificationAdminKey({ headers: { 'x-notification-admin-key': KEY } }), true);
    let nextCalled = false;
    requireNotificationAdmin({ headers: { 'x-notification-admin-key': KEY } }, {}, () => { nextCalled = true; });
    assert.equal(nextCalled, true);
});

for (const mount of ['/send-notifications', '/api/notifications/send-notifications']) {
    test(`POST ${mount} without the key is 401 and touches neither Supabase nor FCM (key unset and key set)`, async () => {
        for (const keyEnv of [undefined, KEY]) {
            if (keyEnv === undefined) delete process.env.NOTIFICATION_ADMIN_KEY; else process.env.NOTIFICATION_ADMIN_KEY = keyEnv;
            reset();
            const payload = { title: 'hello', body: 'world' };
            assert.equal((await post(mount, payload)).status, 401);
            assert.equal((await post(mount, payload, { 'x-notification-admin-key': 'wrong' })).status, 401);
            assert.equal((await post(mount, payload, { 'x-admin-key': 'whatever' })).status, 401);
            assert.equal(supabaseCalls.length, 0);
            assert.equal(sent.length, 0);
        }
    });
}

test('POST /send-notifications with the key passes auth (validation then applies); with key unset a "matching" header still fails', async () => {
    process.env.NOTIFICATION_ADMIN_KEY = KEY;
    reset();
    const r = await post('/send-notifications', {}, { 'x-notification-admin-key': KEY });
    assert.equal(r.status, 400); // got past auth to body validation
    assert.equal(sent.length, 0);
    delete process.env.NOTIFICATION_ADMIN_KEY;
    assert.equal((await post('/send-notifications', {}, { 'x-notification-admin-key': KEY })).status, 401);
});

test('POST /configs requires the key', async () => {
    process.env.NOTIFICATION_ADMIN_KEY = KEY;
    reset();
    assert.equal((await post('/configs', { name: 'a', message: 'b' })).status, 401);
    assert.equal(supabaseCalls.length, 0);
});

for (const route of ['run-now', 'stop', 'start', 'test-user']) {
    test(`POST /api/streak-notifications/${route} requires the key`, async () => {
        process.env.NOTIFICATION_ADMIN_KEY = KEY;
        reset();
        assert.equal((await post(`/api/streak-notifications/${route}`, { userEmail: 'a@b.co' })).status, 401);
        delete process.env.NOTIFICATION_ADMIN_KEY;
        assert.equal((await post(`/api/streak-notifications/${route}`, { userEmail: 'a@b.co' }, { 'x-notification-admin-key': KEY })).status, 401);
        assert.equal(supabaseCalls.length, 0);
        assert.equal(sent.length, 0);
    });
}

test('POST /api/daily-puzzles/trigger-job requires the key', async () => {
    process.env.NOTIFICATION_ADMIN_KEY = KEY;
    reset();
    assert.equal((await post('/api/daily-puzzles/trigger-job', {})).status, 401);
    assert.equal(supabaseCalls.length, 0);
    assert.equal(sent.length, 0);
});

test('POST /test-notification without the key still works for the apps but ignores a caller-chosen title/body', async () => {
    process.env.NOTIFICATION_ADMIN_KEY = KEY;
    reset();
    supabaseReply = { status: 200, body: [{ fcm_token: 'tok-1' }] };
    const r = await post('/test-notification', { userEmail: 'owner-a@example.test', title: 'PHISHING TITLE', body: 'PHISHING BODY' });
    assert.equal(r.status, 200);
    assert.equal(sent.length, 1);
    assert.deepEqual(sent[0].tokens, ['tok-1']);
    assert.notEqual(sent[0].notification.title, 'PHISHING TITLE');
    assert.notEqual(sent[0].notification.body, 'PHISHING BODY');
});

test('POST /test-notification with the key may set title/body', async () => {
    process.env.NOTIFICATION_ADMIN_KEY = KEY;
    reset();
    supabaseReply = { status: 200, body: [{ fcm_token: 'tok-1' }] };
    const r = await post('/test-notification', { userEmail: 'owner-b@example.test', title: 'T', body: 'B' }, { 'x-notification-admin-key': KEY });
    assert.equal(r.status, 200);
    assert.equal(sent[0].notification.title, 'T');
    assert.equal(sent[0].notification.body, 'B');
});

test('anonymous /test-notification is capped per account per hour', async () => {
    const now = 1_000_000;
    for (let i = 0; i < 5; i++) assert.equal(allowAnonymousTestNotification('Cap@Example.test', now + i), true);
    assert.equal(allowAnonymousTestNotification('cap@example.test', now + 10), false);
    assert.equal(allowAnonymousTestNotification('cap@example.test', now + 61 * 60 * 1000), true);
    reset();
    process.env.NOTIFICATION_ADMIN_KEY = KEY;
    assert.equal((await post('/test-notification', {})).status, 400);
    assert.equal(sent.length, 0);
});

test('POST /register-fcm-token (alias) validates exactly like /api/daily-puzzles/register-fcm-token', async () => {
    const good = { userEmail: 'u@example.test', fcmToken: 'x'.repeat(150), platform: 'android' };
    const cases = [
        [{}, 400],
        [{ userEmail: 'u@example.test' }, 400],
        [{ ...good, platform: 'windows' }, 400],
        [{ ...good, fcmToken: 12345 }, 400],
        [{ ...good, userEmail: 'not-an-email' }, 400],
        [{ ...good, fcmToken: 'x'.repeat(5000) }, 400],
        [{ ...good, userEmail: ['a@b.co'] }, 400],
    ];
    for (const path of ['/register-fcm-token', '/api/daily-puzzles/register-fcm-token']) {
        for (const [body, status] of cases) {
            reset();
            const r = await post(path, body);
            assert.equal(r.status, status, `${path} ${JSON.stringify(body).slice(0, 60)}`);
            assert.equal(supabaseCalls.length, 0, 'invalid input must not write');
        }
        reset();
        const ok = await post(path, good);
        assert.equal(ok.status, 200, path);
        assert.equal(supabaseCalls.filter((c) => c.method === 'POST').length, 1);
        assert.equal(sent.length, 0);
    }
});

test('the alias is rate limited', async () => {
    reset();
    let limited = 0;
    for (let i = 0; i < 130; i++) {
        const r = await realFetch(base + '/register-fcm-token', { method: 'POST', headers: { 'content-type': 'application/json', 'cf-connecting-ip': '203.0.113.77' }, body: '{}' });
        if (r.status === 429) limited++;
    }
    assert.ok(limited > 0, 'expected a 429 after the per-address limit');
    const other = await realFetch(base + '/register-fcm-token', { method: 'POST', headers: { 'content-type': 'application/json', 'cf-connecting-ip': '203.0.113.78' }, body: '{}' });
    assert.equal(other.status, 400); // a different client address is not affected
});
