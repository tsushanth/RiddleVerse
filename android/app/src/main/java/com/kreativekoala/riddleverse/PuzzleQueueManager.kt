package com.kreativekoala.riddleverse

import android.content.Context
import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.TimeUnit

data class QueueStats(
    val queueKey: String,
    var totalServed: Int = 0,
    var totalFetched: Int = 0,
    var lastServedAt: Long = 0L,
    var lastFetchedAt: Long = 0L,
    var totalFetchAttempts: Int = 0,
    var failedFetchAttempts: Int = 0,
    var createdAt: Long = System.currentTimeMillis()
) {
    fun recordPuzzleServed() {
        totalServed++
        lastServedAt = System.currentTimeMillis()
    }

    fun recordFetchAttempt(success: Boolean, puzzlesFetched: Int = 0) {
        totalFetchAttempts++
        lastFetchedAt = System.currentTimeMillis()

        if (success) {
            totalFetched += puzzlesFetched
        } else {
            failedFetchAttempts++
        }
    }
}

/**
 * Simplified Puzzle Queue Manager - Handles all puzzle fetching
 * - Fetches today's puzzles for user preferences on initialization
 * - All other puzzles are on-demand
 * - No availability tracking, filtering, or daily limits
 */
class PuzzleQueueManager private constructor(internal val context: Context) {
    companion object {
        @Volatile
        private var INSTANCE: PuzzleQueueManager? = null
        private const val TAG = "PuzzleQueueManager"
        private const val PREFS_NAME = "puzzle_queue"
        private const val LOW_QUEUE_THRESHOLD = 2 // Trigger refetch when queue has ≤2 puzzles

        fun getInstance(context: Context): PuzzleQueueManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: PuzzleQueueManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val gson = Gson()
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private val mediaCache by lazy { MediaCacheManager.getInstance(context) }

    private val lastServedPuzzles = ConcurrentHashMap<String, String>()

    // Simple queues - store puzzles per type+difficulty
    private val puzzleQueues = ConcurrentHashMap<String, ConcurrentLinkedQueue<QueuedPuzzle>>()
    private val queueStats = ConcurrentHashMap<String, QueueStats>()
    private val currentlyFetching = ConcurrentHashMap<String, Job>()

    // Track which puzzle types have been initialized with today's puzzles
    private val initializedTypes = ConcurrentHashMap.newKeySet<String>()
    private val offlinePuzzleTypes = ConcurrentHashMap.newKeySet<String>()

    // Listeners for UI updates
    private val availabilityListeners = mutableSetOf<(String, Boolean) -> Unit>()

    private val queueScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    init {
        loadQueuesFromDisk()
        initializeOfflinePuzzles()
    }

    // Add this new method
    private suspend fun processAndCachePuzzleMedia(puzzle: QueuedPuzzle): QueuedPuzzle {
        return withContext(Dispatchers.IO) {
            try {
                // Extract media URLs from puzzle content
                val mediaUrls = mediaCache.extractMediaUrlsFromPuzzle(puzzle.toPuzzle())

                if (mediaUrls.isNotEmpty()) {
                    Log.d(TAG, "Preloading ${mediaUrls.size} media files for puzzle ${puzzle.puzzleId}")

                    // Create updated puzzle with media URLs tracked
                    val updatedPuzzle = puzzle.copy(mediaUrls = mediaUrls)

                    // Preload the media (downloads and caches)
                    mediaCache.preloadPuzzleMedia(updatedPuzzle)

                    return@withContext updatedPuzzle
                }

                puzzle
            } catch (e: Exception) {
                Log.e(TAG, "Error processing media for puzzle ${puzzle.puzzleId}", e)
                puzzle // Return original if media processing fails
            }
        }
    }

    /**
     * Initialize offline puzzle types as immediately available
     */
    private fun initializeOfflinePuzzles() {
        val allPuzzleTypes = QuizCategories.getAllAvailablePuzzleTypes()

        allPuzzleTypes.forEach { puzzleType ->
            if (isOfflinePuzzleType(puzzleType)) {
                offlinePuzzleTypes.add(puzzleType)
                initializedTypes.add(puzzleType)

                // Create offline puzzle data
                val offlinePuzzles = createOfflinePuzzlesForType(puzzleType, 5)
                val queue = ConcurrentLinkedQueue<QueuedPuzzle>()
                queue.addAll(offlinePuzzles)
                puzzleQueues["${puzzleType}_medium"] = queue

                // Notify listeners
                notifyAvailabilityChange(puzzleType, true)

                Log.d(TAG, "Initialized offline puzzle type: $puzzleType")
            }
        }
    }

    /**
     * Fetch today's puzzles for For You tab - uses new daily cache API
     */
    fun fetchTodaysPuzzlesForForYou(
        userFavorites: List<String>,
        trending: List<String>,
        recommended: List<String>
    ) {
        val allNeededTypes = (userFavorites + trending + recommended).distinct()
            .filter { !isOfflinePuzzleType(it) } // Skip offline types

        Log.d(TAG, "Fetching today's puzzles for: $allNeededTypes")

        allNeededTypes.forEach { puzzleType ->
            if (!initializedTypes.contains(puzzleType)) {
                fetchTodaysPuzzles(puzzleType)
            }
        }
    }

    /**
     * Fetch today's puzzles using new daily cache API
     */
    private fun fetchTodaysPuzzles(puzzleType: String, difficulty: String = "medium", count: Int = 5) {
        val queueKey = "${puzzleType}_${difficulty.lowercase()}"

        if (currentlyFetching.containsKey(queueKey)) {
            Log.d(TAG, "Already fetching today's puzzles for $puzzleType")
            return
        }

        val fetchJob = queueScope.launch {
            try {
                Log.d(TAG, "Fetching today's puzzles for $puzzleType")

                val url = "https://puzzleverseai.com/api/daily-puzzle-cache/todays-puzzles/$puzzleType?difficulty=${difficulty}&count=$count"
                val request = Request.Builder().url(url).build()

                val response = client.newCall(request).execute()
                response.use {
                    if (it.isSuccessful) {
                        val responseBody = it.body?.string()
                        if (responseBody != null) {
                            val puzzles = parseTodaysPuzzlesResponse(responseBody, puzzleType)
                            val cachedPuzzles = puzzles.map { puzzle ->
                                async { processAndCachePuzzleMedia(puzzle) }
                            }.awaitAll()


                            if (cachedPuzzles.isNotEmpty()) {
                                val queue = puzzleQueues.getOrPut(queueKey) { ConcurrentLinkedQueue() }
                                queue.clear() // Clear any existing puzzles
                                queue.addAll(cachedPuzzles)

                                // Mark as initialized
                                initializedTypes.add(puzzleType)

                                // Update stats
                                val stats = queueStats.getOrPut(queueKey) { QueueStats(queueKey) }
                                stats.recordFetchAttempt(true, cachedPuzzles.size)

                                Log.d(TAG, "Successfully fetched ${cachedPuzzles.size} today's puzzles for $puzzleType")

                                // Notify UI
                                notifyAvailabilityChange(puzzleType, true)

                                saveQueuesToDisk()
                            } else {
                                Log.w(TAG, "No today's puzzles returned for $puzzleType")
                                val stats = queueStats.getOrPut(queueKey) { QueueStats(queueKey) }
                                stats.recordFetchAttempt(false)
                            }
                        }
                    } else {
                        Log.w(TAG, "Failed to fetch today's puzzles for $puzzleType: ${it.code}")
                        val stats = queueStats.getOrPut(queueKey) { QueueStats(queueKey) }
                        stats.recordFetchAttempt(false)
                    }
                }

            } catch (e: Exception) {
                Log.e(TAG, "Error fetching today's puzzles for $puzzleType", e)
                val stats = queueStats.getOrPut(queueKey) { QueueStats(queueKey) }
                stats.recordFetchAttempt(false)
            } finally {
                currentlyFetching.remove(queueKey)
            }
        }

        currentlyFetching[queueKey] = fetchJob
    }

    /**
     * Parse today's puzzles API response
     */
    private fun parseTodaysPuzzlesResponse(responseBody: String, puzzleType: String): List<QueuedPuzzle> {
        return try {
            val json = JSONObject(responseBody)
            if (!json.optBoolean("success", false)) {
                return emptyList()
            }

            val puzzlesArray = json.getJSONArray("puzzles")
            val puzzles = mutableListOf<QueuedPuzzle>()

            for (i in 0 until puzzlesArray.length()) {
                val puzzleObj = puzzlesArray.getJSONObject(i)

                val options = mutableListOf<String>()
                val optionsArray = puzzleObj.optJSONArray("options")
                if (optionsArray != null) {
                    for (j in 0 until optionsArray.length()) {
                        options.add(optionsArray.optString(j))
                    }
                }

                val queuedPuzzle = QueuedPuzzle(
                    puzzleId = puzzleObj.optString("id", "today_${System.currentTimeMillis()}"),
                    puzzleType = puzzleType,
                    difficulty = puzzleObj.optString("difficulty", "medium"),
                    question = puzzleObj.optString("question", ""),
                    answer = puzzleObj.optString("answer", ""),
                    hint = "", // Today's puzzles don't include hints
                    options = options,
                    queuedAt = System.currentTimeMillis(),
                    mediaUrls = emptyList()
                )
                puzzles.add(queuedPuzzle)
            }

            puzzles
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse today's puzzles response for $puzzleType", e)
            emptyList()
        }
    }

    /**
     * Get next puzzle for immediate play
     */
    suspend fun getNextPuzzleForPlay(puzzleType: String, difficulty: String = "medium"): QueuedPuzzle? {
        return withContext(Dispatchers.IO) {
            val queueKey = "${puzzleType}_${difficulty.lowercase()}"
            val queue = puzzleQueues[queueKey]

            if (queue != null && queue.isNotEmpty()) {
                val puzzle = queue.poll()
                if (puzzle != null) {
                    // Clean up previous puzzle's media
                    val previousPuzzleId = lastServedPuzzles.put(queueKey, puzzle.puzzleId)
                    if (previousPuzzleId != null) {
                        queueScope.launch {
                            mediaCache.cleanupPuzzleMedia(previousPuzzleId)
                        }
                    }

                    val stats = queueStats.getOrPut(queueKey) { QueueStats(queueKey) }
                    stats.recordPuzzleServed()

                    // Check if queue is running low and refetch
                    if (queue.size <= LOW_QUEUE_THRESHOLD && !isOfflinePuzzleType(puzzleType)) {
                        Log.d(TAG, "Queue low for $puzzleType (${queue.size} remaining), triggering refetch")
                        refetchPuzzles(puzzleType, difficulty)
                    }

                    saveQueuesToDisk()
                    return@withContext puzzle
                }
            }

            // No puzzles in queue - try immediate fetch for server types
            if (!isOfflinePuzzleType(puzzleType)) {
                Log.d(TAG, "No cached puzzles for $puzzleType, fetching immediately...")
                val immediatePuzzle = fetchSinglePuzzleBlocking(puzzleType, difficulty)
                if (immediatePuzzle != null) {
                    // Clean up previous puzzle's media for immediate fetches too
                    val previousPuzzleId = lastServedPuzzles.put(queueKey, immediatePuzzle.puzzleId)
                    if (previousPuzzleId != null) {
                        queueScope.launch {
                            mediaCache.cleanupPuzzleMedia(previousPuzzleId)
                        }
                    }

                    // Start background batch fetch for more
                    refetchPuzzles(puzzleType, difficulty)
                }
                return@withContext immediatePuzzle
            }

            null
        }
    }

    /**
     * Refetch puzzles when queue is low - tries batch first, then single+async batch
     */
    private fun refetchPuzzles(puzzleType: String, difficulty: String) {
        val queueKey = "${puzzleType}_${difficulty.lowercase()}"

        if (currentlyFetching.containsKey(queueKey)) {
            Log.d(TAG, "Already refetching $puzzleType")
            return
        }

        val refetchJob = queueScope.launch {
            try {
                Log.d(TAG, "Refetching puzzles for $puzzleType")

                // Try batch fetch first (synchronous)
                val batchPuzzles = fetchBatchPuzzles(puzzleType, difficulty, 5)

                if (batchPuzzles.isNotEmpty()) {
                    // Success with batch
                    val queue = puzzleQueues.getOrPut(queueKey) { ConcurrentLinkedQueue() }
                    queue.addAll(batchPuzzles)

                    val stats = queueStats.getOrPut(queueKey) { QueueStats(queueKey) }
                    stats.recordFetchAttempt(true, batchPuzzles.size)

                    Log.d(TAG, "Batch refetch successful for $puzzleType: ${batchPuzzles.size} puzzles")
                    saveQueuesToDisk()
                } else {
                    // Batch failed, try single fetch (synchronous) + async batch
                    Log.w(TAG, "Batch fetch failed for $puzzleType, trying single fetch")

                    val singlePuzzle = fetchSinglePuzzleBlocking(puzzleType, difficulty)
                    if (singlePuzzle != null) {
                        val queue = puzzleQueues.getOrPut(queueKey) { ConcurrentLinkedQueue() }
                        queue.offer(singlePuzzle)

                        Log.d(TAG, "Single fetch successful for $puzzleType, starting async batch fetch")

                        // Start async batch fetch for the rest
                        launch {
                            delay(1000) // Small delay before retry
                            val asyncBatchPuzzles = fetchBatchPuzzles(puzzleType, difficulty, 4)
                            if (asyncBatchPuzzles.isNotEmpty()) {
                                queue.addAll(asyncBatchPuzzles)
                                Log.d(TAG, "Async batch fetch successful for $puzzleType: ${asyncBatchPuzzles.size} puzzles")
                                saveQueuesToDisk()
                            }
                        }
                    } else {
                        Log.e(TAG, "Both batch and single fetch failed for $puzzleType")
                        val stats = queueStats.getOrPut(queueKey) { QueueStats(queueKey) }
                        stats.recordFetchAttempt(false)
                    }
                }

            } catch (e: Exception) {
                Log.e(TAG, "Error refetching puzzles for $puzzleType", e)
                val stats = queueStats.getOrPut(queueKey) { QueueStats(queueKey) }
                stats.recordFetchAttempt(false)
            } finally {
                currentlyFetching.remove(queueKey)
            }
        }

        currentlyFetching[queueKey] = refetchJob
    }

    /**
     * Fetch batch puzzles (5 at a time)
     */
    private suspend fun fetchBatchPuzzles(puzzleType: String, difficulty: String, count: Int): List<QueuedPuzzle> {
        return withContext(Dispatchers.IO) {
            try {
                val userId = FirebaseAuth.getInstance().currentUser?.uid ?: ""

                val requestData = JSONObject().apply {
                    put("userId", userId)
                    put("puzzleTypes", JSONArray(listOf(puzzleType)))
                    put("puzzlesPerType", count)
                    put("excludeCompleted", false)
                    put("difficulty", difficulty)
                }

                val requestBody = requestData.toString().toRequestBody("application/json".toMediaType())
                val request = Request.Builder()
                    .url("https://puzzleverseai.com/batch-fetch-puzzles")
                    .post(requestBody)
                    .addHeader("Content-Type", "application/json")
                    .build()

                val response = client.newCall(request).execute()
                response.use {
                    if (it.isSuccessful) {
                        val responseBody = it.body?.string()
                        if (responseBody != null) {
                            val puzzles = processBatchResponse(responseBody)

                            // Process media for all puzzles in parallel
                            return@withContext puzzles.map { puzzle ->
                                async { processAndCachePuzzleMedia(puzzle) }
                            }.awaitAll()
                        }
                    }
                }

                emptyList()
            } catch (e: Exception) {
                Log.e(TAG, "Error in batch fetch for $puzzleType", e)
                emptyList()
            }
        }
    }

    /**
     * Fetch single puzzle immediately (blocking)
     */
    private suspend fun fetchSinglePuzzleBlocking(puzzleType: String, difficulty: String): QueuedPuzzle? {
        return withContext(Dispatchers.IO) {
            try {
                val userId = FirebaseAuth.getInstance().currentUser?.uid ?: ""
                val userEmail = FirebaseAuth.getInstance().currentUser?.email ?: ""

                val url = "https://puzzleverseai.com/fetch-next-puzzle-ios/$puzzleType?userId=$userId&email=$userEmail&difficulty=$difficulty"
                val request = Request.Builder().url(url).build()

                val response = client.newCall(request).execute()
                response.use {
                    if (it.isSuccessful) {
                        val responseBody = it.body?.string()
                        if (responseBody != null) {
                            val json = JSONObject(responseBody)
                            if (json.optBoolean("success", false)) {
                                val puzzleData = json.getJSONObject("puzzleData")

                                if (puzzleData.optBoolean("regenerationBlocked", false)) {
                                    Log.w(TAG, "Regeneration blocked for $puzzleType")
                                    return@withContext null
                                }

                                val queuedPuzzle = convertServerResponseToQueuedPuzzle(puzzleData, puzzleType, difficulty)
                                Log.d(TAG, "Successfully fetched single puzzle: ${queuedPuzzle.puzzleId}")

                                // Process and cache media for the single puzzle
                                return@withContext processAndCachePuzzleMedia(queuedPuzzle)
                            }
                        }
                    }
                }

                null
            } catch (e: Exception) {
                Log.e(TAG, "Error fetching single puzzle for $puzzleType", e)
                null
            }
        }
    }

    /**
     * Check if puzzle type has been initialized (has puzzles available)
     */
    fun hasPuzzleData(puzzleType: String): Boolean {
        return initializedTypes.contains(puzzleType)
    }

    /**
     * Get available puzzle types
     */
    fun getAvailablePuzzleTypes(): Set<String> {
        return initializedTypes.toSet()
    }

    /**
     * Add listener for availability changes
     */
    fun addAvailabilityListener(listener: (String, Boolean) -> Unit) {
        availabilityListeners.add(listener)
    }

    fun removeAvailabilityListener(listener: (String, Boolean) -> Unit) {
        availabilityListeners.remove(listener)
    }

    private fun notifyAvailabilityChange(puzzleType: String, isAvailable: Boolean) {
        availabilityListeners.forEach { listener ->
            try {
                listener(puzzleType, isAvailable)
            } catch (e: Exception) {
                Log.e(TAG, "Error in availability listener", e)
            }
        }
    }

    /**
     * Create offline puzzles for a given type
     */
    private fun createOfflinePuzzlesForType(puzzleType: String, count: Int): List<QueuedPuzzle> {
        val puzzles = mutableListOf<QueuedPuzzle>()

        for (i in 1..count) {
            puzzles.add(QueuedPuzzle(
                puzzleId = "offline_${puzzleType}_$i",
                puzzleType = puzzleType,
                question = "Offline puzzle $i ready",
                answer = "Ready",
                hint = "This puzzle works offline",
                difficulty = "medium",
                options = listOf("Ready", "Available", "Offline"),
                queuedAt = System.currentTimeMillis()
            ))
        }

        return puzzles
    }

    private fun isOfflinePuzzleType(puzzleType: String): Boolean {
        return try {
            QuizCategories.isOfflinePuzzleType(puzzleType)
        } catch (e: Exception) {
            // Fallback to hardcoded list
            val offlineTypes = setOf(
                "uniqueobject", "discounts", "division", "contextswitch", "mathcrossword",
                "symmetry", "trainrouting", "pinballdeflector", "memorypreviouspair",
                "memoryprevioussingle", "geography_countries", "geography_cities",
                "colortextmatching", "conversion", "triangledotmemory", "numbersequence",
                "dualcard", "mathestimation", "mathtipping", "percentages", "numbersum",
                "symbolswipe", "colormatching", "imagevortex", "memorysquares",
                "mathexpression", "purchasing", "subtraction", "mathcomparison"
            )
            offlineTypes.contains(puzzleType)
        }
    }

    // Utility methods (kept from original implementation)
    private fun convertServerResponseToQueuedPuzzle(puzzleData: JSONObject, puzzleType: String, difficulty: String): QueuedPuzzle {
        val options = mutableListOf<String>()
        if (puzzleData.has("options") && !puzzleData.isNull("options")) {
            try {
                val optionsValue = puzzleData.get("options")
                when (optionsValue) {
                    is JSONArray -> {
                        for (i in 0 until optionsValue.length()) {
                            options.add(optionsValue.optString(i, ""))
                        }
                    }
                    is String -> {
                        if (optionsValue.trim().startsWith("[") && optionsValue.trim().endsWith("]")) {
                            if (optionsValue.trim() != "[]") {
                                try {
                                    val optionsArray = JSONArray(optionsValue)
                                    for (i in 0 until optionsArray.length()) {
                                        options.add(optionsArray.optString(i, ""))
                                    }
                                } catch (e: Exception) {
                                    Log.w(TAG, "Failed to parse options string: $optionsValue", e)
                                }
                            }
                        } else {
                            if (optionsValue.isNotEmpty()) {
                                options.add(optionsValue)
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error processing options", e)
            }
        }

        return QueuedPuzzle(
            puzzleId = puzzleData.optString("puzzleId", "queue_${System.currentTimeMillis()}"),
            puzzleType = puzzleType,
            difficulty = difficulty,
            question = puzzleData.optString("question", ""),
            answer = puzzleData.optString("answer", ""),
            hint = puzzleData.optString("hint", ""),
            options = options,
            queuedAt = System.currentTimeMillis(),
            mediaUrls = extractMediaUrls(puzzleData)
        )
    }

    private fun processBatchResponse(responseBody: String): List<QueuedPuzzle> {
        return try {
            val json = JSONObject(responseBody)
            if (!json.optBoolean("success", false)) {
                return emptyList()
            }

            val puzzleData = json.getJSONObject("puzzleData")
            val allPuzzles = mutableListOf<QueuedPuzzle>()

            puzzleData.keys().forEach { puzzleType ->
                val puzzlesArray = puzzleData.getJSONArray(puzzleType)

                for (i in 0 until puzzlesArray.length()) {
                    try {
                        val puzzleObj = puzzlesArray.getJSONObject(i)
                        val queuedPuzzle = convertSinglePuzzleDataToQueuedPuzzle(puzzleObj, "medium")
                        allPuzzles.add(queuedPuzzle)
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to parse puzzle $i for $puzzleType", e)
                    }
                }
            }

            allPuzzles
        } catch (e: Exception) {
            Log.e(TAG, "Failed to process batch response", e)
            emptyList()
        }
    }

    private fun convertSinglePuzzleDataToQueuedPuzzle(puzzleData: JSONObject, difficulty: String): QueuedPuzzle {
        val puzzleId = when {
            puzzleData.has("puzzleId") -> puzzleData.getString("puzzleId")
            puzzleData.has("puzzleid") -> puzzleData.getString("puzzleid")
            puzzleData.has("id") -> puzzleData.getString("id")
            else -> "queue_${System.currentTimeMillis()}_${(0..9999).random()}"
        }

        val puzzleType = when {
            puzzleData.has("puzzleType") -> puzzleData.getString("puzzleType")
            puzzleData.has("type") -> puzzleData.getString("type")
            else -> "unknown"
        }

        val options = mutableListOf<String>()
        if (puzzleData.has("options") && !puzzleData.isNull("options")) {
            try {
                val optionsValue = puzzleData.get("options")
                when (optionsValue) {
                    is JSONArray -> {
                        for (i in 0 until optionsValue.length()) {
                            options.add(optionsValue.optString(i))
                        }
                    }
                    is String -> {
                        if (optionsValue.trim().startsWith("[") && optionsValue.trim().endsWith("]")) {
                            if (optionsValue.trim() != "[]") {
                                try {
                                    val optionsArray = JSONArray(optionsValue)
                                    for (i in 0 until optionsArray.length()) {
                                        options.add(optionsArray.optString(i))
                                    }
                                } catch (e: Exception) {
                                    Log.w(TAG, "Failed to parse options string: $optionsValue", e)
                                }
                            }
                        } else {
                            if (optionsValue.isNotEmpty()) {
                                options.add(optionsValue)
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error processing options", e)
            }
        }

        return QueuedPuzzle(
            puzzleId = puzzleId,
            puzzleType = puzzleType,
            question = puzzleData.optString("question", ""),
            answer = puzzleData.optString("answer", ""),
            hint = puzzleData.optString("hint", ""),
            difficulty = puzzleData.optString("difficulty", difficulty),
            options = options,
            queuedAt = System.currentTimeMillis(),
            mediaUrls = extractMediaUrls(puzzleData)
        )
    }

    private fun extractMediaUrls(puzzleData: JSONObject): List<String> {
        val urls = mutableListOf<String>()
        val urlRegex = """https://[^\s\)"']+\.(jpg|jpeg|png|gif|webp|mp3|wav|ogg|m4a)(\?[^\s\)"']*)?""".toRegex(RegexOption.IGNORE_CASE)

        listOf(
            puzzleData.optString("question", ""),
            puzzleData.optString("hint", ""),
            puzzleData.optString("answer", "")
        ).forEach { text ->
            urls.addAll(urlRegex.findAll(text).map { it.value })
        }

        return urls.distinct()
    }

    private fun loadQueuesFromDisk() {
        try {
            val queueData = prefs.getString("queue_data", null)
            if (queueData != null) {
                val type = object : TypeToken<Map<String, List<QueuedPuzzle>>>() {}.type
                val loadedQueues: Map<String, List<QueuedPuzzle>> = gson.fromJson(queueData, type)

                loadedQueues.forEach { (queueKey, puzzles) ->
                    val queue = ConcurrentLinkedQueue<QueuedPuzzle>()
                    queue.addAll(puzzles)
                    puzzleQueues[queueKey] = queue

                    // Extract puzzle type and mark as initialized
                    val puzzleType = queueKey.substringBeforeLast("_")
                    if (puzzles.isNotEmpty()) {
                        initializedTypes.add(puzzleType)
                    }
                }

                Log.d(TAG, "Loaded ${puzzleQueues.size} queues from disk")
            }

            val statsData = prefs.getString("queue_stats", null)
            if (statsData != null) {
                val type = object : TypeToken<Map<String, QueueStats>>() {}.type
                val loadedStats: Map<String, QueueStats> = gson.fromJson(statsData, type)
                queueStats.putAll(loadedStats)
            }

        } catch (e: Exception) {
            Log.e(TAG, "Failed to load queues from disk", e)
        }
    }

    private fun saveQueuesToDisk() {
        try {
            val queueData = puzzleQueues.mapValues { (_, queue) -> queue.toList() }
            val queueJson = gson.toJson(queueData)
            val statsJson = gson.toJson(queueStats)

            prefs.edit()
                .putString("queue_data", queueJson)
                .putString("queue_stats", statsJson)
                .putLong("last_saved", System.currentTimeMillis())
                .apply()

        } catch (e: Exception) {
            Log.e(TAG, "Failed to save queues to disk", e)
        }
    }

    // Cleanup
    fun cleanup() {
        currentlyFetching.values.forEach { it.cancel() }
        currentlyFetching.clear()
        queueScope.cancel()
    }
}

data class QueuedPuzzle(
    val puzzleId: String,
    val puzzleType: String,
    val question: String,
    val answer: String,
    val hint: String = "",
    val difficulty: String = "medium",
    val options: List<String> = emptyList(),
    val queuedAt: Long = System.currentTimeMillis(),
    val mediaUrls: List<String> = emptyList()
) {
    fun toPuzzle(): Puzzle {
        return Puzzle(
            puzzleId = this.puzzleId,
            puzzleType = this.puzzleType,
            question = this.question,
            answer = this.answer,
            hint = this.hint,
            difficulty = this.difficulty,
            options = this.options
        )
    }
}