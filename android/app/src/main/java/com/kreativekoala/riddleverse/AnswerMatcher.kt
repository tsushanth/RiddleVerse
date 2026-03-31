package com.kreativekoala.riddleverse

import java.text.Normalizer
import kotlin.math.max
import kotlin.math.min

/**
 * Robust answer matching system for puzzle games
 * Handles various user input scenarios with configurable tolerance
 */
class AnswerMatcher {

    companion object {
        // Default thresholds for different matching strategies
        private const val DEFAULT_SIMILARITY_THRESHOLD = 0.75
        private const val DEFAULT_PARTIAL_MATCH_THRESHOLD = 0.8
        private const val DEFAULT_FUZZY_THRESHOLD = 0.7

        // Common synonyms and variations
        private val commonSynonyms = mapOf(
            "car" to listOf("automobile", "vehicle", "auto"),
            "dog" to listOf("canine", "puppy", "hound", "mutt"),
            "cat" to listOf("feline", "kitten", "kitty"),
            "house" to listOf("home", "building", "residence"),
            "phone" to listOf("telephone", "mobile", "cell", "smartphone"),
            "computer" to listOf("pc", "laptop", "desktop", "machine"),
            "bike" to listOf("bicycle", "cycle"),
            "plane" to listOf("airplane", "aircraft", "jet"),
            "boat" to listOf("ship", "vessel", "yacht"),
            "tree" to listOf("oak", "pine", "maple", "birch", "cedar"),
            "flower" to listOf("rose", "daisy", "tulip", "lily"),
            "bird" to listOf("eagle", "robin", "sparrow", "hawk"),
            "fish" to listOf("salmon", "trout", "bass", "tuna"),
            "food" to listOf("meal", "dish", "cuisine"),
            "drink" to listOf("beverage", "liquid", "fluid"),
            "book" to listOf("novel", "tome", "publication"),
            "movie" to listOf("film", "cinema", "picture"),
            "music" to listOf("song", "tune", "melody"),
            "picture" to listOf("photo", "image", "photograph")
        )

        // Articles and common words to ignore
        private val ignoredWords = setOf(
            "a", "an", "the", "is", "are", "was", "were", "be", "been", "being",
            "have", "has", "had", "do", "does", "did", "will", "would", "could",
            "should", "may", "might", "can", "must", "shall", "this", "that",
            "these", "those", "in", "on", "at", "by", "for", "with", "without",
            "of", "to", "from", "up", "down", "out", "off", "over", "under",
            "again", "further", "then", "once", "here", "there", "when", "where",
            "why", "how", "all", "any", "both", "each", "few", "more", "most",
            "other", "some", "such", "no", "nor", "not", "only", "own", "same",
            "so", "than", "too", "very", "just", "now"
        )

        // Plural handling
        private val pluralRules = listOf(
            Pair(Regex("ies$"), "y"),      // cities -> city
            Pair(Regex("ves$"), "f"),      // wolves -> wolf
            Pair(Regex("ches$"), "ch"),    // watches -> watch
            Pair(Regex("shes$"), "sh"),    // dishes -> dish
            Pair(Regex("xes$"), "x"),      // boxes -> box
            Pair(Regex("oes$"), "o"),      // heroes -> hero
            Pair(Regex("s$"), "")          // cats -> cat
        )
    }

    /**
     * Main matching function that uses multiple strategies
     */
    fun isMatch(
        userAnswer: String,
        correctAnswer: String,
        allowPartialMatch: Boolean = true,
        allowSynonyms: Boolean = true,
        allowTypos: Boolean = true,
        customSynonyms: Map<String, List<String>> = emptyMap()
    ): MatchResult {

        val normalizedUser = normalizeText(userAnswer)
        val normalizedCorrect = normalizeText(correctAnswer)

        // 1. Exact match (highest confidence)
        if (normalizedUser == normalizedCorrect) {
            return MatchResult(true, 1.0, MatchType.EXACT, "Exact match")
        }

        // 2. Check for synonyms and variations
        if (allowSynonyms) {
            val synonymMatch = checkSynonyms(normalizedUser, normalizedCorrect, customSynonyms)
            if (synonymMatch.isMatch) return synonymMatch
        }

        // 3. Partial matching (contains key words)
        if (allowPartialMatch) {
            val partialMatch = checkPartialMatch(normalizedUser, normalizedCorrect)
            if (partialMatch.isMatch) return partialMatch
        }

        // 4. Fuzzy matching for typos and similar strings
        if (allowTypos) {
            val fuzzyMatch = checkFuzzyMatch(normalizedUser, normalizedCorrect)
            if (fuzzyMatch.isMatch) return fuzzyMatch
        }

        // 5. Plural/singular variations
        val pluralMatch = checkPluralVariations(normalizedUser, normalizedCorrect)
        if (pluralMatch.isMatch) return pluralMatch

        return MatchResult(false, 0.0, MatchType.NO_MATCH, "No match found")
    }

    /**
     * Normalize text for comparison
     */
    private fun normalizeText(text: String): String {
        return text
            .lowercase()
            .trim()
            // Remove accents and diacritics
            .let { Normalizer.normalize(it, Normalizer.Form.NFD) }
            .replace(Regex("[\\p{InCombiningDiacriticalMarks}]"), "")
            // Remove punctuation
            .replace(Regex("[^a-z0-9\\s]"), "")
            // Normalize whitespace
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    /**
     * Check for synonyms and word variations
     */
    private fun checkSynonyms(
        userAnswer: String,
        correctAnswer: String,
        customSynonyms: Map<String, List<String>>
    ): MatchResult {
        val userWords = extractKeyWords(userAnswer)
        val correctWords = extractKeyWords(correctAnswer)

        // Combine built-in and custom synonyms
        val allSynonyms = commonSynonyms + customSynonyms

        for (correctWord in correctWords) {
            for (userWord in userWords) {
                // Direct synonym check
                val synonyms = allSynonyms[correctWord] ?: emptyList()
                if (synonyms.contains(userWord)) {
                    return MatchResult(true, 0.9, MatchType.SYNONYM, "Synonym match: $userWord -> $correctWord")
                }

                // Reverse synonym check
                val reverseSynonyms = allSynonyms[userWord] ?: emptyList()
                if (reverseSynonyms.contains(correctWord)) {
                    return MatchResult(true, 0.9, MatchType.SYNONYM, "Reverse synonym match: $userWord -> $correctWord")
                }
            }
        }

        return MatchResult(false, 0.0, MatchType.NO_MATCH, "No synonym match")
    }

    /**
     * Check if user answer contains key words from correct answer
     */
    private fun checkPartialMatch(userAnswer: String, correctAnswer: String): MatchResult {
        val userWords = extractKeyWords(userAnswer)
        val correctWords = extractKeyWords(correctAnswer)

        if (correctWords.isEmpty()) return MatchResult(false, 0.0, MatchType.NO_MATCH, "No key words")

        var matches = 0
        val matchedWords = mutableListOf<String>()

        for (correctWord in correctWords) {
            for (userWord in userWords) {
                if (userWord.contains(correctWord) || correctWord.contains(userWord)) {
                    matches++
                    matchedWords.add(correctWord)
                    break
                }
            }
        }

        val confidence = matches.toDouble() / correctWords.size

        if (confidence >= DEFAULT_PARTIAL_MATCH_THRESHOLD) {
            return MatchResult(
                true,
                confidence,
                MatchType.PARTIAL,
                "Partial match: ${matchedWords.joinToString(", ")}"
            )
        }

        return MatchResult(false, confidence, MatchType.NO_MATCH, "Insufficient partial match")
    }

    /**
     * Fuzzy matching using Levenshtein distance for typo tolerance
     */
    private fun checkFuzzyMatch(userAnswer: String, correctAnswer: String): MatchResult {
        val similarity = calculateSimilarity(userAnswer, correctAnswer)

        if (similarity >= DEFAULT_FUZZY_THRESHOLD) {
            return MatchResult(
                true,
                similarity,
                MatchType.FUZZY,
                "Fuzzy match (similarity: ${String.format("%.2f", similarity)})"
            )
        }

        // Also check fuzzy matching on individual words
        val userWords = extractKeyWords(userAnswer)
        val correctWords = extractKeyWords(correctAnswer)

        var bestSimilarity = 0.0
        var bestMatch = ""

        for (correctWord in correctWords) {
            for (userWord in userWords) {
                val wordSimilarity = calculateSimilarity(userWord, correctWord)
                if (wordSimilarity > bestSimilarity) {
                    bestSimilarity = wordSimilarity
                    bestMatch = "$userWord -> $correctWord"
                }
            }
        }

        if (bestSimilarity >= DEFAULT_FUZZY_THRESHOLD) {
            return MatchResult(
                true,
                bestSimilarity,
                MatchType.FUZZY,
                "Fuzzy word match: $bestMatch"
            )
        }

        return MatchResult(false, bestSimilarity, MatchType.NO_MATCH, "No fuzzy match")
    }

    /**
     * Check for plural/singular variations
     */
    private fun checkPluralVariations(userAnswer: String, correctAnswer: String): MatchResult {
        val userSingular = makeSingular(userAnswer)
        val correctSingular = makeSingular(correctAnswer)

        if (userSingular == correctSingular) {
            return MatchResult(true, 0.95, MatchType.PLURAL_VARIATION, "Plural/singular variation")
        }

        return MatchResult(false, 0.0, MatchType.NO_MATCH, "No plural variation match")
    }

    /**
     * Extract meaningful words (remove common articles, prepositions, etc.)
     */
    private fun extractKeyWords(text: String): List<String> {
        return text.split(" ")
            .filter { it.length > 2 && !ignoredWords.contains(it) }
            .distinct()
    }

    /**
     * Convert to singular form using basic rules
     */
    private fun makeSingular(text: String): String {
        val words = text.split(" ")
        return words.joinToString(" ") { word ->
            for ((pattern, replacement) in pluralRules) {
                if (pattern.containsMatchIn(word)) {
                    return@joinToString word.replace(pattern, replacement)
                }
            }
            word
        }
    }

    /**
     * Calculate string similarity using Levenshtein distance
     */
    private fun calculateSimilarity(s1: String, s2: String): Double {
        val distance = levenshteinDistance(s1, s2)
        val maxLength = max(s1.length, s2.length)

        return if (maxLength == 0) 1.0 else 1.0 - (distance.toDouble() / maxLength)
    }

    /**
     * Calculate Levenshtein distance between two strings
     */
    private fun levenshteinDistance(s1: String, s2: String): Int {
        val dp = Array(s1.length + 1) { IntArray(s2.length + 1) }

        for (i in 0..s1.length) dp[i][0] = i
        for (j in 0..s2.length) dp[0][j] = j

        for (i in 1..s1.length) {
            for (j in 1..s2.length) {
                val cost = if (s1[i - 1] == s2[j - 1]) 0 else 1
                dp[i][j] = min(
                    min(dp[i - 1][j] + 1, dp[i][j - 1] + 1),
                    dp[i - 1][j - 1] + cost
                )
            }
        }

        return dp[s1.length][s2.length]
    }
}

/**
 * Result of answer matching
 */
data class MatchResult(
    val isMatch: Boolean,
    val confidence: Double,
    val matchType: MatchType,
    val explanation: String
)

/**
 * Types of matches
 */
enum class MatchType {
    EXACT,              // Perfect match
    SYNONYM,            // Synonym or known variation
    PARTIAL,            // Contains key words
    FUZZY,              // Similar with typos
    PLURAL_VARIATION,   // Plural/singular difference
    NO_MATCH            // No match found
}

/**
 * Enhanced answer checking for Progressive Reveal puzzles (client-side)
 * Mimics server behavior but runs locally for speed and cost efficiency
 */
fun checkProgressiveAnswer(
    userAnswer: String,
    correctAnswer: String,
    puzzleData: ProgressiveRevealPuzzleData,
    currentStep: Int
): MatchResult {
    val matcher = AnswerMatcher()

    // Get custom synonyms from puzzle data if available
    val customSynonyms = mutableMapOf<String, List<String>>()

    // Extract category-specific synonyms if defined in puzzle
    puzzleData.gameFlow.scoringSystem
        .filterKeys { it.startsWith("synonym_") }
        .forEach { (key, value) ->
            val word = key.removePrefix("synonym_")
            customSynonyms[word] = value.toString().split(",").map { it.trim() }
        }

    // Adjust similarity threshold based on difficulty and step
    val similarityThreshold = when (puzzleData.difficulty.lowercase()) {
        "easy" -> 0.70      // More lenient
        "medium" -> 0.75
        "hard" -> 0.80
        "hardest" -> 0.85   // Strict like server's high threshold
        else -> 0.75
    }

    // Be more lenient on later steps (more clues given)
    val adjustedThreshold = when {
        currentStep >= 4 -> similarityThreshold - 0.05
        currentStep >= 3 -> similarityThreshold - 0.03
        else -> similarityThreshold
    }

    // Adjust tolerance based on difficulty and step
    val allowPartialMatch = puzzleData.difficulty != "hardest"
    val allowSynonyms = true
    val allowTypos = currentStep > 2 || puzzleData.difficulty == "easy"

    return matcher.isMatch(
        userAnswer = userAnswer,
        correctAnswer = correctAnswer,
        allowPartialMatch = allowPartialMatch,
        allowSynonyms = allowSynonyms,
        allowTypos = allowTypos,
        customSynonyms = customSynonyms
    )
}

fun checkQAAnswer(
    userAnswer: String,
    correctAnswer: String,
    puzzleType: String? = null,
    puzzleId: String? = null
): MatchResult {
    val matcher = AnswerMatcher()

    // Create Q&A specific synonyms based on puzzle type
    val customSynonyms = getQASpecificSynonyms(puzzleType)

    // Adjust matching parameters based on puzzle type
    val (allowPartialMatch, allowSynonyms, allowTypos) = getQAMatchingSettings(puzzleType)

    // Use existing isMatch function with Q&A specific settings
    val result = matcher.isMatch(
        userAnswer = userAnswer,
        correctAnswer = correctAnswer,
        allowPartialMatch = allowPartialMatch,
        allowSynonyms = allowSynonyms,
        allowTypos = allowTypos,
        customSynonyms = customSynonyms
    )

    // Enhanced result for numeric answers in math puzzles
    if (puzzleType?.contains("math", ignoreCase = true) == true ||
        puzzleType?.contains("arithmetic", ignoreCase = true) == true) {

        val numericResult = checkNumericAnswerQA(userAnswer, correctAnswer)
        if (numericResult.isMatch && numericResult.confidence > result.confidence) {
            return numericResult
        }
    }

    return result
}

/**
 * Get Q&A specific synonyms based on puzzle type
 */
private fun getQASpecificSynonyms(puzzleType: String?): Map<String, List<String>> {
    val baseSynonyms = mutableMapOf<String, List<String>>()

    when (puzzleType?.lowercase()) {
        "math", "arithmetic" -> {
            baseSynonyms.putAll(mapOf(
                "plus" to listOf("add", "addition", "sum", "+", "and"),
                "minus" to listOf("subtract", "subtraction", "difference", "-", "take away"),
                "times" to listOf("multiply", "multiplication", "product", "x", "*", "multiplied by"),
                "divide" to listOf("division", "quotient", "/", "divided by"),
                "equals" to listOf("equal", "is", "=", "makes", "gives"),
                "zero" to listOf("0", "nothing", "none"),
                "one" to listOf("1", "single", "unity"),
                "two" to listOf("2", "pair", "double", "couple"),
                "three" to listOf("3", "triple", "trio"),
                "four" to listOf("4", "quarter", "quad"),
                "five" to listOf("5", "quintuple"),
                "six" to listOf("6", "half dozen"),
                "seven" to listOf("7"),
                "eight" to listOf("8"),
                "nine" to listOf("9"),
                "ten" to listOf("10", "decade"),
                "hundred" to listOf("100"),
                "thousand" to listOf("1000")
            ))
        }

        "riddle", "riddles" -> {
            baseSynonyms.putAll(mapOf(
                "water" to listOf("h2o", "liquid", "fluid"),
                "fire" to listOf("flame", "blaze", "heat"),
                "earth" to listOf("ground", "soil", "dirt", "land"),
                "air" to listOf("wind", "breeze", "atmosphere"),
                "sun" to listOf("star", "solar"),
                "moon" to listOf("lunar", "satellite"),
                "time" to listOf("clock", "hour", "minute", "moment"),
                "money" to listOf("cash", "currency", "coin", "dollar"),
                "death" to listOf("dying", "end", "demise"),
                "life" to listOf("living", "existence", "being")
            ))
        }

        "geography" -> {
            baseSynonyms.putAll(mapOf(
                "mountain" to listOf("peak", "hill", "summit", "mount"),
                "river" to listOf("stream", "creek", "waterway"),
                "ocean" to listOf("sea", "water", "marine"),
                "city" to listOf("town", "urban", "municipality"),
                "country" to listOf("nation", "state", "republic"),
                "capital" to listOf("main city", "seat"),
                "desert" to listOf("wasteland", "arid", "sahara"),
                "forest" to listOf("woods", "jungle", "trees")
            ))
        }

        "history" -> {
            baseSynonyms.putAll(mapOf(
                "war" to listOf("battle", "conflict", "fight", "combat"),
                "king" to listOf("monarch", "ruler", "emperor", "royal"),
                "queen" to listOf("monarch", "ruler", "empress", "royal"),
                "president" to listOf("leader", "head of state"),
                "revolution" to listOf("uprising", "revolt", "rebellion"),
                "ancient" to listOf("old", "historic", "classical"),
                "modern" to listOf("current", "contemporary", "recent")
            ))
        }

        "science" -> {
            baseSynonyms.putAll(mapOf(
                "atom" to listOf("particle", "element"),
                "molecule" to listOf("compound", "chemical"),
                "energy" to listOf("power", "force", "electricity"),
                "gravity" to listOf("gravitational force", "weight"),
                "light" to listOf("photon", "illumination", "bright"),
                "sound" to listOf("audio", "noise", "vibration"),
                "heat" to listOf("thermal", "temperature", "warm"),
                "cold" to listOf("cool", "freezing", "ice")
            ))
        }

        "literature", "books" -> {
            baseSynonyms.putAll(mapOf(
                "author" to listOf("writer", "novelist", "poet"),
                "book" to listOf("novel", "publication", "tome"),
                "story" to listOf("tale", "narrative", "plot"),
                "character" to listOf("person", "protagonist", "hero"),
                "poem" to listOf("verse", "poetry", "rhyme")
            ))
        }
    }

    return baseSynonyms
}

/**
 * Get matching settings based on puzzle type
 */
private fun getQAMatchingSettings(puzzleType: String?): Triple<Boolean, Boolean, Boolean> {
    return when (puzzleType?.lowercase()) {
        "math", "arithmetic" -> {
            // Math: strict on exact answers, but allow typos for number words
            Triple(false, true, true)
        }

        "riddle", "riddles" -> {
            // Riddles: very flexible, accept creative answers
            Triple(true, true, true)
        }

        "geography", "history", "science" -> {
            // Factual subjects: moderate flexibility
            Triple(true, true, true)
        }

        "literature", "books" -> {
            // Literature: allow partial matches for names and titles
            Triple(true, true, true)
        }

        else -> {
            // Default: balanced approach
            Triple(true, true, true)
        }
    }
}

/**
 * Enhanced numeric checking for Q&A math puzzles
 */
private fun checkNumericAnswerQA(userAnswer: String, correctAnswer: String): MatchResult {
    try {
        val userNum = parseNumber(userAnswer)
        val correctNum = parseNumber(correctAnswer)

        if (userNum != null && correctNum != null) {
            if (userNum == correctNum) {
                return MatchResult(
                    isMatch = true,
                    confidence = 1.0,
                    matchType = MatchType.EXACT,
                    explanation = "Exact numeric match"
                )
            }

            // Allow for small floating point differences
            val difference = kotlin.math.abs(userNum - correctNum)
            val tolerance = kotlin.math.max(1.0, kotlin.math.abs(correctNum) * 0.001)

            if (difference <= tolerance) {
                return MatchResult(
                    isMatch = true,
                    confidence = 0.98,
                    matchType = MatchType.FUZZY,
                    explanation = "Close numeric match within tolerance"
                )
            }

            // Check for reasonable rounding differences
            if (difference <= 1.0 && correctNum > 10) {
                return MatchResult(
                    isMatch = true,
                    confidence = 0.90,
                    matchType = MatchType.FUZZY,
                    explanation = "Reasonable rounding difference"
                )
            }
        }

    } catch (e: Exception) {
        // Not valid numbers, return no match
    }

    return MatchResult(
        isMatch = false,
        confidence = 0.0,
        matchType = MatchType.NO_MATCH,
        explanation = "Numeric parsing failed"
    )
}

/**
 * Parse number from string, handling various formats
 */
private fun parseNumber(str: String): Double? {
    val cleanStr = str.trim().lowercase()

    // Try direct parsing first
    try {
        return cleanStr.toDouble()
    } catch (e: NumberFormatException) {
        // Continue to word parsing
    }

    // Handle written numbers
    val numberWords = mapOf(
        "zero" to 0.0, "one" to 1.0, "two" to 2.0, "three" to 3.0, "four" to 4.0,
        "five" to 5.0, "six" to 6.0, "seven" to 7.0, "eight" to 8.0, "nine" to 9.0,
        "ten" to 10.0, "eleven" to 11.0, "twelve" to 12.0, "thirteen" to 13.0,
        "fourteen" to 14.0, "fifteen" to 15.0, "sixteen" to 16.0, "seventeen" to 17.0,
        "eighteen" to 18.0, "nineteen" to 19.0, "twenty" to 20.0, "thirty" to 30.0,
        "forty" to 40.0, "fifty" to 50.0, "sixty" to 60.0, "seventy" to 70.0,
        "eighty" to 80.0, "ninety" to 90.0, "hundred" to 100.0, "thousand" to 1000.0,
        "million" to 1000000.0
    )

    // Check for exact word match
    numberWords[cleanStr]?.let { return it }

    // Handle fractions
    if (cleanStr.contains("/")) {
        val parts = cleanStr.split("/")
        if (parts.size == 2) {
            try {
                val numerator = parts[0].toDouble()
                val denominator = parts[1].toDouble()
                if (denominator != 0.0) {
                    return numerator / denominator
                }
            } catch (e: NumberFormatException) {
                // Continue
            }
        }
    }

    // Handle percentage
    if (cleanStr.endsWith("%")) {
        try {
            val numPart = cleanStr.dropLast(1).toDouble()
            return numPart / 100.0
        } catch (e: NumberFormatException) {
            // Continue
        }
    }

    return null
}

/**
 * Convenience function that matches the interface from ProgressiveRevealPuzzleScreen
 * This allows easy integration with existing Q&A puzzle screens
 */
fun AnswerMatcher.Companion.checkQAAnswerCompatible(
    userAnswer: String,
    correctAnswer: String,
    puzzleType: String? = null,
    puzzleId: String? = null
): MatchResult {
    return checkQAAnswer(userAnswer, correctAnswer, puzzleType, puzzleId)
}