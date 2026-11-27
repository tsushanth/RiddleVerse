// The first puzzle a new visitor meets: a 4x4 Sudoku with five blanks (the Android app's StarterPuzzle). Tap only,
// no lives and no timer, so it can always be finished. Pure logic: node tests load it without a browser.
import { generateSudokuRaw } from '../components/puzzles/grid/engine/sudoku.js'

export const STARTER_SLUG = 'starter'
export const STARTER_CLUES = 11
export const STARTER_DONE_KEY = 'rv_starter_done'
export const KNOWN_PLAYER_KEY = 'rv_known_player'

export function createStarter(seed) {
  const { puzzle, solution } = generateSudokuRaw(4, 2, 2, STARTER_CLUES, seed)
  const values = puzzle.slice()
  return { given: puzzle, solution, values, selected: nextOpen(values, solution), solved: false }
}

/** First empty or wrong cell, or -1. */
export function nextOpen(values, solution) {
  return values.findIndex((v, i) => v !== solution[i])
}

export function selectCell(state, cell) {
  return state.solved || state.given[cell] !== 0 ? state : { ...state, selected: cell }
}

/** A right digit moves the selection on; a wrong one stays selected, marked, until another is tapped. */
export function enterDigit(state, digit) {
  const i = state.selected
  if (state.solved || i < 0 || state.given[i] !== 0 || digit < 1 || digit > 4) return state
  const values = state.values.slice()
  values[i] = digit
  const open = nextOpen(values, state.solution)
  return { ...state, values, selected: digit === state.solution[i] ? open : i, solved: open === -1 }
}

/** True for a browser that has neither solved the starter nor signed in before. `getStorage` may throw. */
export function isNewVisitor(getStorage) {
  try {
    const s = getStorage()
    return ![STARTER_DONE_KEY, KNOWN_PLAYER_KEY, 'userId', 'token'].some((k) => s.getItem(k))
  } catch {
    return false
  }
}

export function remember(getStorage, key) {
  try { getStorage().setItem(key, '1') } catch { /* private mode: the starter shows again, which is harmless */ }
}
