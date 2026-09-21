package com.kreativekoala.riddleverse

import com.kreativekoala.riddleverse.ui.theme.*
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarOutline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.auth.FirebaseAuth
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import org.json.JSONObject
import java.io.IOException

// Enhanced AppRatingManager with persistent preferences
class AppRatingManager(private val context: Context) {
    private val prefs = context.getSharedPreferences("app_rating_prefs", Context.MODE_PRIVATE)

    companion object {
        private const val PREF_LAUNCH_COUNT = "launch_count"
        private const val PREF_FIRST_LAUNCH = "first_launch_date"
        private const val PREF_LAST_PROMPT_DATE = "last_prompt_date"
        private const val PREF_USER_RATED = "user_has_rated"
        private const val PREF_USER_DISMISSED_PERMANENTLY = "user_dismissed_permanently"
        private const val PREF_DISMISSED_COUNT = "dismissed_count"
        private const val PREF_LAST_DISMISSED_DATE = "last_dismissed_date"
        private const val PREF_NEGATIVE_FEEDBACK_GIVEN = "negative_feedback_given"

        // Rating trigger thresholds
        private const val MIN_LAUNCHES_FOR_RATING = 5
        private const val DAYS_BETWEEN_PROMPTS = 7
        private const val MAX_DISMISSALS = 3
        private const val DAYS_AFTER_DISMISSAL = 14
    }

    fun incrementLaunchCount() {
        val currentCount = prefs.getInt(PREF_LAUNCH_COUNT, 0)
        prefs.edit().putInt(PREF_LAUNCH_COUNT, currentCount + 1).apply()

        if (prefs.getLong(PREF_FIRST_LAUNCH, 0L) == 0L) {
            prefs.edit().putLong(PREF_FIRST_LAUNCH, System.currentTimeMillis()).apply()
        }

        Log.d("AppRating", "Launch count incremented to: ${currentCount + 1}")
    }

    fun shouldShowRatingPrompt(): Boolean {
        if (hasUserRated()) {
            Log.d("AppRating", "User has already rated - not showing prompt")
            return false
        }

        if (hasUserDismissedPermanently()) {
            Log.d("AppRating", "User permanently dismissed - not showing prompt")
            return false
        }

        if (hasGivenNegativeFeedback()) {
            Log.d("AppRating", "User gave negative feedback - not showing prompt")
            return false
        }

        if (hasDismissedTooManyTimes()) {
            Log.d("AppRating", "User has dismissed too many times - not showing prompt")
            return false
        }

        val launchCount = prefs.getInt(PREF_LAUNCH_COUNT, 0)
        if (launchCount < MIN_LAUNCHES_FOR_RATING) {
            Log.d("AppRating", "Not enough launches ($launchCount < $MIN_LAUNCHES_FOR_RATING)")
            return false
        }

        val lastPromptDate = prefs.getLong(PREF_LAST_PROMPT_DATE, 0L)
        val daysSinceLastPrompt = (System.currentTimeMillis() - lastPromptDate) / (1000 * 60 * 60 * 24)

        if (lastPromptDate > 0 && daysSinceLastPrompt < DAYS_BETWEEN_PROMPTS) {
            Log.d("AppRating", "Too soon since last prompt ($daysSinceLastPrompt < $DAYS_BETWEEN_PROMPTS days)")
            return false
        }

        val lastDismissedDate = prefs.getLong(PREF_LAST_DISMISSED_DATE, 0L)
        if (lastDismissedDate > 0) {
            val daysSinceLastDismissal = (System.currentTimeMillis() - lastDismissedDate) / (1000 * 60 * 60 * 24)
            if (daysSinceLastDismissal < DAYS_AFTER_DISMISSAL) {
                Log.d("AppRating", "Too soon since last dismissal ($daysSinceLastDismissal < $DAYS_AFTER_DISMISSAL days)")
                return false
            }
        }

        Log.d("AppRating", "All conditions met - should show rating prompt")
        return true
    }

    fun markRatingPromptShown() {
        prefs.edit().putLong(PREF_LAST_PROMPT_DATE, System.currentTimeMillis()).apply()
        Log.d("AppRating", "Rating prompt shown timestamp updated")
    }

    fun markUserRated() {
        prefs.edit().apply {
            putBoolean(PREF_USER_RATED, true)
            putLong(PREF_LAST_PROMPT_DATE, System.currentTimeMillis())
        }.apply()

        Log.d("AppRating", "User marked as having rated the app")

        AnalyticsManager.getInstance()?.track(AnalyticsEvent("app_rated", mapOf(
            "rating_source" to "in_app_prompt",
            "launch_count" to prefs.getInt(PREF_LAUNCH_COUNT, 0),
            "days_since_first_launch" to getDaysSinceFirstLaunch()
        )))
    }

    fun markUserDismissedTemporarily() {
        val currentDismissals = prefs.getInt(PREF_DISMISSED_COUNT, 0)
        prefs.edit().apply {
            putInt(PREF_DISMISSED_COUNT, currentDismissals + 1)
            putLong(PREF_LAST_DISMISSED_DATE, System.currentTimeMillis())
            putLong(PREF_LAST_PROMPT_DATE, System.currentTimeMillis())
        }.apply()

        Log.d("AppRating", "User dismissed temporarily - count: ${currentDismissals + 1}")

        AnalyticsManager.getInstance()?.track(AnalyticsEvent("rating_prompt_dismissed", mapOf(
            "dismissal_count" to (currentDismissals + 1),
            "dismissal_type" to "temporary",
            "launch_count" to prefs.getInt(PREF_LAUNCH_COUNT, 0)
        )))
    }

    fun markUserDismissedPermanently() {
        prefs.edit().apply {
            putBoolean(PREF_USER_DISMISSED_PERMANENTLY, true)
            putLong(PREF_LAST_DISMISSED_DATE, System.currentTimeMillis())
            putLong(PREF_LAST_PROMPT_DATE, System.currentTimeMillis())
        }.apply()

        Log.d("AppRating", "User dismissed permanently - will not show again")

        AnalyticsManager.getInstance()?.track(AnalyticsEvent("rating_prompt_dismissed", mapOf(
            "dismissal_type" to "permanent",
            "launch_count" to prefs.getInt(PREF_LAUNCH_COUNT, 0),
            "total_dismissals" to prefs.getInt(PREF_DISMISSED_COUNT, 0)
        )))
    }

    fun markNegativeFeedbackGiven() {
        prefs.edit().putBoolean(PREF_NEGATIVE_FEEDBACK_GIVEN, true).apply()
        Log.d("AppRating", "User gave negative feedback - won't show rating prompt again")
    }

    fun markUserDismissedWithCustomDelay(days: Int) {
        val customDelayUntil = System.currentTimeMillis() + (days * 24 * 60 * 60 * 1000L)

        prefs.edit().apply {
            putLong("last_dismissed_date", System.currentTimeMillis())
            putLong("custom_delay_until", customDelayUntil)
            putInt("dismissed_count", prefs.getInt("dismissed_count", 0) + 1)
        }.apply()

        Log.d("AppRating", "User dismissed with custom delay: $days days")
    }

    fun hasUserRated(): Boolean = prefs.getBoolean(PREF_USER_RATED, false)

    fun hasUserDismissedPermanently(): Boolean = prefs.getBoolean(PREF_USER_DISMISSED_PERMANENTLY, false)

    fun hasGivenNegativeFeedback(): Boolean = prefs.getBoolean(PREF_NEGATIVE_FEEDBACK_GIVEN, false)

    private fun hasDismissedTooManyTimes(): Boolean {
        val dismissalCount = prefs.getInt(PREF_DISMISSED_COUNT, 0)
        return dismissalCount >= MAX_DISMISSALS
    }

    private fun getDaysSinceFirstLaunch(): Long {
        val firstLaunch = prefs.getLong(PREF_FIRST_LAUNCH, 0L)
        return if (firstLaunch > 0) {
            (System.currentTimeMillis() - firstLaunch) / (1000 * 60 * 60 * 24)
        } else 0L
    }

    fun debugRatingState(): String {
        return buildString {
            appendLine("=== Rating Manager Debug ===")
            appendLine("Launch Count: ${prefs.getInt(PREF_LAUNCH_COUNT, 0)}")
            appendLine("Has Rated: ${hasUserRated()}")
            appendLine("Permanently Dismissed: ${hasUserDismissedPermanently()}")
            appendLine("Gave Negative Feedback: ${hasGivenNegativeFeedback()}")
            appendLine("Dismissal Count: ${prefs.getInt(PREF_DISMISSED_COUNT, 0)}")
            appendLine("Days Since First Launch: ${getDaysSinceFirstLaunch()}")
            appendLine("Should Show Prompt: ${shouldShowRatingPrompt()}")

            val lastPrompt = prefs.getLong(PREF_LAST_PROMPT_DATE, 0L)
            if (lastPrompt > 0) {
                appendLine("Last Prompt: ${java.util.Date(lastPrompt)}")
            }

            val lastDismissed = prefs.getLong(PREF_LAST_DISMISSED_DATE, 0L)
            if (lastDismissed > 0) {
                appendLine("Last Dismissed: ${java.util.Date(lastDismissed)}")
            }
        }
    }

    fun resetRatingPreferences() {
        prefs.edit().clear().apply()
        Log.d("AppRating", "All rating preferences reset")
    }
}
// Server feedback submission
fun submitFeedbackToServer(
    context: Context,
    isPositive: Boolean,
    message: String,
    onResult: (Boolean, String?) -> Unit
) {
    val userId = FirebaseAuth.getInstance().currentUser?.email ?: ""
    if (userId.isEmpty()) {
        onResult(false, "Please sign in to submit feedback")
        return
    }

    val url = "https://puzzleverseai.com/submit-feedback"
    val client = OkHttpClient()

    try {
        val appVersion = try {
            val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            packageInfo.versionName
        } catch (e: Exception) {
            "unknown"
        }

        val deviceInfo = "${Build.MANUFACTURER} ${Build.MODEL} (Android ${Build.VERSION.RELEASE})"

        val requestBody = JSONObject().apply {
            put("userId", userId)
            put("isPositive", isPositive)
            put("message", message)
            put("timestamp", System.currentTimeMillis())
            put("appVersion", appVersion)
            put("deviceInfo", deviceInfo)
        }

        val body = RequestBody.create(
            "application/json".toMediaType(),
            requestBody.toString()
        )

        val request = Request.Builder()
            .url(url)
            .post(body)
            .build()

        Log.d("FeedbackSubmission", "Submitting feedback: isPositive=$isPositive, message length=${message.length}")

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e("FeedbackSubmission", "Failed to submit feedback", e)
                Handler(Looper.getMainLooper()).post {
                    onResult(false, "Network error - please try again")
                }
            }

            override fun onResponse(call: Call, response: Response) {
                val responseBody = response.body?.string()

                Handler(Looper.getMainLooper()).post {
                    if (response.isSuccessful) {
                        Log.d("FeedbackSubmission", "Feedback submitted successfully")
                        onResult(true, "Thank you for your feedback!")

                        AnalyticsManager.getInstance()?.track(AnalyticsEvent("feedback_submitted", mapOf(
                            "is_positive" to isPositive,
                            "message_length" to message.length,
                            "submission_method" to "in_app_form"
                        )))
                    } else {
                        Log.e("FeedbackSubmission", "Server error: ${response.code} - $responseBody")
                        onResult(false, "Server error - please try again later")
                    }
                }
            }
        })

    } catch (e: Exception) {
        Log.e("FeedbackSubmission", "Exception submitting feedback", e)
        onResult(false, "Error submitting feedback - please try again")
    }
}

// Compose-compatible dismissal options dialog
@Composable
fun DismissalOptionsDialog(
    ratingManager: AppRatingManager,
    onDismiss: () -> Unit,
    onComplete: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(R.string.rating_reminder),
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Text(stringResource(R.string.when_remind_again))
        },
        confirmButton = {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = {
                        ratingManager.markUserDismissedTemporarily()
                        Log.d("RatingDialog", "User chose: remind in a week")
                        onComplete()
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.remind_in_week))
                }

                Button(
                    onClick = {
                        ratingManager.markUserDismissedWithCustomDelay(30)
                        Log.d("RatingDialog", "User chose: remind in a month")
                        onComplete()
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.remind_in_month))
                }

                Button(
                    onClick = {
                        ratingManager.markUserDismissedPermanently()
                        Log.d("RatingDialog", "User chose: don't ask again")
                        onComplete()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = RvFlame
                    )
                ) {
                    Text(stringResource(R.string.dont_ask_again))
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

enum class RatingStep {
    INITIAL,
    HIGH_RATING_STORE,
    LOW_RATING_FEEDBACK
}

// Enhanced Rating Dialog with server feedback integration
@Composable
fun RatingFlowDialog(
    onDismiss: () -> Unit,
    onCompleted: () -> Unit
) {
    val context = LocalContext.current
    val ratingManager = remember { AppRatingManager(context) }

    var currentStep by remember { mutableStateOf(RatingStep.INITIAL) }
    var userRating by remember { mutableStateOf(0) }
    var showFeedbackDialog by remember { mutableStateOf(false) }
    var showDismissalOptions by remember { mutableStateOf(false) }

    // Handle feedback dialog
    if (showFeedbackDialog) {
        FeedbackDialog(
            isPositive = userRating >= 4,
            onDismiss = {
                showFeedbackDialog = false
            },
            onSubmitted = {
                showFeedbackDialog = false
                if (userRating >= 4) {
                    ratingManager.markUserRated()
                } else {
                    ratingManager.markNegativeFeedbackGiven()
                }
                onCompleted()
            }
        )
    }

    // Handle dismissal options dialog
    if (showDismissalOptions) {
        DismissalOptionsDialog(
            ratingManager = ratingManager,
            onDismiss = {
                showDismissalOptions = false
            },
            onComplete = {
                showDismissalOptions = false
                onDismiss()
            }
        )
    }

    // Main rating dialog
    if (!showFeedbackDialog && !showDismissalOptions) {
        AlertDialog(
            onDismissRequest = {
                when (currentStep) {
                    RatingStep.INITIAL -> {
                        ratingManager.markUserDismissedTemporarily()
                        Log.d("RatingDialog", "User dismissed at initial step - temporary dismissal")
                    }
                    RatingStep.LOW_RATING_FEEDBACK -> {
                        ratingManager.markUserDismissedTemporarily()
                        Log.d("RatingDialog", "User dismissed during feedback - temporary dismissal")
                    }
                    else -> {
                        Log.d("RatingDialog", "User dismissed after engaging positively")
                    }
                }
                onDismiss()
            },
            title = {
                Text(
                    text = when (currentStep) {
                        RatingStep.INITIAL -> stringResource(R.string.enjoying_app)
                        RatingStep.HIGH_RATING_STORE -> stringResource(R.string.rate_in_play_store)
                        RatingStep.LOW_RATING_FEEDBACK -> stringResource(R.string.help_us_improve)
                    },
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
            },
            text = {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    when (currentStep) {
                        RatingStep.INITIAL -> {
                            Text(stringResource(R.string.rate_experience))

                            RatingStars(
                                rating = userRating,
                                onRatingChanged = { userRating = it }
                            )

                            if (userRating >= 4) {
                                Text(
                                    text = stringResource(R.string.rate_incentive),
                                    fontSize = 12.sp,
                                    color = RvSun,
                                    fontWeight = FontWeight.Bold,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                        RatingStep.HIGH_RATING_STORE -> {
                            Text(stringResource(R.string.thanks_great_rating))
                            Text(
                                text = stringResource(R.string.share_detailed_feedback),
                                fontSize = 12.sp,
                                color = Color.Gray
                            )
                        }
                        RatingStep.LOW_RATING_FEEDBACK -> {
                            Text(stringResource(R.string.sorry_bad_experience))
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        when (currentStep) {
                            RatingStep.INITIAL -> {
                                if (userRating >= 4) {
                                    currentStep = RatingStep.HIGH_RATING_STORE
                                } else if (userRating > 0) {
                                    currentStep = RatingStep.LOW_RATING_FEEDBACK
                                }
                            }
                            RatingStep.HIGH_RATING_STORE -> {
                                ratingManager.markUserRated()
                                openPlayStore(context)
                                onCompleted()
                            }
                            RatingStep.LOW_RATING_FEEDBACK -> {
                                showFeedbackDialog = true
                            }
                        }
                    }
                ) {
                    Text(
                        when (currentStep) {
                            RatingStep.INITIAL -> if (userRating > 0) stringResource(R.string.continue_label) else stringResource(R.string.rate)
                            RatingStep.HIGH_RATING_STORE -> stringResource(R.string.rate_in_store)
                            RatingStep.LOW_RATING_FEEDBACK -> stringResource(R.string.give_feedback)
                        }
                    )
                }
            },
            dismissButton = {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (currentStep == RatingStep.HIGH_RATING_STORE) {
                        TextButton(
                            onClick = {
                                showFeedbackDialog = true
                            }
                        ) {
                            Text(stringResource(R.string.feedback_first))
                        }
                    }

                    TextButton(
                        onClick = {
                            when (currentStep) {
                                RatingStep.INITIAL -> {
                                    showDismissalOptions = true
                                }
                                else -> {
                                    ratingManager.markUserDismissedTemporarily()
                                    onDismiss()
                                }
                            }
                        }
                    ) {
                        Text(
                            when (currentStep) {
                                RatingStep.INITIAL -> stringResource(R.string.not_now)
                                else -> stringResource(R.string.maybe_later)
                            }
                        )
                    }
                }
            }
        )
    }
}

@Composable
fun FeedbackDialog(
    isPositive: Boolean,
    onDismiss: () -> Unit,
    onSubmitted: () -> Unit
) {
    val context = LocalContext.current
    var feedbackText by remember { mutableStateOf("") }
    var isSubmitting by remember { mutableStateOf(false) }
    var hasSubmitted by remember { mutableStateOf(false) }
    var submissionResult by remember { mutableStateOf<Pair<Boolean, String?>?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (isPositive) stringResource(R.string.tell_us_what_you_love) else stringResource(R.string.help_us_improve),
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = if (isPositive) {
                        stringResource(R.string.what_enjoy_most)
                    } else {
                        stringResource(R.string.what_can_we_improve)
                    }
                )

                OutlinedTextField(
                    value = feedbackText,
                    onValueChange = { feedbackText = it },
                    label = { Text(stringResource(R.string.your_feedback)) },
                    placeholder = {
                        Text(
                            if (isPositive) {
                                stringResource(R.string.feedback_placeholder_positive)
                            } else {
                                stringResource(R.string.feedback_placeholder_negative)
                            }
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp),
                    maxLines = 5,
                    singleLine = false
                )

                submissionResult?.let { (success, message) ->
                    Text(
                        text = message ?: if (success) stringResource(R.string.submitted_successfully) else stringResource(R.string.submission_failed),
                        color = if (success) RvSuccess else RvFlame,
                        fontSize = 12.sp
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (feedbackText.trim().isNotEmpty() && !isSubmitting && !hasSubmitted) {
                        hasSubmitted = true
                        isSubmitting = true
                        submissionResult = null

                        submitFeedbackToServer(
                            context = context,
                            isPositive = isPositive,
                            message = feedbackText.trim()
                        ) { success, message ->
                            isSubmitting = false
                            submissionResult = Pair(success, message)

                            if (success) {
                                Handler(Looper.getMainLooper()).postDelayed({
                                    onSubmitted()
                                }, 1500)
                            }
                        }
                    }
                },
                enabled = feedbackText.trim().isNotEmpty() && !isSubmitting && !hasSubmitted
            ) {
                if (isSubmitting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        color = RvInk
                    )
                } else {
                    Text(stringResource(R.string.submit))
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

@Composable
fun RatingStars(
    rating: Int,
    onRatingChanged: (Int) -> Unit,
    maxRating: Int = 5
) {
    Row(
        horizontalArrangement = Arrangement.Center,
        modifier = Modifier.fillMaxWidth()
    ) {
        for (i in 1..maxRating) {
            Icon(
                imageVector = if (i <= rating) Icons.Default.Star else Icons.Default.StarOutline,
                contentDescription = "Star $i",
                tint = if (i <= rating) RvSun else Color.Gray,
                modifier = Modifier
                    .size(32.dp)
                    .clickable { onRatingChanged(i) }
                    .padding(2.dp)
            )
        }
    }
}

@Composable
fun CoinBalanceWidget() {
    val context = LocalContext.current
    val coinManager = CoinManager.shared
    val coinBalance = coinManager.balance

    LaunchedEffect(Unit) {
        coinManager.fetchBalance()
    }

    Card(
        modifier = Modifier
            .padding(8.dp)
            .clickable {
                Toast.makeText(context, "Earn coins by rating the app and completing achievements!", Toast.LENGTH_SHORT).show()
            },
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFFFFF8E1)
        )
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "🪙",
                fontSize = 20.sp
            )

            Column {
                Text(
                    text = "$coinBalance Coins",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = RvFlame
                )

                Text(
                    text = stringResource(R.string.rate_for_early_access),
                    fontSize = 10.sp,
                    color = RvFlame
                )
            }
        }
    }
}

// Fallback email method
fun openFeedbackEmail(context: Context) {
    try {
        val intent = Intent(Intent.ACTION_SENDTO).apply {
            data = Uri.parse("mailto:")
            putExtra(Intent.EXTRA_EMAIL, arrayOf("support@riddleverse.com"))
            putExtra(Intent.EXTRA_SUBJECT, "RiddleVerse App Feedback")
            putExtra(Intent.EXTRA_TEXT, "Hi,\n\nI'd like to share some feedback about RiddleVerse:\n\n")
        }
        context.startActivity(intent)
    } catch (e: Exception) {
        Log.e("RatingDialog", "Failed to open email client", e)
        Toast.makeText(context, "Please email us at support@riddleverse.com", Toast.LENGTH_LONG).show()
    }
}

// Helper functions
fun openPlayStore(context: Context) {
    try {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            data = Uri.parse("market://details?id=${context.packageName}")
        }
        context.startActivity(intent)
    } catch (e: Exception) {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            data = Uri.parse("https://play.google.com/store/apps/details?id=${context.packageName}")
        }
        context.startActivity(intent)
    }
}

fun getAppVersion(context: Context): String {
    return try {
        val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
        packageInfo.versionName ?: "Unknown"
    } catch (e: Exception) {
        "Unknown"
    }
}

fun getDeviceInfo(): String {
    return "${Build.MANUFACTURER} ${Build.MODEL} (Android ${Build.VERSION.RELEASE})"
}
