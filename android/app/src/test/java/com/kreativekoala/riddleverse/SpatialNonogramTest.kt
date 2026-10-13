package com.kreativekoala.riddleverse

import org.junit.Assert.*
import org.junit.Test

class SpatialNonogramTest {

    private fun rowCandidates(size: Int, clue: IntArray): List<Int> {
        val out = ArrayList<Int>()
        for (m in 0 until (1 shl size)) {
            val line = BooleanArray(size) { (m shr it) and 1 == 1 }
            if (NonogramEngine.cluesOf(line).contentEquals(clue)) out.add(m)
        }
        return out
    }

    /** Independent brute force: count grids matching all clues (5x5 only). */
    private fun bruteCount(p: NonogramPuzzle): Int {
        val cands = p.rowClues.map { rowCandidates(p.size, it) }
        var count = 0
        fun rec(r: Int, rows: IntArray) {
            if (r == p.size) {
                for (c in 0 until p.size) {
                    val line = BooleanArray(p.size) { (rows[it] shr c) and 1 == 1 }
                    if (!NonogramEngine.cluesOf(line).contentEquals(p.colClues[c])) return
                }
                count++; return
            }
            for (m in cands[r]) { rows[r] = m; rec(r + 1, rows) }
        }
        rec(0, IntArray(p.size))
        return count
    }

    @Test fun easyPuzzlesAreUniqueByBruteForce() {
        for (seed in 1L..40L) {
            val p = NonogramEngine.generate("Easy", seed)
            assertEquals(5, p.size)
            assertEquals("seed $seed", 1, bruteCount(p))
            assertTrue(NonogramEngine.solvableByLogic(p))
        }
    }

    @Test fun mediumAndHardAreLogicSolvableAndMatchSolution() {
        for ((d, seeds) in listOf("Medium" to 8L, "Hard" to 4L)) {
            for (seed in 1L..seeds) {
                val p = NonogramEngine.generate(d, seed)
                val g = NonogramEngine.propagate(p.size, p.rowClues, p.colClues)
                assertNotNull(g)
                for (i in g!!.indices) assertEquals("$d seed $seed cell $i", if (p.solution[i]) 1 else 0, g[i])
                assertEquals(1, NonogramEngine.countSolutions(p.size, p.rowClues, p.colClues))
            }
        }
    }

    @Test fun sizesMatchDifficulty() {
        assertEquals(5, NonogramEngine.generate("easy", 1).size)
        assertEquals(10, NonogramEngine.generate("medium", 1).size)
        assertEquals(15, NonogramEngine.generate("hard", 1).size)
    }

    @Test fun deterministicForSeed() {
        val a = NonogramEngine.generate("Medium", 99)
        val b = NonogramEngine.generate("Medium", 99)
        assertArrayEquals(a.solution, b.solution)
        val c = NonogramEngine.generate("Medium", 100)
        assertFalse(a.solution.contentEquals(c.solution))
    }

    @Test fun lineSolverBasics() {
        // clue 5 in line of 5 -> all filled
        assertArrayEquals(intArrayOf(1, 1, 1, 1, 1), NonogramEngine.solveLine(intArrayOf(5), IntArray(5) { -1 }))
        // clue 3 in 5 -> middle cell forced
        val r = NonogramEngine.solveLine(intArrayOf(3), IntArray(5) { -1 })!!
        assertArrayEquals(intArrayOf(-1, -1, 1, -1, -1), r)
        // contradiction
        assertNull(NonogramEngine.solveLine(intArrayOf(4), intArrayOf(0, 1, 0, 1, 1)))
        // zero clue
        assertArrayEquals(IntArray(4), NonogramEngine.solveLine(intArrayOf(0), IntArray(4) { -1 }))
    }

    @Test fun stateUndoHintAndSolve() {
        val p = NonogramEngine.generate("Easy", 7)
        val s = NonogramState(p)
        s.applyStroke(intArrayOf(0, 1, 2), 1)
        assertTrue(s.canUndo())
        s.undo()
        assertTrue(s.cells.all { it == 0 })
        // fill the exact solution -> solved + clues satisfied
        s.applyStroke(p.solution.indices.filter { p.solution[it] }.toIntArray(), 1)
        assertTrue(s.filledMatches())
        for (i in 0 until 5) { assertTrue(s.isRowSatisfied(i)); assertTrue(s.isColSatisfied(i)) }
        // round-trip encode
        val t = NonogramState(p); t.restore(s.encode())
        assertArrayEquals(s.cells, t.cells)
    }

    @Test fun hintFixesMistakeThenRevealsSolutionCell() {
        val p = NonogramEngine.generate("Easy", 3)
        val s = NonogramState(p)
        val wrong = p.solution.indices.first { !p.solution[it] }
        s.applyStroke(intArrayOf(wrong), 1)
        assertEquals(wrong, s.hint())
        assertEquals(2, s.cells[wrong])
        val idx = s.hint()
        assertTrue(p.solution[idx]); assertEquals(1, s.cells[idx])
        assertEquals(2, s.hintsUsed)
    }
}
