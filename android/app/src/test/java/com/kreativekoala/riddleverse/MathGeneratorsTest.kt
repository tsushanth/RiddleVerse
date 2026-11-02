package com.kreativekoala.riddleverse

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Client-side math generators: always solvable, answer keys exact, never fail to produce a puzzle. */
class MathGeneratorsTest {
    private val levels = listOf("easy", "medium", "hard", "expert")

    @Test fun percentagesAreWholeAndCorrect() {
        for (d in levels) repeat(1500) {
            val g = MathPuzzleGenerators()
            val p = assertNotNull_("percentages/$d", g.generatePercentages(d))
            assertEquals(p.total * p.percentage / 100.0, p.answer, 1e-9)
            assertEquals("whole answer", 0.0, p.answer - Math.floor(p.answer), 1e-9)
        }
    }

    @Test fun averagesAreWholeAndCorrect() {
        for (d in levels) repeat(1500) {
            val p = assertNotNull_("average/$d", MathPuzzleGenerators().generateAverage(d))
            assertEquals(p.numbers.sum().toDouble() / p.numbers.size, p.average, 1e-9)
            assertEquals(0.0, p.average - Math.floor(p.average), 1e-9)
        }
    }

    @Test fun tippingKeyIsBillTimesPercent() {
        for (d in levels) repeat(1500) {
            val p = assertNotNull_("tipping/$d", MathPuzzleGenerators().generateMathTipping(d))
            assertEquals(p.billAmount * p.tipPercentage / 100.0, p.tipAmount, 0.006)
            assertTrue(p.tipAmount > 0)
        }
    }

    @Test fun estimationSumMatches() {
        for (d in levels) repeat(1500) {
            val p = assertNotNull_("estimation/$d", MathPuzzleGenerators().generateMathEstimation(d))
            assertEquals(p.numbers.sum(), p.sum, 0.011)
        }
    }

    private fun <T> assertNotNull_(what: String, v: T?): T { assertNotNull("$what returned null", v); return v!! }
}
