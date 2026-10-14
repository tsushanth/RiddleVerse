package com.kreativekoala.riddleverse.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp

/** Pure rule: next font size to try when text of [current]sp does not fit, or null once [min] is reached. */
fun nextShrinkSize(current: Float, min: Float, factor: Float = 0.9f): Float? =
    if (current <= min + 0.01f) null else maxOf(min, current * factor)

/**
 * Text that wraps to at most [maxLines] lines and, if it still overflows, shrinks in 10% steps down to [minSize]
 * (never below it; then it ellipsises). For titles that must show every word on a 320dp phone.
 */
@Composable
fun AutoShrinkText(
    text: String,
    maxSize: TextUnit,
    minSize: TextUnit,
    modifier: Modifier = Modifier,
    maxLines: Int = 2,
    color: Color = Color.Unspecified,
    fontWeight: FontWeight? = null,
    textAlign: TextAlign? = null,
) {
    var size by remember(text, maxSize) { mutableStateOf(maxSize.value) }
    Text(
        text = text,
        modifier = modifier.fillMaxWidth(),
        fontSize = size.sp,
        lineHeight = (size * 1.15f).sp,
        maxLines = maxLines,
        softWrap = true,
        overflow = TextOverflow.Ellipsis,
        color = color,
        fontWeight = fontWeight,
        textAlign = textAlign,
        onTextLayout = { r ->
            if (r.hasVisualOverflow) nextShrinkSize(size, minSize.value)?.let { size = it }
        }
    )
}
