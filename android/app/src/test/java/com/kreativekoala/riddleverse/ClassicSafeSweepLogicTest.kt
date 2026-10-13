package com.kreativekoala.riddleverse

import org.junit.Assert.*
import org.junit.Test

class ClassicSafeSweepLogicTest {
    @Test fun firstTapNeverMineAndOpensArea() {
        for (tier in 0..4) for (seed in 0L until 40L) {
            val c = SafeSweepRules.configFor(tier)
            val start = (seed.toInt() * 7) % (c.rows * c.cols)
            val g = SafeSweepGame.newGame(tier, seed).tap(start)
            assertEquals(SweepStatus.PLAYING, g.status)
            assertTrue(g.isRevealed(start))
            assertFalse(g.isMine(start))
            assertEquals(0, g.adjacentMines(start))
            assertEquals(c.mines, (0 until g.size).count { g.isMine(it) })
            assertTrue(g.revealedCount > 1)
        }
    }

    @Test fun deterministicWithSeed() {
        val a = SafeSweepGame.newGame(2, 5).tap(0)
        val b = SafeSweepGame.newGame(2, 5).tap(0)
        assertEquals(a.encode(), b.encode())
    }

    @Test fun mineHitLoses() {
        val g = SafeSweepGame.withMines(3, 3, setOf(4)).tap(4)
        assertEquals(SweepStatus.LOST, g.status)
        assertEquals(4, g.explodedIndex)
    }

    @Test fun floodFillAndWin() {
        // single mine in corner of 3x3: tapping opposite corner opens everything but the mine
        val g = SafeSweepGame.withMines(3, 3, setOf(0)).tap(8)
        assertEquals(SweepStatus.WON, g.status)
        assertTrue(g.isFlagged(0))
        assertEquals(8, g.revealedCount)
    }

    @Test fun numbersCounted() {
        val g = SafeSweepGame.withMines(3, 3, setOf(0, 2))
        assertEquals(2, g.adjacentMines(1))
        assertEquals(2, g.adjacentMines(4))
    }

    @Test fun flagsBlockRevealAndCountMines() {
        var g = SafeSweepGame.withMines(3, 3, setOf(0))
        g = g.toggleFlag(0)
        assertEquals(0, g.minesLeft)
        assertSame(g, g.tap(0))
        g = g.toggleFlag(0)
        assertEquals(1, g.minesLeft)
    }

    @Test fun chordRequiresMatchingFlags() {
        // mine at 0; reveal 1 (shows 1); wrong flag count => chord does nothing
        var g = SafeSweepGame.withMines(3, 3, setOf(0)).tap(1)
        assertTrue(g.isRevealed(1))
        val before = g.encode()
        assertEquals(before, g.chord(1).encode())
        g = g.toggleFlag(0).chord(1)
        assertEquals(SweepStatus.WON, g.status)
    }

    @Test fun chordWithWrongFlagHitsMine() {
        var g = SafeSweepGame.withMines(3, 3, setOf(0, 8)).tap(1)
        // number at 1 is 1 (mine 0). Flag the wrong cell 3? 3 is neighbour of 1? neighbours: 0,2,3,4,5
        g = g.toggleFlag(3).chord(1)
        assertEquals(SweepStatus.LOST, g.status)
    }

    @Test fun encodeDecodeRoundTripAndNoActionsAfterEnd() {
        val g = SafeSweepGame.newGame(1, 3).tap(10).toggleFlag(0)
        assertEquals(g.encode(), SafeSweepGame.decode(g.encode())!!.encode())
        val lost = SafeSweepGame.withMines(3, 3, setOf(4)).tap(4)
        assertSame(lost, lost.tap(0))
        assertSame(lost, lost.toggleFlag(0))
        assertNull(SafeSweepGame.decode("nope"))
    }

    @Test fun smallAndMediumBoardsFitA360dpWidth() {
        val avail = 360f - 32f
        for (cols in listOf(8, 10)) {
            assertTrue(SweepLayout.fitsWidth(avail, cols))
            val cell = SweepLayout.cellDp(avail, cols)
            assertTrue("cell $cell", cell >= 30f && cell <= 64f)
            assertTrue(cell * cols + SweepLayout.BOARD_PADDING_DP <= avail + 0.01f)
        }
        assertTrue(SweepLayout.cellDp(avail, 8) >= 36f)
    }

    @Test fun largeBoardKeepsTouchSizedCellsAndScrolls() {
        val avail = 360f - 32f
        assertFalse(SweepLayout.fitsWidth(avail, 14))
        assertEquals(48f, SweepLayout.cellDp(avail, 14), 0.001f)
    }
}
