// Fire-and-forget puzzle telemetry. Contract: POST /api/puzzle-events
// { installId, platform:'web', appVersion, events:[{type, puzzleType, difficulty, sessionId,
//   durationMs, score, moves, hintsUsed, mistakes, errorMessage, ts}] }
// Batched, flushed every ~15 s and on pagehide. Never throws; ignores all HTTP failures. No PII.

const API_BASE_URL = import.meta.env.DEV ? '' : 'https://puzzleverseai.com'
const ENDPOINT = `${API_BASE_URL}/api/puzzle-events`
const APP_VERSION = import.meta.env.VITE_APP_VERSION || '2.0.0'
const FLUSH_MS = 15000
const MAX_QUEUE = 200
const MAX_BATCH = 50

let queue = []
let timer = null
let installed = false

function uuid() {
  try {
    if (globalThis.crypto?.randomUUID) return globalThis.crypto.randomUUID()
  } catch { /* fall through */ }
  return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, (c) => {
    const r = (Math.random() * 16) | 0
    return (c === 'x' ? r : (r & 0x3) | 0x8).toString(16)
  })
}

export function getInstallId() {
  try {
    let id = localStorage.getItem('rv_install_id')
    if (!id) {
      id = uuid()
      localStorage.setItem('rv_install_id', id)
    }
    return id
  } catch {
    return 'unknown'
  }
}

export function newSessionId() {
  return uuid()
}

export function flushPuzzleEvents() {
  try {
    if (!queue.length) return
    const batch = queue.slice(0, MAX_BATCH)
    queue = queue.slice(MAX_BATCH)
    const body = JSON.stringify({ installId: getInstallId(), platform: 'web', appVersion: APP_VERSION, events: batch })
    let sent = false
    try {
      if (typeof navigator !== 'undefined' && navigator.sendBeacon) {
        sent = navigator.sendBeacon(ENDPOINT, new Blob([body], { type: 'application/json' }))
      }
    } catch { /* fall back to fetch */ }
    if (!sent && typeof fetch === 'function') {
      fetch(ENDPOINT, { method: 'POST', headers: { 'Content-Type': 'application/json' }, body, keepalive: true })
        .catch(() => {})
    }
    if (queue.length) flushPuzzleEvents()
  } catch { /* never throw */ }
}

function install() {
  if (installed || typeof window === 'undefined') return
  installed = true
  timer = setInterval(flushPuzzleEvents, FLUSH_MS)
  window.addEventListener('pagehide', flushPuzzleEvents)
  document.addEventListener('visibilitychange', () => {
    if (document.visibilityState === 'hidden') flushPuzzleEvents()
  })
}

/**
 * @param {'start'|'complete'|'fail'|'abandon'|'hint'|'undo'|'error'} type
 * @param {string} puzzleType
 * @param {object} [fields] difficulty, sessionId, durationMs, score, moves, hintsUsed, mistakes, errorMessage
 */
export function trackPuzzleEvent(type, puzzleType, fields = {}) {
  try {
    install()
    const f = fields || {}
    queue.push({
      type,
      puzzleType,
      difficulty: f.difficulty == null ? undefined : String(f.difficulty),
      sessionId: f.sessionId,
      durationMs: f.durationMs,
      score: f.score,
      moves: f.moves,
      hintsUsed: f.hintsUsed,
      mistakes: f.mistakes,
      errorMessage: f.errorMessage ? String(f.errorMessage).slice(0, 300) : undefined,
      ts: Date.now(),
    })
    if (queue.length > MAX_QUEUE) queue = queue.slice(-MAX_QUEUE)
  } catch { /* never throw */ }
}

