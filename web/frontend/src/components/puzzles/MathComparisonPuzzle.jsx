import { useState, useEffect } from 'react'
import { motion } from 'framer-motion'
import PuzzleTimer from './PuzzleTimer'
import { parseJsonField } from './puzzleUtils'

function generateExpression(difficulty = 'medium') {
  const ops = ['+', '-', '*']
  const maxNum = difficulty === 'easy' ? 20 : difficulty === 'hard' ? 100 : 50

  const makeExpr = () => {
    const a = Math.floor(Math.random() * maxNum) + 1
    const b = Math.floor(Math.random() * maxNum) + 1
    const op = ops[Math.floor(Math.random() * ops.length)]
    let val
    switch (op) {
      case '+': val = a + b; break
      case '-': val = a - b; break
      case '*': val = a * b; break
      default: val = a + b
    }
    return { text: `${a} ${op} ${b}`, value: val }
  }

  return { left: makeExpr(), right: makeExpr() }
}

function generatePuzzle(difficulty = 'medium') {
  const { left, right } = generateExpression(difficulty)
  const correctAnswer = left.value > right.value ? '>' : left.value < right.value ? '<' : '='

  return {
    puzzleId: `local-mathcomp-${Date.now()}`,
    puzzleType: 'mathcomparison',
    question: JSON.stringify({ left: left.text, right: right.text, leftValue: left.value, rightValue: right.value }),
    answer: correctAnswer,
    hint: null,
    timer: difficulty === 'easy' ? '0:30' : difficulty === 'hard' ? '0:15' : '0:20',
    difficulty,
  }
}

export default function MathComparisonPuzzle({ puzzle, onAnswer }) {
  const [localPuzzle, setLocalPuzzle] = useState(null)
  const p = localPuzzle || puzzle

  const parsed = parseJsonField(p.question)
  const leftExpr = parsed?.left || parsed?.expression1 || ''
  const rightExpr = parsed?.right || parsed?.expression2 || ''
  const correctAnswer = (p.answer || '').toString().trim()

  const [selected, setSelected] = useState(null)
  const [submitted, setSubmitted] = useState(false)
  const [isCorrect, setIsCorrect] = useState(null)

  useEffect(() => {
    if (!puzzle.question && !puzzle.answer) {
      setLocalPuzzle(generatePuzzle(puzzle.difficulty || 'medium'))
    }
    setSelected(null)
    setSubmitted(false)
    setIsCorrect(null)
  }, [puzzle.puzzleId])

  const handleSelect = (symbol) => {
    if (submitted) return
    setSelected(symbol)
    setSubmitted(true)
    const correct = symbol === correctAnswer
    setIsCorrect(correct)
    setTimeout(() => onAnswer(correct, symbol), 800)
  }

  const handleTimeUp = () => {
    if (!submitted) {
      setSubmitted(true)
      setIsCorrect(false)
      onAnswer(false, 'timeout')
    }
  }

  const symbols = ['<', '=', '>']

  return (
    <div className="space-y-5">
      <PuzzleTimer timerStr={p.timer || '0:20'} onTimeUp={handleTimeUp} paused={submitted} />

      <p className="text-white/50 text-xs uppercase tracking-widest font-semibold text-center">
        Compare the Expressions
      </p>

      {/* Expression display */}
      <div className="flex items-center justify-center gap-3">
        <motion.div
          initial={{ opacity: 0, x: -20 }}
          animate={{ opacity: 1, x: 0 }}
          className="flex-1 bg-white/10 rounded-xl p-4 text-center border border-white/15"
        >
          <p className="text-white text-xl font-bold font-mono">{leftExpr}</p>
        </motion.div>

        <div className="w-12 h-12 rounded-full bg-purple-500/30 border border-purple-400/50 flex items-center justify-center">
          <span className="text-purple-300 text-2xl font-bold">?</span>
        </div>

        <motion.div
          initial={{ opacity: 0, x: 20 }}
          animate={{ opacity: 1, x: 0 }}
          className="flex-1 bg-white/10 rounded-xl p-4 text-center border border-white/15"
        >
          <p className="text-white text-xl font-bold font-mono">{rightExpr}</p>
        </motion.div>
      </div>

      {/* Comparison buttons */}
      <div className="grid grid-cols-3 gap-3">
        {symbols.map(sym => {
          const isSelected = selected === sym
          const isCorrectOption = sym === correctAnswer
          return (
            <motion.button
              key={sym}
              whileTap={!submitted ? { scale: 0.9 } : {}}
              onClick={() => handleSelect(sym)}
              disabled={submitted}
              className={`py-4 rounded-xl border-2 font-bold text-3xl transition-all ${
                submitted && isCorrectOption
                  ? 'bg-green-500/30 border-green-400 text-green-300'
                  : submitted && isSelected && !isCorrectOption
                  ? 'bg-red-500/30 border-red-400 text-red-300'
                  : submitted
                  ? 'bg-white/5 border-white/10 text-white/30'
                  : 'bg-white/10 border-white/20 text-white hover:bg-white/20 hover:border-purple-400/50'
              }`}
            >
              {sym}
            </motion.button>
          )
        })}
      </div>

      {submitted && (
        <motion.div
          initial={{ opacity: 0, scale: 0.9 }}
          animate={{ opacity: 1, scale: 1 }}
          className={`text-center p-3 rounded-xl ${
            isCorrect ? 'bg-green-500/20 text-green-300' : 'bg-red-500/20 text-red-300'
          }`}
        >
          <p className="font-bold">{isCorrect ? 'Correct!' : `Answer: ${leftExpr} ${correctAnswer} ${rightExpr}`}</p>
        </motion.div>
      )}
    </div>
  )
}
