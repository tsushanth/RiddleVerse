// Fire-and-forget puzzle telemetry. Batches events and POSTs them to /api/puzzle-events.
// Contract: {installId, platform:'web', appVersion, events:[{type, puzzleType, difficulty, sessionId,
// durationMs, score, moves, hintsUsed, mistakes, errorMessage, ts}]}. Never throws, ignores 404/5xx, no PII.

const API_BASE_URL = import.meta.env.DEV ? '' : 'https://puzzleverseai.com'
const ENDPOINT = `${API_BASE_URL}/api/puzzle-events`
const INSTALL_KEY = 'rv_install_id'
const APP_VERSION = 'web'
const FLUSH_MS = 15000
const MAX_QUEUE = 200
const MAX_BATCH = 50
const TYPES = ['start', 'complete', 'fail', 'abandon', 'hint', 'undo', 'error']

let queue = []
let timer = null
let installIdCache = null
let hooked = false

function uuid() {
  try {
    if (typeof crypto !== 'undefined' && crypto.randomUUID) return crypto.randomUUID()
  } catch { /* fall through */ }
  return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, (c) => {
    const r = (Math.random() * 16) | 0
    return (c === 'x' ? r : (r & 0x3) | 0x8).toString(16)
  })
}

export function getInstallId() {
  if (installIdCache) return installIdCache
  try {
    let id = localStorage.getItem(INSTALL_KEY)
    if (!id) {
      id = uuid()
      localStorage.setItem(INSTALL_KEY, id)
    }
    installIdCache = id
  } catch {
    installIdCache = uuid()
  }
  return installIdCache
}

export function newSessionId() {
  return uuid()
}

function send(events, useBeacon) {
  try {
    const body = JSON.stringify({ installId: getInstallId(), platform: 'web', appVersion: APP_VERSION, events })
    if (useBeacon && typeof navigator !== 'undefined' && navigator.sendBeacon) {
      const blob = new Blob([body], { type: 'application/json' })
      if (navigator.sendBeacon(ENDPOINT, blob)) return
    }
    if (typeof fetch === 'function') {
      fetch(ENDPOINT, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body,
        keepalive: true,
      }).catch(() => {})
    }
  } catch { /* telemetry must never throw */ }
}

export function flushPuzzleEvents(useBeacon = false) {
  try {
    while (queue.length) {
      const batch = queue.splice(0, MAX_BATCH)
      send(batch, useBeacon)
    }
  } catch { /* ignore */ }
}

function ensureHooks() {
  if (hooked || typeof window === 'undefined') return
  hooked = true
  try {
    timer = setInterval(() => flushPuzzleEvents(false), FLUSH_MS)
    window.addEventListener('pagehide', () => flushPuzzleEvents(true))
    document.addEventListener('visibilitychange', () => {
      if (document.visibilityState === 'hidden') flushPuzzleEvents(true)
    })
  } catch { /* ignore */ }
}

/**
 * Queue an event. `fields` may carry difficulty, sessionId, durationMs, score, moves,
 * hintsUsed, mistakes, errorMessage. Unknown keys are dropped.
 */
export function trackPuzzleEvent(type, puzzleType, fields = {}) {
  try {
    if (!TYPES.includes(type)) return
    ensureHooks()
    const f = fields || {}
    const ev = { type, puzzleType: String(puzzleType || ''), ts: Date.now() }
    for (const k of ['difficulty', 'sessionId', 'durationMs', 'score', 'moves', 'hintsUsed', 'mistakes']) {
      if (f[k] !== undefined && f[k] !== null) ev[k] = f[k]
    }
    if (f.errorMessage) ev.errorMessage = String(f.errorMessage).slice(0, 300)
    if (queue.length >= MAX_QUEUE) queue.shift()
    queue.push(ev)
  } catch { /* ignore */ }
}

// Exposed for tests / manual teardown.
export function _stopPuzzleEventTimer() {
  if (timer) clearInterval(timer)
  timer = null
}
