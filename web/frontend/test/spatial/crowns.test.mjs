// web/game-crowns: property tests for the Crowns engine (node --test, like the rest of this package).
//
// The web engine is a port of the Android CrownsEngine.kt. Exact boards do NOT match Kotlin for the same seed
// (Kotlin's Random vs the JS mulberry32 PRNG used by every web engine); the algorithm, rules and difficulty
// bands are the same. The uniqueness check below uses its OWN solver (region-by-region, pairwise rule checks),
// not the engine's row-by-row bitmask solver, so a bug in the engine solver cannot hide itself.
import test from 'node:test'
import assert from 'node:assert/strict'
import * as C from '../../src/components/puzzles/crowns/crowns.js'

const SEEDS = 500

// ---------------------------------------------------------------- independent oracle

function touching(a, b, n) {
  const ar = Math.floor(a / n), ac = a % n, br = Math.floor(b / n), bc = b % n
  return Math.abs(ar - br) <= 1 && Math.abs(ac - bc) <= 1
}

/** Counts solutions (stops at `limit`) by choosing one cell per region and checking every pair. */
function oracleCount(n, regions, limit = 2) {
  const cellsOf = Array.from({ length: n }, () => [])
  regions.forEach((g, i) => cellsOf[g].push(i))
  const order = [...cellsOf.keys()].sort((a, b) => cellsOf[a].length - cellsOf[b].length)
  const placed = []
  let count = 0
  const ok = (i) => placed.every((j) => {
    const ri = Math.floor(i / n), rj = Math.floor(j / n)
    return ri !== rj && i % n !== j % n && !touching(i, j, n)
  })
  const rec = (k) => {
    if (count >= limit) return
    if (k === n) { count++; return }
    for (const cell of cellsOf[order[k]]) {
      if (!ok(cell)) continue
      placed.push(cell)
      rec(k + 1)
      placed.pop()
    }
  }
  rec(0)
  return count
}

function connectedOracle(n, regions) {
  for (let id = 0; id < n; id++) {
    const cells = []
    regions.forEach((g, i) => { if (g === id) cells.push(i) })
    if (!cells.length) return false
    const seen = new Set([cells[0]])
    const queue = [cells[0]]
    while (queue.length) {
      const i = queue.pop()
      const r = Math.floor(i / n), c = i % n
      for (const [dr, dc] of [[1, 0], [-1, 0], [0, 1], [0, -1]]) {
        const rr = r + dr, cc = c + dc
        if (rr < 0 || cc < 0 || rr >= n || cc >= n) continue
        const j = rr * n + cc
        if (regions[j] === id && !seen.has(j)) { seen.add(j); queue.push(j) }
      }
    }
    if (seen.size !== cells.length) return false
  }
  return true
}

function assertValid(p, label) {
  const n = p.n
  assert.equal(p.regions.length, n * n, label)
  assert.ok(p.regions.every((g) => Number.isInteger(g) && g >= 0 && g < n), `${label} region ids`)
  assert.ok(connectedOracle(n, p.regions), `${label} regions connected`)
  // stored solution obeys every rule (independent pairwise check)
  assert.equal(p.solution.length, n, label)
  const cells = p.solution.map((c, r) => r * n + c)
  assert.equal(new Set(p.solution).size, n, `${label} one per column`)
  assert.equal(new Set(cells.map((i) => p.regions[i])).size, n, `${label} one per region`)
  for (let a = 0; a < n; a++) for (let b = a + 1; b < n; b++) assert.ok(!touching(cells[a], cells[b], n), `${label} touching`)
  assert.equal(oracleCount(n, p.regions, 2), 1, `${label} must have EXACTLY one solution`)
  assert.ok(p.band >= 1 && p.band <= 4, label)
}

// ---------------------------------------------------------------- generator properties

test(`${SEEDS} seeds per size N=5..9: valid, connected, exactly one solution, < 100 ms each`, () => {
  for (let s = 0; s < 30; s++) C.generate(9, 90000 + s) // JIT warm-up, like a running page
  const report = []
  for (let n = C.MIN_N; n <= C.MAX_N; n++) {
    let total = 0, max = 0
    const bands = [0, 0, 0, 0]
    for (let seed = 1; seed <= SEEDS; seed++) {
      let t0 = performance.now()
      const p = C.generate(n, seed)
      let ms = performance.now() - t0
      // generation is deterministic: a slow sample on a busy machine (GC, parallel builds) is re-timed
      for (let retry = 0; ms >= 100 && retry < 3; retry++) {
        t0 = performance.now(); C.generate(n, seed); ms = performance.now() - t0
      }
      total += ms; if (ms > max) max = ms
      assertValid(p, `n=${n} seed=${seed}`)
      bands[p.band - 1]++
      assert.ok(ms < 100, `n=${n} seed=${seed} took ${ms.toFixed(1)} ms (budget 100)`)
    }
    report.push(`N=${n} avg=${(total / SEEDS).toFixed(2)}ms max=${max.toFixed(2)}ms bands(1..4)=${bands}`)
  }
  console.log(report.join('\n'))
})

test('engine solver agrees with the independent oracle on non-unique boards', () => {
  const bands = Array.from({ length: 25 }, (_, i) => Math.floor(i / 5)) // horizontal stripes: many solutions
  assert.ok(C.countSolutions(5, bands, 2) >= 2)
  assert.ok(oracleCount(5, bands, 2) >= 2)
  for (let seed = 1; seed <= 40; seed++) {
    const p = C.generate(6, seed)
    assert.equal(C.countSolutions(6, p.regions, 2), oracleCount(6, p.regions, 2))
  }
})

test('deterministic for a seed, different for another seed', () => {
  for (let n = 5; n <= 9; n++) {
    const a = C.generate(n, 4242), b = C.generate(n, 4242), c = C.generate(n, 4243)
    assert.deepEqual([...a.regions], [...b.regions])
    assert.deepEqual([...a.solution], [...b.solution])
    assert.equal(a.band, b.band)
    assert.ok(![...a.regions].every((g, i) => g === c.regions[i]) || ![...a.solution].every((x, i) => x === c.solution[i]))
  }
})

test('rejects sizes outside 5..9', () => {
  assert.throws(() => C.generate(4, 1))
  assert.throws(() => C.generate(10, 1))
})

test('placement validity: duplicates and touching are rejected', () => {
  const p = C.generate(5, 3)
  const dup = [...p.solution]; dup[1] = dup[0]
  assert.equal(C.isValidPlacement(5, p.regions, dup), false)
  assert.equal(C.isValidPlacement(5, p.regions, [0, 1, 3, 0, 2]), false)
  assert.equal(C.isValidPlacement(5, p.regions, p.solution), true)
})

// ---------------------------------------------------------------- difficulty

test('difficulty maps to a size range (same mapping as Android)', () => {
  for (let seed = 1; seed <= 40; seed++) {
    assert.ok([5, 6].includes(C.sizeFor('Easy', seed)))
    assert.equal(C.sizeFor('Medium', seed), 7)
    assert.ok([8, 9].includes(C.sizeFor('Hard', seed)))
    assert.equal(C.sizeFor('6', seed), 6)
    assert.equal(C.sizeFor(9, seed), 9)
  }
  assert.equal(C.generateFor('hard', 7).n, C.sizeFor('hard', 7))
})

test('hard bands outrank easy bands, and levels land in their bands', () => {
  let easy = 0, hard = 0, easyOk = 0, medOk = 0, hardOk = 0
  const total = 200
  for (let seed = 1; seed <= total; seed++) {
    const e = C.generateFor('easy', seed), m = C.generateFor('medium', seed), h = C.generateFor('hard', seed)
    easy += e.band; hard += h.band
    if (e.band <= 2) easyOk++
    if (m.band >= 2 && m.band <= 3) medOk++
    if (h.band >= 3) hardOk++
  }
  console.log(`band match: easy=${easyOk}/${total} medium=${medOk}/${total} hard=${hardOk}/${total}`)
  assert.ok(hard > easy, `avg hard band ${hard} should exceed easy ${easy}`)
  assert.ok(easyOk >= total * 0.95, `easy ${easyOk}`)
  assert.ok(medOk >= total * 0.85, `medium ${medOk}`)
  assert.ok(hardOk >= total * 0.85, `hard ${hardOk}`)
})

// ---------------------------------------------------------------- player state

const newState = (seed = 5, n = 5) => new C.CrownsState(C.generate(n, seed))

test('tap cycles empty -> mark -> crown -> empty', () => {
  const g = newState()
  assert.equal(g.tap(0), 1); assert.equal(g.tap(0), 2); assert.equal(g.tap(0), 0)
})

test('undo reverts taps in order', () => {
  const g = newState()
  g.tap(3); g.tap(3); g.tap(7)
  assert.equal(g.cells[3], 2); assert.equal(g.cells[7], 1)
  assert.ok(g.undo()); assert.equal(g.cells[7], 0)
  assert.ok(g.undo()); assert.equal(g.cells[3], 1)
  assert.ok(g.undo()); assert.equal(g.cells[3], 0)
  assert.equal(g.undo(), false)
  assert.equal(g.canUndo(), false)
})

test('conflicts flag row, column, region and touching crowns; marks never conflict', () => {
  const p = C.generate(6, 11)
  const n = p.n
  const g = new C.CrownsState(p)
  const only = (...idx) => { g.cells.fill(0); idx.forEach((i) => { g.cells[i] = 2 }); return g.conflicts() }
  let c = only(0, 3); assert.ok(c[0] && c[3], 'same row')
  c = only(0, 2 * n); assert.ok(c[0] && c[2 * n], 'same column')
  c = only(0, n + 1); assert.ok(c[0] && c[n + 1], 'diagonal touch')
  let pair = null
  for (let i = 0; i < n * n && !pair; i++) for (let j = i + 1; j < n * n && !pair; j++) {
    if (p.regions[i] === p.regions[j] && Math.floor(i / n) !== Math.floor(j / n) && i % n !== j % n &&
      Math.abs(Math.floor(i / n) - Math.floor(j / n)) > 1 && Math.abs((i % n) - (j % n)) > 1) pair = [i, j]
  }
  assert.ok(pair, 'a same-region, non-touching pair exists')
  c = only(...pair); assert.ok(c[pair[0]] && c[pair[1]], 'same region')
  // a lone crown and a legal pair are not flagged
  assert.ok(only(0).every((x) => !x))
  g.cells.fill(1)
  assert.equal(g.hasConflicts(), false)
})

test('placing the solution solves it; a stray extra crown breaks it', () => {
  for (let n = 5; n <= 9; n++) {
    const p = C.generate(n, 21 + n)
    const g = new C.CrownsState(p)
    assert.equal(g.isSolved(), false)
    for (let r = 0; r < n; r++) g.cells[r * n + p.solution[r]] = 2
    assert.ok(g.isSolved(), `n=${n}`)
    const extra = [...g.cells].findIndex((v) => v === 0)
    g.cells[extra] = 2
    assert.equal(g.isSolved(), false)
  }
})

test('n legal-looking crowns that are not the unique solution cannot exist (uniqueness => win check is exact)', () => {
  // any conflict-free set of n crowns is a solution, and the puzzle has exactly one
  const p = C.generate(6, 8)
  const found = C.solutions(6, p.regions, 5)
  assert.equal(found.length, 1)
  assert.deepEqual([...found[0]], [...p.solution])
})

test('hint removes a wrong crown first, then places correct crowns; stars and score follow hints', () => {
  const p = C.generate(6, 8)
  const g = new C.CrownsState(p)
  const wrong = [...Array(36).keys()].find((i) => !p.isSolutionCell(i))
  g.cells[wrong] = 2
  const h1 = g.hint()
  assert.ok(h1.removed); assert.equal(h1.cell, wrong); assert.equal(g.cells[wrong], 0)
  let guard = 0
  while (!g.isSolved() && guard++ < 10) {
    const h = g.hint()
    assert.equal(h.removed, false)
    assert.ok(p.isSolutionCell(h.cell))
  }
  assert.ok(g.isSolved())
  assert.equal(g.hint(), null)
  assert.equal(g.hintsUsed, 7)
  assert.equal(g.stars(), 1)
  assert.ok(g.score() >= 10)
  assert.equal(newState().stars(), 3)
})

test('clear is undoable cell by cell; encode/restore round-trips and ignores garbage', () => {
  const g = newState(); g.tap(1); g.tap(1); g.tap(9); g.hint()
  const s = g.encode()
  const h = new C.CrownsState(g.puzzle); h.restore(s)
  assert.deepEqual([...h.cells], [...g.cells])
  assert.equal(h.hintsUsed, g.hintsUsed)
  h.restore('garbage')
  assert.deepEqual([...h.cells], [...g.cells])
  g.clear()
  assert.ok(g.cells.every((v) => v === 0))
  assert.ok(g.undo())
})

// ---------------------------------------------------------------- accessibility + layout helpers

test('nine region styles: unique colours AND unique patterns (never colour alone)', () => {
  assert.equal(C.REGION_STYLES.length, 9)
  assert.equal(new Set(C.REGION_STYLES.map((s) => s.color)).size, 9)
  assert.equal(new Set(C.REGION_STYLES.map((s) => s.pattern)).size, 9)
  assert.equal(new Set(C.REGION_STYLES.map((s) => s.colorName)).size, 9)
  assert.equal(new Set(C.REGION_STYLES.map((s) => s.patternName)).size, 9)
})

test('cell labels name row, column, region colour, pattern and state', () => {
  const p = C.generate(5, 2)
  const label = C.cellLabel(p, 1, 2, 2, false)
  const style = C.REGION_STYLES[p.regionOf(1, 2) % 9]
  assert.match(label, /^Row 2, column 3, /)
  assert.ok(label.includes(style.colorName))
  assert.ok(label.includes(style.patternName))
  assert.match(label, /crown$/)
  assert.match(C.cellLabel(p, 0, 0, 1, false), /marked empty$/)
  assert.match(C.cellLabel(p, 0, 0, 0, false), /empty$/)
  assert.match(C.cellLabel(p, 0, 0, 2, true), /crown, conflict$/)
})

test('touch targets: cell size is never below 44 px; 5x5 and 6x6 fit a 360 px phone, 9x9 scrolls', () => {
  for (let n = 5; n <= 9; n++) for (const w of [280, 328, 360, 600, 1200]) {
    const size = C.cellSizePx(n, w)
    assert.ok(size >= 44, `n=${n} w=${w} cell ${size}`)
    assert.ok(size <= 64, `n=${n} w=${w} cell ${size}`)
  }
  // a 360 px phone leaves ~294 px inside the page padding (measured in headless Chrome)
  for (const n of [5, 6]) assert.ok(n * C.cellSizePx(n, 294) <= 294, `${n}x${n} fits without scrolling`)
  assert.equal(C.cellSizePx(9, 328), 44, '9x9 on a phone keeps 44 px cells (board scrolls)')
})

test('keyboard navigation: arrows move and clamp at the edges; Home/End jump within the row', () => {
  assert.equal(C.moveFocus(5, 0, 'ArrowRight'), 1)
  assert.equal(C.moveFocus(5, 0, 'ArrowLeft'), 0)
  assert.equal(C.moveFocus(5, 0, 'ArrowUp'), 0)
  assert.equal(C.moveFocus(5, 0, 'ArrowDown'), 5)
  assert.equal(C.moveFocus(5, 24, 'ArrowDown'), 24)
  assert.equal(C.moveFocus(5, 24, 'ArrowRight'), 24)
  assert.equal(C.moveFocus(5, 12, 'Home'), 10)
  assert.equal(C.moveFocus(5, 12, 'End'), 14)
  assert.equal(C.moveFocus(5, 12, 'a'), 12)
})
