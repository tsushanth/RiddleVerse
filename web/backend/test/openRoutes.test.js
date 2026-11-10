// Routes that used to be callable by anyone: token registration / lookup, cost-bearing generation routes, destructive admin routes,
// account deletion, coin awards and the test-notification fan-out. No real Supabase, FCM, LLM or Firebase: fetch is replaced and
// every denied request is asserted to reach none of them.
import test from 'node:test';
import assert from 'node:assert/strict';
import http from 'node:http';

process.env.DOCKER_BUILD = 'true';
process.env.SUPABASE_URL = 'http://supabase.test';
process.env.SUPABASE_ANON_KEY = 'test-anon-key';
process.env.SUPABASE_SERVICE_KEY = 'test-service-key';
const KEY = 'unit-test-notification-key-0123456789';

const supabaseCalls = [];
let supabaseReply = () => ({ status: 200, body: [] });
const realFetch = globalThis.fetch;
globalThis.fetch = async (url, init) => {
    if (String(url).startsWith('http://supabase.test')) {
        const call = { url: String(url), method: init?.method || 'GET', body: init?.body };
        supabaseCalls.push(call);
        const r = supabaseReply(call);
        return new Response(JSON.stringify(r.body), { status: r.status, headers: { 'content-type': 'application/json' } });
    }
    if (String(url).startsWith('http://127.0.0.1')) return realFetch(url, init);
    throw new Error(`unexpected outbound request in test: ${String(url).slice(0, 40)}`);
};

const realSetInterval = globalThis.setInterval;
globalThis.setInterval = (...a) => realSetInterval(...a).unref();

const { default: express } = await import('express');
const { admin } = await import('../config/firebaseAdmin.js');
const identity = await import('../auth/verifyIdentity.js');
const { default: dailyPuzzlesRouter, registerFcmTokenHandler } = await import('../routes/dailyPuzzles.routes.js');
const { default: notificationRouter } = await import('../routes/notificationConfig.routes.js');
const { default: llmRouter } = await import('../routes/llm.routes.js');
const { default: puzzleRouter } = await import('../routes/puzzleRoutes.js');
const { default: bulkRouter } = await import('../routes/dailyPuzzlesBulk.routes.js');
const { default: adminRouter } = await import('../routes/admin.routes.js');
const { default: endpointsRouter } = await import('../routes/puzzleEndpoints.routes.js');
const { default: accountRouter } = await import('../routes/account.routes.js');
const { default: coinsRouter } = await import('../routes/coins.routes.js');
const { default: generationRouter } = await import('../routes/generation.routes.js');
const { fcmTokenRegisterLimiter } = await import('../config/middleware.js');
globalThis.setInterval = realSetInterval;

const sent = [];
Object.defineProperty(admin, 'messaging', {
    value: () => ({
        sendEachForMulticast: async (m) => { sent.push(m); return { successCount: m.tokens.length, failureCount: 0, responses: [] }; },
        sendMulticast: async (m) => { sent.push(m); return { successCount: m.tokens.length, failureCount: 0, responses: [] }; },
    }),
    configurable: true, writable: true,
});

// Firebase tokens in this file are plain strings: "<uid>|<email>|<verified>" -> decoded token. Anything else is rejected.
identity.__setDeps({
    getFirebaseAuth: async () => ({
        verifyIdToken: async (t) => {
            const [uid, email, verified] = String(t).split('|');
            if (!uid || !email) throw Object.assign(new Error('bad token'), { code: 'auth/argument-error' });
            return { uid, email, email_verified: verified === 'true' };
        },
    }),
    getSupabaseUrl: () => undefined,
});
const bearer = (uid, email, verified = true) => ({ authorization: `Bearer ${uid}|${email}|${verified}` });

const app = express();
app.use(express.json());
app.use('/api/daily-puzzles', dailyPuzzlesRouter);
app.post('/register-fcm-token', fcmTokenRegisterLimiter, registerFcmTokenHandler);
app.use('/', notificationRouter);
app.use('/', llmRouter);
app.use('/api/puzzles', puzzleRouter);
app.use('/api/puzzles', bulkRouter);
app.use('/', adminRouter);
app.use('/', endpointsRouter);
app.use('/', accountRouter);
app.use('/api/coins', coinsRouter);
app.use('/', generationRouter);
const server = http.createServer(app);
await new Promise((r) => server.listen(0, r));
const base = `http://127.0.0.1:${server.address().port}`;
test.after(() => { server.close(); globalThis.fetch = realFetch; identity.__setDeps(); });

const call = async (method, path, body, headers = {}) => {
    const r = await realFetch(base + path, {
        method,
        headers: { 'content-type': 'application/json', ...headers },
        body: body === undefined ? undefined : JSON.stringify(body),
    });
    let json = null;
    try { json = await r.json(); } catch { /* empty */ }
    return { status: r.status, body: json };
};
const reset = () => { supabaseCalls.length = 0; sent.length = 0; supabaseReply = () => ({ status: 200, body: [] }); };
const writes = () => supabaseCalls.filter((c) => c.method !== 'GET');

// ---------------------------------------------------------------------------------------------------------------- registration
const TOKEN = 'T'.repeat(150);
const reg = (email, headers) => call('POST', '/register-fcm-token', { userEmail: email, fcmToken: TOKEN, platform: 'android' }, headers);
const tokenHeldBy = (...emails) => (c) => {
    if (c.method === 'GET' && c.url.includes('fcm_token=eq.')) return { status: 200, body: emails.map((e) => ({ user_email: e })) };
    return { status: 200, body: [] };
};

for (const path of ['/register-fcm-token', '/api/daily-puzzles/register-fcm-token']) {
    test(`${path}: legacy call (no credential) for a token nobody holds still registers`, async () => {
        reset();
        delete process.env.FCM_REGISTER_REQUIRE_AUTH;
        const r = await call('POST', path, { userEmail: 'new@example.test', fcmToken: TOKEN, platform: 'android' });
        assert.equal(r.status, 200);
        assert.equal(writes().filter((c) => c.method === 'POST').length, 1);
    });

    test(`${path}: legacy call cannot attach a token that another account holds, and writes nothing`, async () => {
        reset();
        supabaseReply = tokenHeldBy('victim@example.test');
        const r = await call('POST', path, { userEmail: 'attacker@example.test', fcmToken: TOKEN, platform: 'android' });
        assert.equal(r.status, 409);
        assert.equal(writes().length, 0, 'no insert, update or delete may happen');
        assert.equal(JSON.stringify(r.body).includes('victim'), false, 'the holder must not be disclosed');
    });
}

test('legacy refresh of a token the same account already holds is accepted (case-insensitive email)', async () => {
    reset();
    supabaseReply = tokenHeldBy('Same@Example.test');
    const r = await reg('same@example.test');
    assert.equal(r.status, 200);
    assert.equal(writes().filter((c) => c.method === 'PATCH').length, 0, 'a legacy call never deactivates anything');
});

test('a garbage bearer token degrades to the legacy rules, it does not 401 an old/odd client', async () => {
    reset();
    assert.equal((await reg('new2@example.test', { authorization: 'Bearer not-a-real-token' })).status, 200);
    reset();
    supabaseReply = tokenHeldBy('victim@example.test');
    assert.equal((await reg('attacker@example.test', { authorization: 'Bearer not-a-real-token' })).status, 409);
});

test('a signed-in user can register a token for their own email and takes it over from another account on the device', async () => {
    reset();
    supabaseReply = tokenHeldBy('previous-user@example.test');
    const r = await reg('Me@Example.test', bearer('uid-me', 'me@example.test'));
    assert.equal(r.status, 200);
    const patch = writes().find((c) => c.method === 'PATCH');
    assert.ok(patch, 'the previous holder is deactivated');
    assert.match(patch.url, /user_email=in\./);
    assert.equal(JSON.parse(patch.body).is_active, false);
    assert.equal(writes().filter((c) => c.method === 'POST').length, 1);
});

test('a signed-in user cannot register a token for someone else, even a free one', async () => {
    reset();
    const r = await reg('victim@example.test', bearer('uid-me', 'me@example.test'));
    assert.equal(r.status, 403);
    assert.equal(supabaseCalls.length, 0);
});

test('an unverified email in the token proves nothing: same rules as a legacy call', async () => {
    reset();
    supabaseReply = tokenHeldBy('victim@example.test');
    assert.equal((await reg('me@example.test', bearer('uid-me', 'me@example.test', false))).status, 409);
    assert.equal(writes().length, 0);
});

test('FCM_REGISTER_REQUIRE_AUTH=true turns the legacy path off (for use once updated apps send the token)', async () => {
    reset();
    process.env.FCM_REGISTER_REQUIRE_AUTH = 'true';
    try {
        assert.equal((await reg('new3@example.test')).status, 401);
        assert.equal(writes().length, 0);
        assert.equal((await reg('me@example.test', bearer('uid-me', 'me@example.test'))).status, 200);
    } finally {
        delete process.env.FCM_REGISTER_REQUIRE_AUTH;
    }
});

// ---------------------------------------------------------------------------------------------------------------- check-fcm-token
test('GET check-fcm-token: anonymous and other users are 401 and nothing is read', async () => {
    reset();
    process.env.NOTIFICATION_ADMIN_KEY = KEY;
    assert.equal((await call('GET', '/api/daily-puzzles/check-fcm-token?email=victim@example.test')).status, 401);
    assert.equal((await call('GET', '/api/daily-puzzles/check-fcm-token?email=victim@example.test', undefined, bearer('uid-me', 'me@example.test'))).status, 401);
    assert.equal((await call('GET', '/api/daily-puzzles/check-fcm-token?email=victim@example.test', undefined, { 'x-notification-admin-key': 'wrong' })).status, 401);
    assert.equal(supabaseCalls.length, 0);
    assert.equal((await call('GET', '/api/daily-puzzles/check-fcm-token')).status, 400);
});

test('GET check-fcm-token returns only a boolean, to the account owner or the admin key', async () => {
    process.env.NOTIFICATION_ADMIN_KEY = KEY;
    for (const [rows, expected] of [[[{ platform: 'android', fcm_token: 'SECRET-TOKEN-VALUE', last_used: 'x' }], true], [[], false]]) {
        for (const headers of [bearer('uid-me', 'me@example.test'), { 'x-notification-admin-key': KEY }]) {
            reset();
            supabaseReply = () => ({ status: 200, body: rows });
            const r = await call('GET', '/api/daily-puzzles/check-fcm-token?email=me@example.test', undefined, headers);
            assert.equal(r.status, 200);
            assert.deepEqual(r.body, { success: true, registered: expected });
            assert.equal(JSON.stringify(r.body).includes('SECRET'), false);
        }
    }
});

// ---------------------------------------------------------------------------------------------------------------- admin-only routes
const GATED = [
    ['POST', '/api/daily-puzzles/generate', {}],
    ['POST', '/api/daily-puzzles/generate-with-notifications?email=a@b.co', {}],
    ['POST', '/api/daily-puzzles/test-generation', { email: 'a@b.co', topics: ['x'] }],
    ['POST', '/api/daily-puzzles/cleanup', { dryRun: false }],
    ['POST', '/api/puzzles/generate', { puzzleType: 'anagram' }],
    ['POST', '/api/puzzles/migrate', { dryRun: false }],
    ['POST', '/api/puzzles/bulk-validate', { puzzleType: 'anagram' }],
    ['POST', '/api/puzzles/daily/refresh', {}],
    ['POST', '/call-llm', { conversation: [{ role: 'user', content: 'hi' }] }],
    ['POST', '/call-llm-stream', { conversation: [{ role: 'user', content: 'hi' }] }],
    ['POST', '/api/regenerate-stuck-puzzle-sets', { dryRun: false }],
    ['POST', '/api/cleanup-duplicates', { dryRun: false }],
    ['GET', '/api/analyze-duplicates', undefined],
    ['GET', '/generate-puzzle?puzzleType=anagram&modelName=x', undefined],
    ['GET', '/reset-all-users-puzzle-progress?puzzleType=anagram', undefined],
];
for (const [method, path, body] of GATED) {
    test(`${method} ${path.split('?')[0]} requires the notification/ops admin key (fails closed when it is unset)`, async () => {
        for (const keyEnv of [KEY, undefined]) {
            if (keyEnv === undefined) delete process.env.NOTIFICATION_ADMIN_KEY; else process.env.NOTIFICATION_ADMIN_KEY = keyEnv;
            reset();
            assert.equal((await call(method, path, body)).status, 401);
            assert.equal((await call(method, path, body, { 'x-notification-admin-key': 'wrong' })).status, 401);
            assert.equal((await call(method, path, body, { 'x-admin-key': KEY })).status, 401);
            if (keyEnv === undefined) assert.equal((await call(method, path, body, { 'x-notification-admin-key': KEY })).status, 401);
            assert.equal(supabaseCalls.length, 0);
            assert.equal(sent.length, 0);
        }
    });
}

test('with the key the gated routes reach their own validation (spot checks that do no work)', async () => {
    process.env.NOTIFICATION_ADMIN_KEY = KEY;
    const h = { 'x-notification-admin-key': KEY };
    reset();
    assert.equal((await call('POST', '/api/daily-puzzles/test-generation', {}, h)).status, 400);
    assert.equal((await call('POST', '/api/puzzles/generate', {}, h)).status, 400);
    assert.equal((await call('POST', '/call-llm', {}, h)).status, 400);
    assert.equal((await call('GET', '/generate-puzzle', undefined, h)).status, 400);
});

// ---------------------------------------------------------------------------------------------------------------- test-notification
test('test-notification: a signed-in user cannot target another account', async () => {
    reset();
    process.env.NOTIFICATION_ADMIN_KEY = KEY;
    const r = await call('POST', '/test-notification', { userEmail: 'victim@example.test' }, bearer('uid-me', 'me@example.test'));
    assert.equal(r.status, 403);
    assert.equal(supabaseCalls.length, 0);
    assert.equal(sent.length, 0);
});

test('test-notification: the account owner still gets the fixed-text test, the apps (no token) still work', async () => {
    process.env.NOTIFICATION_ADMIN_KEY = KEY;
    reset();
    supabaseReply = () => ({ status: 200, body: [{ fcm_token: 'tok-own' }] });
    assert.equal((await call('POST', '/test-notification', { userEmail: 'owner-c@example.test', title: 'X', body: 'Y' }, bearer('uid-c', 'owner-c@example.test'))).status, 200);
    assert.notEqual(sent[0].notification.title, 'X');
    reset();
    supabaseReply = () => ({ status: 200, body: [{ fcm_token: 'tok-own' }] });
    assert.equal((await call('POST', '/test-notification', { userEmail: 'owner-d@example.test' })).status, 200);
});

test('test-notification: anonymous callers share a per-address and a global cap', async () => {
    const { allowAnonymousTestNotificationSource } = await import('../routes/notificationConfig.routes.js');
    const t0 = 50_000_000;
    for (let i = 0; i < 20; i++) assert.equal(allowAnonymousTestNotificationSource('198.51.100.1', t0 + i), true);
    assert.equal(allowAnonymousTestNotificationSource('198.51.100.1', t0 + 30), false, 'per-address cap');
    assert.equal(allowAnonymousTestNotificationSource('198.51.100.1', t0 + 61 * 60 * 1000), true, 'window slides');
    const t1 = t0 + 10 * 60 * 60 * 1000;
    const allowArray = () => allowAnonymousTestNotificationSource('192.0.2.250', t1 + 399);
    let allowed = 0;
    for (let i = 0; i < 400; i++) if (allowAnonymousTestNotificationSource(`203.0.113.${i % 250}.${i}`, t1 + i)) allowed++;
    // Earlier tests in this file already used a couple of the 300 (real-clock hits), so allow for those.
    assert.ok(allowed >= 295 && allowed < 400, `global cap across many addresses (allowed ${allowed})`);
    assert.equal(allowArray(), false);
});

// ---------------------------------------------------------------------------------------------------------------- account deletion
test('DELETE /delete-user-account: anonymous, wrong-user and unverified callers are 401 and nothing is touched', async () => {
    process.env.NOTIFICATION_ADMIN_KEY = KEY;
    reset();
    const body = { userId: 'uid-victim', email: 'victim@example.test', confirmationText: 'DELETE' };
    assert.equal((await call('DELETE', '/delete-user-account', body)).status, 401);
    assert.equal((await call('DELETE', '/delete-user-account', body, bearer('uid-me', 'me@example.test'))).status, 401);
    assert.equal((await call('DELETE', '/delete-user-account', body, { 'x-notification-admin-key': 'wrong' })).status, 401);
    assert.equal(supabaseCalls.length, 0);
});

test('DELETE /delete-user-account: the owner (uid match) and the admin key get past auth to the confirmation check', async () => {
    process.env.NOTIFICATION_ADMIN_KEY = KEY;
    reset();
    const body = { userId: 'uid-me', email: 'me@example.test' }; // no confirmationText: stops at 400, deletes nothing
    assert.equal((await call('DELETE', '/delete-user-account', body, bearer('uid-me', 'me@example.test'))).status, 400);
    assert.equal((await call('DELETE', '/delete-user-account', body, { 'x-notification-admin-key': KEY })).status, 400);
    assert.equal(supabaseCalls.length, 0);
});

// ---------------------------------------------------------------------------------------------------------------- coins award
test('POST /api/coins/award: only the rating reward, at its fixed size, once per account', async () => {
    reset();
    for (const body of [
        { userId: 'u1', amount: 1000000, reason: 'app_rating_reward' },
        { userId: 'u1', amount: 100, reason: 'anything_else' },
        { userId: 'u1', amount: 99, reason: 'app_rating_reward' },
        { userId: 'u1', amount: '100', reason: 'app_rating_reward' },
        { userId: ['u1'], amount: 100, reason: 'app_rating_reward' },
    ]) {
        assert.equal((await call('POST', '/api/coins/award', body)).status, 400, JSON.stringify(body));
    }
    assert.equal(supabaseCalls.length, 0);

    supabaseReply = () => ({ status: 200, body: { success: true, new_balance: 100 } });
    const ok = await call('POST', '/api/coins/award', { userId: 'u1', amount: 100, reason: 'app_rating_reward', platform: 'ios' });
    assert.equal(ok.status, 200);
    assert.equal(ok.body.coinsAdded, 100);
    const rpc = JSON.parse(supabaseCalls[0].body);
    assert.equal(rpc.p_iap_transaction_id, 'award_app_rating_reward_u1', 'stable per user so the duplicate check enforces once');

    reset();
    supabaseReply = () => ({ status: 200, body: { success: true, duplicate: true } });
    const again = await call('POST', '/api/coins/award', { userId: 'u1', amount: 100, reason: 'app_rating_reward', platform: 'ios' });
    assert.equal(again.status, 200);
    assert.equal(again.body.coinsAdded, 0);
});

// ---------------------------------------------------------------------------------------------------------------- generate-riddle
test('POST /generate-riddle runs behind the input/rate guard (oversize input is refused before any generation)', async () => {
    reset();
    const long = await call('POST', '/generate-riddle', { topic: 'x'.repeat(500), format: 'Q&A', numPuzzles: 3, userId: 'u1' });
    assert.equal(long.status, 400);
    const many = await call('POST', '/generate-riddle', { topic: 'space', format: 'Q&A', numPuzzles: 500, userId: 'u1' });
    assert.equal(many.status, 400);
    const badAuth = await call('POST', '/generate-riddle', { topic: 'space', format: 'Q&A', numPuzzles: 3, userId: 'u1' }, { authorization: 'Bearer nope' });
    assert.equal(badAuth.status, 401);
    assert.equal(supabaseCalls.length, 0);
});
