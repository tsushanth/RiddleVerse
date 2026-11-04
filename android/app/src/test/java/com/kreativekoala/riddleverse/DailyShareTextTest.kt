package com.kreativekoala.riddleverse

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DailyShareTextTest {
    private fun r(n: Int = 123, secs: Int = 102, tries: Int = 1, streak: Int = 5, game: String = "Sudoku") =
        DailyShareResult(n, game, secs, tries, streak)

    @Test fun headline_matchesTheSpecExample() {
        assertEquals("RiddleVerse Daily #123 ✅ 1:42 🔥" + "5", DailyShareText.headline(r()))
    }

    @Test fun headline_omitsFlameWhenNoStreak() {
        assertEquals("RiddleVerse Daily #7 ✅ 0:07", DailyShareText.headline(r(n = 7, secs = 7, streak = 0)))
    }

    @Test fun time_formats() {
        assertEquals("0:00", DailyShareText.formatTime(0))
        assertEquals("0:59", DailyShareText.formatTime(59))
        assertEquals("1:00", DailyShareText.formatTime(60))
        assertEquals("59:59", DailyShareText.formatTime(3599))
        assertEquals("1:00:00", DailyShareText.formatTime(3600))
        assertEquals("1:05:09", DailyShareText.formatTime(3909))
        assertEquals("0:00", DailyShareText.formatTime(-5))
    }

    @Test fun grid_oneSquarePerTry_lastIsGreen() {
        assertEquals("🟩", DailyShareText.attemptGrid(1))
        assertEquals("🟥🟥🟩", DailyShareText.attemptGrid(3))
        assertEquals("🟩", DailyShareText.attemptGrid(0))
        val long = DailyShareText.attemptGrid(40)
        assertTrue(long.endsWith("🟩")); assertTrue(long.contains("…"))
    }

    @Test fun fullText_hasHeadline_game_grid_andPlayLink() {
        val lines = DailyShareText.build(r(tries = 2)).split("\n")
        assertEquals(3, lines.size)
        assertEquals(DailyShareText.headline(r(tries = 2)), lines[0])
        assertEquals("Sudoku 🟥🟩", lines[1])
        assertEquals(DailyShareText.PLAY_URL, lines[2])
    }

    @Test fun playLink_isExactlyTheCampaignUrl() {
        assertEquals(
            "https://play.google.com/store/apps/details?id=com.kreativekoala.riddleverse&utm_source=share&utm_medium=daily&utm_campaign=card",
            DailyShareText.PLAY_URL,
        )
    }

    @Test fun text_neverContainsSeedOrAnswerFields() {
        // The formatter only receives number/game/time/tries/streak; make sure no raw puzzle data shape can appear.
        val spec = DailyPuzzleSchedule.specFor(DailyPuzzleSchedule.FIRST_DAY + 3)
        val text = DailyShareText.build(r(game = PuzzleTypeNames.display(spec.type)))
        assertFalse(text.contains(spec.seed.toString()))
        assertFalse(text.contains("solution", ignoreCase = true))
    }
}
