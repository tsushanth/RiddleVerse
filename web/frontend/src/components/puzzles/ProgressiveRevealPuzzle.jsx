import { useState, useEffect } from 'react'
import { motion, AnimatePresence } from 'framer-motion'
import { Check, Eye, Lightbulb, Loader2 } from 'lucide-react'
import PuzzleTimer from './PuzzleTimer'
import { parseJsonField } from './puzzleUtils'

export default function ProgressiveRevealPuzzle({ puzzle, onAnswer, onSubmitToServer, isChecking }) {
  const parsed = parseJsonField(puzzle.question)

  // Clues can be an array or single string
  const clues = Array.isArray(parsed?.clues || parsed)
    ? (parsed?.clues || parsed)
    : typeof puzzle.question === 'string'
    ? [puzzle.question]
    : [JSON.stringify(puzzle.question)]

  const [revealedCount, setRevealedCount] = useState(1)
  const [input, setInput] = useState('')
  const [submitted, setSubmitted] = useState(false)

  const revealNext = () => {
    if (revealedCount < clues.length) {
      setRevealedCount((prev) => prev + 1)
    }
  }

  const handleSubmit = (e) => {
    e.preventDefault()
    if (!input.trim() || submitted) return
    setSubmitted(true)
    onSubmitToServer(input.trim())
  }

  const handleTimeUp = () => {
    if (!submitted) {
      setSubmitted(true)
      onAnswer(false, input)
    }
  }

  // Score based on how few clues needed
  const clueBonus = clues.length - revealedCount

  return (
    <div className="space-y-5">
      <PuzzleTimer timerStr={puzzle.timer || '1:30'} onTimeUp={handleTimeUp} paused={submitted} />

      <div className="text-center space-y-1">
        <p className="text-white/50 text-xs uppercase tracking-widest font-semibold">
          Progressive Reveal
        </p>
        <p className="text-white/40 text-xs">
          Fewer clues = more points! ({revealedCount}/{clues.length} clues shown)
        </p>
      </div>

      {/* Clues */}
      <div className="space-y-2">
        <AnimatePresence>
          {clues.slice(0, revealedCount).map((clue, i) => (
            <motion.div
              key={i}
              initial={{ opacity: 0, x: -20 }}
              animate={{ opacity: 1, x: 0 }}
              transition={{ delay: i * 0.1 }}
              className="bg-white/10 rounded-xl p-3 border border-white/15"
            >
              <div className="flex items-start gap-2">
                <span className="flex-shrink-0 w-6 h-6 rounded-full bg-purple-500/40 flex items-center justify-center text-xs font-bold text-purple-300">
                  {i + 1}
                </span>
                <p className="text-white/90 text-sm leading-relaxed">
                  {typeof clue === 'object' ? clue.text || clue.clue || JSON.stringify(clue) : clue}
                </p>
              </div>
            </motion.div>
          ))}
        </AnimatePresence>
      </div>

      {/* Reveal more button */}
      {revealedCount < clues.length && !submitted && (
        <button
          onClick={revealNext}
          className="w-full flex items-center justify-center gap-2 bg-white/10 hover:bg-white/15 text-white/70 py-2.5 rounded-xl transition-colors text-sm"
        >
          <Eye size={16} />
          Reveal Next Clue ({clues.length - revealedCount} remaining)
        </button>
      )}

      {/* Answer input */}
      {!submitted && (
        <form onSubmit={handleSubmit} className="space-y-3">
          <input
            type="text"
            value={input}
            onChange={(e) => setInput(e.target.value)}
            placeholder="What is it?"
            className="w-full bg-white/10 border border-white/20 text-white placeholder-white/50 rounded-xl py-3 px-4 focus:outline-none focus:ring-2 focus:ring-purple-400/50"
            disabled={isChecking}
          />
          <button
            type="submit"
            disabled={!input.trim() || isChecking}
            className="w-full flex items-center justify-center gap-2 bg-green-500 hover:bg-green-600 disabled:bg-green-500/50 text-white font-semibold py-3 rounded-xl transition-colors"
          >
            {isChecking ? (
              <Loader2 className="animate-spin" size={20} />
            ) : (
              <>
                <Check size={20} />
                Guess
              </>
            )}
          </button>
        </form>
      )}
    </div>
  )
}
