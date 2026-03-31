import { useState, useEffect } from 'react'
import { motion, AnimatePresence } from 'framer-motion'
import PuzzleTimer from './PuzzleTimer'
import { parseJsonField, shuffle } from './puzzleUtils'

const balloonColors = [
  'from-red-500 to-pink-500',
  'from-blue-500 to-indigo-500',
  'from-green-500 to-emerald-500',
  'from-yellow-500 to-orange-500',
  'from-purple-500 to-violet-500',
  'from-cyan-500 to-teal-500',
  'from-rose-500 to-red-500',
  'from-amber-500 to-yellow-500',
]

function extractAntonymPairs(puzzle) {
  // Handle deeply nested JSON: question -> JSON string -> question -> JSON string
  let data = puzzle.question
  for (let i = 0; i < 4; i++) {
    if (typeof data === 'string') {
      try { data = JSON.parse(data) } catch { break }
    }
    if (data?.pairs) break
    // If data.question is present, dig deeper
    if (data?.question) { data = data.question; continue }
    // If data is already an array of pairs, use it directly
    if (Array.isArray(data) && data.length > 0 && Array.isArray(data[0])) break
  }

  let rawPairs
  if (Array.isArray(data) && data.length > 0 && Array.isArray(data[0])) {
    // Direct array format: [["hot","cold"], ["happy","sad"]]
    rawPairs = data
  } else {
    rawPairs = data?.pairs || []
  }

  // Pairs can be [{word1, word2}] or [[word1, word2]]
  return rawPairs.map(p =>
    Array.isArray(p) ? p : [p.word1, p.word2]
  ).filter(p => p[0] && p[1])
}

export default function AntonymBalloonPuzzle({ puzzle, onAnswer }) {
  const pairs = extractAntonymPairs(puzzle)

  const [balloons, setBalloons] = useState([])
  const [selectedBalloon, setSelectedBalloon] = useState(null)
  const [popped, setPopped] = useState(new Set())
  const [wrongPair, setWrongPair] = useState(null)
  const [solved, setSolved] = useState(false)

  useEffect(() => {
    const allWords = pairs.flat()
    const shuffled = shuffle(allWords).map((word, i) => ({
      id: i,
      word,
      color: balloonColors[i % balloonColors.length],
      x: Math.random() * 60 + 20,
      y: Math.random() * 50 + 20,
    }))
    setBalloons(shuffled)
    setPopped(new Set())
    setSelectedBalloon(null)
    setSolved(false)
  }, [puzzle.puzzleId])

  const findPairIndex = (w1, w2) => {
    return pairs.findIndex(
      (p) =>
        (p[0].toLowerCase() === w1.toLowerCase() && p[1].toLowerCase() === w2.toLowerCase()) ||
        (p[1].toLowerCase() === w1.toLowerCase() && p[0].toLowerCase() === w2.toLowerCase())
    )
  }

  const handleTap = (balloon) => {
    if (solved || popped.has(balloon.id)) return

    if (!selectedBalloon) {
      setSelectedBalloon(balloon)
      return
    }

    if (selectedBalloon.id === balloon.id) {
      setSelectedBalloon(null)
      return
    }

    // Check if they're a pair
    const pairIdx = findPairIndex(selectedBalloon.word, balloon.word)
    if (pairIdx !== -1) {
      // Pop both
      setPopped((prev) => new Set([...prev, selectedBalloon.id, balloon.id]))
      setSelectedBalloon(null)

      const newPoppedCount = popped.size + 2
      if (newPoppedCount >= balloons.length) {
        setSolved(true)
        setTimeout(() => onAnswer(true, 'all-popped'), 800)
      }
    } else {
      setWrongPair({ a: selectedBalloon.id, b: balloon.id })
      setTimeout(() => {
        setWrongPair(null)
        setSelectedBalloon(null)
      }, 600)
    }
  }

  const handleTimeUp = () => {
    if (!solved) onAnswer(popped.size > 0, `${popped.size / 2}/${pairs.length}`)
  }

  return (
    <div className="space-y-5">
      <PuzzleTimer timerStr={puzzle.timer || '2:00'} onTimeUp={handleTimeUp} paused={solved} />

      <div className="text-center space-y-1">
        <p className="text-white/50 text-xs uppercase tracking-widest font-semibold">
          Pop the Antonyms
        </p>
        <p className="text-white/60 text-sm">Tap pairs of opposite words to pop them</p>
      </div>

      {/* Balloon area */}
      <div className="relative min-h-[300px] bg-white/5 rounded-2xl overflow-hidden">
        <AnimatePresence>
          {balloons.map((balloon) => {
            if (popped.has(balloon.id)) return null
            const isSelected = selectedBalloon?.id === balloon.id
            const isWrong = wrongPair?.a === balloon.id || wrongPair?.b === balloon.id

            return (
              <motion.button
                key={balloon.id}
                initial={{ scale: 0, opacity: 0 }}
                animate={{
                  scale: isWrong ? [1, 1.1, 0.9, 1] : 1,
                  opacity: 1,
                  y: [0, -8, 0],
                }}
                exit={{ scale: 0, opacity: 0, transition: { duration: 0.3 } }}
                transition={{
                  y: { repeat: Infinity, duration: 2 + Math.random(), ease: 'easeInOut' },
                }}
                onClick={() => handleTap(balloon)}
                className="absolute"
                style={{ left: `${balloon.x}%`, top: `${balloon.y}%`, transform: 'translate(-50%, -50%)' }}
              >
                <div
                  className={`px-4 py-3 rounded-2xl bg-gradient-to-b ${balloon.color} text-white font-bold text-sm shadow-lg transition-all ${
                    isSelected ? 'ring-3 ring-white scale-110' : ''
                  } ${isWrong ? 'ring-3 ring-red-400' : ''}`}
                >
                  {balloon.word}
                </div>
                {/* Balloon string */}
                <div className="w-px h-6 mx-auto bg-white/30" />
              </motion.button>
            )
          })}
        </AnimatePresence>
      </div>

      <div className="text-center text-white/50 text-sm">
        {Math.floor(popped.size / 2)} / {pairs.length} pairs found
      </div>
    </div>
  )
}
