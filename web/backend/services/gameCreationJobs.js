// Async job layer for game creation (submit + poll) built ON TOP of the existing synchronous
// POST /api/game-creation/generate SSE endpoint, so every gate (paywall, rate limit, coins, Supabase save,
// thumbnail, analytics) stays in exactly one place. The jobs routes call the sync endpoint on this same
// machine, keep reading its event stream in the background and expose the latest state to polling clients.
//
// State is IN MEMORY, per machine. Single-machine deployments (or sticky routing) are fine; with several
// machines a poll that lands on another machine gets 404 (the Android client treats repeated poll errors as
// "lost contact"). See docs/game-creation-jobs.md for the deploy checklist.

import crypto from 'node:crypto';

export const JOB_TTL_MS = 2 * 60 * 60 * 1000;

export function createJobStore({ now = () => Date.now(), ttlMs = JOB_TTL_MS } = {}) {
    const jobs = new Map();

    function sweep() {
        const t = now();
        for (const [id, j] of jobs) if (t - j.updatedAtMs > ttlMs) jobs.delete(id);
    }

    return {
        create() {
            sweep();
            const jobId = crypto.randomUUID();
            const t = now();
            const job = { jobId, status: 'running', startedAt: new Date(t).toISOString(), startedAtMs: t, updatedAtMs: t, progressPercent: 0 };
            jobs.set(jobId, job);
            return job;
        },
        get(jobId) { sweep(); return jobs.get(jobId) || null; },
        delete(jobId) { return jobs.delete(jobId); },
        size() { return jobs.size; },
    };
}

/** Splits an SSE text stream (async iterable of strings/Buffers) into parsed `data:` JSON events. */
export async function* sseEvents(chunks) {
    const decoder = new TextDecoder();
    let buffer = '';
    for await (const chunk of chunks) {
        buffer += typeof chunk === 'string' ? chunk : decoder.decode(chunk, { stream: true });
        const lines = buffer.split('\n');
        buffer = lines.pop() || '';
        for (const line of lines) {
            if (!line.startsWith('data: ')) continue;
            try { yield JSON.parse(line.substring(6)); } catch { /* malformed event: skip */ }
        }
    }
}

/**
 * Drives [job] from the sync endpoint's events. Never throws. Resolves when the stream ends.
 * Event mapping (sync -> job): status -> progress fields, result -> succeeded (+gameId/result), error -> failed.
 */
export async function pumpJob(job, chunks, { now = () => Date.now() } = {}) {
    const touch = () => { job.updatedAtMs = now(); job.updatedAt = new Date(job.updatedAtMs).toISOString(); };
    try {
        for await (const ev of sseEvents(chunks)) {
            if (ev.type === 'status') {
                Object.assign(job, {
                    phase: ev.phase, message: ev.message, detail: ev.detail,
                    progressPercent: ev.progressPercent ?? job.progressPercent,
                    progressEndPct: ev.progressEndPct, estimatedSecondsRemaining: ev.estimatedSecondsRemaining,
                });
                touch();
            } else if (ev.type === 'result') {
                if (ev.success === false) {
                    Object.assign(job, { status: 'failed', error: 'The game was built but could not be saved. Please try again.', reason: 'save_failed' });
                } else {
                    Object.assign(job, {
                        status: 'succeeded', progressPercent: 100, gameId: ev.gameId, gameTitle: ev.gameTitle,
                        result: { bundle: ev.bundle, bundleSize: ev.bundleSize, files: ev.files, generationTime: ev.generationTime, quality: ev.quality },
                    });
                }
                touch();
            } else if (ev.type === 'error') {
                Object.assign(job, { status: 'failed', error: ev.error || 'Generation failed', reason: ev.quotaExhausted ? 'quota' : 'worker_error' });
                if (ev.quotaExhausted) { job.quotaExhausted = true; job.resetTime = ev.resetTime; }
                touch();
            }
        }
    } catch (e) {
        if (job.status === 'running') Object.assign(job, { status: 'failed', error: 'Connection to build server lost', reason: 'stream_error' });
        touch();
    }
    if (job.status === 'running') {
        Object.assign(job, { status: 'failed', error: 'Build ended without a result', reason: 'no_result' });
        touch();
    }
}

/** Client-facing view of a job (never leaks internal timestamps). */
export function publicJob(job) {
    const { startedAtMs, updatedAtMs, ...rest } = job;
    if (rest.status !== 'succeeded') delete rest.result;
    return rest;
}
