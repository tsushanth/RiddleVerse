package com.kreativekoala.riddleverse

import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import android.os.Looper
import java.time.Duration
import org.junit.Assert.assertEquals
import org.robolectric.Shadows.shadowOf
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.robolectric.annotation.Config

/**
 * End to end through the real screen: tapping an option must hand that option to onContinue so the wrapper can
 * judge it. Before the fix the screen always passed `true`, so a correct tap was reported as a wrong answer.
 */
class MultipleChoiceResultTest : LayoutMatrixBase() {

    private fun tap(option: String): Any? {
        var received: Any? = "not called"
        render(1f) {
            MultipleChoicePuzzleScreen(
                puzzleId = "p1", question = "What is 6 x 7?", difficulty = "Easy",
                options = listOf("42", "36", "48", "40"), selectedOption = null, correctAnswer = "42",
                timerSeconds = 60, questionNumber = 1, totalQuestions = 5,
                onOptionSelected = {}, onContinue = { received = it }, onCorrectAnswer = {}, onHint = {}, onBack = {}
            )
        }
        compose.onNodeWithText(option).performClick()
        compose.mainClock.advanceTimeBy(1000); compose.waitForIdle()
        // The feedback overlay hides itself on a Handler delay, then calls onContinue.
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(10))
        compose.waitForIdle()
        return received
    }

    @Test @Config(qualifiers = Devices.TYPICAL_PHONE)
    fun tappingTheCorrectOptionIsJudgedCorrect() {
        val received = tap("42")
        assertEquals("42", received)
        assertTrue(MultipleChoiceJudge.isCorrect(received, "42"))
    }

    @Test @Config(qualifiers = Devices.TYPICAL_PHONE)
    fun tappingAWrongOptionIsJudgedWrong() {
        val received = tap("36")
        assertEquals("36", received)
        assertFalse(MultipleChoiceJudge.isCorrect(received, "42"))
    }
}
