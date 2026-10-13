import test from 'node:test'
import assert from 'node:assert/strict'
import { normDifficulty, seedOfPuzzle } from '../../src/components/puzzles/spatial/engine/common.js'
import { makeRng } from '../../src/components/puzzles/spatial/engine/rng.js'

test('normDifficulty accepts numbers and strings', () => {
  assert.deepEqual([1, 2, 3, 'EASY', 'Hard', undefined, 'x'].map(normDifficulty), ['easy', 'medium', 'hard', 'easy', 'hard', 'medium', 'medium'])
})
test('seedOfPuzzle prefers Android-style question seed, else hashes puzzleId', () => {
  assert.equal(seedOfPuzzle({ question: JSON.stringify({ seed: 123 }) }), 123)
  assert.equal(seedOfPuzzle({ puzzleId: 'a' }), seedOfPuzzle({ puzzleId: 'a' }))
  assert.notEqual(seedOfPuzzle({ puzzleId: 'a' }), seedOfPuzzle({ puzzleId: 'b' }))
})
test('rng deterministic and in range', () => {
  const a = makeRng(5), b = makeRng(5)
  for (let i = 0; i < 100; i++) { const x = a.nextInt(7); assert.equal(x, b.nextInt(7)); assert.ok(x >= 0 && x < 7) }
})
