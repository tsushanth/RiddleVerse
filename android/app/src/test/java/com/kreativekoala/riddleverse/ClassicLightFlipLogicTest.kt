package com.kreativekoala.riddleverse

import org.junit.Assert.*
import org.junit.Test
import kotlin.random.Random

class ClassicLightFlipLogicTest {
    @Test fun pressTogglesCrossShape() {
        val g = LightFlipGame(5, 0, 0, 0).press(12)
        assertEquals(listOf(7, 11, 12, 13, 17), (0 until 25).filter { g.isOn(it) })
        val corner = LightFlipGame(5, 0, 0, 0).press(0)
        assertEquals(listOf(0, 1, 5), (0 until 25).filter { corner.isOn(it) })
    }

    @Test fun pressTwiceIsIdentity() {
        val g = LightFlipGame(5, 0b1010101, 0, 0)
        assertEquals(g.lights, g.press(7).press(7).lights)
    }

    @Test fun generatedPuzzlesSolvableWithPar() {
        for (tier in 0..4) for (seed in 0L until 40L) {
            val g = LightFlipGame.newGame(tier, seed)
            assertNotEquals(0, g.lights)
            val sol = LightFlipRules.solveMinimal(5, g.lights)
            assertNotNull(sol)
            // applying the solution turns everything off
            var x = g
            for (i in 0 until 25) if (sol!! and (1 shl i) != 0) x = x.press(i)
            assertTrue("tier $tier seed $seed", x.isSolved)
            assertEquals(Integer.bitCount(sol!!), g.par)
            assertTrue(g.par <= LightFlipRules.scramblePresses(tier))
        }
    }

    @Test fun solverReturnsNullForUnsolvableState() {
        // Known: on 5x5 only 2^23 of 2^25 states are solvable; find one unsolvable
        val rng = Random(1)
        var foundNull = false
        repeat(2000) {
            if (LightFlipRules.solveMinimal(5, rng.nextInt(1 shl 25)) == null) foundNull = true
        }
        assertTrue(foundNull)
    }

    @Test fun solverIsMinimalAgainstBruteForceOnSmallBoards() {
        for (state in 0 until 512) {
            val sol = LightFlipRules.solveMinimal(3, state)
            var best: Int? = null
            for (presses in 0 until 512) {
                var s = state
                for (i in 0 until 9) if (presses and (1 shl i) != 0) s = s xor LightFlipRules.pressMask(3, i)
                if (s == 0 && (best == null || Integer.bitCount(presses) < best!!)) best = Integer.bitCount(presses)
            }
            assertEquals(best, sol?.let { Integer.bitCount(it) })
        }
    }

    @Test fun hintFollowingSolvesInPar() {
        val g0 = LightFlipGame.newGame(3, 9)
        var g = g0
        var steps = 0
        while (!g.isSolved) { g = g.press(g.hintCell()!!); steps++; assertTrue(steps <= 25) }
        assertEquals(g0.par, steps)
    }

    @Test fun undoAndDeterminismAndRoundTrip() {
        val g = LightFlipGame.newGame(2, 4)
        assertEquals(g, LightFlipGame.newGame(2, 4))
        assertEquals(g.lights, g.press(3).undo().lights)
        val h = g.press(3).press(9).withHintUsed()
        assertEquals(h, LightFlipGame.decode(h.encode()))
    }
}
