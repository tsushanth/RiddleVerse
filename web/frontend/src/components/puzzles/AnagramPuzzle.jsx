import { useState, useEffect } from 'react'
import { motion, AnimatePresence } from 'framer-motion'
import { RotateCcw, Lightbulb } from 'lucide-react'
import PuzzleTimer from './PuzzleTimer'
import { shuffle, fuzzyMatch } from './puzzleUtils'

export default function AnagramPuzzle({ puzzle, onAnswer }) {
  const answer = puzzle.answer || ''
  const clue = puzzle.hint || puzzle.question || ''

  // Scrambled letters from the question field (or shuffle the answer)
  const scrambledSource = puzzle.question || shuffle(answer.split('')).join('')
  const letters = scrambledSource.toUpperCase().replace(/[^A-Z]/g, '').split('')

  const [available, setAvailable] = useState([])
  const [selected, setSelected] = useState([])
  const [solved, setSolved] = useState(false)
  const [showHint, setShowHint] = useState(false)
  const [wrong, setWrong] = useState(false)

  useEffect(() => {
    // Each letter has a unique id to handle duplicates
    setAvailable(letters.map((l, i) => ({ letter: l, id: i })))
    setSelected([])
    setSolved(false)
    setWrong(false)
  }, [puzzle.puzzleId])

  const handleTapAvailable = (tile) => {
    if (solved) return
    setAvailable((prev) => prev.filter((t) => t.id !== tile.id))
    setSelected((prev) => [...prev, tile])
    setWrong(false)
  }

  const handleTapSelected = (tile) => {
    if (solved) return
    setSelected((prev) => prev.filter((t) => t.id !== tile.id))
    setAvailable((prev) => [...prev, tile])
    setWrong(false)
  }

  const handleSubmit = () => {
    if (solved) return
    const guess = selected.map((t) => t.letter).join('')
    const isCorrect = fuzzyMatch(guess, answer.toUpperCase().replace(/[^A-Z]/g, ''))
    if (isCorrect) {
      setSolved(true)
      setTimeout(() => onAnswer(true, guess), 800)
    } else {
      setWrong(true)
      setTimeout(() => {
        // Return all tiles
        setAvailable((prev) => [...prev, ...selected])
        setSelected([])
        setWrong(false)
      }, 600)
    }
  }

  const handleClear = () => {
    if (solved) return
    setAvailable((prev) => [...prev, ...selected])
    setSelected([])
    setWrong(false)
  }

  const handleShuffle = () => {
    if (solved) return
    setAvailable((prev) => shuffle([...prev]))
  }

  const handleTimeUp = () => {
    if (!solved) {
      onAnswer(false, selected.map((t) => t.letter).join(''))
    }
  }

  return (
    <div className="space-y-5">
      <PuzzleTimer timerStr={puzzle.timer} onTimeUp={handleTimeUp} paused={solved} />

      <div className="space-y-2 text-center">
        <p className="text-white/50 text-xs uppercase tracking-widest font-semibold">Unscramble the word</p>
        {clue && clue !== puzzle.question && (
          <p className="text-white/70 text-sm italic">{clue}</p>
        )}
      </div>

      {showHint && puzzle.hint && (
        <motion.div
          initial={{ opacity: 0 }}
          animate={{ opacity: 1 }}
          className="bg-yellow-500/20 rounded-xl p-3 border border-yellow-500/30 text-center"
        >
          <p className="text-yellow-100 text-sm">{puzzle.hint}</p>
        </motion.div>
      )}

      {/* Selected letters (answer area) */}
      <div className={`min-h-[64px] flex flex-wrap items-center justify-center gap-2 p-3 rounded-xl border-2 border-dashed transition-colors ${
        wrong ? 'border-red-500/50 bg-red-500/10' : solved ? 'border-green-500/50 bg-green-500/10' : 'border-white/20 bg-white/5'
      }`}>
        <AnimatePresence>
          {selected.map((tile) => (
            <motion.button
              key={tile.id}
              initial={{ scale: 0, opacity: 0 }}
              animate={{ scale: 1, opacity: 1 }}
              exit={{ scale: 0, opacity: 0 }}
              onClick={() => handleTapSelected(tile)}
              className={`w-11 h-11 rounded-lg font-bold text-lg flex items-center justify-center transition-colors ${
                solved ? 'bg-green-500 text-white' : wrong ? 'bg-red-500 text-white' : 'bg-purple-500 text-white hover:bg-purple-400'
              }`}
            >
              {tile.letter}
            </motion.button>
          ))}
        </AnimatePresence>
        {selected.length === 0 && (
          <p className="text-white/30 text-sm">Tap letters below to spell the word</p>
        )}
      </div>

      {/* Available letters */}
      <div className="flex flex-wrap items-center justify-center gap-2">
        <AnimatePresence>
          {available.map((tile) => (
            <motion.button
              key={tile.id}
              initial={{ scale: 0 }}
              animate={{ scale: 1 }}
              exit={{ scale: 0 }}
              whileTap={{ scale: 0.9 }}
              onClick={() => handleTapAvailable(tile)}
              className="w-11 h-11 rounded-lg bg-white/15 border border-white/25 text-white font-bold text-lg flex items-center justify-center hover:bg-white/25 active:bg-white/30 transition-colors"
            >
              {tile.letter}
            </motion.button>
          ))}
        </AnimatePresence>
      </div>

      {/* Controls */}
      {!solved && (
        <div className="flex gap-3 justify-center">
          <button
            onClick={handleShuffle}
            className="flex items-center gap-2 bg-white/10 hover:bg-white/20 text-white/70 px-4 py-2.5 rounded-xl transition-colors text-sm"
          >
            <RotateCcw size={16} />
            Shuffle
          </button>
          <button
            onClick={handleClear}
            className="flex items-center gap-2 bg-white/10 hover:bg-white/20 text-white/70 px-4 py-2.5 rounded-xl transition-colors text-sm"
          >
            Clear
          </button>
          {selected.length > 0 && (
            <motion.button
              initial={{ scale: 0.8, opacity: 0 }}
              animate={{ scale: 1, opacity: 1 }}
              onClick={handleSubmit}
              className="flex items-center gap-2 bg-green-500 hover:bg-green-600 text-white font-semibold px-6 py-2.5 rounded-xl transition-colors text-sm"
            >
              Check
            </motion.button>
          )}
          {puzzle.hint && !showHint && (
            <button
              onClick={() => setShowHint(true)}
              className="flex items-center gap-2 bg-yellow-500/20 hover:bg-yellow-500/30 text-yellow-400 px-4 py-2.5 rounded-xl transition-colors text-sm"
            >
              <Lightbulb size={16} />
            </button>
          )}
        </div>
      )}
    </div>
  )
}
