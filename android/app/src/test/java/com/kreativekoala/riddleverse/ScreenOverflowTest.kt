package com.kreativekoala.riddleverse

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density
import androidx.test.core.app.ApplicationProvider
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Guards against the "primary action renders below the viewport with no way to scroll"
 * class of bug (fixed in versionCode 170 for the Estimation and Price Ordering screens).
 *
 * Each screen is rendered on a small phone at 2x system font scale. Every key control must
 * either already be on screen or be reachable by scrolling.
 *
 * Runs on the JVM (Robolectric), no device needed. To add another screen: render it via
 * [renderOnSmallPhone] and call [assertReachable] on its primary controls.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, qualifiers = "w320dp-h568dp-xhdpi")
class ScreenOverflowTest {

    @get:Rule val compose = createComposeRule()

    @Before fun initFirebase() {
        // Screens build a UnifiedFeedbackManager, which reads the current Firebase user.
        val context = ApplicationProvider.getApplicationContext<Application>()
        if (FirebaseApp.getApps(context).isEmpty()) {
            FirebaseApp.initializeApp(
                context,
                FirebaseOptions.Builder()
                    .setApplicationId("1:1:android:1")
                    .setApiKey("test")
                    .setProjectId("test")
                    .build()
            )
        }
    }

    private fun renderOnSmallPhone(fontScale: Float, content: @androidx.compose.runtime.Composable () -> Unit) {
        compose.setContent {
            val base = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(base.density, fontScale)) {
                Box(Modifier.fillMaxSize()) { content() }
            }
        }
    }

    /** On screen already, or scrollable into view. Fails if it can be neither. */
    private fun assertReachable(node: SemanticsNodeInteraction) {
        val alreadyVisible = runCatching { node.assertIsDisplayed() }.isSuccess
        if (!alreadyVisible) node.performScrollTo().assertIsDisplayed()
    }

    // ---- Estimation (chart) screen -----------------------------------

    private fun estimationScreen(fontScale: Float) = renderOnSmallPhone(fontScale) {
        EstimationPuzzleScreen(
            timer = "1:00",
            dataPoints = listOf(
                ChartDataPoint(10.0, 0.2f, 0),
                ChartDataPoint(25.0, 0.5f, 1),
                ChartDataPoint(15.0, 0.3f, 2),
                ChartDataPoint(30.0, 0.6f, 3),
            ),
            correctSum = 80.0,
            onSubmitAnswer = {},
            fetchNextPuzzle = {},
            onBack = {}
        )
    }

    @Test fun estimation_submitReachable_defaultFont() {
        estimationScreen(fontScale = 1f)
        assertReachable(compose.onNodeWithText("SUBMIT", substring = true))
    }

    @Test fun estimation_submitReachable_largeFont() {
        estimationScreen(fontScale = 2f)
        assertReachable(compose.onNodeWithText("SUBMIT", substring = true))
    }

    // ---- Price ordering screen ---------------------------------------

    private fun priceItems() = listOf(
        PriceItem(1, "Headphones", "🎧", 100.0, 25, PriceItem.calculateFinalPrice(100.0, 25)),
        PriceItem(2, "Backpack", "🎒", 80.0, 10, PriceItem.calculateFinalPrice(80.0, 10)),
        PriceItem(3, "Sneakers", "👟", 120.0, null, PriceItem.calculateFinalPrice(120.0, null)),
        PriceItem(4, "T-Shirt", "👕", 60.0, 20, PriceItem.calculateFinalPrice(60.0, 20)),
    )

    private fun priceOrderingScreen(fontScale: Float) = renderOnSmallPhone(fontScale) {
        PriceOrderingPuzzleScreen(
            round = "Round 1",
            items = priceItems(),
            onSubmitAnswer = {},
            fetchNextPuzzle = {},
            onBack = {}
        )
    }

    @Test fun priceOrdering_lastItemAndProgressReachable_defaultFont() {
        priceOrderingScreen(fontScale = 1f)
        assertReachable(compose.onNodeWithText("👕", substring = true))
        assertReachable(compose.onNodeWithText("items selected", substring = true))
    }

    @Test fun priceOrdering_lastItemAndProgressReachable_largeFont() {
        priceOrderingScreen(fontScale = 2f)
        assertReachable(compose.onNodeWithText("👕", substring = true))
        assertReachable(compose.onNodeWithText("items selected", substring = true))
    }
}
