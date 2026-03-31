import { useState } from 'react'
import { motion } from 'framer-motion'
import PuzzleTimer from './PuzzleTimer'
import { parseJsonField, shuffle } from './puzzleUtils'

export default function TriviaPuzzle({ puzzle, onAnswer }) {
  const [selected, setSelected] = useState(null)
  const [revealed, setRevealed] = useState(false)

  const parsed = parseJsonField(puzzle.question)
  const question = typeof parsed === 'string' ? parsed : (parsed?.question || parsed?.text || JSON.stringify(parsed))
  let options = puzzle.options || []
  if (typeof options === 'string') {
    try { options = JSON.parse(options) } catch { options = [] }
  }
  if (!Array.isArray(options)) options = []
  const correctAnswer = (typeof puzzle.answer === 'string' ? puzzle.answer : '') || ''

  const handleSelect = (option) => {
    if (revealed) return
    setSelected(option)
    setRevealed(true)

    const isCorrect =
      option.toLowerCase().trim() === correctAnswer.toLowerCase().trim()

    setTimeout(() => {
      onAnswer(isCorrect, option)
    }, 1200)
  }

  const handleTimeUp = () => {
    if (!revealed) {
      setRevealed(true)
      setTimeout(() => onAnswer(false, ''), 800)
    }
  }

  const getOptionStyle = (option) => {
    if (!revealed) {
      return 'bg-white/10 border-white/20 hover:bg-white/20 hover:border-purple-400/50 active:scale-[0.97]'
    }
    const isCorrect = option.toLowerCase().trim() === correctAnswer.toLowerCase().trim()
    const isSelected = option === selected
    if (isCorrect) return 'bg-green-500/30 border-green-400 text-green-300'
    if (isSelected && !isCorrect) return 'bg-red-500/30 border-red-400 text-red-300'
    return 'bg-white/5 border-white/10 text-white/40'
  }

  return (
    <div className="space-y-5">
      <PuzzleTimer timerStr={puzzle.timer} onTimeUp={handleTimeUp} paused={revealed} />

      <div className="space-y-2">
        <p className="text-white/50 text-xs uppercase tracking-widest font-semibold">Question</p>
        <p className="text-white text-lg leading-relaxed font-medium">{question}</p>
      </div>

      {puzzle.hint && (
        <p className="text-yellow-300/70 text-sm italic">Hint: {puzzle.hint}</p>
      )}

      <div className="grid gap-3">
        {options.map((option, i) => (
          <motion.button
            key={i}
            whileTap={!revealed ? { scale: 0.97 } : {}}
            onClick={() => handleSelect(option)}
            disabled={revealed}
            className={`w-full text-left px-4 py-3.5 rounded-xl border-2 transition-all duration-300 font-medium ${getOptionStyle(option)}`}
          >
            <span className="inline-flex items-center gap-3">
              <span className="flex-shrink-0 w-8 h-8 rounded-lg bg-white/10 flex items-center justify-center text-sm font-bold text-white/60">
                {String.fromCharCode(65 + i)}
              </span>
              <span className="text-white">{option}</span>
            </span>
          </motion.button>
        ))}
      </div>
    </div>
  )
}
