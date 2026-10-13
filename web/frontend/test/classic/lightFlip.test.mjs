import test from 'node:test'
import assert from 'node:assert/strict'
import * as L from '../../src/components/puzzles/classic/lightFlipLogic.js'
import { mulberry32, popcount } from '../../src/components/puzzles/classic/rng.js'

test('pressMask: corner, edge, centre', () => {
  assert.equal(popcount(L.pressMask(5, 0)), 3)
  assert.equal(popcount(L.pressMask(5, 2)), 4)
  assert.equal(popcount(L.pressMask(5, 12)), 5)
  assert.equal(L.pressMask(5, 0), (1 << 0) | (1 << 1) | (1 << 5))
})

test('solveMinimal solution actually turns every light off (5x5, 300 random states)', () => {
  const rng = mulberry32(1)
  let solvable = 0
  for (let i = 0; i < 300; i++) {
    const state = Math.floor(rng() * (1 << 25))
    const sol = L.solveMinimal(5, state)
    if (sol === null) continue // 5x5 has a 2-dim null space => only 1/4 of states are solvable
    solvable++
    assert.equal(L.applyPresses(5, state, sol), 0)
  }
  assert.ok(solvable > 30)
})

test('solveMinimal is minimum: 3x3 exhaustive over all 512 states', () => {
  for (let state = 0; state < 512; state++) {
    let best = null
    for (let x = 0; x < 512; x++) {
      if (L.applyPresses(3, state, x) === 0) {
        const w = popcount(x)
        if (best === null || w < best) best = w
      }
    }
    const sol = L.solveMinimal(3, state)
    if (best === null) assert.equal(sol, null)
    else assert.equal(popcount(sol), best, `state ${state}`)
  }
})

test('solveMinimal is minimum: 5x5 brute force over all 2^25 press sets for 12 states', () => {
  const rng = mulberry32(77)
  const targets = new Map()
  while (targets.size < 12) {
    let s = 0
    for (let k = 0; k < 6 + Math.floor(rng() * 12); k++) s ^= L.pressMask(5, Math.floor(rng() * 25))
    if (s) targets.set(s >>> 0, 99)
  }
  const masks = Array.from({ length: 25 }, (_, i) => L.pressMask(5, i))
  // Gray-code walk: one press toggled per step.
  let state = 0
  let weight = 0
  for (let g = 1; g < 1 << 25; g++) {
    const bit = 31 - Math.clz32(g & -g)
    const was = (g ^ (g >> 1)) & (1 << bit) // gray(g) has this bit set after the step?
    state ^= masks[bit]
    weight += was ? 1 : -1
    const cur = targets.get(state >>> 0)
    if (cur !== undefined && weight < cur) targets.set(state >>> 0, weight)
  }
  for (const [s, w] of targets) {
    assert.equal(popcount(L.solveMinimal(5, s)), w, `state ${s}`)
  }
})

test('par is at most the scramble press count and the game is never solved at start', () => {
  for (const tier of [0, 1, 2, 3, 4]) {
    for (let seed = 1; seed <= 100; seed++) {
      const g = L.newGame(tier, seed)
      assert.notEqual(g.lights, 0)
      assert.ok(g.par >= 1 && g.par <= L.scramblePresses(tier))
      assert.equal(popcount(L.solveMinimal(5, g.lights)), g.par)
    }
  }
})

test('following hints solves the board in exactly par taps', () => {
  for (let seed = 1; seed <= 60; seed++) {
    let g = L.newGame(seed % 5, seed)
    const par = g.par
    let guard = 0
    while (!L.isSolved(g) && guard++ < 40) {
      const h = L.hintCell(g)
      assert.notEqual(h, null)
      g = L.press(g, h)
    }
    assert.ok(L.isSolved(g))
    assert.equal(g.moves, par)
  }
})

test('press toggles and undo reverses; double press is identity', () => {
  const g = L.newGame(2, 4)
  const g2 = L.press(L.press(g, 7), 7)
  assert.equal(g2.lights, g.lights)
  assert.equal(g2.moves, 2)
  const g3 = L.undo(L.undo(g2))
  assert.equal(g3.lights, g.lights)
  assert.equal(g3.moves, 0)
  assert.equal(L.press(g, -1), g)
  assert.equal(L.press(g, 25), g)
})

test('determinism and hint accounting', () => {
  assert.deepEqual(L.newGame(3, 1234), L.newGame(3, 1234))
  assert.equal(L.withHintUsed(L.newGame(0, 1)).hintsUsed, 1)
})
