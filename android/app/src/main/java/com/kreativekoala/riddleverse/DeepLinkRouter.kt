package com.kreativekoala.riddleverse

import android.net.Uri

/** Where an incoming link should go. Pure data: parsing is unit tested without Android. */
sealed class DeepLink {
    /** A custom (generated) game by id. */
    data class Game(val gameId: String, val src: String?) : DeepLink()
    /** A built-in puzzle type, e.g. "sudoku"; "daily" means today's daily puzzle. */
    data class PlaySlug(val slug: String, val src: String?) : DeepLink()
    /** The game list (/play). */
    data class PlayIndex(val src: String?) : DeepLink()
    /** One-time code from the Telegram bot (/link). */
    data class TelegramLink(val code: String) : DeepLink()
    /** Legacy riddle share (/open-riddle?puzzleId=). Handled by the existing puzzle fetch in MainActivity. */
    data class Riddle(val puzzleId: String) : DeepLink()
}

object DeepLinkRouter {
    private val SITE_HOSTS = setOf("puzzleverseai.com", "www.puzzleverseai.com")
    private val SLUG = Regex("^[a-z]{2,24}$")
    private val GAME_ID = Regex("^[0-9a-fA-F-]{36}$")
    private val CODE = Regex("^[A-Za-z0-9_-]{32}$")
    private val SRC = Regex("^[a-z0-9_]{1,24}$")

    fun parse(uri: Uri?): DeepLink? {
        if (uri == null) return null
        return parse(uri.scheme, uri.host, uri.pathSegments.orEmpty()) { uri.getQueryParameter(it) }
    }

    /** [query] returns the first value of a query parameter or null. */
    fun parse(scheme: String?, host: String?, segments: List<String>, query: (String) -> String?): DeepLink? {
        val src = query("src")?.lowercase()?.takeIf { SRC.matches(it) }
        when (scheme?.lowercase()) {
            "riddleverse" -> return when (host) {
                "game" -> (segments.firstOrNull() ?: query("gameId"))?.takeIf { it.isNotBlank() }?.let { DeepLink.Game(it, src) }
                "auth" -> query("code")?.takeIf { CODE.matches(it) }?.let { DeepLink.TelegramLink(it) }
                "play" -> segments.firstOrNull()?.takeIf { SLUG.matches(it) }?.let { DeepLink.PlaySlug(it, src) } ?: DeepLink.PlayIndex(src)
                else -> null
            }
            "https" -> {
                if (host?.lowercase() !in SITE_HOSTS) return null
                return when (segments.firstOrNull()) {
                    "play" -> {
                        if (query("session_id") != null) return null // the AI game session wrapper is a web page
                        val seg = segments.getOrNull(1)
                        when {
                            seg == null -> DeepLink.PlayIndex(src)
                            GAME_ID.matches(seg) -> DeepLink.Game(seg, src)
                            SLUG.matches(seg) -> DeepLink.PlaySlug(seg, src)
                            else -> null
                        }
                    }
                    "app" -> if (segments.getOrNull(1) == "link") query("code")?.takeIf { CODE.matches(it) }?.let { DeepLink.TelegramLink(it) } else null
                    "open-riddle" -> query("puzzleId")?.takeIf { it.isNotBlank() }?.let { DeepLink.Riddle(it) }
                    else -> null
                }
            }
        }
        return null
    }

    /** The src label for the deeplink_open event. */
    fun channelOf(link: DeepLink): String = when (link) {
        is DeepLink.Game -> link.src ?: "link"
        is DeepLink.PlaySlug -> link.src ?: "link"
        is DeepLink.PlayIndex -> link.src ?: "link"
        is DeepLink.TelegramLink -> "telegram_link"
        is DeepLink.Riddle -> "riddle"
    }

    /** The puzzle type to report for the deeplink_open event ("app" when the link is not about one game). */
    fun targetOf(link: DeepLink): String = when (link) {
        is DeepLink.PlaySlug -> link.slug
        is DeepLink.Game -> "app"
        else -> "app"
    }
}
