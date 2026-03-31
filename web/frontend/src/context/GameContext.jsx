import { createContext, useContext, useState, useEffect, useCallback } from 'react'
import { api } from '../services/api'

const GameContext = createContext(null)

// Milestone streak rewards
const STREAK_REWARDS = {
  7: 50,
  14: 100,
  30: 250,
  50: 500,
  100: 1000,
}

const MILESTONES = [7, 14, 30, 50, 100]

function getTodayString() {
  return new Date().toISOString().split('T')[0]
}

function addCoinActivity(description, amount) {
  try {
    const activities = JSON.parse(localStorage.getItem('coinActivities') || '[]')
    activities.unshift({
      description,
      amount,
      timestamp: new Date().toISOString(),
    })
    // Keep only last 50 entries
    localStorage.setItem('coinActivities', JSON.stringify(activities.slice(0, 50)))
  } catch {
    // ignore
  }
}

export function GameProvider({ children }) {
  const [coins, setCoins] = useState(() => {
    return parseInt(localStorage.getItem('coins') || '0')
  })
  const [streak, setStreak] = useState(() => {
    return parseInt(localStorage.getItem('streak') || '0')
  })
  const [lastPlayDate, setLastPlayDate] = useState(() => {
    return localStorage.getItem('lastPlayDate') || ''
  })

  // Sync state to localStorage whenever it changes
  useEffect(() => {
    localStorage.setItem('coins', coins.toString())
  }, [coins])

  useEffect(() => {
    localStorage.setItem('streak', streak.toString())
  }, [streak])

  useEffect(() => {
    localStorage.setItem('lastPlayDate', lastPlayDate)
  }, [lastPlayDate])

  // Fetch from server on mount
  useEffect(() => {
    async function fetchServerData() {
      try {
        const data = await api.getUserScore()
        if (data && typeof data.coins === 'number') {
          setCoins(prev => Math.max(prev, data.coins))
        }
        if (data && typeof data.streak === 'number') {
          setStreak(prev => Math.max(prev, data.streak))
        }
      } catch {
        // Use local values as fallback
      }
    }
    fetchServerData()
  }, [])

  const addCoins = useCallback((amount, description = 'Earned coins') => {
    setCoins(prev => {
      const newCoins = prev + amount
      // Fire-and-forget server sync
      const userId = localStorage.getItem('userId')
      if (userId) {
        api.updateCoins(userId, newCoins).catch(() => {})
      }
      return newCoins
    })
    addCoinActivity(description, amount)
  }, [])

  const spendCoins = useCallback((amount) => {
    let success = false
    setCoins(prev => {
      if (prev >= amount) {
        success = true
        addCoinActivity('Spent coins', -amount)
        return prev - amount
      }
      return prev
    })
    return success
  }, [])

  const incrementStreak = useCallback(() => {
    const today = getTodayString()
    if (lastPlayDate === today) {
      // Already played today
      return
    }

    const yesterday = new Date()
    yesterday.setDate(yesterday.getDate() - 1)
    const yesterdayString = yesterday.toISOString().split('T')[0]

    let newStreak
    if (lastPlayDate === yesterdayString) {
      // Consecutive day
      newStreak = streak + 1
    } else {
      // Gap in days, reset streak
      newStreak = 1
    }

    setStreak(newStreak)
    setLastPlayDate(today)

    // Check milestone rewards
    const reward = STREAK_REWARDS[newStreak]
    if (reward) {
      addCoins(reward, `${newStreak}-day streak reward`)
    }

    return newStreak
  }, [lastPlayDate, streak, addCoins])

  const getStreakReward = useCallback(() => {
    // Find next milestone
    const nextMilestone = MILESTONES.find(m => m > streak)
    if (!nextMilestone) return null

    return {
      milestone: nextMilestone,
      reward: STREAK_REWARDS[nextMilestone],
      daysRemaining: nextMilestone - streak,
    }
  }, [streak])

  const getCoinActivities = useCallback(() => {
    try {
      return JSON.parse(localStorage.getItem('coinActivities') || '[]')
    } catch {
      return []
    }
  }, [])

  return (
    <GameContext.Provider value={{
      coins,
      streak,
      lastPlayDate,
      addCoins,
      spendCoins,
      incrementStreak,
      getStreakReward,
      getCoinActivities,
    }}>
      {children}
    </GameContext.Provider>
  )
}

export function useGameContext() {
  const context = useContext(GameContext)
  if (!context) {
    throw new Error('useGameContext must be used within a GameProvider')
  }
  return context
}

export default GameContext
