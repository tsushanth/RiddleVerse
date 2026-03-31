// API Base URL configuration
const API_BASE_URL = import.meta.env.DEV
  ? '' // Use Vite proxy in development
  : 'https://puzzleverseai.com'

// Helper to get auth token
const getAuthHeaders = () => {
  const token = localStorage.getItem('token')
  return {
    'Content-Type': 'application/json',
    ...(token && { Authorization: `Bearer ${token}` })
  }
}

// API service functions
export const api = {
  // Authentication
  async authenticateWithGoogle(idToken) {
    const response = await fetch(`${API_BASE_URL}/auth/google`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ idToken })
    })
    return response.json()
  },

  // Puzzles
  async fetchNextPuzzle(puzzleType) {
    const response = await fetch(`${API_BASE_URL}/fetch-next-puzzle-ios/${puzzleType}`, {
      method: 'GET',
      headers: getAuthHeaders()
    })
    return response.json()
  },

  /**
   * Fetch daily puzzles for multiple types at once.
   * @param {string[]} types - e.g. ['anagram', 'trivia', 'wordsearch']
   * @param {string} difficulty - 'easy' | 'medium' | 'hard'
   * @param {string} [date] - YYYY-MM-DD (defaults to today on server)
   * @returns {{ success, date, puzzles: Record<string, Array> }}
   */
  async fetchDailyPuzzles(types, difficulty = 'easy', date) {
    const params = new URLSearchParams({
      types: types.join(','),
      difficulty,
    })
    if (date) params.set('date', date)
    const response = await fetch(
      `${API_BASE_URL}/api/puzzles/daily?${params.toString()}`,
      { method: 'GET', headers: getAuthHeaders() }
    )
    return response.json()
  },

  async checkAnswer(question, expectedAnswer, guessedAnswer, puzzleType, modelName = 'gpt-3.5-turbo') {
    const response = await fetch(`${API_BASE_URL}/check-answer`, {
      method: 'POST',
      headers: getAuthHeaders(),
      body: JSON.stringify({
        question,
        expected_answer: expectedAnswer,
        guessed_answer: guessedAnswer,
        puzzleType,
        modelName
      })
    })
    return response.json()
  },

  async generateAnswer(question, puzzleType, modelName = 'deepseek') {
    const response = await fetch(`${API_BASE_URL}/api/puzzles/generate-answer`, {
      method: 'POST',
      headers: getAuthHeaders(),
      body: JSON.stringify({ question, puzzleType, modelName })
    })
    return response.json()
  },

  // Leaderboard
  async getLeaderboard() {
    const response = await fetch(`${API_BASE_URL}/leaderboard`, {
      method: 'GET',
      headers: getAuthHeaders()
    })
    return response.json()
  },

  // User
  async resetProgress(userId) {
    const response = await fetch(`${API_BASE_URL}/reset-puzzle-progress/${userId}`, {
      method: 'POST',
      headers: getAuthHeaders()
    })
    return response.json()
  },

  async getUserScore() {
    const response = await fetch(`${API_BASE_URL}/api/user/score`, {
      method: 'GET',
      headers: getAuthHeaders()
    })
    return response.json()
  },

  // Daily Puzzles
  async getDailyPuzzles() {
    const response = await fetch(`${API_BASE_URL}/api/daily-puzzles`, {
      method: 'GET',
      headers: getAuthHeaders()
    })
    return response.json()
  },

  // Achievements
  async getAchievements() {
    const response = await fetch(`${API_BASE_URL}/api/user/achievements`, {
      method: 'GET',
      headers: getAuthHeaders()
    })
    return response.json()
  },

  // Game Creation
  async browseGames(sort = 'newest', limit = 20, offset = 0) {
    const response = await fetch(`${API_BASE_URL}/api/game-creation/browse?sort=${sort}&limit=${limit}&offset=${offset}`, {
      headers: getAuthHeaders()
    })
    return response.json()
  },

  async saveGame(title, bundle, creatorId, creatorName, initialPrompt) {
    const response = await fetch(`${API_BASE_URL}/api/game-creation/save`, {
      method: 'POST',
      headers: getAuthHeaders(),
      body: JSON.stringify({ title, bundle, creatorId, creatorName, initialPrompt })
    })
    return response.json()
  },

  async getGameSuggestions() {
    const response = await fetch(`${API_BASE_URL}/api/game-creation/suggestions`, {
      headers: getAuthHeaders()
    })
    return response.json()
  },

  async getGameLeaderboard(gameId) {
    const response = await fetch(`${API_BASE_URL}/api/game-creation/${gameId}`, {
      headers: getAuthHeaders()
    })
    return response.json()
  },

  async shareScore(puzzleType, score) {
    // Sharing is client-side — no API call needed
    return {
      url: `https://puzzleverseai.com/puzzle/${puzzleType}?challenge=true`,
      text: `I scored ${score} on RiddleVerse! Can you beat me?`
    }
  },

  async updateCoins(userId, coins) {
    const response = await fetch(`${API_BASE_URL}/api/leaderboard/update-score`, {
      method: 'POST',
      headers: getAuthHeaders(),
      body: JSON.stringify({ userId, score: coins, name: 'web-user' })
    })
    return response.json()
  },

  async getStreak(userId) {
    const response = await fetch(`${API_BASE_URL}/api/user/score`, {
      headers: getAuthHeaders()
    })
    return response.json()
  }
}

export default api
