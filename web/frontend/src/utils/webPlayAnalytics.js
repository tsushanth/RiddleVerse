// Privacy-respecting measurement for the public, no-login /play/<slug> pages.
// Reuses POST /api/puzzle-events (platform 'web') with four event names:
//   web_play_view      page opened (once per slug per browser session)
//   web_play_start     first interaction with the game (once per round)
//   web_play_complete  puzzle solved (correct answer only)
//   web_play_cta       click on "Get it on Google Play" (its link carries utm_campaign = the same slug)
//
// Privacy: sends nothing at all when Global Privacy Control or Do Not Track is on (re-read on every call).
// The only identifier is a random id kept in localStorage under its own key (not the signed-in app's id, not a
// cookie, not derived from the device). No URL, referrer, user agent, language, screen size, IP handling
// (the server never stores the IP), query string, answers or free text. Never throws; no queue, no retries.
// Free of imports and of top-level browser globals so node tests can load it.

export const WEB_PLAY_EVENTS = {
  view: 'web_play_view',
  start: 'web_play_start',
  complete: 'web_play_complete',
  cta: 'web_play_cta',
}
export const INSTALL_KEY = 'rv_play_vid'
const VIEW_KEY_PREFIX = 'rv_play_viewed_'
const APP_VERSION = 'web-play'
const SLUG_RE = /^[a-z0-9_]{1,40}$/
const DIFFICULTIES = ['easy', 'medium', 'hard']
const MAX_DURATION_MS = 24 * 3600 * 1000

/** True when the visitor asked not to be tracked (GPC, or DNT in any of its spellings). */
export function trackingSuppressed(nav, win) {
  try {
    if (nav && nav.globalPrivacyControl === true) return true
    const dnt = [nav && nav.doNotTrack, nav && nav.msDoNotTrack, win && win.doNotTrack]
    return dnt.some((v) => v === '1' || v === 'yes' || v === 1 || v === true)
  } catch {
    return false
  }
}

function randomId() {
  try {
    if (typeof crypto !== 'undefined' && crypto.randomUUID) return crypto.randomUUID()
  } catch { /* fall through */ }
  return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, (c) => {
    const r = (Math.random() * 16) | 0
    return (c === 'x' ? r : (r & 0x3) | 0x8).toString(16)
  })
}

function defaultEndpoint() {
  let dev = false
  try { dev = Boolean(import.meta.env && import.meta.env.DEV) } catch { /* not Vite */ }
  return `${dev ? '' : 'https://puzzleverseai.com'}/api/puzzle-events`
}

function g(name) {
  try { return typeof globalThis !== 'undefined' ? globalThis[name] : undefined } catch { return undefined }
}

/** Every dependency is injectable for tests; defaults are read lazily from the browser globals. */
export function createWebPlayAnalytics(env = {}) {
  const nav = () => ('navigator' in env ? env.navigator : g('navigator'))
  const win = () => ('window' in env ? env.window : g('window'))
  const local = () => { try { return 'localStorage' in env ? env.localStorage : g('localStorage') } catch { return undefined } }
  const session = () => { try { return 'sessionStorage' in env ? env.sessionStorage : g('sessionStorage') } catch { return undefined } }
  const uuid = env.uuid || randomId
  const now = env.now || (() => Date.now())

  const pageSessionId = uuid() // per page load, never persisted
  let memoryId = null
  const viewedInMemory = new Set()

  function installId() {
    if (memoryId) return memoryId
    try {
      const ls = local()
      let id = ls && ls.getItem(INSTALL_KEY)
      if (!id) {
        id = uuid()
        if (ls) ls.setItem(INSTALL_KEY, id)
      }
      memoryId = id
    } catch {
      memoryId = uuid()
    }
    return memoryId
  }

  function post(event) {
    try {
      const doFetch = 'fetch' in env ? env.fetch : g('fetch')
      if (typeof doFetch !== 'function') return
      const body = JSON.stringify({ installId: installId(), platform: 'web', appVersion: APP_VERSION, events: [event] })
      const p = doFetch(env.endpoint || defaultEndpoint(), {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body,
        keepalive: true,
      })
      if (p && typeof p.catch === 'function') p.catch(() => {})
    } catch { /* telemetry must never throw */ }
  }

  function emit(type, slug, extra) {
    try {
      if (trackingSuppressed(nav(), win())) return false
      if (typeof slug !== 'string' || !SLUG_RE.test(slug) || slug === 'app') return false
      const ev = { type, puzzleType: slug, sessionId: pageSessionId, ts: now() }
      if (extra && (type === WEB_PLAY_EVENTS.start || type === WEB_PLAY_EVENTS.complete)) {
        if (DIFFICULTIES.includes(extra.difficulty)) ev.difficulty = extra.difficulty
        if (type === WEB_PLAY_EVENTS.complete && typeof extra.durationMs === 'number' && Number.isFinite(extra.durationMs)
          && extra.durationMs >= 0 && extra.durationMs <= MAX_DURATION_MS) {
          ev.durationMs = Math.trunc(extra.durationMs)
        }
      }
      post(ev)
      return true
    } catch {
      return false
    }
  }

  function alreadyViewed(slug) {
    if (viewedInMemory.has(slug)) return true
    try {
      const ss = session()
      return Boolean(ss && ss.getItem(VIEW_KEY_PREFIX + slug))
    } catch {
      return false
    }
  }
  function markViewed(slug) {
    viewedInMemory.add(slug)
    try { const ss = session(); if (ss) ss.setItem(VIEW_KEY_PREFIX + slug, '1') } catch { /* memory set still dedupes */ }
  }

  return {
    view(slug) {
      try {
        if (trackingSuppressed(nav(), win()) || alreadyViewed(slug)) return
        if (emit(WEB_PLAY_EVENTS.view, slug)) markViewed(slug)
      } catch { /* ignore */ }
    },
    start: (slug, difficulty) => { emit(WEB_PLAY_EVENTS.start, slug, { difficulty }) },
    complete: (slug, extra) => { emit(WEB_PLAY_EVENTS.complete, slug, extra || {}) },
    cta: (slug) => { emit(WEB_PLAY_EVENTS.cta, slug) },
  }
}

/**
 * Per-game helper. `start` fires on the first interaction of a round (not on page load, so views and starts differ),
 * `complete` only for a correct answer after a start, then the tracker re-arms so a replay on the same page counts again.
 */
export function createRoundTracker(analytics, slug, now = () => Date.now()) {
  let t0 = null
  return {
    interact(difficulty) {
      if (t0 !== null) return
      t0 = now()
      analytics.start(slug, difficulty)
    },
    answered(isCorrect, difficulty) {
      if (isCorrect !== true || t0 === null) return
      const durationMs = Math.max(0, now() - t0)
      t0 = null
      analytics.complete(slug, { difficulty, durationMs })
    },
    reset() { t0 = null },
  }
}

/** Shared instance for the app (browser globals resolved lazily at call time). */
export const webPlay = createWebPlayAnalytics()
