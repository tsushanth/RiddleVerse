// NumberSumPuzzleScreen.kt
package com.kreativekoala.riddleverse

import android.util.Log
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import com.kreativekoala.riddleverse.ui.theme.RvInkSoft
import com.kreativekoala.riddleverse.ui.theme.RvSurface
import com.kreativekoala.riddleverse.ui.theme.RvCoralEdge
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlin.random.Random
import com.kreativekoala.riddleverse.ui.theme.RvInk
import com.kreativekoala.riddleverse.ui.theme.RvOnTone
import com.kreativekoala.riddleverse.ui.theme.RvSuccess
import com.kreativekoala.riddleverse.ui.theme.RvSurfaceRaised

data class NumberSumConfig(
    val targetSum: Int,
    val correctNumbers: List<Int>,
    val distractorNumbers: List<Int>,
    val timeLimit: Int,
    val name: String
)

data class NumberTile(
    val id: Int,
    val number: Int,
    val isCorrect: Boolean,
    val isSelected: Boolean = false,
    val showFeedback: Boolean = false,
    val feedbackType: FeedbackType = FeedbackType.NONE
)

enum class FeedbackType {
    NONE,
    CORRECT,
    WRONG
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NumberSumPuzzleScreen(
    difficulty: String = "Medium",
    timer: String = "2:00",
    hearts: Int = 3,
    level: String = "1/5",
    onGameComplete: (Boolean, Int) -> Unit,
    onBack: () -> Unit
) {
    // Difficulty configurations
    val difficultyConfigs = mapOf(
        "easy" to { generateEasyPuzzle() },
        "medium" to { generateMediumPuzzle() },
        "hard" to { generateHardPuzzle() },
        "expert" to { generateExpertPuzzle() }
    )

    val config = difficultyConfigs[difficulty.lowercase()]?.invoke() ?: generateMediumPuzzle()

    var timeLeft by remember { mutableStateOf(config.timeLimit) }
    var isPaused by remember { mutableStateOf(false) }
    var currentHearts by remember { mutableStateOf(hearts) }
    var gameCompleted by remember { mutableStateOf(false) }
    var gameStarted by remember { mutableStateOf(true) }
    var currentScore by remember { mutableStateOf(0) }
    var currentRound by remember { mutableStateOf(1) }
    var showCompletionMessage by remember { mutableStateOf(false) }
    var completionMessage by remember { mutableStateOf("") }
    var selectedTileIds by remember { mutableStateOf(listOf<Int>()) }


    // Game state
    var currentConfig by remember { mutableStateOf(config) }
    var numberTiles by remember { mutableStateOf(createNumberTiles(currentConfig)) }
    var selectedNumbers by remember { mutableStateOf(listOf<Int>()) }
    var currentSum by remember { mutableStateOf(0) }
    var showWrongFeedback by remember { mutableStateOf(false) }

    var shouldClearWrongFeedback by remember { mutableStateOf(false) }
    var puzzleCompleted by remember { mutableStateOf(false) }

    // Handle number tile click
    fun onNumberClick(tileId: Int, number: Int, isCorrect: Boolean) {
        if (isPaused || gameCompleted || puzzleCompleted) return

        if (selectedTileIds.contains(tileId)) {
            // Deselect tile
            selectedTileIds = selectedTileIds - tileId
            currentSum -= number

            // Update tile state - remove selection and any feedback
            numberTiles = numberTiles.map { tile ->
                if (tile.id == tileId) {
                    tile.copy(
                        isSelected = false,
                        showFeedback = false,
                        feedbackType = FeedbackType.NONE
                    )
                } else tile
            }
        } else {
            // Select tile (don't check correctness yet)
            selectedTileIds = selectedTileIds + tileId
            currentSum += number

            // Update tile state to show selection (no feedback yet)
            numberTiles = numberTiles.map { tile ->
                if (tile.id == tileId) {
                    tile.copy(
                        isSelected = true,
                        showFeedback = false,
                        feedbackType = FeedbackType.NONE
                    )
                } else tile
            }

            // Check sum only when we have the expected number of selections
            val requiredCount = currentConfig.correctNumbers.size
            if (selectedTileIds.size == requiredCount) {
                // Get the selected numbers
                val selectedNumbers = selectedTileIds.map { id ->
                    numberTiles.find { it.id == id }?.number ?: 0
                }

                // Check if the sum matches the target
                if (currentSum == currentConfig.targetSum) {
                    // ANY valid combination that equals the target sum should be accepted
                    // Mark all selected tiles as correct
                    numberTiles = numberTiles.map { tile ->
                        if (tile.id in selectedTileIds) {
                            tile.copy(showFeedback = true, feedbackType = FeedbackType.CORRECT)
                        } else tile
                    }

                    // Trigger puzzle completion
                    puzzleCompleted = true

                    Log.d("NumberSumPuzzle", "✅ Valid solution! Sum: $currentSum, Numbers: $selectedNumbers")
                } else {
                    // Wrong sum - reduce hearts and show wrong feedback
                    currentHearts = maxOf(0, currentHearts - 1)
                    showWrongFeedback = true

                    Log.d("NumberSumPuzzle", "❌ Wrong sum! Target: ${currentConfig.targetSum}, Got: $currentSum")
                    Log.d("NumberSumPuzzle", "   Selected numbers: $selectedNumbers")

                    // Mark all selected tiles as wrong
                    numberTiles = numberTiles.map { tile ->
                        if (tile.id in selectedTileIds) {
                            tile.copy(showFeedback = true, feedbackType = FeedbackType.WRONG)
                        } else tile
                    }

                    // Trigger auto-clear of wrong feedback
                    shouldClearWrongFeedback = true
                }
            }
        }
    }

    // Generate new puzzle configuration based on difficulty
    fun generateNewPuzzle() {
        currentConfig = difficultyConfigs[difficulty.lowercase()]?.invoke() ?: generateMediumPuzzle()
        numberTiles = createNumberTiles(currentConfig)
        selectedTileIds = listOf() // Change to empty list
        currentSum = 0
        showWrongFeedback = false

        Log.d("NumberSumPuzzle", "New puzzle generated:")
        Log.d("NumberSumPuzzle", "Target: ${currentConfig.targetSum}")
        Log.d("NumberSumPuzzle", "Correct numbers: ${currentConfig.correctNumbers}")
        Log.d("NumberSumPuzzle", "Distractors: ${currentConfig.distractorNumbers}")
    }

    LaunchedEffect(puzzleCompleted) {
        if (puzzleCompleted) {
            // Fixed scoring system
            val basePoints = when (difficulty.lowercase()) {
                "easy" -> 50
                "medium" -> 100
                "hard" -> 150
                "expert" -> 200
                else -> 100
            }

            // Small time bonus (max 50 points)
            val timeBonus = minOf(50, timeLeft * 2)

            val totalPoints = basePoints + timeBonus
            currentScore += totalPoints

            completionMessage = "🎉 Perfect! +$totalPoints points"
            showCompletionMessage = true

            delay(2000)
            showCompletionMessage = false

            // Complete the puzzle immediately, let PuzzleScreen handle the count
            gameCompleted = true
            onGameComplete(true, totalPoints)
        }
    }

    // 🔧 Handle wrong feedback auto-clear
    LaunchedEffect(shouldClearWrongFeedback) {
        if (shouldClearWrongFeedback) {
            delay(1500) // Show wrong feedback for 1.5 seconds

            // Clear selections and feedback
            selectedTileIds = listOf()
            currentSum = 0

            // Clear wrong feedback from tiles
            numberTiles = numberTiles.map { tile ->
                tile.copy(
                    isSelected = false,
                    showFeedback = false,
                    feedbackType = FeedbackType.NONE
                )
            }

            shouldClearWrongFeedback = false
        }
    }

    // Initialize first puzzle
    LaunchedEffect(Unit) {
        generateNewPuzzle()
    }

    // Timer countdown
    LaunchedEffect(gameStarted, isPaused, gameCompleted) {
        if (gameStarted && !isPaused && !gameCompleted) {
            while (timeLeft > 0) {
                delay(1000)
                timeLeft--
            }
            if (timeLeft == 0) {
                gameCompleted = true
                onGameComplete(false, 10)
            }
        }
    }

    // Wrong feedback animation
    LaunchedEffect(showWrongFeedback) {
        if (showWrongFeedback) {
            delay(1000)
            showWrongFeedback = false
        }
    }

    // Clear wrong feedback from tiles
    LaunchedEffect(numberTiles) {
        val wrongTiles = numberTiles.filter { it.showFeedback && it.feedbackType == FeedbackType.WRONG }
        if (wrongTiles.isNotEmpty()) {
            delay(1000)
            numberTiles = numberTiles.map { tile ->
                if (tile.showFeedback && tile.feedbackType == FeedbackType.WRONG) {
                    tile.copy(showFeedback = false, feedbackType = FeedbackType.NONE)
                } else tile
            }
        }
    }



    // Handle game over
    LaunchedEffect(currentHearts) {
        if (currentHearts == 0 && gameStarted && !gameCompleted) {
            gameCompleted = true
            onGameComplete(false, 10)
        }
    }

    // Fit-to-screen: HUD fixed on top, target + tile grid take the remaining space, no scrolling.
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(RvSurface)
            .statusBarsPadding()
    ) {
        val wide = maxWidth > maxHeight || maxWidth >= 600.dp
        val compact = maxHeight < 600.dp
        val pad = if (compact) 8.dp else 16.dp

        val header: @Composable () -> Unit = {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = RvSurfaceRaised)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = if (compact) 0.dp else 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = {
                            gameCompleted = true
                            onBack()
                        },
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(Icons.Default.ArrowBack, contentDescription = stringResource(R.string.back), tint = RvInk)
                    }

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "Puzzle $level",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = RvInkSoft,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = String.format("%02d:%02d", timeLeft / 60, timeLeft % 60),
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (timeLeft <= 30) RvCoralEdge else RvInk,
                            maxLines = 1,
                            modifier = Modifier.testTag("hud_timer")
                        )
                    }

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "Score: $currentScore",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = RvInk,
                            maxLines = 1,
                            modifier = Modifier.testTag("hud_score")
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            repeat(currentHearts) {
                                Text("❤️", fontSize = 16.sp)
                            }
                        }
                    }

                    IconButton(onClick = { isPaused = !isPaused }, modifier = Modifier.size(48.dp)) {
                        Icon(Icons.Default.Pause, contentDescription = null, tint = RvInk)
                    }
                }
            }
        }

        val targetCard: @Composable (Modifier) -> Unit = { m ->
            Card(
                modifier = m.testTag("numsum_target"),
                colors = CardDefaults.cardColors(containerColor = RvSuccess)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = if (compact) 8.dp else 16.dp, horizontal = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Target",
                        fontSize = 16.sp,
                        color = RvInk,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = currentConfig.targetSum.toString(),
                        fontSize = if (compact) 40.sp else 48.sp,
                        color = RvInk,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                    // Always reserve the line so the layout does not jump when a tile is picked.
                    Text(
                        text = if (currentSum > 0) "Current: $currentSum" else " ",
                        fontSize = 14.sp,
                        color = RvInk,
                        maxLines = 1
                    )
                }
            }
        }

        // Tile grid sized from the space it is given.
        val tileGrid: @Composable (Modifier) -> Unit = { m ->
            BoxWithConstraints(modifier = m, contentAlignment = Alignment.Center) {
                val n = numberTiles.size.coerceAtLeast(1)
                val cols = if (n <= 4) 2 else 3
                val rows = (n + cols - 1) / cols
                val gap = if (compact) 8.dp else 16.dp
                val byW = (maxWidth - gap * (cols - 1)) / cols
                val byH = (maxHeight - gap * (rows - 1)) / rows
                val tileSize = minOf(byW, byH).coerceIn(48.dp, 96.dp)
                Column(
                    verticalArrangement = Arrangement.spacedBy(gap),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    numberTiles.chunked(cols).forEach { rowTiles ->
                        Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                            rowTiles.forEach { tile ->
                                Box(Modifier.size(tileSize), contentAlignment = Alignment.Center) {
                                    NumberTileComponent(
                                        tile = tile,
                                        onClick = { onNumberClick(tile.id, tile.number, tile.isCorrect) }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(pad)
                .widthIn(max = 720.dp)
                .align(Alignment.TopCenter),
            verticalArrangement = Arrangement.spacedBy(pad)
        ) {
            header()

            if (!gameCompleted) {
                if (wide) {
                    Row(
                        modifier = Modifier.fillMaxWidth().weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(pad),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        targetCard(Modifier.weight(1f))
                        tileGrid(Modifier.weight(1f).fillMaxHeight())
                    }
                } else {
                    targetCard(Modifier.fillMaxWidth())
                    tileGrid(Modifier.fillMaxWidth().weight(1f))
                }
            }

            // Completion message overlay
            if (showCompletionMessage) {
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Card(
                        modifier = Modifier.padding(8.dp),
                        colors = CardDefaults.cardColors(containerColor = RvSuccess)
                    ) {
                        Text(
                            text = completionMessage,
                            modifier = Modifier.padding(16.dp),
                            color = RvInk,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun NumberTileComponent(
    tile: NumberTile,
    onClick: () -> Unit
) {
    val scale by animateFloatAsState(
        targetValue = if (tile.isSelected) 0.95f else 1f,
        animationSpec = tween(150)
    )

    val backgroundColor = when {
        tile.isSelected -> RvSuccess
        tile.showFeedback && tile.feedbackType == FeedbackType.WRONG -> Color.Red
        else -> RvOnTone
    }

    val textColor = when {
        tile.isSelected -> RvInk
        tile.showFeedback && tile.feedbackType == FeedbackType.WRONG -> RvInk
        else -> Color.Black
    }

    Box(
        modifier = Modifier
            .testTag("numtile")
            .size(80.dp)
            .scale(scale)
            .clip(RoundedCornerShape(12.dp))
            .background(backgroundColor)
            .then(if (tile.isSelected) Modifier.border(3.dp, RvInk, RoundedCornerShape(12.dp)) else Modifier)
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        // Show X for wrong feedback
        if (tile.showFeedback && tile.feedbackType == FeedbackType.WRONG) {
            Icon(
                Icons.Default.Close,
                contentDescription = stringResource(R.string.wrong),
                tint = RvInk,
                modifier = Modifier.size(32.dp)
            )
        } else {
            Text(
                text = tile.number.toString(),
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = textColor
            )
        }
    }
}

fun hasDuplicateSolutions(allNumbers: List<Int>, correctNumbers: List<Int>, targetSum: Int): Boolean {
    val requiredSize = correctNumbers.size

    fun dfs(start: Int, current: List<Int>, sum: Int): Boolean {
        if (current.size > requiredSize || sum > targetSum) return false
        if (current.size == requiredSize && sum == targetSum) {
            return current.sorted() != correctNumbers.sorted()
        }

        for (i in start until allNumbers.size) {
            if (dfs(i + 1, current + allNumbers[i], sum + allNumbers[i])) {
                return true
            }
        }
        return false
    }

    return dfs(0, emptyList(), 0)
}


// Create number tiles from configuration
fun createNumberTiles(config: NumberSumConfig): List<NumberTile> {
    val allNumbers = (config.correctNumbers + config.distractorNumbers).shuffled()
    return allNumbers.mapIndexed { index, number ->
        NumberTile(
            id = index,
            number = number,
            isCorrect = config.correctNumbers.contains(number)
        )
    }
}

// Puzzle generators for different difficulties
fun generateEasyPuzzle(): NumberSumConfig {
    val correctNumbers = listOf(Random.nextInt(1, 6), Random.nextInt(1, 6))
    val targetSum = correctNumbers.sum()

    val distractors = generateDistractors(correctNumbers, targetSum, 2) // Reduced from 4

    return NumberSumConfig(
        targetSum = targetSum,
        correctNumbers = correctNumbers,
        distractorNumbers = distractors,
        timeLimit = 60,
        name = "Easy"
    )
}

fun generateMediumPuzzle(): NumberSumConfig {
    while (true) {
        val correctNumbers = listOf(
            Random.nextInt(2, 8),
            Random.nextInt(2, 8),
            Random.nextInt(1, 5)
        )
        val targetSum = correctNumbers.sum()
        val distractors = generateDistractors(correctNumbers, targetSum, 2)
        val allNumbers = correctNumbers + distractors

        if (!hasDuplicateSolutions(allNumbers, correctNumbers, targetSum)) {
            return NumberSumConfig(
                targetSum = targetSum,
                correctNumbers = correctNumbers,
                distractorNumbers = distractors,
                timeLimit = 100,
                name = "Medium"
            )
        }
    }
}


fun generateHardPuzzle(): NumberSumConfig {
    val correctNumbers = listOf(
        Random.nextInt(3, 12),
        Random.nextInt(3, 12),
        Random.nextInt(2, 8),
        Random.nextInt(1, 6)
    )
    val targetSum = correctNumbers.sum()

    val distractors = generateDistractors(correctNumbers, targetSum, 3) // Keep 3 for hard

    return NumberSumConfig(
        targetSum = targetSum,
        correctNumbers = correctNumbers,
        distractorNumbers = distractors,
        timeLimit = 80,
        name = "Hard"
    )
}

fun generateExpertPuzzle(): NumberSumConfig {
    val correctNumbers = listOf(
        Random.nextInt(5, 15),
        Random.nextInt(5, 15),
        Random.nextInt(3, 10),
        Random.nextInt(3, 10),
        Random.nextInt(1, 8)
    )
    val targetSum = correctNumbers.sum()

    val distractors = generateDistractors(correctNumbers, targetSum, 1)

    return NumberSumConfig(
        targetSum = targetSum,
        correctNumbers = correctNumbers,
        distractorNumbers = distractors,
        timeLimit = 60,
        name = "Expert"
    )
}

// Generate distractor numbers that cannot form the target sum
fun generateDistractors(correctNumbers: List<Int>, targetSum: Int, maxCount: Int): List<Int> {
    val distractors = mutableListOf<Int>()
    val usedNumbers = correctNumbers.toMutableSet()

    // Generate strategic distractors - we'll try to get good ones but stop early if needed
    var successfulDistractors = 0
    val targetDistractors = minOf(maxCount, 3) // Cap at 3 for faster processing

    repeat(targetDistractors) {
        var distractor: Int? = null
        var attempts = 0

        // Try to find a good distractor quickly
        while (attempts < 8 && distractor == null) { // Reduced attempts
            val baseNumber = correctNumbers.random()
            val candidate = when (Random.nextInt(4)) {
                0 -> baseNumber + 1 // One more
                1 -> maxOf(1, baseNumber - 1) // One less
                2 -> baseNumber + 2 // Two more
                else -> maxOf(1, baseNumber - 2) // Two less
            }

            // Quick validation - just check if it's not duplicate and not equal to target
            if (!usedNumbers.contains(candidate) &&
                candidate != targetSum &&
                candidate > 0 &&
                !wouldCreateObviousSolution(candidate, correctNumbers, targetSum)) {
                distractor = candidate
            }
            attempts++
        }

        // If we found a good distractor, use it
        if (distractor != null) {
            distractors.add(distractor)
            usedNumbers.add(distractor)
            successfulDistractors++
        }
    }

    // Fill remaining slots with simple distractors if we need more
    while (distractors.size < minOf(maxCount, 2)) { // Ensure at least 2 distractors
        val simpleDistractor = targetSum + Random.nextInt(1, 5)
        if (!usedNumbers.contains(simpleDistractor)) {
            distractors.add(simpleDistractor)
            usedNumbers.add(simpleDistractor)
        }
    }

    return distractors
}

// Quick check to avoid obvious solutions (much faster than full combination check)
fun wouldCreateObviousSolution(distractor: Int, correctNumbers: List<Int>, targetSum: Int): Boolean {
    // Check if distractor alone equals target
    if (distractor == targetSum) return true

    // Check if distractor + any single correct number equals target
    for (correct in correctNumbers) {
        if (distractor + correct == targetSum) return true
    }

    // Check if distractor + any two correct numbers equals target (only for small sets)
    if (correctNumbers.size <= 3) {
        for (i in correctNumbers.indices) {
            for (j in i + 1 until correctNumbers.size) {
                if (distractor + correctNumbers[i] + correctNumbers[j] == targetSum) return true
            }
        }
    }

    return false
}