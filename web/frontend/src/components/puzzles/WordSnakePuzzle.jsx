import { useState, useEffect } from 'react'
import { motion, AnimatePresence } from 'framer-motion'
import PuzzleTimer from './PuzzleTimer'

export default function WordSnakePuzzle({ puzzle, onAnswer }) {
  // Parse question JSON for grid + words
  let qData = {}
  try {
    qData = typeof puzzle.question === 'string' ? JSON.parse(puzzle.question) : puzzle.question || {}
  } catch { qData = {} }

  const grid = qData.grid || []
  const words = (qData.words || []).map(w => ({
    ...w,
    word: (w.word || '').toUpperCase(),
    path: w.path || []
  }))

  const rows = grid.length
  const cols = grid[0]?.length || 0

  const [selectedCells, setSelectedCells] = useState([])
  const [foundWords, setFoundWords] = useState([])
  const [foundPaths, setFoundPaths] = useState([]) // array of {word, path, colorIdx}
  const [isDragging, setIsDragging] = useState(false)
  const [lastResult, setLastResult] = useState(null)
  const [done, setDone] = useState(false)

  const pathColors = [
    'bg-green-500/50', 'bg-blue-500/50', 'bg-purple-500/50',
    'bg-yellow-500/50', 'bg-pink-500/50', 'bg-cyan-500/50'
  ]

  useEffect(() => {
    setSelectedCells([])
    setFoundWords([])
    setFoundPaths([])
    setDone(false)
    setLastResult(null)
  }, [puzzle.puzzleId])

  const cellKey = (r, c) => `${r},${c}`

  const isAdjacent = (cell, r, c) => {
    return Math.abs(cell.row - r) <= 1 && Math.abs(cell.col - c) <= 1 && !(cell.row === r && cell.col === c)
  }

  const handleCellDown = (r, c) => {
    if (done) return
    setIsDragging(true)
    setSelectedCells([{ row: r, col: c }])
  }

  const handleCellEnter = (r, c) => {
    if (!isDragging || done) return
    const last = selectedCells[selectedCells.length - 1]
    if (!last) return

    // Check if going back
    if (selectedCells.length >= 2) {
      const prev = selectedCells[selectedCells.length - 2]
      if (prev.row === r && prev.col === c) {
        setSelectedCells(prev => prev.slice(0, -1))
        return
      }
    }

    // Only adjacent and not already selected
    if (isAdjacent(last, r, c) && !selectedCells.find(s => s.row === r && s.col === c)) {
      setSelectedCells(prev => [...prev, { row: r, col: c }])
    }
  }

  const handleCellUp = () => {
    if (!isDragging) return
    setIsDragging(false)

    const selectedWord = selectedCells.map(c => grid[c.row]?.[c.col] || '').join('').toUpperCase()

    // Check if it matches any target word
    const match = words.find(w => w.word === selectedWord && !foundWords.includes(w.word))

    if (match) {
      setFoundWords(prev => [...prev, match.word])
      setFoundPaths(prev => [...prev, {
        word: match.word,
        path: selectedCells.map(c => cellKey(c.row, c.col)),
        colorIdx: prev.length % pathColors.length
      }])
      setLastResult('correct')

      if (foundWords.length + 1 === words.length) {
        setDone(true)
        setTimeout(() => onAnswer(true, foundWords.join(', ')), 800)
      }
    } else if (selectedWord.length >= 3) {
      setLastResult('wrong')
    }

    setSelectedCells([])
    setTimeout(() => setLastResult(null), 1000)
  }

  const getCellBg = (r, c) => {
    const key = cellKey(r, c)

    // Check if in current selection
    if (selectedCells.find(s => s.row === r && s.col === c)) {
      return 'bg-green-500/60 scale-110'
    }

    // Check if in found path
    for (const fp of foundPaths) {
      if (fp.path.includes(key)) return pathColors[fp.colorIdx]
    }

    return 'bg-white/10'
  }

  const handleTimeUp = () => {
    setDone(true)
    onAnswer(foundWords.length > 0, foundWords.join(', '))
  }

  return (
    <div className="space-y-4">
      <PuzzleTimer timerStr={puzzle.timer || '3:00'} onTimeUp={handleTimeUp} paused={done} />

      <div className="text-center space-y-1">
        <p className="text-white/50 text-xs uppercase tracking-widest font-semibold">
          Trace words on the grid
        </p>
        <p className="text-white/40 text-xs">Drag through adjacent letters to form words</p>
      </div>

      {/* Grid */}
      <div
        className="flex justify-center"
        onMouseUp={handleCellUp}
        onTouchEnd={handleCellUp}
        onMouseLeave={() => isDragging && handleCellUp()}
      >
        <div
          className="inline-grid gap-1"
          style={{ gridTemplateColumns: `repeat(${cols}, minmax(0, 1fr))` }}
        >
          {grid.map((row, r) =>
            row.map((letter, c) => (
              <motion.div
                key={cellKey(r, c)}
                onMouseDown={() => handleCellDown(r, c)}
                onMouseEnter={() => handleCellEnter(r, c)}
                onTouchStart={(e) => { e.preventDefault(); handleCellDown(r, c) }}
                onTouchMove={(e) => {
                  const touch = e.touches[0]
                  const el = document.elementFromPoint(touch.clientX, touch.clientY)
                  if (el?.dataset?.row && el?.dataset?.col) {
                    handleCellEnter(parseInt(el.dataset.row), parseInt(el.dataset.col))
                  }
                }}
                data-row={r}
                data-col={c}
                className={`w-10 h-10 sm:w-12 sm:h-12 rounded-lg flex items-center justify-center text-lg font-bold text-white cursor-pointer select-none transition-all border border-white/10 ${getCellBg(r, c)}`}
              >
                {letter}
              </motion.div>
            ))
          )}
        </div>
      </div>

      {/* Feedback */}
      <AnimatePresence>
        {lastResult && (
          <motion.div
            initial={{ opacity: 0, y: -10 }}
            animate={{ opacity: 1, y: 0 }}
            exit={{ opacity: 0 }}
            className={`text-center py-2 rounded-xl text-sm font-semibold ${
              lastResult === 'correct' ? 'bg-green-500/20 text-green-300' : 'bg-red-500/20 text-red-300'
            }`}
          >
            {lastResult === 'correct' ? '✓ Found!' : 'Not a target word'}
          </motion.div>
        )}
      </AnimatePresence>

      {/* Word clues */}
      <div className="space-y-2">
        <p className="text-white/50 text-xs uppercase tracking-widest font-semibold">
          Words to find ({foundWords.length}/{words.length})
        </p>
        {words.map((w, i) => {
          const isFound = foundWords.includes(w.word)
          return (
            <div
              key={i}
              className={`flex items-center gap-2 px-3 py-2 rounded-lg ${
                isFound ? 'bg-green-500/20 border border-green-500/30' : 'bg-white/5 border border-white/10'
              }`}
            >
              <span className={`text-lg ${isFound ? 'text-green-400' : 'text-white/30'}`}>
                {isFound ? '✓' : '○'}
              </span>
              <span className={`text-sm font-medium ${isFound ? 'text-green-300' : 'text-white/60'}`}>
                {w.clue || (isFound ? w.word : '???')}
              </span>
              {isFound && (
                <span className="ml-auto text-green-400 font-bold text-sm">{w.word}</span>
              )}
            </div>
          )
        })}
      </div>
    </div>
  )
}
