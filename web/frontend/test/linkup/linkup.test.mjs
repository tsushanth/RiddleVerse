// web/game-linkup: port of the Android LinkupLogicTest cases plus a JS port of scripts/linkup/validate_linkup.py
// run over the whole shared bank. Pure logic only (no React), so it runs under plain `node --test`.
import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { fileURLToPath } from 'node:url'
import { dirname, join } from 'node:path'
import * as L from '../../src/components/puzzles/linkup/linkupLogic.js'
import * as B from '../../src/components/puzzles/linkup/linkupBank.js'

const here = dirname(fileURLToPath(import.meta.url))
const raw = JSON.parse(readFileSync(join(here, '../../../shared/linkupPuzzles.json'), 'utf8'))
const bank = B.parseBank(raw)

const memStore = () => {
  const m = new Map()
  return { getInt: (k, d = 0) => (m.has(k) ? m.get(k) : d), putInt: (k, v) => void m.set(k, v) }
}

const tiny = {
  id: 't1',
  groups: [
    { tier: 1, title: 'Fruit', words: ['APPLE', 'PEAR', 'PLUM', 'FIG'] },
    { tier: 2, title: 'Metals', words: ['IRON', 'TIN', 'ZINC', 'LEAD'] },
    { tier: 3, title: 'Hats', words: ['BERET', 'FEZ', 'CAP', 'TURBAN'] },
    { tier: 4, title: 'Hide a colour', words: ['STAND', 'SHRED', 'SUBLIME', 'STEALTH'] },
  ],
  redHerrings: new Set(['TIN']),
}
const pick = (s, ...w) => w.reduce((st, x) => L.toggle(tiny, st, x), s)

// ---------- content bank validation (JS port of validate_linkup.py + LinkupLogicTest bank cases) ----------

test('bank has at least 150 puzzles', () => assert.ok(bank.puzzles.length >= 150))

test('the validator passes on the whole shared bank (16 unique words, 4x4, tiers 1-4, no title words, one partition)', () => {
  const { errors } = B.validateBank(raw)
  assert.deepEqual(errors, [])
})

test('every puzzle is structurally valid', () => {
  const wordRe = /^[A-Z]{3,10}$/
  const ids = new Set()
  for (const p of bank.puzzles) {
    assert.ok(!ids.has(p.id), `dup id ${p.id}`)
    ids.add(p.id)
    assert.equal(p.groups.length, 4, `${p.id} groups`)
    assert.deepEqual(p.groups.map((g) => g.tier).sort(), [1, 2, 3, 4], `${p.id} tiers`)
    const all = p.groups.flatMap((g) => g.words)
    assert.equal(all.length, 16)
    assert.equal(new Set(all).size, 16, `${p.id} unique words`)
    const titles = p.groups.map((g) => g.title.trim().toLowerCase())
    assert.equal(new Set(titles).size, 4, `${p.id} distinct titles`)
    for (const g of p.groups) {
      assert.equal(g.words.length, 4)
      for (const w of g.words) {
        assert.match(w, wordRe, `${p.id} ${w}`)
        assert.ok(!titles.includes(w.toLowerCase()), `${p.id} '${w}' equals a title`)
        assert.ok(!B.BLOCK.has(w), `${p.id} '${w}' blocked`)
      }
    }
    for (const h of p.redHerrings) assert.ok(all.includes(h), `${p.id} herring ${h} not in puzzle`)
  }
})

test('no duplicate puzzles or groups anywhere in the bank', () => {
  const sets = new Set()
  const groups = new Set()
  for (const p of bank.puzzles) {
    const k = L.puzzleWords(p).sort().join(',')
    assert.ok(!sets.has(k), `${p.id} duplicates another puzzle`)
    sets.add(k)
    for (const g of p.groups) {
      const gk = [...g.words].sort().join(',')
      assert.ok(!groups.has(gk), `${p.id} ${g.title} duplicates a group`)
      groups.add(gk)
    }
  }
})

test('nearly every puzzle declares a red herring', () => {
  assert.ok(bank.puzzles.filter((p) => p.redHerrings.size === 0).length <= 3)
})

test('validator catches each class of defect (it is not a rubber stamp)', () => {
  const good = () => structuredClone(raw.puzzles[0])
  const run = (mutate) => { const p = good(); mutate(p); return B.validateBank({ puzzles: [p] }).errors.join('\n') }
  assert.match(run((p) => { p.groups[0].words[0] = p.groups[1].words[0] }), /unique|more than one group/)
  assert.match(run((p) => { p.groups[0].words.pop() }), /need 4|16 unique/)
  assert.match(run((p) => { p.groups.pop() }), /need 4/)
  assert.match(run((p) => { p.groups[1].tier = 1 }), /tiers must be exactly/)
  assert.match(run((p) => { p.groups[0].words[0] = p.groups[0].title.toUpperCase().replace(/[^A-Z]/g, '') }), /equals a category title|3-10 letters/)
  assert.match(run((p) => { p.groups[0].words[0] = 'lower' }), /3-10 letters/)
  assert.match(run((p) => { p.groups[0].words[0] = 'HELL' }), /blocked/)
  assert.match(run((p) => { p.ambiguity = [{ word: 'NOPE', fits: [1] }] }), /not in the puzzle/)
  assert.match(run((p) => { p.ambiguity = [{ word: p.groups[0].words[0], fits: [1] }] }), /own group/)
  // two words that each fit the other group make two valid partitions
  assert.match(run((p) => {
    p.ambiguity = [{ word: p.groups[0].words[0], fits: [2] }, { word: p.groups[1].words[0], fits: [1] }]
  }), /valid partitions/)
  const dup = B.validateBank({ puzzles: [raw.puzzles[0], raw.puzzles[0]] }).errors.join('\n')
  assert.match(dup, /duplicate puzzle id/)
  assert.match(dup, /identical 16-word set/)
})

// ---------- selection ----------

test('selector serves every puzzle once before repeating, no boundary repeat', () => {
  for (const seed of [1, 42, 987654321]) {
    for (const n of [3, 10, bank.puzzles.length]) {
      const first = Array.from({ length: n }, (_, i) => B.indexFor(seed, n, i))
      assert.deepEqual(new Set(first), new Set(Array.from({ length: n }, (_, i) => i)), `seed ${seed} n ${n}`)
      const second = Array.from({ length: n }, (_, i) => B.indexFor(seed, n, n + i))
      assert.equal(new Set(second).size, n)
      assert.notEqual(first[n - 1], second[0], 'round boundary repeat')
    }
  }
})

test('selector is deterministic and seed dependent', () => {
  const n = bank.puzzles.length
  const a = Array.from({ length: 20 }, (_, i) => B.indexFor(7, n, i))
  assert.deepEqual(a, Array.from({ length: 20 }, (_, i) => B.indexFor(7, n, i)))
  assert.notDeepEqual(a, Array.from({ length: 20 }, (_, i) => B.indexFor(8, n, i)))
})

test('progress persists the seed and advances; a new object on the same store continues', () => {
  const store = memStore()
  let calls = 0
  const p = new B.LinkupProgress(store, () => { calls++; return 1234 })
  const n = 12
  const got = Array.from({ length: n }, () => p.take(n))
  assert.deepEqual(new Set(got), new Set(Array.from({ length: n }, (_, i) => i)))
  assert.equal(calls, 1)
  assert.equal(p.cursor(), n)
  assert.equal(new B.LinkupProgress(store).take(n), B.indexFor(1234, n, n))
})

test('generator walks the whole bank without a repeat', () => {
  const p = new B.LinkupProgress(memStore(), () => 99)
  const ids = bank.puzzles.map(() => B.nextPuzzle(bank, p).id)
  assert.equal(new Set(ids).size, bank.puzzles.length)
})

test('the same play key always yields the same puzzle and consumes the bank once', () => {
  const p = new B.LinkupProgress(memStore(), () => 5)
  const a = B.puzzleFor(bank, 'k1', p)
  assert.equal(B.puzzleFor(bank, 'k1', p), a)
  assert.equal(p.cursor(), 1)
  B.puzzleFor(bank, 'k2', p)
  assert.equal(p.cursor(), 2)
})

test('progress survives a store that throws (blocked storage)', () => {
  const store = { getInt: () => { throw new Error('blocked') }, putInt: () => { throw new Error('blocked') } }
  const p = new B.LinkupProgress(B.safeStore(store), () => 3)
  assert.doesNotThrow(() => p.take(10))
})

test('parseBank merges ambiguity words into red herrings', () => {
  const p = B.parseBank({ puzzles: [{ id: 'x', groups: tiny.groups, ambiguity: [{ word: 'TIN', fits: [1] }], herrings: ['FIG'] }] }).puzzles[0]
  assert.deepEqual([...p.redHerrings].sort(), ['FIG', 'TIN'])
})

// ---------- rules ----------

test('selection caps at four and toggles', () => {
  let s = pick(L.newState(), 'APPLE', 'PEAR', 'PLUM', 'FIG', 'IRON')
  assert.equal(s.selected.length, 4)
  assert.ok(!s.selected.includes('IRON'))
  s = L.toggle(tiny, s, 'FIG')
  assert.deepEqual(s.selected, ['APPLE', 'PEAR', 'PLUM'])
  assert.deepEqual(L.toggle(tiny, s, 'NOTAWORD'), s)
})

test('solving a group', () => {
  const s = pick(L.newState(), 'APPLE', 'PEAR', 'PLUM', 'FIG')
  const { state: n, result } = L.submit(tiny, s)
  assert.equal(result, 'SOLVED')
  assert.deepEqual(n.solved, [0])
  assert.equal(n.selected.length, 0)
  assert.equal(n.mistakes, 0)
  assert.ok(!L.available(tiny, n).some((w) => tiny.groups[0].words.includes(w)))
  assert.equal(L.available(tiny, n).length, 12)
  assert.deepEqual(L.toggle(tiny, n, 'APPLE'), n) // solved tiles cannot be selected again
})

test('close, wrong, already tried, incomplete', () => {
  const close = pick(L.newState(), 'APPLE', 'PEAR', 'PLUM', 'IRON')
  const r1 = L.submit(tiny, close)
  assert.equal(r1.result, 'CLOSE'); assert.equal(r1.state.mistakes, 1)
  const r2 = L.submit(tiny, r1.state)
  assert.equal(r2.result, 'ALREADY_TRIED'); assert.equal(r2.state.mistakes, 1)
  assert.equal(L.submit(tiny, pick(L.newState(), 'APPLE', 'IRON', 'CAP', 'STAND')).result, 'WRONG')
  assert.equal(L.submit(tiny, pick(L.newState(), 'APPLE')).result, 'INCOMPLETE')
})

test('the same four tiles in another order count as already tried', () => {
  const a = L.submit(tiny, pick(L.newState(), 'APPLE', 'PEAR', 'PLUM', 'IRON')).state
  const b = pick(L.deselectAll(a), 'IRON', 'PLUM', 'PEAR', 'APPLE')
  assert.equal(L.submit(tiny, b).result, 'ALREADY_TRIED')
})

test('four mistakes lose', () => {
  let s = L.newState()
  const guesses = [
    ['APPLE', 'PEAR', 'PLUM', 'IRON'], ['APPLE', 'PEAR', 'PLUM', 'TIN'],
    ['APPLE', 'PEAR', 'PLUM', 'CAP'], ['APPLE', 'PEAR', 'PLUM', 'STAND'],
  ]
  for (const g of guesses) {
    assert.ok(!L.isOver(s))
    s = L.submit(tiny, pick(L.deselectAll(s), ...g)).state
  }
  assert.ok(L.isLost(s)); assert.ok(!L.isWon(s))
  assert.equal(L.mistakesLeft(s), 0)
  assert.equal(L.submit(tiny, s).result, 'FINISHED')
  assert.equal(L.unsolvedGroups(tiny, s).length, 4)
  assert.equal(L.score(s, L.profile('medium')), 0)
})

test('winning all groups and score', () => {
  let s = L.newState()
  for (const g of tiny.groups) s = L.submit(tiny, pick(s, ...g.words)).state
  assert.ok(L.isWon(s)); assert.ok(!L.isLost(s))
  assert.equal(L.score(s, L.profile('medium')), 100)
  assert.equal(L.score({ ...s, mistakes: 2 }, L.profile('medium')), 80)
  assert.equal(L.score({ ...s, mistakes: 3, hints: 9 }, L.profile('easy')), 10)
})

test('give up loses and clears the selection', () => {
  const s = L.giveUp(pick(L.newState(), 'APPLE'))
  assert.ok(L.isLost(s)); assert.equal(s.selected.length, 0)
})

test('hint marks a tile of the easiest group, avoids herrings, and is capped by difficulty', () => {
  const prof = L.profile('medium')
  const h1 = L.hint(tiny, L.newState(), prof)
  assert.equal(h1.word, 'APPLE')
  const solved = L.submit(tiny, pick(L.newState(), 'APPLE', 'PEAR', 'PLUM', 'FIG')).state
  const h2 = L.hint(tiny, solved, prof)
  assert.equal(h2.word, 'IRON') // skips the declared herring TIN
  const h3 = L.hint(tiny, h2.state, prof)
  assert.equal(h3.word, 'ZINC')
  assert.equal(L.hint(tiny, h3.state, prof).word, null) // medium: 2 hints
  assert.equal(h1.state.hints, 1)
  assert.equal(L.profile('hard').maxHints, 1)
  assert.equal(L.profile('easy').maxHints, 3)
})

test('close-guess feedback is on for easy and medium, off for hard', () => {
  assert.ok(L.profile('easy').closeFeedback)
  assert.ok(L.profile('medium').closeFeedback)
  assert.ok(!L.profile('hard').closeFeedback)
  assert.equal(L.profile('nonsense').maxHints, 2)
})

test('display order is stable and shuffle changes it', () => {
  const a = L.available(tiny, L.newState())
  assert.deepEqual(a, L.available(tiny, L.newState()))
  assert.deepEqual(new Set(a), new Set(L.puzzleWords(tiny)))
  assert.notDeepEqual(a, L.available(tiny, L.shuffle(L.newState())))
  const solved = L.submit(tiny, pick(L.newState(), 'APPLE', 'PEAR', 'PLUM', 'FIG')).state
  assert.deepEqual(L.available(tiny, solved), a.filter((w) => !tiny.groups[0].words.includes(w)))
})

test('tier names and glyphs differ per tier (never colour alone)', () => {
  assert.equal(new Set([1, 2, 3, 4].map(L.tierGlyph)).size, 4)
  assert.equal(new Set([1, 2, 3, 4].map(L.tierName)).size, 4)
})

test('share text: counts, tier glyphs in solve order, exact Play link', () => {
  assert.equal(
    L.PLAY_LINK,
    'https://play.google.com/store/apps/details?id=com.kreativekoala.riddleverse&utm_source=share&utm_medium=web_linkup&utm_campaign=card',
  )
  let s = L.newState()
  s = L.submit(tiny, pick(s, 'IRON', 'TIN', 'ZINC', 'LEAD')).state
  s = L.submit(tiny, pick(s, 'APPLE', 'PEAR', 'PLUM', 'CAP')).state // one away
  const text = L.shareText(tiny, s)
  assert.match(text, /^RiddleVerse Linkup: 1\/4 groups, 1 mistake, 0 hints/)
  assert.ok(text.includes('▲'), 'solved tier 2 glyph')
  assert.ok(text.endsWith(L.PLAY_LINK))
  assert.ok(!text.includes('IRON'), 'share text must not leak answers')
  const plural = L.shareText(tiny, { ...s, mistakes: 2, hints: 1 })
  assert.match(plural, /2 mistakes, 1 hint\b/)
})
