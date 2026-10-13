// --- web spatial puzzles (feat/web-puzzles-spatial) ---
// Pixel Logic (nonogram) engine. Port of Android NonogramEngine.kt.
// Player cell values: 0 unknown, 1 filled, 2 marked empty (X). Solution cells: 1 filled, 0 empty.
import { makeRng } from './rng.js'

export function sizeFor(difficulty) {
  const d = String(difficulty).toLowerCase()
  return d === 'easy' ? 5 : d === 'hard' ? 15 : 10
}

export function cluesOf(line) {
  const out = []
  let run = 0
  for (const b of line) {
    if (b) run++
    else if (run > 0) {
      out.push(run)
      run = 0
    }
  }
  if (run > 0) out.push(run)
  return out.length ? out : [0]
}

export function cluesFor(size, sol) {
  const rows = []
  const cols = []
  for (let r = 0; r < size; r++) rows.push(cluesOf(Array.from({ length: size }, (_, c) => !!sol[r * size + c])))
  for (let c = 0; c < size; c++) cols.push(cluesOf(Array.from({ length: size }, (_, r) => !!sol[r * size + c])))
  return [rows, cols]
}

/**
 * Line solver. `known` has -1 unknown, 0 empty, 1 filled. Returns the tightened line
 * (same encoding) or null if the line is contradictory.
 */
export function solveLine(clues, known) {
  const n = known.length
  const blocks = clues.length === 1 && clues[0] === 0 ? [] : clues
  const k = blocks.length
  const ok = Array.from({ length: n + 2 }, () => new Uint8Array(k + 1))
  for (let pos = n; pos >= 0; pos--) {
    for (let j = k; j >= 0; j--) {
      if (pos >= n) {
        ok[pos][j] = j === k ? 1 : 0
        continue
      }
      let res = false
      if (known[pos] !== 1 && ok[pos + 1][j]) res = true
      if (!res && j < k) {
        const len = blocks[j]
        if (pos + len <= n) {
          let fits = true
          for (let t = pos; t < pos + len; t++) {
            if (known[t] === 0) {
              fits = false
              break
            }
          }
          if (fits) {
            if (pos + len === n) res = !!ok[n][j + 1]
            else if (known[pos + len] !== 1) res = !!ok[pos + len + 1][j + 1]
          }
        }
      }
      ok[pos][j] = res ? 1 : 0
    }
  }
  if (!ok[0][0]) return null
  const reach = Array.from({ length: n + 2 }, () => new Uint8Array(k + 1))
  const canFill = new Uint8Array(n)
  const canEmpty = new Uint8Array(n)
  reach[0][0] = 1
  for (let pos = 0; pos < n; pos++) {
    for (let j = 0; j <= k; j++) {
      if (!reach[pos][j]) continue
      if (known[pos] !== 1 && ok[pos + 1][j]) {
        canEmpty[pos] = 1
        reach[pos + 1][j] = 1
      }
      if (j < k) {
        const len = blocks[j]
        if (pos + len <= n) {
          let fits = true
          for (let t = pos; t < pos + len; t++) {
            if (known[t] === 0) {
              fits = false
              break
            }
          }
          if (fits) {
            if (pos + len === n) {
              if (ok[n][j + 1]) {
                for (let t = pos; t < n; t++) canFill[t] = 1
                reach[n][j + 1] = 1
              }
            } else if (known[pos + len] !== 1 && ok[pos + len + 1][j + 1]) {
              for (let t = pos; t < pos + len; t++) canFill[t] = 1
              canEmpty[pos + len] = 1
              reach[pos + len + 1][j + 1] = 1
            }
          }
        }
      }
    }
  }
  const out = new Array(n)
  for (let i = 0; i < n; i++) {
    out[i] = canFill[i] && !canEmpty[i] ? 1 : canEmpty[i] && !canFill[i] ? 0 : -1
  }
  return out
}

/** Propagates line logic to a fixed point. Returns the grid (-1/0/1) or null on contradiction. */
export function propagate(size, rows, cols, start = null) {
  const g = start ? Int8Array.from(start) : new Int8Array(size * size).fill(-1)
  let changed = true
  while (changed) {
    changed = false
    for (let r = 0; r < size; r++) {
      const line = Array.from({ length: size }, (_, c) => g[r * size + c])
      const res = solveLine(rows[r], line)
      if (!res) return null
      for (let c = 0; c < size; c++) {
        if (res[c] !== line[c]) {
          if (line[c] !== -1) return null
          g[r * size + c] = res[c]
          changed = true
        }
      }
    }
    for (let c = 0; c < size; c++) {
      const line = Array.from({ length: size }, (_, r) => g[r * size + c])
      const res = solveLine(cols[c], line)
      if (!res) return null
      for (let r = 0; r < size; r++) {
        if (res[r] !== line[r]) {
          if (line[r] !== -1) return null
          g[r * size + c] = res[r]
          changed = true
        }
      }
    }
  }
  return g
}

/** True when line logic alone fully determines the grid (which implies uniqueness). */
export function solvableByLogic(p) {
  const g = propagate(p.size, p.rowClues, p.colClues)
  return !!g && g.every((v) => v !== -1)
}

/** Counts solutions (up to `limit`) by propagation + branching. Independent uniqueness check. */
export function countSolutions(size, rows, cols, limit = 2) {
  const rec = (state) => {
    const g = propagate(size, rows, cols, state)
    if (!g) return 0
    const idx = g.indexOf(-1)
    if (idx === -1) return 1
    let total = 0
    for (let v = 0; v <= 1; v++) {
      const next = Int8Array.from(g)
      next[idx] = v
      total += rec(next)
      if (total >= limit) return total
    }
    return total
  }
  return rec(new Int8Array(size * size).fill(-1))
}

function makePuzzle(size, sol) {
  const [rowClues, colClues] = cluesFor(size, sol)
  return { size, solution: Array.from(sol, (v) => !!v), rowClues, colClues }
}

export function generate(difficulty, seed) {
  const size = sizeFor(difficulty)
  const rnd = makeRng(seed)
  let best = null
  for (let attempt = 0; attempt < 400; attempt++) {
    const density = 0.5 + rnd.nextDouble() * 0.15
    const sol = Array.from({ length: size * size }, () => rnd.nextDouble() < density)
    if (size >= 10) {
      for (let i = 0; i < size * size; i++) {
        const r = Math.floor(i / size)
        const c = i % size
        if (r > 0 && c > 0 && rnd.nextDouble() < 0.35) sol[i] = sol[(r - 1) * size + c]
      }
    }
    if (sol.filter(Boolean).length < (size * size) / 4) continue
    const p = makePuzzle(size, sol)
    if (solvableByLogic(p)) return p
    if (!best) best = p
  }
  // Fallback (extremely unlikely): add filled cells until line logic solves it.
  let p = best
  const sol = p.solution.slice()
  for (let step = 0; step < size * size; step++) {
    const g = propagate(size, p.rowClues, p.colClues)
    if (!g) break
    const unk = []
    g.forEach((v, i) => v === -1 && unk.push(i))
    if (!unk.length) return p
    sol[unk[rnd.nextInt(unk.length)]] = true
    p = makePuzzle(size, sol)
  }
  // Last resort: a banded picture is always logic-solvable.
  const banded = Array.from({ length: size * size }, (_, i) => Math.floor(i / size) % 2 === 0 || i % size === 0)
  return makePuzzle(size, banded)
}

/** Mutable play state with undo, strokes and hints. */
export class NonogramState {
  constructor(puzzle) {
    this.puzzle = puzzle
    this.size = puzzle.size
    this.cells = new Int8Array(this.size * this.size)
    this.history = []
    this.hintsUsed = 0
    this.undoCount = 0
    this.strokeRec = null
  }

  canUndo() {
    return this.history.length > 0
  }

  applyStroke(indices, value) {
    const rec = []
    for (const i of indices) {
      if (this.cells[i] !== value) {
        rec.push(i, this.cells[i])
        this.cells[i] = value
      }
    }
    if (rec.length) this.history.push(rec)
  }

  beginStroke() {
    this.strokeRec = []
  }

  paint(i, value) {
    if (!this.strokeRec) return
    if (this.cells[i] !== value) {
      this.strokeRec.push(i, this.cells[i])
      this.cells[i] = value
    }
  }

  endStroke() {
    const rec = this.strokeRec
    this.strokeRec = null
    if (rec && rec.length) this.history.push(rec)
  }

  cancelStroke() {
    const rec = this.strokeRec
    this.strokeRec = null
    if (!rec) return
    for (let i = rec.length - 2; i >= 0; i -= 2) this.cells[rec[i]] = rec[i + 1]
  }

  undo() {
    if (!this.history.length) return false
    const rec = this.history.pop()
    for (let i = rec.length - 2; i >= 0; i -= 2) this.cells[rec[i]] = rec[i + 1]
    this.undoCount++
    return true
  }

  clear() {
    if (this.cells.every((v) => v === 0)) return
    this.applyStroke(Array.from(this.cells, (_, i) => i), 0)
  }

  filledMatches() {
    for (let i = 0; i < this.cells.length; i++) if ((this.cells[i] === 1) !== this.puzzle.solution[i]) return false
    return true
  }

  isRowSatisfied(r) {
    const line = Array.from({ length: this.size }, (_, c) => this.cells[r * this.size + c] === 1)
    return sameClues(cluesOf(line), this.puzzle.rowClues[r])
  }

  isColSatisfied(c) {
    const line = Array.from({ length: this.size }, (_, r) => this.cells[r * this.size + c] === 1)
    return sameClues(cluesOf(line), this.puzzle.colClues[c])
  }

  /** Indices the player filled that are not part of the solution. */
  mistakes() {
    const out = []
    for (let i = 0; i < this.cells.length; i++) if (this.cells[i] === 1 && !this.puzzle.solution[i]) out.push(i)
    return out
  }

  /** Fixes the first mistake (marks it X) or reveals a missing solution cell. Returns the index or -1. */
  hint() {
    let bad = -1
    for (let i = 0; i < this.cells.length; i++) {
      if (this.cells[i] === 1 && !this.puzzle.solution[i]) {
        bad = i
        break
      }
    }
    let target = bad
    if (target < 0) {
      for (let i = 0; i < this.cells.length; i++) {
        if (this.puzzle.solution[i] && this.cells[i] !== 1) {
          target = i
          break
        }
      }
    }
    if (target < 0) return -1
    this.applyStroke([target], bad >= 0 ? 2 : 1)
    this.hintsUsed++
    return target
  }

  score() {
    const base = this.size === 5 ? 60 : this.size === 10 ? 100 : 150
    return Math.max(base - this.hintsUsed * 15, Math.floor(base / 4))
  }

  stars() {
    return this.hintsUsed === 0 ? 3 : this.hintsUsed <= 2 ? 2 : 1
  }
}

function sameClues(a, b) {
  return a.length === b.length && a.every((v, i) => v === b[i])
}
