package com.kreativekoala.riddleverse

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** Result of a finished grid game. */
object GridStatus {
    const val PLAYING = 0
    const val WON = 1
    const val LOST = 2
}

/**
 * Mutable, Compose-observable play state for one grid puzzle. Pure logic (no UI) so it is unit-testable.
 * Serialises to an IntArray for rememberSaveable (rotation / process death).
 */
class GridPlayState(val spec: GridSpec) {
    val n = spec.n
    val cells = n * n

    val values = mutableStateListOf<Int>().apply { spec.given.forEach { add(it) } }
    val notes = mutableStateListOf<Int>().apply { repeat(cells) { add(0) } } // bitmask, bit d = candidate d
    var selected by mutableIntStateOf(-1)
    var lives by mutableIntStateOf(MAX_LIVES)
    var hintsLeft by mutableIntStateOf(MAX_HINTS)
    var hintsUsed by mutableIntStateOf(0)
    var mistakes by mutableIntStateOf(0)
    var elapsedSeconds by mutableIntStateOf(0)
    var notesMode by mutableStateOf(false)
    var status by mutableIntStateOf(GridStatus.PLAYING)
    var undoDepth by mutableIntStateOf(0)
    private val undoStack = ArrayList<IntArray>()

    fun isGiven(i: Int) = spec.given[i] != 0
    fun isWrong(i: Int) = values[i] != 0 && values[i] != spec.solution[i]
    val usesNotes: Boolean get() = spec.game != GridGame.BINAIRO

    private fun snapshot(): IntArray = IntArray(cells * 2) { if (it < cells) values[it] else notes[it - cells] }

    private fun push() {
        undoStack.add(snapshot())
        if (undoStack.size > MAX_UNDO) undoStack.removeAt(0)
        undoDepth = undoStack.size
    }

    fun undo() {
        if (status != GridStatus.PLAYING || undoStack.isEmpty()) return
        val s = undoStack.removeAt(undoStack.size - 1)
        for (i in 0 until cells) { values[i] = s[i]; notes[i] = s[cells + i] }
        undoDepth = undoStack.size
    }

    fun select(i: Int) { if (status == GridStatus.PLAYING) selected = if (selected == i) -1 else i }

    /** Enter symbol [d] (1..symbols) into the selected cell, or toggle a pencil note in notes mode. */
    fun enter(d: Int) {
        val i = selected
        if (status != GridStatus.PLAYING || i < 0 || isGiven(i) || d < 1 || d > spec.symbols) return
        if (notesMode && usesNotes) {
            if (values[i] != 0) return
            push()
            notes[i] = notes[i] xor (1 shl d)
            return
        }
        if (values[i] == d) { erase(); return }
        push()
        values[i] = d
        notes[i] = 0
        if (usesNotes) clearPeerNotes(i, d)
        if (d != spec.solution[i]) {
            mistakes++
            lives--
            if (lives <= 0) { status = GridStatus.LOST; return }
        }
        checkWin()
    }

    fun erase() {
        val i = selected
        if (status != GridStatus.PLAYING || i < 0 || isGiven(i)) return
        if (values[i] == 0 && notes[i] == 0) return
        push()
        values[i] = 0
        notes[i] = 0
    }

    /** Reveal one correct cell: the selected one if it is empty/wrong, otherwise the first empty/wrong cell. */
    fun hint(): Boolean {
        if (status != GridStatus.PLAYING || hintsLeft <= 0) return false
        val target = if (selected >= 0 && !isGiven(selected) && (values[selected] == 0 || isWrong(selected))) selected
        else (0 until cells).firstOrNull { !isGiven(it) && (values[it] == 0 || isWrong(it)) } ?: return false
        push()
        values[target] = spec.solution[target]
        notes[target] = 0
        if (usesNotes) clearPeerNotes(target, spec.solution[target])
        selected = target
        hintsLeft--
        hintsUsed++
        checkWin()
        return true
    }

    private fun clearPeerNotes(i: Int, d: Int) {
        val r = i / n
        val c = i % n
        val mask = (1 shl d).inv()
        for (k in 0 until n) {
            notes[r * n + k] = notes[r * n + k] and mask
            notes[k * n + c] = notes[k * n + c] and mask
        }
        if (spec.game == GridGame.SUDOKU) {
            val r0 = r / spec.boxRows * spec.boxRows
            val c0 = c / spec.boxCols * spec.boxCols
            for (dr in 0 until spec.boxRows) for (dc in 0 until spec.boxCols) {
                val k = (r0 + dr) * n + c0 + dc
                notes[k] = notes[k] and mask
            }
        }
    }

    private fun checkWin() {
        if ((0 until cells).all { values[it] == spec.solution[it] }) status = GridStatus.WON
    }

    fun tick() { if (status == GridStatus.PLAYING) elapsedSeconds++ }

    fun filledCount(d: Int): Int = (0 until cells).count { values[it] == d && values[it] == spec.solution[it] }

    /** Score awarded on a win: tier base, minus penalties, plus a speed bonus. */
    fun score(): Int {
        val base = when (spec.difficulty) { "easy" -> 60; "hard" -> 150; else -> 100 }
        val par = when (spec.game) {
            GridGame.SUDOKU -> when (spec.difficulty) { "easy" -> 90; "hard" -> 600; else -> 240 }
            GridGame.BINAIRO -> when (spec.difficulty) { "easy" -> 90; "hard" -> 420; else -> 200 }
            GridGame.CAGE_MATH -> when (spec.difficulty) { "easy" -> 120; "hard" -> 600; else -> 300 }
            GridGame.SIGN_GRID -> when (spec.difficulty) { "easy" -> 90; "hard" -> 480; else -> 240 }
        }
        val bonus = if (elapsedSeconds <= par) 30 else if (elapsedSeconds <= par * 2) 15 else 0
        return (base + bonus - mistakes * 10 - hintsUsed * 15).coerceAtLeast(10)
    }

    // ---- saveable encoding ----
    fun toSaved(): IntArray {
        val head = intArrayOf(lives, hintsLeft, hintsUsed, mistakes, elapsedSeconds, if (notesMode) 1 else 0, status, selected, undoStack.size)
        val out = ArrayList<Int>()
        head.forEach { out.add(it) }
        for (i in 0 until cells) out.add(values[i])
        for (i in 0 until cells) out.add(notes[i])
        undoStack.forEach { s -> s.forEach { out.add(it) } }
        return out.toIntArray()
    }

    fun restore(a: IntArray) {
        if (a.size < 9 + cells * 2) return
        lives = a[0]; hintsLeft = a[1]; hintsUsed = a[2]; mistakes = a[3]; elapsedSeconds = a[4]
        notesMode = a[5] == 1; status = a[6]; selected = a[7]
        val undoCount = a[8]
        for (i in 0 until cells) { values[i] = a[9 + i]; notes[i] = a[9 + cells + i] }
        undoStack.clear()
        var p = 9 + cells * 2
        repeat(undoCount) {
            if (p + cells * 2 <= a.size) { undoStack.add(a.copyOfRange(p, p + cells * 2)); p += cells * 2 }
        }
        undoDepth = undoStack.size
    }

    companion object {
        const val MAX_LIVES = 3
        const val MAX_HINTS = 3
        const val MAX_UNDO = 30
    }
}
