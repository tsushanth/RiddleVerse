import { useState, useEffect, useRef } from 'react'
import { motion, AnimatePresence } from 'framer-motion'
import PuzzleTimer from './PuzzleTimer'

export default function LetterSetPuzzle({ puzzle, onAnswer }) {
  // Parse question JSON — handle multiple levels of nesting
  let qData = {}
  try {
    let raw = puzzle.question
    for (let i = 0; i < 3; i++) {
      if (typeof raw === 'string') {
        try { raw = JSON.parse(raw) } catch { break }
      }
      if (raw?.letterSet || raw?.letters || raw?.allWords) { qData = raw; break }
      if (raw?.question) { raw = raw.question; continue }
      qData = raw || {}
      break
    }
  } catch { qData = {} }

  let aData = {}
  try {
    let raw = puzzle.answer
    for (let i = 0; i < 3; i++) {
      if (typeof raw === 'string') {
        try { raw = JSON.parse(raw) } catch { break }
      }
      if (raw?.allWords || raw?.targets) { aData = raw; break }
      if (raw?.answer) { raw = raw.answer; continue }
      aData = raw || {}
      break
    }
  } catch { aData = {} }

  const letters = qData.letters || (qData.letterSet || '').split('')
  const letterSet = qData.letterSet || letters.join('')

  // Build valid word list from question data OR answer data
  const qWords = (qData.allWords || []).map(w => typeof w === 'string' ? { word: w.toUpperCase(), points: 10 } : { ...w, word: (w.word || '').toUpperCase() })
  const aWords = Array.isArray(aData.allWords) ? aData.allWords.map(w => typeof w === 'string' ? { word: w.toUpperCase(), points: 10 } : { ...w, word: (w.word || '').toUpperCase() }) : []
  const allWords = qWords.length > 0 ? qWords : aWords
  const validWordSet = new Set(allWords.map(w => w.word))
  const targets = aData.targets || { bronze: 2, silver: 4, gold: 7 }
  const totalWords = aData.totalWords || allWords.length

  const [input, setInput] = useState([])
  const [foundWords, setFoundWords] = useState([])
  const [score, setScore] = useState(0)
  const [lastResult, setLastResult] = useState(null)
  const [done, setDone] = useState(false)

  useEffect(() => {
    setInput([])
    setFoundWords([])
    setScore(0)
    setDone(false)
    setLastResult(null)
  }, [puzzle.puzzleId])

  const handleLetterTap = (letter, idx) => {
    // Only allow each letter position once
    if (input.find(i => i.idx === idx)) {
      // Deselect
      setInput(prev => prev.filter(i => i.idx !== idx))
    } else {
      setInput(prev => [...prev, { letter, idx }])
    }
  }

  const handleSubmit = () => {
    const word = input.map(i => i.letter).join('').toUpperCase()
    if (word.length < 2) return

    if (foundWords.includes(word)) {
      setLastResult('duplicate')
    } else if (validWordSet.has(word)) {
      const match = allWords.find(w => w.word === word)
      setFoundWords(prev => [...prev, word])
      setScore(prev => prev + (match?.points || 10))
      setLastResult('correct')
    } else {
      setLastResult('wrong')
    }
    setInput([])
    setTimeout(() => setLastResult(null), 1200)
  }

  const handleClear = () => setInput([])

  const handleTimeUp = () => {
    setDone(true)
    onAnswer(foundWords.length > 0, foundWords.join(', '))
  }

  // Medal progress
  const medal = foundWords.length >= targets.gold ? 'gold' :
    foundWords.length >= targets.silver ? 'silver' :
    foundWords.length >= targets.bronze ? 'bronze' : 'none'

  return (
    <div className="space-y-4">
      <PuzzleTimer timerStr={puzzle.timer || '2:00'} onTimeUp={handleTimeUp} paused={done} />

      <div className="text-center space-y-2">
        <p className="text-white/50 text-xs uppercase tracking-widest font-semibold">
          Form words using these letters
        </p>
      </div>

      {/* Letter tiles */}
      <div className="flex justify-center gap-3">
        {letters.map((letter, idx) => {
          const isUsed = input.find(i => i.idx === idx)
          return (
            <motion.button
              key={idx}
              whileTap={{ scale: 0.9 }}
              onClick={() => !done && handleLetterTap(letter, idx)}
              className={`w-14 h-14 rounded-xl text-2xl font-bold transition-all border-2 ${
                isUsed
                  ? 'bg-green-500 border-green-300 text-white shadow-lg shadow-green-500/40 scale-110'
                  : 'bg-white/10 border-white/20 text-white hover:bg-white/20'
              }`}
            >
              {letter}
            </motion.button>
          )
        })}
      </div>

      {/* Current word being formed */}
      <div className="flex justify-center items-center gap-2 min-h-[48px]">
        <div className="px-6 py-2 bg-white/5 border border-white/10 rounded-xl min-w-[120px] text-center">
          <span className="text-2xl font-bold text-yellow-300 tracking-widest">
            {input.map(i => i.letter).join('') || '...'}
          </span>
        </div>
        {input.length > 0 && !done && (
          <>
            <button onClick={handleClear} className="px-3 py-2 bg-red-500/20 text-red-300 rounded-lg text-sm">
              Clear
            </button>
            <button onClick={handleSubmit} className="px-4 py-2 bg-green-500 text-white font-bold rounded-lg">
              Submit
            </button>
          </>
        )}
      </div>

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
              'bg-red-500/20 text-red-300'
            }`}
          >
            {lastResult === 'correct' && '✓ Found it!'}
            {lastResult === 'duplicate' && 'Already found!'}
            {lastResult === 'wrong' && 'Not a valid word'}
          </motion.div>
        )}
      </AnimatePresence>

      {/* Progress */}
      <div className="flex justify-between items-center px-2">
        <div className="flex gap-3 text-sm">
          <span className={foundWords.length >= targets.bronze ? 'text-amber-600' : 'text-white/30'}>🥉 {targets.bronze}</span>
          <span className={foundWords.length >= targets.silver ? 'text-gray-300' : 'text-white/30'}>🥈 {targets.silver}</span>
          <span className={foundWords.length >= targets.gold ? 'text-yellow-400' : 'text-white/30'}>🥇 {targets.gold}</span>
        </div>
        <span className="text-yellow-300 font-bold">{score} pts</span>
      </div>

      {/* Found words */}
      <div className="flex flex-wrap gap-2">
        {foundWords.map((word) => {
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

      <p className="text-center text-white/30 text-xs">{foundWords.length} / {totalWords} words found</p>
    </div>
  )
}
