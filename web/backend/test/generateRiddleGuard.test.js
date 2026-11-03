import test from 'node:test';
import assert from 'node:assert/strict';
import { createGenerateRiddleGuard, readLimits, MAX_NUM_PUZZLES } from '../middleware/generateRiddleGuard.js';

function mkRes() {
    return {
        statusCode: 200, body: undefined, headers: {},
        setHeader(k, v) { this.headers[k] = v; },
        status(c) { this.statusCode = c; return this; },
        json(b) { this.body = b; return this; },
    };
}
const goodBody = (over = {}) => ({ topic: 'Space', format: 'Q&A', numPuzzles: 3, userId: 'kid@example.com', ...over });
function mkReq({ body = goodBody(), headers = {}, ip = '1.2.3.4' } = {}) {
    return { body, headers, ip };
}
async function run(guard, req) {
    const res = mkRes();
    let nexted = false;
    await guard(req, res, () => { nexted = true; });
    return { res, nexted };
}
const okVerify = async (t) => { if (t === 'good') return { uid: 'uid-1' }; throw new Error('bad'); };

test('happy path: legacy client with no credential is allowed and reaches the handler', async () => {
    const guard = createGenerateRiddleGuard({ env: {}, verify: okVerify });
    const { res, nexted } = await run(guard, mkReq());
    assert.equal(nexted, true);
    assert.equal(res.statusCode, 200);
});

test('happy path: valid bearer token is allowed and its uid is recorded', async () => {
    const guard = createGenerateRiddleGuard({ env: {}, verify: okVerify });
    const req = mkReq({ headers: { authorization: 'Bearer good' } });
    const { nexted } = await run(guard, req);
    assert.equal(nexted, true);
    assert.equal(req.generateRiddleUid, 'uid-1');
});

test('401: invalid bearer token is rejected even when auth is optional', async () => {
    const guard = createGenerateRiddleGuard({ env: {}, verify: okVerify });
    const { res, nexted } = await run(guard, mkReq({ headers: { authorization: 'Bearer nope' } }));
    assert.equal(nexted, false);
    assert.equal(res.statusCode, 401);
});

test('401: no credential is rejected when GENERATE_RIDDLE_REQUIRE_AUTH=true; valid token still passes', async () => {
    const guard = createGenerateRiddleGuard({ env: { GENERATE_RIDDLE_REQUIRE_AUTH: 'true' }, verify: okVerify });
    const a = await run(guard, mkReq());
    assert.equal(a.res.statusCode, 401);
    assert.equal(a.nexted, false);
    const b = await run(guard, mkReq({ headers: { authorization: 'Bearer good' } }));
    assert.equal(b.nexted, true);
});

test('400/413: oversized or malformed input is rejected before any quota is used', async () => {
    const guard = createGenerateRiddleGuard({ env: { GENERATE_RIDDLE_USER_PER_HOUR: '1' }, verify: okVerify });
    for (const over of [
        { topic: 'x'.repeat(101) },
        { topic: { $ne: 1 } },
        { format: 'f'.repeat(61) },
        { userId: 'u'.repeat(129) },
        { numPuzzles: MAX_NUM_PUZZLES + 1 },
        { numPuzzles: 100000 },
        { numPuzzles: 2.5 },
        { numPuzzles: -1 },
    ]) {
        const { res, nexted } = await run(guard, mkReq({ body: goodBody(over) }));
        assert.equal(res.statusCode, 400, JSON.stringify(over));
        assert.equal(nexted, false);
    }
    const big = await run(guard, mkReq({ headers: { 'content-length': '999999' } }));
    assert.equal(big.res.statusCode, 413);
    // none of the above consumed the single allowed hit
    assert.equal((await run(guard, mkReq())).nexted, true);
});

test('missing parameters fall through to the handler (its own 400) without using quota', async () => {
    const guard = createGenerateRiddleGuard({ env: { GENERATE_RIDDLE_USER_PER_HOUR: '1' }, verify: okVerify });
    const { nexted } = await run(guard, mkReq({ body: { topic: 'x' } }));
    assert.equal(nexted, true);
    assert.equal((await run(guard, mkReq())).nexted, true);
});

test('429: per-user hourly limit, then the window expires', async () => {
    let t = Date.UTC(2026, 10, 3, 12, 0, 0);
    const guard = createGenerateRiddleGuard({ env: { GENERATE_RIDDLE_USER_PER_HOUR: '2', GENERATE_RIDDLE_IP_PER_HOUR: '100' }, now: () => t, verify: okVerify });
    assert.equal((await run(guard, mkReq({ ip: '9.9.9.1' }))).nexted, true);
    assert.equal((await run(guard, mkReq({ ip: '9.9.9.2' }))).nexted, true);
    const third = await run(guard, mkReq({ ip: '9.9.9.3' })); // different IP, same user
    assert.equal(third.res.statusCode, 429);
    assert.ok(Number(third.res.headers['Retry-After']) > 0);
    // a different user is unaffected
    assert.equal((await run(guard, mkReq({ body: goodBody({ userId: 'other@example.com' }) }))).nexted, true);
    t += 61 * 60 * 1000;
    assert.equal((await run(guard, mkReq())).nexted, true);
});

test('429: per-IP hourly limit applies across rotating userIds', async () => {
    const guard = createGenerateRiddleGuard({ env: { GENERATE_RIDDLE_IP_PER_HOUR: '3' }, verify: okVerify });
    for (let i = 0; i < 3; i++) {
        assert.equal((await run(guard, mkReq({ body: goodBody({ userId: `u${i}` }) }))).nexted, true);
    }
    const r = await run(guard, mkReq({ body: goodBody({ userId: 'u-new' }) }));
    assert.equal(r.res.statusCode, 429);
    assert.equal(r.nexted, false);
});

test('429: global daily cap holds across IPs and users, and resets the next UTC day', async () => {
    let t = Date.UTC(2026, 10, 3, 23, 0, 0);
    const guard = createGenerateRiddleGuard({ env: { GENERATE_RIDDLE_DAILY_CAP: '3' }, now: () => t, verify: okVerify });
    for (let i = 0; i < 3; i++) {
        assert.equal((await run(guard, mkReq({ ip: `5.5.5.${i}`, body: goodBody({ userId: `u${i}` }) }))).nexted, true);
    }
    const over = await run(guard, mkReq({ ip: '5.5.5.99', body: goodBody({ userId: 'u99' }) }));
    assert.equal(over.res.statusCode, 429);
    t += 2 * 60 * 60 * 1000; // past UTC midnight
    assert.equal((await run(guard, mkReq({ ip: '5.5.5.99', body: goodBody({ userId: 'u99' }) }))).nexted, true);
});

test('defaults are sane and env overrides parse', () => {
    assert.deepEqual(readLimits({}), { requireAuth: false, ipPerHour: 10, userPerHour: 5, dailyCap: 100 });
    assert.equal(readLimits({ GENERATE_RIDDLE_DAILY_CAP: 'abc' }).dailyCap, 100);
    assert.equal(readLimits({ GENERATE_RIDDLE_DAILY_CAP: '7' }).dailyCap, 7);
});

test('the route is wired through the guard', async () => {
    const { readFileSync } = await import('node:fs');
    const src = readFileSync(new URL('../routes/generation.routes.js', import.meta.url), 'utf8');
    assert.match(src, /router\.post\('\/generate-riddle', generateRiddleGuard,/);
});
