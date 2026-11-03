// --- web/game-trail ---
// Pointer geometry and the player's line for Trail. Pure (no DOM / React) so every rule is unit tested.

/** Cell under (x, y) on a board of n x n cells of side `cell` whose top-left corner is the origin, or -1 outside. */
export function cellAt(x, y, cell, n) {
  if (!(cell > 0)) return -1
  const c = Math.floor(x / cell), r = Math.floor(y / cell)
  return c < 0 || r < 0 || c >= n || r >= n ? -1 : r * n + c
}

/** True when (x, y) is still inside headCell grown by `margin` x cell on every side (stops edge flicker). */
export function stickyHit(x, y, headCell, cell, n, margin = 0.18) {
  const c = headCell % n, r = Math.floor(headCell / n)
  const m = cell * margin
  return x >= c * cell - m && x <= (c + 1) * cell + m && y >= r * cell - m && y <= (r + 1) * cell + m
}

/**
 * Distinct consecutive cells crossed by the straight move (x0,y0) -> (x1,y1), sampled finely enough that a fast flick
 * cannot skip a cell. Points outside the board are clamped onto it.
 */
export function cellsAlong(x0, y0, x1, y1, cell, n) {
  const side = cell * n
  const clamp = (v) => Math.min(Math.max(v, 0), side - 0.001)
  const dist = Math.hypot(x1 - x0, y1 - y0)
  const steps = Math.max(1, Math.ceil(dist / (cell * 0.2)))
  const out = []
  let last = -1
  for (let i = 0; i <= steps; i++) {
    const t = i / steps
    const c = cellAt(clamp(x0 + (x1 - x0) * t), clamp(y0 + (y1 - y0) * t), cell, n)
    if (c >= 0 && c !== last) { out.push(c); last = c }
  }
  return out
}

const DIRS = { up: [-1, 0], down: [1, 0], left: [0, -1], right: [0, 1] }

/** The player's line. */
export class TrailState {
  constructor(puzzle) {
    this.p = puzzle
    this.m = puzzle.cells
    this.path = new Int32Array(this.m)
    this.onPathArr = new Uint8Array(this.m)
    this.hintsUsed = 0
    this.resets = 0
    this.length = 0
    this.dotsVisited = 0
    this._clear()
  }

  _clear() {
    this.onPathArr.fill(0)
    this.length = 0
    this.dotsVisited = 0
    this._push(this.p.waypoints[0])
  }

  get head() { return this.path[this.length - 1] }
  get totalDots() { return this.p.waypoints.length }
  onPath(cell) { return this.onPathArr[cell] === 1 }
  pathCells() { return Array.from(this.path.subarray(0, this.length)) }

  _push(c) {
    this.path[this.length++] = c
    this.onPathArr[c] = 1
    if (this.p.dotAt(c) >= 0) this.dotsVisited++
  }

  _pop() {
    const c = this.path[--this.length]
    this.onPathArr[c] = 0
    if (this.p.dotAt(c) >= 0) this.dotsVisited--
  }

  canStep(from, to) {
    const p = this.p
    if (p.blocked(from, to) || this.onPathArr[to]) return false
    const d = p.dotAt(to)
    if (d >= 0 && d !== this.dotsVisited) return false // dots must be reached in order
    // the last dot may only close the line once every other cell is covered
    if (d === this.totalDots - 1 && this.length !== this.m - 1) return false
    return true
  }

  /** Extend by one cell, or step back one cell when `to` is the previous cell. Returns true if the line changed. */
  step(to) {
    if (this.length >= 2 && to === this.path[this.length - 2]) { this._pop(); return true }
    if (!this.canStep(this.head, to)) return false
    this._push(to)
    return true
  }

  /** Remove the last cell (never the start). */
  undo() {
    if (this.length <= 1) return false
    this._pop()
    return true
  }

  truncateTo(cell) {
    if (!this.onPathArr[cell] || cell === this.head) return false
    while (this.head !== cell) this._pop()
    return true
  }

  /**
   * Move the line's end towards `target`: dragging onto a cell already on the line cuts back to it; otherwise walk towards
   * the target one cell at a time, choosing a legal orthogonal step, so diagonal drags and fast flicks that jump several
   * cells still work. Stops where the walk gets blocked.
   */
  advanceTo(target) {
    const n = this.p.n
    if (!(target >= 0 && target < this.m) || target === this.head) return false
    if (this.onPathArr[target]) return this.truncateTo(target)
    let changed = false
    let guard = 0
    while (this.head !== target && guard++ < 2 * n) {
      const h = this.head
      const dr = Math.floor(target / n) - Math.floor(h / n)
      const dc = (target % n) - (h % n)
      const vert = dr !== 0 ? h + (dr > 0 ? n : -n) : -1
      const horiz = dc !== 0 ? h + (dc > 0 ? 1 : -1) : -1
      const rowFirst = Math.abs(dr) >= Math.abs(dc)
      const first = rowFirst ? vert : horiz
      const second = rowFirst ? horiz : vert
      const next = first >= 0 && this.canStep(h, first) ? first : second >= 0 && this.canStep(h, second) ? second : -1
      if (next < 0) break
      this._push(next)
      changed = true
    }
    return changed
  }

  /** Tap (screen reader / keyboard friendly): cut back to a cell on the line, or extend onto an adjacent cell. */
  tap(cell) {
    if (!(cell >= 0 && cell < this.m) || cell === this.head) return false
    if (this.onPathArr[cell]) return this.truncateTo(cell)
    return this.p.adjacent(this.head, cell) ? this.step(cell) : false
  }

  /** Arrow key: move the end of the line one cell up/down/left/right (stepping back onto the previous cell retracts). */
  arrow(dir) {
    const d = DIRS[dir]
    if (!d) return false
    const n = this.p.n
    const r = Math.floor(this.head / n) + d[0]
    const c = (this.head % n) + d[1]
    if (r < 0 || c < 0 || r >= n || c >= n) return false
    return this.step(r * n + c)
  }

  /** Plain-language reason an arrow key could not move the line, for the live message and screen readers. */
  whyNot(dir) {
    const d = DIRS[dir]
    if (!d) return 'Use the arrow keys.'
    const n = this.p.n
    const r = Math.floor(this.head / n) + d[0]
    const c = (this.head % n) + d[1]
    if (r < 0 || c < 0 || r >= n || c >= n) return 'That is the edge of the board.'
    const to = r * n + c
    if (this.p.blocked(this.head, to)) return 'A wall is in the way.'
    if (this.onPathArr[to]) return 'The line is already there.'
    const dot = this.p.dotAt(to)
    if (dot >= 0 && dot !== this.dotsVisited) {
      return dot > this.dotsVisited ? `Reach dot ${this.dotsVisited + 1} first.` : 'That dot is already used.'
    }
    if (dot === this.totalDots - 1 && this.length !== this.m - 1) return 'Cover every other square before the last dot.'
    return 'You cannot go that way.'
  }

  reset() { this._clear(); this.resets++ }

  solved() {
    return this.length === this.m && this.head === this.p.waypoints[this.totalDots - 1] && this.dotsVisited === this.totalDots
  }

  /** Number of leading cells that match the solution. */
  correctPrefix() {
    let i = 0
    while (i < this.length && this.path[i] === this.p.solution[i]) i++
    return i
  }

  /**
   * Reveals the next `count` cells of the solution. If the line has strayed from the solution it is first cut back to
   * the last correct cell. Returns the revealed cells (empty when already solved).
   */
  hint(count = 3) {
    const ok = this.correctPrefix()
    if (ok < this.length) this.truncateTo(this.p.solution[ok - 1])
    this.hintsUsed++
    const from = Math.min(ok, this.m)
    const to = Math.min(from + count, this.m)
    return from >= this.m ? [] : Array.from(this.p.solution.slice(from, to))
  }

  stars() { return this.hintsUsed === 0 ? 3 : this.hintsUsed <= 2 ? 2 : 1 }
  score() { return Math.max(10, 40 + this.stars() * 20 + this.p.band * 10 - this.hintsUsed * 8) }

  encode() { return `${this.pathCells().join(',')}|${this.hintsUsed}|${this.resets}` }
  restore(s) {
    try {
      const parts = String(s).split('|')
      const cells = parts[0].split(',').filter((x) => x !== '').map(Number)
      if (!cells.length || cells.some((x) => !Number.isInteger(x)) || cells[0] !== this.p.waypoints[0]) return
      this._clear()
      for (let i = 1; i < cells.length; i++) if (!this.step(cells[i])) break
      this.hintsUsed = Number.parseInt(parts[1], 10) || 0
      this.resets = Number.parseInt(parts[2], 10) || 0
    } catch {
      this._clear()
    }
  }
}
