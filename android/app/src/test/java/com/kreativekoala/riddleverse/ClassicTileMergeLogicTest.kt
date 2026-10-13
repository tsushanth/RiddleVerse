package com.kreativekoala.riddleverse

import org.junit.Assert.*
import org.junit.Test

class ClassicTileMergeLogicTest {
    @Test fun slideLineMergesOncePerPair() {
        assertEquals(listOf(4, 4, 0, 0), TileMergeRules.slideLine(listOf(2, 2, 2, 2)).first)
        assertEquals(8, TileMergeRules.slideLine(listOf(2, 2, 2, 2)).second)
        assertEquals(listOf(4, 2, 0, 0), TileMergeRules.slideLine(listOf(2, 2, 2, 0)).first)
        assertEquals(listOf(4, 4, 0, 0), TileMergeRules.slideLine(listOf(0, 2, 2, 4)).first)
        assertEquals(listOf(8, 4, 0, 0), TileMergeRules.slideLine(listOf(4, 4, 4, 0)).first)
        assertEquals(listOf(2, 4, 2, 4), TileMergeRules.slideLine(listOf(2, 4, 2, 4)).first)
    }

    @Test fun moveDirectionsAreCorrect() {
        val cells = MutableList(16) { 0 }
        cells[5] = 2 // row1 col1
        val g = TileMergeGame(cells, 0, 0, 1L)
        fun tiles(x: TileMergeGame) = x.cells.withIndex().filter { it.value != 0 }.map { it.index }
        assertTrue(tiles(g.move(MergeDir.LEFT, 99999)).contains(4))
        assertTrue(tiles(g.move(MergeDir.RIGHT, 99999)).contains(7))
        assertTrue(tiles(g.move(MergeDir.UP, 99999)).contains(1))
        assertTrue(tiles(g.move(MergeDir.DOWN, 99999)).contains(13))
    }

    @Test fun noOpMoveDoesNotSpawnOrCount() {
        val cells = MutableList(16) { 0 }
        cells[0] = 2
        val g = TileMergeGame(cells, 0, 0, 1L)
        assertSame(g, g.move(MergeDir.LEFT, 128))
        assertSame(g, g.move(MergeDir.UP, 128))
    }

    @Test fun validMoveSpawnsExactlyOneTileAndScores() {
        val cells = MutableList(16) { 0 }
        cells[0] = 2; cells[1] = 2
        val g = TileMergeGame(cells, 0, 0, 7L).move(MergeDir.LEFT, 128)
        assertEquals(4, g.score)
        assertEquals(1, g.moves)
        assertEquals(2, g.cells.count { it != 0 }) // merged 4 + one spawn
        assertEquals(4, g.cells[0])
    }

    @Test fun deterministicWithSeed() {
        fun play(seed: Long): TileMergeGame {
            var g = TileMergeGame.newGame(seed)
            val dirs = MergeDir.values()
            for (i in 0 until 60) g = g.move(dirs[i % 4], 2048)
            return g
        }
        assertEquals(play(42), play(42))
        assertNotEquals(play(42).cells, play(43).cells)
    }

    @Test fun newGameHasTwoTiles() {
        val g = TileMergeGame.newGame(5)
        assertEquals(2, g.cells.count { it != 0 })
        assertTrue(g.cells.all { it == 0 || it == 2 || it == 4 })
    }

    @Test fun winDetectedAtTarget() {
        val cells = MutableList(16) { 0 }
        cells[0] = 64; cells[1] = 64
        val g = TileMergeGame(cells, 0, 0, 3L).move(MergeDir.LEFT, 128)
        assertTrue(g.reachedTarget)
        val h = TileMergeGame(cells, 0, 0, 3L).move(MergeDir.LEFT, 256)
        assertFalse(h.reachedTarget)
    }

    @Test fun lossDetection() {
        // checkerboard of 2/4 with no empty cells and no equal neighbours
        val cells = List(16) { if ((it / 4 + it % 4) % 2 == 0) 2 else 4 }
        assertFalse(TileMergeGame(cells, 0, 0, 1L).canMove())
        val almost = cells.toMutableList().also { it[15] = it[14] }
        assertTrue(TileMergeGame(almost, 0, 0, 1L).canMove())
    }

    @Test fun undoRestoresAndIsSingleLevel() {
        val g0 = TileMergeGame.newGame(11)
        var g = g0
        for (d in MergeDir.values()) { val n = g.move(d, 2048); if (n !== g) { g = n; break } }
        assertTrue(g.canUndo)
        val u = g.undo()
        assertEquals(g0.cells, u.cells)
        assertEquals(0, u.moves)
        assertFalse(u.canUndo)
    }

    @Test fun encodeDecodeRoundTrip() {
        var g = TileMergeGame.newGame(99)
        for (i in 0 until 10) g = g.move(MergeDir.values()[i % 4], 128)
        assertEquals(g, TileMergeGame.decode(g.encode()))
        assertNull(TileMergeGame.decode("garbage"))
    }

    @Test fun configsGrowWithTier() {
        val t = (0..4).map { TileMergeRules.configFor(it) }
        assertTrue(t.zipWithNext().all { (a, b) -> a.target < b.target && a.moveBudget < b.moveBudget })
    }
}
