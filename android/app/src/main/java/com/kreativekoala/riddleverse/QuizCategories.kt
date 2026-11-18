package com.kreativekoala.riddleverse

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext

/**
 * One game card. [title] and [subtitle] are the English source text (also the fallback); never show them
 * directly: use [localizedTitle] / [localizedSubtitle] (or [titleText] / [subtitleText] in Compose), which
 * read the string resources registered in [CatalogText] for [category]. [category] and [filterCategory] are
 * wire/filter keys and must not be translated.
 */
data class QuizCategory(
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val category: String,
    val filterCategory: String,
    // home-discovery: set true on a new registry entry to put it in the Home "New" row (additive, defaulted)
    val isNew: Boolean = false,
    // optional explicit resources for entries that are not registered in CatalogText (0 = look up by [category])
    @StringRes val titleRes: Int = 0,
    @StringRes val subtitleRes: Int = 0
) {
    /** Game name in the app language. */
    fun localizedTitle(context: Context): String {
        val res = if (titleRes != 0) titleRes else CatalogText.titleRes(category)
        return if (res != 0) context.getString(res) else title
    }

    /** One-line game description in the app language. */
    fun localizedSubtitle(context: Context): String {
        val res = if (subtitleRes != 0) subtitleRes else CatalogText.subtitleRes(category)
        return if (res != 0) context.getString(res) else subtitle
    }
}

/** Game name in the app language, for Compose (recomposes when the language changes). */
@Composable
fun QuizCategory.titleText(): String {
    val context = LocalContext.current
    LocalConfiguration.current
    return localizedTitle(context)
}

/** One-line game description in the app language, for Compose. */
@Composable
fun QuizCategory.subtitleText(): String {
    val context = LocalContext.current
    LocalConfiguration.current
    return localizedSubtitle(context)
}

/**
 * String resources for everything the catalog shows, keyed by the (lower-cased) puzzle type id. The English
 * text lives in the registries ([QuizCategories], [NewGameInfo], [DiscoveryCatalog], ...) as source/fallback;
 * ids and filter keys are wire values and stay English.
 */
object CatalogText {
    private val TITLES: Map<String, Int> = mapOf(
        "math" to R.string.cat_math_title, "average" to R.string.cat_average_title,
        "division" to R.string.cat_division_title, "mathestimation" to R.string.cat_mathestimation_title,
        "percentages" to R.string.cat_percentages_title, "discounts" to R.string.cat_discounts_title,
        "purchasing" to R.string.cat_purchasing_title, "conversion" to R.string.cat_conversion_title,
        "subtraction" to R.string.cat_subtraction_title, "mathtipping" to R.string.cat_mathtipping_title,
        "numbersum" to R.string.cat_numbersum_title, "mathexpression" to R.string.cat_mathexpression_title,
        "mathcomparison" to R.string.cat_mathcomparison_title, "mathcrossword" to R.string.cat_mathcrossword_title,
        "storypuzzle" to R.string.cat_storypuzzle_title, "anagram" to R.string.cat_anagram_title,
        "antonyms" to R.string.cat_antonyms_title, "synonyms" to R.string.cat_synonyms_title,
        "crossword" to R.string.cat_crossword_title, "connotationwords" to R.string.cat_connotationwords_title,
        "wordprefix" to R.string.cat_wordprefix_title, "wordsearch" to R.string.cat_wordsearch_title,
        "wordsnake" to R.string.cat_wordsnake_title, "letterset" to R.string.cat_letterset_title,
        "memorysquares" to R.string.cat_memorysquares_title, "memorystory" to R.string.cat_memorystory_title,
        "memorysequencing" to R.string.cat_memorysequencing_title,
        "memoryretention" to R.string.cat_memoryretention_title,
        "pinballdeflector" to R.string.cat_pinballdeflector_title,
        "memoryprevioussingle" to R.string.cat_memoryprevioussingle_title,
        "imagevortex" to R.string.cat_imagevortex_title, "memorypreviouspair" to R.string.cat_memorypreviouspair_title,
        "triangledotmemory" to R.string.cat_triangledotmemory_title,
        "colortextmatching" to R.string.cat_colortextmatching_title,
        "geography_cities" to R.string.cat_geography_cities_title,
        "geography_countries" to R.string.cat_geography_countries_title, "crypto" to R.string.cat_crypto_title,
        "find_object" to R.string.cat_find_object_title, "imagequestion" to R.string.cat_imagequestion_title,
        "waldopuzzle" to R.string.cat_waldopuzzle_title,
        "musicidentification" to R.string.cat_musicidentification_title,
        "imagepuzzle" to R.string.cat_imagepuzzle_title, "uniqueobject" to R.string.cat_uniqueobject_title,
        "colormatching" to R.string.cat_colormatching_title, "symmetry" to R.string.cat_symmetry_title,
        "sudoku" to R.string.cat_sudoku_title, "binarygrid" to R.string.cat_binarygrid_title,
        "cagemath" to R.string.cat_cagemath_title, "signgrid" to R.string.cat_signgrid_title,
        "tilemerge" to R.string.cat_tilemerge_title, "slidetiles" to R.string.cat_slidetiles_title,
        "lightflip" to R.string.cat_lightflip_title, "safesweep" to R.string.cat_safesweep_title,
        "disctower" to R.string.cat_disctower_title, "dualcard" to R.string.cat_dualcard_title,
        "numbersequence" to R.string.cat_numbersequence_title, "symbolswipe" to R.string.cat_symbolswipe_title,
        "contextswitch" to R.string.cat_contextswitch_title,
        "sentencetransitions" to R.string.cat_sentencetransitions_title,
        "progressiverevelation" to R.string.cat_progressiverevelation_title, "realorai" to R.string.cat_realorai_title,
        "trivia" to R.string.cat_trivia_title, "wordguess" to R.string.cat_wordguess_title,
        "wordladder" to R.string.cat_wordladder_title, "letterballoons" to R.string.cat_letterballoons_title,
        "wordhive" to R.string.cat_wordhive_title, "codecracker" to R.string.cat_codecracker_title,
        "linkup" to R.string.cat_linkup_title, "pixellogic" to R.string.cat_pixellogic_title,
        "tubetint" to R.string.cat_tubetint_title, "piecepack" to R.string.cat_piecepack_title,
        "crateshift" to R.string.cat_crateshift_title, "conduit" to R.string.cat_conduit_title,
        "trail" to R.string.cat_trail_title, "crowns" to R.string.cat_crowns_title,
        "riddles" to R.string.cat_riddles_title
    )

    private val SUBTITLES: Map<String, Int> = mapOf(
        "math" to R.string.cat_math_subtitle, "average" to R.string.cat_average_subtitle,
        "division" to R.string.cat_division_subtitle, "mathestimation" to R.string.cat_mathestimation_subtitle,
        "percentages" to R.string.cat_percentages_subtitle, "discounts" to R.string.cat_discounts_subtitle,
        "purchasing" to R.string.cat_purchasing_subtitle, "conversion" to R.string.cat_conversion_subtitle,
        "subtraction" to R.string.cat_subtraction_subtitle, "mathtipping" to R.string.cat_mathtipping_subtitle,
        "numbersum" to R.string.cat_numbersum_subtitle, "mathexpression" to R.string.cat_mathexpression_subtitle,
        "mathcomparison" to R.string.cat_mathcomparison_subtitle,
        "mathcrossword" to R.string.cat_mathcrossword_subtitle, "storypuzzle" to R.string.cat_storypuzzle_subtitle,
        "anagram" to R.string.cat_anagram_subtitle, "antonyms" to R.string.cat_antonyms_subtitle,
        "synonyms" to R.string.cat_synonyms_subtitle, "crossword" to R.string.cat_crossword_subtitle,
        "connotationwords" to R.string.cat_connotationwords_subtitle, "wordprefix" to R.string.cat_wordprefix_subtitle,
        "wordsearch" to R.string.cat_wordsearch_subtitle, "wordsnake" to R.string.cat_wordsnake_subtitle,
        "letterset" to R.string.cat_letterset_subtitle, "memorysquares" to R.string.cat_memorysquares_subtitle,
        "memorystory" to R.string.cat_memorystory_subtitle,
        "memorysequencing" to R.string.cat_memorysequencing_subtitle,
        "memoryretention" to R.string.cat_memoryretention_subtitle,
        "pinballdeflector" to R.string.cat_pinballdeflector_subtitle,
        "memoryprevioussingle" to R.string.cat_memoryprevioussingle_subtitle,
        "imagevortex" to R.string.cat_imagevortex_subtitle,
        "memorypreviouspair" to R.string.cat_memorypreviouspair_subtitle,
        "triangledotmemory" to R.string.cat_triangledotmemory_subtitle,
        "colortextmatching" to R.string.cat_colortextmatching_subtitle,
        "geography_cities" to R.string.cat_geography_cities_subtitle,
        "geography_countries" to R.string.cat_geography_countries_subtitle, "crypto" to R.string.cat_crypto_subtitle,
        "find_object" to R.string.cat_find_object_title, "imagequestion" to R.string.cat_imagequestion_title,
        "waldopuzzle" to R.string.cat_waldopuzzle_subtitle,
        "musicidentification" to R.string.cat_musicidentification_subtitle,
        "imagepuzzle" to R.string.cat_imagepuzzle_title, "uniqueobject" to R.string.cat_uniqueobject_subtitle,
        "colormatching" to R.string.cat_colormatching_subtitle, "symmetry" to R.string.cat_symmetry_subtitle,
        "sudoku" to R.string.cat_sudoku_subtitle, "binarygrid" to R.string.cat_binarygrid_subtitle,
        "cagemath" to R.string.cat_cagemath_subtitle, "signgrid" to R.string.cat_signgrid_subtitle,
        "tilemerge" to R.string.cat_tilemerge_subtitle, "slidetiles" to R.string.cat_slidetiles_subtitle,
        "lightflip" to R.string.cat_lightflip_subtitle, "safesweep" to R.string.cat_safesweep_subtitle,
        "disctower" to R.string.cat_disctower_subtitle, "dualcard" to R.string.cat_dualcard_subtitle,
        "numbersequence" to R.string.cat_numbersequence_subtitle, "symbolswipe" to R.string.cat_symbolswipe_subtitle,
        "contextswitch" to R.string.cat_contextswitch_subtitle,
        "sentencetransitions" to R.string.cat_sentencetransitions_subtitle,
        "progressiverevelation" to R.string.cat_progressiverevelation_subtitle,
        "realorai" to R.string.cat_realorai_title, "trivia" to R.string.cat_trivia_subtitle,
        "wordguess" to R.string.cat_wordguess_subtitle, "wordladder" to R.string.cat_wordladder_subtitle,
        "letterballoons" to R.string.cat_letterballoons_subtitle, "wordhive" to R.string.cat_wordhive_subtitle,
        "codecracker" to R.string.cat_codecracker_subtitle, "linkup" to R.string.cat_linkup_subtitle,
        "pixellogic" to R.string.cat_pixellogic_subtitle, "tubetint" to R.string.cat_tubetint_subtitle,
        "piecepack" to R.string.cat_piecepack_subtitle, "crateshift" to R.string.cat_crateshift_subtitle,
        "conduit" to R.string.cat_conduit_subtitle, "trail" to R.string.cat_trail_subtitle,
        "crowns" to R.string.cat_crowns_subtitle, "riddles" to R.string.cat_riddles_subtitle
    )

    private val TAGLINES: Map<String, Int> = mapOf(
        "wordhive" to R.string.cat_wordhive_subtitle, "sudoku" to R.string.cat_sudoku_tagline,
        "tubetint" to R.string.cat_tubetint_tagline, "pixellogic" to R.string.cat_pixellogic_tagline,
        "wordguess" to R.string.cat_wordguess_tagline, "tilemerge" to R.string.cat_tilemerge_tagline,
        "lightflip" to R.string.cat_lightflip_tagline, "slidetiles" to R.string.cat_slidetiles_tagline,
        "safesweep" to R.string.cat_safesweep_tagline, "wordladder" to R.string.cat_wordladder_subtitle,
        "codecracker" to R.string.cat_codecracker_tagline, "letterballoons" to R.string.cat_letterballoons_subtitle,
        "binarygrid" to R.string.cat_binarygrid_tagline, "signgrid" to R.string.cat_signgrid_tagline,
        "cagemath" to R.string.cat_cagemath_tagline, "piecepack" to R.string.cat_piecepack_tagline,
        "crateshift" to R.string.cat_crateshift_subtitle, "conduit" to R.string.cat_conduit_tagline,
        "disctower" to R.string.cat_disctower_tagline
    )

    private val LABELS: Map<String, Int> = mapOf(
        "Logic Games" to R.string.cat_label_logic_games, "Word Games" to R.string.cat_label_word_games,
        "Memory Games" to R.string.cat_label_memory_games, "Math Games" to R.string.cat_label_math_games,
        "Visual Games" to R.string.cat_label_visual_games, "Focus Games" to R.string.cat_label_focus_games,
        "Geography Games" to R.string.cat_label_geography_games, "Reaction Games" to R.string.cat_label_reaction_games,
        "Audio Games" to R.string.cat_label_audio_games, "Puzzle Games" to R.string.cat_label_puzzle_games,
        "General" to R.string.cat_label_general
    )

    private val FILTERS: Map<String, Int> = mapOf(
        "Math" to R.string.cat_math_title, "English" to R.string.language_english,
        "Memory" to R.string.cat_filter_memory, "Visual" to R.string.cat_filter_visual,
        "Logic" to R.string.cat_filter_logic, "General" to R.string.cat_label_general,
        "Focus" to R.string.cat_filter_focus, "Geography" to R.string.cat_filter_geography,
        "Reaction" to R.string.cat_filter_reaction, "Audio" to R.string.cat_filter_audio,
        "Puzzles" to R.string.tab_puzzles, "Language" to R.string.language
    )

    private val GROUPS: Map<String, Int> = mapOf(
        "spatial_lab" to R.string.cat_group_spatial_lab_name, "visual_memory" to R.string.cat_memorysquares_title,
        "vocabulary_builder" to R.string.cat_group_vocabulary_builder_name,
        "logic_puzzles" to R.string.cat_group_logic_puzzles_name,
        "focus_training" to R.string.cat_group_focus_training_name,
        "story_memory" to R.string.cat_group_story_memory_name, "word_puzzles" to R.string.cat_group_word_puzzles_name,
        "word_games_plus" to R.string.cat_group_word_games_plus_name,
        "trivia_master" to R.string.cat_group_trivia_master_name, "basic_math" to R.string.cat_group_basic_math_name,
        "advanced_math" to R.string.cat_group_advanced_math_name,
        "practical_math" to R.string.cat_group_practical_math_name,
        "beginner_mix" to R.string.cat_group_beginner_mix_name,
        "brain_training" to R.string.cat_group_brain_training_name,
        "expert_challenge" to R.string.cat_group_expert_challenge_name,
        "speed_round" to R.string.cat_group_speed_round_name
    )

    private val EXTRAS: Map<String, Int> = mapOf(
        "wordassociation" to R.string.cat_extra_wordassociation, "imagematch" to R.string.cat_extra_imagematch,
        "find_differences" to R.string.cat_extra_find_differences, "flowpuzzle" to R.string.cat_extra_flowpuzzle,
        "multiple choice" to R.string.cat_extra_multiple_choice, "q&a" to R.string.cat_extra_q_a,
        "daily" to R.string.cat_extra_daily, "custom" to R.string.cat_extra_custom
    )

    private val BENEFITS: Map<String, List<Int>> = mapOf(
        "sudoku" to listOf(R.string.cat_sudoku_benefit_1, R.string.cat_sudoku_benefit_2),
        "binarygrid" to listOf(R.string.cat_binarygrid_benefit_1, R.string.cat_binarygrid_benefit_2),
        "cagemath" to listOf(R.string.cat_cagemath_benefit_1, R.string.cat_cagemath_benefit_2),
        "signgrid" to listOf(R.string.cat_signgrid_benefit_1, R.string.cat_signgrid_benefit_2),
        "tilemerge" to listOf(R.string.cat_tilemerge_benefit_1, R.string.cat_tilemerge_benefit_2),
        "slidetiles" to listOf(R.string.cat_slidetiles_benefit_1, R.string.cat_slidetiles_benefit_2),
        "lightflip" to listOf(R.string.cat_lightflip_benefit_1, R.string.cat_lightflip_benefit_2),
        "safesweep" to listOf(R.string.cat_safesweep_benefit_1, R.string.cat_safesweep_benefit_2),
        "disctower" to listOf(R.string.cat_disctower_benefit_1, R.string.cat_disctower_benefit_2),
        "pixellogic" to listOf(R.string.cat_pixellogic_benefit_1, R.string.cat_pixellogic_benefit_2),
        "tubetint" to listOf(R.string.cat_tubetint_benefit_1, R.string.cat_tubetint_benefit_2),
        "piecepack" to listOf(R.string.cat_piecepack_benefit_1, R.string.cat_piecepack_benefit_2),
        "crateshift" to listOf(R.string.cat_crateshift_benefit_1, R.string.cat_crateshift_benefit_2),
        "conduit" to listOf(R.string.cat_conduit_benefit_1, R.string.cat_conduit_benefit_2),
        "trail" to listOf(R.string.cat_trail_benefit_1, R.string.cat_trail_benefit_2),
        "crowns" to listOf(R.string.cat_crowns_benefit_1, R.string.cat_crowns_benefit_2),
        "wordguess" to listOf(R.string.cat_wordguess_benefit_1, R.string.cat_wordguess_benefit_2),
        "wordladder" to listOf(R.string.cat_wordladder_benefit_1, R.string.cat_wordladder_benefit_2),
        "letterballoons" to listOf(R.string.cat_letterballoons_benefit_1, R.string.cat_letterballoons_benefit_2),
        "wordhive" to listOf(R.string.cat_wordguess_benefit_1, R.string.cat_wordhive_benefit_2),
        "linkup" to listOf(R.string.cat_linkup_benefit_1, R.string.cat_linkup_benefit_2),
        "codecracker" to listOf(R.string.cat_codecracker_benefit_1, R.string.cat_codecracker_benefit_2)
    )

    @StringRes fun titleRes(type: String): Int = TITLES[type.lowercase()] ?: 0
    @StringRes fun subtitleRes(type: String): Int = SUBTITLES[type.lowercase()] ?: 0
    @StringRes fun taglineRes(type: String): Int = TAGLINES[type.lowercase()] ?: 0

    /** Skill-area label such as "Logic Games" (the English label is the key). 0 when unknown. */
    @StringRes fun labelRes(englishLabel: String): Int = LABELS[englishLabel] ?: 0

    /** Name of a Categories filter ("Math", "English", ...). 0 for non-skill filters such as "All" or "Offline". */
    @StringRes fun filterRes(filter: String): Int = FILTERS[filter] ?: 0

    /** Name of a puzzle collection by its group id. 0 when unknown. */
    @StringRes fun groupNameRes(groupId: String): Int = GROUPS[groupId] ?: 0

    /** Name of a legacy puzzle type key that has no card ("daily", "q&a", ...). 0 when unknown. */
    @StringRes fun extraNameRes(key: String): Int = EXTRAS[key.lowercase()] ?: 0

    /** The two BENEFITS bullets of a new-wave game, or null. */
    fun benefitRes(type: String): List<Int>? = BENEFITS[type.lowercase()]

    /** Name of a skill-area filter in the app language; falls back to the raw filter key. */
    fun filterName(context: Context, filter: String): String {
        val res = filterRes(filter)
        return if (res != 0) context.getString(res) else filter
    }
}

/** Name of a Categories filter ("Math", "English", ...) in the app language, for Compose. Unknown keys are returned unchanged. */
@Composable
fun categoryFilterName(filter: String): String {
    val context = LocalContext.current
    LocalConfiguration.current
    return CatalogText.filterName(context, filter)
}

object QuizCategories {
    val allCategories = listOf(
        // Math
        QuizCategory("Math", "Mathematical Challenges", Icons.Default.Calculate, "math", "Math"),
        QuizCategory("Average", "Calculate Averages", Icons.Default.BarChart, "average", "Math"),
        QuizCategory("Division", "Master Division", Icons.Default.Percent, "division", "Math"),
        QuizCategory("Estimation", "Chart Estimation", Icons.Default.TrendingUp, "mathestimation", "Math"),
        QuizCategory("Percentage", "Percentage Calculations", Icons.Default.Percent, "percentages", "Math"),
        QuizCategory("Discounts", "Price Ordering", Icons.Default.LocalOffer, "discounts", "Math"),
        QuizCategory("Purchasing", "Subscription Calculations", Icons.Default.CreditCard, "purchasing", "Math"),
        QuizCategory("Conversion", "Unit Comparisons", Icons.Default.SwapHoriz, "conversion", "Math"),
        QuizCategory("Subtraction", "Find the difference", Icons.Default.Remove, "subtraction", "Math"),
        QuizCategory("Tip Calculation", "Calculate tips", Icons.Default.RestaurantMenu, "mathtipping", "Math"),
        QuizCategory("Number Sum", "Add Numbers to Target", Icons.Default.Calculate, "numbersum", "Math"),
        QuizCategory("Math Expression", "Complete the Equations", Icons.Default.Calculate, "mathexpression", "Focus"),
        QuizCategory("Math Comparison", "Compare mathematical expressions", Icons.Default.Calculate, "mathcomparison", "Math"),
        QuizCategory("Math Crossword", "Solve intersecting math equations in crossword format", Icons.Default.GridOn, "mathcrossword", "Math"),


        // English/Language
        QuizCategory("Story Puzzle", "Narrative Mysteries", Icons.Default.MenuBook, "storyPuzzle", "English"),
        QuizCategory("Anagram", "Word Scrambles", Icons.Default.TextFields, "anagram", "English"),
        QuizCategory("Antonyms", "Match Opposite Words", Icons.Default.BubbleChart, "antonyms", "English"),
        QuizCategory("Synonyms", "Match Same Words", Icons.Default.Link, "synonyms", "English"),
        QuizCategory("Crossword", "Word Crosswords", Icons.Default.GridView, "crossword", "English"),
        QuizCategory("Word Connotations", "Sort words by tone", Icons.Default.Psychology, "connotationwords", "English"),
        QuizCategory("Word Prefixes", "Improve your vocabulary", Icons.Default.Subtitles, "wordprefix", "English"),
        QuizCategory("Word Search", "Find Words in Grid", Icons.Default.Search, "wordsearch", "English"),
        QuizCategory("Word Snake", "Trace Snake-like Word Paths", Icons.Default.Timeline, "wordsnake", "English"),
        QuizCategory("Letter Search", "Hunt for words in set of letters", Icons.Default.Search, "letterset", "English"),

        // Memory
        QuizCategory("Visual Memory", "Remember patterns", Icons.Default.GridView, "memorysquares", "Memory"),
        QuizCategory("Story Recall", "Listen and remember key items", Icons.Default.RecordVoiceOver, "memorystory", "Memory"),
        QuizCategory("Timeline Puzzle", "Sequence events in chronological order", Icons.Default.Timeline, "memorysequencing", "Memory"),
        QuizCategory("Listen & Learn", "Absorb facts and sort by categories", Icons.Default.Headphones, "memoryretention", "Memory"),
        QuizCategory("Pinball Physics", "Predict ball path through deflectors", Icons.Default.Sports, "pinballdeflector", "Memory"),
        QuizCategory("Memory Match", "Remember and compare symbols in sequence", Icons.Default.Psychology, "memoryprevioussingle", "Memory"),
        QuizCategory("Image Vortex", "Identify New Images", Icons.Default.Visibility, "imagevortex", "Memory"),
        QuizCategory("Memory Pairs", "Recall Previous Screen", Icons.Default.RememberMe, "memorypreviouspair", "Memory"),
        QuizCategory("Triangle Memory", "Pattern recognition", Icons.Default.ChangeHistory, "triangledotmemory", "Memory"),
        QuizCategory("Color Match", "Text vs meaning", Icons.Default.Palette, "colortextmatching", "Memory"),


        // Geography
        QuizCategory("City Geography", "Place cities on world map", Icons.Default.LocationCity, "geography_cities", "Geography"),
        QuizCategory("Country Geography", "Place countries on world map", Icons.Default.Public, "geography_countries", "Geography"),

        QuizCategory("Word Crypto", "Decode secret messages using number substitution", Icons.Default.Pin, "crypto", "Puzzles"),
        //QuizCategory("Find Differences", "Spot the differences", Icons.Default.Search, "find_differences", "Visual"),
        QuizCategory("Find Object", "Find Object", Icons.Default.Search, "find_object", "Visual"),

        QuizCategory("Image Questions", "Image Questions", Icons.Default.Photo, "imagequestion", "Visual"),


        QuizCategory("Find It", "Spot the hidden items in a busy scene", Icons.Default.CheckCircle, "waldopuzzle", "Visual"),

        QuizCategory("Music Match", "Multi-Match Music Quiz", Icons.Default.MusicNote, "musicidentification", "Audio"),

        QuizCategory("Image Puzzle", "Image Puzzle", Icons.Default.CheckCircle, "imagepuzzle", "Visual"),


        // Focus
        QuizCategory("Unique Object", "Find the Odd One Out", Icons.Default.VisibilityOff, "uniqueobject", "Focus"),
        QuizCategory("Color Shape Match", "Does the text match the shape color?", Icons.Default.Palette, "colormatching", "Focus"),

        QuizCategory("Symmetry", "Copy patterns from left grid to right grid", Icons.Default.GridOn, "symmetry", "Visual"),
        // --- grid puzzles (feat/puzzles-grid) ---
        QuizCategory("Sudoku", "Fill the grid so every row, column and box has each digit once", Icons.Default.GridOn, "sudoku", "Logic"),
        QuizCategory("Binary Grid", "Fill in 0s and 1s with no three in a row", Icons.Default.GridOn, "binarygrid", "Logic"),
        QuizCategory("Cage Math", "Fill the grid so each cage hits its target", Icons.Default.GridOn, "cagemath", "Logic"),
        QuizCategory("Sign Grid", "Fill the grid and obey every greater/less sign", Icons.Default.GridOn, "signgrid", "Logic"),

        // --- classic puzzles (feat/puzzles-classic) ---
        QuizCategory("Tile Merge", "Swipe and merge tiles to reach the goal", Icons.Default.Apps, "tilemerge", "Logic"),
        QuizCategory("Slide Tiles", "Slide the numbered tiles back into order", Icons.Default.ViewModule, "slidetiles", "Logic"),
        QuizCategory("Light Flip", "Turn off every light in as few taps as possible", Icons.Default.Lightbulb, "lightflip", "Logic"),
        QuizCategory("Safe Sweep", "Find the mines and open every safe square", Icons.Default.Flag, "safesweep", "Logic"),
        QuizCategory("Disc Tower", "Move the stack of discs to the far peg", Icons.Default.Layers, "disctower", "Logic"),

        // Logic
        QuizCategory("Dual Card Logic", "Check if numbers are even or letters are vowels", Icons.Default.CheckCircle, "dualcard", "Logic"),
        QuizCategory("Number Sequence", "Tap numbers in ascending order", Icons.Default.FilterList, "numbersequence", "Logic"),
        //QuizCategory("Flow Puzzle", "Connect the Dots", Icons.Default.GridOn, "flowpuzzle", "Logic"),


        // Reaction
        QuizCategory("Symbol Swipe", "Swipe symbols in the correct direction", Icons.Default.SwipeRight, "symbolswipe", "Reaction"),
        QuizCategory("Context Switch", "Remember items through distracting tasks", Icons.Default.Psychology, "contextswitch", "Memory"),

        QuizCategory("Sentence Transitions", "Complete sentences with the right connecting words", Icons.Default.Link, "sentenceTransitions", "Language"),





        // General
        //QuizCategory("Image Match", "match image to text", Icons.Default.Palette, "imagematch", "General"),
        QuizCategory("Progressive Reveal", "guess the blurred image", Icons.Default.Visibility, "progressiverevelation", "Visual"),
        QuizCategory("Real or AI", "Real or AI", Icons.Default.CheckCircle, "realorai", "General"),


        // --- word puzzles (feat/puzzles-word) ---
        *WordPuzzleRegistry.quizCategories.toTypedArray(),

        QuizCategory("Trivia", "General Knowledge", Icons.Default.Quiz, "trivia", "General")
    ) + SpatialPuzzles.categories  // --- spatial puzzles (feat/puzzles-spatial) ---

    /**
     * Get all available puzzle types as a list of category strings
     */
    fun getAllAvailablePuzzleTypes(): List<String> = allCategories.map { it.category }

    /**
     * Get QuizCategory for a specific puzzle type with intelligent fallback
     */
    fun getQuizCategoryForType(puzzleType: String): QuizCategory? {
        // First try direct lookup from our comprehensive list
        allCategories.find { it.category == puzzleType }?.let { return it }

        // If not found, use the original when-based mapping for backward compatibility
        return when (puzzleType) {
            "math" -> QuizCategory("Math", "Mathematical Challenges", Icons.Default.Calculate, "math", "Math")
            "storyPuzzle" -> QuizCategory("Story Puzzle", "Narrative Mysteries", Icons.Default.MenuBook, "storyPuzzle", "English")
            "anagram" -> QuizCategory("Anagram", "Word Scrambles", Icons.Default.TextFields, "anagram", "English")
            "antonyms" -> QuizCategory("Antonyms", "Match Opposite Words", Icons.Default.BubbleChart, "antonyms", "English")
            "synonyms" -> QuizCategory("Synonyms", "Match Same Words", Icons.Default.Link, "synonyms", "English")
            "memorysquares" -> QuizCategory("Visual Memory", "Remember patterns", Icons.Default.GridView, "memorysquares", "Memory")
            "crossword" -> QuizCategory("Crossword", "Word Crosswords", Icons.Default.GridView, "crossword", "English")
            "memorystory" -> QuizCategory("Story Recall", "Listen and remember key items", Icons.Default.RecordVoiceOver, "memorystory", "Memory")
            "memorysequencing" -> QuizCategory("Timeline Puzzle", "Sequence events in chronological order", Icons.Default.Timeline, "memorysequencing", "Memory")
            "memoryretention" -> QuizCategory("Listen & Learn", "Absorb facts and sort by categories", Icons.Default.Headphones, "memoryretention", "Memory")
            "pinballdeflector" -> QuizCategory("Pinball Physics", "Predict ball path through deflectors", Icons.Default.Sports, "pinballdeflector", "Memory")
            "trivia" -> QuizCategory("Trivia", "General Knowledge", Icons.Default.Quiz, "trivia", "General")
            "average" -> QuizCategory("Average", "Calculate Averages", Icons.Default.BarChart, "average", "Math")
            "division" -> QuizCategory("Division", "Master Division", Icons.Default.Percent, "division", "Math")
            "mathestimation" -> QuizCategory("Estimation", "Chart Estimation", Icons.Default.TrendingUp, "mathestimation", "Math")
            "percentages" -> QuizCategory("Percentage", "Percentage Calculations", Icons.Default.Percent, "percentages", "Math")
            "discounts" -> QuizCategory("Discounts", "Price Ordering", Icons.Default.LocalOffer, "discounts", "Math")
            "purchasing" -> QuizCategory("Purchasing", "Subscription Calculations", Icons.Default.CreditCard, "purchasing", "Math")
            "conversion" -> QuizCategory("Conversion", "Unit Comparisons", Icons.Default.SwapHoriz, "conversion", "Math")
            "connotationwords" -> QuizCategory("Word Connotations", "Sort words by tone", Icons.Default.Psychology, "connotationwords", "English")
            "wordprefix" -> QuizCategory("Word Prefixes", "Improve your vocabulary", Icons.Default.Subtitles, "wordprefix", "English")
            "subtraction" -> QuizCategory("Subtraction", "Find the difference", Icons.Default.Remove, "subtraction", "Math")
            "mathtipping" -> QuizCategory("Tip Calculation", "Calculate tips", Icons.Default.RestaurantMenu, "mathtipping", "Math")
            "geography_cities" -> QuizCategory("City Geography", "Place cities on world map", Icons.Default.LocationCity, "geography_cities", "Geography")
            "geography_countries" -> QuizCategory("Country Geography", "Place countries on world map", Icons.Default.Public, "geography_countries", "Geography")
            else -> {
                // Fallback for new/unmapped puzzle types
                when {
                    puzzleType.contains("math", ignoreCase = true) ->
                        QuizCategory(PuzzleTypeNames.display(puzzleType), "Mathematical Challenge", Icons.Default.Calculate, puzzleType, "Math", subtitleRes = R.string.cat_fallback_math_subtitle)
                    puzzleType.contains("memory", ignoreCase = true) ->
                        QuizCategory(PuzzleTypeNames.display(puzzleType), "Memory Challenge", Icons.Default.Psychology, puzzleType, "Memory", subtitleRes = R.string.cat_fallback_memory_subtitle)
                    puzzleType.contains("word", ignoreCase = true) ->
                        QuizCategory(PuzzleTypeNames.display(puzzleType), "Word Challenge", Icons.Default.TextFields, puzzleType, "English", subtitleRes = R.string.cat_fallback_word_subtitle)
                    puzzleType.contains("geography", ignoreCase = true) ->
                        QuizCategory(PuzzleTypeNames.display(puzzleType), "Geography Challenge", Icons.Default.Public, puzzleType, "Geography", subtitleRes = R.string.cat_fallback_geography_subtitle)
                    else ->
                        QuizCategory(PuzzleTypeNames.display(puzzleType), "Brain Challenge", Icons.Default.Extension, puzzleType, "General", subtitleRes = R.string.cat_fallback_brain_subtitle)
                }
            }
        }
    }

    /**
     * Get categories filtered by filter category
     */
    fun getCategoriesByFilter(filterCategory: String): List<QuizCategory> {
        return if (filterCategory == "All") {
            allCategories
        } else {
            allCategories.filter { it.filterCategory == filterCategory }
        }
    }

    /**
     * Get all unique filter categories
     */
    fun getFilterCategories(): List<String> {
        return listOf("All") + allCategories.map { it.filterCategory }.distinct().sorted()
    }

    /**
     * Get smart fallback recent puzzles that include newer types
     */
    fun getSmartFallbackRecentPuzzles(): List<String> {
        val allTypes = getAllAvailablePuzzleTypes()
        // Mix of popular classics and some newer types including geography
        val priorityTypes = listOf(
            "memoryprevioussingle",
            "memorypreviouspair",
            "pinballdeflector",
            "wordsearch",
            "memorystory",
            "memorysequencing",
            "memoryretention",
            "geography_cities",
            "crypto",
            "geography_countries",
            "find_object",
            "imagepuzzle"
        )
        val availablePriority = priorityTypes.filter { it in allTypes }

        return if (availablePriority.size >= 3) {
            availablePriority.take(3)
        } else {
            // Fill with random selection if not enough priority types
            (availablePriority + allTypes.shuffled()).take(3).distinct()
        }
    }

    /**
     * Check if a puzzle type is new/recently added
     */
    fun isPuzzleTypeNew(puzzleType: String): Boolean {
        val newPuzzleTypes = listOf(
            "find_object",
            "pinballdeflector", // Recently added
            "memoryprevioussingle",
            "memorypreviouspair",
            "wordsearch",
            "crypto",
            "imagepuzzle",
            "geography_cities",    // Geography puzzles are new
            "geography_countries"  // Geography puzzles are new
            // Add other new types here
        )
        // New game wave (grid, classic, word, spatial): flag them so they stand out in the list
        val newGridTypes = listOf("sudoku", "binarygrid", "cagemath", "signgrid")
        return puzzleType in newPuzzleTypes ||
            puzzleType in newGridTypes ||
            ClassicPuzzles.isClassic(puzzleType) ||
            WordPuzzleRegistry.isWordPuzzleType(puzzleType) ||
            SpatialPuzzles.isSpatial(puzzleType)
    }

    fun isOfflinePuzzleType(puzzleType: String): Boolean {
        // Define which puzzle types can work without internet connection
        // These are the specific offline puzzle types provided
        val offlinePuzzleTypes = setOf(
            "uniqueobject",
            "discounts",
            "division",
            "contextswitch",
            "mathcrossword",
            "symmetry",
            // --- grid puzzles (feat/puzzles-grid) ---
            "sudoku", "binarygrid", "cagemath", "signgrid",
            "trainrouting",
            "pinballdeflector",
            "memorypreviouspair",
            "memoryprevioussingle",
            "geography_countries",
            "geography_cities",
            "colortextmatching",
            "conversion",
            "triangledotmemory",
            "numbersequence",
            "dualcard",
            "mathestimation",
            "mathtipping",
            "percentages",
            "numbersum",
            "symbolswipe",
            "colormatching",
            "imagevortex",
            "memorysquares",
            "mathexpression",
            "purchasing",
            "subtraction",
            "mathcomparison"
        )

        // classic + word + spatial puzzles are fully local
        return offlinePuzzleTypes.contains(puzzleType) ||
            ClassicPuzzles.isClassic(puzzleType) ||
            WordPuzzleRegistry.isWordPuzzleType(puzzleType) ||
            SpatialPuzzles.isSpatial(puzzleType)
    }
}