import { useState } from 'react'
import { motion } from 'framer-motion'
import { Check, Lightbulb } from 'lucide-react'
import PuzzleTimer from './PuzzleTimer'
import { parseJsonField, fuzzyMatch } from './puzzleUtils'

export default function NumberSequencePuzzle({ puzzle, onAnswer }) {
  const parsed = parseJsonField(puzzle.question)

  // Extract sequence - could be array or comma-separated string
  let sequence = []
  if (Array.isArray(parsed)) {
    sequence = parsed
  } else if (parsed?.sequence) {
    sequence = parsed.sequence
  } else if (typeof puzzle.question === 'string') {
    // Try to extract numbers from text like "2, 4, 8, 16, ?"
    sequence = puzzle.question.match(/-?\d+\.?\d*/g)?.map(Number) || []
  }

  const correctAnswer = puzzle.answer?.toString() || ''
  const [input, setInput] = useState('')
  const [showHint, setShowHint] = useState(false)
  const [submitted, setSubmitted] = useState(false)
  const [isCorrect, setIsCorrect] = useState(null)

  const handleSubmit = (e) => {
    e.preventDefault()
    if (!input.trim() || submitted) return
    setSubmitted(true)

    const correct = fuzzyMatch(input.trim(), correctAnswer)
    setIsCorrect(correct)
    setTimeout(() => onAnswer(correct, input.trim()), 800)
  }

  const handleTimeUp = () => {
    if (!submitted) {
      setSubmitted(true)
      setIsCorrect(false)
      onAnswer(false, input)
    }
  }

  return (
    <div className="space-y-5">
      <PuzzleTimer timerStr={puzzle.timer} onTimeUp={handleTimeUp} paused={submitted} />

      <p className="text-white/50 text-xs uppercase tracking-widest font-semibold text-center">
        What comes next?
      </p>

      {/* Sequence display */}
      <div className="flex flex-wrap items-center justify-center gap-2">
        {sequence.map((num, i) => (
          <motion.div
            key={i}
            initial={{ opacity: 0, y: 10 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ delay: i * 0.1 }}
            className="flex items-center gap-2"
          >
            <div className="w-14 h-14 rounded-xl bg-white/15 border border-white/25 flex items-center justify-center">
              <span className="text-white font-bold text-lg">{num}</span>
            </div>
            {i < sequence.length - 1 && (
              <span className="text-white/30 text-lg">,</span>
            )}
          </motion.div>
        ))}

        {/* Blank slot */}
        <div className="flex items-center gap-2">
          <span className="text-white/30 text-lg">,</span>
          <motion.div
            animate={submitted ? {} : { borderColor: ['rgba(168,85,247,0.3)', 'rgba(168,85,247,0.7)', 'rgba(168,85,247,0.3)'] }}
            transition={{ repeat: Infinity, duration: 2 }}
            className={`w-14 h-14 rounded-xl border-2 border-dashed flex items-center justify-center ${
              submitted
                ? isCorrect
                  ? 'bg-green-500/20 border-green-400'
                  : 'bg-red-500/20 border-red-400'
                : 'bg-white/5 border-purple-400/50'
            }`}
          >
            <span className="text-white font-bold text-lg">?</span>
          </motion.div>
        </div>
      </div>

      {showHint && puzzle.hint && (
        <motion.div
          initial={{ opacity: 0 }}
          animate={{ opacity: 1 }}
          className="bg-yellow-500/20 rounded-xl p-3 border border-yellow-500/30 text-center"
        >
          <p className="text-yellow-100 text-sm">{puzzle.hint}</p>
        </motion.div>
      )}

      {!submitted && (
        <form onSubmit={handleSubmit} className="space-y-3">
          <input
            type="text"
            value={input}
            onChange={(e) => setInput(e.target.value)}
            placeholder="Enter the next number..."
            className="w-full bg-white/10 border border-white/20 text-white placeholder-white/50 rounded-xl py-3 px-4 focus:outline-none focus:ring-2 focus:ring-purple-400/50 text-center text-lg font-bold"
            inputMode="numeric"
          />
          <div className="flex gap-3">
            <button
              type="submit"
              disabled={!input.trim()}
              className="flex-1 flex items-center justify-center gap-2 bg-green-500 hover:bg-green-600 disabled:bg-green-500/50 text-white font-semibold py-3 rounded-xl transition-colors"
            >
              <Check size={20} />
              Submit
            </button>
            {puzzle.hint && !showHint && (
              <button
                type="button"
                onClick={() => setShowHint(true)}
                className="flex items-center gap-2 bg-yellow-500/20 hover:bg-yellow-500/30 text-yellow-400 px-4 py-3 rounded-xl transition-colors"
              >
                <Lightbulb size={20} />
              </button>
            )}
          </div>
        </form>
      )}
    </div>
  )
}
