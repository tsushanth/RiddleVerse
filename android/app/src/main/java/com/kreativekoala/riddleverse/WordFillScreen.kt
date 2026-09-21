package com.kreativekoala.riddleverse
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import com.kreativekoala.riddleverse.ui.theme.RvInk
import com.kreativekoala.riddleverse.ui.theme.RvOnTone
import com.kreativekoala.riddleverse.ui.theme.RvSky
import com.kreativekoala.riddleverse.ui.theme.RvSuccess

@Composable
fun WordFillScreen(
    questionData: WordMatchQuestion,
    difficulty: String = "Medium", // ADD: dynamic difficulty parameter
    timer: String = "1:30",
    onOptionSelected: (String) -> Unit = {},
    onContinue: () -> Unit,
    onCorrectAnswer: () -> Unit,
    onBack: () -> Unit
) {
    val feedbackManager = rememberUnifiedFeedbackManager()
    val currentLevel = feedbackManager.getCurrentLevel()
    val streakInfo = feedbackManager.getStreakInfo()
    val haptics = LocalHapticFeedback.current
    val coroutineScope = rememberCoroutineScope()

    val optionsTop = questionData.options.take(questionData.options.size / 2)
    val optionsBottom = questionData.options.drop(questionData.options.size / 2)

    var selectedAnswer by remember { mutableStateOf<String?>(null) }
    var isAnswered by remember { mutableStateOf(false) }

    val timeRemaining = remember(timer) {
        val parts = timer.split(":")
        if (parts.size == 2) {
            (parts[0].toIntOrNull() ?: 0) * 60 + (parts[1].toIntOrNull() ?: 0)
        } else {
            90
        }
    }

    // Reset state function
    fun resetState() {
        isAnswered = false
        selectedAnswer = null
    }

    // Handle option selection with enhanced feedback
    fun handleOptionSelection(selected: String) {
        if (!isAnswered) {
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            selectedAnswer = selected
            isAnswered = true

            val isCorrect = selected == questionData.correctWord

            feedbackManager.showFeedback(
                puzzleType = "anagram", // See specific types below
                isCorrect = isCorrect,
                userAnswer = selected,
                correctAnswer = questionData.correctWord,
                timeSpent = 0L, // Convert to milliseconds
                difficulty = difficulty,
                timeRemaining = timeRemaining,
                totalTime = 90, // See specific values below
                onComplete = {
                    if (isCorrect) {
                        onCorrectAnswer()
                    }
                    // Always continue regardless of correct/incorrect
                    onContinue()
                }
            )

            onOptionSelected(selected)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.linearGradient(
                        listOf(Color(0xFF001F2E), Color(0xFF03344A))
                    )
                )
                .statusBarsPadding()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Enhanced Top bar with progression system
            EnhancedWordFillTopBar(
                level = currentLevel,
                streakInfo = streakInfo,
                timer = timer,
                lives = 4, // Using fixed value as suggested in original
                onBack = onBack,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(32.dp))

            // TOP options
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                optionsTop.forEach { option ->
                    OptionBox(
                        option = option,
                        isSelected = selectedAnswer == option,
                        isEnabled = !isAnswered
                    ) {
                        handleOptionSelection(option)
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Main sentence box with selected answer (if any)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(RvSky, shape = RoundedCornerShape(12.dp))
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                val displaySentence = questionData.sentence.replace(
                    "____",
                    selectedAnswer ?: "______",
                    ignoreCase = true
                )

                Text(
                    text = displaySentence,
                    color = RvOnTone,
                    fontSize = 20.sp,
                    textAlign = TextAlign.Center,
                    fontWeight = FontWeight.Medium
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            // BOTTOM options
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                optionsBottom.forEach { option ->
                    OptionBox(
                        option = option,
                        isSelected = selectedAnswer == option,
                        isEnabled = !isAnswered
                    ) {
                        handleOptionSelection(option)
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }

        EnhancedUniversalFeedback(feedbackManager)

    }
}

@Composable
fun EnhancedWordFillTopBar(
    level: UserLevel,
    streakInfo: StreakInfo,
    timer: String,
    lives: Int,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        // Enhanced stats row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            // Left: Back button, Pause and Level progression
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.Default.ArrowBack,
                        contentDescription = stringResource(R.string.back),
                        tint = RvInk
                    )
                }

                Icon(Icons.Default.Pause, contentDescription = "Pause", tint = RvInk)

                Column {
                    Text(
                        text = "${stringResource(R.string.level_label)} ${level.level}",
                        color = RvInk,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                    LevelProgressBar(
                        level = level,
                        modifier = Modifier.width(100.dp)
                    )
                }
            }

            // Center: Lives (hearts)
            Row {
                repeat(lives) {
                    Icon(
                        Icons.Default.Favorite,
                        contentDescription = "Heart",
                        tint = RvInk,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            // Right: Timer and Streak
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = timer,
                    color = RvInk,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )

                if (streakInfo.currentStreak > 0) {
                    StreakDisplay(
                        streakInfo = streakInfo,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun OptionBox(
    option: String,
    isSelected: Boolean = false,
    isEnabled: Boolean = true,
    onOptionSelected: (String) -> Unit
) {
    Box(
        modifier = Modifier
            .wrapContentWidth()
            .defaultMinSize(minHeight = 50.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(
                when {
                    isSelected -> RvSuccess // Green when selected
                    isEnabled -> Color(0xFF26C6DA) // Default cyan
                    else -> Color(0xFF26C6DA).copy(alpha = 0.5f) // Disabled state
                }
            )
            .clickable(enabled = isEnabled) { onOptionSelected(option) }
            .padding(horizontal = 24.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = option,
            color = RvOnTone,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
    }
}

data class WordMatchQuestion(
    val sentence: String,    // Sentence with a "____" or placeholder
    val correctWord: String, // Correct answer
    val options: List<String> // List of options (including correct one)
)