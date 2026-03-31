package com.kreativekoala.riddleverse

data class PuzzleTypeConfig(
    val type: String,
    val requiredCount: Int
)

data class PuzzleGroupConfig(
    val groupId: String,
    val groupName: String,
    val puzzleTypes: List<PuzzleTypeConfig>,
    val difficulty: String = "Mixed"
) {
    fun getRequiredCountForType(puzzleType: String): Int {
        return puzzleTypes.find { it.type == puzzleType }?.requiredCount ?: 5 // Default fallback
    }

    fun getTotalRequiredPuzzles(): Int {
        return puzzleTypes.sumOf { it.requiredCount }
    }

    fun getPuzzleTypesList(): List<String> {
        return puzzleTypes.map { it.type }
    }
}

object PuzzleGroupConfigs {

    val PUZZLE_GROUPS = mapOf(
        "word-wizard" to PuzzleGroupConfig(
            groupId = "word-wizard",
            groupName = "Word Wizard",
            puzzleTypes = listOf(
                PuzzleTypeConfig("anagram", 3),
                PuzzleTypeConfig("synonyms", 5),
                PuzzleTypeConfig("antonyms", 4)
            ),
            difficulty = "Easy"
        ),

        "math-master" to PuzzleGroupConfig(
            groupId = "math-master",
            groupName = "Math Master",
            puzzleTypes = listOf(
                PuzzleTypeConfig("math", 7),
                PuzzleTypeConfig("average", 3),
                PuzzleTypeConfig("percentages", 5)
            ),
            difficulty = "Medium"
        ),

        "memory-champion" to PuzzleGroupConfig(
            groupId = "memory-champion",
            groupName = "Memory Champion",
            puzzleTypes = listOf(
                PuzzleTypeConfig("memorysquares", 4),
                PuzzleTypeConfig("memorystory", 6)
            ),
            difficulty = "Hard"
        ),

        "brain-teaser" to PuzzleGroupConfig(
            groupId = "brain-teaser",
            groupName = "Brain Teaser",
            puzzleTypes = listOf(
                PuzzleTypeConfig("trivia", 8),
                PuzzleTypeConfig("storypuzzle", 3)
            ),
            difficulty = "Mixed"
        )
    )

    fun getGroupConfig(groupId: String): PuzzleGroupConfig? {
        return PUZZLE_GROUPS[groupId]
    }

    fun getRequiredCountForType(groupId: String, puzzleType: String): Int {
        return getGroupConfig(groupId)?.getRequiredCountForType(puzzleType) ?: 5
    }

    fun getAllGroups(): List<PuzzleGroupConfig> {
        return PUZZLE_GROUPS.values.toList()
    }
}