package com.kreativekoala.riddleverse

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex

/**
 * Tutorial Overlay Component that appears over the puzzle screen
 * Provides step-by-step guidance with progress tracking
 */
@Composable
fun TutorialOverlay(
    currentStep: TutorialStep,
    totalSteps: Int,
    currentStepNumber: Int,
    onNext: () -> Unit,
    onSkip: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.7f))
            .clickable(enabled = false) { /* Prevent clicks through */ }
            .zIndex(1000f)
    ) {
        // Tutorial card
        Card(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp)
            ) {
                // Header with step indicator
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = currentStep.title,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF333333)
                        )
                        Text(
                            text = "Step $currentStepNumber of $totalSteps",
                            fontSize = 12.sp,
                            color = Color.Gray
                        )
                    }

                    IconButton(onClick = onSkip) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = stringResource(R.string.skip_tutorial),
                            tint = Color.Gray
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Progress indicator
                LinearProgressIndicator(
                    progress = currentStepNumber.toFloat() / totalSteps.toFloat(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp),
                    color = Color(0xFF7B1FA2),
                    trackColor = Color(0xFFE1BEE7)
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = currentStep.description,
                    fontSize = 16.sp,
                    color = Color(0xFF666666),
                    lineHeight = 22.sp
                )

                Spacer(modifier = Modifier.height(24.dp))

                // Action buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = if (currentStep.interactionRequired)
                        Arrangement.SpaceBetween
                    else
                        Arrangement.End
                ) {
                    // Skip button (always visible)
                    TextButton(
                        onClick = onSkip,
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = Color.Gray
                        )
                    ) {
                        Text(stringResource(R.string.skip_tutorial))
                    }

                    // Next button (only visible if no interaction required)
                    if (!currentStep.interactionRequired) {
                        Button(
                            onClick = onNext,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF7B1FA2)
                            ),
                            shape = RoundedCornerShape(25.dp)
                        ) {
                            Text(
                                text = if (currentStepNumber == totalSteps) stringResource(R.string.done) else stringResource(R.string.next),
                                color = Color.White,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    } else {
                        // Show instruction for required interaction
                        Text(
                            text = "👆 ${getInteractionInstruction(currentStep.expectedAction)}",
                            fontSize = 14.sp,
                            color = Color(0xFF7B1FA2),
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }
    }
}

/**
 * Helper function to get interaction instructions for different actions
 */
fun getInteractionInstruction(expectedAction: String?): String {
    return when (expectedAction) {
        "add_10" -> "Tap the '+' button above the 10"
        "submit" -> "Tap the golden circle to submit"
        "select_option" -> "Tap on an answer choice"
        "select_answer" -> "Choose your answer"
        "make_match" -> "Drag to match related words"
        "group_words" -> "Drag words into groups"
        "spell_word" -> "Tap letters to spell the word"
        "repeat_pattern" -> "Repeat the pattern you saw"
        "answer_question" -> "Answer the question"
        else -> "Try the action described above"
    }
}

@Composable
fun FloatingTutorialTooltip(
    currentStep: TutorialStep,
    totalSteps: Int,
    currentStepNumber: Int,
    onNext: () -> Unit,
    onSkip: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Only show background dimming for non-interactive steps
    if (!currentStep.interactionRequired) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.3f))
        )
    }

    // Floating tooltip
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = when (currentStep.targetComponent) {
            "valueButtons" -> Alignment.TopCenter // Show at top when buttons are at bottom
            "problem" -> Alignment.BottomCenter // Show at bottom when problem is at top
            "answerCircle" -> Alignment.TopCenter // Show at top when circle is in middle
            else -> Alignment.Center
        }
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .padding(
                    when (currentStep.targetComponent) {
                        "valueButtons" -> PaddingValues(top = 80.dp)
                        "problem" -> PaddingValues(bottom = 80.dp)
                        "answerCircle" -> PaddingValues(top = 80.dp)
                        else -> PaddingValues(16.dp)
                    }
                ),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${currentStep.title} ($currentStepNumber/$totalSteps)",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF333333),
                        modifier = Modifier.weight(1f)
                    )

                    IconButton(
                        onClick = onSkip,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = stringResource(R.string.skip),
                            tint = Color.Gray,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Progress bar
                LinearProgressIndicator(
                    progress = { currentStepNumber.toFloat() / totalSteps.toFloat() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(2.dp),
                    color = Color(0xFF7B1FA2),
                    trackColor = Color(0xFFE1BEE7)
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Description
                Text(
                    text = currentStep.description,
                    fontSize = 14.sp,
                    color = Color(0xFF666666),
                    lineHeight = 18.sp
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Action area
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (currentStep.interactionRequired) {
                        Text(
                            text = "👆 ${getInteractionInstruction(currentStep.expectedAction)}",
                            fontSize = 12.sp,
                            color = Color(0xFF7B1FA2),
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.weight(1f)
                        )
                    } else {
                        Spacer(modifier = Modifier.weight(1f))

                        Button(
                            onClick = onNext,
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF7B1FA2)),
                            shape = RoundedCornerShape(20.dp),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = if (currentStepNumber == totalSteps) stringResource(R.string.done) else stringResource(R.string.next),
                                color = Color.White,
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Tutorial action handler utility function
 */
fun handleTutorialAction(
    action: String,
    currentStep: TutorialStep?,
    onNextStep: () -> Unit
) {
    if (currentStep?.interactionRequired == true && currentStep.expectedAction == action) {
        onNextStep()
    }
}

@Composable
fun MinimalInteractiveOverlay(
    currentStep: TutorialStep,
    totalSteps: Int,
    currentStepNumber: Int,
    onNext: () -> Unit,
    onSkip: () -> Unit
) {
    // Very light background
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.1f))
    )

    // Floating instruction bubble
    FloatingTutorialTooltip(
        currentStep = currentStep,
        totalSteps = totalSteps,
        currentStepNumber = currentStepNumber,
        onNext = onNext,
        onSkip = onSkip
    )
}

@Composable
fun SmartTutorialOverlay(
    currentStep: TutorialStep,
    totalSteps: Int,
    currentStepNumber: Int,
    onNext: () -> Unit,
    onSkip: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Determine overlay position based on target component
    val overlayPosition = when (currentStep.targetComponent) {
        "valueButtons" -> OverlayPosition.TOP // Show at top when targeting bottom buttons
        "answerCircle" -> OverlayPosition.BOTTOM // Show at bottom when targeting middle
        "problem" -> OverlayPosition.BOTTOM // Show at bottom when targeting top
        else -> OverlayPosition.BOTTOM
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .zIndex(1000f)
    ) {
        // Semi-transparent background that allows interaction in highlighted areas
        if (currentStep.interactionRequired) {
            InteractiveOverlayBackground(
                targetComponent = currentStep.targetComponent ?: "",
                highlightArea = currentStep.highlightArea
            )
        } else {
            // Full overlay for non-interactive steps
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.7f))
                    .clickable(enabled = false) { }
            )
        }

        // Tutorial card positioned smartly
        TutorialCard(
            currentStep = currentStep,
            totalSteps = totalSteps,
            currentStepNumber = currentStepNumber,
            onNext = onNext,
            onSkip = onSkip,
            position = overlayPosition,
            modifier = Modifier.align(
                when (overlayPosition) {
                    OverlayPosition.TOP -> Alignment.TopCenter
                    OverlayPosition.BOTTOM -> Alignment.BottomCenter
                    OverlayPosition.CENTER -> Alignment.Center
                }
            )
        )
    }
}

enum class OverlayPosition {
    TOP, BOTTOM, CENTER
}

/**
 * Interactive overlay background that only dims non-interactive areas
 */
@Composable
fun InteractiveOverlayBackground(
    targetComponent: String,
    highlightArea: String?
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.3f)) // Lighter background for interactive mode
    )
}

/**
 * Tutorial card that can be positioned at top or bottom
 */
@Composable
fun TutorialCard(
    currentStep: TutorialStep,
    totalSteps: Int,
    currentStepNumber: Int,
    onNext: () -> Unit,
    onSkip: () -> Unit,
    position: OverlayPosition,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 12.dp),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(20.dp)
        ) {
            // Compact header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = currentStep.title,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF333333)
                    )
                    Text(
                        text = "Step $currentStepNumber of $totalSteps",
                        fontSize = 11.sp,
                        color = Color.Gray
                    )
                }

                IconButton(
                    onClick = onSkip,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = stringResource(R.string.skip_tutorial),
                        tint = Color.Gray,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Compact progress indicator
            LinearProgressIndicator(
                progress = { currentStepNumber.toFloat() / totalSteps.toFloat() },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp),
                color = Color(0xFF7B1FA2),
                trackColor = Color(0xFFE1BEE7),
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Description
            Text(
                text = currentStep.description,
                fontSize = 14.sp,
                color = Color(0xFF666666),
                lineHeight = 20.sp
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Compact action buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Skip button
                TextButton(
                    onClick = onSkip,
                    colors = ButtonDefaults.textButtonColors(contentColor = Color.Gray),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                ) {
                    Text(stringResource(R.string.skip), fontSize = 12.sp)
                }

                // Next button or instruction
                if (!currentStep.interactionRequired) {
                    Button(
                        onClick = onNext,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF7B1FA2)),
                        shape = RoundedCornerShape(20.dp),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        Text(
                            text = if (currentStepNumber == totalSteps) stringResource(R.string.done) else stringResource(R.string.next),
                            color = Color.White,
                            fontWeight = FontWeight.Medium,
                            fontSize = 12.sp
                        )
                    }
                } else {
                    // Compact instruction
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "👆",
                            fontSize = 16.sp
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = getInteractionInstruction(currentStep.expectedAction),
                            fontSize = 12.sp,
                            color = Color(0xFF7B1FA2),
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }
    }
}