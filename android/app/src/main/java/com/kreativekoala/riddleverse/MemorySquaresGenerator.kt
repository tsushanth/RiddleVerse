package com.kreativekoala.riddleverse

import kotlin.random.Random

/**
 * Local generator for Memory Squares puzzles
 * Generates n×n matrices with m positions set to 1 and rest to 0
 */
object MemorySquaresGenerator {

    data class DifficultySettings(
        val gridSize: Int,
        val targetCount: Int,
        val timeLimit: Int
    )

    private val difficultySettings = mapOf(
        "easy" to DifficultySettings(gridSize = 4, targetCount = 4, timeLimit = 8),
        "medium" to DifficultySettings(gridSize = 5, targetCount = 8, timeLimit = 10),
        "hard" to DifficultySettings(gridSize = 6, targetCount = 12, timeLimit = 12),
        "expert" to DifficultySettings(gridSize = 7, targetCount = 18, timeLimit = 15)
    )

    /**
     * Generate a complete Memory Squares puzzle
     */
    fun generatePuzzle(difficulty: String = "medium"): Pair<String, String> {
        val settings = difficultySettings[difficulty.lowercase()]
            ?: difficultySettings["medium"]!!

        val gridSize = settings.gridSize
        val targetCount = settings.targetCount
        val timeLimit = settings.timeLimit

        // Validate that targetCount doesn't exceed total positions
        if (targetCount > gridSize * gridSize) {
            throw Exception("Cannot place $targetCount ones in ${gridSize}×${gridSize} matrix (max: ${gridSize * gridSize})")
        }

        // Generate matrix with targetCount random positions set to 1
        val matrix = generateMatrix(gridSize, targetCount)
        val positions = getOnesPositions(matrix)
        val compressed = compressMatrix(matrix)

        // Create matrix data
        val matrixData = mapOf(
            "matrix" to matrix,
            "size" to gridSize,
            "onesCount" to targetCount,
            "difficulty" to difficulty,
            "timeLimit" to timeLimit,
            "positions" to positions,
            "compressed" to compressed
        )

        // Build question JSON
        val questionJson = buildJsonString(matrixData)

        // Build answer JSON (positions of 1s)
        val answerJson = buildJsonString(positions)

        return Pair(questionJson, answerJson)
    }

    /**
     * Generate n×n matrix with exactly m positions set to 1
     */
    private fun generateMatrix(gridSize: Int, targetCount: Int): List<List<Int>> {
        // Initialize matrix with all zeros
        val matrix = MutableList(gridSize) { MutableList(gridSize) { 0 } }

        // Generate all possible positions
        val allPositions = mutableListOf<Pair<Int, Int>>()
        for (i in 0 until gridSize) {
            for (j in 0 until gridSize) {
                allPositions.add(Pair(i, j))
            }
        }

        // Randomly select targetCount positions
        val selectedPositions = getRandomPositions(allPositions, targetCount)

        // Set selected positions to 1
        selectedPositions.forEach { (row, col) ->
            matrix[row][col] = 1
        }

        return matrix
    }

    /**
     * Randomly select count positions from available positions
     */
    private fun getRandomPositions(positions: List<Pair<Int, Int>>, count: Int): List<Pair<Int, Int>> {
        if (positions.size < count) {
            throw Exception("Not enough positions available. Need $count, have ${positions.size}")
        }

        return positions.shuffled().take(count)
    }

    /**
     * Get positions of all 1s in the matrix
     */
    private fun getOnesPositions(matrix: List<List<Int>>): List<List<Int>> {
        val positions = mutableListOf<List<Int>>()
        for (i in matrix.indices) {
            for (j in matrix[i].indices) {
                if (matrix[i][j] == 1) {
                    positions.add(listOf(i, j))
                }
            }
        }
        return positions
    }

    /**
     * Compress matrix to string for efficient storage
     */
    private fun compressMatrix(matrix: List<List<Int>>): String {
        return matrix.joinToString("") { row ->
            row.joinToString("")
        }
    }

    /**
     * Decompress string back to matrix
     */
    fun decompressMatrix(compressed: String, size: Int): List<List<Int>> {
        val matrix = mutableListOf<List<Int>>()
        for (i in 0 until size) {
            val row = mutableListOf<Int>()
            for (j in 0 until size) {
                val index = i * size + j
                val value = if (index < compressed.length) {
                    compressed[index].toString().toIntOrNull() ?: 0
                } else 0
                row.add(value)
            }
            matrix.add(row)
        }
        return matrix
    }

    /**
     * Validate a user's answer against the correct positions
     */
    fun validateAnswer(
        userPositions: List<List<Int>>,
        correctPositions: List<List<Int>>,
        tolerance: Int = 20
    ): ValidationResult {
        return try {
            // Convert to sets of strings for comparison
            val userSet = userPositions.map { "${it[0]},${it[1]}" }.toSet()
            val correctSet = correctPositions.map { "${it[0]},${it[1]}" }.toSet()

            // Calculate matches
            val matches = userSet.intersect(correctSet).size
            val accuracy = if (correctSet.isNotEmpty()) {
                (matches * 100) / correctSet.size
            } else 100

            // Allow for tolerance (e.g., 80% accuracy)
            val threshold = 100 - tolerance
            val isCorrect = accuracy >= threshold

            ValidationResult(
                isCorrect = isCorrect,
                accuracy = accuracy,
                matches = matches,
                total = correctSet.size,
                feedback = generateFeedback(accuracy, matches, correctSet.size)
            )
        } catch (e: Exception) {
            ValidationResult(
                isCorrect = false,
                accuracy = 0,
                matches = 0,
                total = 0,
                feedback = "Error validating answer: ${e.message}"
            )
        }
    }

    /**
     * Generate feedback based on performance
     */
    private fun generateFeedback(accuracy: Int, matches: Int, total: Int): String {
        return when {
            accuracy >= 100 -> "Perfect! You remembered all positions correctly! 🎉"
            accuracy >= 80 -> "Excellent! You got $matches/$total positions correct! 👏"
            accuracy >= 60 -> "Good job! You remembered $matches/$total positions. Keep practicing! 👍"
            accuracy >= 40 -> "Not bad! You got $matches/$total correct. Try to focus more on the pattern! 🤔"
            else -> "Keep trying! You got $matches/$total correct. Practice makes perfect! 💪"
        }
    }

    /**
     * Get puzzle statistics for analysis
     */
    fun getPuzzleStats(difficulty: String): PuzzleStats? {
        val settings = difficultySettings[difficulty.lowercase()] ?: return null
        val gridSize = settings.gridSize
        val targetCount = settings.targetCount
        val timeLimit = settings.timeLimit

        return PuzzleStats(
            difficulty = difficulty,
            gridSize = "${gridSize}×${gridSize}",
            totalCells = gridSize * gridSize,
            targetCells = targetCount,
            emptyCells = gridSize * gridSize - targetCount,
            density = "${((targetCount.toDouble() / (gridSize * gridSize)) * 100).toInt()}%",
            timeLimit = "${timeLimit} seconds",
            estimatedDifficulty = calculateDifficultyScore(gridSize, targetCount, timeLimit)
        )
    }

    /**
     * Calculate difficulty score based on grid size, target count, and time
     */
    private fun calculateDifficultyScore(gridSize: Int, targetCount: Int, timeLimit: Int): String {
        // Factors: grid complexity, target density, time pressure
        val gridComplexity = gridSize * gridSize
        val density = targetCount.toDouble() / (gridSize * gridSize)
        val timePressure = 1.0 / timeLimit

        val score = (gridComplexity * density * timePressure * 1000)

        return when {
            score < 5 -> "Very Easy"
            score < 15 -> "Easy"
            score < 35 -> "Medium"
            score < 60 -> "Hard"
            else -> "Very Hard"
        }
    }

    /**
     * Build JSON string from nested data structures
     */
    private fun buildJsonString(data: Any): String {
        return when (data) {
            is Map<*, *> -> {
                val entries = data.entries.joinToString(",") { (key, value) ->
                    "\"$key\":${value?.let { buildJsonString(it) }}"
                }
                "{$entries}"
            }
            is List<*> -> {
                val elements = data.joinToString(",") {
                    (if (it != null) {
                        buildJsonString(it)
                    } else {
                        ""
                    }).toString()
                }
                "[$elements]"
            }
            is String -> "\"$data\""
            is Number -> data.toString()
            is Boolean -> data.toString()
            null -> "null"
            else -> "\"$data\""
        }
    }

    // Data classes for results
    data class ValidationResult(
        val isCorrect: Boolean,
        val accuracy: Int,
        val matches: Int,
        val total: Int,
        val feedback: String
    )

    data class PuzzleStats(
        val difficulty: String,
        val gridSize: String,
        val totalCells: Int,
        val targetCells: Int,
        val emptyCells: Int,
        val density: String,
        val timeLimit: String,
        val estimatedDifficulty: String
    )
}