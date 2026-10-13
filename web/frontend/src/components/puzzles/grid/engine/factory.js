import { generateSudoku } from './sudoku.js'
import { generateBinary } from './binary.js'
import { generateCageMath } from './cagemath.js'
import { generateSignGrid } from './signgrid.js'

/** Same type keys as the Android app. */
export const GRID_GAMES = {
  sudoku: { key: 'sudoku', title: 'Sudoku', emoji: '🔢' },
  binarygrid: { key: 'binarygrid', title: 'Binary Grid', emoji: '⚫' },
  cagemath: { key: 'cagemath', title: 'Cage Math', emoji: '\ud83e\uddee' },
  signgrid: { key: 'signgrid', title: 'Sign Grid', emoji: '⚖️' },
}

export const GRID_GAME_KEYS = Object.keys(GRID_GAMES)

export function isGridType(key) {
  return typeof key === 'string' && Object.prototype.hasOwnProperty.call(GRID_GAMES, key.toLowerCase())
}

/**
 * Accepts the web app's numeric levels (1 easy, 2 medium, 3+ hard) or Android-style strings.
 * Returns 'easy' | 'medium' | 'hard'.
 */
export function normalizeDifficulty(d) {
  if (typeof d === 'number') return d <= 1 ? 'easy' : d === 2 ? 'medium' : 'hard'
  switch (String(d == null ? '' : d).toLowerCase()) {
    case '1':
    case 'easy':
    case 'beginner':
    case 'tutorial':
      return 'easy'
    case '3':
    case '4':
    case '5':
    case 'hard':
    case 'expert':
    case 'master':
    case 'grandmaster':
    case 'hard+':
    case 'hard-':
      return 'hard'
    default:
      return 'medium'
  }
}

/**
 * Builds the game-agnostic spec consumed by the UI and play state.
 * Binary Grid stores symbols as 1 ("0") and 2 ("1"); `symbolText` maps back.
 */
export function buildSpec(gameKey, difficulty, seed) {
  const game = String(gameKey).toLowerCase()
  const d = normalizeDifficulty(difficulty)
  const base = { game, difficulty: d, seed, boxRows: 0, boxCols: 0, cages: [], cageOf: [], hRel: [], vRel: [] }
  switch (game) {
    case 'sudoku': {
      const p = generateSudoku(d, seed)
      return { ...base, n: p.size, given: p.puzzle, solution: p.solution, boxRows: p.boxRows, boxCols: p.boxCols, symbols: p.size }
    }
    case 'binarygrid': {
      const p = generateBinary(d, seed)
      return { ...base, n: p.size, given: p.puzzle, solution: p.solution, symbols: 2 }
    }
    case 'cagemath': {
      const p = generateCageMath(d, seed)
      return { ...base, n: p.size, given: new Array(p.size * p.size).fill(0), solution: p.solution, cages: p.cages, cageOf: p.cageOf, symbols: p.size }
    }
    case 'signgrid': {
      const p = generateSignGrid(d, seed)
      return { ...base, n: p.size, given: p.givens, solution: p.solution, hRel: p.hRel, vRel: p.vRel, symbols: p.size }
    }
    default:
      throw new Error(`unknown grid game: ${gameKey}`)
  }
}

export function symbolText(spec, v) {
  if (v === 0) return ''
  return spec.game === 'binarygrid' ? String(v - 1) : String(v)
}

export function hintFor(game) {
  switch (game) {
    case 'sudoku':
      return 'Every row, column and box holds each digit exactly once'
    case 'binarygrid':
      return 'Equal 0s and 1s per line, never three in a row'
    case 'cagemath':
      return 'Fill the grid so each cage hits its target'
    default:
      return 'Digits never repeat in a line and every sign must hold'
  }
}

export function rulesFor(game) {
  switch (game) {
    case 'sudoku':
      return 'Fill every empty cell so each row, each column and each box contains every digit exactly once. Use the pencil tool to jot candidate digits. Every puzzle has exactly one solution.'
    case 'binarygrid':
      return 'Fill every cell with 0 or 1. No more than two equal digits may touch in a row or column, each row and column has as many 0s as 1s, and no two rows (or two columns) are identical.'
    case 'cagemath':
      return "Fill the grid with digits so none repeats in any row or column. The number and sign in a cage corner give the target: the cage's digits must add, subtract, multiply or divide to it. A cage with only a number is a single cell. Digits may repeat inside a cage if they are in different rows and columns."
    default:
      return 'Fill the grid so no digit repeats in any row or column. A sign between two cells points to the smaller one: the cell at the narrow end holds the smaller digit.'
  }
}

/** Reads the seed from a puzzle.question JSON if it belongs to this game, else null. */
export function seedFromPuzzle(puzzle, gameKey) {
  try {
    const q = typeof puzzle?.question === 'string' ? JSON.parse(puzzle.question) : puzzle?.question
    if (q && q.game === gameKey && Number.isFinite(Number(q.seed))) return Number(q.seed)
  } catch {
    /* not ours */
  }
  return null
}

export function randomSeed() {
  return Math.floor(Math.random() * 2147483647) + 1
}
