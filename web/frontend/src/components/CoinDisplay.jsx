import { useNavigate } from 'react-router-dom'
import { motion, AnimatePresence } from 'framer-motion'
import { useGameContext } from '../context/GameContext'
import { useEffect, useState, useRef } from 'react'

export default function CoinDisplay() {
  const { coins } = useGameContext()
  const navigate = useNavigate()
  const [displayCoins, setDisplayCoins] = useState(coins)
  const [isAnimating, setIsAnimating] = useState(false)
  const prevCoins = useRef(coins)

  useEffect(() => {
    if (coins !== prevCoins.current) {
      setIsAnimating(true)
      // Animate the number incrementally
      const diff = coins - prevCoins.current
      const steps = Math.min(Math.abs(diff), 20)
      const stepValue = diff / steps
      let current = prevCoins.current
      let step = 0

      const interval = setInterval(() => {
        step++
        if (step >= steps) {
          setDisplayCoins(coins)
          clearInterval(interval)
          setTimeout(() => setIsAnimating(false), 300)
        } else {
          current += stepValue
          setDisplayCoins(Math.round(current))
        }
      }, 30)

      prevCoins.current = coins
      return () => clearInterval(interval)
    }
  }, [coins])

  return (
    <motion.button
      onClick={() => navigate('/coins')}
      className="flex items-center gap-1.5 bg-black/20 hover:bg-black/30 rounded-full px-3 py-1.5 transition-colors cursor-pointer"
      whileHover={{ scale: 1.05 }}
      whileTap={{ scale: 0.95 }}
    >
      <span className="text-yellow-400 text-sm">{'\uD83D\uDCB0'}</span>
      <AnimatePresence mode="wait">
        <motion.span
          key={isAnimating ? 'animating' : displayCoins}
          initial={isAnimating ? { y: -10, opacity: 0 } : false}
          animate={{ y: 0, opacity: 1 }}
          exit={{ y: 10, opacity: 0 }}
          transition={{ duration: 0.15 }}
          className={`text-yellow-400 font-bold text-sm ${isAnimating ? 'text-yellow-300' : ''}`}
        >
          {displayCoins}
        </motion.span>
      </AnimatePresence>
    </motion.button>
  )
}
