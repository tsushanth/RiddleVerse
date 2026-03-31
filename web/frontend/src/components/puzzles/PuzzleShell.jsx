import { motion, AnimatePresence } from 'framer-motion'
import { Check, X, SkipForward, Loader2 } from 'lucide-react'
import ShareButton from '../ShareButton'

/**
 * Shared wrapper for puzzle result display and next-puzzle button.
 * Each puzzle renderer shows its own interactive UI, then calls onAnswer().
 * This shell displays the result feedback and navigation.
 */
export default function PuzzleShell({
  result,         // 'correct' | 'incorrect' | 'showed' | null
  userAnswer,
  correctAnswer,
  score,
  puzzleType,
  puzzleTypeName,
  onNext,
  isLoadingNext,
  children,
}) {
  return (
    <div className="space-y-4">
      {/* Puzzle-specific interactive content */}
      {children}

      {/* Result display */}
      <AnimatePresence>
        {result && (
          <motion.div
            initial={{ opacity: 0, y: 20 }}
            animate={{ opacity: 1, y: 0 }}
            exit={{ opacity: 0, y: -20 }}
            className="space-y-4"
          >
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
                  <p className="text-white/70">
                    Correct answer: <span className="text-green-400">{correctAnswer}</span>
                  </p>
                </div>
              ) : (
                <div className="space-y-2">
                  <p className="text-white/70">The answer is:</p>
                  <p className="text-white text-xl font-bold">{correctAnswer}</p>
                </div>
              )}
            </div>

            {result === 'correct' && (
              <div className="flex justify-center">
                <ShareButton
                  title="RiddleVerse Challenge"
                  text={`I scored ${score} on ${puzzleTypeName} in RiddleVerse! Can you beat me?`}
                  url={`https://puzzleverseai.com/puzzle/${puzzleType}?challenge=true`}
                />
              </div>
            )}

            <button
              onClick={onNext}
              disabled={isLoadingNext}
              className="w-full flex items-center justify-center gap-2 bg-purple-500 hover:bg-purple-600 text-white font-semibold py-3 rounded-xl transition-colors"
            >
              {isLoadingNext ? (
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
    </div>
  )
}
