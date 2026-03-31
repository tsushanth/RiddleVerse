package com.kreativekoala.riddleverse

import kotlin.random.Random

data class CryptoPuzzleConfig(
    val originalText: String,
    val numberMapping: Map<Char, Int>,
    val revealedLetters: Set<Char>,
    val hiddenLetters: Set<Char>,
    val frequencyMap: Map<Char, Int>,
    val targetWord: String? = null
)

class CryptoPuzzlePreprocessor {

    /**
     * Preprocesses a quote to create a challenging crypto puzzle
     */
    fun preprocessQuote(
        originalText: String,
        difficulty: String = "Medium",
        targetWord: String? = null
    ): CryptoPuzzleConfig {
        val cleanText = originalText.uppercase().filter { it.isLetter() || it.isWhitespace() }

        // Step 1: Create frequency map for all letters
        val frequencyMap = createFrequencyMap(cleanText)

        // Step 2: Determine removal percentage based on difficulty
        val removalPercentage = when (difficulty.lowercase()) {
            "easy" -> 0.5    // Remove 50% of high-frequency chars
            "medium" -> 0.6  // Remove 60% of high-frequency chars
            "hard" -> 0.7    // Remove 70% of high-frequency chars
            "expert" -> 0.8  // Remove 80% of high-frequency chars
            else -> 0.6
        }

        // Step 3: Select high-frequency characters to hide
        val hiddenLetters = selectCharactersToHide(frequencyMap, removalPercentage)

        // Step 4: Create number mapping for hidden letters
        val numberMapping = createNumberMapping(hiddenLetters)

        // Step 5: Determine revealed letters (some hidden letters might be revealed as hints)
        val revealedLetters = selectRevealedLetters(hiddenLetters, difficulty)

        return CryptoPuzzleConfig(
            originalText = cleanText,
            numberMapping = numberMapping,
            revealedLetters = revealedLetters,
            hiddenLetters = hiddenLetters,
            frequencyMap = frequencyMap,
            targetWord = targetWord
        )
    }

    /**
     * Creates a frequency map of all letters in the text
     */
    private fun createFrequencyMap(text: String): Map<Char, Int> {
        val frequencyMap = mutableMapOf<Char, Int>()

        text.forEach { char ->
            if (char.isLetter()) {
                val upperChar = char.uppercaseChar()
                frequencyMap[upperChar] = frequencyMap.getOrDefault(upperChar, 0) + 1
            }
        }

        return frequencyMap.toMap()
    }

    /**
     * Selects high-frequency characters to hide based on removal percentage
     */
    private fun selectCharactersToHide(
        frequencyMap: Map<Char, Int>,
        removalPercentage: Double
    ): Set<Char> {
        // Sort characters by frequency (descending)
        val sortedByFrequency = frequencyMap.entries
            .sortedByDescending { it.value }
            .map { it.key }

        val hiddenLetters = mutableSetOf<Char>()

        // Always include some common letters to make it challenging
        val guaranteedHidden = listOf('E', 'T', 'A', 'O', 'I', 'N', 'S', 'H', 'R')

        // Add guaranteed letters that exist in the text
        guaranteedHidden.forEach { letter ->
            if (frequencyMap.containsKey(letter)) {
                hiddenLetters.add(letter)
            }
        }

        // Calculate how many more characters to hide
        val totalUniqueLetters = frequencyMap.size
        val targetHiddenCount = (totalUniqueLetters * removalPercentage).toInt()
        val remainingToHide = maxOf(0, targetHiddenCount - hiddenLetters.size)

        // Add additional high-frequency characters
        sortedByFrequency.take(remainingToHide + hiddenLetters.size).forEach { letter ->
            hiddenLetters.add(letter)
        }

        // Randomize: sometimes remove ALL occurrences, sometimes leave some
        val finalHiddenLetters = mutableSetOf<Char>()
        hiddenLetters.forEach { letter ->
            val randomChance = Random.nextDouble()
            when {
                randomChance < 0.7 -> finalHiddenLetters.add(letter) // 70% chance to hide completely
                randomChance < 0.9 -> {
                    // 20% chance to hide only if frequency > 2
                    if (frequencyMap[letter]!! > 2) {
                        finalHiddenLetters.add(letter)
                    }
                }
                // 10% chance to not hide this letter at all
            }
        }

        // Ensure we have at least some hidden letters
        if (finalHiddenLetters.size < 3) {
            sortedByFrequency.take(5).forEach { finalHiddenLetters.add(it) }
        }

        return finalHiddenLetters
    }

    /**
     * Creates number mapping for hidden letters (1-26)
     */
    private fun createNumberMapping(hiddenLetters: Set<Char>): Map<Char, Int> {
        val numberMapping = mutableMapOf<Char, Int>()
        val availableNumbers = (1..26).toMutableList()
        availableNumbers.shuffle() // Randomize number assignments

        hiddenLetters.forEachIndexed { index, letter ->
            if (index < availableNumbers.size) {
                numberMapping[letter] = availableNumbers[index]
            }
        }

        return numberMapping
    }

    /**
     * Selects some hidden letters to reveal as hints based on difficulty
     */
    private fun selectRevealedLetters(hiddenLetters: Set<Char>, difficulty: String): Set<Char> {
        val revealPercentage = when (difficulty.lowercase()) {
            "easy" -> 0.4    // Reveal 40% of hidden letters
            "medium" -> 0.25 // Reveal 25% of hidden letters
            "hard" -> 0.15   // Reveal 15% of hidden letters
            "expert" -> 0.1  // Reveal 10% of hidden letters
            else -> 0.25
        }

        val revealCount = (hiddenLetters.size * revealPercentage).toInt()
        if (revealCount == 0) return emptySet()

        // Prioritize revealing less common letters to give strategic hints
        val sortedHiddenLetters = hiddenLetters.toList().shuffled()

        return sortedHiddenLetters.take(revealCount).toSet()
    }

    /**
     * Analyzes the puzzle difficulty and provides stats
     */
    fun analyzePuzzle(config: CryptoPuzzleConfig): PuzzleAnalysis {
        val totalLetters = config.originalText.count { it.isLetter() }
        val uniqueLetters = config.frequencyMap.size
        val hiddenCount = config.hiddenLetters.size
        val revealedCount = config.revealedLetters.size
        val hiddenPercentage = (hiddenCount.toDouble() / uniqueLetters * 100).toInt()

        return PuzzleAnalysis(
            totalLetters = totalLetters,
            uniqueLetters = uniqueLetters,
            hiddenLetters = hiddenCount,
            revealedHints = revealedCount,
            hiddenPercentage = hiddenPercentage,
            topFrequentLetters = config.frequencyMap.entries
                .sortedByDescending { it.value }
                .take(5)
                .map { "${it.key}(${it.value})" }
        )
    }
}

data class PuzzleAnalysis(
    val totalLetters: Int,
    val uniqueLetters: Int,
    val hiddenLetters: Int,
    val revealedHints: Int,
    val hiddenPercentage: Int,
    val topFrequentLetters: List<String>
)

// Usage example:
/*
val preprocessor = CryptoPuzzlePreprocessor()
val config = preprocessor.preprocessQuote(
    originalText = "WHERE THERE IS LOVE THERE IS LIFE",
    difficulty = "Medium",
    targetWord = "LOVE"
)

val analysis = preprocessor.analyzePuzzle(config)
Log.d("CryptoPuzzle", "📊 Puzzle Analysis:")
Log.d("CryptoPuzzle", "  Hidden: ${analysis.hiddenLetters}/${analysis.uniqueLetters} letters (${analysis.hiddenPercentage}%)")
Log.d("CryptoPuzzle", "  Revealed hints: ${analysis.revealedHints}")
Log.d("CryptoPuzzle", "  Top frequent: ${analysis.topFrequentLetters.joinToString(", ")}")
Log.d("CryptoPuzzle", "  Hidden letters: ${config.hiddenLetters.sorted()}")
Log.d("CryptoPuzzle", "  Revealed letters: ${config.revealedLetters.sorted()}")

// Convert to your existing CryptoPuzzleData format:
val puzzleData = CryptoPuzzleData(
    originalText = config.originalText,
    numberMapping = config.numberMapping,
    revealedLetters = config.revealedLetters,
    targetWord = config.targetWord
)
*/