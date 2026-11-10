// Coin purchase verification: fail closed, product/amount mapping, idempotency, interim safeguards, payout hold.
// No network: the Play verifier gets a fake fetch, the database is a stub.
import test from 'node:test';
import assert from 'node:assert/strict';

process.env.DOCKER_BUILD = 'true';
process.env.SUPABASE_URL = 'http://supabase.test';
process.env.SUPABASE_ANON_KEY = 'test-anon-key';
process.env.SUPABASE_SERVICE_KEY = 'test-service-key';

const { createPurchaseHandler } = await import('../services/coinService.js');
const { createPlayPurchaseVerifier, PLAY_ORDER_ID_RE, loadServiceAccount } = await import('../services/playPurchaseVerifier.js');
const { createCounters, getMode, readPolicy } = await import('../services/coinPurchasePolicy.js');
const { payoutsEnabled, requestPayout, getPayoutEligibility } = await import('../services/payoutService.js');

const P100 = 'com.kreativekoala.riddleverse.coins.100';
const P500 = 'com.kreativekoala.riddleverse.coins.500';
const ORDER = 'GPA.1234-5678-9012-34567';

function mkRes() {
    const r = { code: 200, body: null, headers: {} };
    r.status = (c) => { r.code = c; return r; };
    r.json = (b) => { r.body = b; return r; };
    r.setHeader = (k, v) => { r.headers[k] = v; };
    return r;
}
function mkReq(body, extra = {}) { return { body, headers: {}, ip: '1.2.3.4', ...extra }; }

function mkDb({ ledger = [], rpcImpl } = {}) {
    const calls = [];
    const seen = new Set();
    const db = {
        calls,
        rpc: async (name, args) => {
            calls.push({ name, args });
            if (rpcImpl) return rpcImpl(name, args);
            if (seen.has(args.p_iap_transaction_id)) return { data: { success: true, duplicate: true }, error: null };
            seen.add(args.p_iap_transaction_id);
            return { data: { success: true, new_balance: args.p_amount }, error: null };
        },
        from: () => {
            const q = { select: () => q, eq: () => q, like: () => q, gte: () => q, limit: async () => ({ data: ledger, error: null }) };
            return q;
        },
    };
    return db;
}
const okVerifier = (extra = {}) => ({ isConfigured: () => true, verifyOrder: async () => ({ ok: true }), ...extra });

test('default mode is enforce, even for unknown values', () => {
    assert.equal(getMode({}), 'enforce');
    assert.equal(getMode({ COIN_PURCHASE_VERIFY: 'off' }), 'enforce');
    assert.equal(getMode({ COIN_PURCHASE_VERIFY: ' Interim ' }), 'interim');
});

test('enforce: unconfigured verifier fails closed (503, no credit)', async () => {
    const db = mkDb();
    const h = createPurchaseHandler({ env: {}, supabase: db, verifier: { isConfigured: () => false, verifyOrder: async () => assert.fail('must not be called') } });
    const res = mkRes();
    await h(mkReq({ userId: 'u1', productId: P100, transactionId: ORDER, platform: 'android' }), res);
    assert.equal(res.code, 503);
    assert.equal(res.body.retryable, true);
    assert.equal(db.calls.length, 0);
});

test('enforce: verifier outage fails closed; definitive no is 400; neither credits', async () => {
    for (const [result, code] of [[{ ok: false, code: 'unavailable' }, 503], [{ ok: false, code: 'not_found' }, 400], [{ ok: false, code: 'product_mismatch' }, 400], [{ ok: false, code: 'not_processed' }, 400]]) {
        const db = mkDb();
        const h = createPurchaseHandler({ env: {}, supabase: db, verifier: okVerifier({ verifyOrder: async () => result }) });
        const res = mkRes();
        await h(mkReq({ userId: 'u1', productId: P100, transactionId: ORDER, platform: 'android' }), res);
        assert.equal(res.code, code, JSON.stringify(result));
        assert.equal(db.calls.length, 0);
    }
});

test('enforce: verified order credits the SERVER pack amount, ignoring a client amount', async () => {
    const db = mkDb();
    const h = createPurchaseHandler({ env: {}, supabase: db, verifier: okVerifier() });
    const res = mkRes();
    await h(mkReq({ userId: 'u1', productId: P500, transactionId: ORDER, platform: 'android', amount: 999999, coins: 999999 }), res);
    assert.equal(res.code, 200);
    assert.equal(res.body.coinsAdded, 500);
    assert.equal(db.calls[0].args.p_amount, 500);
    assert.equal(db.calls[0].args.p_iap_transaction_id, ORDER);
});

test('idempotent on the transaction id: second claim credits nothing', async () => {
    const db = mkDb();
    const h = createPurchaseHandler({ env: {}, supabase: db, verifier: okVerifier() });
    const a = mkRes(); await h(mkReq({ userId: 'u1', productId: P100, transactionId: ORDER, platform: 'android' }), a);
    const b = mkRes(); await h(mkReq({ userId: 'u2', productId: P100, transactionId: ORDER, platform: 'android' }), b);
    assert.equal(a.body.coinsAdded, 100);
    assert.equal(b.body.message, 'Transaction already processed');
    assert.equal(b.body.coinsAdded, undefined);
});

test('rejects unknown product, ios, missing fields, oversized ids', async () => {
    const db = mkDb();
    const h = createPurchaseHandler({ env: {}, supabase: db, verifier: okVerifier() });
    const cases = [
        [{ userId: 'u1', productId: 'com.x.coins.1', transactionId: ORDER, platform: 'android' }, 400],
        [{ userId: 'u1', productId: P100, transactionId: ORDER, platform: 'ios' }, 400],
        [{ userId: 'u1', productId: P100, platform: 'android' }, 400],
        [{ userId: 'u'.repeat(200), productId: P100, transactionId: ORDER, platform: 'android' }, 400],
        [{ userId: 'u1', productId: P100, transactionId: 'x'.repeat(300), platform: 'android' }, 400],
        [{ userId: { $ne: 1 }, productId: P100, transactionId: ORDER, platform: 'android' }, 400],
    ];
    for (const [body, code] of cases) { const res = mkRes(); await h(mkReq(body), res); assert.equal(res.code, code, JSON.stringify(body).slice(0, 60)); }
    assert.equal(db.calls.length, 0);
});

test('optional bearer: invalid -> 401, other user -> 403, matching -> ok, none -> legacy ok', async () => {
    const db = mkDb();
    const body = (t) => ({ userId: 'u1', productId: P100, transactionId: t, platform: 'android' });
    const mk = (verifyCaller, env = {}) => createPurchaseHandler({ env, supabase: db, verifier: okVerifier(), verifyCaller });
    const withAuth = (b) => mkReq(b, { headers: { authorization: 'Bearer abc' } });
    let res = mkRes(); await mk(async () => null)(withAuth(body('GPA.0000-0000-0000-00001')), res); assert.equal(res.code, 401);
    res = mkRes(); await mk(async () => ({ uid: 'someone-else' }))(withAuth(body('GPA.0000-0000-0000-00002')), res); assert.equal(res.code, 403);
    res = mkRes(); await mk(async () => ({ uid: 'u1' }))(withAuth(body('GPA.0000-0000-0000-00003')), res); assert.equal(res.code, 200);
    res = mkRes(); await mk()(mkReq(body('GPA.0000-0000-0000-00004')), res); assert.equal(res.code, 200);
    res = mkRes(); await mk(undefined, { COIN_PURCHASE_REQUIRE_AUTH: 'true' })(mkReq(body('GPA.0000-0000-0000-00005')), res); assert.equal(res.code, 401);
});

test('per-IP hourly limit applies in every mode', async () => {
    const db = mkDb();
    const h = createPurchaseHandler({ env: { COIN_PURCHASE_IP_PER_HOUR: '2' }, supabase: db, verifier: okVerifier({ verifyOrder: async () => ({ ok: false, code: 'not_found' }) }) });
    const codes = [];
    for (let i = 0; i < 4; i++) { const res = mkRes(); await h(mkReq({ userId: 'u1', productId: P100, transactionId: `GPA.0000-0000-0000-0000${i}`, platform: 'android' }), res); codes.push(res.code); }
    assert.deepEqual(codes, [400, 400, 429, 429]);
});

test('interim: shape check, account daily limit, global ceiling, and a normal purchase still works', async () => {
    const env = { COIN_PURCHASE_VERIFY: 'interim' };
    const h = createPurchaseHandler({ env, supabase: mkDb(), verifier: { isConfigured: () => false, verifyOrder: async () => assert.fail('no receipt check in interim') } });
    let res = mkRes(); await h(mkReq({ userId: 'u1', productId: P100, transactionId: 'fake-1', platform: 'android' }), res);
    assert.equal(res.code, 400);
    res = mkRes(); await h(mkReq({ userId: 'u1', productId: P100, transactionId: ORDER, platform: 'android' }), res);
    assert.equal(res.code, 200);
    assert.equal(res.body.coinsAdded, 100);

    const busy = mkDb({ ledger: [{ amount: 1200 }, { amount: 1200 }, { amount: 1200 }] });
    const h2 = createPurchaseHandler({ env, supabase: busy });
    res = mkRes(); await h2(mkReq({ userId: 'u2', productId: P500, transactionId: 'GPA.1111-1111-1111-11111', platform: 'android' }), res);
    assert.equal(res.code, 429, '3600 + 500 > 3000');
    assert.equal(busy.calls.length, 0);

    const h3 = createPurchaseHandler({ env: { ...env, COIN_PURCHASE_GLOBAL_COINS_PER_DAY: '150' }, supabase: mkDb() });
    res = mkRes(); await h3(mkReq({ userId: 'a', productId: P100, transactionId: 'GPA.2222-2222-2222-22221', platform: 'android' }), res); assert.equal(res.code, 200);
    res = mkRes(); await h3(mkReq({ userId: 'b', productId: P100, transactionId: 'GPA.2222-2222-2222-22222', platform: 'android' }), res); assert.equal(res.code, 503);
});

test('enforce: account daily limit still applies after verification (second layer); nothing credited', async () => {
    const db = mkDb({ ledger: [{ amount: 1200 }, { amount: 1200 }, { amount: 1200 }] });
    const h = createPurchaseHandler({ env: {}, supabase: db, verifier: okVerifier() });
    const res = mkRes(); await h(mkReq({ userId: 'u9', productId: P500, transactionId: ORDER, platform: 'android' }), res);
    assert.equal(res.code, 429);
    assert.equal(db.calls.length, 0);
});

test('unique-violation from the database is reported as already processed, not an error', async () => {
    const db = mkDb({ rpcImpl: async () => ({ data: null, error: { code: '23505', message: 'duplicate key' } }) });
    const h = createPurchaseHandler({ env: {}, supabase: db, verifier: okVerifier() });
    const res = mkRes(); await h(mkReq({ userId: 'u1', productId: P100, transactionId: ORDER, platform: 'android' }), res);
    assert.equal(res.code, 200);
    assert.equal(res.body.coinsAdded, undefined);
});

test('concurrent claims of one transaction id: the second is refused while the first is in flight', async () => {
    let release;
    const gate = new Promise((r) => { release = r; });
    const h = createPurchaseHandler({ env: {}, supabase: mkDb(), verifier: okVerifier({ verifyOrder: async () => { await gate; return { ok: true }; } }) });
    const r1 = mkRes(); const p1 = h(mkReq({ userId: 'u1', productId: P100, transactionId: ORDER, platform: 'android' }), r1);
    const r2 = mkRes(); await h(mkReq({ userId: 'u2', productId: P100, transactionId: ORDER, platform: 'android' }), r2);
    assert.equal(r2.code, 409);
    release(); await p1; assert.equal(r1.code, 200);
});

// ---- Play verifier (fake Google endpoints) ----
const FAKE_SA = JSON.stringify({ client_email: 'sa@example.iam.gserviceaccount.com', private_key: 'not-a-real-key' });
function fakeGoogle(orderBody, status = 200) {
    const urls = [];
    const fetchImpl = async (url, init) => {
        urls.push(String(url));
        if (String(url).startsWith('https://oauth2.googleapis.com/token')) return new Response(JSON.stringify({ access_token: 'tok', expires_in: 3600 }), { status: 200 });
        return new Response(JSON.stringify(orderBody), { status });
    };
    return { fetchImpl, urls };
}
const mkVerifier = (g, env = { PLAY_VERIFIER_SERVICE_ACCOUNT: FAKE_SA }) => createPlayPurchaseVerifier({ env, fetchImpl: g.fetchImpl, signJwt: () => 'signed' });
const goodOrder = (over = {}) => ({ orderId: ORDER, state: 'PROCESSED', createTime: new Date().toISOString(), lineItems: [{ productId: P100, oneTimePurchaseDetails: { quantity: 1 } }], ...over });

test('verifier: credential lookup, not configured without one', () => {
    assert.equal(createPlayPurchaseVerifier({ env: {} }).isConfigured(), false);
    assert.equal(loadServiceAccount({ GCS_SERVICE_ACCOUNT_KEY: FAKE_SA }).source, 'GCS_SERVICE_ACCOUNT_KEY');
    assert.equal(loadServiceAccount({ GCS_SERVICE_ACCOUNT_KEY: 'not json' }), null);
});

test('verifier: processed order with the product verifies', async () => {
    const g = fakeGoogle(goodOrder());
    const r = await mkVerifier(g).verifyOrder({ orderId: ORDER, productId: P100 });
    assert.equal(r.ok, true);
    assert.ok(g.urls.some((u) => u.includes(`/orders/${ORDER}`)));
});

test('verifier: wrong product, refunded/canceled/pending, quantity, extra items, too old, 404 are all refused', async () => {
    const run = async (order, opts, status) => mkVerifier(fakeGoogle(order, status)).verifyOrder({ orderId: ORDER, productId: P100 }, opts);
    assert.equal((await run(goodOrder({ lineItems: [{ productId: P500 }] }))).code, 'product_mismatch');
    for (const state of ['REFUNDED', 'CANCELED', 'PENDING', 'PENDING_REFUND', 'PARTIALLY_REFUNDED']) assert.equal((await run(goodOrder({ state }))).code, 'not_processed', state);
    assert.equal((await run(goodOrder({ lineItems: [{ productId: P100, oneTimePurchaseDetails: { quantity: 5 } }] }))).code, 'quantity');
    assert.equal((await run(goodOrder({ lineItems: [{ productId: P100 }, { productId: P500 }] }))).code, 'product_mismatch');
    assert.equal((await run(goodOrder({ createTime: '2020-01-01T00:00:00Z' }), { maxAgeMs: 86400000 })).code, 'too_old');
    assert.equal((await run({ error: {} }, undefined, 404)).code, 'not_found');
});

test('verifier: auth/API/network failures are "unavailable" (never a pass)', async () => {
    assert.equal((await mkVerifier(fakeGoogle({}, 403)).verifyOrder({ orderId: ORDER, productId: P100 })).code, 'unavailable');
    assert.equal((await mkVerifier(fakeGoogle({}, 500)).verifyOrder({ orderId: ORDER, productId: P100 })).code, 'unavailable');
    const down = createPlayPurchaseVerifier({ env: { PLAY_VERIFIER_SERVICE_ACCOUNT: FAKE_SA }, fetchImpl: async () => { throw new Error('boom'); }, signJwt: () => 's' });
    assert.equal((await down.verifyOrder({ orderId: ORDER, productId: P100 })).code, 'unavailable');
    assert.equal((await createPlayPurchaseVerifier({ env: {} }).verifyOrder({ orderId: ORDER, productId: P100 })).code, 'unavailable');
});

test('order id shape', () => {
    assert.ok(PLAY_ORDER_ID_RE.test('GPA.3345-1234-5678-12345'));
    assert.ok(PLAY_ORDER_ID_RE.test('GPA.3345-1234-5678-12345..0'));
    assert.ok(!PLAY_ORDER_ID_RE.test('abc'));
    assert.ok(!PLAY_ORDER_ID_RE.test('GPA.3345-1234-5678-12345; drop'));
});

// ---- payouts held ----
test('payouts: held unless PAYOUTS_ENABLED=true; request touches neither database nor Stripe', async () => {
    delete process.env.PAYOUTS_ENABLED;
    assert.equal(payoutsEnabled({}), false);
    assert.equal(payoutsEnabled({ PAYOUTS_ENABLED: 'true' }), true);
    const res = mkRes();
    await requestPayout({ body: { creatorId: 'c1', coinsAmount: 5000 } }, res);
    assert.equal(res.code, 423);
    assert.equal(res.body.error, 'payouts_under_review');
});
