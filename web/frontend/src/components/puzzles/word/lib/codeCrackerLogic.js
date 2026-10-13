// Code Cracker (Mastermind-style). Mirrors CodeCrackerLogic.kt. Colours are ints 0..colors-1.
import { createRng, shuffled } from './prng.js'

export const COLOR_NAMES = ['Red', 'Blue', 'Green', 'Yellow', 'Purple', 'Orange']
/** One distinct symbol per colour so colour is never the only cue. */
export const COLOR_SYMBOLS = ['●', '▲', '■', '★', '◆', '✖']
export const COLOR_HEX = ['#e53935', '#1e88e5', '#43a047', '#fdd835', '#8e24aa', '#fb8c00']

/** exact = right colour right slot, misplaced = right colour wrong slot. Handles repeats. */
export function feedback(secret, guess) {
  if (secret.length !== guess.length) throw new Error('length mismatch')
  let exact = 0
  const sc = {}
  const gc = {}
  for (let i = 0; i < secret.length; i++) {
    if (secret[i] === guess[i]) exact++
    else {
      sc[secret[i]] = (sc[secret[i]] || 0) + 1
      gc[guess[i]] = (gc[guess[i]] || 0) + 1
    }
  }
  let misplaced = 0
  for (const c of Object.keys(gc)) misplaced += Math.min(gc[c], sc[c] || 0)
  return { exact, misplaced }
}

export function config(difficulty) {
  const d = String(difficulty).toLowerCase()
  if (d === 'easy') return { positions: 4, colors: 5, allowRepeats: false, maxTries: 10 }
  if (d === 'hard') return { positions: 5, colors: 6, allowRepeats: true, maxTries: 10 }
  return { positions: 4, colors: 6, allowRepeats: true, maxTries: 10 }
}

export function consistent(candidate, history) {
  return history.every(([g, fb]) => {
    const f = feedback(candidate, g)
    return f.exact === fb.exact && f.misplaced === fb.misplaced
  })
}

export function score(triesUsed, maxTries, hintsUsed) {
  return Math.max(50 + (maxTries - triesUsed) * 12 - hintsUsed * 20, 10)
}

export function generate(difficulty, seed) {
  const cfg = config(difficulty)
  const rng = createRng(seed)
  const secret = cfg.allowRepeats
    ? Array.from({ length: cfg.positions }, () => rng.int(cfg.colors))
    : shuffled([...Array(cfg.colors).keys()], rng).slice(0, cfg.positions)
  return {
    game: 'codecracker', secret, positions: cfg.positions, colors: cfg.colors,
    repeats: cfg.allowRepeats, tries: cfg.maxTries, seed, difficulty,
  }
}

/** Spoiler-free share text. */
export function shareText(guesses, feedbacks, won, maxTries) {
  const head = `RiddleVerse Code Cracker ${won ? guesses.length : 'X'}/${maxTries}`
  const rows = feedbacks.map((f) => '⚫'.repeat(f.exact) + '⚪'.repeat(f.misplaced) || '·')
  return `${head}\n\n${rows.join('\n')}`
}
