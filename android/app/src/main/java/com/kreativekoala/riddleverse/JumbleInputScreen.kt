package com.kreativekoala.riddleverse

import android.util.Log
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Backspace
import androidx.compose.material.icons.filled.Flight
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

@Composable
fun JumbleInputScreen(
    clueText: String = "to receive someone gladly",
    targetWord: String = "welcome",
    shuffledLetters: List<Char> = listOf('v', 'o', 'e', 'v', 'q', 'm', 't', 'b', 'y', 'c'),
    score: Int = 0,
    timerSeconds: Int = 90,
    onSubmit: (String, Int) -> Unit = { s: String, i: Int -> },
    onSkip: () -> Unit = {},
    onBackToHome: () -> Unit
) {
    var currentInput by remember { mutableStateOf("") }
    var remainingTime by remember { mutableStateOf(timerSeconds) }
    var isAnswered by remember { mutableStateOf(false) }
    var currentScore by remember { mutableStateOf(score) }
    var streak by remember { mutableStateOf(0) }

    val feedbackManager = rememberUnifiedFeedbackManager()
    val currentLevel = feedbackManager.getCurrentLevel()
    val streakInfo = feedbackManager.getStreakInfo()
    val haptics = LocalHapticFeedback.current

    // Reset state function
    fun resetState() {
        isAnswered = false
        currentInput = ""
    }

    // Timer countdown
    LaunchedEffect(Unit) {
        while (remainingTime > 0 && !isAnswered) {
            delay(1000L)
            remainingTime--
        }
        // Time's up - auto submit
        if (remainingTime <= 0 && !isAnswered) {
            isAnswered = true
            feedbackManager.showFeedback(
                puzzleType = "anagram", // See specific types below
                isCorrect = false,
                userAnswer = currentInput.ifEmpty { "No answer" }, // Keep existing user answer logic
                correctAnswer = targetWord, // Keep existing correct answer logic
                timeSpent = 90 * 1000L, // Convert to milliseconds
                difficulty = "Easy",
                timeRemaining = 0,
                totalTime = 90, // See specific values below
                onComplete = {
                    onSubmit(currentInput, 10)
                }
            )
        }
    }


    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFB2E5F3))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(16.dp)
        ) {
            // Back button
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBackToHome) {
                    Icon(
                        imageVector = Icons.Default.ArrowBack,
                        contentDescription = stringResource(R.string.back),
                        tint = Color.Black
                    )
                }
            }

            // Top Bar with score and timer
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    // Level text with better contrast
                    Text(
                        text = "${stringResource(R.string.level_label)} ${currentLevel.level}",
                        color = Color(0xFF243447), // Dark blue/gray for good contrast on light blue background
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )

                    // Custom level progress bar with better colors
                    Box(
                        modifier = Modifier
                            .width(100.dp)
                            .height(6.dp)
                            .background(
                                Color.White.copy(alpha = 0.3f),
                                RoundedCornerShape(3.dp)
                            )
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .fillMaxWidth(fraction = (currentLevel.progressPercentage / 100f).coerceIn(0f, 1f))
                                .background(
                                    Color(0xFF264653), // Dark teal for good contrast
                                    RoundedCornerShape(3.dp)
                                )
                        )
                    }

                    // Show streak if exists with better colors
                    if (streakInfo.currentStreak > 0) {
                        Text(
                            text = "🔥 ${streakInfo.currentStreak}",
                            color = Color(0xFF243447), // Same dark color for consistency
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                Text(
                    text = String.format("%d:%02d", remainingTime / 60, remainingTime % 60),
                    color = Color(0xFFEF6C6C),
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(Modifier.height(32.dp))

            // Clue box
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFFDEF6FC), shape = RoundedCornerShape(8.dp))
                    .padding(16.dp)
            ) {
                Text(
                    text = clueText,
                    color = Color(0xFF243447),
                    fontSize = 20.sp,
                    textAlign = TextAlign.Center
                )
            }

            Spacer(Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Flight, // or use a finger/touch icon
                    contentDescription = null,
                    tint = Color(0xFF243447),
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Build your answer by tapping letters",
                    color = Color(0xFF243447),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                )
            }

            // Current assembled word
            // Replace the current assembled word box with:
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF51A7C1), shape = RoundedCornerShape(8.dp))
                    .border(2.dp, Color.White, RoundedCornerShape(8.dp))
                    .padding(vertical = 16.dp),
                contentAlignment = Alignment.Center
            ) {
                if (currentInput.isEmpty()) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "_ _ _ _ _ _ _",
                            color = Color.White.copy(alpha = 0.7f),
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 4.sp
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Tap letters to spell your answer",
                            color = Color.White.copy(alpha = 0.6f),
                            fontSize = 12.sp
                        )
                    }
                } else {
                    Text(
                        text = currentInput.uppercase(),
                        color = Color.White,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 2.sp
                    )
                }
            }

            Spacer(Modifier.height(16.dp))


            Text(
                text = "👆 Tap letters below to build your answer",
                color = Color(0xFF243447),
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(16.dp))

            val infiniteTransition = rememberInfiniteTransition(label = "letter-hint")
            val scale by infiniteTransition.animateFloat(
                initialValue = 1f,
                targetValue = 1.05f,
                animationSpec = infiniteRepeatable(
                    animation = tween(1000),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "scale"
            )

            var showHint by remember { mutableStateOf(true) }

            LaunchedEffect(Unit) {
                delay(3000) // Show hint for 3 seconds
                showHint = false
            }
            // Letter grid (shuffled letters)
            val columns = 5
            val rows = (shuffledLetters.size + columns - 1) / columns


            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                for (rowIndex in 0 until rows) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val startIndex = rowIndex * columns
                        val endIndex = minOf(startIndex + columns, shuffledLetters.size)
                        for (i in startIndex until endIndex) {
                            val letter = shuffledLetters[i]
                            Box(
                                modifier = Modifier
                                    .size(56.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(
                                        if (isAnswered) Color.Gray else Color(0xFF264653)
                                    )
                                    .border(
                                        width = 2.dp,
                                        color = if (isAnswered) Color.Transparent else Color(0xFF51A7C1),
                                        shape = RoundedCornerShape(8.dp)
                                    )
                                    .clickable(enabled = !isAnswered) {
                                        currentInput += letter
                                        haptics.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = letter.toString().uppercase(),
                                    color = Color.White,
                                    fontSize = 24.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(24.dp))

            // Bottom buttons: Skip, Submit, Backspace
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Button(
                    onClick = {
                        if (!isAnswered) {
                            println("🔥 Jumble Skip clicked")
                            isAnswered = true
                            feedbackManager.showFeedback(
                                puzzleType = "anagram", // See specific types below
                                isCorrect = false,
                                userAnswer = "Skipped", // Keep existing user answer logic
                                correctAnswer = targetWord, // Keep existing correct answer logic
                                timeSpent = 90 * 1000L, // Convert to milliseconds
                                difficulty = "Easy",
                                timeRemaining = 0,
                                totalTime = 90, // See specific values below
                                onComplete = {
                                    onSkip()
                                }
                            )
                        }
                    },
                    enabled = !isAnswered,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.White,
                        disabledContainerColor = Color.Gray.copy(alpha = 0.3f)
                    ),
                    border = BorderStroke(1.dp, Color.Gray)
                ) {
                    Text(stringResource(R.string.skip), color = Color.Gray)
                }

                Button(
                    onClick = {
                        if (!isAnswered && currentInput.isNotEmpty()) {
                            println("🔥 Jumble Submit clicked - input: $currentInput")
                            isAnswered = true

                            val isCorrect = currentInput.equals(targetWord, ignoreCase = true)
                            println("🎯 Jumble Answer: $currentInput, Correct: $isCorrect, Expected: $targetWord")

                            // Update streak and score
                            if (isCorrect) {
                                streak++
                                val timeBonus = if (remainingTime > 30) 15 else 10
                                currentScore += timeBonus
                            } else {
                                streak = 0
                            }

                            println("📊 Jumble Showing feedback - isCorrect: $isCorrect")

                            feedbackManager.showFeedback(
                                puzzleType = "anagram", // See specific types below
                                isCorrect = isCorrect,
                                userAnswer = currentInput, // Keep existing user answer logic
                                correctAnswer = targetWord, // Keep existing correct answer logic
                                timeSpent = 90 * 1000L, // Convert to milliseconds
                                difficulty = "Medium",
                                timeRemaining = 0,
                                totalTime = 90, // See specific values below
                                onComplete = {
                                    Log.d("JumbleInput", "🎯 Feedback onComplete called - calling onSubmit")
                                    onSubmit(currentInput, 10)
                                    Log.d("JumbleInput", "🎯 onSubmit finished")
                                }
                            )
                        }
                    },
                    enabled = !isAnswered && currentInput.isNotEmpty(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFDEF6FC),
                        disabledContainerColor = Color.Gray.copy(alpha = 0.3f)
                    )
                ) {
                    Text(
                        stringResource(R.string.submit),
                        color = Color(0xFF243447),
                        fontWeight = FontWeight.Bold
                    )
                }

                Button(
                    onClick = {
                        if (!isAnswered && currentInput.isNotEmpty()) {
                            currentInput = currentInput.dropLast(1)
                        }
                    },
                    enabled = !isAnswered && currentInput.isNotEmpty(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.White,
                        disabledContainerColor = Color.Gray.copy(alpha = 0.3f)
                    ),
                    border = BorderStroke(1.dp, Color.Gray)
                ) {
                    Icon(
                        Icons.Default.Backspace,
                        contentDescription = "Backspace",
                        tint = Color.Gray
                    )
                }
            }
        }

        EnhancedUniversalFeedback(feedbackManager)

    }
}