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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.zIndex
import kotlinx.coroutines.delay
import org.json.JSONObject
import kotlin.random.Random

// Data classes for the color-text matching game
data class ColorTextStep(
    val step: Int,
    val meaningColorName: String,  // Color name shown in top card (meaning)
    val textColorName: String,     // Color name in bottom card
    val textDisplayColor: Color,   // Actual color of the text in bottom card
    val isMatch: Boolean          // Whether meaning matches text color
)

data class ColorTextInstructions(
    val title: String,
    val description: String,
    val steps: List<String>,
    val tip: String
)

data class ColorTextPuzzleData(
    val sequence: List<ColorTextStep>,
    val instructions: ColorTextInstructions,
    val totalSteps: Int,
    val totalQuestions: Int,
    val difficulty: String
)

data class ColorTextAnswerData(
    val correctAnswers: List<Int>, // 1 for match, 0 for no match
    val totalQuestions: Int,
    val maxScore: Int
)

// Color definitions
object GameColors {
    val colorMap = mapOf(
        "red" to RvError,
        "blue" to Color(0xFF3182CE),
        "green" to RvSuccess,
        "yellow" to RvSun,
        "purple" to RvGrape,
        "orange" to Color(0xFFDD6B20),
        "pink" to Color(0xFFD53F8C),
        "brown" to Color(0xFF8B4513),
        "gray" to Color(0xFF718096),
        "black" to RvInk
    )

    val colorNames = colorMap.keys.toList()
}

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun ColorTextMatchingPuzzleScreen(
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
    val TAG = "ColorTextMatching"

    // Generate puzzle data
    val parsedData = remember(puzzleData) {
        generateColorTextPuzzleData(difficulty)
    }

    // Generate answer data
    val answerData = remember(parsedData) {
        generateColorTextAnswers(parsedData)
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

    LaunchedEffect(gameState) {
        if (gameState == "playing") {
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
            .background(Color(0xFF8B5A3C)) // Wood-like brown background
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
                    .background(RvSky)
                    .clickable { onBack() }
                    .zIndex(1f),
                contentAlignment = Alignment.Center
            ) {
                Text("||", color = RvInk, fontWeight = FontWeight.Bold)
            }

            // Timer and Score
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(RvSurface)
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = "TIME ${String.format("%d:%02d", timeRemaining / 60, timeRemaining % 60)}",
                        color = RvInk,
                        fontWeight = FontWeight.Bold
                    )
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(RvSurface)
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = "SCORE $currentScore",
                        color = RvInk,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        when (gameState) {
            "instructions" -> {
                ColorTextInstructionsScreen(
                    instructions = parsedData.instructions,
                    onStartGame = {
                        gameState = "playing"
                        Log.d(TAG, "Starting color-text matching game with ${parsedData.totalQuestions} questions")
                    }
                )
            }

            "playing" -> {
                ColorTextGameScreen(
                    currentQuestion = currentQuestionIndex + 1,
                    totalQuestions = parsedData.totalQuestions,
                    currentStep = parsedData.sequence[currentQuestionIndex],
                    showFeedback = showFeedback,
                    lastAnswerCorrect = lastAnswerCorrect,
                    onAnswer = { isMatch ->
                        val userAnswer = if (isMatch) 1 else 0
                        val correctAnswer = answerData.correctAnswers[currentQuestionIndex]
                        val isCorrect = userAnswer == correctAnswer

                        Log.d(TAG, "Question ${currentQuestionIndex + 1}: User answered $userAnswer, correct was $correctAnswer")

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
                ColorTextCompletionScreen(
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
private fun ColorTextInstructionsScreen(
    instructions: ColorTextInstructions,
    onStartGame: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight()
    ) {
        // Scrollable content
        Column(
            modifier = Modifier
                .weight(1f)
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

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = instructions.description,
                fontSize = 16.sp,
                color = RvInkSoft.copy(alpha = 0.9f),
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(20.dp))

            // Compact Example demonstration
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = RvSurface)
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Example",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = RvInk
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Compact example cards
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Example meaning card
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                text = "meaning",
                                fontSize = 10.sp,
                                color = RvInkSoft,
                                modifier = Modifier
                                    .background(RvInkSoft.copy(alpha = 0.2f), RoundedCornerShape(4.dp))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Card(
                                colors = CardDefaults.cardColors(containerColor = RvSurfaceRaised),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(40.dp)
                            ) {
                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "blue",
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = RvInk,
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        // Example text color card
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.weight(1f)
                        ) {
                            Card(
                                colors = CardDefaults.cardColors(containerColor = RvSurfaceRaised),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(40.dp)
                            ) {
                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "red",
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.Blue, // Blue color text
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "text color",
                                fontSize = 10.sp,
                                color = RvInkSoft,
                                modifier = Modifier
                                    .background(RvInkSoft.copy(alpha = 0.2f), RoundedCornerShape(4.dp))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "Answer: YES (both are blue)",
                        fontSize = 12.sp,
                        color = RvSuccess,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Compact Instructions list
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = RvSurface)
            ) {
                Column(
                    modifier = Modifier.padding(12.dp)
                ) {
                    instructions.steps.take(3).forEach { step -> // Show only first 3 steps
                        Text(
                            text = "• $step",
                            fontSize = 12.sp,
                            color = RvInk,
                            modifier = Modifier.padding(vertical = 1.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "💡 ${instructions.tip}",
                        fontSize = 12.sp,
                        color = RvInk,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            // Add bottom padding to ensure content doesn't touch button
            Spacer(modifier = Modifier.height(16.dp))
        }

        // Fixed button at bottom
        Button(
            onClick = onStartGame,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            colors = ButtonDefaults.buttonColors(containerColor = RvSuccess)
        ) {
            Text(
                text = stringResource(R.string.start_game),
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = RvOnTone
            )
        }
    }
}

@SuppressLint("UnusedContentLambdaTargetStateParameter")
@OptIn(ExperimentalAnimationApi::class)
@Composable
private fun ColorTextGameScreen(
    currentQuestion: Int,
    totalQuestions: Int,
    currentStep: ColorTextStep,
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
                .background(RvSuccess),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = currentQuestion.toString(),
                color = RvInk,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp
            )
        }

        Spacer(modifier = Modifier.height(32.dp))

        // Question text
        Text(
            text = stringResource(R.string.color_match_question),
            fontSize = 18.sp,
            color = RvInk,
            textAlign = TextAlign.Center,
            fontWeight = FontWeight.Medium
        )

        Spacer(modifier = Modifier.height(32.dp))

        // Meaning card (top)
        AnimatedContent(
            targetState = currentQuestion,
            transitionSpec = {
                (fadeIn(tween(300)) + scaleIn(tween(300))) with
                        (fadeOut(tween(300)) + scaleOut(tween(300)))
            }
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "meaning",
                        fontSize = 14.sp,
                        color = RvInkSoft.copy(alpha = 0.8f),
                        modifier = Modifier
                            .background(RvSurface, RoundedCornerShape(4.dp))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Card(
                        colors = CardDefaults.cardColors(containerColor = RvSurfaceRaised),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(80.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = currentStep.meaningColorName,
                                fontSize = 28.sp,
                                fontWeight = FontWeight.Bold,
                                color = RvInk,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        // Text color card (bottom)
        AnimatedContent(
            targetState = currentQuestion,
            transitionSpec = {
                (fadeIn(tween(300)) + scaleIn(tween(300))) with
                        (fadeOut(tween(300)) + scaleOut(tween(300)))
            }
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = RvSurfaceRaised),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(80.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = currentStep.textColorName,
                                fontSize = 28.sp,
                                fontWeight = FontWeight.Bold,
                                color = currentStep.textDisplayColor,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "text color",
                        fontSize = 14.sp,
                        color = RvInkSoft.copy(alpha = 0.8f),
                        modifier = Modifier
                            .background(RvSurface, RoundedCornerShape(4.dp))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }
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
                        if (lastAnswerCorrect) RvSuccess else RvError
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (lastAnswerCorrect) "✓" else "✗",
                    fontSize = 60.sp,
                    color = RvInk,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        // Answer buttons
        if (!showFeedback) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Button(
                    onClick = { onAnswer(false) },
                    modifier = Modifier
                        .weight(1f)
                        .height(64.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = RvSky)
                ) {
                    Text("NO", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = RvOnTone)
                }

                Button(
                    onClick = { onAnswer(true) },
                    modifier = Modifier
                        .weight(1f)
                        .height(64.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = RvSky)
                ) {
                    Text("YES", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = RvOnTone)
                }
            }
        }
    }
}

@Composable
private fun ColorTextCompletionScreen(
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
            color = RvInk,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(32.dp))

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
                    color = RvSuccess
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "${stringResource(R.string.correct_answers)}: $correctCount / $totalQuestions",
                    fontSize = 16.sp,
                    color = RvInk
                )

                val percentage = (correctCount.toDouble() / totalQuestions.toDouble() * 100).toInt()
                Text(
                    text = "${stringResource(R.string.accuracy)}: $percentage%",
                    fontSize = 16.sp,
                    color = RvInk
                )
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        Button(
            onClick = onContinue,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            colors = ButtonDefaults.buttonColors(containerColor = RvSuccess)
        ) {
            Text(
                text = stringResource(R.string.continue_label_caps),
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = RvOnTone
            )
        }
    }
}

// Helper functions to generate puzzle data
private fun generateColorTextPuzzleData(difficulty: String): ColorTextPuzzleData {
    val totalQuestions = when (difficulty.lowercase()) {
        "easy" -> 10
        "medium" -> 15
        "hard" -> 20
        else -> 15
    }

    val sequence = mutableListOf<ColorTextStep>()
    val colorNames = GameColors.colorNames

    // Generate questions with a mix of matches and non-matches
    repeat(totalQuestions) { i ->
        val meaningColorName = colorNames.random()
        val textColorName = colorNames.random()

        // Decide if this should be a match or not (roughly 50/50 split)
        val shouldMatch = Random.nextBoolean()

        val actualTextDisplayColor = if (shouldMatch) {
            // Make it match: text display color should match the meaning
            GameColors.colorMap[meaningColorName] ?: RvInk
        } else {
            // Make it not match: use a different color for text display
            val differentColor = colorNames.filter { it != meaningColorName }.random()
            GameColors.colorMap[differentColor] ?: RvInk
        }

        sequence.add(
            ColorTextStep(
                step = i + 1,
                meaningColorName = meaningColorName,
                textColorName = textColorName,
                textDisplayColor = actualTextDisplayColor,
                isMatch = shouldMatch
            )
        )
    }

    val instructions = ColorTextInstructions(
        title = "Color-Text Matching",
        description = "Compare the color name meaning with the actual text color and decide if they match.",
        steps = listOf(
            "Look at the top card - this shows the color name meaning",
            "Look at the bottom card - focus on the actual COLOR of the text, not what it says",
            "Compare: Does the meaning match the text color?",
            "Tap YES if the meaning matches the text color",
            "Tap NO if they don't match"
        ),
        tip = "Focus on the actual color of the text in the bottom card, not what the word says!"
    )

    return ColorTextPuzzleData(
        sequence = sequence,
        instructions = instructions,
        totalSteps = totalQuestions,
        totalQuestions = totalQuestions,
        difficulty = difficulty
    )
}

private fun generateColorTextAnswers(puzzleData: ColorTextPuzzleData): ColorTextAnswerData {
    val correctAnswers = puzzleData.sequence.map { step ->
        if (step.isMatch) 1 else 0
    }

    return ColorTextAnswerData(
        correctAnswers = correctAnswers,
        totalQuestions = puzzleData.totalQuestions,
        maxScore = puzzleData.totalQuestions * 10 // 10 points per correct answer
    )
}