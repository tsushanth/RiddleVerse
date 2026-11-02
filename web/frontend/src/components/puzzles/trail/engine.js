// --- web/game-trail ---
// Trail: draw ONE continuous line from dot 1 through the numbered dots in order, visiting every cell exactly once.
// Optional walls sit between neighbouring cells and the line can never cross them. Pure JS, no DOM, deterministic by seed.
// Every generated puzzle has exactly one solution, verified by the solver below (counts solutions up to 2).
//
// Port of the Android TrailEngine.kt (generator via backbite Hamiltonian path + uniqueness solver). The algorithm is the
// same but the PRNG is not (mulberry32 here, kotlin.random.Random there), so a given seed does NOT produce the same board
// on web and Android. The contract is what matches: valid, unique, easy has no walls, harder bands are harder.
//
// The solver's visited set is a 64-bit mask (8x8 = 64 cells) held as two int32 halves: lo = cells 0..31, hi = cells 32..63.
import { makeRng } from '../spatial/engine/rng.js'

export const MIN_N = 5
export const MAX_N = 8

/** Per-solve and per-puzzle search budgets. Counted in nodes (not time) so generation stays deterministic. */
const SOLVE_NODE_LIMIT = 30000
const REDUCE_NODE_BUDGET = 60000
const WALL_TRIES = 40
const WALL_NODE_BUDGET = 60000

export class TrailPuzzle {
  /**
   * @param n side length
   * @param waypoints cell index of dot k+1 (waypoints[0] is where the line starts, the last entry is where it ends)
   * @param wallE wallE[i] = wall between cell i and the cell to its right
   * @param wallS wallS[i] = wall between cell i and the cell below it
   * @param solution the unique solution: every cell, in visiting order
   * @param band 0 easy, 1 medium, 2 hard
   * @param nodes solver search nodes needed to prove the solution unique (a branching measure of difficulty)
   */
  constructor(n, waypoints, wallE, wallS, solution, band, nodes) {
    this.n = n
    this.waypoints = waypoints
    this.wallE = wallE
    this.wallS = wallS
    this.solution = solution
    this.band = band
    this.nodes = nodes
    this.cells = n * n
    let w = 0
    for (let i = 0; i < this.cells; i++) w += (wallE[i] ? 1 : 0) + (wallS[i] ? 1 : 0)
    this.wallCount = w
    this._dotAt = new Int8Array(this.cells).fill(-1)
    waypoints.forEach((c, k) => { this._dotAt[c] = k })
  }

  /** 0-based index of the dot on `cell`, or -1 */
  dotAt(cell) { return this._dotAt[cell] }

  adjacent(a, b) {
    const n = this.n
    const d = Math.abs(a - b)
    return d === n || (d === 1 && Math.floor(a / n) === Math.floor(b / n))
  }

  /** true when the line may NOT go between neighbouring cells a and b (wall, or not neighbours at all) */
  blocked(a, b) {
    const m = this.cells
    if (a < 0 || b < 0 || a >= m || b >= m || !this.adjacent(a, b)) return true
    switch (b - a) {
      case 1: return !!this.wallE[a]
      case -1: return !!this.wallE[b]
      case this.n: return !!this.wallS[a]
      default: return !!this.wallS[b]
    }
  }

  /** Independent validity check (used by tests and by the generator's self-check). */
  isValidSolution(path) {
    const m = this.cells
    if (path.length !== m) return false
    const seen = new Uint8Array(m)
    let nextDot = 0
    for (let i = 0; i < m; i++) {
      const c = path[i]
      if (c < 0 || c >= m || seen[c]) return false
      seen[c] = 1
      if (i > 0 && this.blocked(path[i - 1], c)) return false
      const d = this._dotAt[c]
      if (d >= 0) { if (d !== nextDot) return false; nextDot++ }
    }
    return nextDot === this.waypoints.length && path[0] === this.waypoints[0] && path[m - 1] === this.waypoints[this.waypoints.length - 1]
  }
}

// ---------------------------------------------------------------------------------------------------------------------
// Solver
// ---------------------------------------------------------------------------------------------------------------------

/**
 * Counts solutions (up to `limit`) with a depth-first search over 64-bit cell masks (two int32 halves).
 * Pruning: dead-end cells, connectivity of the unvisited region, the next dot being reachable without passing another
 * dot, and the final dot only being enterable last.
 */
class Solver {
  constructor(n, wp, wallE, wallS, limit, nodeLimit) {
    this.n = n
    this.wp = wp
    this.limit = limit
    this.nodeLimit = nodeLimit
    const m = (this.m = n * n)
    // all-cells mask
    this.allLo = m >= 32 ? -1 : (1 << m) - 1
    this.allHi = m <= 32 ? 0 : m >= 64 ? -1 : (1 << (m - 32)) - 1
    this.nbrLo = new Int32Array(m); this.nbrHi = new Int32Array(m)
    // can[X] = cells that have an open edge in direction X
    this.cEl = 0; this.cEh = 0; this.cWl = 0; this.cWh = 0; this.cSl = 0; this.cSh = 0; this.cNl = 0; this.cNh = 0
    const setBit = (c, which) => {
      const lo = c < 32 ? 1 << c : 0
      const hi = c < 32 ? 0 : 1 << (c - 32)
      this[which + 'l'] |= lo
      this[which + 'h'] |= hi
    }
    const link = (a, b) => {
      if (b < 32) this.nbrLo[a] |= 1 << b; else this.nbrHi[a] |= 1 << (b - 32)
      if (a < 32) this.nbrLo[b] |= 1 << a; else this.nbrHi[b] |= 1 << (a - 32)
    }
    for (let c = 0; c < m; c++) {
      const col = c % n
      if (col + 1 < n && !wallE[c]) { setBit(c, 'cE'); setBit(c + 1, 'cW'); link(c, c + 1) }
      if (c + n < m && !wallS[c]) { setBit(c, 'cS'); setBit(c + n, 'cN'); link(c, c + n) }
    }
    this.wpAt = new Int8Array(m).fill(-1)
    this.wpLo = 0; this.wpHi = 0
    wp.forEach((c, k) => {
      this.wpAt[c] = k
      if (c < 32) this.wpLo |= 1 << c; else this.wpHi |= 1 << (c - 32)
    })
    this.endCell = wp[wp.length - 1]
    this.endLo = this.endCell < 32 ? 1 << this.endCell : 0
    this.endHi = this.endCell < 32 ? 0 : 1 << (this.endCell - 32)
    this.path = new Int32Array(m)
    this.found = []
    this.count = 0
    this.nodes = 0
    this.aborted = false
    this.sl = 0; this.sh = 0 // scratch result of spread()
  }

  run() {
    const s = this.wp[0]
    this.dfs(s, s < 32 ? 1 << s : 0, s < 32 ? 0 : 1 << (s - 32), 1, 1)
    return { count: this.count, nodes: this.nodes, aborted: this.aborted, solutions: this.found }
  }

  /** One flood-fill step from region r, kept inside mask ml/mh. Result in this.sl / this.sh. */
  spread(rl, rh, ml, mh) {
    const n = this.n
    const el = rl & this.cEl, eh = rh & this.cEh // move east: << 1
    const wl = rl & this.cWl, wh = rh & this.cWh // west: >>> 1
    const sl = rl & this.cSl, sh = rh & this.cSh // south: << n
    const nl = rl & this.cNl, nh = rh & this.cNh // north: >>> n
    const lo = rl | (el << 1) | ((wl >>> 1) | (wh << 31)) | (sl << n) | ((nl >>> n) | (nh << (32 - n)))
    const hi = rh | ((eh << 1) | (el >>> 31)) | (wh >>> 1) | ((sh << n) | (sl >>> (32 - n))) | (nh >>> n)
    this.sl = lo & ml
    this.sh = hi & mh
  }

  feasible(cur, visLo, visHi, k) {
    const n = this.n
    const ul = this.allLo & ~visLo
    const uh = this.allHi & ~visHi
    if ((ul | uh) === 0) return true
    const al = ul | (cur < 32 ? 1 << cur : 0)
    const ah = uh | (cur < 32 ? 0 : 1 << (cur - 32))
    // for each cell: which of its four directions lead to an available cell
    const a0l = ((al >>> 1) | (ah << 31)) & this.cEl, a0h = (ah >>> 1) & this.cEh
    const a1l = (al << 1) & this.cWl, a1h = ((ah << 1) | (al >>> 31)) & this.cWh
    const a2l = ((al >>> n) | (ah << (32 - n))) & this.cSl, a2h = (ah >>> n) & this.cSh
    const a3l = (al << n) & this.cNl, a3h = ((ah << n) | (al >>> (32 - n))) & this.cNh
    // twos = cells with at least two available neighbours (bitwise half adder chain)
    let onesL = a0l, twosL = 0
    twosL |= onesL & a1l; onesL ^= a1l
    twosL |= onesL & a2l; onesL ^= a2l
    twosL |= onesL & a3l
    let onesH = a0h, twosH = 0
    twosH |= onesH & a1h; onesH ^= a1h
    twosH |= onesH & a2h; onesH ^= a2h
    twosH |= onesH & a3h
    const anyL = a0l | a1l | a2l | a3l
    const anyH = a0h | a1h | a2h | a3h
    // every unvisited cell needs two usable neighbours (in and out); only the final dot may be a dead end
    if (((ul & ~twosL & ~this.endLo) | (uh & ~twosH & ~this.endHi)) !== 0) return false
    if (((ul & this.endLo) | (uh & this.endHi)) !== 0 && ((anyL & this.endLo) | (anyH & this.endHi)) === 0) return false
    // unvisited region must be one connected piece reachable from here
    let rl = this.nbrLo[cur] & ul, rh = this.nbrHi[cur] & uh
    if ((rl | rh) === 0) return false
    for (;;) {
      this.spread(rl, rh, ul, uh)
      if (this.sl === rl && this.sh === rh) break
      rl = this.sl; rh = this.sh
    }
    if (rl !== ul || rh !== uh) return false
    // the next dot must be reachable without passing through a later dot
    const tgt = this.wp[k]
    const tl = tgt < 32 ? 1 << tgt : 0, th = tgt < 32 ? 0 : 1 << (tgt - 32)
    const fl = (ul & ~this.wpLo) | tl, fh = (uh & ~this.wpHi) | th
    let r2l = this.nbrLo[cur] & fl, r2h = this.nbrHi[cur] & fh
    if (((r2l & tl) | (r2h & th)) === 0) {
      for (;;) {
        this.spread(r2l, r2h, fl, fh)
        if (this.sl === r2l && this.sh === r2h) return false
        r2l = this.sl; r2h = this.sh
        if (((r2l & tl) | (r2h & th)) !== 0) break
      }
    }
    return true
  }

  dfs(cur, visLo, visHi, k, depth) {
    if (this.aborted || this.count >= this.limit) return
    if (++this.nodes > this.nodeLimit) { this.aborted = true; return }
    this.path[depth - 1] = cur
    const m = this.m
    if (depth === m) {
      this.count++
      this.found.push(Array.from(this.path))
      return
    }
    const candLo = this.nbrLo[cur] & this.allLo & ~visLo
    const candHi = this.nbrHi[cur] & this.allHi & ~visHi
    for (let half = 0; half < 2; half++) {
      let cand = half === 0 ? candLo : candHi
      const base = half * 32
      while (cand !== 0) {
        const low = cand & -cand
        const nb = base + (31 - Math.clz32(low))
        cand &= cand - 1
        const wi = this.wpAt[nb]
        if (wi >= 0 && wi !== k) continue
        if (nb === this.endCell && depth !== m - 1) continue
        const nvLo = nb < 32 ? visLo | (1 << nb) : visLo
        const nvHi = nb < 32 ? visHi : visHi | (1 << (nb - 32))
        const nk = wi >= 0 ? k + 1 : k
        if (!this.feasible(nb, nvLo, nvHi, nk)) continue
        this.dfs(nb, nvLo, nvHi, nk, depth + 1)
        if (this.aborted || this.count >= this.limit) return
      }
    }
  }
}

/** Counts solutions of a board (up to `limit`). Returns { count, nodes, aborted, solutions }. */
export function solveBoard(n, waypoints, wallE, wallS, limit = 2, nodeLimit = 2000000) {
  return new Solver(n, waypoints, wallE, wallS, limit, nodeLimit).run()
}

export function solve(p, limit = 2, nodeLimit = 2000000) {
  return solveBoard(p.n, p.waypoints, p.wallE, p.wallS, limit, nodeLimit)
}

// ---------------------------------------------------------------------------------------------------------------------
// Generator
// ---------------------------------------------------------------------------------------------------------------------

export function bandFor(difficulty) {
  const d = String(difficulty).toLowerCase()
  return d === 'easy' ? 0 : d === 'hard' ? 2 : 1
}

/** Easy 5/6, Medium 6/7, Hard 7/8; the seed picks which of the two. */
export function sizeFor(difficulty, seed) {
  const hi = (((Math.trunc(Number(seed)) % 2) + 2) % 2) === 1
  switch (bandFor(difficulty)) {
    case 0: return hi ? 6 : 5
    case 2: return hi ? 8 : 7
    default: return hi ? 7 : 6
  }
}

export function generateFor(difficulty, seed) {
  return generate(sizeFor(difficulty, seed), bandFor(difficulty), seed)
}

/** A random Hamiltonian path on the n x n grid (backbite moves from a snake). */
export function randomHamiltonianPath(n, rnd) {
  const m = n * n
  const path = new Int32Array(m)
  for (let i = 0; i < m; i++) { const r = Math.floor(i / n), k = i % n; path[i] = r * n + (r % 2 === 0 ? k : n - 1 - k) }
  const pos = new Int32Array(m)
  for (let i = 0; i < m; i++) pos[path[i]] = i
  const reverse = (from, to) => {
    let a = from, b = to
    while (a < b) {
      const t = path[a]; path[a] = path[b]; path[b] = t
      pos[path[a]] = a; pos[path[b]] = b
      a++; b--
    }
  }
  const nb = [0, 0, 0, 0]
  for (let it = 0; it < 40 * m; it++) {
    if (rnd.nextInt(2) === 1) reverse(0, m - 1)
    const e = path[m - 1]
    const r = Math.floor(e / n), c = e % n
    let cnt = 0
    if (r > 0) nb[cnt++] = e - n
    if (r < n - 1) nb[cnt++] = e + n
    if (c > 0) nb[cnt++] = e - 1
    if (c < n - 1) nb[cnt++] = e + 1
    const u = nb[rnd.nextInt(cnt)]
    if (u !== path[m - 2]) reverse(pos[u] + 1, m - 1)
  }
  return Array.from(path)
}

/**
 * Hard keeps the hardest of a few candidate boards (most solver nodes to prove unique), because walls prune the search so
 * much that one random hard board is not reliably harder than a medium one. The number of candidates and the budget are
 * counted in solver nodes, never time, so the result is still a pure function of (n, band, seed).
 */
const HARD_CANDIDATES = (n) => (n <= 6 ? 4 : n === 7 ? 3 : 2)
const CANDIDATE_NODE_BUDGET = 25000

export function generate(n, band, seed) {
  if (!(n >= 2 && n <= 8)) throw new RangeError('n must be 2..8')
  const m = n * n
  const want = band === 2 ? HARD_CANDIDATES(n) : 1
  let best = null
  let spentTotal = 0
  let got = 0
  for (let salt = 0; salt < 500; salt++) {
    const rnd = makeRng(`trail|${seed}|${n}|${band}|${salt}`)
    const p = attempt(n, m, band, rnd)
    if (p) {
      got++
      spentTotal += p.nodes
      if (!best || p.nodes > best.nodes) best = p
      if (got >= want || spentTotal > CANDIDATE_NODE_BUDGET) return best
    }
  }
  if (best) return best
  throw new Error('trail: generator gave up')
}

function attempt(n, m, band, rnd) {
  const sol = randomHamiltonianPath(n, rnd)
  const pos = new Int32Array(m)
  for (let i = 0; i < m; i++) pos[sol[i]] = i
  const wallE = new Uint8Array(m)
  const wallS = new Uint8Array(m)
  // waypoints stored as indices along the solution, so dot order is always the solution order
  const isWp = new Uint8Array(m)
  isWp[0] = 1; isWp[m - 1] = 1
  let wpCount = 2
  const addWp = (i) => { if (isWp[i]) return false; isWp[i] = 1; wpCount++; return true }
  const initial = band === 0 ? Math.max(3, Math.floor(m * 0.30)) : band === 1 ? Math.max(3, Math.floor(m * 0.14)) : 3
  while (wpCount < initial) addWp(rnd.nextInt(m))
  const wallBias = band === 0 ? 0.0 : band === 1 ? 0.25 : 0.55
  let spent = 0

  const wpCells = () => { const out = []; for (let i = 0; i < m; i++) if (isWp[i]) out.push(sol[i]); return out }
  const addWallBetween = (a, b) => {
    if (b === a + 1) wallE[a] = 1
    else if (b === a - 1) wallE[b] = 1
    else if (b === a + n) wallS[a] = 1
    else wallS[b] = 1
  }
  const sameAs = (q) => { for (let i = 0; i < m; i++) if (q[i] !== sol[i]) return false; return true }

  let result
  let rounds = 0
  for (;;) {
    if (++rounds > 400) return null
    result = new Solver(n, wpCells(), wallE, wallS, 2, SOLVE_NODE_LIMIT).run()
    spent += result.nodes
    if (result.aborted) {
      // too hard to prove within budget: give the player more dots
      let added = 0
      while (added < 2 && wpCount < m) { if (addWp(rnd.nextInt(m))) added++ }
      continue
    }
    if (result.count === 1) break
    if (result.count === 0) return null // cannot happen (the generator path is always a solution); defensive
    // two solutions found: at least one differs from the generator's path
    const q = result.solutions.find((s) => !sameAs(s))
    if (!q) return null
    const posQ = new Int32Array(m)
    for (let i = 0; i < m; i++) posQ[q[i]] = i
    let done = false
    if (rnd.nextDouble() >= wallBias) {
      // add a dot where Q leaves the window between its neighbouring dots
      const lower = new Int32Array(m), higher = new Int32Array(m)
      let last = 0
      for (let i = 0; i < m; i++) { lower[i] = last; if (isWp[i]) last = i }
      last = m - 1
      for (let i = m - 1; i >= 0; i--) { higher[i] = last; if (isWp[i]) last = i }
      const cands = []
      for (let c = 0; c < m; c++) {
        const i = pos[c]
        if (isWp[i]) continue
        const pq = posQ[c]
        if (pq < posQ[sol[lower[i]]] || pq > posQ[sol[higher[i]]]) cands.push(i)
      }
      if (cands.length) { addWp(cands[rnd.nextInt(cands.length)]); done = true }
    }
    if (!done) {
      if (band === 0) return null // easy puzzles never use walls: try another path
      // add a wall on an edge Q uses but the generator path does not
      const cands = []
      for (let i = 0; i < m - 1; i++) {
        if (Math.abs(pos[q[i]] - pos[q[i + 1]]) !== 1) cands.push(i)
      }
      if (!cands.length) return null
      const i = cands[rnd.nextInt(cands.length)]
      addWallBetween(q[i], q[i + 1])
    }
  }

  // reduction: drop dots that are not needed for uniqueness (harder puzzles), within a node budget
  if (band >= 1) {
    const tries = band === 1 ? 6 : 60
    const removable = []
    for (let x = 1; x < m - 1; x++) if (isWp[x]) removable.push(x)
    rnd.shuffle(removable)
    let t = 0
    for (const x of removable) {
      if (t >= tries || spent > REDUCE_NODE_BUDGET || wpCount <= 3) break
      t++
      isWp[x] = 0; wpCount--
      const r = new Solver(n, wpCells(), wallE, wallS, 2, SOLVE_NODE_LIMIT).run()
      spent += r.nodes
      if (r.aborted || r.count !== 1) { isWp[x] = 1; wpCount++ }
    }
  }

  // hard only: also drop walls that are not needed. Walls prune the search a lot, so without this the hardest band would
  // need fewer solver nodes than the medium one. Fewer clues of any kind = more branching = harder to deduce.
  if (band === 2) {
    const wallsList = []
    for (let i = 0; i < m; i++) { if (wallE[i]) wallsList.push([wallE, i]); if (wallS[i]) wallsList.push([wallS, i]) }
    rnd.shuffle(wallsList)
    let t = 0
    let wallSpent = 0
    for (const [arr, i] of wallsList) {
      if (t >= WALL_TRIES || wallSpent > WALL_NODE_BUDGET) break
      t++
      arr[i] = 0
      const r = new Solver(n, wpCells(), wallE, wallS, 2, SOLVE_NODE_LIMIT).run()
      wallSpent += r.nodes
      if (r.aborted || r.count !== 1) arr[i] = 1
    }
  }

  const wps = wpCells()
  const finalRes = new Solver(n, wps, wallE, wallS, 2, Infinity).run()
  if (finalRes.count !== 1) return null
  const puzzle = new TrailPuzzle(n, wps, wallE, wallS, sol, band, finalRes.nodes)
  return puzzle.isValidSolution(sol) ? puzzle : null
}
