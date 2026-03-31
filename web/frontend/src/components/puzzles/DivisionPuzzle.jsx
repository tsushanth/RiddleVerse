import { useState, useEffect } from 'react'
import { motion } from 'framer-motion'
import { Check } from 'lucide-react'
import PuzzleTimer from './PuzzleTimer'
import { parseJsonField, fuzzyMatch } from './puzzleUtils'

function generatePuzzle(difficulty = 'medium') {
  const divisors = difficulty === 'easy' ? [2, 3, 4, 5] : difficulty === 'hard' ? [6, 7, 8, 9, 11, 12, 13] : [3, 4, 5, 6, 7, 8, 9]
  const divisor = divisors[Math.floor(Math.random() * divisors.length)]
  const quotient = Math.floor(Math.random() * (difficulty === 'hard' ? 50 : 20)) + 2
  const dividend = divisor * quotient

  return {
    puzzleId: `local-div-${Date.now()}`,
    puzzleType: 'division',
    question: `${dividend} ÷ ${divisor} = ?`,
    answer: String(quotient),
    hint: `Think: ${divisor} × ? = ${dividend}`,
    timer: difficulty === 'easy' ? '0:30' : difficulty === 'hard' ? '0:20' : '0:25',
    difficulty,
  }
}

export default function DivisionPuzzle({ puzzle, onAnswer }) {
  const [localPuzzle, setLocalPuzzle] = useState(null)
  const p = localPuzzle || puzzle

  const question = typeof p.question === 'string' ? p.question : JSON.stringify(p.question)
  const correctAnswer = (p.answer || '').toString()

  const [input, setInput] = useState('')
  const [submitted, setSubmitted] = useState(false)
  const [isCorrect, setIsCorrect] = useState(null)

  useEffect(() => {
    if (!puzzle.question && !puzzle.answer) {
      setLocalPuzzle(generatePuzzle(puzzle.difficulty || 'medium'))
    }
    setInput('')
    setSubmitted(false)
    setIsCorrect(null)
  }, [puzzle.puzzleId])

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
      <PuzzleTimer timerStr={p.timer || '0:25'} onTimeUp={handleTimeUp} paused={submitted} />

      <p className="text-white/50 text-xs uppercase tracking-widest font-semibold text-center">Division</p>

      <div className="bg-white/10 rounded-2xl p-6 text-center border border-white/15">
        <p className="text-white text-2xl font-bold leading-relaxed font-mono">{question}</p>
      </div>

      {!submitted ? (
        <form onSubmit={handleSubmit} className="space-y-3">
          <input
            type="text"
            value={input}
            onChange={(e) => setInput(e.target.value)}
            placeholder="Your answer..."
            className="w-full bg-white/10 border border-white/20 text-white placeholder-white/50 rounded-xl py-3 px-4 focus:outline-none focus:ring-2 focus:ring-purple-400/50 text-center text-xl font-bold"
            inputMode="numeric"
            autoFocus
          />
          <button
            type="submit"
            disabled={!input.trim()}
            className="w-full flex items-center justify-center gap-2 bg-green-500 hover:bg-green-600 disabled:bg-green-500/50 text-white font-semibold py-3 rounded-xl transition-colors"
          >
            <Check size={20} />
            Submit
          </button>
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
