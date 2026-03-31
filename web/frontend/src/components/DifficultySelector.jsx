import { motion } from 'framer-motion'
import { Lock } from 'lucide-react'

const LEVELS = [
  { level: 1, label: 'Easy', color: 'bg-green-500', textColor: 'text-green-400', cost: 0 },
  { level: 2, label: 'Medium', color: 'bg-yellow-500', textColor: 'text-yellow-400', cost: 5 },
  { level: 3, label: 'Hard', color: 'bg-orange-500', textColor: 'text-orange-400', cost: 10 },
  { level: 4, label: 'Expert', color: 'bg-red-500', textColor: 'text-red-400', cost: 20 },
  { level: 5, label: 'Master', color: 'bg-purple-500', textColor: 'text-purple-400', cost: 50 },
]

export default function DifficultySelector({ currentLevel = 1, onSelect, unlockedLevels = 1 }) {
  return (
    <div className="glass rounded-2xl p-4">
      <p className="text-white/70 text-sm mb-3">Difficulty</p>
      <div className="flex gap-2">
        {LEVELS.map(({ level, label, color, textColor, cost }) => {
          const isUnlocked = level <= unlockedLevels
          const isSelected = level === currentLevel

          return (
            <motion.button
              key={level}
              whileTap={isUnlocked ? { scale: 0.95 } : {}}
              onClick={() => isUnlocked && onSelect(level)}
              className={`relative flex-1 py-2 px-1 rounded-xl text-center transition-all ${
                isSelected
                  ? `${color} text-white shadow-lg`
                  : isUnlocked
                  ? 'bg-white/10 hover:bg-white/15 text-white'
                  : 'bg-white/5 cursor-not-allowed'
              } ${!isUnlocked ? 'opacity-50' : ''}`}
            >
              {/* Lock overlay */}
              {!isUnlocked && (
                <div className="absolute inset-0 flex items-center justify-center">
                  <Lock size={14} className="text-white/40" />
                </div>
              )}

              <span
                className={`text-xs font-semibold block ${
                  !isUnlocked ? 'invisible' : ''
                }`}
              >
                {label}
              </span>

              {/* Coin cost for locked levels */}
              {!isUnlocked && (
                <span className="text-[10px] text-yellow-400/70 block mt-0.5">
                  {cost} coins
                </span>
              )}
            </motion.button>
          )
        })}
      </div>
    </div>
  )
}
