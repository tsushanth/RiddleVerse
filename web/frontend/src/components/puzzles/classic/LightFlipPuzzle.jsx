// --- web classic puzzles (feat/web-puzzles-classic) --- Light Flip screen
import { useState, useRef, useEffect } from 'react'
import * as L from './lightFlipLogic'
import { randomSeed, popcount } from './rng'
import { tierFromPuzzle, efficiencyScore, getBest, submitBest } from './common'
import { ClassicFrame, ResultDialog, useClassicSession, btnClass, summaryAnswer } from './ClassicShell'

export default function LightFlipPuzzle({ puzzle, onAnswer }) {
  const tier = tierFromPuzzle(puzzle)
  const session = useClassicSession('lightflip', tier)
  const [game, setGame] = useState(() => L.newGame(tier, randomSeed()))
  const [hint, setHint] = useState(-1)
  const [newBest, setNewBest] = useState(false)
  const best = useRef(getBest('lightflip', tier)).current
  const reported = useRef(false)
  const solved = L.isSolved(game)
  const n = game.n
  const score = efficiencyScore(game.par, game.moves, 10 * game.hintsUsed)

  useEffect(() => {
    if (solved) {
      setNewBest(submitBest('lightflip', tier, game.moves, true))
      session.finish(true, { score, moves: game.moves })
    }
  }, [solved]) // eslint-disable-line

  return (
    <ClassicFrame
      game="lightflip" tier={tier}
      subtitle="Tap a light to flip it and its neighbours. Turn them all off."
      stats={[['Taps', game.moves], ['Par', game.par], ['Lights on', popcount(game.lights)], ['Best', best ?? '-']]}
    >
      <div
        className="mx-auto w-full max-w-[420px] aspect-square bg-black/25 rounded-2xl p-1.5 grid gap-1.5"
        style={{ gridTemplateColumns: `repeat(${n}, 1fr)`, gridTemplateRows: `repeat(${n}, 1fr)`, touchAction: 'manipulation' }}
        role="group" aria-label="Light grid"
      >
        {Array.from({ length: n * n }, (_, idx) => {
          const on = L.isOn(game, idx)
          return (
            <button
              key={idx} disabled={solved}
              onClick={() => { setGame(L.press(game, idx)); setHint(-1) }}
              aria-label={`Row ${Math.floor(idx / n) + 1} column ${(idx % n) + 1}, light ${on ? 'on' : 'off'}${hint === idx ? ', suggested tap' : ''}`}
              aria-pressed={on}
              className="rounded-xl flex items-center justify-center text-lg font-bold focus:outline-none focus-visible:ring-2 focus-visible:ring-white transition-colors"
              style={{
                minHeight: 44, background: on ? '#FFB020' : '#3A3463', color: on ? '#3B2A00' : '#9d97c9',
                boxShadow: hint === idx ? 'inset 0 0 0 3px #2DC98E' : undefined,
              }}
            >
              <span aria-hidden="true">{on ? '●' : '○'}</span>
            </button>
          )
        })}
      </div>
      <div className="grid grid-cols-2 gap-2">
        <button className={btnClass} disabled={!L.canUndo(game) || solved} onClick={() => { setGame(L.undo(game)); setHint(-1); session.undo() }}>↶ Undo</button>
        <button
          className={btnClass} disabled={solved}
          onClick={() => { setHint(L.hintCell(game) ?? -1); setGame(L.withHintUsed(game)); session.hint() }}
        >
          Hint ({game.hintsUsed})
        </button>
      </div>
      {solved && (
        <ResultDialog
          success headline="All lights off!"
          lines={[`Taps: ${game.moves} (par ${game.par})`, ...(game.hintsUsed ? [`Hints used: ${game.hintsUsed}`] : []), ...(newBest ? ['New best!'] : [])]}
          onContinue={() => {
            if (reported.current) return
            reported.current = true
            onAnswer(true, summaryAnswer([`Score ${score}`, `${game.moves} taps (par ${game.par})`]))
          }}
        />
      )}
    </ClassicFrame>
  )
}
