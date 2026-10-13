import test from 'node:test'
import assert from 'node:assert/strict'
import * as P from '../../src/components/puzzles/spatial/engine/piecepack.js'

const square = P.SHAPES[9]
const dot = P.SHAPES[0]

test('drop origin: centred on pointer and lifted', () => {
  assert.deepEqual(P.dropOrigin(50, 80, square, 0, 0, 10, 20), [4, 4])
})
test('drop origin clamps to board', () => {
  assert.deepEqual(P.dropOrigin(-100, -100, square, 0, 0, 10, 20), [0, 0])
  assert.deepEqual(P.dropOrigin(1000, 1000, square, 0, 0, 10, 20), [6, 6])
})
test('drop origin respects board offset', () => {
  assert.deepEqual(P.dropOrigin(105, 55, dot, 100, 0, 10, 0), [5, 0])
})
test('edge drops always fit an empty board', () => {
  const b = new Array(64).fill(0)
  for (const p of P.SHAPES) for (let x = -20; x <= 100; x += 7) for (let y = -20; y <= 140; y += 7) {
    const [r, c] = P.dropOrigin(x, y, p, 0, 0, 10, 20)
    assert.ok(P.fits(b, p, r, c))
  }
})

test('placing clears full rows and columns together', () => {
  const b = new Array(64).fill(0)
  for (let c = 0; c < 7; c++) b[c] = 1 // row 0 missing col 7
  for (let r = 1; r < 8; r++) b[r * 8 + 7] = 1 // col 7 missing row 0
  assert.equal(P.place(b, dot, 0, 7), 2)
  assert.ok(b.every((v) => v === 0))
})

test('state: place, undo, status, determinism', () => {
  const a = new P.PiecePackState(99, 'medium')
  const b = new P.PiecePackState(99, 'medium')
  assert.deepEqual(a.hand, b.hand)
  const mv = a.hint()
  assert.ok(mv)
  assert.ok(a.tryPlace(mv[0], mv[1], mv[2]))
  assert.equal(a.piecesPlaced, 1)
  assert.ok(a.canUndo() && a.undoMove())
  assert.equal(a.piecesPlaced, 0)
  assert.equal(a.tryPlace(0, -1, 0), false)
})

test('hint bot clears the target within budget on most seeds (easy)', () => {
  let wins = 0
  for (let seed = 1; seed <= 20; seed++) {
    const s = new P.PiecePackState(seed, 'easy')
    while (s.status === P.STATUS.PLAYING) {
      const m = s.hint()
      if (!m) break
      assert.ok(s.tryPlace(m[0], m[1], m[2]))
    }
    if (s.status === P.STATUS.WON) wins++
  }
  assert.ok(wins >= 14, `bot wins ${wins}/20`)
})

test('every dealt hand has a placeable piece on the empty board', () => {
  for (let seed = 1; seed <= 30; seed++) {
    const h = P.deal(seed, 0, new Array(64).fill(0), 1)
    assert.ok(h.some((id) => P.anyFit(new Array(64).fill(0), P.SHAPES[id])))
  }
})
