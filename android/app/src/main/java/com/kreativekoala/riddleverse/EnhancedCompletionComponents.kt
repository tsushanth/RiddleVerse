// EnhancedCompletionComponents.kt - Enhanced completion screen with ranking improvements and animations
package com.kreativekoala.riddleverse

import com.kreativekoala.riddleverse.ui.theme.RvGrape
import com.kreativekoala.riddleverse.ui.theme.RvInfo
import com.kreativekoala.riddleverse.ui.theme.RvInk
import com.kreativekoala.riddleverse.ui.theme.RvInkSoft
import com.kreativekoala.riddleverse.ui.theme.RvOnTone
import com.kreativekoala.riddleverse.ui.theme.RvOutline
import com.kreativekoala.riddleverse.ui.theme.RvSky
import com.kreativekoala.riddleverse.ui.theme.RvSkyEdge
import com.kreativekoala.riddleverse.ui.theme.RvSuccess
import com.kreativekoala.riddleverse.ui.theme.RvSuccessEdge
import com.kreativekoala.riddleverse.ui.theme.RvSurface
import com.kreativekoala.riddleverse.ui.theme.RvViolet
import android.util.Log
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.delay
import kotlin.math.cos
import androidx.compose.ui.res.stringResource
import kotlin.math.sin

@Composable
fun RankingImprovementCard(
    rankingImprovement: CompetitiveRankingManager.RankingImprovement?,
    modifier: Modifier = Modifier
) {
    if (rankingImprovement == null) return

    AnimatedVisibility(
        visible = true,
        enter = slideInVertically(
            initialOffsetY = { it },
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessLow
            )
        ) + fadeIn(animationSpec = tween(500))
    ) {
        Card(
            modifier = modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = if (rankingImprovement.improved) {
                    RvSuccess.copy(alpha = 0.15f)
                } else {
                    RvSky.copy(alpha = 0.15f)
                }
            ),
            shape = RoundedCornerShape(16.dp),
            border = CardDefaults.outlinedCardBorder().copy(
                width = 2.dp,
                brush = Brush.horizontalGradient(
                    colors = if (rankingImprovement.improved) {
                        listOf(RvSuccess, RvSuccessEdge)
                    } else {
                        listOf(RvSky, RvSkyEdge)
                    }
                )
            )
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header with animation
                if (rankingImprovement.showAnimation) {
                    RankingImprovementAnimation(
                        oldPercentile = rankingImprovement.oldPercentile,
                        newPercentile = rankingImprovement.newPercentile,
                        improved = rankingImprovement.improved
                    )
                } else {
                    // Simple static display
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            imageVector = if (rankingImprovement.improved) Icons.Default.TrendingUp else Icons.Default.Assessment,
                            contentDescription = null,
                            tint = if (rankingImprovement.improved) RvSuccess else RvSky,
                            modifier = Modifier.size(32.dp)
                        )

                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "Global Ranking",
                                fontSize = 14.sp,
                                color = RvInkSoft
                            )
                            Text(
                                text = "${rankingImprovement.newPercentile}th percentile",
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Bold,
                                color = RvInk
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Improvement message
                Text(
                    text = rankingImprovement.rankingMessage,
                    fontSize = 14.sp,
                    color = RvInk,
                    textAlign = TextAlign.Center,
                    lineHeight = 20.sp
                )

                if (rankingImprovement.improved && rankingImprovement.percentileGain > 0) {
                    Spacer(modifier = Modifier.height(8.dp))

                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = RvSuccess.copy(alpha = 0.2f)
                        ),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = "📈 +${rankingImprovement.percentileGain} percentile points!",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = RvSuccess,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun RankingImprovementAnimation(
    oldPercentile: Int,
    newPercentile: Int,
    improved: Boolean
) {
    var animationPlayed by remember { mutableStateOf(false) }
    val animatedPercentile by animateIntAsState(
        targetValue = if (animationPlayed) newPercentile else oldPercentile,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "percentile_animation"
    )

    val scaleAnimation by animateFloatAsState(
        targetValue = if (animationPlayed) 1f else 0.8f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "scale_animation"
    )

    LaunchedEffect(Unit) {
        delay(500) // Wait a bit before starting animation
        animationPlayed = true
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.scale(scaleAnimation)
    ) {
        // Animated icon with particles
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(80.dp)
        ) {
            if (improved && animationPlayed) {
                ParticleExplosionEffect()
            }

            Icon(
                imageVector = if (improved) Icons.Default.TrendingUp else Icons.Default.Assessment,
                contentDescription = null,
                tint = if (improved) RvSuccess else RvSky,
                modifier = Modifier.size(48.dp)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Animated percentile display
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (oldPercentile != newPercentile) {
                Text(
                    text = "${oldPercentile}%",
                    fontSize = 18.sp,
                    color = RvInkSoft,
                    fontWeight = FontWeight.Medium
                )

                Icon(
                    imageVector = Icons.Default.ArrowForward,
                    contentDescription = "to",
                    tint = RvInkSoft,
                    modifier = Modifier.size(20.dp)
                )
            }

            Text(
                text = "${animatedPercentile}%",
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                color = if (improved) RvSuccess else RvSky
            )
        }

        Text(
            text = "Global Percentile",
            fontSize = 14.sp,
            color = RvInkSoft
        )
    }
}

@Composable
fun ParticleExplosionEffect() {
    var triggerAnimation by remember { mutableStateOf(false) }
    val particles = remember { List(12) { ParticleState() } }

    LaunchedEffect(Unit) {
        triggerAnimation = true
    }

    Canvas(modifier = Modifier.fillMaxSize()) {
        if (triggerAnimation) {
            particles.forEachIndexed { index, particle ->
                val angle = (index * 30f) * (3.14159f / 180f) // Convert to radians
                val distance = 40.dp.toPx()
                val x = center.x + cos(angle) * distance
                val y = center.y + sin(angle) * distance

                drawCircle(
                    color = RvSuccess.copy(alpha = 0.7f),
                    radius = 3.dp.toPx(),
                    center = Offset(x, y)
                )
            }
        }
    }
}

data class ParticleState(
    var x: Float = 0f,
    var y: Float = 0f,
    var alpha: Float = 1f
)

@Composable
fun AdaptiveDifficultyProgressCard(
    oldDifficulty: DifficultyManager.DifficultyLevel?,
    newDifficulty: DifficultyManager.DifficultyLevel,
    adaptationInfo: DifficultyManager.AdaptiveConfig?,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = RvInfo.copy(alpha = 0.15f)
        ),
        shape = RoundedCornerShape(16.dp),
        border = CardDefaults.outlinedCardBorder().copy(
            width = 1.dp,
            brush = Brush.horizontalGradient(
                colors = listOf(RvSky, RvGrape)
            )
        )
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
                    text = "🧠 AI Adaptation Results",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = RvInk
                )

                Icon(
                    Icons.Default.Psychology,
                    contentDescription = "AI Analysis",
                    tint = RvSky,
                    modifier = Modifier.size(24.dp)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Difficulty level comparison
            if (oldDifficulty != null && oldDifficulty.name != newDifficulty.name) {
                DifficultyLevelComparison(
                    oldDifficulty = oldDifficulty,
                    newDifficulty = newDifficulty
                )
            } else {
                CurrentDifficultyDisplay(currentDifficulty = newDifficulty)
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Adaptation reasoning
            adaptationInfo?.let { info ->
                AdaptationReasoningSection(info)
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Next challenge info
            NextChallengePreview(newDifficulty)
        }
    }
}

@Composable
fun DifficultyLevelComparison(
    oldDifficulty: DifficultyManager.DifficultyLevel,
    newDifficulty: DifficultyManager.DifficultyLevel
) {
    val isIncrease = newDifficulty.basePoints > oldDifficulty.basePoints

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                if (isIncrease) RvSuccess.copy(alpha = 0.1f) else RvSky.copy(alpha = 0.1f),
                RoundedCornerShape(12.dp)
            )
            .padding(16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Old difficulty
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "Previous",
                fontSize = 12.sp,
                color = RvInkSoft
            )
            Text(
                text = oldDifficulty.name,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                color = RvInkSoft
            )
            Text(
                text = "${oldDifficulty.gridSize}×${oldDifficulty.gridSize}",
                fontSize = 12.sp,
                color = RvInkSoft
            )
        }

        // Arrow and change indicator
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = if (isIncrease) Icons.Default.TrendingUp else Icons.Default.TrendingFlat,
                contentDescription = null,
                tint = if (isIncrease) RvSuccess else RvSky,
                modifier = Modifier.size(32.dp)
            )
            Text(
                text = if (isIncrease) stringResource(R.string.level_up) else "Adjusted",
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = if (isIncrease) RvSuccess else RvSky
            )
        }

        // New difficulty
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "Current",
                fontSize = 12.sp,
                color = RvInkSoft
            )
            Text(
                text = newDifficulty.name,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = if (isIncrease) RvSuccess else RvSky
            )
            Text(
                text = "${newDifficulty.gridSize}×${newDifficulty.gridSize} • ${newDifficulty.targetCount} targets",
                fontSize = 12.sp,
                color = RvInkSoft
            )
        }
    }
}

@Composable
fun CurrentDifficultyDisplay(currentDifficulty: DifficultyManager.DifficultyLevel) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                RvSurface,
                RoundedCornerShape(12.dp)
            )
            .padding(16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(
                text = "Current Level",
                fontSize = 12.sp,
                color = RvInkSoft
            )
            Text(
                text = currentDifficulty.name,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = RvSky
            )
        }

        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = "Challenge Details",
                fontSize = 12.sp,
                color = RvInkSoft
            )
            Text(
                text = "${currentDifficulty.gridSize}×${currentDifficulty.gridSize} grid",
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = RvInk
            )
            Text(
                text = "${currentDifficulty.targetCount} targets • ${currentDifficulty.livesAllowed} lives",
                fontSize = 12.sp,
                color = RvInkSoft
            )
        }
    }
}

@Composable
fun AdaptationReasoningSection(adaptationInfo: DifficultyManager.AdaptiveConfig) {
    Column {
        Text(
            text = "🎯 Why This Change?",
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = RvInk,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        Text(
            text = adaptationInfo.adjustmentReason,
            fontSize = 12.sp,
            color = RvInkSoft,
            lineHeight = 16.sp
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Confidence indicator
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = "AI Confidence:",
                fontSize = 11.sp,
                color = RvInkSoft
            )

            Spacer(modifier = Modifier.width(8.dp))

            LinearProgressIndicator(
                progress = adaptationInfo.confidenceScore,
                modifier = Modifier
                    .weight(1f)
                    .height(4.dp),
                color = RvSuccess,
                trackColor = RvOutline
            )

            Spacer(modifier = Modifier.width(8.dp))

            Text(
                text = "${(adaptationInfo.confidenceScore * 100).toInt()}%",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = RvSuccess
            )
        }
    }
}

@Composable
fun NextChallengePreview(difficulty: DifficultyManager.DifficultyLevel) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                RvGrape.copy(alpha = 0.1f),
                RoundedCornerShape(8.dp)
            )
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "🎮",
            fontSize = 16.sp,
            modifier = Modifier.padding(end = 8.dp)
        )

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Next Challenge Preview",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = RvGrape
            )
            Text(
                text = "You'll face ${difficulty.gridSize}×${difficulty.gridSize} grids with ${difficulty.targetCount} targets to memorize in ${difficulty.memorizeTime} seconds",
                fontSize = 11.sp,
                color = RvInkSoft,
                lineHeight = 14.sp
            )
        }
    }
}

/**
 * Main enhanced completion content that integrates all components
 */
/**
 * Enhanced Completion Content with Ranking improvements and adaptive difficulty feedback
 */
@Composable
fun EnhancedCompletionContentWithRanking(
    puzzleType: String,
    sessionScore: Int,
    oldDifficulty: DifficultyManager.DifficultyLevel? = null,
    newDifficulty: DifficultyManager.DifficultyLevel,
    adaptationInfo: DifficultyManager.AdaptiveConfig? = null
) {
    var rankingImprovement by remember { mutableStateOf<CompetitiveRankingManager.RankingImprovement?>(null) }
    var isLoading by remember { mutableStateOf(true) }

    val context = LocalContext.current
    val currentUser = FirebaseAuth.getInstance().currentUser

    // Load ranking data
    LaunchedEffect(sessionScore) {
        try {
            if (currentUser != null && sessionScore > 0) {
                val rankingManager = CompetitiveRankingManager.getInstance()
                rankingImprovement = rankingManager.submitScoreAndGetImprovement(
                    userId = currentUser.uid,
                    puzzleType = puzzleType,
                    difficulty = newDifficulty.name,
                    sessionScore = sessionScore
                )
            }
        } catch (e: Exception) {
            Log.e("EnhancedCompletion", "Failed to load ranking data", e)
        }
        isLoading = false
    }

    if (isLoading) {
        // Loading state
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = RvSurface),
            shape = RoundedCornerShape(16.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = RvInk, modifier = Modifier.size(32.dp))
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Analyzing performance...",
                        color = RvInkSoft,
                        fontSize = 12.sp
                    )
                }
            }
        }
    } else {
        // Ranking Improvement Card
        AnimatedVisibility(
            visible = rankingImprovement != null,
            enter = slideInVertically() + fadeIn(),
            exit = slideOutVertically() + fadeOut()
        ) {
            rankingImprovement?.let { improvement ->
                RankingImprovementCard(
                    improvement = improvement,
                    puzzleType = puzzleType
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Difficulty Adaptation Card
        AnimatedVisibility(
            visible = adaptationInfo != null || (oldDifficulty != null && newDifficulty != oldDifficulty),
            enter = slideInVertically() + fadeIn(),
            exit = slideOutVertically() + fadeOut()
        ) {
            DifficultyAdaptationCard(
                oldDifficulty = oldDifficulty,
                newDifficulty = newDifficulty,
                adaptationInfo = adaptationInfo
            )
        }
    }
}

/**
 * Ranking Improvement Display Card - Updated to match actual RankingImprovement data class
 */
@Composable
fun RankingImprovementCard(
    improvement: CompetitiveRankingManager.RankingImprovement,
    puzzleType: String
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (improvement.improved) RvSuccess else RvSky
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = if (improvement.improved) "🏆" else "📊",
                    fontSize = 24.sp
                )
                Text(
                    text = "Performance Ranking",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = RvOnTone
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Percentile comparison
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "Previous",
                        fontSize = 12.sp,
                        color = RvOnTone.copy(alpha = 0.8f)
                    )
                    Text(
                        text = "${improvement.oldPercentile}%",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = RvOnTone
                    )
                }

                // Improvement indicator
                if (improvement.improved && improvement.percentileGain > 0) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.TrendingUp,
                            contentDescription = "Improvement",
                            tint = RvOnTone,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = "+${improvement.percentileGain}%",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = RvOnTone
                        )
                    }
                } else if (improvement.percentileGain == 0) {
                    Icon(
                        imageVector = Icons.Default.HorizontalRule,
                        contentDescription = "No change",
                        tint = RvOnTone.copy(alpha = 0.7f),
                        modifier = Modifier.size(20.dp)
                    )
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "Current",
                        fontSize = 12.sp,
                        color = RvOnTone.copy(alpha = 0.8f)
                    )
                    Text(
                        text = "${improvement.newPercentile}%",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = RvOnTone
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Ranking message
            Card(
                colors = CardDefaults.cardColors(containerColor = RvOnTone.copy(alpha = 0.15f)),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(
                    text = improvement.rankingMessage,
                    fontSize = 14.sp,
                    color = RvOnTone,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(12.dp),
                    textAlign = TextAlign.Center
                )
            }

            // Performance interpretation
            if (improvement.newPercentile >= 90) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "🎖️ Elite Performer",
                    fontSize = 12.sp,
                    color = RvOnTone,
                    fontWeight = FontWeight.Bold
                )
            } else if (improvement.newPercentile >= 75) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "⭐ Advanced Player",
                    fontSize = 12.sp,
                    color = RvOnTone,
                    fontWeight = FontWeight.Bold
                )
            } else if (improvement.newPercentile >= 50) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "📈 Above Average",
                    fontSize = 12.sp,
                    color = RvOnTone,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

/**
 * Difficulty Adaptation Display Card
 */
@Composable
fun DifficultyAdaptationCard(
    oldDifficulty: DifficultyManager.DifficultyLevel? = null,
    newDifficulty: DifficultyManager.DifficultyLevel,
    adaptationInfo: DifficultyManager.AdaptiveConfig? = null
) {
    val hasLevelChange = oldDifficulty != null && oldDifficulty.name != newDifficulty.name

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (hasLevelChange) RvSky else RvViolet
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = if (hasLevelChange) "⚡" else "🎯",
                    fontSize = 20.sp
                )
                Text(
                    text = if (hasLevelChange) "Difficulty Updated!" else "Performance Analysis",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = RvOnTone
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (hasLevelChange) {
                // Difficulty change display
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "Previous",
                            fontSize = 12.sp,
                            color = RvOnTone.copy(alpha = 0.8f)
                        )
                        Text(
                            text = oldDifficulty!!.name,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium,
                            color = RvOnTone
                        )
                    }

                    Icon(
                        imageVector = Icons.Default.ArrowForward,
                        contentDescription = "Arrow",
                        tint = RvOnTone,
                        modifier = Modifier.size(20.dp)
                    )

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "Current",
                            fontSize = 12.sp,
                            color = RvOnTone.copy(alpha = 0.8f)
                        )
                        Text(
                            text = newDifficulty.name,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = RvOnTone
                        )
                    }
                }

                // Show level indices if helpful
                if (oldDifficulty != null) {
                    if (oldDifficulty.index != newDifficulty.index) {
                        Spacer(modifier = Modifier.height(8.dp))
                        if (oldDifficulty != null) {
                            Text(
                                text = "Level ${oldDifficulty.index + 1} → ${newDifficulty.index + 1}",
                                fontSize = 12.sp,
                                color = RvOnTone.copy(alpha = 0.7f)
                            )
                        }
                    }
                }
            } else {
                // Current difficulty info
                Text(
                    text = "Current Difficulty: ${newDifficulty.name}",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    color = RvOnTone
                )

                Text(
                    text = "Level ${newDifficulty.index + 1}",
                    fontSize = 12.sp,
                    color = RvOnTone.copy(alpha = 0.7f)
                )
            }

            // Adaptive insights
            adaptationInfo?.let { info ->
                Spacer(modifier = Modifier.height(12.dp))

                // Adjustment reason
                Text(
                    text = info.adjustmentReason,
                    fontSize = 13.sp,
                    color = RvOnTone.copy(alpha = 0.9f),
                    textAlign = TextAlign.Center
                )

                // Confidence indicator
                if (info.confidenceScore > 0.7f) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text("🎯", fontSize = 12.sp)
                        Text(
                            text = "High confidence adjustment",
                            fontSize = 11.sp,
                            color = RvOnTone.copy(alpha = 0.8f)
                        )
                    }
                }
            }

            // Show difficulty details
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                if (newDifficulty.timeLimit > 0) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "⏱️",
                            fontSize = 14.sp
                        )
                        Text(
                            text = "${newDifficulty.timeLimit}s",
                            fontSize = 11.sp,
                            color = RvOnTone.copy(alpha = 0.8f)
                        )
                    }
                }

                if (newDifficulty.livesAllowed > 0) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "❤️",
                            fontSize = 14.sp
                        )
                        Text(
                            text = "${newDifficulty.livesAllowed}",
                            fontSize = 11.sp,
                            color = RvOnTone.copy(alpha = 0.8f)
                        )
                    }
                }

                if (newDifficulty.basePoints > 0) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "🎯",
                            fontSize = 14.sp
                        )
                        Text(
                            text = "${newDifficulty.basePoints}pt",
                            fontSize = 11.sp,
                            color = RvOnTone.copy(alpha = 0.8f)
                        )
                    }
                }
            }
        }
    }
}

/**
 * Integration helper for existing completion screens
 */
@Composable
fun IntegrateEnhancedCompletionFeatures(
    puzzleType: String,
    sessionScore: Int,
    sessionStats: SessionStatistics?,
    onEnhancedDataLoaded: (
        rankingImprovement: CompetitiveRankingManager.RankingImprovement?,
        adaptationInfo: DifficultyManager.AdaptiveConfig?
    ) -> Unit = { _, _ -> }
) {
    val context = LocalContext.current
    val currentUser = FirebaseAuth.getInstance().currentUser

    LaunchedEffect(sessionScore) {
        var rankingImprovement: CompetitiveRankingManager.RankingImprovement? = null
        var adaptationInfo: DifficultyManager.AdaptiveConfig? = null

        try {
            // Get ranking improvement
            if (currentUser != null && sessionScore > 0) {
                val rankingManager = CompetitiveRankingManager.getInstance()
                rankingImprovement = rankingManager.submitScoreAndGetImprovement(
                    userId = currentUser.uid,
                    puzzleType = puzzleType,
                    difficulty = "Medium", // You'd get this from your current system
                    sessionScore = sessionScore
                )
            }

            // Get adaptive difficulty info
            if (sessionStats != null) {
                val difficultyManager = DifficultyManager()
                val performance = DifficultyManager.PlayerPerformance(
                    accuracy = sessionStats.winRate,
                    averageTime = (sessionStats.totalTimeSeconds / maxOf(
                        1,
                        sessionStats.totalAnswers
                    )).toFloat(),
                    streakLength = sessionStats.bestStreak,
                    livesRemaining = 3, // You'd track this from the actual game state
                    gameScore = sessionScore,
                    difficulty = "Medium", // You'd get this from current difficulty level
                    puzzleType = puzzleType,
                    timestamp = System.currentTimeMillis(),
                    timeEfficiency = if (sessionStats.totalAnswers > 0) {
                        sessionStats.averageTimePerPuzzle.toFloat() / 30f // Normalized to expected 30s per puzzle
                    } else {
                        1.0f
                    }
                )
                adaptationInfo = difficultyManager.recordPerformance(performance)
            }

        } catch (e: Exception) {
            Log.e("EnhancedCompletion", "Failed to load enhanced data", e)
        }

        onEnhancedDataLoaded(rankingImprovement, adaptationInfo)
    }
}