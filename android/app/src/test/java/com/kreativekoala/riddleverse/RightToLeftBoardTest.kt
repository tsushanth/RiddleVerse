package com.kreativekoala.riddleverse

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.LayoutDirection
import org.junit.Assert.assertTrue
import org.junit.Test
import org.robolectric.annotation.Config

/**
 * Right-to-left app languages (Arabic, Persian, Urdu, Hebrew) must not mirror puzzle boards: Sign Grid draws its signs
 * on a canvas in left-to-right coordinates, so mirrored cells put every sign between the wrong two cells.
 */
class RightToLeftBoardTest : LayoutMatrixBase() {

    private fun renderRtl(content: @Composable () -> Unit) =
        render { CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) { content() } }

    @Test @Config(qualifiers = Devices.TYPICAL_PHONE)
    fun gridBoardKeepsColumnOrder() {
        val spec = GridPuzzleFactory.build(GridGame.SIGN_GRID, "Medium", 7L)
        renderRtl { GridPuzzleContent(spec, "1/1", { _, _ -> }, {}) }
        fun left(column: Int) = compose.onNode(hasContentDescription("Row 1, column $column, ", substring = true))
            .fetchSemanticsNode().boundsInRoot.left
        for (c in 1 until spec.n) assertTrue("column $c must be left of column ${c + 1}", left(c) < left(c + 1))
    }

    @Test @Config(qualifiers = Devices.TYPICAL_PHONE)
    fun wordHiveShowsTypedWordLeftToRight() {
        val puzzle = WordHiveGenerator.generate(WordPuzzleTestSupport.lists, "Medium", 7L)!!
        renderRtl { WordHiveScreen(puzzle.toJson(7L, "Medium").toString(), "1/1", {}, { _, _ -> }) }
        val typed = listOf(puzzle.letters[0], puzzle.letters[1])
        typed.forEach { compose.onNodeWithContentDescription("Letter ${it.uppercaseChar()}").performScrollTo().performClick() }
        fun left(letter: Char) = compose.onNode(
            hasText(letter.uppercase()) and hasAnyAncestor(hasContentDescription("Current word", substring = true)),
            useUnmergedTree = true
        ).fetchSemanticsNode().boundsInRoot.left
        assertTrue("first typed letter must be on the left", left(typed[0]) < left(typed[1]))
    }
}
