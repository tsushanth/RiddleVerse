import { useState, useEffect } from 'react'
import { motion } from 'framer-motion'
import { Check, CreditCard } from 'lucide-react'
import PuzzleTimer from './PuzzleTimer'
import { parseJsonField, shuffle } from './puzzleUtils'

const services = ['StreamFlix', 'MusicPro', 'CloudStore', 'FitTrack', 'NewsDaily', 'GamePass', 'LearnHub', 'PhotoEdit']

function generatePuzzle(difficulty = 'medium') {
  const planCount = difficulty === 'easy' ? 2 : difficulty === 'hard' ? 4 : 3
  const plans = []

  const service = services[Math.floor(Math.random() * services.length)]

  for (let i = 0; i < planCount; i++) {
    const monthly = Math.floor(Math.random() * 20) + 5
    const period = [1, 3, 6, 12][Math.floor(Math.random() * 4)]
    const discount = period > 1 ? Math.floor(Math.random() * 30) + 5 : 0
    const totalCost = monthly * period * (1 - discount / 100)
    const perMonth = totalCost / period

    plans.push({
      name: `${period === 1 ? 'Monthly' : period === 3 ? 'Quarterly' : period === 6 ? 'Semi-Annual' : 'Annual'}`,
      monthlyRate: monthly,
      period,
      discountPercent: discount,
      totalCost: Math.round(totalCost * 100) / 100,
      effectiveMonthly: Math.round(perMonth * 100) / 100,
    })
  }

  // Best value = lowest effective monthly rate
  const bestPlan = plans.reduce((best, p) => p.effectiveMonthly < best.effectiveMonthly ? p : best)

  return {
    puzzleId: `local-sub-${Date.now()}`,
    puzzleType: 'subscription',
    question: JSON.stringify({ service, plans }),
    answer: bestPlan.name,
    options: shuffle(plans.map(p => p.name)),
    hint: 'Compare the effective monthly cost of each plan',
    timer: difficulty === 'easy' ? '1:00' : difficulty === 'hard' ? '0:40' : '0:45',
    difficulty,
  }
}

export default function SubscriptionPuzzle({ puzzle, onAnswer }) {
  const [localPuzzle, setLocalPuzzle] = useState(null)
  const p = localPuzzle || puzzle

  const parsed = parseJsonField(p.question)
  const service = parsed?.service || 'Service'
  const plans = parsed?.plans || []
  const correctAnswer = (p.answer || '').toString().trim()
  const options = p.options || plans.map(pl => pl.name)

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
      <PuzzleTimer timerStr={p.timer || '0:45'} onTimeUp={handleTimeUp} paused={revealed} />

      <div className="flex items-center gap-2 justify-center">
        <CreditCard size={18} className="text-purple-400" />
        <p className="text-white/50 text-xs uppercase tracking-widest font-semibold">Best Value</p>
      </div>

      <p className="text-white text-center text-lg font-medium">
        Which <span className="text-purple-400 font-bold">{service}</span> plan is the best value?
      </p>

      {/* Plan cards */}
      <div className="space-y-3">
        {plans.map((plan, i) => (
          <motion.div
            key={i}
            initial={{ opacity: 0, x: -20 }}
            animate={{ opacity: 1, x: 0 }}
            transition={{ delay: i * 0.1 }}
            className="bg-white/10 rounded-xl p-4 border border-white/15"
          >
            <div className="flex items-center justify-between">
              <div>
                <h4 className="text-white font-bold text-sm">{plan.name}</h4>
                <p className="text-white/50 text-xs">
                  ${plan.monthlyRate}/mo base x {plan.period} month{plan.period > 1 ? 's' : ''}
                </p>
              </div>
              <div className="text-right">
                {plan.discountPercent > 0 && (
                  <span className="text-green-400 text-xs font-bold">-{plan.discountPercent}%</span>
                )}
                <p className="text-white font-bold">${plan.totalCost}</p>
                <p className="text-white/40 text-xs">${plan.effectiveMonthly}/mo effective</p>
              </div>
            </div>
          </motion.div>
        ))}
      </div>

      {/* Options */}
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
              className={`py-3 rounded-xl border-2 font-semibold text-sm transition-all ${
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
