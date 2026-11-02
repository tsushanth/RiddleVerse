package com.kreativekoala.riddleverse

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MultipleChoiceJudgeTest {

    @Test fun theTappedOptionIsJudgedAgainstTheAnswerKey() {
        assertTrue(MultipleChoiceJudge.isCorrect("Joyful", "Joyful"))
        assertFalse(MultipleChoiceJudge.isCorrect("Sad", "Joyful"))
    }

    @Test fun aBooleanIsNeverACorrectAnswer() {
        // The screen used to call onContinue(true) and the wrappers compared that to the answer string,
        // which marked every Math/Trivia/Q&A answer wrong (0 completes / 11 fails in the field).
        assertFalse(MultipleChoiceJudge.isCorrect(true, "true"))
        assertFalse(MultipleChoiceJudge.isCorrect(true, "42"))
        assertFalse(MultipleChoiceJudge.isCorrect(null, "42"))
    }

    @Test fun formattingDifferencesDoNotMakeACorrectAnswerWrong() {
        assertTrue(MultipleChoiceJudge.isCorrect("  joyful ", "Joyful"))
        assertTrue(MultipleChoiceJudge.isCorrect("42", "42.0"))
        assertTrue(MultipleChoiceJudge.isCorrect("42.00", "42"))
        assertTrue(MultipleChoiceJudge.isCorrect("1,000", "1000"))
        assertFalse(MultipleChoiceJudge.isCorrect("42", "43"))
        assertFalse(MultipleChoiceJudge.isCorrect("4", "42"))
    }

    @Test fun blankIsNeverAMatch() {
        assertFalse(MultipleChoiceJudge.isCorrect("", ""))
        assertFalse(MultipleChoiceJudge.isCorrect("  ", ""))
        assertFalse(MultipleChoiceJudge.sameAnswer("", "x"))
    }
}
