package com.kreativekoala.riddleverse

data class CustomPuzzle(
    val id: String,
    val name: String,
    val numPuzzles: String,
    val format: String,
    val creator: String,
    val status: String,
    val createdAt: Long,
    val updatedAt: Long,
    val topic: String? = null, // For icon selection
    val themeColors: PuzzleTheme? = null, // For background theming
    val playCount: Int = 0, // For popularity indicators
    val averageRating: Float = 0f, // For quality indicators
    val completionCount: Int = 0, // For engagement metrics
    val difficulty: String? = null // For difficulty badges
)

data class PuzzleTheme(
    val primaryColor: String,
    val secondaryColor: String,
    val backgroundPattern: String? = null,
    val emoji: String? = null
)

data class PuzzleLeaderboard(
    val puzzleId: String,
    val puzzleName: String? = null,
    val puzzleCreator: String? = null,
    val topPlayers: List<LeaderboardEntry>,
    val userRank: Int? = null,
    val userScore: Int? = null,
    val userTime: Long? = null,
    val userPercentile: Double? = null,
    val totalPlayers: Int,
    val statistics: LeaderboardStatistics? = null,
    val isEmpty: Boolean = false
)

data class CustomPuzzleDetail(
    val puzzleId: String,
    val question: String,
    val answer: String,
    val hint: String,
    val format: String,
    val options: List<String> = emptyList()
)

data class CustomPuzzleSet(
    val status: String,
    val id: String,
    val topic: String,
    val format: String,
    val numPuzzles: Int,
    val puzzleData: PuzzleData,
    val createdAt: Long,
    val updatedAt: Long
)

data class PuzzleData(
    val puzzles: List<Puzzle>,
    val leaderboard: List<LeaderboardEntry> = emptyList()
) {
    fun isValid(): Boolean {
        return puzzles.isNotEmpty() && puzzles.all { puzzle ->
            puzzle.question.isNotEmpty() && puzzle.answer.isNotEmpty()
        }
    }
}

data class LeaderboardEntry(
    val rank: Int,
    val playerName: String,
    val score: Int,
    val timeTaken: Long? = null,
    val userId: String,
    val badgeIcon: String = "🏆"
)

data class LeaderboardStatistics(
    val totalPlayers: Int,
    val highestScore: Int?,
    val lowestScore: Int?,
    val averageScore: Double,
    val fastestTime: Long?,
    val slowestTime: Long?,
    val averageTime: Long,
    val totalCompletions: Int = totalPlayers // Use totalPlayers as completions
)