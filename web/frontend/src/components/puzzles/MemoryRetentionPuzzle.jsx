import { useState, useEffect, useCallback } from 'react'
import { motion, AnimatePresence } from 'framer-motion'
import { Eye, EyeOff, Check, Volume2 } from 'lucide-react'
import PuzzleTimer from './PuzzleTimer'
import { parseJsonField } from './puzzleUtils'

/**
 * MemoryRetentionPuzzle — DB type "memoryretention"
 * Shows an essay about a topic with subjects, then asks user to assign facts to correct subjects.
 */
export default function MemoryRetentionPuzzle({ puzzle, onAnswer }) {
  const parsed = parseJsonField(puzzle.question)
  const answerParsed = parseJsonField(puzzle.answer)

  const topic = parsed?.topic || 'Memory Retention'
  const essay = parsed?.essay || ''
  const subjects = parsed?.subjects || []
  const facts = parsed?.facts || []
  const audioUrl = parsed?.audioUrl || null
  const answerKey = answerParsed?.answerKey || answerParsed?.factMappings || {}

  const [phase, setPhase] = useState('read') // 'read' | 'assign'
  const [currentFactIndex, setCurrentFactIndex] = useState(0)
  const [assignments, setAssignments] = useState({})
  const [submitted, setSubmitted] = useState(false)
  const [score, setScore] = useState(0)
  const [showResults, setShowResults] = useState(false)

  useEffect(() => {
    setPhase('read')
    setCurrentFactIndex(0)
    setAssignments({})
    setSubmitted(false)
    setScore(0)
    setShowResults(false)
  }, [puzzle.puzzleId])

  const handleReadComplete = useCallback(() => {
    setPhase('assign')
  }, [])

  const handleAssignFact = (factId, subjectId) => {
    if (submitted) return
    setAssignments(prev => ({ ...prev, [factId]: subjectId }))

    // Auto-advance to next unassigned fact
    const nextIndex = facts.findIndex((f, i) => i > currentFactIndex && !assignments[f.id] && f.id !== factId)
    if (nextIndex !== -1) {
      setTimeout(() => setCurrentFactIndex(nextIndex), 300)
    }
  }

  const handleSubmit = () => {
    if (submitted) return
    setSubmitted(true)
    setShowResults(true)

    let correct = 0
    facts.forEach(fact => {
      const userChoice = assignments[fact.id]
      const correctChoice = answerKey[fact.id] || fact.correctSubject
      if (userChoice && userChoice === correctChoice) correct++
    })

    setScore(correct)
    const totalFacts = facts.length || 1
    const passed = correct >= Math.ceil(totalFacts * 0.5)
    setTimeout(() => onAnswer(passed, `${correct}/${totalFacts}`), 1200)
  }

  const handleTimeUp = () => {
    if (phase === 'read') {
      setPhase('assign')
    } else if (!submitted) {
      handleSubmit()
    }
  }

  const playAudio = () => {
    if (audioUrl) {
      const audio = new Audio(audioUrl)
      audio.play().catch(() => speakText())
    } else {
      speakText()
    }
  }

  const speakText = () => {
    if ('speechSynthesis' in window && essay) {
      window.speechSynthesis.cancel()
      const utterance = new SpeechSynthesisUtterance(essay)
      utterance.rate = 0.9
      window.speechSynthesis.speak(utterance)
    }
  }

  const allAssigned = facts.every(f => assignments[f.id])
  const currentFact = facts[currentFactIndex]

  return (
    <div className="space-y-5">
      <PuzzleTimer
        timerStr={phase === 'read' ? '0:30' : (puzzle.timer || '1:30')}
        onTimeUp={handleTimeUp}
        paused={submitted}
        key={phase}
      />

      <AnimatePresence mode="wait">
        {phase === 'read' ? (
          <motion.div
            key="read"
            initial={{ opacity: 0 }}
            animate={{ opacity: 1 }}
            exit={{ opacity: 0, y: -20 }}
            className="space-y-4"
          >
            <div className="flex items-center gap-2 justify-center">
              <Eye size={18} className="text-purple-400" />
              <p className="text-purple-300 text-sm font-semibold uppercase tracking-widest">
                Memorize the Details
              </p>
            </div>

            <div className="text-center">
              <h3 className="text-white font-bold text-lg">{topic}</h3>
            </div>

            {essay && (
              <div className="flex justify-center">
                <button
                  onClick={playAudio}
                  className="flex items-center gap-2 px-4 py-2 bg-purple-500/30 border border-purple-400/50 rounded-xl text-purple-300 hover:bg-purple-500/40 transition-colors"
                >
                  <Volume2 size={18} />
                  Listen
                </button>
              </div>
            )}

            <div className="bg-white/10 rounded-xl p-4 border border-white/20 max-h-64 overflow-y-auto">
              <p className="text-white text-sm leading-relaxed">{essay}</p>
            </div>

            {subjects.length > 0 && (
              <div className="space-y-2">
                <p className="text-white/50 text-xs text-center">Subjects to remember:</p>
                <div className="flex flex-wrap gap-2 justify-center">
                  {subjects.map(sub => (
                    <div key={sub.id} className="bg-purple-500/20 border border-purple-400/30 rounded-lg px-3 py-1.5">
                      <p className="text-purple-200 text-sm font-medium">{sub.name}</p>
                    </div>
                  ))}
                </div>
              </div>
            )}

            <p className="text-white/40 text-xs text-center">
              You will need to assign facts to subjects from memory!
            </p>

            <button
              onClick={handleReadComplete}
              className="w-full bg-purple-500 hover:bg-purple-600 text-white font-semibold py-3 rounded-xl transition-colors"
            >
              I'm Ready - Start Assigning
            </button>
          </motion.div>
        ) : (
          <motion.div
            key="assign"
            initial={{ opacity: 0, y: 20 }}
            animate={{ opacity: 1, y: 0 }}
            className="space-y-4"
          >
            <div className="flex items-center gap-2 justify-center">
              <EyeOff size={18} className="text-amber-400" />
              <p className="text-amber-300 text-sm font-semibold uppercase tracking-widest">
                Assign Facts to Subjects
              </p>
            </div>

            {/* Progress */}
            <div className="flex items-center gap-2">
              <div className="flex-1 h-2 bg-white/10 rounded-full overflow-hidden">
                <motion.div
                  className="h-full bg-purple-500 rounded-full"
                  animate={{ width: `${(Object.keys(assignments).length / Math.max(facts.length, 1)) * 100}%` }}
                />
              </div>
              <span className="text-white/50 text-xs">{Object.keys(assignments).length}/{facts.length}</span>
            </div>

            {/* Current fact */}
            {currentFact && !showResults && (
              <motion.div
                key={currentFact.id}
                initial={{ opacity: 0, x: 20 }}
                animate={{ opacity: 1, x: 0 }}
                className="bg-white/10 rounded-xl p-4 border border-white/20"
              >
                <p className="text-white text-sm">{currentFact.text}</p>
              </motion.div>
            )}

            {/* Subject buttons */}
            {!showResults && currentFact && (
              <div className="space-y-2">
                <p className="text-white/50 text-xs text-center">Which subject does this fact belong to?</p>
                <div className="grid gap-2">
                  {subjects.map(sub => {
                    const isSelected = assignments[currentFact?.id] === sub.id
                    return (
                      <motion.button
                        key={sub.id}
                        whileTap={{ scale: 0.98 }}
                        onClick={() => handleAssignFact(currentFact.id, sub.id)}
                        className={`w-full text-left px-4 py-3 rounded-xl border-2 transition-all text-sm font-medium ${
                          isSelected
                            ? 'bg-purple-500/30 border-purple-400 text-purple-200'
                            : 'bg-white/10 border-white/15 text-white/80 hover:bg-white/20'
                        }`}
                      >
                        {sub.name}
                      </motion.button>
                    )
                  })}
                </div>
              </div>
            )}

            {/* Fact navigation */}
            {!showResults && facts.length > 1 && (
              <div className="flex gap-2 justify-center flex-wrap">
                {facts.map((f, i) => (
                  <button
                    key={f.id}
                    onClick={() => setCurrentFactIndex(i)}
                    className={`w-8 h-8 rounded-full text-xs font-bold transition-colors ${
                      i === currentFactIndex
                        ? 'bg-purple-500 text-white'
                        : assignments[f.id]
                        ? 'bg-green-500/30 text-green-300 border border-green-500/50'
                        : 'bg-white/10 text-white/50'
                    }`}
                  >
                    {i + 1}
                  </button>
                ))}
              </div>
            )}

            {/* Results */}
            {showResults && (
              <motion.div
                initial={{ opacity: 0 }}
                animate={{ opacity: 1 }}
                className="space-y-2"
              >
                {facts.map(fact => {
                  const userChoice = assignments[fact.id]
                  const correctChoice = answerKey[fact.id] || fact.correctSubject
                  const isCorrect = userChoice === correctChoice
                  const subjectName = subjects.find(s => s.id === userChoice)?.name || 'Not assigned'
                  const correctName = subjects.find(s => s.id === correctChoice)?.name || correctChoice

                  return (
                    <div key={fact.id} className={`rounded-lg p-3 border text-sm ${
                      isCorrect ? 'bg-green-500/15 border-green-500/30' : 'bg-red-500/15 border-red-500/30'
                    }`}>
                      <p className="text-white/80">{fact.text}</p>
                      <p className={`text-xs mt-1 ${isCorrect ? 'text-green-400' : 'text-red-400'}`}>
                        {isCorrect ? `Correct: ${correctName}` : `Your: ${subjectName} | Correct: ${correctName}`}
                      </p>
                    </div>
                  )
                })}
              </motion.div>
            )}

            {!submitted && (
              <button
                onClick={handleSubmit}
                disabled={!allAssigned}
                className="w-full flex items-center justify-center gap-2 bg-green-500 hover:bg-green-600 disabled:bg-green-500/50 text-white font-semibold py-3 rounded-xl transition-colors"
              >
                <Check size={20} />
                Submit ({Object.keys(assignments).length}/{facts.length})
              </button>
            )}
          </motion.div>
        )}
      </AnimatePresence>
    </div>
  )
}
