import test from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { isValidScoreDelta } from '../utils/scoreBounds.js';

test('accepts whole-number deltas inside 1..1000', () => {
    for (const s of [1, 10, 200, 640, 1000]) assert.equal(isValidScoreDelta(s), true, String(s));
});

test('rejects negative, zero, fractional, huge, non-finite and non-number deltas', () => {
    for (const s of [0, -5, 1.5, 1001, 1e9, Number.MAX_SAFE_INTEGER, NaN, Infinity, '500', null, undefined, {}, [500]]) {
        assert.equal(isValidScoreDelta(s), false, String(s));
    }
});

test('both /update-score routes use the bound', () => {
    for (const f of ['../routes/scoring.routes.js', '../routes/leaderboard.routes.js']) {
        const src = readFileSync(new URL(f, import.meta.url), 'utf8');
        assert.match(src, /isValidScoreDelta\(score\)/, f);
        assert.doesNotMatch(src, /typeof score !== 'number'/, f);
    }
});
