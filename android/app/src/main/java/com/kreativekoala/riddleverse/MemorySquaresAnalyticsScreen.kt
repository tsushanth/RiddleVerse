// MemorySquaresAnalyticsScreen.kt - Shows adaptive difficulty analytics
package com.kreativekoala.riddleverse

import com.kreativekoala.riddleverse.ui.theme.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.kreativekoala.riddleverse.ui.rvDifficultyLabel
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun MemorySquaresAnalyticsScreen(
    difficultyManager: DifficultyManager,
    onBack: () -> Unit
) {
    val analytics = difficultyManager.getPerformanceAnalytics(
        puzzleType = "memorysquares"
    )
    val currentDifficulty = difficultyManager.getCurrentDifficulty(
        puzzleType = "memorysquares"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF8D6E63))
            .statusBarsPadding()
            .padding(16.dp)
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back), tint = RvInk)
            }

            Text(
                text = stringResource(R.string.performance_analytics),
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = RvInk
            )

            Icon(
                Icons.Default.Analytics,
                contentDescription = stringResource(R.string.stats_analytics),
                tint = RvInk
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Current Status Card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = RvSurface
                    ),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = stringResource(R.string.stats_current_level),
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Icon(
                                Icons.Default.TrendingUp,
                                contentDescription = stringResource(R.string.level_label),
                                tint = Color(0xFF4CAF50)
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Text(
                            text = rvDifficultyLabel(currentDifficulty.name),
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF2196F3)
                        )

                        Text(
                            text = stringResource(R.string.stats_grid_targets, currentDifficulty.gridSize, currentDifficulty.targetCount),
                            fontSize = 14.sp,
                            color = Color.Gray
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            MetricChip(
                                label = stringResource(R.string.stats_memorize_time),
                                value = stringResource(R.string.stats_seconds_short, currentDifficulty.memorizeTime),
                                icon = Icons.Default.Timer,
                                color = Color(0xFFFF9800)
                            )

                            MetricChip(
                                label = stringResource(R.string.stats_lives),
                                value = "${currentDifficulty.livesAllowed}",
                                icon = Icons.Default.Favorite,
                                color = Color(0xFFF44336)
                            )

                            MetricChip(
                                label = stringResource(R.string.stats_points),
                                value = "${currentDifficulty.basePoints}",
                                icon = Icons.Default.Star,
                                color = Color(0xFF9C27B0)
                            )
                        }
                    }
                }
            }

            // Performance Metrics Card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = RvSurface
                    ),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = stringResource(R.string.performance_stats),
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Icon(
                                Icons.Default.Assessment,
                                contentDescription = stringResource(R.string.stats_stats),
                                tint = Color(0xFF673AB7)
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        if (analytics.isNotEmpty()) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceEvenly
                            ) {
                                StatCard(
                                    title = stringResource(R.string.accuracy),
                                    value = "${analytics["averageAccuracy"]}%",
                                    subtitle = stringResource(R.string.cat_average_title),
                                    color = getAccuracyColor(analytics["averageAccuracy"] as? Int ?: 0)
                                )

                                StatCard(
                                    title = stringResource(R.string.avg_time),
                                    value = stringResource(R.string.stats_seconds_short, analytics["averageTime"] as? Int ?: 0),
                                    subtitle = stringResource(R.string.stats_per_game),
                                    color = Color(0xFF00BCD4)
                                )

                                StatCard(
                                    title = stringResource(R.string.streak_label),
                                    value = "${analytics["currentStreak"]}",
                                    subtitle = stringResource(R.string.common_current),
                                    color = if ((analytics["currentStreak"] as? Int ?: 0) > 0)
                                        Color(0xFFFF6F00) else Color.Gray
                                )
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceEvenly
                            ) {
                                StatCard(
                                    title = stringResource(R.string.tab_games),
                                    value = "${analytics["gamesPlayed"]}",
                                    subtitle = stringResource(R.string.stats_played),
                                    color = RvInkSoft
                                )

                                StatCard(
                                    title = stringResource(R.string.stats_trend),
                                    value = when (analytics["trend"]) {
                                        "Improving" -> stringResource(R.string.stats_status_improving)
                                        "Declining" -> stringResource(R.string.stats_trend_declining)
                                        else -> stringResource(R.string.stats_trend_stable)
                                    },
                                    subtitle = stringResource(R.string.stats_performance),
                                    color = when (analytics["trend"]) {
                                        "Improving" -> Color(0xFF4CAF50)
                                        "Declining" -> Color(0xFFF44336)
                                        else -> Color(0xFF757575)
                                    }
                                )

                                StatCard(
                                    title = stringResource(R.string.stats_adaptations),
                                    value = "${analytics["adaptationCount"]}",
                                    subtitle = stringResource(R.string.stats_this_session),
                                    color = Color(0xFF3F51B5)
                                )
                            }
                        } else {
                            Text(
                                text = stringResource(R.string.stats_play_few_games),
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth().padding(20.dp),
                                color = Color.Gray
                            )
                        }
                    }
                }
            }

            // Difficulty Progression Card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = RvSurface
                    ),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = stringResource(R.string.stats_difficulty_journey),
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Icon(
                                Icons.Default.Timeline,
                                contentDescription = stringResource(R.string.stats_journey),
                                tint = Color(0xFF795548)
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        DifficultyProgressionTimeline(currentDifficulty)
                    }
                }
            }

            // Tips and Insights Card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = RvSurface
                    ),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = stringResource(R.string.stats_adaptive_insights),
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Icon(
                                Icons.Default.Lightbulb,
                                contentDescription = stringResource(R.string.stats_insights),
                                tint = Color(0xFFFFC107)
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        InsightCards(analytics, currentDifficulty)
                    }
                }
            }
        }
    }
}

@Composable
fun MetricChip(
    label: String,
    value: String,
    icon: ImageVector,
    color: Color
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .background(color.copy(alpha = 0.1f), CircleShape)
                .border(2.dp, color.copy(alpha = 0.3f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                icon,
                contentDescription = label,
                tint = color,
                modifier = Modifier.size(24.dp)
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = value,
            fontSize = 12.sp,
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

@Composable
fun StatCard(
    title: String,
    value: String,
    subtitle: String,
    color: Color
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .background(color.copy(alpha = 0.1f), RoundedCornerShape(12.dp))
            .padding(12.dp)
    ) {
        Text(
            text = value,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = color
        )

        Text(
            text = title,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            color = RvInk
        )

        Text(
            text = subtitle,
            fontSize = 10.sp,
            color = Color.Gray
        )
    }
}

@Composable
fun DifficultyProgressionTimeline(currentDifficulty: DifficultyManager.DifficultyLevel) {
    val levels = listOf(
        "Tutorial", "Beginner", "Easy", "Easy+", "Medium-", "Medium",
        "Medium+", "Hard-", "Hard", "Hard+", "Expert-", "Expert",
        "Expert+", "Master", "Grandmaster"
    )

    val currentIndex = levels.indexOf(currentDifficulty.name)

    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        items(levels) { level ->
            val index = levels.indexOf(level)
            val isCurrentLevel = level == currentDifficulty.name
            val isPastLevel = index < currentIndex

            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .background(
                            when {
                                isCurrentLevel -> Color(0xFF2196F3)
                                isPastLevel -> Color(0xFF4CAF50)
                                else -> Color.Gray.copy(alpha = 0.3f)
                            },
                            CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    when {
                        isCurrentLevel -> Icon(
                            Icons.Default.RadioButtonChecked,
                            contentDescription = stringResource(R.string.common_current),
                            tint = RvInk,
                            modifier = Modifier.size(16.dp)
                        )
                        isPastLevel -> Icon(
                            Icons.Default.CheckCircle,
                            contentDescription = stringResource(R.string.completed),
                            tint = RvInk,
                            modifier = Modifier.size(16.dp)
                        )
                        else -> Icon(
                            Icons.Default.Circle,
                            contentDescription = stringResource(R.string.stats_future),
                            tint = RvInkSoft.copy(alpha = 0.5f),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = rvDifficultyLabel(level),
                    fontSize = 8.sp,
                    fontWeight = if (isCurrentLevel) FontWeight.Bold else FontWeight.Normal,
                    color = if (isCurrentLevel) Color(0xFF2196F3) else Color.Gray,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.width(40.dp)
                )
            }
        }
    }
}

@Composable
fun InsightCards(analytics: Map<String, Any>, currentDifficulty: DifficultyManager.DifficultyLevel) {
    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Adaptive AI Explanation
        InsightCard(
            icon = Icons.Default.Psychology,
            title = stringResource(R.string.stats_insight_how_title),
            description = stringResource(R.string.stats_insight_how_desc),
            color = Color(0xFF9C27B0)
        )

        // Performance-based insights
        if (analytics.isNotEmpty()) {
            val accuracy = analytics["averageAccuracy"] as? Int ?: 0
            val trend = analytics["trend"] as? String ?: "Stable"
            val streak = analytics["currentStreak"] as? Int ?: 0

            when {
                accuracy >= 90 -> InsightCard(
                    icon = Icons.Default.EmojiEvents,
                    title = stringResource(R.string.stats_insight_excellent_title),
                    description = stringResource(R.string.stats_insight_excellent_desc, accuracy),
                    color = Color(0xFF4CAF50)
                )

                accuracy >= 70 -> InsightCard(
                    icon = Icons.Default.TrendingUp,
                    title = stringResource(R.string.stats_insight_good_title),
                    description = stringResource(R.string.stats_insight_good_desc, accuracy),
                    color = Color(0xFF2196F3)
                )

                accuracy < 50 -> InsightCard(
                    icon = Icons.Default.School,
                    title = stringResource(R.string.stats_insight_learning_title),
                    description = stringResource(R.string.stats_insight_learning_desc),
                    color = Color(0xFFFF9800)
                )
            }

            if (streak >= 3) {
                InsightCard(
                    icon = Icons.Default.LocalFireDepartment,
                    title = stringResource(R.string.stats_insight_fire_title),
                    description = LocalContext.current.resources.getQuantityString(R.plurals.stats_insight_fire_desc, streak, streak),
                    color = Color(0xFFFF6F00)
                )
            }

            when (trend) {
                "Improving" -> InsightCard(
                    icon = Icons.Default.TrendingUp,
                    title = stringResource(R.string.stats_insight_improving_title),
                    description = stringResource(R.string.stats_insight_improving_desc),
                    color = Color(0xFF4CAF50)
                )

                "Declining" -> InsightCard(
                    icon = Icons.Default.SupportAgent,
                    title = stringResource(R.string.stats_insight_adjusted_title),
                    description = stringResource(R.string.stats_insight_adjusted_desc),
                    color = Color(0xFF2196F3)
                )
            }
        }

        // Memory tips based on current difficulty
        when {
            currentDifficulty.gridSize <= 4 -> InsightCard(
                icon = Icons.Default.TipsAndUpdates,
                title = stringResource(R.string.stats_insight_tip_beginner_title),
                description = stringResource(R.string.stats_insight_tip_beginner_desc),
                color = RvInkSoft
            )

            currentDifficulty.gridSize == 5 -> InsightCard(
                icon = Icons.Default.Extension,
                title = stringResource(R.string.stats_insight_tip_chunking_title),
                description = stringResource(R.string.stats_insight_tip_chunking_desc),
                color = RvInkSoft
            )

            currentDifficulty.gridSize >= 6 -> InsightCard(
                icon = Icons.Default.Memory,
                title = stringResource(R.string.stats_insight_tip_advanced_title),
                description = stringResource(R.string.stats_insight_tip_advanced_desc),
                color = RvInkSoft
            )
        }
    }
}

@Composable
fun InsightCard(
    icon: ImageVector,
    title: String,
    description: String,
    color: Color
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = color.copy(alpha = 0.1f)
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.Top
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(color.copy(alpha = 0.2f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    icon,
                    contentDescription = title,
                    tint = color,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = title,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = color
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = description,
                    fontSize = 12.sp,
                    color = Color.Black.copy(alpha = 0.8f),
                    lineHeight = 16.sp
                )
            }
        }
    }
}

fun getAccuracyColor(accuracy: Int): Color {
    return when {
        accuracy >= 90 -> Color(0xFF4CAF50)  // Green
        accuracy >= 70 -> Color(0xFF2196F3)  // Blue
        accuracy >= 50 -> Color(0xFFFF9800)  // Orange
        else -> Color(0xFFF44336)             // Red
    }
}