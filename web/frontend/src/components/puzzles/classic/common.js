// --- web classic puzzles (feat/web-puzzles-classic) --- shared registry data, tiers, scoring, stats (pure)

export const CLASSIC_TYPES = ['tilemerge', 'slidetiles', 'lightflip', 'safesweep', 'disctower']

export const CLASSIC_NAMES = {
  tilemerge: 'Tile Merge',
  slidetiles: 'Slide Tiles',
  lightflip: 'Light Flip',
  safesweep: 'Safe Sweep',
  disctower: 'Disc Tower',
}

export const TIER_LABELS = ['beginner', 'easy', 'medium', 'hard', 'expert']

export function isClassicType(type) {
  return typeof type === 'string' && CLASSIC_TYPES.includes(type.toLowerCase())
}

/** Maps a difficulty label (Beginner/Easy/Medium/Hard/Expert, any case) to tier 0..4. */
export function tierOf(difficulty) {
  switch (String(difficulty ?? '').trim().toLowerCase()) {
    case 'beginner': return 0
    case 'easy': return 1
    case 'medium':
    case 'mixed': return 2
    case 'hard': return 3
    case 'expert': return 4
    default: return 2
  }
}

/**
 * Tier for a web puzzle object. The web difficulty selector is 1..5 (Easy..Master) and is stored on
 * client-built puzzles as a number; that maps to tiers 0..4 so all five Android tiers are reachable.
 * String difficulties from a server use the Android label mapping.
 */
export function tierFromPuzzle(puzzle) {
  const d = puzzle?.difficulty
  if (typeof d === 'number' && Number.isFinite(d)) return Math.min(4, Math.max(0, Math.round(d) - 1))
  if (typeof d === 'string' && /^\d+$/.test(d.trim())) return Math.min(4, Math.max(0, parseInt(d, 10) - 1))
  return tierOf(d)
}

/** 10..100 score from how close the move count is to par. */
export function efficiencyScore(par, moves, penalty = 0) {
  if (moves <= 0) return 10
  const ratio = Math.floor((100 * par) / Math.max(moves, par, 1))
  return Math.min(100, Math.max(10, ratio - penalty))
}

export const TUTORIALS = {
  tilemerge: {
    title: 'Tile Merge',
    instruction: 'Swipe (or use the arrow keys / WASD) to slide every tile. Equal tiles that touch merge into one bigger tile. Reach the goal tile before you run out of moves or space. You get 3 undos.',
    tip: 'Keep your biggest tile in a corner and build next to it.',
  },
  slidetiles: {
    title: 'Slide Tiles',
    instruction: 'Tap a tile in the same row or column as the empty space to slide it (arrow keys / WASD also work). Put the numbers in order, with the empty space in the bottom right corner.',
    tip: 'Solve the top row first, then the next one, and leave the last two rows for the end.',
  },
  lightflip: {
    title: 'Light Flip',
    instruction: 'Tapping a light flips it and its four neighbours. Turn every light off in as few taps as you can.',
    tip: 'Tapping the same cell twice does nothing. Use Hint if you get stuck (each hint costs score).',
  },
  safesweep: {
    title: 'Safe Sweep',
    instruction: 'Tap a square to open it. A number tells how many mines touch that square. Flag mines you are sure about, then open every safe square. Your first tap is always safe.',
    tip: 'Long press, right click or use Flag mode to place a flag. Tap a number whose flags are all placed to open its other neighbours.',
  },
  disctower: {
    title: 'Disc Tower',
    instruction: 'Move the whole stack to the right-hand peg. Move one disc at a time, and never put a bigger disc on a smaller one. Tap a peg to pick up its top disc, then tap where to drop it.',
    tip: 'The best possible score needs 2 to the power of discs, minus 1 moves. Hint shows the next best move.',
  },
}

// ---- persistent bests (localStorage; every failure swallowed) ----

const STATS_KEY = 'rv_classic_stats'

function readStats() {
  try {
    const raw = localStorage.getItem(STATS_KEY)
    return raw ? JSON.parse(raw) || {} : {}
  } catch {
    return {}
  }
}

export function getBest(game, tier) {
  const v = readStats()[`${game}_t${tier}`]
  return typeof v === 'number' ? v : null
}

/** Stores value if it beats the best. Returns true only when it replaced an older best. */
export function submitBest(game, tier, value, lowerIsBetter) {
  try {
    const stats = readStats()
    const key = `${game}_t${tier}`
    const old = typeof stats[key] === 'number' ? stats[key] : null
    const better = old === null || (lowerIsBetter ? value < old : value > old)
    if (better) {
      stats[key] = value
      localStorage.setItem(STATS_KEY, JSON.stringify(stats))
    }
    return better && old !== null
  } catch {
    return false
  }
}

const SEEN_KEY = 'rv_classic_tutorial_seen'

export function tutorialSeen(game) {
  try {
    return !!JSON.parse(localStorage.getItem(SEEN_KEY) || '{}')[game]
  } catch {
    return false
  }
}

export function markTutorialSeen(game) {
  try {
    const seen = JSON.parse(localStorage.getItem(SEEN_KEY) || '{}')
    seen[game] = true
    localStorage.setItem(SEEN_KEY, JSON.stringify(seen))
  } catch { /* ignore */ }
}
