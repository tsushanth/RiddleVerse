import { motion } from 'framer-motion'
import { useGameContext } from '../context/GameContext'
import { Lock, Coins, Trophy, Flame, PlusCircle, Sparkles } from 'lucide-react'

const earnMethods = [
  {
    icon: '\u2705',
    label: 'Correct answer',
    amount: '+10',
    color: 'text-green-400',
  },
  {
    icon: '\uD83D\uDCC5',
    label: 'Daily challenge',
    amount: '+25',
    color: 'text-orange-400',
  },
  {
    icon: '\uD83D\uDD25',
    label: '7-day streak',
    amount: '+50',
    color: 'text-red-400',
  },
  {
    icon: '\uD83C\uDFAE',
    label: 'Create a game',
    amount: '+100',
    color: 'text-purple-400',
  },
]

const purchaseOptions = [
  { coins: 100, price: '$0.99' },
  { coins: 500, price: '$3.99' },
  { coins: 1200, price: '$7.99' },
]

export default function CoinShopPage() {
  const { coins, getCoinActivities } = useGameContext()
  const activities = getCoinActivities()

  function formatTime(timestamp) {
    const date = new Date(timestamp)
    const now = new Date()
    const diffMs = now - date
    const diffMin = Math.floor(diffMs / 60000)
    const diffHrs = Math.floor(diffMin / 60)
    const diffDays = Math.floor(diffHrs / 24)

    if (diffMin < 1) return 'Just now'
    if (diffMin < 60) return `${diffMin}m ago`
    if (diffHrs < 24) return `${diffHrs}h ago`
    if (diffDays < 7) return `${diffDays}d ago`
    return date.toLocaleDateString()
  }

  return (
    <div className="max-w-2xl mx-auto space-y-6 pb-8">
      {/* Coin Balance Header */}
      <motion.div
        initial={{ opacity: 0, y: -20 }}
        animate={{ opacity: 1, y: 0 }}
        className="glass rounded-2xl p-8 text-center"
      >
        <motion.span
          className="text-5xl block mb-3"
          animate={{ rotate: [0, -10, 10, -10, 0] }}
          transition={{ duration: 1.5, repeat: Infinity, repeatDelay: 3 }}
        >
          {'\uD83D\uDCB0'}
        </motion.span>
        <motion.p
          className="text-5xl font-bold text-yellow-400"
          key={coins}
          initial={{ scale: 1.2 }}
          animate={{ scale: 1 }}
          transition={{ type: 'spring', stiffness: 300 }}
        >
          {coins.toLocaleString()}
        </motion.p>
        <p className="text-white/50 mt-2 text-sm">Total Coins</p>
      </motion.div>

      {/* How to Earn Coins */}
      <motion.div
        initial={{ opacity: 0, y: 20 }}
        animate={{ opacity: 1, y: 0 }}
        transition={{ delay: 0.1 }}
        className="glass rounded-2xl p-6"
      >
        <h2 className="text-lg font-semibold text-white mb-4 flex items-center gap-2">
          <Sparkles size={20} className="text-yellow-400" />
          How to Earn Coins
        </h2>

        <div className="space-y-3">
          {earnMethods.map((method, index) => (
            <motion.div
              key={method.label}
              initial={{ opacity: 0, x: -20 }}
              animate={{ opacity: 1, x: 0 }}
              transition={{ delay: 0.1 * (index + 1) }}
              className="flex items-center justify-between bg-white/5 rounded-xl p-4"
            >
              <div className="flex items-center gap-3">
                <span className="text-xl">{method.icon}</span>
                <span className="text-white text-sm">{method.label}</span>
              </div>
              <span className={`font-bold text-sm ${method.color}`}>
                {method.amount} {'\uD83D\uDCB0'}
              </span>
            </motion.div>
          ))}
        </div>
      </motion.div>

      {/* Coming Soon: Purchase Coins */}
      <motion.div
        initial={{ opacity: 0, y: 20 }}
        animate={{ opacity: 1, y: 0 }}
        transition={{ delay: 0.2 }}
        className="glass rounded-2xl p-6 opacity-60"
      >
        <div className="flex items-center gap-2 mb-4">
          <Lock size={20} className="text-white/50" />
          <h2 className="text-lg font-semibold text-white/50">Coming Soon: Purchase Coins</h2>
        </div>

        <div className="space-y-3">
          {purchaseOptions.map((option) => (
            <div
              key={option.coins}
              className="flex items-center justify-between bg-white/5 rounded-xl p-4 cursor-not-allowed"
            >
              <div className="flex items-center gap-3">
                <span className="text-xl">{'\uD83D\uDCB0'}</span>
                <span className="text-white/50 font-medium">{option.coins.toLocaleString()} coins</span>
              </div>
              <div className="flex items-center gap-2">
                <span className="text-white/40 font-bold">{option.price}</span>
                <Lock size={14} className="text-white/30" />
              </div>
            </div>
          ))}
        </div>
      </motion.div>

      {/* Recent Activity */}
      <motion.div
        initial={{ opacity: 0, y: 20 }}
        animate={{ opacity: 1, y: 0 }}
        transition={{ delay: 0.3 }}
        className="glass rounded-2xl p-6"
      >
        <h2 className="text-lg font-semibold text-white mb-4 flex items-center gap-2">
          <Coins size={20} className="text-yellow-400" />
          Recent Activity
        </h2>

        {activities.length === 0 ? (
          <p className="text-white/40 text-sm text-center py-4">
            No coin activity yet. Start solving puzzles to earn coins!
          </p>
        ) : (
          <div className="space-y-2 max-h-64 overflow-y-auto">
            {activities.slice(0, 20).map((activity, index) => (
              <div
                key={index}
                className="flex items-center justify-between py-2 px-3 rounded-lg bg-white/5"
              >
                <div className="flex-1 min-w-0">
                  <p className="text-white/70 text-sm truncate">{activity.description}</p>
                  <p className="text-white/30 text-xs">{formatTime(activity.timestamp)}</p>
                </div>
                <span className={`font-bold text-sm ml-3 ${
                  activity.amount > 0 ? 'text-green-400' : 'text-red-400'
                }`}>
                  {activity.amount > 0 ? '+' : ''}{activity.amount}
                </span>
              </div>
            ))}
          </div>
        )}
      </motion.div>
    </div>
  )
}
