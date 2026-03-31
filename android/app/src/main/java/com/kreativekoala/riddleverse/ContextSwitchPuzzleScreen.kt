// Simplified ContextSwitchPuzzleScreen.kt with Alphabetization Only

package com.kreativekoala.riddleverse

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import kotlinx.coroutines.delay
import org.json.JSONArray
import org.json.JSONObject
import kotlin.random.Random
import androidx.compose.ui.geometry.Offset
import kotlin.math.roundToInt

// Data classes for the puzzle
data class ContextSwitchPuzzleData(
    val memoryItems: List<String>,
    val interferenceTask: AlphabetizeTask,
    val recognitionItems: List<String>, // Mix of original + distractors
    val correctAnswers: List<String>
)

data class AlphabetizeTask(
    val items: List<String>,
    val instruction: String,
    val correctOrder: List<String>
)

// Generator for dynamic content
object ContextSwitchGenerator {

    private val contentCategories = mapOf(
        "european_countries" to listOf("France", "Germany", "Italy", "Spain", "Netherlands", "Belgium", "Austria", "Portugal", "Greece", "Czech Republic", "Hungary", "Poland", "Sweden", "Norway", "Denmark"),
        "asian_cuisines" to listOf("Sushi", "Pad Thai", "Kimchi", "Ramen", "Biryani", "Pho", "Dumplings", "Curry", "Teriyaki", "Miso", "Satay", "Laksa", "Bulgogi", "Rendang", "Tandoori"),
        "programming_languages" to listOf("Python", "JavaScript", "Java", "Kotlin", "Swift", "C++", "Go", "Rust", "TypeScript", "PHP", "Ruby", "Scala", "Dart", "C#", "Objective-C"),
        "dog_breeds" to listOf("Labrador", "Golden Retriever", "Bulldog", "Beagle", "Poodle", "Rottweiler", "Yorkshire", "Boxer", "Husky", "Dachshund", "Shepherd", "Chihuahua", "Collie", "Mastiff", "Spaniel"),
        "musical_instruments" to listOf("Piano", "Guitar", "Violin", "Drums", "Trumpet", "Saxophone", "Flute", "Cello", "Clarinet", "Trombone", "Harp", "Banjo", "Accordion", "Mandolin", "Xylophone")
    )

    // Simple words for alphabetization tasks only
    private val alphabetizeCategories = mapOf(
        "simple_words" to listOf("Apple", "Banana", "Cherry", "Grape", "Orange", "Peach", "Plum", "Berry", "Lemon", "Mango", "Kiwi", "Melon", "Date", "Fig", "Lime"),
        "animals" to listOf("Bear", "Cat", "Dog", "Eagle", "Fox", "Goat", "Horse", "Iguana", "Jaguar", "Koala", "Lion", "Mouse", "Newt", "Owl", "Pig"),
        "colors" to listOf("Blue", "Green", "Red", "Yellow", "Purple", "Orange", "Pink", "Brown", "Black", "White", "Gray", "Violet", "Indigo", "Crimson", "Teal")
    )

    fun generatePuzzle(difficulty: String): ContextSwitchPuzzleData {
        val category = contentCategories.keys.random()
        val allItems = contentCategories[category] ?: contentCategories.values.first()

        // Determine list size based on difficulty
        val listSize = when (difficulty.lowercase()) {
            "easy" -> 4
            "medium" -> 5
            "hard" -> 6
            else -> 5
        }

        // Select random items for memory phase
        val memoryItems = allItems.shuffled().take(listSize)

        // Generate alphabetize task
        val interferenceTask = generateAlphabetizeTask(difficulty)

        // Generate recognition items (memory items + distractors)
        val distractorCount = when (difficulty.lowercase()) {
            "easy" -> 2  // 4 memory + 2 distractors = 6 total
            "medium" -> 3 // 5 memory + 3 distractors = 8 total
            "hard" -> 4   // 6 memory + 4 distractors = 10 total
            else -> 3
        }

        // Create smart distractors from same category
        val availableDistractors = allItems.filter { it !in memoryItems }
        val distractors = availableDistractors.shuffled().take(distractorCount)

        val recognitionItems = (memoryItems + distractors).shuffled()

        return ContextSwitchPuzzleData(
            memoryItems = memoryItems,
            interferenceTask = interferenceTask,
            recognitionItems = recognitionItems,
            correctAnswers = memoryItems
        )
    }

    private fun generateAlphabetizeTask(difficulty: String): AlphabetizeTask {
        // Select category for alphabetization
        val alphabetCategory = alphabetizeCategories.keys.random()
        val allWords = alphabetizeCategories[alphabetCategory] ?: alphabetizeCategories["simple_words"]!!

        // Number of words to alphabetize based on difficulty
        val wordCount = when (difficulty.lowercase()) {
            "easy" -> 3
            "medium" -> 4
            "hard" -> 5
            else -> 4
        }

        val selectedWords = allWords.shuffled().take(wordCount)
        val correctOrder = selectedWords.sorted()

        return AlphabetizeTask(
            items = selectedWords.shuffled(), // Present in random order
            instruction = "Drag to alphabetize these words:",
            correctOrder = correctOrder
        )
    }
}

// Main Composable Screen
@Composable
fun ContextSwitchPuzzleScreen(
    difficulty: String,
    timer: String,
    hearts: Int,
    level: String,
    puzzleData: String = "", // JSON data if provided
    onSubmitAnswer: (Boolean) -> Unit,
    fetchNextPuzzle: (Int) -> Unit,
    onBack: () -> Unit
) {
    var gamePhase by remember { mutableStateOf("memory") } // "memory", "interference", "recognition"
    var puzzleState by remember {
        mutableStateOf(
            if (puzzleData.isNotEmpty()) {
                parsePuzzleData(puzzleData)
            } else {
                ContextSwitchGenerator.generatePuzzle(difficulty)
            }
        )
    }
    var selectedAnswers by remember { mutableStateOf(setOf<String>()) }
    var userAlphabetOrder by remember { mutableStateOf(listOf<String>()) }
    var showFeedback by remember { mutableStateOf(false) }
    var isCorrect by remember { mutableStateOf(false) }
    var timeRemaining by remember { mutableStateOf(parseTimer(timer)) }
    var finalScore by remember { mutableStateOf(0) }
    var alphabetTaskCompleted by remember { mutableStateOf(false) }
    var hasInteracted by remember { mutableStateOf(false) }

    // Drag and drop state for sequencing tasks
    var draggedItemIndex by remember { mutableStateOf(-1) }
    var dragOffset by remember { mutableStateOf(Offset.Zero) }

    // Timer countdown
    LaunchedEffect(timeRemaining) {
        if (timeRemaining > 0 && !showFeedback) {
            delay(1000)
            timeRemaining--
        } else if (timeRemaining == 0 && !showFeedback) {
            // Time's up
            handleCompletion(false, 0, onSubmitAnswer, fetchNextPuzzle) {
                showFeedback = true
                isCorrect = false
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF1A1A2E))
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Header
        PuzzleHeader(
            difficulty = difficulty,
            timer = formatTime(timeRemaining),
            hearts = hearts,
            level = level,
            onBack = onBack
        )

        Spacer(modifier = Modifier.height(24.dp))

        when (gamePhase) {
            "memory" -> MemoryPhase(
                items = puzzleState.memoryItems,
                onReady = {
                    gamePhase = "interference"
                    userAlphabetOrder = puzzleState.interferenceTask.items
                }
            )

            "interference" -> AlphabetizeInterferencePhase(
                task = puzzleState.interferenceTask,
                userAlphabetOrder = userAlphabetOrder,
                alphabetTaskCompleted = alphabetTaskCompleted,
                hasInteracted = hasInteracted,
                draggedItemIndex = draggedItemIndex,
                dragOffset = dragOffset,
                onAlphabetOrderChanged = { userAlphabetOrder = it },
                onInteractionChanged = { hasInteracted = it },
                onTaskCompletedChanged = { alphabetTaskCompleted = it },
                onItemDrag = { index, offset ->
                    draggedItemIndex = index
                    dragOffset = offset
                },
                onItemDrop = { fromIndex, toIndex ->
                    if (fromIndex != toIndex && fromIndex >= 0 && toIndex >= 0 && fromIndex < userAlphabetOrder.size) {
                        val newItems = userAlphabetOrder.toMutableList()
                        val item = newItems.removeAt(fromIndex)
                        val clampedToIndex = toIndex.coerceIn(0, newItems.size)
                        newItems.add(clampedToIndex, item)
                        userAlphabetOrder = newItems
                        hasInteracted = true
                    }
                    draggedItemIndex = -1
                    dragOffset = Offset.Zero
                },
                onComplete = { gamePhase = "recognition" }
            )

            "recognition" -> RecognitionPhase(
                items = puzzleState.recognitionItems,
                selectedAnswers = selectedAnswers,
                onSelectionChanged = { selectedAnswers = it },
                onSubmit = {
                    val score = calculateScore(selectedAnswers, puzzleState.correctAnswers, timeRemaining)
                    val correct = selectedAnswers == puzzleState.correctAnswers.toSet()

                    finalScore = score
                    isCorrect = correct

                    handleCompletion(correct, score, onSubmitAnswer, fetchNextPuzzle) {
                        showFeedback = true
                    }
                }
            )
        }
    }

    // Feedback Dialog
    if (showFeedback) {
        FeedbackDialog(
            isCorrect = isCorrect,
            score = finalScore,
            correctAnswers = puzzleState.correctAnswers,
            userAnswers = selectedAnswers.toList(),
            onDismiss = { fetchNextPuzzle(finalScore) }
        )
    }
}

@Composable
private fun MemoryPhase(
    items: List<String>,
    onReady: () -> Unit
) {
    var showItems by remember { mutableStateOf(true) }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Study these items",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Memorize all items in the list below",
            fontSize = 16.sp,
            color = Color.Gray,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(32.dp))

        if (showItems) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF2A2A3E))
            ) {
                LazyColumn(
                    modifier = Modifier.padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(items) { item ->
                        Text(
                            text = "• $item",
                            fontSize = 18.sp,
                            color = Color.White,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            Button(
                onClick = {
                    showItems = false
                    onReady()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 32.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50))
            ) {
                Text(
                    text = stringResource(R.string.im_ready),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun AlphabetizeInterferencePhase(
    task: AlphabetizeTask,
    userAlphabetOrder: List<String>,
    alphabetTaskCompleted: Boolean,
    hasInteracted: Boolean,
    draggedItemIndex: Int,
    dragOffset: Offset,
    onAlphabetOrderChanged: (List<String>) -> Unit,
    onInteractionChanged: (Boolean) -> Unit,
    onTaskCompletedChanged: (Boolean) -> Unit,
    onItemDrag: (Int, Offset) -> Unit,
    onItemDrop: (Int, Int) -> Unit,
    onComplete: () -> Unit
) {
    // Check if current order is correct
    LaunchedEffect(userAlphabetOrder) {
        val isCorrectOrder = userAlphabetOrder == task.correctOrder
        onTaskCompletedChanged(isCorrectOrder)
    }

    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Alphabetization Task",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = task.instruction,
                fontSize = 16.sp,
                color = Color.Gray,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(32.dp))

            // Drag and drop alphabetization interface
            AlphabetizeDragDropInterface(
                items = userAlphabetOrder,
                draggedItemIndex = draggedItemIndex,
                dragOffset = dragOffset,
                onItemDrag = onItemDrag,
                onItemDrop = onItemDrop,
                onOrderChanged = { newOrder ->
                    onAlphabetOrderChanged(newOrder)
                    onInteractionChanged(true)
                }
            )

            Spacer(modifier = Modifier.height(80.dp)) // Space for floating button
        }

        // Floating Continue Button - only show when task is completed
        if (alphabetTaskCompleted) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                contentAlignment = Alignment.BottomCenter
            ) {
                Button(
                    onClick = onComplete,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50))
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(R.string.continue_label),
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "→",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AlphabetizeDragDropInterface(
    items: List<String>,
    draggedItemIndex: Int,
    dragOffset: Offset,
    onItemDrag: (Int, Offset) -> Unit,
    onItemDrop: (Int, Int) -> Unit,
    onOrderChanged: (List<String>) -> Unit
) {
    val haptics = LocalHapticFeedback.current

    Column(
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = "Drag to reorder alphabetically:",
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium,
            color = Color.White,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        items.forEachIndexed { index, word ->
            AlphabetizeItem(
                word = word,
                index = index,
                isDragged = draggedItemIndex == index,
                dragOffset = if (draggedItemIndex == index) dragOffset else Offset.Zero,
                onMoveUp = {
                    if (index > 0) {
                        val newItems = items.toMutableList()
                        newItems.add(index - 1, newItems.removeAt(index))
                        onOrderChanged(newItems)
                    }
                },
                onMoveDown = {
                    if (index < items.size - 1) {
                        val newItems = items.toMutableList()
                        newItems.add(index + 1, newItems.removeAt(index))
                        onOrderChanged(newItems)
                    }
                },
                onDrag = { offset -> onItemDrag(index, offset) },
                onDrop = { toIndex -> onItemDrop(index, toIndex) },
                haptics = haptics
            )

            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

@Composable
private fun AlphabetizeItem(
    word: String,
    index: Int,
    isDragged: Boolean,
    dragOffset: Offset,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onDrag: (Offset) -> Unit,
    onDrop: (Int) -> Unit,
    haptics: androidx.compose.ui.hapticfeedback.HapticFeedback
) {
    var dragState by remember { mutableStateOf(Offset.Zero) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .offset {
                if (isDragged) IntOffset(dragState.x.roundToInt(), dragState.y.roundToInt())
                else IntOffset.Zero
            }
            .zIndex(if (isDragged) 1f else 0f)
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { _ ->
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        dragState = Offset.Zero
                        onDrag(Offset.Zero)
                    },
                    onDrag = { _, dragAmount ->
                        dragState += dragAmount
                        onDrag(dragState)
                    },
                    onDragEnd = {
                        // Calculate drop position based on drag offset
                        val itemHeight = 64.dp.toPx()
                        val offsetItems = (dragState.y / itemHeight).roundToInt()
                        val newIndex = (index + offsetItems).coerceAtLeast(0)
                        onDrop(newIndex)
                        dragState = Offset.Zero
                    }
                )
            },
        colors = CardDefaults.cardColors(
            containerColor = if (isDragged)
                Color(0xFF00BCD4).copy(alpha = 0.3f)
            else
                Color(0xFF2A2A3E)
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.DragHandle,
                contentDescription = "Drag",
                tint = Color.White.copy(alpha = 0.7f),
                modifier = Modifier.size(20.dp)
            )

            Spacer(modifier = Modifier.width(12.dp))

            Text(
                text = "${index + 1}.",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White.copy(alpha = 0.7f),
                modifier = Modifier.width(32.dp)
            )

            Text(
                text = word,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                color = Color.White,
                modifier = Modifier.weight(1f)
            )

            // Up/Down buttons for alternative interaction
            Column {
                IconButton(
                    onClick = onMoveUp,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowUp,
                        contentDescription = "Move Up",
                        tint = Color.White.copy(alpha = 0.7f),
                        modifier = Modifier.size(16.dp)
                    )
                }

                IconButton(
                    onClick = onMoveDown,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowDown,
                        contentDescription = "Move Down",
                        tint = Color.White.copy(alpha = 0.7f),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun RecognitionPhase(
    items: List<String>,
    selectedAnswers: Set<String>,
    onSelectionChanged: (Set<String>) -> Unit,
    onSubmit: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Recognition Test",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Select all items from the original list",
            fontSize = 16.sp,
            color = Color.Gray,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(32.dp))

        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            items(items) { item ->
                val isSelected = item in selectedAnswers

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            val newSelection = if (isSelected) {
                                selectedAnswers - item
                            } else {
                                selectedAnswers + item
                            }
                            onSelectionChanged(newSelection)
                        }
                        .border(
                            width = if (isSelected) 3.dp else 1.dp,
                            color = if (isSelected) Color(0xFF4CAF50) else Color.Gray,
                            shape = RoundedCornerShape(12.dp)
                        ),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isSelected) Color(0xFF2A4A2A) else Color(0xFF2A2A3E)
                    )
                ) {
                    Text(
                        text = item,
                        fontSize = 16.sp,
                        color = Color.White,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        Button(
            onClick = onSubmit,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 32.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF6B35)),
            enabled = selectedAnswers.isNotEmpty()
        ) {
            Text(
                text = stringResource(R.string.submit_answer),
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

// Helper functions
private fun parsePuzzleData(jsonData: String): ContextSwitchPuzzleData {
    return try {
        val json = JSONObject(jsonData)
        val memoryArray = json.getJSONArray("memoryItems")
        val recognitionArray = json.getJSONArray("recognitionItems")
        val correctArray = json.getJSONArray("correctAnswers")
        val interferenceObj = json.getJSONObject("interferenceTask")

        ContextSwitchPuzzleData(
            memoryItems = (0 until memoryArray.length()).map { memoryArray.getString(it) },
            interferenceTask = AlphabetizeTask(
                items = (0 until interferenceObj.getJSONArray("items").length()).map {
                    interferenceObj.getJSONArray("items").getString(it)
                },
                instruction = interferenceObj.getString("instruction"),
                correctOrder = (0 until interferenceObj.getJSONArray("items").length()).map {
                    interferenceObj.getJSONArray("items").getString(it)
                }.sorted()
            ),
            recognitionItems = (0 until recognitionArray.length()).map { recognitionArray.getString(it) },
            correctAnswers = (0 until correctArray.length()).map { correctArray.getString(it) }
        )
    } catch (e: Exception) {
        ContextSwitchGenerator.generatePuzzle("medium")
    }
}

private fun calculateScore(
    userAnswers: Set<String>,
    correctAnswers: List<String>,
    timeRemaining: Int
): Int {
    val correctCount = userAnswers.intersect(correctAnswers.toSet()).size
    val incorrectCount = userAnswers.subtract(correctAnswers.toSet()).size
    val missedCount = correctAnswers.size - correctCount

    val baseScore = (correctCount * 100) - (incorrectCount * 50) - (missedCount * 25)
    val timeBonus = timeRemaining * 2

    return maxOf(0, baseScore + timeBonus)
}

private fun handleCompletion(
    isCorrect: Boolean,
    score: Int,
    onSubmitAnswer: (Boolean) -> Unit,
    fetchNextPuzzle: (Int) -> Unit,
    onShowFeedback: () -> Unit
) {
    onSubmitAnswer(isCorrect)
    onShowFeedback()
}

@Composable
private fun FeedbackDialog(
    isCorrect: Boolean,
    score: Int,
    correctAnswers: List<String>,
    userAnswers: List<String>,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (isCorrect) "Excellent!" else "Not quite",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column {
                Text("Score: $score points")
                Spacer(modifier = Modifier.height(8.dp))
                Text("Correct items: ${correctAnswers.joinToString(", ")}")
                if (!isCorrect) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Your selection: ${userAnswers.joinToString(", ")}")
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) {
                Text(stringResource(R.string.continue_label))
            }
        }
    )
}

@Composable
private fun PuzzleHeader(
    difficulty: String,
    timer: String,
    hearts: Int,
    level: String,
    onBack: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Button(
            onClick = onBack,
            colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent)
        ) {
            Text(stringResource(R.string.back), color = Color.White)
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = level,
                color = Color.White,
                fontSize = 14.sp
            )
            Text(
                text = timer,
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Row {
            repeat(hearts) {
                Text("❤️", fontSize = 16.sp)
            }
        }
    }
}