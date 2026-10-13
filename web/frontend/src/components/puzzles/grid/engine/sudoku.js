import { Rng, range, bitCount } from './prng.js'

/** Tier -> [size, boxRows, boxCols, targetClues] (matches Android SudokuGenerator.configFor). */
export function sudokuConfigFor(difficulty) {
  switch (String(difficulty).toLowerCase()) {
    case 'easy':
    case 'beginner':
      return [4, 2, 2, 8]
    case 'medium':
      return [6, 2, 3, 16]
    default:
      return [9, 3, 3, 30]
  }
}

function candidatesMask(g, n, br, bc, cell) {
  const r = Math.floor(cell / n)
  const c = cell % n
  let used = 0
  for (let i = 0; i < n; i++) used |= (1 << g[r * n + i]) | (1 << g[i * n + c])
  const r0 = Math.floor(r / br) * br
  const c0 = Math.floor(c / bc) * bc
  for (let dr = 0; dr < br; dr++) for (let dc = 0; dc < bc; dc++) used |= 1 << g[(r0 + dr) * n + c0 + dc]
  const all = (1 << (n + 1)) - 2
  return all & ~used
}

function pickCell(g, n, br, bc) {
  let best = -1
  let bestMask = 0
  let bestCount = 99
  for (let cell = 0; cell < g.length; cell++) {
    if (g[cell] !== 0) continue
    const m = candidatesMask(g, n, br, bc, cell)
    const k = bitCount(m)
    if (k < bestCount) {
      best = cell
      bestMask = m
      bestCount = k
      if (k <= 1) break
    }
  }
  return { best, bestMask, bestCount }
}

function fill(g, n, br, bc, rnd) {
  const { best, bestMask, bestCount } = pickCell(g, n, br, bc)
  if (best === -1) return true
  if (bestCount === 0) return false
  const digits = rnd.shuffled(range(n).map((i) => i + 1).filter((d) => bestMask & (1 << d)))
  for (const d of digits) {
    g[best] = d
    if (fill(g, n, br, bc, rnd)) return true
  }
  g[best] = 0
  return false
}

/** Counts solutions up to `limit`. Does not mutate `puzzle`. */
export function sudokuCountSolutions(puzzle, n, br, bc, limit) {
  const g = Array.from(puzzle)
  let count = 0
  const go = () => {
    if (count >= limit) return
    const { best, bestMask, bestCount } = pickCell(g, n, br, bc)
    if (best === -1) {
      count++
      return
    }
    if (bestCount === 0) return
    for (let d = 1; d <= n; d++) {
      if (!(bestMask & (1 << d))) continue
      g[best] = d
      go()
      if (count >= limit) break
    }
    g[best] = 0
  }
  go()
  return count
}

/** True when the (possibly partial) grid has no row/column/box duplicate. */
export function sudokuIsConsistent(grid, n, br, bc) {
  const g = Array.from(grid)
  for (let cell = 0; cell < g.length; cell++) {
    const v = g[cell]
    if (v === 0) continue
    g[cell] = 0
    const ok = (candidatesMask(g, n, br, bc, cell) & (1 << v)) !== 0
    g[cell] = v
    if (!ok) return false
  }
  return true
}

export function generateSudokuRaw(size, boxRows, boxCols, targetClues, seed) {
  const rnd = new Rng(seed)
  const solution = new Array(size * size).fill(0)
  if (!fill(solution, size, boxRows, boxCols, rnd)) throw new Error('could not build a Sudoku solution')
  const puzzle = solution.slice()
  let clues = size * size
  for (const cell of rnd.shuffled(range(size * size))) {
    if (clues <= targetClues) break
    const keep = puzzle[cell]
    puzzle[cell] = 0
    if (sudokuCountSolutions(puzzle, size, boxRows, boxCols, 2) === 1) clues--
    else puzzle[cell] = keep
  }
  return { size, boxRows, boxCols, puzzle, solution, clueCount: puzzle.filter((x) => x !== 0).length }
}

export function generateSudoku(difficulty, seed) {
  const [size, br, bc, clues] = sudokuConfigFor(difficulty)
  return generateSudokuRaw(size, br, bc, clues, seed)
}
