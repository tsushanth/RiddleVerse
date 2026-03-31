// NumberSequencePuzzleScreen.kt
package com.kreativekoala.riddleverse

// Add this to your PuzzleScreenType enum:
// NUMBER_SEQUENCE_SCREEN("Number Sequence Screen"),

import android.util.Log
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
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
    var feedbackColor by remember { mutableStateOf(Color.Red) }

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
            Color(0xFF2196F3), // Blue
            Color(0xFF4CAF50), // Green
            Color(0xFFE91E63), // Pink
            Color(0xFFFF9800), // Orange
            Color(0xFF9C27B0), // Purple
            Color(0xFF00BCD4), // Cyan
            Color(0xFFFF5722), // Deep Orange
            Color(0xFF795548), // Brown
            Color(0xFF607D8B), // Blue Grey
            Color(0xFF8BC34A), // Light Green
            Color(0xFFFF6B35), // Red Orange
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
            feedbackColor = Color(0xFF4CAF50)
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
            feedbackColor = Color.Red
            showFeedback = true
            delay(2000)
            onGameComplete(false, 10)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF1B5E20))
            .statusBarsPadding()
    ) {
        // Header
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = {
                        gameCompleted = true
                        onBack()
                    }
                ) {
                    Icon(Icons.Default.ArrowBack, contentDescription = stringResource(R.string.back))
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "${config.name} • Round $currentRound",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.Gray
                    )
                    Text(
                        text = String.format("%02d:%02d", timeLeft / 60, timeLeft % 60),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (timeLeft <= 30) Color.Red else Color.Black
                    )
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "Score: $totalScore",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        repeat(currentHearts) {
                            Text("❤️", fontSize = 16.sp)
                        }
                    }
                }

                IconButton(onClick = { isPaused = !isPaused }) {
                    Icon(Icons.Default.Pause, contentDescription = "Pause")
                }
            }
        }

        // Instructions
        if (!gameStarted) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "🔢 Number Sequence",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.tap_ascending_order),
                        fontSize = 16.sp,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Range: ${config.numberRange.first}-${config.numberRange.last} • ${config.numberCount} numbers per round",
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center,
                        color = Color.Gray
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = { gameStarted = true },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50))
                    ) {
                        Text(stringResource(R.string.start).uppercase(), fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // Game Area
        if (gameStarted && !gameCompleted) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(16.dp)
            ) {
                // Game background
                Canvas(
                    modifier = Modifier.fillMaxSize()
                ) {
                    // Draw subtle grid pattern
                    val gridSize = 40.dp.toPx()
                    val gridColor = Color.White.copy(alpha = 0.1f)

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

                // Numbers
                BoxWithConstraints {
                    val screenWidth = maxWidth
                    val screenHeight = maxHeight

                    numberPositions.forEach { position ->
                        val isVisible = numbersVisible[position.number] ?: false

                        if (isVisible) {
                            val xPos = (screenWidth * position.x)
                            val yPos = (screenHeight * position.y)

                            Box(
                                modifier = Modifier
                                    .offset(x = xPos, y = yPos)
                                    .size(60.dp)
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
                                Text(
                                    text = position.number.toString(),
                                    color = Color.White,
                                    fontSize = 24.sp,
                                    fontWeight = FontWeight.Bold
                                )
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
                                tint = Color.Red,
                                modifier = Modifier.size(40.dp)
                            )
                        }
                    }
                }
            }
        }

        // Feedback overlay
        if (showFeedback) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                colors = CardDefaults.cardColors(containerColor = feedbackColor.copy(alpha = 0.9f))
            ) {
                Text(
                    text = feedbackMessage,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}