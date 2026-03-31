package com.kreativekoala.riddleverse

import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Essential UI Components for Puzzle Screens
 * These are used across all puzzle activities and need to work with the new architecture
 */

// ================================
// LEVEL PROGRESS BAR COMPONENT
// Used in puzzle headers to show XP progress
// ================================

@Composable
fun LevelProgressBar(
    level: UserLevel,
    modifier: Modifier = Modifier,
    showLabel: Boolean = true,
    compact: Boolean = false
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (showLabel) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "${stringResource(R.string.level_label)} ${level.level}",
                    fontSize = if (compact) 12.sp else 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )

                if (!compact) {
                    Text(
                        text = "${level.currentXP}/${level.xpToNextLevel} XP",
                        fontSize = 10.sp,
                        color = Color.White.copy(alpha = 0.8f)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        LinearProgressIndicator(
            progress = level.progressPercentage,
            modifier = Modifier
                .fillMaxWidth()
                .height(if (compact) 4.dp else 6.dp)
                .clip(RoundedCornerShape(if (compact) 2.dp else 3.dp)),
            color = Color(0xFFFFD700),
            trackColor = Color.White.copy(alpha = 0.3f)
        )

        if (compact && showLabel) {
            Text(
                text = "${level.currentXP}/${level.xpToNextLevel}",
                fontSize = 8.sp,
                color = Color.White.copy(alpha = 0.7f)
            )
        }
    }
}

// ================================
// STREAK DISPLAY COMPONENT
// Shows current streak with multiplier
// ================================

@Composable
fun StreakDisplay(
    streakInfo: StreakInfo,
    modifier: Modifier = Modifier,
    showDailyStreak: Boolean = false,
    compact: Boolean = false
) {
    if (streakInfo.currentStreak > 0 || (showDailyStreak && streakInfo.dailyStreak > 0)) {
        Row(
            modifier = modifier,
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(if (compact) 6.dp else 8.dp)
        ) {
            // Question streak
            if (streakInfo.currentStreak > 0) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "⚡",
                        fontSize = if (compact) 14.sp else 16.sp
                    )
                    Text(
                        text = streakInfo.currentStreak.toString(),
                        fontSize = if (compact) 12.sp else 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFFF6F00)
                    )
                    if (streakInfo.hasStreakBonus && !compact) {
                        Text(
                            text = "×${streakInfo.streakMultiplier}",
                            fontSize = 10.sp,
                            color = Color(0xFFFF9800),
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            // Daily streak
            if (showDailyStreak && streakInfo.dailyStreak > 0) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "🔥",
                        fontSize = if (compact) 14.sp else 16.sp
                    )
                    Text(
                        text = "${streakInfo.dailyStreak}d",
                        fontSize = if (compact) 12.sp else 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFFF5722)
                    )
                }
            }
        }
    }
}

// ================================
// ENHANCED STREAK DISPLAY
// For when you have StreakDisplayInfo from dashboard
// ================================

@Composable
fun EnhancedStreakDisplay(
    streakInfo: StreakDisplayInfo,
    modifier: Modifier = Modifier,
    showBonusInfo: Boolean = false
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Question streak
        if (streakInfo.currentQuestionStreak > 0) {
            StreakItem(
                emoji = "⚡",
                value = streakInfo.currentQuestionStreak.toString(),
                label = if (showBonusInfo) "×${streakInfo.questionStreakMultiplier}" else null,
                color = Color(0xFFFF6F00)
            )
        }

        // Daily streak
        if (streakInfo.currentDailyStreak > 0) {
            StreakItem(
                emoji = "🔥",
                value = "${streakInfo.currentDailyStreak}d",
                label = if (showBonusInfo && streakInfo.dailyStreakBonus > 1.0f) "×${streakInfo.dailyStreakBonus}" else null,
                color = Color(0xFFFF5722)
            )
        }
    }
}

@Composable
private fun StreakItem(
    emoji: String,
    value: String,
    label: String?,
    color: Color
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(emoji, fontSize = 16.sp)
        Text(
            text = value,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = color
        )
        if (label != null) {
            Text(
                text = label,
                fontSize = 10.sp,
                color = color.copy(alpha = 0.8f),
                fontWeight = FontWeight.Medium
            )
        }
    }
}

// ================================
// PUZZLE HEADER COMPONENT
// Complete header with level, streak, and score
// ================================

@Composable
fun PuzzleHeader(
    level: UserLevel,
    streakInfo: StreakInfo,
    currentScore: Int = 0,
    modifier: Modifier = Modifier,
    backgroundColor: Color = Color.Transparent,
    onBack: (() -> Unit)? = null,
    title: String? = null
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(
                if (backgroundColor != Color.Transparent) {
                    Brush.horizontalGradient(
                        listOf(backgroundColor, backgroundColor.copy(alpha = 0.8f))
                    )
                } else {
                    Brush.horizontalGradient(listOf(Color.Transparent, Color.Transparent))
                }
            )
            .statusBarsPadding()
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left side - Back button and title
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (onBack != null) {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = stringResource(R.string.back),
                            tint = Color.White
                        )
                    }
                }

                if (title != null) {
                    Text(
                        text = title,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }

            // Center - Level Progress
            LevelProgressBar(
                level = level,
                modifier = Modifier.width(120.dp),
                compact = true
            )

            // Right side - Streak and Score
            Column(
                horizontalAlignment = Alignment.End
            ) {
                if (currentScore > 0) {
                    Text(
                        text = "$currentScore pts",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFFFD700)
                    )
                }

                StreakDisplay(
                    streakInfo = streakInfo,
                    compact = true
                )
            }
        }
    }
}

// ================================
// PUZZLE PROGRESS CARD
// Shows current session progress
// ================================

@Composable
fun PuzzleProgressCard(
    questionsAnswered: Int,
    totalQuestions: Int,
    correctAnswers: Int,
    currentScore: Int,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = Color.White.copy(alpha = 0.9f)
        ),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            ProgressItem(
                label = stringResource(R.string.progress),
                value = "$questionsAnswered/$totalQuestions",
                icon = "📊",
                color = Color(0xFF2196F3)
            )

            ProgressItem(
                label = stringResource(R.string.correct_answers),
                value = correctAnswers.toString(),
                icon = "✅",
                color = Color(0xFF4CAF50)
            )

            ProgressItem(
                label = stringResource(R.string.score_label),
                value = currentScore.toString(),
                icon = "⭐",
                color = Color(0xFFFF9800)
            )

            val accuracy = if (questionsAnswered > 0) {
                (correctAnswers.toFloat() / questionsAnswered * 100).toInt()
            } else 0

            ProgressItem(
                label = stringResource(R.string.accuracy),
                value = "$accuracy%",
                icon = "🎯",
                color = Color(0xFF9C27B0)
            )
        }
    }
}

@Composable
private fun ProgressItem(
    label: String,
    value: String,
    icon: String,
    color: Color
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(icon, fontSize = 16.sp)
        Text(
            text = value,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = color
        )
        Text(
            text = label,
            fontSize = 10.sp,
            color = Color.Gray
        )
    }
}

// ================================
// COMPOSE HELPERS FOR PUZZLES
// ================================

/**
 * Remember progression data for puzzle screens
 */
@Composable
fun rememberPuzzleProgressionData(): PuzzleProgressionData {
    val context = LocalContext.current
    val userStatsManager = remember { UserStatsManager.getInstance(context) }
    val progressionEngine = remember { ProgressionEngine(context, userStatsManager) }

    // Get current data
    val level = remember { progressionEngine.getCurrentLevel() }
    val questionStreak = remember { progressionEngine.getCurrentStreak() }
    val bestStreak = remember { progressionEngine.getBestStreak() }
    val dailyStreak = remember { progressionEngine.getCurrentDailyStreak() }

    val streakInfo = StreakInfo(
        currentStreak = questionStreak,
        bestStreak = bestStreak,
        streakMultiplier = when {
            questionStreak >= 10 -> 2.0f
            questionStreak >= 5 -> 1.5f
            questionStreak >= 3 -> 1.25f
            else -> 1.0f
        },
        dailyStreak = dailyStreak,
        hasDailyStreakBonus = dailyStreak >= 3
    )

    return PuzzleProgressionData(
        level = level,
        streakInfo = streakInfo,
        userStatsManager = userStatsManager,
        progressionEngine = progressionEngine
    )
}

/**
 * Data class to hold puzzle progression information
 */
data class PuzzleProgressionData(
    val level: UserLevel,
    val streakInfo: StreakInfo,
    val userStatsManager: UserStatsManager,
    val progressionEngine: ProgressionEngine
)