import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const here = path.dirname(fileURLToPath(import.meta.url))
export const rawBank = fs.readFileSync(path.resolve(here, '..', '..', '..', 'shared', 'riddles_v1.json'), 'utf8')
export const golden = JSON.parse(fs.readFileSync(path.join(here, 'golden.json'), 'utf8'))

/** Same shape the app uses after parseBank(). */
export function riddle(answer, ...alt) {
  return { id: 'e000', tier: 'easy', question: 'Q?', answer, alternates: alt, hints: ['a', 'b', 'c'], explanation: 'why' }
}

export class MemStore {
  constructor() { this.m = new Map() }
  getString(k) { return this.m.has(k) ? this.m.get(k) : null }
  putString(k, v) { this.m.set(k, v) }
}

/** Small deterministic PRNG (mulberry32) standing in for Kotlin's Random(seed). */
export function rng(seed) {
  let a = (seed + 0x6d2b79f5) >>> 0
  return () => {
    a = (a + 0x6d2b79f5) >>> 0
    let t = a
    t = Math.imul(t ^ (t >>> 15), t | 1)
    t ^= t + Math.imul(t ^ (t >>> 7), t | 61)
    return ((t ^ (t >>> 14)) >>> 0) / 4294967296
  }
}
