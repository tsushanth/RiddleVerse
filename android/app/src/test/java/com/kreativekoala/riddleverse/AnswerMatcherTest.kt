package com.kreativekoala.riddleverse

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AnswerMatcherTest {
    private val matcher = AnswerMatcher()

    // ---- exact / normalization ---------------------------------------

    @Test fun exact_ignoresCaseWhitespaceAndPunctuation() {
        val r = matcher.isMatch("  The   MOON! ", "the moon")
        assertTrue(r.isMatch)
        assertEquals(MatchType.EXACT, r.matchType)
    }

    @Test fun exact_ignoresAccents() {
        assertEquals(MatchType.EXACT, matcher.isMatch("Café", "cafe").matchType)
    }

    // ---- wrong answers must be rejected ------------------------------

    @Test fun emptyAnswer_isNeverAMatch() {
        assertFalse(matcher.isMatch("", "moon").isMatch)
        assertFalse(matcher.isMatch("   ", "moon").isMatch)
        assertFalse(matcher.isMatch("!!!", "moon").isMatch)
    }

    @Test fun unrelatedAnswer_isRejected() {
        val r = matcher.isMatch("elephant", "mouse")
        assertFalse(r.isMatch)
        assertEquals(MatchType.NO_MATCH, r.matchType)
    }

    @Test fun onlyStopWords_areNotAMatch() {
        assertFalse(matcher.isMatch("the", "moon").isMatch)
    }

    // ---- synonyms / plurals / typos ----------------------------------

    @Test fun builtInSynonym_matches() {
        val r = matcher.isMatch("automobile", "car")
        assertTrue(r.isMatch)
        assertEquals(MatchType.SYNONYM, r.matchType)
    }

    @Test fun synonymsCanBeDisabled() {
        assertFalse(
            matcher.isMatch(
                "automobile", "car",
                allowSynonyms = false, allowPartialMatch = false, allowTypos = false
            ).isMatch
        )
    }

    @Test fun customSynonym_matches() {
        val r = matcher.isMatch("sofa", "couch", customSynonyms = mapOf("couch" to listOf("sofa")))
        assertTrue(r.isMatch)
    }

    @Test fun pluralAndSingular_match() {
        assertTrue(matcher.isMatch("cats", "cat").isMatch)
        assertTrue(matcher.isMatch("cat", "cats").isMatch)
        assertTrue(matcher.isMatch("cities", "city").isMatch)
    }

    @Test fun smallTypo_matchesWhenTyposAllowed() {
        val r = matcher.isMatch("elephnt", "elephant", allowPartialMatch = false, allowSynonyms = false)
        assertTrue(r.isMatch)
        assertEquals(MatchType.FUZZY, r.matchType)
    }

    @Test fun typo_isRejectedWhenTyposDisallowed() {
        val r = matcher.isMatch(
            "elephnt", "elephant",
            allowPartialMatch = false, allowSynonyms = false, allowTypos = false
        )
        assertFalse(r.isMatch)
    }

    @Test fun articleIsIgnoredForPartialMatch() {
        assertTrue(matcher.isMatch("a moon", "the moon").isMatch)
    }

    // ---- checkQAAnswer: numeric puzzles ------------------------------

    private fun math(user: String, correct: String) = checkQAAnswer(user, correct, puzzleType = "math")

    @Test fun math_exactIntegerMatches() {
        assertTrue(math("12", "12").isMatch)
        assertTrue(math(" 12 ", "12").isMatch)
        assertTrue(math("12.0", "12").isMatch)
    }

    @Test fun math_offByOneIsWrong() {
        // Regression: tolerance used to be max(1.0, ...) so 13 was accepted for 12.
        assertFalse(math("13", "12").isMatch)
        assertFalse(math("11", "12").isMatch)
        assertFalse(math("101", "100").isMatch)
    }

    @Test fun math_nearMissOnLargeNumbersIsWrong() {
        // Regression: string fuzzing accepted 1001 for 1000 (similarity 0.75).
        assertFalse(math("1001", "1000").isMatch)
        assertFalse(math("2499", "2500").isMatch)
    }

    @Test fun math_wordsAndFractionsAndPercent() {
        assertTrue(math("twelve", "12").isMatch)
        assertTrue(math("12", "twelve").isMatch)
        assertTrue(math("1/2", "0.5").isMatch)
        assertTrue(math("50%", "0.5").isMatch)
    }

    @Test fun math_decimalRoundingIsAccepted() {
        assertTrue(math("3.14", "3.14159").isMatch)
        assertFalse(math("3.2", "3.14159").isMatch)
    }

    @Test fun math_nonNumericAnswerIsWrong() {
        assertFalse(math("banana", "12").isMatch)
        assertFalse(math("", "12").isMatch)
    }

    @Test fun math_negativeNumbers() {
        assertTrue(math("-5", "-5").isMatch)
        assertFalse(math("5", "-5").isMatch)
    }

    @Test fun nonMathPuzzle_stillUsesTextMatching() {
        assertTrue(checkQAAnswer("Paris", "paris", puzzleType = "geography").isMatch)
        assertFalse(checkQAAnswer("London", "Paris", puzzleType = "geography").isMatch)
    }
}
