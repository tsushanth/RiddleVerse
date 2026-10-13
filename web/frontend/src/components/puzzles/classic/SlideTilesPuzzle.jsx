// --- web classic puzzles (feat/web-puzzles-classic) --- Slide Tiles screen
import { useState, useRef, useEffect } from 'react'
import * as S from './slideTilesLogic'
import { randomSeed } from './rng'
import { tierFromPuzzle, efficiencyScore, getBest, submitBest } from './common'
import { ClassicFrame, ResultDialog, useClassicSession, useGameKeys, KEY_DIR, btnClass, summaryAnswer } from './ClassicShell'

export default function SlideTilesPuzzle({ puzzle, onAnswer }) {
  const tier = tierFromPuzzle(puzzle)
  const session = useClassicSession('slidetiles', tier)
  const [game, setGame] = useState(() => S.newGame(tier, randomSeed()))
  const [newBest, setNewBest] = useState(false)
  const best = useRef(getBest('slidetiles', tier)).current
  const reported = useRef(false)
  const solved = S.isSolved(game)
  const n = game.n

  useEffect(() => {
    if (solved) {
      setNewBest(submitBest('slidetiles', tier, game.moves, true))
      session.finish(true, { score: efficiencyScore(game.par, game.moves), moves: game.moves })
    }
  }, [solved]) // eslint-disable-line

  const doTap = (idx) => {
    if (solved) return
    const next = S.tap(game, idx)
    if (next) setGame(next)
    else session.mistake()
  }

  useGameKeys((e) => {
    const dir = KEY_DIR[e.key]
    if (!dir) return false
    const idx = S.keyTarget(game, dir)
    if (idx >= 0) doTap(idx)
    return true
  }, !solved)

  const score = efficiencyScore(game.par, game.moves)
  return (
    <ClassicFrame
      game="slidetiles" tier={tier}
      subtitle="Put the numbers in order, empty space bottom right."
      stats={[['Moves', game.moves], ['Par', game.par], ['Best', best ?? '-']]}
    >
      <div
        className="mx-auto w-full max-w-[420px] aspect-square bg-black/25 rounded-2xl p-1.5 grid gap-1.5"
        style={{ gridTemplateColumns: `repeat(${n}, 1fr)`, gridTemplateRows: `repeat(${n}, 1fr)`, touchAction: 'manipulation', containerType: 'inline-size' }}
        role="group" aria-label="Sliding tiles board. Tap a tile in the empty space's row or column, or use arrow keys."
      >
        {game.tiles.map((v, idx) => {
          const home = v === idx + 1
          const label = v === 0 ? `Empty space, row ${Math.floor(idx / n) + 1} column ${(idx % n) + 1}`
            : `Tile ${v}, row ${Math.floor(idx / n) + 1} column ${(idx % n) + 1}${home ? ', in place' : ''}`
          return v === 0 ? (
            <div key={idx} role="img" aria-label={label} className="rounded-lg bg-white/5" />
          ) : (
            <button
              key={idx} onClick={() => doTap(idx)} aria-label={label} disabled={solved}
              className="rounded-lg font-extrabold text-white focus:outline-none focus-visible:ring-2 focus-visible:ring-white transition-colors"
              style={{ background: home ? '#2DC98E' : '#6C4CF1', fontSize: `${38 / n}cqw`, minHeight: 44 }}
            >
              {v}{home ? <span className="sr-only"> (in place)</span> : null}
              {home && <span aria-hidden="true" className="block h-0.5 mx-auto w-3 rounded bg-white/70 -mt-0.5" />}
            </button>
          )
        })}
      </div>
      <button className={`${btnClass} w-full`} onClick={() => { setGame(S.undo(game)); session.undo() }} disabled={!S.canUndo(game) || solved}>
        ↶ Undo
      </button>
      {solved && (
        <ResultDialog
          success headline="Solved!"
          lines={[`Moves: ${game.moves} (par ${game.par})`, ...(newBest ? ['New best!'] : [])]}
          onContinue={() => {
            if (reported.current) return
            reported.current = true
            onAnswer(true, summaryAnswer([`Score ${score}`, `${game.moves} moves (par ${game.par})`]))
          }}
        />
      )}
    </ClassicFrame>
  )
}
