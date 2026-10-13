// --- web spatial puzzles (feat/web-puzzles-spatial) ---
// Conduit (rotate-pipe) engine. Port of Android ConduitEngine.kt.
// Masks: N=1, E=2, S=4, W=8. A level is a random spanning tree rooted at the source, so a solution
// always exists; tiles are then scrambled by random rotations.
import { makeRng } from './rng.js'

export const N = 1
export const E = 2
export const S = 4
export const W = 8
const DR = [-1, 0, 1, 0]
const DC = [0, 1, 0, -1]
const BIT = [N, E, S, W]

export function sizeFor(difficulty) {
  const d = String(difficulty).toLowerCase()
  return d === 'easy' ? 4 : d === 'hard' ? 7 : 5
}

export const rotCW = (m) => ((m << 1) | (m >> 3)) & 15

/** Clockwise taps to turn `from` into `to`, or 0 if not reachable (different shape). */
export function tapsToReach(from, to) {
  let m = from
  for (let t = 0; t <= 3; t++) {
    if (m === to) return t
    m = rotCW(m)
  }
  return 0
}

export const opposite = (d) => (d + 2) % 4

export function neighbour(n, i, d) {
  const r = Math.floor(i / n) + DR[d]
  const c = (i % n) + DC[d]
  return r < 0 || c < 0 || r >= n || c >= n ? -1 : r * n + c
}

function makePuzzle(n, source, solution, start) {
  let par = 0
  for (let i = 0; i < start.length; i++) par += tapsToReach(start[i], solution[i])
  return { n, source, solution, start, par }
}

export function generate(difficulty, seed) {
  const n = sizeFor(difficulty)
  const rnd = makeRng(seed)
  for (let attempt = 0; attempt < 50; attempt++) {
    const source = Math.floor(n / 2) * n + Math.floor(n / 2)
    const sol = new Array(n * n).fill(0)
    const inTree = new Array(n * n).fill(false)
    inTree[source] = true
    const frontier = []
    const addFrontier = (i) => {
      for (let d = 0; d < 4; d++) {
        const nb = neighbour(n, i, d)
        if (nb >= 0 && !inTree[nb]) frontier.push([i, d])
      }
    }
    addFrontier(source)
    while (frontier.length) {
      const k = rnd.nextInt(frontier.length)
      const f = frontier[k]
      frontier[k] = frontier[frontier.length - 1]
      frontier.pop()
      const to = neighbour(n, f[0], f[1])
      if (to < 0 || inTree[to]) continue
      inTree[to] = true
      sol[f[0]] |= BIT[f[1]]
      sol[to] |= BIT[opposite(f[1])]
      addFrontier(to)
    }
    const start = sol.map((m0) => {
      let m = m0
      const t = rnd.nextInt(4)
      for (let i = 0; i < t; i++) m = rotCW(m)
      return m
    })
    const p = makePuzzle(n, source, sol, start)
    if (!isSolved(n, source, start) && p.par >= Math.floor((n * n) / 3)) return p
  }
  // Deterministic fallback: a comb tree with every tile rotated at least once.
  const sol = new Array(n * n).fill(0)
  for (let r = 0; r < n; r++) {
    for (let c = 0; c + 1 < n; c++) {
      const i = r * n + c
      sol[i] |= E
      sol[i + 1] |= W
    }
  }
  for (let r = 0; r < n - 1; r++) {
    const i = r * n
    sol[i] |= S
    sol[i + n] |= N
  }
  const rnd2 = makeRng(seed)
  const start = sol.map((m0) => {
    let m = m0
    const t = 1 + rnd2.nextInt(3)
    for (let i = 0; i < t; i++) m = rotCW(m)
    return m
  })
  return makePuzzle(n, 0, sol, start)
}

/** Powered cells: flood from the source across matched connections. */
export function powered(n, source, masks) {
  const on = new Array(n * n).fill(false)
  const stack = [source]
  on[source] = true
  while (stack.length) {
    const c = stack.pop()
    for (let d = 0; d < 4; d++) {
      if (!(masks[c] & BIT[d])) continue
      const nb = neighbour(n, c, d)
      if (nb < 0 || on[nb]) continue
      if (!(masks[nb] & BIT[opposite(d)])) continue
      on[nb] = true
      stack.push(nb)
    }
  }
  return on
}

/** No pipe end points at a wall or at a tile that does not connect back. */
export function noLeaks(n, masks) {
  for (let i = 0; i < masks.length; i++) {
    for (let d = 0; d < 4; d++) {
      if (!(masks[i] & BIT[d])) continue
      const nb = neighbour(n, i, d)
      if (nb < 0 || !(masks[nb] & BIT[opposite(d)])) return false
    }
  }
  return true
}

export const isSolved = (n, source, masks) => noLeaks(n, masks) && powered(n, source, masks).every(Boolean)

/**
 * Backtracking solver for verification/tests: finds any rotation assignment (final masks) that is
 * leak-free and fully powered, or null.
 */
export function solve(n, source, start) {
  const cur = new Array(n * n).fill(0)
  const options = (m) => {
    const out = []
    let x = m
    for (let i = 0; i < 4; i++) {
      if (!out.includes(x)) out.push(x)
      x = rotCW(x)
    }
    return out
  }
  const rec = (i) => {
    if (i === n * n) return powered(n, source, cur).every(Boolean)
    const r = Math.floor(i / n)
    const c = i % n
    for (const m of options(start[i])) {
      if (r === 0 && m & N) continue
      if (c === 0 && m & W) continue
      if (r === n - 1 && m & S) continue
      if (c === n - 1 && m & E) continue
      if (r > 0 && !!(cur[i - n] & S) !== !!(m & N)) continue
      if (c > 0 && !!(cur[i - 1] & E) !== !!(m & W)) continue
      cur[i] = m
      if (rec(i + 1)) return true
    }
    return false
  }
  return rec(0) ? cur.slice() : null
}

export class ConduitState {
  constructor(puzzle) {
    this.puzzle = puzzle
    this.n = puzzle.n
    this.hintsUsed = 0
    this.restart()
  }

  restart() {
    this.masks = this.puzzle.start.slice()
    this.moves = 0
    this.undoStack = []
  }

  canUndo() {
    return this.undoStack.length > 0
  }

  rotate(i) {
    this.masks[i] = rotCW(this.masks[i])
    this.moves++
    this.undoStack.push(i)
  }

  undo() {
    if (!this.undoStack.length) return false
    const i = this.undoStack.pop()
    for (let k = 0; k < 3; k++) this.masks[i] = rotCW(this.masks[i])
    if (this.moves > 0) this.moves--
    return true
  }

  solved() {
    return isSolved(this.n, this.puzzle.source, this.masks)
  }

  /** A tile that differs from the generator's solution, or -1. */
  hint() {
    const i = this.masks.findIndex((m, k) => m !== this.puzzle.solution[k])
    if (i >= 0) this.hintsUsed++
    return i
  }

  stars() {
    const par = this.puzzle.par
    return this.moves <= par + Math.floor(par / 5) ? 3 : this.moves <= par * 2 ? 2 : 1
  }

  score() {
    return Math.max(40 + this.stars() * 20 - this.hintsUsed * 8, 10)
  }
}
