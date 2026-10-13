import test from 'node:test'
import assert from 'node:assert/strict'
import * as K from '../../src/components/puzzles/spatial/engine/conduit.js'

test('rotation and taps', () => {
  assert.equal(K.rotCW(K.N), K.E)
  assert.equal(K.rotCW(K.W), K.N)
  assert.equal(K.rotCW(K.N | K.E), K.E | K.S)
  assert.equal(K.tapsToReach(K.N, K.S), 2)
})

test('generated puzzles: sizes, scrambled, solvable, solution valid', () => {
  for (const [d, n] of [['easy', 4], ['medium', 5], ['hard', 7]]) {
    for (let seed = 1; seed <= 10; seed++) {
      const p = K.generate(d, seed)
      assert.equal(p.n, n)
      assert.ok(K.isSolved(p.n, p.source, p.solution), 'generator solution invalid')
      assert.ok(!K.isSolved(p.n, p.source, p.start), 'starts solved')
      assert.ok(p.par >= Math.floor((n * n) / 3))
      const sol = K.solve(p.n, p.source, p.start)
      assert.ok(sol && K.isSolved(p.n, p.source, sol))
    }
  }
})

test('replaying par taps toward the solution solves the board', () => {
  const p = K.generate('medium', 4)
  const s = new K.ConduitState(p)
  for (let i = 0; i < s.masks.length; i++) {
    while (s.masks[i] !== p.solution[i]) s.rotate(i)
  }
  assert.ok(s.solved())
  assert.equal(s.moves, p.par)
  assert.equal(s.stars(), 3)
})

test('undo, hint, restart', () => {
  const s = new K.ConduitState(K.generate('easy', 2))
  s.rotate(0); assert.ok(s.undo()); assert.equal(s.moves, 0)
  const before = s.masks.slice()
  assert.deepEqual(s.masks, before)
  const h = s.hint(); assert.ok(h >= 0); assert.equal(s.hintsUsed, 1)
  s.rotate(3); s.restart(); assert.equal(s.moves, 0)
})

test('deterministic', () => {
  assert.deepEqual(K.generate('hard', 9).start, K.generate('hard', 9).start)
})
