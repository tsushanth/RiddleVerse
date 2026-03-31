// SymbolSwipePuzzleScreen.kt
package com.kreativekoala.riddleverse

import android.util.Log
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import androidx.compose.ui.res.stringResource
import kotlin.math.*
import kotlin.random.Random

// Symbol definitions
sealed class GameSymbol(val color: Color, val name: String) {
    object Star : GameSymbol(Color(0xFF4CAF50), "Star")
    object Circle : GameSymbol(Color(0xFF2196F3), "Circle")
    object Triangle : GameSymbol(Color(0xFFFF9800), "Triangle")
    object Diamond : GameSymbol(Color(0xFFE91E63), "Diamond")
    object Square : GameSymbol(Color(0xFF9C27B0), "Square")
    object Heart : GameSymbol(Color(0xFFFF5722), "Heart")
    object Hexagon : GameSymbol(Color(0xFF00BCD4), "Hexagon")
    object Cross : GameSymbol(Color(0xFF795548), "Cross")
}

data class SymbolItem(
    val symbol: GameSymbol,
    val id: Int = Random.nextInt()
)

enum class SwipeDirection {
    LEFT, RIGHT
}

data class DifficultySettings(
    val symbolCount: Int,
    val timePerSymbol: Float, // seconds
    val name: String
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SymbolSwipePuzzleScreen(
    difficulty: String = "Medium",
    timer: String = "2:00",
    hearts: Int = 3,
    level: String = "1/5",
    onGameComplete: (Boolean, Int) -> Unit, // Added score parameter
    onBack: () -> Unit,
    isFirstPuzzle: Boolean = true // New parameter to control showing instructions
) {
    // Difficulty configurations
    val difficultyConfigs = mapOf(
        "easy" to DifficultySettings(15, 3.0f, "Easy"),
        "medium" to DifficultySettings(20, 2.5f, "Medium"),
        "hard" to DifficultySettings(25, 2.0f, "Hard"),
        "expert" to DifficultySettings(30, 1.5f, "Expert")
    )

    val config = difficultyConfigs[difficulty.lowercase()] ?: difficultyConfigs["medium"]!!
    val totalTimeInSeconds = (config.symbolCount * config.timePerSymbol).toInt()

    // Game state
    var timeLeft by remember { mutableStateOf(totalTimeInSeconds) }
    var isPaused by remember { mutableStateOf(false) }
    var currentHearts by remember { mutableStateOf(hearts) }
    var gameCompleted by remember { mutableStateOf(false) }
    var gameStarted by remember { mutableStateOf(!isFirstPuzzle) } // Auto-start if not first puzzle
    var showFeedback by remember { mutableStateOf(false) }
    var feedbackMessage by remember { mutableStateOf("") }
    var feedbackColor by remember { mutableStateOf(Color.Green) }

    // Symbol configuration - only 2 symbols used
    var leftSymbol by remember { mutableStateOf<GameSymbol?>(null) }
    var rightSymbol by remember { mutableStateOf<GameSymbol?>(null) }
    var symbolSequence by remember { mutableStateOf(emptyList<SymbolItem>()) }
    var currentSymbolIndex by remember { mutableStateOf(0) }
    var score by remember { mutableStateOf(0) }
    var multiplier by remember { mutableStateOf(1) }
    var consecutiveCorrect by remember { mutableStateOf(0) }

    // Animation states
    var currentSymbolOffset by remember { mutableStateOf(0f) }
    var symbolScale by remember { mutableStateOf(1f) }

    // Parse level to get current puzzle number
    val currentPuzzleNumber = remember(level) {
        try {
            level.split("/")[0].toInt()
        } catch (e: Exception) {
            1
        }
    }

    val totalPuzzles = remember(level) {
        try {
            level.split("/")[1].toInt()
        } catch (e: Exception) {
            5
        }
    }

    // Generate game symbols and sequence
    fun generateGameSymbols() {
        val allSymbols = listOf(
            GameSymbol.Star, GameSymbol.Circle, GameSymbol.Triangle, GameSymbol.Diamond,
            GameSymbol.Square, GameSymbol.Heart, GameSymbol.Hexagon, GameSymbol.Cross
        )

        // Pick 2 random symbols for this game session
        val selectedSymbols = allSymbols.shuffled().take(2)
        leftSymbol = selectedSymbols[0]
        rightSymbol = selectedSymbols[1]

        // Generate sequence using only these 2 symbols
        val sequence = (1..config.symbolCount).map { index ->
            SymbolItem(
                symbol = if (Random.nextBoolean()) leftSymbol!! else rightSymbol!!,
                id = index
            )
        }

        symbolSequence = sequence
        currentSymbolIndex = 0

        Log.d("SymbolSwipe", "Generated game with symbols:")
        Log.d("SymbolSwipe", "Left: ${leftSymbol?.name}, Right: ${rightSymbol?.name}")
        Log.d("SymbolSwipe", "Sequence length: ${sequence.size}")
    }

    // Initialize game
    LaunchedEffect(Unit) {
        generateGameSymbols()
    }

    // Timer countdown
    LaunchedEffect(gameStarted, isPaused, gameCompleted) {
        if (gameStarted && !isPaused && !gameCompleted) {
            while (timeLeft > 0 && currentSymbolIndex < symbolSequence.size) {
                delay(1000)
                timeLeft--
            }
            if (timeLeft == 0 || currentSymbolIndex >= symbolSequence.size) {
                gameCompleted = true
                val success = currentSymbolIndex >= symbolSequence.size
                onGameComplete(success, score)
            }
        }
    }

    // Get required swipe direction for current symbol
    fun getRequiredDirection(symbol: GameSymbol): SwipeDirection {
        return when (symbol) {
            leftSymbol -> SwipeDirection.LEFT
            rightSymbol -> SwipeDirection.RIGHT
            else -> SwipeDirection.LEFT // fallback
        }
    }

    // Handle swipe
    fun handleSwipe(direction: SwipeDirection) {
        if (gameCompleted || !gameStarted || currentSymbolIndex >= symbolSequence.size) return

        val currentSymbol = symbolSequence[currentSymbolIndex].symbol
        val requiredDirection = getRequiredDirection(currentSymbol)
        val isCorrect = direction == requiredDirection

        if (isCorrect) {
            consecutiveCorrect++
            multiplier = when {
                consecutiveCorrect >= 10 -> 4
                consecutiveCorrect >= 5 -> 3
                consecutiveCorrect >= 3 -> 2
                else -> 1
            }
            val points = 10 * multiplier
            score += points

            feedbackMessage = if (multiplier > 1) "Perfect! +$points (x${multiplier})" else "Correct! +$points"
            feedbackColor = Color(0xFF4CAF50)
        } else {
            consecutiveCorrect = 0
            multiplier = 1
            currentHearts = maxOf(0, currentHearts - 1)

            feedbackMessage = "Wrong direction! Score: $score"
            feedbackColor = Color(0xFFE91E63)

            if (currentHearts == 0) {
                gameCompleted = true
                onGameComplete(false, score)
                return
            }
        }

        showFeedback = true
        currentSymbolIndex++

        // Check if game completed
        if (currentSymbolIndex >= symbolSequence.size) {
            gameCompleted = true
            onGameComplete(true, score)
        }
    }

    // Feedback auto-hide
    LaunchedEffect(showFeedback) {
        if (showFeedback) {
            delay(800)
            showFeedback = false
        }
    }

    // Scale animation for symbol
    val animatedScale by animateFloatAsState(
        targetValue = symbolScale,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy)
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF6A1B9A)) // Purple gradient background
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
                IconButton(onClick = onBack) {
                    Icon(Icons.Default.ArrowBack, contentDescription = stringResource(R.string.back))
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "${config.name} • Puzzle $currentPuzzleNumber/$totalPuzzles",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.Gray
                    )
                    Text(
                        text = "${currentSymbolIndex}/${symbolSequence.size} symbols",
                        fontSize = 12.sp,
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
                        text = stringResource(R.string.score_label),
                        fontSize = 12.sp,
                        color = Color.Gray
                    )
                    Text(
                        text = "$score",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                    if (multiplier > 1) {
                        Text(
                            text = "x$multiplier",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFFF9800)
                        )
                    }
                }

                IconButton(onClick = { isPaused = !isPaused }) {
                    Icon(Icons.Default.Pause, contentDescription = "Pause")
                }
            }
        }

        // Instructions - only show for first puzzle
        if (!gameStarted && isFirstPuzzle) {
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
                        text = "🔄 Symbol Swipe",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "When you see the LEFT symbol, swipe LEFT\nWhen you see the RIGHT symbol, swipe RIGHT",
                        fontSize = 16.sp,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    // Show the two symbols that will be used
                    if (leftSymbol != null && rightSymbol != null) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(32.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Box(
                                    modifier = Modifier
                                        .size(60.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(Color.Gray.copy(alpha = 0.1f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    SymbolCanvas(
                                        symbol = leftSymbol!!,
                                        modifier = Modifier.size(40.dp)
                                    )
                                }
                                Text("← SWIPE LEFT", fontSize = 12.sp, color = Color.Gray)
                            }

                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Box(
                                    modifier = Modifier
                                        .size(60.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(Color.Gray.copy(alpha = 0.1f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    SymbolCanvas(
                                        symbol = rightSymbol!!,
                                        modifier = Modifier.size(40.dp)
                                    )
                                }
                                Text("SWIPE RIGHT →", fontSize = 12.sp, color = Color.Gray)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = { gameStarted = true },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50))
                    ) {
                        Text(stringResource(R.string.start), fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // Game Area
        if (gameStarted && !gameCompleted && currentSymbolIndex < symbolSequence.size) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(16.dp)
            ) {
                // Reference symbols in corners (smaller, semi-transparent)
                if (leftSymbol != null) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(16.dp)
                            .size(80.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color.Black.copy(alpha = 0.3f)),
                        contentAlignment = Alignment.Center
                    ) {
                        SymbolCanvas(
                            symbol = leftSymbol!!,
                            modifier = Modifier.size(50.dp)
                        )
                    }
                }

                if (rightSymbol != null) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(16.dp)
                            .size(80.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color.Black.copy(alpha = 0.3f)),
                        contentAlignment = Alignment.Center
                    ) {
                        SymbolCanvas(
                            symbol = rightSymbol!!,
                            modifier = Modifier.size(50.dp)
                        )
                    }
                }

                // Current symbol display (center) - removed TAP indicators
                val currentSymbol = symbolSequence[currentSymbolIndex]
                Box(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(200.dp)
                        .offset(x = currentSymbolOffset.dp)
                        .scale(animatedScale)
                        .clip(RoundedCornerShape(20.dp))
                        .background(Color.White.copy(alpha = 0.95f))
                        .pointerInput(currentSymbolIndex) {
                            detectDragGestures(
                                onDragStart = {
                                    symbolScale = 1.05f
                                },
                                onDragEnd = {
                                    symbolScale = 1f
                                    if (abs(currentSymbolOffset) > 100) {
                                        val direction = if (currentSymbolOffset > 0) SwipeDirection.RIGHT else SwipeDirection.LEFT
                                        handleSwipe(direction)
                                    }
                                    currentSymbolOffset = 0f
                                }
                            ) { _, dragAmount ->
                                currentSymbolOffset += dragAmount.x * 0.5f // Reduce sensitivity
                                currentSymbolOffset = currentSymbolOffset.coerceIn(-300f, 300f)
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    SymbolCanvas(
                        symbol = currentSymbol.symbol,
                        modifier = Modifier.size(120.dp)
                    )
                }

                // Progress dots at bottom
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    repeat(minOf(symbolSequence.size, 9)) { index ->
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(
                                    when {
                                        index < currentSymbolIndex -> Color(0xFF4CAF50)
                                        index == currentSymbolIndex -> Color(0xFFFFEB3B)
                                        else -> Color.White.copy(alpha = 0.4f)
                                    }
                                )
                        )
                    }
                }

                // Hearts display
                Row(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    repeat(5) { index ->
                        val alpha = if (index < currentHearts) 1f else 0.3f
                        Text(
                            text = "●",
                            color = Color(0xFFFFEB3B).copy(alpha = alpha),
                            fontSize = 16.sp
                        )
                    }
                    Text(
                        text = "x1",
                        color = Color(0xFFFFEB3B),
                        fontSize = 14.sp,
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }
            }
        }

        // Auto-start countdown for non-first puzzles
        if (!gameStarted && !isFirstPuzzle) {
            var countdown by remember { mutableStateOf(3) }

            LaunchedEffect(Unit) {
                while (countdown > 0) {
                    delay(1000)
                    countdown--
                }
                gameStarted = true
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color.White)
                ) {
                    Text(
                        text = "${stringResource(R.string.get_ready)}\n$countdown",
                        modifier = Modifier.padding(32.dp),
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
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

@Composable
fun SymbolCanvas(
    symbol: GameSymbol,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val center = Offset(size.width / 2, size.height / 2)
        val radius = minOf(size.width, size.height) / 3

        when (symbol) {
            is GameSymbol.Star -> drawStar(center, radius, symbol.color)
            is GameSymbol.Circle -> drawCircle(symbol.color, radius, center)
            is GameSymbol.Triangle -> drawTriangle(center, radius, symbol.color)
            is GameSymbol.Diamond -> drawDiamond(center, radius, symbol.color)
            is GameSymbol.Square -> drawRect(symbol.color, center, radius)
            is GameSymbol.Heart -> drawHeart(center, radius, symbol.color)
            is GameSymbol.Hexagon -> drawHexagon(center, radius, symbol.color)
            is GameSymbol.Cross -> drawCross(center, radius, symbol.color)
        }
    }
}

private fun DrawScope.drawStar(center: Offset, radius: Float, color: Color) {
    val path = Path()
    val outerRadius = radius
    val innerRadius = radius * 0.4f

    for (i in 0 until 10) {
        val angle = (i * 36 - 90) * PI / 180
        val r = if (i % 2 == 0) outerRadius else innerRadius
        val x = center.x + (r * cos(angle)).toFloat()
        val y = center.y + (r * sin(angle)).toFloat()

        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    path.close()
    drawPath(path, color)
}

private fun DrawScope.drawTriangle(center: Offset, radius: Float, color: Color) {
    val path = Path()
    val height = radius * 1.5f
    path.moveTo(center.x, center.y - height * 0.6f)
    path.lineTo(center.x - radius, center.y + height * 0.4f)
    path.lineTo(center.x + radius, center.y + height * 0.4f)
    path.close()
    drawPath(path, color)
}

private fun DrawScope.drawDiamond(center: Offset, radius: Float, color: Color) {
    val path = Path()
    path.moveTo(center.x, center.y - radius)
    path.lineTo(center.x + radius, center.y)
    path.lineTo(center.x, center.y + radius)
    path.lineTo(center.x - radius, center.y)
    path.close()
    drawPath(path, color)
}

private fun DrawScope.drawRect(color: Color, center: Offset, radius: Float) {
    val size = radius * 1.4f
    drawRect(
        color = color,
        topLeft = Offset(center.x - size/2, center.y - size/2),
        size = androidx.compose.ui.geometry.Size(size, size)
    )
}

private fun DrawScope.drawHeart(center: Offset, radius: Float, color: Color) {
    val path = Path()
    val width = radius * 1.2f
    val height = radius * 1.2f

    path.moveTo(center.x, center.y + height * 0.3f)

    // Left curve
    path.cubicTo(
        center.x - width * 0.6f, center.y - height * 0.2f,
        center.x - width * 0.6f, center.y - height * 0.8f,
        center.x, center.y - height * 0.3f
    )

    // Right curve
    path.cubicTo(
        center.x + width * 0.6f, center.y - height * 0.8f,
        center.x + width * 0.6f, center.y - height * 0.2f,
        center.x, center.y + height * 0.3f
    )

    drawPath(path, color)
}

private fun DrawScope.drawHexagon(center: Offset, radius: Float, color: Color) {
    val path = Path()
    for (i in 0 until 6) {
        val angle = i * 60 * PI / 180
        val x = center.x + (radius * cos(angle)).toFloat()
        val y = center.y + (radius * sin(angle)).toFloat()
        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    path.close()
    drawPath(path, color)
}

private fun DrawScope.drawCross(center: Offset, radius: Float, color: Color) {
    val thickness = radius * 0.3f
    val length = radius * 1.2f

    // Vertical bar
    drawRect(
        color = color,
        topLeft = Offset(center.x - thickness/2, center.y - length/2),
        size = androidx.compose.ui.geometry.Size(thickness, length)
    )

    // Horizontal bar
    drawRect(
        color = color,
        topLeft = Offset(center.x - length/2, center.y - thickness/2),
        size = androidx.compose.ui.geometry.Size(length, thickness)
    )
}