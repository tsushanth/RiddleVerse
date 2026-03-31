import { useState, useEffect, useRef } from 'react'
import { motion } from 'framer-motion'
import { Timer } from 'lucide-react'
import { parseTimer, formatTime } from './puzzleUtils'

export default function PuzzleTimer({ timerStr, onTimeUp, paused = false }) {
  const totalSeconds = parseTimer(timerStr)
  const [remaining, setRemaining] = useState(totalSeconds)
  const intervalRef = useRef(null)

  useEffect(() => {
    setRemaining(totalSeconds)
  }, [totalSeconds, timerStr])

  useEffect(() => {
    if (paused) {
      clearInterval(intervalRef.current)
      return
    }
    intervalRef.current = setInterval(() => {
      setRemaining((prev) => {
        if (prev <= 1) {
          clearInterval(intervalRef.current)
          onTimeUp?.()
          return 0
        }
        return prev - 1
      })
    }, 1000)
    return () => clearInterval(intervalRef.current)
  }, [paused, onTimeUp])

  const pct = (remaining / totalSeconds) * 100
  const isLow = remaining <= 10

  return (
    <div className="flex items-center gap-2">
      <Timer size={18} className={isLow ? 'text-red-400' : 'text-white/70'} />
      <div className="flex-1 h-2 bg-white/10 rounded-full overflow-hidden">
        <motion.div
          className={`h-full rounded-full ${
            isLow ? 'bg-red-500' : pct > 50 ? 'bg-green-500' : 'bg-yellow-500'
          }`}
          initial={false}
          animate={{ width: `${pct}%` }}
          transition={{ duration: 0.5 }}
        />
      </div>
      <span className={`text-sm font-mono font-semibold min-w-[40px] text-right ${
        isLow ? 'text-red-400 animate-pulse' : 'text-white/80'
      }`}>
        {formatTime(remaining)}
      </span>
    </div>
  )
}
