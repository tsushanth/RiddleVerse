import { useState } from 'react'
import { motion } from 'framer-motion'
import PuzzleTimer from './PuzzleTimer'

/**
 * Multiple choice for best transition word in a sentence.
 * Uses the same trivia-style option buttons.
 */
export default function SentenceTransitionsPuzzle({ puzzle, onAnswer }) {
  const question = typeof puzzle.question === 'string' ? puzzle.question : JSON.stringify(puzzle.question)
  const options = puzzle.options || []
  const correctAnswer = (puzzle.answer || '').trim().toLowerCase()

  const [selected, setSelected] = useState(null)
  const [revealed, setRevealed] = useState(false)

  const handleSelect = (option) => {
    if (revealed) return
    setSelected(option)
    setRevealed(true)

    const isCorrect = option.toLowerCase().trim() === correctAnswer
    setTimeout(() => onAnswer(isCorrect, option), 1200)
  }

  const handleTimeUp = () => {
    if (!revealed) {
      setRevealed(true)
      setTimeout(() => onAnswer(false, ''), 800)
    }
  }

  const getStyle = (option) => {
    if (!revealed) return 'bg-white/10 border-white/20 hover:bg-white/20'
    const isCorrect = option.toLowerCase().trim() === correctAnswer
    const isSel = option === selected
    if (isCorrect) return 'bg-green-500/30 border-green-400'
    if (isSel) return 'bg-red-500/30 border-red-400'
    return 'bg-white/5 border-white/10 opacity-50'
  }

  return (
    <div className="space-y-5">
      <PuzzleTimer timerStr={puzzle.timer} onTimeUp={handleTimeUp} paused={revealed} />

      <p className="text-white/50 text-xs uppercase tracking-widest font-semibold text-center">
        Choose the best transition
      </p>

      <div className="bg-white/10 rounded-xl p-4 border border-white/15">
        <p className="text-white text-base leading-relaxed">{question}</p>
      </div>

      <div className="grid gap-3">
        {options.map((option, i) => (
          <motion.button
            key={i}
            whileTap={!revealed ? { scale: 0.97 } : {}}
            onClick={() => handleSelect(option)}
            disabled={revealed}
            className={`w-full text-left px-4 py-3.5 rounded-xl border-2 transition-all duration-300 font-medium text-white ${getStyle(option)}`}
          >
            <span className="inline-flex items-center gap-3">
              <span className="flex-shrink-0 w-8 h-8 rounded-lg bg-white/10 flex items-center justify-center text-sm font-bold text-white/60">
                {String.fromCharCode(65 + i)}
              </span>
              {option}
            </span>
          </motion.button>
        ))}
      </div>
    </div>
  )
}
