package com.kreativekoala.riddleverse

import org.junit.Assert.*
import org.junit.Test

class HomeDiscoveryLogicTest {
    private fun g(type: String, filter: String = "English", isNew: Boolean = false) = GameRef(type, type.replaceFirstChar { it.uppercase() }, filter, isNew)

    private val registry = listOf(
        g("wordprefix"), g("anagram"), g("synonyms"), g("antonyms"), g("letterset"), g("wordsearch"),
        g("colormatching", "Focus"), g("uniqueobject", "Focus"), g("numbersequence", "Logic"), g("symbolswipe", "Reaction"),
        g("trivia", "General"), g("dualcard", "Logic"),
        g("math", "Math"), g("memoryprevioussingle", "Memory"), g("memorysquares", "Memory"),
        g("sudoku", "Logic"), g("wordhive")
    )
    private val similar = mapOf("anagram" to listOf("wordprefix", "synonyms", "letterset"), "sudoku" to listOf("numbersequence"))
    private fun rank(history: List<PlayRecord>, limit: Int = 8) =
        ForYouRanking.rank(history, registry, { similar[it] ?: emptyList() }, limit)

    // ---- cold start ----

    @Test fun coldStartFollowsQuickWinOrderAndIsFullFromRegistry() {
        val c = ForYouRanking.coldStart(registry)
        assertEquals(listOf("wordprefix", "anagram", "colormatching", "synonyms", "antonyms", "letterset", "wordsearch", "uniqueobject"), c.map { it.game.type })
        assertTrue(c.all { it.reason == ForYouReason.QUICK_START })
    }

    @Test fun coldStartNeverContainsMathOrMemory() {
        val all = ForYouRanking.coldStart(registry, limit = 50).map { it.game.type }
        assertFalse(all.any { it == "math" || it.startsWith("memory") })
        // and not even when a triaged game is put first in the cold-start list's reach
        assertTrue(ForYouRanking.COLD_START.none { it.startsWith("math") || it.startsWith("memory") })
    }

    @Test fun coldStartSkipsTypesMissingFromRegistry() {
        val tiny = listOf(g("anagram"), g("trivia", "General"))
        assertEquals(listOf("anagram", "trivia"), ForYouRanking.coldStart(tiny).map { it.game.type })
    }

    @Test fun emptyRegistryGivesEmptyRow() {
        assertTrue(ForYouRanking.coldStart(emptyList()).isEmpty())
    }

    // ---- ranking with history ----

    @Test fun mostRecentIsFirstThenSimilarThenEasyWinners() {
        val h = listOf(
            PlayRecord("anagram", plays = 3, wins = 1, lastPlayed = 1_000),            // most recent
            PlayRecord("colormatching", plays = 5, wins = 5, lastPlayed = 500),        // easy winner
            PlayRecord("trivia", plays = 4, wins = 3, lastPlayed = 400)                // 75%
        )
        val r = rank(h)
        assertEquals("anagram", r[0].game.type); assertEquals(ForYouReason.CONTINUE, r[0].reason)
        assertEquals(listOf("wordprefix", "synonyms", "letterset"), r.subList(1, 4).map { it.game.type })
        assertTrue(r.subList(1, 4).all { it.reason == ForYouReason.SIMILAR && it.basis == "Anagram" })
        val winners = r.filter { it.reason == ForYouReason.EASY_WIN }.map { it.game.type }
        assertEquals(listOf("colormatching", "trivia"), winners)   // best win rate first
    }

    @Test fun sameFamilySiblingsFollowRecommendedSimilar() {
        val r = rank(listOf(PlayRecord("anagram", 1, 0, 10)))
        val sim = r.filter { it.reason == ForYouReason.SIMILAR }.map { it.game.type }
        // recommended three first, then at most two other English games from the registry
        assertEquals(listOf("wordprefix", "synonyms", "letterset", "antonyms", "wordsearch"), sim)
    }

    @Test fun picksAreUniqueAndLimited() {
        val r = rank(listOf(PlayRecord("anagram", 5, 5, 10)), limit = 4)
        assertEquals(4, r.size)
        assertEquals(r.size, r.map { it.game.type }.distinct().size)
    }

    @Test fun triagedGamesAreNeverPromotedEvenIfMostRecentOrBest() {
        val h = listOf(
            PlayRecord("memoryprevioussingle", 9, 9, lastPlayed = 9_999),   // most recent, perfect, but triaged
            PlayRecord("math", 9, 9, lastPlayed = 9_000),
            PlayRecord("sudoku", 2, 1, lastPlayed = 100)
        )
        val r = rank(h, limit = 50)
        assertFalse(r.any { it.game.type == "math" || it.game.type.startsWith("memory") })
        assertEquals("sudoku", r[0].game.type)   // most recent non-triaged game
        assertEquals(ForYouReason.CONTINUE, r[0].reason)
    }

    @Test fun easyWinnersNeedEnoughPlaysAndWinRate() {
        val h = listOf(
            PlayRecord("antonyms", plays = 1, wins = 1, lastPlayed = 5),      // too few plays
            PlayRecord("wordsearch", plays = 10, wins = 5, lastPlayed = 4),   // 50%: not easy
            PlayRecord("synonyms", plays = 3, wins = 2, lastPlayed = 3)       // 67%: easy
        )
        val easy = rank(h, limit = 50).filter { it.reason == ForYouReason.EASY_WIN }.map { it.game.type }
        assertEquals(listOf("synonyms"), easy)
    }

    @Test fun zeroPlayRecordsAreTreatedAsColdStart() {
        val r = rank(listOf(PlayRecord("anagram", 0, 0, 999)))
        assertTrue(r.all { it.reason == ForYouReason.QUICK_START })
    }

    @Test fun rowIsPaddedWithColdStartNeverPlayedFirst() {
        val r = rank(listOf(PlayRecord("wordprefix", 1, 0, 1)), limit = 8)
        val quick = r.filter { it.reason == ForYouReason.QUICK_START }.map { it.game.type }
        assertEquals("wordprefix" !in quick, true) // already shown as CONTINUE, not repeated
        assertEquals(8, r.size)
    }

    @Test fun historyOfTypesNotInRegistryIsIgnored() {
        val r = rank(listOf(PlayRecord("deletedgame", 9, 9, 99)))
        assertTrue(r.none { it.game.type == "deletedgame" })
        assertEquals("wordprefix", r[0].game.type)
    }

    // ---- New row ----

    @Test fun newRowShowsWaveInCuratedOrderAndOnlyWhatTheRegistryHas() {
        val reg = listOf(g("anagram"), g("sudoku", "Logic"), g("wordhive"), g("conduit", "Logic"))
        assertEquals(listOf("wordhive", "sudoku", "conduit"), DiscoveryCatalog.newGames(reg).map { it.type })
    }

    @Test fun gamesAddedLaterAppearFirstWithoutAnyFlag() {
        // "crowns" and "riddles" are in neither the legacy set nor the wave: picked up automatically
        val reg = listOf(g("anagram"), g("sudoku", "Logic"), g("crowns", "Logic"), g("riddles", "General"))
        val ids = DiscoveryCatalog.newGames(reg).map { it.type }
        assertEquals(listOf("riddles", "crowns", "sudoku"), ids)   // newest registry entry first
    }

    @Test fun explicitIsNewFlagPromotesALegacyType() {
        val reg = listOf(g("anagram", isNew = true), g("trivia", "General"))
        assertEquals(listOf("anagram"), DiscoveryCatalog.newGames(reg).map { it.type })
    }

    @Test fun waveHasNineteenGamesAllWithTaglinesAndArt() {
        assertEquals(19, DiscoveryCatalog.NEW_WAVE.size)
        assertEquals(19, DiscoveryCatalog.NEW_WAVE.toSet().size)
        assertTrue(DiscoveryCatalog.NEW_WAVE.all { it in DiscoveryCatalog.TAGLINES })
        assertTrue(DiscoveryCatalog.NEW_WAVE.all { it in GAME_ART_TYPES })
        assertTrue(DiscoveryCatalog.NEW_WAVE.none { it in DiscoveryCatalog.LEGACY_TYPES })
    }

    @Test fun realRegistryContainsTheWholeWave() {
        val types = QuizCategories.allCategories.map { it.category }.toSet()
        assertTrue("missing: " + (DiscoveryCatalog.NEW_WAVE - types), types.containsAll(DiscoveryCatalog.NEW_WAVE))
        // every real game the For-you cold start names exists
        assertTrue("missing: " + (ForYouRanking.COLD_START - types), types.containsAll(ForYouRanking.COLD_START))
    }

    // ---- language suggestion ----

    @Test fun suggestsWhenDeviceLanguageDiffersFromApp() {
        assertEquals("es", LanguageSuggestion.decide("es", "en", false)?.tag)
        assertEquals("Español", LanguageSuggestion.decide("es", "en", false)?.nativeName)
        assertEquals("ar", LanguageSuggestion.decide("ar", "en", false)?.tag)
        assertEquals("en", LanguageSuggestion.decide("en", "es", false)?.tag)   // app was set to Spanish on an English phone
    }

    @Test fun noSuggestionWhenSameUnsupportedOrAlreadyHandled() {
        assertNull(LanguageSuggestion.decide("es", "es", false))
        assertNull(LanguageSuggestion.decide(null, "en", false))
        assertNull(LanguageSuggestion.decide("es", "en", alreadyHandled = true))
        assertNull(LanguageSuggestion.decide("es", null, true))
    }

    @Test fun unknownCurrentLanguageStillSuggests() {
        assertEquals("fr", LanguageSuggestion.decide("fr", null, false)?.tag)
    }

    @Test fun localeMappingCoversLegacyCodesAndRegionVariants() {
        assertEquals("id", AppLanguages.supportedTagFor("in"))
        assertEquals("id", AppLanguages.supportedTagFor("id", country = "ID"))
        assertEquals("tl", AppLanguages.supportedTagFor("fil"))
        assertEquals("pt-BR", AppLanguages.supportedTagFor("pt", country = "PT"))
        assertEquals("zh-CN", AppLanguages.supportedTagFor("zh", script = "Hans", country = "CN"))
        assertEquals("zh-CN", AppLanguages.supportedTagFor("zh", country = "SG"))
        assertNull(AppLanguages.supportedTagFor("zh", country = "TW"))
        assertNull(AppLanguages.supportedTagFor("zh", script = "Hant"))
        assertEquals("es", AppLanguages.supportedTagFor("es", country = "MX"))
        assertNull(AppLanguages.supportedTagFor("sv"))
    }

    @Test fun languageListMatchesTwentyShippedLocales() {
        assertEquals(20, AppLanguages.ALL.size)   // English + the 19 translated locales
        assertEquals(20, AppLanguages.ALL.map { it.tag }.toSet().size)
        assertNotNull(AppLanguages.byTag("ar"))
    }
}
