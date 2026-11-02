package com.kreativekoala.riddleverse

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RiddleMatcherTest {
    private fun riddle(answer: String, vararg alt: String) =
        Riddle("e000", RiddleTier.EASY, "Q?", answer, alt.toList(), listOf("a", "b", "c"), "why")

    private fun ok(input: String, answer: String, vararg alt: String) =
        RiddleMatcher.isCorrect(input, riddle(answer, *alt))

    @Test fun ignoresCaseWhitespacePunctuationAndAccents() {
        assertTrue(ok("  CLOCK!! ", "clock"))
        assertTrue(ok("Cl-ock", "clock")) // a stray hyphen or space inside the word is forgiven
        assertTrue(ok("cafe", "café"))
        assertTrue(ok("Café", "cafe"))
    }

    @Test fun ignoresLeadingArticlesAndFillers() {
        assertTrue(ok("a clock", "clock"))
        assertTrue(ok("An Elephant", "elephant"))
        assertTrue(ok("the moon", "moon"))
        assertTrue(ok("it's a clock", "clock"))
        assertTrue(ok("It is the clock.", "clock"))
        assertTrue(ok("I think it is a clock", "clock"))
        assertTrue(ok("clock", "a clock"))
    }

    @Test fun acceptsSimplePluralsBothWays() {
        assertTrue(ok("clocks", "clock"))
        assertTrue(ok("clock", "clocks"))
        assertTrue(ok("cities", "city"))
        assertTrue(ok("boxes", "box"))
        assertTrue(ok("leaves", "leaf"))
        assertTrue(ok("knives", "knife"))
        assertTrue(ok("glasses", "glass"))
        assertTrue(ok("deck of card", "deck of cards"))
    }

    @Test fun acceptsAlternates() {
        assertTrue(ok("watch", "clock", "watch"))
        assertTrue(ok("The Watches", "clock", "watch"))
    }

    @Test fun rejectsWrongEmptyAndStopWordOnlyAnswers() {
        assertFalse(ok("", "clock"))
        assertFalse(ok("   ", "clock"))
        assertFalse(ok("!!!", "clock"))
        assertFalse(ok("the", "clock"))
        assertFalse(ok("a", "clock"))
        assertFalse(ok("cloak", "clock")) // 5 letters: no typo tolerance
        assertFalse(ok("oak", "tree")) // no synonym or category guessing
        assertFalse(ok("tree", "oak"))
    }

    @Test fun shortAnswersAndNumbersMustBeExact() {
        assertFalse(ok("12", "13", "thirteen"))
        assertFalse(ok("1943", "194"))
        assertTrue(ok("thirteen", "13", "thirteen"))
        assertFalse(ok("monk", "monkey")) // missing letters are not tolerated on 6-letter words
    }

    @Test fun toleratesOneTypoOnLongAnswersOnly() {
        assertTrue(ok("elephnat", "elephant")) // swapped neighbours count as one edit
        assertTrue(ok("elephnt", "elephant"))
        assertTrue(ok("elefant", "elephant").not()) // two edits
        assertTrue(ok("telescpe", "telescope"))
        assertFalse(ok("telscpe", "telescope")) // two edits on a 9-letter word
        assertTrue(ok("thermomter", "thermometer"))
        assertTrue(ok("thermometr", "thermometer"))
    }

    @Test fun singleLetterAnswersGetNoTypoTolerance() {
        assertFalse(ok("the letter R", "the letter M", "m"))
        assertTrue(ok("letter m", "the letter M", "m"))
    }

    @Test fun extraSpacesInsideCompoundWordsAreForgiven() {
        assertTrue(ok("tea pot", "teapot"))
        assertTrue(ok("teapot", "tea pot"))
        assertTrue(ok("book keeper", "bookkeeper"))
    }

    @Test fun editDistanceIsCorrect() {
        assertEquals(0, RiddleMatcher.editDistance("abc", "abc"))
        assertEquals(1, RiddleMatcher.editDistance("abc", "abd"))
        assertEquals(1, RiddleMatcher.editDistance("abc", "abcd"))
        assertEquals(3, RiddleMatcher.editDistance("kitten", "sitting"))
        assertEquals(3, RiddleMatcher.editDistance("", "abc"))
        assertEquals(1, RiddleMatcher.editDistance("ab", "ba"))
    }

    @Test fun turkishLocaleDoesNotBreakNormalisation() {
        val old = java.util.Locale.getDefault()
        try {
            java.util.Locale.setDefault(java.util.Locale("tr", "TR"))
            assertTrue(ok("PIANO", "piano"))
            assertTrue(ok("ICE", "ice"))
        } finally { java.util.Locale.setDefault(old) }
    }
}
