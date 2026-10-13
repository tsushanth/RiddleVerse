// --- web classic puzzles (feat/web-puzzles-classic) --- Tile Merge screen
import { useState, useRef, useEffect, useCallback } from 'react'
import * as T from './tileMergeLogic'
import { randomSeed } from './rng'
import { tierFromPuzzle, getBest, submitBest } from './common'
import { ClassicFrame, ResultDialog, useClassicSession, useGameKeys, KEY_DIR, btnClass, summaryAnswer } from './ClassicShell'

const COLORS = {
  2: ['#E6E1FF', '#231B4A'], 4: ['#CFC6FF', '#231B4A'], 8: ['#3AA0FF', '#fff'], 16: ['#1F7BD6', '#fff'],
  32: ['#2DC98E', '#0B3B2B'], 64: ['#1AA070', '#fff'], 128: ['#FFB020', '#3B2A00'], 256: ['#FF8C00', '#fff'],
  512: ['#FF6B6B', '#fff'], 1024: ['#D94848', '#fff'], 2048: ['#6C4CF1', '#fff'],
}
const tileColors = (v) => COLORS[v] || ['#231B4A', '#fff']
const ARROWS = [['left', '←', 'Move tiles left'], ['up', '↑', 'Move tiles up'], ['down', '↓', 'Move tiles down'], ['right', '→', 'Move tiles right']]

export default function TileMergePuzzle({ puzzle, onAnswer }) {
  const tier = tierFromPuzzle(puzzle)
  const cfg = T.configFor(tier)
  const session = useClassicSession('tilemerge', tier)
  const [game, setGame] = useState(() => T.newGame(randomSeed()))
  const [undosLeft, setUndosLeft] = useState(T.UNDOS)
  const [continuing, setContinuing] = useState(false)
  const [ended, setEnded] = useState(false)
  const [newBest, setNewBest] = useState(false)
  const best = useRef(getBest('tilemerge', tier)).current
  const reported = useRef(false)
  const swipe = useRef(null)

  const st = T.status(game, cfg)
  const showWin = st === 'goal' && !continuing && !ended
  const over = st === 'lost' || st === 'won'
  const showResult = ended || over
  const won = game.reachedTarget

  useEffect(() => {
    if (showResult) {
      setNewBest(submitBest('tilemerge', tier, game.score, false))
      session.finish(won, { score: game.score, moves: game.moves })
    }
  }, [showResult]) // eslint-disable-line

  const doMove = useCallback((dir) => {
    if (showWin || showResult) return
    setGame((g) => T.move(g, dir, cfg.target))
  }, [showWin, showResult, cfg.target])

  useGameKeys((e) => {
    const dir = KEY_DIR[e.key]
    if (!dir) return false
    doMove(dir)
    return true
  }, !showWin && !showResult)

  const doUndo = () => {
    if (undosLeft > 0 && T.canUndo(game) && !showResult) {
      setGame(T.undo(game)); setUndosLeft((n) => n - 1); session.undo()
    }
  }

  const onPointerDown = (e) => { swipe.current = { x: e.clientX, y: e.clientY, id: e.pointerId } }
  const onPointerUp = (e) => {
    const s = swipe.current
    swipe.current = null
    if (!s || s.id !== e.pointerId) return
    const dx = e.clientX - s.x, dy = e.clientY - s.y
    if (Math.max(Math.abs(dx), Math.abs(dy)) < 24) return
    doMove(Math.abs(dx) > Math.abs(dy) ? (dx > 0 ? 'right' : 'left') : (dy > 0 ? 'down' : 'up'))
  }

  const n = T.SIZE
  const report = () => {
    if (reported.current) return
    reported.current = true
    onAnswer(won, summaryAnswer([`Top tile ${T.maxTile(game)}`, `Score ${game.score}`, `${game.moves} moves`]))
  }

  return (
    <ClassicFrame
      game="tilemerge" tier={tier}
      subtitle={`Reach the ${cfg.target} tile within ${cfg.moveBudget} moves.`}
      stats={[['Score', game.score], ['Best', Math.max(best ?? 0, game.score)], ['Moves', `${game.moves}/${cfg.moveBudget}`], ['Top tile', T.maxTile(game)]]}
    >
      <div
        className="mx-auto w-full max-w-[420px] aspect-square bg-black/25 rounded-2xl p-1.5 grid grid-cols-4 grid-rows-4 gap-1.5"
        style={{ touchAction: 'none', containerType: 'inline-size' }}
        role="group"
        aria-label={`Tile board. Highest tile ${T.maxTile(game)}. Swipe, use arrow keys or WASD, or the arrow buttons below.`}
        onPointerDown={onPointerDown} onPointerUp={onPointerUp} onPointerCancel={() => { swipe.current = null }}
      >
        {game.cells.map((v, i) => {
          const [bg, fg] = v ? tileColors(v) : ['rgba(255,255,255,0.08)', '#fff']
          const digits = String(v).length
          const fs = digits <= 2 ? 10.5 : digits === 3 ? 8.5 : 6.5
          return (
            <div
              key={i}
              role="img"
              aria-label={v ? `Row ${Math.floor(i / n) + 1} column ${(i % n) + 1} tile ${v}` : `Row ${Math.floor(i / n) + 1} column ${(i % n) + 1} empty`}
              className="rounded-lg flex items-center justify-center font-extrabold transition-colors duration-100"
              style={{ background: bg, color: fg, fontSize: `${fs}cqw` }}
            >
              {v || ''}
            </div>
          )
        })}
      </div>
      <div className="grid grid-cols-5 gap-2">
        <button className={btnClass} onClick={doUndo} disabled={undosLeft <= 0 || !T.canUndo(game) || showResult} aria-label={`Undo last move, ${undosLeft} left`}>
          ↶ {undosLeft}
        </button>
        {ARROWS.map(([d, glyph, label]) => (
          <button key={d} className={`${btnClass} text-xl`} onClick={() => doMove(d)} aria-label={label}>{glyph}</button>
        ))}
      </div>
      {game.reachedTarget && continuing && !showResult && (
        <div className="text-center">
          <button className={btnClass} onClick={() => setEnded(true)}>Finish with this score</button>
        </div>
      )}
      {showWin && (
        <ResultDialog
          success headline={`You made ${cfg.target}!`}
          lines={['Finish now, or keep going to push your score higher (the move limit no longer applies).']}
          onContinue={() => setEnded(true)}
          extra={<button className={`${btnClass} flex-1`} onClick={() => setContinuing(true)}>Keep going</button>}
        />
      )}
      {showResult && (
        <ResultDialog
          success={won}
          headline={won ? 'Goal reached!' : !T.canMove(game) ? 'No moves left' : 'Out of moves'}
          lines={[`Top tile: ${T.maxTile(game)}`, `Score: ${game.score}`, `Moves: ${game.moves}`, ...(newBest ? ['New best score!'] : [])]}
          onContinue={report}
        />
      )}
    </ClassicFrame>
  )
}
