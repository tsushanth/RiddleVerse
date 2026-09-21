// NumberSequencePuzzleScreen.kt
package com.kreativekoala.riddleverse

// Add this to your PuzzleScreenType enum:
// NUMBER_SEQUENCE_SCREEN("Number Sequence Screen"),

import com.kreativekoala.riddleverse.ui.theme.*
import android.util.Log
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlin.math.*
import kotlin.random.Random

data class NumberPosition(
    val number: Int,
    val x: Float,
    val y: Float,
    val color: Color,
    val isVisible: Boolean = true
)

data class DifficultyConfig(
    val numberCount: Int,
    val numberRange: IntRange,
    val timePerNumber: Int, // seconds
    val name: String
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NumberSequencePuzzleScreen(
    difficulty: String = "Medium",
    timer: String = "2:00",
    hearts: Int = 3,
    level: String = "1/5",
    numberCount: Int = 8, // This will be overridden by difficulty config
    onGameComplete: (Boolean, Int) -> Unit,
    onBack: () -> Unit
) {
    // Difficulty configurations
    val difficultyConfigs = mapOf(
        "easy" to DifficultyConfig(5, 1..20, 15, "Easy"),
        "medium" to DifficultyConfig(7, 1..50, 12, "Medium"),
        "hard" to DifficultyConfig(9, 1..100, 10, "Hard"),
        "expert" to DifficultyConfig(12, 1..200, 8, "Expert")
    )

    val config = difficultyConfigs[difficulty.lowercase()] ?: difficultyConfigs["medium"]!!
    val totalTimeInSeconds = config.numberCount * config.timePerNumber

    var timeLeft by remember { mutableStateOf(totalTimeInSeconds) }
    var isPaused by remember { mutableStateOf(false) }
    var currentHearts by remember { mutableStateOf(hearts) }
    var gameCompleted by remember { mutableStateOf(false) }
    var gameStarted by remember { mutableStateOf(false) }
    var showFeedback by remember { mutableStateOf(false) }
    var feedbackMessage by remember { mutableStateOf("") }
    var feedbackColor by remember { mutableStateOf(RvError) }

    // Game state
    var currentRoundNumbers by remember { mutableStateOf(emptyList<Int>()) }
    var sortedTargetNumbers by remember { mutableStateOf(emptyList<Int>()) }
    var nextExpectedIndex by remember { mutableStateOf(0) }
    var numbersVisible by remember { mutableStateOf(mutableMapOf<Int, Boolean>()) }
    var numberPositions by remember { mutableStateOf(emptyList<NumberPosition>()) }
    var showWrongFeedback by remember { mutableStateOf(false) }
    var wrongFeedbackPosition by remember { mutableStateOf(Offset.Zero) }
    var currentRound by remember { mutableStateOf(1) }
    var totalScore by remember { mutableStateOf(0) }

    // Generate new round of random numbers
    fun generateNewRound() {
        val randomNumbers = (1..config.numberCount).map {
            Random.nextInt(config.numberRange.first, config.numberRange.last + 1)
        }.distinct().take(config.numberCount)

        // If we don't have enough unique numbers, fill with more
        val finalNumbers = if (randomNumbers.size < config.numberCount) {
            val additional = generateSequence {
                Random.nextInt(config.numberRange.first, config.numberRange.last + 1)
            }.filter { it !in randomNumbers }
                .take(config.numberCount - randomNumbers.size)
                .toList()
            randomNumbers + additional
        } else {
            randomNumbers
        }

        currentRoundNumbers = finalNumbers
        sortedTargetNumbers = finalNumbers.sorted()
        nextExpectedIndex = 0
        numbersVisible = finalNumbers.associateWith { true }.toMutableMap()

        Log.d("NumberSequence", "New round $currentRound:")
        Log.d("NumberSequence", "Numbers: $finalNumbers")
        Log.d("NumberSequence", "Target order: $sortedTargetNumbers")
    }

    // Generate positions for current round numbers
    fun generatePositions() {
        val colors = listOf(
            RvSky, // Blue
            RvSuccess, // Green
            Color(0xFFE91E63), // Pink
            RvSun, // Orange
            RvGrape, // Purple
            RvSky, // Cyan
            RvFlame, // Deep Orange
            Color(0xFF795548), // Brown
            RvInkSoft, // Blue Grey
            Color(0xFF8BC34A), // Light Green
            RvFlame, // Red Orange
            Color(0xFF6C5CE7)  // Purple Blue
        )

        val positions = mutableListOf<NumberPosition>()
        val usedPositions = mutableSetOf<Pair<Float, Float>>()

        currentRoundNumbers.forEachIndexed { index, number ->
            var x: Float
            var y: Float
            var attempts = 0

            // Find a position that doesn't overlap with existing numbers
            do {
                x = Random.nextFloat() * 0.7f + 0.15f // Keep within 15% to 85% of screen
                y = Random.nextFloat() * 0.6f + 0.2f  // Keep within 20% to 80% of screen
                attempts++
            } while (usedPositions.any {
                    abs(it.first - x) < 0.15f && abs(it.second - y) < 0.15f
                } && attempts < 50)

            usedPositions.add(Pair(x, y))

            positions.add(
                NumberPosition(
                    number = number,
                    x = x,
                    y = y,
                    color = colors[index % colors.size],
                    isVisible = true
                )
            )
        }

        numberPositions = positions
    }

    // Initialize first round
    LaunchedEffect(Unit) {
        generateNewRound()
        generatePositions()
    }

    // Timer countdown
    LaunchedEffect(gameStarted, isPaused, gameCompleted) {
        if (gameStarted && !isPaused && !gameCompleted) {
            while (timeLeft > 0) {
                delay(1000)
                timeLeft--
            }
            if (timeLeft == 0) {
                // Time's up
                gameCompleted = true
                onGameComplete(false, 10)
            }
        }
    }

    // Wrong feedback animation
    LaunchedEffect(showWrongFeedback) {
        if (showWrongFeedback) {
            delay(800)
            showWrongFeedback = false
        }
    }

    // Check if current round is completed
    LaunchedEffect(numbersVisible.values.toList(), currentRoundNumbers) {
        if (currentRoundNumbers.isNotEmpty() &&
            numbersVisible.isNotEmpty() &&
            numbersVisible.values.none { it }) {

            // Round completed successfully
            totalScore += (config.numberCount * 10) + (timeLeft * 2) // Bonus for time remaining

            feedbackMessage = "🎉 Round $currentRound Complete! +${(config.numberCount * 10) + (timeLeft * 2)} points"
            feedbackColor = RvSuccess
            showFeedback = true

            delay(1500)
            showFeedback = false

            // Start next round
            currentRound++
            generateNewRound()
            generatePositions()

            // Add some bonus time for next round
            timeLeft += (config.timePerNumber * 3) // 3 numbers worth of extra time
        }
    }

    // Handle game over when hearts reach 0
    LaunchedEffect(currentHearts) {
        if (currentHearts == 0 && gameStarted && !gameCompleted) {
            gameCompleted = true
            feedbackMessage = "💔 Game Over! Final Score: $totalScore"
            feedbackColor = RvError
            showFeedback = true
            delay(2000)
            onGameComplete(false, 10)
        }
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(RvCanvas)
    ) {
        val compact = groupEIsCompact(maxWidth, maxHeight)
        val timerText = String.format("%02d:%02d", timeLeft / 60, timeLeft % 60)

        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .widthIn(max = 720.dp)
                .fillMaxSize()
                .statusBarsPadding()
                .padding(horizontal = 16.dp)
                .padding(bottom = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // HUD: one compact row (timer, score, lives, back, pause).
            GroupECompactHud(
                timer = timerText,
                onBack = {
                    gameCompleted = true
                    onBack()
                },
                subtitle = "${config.name} \u2022 Round $currentRound \u2022 ${stringResource(R.string.score_label)} $totalScore",
                lives = currentHearts,
                urgent = timeLeft <= 30,
                onPause = { isPaused = !isPaused }
            )

            Spacer(modifier = Modifier.height(8.dp))

            if (!gameStarted) {
                // Instructions: may scroll as a last-resort safety net; Start is pinned below.
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState()),
                        colors = CardDefaults.cardColors(containerColor = RvSurface),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "\uD83D\uDD22 Number Sequence",
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                                color = RvInk,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = stringResource(R.string.tap_ascending_order),
                                fontSize = 16.sp,
                                color = RvInk,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Range: ${config.numberRange.first}-${config.numberRange.last} \u2022 ${config.numberCount} numbers per round",
                                fontSize = 14.sp,
                                textAlign = TextAlign.Center,
                                color = RvInkSoft
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                GroupEPrimaryButton(
                    text = stringResource(R.string.start).uppercase(),
                    onClick = { gameStarted = true },
                    modifier = Modifier.fillMaxWidth()
                )
            } else if (!gameCompleted) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .testTag("seq_area")
                        .clip(RoundedCornerShape(16.dp))
                        .background(RvSurface)
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val gridSize = 40.dp.toPx()
                        val gridColor = RvOutline

                        for (x in 0 until (size.width / gridSize).toInt()) {
                            drawLine(
                                color = gridColor,
                                start = Offset(x * gridSize, 0f),
                                end = Offset(x * gridSize, size.height),
                                strokeWidth = 1.dp.toPx()
                            )
                        }

                        for (y in 0 until (size.height / gridSize).toInt()) {
                            drawLine(
                                color = gridColor,
                                start = Offset(0f, y * gridSize),
                                end = Offset(size.width, y * gridSize),
                                strokeWidth = 1.dp.toPx()
                            )
                        }
                    }

                    BoxWithConstraints(
                        modifier = Modifier
                            .fillMaxSize()
                            .alpha(if (isPaused) 0.35f else 1f)
                    ) {
                        val fieldW = maxWidth
                        val fieldH = maxHeight
                        val pad = 8.dp
                        val numberSize = minOf(60.dp, minOf(fieldW, fieldH) * 0.2f).coerceAtLeast(48.dp)

                        numberPositions.forEach { position ->
                            val isVisible = numbersVisible[position.number] ?: false

                            if (isVisible) {
                                // Map the normalised slot (0.15..0.85, 0.2..0.8) onto the field minus the circle size.
                                val fx = ((position.x - 0.15f) / 0.7f).coerceIn(0f, 1f)
                                val fy = ((position.y - 0.2f) / 0.6f).coerceIn(0f, 1f)
                                val xPos = pad + (fieldW - numberSize - pad * 2) * fx
                                val yPos = pad + (fieldH - numberSize - pad * 2) * fy

                                Box(
                                    modifier = Modifier
                                        .offset(x = xPos, y = yPos)
                                        .size(numberSize)
                                        .testTag("seq_number")
                                        .clip(CircleShape)
                                        .background(position.color)
                                        .clickable {
                                            if (!isPaused) {
                                                val expectedNumber = sortedTargetNumbers.getOrNull(nextExpectedIndex)

                                                if (position.number == expectedNumber) {
                                                    // Correct number tapped
                                                    numbersVisible = numbersVisible.toMutableMap().apply {
                                                        this[position.number] = false
                                                    }
                                                    nextExpectedIndex++
                                                } else {
                                                    // Wrong number tapped
                                                    wrongFeedbackPosition = Offset(xPos.value, yPos.value)
                                                    showWrongFeedback = true
                                                    currentHearts = maxOf(0, currentHearts - 1)
                                                }
                                            }
                                        }
                                        .animateContentSize(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    // Fixed-size target: number text does not follow the system font scale.
                                    GroupEFontCap(max = 1f) {
                                        Text(
                                            text = position.number.toString(),
                                            color = groupEOn(position.color),
                                            fontSize = (numberSize.value * 0.42f).coerceAtLeast(20f).sp,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1
                                        )
                                    }
                                }
                            }
                        }

                        // Wrong feedback
                        if (showWrongFeedback) {
                            val scale by animateFloatAsState(
                                targetValue = if (showWrongFeedback) 1.5f else 1f,
                                animationSpec = tween(300)
                            )

                            Box(
                                modifier = Modifier
                                    .offset(
                                        x = wrongFeedbackPosition.x.dp,
                                        y = wrongFeedbackPosition.y.dp
                                    )
                                    .scale(scale)
                            ) {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = stringResource(R.string.wrong),
                                    tint = RvError,
                                    modifier = Modifier.size(40.dp)
                                )
                            }
                        }
                    }

                    // Feedback banner overlays the field instead of pushing it around.
                    if (showFeedback) {
                        Card(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .fillMaxWidth()
                                .padding(8.dp),
                            colors = CardDefaults.cardColors(containerColor = RvSurfaceRaised),
                            border = androidx.compose.foundation.BorderStroke(2.dp, feedbackColor)
                        ) {
                            Text(
                                text = feedbackMessage,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                color = RvInk,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            } else if (showFeedback) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    colors = CardDefaults.cardColors(containerColor = RvSurfaceRaised),
                    border = androidx.compose.foundation.BorderStroke(2.dp, feedbackColor)
                ) {
                    Text(
                        text = feedbackMessage,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        color = RvInk,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}
