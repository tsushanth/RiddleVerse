// The legacy game-shell telemetry (utils/puzzleEvents.js) must honour Global Privacy Control / Do Not Track on the public
// /play pages, using the same check as webPlayAnalytics. Loaded through vite's SSR loader (the module reads import.meta.env).
import test, { before, after, beforeEach } from 'node:test'
import assert from 'node:assert/strict'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { createServer } from 'vite'

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..', '..')
let server, P, W
const posts = []

function setEnv({ pathname = '/play/sudoku', nav = {} } = {}) {
  Object.defineProperty(globalThis, 'navigator', { value: nav, configurable: true, writable: true })
  globalThis.window = { location: { pathname }, addEventListener() {}, doNotTrack: undefined }
  globalThis.document = { addEventListener() {}, visibilityState: 'visible' }
}

before(async () => {
  server = await createServer({ root, server: { middlewareMode: true }, appType: 'custom', logLevel: 'error' })
  P = await server.ssrLoadModule('/src/utils/puzzleEvents.js')
  W = await server.ssrLoadModule('/src/utils/webPlayAnalytics.js')
  globalThis.fetch = async (url, init) => { posts.push(JSON.parse(init.body)); return { ok: true } }
  globalThis.localStorage = { getItem: () => null, setItem() {} }
})
after(async () => { P?._stopPuzzleEventTimer(); await server?.close() })
beforeEach(() => { posts.length = 0 })

const sent = () => posts.flatMap((p) => p.events)

test('baseline: with no privacy signal, /play shell events are sent', () => {
  setEnv()
  P.trackPuzzleEvent('start', 'sudoku', { difficulty: 'easy' })
  P.flushPuzzleEvents()
  assert.deepEqual(sent().map((e) => e.type), ['start'])
})

test('GPC on a /play page: start and complete are not queued or sent', () => {
  setEnv({ nav: { globalPrivacyControl: true } })
  P.trackPuzzleEvent('start', 'sudoku'); P.trackPuzzleEvent('complete', 'sudoku', { durationMs: 5 })
  P.flushPuzzleEvents(); P.flushPuzzleEvents(true)
  assert.equal(posts.length, 0)
})

test('DNT on a /play page (navigator "1" / "yes", window "1"): nothing is sent', () => {
  for (const nav of [{ doNotTrack: '1' }, { doNotTrack: 'yes' }, { msDoNotTrack: '1' }]) {
    setEnv({ nav })
    P.trackPuzzleEvent('start', 'sudoku'); P.flushPuzzleEvents()
  }
  setEnv(); globalThis.window.doNotTrack = '1'
  P.trackPuzzleEvent('complete', 'sudoku'); P.flushPuzzleEvents()
  assert.equal(posts.length, 0)
})

test('DNT "0" does not suppress', () => {
  setEnv({ nav: { doNotTrack: '0' } })
  P.trackPuzzleEvent('start', 'sudoku'); P.flushPuzzleEvents()
  assert.equal(sent().length, 1)
})

test('the flag is re-read on every call (flipped mid-session), and an event queued before the flip is not sent after it', () => {
  const nav = {}
  setEnv({ nav })
  P.trackPuzzleEvent('start', 'sudoku')
  nav.globalPrivacyControl = true
  P.flushPuzzleEvents()
  assert.equal(posts.length, 0)
  nav.globalPrivacyControl = false
  P.trackPuzzleEvent('start', 'sudoku'); P.flushPuzzleEvents()
  assert.equal(sent().length, 1)
})

test('outside /play (the signed-in app) behaviour is unchanged even with GPC on', () => {
  setEnv({ pathname: '/puzzle/sudoku', nav: { globalPrivacyControl: true } })
  P.trackPuzzleEvent('start', 'sudoku'); P.flushPuzzleEvents()
  assert.equal(sent().length, 1)
})

test('shared helper: same decision as trackingSuppressed, only on /play and /play/...', () => {
  const gpc = { globalPrivacyControl: true }
  const at = (p) => ({ location: { pathname: p } })
  assert.equal(W.suppressedOnPlayPage(gpc, at('/play')), true)
  assert.equal(W.suppressedOnPlayPage(gpc, at('/play/daily')), true)
  assert.equal(W.suppressedOnPlayPage(gpc, at('/playground')), false)
  assert.equal(W.suppressedOnPlayPage(gpc, at('/puzzle/x')), false)
  assert.equal(W.suppressedOnPlayPage({}, at('/play/x')), false)
  assert.equal(W.suppressedOnPlayPage(gpc, undefined), false)
})
