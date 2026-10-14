package com.kreativekoala.riddleverse.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density

/**
 * Caps the system font scale for [content] at [max]. Use it for fixed-size tiles, chips and bars
 * whose box cannot grow (the label then wraps or ellipsises instead of overflowing); never for
 * reading text. Layout dp are unaffected.
 */
@Composable
fun CappedFontScale(max: Float, content: @Composable () -> Unit) {
    val d = LocalDensity.current
    if (d.fontScale > max) {
        CompositionLocalProvider(LocalDensity provides Density(d.density, max)) { content() }
    } else {
        content()
    }
}
