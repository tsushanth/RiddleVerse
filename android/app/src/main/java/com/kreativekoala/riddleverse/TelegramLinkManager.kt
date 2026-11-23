package com.kreativekoala.riddleverse

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

/**
 * Connects the app to a Telegram account with the one-time code the bot sends for /link.
 *
 * The code is exchanged on the server (POST /api/telegram/link/redeem):
 *  - signed in (also as a guest): the Telegram account is linked to THIS account, nothing else changes;
 *  - signed out: the server answers with a Firebase custom token for the account bound to that Telegram user, and the app signs in with it.
 * This never grants premium or removes ads: those come from the store purchase (RevenueCat), not from how someone logged in.
 */
object TelegramLinkManager {
    private const val TAG = "TelegramLink"

    sealed class Result {
        object SignedIn : Result()
        object Linked : Result()
        data class Failed(val reason: String) : Result()
    }

    suspend fun redeem(code: String): Result = withContext(Dispatchers.IO) {
        try {
            val auth = FirebaseAuth.getInstance()
            // An ID token only when someone is signed in; failure to get one means "treat as signed out".
            val idToken = try { auth.currentUser?.getIdToken(false)?.await()?.token } catch (_: Exception) { null }
            val body = JSONObject().put("code", code).toString().toRequestBody("application/json".toMediaType())
            val req = Request.Builder().url("${BuildConfig.API_BASE_URL}/api/telegram/link/redeem").post(body)
                .apply { if (!idToken.isNullOrEmpty()) header("Authorization", "Bearer $idToken") }.build()
            HttpClientProvider.client.newCall(req).execute().use { res ->
                val text = res.body?.string().orEmpty()
                if (!res.isSuccessful) {
                    val err = try { JSONObject(text).optString("error", "failed") } catch (_: Exception) { "failed" }
                    Log.w(TAG, "redeem failed: ${res.code} $err")
                    return@withContext Result.Failed(err)
                }
                val json = JSONObject(text)
                when (json.optString("mode")) {
                    "linked" -> Result.Linked
                    "signin" -> {
                        val token = json.optString("customToken")
                        if (token.isBlank()) return@withContext Result.Failed("no_token")
                        val user = auth.signInWithCustomToken(token).await().user
                        if (user != null) Result.SignedIn else Result.Failed("sign_in_failed")
                    }
                    else -> Result.Failed("bad_response")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "redeem error", e)
            Result.Failed("network")
        }
    }
}
