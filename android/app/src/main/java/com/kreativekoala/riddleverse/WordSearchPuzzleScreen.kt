package com.kreativekoala.riddleverse


import android.util.Log
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Help
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.window.Dialog
import org.json.JSONObject
import kotlinx.coroutines.delay
import kotlin.math.*

data class WordSearchCell(
    val x: Int,
    val y: Int,
    val letter: String,
    var isSelected: Boolean = false,
    var isFound: Boolean = false,
    var isCurrentPath: Boolean = false,
    var foundWordId: String? = null,
    var isRevealed: Boolean = false // New field for revealed words
)

data class WordSearchWord(
    val word: String,
    val hint: String,
    val startX: Int,
    val startY: Int,
    val endX: Int,
    val endY: Int,
    val direction: String,
    val length: Int,
    var isFound: Boolean = false,
    var isRevealed: Boolean = false, // New field for revealed words
    val id: String = word
)

data class WordSearchPath(
    val startX: Int,
    val startY: Int,
    val endX: Int,
    val endY: Int,
    val word: String,
    val color: Color,
    val isRevealed: Boolean = false // New field to track revealed paths
)

data class WordFindEvent(
    val word: String,
    val timestamp: Long,
    val method: String, // "drag", "tap", "double-tap", "revealed"
    val timeFromStart: Long
)

@Composable
fun WordSearchPuzzleScreen(
    difficulty: String = "Easy",
    timer: String = "5:00",
    puzzleData: String, // JSON string containing word search data
    onSubmitAnswer: (Boolean) -> Unit,
    fetchNextPuzzle: (Int) -> Unit,
    onBack: () -> Unit = {}
) {
    val haptics = LocalHapticFeedback.current
    val feedbackManager = rememberUnifiedFeedbackManager()
    val scope = rememberCoroutineScope()
    val configuration = LocalConfiguration.current
    var tapStart by remember { mutableStateOf<Pair<Int, Int>?>(null) }

    // Get screen dimensions
    val screenHeight = configuration.screenHeightDp.dp
    val screenWidth = configuration.screenWidthDp.dp

    // Score tracking state
    var totalScore by remember { mutableStateOf(0) }
    var gameStartTime by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var wordsFoundEvents by remember { mutableStateOf<List<WordFindEvent>>(emptyList()) }
    var currentStreak by remember { mutableStateOf(0) }
    var bestStreak by remember { mutableStateOf(0) }
    var hintUsed by remember { mutableStateOf(false) }

    // Parse word search data
    val (grid, words, gridWidth, gridHeight) = remember(puzzleData) {
        parseWordSearchData(puzzleData)
    }

    // New state for revealed words
    var revealedWords by remember { mutableStateOf<Set<String>>(emptySet()) }

    // Calculate optimal grid size based on available space
    val topBarHeight = 80.dp
    val progressBarHeight = 80.dp
    val resetButtonHeight = 60.dp
    val systemPadding = 48.dp // For system bars and general padding

    val hintsReservedSpace = 200.dp
    val availableGridHeight = screenHeight - topBarHeight - progressBarHeight -
            resetButtonHeight - hintsReservedSpace - systemPadding

    // Calculate cell size based on grid dimensions and available space
    val maxCellSizeByWidth = (screenWidth - 32.dp) / gridWidth // 16dp padding on each side
    val maxCellSizeByHeight = availableGridHeight / gridHeight
    val optimalCellSize = minOf(maxCellSizeByWidth, maxCellSizeByHeight, 42.dp) // Max 42dp for readability

    Log.d("WordSearch", "Screen: ${screenWidth}x${screenHeight}, Grid: ${gridWidth}x${gridHeight}, Cell: $optimalCellSize")

    // Timer tracking
    val totalTimeSeconds = remember(timer) {
        val parts = timer.split(":")
        if (parts.size == 2) {
            (parts[0].toIntOrNull() ?: 0) * 60 + (parts[1].toIntOrNull() ?: 0)
        } else {
            300 // Default 5 minutes
        }
    }

    var timeRemaining by remember { mutableStateOf(totalTimeSeconds) }
    var displayTimer by remember { mutableStateOf(timer) }

    // State management
    var currentDragPath by remember { mutableStateOf<List<Pair<Int, Int>>>(emptyList()) }
    var foundWords by remember { mutableStateOf<Set<String>>(emptySet()) }
    var isCompleted by remember { mutableStateOf(false) }
    var showCompletionDialog by remember { mutableStateOf(false) }
    var showHintsDialog by remember { mutableStateOf(false) }
    var showDoubleTapInfo by remember { mutableStateOf(false) }
    var foundPaths by remember { mutableStateOf<List<WordSearchPath>>(emptyList()) }
    var recompositionTrigger by remember { mutableStateOf(0) }

    // Timer countdown effect
    LaunchedEffect(timeRemaining, isCompleted) {
        if (timeRemaining > 0 && !isCompleted) {
            delay(1000L)
            timeRemaining--
            displayTimer = "${timeRemaining / 60}:${String.format("%02d", timeRemaining % 60)}"
        } else if (timeRemaining == 0 && !isCompleted) {
            // Time's up - complete with current score
            Log.d("WordSearch", "⏰ Time's up! Final score: $totalScore")
            isCompleted = true
            showCompletionDialog = true
            onSubmitAnswer(foundWords.size == words.size)
            fetchNextPuzzle(totalScore)
        }
    }

    // Colors for found words
    val wordColors = remember {
        listOf(
            Color(0xFF4CAF50), // Green
            Color(0xFF2196F3), // Blue
            Color(0xFFFF9800), // Orange
            Color(0xFF9C27B0), // Purple
            Color(0xFFE91E63), // Pink
            Color(0xFF00BCD4), // Cyan
            Color(0xFFFFEB3B), // Yellow
            Color(0xFF795548), // Brown
            Color(0xFF607D8B), // Blue Grey
            Color(0xFF3F51B5), // Indigo
        )
    }

    // Special color for revealed words
    val revealedColor = Color(0xFFFF5722) // Deep Orange

    // Calculate word search score (excluding revealed words)
    fun calculateWordSearchScore(
        wordsFound: Int,
        totalWords: Int,
        timeSpent: Long,
        findEvents: List<WordFindEvent>,
        difficulty: String,
        gridSize: Int,
        bestStreak: Int,
        hintUsed: Boolean
    ): Int {
        if (wordsFound == 0) return 0

        // Base points per word by difficulty
        val basePointsPerWord = when (difficulty.lowercase()) {
            "easy" -> 20
            "medium" -> 30
            "hard" -> 40
            "expert" -> 50
            else -> 30
        }

        // Base score for words found (excluding revealed words)
        val baseScore = wordsFound * basePointsPerWord

        // Grid complexity bonus
        val complexityMultiplier = when {
            gridSize >= 20 -> 2.0f // Very large grid
            gridSize >= 15 -> 1.6f // Large grid
            gridSize >= 12 -> 1.3f // Medium grid
            gridSize >= 10 -> 1.1f // Small grid
            else -> 1.0f
        }

        // Completion bonus (extra points for finding all words, excluding revealed)
        val completionBonus = if (wordsFound == totalWords) {
            (baseScore * 0.5f).toInt()
        } else {
            0
        }

        // Speed bonus based on average time per word (excluding revealed words)
        val nonRevealedEvents = findEvents.filter { it.method != "revealed" }
        val avgTimePerWord = if (nonRevealedEvents.isNotEmpty()) {
            timeSpent / nonRevealedEvents.size.toFloat()
        } else {
            Float.MAX_VALUE
        }

        val speedBonus = when {
            avgTimePerWord <= 10000f -> (baseScore * 0.4f).toInt() // Very fast (≤10s per word)
            avgTimePerWord <= 20000f -> (baseScore * 0.2f).toInt() // Fast (≤20s per word)
            avgTimePerWord <= 30000f -> (baseScore * 0.1f).toInt() // Moderate (≤30s per word)
            else -> 0
        }

        // Streak bonus (reward for finding words consecutively)
        val streakBonus = when {
            bestStreak >= 5 -> (baseScore * 0.3f).toInt()
            bestStreak >= 3 -> (baseScore * 0.2f).toInt()
            bestStreak >= 2 -> (baseScore * 0.1f).toInt()
            else -> 0
        }

        // Pattern recognition bonus
        val patternBonus = (baseScore * 0.15f).toInt()

        // Hint penalty (including reveal penalty)
        val hintPenalty = if (hintUsed) {
            (baseScore * 0.1f).toInt()
        } else {
            0
        }

        // Additional penalty for revealed words
        val revealPenalty = revealedWords.size * basePointsPerWord / 2

        // Efficiency bonus (no wrong selections tracked in this implementation)
        val efficiencyBonus = (baseScore * 0.1f).toInt()

        val finalScore = maxOf(
            ((baseScore * complexityMultiplier).toInt() + completionBonus + speedBonus + streakBonus + patternBonus + efficiencyBonus - hintPenalty - revealPenalty),
            baseScore / 4 // Minimum 25% of base score
        )

        Log.d("WordSearch", "🏆 Word search score calculation:")
        Log.d("WordSearch", "  Words found: $wordsFound/$totalWords")
        Log.d("WordSearch", "  Revealed words: ${revealedWords.size}")
        Log.d("WordSearch", "  Base score: $baseScore (${basePointsPerWord} per word)")
        Log.d("WordSearch", "  Complexity multiplier: ${complexityMultiplier}x (${gridSize}x${gridSize})")
        Log.d("WordSearch", "  Completion bonus: $completionBonus")
        Log.d("WordSearch", "  Speed bonus: $speedBonus (avg ${avgTimePerWord/1000f}s per word)")
        Log.d("WordSearch", "  Streak bonus: $streakBonus (best streak: $bestStreak)")
        Log.d("WordSearch", "  Pattern bonus: $patternBonus")
        Log.d("WordSearch", "  Efficiency bonus: $efficiencyBonus")
        Log.d("WordSearch", "  Hint penalty: $hintPenalty")
        Log.d("WordSearch", "  Reveal penalty: $revealPenalty")
        Log.d("WordSearch", "  Final score: $finalScore")

        return finalScore
    }

    // Function to handle word found
    fun handleWordFound(word: WordSearchWord, path: List<Pair<Int, Int>>, method: String = "drag") {
        if (!foundWords.contains(word.word) && !revealedWords.contains(word.word)) {
            val currentTime = System.currentTimeMillis()
            val timeFromStart = currentTime - gameStartTime

            // Add to found words
            foundWords = foundWords + word.word

            // Track the find event
            val findEvent = WordFindEvent(
                word = word.word,
                timestamp = currentTime,
                method = method,
                timeFromStart = timeFromStart
            )
            wordsFoundEvents = wordsFoundEvents + findEvent

            // Update streak only for non-revealed words
            currentStreak++
            if (currentStreak > bestStreak) {
                bestStreak = currentStreak
            }

            // Add path with color
            val colorIndex = (foundWords.size + revealedWords.size) % wordColors.size
            val newPath = WordSearchPath(
                startX = path.first().first,
                startY = path.first().second,
                endX = path.last().first,
                endY = path.last().second,
                word = word.word,
                color = wordColors[colorIndex],
                isRevealed = false
            )
            foundPaths = foundPaths + newPath

            // Mark cells as found using the correct path
            markWordAsFound(path, grid, word.word, false)
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            recompositionTrigger += 1

            // Recalculate score (excluding revealed words)
            totalScore = calculateWordSearchScore(
                wordsFound = foundWords.size,
                totalWords = words.size,
                timeSpent = timeFromStart,
                findEvents = wordsFoundEvents,
                difficulty = difficulty,
                gridSize = max(gridWidth, gridHeight),
                bestStreak = bestStreak,
                hintUsed = hintUsed
            )

            Log.d("WordSearch", "✅ Found word: ${word.word} via $method. Score: $totalScore")
        }
    }

    // Function to reveal a word (doesn't count towards score)
    fun revealWord(word: WordSearchWord) {
        if (!foundWords.contains(word.word) && !revealedWords.contains(word.word)) {
            val currentTime = System.currentTimeMillis()
            val timeFromStart = currentTime - gameStartTime

            // Add to revealed words
            revealedWords = revealedWords + word.word

            // Track the reveal event (but don't count towards score)
            val revealEvent = WordFindEvent(
                word = word.word,
                timestamp = currentTime,
                method = "revealed",
                timeFromStart = timeFromStart
            )
            wordsFoundEvents = wordsFoundEvents + revealEvent

            // Reset streak on reveal
            currentStreak = 0

            // Find the word path and mark it
            val wordPath = findWordInGrid(word.word, grid)
            if (wordPath.isNotEmpty()) {
                // Add revealed path with special color
                val revealedPath = WordSearchPath(
                    startX = wordPath.first().first,
                    startY = wordPath.first().second,
                    endX = wordPath.last().first,
                    endY = wordPath.last().second,
                    word = word.word,
                    color = revealedColor,
                    isRevealed = true
                )
                foundPaths = foundPaths + revealedPath

                // Mark cells as revealed
                markWordAsFound(wordPath, grid, word.word, true)
                recompositionTrigger += 1

                Log.d("WordSearch", "👁️ Revealed word: ${word.word} (no score)")
            }
        }
    }

    // Check completion
    LaunchedEffect(foundWords, revealedWords) {
        val totalFoundAndRevealed = foundWords.size + revealedWords.size
        if (totalFoundAndRevealed == words.size && !isCompleted) {
            isCompleted = true
            showCompletionDialog = true
            onSubmitAnswer(foundWords.size == words.size) // Only count actually found words

            Log.d("WordSearch", "🎉 Puzzle completed! Final score: $totalScore")
        }
    }

    // Completion Dialog
    if (showCompletionDialog) {
        EnhancedWordSearchCompletionDialog(
            timeRemaining = timeRemaining,
            totalTime = totalTimeSeconds,
            wordsFound = foundWords.size,
            totalWords = words.size,
            revealedWords = revealedWords.size,
            finalScore = totalScore,
            bestStreak = bestStreak,
            avgTimePerWord = if (wordsFoundEvents.filter { it.method != "revealed" }.isNotEmpty()) {
                (wordsFoundEvents.filter { it.method != "revealed" }.last().timeFromStart / wordsFoundEvents.filter { it.method != "revealed" }.size / 1000f)
            } else 0f,
            onReset = {
                clearWordSearchGrid(grid)
                foundWords = emptySet()
                revealedWords = emptySet()
                foundPaths = emptyList()
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
                Log.d("WordSearch", "🎯 Calling fetchNextPuzzle with score: $totalScore")
                fetchNextPuzzle(totalScore)
            },
            onDismiss = { showCompletionDialog = false }
        )
    }

    // Enhanced Hints Dialog
    if (showHintsDialog) {
        EnhancedWordSearchHintsDialog(
            words = words,
            foundWords = foundWords,
            revealedWords = revealedWords,
            onHintUsed = { hintUsed = true },
            onRevealWord = { word -> revealWord(word) },
            onDismiss = { showHintsDialog = false }
        )
    }

    // Double-tap info dialog
    if (showDoubleTapInfo) {
        DoubleTapInfoDialog(
            onDismiss = { showDoubleTapInfo = false }
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF00BCD4)) // Cyan background
    ) {
        Column(
            modifier = Modifier.fillMaxSize().statusBarsPadding()
        ) {
            // Compact Top Bar with live timer
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(topBarHeight)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
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
                    text = displayTimer,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (timeRemaining <= 30) Color.Red else Color.White
                )

                // Right side icons
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    // Double-tap info button
                    IconButton(
                        onClick = { showDoubleTapInfo = true },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = "Double-tap tip",
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    // Hints button
                    IconButton(
                        onClick = {
                            showHintsDialog = true
                            hintUsed = true
                        },
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
            }

            // Compact progress indicator with score
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(progressBarHeight)
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                colors = CardDefaults.cardColors(
                    containerColor = Color.White.copy(alpha = 0.9f)
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
                        val totalFoundAndRevealed = foundWords.size + revealedWords.size
                        Text(
                            text = "Words: $totalFoundAndRevealed/${words.size}" +
                                    if (revealedWords.size > 0) " (${revealedWords.size} revealed)" else "",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF00BCD4)
                        )

                        LinearProgressIndicator(
                            progress = totalFoundAndRevealed.toFloat() / words.size.toFloat(),
                            modifier = Modifier
                                .width(100.dp)
                                .height(6.dp),
                            color = Color(0xFF4CAF50),
                            trackColor = Color.Gray.copy(alpha = 0.3f)
                        )
                    }

                    // Score and streak display (compact)
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
                                    color = Color(0xFF4CAF50)
                                )
                            }

                            if (currentStreak > 1) {
                                Text(
                                    text = "🔥$currentStreak",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFFF5722)
                                )
                            }
                        }
                    }
                }
            }

            // Clear instruction text
            Text(
                text = stringResource(R.string.drag_to_find_words),
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = Color.White.copy(alpha = 0.9f),
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp)
            )

            // Maximized Word Search Grid
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(availableGridHeight - 30.dp)  // Account for instruction text
                    .padding(horizontal = 8.dp), // Minimal horizontal padding
                contentAlignment = Alignment.Center
            ) {
                WordSearchGrid(
                    grid = grid,
                    words = words,
                    gridWidth = gridWidth,
                    gridHeight = gridHeight,
                    foundPaths = foundPaths,
                    currentDragPath = currentDragPath,
                    cellSize = optimalCellSize,
                    setCurrentDragPath = { currentDragPath = it },
                    onDragStart = { x, y ->
                        currentDragPath = listOf(Pair(x, y))
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    },
                    onDragUpdate = { x, y ->
                        if (currentDragPath.isNotEmpty()) {
                            val newPath = currentDragPath + Pair(x, y)
                            currentDragPath = newPath.distinct()
                        }
                    },
                    onDragEnd = {
                        if (currentDragPath.size >= 2) {
                            val result = checkWordInPath(currentDragPath, grid, words)
                            if (result != null) {
                                val (draggedWord, correctPath) = result
                                handleWordFound(draggedWord, correctPath, "drag")
                            } else {
                                // Reset streak on failed attempt
                                currentStreak = 0
                            }
                        }
                        currentDragPath = emptyList()
                    },
                    onDoubleTap = { x, y ->
                        val tappedCell = grid[y][x]
                        val foundWord = findWordAtPosition(x, y, words, grid)
                        if (foundWord != null && !foundWords.contains(foundWord.word) && !revealedWords.contains(foundWord.word)) {
                            val wordPath = getWordPath(foundWord, grid)
                            if (wordPath.isNotEmpty()) {
                                handleWordFound(foundWord, wordPath, "double-tap")
                            }
                        } else {
                            currentStreak = 0
                        }
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    },
                    tapStart = tapStart,
                    onTapStartChange = { tapStart = it },
                    haptics = haptics,
                    handleWordFound = { word, path ->
                        // This lambda expects the correct path to be passed
                        handleWordFound(word, path, "tap")
                    }
                )
            }

            // Compact Hints List
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                colors = CardDefaults.cardColors(
                    containerColor = Color.White.copy(alpha = 0.95f)
                ),
                shape = RoundedCornerShape(8.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(R.string.find_these_words),
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF00BCD4)
                        )

                        if (difficulty.isNotEmpty()) {
                            Text(
                                text = difficulty.uppercase(),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.Gray
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(words) { word ->
                            CompactWordHintItem(
                                word = word,
                                isFound = foundWords.contains(word.word),
                                isRevealed = revealedWords.contains(word.word),
                                color = if (foundWords.contains(word.word)) {
                                    val index = foundWords.toList().indexOf(word.word)
                                    wordColors[index % wordColors.size]
                                } else if (revealedWords.contains(word.word)) {
                                    revealedColor
                                } else {
                                    Color.Gray
                                },
                                onRevealWord = { revealWord(word) }
                            )
                        }
                    }
                }
            }

            // Compact reset button
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(resetButtonHeight)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.Center
            ) {
                OutlinedButton(
                    onClick = {
                        clearWordSearchGrid(grid)
                        foundWords = emptySet()
                        revealedWords = emptySet()
                        foundPaths = emptyList()
                        currentDragPath = emptyList()
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
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = Color.White
                    ),
                    border = BorderStroke(2.dp, Color.White),
                    modifier = Modifier.height(36.dp)
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

        // Universal Feedback Overlay
        EnhancedUniversalFeedback(feedbackManager)
    }
}

// Updated WordSearchGrid to accept cellSize parameter
@Composable
fun WordSearchGrid(
    grid: List<MutableList<WordSearchCell>>,
    words: List<WordSearchWord>,
    gridWidth: Int,
    gridHeight: Int,
    foundPaths: List<WordSearchPath>,
    currentDragPath: List<Pair<Int, Int>>,
    cellSize: Dp,
    setCurrentDragPath: (List<Pair<Int, Int>>) -> Unit,
    onDragStart: (Int, Int) -> Unit,
    onDragUpdate: (Int, Int) -> Unit,
    onDragEnd: () -> Unit,
    onDoubleTap: (Int, Int) -> Unit,
    tapStart: Pair<Int, Int>?,
    onTapStartChange: (Pair<Int, Int>?) -> Unit,
    haptics: HapticFeedback,
    handleWordFound: (WordSearchWord, List<Pair<Int, Int>>) -> Unit
){
    Box(
        modifier = Modifier
            .wrapContentSize()
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { offset ->
                        val x = (offset.x / cellSize.toPx()).toInt()
                        val y = (offset.y / cellSize.toPx()).toInt()
                        if (x in 0 until gridWidth && y in 0 until gridHeight) {
                            onDragStart(x, y)
                        }
                    },
                    onDragEnd = {
                        onDragEnd()
                    }
                ) { change, _ ->
                    val (x, y) = change.position.closestCell(cellSize.toPx(), 1.dp.toPx())
                    if (x in 0 until gridWidth && y in 0 until gridHeight) {
                        onDragUpdate(x, y)
                    }
                }
            }
            .pointerInput(Unit) {
                detectTapGestures(
                    onDoubleTap = { offset ->
                        val x = (offset.x / cellSize.toPx()).toInt()
                        val y = (offset.y / cellSize.toPx()).toInt()
                        if (x in 0 until gridWidth && y in 0 until gridHeight) {
                            onDoubleTap(x, y)
                        }
                    }
                )
            }
    ) {
        // Grid
        Column(
            verticalArrangement = Arrangement.spacedBy(1.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            for (y in 0 until gridHeight) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(1.dp)
                ) {
                    for (x in 0 until gridWidth) {
                        val cell = grid[y][x]
                        WordSearchCell(
                            cell = cell,
                            size = cellSize,
                            isInCurrentPath = currentDragPath.contains(Pair(x, y)),
                            onClick = {
                                if (tapStart == null) {
                                    onTapStartChange(Pair(cell.x, cell.y))
                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                } else {
                                    val tapEnd = Pair(cell.x, cell.y)
                                    val tempPath = getPathBetween(tapStart, tapEnd, grid)
                                    val result = checkWordInPath(tempPath, grid, words)

                                    if (result != null) {
                                        val (foundWord, correctPath) = result
                                        handleWordFound(foundWord, correctPath)
                                    }

                                    onTapStartChange(null)
                                    setCurrentDragPath(emptyList())
                                }
                            }
                        )
                    }
                }
            }
        }

        // Draw lines for found words
        Canvas(
            modifier = Modifier
                .size(
                    width = cellSize * gridWidth + (gridWidth - 1).dp,
                    height = cellSize * gridHeight + (gridHeight - 1).dp
                )
        ) {
            foundPaths.forEach { path ->
                drawWordPath(
                    path = path,
                    cellSize = cellSize.toPx(),
                    gridSpacing = 1.dp.toPx()
                )
            }

            // Draw current drag path
            if (currentDragPath.size >= 2) {
                val startPos = currentDragPath.first()
                val endPos = currentDragPath.last()
                val startCenterX = startPos.first * (cellSize.toPx() + 1.dp.toPx()) + cellSize.toPx() / 2
                val startCenterY = startPos.second * (cellSize.toPx() + 1.dp.toPx()) + cellSize.toPx() / 2
                val endCenterX = endPos.first * (cellSize.toPx() + 1.dp.toPx()) + cellSize.toPx() / 2
                val endCenterY = endPos.second * (cellSize.toPx() + 1.dp.toPx()) + cellSize.toPx() / 2

                drawLine(
                    color = Color(0xFF81C784).copy(alpha = 0.8f),
                    start = androidx.compose.ui.geometry.Offset(startCenterX, startCenterY),
                    end = androidx.compose.ui.geometry.Offset(endCenterX, endCenterY),
                    strokeWidth = 6.dp.toPx()
                )
            }
        }
    }
}

// Updated compact word hint item with show button
@Composable
fun CompactWordHintItem(
    word: WordSearchWord,
    isFound: Boolean,
    isRevealed: Boolean,
    color: Color,
    onRevealWord: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Word indicator
        Box(
            modifier = Modifier
                .size(12.dp)
                .background(
                    color = if (isFound || isRevealed) color else Color.Gray.copy(alpha = 0.3f),
                    shape = RoundedCornerShape(6.dp)
                )
        )

        Spacer(modifier = Modifier.width(8.dp))

        Column(
            modifier = Modifier.weight(1f)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = word.word,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isFound || isRevealed) Color.Gray else Color.Black,
                    textDecoration = if (isFound || isRevealed) TextDecoration.LineThrough else TextDecoration.None,
                    modifier = Modifier.weight(1f)
                )

                // Show revealed indicator
                if (isRevealed) {
                    Text(
                        text = stringResource(R.string.revealed).uppercase(),
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFFF5722),
                        modifier = Modifier.padding(start = 4.dp)
                    )
                }
            }

            Text(
                text = word.hint,
                fontSize = 10.sp,
                color = if (isFound || isRevealed) Color.Gray.copy(alpha = 0.7f) else Color.Gray,
                modifier = Modifier.alpha(if (isFound || isRevealed) 0.6f else 1f),
                maxLines = 1
            )
        }

        // Show/Found indicator
        if (isFound) {
            Text(
                text = "✓",
                fontSize = 14.sp,
                color = Color(0xFF4CAF50),
                fontWeight = FontWeight.Bold
            )
        } else if (isRevealed) {
            Icon(
                imageVector = Icons.Default.Visibility,
                contentDescription = stringResource(R.string.revealed),
                tint = Color(0xFFFF5722),
                modifier = Modifier.size(16.dp)
            )
        } else {
            // Show button for words that haven't been found or revealed
            Button(
                onClick = onRevealWord,
                modifier = Modifier
                    .height(24.dp)
                    .padding(horizontal = 4.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFFF5722).copy(alpha = 0.1f),
                    contentColor = Color(0xFFFF5722)
                ),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
            ) {
                Text(
                    text = "Show",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

// Enhanced completion dialog with revealed words info
@Composable
fun EnhancedWordSearchCompletionDialog(
    timeRemaining: Int,
    totalTime: Int,
    wordsFound: Int,
    totalWords: Int,
    revealedWords: Int,
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
            colors = CardDefaults.cardColors(containerColor = Color(0xFF00BCD4)),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "🎉 ${stringResource(R.string.puzzle_complete)}",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "Final Score: $finalScore",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Performance stats
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = Color.White.copy(alpha = 0.1f)
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
                            color = Color.White
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("$wordsFound/$totalWords", color = Color.White, fontSize = 12.sp)
                                Text(stringResource(R.string.found), color = Color.White.copy(0.8f), fontSize = 10.sp)
                            }

                            if (revealedWords > 0) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("$revealedWords", color = Color.White, fontSize = 12.sp)
                                    Text(stringResource(R.string.revealed), color = Color.White.copy(0.8f), fontSize = 10.sp)
                                }
                            }

                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("$bestStreak", color = Color.White, fontSize = 12.sp)
                                Text(stringResource(R.string.best_streak), color = Color.White.copy(0.8f), fontSize = 10.sp)
                            }

                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("${String.format("%.1f", avgTimePerWord)}s", color = Color.White, fontSize = 12.sp)
                                Text("Avg/Word", color = Color.White.copy(0.8f), fontSize = 10.sp)
                            }
                        }
                    }
                }

                if (revealedWords > 0) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "⚠️ Revealed words don't count towards score",
                        fontSize = 12.sp,
                        color = Color.White.copy(alpha = 0.8f),
                        textAlign = TextAlign.Center,
                        style = androidx.compose.ui.text.TextStyle(fontStyle = androidx.compose.ui.text.font.FontStyle.Italic)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                val timeUsed = totalTime - timeRemaining
                Text(
                    text = "Time: ${timeUsed/60}:${String.format("%02d", timeUsed%60)}",
                    fontSize = 16.sp,
                    color = Color.White.copy(alpha = 0.9f),
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(24.dp))

                // Action buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    OutlinedButton(
                        onClick = onReset,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = Color.White
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
                            containerColor = Color.White
                        )
                    ) {
                        Text(stringResource(R.string.next), color = Color(0xFF00BCD4))
                    }
                }
            }
        }
    }
}

// Enhanced hints dialog with reveal functionality
@Composable
fun EnhancedWordSearchHintsDialog(
    words: List<WordSearchWord>,
    foundWords: Set<String>,
    revealedWords: Set<String>,
    onHintUsed: () -> Unit,
    onRevealWord: (WordSearchWord) -> Unit,
    onDismiss: () -> Unit
) {
    // Track hint usage
    LaunchedEffect(Unit) {
        onHintUsed()
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Text(
                    text = "All Words & Hints",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.Black,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                Text(
                    text = "⚠️ Using hints or revealing words reduces your final score",
                    fontSize = 12.sp,
                    color = Color.Gray,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                LazyColumn(
                    modifier = Modifier.heightIn(max = 400.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(words) { word ->
                        WordHintItem(
                            word = word,
                            isFound = foundWords.contains(word.word),
                            isRevealed = revealedWords.contains(word.word),
                            color = if (foundWords.contains(word.word)) Color(0xFF4CAF50) else Color(0xFFFF5722),
                            onRevealWord = { onRevealWord(word) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF00BCD4)
                    )
                ) {
                    Text(stringResource(R.string.close))
                }
            }
        }
    }
}

// Helper function to find word in grid
fun findWordInGrid(word: String, grid: List<List<WordSearchCell>>): List<Pair<Int, Int>> {
    // Search for the word in all directions
    for (y in grid.indices) {
        for (x in grid[y].indices) {
            // Check all 8 directions
            val directions = listOf(
                Pair(0, 1),   // horizontal right
                Pair(0, -1),  // horizontal left
                Pair(1, 0),   // vertical down
                Pair(-1, 0),  // vertical up
                Pair(1, 1),   // diagonal down-right
                Pair(-1, -1), // diagonal up-left
                Pair(1, -1),  // diagonal down-left
                Pair(-1, 1)   // diagonal up-right
            )

            for ((dx, dy) in directions) {
                val foundPath = checkWordAtDirection(x, y, dx, dy, word, grid)
                if (foundPath.isNotEmpty()) {
                    return foundPath
                }
            }
        }
    }

    return emptyList()
}

// All other functions remain the same but need to be included...
fun Offset.closestCell(cellSize: Float, spacing: Float): Pair<Int, Int> {
    val adjustedX = (x + cellSize / 2) / (cellSize + spacing)
    val adjustedY = (y + cellSize / 2) / (cellSize + spacing)
    return Pair(adjustedX.toInt(), adjustedY.toInt())
}

fun getPathBetween(start: Pair<Int, Int>, end: Pair<Int, Int>, grid: List<List<WordSearchCell>>): List<Pair<Int, Int>> {
    val (x1, y1) = start
    val (x2, y2) = end

    val dx = x2 - x1
    val dy = y2 - y1

    val stepX = dx.sign
    val stepY = dy.sign

    // Ensure direction is valid (horizontal, vertical, or diagonal)
    if (dx != 0 && dy != 0 && abs(dx) != abs(dy)) return emptyList()

    val length = max(abs(dx), abs(dy)) + 1
    val path = mutableListOf<Pair<Int, Int>>()

    for (i in 0 until length) {
        val x = x1 + i * stepX
        val y = y1 + i * stepY
        if (y in grid.indices && x in grid[y].indices) {
            path.add(Pair(x, y))
        } else {
            return emptyList() // Out of bounds
        }
    }

    return path
}

@Composable
fun DoubleTapInfoDialog(
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "💡 Quick Selection Tip",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF00BCD4),
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "🖱️ Double-tap the start and end letter to select the entire word!",
                    fontSize = 16.sp,
                    color = Color.Black,
                    textAlign = TextAlign.Center,
                    lineHeight = 22.sp
                )

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "This works for horizontal, vertical, and diagonal words.",
                    fontSize = 14.sp,
                    color = Color.Gray,
                    textAlign = TextAlign.Center,
                    style = androidx.compose.ui.text.TextStyle(fontStyle = androidx.compose.ui.text.font.FontStyle.Italic)
                )

                Spacer(modifier = Modifier.height(20.dp))

                Button(
                    onClick = onDismiss,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF00BCD4)
                    )
                ) {
                    Text(stringResource(R.string.got_it), color = Color.White)
                }
            }
        }
    }
}

// Helper function to find word at specific position
fun findWordAtPosition(x: Int, y: Int, words: List<WordSearchWord>, grid: List<List<WordSearchCell>>): WordSearchWord? {
    for (word in words) {
        val wordPath = getWordPath(word, grid)
        if (wordPath.contains(Pair(x, y))) {
            return word
        }
    }
    return null
}

// Helper function to get the complete path of a word
fun getWordPath(word: WordSearchWord, grid: List<List<WordSearchCell>>): List<Pair<Int, Int>> {
    val path = mutableListOf<Pair<Int, Int>>()

    // Search for the word in all directions
    for (y in grid.indices) {
        for (x in grid[y].indices) {
            // Check all 8 directions
            val directions = listOf(
                Pair(0, 1),   // horizontal right
                Pair(0, -1),  // horizontal left
                Pair(1, 0),   // vertical down
                Pair(-1, 0),  // vertical up
                Pair(1, 1),   // diagonal down-right
                Pair(-1, -1), // diagonal up-left
                Pair(1, -1),  // diagonal down-left
                Pair(-1, 1)   // diagonal up-right
            )

            for ((dx, dy) in directions) {
                val foundPath = checkWordAtDirection(x, y, dx, dy, word.word, grid)
                if (foundPath.isNotEmpty()) {
                    return foundPath
                }
            }
        }
    }

    return emptyList()
}

// Helper function to check if word exists at specific position and direction
fun checkWordAtDirection(
    startX: Int,
    startY: Int,
    dx: Int,
    dy: Int,
    word: String,
    grid: List<List<WordSearchCell>>
): List<Pair<Int, Int>> {
    val path = mutableListOf<Pair<Int, Int>>()
    var currentWord = ""

    for (i in word.indices) {
        val x = startX + (dx * i)
        val y = startY + (dy * i)

        if (x < 0 || x >= grid[0].size || y < 0 || y >= grid.size) {
            return emptyList()
        }

        currentWord += grid[y][x].letter
        path.add(Pair(x, y))
    }

    return if (currentWord.equals(word, ignoreCase = true)) path else emptyList()
}

@Composable
fun WordSearchCell(
    cell: WordSearchCell,
    size: Dp,
    isInCurrentPath: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(size)
            .background(
                color = when {
                    isInCurrentPath -> Color(0xFF81C784).copy(alpha = 0.7f)
                    cell.isRevealed -> Color(0xFFFF5722).copy(alpha = 0.3f) // Orange for revealed
                    cell.isFound -> Color(0xFF4CAF50).copy(alpha = 0.3f) // Green for found
                    else -> Color.White
                },
                shape = RoundedCornerShape(4.dp)
            )
            .border(
                width = 1.dp,
                color = Color.Gray.copy(alpha = 0.3f),
                shape = RoundedCornerShape(4.dp)
            )
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = cell.letter,
            fontSize = (size.value * 0.6).sp,
            fontWeight = FontWeight.Bold,
            color = if (cell.isFound || cell.isRevealed) Color.White else Color.Black,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
fun WordHintItem(
    word: WordSearchWord,
    isFound: Boolean,
    isRevealed: Boolean,
    color: Color,
    onRevealWord: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Word indicator
        Box(
            modifier = Modifier
                .size(16.dp)
                .background(
                    color = if (isFound || isRevealed) color else Color.Gray.copy(alpha = 0.3f),
                    shape = RoundedCornerShape(8.dp)
                )
        )

        Spacer(modifier = Modifier.width(12.dp))

        Column(
            modifier = Modifier.weight(1f)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = word.word,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isFound || isRevealed) Color.Gray else Color.Black,
                    textDecoration = if (isFound || isRevealed) TextDecoration.LineThrough else TextDecoration.None,
                    modifier = Modifier.weight(1f)
                )

                // Show revealed indicator
                if (isRevealed) {
                    Text(
                        text = stringResource(R.string.revealed).uppercase(),
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFFF5722),
                        modifier = Modifier.padding(start = 4.dp)
                    )
                }
            }

            Text(
                text = word.hint,
                fontSize = 12.sp,
                color = if (isFound || isRevealed) Color.Gray.copy(alpha = 0.7f) else Color.Gray,
                modifier = Modifier.alpha(if (isFound || isRevealed) 0.6f else 1f)
            )
        }

        // Show/Found indicator and reveal button
        if (isFound) {
            Text(
                text = "✓",
                fontSize = 18.sp,
                color = Color(0xFF4CAF50),
                fontWeight = FontWeight.Bold
            )
        } else if (isRevealed) {
            Icon(
                imageVector = Icons.Default.Visibility,
                contentDescription = stringResource(R.string.revealed),
                tint = Color(0xFFFF5722),
                modifier = Modifier.size(20.dp)
            )
        } else {
            // Show button for words that haven't been found or revealed
            Button(
                onClick = onRevealWord,
                modifier = Modifier.height(32.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFFF5722).copy(alpha = 0.1f),
                    contentColor = Color(0xFFFF5722)
                ),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
            ) {
                Text(
                    text = "Show",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

// Helper function to draw word path
fun DrawScope.drawWordPath(
    path: WordSearchPath,
    cellSize: Float,
    gridSpacing: Float
) {
    val startCenterX = path.startX * (cellSize + gridSpacing) + cellSize / 2
    val startCenterY = path.startY * (cellSize + gridSpacing) + cellSize / 2
    val endCenterX = path.endX * (cellSize + gridSpacing) + cellSize / 2
    val endCenterY = path.endY * (cellSize + gridSpacing) + cellSize / 2

    // Use dashed line for revealed words
    if (path.isRevealed) {
        // Draw dashed line effect by drawing multiple shorter lines
        val totalDistance = sqrt((endCenterX - startCenterX).pow(2) + (endCenterY - startCenterY).pow(2))
        val dashLength = 15f
        val gapLength = 10f
        val totalDashUnit = dashLength + gapLength
        val numDashes = (totalDistance / totalDashUnit).toInt()

        for (i in 0 until numDashes) {
            val startRatio = (i * totalDashUnit) / totalDistance
            val endRatio = ((i * totalDashUnit) + dashLength) / totalDistance

            if (endRatio <= 1f) {
                val dashStartX = startCenterX + (endCenterX - startCenterX) * startRatio
                val dashStartY = startCenterY + (endCenterY - startCenterY) * startRatio
                val dashEndX = startCenterX + (endCenterX - startCenterX) * endRatio
                val dashEndY = startCenterY + (endCenterY - startCenterY) * endRatio

                drawLine(
                    color = path.color,
                    start = androidx.compose.ui.geometry.Offset(dashStartX, dashStartY),
                    end = androidx.compose.ui.geometry.Offset(dashEndX, dashEndY),
                    strokeWidth = 8.dp.toPx()
                )
            }
        }
    } else {
        // Draw solid line for found words
        drawLine(
            color = path.color,
            start = androidx.compose.ui.geometry.Offset(startCenterX, startCenterY),
            end = androidx.compose.ui.geometry.Offset(endCenterX, endCenterY),
            strokeWidth = 8.dp.toPx()
        )
    }
}

// Helper functions
fun parseWordSearchData(puzzleData: String): Tuple4<List<MutableList<WordSearchCell>>, List<WordSearchWord>, Int, Int> {
    try {
        Log.d("WordSearchPuzzleScreen", "🔍 Parsing puzzle data: $puzzleData")

        // Parse the JSON data directly (this is the inner question data)
        val json = JSONObject(puzzleData)
        val matrix = json.getJSONArray("matrix")
        val wordsArray = json.getJSONArray("words")
        val width = json.getInt("width")
        val height = json.getInt("height")

        Log.d("WordSearchPuzzleScreen", "📊 Grid dimensions: ${width}x${height}")
        Log.d("WordSearchPuzzleScreen", "📊 Words array length: ${wordsArray.length()}")

        // Create grid from matrix
        val grid = MutableList(height) { y ->
            MutableList(width) { x ->
                val row = matrix.getJSONArray(y)
                val letter = row.getString(x)
                WordSearchCell(x = x, y = y, letter = letter)
            }
        }

        // Parse words with their hints
        val words = mutableListOf<WordSearchWord>()
        for (i in 0 until wordsArray.length()) {
            val wordObj = wordsArray.getJSONObject(i)
            val word = WordSearchWord(
                word = wordObj.getString("word"),
                hint = wordObj.getString("hint"),
                startX = 0, // Will be calculated based on actual positions
                startY = 0,
                endX = 0,
                endY = 0,
                direction = wordObj.getString("direction"),
                length = wordObj.getInt("length"),
                id = wordObj.getString("word") + "_" + i
            )
            words.add(word)

            Log.d("WordSearchPuzzleScreen", "📝 Parsed word: ${word.word} (${word.direction}, ${word.length} letters)")
            Log.d("WordSearchPuzzleScreen", "   Hint: ${word.hint}")
        }

        Log.d("WordSearchPuzzleScreen", "✅ Successfully parsed ${words.size} words")
        return Tuple4(grid, words, width, height)

    } catch (e: Exception) {
        Log.e("WordSearchPuzzleScreen", "❌ Error parsing word search data: ${e.message}", e)
        Log.e("WordSearchPuzzleScreen", "❌ Input data was: $puzzleData")

        // Return empty grid on error
        val emptyGrid = MutableList(15) { y ->
            MutableList(15) { x ->
                WordSearchCell(x = x, y = y, letter = "?")
            }
        }
        return Tuple4(emptyGrid, emptyList(), 15, 15)
    }
}

fun checkWordInPath(
    path: List<Pair<Int, Int>>,
    grid: List<List<WordSearchCell>>,
    words: List<WordSearchWord>
): Pair<WordSearchWord, List<Pair<Int, Int>>>? {
    if (path.size < 2) return null

    // Get the letters from the original path
    val pathLetters = path.map { (x, y) ->
        if (y < grid.size && x < grid[0].size) grid[y][x].letter else ""
    }.joinToString("")

    val reversedPathLetters = pathLetters.reversed()

    Log.d("WordSearchPuzzleScreen", "🔍 Checking original path: $pathLetters (reversed: $reversedPathLetters)")

    // First, try exact match
    val exactMatchWord = words.find { word ->
        val wordUpper = word.word.uppercase()
        val pathUpper = pathLetters.uppercase()
        val reversedPathUpper = reversedPathLetters.uppercase()

        val matches = wordUpper == pathUpper || wordUpper == reversedPathUpper

        if (matches) {
            Log.d("WordSearchPuzzleScreen", "✅ Exact match: ${word.word} with path: $pathLetters")
        }

        matches
    }

    // If exact match found, return it with the original path
    if (exactMatchWord != null) {
        return Pair(exactMatchWord, path)
    }

    // If no exact match, try forgiving path correction
    return checkForgivingWordPath(path, grid, words)
}

// Enhanced function to handle forgiving word detection
// Returns both the word and the correct path to highlight
fun checkForgivingWordPath(
    originalPath: List<Pair<Int, Int>>,
    grid: List<List<WordSearchCell>>,
    words: List<WordSearchWord>
): Pair<WordSearchWord, List<Pair<Int, Int>>>? {
    if (originalPath.size < 2) return null

    val startPoint = originalPath.first()
    val endPoint = originalPath.last()

    Log.d("WordSearchPuzzleScreen", "🎯 Trying forgiving path from ${startPoint} to ${endPoint}")

    // Try to find a valid word path between start and end points
    for (word in words) {
        // Get all possible paths for this word in the grid
        val wordPaths = findAllWordPaths(word.word, grid)

        for (wordPath in wordPaths) {
            // Check if this word path could match our swipe gesture
            if (isPathForgiving(originalPath, wordPath, grid)) {
                Log.d("WordSearchPuzzleScreen", "✅ Forgiving match found: ${word.word}, using correct path")
                return Pair(word, wordPath)
            }
        }
    }

    return null
}

// Check if the swiped path is close enough to a valid word path
fun isPathForgiving(
    swipedPath: List<Pair<Int, Int>>,
    wordPath: List<Pair<Int, Int>>,
    grid: List<List<WordSearchCell>>
): Boolean {
    if (swipedPath.size < 2 || wordPath.size < 2) return false

    val startPoint = swipedPath.first()
    val endPoint = swipedPath.last()
    val wordStart = wordPath.first()
    val wordEnd = wordPath.last()

    // Check if start and end points are close (within 1 cell)
    val startDistance = maxOf(abs(startPoint.first - wordStart.first), abs(startPoint.second - wordStart.second))
    val endDistance = maxOf(abs(endPoint.first - wordEnd.first), abs(endPoint.second - wordEnd.second))

    val reverseStartDistance = maxOf(abs(startPoint.first - wordEnd.first), abs(startPoint.second - wordEnd.second))
    val reverseEndDistance = maxOf(abs(endPoint.first - wordStart.first), abs(endPoint.second - wordStart.second))

    val forwardMatch = startDistance <= 1 && endDistance <= 1
    val reverseMatch = reverseStartDistance <= 1 && reverseEndDistance <= 1

    if (!forwardMatch && !reverseMatch) return false

    // Calculate path similarity - allow for some deviation
    val pathToCheck = if (forwardMatch) wordPath else wordPath.reversed()
    val maxDeviations = maxOf(1, wordPath.size / 4) // Allow up to 25% deviation or at least 1 cell

    var deviations = 0
    val swipeSet = swipedPath.toSet()
    val wordSet = pathToCheck.toSet()

    // Check how many cells in the word path are covered by the swipe
    val coveredCells = wordSet.intersect(swipeSet).size
    val coverageRatio = coveredCells.toFloat() / wordSet.size

    // We need at least 60% coverage for a forgiving match
    if (coverageRatio >= 0.6f) {
        Log.d("WordSearchPuzzleScreen", "🎯 Forgiving match criteria met: ${coverageRatio * 100}% coverage")
        return true
    }

    // Alternative check: if the swipe passes through most key points of the word
    val keyPoints = listOf(pathToCheck.first(), pathToCheck[pathToCheck.size / 2], pathToCheck.last())
    val keyPointsCovered = keyPoints.count { point ->
        swipedPath.any { swipePoint ->
            maxOf(abs(swipePoint.first - point.first), abs(swipePoint.second - point.second)) <= 1
        }
    }

    if (keyPointsCovered >= 2) {
        Log.d("WordSearchPuzzleScreen", "🎯 Forgiving match via key points: $keyPointsCovered/3 covered")
        return true
    }

    return false
}

// Find all possible paths for a word in the grid
fun findAllWordPaths(word: String, grid: List<List<WordSearchCell>>): List<List<Pair<Int, Int>>> {
    val paths = mutableListOf<List<Pair<Int, Int>>>()

    // Search for the word in all directions from every position
    for (y in grid.indices) {
        for (x in grid[y].indices) {
            // Check all 8 directions
            val directions = listOf(
                Pair(0, 1),   // horizontal right
                Pair(0, -1),  // horizontal left
                Pair(1, 0),   // vertical down
                Pair(-1, 0),  // vertical up
                Pair(1, 1),   // diagonal down-right
                Pair(-1, -1), // diagonal up-left
                Pair(1, -1),  // diagonal down-left
                Pair(-1, 1)   // diagonal up-right
            )

            for ((dx, dy) in directions) {
                val foundPath = checkWordAtDirection(x, y, dx, dy, word, grid)
                if (foundPath.isNotEmpty()) {
                    paths.add(foundPath)
                }
            }
        }
    }

    return paths
}

fun markWordAsFound(
    path: List<Pair<Int, Int>>,
    grid: List<MutableList<WordSearchCell>>,
    word: String,
    isRevealed: Boolean = false
) {
    path.forEach { (x, y) ->
        if (isRevealed) {
            grid[y][x].isRevealed = true
        } else {
            grid[y][x].isFound = true
        }
        grid[y][x].foundWordId = word
    }
}

fun clearWordSearchGrid(grid: List<MutableList<WordSearchCell>>) {
    grid.forEach { row ->
        row.forEach { cell ->
            cell.isFound = false
            cell.isSelected = false
            cell.isCurrentPath = false
            cell.isRevealed = false
            cell.foundWordId = null
        }
    }
}