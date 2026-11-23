import test from 'node:test';
import assert from 'node:assert/strict';
import { specFor, FIRST_DAY, ROTATION, todayFor } from '../services/telegram/dailySchedule.js';
import { loadCatalog } from '../services/playCatalog.js';

test('daily game matches the Android pinned values for Daily #1..#12 (golden vectors from DailyPuzzleScheduleTest.kt)', () => {
    const expected = ['sudoku', 'pixellogic', 'trail', 'binarygrid', 'crateshift', 'wordladder', 'tubetint', 'conduit', 'wordhive', 'crowns', 'signgrid', 'crowns'];
    expected.forEach((type, i) => {
        const s = specFor(FIRST_DAY + i);
        assert.equal(s.type, type, `#${i + 1}`);
        assert.equal(s.number, i + 1);
    });
});

test('every game in the rotation has a public /play page', () => {
    const slugs = new Set(loadCatalog().games.map((g) => g.slug));
    for (const t of ROTATION) assert.ok(slugs.has(t), t);
});

test('todayFor switches at UTC midnight', () => {
    const a = todayFor(Date.UTC(2026, 10, 22, 23, 59, 59));
    const b = todayFor(Date.UTC(2026, 10, 23, 0, 0, 0));
    assert.equal(b.number, a.number + 1);
});
