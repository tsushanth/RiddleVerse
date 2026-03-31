import { useState, useEffect } from 'react'
import { motion } from 'framer-motion'
import { Eye, EyeOff, Check } from 'lucide-react'
import PuzzleTimer from './PuzzleTimer'
import { parseJsonField } from './puzzleUtils'

function generateTrianglePositions(rows) {
  // Generate positions for dots in a triangle formation
  const positions = []
  let id = 0
  for (let row = 0; row < rows; row++) {
    const dotsInRow = row + 1
    const offsetX = (rows - 1 - row) * 25 // center the row
    for (let col = 0; col < dotsInRow; col++) {
      positions.push({
        id: id++,
        row,
        col,
        x: offsetX + col * 50,
        y: row * 45,
      })
    }
  }
  return positions
}

function generatePuzzle(difficulty = 'medium') {
  const rows = difficulty === 'easy' ? 3 : difficulty === 'hard' ? 5 : 4
  const positions = generateTrianglePositions(rows)
  const filledCount = difficulty === 'easy' ? 3 : difficulty === 'hard' ? 6 : 4

  const filled = new Set()
  while (filled.size < Math.min(filledCount, positions.length)) {
    filled.add(Math.floor(Math.random() * positions.length))
  }

  return {
    puzzleId: `local-tridot-${Date.now()}`,
    puzzleType: 'triangledotmemory',
    question: JSON.stringify({ rows, positions, filled: [...filled] }),
    answer: JSON.stringify([...filled].sort((a, b) => a - b)),
    timer: difficulty === 'easy' ? '0:45' : difficulty === 'hard' ? '1:00' : '0:50',
    difficulty,
  }
}

export default function TriangleDotMemoryPuzzle({ puzzle, onAnswer }) {
  const [localPuzzle, setLocalPuzzle] = useState(null)
  const p = localPuzzle || puzzle

  const parsed = parseJsonField(p.question)
  const answerParsed = parseJsonField(p.answer)
  const rows = parsed?.rows || 4
  const positions = parsed?.positions || generateTrianglePositions(rows)
  const correctFilled = new Set(Array.isArray(answerParsed) ? answerParsed : (parsed?.filled || []))

  const [phase, setPhase] = useState('memorize')
  const [userSelection, setUserSelection] = useState(new Set())
  const [submitted, setSubmitted] = useState(false)
  const [isCorrect, setIsCorrect] = useState(null)

  useEffect(() => {
    if (!puzzle.question && !puzzle.answer) {
      setLocalPuzzle(generatePuzzle(puzzle.difficulty || 'medium'))
    }
    setPhase('memorize')
    setUserSelection(new Set())
    setSubmitted(false)
    setIsCorrect(null)
  }, [puzzle.puzzleId])

  useEffect(() => {
    if (phase !== 'memorize') return
    const timer = setTimeout(() => setPhase('recall'), 3000)
    return () => clearTimeout(timer)
  }, [phase])

  const toggleDot = (id) => {
    if (submitted || phase === 'memorize') return
    setUserSelection(prev => {
      const next = new Set(prev)
      if (next.has(id)) next.delete(id)
      else next.add(id)
      return next
    })
  }

  const handleSubmit = () => {
    if (submitted) return
    setSubmitted(true)
    const userArr = [...userSelection].sort((a, b) => a - b)
    const correctArr = [...correctFilled].sort((a, b) => a - b)
    const correct = userArr.length === correctArr.length && userArr.every((v, i) => v === correctArr[i])
    setIsCorrect(correct)
    setTimeout(() => onAnswer(correct, `${userArr.length}/${correctArr.length}`), 800)
  }

  const handleTimeUp = () => {
    if (phase === 'memorize') {
      setPhase('recall')
    } else if (!submitted) {
      handleSubmit()
    }
  }

  // Compute SVG dimensions
  const maxX = Math.max(...positions.map(p => p.x)) + 50
  const maxY = Math.max(...positions.map(p => p.y)) + 50

  return (
    <div className="space-y-5">
      <PuzzleTimer
        timerStr={phase === 'memorize' ? '0:05' : (p.timer || '0:50')}
        onTimeUp={handleTimeUp}
        paused={submitted}
        key={phase}
      />

      <div className="flex items-center gap-2 justify-center">
        {phase === 'memorize' ? (
          <>
            <Eye size={18} className="text-purple-400" />
            <p className="text-purple-300 text-sm font-semibold uppercase tracking-widest">Memorize the Dots</p>
          </>
        ) : (
          <>
            <EyeOff size={18} className="text-amber-400" />
            <p className="text-amber-300 text-sm font-semibold uppercase tracking-widest">Recreate the Pattern</p>
          </>
        )}
      </div>

      {/* Triangle grid */}
      <div className="flex justify-center">
        <svg viewBox={`-10 -10 ${maxX + 20} ${maxY + 20}`} className="max-w-[300px] w-full">
          {positions.map(pos => {
            const isFilled = correctFilled.has(pos.id)
            const isSelected = userSelection.has(pos.id)
            const showPattern = phase === 'memorize' && isFilled
            const showCorrect = submitted && isFilled && isSelected
            const showMissed = submitted && isFilled && !isSelected
            const showWrong = submitted && !isFilled && isSelected

            let fill
            if (showPattern) fill = '#a855f7'
            else if (showCorrect) fill = '#22c55e'
            else if (showMissed) fill = 'rgba(34,197,94,0.3)'
            else if (showWrong) fill = '#ef4444'
            else if (isSelected) fill = 'rgba(168,85,247,0.6)'
            else fill = 'rgba(255,255,255,0.15)'

            return (
              <g key={pos.id} onClick={() => toggleDot(pos.id)} style={{ cursor: phase === 'recall' && !submitted ? 'pointer' : 'default' }}>
                <circle
                  cx={pos.x + 25}
                  cy={pos.y + 25}
                  r={18}
                  fill={fill}
                  stroke={showPattern || isSelected ? 'rgba(255,255,255,0.4)' : 'rgba(255,255,255,0.15)'}
                  strokeWidth={2}
                />
              </g>
            )
          })}
        </svg>
      </div>

      <p className="text-white/40 text-xs text-center">
        {phase === 'memorize'
          ? `Remember ${correctFilled.size} highlighted dots!`
          : `Tap ${correctFilled.size} dots to recreate the pattern`
        }
      </p>

      {phase === 'recall' && !submitted && (
        <div className="flex gap-3">
          <button
            onClick={() => setUserSelection(new Set())}
            className="flex items-center gap-2 bg-white/10 hover:bg-white/20 text-white/70 px-4 py-2.5 rounded-xl transition-colors text-sm"
          >
            Clear
          </button>
          <button
            onClick={handleSubmit}
            disabled={userSelection.size === 0}
            className="flex-1 flex items-center justify-center gap-2 bg-green-500 hover:bg-green-600 disabled:bg-green-500/50 text-white font-semibold py-2.5 rounded-xl transition-colors text-sm"
          >
            <Check size={18} />
            Check ({userSelection.size}/{correctFilled.size})
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
          <p className="font-bold text-lg">{isCorrect ? 'Perfect match!' : 'Not quite right'}</p>
        </motion.div>
      )}
    </div>
  )
}
