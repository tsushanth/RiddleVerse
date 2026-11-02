// Player-state and pointer-geometry tests for Trail.
import test from 'node:test'
import assert from 'node:assert/strict'
import * as T from '../../src/components/puzzles/trail/engine.js'
import { TrailState, cellAt, stickyHit, cellsAlong } from '../../src/components/puzzles/trail/state.js'

const puzzle = () => T.generate(5, 0, 3)

test('following the stored solution solves it', () => {
  for (let seed = 1; seed <= 40; seed++) {
    const p = T.generate(6, seed % 3, seed)
    const s = new TrailState(p)
    assert.equal(s.head, p.solution[0])
    for (let i = 1; i < p.cells; i++) assert.ok(s.step(p.solution[i]), `seed ${seed} step ${i}`)
    assert.ok(s.solved())
    assert.equal(s.dotsVisited, p.waypoints.length)
  }
})

test('extend, retract by stepping back, cannot re-enter the start', () => {
  const p = puzzle(); const s = new TrailState(p)
  assert.ok(s.step(p.solution[1])); assert.ok(s.step(p.solution[2]))
  assert.equal(s.length, 3)
  assert.ok(s.step(p.solution[1]))        // stepping onto the previous cell removes the last one
  assert.equal(s.length, 2)
  assert.ok(s.step(p.solution[0]))        // back to the start leaves only the start
  assert.equal(s.length, 1)
  assert.equal(s.step(p.solution[0]), false)
  assert.equal(s.undo(), false)           // the start can never be removed
})

test('dots must be reached in order, and the last dot only once every other cell is covered', () => {
  const p = puzzle(); const s = new TrailState(p)
  const d3 = p.waypoints[2]
  for (let nb = 0; nb < p.cells; nb++) if (p.adjacent(nb, d3) && !p.blocked(nb, d3)) assert.equal(s.canStep(nb, d3), false)
  const last = p.waypoints[p.waypoints.length - 1]
  for (let nb = 0; nb < p.cells; nb++) if (p.adjacent(nb, last) && !p.blocked(nb, last)) assert.equal(s.canStep(nb, last), false)
})

test('walls are never crossed', () => {
  let checked = 0
  for (let seed = 1; seed <= 80; seed++) {
    const p = T.generate(6, 2, seed)
    if (p.wallCount === 0) continue
    const s = new TrailState(p)
    for (let a = 0; a < p.cells; a++) for (let b = 0; b < p.cells; b++) {
      if (p.adjacent(a, b) && p.blocked(a, b)) { assert.equal(s.canStep(a, b), false); checked++ }
    }
  }
  assert.ok(checked > 50)
  // end to end: from a cell with a wall beside it, stepping across fails and leaves the line unchanged
  const p = T.generate(7, 2, 11)
  const s = new TrailState(p)
  const head = s.head
  for (let to = 0; to < p.cells; to++) {
    if (p.adjacent(head, to) && p.blocked(head, to)) { assert.equal(s.step(to), false); assert.equal(s.length, 1) }
  }
})

test('adjacent() does not wrap around rows', () => {
  const p = puzzle()
  assert.equal(p.adjacent(4, 5), false) // end of row 0, start of row 1
  assert.equal(p.adjacent(0, 1), true)
  assert.equal(p.adjacent(0, 5), true)
  assert.equal(p.adjacent(0, 6), false)
})

test('truncateTo cuts the line back; tap extends or cuts', () => {
  const p = puzzle(); const s = new TrailState(p)
  for (let i = 1; i <= 5; i++) s.step(p.solution[i])
  assert.equal(s.length, 6)
  assert.ok(s.truncateTo(p.solution[2])); assert.equal(s.length, 3)
  assert.equal(s.truncateTo(s.head), false)
  assert.ok(s.tap(p.solution[3])); assert.equal(s.head, p.solution[3])   // adjacent: extends
  assert.equal(s.tap(p.solution[8]), false)                              // far away: refused
  assert.ok(s.tap(p.solution[1])); assert.equal(s.length, 2)             // on the line: cut back
})

test('flick interpolation: a fast drag across several cells fills every cell it crossed', () => {
  const n = 5, cell = 100
  // straight flick along row 2 from column 0 to column 4 in one pointer event
  const cs = Array.from(cellsAlong(50, 250, 450, 250, cell, n))
  assert.deepEqual(cs, [10, 11, 12, 13, 14])
  // flick outside the board is clamped onto it
  assert.deepEqual(Array.from(cellsAlong(50, 50, 900, 50, cell, n)), [0, 1, 2, 3, 4])
  // the same flick, applied to the game, walks the line along the solution when that row is on it
  const p = T.generate(6, 0, 4)
  const s = new TrailState(p)
  const sol = Array.from(p.solution)
  // find a run of >=3 solution cells in a straight line from the start
  let run = 1
  const d = sol[1] - sol[0]
  while (run < p.cells - 1 && sol[run + 1] - sol[run] === d) run++
  if (run >= 2) {
    const target = sol[run]
    assert.ok(s.advanceTo(target))
    assert.equal(s.head, target)
    assert.equal(s.length, run + 1)
  }
})

test('cellsAlong never skips a cell on a diagonal flick, and output is deduplicated', () => {
  const n = 6, cell = 80
  for (const [x0, y0, x1, y1] of [[10, 10, 470, 470], [470, 10, 10, 470], [40, 400, 440, 40], [5, 5, 6, 6]]) {
    const cs = Array.from(cellsAlong(x0, y0, x1, y1, cell, n))
    for (let i = 1; i < cs.length; i++) assert.notEqual(cs[i], cs[i - 1])
    // consecutive visited cells are at most diagonal neighbours (nothing jumped over)
    for (let i = 1; i < cs.length; i++) {
      const dr = Math.abs(Math.floor(cs[i] / n) - Math.floor(cs[i - 1] / n))
      const dc = Math.abs((cs[i] % n) - (cs[i - 1] % n))
      assert.ok(dr <= 1 && dc <= 1, `${cs[i - 1]} -> ${cs[i]}`)
    }
  }
})

test('cellAt and stickyHit', () => {
  assert.equal(cellAt(0, 0, 100, 5), 0)
  assert.equal(cellAt(499, 499, 100, 5), 24)
  assert.equal(cellAt(500, 10, 100, 5), -1)
  assert.equal(cellAt(-1, 10, 100, 5), -1)
  assert.equal(cellAt(10, 10, 0, 5), -1)
  // just outside cell 0 but inside the sticky margin
  assert.equal(stickyHit(110, 50, 0, 100, 5), true)
  assert.equal(stickyHit(130, 50, 0, 100, 5), false)
})

test('diagonal drag is forgiving: advanceTo walks a legal orthogonal path and stops when blocked', () => {
  // find a puzzle whose second solution cell is the head's neighbour, then target the cell two steps away diagonally
  for (let seed = 1; seed <= 60; seed++) {
    const p = T.generate(6, 0, seed)
    const sol = Array.from(p.solution)
    const a = sol[0], b = sol[1], c = sol[2]
    const dr = Math.floor(c / 6) - Math.floor(a / 6), dc = (c % 6) - (a % 6)
    if (Math.abs(dr) === 1 && Math.abs(dc) === 1) { // a -> b -> c turns a corner: c is diagonal from a
      const s = new TrailState(p)
      assert.ok(s.advanceTo(c))
      assert.equal(s.head, c)
      assert.equal(s.length, 3)
      assert.equal(s.pathCells()[1] === b || p.adjacent(s.pathCells()[1], c), true)
      return
    }
  }
  assert.fail('no corner found')
})

test('advanceTo onto a cell already on the line cuts back to it', () => {
  const p = puzzle(); const s = new TrailState(p)
  for (let i = 1; i <= 6; i++) s.step(p.solution[i])
  assert.ok(s.advanceTo(p.solution[2]))
  assert.equal(s.head, p.solution[2]); assert.equal(s.length, 3)
  assert.equal(s.advanceTo(s.head), false)
  assert.equal(s.advanceTo(-1), false)
  assert.equal(s.advanceTo(999), false)
})

test('hint reveals the next cells of the solution, cutting back a wrong line first', () => {
  const p = puzzle(); const s = new TrailState(p)
  let h = s.hint(3)
  assert.deepEqual(Array.from(h), Array.from(p.solution.slice(1, 4)))
  assert.equal(s.hintsUsed, 1)
  s.step(p.solution[1]); s.step(p.solution[2])
  // stray off the solution: take any legal step that is not the solution's next cell
  let strayed = false
  for (let to = 0; to < p.cells && !strayed; to++) {
    if (to !== p.solution[3] && s.canStep(s.head, to) && p.adjacent(s.head, to)) strayed = s.step(to)
  }
  if (strayed) {
    h = s.hint(3)
    assert.equal(s.head, p.solution[2]) // cut back to the last correct cell
    assert.deepEqual(Array.from(h), Array.from(p.solution.slice(3, 6)))
  }
  assert.equal(s.correctPrefix(), s.length)
})

test('hint on a finished line is empty; stars and score fall with hints', () => {
  const p = T.generate(5, 1, 9); const s = new TrailState(p)
  assert.equal(s.stars(), 3)
  const base = s.score()
  for (let i = 1; i < p.cells; i++) s.step(p.solution[i])
  assert.ok(s.solved())
  assert.equal(s.hint(3).length, 0)
  assert.equal(s.hintsUsed, 1)
  assert.equal(s.stars(), 2)
  assert.ok(s.score() < base)
  for (let i = 0; i < 5; i++) s.hint(3)
  assert.equal(s.stars(), 1)
  assert.ok(s.score() >= 10)
})

test('reset clears the line and counts; solved() is false until the last dot closes the line', () => {
  const p = puzzle(); const s = new TrailState(p)
  for (let i = 1; i < p.cells - 1; i++) s.step(p.solution[i])
  assert.equal(s.solved(), false)
  s.reset()
  assert.equal(s.length, 1); assert.equal(s.dotsVisited, 1); assert.equal(s.resets, 1)
  assert.equal(s.solved(), false)
})

test('keyboard: arrow keys extend and retract', () => {
  const p = puzzle(); const s = new TrailState(p)
  const n = p.n
  const dirOf = (a, b) => (b - a === 1 ? 'right' : b - a === -1 ? 'left' : b - a === n ? 'down' : 'up')
  assert.ok(s.arrow(dirOf(p.solution[0], p.solution[1])))
  assert.equal(s.head, p.solution[1])
  assert.ok(s.arrow(dirOf(p.solution[1], p.solution[2])))
  // the opposite arrow retracts
  assert.ok(s.arrow(dirOf(p.solution[2], p.solution[1])))
  assert.equal(s.head, p.solution[1]); assert.equal(s.length, 2)
  // off the board / blocked / bogus direction: refused, state unchanged
  const edge = new TrailState(p)
  const r0 = p.solution[0] % n === 0 ? 'left' : p.solution[0] < n ? 'up' : null
  if (r0) assert.equal(edge.arrow(r0), false)
  assert.equal(edge.arrow('sideways'), false)
  assert.equal(edge.length, 1)
})

test('encode / restore round trip', () => {
  const p = T.generate(6, 1, 5); const s = new TrailState(p)
  for (let i = 1; i < 12; i++) s.step(p.solution[i])
  s.hint(2)
  const t = new TrailState(p); t.restore(s.encode())
  assert.deepEqual(Array.from(t.pathCells()), Array.from(s.pathCells()))
  assert.equal(t.hintsUsed, 1)
  const bad = new TrailState(p); bad.restore('garbage'); assert.equal(bad.length, 1)
})

test('whyNot explains blocked arrows in plain language', () => {
  const p = T.generate(6, 2, 11); const s = new TrailState(p)
  const n = p.n
  const reasons = new Set()
  for (const dir of ['up', 'down', 'left', 'right']) {
    const before = s.length
    const moved = s.arrow(dir)
    if (!moved) { reasons.add(s.whyNot(dir)); assert.equal(s.length, before) }
    else s.undo()
  }
  assert.ok(reasons.size >= 1)
  for (const r of reasons) assert.match(r, /edge|wall|already|first|Cover|cannot/i)
  assert.equal(new TrailState(p).whyNot('diagonal'), 'Use the arrow keys.')
  void n
})
