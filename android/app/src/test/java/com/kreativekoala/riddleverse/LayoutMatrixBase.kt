package com.kreativekoala.riddleverse

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.test.core.app.ApplicationProvider
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import org.junit.Before
import org.junit.Rule
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Device classes to render every game screen on. Use as `@Config(qualifiers = Devices.X)`. */
object Devices {
    const val SMALL_PHONE = "w320dp-h480dp-xhdpi"          // pair with font scale 2.0
    const val COMPACT_PHONE = "w360dp-h640dp-xhdpi"        // pair with font scale 1.3
    const val TYPICAL_PHONE = "w411dp-h891dp-xxhdpi"
    const val PHONE_LANDSCAPE = "w891dp-h411dp-land-xxhdpi"
    const val FOLDABLE_INNER = "w673dp-h841dp-xhdpi"
    const val TABLET_PORTRAIT = "w800dp-h1280dp-xhdpi"
    const val TABLET_LANDSCAPE = "w1280dp-h800dp-land-xhdpi"
}

/**
 * Base class for game-screen layout tests. Renders on the JVM (Robolectric), no device needed.
 * Rule: games must fit the viewport WITHOUT scrolling, so use [assertOnScreen] (never scroll
 * into view) for every primary control and the play area.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
abstract class LayoutMatrixBase {

    @get:Rule val compose = createComposeRule()

    @Before fun initFirebase() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        if (FirebaseApp.getApps(context).isEmpty()) {
            FirebaseApp.initializeApp(
                context,
                FirebaseOptions.Builder().setApplicationId("1:1:android:1").setApiKey("test").setProjectId("test").build()
            )
        }
    }

    protected fun render(fontScale: Float = 1f, content: @Composable () -> Unit) {
        compose.setContent {
            val base = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(base.density, fontScale)) {
                Box(Modifier.fillMaxSize()) { content() }
            }
        }
    }

    protected fun assertOnScreen(node: SemanticsNodeInteraction) {
        node.assertIsDisplayed()
    }
}
