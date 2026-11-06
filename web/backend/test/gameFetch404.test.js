import test from 'node:test';
import assert from 'node:assert/strict';
import http from 'node:http';

// config/database.js builds the Supabase client at import time from env.
process.env.SUPABASE_URL = 'http://supabase.test';
process.env.SUPABASE_ANON_KEY = 'test-anon-key';

// Stand in for PostgREST: supabase-js uses the global fetch.
const realFetch = globalThis.fetch;
let reply = null;
globalThis.fetch = async (url, init) => {
    if (String(url).startsWith('http://supabase.test')) {
        return new Response(JSON.stringify(reply.body), { status: reply.status, headers: { 'content-type': 'application/json' } });
    }
    return realFetch(url, init);
};

// The router starts module-level cache-refresh intervals; unref them so the test process can exit.
const realSetInterval = globalThis.setInterval;
globalThis.setInterval = (...a) => realSetInterval(...a).unref();

const { default: express } = await import('express');
const { default: router } = await import('../routes/gameCreation.routes.js');

globalThis.setInterval = realSetInterval;

const app = express();
app.use('/api/game-creation', router);
const server = http.createServer(app);
await new Promise((r) => server.listen(0, r));
const base = `http://127.0.0.1:${server.address().port}/api/game-creation`;
test.after(() => { server.close(); globalThis.fetch = realFetch; });

async function get(id) {
    const r = await realFetch(`${base}/${id}`);
    return { status: r.status, body: await r.json() };
}

test('GET /:gameId -> 404 when no row matches (PGRST116)', async () => {
    reply = { status: 406, body: { code: 'PGRST116', details: 'The result contains 0 rows', message: 'Cannot coerce the result to a single JSON object' } };
    const r = await get('00000000-0000-0000-0000-000000000000');
    assert.equal(r.status, 404);
    assert.deepEqual(r.body, { error: 'Game not found' });
});

test('GET /:gameId -> 404 for a malformed uuid (22P02)', async () => {
    reply = { status: 400, body: { code: '22P02', message: 'invalid input syntax for type uuid: "nope"' } };
    const r = await get('nope');
    assert.equal(r.status, 404);
    assert.deepEqual(r.body, { error: 'Game not found' });
});

test('GET /:gameId -> other database errors stay 500', async () => {
    const orig = console.error;
    console.error = () => {};
    try {
        reply = { status: 500, body: { code: 'XX000', message: 'internal error' } };
        const r = await get('00000000-0000-0000-0000-000000000000');
        assert.equal(r.status, 500);
        assert.deepEqual(r.body, { error: 'Failed to fetch game' });
    } finally { console.error = orig; }
});
