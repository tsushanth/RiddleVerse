package com.kreativekoala.riddleverse

import android.content.Context
import android.util.Log
import kotlinx.coroutines.*
import okhttp3.*
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * Complete media cache manager with puzzle-aware cleanup
 * Drop-in replacement for basic media caching needs
 */
class MediaCacheManager private constructor(private val context: Context) {
    companion object {
        @Volatile
        private var INSTANCE: MediaCacheManager? = null
        private const val TAG = "MediaCacheManager"
        private const val CACHE_DIR_NAME = "puzzle_media_cache"
        private const val MAX_CACHE_SIZE_MB = 500L
        private const val CACHE_EXPIRY_DAYS = 7L
        private const val MAX_CONCURRENT_DOWNLOADS = 3
        private const val PREFS_NAME = "media_cache_prefs"

        fun getInstance(context: Context): MediaCacheManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: MediaCacheManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private val cacheDir = File(context.cacheDir, CACHE_DIR_NAME).apply {
        if (!exists()) mkdirs()
    }

    // Track puzzle-media relationships
    private val puzzleMediaMapping = ConcurrentHashMap<String, Set<String>>() // puzzleId -> URL hashes
    private val mediaUsageCount = ConcurrentHashMap<String, Int>() // URL hash -> usage count
    private val ongoingDownloads = ConcurrentHashMap<String, Deferred<File?>>()
    private val downloadSemaphore = kotlinx.coroutines.sync.Semaphore(MAX_CONCURRENT_DOWNLOADS)
    private val cacheScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    init {
        loadPuzzleMediaMappings()
        cacheScope.launch {
            cleanupExpiredFiles()
        }
    }

    fun extractMediaUrlsFromPuzzle(puzzle: Puzzle): List<String> {
        val mediaUrls = mutableListOf<String>()

        try {
            val questionText = puzzle.question ?: ""

            // Only attempt JSON parsing for puzzle types known to have structured data
            val structuredPuzzleTypes = setOf(
                "find_object", "imagequestion", "musicidentification", "imagepuzzle"
            )

            val shouldParseAsJson = structuredPuzzleTypes.contains(puzzle.puzzleType?.lowercase()) &&
                    questionText.trim().startsWith("{") &&
                    questionText.trim().endsWith("}")

            if (shouldParseAsJson) {
                try {
                    val questionJson = JSONObject(questionText)

                    // Extract image URL for find object puzzles
                    val imageUrl = questionJson.optString("imageUrl", "")
                    if (imageUrl.isNotEmpty() && imageUrl.startsWith("http")) {
                        mediaUrls.add(imageUrl)
                    }

                    // Extract image URLs from Image Question puzzles
                    val questionsArray = questionJson.optJSONArray("questions")
                    if (questionsArray != null) {
                        for (i in 0 until questionsArray.length()) {
                            val qJson = questionsArray.getJSONObject(i)

                            // Image URLs in questions
                            val qImageUrl = qJson.optString("imageUrl", "")
                            if (qImageUrl.isNotEmpty() && qImageUrl.startsWith("http")) {
                                mediaUrls.add(qImageUrl)
                            }

                            // Audio URLs in music puzzles
                            val audioUrl = qJson.optString("audioUrl", "")
                            if (audioUrl.isNotEmpty() && audioUrl.startsWith("http")) {
                                mediaUrls.add(audioUrl)
                            }

                            // Album images in music puzzles
                            val metadataObj = qJson.optJSONObject("metadata")
                            if (metadataObj != null) {
                                val albumImageUrl = metadataObj.optString("albumImageUrl", "")
                                if (albumImageUrl.isNotEmpty() && albumImageUrl.startsWith("http")) {
                                    mediaUrls.add(albumImageUrl)
                                }
                            }
                        }
                    }

                    // Extract other potential media URLs
                    val mediaArray = questionJson.optJSONArray("mediaUrls")
                    if (mediaArray != null) {
                        for (i in 0 until mediaArray.length()) {
                            val url = mediaArray.optString(i, "")
                            if (url.isNotEmpty() && url.startsWith("http")) {
                                mediaUrls.add(url)
                            }
                        }
                    }
                } catch (jsonException: Exception) {
                    Log.w(TAG, "Question appears to be JSON but failed to parse: $questionText", jsonException)
                    // Fall through to regex extraction
                }
            }

            // Whether JSON parsing succeeded or failed, also extract URLs using regex
            // This handles both plain text questions and any URLs missed in JSON parsing
            extractUrlsWithRegex(puzzle, mediaUrls)

        } catch (e: Exception) {
            Log.e(TAG, "Error extracting media URLs", e)

            // Fallback: try regex extraction on all puzzle fields
            try {
                extractUrlsWithRegex(puzzle, mediaUrls)
            } catch (regexException: Exception) {
                Log.e(TAG, "Regex extraction also failed", regexException)
            }
        }

        return mediaUrls.distinct()
    }

    private fun extractUrlsWithRegex(puzzle: Puzzle, mediaUrls: MutableList<String>) {
        val urlRegex = """https://[^\s\)"']+\.(jpg|jpeg|png|gif|webp|mp3|wav|ogg|m4a|mp4)(\?[^\s\)"']*)?""".toRegex(RegexOption.IGNORE_CASE)

        // Extract from all text fields
        val textFields = listOf(
            puzzle.question ?: "",
            puzzle.hint ?: "",
            puzzle.answer ?: ""
        )

        textFields.forEach { text ->
            urlRegex.findAll(text).forEach { match ->
                val url = match.value
                if (!mediaUrls.contains(url)) {
                    mediaUrls.add(url)
                }
            }
        }

        // Also check options if they exist
        puzzle.options.forEach { option ->
            urlRegex.findAll(option).forEach { match ->
                val url = match.value
                if (!mediaUrls.contains(url)) {
                    mediaUrls.add(url)
                }
            }
        }
    }

    /**
     * Get cached media URL or download if not cached
     */
    suspend fun getCachedMediaUrl(originalUrl: String): String {
        return withContext(Dispatchers.IO) {
            try {
                if (originalUrl.isBlank() || !isMediaUrl(originalUrl)) {
                    return@withContext originalUrl
                }

                val urlHash = hashUrl(originalUrl)
                val cachedFile = getCachedFile(urlHash, originalUrl)

                if (cachedFile != null && cachedFile.exists() && !isFileExpired(cachedFile)) {
                    Log.d(TAG, "Cache hit for: $originalUrl")
                    updateFileAccessTime(cachedFile)
                    return@withContext cachedFile.absolutePath
                }

                // Check if download already in progress
                val ongoingDownload = ongoingDownloads[urlHash]
                if (ongoingDownload != null) {
                    Log.d(TAG, "Download in progress for: $originalUrl")
                    val result = ongoingDownload.await()
                    return@withContext result?.absolutePath ?: originalUrl
                }

                // Start new download
                val downloadJob = cacheScope.async {
                    downloadAndCacheMedia(originalUrl, urlHash)
                }

                ongoingDownloads[urlHash] = downloadJob

                try {
                    val downloadedFile = downloadJob.await()
                    return@withContext downloadedFile?.absolutePath ?: originalUrl
                } finally {
                    ongoingDownloads.remove(urlHash)
                }

            } catch (e: Exception) {
                Log.e(TAG, "Error getting cached media for $originalUrl", e)
                originalUrl
            }
        }
    }

    /**
     * Preload media for a puzzle and track the relationship
     */
    suspend fun preloadPuzzleMedia(puzzle: QueuedPuzzle) {
        if (puzzle.mediaUrls.isEmpty()) return

        withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "Preloading ${puzzle.mediaUrls.size} media files for puzzle ${puzzle.puzzleId}")

                val urlHashes = mutableSetOf<String>()

                puzzle.mediaUrls.forEach { url ->
                    try {
                        getCachedMediaUrl(url) // This caches the media
                        val urlHash = hashUrl(url)
                        urlHashes.add(urlHash)

                        // Increment usage count
                        mediaUsageCount[urlHash] = (mediaUsageCount[urlHash] ?: 0) + 1

                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to preload media: $url", e)
                    }
                }

                // Track puzzle-media relationship
                if (urlHashes.isNotEmpty()) {
                    puzzleMediaMapping[puzzle.puzzleId] = urlHashes
                    savePuzzleMediaMappings()
                    Log.d(TAG, "Tracked ${urlHashes.size} media files for puzzle ${puzzle.puzzleId}")
                } else {
                    Log.d(TAG, "No media files to preload for puzzle ${puzzle.puzzleId}")
                }

            } catch (e: Exception) {
                Log.e(TAG, "Error preloading puzzle media", e)
            }
        }
    }

    /**
     * Clean up media for a completed puzzle
     */
    suspend fun cleanupPuzzleMedia(puzzleId: String) {
        withContext(Dispatchers.IO) {
            try {
                val mediaHashes = puzzleMediaMapping[puzzleId]
                if (mediaHashes.isNullOrEmpty()) {
                    Log.d(TAG, "No tracked media for puzzle $puzzleId")
                    return@withContext
                }

                Log.d(TAG, "Cleaning up ${mediaHashes.size} media files for puzzle $puzzleId")

                var deletedCount = 0
                var freedSpaceMB = 0L

                mediaHashes.forEach { urlHash ->
                    try {
                        // Decrement usage count
                        val currentUsage = mediaUsageCount[urlHash] ?: 0
                        val newUsage = maxOf(0, currentUsage - 1)

                        if (newUsage == 0) {
                            // No more puzzles using this media, safe to delete
                            mediaUsageCount.remove(urlHash)
                            val deletedFile = deleteCachedFile(urlHash)
                            if (deletedFile != null) {
                                freedSpaceMB += deletedFile.length() / (1024 * 1024)
                                deletedCount++
                                Log.d(TAG, "Deleted unused media: ${deletedFile.name}")
                            }
                        } else {
                            // Still in use by other puzzles
                            mediaUsageCount[urlHash] = newUsage
                            Log.d(TAG, "Media $urlHash still used by $newUsage other puzzles")
                        }

                    } catch (e: Exception) {
                        Log.w(TAG, "Error cleaning up media $urlHash", e)
                    }
                }

                // Remove puzzle from tracking
                puzzleMediaMapping.remove(puzzleId)
                savePuzzleMediaMappings()

                if (deletedCount > 0) {
                    Log.d(TAG, "Cleaned up $deletedCount files (${freedSpaceMB}MB) for puzzle $puzzleId")
                }

            } catch (e: Exception) {
                Log.e(TAG, "Error during puzzle media cleanup", e)
            }
        }
    }

    /**
     * Bulk cleanup for multiple puzzles
     */
    suspend fun cleanupMultiplePuzzleMedia(puzzleIds: List<String>) {
        withContext(Dispatchers.IO) {
            Log.d(TAG, "Bulk cleanup for ${puzzleIds.size} puzzles")
            puzzleIds.forEach { puzzleId ->
                cleanupPuzzleMedia(puzzleId)
            }
        }
    }

    /**
     * Force cleanup of unused media files
     */
    suspend fun forceCleanupUnusedMedia() {
        withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "Force cleanup of unused media files")

                val allFiles = cacheDir.listFiles() ?: return@withContext
                val trackedHashes = puzzleMediaMapping.values.flatten().toSet()

                var deletedCount = 0
                var freedSpaceMB = 0L

                allFiles.forEach { file ->
                    val fileHash = file.nameWithoutExtension
                    if (!trackedHashes.contains(fileHash)) {
                        // File not tracked by any puzzle
                        freedSpaceMB += file.length() / (1024 * 1024)
                        if (file.delete()) {
                            deletedCount++
                        }
                    }
                }

                // Clean up usage counts for deleted files
                val existingHashes = (cacheDir.listFiles() ?: emptyArray()).map { it.nameWithoutExtension }.toSet()
                mediaUsageCount.keys.removeAll { !existingHashes.contains(it) }

                Log.d(TAG, "Force cleanup: deleted $deletedCount files, freed ${freedSpaceMB}MB")

            } catch (e: Exception) {
                Log.e(TAG, "Error during force cleanup", e)
            }
        }
    }

    /**
     * Clear all cache and reset tracking
     */
    fun clearCache() {
        cacheScope.launch {
            try {
                cacheDir.listFiles()?.forEach { it.delete() }
                puzzleMediaMapping.clear()
                mediaUsageCount.clear()
                savePuzzleMediaMappings()
                Log.d(TAG, "Cache cleared completely")
            } catch (e: Exception) {
                Log.e(TAG, "Error clearing cache", e)
            }
        }
    }

    /**
     * Get detailed cache statistics
     */
    suspend fun getCacheStats(): CacheStats {
        return withContext(Dispatchers.IO) {
            val files = cacheDir.listFiles() ?: emptyArray()
            val totalSizeMB = files.sumOf { it.length() } / (1024 * 1024)
            val fileCount = files.size
            val expiredFiles = files.count { isFileExpired(it) }
            val trackedPuzzles = puzzleMediaMapping.size
            val trackedMediaFiles = puzzleMediaMapping.values.flatten().distinct().size
            val unusedFiles = files.count { file ->
                val hash = file.nameWithoutExtension
                !puzzleMediaMapping.values.flatten().contains(hash)
            }

            CacheStats(
                totalFiles = fileCount,
                totalSizeMB = totalSizeMB,
                expiredFiles = expiredFiles,
                maxSizeMB = MAX_CACHE_SIZE_MB,
                trackedPuzzles = trackedPuzzles,
                trackedMediaFiles = trackedMediaFiles,
                unusedFiles = unusedFiles,
                usageMapSize = mediaUsageCount.size
            )
        }
    }

    // Private helper methods

    private suspend fun downloadAndCacheMedia(url: String, urlHash: String): File? {
        return try {
            downloadSemaphore.acquire()

            Log.d(TAG, "Downloading: $url")

            val request = Request.Builder().url(url).build()
            val response = client.newCall(request).execute()

            if (!response.isSuccessful) {
                Log.w(TAG, "Download failed: ${response.code} for $url")
                return null
            }

            val contentType = response.header("content-type") ?: ""
            val fileExtension = getFileExtensionFromContentType(contentType)
                ?: getFileExtensionFromUrl(url)
                ?: "tmp"

            val cacheFile = File(cacheDir, "$urlHash.$fileExtension")

            response.body?.byteStream()?.use { inputStream ->
                cacheFile.outputStream().use { outputStream ->
                    inputStream.copyTo(outputStream)
                }
            }

            cacheFile.setLastModified(System.currentTimeMillis())
            checkAndCleanupBySize()

            Log.d(TAG, "Cached: ${cacheFile.name} (${cacheFile.length() / 1024}KB)")
            cacheFile

        } catch (e: Exception) {
            Log.e(TAG, "Download error for $url", e)
            null
        } finally {
            downloadSemaphore.release()
        }
    }

    private fun getCachedFile(urlHash: String, originalUrl: String): File? {
        val possibleExtensions = listOf(
            getFileExtensionFromUrl(originalUrl),
            "jpg", "jpeg", "png", "gif", "webp",
            "mp3", "wav", "ogg", "m4a", "mp4",
            "tmp"
        ).filterNotNull()

        for (extension in possibleExtensions) {
            val file = File(cacheDir, "$urlHash.$extension")
            if (file.exists()) return file
        }
        return null
    }

    private fun deleteCachedFile(urlHash: String): File? {
        val possibleExtensions = listOf(
            "jpg", "jpeg", "png", "gif", "webp",
            "mp3", "wav", "ogg", "m4a", "mp4", "tmp"
        )

        for (extension in possibleExtensions) {
            val file = File(cacheDir, "$urlHash.$extension")
            if (file.exists() && file.delete()) {
                return file
            }
        }
        return null
    }

    private fun hashUrl(url: String): String {
        return try {
            val digest = MessageDigest.getInstance("SHA-256")
            val hash = digest.digest(url.toByteArray())
            hash.joinToString("") { "%02x".format(it) }.take(16)
        } catch (e: Exception) {
            Log.w(TAG, "Hash error, using simple hash", e)
            url.hashCode().toString()
        }
    }

    private fun isMediaUrl(url: String): Boolean {
        val mediaExtensions = listOf(
            "jpg", "jpeg", "png", "gif", "webp", "bmp",
            "mp3", "wav", "ogg", "m4a", "mp4", "mov", "avi"
        )
        val extension = getFileExtensionFromUrl(url)?.lowercase()
        return extension in mediaExtensions || url.contains("supabase", ignoreCase = true)
    }

    private fun getFileExtensionFromUrl(url: String): String? {
        return try {
            val path = url.substringBefore("?")
            val lastDot = path.lastIndexOf(".")
            if (lastDot != -1 && lastDot < path.length - 1) {
                path.substring(lastDot + 1).lowercase()
            } else null
        } catch (e: Exception) {
            null
        }
    }

    private fun getFileExtensionFromContentType(contentType: String): String? {
        return when {
            contentType.contains("image/jpeg") -> "jpg"
            contentType.contains("image/png") -> "png"
            contentType.contains("image/gif") -> "gif"
            contentType.contains("image/webp") -> "webp"
            contentType.contains("audio/mpeg") -> "mp3"
            contentType.contains("audio/wav") -> "wav"
            contentType.contains("audio/ogg") -> "ogg"
            contentType.contains("audio/mp4") -> "m4a"
            contentType.contains("video/mp4") -> "mp4"
            else -> null
        }
    }

    private fun isFileExpired(file: File): Boolean {
        val ageMillis = System.currentTimeMillis() - file.lastModified()
        val ageDays = TimeUnit.MILLISECONDS.toDays(ageMillis)
        return ageDays > CACHE_EXPIRY_DAYS
    }

    private fun updateFileAccessTime(file: File) {
        try {
            file.setLastModified(System.currentTimeMillis())
        } catch (e: Exception) {
            Log.w(TAG, "Failed to update access time for ${file.name}")
        }
    }

    private suspend fun cleanupExpiredFiles() {
        try {
            val files = cacheDir.listFiles() ?: return
            val trackedHashes = puzzleMediaMapping.values.flatten().toSet()

            var deletedCount = 0
            var freedSpaceMB = 0L

            files.filter { isFileExpired(it) }.forEach { file ->
                val fileHash = file.nameWithoutExtension

                // Only delete expired files not tracked by active puzzles
                if (!trackedHashes.contains(fileHash)) {
                    freedSpaceMB += file.length() / (1024 * 1024)
                    if (file.delete()) {
                        deletedCount++
                        mediaUsageCount.remove(fileHash)
                    }
                }
            }

            if (deletedCount > 0) {
                Log.d(TAG, "Expired cleanup: deleted $deletedCount files, freed ${freedSpaceMB}MB")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error during expired files cleanup", e)
        }
    }

    private suspend fun checkAndCleanupBySize() {
        try {
            val files = cacheDir.listFiles() ?: return
            val totalSizeMB = files.sumOf { it.length() } / (1024 * 1024)

            if (totalSizeMB > MAX_CACHE_SIZE_MB) {
                val trackedHashes = puzzleMediaMapping.values.flatten().toSet()

                // Delete oldest untracked files first
                val untrackedFiles = files.filter { file ->
                    !trackedHashes.contains(file.nameWithoutExtension)
                }.sortedBy { it.lastModified() }

                var currentSizeMB = totalSizeMB
                var deletedCount = 0

                for (file in untrackedFiles) {
                    if (currentSizeMB <= MAX_CACHE_SIZE_MB * 0.8) break

                    val fileSizeMB = file.length() / (1024 * 1024)
                    if (file.delete()) {
                        currentSizeMB -= fileSizeMB
                        deletedCount++
                        mediaUsageCount.remove(file.nameWithoutExtension)
                    }
                }

                if (deletedCount > 0) {
                    Log.d(TAG, "Size cleanup: deleted $deletedCount untracked files")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error during size cleanup", e)
        }
    }

    private fun savePuzzleMediaMappings() {
        try {
            val mappingsJson = com.google.gson.Gson().toJson(puzzleMediaMapping.toMap())
            val usageJson = com.google.gson.Gson().toJson(mediaUsageCount.toMap())

            prefs.edit()
                .putString("puzzle_media_mappings", mappingsJson)
                .putString("media_usage_count", usageJson)
                .putLong("last_saved", System.currentTimeMillis())
                .apply()

        } catch (e: Exception) {
            Log.e(TAG, "Error saving puzzle media mappings", e)
        }
    }

    private fun loadPuzzleMediaMappings() {
        try {
            val mappingsJson = prefs.getString("puzzle_media_mappings", null)
            val usageJson = prefs.getString("media_usage_count", null)

            if (mappingsJson != null) {
                val type = object : com.google.gson.reflect.TypeToken<Map<String, Set<String>>>() {}.type
                val loadedMappings: Map<String, Set<String>> = com.google.gson.Gson().fromJson(mappingsJson, type)
                puzzleMediaMapping.putAll(loadedMappings)
            }

            if (usageJson != null) {
                val type = object : com.google.gson.reflect.TypeToken<Map<String, Int>>() {}.type
                val loadedUsage: Map<String, Int> = com.google.gson.Gson().fromJson(usageJson, type)
                mediaUsageCount.putAll(loadedUsage)
            }

            Log.d(TAG, "Loaded ${puzzleMediaMapping.size} puzzle mappings, ${mediaUsageCount.size} usage counts")

        } catch (e: Exception) {
            Log.e(TAG, "Error loading puzzle media mappings", e)
        }
    }
}

data class CacheStats(
    val totalFiles: Int,
    val totalSizeMB: Long,
    val expiredFiles: Int,
    val maxSizeMB: Long,
    val trackedPuzzles: Int,
    val trackedMediaFiles: Int,
    val unusedFiles: Int,
    val usageMapSize: Int
)