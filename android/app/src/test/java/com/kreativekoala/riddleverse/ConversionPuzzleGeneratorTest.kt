package com.kreativekoala.riddleverse

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ConversionPuzzleGeneratorTest {
    private val generator = ConversionPuzzleGenerator()

    /** "84 min", "1.5 hr", "212 °F"-style labels: leading number only. */
    private val leadingNumber = Regex("""^-?(\d+)(?:\.(\d+))?""")

    private fun assertReadable(label: String) {
        val match = leadingNumber.find(label.trim())
        assertNotNull("no leading number in '$label'", match)
        val whole = match!!.groupValues[1]
        val decimals = match.groupValues[2]
        assertTrue("too many decimals in '$label'", decimals.length <= 1)
        val value = label.trim().takeWhile { it.isDigit() || it == '.' || it == '-' }.toDouble()
        assertTrue("tiny value in '$label'", value >= 0.5)
        if (value >= 100) assertEquals("decimals on a big number in '$label'", "", decimals)
        assertTrue(whole.isNotEmpty())
    }

    @Test fun labels_areReadableNumbers_atEveryDifficulty() {
        for (difficulty in listOf("easy", "medium", "hard")) {
            var generated = 0
            repeat(400) {
                val puzzle = generator.generateConversionPuzzle(difficulty) ?: return@repeat
                generated++
                assertReadable(puzzle.leftBlock.label)
                assertReadable(puzzle.rightBlock.label)
            }
            assertTrue("$difficulty generated only $generated of 400", generated >= 380)
        }
    }

    @Test fun isFriendly_examples() {
        assertTrue(generator.isFriendly(1.5))
        assertTrue(generator.isFriendly(90.0))
        assertTrue(generator.isFriendly(250.0))
        assertTrue(!generator.isFriendly(0.0233))   // hours from 84 seconds
        assertTrue(!generator.isFriendly(0.3))      // too small to read at a glance
        assertTrue(!generator.isFriendly(12.345))
        assertTrue(!generator.isFriendly(250.4))
    }
}

class FriendlyConversionLabelTest {
    @Test fun rawServerDecimals_areShortened() {
        assertEquals("0.023 hr", friendlyConversionLabel("0.02334449 hr"))
        assertEquals("12.3 km", friendlyConversionLabel("12.3456 km"))
        assertEquals("1235 m", friendlyConversionLabel("1234.5678 m"))
    }

    @Test fun alreadyFriendlyLabels_areUntouched() {
        assertEquals("90 min", friendlyConversionLabel("90 min"))
        assertEquals("1.5 hr", friendlyConversionLabel("1.5 hr"))
        assertEquals("98.6 °F", friendlyConversionLabel("98.6 °F"))
    }
}
