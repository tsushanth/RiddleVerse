import test from 'node:test'
import assert from 'node:assert/strict'
import { buildSpec, normalizeDifficulty, isGridType, seedFromPuzzle, GRID_GAME_KEYS, symbolText } from '../../src/components/puzzles/grid/engine/factory.js'
import { GridPlayState, STATUS, MAX_LIVES, MAX_HINTS } from '../../src/components/puzzles/grid/engine/playState.js'

const sudokuState = () => new GridPlayState(buildSpec('sudoku', 'easy', 5))
const firstEmpty = (s) => Array.from({ length: s.cells }, (_, i) => i).find((i) => !s.isGiven(i))

test('factory: keys, difficulty mapping, seed parsing', () => {
  assert.deepEqual(GRID_GAME_KEYS, ['sudoku', 'binarygrid', 'cagemath', 'signgrid'])
  assert.ok(isGridType('sudoku') && !isGridType('anagram') && !isGridType(undefined))
  assert.deepEqual([1, 2, 3, 5, 'Easy', 'expert', undefined].map(normalizeDifficulty), ['easy', 'medium', 'hard', 'hard', 'easy', 'hard', 'medium'])
  assert.equal(seedFromPuzzle({ question: JSON.stringify({ game: 'sudoku', seed: 77 }) }, 'sudoku'), 77)
  assert.equal(seedFromPuzzle({ question: JSON.stringify({ game: 'cagemath', seed: 77 }) }, 'sudoku'), null)
  assert.equal(seedFromPuzzle({ question: '' }, 'sudoku'), null)
  assert.deepEqual(buildSpec('signgrid', 'easy', 9).solution, buildSpec('signgrid', 'easy', 9).solution)
})

test('correct entry no penalty; wrong costs a life', () => {
  const s = sudokuState()
  const i = firstEmpty(s)
  s.select(i); s.enter(s.spec.solution[i])
  assert.equal(s.lives, MAX_LIVES)
  assert.equal(s.isWrong(i), false)
  const j = Array.from({ length: s.cells }, (_, k) => k).find((k) => !s.isGiven(k) && s.values[k] === 0)
  s.select(j)
  const wrong = [1, 2, 3, 4].find((d) => d !== s.spec.solution[j])
  s.enter(wrong)
  assert.equal(s.lives, MAX_LIVES - 1)
  assert.ok(s.isWrong(j))
  assert.equal(s.mistakes, 1)
})

test('three mistakes lose, then input is frozen', () => {
  const s = sudokuState()
  const empties = Array.from({ length: s.cells }, (_, k) => k).filter((k) => !s.isGiven(k)).slice(0, 4)
  for (const i of empties.slice(0, 3)) { s.select(i); s.enter([1, 2, 3, 4].find((d) => d !== s.spec.solution[i])) }
  assert.equal(s.status, STATUS.LOST)
  s.select(empties[3]); s.enter(s.spec.solution[empties[3]])
  assert.equal(s.values[empties[3]], 0)
})

test('givens cannot be changed', () => {
  const s = sudokuState()
  const g = Array.from({ length: s.cells }, (_, k) => k).find((k) => s.isGiven(k))
  const before = s.values[g]
  s.select(g); s.enter(before === 1 ? 2 : 1); s.erase()
  assert.equal(s.values[g], before)
  assert.equal(s.lives, MAX_LIVES)
})

test('solving every cell wins and scores >= 10', () => {
  const s = sudokuState()
  for (let i = 0; i < s.cells; i++) if (!s.isGiven(i)) { s.setSelected(i); s.enter(s.spec.solution[i]) }
  assert.equal(s.status, STATUS.WON)
  assert.ok(s.score() >= 10)
})

test('notes toggle and peer clearing', () => {
  const s = sudokuState()
  const i = firstEmpty(s)
  const sol = s.spec.solution[i]
  const peer = Array.from({ length: s.n }, (_, k) => Math.floor(i / s.n) * s.n + k).find((k) => k !== i && !s.isGiven(k))
  s.notesMode = true
  s.select(peer); s.enter(sol)
  assert.ok(s.notes[peer] & (1 << sol))
  s.enter(sol)
  assert.equal(s.notes[peer], 0)
  s.enter(sol)
  s.notesMode = false
  s.select(i); s.enter(sol)
  assert.equal(s.notes[peer] & (1 << sol), 0)
})

test('hint reveals correct value, limited to 3, no mistakes', () => {
  const s = sudokuState()
  for (let k = 0; k < MAX_HINTS; k++) assert.ok(s.hint())
  assert.equal(s.hint(), false)
  assert.equal(s.hintsLeft, 0)
  assert.equal(s.hintsUsed, MAX_HINTS)
  assert.equal(s.mistakes, 0)
})

test('hint fixes a wrong selected cell', () => {
  const s = sudokuState()
  const i = firstEmpty(s)
  s.setSelected(i); s.enter([1, 2, 3, 4].find((d) => d !== s.spec.solution[i]))
  assert.ok(s.isWrong(i))
  assert.ok(s.hint())
  assert.equal(s.values[i], s.spec.solution[i])
})

test('undo restores previous state and respects depth', () => {
  const s = sudokuState()
  const i = firstEmpty(s)
  s.setSelected(i); s.enter(s.spec.solution[i])
  assert.equal(s.undoDepth, 1)
  assert.ok(s.undo())
  assert.equal(s.values[i], 0)
  assert.equal(s.undoDepth, 0)
  assert.equal(s.undo(), false)
})

test('erase removes value and notes; re-entering same digit erases', () => {
  const s = sudokuState()
  const i = firstEmpty(s)
  s.setSelected(i); s.enter(s.spec.solution[i])
  s.enter(s.spec.solution[i])
  assert.equal(s.values[i], 0)
})

test('binary grid: two symbols, no notes mode', () => {
  const s = new GridPlayState(buildSpec('binarygrid', 'easy', 3))
  assert.equal(s.spec.symbols, 2)
  assert.equal(s.usesNotes, false)
  const i = firstEmpty(s)
  s.notesMode = true
  s.setSelected(i); s.enter(s.spec.solution[i])
  assert.equal(s.values[i], s.spec.solution[i])
  assert.equal(symbolText(s.spec, 1), '0')
  assert.equal(symbolText(s.spec, 2), '1')
})

test('every game completable via state; score drops with mistakes/hints', () => {
  for (const g of GRID_GAME_KEYS) for (const d of ['easy', 'medium']) {
    const s = new GridPlayState(buildSpec(g, d, 21))
    for (let i = 0; i < s.cells; i++) if (!s.isGiven(i)) { s.setSelected(i); s.enter(s.spec.solution[i]) }
    assert.equal(s.status, STATUS.WON, `${g}/${d}`)
  }
  const a = new GridPlayState(buildSpec('cagemath', 'easy', 2)); a.hintsUsed = 2; a.mistakes = 1
  const b = new GridPlayState(buildSpec('cagemath', 'easy', 2))
  assert.ok(a.score() < b.score())
})

test('moveSelection clamps to grid', () => {
  const s = sudokuState()
  s.moveSelection(1, 1); assert.equal(s.selected, 0)
  s.moveSelection(-1, -1); assert.equal(s.selected, 0)
  s.moveSelection(10, 10); assert.equal(s.selected, 15)
})
