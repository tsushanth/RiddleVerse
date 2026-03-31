import { useState, useEffect, useRef, useCallback } from 'react'
import { motion } from 'framer-motion'
import PuzzleTimer from './PuzzleTimer'
import { parseJsonField } from './puzzleUtils'

export default function CrosswordPuzzle({ puzzle, onAnswer }) {
  const parsed = parseJsonField(puzzle.question)

  // Extract grid and clues from various JSON shapes
  const matrix = parsed?.matrix || parsed?.grid || []
  const acrossClues = parsed?.acrossClues || parsed?.across || parsed?.clues?.across || []
  const downClues = parsed?.downClues || parsed?.down || parsed?.clues?.down || []

  // Build cell map: which cells are fillable (not '#' or black)
  const rows = matrix.length
  const cols = matrix[0]?.length || 0

  const isBlack = (r, c) => {
    const v = matrix[r]?.[c]
    return v === '#' || v === '.' || v === null || v === undefined
  }

  // Number cells
  const [cellNumbers, setCellNumbers] = useState({})
  useEffect(() => {
    const nums = {}
    let num = 1
    for (let r = 0; r < rows; r++) {
      for (let c = 0; c < cols; c++) {
        if (isBlack(r, c)) continue
        const startsAcross = c === 0 || isBlack(r, c - 1)
        const startsDown = r === 0 || isBlack(r - 1, c)
        if (startsAcross || startsDown) {
          nums[`${r}-${c}`] = num++
        }
      }
    }
    setCellNumbers(nums)
  }, [puzzle.puzzleId])

  const [userGrid, setUserGrid] = useState(() => {
    return matrix.map((row) =>
      row.map((cell) => (cell === '#' || cell === '.' ? '' : ''))
    )
  })

  const [activeCell, setActiveCell] = useState(null)
  const [direction, setDirection] = useState('across') // 'across' | 'down'
  const [solved, setSolved] = useState(false)
  const inputRefs = useRef({})

  // Parse answer grid
  const answerParsed = parseJsonField(puzzle.answer)
  const answerGrid = answerParsed?.grid || answerParsed?.matrix || answerParsed || matrix

  const handleCellClick = (r, c) => {
    if (isBlack(r, c) || solved) return
    if (activeCell && activeCell.r === r && activeCell.c === c) {
      setDirection((d) => (d === 'across' ? 'down' : 'across'))
    }
    setActiveCell({ r, c })
    const key = `${r}-${c}`
    inputRefs.current[key]?.focus()
  }

  const handleInput = (r, c, value) => {
    if (solved) return
    const letter = value.toUpperCase().replace(/[^A-Z]/g, '').slice(-1)
    setUserGrid((prev) => {
      const next = prev.map((row) => [...row])
      next[r][c] = letter
      return next
    })

    // Move to next cell
    if (letter) {
      moveToNext(r, c)
    }
  }

  const handleKeyDown = (r, c, e) => {
    if (e.key === 'Backspace' && !userGrid[r]?.[c]) {
      e.preventDefault()
      moveToPrev(r, c)
    } else if (e.key === 'ArrowRight') {
      e.preventDefault()
      setDirection('across')
      moveToNext(r, c)
    } else if (e.key === 'ArrowLeft') {
      e.preventDefault()
      setDirection('across')
      moveToPrev(r, c)
    } else if (e.key === 'ArrowDown') {
      e.preventDefault()
      setDirection('down')
      moveToNext(r, c)
    } else if (e.key === 'ArrowUp') {
      e.preventDefault()
      setDirection('down')
      moveToPrev(r, c)
    }
  }

  const moveToNext = (r, c) => {
    const dr = direction === 'down' ? 1 : 0
    const dc = direction === 'across' ? 1 : 0
    let nr = r + dr, nc = c + dc
    while (nr < rows && nc < cols) {
      if (!isBlack(nr, nc)) {
        setActiveCell({ r: nr, c: nc })
        inputRefs.current[`${nr}-${nc}`]?.focus()
        return
      }
      nr += dr
      nc += dc
    }
  }

  const moveToPrev = (r, c) => {
    const dr = direction === 'down' ? -1 : 0
    const dc = direction === 'across' ? -1 : 0
    let nr = r + dr, nc = c + dc
    while (nr >= 0 && nc >= 0) {
      if (!isBlack(nr, nc)) {
        setActiveCell({ r: nr, c: nc })
        inputRefs.current[`${nr}-${nc}`]?.focus()
        return
      }
      nr += dr
      nc += dc
    }
  }

  const checkSolution = () => {
    let correct = true
    for (let r = 0; r < rows; r++) {
      for (let c = 0; c < cols; c++) {
        if (isBlack(r, c)) continue
        const expected = (typeof answerGrid === 'object' && answerGrid[r]?.[c])
          ? answerGrid[r][c].toUpperCase()
          : (matrix[r]?.[c] || '').toUpperCase()
        if (userGrid[r]?.[c]?.toUpperCase() !== expected) {
          correct = false
          break
        }
      }
      if (!correct) break
    }
    if (correct) {
      setSolved(true)
      setTimeout(() => onAnswer(true, 'solved'), 800)
    } else {
      // Shake feedback
      onAnswer(false, 'incomplete')
    }
  }

  const handleTimeUp = () => {
    if (!solved) onAnswer(false, 'timeout')
  }

  // Highlight active row/col
  const isHighlighted = (r, c) => {
    if (!activeCell) return false
    if (direction === 'across' && r === activeCell.r) return true
    if (direction === 'down' && c === activeCell.c) return true
    return false
  }

  if (!rows || !cols) {
    return <p className="text-white/50 text-center py-8">Could not parse crossword grid.</p>
  }

  return (
    <div className="space-y-4">
      <PuzzleTimer timerStr={puzzle.timer || '5:00'} onTimeUp={handleTimeUp} paused={solved} />

      <p className="text-white/50 text-xs uppercase tracking-widest font-semibold text-center">Crossword</p>

      {/* Grid */}
      <div className="flex justify-center overflow-x-auto">
        <div
          className="inline-grid gap-0.5"
          style={{ gridTemplateColumns: `repeat(${cols}, 1fr)` }}
        >
          {matrix.map((row, ri) =>
            row.map((cell, ci) => {
              const black = isBlack(ri, ci)
              const num = cellNumbers[`${ri}-${ci}`]
              const isActive = activeCell?.r === ri && activeCell?.c === ci
              const highlighted = isHighlighted(ri, ci) && !black

              return (
                <div
                  key={`${ri}-${ci}`}
                  onClick={() => handleCellClick(ri, ci)}
                  className={`relative w-8 h-8 sm:w-10 sm:h-10 flex items-center justify-center rounded-sm cursor-pointer transition-colors ${
                    black
                      ? 'bg-black/60'
                      : isActive
                      ? 'bg-purple-500/50 ring-2 ring-purple-400'
                      : highlighted
                      ? 'bg-purple-500/20'
                      : 'bg-white/10'
                  }`}
                >
                  {num && (
                    <span className="absolute top-0 left-0.5 text-[8px] text-white/50 leading-none">
                      {num}
                    </span>
                  )}
                  {!black && (
                    <input
                      ref={(el) => (inputRefs.current[`${ri}-${ci}`] = el)}
                      type="text"
                      maxLength={1}
                      value={userGrid[ri]?.[ci] || ''}
                      onChange={(e) => handleInput(ri, ci, e.target.value)}
                      onKeyDown={(e) => handleKeyDown(ri, ci, e)}
                      onFocus={() => setActiveCell({ r: ri, c: ci })}
                      className="w-full h-full bg-transparent text-white text-center font-bold text-sm sm:text-base uppercase focus:outline-none caret-transparent"
                      disabled={solved}
                    />
                  )}
                </div>
              )
            })
          )}
        </div>
      </div>

      {/* Direction toggle */}
      <div className="flex justify-center gap-2">
        <button
          onClick={() => setDirection('across')}
          className={`px-4 py-1.5 rounded-full text-sm font-semibold transition-colors ${
            direction === 'across' ? 'bg-purple-500 text-white' : 'bg-white/10 text-white/60'
          }`}
        >
          Across
        </button>
        <button
          onClick={() => setDirection('down')}
          className={`px-4 py-1.5 rounded-full text-sm font-semibold transition-colors ${
            direction === 'down' ? 'bg-purple-500 text-white' : 'bg-white/10 text-white/60'
          }`}
        >
          Down
        </button>
      </div>

      {/* Clues */}
      <div className="grid grid-cols-1 sm:grid-cols-2 gap-4 max-h-48 overflow-y-auto">
        {acrossClues.length > 0 && (
          <div>
            <p className="text-white/60 text-xs uppercase tracking-widest mb-2 font-semibold">Across</p>
            <div className="space-y-1">
              {acrossClues.map((clue, i) => (
                <p key={i} className="text-white/80 text-sm">
                  {typeof clue === 'object' ? `${clue.number}. ${clue.clue}` : clue}
                </p>
              ))}
            </div>
          </div>
        )}
        {downClues.length > 0 && (
          <div>
            <p className="text-white/60 text-xs uppercase tracking-widest mb-2 font-semibold">Down</p>
            <div className="space-y-1">
              {downClues.map((clue, i) => (
                <p key={i} className="text-white/80 text-sm">
                  {typeof clue === 'object' ? `${clue.number}. ${clue.clue}` : clue}
                </p>
              ))}
            </div>
          </div>
        )}
      </div>

      {!solved && (
        <button
          onClick={checkSolution}
          className="w-full bg-green-500 hover:bg-green-600 text-white font-semibold py-3 rounded-xl transition-colors"
        >
          Check Solution
        </button>
      )}
    </div>
  )
}
