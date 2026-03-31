import { useState } from 'react'
import { motion } from 'framer-motion'
import PuzzleTimer from './PuzzleTimer'
import { parseJsonField } from './puzzleUtils'

const colorMap = {
  red: '#ef4444',
  blue: '#3b82f6',
  green: '#22c55e',
  yellow: '#eab308',
  purple: '#a855f7',
  orange: '#f97316',
  pink: '#ec4899',
  cyan: '#06b6d4',
  white: '#ffffff',
  black: '#1f2937',
  brown: '#92400e',
  gray: '#6b7280',
  grey: '#6b7280',
}

export default function ColorTextMatchingPuzzle({ puzzle, onAnswer }) {
  const parsed = parseJsonField(puzzle.question)
  const answerParsed = parseJsonField(puzzle.answer)

  const items = Array.isArray(parsed?.items) ? parsed.items : (Array.isArray(parsed) ? parsed : [])
  const correctAnswers = Array.isArray(answerParsed?.answers) ? answerParsed.answers : (Array.isArray(answerParsed) ? answerParsed : [])
  const isMultiMode = items.length > 0

  const question = typeof puzzle.question === 'string' ? puzzle.question : ''
  const options = puzzle.options || Object.keys(colorMap).slice(0, 4)
  const correctAnswer = (typeof puzzle.answer === 'string' ? puzzle.answer : '').toLowerCase().trim()

  // All hooks declared unconditionally
  const [selected, setSelected] = useState(null)
  const [revealed, setRevealed] = useState(false)
  const [currentIndex, setCurrentIndex] = useState(0)
  const [multiScore, setMultiScore] = useState(0)
  const [solved, setSolved] = useState(false)

  // --- Single mode handlers ---
  const handleSelectSingle = (option) => {
    if (revealed) return
    setSelected(option)
    setRevealed(true)
    const isCorrect = option.toLowerCase().trim() === correctAnswer
    setTimeout(() => onAnswer(isCorrect, option), 1000)
  }

  const handleTimeUpSingle = () => {
    if (!revealed) {
      setRevealed(true)
      setTimeout(() => onAnswer(false, ''), 800)
    }
  }

  // --- Multi mode handlers ---
  const handleItemAnswer = (answer) => {
    const correct = answer.toLowerCase() === (correctAnswers[currentIndex] || '').toLowerCase()
    const newScore = correct ? multiScore + 1 : multiScore
    if (correct) setMultiScore(newScore)

    if (currentIndex + 1 >= items.length) {
      setSolved(true)
      const passed = newScore >= Math.ceil(items.length / 2)
      setTimeout(() => onAnswer(passed, `${newScore}/${items.length}`), 800)
    } else {
      setCurrentIndex((i) => i + 1)
    }
  }

  const handleTimeUpMulti = () => {
    if (!solved) onAnswer(false, `${multiScore}/${items.length}`)
  }

  // --- Render ---
  if (!isMultiMode) {
    return (
      <div className="space-y-5">
        <PuzzleTimer timerStr={puzzle.timer} onTimeUp={handleTimeUpSingle} paused={revealed} />
        <p className="text-white/50 text-xs uppercase tracking-widest font-semibold text-center">
          Color Match
        </p>
        <div className="bg-white/10 rounded-xl p-5 border border-white/15 text-center">
          <p className="text-white text-lg leading-relaxed">{question}</p>
        </div>
        <div className="grid grid-cols-2 gap-3">
          {options.map((opt, i) => {
            const color = colorMap[opt.toLowerCase()] || '#ffffff'
            const isCorrectOpt = opt.toLowerCase().trim() === correctAnswer
            const isSel = opt === selected
            return (
              <motion.button
                key={i}
                whileTap={!revealed ? { scale: 0.95 } : {}}
                onClick={() => handleSelectSingle(opt)}
                disabled={revealed}
                className={`py-4 rounded-xl border-2 font-bold text-lg transition-all ${
                  revealed
                    ? isCorrectOpt
                      ? 'border-green-400 bg-green-500/20'
                      : isSel
                      ? 'border-red-400 bg-red-500/20'
                      : 'border-white/10 opacity-40'
                    : 'border-white/20 bg-white/10 hover:bg-white/20'
                }`}
                style={{ color }}
              >
                {opt}
              </motion.button>
            )
          })}
        </div>
      </div>
    )
  }

  // Multi-item Stroop mode
  const item = items[currentIndex]

  return (
    <div className="space-y-5">
      <PuzzleTimer timerStr={puzzle.timer || '1:00'} onTimeUp={handleTimeUpMulti} paused={solved} />
      <p className="text-white/50 text-xs uppercase tracking-widest font-semibold text-center">
        Color Match ({currentIndex + 1}/{items.length})
      </p>
      <div className="bg-white/10 rounded-xl p-8 text-center border border-white/15">
        <p
          className="text-4xl font-black"
          style={{ color: colorMap[item?.displayColor?.toLowerCase()] || '#ffffff' }}
        >
          {item?.text || item}
        </p>
      </div>
      <p className="text-white/60 text-sm text-center">What COLOR is the text displayed in?</p>
      <div className="grid grid-cols-2 gap-3">
        {(item?.options || options).map((opt, i) => (
          <motion.button
            key={i}
            whileTap={{ scale: 0.95 }}
            onClick={() => handleItemAnswer(opt)}
            className="py-3 rounded-xl border-2 border-white/20 bg-white/10 hover:bg-white/20 text-white font-bold transition-all"
          >
            {opt}
          </motion.button>
        ))}
      </div>
    </div>
  )
}
