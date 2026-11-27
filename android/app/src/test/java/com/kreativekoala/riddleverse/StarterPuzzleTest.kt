package com.kreativekoala.riddleverse

import org.junit.Assert.*
import org.junit.Test

/** The first puzzle a new player meets must be short, tap only and always finishable. */
class StarterPuzzleTest {

    /** True when every blank can be filled one at a time because only one digit fits it. */
    private fun solvableBySingleCandidates(spec: GridSpec): Boolean {
        val n = spec.n
        val g = spec.given.copyOf()
        while (g.any { it == 0 }) {
            val next = g.indices.filter { g[it] == 0 }.firstNotNullOfOrNull { i ->
                val r = i / n
                val c = i % n
                val used = HashSet<Int>()
                for (k in 0 until n) { used += g[r * n + k]; used += g[k * n + c] }
                for (dr in 0 until 2) for (dc in 0 until 2) used += g[(r / 2 * 2 + dr) * n + c / 2 * 2 + dc]
                (1..n).filter { it !in used }.singleOrNull()?.let { i to it }
            } ?: return false
            g[next.first] = next.second
        }
        return g.contentEquals(spec.solution)
    }

    @Test fun five_blanks_each_solvable_by_looking_at_one_square() {
        for (seed in 0L until 500L) {
            val spec = StarterPuzzle.spec(seed)
            assertEquals(4, spec.n)
            assertEquals("seed $seed", 16 - StarterPuzzle.CLUES, spec.given.count { it == 0 })
            assertTrue("seed $seed", solvableBySingleCandidates(spec))
        }
    }

    @Test fun five_correct_taps_finish_it_without_selecting_cells() {
        val s = GridPlayState(StarterPuzzle.spec(7), forgiving = true)
        s.selectNextOpen()
        var taps = 0
        while (s.status == GridStatus.PLAYING && taps < 20) { s.enter(s.spec.solution[s.selected]); taps++ }
        assertEquals(GridStatus.WON, s.status)
        assertEquals(5, taps)
    }

    @Test fun wrong_taps_never_end_it() {
        val s = GridPlayState(StarterPuzzle.spec(7), forgiving = true)
        s.selectNextOpen()
        val cell = s.selected
        val wrong = (1..s.n).filter { it != s.spec.solution[cell] }
        repeat(10) { s.enter(wrong[it % wrong.size]) }
        assertEquals(GridStatus.PLAYING, s.status)
        assertEquals(GridPlayState.MAX_LIVES, s.lives)
        assertEquals("the selection stays on the wrong cell", cell, s.selected)
        s.enter(s.spec.solution[cell])
        assertFalse(s.isWrong(cell))
        assertNotEquals(cell, s.selected)
    }

    @Test fun puzzle_record_is_marked_as_the_starter() {
        val p = StarterPuzzle.create(42)
        assertTrue(StarterPuzzle.isStarter(p))
        assertEquals("sudoku", p.puzzleType)
        assertFalse(StarterPuzzle.isStarter(GridPuzzleFactory.createPuzzle(GridGame.SUDOKU, "easy", 42)))
        assertFalse(StarterPuzzle.isStarter(null))
        assertEquals("starter", PuzzleEventsClient.sanitizePuzzleType(StarterPuzzle.TELEMETRY_TYPE))
    }

    @Test fun offered_only_to_a_new_install_that_has_seen_no_puzzle() {
        val hour = 3600_000L
        assertTrue(StarterOffer.shouldAutoStart(hour, playedAny = false, offered = false))
        assertFalse(StarterOffer.shouldAutoStart(hour, playedAny = true, offered = false))
        assertFalse(StarterOffer.shouldAutoStart(hour, playedAny = false, offered = true))
        assertFalse(StarterOffer.shouldAutoStart(25 * hour, playedAny = false, offered = false))
        assertFalse(StarterOffer.shouldAutoStart(-hour, playedAny = false, offered = false))
    }

    @Test fun push_registration_reports_only_a_made_reminder_choice() {
        val undecided = PushRegistration.payload("a@b.c", "uid", "tok", "en", reminderChosen = false)
        assertFalse("serverDailyReminder" in undecided)
        val decided = PushRegistration.payload("a@b.c", "uid", "tok", "en", reminderChosen = true)
        assertEquals(false, decided["serverDailyReminder"])
        assertEquals("android", decided["platform"])
        assertEquals("tok", decided["fcmToken"])
    }
}
