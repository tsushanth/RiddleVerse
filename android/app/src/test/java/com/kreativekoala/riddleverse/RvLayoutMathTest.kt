package com.kreativekoala.riddleverse

import com.kreativekoala.riddleverse.ui.BoardFit
import com.kreativekoala.riddleverse.ui.MAX_CONTENT_WIDTH_DP
import com.kreativekoala.riddleverse.ui.rvContentWidthDp
import org.junit.Assert.*
import org.junit.Test

/** Size sweeps for the pure layout rules, from the smallest realistic phone to a tablet. */
class RvLayoutMathTest {
    private val sizes = listOf(
        320 to 568, 360 to 640, 393 to 852, 411 to 891, 427 to 952, 330 to 700, // 330: "largest display size"
        568 to 320, 852 to 393, 600 to 960, 800 to 1280, 1280 to 800            // landscape and tablets
    )
    private val barsV = listOf(24f + 24f, 48f + 24f) // gesture / 3-button bottom + status bar

    @Test fun contentWidthIsCappedAndNeverNegative() {
        for ((w, _) in sizes) {
            val c = rvContentWidthDp(w.toFloat())
            assertTrue(c <= MAX_CONTENT_WIDTH_DP && c <= w && c >= 0f)
        }
        assertEquals(0f, rvContentWidthDp(-5f), 0f)
        assertEquals(320f, rvContentWidthDp(320f), 0f)
    }

    @Test fun squareBoardNeverExceedsWidthAndFitsHeightWhenItCan() {
        val reserved = 52f + 56f + (52f + 8f) + 44f // grid header + controls + pad + gaps (9x9 sudoku)
        for ((w, h) in sizes) for (bars in barsV) {
            val availW = rvContentWidthDp(w.toFloat())
            val availH = h - bars
            val side = BoardFit.squareSide(availW, availH, reserved, hPad = 24f, minSide = 200f)
            assertTrue("$w x $h: side $side wider than ${availW - 24f}", side <= maxOf(availW - 24f, 200f))
            if (availH - reserved >= 200f) {
                assertTrue("$w x $h: board+controls $side+$reserved exceed $availH", side + reserved <= availH + 0.01f)
            }
        }
    }

    @Test fun tipBubblesAlwaysFitFourAcross() {
        for ((w, _) in sizes) {
            val usable = rvContentWidthDp(w.toFloat())
            val size = TipBubbleLayout.bubbleSizeDp(usable)
            assertTrue(size in TipBubbleLayout.MIN_SIZE_DP..TipBubbleLayout.MAX_SIZE_DP)
            val needed = 2 * TipBubbleLayout.SIDE_PADDING_DP + 4 * size + 3 * TipBubbleLayout.MIN_GAP_DP
            assertTrue("$w: four bubbles of $size need $needed", needed <= usable + 0.01f)
        }
        // the old fixed 120dp bubbles needed 520dp, wider than every phone
        assertTrue(TipBubbleLayout.bubbleSizeDp(427f) < 120f)
    }

    @Test fun safeSweepCellsFitOrScrollAtEveryWidth() {
        for ((w, _) in sizes) for (cols in listOf(6, 8, 10, 12)) {
            val avail = rvContentWidthDp(w.toFloat()) - 32f
            val cell = SweepLayout.cellDp(avail, cols)
            if (SweepLayout.fitsWidth(avail, cols)) assertTrue(cell * cols + SweepLayout.BOARD_PADDING_DP <= avail + 0.01f)
            else assertEquals(SweepLayout.SCROLL_CELL_DP, cell, 0f)
        }
    }
}
