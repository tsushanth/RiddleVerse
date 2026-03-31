import { useState, useEffect, useCallback } from 'react'
import { motion } from 'framer-motion'
import { Eye, EyeOff, Check } from 'lucide-react'
import PuzzleTimer from './PuzzleTimer'
import { parseJsonField } from './puzzleUtils'

function generatePuzzle(difficulty = 'medium') {
  const gridSize = difficulty === 'easy' ? 3 : difficulty === 'hard' ? 5 : 4
  const filledCount = difficulty === 'easy' ? 3 : difficulty === 'hard' ? 8 : 5

  const totalCells = gridSize * gridSize
  const pattern = new Set()
  while (pattern.size < filledCount) {
    pattern.add(Math.floor(Math.random() * totalCells))
  }

  return {
    puzzleId: `local-memsq-${Date.now()}`,
    puzzleType: 'memorysquares',
    question: JSON.stringify({ gridSize, pattern: [...pattern] }),
    answer: JSON.stringify([...pattern].sort((a, b) => a - b)),
    timer: difficulty === 'easy' ? '0:45' : difficulty === 'hard' ? '1:00' : '0:50',
    difficulty,
  }
}

export default function MemorySquaresPuzzle({ puzzle, onAnswer }) {
  // Generate locally on first render if no DB data
  const [localPuzzle, setLocalPuzzle] = useState(() => {
    const needsLocal = !puzzle.question || puzzle.question === '' || puzzle.question === '{}'
    return needsLocal ? generatePuzzle(puzzle.difficulty || 'medium') : null
  })
  const p = localPuzzle || puzzle

  const parsed = parseJsonField(p.question)
  const answerParsed = parseJsonField(p.answer)
  const gridSize = parsed?.gridSize || 4
  const correctPattern = new Set(Array.isArray(answerParsed) ? answerParsed : (parsed?.pattern || []))

  const [phase, setPhase] = useState('memorize') // 'memorize' | 'recall'
  const [userSelection, setUserSelection] = useState(new Set())
  const [submitted, setSubmitted] = useState(false)
  const [isCorrect, setIsCorrect] = useState(null)

  useEffect(() => {
    setPhase('memorize')
    setUserSelection(new Set())
    setSubmitted(false)
    setIsCorrect(null)
  }, [p.puzzleId])

  // Auto-hide after viewing period
  useEffect(() => {
    if (phase !== 'memorize') return
    const timer = setTimeout(() => setPhase('recall'), 4000)
    return () => clearTimeout(timer)
  }, [phase])

  const toggleCell = (index) => {
    if (submitted || phase === 'memorize') return
    setUserSelection(prev => {
      const next = new Set(prev)
      if (next.has(index)) next.delete(index)
      else next.add(index)
      return next
    })
  }

  const handleSubmit = () => {
    if (submitted) return
    setSubmitted(true)

    // Check if selections match pattern
    const userArr = [...userSelection].sort((a, b) => a - b)
    const correctArr = [...correctPattern].sort((a, b) => a - b)
    const correct = userArr.length === correctArr.length && userArr.every((v, i) => v === correctArr[i])

    setIsCorrect(correct)
    setTimeout(() => onAnswer(correct, `${userArr.length}/${correctArr.length} cells`), 800)
  }

  const handleTimeUp = () => {
    if (phase === 'memorize') {
      setPhase('recall')
    } else if (!submitted) {
      handleSubmit()
    }
  }

  const totalCells = gridSize * gridSize

  return (
    <div className="space-y-5">
      <PuzzleTimer
        timerStr={phase === 'memorize' ? '0:05' : (p.timer || '0:45')}
        onTimeUp={handleTimeUp}
        paused={submitted}
        key={phase}
      />

      <div className="flex items-center gap-2 justify-center">
        {phase === 'memorize' ? (
          <>
            <Eye size={18} className="text-purple-400" />
            <p className="text-purple-300 text-sm font-semibold uppercase tracking-widest">Memorize the Pattern</p>
          </>
        ) : (
          <>
            <EyeOff size={18} className="text-amber-400" />
            <p className="text-amber-300 text-sm font-semibold uppercase tracking-widest">Recreate the Pattern</p>
          </>
        )}
      </div>

      {/* Grid */}
      <div
        className="mx-auto"
        style={{
          display: 'grid',
          gridTemplateColumns: `repeat(${gridSize}, 1fr)`,
          gap: '4px',
          maxWidth: `${Math.min(gridSize * 70, 350)}px`,
        }}
      >
        {Array.from({ length: totalCells }, (_, i) => {
          const isInPattern = correctPattern.has(i)
          const isSelected = userSelection.has(i)
          const showPattern = phase === 'memorize' && isInPattern
          const showCorrectFeedback = submitted && isInPattern
          const showWrongFeedback = submitted && isSelected && !isInPattern

          return (
            <motion.button
              key={i}
              whileTap={phase === 'recall' && !submitted ? { scale: 0.9 } : {}}
              onClick={() => toggleCell(i)}
              disabled={phase === 'memorize' || submitted}
              className={`aspect-square rounded-lg border-2 transition-all ${
                showPattern
                  ? 'bg-yellow-400 border-yellow-300 shadow-lg shadow-yellow-400/50'
                  : showCorrectFeedback && isSelected
                  ? 'bg-green-500 border-green-400'
                  : showCorrectFeedback
                  ? 'bg-green-500/30 border-green-400/50'
                  : showWrongFeedback
                  ? 'bg-red-500/50 border-red-400'
                  : isSelected
                  ? 'bg-cyan-500 border-cyan-400 shadow-lg shadow-cyan-400/40'
                  : 'bg-white/10 border-white/20 hover:bg-white/20'
              }`}
            />
          )
        })}
      </div>

      <p className="text-white/40 text-xs text-center">
        {phase === 'memorize'
          ? `Remember the ${correctPattern.size} highlighted squares!`
          : `Tap ${correctPattern.size} squares to recreate the pattern`
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
            Check ({userSelection.size}/{correctPattern.size})
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
            {isCorrect ? 'Perfect match!' : 'Not quite right'}
          </p>
        </motion.div>
      )}
    </div>
  )
}
