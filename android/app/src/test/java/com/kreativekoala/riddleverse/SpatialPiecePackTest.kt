package com.kreativekoala.riddleverse

import org.junit.Assert.*
import org.junit.Test

class SpatialPiecePackTest {

    @Test fun rowAndColumnClearTogether() {
        val b = IntArray(64)
        // fill row 0 and col 0 except the shared corner (0,0)
        for (i in 1 until 8) { b[i] = 1; b[i * 8] = 1 }
        val dot = PiecePackEngine.SHAPES[0]
        assertEquals(2, PiecePackEngine.place(b, dot, 0, 0))
        assertTrue(b.all { it == 0 })
    }

    @Test fun fitsRespectsBoundsAndOverlap() {
        val b = IntArray(64)
        val bar5 = PiecePackEngine.SHAPES[7]
        assertTrue(PiecePackEngine.fits(b, bar5, 0, 3))
        assertFalse(PiecePackEngine.fits(b, bar5, 0, 4))
        b[2] = 3
        assertFalse(PiecePackEngine.fits(b, bar5, 0, 0))
    }

    @Test fun dealIsDeterministic() {
        val b = IntArray(64)
        assertArrayEquals(PiecePackEngine.deal(42, 3, b, 1), PiecePackEngine.deal(42, 3, b, 1))
    }

    @Test fun shapeIdsMatchIndices() {
        PiecePackEngine.SHAPES.forEachIndexed { i, s -> assertEquals(i, s.id); assertTrue(s.id + 1 < 26) }
    }

    @Test fun stuckWhenNothingFits() {
        val s = PiecePackState(1, "Hard")
        // fill board entirely but leave pattern with no clear: checkerboard
        for (i in 0 until 64) s.board[i] = if ((i / 8 + i % 8) % 2 == 0) 1 else 0
        // 3 hand pieces of 2x2 squares cannot fit on checkerboard
        s.hand = intArrayOf(9, 9, 9)
        // placing is impossible; evaluate via restore round trip (re-evaluates status)
        val t = PiecePackState(1, "Hard")
        t.restore(s.encode())
        assertEquals(PiecePackStatus.STUCK, t.status)
    }

    @Test fun undoRestoresState() {
        val s = PiecePackState(7, "Easy")
        val before = s.encode()
        val m = s.hint()!!
        assertTrue(s.tryPlace(m[0], m[1], m[2]))
        assertEquals(1, s.piecesPlaced)
        assertTrue(s.undoMove())
        assertEquals(before, s.encode())
    }

    /** The bot (same heuristic used for the Hint button) must clear the goal in budget for most seeds. */
    private fun winRate(d: String, seeds: Int): Double {
        var wins = 0
        for (seed in 1L..seeds) {
            val s = PiecePackState(seed, d)
            var guard = 0
            while (s.status == PiecePackStatus.PLAYING && guard++ < 200) {
                val m = s.hint() ?: break
                assertTrue(s.tryPlace(m[0], m[1], m[2]))
            }
            if (s.status == PiecePackStatus.WON) wins++
        }
        return wins.toDouble() / seeds
    }

    @Test fun puzzlesAreWinnableByHeuristicPlayer() {
        val e = winRate("Easy", 100); val m = winRate("Medium", 100); val h = winRate("Hard", 100)
        println("PiecePack bot win rates: easy=$e medium=$m hard=$h")
        assertTrue("easy $e", e >= 0.95)
        assertTrue("medium $m", m >= 0.85)
        assertTrue("hard $h", h >= 0.6)
    }
}
