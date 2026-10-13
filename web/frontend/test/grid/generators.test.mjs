import test from 'node:test'
import assert from 'node:assert/strict'
import { generateSudoku, sudokuConfigFor, sudokuCountSolutions, sudokuIsConsistent } from '../../src/components/puzzles/grid/engine/sudoku.js'
import { generateBinary, binaryConfigFor, binaryCountSolutions, binaryIsSolved } from '../../src/components/puzzles/grid/engine/binary.js'
import { generateCageMath, cageMathCountSolutions, cageSatisfied } from '../../src/components/puzzles/grid/engine/cagemath.js'
import { generateSignGrid, signGridCountSolutions, signGridIsSolved } from '../../src/components/puzzles/grid/engine/signgrid.js'
import { Rng } from '../../src/components/puzzles/grid/engine/prng.js'

const seeds = [1, 2, 3, 42, 2026, 99991]
const set = (n) => new Set(Array.from({ length: n }, (_, i) => i + 1))
const eqSet = (a, b) => a.size === b.size && [...a].every((x) => b.has(x))

test('prng: deterministic and seed sensitive', () => {
  const a = new Rng(5), b = new Rng(5), c = new Rng(6)
  const sa = Array.from({ length: 10 }, () => a.next())
  assert.deepEqual(sa, Array.from({ length: 10 }, () => b.next()))
  assert.notDeepEqual(sa, Array.from({ length: 10 }, () => c.next()))
  assert.deepEqual(new Rng(9).shuffled([1, 2, 3, 4, 5]), new Rng(9).shuffled([1, 2, 3, 4, 5]))
})

for (const [diff, size] of [['easy', 4], ['medium', 6], ['hard', 9]]) {
  test(`sudoku ${diff}: unique, valid, givens match solution`, () => {
    for (const s of seeds) {
      const p = generateSudoku(diff, s)
      assert.equal(p.size, size)
      assert.ok(sudokuIsConsistent(p.solution, size, p.boxRows, p.boxCols), `${diff}/${s} consistent`)
      assert.ok(p.solution.every((x) => x !== 0))
      assert.equal(sudokuCountSolutions(p.puzzle, size, p.boxRows, p.boxCols, 2), 1, `${diff}/${s} unique`)
      p.puzzle.forEach((v, i) => v !== 0 && assert.equal(v, p.solution[i]))
      for (let r = 0; r < size; r++) {
        assert.ok(eqSet(new Set(Array.from({ length: size }, (_, k) => p.solution[r * size + k])), set(size)))
        assert.ok(eqSet(new Set(Array.from({ length: size }, (_, k) => p.solution[k * size + r])), set(size)))
      }
    }
  })
}

test('sudoku: tier clue counts decrease, stay near target', () => {
  const cfg = ['easy', 'medium', 'hard'].map(sudokuConfigFor)
  assert.deepEqual(cfg.map((c) => c[0]), [4, 6, 9])
  const frac = cfg.map((c) => c[3] / (c[0] * c[0]))
  assert.ok(frac[0] > frac[1] && frac[1] > frac[2])
  for (const d of ['easy', 'medium', 'hard']) {
    const p = generateSudoku(d, 7)
    assert.ok(p.clueCount <= sudokuConfigFor(d)[3] + 2, `${d} clues ${p.clueCount}`)
  }
})

test('sudoku: deterministic and seed sensitive', () => {
  const a = generateSudoku('hard', 5), b = generateSudoku('hard', 5), c = generateSudoku('hard', 6)
  assert.deepEqual(a.puzzle, b.puzzle)
  assert.deepEqual(a.solution, b.solution)
  assert.notDeepEqual(a.solution, c.solution)
})

test('sudoku: solver detects multiple and no solutions', () => {
  assert.equal(sudokuCountSolutions(new Array(16).fill(0), 4, 2, 2, 2), 2)
  const bad = new Array(16).fill(0); bad[0] = 1; bad[1] = 1
  assert.equal(sudokuCountSolutions(bad, 4, 2, 2, 2), 0)
})

for (const [diff, size] of [['easy', 6], ['medium', 8], ['hard', 10]]) {
  test(`binarygrid ${diff}: unique and valid`, () => {
    for (const s of seeds) {
      const p = generateBinary(diff, s)
      assert.equal(p.size, size)
      assert.ok(binaryIsSolved(p.solution, size), `${diff}/${s} valid`)
      assert.equal(binaryCountSolutions(p.puzzle, size, 2), 1, `${diff}/${s} unique`)
      p.puzzle.forEach((v, i) => v !== 0 && assert.equal(v, p.solution[i]))
    }
  })
}

test('binarygrid: tiers, determinism, rule violations', () => {
  const c = ['easy', 'medium', 'hard'].map(binaryConfigFor)
  const frac = c.map((x) => x[1] / (x[0] * x[0]))
  assert.ok(frac[0] > frac[1] && frac[1] > frac[2])
  assert.deepEqual(generateBinary('medium', 11).puzzle, generateBinary('medium', 11).puzzle)
  const g = new Array(36).fill(0); g[0] = g[1] = g[2] = 1
  assert.equal(binaryCountSolutions(g, 6, 2), 0)
  assert.equal(binaryIsSolved(new Array(36).fill(1), 6), false)
})

test('cagemath: unique, valid latin square, cages partition grid and are satisfied', () => {
  for (const [diff, size] of [['easy', 4], ['medium', 5], ['hard', 6]]) {
    for (const s of seeds) {
      const p = generateCageMath(diff, s)
      assert.equal(p.size, size)
      for (let r = 0; r < size; r++) {
        assert.ok(eqSet(new Set(Array.from({ length: size }, (_, k) => p.solution[r * size + k])), set(size)))
        assert.ok(eqSet(new Set(Array.from({ length: size }, (_, k) => p.solution[k * size + r])), set(size)))
      }
      assert.deepEqual(p.cages.flatMap((c) => c.cells).sort((a, b) => a - b), Array.from({ length: size * size }, (_, i) => i))
      for (const c of p.cages) {
        assert.ok(cageSatisfied(c, c.cells.map((x) => p.solution[x])))
        assert.ok(c.cells.length >= 1 && c.cells.length <= 4)
        assert.ok(connected(c.cells, size), 'connected')
      }
      assert.equal(cageMathCountSolutions(size, p.cages, 2), 1, `${diff}/${s} unique`)
    }
  }
})

function connected(cells, n) {
  const set = new Set(cells), seen = new Set([cells[0]]), q = [cells[0]]
  while (q.length) {
    const x = q.pop(), r = Math.floor(x / n), c = x % n
    for (const y of [r > 0 && x - n, r < n - 1 && x + n, c > 0 && x - 1, c < n - 1 && x + 1])
      if (y !== false && set.has(y) && !seen.has(y)) { seen.add(y); q.push(y) }
  }
  return seen.size === cells.length
}

test('cagemath: ops follow tier; deterministic', () => {
  const easy = new Set(Array.from({ length: 20 }, (_, i) => generateCageMath('easy', i + 1).cages.map((c) => c.op)).flat())
  assert.ok([...easy].every((o) => '+-='.includes(o)))
  const hard = new Set(Array.from({ length: 40 }, (_, i) => generateCageMath('hard', i + 1).cages.map((c) => c.op)).flat())
  assert.ok(hard.has('*'))
  const a = generateCageMath('hard', 3), b = generateCageMath('hard', 3)
  assert.deepEqual(a.solution, b.solution)
  assert.deepEqual(a.cages, b.cages)
})

test('signgrid: unique, valid, rules hold on solution', () => {
  for (const [diff, size] of [['easy', 4], ['medium', 5], ['hard', 6]]) {
    for (const s of seeds) {
      const p = generateSignGrid(diff, s)
      assert.equal(p.size, size)
      assert.ok(signGridIsSolved(p, p.solution), `${diff}/${s} solution valid`)
      assert.equal(signGridCountSolutions(size, p.givens, p.hRel, p.vRel, 2), 1, `${diff}/${s} unique`)
    }
  }
})

test('signgrid: harder tiers use fewer givens/signs; deterministic', () => {
  const avg = (d) => {
    let t = 0
    for (let s = 1; s <= 8; s++) { const p = generateSignGrid(d, s); const n = p.size * p.size; t += (p.givens.filter(Boolean).length) / n }
    return t / 8
  }
  assert.ok(avg('easy') > avg('hard'))
  assert.deepEqual(generateSignGrid('medium', 4), generateSignGrid('medium', 4))
  const p = generateSignGrid('easy', 1)
  const wrong = p.solution.slice(); wrong[0] = wrong[0] === 1 ? 2 : 1
  assert.equal(signGridIsSolved(p, wrong), false)
})
