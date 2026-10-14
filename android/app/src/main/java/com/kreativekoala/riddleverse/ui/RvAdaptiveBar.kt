package com.kreativekoala.riddleverse.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** On short screens (rvIsShort, landscape phones) pages use at least this height scroll as a whole instead of squeezing their board/controls. */
val RV_MIN_PAGE_HEIGHT: Dp = 500.dp

/** True when [heightDp] is too short for a stacked button bar / fixed chrome (landscape phones). */
fun rvIsShort(heightDp: Float): Boolean = heightDp < 420f

/** Landscape phone: short AND clearly wider than tall. Swipe/drag games then use board-left / controls-right. */
fun rvSideBySide(widthDp: Float, heightDp: Float): Boolean = rvIsShort(heightDp) && widthDp > heightDp * 1.3f

/** Width of the right-hand info/controls pane in side-by-side layouts. */
val RV_SIDE_PANE_WIDTH: Dp = 240.dp

/** Pure: side of the largest square board that fits left of the pane, above [controlsH] of controls. */
fun rvLandscapeBoardSide(totalW: Float, totalH: Float, paneW: Float = 240f, controlsH: Float = 0f, gap: Float = 12f, minSide: Float = 120f): Float =
    minOf(totalW - paneW - gap, totalH - controlsH).coerceAtLeast(minSide)

/**
 * Button bar that stacks vertically on normal screens and becomes one row of equal-width buttons when the
 * screen is short (landscape), so it no longer covers most of the page.
 */
@Composable
fun AdaptiveButtonBar(horizontal: Boolean, spacing: Dp = 12.dp, content: @Composable () -> Unit) {
    if (!horizontal) {
        Column(verticalArrangement = Arrangement.spacedBy(spacing)) { content() }
    } else {
        Layout(content = content) { measurables, c ->
            val gap = spacing.roundToPx()
            val n = measurables.size.coerceAtLeast(1)
            val w = ((c.maxWidth - gap * (n - 1)) / n).coerceAtLeast(0)
            val placeables = measurables.map { it.measure(Constraints(minWidth = w, maxWidth = w, minHeight = 0, maxHeight = c.maxHeight)) }
            val h = placeables.maxOfOrNull { it.height } ?: 0
            layout(c.maxWidth, h) {
                placeables.forEachIndexed { i, p -> p.placeRelative(i * (w + gap), 0) }
            }
        }
    }
}
