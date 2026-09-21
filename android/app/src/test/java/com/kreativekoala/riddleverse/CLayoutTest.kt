package com.kreativekoala.riddleverse

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.*
import androidx.compose.ui.unit.dp
import org.junit.Test
import org.robolectric.annotation.Config

class CLayoutTest : LayoutMatrixBase() {

    companion object {
        const val PINBALL_JSON = """{"matrix":[[0,1,0,0,0],[0,0,0,2,0],[0,0,0,0,0],[1,0,0,0,0],[0,0,2,0,0]],"matrixSize":5,"startPosition":[0,0],"startDirection":{"dr":1,"dc":0,"name":"DOWN"},"memoryTime":3000}"""
    }

    // ---- Pinball (both variants) ---------------------------------------

    /** Memorize phase then guessing phase: board, HUD, and every edge target fully on screen. */
    private fun pinballChecks() {
        assertOnScreen(compose.onNodeWithTag("pinball_board"))
        assertOnScreen(compose.onNodeWithText("Memorize", substring = true))
        assertOnScreen(compose.onNodeWithText("1:30", substring = true))
        compose.mainClock.advanceTimeBy(8000)
        compose.waitForIdle()
        assertOnScreen(compose.onNodeWithTag("pinball_board"))
        assertOnScreen(compose.onNodeWithText("Select where", substring = true))
        val edges = compose.onAllNodesWithTag("pinball_edge")
        val n = edges.fetchSemanticsNodes().size
        check(n > 0) { "no edge targets" }
        for (i in 0 until n) {
            assertOnScreen(edges[i])
            edges[i].assertWidthIsAtLeast(40.dp)
        }
    }

    private fun pinball(fontScale: Float) {
        render(fontScale) { PinballDeflectorPuzzleScreen("Medium", "1:30", 3, "1/5", PINBALL_JSON, {}, {}, {}) }
        pinballChecks()
    }

    private fun adaptivePinball(fontScale: Float) {
        render(fontScale) { AdaptivePinballDeflectorPuzzleScreen("Medium", "1:30", 3, "1/5", {}, {}, {}) }
        assertOnScreen(compose.onNodeWithTag("pinball_board"))
        assertOnScreen(compose.onNodeWithText("Memorize", substring = true))
        compose.mainClock.advanceTimeBy(10000)
        compose.waitForIdle()
        assertOnScreen(compose.onNodeWithTag("pinball_board"))
        val edges = compose.onAllNodesWithTag("pinball_edge")
        val n = edges.fetchSemanticsNodes().size
        check(n > 0) { "no edge targets" }
        for (i in 0 until n) assertOnScreen(edges[i])
    }

    @Test @Config(qualifiers = Devices.SMALL_PHONE) fun pinball_smallPhone_2xFont() = pinball(2f)
    @Test @Config(qualifiers = Devices.COMPACT_PHONE) fun pinball_compactPhone_1_3xFont() = pinball(1.3f)
    @Test @Config(qualifiers = Devices.TYPICAL_PHONE) fun pinball_typicalPhone() = pinball(1f)
    @Test @Config(qualifiers = Devices.PHONE_LANDSCAPE) fun pinball_phoneLandscape() = pinball(1f)
    @Test @Config(qualifiers = Devices.FOLDABLE_INNER) fun pinball_foldableInner() = pinball(1f)
    @Test @Config(qualifiers = Devices.TABLET_PORTRAIT) fun pinball_tabletPortrait() = pinball(1f)
    @Test @Config(qualifiers = Devices.TABLET_LANDSCAPE) fun pinball_tabletLandscape() = pinball(1f)

    @Test @Config(qualifiers = Devices.SMALL_PHONE) fun adaptivePinball_smallPhone_2xFont() = adaptivePinball(2f)
    @Test @Config(qualifiers = Devices.COMPACT_PHONE) fun adaptivePinball_compactPhone_1_3xFont() = adaptivePinball(1.3f)
    @Test @Config(qualifiers = Devices.TYPICAL_PHONE) fun adaptivePinball_typicalPhone() = adaptivePinball(1f)
    @Test @Config(qualifiers = Devices.PHONE_LANDSCAPE) fun adaptivePinball_phoneLandscape() = adaptivePinball(1f)
    @Test @Config(qualifiers = Devices.FOLDABLE_INNER) fun adaptivePinball_foldableInner() = adaptivePinball(1f)
    @Test @Config(qualifiers = Devices.TABLET_PORTRAIT) fun adaptivePinball_tabletPortrait() = adaptivePinball(1f)
    @Test @Config(qualifiers = Devices.TABLET_LANDSCAPE) fun adaptivePinball_tabletLandscape() = adaptivePinball(1f)
}
