// Pure rules for Word Guess (5 letters, 6 tries). Mirrors WordGuessLogic.kt.
import { createRng } from './prng.js'

export const WORD_LENGTH = 5
export const MAX_TRIES = 6
export const STATE_RANK = { unknown: 0, absent: 1, present: 2, correct: 3 }

/**
 * Two-pass feedback: exact matches first, then each remaining answer letter can satisfy
 * at most ONE misplaced guess letter (left to right).
 */
export function evaluate(guess, answer) {
  if (guess.length !== answer.length) throw new Error('length mismatch')
  const g = guess.toLowerCase()
  const a = answer.toLowerCase()
  const result = new Array(g.length).fill('absent')
  const remaining = {}
  for (let i = 0; i < g.length; i++) {
    if (g[i] === a[i]) result[i] = 'correct'
    else remaining[a[i]] = (remaining[a[i]] || 0) + 1
  }
  for (let i = 0; i < g.length; i++) {
    if (result[i] === 'correct') continue
    if ((remaining[g[i]] || 0) > 0) {
      result[i] = 'present'
      remaining[g[i]]--
    }
  }
  return result
}

export function keyboardStates(guesses, answer) {
  const out = {}
  for (const guess of guesses) {
    const fb = evaluate(guess, answer)
    for (let i = 0; i < guess.length; i++) {
      const c = guess[i].toLowerCase()
      if (STATE_RANK[fb[i]] > STATE_RANK[out[c] || 'unknown']) out[c] = fb[i]
    }
  }
  return out
}

/** Returns null when [candidate] is legal in hard mode, else a short message. */
export function hardModeViolation(previous, answer, candidate) {
  const cand = candidate.toLowerCase()
  for (const prev of previous) {
    const fb = evaluate(prev, answer)
    for (let i = 0; i < prev.length; i++) {
      if (fb[i] === 'correct' && cand[i] !== prev[i].toLowerCase()) {
        return `Letter ${i + 1} must be ${prev[i].toUpperCase()}`
      }
    }
    const needed = {}
    for (let i = 0; i < prev.length; i++) {
      if (fb[i] !== 'absent') {
        const c = prev[i].toLowerCase()
        needed[c] = (needed[c] || 0) + 1
      }
    }
    for (const [c, n] of Object.entries(needed)) {
      let have = 0
      for (const ch of cand) if (ch === c) have++
      if (have < n) return `Guess must contain ${c.toUpperCase()}`
    }
  }
  return null
}

/** Spoiler-free share text: emoji grid only, no letters. */
export function shareText(guesses, answer, won, hard) {
  const score = won ? `${guesses.length}/${MAX_TRIES}` : `X/${MAX_TRIES}`
  const rows = guesses.map((g) =>
    evaluate(g, answer)
      .map((s) => (s === 'correct' ? '\u{1F7E9}' : s === 'present' ? '\u{1F7E8}' : '⬛'))
      .join('')
  )
  return `RiddleVerse Word Guess ${score}${hard ? '*' : ''}\n\n${rows.join('\n')}`.trimEnd()
}

export function score(triesUsed, hintUsed, hard) {
  const base = 60 + (MAX_TRIES - triesUsed) * 20 + (hard ? 20 : 0)
  return Math.max(hintUsed ? base - 30 : base, 10)
}

export function newGame(answer, hard) {
  return { answer, hard: !!hard, guesses: [], current: '', hintUsed: false }
}

export function status(game) {
  if (game.guesses.length && game.guesses[game.guesses.length - 1] === game.answer) return 'won'
  if (game.guesses.length >= MAX_TRIES) return 'lost'
  return 'playing'
}

export function typeLetter(game, c) {
  const ch = String(c).toLowerCase()
  if (status(game) !== 'playing' || game.current.length >= WORD_LENGTH || !/^[a-z]$/.test(ch)) return game
  return { ...game, current: game.current + ch }
}

export function backspace(game) {
  if (status(game) !== 'playing' || !game.current.length) return game
  return { ...game, current: game.current.slice(0, -1) }
}

/** Returns {game, outcome} with outcome: accepted | tooShort | notInList | hardMode | gameOver. */
export function submit(game, isValid) {
  if (status(game) !== 'playing') return { game, outcome: 'gameOver' }
  if (game.current.length < WORD_LENGTH) return { game, outcome: 'tooShort' }
  if (!isValid(game.current)) return { game, outcome: 'notInList' }
  if (game.hard) {
    const msg = hardModeViolation(game.guesses, game.answer, game.current)
    if (msg) return { game, outcome: 'hardMode', message: msg }
  }
  return { game: { ...game, guesses: [...game.guesses, game.current], current: '' }, outcome: 'accepted' }
}

/** 0-based index of the first answer letter not yet placed correctly, or -1. */
export function hintPosition(game) {
  const solved = new Array(game.answer.length).fill(false)
  for (const g of game.guesses) for (let i = 0; i < g.length; i++) if (g[i] === game.answer[i]) solved[i] = true
  return solved.indexOf(false)
}

export function generate(lists, difficulty, seed) {
  const d = String(difficulty).toLowerCase()
  const pool = d === 'easy' ? lists.answerPool(0, 700) : d === 'hard' ? lists.answerPool(700, 1700) : lists.answerPool(0, 1400)
  if (!pool.length) throw new Error('word list unavailable')
  const answer = pool[createRng(seed).int(pool.length)]
  return { game: 'wordguess', answer, hard: d === 'hard', seed, difficulty }
}
