import { useState, useEffect } from 'react'
import { motion } from 'framer-motion'
import PuzzleTimer from './PuzzleTimer'
import { parseJsonField, shuffle } from './puzzleUtils'

function generateGrid(size) {
  const grid = []
  for (let r = 0; r < size; r++) {
    const row = []
    for (let c = 0; c < Math.ceil(size / 2); c++) {
      row.push(Math.random() > 0.5)
    }
    grid.push(row)
  }
  return grid
}

function mirrorGrid(halfGrid, size) {
  return halfGrid.map(row => {
    const full = [...row]
    for (let c = Math.ceil(size / 2) - 1 - (size % 2 === 0 ? 0 : 1); c >= 0; c--) {
      full.push(row[c])
    }
    return full
  })
}

function corruptGrid(grid, corruptions) {
  const result = grid.map(r => [...r])
  let flipped = 0
  while (flipped < corruptions) {
    const r = Math.floor(Math.random() * result.length)
    const c = Math.floor(Math.random() * result[0].length)
    result[r][c] = !result[r][c]
    flipped++
  }
  return result
}

function generatePuzzle(difficulty = 'medium') {
  const size = difficulty === 'easy' ? 4 : difficulty === 'hard' ? 6 : 5
  const half = generateGrid(size)
  const correctMirror = mirrorGrid(half, size)

  // Generate 3 wrong options and 1 correct
  const options = [correctMirror]
  for (let i = 0; i < 3; i++) {
    options.push(corruptGrid(correctMirror, difficulty === 'easy' ? 2 : difficulty === 'hard' ? 1 : 1))
  }

  const shuffledOptions = shuffle(options.map((grid, i) => ({ grid, id: i })))
  const correctId = shuffledOptions.find(o => o.id === 0)?.id

  return {
    puzzleId: `local-sym-${Date.now()}`,
    puzzleType: 'symmetry',
    question: JSON.stringify({ size, half, options: shuffledOptions }),
    answer: '0', // The option with id 0 is always correct
    timer: difficulty === 'easy' ? '0:30' : difficulty === 'hard' ? '0:20' : '0:25',
    difficulty,
  }
}

function GridDisplay({ grid, size, cellSize = 20, highlight = false, wrong = false }) {
  return (
    <div
      className={`inline-grid gap-[2px] rounded-lg p-1.5 border-2 transition-all ${
        highlight ? 'border-green-400 bg-green-500/10' : wrong ? 'border-red-400 bg-red-500/10' : 'border-white/20 bg-white/5'
      }`}
      style={{ gridTemplateColumns: `repeat(${grid[0]?.length || size}, ${cellSize}px)` }}
    >
      {grid.flatMap((row, r) =>
        row.map((filled, c) => (
          <div
            key={`${r}-${c}`}
            style={{ width: cellSize, height: cellSize }}
            className={`rounded-sm ${filled ? 'bg-yellow-400' : 'bg-white/10'}`}
          />
        ))
      )}
    </div>
  )
}

export default function SymmetryPuzzle({ puzzle, onAnswer }) {
  const [localPuzzle, setLocalPuzzle] = useState(null)
  const p = localPuzzle || puzzle

  const parsed = parseJsonField(p.question)
  const size = parsed?.size || 5
  const half = parsed?.half || []
  const options = parsed?.options || []
  const correctAnswer = (p.answer || '0').toString()

  const [selected, setSelected] = useState(null)
  const [revealed, setRevealed] = useState(false)

  useEffect(() => {
    if (!puzzle.question && !puzzle.answer) {
      setLocalPuzzle(generatePuzzle(puzzle.difficulty || 'medium'))
    }
    setSelected(null)
    setRevealed(false)
  }, [puzzle.puzzleId])

  const handleSelect = (optId) => {
    if (revealed) return
    setSelected(optId)
    setRevealed(true)
    const isCorrect = String(optId) === correctAnswer
    setTimeout(() => onAnswer(isCorrect, String(optId)), 1000)
  }

  const handleTimeUp = () => {
    if (!revealed) {
      setRevealed(true)
      onAnswer(false, '')
    }
  }

  // Build the left half display (with empty right side)
  const halfDisplay = half.map(row => {
    const full = [...row]
    while (full.length < size) full.push(null)
    return full
  })

  const cellSize = size <= 4 ? 22 : size <= 5 ? 18 : 14

  return (
    <div className="space-y-5">
      <PuzzleTimer timerStr={p.timer || '0:25'} onTimeUp={handleTimeUp} paused={revealed} />

      <p className="text-white/50 text-xs uppercase tracking-widest font-semibold text-center">
        Find the Mirror Image
      </p>

      {/* Reference half pattern */}
      <div className="text-center">
        <p className="text-white/40 text-xs mb-2">This is the left half:</p>
        <div className="flex justify-center">
          <div
            className="inline-grid gap-[2px] rounded-lg p-1.5 border-2 border-yellow-400/50 bg-yellow-500/10"
            style={{ gridTemplateColumns: `repeat(${Math.ceil(size / 2)}, ${cellSize}px)` }}
          >
            {half.flatMap((row, r) =>
              row.map((filled, c) => (
                <div
                  key={`h-${r}-${c}`}
                  style={{ width: cellSize, height: cellSize }}
                  className={`rounded-sm ${filled ? 'bg-yellow-400' : 'bg-white/10'}`}
                />
              ))
            )}
          </div>
        </div>
        <p className="text-white/40 text-xs mt-2">Which is the complete symmetric pattern?</p>
      </div>

      {/* Options */}
      <div className="grid grid-cols-2 gap-3">
        {options.map((opt, i) => {
          const isCorrectOpt = String(opt.id) === correctAnswer
          const isSel = selected === opt.id

          return (
            <motion.button
              key={i}
              whileTap={!revealed ? { scale: 0.95 } : {}}
              onClick={() => handleSelect(opt.id)}
              disabled={revealed}
              className={`flex flex-col items-center py-3 px-2 rounded-xl border-2 transition-all ${
                revealed && isCorrectOpt
                  ? 'border-green-400 bg-green-500/10'
                  : revealed && isSel && !isCorrectOpt
                  ? 'border-red-400 bg-red-500/10'
                  : revealed
                  ? 'border-white/10 opacity-40'
                  : 'border-white/20 bg-white/5 hover:bg-white/10'
              }`}
            >
              <span className="text-white/50 text-xs mb-1">{String.fromCharCode(65 + i)}</span>
              <GridDisplay grid={opt.grid} size={size} cellSize={cellSize} />
            </motion.button>
          )
        })}
      </div>
    </div>
  )
}
