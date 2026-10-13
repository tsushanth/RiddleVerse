import test from 'node:test'
import assert from 'node:assert/strict'
import * as C from '../../src/components/puzzles/spatial/engine/crateshift.js'

test('generated levels: solver replay solves them, par matches, non-trivial', () => {
  for (const d of ['easy', 'medium', 'hard']) {
    for (let seed = 1; seed <= 6; seed++) {
      const l = C.generate(d, seed)
      const sol = C.solve(l, 600000)
      assert.ok(sol, `${d} ${seed}`)
      assert.equal(sol.length, l.par)
      const s = new C.CrateShiftState(l)
      for (const m of sol) assert.ok(s.move(m), 'illegal solver move')
      assert.ok(s.solved())
      assert.equal(s.pushes, l.minPushes)
      assert.ok(l.minPushes >= 3)
    }
  }
})

test('levels vary across seeds (no single fixed fallback)', () => {
  for (const d of ['medium', 'hard']) {
    const keys = new Set()
    for (let seed = 1; seed <= 6; seed++) keys.add(JSON.stringify(C.generate(d, seed).boxes))
    assert.ok(keys.size >= 4, `${d} produced ${keys.size} distinct layouts`)
  }
})

test('deterministic', () => {
  const a = C.generate('medium', 11)
  const b = C.generate('medium', 11)
  assert.deepEqual(a.boxes, b.boxes)
  assert.deepEqual(a.walls, b.walls)
  assert.equal(a.player, b.player)
})

function corridor(boxIdx) {
  const w = 6, h = 3
  const walls = Array.from({ length: w * h }, (_, i) => Math.floor(i / w) !== 1 || i % w === 0 || i % w === 5)
  const goals = new Array(w * h).fill(false)
  const boxes = new Array(w * h).fill(false)
  boxIdx.forEach((i) => (boxes[w + i] = true))
  return { w, h, walls, goals, boxes, player: w + 1, par: 0, minPushes: 0 }
}

test('move rules, undo and counting', () => {
  const l = corridor([2])
  l.goals[6 + 4] = true
  const s = new C.CrateShiftState(l)
  assert.equal(s.move(3), false) // wall
  assert.ok(s.move(1))
  assert.equal(s.pushes, 1)
  assert.ok(s.move(1))
  assert.ok(s.solved())
  assert.equal(s.move(1), false) // box would hit wall
  assert.ok(s.undo()); assert.equal(s.solved(), false)
  assert.ok(s.undo()); assert.equal(s.moves, 0); assert.equal(s.pushes, 0)
  assert.equal(s.undo(), false)
})

test('cannot push two crates; deadlock detection; unsolvable level', () => {
  const s = new C.CrateShiftState(corridor([2, 3]))
  assert.equal(s.move(1), false)
  const w = 5, h = 5
  const walls = Array.from({ length: 25 }, (_, i) => { const r = Math.floor(i / w), c = i % w; return r === 0 || c === 0 || r === 4 || c === 4 })
  const goals = new Array(25).fill(false); goals[2 * w + 3] = true
  const boxes = new Array(25).fill(false); boxes[w + 1] = true
  assert.equal(C.solveFrom(w, h, walls, goals, boxes, 2 * w + 2), null)
  const d = new C.CrateShiftState({ w, h, walls, goals, boxes, player: 2 * w + 2, par: 0, minPushes: 0 })
  assert.ok(d.deadlocked())
})

test('hint is the solver first move; stars follow par', () => {
  const l = C.generate('easy', 3)
  const s = new C.CrateShiftState(l)
  assert.equal(s.hint(), C.solve(l)[0])
  s.moves = l.par; assert.equal(s.stars(), 3)
  s.moves = l.par * 3; assert.equal(s.stars(), 1)
})
