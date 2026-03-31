package com.kreativekoala.riddleverse

import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.*
import kotlin.random.Random

data class UniqueObjectGeneratorResult(
    val success: Boolean,
    val puzzleData: GeneratedUniqueObjectData? = null,
    val message: String? = null
)

data class GeneratedUniqueObjectData(
    val question: String,
    val answer: String,
    val hint: String,
    val difficulty: String,
    val metadata: Map<String, Any>
)

data class UniqueObjectDifficultyConfig(
    val totalObjects: Int,
    val minDuplicates: Int,
    val maxDuplicates: Int,
    val shapeRange: Int,
    val colorRange: Int,
    val distractorGroups: Int,
    val guaranteedStrategy: String?,
    val visualComplexity: String,
    val allowSimilarColors: Boolean,
    val allowSimilarShapes: Boolean,
    val requireCrossCategoryDistractors: Boolean,
    val enableRedHerrings: Boolean,
    val description: String
)

data class GeneratedDuplicateGroup(
    val combination: String,
    val indices: List<Int>,
    val count: Int,
    val groupType: String
)

data class GeneratedPuzzleLayout(
    val objects: List<UniqueObject>,
    val uniqueObjectIndex: Int,
    val duplicateGroups: List<GeneratedDuplicateGroup>,
    val usedCombinations: List<String>
)

class UniqueObjectGenerator {
    private val TAG = "UniqueObjectGen"
    private var debugMode = false

    // Define universe of shapes and colors
    private val shapes = mapOf(
        0 to "circle",
        1 to "square",
        2 to "triangle",
        3 to "diamond",
        4 to "hexagon",
        5 to "star"
    )

    private val colors = mapOf(
        0 to "red",
        1 to "blue",
        2 to "yellow",
        3 to "green",
        4 to "purple",
        5 to "orange",
        6 to "pink",     // Similar to red - adds confusion in hard mode
        7 to "cyan"      // Similar to blue - adds confusion in hard mode
    )

    // Define similar color pairs for confusion in hard mode
    private val similarColorPairs = listOf(
        listOf(0, 6), // red, pink
        listOf(1, 7), // blue, cyan
        listOf(2, 5)  // yellow, orange
    )

    // Define similar shape pairs for confusion in hard mode
    private val similarShapePairs = listOf(
        listOf(0, 4), // circle, hexagon (both rounded)
        listOf(1, 3), // square, diamond (both angular)
        listOf(2, 5)  // triangle, star (both pointed)
    )

    // Enhanced difficulty settings with multiple cognitive challenges
    private val difficultySettings = mapOf(
        "easy" to UniqueObjectDifficultyConfig(
            totalObjects = 6,
            minDuplicates = 2,
            maxDuplicates = 2,
            shapeRange = 3, // Use first 3 shapes: circle, square, triangle
            colorRange = 3, // Use first 3 colors: red, blue, yellow
            distractorGroups = 1, // Only 1 other group besides unique
            guaranteedStrategy = "shared_shape_different_color", // Force easier strategy
            visualComplexity = "low",
            allowSimilarColors = false,
            allowSimilarShapes = false,
            requireCrossCategoryDistractors = false,
            enableRedHerrings = false,
            description = "Few objects, distinct shapes/colors, clear pattern"
        ),
        "medium" to UniqueObjectDifficultyConfig(
            totalObjects = 9,
            minDuplicates = 2,
            maxDuplicates = 3,
            shapeRange = 4, // Add diamond
            colorRange = 4, // Add green
            distractorGroups = 2, // 2 other groups besides unique
            guaranteedStrategy = null, // Allow both strategies
            visualComplexity = "medium",
            allowSimilarColors = false, // No red/pink confusion yet
            allowSimilarShapes = false,
            requireCrossCategoryDistractors = false,
            enableRedHerrings = false,
            description = "More objects, additional shapes/colors, multiple groups"
        ),
        "hard" to UniqueObjectDifficultyConfig(
            totalObjects = 12,
            minDuplicates = 2,
            maxDuplicates = 4,
            shapeRange = 5, // Add hexagon
            colorRange = 6, // Add purple, orange
            distractorGroups = 3, // 3+ other groups besides unique
            guaranteedStrategy = null,
            visualComplexity = "high",
            allowSimilarColors = true, // Enable red/pink, blue/cyan confusion
            allowSimilarShapes = true, // Enable circle/hexagon, square/diamond confusion
            requireCrossCategoryDistractors = true, // Force both shape AND color distractors
            enableRedHerrings = false,
            description = "Many objects, similar shapes/colors, complex groupings"
        ),
        "expert" to UniqueObjectDifficultyConfig(
            totalObjects = 15,
            minDuplicates = 3,
            maxDuplicates = 5,
            shapeRange = 6, // All shapes
            colorRange = 8, // All colors
            distractorGroups = 4,
            guaranteedStrategy = null,
            visualComplexity = "extreme",
            allowSimilarColors = true,
            allowSimilarShapes = true,
            requireCrossCategoryDistractors = true,
            enableRedHerrings = true, // Add objects that ALMOST break the pattern
            description = "Maximum complexity with visual and cognitive challenges"
        )
    )

    fun setDebugMode(enabled: Boolean) {
        debugMode = enabled
    }

    private fun debugLog(message: String, type: String = "info") {
        if (debugMode) {
            val emoji = when (type) {
                "error" -> "❌"
                "success" -> "✅"
                "warning" -> "⚠️"
                else -> "🔄"
            }
            Log.d(TAG, "$emoji UNIQUE_OBJ: $message")
        }
    }

    /**
     * Generate a unique object puzzle
     */
    fun generateUniqueObjectPuzzle(difficulty: String = "medium"): UniqueObjectGeneratorResult {
        return try {
            debugLog("🎲 Generating unique object puzzle ($difficulty)")

            val settings = difficultySettings[difficulty.lowercase()]
                ?: throw IllegalArgumentException("Invalid difficulty: $difficulty")

            // Generate the puzzle layout
            val puzzleData = generatePuzzleLayout(settings)

            // Create question and answer data
            val questionData = createQuestionData(puzzleData, settings)
            val answerData = createAnswerData(puzzleData)

            val puzzle = GeneratedUniqueObjectData(
                question = questionData.toString(),
                answer = answerData.toString(),
                hint = "Find the object that appears only once - it has a unique combination of shape and color",
                difficulty = difficulty,
                metadata = mapOf(
                    "totalObjects" to puzzleData.objects.size,
                    "uniqueObjectIndex" to puzzleData.uniqueObjectIndex,
                    "duplicateGroups" to puzzleData.duplicateGroups.size,
                    "generatedAt" to System.currentTimeMillis(),
                    "shapeColorMappings" to mapOf(
                        "shapes" to getShapeMapping(settings.shapeRange),
                        "colors" to getColorMapping(settings.colorRange)
                    )
                )
            )

            debugLog("✅ Generated unique object puzzle with ${puzzleData.objects.size} objects", "success")

            UniqueObjectGeneratorResult(success = true, puzzleData = puzzle)

        } catch (error: Exception) {
            debugLog("❌ Generation failed: ${error.message}", "error")
            UniqueObjectGeneratorResult(
                success = false,
                message = "Unique object generation failed: ${error.message}"
            )
        }
    }

    /**
     * Generate the core puzzle layout with difficulty-appropriate challenges
     * CORRECTED VERSION: Ensure exactly one unique object
     */
    private fun generatePuzzleLayout(settings: UniqueObjectDifficultyConfig): GeneratedPuzzleLayout {
        val totalObjects = settings.totalObjects
        val minDuplicates = settings.minDuplicates
        val maxDuplicates = settings.maxDuplicates
        val shapeRange = settings.shapeRange
        val colorRange = settings.colorRange

        val objects = mutableListOf<UniqueObject>()
        val usedCombinations = mutableSetOf<String>()
        val duplicateGroups = mutableListOf<GeneratedDuplicateGroup>()

        // STEP 1: Generate the unique object FIRST
        val uniqueObject = createStrategicUniqueObject(
            emptyList(), emptyList(), usedCombinations, shapeRange, colorRange
        )
        usedCombinations.add("${uniqueObject.shape}-${uniqueObject.color}")

        // STEP 2: Fill remaining slots with ONLY duplicate groups
        var remainingSlots = totalObjects - 1 // Reserve 1 slot for unique

        while (remainingSlots >= minDuplicates) {
            // Determine group size
            val maxGroupSize = minOf(maxDuplicates, remainingSlots)
            val groupSize = maxOf(minDuplicates,
                minOf(maxGroupSize, minDuplicates + Random.nextInt(2)))

            // Generate a NEW combination that doesn't conflict with unique
            var groupObject: UniqueObject
            var attempts = 0
            do {
                groupObject = UniqueObject(
                    shape = Random.nextInt(shapeRange),
                    color = Random.nextInt(colorRange)
                )
                attempts++
                if (attempts > 50) {
                    throw RuntimeException("Could not generate non-conflicting duplicate group")
                }
            } while (usedCombinations.contains("${groupObject.shape}-${groupObject.color}"))

            // Mark this combination as used
            usedCombinations.add("${groupObject.shape}-${groupObject.color}")

            // Create the duplicate group
            val groupIndices = mutableListOf<Int>()
            for (i in 0 until groupSize) {
                objects.add(groupObject.copy())
                groupIndices.add(objects.size - 1)
            }

            duplicateGroups.add(GeneratedDuplicateGroup(
                combination = "${groupObject.shape}-${groupObject.color}",
                indices = groupIndices,
                count = groupSize,
                groupType = "duplicate"
            ))

            remainingSlots -= groupSize
        }

        // STEP 3: Add the unique object
        objects.add(uniqueObject)
        val uniqueObjectIndex = objects.size - 1

        // STEP 4: Shuffle while tracking unique object
        val shuffledObjects = objects.toMutableList()
        var newUniqueIndex = uniqueObjectIndex

        // Fisher-Yates shuffle with proper tracking
        for (i in shuffledObjects.size - 1 downTo 1) {
            val j = Random.nextInt(i + 1)

            // Track the unique object's position
            when {
                i == newUniqueIndex -> newUniqueIndex = j
                j == newUniqueIndex -> newUniqueIndex = i
            }

            // Perform swap
            val temp = shuffledObjects[i]
            shuffledObjects[i] = shuffledObjects[j]
            shuffledObjects[j] = temp
        }

        // STEP 5: VALIDATE the result
        val combinations = mutableMapOf<String, MutableList<Int>>()
        shuffledObjects.forEachIndexed { idx, obj ->
            val combo = "${obj.shape}-${obj.color}"
            if (!combinations.containsKey(combo)) {
                combinations[combo] = mutableListOf()
            }
            combinations[combo]!!.add(idx)
        }

        val uniqueCombos = combinations.entries.filter { it.value.size == 1 }

        if (uniqueCombos.size != 1) {
            throw RuntimeException("VALIDATION FAILED: Generated ${uniqueCombos.size} unique objects, expected 1. Combinations: $combinations")
        }

        val (actualCombo, actualIndices) = uniqueCombos[0]
        val actualIndex = actualIndices[0]
        if (actualIndex != newUniqueIndex) {
            debugLog("Index tracking error: expected $newUniqueIndex, found $actualIndex", "error")
            newUniqueIndex = actualIndex // Correct the tracking
        }

        debugLog("✅ Valid puzzle: $actualCombo unique at index $newUniqueIndex", "success")

        return GeneratedPuzzleLayout(
            objects = shuffledObjects,
            uniqueObjectIndex = newUniqueIndex,
            duplicateGroups = duplicateGroups,
            usedCombinations = usedCombinations.toList()
        )
    }

    /**
     * Create a strategically placed unique object with difficulty-appropriate constraints
     */
    private fun createStrategicUniqueObject(
        baseShapes: List<Int>,
        baseColors: List<Int>,
        usedCombinations: MutableSet<String>,
        shapeRange: Int,
        colorRange: Int,
        guaranteedStrategy: String? = null,
        requireCrossCategoryDistractors: Boolean = false
    ): UniqueObject {
        // Handle empty base arrays properly (first call)
        if (baseShapes.isEmpty() || baseColors.isEmpty()) {
            // No existing objects yet, just create a random unique object
            var attempts = 0
            var uniqueObject: UniqueObject

            do {
                uniqueObject = UniqueObject(
                    shape = Random.nextInt(shapeRange),
                    color = Random.nextInt(colorRange)
                )
                attempts++

                if (attempts > 50) {
                    throw RuntimeException("Could not generate unique object after 50 attempts")
                }
            } while (usedCombinations.contains("${uniqueObject.shape}-${uniqueObject.color}"))

            debugLog("🎯 Generated initial unique object: ${uniqueObject.shape}-${uniqueObject.color}")
            return uniqueObject
        }

        val strategies = if (guaranteedStrategy != null) {
            listOf(guaranteedStrategy)
        } else {
            listOf("shared_shape_different_color", "shared_color_different_shape")
        }

        val strategy = strategies.random()

        return if (strategy == "shared_shape_different_color") {
            // Pick a shape that already exists, but use a different color
            val existingShape = baseShapes.random()

            // Find a color not used with this shape
            var uniqueColor: Int
            var attempts = 0
            do {
                uniqueColor = Random.nextInt(colorRange)
                attempts++

                // In hard mode with cross-category distractors, prefer colors used elsewhere
                if (requireCrossCategoryDistractors && attempts < 10) {
                    if (baseColors.contains(uniqueColor)) {
                        break // Use a color that appears in other groups
                    }
                }
            } while (usedCombinations.contains("$existingShape-$uniqueColor") && attempts < 20)

            debugLog("🎯 Generated strategic unique object (shared shape): $existingShape-$uniqueColor")
            UniqueObject(shape = existingShape, color = uniqueColor)

        } else {
            // Pick a color that already exists, but use a different shape
            val existingColor = baseColors.random()

            // Find a shape not used with this color
            var uniqueShape: Int
            var attempts = 0
            do {
                uniqueShape = Random.nextInt(shapeRange)
                attempts++

                // In hard mode with cross-category distractors, prefer shapes used elsewhere
                if (requireCrossCategoryDistractors && attempts < 10) {
                    if (baseShapes.contains(uniqueShape)) {
                        break // Use a shape that appears in other groups
                    }
                }
            } while (usedCombinations.contains("$uniqueShape-$existingColor") && attempts < 20)

            debugLog("🎯 Generated strategic unique object (shared color): $uniqueShape-$existingColor")
            UniqueObject(shape = uniqueShape, color = existingColor)
        }
    }

    /**
     * Create question data for client
     */
    private fun createQuestionData(puzzleData: GeneratedPuzzleLayout, settings: UniqueObjectDifficultyConfig): JSONObject {
        return JSONObject().apply {
            put("objects", JSONArray().apply {
                puzzleData.objects.forEach { obj ->
                    put(JSONObject().apply {
                        put("shape", obj.shape)
                        put("color", obj.color)
                    })
                }
            })
            put("totalObjects", puzzleData.objects.size)
            put("instruction", "Find the odd one out, and tap on it.")
            put("shapeMappings", JSONObject().apply {
                getShapeMapping(settings.shapeRange).forEach { (key, value) ->
                    put(key, value)
                }
            })
            put("colorMappings", JSONObject().apply {
                getColorMapping(settings.colorRange).forEach { (key, value) ->
                    put(key, value)
                }
            })
            put("layout", JSONObject().apply {
                put("grid", JSONObject().apply {
                    val gridLayout = calculateGridLayout(puzzleData.objects.size)
                    put("rows", gridLayout.first)
                    put("cols", gridLayout.second)
                    put("totalCells", gridLayout.first * gridLayout.second)
                })
                put("spacing", "auto")
            })
        }
    }

    /**
     * Create answer data
     */
    private fun createAnswerData(puzzleData: GeneratedPuzzleLayout): JSONObject {
        return JSONObject().apply {
            put("uniqueObjectIndex", puzzleData.uniqueObjectIndex)
            put("uniqueObject", JSONObject().apply {
                val uniqueObj = puzzleData.objects[puzzleData.uniqueObjectIndex]
                put("shape", uniqueObj.shape)
                put("color", uniqueObj.color)
            })
            put("duplicateGroups", JSONArray().apply {
                puzzleData.duplicateGroups.forEach { group ->
                    put(JSONObject().apply {
                        put("combination", group.combination)
                        put("indices", JSONArray(group.indices))
                        put("count", group.count)
                        put("groupType", group.groupType)
                    })
                }
            })
            put("explanation", "Object at position ${puzzleData.uniqueObjectIndex + 1} is unique")
            put("scoring", JSONObject().apply {
                put("correctAnswerPoints", 100)
                put("timeBonus", true)
                put("maxTimeBonus", 50)
            })
        }
    }

    /**
     * Get shape mapping for given range
     */
    private fun getShapeMapping(range: Int): Map<String, String> {
        val mapping = mutableMapOf<String, String>()
        for (i in 0 until range) {
            shapes[i]?.let { shapeName ->
                mapping[i.toString()] = shapeName
            }
        }
        return mapping
    }

    /**
     * Get color mapping for given range
     */
    private fun getColorMapping(range: Int): Map<String, String> {
        val mapping = mutableMapOf<String, String>()
        for (i in 0 until range) {
            colors[i]?.let { colorName ->
                mapping[i.toString()] = colorName
            }
        }
        return mapping
    }

    /**
     * Calculate optimal grid layout for number of objects
     */
    private fun calculateGridLayout(objectCount: Int): Pair<Int, Int> {
        // Try to make a roughly square grid
        val sqrt = sqrt(objectCount.toFloat())
        val rows = ceil(sqrt).toInt()
        val cols = ceil(objectCount.toFloat() / rows).toInt()

        return Pair(rows, cols)
    }

    /**
     * Validate generated puzzle for quality
     */
    private fun validatePuzzle(puzzleData: GeneratedPuzzleLayout): Boolean {
        val objects = puzzleData.objects
        val uniqueObjectIndex = puzzleData.uniqueObjectIndex
        val duplicateGroups = puzzleData.duplicateGroups

        // Check unique object exists and is actually unique
        val uniqueObj = objects[uniqueObjectIndex]
        val uniqueCombo = "${uniqueObj.shape}-${uniqueObj.color}"

        var uniqueCount = 0
        for (obj in objects) {
            if ("${obj.shape}-${obj.color}" == uniqueCombo) {
                uniqueCount++
            }
        }

        if (uniqueCount != 1) {
            throw RuntimeException("Unique object appears $uniqueCount times, should be 1")
        }

        // Validate all duplicate groups have at least 2 members
        for (group in duplicateGroups) {
            if (group.count < 2) {
                throw RuntimeException("Duplicate group has ${group.count} members, minimum is 2")
            }
        }

        // Validate total object count
        val expectedTotal = 1 + duplicateGroups.sumOf { it.count }
        if (objects.size != expectedTotal) {
            throw RuntimeException("Object count mismatch: ${objects.size} vs expected $expectedTotal")
        }

        // Additional quality check: ensure unique object shares exactly one attribute
        val uniqueShape = uniqueObj.shape
        val uniqueColor = uniqueObj.color

        var sharesShapeCount = 0
        var sharesColorCount = 0

        for (obj in objects) {
            if (obj == uniqueObj) continue // Skip the unique object itself

            if (obj.shape == uniqueShape) sharesShapeCount++
            if (obj.color == uniqueColor) sharesColorCount++
        }

        val sharesShape = sharesShapeCount > 0
        val sharesColor = sharesColorCount > 0

        if (!sharesShape && !sharesColor) {
            throw RuntimeException("Quality issue: Unique object shares no attributes with other objects")
        }

        if (sharesShape && sharesColor) {
            throw RuntimeException("Quality issue: Unique object shares both shape and color with other objects")
        }

        debugLog("✅ Quality validation passed", "success")
        return true
    }
}