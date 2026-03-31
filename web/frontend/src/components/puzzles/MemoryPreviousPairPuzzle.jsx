import { useState, useEffect } from 'react'
import { motion, AnimatePresence } from 'framer-motion'
import { Eye, ChevronRight, Check } from 'lucide-react'
import PuzzleTimer from './PuzzleTimer'
import { parseJsonField } from './puzzleUtils'

/**
 * MemoryPreviousPairPuzzle — DB type "memorypreviouspair"
 * Sequential screens show number pairs. User must identify the linking number
 * between consecutive screens.
 */
export default function MemoryPreviousPairPuzzle({ puzzle, onAnswer }) {
  const parsed = parseJsonField(puzzle.question)
  const answerParsed = parseJsonField(puzzle.answer)

  // Screens are arrays of number pairs
  const screens = parsed?.screens || parsed?.questionSequence || []
  const instructions = parsed?.instructions || {}
  const correctAnswers = answerParsed?.correctAnswers || answerParsed?.answerSequence || []

  const [phase, setPhase] = useState('viewing') // 'viewing' | 'answering'
  const [currentScreen, setCurrentScreen] = useState(0)
  const [viewedScreens, setViewedScreens] = useState([])
  const [currentAnswerIndex, setCurrentAnswerIndex] = useState(0)
  const [userAnswers, setUserAnswers] = useState([])
  const [input, setInput] = useState('')
  const [submitted, setSubmitted] = useState(false)
  const [showResults, setShowResults] = useState(false)

  useEffect(() => {
    setPhase('viewing')
    setCurrentScreen(0)
    setViewedScreens([])
    setCurrentAnswerIndex(0)
    setUserAnswers([])
    setInput('')
    setSubmitted(false)
    setShowResults(false)
  }, [puzzle.puzzleId])

  const handleNextScreen = () => {
    const screenData = screens[currentScreen]
    setViewedScreens(prev => [...prev, screenData])

    if (currentScreen + 1 < screens.length) {
      setCurrentScreen(prev => prev + 1)
    } else {
      // All screens viewed, switch to answering
      setPhase('answering')
    }
  }

  const handleAnswerSubmit = (e) => {
    e.preventDefault()
    if (!input.trim()) return

    const newAnswers = [...userAnswers, input.trim()]
    setUserAnswers(newAnswers)
    setInput('')

    if (currentAnswerIndex + 1 < correctAnswers.length) {
      setCurrentAnswerIndex(prev => prev + 1)
    } else {
      // All answers given
      setSubmitted(true)
      setShowResults(true)

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

  // Extract numbers from screen data
  const getScreenNumbers = (screen) => {
    if (Array.isArray(screen)) return screen
    if (screen?.values) return screen.values
    if (screen?.numbers) return screen.numbers
    if (screen?.value !== undefined) return [screen.value]
    if (typeof screen === 'object') {
      const nums = Object.values(screen).filter(v => typeof v === 'number')
      return nums.length > 0 ? nums : [JSON.stringify(screen)]
    }
    return [screen]
  }

  const currentScreenData = screens[currentScreen]

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
                Memorize the Numbers
              </p>
            </div>

            {instructions?.description && (
              <p className="text-white/60 text-sm text-center">{instructions.description}</p>
            )}

            {/* Progress dots */}
            <div className="flex items-center justify-center gap-2">
              {screens.map((_, i) => (
                <div
                  key={i}
                  className={`w-3 h-3 rounded-full transition-colors ${
                    i < currentScreen ? 'bg-green-500' : i === currentScreen ? 'bg-purple-500' : 'bg-white/20'
                  }`}
                />
              ))}
            </div>

            {/* Current screen */}
            <motion.div
              key={currentScreen}
              initial={{ opacity: 0, scale: 0.9 }}
              animate={{ opacity: 1, scale: 1 }}
              className="bg-white/10 rounded-2xl p-8 border border-white/20 text-center"
            >
              <p className="text-white/40 text-xs mb-3">Screen {currentScreen + 1} of {screens.length}</p>
              <div className="flex flex-wrap items-center justify-center gap-4">
                {getScreenNumbers(currentScreenData).map((num, i) => (
                  <motion.div
                    key={i}
                    initial={{ opacity: 0, y: 10 }}
                    animate={{ opacity: 1, y: 0 }}
                    transition={{ delay: i * 0.15 }}
                    className="w-16 h-16 rounded-xl bg-purple-500/30 border border-purple-400/50 flex items-center justify-center"
                  >
                    <span className="text-white font-bold text-2xl">{num}</span>
                  </motion.div>
                ))}
              </div>
            </motion.div>

            <button
              onClick={handleNextScreen}
              className="w-full flex items-center justify-center gap-2 bg-purple-500 hover:bg-purple-600 text-white font-semibold py-3 rounded-xl transition-colors"
            >
              {currentScreen + 1 < screens.length ? (
                <>Next Screen <ChevronRight size={18} /></>
              ) : (
                <>Start Answering <ChevronRight size={18} /></>
              )}
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
              What was the linking number?
            </p>

            {!submitted && (
              <>
                <div className="bg-amber-500/10 rounded-xl p-4 border border-amber-500/20 text-center">
                  <p className="text-white text-sm">
                    Between screen {currentAnswerIndex + 1} and screen {currentAnswerIndex + 2},
                    what number appeared on both?
                  </p>
                </div>

                {/* Show previous user answers */}
                {userAnswers.length > 0 && (
                  <div className="space-y-1">
                    {userAnswers.map((ans, i) => (
                      <div key={i} className="bg-white/5 rounded-lg px-3 py-2 text-sm text-white/60">
                        Screens {i + 1}-{i + 2}: <span className="text-white font-bold">{ans}</span>
                      </div>
                    ))}
                  </div>
                )}

                <form onSubmit={handleAnswerSubmit} className="space-y-3">
                  <input
                    type="text"
                    value={input}
                    onChange={(e) => setInput(e.target.value)}
                    placeholder="Enter the linking number..."
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
                    {currentAnswerIndex + 1 < correctAnswers.length ? 'Next' : 'Submit'}
                  </button>
                </form>
              </>
            )}

            {showResults && (
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
                        Screens {i + 1}-{i + 2}: You said <strong>{ans}</strong>
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
