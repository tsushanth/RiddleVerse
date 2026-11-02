import test from 'node:test'
import assert from 'node:assert/strict'
import { isCorrectChoice, sameAnswer } from '../../src/components/puzzles/choiceJudge.js'

// The Android multiple-choice screen handed `true` to its result callback instead of the tapped option, so every
// Math/Trivia/Q&A/Sentence-Transitions answer scored wrong. The web passes the tapped option text; these tests pin
// that the web judges the TAPPED OPTION against the key, in every shape the server can send the key in.

test('tapped option equal to the key is correct, any other option is wrong', () => {
  const options = ['Paris', 'Rome', 'Berlin', 'Madrid']
  const key = 'Paris'
  assert.deepEqual(options.map((o) => isCorrectChoice(o, key)), [true, false, false, false])
})

test('a boolean (the Android bug: callback received `true`) is never a correct answer', () => {
  assert.equal(isCorrectChoice(true, 'true'), false)
  assert.equal(isCorrectChoice(true, '42'), false)
  assert.equal(isCorrectChoice(undefined, '42'), false)
  assert.equal(isCorrectChoice(null, ''), false)
})

test('case and surrounding whitespace do not matter', () => {
  assert.equal(isCorrectChoice('  paris ', 'Paris'), true)
  assert.equal(isCorrectChoice('HOWEVER', 'however'), true)
})

test('math: numbers compare by value, not by formatting ("42" == "42.0" == "42.00" == 42)', () => {
  assert.equal(isCorrectChoice('42', '42.0'), true)
  assert.equal(isCorrectChoice('42.00', '42'), true)
  assert.equal(isCorrectChoice('1,250', '1250'), true)
  assert.equal(isCorrectChoice('42', 42), true) // DB / JSON can hand the key over as a number
  assert.equal(isCorrectChoice(42, '42'), true) // and options as numbers
  assert.equal(isCorrectChoice('42', '43'), false)
  assert.equal(isCorrectChoice('0.5', '0.50'), true)
  assert.equal(isCorrectChoice('0.5', '5'), false)
})

test('a missing/blank key never matches a blank option (a timed-out answer is "")', () => {
  assert.equal(isCorrectChoice('', ''), false)
  assert.equal(isCorrectChoice('  ', undefined), false)
  assert.equal(sameAnswer('', ''), false)
})

test('does not treat different words that merely look numeric-ish as equal', () => {
  assert.equal(isCorrectChoice('1st', '1'), false)
  assert.equal(isCorrectChoice('10%', '10'), false)
  assert.equal(isCorrectChoice('abc', 'abd'), false)
})
