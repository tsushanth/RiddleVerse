// --- web classic puzzles (feat/web-puzzles-classic) --- Disc Tower screen
import { useState, useRef, useEffect } from 'react'
import * as D from './discTowerLogic'
import { tierFromPuzzle, efficiencyScore, getBest, submitBest } from './common'
import { ClassicFrame, ResultDialog, useClassicSession, useGameKeys, btnClass, summaryAnswer } from './ClassicShell'

const DISC_COLORS = ['#FF6B6B', '#FFB020', '#2DC98E', '#3AA0FF', '#6C4CF1', '#9B5DE5', '#FF8C00', '#1AA070']

export default function DiscTowerPuzzle({ puzzle, onAnswer }) {
  const tier = tierFromPuzzle(puzzle)
  const discs = D.discsFor(tier)
  const session = useClassicSession('disctower', tier)
  const [game, setGame] = useState(() => D.newGame(discs))
  const [selected, setSelected] = useState(-1)
  const [hints, setHints] = useState(0)
  const [message, setMessage] = useState('')
  const [newBest, setNewBest] = useState(false)
  const best = useRef(getBest('disctower', tier)).current
  const reported = useRef(false)
  const solved = D.isSolved(game)
  const par = D.parOf(game)
  const score = efficiencyScore(par, game.moves, 5 * hints)

  useEffect(() => {
    if (solved) {
      setNewBest(submitBest('disctower', tier, game.moves, true))
      session.finish(true, { score, moves: game.moves })
    }
  }, [solved]) // eslint-disable-line

  const tapPeg = (p) => {
    if (solved) return
    setMessage('')
    if (selected === -1) {
      if (game.pegs[p].length) setSelected(p)
    } else if (selected === p) {
      setSelected(-1)
    } else if (D.canMove(game, selected, p)) {
      setGame(D.move(game, selected, p)); setSelected(-1)
    } else {
      setMessage("A bigger disc can't go on a smaller one.")
      setSelected(-1); session.mistake()
    }
  }
  const doHint = () => {
    const m = D.hintMove(game)
    if (!m) return
    setHints((h) => h + 1); session.hint()
    setSelected(m[0])
    setMessage(`Hint: move the top disc from peg ${m[0] + 1} to peg ${m[1] + 1}.`)
  }

  useGameKeys((e) => {
    if (e.key >= '1' && e.key <= '3') { tapPeg(Number(e.key) - 1); return true }
    if (e.key === 'h' || e.key === 'H') { doHint(); return true }
    return false
  }, !solved)

  const discH = Math.min(30, Math.floor(220 / (discs + 1)))
  return (
    <ClassicFrame
      game="disctower" tier={tier}
      subtitle={`Move all ${discs} discs to the right-hand peg. Never put a bigger disc on a smaller one.`}
      stats={[['Moves', game.moves], ['Par', par], ['Best', best ?? '-']]}
    >
      <div className="grid grid-cols-3 gap-2" style={{ touchAction: 'manipulation' }}>
        {game.pegs.map((stack, p) => {
          const sel = selected === p
          return (
            <button
              key={p} onClick={() => tapPeg(p)} disabled={solved}
              aria-label={`Peg ${p + 1}${p === D.TARGET_PEG ? ' (goal)' : ''}${sel ? ', selected' : ''}${stack.length ? `, discs from bottom: ${stack.join(', ')}` : ', empty'}`}
              aria-pressed={sel}
              className="relative flex flex-col-reverse items-center rounded-2xl bg-black/25 pb-1.5 focus:outline-none focus-visible:ring-2 focus-visible:ring-white"
              style={{ height: discH * (discs + 1) + 24, boxShadow: sel ? 'inset 0 0 0 3px #A78BFA' : undefined }}
            >
              <span aria-hidden="true" className="absolute bottom-0 w-2 rounded bg-white/25" style={{ height: '97%' }} />
              <span aria-hidden="true" className="absolute top-1 text-[11px] text-white/50">{p === D.TARGET_PEG ? 'goal' : p + 1}</span>
              {stack.map((size, i) => {
                const lifted = sel && i === stack.length - 1
                return (
                  <span
                    key={size} aria-hidden="true"
                    className="relative z-10 rounded-full flex items-center justify-center text-white text-xs font-bold my-px"
                    style={{
                      width: `${30 + (65 * size) / discs}%`, height: discH, background: DISC_COLORS[(size - 1) % DISC_COLORS.length],
                      boxShadow: lifted ? '0 0 0 2px #fff' : undefined,
                    }}
                  >
                    {discH >= 18 ? size : ''}
                  </span>
                )
              })}
            </button>
          )
        })}
      </div>
      <p className="text-sm text-red-300 min-h-[20px] text-center" aria-live="polite">{message}</p>
      <div className="grid grid-cols-2 gap-2">
        <button className={btnClass} disabled={!D.canUndo(game) || solved} onClick={() => { setGame(D.undo(game)); setSelected(-1); setMessage(''); session.undo() }}>↶ Undo</button>
        <button className={btnClass} disabled={solved} onClick={doHint}>Hint ({hints})</button>
      </div>
      {solved && (
        <ResultDialog
          success headline="Tower moved!"
          lines={[`Moves: ${game.moves} (best possible ${par})`, ...(hints ? [`Hints used: ${hints}`] : []), ...(newBest ? ['New best!'] : [])]}
          onContinue={() => {
            if (reported.current) return
            reported.current = true
            onAnswer(true, summaryAnswer([`Score ${score}`, `${game.moves} moves (par ${par})`]))
          }}
        />
      )}
    </ClassicFrame>
  )
}
