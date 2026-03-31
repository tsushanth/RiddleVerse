import { useNavigate } from 'react-router-dom'
import { motion } from 'framer-motion'
import { useAuth } from '../context/AuthContext'
import { useGameContext } from '../context/GameContext'
import { useQuery } from '@tanstack/react-query'
import { api } from '../services/api'
import StreakBanner from '../components/StreakBanner'
import {
  Brain, Lightbulb, BookOpen, Calculator, Globe, Puzzle,
  MessageSquare, Hash, Shuffle, Timer, Zap, Target,
  Grid3X3, Languages, Map, Search, Key, Layers,
  Minus, Divide, Percent, BarChart3, Tag, Receipt,
  CreditCard, ArrowLeftRight, Eye, Image, ListOrdered,
  Shapes, SquareStack, Triangle, FlipHorizontal, RefreshCw,
  SplitSquareHorizontal, CopySlash
} from 'lucide-react'

// Puzzle category definitions
const puzzleCategories = [
  {
    id: 'word',
    title: 'Word Puzzles',
    puzzles: [
      { type: 'anagram', name: 'Anagram', icon: Shuffle, color: '#ec4899', description: 'Unscramble the letters' },
      { type: 'letterset', name: 'Letter Set', icon: Grid3X3, color: '#3b82f6', description: 'Form words from letters' },
      { type: 'wordsnake', name: 'Word Snake', icon: Layers, color: '#eab308', description: 'Trace words on a grid' },
      { type: 'wordsearch', name: 'Word Search', icon: Search, color: '#14b8a6', description: 'Find hidden words' },
      { type: 'wordprefix', name: 'Word Prefix', icon: Key, color: '#22c55e', description: 'Find words with prefix' },
      { type: 'synonyms', name: 'Synonyms', icon: Languages, color: '#8b5cf6', description: 'Group similar words' },
      { type: 'antonyms', name: 'Antonyms', icon: Zap, color: '#f97316', description: 'Match opposite words' },
      { type: 'crossword', name: 'Crossword', icon: Hash, color: '#6366f1', description: 'Classic crossword puzzle' },
      { type: 'sentencetransitions', name: 'Transitions', icon: MessageSquare, color: '#f43f5e', description: 'Complete the sentence' },
      { type: 'cryptoword', name: 'Crypto Word', icon: Key, color: '#0ea5e9', description: 'Decode the cipher' },
      { type: 'contextswitch', name: 'Context Switch', icon: RefreshCw, color: '#7c3aed', description: 'Pick the right meaning' },
      { type: 'progressivereveal', name: 'Progressive', icon: Eye, color: '#d946ef', description: 'Guess from clues' },
    ]
  },
  {
    id: 'math',
    title: 'Math & Logic',
    puzzles: [
      { type: 'trivia', name: 'Trivia', icon: Lightbulb, color: '#f97316', description: 'Test your knowledge' },
      { type: 'mathexpression', name: 'Math', icon: Calculator, color: '#3b82f6', description: 'Solve expressions' },
      { type: 'numbersequence', name: 'Sequences', icon: ListOrdered, color: '#0ea5e9', description: 'Find the pattern' },
      { type: 'numbersum', name: 'Number Sum', icon: Target, color: '#22c55e', description: 'Find numbers that sum' },
      { type: 'mathcomparison', name: 'Compare', icon: SplitSquareHorizontal, color: '#8b5cf6', description: 'Greater, less, or equal' },
      { type: 'mathestimation', name: 'Estimation', icon: BarChart3, color: '#f59e0b', description: 'Estimate the result' },
      { type: 'percentage', name: 'Percentage', icon: Percent, color: '#ef4444', description: 'Calculate percentages' },
      { type: 'average', name: 'Average', icon: BarChart3, color: '#06b6d4', description: 'Find the average' },
      { type: 'division', name: 'Division', icon: Divide, color: '#a855f7', description: 'Division problems' },
      { type: 'subtraction', name: 'Subtraction', icon: Minus, color: '#ec4899', description: 'Subtraction problems' },
      { type: 'discountprice', name: 'Discounts', icon: Tag, color: '#10b981', description: 'Calculate sale prices' },
      { type: 'tipbubble', name: 'Tip Calc', icon: Receipt, color: '#f97316', description: 'Calculate tips' },
      { type: 'subscription', name: 'Best Value', icon: CreditCard, color: '#6366f1', description: 'Compare plans' },
      { type: 'conversion', name: 'Conversion', icon: ArrowLeftRight, color: '#14b8a6', description: 'Convert units' },
      { type: 'dualcard', name: 'Dual Card', icon: CopySlash, color: '#f43f5e', description: 'Compare two cards' },
    ]
  },
  {
    id: 'memory',
    title: 'Memory & Focus',
    puzzles: [
      { type: 'memorystory', name: 'Story Memory', icon: BookOpen, color: '#22c55e', description: 'Remember story details' },
      { type: 'memoryretention', name: 'Retention', icon: Brain, color: '#8b5cf6', description: 'Assign facts to topics' },
      { type: 'memorypreviouspair', name: 'Pair Link', icon: Puzzle, color: '#f97316', description: 'Find linking numbers' },
      { type: 'memorysequencing', name: 'Sequencing', icon: ListOrdered, color: '#0ea5e9', description: 'Order items correctly' },
      { type: 'memoryprevioussingle', name: 'N-Back', icon: Brain, color: '#ec4899', description: 'Recall previous values' },
      { type: 'uniqueobject', name: 'Unique Object', icon: Zap, color: '#3b82f6', description: 'Find the unique item' },
      { type: 'colortextmatching', name: 'Color Match', icon: Shapes, color: '#ef4444', description: 'Stroop color test' },
    ]
  },
  {
    id: 'visual',
    title: 'Visual & Spatial',
    puzzles: [
      { type: 'imagepuzzle', name: 'Image Puzzle', icon: Image, color: '#f97316', description: 'Assemble the image' },
      { type: 'colorshapematching', name: 'Shape Match', icon: Shapes, color: '#8b5cf6', description: 'Match colored shapes' },
      { type: 'memorysquares', name: 'Grid Memory', icon: SquareStack, color: '#3b82f6', description: 'Recreate grid patterns' },
      { type: 'triangledotmemory', name: 'Dot Memory', icon: Triangle, color: '#22c55e', description: 'Memorize dot positions' },
      { type: 'symmetry', name: 'Symmetry', icon: FlipHorizontal, color: '#06b6d4', description: 'Find the mirror image' },
      { type: 'realorai', name: 'Real or AI?', icon: Globe, color: '#f43f5e', description: 'Spot AI content' },
    ]
  },
]

function getGreeting() {
  const hour = new Date().getHours()
  if (hour >= 5 && hour < 12) return 'Good Morning'
  if (hour >= 12 && hour < 17) return 'Good Afternoon'
  if (hour >= 17 && hour < 21) return 'Good Evening'
  return 'Good Night'
}

function getGreetingEmoji() {
  const hour = new Date().getHours()
  if (hour >= 5 && hour < 12) return '☀️'
  if (hour >= 12 && hour < 17) return '⛅'
  if (hour >= 17 && hour < 21) return '🌇'
  return '🌙'
}

export default function HomePage() {
  const { user } = useAuth()
  const { coins } = useGameContext()
  const navigate = useNavigate()

  // Fetch user score
  const { data: scoreData } = useQuery({
    queryKey: ['userScore'],
    queryFn: api.getUserScore,
    retry: 1
  })

  const handlePuzzleSelect = (puzzleType) => {
    navigate(`/puzzle/${puzzleType}`)
  }

  return (
    <div className="space-y-6 pb-8">
      {/* Header Section */}
      <motion.div
        initial={{ opacity: 0, y: -20 }}
        animate={{ opacity: 1, y: 0 }}
        className="glass rounded-2xl p-6"
      >
        <div className="flex items-center gap-4">
          {/* Star icon */}
          <div className="text-3xl text-gray-300">⭐</div>

          <div className="flex-1">
            <p className="text-white/70 text-sm">
              {getGreeting()} {getGreetingEmoji()}
            </p>
            <h1 className="text-xl font-bold text-white">
              {user?.displayName || user?.email?.split('@')[0] || 'Player'}
            </h1>
            <div className="flex items-center gap-2 mt-1">
              <span className="text-lg">🏅</span>
              <span className="text-purple-300 font-semibold">
                Score: {scoreData?.score || localStorage.getItem('userScore') || 0}
              </span>
            </div>
          </div>

          {/* Coins display */}
          <div className="flex items-center gap-2 bg-black/20 rounded-full px-4 py-2">
            <span className="text-yellow-400">💰</span>
            <span className="text-yellow-400 font-bold">
              {coins}
            </span>
          </div>
        </div>
      </motion.div>

      {/* Streak Banner */}
      <StreakBanner />

      {/* Quick Actions */}
      <motion.div
        initial={{ opacity: 0, y: 20 }}
        animate={{ opacity: 1, y: 0 }}
        transition={{ delay: 0.1 }}
        className="grid grid-cols-2 gap-3"
      >
        <button
          onClick={() => navigate('/browse')}
          className="glass rounded-2xl p-4 text-left hover:bg-white/20 transition-all"
        >
          <div className="w-10 h-10 bg-cyan-500/20 rounded-xl flex items-center justify-center mb-2">
            <Puzzle size={20} className="text-cyan-400" />
          </div>
          <p className="text-white font-semibold text-sm">Browse Games</p>
          <p className="text-white/50 text-xs">Play community games</p>
        </button>

        <button
          onClick={() => navigate('/create')}
          className="glass rounded-2xl p-4 text-left hover:bg-white/20 transition-all"
        >
          <div className="w-10 h-10 bg-green-500/20 rounded-xl flex items-center justify-center mb-2">
            <Zap size={20} className="text-green-400" />
          </div>
          <p className="text-white font-semibold text-sm">Create Game</p>
          <p className="text-white/50 text-xs">Build with AI</p>
        </button>
      </motion.div>

      {/* Daily Challenge Banner */}
      <motion.button
        initial={{ opacity: 0, scale: 0.95 }}
        animate={{ opacity: 1, scale: 1 }}
        transition={{ delay: 0.15 }}
        onClick={() => navigate('/puzzle/trivia')}
        className="w-full glass rounded-2xl p-6 text-left hover:bg-white/20 transition-all group"
      >
        <div className="flex items-center justify-between">
          <div>
            <div className="flex items-center gap-2 text-orange-400 font-semibold mb-1">
              <Timer size={18} />
              <span>Daily Challenge</span>
            </div>
            <p className="text-white/70 text-sm">Today's fresh puzzles are ready!</p>
          </div>
          <div className="w-12 h-12 bg-orange-500/20 rounded-full flex items-center justify-center group-hover:scale-110 transition-transform">
            <Zap className="text-orange-400" size={24} />
          </div>
        </div>
      </motion.button>

      {/* Puzzle Categories */}
      {puzzleCategories.map((category, categoryIndex) => (
        <motion.div
          key={category.id}
          initial={{ opacity: 0, y: 20 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ delay: 0.1 * (categoryIndex + 1) }}
          className="space-y-3"
        >
          <h2 className="text-lg font-semibold text-white px-1">{category.title}</h2>

          <div className="grid grid-cols-2 sm:grid-cols-3 lg:grid-cols-4 xl:grid-cols-5 gap-3">
            {category.puzzles.map((puzzle) => {
              const Icon = puzzle.icon
              return (
                <motion.button
                  key={puzzle.type}
                  onClick={() => handlePuzzleSelect(puzzle.type)}
                  className="group relative overflow-hidden rounded-xl p-4 text-left transition-all hover:scale-105"
                  style={{ backgroundColor: puzzle.color }}
                  whileHover={{ y: -4 }}
                  whileTap={{ scale: 0.95 }}
                >
                  {/* Icon */}
                  <div className="w-10 h-10 bg-white/20 rounded-full flex items-center justify-center mb-3">
                    <Icon className="text-white" size={20} />
                  </div>

                  {/* Text */}
                  <h3 className="font-semibold text-white text-sm leading-tight mb-1">
                    {puzzle.name}
                  </h3>
                  <p className="text-white/70 text-xs line-clamp-2">
                    {puzzle.description}
                  </p>

                  {/* Shine effect on hover */}
                  <div className="absolute inset-0 bg-gradient-to-r from-transparent via-white/20 to-transparent -translate-x-full group-hover:translate-x-full transition-transform duration-700" />
                </motion.button>
              )
            })}
          </div>
        </motion.div>
      ))}
    </div>
  )
}
