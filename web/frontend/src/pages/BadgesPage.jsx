import { motion } from 'framer-motion'
import { useQuery } from '@tanstack/react-query'
import { api } from '../services/api'
import { Lock } from 'lucide-react'

const BADGE_DEFINITIONS = [
  { id: 'first_puzzle', emoji: '🎯', title: 'First Steps', description: 'Complete your first puzzle' },
  { id: 'streak_7', emoji: '🔥', title: 'Week Warrior', description: '7-day streak' },
  { id: 'streak_30', emoji: '💎', title: 'Monthly Master', description: '30-day streak' },
  { id: 'score_100', emoji: '💯', title: 'Century', description: 'Reach 100 points' },
  { id: 'score_1000', emoji: '🏆', title: 'Grand Master', description: 'Reach 1000 points' },
  { id: 'all_categories', emoji: '🌟', title: 'Well Rounded', description: 'Play all puzzle categories' },
  { id: 'speed_demon', emoji: '⚡', title: 'Speed Demon', description: 'Solve 5 puzzles under 10 seconds' },
  { id: 'perfect_10', emoji: '✨', title: 'Perfect Ten', description: '10 correct answers in a row' },
  { id: 'game_creator', emoji: '🎮', title: 'Game Creator', description: 'Create your first AI game' },
  { id: 'social_butterfly', emoji: '🦋', title: 'Social Butterfly', description: 'Share a score' },
  { id: 'night_owl', emoji: '🦉', title: 'Night Owl', description: 'Play after midnight' },
  { id: 'early_bird', emoji: '🐦', title: 'Early Bird', description: 'Play before 6 AM' },
]

export default function BadgesPage() {
  const { data: achievementsData, isError } = useQuery({
    queryKey: ['achievements'],
    queryFn: api.getAchievements,
    retry: 1
  })

  // Build set of unlocked badge IDs from API response
  const unlockedIds = new Set()
  if (!isError && achievementsData && Array.isArray(achievementsData)) {
    achievementsData.forEach((a) => {
      if (a.unlocked) unlockedIds.add(a.id)
    })
  }

  const badges = BADGE_DEFINITIONS.map((badge) => ({
    ...badge,
    unlocked: unlockedIds.has(badge.id)
  }))

  const unlockedCount = badges.filter((b) => b.unlocked).length

  return (
    <div className="max-w-4xl mx-auto space-y-6 pb-8">
      {/* Header */}
      <motion.div
        initial={{ opacity: 0, y: -20 }}
        animate={{ opacity: 1, y: 0 }}
        className="glass rounded-2xl p-6"
      >
        <h1 className="text-2xl font-bold text-white mb-1">Badges & Achievements</h1>
        <p className="text-white/70">
          {unlockedCount} of {badges.length} unlocked
        </p>
        {/* Progress bar */}
        <div className="mt-3 h-2 bg-white/10 rounded-full overflow-hidden">
          <motion.div
            initial={{ width: 0 }}
            animate={{ width: `${(unlockedCount / badges.length) * 100}%` }}
            transition={{ duration: 0.8, ease: 'easeOut' }}
            className="h-full bg-gradient-to-r from-purple-400 to-orange-400 rounded-full"
          />
        </div>
      </motion.div>

      {/* Badge Grid */}
      <div className="grid grid-cols-2 sm:grid-cols-3 lg:grid-cols-4 gap-4">
        {badges.map((badge, index) => (
          <motion.div
            key={badge.id}
            initial={{ opacity: 0, y: 20 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ delay: index * 0.05 }}
            className={`glass rounded-2xl p-6 text-center relative overflow-hidden transition-all ${
              badge.unlocked
                ? 'shadow-lg shadow-purple-500/20'
                : 'opacity-50 grayscale'
            }`}
          >
            {/* Glow effect for unlocked */}
            {badge.unlocked && (
              <div className="absolute inset-0 bg-gradient-to-br from-purple-400/10 to-orange-400/10 pointer-events-none" />
            )}

            {/* Lock icon overlay for locked */}
            {!badge.unlocked && (
              <div className="absolute top-2 right-2">
                <Lock size={14} className="text-white/40" />
              </div>
            )}

            {/* Emoji */}
            <div className="text-4xl mb-3">{badge.emoji}</div>

            {/* Title */}
            <h3 className="font-semibold text-white text-sm mb-1">{badge.title}</h3>

            {/* Description */}
            <p className="text-white/70 text-xs leading-relaxed">{badge.description}</p>

            {/* Unlocked indicator */}
            {badge.unlocked && (
              <motion.div
                initial={{ scale: 0 }}
                animate={{ scale: 1 }}
                className="mt-3 inline-block bg-green-500/20 text-green-400 text-xs font-medium px-2 py-0.5 rounded-full"
              >
                Unlocked
              </motion.div>
            )}
          </motion.div>
        ))}
      </div>
    </div>
  )
}
