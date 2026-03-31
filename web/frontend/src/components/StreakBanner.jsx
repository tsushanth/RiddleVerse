import { motion } from 'framer-motion'
import { useGameContext } from '../context/GameContext'

export default function StreakBanner() {
  const { streak, getStreakReward } = useGameContext()
  const nextReward = getStreakReward()

  return (
    <motion.div
      initial={{ opacity: 0, y: 20 }}
      animate={{ opacity: 1, y: 0 }}
      transition={{ delay: 0.05 }}
      className="glass rounded-2xl p-5 relative overflow-hidden"
    >
      {/* Animated fire background glow when streak > 0 */}
      {streak > 0 && (
        <motion.div
          className="absolute inset-0 bg-gradient-to-r from-orange-500/10 via-red-500/10 to-orange-500/10"
          animate={{
            opacity: [0.3, 0.6, 0.3],
          }}
          transition={{
            duration: 2,
            repeat: Infinity,
            ease: 'easeInOut',
          }}
        />
      )}

      <div className="relative flex items-center gap-4">
        {/* Fire icon with animation */}
        <div className="relative">
          <motion.div
            className="w-14 h-14 bg-orange-500/20 rounded-full flex items-center justify-center"
            animate={streak > 0 ? {
              scale: [1, 1.1, 1],
            } : {}}
            transition={{
              duration: 1.5,
              repeat: Infinity,
              ease: 'easeInOut',
            }}
          >
            <span className="text-3xl">{streak > 0 ? '\uD83D\uDD25' : '\u2744\uFE0F'}</span>
          </motion.div>

          {/* Floating fire particles */}
          {streak > 0 && (
            <>
              <motion.span
                className="absolute -top-1 left-1/2 text-xs pointer-events-none"
                animate={{
                  y: [-5, -15],
                  opacity: [1, 0],
                  x: [-3, -8],
                }}
                transition={{
                  duration: 1.2,
                  repeat: Infinity,
                  delay: 0,
                }}
              >
                {'\uD83D\uDD25'}
              </motion.span>
              <motion.span
                className="absolute -top-1 left-1/2 text-xs pointer-events-none"
                animate={{
                  y: [-5, -18],
                  opacity: [1, 0],
                  x: [3, 8],
                }}
                transition={{
                  duration: 1.4,
                  repeat: Infinity,
                  delay: 0.5,
                }}
              >
                {'\uD83D\uDD25'}
              </motion.span>
            </>
          )}
        </div>

        {/* Streak info */}
        <div className="flex-1">
          <div className="flex items-baseline gap-2">
            <motion.span
              className="text-3xl font-bold text-white"
              key={streak}
              initial={{ scale: 1.3, color: '#fb923c' }}
              animate={{ scale: 1, color: '#ffffff' }}
              transition={{ duration: 0.4 }}
            >
              {streak}
            </motion.span>
            <span className="text-white/50 text-sm">
              day{streak !== 1 ? 's' : ''} streak
            </span>
          </div>

          {/* Next milestone */}
          {nextReward ? (
            <div className="mt-1.5">
              <p className="text-orange-300 text-sm font-medium">
                {nextReward.daysRemaining} more day{nextReward.daysRemaining !== 1 ? 's' : ''} to {nextReward.milestone}-day reward!
              </p>
              <p className="text-yellow-400/70 text-xs mt-0.5">
                {'\uD83D\uDCB0'} +{nextReward.reward} coins
              </p>
            </div>
          ) : (
            <p className="text-orange-300 text-sm font-medium mt-1">
              Maximum streak reached! Amazing!
            </p>
          )}
        </div>

        {/* Streak badge */}
        {streak >= 7 && (
          <div className="bg-orange-500/20 rounded-xl px-3 py-2 text-center">
            <span className="text-orange-400 text-xs font-medium block">
              {streak >= 100 ? 'Legendary' : streak >= 50 ? 'Unstoppable' : streak >= 30 ? 'On Fire' : streak >= 14 ? 'Blazing' : 'Hot'}
            </span>
          </div>
        )}
      </div>
    </motion.div>
  )
}
