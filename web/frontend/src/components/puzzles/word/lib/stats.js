// Per-game local stats (played / won / streak / best / solve-in-N distribution). Mirrors WordPuzzleStats.kt.
// The store is any object with getItem/setItem (localStorage); failures are swallowed.

const PREFIX = 'rv_word_stats_'

function memoryStore() {
  const m = new Map()
  return { getItem: (k) => (m.has(k) ? m.get(k) : null), setItem: (k, v) => void m.set(k, String(v)) }
}

export function defaultStore() {
  try {
    if (typeof localStorage !== 'undefined') {
      localStorage.getItem('rv_probe')
      return localStorage
    }
  } catch { /* blocked storage */ }
  return memoryStore()
}

export function createStats(store = defaultStore()) {
  const get = (k, d = 0) => {
    try {
      const v = parseInt(store.getItem(PREFIX + k), 10)
      return Number.isFinite(v) ? v : d
    } catch {
      return d
    }
  }
  const put = (k, v) => {
    try { store.setItem(PREFIX + k, String(v)) } catch { /* ignore */ }
  }
  return {
    record(game, won, attempts = 0, distributionSize = 0) {
      put(`${game}_played`, get(`${game}_played`) + 1)
      if (won) {
        put(`${game}_won`, get(`${game}_won`) + 1)
        const s = get(`${game}_streak`) + 1
        put(`${game}_streak`, s)
        if (s > get(`${game}_best`)) put(`${game}_best`, s)
        if (attempts >= 1 && attempts <= distributionSize) put(`${game}_dist_${attempts}`, get(`${game}_dist_${attempts}`) + 1)
      } else {
        put(`${game}_streak`, 0)
      }
    },
    snapshot(game, distributionSize = 0) {
      const played = get(`${game}_played`)
      const won = get(`${game}_won`)
      return {
        played, won,
        streak: get(`${game}_streak`),
        bestStreak: get(`${game}_best`),
        distribution: Array.from({ length: distributionSize }, (_, i) => get(`${game}_dist_${i + 1}`)),
        winPercent: played === 0 ? 0 : Math.floor((won * 100) / played),
      }
    },
  }
}
