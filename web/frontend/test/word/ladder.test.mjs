import test from 'node:test'
import assert from 'node:assert/strict'
import * as wl from '../../src/components/puzzles/word/lib/wordLadderLogic.js'
import { lists } from './support.mjs'

const g = new wl.WordLadderGraph(['cat', 'cot', 'cog', 'dog', 'dot', 'cut', 'hut', 'hat', 'isle'.slice(0, 3)])

test('graph neighbours sorted, BFS distances and path', () => {
  assert.deepEqual(g.neighbors('cat'), ['cot', 'cut', 'hat'])
  assert.equal(g.distancesFrom('cat').get('dog'), 3)
  const p = g.shortestPath('cat', 'dog')
  assert.equal(p.length, 4)
  assert.equal(p[0], 'cat'); assert.equal(p[3], 'dog')
  for (let i = 1; i < p.length; i++) assert.ok(wl.differsByOne(p[i - 1], p[i]))
})
test('unreachable / unknown', () => {
  const h = new wl.WordLadderGraph(['cat', 'dog'])
  assert.equal(h.shortestPath('cat', 'dog'), null)
  assert.equal(h.shortestPath('cat', 'xyz'), null)
  assert.deepEqual(g.shortestPath('cat', 'cat'), ['cat'])
})
test('rules, stars, score', () => {
  assert.equal(wl.stars(3, 3), 3); assert.equal(wl.stars(5, 3), 2); assert.equal(wl.stars(6, 3), 1)
  assert.equal(wl.score(3, 3, 0), 130)
  assert.equal(wl.score(9, 3, 5), 10) // floor at 10
  assert.equal(wl.score(3, 3, 2), 100)
  assert.equal(wl.rejection(g, ['cat'], 'cat'), 'Change exactly one letter')
  assert.equal(wl.rejection(g, ['cat'], 'dog'), 'Change exactly one letter')
  assert.equal(wl.rejection(g, ['cat'], 'caz'), 'Not in word list')
  assert.equal(wl.rejection(g, ['cat'], 'cot'), null)
  assert.equal(wl.rejection(g, ['cat'], 'cats'), 'Needs 3 letters')
  assert.equal(wl.rejection(g, ['cat', 'cot', 'cut'], 'cat'), 'Already used')
  assert.equal(wl.rejection(g, ['cat', 'cot'], 'cut'), null)
})
test('generated puzzles are deterministic, solvable, at par', () => {
  for (const diff of ['easy', 'medium', 'hard']) {
    const [len, lo, hi] = wl.config(diff)
    for (const seed of [1, 2, 3, 42]) {
      const p = wl.generate(lists, diff, seed)
      assert.ok(p, `${diff} ${seed}`)
      assert.deepEqual(p, wl.generate(lists, diff, seed))
      assert.equal(p.start.length, len)
      assert.ok(p.par >= lo && p.par <= hi)
      // independent BFS check
      const graph = new wl.WordLadderGraph(wl.poolFor(lists, len))
      assert.equal(graph.distancesFrom(p.start).get(p.target), p.par)
      assert.equal(p.solution.length, p.par + 1)
      for (let i = 1; i < p.solution.length; i++) {
        assert.ok(wl.differsByOne(p.solution[i - 1], p.solution[i]))
        assert.ok(graph.contains(p.solution[i]))
      }
    }
  }
})
