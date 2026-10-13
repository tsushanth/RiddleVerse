// Word Hive: 7 letters, required centre letter, words of 4+ letters. Mirrors WordHiveLogic.kt.
import { createRng, shuffled } from './prng.js'

export const MIN_WORD = 4
export const PANGRAM_BONUS = 7

/** [name, minimum percent of the maximum score], ascending. */
export const RANKS = [
  ['Newcomer', 0], ['Getting Going', 2], ['Buzzing', 5], ['Steady', 8], ['Solid', 15],
  ['Sharp', 25], ['Great', 40], ['Amazing', 50], ['Genius', 70], ['Queen of the Hive', 100],
]

const cmp = (a, b) => (a < b ? -1 : a > b ? 1 : 0)

export function isPangram(word, letters) {
  const set = new Set(word)
  if (set.size !== letters.size) return false
  for (const c of set) if (!letters.has(c)) return false
  return true
}

export function wordScore(word, letters) {
  return (word.length === MIN_WORD ? 1 : word.length) + (isPangram(word, letters) ? PANGRAM_BONUS : 0)
}

/** Every word buildable from [letters] that contains [center]; sorted, distinct. */
export function solutions(common, letters, center) {
  const out = new Set()
  for (const w of common) {
    if (w.length >= MIN_WORD && w.includes(center) && [...w].every((c) => letters.has(c))) out.add(w)
  }
  return [...out].sort(cmp)
}

export function rankIndex(score, maxScore) {
  if (maxScore <= 0) return 0
  const pct = (score * 100) / maxScore
  let idx = 0
  RANKS.forEach((r, i) => {
    if (pct + 1e-9 >= r[1]) idx = i
  })
  return idx
}

export function targetRankIndex(difficulty) {
  const d = String(difficulty).toLowerCase()
  return d === 'easy' ? 6 : d === 'hard' ? 8 : 7
}

export function rejection(entry, letters, center, solutionSet, found) {
  if (entry.length < MIN_WORD) return 'Too short'
  if ([...entry].some((c) => !letters.has(c))) return 'Bad letters'
  if (!entry.includes(center)) return 'Missing centre letter'
  if (found.includes(entry)) return 'Already found'
  if (!solutionSet.has(entry)) return 'Not in word list'
  return null
}

function window_(difficulty) {
  const d = String(difficulty).toLowerCase()
  return d === 'easy' ? [18, 40] : d === 'hard' ? [40, 90] : [25, 60]
}

/** Deterministic; returns null only for a degenerate word list. */
export function generate(lists, difficulty, seed) {
  const common = lists.common.filter((w) => w.length >= 4 && w.length <= 12 && !w.includes('s'))
  const seen = new Set()
  const seeds = []
  for (const w of common) {
    if (w.length < 7 || new Set(w).size !== 7) continue
    const key = [...new Set(w)].sort().join('')
    if (seen.has(key)) continue
    seen.add(key)
    seeds.push(w)
    if (seeds.length >= 4000) break
  }
  seeds.sort(cmp)
  if (!seeds.length) return null
  const rng = createRng(seed)
  const order = shuffled(seeds.map((_, i) => i), rng)
  const [lo, hi] = window_(difficulty)
  let best = null
  let bestGap = Infinity
  for (let tries = 0; tries < order.length && tries < 150; tries++) {
    const set = new Set(seeds[order[tries]])
    const sorted = [...set].sort()
    const center = sorted[rng.int(7)]
    const sols = solutions(common, set, center)
    if (!sols.some((w) => isPangram(w, set))) continue
    const gap = sols.length < lo ? lo - sols.length : sols.length > hi ? sols.length - hi : 0
    const p = {
      game: 'wordhive',
      center,
      outer: sorted.filter((c) => c !== center).join(''),
      words: sols,
      maxScore: sols.reduce((a, w) => a + wordScore(w, set), 0),
      seed,
      difficulty,
    }
    if (gap === 0) return p
    if (gap < bestGap) {
      best = p
      bestGap = gap
    }
  }
  return best
}
