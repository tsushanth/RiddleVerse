// Single place that decides whether a multiple-choice answer is right (Trivia, Math, Q&A options, Sentence
// Transitions). Pure JS so it can be unit-tested with `node --test`.
//
// Why it exists: the Android multiple-choice screen passed `true` to its result callback instead of the tapped
// option, so every answer scored wrong. The web always compared the tapped option, but each component repeated
// `option.toLowerCase().trim() === answer.toLowerCase().trim()`, which (a) throws if an option or the key arrives as
// a number, (b) scores "42" vs "42.0" as wrong, and (c) treated a blank option as correct when the key was blank.

const clean = (v) => (v === null || v === undefined || typeof v === 'boolean' ? '' : String(v).trim())

/** Trim + case-insensitive; numbers compare by value. Two blanks never match. */
export function sameAnswer(a, b) {
  const x = clean(a)
  const y = clean(b)
  if (!x || !y) return false
  if (x.toLowerCase() === y.toLowerCase()) return true
  const nx = Number(x.replace(/,/g, ''))
  const ny = Number(y.replace(/,/g, ''))
  return Number.isFinite(nx) && Number.isFinite(ny) && nx === ny
}

/** `selected` must be the tapped option; anything else (e.g. a boolean) is wrong. */
export function isCorrectChoice(selected, correct) {
  return sameAnswer(selected, correct)
}
