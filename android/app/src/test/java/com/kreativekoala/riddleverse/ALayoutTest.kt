package com.kreativekoala.riddleverse

import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import org.junit.Test
import org.robolectric.annotation.Config

/** Group A layout matrix: every primary control must be on screen without scrolling. */
class ALayoutTest : LayoutMatrixBase() {

    // ---- DualCard ---------------------------------------------------------
    private fun dualCard(fontScale: Float) {
        render(fontScale) {
            DualCardPuzzleScreen(
                difficulty = "Medium", timer = "1:30", hearts = 3, level = "1/5",
                onSubmitAnswer = {}, fetchNextPuzzle = {}, onBack = {}
            )
        }
        assertOnScreen(compose.onNodeWithTag("dualcard_top"))
        assertOnScreen(compose.onNodeWithTag("dualcard_bottom"))
        assertOnScreen(compose.onNodeWithText("NO"))
        assertOnScreen(compose.onNodeWithText("YES"))
        assertOnScreen(compose.onNodeWithText("1:30"))
        assertOnScreen(compose.onNodeWithText("Round 1 of", substring = true))
    }

    @Test @Config(qualifiers = Devices.SMALL_PHONE) fun dualCard_smallPhone() = dualCard(2f)
    @Test @Config(qualifiers = Devices.COMPACT_PHONE) fun dualCard_compactPhone() = dualCard(1.3f)
    @Test @Config(qualifiers = Devices.TYPICAL_PHONE) fun dualCard_typicalPhone() = dualCard(1f)
    @Test @Config(qualifiers = Devices.PHONE_LANDSCAPE) fun dualCard_phoneLandscape() = dualCard(1f)
    @Test @Config(qualifiers = Devices.FOLDABLE_INNER) fun dualCard_foldable() = dualCard(1f)
    @Test @Config(qualifiers = Devices.TABLET_PORTRAIT) fun dualCard_tabletPortrait() = dualCard(1f)
    @Test @Config(qualifiers = Devices.TABLET_LANDSCAPE) fun dualCard_tabletLandscape() = dualCard(1f)

    // ---- NumberSum (plain + adaptive) ----------------------------------------
    private fun assertNumberSum() {
        assertOnScreen(compose.onNodeWithTag("numsum_target"))
        assertOnScreen(compose.onNodeWithText("Score: 0"))
        val n = compose.onAllNodesWithTag("numtile").fetchSemanticsNodes().size
        assert(n >= 3) { "expected number tiles, found $n" }
        for (i in 0 until n) assertOnScreen(compose.onAllNodesWithTag("numtile")[i])
    }
    private fun numberSum(fontScale: Float) {
        render(fontScale) { NumberSumPuzzleScreen(difficulty = "Expert", onGameComplete = { _, _ -> }, onBack = {}) }
        assertNumberSum()
    }
    private fun adaptiveNumberSum(fontScale: Float) {
        render(fontScale) { AdaptiveNumberSumPuzzleScreen(initialDifficulty = "Expert", onGameComplete = { _, _ -> }, onBack = {}) }
        assertNumberSum()
    }
    @Test @Config(qualifiers = Devices.SMALL_PHONE) fun numberSum_smallPhone() = numberSum(2f)
    @Test @Config(qualifiers = Devices.COMPACT_PHONE) fun numberSum_compactPhone() = numberSum(1.3f)
    @Test @Config(qualifiers = Devices.TYPICAL_PHONE) fun numberSum_typicalPhone() = numberSum(1f)
    @Test @Config(qualifiers = Devices.PHONE_LANDSCAPE) fun numberSum_phoneLandscape() = numberSum(1f)
    @Test @Config(qualifiers = Devices.FOLDABLE_INNER) fun numberSum_foldable() = numberSum(1f)
    @Test @Config(qualifiers = Devices.TABLET_PORTRAIT) fun numberSum_tabletPortrait() = numberSum(1f)
    @Test @Config(qualifiers = Devices.TABLET_LANDSCAPE) fun numberSum_tabletLandscape() = numberSum(1f)
    @Test @Config(qualifiers = Devices.SMALL_PHONE) fun adaptiveNumberSum_smallPhone() = adaptiveNumberSum(2f)
    @Test @Config(qualifiers = Devices.COMPACT_PHONE) fun adaptiveNumberSum_compactPhone() = adaptiveNumberSum(1.3f)
    @Test @Config(qualifiers = Devices.TYPICAL_PHONE) fun adaptiveNumberSum_typicalPhone() = adaptiveNumberSum(1f)
    @Test @Config(qualifiers = Devices.PHONE_LANDSCAPE) fun adaptiveNumberSum_phoneLandscape() = adaptiveNumberSum(1f)
    @Test @Config(qualifiers = Devices.FOLDABLE_INNER) fun adaptiveNumberSum_foldable() = adaptiveNumberSum(1f)
    @Test @Config(qualifiers = Devices.TABLET_PORTRAIT) fun adaptiveNumberSum_tabletPortrait() = adaptiveNumberSum(1f)
    @Test @Config(qualifiers = Devices.TABLET_LANDSCAPE) fun adaptiveNumberSum_tabletLandscape() = adaptiveNumberSum(1f)

    // ---- Estimation (plain + adaptive) ---------------------------------------
    private fun assertEstimation() {
        assertOnScreen(compose.onNodeWithTag("estimation_chart"))
        assertOnScreen(compose.onNodeWithText("SUBMIT", substring = true))
        assertOnScreen(compose.onNodeWithTag("hud_score"))
        // Both the drag instruction and the current-value card legitimately say "estimate";
        // require at least one rather than exactly one.
        val estimateNodes = compose.onAllNodesWithText("estimate", substring = true, ignoreCase = true)
            .fetchSemanticsNodes()
        check(estimateNodes.isNotEmpty()) { "no on-screen text mentions 'estimate'" }
    }
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
        assertEstimation()
    }
    private fun adaptiveEstimation(fontScale: Float) {
        render(fontScale) {
            AdaptiveMathEstimationScreen(timer = "1:00", onSubmitAnswer = {}, fetchNextPuzzle = {}, onBack = {})
        }
        assertEstimation()
    }

    // ---- Discount price ordering ----------------------------------------------
    private fun discountOrdering(fontScale: Float) {
        render(fontScale) {
            PriceOrderingPuzzleScreen(
                round = "ROUND 1 of 5",
                items = listOf(
                    PriceItem(1, "Headphones", "🎧", 100.0, 25, PriceItem.calculateFinalPrice(100.0, 25)),
                    PriceItem(2, "Backpack", "🎒", 80.0, 10, PriceItem.calculateFinalPrice(80.0, 10)),
                    PriceItem(3, "Sneakers", "👟", 120.0, null, PriceItem.calculateFinalPrice(120.0, null)),
                    PriceItem(4, "T-Shirt", "👕", 60.0, 20, PriceItem.calculateFinalPrice(60.0, 20)),
                    PriceItem(5, "Book", "📚", 25.0, null, PriceItem.calculateFinalPrice(25.0, null)),
                    PriceItem(6, "Ball", "⚽", 36.0, 50, PriceItem.calculateFinalPrice(36.0, 50)),
                ),
                onSubmitAnswer = {}, fetchNextPuzzle = {}, onBack = {}
            )
        }
        val n = compose.onAllNodesWithTag("price_card").fetchSemanticsNodes().size
        assert(n == 6) { "expected 6 item cards, found $n" }
        for (i in 0 until n) assertOnScreen(compose.onAllNodesWithTag("price_card")[i])
        assertOnScreen(compose.onNodeWithText("items selected", substring = true))
        assertOnScreen(compose.onNodeWithTag("hud_score"))
        assertOnScreen(compose.onNodeWithText("TAP THE ITEMS", substring = true))
    }
    @Test @Config(qualifiers = Devices.SMALL_PHONE) fun estimation_smallPhone() = estimation(2f)
    @Test @Config(qualifiers = Devices.COMPACT_PHONE) fun estimation_compactPhone() = estimation(1.3f)
    @Test @Config(qualifiers = Devices.TYPICAL_PHONE) fun estimation_typicalPhone() = estimation(1f)
    @Test @Config(qualifiers = Devices.PHONE_LANDSCAPE) fun estimation_phoneLandscape() = estimation(1f)
    @Test @Config(qualifiers = Devices.FOLDABLE_INNER) fun estimation_foldable() = estimation(1f)
    @Test @Config(qualifiers = Devices.TABLET_PORTRAIT) fun estimation_tabletPortrait() = estimation(1f)
    @Test @Config(qualifiers = Devices.TABLET_LANDSCAPE) fun estimation_tabletLandscape() = estimation(1f)
    @Test @Config(qualifiers = Devices.SMALL_PHONE) fun adaptiveEstimation_smallPhone() = adaptiveEstimation(2f)
    @Test @Config(qualifiers = Devices.COMPACT_PHONE) fun adaptiveEstimation_compactPhone() = adaptiveEstimation(1.3f)
    @Test @Config(qualifiers = Devices.TYPICAL_PHONE) fun adaptiveEstimation_typicalPhone() = adaptiveEstimation(1f)
    @Test @Config(qualifiers = Devices.PHONE_LANDSCAPE) fun adaptiveEstimation_phoneLandscape() = adaptiveEstimation(1f)
    @Test @Config(qualifiers = Devices.FOLDABLE_INNER) fun adaptiveEstimation_foldable() = adaptiveEstimation(1f)
    @Test @Config(qualifiers = Devices.TABLET_PORTRAIT) fun adaptiveEstimation_tabletPortrait() = adaptiveEstimation(1f)
    @Test @Config(qualifiers = Devices.TABLET_LANDSCAPE) fun adaptiveEstimation_tabletLandscape() = adaptiveEstimation(1f)
    @Test @Config(qualifiers = Devices.SMALL_PHONE) fun discountOrdering_smallPhone() = discountOrdering(2f)
    @Test @Config(qualifiers = Devices.COMPACT_PHONE) fun discountOrdering_compactPhone() = discountOrdering(1.3f)
    @Test @Config(qualifiers = Devices.TYPICAL_PHONE) fun discountOrdering_typicalPhone() = discountOrdering(1f)
    @Test @Config(qualifiers = Devices.PHONE_LANDSCAPE) fun discountOrdering_phoneLandscape() = discountOrdering(1f)
    @Test @Config(qualifiers = Devices.FOLDABLE_INNER) fun discountOrdering_foldable() = discountOrdering(1f)
    @Test @Config(qualifiers = Devices.TABLET_PORTRAIT) fun discountOrdering_tabletPortrait() = discountOrdering(1f)
    @Test @Config(qualifiers = Devices.TABLET_LANDSCAPE) fun discountOrdering_tabletLandscape() = discountOrdering(1f)

    private fun assertAllOnScreen(tag: String, min: Int) {
        val n = compose.onAllNodesWithTag(tag).fetchSemanticsNodes().size
        assert(n >= min) { "expected >= $min nodes tagged $tag, found $n" }
        for (i in 0 until n) assertOnScreen(compose.onAllNodesWithTag(tag)[i])
    }

    // ---- Adaptive DualCard ------------------------------------------------------
    private fun adaptiveDualCard(fontScale: Float) {
        render(fontScale) { AdaptiveDualCardPuzzleScreen(onGameComplete = { _, _ -> }, onBack = {}) }
        assertOnScreen(compose.onNodeWithTag("dualcard_top"))
        assertOnScreen(compose.onNodeWithTag("dualcard_bottom"))
        assertOnScreen(compose.onNodeWithText("NO"))
        assertOnScreen(compose.onNodeWithText("YES"))
        assertOnScreen(compose.onNodeWithText("Round 1 of", substring = true))
        assertOnScreen(compose.onNodeWithText("Score: 0"))
    }

    // ---- Subtraction --------------------------------------------------------------
    private fun subtraction(fontScale: Float) {
        render(fontScale) {
            MathDifferenceGameScreen(
                timer = "1:00", level = "3/7", number1 = 734, number2 = 289,
                onSubmitAnswer = {}, fetchNextPuzzle = {}, onBack = {}
            )
        }
        assertOnScreen(compose.onNodeWithTag("subtraction_problem"))
        assertOnScreen(compose.onNodeWithTag("calc_display"))
        assertOnScreen(compose.onNodeWithTag("calc_submit"))
        assertAllOnScreen("calc_key", 11)
        assertOnScreen(compose.onNodeWithTag("hud_timer"))
        assertOnScreen(compose.onNodeWithTag("hud_score"))
    }

    // ---- Division -----------------------------------------------------------------
    private fun division(fontScale: Float) {
        render(fontScale) {
            DivisionPuzzleScreen(timer = "1:00", onSubmitAnswer = {}, fetchNextPuzzle = {}, onBack = {})
        }
        assertOnScreen(compose.onNodeWithTag("division_problem"))
        assertOnScreen(compose.onNodeWithTag("division_steppers"))
        assertOnScreen(compose.onNodeWithTag("division_total"))
        assertOnScreen(compose.onNodeWithText("SUBMIT"))
        val plus = compose.onAllNodesWithText("+").fetchSemanticsNodes().size
        assert(plus >= 3) { "expected stepper buttons, found $plus" }
        for (i in 0 until plus) assertOnScreen(compose.onAllNodesWithText("+")[i])
        for (i in 0 until plus) assertOnScreen(compose.onAllNodesWithText("−")[i])
    }

    // ---- Subscription (plain + adaptive) ---------------------------------------------
    private fun assertSubscription() {
        assertOnScreen(compose.onNodeWithTag("subscription_price"))
        assertOnScreen(compose.onNodeWithTag("subscription_problem"))
        assertOnScreen(compose.onNodeWithText("ANNUALLY IS:"))
        assertAllOnScreen("subscription_option", 4)
        assertOnScreen(compose.onNodeWithText("Score: 0"))
    }
    private fun subscription(fontScale: Float) {
        render(fontScale) {
            SubscriptionPuzzleScreen(
                timer = "1:23", payment = 21.49, frequency = "biweekly", purpose = "Gym membership",
                yearlyTotal = 558.74, options = listOf(447, 558, 670, 335),
                onSubmitAnswer = {}, fetchNextPuzzle = {}, onBack = {}
            )
        }
        assertSubscription()
        assertOnScreen(compose.onNodeWithTag("hud_timer"))
    }
    private fun adaptiveSubscription(fontScale: Float) {
        render(fontScale) {
            AdaptiveSubscriptionPuzzleScreen(timer = "1:23", onSubmitAnswer = {}, fetchNextPuzzle = {}, onBack = {})
        }
        assertSubscription()
    }

    // ---- ContextSwitch (plain: all three phases; adaptive: memory + interference) ------
    private val ctxJson = """{"memoryItems":["France","Germany","Italy","Spain","Netherlands","Belgium"],
        "interferenceTask":{"items":["Apple","Banana","Cherry","Grape","Peach"],"instruction":"Drag to alphabetize these words:"},
        "recognitionItems":["France","Germany","Italy","Spain","Netherlands","Belgium","Austria","Portugal","Greece","Poland"],
        "correctAnswers":["France","Germany","Italy","Spain","Netherlands","Belgium"]}"""
    private fun contextSwitch(fontScale: Float) {
        render(fontScale) {
            ContextSwitchPuzzleScreen(
                difficulty = "hard", timer = "2:00", hearts = 3, level = "1/5", puzzleData = ctxJson,
                onSubmitAnswer = {}, fetchNextPuzzle = {}, onBack = {}
            )
        }
        // memory
        assertOnScreen(compose.onNodeWithTag("hud_timer"))
        assertAllOnScreen("ctx_memory_item", 6)
        assertOnScreen(compose.onNodeWithTag("ctx_action"))
        compose.onNodeWithTag("ctx_action").performClick()
        // interference (list already in order, so Continue is enabled)
        compose.waitForIdle()
        assertAllOnScreen("ctx_word", 5)
        assertOnScreen(compose.onNodeWithTag("ctx_action"))
        compose.onNodeWithTag("ctx_action").performClick()
        // recognition
        compose.waitForIdle()
        assertAllOnScreen("ctx_item", 10)
        assertOnScreen(compose.onNodeWithTag("ctx_action"))
    }
    private fun adaptiveContextSwitch(fontScale: Float) {
        render(fontScale) {
            AdaptiveContextSwitchPuzzleScreen(
                initialDifficulty = "Medium", timer = "3:00", hearts = 3, level = "1/5",
                onSubmitAnswer = {}, fetchNextPuzzle = {}, onBack = {}
            )
        }
        assertOnScreen(compose.onNodeWithTag("ctx_action"))
        assertAllOnScreen("ctx_memory_item", 2)
        compose.onNodeWithTag("ctx_action").performClick()
        compose.waitForIdle()
        assertOnScreen(compose.onNodeWithTag("ctx_action"))
    }
    @Test @Config(qualifiers = Devices.SMALL_PHONE) fun adaptiveDualCard_smallPhone() = adaptiveDualCard(2f)
    @Test @Config(qualifiers = Devices.COMPACT_PHONE) fun adaptiveDualCard_compactPhone() = adaptiveDualCard(1.3f)
    @Test @Config(qualifiers = Devices.TYPICAL_PHONE) fun adaptiveDualCard_typicalPhone() = adaptiveDualCard(1f)
    @Test @Config(qualifiers = Devices.PHONE_LANDSCAPE) fun adaptiveDualCard_phoneLandscape() = adaptiveDualCard(1f)
    @Test @Config(qualifiers = Devices.FOLDABLE_INNER) fun adaptiveDualCard_foldable() = adaptiveDualCard(1f)
    @Test @Config(qualifiers = Devices.TABLET_PORTRAIT) fun adaptiveDualCard_tabletPortrait() = adaptiveDualCard(1f)
    @Test @Config(qualifiers = Devices.TABLET_LANDSCAPE) fun adaptiveDualCard_tabletLandscape() = adaptiveDualCard(1f)
    @Test @Config(qualifiers = Devices.SMALL_PHONE) fun subtraction_smallPhone() = subtraction(2f)
    @Test @Config(qualifiers = Devices.COMPACT_PHONE) fun subtraction_compactPhone() = subtraction(1.3f)
    @Test @Config(qualifiers = Devices.TYPICAL_PHONE) fun subtraction_typicalPhone() = subtraction(1f)
    @Test @Config(qualifiers = Devices.PHONE_LANDSCAPE) fun subtraction_phoneLandscape() = subtraction(1f)
    @Test @Config(qualifiers = Devices.FOLDABLE_INNER) fun subtraction_foldable() = subtraction(1f)
    @Test @Config(qualifiers = Devices.TABLET_PORTRAIT) fun subtraction_tabletPortrait() = subtraction(1f)
    @Test @Config(qualifiers = Devices.TABLET_LANDSCAPE) fun subtraction_tabletLandscape() = subtraction(1f)
    @Test @Config(qualifiers = Devices.SMALL_PHONE) fun division_smallPhone() = division(2f)
    @Test @Config(qualifiers = Devices.COMPACT_PHONE) fun division_compactPhone() = division(1.3f)
    @Test @Config(qualifiers = Devices.TYPICAL_PHONE) fun division_typicalPhone() = division(1f)
    @Test @Config(qualifiers = Devices.PHONE_LANDSCAPE) fun division_phoneLandscape() = division(1f)
    @Test @Config(qualifiers = Devices.FOLDABLE_INNER) fun division_foldable() = division(1f)
    @Test @Config(qualifiers = Devices.TABLET_PORTRAIT) fun division_tabletPortrait() = division(1f)
    @Test @Config(qualifiers = Devices.TABLET_LANDSCAPE) fun division_tabletLandscape() = division(1f)
    @Test @Config(qualifiers = Devices.SMALL_PHONE) fun subscription_smallPhone() = subscription(2f)
    @Test @Config(qualifiers = Devices.COMPACT_PHONE) fun subscription_compactPhone() = subscription(1.3f)
    @Test @Config(qualifiers = Devices.TYPICAL_PHONE) fun subscription_typicalPhone() = subscription(1f)
    @Test @Config(qualifiers = Devices.PHONE_LANDSCAPE) fun subscription_phoneLandscape() = subscription(1f)
    @Test @Config(qualifiers = Devices.FOLDABLE_INNER) fun subscription_foldable() = subscription(1f)
    @Test @Config(qualifiers = Devices.TABLET_PORTRAIT) fun subscription_tabletPortrait() = subscription(1f)
    @Test @Config(qualifiers = Devices.TABLET_LANDSCAPE) fun subscription_tabletLandscape() = subscription(1f)
    @Test @Config(qualifiers = Devices.SMALL_PHONE) fun adaptiveSubscription_smallPhone() = adaptiveSubscription(2f)
    @Test @Config(qualifiers = Devices.COMPACT_PHONE) fun adaptiveSubscription_compactPhone() = adaptiveSubscription(1.3f)
    @Test @Config(qualifiers = Devices.TYPICAL_PHONE) fun adaptiveSubscription_typicalPhone() = adaptiveSubscription(1f)
    @Test @Config(qualifiers = Devices.PHONE_LANDSCAPE) fun adaptiveSubscription_phoneLandscape() = adaptiveSubscription(1f)
    @Test @Config(qualifiers = Devices.FOLDABLE_INNER) fun adaptiveSubscription_foldable() = adaptiveSubscription(1f)
    @Test @Config(qualifiers = Devices.TABLET_PORTRAIT) fun adaptiveSubscription_tabletPortrait() = adaptiveSubscription(1f)
    @Test @Config(qualifiers = Devices.TABLET_LANDSCAPE) fun adaptiveSubscription_tabletLandscape() = adaptiveSubscription(1f)
    @Test @Config(qualifiers = Devices.SMALL_PHONE) fun contextSwitch_smallPhone() = contextSwitch(2f)
    @Test @Config(qualifiers = Devices.COMPACT_PHONE) fun contextSwitch_compactPhone() = contextSwitch(1.3f)
    @Test @Config(qualifiers = Devices.TYPICAL_PHONE) fun contextSwitch_typicalPhone() = contextSwitch(1f)
    @Test @Config(qualifiers = Devices.PHONE_LANDSCAPE) fun contextSwitch_phoneLandscape() = contextSwitch(1f)
    @Test @Config(qualifiers = Devices.FOLDABLE_INNER) fun contextSwitch_foldable() = contextSwitch(1f)
    @Test @Config(qualifiers = Devices.TABLET_PORTRAIT) fun contextSwitch_tabletPortrait() = contextSwitch(1f)
    @Test @Config(qualifiers = Devices.TABLET_LANDSCAPE) fun contextSwitch_tabletLandscape() = contextSwitch(1f)
    @Test @Config(qualifiers = Devices.SMALL_PHONE) fun adaptiveContextSwitch_smallPhone() = adaptiveContextSwitch(2f)
    @Test @Config(qualifiers = Devices.COMPACT_PHONE) fun adaptiveContextSwitch_compactPhone() = adaptiveContextSwitch(1.3f)
    @Test @Config(qualifiers = Devices.TYPICAL_PHONE) fun adaptiveContextSwitch_typicalPhone() = adaptiveContextSwitch(1f)
    @Test @Config(qualifiers = Devices.PHONE_LANDSCAPE) fun adaptiveContextSwitch_phoneLandscape() = adaptiveContextSwitch(1f)
    @Test @Config(qualifiers = Devices.FOLDABLE_INNER) fun adaptiveContextSwitch_foldable() = adaptiveContextSwitch(1f)
    @Test @Config(qualifiers = Devices.TABLET_PORTRAIT) fun adaptiveContextSwitch_tabletPortrait() = adaptiveContextSwitch(1f)
    @Test @Config(qualifiers = Devices.TABLET_LANDSCAPE) fun adaptiveContextSwitch_tabletLandscape() = adaptiveContextSwitch(1f)
}
