import { useState, useEffect } from 'react'
import { motion } from 'framer-motion'
import PuzzleTimer from './PuzzleTimer'

const SHAPES = {
  0: { name: 'circle', render: (color, size) => <div style={{ width: size, height: size, borderRadius: '50%', background: color }} /> },
  1: { name: 'square', render: (color, size) => <div style={{ width: size, height: size, borderRadius: 4, background: color }} /> },
  2: { name: 'triangle', render: (color, size) => (
    <div style={{ width: 0, height: 0, borderLeft: `${size/2}px solid transparent`, borderRight: `${size/2}px solid transparent`, borderBottom: `${size}px solid ${color}` }} />
  )},
  3: { name: 'diamond', render: (color, size) => (
    <div style={{ width: size, height: size, background: color, transform: 'rotate(45deg)', borderRadius: 4 }} />
  )},
  4: { name: 'star', render: (color, size) => (
    <svg width={size} height={size} viewBox="0 0 24 24" fill={color}>
      <path d="M12 2l3.09 6.26L22 9.27l-5 4.87 1.18 6.88L12 17.77l-6.18 3.25L7 14.14 2 9.27l6.91-1.01L12 2z" />
    </svg>
  )},
  5: { name: 'hexagon', render: (color, size) => (
    <svg width={size} height={size} viewBox="0 0 24 24" fill={color}>
      <path d="M12 2l8.66 5v10L12 22l-8.66-5V7L12 2z" />
    </svg>
  )},
}

const COLORS = {
  0: '#ef4444', // red
  1: '#3b82f6', // blue
  2: '#22c55e', // green
  3: '#eab308', // yellow
  4: '#a855f7', // purple
  5: '#f97316', // orange
  6: '#ec4899', // pink
  7: '#06b6d4', // cyan
}

export default function UniqueObjectPuzzle({ puzzle, onAnswer }) {
  let qData = {}
  try {
    qData = typeof puzzle.question === 'string' ? JSON.parse(puzzle.question) : puzzle.question || {}
  } catch { qData = {} }

  let aData = {}
  try {
    aData = typeof puzzle.answer === 'string' ? JSON.parse(puzzle.answer) : puzzle.answer || {}
  } catch { aData = {} }

  const objects = qData.objects || []
  const instruction = qData.instruction || 'Find the odd one out!'
  const uniqueIndex = aData.uniqueObjectIndex

  const [selected, setSelected] = useState(null)
  const [revealed, setRevealed] = useState(false)
  const [done, setDone] = useState(false)

  useEffect(() => {
    setSelected(null)
    setRevealed(false)
    setDone(false)
  }, [puzzle.puzzleId])

  const handleTap = (idx) => {
    if (done) return
    setSelected(idx)
    setRevealed(true)
    setDone(true)

    const isCorrect = idx === uniqueIndex
    setTimeout(() => onAnswer(isCorrect, `object ${idx}`), 1200)
  }

  const handleTimeUp = () => {
    setDone(true)
    setRevealed(true)
    onAnswer(false, 'timeout')
  }

  const objectSize = objects.length <= 4 ? 70 : objects.length <= 6 ? 60 : 50

  return (
    <div className="space-y-5">
      <PuzzleTimer timerStr={puzzle.timer || '0:30'} onTimeUp={handleTimeUp} paused={done} />

      <div className="text-center space-y-1">
        <p className="text-white/50 text-xs uppercase tracking-widest font-semibold">
          Find the Unique Object
        </p>
        <p className="text-white/60 text-sm">{instruction}</p>
      </div>

      {puzzle.hint && (
        <p className="text-yellow-300/70 text-sm text-center italic">Hint: {puzzle.hint}</p>
      )}

      {/* Objects grid */}
      <div className="flex flex-wrap justify-center gap-4 py-4">
        {objects.map((obj, idx) => {
          const shape = SHAPES[obj.shape] || SHAPES[0]
          const color = COLORS[obj.color] || COLORS[0]
          const isSelected = selected === idx
          const isCorrect = uniqueIndex === idx

          let borderClass = 'border-white/15'
          if (revealed) {
            if (isCorrect) borderClass = 'border-green-400 ring-2 ring-green-400'
            else if (isSelected && !isCorrect) borderClass = 'border-red-400 ring-2 ring-red-400'
          } else if (isSelected) {
            borderClass = 'border-green-400'
          }

          return (
            <motion.button
              key={idx}
              whileTap={{ scale: 0.9 }}
              onClick={() => handleTap(idx)}
              className={`w-24 h-24 rounded-2xl bg-white/10 border-2 ${borderClass} flex items-center justify-center transition-all`}
              animate={revealed && isCorrect ? { scale: [1, 1.1, 1] } : {}}
              transition={{ duration: 0.5 }}
            >
              {shape.render(color, objectSize)}
            </motion.button>
          )
        })}
      </div>

      {/* Result */}
      {revealed && (
        <motion.div
          initial={{ opacity: 0, y: 10 }}
          animate={{ opacity: 1, y: 0 }}
          className={`text-center py-3 rounded-xl font-semibold ${
            selected === uniqueIndex
              ? 'bg-green-500/20 text-green-300'
              : 'bg-red-500/20 text-red-300'
          }`}
        >
          {selected === uniqueIndex
            ? '✓ Correct! You found the unique object!'
            : `✗ The unique one was object #${uniqueIndex + 1}`}
        </motion.div>
      )}
    </div>
  )
}
