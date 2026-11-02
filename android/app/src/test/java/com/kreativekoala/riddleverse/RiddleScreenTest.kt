package com.kreativekoala.riddleverse

import android.app.Application
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.robolectric.annotation.Config

/**
 * Renders the riddle card on the JVM (Robolectric). The answer field and its Check button are pinned, so they must be on
 * screen WITHOUT scrolling even at large system font sizes; the riddle itself may scroll.
 */
class RiddleScreenTest : LayoutMatrixBase() {
    private val riddle = RiddleTestSupport.byId("e001") // clock; alt: watch, wall clock
    private var finished: Pair<Boolean, Int>? = null

    private fun show(fontScale: Float = 1f, r: Riddle = riddle) {
        finished = null
        render(fontScale) {
            RiddleScreen(r.toJson().toString(), "1/5", onBack = {}) { ok, score -> finished = ok to score }
        }
    }

    private fun type(text: String) {
        compose.onNode(hasSetTextAction()).performTextInput(text)
        compose.onNodeWithText("Check").performClick()
    }

    // ---- layout: font scale 1.3 (the in-game cap), 2.0 (accessibility maximum), small and compact phones ---------------

    private fun assertPlayable() {
        assertOnScreen(compose.onNodeWithText(riddle.question))
        assertOnScreen(compose.onNode(hasSetTextAction()))
        assertOnScreen(compose.onNodeWithText("Check"))
        assertOnScreen(compose.onNodeWithText("Skip"))
        assertOnScreen(compose.onNodeWithText("Reveal"))
        assertOnScreen(compose.onNodeWithText("Hint 1 of 3"))
    }

    @Test @Config(qualifiers = Devices.COMPACT_PHONE) fun compactPhone_font1_3_everythingOnScreen() { show(1.3f); assertPlayable() }
    @Test @Config(qualifiers = Devices.SMALL_PHONE) fun smallPhone_font2_0_everythingOnScreen() { show(2f); assertPlayable() }
    @Test @Config(qualifiers = Devices.TYPICAL_PHONE) fun typicalPhone_everythingOnScreen() { show(1f); assertPlayable() }
    @Test @Config(qualifiers = Devices.PHONE_LANDSCAPE) fun phoneLandscape_everythingOnScreen() { show(1f); assertPlayable() }
    @Test @Config(qualifiers = Devices.TABLET_PORTRAIT) fun tablet_everythingOnScreen() { show(1f); assertPlayable() }

    @Test @Config(qualifiers = Devices.SMALL_PHONE) fun longestRiddleStaysReachableOnSmallPhoneAtDoubleFont() {
        val longest = RiddleTestSupport.bank.maxByOrNull { it.question.length }!!
        show(2f, longest)
        // The riddle text may need scrolling here, but the answer controls must stay pinned and visible.
        assertOnScreen(compose.onNode(hasSetTextAction()))
        assertOnScreen(compose.onNodeWithText("Check"))
        compose.onNodeWithText(longest.question).assertExists()
    }

    // ---- behaviour -----------------------------------------------------------------------------------------------

    @Test @Config(qualifiers = Devices.COMPACT_PHONE) fun wrongAnswerGivesGentleFeedbackAndKeepsPlaying() {
        show()
        type("banana")
        compose.onNodeWithText("Not quite. Try again or take a hint.").assertIsDisplayed()
        assertNull(finished)
    }

    @Test @Config(qualifiers = Devices.COMPACT_PHONE) fun emptyAnswerAsksForOne() {
        show()
        compose.onNodeWithText("Check").performClick()
        compose.onNodeWithText("Type an answer first.").assertIsDisplayed()
    }

    @Test @Config(qualifiers = Devices.COMPACT_PHONE) fun hintsAreFreeButTakeStarsOff() {
        show()
        compose.onNodeWithContentDescription("3 of 3 stars").assertExists()
        compose.onNodeWithText("Hint 1 of 3").performClick()
        compose.onNodeWithText("Hint 1: " + riddle.hints[0]).assertIsDisplayed()
        compose.onNodeWithContentDescription("2 of 3 stars").assertExists()
        compose.onNodeWithText("Hint 2 of 3").performClick()
        compose.onNodeWithText("Hint 2: " + riddle.hints[1]).assertIsDisplayed()
        compose.onNodeWithContentDescription("1 of 3 stars").assertExists()
        compose.onNodeWithText("Hint 3 of 3").performClick()
        compose.onNodeWithText("Hint 3: " + riddle.hints[2]).assertIsDisplayed()
        compose.onNodeWithContentDescription("1 of 3 stars").assertExists()
        compose.onNodeWithText("No more hints").assertExists()
    }

    @Test @Config(qualifiers = Devices.COMPACT_PHONE) fun tolerantCorrectAnswerFinishesWithThreeStars() {
        show()
        type("  The CLOCKS! ")
        compose.onNodeWithText("Correct! The answer is Clock.").assertIsDisplayed()
        compose.onNodeWithText(riddle.explanation).assertIsDisplayed()
        compose.onNodeWithText("Stars earned: 3 of 3 (hints used: 0)").assertIsDisplayed()
        assertNull("completion must wait for the player to continue", finished)
        compose.onNodeWithText("Next riddle").performClick()
        assertEquals(true to 30, finished)
    }

    @Test @Config(qualifiers = Devices.COMPACT_PHONE) fun solvingAfterHintsScoresLess() {
        show()
        compose.onNodeWithText("Hint 1 of 3").performClick()
        type("watch")
        compose.onNodeWithText("Stars earned: 2 of 3 (hints used: 1)").assertIsDisplayed()
        compose.onNodeWithText("Next riddle").performClick()
        assertEquals(true to 20, finished)
    }

    @Test @Config(qualifiers = Devices.COMPACT_PHONE) fun revealShowsTheAnswerAfterConfirmationAndCountsAsUnsolved() {
        show()
        compose.onNodeWithText("Reveal").performClick()
        compose.onNodeWithText("Reveal the answer?").assertIsDisplayed()
        compose.onNodeWithText("Yes").performClick()
        compose.onNodeWithText("The answer is Clock.").assertIsDisplayed()
        compose.onNodeWithText("Next riddle").performClick()
        assertEquals(false to 0, finished)
    }

    @Test @Config(qualifiers = Devices.COMPACT_PHONE) fun cancellingRevealKeepsThePlayerInTheRiddle() {
        show()
        compose.onNodeWithText("Reveal").performClick()
        compose.onNodeWithText("Cancel").performClick()
        assertNull(finished)
        compose.onNodeWithText("Check").assertIsDisplayed()
    }

    @Test @Config(qualifiers = Devices.COMPACT_PHONE) fun skipMovesOnImmediatelyWhenThereIsNoStreak() {
        show()
        compose.onNodeWithText("Skip").performClick()
        assertEquals(false to 0, finished)
    }

    private fun storedStreak(): Int {
        val app = ApplicationProvider.getApplicationContext<Application>()
        return WordPuzzleStats(PrefsKeyValueStore(app)).snapshot(RiddlePack.STATS_KEY).streak
    }

    private fun seedStreak(n: Int) {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val stats = WordPuzzleStats(PrefsKeyValueStore(app))
        repeat(n) { stats.record(RiddlePack.STATS_KEY, true) }
    }

    @Test @Config(qualifiers = Devices.COMPACT_PHONE) fun streakGrowsWithCorrectAnswers() {
        seedStreak(2)
        show()
        compose.onNodeWithContentDescription("Correct answers in a row: 2").assertExists()
        type("clock")
        compose.onNodeWithContentDescription("Correct answers in a row: 3").assertExists()
        assertEquals(3, storedStreak())
    }

    @Test @Config(qualifiers = Devices.COMPACT_PHONE) fun skipWithAStreakAsksFirstThenBreaksIt() {
        seedStreak(2)
        show()
        compose.onNodeWithText("Skip").performClick()
        compose.onNodeWithText("Skip this riddle?").assertIsDisplayed()
        assertNull(finished)
        compose.onNodeWithText("Yes").performClick()
        assertEquals(false to 0, finished)
        assertEquals(0, storedStreak())
    }

    @Test @Config(qualifiers = Devices.COMPACT_PHONE) fun revealBreaksTheStreakToo() {
        seedStreak(4)
        show()
        compose.onNodeWithText("Reveal").performClick()
        compose.onNodeWithText("Yes").performClick()
        compose.waitForIdle()
        assertEquals(0, storedStreak())
    }

    @Test @Config(qualifiers = Devices.COMPACT_PHONE) fun wrongGuessesDoNotBreakTheStreak() {
        seedStreak(2)
        show()
        type("banana")
        type("cheese")
        assertEquals(2, storedStreak())
    }

    @Test @Config(qualifiers = Devices.COMPACT_PHONE) fun shareTextHasRiddleAndLinkButNeverTheAnswer() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        for (r in RiddleTestSupport.bank) {
            val text = RiddlePack.shareText(app, r)
            assertTrue(text.contains(r.question))
            assertTrue(text.contains(RiddlePack.PLAY_LINK))
            assertFalse("${r.id} share text must not contain the hints", r.hints.any { text.contains(it) })
            assertFalse("${r.id} share text must not contain the explanation", text.contains(r.explanation))
        }
        assertTrue(RiddlePack.shareText(app, riddle).endsWith(RiddlePack.PLAY_LINK))
    }
}
