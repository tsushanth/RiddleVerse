// Port of Android DailyShareTextTest.kt. Web differs only in the campaign link (utm_medium=web_daily).
import test from 'node:test'
import assert from 'node:assert/strict'
import { PLAY_URL, formatTime, attemptGrid, headline, buildShareText } from '../../src/daily/dailyShare.js'
import { specFor, FIRST_DAY } from '../../src/daily/dailySchedule.js'

const r = (o = {}) => ({ number: 123, gameName: 'Sudoku', seconds: 102, attempts: 1, streak: 5, ...o })

test('headline matches the spec example', () => {
  assert.equal(headline(r()), 'RiddleVerse Daily #123 ✅ 1:42 \u{1F525}5')
  assert.equal(headline(r()), 'RiddleVerse Daily #123 ✅ 1:42 🔥5')
})

test('headline omits the flame when there is no streak', () => {
  assert.equal(headline(r({ number: 7, seconds: 7, streak: 0 })), 'RiddleVerse Daily #7 ✅ 0:07')
})

test('time formats', () => {
  assert.equal(formatTime(0), '0:00')
  assert.equal(formatTime(59), '0:59')
  assert.equal(formatTime(60), '1:00')
  assert.equal(formatTime(3599), '59:59')
  assert.equal(formatTime(3600), '1:00:00')
  assert.equal(formatTime(3909), '1:05:09')
  assert.equal(formatTime(-5), '0:00')
  assert.equal(formatTime(NaN), '0:00')
  assert.equal(formatTime(61.9), '1:01')
})

test('grid: one square per try, last is green', () => {
  assert.equal(attemptGrid(1), '🟩')
  assert.equal(attemptGrid(3), '🟥🟥🟩')
  assert.equal(attemptGrid(0), '🟩')
  const long = attemptGrid(40)
  assert.ok(long.endsWith('🟩')); assert.ok(long.includes('…'))
  assert.equal(attemptGrid(6), '🟥🟥🟥🟥🟥🟩')
})

test('full text has headline, game plus grid, and the Play link', () => {
  const lines = buildShareText(r({ attempts: 2 })).split('\n')
  assert.equal(lines.length, 3)
  assert.equal(lines[0], headline(r({ attempts: 2 })))
  assert.equal(lines[1], 'Sudoku 🟥🟩')
  assert.equal(lines[2], PLAY_URL)
})

test('play link is exactly the web daily campaign URL', () => {
  assert.equal(PLAY_URL,
    'https://play.google.com/store/apps/details?id=com.kreativekoala.riddleverse&utm_source=share&utm_medium=web_daily&utm_campaign=card')
})

test('text never contains the seed or answer fields', () => {
  const spec = specFor(FIRST_DAY + 3)
  const text = buildShareText(r({ gameName: 'Word Hive' }))
  assert.ok(!text.includes(spec.seed.toString()))
  assert.ok(!/solution|answer/i.test(text))
})
