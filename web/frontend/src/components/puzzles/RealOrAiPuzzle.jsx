import { useState } from 'react'
import { motion } from 'framer-motion'
import { Bot, User, Camera } from 'lucide-react'
import PuzzleTimer from './PuzzleTimer'
import { parseJsonField } from './puzzleUtils'

export default function RealOrAiPuzzle({ puzzle, onAnswer }) {
  const parsed = parseJsonField(puzzle.question)

  // Handle "which_is_real" format with two images
  const hasImages = parsed?.imageA?.url && parsed?.imageB?.url
  const description = parsed?.description || parsed?.subjectName || ''

  // Parse the answer — it can be a plain string or JSON with correctAnswer field
  const answerParsed = parseJsonField(puzzle.answer)
  const correctAnswer = (
    (typeof answerParsed === 'object' && answerParsed?.correctAnswer) ||
    (typeof answerParsed === 'string' && answerParsed) ||
    parsed?.answer || ''
  ).toLowerCase().trim()

  const tips = parsed?.instructions?.tips || parsed?.expertClues?.real || answerParsed?.expertClues?.real || []

  const [selected, setSelected] = useState(null)
  const [revealed, setRevealed] = useState(false)

  const handleSelectImage = (choice) => {
    // choice is 'image_a' or 'image_b'
    if (revealed) return
    setSelected(choice)
    setRevealed(true)

    const isCorrect = choice === correctAnswer ||
      choice.replace('_', '') === correctAnswer.replace('_', '')
    setTimeout(() => onAnswer(isCorrect, choice), 1200)
  }

  const handleSelectBinary = (choice) => {
    // choice is 'real' or 'ai'
    if (revealed) return
    setSelected(choice)
    setRevealed(true)

    const isCorrect = choice === correctAnswer ||
      (choice === 'real' && ['real', 'human', 'true', 'image_a', 'imagea'].includes(correctAnswer)) ||
      (choice === 'ai' && ['ai', 'fake', 'false', 'generated', 'image_b', 'imageb'].includes(correctAnswer))
    setTimeout(() => onAnswer(isCorrect, choice), 1000)
  }

  const handleTimeUp = () => {
    if (!revealed) {
      setRevealed(true)
      setTimeout(() => onAnswer(false, ''), 800)
    }
  }

  const getImageBorder = (imageKey) => {
    if (!revealed) return selected === imageKey ? 'ring-4 ring-white' : ''
    const isReal = correctAnswer === imageKey || correctAnswer.replace('_', '') === imageKey.replace('_', '')
    const isSel = selected === imageKey
    if (isReal) return 'ring-4 ring-green-400'
    if (isSel) return 'ring-4 ring-red-400'
    return 'opacity-60'
  }

  // Two-image comparison mode
  if (hasImages) {
    return (
      <div className="space-y-4">
        <PuzzleTimer timerStr={puzzle.timer || '1:30'} onTimeUp={handleTimeUp} paused={revealed} />

        <div className="text-center space-y-1">
          <p className="text-white/50 text-xs uppercase tracking-widest font-semibold">
            Real or AI?
          </p>
          {description && <p className="text-white/80 text-sm font-medium">{description}</p>}
          <p className="text-yellow-300 text-sm font-semibold">Tap the REAL photo</p>
        </div>

        {/* Two images side by side */}
        <div className="grid grid-cols-2 gap-3">
          <motion.button
            whileTap={!revealed ? { scale: 0.97 } : {}}
            onClick={() => handleSelectImage('image_a')}
            disabled={revealed}
            className={`relative rounded-xl overflow-hidden border-2 border-white/20 transition-all ${getImageBorder('image_a')}`}
          >
            <img
              src={parsed.imageA.url}
              alt="Image A"
              className="w-full h-48 sm:h-56 object-cover"
              loading="eager"
            />
            <div className="absolute bottom-0 left-0 right-0 bg-gradient-to-t from-black/70 to-transparent p-2">
              <span className="text-white font-bold text-sm">Image A</span>
            </div>
            {revealed && correctAnswer === 'image_a' && (
              <div className="absolute top-2 right-2 bg-green-500 text-white text-xs font-bold px-2 py-1 rounded-full">
                REAL
              </div>
            )}
            {revealed && correctAnswer !== 'image_a' && (
              <div className="absolute top-2 right-2 bg-purple-500 text-white text-xs font-bold px-2 py-1 rounded-full">
                AI
              </div>
            )}
          </motion.button>

          <motion.button
            whileTap={!revealed ? { scale: 0.97 } : {}}
            onClick={() => handleSelectImage('image_b')}
            disabled={revealed}
            className={`relative rounded-xl overflow-hidden border-2 border-white/20 transition-all ${getImageBorder('image_b')}`}
          >
            <img
              src={parsed.imageB.url}
              alt="Image B"
              className="w-full h-48 sm:h-56 object-cover"
              loading="eager"
            />
            <div className="absolute bottom-0 left-0 right-0 bg-gradient-to-t from-black/70 to-transparent p-2">
              <span className="text-white font-bold text-sm">Image B</span>
            </div>
            {revealed && correctAnswer === 'image_b' && (
              <div className="absolute top-2 right-2 bg-green-500 text-white text-xs font-bold px-2 py-1 rounded-full">
                REAL
              </div>
            )}
            {revealed && correctAnswer !== 'image_b' && (
              <div className="absolute top-2 right-2 bg-purple-500 text-white text-xs font-bold px-2 py-1 rounded-full">
                AI
              </div>
            )}
          </motion.button>
        </div>

        {/* Tips */}
        {!revealed && tips.length > 0 && (
          <div className="bg-white/5 rounded-xl p-3">
            <p className="text-white/40 text-xs mb-1">Tips:</p>
            <ul className="text-white/50 text-xs space-y-0.5">
              {tips.slice(0, 2).map((tip, i) => (
                <li key={i}>• {tip}</li>
              ))}
            </ul>
          </div>
        )}

        {/* Result */}
        {revealed && (
          <motion.div
            initial={{ opacity: 0, y: 10 }}
            animate={{ opacity: 1, y: 0 }}
            className={`text-center py-3 rounded-xl font-semibold ${
              selected === correctAnswer ? 'bg-green-500/20 text-green-300' : 'bg-red-500/20 text-red-300'
            }`}
          >
            {selected === correctAnswer ? '✓ Correct!' : `✗ The real one was ${correctAnswer === 'image_a' ? 'Image A' : 'Image B'}`}
          </motion.div>
        )}
      </div>
    )
  }

  // Fallback: simple binary choice (text-based)
  const textContent = typeof parsed === 'string' ? parsed : (parsed?.text || parsed?.content || JSON.stringify(parsed))

  return (
    <div className="space-y-5">
      <PuzzleTimer timerStr={puzzle.timer || '0:30'} onTimeUp={handleTimeUp} paused={revealed} />

      <p className="text-white/50 text-xs uppercase tracking-widest font-semibold text-center">
        Real or AI?
      </p>

      <div className="bg-white/10 rounded-xl p-5 border border-white/15">
        <p className="text-white text-base leading-relaxed">{textContent}</p>
      </div>

      <div className="grid grid-cols-2 gap-4">
        <motion.button
          whileTap={!revealed ? { scale: 0.95 } : {}}
          onClick={() => handleSelectBinary('real')}
          disabled={revealed}
          className={`flex flex-col items-center gap-3 p-6 rounded-2xl bg-gradient-to-b from-blue-500/20 to-blue-600/10 border-2 border-blue-400/30 transition-all ${
            revealed && ['real','human','true'].includes(correctAnswer) ? 'ring-4 ring-green-400 bg-green-500/30' :
            revealed && selected === 'real' ? 'ring-4 ring-red-400 bg-red-500/30' :
            revealed ? 'opacity-50' : ''
          }`}
        >
          <User size={32} className="text-blue-400" />
          <span className="text-white font-bold text-lg">Real</span>
        </motion.button>

        <motion.button
          whileTap={!revealed ? { scale: 0.95 } : {}}
          onClick={() => handleSelectBinary('ai')}
          disabled={revealed}
          className={`flex flex-col items-center gap-3 p-6 rounded-2xl bg-gradient-to-b from-purple-500/20 to-purple-600/10 border-2 border-purple-400/30 transition-all ${
            revealed && ['ai','fake','false','generated'].includes(correctAnswer) ? 'ring-4 ring-green-400 bg-green-500/30' :
            revealed && selected === 'ai' ? 'ring-4 ring-red-400 bg-red-500/30' :
            revealed ? 'opacity-50' : ''
          }`}
        >
          <Bot size={32} className="text-purple-400" />
          <span className="text-white font-bold text-lg">AI</span>
        </motion.button>
      </div>
    </div>
  )
}
