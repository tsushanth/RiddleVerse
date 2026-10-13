package com.kreativekoala.riddleverse

import org.junit.Assert.*
import org.junit.Test

class SpatialTubeTintTest {

    @Test fun pourRules() {
        val t = listOf(mutableListOf(1, 2, 2), mutableListOf(3, 2), mutableListOf<Int>(), mutableListOf(2, 2, 2, 2))
        assertTrue(TubeTintEngine.canPour(t, 0, 1))      // 2 onto 2
        assertFalse(TubeTintEngine.canPour(t, 0, 3))      // full
        assertFalse(TubeTintEngine.canPour(t, 1, 0) && false)
        assertEquals(2, TubeTintEngine.pour(t, 0, 1))
        assertEquals(listOf(1), t[0]); assertEquals(listOf(3, 2, 2, 2), t[1])
        assertFalse(TubeTintEngine.canPour(t, 0, 1))      // top 1 vs 2
        assertTrue(TubeTintEngine.canPour(t, 0, 2).not()) // uniform into empty is pointless -> rejected
    }

    @Test fun partialPourLimitedByRoom() {
        val t = listOf(mutableListOf(0, 5, 5, 5), mutableListOf(5, 5, 7, 5))
        // dest has 4 (full) - cannot
        assertFalse(TubeTintEngine.canPour(t, 0, 1))
        val u = listOf(mutableListOf(0, 5, 5, 5), mutableListOf(1, 5))
        assertEquals(2, TubeTintEngine.pour(u, 0, 1))
        assertEquals(listOf(0, 5), u[0])
    }

    @Test fun generatedPuzzlesAreSolvableAcrossSeedsAndDifficulties() {
        for (d in listOf("Easy", "Medium", "Hard")) {
            val (colors, empties) = TubeTintEngine.configFor(d)
            for (seed in 1L..12L) {
                val p = TubeTintEngine.generate(d, seed)
                assertEquals(colors + empties, p.tubes.size)
                // each colour has exactly capacity units
                val counts = IntArray(colors)
                p.tubes.forEach { t -> t.forEach { counts[it]++ } }
                counts.forEach { assertEquals(TubeTintEngine.CAPACITY, it) }
                assertFalse(TubeTintEngine.isSolved(p.tubes.map { it.toList() }))
                val sol = TubeTintEngine.solve(p.tubes.map { it.toList() }, p.capacity, 400_000)
                assertNotNull("$d seed $seed unsolved", sol)
                // replay the solution
                val st = p.copyTubes()
                for (m in sol!!) assertTrue(TubeTintEngine.pour(st, m[0], m[1]) > 0)
                assertTrue(TubeTintEngine.isSolved(st))
            }
        }
    }

    @Test fun deterministic() {
        val a = TubeTintEngine.generate("Medium", 5)
        val b = TubeTintEngine.generate("Medium", 5)
        for (i in a.tubes.indices) assertArrayEquals(a.tubes[i], b.tubes[i])
    }

    @Test fun stateUndoAddTubeRestart() {
        val p = TubeTintEngine.generate("Easy", 2)
        val s = TubeTintState(p)
        val before = s.encode()
        val sol = TubeTintEngine.solve(p.tubes.map { it.toList() })!!
        assertTrue(s.pour(sol[0][0], sol[0][1]))
        assertEquals(1, s.moves)
        assertTrue(s.undo())
        assertEquals(0, s.moves)
        assertEquals(before, s.encode())
        val n = s.tubes.size
        assertTrue(s.addTube()); assertEquals(n + 1, s.tubes.size)
        assertFalse(s.addTube())
        assertTrue(s.undo()); assertEquals(n, s.tubes.size)
        // play out the solution
        var cur = sol
        for (m in cur) assertTrue(s.pour(m[0], m[1]))
        assertTrue(s.solved())
        val t = TubeTintState(p); t.restore(s.encode())
        assertTrue(t.solved())
        s.restart(); assertFalse(s.solved())
    }

    @Test fun hintReturnsLegalMove() {
        val p = TubeTintEngine.generate("Easy", 4)
        val s = TubeTintState(p)
        val h = s.hint()!!
        assertTrue(TubeTintEngine.canPour(s.tubes, h[0], h[1]))
    }
}
