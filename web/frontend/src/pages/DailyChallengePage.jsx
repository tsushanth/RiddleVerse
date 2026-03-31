import { useNavigate } from 'react-router-dom'
import { motion } from 'framer-motion'
import { useQuery } from '@tanstack/react-query'
import { api } from '../services/api'
import { Calendar, Flame, Trophy, Loader2, ChevronRight } from 'lucide-react'

const puzzleTypeColors = {
  trivia: '#f97316',
  riddle: '#8b5cf6',
  math: '#3b82f6',
  anagram: '#ec4899',
  synonym: '#22c55e',
  antonym: '#f43f5e',
  wordprefix: '#14b8a6',
  crypto: '#eab308',
  default: '#6366f1',
}

function getPuzzleColor(type) {
  return puzzleTypeColors[type] || puzzleTypeColors.default
}

export default function DailyChallengePage() {
  const navigate = useNavigate()

  const { data, isLoading, error } = useQuery({
    queryKey: ['dailyPuzzles'],
    queryFn: api.getDailyPuzzles,
    retry: 1,
  })

  const puzzles = data?.puzzles || data?.dailyPuzzles || []
  const streak = data?.streak || data?.currentStreak || 0

  return (
    <div className="space-y-6 pb-24">
      {/* Header */}
      <motion.div
        initial={{ opacity: 0, y: -20 }}
        animate={{ opacity: 1, y: 0 }}
        className="text-center"
      >
        <div className="inline-flex items-center gap-2 bg-orange-500/20 rounded-full px-4 py-2 mb-3">
          <Calendar size={18} className="text-orange-300" />
          <span className="text-orange-300 text-sm font-medium">Daily Challenge</span>
        </div>
        <h1 className="text-2xl font-bold text-white">Today's Puzzles</h1>
        <p className="text-white/70 mt-1">Complete all challenges for bonus rewards</p>
      </motion.div>

      {/* Streak Card */}
      <motion.div
        initial={{ opacity: 0, y: 20 }}
        animate={{ opacity: 1, y: 0 }}
        transition={{ delay: 0.1 }}
        className="glass rounded-2xl p-6"
      >
        <div className="flex items-center justify-between">
          <div className="flex items-center gap-4">
            <div className="w-14 h-14 bg-orange-500/20 rounded-full flex items-center justify-center">
              <Flame size={28} className="text-orange-400" />
            </div>
            <div>
              <p className="text-white/50 text-sm">Current Streak</p>
              <p className="text-3xl font-bold text-white">{streak}</p>
              <p className="text-white/40 text-xs">
                {streak === 1 ? 'day' : 'days'} in a row
              </p>
            </div>
          </div>
          <div className="flex items-center gap-2 bg-orange-500/10 rounded-xl px-4 py-2">
            <Trophy size={18} className="text-orange-400" />
            <span className="text-orange-300 text-sm font-medium">
              {streak >= 7 ? 'On Fire!' : streak >= 3 ? 'Great!' : 'Keep going!'}
            </span>
          </div>
        </div>
      </motion.div>

      {/* Loading */}
      {isLoading && (
        <div className="flex items-center justify-center py-16">
          <Loader2 size={32} className="text-purple-400 animate-spin" />
        </div>
      )}

      {/* Error */}
      {error && (
        <div className="glass rounded-2xl p-6 text-center">
          <p className="text-red-300">Failed to load daily puzzles. Please try again.</p>
        </div>
      )}

      {/* Puzzle Cards */}
      {!isLoading && !error && (
        <div className="space-y-3">
          <h2 className="text-lg font-semibold text-white px-1">Challenges</h2>
          {puzzles.map((puzzle, index) => {
            const color = getPuzzleColor(puzzle.type)
            const isCompleted = puzzle.completed || puzzle.isCompleted

            return (
              <motion.button
                key={puzzle.id || puzzle.type || index}
                initial={{ opacity: 0, x: -20 }}
                animate={{ opacity: 1, x: 0 }}
                transition={{ delay: 0.1 * (index + 1) }}
                onClick={() => navigate(`/puzzle/${puzzle.type}`)}
                className="w-full glass rounded-2xl p-5 text-left hover:bg-white/15 transition-all flex items-center gap-4 group"
              >
                {/* Color indicator */}
                <div
                  className="w-12 h-12 rounded-xl flex items-center justify-center shrink-0"
                  style={{ backgroundColor: `${color}30` }}
                >
                  <div
                    className="w-3 h-3 rounded-full"
                    style={{ backgroundColor: isCompleted ? '#22c55e' : color }}
                  />
                </div>

                {/* Info */}
                <div className="flex-1 min-w-0">
                  <h3 className="font-semibold text-white text-base">
                    {puzzle.title || puzzle.name || puzzle.type}
                  </h3>
                  {puzzle.description && (
                    <p className="text-white/50 text-sm mt-0.5 truncate">{puzzle.description}</p>
                  )}
                  {isCompleted && (
                    <span className="text-green-400 text-xs font-medium">Completed</span>
                  )}
                </div>

                {/* Arrow */}
                <ChevronRight
                  size={20}
                  className="text-white/30 group-hover:text-white/60 transition-colors shrink-0"
                />
              </motion.button>
            )
          })}
        </div>
      )}

      {/* Empty state */}
      {!isLoading && !error && puzzles.length === 0 && (
        <motion.div
          initial={{ opacity: 0 }}
          animate={{ opacity: 1 }}
          className="glass rounded-2xl p-8 text-center"
        >
          <Calendar size={40} className="text-white/20 mx-auto mb-3" />
          <p className="text-white/50">No daily puzzles available right now. Check back soon!</p>
        </motion.div>
      )}
    </div>
  )
}
