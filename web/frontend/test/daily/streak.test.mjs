// Port of Android DailyStreakRulesTest.kt (the open-streak migration is Android-only: the web has no old streak).
import test from 'node:test'
import assert from 'node:assert/strict'
import { EMPTY_STREAK, applySolve, display, solvedOn, loadDailyState, saveDailyState, emptyDailyState } from '../../src/daily/dailyStreak.js'
import { dayOf } from '../../src/daily/dailySchedule.js'

const none = EMPTY_STREAK
const d0 = 20_000
const utcDay = (iso) => dayOf(Date.parse(iso))

test('first solve starts at one', () => {
  const o = applySolve(none, d0)
  assert.equal(o.counted, true); assert.equal(o.streak, 1); assert.equal(o.state.lastSolveDay, d0)
  assert.equal(o.state.best, 1); assert.equal(o.state.totalSolved, 1)
})

test('consecutive days extend', () => {
  let s = none
  for (let i = 0; i < 5; i++) s = applySolve(s, d0 + i).state
  assert.equal(s.current, 5); assert.equal(s.best, 5); assert.equal(s.totalSolved, 5)
})

test('same day twice is idempotent', () => {
  const once = applySolve(none, d0).state
  const twice = applySolve(once, d0)
  assert.equal(twice.counted, false)
  assert.deepEqual(twice.state, once)
  assert.equal(twice.streak, 1)
})

test('missed day resets to one but keeps best', () => {
  let s = none
  for (let i = 0; i < 4; i++) s = applySolve(s, d0 + i).state
  const o = applySolve(s, d0 + 3 + 2)
  assert.equal(o.streak, 1); assert.equal(o.state.best, 4)
})

test('clock moved back does not count or break', () => {
  const s = applySolve(none, d0 + 10).state
  const o = applySolve(s, d0 + 5)
  assert.equal(o.counted, false); assert.deepEqual(o.state, s)
})

test('display is alive today and tomorrow, then zero', () => {
  const s = applySolve(none, d0).state
  assert.equal(display(s, d0), 1)
  assert.equal(display(s, d0 + 1), 1)
  assert.equal(display(s, d0 + 2), 0)
  assert.equal(display(none, d0), 0)
})

test('solvedOn', () => {
  const s = applySolve(none, d0).state
  assert.equal(solvedOn(s, d0), true); assert.equal(solvedOn(s, d0 + 1), false)
  assert.equal(solvedOn(none, d0), false)
})

test('applySolve does not mutate its input', () => {
  const s = Object.freeze({ ...applySolve(none, d0).state })
  applySolve(s, d0 + 1)
  assert.equal(s.current, 1)
})

test('time zone boundary: late in Tokyo then early in Los Angeles are consecutive UTC days', () => {
  const a = utcDay('2026-11-02T08:30:00+09:00') // 23:30 UTC Nov 1
  const b = utcDay('2026-11-02T01:00:00-08:00') // 09:00 UTC Nov 2
  assert.equal(b, a + 1)
  const s1 = applySolve(none, a).state
  assert.equal(applySolve(s1, b).streak, 2)
})

test('time zone boundary: a traveller cannot solve twice in one UTC day', () => {
  const t = utcDay('2026-11-02T07:00:00+09:00') // 22:00 UTC Nov 1
  const h = utcDay('2026-11-01T13:00:00-10:00') // 23:00 UTC Nov 1
  assert.equal(t, h)
  const once = applySolve(none, t).state
  assert.equal(applySolve(once, h).counted, false)
})

// ---- localStorage layer ----
function fakeStorage(initial) {
  const m = new Map(initial ? [['rv_daily_v1', initial]] : [])
  return { getItem: (k) => (m.has(k) ? m.get(k) : null), setItem: (k, v) => m.set(k, String(v)), _m: m }
}

test('storage: round trip', () => {
  const st = fakeStorage()
  const state = { ...emptyDailyState(), streak: applySolve(none, d0).state, result: { day: d0, seconds: 95, attempts: 2 } }
  saveDailyState(st, state)
  assert.deepEqual(loadDailyState(st), state)
})

test('storage: missing, corrupt, wrong-shape and hostile values fall back to empty', () => {
  assert.deepEqual(loadDailyState(fakeStorage()), emptyDailyState())
  assert.deepEqual(loadDailyState(fakeStorage('{not json')), emptyDailyState())
  assert.deepEqual(loadDailyState(fakeStorage('[1,2]')), emptyDailyState())
  const bad = loadDailyState(fakeStorage(JSON.stringify({ streak: { current: -4, best: 'x', lastSolveDay: 'soon', totalSolved: 1.5 }, result: { day: 'a' } })))
  assert.deepEqual(bad, emptyDailyState())
  assert.deepEqual(loadDailyState(null), emptyDailyState())
})

test('storage: a throwing storage (private mode) never crashes', () => {
  const boom = { getItem() { throw new Error('denied') }, setItem() { throw new Error('denied') } }
  assert.deepEqual(loadDailyState(boom), emptyDailyState())
  assert.doesNotThrow(() => saveDailyState(boom, emptyDailyState()))
})
