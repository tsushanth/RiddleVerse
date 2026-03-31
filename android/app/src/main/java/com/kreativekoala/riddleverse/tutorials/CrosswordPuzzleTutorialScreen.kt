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
import org.json.JSONObject
import org.json.JSONArray

// Extension function for tutorial highlighting
fun Modifier.cwtutorialHighlight(): Modifier {
    return this.then(
        Modifier.border(
            width = 3.dp,
            color = Color(0xFF7B1FA2).copy(alpha = 0.8f),
            shape = RoundedCornerShape(8.dp)
        )
    )
}

/**
 * Tutorial screen for Crossword puzzles
 */
@Composable
fun CrosswordPuzzleTutorialScreen(
    onTutorialComplete: () -> Unit,
    onTutorialSkipped: () -> Unit,
    onBack: () -> Unit
) {
    val tutorialManager = remember { CrosswordPuzzleTutorialManager() }
    val steps = tutorialManager.getTutorialSteps()
    var currentStepIndex by remember { mutableStateOf(0) }
    var tutorialState by remember { mutableStateOf(TutorialState.ACTIVE) }

    // Sample tutorial crossword data
    val sampleCrosswordData = createSampleCrosswordData()

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
        // Tutorial version of the crossword screen
        TutorialCrosswordContent(
            currentStep = steps.getOrNull(currentStepIndex),
            sampleCrosswordData = sampleCrosswordData,
            onTutorialAction = ::handleTutorialAction,
            onBack = onBack
        )

        // Tutorial overlay
        if (tutorialState == TutorialState.ACTIVE && currentStepIndex < steps.size) {
            val currentStep = steps[currentStepIndex]

            if (currentStep.id.isNotEmpty()) {
                // Interactive step - requires user action
                MinimalInteractiveOverlay(
                    currentStep = currentStep,
                    totalSteps = steps.size,
                    currentStepNumber = currentStepIndex + 1,
                    onNext = { advanceStep() },
                    onSkip = { skipTutorial() }
                )
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
 * Interactive tutorial version of the crossword screen
 */
@Composable
fun TutorialCrosswordContent(
    currentStep: TutorialStep?,
    sampleCrosswordData: String,
    onTutorialAction: (String) -> Unit,
    onBack: () -> Unit
) {
    // Parse crossword data
    val (grid, words, gridWidth, gridHeight) = remember(sampleCrosswordData) {
        parseCrosswordData(sampleCrosswordData)
    }

    var selectedCell by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    var currentDirection by remember { mutableStateOf(Direction.HORIZONTAL) }
    var currentHint by remember { mutableStateOf("") }
    var currentWord by remember { mutableStateOf<CrosswordWord?>(null) }
    var isCompleted by remember { mutableStateOf(false) }
    var showErrors by remember { mutableStateOf(false) }
    var recompositionTrigger by remember { mutableStateOf(0) }

    // Update grid highlighting when selection changes
    LaunchedEffect(selectedCell, currentDirection) {
        selectedCell?.let { (x, y) ->
            updateGridHighlighting(grid, x, y, currentDirection, words)
            updateCurrentHint(x, y, currentDirection, words) { hint, word ->
                currentHint = hint
                currentWord = word
            }
        }
    }

    // Handle cell clicks
    fun handleCellClick(x: Int, y: Int) {
        if (selectedCell == Pair(x, y)) {
            currentDirection = if (currentDirection == Direction.HORIZONTAL) {
                Direction.VERTICAL
            } else {
                Direction.HORIZONTAL
            }
            onTutorialAction("switch_direction")
        } else {
            selectedCell = Pair(x, y)
            currentDirection = getDefaultDirection(x, y, words, currentDirection)
            onTutorialAction("select_cell")
        }
    }

    // Handle letter input
    fun handleLetterInput(letter: String) {
        selectedCell?.let { (currentX, currentY) ->
            fillCellAndAdvance(
                x = currentX,
                y = currentY,
                letter = letter,
                direction = currentDirection,
                grid = grid,
                words = words,
                gridWidth = gridWidth,
                gridHeight = gridHeight,
                onAdvance = { newX, newY ->
                    selectedCell = Pair(newX, newY)
                },
                onComplete = {
                    recompositionTrigger += 1
                    checkCompletion(grid, words) { completed ->
                        if (completed && !isCompleted) {
                            isCompleted = true
                            onTutorialAction("complete_word")
                        }
                    }
                },
                selectedCell = selectedCell
            )
            recompositionTrigger += 1
            onTutorialAction("type_letter")
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF00BCD4)) // Cyan background like the main screen
    ) {
        Column(
            modifier = Modifier.fillMaxSize().statusBarsPadding()
        ) {
            // Top Bar with highlighting
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .then(
                        if (currentStep?.targetComponent == "timer") {
                            Modifier.cwtutorialHighlight()
                        } else Modifier
                    ),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ArrowBack,
                        contentDescription = stringResource(R.string.back),
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }

                Text(
                    text = "2:00",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )

                Row {
                    IconButton(
                        onClick = { onTutorialAction("help_clicked") },
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Help,
                            contentDescription = stringResource(R.string.hint),
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }

            // Current Hint Display with highlighting
            if (currentHint.isNotEmpty()) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .then(
                            if (currentStep?.targetComponent == "hint") {
                                Modifier.cwtutorialHighlight()
                            } else Modifier
                        ),
                    colors = CardDefaults.cardColors(
                        containerColor = Color.White.copy(alpha = 0.9f)
                    ),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp)
                    ) {
                        Text(
                            text = "${currentWord?.number ?: ""} ${if (currentDirection == Direction.HORIZONTAL) "Across" else "Down"}",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF00BCD4)
                        )
                        Text(
                            text = currentHint,
                            fontSize = 14.sp,
                            color = Color.Black,
                            lineHeight = 16.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Crossword Grid with highlighting
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(top = 8.dp)
                    .then(
                        if (currentStep?.targetComponent == "grid") {
                            Modifier.cwtutorialHighlight()
                        } else Modifier
                    ),
                contentAlignment = Alignment.Center
            ) {
                CrosswordGrid(
                    grid = grid,
                    gridWidth = gridWidth,
                    gridHeight = gridHeight,
                    onCellClick = ::handleCellClick,
                    words = words
                )
            }

            // Bottom action buttons with highlighting
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .then(
                        if (currentStep?.targetComponent == "buttons") {
                            Modifier.cwtutorialHighlight()
                        } else Modifier
                    ),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                // Check button
                OutlinedButton(
                    onClick = {
                        val allCorrect = validateAllAnswers(grid, words)
                        if (!allCorrect) {
                            showErrors = true
                            markErrors(grid, words)
                        } else {
                            isCompleted = true
                        }
                        onTutorialAction("check_answers")
                    },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = Color.White
                    ),
                    border = BorderStroke(2.dp, Color.White)
                ) {
                    Text(stringResource(R.string.check).uppercase(), fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }

                Spacer(modifier = Modifier.width(16.dp))

                // Clear button
                OutlinedButton(
                    onClick = {
                        clearGrid(grid)
                        showErrors = false
                        isCompleted = false
                        selectedCell = null
                        currentHint = ""
                        onTutorialAction("clear_grid")
                    },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = Color.White
                    ),
                    border = BorderStroke(2.dp, Color.White)
                ) {
                    Text(stringResource(R.string.clear).uppercase(), fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }
            }

            // Crossword Keyboard with highlighting
            Box(
                modifier = Modifier.then(
                    if (currentStep?.targetComponent == "keyboard") {
                        Modifier.cwtutorialHighlight()
                    } else Modifier
                )
            ) {
                CrosswordKeyboard(
                    onLetterClick = ::handleLetterInput,
                    onBackspaceClick = {
                        selectedCell?.let { (currentX, currentY) ->
                            handleBackspace(
                                currentX = currentX,
                                currentY = currentY,
                                direction = currentDirection,
                                grid = grid,
                                words = words,
                                onMoveToPrevious = { newX, newY ->
                                    selectedCell = Pair(newX, newY)
                                }
                            )
                            recompositionTrigger += 1
                            onTutorialAction("backspace")
                        }
                    },
                    recompositionTrigger = recompositionTrigger
                )
            }
        }
    }
}

/**
 * Create sample crossword data for tutorial
 */
fun createSampleCrosswordData(): String {
    return """
    {
        "width": 7,
        "height": 7,
        "words": [
            {
                "word": "CAT",
                "hint": "Furry pet that meows",
                "startX": 1,
                "startY": 1,
                "direction": "horizontal",
                "length": 3
            },
            {
                "word": "DOG",
                "hint": "Loyal pet that barks",
                "startX": 1,
                "startY": 3,
                "direction": "horizontal",
                "length": 3
            },
            {
                "word": "COD",
                "hint": "Type of fish",
                "startX": 1,
                "startY": 1,
                "direction": "vertical",
                "length": 3
            }
        ]
    }
    """.trimIndent()
}

/**
 * Tutorial version of crossword keyboard with enhanced highlighting
 */
@Composable
fun TutorialCrosswordKeyboard(
    onLetterClick: (String) -> Unit,
    onBackspaceClick: () -> Unit,
    isHighlighted: Boolean = false
) {
    val keyboardRows = listOf(
        listOf("Q", "W", "E", "R", "T", "Y", "U", "I", "O", "P"),
        listOf("A", "S", "D", "F", "G", "H", "J", "K", "L"),
        listOf("Z", "X", "C", "V", "B", "N", "M")
    )

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
        label = "keyboardPulse"
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF87CEEB))
            .padding(12.dp)
            .then(
                if (isHighlighted) {
                    Modifier.border(
                        width = 3.dp,
                        color = Color(0xFF7B1FA2).copy(alpha = pulseAlpha),
                        shape = RoundedCornerShape(8.dp)
                    )
                } else Modifier
            ),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        keyboardRows.forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                row.forEach { letter ->
                    TutorialKeyboardButton(
                        text = letter,
                        onClick = { onLetterClick(letter) },
                        modifier = Modifier.weight(1f),
                        isHighlighted = isHighlighted
                    )
                }

                if (row == keyboardRows.last()) {
                    TutorialKeyboardButton(
                        text = "⌫",
                        onClick = onBackspaceClick,
                        modifier = Modifier.weight(1.2f),
                        backgroundColor = Color(0xFF5F9EA0),
                        isHighlighted = isHighlighted
                    )
                }
            }
        }
    }
}

/**
 * Tutorial version of keyboard button with enhanced highlighting
 */
@Composable
fun TutorialKeyboardButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    backgroundColor: Color = Color.White,
    isHighlighted: Boolean = false
) {
    val scale by animateFloatAsState(
        targetValue = if (isHighlighted) 1.05f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "buttonScale"
    )

    Button(
        onClick = onClick,
        modifier = modifier
            .padding(horizontal = 3.dp)
            .height(56.dp)
            .scale(scale),
        colors = ButtonDefaults.buttonColors(
            containerColor = backgroundColor
        ),
        shape = RoundedCornerShape(8.dp),
        contentPadding = PaddingValues(6.dp)
    ) {
        Text(
            text = text,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = Color.Black
        )
    }
}

/**
 * Tutorial manager for crossword puzzles
 */
class CrosswordPuzzleTutorialManager {
    fun getTutorialSteps(): List<TutorialStep> =
        listOf(
            TutorialStep(
                title = "Welcome to Crossword Puzzles! 🧩",
                description = "Learn how to solve crossword puzzles by filling in words based on clues. Let's start with the basics!",
                targetComponent = "grid",
                id = "" // Informational - just introduction
            ),
            TutorialStep(
                title = "The Crossword Grid 📝",
                description = "This is your crossword grid. White squares need letters, black squares are blocked. Numbers show where words begin.",
                targetComponent = "grid",
                id = "" // Informational - explaining the grid
            ),
            TutorialStep(
                title = "Tap a Cell to Start ✨",
                description = "Tap on any numbered white cell to select it and see the clue. Try tapping cell 2!",
                targetComponent = "grid",
                id = "select_cell" // INTERACTIVE - user must tap a cell
            ),
            TutorialStep(
                title = "Reading Clues 💡",
                description = "Great! When you select a cell, the clue appears above the grid. This shows you what word to enter.",
                targetComponent = "hint",
                id = "" // Informational - explaining clues
            ),
            TutorialStep(
                title = "Use the Keyboard ⌨️",
                description = "Type letters using the keyboard below. Each letter will fill the current cell and move to the next one.",
                targetComponent = "keyboard",
                id = "type_letter" // INTERACTIVE - user must type a letter
            ),
            TutorialStep(
                title = "Direction Matters ↔️",
                description = "Words can go across (horizontal) or down (vertical). Tap the same cell again to switch directions.",
                targetComponent = "grid",
                id = "switch_direction" // INTERACTIVE - user should try switching direction
            ),
            TutorialStep(
                title = "Completing Words ✅",
                description = "Keep typing to complete the word. Try finishing the current word by entering all letters!",
                targetComponent = "keyboard",
                id = "complete_word" // INTERACTIVE - user must complete a word
            ),
            TutorialStep(
                title = "Reveal the Answer 👀",
                description = "Tap the REVEAL button to check your answer for the selected word. If it's wrong, the correct word will be shown and filled in automatically.",
                targetComponent = "buttons",
                id = "check_answers"
            ),
            TutorialStep(
                title = "Get Help When Stuck 🆘",
                description = "Tap the help button (?) to see all clues, or use CLEAR to start over if needed.",
                targetComponent = "timer",
                id = "" // Informational - explaining help features
            ),
            TutorialStep(
                title = "You're Ready! 🎉",
                description = "Perfect! You now know how to solve crossword puzzles. Fill all words correctly to complete the puzzle!",
                targetComponent = "grid",
                id = "" // Informational - congratulations
            )
        )
}