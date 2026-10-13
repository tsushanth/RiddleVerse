import test from 'node:test'
import assert from 'node:assert/strict'
import * as wg from '../../src/components/puzzles/word/lib/wordGuessLogic.js'
import { lists } from './support.mjs'

const fb = (g, a) => wg.evaluate(g, a).join(',')

test('duplicate letters: LLAMA vs ALLOY', () => {
  assert.equal(fb('llama', 'alloy'), 'present,correct,present,absent,absent')
})
test('duplicate letters: only one yellow when answer has one copy', () => {
  assert.equal(fb('geese', 'these'), 'absent,absent,correct,correct,correct')
  assert.equal(fb('speed', 'abide'), 'absent,absent,present,absent,present')
  assert.equal(fb('eerie', 'berry'), 'absent,correct,correct,absent,absent')
})
test('exact match claims letter before misplaced', () => {
  // answer has a single A at index 2; guess AAxxx: index 2 isn't A so first A is present only if no exact
  assert.equal(fb('aabbb', 'ccacc'), 'present,absent,absent,absent,absent')
  assert.equal(fb('ccacd', 'aaaaa'), 'absent,absent,correct,absent,absent')
})
test('all correct', () => assert.equal(fb('crane', 'crane'), 'correct,correct,correct,correct,correct'))
test('keyboard keeps best state', () => {
  const ks = wg.keyboardStates(['llama', 'alloy'], 'alloy')
  assert.equal(ks.l, 'correct')
  assert.equal(ks.a, 'correct')
  assert.equal(ks.m, 'absent')
})
test('hard mode: green must stay, yellow must be reused', () => {
  assert.equal(wg.hardModeViolation(['crane'], 'crate', 'crazy'), 'Letter 5 must be E')
  // CRANE vs CRATE: C R A green, N absent, E green
  assert.match(wg.hardModeViolation(['crane'], 'crate', 'plate'), /Letter 1 must be C/)
  assert.equal(wg.hardModeViolation(['crane'], 'crate', 'crate'), null)
  // yellow reuse: guess "tacos" vs answer "stare": t present, a present, s present
  assert.match(wg.hardModeViolation(['tacos'], 'stare', 'brown'), /must contain/)
  assert.equal(wg.hardModeViolation(['tacos'], 'stare', 'stare'), null)
})
test('hard mode via submit', () => {
  let g = wg.newGame('crate', true)
  for (const c of 'crane') g = wg.typeLetter(g, c)
  g = wg.submit(g, () => true).game
  for (const c of 'plate') g = wg.typeLetter(g, c)
  const r = wg.submit(g, () => true)
  assert.equal(r.outcome, 'hardMode')
})
test('submit outcomes and status', () => {
  let g = wg.newGame('crate', false)
  assert.equal(wg.submit(g, () => true).outcome, 'tooShort')
  for (const c of 'zzzzz') g = wg.typeLetter(g, c)
  assert.equal(wg.submit(g, () => false).outcome, 'notInList')
  g = wg.backspace(wg.backspace(wg.backspace(wg.backspace(wg.backspace(g)))))
  for (let i = 0; i < 6; i++) {
    for (const c of 'plumb') g = wg.typeLetter(g, c)
    g = wg.submit(g, () => true).game
  }
  assert.equal(wg.status(g), 'lost')
  assert.equal(wg.submit(g, () => true).outcome, 'gameOver')
  let w = wg.newGame('crate', false)
  for (const c of 'crate') w = wg.typeLetter(w, c)
  assert.equal(wg.status(wg.submit(w, () => true).game), 'won')
})
test('typeLetter ignores non letters and overflow', () => {
  let g = wg.newGame('crate', false)
  g = wg.typeLetter(g, '1')
  for (const c of 'abcdefg') g = wg.typeLetter(g, c)
  assert.equal(g.current, 'abcde')
})
test('share text is spoiler free', () => {
  const t = wg.shareText(['crane', 'crate'], 'crate', true, true)
  assert.match(t, /^RiddleVerse Word Guess 2\/6\*/)
  assert.ok(!/[a-z]{5}/i.test(t.split('\n').slice(2).join('')))
  assert.match(wg.shareText(['plumb'], 'crate', false, false), /X\/6/)
})
test('score', () => {
  assert.equal(wg.score(1, false, false), 160)
  assert.equal(wg.score(6, true, false), 30)
  assert.equal(wg.score(6, true, true), 50)
})
test('hint position', () => {
  const g = { ...wg.newGame('crate', false), guesses: ['crane'] }
  assert.equal(wg.hintPosition(g), 3)
})
test('generation is deterministic and from pool; every answer is valid', () => {
  const a = wg.generate(lists, 'medium', 12345)
  const b = wg.generate(lists, 'medium', 12345)
  assert.deepEqual(a, b)
  assert.ok(lists.valid5.has(a.answer))
  assert.equal(wg.generate(lists, 'hard', 1).hard, true)
  const seen = new Set()
  for (let i = 0; i < 50; i++) seen.add(wg.generate(lists, 'easy', i).answer)
  assert.ok(seen.size > 20)
})
