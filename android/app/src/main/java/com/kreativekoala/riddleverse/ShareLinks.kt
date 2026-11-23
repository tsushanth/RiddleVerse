package com.kreativekoala.riddleverse

import android.content.Context
import android.content.Intent

/**
 * The one place that builds the links people share. Every shared link is a playable web page
 * (https://puzzleverseai.com/play/<puzzle type or custom game id>) so a friend without the app can play at once; with the app
 * installed, Android App Links open the app instead (see AndroidManifest and [DeepLinkRouter]). The link carries
 * src=share plus utm tags; the server counts the opens (share_open) and the app counts the sends (share_sent).
 */
object ShareLinks {
    const val ORIGIN = "https://puzzleverseai.com"
    private val SLUG = Regex("^[a-z]{2,24}$")
    private val GAME_ID = Regex("^[0-9a-fA-F-]{36}$")
    private val LABEL = Regex("^[a-z0-9_]{1,24}$")
    private val START = Regex("^[A-Za-z0-9_-]{1,64}$")

    /** Telegram, WhatsApp, or anything else the user picked in the share sheet. */
    const val CHANNEL_TELEGRAM = "telegram"
    const val CHANNEL_WHATSAPP = "whatsapp"
    const val CHANNEL_OTHER = "other"

    /** A puzzle type key (letters only), a custom game id (UUID) or null for the game list. Anything else falls back to /play. */
    fun playUrl(target: String?, channel: String, medium: String = "app_share"): String {
        val t = target?.trim().orEmpty()
        val path = when {
            SLUG.matches(t) || GAME_ID.matches(t) -> "/play/$t" // i18n-ok: URL path
            else -> "/play" // i18n-ok: URL path
        }
        val ch = channel.takeIf { LABEL.matches(it) } ?: CHANNEL_OTHER
        val med = medium.takeIf { LABEL.matches(it) } ?: "app_share"
        val campaign = if (SLUG.matches(t)) t else if (GAME_ID.matches(t)) "custom" else "index"
        return "$ORIGIN$path?src=share&utm_source=$ch&utm_medium=$med&utm_campaign=$campaign"
    }

    /** The puzzle type to report in share_sent for a share target (custom game ids are reported as "custom"). */
    fun eventTarget(target: String?): String? = target?.trim()?.takeIf { SLUG.matches(it) }

    /** https://t.me/Riddleverse_bot, with an optional /start payload (g_<slug>, daily, link). Bad payloads are dropped. */
    fun botUrl(start: String? = null): String {
        val s = start?.takeIf { START.matches(it) }
        return "https://t.me/${TelegramPromo.BOT_USERNAME}" + (if (s != null) "?start=$s" else "")
    }

    /** Opens the Android share sheet with [text] and counts one share_sent. Never throws. */
    fun shareText(context: Context, text: String, target: String?, chooserTitle: String?, channel: String = CHANNEL_OTHER) {
        try {
            PuzzleEvents.shareSent(eventTarget(target) ?: "custom", channel)
            val send = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, text)
            }
            context.startActivity(Intent.createChooser(send, chooserTitle).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (_: Throwable) { }
    }
}
