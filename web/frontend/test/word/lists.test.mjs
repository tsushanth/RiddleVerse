import test from 'node:test'
import assert from 'node:assert/strict'
import { isBlocked } from '../../src/components/puzzles/word/lib/blocklist.js'
import { buildLists } from '../../src/components/puzzles/word/lib/wordLists.js'
import { createRng, shuffled, normalizeDifficulty } from '../../src/components/puzzles/word/lib/prng.js'
import { createStats } from '../../src/components/puzzles/word/lib/stats.js'
import { lists } from './support.mjs'

test('blocklist exact words and inflections', () => {
  assert.ok(isBlocked('porn'))
  assert.ok(isBlocked('Whores'))
  assert.ok(isBlocked('fucking'))
  assert.ok(!isBlocked('class'))
  assert.ok(!isBlocked('shell'))
})
test('common list has no blocked word and is lowercase a-z', () => {
  assert.ok(lists.common.length > 30000)
  for (const w of lists.common) {
    assert.match(w, /^[a-z]+$/)
    assert.ok(!isBlocked(w), w)
  }
})
test('buildLists filters and dedupes', () => {
  const l = buildLists(['the', 'The', 'porn', 'cat1', '', 'house', 'houses'], ['aahed', 'bad', 'ZZZZZ'])
  assert.deepEqual(l.common, ['the', 'house', 'houses'])
  assert.ok(l.valid5.has('aahed') && l.valid5.has('zzzzz') && l.valid5.has('house') && !l.valid5.has('bad'))
})
test('answer pool excludes simple plurals', () => {
  const pool = lists.answerPool(0, 1400)
  assert.ok(pool.length > 500)
  for (const w of pool) assert.ok(!(w.endsWith('s') && !w.endsWith('ss') && lists.commonSet.has(w.slice(0, -1))), w)
})
test('prng determinism, range, shuffle', () => {
  const a = createRng(7), b = createRng(7)
  for (let i = 0; i < 20; i++) assert.equal(a.next(), b.next())
  const r = createRng('abc')
  for (let i = 0; i < 1000; i++) { const x = r.int(5); assert.ok(x >= 0 && x < 5) }
  assert.deepEqual(shuffled([1, 2, 3, 4, 5], createRng(3)), shuffled([1, 2, 3, 4, 5], createRng(3)))
  assert.deepEqual(shuffled([1, 2, 3, 4, 5], createRng(3)).sort(), [1, 2, 3, 4, 5])
})
test('difficulty normalisation', () => {
  assert.equal(normalizeDifficulty(1), 'easy')
  assert.equal(normalizeDifficulty(3), 'hard')
  assert.equal(normalizeDifficulty(undefined), 'medium')
  assert.equal(normalizeDifficulty('Hard'), 'hard')
})
test('stats', () => {
  const m = new Map()
  const s = createStats({ getItem: (k) => m.get(k) ?? null, setItem: (k, v) => m.set(k, v) })
  s.record('g', true, 3, 6); s.record('g', true, 3, 6); s.record('g', false, 0, 6); s.record('g', true, 1, 6)
  const snap = s.snapshot('g', 6)
  assert.equal(snap.played, 4); assert.equal(snap.won, 3); assert.equal(snap.winPercent, 75)
  assert.equal(snap.streak, 1); assert.equal(snap.bestStreak, 2)
  assert.deepEqual(snap.distribution, [1, 0, 2, 0, 0, 0])
})
