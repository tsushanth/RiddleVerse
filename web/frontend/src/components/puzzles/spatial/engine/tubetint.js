// --- web spatial puzzles (feat/web-puzzles-spatial) ---
// Tube Tint (liquid sort) engine. Port of Android TubeTintEngine.kt.
// A tube is an array of colour ids, bottom to top, with capacity CAPACITY.
import { makeRng } from './rng.js'

export const CAPACITY = 4

/** [colours, empty tubes] */
export function configFor(difficulty) {
  const d = String(difficulty).toLowerCase()
  return d === 'easy' ? [5, 2] : d === 'hard' ? [11, 2] : [8, 2]
}

export function topRun(t) {
  if (!t.length) return 0
  const c = t[t.length - 1]
  let n = 0
  for (let i = t.length - 1; i >= 0 && t[i] === c; i--) n++
  return n
}

export function canPour(tubes, from, to, capacity = CAPACITY) {
  if (from === to) return false
  const a = tubes[from]
  const b = tubes[to]
  if (!a.length || b.length >= capacity) return false
  if (b.length && b[b.length - 1] !== a[a.length - 1]) return false
  // Pointless: pouring a uniform tube into an empty tube just relocates it.
  if (!b.length && a.every((x) => x === a[0])) return false
  return true
}

/** Executes the pour in place; returns the number of units moved (0 if illegal). */
export function pour(tubes, from, to, capacity = CAPACITY) {
  if (!canPour(tubes, from, to, capacity)) return 0
  const a = tubes[from]
  const b = tubes[to]
  const n = Math.min(topRun(a), capacity - b.length)
  const c = a[a.length - 1]
  for (let i = 0; i < n; i++) {
    a.pop()
    b.push(c)
  }
  return n
}

export const isTubeDone = (t, capacity = CAPACITY) => t.length === capacity && t.every((x) => x === t[0])
export const isSolved = (tubes, capacity = CAPACITY) => tubes.every((t) => t.length === 0 || isTubeDone(t, capacity))

function key(tubes) {
  return tubes.map((t) => t.join(',')).sort().join('|')
}

/**
 * DFS solver with a visited set and node cap. Returns the move list [[from,to],...] or null.
 * Deterministic.
 */
export function solve(start, capacity = CAPACITY, maxNodes = 250000) {
  const visited = new Set()
  let nodes = 0
  const path = []
  const cur = start.map((t) => Array.from(t))

  const score = (a, b) => {
    const src = cur[a]
    const dst = cur[b]
    let s = 0
    if (dst.length) s += 10
    if (dst.length && dst.every((x) => x === dst[0])) s += 5
    const n = Math.min(topRun(src), capacity - dst.length)
    if (n === src.length) s += 4
    return s + n
  }

  const dfs = () => {
    if (isSolved(cur, capacity)) return true
    if (++nodes > maxNodes) return false
    const k = key(cur)
    if (visited.has(k)) return false
    visited.add(k)
    const moves = []
    for (let a = 0; a < cur.length; a++) {
      for (let b = 0; b < cur.length; b++) {
        if (canPour(cur, a, b, capacity)) moves.push([a, b, score(a, b)])
      }
    }
    moves.sort((x, y) => y[2] - x[2]) // stable: ties keep generation order
    for (const m of moves) {
      const a = cur[m[0]]
      const b = cur[m[1]]
      const aBefore = a.slice()
      const bBefore = b.slice()
      pour(cur, m[0], m[1], capacity)
      path.push([m[0], m[1]])
      if (dfs()) return true
      path.pop()
      a.length = 0
      a.push(...aBefore)
      b.length = 0
      b.push(...bBefore)
      if (nodes > maxNodes) return false
    }
    return false
  }
  return dfs() ? path : null
}

/**
 * Generates a puzzle from the solved layout by random REVERSE unit moves (the inverse of a legal
 * unit pour), then keeps only layouts the solver can finish.
 */
export function generate(difficulty, seed) {
  const [colors, empties] = configFor(difficulty)
  const cap = CAPACITY
  const rnd = makeRng(seed)
  for (let attempt = 0; attempt < 60; attempt++) {
    const tubes = []
    for (let c = 0; c < colors; c++) tubes.push(new Array(cap).fill(c))
    for (let i = 0; i < empties; i++) tubes.push([])
    const steps = colors * cap * 6
    let done = 0
    let guard = 0
    while (done < steps && guard < steps * 20) {
      guard++
      const b = rnd.nextInt(tubes.length)
      const tb = tubes[b]
      if (!tb.length) continue
      const c = tb[tb.length - 1]
      if (tb.length >= 2 && tb[tb.length - 2] !== c) continue
      const a = rnd.nextInt(tubes.length)
      if (a === b || tubes[a].length >= cap) continue
      tb.pop()
      tubes[a].push(c)
      done++
    }
    rnd.shuffle(tubes)
    if (isSolved(tubes, cap)) continue
    if (tubes.filter((t) => isTubeDone(t, cap)).length > Math.floor(colors / 2)) continue
    if (solve(tubes, cap, 60000)) return { colors, tubes: tubes.map((t) => t.slice()), capacity: cap }
  }
  const tubes = []
  for (let c = 0; c < colors; c++) tubes.push(new Array(cap).fill(c))
  tubes[0][cap - 1] = 1
  tubes[1][cap - 1] = 0
  for (let i = 0; i < empties; i++) tubes.push([])
  return { colors, tubes, capacity: cap }
}

/** Play state with undo, restart and a single optional extra tube. */
export class TubeTintState {
  constructor(puzzle) {
    this.puzzle = puzzle
    this.capacity = puzzle.capacity
    this.tubes = puzzle.tubes.map((t) => t.slice())
    this.undoStack = []
    this.moves = 0
    this.extraTubeAdded = false
    this.hintsUsed = 0
    this.undoCount = 0
  }

  // Snapshot includes moves and the extra-tube flag so undoing "add tube" restores both
  // (the Android port leaves the flag set and miscounts moves).
  snapshot() {
    return { tubes: this.tubes.map((t) => t.slice()), moves: this.moves, extra: this.extraTubeAdded }
  }

  canUndo() {
    return this.undoStack.length > 0
  }

  pour(from, to) {
    if (!canPour(this.tubes, from, to, this.capacity)) return false
    this.undoStack.push(this.snapshot())
    pour(this.tubes, from, to, this.capacity)
    this.moves++
    return true
  }

  undo() {
    if (!this.undoStack.length) return false
    const snap = this.undoStack.pop()
    this.tubes = snap.tubes
    this.moves = snap.moves
    this.extraTubeAdded = snap.extra
    this.undoCount++
    return true
  }

  addTube() {
    if (this.extraTubeAdded) return false
    this.undoStack.push(this.snapshot())
    this.tubes.push([])
    this.extraTubeAdded = true
    return true
  }

  restart() {
    this.tubes = this.puzzle.tubes.map((t) => t.slice())
    this.undoStack = []
    this.moves = 0
    this.extraTubeAdded = false
  }

  solved() {
    return isSolved(this.tubes, this.capacity)
  }

  /** Next pour suggested by the solver as [from,to], or null if it cannot finish from here. */
  hint() {
    const path = solve(this.tubes, this.capacity, 120000)
    if (!path) return null
    this.hintsUsed++
    return path.length ? path[0] : null
  }

  score() {
    const c = this.puzzle.colors
    const base = 40 + c * 8
    const s = base - this.hintsUsed * 10 - (this.extraTubeAdded ? 10 : 0) - Math.floor(Math.max(moveExcess(this.moves, c), 0) / 2)
    return Math.max(s, 10)
  }

  stars() {
    return this.hintsUsed === 0 && !this.extraTubeAdded ? 3 : this.hintsUsed <= 1 ? 2 : 1
  }
}

function moveExcess(moves, colors) {
  return moves - colors * 3
}
