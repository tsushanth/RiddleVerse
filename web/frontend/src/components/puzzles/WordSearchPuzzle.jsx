import { useState, useEffect, useRef, useCallback } from 'react'
import { motion } from 'framer-motion'
import PuzzleTimer from './PuzzleTimer'
import { parseJsonField } from './puzzleUtils'

export default function WordSearchPuzzle({ puzzle, onAnswer }) {
  const parsed = parseJsonField(puzzle.question)
  const grid = parsed?.grid || parsed?.matrix || []
  const wordsToFind = (parsed?.words || parsed?.wordList || []).map((w) =>
    typeof w === 'string' ? w.toUpperCase() : w
  )

  const [found, setFound] = useState([])
  const [selecting, setSelecting] = useState(false)
  const [selectionStart, setSelectionStart] = useState(null)
  const [selectionCurrent, setSelectionCurrent] = useState(null)
  const [highlightedCells, setHighlightedCells] = useState([]) // [{row, col, wordIndex}]
  const gridRef = useRef(null)
  const solved = found.length === wordsToFind.length && wordsToFind.length > 0

  // Colors for found words
  const wordColors = [
    'rgba(168, 85, 247, 0.4)',  // purple
    'rgba(59, 130, 246, 0.4)',  // blue
    'rgba(16, 185, 129, 0.4)',  // green
    'rgba(245, 158, 11, 0.4)',  // amber
    'rgba(239, 68, 68, 0.4)',   // red
    'rgba(236, 72, 153, 0.4)',  // pink
    'rgba(6, 182, 212, 0.4)',   // cyan
    'rgba(132, 204, 22, 0.4)',  // lime
  ]

  const getCellsInLine = useCallback((start, end) => {
    if (!start || !end) return []
    const dr = Math.sign(end.row - start.row)
    const dc = Math.sign(end.col - start.col)
    const rowDist = Math.abs(end.row - start.row)
    const colDist = Math.abs(end.col - start.col)

    // Must be horizontal, vertical, or diagonal
    if (rowDist !== colDist && rowDist !== 0 && colDist !== 0) return []

    const steps = Math.max(rowDist, colDist)
    const cells = []
    for (let i = 0; i <= steps; i++) {
      cells.push({ row: start.row + i * dr, col: start.col + i * dc })
    }
    return cells
  }, [])

  const getWordFromCells = useCallback((cells) => {
    return cells.map((c) => (grid[c.row]?.[c.col] || '').toUpperCase()).join('')
  }, [grid])

  const handleCellDown = (row, col) => {
    if (solved) return
    setSelecting(true)
    setSelectionStart({ row, col })
    setSelectionCurrent({ row, col })
  }

  const handleCellMove = (row, col) => {
    if (!selecting) return
    setSelectionCurrent({ row, col })
  }

  const handleCellUp = () => {
    if (!selecting || !selectionStart || !selectionCurrent) {
      setSelecting(false)
      return
    }

    const cells = getCellsInLine(selectionStart, selectionCurrent)
    const word = getWordFromCells(cells)
    const reverseWord = word.split('').reverse().join('')

    const matchIndex = wordsToFind.findIndex(
      (w) => !found.includes(w) && (w === word || w === reverseWord)
    )

    if (matchIndex !== -1) {
      const matchedWord = wordsToFind[matchIndex]
      setFound((prev) => [...prev, matchedWord])
      const colorIdx = (found.length) % wordColors.length
      setHighlightedCells((prev) => [
        ...prev,
        ...cells.map((c) => ({ ...c, color: wordColors[colorIdx] })),
      ])

      // Check if all found
      if (found.length + 1 === wordsToFind.length) {
        setTimeout(() => onAnswer(true, 'all-found'), 800)
      }
    }

    setSelecting(false)
    setSelectionStart(null)
    setSelectionCurrent(null)
  }

  const handleTimeUp = () => {
    if (!solved) {
      onAnswer(found.length > 0, found.join(','))
    }
  }

  // Current drag line cells
  const dragCells = selecting ? getCellsInLine(selectionStart, selectionCurrent) : []
  const dragCellSet = new Set(dragCells.map((c) => `${c.row}-${c.col}`))

  const getCellHighlight = (row, col) => {
    const key = `${row}-${col}`
    if (dragCellSet.has(key)) return 'bg-purple-500/40'
    const hl = highlightedCells.find((c) => c.row === row && c.col === col)
    if (hl) return ''
    return ''
  }

  const getCellBgStyle = (row, col) => {
    const hl = highlightedCells.find((c) => c.row === row && c.col === col)
    if (hl) return { backgroundColor: hl.color }
    return {}
  }

  if (!grid.length) {
    return <p className="text-white/50 text-center py-8">Could not parse word search grid.</p>
  }

  return (
    <div className="space-y-4">
      <PuzzleTimer timerStr={puzzle.timer || '3:00'} onTimeUp={handleTimeUp} paused={solved} />

      <p className="text-white/50 text-xs uppercase tracking-widest font-semibold text-center">
        Find {wordsToFind.length} words
      </p>

      {/* Grid */}
      <div
        ref={gridRef}
        className="flex justify-center select-none"
        onMouseLeave={handleCellUp}
        onTouchEnd={handleCellUp}
      >
        <div
          className="inline-grid gap-0.5"
          style={{ gridTemplateColumns: `repeat(${grid[0]?.length || 1}, 1fr)` }}
        >
          {grid.map((row, ri) =>
            row.map((cell, ci) => (
              <motion.div
                key={`${ri}-${ci}`}
                onMouseDown={() => handleCellDown(ri, ci)}
                onMouseEnter={() => handleCellMove(ri, ci)}
                onMouseUp={handleCellUp}
                onTouchStart={() => handleCellDown(ri, ci)}
                onTouchMove={(e) => {
                  const touch = e.touches[0]
                  const el = document.elementFromPoint(touch.clientX, touch.clientY)
                  if (el?.dataset?.row && el?.dataset?.col) {
                    handleCellMove(parseInt(el.dataset.row), parseInt(el.dataset.col))
                  }
                }}
                data-row={ri}
                data-col={ci}
                className={`w-8 h-8 sm:w-10 sm:h-10 flex items-center justify-center rounded text-sm sm:text-base font-bold text-white cursor-pointer transition-colors ${getCellHighlight(ri, ci)} ${
                  !highlightedCells.find((c) => c.row === ri && c.col === ci) && !dragCellSet.has(`${ri}-${ci}`)
                    ? 'bg-white/10 hover:bg-white/20'
                    : ''
                }`}
                style={getCellBgStyle(ri, ci)}
              >
                {(cell || '').toUpperCase()}
              </motion.div>
            ))
          )}
        </div>
      </div>

      {/* Word list */}
      <div className="flex flex-wrap gap-2 justify-center">
        {wordsToFind.map((word, i) => {
          const isFound = found.includes(word)
          return (
            <span
              key={i}
              className={`px-3 py-1.5 rounded-full text-sm font-semibold transition-all ${
                isFound
                  ? 'bg-green-500/30 text-green-300 line-through'
                  : 'bg-white/10 text-white/80'
              }`}
            >
              {word}
            </span>
          )
        })}
      </div>

      {/* Progress */}
      <div className="text-center text-white/50 text-sm">
        {found.length} / {wordsToFind.length} words found
      </div>
    </div>
  )
}
