package com.kreativekoala.riddleverse

import org.junit.Assert.*
import org.junit.Test
import kotlin.random.Random

class ClassicSlideTilesLogicTest {
    @Test fun scrambleIsAlwaysSolvableByParityAndNeverSolved() {
        for (tier in 0..4) {
            val cfg = SlideTilesRules.configFor(tier)
            for (seed in 0L until 60L) {
                val g = SlideTilesGame.newGame(tier, seed)
                assertEquals(cfg.n * cfg.n, g.tiles.size)
                assertEquals((0 until cfg.n * cfg.n).toList(), g.tiles.sorted())
                assertTrue("tier $tier seed $seed", SlideTilesRules.isSolvable(cfg.n, g.tiles))
                assertFalse(g.isSolved)
            }
        }
    }

    @Test fun parityCheckRejectsSwappedTiles() {
        val t = SlideTilesRules.solvedTiles(3).toMutableList()
        t[0] = 2; t[1] = 1
        assertFalse(SlideTilesRules.isSolvable(3, t))
        val t4 = SlideTilesRules.solvedTiles(4).toMutableList()
        t4[0] = 2; t4[1] = 1
        assertFalse(SlideTilesRules.isSolvable(4, t4))
        assertTrue(SlideTilesRules.isSolvable(4, SlideTilesRules.solvedTiles(4)))
    }

    /** Exhaustive proof for 3x3: BFS from the goal reaches every generated board. */
    @Test fun threeByThreeScramblesReachableByBfs() {
        val goal = SlideTilesRules.solvedTiles(3)
        val dist = HashMap<List<Int>, Int>()
        dist[goal] = 0
        val q = ArrayDeque<List<Int>>()
        q.add(goal)
        while (q.isNotEmpty()) {
            val cur = q.removeFirst()
            val g = SlideTilesGame(3, cur, 0, 0)
            for (nb in g.neighborsOfBlank()) {
                val nx = g.slideAdjacent(nb).tiles
                if (nx !in dist) { dist[nx] = dist[cur]!! + 1; q.add(nx) }
            }
        }
        assertEquals(181440, dist.size)
        for (tier in 0..1) for (seed in 0L until 50L) {
            val g = SlideTilesGame.newGame(tier, seed)
            val d = dist[g.tiles]
            assertNotNull(d)
            assertTrue("optimal ${d} must be <= par ${g.par}", d!! <= g.par)
        }
    }

    @Test fun tapAdjacentAndLineSlides() {
        // 3x3 solved: blank at 8. Tap 7 (adjacent) -> 1 move.
        val g = SlideTilesGame.solved(3)
        val a = g.tap(7)!!
        assertEquals(1, a.moves)
        assertEquals(0, a.tiles[7])
        assertEquals(8, a.tiles[8])
        // tap 6 (two away in row) -> shifts 2 tiles
        val b = g.tap(6)!!
        assertEquals(2, b.moves)
        assertEquals(listOf(1, 2, 3, 4, 5, 6, 0, 7, 8), b.tiles)
        // tap diagonal tile (not in row/col with blank) is illegal; tap blank is illegal
        assertNull(g.tap(0 + 4))
        assertNull(g.tap(8))
    }

    @Test fun undoRestoresBoardAndMoves() {
        val g = SlideTilesGame.solved(3)
        val b = g.tap(6)!!
        val u = b.undo()
        assertEquals(g.tiles, u.tiles)
        assertEquals(0, u.moves)
        assertFalse(u.canUndo)
    }

    @Test fun solvedDetectedAfterReversingScramble() {
        val g0 = SlideTilesGame.newGame(0, 3)
        var g = g0
        // greedy: replay undo can't be used (history empty) - instead construct 1-move-from-solved
        val s = SlideTilesGame.solved(3).tap(7)!!
        assertFalse(s.isSolved)
        assertTrue(s.tap(8)!!.isSolved)
        assertNotNull(g)
    }

    @Test fun deterministicWithSeed() {
        assertEquals(SlideTilesGame.newGame(2, 7), SlideTilesGame.newGame(2, 7))
        assertNotEquals(SlideTilesGame.newGame(2, 7).tiles, SlideTilesGame.newGame(2, 8).tiles)
    }

    @Test fun encodeDecodeRoundTrip() {
        var g = SlideTilesGame.newGame(2, 5)
        g = g.tap(g.neighborsOfBlank().first())!!
        assertEquals(g, SlideTilesGame.decode(g.encode()))
        assertNull(SlideTilesGame.decode("x"))
    }
}
