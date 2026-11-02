// --- web/game-trail ---
// Trail: drag one line from dot 1 through the dots in order, covering every square once. Drag back to undo.
// Also playable without dragging: tap/click a square next to the end of the line, or use the arrow keys.
import { useCallback, useEffect, useMemo, useReducer, useRef, useState } from 'react'
import { HelpCircle } from 'lucide-react'
import { generateFor } from './engine.js'
import { TrailState, cellAt, cellsAlong, stickyHit } from './state.js'
import { ActionButton, ResultPanel, Loading, StdIcons, useSpatialSession, useGenerated } from '../spatial/SpatialShell.jsx'

const RULES =
  'Draw one line that starts at dot 1 and passes through the dots in order, covering every square exactly once. ' +
  'The line can never cross a wall. Drag from the end of the line to extend it, and drag back along it to undo. ' +
  'You can also tap a square next to the end of the line, or use the arrow keys.'
const TIP = 'Squares in a corner or beside walls have only one or two ways through. Start there. Hint shows the next few squares.'

const KEY_DIRS = { ArrowUp: 'up', ArrowDown: 'down', ArrowLeft: 'left', ArrowRight: 'right' }

const reducedMotion = () => typeof window !== 'undefined' && !!window.matchMedia?.('(prefers-reduced-motion: reduce)')?.matches

export default function TrailGame({ difficulty, seed, onFinished }) {
  const puzzle = useGenerated(() => generateFor(difficulty, seed), [difficulty, seed])
  if (!puzzle) return <Loading />
  return <Board puzzle={puzzle} difficulty={difficulty} onFinished={onFinished} />
}

function describeSquare(p, g, i) {
  let s = `Row ${Math.floor(i / p.n) + 1}, column ${(i % p.n) + 1}. `
  const d = p.dotAt(i)
  if (d >= 0) s += `Dot ${d + 1}. `
  if (i === g.head) s += 'End of your line.'
  else if (g.onPath(i)) s += 'On your line. Activate to cut the line back to here.'
  else if (p.adjacent(g.head, i) && g.canStep(g.head, i)) s += 'Next to the end of your line. Activate to extend.'
  else s += 'Empty.'
  for (const [dr, dc, name] of [[0, 1, 'right'], [1, 0, 'below']]) {
    const r = Math.floor(i / p.n) + dr, c = (i % p.n) + dc
    if (r < p.n && c < p.n && p.blocked(i, r * p.n + c)) s += ` Wall ${name}.`
  }
  return s
}

export function Board({ puzzle, difficulty, onFinished }) {
  const n = puzzle.n
  const game = useMemo(() => new TrailState(puzzle), [puzzle])
  const [, bump] = useReducer((x) => x + 1, 0)
  const tel = useSpatialSession('trail', difficulty)
  const [done, setDone] = useState(false)
  const [lit, setLit] = useState(false)
  const [showResult, setShowResult] = useState(false)
  const [hintCells, setHintCells] = useState([])
  const [message, setMessage] = useState('')
  const [showRules, setShowRules] = useState(false)
  const boardRef = useRef(null)
  const drag = useRef({ active: false, last: null })
  useEffect(() => { tel.trackMoves(game.length) })

  const afterChange = useCallback(() => {
    setHintCells([])
    bump()
    if (game.solved()) {
      setDone(true)
      tel.complete({ score: game.score(), moves: game.length, hintsUsed: game.hintsUsed })
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [game])

  // win: a gold light runs along the finished line, then the result panel opens
  useEffect(() => {
    if (!done) return undefined
    const quick = reducedMotion()
    const a = setTimeout(() => setLit(true), quick ? 0 : 30)
    const b = setTimeout(() => setShowResult(true), quick ? 0 : 1400)
    return () => { clearTimeout(a); clearTimeout(b) }
  }, [done])

  const point = (e) => {
    const r = boardRef.current.getBoundingClientRect()
    return { x: e.clientX - r.left, y: e.clientY - r.top, cell: r.width / n }
  }

  const onPointerDown = (e) => {
    if (done || (e.pointerType === 'mouse' && e.button !== 0)) return
    const { x, y, cell } = point(e)
    const c0 = cellAt(x, y, cell, n)
    if (c0 < 0) return
    // only grab the line (touch it, or press the square next to its end); stray presses elsewhere draw nothing
    const grab = game.onPath(c0) || (puzzle.adjacent(game.head, c0) && game.canStep(game.head, c0))
    if (!grab) return
    e.preventDefault()
    try { e.currentTarget.setPointerCapture(e.pointerId) } catch { /* not capturable: window events still arrive */ }
    drag.current = { active: true, last: { x, y } }
    setMessage('')
    if (game.advanceTo(c0)) afterChange()
  }

  const onPointerMove = (e) => {
    const d = drag.current
    if (!d.active || done) return
    const { x, y, cell } = point(e)
    const cells = cellsAlong(d.last.x, d.last.y, x, y, cell, n)
    let any = false
    for (let i = 0; i < cells.length; i++) {
      const c = cells[i]
      if (c === game.head) continue
      // the final square only counts once the pointer is clearly inside it
      if (i === cells.length - 1 && stickyHit(x, y, game.head, cell, n)) continue
      if (game.advanceTo(c)) any = true
    }
    d.last = { x, y }
    if (any) afterChange()
  }

  const endDrag = (e) => {
    drag.current = { active: false, last: null }
    try { e.currentTarget.releasePointerCapture(e.pointerId) } catch { /* already released */ }
  }

  const say = (m) => setMessage(m)
  const where = () => `Row ${Math.floor(game.head / n) + 1}, column ${(game.head % n) + 1}. ${game.length} of ${puzzle.cells} squares.`

  const onKeyDown = (e) => {
    if (done || e.altKey || e.ctrlKey || e.metaKey) return
    const dir = KEY_DIRS[e.key]
    if (dir) {
      e.preventDefault()
      if (game.arrow(dir)) { afterChange(); say(where()) } else say(game.whyNot(dir))
    } else if (e.key === 'Backspace' || e.key === 'Delete') {
      e.preventDefault()
      if (game.undo()) { tel.undo(); afterChange(); say(where()) }
    }
  }

  const onTap = (i) => {
    if (done) return
    if (game.tap(i)) { afterChange(); say(where()) } else if (i !== game.head) say('Choose a square next to the end of the line.')
  }

  const nextDot = Math.min(game.dotsVisited + 1, game.totalDots)
  const cellsPct = 100 / n
  const center = (i) => [(i % n) * 100 + 50, Math.floor(i / n) * 100 + 50]
  const cells = game.pathCells()
  const linePoints = cells.map((c) => center(c).join(',')).join(' ')
  const head = center(game.head)

  const actions = [
    { icon: StdIcons.Undo2, label: 'Undo', disabled: game.length <= 1 || done, onClick: () => { if (game.undo()) { tel.undo(); afterChange() } } },
    { icon: StdIcons.RotateCcw, label: 'Reset', disabled: done, onClick: () => { game.reset(); setMessage(''); afterChange() } },
    {
      icon: StdIcons.Lightbulb, label: 'Hint', disabled: done,
      onClick: () => {
        const h = game.hint(3)
        tel.hint()
        bump()
        setHintCells(h)
        setMessage(h.length ? 'The next squares are marked.' : '')
      },
    },
    { icon: StdIcons.SkipForward, label: 'Skip', disabled: done, onClick: () => { tel.fail({ moves: game.length, hintsUsed: game.hintsUsed }); onFinished(false, 0, 'skipped') } },
  ]

  const label = `Trail puzzle, ${n} by ${n}. ${game.length} of ${puzzle.cells} squares covered. ${done ? 'Solved. ' : `Dot ${nextDot} of ${game.totalDots} is next. `}` +
    'Use the arrow keys to extend or retract the line, and Backspace to undo.'

  return (
    <div className="space-y-3 select-none">
      <div className="flex items-center justify-between gap-2">
        <div>
          <h2 className="text-lg font-bold text-white leading-tight">Trail</h2>
          <p className="text-xs text-white/60 capitalize">{difficulty}, {n}x{n}</p>
        </div>
        <p className="text-sm font-semibold text-white">Squares {game.length}/{puzzle.cells}</p>
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
      <p className="text-xs text-white/70 text-center">
        {done ? 'Solved. Every square is covered and the dots are joined in order.' : `Dot ${nextDot} of ${game.totalDots} is next. Drag from the end of the line, tap a square next to it, or use the arrow keys.`}
      </p>
      <p className="min-h-[1.25rem] text-xs text-white/80 text-center" role="status" aria-live="polite">{message}</p>

      <div
        ref={boardRef}
        role="group"
        aria-label={label}
        tabIndex={0}
        onKeyDown={onKeyDown}
        onPointerDown={onPointerDown}
        onPointerMove={onPointerMove}
        onPointerUp={endDrag}
        onPointerCancel={endDrag}
        onContextMenu={(e) => e.preventDefault()}
        className="relative mx-auto aspect-square w-full rounded-xl focus:outline-none focus-visible:ring-4 focus-visible:ring-white/80"
        style={{ maxWidth: Math.min(n * 80, 560), touchAction: 'none' }}
      >
        <svg viewBox={`0 0 ${n * 100} ${n * 100}`} className="absolute inset-0 h-full w-full block" aria-hidden="true">
          {Array.from({ length: puzzle.cells }, (_, i) => (
            <rect
              key={i}
              x={(i % n) * 100 + 3} y={Math.floor(i / n) * 100 + 3} width="94" height="94" rx="12"
              fill={game.onPath(i) ? 'rgba(56,189,248,0.25)' : 'rgba(255,255,255,0.12)'}
            />
          ))}
          {cells.length > 1 && (
            <polyline points={linePoints} fill="none" stroke="#38bdf8" strokeWidth="36" strokeLinecap="round" strokeLinejoin="round" />
          )}
          <circle cx={head[0]} cy={head[1]} r="27" fill="#38bdf8" />
          <circle cx={head[0]} cy={head[1]} r="10" fill="rgba(255,255,255,0.6)" />
          {hintCells.length > 0 && (
            <g>
              <polyline
                points={[head, ...hintCells.map(center)].map((pt) => pt.join(',')).join(' ')}
                fill="none" stroke="#4ade80" strokeWidth="14" strokeLinecap="round" strokeLinejoin="round" strokeDasharray="2 22" opacity="0.95"
              />
              {hintCells.map((c) => <circle key={c} cx={center(c)[0]} cy={center(c)[1]} r="13" fill="#4ade80" />)}
            </g>
          )}
          {/* walls */}
          {Array.from({ length: puzzle.cells }, (_, i) => {
            const x = (i % n) * 100, y = Math.floor(i / n) * 100
            return (
              <g key={i} stroke="#fff" strokeWidth="11" strokeLinecap="round">
                {puzzle.wallE[i] ? <line x1={x + 100} y1={y + 4} x2={x + 100} y2={y + 96} /> : null}
                {puzzle.wallS[i] ? <line x1={x + 4} y1={y + 100} x2={x + 96} y2={y + 100} /> : null}
              </g>
            )
          })}
          {done && cells.length > 1 && (
            <polyline
              points={linePoints} fill="none" stroke="#fbbf24" strokeWidth="30" strokeLinecap="round" strokeLinejoin="round"
              pathLength="1" strokeDasharray="1" strokeDashoffset={lit ? 0 : 1}
              style={{ transition: reducedMotion() ? 'none' : 'stroke-dashoffset 1.3s ease-in-out' }}
            />
          )}
          {puzzle.waypoints.map((c, k) => {
            const [cx, cy] = center(c)
            const visited = k < game.dotsVisited
            const isNext = k === game.dotsVisited && !done
            return (
              <g key={k}>
                {isNext && <circle cx={cx} cy={cy} r="40" fill="none" stroke="#fbbf24" strokeWidth="6" />}
                <circle cx={cx} cy={cy} r="30" fill={done ? '#fbbf24' : visited ? '#22c55e' : '#fff'} stroke="rgba(0,0,0,0.35)" strokeWidth="2" />
                <text
                  x={cx} y={cy} textAnchor="middle" dominantBaseline="central" fontSize={k + 1 > 9 ? 30 : 36} fontWeight="700"
                  fill={done ? '#000' : visited ? '#fff' : '#1f2937'}
                >
                  {k + 1}
                </text>
              </g>
            )
          })}
        </svg>
        {/* one real button per square: keyboard / screen reader activation extends or cuts the line; pointer input is handled above */}
        <div className="absolute inset-0 grid" style={{ gridTemplateColumns: `repeat(${n}, ${cellsPct}%)`, gridTemplateRows: `repeat(${n}, ${cellsPct}%)` }}>
          {Array.from({ length: puzzle.cells }, (_, i) => (
            <button
              key={i}
              type="button"
              tabIndex={-1}
              aria-label={describeSquare(puzzle, game, i)}
              onClick={() => onTap(i)}
              className="block h-full w-full rounded-lg bg-transparent focus:outline-none focus-visible:ring-4 focus-visible:ring-white focus-visible:ring-inset"
            />
          ))}
        </div>
      </div>

      <div className="flex flex-wrap items-center justify-center gap-1">
        {actions.map((a) => <ActionButton key={a.label} {...a} />)}
      </div>

      {done && showResult && (
        <ResultPanel
          success
          title="Trail complete!"
          detail={`Every square covered with one line. Score ${game.score()}`}
          stars={game.stars()}
          primaryLabel="Continue"
          onPrimary={() => onFinished(true, game.score(), 'completed')}
          secondaryLabel="Play again"
          onSecondary={() => {
            game.reset(); setDone(false); setLit(false); setShowResult(false); setMessage(''); setHintCells([]); tel.restart(); bump()
          }}
        />
      )}
    </div>
  )
}
