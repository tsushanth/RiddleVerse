import { useState, useEffect } from 'react'
import { motion } from 'framer-motion'
import PuzzleTimer from './PuzzleTimer'
import { parseJsonField, shuffle } from './puzzleUtils'

const COLORS = ['#ef4444', '#3b82f6', '#22c55e', '#eab308', '#a855f7', '#f97316', '#ec4899', '#06b6d4']
const COLOR_NAMES = ['Red', 'Blue', 'Green', 'Yellow', 'Purple', 'Orange', 'Pink', 'Cyan']
const SHAPES = ['circle', 'square', 'triangle', 'diamond', 'star', 'hexagon']

function renderShape(shape, color, size = 40) {
  const s = size
  switch (shape) {
    case 'circle':
      return <div style={{ width: s, height: s, borderRadius: '50%', backgroundColor: color }} />
    case 'square':
      return <div style={{ width: s, height: s, borderRadius: 4, backgroundColor: color }} />
    case 'triangle':
      return (
        <div style={{ width: 0, height: 0, borderLeft: `${s/2}px solid transparent`, borderRight: `${s/2}px solid transparent`, borderBottom: `${s}px solid ${color}` }} />
      )
    case 'diamond':
      return <div style={{ width: s * 0.7, height: s * 0.7, backgroundColor: color, transform: 'rotate(45deg)', borderRadius: 3 }} />
    case 'star':
      return <div style={{ width: s, height: s, backgroundColor: color, clipPath: 'polygon(50% 0%, 61% 35%, 98% 35%, 68% 57%, 79% 91%, 50% 70%, 21% 91%, 32% 57%, 2% 35%, 39% 35%)' }} />
    case 'hexagon':
      return <div style={{ width: s, height: s, backgroundColor: color, clipPath: 'polygon(25% 0%, 75% 0%, 100% 50%, 75% 100%, 25% 100%, 0% 50%)' }} />
    default:
      return <div style={{ width: s, height: s, borderRadius: '50%', backgroundColor: color }} />
  }
}

function generatePuzzle(difficulty = 'medium') {
  const gridSize = difficulty === 'easy' ? 3 : difficulty === 'hard' ? 5 : 4
  const pairCount = Math.floor((gridSize * gridSize) / 2)

  // Generate pairs of color+shape combos
  const pairs = []
  const usedCombos = new Set()
  for (let i = 0; i < pairCount; i++) {
    let combo
    do {
      const colorIdx = Math.floor(Math.random() * COLORS.length)
      const shapeIdx = Math.floor(Math.random() * SHAPES.length)
      combo = `${colorIdx}-${shapeIdx}`
    } while (usedCombos.has(combo))
    usedCombos.add(combo)
    const [ci, si] = combo.split('-').map(Number)
    pairs.push({ color: COLORS[ci], colorName: COLOR_NAMES[ci], shape: SHAPES[si], id: i })
  }

  // Double them for matching pairs
  const cards = shuffle([...pairs, ...pairs].map((p, i) => ({ ...p, cardId: i })))
    .slice(0, gridSize * gridSize) // trim if odd grid

  return {
    puzzleId: `local-csm-${Date.now()}`,
    puzzleType: 'colorshapematching',
    question: JSON.stringify({ gridSize, cards }),
    answer: String(pairCount),
    timer: difficulty === 'easy' ? '1:00' : difficulty === 'hard' ? '1:30' : '1:15',
    difficulty,
  }
}

export default function ColorShapeMatchingPuzzle({ puzzle, onAnswer }) {
  const [localPuzzle, setLocalPuzzle] = useState(null)
  const p = localPuzzle || puzzle

  const parsed = parseJsonField(p.question)
  const gridSize = parsed?.gridSize || 4
  const cards = parsed?.cards || []

  const [flipped, setFlipped] = useState(new Set())
  const [matched, setMatched] = useState(new Set())
  const [first, setFirst] = useState(null)
  const [second, setSecond] = useState(null)
  const [moves, setMoves] = useState(0)
  const [done, setDone] = useState(false)
  const [locked, setLocked] = useState(false)

  useEffect(() => {
    if (!puzzle.question && !puzzle.answer) {
      setLocalPuzzle(generatePuzzle(puzzle.difficulty || 'medium'))
    }
    setFlipped(new Set())
    setMatched(new Set())
    setFirst(null)
    setSecond(null)
    setMoves(0)
    setDone(false)
    setLocked(false)
  }, [puzzle.puzzleId])

  const handleCardClick = (index) => {
    if (done || locked || flipped.has(index) || matched.has(index)) return

    if (first === null) {
      setFirst(index)
      setFlipped(new Set([index]))
    } else if (second === null && index !== first) {
      setSecond(index)
      setFlipped(new Set([first, index]))
      setMoves(m => m + 1)
      setLocked(true)

      const card1 = cards[first]
      const card2 = cards[index]
      const isMatch = card1.id === card2.id

      setTimeout(() => {
        if (isMatch) {
          const newMatched = new Set([...matched, first, index])
          setMatched(newMatched)

          // Check if all matched
          if (newMatched.size >= cards.length) {
            setDone(true)
            const maxMoves = cards.length * 2
            const passed = moves + 1 <= maxMoves
            setTimeout(() => onAnswer(true, `${moves + 1} moves`), 800)
          }
        }
        setFlipped(new Set())
        setFirst(null)
        setSecond(null)
        setLocked(false)
      }, 800)
    }
  }

  const handleTimeUp = () => {
    if (!done) {
      setDone(true)
      onAnswer(false, `${matched.size / 2} pairs found`)
    }
  }

  return (
    <div className="space-y-5">
      <PuzzleTimer timerStr={p.timer || '1:15'} onTimeUp={handleTimeUp} paused={done} />

      <div className="flex items-center justify-between">
        <p className="text-white/50 text-xs uppercase tracking-widest font-semibold">Shape Match</p>
        <p className="text-white/50 text-xs">Moves: {moves} | Pairs: {matched.size / 2}/{Math.floor(cards.length / 2)}</p>
      </div>

      {/* Card grid */}
      <div
        className="mx-auto"
        style={{
          display: 'grid',
          gridTemplateColumns: `repeat(${gridSize}, 1fr)`,
          gap: '6px',
          maxWidth: `${Math.min(gridSize * 75, 360)}px`,
        }}
      >
        {cards.map((card, index) => {
          const isFlipped = flipped.has(index)
          const isMatched = matched.has(index)
          const showFace = isFlipped || isMatched

          return (
            <motion.button
              key={index}
              whileTap={!done && !showFace ? { scale: 0.9 } : {}}
              onClick={() => handleCardClick(index)}
              disabled={done}
              className={`aspect-square rounded-lg flex items-center justify-center transition-all ${
                isMatched
                  ? 'bg-green-500/20 border-2 border-green-400/50'
                  : showFace
                  ? 'bg-white/15 border-2 border-purple-400/50'
                  : 'bg-white/10 border-2 border-white/20 hover:bg-white/20'
              }`}
            >
              {showFace ? (
                <motion.div
                  initial={{ rotateY: 90 }}
                  animate={{ rotateY: 0 }}
                  className="flex items-center justify-center"
                >
                  {renderShape(card.shape, card.color, gridSize <= 3 ? 32 : 24)}
                </motion.div>
              ) : (
                <span className="text-white/30 text-lg">?</span>
              )}
            </motion.button>
          )
        })}
      </div>

      {done && (
        <motion.div
          initial={{ opacity: 0, scale: 0.9 }}
          animate={{ opacity: 1, scale: 1 }}
          className="text-center p-4 rounded-xl bg-green-500/20 text-green-300"
        >
          <p className="font-bold text-lg">All pairs found in {moves} moves!</p>
        </motion.div>
      )}
    </div>
  )
}
