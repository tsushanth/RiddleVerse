package com.kreativekoala.riddleverse

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import kotlin.random.Random

object SymmetryPuzzleGenerator {
    private const val TAG = "SymmetryPuzzleGenerator"
    private const val PREFS_NAME = "symmetry_puzzle_prefs"
    private const val KEY_CONSECUTIVE_CORRECT = "consecutive_correct"
    private const val KEY_CURRENT_QUESTION = "current_question"
    private const val KEY_TOTAL_SCORE = "total_score"
    private const val KEY_TOTAL_PUZZLES_PLAYED = "total_puzzles_played"
    private const val KEY_TOTAL_CORRECT = "total_correct"
    private const val KEY_MIRROR_PUZZLES_PLAYED = "mirror_puzzles_played"
    private const val KEY_MIRROR_PUZZLES_CORRECT = "mirror_puzzles_correct"
    private const val KEY_SKILL_LEVEL = "skill_level" // 0=beginner, 1=intermediate, 2=advanced

    fun generatePuzzle(difficulty: String, context: Context? = null): Pair<String, String> {
        val prefs = context?.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        // Get current progression
        val consecutiveCorrect = prefs?.getInt(KEY_CONSECUTIVE_CORRECT, 0) ?: 0
        val currentQuestion = prefs?.getInt(KEY_CURRENT_QUESTION, 1) ?: 1

        // Determine grid size based on progression and difficulty
        val gridSize = determineGridSize(difficulty, consecutiveCorrect, currentQuestion)

        // Determine if this should be a mirror puzzle
        val isMirror = shouldUseMirror(difficulty, consecutiveCorrect, currentQuestion)
        val mirrorType = if (isMirror) {
            // Start with horizontal mirrors, add vertical as difficulty increases
            when (difficulty.lowercase()) {
                "easy" -> "horizontal"
                "medium" -> listOf("horizontal", "vertical").random()
                "hard" -> listOf("horizontal", "vertical").random()
                else -> "horizontal"
            }
        } else {
            "none"
        }

        Log.d(TAG, "🎯 Generating symmetry puzzle:")
        Log.d(TAG, "   Difficulty: $difficulty")
        Log.d(TAG, "   Question: $currentQuestion")
        Log.d(TAG, "   Consecutive correct: $consecutiveCorrect")
        Log.d(TAG, "   Grid size: ${gridSize}x${gridSize}")
        Log.d(TAG, "   Is mirror: $isMirror")
        Log.d(TAG, "   Mirror type: $mirrorType")

        // Generate the left pattern (what user needs to copy/mirror)
        val leftPattern = generateSymmetryPattern(gridSize, difficulty, isMirror)

        // Generate the correct right pattern based on mirror mode
        val correctRightPattern = if (isMirror) {
            when (mirrorType) {
                "horizontal" -> mirrorPatternHorizontally(leftPattern)
                "vertical" -> mirrorPatternVertically(leftPattern)
                else -> leftPattern // fallback
            }
        } else {
            leftPattern // exact copy
        }

        // Create the puzzle data JSON
        val questionJson = JSONObject().apply {
            put("difficulty", difficulty)
            put("gridSize", gridSize)
            put("questionNumber", currentQuestion)
            put("consecutiveCorrect", consecutiveCorrect)
            put("leftPattern", patternToJsonArray(leftPattern))
            put("isMirror", isMirror)
            put("mirrorType", mirrorType)
            put("maxQuestions", getMaxQuestions(difficulty))
            put("instructions", if (isMirror) "Create a $mirrorType mirror reflection" else "Copy the exact pattern")
            put("gameType", "symmetry")
        }

        // Create the answer JSON
        val answerJson = JSONObject().apply {
            put("correctPattern", patternToJsonArray(correctRightPattern))
            put("gridSize", gridSize)
            put("isMirror", isMirror)
            put("mirrorType", mirrorType)
            put("expectedScore", calculateExpectedScore(gridSize, isMirror))
        }

        Log.d(TAG, "✅ Generated ${if (isMirror) "mirror" else "copy"} puzzle with ${countFilledCells(leftPattern)} filled cells")

        return Pair(questionJson.toString(), answerJson.toString())
    }

    private fun shouldUseMirror(difficulty: String, consecutiveCorrect: Int, questionNumber: Int): Boolean {
        // FOR TESTING: Temporarily force more mirrors to appear
        // You can remove this and use the original logic below once testing is complete

        // TESTING VERSION - 50% chance for mirrors to make them appear more frequently
        if (questionNumber <= 3) {
            return questionNumber % 2 == 0 // Every other question for first 3
        }

        // Original logic with earlier introduction for testing
        return when (difficulty.lowercase()) {
            "easy" -> {
                // Start introducing mirrors immediately for testing
                if (consecutiveCorrect >= 1) {
                    (questionNumber % 3) == 0 // Every 3rd question is a mirror
                } else {
                    questionNumber % 4 == 0 // Every 4th question
                }
            }
            "medium" -> {
                // More frequent mirrors for testing
                if (consecutiveCorrect >= 1) {
                    (questionNumber % 2) == 0 // Every other question is a mirror
                } else {
                    (questionNumber % 3) == 0 // Every 3rd question
                }
            }
            "hard" -> {
                // Most questions are mirrors in hard mode
                if (consecutiveCorrect >= 1) {
                    (questionNumber % 3) != 0 // 2 out of 3 questions are mirrors
                } else {
                    (questionNumber % 2) == 0 // Every other question
                }
            }
            else -> (questionNumber % 2) == 0 // 50% chance
        }

        /* ORIGINAL LOGIC - USE THIS AFTER TESTING:
        return when (difficulty.lowercase()) {
            "easy" -> {
                // Start introducing mirrors after 5 consecutive correct
                if (consecutiveCorrect >= 5) {
                    (questionNumber % 4) == 0 // Every 4th question is a mirror
                } else {
                    false // No mirrors until they've shown consistency
                }
            }
            "medium" -> {
                // Introduce mirrors earlier and more frequently
                if (consecutiveCorrect >= 3) {
                    (questionNumber % 3) != 0 // 2 out of 3 questions are mirrors
                } else {
                    (questionNumber % 5) == 0 // Every 5th question until consistent
                }
            }
            "hard" -> {
                // Most questions are mirrors in hard mode
                if (consecutiveCorrect >= 2) {
                    (questionNumber % 4) != 0 // 3 out of 4 questions are mirrors
                } else {
                    (questionNumber % 2) == 0 // Every other question until consistent
                }
            }
            else -> Random.nextBoolean()
        }
        */
    }

    fun recordResult(context: Context, isCorrect: Boolean, score: Int) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val consecutiveCorrect = prefs.getInt(KEY_CONSECUTIVE_CORRECT, 0)
        val currentQuestion = prefs.getInt(KEY_CURRENT_QUESTION, 1)
        val totalScore = prefs.getInt(KEY_TOTAL_SCORE, 0)

        prefs.edit().apply {
            if (isCorrect) {
                // Increase consecutive correct count
                putInt(KEY_CONSECUTIVE_CORRECT, consecutiveCorrect + 1)
                putInt(KEY_TOTAL_SCORE, totalScore + score)
                Log.d(TAG, "✅ Correct answer! Consecutive: ${consecutiveCorrect + 1}")
            } else {
                // Reset consecutive count on wrong answer
                putInt(KEY_CONSECUTIVE_CORRECT, 0)
                Log.d(TAG, "❌ Wrong answer! Reset consecutive count")
            }

            // Always increment question number
            putInt(KEY_CURRENT_QUESTION, currentQuestion + 1)
            apply()
        }
    }

    fun resetProgression(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().clear().apply()
        Log.d(TAG, "🔄 Reset progression")
    }

    private fun determineGridSize(
        difficulty: String,
        consecutiveCorrect: Int,
        questionNumber: Int
    ): Int {
        return when (difficulty.lowercase()) {
            "easy" -> when {
                consecutiveCorrect >= 10 -> 4 // After 10 correct, move to 4x4
                consecutiveCorrect >= 5 -> 3  // After 5 correct, stay at 3x3
                else -> 3  // Start with 3x3
            }

            "medium" -> when {
                consecutiveCorrect >= 15 -> 5 // After 15 correct, move to 5x5
                consecutiveCorrect >= 8 -> 4  // After 8 correct, move to 4x4
                consecutiveCorrect >= 3 -> 3  // After 3 correct, stay at 3x3
                else -> 3  // Start with 3x3
            }

            "hard" -> when {
                consecutiveCorrect >= 20 -> 6 // After 20 correct, move to 6x6
                consecutiveCorrect >= 12 -> 5 // After 12 correct, move to 5x5
                consecutiveCorrect >= 6 -> 4  // After 6 correct, move to 4x4
                consecutiveCorrect >= 2 -> 3  // After 2 correct, stay at 3x3
                else -> 3  // Start with 3x3
            }

            else -> 3 // Default fallback
        }.coerceIn(3, 6) // Ensure grid size stays between 3x3 and 6x6
    }

    private fun generateSymmetryPattern(gridSize: Int, difficulty: String, isMirror: Boolean = false): List<List<Boolean>> {
        val pattern = MutableList(gridSize) { MutableList(gridSize) { false } }

        // For mirror puzzles, we want patterns that look good when mirrored
        // For copy puzzles, we can use any pattern
        val fillDensity = when (difficulty.lowercase()) {
            "easy" -> when (gridSize) {
                3 -> if (isMirror) 0.25f else 0.30f  // Simpler patterns for mirrors
                4 -> if (isMirror) 0.20f else 0.25f
                else -> 0.20f
            }
            "medium" -> when (gridSize) {
                3 -> if (isMirror) 0.35f else 0.40f
                4 -> if (isMirror) 0.30f else 0.35f
                5 -> if (isMirror) 0.25f else 0.30f
                else -> 0.25f
            }
            "hard" -> when (gridSize) {
                3 -> if (isMirror) 0.45f else 0.50f
                4 -> if (isMirror) 0.40f else 0.45f
                5 -> if (isMirror) 0.35f else 0.40f
                6 -> if (isMirror) 0.30f else 0.35f
                else -> 0.30f
            }
            else -> 0.30f
        }

        // Calculate target number of filled cells
        val totalCells = gridSize * gridSize
        val targetFilledCells = (totalCells * fillDensity).toInt().coerceIn(1, totalCells - 1)

        // Create patterns optimized for the type of puzzle
        if (isMirror && Random.nextFloat() < 0.6f) {
            // 60% chance for asymmetric patterns that look good when mirrored
            createAsymmetricPattern(pattern, targetFilledCells, gridSize)
        } else if (Random.nextFloat() < 0.4f) {
            // 40% chance for structured patterns (easier to recognize quickly)
            createStructuredPattern(pattern, targetFilledCells, gridSize)
        } else {
            // Random patterns
            createRandomPattern(pattern, targetFilledCells, gridSize)
        }

        return pattern.map { it.toList() }
    }

    private fun createAsymmetricPattern(
        pattern: MutableList<MutableList<Boolean>>,
        targetCells: Int,
        gridSize: Int
    ) {
        // Create patterns that are intentionally asymmetric for better mirror puzzles
        val patterns = when (gridSize) {
            3 -> listOf("L_shape", "step", "corner_cluster", "diagonal")
            4 -> listOf("L_shape", "step", "corner_cluster", "diagonal", "zigzag")
            5 -> listOf("L_shape", "step", "spiral", "diagonal", "zigzag")
            6 -> listOf("L_shape", "step", "spiral", "diagonal", "zigzag", "wave")
            else -> listOf("L_shape", "step", "diagonal")
        }

        when (patterns.random()) {
            "L_shape" -> {
                // Create an L-shaped pattern
                val startRow = Random.nextInt(gridSize - 2)
                val startCol = Random.nextInt(gridSize - 2)
                val armLength = Random.nextInt(2, gridSize - startRow)

                // Vertical arm
                for (i in 0 until armLength) {
                    if (startRow + i < gridSize) {
                        pattern[startRow + i][startCol] = true
                    }
                }
                // Horizontal arm
                for (i in 0 until armLength) {
                    if (startCol + i < gridSize) {
                        pattern[startRow][startCol + i] = true
                    }
                }
            }
            "step" -> {
                // Create a step pattern
                for (i in 0 until gridSize - 1) {
                    if (i < gridSize && i + 1 < gridSize) {
                        pattern[i][i] = true
                        pattern[i + 1][i] = true
                    }
                }
            }
            "diagonal" -> {
                // Create a diagonal line with some offset
                val offset = Random.nextInt(-1, 2)
                for (i in 0 until gridSize) {
                    val col = i + offset
                    if (col in 0 until gridSize) {
                        pattern[i][col] = true
                    }
                }
            }
            else -> createRandomPattern(pattern, targetCells, gridSize)
        }

        // Adjust to match target cell count
        adjustPatternToTarget(pattern, targetCells, gridSize)
    }

    private fun createStructuredPattern(
        pattern: MutableList<MutableList<Boolean>>,
        targetCells: Int,
        gridSize: Int
    ) {
        val patterns = when (gridSize) {
            3 -> listOf("cross", "corners", "line", "L_shape")
            4 -> listOf("cross", "corners", "diamond", "line", "square")
            5 -> listOf("plus", "corners", "diamond", "cross", "border")
            6 -> listOf("plus", "diamond", "border", "cross", "corners")
            else -> listOf("cross", "corners", "line")
        }

        when (patterns.random()) {
            "cross" -> {
                val center = gridSize / 2
                // Vertical line
                for (row in 0 until gridSize) {
                    pattern[row][center] = true
                }
                // Horizontal line
                for (col in 0 until gridSize) {
                    pattern[center][col] = true
                }
            }
            "corners" -> {
                pattern[0][0] = true
                pattern[0][gridSize-1] = true
                pattern[gridSize-1][0] = true
                pattern[gridSize-1][gridSize-1] = true
            }
            "diamond" -> {
                val center = gridSize / 2
                for (i in 0 until gridSize) {
                    for (j in 0 until gridSize) {
                        if (kotlin.math.abs(i - center) + kotlin.math.abs(j - center) == center) {
                            pattern[i][j] = true
                        }
                    }
                }
            }
            "line" -> {
                val isVertical = Random.nextBoolean()
                val linePos = Random.nextInt(gridSize)
                if (isVertical) {
                    for (row in 0 until gridSize) {
                        pattern[row][linePos] = true
                    }
                } else {
                    for (col in 0 until gridSize) {
                        pattern[linePos][col] = true
                    }
                }
            }
            else -> createRandomPattern(pattern, targetCells, gridSize)
        }

        // Adjust to match target cell count
        adjustPatternToTarget(pattern, targetCells, gridSize)
    }

    private fun createRandomPattern(
        pattern: MutableList<MutableList<Boolean>>,
        targetCells: Int,
        gridSize: Int
    ) {
        val positions = mutableListOf<Pair<Int, Int>>()
        for (row in 0 until gridSize) {
            for (col in 0 until gridSize) {
                positions.add(Pair(row, col))
            }
        }
        positions.shuffle()

        // Fill the target number of cells
        for (i in 0 until targetCells.coerceAtMost(positions.size)) {
            val (row, col) = positions[i]
            pattern[row][col] = true
        }
    }

    private fun adjustPatternToTarget(
        pattern: MutableList<MutableList<Boolean>>,
        targetCells: Int,
        gridSize: Int
    ) {
        val currentCount = pattern.sumOf { row -> row.count { it } }

        if (currentCount < targetCells) {
            // Add more cells
            val emptyCells = mutableListOf<Pair<Int, Int>>()
            for (row in 0 until gridSize) {
                for (col in 0 until gridSize) {
                    if (!pattern[row][col]) {
                        emptyCells.add(Pair(row, col))
                    }
                }
            }
            emptyCells.shuffle()

            val toAdd = (targetCells - currentCount).coerceAtMost(emptyCells.size)
            for (i in 0 until toAdd) {
                val (row, col) = emptyCells[i]
                pattern[row][col] = true
            }
        } else if (currentCount > targetCells) {
            // Remove some cells
            val filledCells = mutableListOf<Pair<Int, Int>>()
            for (row in 0 until gridSize) {
                for (col in 0 until gridSize) {
                    if (pattern[row][col]) {
                        filledCells.add(Pair(row, col))
                    }
                }
            }
            filledCells.shuffle()

            val toRemove = currentCount - targetCells
            for (i in 0 until toRemove) {
                val (row, col) = filledCells[i]
                pattern[row][col] = false
            }
        }
    }

    private fun patternToJsonArray(pattern: List<List<Boolean>>): JSONArray {
        val jsonArray = JSONArray()
        pattern.forEach { row ->
            val rowArray = JSONArray()
            row.forEach { cell ->
                rowArray.put(cell)
            }
            jsonArray.put(rowArray)
        }
        return jsonArray
    }

    private fun countFilledCells(pattern: List<List<Boolean>>): Int {
        return pattern.sumOf { row -> row.count { it } }
    }

    private fun calculateExpectedScore(gridSize: Int, isMirror: Boolean = false): Int {
        val baseScore = gridSize * gridSize * 15 // 15 points per cell
        val mirrorBonus = if (isMirror) (baseScore * 0.5).toInt() else 0 // 50% bonus for mirror puzzles
        return baseScore + mirrorBonus
    }

    // Mirror transformation functions
    private fun mirrorPatternHorizontally(pattern: List<List<Boolean>>): List<List<Boolean>> {
        return pattern.map { row -> row.reversed() }
    }

    private fun mirrorPatternVertically(pattern: List<List<Boolean>>): List<List<Boolean>> {
        return pattern.reversed()
    }

    private fun getMaxQuestions(difficulty: String): Int {
        return when (difficulty.lowercase()) {
            "easy" -> 30
            "medium" -> 40
            "hard" -> 50
            else -> 40
        }
    }

    // Helper function to get current progression stats
    fun getProgressionStats(context: Context): Triple<Int, Int, Int> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val consecutiveCorrect = prefs.getInt(KEY_CONSECUTIVE_CORRECT, 0)
        val currentQuestion = prefs.getInt(KEY_CURRENT_QUESTION, 1)
        val totalScore = prefs.getInt(KEY_TOTAL_SCORE, 0)

        return Triple(consecutiveCorrect, currentQuestion, totalScore)
    }

    // Helper function to determine if user should advance to next grid size
    fun shouldAdvanceGridSize(
        difficulty: String,
        consecutiveCorrect: Int,
        currentGridSize: Int
    ): Boolean {
        val nextGridSize = determineGridSize(difficulty, consecutiveCorrect, 0)
        return nextGridSize > currentGridSize
    }

    // Helper function to get encouragement message based on progression
    fun getProgressionMessage(
        difficulty: String,
        consecutiveCorrect: Int,
        currentGridSize: Int
    ): String {
        val nextThreshold = when (difficulty.lowercase()) {
            "easy" -> when (currentGridSize) {
                3 -> 10 - consecutiveCorrect
                else -> 0
            }
            "medium" -> when (currentGridSize) {
                3 -> 8 - consecutiveCorrect
                4 -> 15 - consecutiveCorrect
                else -> 0
            }
            "hard" -> when (currentGridSize) {
                3 -> 6 - consecutiveCorrect
                4 -> 12 - consecutiveCorrect
                5 -> 20 - consecutiveCorrect
                else -> 0
            }
            else -> 0
        }

        return when {
            nextThreshold <= 0 -> "🎉 You've mastered this level!"
            nextThreshold <= 2 -> "🔥 Almost there! Just $nextThreshold more correct!"
            nextThreshold <= 5 -> "💪 Keep going! $nextThreshold more to advance!"
            else -> "🌟 $consecutiveCorrect correct in a row! Keep it up!"
        }
    }
}