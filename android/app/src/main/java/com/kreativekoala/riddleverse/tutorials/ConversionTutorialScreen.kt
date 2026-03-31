package com.kreativekoala.riddleverse

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Tutorial screen for Conversion (Swipeable Blocks) puzzles
 */
@Composable
fun ConversionTutorialScreen(
    onTutorialComplete: () -> Unit,
    onTutorialSkipped: () -> Unit,
    onBack: () -> Unit
) {
    val tutorialManager = remember { ConversionTutorialManager() }
    val steps = tutorialManager.getTutorialSteps()
    var currentStepIndex by remember { mutableStateOf(0) }
    var tutorialState by remember { mutableStateOf(TutorialState.ACTIVE) }

    // Sample tutorial data
    val sampleLeftBlock = ConversionBlock(
        id = 1,
        label = "5 kg",
        value = 5.0,
        unit = "kilograms",
        color = Color(0xFF4CAF50)
    )

    val sampleRightBlock = ConversionBlock(
        id = 2,
        label = "10 lbs",
        value = 10.0,
        unit = "pounds",
        color = Color(0xFF2196F3)
    )

    val sampleConversionType = ConversionType.WEIGHT
    val sampleCorrectAnswer = ComparisonResult.LEFT_HEAVIER // 5 kg > 10 lbs

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

    fun skipTutorial() {
        tutorialState = TutorialState.SKIPPED
    }

    fun handleTutorialAction(action: String) {
        val step = steps.getOrNull(currentStepIndex)
        if (step?.interactionRequired == true && step.expectedAction == action) {
            advanceStep()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // Tutorial version of the conversion screen
        TutorialConversionContent(
            currentStep = steps.getOrNull(currentStepIndex),
            leftBlock = sampleLeftBlock,
            rightBlock = sampleRightBlock,
            conversionType = sampleConversionType,
            correctAnswer = sampleCorrectAnswer,
            onTutorialAction = ::handleTutorialAction,
            onBack = onBack
        )

        // Tutorial overlay
        if (tutorialState == TutorialState.ACTIVE && currentStepIndex < steps.size) {
            val currentStep = steps[currentStepIndex]

            if (currentStep.interactionRequired) {
                MinimalInteractiveOverlay(
                    currentStep = currentStep,
                    totalSteps = steps.size,
                    currentStepNumber = currentStepIndex + 1,
                    onNext = { advanceStep() },
                    onSkip = { skipTutorial() }
                )
            } else {
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
 * Interactive tutorial version of the conversion screen
 */
@Composable
fun TutorialConversionContent(
    currentStep: TutorialStep?,
    leftBlock: ConversionBlock,
    rightBlock: ConversionBlock,
    conversionType: ConversionType,
    correctAnswer: ComparisonResult,
    onTutorialAction: (String) -> Unit,
    onBack: () -> Unit
) {
    // Tutorial game state
    var leftBlockState by remember { mutableStateOf(leftBlock.copy()) }
    var rightBlockState by remember { mutableStateOf(rightBlock.copy()) }
    var showContinueButton by remember { mutableStateOf(false) }
    var hasSubmitted by remember { mutableStateOf(false) }
    var hasInteractedWithBlocks by remember { mutableStateOf(false) }

    val haptics = LocalHapticFeedback.current
    val density = LocalDensity.current

    // Threshold for determining position relationships (in dp converted to pixels)
    val positionThreshold = with(density) { 50.dp.toPx() }

    // Calculate current user answer based on block positions
    fun getCurrentAnswer(): ComparisonResult {
        return when {
            leftBlockState.offsetY < rightBlockState.offsetY - positionThreshold -> ComparisonResult.LEFT_HEAVIER
            rightBlockState.offsetY < leftBlockState.offsetY - positionThreshold -> ComparisonResult.RIGHT_HEAVIER
            else -> ComparisonResult.EQUAL
        }
    }

    // Check if blocks have been moved enough to show continue button
    LaunchedEffect(leftBlockState.offsetY, rightBlockState.offsetY) {
        val totalMovement = abs(leftBlockState.offsetY) + abs(rightBlockState.offsetY)
        showContinueButton = totalMovement > positionThreshold && !hasSubmitted

        if (totalMovement > 10f && !hasInteractedWithBlocks) {
            hasInteractedWithBlocks = true
            onTutorialAction("drag_block")
        }

        if (totalMovement > positionThreshold) {
            onTutorialAction("position_blocks")
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF1A1A2E),
                        Color(0xFF16213E),
                        Color(0xFF0F3460)
                    )
                )
            )
    ) {
        Column(
            modifier = Modifier.fillMaxSize().statusBarsPadding()
        ) {
            // Top Bar with tutorial highlighting
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, bottom = 8.dp)
                    .then(
                        if (currentStep?.targetComponent == "timer") {
                            Modifier.tutorialHighlight()
                        } else Modifier
                    ),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                // Left side: Back button and level
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.size(44.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = stringResource(R.string.back),
                            tint = Color.White,
                            modifier = Modifier.size(28.dp)
                        )
                    }

                    Column {
                        Text(
                            text = "TUTORIAL",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }

                // Center: Timer (frozen for tutorial)
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "2:00",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }

                // Right side: Hearts
                Column(
                    horizontalAlignment = Alignment.End
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        repeat(3) {
                            Icon(
                                imageVector = Icons.Default.Favorite,
                                contentDescription = null,
                                tint = Color(0xFFFF69B4),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                    Text(
                        text = "Tutorial",
                        fontSize = 16.sp,
                        color = Color.White,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Title
            Text(
                text = "COMPARE ${conversionType.displayName.uppercase()}",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Instructions
            Text(
                text = "Drag the blocks to show which ${conversionType.displayName.lowercase()} is greater",
                fontSize = 14.sp,
                color = Color.White.copy(alpha = 0.8f),
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(30.dp))

            // Universal scale visual representation with highlighting
            UniversalScale(
                conversionType = conversionType,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(80.dp)
                    .padding(horizontal = 40.dp)
                    .then(
                        if (currentStep?.targetComponent == "scale") {
                            Modifier.tutorialHighlight()
                        } else Modifier
                    )
            )

            Spacer(modifier = Modifier.height(20.dp))

            // Main content area with draggable blocks
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = 40.dp)
                    .then(
                        if (currentStep?.targetComponent == "blocks") {
                            Modifier.tutorialHighlight()
                        } else Modifier
                    )
            ) {
                // Left block
                TutorialDraggableConversionBlock(
                    block = leftBlockState,
                    isHighlighted = currentStep?.targetComponent == "blocks" &&
                            currentStep.interactionRequired,
                    onDrag = { offset ->
                        if (!hasSubmitted) {
                            leftBlockState = leftBlockState.copy(
                                offsetY = (leftBlockState.offsetY + offset.y).coerceIn(-200f, 200f),
                                isDragging = true
                            )
                        }
                    },
                    onDragEnd = {
                        leftBlockState = leftBlockState.copy(isDragging = false)
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    },
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .offset { IntOffset(0, leftBlockState.offsetY.roundToInt()) }
                )

                // Right block
                TutorialDraggableConversionBlock(
                    block = rightBlockState,
                    isHighlighted = currentStep?.targetComponent == "blocks" &&
                            currentStep.interactionRequired,
                    onDrag = { offset ->
                        if (!hasSubmitted) {
                            rightBlockState = rightBlockState.copy(
                                offsetY = (rightBlockState.offsetY + offset.y).coerceIn(-200f, 200f),
                                isDragging = true
                            )
                        }
                    },
                    onDragEnd = {
                        rightBlockState = rightBlockState.copy(isDragging = false)
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    },
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .offset { IntOffset(0, rightBlockState.offsetY.roundToInt()) }
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Continue button with highlighting
            AnimatedVisibility(
                visible = showContinueButton,
                enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
            ) {
                TutorialContinueButton(
                    isHighlighted = currentStep?.targetComponent == "continue_button" &&
                            currentStep.interactionRequired,
                    onClick = {
                        hasSubmitted = true
                        onTutorialAction("submit_answer")

                        val userAnswer = getCurrentAnswer()
                        val isCorrect = userAnswer == correctAnswer

                        // Show simple feedback for tutorial
                        val feedbackText = if (isCorrect) {
                            "Correct! 5 kg is heavier than 10 lbs (5 kg ≈ 11 lbs)"
                        } else {
                            "5 kg is actually heavier than 10 lbs. 5 kg equals about 11 pounds!"
                        }

                        // Reset for next interaction
                        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main).launch {
                            kotlinx.coroutines.delay(2000)
                            leftBlockState = leftBlock.copy()
                            rightBlockState = rightBlock.copy()
                            showContinueButton = false
                            hasSubmitted = false
                            hasInteractedWithBlocks = false
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 40.dp)
                        .padding(bottom = 40.dp)
                )
            }
        }
    }
}

/**
 * Tutorial version of draggable conversion block with enhanced highlighting
 */
@Composable
fun TutorialDraggableConversionBlock(
    block: ConversionBlock,
    isHighlighted: Boolean = false,
    onDrag: (Offset) -> Unit,
    onDragEnd: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Pulsing effect for highlighted blocks
    val pulseScale by animateFloatAsState(
        targetValue = if (isHighlighted) 1.05f else 1f,
        animationSpec = if (isHighlighted) {
            infiniteRepeatable(
                animation = tween(1000, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            )
        } else {
            tween(300)
        },
        label = "pulseAnimation"
    )

    val borderColor by animateColorAsState(
        targetValue = if (isHighlighted) Color(0xFF7B1FA2) else Color.Transparent,
        animationSpec = tween(300),
        label = "borderColor"
    )

    Card(
        modifier = modifier
            .width(120.dp)
            .height(70.dp)
            .scale(pulseScale)
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragEnd = { onDragEnd() }
                ) { change, dragAmount ->
                    onDrag(dragAmount)
                }
            }
            .shadow(
                elevation = if (block.isDragging) 12.dp else 6.dp,
                shape = RoundedCornerShape(12.dp)
            )
            .border(
                width = if (isHighlighted) 3.dp else 0.dp,
                color = borderColor,
                shape = RoundedCornerShape(12.dp)
            ),
        colors = CardDefaults.cardColors(
            containerColor = block.color
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = block.label,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                textAlign = TextAlign.Center
            )
        }
    }
}

/**
 * Tutorial version of continue button with highlighting
 */
@Composable
fun TutorialContinueButton(
    isHighlighted: Boolean = false,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val pulseAlpha by animateFloatAsState(
        targetValue = if (isHighlighted) 0.8f else 1f,
        animationSpec = if (isHighlighted) {
            infiniteRepeatable(
                animation = tween(1000, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            )
        } else {
            tween(300)
        },
        label = "pulseAnimation"
    )

    Button(
        onClick = onClick,
        modifier = modifier
            .height(56.dp)
            .alpha(pulseAlpha)
            .then(
                if (isHighlighted) {
                    Modifier.border(
                        width = 3.dp,
                        color = Color(0xFF7B1FA2),
                        shape = RoundedCornerShape(28.dp)
                    )
                } else Modifier
            ),
        colors = ButtonDefaults.buttonColors(
            containerColor = Color(0xFF4CAF50)
        ),
        shape = RoundedCornerShape(28.dp)
    ) {
        Text(
            text = stringResource(R.string.continue_label_caps),
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )
    }
}