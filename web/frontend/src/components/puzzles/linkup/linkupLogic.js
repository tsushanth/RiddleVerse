// web/game-linkup: pure rules for Linkup (port of Android LinkupLogic.kt LinkupRules). No React, no storage.
// 16 tiles, find the 4 hidden groups of 4, 4 mistakes allowed.
import { createRng, shuffled, hashSeed } from '../word/lib/prng.js'

export const GROUP_SIZE = 4
export const MAX_MISTAKES = 4
export const GROUPS = 4

export const PLAY_LINK =
  'https://play.google.com/store/apps/details?id=com.kreativekoala.riddleverse&utm_source=share&utm_medium=web_linkup&utm_campaign=card'

/** @returns {{solved:number[], selected:string[], mistakes:number, hints:number, hinted:string[], tried:string[], shuffle:number, gaveUp:boolean}} */
export function newState() {
  return { solved: [], selected: [], mistakes: 0, hints: 0, hinted: [], tried: [], shuffle: 0, gaveUp: false }
}

/** difficulty: 'easy' | 'medium' | 'hard' (anything else is medium). closeFeedback = say when a guess was one tile away. */
export function profile(difficulty) {
  switch (String(difficulty || '').toLowerCase()) {
    case 'easy': return { maxHints: 3, closeFeedback: true, baseScore: 80 }
    case 'hard': return { maxHints: 1, closeFeedback: false, baseScore: 120 }
    default: return { maxHints: 2, closeFeedback: true, baseScore: 100 }
  }
}

export const tierName = (tier) => ({ 1: 'Easy', 2: 'Medium', 3: 'Tricky' })[tier] || 'Very tricky'
/** Shape marker per tier so the tier is never conveyed by colour alone. */
export const tierGlyph = (tier) => ({ 1: '●', 2: '▲', 3: '■' })[tier] || '★'

export const puzzleWords = (p) => p.groups.flatMap((g) => g.words)
export const groupIndexOf = (p, word) => p.groups.findIndex((g) => g.words.includes(word))

export const isWon = (s) => s.solved.length >= GROUPS
export const isLost = (s) => !isWon(s) && (s.mistakes >= MAX_MISTAKES || s.gaveUp)
export const isOver = (s) => isWon(s) || isLost(s)
export const mistakesLeft = (s) => Math.max(0, MAX_MISTAKES - s.mistakes)
export const hintsLeft = (s, prof) => Math.max(0, prof.maxHints - s.hints)

/** Tiles still in play, in a stable shuffled order (the shuffle button bumps state.shuffle). */
export function available(p, s) {
  const solvedWords = new Set(s.solved.flatMap((i) => p.groups[i].words))
  const order = shuffled(puzzleWords(p), createRng(hashSeed(`${p.id}:${s.shuffle}`)))
  return order.filter((w) => !solvedWords.has(w))
}

export function toggle(p, s, word) {
  if (isOver(s) || !available(p, s).includes(word)) return s
  if (s.selected.includes(word)) return { ...s, selected: s.selected.filter((w) => w !== word) }
  if (s.selected.length >= GROUP_SIZE) return s
  return { ...s, selected: [...s.selected, word] }
}

export const deselectAll = (s) => ({ ...s, selected: [] })
export const shuffle = (s) => ({ ...s, shuffle: s.shuffle + 1 })
export const giveUp = (s) => (isOver(s) ? s : { ...s, gaveUp: true, selected: [] })

const key = (words) => [...words].sort().join(',')

/** @returns {{state, result: 'SOLVED'|'CLOSE'|'WRONG'|'ALREADY_TRIED'|'INCOMPLETE'|'FINISHED'}} */
export function submit(p, s) {
  if (isOver(s)) return { state: s, result: 'FINISHED' }
  if (s.selected.length !== GROUP_SIZE) return { state: s, result: 'INCOMPLETE' }
  const k = key(s.selected)
  if (s.tried.includes(k)) return { state: s, result: 'ALREADY_TRIED' }
  const counts = new Map()
  for (const w of s.selected) {
    const g = groupIndexOf(p, w)
    counts.set(g, (counts.get(g) || 0) + 1)
  }
  let bestGroup = -1
  let bestCount = 0
  for (const [g, c] of counts) if (c > bestCount) { bestGroup = g; bestCount = c }
  if (bestCount === GROUP_SIZE && !s.solved.includes(bestGroup)) {
    return { state: { ...s, solved: [...s.solved, bestGroup], selected: [] }, result: 'SOLVED' }
  }
  return {
    state: { ...s, mistakes: s.mistakes + 1, tried: [...s.tried, k] },
    result: bestCount === GROUP_SIZE - 1 ? 'CLOSE' : 'WRONG',
  }
}

/**
 * Marks one tile of the easiest unsolved group, preferring a tile that is not a declared red herring and one not
 * already shown. Returns { state, word } with word === null when no hint is left.
 */
export function hint(p, s, prof) {
  if (isOver(s) || hintsLeft(s, prof) === 0) return { state: s, word: null }
  const open = p.groups.map((_, i) => i).filter((i) => !s.solved.includes(i))
  if (!open.length) return { state: s, word: null }
  const idx = open.reduce((a, b) => (p.groups[b].tier < p.groups[a].tier ? b : a))
  const candidates = p.groups[idx].words.filter((w) => !s.hinted.includes(w))
  const word = candidates.find((w) => !p.redHerrings.has(w)) ?? candidates[0]
  if (word === undefined) return { state: s, word: null }
  return { state: { ...s, hints: s.hints + 1, hinted: [...s.hinted, word] }, word }
}

export const score = (s, prof) => (isWon(s) ? Math.max(10, prof.baseScore - 10 * s.mistakes - 10 * s.hints) : 0)

/** Groups not found yet, easiest first (revealed when the game is lost). */
export function unsolvedGroups(p, s) {
  return p.groups.filter((_, i) => !s.solved.includes(i)).sort((a, b) => a.tier - b.tier)
}

/** Spoiler-free: counts, the tiers found in solve order as shapes, and the Play link. */
export function shareText(p, s) {
  const plural = (n, w) => `${n} ${w}${n === 1 ? '' : 's'}`
  const found = s.solved.map((i) => tierGlyph(p.groups[i].tier)).join(' ')
  const head = `RiddleVerse Linkup: ${s.solved.length}/${GROUPS} groups, ${plural(s.mistakes, 'mistake')}, ${plural(s.hints, 'hint')}`
  return `${head}${found ? `\n${found}` : ''}\n${PLAY_LINK}`
}
