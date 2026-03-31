package com.kreativekoala.riddleverse

/**
 * Static mapping of puzzle types to recommended follow-up puzzles
 * Designed to increase discoverability and create natural progression paths
 */
object PuzzleRecommendations {

    private val recommendationMap = mapOf(
        // Math puzzle progressions
        "math" to listOf("average", "percentages", "mathtipping"),
        "average" to listOf("percentages", "mathestimation", "division"),
        "division" to listOf("percentages", "subtraction", "purchasing"),
        "mathestimation" to listOf("average", "conversion", "discounts"),
        "percentages" to listOf("discounts", "purchasing", "mathtipping"),
        "discounts" to listOf("purchasing", "conversion", "percentages"),
        "purchasing" to listOf("mathtipping", "discounts", "percentages"),
        "conversion" to listOf("mathestimation", "average", "percentages"),
        "subtraction" to listOf("math", "division", "numbersum"),
        "mathtipping" to listOf("percentages", "purchasing", "mathcomparison"),
        "numbersum" to listOf("mathexpression", "subtraction", "math"),
        "mathexpression" to listOf("mathcomparison", "numbersum", "mathcrossword"),
        "mathcomparison" to listOf("mathexpression", "math", "numbersum"),
        "mathcrossword" to listOf("crossword", "mathexpression", "math"),

        // English/Language progressions
        "storyPuzzle" to listOf("memorystory", "memorysequencing", "sentenceTransitions"),
        "anagram" to listOf("wordprefix", "synonyms", "letterset"),
        "antonyms" to listOf("synonyms", "connotationwords", "wordprefix"),
        "synonyms" to listOf("antonyms", "connotationwords", "anagram"),
        "crossword" to listOf("anagram", "wordsearch", "wordsnake"),
        "connotationwords" to listOf("synonyms", "antonyms", "wordprefix"),
        "wordprefix" to listOf("anagram", "synonyms", "connotationwords"),
        "wordsearch" to listOf("wordsnake", "letterset", "crossword"),
        "wordsnake" to listOf("wordsearch", "letterset", "anagram"),
        "letterset" to listOf("anagram", "wordsearch", "wordsnake"),
        "sentenceTransitions" to listOf("storyPuzzle", "synonyms", "connotationwords"),

        // Memory puzzle progressions
        "memorysquares" to listOf("triangledotmemory", "memoryprevioussingle", "imagevortex"),
        "memorystory" to listOf("memorysequencing", "memoryretention", "storyPuzzle"),
        "memorysequencing" to listOf("memorystory", "memoryretention", "contextswitch"),
        "memoryretention" to listOf("memorystory", "memorysequencing", "contextswitch"),
        "pinballdeflector" to listOf("memorysquares", "triangledotmemory", "memoryprevioussingle"),
        "memoryprevioussingle" to listOf("memorypreviouspair", "memorysquares", "imagevortex"),
        "imagevortex" to listOf("memoryprevioussingle", "memorysquares", "triangledotmemory"),
        "memorypreviouspair" to listOf("memoryprevioussingle", "contextswitch", "colortextmatching"),
        "triangledotmemory" to listOf("memorysquares", "pinballdeflector", "imagevortex"),
        "colortextmatching" to listOf("colormatching", "memorypreviouspair", "uniqueobject"),
        "contextswitch" to listOf("memorypreviouspair", "memoryretention", "memorysequencing"),

        // Geography progressions
        "geography_cities" to listOf("geography_countries", "trivia", "memoryretention"),
        "geography_countries" to listOf("geography_cities", "trivia", "memoryretention"),

        // Visual/Focus progressions
        "crypto" to listOf("anagram", "letterset", "wordprefix"),
        "find_object" to listOf("waldopuzzle", "imagepuzzle", "uniqueobject"),
        "imagequestion" to listOf("imagematch", "imagepuzzle", "find_object"),
        "waldopuzzle" to listOf("find_object", "imagepuzzle", "uniqueobject"),
        "imagepuzzle" to listOf("waldopuzzle", "find_object", "imagequestion"),
        "uniqueobject" to listOf("colormatching", "colortextmatching", "find_object"),
        "colormatching" to listOf("colortextmatching", "uniqueobject", "symbolswipe"),
        "symmetry" to listOf("flowpuzzle", "memorysquares", "triangledotmemory"),
        "imagematch" to listOf("imagequestion", "imagepuzzle", "uniqueobject"),

        // Logic progressions
        "dualcard" to listOf("numbersequence", "flowpuzzle", "mathcomparison"),
        "numbersequence" to listOf("dualcard", "flowpuzzle", "subtraction"),
        "flowpuzzle" to listOf("symmetry", "dualcard", "numbersequence"),

        // Reaction progressions
        "symbolswipe" to listOf("colormatching", "contextswitch", "colortextmatching"),

        // Audio progressions
        "musicidentification" to listOf("memorystory", "memoryretention", "trivia"),

        // General progressions
        "trivia" to listOf("geography_cities", "geography_countries", "memoryretention")
    )

    /**
     * Get recommended puzzles for a given puzzle type
     * Returns up to 3 recommendations, filtering out any that might not be available
     */
    fun getRecommendationsFor(completedPuzzleType: String): List<String> {
        val recommendations = recommendationMap[completedPuzzleType] ?: getSmartFallbackRecommendations(completedPuzzleType)

        // Filter to ensure all recommended puzzles exist in our categories
        val availablePuzzleTypes = QuizCategories.getAllAvailablePuzzleTypes().toSet()
        return recommendations.filter { it in availablePuzzleTypes }.take(3)
    }

    /**
     * Smart fallback for puzzle types not in our mapping
     * Uses category-based recommendations and popular puzzles
     */
    private fun getSmartFallbackRecommendations(puzzleType: String): List<String> {
        val category = QuizCategories.getQuizCategoryForType(puzzleType)

        return when (category?.filterCategory) {
            "Math" -> listOf("percentages", "average", "mathtipping")
            "English" -> listOf("anagram", "synonyms", "crossword")
            "Memory" -> listOf("memorysquares", "memoryprevioussingle", "pinballdeflector")
            "Geography" -> listOf("geography_cities", "geography_countries", "trivia")
            "Visual" -> listOf("find_object", "uniqueobject", "colormatching")
            "Logic" -> listOf("dualcard", "flowpuzzle", "numbersequence")
            "Focus" -> listOf("uniqueobject", "colormatching", "symbolswipe")
            "Reaction" -> listOf("symbolswipe", "colormatching", "contextswitch")
            "Audio" -> listOf("musicidentification", "memorystory", "trivia")

            else -> QuizCategories.getSmartFallbackRecentPuzzles()
        }
    }

    /**
     * Get display names for recommended puzzles
     */
    fun getRecommendationDisplayData(puzzleType: String): List<RecommendationData> {
        return getRecommendationsFor(puzzleType).map { recommendedType ->
            val category = QuizCategories.getQuizCategoryForType(recommendedType)
            RecommendationData(
                puzzleType = recommendedType,
                displayName = category?.title ?: recommendedType.replaceFirstChar { it.uppercase() },
                subtitle = category?.subtitle ?: "Brain Challenge",
                isNew = QuizCategories.isPuzzleTypeNew(recommendedType)
            )
        }
    }
}

/**
 * Data class for displaying puzzle recommendations
 */
data class RecommendationData(
    val puzzleType: String,
    val displayName: String,
    val subtitle: String,
    val isNew: Boolean = false
)