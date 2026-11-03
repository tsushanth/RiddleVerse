// Answer matching for typed riddle answers. A faithful port of Android RiddleMatcher.kt (feat/riddle-pack);
// scripts/riddles/riddle_norm.py is the third mirror and test/riddles/golden.json pins all three together.
// Deliberately stricter than a fuzzy matcher: a riddle has ONE answer, so "tree" must not be accepted for "oak".

/** Leading filler words that are dropped ("it is a clock", "the clock"). */
const FILLERS = new Set(['a', 'an', 'the', 'it', 'its', 'is', 'are', 'they', 'theyre', 'i', 'think', 'maybe', 'this', 'that', 'answer'])

/** Lower-case, accent-free, apostrophes removed, punctuation -> space, leading fillers dropped. */
export function normalize(s) {
  const nfd = String(s ?? '').toLowerCase().normalize('NFD')
  let out = ''
  for (const c of nfd) {
    if (/\p{Mn}/u.test(c)) continue
    if (c === "'" || c === '’') continue
    if (c === '&') out += ' and '
    else if ((c >= 'a' && c <= 'z') || (c >= '0' && c <= '9')) out += c
    else out += ' '
  }
  let toks = out.split(' ').filter((t) => t.length > 0)
  while (toks.length > 1 && FILLERS.has(toks[0])) toks = toks.slice(1)
  return toks
}

/** A token plus its simple singular forms, so "cities" ~ "city", "boxes" ~ "box", "leaves" ~ "leaf". */
export function variants(t) {
  const v = new Set([t])
  if (t.length > 4 && t.endsWith('ies')) v.add(t.slice(0, -3) + 'y')
  if (t.length > 4 && t.endsWith('ves')) { v.add(t.slice(0, -3) + 'f'); v.add(t.slice(0, -3) + 'fe') }
  if (t.length > 3 && t.endsWith('es')) v.add(t.slice(0, -2))
  if (t.length > 2 && t.endsWith('s') && !t.endsWith('ss')) v.add(t.slice(0, -1))
  return v
}

/** Same words in the same order, each pair equal up to a plural form. */
export function same(a, b) {
  const ta = normalize(a)
  const tb = normalize(b)
  if (ta.length === 0 || ta.length !== tb.length) return false
  return ta.every((t, i) => {
    const vb = variants(tb[i])
    for (const x of variants(t)) if (vb.has(x)) return true
    return false
  })
}

const compact = (s) => normalize(s).join('')

/** Edits tolerated for a typo: none for short answers and numbers, one for 7+ letters, two for 12+. */
export function typoBudget(accepted) {
  const c = compact(accepted)
  if (normalize(accepted).some((t) => t.length === 1)) return 0 // "the letter M" must not accept "the letter R"
  if (c.length < 7 || /^[0-9]+$/.test(c)) return 0
  if (c.length >= 12) return 2
  return 1
}

export function matchesForm(input, accepted) {
  if (same(input, accepted)) return true
  const u = compact(input)
  const c = compact(accepted)
  if (u.length === 0 || c.length === 0) return false
  if (u === c) return true // "tea pot" ~ "teapot"
  const budget = typoBudget(accepted)
  return budget > 0 && Math.abs(u.length - c.length) <= budget && editDistance(u, c) <= budget
}

export function isCorrect(input, riddle) {
  return [riddle.answer, ...(riddle.alternates || [])].some((a) => matchesForm(input, a))
}

/** Optimal string alignment distance: insert, delete, substitute or swap two neighbouring letters, each costs 1. */
export function editDistance(a, b) {
  const d = Array.from({ length: a.length + 1 }, () => new Array(b.length + 1).fill(0))
  for (let i = 0; i <= a.length; i++) d[i][0] = i
  for (let j = 0; j <= b.length; j++) d[0][j] = j
  for (let i = 1; i <= a.length; i++) {
    for (let j = 1; j <= b.length; j++) {
      const cost = a[i - 1] === b[j - 1] ? 0 : 1
      d[i][j] = Math.min(d[i - 1][j] + 1, d[i][j - 1] + 1, d[i - 1][j - 1] + cost)
      if (i > 1 && j > 1 && a[i - 1] === b[j - 2] && a[i - 2] === b[j - 1]) d[i][j] = Math.min(d[i][j], d[i - 2][j - 2] + 1)
    }
  }
  return d[a.length][b.length]
}
