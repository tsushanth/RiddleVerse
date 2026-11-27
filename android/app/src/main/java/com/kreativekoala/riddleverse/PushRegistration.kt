package com.kreativekoala.riddleverse

import android.content.Context
import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.messaging.FirebaseMessaging
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.Executors

/** Registers this device's push token for the signed-in account. Guests have no account, so nothing is sent. */
object PushRegistration {
    private const val TAG = "PushRegistration"
    private const val URL = "https://puzzleverseai.com/register-fcm-token"
    private val io = Executors.newSingleThreadExecutor { r -> Thread(r, "push-registration").apply { isDaemon = true } }
    /** The account whose token this process already sent. */
    @Volatile private var sentForUid: String? = null

    /**
     * [reminderChosen]: the player has answered the reminder question (on, off or "Not now"). The server must then not
     * send its own daily reminder: either the on-device one covers this player or they said no. Only ever sends false.
     */
    fun payload(email: String, uid: String, token: String, language: String, reminderChosen: Boolean): Map<String, Any> =
        buildMap {
            put("userEmail", email)
            put("userId", uid)
            put("fcmToken", token)
            put("platform", "android")
            put("language", language)
            if (reminderChosen) put("serverDailyReminder", false)
        }

    /** Registers only when this process has not yet done so for the signed-in account (right after a sign-in). */
    fun registerIfNewAccount(context: Context) {
        try {
            val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
            if (uid != sentForUid) registerCurrent(context)
        } catch (e: Exception) {
            Log.w(TAG, "Push registration failed: ${e.javaClass.simpleName}")
        }
    }

    /** Fetches the current token and registers it. Safe to call often; never throws. */
    fun registerCurrent(context: Context) {
        val app = context.applicationContext
        try {
            if (FirebaseAuth.getInstance().currentUser == null) return
            FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
                if (task.isSuccessful) send(app, task.result)
                else Log.w(TAG, "Fetching the push token failed", task.exception)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Push registration failed: ${e.javaClass.simpleName}")
        }
    }

    fun send(context: Context, token: String) {
        val app = context.applicationContext
        val user = FirebaseAuth.getInstance().currentUser
        val email = user?.email ?: return
        val uid = user.uid
        sentForUid = uid
        io.execute {
            try {
                val chosen = DailyReminder.offerShown(app) || DailyReminder.isEnabled(app)
                val body = JSONObject(payload(email, uid, token, ContentLanguage.current(), chosen)).toString()
                val request = Request.Builder().url(URL)
                    .post(body.toRequestBody("application/json".toMediaType()))
                    .build()
                HttpClientProvider.client.newCall(request).execute().use {
                    if (!it.isSuccessful) { sentForUid = null; Log.w(TAG, "Push token registration answered ${it.code}") }
                }
            } catch (e: Exception) {
                sentForUid = null
                Log.w(TAG, "Push registration failed: ${e.javaClass.simpleName}")
            }
        }
    }
}
