import test from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { clampScoreDelta } from '../utils/scoreBounds.js';

test('passes whole-number deltas inside 1..1000 through unchanged', () => {
    for (const s of [1, 10, 200, 640, 1000]) assert.equal(clampScoreDelta(s), s, String(s));
});

test('clamps values above the cap to 1000 (older clients that post a running total keep working)', () => {
    for (const s of [1001, 5000, 140500, 1e9, Number.MAX_SAFE_INTEGER]) assert.equal(clampScoreDelta(s), 1000, String(s));
});

test('floors fractional values of at least 1', () => {
    assert.equal(clampScoreDelta(1.5), 1);
    assert.equal(clampScoreDelta(640.9), 640);
});

test('rejects zero, negative, sub-1, non-finite and non-number values', () => {
    for (const s of [0, -5, 0.5, NaN, Infinity, -Infinity, '500', null, undefined, {}, [500], true]) {
        assert.equal(clampScoreDelta(s), null, String(s));
    }
});

test('both /update-score routes use the clamp', () => {
    for (const f of ['../routes/scoring.routes.js', '../routes/leaderboard.routes.js']) {
        const src = readFileSync(new URL(f, import.meta.url), 'utf8');
        assert.match(src, /clampScoreDelta\(rawScore\)/, f);
        assert.match(src, /score === null/, f);
        assert.doesNotMatch(src, /typeof score !== 'number'/, f);
    }
});
