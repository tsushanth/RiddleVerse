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
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.auth.FirebaseAuth
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import org.json.JSONObject
import java.io.IOException

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
