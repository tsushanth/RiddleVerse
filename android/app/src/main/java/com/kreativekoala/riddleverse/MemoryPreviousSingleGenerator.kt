package com.kreativekoala.riddleverse

import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

/**
 * Local generator for Memory Previous Single puzzles
 * Generates binary sequences where user must remember if current state matches previous state
 */
object MemoryPreviousSingleGenerator {

    data class DifficultyConfig(
        val sequenceLength: Int,
        val minContinuousSequences: Int,
        val minSequenceLength: Int,
        val sameAsPreviousRatio: Double
    )

    private val difficultyConfigs = mapOf(
        "easy" to DifficultyConfig(
            sequenceLength = 20,
            minContinuousSequences = 8,
            minSequenceLength = 2,
            sameAsPreviousRatio = 0.6
        ),
        "medium" to DifficultyConfig(
            sequenceLength = 18,
            minContinuousSequences = 6,
            minSequenceLength = 2,
            sameAsPreviousRatio = 0.55
        ),
        "hard" to DifficultyConfig(
            sequenceLength = 16,
            minContinuousSequences = 4,
            minSequenceLength = 2,
            sameAsPreviousRatio = 0.5
        )
    )

    /**
     * Generate a complete Memory Previous Single puzzle
     */
    fun generatePuzzle(difficulty: String = "easy"): Pair<String, String> {
        val config = difficultyConfigs[difficulty.lowercase()]
            ?: difficultyConfigs["easy"]!!

        // Generate binary sequence with required continuous sequences
        val binarySequence = generateBinarySequence(config)

        // Create the question sequence (what user sees)
        val questionSequence = createQuestionSequence(binarySequence)

        // Create the answer sequence (0 = different, 1 = same as previous)
        val answerSequence = createAnswerSequence(binarySequence)

        // Create instructions
        val instructions = mapOf(
            "title" to "Memory Previous Single",
            "description" to "Remember the previous state and compare it with the current one!",
            "steps" to listOf(
                "1. Look at the first object and remember it",
                "2. For each new object, decide if it's the SAME or DIFFERENT from the previous one",
                "3. Tap 'SAME' if it matches the previous object, 'DIFFERENT' if it doesn't",
                "4. Continue through the entire sequence",
                "5. Be quick and accurate for maximum points!"
            ),
            "tip" to "Focus on the immediate previous object, not the entire sequence"
        )

        // Build question JSON
        val questionData = mapOf(
            "binarySequence" to binarySequence,
            "questionSequence" to questionSequence,
            "instructions" to instructions,
            "totalSteps" to binarySequence.size,
            "totalQuestions" to answerSequence.size,
            "difficulty" to difficulty
        )

        // Build answer JSON
        val answerData = mapOf(
            "answerSequence" to answerSequence,
            "correctAnswers" to answerSequence,
            "totalQuestions" to answerSequence.size,
            "maxScore" to answerSequence.size * 5
        )

        val questionJson = buildJsonString(questionData)
        val answerJson = buildJsonString(answerData)

        return Pair(questionJson, answerJson)
    }

    /**
     * Generate binary sequence with required continuous sequences
     */
    private fun generateBinarySequence(config: DifficultyConfig): List<Int> {
        var attempts = 0
        val maxAttempts = 100

        while (attempts < maxAttempts) {
            val sequence = createRandomBinarySequence(config.sequenceLength, config.sameAsPreviousRatio)
            val continuousCount = countContinuousSequences(sequence)

            if (continuousCount >= config.minContinuousSequences) {
                return sequence
            }
            attempts++
        }

        // Fallback: force creation of sequence with required continuous sequences
        return createForcedContinuousSequence(config)
    }

    /**
     * Create a random binary sequence biased toward the desired ratio
     */
    private fun createRandomBinarySequence(length: Int, sameAsPreviousRatio: Double): List<Int> {
        val sequence = mutableListOf<Int>()
        sequence.add(if (Random.nextDouble() < 0.5) 0 else 1) // Random start

        for (i in 1 until length) {
            val shouldBeSame = Random.nextDouble() < sameAsPreviousRatio

            if (shouldBeSame) {
                sequence.add(sequence[i - 1]) // Same as previous
            } else {
                sequence.add(if (sequence[i - 1] == 0) 1 else 0) // Different from previous
            }
        }

        return sequence
    }

    /**
     * Create sequence with forced continuous sequences (fallback method)
     */
    private fun createForcedContinuousSequence(config: DifficultyConfig): List<Int> {
        val sequence = mutableListOf<Int>()
        var remainingLength = config.sequenceLength
        var sequencesCreated = 0

        // Start with random value
        var currentValue = if (Random.nextDouble() < 0.5) 0 else 1

        while (remainingLength > 0 && sequencesCreated < config.minContinuousSequences) {
            // Determine length of this continuous sequence
            val maxLength = min(remainingLength, remainingLength / (config.minContinuousSequences - sequencesCreated))
            val seqLength = max(config.minSequenceLength, Random.nextInt(maxLength) + 1)

            // Add continuous sequence
            repeat(seqLength) {
                sequence.add(currentValue)
            }

            remainingLength -= seqLength
            sequencesCreated++

            // Switch to opposite value for next sequence
            currentValue = if (currentValue == 0) 1 else 0
        }

        // Fill remaining with random values
        while (remainingLength > 0) {
            val shouldBeSame = Random.nextDouble() < 0.5
            if (shouldBeSame && sequence.isNotEmpty()) {
                sequence.add(sequence.last())
            } else {
                sequence.add(if (Random.nextDouble() < 0.5) 0 else 1)
            }
            remainingLength--
        }

        return sequence
    }

    /**
     * Count continuous sequences in binary array
     */
    private fun countContinuousSequences(sequence: List<Int>): Int {
        if (sequence.size < 2) return 0

        var count = 0
        var currentSequenceLength = 1

        for (i in 1 until sequence.size) {
            if (sequence[i] == sequence[i - 1]) {
                currentSequenceLength++
            } else {
                if (currentSequenceLength >= 2) {
                    count++
                }
                currentSequenceLength = 1
            }
        }

        // Check final sequence
        if (currentSequenceLength >= 2) {
            count++
        }

        return count
    }

    /**
     * Create question sequence (what user sees step by step)
     */
    private fun createQuestionSequence(binarySequence: List<Int>): List<Map<String, Any>> {
        return binarySequence.mapIndexed { index, value ->
            mapOf(
                "step" to (index + 1),
                "value" to value,
                "isFirstStep" to (index == 0)
            )
        }
    }

    /**
     * Create answer sequence (0 = different, 1 = same as previous)
     */
    private fun createAnswerSequence(binarySequence: List<Int>): List<Int> {
        val answers = mutableListOf<Int>()

        for (i in 1 until binarySequence.size) {
            val currentValue = binarySequence[i]
            val previousValue = binarySequence[i - 1]

            // 1 = same as previous, 0 = different from previous
            answers.add(if (currentValue == previousValue) 1 else 0)
        }

        return answers
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
            else -> "\"$data\""
        }
    }
}