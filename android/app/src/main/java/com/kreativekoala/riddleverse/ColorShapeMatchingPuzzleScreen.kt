// ColorShapeMatchingPuzzleScreen.kt
package com.kreativekoala.riddleverse

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

data class ColorShapeChallenge(
    val shapeColor: Color,
    val shapeType: ShapeType,
    val colorNameText: String?,
    val colorNameFontColor: Color,
    val isCorrectMatch: Boolean
)

enum class ShapeType {
    CIRCLE, SQUARE, TRIANGLE, HEXAGON, DIAMOND, STAR
}

@Composable
fun PuzzleHeader(
    difficulty: String,
    timer: String,
    hearts: Int,
    level: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    maxHearts: Int = 3
) {
    Card(
        modifier = modifier.statusBarsPadding().fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = Color.White
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Top row: Back button, Level, Hearts
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Back button
                IconButton(
                    onClick = onBack,
                    modifier = Modifier
                        .size(40.dp)
                        .background(
                            Color(0xFFF0F0F0),
                            CircleShape
                        )
                ) {
                    Icon(
                        imageVector = Icons.Default.ArrowBack,
                        contentDescription = stringResource(R.string.back),
                        tint = Color(0xFF333333),
                        modifier = Modifier.size(20.dp)
                    )
                }

                // Level indicator
                Text(
                    text = level,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF333333),
                    textAlign = TextAlign.Center
                )

                // Hearts
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    repeat(maxHearts) { index ->
                        Icon(
                            imageVector = if (index < hearts) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                            contentDescription = null,
                            tint = if (index < hearts) Color(0xFFE53E3E) else Color(0xFFCCCCCC),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Bottom row: Difficulty and Timer
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Difficulty badge
                DifficultyBadge(difficulty = difficulty)

                // Timer
                TimerDisplay(timer = timer)
            }
        }
    }
}

@Composable
private fun DifficultyBadge(
    difficulty: String,
    modifier: Modifier = Modifier
) {
    val (backgroundColor, textColor) = when (difficulty.lowercase()) {
        "easy" -> Color(0xFFE6FFFA) to Color(0xFF38A169)
        "medium" -> Color(0xFFFFF3CD) to Color(0xFFD69E2E)
        "hard" -> Color(0xFFFED7D7) to Color(0xFFE53E3E)
        "expert" -> Color(0xFFE6E6FA) to Color(0xFF805AD5)
        else -> Color(0xFFF0F0F0) to Color(0xFF666666)
    }

    Box(
        modifier = modifier
            .background(
                color = backgroundColor,
                shape = RoundedCornerShape(20.dp)
            )
            .border(
                width = 1.dp,
                color = textColor.copy(alpha = 0.3f),
                shape = RoundedCornerShape(20.dp)
            )
            .padding(horizontal = 12.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = difficulty.uppercase(),
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = textColor
        )
    }
}

@Composable
private fun TimerDisplay(
    timer: String,
    modifier: Modifier = Modifier
) {
    val (backgroundColor, textColor, iconColor) = when {
        timer.startsWith("0:") && timer.substring(2).toIntOrNull()?.let { it <= 30 } == true -> {
            // Less than 30 seconds - critical
            Triple(Color(0xFFFED7D7), Color(0xFFE53E3E), Color(0xFFE53E3E))
        }
        timer.startsWith("1:") -> {
            // 1-2 minutes - warning
            Triple(Color(0xFFFFF3CD), Color(0xFFD69E2E), Color(0xFFD69E2E))
        }
        else -> {
            // More than 2 minutes - normal
            Triple(Color(0xFFE6FFFA), Color(0xFF38A169), Color(0xFF38A169))
        }
    }

    Row(
        modifier = modifier
            .background(
                color = backgroundColor,
                shape = RoundedCornerShape(20.dp)
            )
            .border(
                width = 1.dp,
                color = textColor.copy(alpha = 0.3f),
                shape = RoundedCornerShape(20.dp)
            )
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        // Timer icon
        Text(
            text = "⏰",
            fontSize = 14.sp
        )

        Text(
            text = timer,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = textColor
        )
    }
}

@Composable
fun PuzzleHeaderPreview() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFFF5F5F5))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Easy difficulty with full hearts
        PuzzleHeader(
            difficulty = "Easy",
            timer = "2:45",
            hearts = 3,
            level = "1/5",
            onBack = {}
        )

        // Medium difficulty with 2 hearts
        PuzzleHeader(
            difficulty = "Medium",
            timer = "1:23",
            hearts = 2,
            level = "3/8",
            onBack = {}
        )

        // Hard difficulty with 1 heart and low time
        PuzzleHeader(
            difficulty = "Hard",
            timer = "0:15",
            hearts = 1,
            level = "7/10",
            onBack = {}
        )

        // Expert difficulty with no hearts
        PuzzleHeader(
            difficulty = "Expert",
            timer = "0:03",
            hearts = 0,
            level = "10/10",
            onBack = {}
        )
    }
}

@Composable
fun ColorShapeMatchingPuzzleScreen(
    difficulty: String = "Medium",
    timer: String = "2:00",
    hearts: Int = 3,
    level: String = "1/5",
    onSubmitAnswer: (Boolean) -> Unit,
    fetchNextPuzzle: (Int) -> Unit,
    onBack: () -> Unit
) {
    var currentChallenge by remember { mutableStateOf(generateRandomChallenge()) }
    var selectedAnswer by remember { mutableStateOf<Boolean?>(null) }
    var showFeedback by remember { mutableStateOf(false) }
    var isCorrect by remember { mutableStateOf(false) }
    var score by remember { mutableStateOf(0) }
    var timeLeft by remember { mutableStateOf(parseTimeToSeconds(timer)) }
    var currentHearts by remember { mutableStateOf(hearts) }
    var gameCompleted by remember { mutableStateOf(false) }
    var challengeNumber by remember { mutableStateOf(1) }
    val totalChallenges = 10

    // Timer effect
    LaunchedEffect(timeLeft) {
        if (timeLeft > 0 && !gameCompleted) {
            delay(1000L)
            timeLeft--
        } else if (timeLeft == 0) {
            gameCompleted = true
            onSubmitAnswer(false)
        }
    }

    // Auto-advance effect after showing feedback
    LaunchedEffect(showFeedback) {
        if (showFeedback) {
            delay(1500L)
            showFeedback = false
            selectedAnswer = null

            if (challengeNumber >= totalChallenges) {
                gameCompleted = true
                fetchNextPuzzle(score)
            } else {
                challengeNumber++
                currentChallenge = generateRandomChallenge()
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF5F5F5))
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Header
        PuzzleHeader(
            difficulty = difficulty,
            timer = formatTime(timeLeft),
            hearts = currentHearts,
            level = level,
            onBack = onBack
        )

        Spacer(modifier = Modifier.height(20.dp))

        // Progress indicator
        Text(
            text = "Challenge $challengeNumber of $totalChallenges",
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium,
            color = Color(0xFF666666)
        )

        Spacer(modifier = Modifier.height(8.dp))

        LinearProgressIndicator(
            progress = challengeNumber.toFloat() / totalChallenges,
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp)),
            color = Color(0xFF4CAF50),
            trackColor = Color(0xFFE0E0E0)
        )

        Spacer(modifier = Modifier.height(32.dp))

        // Instructions
        Text(
            text = "Does the text meaning match the shape color?",
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            color = Color(0xFF333333)
        )

        Spacer(modifier = Modifier.height(32.dp))

        // Color name text (if present)
        currentChallenge.colorNameText?.let { colorText ->
            Text(
                text = colorText,
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                color = currentChallenge.colorNameFontColor,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(24.dp))
        }

        // Shape display
        Box(
            modifier = Modifier
                .size(200.dp)
                .background(Color.White, RoundedCornerShape(16.dp))
                .padding(32.dp),
            contentAlignment = Alignment.Center
        ) {
            ShapeDisplay(
                shapeType = currentChallenge.shapeType,
                color = currentChallenge.shapeColor,
                modifier = Modifier.size(120.dp)
            )
        }

        Spacer(modifier = Modifier.weight(1f))

        // Answer buttons
        if (!showFeedback) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                // No button
                Button(
                    onClick = {
                        selectedAnswer = false
                        isCorrect = !currentChallenge.isCorrectMatch
                        if (isCorrect) {
                            score += 10
                        } else {
                            currentHearts = maxOf(0, currentHearts - 1)
                        }
                        showFeedback = true
                        onSubmitAnswer(isCorrect)
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(60.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFE53E3E)
                    ),
                    shape = RoundedCornerShape(30.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Text("✗", fontSize = 24.sp, color = Color.White)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "NO",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }

                Spacer(modifier = Modifier.width(16.dp))

                // Yes button
                Button(
                    onClick = {
                        selectedAnswer = true
                        isCorrect = currentChallenge.isCorrectMatch
                        if (isCorrect) {
                            score += 10
                        } else {
                            currentHearts = maxOf(0, currentHearts - 1)
                        }
                        showFeedback = true
                        onSubmitAnswer(isCorrect)
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(60.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF38A169)
                    ),
                    shape = RoundedCornerShape(30.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Text("✓", fontSize = 24.sp, color = Color.White)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.yes),
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }
            }
        }

        // Feedback display
        if (showFeedback) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(100.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (isCorrect) Color(0xFFE6FFFA) else Color(0xFFFFF5F5)
                ),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = if (isCorrect) "✅ Correct!" else "❌ Incorrect",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isCorrect) Color(0xFF38A169) else Color(0xFFE53E3E)
                    )

                    Text(
                        text = "Score: $score",
                        fontSize = 14.sp,
                        color = Color(0xFF666666)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
fun ShapeDisplay(
    shapeType: ShapeType,
    color: Color,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val center = Offset(size.width / 2f, size.height / 2f)
        val radius = minOf(size.width, size.height) / 2f * 0.8f

        when (shapeType) {
            ShapeType.CIRCLE -> {
                drawCircle(
                    color = color,
                    radius = radius,
                    center = center
                )
            }
            ShapeType.SQUARE -> {
                drawRect(
                    color = color,
                    topLeft = Offset(center.x - radius, center.y - radius),
                    size = androidx.compose.ui.geometry.Size(radius * 2, radius * 2)
                )
            }
            ShapeType.TRIANGLE -> {
                drawTriangle(color, center, radius)
            }
            ShapeType.HEXAGON -> {
                drawPolygon(color, center, radius, 6)
            }
            ShapeType.DIAMOND -> {
                drawDiamond(color, center, radius)
            }
            ShapeType.STAR -> {
                drawStar(color, center, radius)
            }
        }
    }
}

private fun DrawScope.drawTriangle(color: Color, center: Offset, radius: Float) {
    val path = Path().apply {
        moveTo(center.x, center.y - radius)
        lineTo(center.x - radius * 0.866f, center.y + radius * 0.5f)
        lineTo(center.x + radius * 0.866f, center.y + radius * 0.5f)
        close()
    }
    drawPath(path, color)
}

private fun DrawScope.drawPolygon(color: Color, center: Offset, radius: Float, sides: Int) {
    val path = Path()
    val angleStep = 2 * PI / sides

    for (i in 0 until sides) {
        val angle = i * angleStep - PI / 2
        val x = center.x + radius * cos(angle).toFloat()
        val y = center.y + radius * sin(angle).toFloat()

        if (i == 0) {
            path.moveTo(x, y)
        } else {
            path.lineTo(x, y)
        }
    }
    path.close()
    drawPath(path, color)
}

private fun DrawScope.drawDiamond(color: Color, center: Offset, radius: Float) {
    val path = Path().apply {
        moveTo(center.x, center.y - radius)
        lineTo(center.x + radius, center.y)
        lineTo(center.x, center.y + radius)
        lineTo(center.x - radius, center.y)
        close()
    }
    drawPath(path, color)
}

private fun DrawScope.drawStar(color: Color, center: Offset, radius: Float) {
    val path = Path()
    val innerRadius = radius * 0.4f
    val points = 5
    val angleStep = PI / points

    for (i in 0 until points * 2) {
        val angle = i * angleStep - PI / 2
        val currentRadius = if (i % 2 == 0) radius else innerRadius
        val x = center.x + currentRadius * cos(angle).toFloat()
        val y = center.y + currentRadius * sin(angle).toFloat()

        if (i == 0) {
            path.moveTo(x, y)
        } else {
            path.lineTo(x, y)
        }
    }
    path.close()
    drawPath(path, color)
}

private fun generateRandomChallenge(): ColorShapeChallenge {
    val colors = listOf(
        Color.Red to "RED",
        Color.Blue to "BLUE",
        Color.Green to "GREEN",
        Color.Yellow to "YELLOW",
        Color.Magenta to "PURPLE",
        Color(0xFFFFA500) to "ORANGE",
        Color(0xFFFFC0CB) to "PINK",
        Color(0xFF8B4513) to "BROWN"
    )

    val shapes = ShapeType.values()

    val shapeColorPair = colors.random()
    val shapeColor = shapeColorPair.first
    val shapeColorName = shapeColorPair.second
    val shapeType = shapes.random()

    // Always show text - that's the whole point of the challenge!
    // Generate color name text and font color
    val textColorPair = colors.random()
    val textFontColor = textColorPair.first

    // Different scenarios for confusion
    val scenario = Random.nextInt(3) // Changed from 4 to 3

    return when (scenario) {
        0 -> {
            // Correct color name in correct font color (MATCH)
            ColorShapeChallenge(
                shapeColor = shapeColor,
                shapeType = shapeType,
                colorNameText = shapeColorName,
                colorNameFontColor = shapeColor,
                isCorrectMatch = true
            )
        }
        1 -> {
            // Correct color name in wrong font color (STILL A MATCH)
            val wrongFontColor = colors.filter { it.first != shapeColor }.random().first
            ColorShapeChallenge(
                shapeColor = shapeColor,
                shapeType = shapeType,
                colorNameText = shapeColorName,
                colorNameFontColor = wrongFontColor,
                isCorrectMatch = true  // Text content matches shape
            )
        }
        else -> {
            // Wrong color name in any font color (NO MATCH)
            val wrongColorName = colors.filter { it.second != shapeColorName }.random().second
            val fontColor = colors.random().first // Any random font color
            ColorShapeChallenge(
                shapeColor = shapeColor,
                shapeType = shapeType,
                colorNameText = wrongColorName,
                colorNameFontColor = fontColor,
                isCorrectMatch = false
            )
        }
    }
}

private fun parseTimeToSeconds(timeString: String): Int {
    val parts = timeString.split(":")
    return if (parts.size == 2) {
        parts[0].toIntOrNull()?.times(60)?.plus(parts[1].toIntOrNull() ?: 0) ?: 120
    } else {
        120 // Default 2 minutes
    }
}