package com.kreativekoala.riddleverse

import org.junit.Assert.*
import org.junit.Test

/** Pure-JVM tests for the feat/puzzles-grid generators/solvers. */
class GridGeneratorsTest {

    private val seeds = longArrayOf(1L, 2L, 3L, 42L, 2026L, 99991L)

    // ---------- Sudoku ----------
    @Test fun sudoku_unique_valid_and_matches_tier() {
        for ((diff, size) in listOf("easy" to 4, "medium" to 6, "hard" to 9)) {
            for (s in seeds) {
                val p = SudokuGenerator.generate(diff, s)
                assertEquals(size, p.size)
                assertEquals("$diff/$s solution consistent", true, SudokuGenerator.isConsistent(p.solution.copyOf(), p.size, p.boxRows, p.boxCols))
                assertTrue(p.solution.none { it == 0 })
                assertEquals("$diff/$s unique", 1, SudokuGenerator.countSolutions(p.puzzle, p.size, p.boxRows, p.boxCols, 2))
                for (i in p.puzzle.indices) if (p.puzzle[i] != 0) assertEquals(p.solution[i], p.puzzle[i])
                // each unit contains each digit once
                for (r in 0 until size) assertEquals((1..size).toSet(), (0 until size).map { p.solution[r * size + it] }.toSet())
                for (c in 0 until size) assertEquals((1..size).toSet(), (0 until size).map { p.solution[it * size + c] }.toSet())
            }
        }
    }

    @Test fun sudoku_tiers_are_progressively_harder() {
        val cfg = listOf("easy", "medium", "hard").map { SudokuGenerator.configFor(it) }
        assertEquals(listOf(4, 6, 9), cfg.map { it[0] })
        // fraction of cells given decreases with difficulty
        val frac = cfg.map { it[3].toDouble() / (it[0] * it[0]) }
        assertTrue(frac[0] > frac[1] && frac[1] > frac[2])
        for (d in listOf("easy", "medium", "hard")) {
            val p = SudokuGenerator.generate(d, 7)
            assertTrue("$d clues ${p.clueCount}", p.clueCount <= SudokuGenerator.configFor(d)[3] + 2)
        }
    }

    @Test fun sudoku_deterministic_and_seed_sensitive() {
        val a = SudokuGenerator.generate("hard", 5)
        val b = SudokuGenerator.generate("hard", 5)
        val c = SudokuGenerator.generate("hard", 6)
        assertArrayEquals(a.puzzle, b.puzzle)
        assertArrayEquals(a.solution, b.solution)
        assertFalse(a.solution.contentEquals(c.solution))
    }

    @Test fun sudoku_solver_detects_multiple_and_no_solutions() {
        val empty = IntArray(16)
        assertEquals(2, SudokuGenerator.countSolutions(empty, 4, 2, 2, 2))
        val bad = IntArray(16).also { it[0] = 1; it[1] = 1 }
        assertEquals(0, SudokuGenerator.countSolutions(bad, 4, 2, 2, 2))
    }

    // ---------- Binary Grid ----------
    @Test fun binairo_unique_and_valid() {
        for ((diff, size) in listOf("easy" to 6, "medium" to 8, "hard" to 10)) {
            for (s in seeds) {
                val p = BinairoGenerator.generate(diff, s)
                assertEquals(size, p.size)
                assertTrue("$diff/$s solution valid", BinairoGenerator.isSolved(p.solution, size))
                assertEquals("$diff/$s unique", 1, BinairoGenerator.countSolutions(p.puzzle, size, 2))
                for (i in p.puzzle.indices) if (p.puzzle[i] != 0) assertEquals(p.solution[i], p.puzzle[i])
            }
        }
    }

    @Test fun binairo_tiers_and_determinism() {
        val c = listOf("easy", "medium", "hard").map { BinairoGenerator.configFor(it) }
        val frac = c.map { it[1].toDouble() / (it[0] * it[0]) }
        assertTrue(frac[0] > frac[1] && frac[1] > frac[2])
        val a = BinairoGenerator.generate("medium", 11)
        val b = BinairoGenerator.generate("medium", 11)
        assertArrayEquals(a.puzzle, b.puzzle)
    }

    @Test fun binairo_solver_rejects_rule_violations() {
        val g = IntArray(36)
        g[0] = 1; g[1] = 1; g[2] = 1
        assertEquals(0, BinairoGenerator.countSolutions(g, 6, 2))
        assertFalse(BinairoGenerator.isSolved(IntArray(36) { 1 }, 6))
    }

    // ---------- Cage Math ----------
    @Test fun cageMath_unique_and_valid() {
        for ((diff, size) in listOf("easy" to 4, "medium" to 5, "hard" to 6)) {
            for (s in seeds) {
                val p = KenKenGenerator.generate(diff, s)
                assertEquals(size, p.size)
                for (r in 0 until size) {
                    assertEquals((1..size).toSet(), (0 until size).map { p.solution[r * size + it] }.toSet())
                    assertEquals((1..size).toSet(), (0 until size).map { p.solution[it * size + r] }.toSet())
                }
                // cages partition the grid, are connected, and are satisfied by the solution
                assertEquals((0 until size * size).toList(), p.cages.flatMap { it.cells.toList() }.sorted())
                for (c in p.cages) {
                    assertTrue(KenKenGenerator.cageSatisfied(c, c.cells.map { p.solution[it] }))
                    assertTrue(c.cells.size in 1..4)
                    assertTrue("connected", connected(c.cells.toList(), size))
                }
                assertEquals("$diff/$s unique", 1, KenKenGenerator.countSolutions(size, p.cages, 2))
            }
        }
    }

    @Test fun cageMath_ops_follow_tier() {
        val easyOps = (1L..20L).flatMap { KenKenGenerator.generate("easy", it).cages.map { c -> c.op } }.toSet()
        assertTrue(easyOps.all { it == '+' || it == '-' || it == '=' })
        val hardOps = (1L..40L).flatMap { KenKenGenerator.generate("hard", it).cages.map { c -> c.op } }.toSet()
        assertTrue(hardOps.contains('*'))
    }

    @Test fun cageMath_deterministic() {
        val a = KenKenGenerator.generate("hard", 3)
        val b = KenKenGenerator.generate("hard", 3)
        assertArrayEquals(a.solution, b.solution)
        assertEquals(a.cages.map { it.label + it.cells.toList() }, b.cages.map { it.label + it.cells.toList() })
    }

    private fun connected(cells: List<Int>, n: Int): Boolean {
        val seen = mutableSetOf(cells.first())
        val q = ArrayDeque(listOf(cells.first()))
        while (q.isNotEmpty()) {
            val x = q.removeFirst()
            for (y in cells) if (y !in seen) {
                val adj = (x / n == y / n && kotlin.math.abs(x - y) == 1) || (x % n == y % n && kotlin.math.abs(x - y) == n)
                if (adj) { seen.add(y); q.add(y) }
            }
        }
        return seen.size == cells.size
    }

    // ---------- Sign Grid ----------
    @Test fun signGrid_unique_and_valid() {
        for ((diff, size) in listOf("easy" to 4, "medium" to 5, "hard" to 6)) {
            for (s in seeds) {
                val p = FutoshikiGenerator.generate(diff, s)
                assertEquals(size, p.size)
                assertTrue("$diff/$s solution valid", FutoshikiGenerator.isSolved(p, p.solution))
                assertEquals("$diff/$s unique", 1, FutoshikiGenerator.countSolutions(size, p.givens, p.hRel, p.vRel, 2))
                for (i in p.givens.indices) if (p.givens[i] != 0) assertEquals(p.solution[i], p.givens[i])
            }
        }
    }

    @Test fun signGrid_hard_has_fewer_clues_than_easy_on_average() {
        fun avg(d: String) = (1L..6L).map { FutoshikiGenerator.generate(d, it).clueCount.toDouble() / FutoshikiGenerator.sizeFor(d).let { n -> n * n } }.average()
        assertTrue(avg("easy") > avg("hard"))
    }

    @Test fun signGrid_deterministic() {
        val a = FutoshikiGenerator.generate("medium", 8)
        val b = FutoshikiGenerator.generate("medium", 8)
        assertArrayEquals(a.givens, b.givens); assertArrayEquals(a.hRel, b.hRel); assertArrayEquals(a.vRel, b.vRel)
    }

    // ---------- Factory ----------
    @Test fun factory_builds_every_game_and_round_trips_seed() {
        for (g in GridGame.values()) for (d in listOf("Easy", "Medium", "Hard")) {
            val p = GridPuzzleFactory.createPuzzle(g, d, 77L)
            assertEquals(g.key, p.puzzleType)
            assertEquals(77L, GridPuzzleFactory.seedFrom(p))
            val spec = GridPuzzleFactory.build(g, d, 77L)
            assertEquals(spec.n * spec.n, spec.solution.size)
        }
        assertNull(GridPuzzleFactory.createPuzzleOrNull("symmetry", "Easy"))
    }
}
