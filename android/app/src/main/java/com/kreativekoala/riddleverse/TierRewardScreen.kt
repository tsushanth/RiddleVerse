package com.kreativekoala.riddleverse

import com.kreativekoala.riddleverse.ui.RvActivityTheme
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.annotation.StringRes
import androidx.compose.ui.res.stringResource
import com.kreativekoala.riddleverse.ui.theme.RiddleVerseTheme
import com.kreativekoala.riddleverse.ui.theme.RvGrape
import com.kreativekoala.riddleverse.ui.theme.RvInk
import com.kreativekoala.riddleverse.ui.theme.RvInkSoft
import com.kreativekoala.riddleverse.ui.theme.RvOnTone
import com.kreativekoala.riddleverse.ui.theme.RvOutline
import com.kreativekoala.riddleverse.ui.theme.RvSuccess
import com.kreativekoala.riddleverse.ui.theme.RvSurfaceRaised

class TierRewardActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            RvActivityTheme {
                TierRewardScreen(
                    onBack = { finish() }
                )
            }
        }
    }
}

@Composable
fun TierRewardScreen(onBack: () -> Unit = {}) {
    val context = LocalContext.current

    // Use new clean architecture
    val userStatsManager = remember { UserStatsManager.getInstance(context) }
    val progressionEngine = remember { ProgressionEngine(context, userStatsManager) }
    val dataProvider = remember { DashboardDataProvider(context, userStatsManager, progressionEngine) }

    // Get real data
    val dashboardData by remember { mutableStateOf(dataProvider.getDashboardData()) }
    val currentLevel = dashboardData.levelInfo
    val tierInfo = dashboardData.tierInfo
    val globalStats = dashboardData.globalStats

    // Define tier structure with rewards
    val tierStructure = remember {
        listOf(
            TierRewardInfo(
                tier = "Bronze",
                tierNameRes = R.string.stats_tier_bronze,
                emoji = "🥉",
                xpRequired = 0,
                rewards = listOf(R.string.stats_reward_basic_puzzles, R.string.stats_reward_progress_tracking, R.string.stats_reward_bronze_badge),
                isUnlocked = tierInfo.totalXP >= 0,
                color = Color(0xFFD2691E)
            ),
            TierRewardInfo(
                tier = "Silver",
                tierNameRes = R.string.stats_tier_silver,
                emoji = "🥈",
                xpRequired = 500,
                rewards = listOf(R.string.stats_reward_silver_theme, R.string.stats_reward_score_boost, R.string.stats_reward_advanced_stats),
                isUnlocked = tierInfo.totalXP >= 500,
                color = Color(0xFFC0C0C0)
            ),
            TierRewardInfo(
                tier = "Gold",
                tierNameRes = R.string.stats_tier_gold,
                emoji = "🥇",
                xpRequired = 1500,
                rewards = listOf(R.string.stats_reward_gold_badge_crown, R.string.stats_reward_streak_bonus, R.string.stats_reward_elite_challenges),
                isUnlocked = tierInfo.totalXP >= 1500,
                color = Color(0xFFFFD700)
            ),
            TierRewardInfo(
                tier = "Platinum",
                tierNameRes = R.string.stats_tier_platinum,
                emoji = "💎",
                xpRequired = 3000,
                rewards = listOf(R.string.stats_reward_platinum_theme, R.string.stats_reward_leaderboard_access, R.string.stats_reward_exclusive_puzzles),
                isUnlocked = tierInfo.totalXP >= 3000,
                color = Color(0xFF98D8E8)
            ),
            TierRewardInfo(
                tier = "Diamond",
                tierNameRes = R.string.stats_tier_diamond,
                emoji = "💠",
                xpRequired = 6000,
                rewards = listOf(R.string.stats_reward_diamond_badge, R.string.stats_reward_custom_avatar, R.string.stats_reward_speed_boosts),
                isUnlocked = tierInfo.totalXP >= 6000,
                color = Color(0xFFB9F2FF)
            ),
            TierRewardInfo(
                tier = "Master",
                tierNameRes = R.string.xa_difficulty_master,
                emoji = "👑",
                xpRequired = 10000,
                rewards = listOf(R.string.stats_reward_master_crown, R.string.stats_reward_ultimate_rewards, R.string.stats_reward_vip_features),
                isUnlocked = tierInfo.totalXP >= 10000,
                color = RvGrape
            )
        )
    }

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
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.back)
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.stats_tier_rewards_title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    stringResource(R.string.stats_tier_rewards_subtitle),
                    fontSize = 14.sp,
                    color = Color.Gray
                )
            }

            // Current XP badge
            Box(
                modifier = Modifier
                    .background(
                        Color(0xFF7B1FA2),
                        RoundedCornerShape(12.dp)
                    )
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text(
                    stringResource(R.string.home_xp_amount, tierInfo.totalXP),
                    color = RvOnTone,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Current Tier Progress Card
        CurrentTierProgressCard(tierInfo, currentLevel)

        Spacer(modifier = Modifier.height(16.dp))

        // Tier List
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(tierStructure) { tierReward ->
                TierRewardCard(
                    tierReward = tierReward,
                    isCurrentTier = tierReward.tier == tierInfo.currentTier,
                    currentXP = tierInfo.totalXP
                )
            }
        }
    }
}

@Composable
fun CurrentTierProgressCard(tierInfo: TierInfo, currentLevel: UserLevel) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = RvSurfaceRaised
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
        shape = RoundedCornerShape(16.dp)
    ) {
        Box(
            modifier = Modifier.fillMaxWidth()
        ) {
            // Gradient background
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        brush = Brush.linearGradient(
                            listOf(
                                getTierColor(tierInfo.currentTier),
                                getTierColor(tierInfo.currentTier).copy(alpha = 0.8f)
                            )
                        ),
                        shape = RoundedCornerShape(16.dp)
                    )
            )

            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Current tier display
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            "${getTierEmoji(tierInfo.currentTier)} ${stringResource(R.string.stats_tier_with_label, stringResource(tierInfo.currentTierRes))}",
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                            color = RvInk
                        )
                        Text(
                            stringResource(R.string.stats_level_number, currentLevel.level),
                            fontSize = 16.sp,
                            color = RvInkSoft
                        )
                    }

                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            stringResource(R.string.home_xp_amount, tierInfo.totalXP),
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = RvInk
                        )
                        if (tierInfo.currentTier != tierInfo.nextTier) {
                            Text(
                                stringResource(R.string.stats_next_tier, stringResource(tierInfo.nextTierRes)),
                                fontSize = 12.sp,
                                color = RvInkSoft
                            )
                        }
                    }
                }

                if (tierInfo.currentTier != tierInfo.nextTier) {
                    Spacer(modifier = Modifier.height(16.dp))

                    // Progress to next tier
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                stringResource(R.string.stats_progress_to_tier, stringResource(tierInfo.nextTierRes)),
                                fontSize = 14.sp,
                                color = RvInkSoft
                            )
                            Text(
                                stringResource(R.string.stats_xp_to_go, tierInfo.pointsToNextTier - tierInfo.pointsInTier),
                                fontSize = 12.sp,
                                color = RvInkSoft
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        LinearProgressIndicator(
                            progress = { tierInfo.progressPercentage },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(RoundedCornerShape(4.dp)),
                            color = RvInk,
                            trackColor = RvOutline
                        )
                    }
                } else {
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        stringResource(R.string.stats_max_tier_short),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = RvInk,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

@Composable
fun TierRewardCard(
    tierReward: TierRewardInfo,
    isCurrentTier: Boolean,
    currentXP: Int
) {
    val cardColor = when {
        tierReward.isUnlocked -> tierReward.color.copy(alpha = 0.1f)
        isCurrentTier -> tierReward.color.copy(alpha = 0.05f)
        else -> Color.Gray.copy(alpha = 0.05f)
    }

    val borderColor = when {
        isCurrentTier -> tierReward.color
        tierReward.isUnlocked -> tierReward.color.copy(alpha = 0.5f)
        else -> Color.Gray.copy(alpha = 0.3f)
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = if (isCurrentTier) 2.dp else 1.dp,
                color = borderColor,
                shape = RoundedCornerShape(16.dp)
            ),
        colors = CardDefaults.cardColors(containerColor = cardColor),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isCurrentTier) 8.dp else 4.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Tier Icon
            Box(
                modifier = Modifier
                    .size(60.dp)
                    .clip(CircleShape)
                    .background(
                        if (tierReward.isUnlocked || isCurrentTier) {
                            tierReward.color.copy(alpha = 0.2f)
                        } else {
                            Color.Gray.copy(alpha = 0.2f)
                        }
                    )
                    .border(
                        2.dp,
                        if (tierReward.isUnlocked || isCurrentTier) {
                            tierReward.color
                        } else {
                            Color.Gray
                        },
                        CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (tierReward.isUnlocked) {
                    Text(
                        text = tierReward.emoji,
                        fontSize = 28.sp
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = stringResource(R.string.common_locked),
                        tint = Color.Gray,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            // Tier Info
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = stringResource(tierReward.tierNameRes),
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (tierReward.isUnlocked || isCurrentTier) {
                            tierReward.color
                        } else {
                            Color.Gray
                        }
                    )

                    if (isCurrentTier) {
                        Box(
                            modifier = Modifier
                                .background(
                                    tierReward.color,
                                    RoundedCornerShape(8.dp)
                                )
                                .padding(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Text(
                                stringResource(R.string.stats_badge_current),
                                color = RvInk,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    } else if (tierReward.isUnlocked) {
                        Box(
                            modifier = Modifier
                                .background(
                                    RvSuccess,
                                    RoundedCornerShape(8.dp)
                                )
                                .padding(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Text(
                                stringResource(R.string.stats_badge_unlocked),
                                color = RvOnTone,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                Text(
                    text = stringResource(R.string.stats_xp_required, tierReward.xpRequired),
                    fontSize = 12.sp,
                    color = Color.Gray,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                // Rewards list
                tierReward.rewards.forEach { reward ->
                    Text(
                        text = "• ${stringResource(reward)}",
                        fontSize = 12.sp,
                        color = if (tierReward.isUnlocked) {
                            RvSuccess
                        } else {
                            Color.Gray
                        },
                        modifier = Modifier.padding(vertical = 1.dp)
                    )
                }

                // Progress for locked tiers
                if (!tierReward.isUnlocked && currentXP > 0 && tierReward.xpRequired > currentXP) {
                    Spacer(modifier = Modifier.height(8.dp))

                    val progress = (currentXP.toFloat() / tierReward.xpRequired.toFloat()).coerceAtMost(1f)

                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp)),
                        color = tierReward.color,
                        trackColor = Color.Gray.copy(alpha = 0.3f)
                    )

                    Text(
                        stringResource(R.string.stats_xp_to_unlock, tierReward.xpRequired - currentXP),
                        fontSize = 10.sp,
                        color = Color.Gray,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        }
    }
}

// Data class for tier reward information
data class TierRewardInfo(
    val tier: String,
    @StringRes val tierNameRes: Int,
    val emoji: String,
    val xpRequired: Int,
    /** String resource ids of the reward labels. */
    val rewards: List<Int>,
    val isUnlocked: Boolean,
    val color: Color
)



