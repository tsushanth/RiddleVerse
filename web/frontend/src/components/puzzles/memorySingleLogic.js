// Pure logic for the "Memory Match" / N-Back single puzzle (type memoryprevioussingle).
// The game: symbols appear one at a time; for every symbol after the first, say whether it is the SAME as the one
// just before it or DIFFERENT. Key: 1 = same, 0 = different (backend memoryPreviousSingleGenerator answerSequence).

import { parseJsonField } from './puzzleUtils.js'

export const FIRST_SYMBOL_MS = 2000 // the first symbol is only to remember
export const FEEDBACK_MS = 700      // tick/cross pause after each answer
export const THINK_SECONDS = 4      // allowance per answer, on top of the unavoidable waiting
export const PASS_RATIO = 0.6       // same bar as Android

export function parseMemorySingle(puzzle) {
  const q = parseJsonField(puzzle?.question)
  const a = parseJsonField(puzzle?.answer)
  const symbols = Array.isArray(q?.binarySequence) ? q.binarySequence.map(Number) : []
  const serverKey = Array.isArray(a?.correctAnswers) ? a.correctAnswers.map(Number)
    : Array.isArray(a?.answerSequence) ? a.answerSequence.map(Number) : []
  return { symbols, serverKey }
}

/** One expected answer per symbol after the first, derived from the sequence itself. */
export function expectedAnswers(symbols) {
  const out = []
  for (let i = 1; i < symbols.length; i++) out.push(symbols[i] === symbols[i - 1] ? 1 : 0)
  return out
}

/** Unanswered symbols count as wrong. */
export function scoreRun(expected, answers) {
  const total = expected.length
  let correct = 0
  for (let i = 0; i < total; i++) if (answers[i] === expected[i]) correct++
  return { correct, total, passed: total > 0 && correct >= Math.ceil(total * PASS_RATIO) }
}

/**
 * Seconds on the clock. The clock runs while the first symbol is shown and during every feedback pause, so it must
 * cover that waiting plus THINK_SECONDS per answer (the old viewing clock was a flat 30 s for 1.8 s x steps).
 */
export function timeBudgetSeconds(symbols) {
  if (symbols < 2) return 0
  const answers = symbols - 1
  const waitMs = FIRST_SYMBOL_MS + answers * FEEDBACK_MS
  return answers * THINK_SECONDS + Math.ceil(waitMs / 1000)
}

export function newSession(symbols) {
  return { symbols, expected: expectedAnswers(symbols), index: 0, answers: [], feedback: null, done: false }
}

/** index 0 = first symbol (remember only); feedback = null | 'correct' | 'wrong'. The sequence is never replaced. */
export function reduce(state, action) {
  if (state.done) return state
  switch (action.type) {
    case 'firstShown':
      return state.index === 0 && state.symbols.length > 1 ? { ...state, index: 1 } : state
    case 'answer': {
      if (state.index < 1 || state.feedback !== null) return state
      const k = state.index - 1
      const ok = action.value === state.expected[k]
      return { ...state, answers: [...state.answers, action.value], feedback: ok ? 'correct' : 'wrong' }
    }
    case 'feedbackDone': {
      if (state.feedback === null) return state
      const last = state.index >= state.symbols.length - 1
      return last ? { ...state, feedback: null, done: true } : { ...state, feedback: null, index: state.index + 1 }
    }
    case 'timeout':
      return { ...state, feedback: null, done: true }
    default:
      return state
  }
}
