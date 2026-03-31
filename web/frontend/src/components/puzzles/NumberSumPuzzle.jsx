import { useState, useEffect } from 'react'
import { motion } from 'framer-motion'
import { Check, RotateCcw } from 'lucide-react'
import PuzzleTimer from './PuzzleTimer'
import { parseJsonField } from './puzzleUtils'

function generatePuzzle(difficulty = 'medium') {
  const diff = { easy: { count: 5, max: 15, pick: 2 }, medium: { count: 7, max: 30, pick: 3 }, hard: { count: 9, max: 50, pick: 4 } }
  const cfg = diff[difficulty] || diff.medium

  // Generate random numbers, pick some that will form the target
  const picked = []
  for (let i = 0; i < cfg.pick; i++) {
    picked.push(Math.floor(Math.random() * cfg.max) + 1)
  }
  const target = picked.reduce((a, b) => a + b, 0)

  // Fill the rest with random numbers that don't form the target with any subset of picked
  const numbers = [...picked]
  while (numbers.length < cfg.count) {
    const n = Math.floor(Math.random() * cfg.max) + 1
    if (!numbers.includes(n)) numbers.push(n)
  }

  // Shuffle
  for (let i = numbers.length - 1; i > 0; i--) {
    const j = Math.floor(Math.random() * (i + 1))
    ;[numbers[i], numbers[j]] = [numbers[j], numbers[i]]
  }

  return {
    puzzleId: `local-numsum-${Date.now()}`,
    puzzleType: 'numbersum',
    question: JSON.stringify({ numbers, target }),
    answer: JSON.stringify(picked.sort((a, b) => a - b)),
    hint: `Find ${cfg.pick} numbers that add up to ${target}`,
    timer: difficulty === 'easy' ? '1:00' : difficulty === 'hard' ? '0:45' : '0:50',
    difficulty,
  }
}

export default function NumberSumPuzzle({ puzzle, onAnswer }) {
  const [localPuzzle, setLocalPuzzle] = useState(null)
  const p = localPuzzle || puzzle

  const parsed = parseJsonField(p.question)
  const answerParsed = parseJsonField(p.answer)

  const numbers = parsed?.numbers || []
  const target = parsed?.target || 0
  const correctNumbers = Array.isArray(answerParsed) ? answerParsed : (answerParsed?.numbers || [])

  const [selected, setSelected] = useState(new Set())
  const [submitted, setSubmitted] = useState(false)
  const [isCorrect, setIsCorrect] = useState(null)

  useEffect(() => {
    if (!puzzle.question && !puzzle.answer) {
      setLocalPuzzle(generatePuzzle(puzzle.difficulty || 'medium'))
    }
    setSelected(new Set())
    setSubmitted(false)
    setIsCorrect(null)
  }, [puzzle.puzzleId])

  const toggleNumber = (index) => {
    if (submitted) return
    setSelected(prev => {
      const next = new Set(prev)
      if (next.has(index)) next.delete(index)
      else next.add(index)
      return next
    })
  }

  const currentSum = [...selected].reduce((sum, i) => sum + numbers[i], 0)

  const handleSubmit = () => {
    if (submitted) return
    setSubmitted(true)
    const correct = currentSum === target
    setIsCorrect(correct)
    const selectedNums = [...selected].map(i => numbers[i]).sort((a, b) => a - b)
    setTimeout(() => onAnswer(correct, selectedNums.join(', ')), 800)
  }

  const handleTimeUp = () => {
    if (!submitted) {
      setSubmitted(true)
      setIsCorrect(false)
      onAnswer(false, 'timeout')
    }
  }

  return (
    <div className="space-y-5">
      <PuzzleTimer timerStr={p.timer || '1:00'} onTimeUp={handleTimeUp} paused={submitted} />

      <div className="text-center space-y-1">
        <p className="text-white/50 text-xs uppercase tracking-widest font-semibold">Number Sum</p>
        <p className="text-white text-lg font-bold">Find numbers that sum to <span className="text-purple-400">{target}</span></p>
      </div>

      {/* Current sum display */}
      <div className="flex items-center justify-center gap-2">
        <span className="text-white/60 text-sm">Current sum:</span>
        <span className={`text-xl font-bold ${
          currentSum === target ? 'text-green-400' : currentSum > target ? 'text-red-400' : 'text-white'
        }`}>
          {currentSum}
        </span>
        <span className="text-white/40 text-sm">/ {target}</span>
      </div>

      {/* Number grid */}
      <div className="grid grid-cols-3 sm:grid-cols-4 gap-3 justify-center">
        {numbers.map((num, i) => {
          const isSelected = selected.has(i)
          return (
            <motion.button
              key={i}
              whileTap={!submitted ? { scale: 0.9 } : {}}
              onClick={() => toggleNumber(i)}
              disabled={submitted}
              className={`h-14 rounded-xl border-2 font-bold text-xl transition-all ${
                submitted && isSelected && isCorrect
                  ? 'bg-green-500/30 border-green-400 text-green-300'
                  : submitted && isSelected && !isCorrect
                  ? 'bg-red-500/20 border-red-400 text-red-300'
                  : isSelected
                  ? 'bg-purple-500/40 border-purple-400 text-white scale-105'
                  : 'bg-white/10 border-white/20 text-white hover:bg-white/20'
              }`}
            >
              {num}
            </motion.button>
          )
        })}
      </div>

      {p.hint && (
        <p className="text-yellow-300/60 text-xs text-center italic">{p.hint}</p>
      )}

      {!submitted && (
        <div className="flex gap-3">
          <button
            onClick={() => setSelected(new Set())}
            className="flex items-center gap-2 bg-white/10 hover:bg-white/20 text-white/70 px-4 py-2.5 rounded-xl transition-colors text-sm"
          >
            <RotateCcw size={16} />
            Clear
          </button>
          <button
            onClick={handleSubmit}
            disabled={selected.size === 0}
            className="flex-1 flex items-center justify-center gap-2 bg-green-500 hover:bg-green-600 disabled:bg-green-500/50 text-white font-semibold py-2.5 rounded-xl transition-colors text-sm"
          >
            <Check size={18} />
            Check Sum
          </button>
        </div>
      )}

      {submitted && (
        <motion.div
          initial={{ opacity: 0, scale: 0.9 }}
          animate={{ opacity: 1, scale: 1 }}
          className={`text-center p-4 rounded-xl ${
            isCorrect ? 'bg-green-500/20 text-green-300' : 'bg-red-500/20 text-red-300'
          }`}
        >
          <p className="font-bold text-lg">
            {isCorrect ? 'Correct!' : `Target was ${target}. One solution: ${correctNumbers.join(' + ')}`}
          </p>
        </motion.div>
      )}
    </div>
  )
}
