package com.kreativekoala.riddleverse

import kotlin.math.abs
import kotlin.random.Random

/**
 * Local generator for Pinball Deflector puzzles
 * Generates matrix with deflectors that change ball direction by 90 degrees
 * User must predict where ball will end up after deflections
 */
object PinballDeflectorGenerator {

    // Deflector orientation constants
    object DeflectorTypes {
        const val EMPTY = 0
        const val SLASH = 1      // / deflector
        const val BACKSLASH = 2  // \ deflector
    }

    // Direction data class
    data class Direction(
        val dr: Int,
        val dc: Int,
        val name: String
    )

    // Predefined directions
    val DIRECTIONS = mapOf(
        "UP" to Direction(-1, 0, "UP"),
        "DOWN" to Direction(1, 0, "DOWN"),
        "LEFT" to Direction(0, -1, "LEFT"),
        "RIGHT" to Direction(0, 1, "RIGHT")
    )

    data class DifficultyConfig(
        val matrixSize: Int,
        val deflectorCount: Int,
        val minActiveDeflectors: Int,
        val memoryTime: Int,
        val maxBounces: Int
    )

    data class BallPathStep(
        val position: List<Int>,
        val direction: Direction,
        val deflected: Boolean,
        val deflectorType: Int?
    )

    data class ActiveDeflector(
        val position: List<Int>,
        val type: Int,
        val oldDirection: Direction,
        val newDirection: Direction
    )

    private val difficultyConfig = mapOf(
        "easy" to DifficultyConfig(
            matrixSize = 5,
            deflectorCount = 3,
            minActiveDeflectors = 2,
            memoryTime = 4000,
            maxBounces = 8
        ),
        "medium" to DifficultyConfig(
            matrixSize = 6,
            deflectorCount = 4,
            minActiveDeflectors = 3,
            memoryTime = 5000,
            maxBounces = 10
        ),
        "hard" to DifficultyConfig(
            matrixSize = 7,
            deflectorCount = 5,
            minActiveDeflectors = 3,
            memoryTime = 6000,
            maxBounces = 12
        )
    )

    /**
     * Generate a complete Pinball Deflector puzzle
     */
    fun generatePuzzle(difficulty: String = "easy"): Pair<String, String> {
        val config = difficultyConfig[difficulty.lowercase()]
            ?: difficultyConfig["easy"]!!

        println("🎯 Generating $difficulty pinball deflector puzzle")

        // Generate puzzle with valid ball path
        val puzzleResult = generatePuzzleWithPath(config)

        if (!puzzleResult.success) {
            throw Exception(puzzleResult.error ?: "Failed to generate valid puzzle")
        }

        val matrix = puzzleResult.matrix!!
        val startPosition = puzzleResult.startPosition!!
        val startDirection = puzzleResult.startDirection!!
        val ballPath = puzzleResult.ballPath!!
        val endPosition = puzzleResult.endPosition!!
        val activeDeflectors = puzzleResult.activeDeflectors!!

        // Create instructions
        val instructions = mapOf(
            "title" to "Pinball Deflector Puzzle",
            "description" to "Predict where the ball will end up after hitting deflectors!",
            "steps" to listOf(
                "1. Memorize the deflector positions and orientations",
                "2. Deflectors will disappear after the timer",
                "3. A ball will appear at the start position",
                "4. Predict where the ball will end up after all deflections",
                "5. Tap the predicted end position"
            ),
            "tip" to "You have ${config.memoryTime / 1000} seconds to memorize the ${config.deflectorCount} deflectors"
        )

        // Build question JSON
        val questionData = mapOf(
            "matrix" to matrix,
            "matrixSize" to config.matrixSize,
            "startPosition" to startPosition,
            "startDirection" to mapOf(
                "dr" to startDirection.dr,
                "dc" to startDirection.dc,
                "name" to startDirection.name
            ),
            "memoryTime" to config.memoryTime,
            "instructions" to instructions,
            "difficulty" to difficulty
        )

        // Build answer JSON
        val answerData = mapOf(
            "endPosition" to endPosition,
            "ballPath" to ballPath.map { step ->
                mapOf(
                    "position" to step.position,
                    "direction" to mapOf(
                        "dr" to step.direction.dr,
                        "dc" to step.direction.dc,
                        "name" to step.direction.name
                    ),
                    "deflected" to step.deflected,
                    "deflectorType" to step.deflectorType
                )
            },
            "activeDeflectors" to activeDeflectors.map { deflector ->
                mapOf(
                    "position" to deflector.position,
                    "type" to deflector.type,
                    "oldDirection" to mapOf(
                        "dr" to deflector.oldDirection.dr,
                        "dc" to deflector.oldDirection.dc,
                        "name" to deflector.oldDirection.name
                    ),
                    "newDirection" to mapOf(
                        "dr" to deflector.newDirection.dr,
                        "dc" to deflector.newDirection.dc,
                        "name" to deflector.newDirection.name
                    )
                )
            },
            "scoring" to mapOf(
                "correctEndBonus" to 70,
                "pathAccuracyBonus" to 30,
                "maxPoints" to 100
            )
        )

        val questionJson = buildJsonString(questionData)
        val answerJson = buildJsonString(answerData)

        println("✅ Generated ${config.matrixSize}x${config.matrixSize} matrix with ${countDeflectors(matrix)} deflectors")
        println("🎯 Ball path: ${ballPath.size} steps, ${ballPath.count { it.deflected }} bounces")
        println("📍 Start: [${startPosition[0]},${startPosition[1]}] ${startDirection.name}, End: [${endPosition[0]},${endPosition[1]}]")

        return Pair(questionJson, answerJson)
    }

    /**
     * Generate puzzle ensuring ball hits minimum deflectors
     */
    private fun generatePuzzleWithPath(config: DifficultyConfig): PuzzleResult {
        var attempts = 0
        val maxAttempts = 50

        while (attempts < maxAttempts) {
            attempts++

            // Create matrix with deflectors
            val matrix = createEmptyMatrix(config.matrixSize)
            val startResult = selectRandomStartPosition(config.matrixSize)

            if (!startResult.success) {
                continue
            }

            val startPosition = startResult.position!!
            val startDirection = startResult.direction!!

            // Place deflectors strategically
            val deflectorResult = placeStrategicDeflectors(matrix, startPosition, startDirection, config)

            if (!deflectorResult.success) {
                continue
            }

            // Simulate ball path
            val pathResult = simulateBallPath(matrix, startPosition, startDirection, config.maxBounces)

            if (!pathResult.success) {
                continue
            }

            val ballPath = pathResult.path!!
            val endPosition = pathResult.endPosition!!
            val activeDeflectors = pathResult.activeDeflectors!!

            // Check if minimum deflectors were hit
            if (activeDeflectors.size >= config.minActiveDeflectors) {
                println("✅ Valid puzzle generated on attempt $attempts")
                return PuzzleResult(
                    success = true,
                    matrix = matrix,
                    startPosition = startPosition,
                    startDirection = startDirection,
                    ballPath = ballPath,
                    endPosition = endPosition,
                    activeDeflectors = activeDeflectors
                )
            }
        }

        return PuzzleResult(
            success = false,
            error = "Could not generate valid puzzle after $maxAttempts attempts"
        )
    }

    /**
     * Create empty matrix
     */
    private fun createEmptyMatrix(size: Int): List<MutableList<Int>> {
        return List(size) { MutableList(size) { DeflectorTypes.EMPTY } }
    }

    /**
     * Select random start position on matrix edge
     */
    private fun selectRandomStartPosition(matrixSize: Int): StartPositionResult {
        val edges = mutableListOf<Pair<List<Int>, Direction>>()

        // Top edge (going down)
        for (i in 0 until matrixSize) {
            edges.add(Pair(listOf(0, i), DIRECTIONS["DOWN"]!!))
        }

        // Bottom edge (going up)
        for (i in 0 until matrixSize) {
            edges.add(Pair(listOf(matrixSize - 1, i), DIRECTIONS["UP"]!!))
        }

        // Left edge (going right)
        for (i in 0 until matrixSize) {
            edges.add(Pair(listOf(i, 0), DIRECTIONS["RIGHT"]!!))
        }

        // Right edge (going left)
        for (i in 0 until matrixSize) {
            edges.add(Pair(listOf(i, matrixSize - 1), DIRECTIONS["LEFT"]!!))
        }

        val randomEdge = edges.random()
        return StartPositionResult(
            success = true,
            position = randomEdge.first,
            direction = randomEdge.second
        )
    }

    /**
     * Place deflectors strategically in ball's path
     */
    private fun placeStrategicDeflectors(
        matrix: List<MutableList<Int>>,
        startPosition: List<Int>,
        startDirection: Direction,
        config: DifficultyConfig
    ): PlacementResult {
        val matrixSize = config.matrixSize
        val deflectorCount = config.deflectorCount
        val minActiveDeflectors = config.minActiveDeflectors

        var currentPos = startPosition.toMutableList()
        var currentDir = startDirection.copy()
        var deflatorsPlaced = 0

        // Simulate initial path to place first deflector
        while (deflatorsPlaced < minOf(deflectorCount, minActiveDeflectors)) {
            // Move in current direction to find placement spot
            val nextPos = listOf(
                currentPos[0] + currentDir.dr,
                currentPos[1] + currentDir.dc
            )

            // Check bounds
            if (nextPos[0] < 0 || nextPos[0] >= matrixSize ||
                nextPos[1] < 0 || nextPos[1] >= matrixSize) {
                break
            }

            // Skip if too close to start (first 1-2 cells)
            val distanceFromStart = abs(nextPos[0] - startPosition[0]) + abs(nextPos[1] - startPosition[1])
            if (distanceFromStart <= 1) {
                currentPos = nextPos.toMutableList()
                continue
            }

            // Place deflector with some probability
            if (Random.nextDouble() < 0.4 && matrix[nextPos[0]][nextPos[1]] == DeflectorTypes.EMPTY) {
                // Randomly choose deflector type
                val deflectorType = if (Random.nextBoolean()) DeflectorTypes.SLASH else DeflectorTypes.BACKSLASH
                matrix[nextPos[0]][nextPos[1]] = deflectorType
                deflatorsPlaced++

                // Update direction based on deflector
                currentDir = getNewDirection(currentDir, deflectorType)
                println("📍 Placed deflector $deflectorType at [${nextPos[0]},${nextPos[1]}], new direction: ${currentDir.name}")
            }

            currentPos = nextPos.toMutableList()
        }

        // Place remaining deflectors randomly
        while (deflatorsPlaced < deflectorCount) {
            val row = Random.nextInt(matrixSize)
            val col = Random.nextInt(matrixSize)

            if (matrix[row][col] == DeflectorTypes.EMPTY) {
                val deflectorType = if (Random.nextBoolean()) DeflectorTypes.SLASH else DeflectorTypes.BACKSLASH
                matrix[row][col] = deflectorType
                deflatorsPlaced++
            }
        }

        return PlacementResult(success = true)
    }

    /**
     * Simulate ball path through matrix
     */
    private fun simulateBallPath(
        matrix: List<List<Int>>,
        startPosition: List<Int>,
        startDirection: Direction,
        maxBounces: Int
    ): PathResult {
        val matrixSize = matrix.size
        val path = mutableListOf<BallPathStep>()
        val activeDeflectors = mutableListOf<ActiveDeflector>()

        var currentPos = startPosition.toMutableList()
        var currentDir = startDirection.copy()
        var bounces = 0

        // Add start position
        path.add(BallPathStep(
            position = currentPos.toList(),
            direction = currentDir.copy(),
            deflected = false,
            deflectorType = null
        ))

        while (bounces < maxBounces) {
            // Calculate next position
            val nextPos = listOf(
                currentPos[0] + currentDir.dr,
                currentPos[1] + currentDir.dc
            )

            // Check if out of bounds
            if (nextPos[0] < 0 || nextPos[0] >= matrixSize ||
                nextPos[1] < 0 || nextPos[1] >= matrixSize) {
                break
            }

            currentPos = nextPos.toMutableList()
            val cellValue = matrix[currentPos[0]][currentPos[1]]

            // Check for deflector
            if (cellValue == DeflectorTypes.SLASH || cellValue == DeflectorTypes.BACKSLASH) {
                // Ball hits deflector
                val newDirection = getNewDirection(currentDir, cellValue)

                path.add(BallPathStep(
                    position = currentPos.toList(),
                    direction = currentDir.copy(),
                    deflected = true,
                    deflectorType = cellValue
                ))

                activeDeflectors.add(ActiveDeflector(
                    position = currentPos.toList(),
                    type = cellValue,
                    oldDirection = currentDir.copy(),
                    newDirection = newDirection.copy()
                ))

                currentDir = newDirection
                bounces++
            } else {
                // Empty cell
                path.add(BallPathStep(
                    position = currentPos.toList(),
                    direction = currentDir.copy(),
                    deflected = false,
                    deflectorType = null
                ))
            }
        }

        val endPosition = path.last().position

        return PathResult(
            success = true,
            path = path,
            endPosition = endPosition,
            activeDeflectors = activeDeflectors
        )
    }

    /**
     * Calculate new direction after hitting deflector
     */
    private fun getNewDirection(currentDirection: Direction, deflectorType: Int): Direction {
        val dr = currentDirection.dr
        val dc = currentDirection.dc

        return when (deflectorType) {
            DeflectorTypes.SLASH -> {
                // Slash deflector: (dr, dc) -> (-dc, -dr)
                getDirectionFromComponents(-dc, -dr)
            }
            DeflectorTypes.BACKSLASH -> {
                // Backslash deflector: (dr, dc) -> (dc, dr)
                getDirectionFromComponents(dc, dr)
            }
            else -> currentDirection // No change
        }
    }

    /**
     * Get direction object from components
     */
    private fun getDirectionFromComponents(dr: Int, dc: Int): Direction {
        return when {
            dr == -1 && dc == 0 -> DIRECTIONS["UP"]!!
            dr == 1 && dc == 0 -> DIRECTIONS["DOWN"]!!
            dr == 0 && dc == -1 -> DIRECTIONS["LEFT"]!!
            dr == 0 && dc == 1 -> DIRECTIONS["RIGHT"]!!
            else -> Direction(dr, dc, "CUSTOM_${dr}_${dc}")
        }
    }

    /**
     * Count deflectors in matrix
     */
    private fun countDeflectors(matrix: List<List<Int>>): Int {
        return matrix.flatten().count { cell ->
            cell == DeflectorTypes.SLASH || cell == DeflectorTypes.BACKSLASH
        }
    }

    /**
     * Build JSON string from nested data structures
     */
    private fun buildJsonString(data: Any?): String {
        return when (data) {
            is Map<*, *> -> {
                val entries = data.entries.joinToString(",") { (key, value) ->
                    "\"$key\":${buildJsonString(value)}"
                }
                "{$entries}"
            }
            is List<*> -> {
                val elements = data.joinToString(",") { buildJsonString(it) }
                "[$elements]"
            }
            is String -> "\"${data.replace("\"", "\\\"")}\""
            is Number -> data.toString()
            is Boolean -> data.toString()
            null -> "null"
            else -> "\"$data\""
        }
    }

    // Helper data classes
    data class PuzzleResult(
        val success: Boolean,
        val matrix: List<MutableList<Int>>? = null,
        val startPosition: List<Int>? = null,
        val startDirection: Direction? = null,
        val ballPath: List<BallPathStep>? = null,
        val endPosition: List<Int>? = null,
        val activeDeflectors: List<ActiveDeflector>? = null,
        val error: String? = null
    )

    data class StartPositionResult(
        val success: Boolean,
        val position: List<Int>? = null,
        val direction: Direction? = null
    )

    data class PlacementResult(
        val success: Boolean
    )

    data class PathResult(
        val success: Boolean,
        val path: List<BallPathStep>? = null,
        val endPosition: List<Int>? = null,
        val activeDeflectors: List<ActiveDeflector>? = null
    )
}