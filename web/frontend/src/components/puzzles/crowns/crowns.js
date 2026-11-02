// web/game-crowns
// Crowns engine. Pure JS (no DOM, no React) so it runs under node --test.
//
// Port of the Android CrownsEngine.kt. Rules: an N x N grid is split into N connected coloured regions; put exactly
// one crown in every row, every column and every region, with no two crowns touching (diagonals included).
// NOTE: the same seed does NOT give the same board as Android (Kotlin Random vs the mulberry32 PRNG every web engine
// uses). The algorithm, rules and difficulty bands are the same.
//
// Generation, deterministic by seed:
//  1. build a valid crown placement (one per row and column, no touching),
//  2. grow N connected regions outwards from the crown cells,
//  3. count solutions with a backtracking solver; while there is a second solution, move one cell of the alternative
//     solution into a neighbouring region (keeping the first solution intact) until the solution is unique,
//  4. rate the puzzle with a logic solver (band 1..4) and keep the candidate that best matches the wanted band.
//
// Player cell values: 0 = empty, 1 = mark (x), 2 = crown.
import { makeRng } from '../spatial/engine/rng.js'

export const MIN_N = 5
export const MAX_N = 9
const MAX_CANDIDATES = 10
const MAX_REPAIRS = 160
const DR = [-1, 0, 1, 0]
const DC = [0, 1, 0, -1]

export class CrownsPuzzle {
  /**
   * @param n size
   * @param regions region id (0..n-1) per cell, row-major
   * @param solution solution[row] = column of the crown in that row
   * @param band 1 singles, 2 pointing/confinement, 3 grouped confinement, 4 trial and error
   * @param trials trial placements the logic solver needed (0 below band 4)
   */
  constructor(n, regions, solution, band, trials) {
    this.n = n
    this.regions = regions
    this.solution = solution
    this.band = band
    this.trials = trials
  }
  regionOf(row, col) { return this.regions[row * this.n + col] }
  isSolutionCell(index) { return this.solution[Math.floor(index / this.n)] === index % this.n }
}

// ---------------------------------------------------------------- difficulty

/** Grid size for a difficulty. A plain "5".."9" (or the number) selects that size directly. */
export function sizeFor(difficulty, seed) {
  const asInt = Number.parseInt(String(difficulty).trim(), 10)
  if (String(difficulty).trim() !== '' && String(asInt) === String(difficulty).trim() && asInt >= MIN_N && asInt <= MAX_N) return asInt
  const coin = makeRng(`crowns-size:${seed}`).nextInt(2)
  switch (String(difficulty).toLowerCase()) {
    case 'easy': return 5 + coin
    case 'hard': return 8 + coin
    default: return 7
  }
}

/** [min, max] band range the generator aims for. */
export function wantedBand(difficulty, n) {
  const d = String(difficulty).trim()
  if (/^\d+$/.test(d)) return n <= 6 ? [1, 2] : n === 7 ? [2, 3] : [3, 4]
  if (d.toLowerCase() === 'easy') return [1, 2]
  if (d.toLowerCase() === 'hard') return [3, 4]
  return [2, 3]
}

export function generateFor(difficulty, seed) {
  const n = sizeFor(difficulty, seed)
  return generate(n, seed, wantedBand(difficulty, n))
}

export function generate(n, seed, wanted = [1, 4]) {
  if (!Number.isInteger(n) || n < MIN_N || n > MAX_N) throw new RangeError(`size ${n} out of range`)
  const rnd = makeRng(`crowns:${n}:${seed}`)
  let best = null
  let bestDist = Infinity
  let found = 0
  let attempts = 0
  while (found < MAX_CANDIDATES) {
    if (++attempts > 400) { if (best) break; throw new Error('crowns: generation failed') }
    const cand = buildUnique(n, rnd)
    if (!cand) continue
    found++
    const [band, trials] = rate(n, cand.regions)
    const dist = band >= wanted[0] && band <= wanted[1] ? 0 : band < wanted[0] ? wanted[0] - band : band - wanted[1]
    if (dist < bestDist) {
      bestDist = dist
      best = new CrownsPuzzle(n, cand.regions, cand.sol, band, trials)
    }
    if (dist === 0) break
  }
  return best
}

// ---------------------------------------------------------------- construction

const shuffled = (rnd, arr) => rnd.shuffle(arr.slice())

function buildUnique(n, rnd) {
  const sol = randomPlacement(n, rnd)
  const regions = grow(n, sol, rnd)
  for (let k = 0; k < MAX_REPAIRS; k++) {
    const found = solutions(n, regions, 2)
    if (found.length === 1) return { sol, regions }
    const alt = found.find((f) => !sameArray(f, sol))
    if (!alt) return { sol, regions }
    if (!repairOnce(n, regions, sol, alt, rnd)) return null
  }
  return null
}

function sameArray(a, b) {
  for (let i = 0; i < a.length; i++) if (a[i] !== b[i]) return false
  return true
}

function randomPlacement(n, rnd) {
  const cols = new Array(n).fill(-1)
  const used = new Array(n).fill(false)
  const rec = (r) => {
    if (r === n) return true
    for (const c of shuffled(rnd, [...Array(n).keys()])) {
      if (used[c]) continue
      if (r > 0 && Math.abs(cols[r - 1] - c) < 2) continue
      cols[r] = c; used[c] = true
      if (rec(r + 1)) return true
      used[c] = false; cols[r] = -1
    }
    return false
  }
  if (!rec(0)) throw new Error('crowns: no placement')
  return cols
}

function grow(n, sol, rnd) {
  const reg = new Int8Array(n * n).fill(-1)
  // ids are shuffled so a region's colour never reveals the row of its crown
  const ids = shuffled(rnd, [...Array(n).keys()])
  for (let r = 0; r < n; r++) reg[r * n + sol[r]] = ids[r]
  let assigned = n
  const cand = new Int32Array(n * n * 4)
  while (assigned < n * n) {
    let k = 0
    for (let i = 0; i < n * n; i++) {
      if (reg[i] !== -1) continue
      const r = Math.floor(i / n), c = i % n
      for (let d = 0; d < 4; d++) {
        const rr = r + DR[d], cc = c + DC[d]
        if (rr >= 0 && rr < n && cc >= 0 && cc < n && reg[rr * n + cc] !== -1) cand[k++] = i * 4 + d
      }
    }
    const pick = cand[rnd.nextInt(k)]
    const i = Math.floor(pick / 4), d = pick % 4
    reg[i] = reg[(Math.floor(i / n) + DR[d]) * n + (i % n + DC[d])]
    assigned++
  }
  return reg
}

/** Kills the alternative solution by moving one of its cells into a neighbouring region; sol stays valid. */
function repairOnce(n, reg, sol, alt, rnd) {
  const rows = shuffled(rnd, [...Array(n).keys()].filter((r) => alt[r] !== sol[r]))
  for (const r of rows) {
    const x = r * n + alt[r]
    const from = reg[x]
    const moves = []
    for (let d = 0; d < 4; d++) {
      const rr = r + DR[d], cc = alt[r] + DC[d]
      if (rr >= 0 && rr < n && cc >= 0 && cc < n && reg[rr * n + cc] !== from) moves.push(reg[rr * n + cc])
    }
    for (const to of shuffled(rnd, moves)) {
      if (stillConnectedWithout(n, reg, from, x, sol)) {
        reg[x] = to
        return true
      }
    }
  }
  return false
}

function stillConnectedWithout(n, reg, id, skip, sol) {
  let start = -1, total = 0
  for (let i = 0; i < n * n; i++) {
    if (reg[i] === id && i !== skip) { total++; if (sol[Math.floor(i / n)] === i % n) start = i }
  }
  if (start === -1) return false
  const seen = new Uint8Array(n * n)
  const stack = [start]
  seen[start] = 1
  let count = 0
  while (stack.length) {
    const i = stack.pop(); count++
    const r = Math.floor(i / n), c = i % n
    for (let d = 0; d < 4; d++) {
      const rr = r + DR[d], cc = c + DC[d]
      if (rr < 0 || rr >= n || cc < 0 || cc >= n) continue
      const j = rr * n + cc
      if (j !== skip && !seen[j] && reg[j] === id) { seen[j] = 1; stack.push(j) }
    }
  }
  return count === total
}

// ---------------------------------------------------------------- exact solver

/** Backtracking, one row at a time. Returns up to `limit` solutions (column per row). */
export function solutions(n, reg, limit) {
  const out = []
  const cols = new Array(n).fill(0)
  const rec = (r, colMask, regMask) => {
    if (r === n) { out.push(cols.slice()); return }
    for (let c = 0; c < n; c++) {
      if (out.length >= limit) return
      if (colMask & (1 << c)) continue
      if (r > 0 && Math.abs(cols[r - 1] - c) < 2) continue
      const g = reg[r * n + c]
      if (regMask & (1 << g)) continue
      cols[r] = c
      rec(r + 1, colMask | (1 << c), regMask | (1 << g))
    }
  }
  rec(0, 0, 0)
  return out
}

export const countSolutions = (n, reg, limit = 2) => solutions(n, reg, limit).length

// ---------------------------------------------------------------- rules

/** True when `cols` (column per row) satisfies every rule for the region map. */
export function isValidPlacement(n, reg, cols) {
  if (cols.length !== n) return false
  const colSeen = new Array(n).fill(false)
  const regSeen = new Array(n).fill(false)
  for (let r = 0; r < n; r++) {
    const c = cols[r]
    if (!(c >= 0 && c < n) || colSeen[c]) return false
    colSeen[c] = true
    const g = reg[r * n + c]
    if (regSeen[g]) return false
    regSeen[g] = true
    if (r > 0 && Math.abs(cols[r - 1] - c) < 2) return false
  }
  return true
}

// ---------------------------------------------------------------- logic rating

/** Returns [band, trials]. Band: 1 singles, 2 pointing, 3 grouped confinement, 4 needs trial and error. */
export function rate(n, reg) {
  const l = new Logic(n, reg)
  l.propagate()
  if (!l.bad && l.placed === n) return [l.maxLevel, 0]
  let trials = 0
  let progress = true
  while (progress && !l.bad && l.placed < n) {
    progress = false
    for (let i = 0; i < n * n; i++) {
      if (!l.alive[i]) continue
      const probe = l.copy()
      probe.place(i)
      probe.propagate()
      trials++
      if (probe.bad) {
        l.alive[i] = 0
        l.propagate()
        progress = true
        if (l.bad || l.placed === n) break
      }
    }
  }
  return [4, trials]
}

const PAIRS = [[2, 0], [2, 1], [0, 2], [1, 2], [0, 1], [1, 0]]
const popcount = (x) => { let c = 0; while (x) { x &= x - 1; c++ } return c }

/** Candidate-elimination solver used only to rate difficulty. Dimensions: 0 row, 1 column, 2 region. */
class Logic {
  constructor(n, reg) {
    this.n = n
    this.reg = reg
    this.total = n * n
    this.alive = new Uint8Array(this.total).fill(1)
    this.done = [new Uint8Array(n), new Uint8Array(n), new Uint8Array(n)]
    this.placed = 0
    this.bad = false
    this.maxLevel = 1
  }
  copy() {
    const c = new Logic(this.n, this.reg)
    c.alive = this.alive.slice()
    for (let d = 0; d < 3; d++) c.done[d] = this.done[d].slice()
    c.placed = this.placed; c.bad = this.bad; c.maxLevel = this.maxLevel
    return c
  }
  dim(d, i) { return d === 0 ? Math.floor(i / this.n) : d === 1 ? i % this.n : this.reg[i] }

  place(i) {
    const { n, reg, total, alive } = this
    if (!alive[i]) { this.bad = true; return }
    const r = Math.floor(i / n), c = i % n, g = reg[i]
    this.placed++
    this.done[0][r] = 1; this.done[1][c] = 1; this.done[2][g] = 1
    for (let j = 0; j < total; j++) {
      if (!alive[j]) continue
      const jr = Math.floor(j / n), jc = j % n
      if (jr === r || jc === c || reg[j] === g || (Math.abs(jr - r) <= 1 && Math.abs(jc - c) <= 1)) alive[j] = 0
    }
  }

  propagate() {
    while (!this.bad && this.placed < this.n) {
      if (this.singles()) continue
      if (this.bad) return
      if (this.pointing()) { this.maxLevel = Math.max(this.maxLevel, 2); continue }
      if (this.bad) return
      if (this.subsets(2, 4)) { this.maxLevel = Math.max(this.maxLevel, 3); continue }
      return
    }
  }

  singles() {
    const { n, total, alive } = this
    let any = false
    for (let d = 0; d < 3; d++) for (let k = 0; k < n; k++) {
      if (this.done[d][k]) continue
      let cnt = 0, at = -1
      for (let i = 0; i < total; i++) if (alive[i] && this.dim(d, i) === k) { cnt++; at = i }
      if (cnt === 0) { this.bad = true; return any }
      if (cnt === 1) { this.place(at); any = true; if (this.bad) return true }
    }
    return any
  }

  /** Level 2: confinement of one item to one line, and cells that touch every candidate of an item. */
  pointing() {
    const { n, total, alive } = this
    if (this.subsets(1, 1)) return true
    for (let d = 0; d < 3; d++) for (let k = 0; k < n; k++) {
      if (this.done[d][k]) continue
      const cells = []
      for (let i = 0; i < total; i++) if (alive[i] && this.dim(d, i) === k) cells.push(i)
      if (!cells.length) { this.bad = true; return false }
      let killed = false
      for (let x = 0; x < total; x++) {
        if (!alive[x] || this.dim(d, x) === k) continue
        const xr = Math.floor(x / n), xc = x % n
        if (cells.every((i) => Math.abs(Math.floor(i / n) - xr) <= 1 && Math.abs((i % n) - xc) <= 1)) { alive[x] = 0; killed = true }
      }
      if (killed) return true
    }
    return false
  }

  /**
   * k items of kind a whose candidates all sit inside k items of kind b: those k b-items belong to the a-items,
   * so every other candidate in them can go.
   */
  subsets(kMin, kMax) {
    const { n, total, alive } = this
    for (const [a, b] of PAIRS) {
      const bm = new Array(n).fill(0)
      for (let j = 0; j < total; j++) if (alive[j]) bm[this.dim(a, j)] |= 1 << this.dim(b, j)
      let itemMask = 0
      for (let k = 0; k < n; k++) if (!this.done[a][k]) itemMask |= 1 << k
      const limit = 1 << n
      for (let s = 1; s < limit; s++) {
        if (s & ~itemMask) continue
        const k = popcount(s)
        if (k < kMin || k > kMax) continue
        let union = 0
        for (let item = 0; item < n; item++) if (s & (1 << item)) union |= bm[item]
        const pc = popcount(union)
        if (pc < k) { this.bad = true; return false }
        if (pc !== k) continue
        let killed = false
        for (let j = 0; j < total; j++) {
          if (!alive[j]) continue
          if ((union & (1 << this.dim(b, j))) && !(s & (1 << this.dim(a, j)))) { alive[j] = 0; killed = true }
        }
        if (killed) return true
      }
    }
    return false
  }
}

// ---------------------------------------------------------------- player state

export class CrownsState {
  constructor(puzzle) {
    this.puzzle = puzzle
    this.n = puzzle.n
    this.cells = new Uint8Array(this.n * this.n)
    this.hintsUsed = 0
    this._undo = [] // [index, previous value]
  }
  canUndo() { return this._undo.length > 0 }
  nextValue(i) { return (this.cells[i] + 1) % 3 }

  /** empty -> mark -> crown -> empty. Returns the new value. */
  tap(i) {
    const v = this.nextValue(i)
    this._set(i, v)
    return v
  }
  _set(i, v) {
    if (this.cells[i] === v) return
    this._undo.push([i, this.cells[i]])
    this.cells[i] = v
  }
  undo() {
    if (!this._undo.length) return false
    const [i, v] = this._undo.pop()
    this.cells[i] = v
    return true
  }
  /** Sets a cell directly (keyboard Backspace/Delete); recorded for undo like a tap. */
  setCell(i, v) { this._set(i, v) }
  clear() { for (let i = 0; i < this.cells.length; i++) this._set(i, 0) }
  crownCount() { let c = 0; for (const v of this.cells) if (v === 2) c++; return c }

  /** Crowns that break a rule: same row, column or region as another crown, or touching one. */
  conflicts() {
    const { n, cells, puzzle } = this
    const bad = new Array(n * n).fill(false)
    const crowns = []
    for (let i = 0; i < cells.length; i++) if (cells[i] === 2) crowns.push(i)
    for (let a = 0; a < crowns.length; a++) for (let b = a + 1; b < crowns.length; b++) {
      const i = crowns[a], j = crowns[b]
      const ri = Math.floor(i / n), ci = i % n, rj = Math.floor(j / n), cj = j % n
      if (ri === rj || ci === cj || puzzle.regions[i] === puzzle.regions[j] || (Math.abs(ri - rj) <= 1 && Math.abs(ci - cj) <= 1)) {
        bad[i] = true; bad[j] = true
      }
    }
    return bad
  }
  hasConflicts() { return this.conflicts().some(Boolean) }
  /** Solved when n crowns stand with no conflict: that is exactly one per row, column and region. */
  isSolved() { return this.crownCount() === this.n && !this.hasConflicts() }

  /** Removes a wrong crown if there is one, otherwise places a correct crown in the smallest region still empty. */
  hint() {
    const { n, cells, puzzle } = this
    for (let i = 0; i < cells.length; i++) {
      if (cells[i] === 2 && !puzzle.isSolutionCell(i)) {
        this._set(i, 0); this.hintsUsed++
        return { cell: i, removed: true }
      }
    }
    const sizes = new Array(n).fill(0)
    for (const g of puzzle.regions) sizes[g]++
    let best = -1
    for (let r = 0; r < n; r++) {
      const i = r * n + puzzle.solution[r]
      if (cells[i] === 2) continue
      if (best === -1 || sizes[puzzle.regions[i]] < sizes[puzzle.regions[best]]) best = i
    }
    if (best === -1) return null
    this._set(best, 2); this.hintsUsed++
    return { cell: best, removed: false }
  }
  score() { return Math.max(10, this.n * 40 - this.hintsUsed * 30) }
  stars() { return this.hintsUsed === 0 ? 3 : this.hintsUsed <= 2 ? 2 : 1 }

  encode() { return `${Array.from(this.cells).join('')};${this.hintsUsed}` }
  restore(s) {
    const [body, hints] = String(s).split(';')
    if (body.length !== this.cells.length) return
    for (let i = 0; i < body.length; i++) this.cells[i] = Math.min(2, Math.max(0, Number(body[i]) || 0))
    this.hintsUsed = Number.parseInt(hints, 10) || 0
    this._undo = []
  }
}

// ---------------------------------------------------------------- presentation data (no DOM)

/**
 * Orange/sky/green/yellow/blue/vermilion/pink are colour-blind-safe (Okabe-Ito family); grey and lavender complete nine.
 * Every region has a pattern too, so the board never relies on colour alone.
 */
export const REGION_STYLES = [
  { color: '#F2B05A', colorName: 'orange', pattern: 0, patternName: 'plain' },
  { color: '#8CCBEF', colorName: 'sky blue', pattern: 1, patternName: 'diagonal stripes' },
  { color: '#6FCBA5', colorName: 'green', pattern: 2, patternName: 'dots' },
  { color: '#F3EA7A', colorName: 'yellow', pattern: 3, patternName: 'horizontal lines' },
  { color: '#6F9FDB', colorName: 'blue', pattern: 4, patternName: 'vertical lines' },
  { color: '#E88B5C', colorName: 'vermilion', pattern: 5, patternName: 'crosshatch' },
  { color: '#E3A6C9', colorName: 'pink', pattern: 6, patternName: 'back stripes' },
  { color: '#C4C4C4', colorName: 'grey', pattern: 7, patternName: 'checks' },
  { color: '#C1B5F0', colorName: 'lavender', pattern: 8, patternName: 'rings' },
]

/** Screen-reader label of one cell: row, column, region colour, pattern, state. */
export function cellLabel(puzzle, r, c, value, conflict) {
  const style = REGION_STYLES[puzzle.regionOf(r, c) % REGION_STYLES.length]
  const state = value === 2 ? 'crown' : value === 1 ? 'marked empty' : 'empty'
  return `Row ${r + 1}, column ${c + 1}, ${style.colorName} region, ${style.patternName} pattern, ${state}${conflict ? ', conflict' : ''}`
}

/**
 * Cell edge in px for a board that may use `availableWidth` px. Never below the 44 px touch-target minimum:
 * when N x 44 is wider than the screen (9x9 on a 360 px phone) the board scrolls sideways instead of shrinking.
 */
export function cellSizePx(n, availableWidth) {
  return Math.max(44, Math.min(64, Math.floor(availableWidth / n)))
}

/** Next focused cell index for an arrow/Home/End key; clamps at the edges. */
export function moveFocus(n, index, key) {
  const r = Math.floor(index / n), c = index % n
  switch (key) {
    case 'ArrowUp': return Math.max(0, r - 1) * n + c
    case 'ArrowDown': return Math.min(n - 1, r + 1) * n + c
    case 'ArrowLeft': return r * n + Math.max(0, c - 1)
    case 'ArrowRight': return r * n + Math.min(n - 1, c + 1)
    case 'Home': return r * n
    case 'End': return r * n + n - 1
    default: return index
  }
}
