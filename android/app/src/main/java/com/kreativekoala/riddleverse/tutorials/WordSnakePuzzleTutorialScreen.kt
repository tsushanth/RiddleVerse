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
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Help
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.res.stringResource
import org.json.JSONObject
import org.json.JSONArray

// Extension function for tutorial highlighting
fun Modifier.wstutorialHighlight(): Modifier {
    return this.then(
        Modifier.border(
            width = 3.dp,
            color = Color(0xFF4CAF50).copy(alpha = 0.8f),
            shape = RoundedCornerShape(8.dp)
        )
    )
}

/**
 * Tutorial screen for Word Snake puzzles
 */
@Composable
fun WordSnakePuzzleTutorialScreen(
    onTutorialComplete: () -> Unit,
    onTutorialSkipped: () -> Unit,
    onBack: () -> Unit
) {
    val tutorialManager = remember { WordSnakePuzzleTutorialManager() }
    val steps = tutorialManager.getTutorialSteps()
    var currentStepIndex by remember { mutableStateOf(0) }
    var tutorialState by remember { mutableStateOf(TutorialState.ACTIVE) }

    // Sample tutorial word snake data
    val sampleWordSnakeData = createSampleWordSnakeData()

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
        // Tutorial version of the word snake screen
        TutorialWordSnakeContent(
            currentStep = steps.getOrNull(currentStepIndex),
            sampleWordSnakeData = sampleWordSnakeData,
            onTutorialAction = ::handleTutorialAction,
            onBack = onBack
        )

        // Tutorial overlay
        if (tutorialState == TutorialState.ACTIVE && currentStepIndex < steps.size) {
            val currentStep = steps[currentStepIndex]

            if (currentStep.id.isNotEmpty()) {
                // Interactive step - requires user action
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .then(
                            if (currentStepIndex == 5) Modifier.padding(top = 100.dp)
                            else Modifier
                        ),
                    contentAlignment = if (currentStepIndex == 5) Alignment.TopCenter else Alignment.Center
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
                // Informational step - just shows information
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
 * Interactive tutorial version of the word snake screen
 */
@Composable
fun TutorialWordSnakeContent(
    currentStep: TutorialStep?,
    sampleWordSnakeData: String,
    onTutorialAction: (String) -> Unit,
    onBack: () -> Unit
) {
    val haptics = LocalHapticFeedback.current
    val configuration = LocalConfiguration.current

    // Get screen dimensions
    val screenHeight = configuration.screenHeightDp.dp
    val screenWidth = configuration.screenWidthDp.dp

    // Parse word snake data
    val (grid, words, gridSize) = remember(sampleWordSnakeData) {
        parseWordSnakeData(sampleWordSnakeData)
    }

    // Calculate optimal grid size
    val topBarHeight = 80.dp
    val progressBarHeight = 80.dp
    val resetButtonHeight = 60.dp
    val systemPadding = 48.dp
    val hintsReservedSpace = 200.dp

    val availableGridHeight = screenHeight - topBarHeight - progressBarHeight -
            resetButtonHeight - hintsReservedSpace - systemPadding

    val maxCellSizeByWidth = (screenWidth - 32.dp) / gridSize
    val maxCellSizeByHeight = availableGridHeight / gridSize
    val optimalCellSize = minOf(maxCellSizeByWidth, maxCellSizeByHeight, 45.dp)

    // State management
    var foundWords by remember { mutableStateOf<Set<String>>(emptySet()) }
    var selectedCells by remember { mutableStateOf<List<GridPosition>>(emptyList()) }
    var currentSelectionWord by remember { mutableStateOf("") }
    var foundPaths by remember { mutableStateOf<List<WordSnakePath>>(emptyList()) }
    var totalScore by remember { mutableStateOf(0) }
    var currentStreak by remember { mutableStateOf(0) }
    var recompositionTrigger by remember { mutableStateOf(0) }

    // Function to handle word found
    fun handleWordFound(word: WordSnakeWord) {
        if (!foundWords.contains(word.word)) {
            foundWords = foundWords + word.word
            currentStreak++

            // Add found path
            val pathColor = Color(android.graphics.Color.parseColor(word.color))
            val newPath = WordSnakePath(
                word = word.word,
                path = word.path,
                color = pathColor,
                isFound = true
            )
            foundPaths = foundPaths + newPath

            // Mark cells as found and clear selection
            markWordAsFound(word.path, grid, word.word)
            clearSelection(grid)
            selectedCells = emptyList()
            currentSelectionWord = ""

            totalScore += 50 // Simple scoring for tutorial
            recompositionTrigger += 1
        }
    }

    // Function to handle cell tap
    fun handleCellTap(position: GridPosition) {
        val cell = grid[position.row][position.col]

        // Don't allow selection of already found cells
        if (cell.isFound) return

        if (cell.isSelected) {
            // Unselect cell and all cells selected after it
            val cellIndex = selectedCells.indexOf(position)
            if (cellIndex != -1) {
                val newSelection = selectedCells.take(cellIndex)

                // Clear selection state for removed cells
                for (i in cellIndex until selectedCells.size) {
                    val pos = selectedCells[i]
                    grid[pos.row][pos.col].isSelected = false
                    grid[pos.row][pos.col].selectionOrder = -1
                }

                selectedCells = newSelection
                currentSelectionWord = newSelection.map { pos ->
                    grid[pos.row][pos.col].letter
                }.joinToString("")

                onTutorialAction("unselect_cell")
            }
        } else {
            // Select cell
            cell.isSelected = true
            cell.selectionOrder = selectedCells.size
            selectedCells = selectedCells + position
            currentSelectionWord = selectedCells.map { pos ->
                grid[pos.row][pos.col].letter
            }.joinToString("")

            onTutorialAction("select_cell")

            // Check if current selection matches any word
            val matchedWord = checkWordMatch(selectedCells, words, foundWords)
            if (matchedWord != null) {
                handleWordFound(matchedWord)
                onTutorialAction("complete_word")
            }
        }

        recompositionTrigger += 1
    }



    // Function to clear current selection
    fun clearCurrentSelection() {
        clearSelection(grid)
        selectedCells = emptyList()
        currentSelectionWord = ""
        recompositionTrigger += 1
        onTutorialAction("clear_selection")
    }

    // Function to reset puzzle
    fun resetPuzzle() {
        clearWordSnakeGrid(grid)
        foundWords = emptySet()
        foundPaths = emptyList()
        selectedCells = emptyList()
        currentSelectionWord = ""
        totalScore = 0
        currentStreak = 0
        recompositionTrigger += 1
        onTutorialAction("reset_puzzle")
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF2C3E50)) // Dark blue-grey background
    ) {
        Column(
            modifier = Modifier.fillMaxSize().statusBarsPadding()
        ) {
            // Top Bar with highlighting
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(topBarHeight)
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .then(
                        if (currentStep?.targetComponent == "timer") {
                            Modifier.wstutorialHighlight()
                        } else Modifier
                    ),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ArrowBack,
                        contentDescription = stringResource(R.string.back),
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Text(
                    text = "4:00",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )

                IconButton(
                    onClick = { onTutorialAction("help_clicked") },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Help,
                        contentDescription = stringResource(R.string.hint),
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            // Progress indicator with highlighting
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(progressBarHeight)
                    .padding(horizontal = 16.dp, vertical = 4.dp)
                    .then(
                        if (currentStep?.targetComponent == "progress") {
                            Modifier.wstutorialHighlight()
                        } else Modifier
                    ),
                colors = CardDefaults.cardColors(
                    containerColor = Color.White.copy(alpha = 0.95f)
                ),
                shape = RoundedCornerShape(8.dp)
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Words: ${foundWords.size}/${words.size}",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF2C3E50)
                        )

                        LinearProgressIndicator(
                            progress = foundWords.size.toFloat() / words.size.toFloat(),
                            modifier = Modifier
                                .width(100.dp)
                                .height(6.dp),
                            color = Color(0xFF27AE60),
                            trackColor = Color.Gray.copy(alpha = 0.3f)
                        )
                    }

                    if (totalScore > 0 || currentStreak > 0) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            if (totalScore > 0) {
                                Text(
                                    text = "Score: $totalScore",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF27AE60)
                                )
                            }

                            if (currentStreak > 1) {
                                Text(
                                    text = "🔥$currentStreak",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFE74C3C)
                                )
                            }
                        }
                    }

                    // Show current selection word
                    if (currentSelectionWord.isNotEmpty()) {
                        Text(
                            text = "Current: $currentSelectionWord",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF3498DB)
                        )
                    }
                }
            }

            // Word Snake Grid with highlighting
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(availableGridHeight)
                    .padding(horizontal = 8.dp)
                    .then(
                        if (currentStep?.targetComponent == "grid") {
                            Modifier.wstutorialHighlight()
                        } else Modifier
                    ),
                contentAlignment = Alignment.Center
            ) {
                TutorialWordSnakeGrid(
                    grid = grid,
                    words = words,
                    gridSize = gridSize,
                    foundPaths = foundPaths,
                    selectedCells = selectedCells,
                    cellSize = optimalCellSize,
                    onCellTap = { position -> handleCellTap(position) },
                    haptics = haptics,
                    isHighlighted = currentStep?.targetComponent == "grid"
                )
            }

            // Word clues with highlighting
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .then(
                        if (currentStep?.targetComponent == "clues") {
                            Modifier.wstutorialHighlight()
                        } else Modifier
                    ),
                colors = CardDefaults.cardColors(
                    containerColor = Color.White.copy(alpha = 0.95f)
                ),
                shape = RoundedCornerShape(8.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = "Find these snake words:",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF2C3E50)
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(words) { word ->
                            TutorialWordSnakeClueItem(
                                word = word,
                                isFound = foundWords.contains(word.word),
                                color = Color(android.graphics.Color.parseColor(word.color))
                            )
                        }
                    }
                }
            }

            // Control buttons with highlighting
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(resetButtonHeight)
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .then(
                        if (currentStep?.targetComponent == "buttons") {
                            Modifier.wstutorialHighlight()
                        } else Modifier
                    ),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                // Clear selection button
                OutlinedButton(
                    onClick = { clearCurrentSelection() },
                    enabled = selectedCells.isNotEmpty(),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = Color.White,
                        disabledContentColor = Color.White.copy(alpha = 0.5f)
                    ),
                    border = BorderStroke(2.dp, Color.White.copy(alpha = if (selectedCells.isNotEmpty()) 1f else 0.5f)),
                    modifier = Modifier
                        .weight(1f)
                        .height(36.dp)
                ) {
                    Text(stringResource(R.string.clear).uppercase(), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }

                Spacer(modifier = Modifier.width(8.dp))

                // Reset button
                OutlinedButton(
                    onClick = { resetPuzzle() },
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = Color.White
                    ),
                    border = BorderStroke(2.dp, Color.White),
                    modifier = Modifier
                        .weight(1f)
                        .height(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = stringResource(R.string.reset),
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(stringResource(R.string.reset).uppercase(), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

/**
 * Tutorial version of the word snake grid
 */
@Composable
fun TutorialWordSnakeGrid(
    grid: List<MutableList<WordSnakeCell>>,
    words: List<WordSnakeWord>,
    gridSize: Int,
    foundPaths: List<WordSnakePath>,
    selectedCells: List<GridPosition>,
    cellSize: Dp,
    onCellTap: (GridPosition) -> Unit,
    haptics: HapticFeedback,
    isHighlighted: Boolean = false
) {
    val pulseAlpha by animateFloatAsState(
        targetValue = if (isHighlighted) 0.8f else 0.3f,
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
        modifier = Modifier.wrapContentSize()
    ) {
        // Grid
        Column(
            verticalArrangement = Arrangement.spacedBy(2.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            for (row in 0 until gridSize) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    for (col in 0 until gridSize) {
                        val cell = grid[row][col]
                        val position = GridPosition(row, col)
                        TutorialWordSnakeCell(
                            cell = cell,
                            size = cellSize,
                            isSelected = selectedCells.contains(position),
                            selectionOrder = selectedCells.indexOf(position),
                            onTap = { onCellTap(position) },
                            isHighlighted = isHighlighted
                        )
                    }
                }
            }
        }

        // Draw snake paths
        Canvas(
            modifier = Modifier
                .size(
                    width = cellSize * gridSize + (gridSize - 1).dp * 2,
                    height = cellSize * gridSize + (gridSize - 1).dp * 2
                )
        ) {
            foundPaths.forEach { snakePath ->
                drawSnakePath(
                    path = snakePath,
                    cellSize = cellSize.toPx(),
                    gridSpacing = 2.dp.toPx()
                )
            }

            // Draw current selection path
            if (selectedCells.size >= 2) {
                drawSelectionPath(
                    path = selectedCells,
                    cellSize = cellSize.toPx(),
                    gridSpacing = 2.dp.toPx()
                )
            }
        }
    }
}

/**
 * Tutorial version of word snake cell
 */
@Composable
fun TutorialWordSnakeCell(
    cell: WordSnakeCell,
    size: Dp,
    isSelected: Boolean,
    selectionOrder: Int,
    onTap: () -> Unit,
    isHighlighted: Boolean = false
) {
    val scale by animateFloatAsState(
        targetValue = if (isHighlighted && !cell.isFound) 1.05f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "cellScale"
    )

    Box(
        modifier = Modifier
            .size(size)
            .scale(scale)
            .background(
                color = when {
                    isSelected -> Color(0xFF3498DB).copy(alpha = 0.8f) // Blue for selected
                    cell.isFound -> Color(0xFF27AE60).copy(alpha = 0.3f) // Green for found
                    else -> Color.White
                },
                shape = RoundedCornerShape(6.dp)
            )
            .border(
                width = if (isSelected) 3.dp else 2.dp,
                color = when {
                    isSelected -> Color(0xFF2980B9) // Darker blue border for selected
                    cell.isFound -> Color(0xFF27AE60)
                    else -> Color.Gray.copy(alpha = 0.3f)
                },
                shape = RoundedCornerShape(6.dp)
            )
            .clickable { onTap() },
        contentAlignment = Alignment.Center
    ) {
        // Selection order indicator
        if (isSelected && selectionOrder >= 0) {
            Box(
                modifier = Modifier
                    .size((size.value * 0.3).dp)
                    .background(
                        Color(0xFF2980B9),
                        shape = RoundedCornerShape(50)
                    )
                    .align(Alignment.TopEnd)
                    .offset(x = (size.value * 0.1).dp, y = -(size.value * 0.1).dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = (selectionOrder + 1).toString(),
                    fontSize = (size.value * 0.2).sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
        }

        Text(
            text = cell.letter,
            fontSize = (size.value * 0.5).sp,
            fontWeight = FontWeight.Bold,
            color = if (cell.isFound || isSelected) Color.White else Color.Black,
            textAlign = TextAlign.Center
        )
    }
}

/**
 * Tutorial version of word snake clue item
 */
@Composable
fun TutorialWordSnakeClueItem(
    word: WordSnakeWord,
    isFound: Boolean,
    color: Color
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(16.dp)
                .background(
                    color = if (isFound) color else Color.Gray.copy(alpha = 0.3f),
                    shape = RoundedCornerShape(8.dp)
                )
        )

        Spacer(modifier = Modifier.width(12.dp))

        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = word.word,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = if (isFound) Color.Gray else Color.Black,
                textDecoration = if (isFound) TextDecoration.LineThrough else TextDecoration.None
            )

            Text(
                text = word.clue,
                fontSize = 12.sp,
                color = if (isFound) Color.Gray.copy(alpha = 0.7f) else Color.Gray,
                modifier = Modifier.alpha(if (isFound) 0.6f else 1f)
            )
        }

        if (isFound) {
            Text(
                text = "✓",
                fontSize = 18.sp,
                color = Color(0xFF27AE60),
                fontWeight = FontWeight.Bold
            )
        }
    }
}

/**
 * Create sample word snake data for tutorial
 */
fun createSampleWordSnakeData(): String {
    return """
    {
        "gridSize": 5,
        "grid": [
            ["C", "A", "R", "X", "Y"],
            ["O", "T", "E", "Z", "W"],
            ["W", "S", "A", "B", "I"],
            ["M", "Q", "D", "C", "N"],
            ["P", "F", "G", "H", "S"]
        ],
        "words": [
            {
                "word": "CAT",
                "clue": "Furry pet that meows",
                "path": [
                    {"row": 0, "col": 0},
                    {"row": 0, "col": 1},
                    {"row": 1, "col": 1}
                ],
                "color": "#E74C3C",
                "found": false
            },
            {
                "word": "COW",
                "clue": "Farm animal that gives milk",
                "path": [
                    {"row": 0, "col": 0},
                    {"row": 1, "col": 0},
                    {"row": 2, "col": 0}
                ],
                "color": "#3498DB",
                "found": false
            },
            {
                "word": "READ",
                "clue": "Look at words in a book",
                "path": [
                    {"row": 0, "col": 2},
                    {"row": 1, "col": 2},
                    {"row": 2, "col": 2},
                    {"row": 2, "col": 3}
                ],
                "color": "#2ECC71",
                "found": false
            }
        ]
    }
    """.trimIndent()
}

/**
 * Tutorial manager for word snake puzzles
 */
class WordSnakePuzzleTutorialManager {
    fun getTutorialSteps(): List<TutorialStep> =
        listOf(
            TutorialStep(
                title = "Welcome to Word Snake! 🐍",
                description = "Learn how to find hidden words by connecting letters in snaking paths. Let's explore this unique word puzzle!",
                targetComponent = "grid",
                id = "" // Informational - introduction
            ),
            TutorialStep(
                title = "The Letter Grid 📝",
                description = "This grid contains letters that form hidden words. Unlike regular word searches, words can snake in any direction - even diagonally!",
                targetComponent = "grid",
                id = "" // Informational - explaining the grid
            ),
            TutorialStep(
                title = "Reading the Clues 💡",
                description = "Look at the clues below the grid. Each clue tells you what word to find. Let's start by finding a simple 3-letter word.",
                targetComponent = "clues",
                id = "" // Informational - explaining clues
            ),
            TutorialStep(
                title = "Tap to Select Letters ✨",
                description = "Tap any letter to start building a word. Try tapping the 'C' in the top-left corner to begin!",
                targetComponent = "grid",
                id = "select_cell" // INTERACTIVE - user must tap a cell
            ),
            TutorialStep(
                title = "Building Your Snake Path 🔗",
                description = "Great! Now tap the 'A' next to it to continue the word. Notice the blue line connecting your selections.",
                targetComponent = "grid",
                id = "select_cell" // INTERACTIVE - user must tap another cell
            ),
            TutorialStep(
                title = "Complete the Word 🎯",
                description = "Perfect! The blue numbers show your selection order. Now tap the 'T' below the 'A' to complete 'CAT'!",
                targetComponent = "grid",
                id = "complete_word" // INTERACTIVE - user must complete the word
            ),
            TutorialStep(
                title = "Word Found! 🎉",
                description = "Excellent! When you complete a valid word, it turns green and gets marked as found. Your score increases too!",
                targetComponent = "progress",
                id = "" // Informational - celebrating success
            ),
            TutorialStep(
                title = "Correcting Mistakes ↩️",
                description = "If you select wrong letters, tap any selected letter to remove it and all letters after it. Try the CLEAR button to start fresh!",
                targetComponent = "buttons",
                id = "clear_selection" // INTERACTIVE - user should try clearing
            ),
            TutorialStep(
                title = "Snake Paths Can Turn 🌀",
                description = "Words can snake in any direction! Try finding 'COW' by going down from 'C'. Letters can connect horizontally, vertically, or diagonally.",
                targetComponent = "grid",
                id = "complete_word" // INTERACTIVE - find another word
            ),
            TutorialStep(
                title = "Tracking Your Progress 📊",
                description = "The progress bar shows how many words you've found. Your current selection appears here too, helping you build words letter by letter.",
                targetComponent = "progress",
                id = "" // Informational - explaining progress
            ),
            TutorialStep(
                title = "Need Help? 🆘",
                description = "Tap the help button (?) to see all clues again, or use RESET to start the entire puzzle over.",
                targetComponent = "timer",
                id = "help_clicked" // INTERACTIVE - user should try help
            ),
            TutorialStep(
                title = "You're a Snake Master! 🏆",
                description = "Perfect! You now know how to solve Word Snake puzzles. Find all the hidden words by creating snaking letter paths. Happy puzzling! 🐍✨",
                targetComponent = "grid",
                id = "" // Informational - congratulations
            )
        )
}