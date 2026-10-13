// --- web spatial puzzles (feat/web-puzzles-spatial) ---
// Pixel Logic: nonogram with Fill / X / Move modes, drag painting, keyboard play, magnifier for big grids.
import { useCallback, useEffect, useLayoutEffect, useMemo, useReducer, useRef, useState } from 'react'
import { Square, X, Move, Eraser, FileCheck, ZoomIn, ZoomOut } from 'lucide-react'
import { generate, NonogramState } from './engine/nonogram.js'
import { SpatialFrame, ResultPanel, Loading, StdIcons, useSpatialSession, useGenerated } from './SpatialShell.jsx'

const MODE_FILL = 'fill'
const MODE_X = 'x'
const MODE_MOVE = 'move'
const CLUE_UNIT = 14
const ZOOM_CELL = 44

export default function PixelLogicGame({ difficulty, seed, onFinished }) {
  const puzzle = useGenerated(() => generate(difficulty, seed), [difficulty, seed])
  if (!puzzle) return <Loading />
  return <Board puzzle={puzzle} difficulty={difficulty} onFinished={onFinished} />
}

function Board({ puzzle, difficulty, onFinished }) {
  const n = puzzle.size
  const game = useMemo(() => new NonogramState(puzzle), [puzzle])
  const [, bump] = useReducer((x) => x + 1, 0)
  const tel = useSpatialSession('pixellogic', difficulty)
  const [mode, setMode] = useState(MODE_FILL)
  const [done, setDone] = useState(false)
  const [mistakes, setMistakes] = useState(null)
  const [message, setMessage] = useState('')
  const [zoomed, setZoomed] = useState(false)
  const [cursor, setCursor] = useState(0)
  const [boxW, setBoxW] = useState(320)
  const boxRef = useRef(null)
  const wrapRef = useRef(null)
  const cellsRef = useRef(null)
  const stroke = useRef(null)
  const zoomAnchor = useRef(null)

  const maxRow = Math.max(...puzzle.rowClues.map((c) => c.length))
  const maxCol = Math.max(...puzzle.colClues.map((c) => c.length))
  const clueW = maxRow * CLUE_UNIT + 8
  const clueH = maxCol * CLUE_UNIT + 6
  const baseCell = Math.max(14, Math.min(40, Math.floor((boxW - clueW - 2) / n)))
  const canZoom = baseCell < ZOOM_CELL - 4
  const cell = zoomed && canZoom ? ZOOM_CELL : baseCell

  useEffect(() => {
    const el = wrapRef.current // measure the outer wrapper: the scroll box width depends on the cell size
    if (!el) return undefined
    const ro = new ResizeObserver(() => setBoxW(el.clientWidth || 320))
    ro.observe(el)
    setBoxW(el.clientWidth || 320)
    return () => ro.disconnect()
  }, [])

  // keep the viewport centre stable when toggling the magnifier
  useLayoutEffect(() => {
    const a = zoomAnchor.current
    const el = boxRef.current
    if (!a || !el) return
    el.scrollLeft = a.fx * n * cell - el.clientWidth / 2 + clueW
    el.scrollTop = a.fy * n * cell - el.clientHeight / 2 + clueH
    zoomAnchor.current = null
  }, [cell, n, clueW, clueH])

  const toggleZoom = () => {
    const el = boxRef.current
    if (el) {
      zoomAnchor.current = {
        fx: (el.scrollLeft + el.clientWidth / 2 - clueW) / (n * cell),
        fy: (el.scrollTop + el.clientHeight / 2 - clueH) / (n * cell),
      }
    }
    setZoomed((z) => !z)
  }

  const rowOk = Array.from({ length: n }, (_, r) => game.isRowSatisfied(r))
  const colOk = Array.from({ length: n }, (_, c) => game.isColSatisfied(c))
  const filled = game.cells.reduce((s, v) => s + (v === 1 ? 1 : 0), 0)
  const target = puzzle.solution.filter(Boolean).length

  const commit = useCallback(() => {
    bump()
    if (!done && game.filledMatches()) {
      setDone(true)
      tel.complete({ score: game.score(), hintsUsed: game.hintsUsed, mistakes: 0 })
    }
  }, [done, game, tel])

  const cellFromEvent = (e) => {
    const rect = cellsRef.current.getBoundingClientRect()
    const c = Math.floor((e.clientX - rect.left) / cell)
    const r = Math.floor((e.clientY - rect.top) / cell)
    return r >= 0 && c >= 0 && r < n && c < n ? r * n + c : -1
  }

  const onPointerDown = (e) => {
    if (done) return
    const i = cellFromEvent(e)
    if (mode === MODE_MOVE) {
      if (e.pointerType === 'mouse') {
        const el = boxRef.current
        stroke.current = { pan: true, x: e.clientX, y: e.clientY, sl: el.scrollLeft, st: el.scrollTop }
        e.currentTarget.setPointerCapture(e.pointerId)
      }
      return
    }
    if (i < 0) return
    e.preventDefault()
    e.currentTarget.setPointerCapture(e.pointerId)
    const want = mode === MODE_FILL ? 1 : 2
    const old = game.cells[i]
    const val = old === want ? 0 : want
    game.beginStroke()
    game.paint(i, val)
    stroke.current = { start: i, last: i, old, val, axis: 0, x0: e.clientX, y0: e.clientY }
    setMistakes(null)
    setCursor(i)
    bump()
  }

  const onPointerMove = (e) => {
    const s = stroke.current
    if (!s) return
    if (s.pan) {
      const el = boxRef.current
      el.scrollLeft = s.sl - (e.clientX - s.x)
      el.scrollTop = s.st - (e.clientY - s.y)
      return
    }
    const dx = e.clientX - s.x0
    const dy = e.clientY - s.y0
    if (!s.axis && (Math.abs(dx) > 6 || Math.abs(dy) > 6)) s.axis = Math.abs(dx) >= Math.abs(dy) ? 1 : 2
    const rect = cellsRef.current.getBoundingClientRect()
    let x = e.clientX
    let y = e.clientY
    // lock to the row/column the stroke started on
    if (s.axis === 1) y = rect.top + Math.floor(s.start / n) * cell + cell / 2
    if (s.axis === 2) x = rect.left + (s.start % n) * cell + cell / 2
    const c = Math.floor((x - rect.left) / cell)
    const r = Math.floor((y - rect.top) / cell)
    if (r < 0 || c < 0 || r >= n || c >= n) return
    const idx = r * n + c
    if (idx === s.last) return
    // fast drags can skip cells between pointer events: fill the whole run along the locked axis
    const stepBy = s.axis === 2 ? n : 1
    const from = s.last
    const path = []
    if (s.axis && from !== idx && (idx - from) % stepBy === 0) {
      const dir = idx > from ? stepBy : -stepBy
      for (let k = from + dir; k !== idx + dir; k += dir) path.push(k)
    } else path.push(idx)
    s.last = idx
    let changed = false
    for (const k of path) {
      if (k === s.start) continue
      const cur = game.cells[k]
      const ok = s.val === 0 ? cur === s.old : cur === 0
      if (ok && cur !== s.val) {
        game.paint(k, s.val)
        changed = true
      }
    }
    if (changed) {
      setCursor(idx)
      bump()
    }
  }

  const onPointerUp = () => {
    const s = stroke.current
    stroke.current = null
    if (s && !s.pan) {
      game.endStroke()
      commit()
    }
  }

  const onKeyDown = (e) => {
    if (done) return
    const k = e.key
    const move = { ArrowLeft: [0, -1], ArrowRight: [0, 1], ArrowUp: [-1, 0], ArrowDown: [1, 0] }[k]
    if (move) {
      e.preventDefault()
      const r = Math.min(n - 1, Math.max(0, Math.floor(cursor / n) + move[0]))
      const c = Math.min(n - 1, Math.max(0, (cursor % n) + move[1]))
      setCursor(r * n + c)
      document.getElementById(`pl-cell-${r * n + c}`)?.scrollIntoView({ block: 'nearest', inline: 'nearest' })
      return
    }
    const want = k === ' ' || k === 'Enter' || k === 'f' ? (mode === MODE_X ? 2 : 1) : k === 'x' ? 2 : 0
    if (want) {
      e.preventDefault()
      game.applyStroke([cursor], game.cells[cursor] === want ? 0 : want)
      setMistakes(null)
      commit()
    } else if (k === 'Backspace' || k === 'Delete') {
      e.preventDefault()
      game.applyStroke([cursor], 0)
      commit()
    }
  }

  const actions = [
    { icon: StdIcons.Undo2, label: 'Undo', disabled: !game.canUndo() || done, onClick: () => { game.undo(); tel.undo(); setMistakes(null); commit() } },
    {
      icon: StdIcons.Lightbulb, label: 'Hint', disabled: done,
      onClick: () => {
        const i = game.hint()
        tel.hint()
        setMessage(i >= 0 ? `Revealed row ${Math.floor(i / n) + 1}, column ${(i % n) + 1}` : 'Nothing left to reveal')
        setMistakes(null)
        commit()
      },
    },
    {
      icon: FileCheck, label: 'Check', disabled: done,
      onClick: () => {
        const m = game.mistakes()
        setMistakes(m)
        setMessage(m.length ? `${m.length} wrong cell(s) outlined` : 'No mistakes so far')
      },
    },
    { icon: Eraser, label: 'Clear', disabled: done || game.cells.every((v) => v === 0), onClick: () => { game.clear(); setMistakes(null); commit() } },
    {
      icon: StdIcons.SkipForward, label: 'Skip', disabled: done,
      onClick: () => {
        tel.fail({ hintsUsed: game.hintsUsed, mistakes: game.mistakes().length })
        onFinished(false, 0, 'skipped')
      },
    },
  ]

  const modeBtn = (m, Icon, label) => (
    <button
      key={m}
      type="button"
      role="radio"
      aria-checked={mode === m}
      onClick={() => setMode(m)}
      className={`flex-1 min-h-[44px] flex items-center justify-center gap-1.5 rounded-xl text-sm border transition-colors ${
        mode === m ? 'bg-white/30 border-white/70 text-white font-semibold' : 'bg-white/5 border-white/15 text-white/80 hover:bg-white/15'
      }`}
    >
      <Icon size={16} aria-hidden="true" /> {label}
    </button>
  )

  const mistakeSet = new Set(mistakes || [])
  const gridW = clueW + n * cell
  const gridH = clueH + n * cell

  return (
    <SpatialFrame type="pixellogic" difficulty={difficulty} status={`${filled}/${target}`} message={message} actions={actions}>
      <div role="radiogroup" aria-label="Tool" className="flex gap-2">
        {modeBtn(MODE_FILL, Square, 'Fill')}
        {modeBtn(MODE_X, X, 'X mark')}
        {modeBtn(MODE_MOVE, Move, 'Move')}
      </div>

      <div className="relative" ref={wrapRef}>
        <div
          ref={boxRef}
          className="overflow-auto rounded-xl bg-black/20 mx-auto"
          style={{ maxHeight: '68vh', maxWidth: gridW + 2, overscrollBehavior: 'contain' }}
        >
          <div
            role="grid"
            tabIndex={0}
            aria-label={`Nonogram, ${n} by ${n}. ${filled} of ${target} cells filled. Arrow keys move, space fills, X marks.`}
            aria-activedescendant={`pl-cell-${cursor}`}
            onKeyDown={onKeyDown}
            className="outline-none focus-visible:ring-2 focus-visible:ring-white/70 rounded-xl"
            style={{ display: 'grid', gridTemplateColumns: `${clueW}px ${n * cell}px`, gridTemplateRows: `${clueH}px ${n * cell}px`, width: gridW, height: gridH }}
          >
            {/* corner */}
            <div className="z-20 bg-[#1d1440]" style={{ position: 'sticky', top: 0, left: 0, width: clueW, height: clueH, gridColumn: 1, gridRow: 1 }} />
            {/* column clues */}
            <div className="z-10 flex bg-[#1d1440]" style={{ position: 'sticky', top: 0, height: clueH, width: n * cell, gridColumn: 2, gridRow: 1 }}>
              {puzzle.colClues.map((cl, c) => (
                <div key={c} className="flex flex-col justify-end items-center" style={{ width: cell }}>
                  {cl.map((v, k) => (
                    <span key={k} className={`text-xs font-bold ${colOk[c] ? 'text-white/35 line-through' : 'text-white'}`} style={{ height: CLUE_UNIT, lineHeight: `${CLUE_UNIT}px` }}>
                      {v}
                    </span>
                  ))}
                </div>
              ))}
            </div>
            {/* row clues */}
            <div className="z-10 bg-[#1d1440]" style={{ position: 'sticky', left: 0, width: clueW, gridColumn: 1, gridRow: 2 }}>
              {puzzle.rowClues.map((cl, r) => (
                <div key={r} className="flex items-center justify-end gap-1 pr-1.5" style={{ height: cell }}>
                  {cl.map((v, k) => (
                    <span key={k} className={`text-xs font-bold ${rowOk[r] ? 'text-white/35 line-through' : 'text-white'}`}>
                      {v}
                    </span>
                  ))}
                </div>
              ))}
            </div>
              {/* cells */}
              <div
                ref={cellsRef}
                onPointerDown={onPointerDown}
                onPointerMove={onPointerMove}
                onPointerUp={onPointerUp}
                onPointerCancel={onPointerUp}
                className="grid"
                style={{
                  gridColumn: 2, gridRow: 2,
                  gridTemplateColumns: `repeat(${n}, ${cell}px)`,
                  width: n * cell,
                  touchAction: mode === MODE_MOVE ? 'auto' : 'none',
                  cursor: mode === MODE_MOVE ? 'grab' : 'crosshair',
                }}
              >
                {Array.from(game.cells).map((v, i) => {
                  const r = Math.floor(i / n)
                  const c = i % n
                  const bad = mistakeSet.has(i)
                  return (
                    <div
                      key={i}
                      id={`pl-cell-${i}`}
                      role="gridcell"
                      aria-label={`Row ${r + 1}, column ${c + 1}, ${v === 1 ? 'filled' : v === 2 ? 'marked empty' : 'unknown'}${bad ? ', wrong' : ''}`}
                      className="p-[1px]"
                      style={{
                        width: cell, height: cell,
                        borderRight: (c + 1) % 5 === 0 && c < n - 1 ? '2px solid rgba(255,255,255,0.35)' : undefined,
                        borderBottom: (r + 1) % 5 === 0 && r < n - 1 ? '2px solid rgba(255,255,255,0.35)' : undefined,
                      }}
                    >
                      <div
                        className={`relative w-full h-full rounded-[3px] flex items-center justify-center ${v === 1 ? 'bg-yellow-300' : 'bg-white/10'} ${
                          bad ? 'ring-2 ring-red-500' : ''
                        } ${i === cursor ? 'outline outline-2 outline-white/80 -outline-offset-2' : ''}`}
                      >
                        {v === 2 && <X size={Math.max(10, cell * 0.55)} className="text-white/45" aria-hidden="true" />}
                        {bad && <span className="absolute text-red-700 font-black text-xs" aria-hidden="true">!</span>}
                      </div>
                    </div>
                  )
                })}
              </div>
          </div>
        </div>
        {canZoom && (
          <button
            type="button"
            onClick={toggleZoom}
            aria-label={zoomed ? 'Show whole board' : 'Magnify board'}
            className="absolute bottom-2 right-2 h-12 w-12 rounded-full bg-purple-500/90 hover:bg-purple-500 text-white flex items-center justify-center shadow-lg"
          >
            {zoomed ? <ZoomOut size={22} /> : <ZoomIn size={22} />}
          </button>
        )}
      </div>
      {zoomed && <p className="text-xs text-white/60 text-center">Use the Move tool (or scroll) to pan the magnified board.</p>}

      {done && (
        <ResultPanel
          success
          title="Picture complete!"
          detail={`Hints used: ${game.hintsUsed}. Score ${game.score()}`}
          stars={game.stars()}
          primaryLabel="Continue"
          onPrimary={() => onFinished(true, game.score(), 'completed')}
        />
      )}
    </SpatialFrame>
  )
}
