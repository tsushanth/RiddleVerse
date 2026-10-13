import test from 'node:test'
import assert from 'node:assert/strict'
import * as D from '../../src/components/puzzles/classic/discTowerLogic.js'
import { mulberry32 } from '../../src/components/puzzles/classic/rng.js'

test('discs per tier and par = 2^n - 1', () => {
  assert.deepEqual([0, 1, 2, 3, 4].map(D.discsFor), [3, 4, 5, 6, 7])
  assert.deepEqual([3, 4, 5, 6, 7].map(D.parFor), [7, 15, 31, 63, 127])
})

test('move rules: smaller on bigger only, empty source rejected, same peg rejected', () => {
  const g = D.newGame(3)
  assert.equal(D.canMove(g, 0, 1), true)
  assert.equal(D.canMove(g, 1, 0), false)
  assert.equal(D.canMove(g, 0, 0), false)
  const g2 = D.move(g, 0, 1)
  assert.deepEqual(g2.pegs, [[3, 2], [1], []])
  assert.equal(D.canMove(g2, 0, 1), false) // 2 on 1
  assert.equal(D.move(g2, 0, 1), g2)
  assert.equal(D.canMove(g2, 0, 5), false)
  assert.equal(D.canMove(g2, -1, 1), false)
})

test('undo reverses moves', () => {
  let g = D.newGame(4)
  g = D.move(D.move(g, 0, 1), 0, 2)
  const back = D.undo(D.undo(g))
  assert.deepEqual(back.pegs, D.newGame(4).pegs)
  assert.equal(back.moves, 0)
  assert.equal(D.undo(back), back)
})

test('following the hint from the start reaches the goal in exactly 2^n - 1 moves (n = 3..7)', () => {
  for (let n = 3; n <= 7; n++) {
    let g = D.newGame(n)
    let guard = 0
    while (!D.isSolved(g) && guard++ < 1000) {
      const h = D.hintMove(g)
      assert.ok(h)
      assert.equal(D.canMove(g, h[0], h[1]), true, 'hint is legal')
      g = D.move(g, h[0], h[1])
    }
    assert.equal(D.isSolved(g), true)
    assert.equal(g.moves, 2 ** n - 1)
    assert.equal(D.hintMove(g), null)
  }
})

test('hint from any reachable position still terminates and never exceeds par', () => {
  const rng = mulberry32(5)
  for (let trial = 0; trial < 200; trial++) {
    const n = 3 + (trial % 5)
    let g = D.newGame(n)
    for (let i = 0; i < 40; i++) { // random legal walk
      const from = Math.floor(rng() * 3), to = Math.floor(rng() * 3)
      g = D.move(g, from, to)
    }
    const walked = g.moves
    let h = 0
    while (!D.isSolved(g) && h++ < 300) {
      const m = D.hintMove(g)
      assert.ok(m)
      g = D.move(g, m[0], m[1])
    }
    assert.equal(D.isSolved(g), true)
    assert.ok(g.moves - walked <= 2 ** n - 1)
  }
})

test('solved detection requires all discs on the right peg', () => {
  let g = D.newGame(2)
  g = D.move(g, 0, 1); g = D.move(g, 0, 2); g = D.move(g, 1, 2)
  assert.equal(D.isSolved(g), true)
  assert.equal(g.moves, 3)
})
