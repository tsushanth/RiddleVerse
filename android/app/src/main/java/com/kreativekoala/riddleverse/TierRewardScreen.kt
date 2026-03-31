package com.kreativekoala.riddleverse

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import com.kreativekoala.riddleverse.ui.theme.RiddleVerseTheme

class TierRewardActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            RiddleVerseTheme {
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
                emoji = "🥉",
                xpRequired = 0,
                rewards = listOf("🎮 Basic Puzzles", "📊 Progress Tracking", "🏅 Bronze Badge"),
                isUnlocked = tierInfo.totalXP >= 0,
                color = Color(0xFFD2691E)
            ),
            TierRewardInfo(
                tier = "Silver",
                emoji = "🥈",
                xpRequired = 500,
                rewards = listOf("🎨 Silver Theme", "⭐ Score Boost", "📈 Advanced Stats"),
                isUnlocked = tierInfo.totalXP >= 500,
                color = Color(0xFFC0C0C0)
            ),
            TierRewardInfo(
                tier = "Gold",
                emoji = "🥇",
                xpRequired = 1500,
                rewards = listOf("👑 Gold Badge", "🔥 Streak Bonus", "🎯 Elite Challenges"),
                isUnlocked = tierInfo.totalXP >= 1500,
                color = Color(0xFFFFD700)
            ),
            TierRewardInfo(
                tier = "Platinum",
                emoji = "💎",
                xpRequired = 3000,
                rewards = listOf("💎 Platinum Theme", "🏆 Leaderboard Access", "🌟 Exclusive Puzzles"),
                isUnlocked = tierInfo.totalXP >= 3000,
                color = Color(0xFF98D8E8)
            ),
            TierRewardInfo(
                tier = "Diamond",
                emoji = "💠",
                xpRequired = 6000,
                rewards = listOf("💠 Diamond Badge", "🎭 Custom Avatar", "⚡ Speed Boosts"),
                isUnlocked = tierInfo.totalXP >= 6000,
                color = Color(0xFFB9F2FF)
            ),
            TierRewardInfo(
                tier = "Master",
                emoji = "👑",
                xpRequired = 10000,
                rewards = listOf("👑 Master Crown", "🌟 Ultimate Rewards", "🎪 VIP Features"),
                isUnlocked = tierInfo.totalXP >= 10000,
                color = Color(0xFF9C27B0)
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
                    imageVector = Icons.Default.ArrowBack,
                    contentDescription = stringResource(R.string.back)
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "Tier Rewards",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "Unlock rewards as you progress",
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
                    "${tierInfo.totalXP} XP",
                    color = Color.White,
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
            containerColor = Color.White
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
                            "${getTierEmoji(tierInfo.currentTier)} ${tierInfo.currentTier} Tier",
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            "Level ${currentLevel.level}",
                            fontSize = 16.sp,
                            color = Color.White.copy(alpha = 0.9f)
                        )
                    }

                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            "${tierInfo.totalXP} XP",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        if (tierInfo.currentTier != tierInfo.nextTier) {
                            Text(
                                "Next: ${tierInfo.nextTier}",
                                fontSize = 12.sp,
                                color = Color.White.copy(alpha = 0.8f)
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
                                "Progress to ${tierInfo.nextTier}",
                                fontSize = 14.sp,
                                color = Color.White.copy(alpha = 0.9f)
                            )
                            Text(
                                "${tierInfo.pointsToNextTier - tierInfo.pointsInTier} XP to go",
                                fontSize = 12.sp,
                                color = Color.White.copy(alpha = 0.8f)
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        LinearProgressIndicator(
                            progress = { tierInfo.progressPercentage },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(RoundedCornerShape(4.dp)),
                            color = Color.White,
                            trackColor = Color.White.copy(alpha = 0.3f)
                        )
                    }
                } else {
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        "🎉 Maximum tier achieved! 🎉",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
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
                        contentDescription = "Locked",
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
                        text = tierReward.tier,
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
                                "CURRENT",
                                color = Color.White,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    } else if (tierReward.isUnlocked) {
                        Box(
                            modifier = Modifier
                                .background(
                                    Color(0xFF4CAF50),
                                    RoundedCornerShape(8.dp)
                                )
                                .padding(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Text(
                                "UNLOCKED",
                                color = Color.White,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                Text(
                    text = "${tierReward.xpRequired} XP required",
                    fontSize = 12.sp,
                    color = Color.Gray,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                // Rewards list
                tierReward.rewards.forEach { reward ->
                    Text(
                        text = "• $reward",
                        fontSize = 12.sp,
                        color = if (tierReward.isUnlocked) {
                            Color(0xFF2E7D32)
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
                        "${tierReward.xpRequired - currentXP} XP to unlock",
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
    val emoji: String,
    val xpRequired: Int,
    val rewards: List<String>,
    val isUnlocked: Boolean,
    val color: Color
)



