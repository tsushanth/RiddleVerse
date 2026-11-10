import test from 'node:test';
import assert from 'node:assert/strict';
import { createGameCreationGuard, createWriteRateLimit, classify, readConfig, DEFAULT_COSTS } from '../middleware/gameCreationGuard.js';

function mkRes() {
    const r = { code: 200, body: null, headers: {} };
    r.status = (c) => { r.code = c; return r; };
    r.json = (b) => { r.body = b; return r; };
    r.setHeader = (k, v) => { r.headers[k] = v; };
    return r;
}
async function run(mw, { method = 'POST', path = '/generate', body = {}, headers = {}, ip = '9.9.9.9' } = {}) {
    const req = { method, path, body, headers, ip };
    const res = mkRes();
    let nexted = false;
    await mw(req, res, () => { nexted = true; });
    return { res, nexted, req };
}
const quiet = () => {};

test('classify: only spend-bearing POST routes are metered', () => {
    assert.equal(classify('POST', '/generate'), 'generate');
    assert.equal(classify('POST', '/remix'), 'remix');
    assert.equal(classify('POST', '/save'), 'save');
    assert.equal(classify('POST', '/abc/tweak'), 'tweak');
    assert.equal(classify('POST', '/abc/customize'), 'customize');
    assert.equal(classify('POST', '/abc/difficulty/3/generate'), 'difficulty');
    assert.equal(classify('POST', '/abc/next-level/generate'), 'nextlevel');
    assert.equal(classify('GET', '/generate'), null);
    assert.equal(classify('GET', '/browse'), null);
    assert.equal(classify('POST', '/abc/play-event/start'), null);
});

test('config defaults and overrides; bad JSON keeps defaults', () => {
    const c = readConfig({});
    assert.equal(c.requireAuth, false);
    assert.deepEqual(c.costs, DEFAULT_COSTS);
    assert.equal(readConfig({ GAME_CREATION_REQUIRE_AUTH: 'true', GAME_CREATION_IP_PER_HOUR: '3' }).ipPerHour, 3);
    assert.equal(readConfig({ GAME_CREATION_COST_ESTIMATES: '{bad' }).costs.generate, DEFAULT_COSTS.generate);
    assert.equal(readConfig({ GAME_CREATION_COST_ESTIMATES: '{"generate":2}' }).costs.generate, 2);
});

test('per-IP hourly limit, then recovers after the window', async () => {
    let t = 1_000_000;
    const mw = createGameCreationGuard({ env: { GAME_CREATION_IP_PER_HOUR: '2', GAME_CREATION_KEY_PER_HOUR: '100' }, now: () => t, log: quiet });
    const codes = [];
    for (let i = 0; i < 3; i++) codes.push((await run(mw, { body: { userId: `u${i}` } })).res.code);
    assert.deepEqual(codes, [200, 200, 429]);
    t += 3_600_001;
    assert.equal((await run(mw, { body: { userId: 'z' } })).nexted, true);
    assert.ok((await run(mw, { ip: '1.1.1.1' })).nexted, 'other IP unaffected');
});

test('per-key limit: rotating IPs does not help, rotating body userIds is bounded by the IP limit', async () => {
    const mw = createGameCreationGuard({ env: { GAME_CREATION_KEY_PER_HOUR: '2', GAME_CREATION_IP_PER_HOUR: '5' }, log: quiet });
    const codes = [];
    for (let i = 0; i < 3; i++) codes.push((await run(mw, { ip: `5.5.5.${i}`, body: { userId: 'same' } })).res.code);
    assert.deepEqual(codes, [200, 200, 429]);
    // one IP, many fresh userIds -> IP limit
    const codes2 = [];
    for (let i = 0; i < 7; i++) codes2.push((await run(mw, { ip: '7.7.7.7', body: { userId: `fresh${i}` } })).res.code);
    assert.deepEqual(codes2, [200, 200, 200, 200, 200, 429, 429]);
});

test('install id header is the key when there is no verified uid (beats a rotating body userId)', async () => {
    const mw = createGameCreationGuard({ env: { GAME_CREATION_KEY_PER_HOUR: '2', GAME_CREATION_IP_PER_HOUR: '100' }, log: quiet });
    const codes = [];
    for (let i = 0; i < 3; i++) codes.push((await run(mw, { ip: `6.6.6.${i}`, headers: { 'x-install-id': 'inst-1' }, body: { userId: `rot${i}` } })).res.code);
    assert.deepEqual(codes, [200, 200, 429]);
});

test('global daily budget: caps estimated spend, resets next UTC day, reads are never blocked', async () => {
    let t = Date.parse('2026-11-10T10:00:00Z');
    const mw = createGameCreationGuard({
        env: { GAME_CREATION_DAILY_BUDGET_USD: '1.5', GAME_CREATION_COST_ESTIMATES: '{"generate":1,"tweak":0.4}', GAME_CREATION_IP_PER_HOUR: '1000', GAME_CREATION_KEY_PER_HOUR: '1000' },
        now: () => t, log: quiet,
    });
    assert.equal((await run(mw, { ip: '1.0.0.1', body: { userId: 'a' } })).res.code, 200); // 1.0
    const over = await run(mw, { ip: '1.0.0.2', body: { userId: 'b' } });                   // 2.0 > 1.5
    assert.equal(over.res.code, 429);
    assert.ok(Number(over.res.headers['Retry-After']) > 0);
    assert.equal((await run(mw, { ip: '1.0.0.3', path: '/x/tweak', body: { userId: 'c' } })).res.code, 200); // 1.4 ok
    assert.equal((await run(mw, { method: 'GET', path: '/browse' })).nexted, true);
    assert.equal((await run(mw, { path: '/save', body: { userId: 'c' } })).nexted, true, 'free action not blocked by budget');
    t = Date.parse('2026-11-11T00:00:01Z');
    assert.equal((await run(mw, { ip: '1.0.0.9', body: { userId: 'd' } })).res.code, 200);
});

test('auth enforce flag: off by default (legacy clients pass), on -> 401 without token, reads still open', async () => {
    const verify = async (tok) => { if (tok === 'good') return { uid: 'u1' }; throw new Error('bad'); };
    const off = createGameCreationGuard({ env: {}, verify, log: quiet });
    assert.equal((await run(off)).nexted, true);
    const on = createGameCreationGuard({ env: { GAME_CREATION_REQUIRE_AUTH: 'true' }, verify, log: quiet });
    assert.equal((await run(on)).res.code, 401);
    assert.equal((await run(on, { method: 'GET', path: '/browse' })).nexted, true);
    assert.equal((await run(on, { headers: { authorization: 'Bearer good' }, body: { userId: 'u1' } })).nexted, true);
});

test('bearer token: invalid -> 401; uid must match body userId -> 403; verified uid is the key', async () => {
    const verify = async (tok) => { if (tok === 'good') return { uid: 'u1' }; throw new Error('bad'); };
    const mw = createGameCreationGuard({ env: { GAME_CREATION_KEY_PER_HOUR: '1', GAME_CREATION_IP_PER_HOUR: '100' }, verify, log: quiet });
    assert.equal((await run(mw, { headers: { authorization: 'Bearer nope' } })).res.code, 401);
    assert.equal((await run(mw, { headers: { authorization: 'Bearer good' }, body: { userId: 'victim' } })).res.code, 403);
    const a = await run(mw, { ip: '2.0.0.1', headers: { authorization: 'Bearer good' }, body: { userId: 'u1' } });
    assert.equal(a.nexted, true); assert.equal(a.req.gameCreationUid, 'u1');
    assert.equal((await run(mw, { ip: '2.0.0.2', headers: { authorization: 'Bearer good' }, body: { userId: 'u1' } })).res.code, 429);
});

test('invalid userId shapes are rejected before any quota is used', async () => {
    const mw = createGameCreationGuard({ env: {}, log: quiet });
    assert.equal((await run(mw, { body: { userId: { $ne: 1 } } })).res.code, 400);
    assert.equal((await run(mw, { body: { userId: 'x'.repeat(200) } })).res.code, 400);
});

test('write rate limit: per IP per minute, GETs untouched', async () => {
    let t = 5_000_000;
    const mw = createWriteRateLimit({ env: { WRITE_RATE_PER_MINUTE: '3' }, now: () => t, log: quiet });
    const codes = [];
    for (let i = 0; i < 4; i++) codes.push((await run(mw, { method: 'POST' })).res.code);
    assert.deepEqual(codes, [200, 200, 200, 429]);
    assert.equal((await run(mw, { method: 'GET' })).nexted, true);
    t += 60_001;
    assert.equal((await run(mw, { method: 'POST' })).nexted, true);
});
