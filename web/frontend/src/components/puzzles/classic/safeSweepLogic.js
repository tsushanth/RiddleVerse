// --- web classic puzzles (feat/web-puzzles-classic) --- Safe Sweep: pure game logic (port of SafeSweepLogic.kt)
import { mulberry32, shuffled } from './rng.js'

export function configFor(tier) {
  switch (tier) {
    case 0: return { rows: 8, cols: 8, mines: 8, sizeLabel: 'Small' }
    case 1: return { rows: 8, cols: 8, mines: 11, sizeLabel: 'Small' }
    case 2: return { rows: 10, cols: 10, mines: 16, sizeLabel: 'Medium' }
    case 3: return { rows: 10, cols: 10, mines: 22, sizeLabel: 'Medium' }
    default: return { rows: 12, cols: 14, mines: 34, sizeLabel: 'Large' }
  }
}

/**
 * Game = { rows, cols, mineCount, seed, mines: null | boolean[], state: string[] ('.' hidden, 'F' flag, 'R' revealed),
 * status: 'playing'|'won'|'lost', explodedIndex, actions }. Mines are null until the first reveal, so the first tap
 * is always safe. All functions return new objects (the input is never mutated).
 */
export function newGame(rows, cols, mines, seed) {
  if (!(mines >= 1 && mines < rows * cols)) throw new Error('bad mine count')
  return {
    rows, cols, mineCount: mines, seed: seed >>> 0, mines: null,
    state: new Array(rows * cols).fill('.'), status: 'playing', explodedIndex: -1, actions: 0,
  }
}

export const newGameForTier = (tier, seed) => {
  const c = configFor(tier)
  return newGame(c.rows, c.cols, c.mines, seed)
}

/** Test/helper constructor with an explicit mine layout. */
export function withMines(rows, cols, mineIdx, seed = 0) {
  const set = new Set(mineIdx)
  return {
    rows, cols, mineCount: set.size, seed, mines: Array.from({ length: rows * cols }, (_, i) => set.has(i)),
    state: new Array(rows * cols).fill('.'), status: 'playing', explodedIndex: -1, actions: 0,
  }
}

export const size = (g) => g.rows * g.cols
export const started = (g) => g.mines !== null
export const flagCount = (g) => g.state.filter((s) => s === 'F').length
export const minesLeft = (g) => g.mineCount - flagCount(g)
export const isRevealed = (g, i) => g.state[i] === 'R'
export const isFlagged = (g, i) => g.state[i] === 'F'
export const isMine = (g, i) => g.mines !== null && g.mines[i] === true
/** Safe squares opened (excludes mines revealed after a loss). */
export const safeOpened = (g) => g.state.reduce((n, s, i) => n + (s === 'R' && !isMine(g, i) ? 1 : 0), 0)

export function neighbors(g, i) {
  const r = Math.floor(i / g.cols)
  const c = i % g.cols
  const out = []
  for (let dr = -1; dr <= 1; dr++) {
    for (let dc = -1; dc <= 1; dc++) {
      if (dr === 0 && dc === 0) continue
      const nr = r + dr
      const nc = c + dc
      if (nr >= 0 && nr < g.rows && nc >= 0 && nc < g.cols) out.push(nr * g.cols + nc)
    }
  }
  return out
}

export const adjacentMines = (g, i) => neighbors(g, i).filter((x) => isMine(g, x)).length

export function toggleFlag(g, i) {
  if (g.status !== 'playing' || g.state[i] === 'R') return g
  const s = g.state.slice()
  s[i] = s[i] === 'F' ? '.' : 'F'
  return { ...g, state: s, actions: g.actions + 1 }
}

/** Places mines avoiding `first` and (when there is room) its neighbours, so the first tap opens an area. */
function placeMines(g, first) {
  const rng = mulberry32(g.seed)
  const prot = new Set([...neighbors(g, first), first])
  let pool = []
  for (let i = 0; i < size(g); i++) if (!prot.has(i)) pool.push(i)
  if (pool.length < g.mineCount) {
    pool = []
    for (let i = 0; i < size(g); i++) if (i !== first) pool.push(i)
  }
  const arr = new Array(size(g)).fill(false)
  shuffled(pool, rng).slice(0, g.mineCount).forEach((i) => { arr[i] = true })
  return arr
}

function reveal(g, targets) {
  const m = g.mines ?? placeMines(g, targets[0])
  const withM = { ...g, mines: m }
  const s = g.state.slice()
  let exploded = -1
  for (const t of targets) {
    if (s[t] !== '.') continue
    if (m[t]) { exploded = t; s[t] = 'R'; continue }
    const stack = [t]
    while (stack.length) {
      const cur = stack.pop()
      if (s[cur] !== '.') continue
      s[cur] = 'R'
      if (adjacentMines(withM, cur) === 0) for (const nb of neighbors(g, cur)) if (s[nb] === '.') stack.push(nb)
    }
  }
  if (exploded >= 0) {
    for (let k = 0; k < s.length; k++) if (m[k] && s[k] !== 'F') s[k] = 'R'
    return { ...withM, state: s, status: 'lost', explodedIndex: exploded, actions: g.actions + 1 }
  }
  let safeLeft = 0
  for (let k = 0; k < s.length; k++) if (!m[k] && s[k] !== 'R') safeLeft++
  if (safeLeft === 0) {
    for (let k = 0; k < s.length; k++) if (m[k]) s[k] = 'F'
    return { ...withM, state: s, status: 'won', actions: g.actions + 1 }
  }
  return { ...withM, state: s, actions: g.actions + 1 }
}

/** Opens neighbours of a revealed number once exactly that many flags touch it. */
export function chord(g, i) {
  if (g.status !== 'playing' || g.state[i] !== 'R') return g
  const n = adjacentMines(g, i)
  if (n === 0) return g
  const nb = neighbors(g, i)
  if (nb.filter((x) => g.state[x] === 'F').length !== n) return g
  const targets = nb.filter((x) => g.state[x] === '.')
  if (targets.length === 0) return g
  return reveal(g, targets)
}

/** Reveals a hidden cell, or chords on a satisfied revealed number. */
export function tap(g, i) {
  if (g.status !== 'playing') return g
  switch (g.state[i]) {
    case 'F': return g
    case 'R': return chord(g, i)
    default: return reveal(g, [i])
  }
}
