import { useState, useEffect, useRef } from 'react'
import { motion, AnimatePresence } from 'framer-motion'
import PuzzleTimer from './PuzzleTimer'

export default function WordPrefixPuzzle({ puzzle, onAnswer }) {
  // Parse answer data
  let answerData = {}
  try {
    answerData = typeof puzzle.answer === 'string' ? JSON.parse(puzzle.answer) : puzzle.answer || {}
  } catch { answerData = {} }

  const prefix = (typeof puzzle.question === 'string' && !puzzle.question.startsWith('{'))
    ? puzzle.question.toLowerCase()
    : ''
  const allWords = (answerData.allWords || []).map(w => ({
    ...w,
    word: (w.word || '').toLowerCase()
  }))
  const timeLimit = answerData.timeLimit || 120

  const [input, setInput] = useState('')
  const [foundWords, setFoundWords] = useState([])
  const [score, setScore] = useState(0)
  const [lastResult, setLastResult] = useState(null) // 'correct' | 'duplicate' | 'wrong' | 'not-prefix'
  const [done, setDone] = useState(false)
  const inputRef = useRef(null)

  useEffect(() => {
    setFoundWords([])
    setScore(0)
    setInput('')
    setDone(false)
    setLastResult(null)
  }, [puzzle.puzzleId])

  const handleSubmit = (e) => {
    e?.preventDefault()
    const guess = input.trim().toLowerCase()
    if (!guess) return

    if (!guess.startsWith(prefix)) {
      setLastResult('not-prefix')
    } else if (foundWords.includes(guess)) {
      setLastResult('duplicate')
    } else {
      const match = allWords.find(w => w.word === guess)
      if (match) {
        setFoundWords(prev => [...prev, guess])
        setScore(prev => prev + (match.points || 10))
        setLastResult('correct')
      } else {
        setLastResult('wrong')
      }
    }
    setInput('')
    setTimeout(() => setLastResult(null), 1500)
    inputRef.current?.focus()
  }

  const handleTimeUp = () => {
    setDone(true)
    onAnswer(foundWords.length > 0, foundWords.join(', '))
  }

  // Sort found words by points (highest first)
  const sortedFound = [...foundWords].sort((a, b) => {
    const wa = allWords.find(w => w.word === a)
    const wb = allWords.find(w => w.word === b)
    return (wb?.points || 0) - (wa?.points || 0)
  })

  return (
    <div className="space-y-4">
      <PuzzleTimer timerStr={`${Math.floor(timeLimit / 60)}:${String(timeLimit % 60).padStart(2, '0')}`} onTimeUp={handleTimeUp} paused={done} />

      <div className="text-center space-y-2">
        <p className="text-white/50 text-xs uppercase tracking-widest font-semibold">
          Find words starting with
        </p>
        <p className="text-5xl font-bold text-yellow-300 tracking-wider">
          {prefix.toUpperCase()}
        </p>
        {puzzle.hint && (
          <p className="text-white/40 text-xs">{puzzle.hint}</p>
        )}
      </div>

      {/* Input */}
      {!done && (
        <form onSubmit={handleSubmit} className="flex gap-2">
          <input
            ref={inputRef}
            type="text"
            value={input}
            onChange={(e) => setInput(e.target.value)}
            placeholder={`Type a word starting with "${prefix}"...`}
            className="flex-1 px-4 py-3 bg-white/10 border border-white/20 rounded-xl text-white placeholder-white/30 text-lg focus:outline-none focus:border-purple-400 focus:ring-1 focus:ring-purple-400"
            autoFocus
            autoComplete="off"
          />
          <button
            type="submit"
            className="px-6 py-3 bg-green-500 hover:bg-green-400 text-white font-bold rounded-xl transition-colors"
          >
            Go
          </button>
        </form>
      )}

      {/* Feedback */}
      <AnimatePresence>
        {lastResult && (
          <motion.div
            initial={{ opacity: 0, y: -10 }}
            animate={{ opacity: 1, y: 0 }}
            exit={{ opacity: 0 }}
            className={`text-center py-2 rounded-xl text-sm font-semibold ${
              lastResult === 'correct' ? 'bg-green-500/20 text-green-300' :
              lastResult === 'duplicate' ? 'bg-yellow-500/20 text-yellow-300' :
              lastResult === 'not-prefix' ? 'bg-orange-500/20 text-orange-300' :
              'bg-red-500/20 text-red-300'
            }`}
          >
            {lastResult === 'correct' && '✓ Nice!'}
            {lastResult === 'duplicate' && 'Already found!'}
            {lastResult === 'not-prefix' && `Must start with "${prefix}"`}
            {lastResult === 'wrong' && 'Not in word list'}
          </motion.div>
        )}
      </AnimatePresence>

      {/* Score bar */}
      <div className="flex justify-between items-center px-1">
        <span className="text-white/60 text-sm">{foundWords.length} words found</span>
        <span className="text-yellow-300 font-bold text-lg">{score} pts</span>
      </div>

      {/* Found words */}
      <div className="flex flex-wrap gap-2">
        {sortedFound.map((word, i) => {
          const match = allWords.find(w => w.word === word)
          return (
            <motion.span
              key={word}
              initial={{ opacity: 0, scale: 0.8 }}
              animate={{ opacity: 1, scale: 1 }}
              className="px-3 py-1.5 bg-purple-500/30 border border-purple-400/50 rounded-full text-sm text-purple-200 font-medium"
            >
              {word} <span className="text-purple-400/70 text-xs">+{match?.points || 10}</span>
            </motion.span>
          )
        })}
      </div>

      {/* Done summary */}
      {done && (
        <div className="text-center bg-white/5 rounded-xl p-4 space-y-1">
          <p className="text-white/60 text-sm">Time's up!</p>
          <p className="text-2xl font-bold text-white">{foundWords.length} words / {score} points</p>
        </div>
      )}
    </div>
  )
}
