package com.kreativekoala.riddleverse

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RiddlePackTest {
    @Test fun starsDropOnePerHintButNeverBelowOne() {
        assertEquals(3, RiddlePack.stars(0))
        assertEquals(2, RiddlePack.stars(1))
        assertEquals(1, RiddlePack.stars(2))
        assertEquals(1, RiddlePack.stars(3))
        assertEquals(1, RiddlePack.stars(9))
    }

    @Test fun pointsOnlyForSolvedRiddlesAndHardIsWorthMore() {
        assertEquals(0, RiddlePack.points(RiddleTier.HARD, 0, solved = false))
        assertEquals(30, RiddlePack.points(RiddleTier.EASY, 0, true))
        assertEquals(35, RiddlePack.points(RiddleTier.MEDIUM, 0, true))
        assertEquals(40, RiddlePack.points(RiddleTier.HARD, 0, true))
        assertEquals(20, RiddlePack.points(RiddleTier.EASY, 1, true))
        assertEquals(10, RiddlePack.points(RiddleTier.EASY, 3, true))
    }

    @Test fun difficultyNamesMapOntoTiers() {
        assertEquals(RiddleTier.EASY, RiddleTier.fromDifficulty("Easy"))
        assertEquals(RiddleTier.EASY, RiddleTier.fromDifficulty("Beginner"))
        assertEquals(RiddleTier.EASY, RiddleTier.fromDifficulty("Easy+"))
        assertEquals(RiddleTier.EASY, RiddleTier.fromDifficulty("Tutorial"))
        assertEquals(RiddleTier.MEDIUM, RiddleTier.fromDifficulty("Medium"))
        assertEquals(RiddleTier.MEDIUM, RiddleTier.fromDifficulty("Medium+"))
        assertEquals(RiddleTier.MEDIUM, RiddleTier.fromDifficulty(""))
        assertEquals(RiddleTier.MEDIUM, RiddleTier.fromDifficulty(null))
        assertEquals(RiddleTier.HARD, RiddleTier.fromDifficulty("Hard-"))
        assertEquals(RiddleTier.HARD, RiddleTier.fromDifficulty("EXPERT"))
    }

    @Test fun playLinkIsExactlyTheShareLink() {
        assertEquals(
            "https://play.google.com/store/apps/details?id=com.kreativekoala.riddleverse&utm_source=share&utm_medium=riddle&utm_campaign=card",
            RiddlePack.PLAY_LINK
        )
    }

    // ---- registry wiring: the whole integration is the one block in WordPuzzleRegistry ----------------------------

    @Test fun registeredThroughWordPuzzleRegistry() {
        assertTrue(RiddlePack.TYPE in WordPuzzleRegistry.TYPES)
        assertTrue(WordPuzzleRegistry.isWordPuzzleType("riddles"))
        assertTrue(WordPuzzleRegistry.quizCategories.any { it.category == RiddlePack.TYPE })
        assertEquals(1, WordPuzzleRegistry.quizCategories.count { it.category == RiddlePack.TYPE })
        assertEquals(listOf(RiddlePack.SCREEN_NAME), WordPuzzleRegistry.screenOptions(RiddlePack.TYPE))
    }

    @Test fun screenNameRoutesToTheWordPuzzleWrapper() {
        val wordScreens = setOf(
            PuzzleScreenType.WORD_GUESS_SCREEN, PuzzleScreenType.WORD_LADDER_SCREEN, PuzzleScreenType.LETTER_BALLOONS_SCREEN,
            PuzzleScreenType.WORD_HIVE_SCREEN, PuzzleScreenType.CODE_CRACKER_SCREEN
        )
        // PuzzleScreen sends exactly these screen types to WordPuzzleScreenWrapper, which then dispatches on the "game" key.
        assertTrue(PuzzleScreenType.fromString(RiddlePack.SCREEN_NAME) in wordScreens)
    }

    @Test fun hasATutorial() {
        val t = WordPuzzleRegistry.tutorialContent(RiddlePack.TYPE)
        assertEquals("Riddles", t.title)
        assertTrue(t.instruction.isNotBlank())
    }

    @Test fun puzzleJsonRoundTripsAndCarriesTheGameKey() {
        val r = RiddleTestSupport.byId("m001")
        val json = r.toJson()
        assertEquals("riddles", json.getString("game"))
        assertEquals(r, Riddle.fromJson(json))
    }

    @Test fun displayAnswerCapitalisesTheFirstLetter() {
        assertEquals("Deck of cards", RiddleTestSupport.bank.first { it.answer == "deck of cards" }.displayAnswer)
        assertEquals("The letter M", RiddleTestSupport.bank.first { it.answer == "the letter M" }.displayAnswer)
        assertFalse(RiddleTestSupport.bank.any { it.displayAnswer.isBlank() })
    }
}
