import { useState, useEffect } from 'react'
import { motion } from 'framer-motion'
import PuzzleTimer from './PuzzleTimer'
import { parseJsonField, shuffle } from './puzzleUtils'

const cardData = [
  { category: 'Countries', pairs: [
    { a: { name: 'Russia', area: '17.1M km²', pop: '144M', continent: 'Europe/Asia' }, b: { name: 'Canada', area: '10M km²', pop: '38M', continent: 'North America' }, questions: [{ q: 'Which has a larger area?', answer: 'Russia' }, { q: 'Which has a larger population?', answer: 'Russia' }] },
    { a: { name: 'India', area: '3.3M km²', pop: '1.4B', continent: 'Asia' }, b: { name: 'Australia', area: '7.7M km²', pop: '26M', continent: 'Oceania' }, questions: [{ q: 'Which has a larger area?', answer: 'Australia' }, { q: 'Which has a larger population?', answer: 'India' }] },
    { a: { name: 'Brazil', area: '8.5M km²', pop: '214M', continent: 'South America' }, b: { name: 'Japan', area: '378K km²', pop: '125M', continent: 'Asia' }, questions: [{ q: 'Which has a larger area?', answer: 'Brazil' }, { q: 'Which has a larger population?', answer: 'Brazil' }] },
  ]},
  { category: 'Animals', pairs: [
    { a: { name: 'Blue Whale', weight: '150 tons', speed: '30 km/h', lifespan: '80-90 years' }, b: { name: 'African Elephant', weight: '6 tons', speed: '40 km/h', lifespan: '60-70 years' }, questions: [{ q: 'Which is heavier?', answer: 'Blue Whale' }, { q: 'Which is faster?', answer: 'African Elephant' }] },
    { a: { name: 'Cheetah', weight: '50 kg', speed: '120 km/h', lifespan: '10-12 years' }, b: { name: 'Lion', weight: '190 kg', speed: '80 km/h', lifespan: '10-14 years' }, questions: [{ q: 'Which is faster?', answer: 'Cheetah' }, { q: 'Which is heavier?', answer: 'Lion' }] },
  ]},
  { category: 'Planets', pairs: [
    { a: { name: 'Jupiter', diameter: '139,820 km', moons: '95', distance: '778M km' }, b: { name: 'Saturn', diameter: '116,460 km', moons: '146', distance: '1.4B km' }, questions: [{ q: 'Which has more moons?', answer: 'Saturn' }, { q: 'Which is larger?', answer: 'Jupiter' }] },
    { a: { name: 'Earth', diameter: '12,742 km', moons: '1', distance: '150M km' }, b: { name: 'Mars', diameter: '6,779 km', moons: '2', distance: '228M km' }, questions: [{ q: 'Which is larger?', answer: 'Earth' }, { q: 'Which has more moons?', answer: 'Mars' }] },
  ]},
]

function generatePuzzle(difficulty = 'medium') {
  const cat = cardData[Math.floor(Math.random() * cardData.length)]
  const pair = cat.pairs[Math.floor(Math.random() * cat.pairs.length)]
  const qObj = pair.questions[Math.floor(Math.random() * pair.questions.length)]

  return {
    puzzleId: `local-dual-${Date.now()}`,
    puzzleType: 'dualcard',
    question: JSON.stringify({ category: cat.category, cardA: pair.a, cardB: pair.b, comparison: qObj.q }),
    answer: qObj.answer,
    options: shuffle([pair.a.name, pair.b.name]),
    timer: difficulty === 'easy' ? '0:30' : difficulty === 'hard' ? '0:15' : '0:20',
    difficulty,
  }
}

function CardDisplay({ card }) {
  const entries = Object.entries(card).filter(([k]) => k !== 'name')
  return (
    <div className="bg-white/10 rounded-xl p-4 border border-white/15 flex-1">
      <h4 className="text-white font-bold text-lg text-center mb-3">{card.name}</h4>
      <div className="space-y-1.5">
        {entries.map(([key, val]) => (
          <div key={key} className="flex justify-between text-sm">
            <span className="text-white/50 capitalize">{key}</span>
            <span className="text-white font-medium">{val}</span>
          </div>
        ))}
      </div>
    </div>
  )
}

export default function DualCardPuzzle({ puzzle, onAnswer }) {
  const [localPuzzle, setLocalPuzzle] = useState(null)
  const p = localPuzzle || puzzle

  const parsed = parseJsonField(p.question)
  const category = parsed?.category || ''
  const cardA = parsed?.cardA || {}
  const cardB = parsed?.cardB || {}
  const comparison = parsed?.comparison || (typeof p.question === 'string' ? p.question : '')
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

      <div className="text-center space-y-1">
        {category && <p className="text-purple-400 text-xs font-semibold uppercase tracking-widest">{category}</p>}
        <p className="text-white/50 text-xs uppercase tracking-widest font-semibold">Dual Card Compare</p>
      </div>

      {/* Cards side by side */}
      <div className="flex gap-3">
        <motion.div initial={{ opacity: 0, x: -20 }} animate={{ opacity: 1, x: 0 }} className="flex-1">
          <CardDisplay card={cardA} />
        </motion.div>
        <div className="flex items-center">
          <span className="text-white/30 font-bold text-xl">VS</span>
        </div>
        <motion.div initial={{ opacity: 0, x: 20 }} animate={{ opacity: 1, x: 0 }} className="flex-1">
          <CardDisplay card={cardB} />
        </motion.div>
      </div>

      {/* Question */}
      <div className="bg-amber-500/10 rounded-xl p-3 border border-amber-500/20 text-center">
        <p className="text-white font-medium">{comparison}</p>
      </div>

      {/* Answer options */}
      <div className="grid grid-cols-2 gap-3">
        {options.map((opt, i) => {
          const isCorrectOpt = opt.toLowerCase().trim() === correctAnswer.toLowerCase()
          const isSel = opt === selected
          return (
            <motion.button
              key={i}
              whileTap={!revealed ? { scale: 0.95 } : {}}
              onClick={() => handleSelect(opt)}
              disabled={revealed}
              className={`py-4 rounded-xl border-2 font-bold text-lg transition-all ${
                revealed && isCorrectOpt
                  ? 'bg-green-500/30 border-green-400 text-green-300'
                  : revealed && isSel && !isCorrectOpt
                  ? 'bg-red-500/30 border-red-400 text-red-300'
                  : revealed
                  ? 'bg-white/5 border-white/10 text-white/30'
                  : 'bg-white/10 border-white/20 text-white hover:bg-white/20'
              }`}
            >
              {opt}
            </motion.button>
          )
        })}
      </div>
    </div>
  )
}
