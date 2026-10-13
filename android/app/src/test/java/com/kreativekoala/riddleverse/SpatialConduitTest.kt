package com.kreativekoala.riddleverse

import org.junit.Assert.*
import org.junit.Test

class SpatialConduitTest {

    @Test fun generatedSolutionIsValidAndStartIsNot() {
        for (d in listOf("Easy", "Medium", "Hard")) {
            for (seed in 1L..30L) {
                val p = ConduitEngine.generate(d, seed)
                assertTrue("$d $seed solution invalid", ConduitEngine.isSolved(p.n, p.source, p.solution))
                assertFalse("$d $seed start already solved", ConduitEngine.isSolved(p.n, p.source, p.start))
                // spanning tree: n*n-1 edges
                val edges = p.solution.sumOf { Integer.bitCount(it) } / 2
                assertEquals(p.n * p.n - 1, edges)
                // start is a pure rotation of the solution per tile
                for (i in p.start.indices) {
                    var m = p.start[i]; var ok = false
                    repeat(4) { if (m == p.solution[i]) ok = true; m = ConduitEngine.rotCW(m) }
                    assertTrue(ok)
                }
            }
        }
    }

    @Test fun backtrackingSolverFindsASolution() {
        for (d in listOf("Easy", "Medium", "Hard")) {
            for (seed in 1L..15L) {
                val p = ConduitEngine.generate(d, seed)
                val sol = ConduitEngine.solve(p.n, p.source, p.start)
                assertNotNull("$d $seed", sol)
                assertTrue(ConduitEngine.isSolved(p.n, p.source, sol!!))
            }
        }
    }

    @Test fun rotateAndUndoAndSolveByTapping() {
        val p = ConduitEngine.generate("Easy", 5)
        val s = ConduitState(p)
        val before = s.masks.copyOf()
        s.rotate(0); s.undo()
        assertArrayEquals(before, s.masks)
        for (i in s.masks.indices) repeat(ConduitEngine.tapsToReach(s.masks[i], p.solution[i])) { s.rotate(i) }
        assertTrue(s.solved())
        assertEquals(p.par, s.moves)
        assertEquals(3, s.stars())
        val t = ConduitState(p); t.restore(s.encode()); assertTrue(t.solved())
    }

    @Test fun rotationMath() {
        assertEquals(ConduitEngine.E, ConduitEngine.rotCW(ConduitEngine.N))
        assertEquals(ConduitEngine.N, ConduitEngine.rotCW(ConduitEngine.W))
        assertEquals(15, ConduitEngine.rotCW(15))
    }

    @Test fun deterministic() {
        val a = ConduitEngine.generate("Hard", 9); val b = ConduitEngine.generate("Hard", 9)
        assertArrayEquals(a.start, b.start); assertArrayEquals(a.solution, b.solution)
    }
}
