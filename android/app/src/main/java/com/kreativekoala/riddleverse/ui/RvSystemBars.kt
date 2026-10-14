package com.kreativekoala.riddleverse.ui

import android.content.res.Configuration
import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.systemBarsIgnoringVisibility
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.waterfall
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.kreativekoala.riddleverse.ui.theme.RiddleVerseTheme
import com.kreativekoala.riddleverse.ui.theme.RvCanvas

/**
 * App-wide handling of window insets, so every screen is safe on every phone without each of the
 * ~110 older puzzle screens having to remember to pad itself.
 *
 * The app targets SDK 36, so edge-to-edge is enforced: content draws behind the status bar, the
 * gesture/3-button navigation bar and any display cutout unless something pads for them.
 * [RvScreenHost] sits at the root of every Compose activity and does three things:
 *
 *  1. Pads its content by the system bars (measured IGNORING visibility, so a status bar that is
 *     momentarily hidden, for example while a heads-up notification animates, does not make the
 *     header jump under it), the display cutout / waterfall and, optionally, the keyboard.
 *  2. Consumes those insets. Compose's `statusBarsPadding()`, `navigationBarsPadding()`,
 *     `imePadding()` and `safeDrawing` on screens further down become zero instead of double-padding,
 *     so screens that already pad themselves look exactly as before.
 *  3. Caps the content width at [MAX_CONTENT_WIDTH_DP] and centres it, so tablets, foldables and
 *     landscape phones do not stretch boards and buttons absurdly. Because older screens size
 *     things from `LocalConfiguration.screenWidthDp/HeightDp`, those two values are replaced for
 *     the content with the real usable area.
 */
const val MAX_CONTENT_WIDTH_DP = 720

/** Insets applied by [RvScreenHost]; bars are taken ignoring visibility. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun rvHostInsets(includeIme: Boolean): WindowInsets {
    val bars = WindowInsets.systemBarsIgnoringVisibility.union(WindowInsets.displayCutout).union(WindowInsets.waterfall)
    return if (includeIme) bars.union(WindowInsets.ime) else bars
}

@Composable
fun RvScreenHost(
    modifier: Modifier = Modifier,
    background: Color = RvCanvas,
    includeIme: Boolean = true,
    maxContentWidth: Dp = MAX_CONTENT_WIDTH_DP.dp,
    content: @Composable () -> Unit
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(background)
            .windowInsetsPadding(rvHostInsets(includeIme)),
        contentAlignment = Alignment.TopCenter
    ) {
        BoxWithConstraints(
            modifier = Modifier.fillMaxHeight().widthIn(max = maxContentWidth).fillMaxWidth(),
            propagateMinConstraints = true
        ) {
            val base = LocalConfiguration.current
            val w = maxWidth.value.toInt()
            val h = maxHeight.value.toInt()
            val adjusted = remember(base, w, h) {
                Configuration(base).apply {
                    screenWidthDp = w
                    screenHeightDp = h
                }
            }
            CompositionLocalProvider(LocalConfiguration provides adjusted) { content() }
        }
    }
}

/** Theme + [RvScreenHost]: what every Compose activity passes to `setContent`. */
@Composable
fun RvActivityTheme(includeIme: Boolean = true, content: @Composable () -> Unit) {
    RiddleVerseTheme { RvScreenHost(includeIme = includeIme, content = content) }
}

/** Pure width rule used by [RvScreenHost]: the content width for a window of [availableWidthDp]. */
fun rvContentWidthDp(availableWidthDp: Float, maxWidthDp: Float = MAX_CONTENT_WIDTH_DP.toFloat()): Float =
    availableWidthDp.coerceAtLeast(0f).coerceAtMost(maxWidthDp)

/**
 * For XML-layout activities: pads [this] (once, on top of the padding it already has) by the system
 * bars, display cutout and keyboard. Hard-coded "40dp so it clears the status bar" spacers are not needed.
 */
fun View.applyRvSystemBarPadding(includeIme: Boolean = true) {
    val left = paddingLeft
    val top = paddingTop
    val right = paddingRight
    val bottom = paddingBottom
    ViewCompat.setOnApplyWindowInsetsListener(this) { v, insets ->
        var types = WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
        if (includeIme) types = types or WindowInsetsCompat.Type.ime()
        val i = insets.getInsets(types)
        // systemBars() can report 0 for a hidden bar; take the larger of that and the stable bars.
        val stable = insets.getInsetsIgnoringVisibility(WindowInsetsCompat.Type.systemBars())
        v.setPadding(
            left + maxOf(i.left, stable.left),
            top + maxOf(i.top, stable.top),
            right + maxOf(i.right, stable.right),
            bottom + maxOf(i.bottom, stable.bottom)
        )
        WindowInsetsCompat.CONSUMED
    }
    ViewCompat.requestApplyInsets(this)
}

/** Call right after `setContentView` in an XML-layout activity: edge-to-edge with the content padded by the system bars. */
fun android.app.Activity.applyRvEdgeToEdge(includeIme: Boolean = true) {
    androidx.core.view.WindowCompat.setDecorFitsSystemWindows(window, false)
    findViewById<View>(android.R.id.content).applyRvSystemBarPadding(includeIme)
}
