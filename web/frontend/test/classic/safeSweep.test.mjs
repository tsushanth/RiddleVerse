import test from 'node:test'
import assert from 'node:assert/strict'
import * as M from '../../src/components/puzzles/classic/safeSweepLogic.js'

test('tier configs match Android', () => {
  assert.deepEqual([0, 1, 2, 3, 4].map((t) => { const c = M.configFor(t); return [c.rows, c.cols, c.mines] }),
    [[8, 8, 8], [8, 8, 11], [10, 10, 16], [10, 10, 22], [12, 14, 34]])
})

test('first tap is always safe and opens an area, for every tier and 300 seeds / every start cell sample', () => {
  for (let tier = 0; tier < 5; tier++) {
    const cfg = M.configFor(tier)
    for (let seed = 1; seed <= 60; seed++) {
      for (const first of [0, cfg.cols - 1, cfg.rows * cfg.cols - 1, Math.floor((cfg.rows * cfg.cols) / 2), seed % (cfg.rows * cfg.cols)]) {
        const g = M.tap(M.newGameForTier(tier, seed), first)
        assert.equal(g.status === 'lost', false)
        assert.equal(M.isMine(g, first), false)
        assert.equal(g.mines.filter(Boolean).length, cfg.mines)
        assert.equal(M.adjacentMines(g, first), 0, 'neighbours of first tap are protected => flood opens')
        assert.ok(M.safeOpened(g) > 1 || M.isRevealed(g, first))
      }
    }
  }
})

test('mines are not placed until the first tap, and layout is deterministic per seed', () => {
  const g0 = M.newGameForTier(2, 5)
  assert.equal(M.started(g0), false)
  const a = M.tap(g0, 11)
  const b = M.tap(M.newGameForTier(2, 5), 11)
  assert.deepEqual(a.mines, b.mines)
  assert.notDeepEqual(a.mines, M.tap(M.newGameForTier(2, 6), 11).mines)
  assert.equal(g0.mines, null, 'input not mutated')
})

test('flood fill opens zero regions and numbered border', () => {
  // 3x3, single mine in the corner (index 8)
  let g = M.withMines(3, 3, [8])
  g = M.tap(g, 0)
  assert.equal(M.isRevealed(g, 8), false)
  assert.equal(M.safeOpened(g), 8)
  assert.equal(g.status, 'won')
})

test('hitting a mine loses, reveals unflagged mines, records the exploded cell', () => {
  let g = M.withMines(4, 4, [0, 15])
  g = M.toggleFlag(g, 15)
  g = M.tap(g, 0)
  assert.equal(g.status, 'lost')
  assert.equal(g.explodedIndex, 0)
  assert.equal(M.isFlagged(g, 15), true)
  assert.equal(M.tap(g, 5), g, 'no moves after the game ends')
})

test('flags: toggle, count, block reveal, cannot flag revealed', () => {
  let g = M.withMines(4, 4, [0, 15])
  g = M.toggleFlag(g, 5)
  assert.equal(M.minesLeft(g), 1)
  assert.equal(M.tap(g, 5), g, 'flagged cell ignores tap')
  g = M.toggleFlag(g, 5)
  assert.equal(M.minesLeft(g), 2)
  g = M.tap(g, 6)
  const revealed = [...Array(16).keys()].find((i) => M.isRevealed(g, i))
  assert.equal(M.toggleFlag(g, revealed), g)
})

test('chord: opens remaining neighbours when flags match; wrong flag blows up; mismatch does nothing', () => {
  // 3x3 mine at 0 and 2; cell 1 sees both. cell 4 sees both too.
  let g = M.withMines(3, 3, [0, 2])
  g = M.tap(g, 4)
  assert.equal(M.isRevealed(g, 4), true)
  assert.equal(M.adjacentMines(g, 4), 2)
  assert.equal(M.chord(g, 4), g, 'no flags => nothing')
  let one = M.toggleFlag(g, 0)
  assert.equal(M.chord(one, 4), one, 'flag count mismatch => nothing')
  let right = M.toggleFlag(one, 2)
  const opened = M.chord(right, 4)
  assert.equal(opened.status, 'won', 'all safe cells open, game won')
  // wrong flags: flag two safe cells -> chord opens the real mines
  let wrong = M.toggleFlag(M.toggleFlag(g, 1), 3)
  const boom = M.tap(wrong, 4) // tap on a revealed number chords
  assert.equal(boom.status, 'lost')
})

test('win: revealing every safe cell wins and auto-flags mines', () => {
  let g = M.withMines(2, 3, [0])
  for (const i of [1, 2, 3, 4, 5]) g = M.tap(g, i)
  assert.equal(g.status, 'won')
  assert.equal(M.isFlagged(g, 0), true)
  assert.equal(M.minesLeft(g), 0)
})

test('encode-free immutability: operations never mutate their input', () => {
  const g = M.newGameForTier(1, 3)
  const snap = JSON.stringify(g)
  M.tap(g, 10); M.toggleFlag(g, 3)
  assert.equal(JSON.stringify(g), snap)
})

test('newGame validates mine count', () => {
  assert.throws(() => M.newGame(3, 3, 0, 1))
  assert.throws(() => M.newGame(3, 3, 9, 1))
})
