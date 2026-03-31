package com.kreativekoala.riddleverse

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.Image
import kotlinx.coroutines.delay
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Timer
import androidx.compose.ui.text.style.TextAlign
import android.os.Handler
import android.os.Looper
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource

@Composable
fun FallingGameScreen(
    puzzleId: String,
    question: String,
    difficulty: String = "Easy",
    options: List<String>,
    correctAnswer: String,
    selectedOption: String?,
    timerSeconds: Int = 60,
    questionNumber: Int = 1,
    totalQuestions: Int = 1,
    onOptionSelected: (String) -> Unit,
    onContinue: (String?) -> Unit,
    onCorrectAnswer: (Boolean) -> Unit,
    onHint: () -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val rocketHeight = 100.dp
    val screenHeight = LocalConfiguration.current.screenHeightDp.dp
    val density = LocalDensity.current
    val screenHeightPx = with(density) { screenHeight.toPx() }
    val rocketHeightPx = with(density) { rocketHeight.toPx() }

    var rocketY by remember { mutableStateOf(0f) }
    var lives by remember { mutableStateOf(3) }
    var gameScore by remember { mutableStateOf(0) }
    var remainingTime by remember { mutableIntStateOf(timerSeconds) }
    var isGameActive by remember { mutableStateOf(true) }
    var showResult by remember { mutableStateOf(false) }
    var isCorrect by remember { mutableStateOf(false) }
    var selectedAnswer by remember { mutableStateOf<String?>(null) }

    // Game timer
    LaunchedEffect(Unit) {
        while (remainingTime > 0 && isGameActive && lives > 0) {
            delay(1000L)
            remainingTime--
        }
        if (remainingTime <= 0 || lives <= 0) {
            isGameActive = false
        }
    }

    // Rocket falling animation
    LaunchedEffect(isGameActive) {
        if (isGameActive) {
            while (rocketY < screenHeightPx - rocketHeightPx && isGameActive && lives > 0) {
                val progress = rocketY / (screenHeightPx - rocketHeightPx)
                val speed = 3f * (1f - progress * 0.5f).coerceAtLeast(0.5f) // Slower falling

                rocketY += speed
                delay(16L) // ~60 FPS
            }
            // If rocket reaches bottom without answer, lose a life
            if (rocketY >= screenHeightPx - rocketHeightPx && isGameActive && selectedAnswer == null) {
                lives--
                rocketY = 0f // Reset rocket position
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                // Gradient space background
                androidx.compose.ui.graphics.Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF000428),
                        Color(0xFF004e92)
                    )
                )
            )
            .statusBarsPadding()
            .padding(16.dp)
    ) {
        // Top Bar
        Row(
            modifier = Modifier
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Back button and progress
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(
                        Icons.Default.ArrowBack,
                        contentDescription = stringResource(R.string.back),
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }
                Column {
                    Text(
                        "Question $questionNumber/$totalQuestions",
                        color = Color.White,
                        fontSize = 12.sp
                    )
                    Text(
                        difficulty,
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 10.sp
                    )
                }
            }

            // Score
            Text(
                "Score: $gameScore",
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )

            // Timer and Lives
            Column(horizontalAlignment = Alignment.End) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Timer,
                        contentDescription = "Timer",
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        String.format("%d:%02d", remainingTime / 60, remainingTime % 60),
                        color = Color.White,
                        fontSize = 14.sp
                    )
                }
                Row {
                    repeat(lives) {
                        Icon(
                            Icons.Default.Favorite,
                            contentDescription = "Life",
                            tint = Color.Red,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }

        // Falling Object (Rocket/Meteor/Star)
        Box(
            modifier = Modifier
                .offset(y = with(LocalDensity.current) { rocketY.toDp() })
                .align(Alignment.TopCenter)
                .size(80.dp)
        ) {
            // Fallback design if no rocket image
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(CircleShape)
                    .background(
                        androidx.compose.ui.graphics.Brush.radialGradient(
                            colors = listOf(
                                Color(0xFFFFD700),
                                Color(0xFFFF8C00)
                            )
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "🚀",
                    fontSize = 32.sp
                )
            }
        }

        // Question Card
        Card(
            modifier = Modifier
                .align(Alignment.Center)
                .padding(horizontal = 32.dp)
                .offset(y = (-40).dp),
            colors = CardDefaults.cardColors(
                containerColor = Color.Black.copy(alpha = 0.7f)
            ),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    "Quick! Answer before it crashes!",
                    color = Color.White.copy(alpha = 0.8f),
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    question,
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
            }
        }

        // Result Display
        if (showResult) {
            Card(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(32.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (isCorrect) Color(0xFF4CAF50) else Color(0xFFE57373)
                )
            ) {
                Text(
                    text = if (isCorrect) "🎉 Correct! +10 Points" else "❌ Wrong! Answer: $correctAnswer",
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(16.dp)
                )
            }
        }

        // Option Buttons
        if (!showResult && isGameActive && lives > 0) {
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 40.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                options.forEach { option ->
                    Button(
                        onClick = {
                            if (selectedAnswer == null && isGameActive) {
                                selectedAnswer = option
                                onOptionSelected(option)
                                isCorrect = option == correctAnswer
                                showResult = true
                                isGameActive = false

                                if (isCorrect) {
                                    gameScore += 10
                                    onCorrectAnswer(remainingTime > 0)
                                } else {
                                    lives--
                                }

                                // Auto-continue after showing result
                                Handler(Looper.getMainLooper()).postDelayed({
                                    onContinue(option)
                                }, 2500)
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF1E88E5).copy(alpha = 0.9f)
                        ),
                        shape = RoundedCornerShape(25.dp),
                        modifier = Modifier
                            .fillMaxWidth(0.8f)
                            .height(50.dp)
                    ) {
                        Text(
                            option,
                            fontSize = 14.sp,
                            color = Color.White,
                            fontWeight = FontWeight.Medium,
                            textAlign = TextAlign.Center
                        )
                    }
                }

                Spacer(Modifier.height(8.dp))

                // Hint Button
                OutlinedButton(
                    onClick = onHint,
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = Color.White
                    ),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color.White),
                    shape = RoundedCornerShape(20.dp),
                    enabled = isGameActive
                ) {
                    Text("💡 Hint", fontSize = 12.sp)
                }
            }
        }

        // Game Over Screen
        if (!isGameActive && lives <= 0 && !showResult) {
            Card(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(32.dp),
                colors = CardDefaults.cardColors(
                    containerColor = Color.Black.copy(alpha = 0.8f)
                )
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        "💥 Game Over!",
                        color = Color.Red,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "${stringResource(R.string.final_score)}: $gameScore",
                        color = Color.White,
                        fontSize = 16.sp
                    )
                    Spacer(Modifier.height(16.dp))
                    Button(
                        onClick = { onContinue(null) },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF1E88E5)
                        )
                    ) {
                        Text(stringResource(R.string.continue_label), color = Color.White)
                    }
                }
            }
        }

        // Time's Up Screen
        if (!isGameActive && remainingTime <= 0 && !showResult) {
            Card(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(32.dp),
                colors = CardDefaults.cardColors(
                    containerColor = Color.Black.copy(alpha = 0.8f)
                )
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        "⏰ Time's Up!",
                        color = Color(0xFFFF9800),
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Answer: $correctAnswer",
                        color = Color.White,
                        fontSize = 16.sp,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(16.dp))
                    Button(
                        onClick = { onContinue(null) },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF1E88E5)
                        )
                    ) {
                        Text(stringResource(R.string.continue_label), color = Color.White)
                    }
                }
            }
        }
    }
}
