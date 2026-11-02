// Ported from android RiddleDeckTest.kt + RiddlePackTest.kt (pure rules) + RiddleBankTest.kt (content checks).
import test from 'node:test'
import assert from 'node:assert/strict'
import { createDeck } from '../../src/components/puzzles/riddles/riddleDeck.js'
import { parseBank } from '../../src/components/puzzles/riddles/riddleBank.js'
import * as P from '../../src/components/puzzles/riddles/riddlePack.js'
import * as M from '../../src/components/puzzles/riddles/riddleMatcher.js'
import { rawBank, MemStore, rng } from './support.mjs'

const bank = parseBank(JSON.parse(rawBank))

test('noRepeatUntilTierIsExhausted', () => {
  const deck = createDeck(new MemStore())
  const r = rng(1)
  const drawn = Array.from({ length: 100 }, () => deck.next('easy', bank, r).id)
  assert.equal(new Set(drawn).size, 100)
  assert.ok(drawn.every((id) => id.startsWith('e')))
  assert.equal(deck.remaining('easy', bank), 0)
  assert.equal(deck.passesCompleted('easy'), 0)
})

test('newPassStartsAfterExhaustionAndDoesNotRepeatTheLastRiddleFirst', () => {
  for (let seed = 0; seed < 20; seed++) {
    const d = createDeck(new MemStore())
    const r = rng(seed)
    let last = ''
    for (let i = 0; i < 80; i++) last = d.next('hard', bank, r).id
    const first = d.next('hard', bank, r).id
    assert.notEqual(first, last, `seed ${seed} repeated the last riddle`)
    assert.equal(d.passesCompleted('hard'), 1)
    assert.equal(d.remaining('hard', bank), 79)
  }
})

test('tiersHaveIndependentDecks', () => {
  const deck = createDeck(new MemStore())
  const r = rng(3)
  for (let i = 0; i < 10; i++) deck.next('easy', bank, r)
  assert.equal(deck.remaining('easy', bank), 90)
  assert.equal(deck.remaining('medium', bank), 120)
})

test('progressSurvivesRestartBecauseItLivesInTheStore', () => {
  const store = new MemStore()
  const r = rng(4)
  const first = Array.from({ length: 40 }, () => createDeck(store).next('medium', bank, r).id) // new deck object each time = new page load
  const second = Array.from({ length: 80 }, () => createDeck(store).next('medium', bank, r).id)
  assert.equal(new Set([...first, ...second]).size, 120)
})

test('unknownIdsFromAnOlderBankAreIgnored', () => {
  const store = new MemStore()
  store.putString('seen_easy', 'e001,e999,zzz')
  const deck = createDeck(store)
  assert.equal(deck.remaining('easy', bank), 99)
  const next = deck.next('easy', bank, rng(5))
  assert.notEqual(next.id, 'e001')
})

test('differentSeedsGiveDifferentFirstRiddles', () => {
  const firsts = new Set(Array.from({ length: 30 }, (_, i) => createDeck(new MemStore()).next('medium', bank, rng(i)).id))
  assert.ok(firsts.size > 10, `picks should be random, got ${firsts.size} distinct`)
})

test('deck without a working store (private mode / quota) still deals riddles and never throws', () => {
  const broken = { getString() { throw new Error('denied') }, putString() { throw new Error('denied') } }
  const deck = createDeck(broken)
  assert.ok(deck.next('easy', bank, rng(1)).id.startsWith('e'))
})

test('deck with an empty tier throws a clear error', () => {
  assert.throws(() => createDeck(new MemStore()).next('easy', bank.filter((b) => b.tier !== 'easy'), rng(1)), /no riddles/)
})

// ---- RiddlePackTest ------------------------------------------------------------------------------------------

test('starsDropOnePerHintButNeverBelowOne', () => {
  assert.deepEqual([0, 1, 2, 3, 9].map(P.stars), [3, 2, 1, 1, 1])
})

test('pointsOnlyForSolvedRiddlesAndHardIsWorthMore', () => {
  assert.equal(P.points('hard', 0, false), 0)
  assert.equal(P.points('easy', 0, true), 30)
  assert.equal(P.points('medium', 0, true), 35)
  assert.equal(P.points('hard', 0, true), 40)
  assert.equal(P.points('easy', 1, true), 20)
  assert.equal(P.points('easy', 3, true), 10)
})

test('difficultyNamesAndNumbersMapOntoTiers', () => {
  for (const [d, t] of [['Easy', 'easy'], ['Beginner', 'easy'], ['Easy+', 'easy'], ['Tutorial', 'easy'], ['Medium', 'medium'], ['Medium+', 'medium'],
    ['', 'medium'], [null, 'medium'], ['Hard-', 'hard'], ['EXPERT', 'hard'], [1, 'easy'], [2, 'medium'], [3, 'hard'], ['3', 'hard'], [0, 'easy']]) {
    assert.equal(P.tierFromDifficulty(d), t, String(d))
  }
  assert.equal(P.tierFromPuzzle({ difficulty: 3 }), 'hard')
  assert.equal(P.tierFromPuzzle({}), 'medium')
})

test('share text is the riddle plus the web Play link and never the answer or a hint', () => {
  assert.equal(
    P.PLAY_LINK,
    'https://play.google.com/store/apps/details?id=com.kreativekoala.riddleverse&utm_source=share&utm_medium=web_riddle&utm_campaign=card',
  )
  for (const r of bank) {
    const t = P.shareText(r)
    assert.ok(t.includes(r.question) && t.includes(P.PLAY_LINK), r.id)
    assert.equal(t, `Can you solve this riddle?\n\n${r.question}\n\nPlay more riddles in RiddleVerse: ${P.PLAY_LINK}`, r.id) // nothing but the riddle and the link
    for (const h of r.hints) assert.ok(!t.includes(h), `${r.id} leaks a hint`)
  }
})

test('displayAnswerCapitalisesTheFirstLetter', () => {
  assert.equal(bank.find((r) => r.answer === 'deck of cards').displayAnswer, 'Deck of cards')
  assert.equal(bank.find((r) => r.answer === 'the letter M').displayAnswer, 'The letter M')
  assert.ok(bank.every((r) => r.displayAnswer.trim()))
})

// ---- streak (per device) -------------------------------------------------------------------------------------

test('streak: solving extends it and tracks best; skip and reveal reset it', () => {
  const s = new MemStore()
  assert.equal(P.loadStats(s).streak, 0)
  P.recordResult(s, true); P.recordResult(s, true); P.recordResult(s, true)
  assert.deepEqual(P.loadStats(s), { played: 3, solved: 3, streak: 3, best: 3 })
  P.recordResult(s, false) // skip or reveal
  assert.deepEqual(P.loadStats(s), { played: 4, solved: 3, streak: 0, best: 3 })
  P.recordResult(s, true)
  assert.equal(P.loadStats(s).streak, 1)
  assert.equal(P.loadStats(s).best, 3)
})

test('streak: corrupt stored value falls back to zeros', () => {
  const s = new MemStore()
  s.putString('stats', '{not json')
  assert.deepEqual(P.loadStats(s), { played: 0, solved: 0, streak: 0, best: 0 })
})

// ---- RiddleBankTest (content checks on the copy in web/shared) ---------------------------------------------

test('bank: 300 riddles, 100/120/80 per tier, version 1, every entry parses', () => {
  const raw = JSON.parse(rawBank)
  assert.equal(raw.version, 1)
  assert.equal(bank.length, raw.riddles.length)
  assert.equal(bank.length, 300)
  assert.deepEqual(['easy', 'medium', 'hard'].map((t) => bank.filter((r) => r.tier === t).length), [100, 120, 80])
})

test('bank: schema and length limits, unique ids, ASCII file', () => {
  const ids = new Set()
  for (const r of bank) {
    assert.ok(/^[emh]\d{3}$/.test(r.id) && r.id[0] === r.tier[0], r.id)
    assert.ok(!ids.has(r.id), `dup id ${r.id}`); ids.add(r.id)
    assert.ok(r.question.length >= 15 && r.question.length <= 240, `${r.id} q`)
    assert.ok(r.answer.length >= 1 && r.answer.length <= 40, `${r.id} a`)
    assert.ok(r.alternates.length <= 3)
    assert.equal(r.hints.length, 3)
    assert.ok(r.explanation.trim() && r.explanation.length <= 160 && !r.explanation.includes('\n'), `${r.id} why`)
  }
  assert.ok(/^[\x00-\x7f]*$/.test(rawBank))
})

test('bank: every accepted form is accepted by its own riddle', () => {
  for (const r of bank) for (const f of [r.answer, ...r.alternates]) assert.ok(M.isCorrect(f, r), `${r.id} rejects '${f}'`)
})

test("bank: no riddle accepts another riddle's answer", () => {
  for (const a of bank) for (const b of bank) {
    if (a.id === b.id) continue
    for (const f of [b.answer, ...b.alternates]) assert.ok(!M.isCorrect(f, a), `${a.id} (${a.answer}) wrongly accepts '${f}' from ${b.id}`)
  }
})

test('parseBank skips malformed entries rather than throwing', () => {
  const out = parseBank({ riddles: [{ id: 'e001', tier: 'nope' }, null, { id: 'x' }, JSON.parse(rawBank).riddles[0]] })
  assert.equal(out.length, 1)
  assert.equal(out[0].id, 'e001')
})

// ---- bundle hygiene -------------------------------------------------------------------------------------------

test('the riddle bank is only ever a dynamic import (never in the main chunk) and is not read from android/', async () => {
  const fs = await import('node:fs')
  const path = await import('node:path')
  const root = path.resolve(import.meta.dirname, '..', '..', 'src')
  const hits = []
  const walk = (d) => {
    for (const e of fs.readdirSync(d, { withFileTypes: true })) {
      const p = path.join(d, e.name)
      if (e.isDirectory()) walk(p)
      else if (/\.(jsx?|mjs)$/.test(e.name)) {
        const t = fs.readFileSync(p, 'utf8')
        if (/^\s*import[^(\n]*['"][^'"]*riddles_v1\.json['"]/m.test(t)) hits.push(`static import in ${p}`)
        if (/android\//.test(t) && /riddles/.test(t)) hits.push(`android path in ${p}`)
      }
    }
  }
  walk(root)
  assert.deepEqual(hits, [])
  assert.match(fs.readFileSync(path.join(root, 'components/puzzles/riddles/riddleBank.js'), 'utf8'), /import\('\.\.\/\.\.\/\.\.\/\.\.\/\.\.\/shared\/riddles_v1\.json'\)/)
})
