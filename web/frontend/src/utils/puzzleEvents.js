// Puzzle telemetry client. Fire-and-forget batching to POST /api/puzzle-events.
// Contract: body {installId, platform:'web', appVersion, events:[{type, puzzleType, difficulty, sessionId,
// durationMs, score, moves, hintsUsed, mistakes, errorMessage, ts}]}. No PII. Never throws.
const API_BASE = import.meta.env?.DEV ? '' : 'https://puzzleverseai.com'
const URL_PATH = '/api/puzzle-events'
const FLUSH_MS = 15000
const MAX_QUEUE = 100
const APP_VERSION = import.meta.env?.VITE_APP_VERSION || 'web-2.0.0'
const ID_KEY = 'rv_install_id'

let queue = []
let timer = null
let hooked = false

function uuid() {
  try {
    if (crypto?.randomUUID) return crypto.randomUUID()
  } catch { /* ignore */ }
  return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, (c) => {
    const r = (Math.random() * 16) | 0
    return (c === 'x' ? r : (r & 0x3) | 0x8).toString(16)
  })
}

function installId() {
  try {
    let id = localStorage.getItem(ID_KEY)
    if (!id) {
      id = uuid()
      localStorage.setItem(ID_KEY, id)
    }
    return id
  } catch {
    return 'anonymous'
  }
}

export function newSessionId() {
  return uuid()
}

export function flushPuzzleEvents() {
  try {
    if (!queue.length) return
    const events = queue
    queue = []
    const body = JSON.stringify({ installId: installId(), platform: 'web', appVersion: APP_VERSION, events })
    const url = `${API_BASE}${URL_PATH}`
    if (typeof navigator !== 'undefined' && navigator.sendBeacon) {
      const ok = navigator.sendBeacon(url, new Blob([body], { type: 'application/json' }))
      if (ok) return
    }
    fetch(url, { method: 'POST', headers: { 'Content-Type': 'application/json' }, body, keepalive: true }).catch(() => {})
  } catch { /* never throw */ }
}

function ensureHooks() {
  if (hooked || typeof window === 'undefined') return
  hooked = true
  timer = setInterval(flushPuzzleEvents, FLUSH_MS)
  window.addEventListener('pagehide', flushPuzzleEvents)
  document.addEventListener('visibilitychange', () => {
    if (document.visibilityState === 'hidden') flushPuzzleEvents()
  })
}

/** type: 'start'|'complete'|'fail'|'abandon'|'hint'|'undo'|'error'. fields: difficulty, sessionId, durationMs, score, moves, hintsUsed, mistakes, errorMessage. */
export function trackPuzzleEvent(type, puzzleType, fields = {}) {
  try {
    ensureHooks()
    const { difficulty, sessionId, durationMs, score, moves, hintsUsed, mistakes, errorMessage } = fields || {}
    queue.push({
      type, puzzleType, difficulty, sessionId, durationMs, score, moves, hintsUsed, mistakes,
      errorMessage: errorMessage ? String(errorMessage).slice(0, 300) : undefined,
      ts: Date.now(),
    })
    if (queue.length >= MAX_QUEUE) flushPuzzleEvents()
  } catch { /* never throw */ }
}
