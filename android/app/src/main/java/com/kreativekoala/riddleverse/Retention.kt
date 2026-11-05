package com.kreativekoala.riddleverse

import android.app.Application
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kreativekoala.riddleverse.ui.theme.*
import java.util.TimeZone

// --- retention --- welcome-back state for returning players, the reminder opt-in card shown after a solve,
// and the reminder switch in Settings. Everything is local; nothing here needs an account.

/** Pure rules for the welcome-back card, JVM-testable. Days are local epoch days. */
object WelcomeBackRules {
    /** Away this many whole days (or more) counts as "returning". */
    const val AWAY_AFTER_DAYS = 3

    fun awayDays(lastOpenDay: Long?, today: Long): Int {
        if (lastOpenDay == null || lastOpenDay < 0) return 0
        val gap = today - lastOpenDay
        return if (gap >= AWAY_AFTER_DAYS) gap.coerceAtMost(3650).toInt() else 0
    }

    /** Body text; never guilt-trips, never promises anything we cannot keep. */
    fun message(awayDays: Int, streakAlive: Int, bestStreak: Int, puzzleNumber: Int): String = when {
        streakAlive > 0 -> "You were away $awayDays days and your $streakAlive-day streak is still alive. Today's puzzle #$puzzleNumber is ready."
        bestStreak > 1 -> "It has been $awayDays days. Your best streak was $bestStreak days, and today's puzzle #$puzzleNumber is a good day to start a new one."
        else -> "It has been $awayDays days. Today's puzzle #$puzzleNumber is ready, the same for everyone."
    }
}

object Retention {
    private const val PREFS = "retention_v1"
    private const val K_LAST_OPEN_DAY = "last_open_local_day"
    private const val K_AWAY = "welcome_away_days"
    private const val K_OFFER_COUNT = "reminder_offer_count"
    private const val MAX_OFFERS = 3
    private var started = 0

    private fun prefs(c: Context): SharedPreferences = c.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private fun localDay(ms: Long): Long = Math.floorDiv(ms + TimeZone.getDefault().getOffset(ms), 24 * 3600 * 1000L)

    /** Registers a foreground hook: one "app opened" per process start. Never throws. */
    fun init(app: Application) {
        try {
            app.registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
                override fun onActivityCreated(a: Activity, b: Bundle?) {}
                override fun onActivityStarted(a: Activity) {
                    val first = synchronized(Retention) { started++ == 0 }
                    if (first) onForeground(a.applicationContext)
                }
                override fun onActivityResumed(a: Activity) {}
                override fun onActivityPaused(a: Activity) {}
                override fun onActivityStopped(a: Activity) { synchronized(Retention) { started = (started - 1).coerceAtLeast(0) } }
                override fun onActivitySaveInstanceState(a: Activity, b: Bundle) {}
                override fun onActivityDestroyed(a: Activity) {}
            })
        } catch (_: Throwable) { }
    }

    internal fun onForeground(c: Context) {
        try {
            val p = prefs(c)
            val today = localDay(System.currentTimeMillis())
            val last = p.getLong(K_LAST_OPEN_DAY, -1L).takeIf { it >= 0 }
            val away = WelcomeBackRules.awayDays(last, today)
            val e = p.edit().putLong(K_LAST_OPEN_DAY, today)
            // Keep an unseen card across several opens on the same day; a new away gap replaces it.
            if (away > 0) e.putInt(K_AWAY, away) else if (last != today) e.putInt(K_AWAY, 0)
            e.apply()
            DailyReminder.onAppOpened(c)
            DailyReminder.schedule(c) // idempotent; keeps the alarm alive on devices that dropped it
            Thread { Supporter.refresh(c) }.start() // flag file + entitlement, off the main thread
        } catch (_: Throwable) { }
    }

    /** Days the player was away, or 0 when there is nothing to welcome back (already dismissed, solved today, or not away). */
    fun pendingWelcomeBackDays(c: Context): Int {
        val away = prefs(c).getInt(K_AWAY, 0)
        if (away <= 0) return 0
        return if (DailyPuzzleStore.resultFor(c, DailyPuzzleStore.today()) != null) 0 else away
    }

    fun dismissWelcomeBack(c: Context) { prefs(c).edit().putInt(K_AWAY, 0).apply() }

    fun offerCount(c: Context): Int = prefs(c).getInt(K_OFFER_COUNT, 0)
    fun recordOfferShown(c: Context) { prefs(c).edit().putInt(K_OFFER_COUNT, offerCount(c) + 1).apply() }

    /** The reminder opt-in card is offered only until the player decides, and at most [MAX_OFFERS] times. */
    fun shouldOfferReminder(c: Context): Boolean =
        !DailyReminder.isEnabled(c) && !DailyReminder.offerShown(c) && offerCount(c) < MAX_OFFERS
}

/** Shown at the top of Home to someone who has been away a few days. Dismissible; the daily card sits right below it. */
@Composable
fun WelcomeBackCard(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var away by remember { mutableIntStateOf(Retention.pendingWelcomeBackDays(context)) }
    if (away <= 0) return
    LaunchedEffect(away) { Funnel.log("welcome_back_shown") }
    val day = DailyPuzzleStore.today()
    val spec = remember(day) { DailyPuzzleSchedule.specFor(day) }
    val streak = DailyPuzzleStore.displayStreak(context, day)
    val best = DailyPuzzleStore.streakState(context).best
    Card(
        modifier = modifier.fillMaxWidth().semantics { contentDescription = "Welcome back" },
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = RvSurfaceRaised),
        border = BorderStroke(2.dp, RvOutline),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Welcome back", fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, color = RvInk)
            Text(WelcomeBackRules.message(away, streak, best, spec.number), fontSize = 14.sp, color = RvInkSoft)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.clip(RoundedCornerShape(50)).background(RvSun)
                        .clickable {
                            Retention.dismissWelcomeBack(context); away = 0
                            context.startActivity(Intent(context, DailyPuzzleActivity::class.java))
                        }
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) { Text("Play today's puzzle", fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, color = RvOnTone) }
                Box(
                    Modifier.clip(RoundedCornerShape(50)).clickable { Retention.dismissWelcomeBack(context); away = 0 }
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) { Text("Dismiss", fontSize = 14.sp, color = RvInkSoft) }
            }
        }
    }
}

/**
 * Asks for the reminder with the reason in plain words, right after a solve (the moment it makes sense). The Android 13+
 * system dialog appears only after the player taps "Remind me"; "Not now" never triggers it.
 */
@Composable
fun ReminderOfferCard(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var visible by remember { mutableStateOf(Retention.shouldOfferReminder(context)) }
    var note by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) { if (visible) Retention.recordOfferShown(context) }

    fun enable() {
        DailyReminder.setEnabled(context, true)
        Funnel.log("reminder_enabled")
        DailyReminder.markOfferShown(context)
        visible = false
        note = "Reminder on. You can change the time or turn it off in Settings."
    }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) { Funnel.log("notif_permission_granted"); enable() }
        else {
            Funnel.log("notif_permission_denied")
            DailyReminder.markOfferShown(context)
            visible = false
            note = "No problem. You can turn the reminder on later in Settings."
        }
    }

    note?.let {
        Text(it, fontSize = 13.sp, color = RvInkSoft, textAlign = TextAlign.Center, modifier = modifier.fillMaxWidth())
    }
    if (!visible) return
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = RvSurfaceRaised),
        border = BorderStroke(2.dp, RvOutline),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Get a reminder for tomorrow's puzzle?", fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, color = RvInk)
            Text(
                "One notification a day, only if you have not solved it yet, so your streak is easy to keep. No ads in it. You can turn it off any time in Settings.",
                fontSize = 13.sp, color = RvInkSoft,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.clip(RoundedCornerShape(50)).background(RvSun)
                        .clickable {
                            if (DailyReminder.needsRuntimePermission(context)) {
                                launcher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                            } else enable()
                        }
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) { Text("Remind me", fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, color = RvOnTone) }
                Box(
                    Modifier.clip(RoundedCornerShape(50))
                        .clickable { DailyReminder.markOfferShown(context); visible = false }
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) { Text("Not now", fontSize = 14.sp, color = RvInkSoft) }
            }
        }
    }
}

/** Settings > Daily puzzle: reminder switch + time, and how many streak freezes are in the bag. */
@Composable
fun DailyPuzzleSettingsSection() {
    val context = LocalContext.current
    var enabled by remember { mutableStateOf(DailyReminder.isEnabled(context)) }
    var hour by remember { mutableIntStateOf(DailyReminder.hour(context)) }
    var blocked by remember { mutableStateOf(false) }
    val freezes = remember { DailyPuzzleStore.freezesLeft(context) }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            Funnel.log("notif_permission_granted"); Funnel.log("reminder_enabled")
            DailyReminder.setEnabled(context, true); DailyReminder.markOfferShown(context)
            enabled = true; blocked = false
        } else {
            Funnel.log("notif_permission_denied")
            DailyReminder.setEnabled(context, false); enabled = false; blocked = true
        }
    }

    SettingsSection(title = "Daily puzzle") {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Daily reminder", fontWeight = FontWeight.Medium, color = RvInk)
                    Text("One notification a day, only if you have not solved today's puzzle.", fontSize = 12.sp, color = RvInkSoft)
                }
                Switch(
                    checked = enabled,
                    onCheckedChange = { want ->
                        if (!want) { DailyReminder.setEnabled(context, false); enabled = false; blocked = false }
                        else if (DailyReminder.needsRuntimePermission(context)) launcher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                        else if (!DailyReminder.notificationsAllowed(context)) blocked = true
                        else { DailyReminder.setEnabled(context, true); Funnel.log("reminder_enabled"); DailyReminder.markOfferShown(context); enabled = true; blocked = false }
                    },
                )
            }
            if (blocked) {
                Text("Notifications are blocked for RiddleVerse on this phone.", fontSize = 12.sp, color = RvInkSoft)
                Text(
                    "Open notification settings",
                    fontSize = 13.sp, fontWeight = FontWeight.Bold, color = RvViolet,
                    modifier = Modifier.clickable {
                        context.startActivity(
                            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                                .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                    },
                )
            }
            if (enabled) {
                Text("Reminder time", fontSize = 12.sp, color = RvInkSoft)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ReminderPolicy.HOUR_CHOICES.forEach { h ->
                        val sel = h == hour
                        Box(
                            Modifier.clip(RoundedCornerShape(50))
                                .background(if (sel) RvSun else RvSurfaceRaised)
                                .clickable { hour = h; DailyReminder.setHour(context, h) }
                                .padding(horizontal = 14.dp, vertical = 8.dp)
                                .semantics { contentDescription = "Remind me at ${hourLabel(h)}" }
                        ) { Text(hourLabel(h), fontSize = 13.sp, fontWeight = if (sel) FontWeight.ExtraBold else FontWeight.Normal, color = if (sel) RvOnTone else RvInk) }
                    }
                }
            }
            Text(
                "Streak freezes: $freezes of ${DailyStreakRules.MAX_FREEZES}. A freeze quietly covers a missed day so your streak survives; you earn one every ${DailyStreakRules.FREEZE_EVERY} streak days.",
                fontSize = 12.sp, color = RvInkSoft,
            )
        }
    }
}

private fun hourLabel(h: Int): String = when {
    h == 0 -> "12 AM"; h < 12 -> "$h AM"; h == 12 -> "12 PM"; else -> "${h - 12} PM"
}
