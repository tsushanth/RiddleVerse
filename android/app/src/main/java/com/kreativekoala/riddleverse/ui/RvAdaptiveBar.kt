package com.kreativekoala.riddleverse.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Pages shorter than this (landscape phones) scroll as a whole instead of squeezing their board/controls. */
val RV_MIN_PAGE_HEIGHT: Dp = 460.dp

/** True when [heightDp] is too short for a stacked button bar / fixed chrome (landscape phones). */
fun rvIsShort(heightDp: Float): Boolean = heightDp < 420f

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
