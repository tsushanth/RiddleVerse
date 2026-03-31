// Shared puzzle utilities

/**
 * Safely parse a JSON string field from puzzle data.
 * Returns the parsed object, or the original string if parsing fails.
 */
export function parseJsonField(field) {
  if (!field) return null
  if (typeof field === 'object') return field
  // Parse up to 4 levels deep to handle nested JSON strings
  let data = field
  for (let i = 0; i < 4; i++) {
    if (typeof data !== 'string') break
    try {
      data = JSON.parse(data)
    } catch {
      break
    }
    // If we got a wrapper object with a 'question' field that's a string, dig into it
    if (typeof data === 'object' && data !== null && typeof data.question === 'string') {
      try {
        const inner = JSON.parse(data.question)
        if (typeof inner === 'object' && inner !== null) {
          // Merge top-level metadata (hint, difficulty, etc.) with inner data
          const { question: _, ...rest } = data
          data = { ...rest, ...inner }
        }
      } catch {
        // question wasn't JSON, keep as-is
      }
    }
  }
  return data
}

/**
 * Fuzzy string comparison for answer checking.
 * Returns true if the strings are close enough.
 */
export function fuzzyMatch(guess, answer) {
  const normalize = (s) =>
    s.toLowerCase().trim().replace(/[^a-z0-9]/g, '')
  const g = normalize(guess)
  const a = normalize(answer)
  if (g === a) return true
  // Check if one contains the other
  if (a.includes(g) || g.includes(a)) {
    const ratio = Math.min(g.length, a.length) / Math.max(g.length, a.length)
    return ratio > 0.7
  }
  // Levenshtein distance
  const dist = levenshtein(g, a)
  const maxLen = Math.max(g.length, a.length)
  return maxLen > 0 && dist / maxLen < 0.25
}

function levenshtein(a, b) {
  const m = a.length, n = b.length
  const dp = Array.from({ length: m + 1 }, () => Array(n + 1).fill(0))
  for (let i = 0; i <= m; i++) dp[i][0] = i
  for (let j = 0; j <= n; j++) dp[0][j] = j
  for (let i = 1; i <= m; i++) {
    for (let j = 1; j <= n; j++) {
      dp[i][j] = a[i - 1] === b[j - 1]
        ? dp[i - 1][j - 1]
        : 1 + Math.min(dp[i - 1][j], dp[i][j - 1], dp[i - 1][j - 1])
    }
  }
  return dp[m][n]
}

/**
 * Parse timer string like "1:00" into seconds.
 */
export function parseTimer(timerStr) {
  if (!timerStr) return 60
  const parts = timerStr.split(':')
  if (parts.length === 2) {
    return parseInt(parts[0]) * 60 + parseInt(parts[1])
  }
  return parseInt(timerStr) || 60
}

/**
 * Format seconds into MM:SS display.
 */
export function formatTime(seconds) {
  const m = Math.floor(seconds / 60)
  const s = seconds % 60
  return `${m}:${s.toString().padStart(2, '0')}`
}

/**
 * Shuffle an array (Fisher-Yates).
 */
export function shuffle(arr) {
  const a = [...arr]
  for (let i = a.length - 1; i > 0; i--) {
    const j = Math.floor(Math.random() * (i + 1))
    ;[a[i], a[j]] = [a[j], a[i]]
  }
  return a
}
