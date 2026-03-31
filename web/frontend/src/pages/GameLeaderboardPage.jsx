import { useParams, useNavigate } from 'react-router-dom'
import { motion } from 'framer-motion'
import { useQuery } from '@tanstack/react-query'
import { api } from '../services/api'
import { ArrowLeft, Trophy, Clock } from 'lucide-react'

const medalColors = ['text-yellow-400', 'text-gray-300', 'text-orange-500']
const medalBgs = ['bg-yellow-500/20', 'bg-gray-400/20', 'bg-orange-500/20']
const medalLabels = ['1st', '2nd', '3rd']

function timeAgo(dateString) {
  if (!dateString) return ''
  const seconds = Math.floor((Date.now() - new Date(dateString).getTime()) / 1000)
  if (seconds < 60) return 'just now'
  if (seconds < 3600) return `${Math.floor(seconds / 60)}m ago`
  if (seconds < 86400) return `${Math.floor(seconds / 3600)}h ago`
  return `${Math.floor(seconds / 86400)}d ago`
}

export default function GameLeaderboardPage() {
  const { gameId } = useParams()
  const navigate = useNavigate()

  // Fetch game details
  const { data: gameData } = useQuery({
    queryKey: ['game', gameId],
    queryFn: () => api.getGameLeaderboard(gameId),
    retry: 1
  })

  // Fetch leaderboard
  const { data: leaderboardData, isError: leaderboardError } = useQuery({
    queryKey: ['gameLeaderboard', gameId],
    queryFn: async () => {
      const response = await fetch(
        `${import.meta.env.DEV ? '' : 'https://puzzleverseai.com'}/api/leaderboard/game/${gameId}`,
        {
          headers: {
            'Content-Type': 'application/json',
            ...(localStorage.getItem('token') && {
              Authorization: `Bearer ${localStorage.getItem('token')}`
            })
          }
        }
      )
      return response.json()
    },
    retry: 1
  })

  const gameTitle = gameData?.title || gameData?.game?.title || 'Game'
  const entries = Array.isArray(leaderboardData?.leaderboard)
    ? leaderboardData.leaderboard
    : Array.isArray(leaderboardData)
    ? leaderboardData
    : []

  const topThree = entries.slice(0, 3)
  const rest = entries.slice(3)

  return (
    <div className="max-w-2xl mx-auto space-y-6 pb-8">
      {/* Header */}
      <motion.div
        initial={{ opacity: 0, y: -20 }}
        animate={{ opacity: 1, y: 0 }}
        className="flex items-center justify-between"
      >
        <button
          onClick={() => navigate(-1)}
          className="flex items-center gap-2 text-white/70 hover:text-white transition-colors"
        >
          <ArrowLeft size={20} />
          <span>Back</span>
        </button>

        <h1 className="text-xl font-bold text-white">{gameTitle}</h1>

        <div className="w-16" /> {/* Spacer */}
      </motion.div>

      {/* Leaderboard Title */}
      <motion.div
        initial={{ opacity: 0, y: 20 }}
        animate={{ opacity: 1, y: 0 }}
        className="glass rounded-2xl p-6 text-center"
      >
        <Trophy className="text-yellow-400 mx-auto mb-2" size={32} />
        <h2 className="text-lg font-bold text-white">Leaderboard</h2>
        <p className="text-white/70 text-sm">{gameTitle}</p>
      </motion.div>

      {/* Leaderboard error / placeholder */}
      {(leaderboardError || entries.length === 0) && (
        <motion.div
          initial={{ opacity: 0, y: 20 }}
          animate={{ opacity: 1, y: 0 }}
          className="glass rounded-2xl p-8 text-center"
        >
          <p className="text-white/70 text-lg">Leaderboard coming soon</p>
          <p className="text-white/50 text-sm mt-2">
            Be the first to set a high score!
          </p>
        </motion.div>
      )}

      {/* Top 3 */}
      {topThree.length > 0 && (
        <div className="grid grid-cols-3 gap-3">
          {topThree.map((entry, index) => (
            <motion.div
              key={entry.userId || index}
              initial={{ opacity: 0, y: 20 }}
              animate={{ opacity: 1, y: 0 }}
              transition={{ delay: index * 0.1 }}
              className={`glass rounded-2xl p-4 text-center ${
                index === 0 ? 'ring-2 ring-yellow-400/50' : ''
              }`}
            >
              <div
                className={`w-10 h-10 mx-auto rounded-full flex items-center justify-center mb-2 ${medalBgs[index]}`}
              >
                <span className={`font-bold ${medalColors[index]}`}>
                  {medalLabels[index]}
                </span>
              </div>
              <p className="text-white font-semibold text-sm truncate">
                {entry.displayName || entry.name || 'Anonymous'}
              </p>
              <p className={`font-bold text-lg ${medalColors[index]}`}>
                {entry.score}
              </p>
              {entry.createdAt && (
                <div className="flex items-center justify-center gap-1 mt-1">
                  <Clock size={10} className="text-white/40" />
                  <span className="text-white/40 text-xs">{timeAgo(entry.createdAt)}</span>
                </div>
              )}
            </motion.div>
          ))}
        </div>
      )}

      {/* Rest of leaderboard */}
      {rest.length > 0 && (
        <motion.div
          initial={{ opacity: 0, y: 20 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ delay: 0.3 }}
          className="glass rounded-2xl divide-y divide-white/10 overflow-hidden"
        >
          {rest.map((entry, index) => (
            <div
              key={entry.userId || index + 3}
              className="flex items-center gap-4 px-6 py-4"
            >
              <span className="text-white/50 font-semibold w-8 text-center">
                {index + 4}
              </span>
              <div className="flex-1 min-w-0">
                <p className="text-white font-medium truncate">
                  {entry.displayName || entry.name || 'Anonymous'}
                </p>
                {entry.createdAt && (
                  <p className="text-white/40 text-xs">{timeAgo(entry.createdAt)}</p>
                )}
              </div>
              <span className="text-purple-300 font-bold">{entry.score}</span>
            </div>
          ))}
        </motion.div>
      )}
    </div>
  )
}
