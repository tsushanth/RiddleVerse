// Seeded PRNG (mulberry32) + helpers. Same seed => same sequence.

export function seedToUint32(seed) {
  const s = Number.isFinite(Number(seed)) ? Math.floor(Math.abs(Number(seed))) : 0
  const lo = s % 4294967296
  const hi = Math.floor(s / 4294967296)
  return ((lo ^ Math.imul(hi + 0x9e3779b9, 0x85ebca6b)) >>> 0)
}

export class Rng {
  constructor(seed) {
    this.state = seedToUint32(seed) || 0x1234567
  }

  /** float in [0,1) */
  next() {
    let t = (this.state = (this.state + 0x6d2b79f5) >>> 0)
    t = Math.imul(t ^ (t >>> 15), t | 1)
    t ^= t + Math.imul(t ^ (t >>> 7), t | 61)
    return ((t ^ (t >>> 14)) >>> 0) / 4294967296
  }

  /** int in [0,n) */
  int(n) {
    return Math.floor(this.next() * n)
  }

  bool() {
    return this.next() < 0.5
  }

  /** Fisher-Yates; returns a new array. */
  shuffled(arr) {
    const a = Array.from(arr)
    for (let i = a.length - 1; i > 0; i--) {
      const j = this.int(i + 1)
      const t = a[i]
      a[i] = a[j]
      a[j] = t
    }
    return a
  }
}

export function range(n) {
  return Array.from({ length: n }, (_, i) => i)
}

export function bitCount(x) {
  let c = 0
  while (x) {
    x &= x - 1
    c++
  }
  return c
}

/** Random-looking latin square via permuted cyclic square. Values 1..n. */
export function latinSquare(n, rnd) {
  const rowPerm = rnd.shuffled(range(n))
  const colPerm = rnd.shuffled(range(n))
  const symPerm = rnd.shuffled(range(n).map((i) => i + 1))
  const g = new Array(n * n)
  for (let r = 0; r < n; r++) for (let c = 0; c < n; c++) g[r * n + c] = symPerm[(rowPerm[r] + colPerm[c]) % n]
  return g
}
