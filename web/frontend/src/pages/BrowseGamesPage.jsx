import { useState } from 'react'
import { motion } from 'framer-motion'
import { useQuery } from '@tanstack/react-query'
import { api } from '../services/api'
import { Compass, Play, User, Clock, TrendingUp, Loader2 } from 'lucide-react'

function timeAgo(dateStr) {
  const now = new Date()
  const date = new Date(dateStr)
  const seconds = Math.floor((now - date) / 1000)

  if (seconds < 60) return 'just now'
  if (seconds < 3600) return `${Math.floor(seconds / 60)}m ago`
  if (seconds < 86400) return `${Math.floor(seconds / 3600)}h ago`
  if (seconds < 604800) return `${Math.floor(seconds / 86400)}d ago`
  return date.toLocaleDateString()
}

export default function BrowseGamesPage() {
  const [activeTab, setActiveTab] = useState('newest')

  const { data, isLoading, error } = useQuery({
    queryKey: ['browseGames', activeTab],
    queryFn: () => api.browseGames(activeTab, 20, 0),
    retry: 1,
  })

  const games = data?.games || []

  const handlePlayGame = (gameId) => {
    window.open(`https://puzzleverseai.com/play/${gameId}`, '_blank')
  }

  return (
    <div className="space-y-6 pb-24">
      {/* Header */}
      <motion.div
        initial={{ opacity: 0, y: -20 }}
        animate={{ opacity: 1, y: 0 }}
        className="text-center"
      >
        <div className="inline-flex items-center gap-2 bg-purple-500/20 rounded-full px-4 py-2 mb-3">
          <Compass size={18} className="text-purple-300" />
          <span className="text-purple-300 text-sm font-medium">Community Games</span>
        </div>
        <h1 className="text-2xl font-bold text-white">Browse Games</h1>
        <p className="text-white/70 mt-1">Play games created by the community</p>
      </motion.div>

      {/* Tabs */}
      <motion.div
        initial={{ opacity: 0, y: 20 }}
        animate={{ opacity: 1, y: 0 }}
        transition={{ delay: 0.1 }}
        className="flex gap-2"
      >
        <button
          onClick={() => setActiveTab('newest')}
          className={`flex items-center gap-2 px-5 py-2.5 rounded-xl font-medium text-sm transition-all ${
            activeTab === 'newest'
              ? 'bg-purple-500 text-white'
              : 'bg-white/5 text-white/60 hover:bg-white/10 hover:text-white'
          }`}
        >
          <Clock size={16} />
          Newest
        </button>
        <button
          onClick={() => setActiveTab('popular')}
          className={`flex items-center gap-2 px-5 py-2.5 rounded-xl font-medium text-sm transition-all ${
            activeTab === 'popular'
              ? 'bg-purple-500 text-white'
              : 'bg-white/5 text-white/60 hover:bg-white/10 hover:text-white'
          }`}
        >
          <TrendingUp size={16} />
          Popular
        </button>
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
          <p className="text-red-300">Failed to load games. Please try again.</p>
        </div>
      )}

      {/* Games Grid */}
      {!isLoading && !error && (
        <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-4">
          {games.map((game, index) => (
            <motion.button
              key={game.id}
              initial={{ opacity: 0, y: 20 }}
              animate={{ opacity: 1, y: 0 }}
              transition={{ delay: 0.05 * index }}
              onClick={() => handlePlayGame(game.id)}
              className="glass rounded-2xl p-5 text-left hover:bg-white/15 transition-all group"
            >
              {/* Title */}
              <h3 className="font-semibold text-white text-base leading-tight mb-2 line-clamp-2">
                {game.title}
              </h3>

              {/* Description */}
              {game.description && (
                <p className="text-white/50 text-sm mb-4 line-clamp-2">{game.description}</p>
              )}

              {/* Footer info */}
              <div className="flex items-center justify-between mt-auto">
                <div className="flex items-center gap-1.5 text-white/40 text-xs">
                  <User size={12} />
                  <span>{game.creator_name || 'Anonymous'}</span>
                </div>
                <div className="flex items-center gap-3 text-xs">
                  <span className="flex items-center gap-1 text-white/40">
                    <Play size={12} />
                    {game.play_count || 0}
                  </span>
                  <span className="text-white/30">{timeAgo(game.created_at)}</span>
                </div>
              </div>

              {/* Play button overlay on hover */}
              <div className="mt-3 flex items-center justify-center gap-2 bg-purple-500/20 group-hover:bg-purple-500/40 rounded-xl py-2 transition-all">
                <Play size={16} className="text-purple-300" />
                <span className="text-purple-300 text-sm font-medium">Play</span>
              </div>
            </motion.button>
          ))}
        </div>
      )}

      {/* Empty state */}
      {!isLoading && !error && games.length === 0 && (
        <motion.div
          initial={{ opacity: 0 }}
          animate={{ opacity: 1 }}
          className="glass rounded-2xl p-8 text-center"
        >
          <Compass size={40} className="text-white/20 mx-auto mb-3" />
          <p className="text-white/50">No games found yet. Be the first to create one!</p>
        </motion.div>
      )}
    </div>
  )
}
