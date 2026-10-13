package com.kreativekoala.riddleverse

import org.junit.Assert.*
import org.junit.Test

class PiecePackDropTest {
    private val square = PiecePackEngine.SHAPES[9] // 2x2
    private val dot = PiecePackEngine.SHAPES[0]

    @Test fun centredOnPointerAndLifted() {
        // cell 10, board at (0,0), lift 20: pointer (50, 80) -> piece top-left x=40,y=80-20-20=40 -> col 4,row 4
        val o = PiecePackEngine.dropOrigin(50f, 80f, square, 0f, 0f, 10f, 20f)
        assertArrayEquals(intArrayOf(4, 4), o)
    }

    @Test fun clampsToBoardEdges() {
        val far = PiecePackEngine.dropOrigin(-100f, -100f, square, 0f, 0f, 10f, 20f)
        assertArrayEquals(intArrayOf(0, 0), far)
        val near = PiecePackEngine.dropOrigin(1000f, 1000f, square, 0f, 0f, 10f, 20f)
        assertArrayEquals(intArrayOf(6, 6), near)
    }

    @Test fun respectsBoardOffset() {
        val o = PiecePackEngine.dropOrigin(105f, 55f, dot, 100f, 0f, 10f, 0f)
        assertArrayEquals(intArrayOf(5, 0), o)
    }

    @Test fun edgeDropAlwaysFitsOnEmptyBoard() {
        val b = IntArray(64)
        for (p in PiecePackEngine.SHAPES) for (x in -20..100 step 7) for (y in -20..140 step 7) {
            val o = PiecePackEngine.dropOrigin(x.toFloat(), y.toFloat(), p, 0f, 0f, 10f, 20f)
            assertTrue(PiecePackEngine.fits(b, p, o[0], o[1]))
        }
    }
}
