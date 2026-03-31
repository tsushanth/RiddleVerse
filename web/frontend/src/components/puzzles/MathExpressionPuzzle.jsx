import { useState } from 'react'
import { motion } from 'framer-motion'
import { Check, Lightbulb } from 'lucide-react'
import PuzzleTimer from './PuzzleTimer'
import { fuzzyMatch } from './puzzleUtils'

export default function MathExpressionPuzzle({ puzzle, onAnswer }) {
  const question = typeof puzzle.question === 'string' ? puzzle.question : JSON.stringify(puzzle.question)
  const correctAnswer = (puzzle.answer || '').toString()

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
        Solve it
      </p>

      {/* Math expression display */}
      <div className="bg-white/10 rounded-2xl p-6 text-center border border-white/15">
        <p className="text-white text-2xl font-bold leading-relaxed font-mono">{question}</p>
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

      {!submitted ? (
        <form onSubmit={handleSubmit} className="space-y-3">
          <input
            type="text"
            value={input}
            onChange={(e) => setInput(e.target.value)}
            placeholder="Your answer..."
            className="w-full bg-white/10 border border-white/20 text-white placeholder-white/50 rounded-xl py-3 px-4 focus:outline-none focus:ring-2 focus:ring-purple-400/50 text-center text-xl font-bold"
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
      ) : (
        <motion.div
          initial={{ scale: 0.8, opacity: 0 }}
          animate={{ scale: 1, opacity: 1 }}
          className={`text-center p-4 rounded-xl ${
            isCorrect ? 'bg-green-500/20 text-green-300' : 'bg-red-500/20 text-red-300'
          }`}
        >
          <p className="font-bold text-lg">{isCorrect ? 'Correct!' : `Answer: ${correctAnswer}`}</p>
        </motion.div>
      )}
    </div>
  )
}
