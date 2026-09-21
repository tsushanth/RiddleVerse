// SymbolSwipePuzzleScreen.kt
package com.kreativekoala.riddleverse

import com.kreativekoala.riddleverse.ui.theme.*
import android.util.Log
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
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
    object Star : GameSymbol(RvSuccess, "Star")
    object Circle : GameSymbol(RvSky, "Circle")
    object Triangle : GameSymbol(RvSun, "Triangle")
    object Diamond : GameSymbol(Color(0xFFE91E63), "Diamond")
    object Square : GameSymbol(RvGrape, "Square")
    object Heart : GameSymbol(RvFlame, "Heart")
    object Hexagon : GameSymbol(RvSky, "Hexagon")
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
            feedbackColor = RvSuccess
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

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(RvCanvas)
    ) {
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
            GroupECompactHud(
                timer = String.format("%02d:%02d", timeLeft / 60, timeLeft % 60),
                onBack = onBack,
                subtitle = "${stringResource(R.string.score_label)} $score" +
                    (if (multiplier > 1) " \u00D7$multiplier" else "") +
                    " \u2022 ${currentSymbolIndex}/${symbolSequence.size}",
                lives = currentHearts,
                urgent = timeLeft <= 30,
                onPause = { isPaused = !isPaused }
            )

            Spacer(modifier = Modifier.height(8.dp))

            if (!gameStarted && isFirstPuzzle) {
                GroupESwipeInstructions(
                    title = "\uD83D\uDD04 Symbol Swipe",
                    leftSymbol = leftSymbol,
                    rightSymbol = rightSymbol,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                GroupEPrimaryButton(
                    text = stringResource(R.string.start),
                    onClick = { gameStarted = true },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            if (gameStarted && !gameCompleted && currentSymbolIndex < symbolSequence.size) {
                GroupESwipeField(
                    leftSymbol = leftSymbol,
                    rightSymbol = rightSymbol,
                    currentSymbol = symbolSequence[currentSymbolIndex].symbol,
                    offsetDp = currentSymbolOffset,
                    scale = animatedScale,
                    dragKey = currentSymbolIndex,
                    onDragStart = { symbolScale = 1.05f },
                    onDragEnd = {
                        symbolScale = 1f
                        if (abs(currentSymbolOffset) > 100) {
                            val direction = if (currentSymbolOffset > 0) SwipeDirection.RIGHT else SwipeDirection.LEFT
                            handleSwipe(direction)
                        }
                        currentSymbolOffset = 0f
                    },
                    onDrag = { dx ->
                        currentSymbolOffset += dx * 0.5f // Reduce sensitivity
                        currentSymbolOffset = currentSymbolOffset.coerceIn(-300f, 300f)
                    },
                    progressIndex = currentSymbolIndex,
                    progressTotal = symbolSequence.size,
                    feedbackMessage = if (showFeedback) feedbackMessage else null,
                    feedbackColor = feedbackColor,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                )
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
                        colors = CardDefaults.cardColors(containerColor = RvSurface)
                    ) {
                        Text(
                            text = "${stringResource(R.string.get_ready)}\n$countdown",
                            modifier = Modifier.padding(32.dp),
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                            color = RvInk,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
    }
}

/** Instructions for the swipe games. Scrolls only as a last-resort safety net; Start sits outside it. */
@Composable
internal fun GroupESwipeInstructions(
    title: String,
    leftSymbol: GameSymbol?,
    rightSymbol: GameSymbol?,
    modifier: Modifier = Modifier,
    extra: String? = null
) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
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
                    text = title,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = RvInk,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "When you see the LEFT symbol, swipe LEFT\nWhen you see the RIGHT symbol, swipe RIGHT",
                    fontSize = 16.sp,
                    color = RvInk,
                    textAlign = TextAlign.Center
                )
                if (extra != null) {
                    Text(
                        text = extra,
                        fontSize = 14.sp,
                        color = RvInkSoft,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))

                if (leftSymbol != null && rightSymbol != null) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(24.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        listOf(leftSymbol to "\u2190 SWIPE LEFT", rightSymbol to "SWIPE RIGHT \u2192").forEach { (sym, label) ->
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Box(
                                    modifier = Modifier
                                        .size(64.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(RvSurfaceRaised),
                                    contentAlignment = Alignment.Center
                                ) {
                                    SymbolCanvas(symbol = sym, modifier = Modifier.size(44.dp))
                                }
                                Text(label, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = RvInkSoft, textAlign = TextAlign.Center)
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Swipe play field: the two reference symbols sit on the sides they must be swiped to, the card is in
 * the middle and everything is sized from the space actually available (no scrolling, no clipping).
 */
@Composable
internal fun GroupESwipeField(
    leftSymbol: GameSymbol?,
    rightSymbol: GameSymbol?,
    currentSymbol: GameSymbol,
    offsetDp: Float,
    scale: Float,
    dragKey: Int,
    onDragStart: () -> Unit,
    onDragEnd: () -> Unit,
    onDrag: (Float) -> Unit,
    progressIndex: Int,
    progressTotal: Int,
    feedbackMessage: String?,
    feedbackColor: Color,
    modifier: Modifier = Modifier
) {
    BoxWithConstraints(
        modifier = modifier
            .testTag("swipe_area")
            .clip(RoundedCornerShape(16.dp))
            .background(RvSurface)
    ) {
        val w = maxWidth
        val h = maxHeight
        val dotsBand = 24.dp
        val refSize = (w * 0.2f).coerceIn(56.dp, 88.dp)
        val cardMax = minOf(w - (refSize + 8.dp) * 2 - 16.dp, h - dotsBand - 16.dp)
        val card = cardMax.coerceIn(96.dp, 260.dp)

        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 8.dp, end = 8.dp, bottom = dotsBand),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            GroupESwipeRef(leftSymbol, refSize, "\u2190", "swipe_ref_left")

            Box(
                modifier = Modifier
                    .size(card)
                    .offset(x = offsetDp.dp)
                    .scale(scale)
                    .testTag("swipe_card")
                    .clip(RoundedCornerShape(20.dp))
                    .background(RvSurfaceRaised)
                    .border(2.dp, RvOutline, RoundedCornerShape(20.dp))
                    .pointerInput(dragKey) {
                        detectDragGestures(
                            onDragStart = { onDragStart() },
                            onDragEnd = { onDragEnd() }
                        ) { _, dragAmount -> onDrag(dragAmount.x) }
                    },
                contentAlignment = Alignment.Center
            ) {
                SymbolCanvas(symbol = currentSymbol, modifier = Modifier.size(card * 0.6f))
            }

            GroupESwipeRef(rightSymbol, refSize, "\u2192", "swipe_ref_right")
        }

        // Progress dots (decorative; the exact count is also in the HUD text).
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            repeat(minOf(progressTotal, 9)) { index ->
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(
                            when {
                                index < progressIndex -> RvSuccess
                                index == progressIndex -> RvSun
                                else -> RvOutline
                            }
                        )
                )
            }
        }

        if (feedbackMessage != null) {
            Card(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(8.dp),
                colors = CardDefaults.cardColors(containerColor = RvSurfaceRaised),
                border = BorderStroke(2.dp, feedbackColor)
            ) {
                Text(
                    text = feedbackMessage,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
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
}

@Composable
private fun GroupESwipeRef(symbol: GameSymbol?, size: Dp, arrow: String, tag: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(size)
                .testTag(tag)
                .clip(RoundedCornerShape(12.dp))
                .background(RvSurfaceRaised)
                .border(2.dp, RvOutline, RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center
        ) {
            if (symbol != null) SymbolCanvas(symbol = symbol, modifier = Modifier.size(size * 0.7f))
        }
        Text(text = arrow, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = RvInkSoft)
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