import { useState, useEffect } from 'react'
import { motion, AnimatePresence } from 'framer-motion'
import PuzzleTimer from './PuzzleTimer'
import { parseJsonField, shuffle } from './puzzleUtils'

const groupColors = [
  { bg: 'bg-purple-500/30', border: 'border-purple-400', text: 'text-purple-300', label: 'Group 1' },
  { bg: 'bg-purple-500/30', border: 'border-purple-400', text: 'text-purple-300', label: 'Group 2' },
  { bg: 'bg-purple-500/30', border: 'border-purple-400', text: 'text-purple-300', label: 'Group 3' },
  { bg: 'bg-purple-500/30', border: 'border-purple-400', text: 'text-purple-300', label: 'Group 4' },
]

function extractSynonymData(puzzle) {
  // Handle deeply nested JSON from DB: question -> JSON string -> question -> JSON string with wordSets
  let data = puzzle.question
  // Parse up to 3 levels deep to find wordSets
  for (let i = 0; i < 3; i++) {
    if (typeof data === 'string') {
      try { data = JSON.parse(data) } catch { break }
    }
    if (data?.wordSets) break
    if (data?.question) data = data.question
  }

  const wordSets = data?.wordSets || data?.groups || []
  const categories = data?.categories || []
  return { wordSets, categories }
}

export default function SynonymGroupingPuzzle({ puzzle, onAnswer }) {
  const { wordSets, categories } = extractSynonymData(puzzle)

  // wordSets is array of arrays: [["old","aged","ancient","elderly"], ["loud","noisy",...], ...]
  const correctGroups = wordSets
  const groupCount = correctGroups.length || 3
  const allWords = correctGroups.flat ? correctGroups.flat() : []

  const [words, setWords] = useState([])
  const [selected, setSelected] = useState([])
  const [solvedGroups, setSolvedGroups] = useState([]) // [{words: [...], colorIdx}]
  const [wrongShake, setWrongShake] = useState(false)
  const [solved, setSolved] = useState(false)
  const wordsPerGroup = allWords.length > 0 ? Math.ceil(allWords.length / groupCount) : 4

  useEffect(() => {
    if (allWords.length > 0) {
      setWords(shuffle([...allWords]))
    }
    setSelected([])
    setSolvedGroups([])
    setSolved(false)
  }, [puzzle.puzzleId])

  const handleTap = (word) => {
    if (solved) return
    setSelected((prev) =>
      prev.includes(word) ? prev.filter((w) => w !== word) : [...prev, word]
    )
  }

  const handleSubmitGroup = () => {
    if (selected.length !== wordsPerGroup) return

    // Check if this selection matches any correct group
    const normalizeGroup = (g) => g.map((w) => w.toLowerCase().trim()).sort().join('|')
    const selectedNorm = normalizeGroup(selected)

    const matchIdx = correctGroups.findIndex(
      (g) => normalizeGroup(g) === selectedNorm
    )

    if (matchIdx !== -1) {
      const colorIdx = solvedGroups.length % groupColors.length
      setSolvedGroups((prev) => [...prev, { words: [...selected], colorIdx }])
      setWords((prev) => prev.filter((w) => !selected.includes(w)))
      setSelected([])

      if (solvedGroups.length + 1 === groupCount) {
        setSolved(true)
        setTimeout(() => onAnswer(true, 'all-grouped'), 800)
      }
    } else {
      setWrongShake(true)
      setTimeout(() => setWrongShake(false), 600)
    }
  }

  const handleTimeUp = () => {
    if (!solved) onAnswer(solvedGroups.length > 0, `${solvedGroups.length}/${groupCount}`)
  }

  return (
    <div className="space-y-5">
      <PuzzleTimer timerStr={puzzle.timer || '2:00'} onTimeUp={handleTimeUp} paused={solved} />

      <div className="text-center space-y-1">
        <p className="text-white/50 text-xs uppercase tracking-widest font-semibold">
          Group the Synonyms
        </p>
        <p className="text-white/60 text-sm">
          Select {wordsPerGroup} words that belong together
        </p>
      </div>

      {puzzle.hint && (
        <p className="text-yellow-300/70 text-sm text-center italic">Hint: {puzzle.hint}</p>
      )}

      {/* Solved groups */}
      <AnimatePresence>
        {solvedGroups.map((group, i) => (
          <motion.div
            key={i}
            initial={{ opacity: 0, scale: 0.9 }}
            animate={{ opacity: 1, scale: 1 }}
            className={`flex flex-wrap gap-2 p-3 rounded-xl border ${groupColors[group.colorIdx].bg} ${groupColors[group.colorIdx].border}`}
          >
            {group.words.map((w, j) => (
              <span key={j} className={`px-3 py-1.5 rounded-full text-sm font-semibold ${groupColors[group.colorIdx].text}`}>
                {w}
              </span>
            ))}
          </motion.div>
        ))}
      </AnimatePresence>

      {/* Remaining words */}
      <motion.div
        animate={wrongShake ? { x: [-8, 8, -6, 6, -3, 3, 0] } : {}}
        transition={{ duration: 0.4 }}
        className="flex flex-wrap gap-2 justify-center"
      >
        {words.map((word, i) => {
          const isSelected = selected.includes(word)
          return (
            <motion.button
              key={word + i}
              whileTap={{ scale: 0.95 }}
              onClick={() => handleTap(word)}
              className={`px-4 py-2 rounded-xl text-sm font-semibold transition-all border-2 ${
                isSelected
                  ? 'bg-green-500 border-green-300 text-white shadow-lg shadow-green-500/40 scale-110 ring-2 ring-green-300'
                  : 'bg-white/10 border-white/20 text-white/80 hover:bg-white/20'
              }`}
            >
              {word}
            </motion.button>
          )
        })}
      </motion.div>

      {!solved && selected.length === wordsPerGroup && (
        <motion.button
          initial={{ opacity: 0, y: 10 }}
          animate={{ opacity: 1, y: 0 }}
          onClick={handleSubmitGroup}
          className="w-full bg-green-500 hover:bg-green-600 text-white font-semibold py-3 rounded-xl transition-colors"
        >
          Submit Group ({selected.length}/{wordsPerGroup})
        </motion.button>
      )}

      <div className="text-center text-white/50 text-sm">
        {solvedGroups.length} / {groupCount} groups found
      </div>
    </div>
  )
}
