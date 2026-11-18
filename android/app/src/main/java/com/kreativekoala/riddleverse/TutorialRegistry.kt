package com.kreativekoala.riddleverse

/**
 * One answer to "does this game have a tutorial, and under which key is it dispatched?".
 *
 * Tutorials are dispatched by a `when` in StartPuzzleScreen (first launch per game, "?" icon, banner) and in
 * DailyPuzzleActivity. The home catalog passes `QuizCategory.category` as the type, and for several older games
 * that key differs from the key the tutorial was registered under. [dispatchKey] maps one to the other, and
 * [hasTutorial] is the single gate used by the auto-show check, the "?" icon and the tutorial banner, so they can no
 * longer disagree. `TutorialRegistryTest` fails when a catalog game has no tutorial.
 */
object TutorialRegistry {
    /** Catalog or custom-format key -> the key its tutorial is registered under in StartPuzzleScreen's `when`. */
    internal val ALIASES: Map<String, String> = mapOf(
        "average" to "averages",
        "percentages" to "percentage",
        "mathtipping" to "tipbubble",
        "anagram" to "jumbleinput",
        "connotationwords" to "swipeword",
        "geography_cities" to "geographycity",
        "geography_countries" to "geographycountry",
        "find_object" to "findobject",
        "find_differences" to "finddifferences",
        "musicidentification" to "multimatchmusic",
        "colormatching" to "colorshapematching",
        "progressiverevelation" to "progressivereveal",
        // Quiz-style games play on the multiple-choice screens, so they share its card.
        "math" to "multiplechoice",
        "trivia" to "multiplechoice",
        "storypuzzle" to "multiplechoice",
        "q&a" to "qa",
        "🖼️ image puzzle" to "imagepuzzle",
    )

    /** Dispatch keys whose tutorial card lives in SimpleTutorialScreen.kt or tutorials/ (the literal branches of the `when`). */
    internal val LEGACY_KEYS: Set<String> = setOf(
        // Interactive tutorials
        "crypto", "wordsnake", "division", "subtraction",
        "antonyms", "antonymballoon", "memorysquares", "synonyms",
        "mathestimation", "discounts", "conversion", "crossword",
        "pinballdeflector", "imagepuzzle",
        // Single-screen cards: math
        "numbersum", "averages", "percentage", "mathcomparison", "mathexpression", "mathcrossword", "tipbubble", "purchasing",
        // memory
        "memorystory", "memorysequencing", "memoryretention",
        "memoryprevioussingle", "memorypreviouspair", "triangledotmemory",
        // word / language
        "wordsearch", "swipeword", "jumbleinput", "wordprefix", "letterset", "sentencetransitions",
        // matching, pattern, visual
        "symbolswipe", "symmetry", "colortextmatching", "numbersequence", "colorshapematching", "imagematch", "match",
        "imagevortex", "imagequestion", "finddifferences", "findobject",
        "waldopuzzle", "uniqueobject", "progressivereveal", "realorai",
        // logic and other
        "flowpuzzle", "contextswitch", "dualcard", "multimatchmusic",
        "geographycity", "geographycountry",
        // basic types
        "qa", "multiplechoice",
    )

    private fun isGroupType(key: String): Boolean =
        WordPuzzleRegistry.isWordPuzzleType(key) || ClassicPuzzles.isClassic(key) ||
            SpatialPuzzles.isSpatial(key) || GridPuzzleFactory.isGridType(key)

    /**
     * The key to switch on when picking a tutorial: registry-group games (word, classic, spatial, grid) keep their own
     * key, aliased games map to their tutorial's key, everything else is normalised (case, spaces, underscores).
     */
    fun dispatchKey(type: String?): String {
        val lower = type.orEmpty().trim().lowercase()
        if (isGroupType(lower)) return lower
        ALIASES[lower]?.let { return it }
        val normalised = lower.replace(" ", "").replace("_", "")
        ALIASES[normalised]?.let { return it }
        return if (normalised in LEGACY_KEYS) normalised else lower
    }

    /** True when a tutorial exists for [type] (the key a catalog card or a custom puzzle format passes). */
    fun hasTutorial(type: String?): Boolean {
        val key = dispatchKey(type)
        return key in LEGACY_KEYS || isGroupType(key)
    }
}
