import test from 'node:test'
import assert from 'node:assert/strict'
import * as cc from '../../src/components/puzzles/word/lib/codeCrackerLogic.js'

test('feedback pegs', () => {
  assert.deepEqual(cc.feedback([0, 1, 2, 3], [0, 1, 2, 3]), { exact: 4, misplaced: 0 })
  assert.deepEqual(cc.feedback([0, 1, 2, 3], [3, 2, 1, 0]), { exact: 0, misplaced: 4 })
  assert.deepEqual(cc.feedback([0, 1, 2, 3], [4, 4, 4, 4]), { exact: 0, misplaced: 0 })
})
test('feedback with repeated colours', () => {
  assert.deepEqual(cc.feedback([0, 0, 1, 1], [0, 1, 0, 0]), { exact: 1, misplaced: 2 })
  assert.deepEqual(cc.feedback([0, 1, 2, 3], [0, 0, 0, 0]), { exact: 1, misplaced: 0 })
  assert.deepEqual(cc.feedback([1, 1, 2, 3], [1, 0, 1, 1]), { exact: 1, misplaced: 1 })
  assert.deepEqual(cc.feedback([2, 2, 2, 2], [2, 2, 2, 2]), { exact: 4, misplaced: 0 })
  assert.throws(() => cc.feedback([1], [1, 2]))
})
test('config per difficulty', () => {
  assert.deepEqual(cc.config('easy'), { positions: 4, colors: 5, allowRepeats: false, maxTries: 10 })
  assert.equal(cc.config('hard').positions, 5)
  assert.equal(cc.config('hard').colors, 6)
})
test('symbols are distinct and colours covered', () => {
  assert.equal(new Set(cc.COLOR_SYMBOLS).size, 6)
  assert.equal(new Set(cc.COLOR_HEX).size, 6)
  assert.equal(cc.COLOR_NAMES.length, 6)
})
test('generation: deterministic, no repeats on easy, in range', () => {
  assert.deepEqual(cc.generate('hard', 5), cc.generate('hard', 5))
  for (let s = 0; s < 100; s++) {
    const e = cc.generate('easy', s)
    assert.equal(new Set(e.secret).size, 4)
    assert.ok(e.secret.every((c) => c >= 0 && c < 5))
    const h = cc.generate('hard', s)
    assert.equal(h.secret.length, 5)
    assert.ok(h.secret.every((c) => c >= 0 && c < 6))
  }
})
test('consistent() and score', () => {
  const secret = [0, 1, 2, 3]
  const hist = [[[0, 0, 1, 1], cc.feedback(secret, [0, 0, 1, 1])]]
  assert.ok(cc.consistent(secret, hist))
  assert.ok(!cc.consistent([5, 5, 5, 5], hist))
  assert.equal(cc.score(1, 10, 0), 158); assert.equal(cc.score(10, 10, 5), 10)
})
test('share text has no secret info beyond pegs', () => {
  const t = cc.shareText([[0, 1, 2, 3]], [{ exact: 2, misplaced: 1 }], true, 10)
  assert.match(t, /^RiddleVerse Code Cracker 1\/10/)
})
