import { useState, useEffect } from 'react'
import { motion } from 'framer-motion'
import { Check, ArrowRight } from 'lucide-react'
import PuzzleTimer from './PuzzleTimer'
import { parseJsonField, fuzzyMatch } from './puzzleUtils'

const conversions = [
  { from: 'km', to: 'miles', factor: 0.621371, label: 'Kilometers to Miles' },
  { from: 'miles', to: 'km', factor: 1.60934, label: 'Miles to Kilometers' },
  { from: 'kg', to: 'lbs', factor: 2.20462, label: 'Kilograms to Pounds' },
  { from: 'lbs', to: 'kg', factor: 0.453592, label: 'Pounds to Kilograms' },
  { from: 'cm', to: 'inches', factor: 0.393701, label: 'Centimeters to Inches' },
  { from: 'inches', to: 'cm', factor: 2.54, label: 'Inches to Centimeters' },
  { from: 'liters', to: 'gallons', factor: 0.264172, label: 'Liters to Gallons' },
  { from: 'gallons', to: 'liters', factor: 3.78541, label: 'Gallons to Liters' },
  { from: 'meters', to: 'feet', factor: 3.28084, label: 'Meters to Feet' },
  { from: 'feet', to: 'meters', factor: 0.3048, label: 'Feet to Meters' },
  { from: 'celsius', to: 'fahrenheit', factor: null, label: 'Celsius to Fahrenheit' },
  { from: 'fahrenheit', to: 'celsius', factor: null, label: 'Fahrenheit to Celsius' },
]

function generatePuzzle(difficulty = 'medium') {
  const conv = conversions[Math.floor(Math.random() * conversions.length)]
  const max = difficulty === 'easy' ? 50 : difficulty === 'hard' ? 500 : 100
  const value = Math.floor(Math.random() * max) + 1

  let answer
  if (conv.from === 'celsius') {
    answer = (value * 9 / 5) + 32
  } else if (conv.from === 'fahrenheit') {
    answer = (value - 32) * 5 / 9
  } else {
    answer = value * conv.factor
  }

  const rounded = Math.round(answer * 100) / 100

  return {
    puzzleId: `local-conv-${Date.now()}`,
    puzzleType: 'conversion',
    question: JSON.stringify({ value, from: conv.from, to: conv.to, label: conv.label }),
    answer: String(rounded),
    hint: conv.factor ? `1 ${conv.from} = ${conv.factor} ${conv.to}` : null,
    timer: difficulty === 'easy' ? '0:30' : difficulty === 'hard' ? '0:20' : '0:25',
    difficulty,
  }
}

export default function ConversionPuzzle({ puzzle, onAnswer }) {
  const [localPuzzle, setLocalPuzzle] = useState(null)
  const p = localPuzzle || puzzle

  const parsed = parseJsonField(p.question)
  const value = parsed?.value || 0
  const fromUnit = parsed?.from || ''
  const toUnit = parsed?.to || ''
  const label = parsed?.label || `${fromUnit} to ${toUnit}`
  const questionText = typeof p.question === 'string' && !parsed?.value ? p.question : ''
  const correctAnswer = (p.answer || '').toString()

  const [input, setInput] = useState('')
  const [submitted, setSubmitted] = useState(false)
  const [isCorrect, setIsCorrect] = useState(null)
  const [showHint, setShowHint] = useState(false)

  useEffect(() => {
    if (!puzzle.question && !puzzle.answer) {
      setLocalPuzzle(generatePuzzle(puzzle.difficulty || 'medium'))
    }
    setInput('')
    setSubmitted(false)
    setIsCorrect(null)
    setShowHint(false)
  }, [puzzle.puzzleId])

  const handleSubmit = (e) => {
    e.preventDefault()
    if (!input.trim() || submitted) return
    setSubmitted(true)

    // Allow some tolerance for rounding
    const userNum = parseFloat(input.trim())
    const correctNum = parseFloat(correctAnswer)
    const tolerance = Math.abs(correctNum) * 0.05 // 5% tolerance
    const correct = !isNaN(userNum) && !isNaN(correctNum) && Math.abs(userNum - correctNum) <= Math.max(tolerance, 0.5)

    setIsCorrect(correct)
    setTimeout(() => onAnswer(correct, input.trim()), 800)
  }

  const handleTimeUp = () => {
    if (!submitted) {
      setSubmitted(true)
      setIsCorrect(false)
      onAnswer(false, input)
    }
  }

  return (
    <div className="space-y-5">
      <PuzzleTimer timerStr={p.timer || '0:25'} onTimeUp={handleTimeUp} paused={submitted} />

      <p className="text-white/50 text-xs uppercase tracking-widest font-semibold text-center">
        Unit Conversion
      </p>

      {questionText ? (
        <div className="bg-white/10 rounded-2xl p-6 text-center border border-white/15">
          <p className="text-white text-xl font-bold">{questionText}</p>
        </div>
      ) : (
        <div className="bg-white/10 rounded-2xl p-5 border border-white/15">
          <p className="text-white/50 text-xs text-center mb-3">{label}</p>
          <div className="flex items-center justify-center gap-3">
            <div className="text-center">
              <p className="text-white font-bold text-3xl">{value}</p>
              <p className="text-white/50 text-sm">{fromUnit}</p>
            </div>
            <ArrowRight size={24} className="text-purple-400" />
            <div className="text-center">
              <p className="text-purple-400 font-bold text-3xl">?</p>
              <p className="text-white/50 text-sm">{toUnit}</p>
            </div>
          </div>
        </div>
      )}

      {showHint && p.hint && (
        <motion.div
          initial={{ opacity: 0 }}
          animate={{ opacity: 1 }}
          className="bg-yellow-500/20 rounded-xl p-3 border border-yellow-500/30 text-center"
        >
          <p className="text-yellow-100 text-sm">{p.hint}</p>
        </motion.div>
      )}

      {!submitted ? (
        <form onSubmit={handleSubmit} className="space-y-3">
          <input
            type="text"
            value={input}
            onChange={(e) => setInput(e.target.value)}
            placeholder={`Answer in ${toUnit}...`}
            className="w-full bg-white/10 border border-white/20 text-white placeholder-white/50 rounded-xl py-3 px-4 focus:outline-none focus:ring-2 focus:ring-purple-400/50 text-center text-xl font-bold"
            inputMode="decimal"
            autoFocus
          />
          <div className="flex gap-3">
            <button
              type="submit"
              disabled={!input.trim()}
              className="flex-1 flex items-center justify-center gap-2 bg-green-500 hover:bg-green-600 disabled:bg-green-500/50 text-white font-semibold py-3 rounded-xl transition-colors"
            >
              <Check size={20} />
              Submit
            </button>
            {p.hint && !showHint && (
              <button
                type="button"
                onClick={() => setShowHint(true)}
                className="bg-yellow-500/20 hover:bg-yellow-500/30 text-yellow-400 px-4 py-3 rounded-xl transition-colors text-sm"
              >
                Hint
              </button>
            )}
          </div>
        </form>
      ) : (
        <motion.div
          initial={{ scale: 0.8, opacity: 0 }}
          animate={{ scale: 1, opacity: 1 }}
          className={`text-center p-4 rounded-xl ${
            isCorrect ? 'bg-green-500/20 text-green-300' : 'bg-red-500/20 text-red-300'
          }`}
        >
          <p className="font-bold text-lg">
            {isCorrect ? 'Correct!' : `Answer: ${correctAnswer} ${toUnit}`}
          </p>
        </motion.div>
      )}
    </div>
  )
}
