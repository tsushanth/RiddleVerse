package com.kreativekoala.riddleverse

import android.content.Context
import androidx.compose.foundation.BorderStroke
import android.app.Activity
import android.content.ContentValues.TAG
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import com.kreativekoala.riddleverse.GroupCompletionManager
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.gson.Gson
import com.kreativekoala.riddleverse.ui.theme.RiddleVerseTheme
import okhttp3.*
import okio.IOException
import org.json.JSONObject
import java.net.URLEncoder
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Help
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Settings
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

class RegenerationLimitManager private constructor(context: Context) {

    private val prefs = context.getSharedPreferences("regeneration_limits", Context.MODE_PRIVATE)

    companion object {
        @Volatile
        private var INSTANCE: RegenerationLimitManager? = null

        fun getInstance(context: Context): RegenerationLimitManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: RegenerationLimitManager(context).also { INSTANCE = it }
            }
        }
    }

    data class LimitInfo(
        val reason: String,
        val dailyUsed: Int,
        val dailyLimit: Int,
        val monthlyUsed: Int,
        val monthlyLimit: Int,
        val resetTime: String,
        val upgradeUrl: String,
        val message: String
    )

    fun saveLimitInfo(puzzleType: String, limitInfo: LimitInfo) {
        prefs.edit()
            .putString("${puzzleType}_limit_reason", limitInfo.reason)
            .putInt("${puzzleType}_daily_used", limitInfo.dailyUsed)
            .putInt("${puzzleType}_daily_limit", limitInfo.dailyLimit)
            .putInt("${puzzleType}_monthly_used", limitInfo.monthlyUsed)
            .putInt("${puzzleType}_monthly_limit", limitInfo.monthlyLimit)
            .putString("${puzzleType}_reset_time", limitInfo.resetTime)
            .putString("${puzzleType}_message", limitInfo.message)
            .putLong("${puzzleType}_limit_timestamp", System.currentTimeMillis())
            .apply()
    }

    fun getLimitInfo(puzzleType: String): LimitInfo? {
        val reason = prefs.getString("${puzzleType}_limit_reason", null) ?: return null
        val timestamp = prefs.getLong("${puzzleType}_limit_timestamp", 0)

        // Check if limit info is still relevant (within 24 hours)
        if (System.currentTimeMillis() - timestamp > 24 * 60 * 60 * 1000) {
            clearLimitInfo(puzzleType)
            return null
        }

        return LimitInfo(
            reason = reason,
            dailyUsed = prefs.getInt("${puzzleType}_daily_used", 0),
            dailyLimit = prefs.getInt("${puzzleType}_daily_limit", 0),
            monthlyUsed = prefs.getInt("${puzzleType}_monthly_used", 0),
            monthlyLimit = prefs.getInt("${puzzleType}_monthly_limit", 0),
            resetTime = prefs.getString("${puzzleType}_reset_time", "") ?: "",
            upgradeUrl = "/upgrade",
            message = prefs.getString("${puzzleType}_message", "") ?: ""
        )
    }

    fun clearAllLimits() {
        // Clear all stored limit info
        val editor = prefs.edit()
        val allKeys = prefs.all.keys
        for (key in allKeys) {
            if (key.contains("_limit_") || key.contains("_daily_") || key.contains("_monthly_")) {
                editor.remove(key)
            }
        }
        editor.apply()
    }

    fun clearLimitInfo(puzzleType: String) {
        prefs.edit()
            .remove("${puzzleType}_limit_reason")
            .remove("${puzzleType}_daily_used")
            .remove("${puzzleType}_daily_limit")
            .remove("${puzzleType}_monthly_used")
            .remove("${puzzleType}_monthly_limit")
            .remove("${puzzleType}_reset_time")
            .remove("${puzzleType}_message")
            .remove("${puzzleType}_limit_timestamp")
            .apply()
    }

    fun hasActiveLimits(puzzleType: String): Boolean {
        return getLimitInfo(puzzleType) != null
    }
}

// Enhanced error message display component
@Composable
fun RegenerationLimitCard(
    puzzleType: String,
    limitInfo: RegenerationLimitManager.LimitInfo,
    onDismiss: () -> Unit,
    onUpgrade: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF1565C0).copy(alpha = 0.1f)
        ),
        border = BorderStroke(1.dp, Color(0xFF1565C0).copy(alpha = 0.3f)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = "Generation Limit",
                    tint = Color(0xFF1565C0),
                    modifier = Modifier.size(24.dp)
                )

                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Dismiss",
                        tint = Color.Gray,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = when (limitInfo.reason) {
                    "daily_limit_exceeded" -> "Daily Generation Limit Reached"
                    "monthly_limit_exceeded" -> "Monthly Generation Limit Reached"
                    "rapid_fire_detected" -> "Rate Limit Active"
                    else -> "Generation Limited"
                },
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = getEnhancedLimitMessage(puzzleType, limitInfo),
                fontSize = 14.sp,
                color = Color.Gray,
                lineHeight = 20.sp
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Usage progress bars
            if (limitInfo.reason.contains("daily")) {
                UsageProgressBar(
                    label = "Daily Usage",
                    used = limitInfo.dailyUsed,
                    total = limitInfo.dailyLimit,
                    color = Color(0xFF1565C0)
                )
                Spacer(modifier = Modifier.height(8.dp))
            }

            if (limitInfo.reason.contains("monthly")) {
                UsageProgressBar(
                    label = "Monthly Usage",
                    used = limitInfo.monthlyUsed,
                    total = limitInfo.monthlyLimit,
                    color = Color(0xFF1565C0)
                )
                Spacer(modifier = Modifier.height(8.dp))
            }

            // Reset time info
            if (limitInfo.resetTime.isNotEmpty()) {
                Text(
                    text = "Resets: ${formatResetTime(limitInfo.resetTime)}",
                    fontSize = 12.sp,
                    color = Color.Gray,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }

            // Action buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                if (limitInfo.reason != "rapid_fire_detected") {
                    Button(
                        onClick = onUpgrade,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF1565C0)
                        ),
                        shape = RoundedCornerShape(20.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(stringResource(R.string.upgrade_for_unlimited), fontSize = 12.sp)
                    }

                    Spacer(modifier = Modifier.width(8.dp))
                }

                OutlinedButton(
                    onClick = onDismiss,
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = Color.Gray
                    ),
                    border = BorderStroke(1.dp, Color.Gray.copy(alpha = 0.3f)),
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = if (limitInfo.reason == "rapid_fire_detected") stringResource(R.string.got_it) else "Play Available",
                        fontSize = 12.sp
                    )
                }
            }
        }
    }
}

@Composable
fun UsageProgressBar(
    label: String,
    used: Int,
    total: Int,
    color: Color
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = label,
                fontSize = 12.sp,
                color = Color.Gray
            )
            Text(
                text = "$used/$total",
                fontSize = 12.sp,
                color = Color.White,
                fontWeight = FontWeight.Medium
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        LinearProgressIndicator(
            progress = if (total > 0) (used.toFloat() / total.toFloat()) else 0f,
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp)),
            color = color,
            trackColor = Color.Gray.copy(alpha = 0.3f)
        )
    }
}

// Helper functions
private fun getEnhancedLimitMessage(puzzleType: String, limitInfo: RegenerationLimitManager.LimitInfo): String {
    val displayName = getPuzzleDisplayName(puzzleType)

    return when (limitInfo.reason) {
        "daily_limit_exceeded" ->
            "You've used all ${limitInfo.dailyLimit} daily puzzle generations for $displayName. " +
                    "You can still play from existing puzzles, or upgrade for unlimited access."

        "monthly_limit_exceeded" ->
            "You've reached your monthly limit of ${limitInfo.monthlyLimit} puzzle generations. " +
                    "Consider upgrading for unlimited monthly access to fresh puzzles."

        "rapid_fire_detected" ->
            "Please slow down! You're generating puzzles too quickly. " +
                    "Wait a moment before trying again to help us manage server resources."

        else ->
            "Puzzle generation is temporarily limited. You can still play available puzzles " +
                    "or try again later."
    }
}

private fun formatResetTime(resetTimeIso: String): String {
    return try {
        // Parse ISO 8601 date string
        val format = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
        format.timeZone = TimeZone.getTimeZone("UTC")
        val resetTime = format.parse(resetTimeIso)

        if (resetTime != null) {
            val now = Date()
            val durationMs = resetTime.time - now.time

            when {
                durationMs <= 0 -> "now"
                TimeUnit.MILLISECONDS.toDays(durationMs) > 0 -> {
                    val days = TimeUnit.MILLISECONDS.toDays(durationMs)
                    "in ${days} day${if (days == 1L) "" else "s"}"
                }
                TimeUnit.MILLISECONDS.toHours(durationMs) > 0 -> {
                    val hours = TimeUnit.MILLISECONDS.toHours(durationMs)
                    "in ${hours} hour${if (hours == 1L) "" else "s"}"
                }
                TimeUnit.MILLISECONDS.toMinutes(durationMs) > 0 -> {
                    val minutes = TimeUnit.MILLISECONDS.toMinutes(durationMs)
                    "in ${minutes} minute${if (minutes == 1L) "" else "s"}"
                }
                else -> "soon"
            }
        } else {
            "later"
        }
    } catch (e: Exception) {
        "later"
    }
}