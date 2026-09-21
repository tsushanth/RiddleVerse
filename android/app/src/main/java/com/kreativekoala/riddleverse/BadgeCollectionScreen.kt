package com.kreativekoala.riddleverse

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.compose.setContent
import androidx.compose.ui.res.stringResource
import com.kreativekoala.riddleverse.ui.theme.*

class BadgeCollectionActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            RiddleVerseTheme {
                BadgeCollectionScreen(
                    onBack = { finish() }
                )
            }
        }
    }
}

@Composable
fun BadgeCollectionScreen(onBack: () -> Unit = {}) {
    val context = LocalContext.current

    // Use new clean architecture
    val userStatsManager = remember { UserStatsManager.getInstance(context) }
    val progressionEngine = remember { ProgressionEngine(context, userStatsManager) }
    val dataProvider = remember { DashboardDataProvider(context, userStatsManager, progressionEngine) }

    // Get real data
    val dashboardData by remember { mutableStateOf(dataProvider.getDashboardData()) }
    val achievements = dashboardData.achievements
    val currentLevel = dashboardData.levelInfo
    val streakInfo = dashboardData.streakInfo
    val tierInfo = dashboardData.tierInfo

    // Categorize achievements
    val unlockedAchievements = achievements.filter { it.isUnlocked }
    val lockedAchievements = achievements.filter { !it.isUnlocked }
    val inProgressAchievements = lockedAchievements.filter { it.progress > 0 }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(16.dp)
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.Default.ArrowBack,
                    contentDescription = stringResource(R.string.back)
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "Badge Collection",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "${unlockedAchievements.size}/${achievements.size} unlocked",
                    fontSize = 14.sp,
                    color = RvInkSoft
                )
            }

            // Collection completion percentage
            val completionPercentage = if (achievements.isNotEmpty()) {
                (unlockedAchievements.size.toFloat() / achievements.size.toFloat() * 100).toInt()
            } else 0

            Box(
                modifier = Modifier
                    .background(
                        RvViolet,
                        RoundedCornerShape(12.dp)
                    )
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text(
                    "$completionPercentage%",
                    color = RvOnTone,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Enhanced Progress Summary
        EnhancedProgressSummaryCard(currentLevel, streakInfo, tierInfo, dashboardData.globalStats)

        Spacer(modifier = Modifier.height(16.dp))

        // Collection Stats
        CollectionStatsRow(
            total = achievements.size,
            unlocked = unlockedAchievements.size,
            inProgress = inProgressAchievements.size
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Achievements Grid with sections
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 16.dp)
        ) {
            // Recently unlocked section
            val recentlyUnlocked = unlockedAchievements.take(3)
            if (recentlyUnlocked.isNotEmpty()) {
                item {
                    Text(
                        "✨ Recently Unlocked",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = RvViolet,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                }
                items(recentlyUnlocked) { achievement ->
                    EnhancedAchievementBadgeItem(
                        achievement = achievement,
                        isHighlighted = true
                    )
                }

                // Spacer item
                item {
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }

            // In progress section
            if (inProgressAchievements.isNotEmpty()) {
                item {
                    Text(
                        "🎯 In Progress",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = RvWarning,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                }
                items(inProgressAchievements) { achievement ->
                    EnhancedAchievementBadgeItem(achievement = achievement)
                }

                // Spacer item
                item {
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }

            // All other unlocked achievements
            val otherUnlocked = unlockedAchievements.drop(3)
            if (otherUnlocked.isNotEmpty()) {
                item {
                    Text(
                        "🏆 Unlocked",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = RvSuccess,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                }
                items(otherUnlocked) { achievement ->
                    EnhancedAchievementBadgeItem(achievement = achievement)
                }

                // Spacer item
                item {
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }

            // Locked achievements
            val purelyLocked = lockedAchievements.filter { it.progress == 0 }
            if (purelyLocked.isNotEmpty()) {
                item {
                    Text(
                        "🔒 Locked",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = RvInkSoft,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                }
                items(purelyLocked) { achievement ->
                    EnhancedAchievementBadgeItem(achievement = achievement)
                }
            }
        }
    }
}

@Composable
fun EnhancedProgressSummaryCard(
    level: UserLevel,
    streakInfo: StreakDisplayInfo,
    tierInfo: TierInfo,
    globalStats: GlobalStatsInfo
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = RvViolet
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = androidx.compose.foundation.BorderStroke(2.dp, RvOutline),
        shape = RoundedCornerShape(24.dp)
    ) {
        Box(
            modifier = Modifier.fillMaxWidth()
        ) {
            // Gradient background
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        brush = Brush.linearGradient(listOf(RvViolet, RvViolet)),
                        shape = RoundedCornerShape(24.dp)
                    )
            )

            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Tier and Level
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            "${getTierEmoji(tierInfo.currentTier)} ${tierInfo.currentTier}",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = RvOnTone
                        )
                        Text(
                            "Level ${level.level}",
                            fontSize = 14.sp,
                            color = RvOnTone.copy(alpha = 0.9f)
                        )
                    }

                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            "${level.totalXP} XP",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = RvOnTone
                        )
                        Text(
                            "Total Earned",
                            fontSize = 12.sp,
                            color = RvOnTone.copy(alpha = 0.8f)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Stats Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    EnhancedStatItem(
                        "Games Played",
                        globalStats.totalGamesPlayed.toString(),
                        "🎮"
                    )
                    EnhancedStatItem(
                        "Current Streak",
                        streakInfo.currentQuestionStreak.toString(),
                        "⚡"
                    )
                    EnhancedStatItem(
                        stringResource(R.string.daily_streak),
                        streakInfo.currentDailyStreak.toString(),
                        "🔥"
                    )
                    EnhancedStatItem(
                        stringResource(R.string.best_streak),
                        streakInfo.bestQuestionStreak.toString(),
                        "💫"
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Level progress bar
                if (level.xpToNextLevel > 0) {
                    Column {
                        Text(
                            "Next Level Progress",
                            fontSize = 12.sp,
                            color = RvOnTone.copy(alpha = 0.8f)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        LinearProgressIndicator(
                            progress = level.progressPercentage,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp)),
                            color = RvOnTone,
                            trackColor = RvOnTone.copy(alpha = 0.3f)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            "${level.currentXP}/${level.xpToNextLevel} XP to Level ${level.level + 1}",
                            fontSize = 10.sp,
                            color = RvOnTone.copy(alpha = 0.7f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun CollectionStatsRow(total: Int, unlocked: Int, inProgress: Int) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        StatCard("Total", total.toString(), RvSky, "🏅")
        StatCard("Unlocked", unlocked.toString(), RvSuccess, "✅")
        StatCard("In Progress", inProgress.toString(), RvWarning, "⏳")
        StatCard("Locked", (total - unlocked).toString(), RvInkSoft, "🔒")
    }
}

@Composable
fun StatCard(label: String, value: String, color: Color, emoji: String) {
    Card(
        modifier = Modifier
            .width(80.dp)
            .height(70.dp),
        colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.1f)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(emoji, fontSize = 16.sp)
            Text(
                value,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = color
            )
            Text(
                label,
                fontSize = 9.sp,
                color = color.copy(alpha = 0.8f),
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
fun EnhancedStatItem(label: String, value: String, emoji: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(emoji, fontSize = 18.sp)
        Text(
            value,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = RvOnTone
        )
        Text(
            label,
            fontSize = 10.sp,
            color = RvOnTone.copy(alpha = 0.8f),
            textAlign = TextAlign.Center
        )
    }
}

@Composable
fun EnhancedAchievementBadgeItem(
    achievement: Achievement,
    isHighlighted: Boolean = false
) {
    val backgroundColor = when {
        isHighlighted -> RvSun.copy(alpha = 0.4f)
        achievement.isUnlocked -> RvSuccess.copy(alpha = 0.2f)
        achievement.progress > 0 -> RvWarning.copy(alpha = 0.2f)
        else -> RvSurface.copy(alpha = 0.2f)
    }

    val borderColor = when {
        isHighlighted -> RvSun
        achievement.isUnlocked -> RvSuccess
        achievement.progress > 0 -> RvWarning
        else -> RvDisabled
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding(4.dp)
    ) {
        Box(
            modifier = Modifier
                .size(if (isHighlighted) 85.dp else 75.dp)
                .clip(CircleShape)
                .background(backgroundColor)
                .border(
                    if (isHighlighted) 3.dp else 2.dp,
                    borderColor,
                    CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            if (achievement.isUnlocked) {
                Text(
                    text = achievement.icon,
                    fontSize = if (isHighlighted) 36.sp else 28.sp
                )

                // Special sparkle effect for highlighted
                if (isHighlighted) {
                    Box(
                        modifier = Modifier
                            .size(20.dp)
                            .offset(x = 25.dp, y = (-25).dp)
                            .background(RvSun, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("✨", fontSize = 10.sp)
                    }
                }
            } else {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = "Locked",
                    tint = RvInkSoft,
                    modifier = Modifier.size(if (isHighlighted) 36.dp else 28.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = achievement.title,
            fontSize = if (isHighlighted) 13.sp else 11.sp,
            fontWeight = if (isHighlighted) FontWeight.Bold else FontWeight.Medium,
            maxLines = 2,
            textAlign = TextAlign.Center,
            color = if (isHighlighted) RvSunEdge else RvInk
        )

        // Progress for incomplete achievements
        if (!achievement.isUnlocked && achievement.maxProgress > 1) {
            Spacer(modifier = Modifier.height(4.dp))
            LinearProgressIndicator(
                progress = achievement.progressPercentage,
                modifier = Modifier
                    .width(if (isHighlighted) 65.dp else 55.dp)
                    .height(3.dp)
                    .clip(RoundedCornerShape(1.5.dp)),
                color = borderColor,
                trackColor = RvInkSoft.copy(alpha = 0.3f)
            )
            Text(
                "${achievement.progress}/${achievement.maxProgress}",
                fontSize = 9.sp,
                color = RvInkSoft,
                fontWeight = FontWeight.Medium
            )
        }
    }
}