package com.kreativekoala.riddleverse

data class Puzzle(
    val puzzleId: String,
    val puzzleType: String?,
    val question: String,
    val answer: String,
    val hint: String,
    val difficulty: String?,
    val timer: String? = "1:00",
    val options: List<String> = emptyList()
)

data class PuzzleGroup(
    val id: String,
    val name: String,
    val description: String,
    val puzzleTypes: List<String>, // List of puzzle type names like ["math", "anagram", "synonyms"]
    val completedTypes: Set<String> = emptySet(), // Which puzzle types user has completed
    val category: String,
    val difficulty: String = "Mixed",
    val totalTypes: Int
) {
    val completedCount: Int get() = completedTypes.size
    val isCompleted: Boolean get() = completedTypes.size == puzzleTypes.size
    val currentTypeIndex: Int get() = completedTypes.size.coerceAtMost(puzzleTypes.size - 1)
}
