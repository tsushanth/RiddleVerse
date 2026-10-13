import test from 'node:test';
import assert from 'node:assert/strict';
import express from 'express';
import cors from 'cors';
import { createPuzzleMonitoringRouters } from '../routes/puzzleMonitoring.routes.js';
import { validatePayload, createLimiter, buildAdminPuzzles, computeAlerts, NEW_WAVE_TYPES } from '../services/puzzleMonitoring.js';

const NOW = 1_800_000_000_000;
const good = () => ({ installId: 'abc', platform: 'web', events: [{ type: 'start', puzzleType: 'sudoku' }] });

function fakeClient({ rpcData = {}, insertError = null } = {}) {
    const c = { inserted: [], rpcCalls: [] };
    c.from = (t) => ({ insert: async (rows) => { assert.equal(t, 'puzzle_events'); c.inserted.push(...rows); return { error: insertError }; } });
    c.rpc = async (fn, args) => { c.rpcCalls.push([fn, args]); return { data: rpcData, error: null }; };
    return c;
}

async function boot(over = {}) {
    const client = over.client || fakeClient();
    const { ingest, admin } = createPuzzleMonitoringRouters({
        getClient: () => client, now: () => NOW, limiter: createLimiter({ now: () => NOW }),
        verifyBearerToken: async (h) => { if (h === 'Bearer good') return { uid: 'u1' }; throw new Error('bad'); },
        isAdmin: async (req) => req.headers.authorization === 'Bearer admin',
        ...over.deps,
    });
    const app = express();
    app.use(cors());
    app.use('/api/puzzle-events', ingest);
    app.use(express.json());
    app.use('/api/dash', admin);
    const server = await new Promise((r) => { const s = app.listen(0, () => r(s)); });
    const base = `http://127.0.0.1:${server.address().port}`;
    return { client, base, close: () => server.close() };
}
const post = (base, body, headers = {}) => fetch(`${base}/api/puzzle-events`, {
    method: 'POST', headers: { 'content-type': 'application/json', ...headers },
    body: typeof body === 'string' ? body : JSON.stringify(body),
});

test('validate: accepts minimal and ignores unknown fields', () => {
    const v = validatePayload({ ...good(), extra: 1, userId: 'evil' }, NOW);
    assert.equal(v.events.length, 1);
    assert.equal(v.userId, undefined);
});
test('validate: rejects bad shapes and limits', () => {
    const bad = [
        null, [], {}, { ...good(), installId: '' }, { ...good(), installId: 'x'.repeat(65) }, { ...good(), installId: 5 },
        { ...good(), platform: 'ios' }, { ...good(), events: [] }, { ...good(), events: 'x' },
        { ...good(), events: Array(51).fill({ type: 'start', puzzleType: 'sudoku' }) },
        { ...good(), events: [{ type: 'nope', puzzleType: 'sudoku' }] },
        { ...good(), events: [{ type: 'start', puzzleType: 'Sudoku' }] },
        { ...good(), events: [{ type: 'start', puzzleType: 'a-b' }] },
        { ...good(), events: [{ type: 'start', puzzleType: 'a'.repeat(41) }] },
        { ...good(), events: [{ type: 'start' }] }, { ...good(), events: ['x'] },
    ];
    for (const b of bad) assert.equal(validatePayload(b, NOW), null, JSON.stringify(b)?.slice(0, 80));
    assert.ok(validatePayload({ ...good(), events: Array(50).fill({ type: 'start', puzzleType: 'a'.repeat(40) }) }, NOW));
});
test('validate: field sanitising (ints, truncation, ts clamp)', () => {
    const v = validatePayload({ installId: 'i', platform: 'android', appVersion: 'v'.repeat(100), events: [
        { type: 'error', puzzleType: 'conduit', errorMessage: 'e'.repeat(500), durationMs: -5, score: 1.9, moves: 'x', hintsUsed: 3, mistakes: 1e12, ts: NOW + 10 * 86400e3 },
        { type: 'start', puzzleType: 'conduit', ts: 1 },
    ] }, NOW);
    const [a, b] = v.events;
    assert.equal(a.error_message.length, 300);
    assert.equal(a.duration_ms, null); assert.equal(a.score, 1); assert.equal(a.moves, null);
    assert.equal(a.hints_used, 3); assert.equal(a.mistakes, null);
    assert.equal(Date.parse(a.event_ts), NOW + 86400e3);
    assert.equal(Date.parse(b.event_ts), NOW - 86400e3);
    assert.equal(v.appVersion.length, 32);
});

test('ingest: 202 shape, anonymous -> user_id null, body userId ignored', async () => {
    const s = await boot();
    const r = await post(s.base, { ...good(), userId: 'evil', events: [{ type: 'start', puzzleType: 'sudoku', userId: 'evil' }, { type: 'complete', puzzleType: 'sudoku', durationMs: 5000 }] });
    assert.equal(r.status, 202);
    assert.deepEqual(await r.json(), { accepted: 2 });
    assert.equal(s.client.inserted.length, 2);
    assert.ok(s.client.inserted.every((x) => x.user_id === null && x.install_id === 'abc' && x.platform === 'web'));
    assert.equal(s.client.inserted[1].duration_ms, 5000);
    s.close();
});
test('ingest: verified token sets user_id; bad token is anonymous, not an error', async () => {
    const s = await boot();
    assert.equal((await post(s.base, good(), { authorization: 'Bearer good' })).status, 202);
    assert.equal((await post(s.base, good(), { authorization: 'Bearer junk' })).status, 202);
    assert.deepEqual(s.client.inserted.map((x) => x.user_id), ['u1', null]);
    s.close();
});
test('ingest: 400 malformed (bad json and bad fields), 413 too large', async () => {
    const s = await boot();
    assert.equal((await post(s.base, '{nope')).status, 400);
    assert.deepEqual(await (await post(s.base, { installId: 'a' })).json(), { error: 'bad_request' });
    const big = { ...good(), events: [{ type: 'error', puzzleType: 'sudoku', errorMessage: 'x'.repeat(70000) }] };
    assert.equal((await post(s.base, big)).status, 413);
    assert.equal(s.client.inserted.length, 0);
    s.close();
});
test('ingest: CORS preflight allowed', async () => {
    const s = await boot();
    const r = await fetch(`${s.base}/api/puzzle-events`, { method: 'OPTIONS', headers: { origin: 'https://x.test', 'access-control-request-method': 'POST' } });
    assert.ok(r.status < 300);
    assert.equal(r.headers.get('access-control-allow-origin'), '*');
    s.close();
});
test('ingest: per-IP limit (60 req/min) -> 429', async () => {
    const s = await boot();
    for (let i = 0; i < 60; i++) assert.equal((await post(s.base, { ...good(), installId: `i${i}` })).status, 202);
    assert.equal((await post(s.base, good())).status, 429);
    s.close();
});
test('ingest: per-install limit (300 events/hour) -> 429, other installs unaffected', async () => {
    const s = await boot();
    const ev = Array(50).fill({ type: 'hint', puzzleType: 'sudoku' });
    for (let i = 0; i < 6; i++) assert.equal((await post(s.base, { ...good(), events: ev })).status, 202);
    assert.equal((await post(s.base, good())).status, 429);
    assert.equal((await post(s.base, { ...good(), installId: 'other' })).status, 202);
    s.close();
});
test('limiter: window expires', () => {
    let t = 0; const l = createLimiter({ now: () => t });
    assert.ok(l.take('k', 5, 5, 1000)); assert.ok(!l.take('k', 1, 5, 1000));
    t = 1001; assert.ok(l.take('k', 1, 5, 1000));
});
test('ingest: db failure -> 500 (never claims accepted)', async () => {
    const s = await boot({ client: fakeClient({ insertError: { message: 'boom' } }) });
    assert.equal((await post(s.base, good())).status, 500);
    s.close();
});

test('admin: requires admin (mirrors /api/dash/overview: 404 for non-admin)', async () => {
    const s = await boot();
    for (const h of [{}, { authorization: 'Bearer good' }, { authorization: 'Bearer nonsense' }]) {
        const r = await fetch(`${s.base}/api/dash/puzzles`, { headers: h });
        assert.equal(r.status, 404);
    }
    assert.equal(s.client.rpcCalls.length, 0);
    s.close();
});
test('admin: params clamped and passed to rpc', async () => {
    const s = await boot();
    const h = { authorization: 'Bearer admin' };
    let r = await fetch(`${s.base}/api/dash/puzzles?days=999&platform=android`, { headers: h });
    assert.equal(r.status, 200);
    let j = await r.json();
    assert.equal(j.days, 90); assert.equal(j.platform, 'android');
    await fetch(`${s.base}/api/dash/puzzles?days=abc&platform=ios`, { headers: h });
    assert.deepEqual(s.client.rpcCalls.map((c) => c[1]), [{ p_days: 90, p_platform: 'android' }, { p_days: 7, p_platform: 'all' }]);
    s.close();
});

test('aggregation math, rates, totals, new-wave flag', () => {
    const raw = {
        activeInstalls: 12,
        puzzles: [
            { puzzleType: 'sudoku', starts: 100, completes: 40, fails: 5, abandons: 30, hints: 150, errors: 2, medianDurationMs: 61234.6, android: 120, web: 80, lastSeen: 't' },
            { puzzleType: 'legacyquiz', starts: 10, completes: 9, errors: 0 },
        ],
        daily: [{ date: '2026-10-11', starts: 3, completes: 1, errors: 0 }],
        topErrors: [{ puzzleType: 'sudoku', message: 'm', count: 2, lastSeen: 't' }],
    };
    const r = buildAdminPuzzles(raw, { days: 7, platform: 'all' }, NOW);
    assert.equal(r.byPuzzle.length, 19 + 1);
    const s = r.byPuzzle.find((p) => p.puzzleType === 'sudoku');
    assert.equal(s.completionRate, 0.4); assert.equal(s.errorRate, 0.02); assert.equal(s.avgHintsPerPlay, 1.5);
    assert.equal(s.medianDurationMs, 61235); assert.deepEqual(s.platforms, { android: 120, web: 80 });
    assert.equal(s.isNewWave, true);
    assert.equal(r.byPuzzle.find((p) => p.puzzleType === 'legacyquiz').isNewWave, false);
    assert.equal(r.byPuzzle.find((p) => p.puzzleType === 'conduit').completionRate, null);
    assert.deepEqual(r.totals, { starts: 110, completes: 49, fails: 5, abandons: 30, errors: 2, activeInstalls: 12 });
    assert.equal(r.generatedAt, new Date(NOW).toISOString());
    assert.equal(r.daily.length, 1);
});
test('alerts: thresholds, min-starts gate, new-wave silence, severity order', () => {
    const mk = (puzzleType, starts, completes, errors, isNewWave = true) => ({ puzzleType, isNewWave, starts, completes, errors,
        completionRate: starts ? completes / starts : null, errorRate: starts ? errors / starts : null });
    const a = computeAlerts([
        mk('low_comp', 20, 3, 0), mk('edge_comp', 20, 4, 0), mk('few', 19, 0, 19), mk('errs', 100, 90, 6), mk('edge_err', 100, 90, 5),
        mk('silent', 0, 0, 0), mk('old_silent', 0, 0, 0, false),
    ], 7, 'all');
    const key = (x) => `${x.severity}:${x.puzzleType}`;
    assert.deepEqual(a.map(key), ['critical:errs', 'warning:low_comp', 'info:silent', 'info:old_silent'].filter((k) => k !== 'info:old_silent'));
    assert.equal(computeAlerts([mk('silent', 0, 0, 0)], 1).length, 0);
    assert.equal(NEW_WAVE_TYPES.length, 19);
});
test('alerts end-to-end: all 19 silent -> 19 info', () => {
    const r = buildAdminPuzzles({}, { days: 7, platform: 'all' }, NOW);
    assert.equal(r.alerts.length, 19); assert.ok(r.alerts.every((x) => x.severity === 'info'));
});
