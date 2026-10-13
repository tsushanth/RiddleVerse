package com.kreativekoala.riddleverse

import org.junit.Assert.*
import org.junit.Test

class ClassicDiscTowerLogicTest {
    @Test fun legalityRules() {
        val g = DiscTowerGame.newGame(3)
        assertFalse(g.canMove(1, 2)) // empty source
        assertTrue(g.canMove(0, 1))
        assertFalse(g.canMove(0, 0))
        val m = g.move(0, 1)
        assertFalse(m.canMove(0, 1)) // 2 onto 1 is illegal
        assertTrue(m.canMove(0, 2))
        assertSame(m, m.move(0, 1)) // illegal move ignored
    }

    @Test fun hintsSolveInExactlyParMovesFromStart() {
        for (n in 3..8) {
            var g = DiscTowerGame.newGame(n)
            var steps = 0
            while (!g.isSolved) {
                val (f, t) = g.hintMove()!!
                assertTrue(g.canMove(f, t))
                g = g.move(f, t)
                steps++
                assertTrue(steps <= g.par)
            }
            assertEquals(DiscTowerRules.parFor(n), steps)
            assertEquals(steps, g.moves)
        }
    }

    @Test fun hintOptimalFromArbitraryPosition() {
        // after an arbitrary legal detour, following hints always terminates
        var g = DiscTowerGame.newGame(4).move(0, 1).move(0, 2).move(1, 2).move(0, 1)
        var guard = 0
        while (!g.isSolved) { val (f, t) = g.hintMove()!!; g = g.move(f, t); assertTrue(guard++ < 15) }
    }

    @Test fun undoAndRoundTrip() {
        val g = DiscTowerGame.newGame(4).move(0, 2).move(0, 1)
        val u = g.undo()
        assertEquals(1, u.moves)
        assertEquals(listOf(listOf(4, 3, 2), emptyList<Int>(), listOf(1)), u.pegs)
        assertEquals(DiscTowerGame.newGame(4), g.undo().undo())
        assertEquals(g, DiscTowerGame.decode(g.encode()))
        assertNull(DiscTowerGame.decode("zzz"))
    }

    @Test fun discsPerTierAndPar() {
        assertEquals(listOf(3, 4, 5, 6, 7), (0..4).map { DiscTowerRules.discsFor(it) })
        assertEquals(7, DiscTowerRules.parFor(3))
        assertEquals(255, DiscTowerRules.parFor(8))
    }
}
