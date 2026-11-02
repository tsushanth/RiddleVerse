package com.kreativekoala.riddleverse

/**
 * Pure (no Android) logic behind the Home discovery surfaces: the "New" row, the "For you" row
 * and the first-run language suggestion. Kept free of Compose/Context so it is unit-testable.
 */

/** Minimal view of a registry entry: [QuizCategory] mapped down to what discovery needs. */
data class GameRef(val type: String, val title: String, val filterCategory: String, val isNew: Boolean = false)

fun QuizCategory.toGameRef() = GameRef(category, title, filterCategory, isNew)

/** One entry of a player's local history (from UserStatsManager). */
data class PlayRecord(val type: String, val plays: Int, val wins: Int, val lastPlayed: Long) {
    val winRate: Float get() = if (plays > 0) wins.toFloat() / plays else 0f
}

// ---------------------------------------------------------------------------------------------
// New row
// ---------------------------------------------------------------------------------------------

object DiscoveryCatalog {
    /**
     * The 19 games of the grid/classic/spatial/word wave, best first. This order is the order of the
     * "New" row (after any games the registry flags as new, which go first).
     */
    val NEW_WAVE: List<String> = listOf(
        "wordhive", "sudoku", "tubetint", "pixellogic", "wordguess", "tilemerge", "lightflip",
        "slidetiles", "safesweep", "wordladder", "codecracker", "letterballoons", "binarygrid",
        "signgrid", "cagemath", "piecepack", "crateshift", "conduit", "disctower"
    )

    /**
     * Every registry type that existed before the new wave. A registry entry that is in neither this
     * set nor [NEW_WAVE] was added later (Crowns, Trail, Linkup, Riddles, ...) and is treated as new
     * automatically, so the New row needs no edit when another branch adds a game.
     */
    val LEGACY_TYPES: Set<String> = setOf(
        "math", "average", "division", "mathestimation", "percentages", "discounts", "purchasing",
        "conversion", "subtraction", "mathtipping", "numbersum", "mathexpression", "mathcomparison",
        "mathcrossword", "storyPuzzle", "anagram", "antonyms", "synonyms", "crossword",
        "connotationwords", "wordprefix", "wordsearch", "wordsnake", "letterset", "memorysquares",
        "memorystory", "memorysequencing", "memoryretention", "pinballdeflector",
        "memoryprevioussingle", "imagevortex", "memorypreviouspair", "triangledotmemory",
        "colortextmatching", "geography_cities", "geography_countries", "crypto", "find_object",
        "imagequestion", "waldopuzzle", "musicidentification", "imagepuzzle", "uniqueobject",
        "colormatching", "symmetry", "dualcard", "numbersequence", "symbolswipe", "contextswitch",
        "sentenceTransitions", "progressiverevelation", "realorai", "trivia", "flowpuzzle",
        "find_differences", "imagematch", "trainrouting"
    )

    /** One-line pitch for the New row. Games without an entry fall back to the registry subtitle. */
    val TAGLINES: Map<String, String> = mapOf(
        "wordhive" to "Build words from seven letters",
        "sudoku" to "Every row, column and box, once",
        "tubetint" to "Pour colours until each tube is one",
        "pixellogic" to "Number clues reveal a hidden picture",
        "wordguess" to "Find the 5-letter word in six tries",
        "tilemerge" to "Swipe, merge, reach the goal tile",
        "lightflip" to "Switch off every light",
        "slidetiles" to "Slide the tiles back in order",
        "safesweep" to "Open every safe square, dodge the mines",
        "wordladder" to "Change one letter at a time",
        "codecracker" to "Crack the hidden colour code",
        "letterballoons" to "Guess the word before the balloons pop",
        "binarygrid" to "Fill 0s and 1s, no three in a row",
        "signgrid" to "Fill the grid, obey every sign",
        "cagemath" to "Make every cage hit its target",
        "piecepack" to "Fit the pieces, clear the lines",
        "crateshift" to "Push every crate onto a target",
        "conduit" to "Rotate pipes to light every lamp",
        "disctower" to "Move the whole tower to the far peg"
    )

    /**
     * Games for the New row: registry-flagged or post-wave entries first (newest registry entry
     * first), then the [NEW_WAVE] in curated order. Only types present in [all] are returned.
     */
    fun newGames(all: List<GameRef>): List<GameRef> {
        val byType = all.associateBy { it.type }
        val wave = NEW_WAVE.toSet()
        val later = all.filter { (it.isNew || it.type !in LEGACY_TYPES) && it.type !in wave }.asReversed()
        val curated = NEW_WAVE.mapNotNull { byType[it] }
        return (later + curated).distinctBy { it.type }
    }
}

// ---------------------------------------------------------------------------------------------
// For you row
// ---------------------------------------------------------------------------------------------

enum class ForYouReason { CONTINUE, SIMILAR, EASY_WIN, QUICK_START }

/** [basis] is the display title of the game a SIMILAR pick was derived from. */
data class ForYouPick(val game: GameRef, val reason: ForYouReason, val basis: String? = null)

object ForYouRanking {
    const val DEFAULT_LIMIT = 8

    /** Wins/plays needed (with at least [MIN_PLAYS_FOR_EASY]) to count as an easy winner. */
    const val EASY_WIN_RATE = 0.6f
    const val MIN_PLAYS_FOR_EASY = 2

    /**
     * Quick-win starter list for players with no history. Chosen from finish rates in telemetry:
     * word-prefix, anagram, colour match and the other short, high-completion types. Order = priority.
     */
    val COLD_START: List<String> = listOf(
        "wordprefix", "anagram", "colormatching", "synonyms", "antonyms", "letterset",
        "wordsearch", "uniqueobject", "numbersequence", "symbolswipe", "trivia", "dualcard"
    )

    /** Math and Memory games are being triaged: never promoted by this row. */
    fun isTriaged(g: GameRef): Boolean =
        g.filterCategory == "Math" || g.filterCategory == "Memory" ||
            g.type.startsWith("math") || g.type.startsWith("memory")

    /**
     * @param history local play history; entries with zero plays are ignored
     * @param all the registry (only types in it can be returned)
     * @param similarTo "similar" game types for a type, best first (PuzzleRecommendations)
     */
    fun rank(
        history: List<PlayRecord>,
        all: List<GameRef>,
        similarTo: (String) -> List<String>,
        limit: Int = DEFAULT_LIMIT
    ): List<ForYouPick> {
        val byType = all.filter { !isTriaged(it) }.associateBy { it.type }
        val played = history.filter { it.plays > 0 && it.type in byType }
        val out = LinkedHashMap<String, ForYouPick>()
        fun add(type: String, reason: ForYouReason, basis: String? = null) {
            if (out.size >= limit || type in out) return
            val g = byType[type] ?: return
            out[type] = ForYouPick(g, reason, basis)
        }

        // 1. most recently played type (non-triaged), then 2. types similar to it
        val recent = played.maxByOrNull { it.lastPlayed }
        if (recent != null) {
            val recentGame = byType.getValue(recent.type)
            add(recent.type, ForYouReason.CONTINUE)
            similarTo(recent.type).forEach { add(it, ForYouReason.SIMILAR, recentGame.title) }
            // same-family siblings the recommendation map does not know about
            all.filter { it.filterCategory == recentGame.filterCategory }
                .forEach { add(it.type, ForYouReason.SIMILAR, recentGame.title) }
        }

        // 3. easy winners: high win rate over enough plays, best first
        played.filter { it.plays >= MIN_PLAYS_FOR_EASY && it.winRate >= EASY_WIN_RATE }
            .sortedWith(compareByDescending<PlayRecord> { it.winRate }.thenByDescending { it.plays })
            .forEach { add(it.type, ForYouReason.EASY_WIN) }

        // 4. fill with the cold-start list, never-played types first
        val playedTypes = played.map { it.type }.toSet()
        (COLD_START.filter { it !in playedTypes } + COLD_START.filter { it in playedTypes })
            .forEach { add(it, ForYouReason.QUICK_START) }

        return out.values.toList()
    }

    /** Cold start: the quick-win list, restricted to what the registry has and not triaged. */
    fun coldStart(all: List<GameRef>, limit: Int = DEFAULT_LIMIT): List<ForYouPick> =
        rank(emptyList(), all, { emptyList() }, limit)
}

// ---------------------------------------------------------------------------------------------
// Language suggestion
// ---------------------------------------------------------------------------------------------

/** A language the app ships translations for. [tag] is what setApplicationLocales takes. */
data class AppLanguage(val tag: String, val nativeName: String)

object AppLanguages {
    /** English (the default resources) plus the 19 translated locales. Keep in sync with xml/locales_config.xml. */
    val ALL: List<AppLanguage> = listOf(
        AppLanguage("en", "English"),
        AppLanguage("es", "Español"),
        AppLanguage("fr", "Français"),
        AppLanguage("de", "Deutsch"),
        AppLanguage("it", "Italiano"),
        AppLanguage("pt-BR", "Português (Brasil)"),
        AppLanguage("nl", "Nederlands"),
        AppLanguage("pl", "Polski"),
        AppLanguage("ru", "Русский"),
        AppLanguage("tr", "Türkçe"),
        AppLanguage("ar", "العربية"),
        AppLanguage("hi", "हिन्दी"),
        AppLanguage("ja", "日本語"),
        AppLanguage("ko", "한국어"),
        AppLanguage("zh-CN", "中文(简体)"),
        AppLanguage("th", "ไทย"),
        AppLanguage("vi", "Tiếng Việt"),
        AppLanguage("id", "Bahasa Indonesia"),
        AppLanguage("ms", "Bahasa Melayu"),
        AppLanguage("tl", "Filipino")
    )

    /**
     * Maps any Android locale (language, script, country) to one of the [ALL] tags, or null if the
     * app has no translation for it. Handles the legacy codes (in/iw-style, fil) and that the app
     * ships only pt-BR and zh-CN.
     */
    fun supportedTagFor(language: String, script: String = "", country: String = ""): String? {
        val lang = when (language.lowercase()) {
            "in" -> "id"
            "fil" -> "tl"
            else -> language.lowercase()
        }
        return when (lang) {
            "pt" -> "pt-BR"
            "zh" -> if (script.equals("Hant", true) || country.uppercase() in setOf("TW", "HK", "MO")) null else "zh-CN"
            else -> ALL.firstOrNull { it.tag == lang }?.tag
        }
    }

    fun byTag(tag: String?): AppLanguage? = ALL.firstOrNull { it.tag == tag }
}

object LanguageSuggestion {
    /**
     * @param deviceTag supported tag for the device locale (null if unsupported), see [AppLanguages.supportedTagFor]
     * @param currentTag supported tag of the language the app is showing now (null if unknown)
     * @param alreadyHandled the chip was shown, switched or dismissed before (one-time)
     * @return the language to offer, or null for no chip
     */
    fun decide(deviceTag: String?, currentTag: String?, alreadyHandled: Boolean): AppLanguage? {
        if (alreadyHandled || deviceTag == null) return null
        if (deviceTag == currentTag) return null
        return AppLanguages.byTag(deviceTag)
    }
}
