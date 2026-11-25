// Play purchase tokens: verification at Play, one account per token, server amounts, one credit per paid period.
// No network and no database: Play is a stub, the database is an in-memory stand-in with the same unique keys.
import test from 'node:test';
import assert from 'node:assert/strict';

process.env.DOCKER_BUILD = 'true';
process.env.SUPABASE_URL = 'http://supabase.test';
process.env.SUPABASE_ANON_KEY = 'test-anon-key';
process.env.SUPABASE_SERVICE_KEY = 'test-service-key';

const { applySubscription, refreshDueSubscriptions, planFor, isPurchaseToken, monthOfPaidYear } = await import('../services/playPurchases.js');
const { createSubscriptionPurchaseHandler } = await import('../services/subscription-purchase.js');
const { createPurchaseHandler } = await import('../services/coinService.js');
const { createPlayPurchaseVerifier, PLAY_CREDENTIAL } = await import('../services/playPurchaseVerifier.js');

const NOW = Date.parse('2026-12-01T12:00:00Z');
const now = () => NOW;
const TOKEN = 'made-up-play-token-number-one';
const TOKEN2 = 'made-up-play-token-number-two';
const P = 'com.kreativekoala.riddleverse.premium';
const COINS = 'com.kreativekoala.riddleverse.coins.500';
const ORDER = 'GPA.1111-2222-3333-44444';

function mkDb({ failSave = false } = {}) {
    const tables = { play_purchases: [], user_subscriptions: [], users: [], coin_transactions: [] };
    const ledger = [];
    const writes = [];
    function from(name) {
        const rows = (tables[name] ||= []);
        const filters = [];
        let patch = null;
        const match = (r) => filters.every((f) => f(r));
        const q = {
            insert: async (row) => {
                writes.push(['insert', name]);
                if (name === 'play_purchases' && rows.some((r) => r.token_hash === row.token_hash)) return { error: { code: '23505' } };
                rows.push({ ...row });
                return { error: null };
            },
            upsert: async (row) => {
                writes.push(['upsert', name]);
                if (failSave) return { error: { message: 'boom' } };
                const at = rows.findIndex((r) => r.purchase_token_hash === row.purchase_token_hash);
                if (at < 0) rows.push({ ...row }); else Object.assign(rows[at], row);
                return { error: null };
            },
            update: (p) => { patch = p; writes.push(['update', name]); return q; },
            select: () => q,
            eq: (k, v) => { filters.push((r) => r[k] === v); return q; },
            in: (k, v) => { filters.push((r) => v.includes(r[k])); return q; },
            lt: (k, v) => { filters.push((r) => r[k] < v); return q; },
            gte: (k, v) => { filters.push((r) => r[k] >= v); return q; },
            like: () => q, order: () => q,
            limit: () => Promise.resolve({ data: name === 'coin_transactions' ? [] : rows.filter(match), error: null }),
            maybeSingle: async () => ({ data: rows.find(match) || null, error: null }),
            then: (ok, no) => { if (patch) rows.filter(match).forEach((r) => Object.assign(r, patch)); return Promise.resolve({ error: null }).then(ok, no); },
        };
        return q;
    }
    return {
        tables, ledger, writes, from,
        balance: (u) => ledger.filter((l) => l.user === u).reduce((s, l) => s + l.amount, 0),
        rpc: async (name, a) => {
            writes.push(['rpc', name]);
            if (ledger.some((l) => l.id === a.p_iap_transaction_id)) return { data: { success: true, duplicate: true }, error: null };
            ledger.push({ id: a.p_iap_transaction_id, user: a.p_user_id, amount: a.p_amount });
            tables.coin_transactions.push({ id: ledger.length, iap_transaction_id: a.p_iap_transaction_id });
            return { data: { success: true, new_balance: ledger.filter((l) => l.user === a.p_user_id).reduce((s, l) => s + l.amount, 0) }, error: null };
        },
    };
}

const subscription = ({ state = 'ACTIVE', product = `${P}.mo`, basePlanId = 'base', order = ORDER, days = 20, phase, offerId, ...more } = {}) => ({
    subscriptionState: `SUBSCRIPTION_STATE_${state}`,
    startTime: '2026-11-26T00:00:00Z',
    lineItems: [{
        productId: product, latestSuccessfulOrderId: order, expiryTime: new Date(NOW + days * 86_400_000).toISOString(),
        offerDetails: { basePlanId, ...(offerId ? { offerId } : {}) }, ...(phase ? { offerPhase: phase } : {}),
    }],
    ...more,
});
const play = (answer) => ({
    isConfigured: () => true,
    getSubscription: async () => (typeof answer === 'function' ? answer() : { ok: true, data: answer }),
    getProduct: async () => (typeof answer === 'function' ? answer() : { ok: true, data: answer }),
});
const apply = (db, data, userId = 'u1', purchaseToken = TOKEN) => applySubscription({ db, verifier: play(data), userId, purchaseToken, now });

function mkRes() {
    const r = { code: 200, body: null, headers: {} };
    r.status = (c) => { r.code = c; return r; };
    r.json = (b) => { r.body = b; return r; };
    r.setHeader = (k, v) => { r.headers[k] = v; };
    return r;
}
const signedIn = (uid) => async () => ({ uid });

test('plans and amounts come from the server table', () => {
    assert.equal(planFor(`${P}.mo`).coins, 150);
    assert.equal(planFor(`${P}.yr`).coins, 700);
    assert.equal(planFor(P, 'mo').tier, 'premium');
    assert.equal(planFor(P, 'yr').tier, 'premium_yearly');
    assert.equal(planFor(P, 'weekly'), null);
    assert.equal(planFor(COINS), null);
    assert.equal(isPurchaseToken(TOKEN), true);
    assert.equal(isPurchaseToken(ORDER), false);
    assert.equal(isPurchaseToken(''), false);
});

test('without the Play account, or when Play refuses it, nothing is written', async () => {
    const real = createPlayPurchaseVerifier({ env: { GCS_SERVICE_ACCOUNT_KEY: JSON.stringify({ client_email: 'a@b', private_key: 'k' }) }, credentials: [PLAY_CREDENTIAL], fetchImpl: () => assert.fail('no request without the Play account') });
    const refused = { isConfigured: () => true, getSubscription: async () => ({ ok: false, code: 'unavailable', detail: 'play api 401' }) };
    for (const verifier of [real, refused]) {
        const db = mkDb();
        const r = await applySubscription({ db, verifier, userId: 'u1', purchaseToken: TOKEN, now });
        assert.equal(r.status, 503);
        assert.equal(r.body.error, 'purchase_verification_unavailable');
        assert.equal(r.body.retryable, true);
        assert.deepEqual(db.writes, []);
    }
});

test('the token verifier asks the two Play endpoints with the Play account', async () => {
    const asked = [];
    let status = 200;
    const verifier = createPlayPurchaseVerifier({
        env: { [PLAY_CREDENTIAL]: JSON.stringify({ client_email: 'a@b', private_key: 'k' }) }, credentials: [PLAY_CREDENTIAL], signJwt: () => 'jwt',
        fetchImpl: async (url) => {
            if (url.includes('oauth2')) return { ok: true, status: 200, json: async () => ({ access_token: 't' }) };
            asked.push(url.split('/applications/')[1]);
            return { ok: status === 200, status, json: async () => ({}) };
        },
    });
    assert.equal((await verifier.getSubscription(TOKEN)).ok, true);
    assert.equal((await verifier.getProduct(COINS, TOKEN)).ok, true);
    assert.deepEqual(asked, [
        `com.kreativekoala.riddleverse/purchases/subscriptionsv2/tokens/${TOKEN}`,
        `com.kreativekoala.riddleverse/purchases/products/${COINS}/tokens/${TOKEN}`,
    ]);
    for (const [s, code] of [[401, 'unavailable'], [403, 'unavailable'], [500, 'unavailable'], [400, 'not_found'], [410, 'not_found']]) {
        status = s;
        assert.equal((await verifier.getSubscription(TOKEN)).code, code, String(s));
    }
});

test('a paid period is credited once, with the signup bonus once per account', async () => {
    const db = mkDb();
    const first = await apply(db, subscription());
    assert.equal(first.status, 200);
    assert.equal(first.body.coinsGranted, 300);
    assert.equal(first.body.subscription.tier, 'premium');
    assert.equal((await apply(db, subscription())).body.coinsGranted, 0);
    assert.equal((await apply(db, subscription({ order: `${ORDER}..0` }))).body.coinsGranted, 150);
    assert.equal(db.balance('u1'), 450);
    // a second subscription of the same account: its period, no second bonus
    assert.equal((await apply(db, subscription({ product: `${P}.yr`, order: 'GPA.9999-2222-3333-44444' }), 'u1', TOKEN2)).body.coinsGranted, 700);
    assert.equal(db.tables.user_subscriptions.length, 2);
});

test('one token cannot serve two accounts', async () => {
    const db = mkDb();
    await apply(db, subscription());
    const other = await apply(db, subscription({ order: `${ORDER}..0` }), 'u2');
    assert.equal(other.status, 409);
    assert.equal(db.balance('u2'), 0);
    assert.equal(db.tables.user_subscriptions[0].user_id, 'u1');
});

test('no coins for a free trial, a failing renewal, a hold, an ended period or a pending purchase', async () => {
    const cases = [
        [subscription({ phase: { freeTrial: {} } }), 200],
        [subscription({ offerId: 'yearly-trial' }), 200],
        [subscription({ state: 'IN_GRACE_PERIOD' }), 200],
        [subscription({ state: 'ON_HOLD' }), 200],
        [subscription({ state: 'EXPIRED', days: -3 }), 200],
        [subscription({ state: 'ACTIVE', days: -1 }), 200],
        [subscription({ state: 'PENDING' }), 202],
        [subscription({ product: 'com.other.product' }), 400],
    ];
    for (const [data, status] of cases) {
        const db = mkDb();
        const r = await apply(db, data);
        assert.equal(r.status, status, JSON.stringify(data).slice(0, 80));
        assert.equal(db.ledger.length, 0, JSON.stringify(data).slice(0, 80));
    }
    // the first paid renewal after a trial, and a cancelled but still paid period, are credited
    for (const data of [subscription({ offerId: 'yearly-trial', order: `${ORDER}..0` }), subscription({ phase: { basePrice: {} } }), subscription({ state: 'CANCELED' })]) {
        const db = mkDb();
        assert.equal((await apply(db, data)).body.coinsGranted, 300);
    }
});

test('a save that fails credits nothing and a retry completes it', async () => {
    const broken = mkDb({ failSave: true });
    assert.equal((await apply(broken, subscription())).status, 500);
    assert.equal(broken.ledger.length, 0);
});

test('the sweep credits a renewal once and does nothing without the Play account', async () => {
    const db = mkDb();
    await apply(db, subscription({ days: 1 }));
    const later = () => NOW + 2 * 86_400_000;
    assert.deepEqual(await refreshDueSubscriptions({ db, verifier: { isConfigured: () => false }, now: later }), { checked: 0 });
    const renewed = play(subscription({ order: `${ORDER}..0`, days: 32 }));
    assert.deepEqual(await refreshDueSubscriptions({ db, verifier: renewed, now: later }), { checked: 1 });
    assert.deepEqual(await refreshDueSubscriptions({ db, verifier: renewed, now: later }), { checked: 0 });
    assert.equal(db.balance('u1'), 450);
});

const at = (iso) => () => Date.parse(iso);
// A year bought on 2026-12-01, as Play reports it.
const yearly = ({ order = ORDER, expiry = '2027-12-01T12:00:00Z', ...more } = {}) => {
    const data = subscription({ product: `${P}.yr`, order, ...more });
    data.lineItems[0].expiryTime = expiry;
    return data;
};
const applyAt = (db, data, iso) => applySubscription({ db, verifier: play(data), userId: 'u1', purchaseToken: TOKEN, now: at(iso) });

test('months of a paid year run from the purchase day, and short months end on their last day', () => {
    const expiry = Date.parse('2027-12-01T12:00:00Z');
    assert.equal(monthOfPaidYear(expiry, Date.parse('2026-12-01T12:00:00Z')), 1);
    assert.equal(monthOfPaidYear(expiry, Date.parse('2027-01-01T11:59:59Z')), 1);
    assert.equal(monthOfPaidYear(expiry, Date.parse('2027-01-01T12:00:00Z')), 2);
    assert.equal(monthOfPaidYear(expiry, Date.parse('2027-11-01T12:00:00Z')), 12);
    assert.equal(monthOfPaidYear(expiry, expiry - 1), 12);
    // a renewal reported a few hours before the old year ended counts as the new year's first month
    assert.equal(monthOfPaidYear(expiry, Date.parse('2026-11-30T20:00:00Z')), 1);
    // bought on the 31st
    const end = Date.parse('2028-01-31T00:00:00Z');
    assert.equal(monthOfPaidYear(end, Date.parse('2027-02-27T23:59:59Z')), 1);
    assert.equal(monthOfPaidYear(end, Date.parse('2027-02-28T00:00:00Z')), 2);
    assert.equal(monthOfPaidYear(end, Date.parse('2027-03-30T23:59:59Z')), 2);
    assert.equal(monthOfPaidYear(end, Date.parse('2027-03-31T00:00:00Z')), 3);
});

test('a yearly plan is credited once in each paid month, twelve times a year, never twice', async () => {
    const db = mkDb();
    assert.equal((await applyAt(db, yearly(), '2026-12-01T12:00:00Z')).body.coinsGranted, 1200); // 700 and the signup bonus
    assert.equal((await applyAt(db, yearly(), '2026-12-31T00:00:00Z')).body.coinsGranted, 0);
    assert.equal((await applyAt(db, yearly(), '2027-01-01T12:00:00Z')).body.coinsGranted, 700);
    assert.equal((await applyAt(db, yearly(), '2027-01-15T00:00:00Z')).body.coinsGranted, 0);
    for (let m = 3; m <= 12; m += 1) {
        const day = new Date(Date.UTC(2026, 11 + m - 1, 2)).toISOString();
        assert.equal((await applyAt(db, yearly(), day)).body.coinsGranted, 700, day);
        assert.equal((await applyAt(db, yearly(), day)).body.coinsGranted, 0, day);
    }
    assert.equal(db.balance('u1'), 12 * 700 + 500);
    // the next paid year is a new order
    const renewed = yearly({ order: `${ORDER}..0`, expiry: '2028-12-01T12:00:00Z' });
    assert.equal((await applyAt(db, renewed, '2027-12-01T13:00:00Z')).body.coinsGranted, 700);
});

test('months that passed are not made up', async () => {
    const db = mkDb();
    // first seen in the sixth month of the year: this month only
    assert.equal((await applyAt(db, yearly(), '2027-05-10T00:00:00Z')).body.coinsGranted, 1200);
    // not seen again until the ninth month: one month, not three
    assert.equal((await applyAt(db, yearly(), '2027-08-10T00:00:00Z')).body.coinsGranted, 700);
    assert.equal(db.balance('u1'), 1900);
});

test('a monthly plan is still credited once per billing period, whenever it is asked again', async () => {
    const db = mkDb();
    const data = subscription({ days: 31 });
    assert.equal((await apply(db, data)).body.coinsGranted, 300);
    const nearEnd = () => NOW + 30 * 86_400_000;
    assert.equal((await applySubscription({ db, verifier: play(data), userId: 'u1', purchaseToken: TOKEN, now: nearEnd })).body.coinsGranted, 0);
});

test('the sweep credits each new month of a running yearly plan once, and only after Play confirms it', async () => {
    const db = mkDb();
    await applyAt(db, yearly(), '2026-12-01T12:00:00Z');
    let asked = 0;
    const verifier = play(() => { asked += 1; return { ok: true, data: yearly() }; });
    assert.deepEqual(await refreshDueSubscriptions({ db, verifier, now: at('2026-12-20T00:00:00Z') }), { checked: 0 });
    assert.equal(asked, 0);
    assert.deepEqual(await refreshDueSubscriptions({ db, verifier, now: at('2027-01-02T00:00:00Z') }), { checked: 1 });
    assert.deepEqual(await refreshDueSubscriptions({ db, verifier, now: at('2027-01-02T06:00:00Z') }), { checked: 0 });
    assert.equal(asked, 1);
    assert.equal(db.balance('u1'), 1900);
    // on hold at Play: asked, not paid
    const held = await refreshDueSubscriptions({ db, verifier: play(yearly({ state: 'ON_HOLD' })), now: at('2027-02-02T00:00:00Z') });
    assert.deepEqual(held, { checked: 1 });
    assert.equal(db.balance('u1'), 1900);
});

test('the subscription route needs a signed-in user, a real token, and ignores client amounts', async () => {
    const db = mkDb();
    const h = createSubscriptionPurchaseHandler({ supabase: db, verifier: play(subscription()), now, verifyCaller: signedIn('u1') });
    const anonymous = createSubscriptionPurchaseHandler({ supabase: db, verifier: play(subscription()), now, verifyCaller: async () => null });
    const send = async (handler, body) => { const res = mkRes(); await handler({ body, headers: {}, ip: '1.1.1.1' }, res); return res; };
    // what versions up to 188 send
    assert.equal((await send(anonymous, { userId: 'u1', email: 'x@y.z', purchaseToken: ORDER, monthlyCoins: 99999 })).code, 401);
    assert.equal((await send(h, { userId: 'u1', purchaseToken: ORDER })).code, 400);
    assert.equal((await send(h, { userId: 'u2', purchaseToken: TOKEN })).code, 403);
    assert.deepEqual(db.writes, []);
    const ok = await send(h, { purchaseToken: TOKEN, monthlyCoins: 99999, signupBonus: 99999, tier: 'unlimited', productId: `${P}.yr` });
    assert.equal(ok.code, 200);
    assert.equal(ok.body.coinsGranted, 300);
    assert.equal(db.balance('u1'), 300);
});

const pack = (more = {}) => ({ purchaseState: 0, orderId: ORDER, purchaseTimeMillis: String(Date.parse('2026-11-28T00:00:00Z')), ...more });
async function buy(db, data, { uid = 'u1', body = {}, verifier = play(data), authed = true } = {}) {
    const h = createPurchaseHandler({ env: {}, supabase: db, now, tokenVerifier: verifier, verifier: { isConfigured: () => true, verifyOrder: async () => ({ ok: true, order: { orderId: ORDER } }) }, verifyCaller: signedIn(uid) });
    const res = mkRes();
    await h({ body: { userId: uid, productId: COINS, purchaseToken: TOKEN, transactionId: ORDER, platform: 'android', ...body }, headers: authed ? { authorization: 'Bearer x' } : {}, ip: '2.2.2.2' }, res);
    return res;
}

test('coin pack by token: server amount, once, under the same order id as the older route', async () => {
    const db = mkDb();
    const a = await buy(db, pack(), { body: { amount: 99999 } });
    assert.equal(a.code, 200);
    assert.equal(a.body.coinsAdded, 500);
    assert.equal((await buy(db, pack())).body.message, 'Transaction already processed');
    // the order-id route cannot credit it again, with or without the token route first
    const older = await buy(db, pack(), { body: { purchaseToken: undefined } });
    assert.equal(older.body.message, 'Transaction already processed');
    const olderFirst = mkDb();
    await buy(olderFirst, pack(), { body: { purchaseToken: undefined } });
    assert.equal((await buy(olderFirst, pack())).body.message, 'Transaction already processed');
    assert.equal(db.balance('u1'), 500);
    assert.equal(olderFirst.balance('u1'), 500);
});

test('coin pack by token: one account, signed in, purchased, not older than this route', async () => {
    const db = mkDb();
    await buy(db, pack());
    const other = await buy(db, pack(), { uid: 'u2' });
    assert.equal(other.code, 409);
    assert.equal(db.balance('u2'), 0);
    for (const [data, opts, code] of [
        [pack(), { authed: false }, 401],
        [pack({ purchaseState: 1 }), {}, 400],
        [pack({ purchaseState: 2 }), {}, 202],
        [pack({ quantity: 3 }), {}, 400],
        [pack({ purchaseTimeMillis: String(Date.parse('2026-03-01T00:00:00Z')) }), {}, 400],
        [pack(), { verifier: { isConfigured: () => false } }, 503],
        [null, { verifier: play(() => ({ ok: false, code: 'unavailable', detail: 'play api 401' })) }, 503],
        [null, { verifier: play(() => ({ ok: false, code: 'not_found' })) }, 400],
        [pack(), { body: { purchaseToken: ORDER } }, 400],
    ]) {
        const fresh = mkDb();
        const r = await buy(fresh, data, opts);
        assert.equal(r.code, code, `${code} ${JSON.stringify(r.body)}`);
        assert.deepEqual(fresh.writes, []);
    }
});
