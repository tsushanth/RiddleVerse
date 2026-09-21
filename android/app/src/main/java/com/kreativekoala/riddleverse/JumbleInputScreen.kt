package com.kreativekoala.riddleverse

import android.util.Log
import com.kreativekoala.riddleverse.ui.theme.RvDisabled
import com.kreativekoala.riddleverse.ui.theme.RvInkSoft
import com.kreativekoala.riddleverse.ui.theme.RvVioletEdge
import com.kreativekoala.riddleverse.ui.theme.RvViolet
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.BoxWithConstraints
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
import com.kreativekoala.riddleverse.ui.theme.RvInk
import com.kreativekoala.riddleverse.ui.theme.RvOnTone
import com.kreativekoala.riddleverse.ui.theme.RvOutline
import com.kreativekoala.riddleverse.ui.theme.RvSurface
import com.kreativekoala.riddleverse.ui.theme.RvSurfaceRaised

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


    // ---- Action handlers (logic unchanged; shared by every layout mode) ----
    val onSkipClick: () -> Unit = {
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
    }
    val onSubmitClick: () -> Unit = {
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
    }
    val onBackspaceClick: () -> Unit = {
        if (!isAnswered && currentInput.isNotEmpty()) {
            currentInput = currentInput.dropLast(1)
        }
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFB2E5F3))
    ) {
        // Fit-to-screen: HUD on top, play area fills the middle, actions pinned at the bottom.
        val compact = maxHeight < 600.dp
        val landscape = maxWidth > maxHeight
        val contentWidth = minOf(maxWidth, 640.dp.let { if (landscape) 880.dp else it })
        val gutter = if (compact) 12.dp else 16.dp
        val paneWidth = if (landscape) (contentWidth - gutter * 2 - 16.dp) / 2 else contentWidth - gutter * 2
        val columns = 5
        val tile = minOf(if (compact) 48.dp else 56.dp, (paneWidth - 8.dp * (columns - 1)) / columns)
            .coerceAtLeast(44.dp)
        val rows = (shuffledLetters.size + columns - 1) / columns
        val clueSize = if (compact) 18.sp else 20.sp

        val clueBlock: @Composable () -> Unit = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFFDEF6FC), shape = RoundedCornerShape(8.dp))
                    .padding(if (compact) 12.dp else 16.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = clueText,
                    color = RvInk,
                    fontSize = clueSize,
                    textAlign = TextAlign.Center,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        val answerBlock: @Composable () -> Unit = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 56.dp)
                    .background(RvInk, shape = RoundedCornerShape(8.dp))
                    .border(2.dp, RvViolet, RoundedCornerShape(8.dp))
                    .padding(vertical = if (compact) 8.dp else 12.dp, horizontal = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                if (currentInput.isEmpty()) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "_ _ _ _ _ _ _",
                            color = RvOnTone.copy(alpha = 0.8f),
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 4.sp,
                            maxLines = 1
                        )
                        if (!compact) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Tap letters to spell your answer",
                                color = RvOnTone.copy(alpha = 0.8f),
                                fontSize = 14.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                } else {
                    Text(
                        text = currentInput.uppercase(),
                        color = RvOnTone,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 2.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
        val lettersBlock: @Composable () -> Unit = {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                for (rowIndex in 0 until rows) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val startIndex = rowIndex * columns
                        val endIndex = minOf(startIndex + columns, shuffledLetters.size)
                        for (i in startIndex until endIndex) {
                            val letter = shuffledLetters[i]
                            Box(
                                modifier = Modifier
                                    .size(tile)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isAnswered) RvDisabled else RvViolet)
                                    .border(
                                        width = 2.dp,
                                        color = if (isAnswered) Color.Transparent else RvVioletEdge,
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
                                    color = RvOnTone,
                                    fontSize = 24.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }

        Column(
            modifier = Modifier
                .widthIn(max = contentWidth)
                .fillMaxWidth()
                .fillMaxHeight()
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(horizontal = gutter, vertical = if (compact) 4.dp else 12.dp)
        ) {
            // HUD: back, level + progress, timer (single compact row)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBackToHome, modifier = Modifier.size(48.dp)) {
                    Icon(
                        imageVector = Icons.Default.ArrowBack,
                        contentDescription = stringResource(R.string.back),
                        tint = RvInk
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "${stringResource(R.string.level_label)} ${currentLevel.level}",
                            color = RvInk,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (streakInfo.currentStreak > 0) {
                            Text(
                                text = "🔥 ${streakInfo.currentStreak}",
                                color = RvInk,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1
                            )
                        }
                    }
                    Box(
                        modifier = Modifier
                            .width(100.dp)
                            .height(6.dp)
                            .background(RvOutline, RoundedCornerShape(3.dp))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .fillMaxWidth(fraction = (currentLevel.progressPercentage / 100f).coerceIn(0f, 1f))
                                .background(RvViolet, RoundedCornerShape(3.dp))
                        )
                    }
                }
                Text(
                    text = String.format("%d:%02d", remainingTime / 60, remainingTime % 60),
                    color = Color(0xFFB3261E),
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1
                )
            }

            Spacer(Modifier.height(if (compact) 4.dp else 8.dp))

            // Play area: takes all remaining space between the HUD and the pinned action bar.
            // The scroll here is only an invisible last-resort safety net for extreme font/size combos.
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                if (landscape) {
                    Row(
                        modifier = Modifier.fillMaxSize(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            clueBlock()
                            answerBlock()
                        }
                        Box(modifier = Modifier.weight(1f)) { lettersBlock() }
                    }
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(if (compact) 8.dp else 16.dp, Alignment.CenterVertically)
                    ) {
                        clueBlock()
                        answerBlock()
                        if (!compact) {
                            Text(
                                text = "👆 Tap letters below to build your answer",
                                color = RvInk,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Medium,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                        lettersBlock()
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            // Pinned action bar: Skip, Submit (primary), Backspace
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = if (compact) 4.dp else 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = onSkipClick,
                    enabled = !isAnswered,
                    modifier = Modifier.weight(1f).height(56.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = RvSurfaceRaised,
                        disabledContainerColor = RvOutline
                    ),
                    border = BorderStroke(2.dp, RvInkSoft)
                ) {
                    Text(stringResource(R.string.skip), color = RvInk, fontSize = 16.sp, maxLines = 1)
                }

                Button(
                    onClick = onSubmitClick,
                    enabled = !isAnswered && currentInput.isNotEmpty(),
                    modifier = Modifier.weight(1.6f).height(56.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = RvViolet,
                        disabledContainerColor = RvOutline
                    )
                ) {
                    Text(
                        stringResource(R.string.submit),
                        color = if (!isAnswered && currentInput.isNotEmpty()) RvOnTone else RvInkSoft,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                }

                Button(
                    onClick = onBackspaceClick,
                    enabled = !isAnswered && currentInput.isNotEmpty(),
                    modifier = Modifier.width(56.dp).height(56.dp),
                    contentPadding = PaddingValues(0.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = RvSurfaceRaised,
                        disabledContainerColor = RvOutline
                    ),
                    border = BorderStroke(2.dp, RvInkSoft)
                ) {
                    Icon(
                        Icons.Default.Backspace,
                        contentDescription = "Backspace",
                        tint = RvInk
                    )
                }
            }
        }

        EnhancedUniversalFeedback(feedbackManager)
    }
}
