// --- web classic puzzles (feat/web-puzzles-classic) --- Slide Tiles: pure game logic (port of SlideTilesLogic.kt)
import { mulberry32, randInt } from './rng.js'

/** Board size and shuffle depth per difficulty tier. */
export function configFor(tier) {
  switch (tier) {
    case 0: return { n: 3, shuffleMoves: 12 }
    case 1: return { n: 3, shuffleMoves: 24 }
    case 2: return { n: 4, shuffleMoves: 40 }
    case 3: return { n: 4, shuffleMoves: 80 }
    default: return { n: 5, shuffleMoves: 110 }
  }
}

export const solvedTiles = (n) => Array.from({ length: n * n }, (_, i) => (i === n * n - 1 ? 0 : i + 1))

/** Standard parity test: true when `tiles` (0 = blank, row-major) can reach the solved layout. */
export function isSolvable(n, tiles) {
  const seq = tiles.filter((t) => t !== 0)
  let inv = 0
  for (let i = 0; i < seq.length; i++) for (let j = i + 1; j < seq.length; j++) if (seq[i] > seq[j]) inv++
  if (n % 2 === 1) return inv % 2 === 0
  const blankRowFromBottom = n - Math.floor(tiles.indexOf(0) / n)
  return (inv + blankRowFromBottom) % 2 === 1
}

export function neighborsOfBlank(n, tiles) {
  const b = tiles.indexOf(0)
  const r = Math.floor(b / n)
  const c = b % n
  const out = []
  if (r > 0) out.push(b - n)
  if (r < n - 1) out.push(b + n)
  if (c > 0) out.push(b - 1)
  if (c < n - 1) out.push(b + 1)
  return out
}

function swapBlank(tiles, idx) {
  const t = tiles.slice()
  const b = t.indexOf(0)
  t[b] = t[idx]
  t[idx] = 0
  return t
}

const isSolvedTiles = (n, tiles) => tiles.every((v, i) => v === (i === n * n - 1 ? 0 : i + 1))

/**
 * Shuffles by performing `moves` legal slides from the solved board (never undoing the previous slide),
 * so the result is solvable by construction. Never returns a solved board.
 */
export function scramble(n, moves, rng) {
  let tiles = solvedTiles(n)
  let lastBlank = -1
  let made = 0
  while (made < moves || isSolvedTiles(n, tiles)) {
    const blank = tiles.indexOf(0)
    const options = neighborsOfBlank(n, tiles).filter((i) => i !== lastBlank)
    const pick = options[randInt(rng, options.length)]
    lastBlank = blank
    tiles = swapBlank(tiles, pick)
    made++
  }
  return tiles
}

export function newGame(tier, seed) {
  const { n, shuffleMoves } = configFor(tier)
  const tiles = scramble(n, shuffleMoves, mulberry32(seed))
  return { n, tiles, moves: 0, par: shuffleMoves, history: [] }
}

export const isSolved = (g) => isSolvedTiles(g.n, g.tiles)
export const canUndo = (g) => g.history.length > 0
export const blankIndex = (g) => g.tiles.indexOf(0)

/**
 * Tapping a tile in the blank's row/column slides every tile between it and the blank
 * (each shifted tile counts as one move). Returns null if the tap is not legal.
 */
export function tap(g, idx) {
  const { n, tiles } = g
  if (!Number.isInteger(idx) || idx < 0 || idx >= tiles.length || tiles[idx] === 0) return null
  const b = tiles.indexOf(0)
  const sameRow = Math.floor(idx / n) === Math.floor(b / n)
  const sameCol = idx % n === b % n
  if (!sameRow && !sameCol) return null
  const step = sameRow ? (idx > b ? 1 : -1) : (idx > b ? n : -n)
  const t = tiles.slice()
  let pos = b
  let shifted = 0
  while (pos !== idx) {
    t[pos] = t[pos + step]
    pos += step
    shifted++
  }
  t[idx] = 0
  return { ...g, tiles: t, moves: g.moves + shifted, history: [...g.history, { tiles, moves: g.moves }] }
}

/** Keyboard: the arrow gives the direction tiles travel. Returns the tile index to tap, or -1. */
export function keyTarget(g, dir) {
  const { n } = g
  const b = blankIndex(g)
  const r = Math.floor(b / n)
  const c = b % n
  switch (dir) {
    case 'left': return c < n - 1 ? b + 1 : -1
    case 'right': return c > 0 ? b - 1 : -1
    case 'up': return r < n - 1 ? b + n : -1
    case 'down': return r > 0 ? b - n : -1
    default: return -1
  }
}

export function undo(g) {
  if (g.history.length === 0) return g
  const last = g.history[g.history.length - 1]
  return { ...g, tiles: last.tiles, moves: last.moves, history: g.history.slice(0, -1) }
}
