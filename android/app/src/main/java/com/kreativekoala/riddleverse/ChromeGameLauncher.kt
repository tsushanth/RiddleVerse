package com.kreativekoala.riddleverse

import android.content.Context
import android.net.Uri
import androidx.browser.customtabs.CustomTabColorSchemeParams
import androidx.browser.customtabs.CustomTabsIntent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Launches a game in a Chrome Custom Tab via the host-controlled session wrapper.
 * Calls /api/sessions/start first to get a /play?session_id= wrapper URL,
 * which owns the lifecycle (timer, score, finish button).
 * Falls back to the direct game URL if session start fails.
 */
object ChromeGameLauncher {

    private const val API_BASE = "https://puzzleverseai.com"
    private const val GAME_BASE = "$API_BASE/api/game-creation"

    /**
     * Start a session and return the wrapper URL, or null on failure.
     * Must be called from a coroutine (suspend).
     */
    suspend fun startSession(gameId: String, userId: String?): String? = withContext(Dispatchers.IO) {
        try {
            val url = URL("$API_BASE/api/sessions/start")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.setRequestProperty("Content-Type", "application/json")
            conn.doOutput = true
            conn.connectTimeout = 10_000
            conn.readTimeout = 10_000

            val body = JSONObject().apply {
                put("game_id", gameId)
                put("user_id", userId ?: "anon-android")
                put("source", "android")
            }
            conn.outputStream.use { it.write(body.toString().toByteArray()) }

            if (conn.responseCode == 200) {
                val response = conn.inputStream.bufferedReader().readText()
                JSONObject(response).optString("wrapper_url").takeIf { it.isNotBlank() }
            } else null
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Launches a game. Starts a session for wrapper URL; falls back to direct URL.
     * Fire-and-forget — safe to call from any context.
     */
    fun launchGame(context: Context, gameId: String, userId: String? = null) {
        CoroutineScope(Dispatchers.IO).launch {
            val wrapperUrl = startSession(gameId, userId)
            val launchUrl = wrapperUrl
                ?: "$GAME_BASE/$gameId?platform=session${if (!userId.isNullOrBlank()) "&userId=$userId" else ""}"

            val colorScheme = CustomTabColorSchemeParams.Builder()
                .setToolbarColor(0xFF1A1A2E.toInt())
                .setNavigationBarColor(0xFF1A1A2E.toInt())
                .build()

            val customTabsIntent = CustomTabsIntent.Builder()
                .setDefaultColorSchemeParams(colorScheme)
                .setShowTitle(false)
                .setUrlBarHidingEnabled(true)
                .build()

            withContext(Dispatchers.Main) {
                customTabsIntent.launchUrl(context, Uri.parse(launchUrl))
            }
        }
    }

    /**
     * Parse a deep link URI from riddleverse://game-complete?gameId=X&score=Y
     */
    fun parseGameCompleteDeepLink(uri: Uri): GameCompleteResult? {
        if (uri.scheme != "riddleverse" || uri.host != "game-complete") return null
        val gameId = uri.getQueryParameter("gameId") ?: return null
        val score = uri.getQueryParameter("score")?.toIntOrNull() ?: 0
        val userId = uri.getQueryParameter("userId")
        val creatorId = uri.getQueryParameter("creatorId")
        val action = uri.getQueryParameter("action")
        return GameCompleteResult(gameId, score, userId, creatorId, action)
    }

    data class GameCompleteResult(
        val gameId: String,
        val score: Int,
        val userId: String?,
        val creatorId: String? = null,
        val action: String? = null
    )
}
