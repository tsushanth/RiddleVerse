package com.kreativekoala.riddleverse

import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertEquals
import org.junit.Test
import org.robolectric.annotation.Config

/**
 * Memory Match (memoryprevioussingle) must keep ONE symbol sequence for the whole game. The screen used to
 * regenerate the puzzle whenever the adaptive difficulty moved (after ~2 answers), while the question index,
 * hearts and score carried over: the "previous symbol" the player was holding in their head changed under them.
 */
class MemorySingleSessionTest : LayoutMatrixBase() {

    private fun pause() {
        Thread.sleep(1650)                       // old code waited in real time (GlobalScope)
        compose.mainClock.advanceTimeBy(1700)    // new code waits on the compose clock
        compose.waitForIdle()
    }

    @Test @Config(qualifiers = Devices.TYPICAL_PHONE)
    fun puzzleIsGeneratedOnce_evenWhenAdaptationFires() {
        val before = SimplifiedAdaptiveMemoryGenerator.generatedCount
        render(1f) { SimplifiedAdaptiveMemoryScreen("Easy", "2:00", 3, "1/5", {}, {}, {}) }
        compose.mainClock.advanceTimeBy(700); compose.waitForIdle()
        compose.onNodeWithText("START CHALLENGE").performClick()
        compose.mainClock.advanceTimeBy(3000); compose.waitForIdle()   // first symbol auto-advances after 2 s
        // Alternate answers so some are wrong: accuracy swings are what move the adaptive level.
        repeat(4) {
            compose.onNodeWithText("DIFFERENT").performClick(); pause()
            compose.onNodeWithText("SAME").performClick(); pause()
        }
        assertEquals("sequence regenerated mid-game", 1, SimplifiedAdaptiveMemoryGenerator.generatedCount - before)
    }
}
