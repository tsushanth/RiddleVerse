package com.kreativekoala.riddleverse

import com.kreativekoala.riddleverse.ui.theme.*
import android.util.Log
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Help
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.zIndex
import org.json.JSONArray
import org.json.JSONObject
import androidx.compose.ui.res.stringResource
import kotlinx.coroutines.delay
import kotlin.math.*

data class WordSnakeCell(
    val row: Int,
    val col: Int,
    val letter: String,
    var isHighlighted: Boolean = false,
    var isFound: Boolean = false,
    var foundWordId: String? = null,
    var pathIndex: Int = -1, // Track position in found word path
    var isSelected: Boolean = false, // New: for tap selection
    var selectionOrder: Int = -1 // New: order in which cell was selected
)

data class WordSnakeWord(
    val word: String,
    val clue: String,
    val path: List<GridPosition>,
    val color: String,
    var found: Boolean = false,
    val id: String = word
)

data class GridPosition(
    val row: Int,
    val col: Int
)

data class WordSnakePath(
    val word: String,
    val path: List<GridPosition>,
    val color: Color,
    val isFound: Boolean = true
)

data class WordSnakeCompletionEvent(
    val word: String,
    val timestamp: Long,
    val timeFromStart: Long,
    val method: String = "tap_trace"
)

@Composable
fun WordSnakeScreen(
    difficulty: String = "Medium",
    timer: String = "4:00",
    puzzleData: String, // JSON string containing word snake data
    onSubmitAnswer: (Boolean) -> Unit,
    fetchNextPuzzle: (Int) -> Unit,
    onBack: () -> Unit = {}
) {
    val haptics = LocalHapticFeedback.current
    val feedbackManager = rememberUnifiedFeedbackManager()
    val configuration = LocalConfiguration.current

    // Get screen dimensions
    val screenHeight = configuration.screenHeightDp.dp
    val screenWidth = configuration.screenWidthDp.dp

    // Score tracking state
    var totalScore by remember { mutableStateOf(0) }
    var gameStartTime by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var wordsFoundEvents by remember { mutableStateOf<List<WordSnakeCompletionEvent>>(emptyList()) }
    var currentStreak by remember { mutableStateOf(0) }
    var bestStreak by remember { mutableStateOf(0) }
    var hintUsed by remember { mutableStateOf(false) }

    // Parse word snake data
    val (grid, words, gridSize) = remember(puzzleData) {
        parseWordSnakeData(puzzleData)
    }

    // Calculate optimal grid size based on available space
    val topBarHeight = 80.dp
    val progressBarHeight = 80.dp
    val resetButtonHeight = 60.dp
    val systemPadding = 48.dp
    val hintsReservedSpace = 200.dp

    val availableGridHeight = screenHeight - topBarHeight - progressBarHeight -
            resetButtonHeight - hintsReservedSpace - systemPadding

    // Calculate cell size based on grid dimensions and available space
    val maxCellSizeByWidth = (screenWidth - 32.dp) / gridSize
    val maxCellSizeByHeight = availableGridHeight / gridSize
    val optimalCellSize = minOf(maxCellSizeByWidth, maxCellSizeByHeight, 45.dp)

    Log.d("WordSnake", "Screen: ${screenWidth}x${screenHeight}, Grid: ${gridSize}x${gridSize}, Cell: $optimalCellSize")

    // Timer tracking
    val totalTimeSeconds = remember(timer) {
        val parts = timer.split(":")
        if (parts.size == 2) {
            (parts[0].toIntOrNull() ?: 0) * 60 + (parts[1].toIntOrNull() ?: 0)
        } else {
            240 // Default 4 minutes
        }
    }

    var timeRemaining by remember { mutableStateOf(totalTimeSeconds) }
    var displayTimer by remember { mutableStateOf(timer) }

    // State management
    var foundWords by remember { mutableStateOf<Set<String>>(emptySet()) }
    var isCompleted by remember { mutableStateOf(false) }
    var showCompletionDialog by remember { mutableStateOf(false) }
    var showHintsDialog by remember { mutableStateOf(false) }
    var foundPaths by remember { mutableStateOf<List<WordSnakePath>>(emptyList()) }

    // New tap selection state
    var selectedCells by remember { mutableStateOf<List<GridPosition>>(emptyList()) }
    var currentSelectionWord by remember { mutableStateOf("") }
    var recompositionTrigger by remember { mutableStateOf(0) }

    // Timer countdown effect
    LaunchedEffect(timeRemaining, isCompleted) {
        if (timeRemaining > 0 && !isCompleted) {
            delay(1000L)
            timeRemaining--
            displayTimer = "${timeRemaining / 60}:${String.format("%02d", timeRemaining % 60)}"
        } else if (timeRemaining == 0 && !isCompleted) {
            isCompleted = true
            showCompletionDialog = true
            onSubmitAnswer(foundWords.size == words.size)
            fetchNextPuzzle(totalScore)
        }
    }

    // Calculate word snake score
    fun calculateWordSnakeScore(
        wordsFound: Int,
        totalWords: Int,
        timeSpent: Long,
        findEvents: List<WordSnakeCompletionEvent>,
        difficulty: String,
        gridSize: Int,
        bestStreak: Int,
        hintUsed: Boolean
    ): Int {
        if (wordsFound == 0) return 0

        val basePointsPerWord = when (difficulty.lowercase()) {
            "easy" -> 25
            "medium" -> 35
            "hard" -> 45
            "expert" -> 55
            else -> 35
        }

        val baseScore = wordsFound * basePointsPerWord

        // Snake complexity bonus (harder than straight lines)
        val complexityMultiplier = when {
            gridSize >= 12 -> 2.2f
            gridSize >= 10 -> 1.8f
            gridSize >= 8 -> 1.5f
            else -> 1.2f
        }

        val completionBonus = if (wordsFound == totalWords) {
            (baseScore * 0.6f).toInt()
        } else {
            0
        }

        val avgTimePerWord = if (findEvents.isNotEmpty()) {
            timeSpent / findEvents.size.toFloat()
        } else {
            Float.MAX_VALUE
        }

        val speedBonus = when {
            avgTimePerWord <= 15000f -> (baseScore * 0.4f).toInt()
            avgTimePerWord <= 30000f -> (baseScore * 0.2f).toInt()
            avgTimePerWord <= 45000f -> (baseScore * 0.1f).toInt()
            else -> 0
        }

        val streakBonus = when {
            bestStreak >= 4 -> (baseScore * 0.3f).toInt()
            bestStreak >= 3 -> (baseScore * 0.2f).toInt()
            bestStreak >= 2 -> (baseScore * 0.1f).toInt()
            else -> 0
        }

        val hintPenalty = if (hintUsed) {
            (baseScore * 0.15f).toInt()
        } else {
            0
        }

        val finalScore = maxOf(
            ((baseScore * complexityMultiplier).toInt() + completionBonus + speedBonus + streakBonus - hintPenalty),
            baseScore / 3
        )

        Log.d("WordSnake", "🏆 Word snake score calculation:")
        Log.d("WordSnake", "  Words found: $wordsFound/$totalWords")
        Log.d("WordSnake", "  Base score: $baseScore (${basePointsPerWord} per word)")
        Log.d("WordSnake", "  Complexity multiplier: ${complexityMultiplier}x (${gridSize}x${gridSize})")
        Log.d("WordSnake", "  Completion bonus: $completionBonus")
        Log.d("WordSnake", "  Speed bonus: $speedBonus")
        Log.d("WordSnake", "  Streak bonus: $streakBonus")
        Log.d("WordSnake", "  Hint penalty: $hintPenalty")
        Log.d("WordSnake", "  Final score: $finalScore")

        return finalScore
    }

    // Function to handle word found
    fun handleWordFound(word: WordSnakeWord, method: String = "tap_select") {
        if (!foundWords.contains(word.word)) {
            val currentTime = System.currentTimeMillis()
            val timeFromStart = currentTime - gameStartTime

            foundWords = foundWords + word.word

            val findEvent = WordSnakeCompletionEvent(
                word = word.word,
                timestamp = currentTime,
                timeFromStart = timeFromStart,
                method = method
            )
            wordsFoundEvents = wordsFoundEvents + findEvent

            currentStreak++
            if (currentStreak > bestStreak) {
                bestStreak = currentStreak
            }

            // Add found path
            val pathColor = Color(android.graphics.Color.parseColor(word.color))
            val newPath = WordSnakePath(
                word = word.word,
                path = word.path,
                color = pathColor,
                isFound = true
            )
            foundPaths = foundPaths + newPath

            // Mark cells as found
            markWordAsFound(word.path, grid, word.word)
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)

            // Clear selection after finding word
            clearSelection(grid)
            selectedCells = emptyList()
            currentSelectionWord = ""

            recompositionTrigger += 1

            totalScore = calculateWordSnakeScore(
                wordsFound = foundWords.size,
                totalWords = words.size,
                timeSpent = timeFromStart,
                findEvents = wordsFoundEvents,
                difficulty = difficulty,
                gridSize = gridSize,
                bestStreak = bestStreak,
                hintUsed = hintUsed
            )

            Log.d("WordSnake", "✅ Found word: ${word.word} via $method. Score: $totalScore")
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
                // Remove this cell and all subsequent selections
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

                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                Log.d("WordSnake", "🔄 Unselected cell at ${position.row},${position.col}. Word: $currentSelectionWord")
            }
        } else {
            // Select cell
            cell.isSelected = true
            cell.selectionOrder = selectedCells.size
            selectedCells = selectedCells + position
            currentSelectionWord = selectedCells.map { pos ->
                grid[pos.row][pos.col].letter
            }.joinToString("")

            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            Log.d("WordSnake", "📍 Selected cell at ${position.row},${position.col}. Word: $currentSelectionWord")

            // Check if current selection matches any word
            val matchedWord = checkWordMatch(selectedCells, words, foundWords)
            if (matchedWord != null) {
                Log.d("WordSnake", "🎯 Word match found: ${matchedWord.word}")
                handleWordFound(matchedWord, "tap_select")
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
    }

    // Check completion
    LaunchedEffect(foundWords) {
        if (foundWords.size == words.size && !isCompleted) {
            isCompleted = true
            showCompletionDialog = true
            onSubmitAnswer(true)
            Log.d("WordSnake", "🎉 Puzzle completed! Final score: $totalScore")
        }
    }

    // Completion Dialog
    if (showCompletionDialog) {
        WordSnakeCompletionDialog(
            timeRemaining = timeRemaining,
            totalTime = totalTimeSeconds,
            wordsFound = foundWords.size,
            totalWords = words.size,
            finalScore = totalScore,
            bestStreak = bestStreak,
            avgTimePerWord = if (wordsFoundEvents.isNotEmpty()) {
                (wordsFoundEvents.last().timeFromStart / wordsFoundEvents.size / 1000f)
            } else 0f,
            onReset = {
                clearWordSnakeGrid(grid)
                foundWords = emptySet()
                foundPaths = emptyList()
                selectedCells = emptyList()
                currentSelectionWord = ""
                isCompleted = false
                showCompletionDialog = false
                totalScore = 0
                wordsFoundEvents = emptyList()
                currentStreak = 0
                bestStreak = 0
                hintUsed = false
                gameStartTime = System.currentTimeMillis()
                timeRemaining = totalTimeSeconds
                recompositionTrigger += 1
            },
            onNext = {
                showCompletionDialog = false
                fetchNextPuzzle(totalScore)
            },
            onDismiss = { showCompletionDialog = false }
        )
    }

    // Hints Dialog
    if (showHintsDialog) {
        WordSnakeHintsDialog(
            words = words,
            foundWords = foundWords,
            onHintUsed = { hintUsed = true },
            onDismiss = { showHintsDialog = false }
        )
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(RvCanvas)
    ) {
        val wide = maxWidth > maxHeight

        // --- shared pieces -------------------------------------------------------------------
        val progressRow: @Composable () -> Unit = {
            GroupEFontCap {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 32.dp)
                        .testTag("snake_words_progress"),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Words: ${foundWords.size}/${words.size}",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = RvInk,
                        maxLines = 1
                    )
                    LinearProgressIndicator(
                        progress = if (words.isEmpty()) 0f else foundWords.size.toFloat() / words.size.toFloat(),
                        modifier = Modifier
                            .weight(1f)
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp)),
                        color = RvSuccess,
                        trackColor = RvOutline
                    )
                    if (currentSelectionWord.isNotEmpty()) {
                        Text(
                            text = currentSelectionWord,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = RvInk,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.widthIn(max = 120.dp)
                        )
                    }
                }
            }
        }

        val clueList: @Composable (Modifier) -> Unit = { m ->
            // Fits without scrolling on normal devices; the scroll state is only a last-resort safety net.
            val cols = if (maxWidth >= 300.dp && words.size > 3) 2 else 1
            Column(
                modifier = m
                    .clip(RoundedCornerShape(12.dp))
                    .background(RvSurface)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                words.chunked(cols).forEach { rowWords ->
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        rowWords.forEach { word ->
                            Box(modifier = Modifier.weight(1f)) {
                                WordSnakeClueItem(
                                    word = word,
                                    isFound = foundWords.contains(word.word),
                                    color = Color(android.graphics.Color.parseColor(word.color))
                                )
                            }
                        }
                        if (rowWords.size < cols) Spacer(Modifier.weight(1f))
                    }
                }
            }
        }

        val controls: @Composable (Modifier) -> Unit = { m ->
            Row(
                modifier = m,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = { clearCurrentSelection() },
                    enabled = selectedCells.isNotEmpty(),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = RvVioletEdge,
                        disabledContentColor = RvInkSoft
                    ),
                    border = BorderStroke(2.dp, if (selectedCells.isNotEmpty()) RvViolet else RvDisabled),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                        .testTag("snake_clear")
                ) {
                    Text("CLEAR", fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                }

                OutlinedButton(
                    onClick = {
                        clearWordSnakeGrid(grid)
                        foundWords = emptySet()
                        foundPaths = emptyList()
                        selectedCells = emptyList()
                        currentSelectionWord = ""
                        isCompleted = false
                        totalScore = 0
                        wordsFoundEvents = emptyList()
                        currentStreak = 0
                        bestStreak = 0
                        hintUsed = false
                        gameStartTime = System.currentTimeMillis()
                        recompositionTrigger += 1
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    },
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = RvInk),
                    border = BorderStroke(2.dp, RvOutline),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                        .testTag("snake_reset")
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = stringResource(R.string.reset),
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("RESET", fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                }
            }
        }

        // Grid sized from the space it is given: a square, cells derived from it.
        val gridBox: @Composable (Modifier, Dp) -> Unit = { m, side ->
            val spacing = 2.dp
            val cell = ((side - spacing * (gridSize - 1)) / gridSize).coerceIn(16.dp, 56.dp)
            Box(
                modifier = m,
                contentAlignment = Alignment.Center
            ) {
                Box(modifier = Modifier.testTag("snake_grid")) {
                    WordSnakeGrid(
                        grid = grid,
                        words = words,
                        gridSize = gridSize,
                        foundPaths = foundPaths,
                        selectedCells = selectedCells,
                        cellSize = cell,
                        onCellTap = { position -> handleCellTap(position) },
                        haptics = haptics
                    )
                }
            }
        }

        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .widthIn(max = 960.dp)
                .fillMaxSize()
                .statusBarsPadding()
                .padding(horizontal = 16.dp)
                .padding(bottom = 8.dp)
        ) {
            GroupECompactHud(
                timer = displayTimer,
                onBack = onBack,
                subtitle = "${stringResource(R.string.score_label)} $totalScore" +
                    (if (currentStreak > 1) " \u2022 \uD83D\uDD25$currentStreak" else ""),
                urgent = timeRemaining <= 30,
                actions = {
                    IconButton(
                        onClick = {
                            showHintsDialog = true
                            hintUsed = true
                        },
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("snake_hints")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Help,
                            contentDescription = "Hints",
                            tint = RvInk
                        )
                    }
                }
            )

            Spacer(modifier = Modifier.height(4.dp))

            BoxWithConstraints(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                val bodyW = maxWidth
                val bodyH = maxHeight
                val controlsH = 48.dp + 8.dp
                val progressH = 32.dp + 4.dp
                val gridMin = 28.dp * gridSize + 2.dp * (gridSize - 1)

                if (wide) {
                    // Two panes: grid on the left, progress + clues + controls on the right.
                    val side = minOf(bodyH, bodyW * 0.55f)
                    Row(
                        modifier = Modifier.fillMaxSize(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        gridBox(Modifier.size(width = side, height = bodyH), side)
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight(),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            progressRow()
                            clueList(Modifier.weight(1f).fillMaxWidth())
                            controls(Modifier.fillMaxWidth())
                        }
                    }
                } else {
                    val rowsNeeded = if (bodyW >= 300.dp && words.size > 3) (words.size + 1) / 2 else words.size
                    val clueNeed = (44.dp * rowsNeeded + 16.dp).coerceIn(72.dp, 200.dp)
                    val avail = bodyH - controlsH - progressH - 16.dp
                    var side = minOf(bodyW, avail - clueNeed)
                    // Never let the grid drop below tappable cells if the clue list can give way.
                    side = maxOf(side, minOf(gridMin, bodyW, avail - 72.dp))
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        progressRow()
                        gridBox(Modifier.fillMaxWidth().height(side), side)
                        clueList(Modifier.weight(1f).fillMaxWidth())
                        controls(Modifier.fillMaxWidth())
                    }
                }
            }
        }

        // Universal Feedback Overlay
        EnhancedUniversalFeedback(feedbackManager)
    }
}

@Composable
fun WordSnakeGrid(
    grid: List<MutableList<WordSnakeCell>>,
    words: List<WordSnakeWord>,
    gridSize: Int,
    foundPaths: List<WordSnakePath>,
    selectedCells: List<GridPosition>,
    cellSize: Dp,
    onCellTap: (GridPosition) -> Unit,
    haptics: HapticFeedback
) {
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
                        WordSnakeCell(
                            cell = cell,
                            size = cellSize,
                            isSelected = selectedCells.contains(position),
                            selectionOrder = selectedCells.indexOf(position),
                            onTap = { onCellTap(position) }
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

@Composable
fun WordSnakeCell(
    cell: WordSnakeCell,
    size: Dp,
    isSelected: Boolean,
    selectionOrder: Int,
    onTap: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(size)
            .background(
                color = when {
                    isSelected -> RvSky.copy(alpha = 0.8f) // Blue for selected
                    cell.isFound -> RvSuccess.copy(alpha = 0.3f) // Green for found
                    else -> Color.White
                },
                shape = RoundedCornerShape(6.dp)
            )
            .border(
                width = if (isSelected) 3.dp else 2.dp,
                color = when {
                    isSelected -> Color(0xFF2980B9) // Darker blue border for selected
                    cell.isFound -> RvSuccess
                    else -> Color.Gray.copy(alpha = 0.3f)
                },
                shape = RoundedCornerShape(6.dp)
            )
            .testTag("snake_cell")
            .clickable { onTap() },
        contentAlignment = Alignment.Center
    ) {
        // Selection order indicator
        if (isSelected && selectionOrder >= 0) {
            Box(
                modifier = Modifier
                    .size(maxOf(size.value * 0.36f, 14f).dp)
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
                    fontSize = maxOf(size.value * 0.24f, 10f).sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
        }

        // Fixed-size cell: the letter does not follow the system font scale (the HUD and clues do).
        GroupEFontCap(max = 1f) {
            Text(
                text = cell.letter,
                fontSize = (size.value * 0.5f).sp,
                fontWeight = FontWeight.Bold,
                color = RvInk,
                textAlign = TextAlign.Center,
                maxLines = 1
            )
        }
    }
}

@Composable
fun WordSnakeClueItem(
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
                color = if (isFound) RvInkSoft else RvInk,
                textDecoration = if (isFound) TextDecoration.LineThrough else TextDecoration.None
            )

            Text(
                text = word.clue,
                fontSize = 12.sp,
                color = RvInkSoft,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.alpha(if (isFound) 0.7f else 1f)
            )
        }

        if (isFound) {
            Text(
                text = "✓",
                fontSize = 18.sp,
                color = RvInk,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
fun WordSnakeCompletionDialog(
    timeRemaining: Int,
    totalTime: Int,
    wordsFound: Int,
    totalWords: Int,
    finalScore: Int,
    bestStreak: Int,
    avgTimePerWord: Float,
    onReset: () -> Unit,
    onNext: () -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            colors = CardDefaults.cardColors(containerColor = RvCanvas),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "🐍 Snake Complete!",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = RvInk,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "${stringResource(R.string.final_score)}: $finalScore",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = RvInk,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(16.dp))

                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = RvSurface
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Performance:",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = RvInk
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("$wordsFound/$totalWords", color = RvInk, fontSize = 12.sp)
                                Text(stringResource(R.string.found), color = RvInkSoft.copy(0.8f), fontSize = 12.sp)
                            }

                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("$bestStreak", color = RvInk, fontSize = 12.sp)
                                Text(stringResource(R.string.best_streak), color = RvInkSoft.copy(0.8f), fontSize = 12.sp)
                            }

                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("${String.format("%.1f", avgTimePerWord)}s", color = RvInk, fontSize = 12.sp)
                                Text("Avg/Word", color = RvInkSoft.copy(0.8f), fontSize = 12.sp)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                val timeUsed = totalTime - timeRemaining
                Text(
                    text = "${stringResource(R.string.time_label)}: ${timeUsed/60}:${String.format("%02d", timeUsed%60)}",
                    fontSize = 16.sp,
                    color = RvInkSoft.copy(alpha = 0.9f),
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(24.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    OutlinedButton(
                        onClick = onReset,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = RvInk
                        ),
                        border = BorderStroke(2.dp, Color.White)
                    ) {
                        Text(stringResource(R.string.reset))
                    }

                    Spacer(modifier = Modifier.width(16.dp))

                    Button(
                        onClick = onNext,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = RvSurfaceRaised
                        )
                    ) {
                        Text(stringResource(R.string.continue_label), color = RvInk)
                    }
                }
            }
        }
    }
}

@Composable
fun WordSnakeHintsDialog(
    words: List<WordSnakeWord>,
    foundWords: Set<String>,
    onHintUsed: () -> Unit,
    onDismiss: () -> Unit
) {
    LaunchedEffect(Unit) {
        onHintUsed()
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            colors = CardDefaults.cardColors(containerColor = RvSurfaceRaised),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Text(
                    text = "Word Snake Hints",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = RvInk,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                LazyColumn(
                    modifier = Modifier.heightIn(max = 400.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(words) { word ->
                        WordSnakeClueItem(
                            word = word,
                            isFound = foundWords.contains(word.word),
                            color = Color(android.graphics.Color.parseColor(word.color))
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = RvCanvas
                    )
                ) {
                    Text(stringResource(R.string.close))
                }
            }
        }
    }
}

// Helper function to draw snake path
fun DrawScope.drawSnakePath(
    path: WordSnakePath,
    cellSize: Float,
    gridSpacing: Float
) {
    if (path.path.size < 2) return

    val strokeWidth = 8.dp.toPx()
    val positions = path.path.map { pos ->
        Offset(
            x = pos.col * (cellSize + gridSpacing) + cellSize / 2,
            y = pos.row * (cellSize + gridSpacing) + cellSize / 2
        )
    }

    // Draw path as connected line segments
    for (i in 0 until positions.size - 1) {
        drawLine(
            color = path.color,
            start = positions[i],
            end = positions[i + 1],
            strokeWidth = strokeWidth
        )
    }

    // Draw circles at path endpoints
    drawCircle(
        color = path.color,
        radius = strokeWidth / 2,
        center = positions.first()
    )
    drawCircle(
        color = path.color,
        radius = strokeWidth / 2,
        center = positions.last()
    )
}

// Helper function to draw current selection path
fun DrawScope.drawSelectionPath(
    path: List<GridPosition>,
    cellSize: Float,
    gridSpacing: Float
) {
    if (path.size < 2) return

    val strokeWidth = 6.dp.toPx()
    val color = RvSky.copy(alpha = 0.8f)

    val positions = path.map { pos ->
        Offset(
            x = pos.col * (cellSize + gridSpacing) + cellSize / 2,
            y = pos.row * (cellSize + gridSpacing) + cellSize / 2
        )
    }

    for (i in 0 until positions.size - 1) {
        drawLine(
            color = color,
            start = positions[i],
            end = positions[i + 1],
            strokeWidth = strokeWidth
        )
    }

    // Draw selection indicators
    positions.forEachIndexed { index, position ->
        drawCircle(
            color = color,
            radius = strokeWidth / 2,
            center = position
        )
    }
}

// Helper functions
fun parseWordSnakeData(puzzleData: String): Triple<List<MutableList<WordSnakeCell>>, List<WordSnakeWord>, Int> {
    try {
        Log.d("WordSnake", "🔍 Parsing puzzle data: $puzzleData")

        val json = JSONObject(puzzleData)
        val gridArray = json.getJSONArray("grid")
        val wordsArray = json.getJSONArray("words")
        val gridSize = json.getInt("gridSize")

        Log.d("WordSnake", "📊 Grid size: ${gridSize}x${gridSize}")
        Log.d("WordSnake", "📊 Words array length: ${wordsArray.length()}")

        // Create grid
        val grid = MutableList(gridSize) { row ->
            MutableList(gridSize) { col ->
                val gridRow = gridArray.getJSONArray(row)
                val letter = gridRow.getString(col)
                WordSnakeCell(row = row, col = col, letter = letter)
            }
        }

        // Parse words
        val words = mutableListOf<WordSnakeWord>()
        for (i in 0 until wordsArray.length()) {
            val wordObj = wordsArray.getJSONObject(i)
            val pathArray = wordObj.getJSONArray("path")

            val path = mutableListOf<GridPosition>()
            for (j in 0 until pathArray.length()) {
                val pathObj = pathArray.getJSONObject(j)
                path.add(GridPosition(
                    row = pathObj.getInt("row"),
                    col = pathObj.getInt("col")
                ))
            }

            val word = WordSnakeWord(
                word = wordObj.getString("word"),
                clue = wordObj.getString("clue"),
                path = path,
                color = wordObj.getString("color"),
                found = wordObj.getBoolean("found"),
                id = wordObj.getString("word") + "_" + i
            )
            words.add(word)

            Log.d("WordSnake", "📝 Parsed word: ${word.word} (${word.path.size} positions)")
            Log.d("WordSnake", "   Clue: ${word.clue}")
            Log.d("WordSnake", "   Color: ${word.color}")
        }

        Log.d("WordSnake", "✅ Successfully parsed ${words.size} words")
        return Triple(grid, words, gridSize)

    } catch (e: Exception) {
        Log.e("WordSnake", "❌ Error parsing word snake data: ${e.message}", e)
        Log.e("WordSnake", "❌ Input data was: $puzzleData")

        // Return empty grid on error
        val emptyGrid = MutableList(8) { row ->
            MutableList(8) { col ->
                WordSnakeCell(row = row, col = col, letter = "?")
            }
        }
        return Triple(emptyGrid, emptyList(), 8)
    }
}

fun checkWordMatch(
    selectedPath: List<GridPosition>,
    words: List<WordSnakeWord>,
    foundWords: Set<String>
): WordSnakeWord? {
    if (selectedPath.size < 2) return null

    // Check each word to see if the selected path matches
    for (word in words) {
        if (foundWords.contains(word.word)) continue

        // Check if paths match (forward or backward)
        if (pathsMatch(selectedPath, word.path) || pathsMatch(selectedPath, word.path.reversed())) {
            Log.d("WordSnake", "✅ Found matching word: ${word.word}")
            return word
        }
    }

    return null
}

fun pathsMatch(path1: List<GridPosition>, path2: List<GridPosition>): Boolean {
    if (path1.size != path2.size) return false

    return path1.zip(path2).all { (pos1, pos2) ->
        pos1.row == pos2.row && pos1.col == pos2.col
    }
}

fun markWordAsFound(
    path: List<GridPosition>,
    grid: List<MutableList<WordSnakeCell>>,
    word: String
) {
    path.forEachIndexed { index, pos ->
        grid[pos.row][pos.col].isFound = true
        grid[pos.row][pos.col].foundWordId = word
        grid[pos.row][pos.col].pathIndex = index
    }
}

fun clearSelection(grid: List<MutableList<WordSnakeCell>>) {
    grid.forEach { row ->
        row.forEach { cell ->
            cell.isSelected = false
            cell.selectionOrder = -1
        }
    }
}

fun clearWordSnakeGrid(grid: List<MutableList<WordSnakeCell>>) {
    grid.forEach { row ->
        row.forEach { cell ->
            cell.isFound = false
            cell.isHighlighted = false
            cell.foundWordId = null
            cell.pathIndex = -1
            cell.isSelected = false
            cell.selectionOrder = -1
        }
    }
}