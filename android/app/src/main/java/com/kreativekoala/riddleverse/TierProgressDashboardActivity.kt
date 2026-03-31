package com.kreativekoala.riddleverse

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import androidx.compose.ui.res.stringResource
import java.util.*

class TierProgressDashboardActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            RealDataTierProgressDashboard(onBackClick = { finish() })
        }
    }
}

@Composable
fun RealDataTierProgressDashboard(onBackClick: () -> Unit) {
    val context = LocalContext.current

    // Use new clean architecture
    val userStatsManager = remember { UserStatsManager.getInstance(context) }
    val progressionEngine = remember { ProgressionEngine(context, userStatsManager) }
    val dataProvider = remember { DashboardDataProvider(context, userStatsManager, progressionEngine) }

    // Get real data
    val dashboardData by remember { mutableStateOf(dataProvider.getDashboardData()) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBackClick) {
                    Icon(Icons.Default.ArrowBack, contentDescription = stringResource(R.string.back))
                }
                Text(
                    "Progress Dashboard",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        item {
            // Real Tier Progress Section
            RealTierProgressCard(dashboardData.tierInfo, dashboardData.globalStats)
        }

        item {
            // Real Weekly Challenge Section
            RealWeeklyChallengeCard(dashboardData.weeklyChallenge)
        }

        if (dashboardData.seasonalEvent != null) {
            item {
                // Real Seasonal Event Section
                RealSeasonalEventCard(dashboardData.seasonalEvent!!)
            }
        }

        item {
            // Real Statistics Overview
            RealStatisticsOverviewCard(dashboardData.globalStats, dashboardData.streakInfo)
        }

        if (dashboardData.puzzlePerformance.isNotEmpty()) {
            item {
                // Real Puzzle Type Performance
                RealPuzzleTypePerformanceCard(dashboardData.puzzlePerformance)
            }
        }

        item {
            // Real Daily Streak Card
            RealDailyStreakCard(dashboardData.streakInfo)
        }

        item {
            // Game Time Analytics
            GameTimeAnalyticsCard(dashboardData.globalStats, dashboardData.puzzlePerformance)
        }

        if (dashboardData.globalStats.totalGamesPlayed == 0) {
            item {
                // Welcome card for new users
                WelcomeNewUserCard()
            }
        }
    }
}

@Composable
fun RealTierProgressCard(tierInfo: TierInfo, globalStats: GlobalStatsInfo) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = getTierColor(tierInfo.currentTier)),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        "${getTierEmoji(tierInfo.currentTier)} ${tierInfo.currentTier}",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        "Level ${tierInfo.currentLevel}",
                        fontSize = 16.sp,
                        color = Color.White.copy(alpha = 0.8f)
                    )
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        "${tierInfo.totalXP} XP",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    if (tierInfo.currentTier != tierInfo.nextTier) {
                        Text(
                            "${tierInfo.pointsToNextTier - tierInfo.pointsInTier} to ${tierInfo.nextTier}",
                            fontSize = 12.sp,
                            color = Color.White.copy(alpha = 0.8f)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Progress bar (only show if not at max tier)
            if (tierInfo.currentTier != tierInfo.nextTier) {
                LinearProgressIndicator(
                    progress = { tierInfo.progressPercentage },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp)),
                    color = Color.White,
                    trackColor = Color.White.copy(alpha = 0.3f)
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    "Next Tier Rewards:",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color.White
                )

                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(top = 8.dp)
                ) {
                    items(tierInfo.nextTierRewards) { reward ->
                        RewardChip(reward)
                    }
                }
            } else {
                Text(
                    "🎉 Maximum tier achieved! You're a puzzle master!",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color.White,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
fun RealWeeklyChallengeCard(challenge: WeeklyChallengeInfo) {
    var timeLeft by remember { mutableStateOf(challenge.timeUntilReset) }

    LaunchedEffect(Unit) {
        while (timeLeft > 0) {
            delay(1000L)
            timeLeft -= 1000L
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (challenge.isCompleted) Color(0xFF4CAF50) else Color(0xFFE8F5E8)
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    if (challenge.isCompleted) "🏆 Weekly Challenge Complete!" else "🎯 Weekly Challenge",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (challenge.isCompleted) Color.White else Color(0xFF2E7D32)
                )
                Text(
                    formatTimeLeft(timeLeft),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color(0xFFFF6F00)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                "Complete ${challenge.weeklyGoal} puzzles this week",
                fontSize = 16.sp,
                color = if (challenge.isCompleted) Color.White else Color.Black
            )

            Spacer(modifier = Modifier.height(8.dp))

            LinearProgressIndicator(
                progress = { challenge.progressPercentage },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp)),
                color = if (challenge.isCompleted) Color.White else Color(0xFF4CAF50),
                trackColor = (if (challenge.isCompleted) Color.White else Color.Gray).copy(alpha = 0.3f)
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    "${challenge.weeklyProgress}/${challenge.weeklyGoal} completed",
                    fontSize = 14.sp,
                    color = if (challenge.isCompleted) Color.White else Color.Black
                )
                Text(
                    if (challenge.isCompleted) "✅ ${challenge.weeklyReward} XP earned!" else "Reward: ${challenge.weeklyReward} XP",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = if (challenge.isCompleted) Color.White else Color(0xFF2E7D32)
                )
            }
        }
    }
}

@Composable
fun RealSeasonalEventCard(event: SeasonalEventInfo) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (event.isCompleted) Color(0xFFFF6F00) else Color(0xFFFFE0B2)
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                "${event.theme} ${event.eventName}",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = if (event.isCompleted) Color.White else Color(0xFFE65100)
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                event.description,
                fontSize = 14.sp,
                color = if (event.isCompleted) Color.White.copy(alpha = 0.9f) else Color(0xFF424242)
            )

            Spacer(modifier = Modifier.height(12.dp))

            LinearProgressIndicator(
                progress = { event.progressPercentage },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp)),
                color = if (event.isCompleted) Color.White else Color(0xFFFF9800),
                trackColor = (if (event.isCompleted) Color.White else Color.Gray).copy(alpha = 0.3f)
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    if (event.isCompleted) "🎉 Event Complete!" else "${event.progress}/${event.maxProgress}",
                    fontSize = 14.sp,
                    color = if (event.isCompleted) Color.White else Color.Black
                )
                Text(
                    "Ends ${formatEventTime(event.endTime)}",
                    fontSize = 12.sp,
                    color = if (event.isCompleted) Color.White.copy(alpha = 0.8f) else Color.Gray
                )
            }
        }
    }
}

@Composable
fun RealStatisticsOverviewCard(globalStats: GlobalStatsInfo, streakInfo: StreakDisplayInfo) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFF3E5F5)),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                "📊 Your Statistics",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF7B1FA2)
            )

            Spacer(modifier = Modifier.height(16.dp))

            // First row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                DashboardStatItem("Games", globalStats.totalGamesPlayed.toString(), "🎮")
                DashboardStatItem("Level", globalStats.currentLevel.toString(), "🏆")
                DashboardStatItem("Total XP", globalStats.totalXP.toString(), "⭐")
                DashboardStatItem("Streak", streakInfo.currentQuestionStreak.toString(), "🔥")
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Second row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                DashboardStatItem("Hours", String.format("%.1f", globalStats.totalTimeHours), "⏰")
                DashboardStatItem("Avg XP", globalStats.averageXPPerGame.toString(), "📊")
                DashboardStatItem("Daily", streakInfo.currentDailyStreak.toString(), "📅")
                DashboardStatItem("Rank", globalStats.rank, "🏅")
            }
        }
    }
}

@Composable
fun RealPuzzleTypePerformanceCard(puzzleStats: List<PuzzlePerformanceInfo>) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFE1F5FE)),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                "🎮 Puzzle Performance",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF0277BD)
            )

            Spacer(modifier = Modifier.height(16.dp))

            puzzleStats.take(5).forEach { stat ->
                RealPuzzleStatRow(stat)
                Spacer(modifier = Modifier.height(8.dp))
            }

            if (puzzleStats.isEmpty()) {
                Text(
                    "Complete some puzzles to see your performance stats!",
                    fontSize = 14.sp,
                    color = Color.Gray,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
fun RealPuzzleStatRow(stat: PuzzlePerformanceInfo) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(
                stat.puzzleType,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium
            )
            Text(
                "${stat.totalSolved} solved • ${(stat.accuracy * 100).toInt()}% accuracy • High: ${stat.highScore}",
                fontSize = 12.sp,
                color = Color.Gray
            )
        }

        Text(
            "${stat.averageTime.toInt()}s avg",
            fontSize = 12.sp,
            color = Color(0xFF0277BD),
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
fun RealDailyStreakCard(streakInfo: StreakDisplayInfo) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (streakInfo.streakActive) Color(0xFFFF5722) else Color(0xFFFFEBEE)
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                "🔥 Daily Streak",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = if (streakInfo.streakActive) Color.White else Color(0xFFD32F2F)
            )

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                DashboardStatItem(
                    "Current",
                    streakInfo.currentDailyStreak.toString(),
                    "🔥",
                    textColor = if (streakInfo.streakActive) Color.White else Color.Black
                )
                DashboardStatItem(
                    "Best Ever",
                    streakInfo.longestDailyStreak.toString(),
                    "🏆",
                    textColor = if (streakInfo.streakActive) Color.White else Color.Black
                )
                DashboardStatItem(
                    "Status",
                    if (streakInfo.hasPlayedToday) "Active" else "Inactive",
                    "✅",
                    textColor = if (streakInfo.streakActive) Color.White else Color.Black
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                streakInfo.streakMessage,
                fontSize = 14.sp,
                textAlign = TextAlign.Center,
                color = if (streakInfo.streakActive) Color.White else Color(0xFF666666),
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
fun GameTimeAnalyticsCard(globalStats: GlobalStatsInfo, puzzleStats: List<PuzzlePerformanceInfo>) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFF0F4C3)),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                "⏱️ Time Analytics",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF689F38)
            )

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                DashboardStatItem(
                    "Total Hours",
                    String.format("%.1f", globalStats.totalTimeHours),
                    "⏰"
                )

                val avgTime = if (globalStats.totalGamesPlayed > 0) {
                    (globalStats.totalTimeHours * 3600 / globalStats.totalGamesPlayed).toInt()
                } else 0

                DashboardStatItem(
                    "Avg/Game",
                    "${avgTime}s",
                    "⚡"
                )

                val fastestPuzzle = puzzleStats.minByOrNull { it.averageTime }
                DashboardStatItem(
                    "Fastest Type",
                    fastestPuzzle?.puzzleType?.take(8) ?: "None",
                    "🚀"
                )
            }

            if (globalStats.totalTimeHours > 0) {
                Spacer(modifier = Modifier.height(12.dp))

                val days = (globalStats.totalTimeHours / 24).toInt()
                val hours = (globalStats.totalTimeHours % 24).toInt()

                Text(
                    "🎯 You've spent ${if (days > 0) "${days} days and " else ""}${hours} hours sharpening your mind!",
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center,
                    color = Color(0xFF388E3C),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
fun WelcomeNewUserCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFE8F5E8)),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                "🎉 Welcome to Puzzle Universe!",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF2E7D32),
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                "Start playing puzzles to unlock your progress dashboard. Every game earns XP, builds streaks, and unlocks achievements!",
                fontSize = 14.sp,
                color = Color(0xFF424242),
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                "🎯 Complete your first puzzle to begin your journey!",
                fontSize = 12.sp,
                color = Color(0xFF689F38),
                textAlign = TextAlign.Center,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

// Enhanced helper functions
@Composable
fun DashboardStatItem(
    label: String,
    value: String,
    emoji: String,
    textColor: Color = Color.Black
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(emoji, fontSize = 20.sp)
        Text(
            value,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = textColor
        )
        Text(
            label,
            fontSize = 12.sp,
            color = textColor.copy(alpha = 0.7f)
        )
    }
}

@Composable
fun RewardChip(reward: String) {
    Surface(
        color = Color.White.copy(alpha = 0.8f),
        shape = RoundedCornerShape(12.dp)
    ) {
        Text(
            reward,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            fontSize = 12.sp,
            color = Color.Black
        )
    }
}

// Keep existing utility functions
fun getTierColor(tier: String): Color {
    return when (tier) {
        "Bronze" -> Color(0xFFD2691E)
        "Silver" -> Color(0xFFC0C0C0)
        "Gold" -> Color(0xFFFFD700)
        "Platinum" -> Color(0xFF98D8E8)
        "Diamond" -> Color(0xFFB9F2FF)
        "Master" -> Color(0xFF9C27B0)
        else -> Color.Gray
    }
}

fun getTierEmoji(tier: String): String {
    return when (tier) {
        "Bronze" -> "🥉"
        "Silver" -> "🥈"
        "Gold" -> "🥇"
        "Platinum" -> "💎"
        "Diamond" -> "💠"
        "Master" -> "👑"
        else -> "🏆"
    }
}

fun formatTimeLeft(ms: Long): String {
    val days = ms / (24 * 60 * 60 * 1000)
    val hours = (ms / (60 * 60 * 1000)) % 24
    return "${days}d ${hours}h"
}

fun formatEventTime(timestamp: Long): String {
    val sdf = SimpleDateFormat("MMM dd", Locale.getDefault())
    return sdf.format(Date(timestamp))
}