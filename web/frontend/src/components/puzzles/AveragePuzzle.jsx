import { useState, useEffect } from 'react'
import { motion } from 'framer-motion'
import { Check } from 'lucide-react'
import PuzzleTimer from './PuzzleTimer'
import { parseJsonField, fuzzyMatch } from './puzzleUtils'

function generatePuzzle(difficulty = 'medium') {
  const count = difficulty === 'easy' ? 3 : difficulty === 'hard' ? 6 : 4
  const max = difficulty === 'easy' ? 20 : difficulty === 'hard' ? 100 : 50

  const numbers = []
  for (let i = 0; i < count; i++) {
    numbers.push(Math.floor(Math.random() * max) + 1)
  }

  const sum = numbers.reduce((a, b) => a + b, 0)
  const avg = sum / count

  return {
    puzzleId: `local-avg-${Date.now()}`,
    puzzleType: 'average',
    question: `Find the average of [${numbers.join(', ')}]`,
    answer: String(Number.isInteger(avg) ? avg : avg.toFixed(1)),
    hint: `Sum all numbers and divide by ${count}`,
    timer: difficulty === 'easy' ? '0:30' : difficulty === 'hard' ? '0:25' : '0:30',
    difficulty,
    options: null,
  }
}

export default function AveragePuzzle({ puzzle, onAnswer }) {
  const [localPuzzle, setLocalPuzzle] = useState(null)
  const p = localPuzzle || puzzle

  const question = typeof p.question === 'string' ? p.question : JSON.stringify(p.question)
  const correctAnswer = (p.answer || '').toString()

  // Try to extract numbers from question
  const numbersMatch = question.match(/\[([^\]]+)\]/)
  const displayNumbers = numbersMatch
    ? numbersMatch[1].split(',').map(s => s.trim())
    : []

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
      <PuzzleTimer timerStr={p.timer || '0:30'} onTimeUp={handleTimeUp} paused={submitted} />

      <p className="text-white/50 text-xs uppercase tracking-widest font-semibold text-center">
        Find the Average
      </p>

      {displayNumbers.length > 0 ? (
        <div className="flex flex-wrap items-center justify-center gap-2">
          {displayNumbers.map((num, i) => (
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
              {i < displayNumbers.length - 1 && (
                <span className="text-white/30 text-lg">+</span>
              )}
            </motion.div>
          ))}
          <div className="w-full text-center mt-2">
            <span className="text-white/50 text-sm">÷ {displayNumbers.length} = ?</span>
          </div>
        </div>
      ) : (
        <div className="bg-white/10 rounded-2xl p-6 text-center border border-white/15">
          <p className="text-white text-xl font-bold">{question}</p>
        </div>
      )}

      {!submitted ? (
        <form onSubmit={handleSubmit} className="space-y-3">
          <input
            type="text"
            value={input}
            onChange={(e) => setInput(e.target.value)}
            placeholder="Enter the average..."
            className="w-full bg-white/10 border border-white/20 text-white placeholder-white/50 rounded-xl py-3 px-4 focus:outline-none focus:ring-2 focus:ring-purple-400/50 text-center text-xl font-bold"
            inputMode="decimal"
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
