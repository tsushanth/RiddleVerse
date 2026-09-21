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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.material.icons.filled.Check
import com.kreativekoala.riddleverse.ui.theme.RvInkSoft
import com.kreativekoala.riddleverse.ui.theme.RvOutline
import com.kreativekoala.riddleverse.ui.theme.RvViolet
import com.kreativekoala.riddleverse.ui.theme.RvDisabled
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import kotlinx.coroutines.delay
import org.json.JSONArray
import org.json.JSONObject
import kotlin.random.Random
import androidx.compose.ui.geometry.Offset
import kotlin.math.roundToInt
import com.kreativekoala.riddleverse.ui.theme.RvCanvas
import com.kreativekoala.riddleverse.ui.theme.RvInk
import com.kreativekoala.riddleverse.ui.theme.RvOnTone
import com.kreativekoala.riddleverse.ui.theme.RvSurface

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

    // Fit-to-screen: fixed header, the current phase takes the remaining space, its primary
    // action (I'm ready / Continue / Submit) is pinned at the bottom. Landscape uses two panes.
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(RvCanvas)
    ) {
        val wide = maxWidth > maxHeight || maxWidth >= 600.dp
        val compact = maxHeight < 700.dp
        val pad = if (compact) 8.dp else 16.dp

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(pad)
                .widthIn(max = if (wide) 960.dp else 640.dp)
                .align(Alignment.TopCenter),
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

            Spacer(modifier = Modifier.height(if (compact) 4.dp else 16.dp))

            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                when (gamePhase) {
                    "memory" -> MemoryPhase(
                        items = puzzleState.memoryItems,
                        wide = wide,
                        compact = compact,
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
                        wide = wide,
                        compact = compact,
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
                        wide = wide,
                        compact = compact,
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

/** Phase layout: title block + content + pinned primary action; two panes when [wide]. */
@Composable
internal fun CsPhaseScaffold(
    wide: Boolean,
    compact: Boolean,
    title: String,
    subtitle: String?,
    action: @Composable () -> Unit,
    content: @Composable (Modifier) -> Unit
) {
    val head: @Composable () -> Unit = {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = title,
                fontSize = if (compact) 20.sp else 24.sp,
                fontWeight = FontWeight.Bold,
                color = RvInk,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            if (subtitle != null) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = subtitle,
                    fontSize = if (compact) 14.sp else 16.sp,
                    color = RvInkSoft,
                    textAlign = TextAlign.Center,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
    if (wide) {
        Row(modifier = Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Column(
                modifier = Modifier.weight(0.8f).fillMaxHeight(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                head()
                Spacer(Modifier.weight(1f))
                action()
            }
            content(Modifier.weight(1.2f).fillMaxHeight())
        }
    } else {
        Column(modifier = Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
            head()
            Spacer(modifier = Modifier.height(if (compact) 8.dp else 16.dp))
            content(Modifier.fillMaxWidth().weight(1f))
            Spacer(modifier = Modifier.height(if (compact) 8.dp else 12.dp))
            action()
        }
    }
}

@Composable
internal fun CsPrimaryAction(text: String, enabled: Boolean = true, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .testTag("ctx_action"),
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = RvViolet,
            contentColor = RvOnTone,
            disabledContainerColor = RvDisabled,
            disabledContentColor = RvInk
        )
    ) {
        Text(
            text = text,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun MemoryPhase(
    items: List<String>,
    wide: Boolean,
    compact: Boolean,
    onReady: () -> Unit
) {
    var showItems by remember { mutableStateOf(true) }

    CsPhaseScaffold(
        wide = wide,
        compact = compact,
        title = "Study these items",
        subtitle = "Memorize all items in the list below",
        action = {
            if (showItems) {
                CsPrimaryAction(text = stringResource(R.string.im_ready)) {
                    showItems = false
                    onReady()
                }
            }
        }
    ) { m ->
        if (showItems) {
            CsMemoryList(items = items, modifier = m)
        }
    }
}

/** Memory list: items flow into as many columns as needed to fit the height (no scrolling). */
@Composable
internal fun CsMemoryList(items: List<String>, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = RvSurface)
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize().padding(12.dp), contentAlignment = Alignment.Center) {
            val rowH = 40.dp
            val perCol = (maxHeight / rowH).toInt().coerceAtLeast(1)
            val cols = ((items.size + perCol - 1) / perCol).coerceIn(1, 3)
            val chunk = (items.size + cols - 1) / cols
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                items.chunked(chunk.coerceAtLeast(1)).forEach { colItems ->
                    Column(
                        modifier = Modifier.weight(1f, fill = false),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        colItems.forEach { item ->
                            Text(
                                text = "• $item",
                                fontSize = aCapSp(18f, 1.3f),
                                color = RvInk,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.heightIn(min = 36.dp).testTag("ctx_memory_item")
                            )
                        }
                    }
                }
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
    wide: Boolean,
    compact: Boolean,
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

    CsPhaseScaffold(
        wide = wide,
        compact = compact,
        title = "Alphabetization Task",
        subtitle = task.instruction,
        // The button is always visible (disabled until the order is right) so the layout never
        // jumps and the next step is always discoverable.
        action = {
            CsPrimaryAction(text = "${stringResource(R.string.continue_label)} →", enabled = alphabetTaskCompleted, onClick = onComplete)
        }
    ) { m ->
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
            },
            modifier = m
        )
    }
}

@Composable
private fun AlphabetizeDragDropInterface(
    items: List<String>,
    draggedItemIndex: Int,
    dragOffset: Offset,
    onItemDrag: (Int, Offset) -> Unit,
    onItemDrop: (Int, Int) -> Unit,
    onOrderChanged: (List<String>) -> Unit,
    modifier: Modifier = Modifier
) {
    val haptics = LocalHapticFeedback.current

    BoxWithConstraints(modifier = modifier) {
        val gap = 8.dp
        val n = items.size.coerceAtLeast(1)
        // Rows share the available height (48-64dp); the drop index maths uses the same height.
        val itemH = ((maxHeight - 28.dp - gap * (n - 1)) / n).coerceIn(48.dp, 64.dp)
        Column(modifier = Modifier.fillMaxWidth().align(Alignment.TopCenter)) {
            Text(
                text = "Drag to reorder alphabetically:",
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = RvInk,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.height(28.dp)
            )

            items.forEachIndexed { index, word ->
                AlphabetizeItem(
                    word = word,
                    index = index,
                    itemHeight = itemH,
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

                if (index < items.size - 1) Spacer(modifier = Modifier.height(gap))
            }
        }
    }
}

@Composable
private fun AlphabetizeItem(
    word: String,
    index: Int,
    itemHeight: androidx.compose.ui.unit.Dp,
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
            .height(itemHeight)
            .testTag("ctx_word")
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
                        // Calculate drop position based on drag offset (row height + 8dp gap)
                        val itemHeightPx = (itemHeight + 8.dp).toPx()
                        val offsetItems = (dragState.y / itemHeightPx).roundToInt()
                        val newIndex = (index + offsetItems).coerceAtLeast(0)
                        onDrop(newIndex)
                        dragState = Offset.Zero
                    }
                )
            },
        colors = CardDefaults.cardColors(
            containerColor = if (isDragged) RvViolet.copy(alpha = 0.2f) else RvSurface
        ),
        border = androidx.compose.foundation.BorderStroke(1.dp, if (isDragged) RvViolet else RvOutline),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.DragHandle,
                contentDescription = "Drag",
                tint = RvInkSoft,
                modifier = Modifier.size(24.dp)
            )

            Spacer(modifier = Modifier.width(8.dp))

            Text(
                text = "${index + 1}.",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = RvInkSoft,
                modifier = Modifier.widthIn(min = 28.dp)
            )

            Text(
                text = word,
                fontSize = aCapSp(16f, 1.3f),
                fontWeight = FontWeight.Medium,
                color = RvInk,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )

            // Up/Down buttons for alternative interaction (48dp tap targets side by side)
            IconButton(onClick = onMoveUp, modifier = Modifier.size(48.dp)) {
                Icon(
                    imageVector = Icons.Default.KeyboardArrowUp,
                    contentDescription = "Move Up",
                    tint = RvInk,
                    modifier = Modifier.size(28.dp)
                )
            }

            IconButton(onClick = onMoveDown, modifier = Modifier.size(48.dp)) {
                Icon(
                    imageVector = Icons.Default.KeyboardArrowDown,
                    contentDescription = "Move Down",
                    tint = RvInk,
                    modifier = Modifier.size(28.dp)
                )
            }
        }
    }
}

@Composable
private fun RecognitionPhase(
    items: List<String>,
    selectedAnswers: Set<String>,
    wide: Boolean,
    compact: Boolean,
    onSelectionChanged: (Set<String>) -> Unit,
    onSubmit: () -> Unit
) {
    CsPhaseScaffold(
        wide = wide,
        compact = compact,
        title = "Recognition Test",
        subtitle = "Select all items from the original list",
        action = {
            CsPrimaryAction(
                text = stringResource(R.string.submit_answer),
                enabled = selectedAnswers.isNotEmpty(),
                onClick = onSubmit
            )
        }
    ) { m ->
        CsSelectionGrid(
            items = items,
            selectedAnswers = selectedAnswers,
            onSelectionChanged = onSelectionChanged,
            modifier = m
        )
    }
}

/** Multi-select grid of items, cell height derived from the space given (no scrolling). */
@Composable
internal fun CsSelectionGrid(
    items: List<String>,
    selectedAnswers: Set<String>,
    onSelectionChanged: (Set<String>) -> Unit,
    modifier: Modifier = Modifier
) {
        BoxWithConstraints(modifier = modifier, contentAlignment = Alignment.Center) {
            val gap = 8.dp
            // Fewest columns (2..4) whose rows still fit at >= 48dp each.
            val cols = (2..4).firstOrNull { c ->
                val rows = (items.size + c - 1) / c
                (maxHeight - gap * (rows - 1)) / rows >= 48.dp
            } ?: 4
            val rows = ((items.size + cols - 1) / cols).coerceAtLeast(1)
            val cellH = ((maxHeight - gap * (rows - 1)) / rows).coerceIn(48.dp, 72.dp)
            Column(verticalArrangement = Arrangement.spacedBy(gap)) {
                items.chunked(cols).forEach { rowItems ->
                    Row(horizontalArrangement = Arrangement.spacedBy(gap), modifier = Modifier.fillMaxWidth()) {
                        rowItems.forEach { item ->
                            val isSelected = item in selectedAnswers
                            Card(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(cellH)
                                    .testTag("ctx_item")
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
                                        color = if (isSelected) RvViolet else RvInkSoft,
                                        shape = RoundedCornerShape(12.dp)
                                    ),
                                colors = CardDefaults.cardColors(
                                    containerColor = if (isSelected) RvViolet.copy(alpha = 0.12f) else RvSurface
                                )
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    if (isSelected) {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = null,
                                            tint = RvViolet,
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Spacer(Modifier.width(4.dp))
                                    }
                                    Text(
                                        text = item,
                                        fontSize = aCapSp(16f, 1.15f),
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        color = RvInk,
                                        textAlign = TextAlign.Center,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                        // keep last-row cells the same width
                        repeat(cols - rowItems.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
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
        modifier = Modifier.fillMaxWidth().statusBarsPadding(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            TextButton(
                onClick = onBack,
                modifier = Modifier.heightIn(min = 48.dp)
            ) {
                Text(stringResource(R.string.back), color = RvInk, fontSize = 16.sp, maxLines = 1)
            }
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = level,
                color = RvInkSoft,
                fontSize = 14.sp,
                maxLines = 1
            )
            Text(
                text = timer,
                color = RvInk,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                modifier = Modifier.testTag("hud_timer")
            )
        }

        Row(modifier = Modifier.weight(1f), horizontalArrangement = Arrangement.End) {
            repeat(hearts) {
                Text("❤️", fontSize = 16.sp)
            }
        }
    }
}
