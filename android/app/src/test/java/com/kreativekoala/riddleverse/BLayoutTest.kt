package com.kreativekoala.riddleverse

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import org.junit.Test
import org.robolectric.annotation.Config

/** Group B layout matrix: fit-to-screen (no scrolling) checks for group B game screens. */
class BLayoutTest : LayoutMatrixBase() {

    private fun settle() { compose.mainClock.advanceTimeBy(700); compose.waitForIdle() }

    /** Every clickable / text node must lie fully inside the root viewport (no clipping, no scrolling). */
    private fun assertAllFullyOnScreen() {
        val root = compose.onRoot().fetchSemanticsNode().boundsInRoot
        val bad = compose.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.Text) or hasClickAction())
            .fetchSemanticsNodes()
            .filter { n ->
                val b = n.boundsInRoot
                b.width > 0f && (b.left < root.left - 1f || b.top < root.top - 1f || b.right > root.right + 1f || b.bottom > root.bottom + 1f)
            }
        if (bad.isNotEmpty()) throw AssertionError("Off-screen (root=$root): " + bad.joinToString { n ->
            val texts = n.config.getOrElse(SemanticsProperties.Text) { emptyList() }
            (if (texts.isNotEmpty()) texts.joinToString { t -> t.text } else "clickable") + "@" + n.boundsInRoot })
    }

    private val cmpJson = """{"sequence":[{"pairNumber":1,"leftValue":"3 + 4","rightValue":"2 x 4","leftNumeric":7.0,"rightNumeric":8.0,"correctAnswer":"right","operationType":"mixed","difficulty":2.0}]}"""
    private val pairJson = """{"sequence":[{"screenNumber":1,"numbers":[3,7,9],"linkingNumber":null,"isFirstScreen":true},{"screenNumber":2,"numbers":[4,7,5],"linkingNumber":7,"isFirstScreen":false}]}"""
    private val retQ = """{"topic":"Volcanoes","essay":"Volcanoes erupt.","subjects":[{"id":"s1","name":"Etna","description":"d"},{"id":"s2","name":"Fuji","description":"d"}],"facts":[{"id":"f1","text":"Fact one","correctSubject":"s1","showTiming":1000},{"id":"f2","text":"Fact two","correctSubject":"s2","showTiming":2000}]}"""
    private val retA = """{"answers":{"f1":"s1","f2":"s2"}}"""
    private val letterJson = """{"letterSet":"PLANET","letters":["P","L","A","N","E","T","R","S","I"],"allWords":[{"word":"PLANT","length":5,"points":10,"rarity":"common","frequency":10,"usesAllLetters":false},{"word":"PLANET","length":6,"points":20,"rarity":"common","frequency":10,"usesAllLetters":true}],"keyWord":"PLANET","difficulty":"Medium","timeLimit":120,"scoring":{"basePointsPerWord":10,"lengthMultiplier":{},"rarityBonus":{},"allLettersBonus":10,"speedBonus":null},"targets":{"bronze":10,"silver":20,"gold":30},"metadata":{"generatedAt":"x","totalWords":2,"source":"s","expectedDifficulty":"Medium","letterSetSource":"s","keyWordFound":true}}"""

    /** Advance the compose clock (game phases run on delays). */
    private fun advance(ms: Long) { compose.mainClock.advanceTimeBy(ms); compose.waitForIdle() }

    private fun vortex(fs: Float) {
        render(fs) { ImageVortexPuzzleScreen(onSubmitAnswer = {}, fetchNextPuzzle = {}, onBack = {}) }
        settle()
        assertOnScreen(compose.onNodeWithText("2:30", substring = true))
        assertOnScreen(compose.onNodeWithText("Progress", substring = true))
        assertOnScreen(compose.onNodeWithTag("vortex_emoji"))
        assertAllFullyOnScreen()
    }
    @Test @Config(qualifiers = Devices.SMALL_PHONE) fun vortex_smallPhone_2xFont() = vortex(2f)
    @Test @Config(qualifiers = Devices.COMPACT_PHONE) fun vortex_compactPhone_1_3xFont() = vortex(1.3f)
    @Test @Config(qualifiers = Devices.TYPICAL_PHONE) fun vortex_typicalPhone() = vortex(1f)
    @Test @Config(qualifiers = Devices.PHONE_LANDSCAPE) fun vortex_phoneLandscape() = vortex(1f)
    @Test @Config(qualifiers = Devices.FOLDABLE_INNER) fun vortex_foldableInner() = vortex(1f)
    @Test @Config(qualifiers = Devices.TABLET_PORTRAIT) fun vortex_tabletPortrait() = vortex(1f)
    @Test @Config(qualifiers = Devices.TABLET_LANDSCAPE) fun vortex_tabletLandscape() = vortex(1f)

    private fun adaptiveVortex(fs: Float) {
        render(fs) { AdaptiveImageVortexPuzzleScreen(onSubmitAnswer = {}, fetchNextPuzzle = {}, onBack = {}) }
        settle()
        assertOnScreen(compose.onNodeWithText("2:30", substring = true))
        assertOnScreen(compose.onNodeWithText("Score", substring = true))
        assertAllFullyOnScreen()
    }
    @Test @Config(qualifiers = Devices.SMALL_PHONE) fun adaptiveVortex_smallPhone_2xFont() = adaptiveVortex(2f)
    @Test @Config(qualifiers = Devices.COMPACT_PHONE) fun adaptiveVortex_compactPhone_1_3xFont() = adaptiveVortex(1.3f)
    @Test @Config(qualifiers = Devices.TYPICAL_PHONE) fun adaptiveVortex_typicalPhone() = adaptiveVortex(1f)
    @Test @Config(qualifiers = Devices.PHONE_LANDSCAPE) fun adaptiveVortex_phoneLandscape() = adaptiveVortex(1f)
    @Test @Config(qualifiers = Devices.FOLDABLE_INNER) fun adaptiveVortex_foldableInner() = adaptiveVortex(1f)
    @Test @Config(qualifiers = Devices.TABLET_PORTRAIT) fun adaptiveVortex_tabletPortrait() = adaptiveVortex(1f)
    @Test @Config(qualifiers = Devices.TABLET_LANDSCAPE) fun adaptiveVortex_tabletLandscape() = adaptiveVortex(1f)

    private fun jumble(fs: Float) {
        render(fs) { JumbleInputScreen(onBackToHome = {}) }
        settle()
        assertOnScreen(compose.onNodeWithText("1:30"))
        assertOnScreen(compose.onNodeWithText("Submit"))
        assertOnScreen(compose.onNodeWithText("Skip"))
        assertAllFullyOnScreen()
        // type a word: assembled-word box and enabled actions must still fit.
        // The default puzzle repeats letters (shuffledLetters has two "v"s), so pick by index.
        compose.onAllNodesWithText("V")[0].performClick()
        compose.onAllNodesWithText("O")[0].performClick()
        settle()
        assertAllFullyOnScreen()
    }
    @Test @Config(qualifiers = Devices.SMALL_PHONE) fun jumble_smallPhone_2xFont() = jumble(2f)
    @Test @Config(qualifiers = Devices.COMPACT_PHONE) fun jumble_compactPhone_1_3xFont() = jumble(1.3f)
    @Test @Config(qualifiers = Devices.TYPICAL_PHONE) fun jumble_typicalPhone() = jumble(1f)
    @Test @Config(qualifiers = Devices.PHONE_LANDSCAPE) fun jumble_phoneLandscape() = jumble(1f)
    @Test @Config(qualifiers = Devices.FOLDABLE_INNER) fun jumble_foldableInner() = jumble(1f)
    @Test @Config(qualifiers = Devices.TABLET_PORTRAIT) fun jumble_tabletPortrait() = jumble(1f)
    @Test @Config(qualifiers = Devices.TABLET_LANDSCAPE) fun jumble_tabletLandscape() = jumble(1f)

    private fun mathComparison(fs: Float) {
        render(fs) { MathComparisonPuzzleScreen("Medium", "1:00", 3, "1/5", cmpJson, {}, {}, {}) }
        settle()
        assertOnScreen(compose.onNodeWithText("1:00"))
        assertOnScreen(compose.onNodeWithText("3 + 4"))
        assertOnScreen(compose.onNodeWithText("2 x 4"))
        assertOnScreen(compose.onNodeWithText("EQUAL"))
        assertAllFullyOnScreen()
        compose.onNodeWithText("EQUAL").performClick()
        advance(400)
        assertAllFullyOnScreen()
    }
    @Test @Config(qualifiers = Devices.SMALL_PHONE) fun mathComparison_smallPhone_2xFont() = mathComparison(2f)
    @Test @Config(qualifiers = Devices.COMPACT_PHONE) fun mathComparison_compactPhone_1_3xFont() = mathComparison(1.3f)
    @Test @Config(qualifiers = Devices.TYPICAL_PHONE) fun mathComparison_typicalPhone() = mathComparison(1f)
    @Test @Config(qualifiers = Devices.PHONE_LANDSCAPE) fun mathComparison_phoneLandscape() = mathComparison(1f)
    @Test @Config(qualifiers = Devices.FOLDABLE_INNER) fun mathComparison_foldableInner() = mathComparison(1f)
    @Test @Config(qualifiers = Devices.TABLET_PORTRAIT) fun mathComparison_tabletPortrait() = mathComparison(1f)
    @Test @Config(qualifiers = Devices.TABLET_LANDSCAPE) fun mathComparison_tabletLandscape() = mathComparison(1f)

    private fun adaptiveMathComparison(fs: Float) {
        render(fs) { AdaptiveMathComparisonPuzzleScreen("Medium", "1:00", 3, "1/5", {}, {}, {}) }
        settle()
        assertOnScreen(compose.onNodeWithText("1:00"))
        assertOnScreen(compose.onNodeWithText("EQUAL"))
        assertOnScreen(compose.onNodeWithText("Which value is greater?"))
        assertAllFullyOnScreen()
        compose.onNodeWithText("EQUAL").performClick()
        advance(400)
        assertAllFullyOnScreen()
    }
    @Test @Config(qualifiers = Devices.SMALL_PHONE) fun adaptiveMathComparison_smallPhone_2xFont() = adaptiveMathComparison(2f)
    @Test @Config(qualifiers = Devices.COMPACT_PHONE) fun adaptiveMathComparison_compactPhone_1_3xFont() = adaptiveMathComparison(1.3f)
    @Test @Config(qualifiers = Devices.TYPICAL_PHONE) fun adaptiveMathComparison_typicalPhone() = adaptiveMathComparison(1f)
    @Test @Config(qualifiers = Devices.PHONE_LANDSCAPE) fun adaptiveMathComparison_phoneLandscape() = adaptiveMathComparison(1f)
    @Test @Config(qualifiers = Devices.FOLDABLE_INNER) fun adaptiveMathComparison_foldableInner() = adaptiveMathComparison(1f)
    @Test @Config(qualifiers = Devices.TABLET_PORTRAIT) fun adaptiveMathComparison_tabletPortrait() = adaptiveMathComparison(1f)
    @Test @Config(qualifiers = Devices.TABLET_LANDSCAPE) fun adaptiveMathComparison_tabletLandscape() = adaptiveMathComparison(1f)

    private fun mathCrossword(fs: Float) {
        render(fs) { MathCrosswordPuzzleScreen("Medium", "4:00", 3, "1/5", "{}", {}, {}, {}) }
        settle()
        assertOnScreen(compose.onNodeWithText("04:00"))
        assertOnScreen(compose.onNodeWithText("Check", substring = true))
        assertAllFullyOnScreen()
        // select an empty number cell: the number pad appears in its reserved space
        compose.onAllNodes(hasText("") and hasClickAction())[0].performClick()
        settle()
        assertAllFullyOnScreen()
    }
    @Test @Config(qualifiers = Devices.SMALL_PHONE) fun mathCrossword_smallPhone_2xFont() = mathCrossword(2f)
    @Test @Config(qualifiers = Devices.COMPACT_PHONE) fun mathCrossword_compactPhone_1_3xFont() = mathCrossword(1.3f)
    @Test @Config(qualifiers = Devices.TYPICAL_PHONE) fun mathCrossword_typicalPhone() = mathCrossword(1f)
    @Test @Config(qualifiers = Devices.PHONE_LANDSCAPE) fun mathCrossword_phoneLandscape() = mathCrossword(1f)
    @Test @Config(qualifiers = Devices.FOLDABLE_INNER) fun mathCrossword_foldableInner() = mathCrossword(1f)
    @Test @Config(qualifiers = Devices.TABLET_PORTRAIT) fun mathCrossword_tabletPortrait() = mathCrossword(1f)
    @Test @Config(qualifiers = Devices.TABLET_LANDSCAPE) fun mathCrossword_tabletLandscape() = mathCrossword(1f)

    private fun adaptiveMathCrossword(fs: Float) {
        render(fs) { AdaptiveMathCrosswordPuzzleScreen(onGameComplete = { _, _ -> }, onBack = {}) }
        settle()
        assertOnScreen(compose.onNodeWithText("Check", substring = true))
        assertAllFullyOnScreen()
        compose.onAllNodes(hasText("") and hasClickAction())[0].performClick()
        settle()
        assertAllFullyOnScreen()
    }
    @Test @Config(qualifiers = Devices.SMALL_PHONE) fun adaptiveMathCrossword_smallPhone_2xFont() = adaptiveMathCrossword(2f)
    @Test @Config(qualifiers = Devices.COMPACT_PHONE) fun adaptiveMathCrossword_compactPhone_1_3xFont() = adaptiveMathCrossword(1.3f)
    @Test @Config(qualifiers = Devices.TYPICAL_PHONE) fun adaptiveMathCrossword_typicalPhone() = adaptiveMathCrossword(1f)
    @Test @Config(qualifiers = Devices.PHONE_LANDSCAPE) fun adaptiveMathCrossword_phoneLandscape() = adaptiveMathCrossword(1f)
    @Test @Config(qualifiers = Devices.FOLDABLE_INNER) fun adaptiveMathCrossword_foldableInner() = adaptiveMathCrossword(1f)
    @Test @Config(qualifiers = Devices.TABLET_PORTRAIT) fun adaptiveMathCrossword_tabletPortrait() = adaptiveMathCrossword(1f)
    @Test @Config(qualifiers = Devices.TABLET_LANDSCAPE) fun adaptiveMathCrossword_tabletLandscape() = adaptiveMathCrossword(1f)

    private fun memoryPair(fs: Float) {
        render(fs) { MemoryPreviousPairPuzzleScreen("Medium", "2:00", 3, "1/5", pairJson, "7", {}, {}, {}) }
        settle()
        assertOnScreen(compose.onNodeWithText("2:00"))
        assertOnScreen(compose.onNodeWithText("Screen 1 of 2", substring = true))
        assertAllFullyOnScreen()
        advance(3500) // study screen -> answer screen (animals become tappable)
        assertOnScreen(compose.onNodeWithText("Screen 2 of 2", substring = true))
        assertAllFullyOnScreen()
    }
    @Test @Config(qualifiers = Devices.SMALL_PHONE) fun memoryPair_smallPhone_2xFont() = memoryPair(2f)
    @Test @Config(qualifiers = Devices.COMPACT_PHONE) fun memoryPair_compactPhone_1_3xFont() = memoryPair(1.3f)
    @Test @Config(qualifiers = Devices.TYPICAL_PHONE) fun memoryPair_typicalPhone() = memoryPair(1f)
    @Test @Config(qualifiers = Devices.PHONE_LANDSCAPE) fun memoryPair_phoneLandscape() = memoryPair(1f)
    @Test @Config(qualifiers = Devices.FOLDABLE_INNER) fun memoryPair_foldableInner() = memoryPair(1f)
    @Test @Config(qualifiers = Devices.TABLET_PORTRAIT) fun memoryPair_tabletPortrait() = memoryPair(1f)
    @Test @Config(qualifiers = Devices.TABLET_LANDSCAPE) fun memoryPair_tabletLandscape() = memoryPair(1f)

    private fun adaptiveMemoryPair(fs: Float) {
        render(fs) { AdaptiveMemoryPreviousPairScreen("Medium", "2:00", 3, "1/5", {}, {}, {}) }
        settle()
        assertOnScreen(compose.onNodeWithText("2:00"))
        assertOnScreen(compose.onNodeWithText("Screen 1 of", substring = true))
        assertAllFullyOnScreen()
        advance(6000)
        assertAllFullyOnScreen()
    }
    @Test @Config(qualifiers = Devices.SMALL_PHONE) fun adaptiveMemoryPair_smallPhone_2xFont() = adaptiveMemoryPair(2f)
    @Test @Config(qualifiers = Devices.COMPACT_PHONE) fun adaptiveMemoryPair_compactPhone_1_3xFont() = adaptiveMemoryPair(1.3f)
    @Test @Config(qualifiers = Devices.TYPICAL_PHONE) fun adaptiveMemoryPair_typicalPhone() = adaptiveMemoryPair(1f)
    @Test @Config(qualifiers = Devices.PHONE_LANDSCAPE) fun adaptiveMemoryPair_phoneLandscape() = adaptiveMemoryPair(1f)
    @Test @Config(qualifiers = Devices.FOLDABLE_INNER) fun adaptiveMemoryPair_foldableInner() = adaptiveMemoryPair(1f)
    @Test @Config(qualifiers = Devices.TABLET_PORTRAIT) fun adaptiveMemoryPair_tabletPortrait() = adaptiveMemoryPair(1f)
    @Test @Config(qualifiers = Devices.TABLET_LANDSCAPE) fun adaptiveMemoryPair_tabletLandscape() = adaptiveMemoryPair(1f)

    private fun adaptiveMemorySingle(fs: Float) {
        render(fs) { SimplifiedAdaptiveMemoryScreen("Medium", "2:00", 3, "1/5", {}, {}, {}) }
        settle()
        assertOnScreen(compose.onNodeWithText("START CHALLENGE"))
        assertAllFullyOnScreen()
        compose.onNodeWithText("START CHALLENGE").performClick()
        advance(6000)
        assertAllFullyOnScreen()
    }
    @Test @Config(qualifiers = Devices.SMALL_PHONE) fun adaptiveMemorySingle_smallPhone_2xFont() = adaptiveMemorySingle(2f)
    @Test @Config(qualifiers = Devices.COMPACT_PHONE) fun adaptiveMemorySingle_compactPhone_1_3xFont() = adaptiveMemorySingle(1.3f)
    @Test @Config(qualifiers = Devices.TYPICAL_PHONE) fun adaptiveMemorySingle_typicalPhone() = adaptiveMemorySingle(1f)
    @Test @Config(qualifiers = Devices.PHONE_LANDSCAPE) fun adaptiveMemorySingle_phoneLandscape() = adaptiveMemorySingle(1f)
    @Test @Config(qualifiers = Devices.FOLDABLE_INNER) fun adaptiveMemorySingle_foldableInner() = adaptiveMemorySingle(1f)
    @Test @Config(qualifiers = Devices.TABLET_PORTRAIT) fun adaptiveMemorySingle_tabletPortrait() = adaptiveMemorySingle(1f)
    @Test @Config(qualifiers = Devices.TABLET_LANDSCAPE) fun adaptiveMemorySingle_tabletLandscape() = adaptiveMemorySingle(1f)

    private fun memoryRetention(fs: Float) {
        render(fs) { MemoryRetentionPuzzleScreen(puzzleData = retQ, correctAnswer = retA, onSubmitAnswer = {}, fetchNextPuzzle = {}) }
        settle()
        assertOnScreen(compose.onNodeWithText("Begin", substring = true))
        assertAllFullyOnScreen()
        compose.onNodeWithText("Begin", substring = true).performClick()
        advance(1500)
        assertOnScreen(compose.onNodeWithText("Volcanoes"))
        assertOnScreen(compose.onNodeWithText("Etna"))
        assertOnScreen(compose.onNodeWithText("Fuji"))
        assertAllFullyOnScreen()
    }
    @Test @Config(qualifiers = Devices.SMALL_PHONE) fun memoryRetention_smallPhone_2xFont() = memoryRetention(2f)
    @Test @Config(qualifiers = Devices.COMPACT_PHONE) fun memoryRetention_compactPhone_1_3xFont() = memoryRetention(1.3f)
    @Test @Config(qualifiers = Devices.TYPICAL_PHONE) fun memoryRetention_typicalPhone() = memoryRetention(1f)
    @Test @Config(qualifiers = Devices.PHONE_LANDSCAPE) fun memoryRetention_phoneLandscape() = memoryRetention(1f)
    @Test @Config(qualifiers = Devices.FOLDABLE_INNER) fun memoryRetention_foldableInner() = memoryRetention(1f)
    @Test @Config(qualifiers = Devices.TABLET_PORTRAIT) fun memoryRetention_tabletPortrait() = memoryRetention(1f)
    @Test @Config(qualifiers = Devices.TABLET_LANDSCAPE) fun memoryRetention_tabletLandscape() = memoryRetention(1f)

    private fun letterSet(fs: Float) {
        val ctx = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.app.Application>()
        val pz = com.google.gson.Gson().fromJson(letterJson, LetterSetPuzzle::class.java)
        render(fs) { EnhancedLetterSetGameScreen(pz, ctx, {}, { _, _ -> }) }
        compose.waitUntil(30_000) { compose.onAllNodes(hasText("Loading", substring = true)).fetchSemanticsNodes().isEmpty() }
        settle()
        assertOnScreen(compose.onNodeWithText("02:00"))
        assertOnScreen(compose.onNodeWithTag("ls_word"))
        assertOnScreen(compose.onNodeWithContentDescription("Submit"))
        compose.onAllNodesWithTag("ls_key").assertCountEquals(9)
        compose.onAllNodesWithTag("ls_key").fetchSemanticsNodes().forEachIndexed { i, _ -> assertOnScreen(compose.onAllNodesWithTag("ls_key")[i]) }
        assertAllFullyOnScreen()
    }
    @Test @Config(qualifiers = Devices.SMALL_PHONE) fun letterSet_smallPhone_2xFont() = letterSet(2f)
    @Test @Config(qualifiers = Devices.COMPACT_PHONE) fun letterSet_compactPhone_1_3xFont() = letterSet(1.3f)
    @Test @Config(qualifiers = Devices.TYPICAL_PHONE) fun letterSet_typicalPhone() = letterSet(1f)
    @Test @Config(qualifiers = Devices.PHONE_LANDSCAPE) fun letterSet_phoneLandscape() = letterSet(1f)
    @Test @Config(qualifiers = Devices.FOLDABLE_INNER) fun letterSet_foldableInner() = letterSet(1f)
    @Test @Config(qualifiers = Devices.TABLET_PORTRAIT) fun letterSet_tabletPortrait() = letterSet(1f)
    @Test @Config(qualifiers = Devices.TABLET_LANDSCAPE) fun letterSet_tabletLandscape() = letterSet(1f)

}
