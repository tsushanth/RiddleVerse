import test from 'node:test'
import assert from 'node:assert/strict'
import * as T from '../../src/components/puzzles/classic/tileMergeLogic.js'

test('slideLine merges once per pair, packs toward index 0', () => {
  assert.deepEqual(T.slideLine([2, 2, 2, 2]), { line: [4, 4, 0, 0], gained: 8 })
  assert.deepEqual(T.slideLine([2, 2, 4, 0]), { line: [4, 4, 0, 0], gained: 4 })
  assert.deepEqual(T.slideLine([0, 2, 0, 2]), { line: [4, 0, 0, 0], gained: 4 })
  assert.deepEqual(T.slideLine([4, 2, 2, 0]), { line: [4, 4, 0, 0], gained: 4 })
  assert.deepEqual(T.slideLine([2, 4, 8, 16]), { line: [2, 4, 8, 16], gained: 0 })
})

test('newGame starts with two tiles of 2 or 4', () => {
  const g = T.newGame(123)
  const tiles = g.cells.filter(Boolean)
  assert.equal(tiles.length, 2)
  assert.ok(tiles.every((v) => v === 2 || v === 4))
})

test('determinism: same seed and moves give the same game', () => {
  const play = (seed) => {
    let g = T.newGame(seed)
    const seq = ['left', 'up', 'right', 'down', 'left', 'left', 'up', 'right']
    for (let i = 0; i < 40; i++) g = T.move(g, seq[i % seq.length], 2048)
    return g
  }
  assert.deepEqual(play(7), play(7))
  assert.notDeepEqual(play(7).cells, play(8).cells)
})

test('move spawns exactly one tile and a no-op returns the same object', () => {
  let g = { cells: [2, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0], score: 0, moves: 0, seed: 5, reachedTarget: false, prev: null }
  const same = T.move(g, 'left', 128)
  assert.equal(same, g) // already against the left wall
  const g2 = T.move(g, 'right', 128)
  assert.notEqual(g2, g)
  assert.equal(g2.cells.filter(Boolean).length, 2)
  assert.equal(g2.cells[3], 2)
  assert.equal(g2.moves, 1)
})

test('merging adds score and sets reachedTarget', () => {
  const cells = new Array(16).fill(0)
  cells[0] = 64; cells[1] = 64
  const g = { cells, score: 0, moves: 0, seed: 1, reachedTarget: false, prev: null }
  const g2 = T.move(g, 'left', 128)
  assert.equal(g2.cells[0], 128)
  assert.equal(g2.score, 128)
  assert.equal(g2.reachedTarget, true)
  assert.equal(T.status(g2, T.configFor(0)), 'goal')
})

test('undo restores the exact previous state (single step, like Android)', () => {
  let g = T.newGame(11)
  const g1 = T.move(g, 'left', 128) !== g ? T.move(g, 'left', 128) : T.move(g, 'right', 128)
  assert.ok(T.canUndo(g1))
  const back = T.undo(g1)
  assert.deepEqual(back.cells, g.cells)
  assert.equal(back.moves, 0)
  assert.equal(T.canUndo(back), false)
  assert.equal(T.undo(back), back)
})

test('lose when board is full with no merges; win-locked when goal already reached', () => {
  const cells = [2, 4, 2, 4, 4, 2, 4, 2, 2, 4, 2, 4, 4, 2, 4, 2]
  const g = { cells, score: 0, moves: 10, seed: 1, reachedTarget: false, prev: null }
  assert.equal(T.canMove(g), false)
  assert.equal(T.status(g, T.configFor(0)), 'lost')
  assert.equal(T.status({ ...g, reachedTarget: true }, T.configFor(0)), 'won')
})

test('move budget: lost once spent without the goal, but not after the goal', () => {
  const cfg = T.configFor(0)
  const base = { ...T.newGame(3), moves: cfg.moveBudget }
  assert.equal(T.status(base, cfg), 'lost')
  assert.equal(T.status({ ...base, reachedTarget: true }, cfg), 'goal')
})

test('tier configs match Android', () => {
  assert.deepEqual([0, 1, 2, 3, 4].map((t) => T.configFor(t)), [
    { target: 128, moveBudget: 150 }, { target: 256, moveBudget: 260 }, { target: 512, moveBudget: 500 },
    { target: 1024, moveBudget: 900 }, { target: 2048, moveBudget: 1700 },
  ])
})

test('random play never produces invalid boards (invariants)', () => {
  let g = T.newGame(99)
  const dirs = T.DIRS
  for (let i = 0; i < 500 && T.canMove(g); i++) {
    const before = g.cells.reduce((a, b) => a + b, 0)
    const g2 = T.move(g, dirs[i % 4], 2048)
    if (g2 !== g) {
      const after = g2.cells.reduce((a, b) => a + b, 0)
      assert.ok(after === before + 2 || after === before + 4) // merges conserve sum, spawn adds 2/4
      assert.ok(g2.cells.every((v) => v === 0 || (v & (v - 1)) === 0))
    }
    g = g2
  }
})
