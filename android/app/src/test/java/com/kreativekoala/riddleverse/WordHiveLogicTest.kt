package com.kreativekoala.riddleverse

import org.junit.Assert.*
import org.junit.Test

class WordHiveLogicTest {
    private val letters = "abcdefg".toSet()

    @Test fun scoring() {
        assertEquals(1, WordHiveRules.wordScore("abed", letters))
        assertEquals(5, WordHiveRules.wordScore("faced", letters))
        assertEquals(6, WordHiveRules.wordScore("beaded", letters))
        // pangram: 7 letters + 7 bonus
        assertTrue(WordHiveRules.isPangram("abcdefg", letters))
        assertEquals(14, WordHiveRules.wordScore("abcdefg", letters))
        // pangram longer than 7 letters
        assertEquals(8 + 7, WordHiveRules.wordScore("abcdefgg", letters))
        assertFalse(WordHiveRules.isPangram("abcdef", letters))
    }

    @Test fun solutionsRequireCenterLettersAndLength() {
        val words = listOf("abed", "bade", "cab", "faded", "fad", "gaff", "abcdefg", "zebra", "abed")
        val sols = WordHiveRules.solutions(words, letters, 'a')
        assertEquals(listOf("abcdefg", "abed", "bade", "faded", "gaff"), sols)
        assertEquals(listOf("abcdefg", "abed", "bade", "faded"), WordHiveRules.solutions(words, letters, 'd'))
    }

    @Test fun rankThresholds() {
        assertEquals(0, WordHiveRules.rankIndex(0, 100))
        assertEquals(1, WordHiveRules.rankIndex(2, 100))
        assertEquals(8, WordHiveRules.rankIndex(70, 100))
        assertEquals(7, WordHiveRules.rankIndex(69, 100))
        assertEquals(9, WordHiveRules.rankIndex(100, 100))
        assertEquals(0, WordHiveRules.rankIndex(5, 0))
    }

    @Test fun rejections() {
        val sol = setOf("abed", "faded")
        fun r(e: String, found: List<String> = emptyList()) = WordHiveRules.rejection(e, letters, 'a', sol, found)
        assertEquals("Too short", r("ab"))
        assertEquals("Bad letters", r("abez"))
        assertEquals("Missing centre letter", r("cede"))
        assertEquals("Not in word list", r("fabe"))
        assertEquals("Already found", r("abed", listOf("abed")))
        assertNull(r("abed"))
    }

    @Test fun generatedPuzzlesAreValid() {
        val lists = WordPuzzleTestSupport.lists
        for (d in listOf("Easy", "Medium", "Hard")) for (seed in 0L until 8L) {
            val p = WordHiveGenerator.generate(lists, d, seed)
            assertNotNull(p); p!!
            assertEquals(6, p.letters.size)
            assertEquals(7, p.letterSet.size)
            assertFalse('s' in p.letterSet)
            assertTrue("needs a pangram", p.pangrams.isNotEmpty())
            assertEquals(WordHiveRules.solutions(lists.common, p.letterSet, p.center), p.words)
            assertEquals(p.words.sumOf { WordHiveRules.wordScore(it, p.letterSet) }, p.maxScore)
            assertTrue(p.words.all { p.center in it && it.length >= 4 && !WordBlocklist.isBlocked(it) })
            assertTrue("size ${p.words.size}", p.words.size >= 10)
        }
    }

    @Test fun deterministicAndRoundTrip() {
        val lists = WordPuzzleTestSupport.lists
        val a = WordHiveGenerator.generate(lists, "Medium", 11)!!
        assertEquals(a, WordHiveGenerator.generate(lists, "Medium", 11)!!)
        assertEquals(a, WordHivePuzzle.fromJson(a.toJson(11, "Medium")))
    }

    @Test fun targetRankByDifficulty() {
        assertTrue(WordHiveRules.targetRankIndex("easy") < WordHiveRules.targetRankIndex("medium"))
        assertTrue(WordHiveRules.targetRankIndex("medium") < WordHiveRules.targetRankIndex("hard"))
    }
}
