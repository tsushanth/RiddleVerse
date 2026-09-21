package com.kreativekoala.riddleverse

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.ui.text.style.TextOverflow
import com.kreativekoala.riddleverse.ui.theme.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import android.os.Handler
import android.os.Looper
import androidx.compose.ui.res.stringResource
import kotlin.math.*

@Composable
fun PendulumChoiceScreen(
    puzzleId: String,
    question: String,
    difficulty: String = "Easy",
    options: List<String>,
    correctAnswer: String,
    selectedOption: String?,
    timerSeconds: Int = 90,
    questionNumber: Int = 1,
    totalQuestions: Int = 1,
    onOptionSelected: (String) -> Unit,
    onContinue: (String?) -> Unit,
    onCorrectAnswer: (Boolean) -> Unit,
    onHint: () -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var remainingTime by remember { mutableIntStateOf(timerSeconds) }
    var selectedAnswer by remember { mutableStateOf<String?>(null) }
    var showResult by remember { mutableStateOf(false) }
    var isCorrect by remember { mutableStateOf(false) }
    var gameScore by remember { mutableStateOf(0) }

    // Pendulum animation
    val infiniteTransition = rememberInfiniteTransition(label = "pendulum")
    val pendulumAngle by infiniteTransition.animateFloat(
        initialValue = -15f,
        targetValue = 15f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pendulumSwing"
    )

    // Timer
    LaunchedEffect(Unit) {
        while (remainingTime > 0 && !showResult) {
            delay(1000L)
            remainingTime--
        }
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(RvCanvas),
        contentAlignment = Alignment.TopCenter
    ) {
        val compact = maxHeight < 600.dp
        val circleSize = ((maxWidth - 32.dp) / options.size.coerceAtLeast(1) - 8.dp)
            .coerceIn(56.dp, 88.dp)
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .widthIn(max = 640.dp)
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 16.dp)
                .padding(bottom = if (compact) 8.dp else 16.dp)
        ) {
            // Top Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Score and Pause
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.Default.ArrowBack,
                            contentDescription = stringResource(R.string.back),
                            tint = RvInk,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Text(
                        "$gameScore",
                        color = RvInk,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Timer
                Text(
                    String.format("%d:%02d", remainingTime / 60, remainingTime % 60),
                    color = RvInk,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(Modifier.height(if (compact) 4.dp else 24.dp))

            // Question (dominant element, scaled down on small screens)
            Text(
                text = question,
                fontSize = if (compact) 32.sp else 48.sp,
                lineHeight = if (compact) 36.sp else 52.sp,
                fontWeight = FontWeight.Bold,
                color = RvInk,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            )

            Spacer(Modifier.height(if (compact) 4.dp else 16.dp))

            // Pendulum play area takes the remaining space
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .heightIn(max = 340.dp)
                    .align(Alignment.CenterHorizontally),
                contentAlignment = Alignment.TopCenter
            ) {
                Canvas(
                    modifier = Modifier.fillMaxSize()
                ) {
                    drawPendulumLines(
                        options = options,
                        angle = pendulumAngle,
                        selectedOption = selectedAnswer,
                        correctAnswer = if (showResult) correctAnswer else null,
                        showResult = showResult,
                        bottomReserve = circleSize / 2 + 8.dp
                    )
                }

                // Option circles at the bottom
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    options.forEachIndexed { index, option ->
                        OptionPendulumCircle(
                            option = option,
                            size = circleSize,
                            isSelected = selectedAnswer == option,
                            isCorrect = option == correctAnswer,
                            showResult = showResult,
                            onClick = {
                                if (!showResult) {
                                    selectedAnswer = option
                                    onOptionSelected(option)
                                    isCorrect = option == correctAnswer
                                    showResult = true

                                    if (isCorrect) {
                                        gameScore += 10
                                        onCorrectAnswer(remainingTime > 0)
                                    }

                                    // Auto-continue after 2.5 seconds
                                    Handler(Looper.getMainLooper()).postDelayed({
                                        onContinue(option)
                                    }, 2500)
                                }
                            }
                        )
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            // Result Display
            if (showResult) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isCorrect) RvSuccess else RvError
                    )
                ) {
                    Text(
                        text = if (isCorrect) "🎉 Correct!" else "❌ Wrong! Answer: $correctAnswer",
                        color = RvInk,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(12.dp)
                    )
                }
                Spacer(Modifier.height(8.dp))
            }

            // Instructions (dropped in compact mode)
            if (!compact) {
                Text(
                    text = "TAP THE MATCHING VERB",
                    fontSize = 14.sp,
                    color = RvInkSoft,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
                Spacer(Modifier.height(8.dp))
            }

            // Hint Button
            OutlinedButton(
                onClick = onHint,
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = RvInk
                ),
                border = androidx.compose.foundation.BorderStroke(1.dp, RvOutline),
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .heightIn(min = 48.dp),
                enabled = !showResult
            ) {
                Text("💡 Hint", fontSize = 14.sp)
            }
        }
    }
}

private fun DrawScope.drawPendulumLines(
    options: List<String>,
    angle: Float,
    selectedOption: String?,
    correctAnswer: String?,
    showResult: Boolean,
    bottomReserve: androidx.compose.ui.unit.Dp = 40.dp
) {
    val centerX = size.width / 2f
    val topY = 24.dp.toPx()
    val lineLength = (size.height - topY - bottomReserve.toPx()).coerceIn(60.dp.toPx(), 220.dp.toPx())

    // Draw pendulum anchor point
    drawCircle(
        color = RvInk,
        radius = 12.dp.toPx(),
        center = Offset(centerX, topY)
    )

    // Calculate positions for each pendulum line
    val angleStep = if (options.size > 1) 30f / (options.size - 1) else 0f
    val startAngle = -15f

    options.forEachIndexed { index, option ->
        val lineAngle = startAngle + (index * angleStep) + angle
        val radians = Math.toRadians(lineAngle.toDouble())

        val endX = centerX + (sin(radians) * lineLength).toFloat()
        val endY = topY + (cos(radians) * lineLength).toFloat()

        // Determine line color based on state
        val lineColor = when {
            showResult && option == correctAnswer -> RvSuccess
            showResult && option == selectedOption && option != correctAnswer -> RvError
            option == selectedOption -> Color(0xFFE91E63)
            else -> Color(0xFFE91E63).copy(alpha = 0.7f)
        }

        // Draw pendulum line
        drawLine(
            color = lineColor,
            start = Offset(centerX, topY),
            end = Offset(endX, endY),
            strokeWidth = 3.dp.toPx(),
            cap = StrokeCap.Round
        )
    }
}

@Composable
fun OptionPendulumCircle(
    option: String,
    size: androidx.compose.ui.unit.Dp = 80.dp,
    isSelected: Boolean,
    isCorrect: Boolean,
    showResult: Boolean,
    onClick: () -> Unit
) {
    val backgroundColor = when {
        showResult && isCorrect -> RvSuccess
        showResult && isSelected && !isCorrect -> RvError
        isSelected -> Color(0xFFE91E63)
        else -> Color(0xFFE91E63).copy(alpha = 0.8f)
    }

    val pulseScale by animateFloatAsState(
        targetValue = if (isSelected && !showResult) 1.1f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(600),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAnimation"
    )

    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(backgroundColor)
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = option,
            color = RvInk,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(4.dp)
        )
    }
}