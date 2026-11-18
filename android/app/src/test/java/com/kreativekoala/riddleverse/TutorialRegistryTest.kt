package com.kreativekoala.riddleverse

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Completeness guard: every game in the home catalog must have a first-launch tutorial that StartPuzzleScreen can
 * actually dispatch, so a new game cannot ship without one (and the "?" icon cannot say "coming soon" for it).
 */
class TutorialRegistryTest {

    /** Catalog games that deliberately have no tutorial, with the reason. Keep empty unless a game truly needs none. */
    private val notApplicable: Map<String, String> = mapOf(
        "purchasing" to "tutorial card arrives in the next change; remove this entry then",
    )

    private val catalogKeys: List<String> by lazy { QuizCategories.allCategories.map { it.category }.distinct() }

    private val startScreenSource: String by lazy {
        val f = listOf(
            "src/main/java/com/kreativekoala/riddleverse/StartPuzzleScreen.kt",
            "android/app/src/main/java/com/kreativekoala/riddleverse/StartPuzzleScreen.kt",
        ).map { File(it) }.first { it.isFile }
        f.readText()
    }

    /** The tutorial dispatch `when` in StartPuzzleScreen, as text. */
    private val dispatchRegion: String by lazy {
        val start = startScreenSource.indexOf("when (TutorialRegistry.dispatchKey(type))")
        val end = startScreenSource.indexOf("// Existing analytics tracking", start)
        assertTrue("tutorial dispatch not found in StartPuzzleScreen", start > 0 && end > start)
        startScreenSource.substring(start, end)
    }

    private val dispatchLiterals: Set<String> by lazy {
        Regex("""^\s+((?:"[^"]+"(?:,\s*)?)+)\s*->""", RegexOption.MULTILINE).findAll(dispatchRegion)
            .flatMap { m -> Regex(""""([^"]+)"""").findAll(m.groupValues[1]).map { it.groupValues[1] } }.toSet()
    }

    @Test fun everyCatalogGameHasATutorial() {
        val missing = catalogKeys.filter { !TutorialRegistry.hasTutorial(it) && it !in notApplicable }
        assertTrue("catalog games without a tutorial (add one, or allow-list with a reason): $missing", missing.isEmpty())
    }

    @Test fun allowListIsNotStale() {
        for (k in notApplicable.keys) {
            assertTrue("$k is allow-listed but not in the catalog", k in catalogKeys)
            assertFalse("$k is allow-listed but has a tutorial; remove it from the allow-list", TutorialRegistry.hasTutorial(k))
        }
    }

    @Test fun everyCatalogTutorialIsDispatchedByStartPuzzleScreen() {
        val problems = ArrayList<String>()
        for (k in catalogKeys) {
            if (!TutorialRegistry.hasTutorial(k)) continue
            val dk = TutorialRegistry.dispatchKey(k)
            val ok = when {
                WordPuzzleRegistry.isWordPuzzleType(dk) -> "in WordPuzzleRegistry.TYPES" in dispatchRegion
                ClassicPuzzles.isClassic(dk) -> "in ClassicPuzzles.types" in dispatchRegion
                SpatialPuzzles.isSpatial(dk) -> "in SpatialPuzzles.TYPES" in dispatchRegion
                else -> dk in dispatchLiterals
            }
            if (!ok) problems += "$k -> $dk has no branch in the StartPuzzleScreen tutorial dispatch"
        }
        assertTrue(problems.joinToString("\n"), problems.isEmpty())
    }

    @Test fun everyLegacyKeyAndAliasTargetIsDispatched() {
        val targets = TutorialRegistry.LEGACY_KEYS + TutorialRegistry.ALIASES.values
        val missing = targets.filter { it !in dispatchLiterals }
        assertTrue("keys with no dispatch branch: $missing", missing.isEmpty())
    }

    @Test fun aliasesPointAtRealTutorials() {
        for ((from, to) in TutorialRegistry.ALIASES) {
            assertTrue("alias $from -> $to has no tutorial", to in TutorialRegistry.LEGACY_KEYS)
        }
    }

    @Test fun catalogKeyMismatchesThatUsedToHideTutorialsNowResolve() {
        // These catalog keys differ from the key their tutorial is registered under.
        val expected = mapOf(
            "average" to "averages", "percentages" to "percentage", "mathtipping" to "tipbubble",
            "anagram" to "jumbleinput", "connotationwords" to "swipeword", "geography_cities" to "geographycity",
            "geography_countries" to "geographycountry", "find_object" to "findobject",
            "musicidentification" to "multimatchmusic", "colormatching" to "colorshapematching",
            "progressiverevelation" to "progressivereveal", "math" to "multiplechoice", "trivia" to "multiplechoice",
            "storyPuzzle" to "multiplechoice",
        )
        for ((k, v) in expected) assertEquals(k, v, TutorialRegistry.dispatchKey(k))
    }

    @Test fun gridGamesShowTheirTutorialOnFirstLaunch() {
        for (g in GridGame.values()) assertTrue(g.key, TutorialRegistry.hasTutorial(g.key))
    }

    @Test fun helpIconAndBannerUseTheRegistry() {
        // The "?" icon and the banner used to carry their own, shorter lists of games; both must ask the registry.
        assertTrue(Regex("""TutorialRegistry\.hasTutorial\(type\) -> showTutorial = true""").findAll(startScreenSource).count() >= 2)
    }

    @Test fun dailyRotationGamesHaveTutorials() {
        for (t in DailyPuzzleSchedule.ROTATION) assertTrue(t, TutorialRegistry.hasTutorial(t))
    }

    @Test fun registryGroupsProvideContentForEveryType() {
        for (t in SpatialPuzzles.TYPES) assertNotNull("spatial $t", SpatialPuzzles.tutorialContent(t))
        for (t in ClassicPuzzles.types) assertNotNull("classic $t", ClassicPuzzles.tutorialFor(t))
        for (t in GridGame.values()) {
            assertTrue("grid rules $t", GridPuzzleFactory.rulesRes(t) != 0)
            assertTrue("grid tip $t", GridPuzzleFactory.tipRes(t) != 0)
        }
    }

    @Test fun unknownAndBlankTypesHaveNoTutorial() {
        assertFalse(TutorialRegistry.hasTutorial(""))
        assertFalse(TutorialRegistry.hasTutorial(null))
        assertFalse(TutorialRegistry.hasTutorial("daily"))
        assertFalse(TutorialRegistry.hasTutorial("custom"))
    }
}
