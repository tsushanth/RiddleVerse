package com.kreativekoala.riddleverse

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class MyFirebaseMessagingService : FirebaseMessagingService() {

    companion object {
        private const val TAG = "FCMService"
        private const val CHANNEL_ID = "puzzle_notifications"
        private const val CHANNEL_NAME = "Daily Puzzle Notifications"
        private const val CHANNEL_DESCRIPTION = "Notifications for new daily puzzles"
        private const val SERVER_URL = "https://puzzleverseai.com" // Replace with your server URL
    }

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d(TAG, "New FCM token received: $token")

        // Send token to your server
        sendTokenToServer(token)
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)
        Log.d(TAG, "Message received from: ${remoteMessage.from}")

        // Handle different types of messages
        when (remoteMessage.data["type"]) {
            "daily_puzzles" -> handleDailyPuzzleNotification(remoteMessage)
            "puzzle_ready" -> handlePuzzleReadyNotification(remoteMessage)
            "test" -> handleTestNotification(remoteMessage)
            else -> handleGenericNotification(remoteMessage)
        }
    }

    private fun handlePuzzleReadyNotification(remoteMessage: RemoteMessage) {
        val title = remoteMessage.notification?.title ?: "🎯 Puzzles Ready!"
        val body = remoteMessage.notification?.body ?: "New puzzles are now available!"

        val puzzleType = remoteMessage.data["puzzleType"]
        val difficulty = remoteMessage.data["difficulty"]

        Log.d(TAG, "Puzzle ready notification - Type: $puzzleType, Difficulty: $difficulty")

        // Create intent to open the home screen (where users can access the new puzzles)
        val intent = Intent(this, HomeActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("notification_type", "puzzle_ready")
            putExtra("puzzle_type", puzzleType)
            putExtra("difficulty", difficulty)
            // Optional: Add a flag to highlight the specific puzzle type that's ready
            putExtra("highlight_puzzle_type", puzzleType)
        }

        showNotification(
            title = title,
            message = body,
            intent = intent,
            notificationId = 4 // Use a unique ID for puzzle ready notifications
        )
    }

    private fun handleDailyPuzzleNotification(remoteMessage: RemoteMessage) {
        val title = remoteMessage.notification?.title ?: "🧩 New Daily Puzzles Ready!"
        val body = remoteMessage.notification?.body ?: "Fresh puzzles are waiting for you!"

        val topics = remoteMessage.data["topics"]
        val puzzleCount = remoteMessage.data["puzzleCount"]
        val userEmail = remoteMessage.data["userEmail"]

        Log.d(TAG, "Daily puzzle notification - Topics: $topics, Count: $puzzleCount")

        // Create intent to open the daily puzzles section
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("open_daily_puzzles", true)
            putExtra("user_email", userEmail)
            putExtra("notification_type", "daily_puzzles")
        }

        showNotification(
            title = title,
            message = body,
            intent = intent,
            notificationId = 1
        )
    }

    private fun handleTestNotification(remoteMessage: RemoteMessage) {
        val title = remoteMessage.notification?.title ?: "🧪 Test Notification"
        val body = remoteMessage.notification?.body ?: "This is a test notification!"

        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("notification_type", "test")
        }

        showNotification(
            title = title,
            message = body,
            intent = intent,
            notificationId = 2
        )
    }

    private fun handleGenericNotification(remoteMessage: RemoteMessage) {
        val title = remoteMessage.notification?.title ?: "PuzzleVerse"
        val body = remoteMessage.notification?.body ?: "You have a new notification"

        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        showNotification(
            title = title,
            message = body,
            intent = intent,
            notificationId = 3
        )
    }

    private fun showNotification(
        title: String,
        message: String,
        intent: Intent,
        notificationId: Int
    ) {
        val pendingIntent = PendingIntent.getActivity(
            this,
            notificationId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notificationBuilder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(message)
            .setSmallIcon(R.drawable.ic_notification)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(notificationId, notificationBuilder.build())

        Log.d(TAG, "Notification shown with ID: $notificationId")
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = CHANNEL_DESCRIPTION
                enableLights(true)
                enableVibration(true)
                setShowBadge(true)
            }

            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)

            Log.d(TAG, "Notification channel created: $CHANNEL_ID")
        }
    }

    private fun sendTokenToServer(token: String) {
        val userEmail = FirebaseAuth.getInstance().currentUser?.email
        val userId = FirebaseAuth.getInstance().currentUser?.uid

        if (userEmail == null || userId == null) {
            Log.w(TAG, "User not authenticated, cannot send token to server")
            return
        }

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val requestBody = JSONObject().apply {
                    put("userEmail", userEmail)
                    put("userId", userId)
                    put("fcmToken", token)
                    put("platform", "android")
                }.toString()

                val request = Request.Builder()
                    .url("$SERVER_URL/register-fcm-token")
                    .post(requestBody.toRequestBody("application/json".toMediaType()))
                    .addHeader("Content-Type", "application/json")
                    .build()

                val response = httpClient.newCall(request).execute()

                if (response.isSuccessful) {
                    val responseBody = response.body?.string()
                    Log.d(TAG, "FCM token sent to server successfully: $responseBody")
                } else {
                    val errorBody = response.body?.string()
                    Log.e(TAG, "Failed to send FCM token to server: ${response.code} - ${response.message} - $errorBody")
                }

                response.close()

            } catch (e: Exception) {
                Log.e(TAG, "Error sending FCM token to server: ${e.message}", e)
            }
        }
    }

    // Public method to manually send token (call this from your activities)
    fun sendCurrentTokenToServer() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                com.google.firebase.messaging.FirebaseMessaging.getInstance().token
                    .addOnCompleteListener { task ->
                        if (!task.isSuccessful) {
                            Log.w(TAG, "Fetching FCM registration token failed", task.exception)
                            return@addOnCompleteListener
                        }

                        // Get new FCM registration token
                        val token = task.result
                        Log.d(TAG, "Current FCM token: $token")
                        sendTokenToServer(token)
                    }
            } catch (e: Exception) {
                Log.e(TAG, "Error getting current FCM token", e)
            }
        }
    }
}