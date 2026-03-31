import { useState, useEffect, useRef } from 'react'
import { motion, AnimatePresence, Reorder } from 'framer-motion'
import { Eye, EyeOff, Check, Volume2, GripVertical } from 'lucide-react'
import PuzzleTimer from './PuzzleTimer'
import { parseJsonField, shuffle } from './puzzleUtils'

/**
 * MemorySequencingPuzzle — DB type "memorysequencing"
 * Shows items in a narrative (two parts), then user reorders shuffled items chronologically.
 */
export default function MemorySequencingPuzzle({ puzzle, onAnswer }) {
  const parsed = parseJsonField(puzzle.question)
  const answerParsed = parseJsonField(puzzle.answer)

  const topic = parsed?.topic || 'Sequence Memory'
  const description = parsed?.description || ''
  const partOneTitle = parsed?.partOneTitle || 'Part 1'
  const partTwoTitle = parsed?.partTwoTitle || 'Part 2'
  const partOneNarrative = parsed?.partOneNarrative || ''
  const partTwoNarrative = parsed?.partTwoNarrative || ''
  const items = parsed?.items || []
  const shuffledItems = parsed?.shuffledItems || []
  const partOneAudioUrl = parsed?.partOneAudioUrl || null
  const partTwoAudioUrl = parsed?.partTwoAudioUrl || null

  // Correct order from answer
  const correctOrder = Array.isArray(answerParsed)
    ? answerParsed
    : answerParsed?.correctOrder || answerParsed?.sequence || shuffledItems

  const [phase, setPhase] = useState('read') // 'read' | 'reorder'
  const [readPart, setReadPart] = useState(1)
  const [userOrder, setUserOrder] = useState([])
  const [submitted, setSubmitted] = useState(false)
  const [score, setScore] = useState(0)

  useEffect(() => {
    setPhase('read')
    setReadPart(1)
    setSubmitted(false)
    setScore(0)
    // Initialize with shuffled items
    const toShuffle = shuffledItems.length > 0
      ? [...shuffledItems]
      : items.length > 0
      ? shuffle(items.map(i => i.name || i))
      : []
    setUserOrder(toShuffle)
  }, [puzzle.puzzleId])

  const handleFinishReading = () => {
    if (readPart === 1 && partTwoNarrative) {
      setReadPart(2)
    } else {
      setPhase('reorder')
    }
  }

  const handleSubmit = () => {
    if (submitted) return
    setSubmitted(true)

    // Count correct positions
    let correct = 0
    const normalizeStr = (s) => (typeof s === 'string' ? s : String(s)).toLowerCase().trim()
    userOrder.forEach((item, i) => {
      if (i < correctOrder.length && normalizeStr(item) === normalizeStr(correctOrder[i])) {
        correct++
      }
    })

    setScore(correct)
    const total = correctOrder.length || 1
    const passed = correct >= Math.ceil(total * 0.5)
    setTimeout(() => onAnswer(passed, `${correct}/${total} correct`), 1000)
  }

  const handleTimeUp = () => {
    if (phase === 'read') {
      setPhase('reorder')
    } else if (!submitted) {
      handleSubmit()
    }
  }

  const playAudio = (url) => {
    if (url) {
      const audio = new Audio(url)
      audio.play().catch(() => {})
    }
  }

  const moveItem = (fromIndex, direction) => {
    if (submitted) return
    const toIndex = fromIndex + direction
    if (toIndex < 0 || toIndex >= userOrder.length) return
    setUserOrder(prev => {
      const next = [...prev]
      ;[next[fromIndex], next[toIndex]] = [next[toIndex], next[fromIndex]]
      return next
    })
  }

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
            key={`read-${readPart}`}
            initial={{ opacity: 0 }}
            animate={{ opacity: 1 }}
            exit={{ opacity: 0, y: -20 }}
            className="space-y-4"
          >
            <div className="flex items-center gap-2 justify-center">
              <Eye size={18} className="text-purple-400" />
              <p className="text-purple-300 text-sm font-semibold uppercase tracking-widest">
                Read & Memorize
              </p>
            </div>

            <div className="text-center">
              <h3 className="text-white font-bold text-lg">{topic}</h3>
              {description && <p className="text-white/60 text-sm mt-1">{description}</p>}
            </div>

            <div className="bg-amber-500/10 rounded-xl p-4 border border-amber-500/20">
              <h4 className="text-amber-300 font-semibold text-sm mb-2">
                {readPart === 1 ? partOneTitle : partTwoTitle}
              </h4>
              <p className="text-white text-sm leading-relaxed">
                {readPart === 1 ? partOneNarrative : partTwoNarrative}
              </p>
            </div>

            {((readPart === 1 && partOneAudioUrl) || (readPart === 2 && partTwoAudioUrl)) && (
              <div className="flex justify-center">
                <button
                  onClick={() => playAudio(readPart === 1 ? partOneAudioUrl : partTwoAudioUrl)}
                  className="flex items-center gap-2 px-4 py-2 bg-purple-500/30 border border-purple-400/50 rounded-xl text-purple-300 hover:bg-purple-500/40 transition-colors"
                >
                  <Volume2 size={18} />
                  Listen
                </button>
              </div>
            )}

            <button
              onClick={handleFinishReading}
              className="w-full bg-purple-500 hover:bg-purple-600 text-white font-semibold py-3 rounded-xl transition-colors"
            >
              {readPart === 1 && partTwoNarrative ? 'Continue to Part 2' : 'Start Sequencing'}
            </button>
          </motion.div>
        ) : (
          <motion.div
            key="reorder"
            initial={{ opacity: 0, y: 20 }}
            animate={{ opacity: 1, y: 0 }}
            className="space-y-4"
          >
            <div className="flex items-center gap-2 justify-center">
              <EyeOff size={18} className="text-amber-400" />
              <p className="text-amber-300 text-sm font-semibold uppercase tracking-widest">
                Put in Correct Order
              </p>
            </div>

            <p className="text-white/50 text-xs text-center">
              Drag or use arrows to reorder items chronologically
            </p>

            {/* Reorderable list */}
            <div className="space-y-2">
              {userOrder.map((item, index) => {
                const normalizeStr = (s) => (typeof s === 'string' ? s : String(s)).toLowerCase().trim()
                const isCorrectPosition = submitted && index < correctOrder.length &&
                  normalizeStr(item) === normalizeStr(correctOrder[index])

                return (
                  <motion.div
                    key={`${item}-${index}`}
                    layout
                    initial={{ opacity: 0, x: -20 }}
                    animate={{ opacity: 1, x: 0 }}
                    transition={{ delay: index * 0.05 }}
                    className={`flex items-center gap-2 rounded-xl p-3 border-2 transition-all ${
                      submitted
                        ? isCorrectPosition
                          ? 'bg-green-500/20 border-green-400/50'
                          : 'bg-red-500/15 border-red-400/40'
                        : 'bg-white/10 border-white/15 hover:bg-white/15'
                    }`}
                  >
                    {/* Position number */}
                    <span className={`w-7 h-7 rounded-full flex items-center justify-center text-xs font-bold flex-shrink-0 ${
                      submitted
                        ? isCorrectPosition ? 'bg-green-500 text-white' : 'bg-red-500/50 text-white'
                        : 'bg-purple-500/40 text-purple-200'
                    }`}>
                      {index + 1}
                    </span>

                    {/* Item text */}
                    <span className="flex-1 text-white text-sm font-medium">{item}</span>

                    {/* Up/Down buttons */}
                    {!submitted && (
                      <div className="flex flex-col gap-0.5">
                        <button
                          onClick={() => moveItem(index, -1)}
                          disabled={index === 0}
                          className="w-6 h-6 rounded bg-white/10 text-white/50 hover:bg-white/20 disabled:opacity-30 flex items-center justify-center text-xs"
                        >
                          ▲
                        </button>
                        <button
                          onClick={() => moveItem(index, 1)}
                          disabled={index === userOrder.length - 1}
                          className="w-6 h-6 rounded bg-white/10 text-white/50 hover:bg-white/20 disabled:opacity-30 flex items-center justify-center text-xs"
                        >
                          ▼
                        </button>
                      </div>
                    )}
                  </motion.div>
                )
              })}
            </div>

            {/* Correct order reveal on submit */}
            {submitted && (
              <motion.div
                initial={{ opacity: 0 }}
                animate={{ opacity: 1 }}
                className="bg-white/5 rounded-xl p-3 border border-white/10"
              >
                <p className="text-white/50 text-xs mb-2">Correct order:</p>
                {correctOrder.map((item, i) => (
                  <p key={i} className="text-green-300 text-sm">
                    {i + 1}. {item}
                  </p>
                ))}
              </motion.div>
            )}

            {!submitted && (
              <button
                onClick={handleSubmit}
                className="w-full flex items-center justify-center gap-2 bg-green-500 hover:bg-green-600 text-white font-semibold py-3 rounded-xl transition-colors"
              >
                <Check size={20} />
                Submit Order
              </button>
            )}
          </motion.div>
        )}
      </AnimatePresence>
    </div>
  )
}
