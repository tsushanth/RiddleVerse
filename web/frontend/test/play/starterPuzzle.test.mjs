import test from 'node:test'
import assert from 'node:assert/strict'
import {
  createStarter, enterDigit, selectCell, isNewVisitor, remember, STARTER_CLUES, STARTER_DONE_KEY, STARTER_SLUG,
} from '../../src/utils/starterPuzzle.js'

/** Every blank can be filled one at a time because only one digit fits it. */
function solvableBySingles(given, solution) {
  const g = given.slice()
  while (g.includes(0)) {
    let progressed = false
    for (let i = 0; i < 16 && !progressed; i++) {
      if (g[i] !== 0) continue
      const r = Math.floor(i / 4)
      const c = i % 4
      const used = new Set()
      for (let k = 0; k < 4; k++) { used.add(g[r * 4 + k]); used.add(g[k * 4 + c]) }
      for (let dr = 0; dr < 2; dr++) for (let dc = 0; dc < 2; dc++) used.add(g[(Math.floor(r / 2) * 2 + dr) * 4 + Math.floor(c / 2) * 2 + dc])
      const fits = [1, 2, 3, 4].filter((d) => !used.has(d))
      if (fits.length === 1) { g[i] = fits[0]; progressed = true }
    }
    if (!progressed) return false
  }
  return g.every((v, i) => v === solution[i])
}

test('five blanks, each solvable by looking at one square', () => {
  for (let seed = 0; seed < 500; seed++) {
    const s = createStarter(seed)
    assert.equal(s.given.filter((v) => v === 0).length, 16 - STARTER_CLUES, `seed ${seed}`)
    assert.ok(solvableBySingles(s.given, s.solution), `seed ${seed}`)
  }
})

test('five right taps finish it without selecting a cell', () => {
  let s = createStarter(3)
  let taps = 0
  while (!s.solved && taps < 20) { s = enterDigit(s, s.solution[s.selected]); taps++ }
  assert.equal(taps, 5)
  assert.equal(s.solved, true)
})

test('wrong taps never end it, and givens cannot be changed', () => {
  let s = createStarter(3)
  const cell = s.selected
  const wrong = [1, 2, 3, 4].filter((d) => d !== s.solution[cell])
  for (let i = 0; i < 12; i++) s = enterDigit(s, wrong[i % wrong.length])
  assert.equal(s.solved, false)
  assert.equal(s.selected, cell)
  s = enterDigit(s, s.solution[cell])
  assert.notEqual(s.selected, cell)
  const given = s.given.findIndex((v) => v !== 0)
  assert.equal(selectCell(s, given), s)
})

test('only a first-time visitor sees it', () => {
  const store = new Map()
  const get = () => ({ getItem: (k) => store.get(k) ?? null, setItem: (k, v) => store.set(k, v) })
  assert.equal(isNewVisitor(get), true)
  remember(get, STARTER_DONE_KEY)
  assert.equal(isNewVisitor(get), false)
  store.clear()
  store.set('userId', 'x')
  assert.equal(isNewVisitor(get), false)
  assert.equal(isNewVisitor(() => { throw new Error('blocked') }), false)
})

test('its events pass the server rules for web play', async () => {
  const { validatePayload } = await import('../../../backend/services/puzzleMonitoring.js')
  const v = validatePayload({ installId: 'a', platform: 'web', events: [{ type: 'web_play_view', puzzleType: STARTER_SLUG }] })
  assert.equal(v.events[0].puzzle_type, 'starter')
})
