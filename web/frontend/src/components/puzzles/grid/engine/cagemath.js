import { Rng, range, latinSquare } from './prng.js'

// Cage: { cells: number[] (sorted, row-major), op: '+'|'-'|'*'|'/'|'=', target }

export function cageLabel(cage) {
  switch (cage.op) {
    case '=':
      return `${cage.target}`
    case '*':
      return `${cage.target}×`
    case '/':
      return `${cage.target}÷`
    case '-':
      return `${cage.target}−`
    default:
      return `${cage.target}+`
  }
}

export function cageMathSizeFor(difficulty) {
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

function maxCageSize(d) {
  return d === 'easy' || d === 'beginner' || d === 'medium' ? 3 : 4
}

function neighbours(cell, n) {
  const r = Math.floor(cell / n)
  const c = cell % n
  const out = []
  if (r > 0) out.push(cell - n)
  if (r < n - 1) out.push(cell + n)
  if (c > 0) out.push(cell - 1)
  if (c < n - 1) out.push(cell + 1)
  return out
}

function isEasy(d) {
  return d === 'easy' || d === 'beginner'
}

function makeCage(cells, sol, difficulty, rnd) {
  const vals = cells.map((c) => sol[c])
  const arr = cells.slice().sort((a, b) => a - b)
  if (cells.length === 1) return { cells: arr, op: '=', target: vals[0] }
  const sum = vals.reduce((a, b) => a + b, 0)
  const prod = vals.reduce((a, b) => a * b, 1)
  if (cells.length === 2) {
    const hi = Math.max(...vals)
    const lo = Math.min(...vals)
    const options = ['+', '-']
    if (!isEasy(difficulty)) options.push('*')
    if (difficulty === 'hard' && hi % lo === 0) options.push('/')
    const op = options[rnd.int(options.length)]
    if (op === '+') return { cells: arr, op, target: sum }
    if (op === '-') return { cells: arr, op, target: hi - lo }
    if (op === '*') return { cells: arr, op, target: prod }
    return { cells: arr, op: '/', target: hi / lo }
  }
  const mult = !isEasy(difficulty) && rnd.bool()
  return mult ? { cells: arr, op: '*', target: prod } : { cells: arr, op: '+', target: sum }
}

function buildCages(n, sol, difficulty, rnd) {
  const owner = new Array(n * n).fill(-1)
  const groups = []
  const maxSize = maxCageSize(difficulty)
  for (const start of rnd.shuffled(range(n * n))) {
    if (owner[start] !== -1) continue
    const k = rnd.int(10)
    const wanted = k === 0 ? 1 : k <= 5 ? 2 : k <= 8 ? Math.min(3, maxSize) : maxSize
    const cells = [start]
    owner[start] = groups.length
    while (cells.length < wanted) {
      const frontier = [...new Set(cells.flatMap((c) => neighbours(c, n)).filter((x) => owner[x] === -1))]
      if (frontier.length === 0) break
      const next = frontier[rnd.int(frontier.length)]
      owner[next] = groups.length
      cells.push(next)
    }
    groups.push(cells)
  }
  return groups.map((g) => makeCage(g, sol, difficulty, rnd))
}

/** True if the (fully assigned) cage values satisfy the cage. */
export function cageSatisfied(cage, v) {
  switch (cage.op) {
    case '=':
      return v[0] === cage.target
    case '+':
      return v.reduce((a, b) => a + b, 0) === cage.target
    case '*':
      return v.reduce((a, b) => a * b, 1) === cage.target
    case '-':
      return Math.abs(v[0] - v[1]) === cage.target
    default: {
      const hi = Math.max(...v)
      const lo = Math.min(...v)
      return hi % lo === 0 && hi / lo === cage.target
    }
  }
}

/** Counts solutions up to `limit`. */
export function cageMathCountSolutions(n, cages, limit) {
  const cageOf = new Array(n * n).fill(0)
  cages.forEach((c, i) => c.cells.forEach((cell) => (cageOf[cell] = i)))
  const g = new Array(n * n).fill(0)
  let count = 0
  const cageOk = (ci, upTo) => {
    const cage = cages[ci]
    const vals = []
    let unassigned = 0
    for (const cell of cage.cells) {
      if (cell <= upTo) vals.push(g[cell])
      else unassigned++
    }
    if (unassigned === 0) return cageSatisfied(cage, vals)
    if (cage.op === '+') return vals.reduce((a, b) => a + b, 0) + unassigned <= cage.target
    if (cage.op === '*') return cage.target % vals.reduce((a, b) => a * b, 1) === 0
    return true
  }
  const go = (cell) => {
    if (count >= limit) return
    if (cell === n * n) {
      count++
      return
    }
    const r = Math.floor(cell / n)
    const c = cell % n
    for (let d = 1; d <= n; d++) {
      let clash = false
      for (let i = 0; i < c; i++) if (g[r * n + i] === d) { clash = true; break }
      if (!clash) for (let i = 0; i < r; i++) if (g[i * n + c] === d) { clash = true; break }
      if (clash) continue
      g[cell] = d
      if (cageOk(cageOf[cell], cell)) go(cell + 1)
      g[cell] = 0
      if (count >= limit) return
    }
  }
  go(0)
  return count
}

export function generateCageMathRaw(size, difficulty, seed) {
  const rnd = new Rng(seed)
  const solution = latinSquare(size, rnd)
  let attempt = 0
  let cages = buildCages(size, solution, difficulty, rnd)
  while (attempt < 80 && cageMathCountSolutions(size, cages, 2) !== 1) {
    cages = buildCages(size, solution, difficulty, rnd)
    attempt++
  }
  // Fallback: split multi-cell cages into single givens until unique (all singles is always unique).
  while (cageMathCountSolutions(size, cages, 2) !== 1) {
    const multi = cages.filter((c) => c.cells.length > 1)
    const victim = multi[rnd.int(multi.length)]
    const rest = cages.filter((c) => c !== victim)
    victim.cells.forEach((cell) => rest.push({ cells: [cell], op: '=', target: solution[cell] }))
    cages = rest
  }
  cages = cages.slice().sort((a, b) => a.cells[0] - b.cells[0])
  const cageOf = new Array(size * size).fill(0)
  cages.forEach((c, i) => c.cells.forEach((cell) => (cageOf[cell] = i)))
  return { size, cages, cageOf, solution }
}

export function generateCageMath(difficulty, seed) {
  const d = String(difficulty).toLowerCase()
  return generateCageMathRaw(cageMathSizeFor(d), d, seed)
}
