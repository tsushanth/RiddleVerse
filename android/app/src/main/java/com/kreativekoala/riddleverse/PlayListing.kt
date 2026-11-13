package com.kreativekoala.riddleverse

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log

/**
 * Plain "Rate RiddleVerse" link to our own Play listing, used by the Settings row.
 * Automatic rating prompts are owned by RatingKit; this is only a user-initiated shortcut.
 */
object PlayListing {
    fun marketUrl(packageName: String): String = "market://details?id=$packageName"

    fun webUrl(packageName: String): String = "https://play.google.com/store/apps/details?id=$packageName"

    /** Tries the Play Store app first, then the web listing. Returns false if neither could be opened. */
    fun open(context: Context): Boolean {
        val pkg = context.packageName
        for (url in listOf(marketUrl(pkg), webUrl(pkg))) {
            try {
                context.startActivity(
                    Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
                return true
            } catch (e: Exception) {
                Log.w("PlayListing", "Could not open $url", e)
            }
        }
        return false
    }
}
