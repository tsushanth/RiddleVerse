import test from 'node:test'
import assert from 'node:assert/strict'
import {
  createWebPlayAnalytics, createRoundTracker, trackingSuppressed, WEB_PLAY_EVENTS, INSTALL_KEY,
} from '../../src/utils/webPlayAnalytics.js'

function memStore(initial = {}) {
  const m = new Map(Object.entries(initial))
  return {
    getItem: (k) => (m.has(k) ? m.get(k) : null),
    setItem: (k, v) => { m.set(k, String(v)) },
    _m: m,
  }
}

function setup(over = {}) {
  const calls = []
  let n = 0
  const env = {
    navigator: { doNotTrack: null },
    localStorage: memStore(),
    sessionStorage: memStore(),
    fetch: async (url, init) => { calls.push({ url, init, body: JSON.parse(init.body) }); return { ok: true } },
    endpoint: 'https://example.test/api/puzzle-events',
    now: () => 1_800_000_000_000,
    uuid: () => `id-${++n}`,
    ...over,
  }
  return { env, calls, a: createWebPlayAnalytics(env) }
}

test('view: one POST to the existing endpoint with the contract shape and nothing else', () => {
  const { a, calls } = setup()
  a.view('sudoku')
  assert.equal(calls.length, 1)
  assert.equal(calls[0].url, 'https://example.test/api/puzzle-events')
  assert.equal(calls[0].init.method, 'POST')
  const b = calls[0].body
  assert.deepEqual(Object.keys(b).sort(), ['appVersion', 'events', 'installId', 'platform'])
  assert.equal(b.platform, 'web')
  assert.equal(b.events.length, 1)
  assert.deepEqual(b.events[0], { type: 'web_play_view', puzzleType: 'sudoku', sessionId: b.events[0].sessionId, ts: 1_800_000_000_000 })
  assert.ok(b.events[0].sessionId)
})

test('event names are exactly the four allow-listed ones', () => {
  assert.deepEqual(Object.values(WEB_PLAY_EVENTS).sort(), ['web_play_complete', 'web_play_cta', 'web_play_start', 'web_play_view'])
})

test('view fires once per slug per browser session, also across a reload (sessionStorage)', () => {
  const { a, calls, env } = setup()
  a.view('sudoku'); a.view('sudoku')
  assert.equal(calls.length, 1)
  a.view('wordladder')
  assert.equal(calls.length, 2)
  const reloaded = createWebPlayAnalytics(env) // same sessionStorage, new page load
  reloaded.view('sudoku')
  assert.equal(calls.length, 2)
})

test('view dedupe falls back to memory when sessionStorage throws', () => {
  const boom = { getItem() { throw new Error('denied') }, setItem() { throw new Error('denied') } }
  const { a, calls } = setup({ sessionStorage: boom })
  a.view('sudoku'); a.view('sudoku')
  assert.equal(calls.length, 1)
})

test('Global Privacy Control suppresses everything and writes nothing to storage', () => {
  const { a, calls, env } = setup({ navigator: { globalPrivacyControl: true } })
  a.view('sudoku'); a.start('sudoku', 'easy'); a.complete('sudoku', { durationMs: 5 }); a.cta('sudoku')
  assert.equal(calls.length, 0)
  assert.equal(env.localStorage._m.size, 0)
  assert.equal(env.sessionStorage._m.size, 0)
})

test('Do Not Track suppresses ("1" and "yes", navigator or window), "0" does not', () => {
  for (const nav of [{ doNotTrack: '1' }, { doNotTrack: 'yes' }, { msDoNotTrack: '1' }]) {
    const { a, calls } = setup({ navigator: nav })
    a.view('sudoku')
    assert.equal(calls.length, 0, JSON.stringify(nav))
  }
  const w = setup({ navigator: {}, window: { doNotTrack: '1' } })
  w.a.view('sudoku')
  assert.equal(w.calls.length, 0)
  assert.equal(trackingSuppressed({ doNotTrack: '0' }), false)
  assert.equal(trackingSuppressed({}), false)
  assert.equal(trackingSuppressed({ globalPrivacyControl: false }), false)
  assert.equal(trackingSuppressed(undefined), false)
})

test('GPC/DNT is re-read on every call (user flips it mid-session)', () => {
  const nav = { doNotTrack: null }
  const { a, calls } = setup({ navigator: nav })
  a.view('sudoku')
  nav.globalPrivacyControl = true
  a.start('sudoku', 'easy')
  assert.equal(calls.length, 1)
})

test('start / complete / cta carry slug, sessionId and only the allowed extras', () => {
  const { a, calls } = setup()
  a.start('sudoku', 'medium')
  a.complete('sudoku', { difficulty: 'medium', durationMs: 4321.9 })
  a.cta('sudoku')
  const [s, c, k] = calls.map((x) => x.body.events[0])
  assert.equal(s.type, 'web_play_start'); assert.equal(s.difficulty, 'medium')
  assert.equal(c.type, 'web_play_complete'); assert.equal(c.durationMs, 4321); assert.equal(c.difficulty, 'medium')
  assert.equal(k.type, 'web_play_cta'); assert.equal(k.puzzleType, 'sudoku')
  assert.equal(s.sessionId, c.sessionId); assert.equal(c.sessionId, k.sessionId)
  assert.equal('difficulty' in k, false)
  assert.equal('durationMs' in s, false)
})

test('bad difficulty / duration values are dropped, not forwarded', () => {
  const { a, calls } = setup()
  a.complete('sudoku', { difficulty: 'a@b.c', durationMs: -5 })
  a.complete('sudoku', { difficulty: 3, durationMs: 'x' })
  for (const c of calls) {
    const e = c.body.events[0]
    assert.equal('difficulty' in e, false)
    assert.equal('durationMs' in e, false)
  }
})

test('invalid slugs are not sent (uppercase, empty, too long, reserved "app", non-string)', () => {
  const { a, calls } = setup()
  for (const s of ['Sudoku', '', 'a'.repeat(41), 'app', null, undefined, 5, 'a-b', '../x']) a.cta(s)
  assert.equal(calls.length, 0)
})

test('no PII: payload has no url, referrer, user agent, language, screen, or caller-supplied extras', () => {
  const { a, calls } = setup({
    navigator: { userAgent: 'UA-SECRET', language: 'en-XX' },
    window: { location: { href: 'https://x.test/play/sudoku?email=a@b.c' }, document: { referrer: 'https://ref.test' } },
  })
  a.view('sudoku')
  a.complete('sudoku', { difficulty: 'easy', durationMs: 10, email: 'a@b.c', userId: 'u1', answer: '42', errorMessage: 'e' })
  const text = calls.map((c) => c.init.body).join('')
  for (const bad of ['UA-SECRET', 'en-XX', 'a@b.c', 'ref.test', 'u1', '"42"', 'errorMessage', 'location']) {
    assert.equal(text.includes(bad), false, bad)
  }
  const allowed = new Set(['type', 'puzzleType', 'sessionId', 'ts', 'difficulty', 'durationMs'])
  for (const c of calls) for (const k of Object.keys(c.body.events[0])) assert.ok(allowed.has(k), k)
  assert.equal(calls[0].init.credentials, undefined) // no cookies attached
})

test('install-like id: random, stored under its own localStorage key, reused, never an auth/cookie value', () => {
  const { a, calls, env } = setup()
  a.view('sudoku'); a.cta('sudoku')
  const id = calls[0].body.installId
  assert.ok(id && id.length <= 64)
  assert.equal(calls[1].body.installId, id)
  assert.equal(env.localStorage.getItem(INSTALL_KEY), id)
  assert.equal(INSTALL_KEY === 'rv_install_id', false) // not the signed-in app's id
  const again = createWebPlayAnalytics(env)
  again.cta('sudoku')
  assert.equal(calls[2].body.installId, id)
})

test('works (ephemeral id) when localStorage is unavailable, and never throws', async () => {
  const boom = { getItem() { throw new Error('denied') }, setItem() { throw new Error('denied') } }
  const { a, calls } = setup({ localStorage: boom })
  a.view('sudoku'); a.cta('sudoku')
  assert.equal(calls.length, 2)
  assert.equal(calls[0].body.installId, calls[1].body.installId)

  const f1 = createWebPlayAnalytics({ ...setup().env, fetch: () => { throw new Error('sync') } })
  const f2 = createWebPlayAnalytics({ ...setup().env, fetch: () => Promise.reject(new Error('async')) })
  const f3 = createWebPlayAnalytics({ ...setup().env, fetch: undefined })
  for (const x of [f1, f2, f3]) { x.view('sudoku'); x.start('sudoku'); x.complete('sudoku'); x.cta('sudoku') }
  await new Promise((r) => setTimeout(r, 5))
})

test('round tracker: start on first interaction only, complete only after start and only if correct, then re-arms', () => {
  const { a, calls } = setup()
  const t = createRoundTracker(a, 'sudoku')
  t.answered(true, 'easy') // no start yet
  assert.equal(calls.length, 0)
  t.interact('easy'); t.interact('easy')
  assert.equal(calls.length, 1)
  assert.equal(calls[0].body.events[0].type, 'web_play_start')
  t.answered(false, 'easy') // wrong answer / gave up: not a completion
  assert.equal(calls.length, 1)
  t.answered(true, 'easy')
  assert.equal(calls.length, 2)
  const c = calls[1].body.events[0]
  assert.equal(c.type, 'web_play_complete')
  assert.ok(Number.isInteger(c.durationMs) && c.durationMs >= 0)
  t.answered(true, 'easy') // duplicate callback after completion
  assert.equal(calls.length, 2)
  t.interact('easy') // next game in the same page counts as a new start
  assert.equal(calls.length, 3)
  t.reset(); t.interact('easy')
  assert.equal(calls.length, 4)
})
