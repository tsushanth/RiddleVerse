package com.kreativekoala.riddleverse

import org.junit.Assert.*
import org.junit.Test

class SpatialTrailTest {

    private val bandName = listOf("Easy", "Medium", "Hard")

    @Test fun fiveHundredSeedsPerSizeAreValidUniqueAndFast() {
        // warm the JIT so the per-puzzle time limit measures steady state, as on a running app
        for (s in 9000L..9019L) TrailEngine.generate(8, 2, s)
        val sb = StringBuilder()
        for (n in TrailEngine.MIN_N..TrailEngine.MAX_N) {
            var maxMs = 0.0; var totalMs = 0.0
            var dots = 0; var walls = 0; var nodes = 0L
            for (seed in 1L..500L) {
                val band = (seed % 3).toInt()
                val t0 = System.nanoTime()
                val p = TrailEngine.generate(n, band, seed)
                val ms = (System.nanoTime() - t0) / 1e6
                totalMs += ms; if (ms > maxMs) maxMs = ms
                assertTrue("n=$n seed=$seed took ${ms}ms", ms < 200.0)
                assertEquals(n, p.n)
                assertTrue("n=$n seed=$seed generator solution invalid", p.isValidSolution(p.solution))
                // independent uniqueness proof: exactly one solution, and it is the generator's
                val r = TrailEngine.solve(p, limit = 2)
                assertFalse("n=$n seed=$seed solver aborted", r.aborted)
                assertEquals("n=$n seed=$seed band=$band not unique", 1, r.count)
                assertArrayEquals("n=$n seed=$seed solver found a different path", p.solution, r.solutions[0])
                assertTrue(p.waypoints.size >= 2)
                assertEquals(p.solution[0], p.waypoints[0])
                assertEquals(p.solution[p.cells - 1], p.waypoints.last())
                dots += p.waypoints.size; walls += p.wallCount; nodes += p.nodes
            }
            for (b in 0..2) {
                var nd = 0L; var dd = 0; var ww = 0; var tt = 0.0; var mx = 0.0
                for (seed in 1L..100L) {
                    val t0 = System.nanoTime(); val q = TrailEngine.generate(n, b, seed + 1000)
                    val ms = (System.nanoTime() - t0) / 1e6; tt += ms; if (ms > mx) mx = ms
                    nd += q.nodes; dd += q.waypoints.size; ww += q.wallCount
                }
                sb.append(String.format("  N=%d %s: dots %.1f walls %.1f proofNodes %.0f avg %.2f ms max %.1f ms%n", n, bandName[b], dd / 100.0, ww / 100.0, nd / 100.0, tt / 100, mx))
            }
            sb.append(String.format("N=%d: avg %.2f ms, max %.1f ms, dots %.1f, walls %.1f, nodes %.0f%n",
                n, totalMs / 500, maxMs, dots / 500.0, walls / 500.0, nodes / 500.0))
        }
        println("TRAIL STATS\n$sb")
    }

    @Test fun difficultyBandsGetHarder() {
        for (band in 0..2) {
            var dots = 0.0; var walls = 0.0
            for (seed in 1L..120L) {
                val p = TrailEngine.generate(6, band, seed)
                dots += p.waypoints.size.toDouble() / p.cells; walls += p.wallCount
            }
            println("band ${bandName[band]} @6x6: dot density ${"%.3f".format(dots / 120)}, walls ${"%.2f".format(walls / 120)}")
        }
        fun avgDots(band: Int) = (1L..120L).sumOf { TrailEngine.generate(6, band, it).waypoints.size } / 120.0
        assertTrue(avgDots(0) > avgDots(1))
        assertTrue(avgDots(1) >= avgDots(2))
        // easy never has walls
        for (seed in 1L..60L) assertEquals(0, TrailEngine.generate(6, 0, seed).wallCount)
    }

    @Test fun deterministicBySeed() {
        val a = TrailEngine.generate("Hard", 77); val b = TrailEngine.generate("Hard", 77)
        assertArrayEquals(a.solution, b.solution)
        assertArrayEquals(a.waypoints, b.waypoints)
        assertArrayEquals(a.wallE, b.wallE); assertArrayEquals(a.wallS, b.wallS)
        val c = TrailEngine.generate("Hard", 78)
        assertFalse(a.solution.contentEquals(c.solution) && a.n == c.n)
    }

    @Test fun difficultyMapsToSizes() {
        for (s in 1L..10L) {
            assertTrue(TrailEngine.sizeFor("Easy", s) in 5..6)
            assertTrue(TrailEngine.sizeFor("Medium", s) in 6..7)
            assertTrue(TrailEngine.sizeFor("Hard", s) in 7..8)
        }
        assertEquals(5, TrailEngine.sizeFor("Easy", -2L)) // negative seeds are fine
    }

    @Test fun randomPathsAreHamiltonian() {
        for (n in 2..8) for (s in 1L..50L) {
            val p = TrailEngine.randomHamiltonianPath(n, kotlin.random.Random(s))
            assertEquals(n * n, p.toSet().size)
            for (i in 1 until p.size) assertEquals(1, Math.abs(p[i] / n - p[i - 1] / n) + Math.abs(p[i] % n - p[i - 1] % n))
        }
    }

    @Test fun solverCountsMultipleSolutions() {
        // 3x3, only start and end dots: a 3x3 corner-to-corner Hamiltonian path is not unique
        val r = TrailEngine.solve(3, intArrayOf(0, 8), BooleanArray(9), BooleanArray(9), limit = 100)
        assertTrue(r.count > 1)
        // 2x2 with start 0, end 1: unique 0,2,3,1
        val r2 = TrailEngine.solve(2, intArrayOf(0, 1), BooleanArray(4), BooleanArray(4))
        assertEquals(1, r2.count)
        assertArrayEquals(intArrayOf(0, 2, 3, 1), r2.solutions[0])
    }

    private fun puzzle(): TrailPuzzle = TrailEngine.generate(5, 0, 3)

    @Test fun followingTheSolutionSolvesIt() {
        for (seed in 1L..40L) {
            val p = TrailEngine.generate(6, (seed % 3).toInt(), seed)
            val s = TrailState(p)
            assertEquals(p.solution[0], s.head)
            for (i in 1 until p.cells) assertTrue("seed $seed step $i", s.step(p.solution[i]))
            assertTrue(s.solved())
            assertEquals(p.totalDots(), s.dotsVisited)
        }
    }

    private fun TrailPuzzle.totalDots() = waypoints.size

    @Test fun dragBackUndoesAndDotsMustGoInOrder() {
        val p = puzzle(); val s = TrailState(p)
        assertTrue(s.step(p.solution[1])); assertTrue(s.step(p.solution[2]))
        assertEquals(3, s.length)
        assertTrue(s.step(p.solution[1]))          // stepping onto the previous cell removes the last one
        assertEquals(2, s.length)
        assertTrue(s.step(p.solution[0]))          // dragging back to the start leaves only the start
        assertEquals(1, s.length)
        assertFalse(s.step(p.solution[0]))         // the start itself cannot be re-entered
        // entering the last dot early is refused
        val last = p.waypoints.last()
        for (nb in listOf(last - 1, last + 1, last - p.n, last + p.n)) {
            if (nb in 0 until p.cells && p.adjacent(last, nb)) {
                val t = TrailState(p)
                // teleport-free check: canStep from a neighbour of the last dot is false while length < cells - 1
                assertFalse(t.canStep(nb, last))
            }
        }
        // a dot out of order is refused: find dot 3 (index 2) and try to step onto it while only dot 1 is visited
        val d3 = p.waypoints[2]
        val t = TrailState(p)
        for (nb in 0 until p.cells) if (p.adjacent(nb, d3) && !p.blocked(nb, d3)) assertFalse(t.canStep(nb, d3))
    }

    @Test fun wallsAreNeverCrossed() {
        var checked = 0
        for (seed in 1L..80L) {
            val p = TrailEngine.generate(6, 2, seed)
            if (p.wallCount == 0) continue
            val s = TrailState(p)
            for (a in 0 until p.cells) for (b in 0 until p.cells) {
                if (p.adjacent(a, b) && p.blocked(a, b)) { assertFalse(s.canStep(a, b)); checked++ }
            }
            // and the solution never crosses one
            for (i in 1 until p.cells) assertFalse(p.blocked(p.solution[i - 1], p.solution[i]))
        }
        assertTrue("no walls generated in 80 hard puzzles", checked > 0)
    }

    @Test fun flickAndDiagonalDragsDoNotSkipCells() {
        val p = puzzle(); val s = TrailState(p)
        // flick straight onto a cell 3 solution steps ahead that is in a straight line, if any: otherwise use the walker
        val start = p.solution[0]
        // advanceTo a far target on the solution: the walk must only add legal, distinct cells
        val target = p.solution[2]
        s.advanceTo(target)
        val path = s.pathCells()
        for (i in 1 until path.size) { assertTrue(p.adjacent(path[i - 1], path[i])); assertFalse(p.blocked(path[i - 1], path[i])) }
        assertEquals(path.size, path.toSet().size)
        assertEquals(start, path[0])
        // diagonal drag: build an open 3x3 puzzle by hand through the state API
        val open = TrailPuzzle(3, intArrayOf(0, 8), BooleanArray(9), BooleanArray(9), intArrayOf(0, 1, 2, 5, 4, 3, 6, 7, 8), 0, 0)
        val d = TrailState(open)
        assertTrue(d.advanceTo(4))                  // diagonal from corner: one legal intermediate is chosen
        assertEquals(3, d.length)
        assertEquals(4, d.head)
        // dragging onto a cell already on the line cuts back to it
        d.advanceTo(0.let { d.cellAtStep(1) })
        assertEquals(2, d.length)
    }

    @Test fun geometryInterpolatesFastFlicks() {
        val cell = 50f
        // horizontal flick across 5 cells in one event
        val cells = TrailGeometry.cellsAlong(10f, 25f, 240f, 25f, cell, 6)
        assertArrayEquals(intArrayOf(0, 1, 2, 3, 4), cells)
        // far outside the board is clamped, not dropped
        val out = TrailGeometry.cellsAlong(10f, 25f, 900f, 25f, cell, 6)
        assertEquals(5, out.last())
        assertEquals(-1, TrailGeometry.cellAt(-1f, 10f, cell, 6))
        assertEquals(7, TrailGeometry.cellAt(60f, 60f, cell, 6))
        // sticky head: just over the edge still counts as the head cell
        assertTrue(TrailGeometry.stickyHit(54f, 20f, 0, cell, 6))
        assertFalse(TrailGeometry.stickyHit(62f, 20f, 0, cell, 6))
    }

    @Test fun tapExtendsAdjacentAndCutsBack() {
        val p = puzzle(); val s = TrailState(p)
        assertFalse(s.tap(p.solution[5]))           // not adjacent
        assertTrue(s.tap(p.solution[1]))
        assertTrue(s.tap(p.solution[2]))
        assertTrue(s.tap(p.solution[1]))            // tapping an earlier cell cuts back to it
        assertEquals(2, s.length)
    }

    @Test fun hintRevealsNextCellsAndRepairsWrongTurns() {
        val p = puzzle(); val s = TrailState(p)
        val h = s.hint(3)
        assertArrayEquals(p.solution.copyOfRange(1, 4), h)
        assertEquals(1, s.hintsUsed)
        for (i in 1..3) s.step(p.solution[i])
        // wander off the solution if possible
        val wrong = (0 until p.cells).firstOrNull { !s.onPath(it) && p.adjacent(s.head, it) && s.canStep(s.head, it) && it != p.solution[4] }
        if (wrong != null) {
            s.step(wrong)
            val h2 = s.hint(2)
            assertEquals(4, s.length)               // cut back to the last correct cell
            assertArrayEquals(p.solution.copyOfRange(4, 6), h2)
        }
        assertEquals(2, s.stars().coerceAtLeast(2)) // sanity: stars within range
        assertTrue(s.stars() in 1..3)
    }

    @Test fun resetAndEncodeRestore() {
        val p = puzzle(); val s = TrailState(p)
        for (i in 1..6) s.step(p.solution[i])
        val t = TrailState(p); t.restore(s.encode())
        assertArrayEquals(s.pathCells(), t.pathCells())
        assertEquals(s.dotsVisited, t.dotsVisited)
        s.reset()
        assertEquals(1, s.length); assertEquals(1, s.dotsVisited); assertEquals(p.solution[0], s.head)
        t.restore("garbage")                        // never crashes
        assertTrue(t.length >= 1)
    }
}
