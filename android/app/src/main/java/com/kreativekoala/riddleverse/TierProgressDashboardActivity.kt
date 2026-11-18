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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import com.kreativekoala.riddleverse.ui.theme.*
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import androidx.compose.ui.res.stringResource
import java.util.*

class TierProgressDashboardActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            com.kreativekoala.riddleverse.ui.RvScreenHost {
                RealDataTierProgressDashboard(onBackClick = { finish() })
            }
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
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                }
                Text(
                    stringResource(R.string.stats_progress_dashboard),
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
        colors = CardDefaults.cardColors(containerColor = RvSurface),
        border = androidx.compose.foundation.BorderStroke(2.dp, getTierColor(tierInfo.currentTier)),
        shape = RoundedCornerShape(24.dp)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        "${getTierEmoji(tierInfo.currentTier)} ${stringResource(tierInfo.currentTierRes)}",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = RvInk
                    )
                    Text(
                        stringResource(R.string.stats_level_number, tierInfo.currentLevel),
                        fontSize = 16.sp,
                        color = RvInkSoft
                    )
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        stringResource(R.string.home_xp_amount, tierInfo.totalXP),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = RvInk
                    )
                    if (tierInfo.currentTier != tierInfo.nextTier) {
                        Text(
                            stringResource(R.string.stats_xp_to_next_tier, tierInfo.pointsToNextTier - tierInfo.pointsInTier, stringResource(tierInfo.nextTierRes)),
                            fontSize = 12.sp,
                            color = RvInkSoft
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
                    color = getTierColor(tierInfo.currentTier),
                    trackColor = RvOutline
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    stringResource(R.string.stats_next_tier_rewards),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = RvInk
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
                    stringResource(R.string.stats_max_tier_master),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = RvInk,
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
            containerColor = if (challenge.isCompleted) RvSuccess else RvSurface
        ),
        border = androidx.compose.foundation.BorderStroke(2.dp, RvOutline),
        shape = RoundedCornerShape(24.dp)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    if (challenge.isCompleted) stringResource(R.string.stats_weekly_complete) else stringResource(R.string.stats_weekly_challenge),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (challenge.isCompleted) RvOnTone else RvMintEdge
                )
                Text(
                    formatTimeLeft(timeLeft),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = RvFlame
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                LocalContext.current.resources.getQuantityString(R.plurals.stats_weekly_goal, challenge.weeklyGoal, challenge.weeklyGoal),
                fontSize = 16.sp,
                color = if (challenge.isCompleted) RvOnTone else RvInk
            )

            Spacer(modifier = Modifier.height(8.dp))

            LinearProgressIndicator(
                progress = { challenge.progressPercentage },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp)),
                color = if (challenge.isCompleted) RvOnTone else RvSuccess,
                trackColor = (if (challenge.isCompleted) RvOnTone else RvInkSoft).copy(alpha = 0.3f)
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    stringResource(R.string.stats_progress_completed, challenge.weeklyProgress, challenge.weeklyGoal),
                    fontSize = 14.sp,
                    color = if (challenge.isCompleted) RvOnTone else RvInk
                )
                Text(
                    if (challenge.isCompleted) stringResource(R.string.stats_xp_earned, challenge.weeklyReward) else stringResource(R.string.stats_reward_xp, challenge.weeklyReward),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = if (challenge.isCompleted) RvOnTone else RvMintEdge
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
            containerColor = if (event.isCompleted) RvFlame else RvSurface
        ),
        border = androidx.compose.foundation.BorderStroke(2.dp, RvOutline),
        shape = RoundedCornerShape(24.dp)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                "${event.theme} ${event.eventName}",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = if (event.isCompleted) RvOnTone else RvSunEdge
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                event.description,
                fontSize = 14.sp,
                color = if (event.isCompleted) RvOnTone.copy(alpha = 0.9f) else RvInkSoft
            )

            Spacer(modifier = Modifier.height(12.dp))

            LinearProgressIndicator(
                progress = { event.progressPercentage },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp)),
                color = if (event.isCompleted) RvOnTone else RvWarning,
                trackColor = (if (event.isCompleted) RvOnTone else RvInkSoft).copy(alpha = 0.3f)
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    if (event.isCompleted) stringResource(R.string.stats_event_complete) else "${event.progress}/${event.maxProgress}",
                    fontSize = 14.sp,
                    color = if (event.isCompleted) RvOnTone else RvInk
                )
                Text(
                    stringResource(R.string.stats_ends_on, formatEventTime(event.endTime)),
                    fontSize = 12.sp,
                    color = if (event.isCompleted) RvOnTone.copy(alpha = 0.8f) else RvInkSoft
                )
            }
        }
    }
}

@Composable
fun RealStatisticsOverviewCard(globalStats: GlobalStatsInfo, streakInfo: StreakDisplayInfo) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = RvSurface),
        border = androidx.compose.foundation.BorderStroke(2.dp, RvOutline),
        shape = RoundedCornerShape(24.dp)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                stringResource(R.string.stats_your_statistics),
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = RvGrapeEdge
            )

            Spacer(modifier = Modifier.height(16.dp))

            // First row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                DashboardStatItem(stringResource(R.string.tab_games), globalStats.totalGamesPlayed.toString(), "🎮")
                DashboardStatItem(stringResource(R.string.level_label), globalStats.currentLevel.toString(), "🏆")
                DashboardStatItem(stringResource(R.string.stats_total_xp), globalStats.totalXP.toString(), "⭐")
                DashboardStatItem(stringResource(R.string.streak_label), streakInfo.currentQuestionStreak.toString(), "🔥")
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Second row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                DashboardStatItem(stringResource(R.string.stats_hours), String.format("%.1f", globalStats.totalTimeHours), "⏰")
                DashboardStatItem(stringResource(R.string.stats_avg_xp), globalStats.averageXPPerGame.toString(), "📊")
                DashboardStatItem(stringResource(R.string.stats_daily), streakInfo.currentDailyStreak.toString(), "📅")
                DashboardStatItem(stringResource(R.string.rank_label), globalStats.rank, "🏅")
            }
        }
    }
}

@Composable
fun RealPuzzleTypePerformanceCard(puzzleStats: List<PuzzlePerformanceInfo>) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = RvSurface),
        border = androidx.compose.foundation.BorderStroke(2.dp, RvOutline),
        shape = RoundedCornerShape(24.dp)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                stringResource(R.string.stats_puzzle_performance),
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = RvSkyEdge
            )

            Spacer(modifier = Modifier.height(16.dp))

            puzzleStats.take(5).forEach { stat ->
                RealPuzzleStatRow(stat)
                Spacer(modifier = Modifier.height(8.dp))
            }

            if (puzzleStats.isEmpty()) {
                Text(
                    stringResource(R.string.stats_complete_puzzles_prompt),
                    fontSize = 14.sp,
                    color = RvInkSoft,
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
                stringResource(R.string.stats_puzzle_stat_line, stat.totalSolved, (stat.accuracy * 100).toInt(), stat.highScore),
                fontSize = 12.sp,
                color = RvInkSoft
            )
        }

        Text(
            stringResource(R.string.stats_seconds_avg, stat.averageTime.toInt()),
            fontSize = 12.sp,
            color = RvSkyEdge,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
fun RealDailyStreakCard(streakInfo: StreakDisplayInfo) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (streakInfo.streakActive) RvCoral else RvSurface
        ),
        border = androidx.compose.foundation.BorderStroke(2.dp, RvOutline),
        shape = RoundedCornerShape(24.dp)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                "🔥 ${stringResource(R.string.daily_streak)}",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = if (streakInfo.streakActive) RvOnTone else RvCoralEdge
            )

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                DashboardStatItem(
                    stringResource(R.string.common_current),
                    streakInfo.currentDailyStreak.toString(),
                    "🔥",
                    textColor = if (streakInfo.streakActive) RvOnTone else RvInk
                )
                DashboardStatItem(
                    stringResource(R.string.stats_best_ever),
                    streakInfo.longestDailyStreak.toString(),
                    "🏆",
                    textColor = if (streakInfo.streakActive) RvOnTone else RvInk
                )
                DashboardStatItem(
                    stringResource(R.string.stats_status),
                    if (streakInfo.hasPlayedToday) stringResource(R.string.stats_active) else stringResource(R.string.stats_inactive),
                    "✅",
                    textColor = if (streakInfo.streakActive) RvOnTone else RvInk
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                streakInfo.streakMessage,
                fontSize = 14.sp,
                textAlign = TextAlign.Center,
                color = if (streakInfo.streakActive) RvOnTone else RvInkSoft,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
fun GameTimeAnalyticsCard(globalStats: GlobalStatsInfo, puzzleStats: List<PuzzlePerformanceInfo>) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = RvSurface),
        border = androidx.compose.foundation.BorderStroke(2.dp, RvOutline),
        shape = RoundedCornerShape(24.dp)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                stringResource(R.string.stats_time_analytics),
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = RvMintEdge
            )

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                DashboardStatItem(
                    stringResource(R.string.stats_total_hours),
                    String.format("%.1f", globalStats.totalTimeHours),
                    "⏰"
                )

                val avgTime = if (globalStats.totalGamesPlayed > 0) {
                    (globalStats.totalTimeHours * 3600 / globalStats.totalGamesPlayed).toInt()
                } else 0

                DashboardStatItem(
                    stringResource(R.string.stats_avg_per_game),
                    stringResource(R.string.stats_seconds_short, avgTime),
                    "⚡"
                )

                val fastestPuzzle = puzzleStats.minByOrNull { it.averageTime }
                DashboardStatItem(
                    stringResource(R.string.stats_fastest_type),
                    fastestPuzzle?.puzzleType?.take(8) ?: stringResource(R.string.stats_none),
                    "🚀"
                )
            }

            if (globalStats.totalTimeHours > 0) {
                Spacer(modifier = Modifier.height(12.dp))

                val days = (globalStats.totalTimeHours / 24).toInt()
                val hours = (globalStats.totalTimeHours % 24).toInt()

                val resources = LocalContext.current.resources
                Text(
                    if (days > 0) resources.getQuantityString(R.plurals.stats_time_spent_days_hours, days, days, hours)
                    else resources.getQuantityString(R.plurals.stats_time_spent_hours, hours, hours),
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center,
                    color = RvMintEdge,
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
        colors = CardDefaults.cardColors(containerColor = RvSurface),
        border = androidx.compose.foundation.BorderStroke(2.dp, RvOutline),
        shape = RoundedCornerShape(24.dp)
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                stringResource(R.string.stats_welcome_title),
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = RvMintEdge,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                stringResource(R.string.stats_welcome_body),
                fontSize = 14.sp,
                color = RvInkSoft,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                stringResource(R.string.stats_welcome_cta),
                fontSize = 12.sp,
                color = RvMintEdge,
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
    textColor: Color = RvInk
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
fun RewardChip(@androidx.annotation.StringRes reward: Int) {
    Surface(
        color = RvSurfaceRaised.copy(alpha = 0.8f),
        shape = RoundedCornerShape(12.dp)
    ) {
        Text(
            stringResource(reward),
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            fontSize = 12.sp,
            color = RvInk
        )
    }
}

// Keep existing utility functions
fun getTierColor(tier: String): Color {
    return when (tier) {
        "Bronze" -> Color(0xFFD2691E)
        "Silver" -> Color(0xFFC0C0C0)
        "Gold" -> RvSun
        "Platinum" -> Color(0xFF98D8E8)
        "Diamond" -> Color(0xFFB9F2FF)
        "Master" -> RvGrape
        else -> RvInkSoft
    }
}

/** Display name for a tier id ("Bronze", ...). The id itself is what gets compared; only show this. */
fun tierNameRes(tier: String): Int = when (tier) {
    "Silver" -> R.string.stats_tier_silver
    "Gold" -> R.string.stats_tier_gold
    "Platinum" -> R.string.stats_tier_platinum
    "Diamond" -> R.string.stats_tier_diamond
    "Master" -> R.string.xa_difficulty_master
    else -> R.string.stats_tier_bronze
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

@Composable
fun formatTimeLeft(ms: Long): String {
    val days = ms / (24 * 60 * 60 * 1000)
    val hours = (ms / (60 * 60 * 1000)) % 24
    return stringResource(R.string.stats_time_left_short, days, hours)
}

fun formatEventTime(timestamp: Long): String {
    val sdf = SimpleDateFormat("MMM dd", Locale.getDefault())
    return sdf.format(Date(timestamp))
}