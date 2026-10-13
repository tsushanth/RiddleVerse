import { Rng, range } from './prng.js'

// Cell values: 0 empty, 1 = symbol "0", 2 = symbol "1".

/** Tier -> [size, targetClues] (matches Android BinairoGenerator.configFor). */
export function binaryConfigFor(difficulty) {
  switch (String(difficulty).toLowerCase()) {
    case 'easy':
    case 'beginner':
      return [6, 20]
    case 'medium':
      return [8, 28]
    default:
      return [10, 38]
  }
}

function canPlace(g, n, cell, v) {
  const r = Math.floor(cell / n)
  const c = cell % n
  for (let start = c - 2; start <= c; start++) {
    let same = 0
    for (let k = 0; k < 3; k++) {
      const cc = start + k
      if (cc < 0 || cc >= n) {
        same = -9
        break
      }
      const x = cc === c ? v : g[r * n + cc]
      if (x === v) same++
    }
    if (same === 3) return false
  }
  for (let start = r - 2; start <= r; start++) {
    let same = 0
    for (let k = 0; k < 3; k++) {
      const rr = start + k
      if (rr < 0 || rr >= n) {
        same = -9
        break
      }
      const x = rr === r ? v : g[rr * n + c]
      if (x === v) same++
    }
    if (same === 3) return false
  }
  let rowCount = 1
  let colCount = 1
  for (let i = 0; i < n; i++) {
    if (i !== c && g[r * n + i] === v) rowCount++
    if (i !== r && g[i * n + c] === v) colCount++
  }
  return !(rowCount > n / 2 || colCount > n / 2)
}

function linesEqual(g, n, a, b, byRow) {
  for (let i = 0; i < n; i++) {
    const x = byRow ? g[a * n + i] : g[i * n + a]
    const y = byRow ? g[b * n + i] : g[i * n + b]
    if (x !== y || x === 0) return false
  }
  return true
}

function uniqueLinesOk(g, n, cell) {
  const r = Math.floor(cell / n)
  const c = cell % n
  if (range(n).every((i) => g[r * n + i] !== 0)) {
    for (let o = 0; o < n; o++) if (o !== r && linesEqual(g, n, r, o, true)) return false
  }
  if (range(n).every((i) => g[i * n + c] !== 0)) {
    for (let o = 0; o < n; o++) if (o !== c && linesEqual(g, n, c, o, false)) return false
  }
  return true
}

function fill(g, n, cell, rnd) {
  if (cell === n * n) return true
  const order = rnd.bool() ? [1, 2] : [2, 1]
  for (const v of order) {
    if (!canPlace(g, n, cell, v)) continue
    g[cell] = v
    if (uniqueLinesOk(g, n, cell) && fill(g, n, cell + 1, rnd)) return true
    g[cell] = 0
  }
  return false
}

/** Full rule check of a completely filled grid. */
export function binaryIsSolved(g, n) {
  if (g.some((x) => x === 0)) return false
  for (let i = 0; i < n; i++) {
    let rowOnes = 0
    let colOnes = 0
    for (let j = 0; j < n; j++) {
      if (g[i * n + j] === 2) rowOnes++
      if (g[j * n + i] === 2) colOnes++
      if (j >= 2) {
        if (g[i * n + j] === g[i * n + j - 1] && g[i * n + j] === g[i * n + j - 2]) return false
        if (g[j * n + i] === g[(j - 1) * n + i] && g[j * n + i] === g[(j - 2) * n + i]) return false
      }
    }
    if (rowOnes !== n / 2 || colOnes !== n / 2) return false
  }
  for (let a = 0; a < n; a++)
    for (let b = a + 1; b < n; b++) {
      if (linesEqual(g, n, a, b, true) || linesEqual(g, n, a, b, false)) return false
    }
  return true
}

/** Counts solutions up to `limit`. Does not mutate `puzzle`. */
export function binaryCountSolutions(puzzle, n, limit) {
  const g = Array.from(puzzle)
  for (let i = 0; i < g.length; i++) {
    const v = g[i]
    if (v === 0) continue
    g[i] = 0
    const ok = canPlace(g, n, i, v)
    g[i] = v
    if (!ok) return 0
  }
  let count = 0
  const go = () => {
    if (count >= limit) return
    let best = -1
    let bestOpts = 3
    let bestMask = 0
    for (let cell = 0; cell < g.length; cell++) {
      if (g[cell] !== 0) continue
      let mask = 0
      let opts = 0
      for (let v = 1; v <= 2; v++)
        if (canPlace(g, n, cell, v)) {
          mask |= v
          opts++
        }
      if (opts < bestOpts) {
        best = cell
        bestOpts = opts
        bestMask = mask
        if (opts === 0) return
      }
      if (bestOpts <= 1) break
    }
    if (best === -1) {
      if (binaryIsSolved(g, n)) count++
      return
    }
    for (let v = 1; v <= 2; v++) {
      if (!(bestMask & v)) continue
      g[best] = v
      if (uniqueLinesOk(g, n, best)) go()
      g[best] = 0
      if (count >= limit) return
    }
  }
  go()
  return count
}

export function generateBinaryRaw(size, targetClues, seed) {
  if (size % 2 !== 0) throw new Error('size must be even')
  const rnd = new Rng(seed)
  const solution = new Array(size * size).fill(0)
  if (!fill(solution, size, 0, rnd)) throw new Error('no binary grid solution')
  const puzzle = solution.slice()
  let clues = size * size
  for (const cell of rnd.shuffled(range(size * size))) {
    if (clues <= targetClues) break
    const keep = puzzle[cell]
    puzzle[cell] = 0
    if (binaryCountSolutions(puzzle, size, 2) === 1) clues--
    else puzzle[cell] = keep
  }
  return { size, puzzle, solution, clueCount: puzzle.filter((x) => x !== 0).length }
}

export function generateBinary(difficulty, seed) {
  const [size, clues] = binaryConfigFor(difficulty)
  return generateBinaryRaw(size, clues, seed)
}
