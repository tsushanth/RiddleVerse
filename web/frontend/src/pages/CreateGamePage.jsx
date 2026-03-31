import { useState, useRef } from 'react'
import { motion, AnimatePresence } from 'framer-motion'
import { useAuth } from '../context/AuthContext'
import { Sparkles, Loader2, CheckCircle, AlertCircle, Wand2 } from 'lucide-react'

const API_BASE_URL = import.meta.env.DEV ? '' : 'https://puzzleverseai.com'

const suggestionChips = [
  'A memory matching game with emoji pairs and increasing difficulty',
  'A typing speed race game with random sentences and WPM tracking',
  'A color mixing puzzle where you combine RGB colors to match a target',
  'A geography quiz that shows country outlines and you guess the name',
  'A rhythm game where you tap buttons in sync with a beat pattern',
  'A maze escape game with randomly generated walls and a timer',
  'A word chain game where each word must start with the last letter',
  'A math battle game where two players race to solve equations',
]

export default function CreateGamePage() {
  const { user } = useAuth()
  const [prompt, setPrompt] = useState('')
  const [generating, setGenerating] = useState(false)
  const [status, setStatus] = useState(null)
  const [progressPercent, setProgressPercent] = useState(0)
  const [error, setError] = useState(null)
  const [completed, setCompleted] = useState(false)
  const abortRef = useRef(null)

  const handleGenerate = async () => {
    if (!prompt.trim() || generating) return

    setGenerating(true)
    setStatus(null)
    setProgressPercent(0)
    setError(null)
    setCompleted(false)

    const token = localStorage.getItem('token')

    try {
      const response = await fetch(`${API_BASE_URL}/api/game-creation/generate`, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          ...(token && { Authorization: `Bearer ${token}` }),
        },
        body: JSON.stringify({
          prompt: prompt.trim(),
          userId: user?.uid || localStorage.getItem('userId'),
          userName: user?.displayName || user?.email?.split('@')[0] || 'Anonymous',
          title: prompt.trim().substring(0, 100),
        }),
      })

      if (!response.ok) {
        throw new Error(`Server error: ${response.status}`)
      }

      const reader = response.body.getReader()
      const decoder = new TextDecoder()
      let buffer = ''

      while (true) {
        const { done, value } = await reader.read()
        if (done) break

        buffer += decoder.decode(value, { stream: true })
        const lines = buffer.split('\n')
        buffer = lines.pop() || ''

        for (const line of lines) {
          if (!line.startsWith('data: ')) continue
          const raw = line.slice(6).trim()
          if (!raw || raw === '[DONE]') continue

          try {
            const event = JSON.parse(raw)

            if (event.type === 'status') {
              setStatus(event)
              if (event.progressPercent != null) {
                setProgressPercent(event.progressPercent)
              }
            } else if (event.type === 'result') {
              // Save the game
              const saveRes = await fetch(`${API_BASE_URL}/api/game-creation/save`, {
                method: 'POST',
                headers: {
                  'Content-Type': 'application/json',
                  ...(token && { Authorization: `Bearer ${token}` }),
                },
                body: JSON.stringify({
                  title: prompt.trim().substring(0, 100),
                  bundle: event.bundle,
                  creatorId: user?.uid || localStorage.getItem('userId'),
                  creatorName: user?.displayName || user?.email?.split('@')[0] || 'Anonymous',
                  initialPrompt: prompt.trim(),
                }),
              })

              const saveData = await saveRes.json()
              const gameId = saveData.gameId || event.gameId

              setCompleted(true)
              setProgressPercent(100)

              if (gameId) {
                window.open(`https://puzzleverseai.com/play/${gameId}`, '_blank')
              }
            } else if (event.type === 'error') {
              throw new Error(event.message || 'Generation failed')
            }
          } catch (parseErr) {
            // Ignore parse errors for incomplete chunks
            if (parseErr.message && !parseErr.message.includes('JSON')) {
              throw parseErr
            }
          }
        }
      }
    } catch (err) {
      setError(err.message || 'Something went wrong')
    } finally {
      setGenerating(false)
    }
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
          <Wand2 size={18} className="text-purple-300" />
          <span className="text-purple-300 text-sm font-medium">AI Game Creator</span>
        </div>
        <h1 className="text-2xl font-bold text-white">Create Your Own Game</h1>
        <p className="text-white/70 mt-1">Describe your game idea and AI will build it for you</p>
      </motion.div>

      {/* Prompt Input */}
      <motion.div
        initial={{ opacity: 0, y: 20 }}
        animate={{ opacity: 1, y: 0 }}
        transition={{ delay: 0.1 }}
        className="glass rounded-2xl p-6"
      >
        <textarea
          value={prompt}
          onChange={(e) => setPrompt(e.target.value)}
          placeholder="Describe the game you want to create... e.g. 'A space invaders game where you defend Earth from alien puzzles'"
          className="w-full bg-white/5 border border-white/10 rounded-xl p-4 text-white placeholder-white/40 resize-none focus:outline-none focus:ring-2 focus:ring-purple-400/50 transition-all"
          rows={4}
          disabled={generating}
        />

        <button
          onClick={handleGenerate}
          disabled={!prompt.trim() || generating}
          className="mt-4 w-full flex items-center justify-center gap-2 bg-purple-500 hover:bg-purple-400 disabled:bg-white/10 disabled:text-white/30 text-white font-semibold py-3 px-6 rounded-xl transition-all"
        >
          {generating ? (
            <>
              <Loader2 size={20} className="animate-spin" />
              <span>Generating...</span>
            </>
          ) : (
            <>
              <Sparkles size={20} />
              <span>Generate Game</span>
            </>
          )}
        </button>
      </motion.div>

      {/* Progress */}
      <AnimatePresence>
        {(generating || completed) && (
          <motion.div
            initial={{ opacity: 0, y: 20 }}
            animate={{ opacity: 1, y: 0 }}
            exit={{ opacity: 0, y: -10 }}
            className="glass rounded-2xl p-6 space-y-4"
          >
            {/* Progress bar */}
            <div className="w-full bg-white/10 rounded-full h-2 overflow-hidden">
              <motion.div
                className="h-full bg-purple-400 rounded-full"
                initial={{ width: 0 }}
                animate={{ width: `${progressPercent}%` }}
                transition={{ duration: 0.5 }}
              />
            </div>

            {/* Status message */}
            {status && (
              <div className="flex items-start gap-3">
                {completed ? (
                  <CheckCircle size={20} className="text-green-400 mt-0.5 shrink-0" />
                ) : (
                  <Loader2 size={20} className="text-purple-400 animate-spin mt-0.5 shrink-0" />
                )}
                <div>
                  <p className="text-white font-medium">{status.message || status.phase}</p>
                  {status.detail && (
                    <p className="text-white/50 text-sm mt-1">{status.detail}</p>
                  )}
                </div>
              </div>
            )}

            {completed && (
              <p className="text-green-400 text-sm font-medium text-center">
                Your game has been created and opened in a new tab!
              </p>
            )}
          </motion.div>
        )}
      </AnimatePresence>

      {/* Error */}
      <AnimatePresence>
        {error && (
          <motion.div
            initial={{ opacity: 0, y: 20 }}
            animate={{ opacity: 1, y: 0 }}
            exit={{ opacity: 0 }}
            className="glass rounded-2xl p-4 border border-red-500/30"
          >
            <div className="flex items-center gap-3">
              <AlertCircle size={20} className="text-red-400 shrink-0" />
              <p className="text-red-300 text-sm">{error}</p>
            </div>
          </motion.div>
        )}
      </AnimatePresence>

      {/* Suggestion Chips */}
      <motion.div
        initial={{ opacity: 0, y: 20 }}
        animate={{ opacity: 1, y: 0 }}
        transition={{ delay: 0.2 }}
        className="space-y-3"
      >
        <h2 className="text-sm font-medium text-white/50 px-1">Need inspiration? Try one of these:</h2>
        <div className="flex flex-wrap gap-2">
          {suggestionChips.map((chip, i) => (
            <motion.button
              key={i}
              initial={{ opacity: 0, scale: 0.9 }}
              animate={{ opacity: 1, scale: 1 }}
              transition={{ delay: 0.25 + i * 0.05 }}
              onClick={() => {
                if (!generating) setPrompt(chip)
              }}
              disabled={generating}
              className="bg-white/5 hover:bg-white/10 border border-white/10 rounded-full px-4 py-2 text-sm text-white/70 hover:text-white transition-all disabled:opacity-50"
            >
              {chip}
            </motion.button>
          ))}
        </div>
      </motion.div>
    </div>
  )
}
