// --- web spatial puzzles (feat/web-puzzles-spatial) ---
// Piece Pack (block placement) engine. Port of Android PiecePackEngine.kt.
// 8x8 board, hand of three pieces; completed rows/columns clear. Win: clear `targetLines` lines
// using at most `pieceBudget` pieces. Fail: no piece in hand fits, or the budget runs out.
import { makeRng } from './rng.js'

export const N = 8

function shape(id, name, ...rc) {
  const cells = rc.map(([r, c]) => r * 10 + c)
  return {
    id,
    name,
    cells,
    rows: Math.max(...cells.map((x) => Math.floor(x / 10))) + 1,
    cols: Math.max(...cells.map((x) => x % 10)) + 1,
    size: cells.length,
  }
}
const bar = (id, name, len, horizontal) =>
  shape(id, name, ...Array.from({ length: len }, (_, i) => (horizontal ? [0, i] : [i, 0])))

export const SHAPES = [
  shape(0, 'dot', [0, 0]),
  bar(1, 'pair-h', 2, true), bar(2, 'pair-v', 2, false),
  bar(3, 'bar3-h', 3, true), bar(4, 'bar3-v', 3, false),
  bar(5, 'bar4-h', 4, true), bar(6, 'bar4-v', 4, false),
  bar(7, 'bar5-h', 5, true), bar(8, 'bar5-v', 5, false),
  shape(9, 'square', [0, 0], [0, 1], [1, 0], [1, 1]),
  shape(10, 'corner-a', [0, 0], [1, 0], [1, 1]),
  shape(11, 'corner-b', [0, 1], [1, 0], [1, 1]),
  shape(12, 'corner-c', [0, 0], [0, 1], [1, 0]),
  shape(13, 'corner-d', [0, 0], [0, 1], [1, 1]),
  shape(14, 'rect-wide', [0, 0], [0, 1], [0, 2], [1, 0], [1, 1], [1, 2]),
  shape(15, 'rect-tall', [0, 0], [0, 1], [1, 0], [1, 1], [2, 0], [2, 1]),
  shape(16, 'hook-a', [0, 0], [1, 0], [2, 0], [2, 1]),
  shape(17, 'hook-b', [0, 1], [1, 1], [2, 1], [2, 0]),
  shape(18, 'hook-c', [0, 0], [0, 1], [1, 0], [2, 0]),
  shape(19, 'hook-d', [0, 0], [0, 1], [1, 1], [2, 1]),
  shape(20, 'tee', [0, 0], [0, 1], [0, 2], [1, 1]),
  shape(21, 'tee-down', [0, 1], [1, 0], [1, 1], [1, 2]),
  shape(22, 'zig', [0, 0], [0, 1], [1, 1], [1, 2]),
  shape(23, 'zag', [0, 1], [0, 2], [1, 0], [1, 1]),
  shape(24, 'big-corner', [0, 0], [1, 0], [2, 0], [2, 1], [2, 2]),
]

export function configFor(difficulty) {
  const d = String(difficulty).toLowerCase()
  if (d === 'easy') return { targetLines: 4, pieceBudget: 27, smallBias: 3 }
  if (d === 'hard') return { targetLines: 9, pieceBudget: 33, smallBias: 0 }
  return { targetLines: 6, pieceBudget: 30, smallBias: 1 }
}

/**
 * Maps a pointer position to the board origin [row, col] for dropping piece `p`. The piece is
 * centred on x and sits `lift` above y (so the finger does not hide it). Rounded to the nearest
 * cell and clamped so the whole piece stays on the board.
 */
export function dropOrigin(x, y, p, left, top, cell, lift) {
  // Math.round on .5 differs from Kotlin only for negative halves; clamping hides that.
  const c = Math.round((x - (p.cols * cell) / 2 - left) / cell)
  const r = Math.round((y - p.rows * cell - lift - top) / cell)
  return [clamp(r, 0, N - p.rows), clamp(c, 0, N - p.cols)]
}

const clamp = (v, lo, hi) => Math.min(Math.max(v, lo), hi)

export function fits(board, p, r, c) {
  if (r < 0 || c < 0 || r + p.rows > N || c + p.cols > N) return false
  for (const cell of p.cells) if (board[(r + Math.floor(cell / 10)) * N + (c + (cell % 10))] !== 0) return false
  return true
}

export function anyFit(board, p) {
  for (let r = 0; r <= N - p.rows; r++) for (let c = 0; c <= N - p.cols; c++) if (fits(board, p, r, c)) return true
  return false
}

/** Places the piece in place and clears completed lines. Returns the number of lines cleared. */
export function place(board, p, r, c) {
  for (const cell of p.cells) board[(r + Math.floor(cell / 10)) * N + (c + (cell % 10))] = p.id + 1
  const rowFull = []
  const colFull = []
  for (let i = 0; i < N; i++) {
    let rf = true
    let cf = true
    for (let j = 0; j < N; j++) {
      if (board[i * N + j] === 0) rf = false
      if (board[j * N + i] === 0) cf = false
    }
    rowFull[i] = rf
    colFull[i] = cf
  }
  let lines = 0
  for (let i = 0; i < N; i++) {
    if (rowFull[i]) {
      lines++
      for (let j = 0; j < N; j++) board[i * N + j] = 0
    }
    if (colFull[i]) {
      lines++
      for (let j = 0; j < N; j++) board[j * N + i] = 0
    }
  }
  return lines
}

export function dealPiece(rnd, smallBias) {
  const weighted = []
  for (const s of SHAPES) {
    let w = 2
    if (s.size <= 3) w += smallBias
    if (s.size >= 5 && smallBias >= 3) w = 1
    for (let i = 0; i < w; i++) weighted.push(s)
  }
  return weighted[rnd.nextInt(weighted.length)]
}

export function deal(seed, round, board, smallBias) {
  let last = [0, 0, 0]
  for (let attempt = 0; attempt < 8; attempt++) {
    const rnd = makeRng(`${seed}:${round}:${attempt}`)
    const ids = [0, 1, 2].map(() => dealPiece(rnd, smallBias).id)
    last = ids
    if (ids.some((id) => anyFit(board, SHAPES[id]))) return ids
  }
  return last
}

/** Heuristic best move among hand slots as [slot,row,col], or null. Used for hints. */
export function bestMove(board, hand) {
  let best = null
  let bestScore = -Infinity
  for (let slot = 0; slot < hand.length; slot++) {
    const id = hand[slot]
    if (id < 0) continue
    const p = SHAPES[id]
    for (let r = 0; r <= N - p.rows; r++) {
      for (let c = 0; c <= N - p.cols; c++) {
        if (!fits(board, p, r, c)) continue
        const tmp = Array.from(board)
        const lines = place(tmp, p, r, c)
        let s = lines * 1000
        let filled = 0
        let isolated = 0
        for (let i = 0; i < N; i++) {
          for (let j = 0; j < N; j++) {
            if (tmp[i * N + j] !== 0) {
              filled++
              continue
            }
            let free = 0
            if (i > 0 && tmp[(i - 1) * N + j] === 0) free++
            if (i < N - 1 && tmp[(i + 1) * N + j] === 0) free++
            if (j > 0 && tmp[i * N + j - 1] === 0) free++
            if (j < N - 1 && tmp[i * N + j + 1] === 0) free++
            if (free === 0) isolated++
          }
        }
        s -= isolated * 40
        s -= filled * 3
        for (const cell of p.cells) {
          const rr = r + Math.floor(cell / 10)
          const cc = c + (cell % 10)
          if (rr === 0 || rr === N - 1) s += 2
          if (cc === 0 || cc === N - 1) s += 2
        }
        if (s > bestScore) {
          bestScore = s
          best = [slot, r, c]
        }
      }
    }
  }
  return best
}

export const STATUS = { PLAYING: 'playing', WON: 'won', STUCK: 'stuck', OUT_OF_PIECES: 'out_of_pieces' }

export class PiecePackState {
  constructor(seed, difficulty) {
    this.seed = seed
    this.config = configFor(difficulty)
    this.undoStack = []
    this.hintsUsed = 0
    this.reset()
  }

  reset() {
    this.board = new Array(N * N).fill(0)
    this.round = 0
    this.piecesPlaced = 0
    this.linesCleared = 0
    this.score = 0
    this.status = STATUS.PLAYING
    this.hand = deal(this.seed, 0, this.board, this.config.smallBias)
    this.undoStack = []
  }

  canUndo() {
    return this.undoStack.length > 0 && this.status !== STATUS.WON
  }

  piecesLeft() {
    return this.config.pieceBudget - this.piecesPlaced
  }

  tryPlace(slot, r, c) {
    if (this.status !== STATUS.PLAYING) return false
    const id = this.hand[slot]
    if (id === undefined || id < 0) return false
    const p = SHAPES[id]
    if (!fits(this.board, p, r, c)) return false
    this.undoStack.push({
      board: this.board.slice(),
      hand: this.hand.slice(),
      round: this.round,
      placed: this.piecesPlaced,
      lines: this.linesCleared,
      score: this.score,
      status: this.status,
    })
    const lines = place(this.board, p, r, c)
    this.hand[slot] = -1
    this.piecesPlaced++
    this.linesCleared += lines
    this.score += p.size + lines * lines * 10
    if (this.hand.every((v) => v < 0)) {
      this.round++
      this.hand = deal(this.seed, this.round, this.board, this.config.smallBias)
    }
    this.status = this.evaluate()
    return true
  }

  evaluate() {
    if (this.linesCleared >= this.config.targetLines) return STATUS.WON
    if (this.piecesPlaced >= this.config.pieceBudget) return STATUS.OUT_OF_PIECES
    const any = this.hand.some((id) => id >= 0 && anyFit(this.board, SHAPES[id]))
    return any ? STATUS.PLAYING : STATUS.STUCK
  }

  undoMove() {
    if (!this.canUndo()) return false
    const s = this.undoStack.pop()
    this.board = s.board
    this.hand = s.hand
    this.round = s.round
    this.piecesPlaced = s.placed
    this.linesCleared = s.lines
    this.score = s.score
    this.status = s.status
    return true
  }

  restart() {
    this.reset()
  }

  hint() {
    return bestMove(this.board, this.hand)
  }

  finalScore() {
    const bonus = Math.max(this.piecesLeft(), 0) * 3
    return Math.max(50 + Math.floor(this.score / 2) + bonus, 10)
  }

  stars() {
    const b = this.config.pieceBudget
    return this.piecesLeft() >= Math.floor(b / 3) ? 3 : this.piecesLeft() >= Math.floor(b / 6) ? 2 : 1
  }
}
