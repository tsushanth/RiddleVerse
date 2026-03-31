package com.kreativekoala.riddleverse

import kotlin.random.Random

/**
 * Local generator for Memory Previous Pair puzzles
 * Generates number-based sequences that can be mapped to forest animals
 */
object MemoryPreviousPairGenerator {

    data class DifficultyConfig(
        val sequenceLength: Int,
        val objectsPerScreen: Int,
        val maxObjectPool: Int
    )

    data class ScreenData(
        val screenNumber: Int,
        val numbers: List<Int>,
        val linkingNumber: Int?,
        val isFirstScreen: Boolean
    )

    data class LinkingNumberData(
        val screenNumber: Int,
        val linkingNumber: Int
    )

    private val difficultyConfigs = mapOf(
        "easy" to DifficultyConfig(
            sequenceLength = 10,
            objectsPerScreen = 2,
            maxObjectPool = 6  // Use numbers 1-6 (maps to forest animals)
        ),
        "medium" to DifficultyConfig(
            sequenceLength = 16,
            objectsPerScreen = 3,
            maxObjectPool = 8  // Use numbers 1-8
        ),
        "hard" to DifficultyConfig(
            sequenceLength = 20,
            objectsPerScreen = 4,
            maxObjectPool = 10 // Use numbers 1-10
        )
    )

    /**
     * Generate a complete Memory Previous Pair puzzle
     */
    fun generatePuzzle(difficulty: String = "easy"): Pair<String, String> {
        val config = difficultyConfigs[difficulty.lowercase()]
            ?: difficultyConfigs["easy"]!!

        // Generate sequence with linking numbers
        val sequence = generateLinkedNumberSequence(config)

        // Validate sequence
        if (!validateSequence(sequence)) {
            throw Exception("Generated sequence failed validation")
        }

        // Create instructions
        val instructions = mapOf(
            "title" to "Memory Previous Pair",
            "description" to "Remember objects from the previous screen!",
            "steps" to listOf(
                "1. Look at the objects on the first screen",
                "2. On each new screen, tap the object that appeared in the previous screen",
                "3. Continue until you complete the sequence",
                "4. Be quick for bonus points!"
            ),
            "tip" to "Focus on unique features to help remember each object"
        )

        // Build question JSON
        val questionData = mapOf(
            "sequence" to sequence.map { screen ->
                mapOf(
                    "screenNumber" to screen.screenNumber,
                    "numbers" to screen.numbers,
                    "linkingNumber" to screen.linkingNumber,
                    "isFirstScreen" to screen.isFirstScreen
                )
            },
            "instructions" to instructions,
            "totalScreens" to sequence.size,
            "objectsPerScreen" to config.objectsPerScreen,
            "maxObjectPool" to config.maxObjectPool,
            "difficulty" to difficulty
        )

        // Build answer JSON
        val linkingNumbers = sequence
            .filter { it.linkingNumber != null }
            .map { screen ->
                mapOf(
                    "screenNumber" to screen.screenNumber,
                    "linkingNumber" to screen.linkingNumber!!
                )
            }

        val answerData = mapOf(
            "linkingNumbers" to linkingNumbers,
            "totalCorrectAnswers" to linkingNumbers.size,
            "maxScore" to linkingNumbers.size * 10
        )

        val questionJson = buildJsonString(questionData)
        val answerJson = buildJsonString(answerData)

        return Pair(questionJson, answerJson)
    }

    /**
     * Generate linked sequence using numbers that map to forest animals
     */
    private fun generateLinkedNumberSequence(config: DifficultyConfig): List<ScreenData> {
        val availableNumbers = (1..config.maxObjectPool).toList()
        val sequence = mutableListOf<ScreenData>()

        for (i in 0 until config.sequenceLength) {
            val screenNumbers = when (i) {
                0 -> {
                    // First screen: random selection of numbers
                    selectRandomNumbers(availableNumbers, config.objectsPerScreen)
                }
                else -> {
                    // Subsequent screens: must include one number from previous screen
                    val previousScreen = sequence[i - 1]

                    // Pick one random number from previous screen (this is the linking number)
                    val linkingNumber = previousScreen.numbers.random()
                    val screenNumbers = mutableListOf(linkingNumber)

                    // Fill remaining slots with new numbers (not in previous screen)
                    val excludeNumbers = previousScreen.numbers
                    val availableForNew = availableNumbers.filter { it !in excludeNumbers }

                    val newNumbers = selectRandomNumbers(
                        availableForNew,
                        config.objectsPerScreen - 1
                    )

                    screenNumbers.addAll(newNumbers)

                    // Shuffle to randomize positions
                    screenNumbers.shuffled()
                }
            }

            // Create screen data
            val screenData = ScreenData(
                screenNumber = i + 1,
                numbers = screenNumbers.sorted(), // Sort for consistency
                linkingNumber = if (i > 0) findLinkingNumber(sequence[i - 1].numbers, screenNumbers) else null,
                isFirstScreen = i == 0
            )

            sequence.add(screenData)
        }

        return sequence
    }

    /**
     * Find which number links current screen to previous screen
     */
    private fun findLinkingNumber(previousNumbers: List<Int>, currentNumbers: List<Int>): Int? {
        return currentNumbers.find { it in previousNumbers }
    }

    /**
     * Select random numbers from available pool
     */
    private fun selectRandomNumbers(availableNumbers: List<Int>, count: Int): List<Int> {
        if (availableNumbers.size < count) {
            throw Exception("Not enough numbers available. Need $count, have ${availableNumbers.size}")
        }

        return availableNumbers.shuffled().take(count)
    }

    /**
     * Validate generated sequence
     */
    private fun validateSequence(sequence: List<ScreenData>): Boolean {
        println("🔍 Validating number sequence with ${sequence.size} screens...")

        for (i in 1 until sequence.size) {
            val currentScreen = sequence[i]
            val previousScreen = sequence[i - 1]

            // Check if current screen has a linking number
            if (currentScreen.linkingNumber == null) {
                println("❌ Screen ${i + 1} missing linking number")
                return false
            }

            // Verify linking number exists in both screens
            val linkingInCurrent = currentScreen.linkingNumber in currentScreen.numbers
            val linkingInPrevious = currentScreen.linkingNumber in previousScreen.numbers

            if (!linkingInCurrent || !linkingInPrevious) {
                println("❌ Linking number ${currentScreen.linkingNumber} not found in both screens ${i} and ${i + 1}")
                return false
            }
        }

        println("✅ Number sequence validation passed")
        return true
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
}