package com.kreativekoala.riddleverse

import android.app.NotificationManager
import android.content.Context
import android.util.Base64
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.File
import java.util.UUID
import java.util.zip.ZipInputStream

// Unified generation mode
sealed class GenerationMode {
    data class Custom(val prompt: String, val imageBase64: String? = null, val useCoins: Boolean = false) : GenerationMode()
    data class Remix(val puzzleType: String, val difficulty: String, val remixDescription: String,
                     val title: String, val imageBase64: String? = null, val useCoins: Boolean = false) : GenerationMode()
}

data class GenerationResult(
    val bundleDirPath: String,
    val bundleBase64: String,
    val gameId: String? = null
)

class GameGenerationViewModel : ViewModel() {
    private val TAG = "GameGenVM"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    // Generation state
    var isGenerating by mutableStateOf(false)
    var buildPhase by mutableStateOf("")
    var buildDetail by mutableStateOf("")
    var progressPercent by mutableFloatStateOf(0f)
    var estimatedSecondsRemaining by mutableIntStateOf(0)
    var errorMessage by mutableStateOf<String?>(null)

    // Rate limit upsell state
    var showRateLimitUpsell by mutableStateOf(false)
    var rateLimitCoinCost by mutableIntStateOf(10)

    // Hard paywall state (lifetime limit reached)
    var showHardPaywall by mutableStateOf(false)

    // Result state
    var bundleDirPath by mutableStateOf<String?>(null)
    var bundleBase64 by mutableStateOf<String?>(null)
    var showPreview by mutableStateOf(false)
    var resultGameId by mutableStateOf<String?>(null)

    // Remix-specific state
    var remixResultTitle by mutableStateOf("")
    var isRemixGeneration by mutableStateOf(false)
    var isOnRemixSheet by mutableStateOf(false)

    // Backwards compatibility
    var remixResultGameId: String?
        get() = resultGameId
        set(value) { resultGameId = value }

    // Notification state
    var showCompletionBanner by mutableStateOf(false)
    var isOnCreateTab by mutableStateOf(false)

    // The prompt used for current/last generation
    var currentPrompt by mutableStateOf("")

    private var generationJob: Job? = null
    private var progressJob: Job? = null
    private var phaseStartPct: Float = 0f
    private var phaseEndPct: Float = 0f
    private var phaseDuration: Long = 0L
    private var phaseStartTime: Long = 0L

    // MARK: - Unified Generation Entry Point

    fun startGeneration(mode: GenerationMode, context: Context) {
        if (isGenerating) return

        isGenerating = true
        errorMessage = null
        showRateLimitUpsell = false
        showHardPaywall = false
        buildDetail = ""
        progressPercent = 0f
        estimatedSecondsRemaining = 0
        resultGameId = null

        when (mode) {
            is GenerationMode.Custom -> {
                isRemixGeneration = false
                buildPhase = "Connecting..."
                currentPrompt = mode.prompt
            }
            is GenerationMode.Remix -> {
                isRemixGeneration = true
                buildPhase = "Preparing remix..."
                currentPrompt = mode.remixDescription
                remixResultTitle = mode.title
            }
        }

        val completionText = when (mode) {
            is GenerationMode.Custom -> mode.prompt
            is GenerationMode.Remix -> mode.title
        }

        generationJob = scope.launch {
            val result = streamInternal(mode, context)
            stopProgressInterpolation()
            isGenerating = false
            if (result != null) {
                bundleDirPath = result.bundleDirPath
                bundleBase64 = result.bundleBase64
                resultGameId = result.gameId
                progressPercent = 100f
                sendCompletionNotification(context, completionText)

                val isOnScreen = if (isRemixGeneration) isOnRemixSheet else isOnCreateTab
                if (isOnScreen) {
                    showPreview = true
                } else {
                    showCompletionBanner = true
                }
            } else if (errorMessage == null && !showRateLimitUpsell) {
                errorMessage = "Failed to generate game. Please try again."
            }
        }
    }

    // Convenience wrappers for backwards compatibility
    fun startGeneration(prompt: String, context: Context, imageBase64: String? = null, useCoins: Boolean = false) {
        startGeneration(GenerationMode.Custom(prompt, imageBase64, useCoins), context)
    }

    fun startRemixGeneration(
        puzzleType: String,
        difficulty: String,
        remixDescription: String,
        title: String,
        context: Context,
        imageBase64: String? = null,
        useCoins: Boolean = false
    ) {
        startGeneration(GenerationMode.Remix(puzzleType, difficulty, remixDescription, title, imageBase64, useCoins), context)
    }

    private fun sendCompletionNotification(context: Context, prompt: String) {
        try {
            // Check if notifications are enabled before posting
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (!nm.areNotificationsEnabled()) return

            val title = prompt.take(40).split(". ").first()
            val bodyText = if (isRemixGeneration) {
                "Your game \"$title\" is ready! Find it in Explore > Custom Games."
            } else {
                "Your game \"$title\" has been created. Tap to play!"
            }
            val notification = NotificationCompat.Builder(context, "puzzle_notifications")
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentTitle("Game Ready!")
                .setContentText(bodyText)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .build()
            nm.notify(System.currentTimeMillis().toInt(), notification)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to send notification", e)
        }
    }

    private fun startProgressInterpolation() {
        progressJob?.cancel()
        if (phaseDuration <= 0) return
        phaseStartTime = System.currentTimeMillis()
        progressJob = scope.launch {
            while (true) {
                delay(500)
                val elapsed = (System.currentTimeMillis() - phaseStartTime) / 1000.0
                val fraction = (elapsed / phaseDuration).coerceAtMost(0.95)
                progressPercent = phaseStartPct + (phaseEndPct - phaseStartPct) * fraction.toFloat()
            }
        }
    }

    private fun stopProgressInterpolation() {
        progressJob?.cancel()
        progressJob = null
    }

    fun cancelGeneration() {
        generationJob?.cancel()
        stopProgressInterpolation()
        isGenerating = false
        buildPhase = ""
        buildDetail = ""
        progressPercent = 0f
    }

    fun dismissBanner() {
        showCompletionBanner = false
    }

    fun openCompletedGame() {
        showCompletionBanner = false
        showPreview = true
    }

    // MARK: - Unified SSE Streaming

    private suspend fun streamInternal(mode: GenerationMode, context: Context): GenerationResult? {
        return withContext(Dispatchers.IO) {
            try {
                val userId = FirebaseAuth.getInstance().currentUser?.uid ?: "anonymous"
                val userName = FirebaseAuth.getInstance().currentUser?.displayName
                    ?: FirebaseAuth.getInstance().currentUser?.email?.substringBefore("@")
                    ?: "Anonymous"

                val (url, json, logPrefix) = buildRequest(mode, userId, userName)

                val body = json.toString().toRequestBody("application/json".toMediaTypeOrNull())
                val request = Request.Builder()
                    .url(url)
                    .addHeader("x-platform", "android")
                    .addHeader("Content-Type", "application/json")
                    .addHeader("Accept", "text/event-stream")
                    .post(body)
                    .build()

                val client = HttpClientProvider.client.newBuilder()
                    .protocols(listOf(Protocol.HTTP_1_1))
                    .readTimeout(java.time.Duration.ofMinutes(10))
                    .build()

                Log.d(TAG, "$logPrefix request to: $url")
                val response = client.newCall(request).execute()
                if (!response.isSuccessful) {
                    Log.e(TAG, "$logPrefix HTTP ${response.code}")
                    var serverError = "Server error (${response.code})"
                    var canUseCoinsFlag = false
                    var coinCost = 10
                    try {
                        val errBody = response.body?.string()
                        response.close()
                        if (errBody != null) {
                            val errJson = JSONObject(errBody)
                            errJson.optString("error", "").let { if (it.isNotEmpty()) serverError = it }
                            canUseCoinsFlag = errJson.optBoolean("canUseCoins", false)
                            coinCost = errJson.optInt("coinCost", 10)
                        }
                    } catch (_: Exception) {}

                    // Hard paywall — lifetime limit reached, must subscribe
                    if (response.code == 403) {
                        try {
                            val errJson2 = JSONObject(serverError)
                            if (errJson2.optBoolean("requiresSubscription", false)) {
                                withContext(Dispatchers.Main) { showHardPaywall = true }
                                return@withContext null
                            }
                        } catch (_: Exception) {}
                        // Fallback: still show paywall for any 403
                        withContext(Dispatchers.Main) { showHardPaywall = true }
                        return@withContext null
                    }

                    if ((response.code == 429 || response.code == 402) && canUseCoinsFlag) {
                        withContext(Dispatchers.Main) {
                            rateLimitCoinCost = coinCost
                            showRateLimitUpsell = true
                        }
                        return@withContext null
                    }

                    withContext(Dispatchers.Main) {
                        errorMessage = serverError
                    }
                    return@withContext null
                }

                // Parse SSE stream
                val source = response.body?.source() ?: return@withContext null
                var bundleBase64Result: String? = null
                var gameId: String? = null
                var sseBuffer = ""

                while (!source.exhausted()) {
                    val line = source.readUtf8Line() ?: break
                    sseBuffer += line + "\n"

                    while (sseBuffer.contains("\n\n")) {
                        val idx = sseBuffer.indexOf("\n\n")
                        val event = sseBuffer.substring(0, idx)
                        sseBuffer = sseBuffer.substring(idx + 2)

                        for (eventLine in event.split("\n")) {
                            if (!eventLine.startsWith("data: ")) continue
                            val jsonStr = eventLine.substring(6)

                            try {
                                val eventJson = JSONObject(jsonStr)
                                when (eventJson.optString("type")) {
                                    "status" -> {
                                        val message = eventJson.optString("message", "")
                                        val detail = eventJson.optString("detail", "")
                                        val pct = eventJson.optDouble("progressPercent", 0.0).toFloat()
                                        val endPct = eventJson.optDouble("progressEndPct", pct.toDouble()).toFloat()
                                        val phaseSecs = eventJson.optLong("phaseDurationSeconds", 0)
                                        val eta = eventJson.optInt("estimatedSecondsRemaining", 0)
                                        withContext(Dispatchers.Main) {
                                            buildPhase = message
                                            buildDetail = detail
                                            progressPercent = pct
                                            estimatedSecondsRemaining = eta
                                            phaseStartPct = pct
                                            phaseEndPct = endPct
                                            phaseDuration = phaseSecs
                                            startProgressInterpolation()
                                        }
                                    }
                                    "result" -> {
                                        bundleBase64Result = eventJson.optString("bundle", null)
                                        gameId = eventJson.optString("gameId", null)
                                    }
                                    "error" -> {
                                        val errMsg = eventJson.optString("error", "Unknown error")
                                        Log.e(TAG, "$logPrefix SSE error: $errMsg")
                                        withContext(Dispatchers.Main) {
                                            errorMessage = errMsg
                                        }
                                        return@withContext null
                                    }
                                }
                            } catch (e: Exception) {
                                Log.w(TAG, "$logPrefix SSE parse error", e)
                            }
                        }
                    }
                }

                response.close()

                if (bundleBase64Result == null) {
                    Log.e(TAG, "$logPrefix: no bundle in response")
                    withContext(Dispatchers.Main) {
                        errorMessage = "Build completed but no game bundle received. Please try again."
                    }
                    return@withContext null
                }

                withContext(Dispatchers.Main) {
                    buildPhase = "Extracting game..."
                    buildDetail = ""
                    progressPercent = 95f
                }

                val dir = extractGameBundle(context, bundleBase64Result!!)
                if (dir == null) {
                    Log.e(TAG, "$logPrefix zip extraction failed")
                    withContext(Dispatchers.Main) {
                        errorMessage = "Failed to extract game bundle. Please try again."
                    }
                    return@withContext null
                }

                GenerationResult(dir.absolutePath, bundleBase64Result!!, gameId)
            } catch (e: Exception) {
                Log.e(TAG, "Stream error", e)
                withContext(Dispatchers.Main) {
                    errorMessage = when {
                        e is java.net.SocketTimeoutException -> "Connection timed out. Please check your network and try again."
                        e is java.net.UnknownHostException -> "Cannot reach server. Please check your internet connection."
                        e is java.net.ConnectException -> "Cannot connect to server. Please try again later."
                        e.message?.contains("timeout", ignoreCase = true) == true -> "Request timed out. Please try again."
                        else -> "Connection error: ${e.localizedMessage ?: "Unknown error"}"
                    }
                }
                null
            }
        }
    }

    private fun buildRequest(mode: GenerationMode, userId: String, userName: String): Triple<String, JSONObject, String> {
        return when (mode) {
            is GenerationMode.Custom -> {
                val basePrompt = mode.prompt.trim()
                val fullPrompt = "$basePrompt\n\nInclude a brief 'How to Play' note in the game UI."
                val json = JSONObject().apply {
                    put("prompt", fullPrompt)
                    put("userId", userId)
                    put("userName", userName)
                    put("title", basePrompt.take(100))
                    if (mode.imageBase64 != null) put("referenceImage", mode.imageBase64)
                    if (mode.useCoins) put("useCoins", true)
                }
                Triple("https://puzzleverseai.com/api/game-creation/generate", json, "Custom")
            }
            is GenerationMode.Remix -> {
                val json = JSONObject().apply {
                    put("userId", userId)
                    put("userName", userName)
                    put("puzzleType", mode.puzzleType)
                    put("difficulty", mode.difficulty)
                    put("remixDescription", mode.remixDescription.trim() + "\n\nInclude a brief 'How to Play' note in the game UI.")
                    put("title", mode.title)
                    if (mode.imageBase64 != null) put("referenceImage", mode.imageBase64)
                    if (mode.useCoins) put("useCoins", true)
                }
                Triple("https://puzzleverseai.com/api/game-creation/remix", json, "Remix")
            }
        }
    }

    private fun extractGameBundle(context: Context, base64: String): File? {
        return try {
            val zipData = Base64.decode(base64, Base64.DEFAULT)
            val gameDir = File(context.cacheDir, "games/${UUID.randomUUID()}")
            gameDir.mkdirs()

            val zipStream = ZipInputStream(ByteArrayInputStream(zipData))
            var entry = zipStream.nextEntry

            while (entry != null) {
                val outFile = File(gameDir, entry.name)

                if (!outFile.canonicalPath.startsWith(gameDir.canonicalPath)) {
                    zipStream.closeEntry()
                    entry = zipStream.nextEntry
                    continue
                }

                if (entry.isDirectory) {
                    outFile.mkdirs()
                } else {
                    outFile.parentFile?.mkdirs()
                    outFile.outputStream().use { out ->
                        zipStream.copyTo(out)
                    }
                }

                zipStream.closeEntry()
                entry = zipStream.nextEntry
            }
            zipStream.close()

            val indexFile = File(gameDir, "index.html")
            if (!indexFile.exists()) {
                Log.e(TAG, "Bundle missing index.html")
                return null
            }

            gameDir
        } catch (e: Exception) {
            Log.e(TAG, "Zip extraction error", e)
            null
        }
    }
}
