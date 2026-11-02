// Trail generator tests. Boards are NOT expected to match the Android (Kotlin) boards: the PRNG differs
// (mulberry32 here, kotlin.random.Random there). What must match is the contract: valid, exactly one solution,
// easy has no walls, harder bands are harder, fast.
import test from 'node:test'
import assert from 'node:assert/strict'
import { performance } from 'node:perf_hooks'
import * as T from '../../src/components/puzzles/trail/engine.js'
import { makeRng } from '../../src/components/puzzles/spatial/engine/rng.js'

const SEEDS = 300

// ---- independent checker + solver (array based; shares no code with engine.js) ----
function neighbours(n, wallE, wallS) {
  const adj = Array.from({ length: n * n }, () => [])
  for (let r = 0; r < n; r++) {
    for (let c = 0; c < n; c++) {
      const i = r * n + c
      if (c + 1 < n && !wallE[i]) { adj[i].push(i + 1); adj[i + 1].push(i) }
      if (r + 1 < n && !wallS[i]) { adj[i].push(i + n); adj[i + n].push(i) }
    }
  }
  return adj
}

/** Is `path` a legal answer: every cell once, steps along open edges, dots in order, ends on first and last dot. */
function checkAnswer(p, path) {
  const m = p.n * p.n
  if (path.length !== m || new Set(path).size !== m) return false
  const adj = neighbours(p.n, p.wallE, p.wallS)
  for (let i = 1; i < m; i++) if (!adj[path[i - 1]].includes(path[i])) return false
  const order = path.map((c) => p.waypoints.indexOf(c)).filter((x) => x >= 0)
  return order.length === p.waypoints.length && order.every((x, i) => x === i) &&
    path[0] === p.waypoints[0] && path[m - 1] === p.waypoints[p.waypoints.length - 1]
}

/** Counts Hamiltonian paths through the dots in order (up to `limit`). Plain arrays, dead-end + connectivity pruning. */
function countSolutions(n, waypoints, wallE, wallS, limit = 2, nodeCap = 3e7) {
  const m = n * n
  const adj = neighbours(n, wallE, wallS)
  const dot = new Array(m).fill(-1)
  waypoints.forEach((c, k) => { dot[c] = k })
  const end = waypoints[waypoints.length - 1]
  const seen = new Array(m).fill(false)
  const path = []
  const found = []
  let nodes = 0
  let aborted = false

  function viable(cur, nextDot) {
    let left = 0
    for (let u = 0; u < m; u++) {
      if (seen[u]) continue
      left++
      let deg = 0
      for (const v of adj[u]) if (!seen[v] || v === cur) deg++
      if (u === end ? deg < 1 : deg < 2) return false
    }
    if (left === 0) return true
    // flood fill the unvisited region from cur: must be one piece
    const mark = new Array(m).fill(false)
    const stack = []
    for (const v of adj[cur]) if (!seen[v] && !mark[v]) { mark[v] = true; stack.push(v) }
    let reached = 0
    while (stack.length) {
      const u = stack.pop(); reached++
      for (const v of adj[u]) if (!seen[v] && !mark[v]) { mark[v] = true; stack.push(v) }
    }
    if (reached !== left) return false
    // the next dot must be reachable without crossing another dot
    const target = waypoints[nextDot]
    const m2 = new Array(m).fill(false)
    const st2 = []
    let ok = false
    for (const v of adj[cur]) if (!seen[v] && (dot[v] < 0 || v === target)) { m2[v] = true; st2.push(v) }
    while (st2.length) {
      const u = st2.pop()
      if (u === target) { ok = true; break }
      for (const v of adj[u]) if (!seen[v] && !m2[v] && (dot[v] < 0 || v === target)) { m2[v] = true; st2.push(v) }
    }
    return ok
  }

  function dfs(cur, nextDot) {
    if (aborted || found.length >= limit) return
    if (++nodes > nodeCap) { aborted = true; return }
    path.push(cur); seen[cur] = true
    if (path.length === m) {
      if (cur === end) found.push(path.slice())
    } else {
      for (const nb of adj[cur]) {
        if (seen[nb]) continue
        if (dot[nb] >= 0 && dot[nb] !== nextDot) continue
        if (nb === end && path.length !== m - 1) continue
        seen[nb] = true
        const nd = dot[nb] >= 0 ? nextDot + 1 : nextDot
        const good = viable(nb, Math.min(nd, waypoints.length - 1))
        seen[nb] = false
        if (good) dfs(nb, nd)
        if (aborted || found.length >= limit) break
      }
    }
    path.pop(); seen[cur] = false
  }
  dfs(waypoints[0], 1)
  return { count: found.length, solutions: found, aborted, nodes }
}

const bandName = ['easy', 'medium', 'hard']

test('300 seeds per size N=5..8: valid, exactly one solution (independent solver), easy has no walls', () => {
  for (let n = T.MIN_N; n <= T.MAX_N; n++) {
    for (let seed = 1; seed <= SEEDS; seed++) {
      const band = seed % 3
      const p = T.generate(n, band, seed)
      const tag = `n=${n} seed=${seed} band=${bandName[band]}`
      assert.equal(p.n, n, tag)
      assert.ok(checkAnswer(p, p.solution), `${tag}: stored solution invalid`)
      const r = countSolutions(n, p.waypoints, p.wallE, p.wallS, 2)
      assert.ok(!r.aborted, `${tag}: independent solver aborted`)
      assert.equal(r.count, 1, `${tag}: not unique`)
      assert.deepEqual(r.solutions[0], Array.from(p.solution), `${tag}: solver found another path`)
      assert.equal(p.solution[0], p.waypoints[0], tag)
      assert.equal(p.solution[p.cells - 1], p.waypoints[p.waypoints.length - 1], tag)
      assert.ok(p.waypoints.length >= 3 || band === 0, tag)
      // dots are numbered in the order the solution visits them
      const posOf = (c) => Array.from(p.solution).indexOf(c)
      for (let k = 1; k < p.waypoints.length; k++) assert.ok(posOf(p.waypoints[k]) > posOf(p.waypoints[k - 1]), tag)
      if (band === 0) assert.equal(p.wallCount, 0, `${tag}: easy must have no walls`)
    }
  }
})

test('engine solver agrees with the independent solver on under-constrained boards (64-bit mask halves, n up to 8)', () => {
  let compared = 0
  for (const n of [3, 4, 5, 6, 7, 8]) {
    for (let seed = 1; seed <= 12; seed++) {
      const p = T.generate(n, seed % 3, 5000 + seed)
      // drop interior dots so several solutions exist, then compare the counts
      const keep = [p.waypoints[0], ...p.waypoints.slice(1, -1).filter((_, i) => i % 3 === 0), p.waypoints[p.waypoints.length - 1]]
      const a = T.solveBoard(n, keep, p.wallE, p.wallS, 6, 400000)
      const b = countSolutions(n, keep, p.wallE, p.wallS, 6, 4e6)
      if (a.aborted || b.aborted) continue
      assert.equal(a.count, b.count, `n=${n} seed=${seed}`)
      const key = (s) => JSON.stringify(Array.from(s)); // same set of solutions
      assert.deepEqual(a.solutions.map(key).sort(), b.solutions.map(key).sort(), `n=${n} seed=${seed}`)
      compared++
    }
  }
  assert.ok(compared >= 40, `only ${compared} comparisons completed`)
})

test('solver counts several solutions and exact small cases', () => {
  const z = (k) => new Uint8Array(k)
  assert.ok(T.solveBoard(3, [0, 8], z(9), z(9), 100).count > 1)
  const r = T.solveBoard(2, [0, 1], z(4), z(4))
  assert.equal(r.count, 1)
  assert.deepEqual(Array.from(r.solutions[0]), [0, 2, 3, 1])
  // a wall that makes the board unsolvable
  const wE = z(4); wE[2] = 1
  const wS = z(4); wS[0] = 1
  assert.equal(T.solveBoard(2, [0, 1], wE, wS).count, 0)
})

test('under 200 ms per puzzle in node, every size, steady state', () => {
  for (let s = 9000; s < 9020; s++) T.generate(8, 2, s) // JIT warm-up
  const stats = []
  for (let n = T.MIN_N; n <= T.MAX_N; n++) {
    let total = 0, max = 0
    for (let seed = 1; seed <= SEEDS; seed++) {
      const t0 = performance.now()
      T.generate(n, seed % 3, seed)
      const ms = performance.now() - t0
      total += ms; max = Math.max(max, ms)
      assert.ok(ms < 200, `n=${n} seed=${seed} took ${ms.toFixed(1)} ms`)
    }
    stats.push(`N=${n}: avg ${(total / SEEDS).toFixed(2)} ms, max ${max.toFixed(1)} ms`)
  }
  console.log(`TRAIL TIMING (${SEEDS} seeds each)\n  ${stats.join('\n  ')}`)
})

test('difficulty is monotonic in solver nodes, dots fall and walls rise', () => {
  for (const n of [5, 6, 7, 8]) {
    const avg = [0, 1, 2].map((band) => {
      let nodes = 0, dots = 0, walls = 0
      const K = 100
      for (let seed = 1; seed <= K; seed++) {
        const p = T.generate(n, band, seed + 1000)
        nodes += p.nodes; dots += p.waypoints.length; walls += p.wallCount
      }
      return { nodes: nodes / K, dots: dots / K, walls: walls / K }
    })
    console.log(`TRAIL BANDS n=${n}: ` + avg.map((a, b) => `${bandName[b]} nodes ${a.nodes.toFixed(0)} dots ${a.dots.toFixed(1)} walls ${a.walls.toFixed(2)}`).join(' | '))
    assert.ok(avg[0].nodes < avg[1].nodes, `n=${n}: easy nodes ${avg[0].nodes} !< medium ${avg[1].nodes}`)
    assert.ok(avg[1].nodes < avg[2].nodes, `n=${n}: medium nodes ${avg[1].nodes} !< hard ${avg[2].nodes}`)
    assert.ok(avg[0].dots > avg[1].dots && avg[1].dots >= avg[2].dots, `n=${n}: dots should fall with difficulty`)
    assert.equal(avg[0].walls, 0)
    assert.ok(avg[2].walls > avg[1].walls && avg[1].walls > 0, `n=${n}: walls should rise with difficulty`)
  }
})

test('deterministic by seed, different seeds differ', () => {
  const a = T.generateFor('hard', 77), b = T.generateFor('hard', 77)
  assert.deepEqual(Array.from(a.solution), Array.from(b.solution))
  assert.deepEqual(Array.from(a.waypoints), Array.from(b.waypoints))
  assert.deepEqual(Array.from(a.wallE), Array.from(b.wallE))
  assert.deepEqual(Array.from(a.wallS), Array.from(b.wallS))
  const c = T.generateFor('hard', 79)
  assert.ok(a.n !== c.n || Array.from(a.solution).join() !== Array.from(c.solution).join())
})

test('difficulty maps to sizes (easy 5/6, medium 6/7, hard 7/8), negative and large seeds are fine', () => {
  for (let s = 1; s <= 10; s++) {
    assert.ok([5, 6].includes(T.sizeFor('easy', s)))
    assert.ok([6, 7].includes(T.sizeFor('medium', s)))
    assert.ok([7, 8].includes(T.sizeFor('hard', s)))
  }
  assert.equal(T.sizeFor('easy', -2), 5)
  assert.equal(T.sizeFor('easy', -3), 6)
  assert.equal(T.sizeFor('hard', 4294967295), 8)
  assert.equal(T.bandFor('Easy'), 0); assert.equal(T.bandFor('nonsense'), 1); assert.equal(T.bandFor('HARD'), 2)
})

test('random Hamiltonian paths cover every cell with unit steps (n=2..8)', () => {
  for (let n = 2; n <= 8; n++) {
    for (let s = 1; s <= 50; s++) {
      const p = T.randomHamiltonianPath(n, makeRng(`t${n}-${s}`))
      assert.equal(new Set(p).size, n * n)
      for (let i = 1; i < p.length; i++) {
        assert.equal(Math.abs(Math.floor(p[i] / n) - Math.floor(p[i - 1] / n)) + Math.abs((p[i] % n) - (p[i - 1] % n)), 1)
      }
    }
  }
})
