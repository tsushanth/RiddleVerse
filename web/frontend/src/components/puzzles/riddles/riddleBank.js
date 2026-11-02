// Content model + loader for web/shared/riddles_v1.json (a verbatim copy of the Android asset). The 93 KB file is a
// dynamic import so it never lands in the main bundle; it is fetched once when the riddle game first mounts.

export const TIERS = ['easy', 'medium', 'hard']
export const TIER_LABELS = { easy: 'Easy', medium: 'Medium', hard: 'Hard' }

/** Throws if a required field is missing or malformed; parseBank skips such entries. */
export function parseRiddle(o) {
  if (!o || typeof o !== 'object') throw new Error('entry')
  const tier = String(o.tier || '').toLowerCase()
  if (!TIERS.includes(tier)) throw new Error('tier')
  for (const k of ['id', 'q', 'a', 'why']) if (typeof o[k] !== 'string') throw new Error(k)
  const hints = Array.isArray(o.hints) ? o.hints : []
  if (hints.length !== 3 || !hints.every((h) => typeof h === 'string')) throw new Error('hints')
  const alternates = Array.isArray(o.alt) ? o.alt.filter((x) => typeof x === 'string') : []
  return {
    id: o.id,
    tier,
    question: o.q,
    answer: o.a,
    alternates,
    hints,
    explanation: o.why,
    /** Answer as shown to the player ("deck of cards" -> "Deck of cards"). */
    displayAnswer: o.a.charAt(0).toUpperCase() + o.a.slice(1),
  }
}

export function parseBank(json) {
  const arr = Array.isArray(json?.riddles) ? json.riddles : []
  const out = []
  for (const o of arr) {
    try { out.push(parseRiddle(o)) } catch { /* skip malformed */ }
  }
  return out
}

let cache = null

/** Loads the bank once (separate chunk). A failed load is not cached, so the caller can retry. */
export function loadBank() {
  if (cache) return cache
  cache = import('../../../../../shared/riddles_v1.json')
    .then((m) => {
      const bank = parseBank(m.default ?? m)
      if (bank.length === 0) throw new Error('riddle bank is empty')
      return bank
    })
    .catch((e) => { cache = null; throw e })
  return cache
}
