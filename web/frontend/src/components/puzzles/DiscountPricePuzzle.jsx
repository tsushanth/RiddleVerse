import { useState, useEffect } from 'react'
import { motion } from 'framer-motion'
import { Check, Tag } from 'lucide-react'
import PuzzleTimer from './PuzzleTimer'
import { parseJsonField, fuzzyMatch } from './puzzleUtils'

function generatePuzzle(difficulty = 'medium') {
  const prices = difficulty === 'easy'
    ? [10, 20, 25, 30, 40, 50]
    : difficulty === 'hard'
    ? [35, 47, 63, 78, 89, 95, 120, 150, 199]
    : [15, 25, 35, 45, 55, 65, 75, 80, 100]

  const discounts = difficulty === 'easy'
    ? [10, 20, 25, 50]
    : difficulty === 'hard'
    ? [5, 12, 15, 18, 22, 30, 35, 40, 45]
    : [10, 15, 20, 25, 30, 40]

  const price = prices[Math.floor(Math.random() * prices.length)]
  const discount = discounts[Math.floor(Math.random() * discounts.length)]
  const savings = price * (discount / 100)
  const finalPrice = price - savings

  return {
    puzzleId: `local-discount-${Date.now()}`,
    puzzleType: 'discountprice',
    question: JSON.stringify({ originalPrice: price, discountPercent: discount }),
    answer: String(Number.isInteger(finalPrice) ? finalPrice : finalPrice.toFixed(2)),
    hint: `Discount amount: $${Number.isInteger(savings) ? savings : savings.toFixed(2)}`,
    timer: difficulty === 'easy' ? '0:30' : difficulty === 'hard' ? '0:20' : '0:25',
    difficulty,
  }
}

export default function DiscountPricePuzzle({ puzzle, onAnswer }) {
  const [localPuzzle, setLocalPuzzle] = useState(null)
  const p = localPuzzle || puzzle

  const parsed = parseJsonField(p.question)
  const originalPrice = parsed?.originalPrice || parsed?.price || 0
  const discountPercent = parsed?.discountPercent || parsed?.discount || 0
  const questionText = typeof p.question === 'string' && !parsed?.originalPrice
    ? p.question
    : `Original price: $${originalPrice}, Discount: ${discountPercent}% off. What's the final price?`
  const correctAnswer = (p.answer || '').toString()

  const [input, setInput] = useState('')
  const [submitted, setSubmitted] = useState(false)
  const [isCorrect, setIsCorrect] = useState(null)
  const [showHint, setShowHint] = useState(false)

  useEffect(() => {
    if (!puzzle.question && !puzzle.answer) {
      setLocalPuzzle(generatePuzzle(puzzle.difficulty || 'medium'))
    }
    setInput('')
    setSubmitted(false)
    setIsCorrect(null)
    setShowHint(false)
  }, [puzzle.puzzleId])

  const handleSubmit = (e) => {
    e.preventDefault()
    if (!input.trim() || submitted) return
    setSubmitted(true)
    // Allow answers with or without $
    const userAns = input.trim().replace(/^\$/, '')
    const correct = fuzzyMatch(userAns, correctAnswer.replace(/^\$/, ''))
    setIsCorrect(correct)
    setTimeout(() => onAnswer(correct, userAns), 800)
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

      <div className="flex items-center gap-2 justify-center">
        <Tag size={18} className="text-green-400" />
        <p className="text-white/50 text-xs uppercase tracking-widest font-semibold">Discount Price</p>
      </div>

      <div className="bg-white/10 rounded-2xl p-5 text-center border border-white/15 space-y-3">
        {originalPrice ? (
          <>
            <div className="flex items-center justify-center gap-3">
              <span className="text-white/40 line-through text-lg">${originalPrice}</span>
              <span className="bg-red-500/30 text-red-300 px-2 py-0.5 rounded-full text-sm font-bold">
                -{discountPercent}%
              </span>
            </div>
            <p className="text-white text-lg font-medium">What is the final price?</p>
          </>
        ) : (
          <p className="text-white text-lg font-medium">{questionText}</p>
        )}
      </div>

      {showHint && p.hint && (
        <motion.div
          initial={{ opacity: 0 }}
          animate={{ opacity: 1 }}
          className="bg-yellow-500/20 rounded-xl p-3 border border-yellow-500/30 text-center"
        >
          <p className="text-yellow-100 text-sm">{p.hint}</p>
        </motion.div>
      )}

      {!submitted ? (
        <form onSubmit={handleSubmit} className="space-y-3">
          <div className="relative">
            <span className="absolute left-4 top-1/2 -translate-y-1/2 text-white/50 text-xl">$</span>
            <input
              type="text"
              value={input}
              onChange={(e) => setInput(e.target.value)}
              placeholder="0.00"
              className="w-full bg-white/10 border border-white/20 text-white placeholder-white/50 rounded-xl py-3 pl-10 pr-4 focus:outline-none focus:ring-2 focus:ring-purple-400/50 text-center text-xl font-bold"
              inputMode="decimal"
              autoFocus
            />
          </div>
          <div className="flex gap-3">
            <button
              type="submit"
              disabled={!input.trim()}
              className="flex-1 flex items-center justify-center gap-2 bg-green-500 hover:bg-green-600 disabled:bg-green-500/50 text-white font-semibold py-3 rounded-xl transition-colors"
            >
              <Check size={20} />
              Submit
            </button>
            {p.hint && !showHint && (
              <button
                type="button"
                onClick={() => setShowHint(true)}
                className="bg-yellow-500/20 hover:bg-yellow-500/30 text-yellow-400 px-4 py-3 rounded-xl transition-colors text-sm"
              >
                Hint
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
          <p className="font-bold text-lg">{isCorrect ? 'Correct!' : `Answer: $${correctAnswer}`}</p>
        </motion.div>
      )}
    </div>
  )
}
