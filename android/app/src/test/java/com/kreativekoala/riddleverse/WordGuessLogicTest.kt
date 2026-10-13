package com.kreativekoala.riddleverse

import org.junit.Assert.*
import org.junit.Test
import com.kreativekoala.riddleverse.LetterState.*

class WordGuessLogicTest {
    private fun fb(guess: String, answer: String) = WordGuessRules.evaluate(guess, answer)

    @Test fun allCorrect() = assertEquals(List(5) { CORRECT }, fb("crane", "crane"))

    @Test fun allAbsent() = assertEquals(List(5) { ABSENT }, fb("lumps", "crane"))

    @Test fun duplicateGuessLetterOnlyOneYellow() {
        // answer ALLOY has two L; guess LLAMA: L1 present, L2 correct (pos 2), A present, M absent, A absent (only one A in answer)
        assertEquals(listOf(PRESENT, CORRECT, PRESENT, ABSENT, ABSENT), fb("llama", "alloy"))
    }

    @Test fun duplicateLetterInGuessAndAnswer() {
        assertEquals(listOf(ABSENT, ABSENT, PRESENT, CORRECT, PRESENT), fb("floor", "robot"))
    }

    @Test fun exactMatchClaimsBeforeMisplaced() {
        // answer has one E: first guess E is PRESENT, second is ABSENT
        assertEquals(listOf(ABSENT, ABSENT, PRESENT, ABSENT, PRESENT), fb("speed", "abide"))
        // "there" has E at 2 and 4; eerie: E@4 exact, E@0 gets the other, E@1 absent
        assertEquals(listOf(PRESENT, ABSENT, PRESENT, ABSENT, CORRECT), fb("eerie", "there"))
    }

    @Test fun neverMoreMarkedThanAnswerHas() {
        val words = listOf("llama", "alloy", "speed", "abide", "eerie", "there", "tooth", "otter", "mamma", "stall")
        for (a in words) for (g in words) {
            val r = fb(g, a)
            for (c in 'a'..'z') {
                val marked = g.indices.count { g[it] == c && r[it] != ABSENT }
                assertTrue("$g/$a $c", marked <= a.count { it == c })
            }
        }
    }

    @Test fun keyboardKeepsBestState() {
        val k = WordGuessRules.keyboardStates(listOf("llama", "alloy"), "alloy")
        assertEquals(CORRECT, k['a']) // present first, then correct
        assertEquals(CORRECT, k['l'])
        assertEquals(ABSENT, k['m'])
        assertNull(k['z'])
    }

    @Test fun hardModeRequiresGreenAndYellow() {
        val answer = "crane"
        // "react": r present, e present, a present, c present, t absent
        assertNotNull(WordGuessRules.hardModeViolation(listOf("react"), answer, "pulse"))
        assertNull(WordGuessRules.hardModeViolation(listOf("react"), answer, "crane"))
        // green must stay: "crown" -> c,r green
        val msg = WordGuessRules.hardModeViolation(listOf("crown"), answer, "brace")
        assertEquals("Letter 1 must be C", msg)
    }

    @Test fun hardModeCountsRepeatedLetters() {
        // answer "mamma": guess "mummy" -> m,m green... guess "mamas"-> need 2 m and 2 a to be reused
        val prev = listOf("amass") // a present, m present, a present, s absent, s absent vs mamma? evaluate:
        val r = WordGuessRules.evaluate("amass", "mamma")
        assertEquals(3, r.count { it != ABSENT })
        assertNotNull(WordGuessRules.hardModeViolation(prev, "mamma", "mound")) // lacks a's
        assertNull(WordGuessRules.hardModeViolation(prev, "mamma", "mamma"))
    }

    @Test fun gameFlowWinAndLoss() {
        var g = WordGuessGame("crane", hard = false)
        val valid: (String) -> Boolean = { true }
        "cra".forEach { g = g.type(it) }
        assertEquals(SubmitOutcome.TooShort, g.submit(valid).second)
        "ne".forEach { g = g.type(it) }
        g = g.type('x') // ignored, already 5
        assertEquals("crane", g.current)
        val (won, out) = g.submit(valid)
        assertEquals(SubmitOutcome.Accepted, out)
        assertEquals(GuessStatus.WON, won.status)
        // loss after six wrong
        var l = WordGuessGame("crane", false)
        repeat(6) { l = l.copy(current = "slimy").submit(valid).first }
        assertEquals(GuessStatus.LOST, l.status)
        assertEquals(SubmitOutcome.GameOver, l.submit(valid).second)
    }

    @Test fun invalidWordRejectedWithoutConsumingTry() {
        val g = WordGuessGame("crane", false, current = "zzzzz")
        val (g2, out) = g.submit { false }
        assertEquals(SubmitOutcome.NotInList, out)
        assertEquals(0, g2.guesses.size)
        assertEquals("zzzzz", g2.current)
    }

    @Test fun hardModeEnforcedInSubmit() {
        val g = WordGuessGame("crane", true, guesses = listOf("crown"), current = "brace")
        assertTrue(g.submit { true }.second is SubmitOutcome.HardMode)
    }

    @Test fun shareTextHasNoLetters() {
        val t = WordGuessRules.shareText(listOf("react", "crane"), "crane", true, true)
        assertTrue(t.startsWith("RiddleVerse Word Guess 2/6*"))
        assertFalse(t.contains("crane"))
        assertEquals(4, t.lines().size)
    }

    @Test fun hintPositionSkipsSolved() {
        val g = WordGuessGame("crane", false, guesses = listOf("crown"))
        assertEquals(2, g.hintPosition())
    }

    @Test fun generatorDeterministicAndSane() {
        val lists = WordPuzzleTestSupport.lists
        val a = WordGuessGenerator.generate(lists, "Medium", 42).toString()
        val b = WordGuessGenerator.generate(lists, "Medium", 42).toString()
        assertEquals(a, b)
        for (d in listOf("Easy", "Medium", "Hard")) for (s in 0L until 200L) {
            val o = WordGuessGenerator.generate(lists, d, s)
            val ans = o.getString("answer")
            assertEquals(5, ans.length)
            assertTrue(ans in lists.valid5)
            assertFalse(WordBlocklist.isBlocked(ans))
            assertEquals(d == "Hard", o.getBoolean("hard"))
        }
        assertTrue(lists.answerPool(0, 700).size >= 600)
    }
}
