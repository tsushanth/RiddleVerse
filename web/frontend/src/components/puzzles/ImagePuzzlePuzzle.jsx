import { useState, useEffect, useCallback } from 'react'
import { motion } from 'framer-motion'
import { Check, RotateCcw, Move } from 'lucide-react'
import PuzzleTimer from './PuzzleTimer'
import { parseJsonField } from './puzzleUtils'

/**
 * ImagePuzzlePuzzle — DB type "imagepuzzle"
 * Jigsaw-style puzzle: arrange pieces on a grid to match the original image.
 * Uses an actual image URL from the DB data.
 */
export default function ImagePuzzlePuzzle({ puzzle, onAnswer }) {
  const parsed = parseJsonField(puzzle.question)
  const answerParsed = parseJsonField(puzzle.answer)

  const theme = parsed?.theme || parsed?.description || 'Image Puzzle'
  const imageUrl = parsed?.imageUrl || ''
  const gridSize = parsed?.gridSize || 3
  const totalPieces = parsed?.totalPieces || gridSize * gridSize
  const pieceSize = parsed?.pieceSize || 100
  const gameSettings = parsed?.gameSettings || {}
  const puzzleMetadata = parsed?.puzzleMetadata || {}
  const correctAssembly = answerParsed?.correctAssembly || []
  const timeLimit = parsed?.timeLimit || 140000

  // Generate piece indices
  const pieceCount = totalPieces || gridSize * gridSize
  const initialPieces = Array.from({ length: pieceCount }, (_, i) => i)

  const [pieces, setPieces] = useState([])
  const [selectedPiece, setSelectedPiece] = useState(null)
  const [submitted, setSubmitted] = useState(false)
  const [isCorrect, setIsCorrect] = useState(null)
  const [imageLoaded, setImageLoaded] = useState(false)
  const [imageError, setImageError] = useState(false)

  useEffect(() => {
    // Shuffle pieces
    const shuffled = [...initialPieces]
    for (let i = shuffled.length - 1; i > 0; i--) {
      const j = Math.floor(Math.random() * (i + 1))
      ;[shuffled[i], shuffled[j]] = [shuffled[j], shuffled[i]]
    }
    setPieces(shuffled)
    setSelectedPiece(null)
    setSubmitted(false)
    setIsCorrect(null)
  }, [puzzle.puzzleId])

  const handlePieceClick = (index) => {
    if (submitted) return
    if (selectedPiece === null) {
      setSelectedPiece(index)
    } else {
      // Swap pieces
      setPieces(prev => {
        const newPieces = [...prev]
        ;[newPieces[selectedPiece], newPieces[index]] = [newPieces[index], newPieces[selectedPiece]]
        return newPieces
      })
      setSelectedPiece(null)
    }
  }

  const handleSubmit = () => {
    if (submitted) return
    setSubmitted(true)
    const correct = pieces.every((p, i) => p === i)
    setIsCorrect(correct)
    setTimeout(() => onAnswer(correct, pieces.join(',')), 800)
  }

  const handleTimeUp = () => {
    if (!submitted) {
      setSubmitted(true)
      setIsCorrect(false)
      onAnswer(false, 'timeout')
    }
  }

  const timerStr = puzzle.timer || `${Math.floor(timeLimit / 60000)}:${String((timeLimit % 60000) / 1000).padStart(2, '0')}`

  // If no image or image fails, show number-based sliding puzzle
  const useNumberMode = !imageUrl || imageError

  return (
    <div className="space-y-5">
      <PuzzleTimer timerStr={timerStr} onTimeUp={handleTimeUp} paused={submitted} />

      <div className="text-center space-y-1">
        <p className="text-white/50 text-xs uppercase tracking-widest font-semibold">
          Image Puzzle
        </p>
        <p className="text-white/70 text-sm">{theme}</p>
        {!useNumberMode && (
          <p className="text-white/40 text-xs">Tap two pieces to swap them into the correct position</p>
        )}
      </div>

      {puzzle.hint && (
        <p className="text-yellow-300/70 text-sm text-center italic">Hint: {puzzle.hint}</p>
      )}

      {/* Hidden image loader */}
      {imageUrl && !imageError && (
        <img
          src={imageUrl}
          onLoad={() => setImageLoaded(true)}
          onError={() => setImageError(true)}
          className="hidden"
          alt=""
        />
      )}

      {/* Grid */}
      <div
        className="mx-auto"
        style={{
          display: 'grid',
          gridTemplateColumns: `repeat(${gridSize}, 1fr)`,
          gap: '3px',
          maxWidth: `${Math.min(gridSize * 80, 360)}px`,
        }}
      >
        {pieces.map((pieceId, index) => {
          const row = Math.floor(pieceId / gridSize)
          const col = pieceId % gridSize
          const isSelected = selectedPiece === index
          const isInPlace = pieceId === index
          const showCorrect = submitted && isInPlace

          return (
            <motion.button
              key={index}
              whileTap={!submitted ? { scale: 0.95 } : {}}
              onClick={() => handlePieceClick(index)}
              disabled={submitted}
              className={`aspect-square rounded-lg border-2 flex items-center justify-center font-bold text-lg transition-all overflow-hidden ${
                showCorrect
                  ? 'border-green-400 bg-green-500/20'
                  : submitted && !isInPlace
                  ? 'border-red-400/50 bg-red-500/10'
                  : isSelected
                  ? 'border-purple-400 bg-purple-500/30 scale-105'
                  : 'border-white/20 bg-white/10 hover:bg-white/20'
              }`}
              style={
                !useNumberMode && imageLoaded
                  ? {
                      backgroundImage: `url(${imageUrl})`,
                      backgroundSize: `${gridSize * 100}% ${gridSize * 100}%`,
                      backgroundPosition: `${(col / (gridSize - 1)) * 100}% ${(row / (gridSize - 1)) * 100}%`,
                    }
                  : {}
              }
            >
              {useNumberMode && (
                <span className={`${isInPlace && !submitted ? 'text-green-300' : 'text-white'}`}>
                  {pieceId + 1}
                </span>
              )}
              {!useNumberMode && !imageLoaded && (
                <span className="text-white/50 text-sm">{pieceId + 1}</span>
              )}
            </motion.button>
          )
        })}
      </div>

      {/* Status */}
      {!submitted && (
        <div className="space-y-3">
          <div className="flex items-center justify-center gap-2 text-white/40 text-xs">
            <Move size={14} />
            <span>{selectedPiece !== null ? 'Now tap another piece to swap' : 'Tap a piece to select it'}</span>
          </div>

          <div className="flex gap-3">
            <button
              onClick={() => {
                const shuffled = [...initialPieces]
                for (let i = shuffled.length - 1; i > 0; i--) {
                  const j = Math.floor(Math.random() * (i + 1))
                  ;[shuffled[i], shuffled[j]] = [shuffled[j], shuffled[i]]
                }
                setPieces(shuffled)
                setSelectedPiece(null)
              }}
              className="flex items-center gap-2 bg-white/10 hover:bg-white/20 text-white/70 px-4 py-2.5 rounded-xl transition-colors text-sm"
            >
              <RotateCcw size={16} />
              Reshuffle
            </button>
            <button
              onClick={handleSubmit}
              className="flex-1 flex items-center justify-center gap-2 bg-green-500 hover:bg-green-600 text-white font-semibold py-2.5 rounded-xl transition-colors text-sm"
            >
              <Check size={18} />
              Check
            </button>
          </div>
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
            {isCorrect ? 'Puzzle Solved!' : 'Not quite right'}
          </p>
        </motion.div>
      )}
    </div>
  )
}
