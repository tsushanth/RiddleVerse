package com.kreativekoala.riddleverse

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import android.util.Log
import java.util.*
import kotlin.collections.HashMap

data class PuzzleAnalytics(
    val puzzleType: String,
    val totalStarts: Int = 0,
    val totalCompletions: Int = 0,
    val totalPlayers: Int = 0,
    val avgScore: Double = 0.0,
    val recentActivity: Int = 0, // Last 7 days
    val lastPlayed: Long = 0
) {
    val successRate: Double
        get() = if (totalStarts > 0) (totalCompletions.toDouble() / totalStarts) * 100 else 0.0

    fun toFeaturedPuzzleType(): FeaturedPuzzleType {
        val reason = when {
            totalStarts >= 100 -> "Most Popular"
            successRate >= 90 -> "High Success Rate"
            recentActivity >= 10 -> "Recently Played"
            avgScore >= 25 -> "High Scoring"
            else -> "Try This"
        }

        return FeaturedPuzzleType(
            puzzleType = puzzleType,
            playCount = totalStarts,
            successRatePercent = successRate,
            avgScore = avgScore,
            recentPlays = recentActivity,
            featuredReason = reason
        )
    }
}

// Data class matching your iOS Firestore structure
data class FirebaseAnalyticsEvent(
    val event_name: String = "",
    val puzzle_type: String = "",
    val user_id: String = "",
    val timestamp: Long = 0L,
    val score: Double = 0.0,
    val difficulty: String = "",
    val is_correct: Boolean = false,
    val time_spent_seconds: Double = 0.0,
    val question_index: Int = 0,
    val total_questions: Int = 0
)




// 🚀 MEMORY-OPTIMIZED Firebase Analytics Service
class PuzzleAnalyticsService {
    private val firestore by lazy {
        try {
            FirebaseFirestore.getInstance()
        } catch (e: Exception) {
            Log.e(TAG, "Error getting Firestore instance: ${e.message}")
            FirebaseFirestore.getInstance()
        }
    }

    // 🔥 CACHING: Prevent repeated expensive queries
    private var cachedFeaturedPuzzles: List<FeaturedPuzzleType>? = null
    private var lastCacheTime: Long = 0
    private val cacheValidityMs = 5 * 60 * 1000L // 5 minutes cache

    companion object {
        private const val ANALYTICS_COLLECTION = "puzzle_analytics"
        private const val TAG = "PuzzleAnalytics"

        // 🚀 CRITICAL: Reduced limits to prevent OOM
        private const val MAX_DOCUMENTS_PER_QUERY = 200L // Down from 10,000!
        private const val MAX_RECENT_DOCUMENTS = 100L   // For recent activity
        private const val MEMORY_CHECK_THRESHOLD = 75  // Percentage
    }

    /**
     * 🚀 MEMORY-SAFE: Fetch featured puzzle types with caching and limits
     */
    suspend fun fetchFeaturedPuzzleTypes(): List<FeaturedPuzzleType> = withContext(Dispatchers.IO) {
        try {
            // 🔥 CACHING: Return cached data if still valid
            val currentTime = System.currentTimeMillis()
            if (cachedFeaturedPuzzles != null && (currentTime - lastCacheTime) < cacheValidityMs) {
                Log.d(TAG, "📋 Returning cached featured puzzles")
                return@withContext cachedFeaturedPuzzles!!
            }

            // 🚀 MEMORY CHECK: Abort if memory is too low
            if (!isMemorySafe()) {
                Log.w(TAG, "⚠️ Memory usage too high - using fallback data")
                return@withContext getFallbackFeaturedPuzzles()
            }

            Log.d(TAG, "🔍 Fetching puzzle analytics from Firebase Firestore...")

            // 🔥 SMALLER TIME WINDOW: Reduce data volume
            val calendar = Calendar.getInstance()
            val endTime = calendar.timeInMillis
            calendar.add(Calendar.DAY_OF_MONTH, -14) // Reduced from 30 to 14 days
            val startTime = calendar.timeInMillis
            calendar.add(Calendar.DAY_OF_MONTH, 7) // Last 7 days for recent activity
            val recentStartTime = calendar.timeInMillis

            // 🚀 BATCHED PROCESSING: Process in smaller chunks
            val puzzleStats = fetchPuzzleStatisticsBatched(startTime, endTime)
            val recentActivity = fetchRecentActivityBatched(recentStartTime, endTime)

            // Create featured list
            val featuredList = createFeaturedList(puzzleStats, recentActivity)

            // 🔥 CACHE RESULT: Store for future requests
            cachedFeaturedPuzzles = featuredList
            lastCacheTime = currentTime

            Log.d(TAG, "✅ Successfully fetched ${featuredList.size} featured puzzle types from Firebase")
            featuredList

        } catch (e: OutOfMemoryError) {
            Log.e(TAG, "💥 OOM Error in fetchFeaturedPuzzleTypes - using fallback", e)
            clearCacheAndGC()
            getFallbackFeaturedPuzzles()
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to fetch featured puzzle types from Firebase", e)
            getFallbackFeaturedPuzzles()
        }
    }

    /**
     * 🚀 MEMORY-SAFE: Check available memory before processing
     */
    private fun isMemorySafe(): Boolean {
        val runtime = Runtime.getRuntime()
        val usedMemory = runtime.totalMemory() - runtime.freeMemory()
        val maxMemory = runtime.maxMemory()
        val percentUsed = (usedMemory * 100) / maxMemory

        Log.d(TAG, "📊 Memory usage: ${usedMemory / 1024 / 1024}MB / ${maxMemory / 1024 / 1024}MB ($percentUsed%)")

        return percentUsed < MEMORY_CHECK_THRESHOLD
    }

    /**
     * 🚀 BATCHED PROCESSING: Query Firebase in smaller, safer chunks
     */
    private suspend fun fetchPuzzleStatisticsBatched(
        startTime: Long,
        endTime: Long
    ): Map<String, PuzzleAnalytics> {
        return try {
            val puzzleStats = mutableMapOf<String, PuzzleAnalytics>()

            // 🔥 PROCESS IN SMALL BATCHES: Prevent OOM
            val eventTypes = listOf("puzzle_start", "puzzle_complete", "category_clicked")

            for (eventType in eventTypes) {
                // Memory check before each batch
                if (!isMemorySafe()) {
                    Log.w(TAG, "⚠️ Memory limit reached during batched processing")
                    break
                }

                val batchQuery = firestore.collection(ANALYTICS_COLLECTION)
                    .whereGreaterThanOrEqualTo("timestamp", startTime)
                    .whereLessThanOrEqualTo("timestamp", endTime)
                    .whereEqualTo("event_name", eventType) // Single event type per query
                    .orderBy("timestamp", Query.Direction.DESCENDING)
                    .limit(MAX_DOCUMENTS_PER_QUERY) // Much smaller limit

                val snapshot = batchQuery.get().await()

                Log.d(TAG, "📊 Processing batch: $eventType (${snapshot.size()} events)")

                for (document in snapshot.documents) {
                    val puzzleType = document.getString("puzzle_type") ?: continue
                    val userId = document.getString("user_id") ?: "anonymous"
                    val score = document.getDouble("score") ?: 0.0
                    val timestamp = document.getLong("timestamp") ?: 0L

                    val current = puzzleStats.getOrPut(puzzleType) {
                        PuzzleAnalytics(puzzleType = puzzleType)
                    }

                    // Update stats based on event type
                    puzzleStats[puzzleType] = when (eventType) {
                        "puzzle_start", "category_clicked" -> current.copy(
                            totalStarts = current.totalStarts + 1,
                            totalPlayers = current.totalPlayers + 1,
                            lastPlayed = maxOf(current.lastPlayed, timestamp)
                        )
                        "puzzle_complete" -> current.copy(
                            totalCompletions = current.totalCompletions + 1,
                            avgScore = if (current.totalCompletions == 0) score else (current.avgScore + score) / 2,
                            totalPlayers = current.totalPlayers + 1
                        )
                        else -> current
                    }
                }

                // 🔥 FORCE GC BETWEEN BATCHES: Prevent memory buildup
                System.gc()
            }

            Log.d(TAG, "📊 Processed Firebase events for ${puzzleStats.size} puzzle types")
            puzzleStats

        } catch (e: OutOfMemoryError) {
            Log.e(TAG, "💥 OOM Error in fetchPuzzleStatisticsBatched", e)
            clearCacheAndGC()
            emptyMap()
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to fetch puzzle statistics from Firebase", e)
            emptyMap()
        }
    }

    /**
     * 🚀 SMALLER RECENT ACTIVITY QUERY: Reduced memory footprint
     */
    private suspend fun fetchRecentActivityBatched(recentThreshold: Long, endTime: Long): Map<String, Int> {
        return try {
            val recentQuery = firestore.collection(ANALYTICS_COLLECTION)
                .whereGreaterThanOrEqualTo("timestamp", recentThreshold)
                .whereLessThanOrEqualTo("timestamp", endTime)
                .whereIn("event_name", listOf("puzzle_start", "category_clicked"))
                .limit(MAX_RECENT_DOCUMENTS) // Much smaller limit for recent activity

            val snapshot = recentQuery.get().await()
            val recentActivity = mutableMapOf<String, Int>()

            Log.d(TAG, "📈 Processing recent activity: ${snapshot.size()} events")

            for (document in snapshot.documents) {
                val puzzleType = document.getString("puzzle_type") ?: continue
                recentActivity[puzzleType] = recentActivity.getOrDefault(puzzleType, 0) + 1
            }

            Log.d(TAG, "📈 Recent activity from Firebase: ${recentActivity.size} active puzzle types")
            recentActivity

        } catch (e: OutOfMemoryError) {
            Log.e(TAG, "💥 OOM Error in fetchRecentActivityBatched", e)
            clearCacheAndGC()
            emptyMap()
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to fetch recent activity from Firebase", e)
            emptyMap()
        }
    }

    /**
     * 🔥 MEMORY CLEANUP: Clear cache and force garbage collection
     */
    private fun clearCacheAndGC() {
        cachedFeaturedPuzzles = null
        lastCacheTime = 0
        System.gc()
        System.runFinalization()

        Log.d(TAG, "🧹 Cleared cache and forced garbage collection")
    }

    /**
     * Create featured list by combining Firebase stats and applying business logic
     */
    private fun createFeaturedList(
        puzzleStats: Map<String, PuzzleAnalytics>,
        recentActivity: Map<String, Int>
    ): List<FeaturedPuzzleType> {

        // Merge recent activity into puzzle stats
        val enhancedStats = puzzleStats.toMutableMap()
        for ((puzzleType, recentCount) in recentActivity) {
            val current = enhancedStats.getOrPut(puzzleType) {
                PuzzleAnalytics(puzzleType = puzzleType)
            }
            enhancedStats[puzzleType] = current.copy(recentActivity = recentCount)
        }

        // Convert to featured puzzle types and apply filtering/sorting
        return enhancedStats.values
            .filter { it.totalStarts >= 2 } // Lower threshold for Firebase data
            .sortedWith(compareByDescending<PuzzleAnalytics> {
                // Custom scoring algorithm
                (it.totalStarts * 0.4) + (it.successRate * 0.3) + (it.recentActivity * 0.3)
            })
            .take(5) // Top 5 featured
            .map { it.toFeaturedPuzzleType() }
    }

    /**
     * Fallback data when Firebase query fails or returns insufficient data
     */
    private fun getFallbackFeaturedPuzzles(): List<FeaturedPuzzleType> {
        Log.d(TAG, "📊 Using fallback featured puzzles data")
        return listOf(
            FeaturedPuzzleType(
                puzzleType = "math",
                playCount = 150,
                successRatePercent = 85.0,
                avgScore = 28.5,
                recentPlays = 15,
                featuredReason = "Most Popular"
            ),
            FeaturedPuzzleType(
                puzzleType = "anagram",
                playCount = 89,
                successRatePercent = 92.0,
                avgScore = 24.2,
                recentPlays = 12,
                featuredReason = "High Success Rate"
            ),
            FeaturedPuzzleType(
                puzzleType = "connotationwords",
                playCount = 67,
                successRatePercent = 88.0,
                avgScore = 26.1,
                recentPlays = 8,
                featuredReason = "Recently Played"
            ),
            FeaturedPuzzleType(
                puzzleType = "trivia",
                playCount = 45,
                successRatePercent = 78.0,
                avgScore = 22.0,
                recentPlays = 5,
                featuredReason = "Try This"
            ),
            FeaturedPuzzleType(
                puzzleType = "memorysquares",
                playCount = 34,
                successRatePercent = 95.0,
                avgScore = 30.5,
                recentPlays = 7,
                featuredReason = "High Scoring"
            )
        )
    }

    /**
     * 🔥 PUBLIC METHOD: Clear cache manually when needed
     */
    fun clearCache() {
        clearCacheAndGC()
    }
}