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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Preview
import androidx.compose.material.icons.filled.Help
import androidx.compose.material3.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.draw.alpha
import kotlinx.coroutines.delay
import kotlin.random.Random

// Extension function for tutorial highlighting
fun Modifier.imageTutorialHighlight(): Modifier {
    return this.then(
        Modifier.border(
            width = 3.dp,
            color = Color(0xFF4CAF50).copy(alpha = 0.8f),
            shape = RoundedCornerShape(8.dp)
        )
    )
}

/**
 * Tutorial screen for Image puzzles
 */
@Composable
fun ImagePuzzleTutorialScreen(
    onTutorialComplete: () -> Unit,
    onTutorialSkipped: () -> Unit,
    onBack: () -> Unit
) {
    val tutorialManager = remember { ImagePuzzleTutorialManager() }
    val steps = tutorialManager.getTutorialSteps()
    var currentStepIndex by remember { mutableStateOf(0) }
    var tutorialState by remember { mutableStateOf(TutorialState.ACTIVE) }

    // Sample tutorial image puzzle data
    val sampleImagePuzzleData = createSampleImagePuzzleData()

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
        if (step?.id?.isNotEmpty() == true && step.id == action) {
            advanceStep()
        }
    }

    Box(modifier = Modifier.fillMaxSize().imePadding()) {
        // Tutorial version of the image puzzle screen
        TutorialImagePuzzleContent(
            currentStep = steps.getOrNull(currentStepIndex),
            sampleImagePuzzleData = sampleImagePuzzleData,
            onTutorialAction = ::handleTutorialAction,
            onBack = onBack
        )

        // Tutorial overlay
        if (tutorialState == TutorialState.ACTIVE && currentStepIndex < steps.size) {
            val currentStep = steps[currentStepIndex]

            if (currentStep.id.isNotEmpty()) {
                // Interactive step - requires user action - always at top
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(top = 150.dp),
                    contentAlignment = Alignment.TopCenter
                ) {
                    MinimalInteractiveOverlay(
                        currentStep = currentStep,
                        totalSteps = steps.size,
                        currentStepNumber = currentStepIndex + 1,
                        onNext = { advanceStep() },
                        onSkip = { skipTutorial() }
                    )
                }
            } else {
                // Informational step - positioned at bottom to avoid blocking puzzle
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(bottom = 120.dp),
                    contentAlignment = Alignment.BottomCenter
                ) {
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
}

/**
 * Interactive tutorial version of the image puzzle screen
 */
@Composable
fun TutorialImagePuzzleContent(
    currentStep: TutorialStep?,
    sampleImagePuzzleData: AdaptiveImagePuzzleData,
    onTutorialAction: (String) -> Unit,
    onBack: () -> Unit
) {
    val haptics = LocalHapticFeedback.current

    // Tutorial state management
    var puzzlePieces by remember { mutableStateOf(sampleImagePuzzleData.pieces) }
    var selectedPieceId by remember { mutableStateOf(-1) }
    var showPreview by remember { mutableStateOf(false) }
    var currentHearts by remember { mutableStateOf(3) }
    var currentScore by remember { mutableStateOf(0) }
    var hintsUsed by remember { mutableStateOf(0) }
    var isComplete by remember { mutableStateOf(false) }
    var gridBounds by remember { mutableStateOf<androidx.compose.ui.geometry.Rect?>(null) }

    // Track tutorial progress
    var hasSelectedPiece by remember { mutableStateOf(false) }
    var hasPlacedPiece by remember { mutableStateOf(false) }
    var hasRemovedPiece by remember { mutableStateOf(false) }
    var hasRotatedPiece by remember { mutableStateOf(false) }
    var hasUsedPreview by remember { mutableStateOf(false) }

    // Convert to enhanced pieces for drag functionality
    var enhancedPieces by remember {
        mutableStateOf(
            puzzlePieces.map { piece ->
                EnhancedImagePuzzlePiece(
                    id = piece.id,
                    correctRow = piece.correctRow,
                    correctCol = piece.correctCol,
                    bitmap = piece.bitmap,
                    currentPosition = piece.currentPosition,
                    currentRotation = piece.currentRotation,
                    isPlaced = piece.isPlaced,
                    isCorrect = piece.isCorrect,
                    placedInRow = piece.placedInRow,
                    placedInCol = piece.placedInCol,
                    scale = piece.scale,
                    dragState = DragState()
                )
            }
        )
    }

    // Check if puzzle is complete
    LaunchedEffect(enhancedPieces) {
        val correctPieces = enhancedPieces.count { it.isCorrect }
        if (correctPieces == sampleImagePuzzleData.totalPieces && !isComplete) {
            isComplete = true
            currentScore = correctPieces * 10 + 50 // Base score plus bonus
            onTutorialAction("puzzle_complete")
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF2D3748))
            .padding(top = 80.dp, start = 16.dp, end = 16.dp, bottom = 16.dp)
    ) {
        // Enhanced header with highlighting
        Box(
            modifier = Modifier.then(
                if (currentStep?.targetComponent == "header") {
                    Modifier.imageTutorialHighlight()
                } else Modifier
            )
        ) {
            TutorialImagePuzzleHeader(
                difficulty = "Tutorial",
                timer = "∞:∞",
                hearts = currentHearts,
                currentScore = currentScore,
                completedPieces = enhancedPieces.count { it.isCorrect },
                totalPieces = sampleImagePuzzleData.totalPieces,
                onBack = onBack,
                onPreview = {
                    showPreview = !showPreview
                    if (!hasUsedPreview) {
                        hasUsedPreview = true
                        onTutorialAction("preview_used")
                    }
                }
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Success message
        if (isComplete) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF4CAF50).copy(alpha = 0.2f))
            ) {
                Text(
                    text = "🎉 Puzzle Complete! You're an image puzzle master!",
                    modifier = Modifier.padding(12.dp),
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White,
                    textAlign = TextAlign.Center
                )
            }
        }

        // Instructions card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .then(
                    if (currentStep?.targetComponent == "instructions") {
                        Modifier.imageTutorialHighlight()
                    } else Modifier
                ),
            colors = CardDefaults.cardColors(containerColor = Color.Blue.copy(alpha = 0.2f))
        ) {
            Column(
                modifier = Modifier.padding(12.dp)
            ) {
                Text(
                    text = "🎯 Drag puzzle pieces from the bottom panel and drop them on the grid above",
                    fontSize = 14.sp,
                    color = Color.White,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = "💡 Long press placed pieces or click ❌ to remove them",
                    fontSize = 12.sp,
                    color = Color.Yellow,
                    textAlign = TextAlign.Center
                )
            }
        }

        // Preview overlay
        if (showPreview) {
            PreviewOverlayDialog(
                imageUrl = sampleImagePuzzleData.imageUrl,
                originalBitmap = sampleImagePuzzleData.originalBitmap,
                onDismiss = { showPreview = false }
            )
        }

        // Main puzzle grid with highlighting
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(16.dp)
                .then(
                    if (currentStep?.targetComponent == "grid") {
                        Modifier.imageTutorialHighlight()
                    } else Modifier
                )
        ) {
            TutorialEnhancedPuzzleGridWithRemoval(
                gridSize = sampleImagePuzzleData.gridSize,
                puzzlePieces = enhancedPieces,
                onPiecePlaced = { pieceId, row, col ->
                    enhancedPieces = handlePiecePlacement(pieceId, row, col,
                        enhancedPieces.map { it.toImagePuzzlePiece() }
                    ).map { it.toEnhancedImagePuzzlePiece() }

                    if (!hasPlacedPiece) {
                        hasPlacedPiece = true
                        onTutorialAction("piece_placed")
                    }
                },
                onPieceRemoved = { pieceId ->
                    enhancedPieces = handlePieceRemoval(pieceId,
                        enhancedPieces.map { it.toImagePuzzlePiece() }
                    ).map { it.toEnhancedImagePuzzlePiece() }

                    if (!hasRemovedPiece) {
                        hasRemovedPiece = true
                        onTutorialAction("piece_removed")
                    }
                },
                onGridBoundsChanged = { bounds -> gridBounds = bounds },
                onPieceDragUpdate = { pieceId, dragState ->
                    enhancedPieces = enhancedPieces.map { piece ->
                        if (piece.id == pieceId) {
                            piece.copy(dragState = dragState)
                        } else piece
                    }
                },
                isHighlighted = currentStep?.targetComponent == "grid"
            )
        }

        // Enhanced pieces panel with highlighting
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(140.dp)
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .then(
                    if (currentStep?.targetComponent == "pieces") {
                        Modifier.imageTutorialHighlight()
                    } else Modifier
                )
        ) {
            TutorialEnhancedPuzzlePiecesPanel(
                pieces = enhancedPieces.filter { !it.isPlaced },
                selectedPieceId = selectedPieceId,
                allowRotation = sampleImagePuzzleData.config.allowRotation,
                gridBounds = gridBounds,
                gridSize = sampleImagePuzzleData.gridSize,
                onPieceSelected = { pieceId ->
                    selectedPieceId = if (selectedPieceId == pieceId) -1 else pieceId
                    if (!hasSelectedPiece) {
                        hasSelectedPiece = true
                        onTutorialAction("piece_selected")
                    }
                },
                onPieceRotated = { pieceId ->
                    if (sampleImagePuzzleData.config.allowRotation) {
                        enhancedPieces = enhancedPieces.map { piece ->
                            if (piece.id == pieceId) {
                                piece.copy(currentRotation = (piece.currentRotation + 90f) % 360f)
                            } else piece
                        }

                        if (!hasRotatedPiece) {
                            hasRotatedPiece = true
                            onTutorialAction("piece_rotated")
                        }
                    }
                },
                onPieceDragUpdate = { pieceId, dragState ->
                    enhancedPieces = enhancedPieces.map { piece ->
                        if (piece.id == pieceId) {
                            piece.copy(dragState = dragState)
                        } else piece
                    }
                },
                onPiecePlaced = { pieceId, row, col ->
                    enhancedPieces = handlePiecePlacement(pieceId, row, col,
                        enhancedPieces.map { it.toImagePuzzlePiece() }
                    ).map { it.toEnhancedImagePuzzlePiece() }

                    if (!hasPlacedPiece) {
                        hasPlacedPiece = true
                        onTutorialAction("piece_placed")
                    }
                },
                isHighlighted = currentStep?.targetComponent == "pieces"
            )
        }
    }
}

/**
 * Tutorial version of image puzzle header
 */
@Composable
private fun TutorialImagePuzzleHeader(
    difficulty: String,
    timer: String,
    hearts: Int,
    currentScore: Int,
    completedPieces: Int,
    totalPieces: Int,
    onBack: () -> Unit,
    onPreview: () -> Unit
) {
    Column(modifier = Modifier.statusBarsPadding()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, contentDescription = stringResource(R.string.back), tint = Color.White)
            }

            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = stringResource(R.string.tutorial_mode),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White.copy(alpha = 0.8f)
                )
                Text(
                    text = "Image Puzzle",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = Color.White
                )
                Text(
                    text = timer,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = Color.White
                )
            }

            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row {
                    repeat(hearts) {
                        Text(
                            text = "❤️",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        }

        // Progress and controls
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Progress indicator
            Column {
                Text(
                    text = "Progress: $completedPieces/$totalPieces",
                    fontSize = 12.sp,
                    color = Color.White
                )
                LinearProgressIndicator(
                    progress = completedPieces.toFloat() / totalPieces.toFloat(),
                    modifier = Modifier.width(120.dp),
                    color = Color(0xFF10B981)
                )
            }

            // Action buttons
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                IconButton(
                    onClick = onPreview,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        Icons.Default.Preview,
                        null,
                        tint = Color.Cyan,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }

        // Config info
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "🔄 Rotation",
                fontSize = 10.sp,
                color = Color(0xFF4CAF50)
            )
            Text(
                text = "⚡ Tutorial",
                fontSize = 10.sp,
                color = Color(0xFF2196F3)
            )
            Text(
                text = "Score: $currentScore",
                fontSize = 10.sp,
                color = Color.White
            )
        }
    }
}

// Enhanced tutorial versions with highlighting
@Composable
private fun TutorialEnhancedPuzzleGridWithRemoval(
    gridSize: Int,
    puzzlePieces: List<EnhancedImagePuzzlePiece>,
    onPiecePlaced: (Int, Int, Int) -> Unit,
    onPieceRemoved: (Int) -> Unit,
    onGridBoundsChanged: (androidx.compose.ui.geometry.Rect) -> Unit,
    onPieceDragUpdate: (Int, DragState) -> Unit,
    isHighlighted: Boolean = false,
    modifier: Modifier = Modifier
) {
    val pulseAlpha by animateFloatAsState(
        targetValue = if (isHighlighted) 1.0f else 0.8f,
        animationSpec = if (isHighlighted) {
            infiniteRepeatable(
                animation = tween(1000, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            )
        } else {
            tween(300)
        },
        label = "gridPulse"
    )

    Box(
        modifier = modifier.alpha(pulseAlpha)
    ) {
        EnhancedPuzzleGridWithRemoval(
            gridSize = gridSize,
            puzzlePieces = puzzlePieces,
            onPiecePlaced = onPiecePlaced,
            onPieceRemoved = onPieceRemoved,
            onGridBoundsChanged = onGridBoundsChanged,
            onPieceDragUpdate = onPieceDragUpdate
        )
    }
}

@Composable
private fun TutorialEnhancedPuzzlePiecesPanel(
    pieces: List<EnhancedImagePuzzlePiece>,
    selectedPieceId: Int,
    allowRotation: Boolean,
    gridBounds: androidx.compose.ui.geometry.Rect?,
    gridSize: Int,
    onPieceSelected: (Int) -> Unit,
    onPieceRotated: (Int) -> Unit,
    onPieceDragUpdate: (Int, DragState) -> Unit,
    onPiecePlaced: (Int, Int, Int) -> Unit,
    isHighlighted: Boolean = false,
    modifier: Modifier = Modifier
) {
    val pulseAlpha by animateFloatAsState(
        targetValue = if (isHighlighted) 1.0f else 0.8f,
        animationSpec = if (isHighlighted) {
            infiniteRepeatable(
                animation = tween(1000, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            )
        } else {
            tween(300)
        },
        label = "piecesPulse"
    )

    Box(
        modifier = modifier.alpha(pulseAlpha)
    ) {
        EnhancedPuzzlePiecesPanel(
            pieces = pieces,
            selectedPieceId = selectedPieceId,
            allowRotation = allowRotation,
            gridBounds = gridBounds,
            gridSize = gridSize,
            onPieceSelected = onPieceSelected,
            onPieceRotated = onPieceRotated,
            onPieceDragUpdate = onPieceDragUpdate,
            onPiecePlaced = onPiecePlaced
        )
    }
}

/**
 * Create sample image puzzle data for tutorial
 */
fun createSampleImagePuzzleData(): AdaptiveImagePuzzleData {
    // Create a simple 2x2 puzzle for tutorial
    val gridSize = 2
    val totalPieces = 4

    // For tutorial, we'll simulate having image pieces
    // In real implementation, you'd have actual bitmap pieces
    val pieces = mutableListOf<ImagePuzzlePiece>()

    // Create mock pieces (you would replace this with actual bitmap creation)
    for (row in 0 until gridSize) {
        for (col in 0 until gridSize) {
            pieces.add(
                ImagePuzzlePiece(
                    id = row * gridSize + col,
                    correctRow = row,
                    correctCol = col,
                    bitmap = null, // In tutorial, we might use placeholder
                    currentRotation = 0f,
                    isPlaced = false,
                    isCorrect = false,
                    placedInRow = -1,
                    placedInCol = -1
                )
            )
        }
    }

    val config = AdaptiveImagePuzzleConfig(
        gridSize = gridSize,
        allowRotation = true,
        snapTolerance = 30f,
        timeLimit = 300L,
        showPreview = true,
        hintSystem = true,
        adaptiveComplexity = false,
        pieceShuffle = true,
        name = "Tutorial Mode",
        description = "Learn how to solve image puzzles"
    )

    return AdaptiveImagePuzzleData(
        puzzleId = "tutorial_image_puzzle",
        imageUrl = "https://example.com/tutorial-image.jpg", // Placeholder URL
        theme = "Tutorial Puzzle",
        description = "Learn to solve image puzzles",
        gridSize = gridSize,
        totalPieces = totalPieces,
        timeLimit = 300000L,
        pieces = pieces,
        originalBitmap = null, // Would be set in real implementation
        config = config,
        difficulty = "Tutorial"
    )
}

// Extension functions for conversion
fun EnhancedImagePuzzlePiece.toImagePuzzlePiece(): ImagePuzzlePiece {
    return ImagePuzzlePiece(
        id = this.id,
        correctRow = this.correctRow,
        correctCol = this.correctCol,
        bitmap = this.bitmap,
        currentPosition = this.currentPosition,
        currentRotation = this.currentRotation,
        isPlaced = this.isPlaced,
        isCorrect = this.isCorrect,
        placedInRow = this.placedInRow,
        placedInCol = this.placedInCol,
        scale = this.scale
    )
}

fun ImagePuzzlePiece.toEnhancedImagePuzzlePiece(): EnhancedImagePuzzlePiece {
    return EnhancedImagePuzzlePiece(
        id = this.id,
        correctRow = this.correctRow,
        correctCol = this.correctCol,
        bitmap = this.bitmap,
        currentPosition = this.currentPosition,
        currentRotation = this.currentRotation,
        isPlaced = this.isPlaced,
        isCorrect = this.isCorrect,
        placedInRow = this.placedInRow,
        placedInCol = this.placedInCol,
        scale = this.scale,
        dragState = DragState()
    )
}

/**
 * Tutorial manager for image puzzles
 */
class ImagePuzzleTutorialManager {
    fun getTutorialSteps(): List<TutorialStep> =
        listOf(
            TutorialStep(
                title = "Welcome to Image Puzzles! 🧩",
                description = "Learn how to solve image puzzles by arranging pieces to recreate the original picture. Each piece has a specific correct position!",
                targetComponent = "grid",
                id = "" // Informational - introduction
            ),
            TutorialStep(
                title = "Understanding the Puzzle 🖼️",
                description = "Look at the grid above. This is where you'll place puzzle pieces. The grid shows you exactly where each piece should go to complete the image!",
                targetComponent = "grid",
                id = "" // Informational - explaining the grid
            ),
            TutorialStep(
                title = "Your Mission 🎯",
                description = "Your job is to drag pieces from the bottom panel and drop them into the correct positions on the grid. Complete the picture to win!",
                targetComponent = "pieces",
                id = "" // Informational - explaining the goal
            ),
            TutorialStep(
                title = "The Pieces Panel 📱",
                description = "Look at the bottom panel - this is where all your puzzle pieces are stored. You'll drag these pieces up to the grid to solve the puzzle!",
                targetComponent = "pieces",
                id = "" // Informational - explaining pieces panel
            ),
            TutorialStep(
                title = "Selecting a Piece 👆",
                description = "First, tap on any piece in the bottom panel to select it. The selected piece will be highlighted with a blue border and show helpful instructions!",
                targetComponent = "pieces",
                id = "piece_selected" // INTERACTIVE - user must select a piece
            ),
            TutorialStep(
                title = "Drag and Drop 🚀",
                description = "Great! Now drag the selected piece from the bottom panel up to the grid. Drop it on any empty grid cell to place it there!",
                targetComponent = "grid",
                id = "piece_placed" // INTERACTIVE - user must place a piece
            ),
            TutorialStep(
                title = "Excellent Work! ✨",
                description = "Perfect! When you place a piece, it will show whether it's in the correct position. Green means correct, blue means placed but wrong position!",
                targetComponent = "grid",
                id = "" // Informational - explaining feedback
            ),
            TutorialStep(
                title = "Removing Pieces 🔄",
                description = "Made a mistake? No problem! Long press on any placed piece, or click the ❌ button to remove it and try a different position!",
                targetComponent = "grid",
                id = "piece_removed" // INTERACTIVE - user should try removing a piece
            ),
            TutorialStep(
                title = "Rotating Pieces 🔄",
                description = "Some puzzles allow rotation! Double-tap on a piece (either in the panel or on the grid) to rotate it 90 degrees. Try it now!",
                targetComponent = "pieces",
                id = "piece_rotated" // INTERACTIVE - user should rotate a piece
            ),
            TutorialStep(
                title = "Using the Preview 👁️",
                description = "Need help? Tap the preview button (👁️) in the header to see the original image. This helps you figure out where pieces belong!",
                targetComponent = "header",
                id = "preview_used" // INTERACTIVE - user should use preview
            ),
            TutorialStep(
                title = "Progress Tracking 📊",
                description = "Watch your progress in the header! It shows how many pieces you've placed correctly and your current score. Try to get all pieces correct!",
                targetComponent = "header",
                id = "" // Informational - explaining progress
            ),
            TutorialStep(
                title = "Complete the Puzzle 🏆",
                description = "Keep placing pieces until you've completed the entire image! Each correct placement increases your score and brings you closer to victory!",
                targetComponent = "grid",
                id = "puzzle_complete" // INTERACTIVE - user should complete the puzzle
            ),
            TutorialStep(
                title = "You're a Puzzle Master! 🎉",
                description = "Fantastic! You've mastered image puzzles! Use drag-and-drop, rotation, and the preview to solve any puzzle. Happy puzzling! 🧩✨",
                targetComponent = "grid",
                id = "" // Informational - congratulations
            )
        )
}