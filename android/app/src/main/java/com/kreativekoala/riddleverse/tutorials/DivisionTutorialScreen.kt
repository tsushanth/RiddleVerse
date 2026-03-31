package com.kreativekoala.riddleverse

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.sp

/**
 * Interactive Division Tutorial Screen
 */
@Composable
fun DivisionTutorialScreen(
    onTutorialComplete: () -> Unit,
    onTutorialSkipped: () -> Unit,
    onBack: () -> Unit
) {
    val tutorialManager = remember { DivisionTutorialManager() }
    val steps = tutorialManager.getTutorialSteps()
    var currentStepIndex by remember { mutableStateOf(0) }
    var tutorialState by remember { mutableStateOf(TutorialState.ACTIVE) }

    // Tutorial puzzle state
    var valueButtons by remember {
        mutableStateOf(
            listOf(
                ValueButton(50, 0, 5),
                ValueButton(10, 0, 20),
                ValueButton(5, 0, 10),
                ValueButton(1, 0, 50)
            )
        )
    }
    var currentTotal by remember { mutableStateOf(0) }
    var isAnswered by remember { mutableStateOf(false) }

    // Calculate current total
    LaunchedEffect(valueButtons) {
        currentTotal = valueButtons.sumOf { it.value * it.count }
    }

    val haptics = LocalHapticFeedback.current
    val currentStep = steps.getOrNull(currentStepIndex)

    // Handle tutorial completion
    LaunchedEffect(tutorialState) {
        when (tutorialState) {
            TutorialState.COMPLETED -> onTutorialComplete()
            TutorialState.SKIPPED -> onTutorialSkipped()
            else -> {}
        }
    }

    fun advanceStep() {
        if (currentStepIndex < steps.size - 1) {
            currentStepIndex++
        } else {
            tutorialState = TutorialState.COMPLETED
        }
    }

    // Tutorial action handler
    // Tutorial action handler with improved logic and logging
    fun handleTutorialAction(action: String) {
        val step = currentStep
        Log.d("TutorialDebug", "🎯 Action: $action, Step: ${step?.id}, Expected: ${step?.expectedAction}, Total: $currentTotal")

        if (step?.interactionRequired == true && step.expectedAction != null) {
            when (step.expectedAction) {
                "add_10" -> {
                    // Step 4: First time adding 10
                    if (action == "add_10") {
                        Log.d("TutorialDebug", "✅ Step 4 completed - first 10 added")
                        advanceStep()
                    }
                }
                "reach_100" -> {
                    // Step 5: Keep adding until 100
                    if (action == "add_10" && currentTotal >= 90) {
                        Log.d("TutorialDebug", "✅ Step 5 completed - reached 100")
                        advanceStep()
                    } else if (action == "add_10") {
                        Log.d("TutorialDebug", "⏳ Step 5 in progress - total: $currentTotal, need: 100")
                    }
                }
                "add_ones" -> {
                    // Step 6: Add ones until 109
                    if (action == "add_ones" && currentTotal >= 108) {
                        Log.d("TutorialDebug", "✅ Step 6 completed - reached 109")
                        advanceStep()
                    } else if (action == "add_ones") {
                        Log.d("TutorialDebug", "⏳ Step 6 in progress - total: $currentTotal, need: 109")
                    }
                }
                "submit" -> {
                    // Step 8: Submit the answer
                    if (action == "submit" && currentTotal == 109) {
                        Log.d("TutorialDebug", "✅ Step 8 completed - answer submitted")
                        advanceStep()
                    }
                }
            }
        }
    }



    fun skipTutorial() {
        tutorialState = TutorialState.SKIPPED
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // Tutorial Division Puzzle Screen
        TutorialDivisionPuzzleContent(
            currentStep = currentStep,
            valueButtons = valueButtons,
            currentTotal = currentTotal,
            isAnswered = isAnswered,
            onValueButtonClick = { index, increment ->
                if (!isAnswered) {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    valueButtons = valueButtons.mapIndexed { i, button ->
                        if (i == index) {
                            val newCount = if (increment) {
                                (button.count + 1).coerceAtMost(button.maxCount)
                            } else {
                                (button.count - 1).coerceAtLeast(0)
                            }
                            button.copy(count = newCount)
                        } else {
                            button
                        }
                    }

                    // Handle tutorial actions
                    if (increment) {
                        when {
                            valueButtons[index].value == 10 -> handleTutorialAction("add_10")
                            valueButtons[index].value == 1 -> handleTutorialAction("add_ones")
                        }
                    }
                }
            },
            onSubmit = {
                if (!isAnswered && currentTotal == 109) {
                    isAnswered = true
                    handleTutorialAction("submit")
                }
            },
            onBack = onBack
        )

        // Tutorial overlay
        if (tutorialState == TutorialState.ACTIVE && currentStep != null) {
            if (currentStep.interactionRequired) {
                // For interactive steps, use minimal overlay
                MinimalInteractiveOverlay(
                    currentStep = currentStep,
                    totalSteps = steps.size,
                    currentStepNumber = currentStepIndex + 1,
                    onNext = { advanceStep() },
                    onSkip = { skipTutorial() }
                )
            } else {
                // For non-interactive steps, use full overlay
                SmartTutorialOverlay(
                    currentStep = currentStep,
                    totalSteps = steps.size,
                    currentStepNumber = currentStepIndex + 1,
                    onNext = { advanceStep() },
                    onSkip = { skipTutorial() }
                )
            }

        }
    }
}

/**
 * Tutorial-specific division puzzle content
 */
@Composable
fun TutorialDivisionPuzzleContent(
    currentStep: TutorialStep?,
    valueButtons: List<ValueButton>,
    currentTotal: Int,
    isAnswered: Boolean,
    onValueButtonClick: (Int, Boolean) -> Unit,
    onSubmit: () -> Unit,
    onBack: () -> Unit
) {
    val screenHeight = LocalConfiguration.current.screenHeightDp.dp

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF5F5F5))
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            Spacer(modifier = Modifier.height(32.dp))

            // Top Bar
            TutorialTopBar(
                onBack = onBack,
                modifier = Modifier.padding(16.dp)
            )

            Spacer(modifier = Modifier.height(60.dp))

            // Division problem with tutorial highlight
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 40.dp)
                    .then(
                        if (currentStep?.targetComponent == "problem") {
                            Modifier.tutorialHighlight()
                        } else Modifier
                    )
            ) {
                TutorialDivisionProblem()
            }

            Spacer(modifier = Modifier.height(80.dp))

            // Answer display circle with tutorial highlight
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(screenHeight * 0.22f)
                    .then(
                        if (currentStep?.targetComponent == "answerCircle") {
                            Modifier.tutorialHighlight()
                        } else Modifier
                    )
            ) {
                TutorialAnswerCircle(
                    currentTotal = currentTotal,
                    onSubmit = onSubmit
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            // Value input buttons with tutorial highlight
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 40.dp)
                    .then(
                        if (currentStep?.targetComponent == "valueButtons") {
                            Modifier.tutorialHighlight()
                        } else Modifier
                    )
            ) {
                TutorialValueInputSection(
                    valueButtons = valueButtons,
                    currentStep = currentStep,
                    onValueButtonClick = onValueButtonClick
                )
            }
        }
    }
}

// Helper composables for the tutorial
@Composable
fun TutorialTopBar(
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.statusBarsPadding().fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(
            onClick = onBack,
            modifier = Modifier.size(40.dp)
        ) {
            Icon(
                imageVector = Icons.Default.ArrowBack,
                contentDescription = stringResource(R.string.back),
                tint = Color.Gray,
                modifier = Modifier.size(24.dp)
            )
        }

        Spacer(modifier = Modifier.width(16.dp))

        Column {
            Text(
                text = "Tutorial",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = Color.Gray
            )
            Text(
                text = "Learn Division Puzzles",
                fontSize = 14.sp,
                color = Color.Gray
            )
        }
    }
}

@Composable
fun TutorialDivisionProblem() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "872",
            fontSize = 64.sp,
            fontWeight = FontWeight.Bold,
            color = Color.Gray
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "DIVIDED BY 8",
            fontSize = 18.sp,
            fontWeight = FontWeight.Medium,
            color = Color.Gray,
            letterSpacing = 1.sp
        )
    }
}

@Composable
fun TutorialAnswerCircle(
    currentTotal: Int,
    onSubmit: () -> Unit
) {
    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center
    ) {
        // 3D Shadow effect
        Box(
            modifier = Modifier
                .size(180.dp)
                .offset(x = 8.dp, y = 8.dp)
                .background(
                    Color(0xFFD4AF37).copy(alpha = 0.3f),
                    CircleShape
                )
        )

        // Main circle
        Box(
            modifier = Modifier
                .size(180.dp)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            Color(0xFFFFD700),
                            Color(0xFFD4AF37)
                        )
                    ),
                    CircleShape
                )
                .clickable { onSubmit() }
                .shadow(8.dp, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = currentTotal.toString(),
                    fontSize = 48.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = stringResource(R.string.submit).uppercase(),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    letterSpacing = 1.sp
                )
            }
        }
    }
}

@Composable
fun TutorialValueInputSection(
    valueButtons: List<ValueButton>,
    currentStep: TutorialStep?,
    onValueButtonClick: (Int, Boolean) -> Unit
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        // Value buttons row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            valueButtons.forEachIndexed { index, button ->
                TutorialValueInputButton(
                    value = button.value,
                    count = button.count,
                    maxCount = button.maxCount,
                    onIncrement = { onValueButtonClick(index, true) },
                    onDecrement = { onValueButtonClick(index, false) },
                    isHighlighted = currentStep?.highlightArea == button.value.toString()
                )
            }
        }

        // Value labels row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            valueButtons.forEach { button ->
                Text(
                    text = button.value.toString(),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.Gray,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.width(60.dp)
                )
            }
        }
    }
}

@Composable
fun TutorialValueInputButton(
    value: Int,
    count: Int,
    maxCount: Int,
    onIncrement: () -> Unit,
    onDecrement: () -> Unit,
    isHighlighted: Boolean = false
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Increment button with highlight
        Box(
            modifier = Modifier
                .width(60.dp)
                .height(30.dp)
                .background(
                    if (count < maxCount) Color(0xFFD4AF37) else Color.Gray.copy(alpha = 0.3f),
                    RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp)
                )
                .then(
                    if (isHighlighted) {
                        Modifier.border(
                            3.dp,
                            Color(0xFF7B1FA2),
                            RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp)
                        )
                    } else Modifier
                )
                .clickable(enabled = count < maxCount) { onIncrement() },
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "+",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        }

        // Count display
        Box(
            modifier = Modifier
                .width(60.dp)
                .height(40.dp)
                .border(
                    2.dp,
                    Color(0xFFD4AF37),
                    RoundedCornerShape(4.dp)
                )
                .background(
                    if (count > 0) Color(0xFFD4AF37).copy(alpha = 0.2f) else Color.Transparent,
                    RoundedCornerShape(4.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = count.toString(),
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = Color.Gray
            )
        }

        // Decrement button
        Box(
            modifier = Modifier
                .width(60.dp)
                .height(30.dp)
                .background(
                    if (count > 0) Color(0xFFD4AF37) else Color.Gray.copy(alpha = 0.3f),
                    RoundedCornerShape(bottomStart = 8.dp, bottomEnd = 8.dp)
                )
                .clickable(enabled = count > 0) { onDecrement() },
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "−",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        }
    }
}

// Tutorial highlight modifier
fun Modifier.tutorialHighlight(): Modifier {
    return this.border(
        width = 3.dp,
        color = Color(0xFF7B1FA2),
        shape = RoundedCornerShape(8.dp)
    ).shadow(
        elevation = 8.dp,
        shape = RoundedCornerShape(8.dp)
    )
}