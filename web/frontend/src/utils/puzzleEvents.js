// Fire-and-forget puzzle telemetry: batches events and POSTs them to /api/puzzle-events.
// Contract: { installId, platform:'web', appVersion, events:[{ type, puzzleType, difficulty, sessionId,
// durationMs, score, moves, hintsUsed, mistakes, errorMessage, ts }] }. No PII; never throws; ignores 404/5xx.

const API_BASE_URL = import.meta.env?.DEV ? '' : 'https://puzzleverseai.com'
const ENDPOINT = `${API_BASE_URL}/api/puzzle-events`
const APP_VERSION = '2.0.0'
const INSTALL_KEY = 'rv_install_id'
const FLUSH_MS = 15000
const MAX_QUEUE = 200
const MAX_BATCH = 50

const FIELDS = ['difficulty', 'sessionId', 'durationMs', 'score', 'moves', 'hintsUsed', 'mistakes', 'errorMessage']

let queue = []
let hooked = false
let memoryId = null

function uuid() {
  try {
    if (typeof crypto !== 'undefined' && crypto.randomUUID) return crypto.randomUUID()
  } catch { /* fall through */ }
  return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, (c) => {
    const r = (Math.random() * 16) | 0
    return (c === 'x' ? r : (r & 0x3) | 0x8).toString(16)
  })
}

function installId() {
  try {
    let id = localStorage.getItem(INSTALL_KEY)
    if (!id) {
      id = uuid()
      localStorage.setItem(INSTALL_KEY, id)
    }
    return id
  } catch {
    return (memoryId ||= uuid())
  }
}

function post(events) {
  try {
    const body = JSON.stringify({ installId: installId(), platform: 'web', appVersion: APP_VERSION, events })
    fetch(ENDPOINT, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body,
      keepalive: true,
    }).then(() => {}, () => {}) // 404 / 5xx / network errors are deliberately ignored
  } catch { /* never throw */ }
}

export function flushPuzzleEvents() {
  try {
    while (queue.length) post(queue.splice(0, MAX_BATCH))
  } catch { /* never throw */ }
}

function hook() {
  if (hooked || typeof window === 'undefined') return
  hooked = true
  try {
    window.addEventListener('pagehide', flushPuzzleEvents)
    document.addEventListener('visibilitychange', () => {
      if (document.visibilityState === 'hidden') flushPuzzleEvents()
    })
    setInterval(flushPuzzleEvents, FLUSH_MS)
  } catch { /* ignore */ }
}

/**
 * Queue a puzzle event. type: 'start'|'complete'|'fail'|'abandon'|'hint'|'undo'|'error'.
 * fields may contain: difficulty, sessionId, durationMs, score, moves, hintsUsed, mistakes, errorMessage.
 */
export function trackPuzzleEvent(type, puzzleType, fields = {}) {
  try {
    const ev = { type, puzzleType, ts: Date.now() }
    for (const k of FIELDS) {
      const v = fields?.[k]
      if (v !== undefined && v !== null) ev[k] = k === 'errorMessage' ? String(v).slice(0, 300) : v
    }
    queue.push(ev)
    if (queue.length > MAX_QUEUE) queue = queue.slice(-MAX_QUEUE)
    hook()
  } catch { /* never throw */ }
}
