import test from 'node:test';
import assert from 'node:assert/strict';
import { createJobStore, pumpJob, publicJob, sseEvents } from '../services/gameCreationJobs.js';

const sse = (...events) => events.map(e => `data: ${JSON.stringify(e)}\n\n`);
async function* chunks(parts) { for (const p of parts) yield p; }

test('status then result -> succeeded with gameId and bundle', async () => {
    const store = createJobStore();
    const job = store.create();
    assert.equal(job.status, 'running');
    await pumpJob(job, chunks(sse(
        { type: 'status', phase: 'build', message: 'Building', progressPercent: 20, progressEndPct: 40 },
        { type: 'result', success: true, gameId: 'g1', bundle: 'QUJD', bundleSize: 3, files: [], generationTime: 5, quality: {} },
    )));
    const pub = publicJob(job);
    assert.equal(pub.status, 'succeeded');
    assert.equal(pub.gameId, 'g1');
    assert.equal(pub.result.bundle, 'QUJD');
    assert.equal(pub.progressPercent, 100);
    assert.equal('startedAtMs' in pub, false);
});

test('event split across chunks and CRLF-free partial lines are reassembled', async () => {
    const job = createJobStore().create();
    const line = sse({ type: 'result', success: true, gameId: 'g2', bundle: 'x' })[0];
    await pumpJob(job, chunks([line.slice(0, 15), line.slice(15)]));
    assert.equal(job.status, 'succeeded');
    assert.equal(job.gameId, 'g2');
});

test('error event -> failed with message; quota fields kept', async () => {
    const job = createJobStore().create();
    await pumpJob(job, chunks(sse({ type: 'error', error: 'No capacity', quotaExhausted: true, resetTime: 'tomorrow' })));
    const pub = publicJob(job);
    assert.equal(pub.status, 'failed');
    assert.equal(pub.error, 'No capacity');
    assert.equal(pub.quotaExhausted, true);
    assert.equal('result' in pub, false);
});

test('result with success:false (DB save failed) -> failed, not succeeded', async () => {
    const job = createJobStore().create();
    await pumpJob(job, chunks(sse({ type: 'result', success: false, bundle: 'x' })));
    assert.equal(job.status, 'failed');
    assert.equal(job.reason, 'save_failed');
});

test('stream that ends without a result fails the job; a throwing stream fails it too', async () => {
    const a = createJobStore().create();
    await pumpJob(a, chunks(sse({ type: 'status', message: 'x' })));
    assert.equal(a.status, 'failed');
    const b = createJobStore().create();
    await pumpJob(b, (async function* () { throw new Error('boom'); })());
    assert.equal(b.status, 'failed');
    assert.equal(b.reason, 'stream_error');
});

test('store: unknown id is null, delete works, expired jobs are swept', () => {
    let t = 1000;
    const store = createJobStore({ now: () => t, ttlMs: 100 });
    const job = store.create();
    assert.ok(store.get(job.jobId));
    assert.equal(store.get('nope'), null);
    t += 500;
    assert.equal(store.get(job.jobId), null);
    const j2 = store.create();
    assert.equal(store.delete(j2.jobId), true);
    assert.equal(store.get(j2.jobId), null);
});

test('sseEvents skips malformed events', async () => {
    const out = [];
    for await (const e of sseEvents(chunks(['data: {bad\n', 'data: {"type":"status"}\n']))) out.push(e);
    assert.deepEqual(out, [{ type: 'status' }]);
});
