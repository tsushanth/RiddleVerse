import { useEffect, useMemo, useReducer, useRef, useState, useCallback } from 'react'
import { Undo2, Eraser, Pencil, Lightbulb, Heart, HelpCircle, Timer, RefreshCw, Flag } from 'lucide-react'
import GridBoard from './GridBoard'
import GridErrorBoundary from './GridErrorBoundary'
import { GRID_GAMES, buildSpec, normalizeDifficulty, randomSeed, rulesFor, seedFromPuzzle, symbolText } from './engine/factory.js'
import { GridPlayState, STATUS, MAX_LIVES } from './engine/playState.js'
import { trackPuzzleEvent, newSessionId } from '../../../utils/puzzleEvents'

const TIERS = ['easy', 'medium', 'hard']
const SEEN_KEY = 'rv_grid_rules_seen'

function clock(sec) {
  return `${String(Math.floor(sec / 60)).padStart(2, '0')}:${String(sec % 60).padStart(2, '0')}`
}

function rulesSeen(game) {
  try {
    return (JSON.parse(localStorage.getItem(SEEN_KEY) || '{}') || {})[game] === true
  } catch {
    return true
  }
}

function markRulesSeen(game) {
  try {
    const o = JSON.parse(localStorage.getItem(SEEN_KEY) || '{}') || {}
    o[game] = true
    localStorage.setItem(SEEN_KEY, JSON.stringify(o))
  } catch { /* ignore */ }
}

function ControlButton({ label, icon: Icon, onClick, disabled, active, badge }) {
  return (
    <button
      type="button"
      onClick={onClick}
      disabled={disabled}
      aria-pressed={active === undefined ? undefined : active}
      aria-label={label}
      className={`flex-1 min-h-[48px] flex flex-col items-center justify-center gap-0.5 rounded-xl text-xs font-semibold transition-colors focus:outline-none focus-visible:ring-4 focus-visible:ring-white ${
        disabled ? 'bg-white/5 text-white/30 cursor-not-allowed' : active ? 'bg-yellow-400 text-gray-900' : 'bg-white/15 hover:bg-white/25 text-white'
      }`}
    >
      <Icon size={18} aria-hidden="true" />
      <span>{badge || label}</span>
    </button>
  )
}

/** One running game for a built spec. Owns the mutable play state. */
function GridPlay({ spec, onAnswer, onNewPuzzle }) {
  const stateRef = useRef(null)
  if (stateRef.current === null) stateRef.current = new GridPlayState(spec)
  const st = stateRef.current
  const [rev, bump] = useReducer((x) => x + 1, 0)
  const [showRules, setShowRules] = useState(() => !rulesSeen(spec.game))
  const [revealed, setRevealed] = useState(false)
  const cellRefs = useRef([])
  const sessionId = useRef(newSessionId())
  const startedAt = useRef(Date.now())
  const finished = useRef(false)
  const started = useRef(false)
  const mounted = useRef(false)
  const answered = useRef(false)
  const meta = GRID_GAMES[spec.game]

  const ev = useCallback(
    (type, extra = {}) =>
      trackPuzzleEvent(type, spec.game, {
        difficulty: spec.difficulty,
        sessionId: sessionId.current,
        durationMs: Date.now() - startedAt.current,
        moves: st.moves,
        hintsUsed: st.hintsUsed,
        mistakes: st.mistakes,
        ...extra,
      }),
    [spec, st]
  )

  // start on mount, abandon on unmount without finishing (deferred so StrictMode remounts do not emit noise)
  useEffect(() => {
    mounted.current = true
    if (!started.current) {
      started.current = true
      ev('start')
    }
    return () => {
      mounted.current = false
      setTimeout(() => {
        if (!mounted.current && !finished.current && started.current) ev('abandon')
      }, 0)
    }
  }, [ev])

  // timer
  useEffect(() => {
    const id = setInterval(() => {
      if (st.status === STATUS.PLAYING && !document.hidden) {
        st.tick()
        bump()
      }
    }, 1000)
    return () => clearInterval(id)
  }, [st])

  const afterAction = useCallback(() => {
    if (st.status !== STATUS.PLAYING && !finished.current) {
      finished.current = true
      if (st.status === STATUS.WON) ev('complete', { score: st.score() })
      else {
        ev('fail')
        st.revealAll()
        setRevealed(true)
      }
    }
    bump()
  }, [st, ev])

  const act = useCallback(
    (fn) => {
      fn()
      afterAction()
    },
    [afterAction]
  )

  const onCell = (i) => act(() => st.select(i))
  const enter = (d) => act(() => st.enter(d))
  const erase = () => act(() => st.erase())
  const undo = () => act(() => { if (st.undo()) ev('undo') })
  const hint = () => act(() => { if (st.hint()) ev('hint') })
  const toggleNotes = () => act(() => st.toggleNotesMode())

  const giveUp = () => {
    if (finished.current) return
    finished.current = true
    ev('fail')
    st.revealAll()
    setRevealed(true)
    bump()
    setTimeout(() => {
      if (!answered.current) {
        answered.current = true
        onAnswer(false, '', true)
      }
    }, 1800)
  }

  const cont = () => {
    if (answered.current) return
    answered.current = true
    if (st.status === STATUS.WON) onAnswer(true, `${st.score()} pts in ${clock(st.elapsedSeconds)}`)
    else onAnswer(false, '')
  }

  const onKeyDown = (e) => {
    const t = e.target
    if (t && (t.tagName === 'INPUT' || t.tagName === 'TEXTAREA' || t.tagName === 'SELECT' || t.isContentEditable)) return
    if (e.ctrlKey || e.metaKey || e.altKey) {
      if ((e.ctrlKey || e.metaKey) && e.key.toLowerCase() === 'z') {
        e.preventDefault()
        undo()
      }
      return
    }
    const k = e.key
    const n = spec.n
    const moves = { ArrowUp: [-1, 0], ArrowDown: [1, 0], ArrowLeft: [0, -1], ArrowRight: [0, 1] }
    if (moves[k]) {
      e.preventDefault()
      act(() => st.moveSelection(moves[k][0], moves[k][1]))
      const target = cellRefs.current[st.selected]
      if (target) target.focus()
      return
    }
    if (k === 'Backspace' || k === 'Delete') {
      e.preventDefault()
      erase()
      return
    }
    if (k === 'n' || k === 'N') return toggleNotes()
    if (k === 'h' || k === 'H') return hint()
    if (k === 'u' || k === 'U') return undo()
    if (spec.game === 'binarygrid') {
      if (k === '0' || k === '1') {
        e.preventDefault()
        enter(Number(k) + 1)
      }
      return
    }
    if (/^[1-9]$/.test(k) && Number(k) <= n) {
      e.preventDefault()
      enter(Number(k))
    } else if (k === '0') {
      erase()
    }
  }

  // Document-level keys so input works even when a clicked button did not take focus (Safari/Firefox).
  const keyRef = useRef(onKeyDown)
  keyRef.current = onKeyDown
  useEffect(() => {
    const h = (e) => keyRef.current(e)
    document.addEventListener('keydown', h)
    return () => document.removeEventListener('keydown', h)
  }, [])

  const playing = st.status === STATUS.PLAYING
  const symbols = Array.from({ length: spec.symbols }, (_, k) => k + 1)
  const remaining = (d) => (spec.game === 'binarygrid' ? spec.n / 2 : spec.n) - st.filledCount(d)
  const padCols = spec.game === 'binarygrid' ? 2 : spec.symbols > 6 ? 5 : spec.symbols
  const sel = st.selected
  const canErase = playing && sel >= 0 && !st.isGiven(sel)

  return (
    <div className="space-y-3 w-full" data-testid="grid-play">
      {/* Status bar */}
      <div className="flex items-center justify-between gap-2 text-white">
        <div className="min-w-0">
          <p className="font-bold text-sm truncate">
            <span aria-hidden="true">{meta.emoji} </span>
            {meta.title}
          </p>
          <p className="text-white/60 text-xs">
            {spec.difficulty[0].toUpperCase() + spec.difficulty.slice(1)} | {spec.n}x{spec.n}
          </p>
        </div>
        <div className="flex items-center gap-1 font-mono text-sm" role="timer" aria-label={`Elapsed time ${Math.floor(st.elapsedSeconds / 60)} minutes ${st.elapsedSeconds % 60} seconds`}>
          <Timer size={16} aria-hidden="true" />
          {clock(st.elapsedSeconds)}
        </div>
        <div className="flex items-center gap-0.5" role="img" aria-label={`${st.lives} of ${MAX_LIVES} lives left`}>
          {Array.from({ length: MAX_LIVES }, (_, k) => (
            <Heart key={k} size={20} aria-hidden="true" className={k < st.lives ? 'text-red-400 fill-red-400' : 'text-white/30'} />
          ))}
        </div>
        <button
          type="button"
          onClick={() => {
            if (showRules) markRulesSeen(spec.game)
            setShowRules((s) => !s)
          }}
          aria-expanded={showRules}
          aria-label="How to play"
          className="min-w-[44px] min-h-[44px] flex items-center justify-center rounded-xl text-white/80 hover:bg-white/10 focus:outline-none focus-visible:ring-4 focus-visible:ring-white"
        >
          <HelpCircle size={22} aria-hidden="true" />
        </button>
      </div>

      {showRules && (
        <div className="rounded-xl bg-black/25 border border-white/15 p-3 text-sm text-white/90" role="region" aria-label={`How to play ${meta.title}`}>
          <p className="font-semibold mb-1">How to play {meta.title}</p>
          <p>{rulesFor(spec.game)}</p>
          <p className="text-white/60 text-xs mt-2">
            Three wrong digits end the puzzle. {spec.game === 'binarygrid' ? 'Keys: 0 and 1' : 'Keys: 1-' + Math.min(9, spec.n)}, arrows to move, Backspace to erase
            {spec.game === 'binarygrid' ? '' : ', N for notes'}, H for a hint, U to undo.
          </p>
          <button
            type="button"
            onClick={() => {
              markRulesSeen(spec.game)
              setShowRules(false)
            }}
            className="mt-2 min-h-[44px] px-4 rounded-lg bg-white/20 hover:bg-white/30 text-white font-semibold focus:outline-none focus-visible:ring-4 focus-visible:ring-white"
          >
            Got it
          </button>
        </div>
      )}

      <GridBoard spec={spec} state={st} rev={rev} onCell={onCell} cellRefs={cellRefs} revealed={revealed} />

      {/* Result banner */}
      {!playing && (
        <div role="status" aria-live="polite" className={`rounded-xl p-3 text-center text-white ${st.status === STATUS.WON ? 'bg-green-500/25 border border-green-400/40' : 'bg-red-500/25 border border-red-400/40'}`}>
          <p className="font-bold">{st.status === STATUS.WON ? 'Solved!' : 'Out of lives'}</p>
          <p className="text-sm text-white/80">
            {st.status === STATUS.WON
              ? `Time ${clock(st.elapsedSeconds)} | Mistakes ${st.mistakes} | Hints ${st.hintsUsed} | +${st.score()} points`
              : 'Three mistakes ends the puzzle. The solution is shown above.'}
          </p>
          <button type="button" onClick={cont} className="mt-2 min-h-[44px] px-6 rounded-xl bg-purple-500 hover:bg-purple-600 text-white font-semibold focus:outline-none focus-visible:ring-4 focus-visible:ring-white">
            Continue
          </button>
        </div>
      )}

      {/* Number pad */}
      <div className="grid gap-1.5" style={{ gridTemplateColumns: `repeat(${padCols}, minmax(0, 1fr))` }} role="group" aria-label="Digit pad">
        {symbols.map((d) => {
          const left = remaining(d)
          const noteMode = st.notesMode && st.usesNotes
          return (
            <button
              key={d}
              type="button"
              disabled={!playing || sel < 0 || st.isGiven(sel)}
              onClick={() => enter(d)}
              aria-label={(noteMode ? 'Note ' : 'Enter ') + symbolText(spec, d)}
              className={`min-h-[48px] rounded-xl font-bold text-xl focus:outline-none focus-visible:ring-4 focus-visible:ring-white ${
                !playing || sel < 0 || st.isGiven(sel) ? 'bg-white/5 text-white/30' : noteMode ? 'bg-yellow-400/80 text-gray-900' : 'bg-white/20 hover:bg-white/30 text-white'
              }`}
            >
              {symbolText(spec, d)}
              {spec.game !== 'binarygrid' && <span className="block text-[10px] font-normal opacity-60 leading-none">{Math.max(0, left)} left</span>}
            </button>
          )
        })}
      </div>

      {/* Controls */}
      <div className="flex gap-1.5">
        <ControlButton label="Undo" icon={Undo2} disabled={!playing || st.undoDepth === 0} onClick={undo} />
        <ControlButton label="Erase" icon={Eraser} disabled={!canErase} onClick={erase} />
        {st.usesNotes && <ControlButton label="Notes" badge={st.notesMode ? 'Notes on' : 'Notes'} icon={Pencil} disabled={!playing} active={st.notesMode} onClick={toggleNotes} />}
        <ControlButton label={`Hint, ${st.hintsLeft} left`} badge={`Hint ${st.hintsLeft}`} icon={Lightbulb} disabled={!playing || st.hintsLeft <= 0} onClick={hint} />
      </div>

      <div className="flex gap-2 justify-between">
        <button type="button" onClick={onNewPuzzle} className="min-h-[44px] px-3 flex items-center gap-1.5 rounded-xl text-white/70 hover:text-white hover:bg-white/10 text-sm focus:outline-none focus-visible:ring-4 focus-visible:ring-white">
          <RefreshCw size={16} aria-hidden="true" /> New puzzle
        </button>
        {playing && (
          <button type="button" onClick={giveUp} className="min-h-[44px] px-3 flex items-center gap-1.5 rounded-xl text-white/70 hover:text-white hover:bg-white/10 text-sm focus:outline-none focus-visible:ring-4 focus-visible:ring-white">
            <Flag size={16} aria-hidden="true" /> Show solution
          </button>
        )}
      </div>
    </div>
  )
}

/**
 * Shared renderer for the four grid games. Honors the PuzzleRenderer contract:
 * props {puzzle, onAnswer(isCorrect, userAnswer, showedAnswer?)}.
 * The puzzle is generated client-side from a seed (puzzle.question {game, seed} when present).
 */
export default function GridPuzzle({ game, puzzle, onAnswer }) {
  const pid = puzzle?.puzzleId
  const [override, setOverride] = useState({ id: null, tier: null })
  const [round, setRound] = useState({ id: null, n: 0 })
  const tier = override.id === pid && override.tier ? override.tier : normalizeDifficulty(puzzle?.difficulty)
  const roundN = round.id === pid ? round.n : 0
  const seed = useMemo(
    () => (roundN === 0 && override.id !== pid ? seedFromPuzzle(puzzle, game) : null) ?? randomSeed(),
    // eslint-disable-next-line react-hooks/exhaustive-deps
    [pid, game, tier, roundN]
  )
  const [built, setBuilt] = useState({ key: '', spec: null, error: null })
  const key = `${game}|${pid}|${tier}|${seed}`

  useEffect(() => {
    let cancelled = false
    // Yield a frame so "Building your puzzle" paints before the (rarely ~1s) hard generation blocks.
    const t = setTimeout(() => {
      if (cancelled) return
      try {
        setBuilt({ key, spec: buildSpec(game, tier, seed), error: null })
      } catch (e) {
        trackPuzzleEvent('error', game, { difficulty: tier, errorMessage: (e && e.message) || 'generation failed' })
        setBuilt({ key, spec: null, error: e })
      }
    }, 16)
    return () => {
      cancelled = true
      clearTimeout(t)
    }
  }, [key, game, tier, seed])

  const skip = () => onAnswer(false, '', true)
  const ready = built.key === key

  return (
    <GridErrorBoundary key={key} puzzleType={game} difficulty={tier} onSkip={skip}>
      <div className="max-w-[480px] mx-auto space-y-3">
        <div className="flex items-center justify-center gap-1.5" role="group" aria-label="Difficulty">
          {TIERS.map((t) => (
            <button
              key={t}
              type="button"
              aria-pressed={t === tier}
              onClick={() => setOverride({ id: pid, tier: t })}
              className={`min-h-[44px] px-4 rounded-full text-sm font-semibold capitalize focus:outline-none focus-visible:ring-4 focus-visible:ring-white ${
                t === tier ? 'bg-white text-purple-700' : 'bg-white/15 text-white hover:bg-white/25'
              }`}
            >
              {t}
            </button>
          ))}
        </div>
        {!ready && (
          <p role="status" aria-live="polite" className="text-white/70 text-center py-10">
            Building your puzzle...
          </p>
        )}
        {ready && built.error && (
          <div role="alert" className="text-center space-y-3 py-6">
            <p className="text-white font-semibold">Could not build this puzzle.</p>
            <button type="button" onClick={() => setRound({ id: pid, n: roundN + 1 })} className="min-h-[44px] px-5 rounded-xl bg-white/20 hover:bg-white/30 text-white font-semibold">
              Try another
            </button>
          </div>
        )}
        {ready && built.spec && <GridPlay key={key} spec={built.spec} onAnswer={onAnswer} onNewPuzzle={() => setRound({ id: pid, n: roundN + 1 })} />}
      </div>
    </GridErrorBoundary>
  )
}
