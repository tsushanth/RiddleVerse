// --- web classic puzzles (feat/web-puzzles-classic) --- Tile Merge: pure game logic (port of TileMergeLogic.kt)
import { mulberry32, hashSeed, randInt, shuffled } from './rng.js'

export const SIZE = 4
export const DIRS = ['up', 'down', 'left', 'right']
export const UNDOS = 3

/** Goal: reach `target` within `moveBudget` effective moves. */
export function configFor(tier) {
  switch (tier) {
    case 0: return { target: 128, moveBudget: 150 }
    case 1: return { target: 256, moveBudget: 260 }
    case 2: return { target: 512, moveBudget: 500 }
    case 3: return { target: 1024, moveBudget: 900 }
    default: return { target: 2048, moveBudget: 1700 }
  }
}

/** Slides a line toward index 0, merging equal neighbours once. Returns { line, gained }. */
export function slideLine(line) {
  const packed = line.filter((v) => v !== 0)
  const out = []
  let gained = 0
  let i = 0
  while (i < packed.length) {
    if (i + 1 < packed.length && packed[i] === packed[i + 1]) {
      const merged = packed[i] * 2
      out.push(merged)
      gained += merged
      i += 2
    } else {
      out.push(packed[i])
      i++
    }
  }
  while (out.length < line.length) out.push(0)
  return { line: out, gained }
}

export const maxTile = (g) => Math.max(0, ...g.cells)
export const canUndo = (g) => g.prev !== null

export function canMove(g) {
  const n = SIZE
  if (g.cells.some((v) => v === 0)) return true
  for (let r = 0; r < n; r++) {
    for (let c = 0; c < n; c++) {
      const v = g.cells[r * n + c]
      if (c + 1 < n && g.cells[r * n + c + 1] === v) return true
      if (r + 1 < n && g.cells[(r + 1) * n + c] === v) return true
    }
  }
  return false
}

export function newGame(seed) {
  const rng = mulberry32(seed)
  const n = SIZE
  const cells = new Array(n * n).fill(0)
  const spots = shuffled([...Array(n * n).keys()], rng).slice(0, 2)
  for (const s of spots) cells[s] = randInt(rng, 10) === 0 ? 4 : 2
  return { cells, score: 0, moves: 0, seed: seed >>> 0, reachedTarget: false, prev: null }
}

// Spawns derive from (seed, moves), so seed + move sequence always yields the same game.
function spawn(cells, seed, moves) {
  const empty = []
  cells.forEach((v, i) => { if (v === 0) empty.push(i) })
  if (empty.length === 0) return cells
  const rng = mulberry32(hashSeed(seed, moves))
  const spot = empty[randInt(rng, empty.length)]
  const value = randInt(rng, 10) === 0 ? 4 : 2
  const next = cells.slice()
  next[spot] = value
  return next
}

/** Applies a swipe. Returns the same object if nothing moved. */
export function move(g, dir, target) {
  const n = SIZE
  const next = new Array(n * n).fill(0)
  let gained = 0
  for (let k = 0; k < n; k++) {
    const idx = []
    for (let j = 0; j < n; j++) {
      switch (dir) {
        case 'left': idx.push(k * n + j); break
        case 'right': idx.push(k * n + (n - 1 - j)); break
        case 'up': idx.push(j * n + k); break
        default: idx.push((n - 1 - j) * n + k)
      }
    }
    const { line, gained: gg } = slideLine(idx.map((i) => g.cells[i]))
    gained += gg
    idx.forEach((cellIndex, j) => { next[cellIndex] = line[j] })
  }
  if (next.every((v, i) => v === g.cells[i])) return g
  const moves = g.moves + 1
  const cells = spawn(next, g.seed, moves)
  return {
    cells,
    score: g.score + gained,
    moves,
    seed: g.seed,
    reachedTarget: g.reachedTarget || Math.max(...cells) >= target,
    prev: { cells: g.cells, score: g.score, moves: g.moves, reachedTarget: g.reachedTarget },
  }
}

export function undo(g) {
  if (!g.prev) return g
  return {
    cells: g.prev.cells, score: g.prev.score, moves: g.prev.moves, seed: g.seed,
    reachedTarget: g.prev.reachedTarget, prev: null,
  }
}

/**
 * 'playing' | 'goal' (target reached, player chooses Finish / Keep going) | 'won' (board locked after the goal)
 * | 'lost' (no moves, or move budget spent before the goal). Continuing past the goal lifts the move cap.
 */
export function status(g, cfg) {
  const won = g.reachedTarget
  if (!canMove(g)) return won ? 'won' : 'lost'
  if (!won && g.moves >= cfg.moveBudget) return 'lost'
  return won ? 'goal' : 'playing'
}
