// GameSessionPage.jsx — Host-controlled game session wrapper
// Route: /play?session_id=<uuid>
//
// Loads the session from the backend, embeds the AI game in an iframe,
// owns the lifecycle (timer, finish button, timeout), and sends events/end
// to the session API. Score is never trusted from the game alone.

import { useState, useEffect, useRef, useCallback } from 'react'
import { useSearchParams } from 'react-router-dom'

const API_BASE = import.meta.env.DEV ? '' : 'https://puzzleverseai.com'

// ─── Config ─────────────────────────────────────────────────────────────────
const MAX_SESSION_SECONDS = 300        // 5 min hard cap
const FOCUS_LOST_CLOSE_SECONDS = 30   // close if tab hidden this long

// ─── API helpers ─────────────────────────────────────────────────────────────
async function fetchSession(sessionId) {
  const r = await fetch(`${API_BASE}/api/sessions/${sessionId}`)
  if (!r.ok) throw new Error('Session not found')
  return r.json()
}

async function sendEvent(sessionId, type, value) {
  await fetch(`${API_BASE}/api/sessions/event`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ session_id: sessionId, type, value: value ?? null }),
  }).catch(() => {})  // fire-and-forget, never block UX
}

async function endSession(sessionId, score, forcedBy) {
  const body = { session_id: sessionId, forced_by: forcedBy }
  if (typeof score === 'number') body.score = score
  const r = await fetch(`${API_BASE}/api/sessions/end`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body),
  })
  return r.json()
}

// ─── Component ───────────────────────────────────────────────────────────────
export default function GameSessionPage() {
  const [searchParams] = useSearchParams()
  const sessionId = searchParams.get('session_id')

  const [session, setSession] = useState(null)
  const [gameUrl, setGameUrl] = useState(null)
  const [error, setError] = useState(null)
  const [elapsed, setElapsed] = useState(0)
  const [ended, setEnded] = useState(false)
  const [result, setResult] = useState(null)

  const reportedScoreRef = useRef(null)
  const focusLostAtRef = useRef(null)
  const timerRef = useRef(null)
  const focusTimerRef = useRef(null)
  const endedRef = useRef(false)

  // ── Load session on mount ──────────────────────────────────────────────────
  useEffect(() => {
    if (!sessionId) {
      setError('No session ID provided.')
      return
    }
    fetchSession(sessionId)
      .then(s => {
        if (s.ended_at) {
          setEnded(true)
          setResult({ is_billable: s.is_billable, score: s.score, duration_seconds: s.duration_seconds })
          return
        }
        setSession(s)
        setGameUrl(`${API_BASE}/api/game-creation/${s.game_id}?platform=session`)
      })
      .catch(() => setError('Session not found or expired.'))
  }, [sessionId])

  // ── Elapsed timer ──────────────────────────────────────────────────────────
  useEffect(() => {
    if (!session || ended) return

    timerRef.current = setInterval(() => {
      setElapsed(prev => {
        const next = prev + 1
        if (next >= MAX_SESSION_SECONDS) {
          clearInterval(timerRef.current)
          closeSession('timeout')
        }
        return next
      })
    }, 1000)

    return () => clearInterval(timerRef.current)
  }, [session, ended])

  // ── Tab visibility / focus tracking ───────────────────────────────────────
  useEffect(() => {
    if (!session || ended) return

    function handleVisibilityChange() {
      if (document.hidden) {
        focusLostAtRef.current = Date.now()
        sendEvent(sessionId, 'visibility_lost')
        // Auto-close after FOCUS_LOST_CLOSE_SECONDS of hiding
        focusTimerRef.current = setTimeout(() => {
          closeSession('focus_lost')
        }, FOCUS_LOST_CLOSE_SECONDS * 1000)
      } else {
        clearTimeout(focusTimerRef.current)
        focusLostAtRef.current = null
      }
    }

    document.addEventListener('visibilitychange', handleVisibilityChange)
    return () => {
      document.removeEventListener('visibilitychange', handleVisibilityChange)
      clearTimeout(focusTimerRef.current)
    }
  }, [session, ended])

  // ── Listen for postMessage events from the game iframe ───────────────────
  useEffect(() => {
    if (!session) return

    function handleMessage(event) {
      const data = event.data
      if (!data || data.type !== 'GAME_EVENT') return

      if (data.event === 'score' && typeof data.value === 'number') {
        reportedScoreRef.current = data.value
        sendEvent(sessionId, 'score', data.value)
      } else if (data.event === 'interaction') {
        sendEvent(sessionId, 'interaction', data.value ?? null)
      } else if (data.event === 'end') {
        // Game self-reported end — close via user_button path so it's treated
        // as a clean finish, but credit goes to the score it reported.
        if (typeof data.finalScore === 'number') {
          reportedScoreRef.current = data.finalScore
        }
        closeSession('user_button')
      }
    }

    window.addEventListener('message', handleMessage)
    return () => window.removeEventListener('message', handleMessage)
  }, [session])

  // ── Close session (deduplicated) ──────────────────────────────────────────
  const closeSession = useCallback(async (forcedBy) => {
    if (endedRef.current) return
    endedRef.current = true

    clearInterval(timerRef.current)
    clearTimeout(focusTimerRef.current)

    try {
      const res = await endSession(sessionId, reportedScoreRef.current, forcedBy)
      setResult(res)
    } catch {
      setResult({ is_billable: false, score: null, duration_seconds: elapsed })
    }
    setEnded(true)
  }, [sessionId, elapsed])

  // ── Cleanup on unmount ────────────────────────────────────────────────────
  useEffect(() => {
    return () => {
      if (!endedRef.current) {
        endSession(sessionId, reportedScoreRef.current, 'app_background').catch(() => {})
        endedRef.current = true
      }
    }
  }, [sessionId])

  // ── Format timer display ──────────────────────────────────────────────────
  const formatTime = (s) => `${Math.floor(s / 60)}:${String(s % 60).padStart(2, '0')}`

  // ─────────────────────────────────────────────────────────────────────────
  // Render: error state
  if (error) {
    return (
      <div style={styles.center}>
        <p style={styles.errorText}>{error}</p>
      </div>
    )
  }

  // Render: session already ended / result screen
  if (ended && result) {
    return (
      <div style={styles.center}>
        <div style={styles.resultCard}>
          <div style={styles.resultIcon}>{result.is_billable ? '🏆' : '👋'}</div>
          <h2 style={styles.resultTitle}>
            {result.is_billable ? 'Game Complete!' : 'Too Short'}
          </h2>
          {result.score > 0 && (
            <p style={styles.scoreText}>Score: <strong>{result.score}</strong></p>
          )}
          <p style={styles.durationText}>
            Time: {formatTime(result.duration_seconds || 0)}
          </p>
          <button style={styles.doneButton} onClick={() => {
            // Try JS close first (works in some WebViews), then deep link for iOS WKWebView
            try { window.close(); } catch(e) {}
            setTimeout(() => { window.location.href = 'riddleverse://session-complete'; }, 100);
          }}>
            Done
          </button>
        </div>
      </div>
    )
  }

  // Render: loading
  if (!session || !gameUrl) {
    return (
      <div style={styles.center}>
        <div style={styles.spinner} />
      </div>
    )
  }

  // Render: active session with iframe + overlay
  return (
    <div style={styles.container}>
      {/* Floating overlay — always on top */}
      <div style={styles.overlay}>
        <span style={styles.timer}>{formatTime(elapsed)}</span>
        <button style={styles.finishButton} onClick={() => closeSession('user_button')}>
          ✅ Finish
        </button>
      </div>

      {/* Game iframe */}
      <iframe
        src={gameUrl}
        style={styles.frame}
        title="Game"
        allow="autoplay"
        sandbox="allow-scripts allow-same-origin allow-forms allow-pointer-lock allow-top-navigation-by-user-activation"
      />
    </div>
  )
}

// ─── Styles ──────────────────────────────────────────────────────────────────
const styles = {
  container: {
    position: 'fixed', inset: 0, background: '#000', overflow: 'hidden',
  },
  overlay: {
    position: 'absolute', top: 12, left: 0, right: 0,
    display: 'flex', justifyContent: 'space-between', alignItems: 'center',
    padding: '0 16px', zIndex: 100, pointerEvents: 'none',
  },
  timer: {
    background: 'rgba(0,0,0,0.6)', color: '#fff',
    padding: '6px 12px', borderRadius: 20, fontSize: 14, fontWeight: 600,
    fontFamily: 'monospace', pointerEvents: 'none',
  },
  finishButton: {
    background: '#22c55e', color: '#fff', border: 'none',
    padding: '8px 16px', borderRadius: 20, fontSize: 14, fontWeight: 600,
    cursor: 'pointer', pointerEvents: 'auto', boxShadow: '0 2px 8px rgba(0,0,0,0.3)',
  },
  frame: {
    width: '100%', height: '100%', border: 'none',
  },
  center: {
    display: 'flex', alignItems: 'center', justifyContent: 'center',
    height: '100vh', background: '#111',
  },
  errorText: {
    color: '#ef4444', fontFamily: 'sans-serif', fontSize: 16,
  },
  spinner: {
    width: 40, height: 40, border: '4px solid #333',
    borderTop: '4px solid #22c55e', borderRadius: '50%',
    animation: 'spin 0.8s linear infinite',
  },
  resultCard: {
    background: '#1a1a1a', borderRadius: 16, padding: 32,
    textAlign: 'center', color: '#fff', fontFamily: 'sans-serif',
    minWidth: 260, boxShadow: '0 8px 32px rgba(0,0,0,0.4)',
  },
  resultIcon: { fontSize: 48, marginBottom: 12 },
  resultTitle: { margin: '0 0 8px', fontSize: 22 },
  scoreText: { fontSize: 18, margin: '4px 0' },
  durationText: { fontSize: 14, color: '#888', margin: '4px 0 20px' },
  doneButton: {
    background: '#22c55e', color: '#fff', border: 'none',
    padding: '10px 28px', borderRadius: 20, fontSize: 15, fontWeight: 600,
    cursor: 'pointer',
  },
}
