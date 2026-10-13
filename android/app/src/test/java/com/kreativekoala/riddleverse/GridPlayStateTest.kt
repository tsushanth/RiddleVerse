package com.kreativekoala.riddleverse

import org.junit.Assert.*
import org.junit.Test

/** Game-logic tests for GridPlayState (mistakes, notes, hints, undo, win, save/restore). */
class GridPlayStateTest {

    private fun sudokuState(): GridPlayState = GridPlayState(GridPuzzleFactory.build(GridGame.SUDOKU, "easy", 5))

    private fun firstEmpty(s: GridPlayState) = (0 until s.cells).first { !s.isGiven(it) }

    @Test fun correct_entry_has_no_penalty_and_wrong_costs_a_life() {
        val s = sudokuState()
        val i = firstEmpty(s)
        s.select(i)
        s.enter(s.spec.solution[i])
        assertEquals(GridPlayState.MAX_LIVES, s.lives)
        assertFalse(s.isWrong(i))
        val j = (0 until s.cells).first { !s.isGiven(it) && s.values[it] == 0 }
        s.select(j)
        val wrong = (1..s.n).first { it != s.spec.solution[j] }
        s.enter(wrong)
        assertEquals(GridPlayState.MAX_LIVES - 1, s.lives)
        assertTrue(s.isWrong(j))
        assertEquals(1, s.mistakes)
    }

    @Test fun three_mistakes_lose() {
        val s = sudokuState()
        val empties = (0 until s.cells).filter { !s.isGiven(it) }.take(3)
        for (i in empties) { s.select(i); s.enter((1..s.n).first { it != s.spec.solution[i] }) }
        assertEquals(GridStatus.LOST, s.status)
    }

    @Test fun givens_cannot_be_changed() {
        val s = sudokuState()
        val g = (0 until s.cells).first { s.isGiven(it) }
        val before = s.values[g]
        s.select(g); s.enter(if (before == 1) 2 else 1); s.erase()
        assertEquals(before, s.values[g])
        assertEquals(GridPlayState.MAX_LIVES, s.lives)
    }

    @Test fun solving_every_cell_wins_and_scores() {
        val s = sudokuState()
        for (i in 0 until s.cells) if (!s.isGiven(i)) { s.select(i); s.enter(s.spec.solution[i]) }
        assertEquals(GridStatus.WON, s.status)
        assertTrue(s.score() >= 10)
    }

    @Test fun notes_toggle_and_peer_clearing() {
        val s = sudokuState()
        val i = firstEmpty(s)
        val sol = s.spec.solution[i]
        // note the solution digit in a peer cell in the same row
        val peer = (0 until s.n).map { (i / s.n) * s.n + it }.first { it != i && !s.isGiven(it) }
        s.notesMode = true
        s.select(peer); s.enter(sol)
        assertTrue(s.notes[peer] and (1 shl sol) != 0)
        s.enter(sol)
        assertEquals(0, s.notes[peer])
        s.enter(sol)
        s.notesMode = false
        s.select(i); s.enter(sol)
        assertEquals(0, s.notes[peer] and (1 shl sol))
    }

    @Test fun hint_reveals_correct_value_and_is_limited() {
        val s = sudokuState()
        repeat(GridPlayState.MAX_HINTS) { assertTrue(s.hint()) }
        assertFalse(s.hint())
        assertEquals(0, s.hintsLeft)
        assertEquals(GridPlayState.MAX_HINTS, s.hintsUsed)
        assertEquals(0, s.mistakes)
    }

    @Test fun undo_restores_previous_state() {
        val s = sudokuState()
        val i = firstEmpty(s)
        s.select(i); s.enter(s.spec.solution[i])
        assertEquals(1, s.undoDepth)
        s.undo()
        assertEquals(0, s.values[i])
        assertEquals(0, s.undoDepth)
    }

    @Test fun save_and_restore_round_trip() {
        val s = sudokuState()
        val i = firstEmpty(s)
        s.select(i); s.enter(s.spec.solution[i]); s.hint(); s.tick(); s.notesMode = true
        val saved = s.toSaved()
        val r = GridPlayState(s.spec).apply { restore(saved) }
        assertEquals(s.values.toList(), r.values.toList())
        assertEquals(s.notes.toList(), r.notes.toList())
        assertEquals(s.hintsLeft, r.hintsLeft); assertEquals(s.elapsedSeconds, r.elapsedSeconds)
        assertEquals(s.undoDepth, r.undoDepth); assertTrue(r.notesMode)
        assertArrayEquals(saved, r.toSaved())
    }

    @Test fun binary_grid_pad_has_two_symbols_and_no_notes() {
        val s = GridPlayState(GridPuzzleFactory.build(GridGame.BINAIRO, "easy", 3))
        assertEquals(2, s.spec.symbols)
        assertFalse(s.usesNotes)
        val i = firstEmpty(s)
        s.notesMode = true
        s.select(i); s.enter(s.spec.solution[i])
        assertEquals(s.spec.solution[i], s.values[i]) // notes mode ignored for binary grid
    }

    @Test fun every_game_is_completable_via_state() {
        for (g in GridGame.values()) for (d in listOf("easy", "medium")) {
            val s = GridPlayState(GridPuzzleFactory.build(g, d, 21))
            for (i in 0 until s.cells) if (!s.isGiven(i)) { s.select(i); s.enter(s.spec.solution[i]) }
            assertEquals("$g/$d", GridStatus.WON, s.status)
        }
    }
}
