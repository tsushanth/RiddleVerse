package com.kreativekoala.riddleverse

import androidx.compose.ui.test.onNodeWithText
import org.junit.Test
import org.robolectric.annotation.Config

/**
 * Renders puzzle screens across the range of Android device classes (small phone through
 * tablet, portrait and landscape, foldable inner display, large font scales) and checks that
 * every key control is on screen WITHOUT scrolling (players dislike scrolling inside games).
 *
 * JVM only (Robolectric), no device needed. Text metrics here are slightly optimistic compared
 * to a real device, so a pass is a guard, not proof. To cover another screen: add a
 * `screen(...)`/`check(...)` pair below and one test per device row using [Devices].
 *
 * The app declares no orientation lock and no resize restrictions, and targets API 36 (which
 * ignores orientation locks on large screens), so landscape and tablet sizes are real.
 */
class DeviceMatrixTest : LayoutMatrixBase() {

    // ---- Estimation (chart) --------------------------------------------

    private fun estimation(fontScale: Float) {
        render(fontScale) {
            EstimationPuzzleScreen(
                timer = "1:00",
                dataPoints = listOf(
                    ChartDataPoint(10.0, 0.2f, 0), ChartDataPoint(25.0, 0.5f, 1),
                    ChartDataPoint(15.0, 0.3f, 2), ChartDataPoint(30.0, 0.6f, 3),
                ),
                correctSum = 80.0, onSubmitAnswer = {}, fetchNextPuzzle = {}, onBack = {}
            )
        }
        assertOnScreen(compose.onNodeWithText("SUBMIT", substring = true))
    }

    @Test @Config(qualifiers = Devices.SMALL_PHONE) fun estimation_smallPhone_2xFont() = estimation(2f)
    @Test @Config(qualifiers = Devices.COMPACT_PHONE) fun estimation_compactPhone_1_3xFont() = estimation(1.3f)
    @Test @Config(qualifiers = Devices.TYPICAL_PHONE) fun estimation_typicalPhone() = estimation(1f)
    @Test @Config(qualifiers = Devices.PHONE_LANDSCAPE) fun estimation_phoneLandscape() = estimation(1f)
    @Test @Config(qualifiers = Devices.FOLDABLE_INNER) fun estimation_foldableInner() = estimation(1f)
    @Test @Config(qualifiers = Devices.TABLET_PORTRAIT) fun estimation_tabletPortrait() = estimation(1f)
    @Test @Config(qualifiers = Devices.TABLET_LANDSCAPE) fun estimation_tabletLandscape() = estimation(1f)

    // ---- Price ordering ------------------------------------------------

    private fun priceOrdering(fontScale: Float) {
        render(fontScale) {
            PriceOrderingPuzzleScreen(
                round = "Round 1",
                items = listOf(
                    PriceItem(1, "Headphones", "🎧", 100.0, 25, PriceItem.calculateFinalPrice(100.0, 25)),
                    PriceItem(2, "Backpack", "🎒", 80.0, 10, PriceItem.calculateFinalPrice(80.0, 10)),
                    PriceItem(3, "Sneakers", "👟", 120.0, null, PriceItem.calculateFinalPrice(120.0, null)),
                    PriceItem(4, "T-Shirt", "👕", 60.0, 20, PriceItem.calculateFinalPrice(60.0, 20)),
                ),
                onSubmitAnswer = {}, fetchNextPuzzle = {}, onBack = {}
            )
        }
        assertOnScreen(compose.onNodeWithText("👕", substring = true))
        assertOnScreen(compose.onNodeWithText("items selected", substring = true))
    }

    @Test @Config(qualifiers = Devices.SMALL_PHONE) fun priceOrdering_smallPhone_2xFont() = priceOrdering(2f)
    @Test @Config(qualifiers = Devices.COMPACT_PHONE) fun priceOrdering_compactPhone_1_3xFont() = priceOrdering(1.3f)
    @Test @Config(qualifiers = Devices.TYPICAL_PHONE) fun priceOrdering_typicalPhone() = priceOrdering(1f)
    @Test @Config(qualifiers = Devices.PHONE_LANDSCAPE) fun priceOrdering_phoneLandscape() = priceOrdering(1f)
    @Test @Config(qualifiers = Devices.FOLDABLE_INNER) fun priceOrdering_foldableInner() = priceOrdering(1f)
    @Test @Config(qualifiers = Devices.TABLET_PORTRAIT) fun priceOrdering_tabletPortrait() = priceOrdering(1f)
    @Test @Config(qualifiers = Devices.TABLET_LANDSCAPE) fun priceOrdering_tabletLandscape() = priceOrdering(1f)
}
