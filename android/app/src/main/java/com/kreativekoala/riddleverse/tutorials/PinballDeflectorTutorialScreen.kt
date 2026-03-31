package com.kreativekoala.riddleverse

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.Canvas
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import kotlinx.coroutines.delay

/**
 * Tutorial screen for Pinball Deflector puzzles
 */
@Composable
fun PinballDeflectorTutorialScreen(
    onTutorialComplete: () -> Unit,
    onTutorialSkipped: () -> Unit,
    onBack: () -> Unit
) {
    val tutorialManager = remember { PinballDeflectorTutorialManager() }
    val steps = tutorialManager.getTutorialSteps()
    var currentStepIndex by remember { mutableStateOf(0) }
    var tutorialState by remember { mutableStateOf(TutorialState.ACTIVE) }

    // Sample tutorial data
    val sampleDeflectors = listOf(
        DeflectorData(1, 1, 1), // Slash at [1,1]
        DeflectorData(2, 3, 2)  // Backslash at [2,3]
    )
    val sampleStartPosition = BallPosition(0, 1)
    val sampleStartDirection = BallDirection(1, 0, "DOWN")

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
        // Tutorial version of the pinball screen
        TutorialPinballDeflectorContent(
            currentStep = steps.getOrNull(currentStepIndex),
            sampleDeflectors = sampleDeflectors,
            sampleStartPosition = sampleStartPosition,
            sampleStartDirection = sampleStartDirection,
            onTutorialAction = ::handleTutorialAction,
            onBack = onBack
        )

        // Tutorial overlay - positioned at top
        if (tutorialState == TutorialState.ACTIVE && currentStepIndex < steps.size) {
            val currentStep = steps[currentStepIndex]

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.7f))
            ) {
                Card(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 60.dp, start = 16.dp, end = 16.dp)
                        .fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp)
                    ) {
                        // Step indicator
                        Text(
                            text = "Step ${currentStepIndex + 1} of ${steps.size}",
                            fontSize = 12.sp,
                            color = Color.Gray
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        // Title
                        Text(
                            text = currentStep.title,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.Black
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        // Description
                        Text(
                            text = currentStep.description,
                            fontSize = 14.sp,
                            color = Color.DarkGray
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        // Buttons
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            if (!currentStep.interactionRequired) {
                                TextButton(onClick = { skipTutorial() }) {
                                    Text(stringResource(R.string.skip_tutorial))
                                }

                                Button(onClick = { advanceStep() }) {
                                    Text(if (currentStepIndex == steps.size - 1) stringResource(R.string.done) else stringResource(R.string.next))
                                }
                            } else {
                                TextButton(onClick = { skipTutorial() }) {
                                    Text(stringResource(R.string.skip_tutorial))
                                }

                                Spacer(modifier = Modifier.width(1.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Interactive tutorial version of the pinball screen
 */
@Composable
fun TutorialPinballDeflectorContent(
    currentStep: TutorialStep?,
    sampleDeflectors: List<DeflectorData>,
    sampleStartPosition: BallPosition,
    sampleStartDirection: BallDirection,
    onTutorialAction: (String) -> Unit,
    onBack: () -> Unit
) {
    // Tutorial game state
    var gameState by remember { mutableStateOf(PinballGameState.MEMORIZING) }
    var selectedEndPosition by remember { mutableStateOf<BallPosition?>(null) }
    var ballPath by remember { mutableStateOf<List<BallPosition>>(emptyList()) }
    var trajectoryProgress by remember { mutableStateOf(0f) }
    var timeLeft by remember { mutableStateOf(5) } // 5 seconds for tutorial

    // Memory timer for tutorial
    LaunchedEffect(gameState) {
        if (gameState == PinballGameState.MEMORIZING) {
            while (timeLeft > 0 && gameState == PinballGameState.MEMORIZING) {
                delay(1000)
                timeLeft--
            }
            if (gameState == PinballGameState.MEMORIZING) {
                gameState = PinballGameState.GUESSING
                onTutorialAction("memorization_complete")
            }
        }
    }

    // Auto-advance after showing result
    LaunchedEffect(gameState) {
        if (gameState == PinballGameState.SHOWING_RESULT) {
            trajectoryProgress = 0f
            animate(
                initialValue = 0f,
                targetValue = 1f,
                animationSpec = tween(durationMillis = 1500, easing = LinearEasing)
            ) { value, _ ->
                trajectoryProgress = value
            }

            // Auto-advance after animation completes
            delay(2000) // Wait 2 seconds after animation
            onTutorialAction("continue")
        }
    }

    fun handleCellSelection(row: Int, col: Int) {
        if (gameState == PinballGameState.GUESSING) {
            selectedEndPosition = BallPosition(row, col)
            onTutorialAction("select_end_position")
        }
    }

    fun submitAnswer() {
        if (selectedEndPosition == null || gameState != PinballGameState.GUESSING) return

        // For tutorial, simulate the correct path
        val tutorialPath = listOf(
            BallPosition(0, 1), // Start
            BallPosition(1, 1), // Hit first deflector
            BallPosition(1, 0), // Deflected left
            BallPosition(1, -1)

        )

        ballPath = tutorialPath
        gameState = PinballGameState.SHOWING_RESULT
        onTutorialAction("submit_answer")
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF2A2A2A)) // Lighter background
            .padding(16.dp)
    ) {
        // Header
        TutorialPinballHeader(
            onBack = onBack,
            timeLeft = timeLeft,
            gameState = gameState
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Game state indicator - moved to top and made smaller
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
        ) {
            when (gameState) {
                PinballGameState.MEMORIZING -> {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF333333)),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = "Memorize deflector positions: ${timeLeft}s",
                            color = Color.Yellow,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .padding(12.dp)
                                .fillMaxWidth()
                                .then(
                                    if (currentStep?.targetComponent == "timer") {
                                        Modifier.tutorialHighlight()
                                    } else Modifier
                                ),
                            textAlign = TextAlign.Center
                        )
                    }
                }
                PinballGameState.GUESSING -> {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF333333)),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = "Predict where the ball will end up",
                            color = Color.Cyan,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .padding(12.dp)
                                .fillMaxWidth(),
                            textAlign = TextAlign.Center
                        )
                    }
                }
                PinballGameState.SHOWING_RESULT -> {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF333333)),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = "Watch the ball's path! 🎉",
                            color = Color.Green,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .padding(12.dp)
                                .fillMaxWidth(),
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Game board with highlighting
        TutorialPinballGameBoard(
            matrixSize = 4, // Smaller 4x4 grid for tutorial
            startPosition = sampleStartPosition,
            startDirection = sampleStartDirection,
            deflectors = sampleDeflectors,
            selectedEndPosition = selectedEndPosition,
            ballPath = ballPath,
            trajectoryProgress = trajectoryProgress,
            gameState = gameState,
            onCellClick = ::handleCellSelection,
            isHighlighted = currentStep?.targetComponent == "game_board",
            modifier = Modifier.weight(1f)
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Control buttons with highlighting - simplified for tutorial
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    if (currentStep?.targetComponent == "submit_button") {
                        Modifier.tutorialHighlight()
                    } else Modifier
                ),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            when (gameState) {
                PinballGameState.GUESSING -> {
                    Button(
                        onClick = { submitAnswer() },
                        enabled = selectedEndPosition != null,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50))
                    ) {
                        Text(stringResource(R.string.submit_answer))
                    }
                }
                PinballGameState.SHOWING_RESULT -> {
                    // Don't show continue button - auto-advance instead
                    Text(
                        text = "Analyzing result...",
                        color = Color.Gray,
                        fontSize = 14.sp
                    )
                }
                else -> {
                    // Empty space during memorization
                    Spacer(modifier = Modifier.height(48.dp))
                }
            }
        }
    }
}

@Composable
fun TutorialPinballHeader(
    onBack: () -> Unit,
    timeLeft: Int,
    gameState: PinballGameState
) {
    Row(
        modifier = Modifier.statusBarsPadding().fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(
            onClick = onBack,
            modifier = Modifier
                .size(48.dp)
                .background(Color(0xFF444444), CircleShape)
        ) {
            Text("←", color = Color.White, fontSize = 32.sp, fontWeight = FontWeight.Bold)
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "Pinball Tutorial",
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Learn the basics",
                color = Color.Gray,
                fontSize = 14.sp
            )
        }

        Row {
            repeat(3) {
                Text("❤️", fontSize = 16.sp)
            }
        }
    }
}

@Composable
fun TutorialPinballGameBoard(
    matrixSize: Int,
    startPosition: BallPosition,
    startDirection: BallDirection,
    deflectors: List<DeflectorData>,
    selectedEndPosition: BallPosition?,
    ballPath: List<BallPosition>,
    trajectoryProgress: Float,
    gameState: PinballGameState,
    onCellClick: (Int, Int) -> Unit,
    isHighlighted: Boolean = false,
    modifier: Modifier = Modifier
) {
    val boardSize = 300.dp

    Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center
    ) {
        // Main game board canvas with highlighting
        Canvas(
            modifier = Modifier
                .size(boardSize)
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFF404040)) // Lighter board background
                .border(
                    width = if (isHighlighted) 4.dp else 2.dp,
                    color = if (isHighlighted) Color(0xFF7B1FA2) else Color(0xFF555555),
                    shape = RoundedCornerShape(12.dp)
                )
                .then(
                    if (isHighlighted) {
                        Modifier.tutorialHighlight()
                    } else Modifier
                )
        ) {
            val cellSize = size.width / matrixSize

            // Always draw grid
            drawTutorialGrid(matrixSize, cellSize)

            // What to show based on game state
            when (gameState) {
                PinballGameState.MEMORIZING -> {
                    // Show deflectors only
                    deflectors.forEach { deflector ->
                        drawTutorialDeflector(deflector, cellSize)
                    }
                }

                PinballGameState.GUESSING -> {
                    // Show selected position if user has made a selection
                    selectedEndPosition?.let { pos ->
                        if (pos.row >= 0 && pos.row < matrixSize && pos.col >= 0 && pos.col < matrixSize) {
                            drawTutorialSelectedEndPosition(pos, cellSize)
                        }
                    }
                }

                PinballGameState.SHOWING_RESULT -> {
                    // Show everything
                    deflectors.forEach { deflector ->
                        drawTutorialDeflector(deflector, cellSize)
                    }

                    selectedEndPosition?.let { pos ->
                        if (pos.row >= 0 && pos.row < matrixSize && pos.col >= 0 && pos.col < matrixSize) {
                            drawTutorialSelectedEndPosition(pos, cellSize)
                        }
                    }

                    // Show animated ball path
                    if (ballPath.isNotEmpty()) {
                        drawTutorialAnimatedBallPath(ballPath, cellSize, matrixSize, trajectoryProgress)
                    }
                }
            }
        }

        // Edge positions overlay for tutorial
        if (gameState == PinballGameState.GUESSING || gameState == PinballGameState.SHOWING_RESULT) {
            TutorialEdgePositionsOverlay(
                matrixSize = matrixSize,
                startPosition = startPosition,
                startDirection = startDirection,
                selectedEndPosition = selectedEndPosition,
                gameState = gameState,
                onCellClick = onCellClick,
                boardSize = boardSize
            )
        }
    }
}

@Composable
fun TutorialEdgePositionsOverlay(
    matrixSize: Int,
    startPosition: BallPosition,
    startDirection: BallDirection,
    selectedEndPosition: BallPosition?,
    gameState: PinballGameState,
    onCellClick: (Int, Int) -> Unit,
    boardSize: androidx.compose.ui.unit.Dp
) {
    val cellSize = boardSize / matrixSize
    val offset = 30.dp

    // Create simplified edge positions for tutorial - focus on left exit
    val edgePositions = listOf(
        Triple(-1, 1, "top"),     // Top entry (start position)
        Triple(1, -1, "left"),    // Left exit (correct answer)
        Triple(4, 1, "bottom"),   // Bottom exit (decoy)
        Triple(1, 4, "right")     // Right exit (decoy)
    )

    edgePositions.forEach { (row, col, edge) ->
        val (offsetX, offsetY) = when (edge) {
            "top" -> Pair(
                cellSize * col + cellSize/2 - boardSize/2,
                -boardSize/2 - offset
            )
            "bottom" -> Pair(
                cellSize * col + cellSize/2 - boardSize/2,
                boardSize/2 + offset
            )
            "left" -> Pair(
                -boardSize/2 - offset,
                cellSize * row + cellSize/2 - boardSize/2
            )
            "right" -> Pair(
                boardSize/2 + offset,
                cellSize * row + cellSize/2 - boardSize/2
            )
            else -> Pair(0.dp, 0.dp)
        }

        val isSelected = selectedEndPosition?.let {
            getEdgePositionFromExit(it, matrixSize)?.let { edgePos ->
                edgePos.first == row && edgePos.second == col && edgePos.third == edge
            }
        } ?: false

        val isStartPosition = edge == "top" && col == startPosition.col

        Box(
            modifier = Modifier
                .offset(x = offsetX, y = offsetY)
                .size(cellSize * 0.4f)
                .clip(CircleShape)
                .background(
                    when {
                        isStartPosition -> Color(0xFF00BCD4)  // Cyan for start
                        isSelected -> Color(0xFF4CAF50)       // Green for selected
                        gameState == PinballGameState.GUESSING -> Color(0xFF666666) // Gray for selectable
                        else -> Color.Transparent
                    }
                )
                .border(
                    2.dp,
                    when {
                        isStartPosition || isSelected -> Color.White
                        gameState == PinballGameState.GUESSING -> Color(0xFF888888)
                        else -> Color.Transparent
                    },
                    CircleShape
                )
                .clickable(enabled = gameState == PinballGameState.GUESSING && !isStartPosition) {
                    onCellClick(row, col)
                },
            contentAlignment = Alignment.Center
        ) {
            when {
                isStartPosition -> {
                    Text("●", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 8.sp)
                }
                isSelected -> {
                    Text("?", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 8.sp)
                }
            }
        }
    }
}

// Drawing functions for tutorial
fun DrawScope.drawTutorialGrid(matrixSize: Int, cellSize: Float) {
    val gridColor = Color(0xFF666666) // Lighter grid lines

    for (i in 0..matrixSize) {
        val x = i * cellSize
        drawLine(
            color = gridColor,
            start = Offset(x, 0f),
            end = Offset(x, size.height),
            strokeWidth = 2.dp.toPx() // Thicker lines
        )
    }

    for (i in 0..matrixSize) {
        val y = i * cellSize
        drawLine(
            color = gridColor,
            start = Offset(0f, y),
            end = Offset(size.width, y),
            strokeWidth = 2.dp.toPx() // Thicker lines
        )
    }
}

fun DrawScope.drawTutorialDeflector(deflector: DeflectorData, cellSize: Float) {
    val centerX = (deflector.col + 0.5f) * cellSize
    val centerY = (deflector.row + 0.5f) * cellSize
    val strokeWidth = 6.dp.toPx() // Thicker for tutorial visibility
    val lineLength = cellSize * 0.7f

    val color = Color(0xFFFFD700) // Gold color for deflectors

    when (deflector.type) {
        1 -> { // Slash /
            drawLine(
                color = color,
                start = Offset(centerX - lineLength/2, centerY + lineLength/2),
                end = Offset(centerX + lineLength/2, centerY - lineLength/2),
                strokeWidth = strokeWidth
            )
        }
        2 -> { // Backslash \
            drawLine(
                color = color,
                start = Offset(centerX - lineLength/2, centerY - lineLength/2),
                end = Offset(centerX + lineLength/2, centerY + lineLength/2),
                strokeWidth = strokeWidth
            )
        }
    }
}

fun DrawScope.drawTutorialSelectedEndPosition(position: BallPosition, cellSize: Float) {
    val centerX = (position.col + 0.5f) * cellSize
    val centerY = (position.row + 0.5f) * cellSize
    val radius = cellSize * 0.25f

    drawCircle(
        color = Color(0xFF4CAF50),
        radius = radius,
        center = Offset(centerX, centerY)
    )

    val markSize = radius * 0.5f
    drawLine(
        color = Color.White,
        start = Offset(centerX - markSize, centerY - markSize),
        end = Offset(centerX + markSize, centerY + markSize),
        strokeWidth = 3.dp.toPx()
    )
    drawLine(
        color = Color.White,
        start = Offset(centerX + markSize, centerY - markSize),
        end = Offset(centerX - markSize, centerY + markSize),
        strokeWidth = 3.dp.toPx()
    )
}

fun DrawScope.drawTutorialAnimatedBallPath(
    path: List<BallPosition>,
    cellSize: Float,
    matrixSize: Int,
    progress: Float = 1f
) {
    if (path.isEmpty()) return

    val pathColor = Color(0xFFFF5722) // Orange path
    val dotColor = Color(0xFF00BCD4)  // Cyan dots
    val strokeWidth = 4.dp.toPx() // Thicker for tutorial
    val dotRadius = cellSize * 0.1f // Larger dots

    val positionsToShow = (path.size * progress).toInt().coerceAtLeast(1)
    val visiblePath = path.take(positionsToShow)

    for (i in 0 until visiblePath.size) {
        val current = visiblePath[i]

        // Handle positions that may be outside the grid
        val currentX = when {
            current.col < 0 -> 0f // Left edge
            current.col >= matrixSize -> size.width // Right edge
            else -> (current.col + 0.5f) * cellSize
        }

        val currentY = when {
            current.row < 0 -> 0f // Top edge
            current.row >= matrixSize -> size.height // Bottom edge
            else -> (current.row + 0.5f) * cellSize
        }

        // Draw line to next position
        if (i < visiblePath.size - 1) {
            val next = visiblePath[i + 1]

            val nextX = when {
                next.col < 0 -> 0f // Left edge
                next.col >= matrixSize -> size.width // Right edge
                else -> (next.col + 0.5f) * cellSize
            }

            val nextY = when {
                next.row < 0 -> 0f // Top edge
                next.row >= matrixSize -> size.height // Bottom edge
                else -> (next.row + 0.5f) * cellSize
            }

            drawLine(
                color = pathColor,
                start = Offset(currentX, currentY),
                end = Offset(nextX, nextY),
                strokeWidth = strokeWidth
            )
        }

        // Draw dot at current position
        drawCircle(
            color = dotColor,
            radius = dotRadius,
            center = Offset(currentX, currentY)
        )

        drawCircle(
            color = Color.White,
            radius = dotRadius,
            center = Offset(currentX, currentY),
            style = Stroke(width = 2.dp.toPx())
        )
    }
}

/**
 * Tutorial manager for Pinball Deflector puzzles
 */
class PinballDeflectorTutorialManager {
    fun getTutorialSteps(): List<TutorialStep> {
        return listOf(
            TutorialStep(
                id = "intro",
                title = "Welcome to Pinball Physics!",
                description = "In this puzzle, you'll predict where a ball will exit after bouncing off deflectors.",
                interactionRequired = false,
                targetComponent = null,
                expectedAction = null
            ),
            TutorialStep(
                id = "deflectors",
                title = "Deflectors",
                description = "The gold slash (/) deflector will change the ball's direction. When moving DOWN and hitting /, the ball deflects LEFT.",
                interactionRequired = false,
                targetComponent = "game_board",
                expectedAction = null
            ),
            TutorialStep(
                id = "memorize",
                title = "Memorization Phase",
                description = "First, you'll see the deflectors for a few seconds. Memorize their positions!",
                interactionRequired = false,
                targetComponent = "timer",
                expectedAction = null
            ),
            TutorialStep(
                id = "wait_memorize",
                title = "Memorizing...",
                description = "Study the deflector position. The ball starts from the top at the cyan dot and moves down.",
                interactionRequired = true,
                targetComponent = "game_board",
                expectedAction = "memorization_complete"
            ),
            TutorialStep(
                id = "predict",
                title = "Prediction Phase",
                description = "The ball will hit the slash deflector and change direction. Predict where it will exit!",
                interactionRequired = true,
                targetComponent = "game_board",
                expectedAction = "select_end_position"
            ),
            TutorialStep(
                id = "submit",
                title = "Submit Your Answer",
                description = "Great! Now submit your prediction to see if you're correct.",
                interactionRequired = true,
                targetComponent = "submit_button",
                expectedAction = "submit_answer"
            ),
            TutorialStep(
                id = "result",
                title = "See the Result",
                description = "Perfect! Watch how the ball bounces off each deflector. The tutorial will continue automatically.",
                interactionRequired = true,
                targetComponent = null,
                expectedAction = "continue"
            ),
            TutorialStep(
                id = "complete",
                title = "Tutorial Complete!",
                description = "You're ready to play! Remember: memorize deflectors, predict the exit, and watch the physics in action.",
                interactionRequired = false,
                targetComponent = null,
                expectedAction = null
            )
        )
    }
}