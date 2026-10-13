// --- web spatial puzzles (feat/web-puzzles-spatial) ---
// Shared chrome, telemetry hook, error boundary and glyph palette for the five spatial games.
import { Component, useCallback, useEffect, useRef, useState } from 'react'
import { Lightbulb, Undo2, RotateCcw, SkipForward, Star, HelpCircle } from 'lucide-react'
import { trackPuzzleEvent, newSessionId, flushPuzzleEvents } from '../../../utils/puzzleEvents'
import { SPATIAL_NAMES, SPATIAL_RULES, SPATIAL_TIPS } from './engine/common.js'

/** Distinct hues. Every colour also has a glyph so the game never relies on colour alone. */
export const PALETTE = [
  ['red', '#E53935'], ['blue', '#1E88E5'], ['green', '#43A047'], ['yellow', '#FDD835'],
  ['purple', '#8E24AA'], ['orange', '#FB8C00'], ['teal', '#00ACC1'], ['pink', '#EC407A'],
  ['brown', '#6D4C41'], ['lime', '#7CB342'], ['indigo', '#5C6BC0'], ['grey', '#90A4AE'],
]
export const colorName = (i) => PALETTE[i % PALETTE.length][0]
export const colorHex = (i) => PALETTE[i % PALETTE.length][1]

// Shapes in a 0..10 box, one per palette entry.
const GLYPHS = [
  'M5 1.2 L9 9 L1 9 Z', // triangle
  'M1.5 1.5 H8.5 V8.5 H1.5 Z', // square
  'M5 0.8 L9.2 5 L5 9.2 L0.8 5 Z', // diamond
  'M5 0.8 L6.3 3.7 L9.4 4 L7.1 6.1 L7.8 9.2 L5 7.6 L2.2 9.2 L2.9 6.1 L0.6 4 L3.7 3.7 Z', // star
  'M3.7 1 H6.3 V3.7 H9 V6.3 H6.3 V9 H3.7 V6.3 H1 V3.7 H3.7 Z', // plus
  'M5 9 L1.2 5.2 C-0.2 3.2 2.3 0.8 5 3.2 C7.7 0.8 10.2 3.2 8.8 5.2 Z', // heart
  'M2.8 1.2 H7.2 L9.4 5 L7.2 8.8 H2.8 L0.6 5 Z', // hexagon
  'M1 1 L9 9 M9 1 L1 9', // cross (stroke only)
  'M1 9 L5 1 L9 9 Z M3 6 H7', // tent
  'M5 1 A4 4 0 1 0 5 9 A4 4 0 1 0 5 1 Z M5 3 A2 2 0 1 1 5 7 A2 2 0 1 1 5 3 Z', // ring
  'M1 2 H9 M1 5 H9 M1 8 H9', // bars
  'M5 1 A4 4 0 1 0 5 9 A4 4 0 1 0 5 1 Z', // disc
]

/** SVG glyph centred at (cx, cy) with size `s` (user units). */
export function Glyph({ index, cx, cy, s, opacity = 0.9 }) {
  const i = index % GLYPHS.length
  const stroky = i === 7 || i === 10
  return (
    <path
      d={GLYPHS[i]}
      transform={`translate(${cx - s / 2} ${cy - s / 2}) scale(${s / 10})`}
      fill={stroky ? 'none' : '#fff'}
      fillRule="evenodd"
      stroke={stroky ? '#fff' : 'rgba(0,0,0,0.55)'}
      strokeWidth={stroky ? 1.6 : 0.5}
      strokeLinecap="round"
      opacity={opacity}
      pointerEvents="none"
    />
  )
}

/** Logs a render error as a telemetry 'error' event and shows a recoverable message. */
export class SpatialErrorBoundary extends Component {
  constructor(props) {
    super(props)
    this.state = { error: null }
  }
  static getDerivedStateFromError(error) {
    return { error }
  }
  componentDidCatch(error) {
    trackPuzzleEvent('error', this.props.puzzleType, { errorMessage: error?.message || String(error) })
    flushPuzzleEvents()
  }
  render() {
    if (!this.state.error) return this.props.children
    return (
      <div role="alert" className="text-center py-8 space-y-3">
        <p className="text-red-300">Something went wrong with this puzzle.</p>
        <button
          className="min-h-[48px] px-5 rounded-xl bg-white/20 hover:bg-white/30 text-white"
          onClick={() => this.props.onFail?.()}
        >
          Skip puzzle
        </button>
      </div>
    )
  }
}

/**
 * Telemetry for one play session: start on mount, abandon on unmount without finishing,
 * complete / fail on finish, hint / undo on use. restart() begins a fresh session.
 */
export function useSpatialSession(puzzleType, difficulty) {
  const ref = useRef({ id: null, t0: 0, finished: false, hints: 0, moves: 0 })
  const begin = useCallback(() => {
    const s = ref.current
    s.id = newSessionId()
    s.t0 = Date.now()
    s.finished = false
    s.hints = 0
    trackPuzzleEvent('start', puzzleType, { difficulty, sessionId: s.id })
  }, [puzzleType, difficulty])

  useEffect(() => {
    begin()
    return () => {
      const s = ref.current
      if (!s.finished) {
        trackPuzzleEvent('abandon', puzzleType, {
          difficulty, sessionId: s.id, durationMs: Date.now() - s.t0, moves: s.moves, hintsUsed: s.hints,
        })
      }
    }
  }, [begin, puzzleType, difficulty])

  const finish = (type) => (extra = {}) => {
    const s = ref.current
    if (s.finished) return
    s.finished = true
    trackPuzzleEvent(type, puzzleType, { difficulty, sessionId: s.id, durationMs: Date.now() - s.t0, ...extra })
  }
  return {
    complete: finish('complete'),
    fail: finish('fail'),
    hint: () => {
      ref.current.hints++
      trackPuzzleEvent('hint', puzzleType, { difficulty, sessionId: ref.current.id, hintsUsed: ref.current.hints })
    },
    undo: () => trackPuzzleEvent('undo', puzzleType, { difficulty, sessionId: ref.current.id }),
    trackMoves: (n) => { ref.current.moves = n },
    restart: begin,
  }
}

/** Runs a (possibly heavy) generator after first paint so the UI can show "Preparing puzzle". */
export function useGenerated(generate, deps) {
  const [value, setValue] = useState(null)
  useEffect(() => {
    let live = true
    setValue(null)
    const id = setTimeout(() => {
      try {
        const v = generate()
        if (live) setValue({ v })
      } catch (e) {
        if (live) setValue({ error: e })
      }
    }, 20)
    return () => {
      live = false
      clearTimeout(id)
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, deps)
  if (value?.error) throw value.error
  return value ? value.v : null
}

export function Loading({ label = 'Preparing puzzle' }) {
  return (
    <div className="py-16 text-center text-white/70" role="status">
      <div className="mx-auto mb-3 h-8 w-8 rounded-full border-2 border-white/30 border-t-white animate-spin" />
      {label}
    </div>
  )
}

export function ActionButton({ icon: Icon, label, onClick, disabled, selected, className = '' }) {
  return (
    <button
      type="button"
      onClick={onClick}
      disabled={disabled}
      aria-pressed={selected === undefined ? undefined : selected}
      className={`flex flex-col items-center justify-center gap-0.5 min-h-[52px] min-w-[56px] px-2 rounded-xl text-xs transition-colors
        ${disabled ? 'text-white/30' : selected ? 'bg-white/30 text-white ring-2 ring-white/70' : 'text-white hover:bg-white/15 active:bg-white/25'} ${className}`}
    >
      {Icon && <Icon size={20} aria-hidden="true" />}
      <span>{label}</span>
    </button>
  )
}

export const StdIcons = { Lightbulb, Undo2, RotateCcw, SkipForward }

/** Title row, status line, game area, action bar, rules disclosure. */
export function SpatialFrame({ type, difficulty, status, message, actions, children, extra }) {
  const [showRules, setShowRules] = useState(false)
  return (
    <div className="space-y-3 select-none">
      <div className="flex items-center justify-between gap-2">
        <div>
          <h2 className="text-lg font-bold text-white leading-tight">{SPATIAL_NAMES[type]}</h2>
          <p className="text-xs text-white/60 capitalize">{difficulty}</p>
        </div>
        <p className="text-sm font-semibold text-white" aria-live="polite">{status}</p>
        <button
          type="button"
          aria-label="How to play"
          aria-expanded={showRules}
          onClick={() => setShowRules((v) => !v)}
          className="min-h-[44px] min-w-[44px] flex items-center justify-center rounded-full text-white/80 hover:bg-white/15"
        >
          <HelpCircle size={22} />
        </button>
      </div>
      {showRules && (
        <div className="rounded-xl bg-black/25 p-3 text-sm text-white/85 space-y-1">
          <p>{SPATIAL_RULES[type]}</p>
          <p className="text-white/60">Tip: {SPATIAL_TIPS[type]}</p>
        </div>
      )}
      {extra}
      <p className="min-h-[1.25rem] text-xs text-white/70 text-center" role="status" aria-live="polite">{message || ''}</p>
      {children}
      <div className="flex flex-wrap items-center justify-center gap-1">
        {actions.map((a) => (
          <ActionButton key={a.label} {...a} />
        ))}
      </div>
    </div>
  )
}

export function ResultPanel({ success, title, detail, stars = 0, primaryLabel, onPrimary, secondaryLabel, onSecondary }) {
  const ref = useRef(null)
  useEffect(() => { ref.current?.focus() }, [])
  return (
    <div
      ref={ref}
      tabIndex={-1}
      role="dialog"
      aria-label={title}
      className={`rounded-2xl p-5 text-center space-y-3 border ${success ? 'bg-green-500/15 border-green-400/40' : 'bg-red-500/15 border-red-400/40'}`}
    >
      {success && stars > 0 && (
        <div className="flex justify-center gap-1" role="img" aria-label={`${stars} of 3 stars`}>
          {[1, 2, 3].map((i) => (
            <Star key={i} size={30} className={i <= stars ? 'text-yellow-400 fill-yellow-400' : 'text-white/30'} />
          ))}
        </div>
      )}
      <h3 className="text-xl font-bold text-white">{title}</h3>
      <p className="text-white/80 text-sm">{detail}</p>
      <div className="flex flex-col sm:flex-row gap-2 justify-center">
        {secondaryLabel && (
          <button type="button" onClick={onSecondary} className="min-h-[48px] px-5 rounded-xl bg-white/15 hover:bg-white/25 text-white">
            {secondaryLabel}
          </button>
        )}
        <button type="button" onClick={onPrimary} className="min-h-[48px] px-6 rounded-xl bg-purple-500 hover:bg-purple-600 text-white font-semibold">
          {primaryLabel}
        </button>
      </div>
    </div>
  )
}
