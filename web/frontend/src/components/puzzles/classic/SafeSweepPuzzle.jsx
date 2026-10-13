// --- web classic puzzles (feat/web-puzzles-classic) --- Safe Sweep screen
import { useState, useRef, useEffect } from 'react'
import * as M from './safeSweepLogic'
import { randomSeed } from './rng'
import { tierFromPuzzle, getBest, submitBest } from './common'
import { ClassicFrame, ResultDialog, useClassicSession, btnClass, summaryAnswer } from './ClassicShell'

const NUM_COLORS = ['', '#3AA0FF', '#2DC98E', '#FF6B6B', '#A78BFA', '#FF8C00', '#1AA070', '#C77DFF', '#9b95c4']
const LONG_PRESS_MS = 400
const MIN_FIT = 30
const SCROLL_CELL = 48

export default function SafeSweepPuzzle({ puzzle, onAnswer }) {
  const tier = tierFromPuzzle(puzzle)
  const cfg = M.configFor(tier)
  const session = useClassicSession('safesweep', tier)
  const [game, setGame] = useState(() => M.newGameForTier(tier, randomSeed()))
  const [flagMode, setFlagMode] = useState(false)
  const [focus, setFocus] = useState(0)
  const [newBest, setNewBest] = useState(false)
  const [width, setWidth] = useState(340)
  const best = useRef(getBest('safesweep', tier)).current
  const reported = useRef(false)
  const wrap = useRef(null)
  const cellRefs = useRef([])
  const press = useRef(null)
  const gameRef = useRef(game)
  gameRef.current = game
  const over = game.status !== 'playing'
  const won = game.status === 'won'

  useEffect(() => {
    const el = wrap.current
    if (!el) return undefined
    const measure = () => setWidth(el.clientWidth)
    measure()
    if (typeof ResizeObserver === 'undefined') return undefined
    const ro = new ResizeObserver(measure)
    ro.observe(el)
    return () => ro.disconnect()
  }, [])

  useEffect(() => {
    if (over) {
      if (won) setNewBest(submitBest('safesweep', tier, game.actions, true))
      session.finish(won, { score: won ? 50 + cfg.mines * 5 : M.safeOpened(game), moves: game.actions })
    }
  }, [over]) // eslint-disable-line

  const fit = (width - 8) / cfg.cols
  const cell = fit >= MIN_FIT ? Math.min(fit, 64) : SCROLL_CELL

  const apply = (next) => {
    if (next !== gameRef.current) setGame(next)
    return next
  }
  const doTap = (i) => {
    const g = gameRef.current
    if (g.status !== 'playing') return
    apply(flagMode && !M.isRevealed(g, i) ? M.toggleFlag(g, i) : M.tap(g, i))
  }
  const doFlag = (i) => {
    const g = gameRef.current
    if (g.status === 'playing' && !M.isRevealed(g, i)) apply(M.toggleFlag(g, i))
  }

  // Pointer handling: tap, touch long-press (flag), right-click (flag). Panning the scroller cancels the press.
  const onPointerDown = (i) => (e) => {
    if (e.button !== undefined && e.button !== 0) return
    const rec = { x: e.clientX, y: e.clientY, long: false, moved: false, type: e.pointerType }
    rec.timer = setTimeout(() => { rec.long = true; doFlag(i) }, LONG_PRESS_MS)
    press.current = rec
  }
  const onPointerMove = (e) => {
    const r = press.current
    if (r && Math.hypot(e.clientX - r.x, e.clientY - r.y) > 10) { r.moved = true; clearTimeout(r.timer) }
  }
  const onPointerUp = (i) => () => {
    const r = press.current
    press.current = null
    if (!r) return
    clearTimeout(r.timer)
    if (!r.long && !r.moved) doTap(i)
  }
  const cancelPress = () => { if (press.current) clearTimeout(press.current.timer); press.current = null }
  const onContextMenu = (i) => (e) => {
    e.preventDefault()
    if (e.nativeEvent?.pointerType === 'touch' || press.current?.type === 'touch') return // touch long-press handled by timer
    doFlag(i)
  }

  const onKeyDown = (e) => {
    const { cols, rows } = game
    let f = focus
    const r = Math.floor(f / cols), c = f % cols
    switch (e.key) {
      case 'ArrowLeft': f = c > 0 ? f - 1 : f; break
      case 'ArrowRight': f = c < cols - 1 ? f + 1 : f; break
      case 'ArrowUp': f = r > 0 ? f - cols : f; break
      case 'ArrowDown': f = r < rows - 1 ? f + cols : f; break
      case 'Enter': case ' ': e.preventDefault(); doTap(focus); return
      case 'f': case 'F': e.preventDefault(); doFlag(focus); return
      default: return
    }
    e.preventDefault()
    setFocus(f)
    cellRefs.current[f]?.focus()
  }

  const safeOpened = M.safeOpened(game)
  const report = () => {
    if (reported.current) return
    reported.current = true
    onAnswer(won, summaryAnswer([won ? 'Board cleared' : 'Hit a mine', `${game.actions} taps`, `${safeOpened} squares opened`]))
  }

  return (
    <ClassicFrame
      game="safesweep" tier={tier}
      subtitle={`${cfg.sizeLabel} board ${cfg.rows}x${cfg.cols}. Open every safe square. First tap is safe.`}
      stats={[['Mines left', M.minesLeft(game)], ['Taps', game.actions], ['Best', best ?? '-']]}
    >
      <div ref={wrap} className="mx-auto w-full max-w-[640px]">
        <div className="overflow-x-auto rounded-xl bg-black/25" style={{ touchAction: 'pan-x pan-y' }}>
          <div
            role="grid" aria-label={`Minefield ${cfg.rows} by ${cfg.cols}. Arrow keys move, Enter opens, F flags.`}
            onKeyDown={onKeyDown} onPointerMove={onPointerMove} onPointerLeave={cancelPress} onPointerCancel={cancelPress}
            className="p-1 mx-auto"
            style={{ display: 'grid', gridTemplateColumns: `repeat(${cfg.cols}, ${cell}px)`, width: 'max-content' }}
          >
            {game.state.map((s, i) => {
              const revealed = s === 'R', flagged = s === 'F'
              const mine = revealed && M.isMine(game, i)
              const exploded = i === game.explodedIndex
              const count = revealed && !mine ? M.adjacentMines(game, i) : 0
              const r = Math.floor(i / cfg.cols) + 1, c = (i % cfg.cols) + 1
              const label = `Row ${r} column ${c}, ` + (exploded ? 'mine, exploded' : mine ? 'mine' : flagged ? 'flagged' : !revealed ? 'hidden' : count === 0 ? 'empty' : `${count} mines nearby`)
              return (
                <button
                  key={i} ref={(el) => { cellRefs.current[i] = el }}
                  role="gridcell" aria-label={label} tabIndex={i === focus ? 0 : -1}
                  onFocus={() => setFocus(i)}
                  onPointerDown={onPointerDown(i)} onPointerUp={onPointerUp(i)} onContextMenu={onContextMenu(i)}
                  className="m-[1.5px] rounded-md font-extrabold flex items-center justify-center focus:outline-none focus-visible:ring-2 focus-visible:ring-white select-none"
                  style={{
                    width: cell - 3, height: cell - 3, fontSize: cell * 0.5,
                    background: exploded ? '#D94848' : revealed ? 'rgba(15,10,40,0.55)' : '#6C4CF1',
                    color: NUM_COLORS[count] || '#fff', WebkitTouchCallout: 'none',
                  }}
                >
                  <span aria-hidden="true">{mine ? '✹' : flagged ? '⚑' : count > 0 ? count : ''}</span>
                </button>
              )
            })}
          </div>
        </div>
      </div>
      <button
        className={`${btnClass} w-full ${flagMode ? '!bg-amber-500/60' : ''}`}
        onClick={() => setFlagMode((f) => !f)} aria-pressed={flagMode}
        aria-label={`Flag mode toggle, currently ${flagMode ? 'on, taps place flags' : 'off, taps open squares'}`}
      >
        ⚑ {flagMode ? 'Flag mode: ON' : 'Flag mode: OFF'}
      </button>
      {over && (
        <ResultDialog
          success={won}
          headline={won ? 'Board cleared!' : 'Boom! You hit a mine'}
          lines={[`Taps: ${game.actions}`, `Squares opened: ${safeOpened}`, ...(newBest ? ['New best!'] : [])]}
          onContinue={report}
        />
      )}
    </ClassicFrame>
  )
}
