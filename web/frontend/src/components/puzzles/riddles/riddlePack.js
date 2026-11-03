// Pure rules for the Riddles game: stars, points, tier mapping, share text, per-device streak. Mirrors Android RiddlePack.kt.

export const TYPE = 'riddles'
export const MAX_STARS = 3

/** Same Play listing as Android, tagged as coming from the web riddle card. */
export const PLAY_LINK =
  'https://play.google.com/store/apps/details?id=com.kreativekoala.riddleverse&utm_source=share&utm_medium=web_riddle&utm_campaign=card'

/** 3 stars with no hint, then one star less per hint, never below 1 for a solved riddle. Hints themselves are free. */
export const stars = (hintsShown) => Math.min(MAX_STARS, Math.max(1, MAX_STARS - hintsShown))

const TIER_BONUS = { easy: 0, medium: 5, hard: 10 }

/** Session points: 10 per star, plus a bonus for harder tiers. Only solved riddles score. */
export const points = (tier, hintsShown, solved) => (solved ? stars(hintsShown) * 10 + (TIER_BONUS[tier] ?? 0) : 0)

/** Accepts the app's names (Tutorial, Beginner, Easy+, Hard-, Expert ...) and the web's numeric levels 1..3. */
export function tierFromDifficulty(d) {
  if (typeof d === 'number' && Number.isFinite(d)) return d <= 1 ? 'easy' : d >= 3 ? 'hard' : 'medium'
  const s = String(d ?? '').trim().toLowerCase()
  if (/^\d+$/.test(s)) return tierFromDifficulty(parseInt(s, 10))
  if (/^(easy|beginner|tutorial)/.test(s)) return 'easy'
  if (/^(hard|expert|master|extreme)/.test(s)) return 'hard'
  return 'medium'
}

export const tierFromPuzzle = (puzzle) => tierFromDifficulty(puzzle?.difficulty)

/** Text only: the riddle and the Play link, never the answer or the hints. */
export const shareText = (riddle) =>
  `Can you solve this riddle?\n\n${riddle.question}\n\nPlay more riddles in RiddleVerse: ${PLAY_LINK}`

// ---- per-device stats ------------------------------------------------------------------------------------------

const ZERO = { played: 0, solved: 0, streak: 0, best: 0 }

export function loadStats(store) {
  try {
    const o = JSON.parse(store.getString('stats') || 'null')
    if (!o || typeof o !== 'object') return { ...ZERO }
    const n = (v) => (Number.isFinite(v) && v >= 0 ? Math.floor(v) : 0)
    return { played: n(o.played), solved: n(o.solved), streak: n(o.streak), best: n(o.best) }
  } catch {
    return { ...ZERO }
  }
}

/** A solved riddle extends the streak; a skipped or revealed one resets it. */
export function recordResult(store, solved) {
  const s = loadStats(store)
  const next = {
    played: s.played + 1,
    solved: s.solved + (solved ? 1 : 0),
    streak: solved ? s.streak + 1 : 0,
    best: Math.max(s.best, solved ? s.streak + 1 : 0),
  }
  try { store.putString('stats', JSON.stringify(next)) } catch { /* ignore */ }
  return next
}
