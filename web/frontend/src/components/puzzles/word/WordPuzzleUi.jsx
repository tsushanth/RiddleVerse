// Shared UI for the word puzzle family (feat/web-puzzles-word).
import React, { useCallback, useEffect, useRef, useState } from 'react'
import { loadWordLists } from './lib/wordLists.js'
import { createStats } from './lib/stats.js'
import { hashSeed, normalizeDifficulty } from './lib/prng.js'
import { trackPuzzleEvent, newSessionId } from '../../../utils/puzzleEvents.js'
import { reportWebFailure } from '../../../utils/failureReporter.js'

export const stats = createStats()

export const TILE_COLORS = {
  correct: 'bg-emerald-600 border-emerald-600 text-white',
  present: 'bg-amber-500 border-amber-500 text-black',
  absent: 'bg-slate-600 border-slate-600 text-white',
  none: 'bg-white/10 border-white/30 text-white',
}
export const GLYPH = { correct: '✓', present: '~', absent: '×' }
export const STATE_LABEL = { correct: 'correct spot', present: 'wrong spot', absent: 'not in word' }

export function seedFor(puzzle, type) {
  return hashSeed(`${type}:${puzzle?.puzzleId ?? 'x'}`)
}
export function difficultyOf(puzzle) {
  return normalizeDifficulty(puzzle?.difficulty)
}

/** Lazily loads the shared word lists. */
export function useWordLists() {
  const [state, setState] = useState({ lists: null, error: null })
  const [attempt, setAttempt] = useState(0)
  useEffect(() => {
    let live = true
    loadWordLists('/word_puzzles/')
      .then((lists) => live && setState({ lists, error: null }))
      .catch((e) => live && setState({ lists: null, error: e }))
    return () => { live = false }
  }, [attempt])
  return { ...state, retry: () => setAttempt((n) => n + 1) }
}

export function ListsGate({ state, children }) {
  if (state.error) {
    return (
      <div role="alert" className="text-center space-y-3 py-6">
        <p className="text-red-300">Could not load the word list.</p>
        <button onClick={state.retry} className="min-h-[44px] px-5 rounded-xl bg-white/20 text-white">Try again</button>
      </div>
    )
  }
  if (!state.lists) {
    return <p role="status" className="text-center text-white/70 py-10">Loading words...</p>
  }
  return children(state.lists)
}

/** Telemetry session: start on mount, abandon on unmount unless finished. */
export function useGameSession(puzzleType, difficulty) {
  const s = useRef(null)
  if (!s.current) s.current = { id: newSessionId(), t0: Date.now(), done: false, hints: 0, mistakes: 0, moves: 0 }
  useEffect(() => {
    const ss = s.current
    ss.done = false
    trackPuzzleEvent('start', puzzleType, { difficulty, sessionId: ss.id })
    return () => {
      if (!ss.done) {
        trackPuzzleEvent('abandon', puzzleType, {
          difficulty, sessionId: ss.id, durationMs: Date.now() - ss.t0,
          moves: ss.moves, hintsUsed: ss.hints, mistakes: ss.mistakes,
        })
      }
    }
  }, [puzzleType, difficulty])
  return {
    hint() { s.current.hints++; trackPuzzleEvent('hint', puzzleType, { difficulty, sessionId: s.current.id, hintsUsed: s.current.hints }) },
    undo() { trackPuzzleEvent('undo', puzzleType, { difficulty, sessionId: s.current.id }) },
    move() { s.current.moves++ },
    mistake() { s.current.mistakes++ },
    finish(success, score) {
      const ss = s.current
      if (ss.done) return
      ss.done = true
      trackPuzzleEvent(success ? 'complete' : 'fail', puzzleType, {
        difficulty, sessionId: ss.id, durationMs: Date.now() - ss.t0, score,
        moves: ss.moves, hintsUsed: ss.hints, mistakes: ss.mistakes,
      })
    },
  }
}

/** Error boundary: reports and offers a retry. */
export class WordPuzzleBoundary extends React.Component {
  constructor(props) { super(props); this.state = { err: null, n: 0 } }
  static getDerivedStateFromError(err) { return { err } }
  componentDidCatch(err) {
    trackPuzzleEvent('error', this.props.puzzleType, { errorMessage: err?.message })
    reportWebFailure(`word-puzzle:${this.props.puzzleType}`, err)
  }
  render() {
    if (this.state.err) {
      return (
        <div role="alert" className="text-center space-y-3 py-6">
          <p className="text-red-300">Something went wrong with this puzzle.</p>
          <button className="min-h-[44px] px-5 rounded-xl bg-white/20 text-white"
            onClick={() => this.setState({ err: null, n: this.state.n + 1 })}>Try again</button>
        </div>
      )
    }
    return <React.Fragment key={this.state.n}>{this.props.children}</React.Fragment>
  }
}

/** Wraps a game component with boundary, keyed remount per puzzle. */
export function withWordGame(Game, puzzleType) {
  return function WordGame({ puzzle, onAnswer }) {
    return (
      <WordPuzzleBoundary puzzleType={puzzleType}>
        <Game key={puzzle?.puzzleId} puzzle={puzzle} onAnswer={onAnswer} />
      </WordPuzzleBoundary>
    )
  }
}

/** Message line, announced politely to screen readers. */
export function useMessage(ms = 1800) {
  const [msg, setMsg] = useState('')
  const t = useRef(null)
  const show = useCallback((m) => {
    setMsg(m)
    clearTimeout(t.current)
    t.current = setTimeout(() => setMsg(''), ms)
  }, [ms])
  useEffect(() => () => clearTimeout(t.current), [])
  return [msg, show]
}

export function Announcer({ message }) {
  return (
    <div role="status" aria-live="polite" className="min-h-[1.5rem] text-center text-sm font-semibold text-yellow-200">
      {message}
    </div>
  )
}

export function SrOnly({ children }) {
  return <div className="sr-only" role="status" aria-live="polite">{children}</div>
}

/** Physical keyboard: handler(key) with key = single letter | 'Enter' | 'Backspace' | 'ArrowLeft' | 'ArrowRight'. */
export function usePhysicalKeys(handler, enabled = true) {
  const ref = useRef(handler)
  ref.current = handler
  useEffect(() => {
    if (!enabled) return undefined
    const onKey = (e) => {
      if (e.ctrlKey || e.metaKey || e.altKey) return
      const t = e.target
      const tag = t?.tagName
      if (tag === 'INPUT' || tag === 'TEXTAREA' || t?.isContentEditable) return
      let key = null
      if (/^[a-zA-Z]$/.test(e.key)) key = e.key.toLowerCase()
      else if (['Enter', 'Backspace', 'ArrowLeft', 'ArrowRight'].includes(e.key)) key = e.key
      if (!key) return
      // Let Enter activate a focused non-key button (Hint, Give up...).
      if (key === 'Enter' && tag === 'BUTTON' && !t.dataset.wordkey) return
      e.preventDefault()
      ref.current(key)
    }
    window.addEventListener('keydown', onKey)
    return () => window.removeEventListener('keydown', onKey)
  }, [enabled])
}

const ROWS = ['qwertyuiop', 'asdfghjkl', 'zxcvbnm']

export function Keyboard({ keyStates = {}, onLetter, onDelete, onEnter, enterLabel = 'ENTER', showActions = true }) {
  const keyCls = (c) => {
    const st = keyStates[c]
    return st ? TILE_COLORS[st] : 'bg-white/20 border-white/20 text-white hover:bg-white/30'
  }
  const label = (c) => {
    const st = keyStates[c]
    return st ? `${c.toUpperCase()}, ${STATE_LABEL[st]}` : c.toUpperCase()
  }
  return (
    <div className="w-full space-y-1.5 select-none" role="group" aria-label="Keyboard">
      {ROWS.map((row, ri) => (
        <div key={row} className="flex justify-center gap-1">
          {ri === 2 && showActions && (
            <button type="button" data-wordkey="1" onClick={onEnter} aria-label={enterLabel === 'ENTER' ? 'Enter' : enterLabel}
              className="flex-[1.6] min-w-0 h-12 rounded-md bg-white/30 border border-white/30 text-white text-xs font-bold touch-manipulation">
              {enterLabel}
            </button>
          )}
          {[...row].map((c) => (
            <button key={c} type="button" data-wordkey="1" onClick={() => onLetter(c)} aria-label={label(c)}
              className={`flex-1 min-w-0 h-12 rounded-md border text-sm font-bold touch-manipulation ${keyCls(c)}`}>
              {c.toUpperCase()}
              {keyStates[c] && <span aria-hidden="true" className="ml-0.5 text-[9px] align-top">{GLYPH[keyStates[c]]}</span>}
            </button>
          ))}
          {ri === 2 && showActions && (
            <button type="button" data-wordkey="1" onClick={onDelete} aria-label="Delete"
              className="flex-[1.6] min-w-0 h-12 rounded-md bg-white/30 border border-white/30 text-white text-base font-bold touch-manipulation">
              {'⌫'}
            </button>
          )}
        </div>
      ))}
    </div>
  )
}

export function ActionButton({ children, onClick, disabled, label, primary }) {
  return (
    <button type="button" onClick={onClick} disabled={disabled} aria-label={label}
      className={`min-h-[44px] px-4 rounded-xl text-sm font-semibold touch-manipulation transition-colors disabled:opacity-40 ${
        primary ? 'bg-green-500 hover:bg-green-600 text-white' : 'bg-white/15 hover:bg-white/25 text-white'}`}>
      {children}
    </button>
  )
}

export function ResultCard({ success, headline, lines = [], score, snapshot, shareText, onContinue, distribution, highlight }) {
  const [copied, setCopied] = useState('')
  const share = async () => {
    try {
      if (navigator.share) { await navigator.share({ text: shareText }); return }
    } catch { /* cancelled; fall through to copy */ }
    try { await navigator.clipboard.writeText(shareText); setCopied('Copied to clipboard') } catch { setCopied('Copy not available') }
  }
  const max = Math.max(1, ...(distribution || [0]))
  return (
    <div className="rounded-xl bg-black/20 border border-white/15 p-4 space-y-3 text-center w-full">
      <h2 className="text-xl font-bold text-white" role="status" aria-live="polite">{headline}</h2>
      {lines.filter(Boolean).map((l) => <p key={l} className="text-white/80 text-sm">{l}</p>)}
      {success && <p className="text-green-300 font-semibold">+{score} points</p>}
      {snapshot && (
        <dl className="grid grid-cols-4 gap-2 text-white">
          {[['Played', snapshot.played], ['Win %', snapshot.winPercent], ['Streak', snapshot.streak], ['Best', snapshot.bestStreak]].map(([k, v]) => (
            <div key={k}><dd className="text-lg font-bold">{v}</dd><dt className="text-[11px] text-white/60">{k}</dt></div>
          ))}
        </dl>
      )}
      {distribution && distribution.some((n) => n > 0) && (
        <div className="space-y-1 text-left" aria-label="Guess distribution">
          {distribution.map((n, i) => (
            <div key={i} className="flex items-center gap-2 text-xs text-white">
              <span className="w-3">{i + 1}</span>
              <div className={`h-4 rounded px-1 text-right font-bold ${highlight === i + 1 ? 'bg-emerald-600' : 'bg-white/30'}`}
                style={{ width: `${Math.max(8, (n / max) * 100)}%` }}>{n}</div>
            </div>
          ))}
        </div>
      )}
      <div className="flex gap-2 justify-center flex-wrap">
        {shareText && <ActionButton onClick={share}>Share</ActionButton>}
        <ActionButton primary onClick={onContinue}>Continue</ActionButton>
      </div>
      <p role="status" aria-live="polite" className="text-xs text-white/60 min-h-[1rem]">{copied}</p>
    </div>
  )
}
