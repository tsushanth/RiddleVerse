// No public response may carry an account address: several tables use the email as the user id. Supabase and Firebase are
// faked; every row that comes back holds addresses.
import test from 'node:test';
import assert from 'node:assert/strict';
import http from 'node:http';

process.env.DOCKER_BUILD = 'true';
process.env.SUPABASE_URL = 'http://supabase.test';
process.env.SUPABASE_ANON_KEY = 'test-anon-key';
process.env.SUPABASE_SERVICE_KEY = 'test-service-key';
process.env.RV_CHANNEL_REF_KEY = 'unit-test-ref-key';

const A = 'alpha.player@example.test';
const B = 'bo@example.test';
const UID = 'AbCdEfGhIjKlMnOpQrStUvWxYz12';
const EMAIL = /[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}/;

const tables = {
    leaderboard: [
        { user_id: A, name: A, score: 20, last_updated: '2026-01-01' },
        { user_id: UID, name: 'Uid Player', score: 10, last_updated: '2026-01-01' },
    ],
    custom_leaderboard: [
        { parentsetid: 'riddle-1', userid: A, score: 20, timetaken: 5000 },
        { parentsetid: 'riddle-1', userid: B, score: 15, timetaken: 6000 },
        { parentsetid: 'riddle-1', userid: UID, score: 10, timetaken: 7000 },
    ],
    puzzle_sets: [{ id: 'riddle-1', name: 'Set', creator: A, status: 'completed', puzzle_count: 5, format: 'Q/A' }],
    puzzles: [{ puzzleid: 'p1', parentSetId: 'riddle-1', source: B, type: 'riddle', question: 'q', answer: 'a', options: [] }],
    custom_games: [{ id: 'g1', title: 'G', creator_id: A, creator_name: A, html_content: '', play_count: 1, platform_type: 'webview', game_type: 'ai_generated', series_id: null, level_index: 1 }],
    chat_scores: [{ user_id: A, username: B, score: 3, avatar_url: null }],
    screenshot_library: [{ id: 's1', uploaded_by_user_id: A, uploaded_by_name: A }],
    game_sessions: [{ id: 'sess1', user_id: A, game_id: 'g1' }],
    users: [{ user_id: UID, email: A, subscription_tier: 'free' }],
    streak_notification_jobs: [{ job_id: 'j', status: 'completed' }],
};

// Route logging on stdout upsets the test runner's own protocol on some Node versions.
console.log = () => {};

const queries = [];
const realFetch = globalThis.fetch;
globalThis.fetch = async (url, init) => {
    const u = String(url);
    if (u.startsWith('http://supabase.test')) {
        queries.push(u);
        const table = new URL(u).pathname.split('/').pop();
        const rows = tables[table] || [];
        const accept = new Headers(init?.headers).get('accept') || '';
        const body = accept.includes('pgrst.object') ? rows[0] ?? null : rows;
        return new Response(JSON.stringify(body), { status: 200, headers: { 'content-type': 'application/json', 'content-range': `0-${rows.length}/${rows.length}` } });
    }
    if (u.startsWith('http://127.0.0.1')) return realFetch(url, init);
    throw new Error('unexpected outbound request in test');
};

const realSetInterval = globalThis.setInterval;
globalThis.setInterval = (...a) => realSetInterval(...a).unref();
const { default: express } = await import('express');
const identity = await import('../auth/verifyIdentity.js');
const { publicUserId, publicName } = await import('../utils/publicIdentity.js');
const mount = [
    ['/', (await import('../routes/scoring.routes.js')).default],
    ['/api/leaderboard', (await import('../routes/leaderboard.routes.js')).default],
    ['/', (await import('../routes/puzzleEndpoints.routes.js')).default],
    ['/api/game-creation', (await import('../routes/gameCreation.routes.js')).default],
    ['/api/games', (await import('../routes/games.routes.js')).default],
    ['/api/chat-scores', (await import('../routes/chatScores.routes.js')).default],
    ['/api/sessions', (await import('../routes/sessions.routes.js')).default],
    ['/api', (await import('../routes/simpleUserConfig.js')).default],
    ['/api/streak-notifications', (await import('../routes/streakNotifications.routes.js')).default],
];
globalThis.setInterval = realSetInterval;

identity.__setDeps({
    getFirebaseAuth: async () => ({
        verifyIdToken: async (t) => {
            if (t !== 'good') throw Object.assign(new Error('bad token'), { code: 'auth/argument-error' });
            return { uid: UID, email: A, email_verified: true };
        },
    }),
    getSupabaseUrl: () => undefined,
});

const app = express();
app.use(express.json());
for (const [p, r] of mount) app.use(p, r);
const server = http.createServer(app);
await new Promise((r) => server.listen(0, r));
const base = `http://127.0.0.1:${server.address().port}`;
test.after(() => { server.close(); globalThis.fetch = realFetch; });

async function call(path, { method = 'GET', body, headers = {} } = {}) {
    const r = await realFetch(base + path, { method, headers: { 'content-type': 'application/json', ...headers }, body: body && JSON.stringify(body) });
    const text = await r.text();
    return { status: r.status, text, json: JSON.parse(text) };
}

test('publicUserId: addresses become stable opaque ids, other ids pass through', () => {
    assert.match(publicUserId(A), /^u_[0-9a-f]{24}$/);
    assert.equal(publicUserId(A), publicUserId(` ${A.toUpperCase()} `));
    assert.notEqual(publicUserId(A), publicUserId(B));
    assert.equal(publicUserId(UID), UID);
    assert.equal(publicUserId('anonymous'), 'anonymous');
    assert.equal(publicUserId(null), null);
});

test('publicName: masks addresses, leaves ordinary names', () => {
    assert.equal(publicName(A), 'alph***');
    assert.equal(publicName(B), 'b***');
    assert.equal(publicName(`by ${A}!`), 'by alph***!');
    assert.equal(publicName('someone@localhost'), 'some***');
    assert.equal(publicName('Meet @ noon'), 'Meet @ noon');
    assert.equal(publicName(undefined), undefined);
});

const READS = [
    '/leaderboard',
    '/api/leaderboard',
    '/get-puzzle-leaderboard/riddle-1',
    '/api/leaderboard/puzzle/riddle-1',
    '/fetch-custom-puzzle?puzzleId=riddle-1',
    '/list-custom-puzzles',
    '/get-featured-puzzles',
    '/api/leaderboard/featured',
    '/api/game-creation/browse?creatorId=someone',
    '/api/game-creation/g1',
    '/api/games',
    '/api/games/g1',
    '/api/games/screenshots',
    '/api/chat-scores/g1/_app_?platform=app',
    '/api/sessions/sess1',
    `/api/user-config?userId=${UID}&email=probe`,
    '/api/streak-notifications/status',
];

for (const path of READS) {
    test(`no address in ${path.split('?')[0]}`, async () => {
        const r = await call(path);
        assert.equal(r.status, 200, r.text);
        assert.doesNotMatch(r.text, EMAIL);
    });
}

test('streak status does not select the per-account error details', async () => {
    queries.length = 0;
    await call('/api/streak-notifications/status');
    const q = queries.find((u) => u.includes('streak_notification_jobs'));
    assert.ok(q);
    assert.doesNotMatch(decodeURIComponent(q), /error_details|select=\*/);
});

test('score submissions do not echo other players\' addresses, and the rank still matches the sender', async () => {
    const r = await call('/update-puzzle-leaderboard', { method: 'POST', body: { puzzleId: 'riddle-1', userId: B, timeTaken: 6000, score: 15 } });
    assert.equal(r.status, 200, r.text);
    assert.doesNotMatch(r.text, EMAIL);
    assert.equal(r.json.userStats.rank, 2);
    const c = await call('/api/chat-scores', { method: 'POST', body: { gameId: 'g1', chatId: '_app_', platform: 'app', userId: A, username: 'x', score: 1 } });
    assert.doesNotMatch(c.text, EMAIL);
});

test('the signed-in caller\'s own rows are marked, nobody else\'s', async () => {
    const anon = await call('/leaderboard');
    assert.ok(anon.json.leaderboard.every((e) => !('isCurrentUser' in e)));
    for (const path of ['/leaderboard', '/api/leaderboard']) {
        const bad = await call(path, { headers: { authorization: 'Bearer nope' } });
        assert.ok(bad.json.leaderboard.every((e) => !('isCurrentUser' in e)));
        const mine = await call(path, { headers: { authorization: 'Bearer good' } });
        assert.deepEqual(mine.json.leaderboard.map((e) => Boolean(e.isCurrentUser)), [true, true]);
        assert.doesNotMatch(mine.text, EMAIL);
    }
});

test('a uid row keeps its uid so shipped clients still highlight it', async () => {
    const r = await call('/api/leaderboard/puzzle/riddle-1');
    assert.ok(r.json.topPlayers.some((p) => p.userId === UID));
});
