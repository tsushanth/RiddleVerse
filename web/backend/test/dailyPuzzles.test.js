// The daily puzzle cache fills every entry, including pools too large to skip far into, and the stats route reports the
// cache after it has been filled, not the empty one a just-started machine has. Nothing real is contacted.
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

const POOL = 150_000;
const pages = [];
let counts = 0;
const realFetch = globalThis.fetch;
globalThis.fetch = async (url, init) => {
    const u = new URL(String(url));
    if (u.hostname === '127.0.0.1') return realFetch(url, init);
    if (u.hostname !== 'supabase.test') throw new Error('unexpected outbound request in test');
    const type = u.searchParams.get('type')?.slice(3);
    const total = type === 'math' ? 40 : POOL;
    if (init?.method === 'HEAD') {
        counts += 1;
        return new Response(null, { status: 200, headers: { 'content-range': `*/${total}` } });
    }
    const offset = Number(u.searchParams.get('offset'));
    pages.push({ type, offset, order: u.searchParams.get('order') });
    // What the database does when asked to skip tens of thousands of rows.
    if (offset > 5000) return new Response(JSON.stringify({ code: '57014', message: 'canceling statement due to statement timeout' }), { status: 500, headers: { 'content-type': 'application/json' } });
    return new Response(JSON.stringify([{ puzzleid: `${type}-${offset}`, type, question: 'q', answer: 'a' }]), { status: 200, headers: { 'content-type': 'application/json' } });
};

const { default: express } = await import('express');
const daily = await import('../services/dailyPuzzles.js');
const { default: bulk } = await import('../routes/dailyPuzzlesBulk.routes.js');

const app = express();
app.use('/api/puzzles', bulk);
const server = http.createServer(app);
await new Promise((r) => server.listen(0, r));
const base = `http://127.0.0.1:${server.address().port}`;
test.after(() => { server.close(); globalThis.fetch = realFetch; });

test('stats on an empty cache wait for it to be filled, and every pool fills', async () => {
    daily._resetDailyCacheForTests();
    const body = await (await realFetch(`${base}/api/puzzles/daily/stats`)).json();
    assert.equal(body.todayEntries, 60);
});

test('large pools are read a bounded distance from either end; small pools as before', () => {
    assert.ok(pages.every((p) => p.offset < daily.MAX_SKIP));
    const large = pages.filter((p) => p.type !== 'math');
    assert.deepEqual([...new Set(large.map((p) => p.order.split('.')[1]))].sort(), ['asc', 'desc']);
    assert.ok(pages.filter((p) => p.type === 'math').every((p) => p.order.startsWith('timestamp.asc') && p.offset < 35));
});

test('callers at the same time share one fill', async () => {
    daily._resetDailyCacheForTests();
    counts = 0;
    const [a, b] = [daily.refreshDailyPuzzles(), daily.refreshDailyPuzzles()];
    assert.equal(a, b);
    await Promise.all([a, b, realFetch(`${base}/api/puzzles/daily/stats`)]);
    assert.equal(counts, 60);
    assert.equal(daily.pendingDailyRefresh(), null);
});
