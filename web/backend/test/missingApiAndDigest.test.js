// Missing routes, the JSON 404 for unknown API paths, the new-game digest log row, and script escaping. Supabase and FCM
// are faked: no message is ever sent.
import test from 'node:test';
import assert from 'node:assert/strict';
import http from 'node:http';
import fs from 'node:fs';

process.env.DOCKER_BUILD = 'true';
process.env.SUPABASE_URL = 'http://supabase.test';
process.env.SUPABASE_ANON_KEY = 'test-anon-key';
process.env.SUPABASE_SERVICE_KEY = 'test-service-key';
delete process.env.FAILURE_REPORTER_KEY;
console.log = () => {};
console.error = () => {};
console.warn = () => {};

const tables = {
    custom_leaderboard: [{ parentsetid: 'riddle-1', userid: 'patricia@example.test', score: 7, timetaken: 4000 }],
    puzzle_sets: [{ id: 'riddle-1', name: 'Set', creator: 'patricia@example.test' }],
};
const realFetch = globalThis.fetch;
globalThis.fetch = async (url, init) => {
    const u = String(url);
    if (u.startsWith('http://supabase.test')) {
        const rows = tables[new URL(u).pathname.split('/').pop()] || [];
        return new Response(JSON.stringify(rows), { status: 200, headers: { 'content-type': 'application/json' } });
    }
    if (u.startsWith('http://127.0.0.1')) return realFetch(url, init);
    throw new Error('unexpected outbound request in test');
};

const realSetInterval = globalThis.setInterval;
globalThis.setInterval = (...a) => realSetInterval(...a).unref();
const { default: express } = await import('express');
const { default: scoring } = await import('../routes/scoring.routes.js');
const { default: leaderboard } = await import('../routes/leaderboard.routes.js');
const { default: endpoints } = await import('../routes/puzzleEndpoints.routes.js');
const { createMissingApiRouter } = await import('../middleware/missingApi.js');
const { remixDigestService, digestLogId } = await import('../services/remixDigestService.js');
const { jsLiteral } = await import('../utils/jsLiteral.js');
globalThis.setInterval = realSetInterval;

const app = express();
app.use('/', endpoints);
app.use('/api', endpoints);
app.use('/', scoring);
app.use('/api/leaderboard', leaderboard);
app.use(createMissingApiRouter());
app.get('*', (req, res) => res.type('html').send('<html>web app</html>'));
const server = http.createServer(app);
await new Promise((r) => server.listen(0, r));
const base = `http://127.0.0.1:${server.address().port}`;
test.after(() => { server.close(); globalThis.fetch = realFetch; });
const get = async (p, headers = {}, method = 'GET') => {
    const r = await realFetch(base + p, { method, headers });
    return { status: r.status, type: r.headers.get('content-type') || '', text: await r.text() };
};

test('the daily completion screen\'s leaderboard path serves the shape the app parses', async () => {
    const r = await get('/api/puzzle-leaderboard/riddle-1');
    assert.equal(r.status, 200);
    const body = JSON.parse(r.text);
    assert.equal(body.totalPlayers, 1);
    assert.equal(body.statistics.totalCompletions, 1);
    const [p] = body.topPlayers;
    assert.deepEqual([p.rank, p.score, p.timeTaken, p.badgeIcon, typeof p.userId, p.playerName], [1, 7, 4000, '🥇', 'string', 'patr***']);
});

test('the web game leaderboard path exists', async () => {
    const r = await get('/api/leaderboard/game/riddle-1');
    assert.equal(r.status, 200);
    assert.equal(JSON.parse(r.text).leaderboard[0].displayName, 'patr***');
});

test('retired and unknown API paths answer JSON errors, never 200 HTML', async () => {
    const retired = await get('/generate-user-puzzles-with-notifications?email=x');
    assert.equal(retired.status, 410);
    for (const [p, m] of [['/api/nope', 'GET'], ['/api/force-generate-with-path-management', 'POST'], ['/get-daily-puzzle', 'GET']]) {
        const r = await get(p, {}, m);
        assert.equal(r.status, 404, p);
        assert.match(r.type, /json/);
    }
    const page = await get('/play-something-else');
    assert.equal(page.status, 200);
    assert.match(page.type, /html/);
});

test('/leaderboard: JSON for the apps, the web page for a browser', async () => {
    for (const headers of [{}, { accept: '*/*' }, { accept: 'application/json' }]) {
        assert.match((await get('/leaderboard', headers)).type, /json/);
    }
    const browser = await get('/leaderboard', { accept: 'text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8' });
    assert.match(browser.type, /html/);
});

test('server.js mounts the missing-path router just before the web page catch-all', () => {
    const src = fs.readFileSync(new URL('../server.js', import.meta.url), 'utf8');
    const missing = src.indexOf('app.use(createMissingApiRouter());');
    const catchAll = src.indexOf('app.get("*"');
    assert.ok(missing > 0 && catchAll > missing);
    assert.doesNotMatch(src.slice(missing, catchAll), /app\.(get|post|use)\(['"]\/api/);
});

function fakeDb({ claimError = null, tokens = ['t1', 't2', 't3'], active = 20956 } = {}) {
    const writes = [];
    const chain = (result) => { const q = { select: () => q, gte: () => q, order: () => q, eq: () => q, then: (ok) => Promise.resolve(result).then(ok) }; return q; };
    return {
        writes,
        from(table) {
            if (table === 'custom_games') return chain({ data: [{ id: 'g', title: 'T', creator_name: 'maker@example.test' }], error: null });
            if (table === 'user_notification_tokens') return chain({ data: tokens.map((fcm_token) => ({ fcm_token })), error: null, count: active });
            return {
                insert: async (row) => { writes.push(['insert', row]); return { error: claimError }; },
                update: (row) => ({ eq: async (col, id) => { writes.push(['update', row, id]); return { error: null }; } }),
            };
        },
    };
}
const COLUMNS = ['id', 'user_email', 'notification_type', 'title', 'body', 'data', 'sent_at', 'success', 'error_message', 'fcm_response'];
function fakeFcm() {
    const sent = [];
    return { sent, messaging: () => ({ sendEachForMulticast: async (m) => { sent.push(m); return { successCount: m.tokens.length - 1, failureCount: 1, responses: [{ success: false, error: { code: 'messaging/registration-token-not-registered' } }] }; } }) };
}

test('digest: logs one row with real columns, records delivery, keeps the same audience', async () => {
    const db = fakeDb();
    const fcm = fakeFcm();
    const now = new Date('2026-01-02T18:00:00Z');
    await remixDigestService.sendDigest({ db, messaging: fcm.messaging, now, report: () => {} });
    assert.equal(fcm.sent.length, 1);
    assert.deepEqual(fcm.sent[0].tokens, ['t1', 't2', 't3']);
    assert.doesNotMatch(fcm.sent[0].notification.body, /@/);
    const [[, inserted], [, updated, id]] = db.writes;
    for (const row of [inserted, updated]) assert.deepEqual(Object.keys(row).filter((k) => !COLUMNS.includes(k)), []);
    assert.equal(id, digestLogId(now));
    assert.equal(inserted.id, id);
    assert.equal(updated.success, true);
    assert.deepEqual(updated.fcm_response, { sent: 2, failed: 1, error_codes: { 'messaging/registration-token-not-registered': 1 } });
    assert.deepEqual([updated.data.targeted, updated.data.active_tokens, updated.data.state], [3, 20956, 'done']);
});

test('digest: a day already claimed, or an unwritable log, sends nothing', async () => {
    for (const code of ['23505', '42501']) {
        const fcm = fakeFcm();
        const reports = [];
        await remixDigestService.sendDigest({ db: fakeDb({ claimError: { code, message: 'x' } }), messaging: fcm.messaging, now: new Date(), report: (f) => reports.push(f) });
        assert.equal(fcm.sent.length, 0);
        assert.equal(reports.length, code === '23505' ? 0 : 1);
    }
});

test('digest log id: a uuid, one per UTC day', () => {
    assert.match(digestLogId(new Date('2026-01-02T00:00:01Z')), /^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-8[0-9a-f]{3}-[0-9a-f]{12}$/);
    assert.equal(digestLogId(new Date('2026-01-02T00:00:01Z')), digestLogId(new Date('2026-01-02T23:59:59Z')));
    assert.notEqual(digestLogId(new Date('2026-01-02T12:00:00Z')), digestLogId(new Date('2026-01-03T12:00:00Z')));
});

test('jsLiteral: a value cannot leave its string or its script element', () => {
    for (const evil of ["';alert(1);//", '</script><script>alert(1)</script>', 'a\\', 'line\u2028break', ['a', 'b']]) {
        const lit = jsLiteral(evil);
        assert.doesNotMatch(lit, /<|\u2028|\u2029/);
        assert.equal(new Function(`return ${lit};`)(), String(evil));
    }
});

test('no raw query value is interpolated into an inline script', () => {
    const files = ['../server.js', '../routes/gameCreation.routes.js'].map((f) => fs.readFileSync(new URL(f, import.meta.url), 'utf8'));
    for (const src of files) assert.doesNotMatch(src, /(__VB_\w+|CHAT_ID|URL_USER_ID|GAME_ID|PLATFORM|API_BASE)\s*=\s*'\$\{/);
    // A string replacement would expand "$'" and friends from the injected values.
    for (const src of files) assert.doesNotMatch(src, /\.replace\([^,\n]+,\s*(headScript|snippet)\b/);
});
