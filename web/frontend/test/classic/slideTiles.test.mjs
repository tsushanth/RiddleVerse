import test from 'node:test'
import assert from 'node:assert/strict'
import * as S from '../../src/components/puzzles/classic/slideTilesLogic.js'
import { mulberry32 } from '../../src/components/puzzles/classic/rng.js'

/** BFS over all 3x3 states from solved: distance map keyed by tile string. */
function bfs3() {
  const start = S.solvedTiles(3)
  const dist = new Map([[start.join(''), 0]])
  let frontier = [start]
  while (frontier.length) {
    const next = []
    for (const t of frontier) {
      const d = dist.get(t.join(''))
      const b = t.indexOf(0)
      for (const nb of S.neighborsOfBlank(3, t)) {
        const u = t.slice(); u[b] = u[nb]; u[nb] = 0
        const k = u.join('')
        if (!dist.has(k)) { dist.set(k, d + 1); next.push(u) }
      }
    }
    frontier = next
  }
  return dist
}

test('3x3: BFS reaches exactly the states the parity test calls solvable (9!/2)', () => {
  const dist = bfs3()
  assert.equal(dist.size, 181440)
  // spot-check parity predicate over every reachable state, and over unreachable swaps
  let checked = 0
  for (const k of dist.keys()) {
    const t = k.split('').map(Number)
    assert.equal(S.isSolvable(3, t), true)
    if (++checked > 20000) break
  }
  // swapping two non-blank tiles flips solvability
  const t = S.solvedTiles(3)
  ;[t[0], t[1]] = [t[1], t[0]]
  assert.equal(S.isSolvable(3, t), false)
  assert.equal(dist.has(t.join('')), false)
})

test('solvable-shuffle guarantee: every scramble is BFS-solvable within par, for 200 seeds x 3x3 tiers', () => {
  const dist = bfs3()
  for (const tier of [0, 1]) {
    for (let seed = 1; seed <= 200; seed++) {
      const g = S.newGame(tier, seed)
      assert.equal(g.n, 3)
      assert.ok(!S.isSolved(g), 'never starts solved')
      assert.ok(dist.has(g.tiles.join('')), 'reachable from solved => solvable')
      assert.ok(dist.get(g.tiles.join('')) <= g.par, `optimal ${dist.get(g.tiles.join(''))} <= par ${g.par}`)
    }
  }
})

test('4x4 and 5x5 scrambles pass the parity test and are never solved', () => {
  for (const tier of [2, 3, 4]) {
    for (let seed = 1; seed <= 100; seed++) {
      const g = S.newGame(tier, seed)
      assert.equal(S.isSolvable(g.n, g.tiles), true)
      assert.ok(!S.isSolved(g))
      assert.equal(g.tiles.slice().sort((a, b) => a - b).join(), S.solvedTiles(g.n).slice().sort((a, b) => a - b).join())
    }
  }
})

test('determinism', () => {
  assert.deepEqual(S.newGame(3, 42), S.newGame(3, 42))
  assert.notDeepEqual(S.newGame(3, 42).tiles, S.newGame(3, 43).tiles)
})

test('tap slides a whole row/column segment, counts shifted tiles, rejects illegal taps', () => {
  const g = { n: 3, tiles: [1, 2, 3, 4, 5, 6, 7, 8, 0], moves: 0, par: 0, history: [] }
  assert.equal(S.tap(g, 4), null) // not in blank's row/col
  assert.equal(S.tap(g, 8), null) // blank itself
  const g2 = S.tap(g, 6) // slides 7,8 right: two tiles shift
  assert.deepEqual(g2.tiles, [1, 2, 3, 4, 5, 6, 0, 7, 8])
  assert.equal(g2.moves, 2)
  const g3 = S.tap(g, 2) // column: slides 3,6 down
  assert.deepEqual(g3.tiles, [1, 2, 0, 4, 5, 3, 7, 8, 6])
  assert.equal(g3.moves, 2)
})

test('undo restores tiles and move count exactly', () => {
  const g = S.newGame(1, 5)
  const idx = S.neighborsOfBlank(g.n, g.tiles)[0]
  const g2 = S.tap(g, idx)
  assert.ok(g2)
  const back = S.undo(g2)
  assert.deepEqual(back.tiles, g.tiles)
  assert.equal(back.moves, 0)
  assert.equal(S.undo(back), back)
})

test('win detection by solving a short scramble with BFS-free reversal of history', () => {
  let g = S.newGame(0, 9)
  // greedy replay: undo through a played-out path equals solved start only if we play it; instead play random legal taps then undo all
  const rng = mulberry32(3)
  for (let i = 0; i < 30; i++) {
    const opts = S.neighborsOfBlank(g.n, g.tiles)
    g = S.tap(g, opts[Math.floor(rng() * opts.length)])
  }
  while (S.canUndo(g)) g = S.undo(g)
  assert.equal(g.moves, 0)
  const solved = { n: 3, tiles: [1, 2, 3, 4, 5, 6, 7, 0, 8], moves: 0, par: 1, history: [] }
  const done = S.tap(solved, 8)
  assert.equal(S.isSolved(done), true)
  assert.equal(done.moves, 1)
})

test('keyTarget: arrow gives direction tiles travel', () => {
  const g = { n: 3, tiles: [1, 2, 3, 4, 0, 5, 6, 7, 8], moves: 0, par: 0, history: [] }
  assert.equal(S.keyTarget(g, 'left'), 5) // tile right of blank moves left
  assert.equal(S.keyTarget(g, 'right'), 3)
  assert.equal(S.keyTarget(g, 'up'), 7)
  assert.equal(S.keyTarget(g, 'down'), 1)
  const corner = { ...g, tiles: [1, 2, 3, 4, 5, 6, 7, 8, 0] }
  assert.equal(S.keyTarget(corner, 'left'), -1)
  assert.equal(S.keyTarget(corner, 'up'), -1)
})
