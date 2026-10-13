import test from 'node:test'
import assert from 'node:assert/strict'
import * as N from '../../src/components/puzzles/spatial/engine/nonogram.js'

test('sizes per difficulty', () => {
  assert.deepEqual(['easy', 'medium', 'hard'].map(N.sizeFor), [5, 10, 15])
})

test('cluesOf', () => {
  assert.deepEqual(N.cluesOf([false, false]), [0])
  assert.deepEqual(N.cluesOf([true, true, false, true]), [2, 1])
})

test('solveLine deduces forced cells and detects contradictions', () => {
  assert.deepEqual(N.solveLine([4], [-1, -1, -1, -1, -1]), [-1, 1, 1, 1, -1])
  assert.deepEqual(N.solveLine([5], [-1, -1, -1, -1, -1]), [1, 1, 1, 1, 1])
  assert.deepEqual(N.solveLine([0], [-1, -1, -1]), [0, 0, 0])
  assert.equal(N.solveLine([3], [1, 0, -1, -1]), null)
})

test('generated puzzles are line-solvable and unique, all sizes, several seeds', () => {
  for (const d of ['easy', 'medium', 'hard']) {
    for (let seed = 1; seed <= 6; seed++) {
      const p = N.generate(d, seed)
      assert.equal(p.size, N.sizeFor(d))
      assert.ok(N.solvableByLogic(p), `${d} ${seed} not line solvable`)
      assert.equal(N.countSolutions(p.size, p.rowClues, p.colClues), 1, `${d} ${seed} not unique`)
      const g = N.propagate(p.size, p.rowClues, p.colClues)
      assert.ok(g.every((v, i) => (v === 1) === p.solution[i]), `${d} ${seed} propagation != solution`)
    }
  }
})

test('countSolutions finds ambiguity', () => {
  // 2x2 diagonal ambiguity: rows [1],[1] cols [1],[1]
  assert.equal(N.countSolutions(2, [[1], [1]], [[1], [1]]), 2)
})

test('deterministic per seed, different across seeds', () => {
  const a = N.generate('medium', 42)
  const b = N.generate('medium', 42)
  const c = N.generate('medium', 43)
  assert.deepEqual(a.solution, b.solution)
  assert.notDeepEqual(a.solution, c.solution)
})

test('state: strokes, undo, hint, mistakes, completion', () => {
  const p = N.generate('easy', 7)
  const s = new N.NonogramState(p)
  const wrong = p.solution.findIndex((v) => !v)
  s.applyStroke([wrong], 1)
  assert.deepEqual(s.mistakes(), [wrong])
  assert.ok(s.canUndo())
  s.undo()
  assert.deepEqual(s.mistakes(), [])
  s.beginStroke(); s.paint(0, 1); s.paint(1, 1); s.cancelStroke()
  assert.equal(s.cells[0], 0)
  s.applyStroke([wrong], 1)
  const h = s.hint() // fixes the mistake by marking X
  assert.equal(h, wrong)
  assert.equal(s.cells[wrong], 2)
  assert.equal(s.hintsUsed, 1)
  s.undo(); s.undo()
  for (let i = 0; i < p.solution.length; i++) if (p.solution[i]) s.applyStroke([i], 1)
  assert.ok(s.filledMatches())
  for (let r = 0; r < p.size; r++) assert.ok(s.isRowSatisfied(r))
  for (let c = 0; c < p.size; c++) assert.ok(s.isColSatisfied(c))
  assert.equal(s.score(), 60 - 15)
})
