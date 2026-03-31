import { useState, useEffect, useCallback, useRef } from 'react'
import { motion, AnimatePresence } from 'framer-motion'
import { Eye, EyeOff, Check, Volume2 } from 'lucide-react'
import PuzzleTimer from './PuzzleTimer'
import { parseJsonField } from './puzzleUtils'

export default function MemoryStoryPuzzle({ puzzle, onAnswer }) {
  const parsed = parseJsonField(puzzle.question)

  // Handle DB format: storyCard, question, options, audioUrl
  const story = parsed?.storyCard || parsed?.story || parsed?.text || parsed?.passage || ''
  const recallQuestion = parsed?.question || 'What do you remember?'
  const options = parsed?.options || puzzle.options || []
  const audioUrl = parsed?.audioUrl || null
  const correctAnswers = (() => {
    try {
      const a = parseJsonField(puzzle.answer)
      return Array.isArray(a) ? a.map(x => typeof x === 'string' ? x.toLowerCase().replace(/[^\w\s]/g, '').trim() : '') : []
    } catch { return [] }
  })()

  const [phase, setPhase] = useState('read') // 'read' | 'answer'
  const [selectedOptions, setSelectedOptions] = useState(new Set())
  const [submitted, setSubmitted] = useState(false)
  const audioRef = useRef(null)

  useEffect(() => {
    setPhase('read')
    setSelectedOptions(new Set())
    setSubmitted(false)
  }, [puzzle.puzzleId])

  const handleReadComplete = useCallback(() => {
    setPhase('answer')
    if (audioRef.current) audioRef.current.pause()
  }, [])

  const toggleOption = (opt) => {
    if (submitted) return
    setSelectedOptions(prev => {
      const next = new Set(prev)
      if (next.has(opt)) next.delete(opt)
      else next.add(opt)
      return next
    })
  }

  const handleSubmit = () => {
    if (submitted) return
    setSubmitted(true)

    // Check how many selected match correct answers
    const selectedArr = [...selectedOptions]
    const normalizeAnswer = (s) => s.toLowerCase().replace(/[^\w\s]/g, '').trim()
    const correct = selectedArr.filter(opt =>
      correctAnswers.some(ca => normalizeAnswer(opt).includes(ca) || ca.includes(normalizeAnswer(opt)))
    )
    const isCorrect = correct.length >= correctAnswers.length * 0.5
    onAnswer(isCorrect, selectedArr.join(', '))
  }

  const handleTimeUp = () => {
    if (phase === 'read') {
      setPhase('answer')
    } else if (!submitted) {
      setSubmitted(true)
      onAnswer(false, 'timeout')
    }
  }

  const playAudio = () => {
    // Try audio file first, fall back to text-to-speech
    if (audioRef.current && audioUrl) {
      audioRef.current.play().catch(() => {
        // Audio file failed, use TTS
        speakStory()
      })
    } else {
      speakStory()
    }
  }

  const speakStory = () => {
    if ('speechSynthesis' in window && story) {
      window.speechSynthesis.cancel()
      const utterance = new SpeechSynthesisUtterance(story)
      utterance.rate = 0.9
      window.speechSynthesis.speak(utterance)
    }
  }

  return (
    <div className="space-y-5">
      <PuzzleTimer
        timerStr={phase === 'read' ? '0:20' : (puzzle.timer || '1:00')}
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
                Memorize this story
              </p>
            </div>

            {/* Audio player — uses file if available, TTS fallback */}
            <div className="flex justify-center">
              <button
                onClick={playAudio}
                className="flex items-center gap-2 px-4 py-2 bg-purple-500/30 border border-purple-400/50 rounded-xl text-purple-300 hover:bg-purple-500/40 transition-colors"
              >
                <Volume2 size={18} />
                Listen to Story
              </button>
              {audioUrl && <audio ref={audioRef} src={audioUrl} preload="none" />}
            </div>

            <div className="bg-white/10 rounded-xl p-5 border border-white/20">
              <p className="text-white text-base leading-relaxed">{story}</p>
            </div>

            <p className="text-white/40 text-xs text-center">
              The story will disappear. Read carefully!
            </p>

            <button
              onClick={handleReadComplete}
              className="w-full bg-purple-500 hover:bg-purple-600 text-white font-semibold py-3 rounded-xl transition-colors"
            >
              I'm Ready - Hide the Story
            </button>
          </motion.div>
        ) : (
          <motion.div
            key="answer"
            initial={{ opacity: 0, y: 20 }}
            animate={{ opacity: 1, y: 0 }}
            className="space-y-4"
          >
            <div className="flex items-center gap-2 justify-center">
              <EyeOff size={18} className="text-amber-400" />
              <p className="text-amber-300 text-sm font-semibold uppercase tracking-widest">
                Recall Time
              </p>
            </div>

            <div className="bg-amber-500/10 rounded-xl p-4 border border-amber-500/20 text-center">
              <p className="text-white font-medium">{recallQuestion}</p>
            </div>

            {/* Options (multi-select) */}
            {options.length > 0 ? (
              <div className="space-y-2">
                <p className="text-white/50 text-xs text-center">Select all that apply</p>
                {options.map((opt, i) => {
                  const isSelected = selectedOptions.has(opt)
                  const normalizeAnswer = (s) => s.toLowerCase().replace(/[^\w\s]/g, '').trim()
                  const isCorrectOption = submitted && correctAnswers.some(ca =>
                    normalizeAnswer(opt).includes(ca) || ca.includes(normalizeAnswer(opt))
                  )
                  const isWrongSelection = submitted && isSelected && !isCorrectOption

                  return (
                    <motion.button
                      key={i}
                      whileTap={{ scale: 0.98 }}
                      onClick={() => toggleOption(opt)}
                      className={`w-full text-left px-4 py-3 rounded-xl border-2 transition-all text-sm font-medium ${
                        submitted && isCorrectOption
                          ? 'bg-green-500/30 border-green-400 text-green-200'
                          : submitted && isWrongSelection
                          ? 'bg-red-500/30 border-red-400 text-red-200'
                          : isSelected
                          ? 'bg-green-500 border-green-300 text-white'
                          : 'bg-white/10 border-white/15 text-white/80 hover:bg-white/20'
                      }`}
                      disabled={submitted}
                    >
                      <span className="flex items-center gap-2">
                        <span className={`w-5 h-5 rounded border-2 flex items-center justify-center text-xs ${
                          isSelected ? 'bg-white/20 border-white' : 'border-white/30'
                        }`}>
                          {isSelected && '✓'}
                        </span>
                        {opt}
                      </span>
                    </motion.button>
                  )
                })}
              </div>
            ) : (
              <textarea
                placeholder="Type what you remember..."
                rows={4}
                className="w-full bg-white/10 border border-white/20 text-white placeholder-white/40 rounded-xl py-3 px-4 focus:outline-none focus:ring-2 focus:ring-purple-400/50 text-sm resize-none"
                disabled={submitted}
              />
            )}

            {!submitted && (
              <button
                onClick={handleSubmit}
                disabled={selectedOptions.size === 0}
                className="w-full flex items-center justify-center gap-2 bg-green-500 hover:bg-green-600 disabled:bg-green-500/50 text-white font-semibold py-3 rounded-xl transition-colors"
              >
                <Check size={20} />
                Submit Answer
              </button>
            )}
          </motion.div>
        )}
      </AnimatePresence>
    </div>
  )
}
