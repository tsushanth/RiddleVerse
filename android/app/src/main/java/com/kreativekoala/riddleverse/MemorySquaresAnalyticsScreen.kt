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
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.res.stringResource
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
                Icon(Icons.Default.ArrowBack, contentDescription = stringResource(R.string.back), tint = RvInk)
            }

            Text(
                text = stringResource(R.string.performance_analytics),
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = RvInk
            )

            Icon(
                Icons.Default.Analytics,
                contentDescription = "Analytics",
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
                                text = "Current Level",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Icon(
                                Icons.Default.TrendingUp,
                                contentDescription = "Level",
                                tint = Color(0xFF4CAF50)
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Text(
                            text = currentDifficulty.name,
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF2196F3)
                        )

                        Text(
                            text = "${currentDifficulty.gridSize}×${currentDifficulty.gridSize} grid • ${currentDifficulty.targetCount} targets",
                            fontSize = 14.sp,
                            color = Color.Gray
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            MetricChip(
                                label = "Memorize Time",
                                value = "${currentDifficulty.memorizeTime}s",
                                icon = Icons.Default.Timer,
                                color = Color(0xFFFF9800)
                            )

                            MetricChip(
                                label = "Lives",
                                value = "${currentDifficulty.livesAllowed}",
                                icon = Icons.Default.Favorite,
                                color = Color(0xFFF44336)
                            )

                            MetricChip(
                                label = "Points",
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
                                contentDescription = "Stats",
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
                                    subtitle = "Average",
                                    color = getAccuracyColor(analytics["averageAccuracy"] as? Int ?: 0)
                                )

                                StatCard(
                                    title = stringResource(R.string.avg_time),
                                    value = "${analytics["averageTime"]}s",
                                    subtitle = "Per game",
                                    color = Color(0xFF00BCD4)
                                )

                                StatCard(
                                    title = stringResource(R.string.streak_label),
                                    value = "${analytics["currentStreak"]}",
                                    subtitle = "Current",
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
                                    title = "Games",
                                    value = "${analytics["gamesPlayed"]}",
                                    subtitle = "Played",
                                    color = RvInkSoft
                                )

                                StatCard(
                                    title = "Trend",
                                    value = "${analytics["trend"]}",
                                    subtitle = "Performance",
                                    color = when (analytics["trend"]) {
                                        "Improving" -> Color(0xFF4CAF50)
                                        "Declining" -> Color(0xFFF44336)
                                        else -> Color(0xFF757575)
                                    }
                                )

                                StatCard(
                                    title = "Adaptations",
                                    value = "${analytics["adaptationCount"]}",
                                    subtitle = "This session",
                                    color = Color(0xFF3F51B5)
                                )
                            }
                        } else {
                            Text(
                                text = "Play a few games to see your performance analytics!",
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
                                text = "Difficulty Journey",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Icon(
                                Icons.Default.Timeline,
                                contentDescription = "Journey",
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
                                text = "Adaptive Insights",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Icon(
                                Icons.Default.Lightbulb,
                                contentDescription = "Insights",
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
                            contentDescription = "Current",
                            tint = RvInk,
                            modifier = Modifier.size(16.dp)
                        )
                        isPastLevel -> Icon(
                            Icons.Default.CheckCircle,
                            contentDescription = "Completed",
                            tint = RvInk,
                            modifier = Modifier.size(16.dp)
                        )
                        else -> Icon(
                            Icons.Default.Circle,
                            contentDescription = "Future",
                            tint = RvInkSoft.copy(alpha = 0.5f),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = level,
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
            title = "How Adaptive Difficulty Works",
            description = "The AI monitors your accuracy, speed, and streak to automatically adjust challenge level. Perfect games increase difficulty, while struggles reduce it for optimal learning.",
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
                    title = "Excellent Memory Skills!",
                    description = "Your ${accuracy}% accuracy shows strong spatial memory. The AI may increase difficulty to keep you challenged.",
                    color = Color(0xFF4CAF50)
                )

                accuracy >= 70 -> InsightCard(
                    icon = Icons.Default.TrendingUp,
                    title = "Good Progress",
                    description = "Your ${accuracy}% accuracy is solid. Keep practicing pattern recognition and chunking strategies.",
                    color = Color(0xFF2196F3)
                )

                accuracy < 50 -> InsightCard(
                    icon = Icons.Default.School,
                    title = "Learning Mode",
                    description = "The AI detected struggles and reduced difficulty. Focus on creating mental stories to link square positions.",
                    color = Color(0xFFFF9800)
                )
            }

            if (streak >= 3) {
                InsightCard(
                    icon = Icons.Default.LocalFireDepartment,
                    title = "On Fire! 🔥",
                    description = "Your ${streak}-game streak shows mastery! Expect the AI to challenge you with larger grids soon.",
                    color = Color(0xFFFF6F00)
                )
            }

            when (trend) {
                "Improving" -> InsightCard(
                    icon = Icons.Default.TrendingUp,
                    title = "Improving Performance",
                    description = "Your skills are developing! The adaptive system will gradually increase complexity as you improve.",
                    color = Color(0xFF4CAF50)
                )

                "Declining" -> InsightCard(
                    icon = Icons.Default.SupportAgent,
                    title = "Difficulty Adjusted",
                    description = "The AI noticed recent struggles and reduced difficulty to help rebuild confidence and skills.",
                    color = Color(0xFF2196F3)
                )
            }
        }

        // Memory tips based on current difficulty
        when {
            currentDifficulty.gridSize <= 4 -> InsightCard(
                icon = Icons.Default.TipsAndUpdates,
                title = "Beginner Memory Tip",
                description = "Try the 'story method': create a mental story connecting the highlighted squares. For example, imagine a path or shape.",
                color = RvInkSoft
            )

            currentDifficulty.gridSize == 5 -> InsightCard(
                icon = Icons.Default.Extension,
                title = "Chunking Strategy",
                description = "Group nearby squares into patterns or shapes. Your brain remembers 3-4 groups better than individual positions.",
                color = RvInkSoft
            )

            currentDifficulty.gridSize >= 6 -> InsightCard(
                icon = Icons.Default.Memory,
                title = "Advanced Technique",
                description = "Use the 'palace method': imagine familiar locations and place each square there. This leverages spatial memory.",
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