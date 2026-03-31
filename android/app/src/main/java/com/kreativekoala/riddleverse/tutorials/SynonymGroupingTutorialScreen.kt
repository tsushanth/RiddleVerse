package com.kreativekoala.riddleverse

import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.sp

/**
 * Tutorial screen for Synonym Grouping puzzles
 */
@Composable
fun SynonymGroupingTutorialScreen(
    onTutorialComplete: () -> Unit,
    onTutorialSkipped: () -> Unit,
    onBack: () -> Unit
) {
    val tutorialManager = remember { SynonymGroupingTutorialManager() }
    val steps = tutorialManager.getTutorialSteps()
    var currentStepIndex by remember { mutableStateOf(0) }
    var tutorialState by remember { mutableStateOf(TutorialState.ACTIVE) }

    // Tutorial data - simple synonym sets
    val tutorialSynonymSets = remember {
        listOf(
            SynonymSet(
                id = 0,
                words = listOf("happy", "joyful", "cheerful", "glad"),
                displayWord = "Happy",
                color = Color(0xFFE91E63) // Pink
            ),
            SynonymSet(
                id = 1,
                words = listOf("big", "large", "huge", "enormous"),
                displayWord = "Big",
                color = Color(0xFF2196F3) // Blue
            ),
            SynonymSet(
                id = 2,
                words = listOf("fast", "quick", "rapid", "swift"),
                displayWord = "Fast",
                color = Color(0xFF4CAF50) // Green
            )
        )
    }

    // Tutorial word queue
    val tutorialWords = remember {
        listOf("joyful", "enormous", "rapid").mapIndexed { index, word ->
            SynonymWord(word, index)
        }
    }

    var currentWordIndex by remember { mutableIntStateOf(0) }
    var selectedSetId by remember { mutableIntStateOf(-1) }
    var userHasInteracted by remember { mutableStateOf(false) }

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
            userHasInteracted = true
            // Don't auto-advance, let user manually continue
        }
    }

    fun handleSetSelection(setId: Int) {
        selectedSetId = setId
        handleTutorialAction("select_group")
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // Tutorial Synonym Grouping content
        TutorialSynonymGroupingContent(
            currentStep = steps.getOrNull(currentStepIndex),
            tutorialSynonymSets = tutorialSynonymSets,
            tutorialWords = tutorialWords,
            currentWordIndex = currentWordIndex,
            selectedSetId = selectedSetId,
            onSetSelection = ::handleSetSelection,
            onBack = onBack
        )

        // Tutorial overlay
        if (tutorialState == TutorialState.ACTIVE && currentStepIndex < steps.size) {
            val currentStep = steps[currentStepIndex]

            if (currentStep.interactionRequired) {
                // Custom overlay for interaction step
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.BottomCenter
                ) {
                    // Light background
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.2f))
                    )

                    // Tutorial card at bottom
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(20.dp)
                        ) {
                            // Header
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
                                        text = "Step ${currentStepIndex + 1} of ${steps.size}",
                                        fontSize = 11.sp,
                                        color = Color.Gray
                                    )
                                }

                                IconButton(onClick = { skipTutorial() }) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = stringResource(R.string.skip_tutorial),
                                        tint = Color.Gray,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            // Progress bar
                            LinearProgressIndicator(
                                progress = { (currentStepIndex + 1).toFloat() / steps.size.toFloat() },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(3.dp),
                                color = Color(0xFF7B1FA2),
                                trackColor = Color(0xFFE1BEE7)
                            )

                            Spacer(modifier = Modifier.height(12.dp))

                            Text(
                                text = currentStep.description,
                                fontSize = 14.sp,
                                color = Color(0xFF666666),
                                lineHeight = 20.sp
                            )

                            Spacer(modifier = Modifier.height(16.dp))

                            // Action buttons
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                TextButton(
                                    onClick = { skipTutorial() },
                                    colors = ButtonDefaults.textButtonColors(contentColor = Color.Gray)
                                ) {
                                    Text(stringResource(R.string.skip), fontSize = 12.sp)
                                }

                                if (userHasInteracted) {
                                    // Show Next button after interaction
                                    Button(
                                        onClick = { advanceStep() },
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF7B1FA2)),
                                        shape = RoundedCornerShape(20.dp)
                                    ) {
                                        Text(
                                            text = stringResource(R.string.next),
                                            color = Color.White,
                                            fontSize = 12.sp
                                        )
                                    }
                                } else {
                                    // Show instruction to interact
                                    Text(
                                        text = "👆 Tap a colored group above",
                                        fontSize = 12.sp,
                                        color = Color(0xFF7B1FA2),
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        }
                    }
                }
            } else {
                // Use standard overlay for non-interactive steps
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
 * Tutorial-specific synonym grouping content
 */
@Composable
fun TutorialSynonymGroupingContent(
    currentStep: TutorialStep?,
    tutorialSynonymSets: List<SynonymSet>,
    tutorialWords: List<SynonymWord>,
    currentWordIndex: Int,
    selectedSetId: Int,
    onSetSelection: (Int) -> Unit,
    onBack: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF1A1A2E))
            .statusBarsPadding()
            .padding(16.dp)
    ) {
        // Header with highlighting
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    if (currentStep?.targetComponent == "timer_lives") {
                        Modifier.tutorialHighlight()
                    } else Modifier
                ),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.Default.ArrowBack,
                    contentDescription = stringResource(R.string.back),
                    tint = Color.White
                )
            }

            Text(
                text = "TUTORIAL",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Timer,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = "2:30",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(start = 4.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Level and Hearts with highlighting
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    if (currentStep?.targetComponent == "timer_lives") {
                        Modifier.tutorialHighlight()
                    } else Modifier
                ),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Tutorial",
                color = Color.White.copy(alpha = 0.8f),
                fontSize = 14.sp
            )

            Row {
                repeat(3) { index ->
                    Text(
                        text = "❤️",
                        fontSize = 16.sp,
                        modifier = Modifier.padding(horizontal = 2.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Instructions with highlighting
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    if (currentStep?.targetComponent == "instructions") {
                        Modifier.tutorialHighlight()
                    } else Modifier
                ),
            colors = CardDefaults.cardColors(
                containerColor = Color(0xFF2A2A3E)
            ),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text(
                text = "Group the synonyms! Tap the set that matches the word above.",
                color = Color.White,
                fontSize = 14.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(16.dp)
            )
        }

        Spacer(modifier = Modifier.height(64.dp))

        // Main Game Area
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                // Central word display with highlighting
                if (currentWordIndex < tutorialWords.size) {
                    Card(
                        modifier = Modifier
                            .wrapContentSize()
                            .padding(32.dp)
                            .then(
                                if (currentStep?.targetComponent == "word_display") {
                                    Modifier.tutorialHighlight()
                                } else Modifier
                            ),
                        colors = CardDefaults.cardColors(
                            containerColor = Color.White
                        ),
                        shape = RoundedCornerShape(20.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
                    ) {
                        Text(
                            text = tutorialWords[currentWordIndex].word,
                            fontSize = 28.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.Black,
                            modifier = Modifier.padding(32.dp),
                            textAlign = TextAlign.Center
                        )
                    }
                }

                Spacer(modifier = Modifier.height(80.dp))

                // Synonym set buttons with highlighting
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .then(
                            if (currentStep?.targetComponent == "synonym_groups") {
                                Modifier.tutorialHighlight()
                            } else Modifier
                        ),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    tutorialSynonymSets.forEach { set ->
                        TutorialSynonymSetButton(
                            synonymSet = set,
                            isSelected = selectedSetId == set.id,
                            isHighlighted = currentStep?.targetComponent == "synonym_groups" &&
                                    currentStep.interactionRequired,
                            onClick = { onSetSelection(set.id) }
                        )
                    }
                }
            }
        }

        // Score with highlighting
        Text(
            text = "Score: 0",
            color = Color.White,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .then(
                    if (currentStep?.targetComponent == "scoring") {
                        Modifier.tutorialHighlight()
                    } else Modifier
                )
        )

        Spacer(modifier = Modifier.height(16.dp))
    }
}

/**
 * Tutorial version of synonym set button with enhanced highlighting
 */
@Composable
fun TutorialSynonymSetButton(
    synonymSet: SynonymSet,
    isSelected: Boolean,
    isHighlighted: Boolean,
    onClick: () -> Unit
) {
    val animatedScale by animateFloatAsState(
        targetValue = when {
            isSelected -> 1.2f
            isHighlighted -> 1.05f
            else -> 1f
        },
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "buttonScale"
    )

    // Pulsing effect for highlighted buttons
    val pulseAlpha by animateFloatAsState(
        targetValue = if (isHighlighted) 0.8f else 0.2f,
        animationSpec = if (isHighlighted) {
            infiniteRepeatable(
                animation = tween(1000),
                repeatMode = RepeatMode.Reverse
            )
        } else {
            tween(300)
        },
        label = "pulseAnimation"
    )

    Card(
        modifier = Modifier
            .size(100.dp)
            .graphicsLayer {
                scaleX = animatedScale
                scaleY = animatedScale
            }
            .clickable { onClick() },
        colors = CardDefaults.cardColors(
            containerColor = synonymSet.color.copy(alpha = pulseAlpha)
        ),
        shape = CircleShape,
        border = BorderStroke(
            width = if (isSelected || isHighlighted) 3.dp else 2.dp,
            color = if (isSelected) Color.White else synonymSet.color
        )
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = synonymSet.displayWord,
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                lineHeight = 16.sp
            )
        }
    }
}