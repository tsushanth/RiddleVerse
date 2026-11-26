// Reads about one account are answered for that account's own sign-in, in the shapes the apps parse.
// No real Supabase or Firebase: fetch and token verification are replaced.
import test from 'node:test';
import assert from 'node:assert/strict';
import http from 'node:http';

process.env.DOCKER_BUILD = 'true';
process.env.SUPABASE_URL = 'http://supabase.test';
process.env.SUPABASE_ANON_KEY = 'test-anon-key';
process.env.SUPABASE_SERVICE_KEY = 'test-service-key';
console.log = () => {};
console.error = () => {};
console.warn = () => {};

const queries = [];
const rows = {
    user_subscriptions: [{ id: 's1', subscription_tier: 'premium', status: 'active', product_id: 'p', subscription_start_date: '2026-01-01', subscription_end_date: '2999-01-01' }],
    users: { user_id: 'uid-me', subscription_tier: 'premium', daily_count: 3 },
    user_coins: { balance: 7, total_purchased: 100, total_spent: 90, total_earned: 1 },
    coin_transactions: [{ id: 't1', amount: 5 }],
    payout_requests: [{ id: 'p1', coins_amount: 1000, usd_amount: 7, status: 'failed', failure_reason: 'reason', created_at: 'x', completed_at: null }],
    user_topics: [{ topic: 'space' }],
    daily_puzzles: [],
    leaderboard: { name: 'someone@example.test', score: 4, last_updated: 'x' },
};
const realFetch = globalThis.fetch;
globalThis.fetch = async (url, init) => {
    const u = new URL(String(url));
    if (u.hostname === '127.0.0.1') return realFetch(url, init);
    if (u.hostname !== 'supabase.test') throw new Error('unexpected outbound request in test');
    queries.push(decodeURIComponent(u.href));
    const body = rows[u.pathname.split('/').pop()] ?? [];
    return new Response(JSON.stringify(body), { status: 200, headers: { 'content-type': 'application/json' } });
};

const realSetInterval = globalThis.setInterval;
globalThis.setInterval = (...a) => realSetInterval(...a).unref();
const { default: express } = await import('express');
const identity = await import('../auth/verifyIdentity.js');
const load = async (f) => (await import(`../routes/${f}`)).default;
const app = express();
app.use(express.json());
const subscription = await load('subscription.routes.js');
const notification = await load('notificationConfig.routes.js');
app.use('/', await load('account.routes.js'));
app.use('/', subscription);
app.use('/api', subscription);
app.use('/api/notifications', notification);
app.use('/', notification);
app.use('/', await load('scoring.routes.js'));
app.use('/api/coins', await load('coins.routes.js'));
app.use('/api/payouts', await load('payout.routes.js'));
app.use('/api/daily-puzzles', await load('dailyPuzzles.routes.js'));
app.use('/api', await load('simpleUserConfig.js'));
globalThis.setInterval = realSetInterval;

identity.__setDeps({
    getFirebaseAuth: async () => ({
        verifyIdToken: async (t) => {
            if (!t.startsWith('uid-')) throw Object.assign(new Error('bad token'), { code: 'auth/argument-error' });
            return { uid: t, email: `${t}@example.test`, email_verified: t !== 'uid-unverified' };
        },
    }),
});

const server = http.createServer(app);
await new Promise((r) => server.listen(0, r));
const base = `http://127.0.0.1:${server.address().port}`;
test.after(() => { server.close(); globalThis.fetch = realFetch; });

async function get(path, token) {
    queries.length = 0;
    const r = await realFetch(base + path, { headers: token ? { authorization: `Bearer ${token}` } : {} });
    const text = await r.text();
    return { status: r.status, body: JSON.parse(text), queried: queries.length };
}

const ME = 'uid-me';
const MAIL = 'uid-me@example.test';
const OTHERS = [undefined, 'not-a-token', 'uid-someone-else'];

test('subscription status needs the sign-in of the account it names, before anything is looked up', async () => {
    for (const prefix of ['', '/api']) {
        for (const query of [`?userId=${ME}`, `?email=${MAIL}`, '']) {
            for (const token of [undefined, 'not-a-token']) {
                const r = await get(`${prefix}/subscription-status${query}`, token);
                assert.deepEqual([r.status, r.queried], [401, 0], query);
            }
        }
        for (const query of [`?userId=${ME}`, `?email=${MAIL}`, `?userId=uid-someone-else&email=${MAIL}`, `?userId=a&userId=b`]) {
            const r = await get(`${prefix}/subscription-status${query}`, 'uid-someone-else');
            assert.deepEqual([r.status, r.queried], [403, 0], query);
        }
        const unverified = await get(`${prefix}/subscription-status?email=uid-unverified@example.test`, 'uid-unverified');
        assert.deepEqual([unverified.status, unverified.queried], [403, 0]);
    }
});

test('subscription status answers the signed-in account, by its own id', async () => {
    for (const query of ['', `?userId=${ME}`, `?email=${MAIL.toUpperCase()}`]) {
        const r = await get(`/api/subscription-status${query}`, ME);
        assert.equal(r.body.subscription.tier, 'premium', query);
        assert.equal(queries.length, 1);
        assert.match(queries[0], /user_id=eq\.uid-me/);
        assert.doesNotMatch(queries[0], /email=eq/);
    }
});

test('account reads refuse everyone but the account', async () => {
    const paths = [`/api/coins/transactions?userId=${ME}`, `/api/daily-puzzles?email=${MAIL}&topic=space`, `/api/daily-puzzles/topics?email=${MAIL}`,
        `/api/daily-puzzles/history?email=${MAIL}`, `/api/daily-puzzles/statistics?email=${MAIL}`];
    for (const path of paths) {
        for (const token of OTHERS) {
            const r = await get(path, token);
            assert.deepEqual([r.status, r.queried], [token === 'uid-someone-else' ? 403 : 401, 0], path);
        }
        const own = await get(path, ME);
        assert.ok(![401, 403].includes(own.status), path);
        assert.ok(own.queried > 0, path);
    }
});

test('fallback answers keep their shape and look nothing up', async () => {
    for (const token of OTHERS) {
        const rewards = await get(`/get-user-rewards-badges?email=${MAIL}`, token);
        assert.deepEqual([rewards.status, rewards.body, rewards.queried], [200, { rewards: [], badges: [] }, 0]);
        for (const path of ['/list-user-daily-topics', '/api/notifications/list-user-daily-topics']) {
            const topics = await get(`${path}?email=${MAIL}`, token);
            assert.deepEqual([topics.status, topics.body, topics.queried], [200, { topics: [] }, 0]);
        }
        const config = await get(`/api/user-config?userId=${ME}&email=probe`, token);
        assert.equal(config.queried, 0);
        assert.deepEqual([config.body.subscriptionTier, config.body.shouldShowTrialPrompt, config.body.trialPromptReason, config.body.usageStats.dailyGenerations], ['free', false, 'none', 0]);
    }
});

test('the same paths answer in full for the account itself', async () => {
    assert.deepEqual((await get(`/list-user-daily-topics?email=${MAIL}`, ME)).body.topics, ['space']);
    const config = await get(`/api/user-config?userId=${ME}&email=probe`, ME);
    assert.deepEqual([config.body.subscriptionTier, config.body.usageStats.dailyGenerations], ['premium', 3]);
});

test('coin balance and payout history carry the extra fields only for the account itself', async () => {
    for (const token of OTHERS) {
        assert.deepEqual((await get(`/api/coins/balance?userId=${ME}`, token)).body, { success: true, balance: 7 });
        const [payout] = (await get(`/api/payouts/history?creatorId=${ME}`, token)).body.payouts;
        assert.equal(payout.coins_amount, 1000);
        assert.ok(!('failure_reason' in payout));
    }
    assert.equal((await get(`/api/coins/balance?userId=${ME}`, ME)).body.totalPurchased, 100);
    assert.equal((await get(`/api/payouts/history?creatorId=${ME}`, ME)).body.payouts[0].failure_reason, 'reason');
});

test('a score row shows a display name, never a full address', async () => {
    assert.equal((await get(`/get-score?userId=${ME}`)).body.name, 'some***');
});
