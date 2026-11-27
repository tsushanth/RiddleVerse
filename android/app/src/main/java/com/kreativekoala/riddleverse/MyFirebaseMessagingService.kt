package com.kreativekoala.riddleverse

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

class MyFirebaseMessagingService : FirebaseMessagingService() {

    companion object {
        private const val TAG = "FCMService"
        private const val CHANNEL_ID = "puzzle_notifications"
        private const val CHANNEL_NAME = "Daily Puzzle Notifications"
        private const val CHANNEL_DESCRIPTION = "Notifications for new daily puzzles"
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d(TAG, "New FCM token received: $token")

        PushRegistration.send(this, token)
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)
        Log.d(TAG, "Message received from: ${remoteMessage.from}")

        // Handle different types of messages
        when (remoteMessage.data["type"]) {
            "daily_puzzles" -> handleDailyPuzzleNotification(remoteMessage)
            "puzzle_ready" -> handlePuzzleReadyNotification(remoteMessage)
            "test" -> handleTestNotification(remoteMessage)
            DailyReminder.PUSH_TYPE -> handleDailyReminder(remoteMessage)
            else -> handleGenericNotification(remoteMessage)
        }
    }

    private fun handlePuzzleReadyNotification(remoteMessage: RemoteMessage) {
        val title = remoteMessage.notification?.title ?: "🎯 " + DailyReminder.localized(this).getString(R.string.xb_notif_puzzles_ready_title)
        val body = remoteMessage.notification?.body ?: DailyReminder.localized(this).getString(R.string.xb_notif_puzzles_ready_body)

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
        val title = remoteMessage.notification?.title ?: "🧩 " + DailyReminder.localized(this).getString(R.string.xb_notif_daily_puzzles_title)
        val body = remoteMessage.notification?.body ?: DailyReminder.localized(this).getString(R.string.xb_notif_daily_puzzles_body)

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

    /**
     * The server's daily reminder, received while the app is in the foreground (in the background the system shows it).
     * Dropped when the on-device reminder covers this player or today's puzzle is already solved.
     */
    private fun handleDailyReminder(remoteMessage: RemoteMessage) {
        val title = remoteMessage.notification?.title ?: return
        val body = remoteMessage.notification?.body ?: return
        if (DailyReminder.isEnabled(this) || DailyPuzzleStore.resultFor(this, DailyPuzzleStore.today()) != null) return
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("type", DailyReminder.PUSH_TYPE)
        }
        showNotification(title = title, message = body, intent = intent, notificationId = 5)
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
        val body = remoteMessage.notification?.body ?: DailyReminder.localized(this).getString(R.string.xb_notif_generic_body)

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
}
