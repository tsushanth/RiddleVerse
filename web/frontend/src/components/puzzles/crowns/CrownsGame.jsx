// web/game-crowns
// Crowns: one crown per row, column and coloured region; no two crowns touch (diagonals too).
// Accessibility: every region has a colour AND a pattern, every cell is a real button with a label that reads out row,
// column, region, pattern and state, the board is a keyboard grid (arrows, Space/Enter, Backspace), and cells are
// never smaller than 44 px (the board scrolls sideways when N x 44 is wider than the screen).
import { useCallback, useEffect, useMemo, useReducer, useRef, useState } from 'react'
import { HelpCircle } from 'lucide-react'
import {
  CrownsState, MAX_N, MIN_N, REGION_STYLES, cellLabel, cellSizePx, generateFor, moveFocus,
} from './crowns.js'
import { ActionButton, Loading, ResultPanel, StdIcons, useGenerated, useSpatialSession } from '../spatial/SpatialShell.jsx'

const INK = '#1B1B1F'
const RED = '#D32F2F'
const PATTERN_INK = 'rgba(27,27,31,0.38)'
const RULES =
  'Place exactly one crown in every row, every column and every coloured region. No two crowns may touch, not even diagonally. ' +
  'Tap a square once for an X (it cannot hold a crown), twice for a crown, a third time to clear it. ' +
  'On a keyboard use the arrow keys to move and Space to change a square; Backspace clears it.'
const TIP = 'A colour with only one square left must hold a crown. Crowns that break a rule get a red outline and a warning mark.'

export default function CrownsGame({ difficulty, seed, onFinished }) {
  const [round, setRound] = useState(0)
  const [sizeOverride, setSizeOverride] = useState(null)
  const wanted = sizeOverride ?? difficulty
  const puzzle = useGenerated(() => generateFor(wanted, seed + round * 7919), [wanted, seed, round])
  if (!puzzle) return <Loading />
  return (
    <Board
      key={`${wanted}-${seed}-${round}`}
      puzzle={puzzle}
      difficulty={difficulty}
      sizeOverride={sizeOverride}
      onSize={setSizeOverride}
      onNew={() => setRound((r) => r + 1)}
      onFinished={onFinished}
    />
  )
}

function patternEls(pattern) {
  const common = { stroke: PATTERN_INK, strokeWidth: 4.5, fill: 'none' }
  switch (pattern) {
    case 1: return [-100, -76, -52, -28, -4, 20, 44, 68, 92].map((x) => <line key={x} {...common} x1={x} y1="100" x2={x + 100} y2="0" />)
    case 2: return [[17, 17], [50, 17], [83, 17], [33, 50], [67, 50], [17, 83], [50, 83], [83, 83]].map(([x, y]) => <circle key={`${x}-${y}`} cx={x} cy={y} r="6.5" fill={PATTERN_INK} />)
    case 3: return [20, 40, 60, 80].map((y) => <line key={y} {...common} x1="0" y1={y} x2="100" y2={y} />)
    case 4: return [20, 40, 60, 80].map((x) => <line key={x} {...common} x1={x} y1="0" x2={x} y2="100" />)
    case 5: return [-100, -76, -52, -28, -4, 20, 44, 68, 92].flatMap((x) => [
      <line key={`a${x}`} {...common} x1={x} y1="100" x2={x + 100} y2="0" />,
      <line key={`b${x}`} {...common} x1={x} y1="0" x2={x + 100} y2="100" />,
    ])
    case 6: return [-100, -76, -52, -28, -4, 20, 44, 68, 92].map((x) => <line key={x} {...common} x1={x} y1="0" x2={x + 100} y2="100" />)
    case 7: return [0, 1, 2, 3].flatMap((i) => [0, 1, 2, 3].filter((j) => (i + j) % 2 === 0).map((j) => <rect key={`${i}${j}`} x={i * 25} y={j * 25} width="25" height="25" fill={PATTERN_INK} />))
    case 8: return [<circle key="a" {...common} cx="28" cy="28" r="17" />, <circle key="b" {...common} cx="72" cy="72" r="17" />]
    default: return null
  }
}

const CROWN_PATH = 'M20 74 L20 32 L36 52 L50 24 L64 52 L80 32 L80 74 Z'

function CellArt({ style, value, conflict, hinted, edges }) {
  const [top, bottom, left, right] = edges
  const edge = 'rgba(27,27,31,0.85)'
  const faint = 'rgba(27,27,31,0.22)'
  const line = (on, x1, y1, x2, y2) => (
    <line x1={x1} y1={y1} x2={x2} y2={y2} stroke={on ? edge : faint} strokeWidth={on ? 8.5 : 2} />
  )
  return (
    <svg viewBox="0 0 100 100" className="absolute inset-0 h-full w-full" aria-hidden="true" focusable="false">
      <rect width="100" height="100" fill={style.color} />
      {patternEls(style.pattern)}
      {line(top, 0, 0, 100, 0)}{line(bottom, 0, 100, 100, 100)}{line(left, 0, 0, 0, 100)}{line(right, 100, 0, 100, 100)}
      {conflict && (
        <g>
          <rect width="100" height="100" fill={RED} opacity="0.28" />
          <rect x="6" y="6" width="88" height="88" fill="none" stroke={RED} strokeWidth="7" />
          <line x1="82" y1="12" x2="82" y2="30" stroke={RED} strokeWidth="7" />
          <circle cx="82" cy="38" r="4" fill={RED} />
        </g>
      )}
      {hinted && <rect x="10" y="10" width="80" height="80" fill="none" stroke="#fff" strokeWidth="6" strokeDasharray="10 6" />}
      {value === 1 && (
        <g stroke={INK} strokeOpacity="0.65" strokeWidth="7" strokeLinecap="round">
          <line x1="30" y1="30" x2="70" y2="70" /><line x1="70" y1="30" x2="30" y2="70" />
        </g>
      )}
      {value === 2 && (
        <g className="crowns-pop" style={{ transformOrigin: '50px 50px' }}>
          <path d={CROWN_PATH} fill={conflict ? RED : INK} stroke="#fff" strokeWidth="4" strokeLinejoin="round" />
          <circle cx="50" cy="64" r="3.5" fill="#fff" />
        </g>
      )}
    </svg>
  )
}

function Board({ puzzle, difficulty, sizeOverride, onSize, onNew, onFinished }) {
  const n = puzzle.n
  const game = useMemo(() => new CrownsState(puzzle), [puzzle])
  const [, bump] = useReducer((x) => x + 1, 0)
  const tel = useSpatialSession('crowns', difficulty)
  const [done, setDone] = useState(false)
  const [message, setMessage] = useState('')
  const [hintCell, setHintCell] = useState(-1)
  const [cursor, setCursor] = useState(0)
  const [showRules, setShowRules] = useState(false)
  const [boxW, setBoxW] = useState(328)
  const wrapRef = useRef(null)
  const cellRefs = useRef([])
  const moves = useRef(0)

  useEffect(() => {
    const el = wrapRef.current
    if (!el) return undefined
    const measure = () => setBoxW(el.clientWidth || 328)
    measure()
    if (typeof ResizeObserver === 'undefined') return undefined
    const ro = new ResizeObserver(measure)
    ro.observe(el)
    return () => ro.disconnect()
  }, [])

  const cell = cellSizePx(n, boxW)
  const scrolls = n * cell > boxW + 1
  const conflicts = game.conflicts()
  const crowns = game.crownCount()
  const anyConflict = conflicts.some(Boolean)

  const afterMove = useCallback(() => {
    moves.current++
    tel.trackMoves(moves.current)
    setHintCell(-1)
    setMessage('')
    if (game.isSolved()) {
      setDone(true)
      tel.complete({ score: game.score(), moves: moves.current, hintsUsed: game.hintsUsed })
    }
    bump()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [game])

  const tapCell = (i) => {
    if (done) return
    setCursor(i)
    game.tap(i)
    afterMove()
  }

  const onKeyDown = (e) => {
    if (e.key === 'Backspace' || e.key === 'Delete') {
      if (done) return
      e.preventDefault()
      if (game.cells[cursor] !== 0) { game.setCell(cursor, 0); afterMove() }
      return
    }
    const next = moveFocus(n, cursor, e.key)
    if (next !== cursor || ['ArrowUp', 'ArrowDown', 'ArrowLeft', 'ArrowRight', 'Home', 'End'].includes(e.key)) {
      e.preventDefault()
      setCursor(next)
      cellRefs.current[next]?.focus()
    }
  }

  const doHint = () => {
    const h = game.hint()
    if (!h) return
    tel.hint()
    setHintCell(h.cell)
    const where = `row ${Math.floor(h.cell / n) + 1}, column ${(h.cell % n) + 1}`
    setMessage(h.removed ? `That crown at ${where} was wrong, so it was removed` : `A crown belongs at ${where}`)
    if (game.isSolved()) {
      setDone(true)
      tel.complete({ score: game.score(), moves: moves.current, hintsUsed: game.hintsUsed })
    }
    bump()
  }
  const doUndo = () => { if (game.undo()) { tel.undo(); setHintCell(-1); setMessage(''); bump() } }
  const doClear = () => { game.clear(); setHintCell(-1); setMessage(''); bump() }

  const status = message ||
    (anyConflict ? 'Crowns outlined in red share a row, column or colour, or touch' : 'Tap: X, then crown, then clear')

  const edgesOf = (r, c) => {
    const g = puzzle.regionOf(r, c)
    const diff = (rr, cc) => rr < 0 || cc < 0 || rr >= n || cc >= n || puzzle.regionOf(rr, cc) !== g
    return [diff(r - 1, c), diff(r + 1, c), diff(r, c - 1), diff(r, c + 1)]
  }

  return (
    <div className="space-y-3 select-none">
      <style>{`
        @media (prefers-reduced-motion: no-preference) {
          .crowns-pop { animation: crowns-pop 180ms ease-out; }
          @keyframes crowns-pop { from { transform: scale(0.3); } 60% { transform: scale(1.15); } to { transform: scale(1); } }
        }
      `}</style>
      <div className="flex items-center justify-between gap-2">
        <div>
          <h2 className="text-lg font-bold text-white leading-tight">Crowns</h2>
          <p className="text-xs text-white/60">{n} x {n} board</p>
        </div>
        <p className="text-sm font-semibold text-white" aria-live="polite">{crowns}/{n} crowns</p>
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
          <p>{RULES}</p>
          <p className="text-white/60">Tip: {TIP}</p>
        </div>
      )}

      <div className="flex flex-wrap items-center justify-center gap-1" role="group" aria-label="Board size">
        <span className="text-xs text-white/60 pr-1">Size</span>
        {Array.from({ length: MAX_N - MIN_N + 1 }, (_, k) => MIN_N + k).map((s) => (
          <button
            key={s}
            type="button"
            aria-pressed={s === n}
            aria-label={`${s} by ${s} board`}
            onClick={() => { if (s !== n) onSize(s) }}
            className={`min-h-[44px] min-w-[44px] rounded-xl text-sm font-semibold focus:outline-none focus-visible:ring-4 focus-visible:ring-white ${
              s === n ? 'bg-white text-purple-800' : 'bg-white/10 text-white hover:bg-white/20'
            }`}
          >
            {s}
          </button>
        ))}
      </div>
      {sizeOverride !== null && (
        <div className="text-center">
          <button type="button" onClick={() => onSize(null)} className="min-h-[44px] px-3 text-xs text-white/70 underline">
            Back to the difficulty setting
          </button>
        </div>
      )}

      <p className="min-h-[1.25rem] text-xs text-white/70 text-center" role="status" aria-live="polite">{status}</p>

      <div ref={wrapRef} className="w-full">
        <div className="mx-auto w-fit max-w-full overflow-x-auto rounded-md" style={{ touchAction: 'manipulation' }}>
          <div
            role="grid"
            aria-label={`Crowns board, ${n} by ${n}`}
            aria-rowcount={n}
            aria-colcount={n}
            onKeyDown={onKeyDown}
            style={{ width: n * cell }}
          >
            {Array.from({ length: n }, (_, r) => (
              <div role="row" key={r} className="flex">
                {Array.from({ length: n }, (_, c) => {
                  const i = r * n + c
                  const value = game.cells[i]
                  const style = REGION_STYLES[puzzle.regionOf(r, c) % REGION_STYLES.length]
                  return (
                    <div role="gridcell" key={c} className="relative" style={{ width: cell, height: cell }}>
                      <button
                        type="button"
                        ref={(el) => { cellRefs.current[i] = el }}
                        tabIndex={i === cursor ? 0 : -1}
                        disabled={done}
                        aria-label={cellLabel(puzzle, r, c, value, conflicts[i])}
                        onClick={() => tapCell(i)}
                        onFocus={() => setCursor(i)}
                        className="absolute inset-0 h-full w-full focus:outline-none focus-visible:ring-4 focus-visible:ring-inset focus-visible:ring-white focus-visible:z-10"
                      >
                        <CellArt style={style} value={value} conflict={conflicts[i]} hinted={hintCell === i} edges={edgesOf(r, c)} />
                      </button>
                    </div>
                  )
                })}
              </div>
            ))}
          </div>
        </div>
        {scrolls && <p className="mt-1 text-center text-[11px] text-white/50">Swipe the board sideways to see every column</p>}
      </div>

      {done ? (
        <ResultPanel
          success
          title="Crowned!"
          detail={`Hints used: ${game.hintsUsed}. Score ${game.score()}`}
          stars={game.stars()}
          primaryLabel="Continue"
          onPrimary={() => onFinished(true, game.score())}
          secondaryLabel="New board"
          onSecondary={onNew}
        />
      ) : (
        <div className="flex flex-wrap items-center justify-center gap-1">
          <ActionButton icon={StdIcons.Undo2} label="Undo" onClick={doUndo} disabled={!game.canUndo()} />
          <ActionButton icon={StdIcons.Lightbulb} label="Hint" onClick={doHint} />
          <ActionButton icon={StdIcons.RotateCcw} label="Clear" onClick={doClear} disabled={!game.cells.some((v) => v !== 0)} />
        </div>
      )}
    </div>
  )
}
