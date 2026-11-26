// The project runs on Firestore's free daily read quota. Anonymous callers must not be able to scan it, boots must not
// spend it, and puzzles that live in Supabase must keep being served when it is used up. Nothing real is contacted.
import test from 'node:test';
import assert from 'node:assert/strict';
import http from 'node:http';
import fs from 'node:fs';
import { generateKeyPairSync } from 'node:crypto';

process.env.DOCKER_BUILD = 'true';
process.env.SUPABASE_URL = 'http://supabase.test';
process.env.SUPABASE_ANON_KEY = 'test-anon-key';
process.env.SUPABASE_SERVICE_KEY = 'test-service-key';
const KEY = 'unit-test-admin-key-0123456789';
process.env.ADMIN_KEY = KEY;
console.log = () => {};
console.error = () => {};
console.warn = () => {};
// A throwaway service account, generated here, so that `db` exists and can be replaced below.
process.env.FIREBASE_SERVICE_ACCOUNT_KEY = JSON.stringify({
    type: 'service_account', project_id: 'unit-test', client_email: 'test@unit-test.iam.gserviceaccount.com',
    private_key: generateKeyPairSync('rsa', { modulusLength: 2048 }).privateKey.export({ type: 'pkcs8', format: 'pem' }),
});

const realFetch = globalThis.fetch;
globalThis.fetch = async (url, init) => {
    const u = String(url);
    if (u.startsWith('http://supabase.test')) {
        const table = new URL(u).pathname.split('/').pop();
        const rows = table === 'puzzle_path' ? [{ nextpuzzleid: 'p2' }]
            : table === 'puzzles' ? [{ puzzleid: 'p2', type: 'math', question: 'q', answer: 'a' }] : [];
        return new Response(JSON.stringify(rows), { status: 200, headers: { 'content-type': 'application/json' } });
    }
    if (u.startsWith('http://127.0.0.1')) return realFetch(url, init);
    throw new Error('unexpected outbound request in test');
};

const realSetInterval = globalThis.setInterval;
globalThis.setInterval = (...a) => realSetInterval(...a).unref();
const { default: express } = await import('express');
const firebase = await import('../config/firebaseAdmin.js');
const { default: analytics } = await import('../routes/analytics.routes.js');
const { default: ltv } = await import('../routes/ltv.routes.js');
const { default: cache } = await import('../routes/dailyPuzzleCache.routes.js');
const { default: endpoints } = await import('../routes/puzzleEndpoints.routes.js');
const { default: account } = await import('../routes/account.routes.js');
const { dailyPuzzleCache } = await import('../services/dailyPuzzleCache.js');
globalThis.setInterval = realSetInterval;

// Every Firestore access fails the way an exhausted quota does, and is counted.
let firestoreCalls = 0;
const exhausted = () => { firestoreCalls += 1; throw Object.assign(new Error('8 RESOURCE_EXHAUSTED: Quota exceeded.'), { code: 8 }); };
Object.defineProperty(firebase.db, 'collection', { value: exhausted, configurable: true });

const app = express();
app.use(express.json());
app.use('/api/analytics', analytics);
app.use('/api/ltv', ltv);
app.use('/api/daily-puzzle-cache', cache);
app.use('/', endpoints);
app.use('/', account);
const server = http.createServer(app);
await new Promise((r) => server.listen(0, r));
const base = `http://127.0.0.1:${server.address().port}`;
// The cache module opens a Redis client that retries for ever when there is no server.
test.after(() => { server.close(); dailyPuzzleCache.redis.disconnect?.(); globalThis.fetch = realFetch; });

const SCANS = ['/api/analytics/dau', '/api/analytics/mau', '/api/analytics/dau-trend?days=365', '/api/analytics/retention',
    '/api/analytics/dashboard', '/api/analytics/events?days=365', '/api/analytics/summary', '/api/analytics/cache-stats',
    '/api/ltv/user/someone', '/api/daily-puzzle-cache/popular-categories'];

test('routes that scan Firestore refuse anonymous and wrong-key callers without touching it', async () => {
    firestoreCalls = 0;
    for (const path of SCANS) {
        for (const headers of [{}, { 'x-admin-key': 'wrong' }]) {
            const r = await realFetch(base + path, { headers });
            assert.equal(r.status, 401, path);
        }
    }
    const post = await realFetch(`${base}/api/analytics/clear-cache`, { method: 'POST' });
    assert.equal(post.status, 401);
    assert.equal(firestoreCalls, 0);
});

test('the admin key still gets through', async () => {
    const r = await realFetch(`${base}/api/analytics/cache-stats`, { headers: { 'x-admin-key': KEY } });
    assert.equal(r.status, 200);
});

test('batch fetch still serves puzzles when Firestore is unavailable', async () => {
    const r = await realFetch(`${base}/batch-fetch-puzzles`, {
        method: 'POST', headers: { 'content-type': 'application/json' },
        body: JSON.stringify({ userId: 'u1', puzzleTypes: ['math'], puzzlesPerType: 1 }),
    });
    assert.equal(r.status, 200);
    const body = await r.json();
    assert.equal(body.success, true);
    assert.equal(body.puzzleData.math.length, 1);
});

test('rewards and badges answer "unavailable", not an error, when Firestore cannot be read', async () => {
    const r = await realFetch(`${base}/get-user-rewards-badges?email=someone%40example.test`);
    assert.equal(r.status, 200);
    assert.deepEqual(await r.json(), { rewards: [], badges: [], unavailable: true });
});

test('single puzzle fetch is served when Firestore is unavailable, and a rewards answer is reused', async () => {
    const next = await realFetch(`${base}/fetch-next-puzzle-ios/math?userId=u2&email=u2%40example.test`);
    assert.notEqual(next.status, 500);
    assert.ok(firestoreCalls > 0, 'the route reached the failing Firestore');

    // Same address, now readable: the first answer is kept and the second costs no reads.
    let reads = 0;
    Object.defineProperty(firebase.db, 'collection', { configurable: true, value: () => ({ doc: () => ({ get: async () => { reads += 1; return { exists: false }; } }) }) });
    for (let i = 0; i < 3; i++) {
        const r = await realFetch(`${base}/get-user-rewards-badges?email=cached%40example.test`);
        assert.deepEqual(await r.json(), { rewards: [], badges: [] });
    }
    assert.equal(reads, 2);
    Object.defineProperty(firebase.db, 'collection', { value: exhausted, configurable: true });
});

test('boot does not read puzzle configuration from Firestore', () => {
    const src = fs.readFileSync(new URL('../services/dailyPuzzleCacheSetup.js', import.meta.url), 'utf8');
    const boot = src.slice(src.indexOf('export async function initializeDailyPuzzleSystem'), src.indexOf('async function warmupCriticalCaches'));
    assert.ok(boot.length > 100);
    assert.doesNotMatch(boot, /db\.|initializeFirebaseCollections|puzzle_config/);
});

test('sign-in neither logs tokens nor fails on bookkeeping', () => {
    const src = fs.readFileSync(new URL('../routes/auth.routes.js', import.meta.url), 'utf8');
    assert.doesNotMatch(src, /console\.\w+\([^)]*(idToken|token\)|decodedToken)/);
    assert.match(src, /bookkeeping failed \(non-fatal\)/);
});
