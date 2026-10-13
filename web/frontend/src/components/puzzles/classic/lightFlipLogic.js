// --- web classic puzzles (feat/web-puzzles-classic) --- Light Flip: pure game logic (port of LightFlipLogic.kt)
import { mulberry32, shuffled, popcount } from './rng.js'

export const N = 5

/** How many random presses are applied to the solved board, per difficulty tier. */
export function scramblePresses(tier) {
  return [3, 5, 8, 12, 16][tier] ?? 8
}

/** Bitmask of cells toggled when pressing idx (itself + orthogonal neighbours). */
export function pressMask(n, idx) {
  const r = Math.floor(idx / n)
  const c = idx % n
  let m = 1 << idx
  if (r > 0) m |= 1 << (idx - n)
  if (r < n - 1) m |= 1 << (idx + n)
  if (c > 0) m |= 1 << (idx - 1)
  if (c < n - 1) m |= 1 << (idx + 1)
  return m >>> 0
}

/**
 * Minimum-weight set of presses (bitmask) that turns every light off, or null if impossible.
 * Gaussian elimination over GF(2); free variables are enumerated to find the lightest solution.
 * Works for n*n <= 30 (bitmasks stay inside 31 bits).
 */
export function solveMinimal(n, state) {
  const m = n * n
  const stateBit = 1 << m // m <= 30
  // rows[i]: bit j = does pressing j toggle cell i; bit m = state of cell i
  const rows = []
  for (let i = 0; i < m; i++) {
    let row = 0
    for (let j = 0; j < m; j++) if (pressMask(n, j) & (1 << i)) row |= 1 << j
    if (state & (1 << i)) row |= stateBit
    rows.push(row)
  }
  const pivotCol = new Array(m).fill(-1)
  let r = 0
  for (let c = 0; c < m; c++) {
    let p = -1
    for (let i = r; i < m; i++) if (rows[i] & (1 << c)) { p = i; break }
    if (p < 0) continue
    const tmp = rows[p]; rows[p] = rows[r]; rows[r] = tmp
    for (let i = 0; i < m; i++) if (i !== r && rows[i] & (1 << c)) rows[i] ^= rows[r]
    pivotCol[r] = c
    r++
  }
  const coefMask = stateBit - 1
  for (let i = r; i < m; i++) if (rows[i] & stateBit && (rows[i] & coefMask) === 0) return null
  let particular = 0
  for (let i = 0; i < r; i++) if (rows[i] & stateBit) particular |= 1 << pivotCol[i]
  const pivots = new Set(pivotCol.slice(0, r))
  const nullVectors = []
  for (let f = 0; f < m; f++) {
    if (pivots.has(f)) continue
    let v = 1 << f
    for (let i = 0; i < r; i++) if (rows[i] & (1 << f)) v |= 1 << pivotCol[i]
    nullVectors.push(v)
  }
  if (nullVectors.length > 16) throw new Error('null space too large')
  let best = particular
  for (let combo = 1; combo < 1 << nullVectors.length; combo++) {
    let x = particular
    for (let k = 0; k < nullVectors.length; k++) if (combo & (1 << k)) x ^= nullVectors[k]
    if (popcount(x) < popcount(best)) best = x
  }
  return best >>> 0
}

/** Applies a press set to a light state (used by tests and the solver check). */
export function applyPresses(n, state, presses) {
  let s = state
  for (let i = 0; i < n * n; i++) if (presses & (1 << i)) s ^= pressMask(n, i)
  return s >>> 0
}

export const isSolved = (g) => g.lights === 0
export const canUndo = (g) => g.history.length > 0
export const isOn = (g, idx) => (g.lights & (1 << idx)) !== 0

export function press(g, idx) {
  if (!Number.isInteger(idx) || idx < 0 || idx >= g.n * g.n) return g
  return { ...g, lights: (g.lights ^ pressMask(g.n, idx)) >>> 0, moves: g.moves + 1, history: [...g.history, idx] }
}

export function undo(g) {
  if (g.history.length === 0) return g
  const idx = g.history[g.history.length - 1]
  return { ...g, lights: (g.lights ^ pressMask(g.n, idx)) >>> 0, moves: Math.max(0, g.moves - 1), history: g.history.slice(0, -1) }
}

/** A cell that is part of a minimum solution from the current position, or null if solved. */
export function hintCell(g) {
  const sol = solveMinimal(g.n, g.lights)
  if (sol === null || sol === 0) return null
  for (let i = 0; i < g.n * g.n; i++) if (sol & (1 << i)) return i
  return null
}

export const withHintUsed = (g) => ({ ...g, hintsUsed: g.hintsUsed + 1 })

export function newGame(tier, seed) {
  const n = N
  const rng = mulberry32(seed)
  const presses = scramblePresses(tier)
  for (;;) {
    const picks = shuffled([...Array(n * n).keys()], rng).slice(0, presses)
    let lights = 0
    for (const p of picks) lights ^= pressMask(n, p)
    lights >>>= 0
    if (lights === 0) continue
    const par = popcount(solveMinimal(n, lights))
    return { n, lights, moves: 0, par, hintsUsed: 0, history: [] }
  }
}
