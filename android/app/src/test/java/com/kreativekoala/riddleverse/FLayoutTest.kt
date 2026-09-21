package com.kreativekoala.riddleverse

import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.onNodeWithContentDescription
import org.junit.Test
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.Duration

/** Group F game screens: every primary control must be on screen with no scrolling. */
class FLayoutTest : LayoutMatrixBase() {

    // ---- Multiple choice ------------------------------------------------
    private fun multipleChoice(fontScale: Float) {
        render(fontScale) {
            MultipleChoicePuzzleScreen(
                puzzleId = "p1", question = "Which of these words is a synonym of 'happy'?",
                difficulty = "Easy", options = listOf("Joyful", "Sad", "Angry", "Tired"),
                selectedOption = null, correctAnswer = "Joyful", timerSeconds = 60,
                questionNumber = 1, totalQuestions = 5, onOptionSelected = {}, onContinue = {},
                onCorrectAnswer = {}, onHint = {}, onBack = {}
            )
        }
        assertOnScreen(compose.onNodeWithText("Joyful"))
        assertOnScreen(compose.onNodeWithText("Tired"))
        assertOnScreen(compose.onNodeWithText("Need a Hint?"))
        assertOnScreen(compose.onNodeWithText("00:", substring = true))
    }
    @Test @Config(qualifiers = Devices.SMALL_PHONE) fun mc_smallPhone() = multipleChoice(2f)
    @Test @Config(qualifiers = Devices.COMPACT_PHONE) fun mc_compactPhone() = multipleChoice(1.3f)
    @Test @Config(qualifiers = Devices.TYPICAL_PHONE) fun mc_typicalPhone() = multipleChoice(1f)
    @Test @Config(qualifiers = Devices.PHONE_LANDSCAPE) fun mc_phoneLandscape() = multipleChoice(1f)
    @Test @Config(qualifiers = Devices.FOLDABLE_INNER) fun mc_foldable() = multipleChoice(1f)
    @Test @Config(qualifiers = Devices.TABLET_PORTRAIT) fun mc_tabletPortrait() = multipleChoice(1f)
    @Test @Config(qualifiers = Devices.TABLET_LANDSCAPE) fun mc_tabletLandscape() = multipleChoice(1f)

    // ---- Pendulum -------------------------------------------------------
    private fun pendulum(fontScale: Float) {
        render(fontScale) {
            PendulumChoiceScreen(
                puzzleId = "p1", question = "RUN", options = listOf("Sprint", "Sleep", "Sit", "Stay"),
                correctAnswer = "Sprint", selectedOption = null, onOptionSelected = {}, onContinue = {},
                onCorrectAnswer = {}, onHint = {}, onBack = {}
            )
        }
        assertOnScreen(compose.onNodeWithText("RUN"))
        assertOnScreen(compose.onNodeWithText("Sprint"))
        assertOnScreen(compose.onNodeWithText("Stay"))
        assertOnScreen(compose.onNodeWithText("1:30"))
        assertOnScreen(compose.onNodeWithText("Hint", substring = true))
    }
    @Test @Config(qualifiers = Devices.SMALL_PHONE) fun pendulum_smallPhone() = pendulum(2f)
    @Test @Config(qualifiers = Devices.COMPACT_PHONE) fun pendulum_compactPhone() = pendulum(1.3f)
    @Test @Config(qualifiers = Devices.TYPICAL_PHONE) fun pendulum_typicalPhone() = pendulum(1f)
    @Test @Config(qualifiers = Devices.PHONE_LANDSCAPE) fun pendulum_phoneLandscape() = pendulum(1f)
    @Test @Config(qualifiers = Devices.FOLDABLE_INNER) fun pendulum_foldable() = pendulum(1f)
    @Test @Config(qualifiers = Devices.TABLET_PORTRAIT) fun pendulum_tabletPortrait() = pendulum(1f)
    @Test @Config(qualifiers = Devices.TABLET_LANDSCAPE) fun pendulum_tabletLandscape() = pendulum(1f)

    // ---- Percentage -----------------------------------------------------
    private fun percentage(fontScale: Float) {
        render(fontScale) {
            AdaptivePercentagePuzzleScreen(timer = "1:00", onSubmitAnswer = {}, fetchNextPuzzle = {}, onBack = {})
        }
        assertOnScreen(compose.onNodeWithText("Submit"))
        assertOnScreen(compose.onNodeWithText("0"))
        assertOnScreen(compose.onNodeWithText("9"))
        assertOnScreen(compose.onNodeWithText("1"))
        assertOnScreen(compose.onNodeWithText("OF"))
        assertOnScreen(compose.onNodeWithContentDescription("Back"))
    }
    @Test @Config(qualifiers = Devices.SMALL_PHONE) fun percentage_smallPhone() = percentage(2f)
    @Test @Config(qualifiers = Devices.COMPACT_PHONE) fun percentage_compactPhone() = percentage(1.3f)
    @Test @Config(qualifiers = Devices.TYPICAL_PHONE) fun percentage_typicalPhone() = percentage(1f)
    @Test @Config(qualifiers = Devices.PHONE_LANDSCAPE) fun percentage_phoneLandscape() = percentage(1f)
    @Test @Config(qualifiers = Devices.FOLDABLE_INNER) fun percentage_foldable() = percentage(1f)
    @Test @Config(qualifiers = Devices.TABLET_PORTRAIT) fun percentage_tabletPortrait() = percentage(1f)
    @Test @Config(qualifiers = Devices.TABLET_LANDSCAPE) fun percentage_tabletLandscape() = percentage(1f)

    // ---- Swipe word -----------------------------------------------------
    private fun swipeWord(fontScale: Float) {
        render(fontScale) {
            SwipeWordScreen(
                questionData = WordConnotationQuestion(
                    topic = "Feelings", positiveWords = listOf("Zzzpositive"), negativeWords = emptyList(),
                    hint = "Think about how the word feels"
                )
            )
        }
        assertOnScreen(compose.onNodeWithText("Zzzpositive"))
        assertOnScreen(compose.onNodeWithText("SWIPE LEFT"))
        assertOnScreen(compose.onNodeWithText("SWIPE RIGHT"))
        assertOnScreen(compose.onNodeWithText("s", substring = true).let { compose.onNodeWithText("Think about", substring = true) })
        assertOnScreen(compose.onNodeWithText("Time", substring = true))
    }
    @Test @Config(qualifiers = Devices.SMALL_PHONE) fun swipe_smallPhone() = swipeWord(2f)
    @Test @Config(qualifiers = Devices.COMPACT_PHONE) fun swipe_compactPhone() = swipeWord(1.3f)
    @Test @Config(qualifiers = Devices.TYPICAL_PHONE) fun swipe_typicalPhone() = swipeWord(1f)
    @Test @Config(qualifiers = Devices.PHONE_LANDSCAPE) fun swipe_phoneLandscape() = swipeWord(1f)
    @Test @Config(qualifiers = Devices.FOLDABLE_INNER) fun swipe_foldable() = swipeWord(1f)
    @Test @Config(qualifiers = Devices.TABLET_PORTRAIT) fun swipe_tabletPortrait() = swipeWord(1f)
    @Test @Config(qualifiers = Devices.TABLET_LANDSCAPE) fun swipe_tabletLandscape() = swipeWord(1f)

    // ---- Antonym balloons -----------------------------------------------
    private fun antonym(fontScale: Float) {
        render(fontScale) {
            AntonymBalloonPuzzleScreen(
                difficulty = "Medium", timer = "2:00", hearts = 3, level = "1/5",
                puzzleData = """{"pairs":[{"word1":"hot","word2":"cold"},{"word1":"big","word2":"small"},{"word1":"happy","word2":"sad"},{"word1":"fast","word2":"slow"}]}""",
                onSubmitAnswer = {}, fetchNextPuzzle = {}, onBack = {}
            )
        }
        compose.waitUntil(5000) { compose.onAllNodesWithText("hot").fetchSemanticsNodes().isNotEmpty() }
        for (w in listOf("hot", "cold", "big", "small", "happy", "sad", "fast", "slow")) assertOnScreen(compose.onNodeWithText(w))
        assertOnScreen(compose.onNodeWithText("2:0", substring = true))
        assertOnScreen(compose.onNodeWithText("0 / 4", substring = true))
        assertOnScreen(compose.onNodeWithContentDescription("Back"))
    }
    @Test @Config(qualifiers = Devices.SMALL_PHONE) fun antonym_smallPhone() = antonym(2f)
    @Test @Config(qualifiers = Devices.COMPACT_PHONE) fun antonym_compactPhone() = antonym(1.3f)
    @Test @Config(qualifiers = Devices.TYPICAL_PHONE) fun antonym_typicalPhone() = antonym(1f)
    @Test @Config(qualifiers = Devices.PHONE_LANDSCAPE) fun antonym_phoneLandscape() = antonym(1f)
    @Test @Config(qualifiers = Devices.FOLDABLE_INNER) fun antonym_foldable() = antonym(1f)
    @Test @Config(qualifiers = Devices.TABLET_PORTRAIT) fun antonym_tabletPortrait() = antonym(1f)
    @Test @Config(qualifiers = Devices.TABLET_LANDSCAPE) fun antonym_tabletLandscape() = antonym(1f)

    // ---- Unique object ---------------------------------------------------
    private fun uniqueObject(fontScale: Float) {
        val objs = (0 until 12).joinToString(",") { i -> if (i == 5) """{"shape":1,"color":1}""" else """{"shape":0,"color":0}""" }
        render(fontScale) {
            UniqueObjectPuzzleScreen(
                difficulty = "Easy", timer = "1:30", hearts = 3, level = "1/5",
                puzzleData = """{"objects":[$objs],"totalObjects":12,"instruction":"Tap the one that is different",
                    "shapeMappings":{"0":"circle","1":"square"},"colorMappings":{"0":"red","1":"blue"},
                    "layout":{"grid":{"rows":3,"cols":4,"totalCells":12}}}""",
                onSubmitAnswer = {}, fetchNextPuzzle = {}, onBack = {}
            )
        }
        for (i in 0 until 12) assertOnScreen(compose.onNodeWithTag("unique_object_$i"))
        assertOnScreen(compose.onNodeWithText("Tap the one that is different"))
        assertOnScreen(compose.onNodeWithText("1:", substring = true))
        assertOnScreen(compose.onNodeWithContentDescription("Back"))
    }
    @Test @Config(qualifiers = Devices.SMALL_PHONE) fun unique_smallPhone() = uniqueObject(2f)
    @Test @Config(qualifiers = Devices.COMPACT_PHONE) fun unique_compactPhone() = uniqueObject(1.3f)
    @Test @Config(qualifiers = Devices.TYPICAL_PHONE) fun unique_typicalPhone() = uniqueObject(1f)
    @Test @Config(qualifiers = Devices.PHONE_LANDSCAPE) fun unique_phoneLandscape() = uniqueObject(1f)
    @Test @Config(qualifiers = Devices.FOLDABLE_INNER) fun unique_foldable() = uniqueObject(1f)
    @Test @Config(qualifiers = Devices.TABLET_PORTRAIT) fun unique_tabletPortrait() = uniqueObject(1f)
    @Test @Config(qualifiers = Devices.TABLET_LANDSCAPE) fun unique_tabletLandscape() = uniqueObject(1f)

    // ---- Memory sequencing (intro, audio, sequence phases) ---------------
    private fun sequencing(fontScale: Float) {
        val items = (1..6).joinToString(",") { i ->
            """{"id":"i$i","name":"Event number $i happens","partNumber":${if (i <= 3) 1 else 2},"correctOrder":$i}"""
        }
        render(fontScale) {
            SequencingPuzzleScreen(
                difficulty = "Medium", timer = "2:00", hearts = 3, level = "1/5",
                puzzleData = """{"topic":"Morning routine","description":"A short story about waking up and getting ready for the day.","items":[$items]}""",
                correctAnswer = "[]", onSubmitAnswer = {}, fetchNextPuzzle = {}, onBack = {}
            )
        }
        assertOnScreen(compose.onNodeWithText("Begin", ignoreCase = true))
        assertOnScreen(compose.onNodeWithText("REQUIRES AUDIO", substring = true))
        assertOnScreen(compose.onNodeWithContentDescription("Back"))
        compose.onNodeWithText("Begin", ignoreCase = true).performClick()
        compose.mainClock.advanceTimeBy(1500)
        assertOnScreen(compose.onNodeWithText("Morning routine"))
        assertOnScreen(compose.onNodeWithText("LISTEN", substring = true))
        compose.mainClock.advanceTimeBy(7000)
        compose.waitForIdle()
        assertOnScreen(compose.onNodeWithTag("sequence_list"))
        assertOnScreen(compose.onNodeWithText("Submit"))
        assertOnScreen(compose.onNodeWithText("2:", substring = true).let { compose.onNodeWithText("Level", substring = true) })
        for (i in 1..3) assertOnScreen(compose.onNodeWithText("Event number $i happens"))
    }
    @Test @Config(qualifiers = Devices.SMALL_PHONE) fun seq_smallPhone() = sequencing(2f)
    @Test @Config(qualifiers = Devices.COMPACT_PHONE) fun seq_compactPhone() = sequencing(1.3f)
    @Test @Config(qualifiers = Devices.TYPICAL_PHONE) fun seq_typicalPhone() = sequencing(1f)
    @Test @Config(qualifiers = Devices.PHONE_LANDSCAPE) fun seq_phoneLandscape() = sequencing(1f)
    @Test @Config(qualifiers = Devices.FOLDABLE_INNER) fun seq_foldable() = sequencing(1f)
    @Test @Config(qualifiers = Devices.TABLET_PORTRAIT) fun seq_tabletPortrait() = sequencing(1f)
    @Test @Config(qualifiers = Devices.TABLET_LANDSCAPE) fun seq_tabletLandscape() = sequencing(1f)

    /** Node must be fully inside the window (not clipped). */
    private fun assertInsideRoot(node: androidx.compose.ui.test.SemanticsNodeInteraction) {
        val root = compose.onRoot().getBoundsInRoot()
        val b = node.getBoundsInRoot()
        assert(b.left >= root.left - 0.5.dp && b.top >= root.top - 0.5.dp &&
            b.right <= root.right + 0.5.dp && b.bottom <= root.bottom + 0.5.dp) { "clipped: $b not inside $root" }
    }

    // ---- Word search -----------------------------------------------------
    private fun wordSearch(fontScale: Float) {
        val letters = "ABCDEFGHIJKLMNOPQRSTUVWXYZ"
        val n = 10
        val matrix = (0 until n).joinToString(",") { y -> "[" + (0 until n).joinToString(",") { x -> "\"${letters[(x + y * 3) % 26]}\"" } + "]" }
        val words = listOf("APPLE", "BANANA", "CHERRY", "GRAPE", "LEMON", "MANGO").joinToString(",") {
            """{"word":"$it","hint":"a fruit","direction":"horizontal","length":${it.length}}"""
        }
        render(fontScale) {
            WordSearchPuzzleScreen(
                difficulty = "Easy", timer = "5:00",
                puzzleData = """{"matrix":[$matrix],"words":[$words],"width":$n,"height":$n}""",
                onSubmitAnswer = {}, fetchNextPuzzle = {}, onBack = {}
            )
        }
        assertOnScreen(compose.onNodeWithTag("word_grid"))
        assertInsideRoot(compose.onNodeWithTag("word_grid"))
        assertOnScreen(compose.onNodeWithText("5:0", substring = true))
        for (w in listOf("APPLE", "BANANA", "CHERRY", "GRAPE", "LEMON", "MANGO")) assertOnScreen(compose.onNodeWithText(w))
        assertOnScreen(compose.onNodeWithContentDescription("Back"))
        assertOnScreen(compose.onNodeWithContentDescription("Hint"))
    }
    @Test @Config(qualifiers = Devices.SMALL_PHONE) fun wordSearch_smallPhone() = wordSearch(2f)
    @Test @Config(qualifiers = Devices.COMPACT_PHONE) fun wordSearch_compactPhone() = wordSearch(1.3f)
    @Test @Config(qualifiers = Devices.TYPICAL_PHONE) fun wordSearch_typicalPhone() = wordSearch(1f)
    @Test @Config(qualifiers = Devices.PHONE_LANDSCAPE) fun wordSearch_phoneLandscape() = wordSearch(1f)
    @Test @Config(qualifiers = Devices.FOLDABLE_INNER) fun wordSearch_foldable() = wordSearch(1f)
    @Test @Config(qualifiers = Devices.TABLET_PORTRAIT) fun wordSearch_tabletPortrait() = wordSearch(1f)
    @Test @Config(qualifiers = Devices.TABLET_LANDSCAPE) fun wordSearch_tabletLandscape() = wordSearch(1f)

    // ---- Multi-match music -----------------------------------------------
    private fun multiMatch(fontScale: Float) {
        val qs = (1..6).map { i ->
            MusicQuestion(i, "", "What song is this?", "Song $i", listOf("Song $i"), "", MusicMetadata(0, 30, "", "A", "T"))
        }
        render(fontScale) {
            MultiMatchMusicPuzzleScreen(
                puzzleData = MultiMatchMusicData("p1", "Pop hits", "Match songs", qs, 6, 360000L,
                    MusicGameSettings(true, true, false, true, 30)),
                difficulty = "Easy", round = "1/5", onPuzzleComplete = { _, _ -> }, onBack = {}
            )
        }
        compose.waitForIdle()
        assertOnScreen(compose.onNodeWithText("Answer"))
        assertInsideRoot(compose.onNodeWithText("Answer"))
        assertOnScreen(compose.onNodeWithText("6:00"))
        assertOnScreen(compose.onNodeWithContentDescription("Back"))
        for (i in 1..6) {
            assertInsideRoot(compose.onNodeWithTag("answer_pill_Song $i"))
            assertInsideRoot(compose.onNodeWithTag("question_circle_$i"))
        }
    }
    @Test @Config(qualifiers = Devices.SMALL_PHONE) fun multiMatch_smallPhone() = multiMatch(2f)
    @Test @Config(qualifiers = Devices.COMPACT_PHONE) fun multiMatch_compactPhone() = multiMatch(1.3f)
    @Test @Config(qualifiers = Devices.TYPICAL_PHONE) fun multiMatch_typicalPhone() = multiMatch(1f)
    @Test @Config(qualifiers = Devices.PHONE_LANDSCAPE) fun multiMatch_phoneLandscape() = multiMatch(1f)
    @Test @Config(qualifiers = Devices.FOLDABLE_INNER) fun multiMatch_foldable() = multiMatch(1f)
    @Test @Config(qualifiers = Devices.TABLET_PORTRAIT) fun multiMatch_tabletPortrait() = multiMatch(1f)
    @Test @Config(qualifiers = Devices.TABLET_LANDSCAPE) fun multiMatch_tabletLandscape() = multiMatch(1f)

    // ---- Geography (cities + countries): instructions, continent pick, precise placement ----
    private fun geography(fontScale: Float, countries: Boolean) {
        render(fontScale) {
            if (!countries) GeographyDragDropPuzzleScreen("Easy", "2:00", 3, "1/5", "{}", "{}", {}, {}, {})
            else GeographyCountryDragDropPuzzleScreen("Easy", "2:00", 3, "1/5", "{}", "{}", {}, {}, {})
        }
        assertOnScreen(compose.onNodeWithText("START CHALLENGE"))
        compose.onNodeWithText("START CHALLENGE").performClick()
        compose.waitForIdle()
        assertOnScreen(compose.onNodeWithTag("continent_map"))
        assertInsideRoot(compose.onNodeWithTag("continent_map"))
        for (c in Continent.values()) assertOnScreen(compose.onAllNodesWithText(c.displayName).onFirst())
        assertOnScreen(compose.onNodeWithText("TIME", substring = true))
        assertOnScreen(compose.onNodeWithText("SCORE", substring = true))
        assertOnScreen(compose.onNodeWithContentDescription("Back"))
        val q = compose.onNodeWithText("Which continent is", substring = true)
            .fetchSemanticsNode().config[androidx.compose.ui.semantics.SemanticsProperties.Text].first().text
        val name = q.removePrefix("Which continent is ").removeSuffix(" located in?")
        val continent = if (!countries) GeographyCityDatabase.cities.first { it.name == name }.continent
        else GeographyCountryDatabase.countries.first { it.name == name }.continent
        compose.onAllNodesWithContentDescription(continent.displayName).onFirst().performClick()
        compose.waitForIdle()
        // The stage transition runs on a real CoroutineScope(Dispatchers.Main).launch { delay(...) },
        // not a LaunchedEffect on the compose test clock, so advance Robolectric's main looper clock.
        shadowOf(android.os.Looper.getMainLooper()).idleFor(Duration.ofMillis(2500))
        compose.waitForIdle()
        assertOnScreen(compose.onNodeWithTag("placement_board"))
        assertInsideRoot(compose.onNodeWithTag("placement_board"))
        assertOnScreen(compose.onNodeWithText("TIME", substring = true))
        if (countries) {
            assertOnScreen(compose.onNodeWithText("Submit"))
            assertInsideRoot(compose.onNodeWithText("Submit"))
        }
    }
    @Test @Config(qualifiers = Devices.SMALL_PHONE) fun cities_smallPhone() = geography(2f, false)
    @Test @Config(qualifiers = Devices.COMPACT_PHONE) fun cities_compactPhone() = geography(1.3f, false)
    @Test @Config(qualifiers = Devices.TYPICAL_PHONE) fun cities_typicalPhone() = geography(1f, false)
    @Test @Config(qualifiers = Devices.PHONE_LANDSCAPE) fun cities_phoneLandscape() = geography(1f, false)
    @Test @Config(qualifiers = Devices.FOLDABLE_INNER) fun cities_foldable() = geography(1f, false)
    @Test @Config(qualifiers = Devices.TABLET_PORTRAIT) fun cities_tabletPortrait() = geography(1f, false)
    @Test @Config(qualifiers = Devices.TABLET_LANDSCAPE) fun cities_tabletLandscape() = geography(1f, false)
    @Test @Config(qualifiers = Devices.SMALL_PHONE) fun countries_smallPhone() = geography(2f, true)
    @Test @Config(qualifiers = Devices.COMPACT_PHONE) fun countries_compactPhone() = geography(1.3f, true)
    @Test @Config(qualifiers = Devices.TYPICAL_PHONE) fun countries_typicalPhone() = geography(1f, true)
    @Test @Config(qualifiers = Devices.PHONE_LANDSCAPE) fun countries_phoneLandscape() = geography(1f, true)
    @Test @Config(qualifiers = Devices.FOLDABLE_INNER) fun countries_foldable() = geography(1f, true)
    @Test @Config(qualifiers = Devices.TABLET_PORTRAIT) fun countries_tabletPortrait() = geography(1f, true)
    @Test @Config(qualifiers = Devices.TABLET_LANDSCAPE) fun countries_tabletLandscape() = geography(1f, true)

    // ---- Find object -------------------------------------------------------
    private fun findObject(fontScale: Float) {
        val obj = DiscoveredObject("o1", "Cup", 30.0, 40.0, 10.0, "easy", "hint", "d", "good", "medium", "clear", 1, 0, 1)
        val data = FindObjectPuzzleData("p1", "", "Kitchen", "d", "Find them", 3, 90000L, listOf(obj), null,
            GridConfig(3, 3, 9), ObjectGameSettings(10.0, 5, true, "s"), "easy")
        render(fontScale) {
            FindObjectPuzzleScreen(data, "Easy", "1/5", { _, _ -> }, {},
                Puzzle("p1", "findobject", "q", "a", "h", "easy"), PuzzleViewModel())
        }
        compose.waitForIdle()
        for (t in listOf("Full", "Zoom", "Objects")) assertOnScreen(compose.onNodeWithText(t))
        assertOnScreen(compose.onNodeWithContentDescription("Hint"))
        assertOnScreen(compose.onNodeWithContentDescription("Back"))
        assertOnScreen(compose.onNodeWithText("1:30"))
        assertOnScreen(compose.onNodeWithText("Found:", substring = true))
        assertInsideRoot(compose.onNodeWithText("Found:", substring = true))
        assertInsideRoot(compose.onNodeWithText("Objects"))
    }
    @Test @Config(qualifiers = Devices.SMALL_PHONE) fun findObject_smallPhone() = findObject(2f)
    @Test @Config(qualifiers = Devices.COMPACT_PHONE) fun findObject_compactPhone() = findObject(1.3f)
    @Test @Config(qualifiers = Devices.TYPICAL_PHONE) fun findObject_typicalPhone() = findObject(1f)
    @Test @Config(qualifiers = Devices.PHONE_LANDSCAPE) fun findObject_phoneLandscape() = findObject(1f)
    @Test @Config(qualifiers = Devices.FOLDABLE_INNER) fun findObject_foldable() = findObject(1f)
    @Test @Config(qualifiers = Devices.TABLET_PORTRAIT) fun findObject_tabletPortrait() = findObject(1f)
    @Test @Config(qualifiers = Devices.TABLET_LANDSCAPE) fun findObject_tabletLandscape() = findObject(1f)

    // ---- Find differences ------------------------------------------------
    private fun findDifferences(fontScale: Float) {
        val diff = DifferenceData("d1", "desc", "right", 30.0, 40.0, 10.0, "color", "hint")
        val data = FindDifferencesPuzzleData("p1", "", "Park", "Find them", 3, 90000L, listOf(diff),
            GameSettings(10.0, 5, true, "s"))
        render(fontScale) {
            FindDifferencesPuzzleScreen(data, "Easy", "1/5", { _, _ -> }, {},
                Puzzle("p1", "finddiff", "q", "a", "h", "easy"), PuzzleViewModel())
        }
        compose.waitForIdle()
        for (t in listOf("Full", "Zoom", "Zones")) assertOnScreen(compose.onNodeWithText(t))
        assertOnScreen(compose.onNodeWithContentDescription("Hint"))
        assertOnScreen(compose.onNodeWithContentDescription("Back"))
        assertOnScreen(compose.onNodeWithText("1:30"))
        assertOnScreen(compose.onNodeWithText("Found:", substring = true))
        assertInsideRoot(compose.onNodeWithText("Found:", substring = true))
    }
    @Test @Config(qualifiers = Devices.SMALL_PHONE) fun findDiff_smallPhone() = findDifferences(2f)
    @Test @Config(qualifiers = Devices.COMPACT_PHONE) fun findDiff_compactPhone() = findDifferences(1.3f)
    @Test @Config(qualifiers = Devices.TYPICAL_PHONE) fun findDiff_typicalPhone() = findDifferences(1f)
    @Test @Config(qualifiers = Devices.PHONE_LANDSCAPE) fun findDiff_phoneLandscape() = findDifferences(1f)
    @Test @Config(qualifiers = Devices.FOLDABLE_INNER) fun findDiff_foldable() = findDifferences(1f)
    @Test @Config(qualifiers = Devices.TABLET_PORTRAIT) fun findDiff_tabletPortrait() = findDifferences(1f)
    @Test @Config(qualifiers = Devices.TABLET_LANDSCAPE) fun findDiff_tabletLandscape() = findDifferences(1f)

    // ---- Waldo -------------------------------------------------------------
    private fun waldo(fontScale: Float) {
        val hidden = WaldoHiddenObject(1, "Key", "d", 1, 0, 1, 30f, 40f, "good", "loc", "hint", "easy", null)
        val data = WaldoPuzzleData("p1", "Beach", "Find hidden things", "", 1024, 1024, WaldoGridConfig(3, 3, 9),
            listOf(hidden), 1, 90000L, WaldoGameSettings(true, true, true, true), "ok", "ai")
        render(fontScale) { WaldoPuzzleScreen(data, "Easy", "1/5", { _, _ -> }, {}) }
        compose.waitForIdle()
        for (t in listOf("Image", "Objects")) assertOnScreen(compose.onNodeWithText(t))
        assertOnScreen(compose.onNodeWithContentDescription("Hint"))
        assertOnScreen(compose.onNodeWithContentDescription("Back"))
        assertOnScreen(compose.onNodeWithText("Beach"))
        assertOnScreen(compose.onNodeWithText("1:30"))
        assertOnScreen(compose.onNodeWithText("Found:", substring = true))
        assertInsideRoot(compose.onNodeWithText("Found:", substring = true))
    }
    @Test @Config(qualifiers = Devices.SMALL_PHONE) fun waldo_smallPhone() = waldo(2f)
    @Test @Config(qualifiers = Devices.COMPACT_PHONE) fun waldo_compactPhone() = waldo(1.3f)
    @Test @Config(qualifiers = Devices.TYPICAL_PHONE) fun waldo_typicalPhone() = waldo(1f)
    @Test @Config(qualifiers = Devices.PHONE_LANDSCAPE) fun waldo_phoneLandscape() = waldo(1f)
    @Test @Config(qualifiers = Devices.FOLDABLE_INNER) fun waldo_foldable() = waldo(1f)
    @Test @Config(qualifiers = Devices.TABLET_PORTRAIT) fun waldo_tabletPortrait() = waldo(1f)
    @Test @Config(qualifiers = Devices.TABLET_LANDSCAPE) fun waldo_tabletLandscape() = waldo(1f)

    // ---- Image questions (study phase + answering phase) -------------------
    private fun imageQuestion(fontScale: Float) {
        val qs = listOf(ImageQuestion(1, "What colour was the car in the picture?", "Red", "color",
            "easy", listOf("Red", "Blue", "Green", "Yellow"), "It was bright"))
        render(fontScale) {
            Column {
                SimpleImageQuestionHeader("Easy", "1/5", GamePhase.ANSWERING_QUESTIONS, 0, 90L, 1, 1, 0, {})
                Box(Modifier.weight(1f)) {
                    AnsweringQuestionsPhase(qs, 0, emptyList(), "", "Street", false, 0, false, {}, {}, {})
                }
                ProgressIndicator(0, 1, 0, 0, Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
            }
        }
        compose.waitForIdle()
        for (o in listOf("Red", "Blue", "Green", "Yellow")) {
            assertOnScreen(compose.onNodeWithText(o))
            assertInsideRoot(compose.onNodeWithText(o))
        }
        assertOnScreen(compose.onNodeWithText("What colour", substring = true))
        assertOnScreen(compose.onNodeWithText("Reveal Image", substring = true))
        assertOnScreen(compose.onNodeWithText("Progress:", substring = true))
        assertOnScreen(compose.onNodeWithContentDescription("Back"))
    }
    @Test @Config(qualifiers = Devices.SMALL_PHONE) fun imageQuestion_smallPhone() = imageQuestion(2f)
    @Test @Config(qualifiers = Devices.COMPACT_PHONE) fun imageQuestion_compactPhone() = imageQuestion(1.3f)
    @Test @Config(qualifiers = Devices.TYPICAL_PHONE) fun imageQuestion_typicalPhone() = imageQuestion(1f)
    @Test @Config(qualifiers = Devices.PHONE_LANDSCAPE) fun imageQuestion_phoneLandscape() = imageQuestion(1f)
    @Test @Config(qualifiers = Devices.FOLDABLE_INNER) fun imageQuestion_foldable() = imageQuestion(1f)
    @Test @Config(qualifiers = Devices.TABLET_PORTRAIT) fun imageQuestion_tabletPortrait() = imageQuestion(1f)
    @Test @Config(qualifiers = Devices.TABLET_LANDSCAPE) fun imageQuestion_tabletLandscape() = imageQuestion(1f)
}
