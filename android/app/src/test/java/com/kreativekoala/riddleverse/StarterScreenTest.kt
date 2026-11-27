package com.kreativekoala.riddleverse

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertEquals
import org.junit.Test
import org.robolectric.annotation.Config

/** The starter puzzle on screen: fits a small phone, has no clock, hearts or extra buttons, and five taps solve it. */
class StarterScreenTest : LayoutMatrixBase() {

    private fun playThrough(fontScale: Float) {
        val spec = StarterPuzzle.spec(11)
        var result: Boolean? = null
        render(fontScale) { GridPuzzleContent(spec, "1/1", { won, _ -> result = won }, {}, starter = true) }
        compose.waitForIdle()

        compose.onNodeWithText("Tap a number to fill the highlighted square.", substring = true).assertExists()
        listOf("Undo", "Erase", "Notes").forEach { compose.onAllNodesWithContentDescription(it).assertCountEquals(0) }
        compose.onAllNodesWithContentDescription("lives left", substring = true).assertCountEquals(0)
        compose.onAllNodesWithContentDescription("Elapsed time", substring = true).assertCountEquals(0)
        assertOnScreen(compose.onNodeWithContentDescription("Back"))
        assertOnScreen(compose.onNodeWithContentDescription("Enter 4"))

        val root = compose.onRoot().fetchSemanticsNode().boundsInRoot
        val offScreen = compose.onAllNodes(hasClickAction()).fetchSemanticsNodes().map { it.boundsInRoot }.filter {
            it.width > 0f && (it.left < root.left - 1f || it.top < root.top - 1f || it.right > root.right + 1f || it.bottom > root.bottom + 1f)
        }
        assertEquals("off-screen controls (root=$root)", emptyList<Any>(), offScreen)

        // Empty cells are selected one after another, so only the number pad is tapped.
        (0 until spec.n * spec.n).filter { spec.given[it] == 0 }.forEach { cell ->
            compose.onNodeWithContentDescription("Enter ${spec.solution[cell]}").performClick()
            compose.waitForIdle()
        }
        compose.onNodeWithText("Continue").performClick()
        compose.waitForIdle()
        assertEquals(true, result)
    }

    @Test @Config(qualifiers = Devices.SMALL_PHONE) fun small_phone_large_text() = playThrough(1.3f)
    @Test @Config(qualifiers = Devices.TYPICAL_PHONE) fun typical_phone() = playThrough(1f)
    @Test @Config(qualifiers = Devices.PHONE_LANDSCAPE) fun landscape() = playThrough(1f)
}
