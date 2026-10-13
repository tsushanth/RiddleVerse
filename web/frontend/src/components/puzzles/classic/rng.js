// --- web classic puzzles (feat/web-puzzles-classic) --- seeded PRNG helpers (pure, no DOM)

/** mulberry32: small, fast, deterministic PRNG. Returns a function giving floats in [0, 1). */
export function mulberry32(seed) {
  let a = seed >>> 0
  return function next() {
    a = (a + 0x6d2b79f5) >>> 0
    let t = a
    t = Math.imul(t ^ (t >>> 15), t | 1)
    t ^= t + Math.imul(t ^ (t >>> 7), t | 61)
    return ((t ^ (t >>> 14)) >>> 0) / 4294967296
  }
}

/** Mixes two integers into one 32-bit seed. */
export function hashSeed(a, b) {
  let h = Math.imul((a | 0) ^ 0x9e3779b9, 0x85ebca6b)
  h = Math.imul(h ^ (h >>> 13) ^ (b | 0), 0xc2b2ae35)
  return (h ^ (h >>> 16)) >>> 0
}

export function randInt(rng, n) {
  return Math.floor(rng() * n)
}

/** Fisher-Yates on a copy. */
export function shuffled(arr, rng) {
  const a = arr.slice()
  for (let i = a.length - 1; i > 0; i--) {
    const j = randInt(rng, i + 1)
    const t = a[i]
    a[i] = a[j]
    a[j] = t
  }
  return a
}

export function randomSeed() {
  try {
    if (typeof crypto !== 'undefined' && crypto.getRandomValues) {
      return crypto.getRandomValues(new Uint32Array(1))[0]
    }
  } catch { /* fall through */ }
  return Math.floor(Math.random() * 4294967296) >>> 0
}

export function popcount(x) {
  let c = 0
  let v = x >>> 0
  while (v) {
    v &= v - 1
    c++
  }
  return c
}
