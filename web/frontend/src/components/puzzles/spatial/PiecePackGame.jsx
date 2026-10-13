// --- web spatial puzzles (feat/web-puzzles-spatial) ---
// Piece Pack: drag a piece from the tray onto the 8x8 board, tap a piece then the board, or use the keyboard
// (1-3 select a piece, arrows move, Enter places).
import { useEffect, useMemo, useReducer, useRef, useState } from 'react'
import { PiecePackState, SHAPES, N, STATUS, dropOrigin, fits } from './engine/piecepack.js'
import { SpatialFrame, ResultPanel, StdIcons, Glyph, colorHex, colorName, useSpatialSession } from './SpatialShell.jsx'

const CELL = 40
const BOARD = CELL * N
const LIFT = 56
const TRAY_TOP = BOARD + 10
const TRAY_H = 118
const VB_H = TRAY_TOP + TRAY_H
const SLOT_W = BOARD / 3

function trayCell(p) {
  return Math.min(CELL * 0.8, (SLOT_W * 0.88) / p.cols, (TRAY_H * 0.88) / p.rows, 30)
}

function PieceCells({ p, x, y, cell, fill, opacity = 1, glyph = true, stroke }) {
  return (
    <g opacity={opacity} pointerEvents="none">
      {p.cells.map((cc) => {
        const cx = x + (cc % 10) * cell
        const cy = y + Math.floor(cc / 10) * cell
        return (
          <g key={cc}>
            <rect x={cx + 1} y={cy + 1} width={cell - 2} height={cell - 2} rx={cell * 0.14} fill={fill}
              stroke={stroke} strokeWidth={stroke ? 2.5 : 0} strokeDasharray={stroke ? '4 3' : undefined} />
            {glyph && <Glyph index={p.id} cx={cx + cell / 2} cy={cy + cell / 2} s={cell * 0.45} />}
          </g>
        )
      })}
    </g>
  )
}

export default function PiecePackGame({ difficulty, seed, onFinished }) {
  const game = useMemo(() => new PiecePackState(seed, difficulty), [seed, difficulty])
  const [, bump] = useReducer((x) => x + 1, 0)
  const tel = useSpatialSession('piecepack', difficulty)
  const svgRef = useRef(null)
  const [selected, setSelected] = useState(-1)
  const [drag, setDrag] = useState(null) // {slot, x, y}
  const [hintMove, setHintMove] = useState(null)
  const [message, setMessage] = useState('')
  const [cursor, setCursor] = useState([3, 3])
  const [kbd, setKbd] = useState(false)
  const gesture = useRef(null)
  const reported = useRef(false)
  const cfg = game.config
  const over = game.status !== STATUS.PLAYING

  useEffect(() => { tel.trackMoves(game.piecesPlaced) })
  useEffect(() => {
    if (reported.current) return
    if (game.status === STATUS.WON) {
      reported.current = true
      tel.complete({ score: game.finalScore(), moves: game.piecesPlaced, hintsUsed: game.hintsUsed })
    } else if (over) {
      reported.current = true
      tel.fail({ score: game.score, moves: game.piecesPlaced, hintsUsed: game.hintsUsed, errorMessage: undefined })
    }
  })

  const toBoxCoords = (e) => {
    const svg = svgRef.current
    const pt = svg.createSVGPoint()
    pt.x = e.clientX
    pt.y = e.clientY
    const q = pt.matrixTransform(svg.getScreenCTM().inverse())
    return [q.x, q.y]
  }

  const place = (slot, r, c) => {
    const before = game.linesCleared
    if (game.tryPlace(slot, r, c)) {
      setHintMove(null)
      setSelected(-1)
      const cleared = game.linesCleared - before
      setMessage(cleared ? `Cleared ${cleared} line${cleared > 1 ? 's' : ''}` : '')
      bump()
      return true
    }
    setMessage("That piece doesn't fit there")
    bump()
    return false
  }

  const onPointerDown = (e) => {
    if (over) return
    const [x, y] = toBoxCoords(e)
    setKbd(false)
    const slot = y >= TRAY_TOP - 6 ? Math.min(2, Math.max(0, Math.floor(x / SLOT_W))) : -1
    e.preventDefault()
    svgRef.current.setPointerCapture(e.pointerId)
    if (slot >= 0 && game.hand[slot] >= 0) {
      gesture.current = { kind: 'piece', slot, x0: e.clientX, y0: e.clientY, moved: false }
    } else {
      gesture.current = { kind: 'board' }
    }
  }

  const onPointerMove = (e) => {
    const g = gesture.current
    if (!g || g.kind !== 'piece') return
    if (!g.moved && Math.hypot(e.clientX - g.x0, e.clientY - g.y0) > 6) g.moved = true
    if (g.moved) {
      const [x, y] = toBoxCoords(e)
      setDrag({ slot: g.slot, x, y })
    }
  }

  const onPointerUp = (e) => {
    const g = gesture.current
    gesture.current = null
    if (!g) return
    const [x, y] = toBoxCoords(e)
    if (g.kind === 'piece') {
      if (g.moved) {
        const p = SHAPES[game.hand[g.slot]]
        const [r, c] = dropOrigin(x, y, p, 0, 0, CELL, LIFT)
        setDrag(null)
        place(g.slot, r, c)
      } else {
        setSelected((s) => (s === g.slot ? -1 : g.slot))
        setDrag(null)
      }
    } else if (selected >= 0 && game.hand[selected] >= 0 && y < BOARD) {
      const p = SHAPES[game.hand[selected]]
      const c = Math.min(N - p.cols, Math.max(0, Math.floor(x / CELL) - Math.floor(p.cols / 2)))
      const r = Math.min(N - p.rows, Math.max(0, Math.floor(y / CELL) - Math.floor(p.rows / 2)))
      place(selected, r, c)
    }
  }

  const onKeyDown = (e) => {
    if (over) return
    const k = e.key
    if (k >= '1' && k <= '3') {
      const s = Number(k) - 1
      if (game.hand[s] >= 0) { setSelected(s); setKbd(true); e.preventDefault() }
    } else if (k.startsWith('Arrow')) {
      e.preventDefault()
      setKbd(true)
      const d = { ArrowUp: [-1, 0], ArrowDown: [1, 0], ArrowLeft: [0, -1], ArrowRight: [0, 1] }[k]
      setCursor(([r, c]) => [Math.min(N - 1, Math.max(0, r + d[0])), Math.min(N - 1, Math.max(0, c + d[1]))])
    } else if ((k === 'Enter' || k === ' ') && selected >= 0 && game.hand[selected] >= 0) {
      e.preventDefault()
      const p = SHAPES[game.hand[selected]]
      place(selected, Math.min(cursor[0], N - p.rows), Math.min(cursor[1], N - p.cols))
    }
  }

  const actions = [
    { icon: StdIcons.Undo2, label: 'Undo', disabled: !game.canUndo(), onClick: () => { game.undoMove(); reported.current = false; tel.undo(); setSelected(-1); setHintMove(null); bump() } },
    {
      icon: StdIcons.RotateCcw, label: 'Restart',
      onClick: () => {
        if (reported.current) tel.restart()
        reported.current = false
        game.restart(); setSelected(-1); setHintMove(null); setMessage(''); bump()
      },
    },
    {
      icon: StdIcons.Lightbulb, label: 'Hint', disabled: over,
      onClick: () => {
        const h = game.hint()
        game.hintsUsed++
        tel.hint()
        setHintMove(h); setSelected(-1)
        setMessage(h ? 'Suggested spot outlined' : 'No piece fits')
        bump()
      },
    },
    {
      icon: StdIcons.SkipForward, label: 'Skip', disabled: game.status === STATUS.WON,
      onClick: () => {
        if (!reported.current) { reported.current = true; tel.fail({ score: game.score, moves: game.piecesPlaced }) }
        onFinished(false, 0, 'skipped')
      },
    },
  ]

  const handDesc = game.hand.map((id, i) => (id >= 0 ? `${i + 1}: ${SHAPES[id].name}` : null)).filter(Boolean).join(', ')
  let ghost = null
  const activeSlot = drag ? drag.slot : kbd ? selected : -1
  if (activeSlot >= 0 && game.hand[activeSlot] >= 0) {
    const p = SHAPES[game.hand[activeSlot]]
    const [r, c] = drag ? dropOrigin(drag.x, drag.y, p, 0, 0, CELL, LIFT) : [Math.min(cursor[0], N - p.rows), Math.min(cursor[1], N - p.cols)]
    ghost = { p, r, c, ok: fits(game.board, p, r, c) }
  }

  return (
    <SpatialFrame
      type="piecepack"
      difficulty={difficulty}
      status={`${game.linesCleared}/${cfg.targetLines} lines`}
      message={message}
      actions={actions}
      extra={<p className="text-xs text-white/70 text-center">Clear {cfg.targetLines} lines in {cfg.pieceBudget} pieces. Pieces left {game.piecesLeft()}. Score {game.score}</p>}
    >
      <svg
        ref={svgRef}
        viewBox={`0 0 ${BOARD} ${VB_H}`}
        className="w-full max-w-[460px] mx-auto block rounded-xl outline-none focus-visible:ring-2 focus-visible:ring-white/70"
        style={{ touchAction: 'none' }}
        tabIndex={0}
        role="application"
        aria-label={`Block board, 8 by 8. ${game.linesCleared} of ${cfg.targetLines} lines cleared. Pieces in hand ${handDesc}. Drag a piece onto the board, or tap a piece then the board. Keyboard: keys 1 to 3 pick a piece, arrows move, Enter places.`}
        onPointerDown={onPointerDown}
        onPointerMove={onPointerMove}
        onPointerUp={onPointerUp}
        onPointerCancel={() => { gesture.current = null; setDrag(null) }}
        onKeyDown={onKeyDown}
      >
        <rect x="0" y="0" width={BOARD} height={BOARD} fill="rgba(0,0,0,0.25)" rx="8" />
        {game.board.map((v, i) => {
          const r = Math.floor(i / N)
          const c = i % N
          const x = c * CELL
          const y = r * CELL
          return v === 0 ? (
            <rect key={i} x={x + 2} y={y + 2} width={CELL - 4} height={CELL - 4} rx="5" fill="rgba(255,255,255,0.1)" />
          ) : (
            <g key={i}>
              <rect x={x + 1} y={y + 1} width={CELL - 2} height={CELL - 2} rx="5" fill={colorHex(v - 1)} />
              <Glyph index={v - 1} cx={x + CELL / 2} cy={y + CELL / 2} s={CELL * 0.45} />
            </g>
          )
        })}
        {[4].map((i) => (
          <g key={i} stroke="rgba(255,255,255,0.35)" strokeWidth="2">
            <line x1={i * CELL} y1="0" x2={i * CELL} y2={BOARD} />
            <line x1="0" y1={i * CELL} x2={BOARD} y2={i * CELL} />
          </g>
        ))}
        {hintMove && game.hand[hintMove[0]] >= 0 && (
          <PieceCells p={SHAPES[game.hand[hintMove[0]]]} x={hintMove[2] * CELL} y={hintMove[1] * CELL} cell={CELL} fill="none" glyph={false} stroke="#4ade80" />
        )}
        {ghost && (
          <PieceCells p={ghost.p} x={ghost.c * CELL} y={ghost.r * CELL} cell={CELL} glyph={false}
            fill={ghost.ok ? 'rgba(255,255,255,0.45)' : 'rgba(239,68,68,0.45)'} stroke={ghost.ok ? undefined : '#fca5a5'} />
        )}
        {/* tray */}
        <rect x="0" y={TRAY_TOP - 4} width={BOARD} height={TRAY_H + 4} rx="10" fill="rgba(0,0,0,0.2)" />
        {game.hand.map((id, slot) => {
          if (id < 0) return null
          const p = SHAPES[id]
          const tc = trayCell(p)
          const ox = slot * SLOT_W + (SLOT_W - p.cols * tc) / 2
          const oy = TRAY_TOP + (TRAY_H - p.rows * tc) / 2
          return (
            <g key={slot}>
              {selected === slot && (
                <rect x={slot * SLOT_W + 4} y={TRAY_TOP} width={SLOT_W - 8} height={TRAY_H - 4} rx="10" fill="rgba(255,255,255,0.12)" stroke="#fff" strokeWidth="2.5" />
              )}
              <text x={slot * SLOT_W + 12} y={TRAY_TOP + 16} fontSize="12" fill="rgba(255,255,255,0.6)" pointerEvents="none">{slot + 1}</text>
              {!(drag && drag.slot === slot) && <PieceCells p={p} x={ox} y={oy} cell={tc} fill={colorHex(id)} />}
            </g>
          )
        })}
        {drag && game.hand[drag.slot] >= 0 && (() => {
          const p = SHAPES[game.hand[drag.slot]]
          return <PieceCells p={p} x={drag.x - (p.cols * CELL) / 2} y={drag.y - p.rows * CELL - LIFT} cell={CELL} fill={colorHex(p.id)} opacity={0.9} />
        })()}
      </svg>
      <p className="sr-only" role="status">{handDesc}; piece colours: {game.hand.filter((i) => i >= 0).map((i) => colorName(i)).join(', ')}</p>

      {game.status === STATUS.WON && (
        <ResultPanel
          success
          title="Target reached!"
          detail={`Cleared ${game.linesCleared} lines with ${game.piecesPlaced} pieces. Score ${game.finalScore()}`}
          stars={game.stars()}
          primaryLabel="Continue"
          onPrimary={() => onFinished(true, game.finalScore(), 'completed')}
        />
      )}
      {(game.status === STATUS.STUCK || game.status === STATUS.OUT_OF_PIECES) && (
        <ResultPanel
          success={false}
          title={game.status === STATUS.STUCK ? 'No piece fits' : 'Out of pieces'}
          detail={`You cleared ${game.linesCleared} of ${cfg.targetLines} lines. Undo a move, or try again.`}
          primaryLabel="Try again"
          onPrimary={() => { tel.restart(); reported.current = false; game.restart(); setSelected(-1); setHintMove(null); bump() }}
          secondaryLabel="Undo last move"
          onSecondary={() => { game.undoMove(); reported.current = false; bump() }}
        />
      )}
    </SpatialFrame>
  )
}
