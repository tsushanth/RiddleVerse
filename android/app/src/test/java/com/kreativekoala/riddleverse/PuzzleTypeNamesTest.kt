package com.kreativekoala.riddleverse

import org.junit.Assert.*
import org.junit.Test

class PuzzleTypeNamesTest {

    @Test fun humanizeSplitsGluedLowercaseKeys() {
        assertEquals("Number Sequence", PuzzleTypeNames.humanize("numbersequence"))
        assertEquals("Memory Previous Single", PuzzleTypeNames.humanize("memoryprevioussingle"))
        assertEquals("Unique Object", PuzzleTypeNames.humanize("uniqueobject"))
        assertEquals("Color Text Matching", PuzzleTypeNames.humanize("colortextmatching"))
        assertEquals("Word Search", PuzzleTypeNames.humanize("wordsearch"))
        assertEquals("Real Or AI", PuzzleTypeNames.humanize("realorai"))
    }

    @Test fun humanizeHandlesCamelCaseAndSeparators() {
        assertEquals("Story Puzzle", PuzzleTypeNames.humanize("storyPuzzle"))
        assertEquals("Sentence Transitions", PuzzleTypeNames.humanize("sentenceTransitions"))
        assertEquals("Find Object", PuzzleTypeNames.humanize("find_object"))
        assertEquals("Geography Cities", PuzzleTypeNames.humanize("geography-cities"))
        assertEquals("Word Search", PuzzleTypeNames.humanize("word search"))
    }

    @Test fun humanizeNeverFails() {
        assertEquals("Puzzle", PuzzleTypeNames.humanize(""))
        assertEquals("Puzzle", PuzzleTypeNames.humanize("   "))
        assertEquals("Xyzzyfoo", PuzzleTypeNames.humanize("xyzzyfoo")) // unknown blob: capitalised, not guessed
        assertEquals("Puzzle", PuzzleTypeNames.display(null))
    }

    /** Every card on the Categories tab must resolve to the name printed on that card. */
    @Test fun everyCardResolvesToItsOwnName() {
        val cards = QuizCategories.allCategories
        assertTrue(cards.size > 50)
        for (c in cards) {
            val expected = NewGameInfo.of(c.category)?.name ?: c.title
            val got = PuzzleTypeNames.display(c.category)
            assertEquals("display(${c.category})", expected, got)
            val rawCap = c.category.replaceFirstChar { it.uppercase() }
            assertTrue(
                "${c.category} resolved to the capitalised raw key '$got'",
                got != rawCap || c.title == rawCap
            )
            assertNotNull("categoryLabel(${c.category})", PuzzleTypeNames.categoryLabel(c.category))
        }
    }

    @Test fun categoryLabelFollowsTheHomeFilter() {
        assertEquals("Logic Games", PuzzleTypeNames.categoryLabel("numbersequence")) // Logic filter, not "General"
        assertEquals("Word Games", PuzzleTypeNames.categoryLabel("wordsearch"))
        assertEquals("Memory Games", PuzzleTypeNames.categoryLabel("memorysquares"))
        assertEquals("Math Games", PuzzleTypeNames.categoryLabel("division"))
        assertEquals("General", PuzzleTypeNames.categoryLabel("trivia"))
        assertNull(PuzzleTypeNames.categoryLabel("notarealtype"))
        assertEquals("Color Match", PuzzleTypeNames.display("colortextmatching"))
        assertEquals("Disc Tower", PuzzleTypeNames.display("disctower")) // NewGameInfo wins
    }

    @Test fun unknownKeysGetSpacedTitleCase() {
        assertEquals("Memory Pair", PuzzleTypeNames.display("memorypair"))
    }
}
