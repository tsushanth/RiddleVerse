// --- web classic puzzles (feat/web-puzzles-classic) --- shared chrome: session/telemetry hook, error boundary, frame, result dialog
import { Component, useEffect, useRef, useState, useCallback } from 'react'
import { createPortal } from 'react-dom'
import { trackPuzzleEvent } from '../../../utils/puzzleEvents'
import { CLASSIC_NAMES, TIER_LABELS, TUTORIALS, tutorialSeen, markTutorialSeen } from './common'

/** Tracks start / abandon / complete / fail for one mounted game. Returns stable event helpers. */
export function useClassicSession(puzzleType, tier) {
  const ref = useRef({ id: null, t0: 0, finished: false, hints: 0, mistakes: 0 })
  const difficulty = TIER_LABELS[tier] || 'medium'

  useEffect(() => {
    const s = ref.current
    s.id = `${puzzleType}-${Date.now().toString(36)}-${Math.random().toString(36).slice(2, 8)}`
    s.t0 = Date.now()
    s.finished = false
    s.hints = 0
    s.mistakes = 0
    // Deferred so React StrictMode's mount/unmount/mount in dev does not emit a phantom start+abandon.
    const startTimer = setTimeout(() => {
      s.started = true
      trackPuzzleEvent('start', puzzleType, { difficulty, sessionId: s.id })
    }, 0)
    return () => {
      clearTimeout(startTimer)
      if (s.started && !s.finished) {
        trackPuzzleEvent('abandon', puzzleType, {
          difficulty, sessionId: s.id, durationMs: Date.now() - s.t0, hintsUsed: s.hints, mistakes: s.mistakes,
        })
      }
      s.started = false
    }
  }, [puzzleType, difficulty])

  const base = () => ({ difficulty, sessionId: ref.current.id })
  return {
    hint: useCallback(() => { ref.current.hints++; trackPuzzleEvent('hint', puzzleType, base()) }, [puzzleType, difficulty]), // eslint-disable-line
    undo: useCallback(() => trackPuzzleEvent('undo', puzzleType, base()), [puzzleType, difficulty]), // eslint-disable-line
    mistake: useCallback(() => { ref.current.mistakes++ }, []),
    finish: useCallback((success, { score, moves } = {}) => {
      const s = ref.current
      if (s.finished) return
      s.finished = true
      trackPuzzleEvent(success ? 'complete' : 'fail', puzzleType, {
        ...base(), durationMs: Date.now() - s.t0, score, moves, hintsUsed: s.hints, mistakes: s.mistakes,
      })
    }, [puzzleType, difficulty]), // eslint-disable-line
  }
}

export class ClassicErrorBoundary extends Component {
  constructor(props) {
    super(props)
    this.state = { error: null, attempt: 0 }
  }
  static getDerivedStateFromError(error) {
    return { error }
  }
  componentDidCatch(error) {
    trackPuzzleEvent('error', this.props.puzzleType, { errorMessage: error?.message || String(error) })
  }
  render() {
    if (this.state.error) {
      return (
        <div role="alert" className="text-center space-y-3 py-6">
          <p className="text-white font-semibold">Something went wrong with this puzzle.</p>
          <button
            onClick={() => this.setState((s) => ({ error: null, attempt: s.attempt + 1 }))}
            className="min-h-[44px] px-5 rounded-xl bg-white/20 hover:bg-white/30 text-white font-semibold"
          >
            Try again
          </button>
        </div>
      )
    }
    return <div key={this.state.attempt}>{this.props.children}</div>
  }
}

export function Chip({ label, value }) {
  return (
    <div className="flex-1 min-w-0 bg-white/10 rounded-xl py-1.5 px-1 text-center" aria-label={`${label} ${value}`}>
      <div className="text-[11px] text-white/60 truncate">{label}</div>
      <div className="text-base font-bold text-white truncate">{value}</div>
    </div>
  )
}

export const btnClass =
  'min-h-[44px] px-3 rounded-xl bg-white/15 hover:bg-white/25 active:bg-white/30 text-white font-semibold ' +
  'disabled:opacity-40 disabled:cursor-not-allowed focus:outline-none focus-visible:ring-2 focus-visible:ring-white transition-colors'

/** Title, how-to-play card (auto-open the first time), stat chips, then the game. */
export function ClassicFrame({ game, tier, subtitle, stats, children }) {
  const info = TUTORIALS[game]
  const [open, setOpen] = useState(() => !tutorialSeen(game))
  useEffect(() => { if (open) markTutorialSeen(game) }, [open, game])
  return (
    <div className="space-y-3 select-none">
      <div className="flex items-baseline justify-between gap-2">
        <h2 className="text-lg font-bold text-white">{CLASSIC_NAMES[game]}</h2>
        <span className="text-sm text-white/60 capitalize">{TIER_LABELS[tier]}</span>
      </div>
      <p className="text-sm text-white/70">{subtitle}</p>
      <div className="flex gap-2">{stats.map(([l, v]) => <Chip key={l} label={l} value={v} />)}</div>
      <div>
        <button
          onClick={() => setOpen((o) => !o)}
          aria-expanded={open}
          className="text-sm text-purple-200 underline min-h-[44px] -my-2"
        >
          {open ? 'Hide how to play' : 'How to play'}
        </button>
        {open && info && (
          <div className="mt-1 rounded-xl bg-black/20 p-3 text-sm text-white/85 space-y-1">
            <p>{info.instruction}</p>
            <p className="text-white/60">Tip: {info.tip}</p>
          </div>
        )}
      </div>
      {children}
    </div>
  )
}

/** Blocking end-of-game dialog. onContinue hands the result to the puzzle flow. */
export function ResultDialog({ success, headline, lines, onContinue, extra }) {
  const btn = useRef(null)
  useEffect(() => { btn.current?.focus() }, [])
  // Portal: the .glass card uses backdrop-filter, which would otherwise confine position:fixed to the card.
  return createPortal(
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/60 p-4" role="dialog" aria-modal="true" aria-label={headline}>
      <div className="w-full max-w-sm rounded-2xl bg-gray-900 border border-white/20 p-5 space-y-3">
        <h3 className={`text-xl font-bold ${success ? 'text-green-400' : 'text-red-400'}`}>{headline}</h3>
        <div className="text-white/85 space-y-0.5">{lines.map((l) => <p key={l}>{l}</p>)}</div>
        <div className="flex gap-2">
          {extra}
          <button ref={btn} onClick={onContinue} className={`${btnClass} flex-1 !bg-purple-500 hover:!bg-purple-600`}>
            {success ? 'Continue' : 'Next puzzle'}
          </button>
        </div>
      </div>
    </div>,
    document.body,
  )
}

/** Window key handler that ignores typing targets and modifier combos. handler(e) returns true if handled. */
export function useGameKeys(handler, active = true) {
  const h = useRef(handler)
  h.current = handler
  useEffect(() => {
    if (!active) return undefined
    const onKey = (e) => {
      if (e.ctrlKey || e.metaKey || e.altKey) return
      const t = e.target
      if (t && (t.tagName === 'INPUT' || t.tagName === 'TEXTAREA' || t.tagName === 'SELECT')) return
      if (h.current(e)) e.preventDefault()
    }
    window.addEventListener('keydown', onKey)
    return () => window.removeEventListener('keydown', onKey)
  }, [active])
}

export const KEY_DIR = {
  ArrowUp: 'up', ArrowDown: 'down', ArrowLeft: 'left', ArrowRight: 'right',
  w: 'up', s: 'down', a: 'left', d: 'right', W: 'up', S: 'down', A: 'left', D: 'right',
}

export function summaryAnswer(parts) {
  return parts.filter(Boolean).join(' | ')
}
