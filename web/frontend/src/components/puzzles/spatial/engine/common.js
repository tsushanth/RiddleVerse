// --- web spatial puzzles (feat/web-puzzles-spatial) ---
// Shared pure helpers for the spatial puzzle engines. No DOM, no React: importable from Node tests.

export const SPATIAL_TYPES = ['pixellogic', 'tubetint', 'piecepack', 'crateshift', 'conduit']

export const SPATIAL_NAMES = {
  pixellogic: 'Pixel Logic',
  tubetint: 'Tube Tint',
  piecepack: 'Piece Pack',
  crateshift: 'Crate Shift',
  conduit: 'Conduit',
}

/** Same copy as the Android hints/tutorials. */
export const SPATIAL_RULES = {
  pixellogic:
    'Numbers beside each row and column say how long the runs of filled squares are, in order. Fill the squares that must be filled and cross out the ones that cannot be. Drag to paint a line; use the magnifier on big grids.',
  tubetint:
    'Select a tube, then another to pour the top colour across. You can only pour onto the same colour or into an empty tube, and only if there is room. Sort until every tube is a single colour.',
  piecepack:
    'Drag a piece from the tray onto the board. Complete a full row or column to clear it. Clear the target number of lines before you run out of pieces, and before no piece fits.',
  crateshift:
    'Walk into a crate to push it. Push every crate onto a target ring. You cannot pull crates or push two at once. Use the arrow keys, swipe, or the arrow buttons. Fewer moves earn more stars.',
  conduit:
    'Tap a tile to rotate it a quarter turn. Connect pipes so power flows from the glowing source to every lamp, with no open ends left over.',
}

export const SPATIAL_TIPS = {
  pixellogic: 'A clue that nearly fills its line tells you exactly where the squares go. Finished lines turn grey.',
  tubetint: 'Free up an empty tube early. Stuck? Undo, or add one extra tube.',
  piecepack: 'Select a piece, then tap the board if you prefer. Keep the board tidy: holes are hard to fill.',
  crateshift: 'A crate in a corner is stuck for good, so use Undo freely.',
  conduit: 'Edges and corners have few options. Fix those first.',
}

/** Accepts 'easy'|'Medium'|1|2|3|undefined and returns 'easy'|'medium'|'hard'. */
export function normDifficulty(d) {
  if (d === 1 || d === '1') return 'easy'
  if (d === 3 || d === '3') return 'hard'
  if (d === 2 || d === '2') return 'medium'
  const s = String(d ?? '').toLowerCase()
  if (s === 'easy' || s === 'hard' || s === 'medium') return s
  return 'medium'
}

/** FNV-1a style hash of any value to a positive 32-bit int. */
export function hashString(str) {
  let h = 2166136261
  const s = String(str)
  for (let i = 0; i < s.length; i++) {
    h ^= s.charCodeAt(i)
    h = Math.imul(h, 16777619)
  }
  return h >>> 0
}

/**
 * Derives the generation seed for a puzzle object from the normal web flow.
 * Android puzzles carry {"seed":...,"difficulty":...,"game":...} in `question`; reuse it if present
 * so a server-provided puzzle is reproducible, otherwise hash the puzzleId.
 */
export function seedOfPuzzle(puzzle) {
  try {
    const q = typeof puzzle?.question === 'string' ? JSON.parse(puzzle.question) : puzzle?.question
    if (q && q.seed != null) return Number(q.seed)
  } catch {
    /* fall through */
  }
  return hashString(puzzle?.puzzleId ?? `${Date.now()}`)
}
