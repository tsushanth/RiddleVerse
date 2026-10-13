package com.kreativekoala.riddleverse

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onRoot
import org.junit.Test
import org.robolectric.annotation.Config

/** Layout matrix for the grid-logic games: controls + board fit the viewport on small and large phones. */
class GridLayoutTest : LayoutMatrixBase() {

    private fun check(game: GridGame, difficulty: String, fs: Float) {
        val spec = GridPuzzleFactory.build(game, difficulty, 9)
        render(fs) { GridPuzzleContent(spec, "1/5", { _, _ -> }, {}) }
        compose.waitForIdle()
        assertOnScreen(compose.onNodeWithContentDescription("Back"))
        assertOnScreen(compose.onNodeWithContentDescription("Rules"))
        assertOnScreen(compose.onNodeWithContentDescription("Undo"))
        assertOnScreen(compose.onNodeWithContentDescription("Row 1, column 1,", substring = true))
        val root = compose.onRoot().fetchSemanticsNode().boundsInRoot
        val clickable = compose.onAllNodesClickable()
        val bad = clickable.filter { n ->
            val b = n.boundsInRoot
            b.width > 0f && (b.left < root.left - 1f || b.top < root.top - 1f || b.right > root.right + 1f || b.bottom > root.bottom + 1f)
        }
        if (bad.isNotEmpty()) throw AssertionError("Off-screen clickable (root=$root): " + bad.joinToString { it.boundsInRoot.toString() })
    }

    /** Header (Back button) must sit below the status-bar inset, not under it. */
    @Test @Config(qualifiers = Devices.TYPICAL_PHONE) fun header_sits_below_status_bar_inset() {
        val spec = GridPuzzleFactory.build(GridGame.SUDOKU, "easy", 9)
        var view: android.view.View? = null
        render(1f) {
            view = androidx.compose.ui.platform.LocalView.current
            GridPuzzleContent(spec, "1/5", { _, _ -> }, {})
        }
        val insetPx = 120
        compose.runOnUiThread {
            val decor = view!!.rootView
            (view!!.context as? android.app.Activity)?.window?.let { androidx.core.view.WindowCompat.setDecorFitsSystemWindows(it, false) }
            val insets = androidx.core.view.WindowInsetsCompat.Builder()
                .setInsets(androidx.core.view.WindowInsetsCompat.Type.statusBars(), androidx.core.graphics.Insets.of(0, insetPx, 0, 0))
                .setVisible(androidx.core.view.WindowInsetsCompat.Type.statusBars(), true)
                .build()
            androidx.core.view.ViewCompat.dispatchApplyWindowInsets(decor, insets)
        }
        compose.waitForIdle()
        val top = compose.onNodeWithContentDescription("Back").fetchSemanticsNode().boundsInRoot.top
        if (top < insetPx - 1f) throw AssertionError("Back button top=$top is under the status bar inset ($insetPx px)")
    }

    private fun androidx.compose.ui.test.junit4.ComposeContentTestRule.onAllNodesClickable() =
        onAllNodes(hasClickAction()).fetchSemanticsNodes()

    @Test @Config(qualifiers = Devices.COMPACT_PHONE) fun sudoku9_compact() = check(GridGame.SUDOKU, "hard", 1.3f)
    @Test @Config(qualifiers = Devices.TYPICAL_PHONE) fun sudoku9_typical() = check(GridGame.SUDOKU, "hard", 1f)
    @Test @Config(qualifiers = Devices.SMALL_PHONE) fun sudoku9_small_2x() = check(GridGame.SUDOKU, "hard", 2f)
    @Test @Config(qualifiers = Devices.COMPACT_PHONE) fun sudoku4_compact() = check(GridGame.SUDOKU, "easy", 1.3f)
    @Test @Config(qualifiers = Devices.COMPACT_PHONE) fun binary10_compact() = check(GridGame.BINAIRO, "hard", 1.3f)
    @Test @Config(qualifiers = Devices.COMPACT_PHONE) fun cage6_compact() = check(GridGame.CAGE_MATH, "hard", 1.3f)
    @Test @Config(qualifiers = Devices.COMPACT_PHONE) fun sign6_compact() = check(GridGame.SIGN_GRID, "hard", 1.3f)
    @Test @Config(qualifiers = Devices.TABLET_PORTRAIT) fun sudoku9_tablet() = check(GridGame.SUDOKU, "hard", 1f)
    @Test @Config(qualifiers = Devices.TYPICAL_PHONE) fun cage5_typical() = check(GridGame.CAGE_MATH, "medium", 1f)
    @Test @Config(qualifiers = Devices.TYPICAL_PHONE) fun sign5_typical() = check(GridGame.SIGN_GRID, "medium", 1f)
}
