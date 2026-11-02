package com.kreativekoala.riddleverse

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Memory Match generators are pure: the answer key must follow from the symbol sequence, over many random games. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class MemoryGeneratorsTest {

    private fun ints(a: org.json.JSONArray) = List(a.length()) { a.getInt(it) }

    private fun check(question: String, answer: String, minSymbols: Int, what: String) {
        val q = JSONObject(question)
        val seq = ints(q.getJSONArray("binarySequence"))
        val key = ints(JSONObject(answer).getJSONArray("answerSequence"))
        assertEquals("$what: one answer per symbol after the first", seq.size - 1, key.size)
        assertEquals("$what: totalQuestions", key.size, q.getInt("totalQuestions"))
        assertEquals("$what: totalSteps", seq.size, q.getInt("totalSteps"))
        assertTrue("$what: sequence too short", seq.size >= minSymbols)
        assertTrue("$what: symbols must be 0/1", seq.all { it == 0 || it == 1 })
        for (i in 1 until seq.size) {
            assertEquals("$what: answer $i", if (seq[i] == seq[i - 1]) 1 else 0, key[i - 1])
        }
        // Both answers must actually be needed, or a player can win by tapping one button.
        assertTrue("$what: key is all the same answer", key.toSet().size == 2)
        val steps = q.getJSONArray("questionSequence")
        assertEquals("$what: steps", seq.size, steps.length())
        for (i in seq.indices) assertEquals("$what: step $i value", seq[i], steps.getJSONObject(i).getInt("value"))
    }

    @Test fun previousSingleKeyMatchesSequence_allDifficulties() {
        for (d in listOf("easy", "medium", "hard", "Easy", "unknown")) repeat(2000) {
            val (q, a) = MemoryPreviousSingleGenerator.generatePuzzle(d)
            check(q, a, 16, "single/$d")
        }
    }

    @Test fun adaptiveKeyMatchesSequence_allLevels() {
        for (idx in 0..4) repeat(1500) {
            val level = DifficultyManager.DifficultyLevel(name = "L$idx", index = idx, timeLimit = 60, livesAllowed = 3, basePoints = 10)
            val (q, a) = SimplifiedAdaptiveMemoryGenerator.generateSimplePuzzle(level)
            check(q, a, 12, "adaptive/$idx")
        }
    }

    @Test fun clockCoversTheUnavoidableWaiting() {
        // Per answer: 1.5 s feedback pause + the level's reveal delay; plus 2 s on the first symbol.
        val levels = mapOf(20 to 0L, 18 to 300L, 16 to 500L, 14 to 750L, 12 to 1000L)
        for ((symbols, delay) in levels) {
            val budget = SimplifiedAdaptiveMemoryGenerator.timeBudgetSeconds(symbols, delay)
            val waiting = (2000 + (symbols - 1) * (1500 + delay)) / 1000.0
            val thinkingPerAnswer = (budget - waiting) / (symbols - 1)
            assertTrue("level with $symbols symbols leaves only ${"%.1f".format(thinkingPerAnswer)}s per answer", thinkingPerAnswer >= 3.5)
        }
        assertEquals(0, SimplifiedAdaptiveMemoryGenerator.timeBudgetSeconds(0, 0))
    }
}
