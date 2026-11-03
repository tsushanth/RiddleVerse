// Port of Android DailyPuzzleScheduleTest.kt. The pinned vectors are copied from the Kotlin test:
// if one fails, the rotation, shuffle or seed mixing changed and every player's puzzle would change. Don't.
import test from 'node:test'
import assert from 'node:assert/strict'
import {
  FIRST_DAY, ROTATION, DAY_MS, DIFFICULTY, dayOf, numberOf, millisUntilNext, specFor, order, seedFor, SplitMix64,
} from '../../src/daily/dailySchedule.js'

const first = FIRST_DAY
const utcMs = (iso) => Date.parse(iso)

test('firstDay is 2026-11-01 (epoch day 20758) and numbered one', () => {
  assert.equal(first, Math.floor(Date.UTC(2026, 10, 1) / DAY_MS))
  assert.equal(first, 20758)
  assert.equal(numberOf(first), 1)
  assert.equal(numberOf(first + 122), 123)
})

test('rotation is exactly the Android list, in order, and difficulty is Medium', () => {
  assert.deepEqual(ROTATION, ['sudoku', 'binarygrid', 'signgrid', 'pixellogic', 'tubetint', 'conduit', 'crateshift', 'wordladder', 'wordhive'])
  assert.equal(DIFFICULTY, 'Medium')
})

test('sameDay gives the same spec every time', () => {
  for (let d = first - 20; d <= first + 400; d++) assert.deepEqual(specFor(d), specFor(d))
})

test('pinned values for Daily #1..#12 never change (golden vectors from the Kotlin test)', () => {
  const expected = [
    ['crateshift', '4726940851620634300'], ['tubetint', '3169490807060299793'], ['pixellogic', '3931529243979955455'],
    ['wordhive', '6292848748641144246'], ['signgrid', '2836158810257466357'], ['binarygrid', '2599900883310011191'],
    ['sudoku', '3211176445299877934'], ['conduit', '4650300333674332901'], ['wordladder', '5280230434093151369'],
    ['pixellogic', '7175925850101726451'], ['wordhive', '3978227786564930015'], ['crateshift', '5146235931912561850'],
  ]
  expected.forEach(([type, seed], i) => {
    const spec = specFor(first + i)
    assert.equal(spec.number, i + 1, `number ${i}`)
    assert.equal(spec.type, type, `type #${i + 1}`)
    assert.equal(spec.seed.toString(), seed, `seed #${i + 1}`)
  })
})

test('every instant of a UTC day maps to that day', () => {
  const d = Math.floor(Date.UTC(2027, 2, 14) / DAY_MS)
  const start = utcMs('2027-03-14T00:00:00Z')
  assert.equal(dayOf(start), d)
  assert.equal(dayOf(start + DAY_MS - 1), d)
  assert.equal(dayOf(start + DAY_MS), d + 1)
  assert.equal(dayOf(start - 1), d - 1)
})

test('phone time zone does not matter', () => {
  const tokyo = utcMs('2026-11-02T00:30:00+09:00')
  const la = utcMs('2026-11-01T07:30:00-08:00')
  assert.equal(tokyo, la)
  assert.equal(dayOf(tokyo), dayOf(la))
  assert.equal(dayOf(tokyo), first)
})

test('days before the epoch floor correctly', () => {
  assert.equal(dayOf(-1), -1)
  assert.equal(dayOf(0), 0)
})

test('countdown is between 1 ms and one day and hits a full day at midnight', () => {
  const start = first * DAY_MS
  assert.equal(millisUntilNext(start), DAY_MS)
  assert.equal(millisUntilNext(start + DAY_MS - 1), 1)
  assert.equal(millisUntilNext(start + DAY_MS - 3_600_000), 3_600_000)
})

test('each cycle plays every game exactly once', () => {
  const n = ROTATION.length
  for (let cycle = -3; cycle <= 60; cycle++) {
    const types = Array.from({ length: n }, (_, i) => specFor(first + cycle * n + i).type)
    assert.deepEqual(new Set(types), new Set(ROTATION), `cycle ${cycle}`)
    assert.equal(types.length, n)
  }
})

test('never the same game two days running', () => {
  let prev = null
  for (let d = first - 100; d <= first + 3000; d++) {
    const t = specFor(d).type
    assert.notEqual(t, prev, `day ${d}`)
    prev = t
  }
})

test('excluded games never appear', () => {
  const banned = new Set([
    'mathcrossword', 'mathexpression', 'numbersum', 'memorysquares', 'memorypreviouspair', 'memoryprevioussingle',
    'cagemath', 'tilemerge', 'slidetiles', 'lightflip', 'safesweep', 'disctower', 'piecepack', 'letterballoons',
    'wordguess', 'codecracker',
  ])
  assert.ok(ROTATION.every((t) => !banned.has(t)))
  for (let d = first; d <= first + 500; d++) assert.ok(!banned.has(specFor(d).type))
})

test('seeds differ across days and are non-negative 63-bit BigInts', () => {
  const seeds = []
  for (let d = first; d <= first + 2000; d++) seeds.push(specFor(d).seed)
  assert.equal(new Set(seeds.map(String)).size, seeds.length)
  assert.ok(seeds.every((s) => typeof s === 'bigint' && s >= 0n && s < 2n ** 63n))
})

test('rotation is distributed evenly over 100 cycles', () => {
  const counts = {}
  for (let d = first; d < first + 900; d++) counts[specFor(d).type] = (counts[specFor(d).type] || 0) + 1
  assert.equal(Object.keys(counts).length, 9)
  assert.ok(Object.values(counts).every((c) => c === 100))
})

test('SplitMix64 matches the reference generator (seed 0 first outputs)', () => {
  // Well-known SplitMix64 test vector for state 0.
  const g = new SplitMix64(0n)
  assert.equal(g.next(), 0xE220A8397B1DCDAFn)
  assert.equal(g.next(), 0x6E789E6AA1B965F4n)
  assert.equal(g.next(), 0x06C45D188009454Fn)
})

test('order() and seedFor() are pure helpers: a permutation, and the seed depends on the game', () => {
  assert.deepEqual([...order(5)].sort(), [...ROTATION].sort())
  assert.notEqual(seedFor(first, 'sudoku'), seedFor(first, 'conduit'))
})

test('webSeedFor: deterministic, within the 31-bit range the web engines take', async () => {
  const { webSeedFor } = await import('../../src/daily/dailySchedule.js')
  for (let d = first; d < first + 300; d++) {
    const a = webSeedFor(specFor(d)); const b = webSeedFor(specFor(d))
    assert.equal(a, b)
    assert.ok(Number.isInteger(a) && a >= 1 && a <= 2147483646)
  }
})

test('every rotation game is in the public play catalog (the daily page renders it through /play)', async () => {
  const { readFileSync } = await import('node:fs')
  const cat = JSON.parse(readFileSync(new URL('../../../shared/playCatalog.json', import.meta.url), 'utf8'))
  const slugs = new Set(cat.games.map((g) => g.slug))
  for (const t of ROTATION) assert.ok(slugs.has(t), t)
  assert.ok(!slugs.has('daily'), '"daily" is reserved for the /play/daily route')
})
