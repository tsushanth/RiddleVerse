// Daily puzzle schedule: a JS port of Android DailyPuzzleSchedule.kt. Pure functions of the clock, so every
// visitor (and every Android phone) computes the same game and number for the same UTC day with no backend.
//
// COMPATIBILITY WITH ANDROID: the game, the number (Daily #N) and the 63-bit seed per day are bit-identical to
// Android (pinned by test/daily/schedule.test.mjs against the Kotlin golden vectors). The BOARD is not: the web
// ports generate boards with mulberry32/FNV (and the Kotlin app with XorWow), so the same seed produces a different
// board on each platform. Share text therefore carries only the game and the number, never a board or an answer.
//
// FIRST_DAY and ROTATION are "never edit after release": changing either renumbers or reshuffles every day.
// SplitMix64 needs unsigned 64-bit wrap-around, so all hashing below uses BigInt masked to 64 bits.

export const DAY_MS = 86_400_000

/** Daily #1: 2026-11-01 as a UTC epoch day. Never edit after release. */
export const FIRST_DAY = Math.floor(Date.UTC(2026, 10, 1) / DAY_MS)

/** Same members and order as Android. Never edit after release (reshuffles every future day). */
export const ROTATION = Object.freeze([
  'sudoku', 'binarygrid', 'signgrid', 'pixellogic', 'tubetint', 'conduit', 'crateshift', 'wordladder', 'wordhive',
])

/** One fixed level for everybody, matching Android. */
export const DIFFICULTY = 'Medium'

const MASK = (1n << 64n) - 1n
const u64 = (x) => BigInt.asUintN(64, x)
// Kotlin's -0x61c8864680b583ebL, -0x40a7b892e31b1a47L, -0x6b2fb644ecceee15L as unsigned 64-bit.
const GOLDEN = 0x9E3779B97F4A7C15n
const MIX1 = 0xBF58476D1CE4E5B9n
const MIX2 = 0x94D049BB133111EBn
const SHUFFLE_ADD = 0x5851F42D4C957F2Dn

/** SplitMix64: tiny, fully specified generator. State and outputs are unsigned 64-bit BigInts. */
export class SplitMix64 {
  constructor(state) { this.state = u64(BigInt(state)) }
  next() {
    this.state = u64(this.state + GOLDEN)
    let z = this.state
    z = u64((z ^ (z >> 30n)) * MIX1)
    z = u64((z ^ (z >> 27n)) * MIX2)
    return (z ^ (z >> 31n)) & MASK
  }
}

/** UTC epoch day for an instant in milliseconds. Floors, so instants before 1970 stay correct. */
export const dayOf = (epochMillis) => Math.floor(epochMillis / DAY_MS)

/** "Daily #N": 1 on FIRST_DAY. */
export const numberOf = (day) => day - FIRST_DAY + 1

/** Milliseconds until the next puzzle (the next UTC midnight). Always in 1..DAY_MS. */
export const millisUntilNext = (nowMillis) => (dayOf(nowMillis) + 1) * DAY_MS - nowMillis

const floorMod = (a, n) => ((a % n) + n) % n

function shuffled(cycle) {
  const list = [...ROTATION]
  const rng = new SplitMix64(u64(BigInt(cycle) * GOLDEN + SHUFFLE_ADD))
  for (let i = list.length - 1; i >= 0; i--) { // Fisher-Yates, same draw order as Kotlin (i = 0 still consumes one)
    const j = Number((rng.next() >> 1n) % BigInt(i + 1))
    const t = list[i]; list[i] = list[j]; list[j] = t
  }
  return list
}

/** Each cycle of ROTATION.length days plays every game once, in a per-cycle shuffled order. */
export function order(cycle) {
  const current = shuffled(cycle)
  // Never the same game on the last day of one cycle and the first day of the next. Only positions 0 and 1 are
  // ever swapped, so the raw shuffle of the previous cycle has the right last slot.
  if (current[0] === shuffled(cycle - 1)[ROTATION.length - 1]) {
    const t = current[0]; current[0] = current[1]; current[1] = t
  }
  return current
}

/** Positive 63-bit seed (BigInt) derived from the day and the game. Explicit mixing, never String.hashCode. */
export function seedFor(day, type) {
  let h = 1125899906842597n
  for (const ch of type) h = u64(31n * h + BigInt(ch.codePointAt(0)))
  return new SplitMix64(u64(BigInt(day) * GOLDEN ^ h)).next() & ((1n << 63n) - 1n)
}

/** The puzzle for a UTC epoch day. Pure and stable: the same day always maps to the same spec. */
export function specFor(day) {
  const index = day - FIRST_DAY
  const cycle = Math.floor(index / ROTATION.length)
  const slot = floorMod(index, ROTATION.length)
  const type = order(cycle)[slot]
  return { day, number: numberOf(day), type, seed: seedFor(day, type) }
}

/**
 * The web ports take a 31-bit positive seed (see grid/engine/factory.js randomSeed). Folding the 63-bit schedule
 * seed keeps it deterministic per day. This is web-only and independent of Android's engines.
 */
export function webSeedFor(spec) {
  return Number(spec.seed % 2147483646n) + 1
}
