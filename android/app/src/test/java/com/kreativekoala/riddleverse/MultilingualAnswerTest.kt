package com.kreativekoala.riddleverse

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Answers typed in non-Latin scripts used to normalise to "" (every non-ASCII letter was dropped), so they never matched. */
class MultilingualAnswerTest {
    private fun riddle(answer: String, vararg alt: String) =
        Riddle("m000", RiddleTier.EASY, "Q?", answer, alt.toList(), listOf("a", "b", "c"), "why")

    private fun ok(input: String, answer: String, vararg alt: String) = RiddleMatcher.isCorrect(input, riddle(answer, *alt))
    private val am = AnswerMatcher()
    private fun qa(input: String, answer: String) = am.isMatch(input, answer).isMatch

    @Test fun arabicLettersAreKeptAndFolded() {
        assertTrue(ok("ساعة", "ساعة"))
        assertTrue(ok("سَاعَةٌ", "ساعة")) // tashkeel ignored
        assertTrue(ok("ساعه", "ساعة")) // ta marbuta ~ ha
        assertTrue(ok("اسد", "أسد")) // alef with hamza ~ alef
        assertFalse(ok("قلم", "ساعة"))
        assertTrue(ok("كتاب", "کتاب")) // Arabic kaf ~ Persian kaf
    }

    @Test fun devanagariVowelSignsSurvive() {
        assertTrue(ok("किताब", "किताब"))
        assertFalse(ok("कलम", "किताब"))
        assertFalse(ok("कतब", "किताब")) // dropping the vowel signs is a different word
    }

    @Test fun cyrillicGreekAndCjkMatchExactlyButNotEachOther() {
        assertTrue(ok("Мяч!", "мяч"))
        assertTrue(ok("ёж", "еж"))
        assertFalse(ok("маи", "май"))
        assertTrue(ok("ΡΟΛΟΪ", "ρολοι"))
        assertTrue(ok("时钟", "时钟"))
        assertFalse(ok("钟表", "时钟"))
        assertTrue(ok("がっこう", "がっこう"))
        assertFalse(ok("かっこう", "がっこう")) // dakuten matters
        assertTrue(ok("시계", "시계"))
        assertFalse(ok("시간", "시계"))
    }

    @Test fun otherLatinLettersFold() {
        assertTrue(ok("strasse", "Straße"))
        assertTrue(ok("Isparta", "ısparta")) // Turkish dotless i
        assertTrue(ok("İzmir", "izmir")) // Turkish dotted capital I
        assertTrue(ok("Smorgasbord", "smørgasbord".replace("ø", "o")))
        assertTrue(ok("lodz", "łódź".replace("ź", "z")))
    }

    @Test fun digitSystemsFoldToAscii() {
        assertTrue(ok("٣", "3")) // Arabic-Indic
        assertTrue(ok("۳", "3")) // Persian
        assertTrue(ok("३", "3")) // Devanagari
        assertTrue(ok("৩", "3")) // Bengali
        assertTrue(ok("３", "3")) // fullwidth
        assertTrue(ok("12", "١٢"))
    }

    @Test fun nonLatinNeverEqualsEmpty() {
        assertFalse(ok("???", "ساعة"))
        assertFalse(ok("ساعة", "!!!"))
        assertFalse(qa("???", "!!!"))
    }

    @Test fun englishBehaviourIsUnchanged() {
        assertTrue(ok("  CLOCK!! ", "clock"))
        assertTrue(ok("Café", "cafe"))
        assertTrue(ok("cities", "city"))
        assertTrue(ok("it's a clock", "clock"))
        assertTrue(ok("rock & roll", "rock and roll"))
        assertFalse(ok("tree", "oak"))
        assertEquals(listOf("clock"), RiddleMatcher.normalize("The CLOCK."))
    }

    @Test fun qaMatcherAcceptsNonLatinAnswers() {
        assertTrue(qa("ساعة", "ساعة"))
        assertTrue(qa("Мяч", "мяч!"))
        assertTrue(qa("किताब", "किताब"))
        assertTrue(qa("时钟", "时钟"))
        assertFalse(qa("قلم", "ساعة")) // used to be "" == "" -> exact match
        assertFalse(qa("clock", "ساعة"))
        assertTrue(qa("Café", "cafe"))
    }
}
