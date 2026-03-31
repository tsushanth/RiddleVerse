import { useState, useEffect } from 'react'
import { motion, AnimatePresence } from 'framer-motion'
import { Eye, ChevronRight, Check } from 'lucide-react'
import PuzzleTimer from './PuzzleTimer'
import { parseJsonField } from './puzzleUtils'

/**
 * MemoryPreviousSinglePuzzle — DB type "memoryprevioussingle"
 * Shows a sequence of single values one at a time. After viewing, user answers
 * questions about which value was N steps back (n-back task).
 */
export default function MemoryPreviousSinglePuzzle({ puzzle, onAnswer }) {
  const parsed = parseJsonField(puzzle.question)
  const answerParsed = parseJsonField(puzzle.answer)

  const binarySequence = parsed?.binarySequence || []
  const questionSequence = parsed?.questionSequence || []
  const instructions = parsed?.instructions || {}
  const totalSteps = parsed?.totalSteps || binarySequence.length
  const correctAnswers = answerParsed?.correctAnswers || answerParsed?.answerSequence || []

  const [phase, setPhase] = useState('viewing') // 'viewing' | 'answering'
  const [currentStep, setCurrentStep] = useState(0)
  const [currentAnswer, setCurrentAnswer] = useState(0)
  const [userAnswers, setUserAnswers] = useState([])
  const [input, setInput] = useState('')
  const [submitted, setSubmitted] = useState(false)
  const [showingValue, setShowingValue] = useState(true)

  useEffect(() => {
    setPhase('viewing')
    setCurrentStep(0)
    setCurrentAnswer(0)
    setUserAnswers([])
    setInput('')
    setSubmitted(false)
    setShowingValue(true)
  }, [puzzle.puzzleId])

  // Auto-advance through sequence
  useEffect(() => {
    if (phase !== 'viewing' || currentStep >= totalSteps) return

    setShowingValue(true)
    const timer = setTimeout(() => {
      setShowingValue(false)
      const nextTimer = setTimeout(() => {
        if (currentStep + 1 < totalSteps) {
          setCurrentStep(prev => prev + 1)
        } else {
          setPhase('answering')
        }
      }, 300)
      return () => clearTimeout(nextTimer)
    }, 1500)

    return () => clearTimeout(timer)
  }, [phase, currentStep, totalSteps])

  const handleSkipViewing = () => {
    setPhase('answering')
  }

  const handleAnswerSubmit = (e) => {
    e.preventDefault()
    if (!input.trim()) return

    const newAnswers = [...userAnswers, input.trim()]
    setUserAnswers(newAnswers)
    setInput('')

    if (currentAnswer + 1 < correctAnswers.length) {
      setCurrentAnswer(prev => prev + 1)
    } else {
      setSubmitted(true)
      let correct = 0
      newAnswers.forEach((ans, i) => {
        if (String(ans) === String(correctAnswers[i])) correct++
      })
      const passed = correct >= Math.ceil(correctAnswers.length * 0.5)
      setTimeout(() => onAnswer(passed, `${correct}/${correctAnswers.length}`), 1000)
    }
  }

  const handleTimeUp = () => {
    if (!submitted) {
      setSubmitted(true)
      onAnswer(false, 'timeout')
    }
  }

  const currentValue = binarySequence[currentStep]
  const currentQ = questionSequence[currentAnswer]

  return (
    <div className="space-y-5">
      <PuzzleTimer
        timerStr={phase === 'viewing' ? '0:30' : (puzzle.timer || '1:00')}
        onTimeUp={handleTimeUp}
        paused={submitted}
        key={phase}
      />

      <AnimatePresence mode="wait">
        {phase === 'viewing' ? (
          <motion.div
            key="viewing"
            initial={{ opacity: 0 }}
            animate={{ opacity: 1 }}
            exit={{ opacity: 0 }}
            className="space-y-4"
          >
            <div className="flex items-center gap-2 justify-center">
              <Eye size={18} className="text-purple-400" />
              <p className="text-purple-300 text-sm font-semibold uppercase tracking-widest">
                Watch the Sequence
              </p>
            </div>

            {instructions?.description && (
              <p className="text-white/60 text-sm text-center">{instructions.description}</p>
            )}

            {/* Progress bar */}
            <div className="flex items-center gap-2">
              <div className="flex-1 h-2 bg-white/10 rounded-full overflow-hidden">
                <motion.div
                  className="h-full bg-purple-500 rounded-full"
                  animate={{ width: `${((currentStep + 1) / totalSteps) * 100}%` }}
                />
              </div>
              <span className="text-white/50 text-xs">{currentStep + 1}/{totalSteps}</span>
            </div>

            {/* Current value display */}
            <div className="flex justify-center py-8">
              <AnimatePresence mode="wait">
                {showingValue && (
                  <motion.div
                    key={currentStep}
                    initial={{ opacity: 0, scale: 0.5 }}
                    animate={{ opacity: 1, scale: 1 }}
                    exit={{ opacity: 0, scale: 0.5 }}
                    className="w-24 h-24 rounded-2xl bg-purple-500/30 border-2 border-purple-400/50 flex items-center justify-center"
                  >
                    <span className="text-white font-bold text-4xl">{currentValue}</span>
                  </motion.div>
                )}
              </AnimatePresence>
            </div>

            <button
              onClick={handleSkipViewing}
              className="w-full bg-white/10 hover:bg-white/15 text-white/60 font-medium py-2 rounded-xl transition-colors text-sm"
            >
              Skip to Questions
            </button>
          </motion.div>
        ) : (
          <motion.div
            key="answering"
            initial={{ opacity: 0, y: 20 }}
            animate={{ opacity: 1, y: 0 }}
            className="space-y-4"
          >
            <p className="text-amber-300 text-sm font-semibold uppercase tracking-widest text-center">
              Recall the Values
            </p>

            {!submitted && currentQ && (
              <>
                <div className="bg-amber-500/10 rounded-xl p-4 border border-amber-500/20 text-center">
                  <p className="text-white text-sm">
                    {currentQ.isFirstStep
                      ? `What was the value at step ${currentQ.step}?`
                      : `What value was shown at step ${currentQ.step}?`
                    }
                  </p>
                  {currentQ.value !== undefined && (
                    <p className="text-white/40 text-xs mt-1">
                      Current value shown: {currentQ.value}
                    </p>
                  )}
                </div>

                {/* Previous answers */}
                {userAnswers.length > 0 && (
                  <div className="space-y-1">
                    {userAnswers.map((ans, i) => (
                      <div key={i} className="bg-white/5 rounded-lg px-3 py-2 text-sm text-white/60">
                        Question {i + 1}: <span className="text-white font-bold">{ans}</span>
                      </div>
                    ))}
                  </div>
                )}

                <form onSubmit={handleAnswerSubmit} className="space-y-3">
                  <input
                    type="text"
                    value={input}
                    onChange={(e) => setInput(e.target.value)}
                    placeholder="Enter the value..."
                    className="w-full bg-white/10 border border-white/20 text-white placeholder-white/50 rounded-xl py-3 px-4 focus:outline-none focus:ring-2 focus:ring-purple-400/50 text-center text-xl font-bold"
                    inputMode="numeric"
                    autoFocus
                  />
                  <button
                    type="submit"
                    disabled={!input.trim()}
                    className="w-full flex items-center justify-center gap-2 bg-green-500 hover:bg-green-600 disabled:bg-green-500/50 text-white font-semibold py-3 rounded-xl transition-colors"
                  >
                    <Check size={20} />
                    {currentAnswer + 1 < correctAnswers.length ? 'Next' : 'Submit'}
                  </button>
                </form>
              </>
            )}

            {submitted && (
              <motion.div
                initial={{ opacity: 0 }}
                animate={{ opacity: 1 }}
                className="space-y-2"
              >
                {userAnswers.map((ans, i) => {
                  const correct = String(ans) === String(correctAnswers[i])
                  return (
                    <div key={i} className={`rounded-lg p-3 border text-sm ${
                      correct ? 'bg-green-500/15 border-green-500/30' : 'bg-red-500/15 border-red-500/30'
                    }`}>
                      <p className={correct ? 'text-green-300' : 'text-red-300'}>
                        Q{i + 1}: You said <strong>{ans}</strong>
                        {!correct && <> | Correct: <strong>{correctAnswers[i]}</strong></>}
                      </p>
                    </div>
                  )
                })}
              </motion.div>
            )}
          </motion.div>
        )}
      </AnimatePresence>
    </div>
  )
}
