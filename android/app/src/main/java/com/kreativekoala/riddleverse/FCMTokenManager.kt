package com.kreativekoala.riddleverse

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class FCMTokenManager(private val context: Context) {

    companion object {
        private const val TAG = "FCMTokenManager"
        private const val PREFS_NAME = "fcm_token_prefs"
        private const val KEY_LAST_TOKEN = "last_token"
        private const val KEY_LAST_SENT_TIME = "last_sent_time"
        private const val SERVER_URL = "https://puzzleverseai.com"// Replace with your server URL

        // Send token to server every 24 hours minimum
        private const val TOKEN_REFRESH_INTERVAL = 24 * 60 * 60 * 1000L // 24 hours in milliseconds
    }

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private val sharedPrefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /**
     * Initialize FCM token management
     * Call this when user logs in or app starts
     */
    fun initializeTokenManagement() {
        Log.d(TAG, "Initializing FCM token management")

        FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
            if (!task.isSuccessful) {
                Log.w(TAG, "Fetching FCM registration token failed", task.exception)
                return@addOnCompleteListener
            }

            val token = task.result
            Log.d(TAG, "FCM Token retrieved: ${token.take(20)}...")

            // Send token to server
            sendTokenToServer(token, force = false)
        }
    }

    /**
     * Send FCM token to server
     * @param token The FCM token to send
     * @param force Whether to force send even if recently sent
     */
    fun sendTokenToServer(token: String, force: Boolean = false) {
        val userEmail = getCurrentUserEmail()

        if (userEmail == null) {
            Log.w(TAG, "User not authenticated, cannot send token to server")
            return
        }

        // Check if we need to send the token
        if (!force && !shouldSendToken(token)) {
            Log.d(TAG, "Token already sent recently, skipping")
            return
        }

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val success = sendTokenToServerInternal(userEmail, token)

                if (success) {
                    // Save the token and timestamp
                    saveTokenInfo(token, System.currentTimeMillis())
                    Log.d(TAG, "FCM token registered successfully with server")
                } else {
                    Log.e(TAG, "Failed to register FCM token with server")
                }

            } catch (e: Exception) {
                Log.e(TAG, "Error sending FCM token to server: ${e.message}", e)
            }
        }
    }

    /**
     * Force refresh and send token to server
     */
    fun forceRefreshToken() {
        Log.d(TAG, "Force refreshing FCM token")

        FirebaseMessaging.getInstance().deleteToken().addOnCompleteListener { deleteTask ->
            if (deleteTask.isSuccessful) {
                Log.d(TAG, "Old token deleted successfully")

                // Get new token
                FirebaseMessaging.getInstance().token.addOnCompleteListener { getTask ->
                    if (getTask.isSuccessful) {
                        val newToken = getTask.result
                        Log.d(TAG, "New FCM token retrieved: ${newToken.take(20)}...")
                        sendTokenToServer(newToken, force = true)
                    } else {
                        Log.e(TAG, "Failed to get new FCM token", getTask.exception)
                    }
                }
            } else {
                Log.e(TAG, "Failed to delete old FCM token", deleteTask.exception)
            }
        }
    }

    /**
     * Send test notification to current user
     */
    fun sendTestNotification(
        title: String = "🧪 Test Notification",
        body: String = "This is a test notification from PuzzleVerse!",
        callback: (Boolean) -> Unit
    ) {
        val userEmail = getCurrentUserEmail()

        if (userEmail == null) {
            Log.w(TAG, "User not authenticated, cannot send test notification")
            callback(false)
            return
        }

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val requestBody = JSONObject().apply {
                    put("userEmail", userEmail)
                    put("title", title)
                    put("body", body)
                }.toString()

                val request = Request.Builder()
                    .url("$SERVER_URL/test-notification")
                    .post(requestBody.toRequestBody("application/json".toMediaType()))
                    .addHeader("Content-Type", "application/json")
                    .build()

                val response = httpClient.newCall(request).execute()
                val success = response.isSuccessful

                if (success) {
                    val responseBody = response.body?.string()
                    Log.d(TAG, "Test notification sent successfully: $responseBody")
                } else {
                    val errorBody = response.body?.string()
                    Log.e(TAG, "Failed to send test notification: ${response.code} - $errorBody")
                }

                response.close()

                withContext(Dispatchers.Main) {
                    callback(success)
                }

            } catch (e: Exception) {
                Log.e(TAG, "Error sending test notification: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    callback(false)
                }
            }
        }
    }

    /**
     * Check server token registration status
     */
    fun checkTokenRegistrationStatus(callback: (Boolean) -> Unit) {
        val userEmail = getCurrentUserEmail()

        if (userEmail == null) {
            callback(false)
            return
        }

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val request = Request.Builder()
                    .url("$SERVER_URL/check-token-status?userEmail=${userEmail}")
                    .get()
                    .build()

                val response = httpClient.newCall(request).execute()
                val success = response.isSuccessful

                if (success) {
                    val responseBody = response.body?.string()
                    Log.d(TAG, "Token status check response: $responseBody")
                } else {
                    Log.e(TAG, "Token status check failed: ${response.code}")
                }

                response.close()

                withContext(Dispatchers.Main) {
                    callback(success)
                }

            } catch (e: Exception) {
                Log.e(TAG, "Error checking token status: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    callback(false)
                }
            }
        }
    }

    // Private helper methods
    private suspend fun sendTokenToServerInternal(userEmail: String, token: String): Boolean {
        return try {
            val requestBody = JSONObject().apply {
                put("userEmail", userEmail)
                put("userId", FirebaseAuth.getInstance().currentUser?.uid)
                put("fcmToken", token)
                put("platform", "android")
                put("timestamp", System.currentTimeMillis())
            }.toString()

            val request = Request.Builder()
                .url("$SERVER_URL/register-fcm-token")
                .post(requestBody.toRequestBody("application/json".toMediaType()))
                .addHeader("Content-Type", "application/json")
                .addHeader("User-Agent", "PuzzleVerse-Android")
                .build()

            val response = httpClient.newCall(request).execute()

            val success = response.isSuccessful

            if (success) {
                val responseBody = response.body?.string()
                Log.d(TAG, "Server response: $responseBody")
            } else {
                val errorBody = response.body?.string()
                Log.e(TAG, "Server error: ${response.code} - ${response.message} - $errorBody")
            }

            response.close()
            success

        } catch (e: Exception) {
            Log.e(TAG, "Network error sending token: ${e.message}", e)
            false
        }
    }

    private fun shouldSendToken(token: String): Boolean {
        val lastToken = sharedPrefs.getString(KEY_LAST_TOKEN, null)
        val lastSentTime = sharedPrefs.getLong(KEY_LAST_SENT_TIME, 0)
        val currentTime = System.currentTimeMillis()

        // Send if token is different or if enough time has passed
        return lastToken != token || (currentTime - lastSentTime) > TOKEN_REFRESH_INTERVAL
    }

    private fun saveTokenInfo(token: String, timestamp: Long) {
        sharedPrefs.edit()
            .putString(KEY_LAST_TOKEN, token)
            .putLong(KEY_LAST_SENT_TIME, timestamp)
            .apply()
    }

    private fun getCurrentUserEmail(): String? {
        return FirebaseAuth.getInstance().currentUser?.email
    }

    /**
     * Get last known token from preferences
     */
    fun getLastKnownToken(): String? {
        return sharedPrefs.getString(KEY_LAST_TOKEN, null)
    }

    /**
     * Clear stored token info (call on logout)
     */
    fun clearTokenInfo() {
        sharedPrefs.edit()
            .remove(KEY_LAST_TOKEN)
            .remove(KEY_LAST_SENT_TIME)
            .apply()
        Log.d(TAG, "Token info cleared")
    }
}