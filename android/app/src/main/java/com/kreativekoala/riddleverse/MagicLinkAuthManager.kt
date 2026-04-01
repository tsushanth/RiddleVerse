package com.kreativekoala.riddleverse

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.tasks.await

/**
 * Manages magic-link authentication sessions originating from Telegram/WhatsApp bots.
 *
 * When a user authenticates via a bot, a custom Firebase token is provided through
 * the `riddleverse://auth?token=<token>` deep link. This manager handles sign-in
 * and persists the magic-link session flag so SubscriptionManager can grant premium access.
 */
object MagicLinkAuthManager {
    private const val TAG = "MagicLinkAuth"
    private const val PREFS_NAME = "magic_link_auth"
    private const val KEY_IS_MAGIC_LINK_SESSION = "is_magic_link_session"

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /**
     * Attempt sign-in with a Firebase custom token received via magic link.
     * On success, marks this session as a magic-link session.
     */
    suspend fun signInWithToken(context: Context, token: String): Boolean {
        return try {
            val result = FirebaseAuth.getInstance().signInWithCustomToken(token).await()
            val user = result.user
            if (user != null) {
                prefs(context).edit().putBoolean(KEY_IS_MAGIC_LINK_SESSION, true).apply()
                Log.d(TAG, "Magic link sign-in successful: uid=${user.uid}")
                true
            } else {
                Log.e(TAG, "Magic link sign-in returned null user")
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Magic link sign-in failed", e)
            false
        }
    }

    /**
     * Returns true if the current session was established via a magic link
     * (bot-authenticated user who should be treated as premium).
     */
    fun isMagicLinkSession(context: Context): Boolean {
        return prefs(context).getBoolean(KEY_IS_MAGIC_LINK_SESSION, false)
    }

    /**
     * Clears magic link session flag (e.g. on explicit sign-out).
     */
    fun clearSession(context: Context) {
        prefs(context).edit().remove(KEY_IS_MAGIC_LINK_SESSION).apply()
        Log.d(TAG, "Magic link session cleared")
    }
}
