// UnifiedDifficultyManager.kt - Comprehensive difficulty management system
package com.kreativekoala.riddleverse

import android.util.Log
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
        val description: String = ""
    )

    data class AdaptiveConfig(
        val level: DifficultyLevel,
        val adjustmentReason: String,
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
                DifficultyLevel("Tutorial", 0, 3, 2, 5, 90, 5, 50, 0.8f, "Very easy start"),
                DifficultyLevel("Beginner", 1, 3, 3, 4, 90, 4, 75, 0.9f, "Basic memory training"),
                DifficultyLevel("Easy", 2, 4, 3, 4, 80, 4, 100, 1.0f, "Slightly more complex"),
                DifficultyLevel("Easy+", 3, 4, 4, 4, 80, 4, 125, 1.1f, "Building confidence"),
                DifficultyLevel("Medium-", 4, 4, 5, 3, 70, 3, 150, 1.2f, "Stepping up"),
                DifficultyLevel("Medium", 5, 5, 5, 3, 70, 3, 175, 1.3f, "Balanced challenge"),
                DifficultyLevel("Medium+", 6, 5, 6, 3, 70, 3, 200, 1.4f, "Getting harder"),
                DifficultyLevel("Hard-", 7, 5, 7, 2, 60, 3, 225, 1.5f, "Challenging"),
                DifficultyLevel("Hard", 8, 6, 8, 2, 60, 3, 250, 1.6f, "Serious challenge"),
                DifficultyLevel("Hard+", 9, 6, 10, 2, 50, 2, 275, 1.8f, "Very challenging"),
                DifficultyLevel("Expert-", 10, 6, 12, 2, 50, 2, 300, 2.0f, "Expert level"),
                DifficultyLevel("Expert", 11, 7, 12, 2, 50, 2, 325, 2.2f, "True expert"),
                DifficultyLevel("Expert+", 12, 7, 15, 1, 45, 2, 350, 2.5f, "Beyond expert"),
                DifficultyLevel("Master", 13, 8, 18, 1, 40, 2, 400, 3.0f, "Master level"),
                DifficultyLevel("Grandmaster", 14, 8, 22, 1, 35, 1, 500, 4.0f, "Ultimate challenge")
            ),
            thresholds = AdaptationThresholds(),
            defaultLevelIndex = 5 // Start at Medium
        )

        puzzleTypeConfigs["crypto"] = PuzzleTypeConfig(
            name = "Crypto Puzzle",
            levels = listOf(
                DifficultyLevel("Tutorial", 0, 0, 0, 0, 600, 5, 75, 0.8f, "All instances revealed, no locks"),
                DifficultyLevel("Beginner", 1, 0, 0, 0, 480, 4, 100, 0.9f, "Auto-reveal mode, minimal complexity"),
                DifficultyLevel("Easy", 2, 0, 0, 0, 420, 4, 125, 1.0f, "Auto-reveal with some locked positions"),
                DifficultyLevel("Easy+", 3, 0, 0, 0, 360, 4, 150, 1.1f, "Transitioning to single reveals"),
                DifficultyLevel("Medium-", 4, 0, 0, 0, 300, 3, 175, 1.2f, "Single letter reveal mode begins"),
                DifficultyLevel("Medium", 5, 0, 0, 0, 270, 3, 200, 1.3f, "Single reveal with dependency chains"),
                DifficultyLevel("Medium+", 6, 0, 0, 0, 240, 3, 225, 1.4f, "More locked positions"),
                DifficultyLevel("Hard-", 7, 0, 0, 0, 210, 2, 250, 1.5f, "Complex dependency chains"),
                DifficultyLevel("Hard", 8, 0, 0, 0, 180, 2, 275, 1.6f, "Advanced cryptogram solving"),
                DifficultyLevel("Hard+", 9, 0, 0, 0, 150, 2, 300, 1.8f, "Maximum locked positions"),
                DifficultyLevel("Expert-", 10, 0, 0, 0, 120, 2, 325, 2.0f, "Expert pattern recognition"),
                DifficultyLevel("Expert", 11, 0, 0, 0, 100, 2, 350, 2.2f, "Master cryptographer"),
                DifficultyLevel("Expert+", 12, 0, 0, 0, 90, 1, 375, 2.5f, "Beyond expert solving"),
                DifficultyLevel("Master", 13, 0, 0, 0, 75, 1, 400, 3.0f, "Master level cryptograms"),
                DifficultyLevel("Grandmaster", 14, 0, 0, 0, 60, 1, 500, 4.0f, "Ultimate cipher challenge")
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
                DifficultyLevel("Beginner", 0, 3, 0, 0, 60, 5, 40, 0.8f, "3x3 grid, distinct shapes/colors"),
                DifficultyLevel("Easy", 1, 4, 0, 0, 45, 4, 60, 1.0f, "4x4 grid, some similar elements"),
                DifficultyLevel("Medium", 2, 5, 0, 0, 35, 3, 80, 1.2f, "5x5 grid, visual confusion"),
                DifficultyLevel("Hard", 3, 6, 0, 0, 30, 2, 100, 1.5f, "6x6 grid, complex patterns"),
                DifficultyLevel("Expert", 4, 7, 0, 0, 25, 2, 120, 2.0f, "Maximum complexity")
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
                DifficultyLevel("Beginner", 0, 7, 0, 0, 480, 5, 30, 0.8f, "Simple addition/subtraction"),
                DifficultyLevel("Easy", 1, 9, 0, 0, 600, 4, 50, 1.0f, "Basic operations"),
                DifficultyLevel("Medium", 2, 11, 0, 0, 720, 3, 70, 1.2f, "Mixed operations"),
                DifficultyLevel("Hard", 3, 13, 0, 0, 900, 2, 90, 1.5f, "Complex equations"),
                DifficultyLevel("Expert", 4, 15, 0, 0, 1080, 2, 110, 2.0f, "Advanced mathematics")
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
                DifficultyLevel("Beginner", 0, 3, 0, 0, 45, 5, 30, 0.8f, "3x3 copy patterns"),
                DifficultyLevel("Easy", 1, 3, 0, 0, 35, 4, 50, 1.0f, "3x3 with mirrors"),
                DifficultyLevel("Medium", 2, 4, 0, 0, 30, 3, 70, 1.2f, "4x4 complex mirrors"),
                DifficultyLevel("Hard", 3, 5, 0, 0, 25, 2, 90, 1.5f, "5x5 advanced patterns"),
                DifficultyLevel("Expert", 4, 6, 0, 0, 20, 2, 110, 2.0f, "6x6 maximum complexity")
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
                DifficultyLevel("Beginner", 0, 0, 12, 3, 90, 5, 40, 0.8f, "12 symbols, 3s each"),
                DifficultyLevel("Easy", 1, 0, 15, 2, 75, 4, 60, 1.0f, "15 symbols, 2.5s each"),
                DifficultyLevel("Medium", 2, 0, 20, 2, 60, 3, 80, 1.2f, "20 symbols, 2s each"),
                DifficultyLevel("Hard", 3, 0, 25, 1, 50, 2, 100, 1.5f, "25 symbols, 1.8s each"),
                DifficultyLevel("Expert", 4, 0, 30, 1, 40, 2, 120, 2.0f, "30 symbols, 1.5s each")
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
                DifficultyLevel("Beginner", 0, 0, 2, 0, 60, 5, 40, 0.8f, "2-3 simple numbers"),
                DifficultyLevel("Easy", 1, 0, 3, 0, 50, 4, 60, 1.0f, "3-4 numbers"),
                DifficultyLevel("Medium", 2, 0, 4, 0, 40, 3, 80, 1.2f, "4-5 numbers"),
                DifficultyLevel("Hard", 3, 0, 5, 0, 35, 2, 100, 1.5f, "5-6 complex numbers"),
                DifficultyLevel("Expert", 4, 0, 6, 0, 30, 2, 120, 2.0f, "6+ challenging numbers")
            ),
            thresholds = AdaptationThresholds()
        )

        puzzleTypeConfigs["dualcard"] = PuzzleTypeConfig(
            name = "Dual Card Task",
            levels = listOf(
                DifficultyLevel("Tutorial", 0, 0, 0, 0, 180, 5, 50, 0.8f, "8 rounds, predictable switching"),
                DifficultyLevel("Beginner", 1, 0, 0, 0, 150, 4, 75, 0.9f, "10 rounds, simple alternating"),
                DifficultyLevel("Easy", 2, 0, 0, 0, 120, 4, 100, 1.0f, "12 rounds, basic task switching"),
                DifficultyLevel("Easy+", 3, 0, 0, 0, 110, 4, 125, 1.1f, "15 rounds, more variety"),
                DifficultyLevel("Medium-", 4, 0, 0, 0, 100, 3, 150, 1.2f, "15 rounds, alternating pattern"),
                DifficultyLevel("Medium", 5, 0, 0, 0, 90, 3, 175, 1.3f, "15 rounds, mixed switching"),
                DifficultyLevel("Medium+", 6, 0, 0, 0, 80, 3, 200, 1.4f, "18 rounds, complex switching"),
                DifficultyLevel("Hard-", 7, 0, 0, 0, 75, 2, 225, 1.5f, "20 rounds, random switching"),
                DifficultyLevel("Hard", 8, 0, 0, 0, 70, 2, 250, 1.6f, "20 rounds, high complexity"),
                DifficultyLevel("Hard+", 9, 0, 0, 0, 65, 2, 275, 1.8f, "22 rounds, maximum switching"),
                DifficultyLevel("Expert-", 10, 0, 0, 0, 60, 2, 300, 2.0f, "25 rounds, expert switching"),
                DifficultyLevel("Expert", 11, 0, 0, 0, 55, 2, 325, 2.2f, "25 rounds, master level"),
                DifficultyLevel("Expert+", 12, 0, 0, 0, 50, 1, 350, 2.5f, "30 rounds, beyond expert"),
                DifficultyLevel("Master", 13, 0, 0, 0, 45, 1, 400, 3.0f, "30 rounds, master switching"),
                DifficultyLevel("Grandmaster", 14, 0, 0, 0, 40, 1, 500, 4.0f, "35 rounds, ultimate challenge")
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
                DifficultyLevel("Tutorial", 0, 0, 0, 0, 180, 5, 50, 0.8f, "Simple prices, obvious discounts"),
                DifficultyLevel("Beginner", 1, 0, 0, 0, 150, 4, 75, 0.9f, "Basic discount calculations"),
                DifficultyLevel("Easy", 2, 0, 0, 0, 120, 4, 100, 1.0f, "Mixed prices with clear discounts"),
                DifficultyLevel("Easy+", 3, 0, 0, 0, 110, 4, 125, 1.1f, "More items to compare"),
                DifficultyLevel("Medium-", 4, 0, 0, 0, 100, 3, 150, 1.2f, "Complex discount percentages"),
                DifficultyLevel("Medium", 5, 0, 0, 0, 90, 3, 175, 1.3f, "Multiple discount types"),
                DifficultyLevel("Medium+", 6, 0, 0, 0, 80, 3, 200, 1.4f, "Closer final prices"),
                DifficultyLevel("Hard-", 7, 0, 0, 0, 70, 2, 225, 1.5f, "Similar final prices"),
                DifficultyLevel("Hard", 8, 0, 0, 0, 60, 2, 250, 1.6f, "Advanced discount math"),
                DifficultyLevel("Hard+", 9, 0, 0, 0, 55, 2, 275, 1.8f, "Very close comparisons"),
                DifficultyLevel("Expert", 10, 0, 0, 0, 50, 2, 300, 2.0f, "Expert price analysis"),
                DifficultyLevel("Master", 11, 0, 0, 0, 45, 2, 350, 2.5f, "Master discount calculations"),
                DifficultyLevel("Grandmaster", 12, 0, 0, 0, 40, 1, 400, 3.0f, "Ultimate pricing challenge")
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
                DifficultyLevel("Tutorial", 0, 0, 6, 3, 120, 5, 50, 0.8f, "6 patterns, slow pace"),
                DifficultyLevel("Beginner", 1, 0, 8, 2, 100, 4, 70, 0.9f, "8 patterns, comfortable timing"),
                DifficultyLevel("Easy", 2, 0, 10, 2, 90, 4, 90, 1.0f, "10 patterns, steady pace"),
                DifficultyLevel("Easy+", 3, 0, 12, 2, 85, 4, 110, 1.1f, "12 patterns, building challenge"),
                DifficultyLevel("Medium-", 4, 0, 12, 2, 80, 3, 130, 1.2f, "12 patterns, faster pace"),
                DifficultyLevel("Medium", 5, 0, 15, 2, 75, 3, 150, 1.3f, "15 patterns, standard challenge"),
                DifficultyLevel("Medium+", 6, 0, 16, 1, 70, 3, 170, 1.4f, "16 patterns, increased difficulty"),
                DifficultyLevel("Hard-", 7, 0, 18, 1, 65, 2, 190, 1.5f, "18 patterns, challenging pace"),
                DifficultyLevel("Hard", 8, 0, 20, 1, 60, 2, 210, 1.6f, "20 patterns, fast decisions"),
                DifficultyLevel("Hard+", 9, 0, 22, 1, 55, 2, 230, 1.8f, "22 patterns, very challenging"),
                DifficultyLevel("Expert-", 10, 0, 25, 1, 50, 2, 250, 2.0f, "25 patterns, expert memory"),
                DifficultyLevel("Expert", 11, 0, 28, 1, 45, 2, 270, 2.2f, "28 patterns, master level"),
                DifficultyLevel("Expert+", 12, 0, 30, 1, 40, 1, 290, 2.5f, "30 patterns, beyond expert"),
                DifficultyLevel("Master", 13, 0, 35, 1, 35, 1, 320, 3.0f, "35 patterns, master memory"),
                DifficultyLevel("Grandmaster", 14, 0, 40, 1, 30, 1, 400, 4.0f, "40 patterns, ultimate memory test")
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
                adjustmentReason = "Gathering performance data (${history.size}/${thresholds.minGamesForAdaptation})",
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
                adjustmentReason = "Maximum adaptations reached for this session",
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
                    adjustmentReason = "Performance is well-matched to current difficulty",
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
    ): String {
        return when {
            isIncrease -> {
                when {
                    accuracy >= 0.95f && time < 20f ->
                        "🚀 Outstanding performance! ${(accuracy * 100).toInt()}% accuracy with lightning speed!"
                    accuracy >= 0.95f ->
                        "🧠 Perfect memory skills! Time for a bigger challenge"
                    streak >= 5 ->
                        "🔥 Incredible ${streak}-game winning streak! Level up time!"
                    accuracy >= 0.8f && time < 25f ->
                        "💪 Strong and fast performance! Ready for more"
                    else ->
                        "✨ Consistent improvement detected! Advancing difficulty"
                }
            }
            else -> {
                when {
                    accuracy <= 0.3f && time > 45f ->
                        "📚 Let's build confidence with an easier level"
                    accuracy <= 0.5f ->
                        "🎯 Adjusting to your optimal challenge level"
                    streak <= 1 ->
                        "🔧 Fine-tuning difficulty for better flow"
                    else ->
                        "⚖️ Optimizing challenge level for you"
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