import test from 'node:test'
import assert from 'node:assert/strict'
import {
  parseMemorySingle, expectedAnswers, scoreRun, timeBudgetSeconds, newSession, reduce,
  FIRST_SYMBOL_MS, FEEDBACK_MS, PASS_RATIO,
} from '../../src/components/puzzles/memorySingleLogic.js'
import { MemoryPreviousSingleGenerator } from '../../../backend/services/memoryPreviousSingleGenerator.js'

const quiet = async (fn) => {
  const log = console.log
  console.log = () => {}
  try { return await fn() } finally { console.log = log }
}

async function makePuzzles(difficulty, n) {
  return quiet(async () => {
    const g = new MemoryPreviousSingleGenerator()
    const out = []
    for (let i = 0; i < n; i++) {
      const r = await g.generateMemoryPreviousSinglePuzzle(difficulty)
      assert.ok(r.success)
      out.push({ puzzleId: `t${i}`, question: r.puzzleData.question, answer: r.puzzleData.answer })
    }
    return out
  })
}

test('answer key = "same as the symbol just before" and matches the server key, all difficulties', async () => {
  for (const d of ['easy', 'medium', 'hard']) {
    for (const puz of await makePuzzles(d, 300)) {
      const { symbols, serverKey } = parseMemorySingle(puz)
      const exp = expectedAnswers(symbols)
      assert.equal(exp.length, symbols.length - 1)
      assert.deepEqual(exp, serverKey, `${d}: web key must equal backend key`)
      for (let i = 1; i < symbols.length; i++) assert.equal(exp[i - 1], symbols[i] === symbols[i - 1] ? 1 : 0)
    }
  }
})

test('a player with perfect memory who answers the question asked is scored 100% and passes (was a coin flip)', async () => {
  for (const d of ['easy', 'medium', 'hard']) {
    for (const puz of await makePuzzles(d, 300)) {
      const { symbols } = parseMemorySingle(puz)
      let s = newSession(symbols)
      s = reduce(s, { type: 'firstShown' })
      // answer each symbol honestly: SAME iff it equals the one before
      while (!s.done) {
        const i = s.index
        s = reduce(s, { type: 'answer', value: symbols[i] === symbols[i - 1] ? 1 : 0 })
        s = reduce(s, { type: 'feedbackDone' })
      }
      const r = scoreRun(s.expected, s.answers)
      assert.equal(r.correct, r.total)
      assert.equal(r.passed, true)
    }
  }
})

test('pass bar is 60% (Android parity); tapping one button on a puzzle with only 40% "same" fails', () => {
  assert.equal(PASS_RATIO, 0.6)
  const symbols = [0, 0, 1, 1, 0, 1, 1, 0, 0, 1, 0] // 10 transitions: 4 same, 6 different
  const exp = expectedAnswers(symbols)
  assert.equal(exp.filter((x) => x === 1).length, 4)
  assert.equal(scoreRun(exp, exp.map(() => 1)).passed, false)
  assert.equal(scoreRun(exp, exp).passed, true)
})

test('the sequence is fixed for the whole session: nothing regenerates it, answers only append', () => {
  const symbols = [0, 1, 1, 0, 0, 1]
  let s = newSession(symbols)
  const ref = s.symbols
  s = reduce(s, { type: 'firstShown' })
  for (let i = 1; i < symbols.length; i++) {
    s = reduce(s, { type: 'answer', value: 1 })
    s = reduce(s, { type: 'feedbackDone' })
    assert.equal(s.symbols, ref, 'same array instance after every answer')
    assert.deepEqual(s.symbols, [0, 1, 1, 0, 0, 1])
  }
  assert.equal(s.done, true)
  assert.equal(s.answers.length, symbols.length - 1)
})

test('answers are ignored while feedback is showing, before the first symbol is done, and after the end', () => {
  let s = newSession([0, 1, 1])
  assert.equal(reduce(s, { type: 'answer', value: 1 }), s, 'cannot answer the first (remember-only) symbol')
  s = reduce(s, { type: 'firstShown' })
  s = reduce(s, { type: 'answer', value: 0 })
  assert.equal(reduce(s, { type: 'answer', value: 1 }), s, 'double tap during feedback ignored')
  assert.equal(s.answers.length, 1)
  s = reduce(s, { type: 'feedbackDone' })
  s = reduce(s, { type: 'answer', value: 1 })
  s = reduce(s, { type: 'feedbackDone' })
  assert.equal(s.done, true)
  assert.equal(reduce(s, { type: 'answer', value: 1 }), s)
})

test('timeout ends the run; unanswered symbols count as wrong', () => {
  let s = newSession([0, 0, 1, 1])
  s = reduce(s, { type: 'firstShown' })
  s = reduce(s, { type: 'answer', value: 1 })
  s = reduce(s, { type: 'feedbackDone' })
  s = reduce(s, { type: 'timeout' })
  assert.equal(s.done, true)
  const r = scoreRun(s.expected, s.answers)
  assert.equal(r.total, 3)
  assert.equal(r.correct, 1)
  assert.equal(r.passed, false)
})

test('the clock covers the unavoidable waiting: >= 3.5 s of thinking per answer at every real length', () => {
  for (const n of [16, 18, 20]) {
    const budget = timeBudgetSeconds(n)
    const waiting = (FIRST_SYMBOL_MS + (n - 1) * FEEDBACK_MS) / 1000
    assert.ok((budget - waiting) / (n - 1) >= 3.5, `${n} symbols leave ${(budget - waiting) / (n - 1)} s per answer`)
  }
  assert.equal(timeBudgetSeconds(0), 0)
  assert.equal(timeBudgetSeconds(1), 0)
})

test('the old viewing-phase clock (30 s for 1.8 s x steps) would end easy and medium before the sequence finished', () => {
  // Characterises the removed behaviour: 20 x 1.8 = 36 s and 18 x 1.8 = 32.4 s, both over the 30 s viewing timer.
  assert.ok(20 * 1.8 > 30 && 18 * 1.8 > 30)
  // The new budget is never shorter than showing every symbol with its feedback pause.
  for (const n of [16, 18, 20]) assert.ok(timeBudgetSeconds(n) > (FIRST_SYMBOL_MS + (n - 1) * FEEDBACK_MS) / 1000)
})

test('parseMemorySingle tolerates garbage', () => {
  assert.deepEqual(parseMemorySingle({ question: '', answer: '' }).symbols, [])
  assert.deepEqual(parseMemorySingle({}).symbols, [])
  assert.deepEqual(parseMemorySingle({ question: '{"binarySequence":[0,1]}', answer: '' }).symbols, [0, 1])
})
