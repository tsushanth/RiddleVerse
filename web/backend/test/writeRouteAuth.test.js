// Who may change things. Routes no client calls need the admin key and refuse before any data access; routes the shipped
// apps call without a credential stay open and are listed here on purpose. Nothing real is contacted and no mail is sent.
import test from 'node:test';
import assert from 'node:assert/strict';
import http from 'node:http';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

process.env.DOCKER_BUILD = 'true';
process.env.SUPABASE_URL = 'http://supabase.test';
process.env.SUPABASE_ANON_KEY = 'test-anon-key';
process.env.SUPABASE_SERVICE_KEY = 'test-service-key';
const KEY = 'unit-test-admin-key-0123456789';
process.env.ADMIN_KEY = KEY;
delete process.env.SCHEDULER_SECRET;
delete process.env.SHARE_SMTP_USER;
delete process.env.SHARE_SMTP_PASS;
console.log = () => {};
console.error = () => {};
console.warn = () => {};

const calls = [];
let reply = () => ({ status: 200, body: [] });
const realFetch = globalThis.fetch;
globalThis.fetch = async (url, init) => {
    const u = String(url);
    if (u.startsWith('http://supabase.test')) {
        const call = { table: new URL(u).pathname.split('/').pop(), method: init?.method || 'GET', body: init?.body, accept: new Headers(init?.headers).get('accept') || '' };
        calls.push(call);
        const r = reply(call);
        return new Response(JSON.stringify(r.body), { status: r.status, headers: { 'content-type': 'application/json' } });
    }
    if (u.startsWith('http://127.0.0.1')) return realFetch(url, init);
    throw new Error('unexpected outbound request in test');
};

const realSetInterval = globalThis.setInterval;
globalThis.setInterval = (...a) => realSetInterval(...a).unref();
const { default: express } = await import('express');
const firebase = await import('../config/firebaseAdmin.js');
const identity = await import('../auth/verifyIdentity.js');
const load = async (f) => (await import(`../routes/${f}`)).default;
const { createGameCreationGuard, classify } = await import('../middleware/gameCreationGuard.js');
const mounts = [
    ['/', await load('subscription.routes.js')],
    ['/', await load('puzzleEndpoints.routes.js')],
    ['/', await load('scoring.routes.js')],
    ['/', await load('sharing.routes.js')],
    ['/', await load('notificationConfig.routes.js')],
    ['/api/ltv', await load('ltv.routes.js')],
    ['/api/daily-puzzle-cache', await load('dailyPuzzleCache.routes.js')],
    ['/api/games', await load('games.routes.js')],
    ['/api/game-creation', createGameCreationGuard(), await load('gameCreation.routes.js')],
    ['/api/coins', await load('coins.routes.js')],
    ['/api/leaderboard', await load('leaderboard.routes.js')],
    ['/api', await load('simpleUserConfig.js')],
];
const { dailyPuzzleCache } = await import('../services/dailyPuzzleCache.js');
const { shareRecipient } = await import('../services/coinService.js');
const { singleRecipient, mailName } = await import('../utils/shareMail.js');
globalThis.setInterval = realSetInterval;

let firestoreCalls = 0;
if (firebase.db) Object.defineProperty(firebase.db, 'collection', { value: () => { firestoreCalls += 1; throw new Error('no firestore in tests'); }, configurable: true });
identity.__setDeps({
    getFirebaseAuth: async () => ({
        verifyIdToken: async (t) => {
            if (!t.startsWith('uid-')) throw Object.assign(new Error('bad token'), { code: 'auth/argument-error' });
            return { uid: t, email: `${t}@example.test`, email_verified: true };
        },
    }),
    getSupabaseUrl: () => undefined,
});

const app = express();
app.use(express.json());
for (const [p, ...handlers] of mounts) app.use(p, ...handlers);
const server = http.createServer(app);
await new Promise((r) => server.listen(0, r));
const base = `http://127.0.0.1:${server.address().port}`;
test.after(() => { server.close(); dailyPuzzleCache.redis.disconnect?.(); globalThis.fetch = realFetch; });

async function call(method, p, { body, headers = {} } = {}) {
    const r = await realFetch(base + p, { method, headers: { 'content-type': 'application/json', ...headers }, body: body && JSON.stringify(body) });
    return { status: r.status, text: await r.text() };
}
const settle = () => new Promise((r) => setTimeout(r, 50));

const ADMIN_ONLY = [
    ['PUT', '/api/games/g1'], ['PATCH', '/api/games/g1'], ['POST', '/api/games'], ['POST', '/api/games/g1/play'],
    ['POST', '/api/games/screenshots'], ['POST', '/api/games/screenshots/s1/use'], ['POST', '/api/games/upload-screenshot'],
    ['POST', '/delete-score'], ['POST', '/api/leaderboard/delete-score'], ['DELETE', '/api/leaderboard/puzzle/p1/user/u1'],
    ['POST', '/delete-puzzle-leaderboard-entry'], ['GET', '/reset-puzzle-progress?userId=u&puzzleType=math&difficulty=easy'],
    ['POST', '/sync-subscription-status'], ['POST', '/subscription-cancel'], ['GET', '/subscription-metrics'],
    ['POST', '/api/trial-prompt-shown'], ['POST', '/api/game-creation/g1/revert/abc1234'],
    ['POST', '/api/daily-puzzle-cache/scheduler/trigger-daily-refresh'], ['POST', '/api/daily-puzzle-cache/scheduler/trigger-health-check'],
    ['GET', '/api/ltv/audiences'], ['GET', '/stats'],
];

for (const [method, p] of ADMIN_ONLY) {
    test(`${method} ${p.split('?')[0]} refuses without the admin key, before any data access`, async () => {
        await settle();
        calls.length = 0;
        firestoreCalls = 0;
        const body = method === 'GET' ? undefined : { title: 't', name: 'n', puzzleId: 'p1', userId: 'u1', session: 1 };
        for (const headers of [{}, { 'x-admin-key': 'wrong' }, { authorization: 'Bearer uid-someone' }, { 'x-scheduler-secret': 'x' }]) {
            const r = await call(method, p, { body, headers });
            // 403: on game-creation routes a token for a different user is refused by the guard before the admin check.
            assert.ok(r.status === 401 || (r.status === 403 && headers.authorization), `${JSON.stringify(headers)} -> ${r.status} ${r.text.slice(0, 80)}`);
        }
        assert.deepEqual([calls.length, firestoreCalls], [0, 0]);
    });
}

test('the admin key gets through', async () => {
    reply = () => ({ status: 200, body: { id: 'g1', title: 't', creator_id: 'c' } });
    const r = await call('PUT', '/api/games/g1', { body: { title: 'new' }, headers: { 'x-admin-key': KEY } });
    assert.equal(r.status, 200, r.text);
    reply = () => ({ status: 200, body: [] });
});

test('routes the apps call without a credential still answer them (validation errors, not 401)', async () => {
    for (const [method, p] of [['POST', '/update-score'], ['POST', '/update-puzzle-leaderboard'], ['POST', '/api/leaderboard/puzzle/riddle-1'],
        ['POST', '/api/coins/spend'], ['POST', '/api/coins/purchase'], ['DELETE', '/api/game-creation/g1'], ['POST', '/api/game-creation/save'], ['POST', '/share-riddle']]) {
        const r = await call(method, p, { body: {} });
        assert.equal(r.status, 400, `${method} ${p} -> ${r.status} ${r.text.slice(0, 80)}`);
    }
});

function gameStore({ archiveFails = false } = {}) {
    return (c) => {
        if (c.table === 'deleted_games') return archiveFails ? { status: 404, body: { code: '42P01', message: 'missing' } } : { status: 201, body: [] };
        if (c.table === 'custom_games') {
            const row = { id: 'g1', creator_id: 'uid-owner', title: 'T', html_content: 'x' };
            return { status: 200, body: c.accept.includes('pgrst.object') ? row : [row] };
        }
        return { status: 200, body: [] };
    };
}

test('game delete stores a copy first, then deletes', async () => {
    reply = gameStore();
    calls.length = 0;
    const r = await call('DELETE', '/api/game-creation/g1?userId=uid-owner');
    assert.equal(r.status, 200, r.text);
    const writes = calls.filter((c) => c.method !== 'GET').map((c) => `${c.method} ${c.table}`);
    assert.deepEqual(writes, ['POST deleted_games', 'DELETE custom_games']);
    const copy = JSON.parse(calls.find((c) => c.table === 'deleted_games').body);
    assert.deepEqual([copy.game_id, copy.creator_id, copy.verified, copy.game.html_content], ['g1', 'uid-owner', false, 'x']);
});

test('game delete does nothing when the copy cannot be stored, or for a non-owner, or with someone else\'s token', async () => {
    for (const [opts, path_, headers, status] of [
        [{ archiveFails: true }, '/api/game-creation/g1?userId=uid-owner', {}, 500],
        [{}, '/api/game-creation/g1?userId=uid-other', {}, 403],
        [{}, '/api/game-creation/g1?userId=uid-owner', { authorization: 'Bearer uid-other' }, 403],
        [{}, '/api/game-creation/g1?userId=uid-owner', { authorization: 'Bearer nonsense' }, 401],
    ]) {
        reply = gameStore(opts);
        calls.length = 0;
        const r = await call('DELETE', path_, { headers });
        assert.equal(r.status, status, r.text);
        assert.equal(calls.filter((c) => c.method === 'DELETE').length, 0);
    }
    reply = () => ({ status: 200, body: [] });
    assert.equal(classify('DELETE', '/g1'), 'delete'); // so GAME_CREATION_REQUIRE_AUTH covers deletes once clients send tokens
});

test('the creator share only goes to the real creator of the game', async () => {
    const db = (creator) => ({ from: () => ({ select: () => ({ eq: () => ({ maybeSingle: async () => ({ data: creator ? { creator_id: creator } : null, error: null }) }) }) }) });
    assert.equal(await shareRecipient(db('maker'), { userId: 'u', gameId: 'g', creatorId: 'maker' }), 'maker');
    assert.equal(await shareRecipient(db('maker'), { userId: 'u', gameId: 'g', creatorId: 'attacker' }), null);
    assert.equal(await shareRecipient(db(null), { userId: 'u', gameId: 'g', creatorId: 'attacker' }), null);
    assert.equal(await shareRecipient(db('maker'), { userId: 'u', creatorId: 'maker' }), null);
    assert.equal(await shareRecipient(db('u'), { userId: 'u', gameId: 'g', creatorId: 'u' }), null);
});

test('coin spend: a token for another account or a bad token is refused before anything is read', async () => {
    for (const [headers, status] of [[{ authorization: 'Bearer uid-other' }, 403], [{ authorization: 'Bearer nonsense' }, 401]]) {
        calls.length = 0;
        const r = await call('POST', '/api/coins/spend', { body: { userId: 'uid-victim', reason: 'continue_play' }, headers });
        assert.equal(r.status, status, r.text);
        assert.equal(calls.length, 0);
    }
});

test('share mail: one plain recipient, names carry no links or markup', () => {
    assert.equal(singleRecipient(' friend@example.test '), 'friend@example.test');
    for (const bad of ['a@x.test,b@x.test', 'a@x.test; b@x.test', 'Name <a@x.test>', 'a@x', 'a b@x.test', ['a@x.test'], '']) assert.equal(singleRecipient(bad), null, String(bad));
    assert.equal(mailName('Sam', 'there'), 'Sam');
    assert.equal(mailName('<a href=x>win</a> http://evil.test/x www.evil.test evil.com/a me@evil.test', 'there'), '&lt;a href=x&gt;win&lt;/a&gt;');
    assert.equal(mailName('https://evil.test', 'A friend'), 'A friend');
    assert.ok(mailName('x'.repeat(500), 'there').length <= 40);
});

test('share route: lists are refused and one recipient gets at most three a day', async () => {
    const share = (recipientEmail) => call('POST', '/share-riddle', { body: { puzzleId: 'p1', recipientEmail, recipientName: 'N', senderId: 'S' } });
    assert.equal((await share('a@x.test,b@x.test')).status, 400);
    const statuses = [];
    for (let i = 0; i < 4; i++) statuses.push((await share('same@example.test')).status);
    assert.deepEqual(statuses, [503, 503, 503, 429]); // 503: no mail account in tests, so nothing is ever sent
});

// ------------------------------------------------------------------ every state-changing route is accounted for
const backend = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const GUARDS = /requireAdminKey|requireNotificationAdmin|validateAdmin|verifyCreator|requireSchedulerOrAdmin/;
/** Open on purpose. The note says who calls it, or what protects it instead of a route guard. */
const OPEN = {
    'server.js': {
        '/api/payouts/webhook': 'Stripe signature', '/register-fcm-token': 'apps; ownership rules in the handler',
        '/api/puzzles/check-answer': 'apps', '/api/game-score/:gameId': 'the play page', '/api/admin/silence-error': 'admin key checked in the handler',
        '/api/admin/unsilence-error': 'admin key checked in the handler', '/api/admin/clear-silences': 'admin key checked in the handler',
    },
    'account.routes.js': { '/delete-user-account': 'own sign-in or admin key, checked in the handler', '/save-user-preferences': 'iOS' },
    'auth.routes.js': { '/google': 'sign-in' },
    'authLink.routes.js': { '/link-supabase': 'sign-in', '/supabase-exchange': 'sign-in' },
    'chatScores.routes.js': { '/': 'generated game pages and apps' },
    'coins.routes.js': { '/purchase': 'apps; Play verification', '/spend': 'apps; optional token', '/award': 'iOS; one fixed reward per account' },
    'dailyPuzzles.routes.js': { '/register-fcm-token': 'apps' },
    'feedback.routes.js': { '/submit-feedback': 'Android', '/submit-rating': 'iOS' },
    'gameCreation.routes.js': {
        '/generate': 'apps, web', '/save': 'apps, web', '/:gameId/difficulty/:level/generate': 'apps', '/:gameId/tweak': 'apps; owner id check',
        '/:gameId/customize': 'apps', '/remix': 'apps', '/:gameId/play-event/start': 'iOS', '/:gameId/next-level/generate': 'iOS',
        '/:gameId': 'apps delete; owner id check, copy kept',
    },
    'generation.routes.js': { '/generate-riddle': 'apps; generateRiddleGuard' },
    'leaderboard.routes.js': { '/update-score': 'web', '/puzzle/:puzzleId': 'apps' },
    'notificationConfig.routes.js': { '/test-notification': 'apps; capped in the handler' },
    'puzzleEndpoints.routes.js': { '/batch-fetch-puzzles': 'apps', '/check-answer': 'apps, web', '/request-puzzle-regeneration': 'iOS', '/update-puzzle-leaderboard': 'apps' },
    'puzzleMonitoring.routes.js': { '/': 'anonymous event ingest' },
    'scoring.routes.js': { '/update-score': 'apps' },
    'sessions.routes.js': { '/start': 'apps, web', '/event': 'play page', '/end': 'play page' },
    'sharing.routes.js': { '/share-riddle': 'apps; one recipient, rate limited' },
    'subscription.routes.js': { '/subscription-purchase': 'apps' },
    'telegram.routes.js': { '/api/telegram/webhook': 'secret header', '/api/telegram/score': 'Telegram init data', '/api/telegram/link/redeem': 'one-time code', '/api/telegram/daily-push': 'cron secret' },
    'version.routes.js': { '/check-version': 'read only' },
};

test('no state-changing route is open unless it is listed with a reason', () => {
    const server = fs.readFileSync(path.join(backend, 'server.js'), 'utf8');
    const files = [...server.matchAll(/from\s+['"]\.\/routes\/([^'"]+)['"]/g)].map((m) => m[1]);
    assert.ok(files.length > 25);
    const open = [];
    const seen = new Set();
    for (const file of ['server.js', ...new Set(files)]) {
        const src = fs.readFileSync(path.join(backend, file === 'server.js' ? file : `routes/${file}`), 'utf8');
        const routerGuarded = new RegExp(`router\\.use\\(\\s*(?:${GUARDS.source})`).test(src);
        const re = file === 'server.js'
            ? /\bapp\.(post|put|delete|patch|all)\(\s*(['"][^'"]+['"])([^\n]*)/g
            : /\b(?:\w*[rR]outer|ingest|admin)\.(post|put|delete|patch|all)\(\s*(\[[^\]]*\]|['"][^'"]+['"])([^\n]*)/g;
        for (const m of src.matchAll(re)) {
            for (const [, p] of m[2].matchAll(/['"]([^'"]+)['"]/g)) {
                if (routerGuarded || GUARDS.test(m[3])) continue;
                if (OPEN[file]?.[p]) { seen.add(`${file} ${p}`); continue; }
                open.push(`${m[1].toUpperCase()} ${p} (${file})`);
            }
        }
    }
    assert.deepEqual(open, []);
    const stale = Object.entries(OPEN).flatMap(([f, routes]) => Object.keys(routes).map((p) => `${f} ${p}`)).filter((k) => !seen.has(k));
    assert.deepEqual(stale, [], 'listed as open but not found (or now guarded)');
});
