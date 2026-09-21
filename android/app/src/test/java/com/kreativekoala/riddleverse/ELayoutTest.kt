package com.kreativekoala.riddleverse

import android.app.Application
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertTrue
import org.junit.Test
import org.robolectric.annotation.Config

/**
 * Group E game screens: Synonym Grouping, Triangle Dot Memory (+adaptive), Verb Match,
 * Word Snake, Math Expression (+adaptive), Number Sequence (+adaptive),
 * Symbol Swipe (+adaptive), Unique Object (adaptive).
 *
 * Every primary control must be FULLY inside the viewport with no scrolling.
 */
class ELayoutTest : LayoutMatrixBase() {

    private fun str(id: Int): String =
        ApplicationProvider.getApplicationContext<Application>().getString(id)

    /** Stricter than assertIsDisplayed: the whole node must be inside the root, not just part of it. */
    private fun assertFully(node: SemanticsNodeInteraction, what: String = "") {
        assertOnScreen(node)
        val b = node.getUnclippedBoundsInRoot()
        val r = compose.onRoot().getUnclippedBoundsInRoot()
        val slack = 0.5f
        assertTrue(
            "$what not fully inside viewport: node=$b root=$r",
            b.left.value >= r.left.value - slack && b.top.value >= r.top.value - slack &&
                b.right.value <= r.right.value + slack && b.bottom.value <= r.bottom.value + slack
        )
    }

    private fun assertAllFully(tag: String, min: Int = 1) {
        val nodes = compose.onAllNodesWithTag(tag).fetchSemanticsNodes()
        assertTrue("expected at least $min nodes tagged $tag, got ${nodes.size}", nodes.size >= min)
        for (i in nodes.indices) assertFully(compose.onAllNodesWithTag(tag)[i], "$tag[$i]")
    }

    private fun assertMinTouch(node: SemanticsNodeInteraction, what: String) {
        val b = node.getUnclippedBoundsInRoot()
        val width = b.right - b.left
        val height = b.bottom - b.top
        assertTrue("$what touch target too small: $width x $height", width.value >= 47.5f && height.value >= 47.5f)
    }

    // ---- Synonym grouping ------------------------------------------------

    private fun synonym(fontScale: Float) {
        render(fontScale) {
            SynonymGroupingPuzzleScreen(
                synonymSets = listOf(
                    listOf("happy", "glad", "joyful"),
                    listOf("sad", "unhappy", "gloomy"),
                    listOf("big", "large", "huge")
                ),
                onSubmitAnswer = {}, fetchNextPuzzle = {}, onBack = {}
            )
        }
        assertFully(compose.onNodeWithTag("hud_timer"), "timer")
        assertFully(compose.onNodeWithTag("synonym_word"), "current word")
        for (i in 0..2) assertFully(compose.onNodeWithTag("synonym_set_$i"), "set $i")
        for (i in 0..2) assertMinTouch(compose.onNodeWithTag("synonym_set_$i"), "set $i")
        assertFully(compose.onNodeWithContentDescription(str(R.string.back)), "back")
    }

    @Test @Config(qualifiers = Devices.SMALL_PHONE) fun synonym_smallPhone_2xFont() = synonym(2f)
    @Test @Config(qualifiers = Devices.COMPACT_PHONE) fun synonym_compactPhone_1_3xFont() = synonym(1.3f)
    @Test @Config(qualifiers = Devices.TYPICAL_PHONE) fun synonym_typicalPhone() = synonym(1f)
    @Test @Config(qualifiers = Devices.PHONE_LANDSCAPE) fun synonym_phoneLandscape() = synonym(1f)
    @Test @Config(qualifiers = Devices.FOLDABLE_INNER) fun synonym_foldableInner() = synonym(1f)
    @Test @Config(qualifiers = Devices.TABLET_PORTRAIT) fun synonym_tabletPortrait() = synonym(1f)
    @Test @Config(qualifiers = Devices.TABLET_LANDSCAPE) fun synonym_tabletLandscape() = synonym(1f)

    // ---- Triangle dot memory (both variants share a flow) ----------------

    private fun triangleFlow() {
        assertFully(compose.onNodeWithText(str(R.string.start_game)), "start button")
        assertMinTouch(compose.onNodeWithText(str(R.string.start_game)), "start button")
        assertFully(compose.onNodeWithTag("hud_timer"), "timer")
        compose.onNodeWithText(str(R.string.start_game)).performClick()
        compose.mainClock.advanceTimeBy(2600)
        compose.waitForIdle()
        assertFully(compose.onNodeWithTag("triangle_pattern"), "pattern")
        assertFully(compose.onNodeWithText("YES"), "YES")
        assertFully(compose.onNodeWithText("NO"), "NO")
        assertMinTouch(compose.onNodeWithText("YES"), "YES")
        assertFully(compose.onNodeWithTag("hud_timer"), "timer in play")
    }

    private fun triangle(fontScale: Float) {
        render(fontScale) {
            TriangleDotMemoryPuzzleScreen(
                difficulty = "Medium", timer = "1:00", hearts = 3, level = "1/5",
                puzzleData = "", correctAnswer = "",
                onSubmitAnswer = {}, fetchNextPuzzle = {}, onBack = {}
            )
        }
        triangleFlow()
    }

    @Test @Config(qualifiers = Devices.SMALL_PHONE) fun triangle_smallPhone_2xFont() = triangle(2f)
    @Test @Config(qualifiers = Devices.COMPACT_PHONE) fun triangle_compactPhone_1_3xFont() = triangle(1.3f)
    @Test @Config(qualifiers = Devices.TYPICAL_PHONE) fun triangle_typicalPhone() = triangle(1f)
    @Test @Config(qualifiers = Devices.PHONE_LANDSCAPE) fun triangle_phoneLandscape() = triangle(1f)
    @Test @Config(qualifiers = Devices.FOLDABLE_INNER) fun triangle_foldableInner() = triangle(1f)
    @Test @Config(qualifiers = Devices.TABLET_PORTRAIT) fun triangle_tabletPortrait() = triangle(1f)
    @Test @Config(qualifiers = Devices.TABLET_LANDSCAPE) fun triangle_tabletLandscape() = triangle(1f)

    private fun adaptiveTriangle(fontScale: Float) {
        render(fontScale) {
            AdaptiveTriangleDotMemoryPuzzleScreen(
                timer = "1:00", level = "1/5",
                onSubmitAnswer = {}, fetchNextPuzzle = {}, onBack = {}
            )
        }
        triangleFlow()
    }

    @Test @Config(qualifiers = Devices.SMALL_PHONE) fun adaptiveTriangle_smallPhone_2xFont() = adaptiveTriangle(2f)
    @Test @Config(qualifiers = Devices.COMPACT_PHONE) fun adaptiveTriangle_compactPhone_1_3xFont() = adaptiveTriangle(1.3f)
    @Test @Config(qualifiers = Devices.TYPICAL_PHONE) fun adaptiveTriangle_typicalPhone() = adaptiveTriangle(1f)
    @Test @Config(qualifiers = Devices.PHONE_LANDSCAPE) fun adaptiveTriangle_phoneLandscape() = adaptiveTriangle(1f)
    @Test @Config(qualifiers = Devices.FOLDABLE_INNER) fun adaptiveTriangle_foldableInner() = adaptiveTriangle(1f)
    @Test @Config(qualifiers = Devices.TABLET_PORTRAIT) fun adaptiveTriangle_tabletPortrait() = adaptiveTriangle(1f)
    @Test @Config(qualifiers = Devices.TABLET_LANDSCAPE) fun adaptiveTriangle_tabletLandscape() = adaptiveTriangle(1f)

    // ---- Verb match ------------------------------------------------------

    private fun verbMatch(fontScale: Float, optionCount: Int = 4) {
        val opts = listOf("ran", "run", "runs", "running", "runner", "ranged").take(optionCount)
        render(fontScale) {
            MatchScreen(
                puzzleId = "p1", question = "Past tense of run", difficulty = "Easy",
                options = opts, correctAnswer = "ran", selectedOption = null,
                onOptionSelected = {}, onContinue = {}, onCorrectAnswer = {}, onHint = {}, onBack = {}
            )
        }
        assertFully(compose.onNodeWithTag("hud_timer"), "timer")
        assertFully(compose.onNodeWithTag("match_question"), "question")
        assertAllFully("match_option", opts.size)
        assertFully(compose.onNodeWithText(str(R.string.submit)), "submit")
        assertMinTouch(compose.onNodeWithText(str(R.string.submit)), "submit")
        assertFully(compose.onNodeWithContentDescription(str(R.string.back)), "back")
    }

    @Test @Config(qualifiers = Devices.SMALL_PHONE) fun verbMatch_smallPhone_2xFont() = verbMatch(2f)
    @Test @Config(qualifiers = Devices.COMPACT_PHONE) fun verbMatch_compactPhone_1_3xFont() = verbMatch(1.3f)
    @Test @Config(qualifiers = Devices.TYPICAL_PHONE) fun verbMatch_typicalPhone() = verbMatch(1f)
    @Test @Config(qualifiers = Devices.PHONE_LANDSCAPE) fun verbMatch_phoneLandscape() = verbMatch(1f)
    @Test @Config(qualifiers = Devices.FOLDABLE_INNER) fun verbMatch_foldableInner() = verbMatch(1f)
    @Test @Config(qualifiers = Devices.TABLET_PORTRAIT) fun verbMatch_tabletPortrait() = verbMatch(1f)
    @Test @Config(qualifiers = Devices.TABLET_LANDSCAPE) fun verbMatch_tabletLandscape() = verbMatch(1f)
    @Test @Config(qualifiers = Devices.SMALL_PHONE) fun verbMatch6_smallPhone_2xFont() = verbMatch(2f, 6)
    @Test @Config(qualifiers = Devices.PHONE_LANDSCAPE) fun verbMatch6_phoneLandscape() = verbMatch(1f, 6)

    // ---- Word snake ------------------------------------------------------

    private fun wordSnakeJson(size: Int): String {
        val letters = "ABCDEFGHIJKLMNOPQRSTUVWXYZ"
        val grid = (0 until size).joinToString(",", "[", "]") { r ->
            (0 until size).joinToString(",", "[", "]") { c -> "\"${letters[(r * size + c) % 26]}\"" }
        }
        fun word(w: String, row: Int, color: String): String {
            val path = w.indices.joinToString(",", "[", "]") { "{\"row\":$row,\"col\":$it}" }
            return "{\"word\":\"$w\",\"clue\":\"Clue for $w\",\"path\":$path,\"color\":\"$color\",\"found\":false}"
        }
        val words = listOf(
            word("ABCD", 0, "#FF5733"), word("ABCDEF", 1, "#33A1FF"),
            word("ABCDE", 2, "#2ECC71"), word("ABC", 3, "#9B59B6")
        ).joinToString(",", "[", "]")
        return "{\"grid\":$grid,\"words\":$words,\"gridSize\":$size}"
    }

    private fun wordSnake(fontScale: Float, size: Int = 8) {
        render(fontScale) {
            WordSnakeScreen(
                puzzleData = wordSnakeJson(size),
                onSubmitAnswer = {}, fetchNextPuzzle = {}, onBack = {}
            )
        }
        assertFully(compose.onNodeWithTag("hud_timer"), "timer")
        assertFully(compose.onNodeWithTag("snake_grid"), "grid")
        assertAllFully("snake_cell", size * size)
        assertFully(compose.onNodeWithContentDescription(str(R.string.back)), "back")
        assertFully(compose.onNodeWithTag("snake_clear"), "clear")
        assertFully(compose.onNodeWithTag("snake_reset"), "reset")
        assertMinTouch(compose.onNodeWithTag("snake_clear"), "clear")
        assertFully(compose.onNodeWithTag("snake_hints"), "hints")
        assertFully(compose.onNodeWithTag("snake_words_progress"), "progress")
    }

    @Test @Config(qualifiers = Devices.SMALL_PHONE) fun wordSnake_smallPhone_2xFont() = wordSnake(2f)
    @Test @Config(qualifiers = Devices.COMPACT_PHONE) fun wordSnake_compactPhone_1_3xFont() = wordSnake(1.3f)
    @Test @Config(qualifiers = Devices.TYPICAL_PHONE) fun wordSnake_typicalPhone() = wordSnake(1f)
    @Test @Config(qualifiers = Devices.PHONE_LANDSCAPE) fun wordSnake_phoneLandscape() = wordSnake(1f)
    @Test @Config(qualifiers = Devices.FOLDABLE_INNER) fun wordSnake_foldableInner() = wordSnake(1f)
    @Test @Config(qualifiers = Devices.TABLET_PORTRAIT) fun wordSnake_tabletPortrait() = wordSnake(1f)
    @Test @Config(qualifiers = Devices.TABLET_LANDSCAPE) fun wordSnake_tabletLandscape() = wordSnake(1f)
    @Test @Config(qualifiers = Devices.SMALL_PHONE) fun wordSnake10_smallPhone_2xFont() = wordSnake(2f, 10)
    @Test @Config(qualifiers = Devices.PHONE_LANDSCAPE) fun wordSnake10_phoneLandscape() = wordSnake(1f, 10)

    // ---- Math expression (falling equations + keypad) --------------------

    private val mathKeys = listOf("2", "3", "4", "5", "6", "7", "8", "9", "+", "-", "×", "÷")

    private fun mathExpression(fontScale: Float) {
        render(fontScale) {
            MathExpressionPuzzleScreen(onSubmitAnswer = {}, fetchNextPuzzle = {}, onBack = {})
        }
        compose.waitForIdle()
        assertFully(compose.onNodeWithTag("hud_timer"), "timer")
        assertFully(compose.onNodeWithTag("math_play_area"), "play area")
        for (k in mathKeys) assertFully(compose.onNodeWithTag("key_$k"), "key $k")
        assertMinTouch(compose.onNodeWithTag("key_2"), "key 2")
        assertFully(compose.onNodeWithContentDescription(str(R.string.back)), "back")
    }

    @Test @Config(qualifiers = Devices.SMALL_PHONE) fun math_smallPhone_2xFont() = mathExpression(2f)
    @Test @Config(qualifiers = Devices.COMPACT_PHONE) fun math_compactPhone_1_3xFont() = mathExpression(1.3f)
    @Test @Config(qualifiers = Devices.TYPICAL_PHONE) fun math_typicalPhone() = mathExpression(1f)
    @Test @Config(qualifiers = Devices.PHONE_LANDSCAPE) fun math_phoneLandscape() = mathExpression(1f)
    @Test @Config(qualifiers = Devices.FOLDABLE_INNER) fun math_foldableInner() = mathExpression(1f)
    @Test @Config(qualifiers = Devices.TABLET_PORTRAIT) fun math_tabletPortrait() = mathExpression(1f)
    @Test @Config(qualifiers = Devices.TABLET_LANDSCAPE) fun math_tabletLandscape() = mathExpression(1f)

    private fun adaptiveMath(fontScale: Float) {
        render(fontScale) {
            AdaptiveMathExpressionPuzzleScreen(onGameComplete = { _, _ -> }, onBack = {})
        }
        compose.waitForIdle()
        assertFully(compose.onNodeWithTag("hud_timer"), "timer")
        assertFully(compose.onNodeWithTag("math_play_area"), "play area")
        for (k in mathKeys + listOf("0", "1", "C", "BACK", "ENTER")) assertFully(compose.onNodeWithTag("key_$k"), "key $k")
        assertMinTouch(compose.onNodeWithTag("key_2"), "key 2")
        assertMinTouch(compose.onNodeWithTag("key_ENTER"), "enter")
    }

    @Test @Config(qualifiers = Devices.SMALL_PHONE) fun adaptiveMath_smallPhone_2xFont() = adaptiveMath(2f)
    @Test @Config(qualifiers = Devices.COMPACT_PHONE) fun adaptiveMath_compactPhone_1_3xFont() = adaptiveMath(1.3f)
    @Test @Config(qualifiers = Devices.TYPICAL_PHONE) fun adaptiveMath_typicalPhone() = adaptiveMath(1f)
    @Test @Config(qualifiers = Devices.PHONE_LANDSCAPE) fun adaptiveMath_phoneLandscape() = adaptiveMath(1f)
    @Test @Config(qualifiers = Devices.FOLDABLE_INNER) fun adaptiveMath_foldableInner() = adaptiveMath(1f)
    @Test @Config(qualifiers = Devices.TABLET_PORTRAIT) fun adaptiveMath_tabletPortrait() = adaptiveMath(1f)
    @Test @Config(qualifiers = Devices.TABLET_LANDSCAPE) fun adaptiveMath_tabletLandscape() = adaptiveMath(1f)

    // ---- Number sequence -------------------------------------------------

    private fun sequenceFlow() {
        assertFully(compose.onNodeWithTag("hud_timer"), "timer")
        assertFully(compose.onNodeWithText(str(R.string.start).uppercase()), "start")
        assertMinTouch(compose.onNodeWithText(str(R.string.start).uppercase()), "start")
        compose.onNodeWithText(str(R.string.start).uppercase()).performClick()
        compose.waitForIdle()
        assertFully(compose.onNodeWithTag("seq_area"), "play area")
        assertAllFully("seq_number", 5)
        assertFully(compose.onNodeWithTag("hud_timer"), "timer in play")
    }

    private fun numberSequence(fontScale: Float) {
        render(fontScale) {
            NumberSequencePuzzleScreen(onGameComplete = { _, _ -> }, onBack = {})
        }
        sequenceFlow()
    }

    @Test @Config(qualifiers = Devices.SMALL_PHONE) fun sequence_smallPhone_2xFont() = numberSequence(2f)
    @Test @Config(qualifiers = Devices.COMPACT_PHONE) fun sequence_compactPhone_1_3xFont() = numberSequence(1.3f)
    @Test @Config(qualifiers = Devices.TYPICAL_PHONE) fun sequence_typicalPhone() = numberSequence(1f)
    @Test @Config(qualifiers = Devices.PHONE_LANDSCAPE) fun sequence_phoneLandscape() = numberSequence(1f)
    @Test @Config(qualifiers = Devices.FOLDABLE_INNER) fun sequence_foldableInner() = numberSequence(1f)
    @Test @Config(qualifiers = Devices.TABLET_PORTRAIT) fun sequence_tabletPortrait() = numberSequence(1f)
    @Test @Config(qualifiers = Devices.TABLET_LANDSCAPE) fun sequence_tabletLandscape() = numberSequence(1f)

    private fun adaptiveSequence(fontScale: Float) {
        render(fontScale) {
            AdaptiveNumberSequencePuzzleScreen(onGameComplete = { _, _ -> }, onBack = {})
        }
        sequenceFlow()
    }

    @Test @Config(qualifiers = Devices.SMALL_PHONE) fun adaptiveSequence_smallPhone_2xFont() = adaptiveSequence(2f)
    @Test @Config(qualifiers = Devices.COMPACT_PHONE) fun adaptiveSequence_compactPhone_1_3xFont() = adaptiveSequence(1.3f)
    @Test @Config(qualifiers = Devices.TYPICAL_PHONE) fun adaptiveSequence_typicalPhone() = adaptiveSequence(1f)
    @Test @Config(qualifiers = Devices.PHONE_LANDSCAPE) fun adaptiveSequence_phoneLandscape() = adaptiveSequence(1f)
    @Test @Config(qualifiers = Devices.FOLDABLE_INNER) fun adaptiveSequence_foldableInner() = adaptiveSequence(1f)
    @Test @Config(qualifiers = Devices.TABLET_PORTRAIT) fun adaptiveSequence_tabletPortrait() = adaptiveSequence(1f)
    @Test @Config(qualifiers = Devices.TABLET_LANDSCAPE) fun adaptiveSequence_tabletLandscape() = adaptiveSequence(1f)

    // ---- Symbol swipe ----------------------------------------------------

    private fun swipeFlow() {
        assertFully(compose.onNodeWithTag("hud_timer"), "timer")
        assertFully(compose.onNodeWithText(str(R.string.start)), "start")
        assertMinTouch(compose.onNodeWithText(str(R.string.start)), "start")
        compose.onNodeWithText(str(R.string.start)).performClick()
        compose.waitForIdle()
        assertFully(compose.onNodeWithTag("swipe_area"), "play area")
        assertFully(compose.onNodeWithTag("swipe_card"), "swipe card")
        assertFully(compose.onNodeWithTag("swipe_ref_left"), "left reference")
        assertFully(compose.onNodeWithTag("swipe_ref_right"), "right reference")
        assertFully(compose.onNodeWithTag("hud_timer"), "timer in play")
    }

    private fun symbolSwipe(fontScale: Float) {
        render(fontScale) {
            SymbolSwipePuzzleScreen(onGameComplete = { _, _ -> }, onBack = {})
        }
        swipeFlow()
    }

    @Test @Config(qualifiers = Devices.SMALL_PHONE) fun swipe_smallPhone_2xFont() = symbolSwipe(2f)
    @Test @Config(qualifiers = Devices.COMPACT_PHONE) fun swipe_compactPhone_1_3xFont() = symbolSwipe(1.3f)
    @Test @Config(qualifiers = Devices.TYPICAL_PHONE) fun swipe_typicalPhone() = symbolSwipe(1f)
    @Test @Config(qualifiers = Devices.PHONE_LANDSCAPE) fun swipe_phoneLandscape() = symbolSwipe(1f)
    @Test @Config(qualifiers = Devices.FOLDABLE_INNER) fun swipe_foldableInner() = symbolSwipe(1f)
    @Test @Config(qualifiers = Devices.TABLET_PORTRAIT) fun swipe_tabletPortrait() = symbolSwipe(1f)
    @Test @Config(qualifiers = Devices.TABLET_LANDSCAPE) fun swipe_tabletLandscape() = symbolSwipe(1f)

    private fun adaptiveSwipe(fontScale: Float) {
        render(fontScale) {
            AdaptiveSymbolSwipePuzzleScreen(onGameComplete = { _, _ -> }, onBack = {})
        }
        swipeFlow()
    }

    @Test @Config(qualifiers = Devices.SMALL_PHONE) fun adaptiveSwipe_smallPhone_2xFont() = adaptiveSwipe(2f)
    @Test @Config(qualifiers = Devices.COMPACT_PHONE) fun adaptiveSwipe_compactPhone_1_3xFont() = adaptiveSwipe(1.3f)
    @Test @Config(qualifiers = Devices.TYPICAL_PHONE) fun adaptiveSwipe_typicalPhone() = adaptiveSwipe(1f)
    @Test @Config(qualifiers = Devices.PHONE_LANDSCAPE) fun adaptiveSwipe_phoneLandscape() = adaptiveSwipe(1f)
    @Test @Config(qualifiers = Devices.FOLDABLE_INNER) fun adaptiveSwipe_foldableInner() = adaptiveSwipe(1f)
    @Test @Config(qualifiers = Devices.TABLET_PORTRAIT) fun adaptiveSwipe_tabletPortrait() = adaptiveSwipe(1f)
    @Test @Config(qualifiers = Devices.TABLET_LANDSCAPE) fun adaptiveSwipe_tabletLandscape() = adaptiveSwipe(1f)

    // ---- Unique object ---------------------------------------------------

    private fun uniqueObject(fontScale: Float) {
        render(fontScale) {
            AdaptiveUniqueObjectPuzzleScreen(
                timer = "1:00", level = "1/5",
                onSubmitAnswer = {}, fetchNextPuzzle = {}, onBack = {}
            )
        }
        compose.waitForIdle()
        assertFully(compose.onNodeWithTag("hud_timer"), "timer")
        assertFully(compose.onNodeWithTag("unique_area"), "play area")
        assertAllFully("unique_object", 6)
        assertFully(compose.onNodeWithContentDescription(str(R.string.back)), "back")
    }

    @Test @Config(qualifiers = Devices.SMALL_PHONE) fun unique_smallPhone_2xFont() = uniqueObject(2f)
    @Test @Config(qualifiers = Devices.COMPACT_PHONE) fun unique_compactPhone_1_3xFont() = uniqueObject(1.3f)
    @Test @Config(qualifiers = Devices.TYPICAL_PHONE) fun unique_typicalPhone() = uniqueObject(1f)
    @Test @Config(qualifiers = Devices.PHONE_LANDSCAPE) fun unique_phoneLandscape() = uniqueObject(1f)
    @Test @Config(qualifiers = Devices.FOLDABLE_INNER) fun unique_foldableInner() = uniqueObject(1f)
    @Test @Config(qualifiers = Devices.TABLET_PORTRAIT) fun unique_tabletPortrait() = uniqueObject(1f)
    @Test @Config(qualifiers = Devices.TABLET_LANDSCAPE) fun unique_tabletLandscape() = uniqueObject(1f)
}
