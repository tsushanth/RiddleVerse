import { hashString } from './common.js'

/**
 * Small seeded PRNG (mulberry32). Deterministic for a given seed on every platform.
 * Mirrors the subset of kotlin.random.Random the Android engines use.
 */
export function makeRng(seed) {
  let a = hashString(seed) || 0x9e3779b9
  const next = () => {
    a = (a + 0x6d2b79f5) | 0
    let t = Math.imul(a ^ (a >>> 15), 1 | a)
    t = (t + Math.imul(t ^ (t >>> 7), 61 | t)) ^ t
    return ((t ^ (t >>> 14)) >>> 0) / 4294967296
  }
  return {
    nextDouble: next,
    nextInt: (n) => Math.floor(next() * n),
    shuffle(arr) {
      for (let i = arr.length - 1; i > 0; i--) {
        const j = Math.floor(next() * (i + 1))
        const t = arr[i]
        arr[i] = arr[j]
        arr[j] = t
      }
      return arr
    },
  }
}
