package com.kreativekoala.riddleverse

import com.kreativekoala.riddleverse.QuizCategories.getQuizCategoryForType
import kotlinx.coroutines.launch

/**
 * Data class for featured puzzle types from analytics
 */
data class FeaturedPuzzleType(
    val puzzleType: String,
    val playCount: Int,
    val successRatePercent: Double,
    val avgScore: Double,
    val recentPlays: Int,
    val featuredReason: String
) {
    fun getQuizCategory(): QuizCategory? {
        return getQuizCategoryForType(puzzleType)
    }

    val statsText: String
        get() = "$playCount plays • ${successRatePercent.toInt()}% success"
}

/**
 * Helper functions for featured puzzles
 */
object FeaturedPuzzleHelper {

    /**
     * Get fallback featured puzzle types when Firebase is unavailable
     */
    fun getFallbackFeaturedPuzzles(): List<FeaturedPuzzleType> {
        return listOf(
            FeaturedPuzzleType("math", 133, 92.5, 29.8, 12, "Most Popular"),
            FeaturedPuzzleType("memorysquares", 89, 93.8, 24.1, 15, "High Success Rate"),
            FeaturedPuzzleType("anagram", 67, 94.6, 24.5, 8, "Recently Played"),
            FeaturedPuzzleType("trivia", 45, 78.4, 19.9, 5, "Try This"),
            FeaturedPuzzleType("colormatching", 34, 95.2, 30.1, 7, "High Scoring")
        )
    }

    /**
     * Check if puzzle type should be considered "new"
     */
    fun isPuzzleTypeNew(puzzleType: String): Boolean {
        val newPuzzleTypes = listOf(
            "realorai",
            "imagematch",
            "progressiverevelation",
            "pinballdeflector",
            "memoryprevioussingle",
            "memorypreviouspair",
            "wordsearch",
            "crypto",
            "wordsnake",
            "imagepuzzle",
            "imagequestion",
            "musicidentification",
            "find_object"
        )
        return puzzleType in newPuzzleTypes
    }
}

/**
 * Function to fetch featured puzzle types (called from UI)
 */
fun fetchFeaturedPuzzleTypes(onResult: (List<FeaturedPuzzleType>) -> Unit) {
    // Launch coroutine to fetch from Firebase
    kotlinx.coroutines.GlobalScope.launch(kotlinx.coroutines.Dispatchers.Main) {
        try {
            val analyticsService = PuzzleAnalyticsService()
            val featuredTypes = analyticsService.fetchFeaturedPuzzleTypes()

            if (featuredTypes.isNotEmpty()) {
                android.util.Log.d("FeaturedPuzzles", "Loaded ${featuredTypes.size} featured puzzles from Firebase")
                onResult(featuredTypes)
            } else {
                android.util.Log.d("FeaturedPuzzles", "No Firebase data, using fallback")
                onResult(FeaturedPuzzleHelper.getFallbackFeaturedPuzzles())
            }
        } catch (e: Exception) {
            android.util.Log.e("FeaturedPuzzles", "Failed to fetch featured puzzle types from Firebase", e)
            onResult(FeaturedPuzzleHelper.getFallbackFeaturedPuzzles())
        }
    }
}