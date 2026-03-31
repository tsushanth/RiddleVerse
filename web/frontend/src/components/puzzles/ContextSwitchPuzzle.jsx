import { useState, useEffect } from 'react'
import { motion } from 'framer-motion'
import PuzzleTimer from './PuzzleTimer'
import { parseJsonField, shuffle } from './puzzleUtils'

const wordBank = [
  { word: 'bank', contexts: [{ meaning: 'Financial institution', sentence: 'I need to go to the ___ to deposit a check.' }, { meaning: 'River edge', sentence: 'We sat on the ___ of the river.' }] },
  { word: 'bat', contexts: [{ meaning: 'Flying mammal', sentence: 'A ___ flew out of the cave at dusk.' }, { meaning: 'Sports equipment', sentence: 'She swung the baseball ___ with precision.' }] },
  { word: 'bark', contexts: [{ meaning: 'Dog sound', sentence: 'The dog\'s ___ woke up the neighbors.' }, { meaning: 'Tree covering', sentence: 'The ___ of the oak tree was rough and textured.' }] },
  { word: 'spring', contexts: [{ meaning: 'Season', sentence: 'Flowers bloom in ___.' }, { meaning: 'Water source', sentence: 'We found a natural ___ in the mountains.' }, { meaning: 'Coiled metal', sentence: 'The ___ in the mattress was broken.' }] },
  { word: 'light', contexts: [{ meaning: 'Not heavy', sentence: 'The bag was surprisingly ___.' }, { meaning: 'Illumination', sentence: 'Turn on the ___ so I can see.' }] },
  { word: 'rock', contexts: [{ meaning: 'Stone', sentence: 'She threw a ___ into the lake.' }, { meaning: 'Music genre', sentence: 'They played ___ music at the concert.' }, { meaning: 'To sway', sentence: 'The boat began to ___ in the waves.' }] },
  { word: 'match', contexts: [{ meaning: 'Fire starter', sentence: 'He lit the candle with a ___.' }, { meaning: 'Competition', sentence: 'The tennis ___ lasted three hours.' }, { meaning: 'To correspond', sentence: 'These socks don\'t ___ each other.' }] },
  { word: 'fly', contexts: [{ meaning: 'Insect', sentence: 'A ___ was buzzing around the kitchen.' }, { meaning: 'To travel by air', sentence: 'We will ___ to Paris tomorrow.' }] },
  { word: 'nail', contexts: [{ meaning: 'Metal fastener', sentence: 'He hammered the ___ into the wall.' }, { meaning: 'Fingertip covering', sentence: 'She painted her ___ a bright red.' }] },
  { word: 'date', contexts: [{ meaning: 'Calendar day', sentence: 'What is today\'s ___?' }, { meaning: 'Fruit', sentence: 'The ___ palm grows in desert climates.' }, { meaning: 'Romantic outing', sentence: 'They went on a ___ to the movies.' }] },
  { word: 'bolt', contexts: [{ meaning: 'Metal fastener', sentence: 'Tighten the ___ with a wrench.' }, { meaning: 'Lightning', sentence: 'A ___ of lightning struck the tree.' }, { meaning: 'To run', sentence: 'The horse began to ___ across the field.' }] },
  { word: 'ring', contexts: [{ meaning: 'Jewelry', sentence: 'He gave her a diamond ___.' }, { meaning: 'Sound', sentence: 'I heard the phone ___.' }, { meaning: 'Boxing area', sentence: 'The fighters entered the ___.' }] },
  { word: 'crane', contexts: [{ meaning: 'Bird', sentence: 'A ___ stood gracefully in the marsh.' }, { meaning: 'Machine', sentence: 'The construction ___ lifted the steel beam.' }] },
  { word: 'seal', contexts: [{ meaning: 'Marine animal', sentence: 'We saw a ___ sunbathing on the rocks.' }, { meaning: 'To close', sentence: '___ the envelope before mailing it.' }] },
  { word: 'cast', contexts: [{ meaning: 'Actors', sentence: 'The ___ of the play took a bow.' }, { meaning: 'To throw', sentence: 'He ___ his fishing line into the water.' }, { meaning: 'Medical wrap', sentence: 'Her broken arm was in a ___.' }] },
]

function generatePuzzle(difficulty = 'medium') {
  const entry = wordBank[Math.floor(Math.random() * wordBank.length)]
  const correctIdx = Math.floor(Math.random() * entry.contexts.length)
  const correct = entry.contexts[correctIdx]

  // Build options: correct meaning + wrong meanings from other words
  const wrongMeanings = []
  const otherWords = wordBank.filter(w => w.word !== entry.word)
  while (wrongMeanings.length < 3) {
    const other = otherWords[Math.floor(Math.random() * otherWords.length)]
    const ctx = other.contexts[Math.floor(Math.random() * other.contexts.length)]
    if (!wrongMeanings.includes(ctx.meaning) && ctx.meaning !== correct.meaning) {
      wrongMeanings.push(ctx.meaning)
    }
  }

  const options = shuffle([correct.meaning, ...wrongMeanings])

  return {
    puzzleId: `local-ctx-${Date.now()}`,
    puzzleType: 'contextswitch',
    question: JSON.stringify({ word: entry.word, sentence: correct.sentence, allContexts: entry.contexts.map(c => c.meaning) }),
    answer: correct.meaning,
    options,
    hint: `The word "${entry.word}" has ${entry.contexts.length} different meanings`,
    timer: difficulty === 'easy' ? '0:30' : difficulty === 'hard' ? '0:15' : '0:20',
    difficulty,
  }
}

export default function ContextSwitchPuzzle({ puzzle, onAnswer }) {
  const [localPuzzle, setLocalPuzzle] = useState(null)
  const p = localPuzzle || puzzle

  const parsed = parseJsonField(p.question)
  const word = parsed?.word || ''
  const sentence = parsed?.sentence || (typeof p.question === 'string' ? p.question : '')
  const correctAnswer = (p.answer || '').toString().trim()
  const options = p.options || []

  const [selected, setSelected] = useState(null)
  const [revealed, setRevealed] = useState(false)

  useEffect(() => {
    if (!puzzle.question && !puzzle.answer) {
      setLocalPuzzle(generatePuzzle(puzzle.difficulty || 'medium'))
    }
    setSelected(null)
    setRevealed(false)
  }, [puzzle.puzzleId])

  const handleSelect = (opt) => {
    if (revealed) return
    setSelected(opt)
    setRevealed(true)
    const isCorrect = opt.toLowerCase().trim() === correctAnswer.toLowerCase()
    setTimeout(() => onAnswer(isCorrect, opt), 1000)
  }

  const handleTimeUp = () => {
    if (!revealed) {
      setRevealed(true)
      onAnswer(false, '')
    }
  }

  return (
    <div className="space-y-5">
      <PuzzleTimer timerStr={p.timer || '0:20'} onTimeUp={handleTimeUp} paused={revealed} />

      <p className="text-white/50 text-xs uppercase tracking-widest font-semibold text-center">
        Context Switch
      </p>

      {/* Word highlight */}
      {word && (
        <div className="text-center">
          <span className="inline-block bg-purple-500/30 border border-purple-400/50 rounded-xl px-6 py-2">
            <span className="text-purple-300 font-bold text-2xl">{word}</span>
          </span>
        </div>
      )}

      {/* Sentence */}
      <div className="bg-white/10 rounded-xl p-4 border border-white/15 text-center">
        <p className="text-white text-base leading-relaxed italic">"{sentence}"</p>
      </div>

      <p className="text-white/40 text-xs text-center">What does the word mean in this context?</p>

      {/* Options */}
      <div className="grid gap-3">
        {options.map((opt, i) => {
          const isCorrectOpt = opt.toLowerCase().trim() === correctAnswer.toLowerCase()
          const isSel = opt === selected
          return (
            <motion.button
              key={i}
              whileTap={!revealed ? { scale: 0.97 } : {}}
              onClick={() => handleSelect(opt)}
              disabled={revealed}
              className={`w-full text-left px-4 py-3.5 rounded-xl border-2 transition-all font-medium ${
                revealed && isCorrectOpt
                  ? 'bg-green-500/30 border-green-400 text-green-300'
                  : revealed && isSel && !isCorrectOpt
                  ? 'bg-red-500/30 border-red-400 text-red-300'
                  : revealed
                  ? 'bg-white/5 border-white/10 text-white/40'
                  : 'bg-white/10 border-white/20 text-white hover:bg-white/20'
              }`}
            >
              <span className="inline-flex items-center gap-3">
                <span className="flex-shrink-0 w-8 h-8 rounded-lg bg-white/10 flex items-center justify-center text-sm font-bold text-white/60">
                  {String.fromCharCode(65 + i)}
                </span>
                <span>{opt}</span>
              </span>
            </motion.button>
          )
        })}
      </div>
    </div>
  )
}
