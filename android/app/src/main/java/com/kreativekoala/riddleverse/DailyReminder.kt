package com.kreativekoala.riddleverse

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.TaskStackBuilder
import java.util.Calendar

// --- daily reminder --- ONE local notification per day, opt-in, user controlled from Settings.
// No server, no account, no network, no push token. Inexact alarm (no SCHEDULE_EXACT_ALARM). Skipped when
// today's puzzle is already solved, and paused by itself after [ReminderPolicy.MAX_UNOPENED] reminders that the
// player never opened the app for, so it can never turn into spam.

/** Pure rules, JVM-testable. */
object ReminderPolicy {
    const val DEFAULT_HOUR = 12
    val HOUR_CHOICES = listOf(8, 12, 18, 20)
    /** After this many reminders in a row with no app open in between, stop until the player opens the app again. */
    const val MAX_UNOPENED = 5

    fun shouldSend(
        enabled: Boolean,
        notificationsAllowed: Boolean,
        solvedToday: Boolean,
        unopenedCount: Int,
    ): Boolean = enabled && notificationsAllowed && !solvedToday && unopenedCount < MAX_UNOPENED

    /** Text for the reminder; mentions the streak only when there is one worth keeping. Plain, no pressure. */
    fun message(streak: Int): Pair<String, String> =
        if (streak >= 2) "Keep your $streak-day streak going" to "Today's puzzle is ready. It takes a couple of minutes."
        else "Today's puzzle is ready" to "One puzzle, the same for everyone. It takes a couple of minutes."

    /** Next occurrence of [hour]:00 local time strictly after [nowMs]. */
    fun nextTriggerAt(nowMs: Long, hour: Int, zone: java.util.TimeZone = java.util.TimeZone.getDefault()): Long {
        val cal = Calendar.getInstance(zone).apply {
            timeInMillis = nowMs
            set(Calendar.HOUR_OF_DAY, hour.coerceIn(0, 23)); set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }
        if (cal.timeInMillis <= nowMs) cal.add(Calendar.DAY_OF_YEAR, 1)
        return cal.timeInMillis
    }
}

object DailyReminder {
    private const val PREFS = "daily_reminder_v1"
    private const val K_ENABLED = "enabled"
    private const val K_HOUR = "hour"
    private const val K_OFFER_SHOWN = "offer_shown"
    private const val K_UNOPENED = "unopened"
    const val CHANNEL_ID = "daily_reminder"
    private const val NOTIF_ID = 7101
    private const val REQ_ALARM = 7102
    private const val REQ_OPEN = 7103

    private fun prefs(c: Context): SharedPreferences = c.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun isEnabled(c: Context): Boolean = prefs(c).getBoolean(K_ENABLED, false)
    fun hour(c: Context): Int = prefs(c).getInt(K_HOUR, ReminderPolicy.DEFAULT_HOUR)
    fun offerShown(c: Context): Boolean = prefs(c).getBoolean(K_OFFER_SHOWN, false)
    fun markOfferShown(c: Context) { prefs(c).edit().putBoolean(K_OFFER_SHOWN, true).apply() }
    fun unopenedCount(c: Context): Int = prefs(c).getInt(K_UNOPENED, 0)

    /** Android 13+ needs the POST_NOTIFICATIONS runtime permission; older versions only need the user not to have blocked us. */
    fun notificationsAllowed(c: Context): Boolean = NotificationManagerCompat.from(c).areNotificationsEnabled()

    fun needsRuntimePermission(c: Context): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            androidx.core.content.ContextCompat.checkSelfPermission(c, android.Manifest.permission.POST_NOTIFICATIONS) !=
            android.content.pm.PackageManager.PERMISSION_GRANTED

    fun setEnabled(c: Context, enabled: Boolean) {
        prefs(c).edit().putBoolean(K_ENABLED, enabled).putInt(K_UNOPENED, 0).apply()
        if (enabled) schedule(c) else cancel(c)
    }

    fun setHour(c: Context, hour: Int) {
        prefs(c).edit().putInt(K_HOUR, hour.coerceIn(0, 23)).apply()
        if (isEnabled(c)) schedule(c)
    }

    /** Call whenever the app comes to the foreground: a player who opens the app is not ignoring the reminders. */
    fun onAppOpened(c: Context) {
        val p = prefs(c)
        if (p.getInt(K_UNOPENED, 0) != 0) p.edit().putInt(K_UNOPENED, 0).apply()
    }

    /** Idempotent: replaces any existing alarm. Call on app start, boot, update and time zone change. */
    fun schedule(c: Context) {
        if (!isEnabled(c)) return
        val am = c.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val first = ReminderPolicy.nextTriggerAt(System.currentTimeMillis(), hour(c))
        am.setInexactRepeating(AlarmManager.RTC, first, AlarmManager.INTERVAL_DAY, alarmIntent(c))
    }

    fun cancel(c: Context) {
        (c.getSystemService(Context.ALARM_SERVICE) as? AlarmManager)?.cancel(alarmIntent(c))
        NotificationManagerCompat.from(c).cancel(NOTIF_ID)
    }

    private fun alarmIntent(c: Context): PendingIntent = PendingIntent.getBroadcast(
        c.applicationContext, REQ_ALARM,
        Intent(c.applicationContext, DailyReminderReceiver::class.java).setAction(DailyReminderReceiver.ACTION),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    /** Runs on the alarm. Posts at most one notification and never throws. */
    fun fire(c: Context) {
        try {
            val today = DailyPuzzleStore.today()
            val allowed = shouldPost(c, today)
            if (!allowed) return
            ensureChannel(c)
            val streak = DailyPuzzleStore.displayStreak(c, today)
            val (title, text) = ReminderPolicy.message(streak)
            val open = TaskStackBuilder.create(c)
                .addNextIntent(Intent(c, MainActivity::class.java))
                .addNextIntent(Intent(c, DailyPuzzleActivity::class.java))
                .getPendingIntent(REQ_OPEN, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            val n = NotificationCompat.Builder(c, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(title)
                .setContentText(text)
                .setContentIntent(open)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setCategory(NotificationCompat.CATEGORY_REMINDER)
                .build()
            @Suppress("MissingPermission") // checked in shouldPost via areNotificationsEnabled
            NotificationManagerCompat.from(c).notify(NOTIF_ID, n)
            prefs(c).edit().putInt(K_UNOPENED, unopenedCount(c) + 1).apply()
        } catch (_: Throwable) { }
    }

    private fun shouldPost(c: Context, today: Long): Boolean = ReminderPolicy.shouldSend(
        enabled = isEnabled(c),
        notificationsAllowed = notificationsAllowed(c) && !needsRuntimePermission(c),
        solvedToday = DailyPuzzleStore.resultFor(c, today) != null,
        unopenedCount = unopenedCount(c),
    )

    private fun ensureChannel(c: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = c.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (nm.getNotificationChannel(CHANNEL_ID) != null) return
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Daily puzzle reminder", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "One reminder a day for the daily puzzle. You can turn it off in Settings."
            }
        )
    }
}

class DailyReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action == ACTION) DailyReminder.fire(context)
    }
    companion object { const val ACTION = "com.kreativekoala.riddleverse.DAILY_REMINDER" }
}

/** Alarms do not survive a reboot, an app update or a time zone change. */
class DailyReminderBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        when (intent?.action) {
            Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED, Intent.ACTION_TIMEZONE_CHANGED ->
                DailyReminder.schedule(context)
        }
    }
}
