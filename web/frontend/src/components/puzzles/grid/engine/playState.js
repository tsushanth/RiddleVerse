// Pure play-state logic for grid puzzles (port of Android GridPlayState). No React, no DOM.

export const STATUS = { PLAYING: 0, WON: 1, LOST: 2 }
export const MAX_LIVES = 3
export const MAX_HINTS = 3
export const MAX_UNDO = 30

export class GridPlayState {
  constructor(spec) {
    this.spec = spec
    this.n = spec.n
    this.cells = spec.n * spec.n
    this.values = spec.given.slice()
    this.notes = new Array(this.cells).fill(0) // bitmask, bit d = candidate d
    this.selected = -1
    this.lives = MAX_LIVES
    this.hintsLeft = MAX_HINTS
    this.hintsUsed = 0
    this.mistakes = 0
    this.moves = 0
    this.elapsedSeconds = 0
    this.notesMode = false
    this.status = STATUS.PLAYING
    this.undoStack = []
  }

  get undoDepth() {
    return this.undoStack.length
  }

  get usesNotes() {
    return this.spec.game !== 'binarygrid'
  }

  isGiven(i) {
    return this.spec.given[i] !== 0
  }

  isWrong(i) {
    return this.values[i] !== 0 && this.values[i] !== this.spec.solution[i]
  }

  _push() {
    this.undoStack.push([...this.values, ...this.notes])
    if (this.undoStack.length > MAX_UNDO) this.undoStack.shift()
  }

  undo() {
    if (this.status !== STATUS.PLAYING || this.undoStack.length === 0) return false
    const s = this.undoStack.pop()
    for (let i = 0; i < this.cells; i++) {
      this.values[i] = s[i]
      this.notes[i] = s[this.cells + i]
    }
    return true
  }

  select(i) {
    if (this.status === STATUS.PLAYING) this.selected = this.selected === i ? -1 : i
  }

  setSelected(i) {
    if (this.status === STATUS.PLAYING && i >= 0 && i < this.cells) this.selected = i
  }

  moveSelection(dr, dc) {
    if (this.status !== STATUS.PLAYING) return
    if (this.selected < 0) {
      this.selected = 0
      return
    }
    const r = Math.min(this.n - 1, Math.max(0, Math.floor(this.selected / this.n) + dr))
    const c = Math.min(this.n - 1, Math.max(0, (this.selected % this.n) + dc))
    this.selected = r * this.n + c
  }

  toggleNotesMode() {
    if (this.usesNotes) this.notesMode = !this.notesMode
  }

  /** Enter symbol d (1..symbols) in the selected cell, or toggle a pencil note in notes mode. */
  enter(d) {
    const i = this.selected
    if (this.status !== STATUS.PLAYING || i < 0 || this.isGiven(i) || d < 1 || d > this.spec.symbols) return
    if (this.notesMode && this.usesNotes) {
      if (this.values[i] !== 0) return
      this._push()
      this.notes[i] ^= 1 << d
      return
    }
    if (this.values[i] === d) {
      this.erase()
      return
    }
    this._push()
    this.moves++
    this.values[i] = d
    this.notes[i] = 0
    if (this.usesNotes) this._clearPeerNotes(i, d)
    if (d !== this.spec.solution[i]) {
      this.mistakes++
      this.lives--
      if (this.lives <= 0) {
        this.status = STATUS.LOST
        return
      }
    }
    this._checkWin()
  }

  erase() {
    const i = this.selected
    if (this.status !== STATUS.PLAYING || i < 0 || this.isGiven(i)) return
    if (this.values[i] === 0 && this.notes[i] === 0) return
    this._push()
    this.values[i] = 0
    this.notes[i] = 0
  }

  /** Reveal one correct cell: selected one if empty/wrong, otherwise the first empty/wrong cell. */
  hint() {
    if (this.status !== STATUS.PLAYING || this.hintsLeft <= 0) return false
    const needs = (k) => !this.isGiven(k) && (this.values[k] === 0 || this.isWrong(k))
    let target = -1
    if (this.selected >= 0 && needs(this.selected)) target = this.selected
    else {
      for (let k = 0; k < this.cells; k++) {
        if (needs(k)) {
          target = k
          break
        }
      }
    }
    if (target < 0) return false
    this._push()
    this.values[target] = this.spec.solution[target]
    this.notes[target] = 0
    if (this.usesNotes) this._clearPeerNotes(target, this.spec.solution[target])
    this.selected = target
    this.hintsLeft--
    this.hintsUsed++
    this._checkWin()
    return true
  }

  _clearPeerNotes(i, d) {
    const n = this.n
    const r = Math.floor(i / n)
    const c = i % n
    const mask = ~(1 << d)
    for (let k = 0; k < n; k++) {
      this.notes[r * n + k] &= mask
      this.notes[k * n + c] &= mask
    }
    if (this.spec.game === 'sudoku') {
      const r0 = Math.floor(r / this.spec.boxRows) * this.spec.boxRows
      const c0 = Math.floor(c / this.spec.boxCols) * this.spec.boxCols
      for (let dr = 0; dr < this.spec.boxRows; dr++)
        for (let dc = 0; dc < this.spec.boxCols; dc++) this.notes[(r0 + dr) * n + c0 + dc] &= mask
    }
  }

  _checkWin() {
    for (let k = 0; k < this.cells; k++) if (this.values[k] !== this.spec.solution[k]) return
    this.status = STATUS.WON
  }

  tick() {
    if (this.status === STATUS.PLAYING) this.elapsedSeconds++
  }

  filledCount(d) {
    let c = 0
    for (let k = 0; k < this.cells; k++) if (this.values[k] === d && this.values[k] === this.spec.solution[k]) c++
    return c
  }

  /** Score awarded on a win: tier base, minus penalties, plus a speed bonus. */
  score() {
    const diff = this.spec.difficulty
    const base = diff === 'easy' ? 60 : diff === 'hard' ? 150 : 100
    const pars = {
      sudoku: { easy: 90, medium: 240, hard: 600 },
      binarygrid: { easy: 90, medium: 200, hard: 420 },
      cagemath: { easy: 120, medium: 300, hard: 600 },
      signgrid: { easy: 90, medium: 240, hard: 480 },
    }
    const par = pars[this.spec.game][diff] ?? 240
    const bonus = this.elapsedSeconds <= par ? 30 : this.elapsedSeconds <= par * 2 ? 15 : 0
    return Math.max(10, base + bonus - this.mistakes * 10 - this.hintsUsed * 15)
  }

  /** Reveal the whole solution (used by "show answer"). Ends the game as neither won nor lost counting. */
  revealAll() {
    for (let k = 0; k < this.cells; k++) {
      this.values[k] = this.spec.solution[k]
      this.notes[k] = 0
    }
  }
}
