import { useState } from 'react'
import { motion } from 'framer-motion'
import { Check, Lightbulb, Eye, Loader2 } from 'lucide-react'
import PuzzleTimer from './PuzzleTimer'

/**
 * Generic Q&A puzzle: text question + text input answer.
 * Used for qa, riddle, storypuzzle, estimation, etc.
 * Server-side answer checking via onSubmitToServer.
 */
export default function QAPuzzle({ puzzle, onAnswer, onSubmitToServer, isChecking }) {
  const [input, setInput] = useState('')
  const [showHint, setShowHint] = useState(false)
  const [showAnswer, setShowAnswer] = useState(false)
  const [submitted, setSubmitted] = useState(false)

  const question = typeof puzzle.question === 'string' ? puzzle.question : JSON.stringify(puzzle.question)

  const handleSubmit = (e) => {
    e.preventDefault()
    if (!input.trim() || submitted) return
    setSubmitted(true)
    onSubmitToServer(input.trim())
  }

  const handleReveal = () => {
    setShowAnswer(true)
    setSubmitted(true)
    onAnswer(false, '', true) // showed answer
  }

  const handleTimeUp = () => {
    if (!submitted) {
      setSubmitted(true)
      onAnswer(false, '')
    }
  }

  return (
    <div className="space-y-5">
      <PuzzleTimer timerStr={puzzle.timer} onTimeUp={handleTimeUp} paused={submitted} />

      <div className="space-y-2">
        <p className="text-white/50 text-xs uppercase tracking-widest font-semibold">Question</p>
        <p className="text-white text-lg leading-relaxed font-medium">{question}</p>
      </div>

      {puzzle.image_url && (
        <div className="rounded-xl overflow-hidden">
          <img src={puzzle.image_url} alt="Puzzle" className="w-full h-48 object-cover" />
        </div>
      )}

      {showHint && puzzle.hint && (
        <motion.div
          initial={{ opacity: 0, height: 0 }}
          animate={{ opacity: 1, height: 'auto' }}
          className="bg-yellow-500/20 rounded-xl p-4 border border-yellow-500/30"
        >
          <p className="text-yellow-300 text-sm font-medium mb-1">Hint:</p>
          <p className="text-yellow-100">{puzzle.hint}</p>
        </motion.div>
      )}

      {!submitted && (
        <form onSubmit={handleSubmit} className="space-y-4">
          <input
            type="text"
            value={input}
            onChange={(e) => setInput(e.target.value)}
            placeholder="Type your answer..."
            className="w-full bg-white/10 border border-white/20 text-white placeholder-white/50 rounded-xl py-3 px-4 focus:outline-none focus:ring-2 focus:ring-purple-400/50 transition-all"
            disabled={isChecking}
          />

          <div className="flex gap-3">
            <button
              type="submit"
              disabled={!input.trim() || isChecking}
              className="flex-1 flex items-center justify-center gap-2 bg-green-500 hover:bg-green-600 disabled:bg-green-500/50 text-white font-semibold py-3 rounded-xl transition-colors"
            >
              {isChecking ? (
                <Loader2 className="animate-spin" size={20} />
              ) : (
                <>
                  <Check size={20} />
                  Submit
                </>
              )}
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

            <button
              type="button"
              onClick={handleReveal}
              className="flex items-center gap-2 bg-white/10 hover:bg-white/20 text-white/70 px-4 py-3 rounded-xl transition-colors"
            >
              <Eye size={20} />
            </button>
          </div>
        </form>
      )}
    </div>
  )
}
