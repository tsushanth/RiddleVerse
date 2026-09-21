package com.kreativekoala.riddleverse.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFF9C86FF),
    onPrimary = RvInk,
    secondary = RvSun,
    tertiary = RvMint,
    background = RvNight,
    surface = Color(0xFF2A2650),
    onBackground = Color.White,
    onSurface = Color.White
)

private val LightColorScheme = lightColorScheme(
    primary = RvViolet,
    onPrimary = Color.White,
    secondary = RvSun,
    onSecondary = RvInk,
    tertiary = RvMint,
    onTertiary = Color.White,
    background = RvCanvas,
    onBackground = RvInk,
    surface = RvCanvas,
    onSurface = RvInk,
    surfaceVariant = RvSurface,
    onSurfaceVariant = RvInkSoft,
    outline = RvOutline,
    error = RvCoral
)

@Composable
fun RiddleVerseTheme(
    // Light-only: every screen is styled with the light Rv tokens. Pass true only once a dark palette exists.
    darkTheme: Boolean = false,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = remember(darkTheme, dynamicColor) {
        when {
            dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
                // We'll handle this case separately since we need context
                null
            }
            darkTheme -> DarkColorScheme
            else -> LightColorScheme
        }
    }

    val finalColorScheme = colorScheme ?: run {
        // Only get context when we actually need dynamic colors
        if (dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        } else {
            if (darkTheme) DarkColorScheme else LightColorScheme
        }
    }

    MaterialTheme(
        colorScheme = finalColorScheme,
        typography = Typography,
        shapes = RvShapes,
        content = content
    )
}