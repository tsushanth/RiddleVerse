package com.kreativekoala.riddleverse

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.browser.customtabs.CustomTabColorSchemeParams
import androidx.browser.customtabs.CustomTabsIntent

/**
 * Launches a game in a Chrome Custom Tab for consistent rendering.
 * The game is hosted at puzzleverseai.com/play/{gameId} and handles
 * score submission server-side. On game over, it deep links back
 * to the app via riddleverse://game-complete?gameId=X&score=Y
 */
object ChromeGameLauncher {

    private const val BASE_URL = "https://puzzleverseai.com/play"

    fun launchGame(context: Context, gameId: String, userId: String? = null) {
        val url = buildString {
            append("$BASE_URL/$gameId")
            if (!userId.isNullOrBlank()) {
                append("?userId=$userId")
            }
        }

        val colorScheme = CustomTabColorSchemeParams.Builder()
            .setToolbarColor(0xFF1A1A2E.toInt())
            .setNavigationBarColor(0xFF1A1A2E.toInt())
            .build()

        val customTabsIntent = CustomTabsIntent.Builder()
            .setDefaultColorSchemeParams(colorScheme)
            .setShowTitle(true)
            .setUrlBarHidingEnabled(true)
            .build()

        customTabsIntent.launchUrl(context, Uri.parse(url))
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
