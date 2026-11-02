// The share card is drawn on a canvas from the same spoiler-free result as the text. A recording fake context
// proves what gets drawn: only the brand line, number, time, game name, tries squares and streak.
import test from 'node:test'
import assert from 'node:assert/strict'
import { drawShareCard, CARD_SIZE } from '../../src/daily/dailyShareCard.js'
import { specFor, FIRST_DAY } from '../../src/daily/dailySchedule.js'

function fakeCtx() {
  const texts = []; const rects = []; const grads = []
  const ctx = {
    texts, rects, grads,
    createLinearGradient: (...a) => { const g = { stops: [], addColorStop(o, c) { g.stops.push([o, c]) } }; grads.push(a); return g },
    fillRect: (...a) => rects.push(['fillRect', ...a]),
    beginPath() {}, closePath() {}, moveTo() {}, lineTo() {}, arcTo() {}, quadraticCurveTo() {}, fill() {},
    roundRect: (...a) => rects.push(['roundRect', ...a, ctx.fillStyle]),
    fillText: (t, x, y) => texts.push(t),
  }
  return ctx
}
const r = (o = {}) => ({ number: 12, gameName: 'Word Hive', seconds: 95, attempts: 3, streak: 4, ...o })

test('card is 1080 square and draws the expected lines', () => {
  assert.equal(CARD_SIZE, 1080)
  const c = fakeCtx(); drawShareCard(c, r())
  assert.deepEqual(c.texts, ['RiddleVerse', 'Daily #12', 'SOLVED', '1:35', 'Word Hive', '\u{1F525} 4 day streak', "Play today's puzzle on Google Play"])
})

test('no streak line when streak is 0', () => {
  const c = fakeCtx(); drawShareCard(c, r({ streak: 0 }))
  assert.ok(!c.texts.some((t) => t.includes('streak')))
})

test('one square per try, capped at six', () => {
  const squares = (a) => { const c = fakeCtx(); drawShareCard(c, r({ attempts: a })); return c.rects.filter((x) => x[0] === 'roundRect' && x[3] === 84).length }
  assert.equal(squares(1), 1); assert.equal(squares(3), 3); assert.equal(squares(40), 6); assert.equal(squares(0), 1)
})

test('card never draws a seed, a solution or any field it was not given', () => {
  const spec = specFor(FIRST_DAY + 3)
  const c = fakeCtx(); drawShareCard(c, { ...r(), seed: spec.seed, solution: 'SECRET', board: [[1]] })
  const all = c.texts.join('|')
  assert.ok(!all.includes(spec.seed.toString())); assert.ok(!/SECRET/.test(all))
})
