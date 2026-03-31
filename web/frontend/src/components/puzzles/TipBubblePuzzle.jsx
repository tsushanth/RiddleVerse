import { useState, useEffect } from 'react'
import { motion } from 'framer-motion'
import { Check, Receipt } from 'lucide-react'
import PuzzleTimer from './PuzzleTimer'
import { parseJsonField, fuzzyMatch } from './puzzleUtils'

function generatePuzzle(difficulty = 'medium') {
  const bills = difficulty === 'easy'
    ? [20, 25, 30, 40, 50]
    : difficulty === 'hard'
    ? [32, 47, 58, 63, 76, 84, 91, 112]
    : [25, 35, 42, 55, 60, 75, 80]

  const tipPcts = difficulty === 'easy'
    ? [10, 15, 20]
    : difficulty === 'hard'
    ? [12, 15, 18, 20, 22, 25]
    : [10, 15, 18, 20]

  const bill = bills[Math.floor(Math.random() * bills.length)]
  const tipPct = tipPcts[Math.floor(Math.random() * tipPcts.length)]
  const tip = bill * (tipPct / 100)
  const total = bill + tip

  return {
    puzzleId: `local-tip-${Date.now()}`,
    puzzleType: 'tipbubble',
    question: JSON.stringify({ billAmount: bill, tipPercent: tipPct }),
    answer: String(Number.isInteger(total) ? total : total.toFixed(2)),
    hint: `Tip amount: $${Number.isInteger(tip) ? tip : tip.toFixed(2)}`,
    timer: difficulty === 'easy' ? '0:30' : difficulty === 'hard' ? '0:20' : '0:25',
    difficulty,
  }
}

export default function TipBubblePuzzle({ puzzle, onAnswer }) {
  const [localPuzzle, setLocalPuzzle] = useState(null)
  const p = localPuzzle || puzzle

  const parsed = parseJsonField(p.question)
  const billAmount = parsed?.billAmount || parsed?.bill || 0
  const tipPercent = parsed?.tipPercent || parsed?.tip || 0
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
        <Receipt size={18} className="text-amber-400" />
        <p className="text-white/50 text-xs uppercase tracking-widest font-semibold">Tip Calculator</p>
      </div>

      <div className="bg-white/10 rounded-2xl p-5 text-center border border-white/15 space-y-4">
        <div className="space-y-2">
          <div className="flex items-center justify-between px-4">
            <span className="text-white/60">Bill</span>
            <span className="text-white font-bold text-xl">${billAmount}</span>
          </div>
          <div className="border-t border-white/10" />
          <div className="flex items-center justify-between px-4">
            <span className="text-white/60">Tip</span>
            <span className="text-amber-400 font-bold text-xl">{tipPercent}%</span>
          </div>
          <div className="border-t border-white/10" />
          <div className="flex items-center justify-between px-4">
            <span className="text-white/60">Total</span>
            <span className="text-purple-400 font-bold text-xl">?</span>
          </div>
        </div>
      </div>

      {!submitted ? (
        <form onSubmit={handleSubmit} className="space-y-3">
          <div className="relative">
            <span className="absolute left-4 top-1/2 -translate-y-1/2 text-white/50 text-xl">$</span>
            <input
              type="text"
              value={input}
              onChange={(e) => setInput(e.target.value)}
              placeholder="Total with tip..."
              className="w-full bg-white/10 border border-white/20 text-white placeholder-white/50 rounded-xl py-3 pl-10 pr-4 focus:outline-none focus:ring-2 focus:ring-purple-400/50 text-center text-xl font-bold"
              inputMode="decimal"
              autoFocus
            />
          </div>
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
          <p className="font-bold text-lg">{isCorrect ? 'Correct!' : `Answer: $${correctAnswer}`}</p>
        </motion.div>
      )}
    </div>
  )
}
