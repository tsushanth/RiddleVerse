package com.kreativekoala.riddleverse

import org.junit.Assert.*
import org.junit.Test

class CodeCrackerLogicTest {
    private fun fb(s: List<Int>, g: List<Int>) = CodeCrackerRules.feedback(s, g)

    @Test fun exactAndMisplaced() {
        assertEquals(CodeFeedback(4, 0), fb(listOf(0, 1, 2, 3), listOf(0, 1, 2, 3)))
        assertEquals(CodeFeedback(0, 4), fb(listOf(0, 1, 2, 3), listOf(3, 2, 1, 0)))
        assertEquals(CodeFeedback(0, 0), fb(listOf(0, 0, 1, 1), listOf(2, 2, 3, 3)))
        assertEquals(CodeFeedback(1, 1), fb(listOf(0, 1, 2, 3), listOf(0, 2, 4, 4)))
    }

    @Test fun duplicatesDoNotOverCount() {
        // secret has one red; guess has three reds, one in the right slot
        assertEquals(CodeFeedback(1, 0), fb(listOf(0, 1, 2, 3), listOf(0, 0, 0, 5)))
        // secret has two reds in slots 0,1; guess puts reds at 1,2,3 -> one exact, one misplaced
        assertEquals(CodeFeedback(1, 1), fb(listOf(0, 0, 1, 2), listOf(3, 0, 0, 0)))
        // classic: secret 1122, guess 2211 -> 0 exact, 4 misplaced
        assertEquals(CodeFeedback(0, 4), fb(listOf(1, 1, 2, 2), listOf(2, 2, 1, 1)))
        // secret 1123, guess 1111 -> 2 exact, 0 misplaced
        assertEquals(CodeFeedback(2, 0), fb(listOf(1, 1, 2, 3), listOf(1, 1, 1, 1)))
    }

    @Test fun feedbackIsSymmetricInTotals() {
        val r = java.util.Random(1)
        repeat(500) {
            val s = List(4) { r.nextInt(6) }; val g = List(4) { r.nextInt(6) }
            val a = fb(s, g); val b = fb(g, s)
            assertEquals(a, b)
            assertTrue(a.exact + a.misplaced <= 4)
        }
    }

    @Test fun generatorDeterministicAndRespectsConfig() {
        assertEquals(CodeCrackerGenerator.generate("Medium", 9), CodeCrackerGenerator.generate("Medium", 9))
        for (d in listOf("Easy", "Medium", "Hard")) for (s in 0L until 200L) {
            val p = CodeCrackerGenerator.generate(d, s)
            val c = CodeCrackerRules.config(d)
            assertEquals(c.positions, p.secret.size)
            assertTrue(p.secret.all { it in 0 until c.colors })
            if (!c.allowRepeats) assertEquals(p.secret.size, p.secret.toSet().size)
        }
    }

    @Test fun consistencyFilterKeepsSecret() {
        val secret = listOf(0, 1, 2, 3)
        val hist = listOf(listOf(0, 0, 1, 1).let { it to fb(secret, it) }, listOf(3, 2, 1, 0).let { it to fb(secret, it) })
        assertTrue(CodeCrackerRules.consistent(secret, hist))
        assertFalse(CodeCrackerRules.consistent(listOf(5, 5, 5, 5), hist))
    }

    @Test fun jsonRoundTrip() {
        val p = CodeCrackerGenerator.generate("Hard", 4)
        assertEquals(p, CodeCrackerPuzzle.fromJson(p.toJson(4, "Hard")))
    }

    @Test fun scoreDecreasesWithHints() {
        assertTrue(CodeCrackerRules.score(3, 10, 0) > CodeCrackerRules.score(3, 10, 1))
    }
}
