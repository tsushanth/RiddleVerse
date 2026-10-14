package com.kreativekoala.riddleverse

import com.kreativekoala.riddleverse.ui.nextShrinkSize
import org.junit.Assert.*
import org.junit.Test

/** Size sweep for the word-game and board sizing rules (dp, Float). Prints the real numbers for the report. */
class WordFitTest {
    // width x height of the window, then status bar (24) + bottom bar (gesture 24 / 3-button 48)
    private val phones = listOf(320 to 568, 360 to 640, 393 to 852, 411 to 891, 427 to 952)
    private val bottoms = listOf(24f, 48f)
    private fun viewport(h: Int, bottom: Float) = h - 24f - bottom - 52f - 8f // scaffold header 52 + trailing 8
    private val guessFixed = 88f

    @Test fun wordGuessKeysAreAtLeast40AndEverythingFitsWhereItCan() {
        val report = StringBuilder()
        for ((w, h) in phones) for (b in bottoms) {
            val vp = viewport(h, b)
            val s = WordFit.guessLayout(w.toFloat(), vp, 6, 5, 6f, guessFixed)
            report.append("WordGuess ${w}x$h bottom=${b.toInt()}: viewport=${"%.0f".format(vp)} tile=${"%.1f".format(s.tile)} key=${"%.1f".format(s.key)} scroll=${s.needsScroll}\n")
            assertTrue("key >= 40 at ${w}x$h", s.key >= 40f)
            assertTrue("tile >= 32 at ${w}x$h", s.tile >= 32f)
            assertTrue("tile within width", 5 * s.tile + 4 * 6f + 16f <= w + 0.01f)
            if (!s.needsScroll) {
                val used = 6 * s.tile + 5 * 6f + 3 * s.key + guessFixed
                assertTrue("${w}x$h: $used > $vp", used <= vp + 0.5f)
            }
        }
        println(report)
    }

    @Test fun wordGuessNeverNeedsScrollOn360x640AndUp() {
        for ((w, h) in phones.filter { it.first >= 360 }) for (b in bottoms) {
            assertFalse("${w}x$h bottom $b", WordFit.guessLayout(w.toFloat(), viewport(h, b), 6, 5, 6f, guessFixed).needsScroll)
        }
    }

    @Test fun wordGuessSmallestPhoneKeepsKeysAndAcceptsScrollOnlyAsLastResort() {
        val gesture = WordFit.guessLayout(320f, viewport(568, 24f), 6, 5, 6f, guessFixed)
        assertFalse(gesture.needsScroll)           // 320x568 with the gesture bar fits without scrolling
        val threeButton = WordFit.guessLayout(320f, viewport(568, 48f), 6, 5, 6f, guessFixed)
        assertTrue(threeButton.key >= 40f && threeButton.tile >= 32f)
    }

    @Test fun keyHeightAndFitEachStayInRange() {
        for (h in 200..1000 step 25) {
            val k = WordFit.keyHeight(h.toFloat(), 330f)
            assertTrue(k in WordFit.KEY_MIN..WordFit.KEY_MAX)
            val p = WordFit.fitEach(h.toFloat(), 270f, 8, 6f, 24f, 36f)
            assertTrue(p in 24f..36f)
            val sc = WordFit.boardScale(h.toFloat(), 292f)
            assertTrue(sc in 0.7f..1f)
        }
        // roomy screen: full size
        assertEquals(54f, WordFit.keyHeight(900f, 330f), 0f)
        assertEquals(1f, WordFit.boardScale(500f, 292f), 0f)
    }

    @Test fun safeSweepCellsFitWidthAndHeightAboveControls() {
        for ((w, h) in phones) for (b in bottoms) {
            // chrome above the board ~ header 48 + subtitle 2 lines + stats + chip + gaps ~ 208
            val availH = h - 24f - b - 208f
            val availW = w - 32f
            for ((rows, cols) in listOf(8 to 8, 10 to 10, 12 to 14)) {
                val c = SweepLayout.cellDp(availW, availH, cols, rows)
                if (c * cols + SweepLayout.BOARD_PADDING_DP <= availW + 0.01f && c * rows + SweepLayout.BOARD_PADDING_DP > availH + 0.01f) {
                    // height does not fit: only allowed when the board then scrolls vertically at the width-fit size
                    assertTrue("${w}x$h ${rows}x$cols cell=$c", c >= SweepLayout.MIN_FIT_DP)
                }
                println("SafeSweep ${w}x$h bottom=${b.toInt()} ${rows}x$cols: cell=${"%.1f".format(c)} boardH=${"%.0f".format(c * rows + 8)} availH=${"%.0f".format(availH)}")
            }
        }
        // 8x8 on the smallest phone with a gesture bar must fit entirely
        val c = SweepLayout.cellDp(288f, 568f - 24f - 24f - 208f, 8, 8)
        assertTrue(c * 8 + 8 <= 568f - 24f - 24f - 208f + 0.01f)
    }

    @Test fun autoShrinkStepsDownToMinimumThenStops() {
        var s = 36f
        var steps = 0
        while (true) { s = nextShrinkSize(s, 22f) ?: break; steps++ ; assertTrue(s >= 22f) }
        assertEquals(22f, s, 0.01f)
        assertTrue(steps in 3..8)
        assertNull(nextShrinkSize(22f, 22f))
    }
}

class WordFitSmallPhoneTest {
    private fun vp(h: Int, bottom: Float) = h - 24f - bottom - 52f - 8f

    @Test fun balloonsBunchShrinksBeforeKeysAndTheyFit() {
        for (bottom in listOf(24f, 48f)) {
            val v = vp(568, bottom)
            val (bunch, key) = WordFit.bunchAndKey(v, 240f)
            println("Balloons 320x568 bottom=${bottom.toInt()}: viewport=$v bunch=${"%.0f".format(bunch)} key=${"%.0f".format(key)}")
            assertTrue(key >= 40f && bunch >= 70f)
            assertTrue(240f + bunch + 3 * key <= v + 0.5f)
        }
        val (b, k) = WordFit.bunchAndKey(vp(952, 24f), 240f)
        assertEquals(130f, b, 0f); assertEquals(54f, k, 0f)
    }

    @Test fun ladderKeysAtSmallestPhone() {
        for (bottom in listOf(24f, 48f)) {
            val k = WordFit.keyHeight(vp(568, bottom), 330f)
            println("Ladder 320x568 bottom=${bottom.toInt()}: key=${"%.0f".format(k)}")
            assertTrue(k >= 40f)
        }
    }

    @Test fun hiveBoardScaleAtSmallestPhone() {
        for (bottom in listOf(24f, 48f)) {
            val v = vp(568, bottom)
            val s = WordFit.boardScale(v - (300f - 36f), 222f)
            println("Hive 320x568 bottom=${bottom.toInt()}: scale=${"%.2f".format(s)} hex=${"%.0f".format(70 * s)}dp board=${"%.0f".format(222 * s)}dp total=${"%.0f".format(264 + 222 * s)} of $v")
            assertTrue(70 * s >= 48f) // hex taps stay >= 48dp
        }
    }
}

class PinnedLayoutTest {
    private fun vp(h: Int, bottom: Float) = h - 24f - bottom - 52f - 8f

    @Test fun pinnedKeysNeverBelow36AndLandscapeGetsSideBySide() {
        for (bottom in listOf(24f, 48f)) {
            val v = vp(568, bottom)
            val ladderKey = ((v - 230f - 56f) / 3f).coerceIn(36f, 54f)
            val (bunch, balloonKey) = balloonSizes(v, false)
            println("Pinned 320x568 bottom=${bottom.toInt()}: ladderKey=${"%.0f".format(ladderKey)} balloonKey=${"%.0f".format(balloonKey)} bunch=${"%.0f".format(bunch)}")
            assertTrue(ladderKey >= 36f && balloonKey >= 36f && bunch >= 70f)
        }
        // landscape 760x333 usable (side-by-side): viewport = 333 - 52 - 8
        val land = 333f - 52f - 8f
        val g = guessFit(760f - 24f, land, true)
        println("Landscape 760x333: guess tile=${"%.0f".format(g.tile)} key=${"%.0f".format(g.key)} scroll=${g.needsScroll}")
        assertTrue(g.key >= 36f && g.tile >= 32f)
        val (bunchL, keyL) = balloonSizes(land, true)
        assertTrue(keyL >= 36f && bunchL >= 70f)
    }

    @Test fun gridBoardSideBySideIsReadable() {
        // 9x9 sudoku board in landscape: left half of 760dp, height 333 - header 52 - 16
        val side = com.kreativekoala.riddleverse.ui.BoardFit.squareSide(380f, 333f, 68f, 12f, 120f)
        println("Landscape Sudoku board side=${"%.0f".format(side)} cell=${"%.1f".format(side / 9)}")
        assertTrue(side >= 200f && side / 9 >= 22f)
        assertTrue(side + 68f <= 333f + 0.5f)
    }

    @Test fun shortScreenRule() {
        assertTrue(com.kreativekoala.riddleverse.ui.rvIsShort(333f))
        assertFalse(com.kreativekoala.riddleverse.ui.rvIsShort(496f)) // 320x568 portrait stays on the normal path
    }
}
