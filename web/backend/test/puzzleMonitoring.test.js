import test from 'node:test';
import assert from 'node:assert/strict';
import express from 'express';
import cors from 'cors';
import { createPuzzleMonitoringRouters } from '../routes/puzzleMonitoring.routes.js';
import { validatePayload, createLimiter, buildAdminPuzzles, computeAlerts, NEW_WAVE_TYPES, FUNNEL_STEPS, WEB_PLAY_EVENT_TYPES } from '../services/puzzleMonitoring.js';

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

// ---- install funnel / day-N return events ----
const fev = (over = {}) => ({ type: 'funnel', puzzleType: 'app', funnelStep: 'first_open', ts: NOW, ...over });
const fpay = (...events) => ({ installId: 'abc', platform: 'android', appVersion: '1.0', events });

test('funnel: every allow-listed step validates and maps to funnel_step', () => {
    for (const step of FUNNEL_STEPS) {
        const v = validatePayload(fpay(fev({ funnelStep: step })), NOW);
        assert.ok(v, step);
        assert.equal(v.events[0].funnel_step, step);
        assert.equal(v.events[0].event_type, 'funnel');
        assert.equal(v.events[0].puzzle_type, 'app');
        assert.equal(v.events[0].days_since_install, undefined);
    }
    assert.equal(FUNNEL_STEPS.size, 9);
});
test('funnel: rejects unknown/missing step, wrong puzzleType, and free text', () => {
    const bad = [
        fev({ funnelStep: 'bob@example.com' }), fev({ funnelStep: undefined }), fev({ funnelStep: 5 }),
        fev({ funnelStep: 'FIRST_OPEN' }), fev({ puzzleType: 'sudoku' }),
    ];
    for (const e of bad) assert.equal(validatePayload(fpay(e), NOW), null, JSON.stringify(e));
});
test('app_open: needs 0..3650 integer daysSinceInstall', () => {
    const ok = (d) => validatePayload(fpay({ type: 'app_open', puzzleType: 'app', daysSinceInstall: d, ts: NOW }), NOW);
    assert.equal(ok(0).events[0].days_since_install, 0);
    assert.equal(ok(7.9).events[0].days_since_install, 7);
    assert.equal(ok(3650).events[0].event_type, 'app_open');
    assert.equal(ok(3650).events[0].funnel_step, undefined);
    for (const d of [-1, 3651, '7', null, undefined, NaN]) assert.equal(ok(d), null, String(d));
});
test('funnel: puzzleType "app" is reserved, plain puzzle events cannot use it', () => {
    assert.equal(validatePayload(fpay({ type: 'start', puzzleType: 'app' }), NOW), null);
});
test('funnel: extra fields on funnel events are ignored; puzzle rows never carry funnel columns', () => {
    const v = validatePayload(fpay(fev({ score: 9, difficulty: 'hard', sessionId: 's', errorMessage: 'x', email: 'a@b.c' }),
        { type: 'start', puzzleType: 'sudoku', funnelStep: 'first_open', daysSinceInstall: 3 }), NOW);
    const f = v.events[0];
    assert.equal(f.score, null); assert.equal(f.difficulty, null); assert.equal(f.session_id, null); assert.equal(f.error_message, null);
    assert.equal(JSON.stringify(f).includes('a@b.c'), false);
    const p = v.events[1];
    assert.equal('funnel_step' in p, false);
    assert.equal('days_since_install' in p, false);
});
test('funnel: ingest route stores rows with funnel columns, shares install rate limit', async () => {
    const t = await boot();
    try {
        const r = await post(t.base, fpay(fev(), { type: 'app_open', puzzleType: 'app', daysSinceInstall: 1, ts: NOW }));
        assert.equal(r.status, 202);
        assert.equal(t.client.inserted.length, 2);
        assert.equal(t.client.inserted[0].funnel_step, 'first_open');
        assert.equal(t.client.inserted[1].days_since_install, 1);
        assert.equal(t.client.inserted[0].install_id, 'abc');
        assert.equal((await post(t.base, fpay(fev({ funnelStep: 'nope' })))).status, 400);
        // INSTALL_LIMIT (300/h) counts funnel events too
        for (let i = 0; i < 5; i++) await post(t.base, fpay(...Array(50).fill(fev())), {});
        assert.equal((await post(t.base, fpay(...Array(50).fill(fev())))).status, 429);
    } finally { t.close(); }
});
test('funnel: admin puzzle summary hides the placeholder "app" puzzle', () => {
    const out = buildAdminPuzzles({ puzzles: [{ puzzleType: 'app', starts: 0 }, { puzzleType: 'sudoku', starts: 3, completes: 1 }] }, { days: 7, platform: 'all' }, NOW);
    assert.equal(out.byPuzzle.some((p) => p.puzzleType === 'app'), false);
    assert.equal(out.byPuzzle.find((p) => p.puzzleType === 'sudoku').starts, 3);
});

// ---- public /play page measurement (web_play_*) ----
const WP = ['web_play_view', 'web_play_start', 'web_play_complete', 'web_play_cta'];
const wev = (type, over = {}) => ({ type, puzzleType: 'sudoku', sessionId: 's1', ts: NOW, ...over });
const wpay = (...events) => ({ installId: 'abc', platform: 'web', appVersion: 'web-play', events });

test('web_play: the four event names validate and map to puzzle_type = slug', () => {
    assert.deepEqual([...WEB_PLAY_EVENT_TYPES].sort(), [...WP].sort());
    for (const t of WP) {
        const v = validatePayload(wpay(wev(t)), NOW);
        assert.ok(v, t);
        assert.equal(v.events[0].event_type, t);
        assert.equal(v.events[0].puzzle_type, 'sudoku');
        assert.equal(v.events[0].session_id, 's1');
        assert.equal('funnel_step' in v.events[0], false);
    }
});
test('web_play: web platform only, real slug only', () => {
    assert.equal(validatePayload({ ...wpay(wev('web_play_view')), platform: 'android' }, NOW), null);
    for (const e of [wev('web_play_view', { puzzleType: 'app' }), wev('web_play_view', { puzzleType: 'Sudoku' }),
        wev('web_play_view', { puzzleType: 'a-b' }), wev('web_play_view', { puzzleType: undefined })]) {
        assert.equal(validatePayload(wpay(e), NOW), null, JSON.stringify(e));
    }
});
test('web_play: difficulty is an enum, duration only on complete, no score/moves/hints/mistakes/error text', () => {
    const v = validatePayload(wpay(
        wev('web_play_start', { difficulty: 'medium', durationMs: 99 }),
        wev('web_play_complete', { difficulty: 'hard', durationMs: 4321 }),
        wev('web_play_complete', { difficulty: 'me@x.com', durationMs: -1 }),
        wev('web_play_view', { difficulty: 'easy', score: 5, moves: 6, hintsUsed: 7, mistakes: 8, errorMessage: 'secret' }),
    ), NOW);
    const [s, c, bad, view] = v.events;
    assert.equal(s.difficulty, 'medium'); assert.equal(s.duration_ms, null);
    assert.equal(c.difficulty, 'hard'); assert.equal(c.duration_ms, 4321);
    assert.equal(bad.difficulty, null); assert.equal(bad.duration_ms, null);
    assert.equal(view.difficulty, null);
    for (const r of v.events) for (const k of ['score', 'moves', 'hints_used', 'mistakes', 'error_message']) assert.equal(r[k], null, k);
});
test('web_play: no PII fields survive into the row', () => {
    const v = validatePayload(wpay(wev('web_play_cta', { email: 'a@b.c', url: 'https://x', referrer: 'r', userAgent: 'ua', userId: 'u' })), NOW);
    const text = JSON.stringify(v);
    for (const bad of ['a@b.c', 'https://x', '"r"', '"ua"', '"u"']) assert.equal(text.includes(bad), false, bad);
});
test('web_play: ingest stores rows anonymously even when a bearer token is sent', async () => {
    const t = await boot();
    try {
        const r = await post(t.base, wpay(wev('web_play_view'), wev('web_play_cta')), { authorization: 'Bearer good' });
        assert.equal(r.status, 202);
        assert.equal(t.client.inserted.length, 2);
        assert.equal(t.client.inserted[0].user_id, null);
        assert.equal(t.client.inserted[0].platform, 'web');
        assert.equal(t.client.inserted[1].event_type, 'web_play_cta');
        assert.equal((await post(t.base, wpay(wev('web_play_nope')))).status, 400);
        // plain puzzle events from signed-in users still carry user_id
        const r2 = await post(t.base, wpay({ type: 'start', puzzleType: 'sudoku', ts: NOW }), { authorization: 'Bearer good' });
        assert.equal(r2.status, 202);
        assert.equal(t.client.inserted[2].user_id, 'u1');
    } finally { t.close(); }
});
test('web_play: does not change the puzzle summary starts/completes (counted by their own event types)', () => {
    const out = buildAdminPuzzles({ puzzles: [{ puzzleType: 'sudoku', starts: 3, completes: 1 }] }, { days: 7, platform: 'all' }, NOW);
    assert.equal(out.byPuzzle.find((p) => p.puzzleType === 'sudoku').starts, 3);
});
