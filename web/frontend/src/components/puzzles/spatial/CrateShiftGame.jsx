// --- web spatial puzzles (feat/web-puzzles-spatial) ---
// Crate Shift: Sokoban-style. Arrow keys / WASD, swipe on the board, or the on-screen arrow pad.
import { useEffect, useMemo, useReducer, useRef, useState } from 'react'
import { ArrowLeft, ArrowRight, ArrowUp, ArrowDown } from 'lucide-react'
import { generate, CrateShiftState, DR, DC } from './engine/crateshift.js'
import { SpatialFrame, ResultPanel, Loading, StdIcons, useSpatialSession, useGenerated } from './SpatialShell.jsx'

const CELL = 48

export default function CrateShiftGame({ difficulty, seed, onFinished }) {
  const level = useGenerated(() => generate(difficulty, seed), [difficulty, seed])
  if (!level) return <Loading />
  return <Board level={level} difficulty={difficulty} onFinished={onFinished} />
}

function Board({ level, difficulty, onFinished }) {
  const game = useMemo(() => new CrateShiftState(level), [level])
  const [, bump] = useReducer((x) => x + 1, 0)
  const tel = useSpatialSession('crateshift', difficulty)
  const [done, setDone] = useState(false)
  const [hintDir, setHintDir] = useState(-1)
  const [message, setMessage] = useState('')
  const swipe = useRef(null)
  const doneRef = useRef(false)
  doneRef.current = done

  useEffect(() => { tel.trackMoves(game.moves) })

  const go = (d) => {
    if (doneRef.current) return
    setHintDir(-1)
    if (game.move(d)) {
      setMessage(game.deadlocked() ? 'A crate is stuck in a corner. Undo or restart.' : '')
      bump()
      if (game.solved()) {
        setDone(true)
        tel.complete({ score: game.score(), moves: game.moves, hintsUsed: game.hintsUsed })
      }
    }
  }
  const goRef = useRef(go)
  goRef.current = go

  useEffect(() => {
    const keys = { ArrowUp: 0, w: 0, W: 0, ArrowRight: 1, d: 1, D: 1, ArrowDown: 2, s: 2, S: 2, ArrowLeft: 3, a: 3, A: 3 }
    const onKey = (e) => {
      if (e.metaKey || e.ctrlKey || e.altKey) return
      const d = keys[e.key]
      if (d === undefined) return
      e.preventDefault()
      goRef.current(d)
    }
    document.addEventListener('keydown', onKey)
    return () => document.removeEventListener('keydown', onKey)
  }, [])

  const onDown = (e) => {
    e.preventDefault()
    e.currentTarget.setPointerCapture(e.pointerId)
    swipe.current = { x: e.clientX, y: e.clientY, ax: 0, ay: 0 }
  }
  const onMove = (e) => {
    const s = swipe.current
    if (!s) return
    s.ax += e.clientX - s.x
    s.ay += e.clientY - s.y
    s.x = e.clientX
    s.y = e.clientY
    const rect = e.currentTarget.getBoundingClientRect()
    const step = Math.min(rect.width / level.w, rect.height / level.h) * 0.6
    while (Math.abs(s.ax) >= step || Math.abs(s.ay) >= step) {
      if (Math.abs(s.ax) >= Math.abs(s.ay)) {
        if (s.ax > 0) { go(1); s.ax -= step } else { go(3); s.ax += step }
      } else if (s.ay > 0) { go(2); s.ay -= step } else { go(0); s.ay += step }
    }
  }
  const onUp = () => { swipe.current = null }

  const actions = [
    { icon: StdIcons.Undo2, label: 'Undo', disabled: !game.canUndo() || done, onClick: () => { game.undo(); tel.undo(); setHintDir(-1); setMessage(''); bump() } },
    { icon: StdIcons.RotateCcw, label: 'Restart', disabled: done, onClick: () => { game.restart(); setHintDir(-1); setMessage(''); bump() } },
    {
      icon: StdIcons.Lightbulb, label: 'Hint', disabled: done,
      onClick: () => {
        const d = game.hint()
        tel.hint()
        setHintDir(d)
        setMessage(d < 0 ? 'No way forward from here. Undo or restart.' : `Next move: ${['up', 'right', 'down', 'left'][d]}`)
        bump()
      },
    },
    {
      icon: StdIcons.SkipForward, label: 'Skip', disabled: done,
      onClick: () => { tel.fail({ moves: game.moves, hintsUsed: game.hintsUsed }); onFinished(false, 0, 'skipped') },
    },
  ]

  const onGoal = level.goals.reduce((s, g, i) => s + (g && game.boxes[i] ? 1 : 0), 0)
  const total = level.goals.filter(Boolean).length
  const W = level.w * CELL
  const H = level.h * CELL
  const px = (game.player % level.w) * CELL + CELL / 2
  const py = Math.floor(game.player / level.w) * CELL + CELL / 2

  const pad = (d, Icon, label) => (
    <button
      type="button"
      aria-label={label}
      onClick={() => go(d)}
      className="h-12 w-12 rounded-xl bg-white/15 hover:bg-white/25 active:bg-white/35 text-white flex items-center justify-center"
    >
      <Icon size={22} />
    </button>
  )

  return (
    <SpatialFrame
      type="crateshift"
      difficulty={difficulty}
      status={`Moves ${game.moves}`}
      message={message}
      actions={actions}
      extra={<p className="text-xs text-white/70 text-center">Pushes {game.pushes} | Par {level.par} | Crates placed {onGoal}/{total}</p>}
    >
      <svg
        viewBox={`0 0 ${W} ${H}`}
        className="w-full mx-auto block"
        style={{ maxWidth: Math.min(W * 1.15, 560), touchAction: 'none' }}
        role="img"
        aria-label={`Crate puzzle, ${level.w} by ${level.h}. ${onGoal} of ${total} crates on targets. Moves ${game.moves}. Use arrow keys, swipe, or the arrow buttons.`}
        onPointerDown={onDown}
        onPointerMove={onMove}
        onPointerUp={onUp}
        onPointerCancel={onUp}
      >
        <defs>
          <pattern id="cs-wall" width="8" height="8" patternUnits="userSpaceOnUse" patternTransform="rotate(45)">
            <rect width="8" height="8" fill="#3b3560" />
            <rect width="3" height="8" fill="#5a5290" />
          </pattern>
        </defs>
        {level.walls.map((wall, i) => {
          const x = (i % level.w) * CELL
          const y = Math.floor(i / level.w) * CELL
          if (wall) return <rect key={i} x={x} y={y} width={CELL} height={CELL} fill="url(#cs-wall)" stroke="rgba(0,0,0,0.35)" />
          const crate = game.boxes[i]
          const goal = level.goals[i]
          return (
            <g key={i}>
              <rect x={x + 1} y={y + 1} width={CELL - 2} height={CELL - 2} rx="4" fill="rgba(255,255,255,0.12)" />
              {goal && <circle cx={x + CELL / 2} cy={y + CELL / 2} r={CELL * 0.22} fill="none" stroke="#fbbf24" strokeWidth="4" strokeDasharray={crate ? undefined : '5 3'} />}
              {crate && (
                <g>
                  <rect x={x + 4} y={y + 4} width={CELL - 8} height={CELL - 8} rx="5" fill={goal ? '#43A047' : '#C98A3C'} stroke="rgba(0,0,0,0.45)" strokeWidth="2" />
                  {goal ? (
                    <path d={`M${x + 14} ${y + 25} l7 7 l13 -14`} stroke="#fff" strokeWidth="4" fill="none" strokeLinecap="round" />
                  ) : (
                    <g stroke="rgba(0,0,0,0.4)" strokeWidth="2.5">
                      <line x1={x + 9} y1={y + 9} x2={x + CELL - 9} y2={y + CELL - 9} />
                      <line x1={x + CELL - 9} y1={y + 9} x2={x + 9} y2={y + CELL - 9} />
                    </g>
                  )}
                </g>
              )}
            </g>
          )
        })}
        <g style={{ transform: `translate(${px}px, ${py}px)`, transition: 'transform 90ms' }}>
          <circle r={CELL * 0.34} fill="#8b5cf6" stroke="#fff" strokeWidth="2.5" />
          <circle cx="-6" cy="-3" r="3.2" fill="#fff" />
          <circle cx="6" cy="-3" r="3.2" fill="#fff" />
          <circle cx="-6" cy="-3" r="1.4" fill="#222" />
          <circle cx="6" cy="-3" r="1.4" fill="#222" />
          {hintDir >= 0 && (
            <g>
              <line x1="0" y1="0" x2={DC[hintDir] * CELL * 0.5} y2={DR[hintDir] * CELL * 0.5} stroke="#4ade80" strokeWidth="6" strokeLinecap="round" />
              <circle cx={DC[hintDir] * CELL * 0.5} cy={DR[hintDir] * CELL * 0.5} r="6" fill="#4ade80" />
            </g>
          )}
        </g>
      </svg>
      <div className="flex justify-center gap-2" role="group" aria-label="Move">
        {pad(3, ArrowLeft, 'Move left')}
        {pad(0, ArrowUp, 'Move up')}
        {pad(2, ArrowDown, 'Move down')}
        {pad(1, ArrowRight, 'Move right')}
      </div>
      {done && (
        <ResultPanel
          success
          title="All crates in place!"
          detail={`${game.moves} moves (par ${level.par}), ${game.pushes} pushes. Score ${game.score()}`}
          stars={game.stars()}
          primaryLabel="Continue"
          onPrimary={() => onFinished(true, game.score(), 'completed')}
          secondaryLabel="Retry for more stars"
          onSecondary={() => { game.restart(); setDone(false); setHintDir(-1); tel.restart(); bump() }}
        />
      )}
    </SpatialFrame>
  )
}
