// Word data shared by Word Guess, Word Ladder, Word Hive.
//
// SOURCES / LICENCES (same data as the Android app, public/word_puzzles/):
//  - valid5.txt: every 5-letter entry of the app's existing words.db (re-used, exported as text).
//  - common_words.txt: words 3..12 letters long that appear BOTH in words.db and in Peter Norvig's
//    count_1w.txt (Google Web Trillion Word Corpus unigram counts, https://norvig.com/ngrams/,
//    MIT licence), ordered by descending frequency (line order == frequency rank), capped at
//    42,000 lines. Only ordering and words are used; no counts are shipped.
// Offensive words are removed with blocklist.js. No third-party network access at runtime.

import { isBlocked } from './blocklist.js'

export function buildLists(commonRaw, valid5Raw) {
  const common = []
  const commonSet = new Set()
  for (const line of commonRaw) {
    const w = String(line).trim().toLowerCase()
    if (!w || !/^[a-z]+$/.test(w) || isBlocked(w) || commonSet.has(w)) continue
    common.push(w)
    commonSet.add(w)
  }
  const commonByLength = new Map()
  for (const w of common) {
    if (!commonByLength.has(w.length)) commonByLength.set(w.length, [])
    commonByLength.get(w.length).push(w)
  }
  const valid5 = new Set(commonByLength.get(5) || [])
  for (const line of valid5Raw) {
    const w = String(line).trim().toLowerCase()
    if (w.length === 5 && /^[a-z]{5}$/.test(w)) valid5.add(w)
  }
  const top = (length, limit) => (commonByLength.get(length) || []).slice(0, limit)
  /** Frequent 5-letter words that are not simple "-s" plurals of a shorter common word. */
  const answerPool = (fromRank, toRank) => {
    const fives = (commonByLength.get(5) || []).filter(
      (w) => !(w.endsWith('s') && !w.endsWith('ss') && commonSet.has(w.slice(0, -1)))
    )
    if (!fives.length) return []
    const from = Math.min(Math.max(fromRank, 0), fives.length)
    const to = Math.min(Math.max(toRank, from), fives.length)
    return fives.slice(from, to)
  }
  return { common, commonSet, commonByLength, valid5, top, answerPool }
}

let cached = null

/** Lazily fetches and caches both lists (one in-flight promise, retried after failure). */
export function loadWordLists(baseUrl = '/word_puzzles/', fetchImpl = globalThis.fetch) {
  if (cached) return cached
  const get = async (name) => {
    const res = await fetchImpl(baseUrl + name)
    if (!res.ok) throw new Error(`Failed to load ${name} (${res.status})`)
    return (await res.text()).split(/\r?\n/)
  }
  cached = Promise.all([get('common_words.txt'), get('valid5.txt')])
    .then(([c, v]) => buildLists(c, v))
    .catch((e) => {
      cached = null
      throw e
    })
  return cached
}
