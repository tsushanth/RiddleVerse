import { useEffect, useRef, useState } from 'react'
import { Link } from 'react-router-dom'
import { createStarter, enterDigit, selectCell, remember, STARTER_SLUG, STARTER_DONE_KEY } from '../../utils/starterPuzzle'
import { webPlay } from '../../utils/webPlayAnalytics'

const focus = 'focus:outline-none focus-visible:ring-4 focus-visible:ring-white'

/** The starter puzzle card. Ways onward appear only once it is solved. */
export default function StarterPuzzle({ onSolved }) {
  const [state, setState] = useState(() => createStarter(Math.floor(Math.random() * 2 ** 31)))
  const startedAt = useRef(null)

  useEffect(() => { webPlay.view(STARTER_SLUG) }, [])

  const tap = (digit) => {
    const next = enterDigit(state, digit)
    if (next === state) return
    if (startedAt.current === null) {
      startedAt.current = Date.now()
      webPlay.start(STARTER_SLUG, 'easy')
    }
    setState(next)
    if (next.solved) {
      webPlay.complete(STARTER_SLUG, { difficulty: 'easy', durationMs: Date.now() - startedAt.current })
      remember(() => window.localStorage, STARTER_DONE_KEY)
      onSolved?.()
    }
  }

  return (
    <section aria-label="Your first puzzle" className="glass rounded-2xl p-4 text-white space-y-4">
      <p className="text-center">Tap a number to fill the highlighted square. Every row, column and small box needs 1, 2, 3 and 4.</p>
      <div dir="ltr" className="grid grid-cols-4 mx-auto w-full max-w-[18rem] border-2 border-white rounded-lg overflow-hidden">
        {state.values.map((value, i) => {
          const given = state.given[i] !== 0
          const wrong = value !== 0 && value !== state.solution[i]
          const selected = !state.solved && state.selected === i
          return (
            <button
              key={i}
              type="button"
              disabled={given || state.solved}
              onClick={() => setState(selectCell(state, i))}
              aria-label={`Row ${Math.floor(i / 4) + 1}, column ${(i % 4) + 1}, ${value || 'empty'}${wrong ? ', incorrect' : ''}`}
              aria-pressed={selected}
              className={`aspect-square text-3xl font-bold border border-white/30 ${focus} ${i % 4 === 1 ? 'border-r-2 border-r-white' : ''} ${
                Math.floor(i / 4) === 1 ? 'border-b-2 border-b-white' : ''
              } ${selected ? 'bg-yellow-300 text-purple-900' : wrong ? 'bg-red-500/60' : given ? 'bg-white/20' : 'bg-transparent text-yellow-200'}`}
            >
              {value || ''}
            </button>
          )
        })}
      </div>

      {state.solved ? (
        <div role="status" className="text-center space-y-3">
          <p className="text-2xl font-extrabold">Solved!</p>
          <div className="flex flex-wrap justify-center gap-3">
            <Link to="/play/daily" className={`bg-white text-purple-800 font-semibold px-5 py-3 min-h-[48px] rounded-xl ${focus}`}>Play today&apos;s puzzle</Link>
            <Link to="/play" reloadDocument className={`bg-white/15 hover:bg-white/25 font-semibold px-5 py-3 min-h-[48px] rounded-xl ${focus}`}>All games</Link>
          </div>
        </div>
      ) : (
        <div className="grid grid-cols-4 gap-2 mx-auto w-full max-w-[18rem]">
          {[1, 2, 3, 4].map((d) => (
            <button key={d} type="button" onClick={() => tap(d)} className={`min-h-[56px] rounded-xl bg-white text-purple-800 text-2xl font-bold ${focus}`}>
              {d}
            </button>
          ))}
        </div>
      )}
    </section>
  )
}
