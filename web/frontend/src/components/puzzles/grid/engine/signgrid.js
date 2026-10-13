import { Rng, range, latinSquare } from './prng.js'

// hRel[r*(n-1)+c]: relation between (r,c) and (r,c+1); vRel[r*n+c]: between (r,c) and (r+1,c).
// 0 none, 1 = first cell is smaller, -1 = first cell is larger.

export function signGridSizeFor(difficulty) {
  switch (String(difficulty).toLowerCase()) {
    case 'easy':
    case 'beginner':
      return 4
    case 'medium':
      return 5
    default:
      return 6
  }
}

/** Counts solutions up to `limit`. */
export function signGridCountSolutions(n, givens, h, v, limit) {
  const g = new Array(n * n).fill(0)
  let count = 0
  const go = (cell) => {
    if (count >= limit) return
    if (cell === n * n) {
      count++
      return
    }
    const r = Math.floor(cell / n)
    const c = cell % n
    for (let d = 1; d <= n; d++) {
      if (givens[cell] !== 0 && givens[cell] !== d) continue
      let ok = true
      for (let i = 0; i < c; i++) if (g[r * n + i] === d) { ok = false; break }
      if (ok) for (let i = 0; i < r; i++) if (g[i * n + c] === d) { ok = false; break }
      if (!ok) continue
      if (c > 0) {
        const rel = h[r * (n - 1) + c - 1]
        const left = g[cell - 1]
        if (rel === 1 && !(left < d)) continue
        if (rel === -1 && !(left > d)) continue
      }
      if (r > 0) {
        const rel = v[(r - 1) * n + c]
        const up = g[cell - n]
        if (rel === 1 && !(up < d)) continue
        if (rel === -1 && !(up > d)) continue
      }
      if (c < n - 1 && givens[cell + 1] !== 0) {
        const rel = h[r * (n - 1) + c]
        if (rel === 1 && !(d < givens[cell + 1])) continue
        if (rel === -1 && !(d > givens[cell + 1])) continue
      }
      g[cell] = d
      go(cell + 1)
      g[cell] = 0
      if (count >= limit) return
    }
  }
  go(0)
  return count
}

/** Checks all rules for a fully filled grid against a puzzle {size, givens, hRel, vRel}. */
export function signGridIsSolved(p, g) {
  const n = p.size
  if (g.some((x) => x === 0)) return false
  for (let i = 0; i < n; i++) {
    const row = new Set(range(n).map((k) => g[i * n + k]))
    const col = new Set(range(n).map((k) => g[k * n + i]))
    if (row.size !== n || col.size !== n) return false
  }
  for (let r = 0; r < n; r++)
    for (let c = 0; c < n - 1; c++) {
      const rel = p.hRel[r * (n - 1) + c]
      const a = g[r * n + c]
      const b = g[r * n + c + 1]
      if (rel === 1 && a >= b) return false
      if (rel === -1 && a <= b) return false
    }
  for (let r = 0; r < n - 1; r++)
    for (let c = 0; c < n; c++) {
      const rel = p.vRel[r * n + c]
      const a = g[r * n + c]
      const b = g[(r + 1) * n + c]
      if (rel === 1 && a >= b) return false
      if (rel === -1 && a <= b) return false
    }
  for (let i = 0; i < g.length; i++) if (p.givens[i] !== 0 && p.givens[i] !== g[i]) return false
  return true
}

export function generateSignGridRaw(n, difficulty, seed) {
  const rnd = new Rng(seed)
  const sol = latinSquare(n, rnd)
  const density = difficulty === 'easy' || difficulty === 'beginner' ? 0.55 : difficulty === 'medium' ? 0.42 : 0.32
  const h = new Array(n * (n - 1)).fill(0)
  const v = new Array((n - 1) * n).fill(0)
  for (let r = 0; r < n; r++)
    for (let c = 0; c < n - 1; c++)
      if (rnd.next() < density) h[r * (n - 1) + c] = sol[r * n + c] < sol[r * n + c + 1] ? 1 : -1
  for (let r = 0; r < n - 1; r++)
    for (let c = 0; c < n; c++)
      if (rnd.next() < density) v[r * n + c] = sol[r * n + c] < sol[(r + 1) * n + c] ? 1 : -1
  const givens = new Array(n * n).fill(0)
  const order = rnd.shuffled(range(n * n))
  let idx = 0
  while (signGridCountSolutions(n, givens, h, v, 2) !== 1) {
    const cell = order[idx++]
    givens[cell] = sol[cell]
  }
  const hard = difficulty !== 'easy' && difficulty !== 'beginner'
  if (hard) {
    for (const cell of order) {
      if (givens[cell] === 0) continue
      const keep = givens[cell]
      givens[cell] = 0
      if (signGridCountSolutions(n, givens, h, v, 2) !== 1) givens[cell] = keep
    }
  }
  if (difficulty === 'hard') {
    const signs = rnd.shuffled([...h.map((_, i) => [0, i]), ...v.map((_, i) => [1, i])])
    for (const [kind, i] of signs) {
      const arr = kind === 0 ? h : v
      if (arr[i] === 0) continue
      const keep = arr[i]
      arr[i] = 0
      if (signGridCountSolutions(n, givens, h, v, 2) !== 1) arr[i] = keep
    }
  }
  return { size: n, givens, hRel: h, vRel: v, solution: sol }
}

export function generateSignGrid(difficulty, seed) {
  const d = String(difficulty).toLowerCase()
  return generateSignGridRaw(signGridSizeFor(d), d, seed)
}
