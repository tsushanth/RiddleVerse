import { useEffect, useReducer, useRef } from 'react'
import { motion, AnimatePresence } from 'framer-motion'
import { Check, X } from 'lucide-react'
import PuzzleTimer from './PuzzleTimer'
import { formatTime } from './puzzleUtils'
import {
  parseMemorySingle, newSession, reduce, scoreRun, timeBudgetSeconds,
  FIRST_SYMBOL_MS, FEEDBACK_MS,
} from './memorySingleLogic'

/**
 * MemoryPreviousSinglePuzzle — DB type "memoryprevioussingle" (shown as Memory Match / N-Back).
 * Symbols appear one at a time. The first is only to remember; for every later symbol the player says whether it is
 * the SAME as the one just before it or DIFFERENT. The sequence is fixed for the whole game; scoring and the clock
 * live in memorySingleLogic.js (unit-tested).
 */
const SYMBOLS = {
  0: { glyph: '●', color: 'text-sky-300' },    // circle
  1: { glyph: '▲', color: 'text-amber-300' },  // triangle
}

function Session({ symbols, onAnswer }) {
  const [state, dispatch] = useReducer(reduce, symbols, newSession)
  const reported = useRef(false)

  const report = (result) => {
    if (reported.current) return
    reported.current = true
    onAnswer(result.passed, `${result.correct}/${result.total}`)
  }

  // First symbol: show it alone, then move on.
  useEffect(() => {
    if (state.done || state.index !== 0) return
    const t = setTimeout(() => dispatch({ type: 'firstShown' }), FIRST_SYMBOL_MS)
    return () => clearTimeout(t)
  }, [state.done, state.index])

  // Tick/cross pause after each answer.
  useEffect(() => {
    if (state.feedback === null) return
    const t = setTimeout(() => dispatch({ type: 'feedbackDone' }), FEEDBACK_MS)
    return () => clearTimeout(t)
  }, [state.feedback, state.index])

  // Finished: report once, after a short beat so the last tick/cross is visible.
  useEffect(() => {
    if (!state.done) return
    const t = setTimeout(() => report(scoreRun(state.expected, state.answers)), 800)
    return () => clearTimeout(t)
  }, [state.done])

  const handleTimeUp = () => {
    if (!state.done) dispatch({ type: 'timeout' })
  }

  const sym = SYMBOLS[state.symbols[state.index]] || { glyph: String(state.symbols[state.index]), color: 'text-white' }
  const canAnswer = state.index >= 1 && state.feedback === null && !state.done
  const answered = state.answers.length
  const total = state.expected.length

  return (
    <div className="space-y-5">
      <PuzzleTimer
        timerStr={formatTime(timeBudgetSeconds(state.symbols.length))}
        onTimeUp={handleTimeUp}
        paused={state.done}
      />

      <p className="text-purple-300 text-sm font-semibold uppercase tracking-widest text-center">
        {state.index === 0 ? 'Remember this one' : 'Same as the one before?'}
      </p>

      <div className="flex items-center gap-2">
        <div className="flex-1 h-2 bg-white/10 rounded-full overflow-hidden">
          <motion.div
            className="h-full bg-purple-500 rounded-full"
            animate={{ width: `${total ? (answered / total) * 100 : 0}%` }}
          />
        </div>
        <span className="text-white/50 text-xs">{answered}/{total}</span>
      </div>

      <div className="flex justify-center py-6">
        <AnimatePresence mode="wait">
          {!state.done && (
            <motion.div
              key={state.index}
              initial={{ opacity: 0, scale: 0.5 }}
              animate={{ opacity: 1, scale: 1 }}
              exit={{ opacity: 0, scale: 0.5 }}
              className="relative w-28 h-28 rounded-2xl bg-purple-500/30 border-2 border-purple-400/50 flex items-center justify-center"
            >
              <span className={`font-bold text-6xl ${sym.color}`}>{sym.glyph}</span>
              {state.feedback && (
                <span className={`absolute -top-3 -right-3 rounded-full p-1 ${state.feedback === 'correct' ? 'bg-green-500' : 'bg-red-500'}`}>
                  {state.feedback === 'correct' ? <Check size={18} className="text-white" /> : <X size={18} className="text-white" />}
                </span>
              )}
            </motion.div>
          )}
        </AnimatePresence>
      </div>

      {state.index === 0 && !state.done && (
        <p className="text-white/50 text-xs text-center">Nothing to tap yet. Next one: is it the same as this?</p>
      )}

      {state.index >= 1 && !state.done && (
        <div className="grid grid-cols-2 gap-3">
          <button
            onClick={() => dispatch({ type: 'answer', value: 1 })}
            disabled={!canAnswer}
            className="bg-green-500 hover:bg-green-600 disabled:opacity-50 text-white font-semibold py-4 rounded-xl transition-colors"
          >
            SAME
          </button>
          <button
            onClick={() => dispatch({ type: 'answer', value: 0 })}
            disabled={!canAnswer}
            className="bg-rose-500 hover:bg-rose-600 disabled:opacity-50 text-white font-semibold py-4 rounded-xl transition-colors"
          >
            DIFFERENT
          </button>
        </div>
      )}

      {state.done && (
        <p className="text-white/70 text-sm text-center">
          {scoreRun(state.expected, state.answers).correct}/{total} correct
        </p>
      )}
    </div>
  )
}

export default function MemoryPreviousSinglePuzzle({ puzzle, onAnswer }) {
  const { symbols } = parseMemorySingle(puzzle)

  if (symbols.length < 2) {
    return (
      <div className="space-y-3 text-center">
        <p className="text-white/70 text-sm">This puzzle could not be loaded.</p>
        <button
          onClick={() => onAnswer(false, 'invalid', true)}
          className="bg-white/10 hover:bg-white/15 text-white/70 font-medium py-2 px-4 rounded-xl text-sm"
        >
          Skip
        </button>
      </div>
    )
  }

  // key: a new puzzle starts a fresh session; nothing else ever replaces the sequence mid-game.
  return <Session key={puzzle.puzzleId} symbols={symbols} onAnswer={onAnswer} />
}
