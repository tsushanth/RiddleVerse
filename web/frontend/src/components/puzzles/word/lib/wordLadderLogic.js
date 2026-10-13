// Word Ladder: graph over same-length words, BFS par, rules. Mirrors WordLadderLogic.kt.
import { createRng } from './prng.js'

const cmp = (a, b) => (a < b ? -1 : a > b ? 1 : 0)

export class WordLadderGraph {
  constructor(words) {
    this.words = [...new Set(words.map((w) => w.toLowerCase()))].sort(cmp)
    this.wordSet = new Set(this.words)
    this.buckets = new Map()
    for (const w of this.words) {
      for (let i = 0; i < w.length; i++) {
        const k = w.slice(0, i) + '_' + w.slice(i + 1)
        if (!this.buckets.has(k)) this.buckets.set(k, [])
        this.buckets.get(k).push(w)
      }
    }
  }

  contains(w) {
    return this.wordSet.has(w.toLowerCase())
  }

  /** Neighbours in sorted order (deterministic). */
  neighbors(w) {
    const out = new Set()
    for (let i = 0; i < w.length; i++) {
      for (const n of this.buckets.get(w.slice(0, i) + '_' + w.slice(i + 1)) || []) out.add(n)
    }
    out.delete(w)
    return [...out].sort(cmp)
  }

  /** Map of word -> BFS distance from [start]. */
  distancesFrom(start) {
    const dist = new Map()
    if (!this.contains(start)) return dist
    dist.set(start, 0)
    const q = [start]
    for (let h = 0; h < q.length; h++) {
      const cur = q[h]
      for (const n of this.neighbors(cur)) {
        if (!dist.has(n)) {
          dist.set(n, dist.get(cur) + 1)
          q.push(n)
        }
      }
    }
    return dist
  }

  /** A shortest path start..target inclusive, or null. Deterministic. */
  shortestPath(start, target) {
    if (!this.contains(start) || !this.contains(target)) return null
    const prev = new Map()
    const seen = new Set([start])
    const q = [start]
    for (let h = 0; h < q.length; h++) {
      const cur = q[h]
      if (cur === target) break
      for (const n of this.neighbors(cur)) {
        if (!seen.has(n)) {
          seen.add(n)
          prev.set(n, cur)
          q.push(n)
        }
      }
    }
    if (target !== start && !prev.has(target)) return null
    const path = []
    let c = target
    while (c !== undefined) {
      path.push(c)
      c = prev.get(c)
    }
    return path.reverse()
  }
}

export function differsByOne(a, b) {
  if (a.length !== b.length) return false
  let diff = 0
  for (let i = 0; i < a.length; i++) if (a[i] !== b[i]) diff++
  return diff === 1
}

/** 3 stars at par or better, 2 within +2, else 1. */
export function stars(steps, par) {
  return steps <= par ? 3 : steps <= par + 2 ? 2 : 1
}

export function score(steps, par, hintsUsed) {
  return Math.max(40 + stars(steps, par) * 30 - hintsUsed * 15, 10)
}

/** Why [candidate] cannot be appended to [chain], or null if it can. */
export function rejection(graph, chain, candidate) {
  const last = chain[chain.length - 1]
  if (candidate.length !== last.length) return `Needs ${last.length} letters`
  if (candidate === last) return 'Change exactly one letter'
  if (!differsByOne(last, candidate)) return 'Change exactly one letter'
  if (chain.includes(candidate)) return 'Already used'
  if (!graph.contains(candidate)) return 'Not in word list'
  return null
}

/** [wordLength, minPar, maxPar] */
export function config(difficulty) {
  const d = String(difficulty).toLowerCase()
  return d === 'easy' ? [3, 3, 4] : d === 'hard' ? [5, 5, 7] : [4, 4, 6]
}

export function poolFor(lists, length) {
  return lists.top(length, length === 5 ? 2500 : 3000)
}

/** Deterministic; always BFS-verified solvable, or null if the pool is degenerate. */
export function generate(lists, difficulty, seed) {
  const [len, minPar, maxPar] = config(difficulty)
  const pool = poolFor(lists, len)
  if (pool.length < 20) return null
  const graph = new WordLadderGraph(pool)
  const rng = createRng(seed)
  const starts = pool.slice(0, Math.floor(pool.length / 2)).sort(cmp)
  const startSet = new Set(starts)
  for (let attempt = 0; attempt < 200; attempt++) {
    const start = starts[rng.int(starts.length)]
    const dist = graph.distancesFrom(start)
    const candidates = []
    for (const [w, d] of dist) if (d >= minPar && d <= maxPar && startSet.has(w)) candidates.push(w)
    candidates.sort(cmp)
    if (!candidates.length) continue
    const target = candidates[rng.int(candidates.length)]
    const path = graph.shortestPath(start, target)
    if (!path) continue
    return { game: 'wordladder', start, target, par: path.length - 1, length: len, solution: path, seed, difficulty }
  }
  return null
}

/** Tries a few consecutive seeds, then falls back to a fixed puzzle. */
export function generateWithFallback(lists, difficulty, seed) {
  for (let i = 0; i < 5; i++) {
    const p = generate(lists, difficulty, seed + i)
    if (p) return p
  }
  return { game: 'wordladder', start: 'cat', target: 'dog', par: 3, length: 3, solution: ['cat', 'cot', 'dot', 'dog'], seed, difficulty }
}
