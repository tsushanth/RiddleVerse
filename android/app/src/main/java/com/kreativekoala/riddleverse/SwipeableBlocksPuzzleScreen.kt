package com.kreativekoala.riddleverse

import android.util.Log
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.roundToInt

data class WeightBlock(
    val id: Int,
    val label: String,
    val value: Double, // The actual weight value for comparison
    val color: Color,
    var offsetY: Float = 0f,
    var isDragging: Boolean = false
)

enum class ComparisonResult {
    LEFT_HEAVIER,    // Left block should be higher
    RIGHT_HEAVIER,   // Right block should be higher
    EQUAL           // Blocks should be at same level
}

enum class ConversionType(val displayName: String, val icon: String) {
    TEMPERATURE("Temperature", "🌡️"),
    WEIGHT("Weight", "⚖️"),
    DISTANCE("Distance", "📏"),
    VOLUME("Volume", "🥤"),
    TIME("Time", "⏰"),
    AREA("Area", "📐"),
    SPEED("Speed", "🏃"),
    UNKNOWN("Comparison", "🔄")
}

class UniversalConverter {

    // Base units for each conversion type
    private val temperatureToKelvin = mapOf(
        "celsius" to { temp: Double -> temp + 273.15 },
        "fahrenheit" to { temp: Double -> (temp - 32) * 5.0/9.0 + 273.15 },
        "kelvin" to { temp: Double -> temp }
    )

    private val weightToGrams = mapOf(
        "grams" to { weight: Double -> weight },
        "kilograms" to { weight: Double -> weight * 1000 },
        "pounds" to { weight: Double -> weight * 453.592 },
        "ounces" to { weight: Double -> weight * 28.3495 },
        "lbs" to { weight: Double -> weight * 453.592 }
    )

    private val distanceToMeters = mapOf(
        "meters" to { dist: Double -> dist },
        "kilometers" to { dist: Double -> dist * 1000 },
        "miles" to { dist: Double -> dist * 1609.34 },
        "feet" to { dist: Double -> dist * 0.3048 },
        "inches" to { dist: Double -> dist * 0.0254 },
        "yards" to { dist: Double -> dist * 0.9144 },
        "centimeters" to { dist: Double -> dist * 0.01 }
    )

    private val volumeToLiters = mapOf(
        "liters" to { vol: Double -> vol },
        "milliliters" to { vol: Double -> vol * 0.001 },
        "gallons" to { vol: Double -> vol * 3.78541 },
        "quarts" to { vol: Double -> vol * 0.946353 },
        "pints" to { vol: Double -> vol * 0.473176 },
        "cups" to { vol: Double -> vol * 0.236588 }
    )

    private val timeToSeconds = mapOf(
        "seconds" to { time: Double -> time },
        "minutes" to { time: Double -> time * 60 },
        "hours" to { time: Double -> time * 3600 },
        "days" to { time: Double -> time * 86400 },
        "weeks" to { time: Double -> time * 604800 }
    )

    private val areaToSquareMeters = mapOf(
        "square meters" to { area: Double -> area },
        "square kilometers" to { area: Double -> area * 1_000_000 },
        "square feet" to { area: Double -> area * 0.092903 },
        "square miles" to { area: Double -> area * 2_590_000 },
        "acres" to { area: Double -> area * 4047 }
    )

    private val speedToMPS = mapOf(
        "meters per second" to { speed: Double -> speed },
        "kilometers per hour" to { speed: Double -> speed * 0.277778 },
        "miles per hour" to { speed: Double -> speed * 0.44704 },
        "feet per second" to { speed: Double -> speed * 0.3048 }
    )

    fun compareValues(value1: Double, unit1: String, value2: Double, unit2: String): Double {
        val normalizedUnit1 = unit1.lowercase().trim()
        val normalizedUnit2 = unit2.lowercase().trim()

        // Try each conversion type
        temperatureToKelvin[normalizedUnit1]?.let { converter1 ->
            temperatureToKelvin[normalizedUnit2]?.let { converter2 ->
                val temp1 = converter1(value1)
                val temp2 = converter2(value2)
                return temp1 - temp2
            }
        }

        weightToGrams[normalizedUnit1]?.let { converter1 ->
            weightToGrams[normalizedUnit2]?.let { converter2 ->
                val weight1 = converter1(value1)
                val weight2 = converter2(value2)
                return weight1 - weight2
            }
        }

        distanceToMeters[normalizedUnit1]?.let { converter1 ->
            distanceToMeters[normalizedUnit2]?.let { converter2 ->
                val dist1 = converter1(value1)
                val dist2 = converter2(value2)
                return dist1 - dist2
            }
        }

        volumeToLiters[normalizedUnit1]?.let { converter1 ->
            volumeToLiters[normalizedUnit2]?.let { converter2 ->
                val vol1 = converter1(value1)
                val vol2 = converter2(value2)
                return vol1 - vol2
            }
        }

        timeToSeconds[normalizedUnit1]?.let { converter1 ->
            timeToSeconds[normalizedUnit2]?.let { converter2 ->
                val time1 = converter1(value1)
                val time2 = converter2(value2)
                return time1 - time2
            }
        }

        areaToSquareMeters[normalizedUnit1]?.let { converter1 ->
            areaToSquareMeters[normalizedUnit2]?.let { converter2 ->
                val area1 = converter1(value1)
                val area2 = converter2(value2)
                return area1 - area2
            }
        }

        speedToMPS[normalizedUnit1]?.let { converter1 ->
            speedToMPS[normalizedUnit2]?.let { converter2 ->
                val speed1 = converter1(value1)
                val speed2 = converter2(value2)
                return speed1 - speed2
            }
        }

        // If no conversion found, compare as raw values
        return value1 - value2
    }

    fun getConversionType(unit1: String, unit2: String): ConversionType {
        val normalizedUnit1 = unit1.lowercase().trim()
        val normalizedUnit2 = unit2.lowercase().trim()

        return when {
            temperatureToKelvin.containsKey(normalizedUnit1) || temperatureToKelvin.containsKey(normalizedUnit2) -> ConversionType.TEMPERATURE
            weightToGrams.containsKey(normalizedUnit1) || weightToGrams.containsKey(normalizedUnit2) -> ConversionType.WEIGHT
            distanceToMeters.containsKey(normalizedUnit1) || distanceToMeters.containsKey(normalizedUnit2) -> ConversionType.DISTANCE
            volumeToLiters.containsKey(normalizedUnit1) || volumeToLiters.containsKey(normalizedUnit2) -> ConversionType.VOLUME
            timeToSeconds.containsKey(normalizedUnit1) || timeToSeconds.containsKey(normalizedUnit2) -> ConversionType.TIME
            areaToSquareMeters.containsKey(normalizedUnit1) || areaToSquareMeters.containsKey(normalizedUnit2) -> ConversionType.AREA
            speedToMPS.containsKey(normalizedUnit1) || speedToMPS.containsKey(normalizedUnit2) -> ConversionType.SPEED
            else -> ConversionType.UNKNOWN
        }
    }

    fun getShortUnit(unit: String): String {
        return when (unit.lowercase().trim()) {
            "celsius" -> "°C"
            "fahrenheit" -> "°F"
            "kelvin" -> "K"
            "grams" -> "g"
            "kilograms" -> "kg"
            "pounds", "lbs" -> "lbs"
            "ounces" -> "oz"
            "meters" -> "m"
            "kilometers" -> "km"
            "miles" -> "mi"
            "feet" -> "ft"
            "inches" -> "in"
            "yards" -> "yd"
            "centimeters" -> "cm"
            "liters" -> "L"
            "milliliters" -> "mL"
            "gallons" -> "gal"
            "quarts" -> "qt"
            "pints" -> "pt"
            "cups" -> "cups"
            "seconds" -> "sec"
            "minutes" -> "min"
            "hours" -> "hr"
            "days" -> "days"
            "weeks" -> "wks"
            "square meters" -> "m²"
            "square kilometers" -> "km²"
            "square feet" -> "ft²"
            "square miles" -> "mi²"
            "acres" -> "acres"
            "meters per second" -> "m/s"
            "kilometers per hour" -> "km/h"
            "miles per hour" -> "mph"
            "feet per second" -> "ft/s"
            else -> unit
        }
    }
}

data class ConversionBlock(
    val id: Int,
    val label: String,
    val value: Double,
    val unit: String,
    val color: Color,
    var offsetY: Float = 0f,
    var isDragging: Boolean = false
)

@Composable
fun ConversionPuzzleScreen(
    difficulty: String = "Medium",
    timer: String = "1:45",
    hearts: Int = 3,
    level: String,
    leftBlock: ConversionBlock,
    rightBlock: ConversionBlock,
    correctAnswer: ComparisonResult,
    conversionType: ConversionType,
    onSubmitAnswer: (ComparisonResult) -> Unit,
    fetchNextPuzzle: (Int) -> Unit,
    onBack: (() -> Unit)? = null
) {
    val TAG = "ConversionPuzzle"

    // Score tracking state
    var totalScore by remember { mutableIntStateOf(0) }
    var attempts by remember { mutableIntStateOf(0) }
    var gameStartTime by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var hintUsed by remember { mutableStateOf(false) }
    var dragCount by remember { mutableIntStateOf(0) }

    var leftBlockState by remember { mutableStateOf(leftBlock.copy()) }
    var rightBlockState by remember { mutableStateOf(rightBlock.copy()) }
    var showContinueButton by remember { mutableStateOf(false) }
    var hasSubmitted by remember { mutableStateOf(false) }
    var showHint by remember { mutableStateOf(false) }

    val haptics = LocalHapticFeedback.current
    val density = LocalDensity.current
    val feedbackManager = rememberUnifiedFeedbackManager()
    val currentLevel = feedbackManager.getCurrentLevel()
    val streakInfo = feedbackManager.getStreakInfo()

    // Timer tracking
    val totalTimeSeconds = remember(timer) {
        val parts = timer.split(":")
        if (parts.size == 2) {
            (parts[0].toIntOrNull() ?: 0) * 60 + (parts[1].toIntOrNull() ?: 0)
        } else {
            105 // Default 1:45
        }
    }

    var timeRemaining by remember { mutableIntStateOf(totalTimeSeconds) }
    var displayTimer by remember { mutableStateOf(timer) }

    // Timer countdown effect
    LaunchedEffect(timeRemaining, hasSubmitted) {
        if (timeRemaining > 0 && !hasSubmitted) {
            delay(1000L)
            timeRemaining--
            displayTimer = "${timeRemaining / 60}:${String.format("%02d", timeRemaining % 60)}"
        } else if (timeRemaining == 0 && !hasSubmitted) {
            // Time's up - complete with current score
            Log.d(TAG, "⏰ Time's up! Final score: $totalScore")
            hasSubmitted = true
            onSubmitAnswer(ComparisonResult.EQUAL) // Default answer on timeout
            fetchNextPuzzle(totalScore)
        }
    }

    // Threshold for determining position relationships (in dp converted to pixels)
    val positionThreshold = with(density) { 50.dp.toPx() }

    // Calculate conversion score
    fun calculateConversionScore(
        isCorrect: Boolean,
        timeSpent: Long,
        attemptNumber: Int,
        conversionType: ConversionType,
        difficulty: String,
        hintUsed: Boolean,
        dragInteractions: Int
    ): Int {
        if (!isCorrect) return 0

        // Base points by difficulty
        val basePoints = when (difficulty.lowercase()) {
            "easy" -> 40
            "medium" -> 60
            "hard" -> 80
            "expert" -> 100
            else -> 60
        }

        // Conversion complexity bonus based on type
        val complexityMultiplier = when (conversionType) {
            ConversionType.TEMPERATURE -> 1.6f // Complex due to different zero points
            ConversionType.AREA -> 1.5f        // Complex due to squared units
            ConversionType.SPEED -> 1.4f       // Complex due to compound units
            ConversionType.VOLUME -> 1.3f      // Moderately complex
            ConversionType.WEIGHT -> 1.2f      // Moderately complex
            ConversionType.DISTANCE -> 1.1f    // Simple linear conversion
            ConversionType.TIME -> 1.1f        // Simple linear conversion
            ConversionType.UNKNOWN -> 1.0f     // Basic comparison
        }

        // Unit conversion mastery bonus
        val masteryBonus = (basePoints * 0.25f).toInt()

        // Speed bonus (faster understanding = higher score)
        val timeSeconds = timeSpent / 1000f
        val speedBonus = when {
            timeSeconds <= 10f -> (basePoints * 0.4f).toInt() // Very fast
            timeSeconds <= 20f -> (basePoints * 0.25f).toInt() // Fast
            timeSeconds <= 30f -> (basePoints * 0.1f).toInt() // Moderate
            else -> 0
        }

        // First attempt bonus
        val attemptBonus = if (attemptNumber == 1) {
            (basePoints * 0.3f).toInt()
        } else {
            maxOf(0, (basePoints * 0.3f * (1f - (attemptNumber - 1) * 0.2f)).toInt())
        }

        // Efficient interaction bonus (fewer drags = better understanding)
        val efficiencyBonus = when {
            dragInteractions <= 2 -> (basePoints * 0.2f).toInt() // Very efficient
            dragInteractions <= 4 -> (basePoints * 0.1f).toInt() // Efficient
            else -> 0
        }

        // Hint penalty
        val hintPenalty = if (hintUsed) {
            (basePoints * 0.15f).toInt()
        } else {
            0
        }

        // Mathematical reasoning bonus
        val reasoningBonus = (basePoints * 0.15f).toInt()

        val finalScore = ((basePoints * complexityMultiplier).toInt() + masteryBonus + speedBonus + attemptBonus + efficiencyBonus + reasoningBonus - hintPenalty)

        Log.d(TAG, "🏆 Conversion score calculation:")
        Log.d(TAG, "  Conversion type: ${conversionType.displayName}")
        Log.d(TAG, "  Base points: $basePoints")
        Log.d(TAG, "  Complexity multiplier: ${complexityMultiplier}x")
        Log.d(TAG, "  Mastery bonus: $masteryBonus")
        Log.d(TAG, "  Speed bonus: $speedBonus (${timeSeconds}s)")
        Log.d(TAG, "  Attempt bonus: $attemptBonus (attempt #$attemptNumber)")
        Log.d(TAG, "  Efficiency bonus: $efficiencyBonus ($dragInteractions drags)")
        Log.d(TAG, "  Reasoning bonus: $reasoningBonus")
        Log.d(TAG, "  Hint penalty: $hintPenalty")
        Log.d(TAG, "  Final score: $finalScore")

        return maxOf(finalScore, basePoints / 4) // Minimum 25% of base points
    }

    // Calculate current user answer based on block positions
    fun getCurrentAnswer(): ComparisonResult {
        return when {
            leftBlockState.offsetY < rightBlockState.offsetY - positionThreshold -> ComparisonResult.LEFT_HEAVIER
            rightBlockState.offsetY < leftBlockState.offsetY - positionThreshold -> ComparisonResult.RIGHT_HEAVIER
            else -> ComparisonResult.EQUAL
        }
    }

    // Reset state function
    fun resetState() {
        leftBlockState = leftBlock.copy()
        rightBlockState = rightBlock.copy()
        showContinueButton = false
        hasSubmitted = false
        dragCount = 0
        totalScore = 0
    }

    // Check if blocks have been moved enough to show continue button
    LaunchedEffect(leftBlockState.offsetY, rightBlockState.offsetY) {
        val totalMovement = abs(leftBlockState.offsetY) + abs(rightBlockState.offsetY)
        showContinueButton = totalMovement > positionThreshold && !hasSubmitted
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF1A1A2E),
                        Color(0xFF16213E),
                        Color(0xFF0F3460)
                    )
                )
            )
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // Enhanced Top Bar with score display
            EnhancedConversionTopGameBar(
                level = currentLevel,
                streakInfo = streakInfo,
                timer = displayTimer, // Use live countdown
                hearts = hearts,
                roundLevel = level,
                totalScore = totalScore,
                attempts = attempts,
                conversionType = conversionType,
                onBack = onBack,
                onHintClick = {
                    showHint = !showHint
                    hintUsed = true
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                },
                modifier = Modifier.padding(top = 40.dp, start = 16.dp, end = 16.dp, bottom = 8.dp)
            )

            Spacer(modifier = Modifier.height(20.dp))

            // Title
            Text(
                text = "COMPARE ${conversionType.displayName.uppercase()}",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Enhanced instructions with scoring hints
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Drag the blocks to show which ${conversionType.displayName.lowercase()} is greater",
                    fontSize = 14.sp,
                    color = Color.White.copy(alpha = 0.8f),
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
                    conversionType = conversionType,
                    leftBlock = leftBlock,
                    rightBlock = rightBlock,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 16.dp)
                )
            }

            Spacer(modifier = Modifier.height(30.dp))

            // Universal scale visual representation
            UniversalScale(
                conversionType = conversionType,
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
                        val isCorrect = userAnswer == correctAnswer
                        val timeSpent = System.currentTimeMillis() - gameStartTime

                        if (isCorrect) {
                            // Calculate and award score
                            val score = calculateConversionScore(
                                isCorrect = true,
                                timeSpent = timeSpent,
                                attemptNumber = attempts,
                                conversionType = conversionType,
                                difficulty = difficulty,
                                hintUsed = hintUsed,
                                dragInteractions = dragCount
                            )
                            totalScore = score

                            Log.d(TAG, "✅ Correct comparison! Time: ${timeSpent}ms, Score: $totalScore")
                        } else {
                            Log.d(TAG, "❌ Incorrect comparison.")
                        }

                        feedbackManager.showFeedback(
                            puzzleType = "unitConversion",
                            isCorrect = isCorrect,
                            userAnswer = when (userAnswer) {
                                ComparisonResult.LEFT_HEAVIER -> "${leftBlock.label} is ${getComparisonWord(conversionType, true)}"
                                ComparisonResult.RIGHT_HEAVIER -> "${rightBlock.label} is ${getComparisonWord(conversionType, true)}"
                                ComparisonResult.EQUAL -> "Both ${conversionType.displayName.lowercase()}s are equal"
                            },
                            correctAnswer = when (correctAnswer) {
                                ComparisonResult.LEFT_HEAVIER -> "${leftBlock.label} is ${getComparisonWord(conversionType, true)}"
                                ComparisonResult.RIGHT_HEAVIER -> "${rightBlock.label} is ${getComparisonWord(conversionType, true)}"
                                ComparisonResult.EQUAL -> "Both ${conversionType.displayName.lowercase()}s are equal"
                            },
                            timeSpent = timeSpent,
                            difficulty = difficulty,
                            timeRemaining = timeRemaining,
                            totalTime = totalTimeSeconds,
                            onComplete = {
                                onSubmitAnswer(userAnswer)
                                if (isCorrect) {
                                    Log.d(TAG, "🎯 Calling fetchNextPuzzle with score: $totalScore")
                                    fetchNextPuzzle(totalScore)
                                } else {
                                    resetState()
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
}

// Enhanced top bar with score display
@Composable
fun EnhancedConversionTopGameBar(
    level: UserLevel,
    streakInfo: StreakInfo,
    timer: String,
    hearts: Int,
    roundLevel: String,
    totalScore: Int,
    attempts: Int,
    conversionType: ConversionType,
    onBack: (() -> Unit)?,
    onHintClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.statusBarsPadding()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            // Left side: Back button and level progression
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Back button
                IconButton(
                    onClick = { onBack?.invoke() },
                    modifier = Modifier.size(44.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ArrowBack,
                        contentDescription = stringResource(R.string.back),
                        tint = Color.White,
                        modifier = Modifier.size(28.dp)
                    )
                }

                // Hint button
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
                        text = "${stringResource(R.string.level_label)} ${level.level}",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )

                    // Level progress bar
                    LevelProgressBar(
                        level = level,
                        modifier = Modifier.width(100.dp)
                    )
                }
            }

            // Center: Timer with urgency indicator
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                val timeValue = timer.substringAfter(":").toIntOrNull() ?: 0
                val isUrgent = timer.startsWith("0:") && timeValue <= 30

                Text(
                    text = timer,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isUrgent) Color.Red else Color.White
                )

                // Streak display
                if (streakInfo.currentStreak > 0) {
                    StreakDisplay(
                        streakInfo = streakInfo,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }

            // Right side: Hearts and round level
            Column(
                horizontalAlignment = Alignment.End
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    repeat(hearts) {
                        Icon(
                            imageVector = Icons.Default.Favorite,
                            contentDescription = "Heart",
                            tint = Color(0xFFFF69B4),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
                Text(
                    text = roundLevel,
                    fontSize = 16.sp,
                    color = Color.White,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }

        // Score and progress display
        if (totalScore > 0 || attempts > 0) {
            Spacer(modifier = Modifier.height(8.dp))

            Card(
                colors = CardDefaults.cardColors(
                    containerColor = Color.White.copy(alpha = 0.1f)
                ),
                shape = RoundedCornerShape(8.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (totalScore > 0) {
                        Text(
                            text = "${stringResource(R.string.score_label)}: $totalScore",
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Text(
                        text = "${conversionType.icon} ${conversionType.displayName}",
                        color = Color.White.copy(alpha = 0.8f),
                        fontSize = 12.sp
                    )

                    if (attempts > 0) {
                        Text(
                            text = "Attempts: $attempts",
                            color = Color.White.copy(alpha = 0.8f),
                            fontSize = 12.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun UniversalScale(
    conversionType: ConversionType,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Higher/Greater indicator
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = when (conversionType) {
                            ConversionType.TEMPERATURE -> "🔥"
                            ConversionType.WEIGHT -> "⬆️"
                            ConversionType.DISTANCE -> "📏"
                            ConversionType.VOLUME -> "🥤"
                            ConversionType.TIME -> "⏰"
                            ConversionType.AREA -> "📐"
                            ConversionType.SPEED -> "🏃"
                            ConversionType.UNKNOWN -> "⬆️"
                        },
                        fontSize = 24.sp
                    )
                    Text(
                        text = when (conversionType) {
                            ConversionType.TEMPERATURE -> "HOTTER"
                            else -> "GREATER"
                        },
                        fontSize = 12.sp,
                        color = Color.White.copy(alpha = 0.7f)
                    )
                }

                // Center scale/indicator
                Box(
                    modifier = Modifier
                        .height(40.dp)
                        .width(8.dp)
                        .background(
                            when (conversionType) {
                                ConversionType.TEMPERATURE -> Brush.verticalGradient(
                                    colors = listOf(Color.Red, Color.Yellow, Color.Blue)
                                )
                                ConversionType.WEIGHT -> Brush.verticalGradient(
                                    colors = listOf(Color.Gray, Color.LightGray, Color.White)
                                )
                                else -> Brush.verticalGradient(
                                    colors = listOf(Color.Green, Color.Yellow, Color.Red)
                                )
                            },
                            RoundedCornerShape(4.dp)
                        )
                ) {
                    // Scale icon overlay
                    Icon(
                        imageVector = when (conversionType) {
                            ConversionType.WEIGHT -> Icons.Default.Add // Scale symbol
                            else -> Icons.Default.Add
                        },
                        contentDescription = "Scale",
                        tint = Color.White.copy(alpha = 0.5f),
                        modifier = Modifier
                            .align(Alignment.Center)
                            .size(16.dp)
                    )
                }

                // Lower/Smaller indicator
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = when (conversionType) {
                            ConversionType.TEMPERATURE -> "❄️"
                            ConversionType.WEIGHT -> "⬇️"
                            ConversionType.DISTANCE -> "📏"
                            ConversionType.VOLUME -> "🥃"
                            ConversionType.TIME -> "⏱️"
                            ConversionType.AREA -> "📐"
                            ConversionType.SPEED -> "🚶"
                            ConversionType.UNKNOWN -> "⬇️"
                        },
                        fontSize = 24.sp
                    )
                    Text(
                        text = when (conversionType) {
                            ConversionType.TEMPERATURE -> "COLDER"
                            else -> "SMALLER"
                        },
                        fontSize = 12.sp,
                        color = Color.White.copy(alpha = 0.7f)
                    )
                }
            }
        }
    }
}

@Composable
fun DraggableConversionBlock(
    block: ConversionBlock,
    onDrag: (Offset) -> Unit,
    onDragEnd: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .width(120.dp)
            .height(70.dp)
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragEnd = { onDragEnd() }
                ) { change, dragAmount ->
                    onDrag(dragAmount)
                }
            }
            .shadow(
                elevation = if (block.isDragging) 12.dp else 6.dp,
                shape = RoundedCornerShape(12.dp)
            ),
        colors = CardDefaults.cardColors(
            containerColor = block.color
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = block.label,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                textAlign = TextAlign.Center
            )
        }
    }
}

// Helper function for comparison words
fun getComparisonWord(conversionType: ConversionType, isGreater: Boolean): String {
    return when (conversionType) {
        ConversionType.TEMPERATURE -> if (isGreater) "hotter" else "colder"
        ConversionType.WEIGHT -> if (isGreater) "heavier" else "lighter"
        ConversionType.DISTANCE -> if (isGreater) "longer" else "shorter"
        ConversionType.VOLUME -> if (isGreater) "more" else "less"
        ConversionType.TIME -> if (isGreater) "longer" else "shorter"
        ConversionType.AREA -> if (isGreater) "larger" else "smaller"
        ConversionType.SPEED -> if (isGreater) "faster" else "slower"
        ConversionType.UNKNOWN -> if (isGreater) "greater" else "smaller"
    }
}

@Composable
fun ContinueButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Button(
        onClick = onClick,
        modifier = modifier.height(56.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = Color(0xFF4CAF50)
        ),
        shape = RoundedCornerShape(28.dp)
    ) {
        Text(
            text = stringResource(R.string.continue_label_caps),
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )
    }
}

@Composable
fun ConversionHintBubble(
    conversionType: ConversionType,
    leftBlock: ConversionBlock,
    rightBlock: ConversionBlock,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = Color.White.copy(alpha = 0.95f)
        ),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = conversionType.icon,
                    fontSize = 16.sp
                )
                Text(
                    text = "${conversionType.displayName} Conversions",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF333333)
                )
                Text(
                    text = "⚠️ Using hints reduces score",
                    fontSize = 10.sp,
                    color = Color.Gray
                )
            }

            // Show specific conversions based on the puzzle
            when (conversionType) {
                ConversionType.TEMPERATURE -> {
                    ConversionHintRow("32°F", "0°C", "Water freezes")
                    ConversionHintRow("212°F", "100°C", "Water boils")
                    ConversionHintRow("98.6°F", "37°C", "Body temp")
                }
                ConversionType.WEIGHT -> {
                    ConversionHintRow("1 lb", "453.6 g", "")
                    ConversionHintRow("1 kg", "2.2 lbs", "")
                    ConversionHintRow("1 oz", "28.3 g", "")
                }
                ConversionType.DISTANCE -> {
                    ConversionHintRow("1 mile", "1.6 km", "")
                    ConversionHintRow("1 foot", "30.5 cm", "")
                    ConversionHintRow("1 inch", "2.54 cm", "")
                }
                ConversionType.VOLUME -> {
                    ConversionHintRow("1 gallon", "3.8 L", "")
                    ConversionHintRow("1 quart", "0.95 L", "")
                    ConversionHintRow("1 cup", "237 mL", "")
                }
                ConversionType.TIME -> {
                    ConversionHintRow("1 hour", "60 min", "")
                    ConversionHintRow("1 day", "24 hours", "")
                    ConversionHintRow("1 week", "7 days", "")
                }
                ConversionType.AREA -> {
                    ConversionHintRow("1 acre", "4047 m²", "")
                    ConversionHintRow("1 sq mile", "2.59 km²", "")
                    ConversionHintRow("1 sq ft", "0.09 m²", "")
                }
                ConversionType.SPEED -> {
                    ConversionHintRow("60 mph", "96 km/h", "")
                    ConversionHintRow("1 m/s", "3.6 km/h", "")
                    ConversionHintRow("1 mph", "1.6 km/h", "")
                }
                ConversionType.UNKNOWN -> {
                    Text(
                        text = "Compare the values to determine which is greater",
                        fontSize = 12.sp,
                        color = Color(0xFF666666)
                    )
                }
            }
        }
    }
}

@Composable
fun ConversionHintRow(
    left: String,
    right: String,
    note: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = left,
            fontSize = 12.sp,
            color = Color(0xFF666666)
        )
        Text(
            text = "=",
            fontSize = 12.sp,
            color = Color(0xFF999999)
        )
        Text(
            text = right,
            fontSize = 12.sp,
            color = Color(0xFF666666)
        )
        if (note.isNotEmpty()) {
            Text(
                text = note,
                fontSize = 10.sp,
                color = Color(0xFF999999),
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.End
            )
        }
    }
}