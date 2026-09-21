// AdaptiveConversionPuzzleScreen.kt - Enhanced with adaptive difficulty
package com.kreativekoala.riddleverse

import com.kreativekoala.riddleverse.ui.theme.*
import android.util.Log
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.random.Random

// Enhanced conversion puzzle with adaptive difficulty
data class AdaptiveConversionPuzzle(
    val leftBlock: ConversionBlock,
    val rightBlock: ConversionBlock,
    val correctAnswer: ComparisonResult,
    val conversionType: ConversionType,
    val difficulty: String
)

// Adaptive conversion puzzle generator
class AdaptiveConversionPuzzleGenerator {
    private val converter = UniversalConverter()

    fun generateForLevel(difficultyLevel: DifficultyManager.DifficultyLevel): AdaptiveConversionPuzzle {
        val conversionType = selectConversionTypeForDifficulty(difficultyLevel)
        val (leftBlock, rightBlock) = generateConversionPair(conversionType, difficultyLevel)

        val comparison = converter.compareValues(
            leftBlock.value, leftBlock.unit,
            rightBlock.value, rightBlock.unit
        )

        val correctAnswer = when {
            comparison > 0.1 -> ComparisonResult.LEFT_HEAVIER
            comparison < -0.1 -> ComparisonResult.RIGHT_HEAVIER
            else -> ComparisonResult.EQUAL
        }

        return AdaptiveConversionPuzzle(
            leftBlock = leftBlock,
            rightBlock = rightBlock,
            correctAnswer = correctAnswer,
            conversionType = conversionType,
            difficulty = difficultyLevel.name
        )
    }

    private fun selectConversionTypeForDifficulty(difficultyLevel: DifficultyManager.DifficultyLevel): ConversionType {
        return when (difficultyLevel.name.lowercase()) {
            "tutorial", "beginner" -> listOf(ConversionType.DISTANCE, ConversionType.WEIGHT).random()
            "easy", "easy+" -> listOf(ConversionType.DISTANCE, ConversionType.WEIGHT, ConversionType.VOLUME).random()
            "medium-", "medium", "medium+" -> listOf(
                ConversionType.DISTANCE, ConversionType.WEIGHT, ConversionType.VOLUME,
                ConversionType.TIME, ConversionType.TEMPERATURE
            ).random()
            "hard-", "hard", "hard+" -> listOf(
                ConversionType.TEMPERATURE, ConversionType.AREA, ConversionType.SPEED,
                ConversionType.VOLUME, ConversionType.WEIGHT
            ).random()
            else -> ConversionType.values().filter { it != ConversionType.UNKNOWN }.random()
        }
    }

    private fun generateConversionPair(
        conversionType: ConversionType,
        difficultyLevel: DifficultyManager.DifficultyLevel
    ): Pair<ConversionBlock, ConversionBlock> {
        val difficultyFactor = when (difficultyLevel.name.lowercase()) {
            "tutorial", "beginner" -> 0.3  // Easy to distinguish
            "easy", "easy+" -> 0.25
            "medium-", "medium", "medium+" -> 0.2
            "hard-", "hard", "hard+" -> 0.15
            else -> 0.1  // Very close values
        }

        return when (conversionType) {
            ConversionType.TEMPERATURE -> generateTemperaturePair(difficultyFactor)
            ConversionType.WEIGHT -> generateWeightPair(difficultyFactor)
            ConversionType.DISTANCE -> generateDistancePair(difficultyFactor)
            ConversionType.VOLUME -> generateVolumePair(difficultyFactor)
            ConversionType.TIME -> generateTimePair(difficultyFactor)
            ConversionType.AREA -> generateAreaPair(difficultyFactor)
            ConversionType.SPEED -> generateSpeedPair(difficultyFactor)
            ConversionType.UNKNOWN -> generateDistancePair(difficultyFactor) // Fallback
        }
    }

    private fun generateTemperaturePair(difficultyFactor: Double): Pair<ConversionBlock, ConversionBlock> {
        val units = listOf("celsius", "fahrenheit", "kelvin")
        val baseTemp = Random.nextDouble(-10.0, 100.0)
        val variation = baseTemp * difficultyFactor * Random.nextDouble(0.5, 2.0)

        val leftTemp = baseTemp
        val rightTemp = baseTemp + variation

        val leftUnit = units.random()
        val rightUnit = units.filter { it != leftUnit }.random()

        return Pair(
            ConversionBlock(
                id = 1,
                label = "${leftTemp.toInt()}°${converter.getShortUnit(leftUnit).last()}",
                value = leftTemp,
                unit = leftUnit,
                color = RvFlame
            ),
            ConversionBlock(
                id = 2,
                label = "${rightTemp.toInt()}°${converter.getShortUnit(rightUnit).last()}",
                value = rightTemp,
                unit = rightUnit,
                color = Color(0xFF4ECDC4)
            )
        )
    }

    private fun generateWeightPair(difficultyFactor: Double): Pair<ConversionBlock, ConversionBlock> {
        val units = listOf("grams", "kilograms", "pounds", "ounces")
        val baseWeight = Random.nextDouble(100.0, 5000.0)
        val variation = baseWeight * difficultyFactor * Random.nextDouble(0.8, 1.5)

        val leftWeight = baseWeight
        val rightWeight = baseWeight + variation

        val leftUnit = units.random()
        val rightUnit = units.filter { it != leftUnit }.random()

        return Pair(
            ConversionBlock(
                id = 1,
                label = "${leftWeight.toInt()} ${converter.getShortUnit(leftUnit)}",
                value = leftWeight,
                unit = leftUnit,
                color = Color(0xFF9B59B6)
            ),
            ConversionBlock(
                id = 2,
                label = "${rightWeight.toInt()} ${converter.getShortUnit(rightUnit)}",
                value = rightWeight,
                unit = rightUnit,
                color = Color(0xFFE74C3C)
            )
        )
    }

    private fun generateDistancePair(difficultyFactor: Double): Pair<ConversionBlock, ConversionBlock> {
        val units = listOf("meters", "kilometers", "miles", "feet", "inches")
        val baseDistance = Random.nextDouble(10.0, 1000.0)
        val variation = baseDistance * difficultyFactor * Random.nextDouble(0.7, 1.8)

        val leftDistance = baseDistance
        val rightDistance = baseDistance + variation

        val leftUnit = units.random()
        val rightUnit = units.filter { it != leftUnit }.random()

        return Pair(
            ConversionBlock(
                id = 1,
                label = "${leftDistance.toInt()} ${converter.getShortUnit(leftUnit)}",
                value = leftDistance,
                unit = leftUnit,
                color = RvSky
            ),
            ConversionBlock(
                id = 2,
                label = "${rightDistance.toInt()} ${converter.getShortUnit(rightUnit)}",
                value = rightDistance,
                unit = rightUnit,
                color = Color(0xFF2ECC71)
            )
        )
    }

    private fun generateVolumePair(difficultyFactor: Double): Pair<ConversionBlock, ConversionBlock> {
        val units = listOf("liters", "milliliters", "gallons", "quarts", "cups")
        val baseVolume = Random.nextDouble(50.0, 2000.0)
        val variation = baseVolume * difficultyFactor * Random.nextDouble(0.6, 1.4)

        val leftVolume = baseVolume
        val rightVolume = baseVolume + variation

        val leftUnit = units.random()
        val rightUnit = units.filter { it != leftUnit }.random()

        return Pair(
            ConversionBlock(
                id = 1,
                label = "${leftVolume.toInt()} ${converter.getShortUnit(leftUnit)}",
                value = leftVolume,
                unit = leftUnit,
                color = Color(0xFF1ABC9C)
            ),
            ConversionBlock(
                id = 2,
                label = "${rightVolume.toInt()} ${converter.getShortUnit(rightUnit)}",
                value = rightVolume,
                unit = rightUnit,
                color = Color(0xFFF39C12)
            )
        )
    }

    private fun generateTimePair(difficultyFactor: Double): Pair<ConversionBlock, ConversionBlock> {
        val units = listOf("seconds", "minutes", "hours", "days")
        val baseTime = Random.nextDouble(60.0, 10800.0) // 1 minute to 3 hours
        val variation = baseTime * difficultyFactor * Random.nextDouble(0.5, 2.0)

        val leftTime = baseTime
        val rightTime = baseTime + variation

        val leftUnit = units.random()
        val rightUnit = units.filter { it != leftUnit }.random()

        return Pair(
            ConversionBlock(
                id = 1,
                label = "${leftTime.toInt()} ${converter.getShortUnit(leftUnit)}",
                value = leftTime,
                unit = leftUnit,
                color = Color(0xFFE67E22)
            ),
            ConversionBlock(
                id = 2,
                label = "${rightTime.toInt()} ${converter.getShortUnit(rightUnit)}",
                value = rightTime,
                unit = rightUnit,
                color = Color(0xFF8E44AD)
            )
        )
    }

    private fun generateAreaPair(difficultyFactor: Double): Pair<ConversionBlock, ConversionBlock> {
        val units = listOf("square meters", "square feet", "acres")
        val baseArea = Random.nextDouble(100.0, 10000.0)
        val variation = baseArea * difficultyFactor * Random.nextDouble(0.8, 1.6)

        val leftArea = baseArea
        val rightArea = baseArea + variation

        val leftUnit = units.random()
        val rightUnit = units.filter { it != leftUnit }.random()

        return Pair(
            ConversionBlock(
                id = 1,
                label = "${leftArea.toInt()} ${converter.getShortUnit(leftUnit)}",
                value = leftArea,
                unit = leftUnit,
                color = RvSuccess
            ),
            ConversionBlock(
                id = 2,
                label = "${rightArea.toInt()} ${converter.getShortUnit(rightUnit)}",
                value = rightArea,
                unit = rightUnit,
                color = Color(0xFFD35400)
            )
        )
    }

    private fun generateSpeedPair(difficultyFactor: Double): Pair<ConversionBlock, ConversionBlock> {
        val units = listOf("meters per second", "kilometers per hour", "miles per hour")
        val baseSpeed = Random.nextDouble(10.0, 100.0)
        val variation = baseSpeed * difficultyFactor * Random.nextDouble(0.9, 1.3)

        val leftSpeed = baseSpeed
        val rightSpeed = baseSpeed + variation

        val leftUnit = units.random()
        val rightUnit = units.filter { it != leftUnit }.random()

        return Pair(
            ConversionBlock(
                id = 1,
                label = "${leftSpeed.toInt()} ${converter.getShortUnit(leftUnit)}",
                value = leftSpeed,
                unit = leftUnit,
                color = Color(0xFFC0392B)
            ),
            ConversionBlock(
                id = 2,
                label = "${rightSpeed.toInt()} ${converter.getShortUnit(rightUnit)}",
                value = rightSpeed,
                unit = rightUnit,
                color = Color(0xFF16A085)
            )
        )
    }
}

@Composable
fun AdaptiveConversionPuzzleScreen(
    initialDifficulty: String = "Medium",
    timer: String,
    onSubmitAnswer: (ComparisonResult) -> Unit,
    fetchNextPuzzle: (Int) -> Unit,
    onBack: () -> Unit = {}
) {
    val TAG = "AdaptiveConversion"

    // Adaptive difficulty manager
    val difficultyManager = remember { DifficultyManager() }
    var currentDifficultyLevel by remember { mutableStateOf(difficultyManager.getCurrentDifficulty(
        puzzleType = "conversion"
    )) }
    var adaptationInfo by remember { mutableStateOf<DifficultyManager.AdaptiveConfig?>(null) }
    var showAdaptationNotification by remember { mutableStateOf(false) }

    // ✅ NEW: Competitive ranking state
    val currentUser = FirebaseAuth.getInstance().currentUser
    var competitiveInsight by remember { mutableStateOf<CompetitiveRankingManager.CompetitiveInsight?>(null) }

    LaunchedEffect(currentDifficultyLevel) {
        if (currentUser != null) {
            try {
                val adaptiveManager = UnifiedAdaptiveManager.getInstance()
                competitiveInsight = adaptiveManager.getCompetitiveInsight(
                    userId = currentUser.uid,
                    puzzleType = "unitConversion",
                    difficulty = currentDifficultyLevel.name
                )
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load competitive insight", e)
            }
        }
    }

    // Generate puzzle based on current difficulty
    val conversionGenerator = remember { AdaptiveConversionPuzzleGenerator() }
    var currentPuzzle by remember(currentDifficultyLevel) {
        mutableStateOf(generateConversionPuzzleFromDifficulty(conversionGenerator, currentDifficultyLevel))
    }

    // Score and performance tracking
    var totalScore by remember { mutableStateOf(0) }
    var attempts by remember { mutableStateOf(0) }
    var gameStartTime by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var currentHearts by remember { mutableStateOf(currentDifficultyLevel.livesAllowed) }
    var currentStreak by remember { mutableStateOf(0) }
    var gamesPlayedThisSession by remember { mutableStateOf(0) }
    var hintUsed by remember { mutableStateOf(false) }
    var dragCount by remember { mutableStateOf(0) }

    // ✅ NEW: Session tracking
    var sessionStartTime by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var correctAnswers by remember { mutableStateOf(0) }
    var totalAnswers by remember { mutableStateOf(0) }

    // Game state
    var leftBlockState by remember { mutableStateOf(currentPuzzle.leftBlock.copy()) }
    var rightBlockState by remember { mutableStateOf(currentPuzzle.rightBlock.copy()) }
    var showContinueButton by remember { mutableStateOf(false) }
    var hasSubmitted by remember { mutableStateOf(false) }
    var showHint by remember { mutableStateOf(false) }

    val haptics = LocalHapticFeedback.current
    val density = LocalDensity.current
    val feedbackManager = rememberUnifiedFeedbackManager()
    val currentLevel = feedbackManager.getCurrentLevel()
    val streakInfo = feedbackManager.getStreakInfo()

    // ✅ UPDATED: Performance recording function
    fun recordPerformance(isCorrect: Boolean, responseTime: Long, questionDifficulty: Float) {
        recordUnifiedPerformance(
            difficultyManager = difficultyManager,
            isCorrect = isCorrect,
            timeSpent = responseTime,
            streak = currentStreak,
            livesRemaining = currentHearts,
            difficulty = currentDifficultyLevel,
            challengeComplexity = (questionDifficulty * 5).toInt(),
            totalScore = totalScore,
            puzzleType = "unitConversion"
        ) { config ->
            adaptationInfo = config
            if (config.confidenceScore > 0.5f) {
                currentDifficultyLevel = config.level
                showAdaptationNotification = SHOW_ADAPTATION_NOTICES
            }
        }
    }

    // Reset game state when difficulty changes
    LaunchedEffect(currentDifficultyLevel) {
        if (gamesPlayedThisSession > 0) {
            currentPuzzle = generateConversionPuzzleFromDifficulty(conversionGenerator, currentDifficultyLevel)
            leftBlockState = currentPuzzle.leftBlock.copy()
            rightBlockState = currentPuzzle.rightBlock.copy()
            showContinueButton = false
            hasSubmitted = false
            showHint = false
            hintUsed = false
            dragCount = 0
            currentHearts = currentDifficultyLevel.livesAllowed
            gameStartTime = System.currentTimeMillis()
        }
    }

    // Timer calculation and countdown
    val totalTimeSeconds = currentDifficultyLevel.timeLimit
    var timeRemaining by remember { mutableStateOf(totalTimeSeconds) }
    var displayTimer by remember { mutableStateOf("${totalTimeSeconds / 60}:${String.format("%02d", totalTimeSeconds % 60)}") }

    // Threshold for determining position relationships
    val positionThreshold = with(density) { 50.dp.toPx() }

    // Calculate current user answer based on block positions
    fun getCurrentAnswer(): ComparisonResult {
        return when {
            leftBlockState.offsetY < rightBlockState.offsetY - positionThreshold -> ComparisonResult.LEFT_HEAVIER
            rightBlockState.offsetY < leftBlockState.offsetY - positionThreshold -> ComparisonResult.RIGHT_HEAVIER
            else -> ComparisonResult.EQUAL
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        RvInk,
                        RvInk,
                        RvInk
                    )
                )
            )
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            Spacer(modifier = Modifier.height(32.dp))

            // ✅ REPLACE: Use unified header instead of AdaptiveConversionTopGameBar
            AdaptiveUnifiedHeader(
                level = currentLevel,
                streakInfo = streakInfo,
                timer = displayTimer,
                lives = currentHearts,
                currentDifficulty = currentDifficultyLevel,
                score = totalScore,
                puzzleType = "unitConversion",
                competitiveInsight = competitiveInsight,
                onBack = onBack,
                onHint = {
                    showHint = !showHint
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                }
            )

            // ✅ REPLACE: Use unified adaptation notification
            UnifiedAdaptationNotification(
                adaptationInfo = adaptationInfo,
                puzzleType = "unitConversion",
                visible = showAdaptationNotification,
                onDismiss = { showAdaptationNotification = false }
            )

            Spacer(modifier = Modifier.height(20.dp))

            // Title
            Text(
                text = "COMPARE ${currentPuzzle.conversionType.displayName.uppercase()}",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = RvInk,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Instructions
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Drag the blocks to show which ${currentPuzzle.conversionType.displayName.lowercase()} is greater",
                    fontSize = 14.sp,
                    color = RvInkSoft.copy(alpha = 0.8f),
                    textAlign = TextAlign.Center
                )

                if (totalScore > 0) {
                    Text(
                        text = "⚡ Quick decisions and fewer drags earn bonus points!",
                        fontSize = 12.sp,
                        color = Color.Yellow.copy(alpha = 0.7f),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }

            // Hint bubble
            AnimatedVisibility(
                visible = showHint,
                enter = fadeIn() + slideInVertically(),
                exit = fadeOut() + slideOutVertically()
            ) {
                ConversionHintBubble(
                    conversionType = currentPuzzle.conversionType,
                    leftBlock = currentPuzzle.leftBlock,
                    rightBlock = currentPuzzle.rightBlock,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 16.dp)
                )
            }

            Spacer(modifier = Modifier.height(30.dp))

            // Universal scale
            UniversalScale(
                conversionType = currentPuzzle.conversionType,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(80.dp)
                    .padding(horizontal = 40.dp)
            )

            Spacer(modifier = Modifier.height(20.dp))

            // Main content area with draggable blocks
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = 40.dp)
            ) {
                // Left block
                DraggableConversionBlock(
                    block = leftBlockState,
                    onDrag = { offset ->
                        if (!hasSubmitted) {
                            leftBlockState = leftBlockState.copy(
                                offsetY = (leftBlockState.offsetY + offset.y).coerceIn(-200f, 200f),
                                isDragging = true
                            )
                            dragCount++
                        }
                    },
                    onDragEnd = {
                        leftBlockState = leftBlockState.copy(isDragging = false)
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    },
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .offset { IntOffset(0, leftBlockState.offsetY.roundToInt()) }
                )

                // Right block
                DraggableConversionBlock(
                    block = rightBlockState,
                    onDrag = { offset ->
                        if (!hasSubmitted) {
                            rightBlockState = rightBlockState.copy(
                                offsetY = (rightBlockState.offsetY + offset.y).coerceIn(-200f, 200f),
                                isDragging = true
                            )
                            dragCount++
                        }
                    },
                    onDragEnd = {
                        rightBlockState = rightBlockState.copy(isDragging = false)
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    },
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .offset { IntOffset(0, rightBlockState.offsetY.roundToInt()) }
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Continue button
            AnimatedVisibility(
                visible = showContinueButton,
                enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
            ) {
                ContinueButton(
                    onClick = {
                        attempts++
                        hasSubmitted = true
                        val userAnswer = getCurrentAnswer()
                        val isCorrect = userAnswer == currentPuzzle.correctAnswer
                        val timeSpent = System.currentTimeMillis() - gameStartTime

                        totalAnswers++
                        if (isCorrect) {
                            correctAnswers++
                            val score = calculateUnifiedAdaptiveScore(
                                isCorrect = true,
                                timeSpent = timeSpent,
                                difficulty = currentDifficultyLevel,
                                challengeComplexity = 3,
                                currentStreak = currentStreak + 1,
                                challengesCompleted = attempts,
                                timeLimit = currentDifficultyLevel.timeLimit,
                                puzzleType = "unitConversion"
                            )
                            totalScore += score
                            currentStreak++
                            gamesPlayedThisSession++
                        } else {
                            currentHearts = maxOf(0, currentHearts - 1)
                            currentStreak = 0
                        }

                        recordPerformance(isCorrect, timeSpent, 0.5f)

                        feedbackManager.showFeedback(
                            puzzleType = "unitConversion",
                            isCorrect = isCorrect,
                            userAnswer = when (userAnswer) {
                                ComparisonResult.LEFT_HEAVIER -> "${currentPuzzle.leftBlock.label} is ${getComparisonWord(currentPuzzle.conversionType, true)}"
                                ComparisonResult.RIGHT_HEAVIER -> "${currentPuzzle.rightBlock.label} is ${getComparisonWord(currentPuzzle.conversionType, true)}"
                                ComparisonResult.EQUAL -> "Both ${currentPuzzle.conversionType.displayName.lowercase()}s are equal"
                            },
                            correctAnswer = when (currentPuzzle.correctAnswer) {
                                ComparisonResult.LEFT_HEAVIER -> "${currentPuzzle.leftBlock.label} is ${getComparisonWord(currentPuzzle.conversionType, true)}"
                                ComparisonResult.RIGHT_HEAVIER -> "${currentPuzzle.rightBlock.label} is ${getComparisonWord(currentPuzzle.conversionType, true)}"
                                ComparisonResult.EQUAL -> "Both ${currentPuzzle.conversionType.displayName.lowercase()}s are equal"
                            },
                            timeSpent = timeSpent,
                            difficulty = currentDifficultyLevel.name,
                            timeRemaining = timeRemaining,
                            totalTime = totalTimeSeconds,
                            onComplete = {
                                onSubmitAnswer(userAnswer)
                                if (isCorrect) {
                                    fetchNextPuzzle(totalScore)
                                } else {
                                    if (currentHearts > 0) {
                                        // Reset for retry
                                        leftBlockState = currentPuzzle.leftBlock.copy()
                                        rightBlockState = currentPuzzle.rightBlock.copy()
                                        showContinueButton = false
                                        hasSubmitted = false
                                        dragCount = 0
                                    } else {
                                        // Game over
                                        fetchNextPuzzle(totalScore)
                                    }
                                }
                            }
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 40.dp)
                        .padding(bottom = 40.dp)
                )
            }
        }

        EnhancedUniversalFeedback(feedbackManager)
    }

    // Timer countdown effect
    LaunchedEffect(timeRemaining, hasSubmitted) {
        if (timeRemaining > 0 && !hasSubmitted) {
            delay(1000)
            timeRemaining--
            displayTimer = "${timeRemaining / 60}:${String.format("%02d", timeRemaining % 60)}"
        } else if (timeRemaining == 0 && !hasSubmitted) {
            Log.d(TAG, "⏰ Time's up! Final score: $totalScore")
            hasSubmitted = true

            recordPerformance(false, totalTimeSeconds.toLong() * 1000L, 0.5f)
            onSubmitAnswer(ComparisonResult.EQUAL)
            fetchNextPuzzle(totalScore)
        }
    }

    // Check if blocks have been moved enough to show continue button
    LaunchedEffect(leftBlockState.offsetY, rightBlockState.offsetY) {
        val totalMovement = abs(leftBlockState.offsetY) + abs(rightBlockState.offsetY)
        showContinueButton = totalMovement > positionThreshold && !hasSubmitted
    }

    // ✅ NEW: Session completion handling
    if (hasSubmitted) {
        UnifiedSessionCompletionHandler(
            puzzleType = "unitConversion",
            sessionScore = totalScore,
            sessionStats = SessionStatistics(
                correctAnswers = correctAnswers,
                totalAnswers = totalAnswers,
                totalTimeSeconds = ((System.currentTimeMillis() - sessionStartTime) / 1000).toInt(),
                bestStreak = currentStreak,
                winRate = if (totalAnswers > 0) correctAnswers.toFloat() / totalAnswers else 0f,
                totalScore = totalScore,
                averageTimePerPuzzle = if (totalAnswers > 0) { // Fixed: Calculate average time
                    ((System.currentTimeMillis() - sessionStartTime) / 1000).toInt() / totalAnswers
                } else {
                    0
                },
                currentStreak = currentStreak,
                individualTimes = emptyList(),
            ),
            currentDifficulty = currentDifficultyLevel
        ) { result ->
            // Session completion handled
        }
    }
}

@Composable
fun AdaptiveConversionTopGameBar(
    level: UserLevel,
    streakInfo: StreakInfo,
    timer: String,
    lives: Int,
    currentDifficulty: DifficultyManager.DifficultyLevel,
    conversionType: ConversionType,
    onBack: () -> Unit,
    onHintClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.statusBarsPadding().fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top
    ) {
        // Left side: Back button, hint button, and level
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier.size(44.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.ArrowBack,
                    contentDescription = "Back",
                    tint = RvInk,
                    modifier = Modifier.size(28.dp)
                )
            }

            IconButton(
                onClick = onHintClick,
                modifier = Modifier.size(44.dp)
            ) {
                Text(
                    text = "💡",
                    fontSize = 20.sp
                )
            }

            Column {
                Text(
                    text = "Level ${level.level}",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = RvInk
                )

                // Adaptive difficulty indicator
                Text(
                    text = currentDifficulty.name,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color.Cyan
                )

                LevelProgressBar(
                    level = level,
                    modifier = Modifier.width(100.dp)
                )
            }
        }

        // Center: Lives display with difficulty-based count
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            repeat(currentDifficulty.livesAllowed) { index ->
                Text(
                    text = if (index < lives) "❤️" else "🤍",
                    fontSize = 16.sp
                )
            }
        }

        // Right side: Timer and conversion type
        Column(
            horizontalAlignment = Alignment.End
        ) {
            Text(
                text = timer,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = if (timer.startsWith("0:") && timer.substring(2).toIntOrNull()?.let { it <= 30 } == true) {
                    Color.Red
                } else {
                    Color.White
                }
            )

            Text(
                text = "${conversionType.icon} ${conversionType.displayName}",
                fontSize = 12.sp,
                color = RvInkSoft.copy(alpha = 0.8f),
                modifier = Modifier.padding(top = 4.dp)
            )

            if (streakInfo.currentStreak > 0) {
                StreakDisplay(
                    streakInfo = streakInfo,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
    }
}

// Helper function to generate puzzle from difficulty level
fun generateConversionPuzzleFromDifficulty(
    generator: AdaptiveConversionPuzzleGenerator,
    difficultyLevel: DifficultyManager.DifficultyLevel
): AdaptiveConversionPuzzle {
    return generator.generateForLevel(difficultyLevel)
}