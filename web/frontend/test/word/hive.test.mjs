import test from 'node:test'
import assert from 'node:assert/strict'
import * as wh from '../../src/components/puzzles/word/lib/wordHiveLogic.js'
import { isBlocked } from '../../src/components/puzzles/word/lib/blocklist.js'
import { lists } from './support.mjs'

const L = new Set('abcdefg')

test('scoring: 4 letters = 1, longer = length, pangram +7', () => {
  assert.equal(wh.wordScore('abcd', L), 1)
  assert.equal(wh.wordScore('abcde', L), 5)
  assert.equal(wh.wordScore('abcdefga', L), 8 + 7)
  assert.equal(wh.wordScore('abcdefg', L), 14)
  assert.ok(wh.isPangram('gfedcba', L)); assert.ok(!wh.isPangram('abcdef', L))
})
test('ranks: percent thresholds', () => {
  assert.equal(wh.rankIndex(0, 100), 0)
  assert.equal(wh.rankIndex(2, 100), 1)
  assert.equal(wh.rankIndex(49, 100), 6)
  assert.equal(wh.rankIndex(50, 100), 7)
  assert.equal(wh.rankIndex(70, 100), 8)
  assert.equal(wh.rankIndex(100, 100), 9)
  assert.equal(wh.rankIndex(5, 0), 0)
  assert.equal(wh.RANKS[9][0], 'Queen of the Hive')
  assert.deepEqual([wh.targetRankIndex('easy'), wh.targetRankIndex('medium'), wh.targetRankIndex('hard')], [6, 7, 8])
})
test('solutions need centre letter, only hive letters, 4+', () => {
  const s = wh.solutions(['abed', 'bad', 'cafe', 'face', 'zebra', 'abed', 'dead'], L, 'a')
  assert.deepEqual(s, ['abed', 'cafe', 'dead', 'face'])
  assert.deepEqual(wh.solutions(['abed', 'cafe'], L, 'g'), [])
})
test('rejections', () => {
  const sol = new Set(['abed', 'cafe'])
  assert.equal(wh.rejection('abe', L, 'a', sol, []), 'Too short')
  assert.equal(wh.rejection('abez', L, 'a', sol, []), 'Bad letters')
  assert.equal(wh.rejection('bcde', L, 'a', sol, []), 'Missing centre letter')
  assert.equal(wh.rejection('abed', L, 'a', sol, ['abed']), 'Already found')
  assert.equal(wh.rejection('abad', L, 'a', sol, []), 'Not in word list')
  assert.equal(wh.rejection('abed', L, 'a', sol, []), null)
})
test('generated hives: deterministic, valid, have pangram, scores add up', () => {
  for (const diff of ['easy', 'medium', 'hard']) {
    for (const seed of [1, 7, 99]) {
      const p = wh.generate(lists, diff, seed)
      assert.ok(p)
      assert.deepEqual(p, wh.generate(lists, diff, seed))
      assert.equal(p.outer.length, 6)
      const set = new Set(p.outer + p.center)
      assert.equal(set.size, 7)
      assert.ok(p.words.some((w) => wh.isPangram(w, set)))
      assert.equal(p.maxScore, p.words.reduce((a, w) => a + wh.wordScore(w, set), 0))
      for (const w of p.words) {
        assert.ok(w.length >= 4 && w.includes(p.center) && [...w].every((c) => set.has(c)))
        assert.ok(!isBlocked(w)); assert.ok(lists.commonSet.has(w))
      }
    }
  }
})
