import { motion } from 'framer-motion'
import { useQuery } from '@tanstack/react-query'
import { api } from '../services/api'
import { useAuth } from '../context/AuthContext'
import { Trophy, Medal, Crown, Loader2, RefreshCw } from 'lucide-react'

// Helper to mask email for privacy
function maskEmail(email) {
  if (!email) return 'Anonymous'
  const [name, domain] = email.split('@')
  if (!domain) return email.slice(0, 3) + '***'
  const maskedName = name.slice(0, 2) + '***'
  return `${maskedName}@${domain}`
}

export default function LeaderboardPage() {
  const { user } = useAuth()

  const { data, isLoading, isError, refetch } = useQuery({
    queryKey: ['leaderboard'],
    queryFn: api.getLeaderboard,
    staleTime: 1000 * 60 * 5, // 5 minutes
  })

  const leaderboard = data?.leaderboard || []

  // Find current user's rank
  const userRank = leaderboard.findIndex(
    entry => entry.email === user?.email || entry.userId === user?.uid
  ) + 1

  const getRankIcon = (rank) => {
    switch (rank) {
      case 1:
        return <Crown className="text-yellow-400" size={24} />
      case 2:
        return <Medal className="text-gray-300" size={24} />
      case 3:
        return <Medal className="text-orange-400" size={24} />
      default:
        return <span className="text-white/50 font-bold w-6 text-center">{rank}</span>
    }
  }

  const getRankBg = (rank) => {
    switch (rank) {
      case 1:
        return 'bg-gradient-to-r from-yellow-500/30 to-yellow-600/20 border-yellow-500/50'
      case 2:
        return 'bg-gradient-to-r from-gray-400/20 to-gray-500/10 border-gray-400/50'
      case 3:
        return 'bg-gradient-to-r from-orange-500/20 to-orange-600/10 border-orange-500/50'
      default:
        return 'bg-white/5 border-white/10'
    }
  }

  return (
    <div className="max-w-2xl mx-auto space-y-6">
      {/* Header */}
      <motion.div
        initial={{ opacity: 0, y: -20 }}
        animate={{ opacity: 1, y: 0 }}
        className="text-center"
      >
        <div className="flex items-center justify-center gap-2 mb-2">
          <Trophy className="text-yellow-400" size={32} />
          <h1 className="text-2xl font-bold text-white">Leaderboard</h1>
        </div>
        <p className="text-white/60">Top players this week</p>
      </motion.div>

      {/* User's rank card */}
      {userRank > 0 && (
        <motion.div
          initial={{ opacity: 0, scale: 0.95 }}
          animate={{ opacity: 1, scale: 1 }}
          className="glass rounded-xl p-4"
        >
          <div className="flex items-center justify-between">
            <div className="flex items-center gap-3">
              <div className="w-10 h-10 bg-purple-500 rounded-full flex items-center justify-center">
                {user?.photoURL ? (
                  <img src={user.photoURL} alt="" className="w-full h-full rounded-full" />
                ) : (
                  <span className="text-white font-bold">
                    {user?.email?.[0]?.toUpperCase() || '?'}
                  </span>
                )}
              </div>
              <div>
                <p className="text-white font-medium">Your Rank</p>
                <p className="text-white/60 text-sm">{user?.email}</p>
              </div>
            </div>
            <div className="text-right">
              <p className="text-2xl font-bold text-purple-400">#{userRank}</p>
            </div>
          </div>
        </motion.div>
      )}

      {/* Leaderboard list */}
      <motion.div
        initial={{ opacity: 0, y: 20 }}
        animate={{ opacity: 1, y: 0 }}
        className="glass rounded-2xl p-4 space-y-2"
      >
        {/* Loading state */}
        {isLoading && (
          <div className="flex flex-col items-center justify-center py-12">
            <Loader2 className="animate-spin text-white mb-4" size={40} />
            <p className="text-white/70">Loading leaderboard...</p>
          </div>
        )}

        {/* Error state */}
        {isError && (
          <div className="text-center py-12">
            <p className="text-red-400 mb-4">Failed to load leaderboard</p>
            <button
              onClick={() => refetch()}
              className="flex items-center gap-2 mx-auto text-white bg-white/20 hover:bg-white/30 px-4 py-2 rounded-lg transition-colors"
            >
              <RefreshCw size={18} />
              Try Again
            </button>
          </div>
        )}

        {/* Empty state */}
        {!isLoading && !isError && leaderboard.length === 0 && (
          <div className="text-center py-12">
            <Trophy className="text-white/30 mx-auto mb-4" size={48} />
            <p className="text-white/70">No players yet. Be the first!</p>
          </div>
        )}

        {/* Leaderboard entries */}
        {leaderboard.map((entry, index) => {
          const rank = index + 1
          const isCurrentUser = entry.email === user?.email || entry.userId === user?.uid

          return (
            <motion.div
              key={entry.id || entry.email || index}
              initial={{ opacity: 0, x: -20 }}
              animate={{ opacity: 1, x: 0 }}
              transition={{ delay: index * 0.05 }}
              className={`flex items-center gap-4 p-3 rounded-xl border transition-all ${getRankBg(rank)} ${
                isCurrentUser ? 'ring-2 ring-purple-500' : ''
              }`}
            >
              {/* Rank */}
              <div className="w-8 flex justify-center">
                {getRankIcon(rank)}
              </div>

              {/* Avatar */}
              <div className="w-10 h-10 bg-white/20 rounded-full flex items-center justify-center overflow-hidden">
                {entry.photoURL ? (
                  <img src={entry.photoURL} alt="" className="w-full h-full object-cover" />
                ) : (
                  <span className="text-white font-bold text-lg">
                    {(entry.name || entry.email)?.[0]?.toUpperCase() || '?'}
                  </span>
                )}
              </div>

              {/* Name */}
              <div className="flex-1 min-w-0">
                <p className={`font-medium truncate ${isCurrentUser ? 'text-purple-300' : 'text-white'}`}>
                  {entry.name || maskEmail(entry.email)}
                </p>
                {isCurrentUser && (
                  <p className="text-purple-400 text-xs">You</p>
                )}
              </div>

              {/* Score */}
              <div className="text-right">
                <p className={`font-bold ${rank <= 3 ? 'text-yellow-400' : 'text-white'}`}>
                  {entry.score?.toLocaleString() || 0}
                </p>
                <p className="text-white/50 text-xs">points</p>
              </div>
            </motion.div>
          )
        })}
      </motion.div>

      {/* Refresh button */}
      <div className="text-center">
        <button
          onClick={() => refetch()}
          disabled={isLoading}
          className="flex items-center gap-2 mx-auto text-white/70 hover:text-white transition-colors"
        >
          <RefreshCw size={16} className={isLoading ? 'animate-spin' : ''} />
          <span className="text-sm">Refresh</span>
        </button>
      </div>
    </div>
  )
}
