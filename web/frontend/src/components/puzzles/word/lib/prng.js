// Small seeded PRNG (mulberry32). Deterministic for a given seed on every platform.

export function hashSeed(input) {
  const s = String(input)
  let h = 2166136261
  for (let i = 0; i < s.length; i++) {
    h ^= s.charCodeAt(i)
    h = Math.imul(h, 16777619)
  }
  return h >>> 0
}

export function createRng(seed) {
  let a = (typeof seed === 'number' ? seed >>> 0 : hashSeed(seed)) || 0x9e3779b9
  const next = () => {
    a = (a + 0x6d2b79f5) >>> 0
    let t = a
    t = Math.imul(t ^ (t >>> 15), t | 1)
    t ^= t + Math.imul(t ^ (t >>> 7), t | 61)
    return ((t ^ (t >>> 14)) >>> 0) / 4294967296
  }
  return {
    next,
    /** integer in [0, n) */
    int: (n) => Math.floor(next() * n),
  }
}

/** Fisher-Yates; returns a new array. */
export function shuffled(arr, rng) {
  const out = arr.slice()
  for (let i = out.length - 1; i > 0; i--) {
    const j = rng.int(i + 1)
    const t = out[i]
    out[i] = out[j]
    out[j] = t
  }
  return out
}

export function normalizeDifficulty(d) {
  if (d === 1 || d === '1') return 'easy'
  if (d === 3 || d === '3') return 'hard'
  if (d === 2 || d === '2') return 'medium'
  const s = String(d || 'medium').toLowerCase()
  return s === 'easy' || s === 'hard' ? s : 'medium'
}
