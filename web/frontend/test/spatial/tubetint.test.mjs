import test from 'node:test'
import assert from 'node:assert/strict'
import * as T from '../../src/components/puzzles/spatial/engine/tubetint.js'

test('pour rules', () => {
  const t = [[0, 1, 1], [1], [], [2, 2, 2, 2]]
  assert.equal(T.canPour(t, 0, 1), true)
  assert.equal(T.canPour(t, 0, 2), true)
  assert.equal(T.canPour(t, 1, 0), true) // colour 1 onto top colour 1
  assert.equal(T.canPour(t, 3, 0), false) // colour mismatch
  assert.equal(T.canPour(t, 0, 3), false) // full tube
  assert.equal(T.canPour(t, 2, 0), false) // empty source
  assert.equal(T.canPour(t, 0, 0), false)
})

test('pour moves the whole top run limited by room', () => {
  const t = [[0, 1, 1], [1, 1, 1], []]
  assert.equal(T.pour(t, 0, 1), 1) // room for only one
  assert.deepEqual(t[1], [1, 1, 1, 1])
  assert.deepEqual(t[0], [0, 1])
  assert.equal(T.pour(t, 0, 1), 0) // full
  assert.equal(T.canPour([[2, 2, 2], []], 0, 1), false) // pointless relocation
})

test('generated puzzles are solvable and solver replay solves them', () => {
  for (const d of ['easy', 'medium', 'hard']) {
    for (let seed = 1; seed <= 8; seed++) {
      const p = T.generate(d, seed)
      const [colors, empties] = T.configFor(d)
      assert.equal(p.tubes.length, colors + empties)
      assert.ok(!T.isSolved(p.tubes))
      const counts = new Array(colors).fill(0)
      p.tubes.flat().forEach((c) => counts[c]++)
      assert.ok(counts.every((n) => n === T.CAPACITY), 'each colour has CAPACITY units')
      const sol = T.solve(p.tubes)
      assert.ok(sol, `${d} ${seed} unsolved`)
      const s = new T.TubeTintState(p)
      for (const [a, b] of sol) assert.ok(s.pour(a, b), 'illegal solver move')
      assert.ok(s.solved())
    }
  }
})

test('deterministic', () => {
  assert.deepEqual(T.generate('hard', 5).tubes, T.generate('hard', 5).tubes)
})

test('state: undo, add tube once, restart, hint', () => {
  const p = T.generate('easy', 3)
  const s = new T.TubeTintState(p)
  const h = s.hint()
  assert.ok(h)
  assert.ok(s.pour(h[0], h[1]))
  assert.equal(s.moves, 1)
  assert.ok(s.undo())
  assert.equal(s.moves, 0)
  assert.ok(s.addTube())
  assert.equal(s.addTube(), false)
  assert.equal(s.tubes.length, p.tubes.length + 1)
  assert.ok(s.undo()) // undoing the add restores the flag
  assert.equal(s.extraTubeAdded, false)
  s.addTube(); s.restart()
  assert.equal(s.tubes.length, p.tubes.length)
  assert.ok(s.score() >= 10)
})
