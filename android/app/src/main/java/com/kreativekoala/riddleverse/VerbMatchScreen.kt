package com.kreativekoala.riddleverse

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import android.os.Handler
import android.os.Looper
import androidx.compose.ui.platform.LocalContext

@Composable
fun MatchScreen(
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
    onContinue: (String?) -> Unit, // Pass selected answer
    onCorrectAnswer: (Boolean) -> Unit,
    onHint: () -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var remainingTime by remember { mutableIntStateOf(timerSeconds) }
    var currentSelectedOption by remember { mutableStateOf(selectedOption) }
    var isAnswered by remember { mutableStateOf(false) }
    var showResult by remember { mutableStateOf(false) }
    var isCorrect by remember { mutableStateOf(false) }

    // Countdown timer
    LaunchedEffect(Unit) {
        while (remainingTime > 0 && !isAnswered) {
            delay(1000L)
            remainingTime--
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF2C003E)) // Dark purple background
            .statusBarsPadding()
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        // Top Bar
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.Default.ArrowBack,
                    contentDescription = stringResource(R.string.back),
                    tint = Color(0xFFE57373),
                    modifier = Modifier.size(32.dp)
                )
            }

            // Progress indicator
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    "Question $questionNumber of $totalQuestions",
                    color = Color(0xFFE57373),
                    fontSize = 14.sp
                )
                Text(
                    difficulty,
                    color = Color(0xFFE57373).copy(alpha = 0.7f),
                    fontSize = 12.sp
                )
            }

            // Timer
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.Timer,
                    contentDescription = "Timer",
                    tint = Color(0xFFE57373),
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    text = String.format("%d:%02d", remainingTime / 60, remainingTime % 60),
                    color = Color(0xFFE57373),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Spacer(Modifier.height(32.dp))

        // Question Section
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(Color(0xFF4A148C)) // Darker purple for question
                .padding(24.dp)
        ) {
            Column {
                Text(
                    text = "Match the Answer",
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 14.sp,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    text = question,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
            }
        }

        Spacer(Modifier.height(40.dp))

        // Options Section
        if (options.isNotEmpty()) {
            // Arrange options in a grid if more than 3, otherwise in a row
            if (options.size <= 3) {
                // Single row for 3 or fewer options
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    options.forEach { option ->
                        OptionCircle(
                            option = option,
                            isSelected = currentSelectedOption == option,
                            isCorrect = option == correctAnswer,
                            showResult = showResult,
                            onSelect = {
                                if (!isAnswered) {
                                    currentSelectedOption = option
                                    onOptionSelected(option)

                                    // Auto-submit after selection
                                    isAnswered = true
                                    isCorrect = option == correctAnswer
                                    showResult = true

                                    if (isCorrect) {
                                        onCorrectAnswer(remainingTime > 0)
                                    }

                                    // Auto-continue after 2 seconds
                                    Handler(Looper.getMainLooper()).postDelayed({
                                        onContinue(option)
                                    }, 2000)
                                }
                            }
                        )
                    }
                }
            } else {
                // Grid layout for more than 3 options
                val chunkedOptions = options.chunked(2) // 2 options per row
                chunkedOptions.forEach { rowOptions ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        rowOptions.forEach { option ->
                            OptionCircle(
                                option = option,
                                isSelected = currentSelectedOption == option,
                                isCorrect = option == correctAnswer,
                                showResult = showResult,
                                onSelect = {
                                    if (!isAnswered) {
                                        currentSelectedOption = option
                                        onOptionSelected(option)

                                        isAnswered = true
                                        isCorrect = option == correctAnswer
                                        showResult = true

                                        if (isCorrect) {
                                            onCorrectAnswer(remainingTime > 0)
                                        }

                                        Handler(Looper.getMainLooper()).postDelayed({
                                            onContinue(option)
                                        }, 2000)
                                    }
                                }
                            )
                        }
                        // Add spacer if odd number of options in last row
                        if (rowOptions.size == 1) {
                            Spacer(Modifier.weight(1f))
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                }
            }
        }

        Spacer(Modifier.weight(1f))

        // Result display
        if (showResult) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (isCorrect) "🎉 Correct!" else "❌ Wrong! Answer: $correctAnswer",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isCorrect) Color(0xFF4CAF50) else Color(0xFFE57373),
                    textAlign = TextAlign.Center
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        // Bottom Buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            Button(
                onClick = onHint,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF9575CD)),
                shape = RoundedCornerShape(25.dp),
                enabled = !isAnswered
            ) {
                Text("💡 Hint", color = Color.White)
            }

            if (!showResult) {
                Button(
                    onClick = {
                        if (currentSelectedOption != null) {
                            isAnswered = true
                            isCorrect = currentSelectedOption == correctAnswer
                            showResult = true

                            if (isCorrect) {
                                onCorrectAnswer(remainingTime > 0)
                            }

                            Handler(Looper.getMainLooper()).postDelayed({
                                onContinue(currentSelectedOption)
                            }, 2000)
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF64B5F6)),
                    shape = RoundedCornerShape(25.dp),
                    enabled = currentSelectedOption != null && !isAnswered
                ) {
                    Text(stringResource(R.string.submit), color = Color.White)
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        // Instruction text
        Text(
            text = "TAP TO SELECT YOUR ANSWER",
            fontSize = 14.sp,
            color = Color.White.copy(alpha = 0.5f),
            textAlign = TextAlign.Center,
            modifier = Modifier.align(Alignment.CenterHorizontally)
        )

        Spacer(Modifier.height(16.dp))
    }
}

@Composable
fun OptionCircle(
    option: String,
    isSelected: Boolean,
    isCorrect: Boolean,
    showResult: Boolean,
    onSelect: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding(8.dp)
    ) {
        Box(
            modifier = Modifier
                .size(80.dp)
                .clip(CircleShape)
                .background(
                    when {
                        showResult && isCorrect -> Color(0xFF4CAF50)
                        showResult && isSelected && !isCorrect -> Color(0xFFE57373)
                        isSelected -> Color(0xFF81C784)
                        else -> Color(0xFFE57373)
                    }
                )
                .clickable { onSelect() },
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = option,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                textAlign = TextAlign.Center
            )
        }

        Spacer(
            modifier = Modifier
                .width(2.dp)
                .height(20.dp)
                .background(
                    when {
                        showResult && isCorrect -> Color(0xFF4CAF50)
                        showResult && isSelected && !isCorrect -> Color(0xFFE57373)
                        isSelected -> Color(0xFF81C784)
                        else -> Color(0xFFE57373)
                    }
                )
        )
    }
}