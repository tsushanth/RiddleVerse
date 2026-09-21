package com.kreativekoala.riddleverse

import com.kreativekoala.riddleverse.ui.theme.*
import android.annotation.SuppressLint
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.platform.testTag
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

    BoxWithConstraints(modifier = Modifier.fillMaxSize().background(RvCanvas)) {
    Column(
        modifier = Modifier
            .align(Alignment.TopCenter)
            .widthIn(max = 720.dp)
            .fillMaxSize()
            .statusBarsPadding()
            .padding(horizontal = 16.dp)
            .padding(bottom = 8.dp)
    ) {
        // HUD: back, timer and score in one compact row.
        GroupECompactHud(
            timer = String.format("%d:%02d", timeRemaining / 60, timeRemaining % 60),
            onBack = onBack,
            subtitle = "${stringResource(R.string.score_label)} $currentScore",
            urgent = timeRemaining <= 10 && gameState == "playing"
        )

        Spacer(modifier = Modifier.height(8.dp))

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
}

@Composable
private fun TriangleDotInstructionsScreen(
    instructions: TriangleDotInstructions,
    onStartGame: () -> Unit
) {
    GroupETriangleInstructionsBody(instructions = instructions, extra = null, onStart = onStartGame)
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
    GroupETriangleGameBody(
        currentQuestion = currentQuestion,
        totalQuestions = totalQuestions,
        redDotPosition = currentStep.redDotPosition,
        showFeedback = showFeedback,
        lastAnswerCorrect = lastAnswerCorrect,
        extraLabel = null,
        onAnswer = onAnswer
    )
}

/**
 * Scales with the box it is given (designed for 160dp): dot size and triangle radius follow the
 * available size so the pattern is never clipped and can grow on tablets.
 */
@Composable
fun TriangleDotPattern(
    redDotPosition: Int,
    modifier: Modifier = Modifier
) {
    BoxWithConstraints(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        val side = if (maxWidth.value.isFinite() && maxHeight.value.isFinite()) minOf(maxWidth, maxHeight) else 160.dp
        val k = side.value / 160f
        val dotSize = (24f * k).dp
        val triangleRadius = 60f * k

        // Position 0: Top
        val topOffset = Offset(0f, -triangleRadius)
        // Position 1: Bottom-left
        val bottomLeftOffset = Offset(
            -triangleRadius * cos(PI/6).toFloat(),
            triangleRadius * sin(PI/6).toFloat()
        )
        // Position 2: Bottom-right
        val bottomRightOffset = Offset(
            triangleRadius * cos(PI/6).toFloat(),
            triangleRadius * sin(PI/6).toFloat()
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
    Column(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = stringResource(R.string.game_complete),
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = RvInk,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(24.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = RvSurface)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = stringResource(R.string.final_score),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = RvInk
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "$score / $maxScore",
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Bold,
                        color = RvInk
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = "Correct Answers: $correctCount / $totalQuestions",
                        fontSize = 16.sp,
                        color = RvInk
                    )

                    val percentage = (correctCount.toDouble() / totalQuestions.toDouble() * 100).toInt()
                    Text(
                        text = "Accuracy: $percentage%",
                        fontSize = 16.sp,
                        color = RvInk
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        GroupEPrimaryButton(
            text = stringResource(R.string.continue_label_caps),
            onClick = onContinue,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

// ---------------------------------------------------------------------------------------------
// Shared bodies (also used by AdaptiveTriangleDotMemoryPuzzleScreen)
// ---------------------------------------------------------------------------------------------

/** Instructions: content may scroll (non-game copy) but the Start button is pinned and always visible. */
@Composable
internal fun GroupETriangleInstructionsBody(
    instructions: TriangleDotInstructions,
    extra: String?,
    onStart: () -> Unit
) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val short = maxHeight < 460.dp
        Column(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = instructions.title,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = RvInk,
                    textAlign = TextAlign.Center
                )

                if (!short) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = instructions.description,
                        fontSize = 16.sp,
                        color = RvInkSoft,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Example triangle
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = RvSurface)
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            TriangleDotPattern(
                                redDotPosition = 1, // Bottom-left red
                                modifier = Modifier.size(96.dp)
                            )
                            Column {
                                Text(
                                    text = "Example Pattern",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = RvInk
                                )
                                Text(
                                    text = "Red dot at bottom-left",
                                    fontSize = 14.sp,
                                    color = RvInk
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Instructions list
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = RvSurface)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        instructions.steps.forEach { step ->
                            Text(
                                text = "\u2022 $step",
                                fontSize = 14.sp,
                                color = RvInk,
                                modifier = Modifier.padding(vertical = 2.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = "\uD83D\uDCA1 ${instructions.tip}",
                            fontSize = 14.sp,
                            color = RvInk,
                            fontWeight = FontWeight.Medium
                        )

                        if (extra != null) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = extra,
                                fontSize = 14.sp,
                                color = RvInkSoft,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            GroupEPrimaryButton(
                text = stringResource(R.string.start_game),
                onClick = onStart,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

/**
 * Game body: pattern (dominant, square, sized from the space left), the question, and the YES/NO
 * buttons pinned at the bottom (side by side with the pattern on wide screens). No scrolling.
 */
@Composable
internal fun GroupETriangleGameBody(
    currentQuestion: Int,
    totalQuestions: Int,
    redDotPosition: Int,
    showFeedback: Boolean,
    lastAnswerCorrect: Boolean,
    extraLabel: String?,
    onAnswer: (Boolean) -> Unit
) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val wide = maxWidth > maxHeight
        val canAnswer = currentQuestion >= 2 && !showFeedback

        val progress: @Composable () -> Unit = {
            GroupEFontCap {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "$currentQuestion / $totalQuestions",
                        color = RvInk,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                    LinearProgressIndicator(
                        progress = (currentQuestion.toFloat() / totalQuestions.coerceAtLeast(1)).coerceIn(0f, 1f),
                        modifier = Modifier
                            .weight(1f)
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp)),
                        color = RvSuccess,
                        trackColor = RvOutline
                    )
                    if (extraLabel != null) {
                        Text(text = extraLabel, color = RvInkSoft, fontSize = 14.sp, maxLines = 1)
                    }
                }
            }
        }

        val question: @Composable () -> Unit = {
            GroupEFontCap {
                Text(
                    text = "Does this pattern match the\nprevious pattern?",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Medium,
                    color = if (currentQuestion >= 2) RvInk else Color.Transparent,
                    textAlign = TextAlign.Center,
                    lineHeight = 24.sp,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        val buttons: @Composable (Modifier) -> Unit = { m ->
            Row(modifier = m, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                GroupEAnswerButton("NO", canAnswer, { onAnswer(false) }, Modifier.weight(1f))
                GroupEAnswerButton("YES", canAnswer, { onAnswer(true) }, Modifier.weight(1f))
            }
        }

        val pattern: @Composable (Modifier) -> Unit = { m ->
            BoxWithConstraints(modifier = m, contentAlignment = Alignment.Center) {
                val side = minOf(maxWidth, maxHeight).coerceAtMost(360.dp)
                Box(
                    modifier = Modifier
                        .size(side)
                        .testTag("triangle_pattern")
                        .clip(RoundedCornerShape(16.dp))
                        .background(RvSurfaceRaised)
                        .border(2.dp, RvOutline, RoundedCornerShape(16.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    TriangleDotPattern(
                        redDotPosition = redDotPosition,
                        modifier = Modifier.size(side * 0.68f)
                    )
                    // Result mark: shape + colour, so correctness is not colour-only.
                    androidx.compose.animation.AnimatedVisibility(
                        visible = showFeedback,
                        enter = fadeIn() + scaleIn(),
                        exit = fadeOut() + scaleOut()
                    ) {
                        Box(
                            modifier = Modifier
                                .size((side * 0.4f).coerceAtMost(120.dp))
                                .clip(CircleShape)
                                .background(if (lastAnswerCorrect) RvSuccess else RvError),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = if (lastAnswerCorrect) "\u2713" else "\u2717",
                                fontSize = 48.sp,
                                color = RvInk,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }

        if (wide) {
            Row(
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                pattern(Modifier.weight(1f).fillMaxHeight())
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    progress()
                    question()
                    buttons(Modifier.fillMaxWidth())
                }
            }
        } else {
            Column(modifier = Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
                progress()
                pattern(Modifier.weight(1f).fillMaxWidth())
                Spacer(modifier = Modifier.height(8.dp))
                question()
                Spacer(modifier = Modifier.height(8.dp))
                buttons(Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
private fun GroupEAnswerButton(text: String, enabled: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.height(64.dp),
        shape = RoundedCornerShape(18.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = RvViolet,
            contentColor = RvOnTone,
            disabledContainerColor = RvDisabled,
            disabledContentColor = RvInkSoft
        )
    ) {
        Text(text, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1)
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