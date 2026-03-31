package com.kreativekoala.riddleverse

import android.annotation.SuppressLint
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import kotlinx.coroutines.delay
import org.json.JSONObject
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.PI

// Data classes for the triangle dot memory game
data class TriangleDotStep(
    val step: Int,
    val redDotPosition: Int, // 0, 1, or 2 for top, bottom-left, bottom-right
    val isFirstStep: Boolean
)

data class TriangleDotInstructions(
    val title: String,
    val description: String,
    val steps: List<String>,
    val tip: String
)

data class TriangleDotPuzzleData(
    val sequence: List<TriangleDotStep>,
    val instructions: TriangleDotInstructions,
    val totalSteps: Int,
    val totalQuestions: Int,
    val difficulty: String
)

data class TriangleDotAnswerData(
    val correctAnswers: List<Int>, // 1 for same, 0 for different
    val totalQuestions: Int,
    val maxScore: Int
)

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun TriangleDotMemoryPuzzleScreen(
    difficulty: String,
    timer: String,
    hearts: Int,
    level: String,
    puzzleData: String,
    correctAnswer: String,
    onSubmitAnswer: (Boolean) -> Unit,
    fetchNextPuzzle: (Int) -> Unit,
    onBack: () -> Unit
) {
    val TAG = "triangledotmemory"
    var skipFirstComparisonDone by remember { mutableStateOf(false) }

    // Generate or parse puzzle data
    val parsedData = remember(puzzleData) {
        generateTriangleDotPuzzleData(difficulty)
    }

    // Generate answer data
    val answerData = remember(parsedData) {
        generateTriangleDotAnswers(parsedData)
    }

    // Game state
    var gameState by remember { mutableStateOf("instructions") } // instructions, playing, completed
    var currentQuestionIndex by remember { mutableStateOf(0) }
    var currentScore by remember { mutableStateOf(0) }
    var showFeedback by remember { mutableStateOf(false) }
    var lastAnswerCorrect by remember { mutableStateOf(false) }
    var userAnswers by remember { mutableStateOf(mutableListOf<Int>()) }

    // Timer state
    var timeRemaining by remember { mutableStateOf(parsedData.totalQuestions * 4) } // 4 seconds per question

    LaunchedEffect(gameState, currentQuestionIndex) {
        if (gameState == "playing" && currentQuestionIndex == 0 && !skipFirstComparisonDone) {
            delay(2000) // Show first triangle for 2 seconds
            currentQuestionIndex++
            skipFirstComparisonDone = true
        }

        if (gameState == "playing" && skipFirstComparisonDone) {
            while (timeRemaining > 0 && gameState == "playing") {
                delay(1000)
                timeRemaining--
            }
            if (timeRemaining <= 0) {
                gameState = "completed"
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF1E3A8A))
            .padding(top = 80.dp, start = 16.dp, end = 16.dp, bottom = 32.dp)
    ) {
        // Header with timer and score
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Pause button
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF3B82F6))
                    .clickable { onBack() }
                    .zIndex(1f),
                contentAlignment = Alignment.Center
            ) {
                Text("||", color = Color.White, fontWeight = FontWeight.Bold)
            }

            // Timer and Score
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.White.copy(alpha = 0.9f))
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = "TIME ${String.format("%d:%02d", timeRemaining / 60, timeRemaining % 60)}",
                        color = Color(0xFF1E3A8A),
                        fontWeight = FontWeight.Bold
                    )
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.White.copy(alpha = 0.9f))
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = "SCORE $currentScore",
                        color = Color(0xFF1E3A8A),
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        when (gameState) {
            "instructions" -> {
                TriangleDotInstructionsScreen(
                    instructions = parsedData.instructions,
                    onStartGame = {
                        gameState = "playing"
                        Log.d(TAG, "Starting triangle dot memory game with ${parsedData.totalQuestions} questions")
                    }
                )
            }

            "playing" -> {
                TriangleDotGameScreen(
                    currentQuestion = currentQuestionIndex + 1,
                    totalQuestions = parsedData.totalQuestions,
                    currentStep = parsedData.sequence[currentQuestionIndex],
                    previousStep = if (currentQuestionIndex == 0) parsedData.sequence[0] else parsedData.sequence[currentQuestionIndex - 1],
                    showFeedback = showFeedback,
                    lastAnswerCorrect = lastAnswerCorrect,
                    onAnswer = { isSame ->
                        if (currentQuestionIndex < 1) return@TriangleDotGameScreen

                        val userAnswer = if (isSame) 1 else 0
                        val correctAnswerIndex = currentQuestionIndex - 1
                        val correctAnswer = answerData.correctAnswers.getOrNull(correctAnswerIndex) ?: 0
                        val isCorrect = userAnswer == correctAnswer

                        Log.d(TAG, "Question ${currentQuestionIndex}: User answered $userAnswer, correct was $correctAnswer")

                        userAnswers.add(userAnswer)
                        lastAnswerCorrect = isCorrect

                        if (isCorrect) {
                            currentScore += 10
                        }

                        showFeedback = true

                        CoroutineScope(Dispatchers.Main).launch {
                            delay(1500)
                            showFeedback = false

                            if (currentQuestionIndex < parsedData.totalQuestions - 1) {
                                currentQuestionIndex++
                            } else {
                                gameState = "completed"
                            }
                        }
                    }
                )
            }

            "completed" -> {
                TriangleDotCompletionScreen(
                    score = currentScore,
                    maxScore = answerData.maxScore,
                    totalQuestions = parsedData.totalQuestions,
                    correctCount = userAnswers.zip(answerData.correctAnswers).count { it.first == it.second },
                    onContinue = {
                        val finalScore = (currentScore.toDouble() / answerData.maxScore.toDouble()) * 100
                        onSubmitAnswer(finalScore >= 60)
                        fetchNextPuzzle(finalScore.toInt())
                    }
                )
            }
        }
    }
}

@Composable
private fun TriangleDotInstructionsScreen(
    instructions: TriangleDotInstructions,
    onStartGame: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = instructions.title,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = instructions.description,
            fontSize = 16.sp,
            color = Color.White.copy(alpha = 0.9f),
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Example triangle
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.9f))
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Example Pattern",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF1E3A8A)
                )

                Spacer(modifier = Modifier.height(16.dp))

                TriangleDotPattern(
                    redDotPosition = 1, // Bottom-left red
                    modifier = Modifier.size(120.dp)
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Red dot at bottom-left",
                    fontSize = 12.sp,
                    color = Color(0xFF1E3A8A)
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Instructions list
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.9f))
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                instructions.steps.forEach { step ->
                    Text(
                        text = "• $step",
                        fontSize = 14.sp,
                        color = Color(0xFF1E3A8A),
                        modifier = Modifier.padding(vertical = 2.dp)
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "💡 ${instructions.tip}",
                    fontSize = 14.sp,
                    color = Color(0xFF1E3A8A),
                    fontWeight = FontWeight.Medium
                )
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        Button(
            onClick = onStartGame,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981))
        ) {
            Text(
                text = stringResource(R.string.start_game),
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        }
    }
}

@SuppressLint("UnusedContentLambdaTargetStateParameter")
@OptIn(ExperimentalAnimationApi::class)
@Composable
private fun TriangleDotGameScreen(
    currentQuestion: Int,
    totalQuestions: Int,
    currentStep: TriangleDotStep,
    previousStep: TriangleDotStep,
    showFeedback: Boolean,
    lastAnswerCorrect: Boolean,
    onAnswer: (Boolean) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Progress indicator
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(Color(0xFF10B981)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = currentQuestion.toString(),
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp
            )
        }

        Spacer(modifier = Modifier.height(32.dp))

        // Triangle pattern with animation
        AnimatedContent(
            targetState = currentQuestion,
            transitionSpec = {
                (fadeIn(tween(300)) + scaleIn(tween(300))) with
                        (fadeOut(tween(300)) + scaleOut(tween(300)))
            }
        ) {
            Box(
                modifier = Modifier
                    .size(240.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.White),
                contentAlignment = Alignment.Center
            ) {
                TriangleDotPattern(
                    redDotPosition = currentStep.redDotPosition,
                    modifier = Modifier.size(160.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        if (currentQuestion >= 2) {
            Text(
                text = "Does this pattern match the\nprevious pattern?",
                fontSize = 18.sp,
                color = Color.White,
                textAlign = TextAlign.Center,
                lineHeight = 24.sp
            )
        }

        Spacer(modifier = Modifier.weight(1f))

        // Feedback animation
        AnimatedVisibility(
            visible = showFeedback,
            enter = fadeIn() + scaleIn(),
            exit = fadeOut() + scaleOut()
        ) {
            Box(
                modifier = Modifier
                    .size(120.dp)
                    .clip(CircleShape)
                    .background(
                        if (lastAnswerCorrect) Color(0xFF10B981) else Color(0xFFEF4444)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (lastAnswerCorrect) "✓" else "✗",
                    fontSize = 60.sp,
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        // Answer buttons
        if (!showFeedback && currentQuestion >= 2) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Button(
                    onClick = { onAnswer(false) },
                    modifier = Modifier
                        .weight(1f)
                        .height(64.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF3B82F6))
                ) {
                    Text("NO", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White)
                }

                Button(
                    onClick = { onAnswer(true) },
                    modifier = Modifier
                        .weight(1f)
                        .height(64.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF3B82F6))
                ) {
                    Text("YES", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White)
                }
            }
        }
    }
}

@Composable
fun TriangleDotPattern(
    redDotPosition: Int,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        // Calculate triangle positions
        val dotSize = 24.dp
        val triangleRadius = 60.dp

        // Position 0: Top
        val topOffset = Offset(0f, -triangleRadius.value)
        // Position 1: Bottom-left
        val bottomLeftOffset = Offset(
            -triangleRadius.value * cos(PI/6).toFloat(),
            triangleRadius.value * sin(PI/6).toFloat()
        )
        // Position 2: Bottom-right
        val bottomRightOffset = Offset(
            triangleRadius.value * cos(PI/6).toFloat(),
            triangleRadius.value * sin(PI/6).toFloat()
        )

        val offsets = listOf(topOffset, bottomLeftOffset, bottomRightOffset)

        offsets.forEachIndexed { index, offset ->
            Box(
                modifier = Modifier
                    .offset(x = offset.x.dp, y = offset.y.dp)
                    .size(dotSize)
                    .clip(CircleShape)
                    .background(
                        if (index == redDotPosition) Color(0xFFEF4444) else Color(0xFFFFA500)
                    )
            )
        }
    }
}

@Composable
private fun TriangleDotCompletionScreen(
    score: Int,
    maxScore: Int,
    totalQuestions: Int,
    correctCount: Int,
    onContinue: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = stringResource(R.string.game_complete),
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(32.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.9f))
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = stringResource(R.string.final_score),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF1E3A8A)
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "$score / $maxScore",
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF10B981)
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "Correct Answers: $correctCount / $totalQuestions",
                    fontSize = 16.sp,
                    color = Color(0xFF1E3A8A)
                )

                val percentage = (correctCount.toDouble() / totalQuestions.toDouble() * 100).toInt()
                Text(
                    text = "Accuracy: $percentage%",
                    fontSize = 16.sp,
                    color = Color(0xFF1E3A8A)
                )
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        Button(
            onClick = onContinue,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981))
        ) {
            Text(
                text = stringResource(R.string.continue_label_caps),
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        }
    }
}

// Helper functions to generate puzzle data
private fun generateTriangleDotPuzzleData(difficulty: String): TriangleDotPuzzleData {
    val totalQuestions = when (difficulty.lowercase()) {
        "easy" -> 8
        "medium" -> 12
        "hard" -> 16
        else -> 12
    }

    val sequence = mutableListOf<TriangleDotStep>()

    // Generate random sequence
    for (i in 0 until totalQuestions) {
        sequence.add(
            TriangleDotStep(
                step = i + 1,
                redDotPosition = (0..2).random(),
                isFirstStep = i == 0
            )
        )
    }

    val instructions = TriangleDotInstructions(
        title = "Triangle Dot Memory",
        description = "Remember the position of the red dot in each triangle pattern and compare it to the previous one.",
        steps = listOf(
            "Watch each triangle pattern carefully",
            "Note the position of the red dot (top, bottom-left, or bottom-right)",
            "Compare the current pattern to the previous one",
            "Tap YES if the red dot is in the same position",
            "Tap NO if the red dot moved to a different position"
        ),
        tip = "Focus on the red dot's position rather than the orange dots"
    )

    return TriangleDotPuzzleData(
        sequence = sequence,
        instructions = instructions,
        totalSteps = totalQuestions,
        totalQuestions = totalQuestions - 1, // -1 because first step is just shown
        difficulty = difficulty
    )
}

private fun generateTriangleDotAnswers(puzzleData: TriangleDotPuzzleData): TriangleDotAnswerData {
    val correctAnswers = mutableListOf<Int>()

    // Generate correct answers based on sequence
    for (i in 1 until puzzleData.sequence.size) {
        val currentPos = puzzleData.sequence[i].redDotPosition
        val previousPos = puzzleData.sequence[i - 1].redDotPosition

        // 1 if same position, 0 if different
        correctAnswers.add(if (currentPos == previousPos) 1 else 0)
    }

    return TriangleDotAnswerData(
        correctAnswers = correctAnswers,
        totalQuestions = puzzleData.totalQuestions,
        maxScore = puzzleData.totalQuestions * 10 // 10 points per correct answer
    )
}

// Data class for offset calculations
private data class Offset(val x: Float, val y: Float)