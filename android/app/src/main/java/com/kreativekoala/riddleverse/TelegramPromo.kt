package com.kreativekoala.riddleverse

import android.content.Context
import android.content.Intent
import android.net.Uri

/**
 * Rules and plumbing for the Telegram bot promotion (@Riddleverse_bot): the Home banner, the result-screen card, the daily
 * card and the Settings rows. The bot lives on the same backend as the app; /link in the bot connects the Telegram account
 * to the app account (see [TelegramLinkManager]).
 */
object TelegramPromo {
    const val BOT_USERNAME = "Riddleverse_bot"
    const val BANNER_RESHOW_DAYS = 14
    /** After the player opened the bot from any promo, stay quiet for this long. */
    const val QUIET_AFTER_OPEN_DAYS = 45
    private const val DAY_MS = 86_400_000L
    private const val PREFS = "telegram_promo"
    private const val KEY_DISMISSED_AT = "banner_dismissed_at"
    private const val KEY_OPENED_AT = "opened_at"

    /** Pure rule for the Home banner. A dismissal hides it for [BANNER_RESHOW_DAYS]; opening the bot hides it for [QUIET_AFTER_OPEN_DAYS]. */
    fun bannerVisible(now: Long, dismissedAt: Long, openedAt: Long): Boolean {
        if (openedAt > 0 && now - openedAt < QUIET_AFTER_OPEN_DAYS * DAY_MS) return false
        if (dismissedAt > 0 && now - dismissedAt < BANNER_RESHOW_DAYS * DAY_MS) return false
        return true
    }

    /** Pure rule for the daily / result cards: shown unless the player opened the bot recently. */
    fun cardVisible(now: Long, openedAt: Long): Boolean = openedAt <= 0 || now - openedAt >= QUIET_AFTER_OPEN_DAYS * DAY_MS

    private fun prefs(c: Context) = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun isBannerVisible(c: Context, now: Long = System.currentTimeMillis()): Boolean {
        migrateLegacyDismissal(c, now)
        return bannerVisible(now, prefs(c).getLong(KEY_DISMISSED_AT, 0L), prefs(c).getLong(KEY_OPENED_AT, 0L))
    }

    /** Before the re-show rule a dismissal was forever (boolean). Count that as "dismissed now", so the banner returns once after [BANNER_RESHOW_DAYS]. */
    private fun migrateLegacyDismissal(c: Context, now: Long) {
        val legacy = c.getSharedPreferences("telegram_bot_banner", Context.MODE_PRIVATE)
        if (legacy.getBoolean("banner_dismissed", false)) {
            if (prefs(c).getLong(KEY_DISMISSED_AT, 0L) == 0L) prefs(c).edit().putLong(KEY_DISMISSED_AT, now).apply()
            legacy.edit().remove("banner_dismissed").apply()
        }
    }

    fun isCardVisible(c: Context, now: Long = System.currentTimeMillis()): Boolean =
        cardVisible(now, prefs(c).getLong(KEY_OPENED_AT, 0L))

    fun dismissBanner(c: Context, now: Long = System.currentTimeMillis()) {
        prefs(c).edit().putLong(KEY_DISMISSED_AT, now).apply()
    }

    /**
     * Opens the bot (native Telegram if installed, else the browser). [start] is the /start payload: null, "daily", "link" or
     * "g_<puzzle type>". [surface] labels where the tap came from for the telegram_cta event.
     */
    fun open(c: Context, start: String?, surface: String) {
        try {
            PuzzleEvents.telegramCta(surface)
            prefs(c).edit().putLong(KEY_OPENED_AT, System.currentTimeMillis()).apply()
        } catch (_: Throwable) { }
        val web = Uri.parse(ShareLinks.botUrl(start))
        val native = Intent(Intent.ACTION_VIEW, Uri.parse("tg://resolve?domain=$BOT_USERNAME" + (start?.takeIf { it.matches(Regex("^[A-Za-z0-9_-]{1,64}$")) }?.let { "&start=$it" } ?: "")))
            .setPackage("org.telegram.messenger").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            c.startActivity(native)
        } catch (_: Exception) {
            try {
                c.startActivity(Intent(Intent.ACTION_VIEW, web).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            } catch (_: Exception) {
                android.widget.Toast.makeText(c, c.getString(R.string.browse_bot_telegram_open_failed, BOT_USERNAME), android.widget.Toast.LENGTH_LONG).show()
            }
        }
    }
}
