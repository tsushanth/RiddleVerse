package com.kreativekoala.riddleverse

import org.junit.Assert.*
import org.junit.Test

class LetterBalloonsLogicTest {
    private fun game(word: String = "zebra", lives: Int = 3) = LetterBalloonsGame(word, "Animals", lives)

    @Test fun correctLetterRevealsAllOccurrences() {
        val g = game("banana").guess('a')
        assertEquals(listOf(null, 'a', null, 'a', null, 'a'), g.maskedLetters)
    }

    @Test fun wrongGuessCostsBalloonAndRepeatsAreFree() {
        val g = game().guess('q').guess('q').guess('Q')
        assertEquals(2, g.livesLeft)
        assertEquals(listOf('q'), g.wrongLetters)
    }

    @Test fun winAndLoss() {
        var g = game()
        "zebra".forEach { g = g.guess(it) }
        assertTrue(g.isWon); assertTrue(g.isOver); assertTrue(g.score() > 0)
        var l = game()
        "qwx".forEach { l = l.guess(it) }
        assertTrue(l.isLost); assertEquals(0, l.score())
        assertSame(l, l.guess('z')) // ignored after game over
    }

    @Test fun spacesAreFreeAndPhraseWins() {
        var g = game("new zealand", 5)
        assertEquals(' ', g.maskedLetters[3])
        "newzalnd".forEach { g = g.guess(it) }
        assertTrue(g.isWon)
    }

    @Test fun hintRevealsMostFrequentHiddenLetterWithoutLosingBalloon() {
        val g = game("banana").hint()
        assertTrue('a' in g.guessed)
        assertEquals(3, g.livesLeft)
        assertEquals(1, g.hintsUsed)
        val s1 = game("cat").guess('c').guess('a').guess('t').score()
        val s2 = game("cat").hint().guess('c').guess('a').guess('t').score()
        assertTrue(s2 < s1)
    }

    @Test fun dataIsCleanAndGeneratorDeterministic() {
        for ((cat, words) in LetterBalloonsData.categories) {
            assertTrue(cat, words.size >= 25)
            for (w in words) {
                assertTrue(w, w.all { it in 'a'..'z' || it == ' ' })
                assertFalse(w, WordBlocklist.isBlocked(w))
            }
            for (t in listOf("easy", "medium", "hard")) assertTrue("$cat $t", words.any { LetterBalloonsData.tier(it) == t })
        }
        assertEquals(LetterBalloonsGenerator.generate("Hard", 5).toString(), LetterBalloonsGenerator.generate("Hard", 5).toString())
        for (d in listOf("Easy", "Medium", "Hard")) for (s in 0L until 100L) {
            val o = LetterBalloonsGenerator.generate(d, s)
            assertEquals(d.lowercase(), LetterBalloonsData.tier(o.getString("word")))
            assertEquals(LetterBalloonsData.lives(d), o.getInt("lives"))
        }
    }
}
