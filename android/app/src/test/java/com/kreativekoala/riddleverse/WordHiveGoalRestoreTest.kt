package com.kreativekoala.riddleverse

import android.app.Application
import android.content.Context
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.robolectric.annotation.Config

/** Restored games: one already past the goal (but short of the old, higher one) finishes as a win; a result counts once. */
class WordHiveGoalRestoreTest : LayoutMatrixBase() {

    @Test @Config(qualifiers = Devices.TYPICAL_PHONE)
    fun restoredGamePastTheGoalFinishesAsSolved() {
        val puzzle = WordHiveGenerator.generate(WordPuzzleTestSupport.lists, "Medium", 7L)!!
        val json = puzzle.toJson(7L, "Medium").toString()
        var result: Pair<Boolean, Int>? = null
        val restoration = StateRestorationTester(compose)
        restoration.setContent { WordHiveScreen(json, "1/1", {}, { solved, score -> result = solved to score }) }

        var score = 0
        for (word in puzzle.words) {
            if (score * 100 >= puzzle.maxScore * 25) break
            for (letter in word) {
                val centre = if (letter == puzzle.center) ", centre letter, required" else ""
                compose.onNodeWithContentDescription("Letter ${letter.uppercaseChar()}$centre").performScrollTo().performClick()
            }
            compose.onNodeWithText("Enter").performClick()
            score += WordHiveRules.wordScore(word, puzzle.letterSet)
        }
        check(score * 100 < puzzle.maxScore * 50) { "the test game must stay below 50%" }
        restoration.emulateSavedInstanceStateRestore()

        compose.onNodeWithContentDescription("Finish with your rank").performClick()
        compose.onNodeWithText("Continue").performScrollTo().performClick()
        compose.waitForIdle()
        assertEquals(true, result?.first)
    }

    @Test @Config(qualifiers = Devices.TYPICAL_PHONE)
    fun restoredResultCardIsCountedOnce() {
        val puzzle = WordHiveGenerator.generate(WordPuzzleTestSupport.lists, "Medium", 7L)!!
        val restoration = StateRestorationTester(compose)
        restoration.setContent { WordHiveScreen(puzzle.toJson(7L, "Medium").toString(), "1/1", {}, { _, _ -> }) }
        val prefs = ApplicationProvider.getApplicationContext<Application>().getSharedPreferences("word_puzzle_stats", Context.MODE_PRIVATE)

        compose.onNodeWithContentDescription("Give up and see results").performClick()
        compose.waitForIdle()
        restoration.emulateSavedInstanceStateRestore()
        compose.waitForIdle()

        assertEquals(1, prefs.getInt("wordhive_played", 0))
    }
}
