package com.kreativekoala.riddleverse

import com.kreativekoala.riddleverse.ui.theme.*
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.auth.FirebaseAuth

// MARK: - Data Classes
data class ShareableContent(
    val type: ShareType,
    val title: String,
    val message: String,
    val hashtags: List<String> = emptyList(),
    val customData: Map<String, Any> = emptyMap()
)

enum class ShareType {
    PUZZLE_RESULT,
    ACHIEVEMENT,
    LEVEL_UP,
    STREAK,
    CUSTOM_PUZZLE,
    WEEKLY_RANK,
    CHALLENGE_COMPLETION
}

data class ShareOption(
    val title: String,
    val icon: ImageVector,
    val color: Color,
    val action: (Context, ShareableContent) -> Unit
)

// MARK: - Main Sharing Component
@Composable
fun SocialShareDialog(
    shareableContent: ShareableContent,
    onDismiss: () -> Unit,
    showCustomPuzzleSharing: Boolean = false // For your existing custom puzzle sharing
) {
    val context = LocalContext.current
    var showCustomDialog by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                stringResource(R.string.done_share_your_success),
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
        },
        text = {
            Column {
                // Preview of what will be shared
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = RvSurface
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            shareableContent.title,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            shareableContent.message,
                            fontSize = 14.sp,
                            color = RvInkSoft,
                            textAlign = TextAlign.Center
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Sharing options
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    getShareOptions().forEach { option ->
                        ShareOptionRow(
                            option = option,
                            content = shareableContent,
                            onClick = {
                                option.action(context, shareableContent)
                                onDismiss()
                            }
                        )
                    }

                    // Custom puzzle sharing (your existing feature)
                    if (showCustomPuzzleSharing) {
                        ShareOptionRow(
                            option = ShareOption(
                                title = stringResource(R.string.send_via_email),
                                icon = Icons.Default.Email,
                                color = RvSky,
                                action = { _, _ -> }
                            ),
                            content = shareableContent,
                            onClick = {
                                showCustomDialog = true
                            }
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )

    // Your existing custom puzzle dialog
    if (showCustomDialog) {
        CustomPuzzleShareDialog(
            onDismiss = { showCustomDialog = false },
            onShare = { email, name ->
                // Your existing sharePuzzle function
                val puzzleId = shareableContent.customData["puzzleId"] as? String ?: ""
                sharePuzzle(
                    puzzleId = puzzleId,
                    recipientEmail = email,
                    recipientName = name,
                    senderId = FirebaseAuth.getInstance().currentUser?.displayName
                        ?: FirebaseAuth.getInstance().currentUser?.email
                        ?: "anonymous"
                )
                showCustomDialog = false
                onDismiss()
            }
        )
    }
}

@Composable
fun ShareOptionRow(
    option: ShareOption,
    content: ShareableContent,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(vertical = 8.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .background(option.color.copy(alpha = 0.1f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = option.icon,
                contentDescription = option.title,
                tint = option.color,
                modifier = Modifier.size(20.dp)
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        Text(
            text = option.title,
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(1f)
        )

        Icon(
            imageVector = Icons.Default.ChevronRight,
            contentDescription = stringResource(R.string.share),
            tint = RvInkSoft,
            modifier = Modifier.size(20.dp)
        )
    }
}

@Composable
fun CustomPuzzleShareDialog(
    onDismiss: () -> Unit,
    onShare: (String, String) -> Unit
) {
    var recipientEmail by remember { mutableStateOf("") }
    var recipientName by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.share_puzzle)) },
        text = {
            Column {
                OutlinedTextField(
                    value = recipientEmail,
                    onValueChange = { recipientEmail = it },
                    label = { Text(stringResource(R.string.recipient_email)) },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = recipientName,
                    onValueChange = { recipientName = it },
                    label = { Text(stringResource(R.string.recipient_name)) },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onShare(recipientEmail, recipientName) },
                enabled = recipientEmail.isNotBlank() && recipientName.isNotBlank()
            ) {
                Text(stringResource(R.string.share))
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

// MARK: - Share Options Configuration
@Composable
fun getShareOptions(): List<ShareOption> {
    return listOf(
        ShareOption(
            title = stringResource(R.string.done_share_to_social),
            icon = Icons.Default.Share,
            color = Color(0xFF1DA1F2),
            action = { context, content -> shareToSocialMedia(context, content) }
        ),
        ShareOption(
            title = stringResource(R.string.copy_to_clipboard),
            icon = Icons.Default.ContentCopy,
            color = Color(0xFF34A853),
            action = { context, content -> copyToClipboard(context, content) }
        ),
        ShareOption(
            title = stringResource(R.string.done_send_via_messages),
            icon = Icons.Default.Message,
            color = Color(0xFF0F9D58),
            action = { context, content -> shareViaMessages(context, content) }
        )
    )
}

// MARK: - Sharing Actions
fun shareToSocialMedia(context: Context, content: ShareableContent) {
    val shareText = buildShareText(content)
    val shareIntent = Intent().apply {
        action = Intent.ACTION_SEND
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, shareText)
        putExtra(Intent.EXTRA_SUBJECT, content.title)
    }

    context.startActivity(Intent.createChooser(shareIntent, context.getString(R.string.done_share_chooser_title)))
}

fun copyToClipboard(context: Context, content: ShareableContent) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
    val clip = android.content.ClipData.newPlainText("PuzzleVerse Achievement", buildShareText(content))
    clipboard.setPrimaryClip(clip)

    // Show toast (you might want to use a more elegant notification)
    android.widget.Toast.makeText(context, context.getString(R.string.copied_to_clipboard), android.widget.Toast.LENGTH_SHORT).show()
}

fun shareViaMessages(context: Context, content: ShareableContent) {
    val shareText = buildShareText(content)
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, shareText)
        setPackage("com.android.mms") // Try to open default messaging app
    }

    try {
        context.startActivity(intent)
    } catch (e: Exception) {
        // Fallback to general sharing
        shareToSocialMedia(context, content)
    }
}

// MARK: - Content Builders
fun buildShareText(content: ShareableContent): String {
    val hashtags = if (content.hashtags.isNotEmpty()) {
        " " + content.hashtags.joinToString(" ") { "#$it" }
    } else {
        " #PuzzleVerse #BrainTraining"
    }

    return when (content.type) {
        ShareType.PUZZLE_RESULT -> {
            "${content.message}$hashtags"
        }
        ShareType.ACHIEVEMENT -> {
            "🏆 ${content.title}\n${content.message}$hashtags"
        }
        ShareType.LEVEL_UP -> {
            "📈 ${content.title}\n${content.message}$hashtags"
        }
        ShareType.STREAK -> {
            "🔥 ${content.title}\n${content.message}$hashtags"
        }
        ShareType.WEEKLY_RANK -> {
            "📊 ${content.title}\n${content.message}$hashtags"
        }
        ShareType.CHALLENGE_COMPLETION -> {
            "✅ ${content.title}\n${content.message}$hashtags"
        }
        ShareType.CUSTOM_PUZZLE -> {
            "🧩 ${content.title}\n${content.message}$hashtags"
        }
    }
}

// MARK: - Helper Functions for Creating Shareable Content
fun createPuzzleResultShare(context: Context, puzzleType: String, score: Int, streak: Int): ShareableContent {
    return ShareableContent(
        type = ShareType.PUZZLE_RESULT,
        title = context.getString(R.string.puzzle_completed),
        message = context.getString(R.string.done_share_puzzle_result, puzzleType, score, streak),
        hashtags = listOf("PuzzleVerse", "BrainTraining", puzzleType.capitalize())
    )
}

fun createAchievementShare(context: Context, achievement: Achievement): ShareableContent {
    return ShareableContent(
        type = ShareType.ACHIEVEMENT,
        title = context.getString(R.string.achievement_unlocked),
        message = context.getString(R.string.done_share_achievement, context.getString(achievement.titleRes), context.getString(achievement.descriptionRes)),
        hashtags = listOf("PuzzleVerse", "Achievement", "BrainTraining")
    )
}

fun createLevelUpShare(context: Context, oldLevel: Int, newLevel: Int): ShareableContent {
    return ShareableContent(
        type = ShareType.LEVEL_UP,
        title = context.getString(R.string.level_up),
        message = context.getString(R.string.done_share_level_up, newLevel),
        hashtags = listOf("PuzzleVerse", "LevelUp", "BrainTraining")
    )
}

fun createStreakShare(context: Context, streakDays: Int): ShareableContent {
    return ShareableContent(
        type = ShareType.STREAK,
        title = context.getString(R.string.done_share_streak_title),
        message = context.getString(R.string.done_share_streak, streakDays),
        hashtags = listOf("PuzzleVerse", "Streak", "DailyChallenge")
    )
}

fun createWeeklyRankShare(context: Context, rank: Int, score: Int): ShareableContent {
    return ShareableContent(
        type = ShareType.WEEKLY_RANK,
        title = context.getString(R.string.done_share_weekly_title),
        message = context.getString(R.string.done_share_weekly_rank, rank, score),
        hashtags = listOf("PuzzleVerse", "Leaderboard", "Competition")
    )
}

fun createCustomPuzzleShare(context: Context, puzzleId: String, puzzleName: String): ShareableContent {
    return ShareableContent(
        type = ShareType.CUSTOM_PUZZLE,
        title = context.getString(R.string.done_share_custom_created_title),
        message = context.getString(R.string.done_share_custom_created, puzzleName),
        hashtags = listOf("PuzzleVerse", "CustomPuzzle", "Challenge"),
        customData = mapOf("puzzleId" to puzzleId)
    )
}