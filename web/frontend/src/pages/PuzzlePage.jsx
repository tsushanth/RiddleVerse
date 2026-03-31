import { useState, useEffect, useCallback } from 'react'
import { useParams, useNavigate } from 'react-router-dom'
import { motion, AnimatePresence } from 'framer-motion'
import { useMutation } from '@tanstack/react-query'
import { api } from '../services/api'
import { trackEvent } from '../services/firebase'
import { useGameContext } from '../context/GameContext'
import {
  ArrowLeft, Trophy, Loader2, RefreshCw, SkipForward, Check, X
} from 'lucide-react'
import ShareButton from '../components/ShareButton'
import DifficultySelector from '../components/DifficultySelector'
import PuzzleRenderer from '../components/puzzles/PuzzleRenderer'

// Puzzle type display names
const puzzleTypeNames = {
  trivia: 'Trivia',
  anagram: 'Anagram',
  storypuzzle: 'Story Puzzle',
  math: 'Math',
  riddle: 'Riddles',
  qa: 'Q&A',
  synonym: 'Synonym',
  antonym: 'Antonym',
  wordprefix: 'Word Prefix',
  letterset: 'Letter Set',
  wordsnake: 'Word Snake',
  wordsearch: 'Word Search',
  crossword: 'Crossword',
  crypto: 'Cryptogram',
  cryptoword: 'Cryptoword',
  estimation: 'Estimation',
  percentage: 'Percentage',
  average: 'Average',
  conversion: 'Conversion',
  division: 'Division',
  numbersequence: 'Number Sequence',
  memoryretention: 'Memory',
  memorysequencing: 'Sequencing',
  memorystory: 'Story Memory',
  memoryprevioussingle: 'N-Back Single',
  memorypreviouspair: 'N-Back Pair',
  imagepuzzle: 'Image Puzzle',
  waldo: 'Find It',
  oddoneout: 'Odd One Out',
  symmetry: 'Symmetry',
  uniqueobject: 'Unique Object',
  geography_cities: 'Cities',
  geography_countries: 'Countries',
  multiplechoice: 'Multiple Choice',
  daily: 'Daily Challenge',
  jumble: 'Jumble',
  synonymgrouping: 'Synonym Grouping',
  antonymballoon: 'Antonym Balloons',
  mathexpression: 'Math Expression',
  sentencetransitions: 'Transitions',
  progressivereveal: 'Progressive Reveal',
  realorai: 'Real or AI?',
  colortextmatching: 'Color Match',
}

// ---------------------------------------------------------------------------
// Daily puzzle helpers — localStorage-based progress tracking
// ---------------------------------------------------------------------------
const DAILY_CACHE_KEY = 'rv_daily_puzzles'
const DAILY_PROGRESS_KEY = 'rv_daily_progress'

function todayUTC() {
  return new Date().toISOString().split('T')[0]
}

function difficultyLabel(level) {
  return level === 1 ? 'easy' : level === 2 ? 'medium' : 'hard'
}

/** Read today's cached puzzles for a type+difficulty from localStorage. */
function getCachedDaily(type, difficulty) {
  try {
    const raw = localStorage.getItem(DAILY_CACHE_KEY)
    if (!raw) return null
    const cache = JSON.parse(raw)
    if (cache.date !== todayUTC()) return null // stale
    return cache.puzzles?.[type] || null
  } catch {
    return null
  }
}

/** Store the server response for today in localStorage. */
function storeDailyCache(date, puzzles) {
  try {
    const existing = JSON.parse(localStorage.getItem(DAILY_CACHE_KEY) || '{}')
    if (existing.date !== date) {
      // New day — replace entire cache
      localStorage.setItem(DAILY_CACHE_KEY, JSON.stringify({ date, puzzles }))
    } else {
      // Same day — merge new types into existing cache
      const merged = { ...existing.puzzles, ...puzzles }
      localStorage.setItem(DAILY_CACHE_KEY, JSON.stringify({ date, puzzles: merged }))
    }
  } catch { /* quota exceeded — ignore, still works via server */ }
}

/** Get the current progress index for type+difficulty today. */
function getProgressIndex(type, difficulty) {
  try {
    const raw = localStorage.getItem(DAILY_PROGRESS_KEY)
    if (!raw) return 0
    const progress = JSON.parse(raw)
    if (progress.date !== todayUTC()) return 0
    return progress.indices?.[`${type}_${difficulty}`] || 0
  } catch {
    return 0
  }
}

/** Advance the progress index for type+difficulty today. */
function advanceProgress(type, difficulty) {
  try {
    const today = todayUTC()
    const raw = localStorage.getItem(DAILY_PROGRESS_KEY)
    let progress = raw ? JSON.parse(raw) : { date: today, indices: {} }
    if (progress.date !== today) progress = { date: today, indices: {} }
    const key = `${type}_${difficulty}`
    progress.indices[key] = (progress.indices[key] || 0) + 1
    localStorage.setItem(DAILY_PROGRESS_KEY, JSON.stringify(progress))
    return progress.indices[key]
  } catch {
    return 0
  }
}

// ---------------------------------------------------------------------------
// Component
// ---------------------------------------------------------------------------

export default function PuzzlePage() {
  const { type } = useParams()
  const navigate = useNavigate()
  const { coins, addCoins, incrementStreak } = useGameContext()

  const [puzzle, setPuzzle] = useState(null)
  const [result, setResult] = useState(null) // 'correct' | 'incorrect' | 'showed' | null
  const [userAnswer, setUserAnswer] = useState('')
  const [score, setScore] = useState(parseInt(localStorage.getItem('userScore') || '0'))
  const [streak, setStreak] = useState(0)
  const [coinAnimation, setCoinAnimation] = useState(false)
  const [difficulty, setDifficulty] = useState(1)
  const unlockedLevels = parseInt(localStorage.getItem('unlockedLevels') || '1')

  /**
   * Serve the next puzzle from the daily cache (localStorage) or fetch from server.
   * Progress is tracked locally — no Firebase round-trip.
   */
  const loadNextPuzzle = useCallback(async () => {
    const diff = difficultyLabel(difficulty)
    const cached = getCachedDaily(type, diff)

    if (cached && cached.length > 0) {
      const idx = getProgressIndex(type, diff) % cached.length
      advanceProgress(type, diff)
      return cached[idx]
    }

    // Cache miss — fetch from new daily endpoint
    try {
      const res = await api.fetchDailyPuzzles([type], diff)
      if (res.success && res.puzzles?.[type]?.length > 0) {
        storeDailyCache(res.date, res.puzzles)
        const puzzles = res.puzzles[type]
        const idx = getProgressIndex(type, diff) % puzzles.length
        advanceProgress(type, diff)
        return puzzles[idx]
      }
    } catch (err) {
      console.warn('Daily endpoint failed, falling back to legacy:', err)
    }

    // Final fallback — legacy single-puzzle endpoint
    const legacy = await api.fetchNextPuzzle(type)
    if (legacy.success && legacy.puzzleData) return legacy.puzzleData
    return null
  }, [type, difficulty])

  // Fetch puzzle mutation (wraps loadNextPuzzle)
  const fetchPuzzleMutation = useMutation({
    mutationFn: loadNextPuzzle,
    onSuccess: (data) => {
      if (data) {
        setPuzzle(data)
        setResult(null)
        setUserAnswer('')
        trackEvent('puzzle_loaded', { puzzle_type: type })
      } else {
        // No data — set a minimal puzzle object so client-side generators can take over
        setPuzzle({
          puzzleId: `local-${type}-${Date.now()}`,
          puzzleType: type,
          question: '',
          answer: '',
          hint: '',
          options: [],
          timer: '1:30',
          difficulty: difficulty || 'medium',
        })
        setResult(null)
        setUserAnswer('')
      }
    },
    onError: (error) => {
      console.error('Error fetching puzzle:', error)
      // On error, also allow client-side generation
      setPuzzle({
        puzzleId: `local-${type}-${Date.now()}`,
        puzzleType: type,
        question: '',
        answer: '',
        hint: '',
        options: [],
        timer: '1:30',
        difficulty: difficulty || 'medium',
      })
    }
  })

  // Check answer mutation (for QA-style puzzles that need server validation)
  const checkAnswerMutation = useMutation({
    mutationFn: ({ question, expectedAnswer, guessedAnswer }) =>
      api.checkAnswer(question, expectedAnswer, guessedAnswer, type),
    onSuccess: (data) => {
      if (data.correct) {
        handleCorrect()
      } else {
        handleIncorrect()
      }
    }
  })

  // Load initial puzzle (re-fetch when type or difficulty changes)
  useEffect(() => {
    fetchPuzzleMutation.mutate()
  }, [type, difficulty])

  // --- Answer handlers ---

  const handleCorrect = useCallback(() => {
    setResult('correct')
    const newScore = score + 10
    setScore(newScore)
    setStreak((prev) => prev + 1)
    localStorage.setItem('userScore', newScore.toString())
    addCoins(10, `Correct answer: ${puzzleTypeNames[type] || type}`)
    setCoinAnimation(true)
    setTimeout(() => setCoinAnimation(false), 2000)
    if (type === 'daily') {
      incrementStreak()
    }
    trackEvent('puzzle_solved', { puzzle_type: type, score: newScore })
  }, [score, type, addCoins, incrementStreak])

  const handleIncorrect = useCallback(() => {
    setResult('incorrect')
    setStreak(0)
    trackEvent('puzzle_failed', { puzzle_type: type })
  }, [type])

  /**
   * Called by puzzle renderers when the user answers.
   * For client-validated puzzles (trivia, anagram, wordsearch, etc.)
   */
  const handleAnswer = useCallback((isCorrect, answer, showedAnswer = false) => {
    setUserAnswer(answer || '')
    if (showedAnswer) {
      setResult('showed')
      trackEvent('answer_revealed', { puzzle_type: type })
    } else if (isCorrect) {
      handleCorrect()
    } else {
      handleIncorrect()
    }
  }, [handleCorrect, handleIncorrect, type])

  /**
   * Called by puzzle renderers that need server-side answer checking.
   */
  const handleSubmitToServer = useCallback((guessedAnswer) => {
    if (!puzzle) return
    setUserAnswer(guessedAnswer)
    checkAnswerMutation.mutate({
      question: typeof puzzle.question === 'string' ? puzzle.question : JSON.stringify(puzzle.question),
      expectedAnswer: typeof puzzle.answer === 'string' ? puzzle.answer : JSON.stringify(puzzle.answer),
      guessedAnswer,
    })
  }, [puzzle, checkAnswerMutation])

  const handleNextPuzzle = () => {
    fetchPuzzleMutation.mutate()
  }

  return (
    <div className="max-w-2xl mx-auto space-y-4">
      {/* Header */}
      <motion.div
        initial={{ opacity: 0, y: -20 }}
        animate={{ opacity: 1, y: 0 }}
        className="flex items-center justify-between"
      >
        <button
          onClick={() => navigate('/')}
          className="flex items-center gap-2 text-white/70 hover:text-white transition-colors"
        >
          <ArrowLeft size={20} />
          <span>Back</span>
        </button>

        <h1 className="text-xl font-bold text-white">
          {puzzleTypeNames[type] || type}
        </h1>

        <div className="flex items-center gap-3 text-white">
          <div className="flex items-center gap-1 relative">
            <span className="text-sm">{'\uD83D\uDCB0'}</span>
            <span className="font-semibold text-yellow-400 text-sm">{coins}</span>
            <AnimatePresence>
              {coinAnimation && (
                <motion.span
                  initial={{ opacity: 1, y: 0 }}
                  animate={{ opacity: 0, y: -30 }}
                  exit={{ opacity: 0 }}
                  transition={{ duration: 1.5 }}
                  className="absolute -top-2 left-1/2 -translate-x-1/2 text-yellow-400 font-bold text-sm whitespace-nowrap"
                >
                  +10 {'\uD83D\uDCB0'}
                </motion.span>
              )}
            </AnimatePresence>
          </div>
          <div className="flex items-center gap-1">
            <Trophy size={18} className="text-yellow-400" />
            <span className="font-semibold">{score}</span>
          </div>
        </div>
      </motion.div>

      {/* Difficulty Selector */}
      <DifficultySelector
        currentLevel={difficulty}
        onSelect={setDifficulty}
        unlockedLevels={unlockedLevels}
      />

      {/* Streak indicator */}
      {streak > 0 && (
        <motion.div
          initial={{ opacity: 0, scale: 0.8 }}
          animate={{ opacity: 1, scale: 1 }}
          className="text-center"
        >
          <span className="bg-orange-500/20 text-orange-400 px-3 py-1 rounded-full text-sm font-medium">
            {'\uD83D\uDD25'} {streak} streak!
          </span>
        </motion.div>
      )}

      {/* Puzzle Card */}
      <motion.div
        key={puzzle?.puzzleId || 'loading'}
        initial={{ opacity: 0, y: 20 }}
        animate={{ opacity: 1, y: 0 }}
        className="glass rounded-2xl p-6 space-y-4"
      >
        {/* Loading state */}
        {fetchPuzzleMutation.isPending && !puzzle && (
          <div className="flex flex-col items-center justify-center py-12">
            <Loader2 className="animate-spin text-white mb-4" size={40} />
            <p className="text-white/70">Loading puzzle...</p>
          </div>
        )}

        {/* Error state */}
        {fetchPuzzleMutation.isError && (
          <div className="text-center py-12">
            <p className="text-red-400 mb-4">Failed to load puzzle</p>
            <button
              onClick={() => fetchPuzzleMutation.mutate()}
              className="flex items-center gap-2 mx-auto text-white bg-white/20 hover:bg-white/30 px-4 py-2 rounded-lg transition-colors"
            >
              <RefreshCw size={18} />
              Try Again
            </button>
          </div>
        )}

        {/* Active puzzle — delegate to type-specific renderer */}
        {puzzle && !result && (
          <PuzzleRenderer
            puzzleType={puzzle.puzzleType || type}
            puzzle={puzzle}
            onAnswer={handleAnswer}
            onSubmitToServer={handleSubmitToServer}
            isChecking={checkAnswerMutation.isPending}
          />
        )}

        {/* Result display */}
        <AnimatePresence>
          {result && (
            <motion.div
              initial={{ opacity: 0, y: 20 }}
              animate={{ opacity: 1, y: 0 }}
              exit={{ opacity: 0, y: -20 }}
              className="space-y-4"
            >
              {/* Result banner */}
              <div
                className={`rounded-xl p-4 text-center ${
                  result === 'correct'
                    ? 'bg-green-500/20 border border-green-500/30'
                    : result === 'incorrect'
                    ? 'bg-red-500/20 border border-red-500/30'
                    : 'bg-gray-500/20 border border-gray-500/30'
                }`}
              >
                {result === 'correct' ? (
                  <>
                    <div className="flex items-center justify-center gap-2 text-green-400">
                      <Check size={24} />
                      <span className="text-xl font-bold">Correct! +10 points</span>
                    </div>
                    <motion.div
                      initial={{ opacity: 0, scale: 0.5 }}
                      animate={{ opacity: 1, scale: 1 }}
                      transition={{ delay: 0.3 }}
                      className="flex items-center justify-center gap-1 mt-2"
                    >
                      <span className="text-yellow-400 font-semibold">+10</span>
                      <span className="text-yellow-400">{'\uD83D\uDCB0'}</span>
                      <span className="text-yellow-400/70 text-sm">coins earned</span>
                    </motion.div>
                  </>
                ) : result === 'incorrect' ? (
                  <div className="space-y-2">
                    <div className="flex items-center justify-center gap-2 text-red-400">
                      <X size={24} />
                      <span className="text-xl font-bold">Incorrect</span>
                    </div>
                    {userAnswer && (
                      <p className="text-white/70">
                        Your answer: <span className="text-white">{userAnswer}</span>
                      </p>
                    )}
                    {(() => {
                      // Don't show raw JSON answers for complex puzzle types that handle their own feedback
                      const selfHandledTypes = ['realorai','which_is_real','synonyms','antonyms','synonymgrouping','antonymballoon','memorystory','memoryretention','memorysquares','memorypreviouspair','memoryprevioussingle','memorysequencing','uniqueobject','colorshapematching','triangledotmemory','symmetry','imagepuzzle','wordsearch','crossword','wordsnake','letterset','wordprefix']
                      const answerStr = typeof puzzle.answer === 'string' ? puzzle.answer : JSON.stringify(puzzle.answer)
                      const isSelfHandled = selfHandledTypes.includes(type) || selfHandledTypes.includes(puzzle.puzzleType)
                      const isJsonAnswer = answerStr.startsWith('{') || answerStr.startsWith('[')
                      if (isSelfHandled || isJsonAnswer) return null
                      return (
                        <p className="text-white/70">
                          Correct answer:{' '}
                          <span className="text-green-400">{answerStr}</span>
                        </p>
                      )
                    })()}
                  </div>
                ) : (
                  <div className="space-y-2">
                    {(() => {
                      const selfHandledTypes = ['realorai','which_is_real','synonyms','antonyms','synonymgrouping','antonymballoon','memorystory','memoryretention','memorysquares','memorypreviouspair','memoryprevioussingle','memorysequencing','uniqueobject','colorshapematching','triangledotmemory','symmetry','imagepuzzle','wordsearch','crossword','wordsnake','letterset','wordprefix']
                      const answerStr = typeof puzzle.answer === 'string' ? puzzle.answer : JSON.stringify(puzzle.answer)
                      const isSelfHandled = selfHandledTypes.includes(type) || selfHandledTypes.includes(puzzle.puzzleType)
                      const isJsonAnswer = answerStr.startsWith('{') || answerStr.startsWith('[')
                      if (isSelfHandled || isJsonAnswer) return null
                      return (
                        <>
                          <p className="text-white/70">The answer is:</p>
                          <p className="text-white text-xl font-bold">{answerStr}</p>
                        </>
                      )
                    })()}
                  </div>
                )}
              </div>

              {/* Share button */}
              {result === 'correct' && (
                <div className="flex justify-center">
                  <ShareButton
                    title="RiddleVerse Challenge"
                    text={`I scored ${score} on ${puzzleTypeNames[type] || type} in RiddleVerse! Can you beat me?`}
                    url={`https://puzzleverseai.com/puzzle/${type}?challenge=true`}
                  />
                </div>
              )}

              {/* Next puzzle button */}
              <button
                onClick={handleNextPuzzle}
                disabled={fetchPuzzleMutation.isPending}
                className="w-full flex items-center justify-center gap-2 bg-purple-500 hover:bg-purple-600 text-white font-semibold py-3 rounded-xl transition-colors"
              >
                {fetchPuzzleMutation.isPending ? (
                  <Loader2 className="animate-spin" size={20} />
                ) : (
                  <>
                    <SkipForward size={20} />
                    Next Puzzle
                  </>
                )}
              </button>
            </motion.div>
          )}
        </AnimatePresence>
      </motion.div>
    </div>
  )
}
