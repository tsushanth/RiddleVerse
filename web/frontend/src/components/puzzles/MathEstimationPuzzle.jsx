import { useState, useEffect } from 'react'
import { motion } from 'framer-motion'
import PuzzleTimer from './PuzzleTimer'
import { parseJsonField, shuffle } from './puzzleUtils'

function generatePuzzle(difficulty = 'medium') {
  const ops = ['+', '-', '*']
  const max = difficulty === 'easy' ? 50 : difficulty === 'hard' ? 500 : 200

  // Build a multi-step expression
  const parts = difficulty === 'easy' ? 2 : difficulty === 'hard' ? 4 : 3
  let expr = ''
  let val = 0

  for (let i = 0; i < parts; i++) {
    const num = Math.floor(Math.random() * max) + 1
    if (i === 0) {
      expr = `${num}`
      val = num
    } else {
      const op = ops[Math.floor(Math.random() * ops.length)]
      expr += ` ${op} ${num}`
      switch (op) {
        case '+': val += num; break
        case '-': val -= num; break
        case '*': val *= num; break
      }
    }
  }

  // Generate wrong options as nearby values
  const spread = Math.max(Math.abs(val) * 0.3, 10)
  const wrongOptions = new Set()
  while (wrongOptions.size < 3) {
    const offset = Math.floor(Math.random() * spread * 2 - spread)
    const wrong = val + (offset === 0 ? Math.floor(spread / 2) : offset)
    if (wrong !== val) wrongOptions.add(wrong)
  }

  const options = shuffle([val, ...wrongOptions].map(String))

  return {
    puzzleId: `local-estimate-${Date.now()}`,
    puzzleType: 'mathestimation',
    question: expr,
    answer: String(val),
    options,
    hint: 'Estimate the result — no calculator needed!',
    timer: difficulty === 'easy' ? '0:30' : difficulty === 'hard' ? '0:20' : '0:25',
    difficulty,
  }
}

export default function MathEstimationPuzzle({ puzzle, onAnswer }) {
  const [localPuzzle, setLocalPuzzle] = useState(null)
  const p = localPuzzle || puzzle

  const question = typeof p.question === 'string' ? p.question : JSON.stringify(p.question)
  const correctAnswer = (p.answer || '').toString().trim()
  const options = p.options || []

  const [selected, setSelected] = useState(null)
  const [revealed, setRevealed] = useState(false)

  useEffect(() => {
    if (!puzzle.question && !puzzle.answer) {
      setLocalPuzzle(generatePuzzle(puzzle.difficulty || 'medium'))
    }
    setSelected(null)
    setRevealed(false)
  }, [puzzle.puzzleId])

  const handleSelect = (opt) => {
    if (revealed) return
    setSelected(opt)
    setRevealed(true)
    const isCorrect = opt.trim() === correctAnswer
    setTimeout(() => onAnswer(isCorrect, opt), 1000)
  }

  const handleTimeUp = () => {
    if (!revealed) {
      setRevealed(true)
      onAnswer(false, '')
    }
  }

  return (
    <div className="space-y-5">
      <PuzzleTimer timerStr={p.timer || '0:25'} onTimeUp={handleTimeUp} paused={revealed} />

      <p className="text-white/50 text-xs uppercase tracking-widest font-semibold text-center">
        Estimate the Result
      </p>

      <div className="bg-white/10 rounded-2xl p-6 text-center border border-white/15">
        <p className="text-white text-2xl font-bold leading-relaxed font-mono">{question}</p>
      </div>

      <p className="text-white/40 text-xs text-center">Pick the closest answer</p>

      <div className="grid grid-cols-2 gap-3">
        {options.map((opt, i) => {
          const isCorrectOpt = opt.trim() === correctAnswer
          const isSel = opt === selected
          return (
            <motion.button
              key={i}
              whileTap={!revealed ? { scale: 0.95 } : {}}
              onClick={() => handleSelect(opt)}
              disabled={revealed}
              className={`py-4 rounded-xl border-2 font-bold text-xl transition-all ${
                revealed && isCorrectOpt
                  ? 'bg-green-500/30 border-green-400 text-green-300'
                  : revealed && isSel && !isCorrectOpt
                  ? 'bg-red-500/30 border-red-400 text-red-300'
                  : revealed
                  ? 'bg-white/5 border-white/10 text-white/30'
                  : 'bg-white/10 border-white/20 text-white hover:bg-white/20'
              }`}
            >
              {opt}
            </motion.button>
          )
        })}
      </div>
    </div>
  )
}
