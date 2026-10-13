import test from 'node:test'
import assert from 'node:assert/strict'
import * as lb from '../../src/components/puzzles/word/lib/letterBalloonsLogic.js'
import { isBlocked } from '../../src/components/puzzles/word/lib/blocklist.js'

test('12 categories, 30 entries each, no blocked words', () => {
  assert.equal(Object.keys(lb.CATEGORIES).length, 12)
  for (const [, ws] of Object.entries(lb.CATEGORIES)) {
    assert.equal(ws.length, 30)
    for (const w of ws) assert.ok(/^[a-z ]+$/.test(w) && !isBlocked(w), w)
  }
})
test('every difficulty tier is populated for every category', () => {
  for (const ws of Object.values(lb.CATEGORIES)) {
    for (const t of ['easy', 'medium', 'hard']) assert.ok(ws.some((w) => lb.tier(w) === t))
  }
})
test('gameplay: wrong letters pop balloons, win, lose, spaces', () => {
  let g = lb.newGame('new zealand', 'Countries', 3)
  assert.deepEqual(lb.maskedLetters(g).slice(0, 4), [null, null, null, ' '])
  g = lb.guess(g, 'q'); g = lb.guess(g, 'q'); g = lb.guess(g, '1'); g = lb.guess(g, ' ')
  assert.equal(lb.livesLeft(g), 2)
  g = lb.guess(g, 'x'); g = lb.guess(g, 'j')
  assert.ok(lb.isLost(g)); assert.equal(lb.score(g), 0)
  assert.equal(lb.guess(g, 'n'), g)
  let w = lb.newGame('cat', 'Animals', 7)
  for (const c of 'cat') w = lb.guess(w, c)
  assert.ok(lb.isWon(w)); assert.equal(lb.score(w), 120)
})
test('hint reveals most frequent letter, costs score', () => {
  let g = lb.newGame('banana', 'Food', 7)
  g = lb.hint(g)
  assert.deepEqual(g.guessed, ['a']); assert.equal(g.hintsUsed, 1)
  g = lb.hint(g)
  assert.deepEqual(g.guessed, ['a', 'n'])
  for (const c of 'b') g = lb.guess(g, c)
  assert.ok(lb.isWon(g)); assert.equal(lb.score(g), 50 + 70 - 40)
})
test('generation deterministic; lives per difficulty', () => {
  assert.deepEqual(lb.generate('medium', 9), lb.generate('medium', 9))
  assert.equal(lb.lives('easy'), 8); assert.equal(lb.lives('hard'), 6); assert.equal(lb.lives('medium'), 7)
  const cats = new Set()
  for (let i = 0; i < 100; i++) {
    const p = lb.generate('hard', i)
    assert.ok(lb.CATEGORIES[p.category].includes(p.word))
    cats.add(p.category)
  }
  assert.ok(cats.size >= 10)
})
