package com.kreativekoala.riddleverse

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

/**
 * Tutorial screen for Memory Squares puzzles
 */
@Composable
fun MemorySquaresTutorialScreen(
    onTutorialComplete: () -> Unit,
    onTutorialSkipped: () -> Unit,
    onBack: () -> Unit
) {
    val tutorialManager = remember { MemorySquaresTutorialManager() }
    val steps = tutorialManager.getTutorialSteps()
    var currentStepIndex by remember { mutableStateOf(0) }
    var tutorialState by remember { mutableStateOf(TutorialState.ACTIVE) }

    // Tutorial game state
    var gamePhase by remember { mutableStateOf(MemoryGamePhase.COUNTDOWN) }
    var countdownTime by remember { mutableIntStateOf(3) }
    var memorizeTime by remember { mutableIntStateOf(3) }
    var selectedCells by remember { mutableStateOf(setOf<Pair<Int, Int>>()) }
    var currentLives by remember { mutableIntStateOf(3) }
    var userClickCount by remember { mutableIntStateOf(0) }


    // Sample tutorial data - simple 3x3 grid
    val tutorialMatrix = listOf(
        listOf(1, 0, 1),
        listOf(0, 1, 0),
        listOf(1, 0, 0)
    )
    val targetPositions = listOf(Pair(0,0), Pair(0,2), Pair(1,1), Pair(2,0))

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
            userClickCount++
            // Wait for 4 clicks before advancing
            if (userClickCount >= 4) {
                advanceStep()
            }
        }
    }

    // Auto-advance tutorial through game phases
    LaunchedEffect(currentStepIndex) {
        when (currentStepIndex) {
            1 -> { // Grid step - show actual grid
                gamePhase = MemoryGamePhase.RECALL // Show the actual grid
            }
            2 -> { // Countdown step
                gamePhase = MemoryGamePhase.COUNTDOWN
                countdownTime = 3
            }
            3 -> { // Memorize step
                gamePhase = MemoryGamePhase.MEMORIZE
                memorizeTime = 3
            }
            4 -> { // Recall step
                gamePhase = MemoryGamePhase.RECALL
            }
        }
    }

    // Handle cell clicks
    fun onCellClick(row: Int, col: Int) {
        if (gamePhase != MemoryGamePhase.RECALL) return

        val cellPosition = Pair(row, col)
        selectedCells = if (selectedCells.contains(cellPosition)) {
            selectedCells - cellPosition
        } else {
            selectedCells + cellPosition
        }

        // Track each click for tutorial
        handleTutorialAction("select_squares")
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // Tutorial Memory Squares content
        TutorialMemorySquaresContent(
            currentStep = steps.getOrNull(currentStepIndex),
            gamePhase = gamePhase,
            countdownTime = countdownTime,
            memorizeTime = memorizeTime,
            selectedCells = selectedCells,
            currentLives = currentLives,
            userClickCount = userClickCount,
            tutorialMatrix = tutorialMatrix,
            targetPositions = targetPositions,
            onCellClick = ::onCellClick,
            onBack = onBack
        )

        // Tutorial overlay
        // Tutorial overlay with smart positioning
        if (tutorialState == TutorialState.ACTIVE && currentStepIndex < steps.size) {
            val currentStep = steps[currentStepIndex]

            if (currentStep.interactionRequired) {
                // Custom overlay for interaction step with bottom positioning
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
                                        text = "Step ${currentStepIndex + 1} of ${steps.size} • Clicks: $userClickCount/3",
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

                            // Show interaction instruction
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

                                Text(
                                    text = "👆 Tap 4 squares above to continue",
                                    fontSize = 12.sp,
                                    color = Color(0xFF7B1FA2),
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                }
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
 * Tutorial-specific memory squares content
 */
@Composable
fun TutorialMemorySquaresContent(
    currentStep: TutorialStep?,
    gamePhase: MemoryGamePhase,
    countdownTime: Int,
    memorizeTime: Int,
    selectedCells: Set<Pair<Int, Int>>,
    currentLives: Int,
    userClickCount: Int,
    tutorialMatrix: List<List<Int>>,
    targetPositions: List<Pair<Int, Int>>,
    onCellClick: (Int, Int) -> Unit,
    onBack: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF8D6E63))
            .statusBarsPadding()
            .padding(16.dp)
    ) {
        // Top bar
        Row(
            modifier = Modifier.fillMaxWidth(),
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

            IconButton(onClick = { }) {
                Icon(
                    Icons.Default.Pause,
                    contentDescription = null,
                    tint = Color.White
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Game info bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Color.White.copy(alpha = 0.9f),
                    RoundedCornerShape(12.dp)
                )
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "TUTORIAL",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = Color.Black
            )

            Text(
                text = "SCORE    0",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = Color.Black
            )
        }

        Spacer(modifier = Modifier.height(32.dp))

        // Phase indicator with highlighting
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    if (currentStep?.targetComponent == "countdown") {
                        Modifier.tutorialHighlight()
                    } else Modifier
                ),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            when (gamePhase) {
                MemoryGamePhase.COUNTDOWN -> {
                    Text(
                        text = stringResource(R.string.get_ready),
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = countdownTime.toString(),
                        fontSize = 48.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
                MemoryGamePhase.MEMORIZE -> {
                    Text(
                        text = stringResource(R.string.memorize_the_pattern),
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "${memorizeTime}s remaining",
                        fontSize = 16.sp,
                        color = Color.White.copy(alpha = 0.8f)
                    )
                }
                MemoryGamePhase.RECALL -> {
                    Text(
                        text = stringResource(R.string.click_squares_you_remember),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = if (currentStep?.interactionRequired == true) {
                            "Tutorial: ${userClickCount}/4 clicks • Targets: ${selectedCells.size}/${targetPositions.size}"
                        } else {
                            "Targets: ${selectedCells.size}/${targetPositions.size}"
                        },
                        fontSize = 14.sp,
                        color = Color.White.copy(alpha = 0.8f)
                    )
                }
                else -> {
                    Text(
                        text = "Tutorial Mode",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        // Memory squares grid with highlighting
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .then(
                    if (currentStep?.targetComponent == "grid") {
                        Modifier.tutorialHighlight()
                    } else Modifier
                ),
            contentAlignment = Alignment.Center
        ) {
            // Grid background
            Box(
                modifier = Modifier
                    .size(280.dp)
                    .background(
                        Color.Black.copy(alpha = 0.8f),
                        RoundedCornerShape(12.dp)
                    )
                    .padding(8.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    for (row in 0 until 3) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            for (col in 0 until 3) {
                                val isTarget = tutorialMatrix[row][col] == 1
                                val isSelected = selectedCells.contains(Pair(row, col))
                                val showHighlight = gamePhase == MemoryGamePhase.MEMORIZE && isTarget

                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxHeight()
                                        .background(
                                            when {
                                                showHighlight -> Color(0xFF00BCD4) // Cyan for memorization
                                                isSelected && isTarget -> Color(0xFF4CAF50) // Green for correct
                                                isSelected && !isTarget -> Color(0xFFF44336) // Red for wrong
                                                else -> Color(0xFF6D4C41) // Brown default
                                            },
                                            RoundedCornerShape(4.dp)
                                        )
                                        .clickable { onCellClick(row, col) }
                                        .border(
                                            1.dp,
                                            Color.Black.copy(alpha = 0.2f),
                                            RoundedCornerShape(4.dp)
                                        )
                                )
                            }
                        }
                    }
                }
            }

            // Countdown overlay
            if (gamePhase == MemoryGamePhase.COUNTDOWN) {
                Box(
                    modifier = Modifier
                        .size(280.dp)
                        .background(
                            Color.Black.copy(alpha = 0.8f),
                            RoundedCornerShape(12.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = stringResource(R.string.get_ready),
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = countdownTime.toString(),
                            fontSize = 72.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.Cyan
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = stringResource(R.string.memorize_the_pattern),
                            fontSize = 16.sp,
                            color = Color.White.copy(alpha = 0.8f),
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Lives indicator with highlighting
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    if (currentStep?.targetComponent == "lives") {
                        Modifier.tutorialHighlight()
                    } else Modifier
                ),
            horizontalArrangement = Arrangement.Center
        ) {
            repeat(3) { index ->
                Icon(
                    Icons.Default.Favorite,
                    contentDescription = null,
                    tint = if (index < currentLives) Color.Red else Color.Gray,
                    modifier = Modifier
                        .size(24.dp)
                        .padding(horizontal = 4.dp)
                )
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        // Action buttons with highlighting
        if (gamePhase == MemoryGamePhase.RECALL) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(
                        if (currentStep?.targetComponent == "submit") {
                            Modifier.tutorialHighlight()
                        } else Modifier
                    ),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = { },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = Color.White
                    )
                ) {
                    Text(stringResource(R.string.clear))
                }

                Button(
                    onClick = { },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF4FC3F7)
                    )
                ) {
                    Text(stringResource(R.string.submit))
                }
            }
        }
    }
}