// --- web classic puzzles (feat/web-puzzles-classic) --- Disc Tower (Hanoi rules): pure game logic (port of DiscTowerLogic.kt)

export const PEGS = 3
export const TARGET_PEG = 2

export const discsFor = (tier) => [3, 4, 5, 6, 7][tier] ?? 5
export const parFor = (discs) => 2 ** discs - 1

/** pegs[p] lists disc sizes bottom to top (1 = smallest). */
export function newGame(discs) {
  return {
    discs,
    pegs: [Array.from({ length: discs }, (_, i) => discs - i), [], []],
    moves: 0,
    history: [],
  }
}

export const parOf = (g) => parFor(g.discs)
export const isSolved = (g) => g.pegs[TARGET_PEG].length === g.discs
export const canUndo = (g) => g.history.length > 0

export function canMove(g, from, to) {
  if (from === to || from < 0 || from >= PEGS || to < 0 || to >= PEGS) return false
  const src = g.pegs[from][g.pegs[from].length - 1]
  if (src === undefined) return false
  const dst = g.pegs[to][g.pegs[to].length - 1]
  return dst === undefined || dst > src
}

export function move(g, from, to) {
  if (!canMove(g, from, to)) return g
  const pegs = g.pegs.map((p) => p.slice())
  pegs[to].push(pegs[from].pop())
  return { ...g, pegs, moves: g.moves + 1, history: [...g.history, [from, to]] }
}

export function undo(g) {
  if (g.history.length === 0) return g
  const [from, to] = g.history[g.history.length - 1]
  const pegs = g.pegs.map((p) => p.slice())
  pegs[from].push(pegs[to].pop())
  return { ...g, pegs, moves: Math.max(0, g.moves - 1), history: g.history.slice(0, -1) }
}

const pegOf = (g, disc) => g.pegs.findIndex((p) => p.includes(disc))

function nextMove(g, k, target) {
  if (k === 0) return null
  const p = pegOf(g, k)
  if (p === target) return nextMove(g, k - 1, target)
  const spare = 3 - p - target // pegs are 0,1,2 so the third peg is 3 - a - b
  let smallerOnSpare = true
  for (let d = 1; d < k; d++) if (!g.pegs[spare].includes(d)) { smallerOnSpare = false; break }
  return smallerOnSpare ? [p, target] : nextMove(g, k - 1, spare)
}

/** Next move of a shortest solution from the current (legal) position to "all discs on the target peg". */
export function hintMove(g) {
  if (isSolved(g)) return null
  return nextMove(g, g.discs, TARGET_PEG)
}
