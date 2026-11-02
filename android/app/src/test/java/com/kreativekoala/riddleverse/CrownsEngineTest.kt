package com.kreativekoala.riddleverse

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.abs

class CrownsEngineTest {

    private fun assertValid(p: CrownsPuzzle, label: String) {
        val n = p.n
        assertEquals(label, n * n, p.regions.size)
        assertTrue("$label regions connected / ids 0 until n", CrownsEngine.regionsConnected(n, p.regions))
        assertTrue("$label stored solution obeys the rules", CrownsEngine.isValidPlacement(n, p.regions, p.solution))
        // independent check of the "no touching" rule and one-per-line rules
        val cols = p.solution.toSet(); assertEquals(label, n, cols.size)
        val regs = (0 until n).map { p.regions[it * n + p.solution[it]] }.toSet(); assertEquals(label, n, regs.size)
        for (r in 1 until n) assertTrue(label, abs(p.solution[r] - p.solution[r - 1]) >= 2)
        val sols = CrownsEngine.solutions(n, p.regions, 2)
        assertEquals("$label must have exactly one solution", 1, sols.size)
        assertArrayEquals(label, p.solution, sols[0])
    }

    @Test fun thousandSeedsPerSizeAreValidUniqueAndFast() {
        // warm the JIT so the time budget measures steady state, like a running app
        for (s in 0L until 30L) CrownsEngine.generate(9, 90_000 + s)
        val report = StringBuilder()
        val cpu = java.lang.management.ManagementFactory.getThreadMXBean()
        for (n in CrownsEngine.MIN_N..CrownsEngine.MAX_N) {
            var maxMs = 0.0; var totalMs = 0.0
            val bands = IntArray(5)
            for (seed in 1L..1000L) {
                // thread CPU time, not wall clock: other builds on a busy dev machine must not fail the budget
                val t0 = cpu.currentThreadCpuTime
                val p = CrownsEngine.generate(n, seed)
                val ms = (cpu.currentThreadCpuTime - t0) / 1e6
                totalMs += ms; if (ms > maxMs) maxMs = ms
                assertValid(p, "n=$n seed=$seed")
                bands[p.band]++
                assertTrue("n=$n seed=$seed took $ms ms (budget 150)", ms < 150.0)
            }
            report.append("N=$n avg=%.2fms max=%.2fms bands(1..4)=%s\n".format(totalMs / 1000, maxMs, bands.drop(1)))
        }
        println(report)
    }

    @Test fun deterministicForSeed() {
        for (n in 5..9) {
            val a = CrownsEngine.generate(n, 4242)
            val b = CrownsEngine.generate(n, 4242)
            assertArrayEquals(a.regions, b.regions)
            assertArrayEquals(a.solution, b.solution)
            assertEquals(a.band, b.band)
            val c = CrownsEngine.generate(n, 4243)
            assertFalse(a.regions.contentEquals(c.regions) && a.solution.contentEquals(c.solution))
        }
    }

    @Test fun difficultyMapsToSizeRange() {
        for (seed in 1L..40L) {
            assertTrue(CrownsEngine.sizeFor("Easy", seed) in 5..6)
            assertEquals(7, CrownsEngine.sizeFor("Medium", seed))
            assertTrue(CrownsEngine.sizeFor("Hard", seed) in 8..9)
            assertEquals(6, CrownsEngine.sizeFor("6", seed))
        }
        assertEquals(CrownsEngine.sizeFor("Hard", 7), CrownsEngine.generate("Hard", 7).n)
    }

    @Test fun hardBandOutranksEasyBand() {
        var easy = 0; var hard = 0
        for (seed in 1L..60L) {
            easy += CrownsEngine.generate("Easy", seed).band
            hard += CrownsEngine.generate("Hard", seed).band
        }
        assertTrue("avg hard band $hard should exceed easy $easy", hard > easy)
    }

    @Test fun difficultyLevelsLandInTheirBands() {
        var easyOk = 0; var medOk = 0; var hardOk = 0
        val total = 200
        for (seed in 1L..total.toLong()) {
            if (CrownsEngine.generate("Easy", seed).band <= 2) easyOk++
            if (CrownsEngine.generate("Medium", seed).band in 2..3) medOk++
            if (CrownsEngine.generate("Hard", seed).band >= 3) hardOk++
        }
        println("band match: easy=$easyOk/$total medium=$medOk/$total hard=$hardOk/$total")
        assertTrue("easy $easyOk", easyOk >= total * 0.95)
        assertTrue("medium $medOk", medOk >= total * 0.85)
        assertTrue("hard $hardOk", hardOk >= total * 0.85)
    }

    @Test fun solverDetectsNonUniqueAndInvalid() {
        // 5x5 with horizontal bands as regions: many solutions
        val bands = IntArray(25) { it / 5 }
        assertTrue(CrownsEngine.countSolutions(5, bands, 2) >= 2)
        // a region id used twice in a row but crowns forced: placement validity
        val p = CrownsEngine.generate(5, 3)
        val bad = p.solution.copyOf().also { it[1] = it[0] }
        assertFalse(CrownsEngine.isValidPlacement(5, p.regions, bad))
        val touching = intArrayOf(0, 1, 3, 0, 2)
        assertFalse(CrownsEngine.isValidPlacement(5, p.regions, touching))
    }

    // ---- player state

    private fun newState(seed: Long = 5, n: Int = 5) = CrownsState(CrownsEngine.generate(n, seed))

    @Test fun tapCyclesEmptyMarkCrownEmpty() {
        val g = newState()
        assertEquals(1, g.tap(0)); assertEquals(2, g.tap(0)); assertEquals(0, g.tap(0))
    }

    @Test fun undoRevertsTapsInOrder() {
        val g = newState()
        g.tap(3); g.tap(3); g.tap(7)
        assertEquals(2, g.cells[3]); assertEquals(1, g.cells[7])
        assertTrue(g.undo()); assertEquals(0, g.cells[7])
        assertTrue(g.undo()); assertEquals(1, g.cells[3])
        assertTrue(g.undo()); assertEquals(0, g.cells[3])
        assertFalse(g.undo())
    }

    @Test fun conflictsFlagRowColRegionAndTouching() {
        val p = CrownsEngine.generate(6, 11)
        val n = p.n
        val g = CrownsState(p)
        g.cells[0] = 2; g.cells[3] = 2                 // same row
        assertTrue(g.conflicts()[0] && g.conflicts()[3])
        g.cells.fill(0); g.cells[0] = 2; g.cells[2 * n] = 2   // same column
        assertTrue(g.conflicts()[0] && g.conflicts()[2 * n])
        g.cells.fill(0); g.cells[0] = 2; g.cells[n + 1] = 2   // diagonal touch
        assertTrue(g.conflicts()[0] && g.conflicts()[n + 1])
        // same region, in different rows and columns and not touching
        g.cells.fill(0)
        var pair: Pair<Int, Int>? = null
        for (i in 0 until n * n) for (j in i + 1 until n * n) {
            if (p.regions[i] == p.regions[j] && i / n != j / n && i % n != j % n &&
                abs(i / n - j / n) > 1 && abs(i % n - j % n) > 1 && pair == null) pair = i to j
        }
        val (a, b) = pair!!
        g.cells[a] = 2; g.cells[b] = 2
        assertTrue(g.conflicts()[a] && g.conflicts()[b])
        // marks never conflict
        g.cells.fill(1)
        assertFalse(g.hasConflicts())
    }

    @Test fun placingTheSolutionSolvesIt() {
        for (n in 5..9) {
            val p = CrownsEngine.generate(n, 21L + n)
            val g = CrownsState(p)
            assertFalse(g.isSolved())
            for (r in 0 until n) g.cells[r * n + p.solution[r]] = 2
            assertTrue("n=$n", g.isSolved())
            // a stray extra crown breaks it
            val extra = (0 until n * n).first { g.cells[it] == 0 }
            g.cells[extra] = 2
            assertFalse(g.isSolved())
        }
    }

    @Test fun hintRemovesWrongCrownThenPlacesCorrectOnes() {
        val p = CrownsEngine.generate(6, 8)
        val g = CrownsState(p)
        val wrong = (0 until 36).first { !p.isSolutionCell(it) }
        g.cells[wrong] = 2
        val h1 = g.hint()!!
        assertTrue(h1.removed); assertEquals(wrong, h1.cell); assertEquals(0, g.cells[wrong])
        var guard = 0
        while (!g.isSolved() && guard++ < 10) {
            val h = g.hint()!!
            assertFalse(h.removed)
            assertTrue(p.isSolutionCell(h.cell))
        }
        assertTrue(g.isSolved())
        assertNull(g.hint())
        assertEquals(7, g.hintsUsed)
        assertEquals(1, g.stars())
        assertTrue(g.score() >= 10)
    }

    @Test fun encodeRestoreRoundTrip() {
        val g = newState(); g.tap(1); g.tap(1); g.tap(9); g.hint()
        val s = g.encode()
        val h = CrownsState(g.puzzle); h.restore(s)
        assertArrayEquals(g.cells, h.cells)
        assertEquals(g.hintsUsed, h.hintsUsed)
        h.restore("garbage")   // wrong length is ignored
        assertArrayEquals(g.cells, h.cells)
    }

    @Test fun registeredAsPlayableGame() {
        assertTrue(SpatialPuzzles.isSpatial("crowns"))
        assertEquals("Crowns Screen", SpatialPuzzles.screenOption("crowns"))
        assertEquals(PuzzleScreenType.CROWNS_SCREEN, PuzzleScreenType.fromString("Crowns Screen"))
        assertEquals("Crowns", PuzzleTypeNames.display("crowns"))
        assertNotNull(SpatialPuzzles.tutorialContent("crowns"))
        assertTrue(SpatialPuzzles.categories.any { it.category == "crowns" })
    }
}
