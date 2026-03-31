package com.kreativekoala.riddleverse

import android.content.Context
import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.floor

/**
 * Manages competitive ranking using simple Firebase metrics
 * Stores only essential data: score ranges and percentile calculations
 */
class CompetitiveRankingManager private constructor() {

    companion object {
        @Volatile
        private var INSTANCE: CompetitiveRankingManager? = null

        fun getInstance(): CompetitiveRankingManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: CompetitiveRankingManager().also { INSTANCE = it }
            }
        }

        private const val COLLECTION_GLOBAL_STATS = "global_puzzle_stats"
        private const val COLLECTION_USER_RANKINGS = "user_rankings"
        private const val TAG = "CompetitiveRanking"
    }

    private val firestore = FirebaseFirestore.getInstance()

    // Data classes for ranking system
    data class GlobalPuzzleStats(
        val puzzleType: String = "",
        val difficulty: String = "",
        val minScore: Int = 0,
        val maxScore: Int = 0,
        val percentileBreakpoints: Map<String, Int> = emptyMap(), // "25" -> 150, "50" -> 300, etc.
        val totalPlayers: Int = 0,
        val lastUpdated: Long = System.currentTimeMillis()
    )

    data class UserRankingData(
        val userId: String = "",
        val puzzleType: String = "",
        val difficulty: String = "",
        val bestScore: Int = 0,
        val currentPercentile: Int = 0, // 0-100
        val lastUpdated: Long = System.currentTimeMillis()
    )

    data class RankingImprovement(
        val oldPercentile: Int,
        val newPercentile: Int,
        val percentileGain: Int,
        val rankingMessage: String,
        val showAnimation: Boolean
    ) {
        val improved: Boolean get() = newPercentile > oldPercentile
    }

    data class CompetitiveInsight(
        val currentPercentile: Int,
        val totalPlayers: Int,
        val scoreToNextPercentile: Int?,
        val nextPercentileTarget: Int,
        val competitiveMessage: String,
        val performanceLevel: PerformanceLevel
    )

    enum class PerformanceLevel(val displayName: String, val emoji: String) {
        BEGINNER("Beginner", "🌱"),
        DEVELOPING("Developing", "📈"),
        SKILLED("Skilled", "⭐"),
        ADVANCED("Advanced", "🏆"),
        EXPERT("Expert", "👑"),
        ELITE("Elite", "💎")
    }

    /**
     * Submit user's session score and get ranking improvement data
     */
    suspend fun submitScoreAndGetImprovement(
        userId: String,
        puzzleType: String,
        difficulty: String,
        sessionScore: Int
    ): RankingImprovement? = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "🎯 Submitting score: $sessionScore for $puzzleType ($difficulty)")

            // Get current user ranking
            val currentRanking = getUserRanking(userId, puzzleType, difficulty)
            val oldPercentile = currentRanking?.currentPercentile ?: 0
            val oldBestScore = currentRanking?.bestScore ?: 0

            // Only update if this is a new best score
            if (sessionScore <= oldBestScore) {
                Log.d(TAG, "💭 Score $sessionScore not better than best $oldBestScore, no ranking update")
                return@withContext null
            }

            // Get global stats to calculate percentile
            val globalStats = getGlobalStats(puzzleType, difficulty)
            val newPercentile = calculatePercentile(sessionScore, globalStats)

            // Update user ranking
            updateUserRanking(userId, puzzleType, difficulty, sessionScore, newPercentile)

            // Update global stats with new score
            updateGlobalStats(puzzleType, difficulty, sessionScore)

            val percentileGain = newPercentile - oldPercentile

            Log.d(TAG, "📈 Ranking update: $oldPercentile% -> $newPercentile% (+$percentileGain)")

            RankingImprovement(
                oldPercentile = oldPercentile,
                newPercentile = newPercentile,
                percentileGain = percentileGain,
                rankingMessage = generateRankingMessage(oldPercentile, newPercentile, percentileGain),
                showAnimation = percentileGain >= 5 // Show animation for 5%+ improvement
            )

        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to submit score and get improvement", e)
            null
        }
    }

    /**
     * Get competitive insights for current user performance
     */
    suspend fun getCompetitiveInsight(
        userId: String,
        puzzleType: String,
        difficulty: String
    ): CompetitiveInsight? = withContext(Dispatchers.IO) {
        try {
            val userRanking = getUserRanking(userId, puzzleType, difficulty)
            val globalStats = getGlobalStats(puzzleType, difficulty)

            if (userRanking == null || globalStats == null) {
                return@withContext null
            }

            val currentPercentile = userRanking.currentPercentile
            val nextPercentileTarget = getNextPercentileTarget(currentPercentile)
            val scoreToNextPercentile = getScoreForPercentile(nextPercentileTarget, globalStats)

            CompetitiveInsight(
                currentPercentile = currentPercentile,
                totalPlayers = globalStats.totalPlayers,
                scoreToNextPercentile = scoreToNextPercentile?.let { it - userRanking.bestScore }?.takeIf { it > 0 },
                nextPercentileTarget = nextPercentileTarget,
                competitiveMessage = generateCompetitiveMessage(currentPercentile, globalStats.totalPlayers),
                performanceLevel = getPerformanceLevel(currentPercentile)
            )

        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to get competitive insight", e)
            null
        }
    }

    /**
     * Get user's current ranking data
     */
    private suspend fun getUserRanking(
        userId: String,
        puzzleType: String,
        difficulty: String
    ): UserRankingData? = withContext(Dispatchers.IO) {
        try {
            val docId = "${userId}_${puzzleType}_${difficulty}"
            val doc = firestore.collection(COLLECTION_USER_RANKINGS)
                .document(docId)
                .get()
                .await()

            if (doc.exists()) {
                doc.toObject(UserRankingData::class.java)
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to get user ranking", e)
            null
        }
    }

    /**
     * Update user's ranking data
     */
    private suspend fun updateUserRanking(
        userId: String,
        puzzleType: String,
        difficulty: String,
        newBestScore: Int,
        newPercentile: Int
    ) = withContext(Dispatchers.IO) {
        try {
            val docId = "${userId}_${puzzleType}_${difficulty}"
            val rankingData = UserRankingData(
                userId = userId,
                puzzleType = puzzleType,
                difficulty = difficulty,
                bestScore = newBestScore,
                currentPercentile = newPercentile,
                lastUpdated = System.currentTimeMillis()
            )

            firestore.collection(COLLECTION_USER_RANKINGS)
                .document(docId)
                .set(rankingData)
                .await()

            Log.d(TAG, "✅ Updated user ranking: $newPercentile percentile")

        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to update user ranking", e)
        }
    }

    /**
     * Get global statistics for puzzle type/difficulty
     */
    private suspend fun getGlobalStats(
        puzzleType: String,
        difficulty: String
    ): GlobalPuzzleStats? = withContext(Dispatchers.IO) {
        try {
            val docId = "${puzzleType}_${difficulty}"
            val doc = firestore.collection(COLLECTION_GLOBAL_STATS)
                .document(docId)
                .get()
                .await()

            if (doc.exists()) {
                doc.toObject(GlobalPuzzleStats::class.java)
            } else {
                // Create initial stats if none exist
                createInitialGlobalStats(puzzleType, difficulty)
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to get global stats", e)
            null
        }
    }

    /**
     * Update global statistics with new score
     */
    private suspend fun updateGlobalStats(
        puzzleType: String,
        difficulty: String,
        newScore: Int
    ) = withContext(Dispatchers.IO) {
        try {
            val docId = "${puzzleType}_${difficulty}"
            val docRef = firestore.collection(COLLECTION_GLOBAL_STATS).document(docId)

            firestore.runTransaction { transaction ->
                val snapshot = transaction.get(docRef)
                val currentStats = if (snapshot.exists()) {
                    snapshot.toObject(GlobalPuzzleStats::class.java) ?: createDefaultGlobalStats(puzzleType, difficulty)
                } else {
                    createDefaultGlobalStats(puzzleType, difficulty)
                }

                // Update min/max scores
                val newMinScore = minOf(currentStats.minScore.takeIf { it > 0 } ?: newScore, newScore)
                val newMaxScore = maxOf(currentStats.maxScore, newScore)
                val newTotalPlayers = currentStats.totalPlayers + 1

                // Recalculate percentile breakpoints (simplified - in production you'd want more sophisticated calculation)
                val scoreRange = newMaxScore - newMinScore
                val newPercentileBreakpoints = mapOf(
                    "10" to newMinScore + (scoreRange * 0.1).toInt(),
                    "25" to newMinScore + (scoreRange * 0.25).toInt(),
                    "50" to newMinScore + (scoreRange * 0.5).toInt(),
                    "75" to newMinScore + (scoreRange * 0.75).toInt(),
                    "90" to newMinScore + (scoreRange * 0.9).toInt(),
                    "95" to newMinScore + (scoreRange * 0.95).toInt()
                )

                val updatedStats = currentStats.copy(
                    minScore = newMinScore,
                    maxScore = newMaxScore,
                    totalPlayers = newTotalPlayers,
                    percentileBreakpoints = newPercentileBreakpoints,
                    lastUpdated = System.currentTimeMillis()
                )

                transaction.set(docRef, updatedStats)
            }.await()

            Log.d(TAG, "✅ Updated global stats with score: $newScore")

        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to update global stats", e)
        }
    }

    /**
     * Calculate percentile based on score and global stats
     */
    private fun calculatePercentile(score: Int, globalStats: GlobalPuzzleStats?): Int {
        if (globalStats == null) return 50 // Default to median

        val breakpoints = globalStats.percentileBreakpoints

        return when {
            score >= breakpoints["95"] ?: Int.MAX_VALUE -> 95 + ((score - (breakpoints["95"] ?: 0)) * 5 / maxOf(1, globalStats.maxScore - (breakpoints["95"] ?: 0))).coerceAtMost(5)
            score >= breakpoints["90"] ?: Int.MAX_VALUE -> 90 + ((score - (breakpoints["90"] ?: 0)) * 5 / maxOf(1, (breakpoints["95"] ?: 0) - (breakpoints["90"] ?: 0))).coerceAtMost(5)
            score >= breakpoints["75"] ?: Int.MAX_VALUE -> 75 + ((score - (breakpoints["75"] ?: 0)) * 15 / maxOf(1, (breakpoints["90"] ?: 0) - (breakpoints["75"] ?: 0))).coerceAtMost(15)
            score >= breakpoints["50"] ?: Int.MAX_VALUE -> 50 + ((score - (breakpoints["50"] ?: 0)) * 25 / maxOf(1, (breakpoints["75"] ?: 0) - (breakpoints["50"] ?: 0))).coerceAtMost(25)
            score >= breakpoints["25"] ?: Int.MAX_VALUE -> 25 + ((score - (breakpoints["25"] ?: 0)) * 25 / maxOf(1, (breakpoints["50"] ?: 0) - (breakpoints["25"] ?: 0))).coerceAtMost(25)
            score >= breakpoints["10"] ?: Int.MAX_VALUE -> 10 + ((score - (breakpoints["10"] ?: 0)) * 15 / maxOf(1, (breakpoints["25"] ?: 0) - (breakpoints["10"] ?: 0))).coerceAtMost(15)
            else -> maxOf(1, (score - globalStats.minScore) * 10 / maxOf(1, (breakpoints["10"] ?: 0) - globalStats.minScore))
        }.coerceIn(1, 100)
    }

    /**
     * Get score needed for specific percentile
     */
    private fun getScoreForPercentile(percentile: Int, globalStats: GlobalPuzzleStats?): Int? {
        if (globalStats == null) return null

        val breakpoints = globalStats.percentileBreakpoints

        return when {
            percentile >= 95 -> breakpoints["95"]
            percentile >= 90 -> breakpoints["90"]
            percentile >= 75 -> breakpoints["75"]
            percentile >= 50 -> breakpoints["50"]
            percentile >= 25 -> breakpoints["25"]
            percentile >= 10 -> breakpoints["10"]
            else -> globalStats.minScore
        }
    }

    /**
     * Helper functions
     */
    private fun getNextPercentileTarget(currentPercentile: Int): Int {
        return when {
            currentPercentile < 10 -> 10
            currentPercentile < 25 -> 25
            currentPercentile < 50 -> 50
            currentPercentile < 75 -> 75
            currentPercentile < 90 -> 90
            currentPercentile < 95 -> 95
            else -> 99
        }
    }

    private fun getPerformanceLevel(percentile: Int): PerformanceLevel {
        return when {
            percentile >= 95 -> PerformanceLevel.ELITE
            percentile >= 85 -> PerformanceLevel.EXPERT
            percentile >= 70 -> PerformanceLevel.ADVANCED
            percentile >= 50 -> PerformanceLevel.SKILLED
            percentile >= 25 -> PerformanceLevel.DEVELOPING
            else -> PerformanceLevel.BEGINNER
        }
    }

    private fun generateRankingMessage(oldPercentile: Int, newPercentile: Int, gain: Int): String {
        return when {
            gain >= 20 -> "🚀 Incredible jump! You're now in the top ${100 - newPercentile}% of players!"
            gain >= 10 -> "⭐ Amazing improvement! You moved up $gain percentile points!"
            gain >= 5 -> "📈 Nice progress! You're climbing the leaderboard!"
            gain > 0 -> "👍 Small but steady improvement! Keep it up!"
            else -> "🎯 You maintained your ranking at ${newPercentile}th percentile!"
        }
    }

    private fun generateCompetitiveMessage(percentile: Int, totalPlayers: Int): String {
        val playersBelow = ((percentile / 100.0) * totalPlayers).toInt()

        return when {
            percentile >= 95 -> "🏆 Elite performer! You're better than ${playersBelow} out of $totalPlayers players!"
            percentile >= 85 -> "👑 Expert level! You outperform ${playersBelow} players!"
            percentile >= 70 -> "⭐ Advanced skill! You're ahead of ${playersBelow} players!"
            percentile >= 50 -> "📈 Above average! You beat ${playersBelow} players!"
            percentile >= 25 -> "🌱 Room to grow! ${totalPlayers - playersBelow} players to catch up to!"
            else -> "🎯 Just getting started! Lots of room for improvement!"
        }
    }

    private fun createInitialGlobalStats(puzzleType: String, difficulty: String): GlobalPuzzleStats {
        return createDefaultGlobalStats(puzzleType, difficulty).also { stats ->
            // Save to Firebase
            val docId = "${puzzleType}_${difficulty}"
            firestore.collection(COLLECTION_GLOBAL_STATS)
                .document(docId)
                .set(stats)
        }
    }

    private fun createDefaultGlobalStats(puzzleType: String, difficulty: String): GlobalPuzzleStats {
        // Default stats based on puzzle type and difficulty
        val (minScore, maxScore) = when (difficulty.lowercase()) {
            "easy" -> 50 to 500
            "medium" -> 100 to 800
            "hard" -> 200 to 1200
            else -> 100 to 600
        }

        val scoreRange = maxScore - minScore

        return GlobalPuzzleStats(
            puzzleType = puzzleType,
            difficulty = difficulty,
            minScore = minScore,
            maxScore = maxScore,
            percentileBreakpoints = mapOf(
                "10" to minScore + (scoreRange * 0.1).toInt(),
                "25" to minScore + (scoreRange * 0.25).toInt(),
                "50" to minScore + (scoreRange * 0.5).toInt(),
                "75" to minScore + (scoreRange * 0.75).toInt(),
                "90" to minScore + (scoreRange * 0.9).toInt(),
                "95" to minScore + (scoreRange * 0.95).toInt()
            ),
            totalPlayers = 1,
            lastUpdated = System.currentTimeMillis()
        )
    }
}