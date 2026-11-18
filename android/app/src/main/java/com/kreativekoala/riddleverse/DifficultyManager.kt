// UnifiedDifficultyManager.kt - Comprehensive difficulty management system
package com.kreativekoala.riddleverse

import android.util.Log
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import kotlin.math.*

/**
 * Unified adaptive difficulty manager that handles multiple puzzle types
 * with sophisticated performance analysis and smooth difficulty progression
 */
class DifficultyManager {
    private val TAG = "UnifiedAdaptiveManager"

    // Performance tracking
    private val performanceHistory = mutableMapOf<String, MutableList<PlayerPerformance>>()
    private val puzzleTypeConfigs = mutableMapOf<String, PuzzleTypeConfig>()
    private val currentLevelIndices = mutableMapOf<String, Int>()
    private var adaptationsSinceStart = 0

    // Global adaptation limits
    private val MIN_GAMES_FOR_ADAPTATION = 2
    private val MAX_ADAPTATIONS_PER_SESSION = 8
    private val HISTORY_LIMIT = 20
    private val ANALYSIS_WINDOW = 5

    init {
        initializePuzzleConfigs()
    }

    data class PlayerPerformance(
        val accuracy: Float,           // 0.0 - 1.0
        val averageTime: Float,        // seconds
        val streakLength: Int,         // consecutive correct answers
        val livesRemaining: Int,       // lives left after completion
        val gameScore: Int,           // raw score from last game
        val difficulty: String,       // current difficulty level name
        val puzzleType: String,       // type of puzzle
        val timestamp: Long = System.currentTimeMillis(),
        val timeEfficiency: Float = 1.0f  // actual_time / expected_time
    )

    data class DifficultyLevel(
        val name: String,
        val index: Int,
        val gridSize: Int = 0,         // For grid-based puzzles
        val targetCount: Int = 0,      // For memory/counting puzzles
        val memorizeTime: Int = 0,     // Memorization time in seconds
        val timeLimit: Int,            // Total time limit
        val livesAllowed: Int,         // Lives granted
        val basePoints: Int,           // Base score multiplier
        val complexityMultiplier: Float = 1.0f,
        @StringRes val descriptionRes: Int = 0
    )

    data class AdaptiveConfig(
        val level: DifficultyLevel,
        val adjustmentReason: StatsText,
        val confidenceScore: Float,    // 0.0 - 1.0
        val previousLevel: String,
        val puzzleType: String,
        val adaptationCount: Int
    )

    data class PuzzleTypeConfig(
        val name: String,
        val levels: List<DifficultyLevel>,
        val thresholds: AdaptationThresholds,
        val defaultLevelIndex: Int = 1  // Start at Easy typically
    )

    data class AdaptationThresholds(
        val excellentAccuracy: Float = 0.95f,
        val goodAccuracy: Float = 0.8f,
        val poorAccuracy: Float = 0.5f,
        val fastTimeThreshold: Float = 15f,
        val slowTimeThreshold: Float = 45f,
        val minGamesForAdaptation: Int = 2,
        val streakThresholdUp: Int = 3,
        val streakThresholdDown: Int = 1,
        val livesThresholdUp: Float = 2.5f,
        val livesThresholdDown: Float = 1.0f,
        val timeEfficiencyUp: Float = 0.7f,    // Completes in <70% of expected time
        val timeEfficiencyDown: Float = 1.3f   // Takes >130% of expected time
    )

    private fun initializePuzzleConfigs() {
        // Memory Squares (original implementation)
        puzzleTypeConfigs["memorysquares"] = PuzzleTypeConfig( // Note: use "memorysquares" not "memorySquares"
            name = "Memory Squares",
            levels = listOf(
                DifficultyLevel("Tutorial", 0, 3, 2, 5, 90, 5, 50, 0.8f, R.string.stats_levdesc_very_easy_start),
                DifficultyLevel("Beginner", 1, 3, 3, 4, 90, 4, 75, 0.9f, R.string.stats_levdesc_basic_memory_training),
                DifficultyLevel("Easy", 2, 4, 3, 4, 80, 4, 100, 1.0f, R.string.stats_levdesc_slightly_more_complex),
                DifficultyLevel("Easy+", 3, 4, 4, 4, 80, 4, 125, 1.1f, R.string.stats_levdesc_building_confidence),
                DifficultyLevel("Medium-", 4, 4, 5, 3, 70, 3, 150, 1.2f, R.string.stats_levdesc_stepping_up),
                DifficultyLevel("Medium", 5, 5, 5, 3, 70, 3, 175, 1.3f, R.string.stats_levdesc_balanced_challenge),
                DifficultyLevel("Medium+", 6, 5, 6, 3, 70, 3, 200, 1.4f, R.string.stats_levdesc_getting_harder),
                DifficultyLevel("Hard-", 7, 5, 7, 2, 60, 3, 225, 1.5f, R.string.stats_levdesc_challenging),
                DifficultyLevel("Hard", 8, 6, 8, 2, 60, 3, 250, 1.6f, R.string.stats_levdesc_serious_challenge),
                DifficultyLevel("Hard+", 9, 6, 10, 2, 50, 2, 275, 1.8f, R.string.stats_levdesc_very_challenging),
                DifficultyLevel("Expert-", 10, 6, 12, 2, 50, 2, 300, 2.0f, R.string.stats_levdesc_expert_level),
                DifficultyLevel("Expert", 11, 7, 12, 2, 50, 2, 325, 2.2f, R.string.stats_levdesc_true_expert),
                DifficultyLevel("Expert+", 12, 7, 15, 1, 45, 2, 350, 2.5f, R.string.stats_levdesc_beyond_expert),
                DifficultyLevel("Master", 13, 8, 18, 1, 40, 2, 400, 3.0f, R.string.stats_levdesc_master_level),
                DifficultyLevel("Grandmaster", 14, 8, 22, 1, 35, 1, 500, 4.0f, R.string.stats_levdesc_ultimate_challenge)
            ),
            thresholds = AdaptationThresholds(),
            defaultLevelIndex = 5 // Start at Medium
        )

        puzzleTypeConfigs["crypto"] = PuzzleTypeConfig(
            name = "Crypto Puzzle",
            levels = listOf(
                DifficultyLevel("Tutorial", 0, 0, 0, 0, 600, 5, 75, 0.8f, R.string.stats_levdesc_all_instances_revealed_no_locks),
                DifficultyLevel("Beginner", 1, 0, 0, 0, 480, 4, 100, 0.9f, R.string.stats_levdesc_auto_reveal_mode_minimal_complexity),
                DifficultyLevel("Easy", 2, 0, 0, 0, 420, 4, 125, 1.0f, R.string.stats_levdesc_auto_reveal_with_some_locked_positions),
                DifficultyLevel("Easy+", 3, 0, 0, 0, 360, 4, 150, 1.1f, R.string.stats_levdesc_transitioning_to_single_reveals),
                DifficultyLevel("Medium-", 4, 0, 0, 0, 300, 3, 175, 1.2f, R.string.stats_levdesc_single_letter_reveal_mode_begins),
                DifficultyLevel("Medium", 5, 0, 0, 0, 270, 3, 200, 1.3f, R.string.stats_levdesc_single_reveal_with_dependency_chains),
                DifficultyLevel("Medium+", 6, 0, 0, 0, 240, 3, 225, 1.4f, R.string.stats_levdesc_more_locked_positions),
                DifficultyLevel("Hard-", 7, 0, 0, 0, 210, 2, 250, 1.5f, R.string.stats_levdesc_complex_dependency_chains),
                DifficultyLevel("Hard", 8, 0, 0, 0, 180, 2, 275, 1.6f, R.string.stats_levdesc_advanced_cryptogram_solving),
                DifficultyLevel("Hard+", 9, 0, 0, 0, 150, 2, 300, 1.8f, R.string.stats_levdesc_maximum_locked_positions),
                DifficultyLevel("Expert-", 10, 0, 0, 0, 120, 2, 325, 2.0f, R.string.stats_levdesc_expert_pattern_recognition),
                DifficultyLevel("Expert", 11, 0, 0, 0, 100, 2, 350, 2.2f, R.string.stats_levdesc_master_cryptographer),
                DifficultyLevel("Expert+", 12, 0, 0, 0, 90, 1, 375, 2.5f, R.string.stats_levdesc_beyond_expert_solving),
                DifficultyLevel("Master", 13, 0, 0, 0, 75, 1, 400, 3.0f, R.string.stats_levdesc_master_level_cryptograms),
                DifficultyLevel("Grandmaster", 14, 0, 0, 0, 60, 1, 500, 4.0f, R.string.stats_levdesc_ultimate_cipher_challenge)
            ),
            thresholds = AdaptationThresholds(
                excellentAccuracy = 0.9f,
                goodAccuracy = 0.75f,
                poorAccuracy = 0.4f,
                timeEfficiencyUp = 0.5f,     // Fast pattern recognition
                timeEfficiencyDown = 1.8f,   // Slow decoding
                streakThresholdUp = 2,       // Pattern recognition streaks
                streakThresholdDown = 1,     // Quick downgrade for struggles
                minGamesForAdaptation = 1,   // Quick adaptation for cryptograms
                livesThresholdUp = 2.0f,     // High lives for upgrade
                livesThresholdDown = 1.5f    // Moderate lives for downgrade
            ),
            defaultLevelIndex = 4 // Start at Medium- (single reveal mode)
        )


        // Unique Object Puzzle
        puzzleTypeConfigs["uniqueobject"] = PuzzleTypeConfig(
            name = "Unique Object",
            levels = listOf(
                DifficultyLevel("Beginner", 0, 3, 0, 0, 60, 5, 40, 0.8f, R.string.stats_levdesc_3x3_grid_distinct_shapes_colors),
                DifficultyLevel("Easy", 1, 4, 0, 0, 45, 4, 60, 1.0f, R.string.stats_levdesc_4x4_grid_some_similar_elements),
                DifficultyLevel("Medium", 2, 5, 0, 0, 35, 3, 80, 1.2f, R.string.stats_levdesc_5x5_grid_visual_confusion),
                DifficultyLevel("Hard", 3, 6, 0, 0, 30, 2, 100, 1.5f, R.string.stats_levdesc_6x6_grid_complex_patterns),
                DifficultyLevel("Expert", 4, 7, 0, 0, 25, 2, 120, 2.0f, R.string.stats_levdesc_maximum_complexity)
            ),
            thresholds = AdaptationThresholds(
                excellentAccuracy = 0.9f,
                poorAccuracy = 0.3f,
                streakThresholdUp = 2,
                minGamesForAdaptation = 2
            )
        )

        // Math Crossword
        puzzleTypeConfigs["mathcrossword"] = PuzzleTypeConfig(
            name = "Math Crossword",
            levels = listOf(
                DifficultyLevel("Beginner", 0, 7, 0, 0, 480, 5, 30, 0.8f, R.string.stats_levdesc_simple_addition_subtraction),
                DifficultyLevel("Easy", 1, 9, 0, 0, 600, 4, 50, 1.0f, R.string.stats_levdesc_basic_operations),
                DifficultyLevel("Medium", 2, 11, 0, 0, 720, 3, 70, 1.2f, R.string.stats_levdesc_mixed_operations),
                DifficultyLevel("Hard", 3, 13, 0, 0, 900, 2, 90, 1.5f, R.string.stats_levdesc_complex_equations),
                DifficultyLevel("Expert", 4, 15, 0, 0, 1080, 2, 110, 2.0f, R.string.stats_levdesc_advanced_mathematics)
            ),
            thresholds = AdaptationThresholds(
                timeEfficiencyUp = 0.6f,
                timeEfficiencyDown = 1.2f,
                minGamesForAdaptation = 2
            )
        )

        // Symmetry Puzzle
        puzzleTypeConfigs["symmetry"] = PuzzleTypeConfig(
            name = "Symmetry",
            levels = listOf(
                DifficultyLevel("Beginner", 0, 3, 0, 0, 45, 5, 30, 0.8f, R.string.stats_levdesc_3x3_copy_patterns),
                DifficultyLevel("Easy", 1, 3, 0, 0, 35, 4, 50, 1.0f, R.string.stats_levdesc_3x3_with_mirrors),
                DifficultyLevel("Medium", 2, 4, 0, 0, 30, 3, 70, 1.2f, R.string.stats_levdesc_4x4_complex_mirrors),
                DifficultyLevel("Hard", 3, 5, 0, 0, 25, 2, 90, 1.5f, R.string.stats_levdesc_5x5_advanced_patterns),
                DifficultyLevel("Expert", 4, 6, 0, 0, 20, 2, 110, 2.0f, R.string.stats_levdesc_6x6_maximum_complexity)
            ),
            thresholds = AdaptationThresholds(
                timeEfficiencyUp = 0.5f,
                timeEfficiencyDown = 1.5f,
                minGamesForAdaptation = 3
            )
        )

        // Symbol Swipe
        puzzleTypeConfigs["symbolswipe"] = PuzzleTypeConfig(
            name = "Symbol Swipe",
            levels = listOf(
                DifficultyLevel("Beginner", 0, 0, 12, 3, 90, 5, 40, 0.8f, R.string.stats_levdesc_12_symbols_3s_each),
                DifficultyLevel("Easy", 1, 0, 15, 2, 75, 4, 60, 1.0f, R.string.stats_levdesc_15_symbols_2_5s_each),
                DifficultyLevel("Medium", 2, 0, 20, 2, 60, 3, 80, 1.2f, R.string.stats_levdesc_20_symbols_2s_each),
                DifficultyLevel("Hard", 3, 0, 25, 1, 50, 2, 100, 1.5f, R.string.stats_levdesc_25_symbols_1_8s_each),
                DifficultyLevel("Expert", 4, 0, 30, 1, 40, 2, 120, 2.0f, R.string.stats_levdesc_30_symbols_1_5s_each)
            ),
            thresholds = AdaptationThresholds(
                excellentAccuracy = 0.9f,
                streakThresholdUp = 5,
                minGamesForAdaptation = 1
            )
        )

        // Average/Number Puzzle
        puzzleTypeConfigs["average"] = PuzzleTypeConfig(
            name = "Average",
            levels = listOf(
                DifficultyLevel("Beginner", 0, 0, 2, 0, 60, 5, 40, 0.8f, R.string.stats_levdesc_2_3_simple_numbers),
                DifficultyLevel("Easy", 1, 0, 3, 0, 50, 4, 60, 1.0f, R.string.stats_levdesc_3_4_numbers),
                DifficultyLevel("Medium", 2, 0, 4, 0, 40, 3, 80, 1.2f, R.string.stats_levdesc_4_5_numbers),
                DifficultyLevel("Hard", 3, 0, 5, 0, 35, 2, 100, 1.5f, R.string.stats_levdesc_5_6_complex_numbers),
                DifficultyLevel("Expert", 4, 0, 6, 0, 30, 2, 120, 2.0f, R.string.stats_levdesc_6_challenging_numbers)
            ),
            thresholds = AdaptationThresholds()
        )

        puzzleTypeConfigs["dualcard"] = PuzzleTypeConfig(
            name = "Dual Card Task",
            levels = listOf(
                DifficultyLevel("Tutorial", 0, 0, 0, 0, 180, 5, 50, 0.8f, R.string.stats_levdesc_8_rounds_predictable_switching),
                DifficultyLevel("Beginner", 1, 0, 0, 0, 150, 4, 75, 0.9f, R.string.stats_levdesc_10_rounds_simple_alternating),
                DifficultyLevel("Easy", 2, 0, 0, 0, 120, 4, 100, 1.0f, R.string.stats_levdesc_12_rounds_basic_task_switching),
                DifficultyLevel("Easy+", 3, 0, 0, 0, 110, 4, 125, 1.1f, R.string.stats_levdesc_15_rounds_more_variety),
                DifficultyLevel("Medium-", 4, 0, 0, 0, 100, 3, 150, 1.2f, R.string.stats_levdesc_15_rounds_alternating_pattern),
                DifficultyLevel("Medium", 5, 0, 0, 0, 90, 3, 175, 1.3f, R.string.stats_levdesc_15_rounds_mixed_switching),
                DifficultyLevel("Medium+", 6, 0, 0, 0, 80, 3, 200, 1.4f, R.string.stats_levdesc_18_rounds_complex_switching),
                DifficultyLevel("Hard-", 7, 0, 0, 0, 75, 2, 225, 1.5f, R.string.stats_levdesc_20_rounds_random_switching),
                DifficultyLevel("Hard", 8, 0, 0, 0, 70, 2, 250, 1.6f, R.string.stats_levdesc_20_rounds_high_complexity),
                DifficultyLevel("Hard+", 9, 0, 0, 0, 65, 2, 275, 1.8f, R.string.stats_levdesc_22_rounds_maximum_switching),
                DifficultyLevel("Expert-", 10, 0, 0, 0, 60, 2, 300, 2.0f, R.string.stats_levdesc_25_rounds_expert_switching),
                DifficultyLevel("Expert", 11, 0, 0, 0, 55, 2, 325, 2.2f, R.string.stats_levdesc_25_rounds_master_level),
                DifficultyLevel("Expert+", 12, 0, 0, 0, 50, 1, 350, 2.5f, R.string.stats_levdesc_30_rounds_beyond_expert),
                DifficultyLevel("Master", 13, 0, 0, 0, 45, 1, 400, 3.0f, R.string.stats_levdesc_30_rounds_master_switching),
                DifficultyLevel("Grandmaster", 14, 0, 0, 0, 40, 1, 500, 4.0f, R.string.stats_levdesc_35_rounds_ultimate_challenge)
            ),
            thresholds = AdaptationThresholds(
                excellentAccuracy = 0.9f,
                goodAccuracy = 0.75f,
                poorAccuracy = 0.5f,
                timeEfficiencyUp = 0.7f,     // Fast task switching
                timeEfficiencyDown = 1.5f,   // Slow switching costs
                streakThresholdUp = 3,       // Consecutive correct switches
                streakThresholdDown = 2,     // Task switching struggles
                minGamesForAdaptation = 1,   // Quick adaptation for flexibility
                livesThresholdUp = 2.0f,     // High performance for upgrade
                livesThresholdDown = 1.5f    // Moderate performance for downgrade
            ),
            defaultLevelIndex = 5 // Start at Medium
        )

        puzzleTypeConfigs["discounts"] = PuzzleTypeConfig(
            name = "Discount Price Ordering",
            levels = listOf(
                DifficultyLevel("Tutorial", 0, 0, 0, 0, 180, 5, 50, 0.8f, R.string.stats_levdesc_simple_prices_obvious_discounts),
                DifficultyLevel("Beginner", 1, 0, 0, 0, 150, 4, 75, 0.9f, R.string.stats_levdesc_basic_discount_calculations),
                DifficultyLevel("Easy", 2, 0, 0, 0, 120, 4, 100, 1.0f, R.string.stats_levdesc_mixed_prices_with_clear_discounts),
                DifficultyLevel("Easy+", 3, 0, 0, 0, 110, 4, 125, 1.1f, R.string.stats_levdesc_more_items_to_compare),
                DifficultyLevel("Medium-", 4, 0, 0, 0, 100, 3, 150, 1.2f, R.string.stats_levdesc_complex_discount_percentages),
                DifficultyLevel("Medium", 5, 0, 0, 0, 90, 3, 175, 1.3f, R.string.stats_levdesc_multiple_discount_types),
                DifficultyLevel("Medium+", 6, 0, 0, 0, 80, 3, 200, 1.4f, R.string.stats_levdesc_closer_final_prices),
                DifficultyLevel("Hard-", 7, 0, 0, 0, 70, 2, 225, 1.5f, R.string.stats_levdesc_similar_final_prices),
                DifficultyLevel("Hard", 8, 0, 0, 0, 60, 2, 250, 1.6f, R.string.stats_levdesc_advanced_discount_math),
                DifficultyLevel("Hard+", 9, 0, 0, 0, 55, 2, 275, 1.8f, R.string.stats_levdesc_very_close_comparisons),
                DifficultyLevel("Expert", 10, 0, 0, 0, 50, 2, 300, 2.0f, R.string.stats_levdesc_expert_price_analysis),
                DifficultyLevel("Master", 11, 0, 0, 0, 45, 2, 350, 2.5f, R.string.stats_levdesc_master_discount_calculations),
                DifficultyLevel("Grandmaster", 12, 0, 0, 0, 40, 1, 400, 3.0f, R.string.stats_levdesc_ultimate_pricing_challenge)
            ),
            thresholds = AdaptationThresholds(
                excellentAccuracy = 0.95f,
                goodAccuracy = 0.8f,
                poorAccuracy = 0.5f,
                timeEfficiencyUp = 0.7f,
                timeEfficiencyDown = 1.3f,
                streakThresholdUp = 2,
                streakThresholdDown = 1,
                minGamesForAdaptation = 2
            ),
            defaultLevelIndex = 2 // Start at Easy
        )

        puzzleTypeConfigs["triangledotmemory"] = PuzzleTypeConfig(
            name = "Triangle Dot Memory",
            levels = listOf(
                DifficultyLevel("Tutorial", 0, 0, 6, 3, 120, 5, 50, 0.8f, R.string.stats_levdesc_6_patterns_slow_pace),
                DifficultyLevel("Beginner", 1, 0, 8, 2, 100, 4, 70, 0.9f, R.string.stats_levdesc_8_patterns_comfortable_timing),
                DifficultyLevel("Easy", 2, 0, 10, 2, 90, 4, 90, 1.0f, R.string.stats_levdesc_10_patterns_steady_pace),
                DifficultyLevel("Easy+", 3, 0, 12, 2, 85, 4, 110, 1.1f, R.string.stats_levdesc_12_patterns_building_challenge),
                DifficultyLevel("Medium-", 4, 0, 12, 2, 80, 3, 130, 1.2f, R.string.stats_levdesc_12_patterns_faster_pace),
                DifficultyLevel("Medium", 5, 0, 15, 2, 75, 3, 150, 1.3f, R.string.stats_levdesc_15_patterns_standard_challenge),
                DifficultyLevel("Medium+", 6, 0, 16, 1, 70, 3, 170, 1.4f, R.string.stats_levdesc_16_patterns_increased_difficulty),
                DifficultyLevel("Hard-", 7, 0, 18, 1, 65, 2, 190, 1.5f, R.string.stats_levdesc_18_patterns_challenging_pace),
                DifficultyLevel("Hard", 8, 0, 20, 1, 60, 2, 210, 1.6f, R.string.stats_levdesc_20_patterns_fast_decisions),
                DifficultyLevel("Hard+", 9, 0, 22, 1, 55, 2, 230, 1.8f, R.string.stats_levdesc_22_patterns_very_challenging),
                DifficultyLevel("Expert-", 10, 0, 25, 1, 50, 2, 250, 2.0f, R.string.stats_levdesc_25_patterns_expert_memory),
                DifficultyLevel("Expert", 11, 0, 28, 1, 45, 2, 270, 2.2f, R.string.stats_levdesc_28_patterns_master_level),
                DifficultyLevel("Expert+", 12, 0, 30, 1, 40, 1, 290, 2.5f, R.string.stats_levdesc_30_patterns_beyond_expert),
                DifficultyLevel("Master", 13, 0, 35, 1, 35, 1, 320, 3.0f, R.string.stats_levdesc_35_patterns_master_memory),
                DifficultyLevel("Grandmaster", 14, 0, 40, 1, 30, 1, 400, 4.0f, R.string.stats_levdesc_40_patterns_ultimate_memory_test)
            ),
            thresholds = AdaptationThresholds(
                excellentAccuracy = 0.85f,     // High accuracy for memory tasks
                goodAccuracy = 0.70f,         // Good memory performance
                poorAccuracy = 0.50f,         // Struggling with memory
                timeEfficiencyUp = 0.8f,      // Quick recognition
                timeEfficiencyDown = 1.4f,    // Slow pattern processing
                streakThresholdUp = 3,        // Memory consistency
                streakThresholdDown = 2,      // Memory struggles
                minGamesForAdaptation = 1,    // Quick adaptation for memory
                livesThresholdUp = 2.0f,      // High performance
                livesThresholdDown = 1.0f     // Memory difficulties
            ),
            defaultLevelIndex = 5 // Start at Medium
        )

        // Initialize all puzzle types to their default levels
        puzzleTypeConfigs.forEach { (type, config) ->
            currentLevelIndices[type] = config.defaultLevelIndex
        }
    }

    /**
     * Record a game completion and analyze for difficulty adjustment
     */
    fun recordPerformance(performance: PlayerPerformance): AdaptiveConfig {
        val puzzleType = performance.puzzleType

        // Initialize history if needed
        if (!performanceHistory.containsKey(puzzleType)) {
            performanceHistory[puzzleType] = mutableListOf()
        }

        // Add performance record
        performanceHistory[puzzleType]!!.add(performance)

        // Limit history size
        if (performanceHistory[puzzleType]!!.size > HISTORY_LIMIT) {
            performanceHistory[puzzleType] =
                performanceHistory[puzzleType]!!.takeLast(HISTORY_LIMIT - 5).toMutableList()
        }

        return analyzeAndAdjustDifficulty(puzzleType)
    }

    /**
     * Comprehensive difficulty analysis and adjustment
     */
    private fun analyzeAndAdjustDifficulty(puzzleType: String): AdaptiveConfig {
        val config = puzzleTypeConfigs[puzzleType] ?: puzzleTypeConfigs["average"]!!
        val history = performanceHistory[puzzleType]!!
        val currentIndex = currentLevelIndices[puzzleType] ?: config.defaultLevelIndex
        val currentLevel = config.levels[currentIndex]
        val thresholds = config.thresholds

        // Don't adjust if insufficient data
        if (history.size < thresholds.minGamesForAdaptation) {
            return AdaptiveConfig(
                level = currentLevel,
                adjustmentReason = StatsText(R.string.stats_adapt_gathering, listOf(history.size, thresholds.minGamesForAdaptation)),
                confidenceScore = 0.1f,
                previousLevel = currentLevel.name,
                puzzleType = puzzleType,
                adaptationCount = adaptationsSinceStart
            )
        }

        // Don't adjust too frequently
        if (adaptationsSinceStart >= MAX_ADAPTATIONS_PER_SESSION) {
            return AdaptiveConfig(
                level = currentLevel,
                adjustmentReason = StatsText(R.string.stats_adapt_max_reached),
                confidenceScore = 0.0f,
                previousLevel = currentLevel.name,
                puzzleType = puzzleType,
                adaptationCount = adaptationsSinceStart
            )
        }

        // Analyze recent performance
        val recentGames = history.takeLast(ANALYSIS_WINDOW)
        val latestGame = history.last()

        // Calculate performance metrics
        val avgAccuracy = recentGames.map { it.accuracy }.average().toFloat()
        val avgTime = recentGames.map { it.averageTime }.average().toFloat()
        val currentStreak = latestGame.streakLength
        val avgLivesRemaining = recentGames.map { it.livesRemaining }.average().toFloat()
        val avgTimeEfficiency = recentGames.map { it.timeEfficiency }.average().toFloat()
        val trend = calculatePerformanceTrend(history)
        val consistency = calculateConsistency(recentGames)

        // Calculate difficulty adjustment
        val adjustment = calculateDifficultyAdjustment(
            avgAccuracy, avgTime, currentStreak, avgLivesRemaining,
            avgTimeEfficiency, trend, consistency, thresholds
        )

        val previousLevelName = currentLevel.name

        when {
            adjustment > 0 && currentIndex < config.levels.size - 1 -> {
                // Increase difficulty
                val newIndex = minOf(currentIndex + adjustment, config.levels.size - 1)
                val newLevel = config.levels[newIndex]
                val reason = generateAdjustmentReason(adjustment, avgAccuracy, avgTime, currentStreak, true)
                currentLevelIndices[puzzleType] = newIndex
                adaptationsSinceStart++

                Log.d(TAG, "🔥 Difficulty increased for $puzzleType: ${currentLevel.name} → ${newLevel.name}")

                return AdaptiveConfig(
                    level = newLevel,
                    adjustmentReason = reason,
                    confidenceScore = calculateConfidence(adjustment, recentGames.size),
                    previousLevel = previousLevelName,
                    puzzleType = puzzleType,
                    adaptationCount = adaptationsSinceStart
                )
            }

            adjustment < 0 && currentIndex > 0 -> {
                // Decrease difficulty
                val newIndex = maxOf(currentIndex + adjustment, 0)
                val newLevel = config.levels[newIndex]
                val reason = generateAdjustmentReason(adjustment, avgAccuracy, avgTime, currentStreak, false)
                currentLevelIndices[puzzleType] = newIndex
                adaptationsSinceStart++

                Log.d(TAG, "📉 Difficulty decreased for $puzzleType: ${currentLevel.name} → ${newLevel.name}")

                return AdaptiveConfig(
                    level = newLevel,
                    adjustmentReason = reason,
                    confidenceScore = calculateConfidence(abs(adjustment), recentGames.size),
                    previousLevel = previousLevelName,
                    puzzleType = puzzleType,
                    adaptationCount = adaptationsSinceStart
                )
            }

            else -> {
                // No change needed
                return AdaptiveConfig(
                    level = currentLevel,
                    adjustmentReason = StatsText(R.string.stats_adapt_well_matched),
                    confidenceScore = 0.6f,
                    previousLevel = previousLevelName,
                    puzzleType = puzzleType,
                    adaptationCount = adaptationsSinceStart
                )
            }
        }
    }

    /**
     * Calculate difficulty adjustment based on multiple performance factors
     */
    private fun calculateDifficultyAdjustment(
        avgAccuracy: Float,
        avgTime: Float,
        streak: Int,
        avgLivesRemaining: Float,
        avgTimeEfficiency: Float,
        trend: Float,
        consistency: Float,
        thresholds: AdaptationThresholds
    ): Int {
        var adjustment = 0
        var confidenceScore = 0f

        // Primary factor: Accuracy
        when {
            avgAccuracy >= thresholds.excellentAccuracy -> {
                adjustment += if (avgTimeEfficiency < thresholds.timeEfficiencyUp) 2 else 1
                confidenceScore += 0.4f
            }
            avgAccuracy >= thresholds.goodAccuracy -> {
                if (avgTimeEfficiency < thresholds.timeEfficiencyUp && avgLivesRemaining > thresholds.livesThresholdUp) {
                    adjustment += 1
                    confidenceScore += 0.3f
                }
            }
            avgAccuracy <= thresholds.poorAccuracy -> {
                adjustment -= if (avgTimeEfficiency > thresholds.timeEfficiencyDown) 2 else 1
                confidenceScore += 0.4f
            }
        }

        // Secondary factor: Streak
        when {
            streak >= thresholds.streakThresholdUp -> {
                adjustment += 1
                confidenceScore += 0.2f
            }
            streak <= thresholds.streakThresholdDown && performanceHistory.values.any { it.size >= 3 } -> {
                adjustment -= 1
                confidenceScore += 0.2f
            }
        }

        // Third factor: Lives remaining
        when {
            avgLivesRemaining >= thresholds.livesThresholdUp && avgAccuracy > thresholds.goodAccuracy -> {
                adjustment += 1
                confidenceScore += 0.15f
            }
            avgLivesRemaining <= thresholds.livesThresholdDown -> {
                adjustment -= 1
                confidenceScore += 0.2f
            }
        }

        // Fourth factor: Time efficiency
        when {
            avgTimeEfficiency < thresholds.timeEfficiencyUp && avgAccuracy > thresholds.goodAccuracy -> {
                adjustment += 1
                confidenceScore += 0.15f
            }
            avgTimeEfficiency > thresholds.timeEfficiencyDown -> {
                adjustment -= 1
                confidenceScore += 0.15f
            }
        }

        // Fifth factor: Performance trend
        when {
            trend > 0.1f && avgAccuracy > thresholds.goodAccuracy -> {
                adjustment += 1
                confidenceScore += 0.1f
            }
            trend < -0.1f -> {
                adjustment -= 1
                confidenceScore += 0.15f
            }
        }

        // Sixth factor: Consistency (reduces volatility)
        if (consistency > 0.7f) {
            confidenceScore += 0.1f
        } else if (consistency < 0.3f) {
            // Reduce adjustment magnitude for inconsistent performance
            adjustment = (adjustment * 0.5f).toInt()
        }

        // Cap adjustment to prevent dramatic jumps
        return adjustment.coerceIn(-2, 2)
    }

    /**
     * Calculate performance trend over time
     */
    private fun calculatePerformanceTrend(history: List<PlayerPerformance>): Float {
        if (history.size < 4) return 0f

        val recent = history.takeLast(2).map { it.accuracy }.average()
        val older = history.takeLast(4).take(2).map { it.accuracy }.average()

        return (recent - older).toFloat()
    }

    /**
     * Calculate performance consistency
     */
    private fun calculateConsistency(recentGames: List<PlayerPerformance>): Float {
        if (recentGames.size < 2) return 1f

        val accuracies = recentGames.map { it.accuracy }
        val mean = accuracies.average().toFloat()
        val variance = accuracies.map { (it - mean).pow(2) }.average().toFloat()
        val stdDev = sqrt(variance)

        // Convert to consistency score (higher = more consistent)
        return maxOf(0f, 1f - (stdDev * 2f))
    }

    /**
     * Generate human-readable adjustment reason
     */
    private fun generateAdjustmentReason(
        adjustment: Int,
        accuracy: Float,
        time: Float,
        streak: Int,
        isIncrease: Boolean
    ): StatsText {
        return when {
            isIncrease -> {
                when {
                    accuracy >= 0.95f && time < 20f ->
                        StatsText(R.string.stats_adapt_outstanding, listOf((accuracy * 100).toInt()))
                    accuracy >= 0.95f ->
                        StatsText(R.string.stats_adapt_perfect_memory)
                    streak >= 5 ->
                        StatsText(R.plurals.stats_adapt_streak_up, listOf(streak), quantity = streak)
                    accuracy >= 0.8f && time < 25f ->
                        StatsText(R.string.stats_adapt_strong_fast)
                    else ->
                        StatsText(R.string.stats_adapt_consistent_improvement)
                }
            }
            else -> {
                when {
                    accuracy <= 0.3f && time > 45f ->
                        StatsText(R.string.stats_adapt_build_confidence)
                    accuracy <= 0.5f ->
                        StatsText(R.string.stats_adapt_optimal_level)
                    streak <= 1 ->
                        StatsText(R.string.stats_adapt_fine_tuning)
                    else ->
                        StatsText(R.string.stats_adapt_optimizing)
                }
            }
        }
    }

    /**
     * Calculate confidence in adjustment decision
     */
    private fun calculateConfidence(adjustmentMagnitude: Int, dataPoints: Int): Float {
        val baseConfidence = when (adjustmentMagnitude) {
            2 -> 0.9f  // Large adjustment = high confidence
            1 -> 0.7f  // Small adjustment = moderate confidence
            else -> 0.5f
        }

        // Boost confidence with more data
        val dataConfidence = minOf(dataPoints / 5f, 1f) * 0.2f

        return minOf(baseConfidence + dataConfidence, 1f)
    }

    /**
     * Get current difficulty for a puzzle type
     */
    fun getCurrentDifficulty(puzzleType: String): DifficultyLevel {
        val config = puzzleTypeConfigs[puzzleType] ?: puzzleTypeConfigs["average"]!!
        val currentIndex = currentLevelIndices[puzzleType] ?: config.defaultLevelIndex
        return config.levels[currentIndex]
    }

    /**
     * Get comprehensive performance analytics
     */
    fun getPerformanceAnalytics(puzzleType: String): Map<String, Any> {
        val history = performanceHistory[puzzleType] ?: emptyList()
        if (history.isEmpty()) return mapOf("error" to "No performance data available")

        val recent = history.takeLast(ANALYSIS_WINDOW)
        val currentLevel = getCurrentDifficulty(puzzleType)

        return mapOf(
            "averageAccuracy" to (recent.map { it.accuracy }.average() * 100).toInt(),
            "averageTime" to recent.map { it.averageTime }.average().toInt(),
            "currentStreak" to history.last().streakLength,
            "gamesPlayed" to history.size,
            "currentLevel" to currentLevel.name,
            "levelIndex" to currentLevel.index,
            "totalLevels" to (puzzleTypeConfigs[puzzleType]?.levels?.size ?: 0),
            "adaptationCount" to adaptationsSinceStart,
            "trend" to when {
                calculatePerformanceTrend(history) > 0.1f -> "Improving"
                calculatePerformanceTrend(history) < -0.1f -> "Declining"
                else -> "Stable"
            },
            "consistency" to (calculateConsistency(recent) * 100).toInt(),
            "totalScore" to history.sumOf { it.gameScore },
            "averageTimeEfficiency" to (recent.map { it.timeEfficiency }.average() * 100).toInt()
        )
    }

    /**
     * Force set difficulty level (manual override)
     */
    fun setDifficulty(puzzleType: String, levelName: String): Boolean {
        val config = puzzleTypeConfigs[puzzleType] ?: return false
        val levelIndex = config.levels.indexOfFirst { it.name == levelName }

        return if (levelIndex >= 0) {
            currentLevelIndices[puzzleType] = levelIndex
            Log.d(TAG, "🔧 Manual difficulty override for $puzzleType: $levelName")
            true
        } else {
            false
        }
    }

    /**
     * Predict next difficulty based on simulated performance
     */
    fun predictNextDifficulty(puzzleType: String, simulatedPerformance: PlayerPerformance): DifficultyLevel {
        val history = performanceHistory[puzzleType]?.toMutableList() ?: mutableListOf()
        history.add(simulatedPerformance)

        val config = puzzleTypeConfigs[puzzleType] ?: puzzleTypeConfigs["average"]!!
        val currentIndex = currentLevelIndices[puzzleType] ?: config.defaultLevelIndex
        val thresholds = config.thresholds

        val recentGames = history.takeLast(ANALYSIS_WINDOW)
        val avgAccuracy = recentGames.map { it.accuracy }.average().toFloat()
        val avgTimeEfficiency = recentGames.map { it.timeEfficiency }.average().toFloat()
        val avgLivesRemaining = recentGames.map { it.livesRemaining }.average().toFloat()

        val adjustment = calculateDifficultyAdjustment(
            avgAccuracy, 0f, simulatedPerformance.streakLength, avgLivesRemaining,
            avgTimeEfficiency, 0f, 1f, thresholds
        )

        val predictedIndex = (currentIndex + adjustment).coerceIn(0, config.levels.size - 1)
        return config.levels[predictedIndex]
    }

    /**
     * Reset session tracking
     */
    fun resetSession() {
        adaptationsSinceStart = 0
        Log.d(TAG, "🔄 Session reset - adaptation counter cleared")
    }

    /**
     * Reset all progress for a puzzle type or all types
     */
    fun resetProgress(puzzleType: String? = null) {
        if (puzzleType != null) {
            performanceHistory[puzzleType]?.clear()
            puzzleTypeConfigs[puzzleType]?.let { config ->
                currentLevelIndices[puzzleType] = config.defaultLevelIndex
            }
            Log.d(TAG, "🗑️ Progress reset for $puzzleType")
        } else {
            performanceHistory.clear()
            puzzleTypeConfigs.forEach { (type, config) ->
                currentLevelIndices[type] = config.defaultLevelIndex
            }
            Log.d(TAG, "🗑️ All progress reset")
        }
    }

    /**
     * Get all available puzzle types
     */
    fun getAllPuzzleTypes(): List<String> = puzzleTypeConfigs.keys.toList()

    /**
     * Get configuration for a specific puzzle type
     */
    fun getPuzzleConfig(puzzleType: String): PuzzleTypeConfig? = puzzleTypeConfigs[puzzleType]

    /**
     * Get overall statistics across all puzzle types
     */
    fun getOverallStats(): Map<String, Any> {
        val allHistory = performanceHistory.values.flatten()
        if (allHistory.isEmpty()) return mapOf("totalGames" to 0)

        return mapOf(
            "totalGames" to allHistory.size,
            "averageAccuracy" to (allHistory.map { it.accuracy }.average() * 100).toInt(),
            "totalScore" to allHistory.sumOf { it.gameScore },
            "puzzleTypesPlayed" to performanceHistory.keys.size,
            "adaptationsThisSession" to adaptationsSinceStart,
            "bestStreak" to (allHistory.maxOfOrNull { it.streakLength } ?: 0)
        )
    }
}

/** One-line description of this level in the current language (empty when it has none). */
@Composable
fun DifficultyManager.DifficultyLevel.descriptionText(): String =
    if (descriptionRes != 0) stringResource(descriptionRes) else ""
