package com.kreativekoala.riddleverse

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import org.json.JSONObject
import kotlin.random.Random

data class MathCrosswordCell(
    val row: Int,
    val col: Int,
    val value: String = "",
    val isFixed: Boolean = false,
    val isHighlighted: Boolean = false,
    val isCorrect: Boolean = false,
    val isError: Boolean = false,
    val cellType: CellType = CellType.EMPTY
)

enum class CellType {
    EMPTY,
    NUMBER,
    OPERATOR,
    EQUALS,
    BLOCKED
}

data class MathCrosswordPuzzle(
    val grid: List<List<MathCrosswordCell>>,
    val size: Int,
    val equations: List<Equation>,
    val availableNumbers: List<String>,
    val availableOperators: List<String>
)

data class Equation(
    val cells: List<Pair<Int, Int>>, // (row, col) pairs
    val isHorizontal: Boolean,
    val result: Int,
    val operands: List<Int>,
    val operator: String
)

@Composable
fun MathCrosswordPuzzleScreen(
    difficulty: String,
    timer: String,
    hearts: Int = 3,
    level: String,
    puzzleData: String,
    onSubmitAnswer: (Boolean) -> Unit,
    fetchNextPuzzle: (Int) -> Unit,
    onBack: () -> Unit
) {
    var currentGrid by remember { mutableStateOf<List<List<MathCrosswordCell>>>(emptyList()) }
    var selectedCell by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    var availableNumbers by remember { mutableStateOf<List<String>>(emptyList()) }
    var availableOperators by remember { mutableStateOf<List<String>>(emptyList()) }
    var usedNumbers by remember { mutableStateOf<Map<String, Int>>(emptyMap()) }
    var usedOperators by remember { mutableStateOf<Map<String, Int>>(emptyMap()) }
    var score by remember { mutableStateOf(0) }
    var showFeedback by remember { mutableStateOf(false) }
    var feedbackMessage by remember { mutableStateOf("") }
    var isComplete by remember { mutableStateOf(false) }
    var timeLeft by remember { mutableStateOf(parseTimer(timer)) }
    var isTimerRunning by remember { mutableStateOf(true) }
    var selectedCellType by remember { mutableStateOf<CellType?>(null) }
    var answerData by remember { mutableStateOf<JSONObject?>(null) }

    // Parse puzzle data
    LaunchedEffect(puzzleData) {
        val puzzleResult = parseMathCrosswordData(puzzleData, difficulty)
        currentGrid = puzzleResult.puzzle.grid
        availableNumbers = puzzleResult.puzzle.availableNumbers
        availableOperators = puzzleResult.puzzle.availableOperators
        answerData = puzzleResult.answerData

        // Initialize used numbers count
        val numberCounts = mutableMapOf<String, Int>()
        puzzleResult.puzzle.availableNumbers.forEach { number ->
            numberCounts[number] = numberCounts.getOrDefault(number, 0) + 1
        }
        usedNumbers = numberCounts

        // Initialize used operators count
        val operatorCounts = mutableMapOf<String, Int>()
        puzzleResult.puzzle.availableOperators.forEach { operator ->
            operatorCounts[operator] = operatorCounts.getOrDefault(operator, 0) + 1
        }
        usedOperators = operatorCounts
    }

    // Timer logic
    LaunchedEffect(isTimerRunning) {
        while (isTimerRunning && timeLeft > 0) {
            delay(1000)
            timeLeft--
        }
        if (timeLeft == 0) {
            isTimerRunning = false
            showFeedback = true
            feedbackMessage = "Time's up! ⏰"
            onSubmitAnswer(false)
        }
    }

    // Check completion
    LaunchedEffect(currentGrid) {
        if (currentGrid.isNotEmpty() && checkAllCellsFilled(currentGrid)) {
            // Only proceed if all cells are filled
            val isValid = validateAllEquations(currentGrid)

            if (isValid && !isComplete) {
                // All equations are correct
                isComplete = true
                isTimerRunning = false
                val timeBonus = timeLeft * 10
                score = 1000 + timeBonus
                showFeedback = true
                feedbackMessage = "Perfect! All equations are correct! 🎉"
                onSubmitAnswer(true)
            } else if (!isValid && checkAllCellsFilled(currentGrid)) {
                // All cells filled but equations are wrong
                showFeedback = true
                feedbackMessage = "All cells filled but some equations are incorrect. Please check your work! ❌"
                // Don't mark as complete, let user fix the errors
            }
        }
    }

    // Update selected cell type when selection changes
    LaunchedEffect(selectedCell) {
        selectedCellType = selectedCell?.let { (row, col) ->
            if (row < currentGrid.size && col < currentGrid[row].size) {
                currentGrid[row][col].cellType
            } else null
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF8F9FF))
            .padding(16.dp)
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Text("←", fontSize = 24.sp, fontWeight = FontWeight.Bold)
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = difficulty,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color(0xFF666666)
                )
                Text(
                    text = mcformatTime(timeLeft),
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (timeLeft <= 30) Color.Red else Color(0xFF333333)
                )
            }

            Row {
                repeat(hearts) {
                    Text("❤️", fontSize = 16.sp)
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Level indicator
        Text(
            text = level,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            color = Color(0xFF666666),
            modifier = Modifier.align(Alignment.CenterHorizontally)
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Instructions
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFE3F2FD))
        ) {
            Text(
                text = "Complete the math equations by placing missing numbers and operators",
                fontSize = 14.sp,
                color = Color(0xFF1976D2),
                modifier = Modifier.padding(12.dp),
                textAlign = TextAlign.Center
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Math Crossword Grid
        if (currentGrid.isNotEmpty()) {
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                CrosswordGridLayout(
                    grid = currentGrid,
                    selectedCell = selectedCell,
                    onCellClick = { row, col ->
                        val cell = currentGrid[row][col]
                        if (!cell.isFixed && cell.cellType != CellType.BLOCKED) {
                            selectedCell = Pair(row, col)
                        }
                    }
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Selection indicator
        selectedCell?.let { (row, col) ->
            val cell = currentGrid[row][col]
            if (!cell.isFixed && cell.cellType != CellType.BLOCKED) {
                Text(
                    text = when (cell.cellType) {
                        CellType.NUMBER -> "Select a number:"
                        CellType.OPERATOR -> "Select an operator:"
                        else -> "Select a value:"
                    },
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color(0xFF333333),
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }
        }

        // Available Numbers Grid (only show when number cell is selected)
        if (selectedCellType == CellType.NUMBER && usedNumbers.isNotEmpty()) {
            NumberSelectionGrid(
                usedNumbers = usedNumbers,
                onNumberSelected = { number ->
                    selectedCell?.let { (row, col) ->
                        val cell = currentGrid[row][col]
                        if (!cell.isFixed && cell.cellType == CellType.NUMBER && usedNumbers[number]!! > 0) {
                            // Remove old value if exists
                            val oldValue = cell.value
                            if (oldValue.isNotEmpty()) {
                                usedNumbers = usedNumbers.toMutableMap().apply {
                                    this[oldValue] = this.getOrDefault(oldValue, 0) + 1
                                }
                            }

                            // Place new value
                            currentGrid = currentGrid.mapIndexed { r, rowCells ->
                                if (r == row) {
                                    rowCells.mapIndexed { c, cellItem ->
                                        if (c == col) {
                                            cellItem.copy(value = number)
                                        } else cellItem
                                    }
                                } else rowCells
                            }

                            // Update available numbers
                            usedNumbers = usedNumbers.toMutableMap().apply {
                                this[number] = this[number]!! - 1
                            }

                            // Clear selection after placing
                            selectedCell = null
                        }
                    }
                }
            )
        }

        // Available Operators Grid (only show when operator cell is selected)
        if (selectedCellType == CellType.OPERATOR && usedOperators.isNotEmpty()) {
            OperatorSelectionGrid(
                usedOperators = usedOperators,
                onOperatorSelected = { operator ->
                    selectedCell?.let { (row, col) ->
                        val cell = currentGrid[row][col]
                        if (!cell.isFixed && cell.cellType == CellType.OPERATOR && usedOperators[operator]!! > 0) {
                            // Remove old value if exists
                            val oldValue = cell.value
                            if (oldValue.isNotEmpty()) {
                                usedOperators = usedOperators.toMutableMap().apply {
                                    this[oldValue] = this.getOrDefault(oldValue, 0) + 1
                                }
                            }

                            // Place new value
                            currentGrid = currentGrid.mapIndexed { r, rowCells ->
                                if (r == row) {
                                    rowCells.mapIndexed { c, cellItem ->
                                        if (c == col) {
                                            cellItem.copy(value = operator)
                                        } else cellItem
                                    }
                                } else rowCells
                            }

                            // Update available operators
                            usedOperators = usedOperators.toMutableMap().apply {
                                this[operator] = this[operator]!! - 1
                            }

                            // Clear selection after placing
                            selectedCell = null
                        }
                    }
                }
            )
        }

        Spacer(modifier = Modifier.weight(1f))

        // Control Buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = {
                    selectedCell?.let { (row, col) ->
                        if (row < currentGrid.size && col < currentGrid[row].size) {
                            val cell = currentGrid[row][col]
                            if (!cell.isFixed && cell.value.isNotEmpty()) {
                                val oldValue = cell.value

                                // Return value to appropriate pool
                                when (cell.cellType) {
                                    CellType.NUMBER -> {
                                        usedNumbers = usedNumbers.toMutableMap().apply {
                                            this[oldValue] = this.getOrDefault(oldValue, 0) + 1
                                        }
                                    }
                                    CellType.OPERATOR -> {
                                        usedOperators = usedOperators.toMutableMap().apply {
                                            this[oldValue] = this.getOrDefault(oldValue, 0) + 1
                                        }
                                    }
                                    else -> {}
                                }

                                // Clear cell
                                currentGrid = currentGrid.mapIndexed { r, rowCells ->
                                    if (r == row) {
                                        rowCells.mapIndexed { c, cellItem ->
                                            if (c == col) {
                                                cellItem.copy(value = "")
                                            } else cellItem
                                        }
                                    } else rowCells
                                }
                            }
                        }
                    }
                },
                modifier = Modifier.weight(1f),
                enabled = selectedCell != null &&
                        selectedCell!!.let { (row, col) ->
                            row < currentGrid.size && col < currentGrid[row].size &&
                                    !currentGrid[row][col].isFixed &&
                                    currentGrid[row][col].value.isNotEmpty()
                        }
            ) {
                Text(stringResource(R.string.clear))
            }

            OutlinedButton(
                onClick = {
                    // Use stored answer data to reveal correct answers
                    answerData?.let { answer ->
                        try {
                            val correctGrid = answer.getJSONArray("correctGrid")

                            // Fill in all missing answers
                            currentGrid = currentGrid.mapIndexed { row, rowCells ->
                                rowCells.mapIndexed { col, cell ->
                                    if (!cell.isFixed && cell.cellType != CellType.BLOCKED) {
                                        if (row < correctGrid.length()) {
                                            val correctRow = correctGrid.getJSONArray(row)
                                            if (col < correctRow.length()) {
                                                val correctValue = correctRow.getString(col)
                                                if (correctValue.isNotEmpty()) {
                                                    // Return current value to pool if any
                                                    if (cell.value.isNotEmpty()) {
                                                        when (cell.cellType) {
                                                            CellType.NUMBER -> {
                                                                usedNumbers = usedNumbers.toMutableMap().apply {
                                                                    this[cell.value] = this.getOrDefault(cell.value, 0) + 1
                                                                }
                                                            }
                                                            CellType.OPERATOR -> {
                                                                usedOperators = usedOperators.toMutableMap().apply {
                                                                    this[cell.value] = this.getOrDefault(cell.value, 0) + 1
                                                                }
                                                            }
                                                            else -> {}
                                                        }
                                                    }

                                                    // Set correct value and remove from pool
                                                    when (cell.cellType) {
                                                        CellType.NUMBER -> {
                                                            usedNumbers = usedNumbers.toMutableMap().apply {
                                                                this[correctValue] = kotlin.math.max(0, this.getOrDefault(correctValue, 0) - 1)
                                                            }
                                                        }
                                                        CellType.OPERATOR -> {
                                                            usedOperators = usedOperators.toMutableMap().apply {
                                                                this[correctValue] = kotlin.math.max(0, this.getOrDefault(correctValue, 0) - 1)
                                                            }
                                                        }
                                                        else -> {}
                                                    }

                                                    return@mapIndexed cell.copy(
                                                        value = correctValue,
                                                        isCorrect = true
                                                    )
                                                }
                                            }
                                        }
                                    }
                                    cell
                                }
                            }

                            // Clear selection and stop timer
                            selectedCell = null
                            isTimerRunning = false

                            // Show completion message
                            showFeedback = true
                            feedbackMessage = "Puzzle completed! All answers revealed. 📖"
                            score = kotlin.math.max(100, timeLeft * 5) // Reduced score for giving up

                        } catch (e: Exception) {
                            println("Error revealing answers: ${e.message}")
                            showFeedback = true
                            feedbackMessage = "Unable to reveal answers. Please try again."
                        }
                    }
                },
                modifier = Modifier.weight(1f),
                enabled = !isComplete && answerData != null
            ) {
                Text(stringResource(R.string.give_up))
            }

            Button(
                onClick = {
                    if (checkAllCellsFilled(currentGrid)) {
                        val isValid = validateAllEquations(currentGrid)
                        if (isValid) {
                            isComplete = true
                            isTimerRunning = false
                            val timeBonus = timeLeft * 10
                            score = 1000 + timeBonus
                            showFeedback = true
                            feedbackMessage = "Perfect! All equations are correct! 🎉"
                            onSubmitAnswer(true)
                        } else {
                            // Show which equations are wrong
                            val wrongEquations = findIncorrectEquations(currentGrid)
                            showFeedback = true
                            feedbackMessage = if (wrongEquations.isNotEmpty()) {
                                "Found ${wrongEquations.size} incorrect equation(s). Please check your work! 🤔"
                            } else {
                                "Some equations are incorrect. Keep trying! 🤔"
                            }

                            // Optionally highlight wrong equations
                            currentGrid = highlightIncorrectEquations(currentGrid, wrongEquations)
                        }
                    } else {
                        showFeedback = true
                        feedbackMessage = "Please fill in all empty cells first! 📝"
                    }
                },
                modifier = Modifier.weight(1f),
                enabled = !isComplete && checkAllCellsFilled(currentGrid)
            ) {
                Text(stringResource(R.string.check))
            }
        }
    }

    // Feedback Dialog
    if (showFeedback) {
        AlertDialog(
            onDismissRequest = { },
            confirmButton = {
                Button(
                    onClick = {
                        showFeedback = false
                        if (isComplete) {
                            fetchNextPuzzle(score)
                        }
                    }
                ) {
                    Text(if (isComplete) stringResource(R.string.continue_label) else stringResource(R.string.ok))
                }
            },
            title = {
                Text(
                    text = if (isComplete) "🎉 Completed!" else "💡 Hint",
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column {
                    Text(feedbackMessage)
                    if (isComplete) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "${stringResource(R.string.score_label)}: $score",
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF4CAF50)
                        )
                    }
                }
            }
        )
    }
}

fun findIncorrectEquations(grid: List<List<MathCrosswordCell>>): List<List<Pair<Int, Int>>> {
    val incorrectEquations = mutableListOf<List<Pair<Int, Int>>>()

    // Check horizontal equations
    for (row in grid.indices) {
        var currentSequence = mutableListOf<Pair<Int, Int>>()
        var currentCells = mutableListOf<MathCrosswordCell>()

        for (col in grid[row].indices) {
            val cell = grid[row][col]
            if (cell.cellType != CellType.BLOCKED) {
                currentSequence.add(Pair(row, col))
                currentCells.add(cell)
            } else {
                if (currentSequence.size == 5 && !validateEquationCells(currentCells)) {
                    incorrectEquations.add(currentSequence.toList())
                }
                currentSequence.clear()
                currentCells.clear()
            }
        }
        if (currentSequence.size == 5 && !validateEquationCells(currentCells)) {
            incorrectEquations.add(currentSequence.toList())
        }
    }

    // Check vertical equations
    for (col in grid[0].indices) {
        var currentSequence = mutableListOf<Pair<Int, Int>>()
        var currentCells = mutableListOf<MathCrosswordCell>()

        for (row in grid.indices) {
            val cell = grid[row][col]
            if (cell.cellType != CellType.BLOCKED) {
                currentSequence.add(Pair(row, col))
                currentCells.add(cell)
            } else {
                if (currentSequence.size == 5 && !validateEquationCells(currentCells)) {
                    incorrectEquations.add(currentSequence.toList())
                }
                currentSequence.clear()
                currentCells.clear()
            }
        }
        if (currentSequence.size == 5 && !validateEquationCells(currentCells)) {
            incorrectEquations.add(currentSequence.toList())
        }
    }

    return incorrectEquations
}

fun highlightIncorrectEquations(
    grid: List<List<MathCrosswordCell>>,
    incorrectEquations: List<List<Pair<Int, Int>>>
): List<List<MathCrosswordCell>> {
    val incorrectPositions = incorrectEquations.flatten().toSet()

    return grid.mapIndexed { row, rowCells ->
        rowCells.mapIndexed { col, cell ->
            if (Pair(row, col) in incorrectPositions) {
                cell.copy(isError = true)
            } else {
                cell.copy(isError = false)
            }
        }
    }
}


@Composable
fun MathCrosswordCellView(
    cell: MathCrosswordCell,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val backgroundColor = when {
        cell.cellType == CellType.BLOCKED -> Color.Transparent
        cell.isError -> Color(0xFFFFEBEE)
        cell.isCorrect -> Color(0xFFE8F5E8)
        isSelected -> Color(0xFFE3F2FD)
        cell.isFixed -> when (cell.cellType) {
            CellType.OPERATOR -> Color(0xFFFFF3E0)
            CellType.EQUALS -> Color(0xFFF3E5F5)
            else -> Color(0xFFF5F5F5)
        }
        else -> Color.White
    }

    val borderColor = when {
        isSelected -> Color(0xFF2196F3)
        cell.isError -> Color(0xFFE53935)
        cell.isCorrect -> Color(0xFF4CAF50)
        cell.cellType == CellType.OPERATOR -> Color(0xFFFF9800)
        cell.cellType == CellType.EQUALS -> Color(0xFF9C27B0)
        else -> Color(0xFFE0E0E0)
    }

    val textColor = when {
        cell.isError -> Color(0xFFE53935)
        cell.cellType == CellType.OPERATOR -> Color(0xFFE65100)
        cell.cellType == CellType.EQUALS -> Color(0xFF7B1FA2)
        cell.isFixed -> Color(0xFF2E7D32)
        else -> Color(0xFF333333)
    }

    Box(
        modifier = Modifier
            .size(52.dp)
            .padding(1.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(backgroundColor)
            .border(
                width = if (isSelected) 3.dp else 1.dp,
                color = borderColor,
                shape = RoundedCornerShape(6.dp)
            )
            .clickable(enabled = cell.cellType != CellType.BLOCKED) { onClick() },
        contentAlignment = Alignment.Center
    ) {
        if (cell.cellType != CellType.BLOCKED) {
            Text(
                text = cell.value,
                fontSize = when (cell.cellType) {
                    CellType.OPERATOR, CellType.EQUALS -> 20.sp
                    else -> 18.sp
                },
                fontWeight = if (cell.isFixed) FontWeight.Bold else FontWeight.Normal,
                color = textColor,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
fun CrosswordGridLayout(
    grid: List<List<MathCrosswordCell>>,
    selectedCell: Pair<Int, Int>?,
    onCellClick: (Int, Int) -> Unit
) {
    if (grid.isEmpty()) return

    // Find the actual bounds of non-blocked cells
    val visibleCells = mutableMapOf<Pair<Int, Int>, MathCrosswordCell>()

    grid.forEachIndexed { row, rowCells ->
        rowCells.forEachIndexed { col, cell ->
            if (cell.cellType != CellType.BLOCKED) {
                visibleCells[Pair(row, col)] = cell
            }
        }
    }

    if (visibleCells.isEmpty()) return

    val minRow = visibleCells.keys.minOf { it.first }
    val maxRow = visibleCells.keys.maxOf { it.first }
    val minCol = visibleCells.keys.minOf { it.second }
    val maxCol = visibleCells.keys.maxOf { it.second }

    // Add horizontal scrolling if the grid is too wide
    val scrollState = rememberScrollState()

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(scrollState)
    ) {
        for (row in minRow..maxRow) {
            Row(
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                for (col in minCol..maxCol) {
                    val cell = visibleCells[Pair(row, col)]
                    if (cell != null) {
                        MathCrosswordCellView(
                            cell = cell,
                            isSelected = selectedCell == Pair(row, col),
                            onClick = { onCellClick(row, col) }
                        )
                    } else {
                        // Empty space for layout - make sure it's the same size as a cell
                        Spacer(
                            modifier = Modifier
                                .size(52.dp)
                                .padding(1.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun NumberSelectionGrid(
    usedNumbers: Map<String, Int>,
    onNumberSelected: (String) -> Unit
) {
    val sortedNumbers = usedNumbers.keys.sortedBy { it.toIntOrNull() ?: 0 }
    val chunkedNumbers = sortedNumbers.chunked(8) // 8 numbers per row for better density

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        chunkedNumbers.forEach { row ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                row.forEach { number ->
                    val count = usedNumbers[number] ?: 0
                    NumberSelectionButton(
                        number = number,
                        count = count,
                        isAvailable = count > 0,
                        onClick = { if (count > 0) onNumberSelected(number) }
                    )
                }
            }
        }
    }
}

@Composable
fun OperatorSelectionGrid(
    usedOperators: Map<String, Int>,
    onOperatorSelected: (String) -> Unit
) {
    val operatorOrder = listOf("+", "-", "*", "/")
    val sortedOperators = usedOperators.keys.sortedBy { operatorOrder.indexOf(it) }

    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
    ) {
        sortedOperators.forEach { operator ->
            val count = usedOperators[operator] ?: 0
            OperatorSelectionButton(
                operator = operator,
                count = count,
                isAvailable = count > 0,
                onClick = { if (count > 0) onOperatorSelected(operator) },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
fun NumberSelectionButton(
    number: String,
    count: Int,
    isAvailable: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(
                if (isAvailable) Color(0xFF4CAF50) else Color(0xFFE0E0E0)
            )
            .border(
                width = 2.dp,
                color = if (isAvailable) Color(0xFF2E7D32) else Color(0xFFBDBDBD),
                shape = RoundedCornerShape(8.dp)
            )
            .clickable(enabled = isAvailable) { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = number,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = if (isAvailable) Color.White else Color(0xFF757575)
            )
            if (count > 1) {
                Text(
                    text = "×$count",
                    fontSize = 9.sp,
                    color = if (isAvailable) Color.White else Color(0xFF757575)
                )
            }
        }
    }
}

@Composable
fun OperatorSelectionButton(
    operator: String,
    count: Int,
    isAvailable: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .height(50.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(
                if (isAvailable) Color(0xFFFF9800) else Color(0xFFE0E0E0)
            )
            .border(
                width = 2.dp,
                color = if (isAvailable) Color(0xFFE65100) else Color(0xFFBDBDBD),
                shape = RoundedCornerShape(10.dp)
            )
            .clickable(enabled = isAvailable) { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = operator,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = if (isAvailable) Color.White else Color(0xFF757575)
            )
            if (count > 1) {
                Text(
                    text = "×$count",
                    fontSize = 10.sp,
                    color = if (isAvailable) Color.White else Color(0xFF757575)
                )
            }
        }
    }
}

fun setEquationCells(
    grid: Array<Array<MathCrosswordCell>>,
    startRow: Int,
    startCol: Int,
    isHorizontal: Boolean,
    values: List<String>,
    isFixed: List<Boolean>
) {
    values.forEachIndexed { index, value ->
        val row = if (isHorizontal) startRow else startRow + index
        val col = if (isHorizontal) startCol + index else startCol

        if (row < grid.size && col < grid[0].size) {
            val cellType = when {
                value == "+" || value == "-" || value == "*" || value == "/" -> CellType.OPERATOR
                value == "=" -> CellType.EQUALS
                value.toIntOrNull() != null -> CellType.NUMBER
                else -> CellType.EMPTY
            }

            grid[row][col] = MathCrosswordCell(
                row = row,
                col = col,
                value = value,
                isFixed = if (index < isFixed.size) isFixed[index] else false,
                cellType = cellType
            )
        }
    }
}

fun checkAllCellsFilled(grid: List<List<MathCrosswordCell>>): Boolean {
    for (row in grid) {
        for (cell in row) {
            if (cell.cellType != CellType.BLOCKED && !cell.isFixed && cell.value.isEmpty()) {
                return false
            }
        }
    }
    return true
}

fun checkPuzzleCompletion(grid: List<List<MathCrosswordCell>>): Boolean {
    if (!checkAllCellsFilled(grid)) {
        return false
    }
    return validateAllEquations(grid)
}

fun validateAllEquations(grid: List<List<MathCrosswordCell>>): Boolean {
    val equations = mutableListOf<List<MathCrosswordCell>>()

    // Find horizontal equations
    for (row in grid.indices) {
        var currentSequence = mutableListOf<MathCrosswordCell>()
        for (col in grid[row].indices) {
            val cell = grid[row][col]
            if (cell.cellType != CellType.BLOCKED) {
                currentSequence.add(cell)
            } else {
                if (currentSequence.size == 5) {
                    equations.add(currentSequence.toList())
                }
                currentSequence.clear()
            }
        }
        if (currentSequence.size == 5) {
            equations.add(currentSequence.toList())
        }
    }

    // Find vertical equations
    for (col in grid[0].indices) {
        var currentSequence = mutableListOf<MathCrosswordCell>()
        for (row in grid.indices) {
            val cell = grid[row][col]
            if (cell.cellType != CellType.BLOCKED) {
                currentSequence.add(cell)
            } else {
                if (currentSequence.size == 5) {
                    equations.add(currentSequence.toList())
                }
                currentSequence.clear()
            }
        }
        if (currentSequence.size == 5) {
            equations.add(currentSequence.toList())
        }
    }

    return equations.all { validateEquationCells(it) }
}

fun validateEquationCells(cells: List<MathCrosswordCell>): Boolean {
    if (cells.size != 5) return false

    try {
        val operand1Str = cells[0].value.trim()
        val operator = cells[1].value.trim()
        val operand2Str = cells[2].value.trim()
        val equals = cells[3].value.trim()
        val resultStr = cells[4].value.trim()

        if (operand1Str.isEmpty() || operator.isEmpty() || operand2Str.isEmpty() ||
            equals.isEmpty() || resultStr.isEmpty()) {
            return false
        }

        if (equals != "=") return false
        if (operator !in listOf("+", "-", "*", "/")) return false

        val operand1 = operand1Str.toIntOrNull() ?: return false
        val operand2 = operand2Str.toIntOrNull() ?: return false
        val result = resultStr.toIntOrNull() ?: return false

        val calculatedResult = when (operator) {
            "+" -> operand1 + operand2
            "-" -> operand1 - operand2
            "*" -> operand1 * operand2
            "/" -> if (operand2 != 0 && operand1 % operand2 == 0) operand1 / operand2 else return false
            else -> return false
        }

        return calculatedResult == result
    } catch (e: Exception) {
        return false
    }
}

data class MathCrosswordPuzzleResult(
    val puzzle: MathCrosswordPuzzle,
    val answerData: JSONObject?
)

fun parseMathCrosswordData(puzzleData: String, difficulty: String): MathCrosswordPuzzleResult {
    return try {
        val json = JSONObject(puzzleData)

        if (json.has("gridSize") && json.has("grid") && json.has("numberCounts")) {
            val gridSize = json.getInt("gridSize")
            val gridJson = json.getJSONArray("grid")
            val numberCountsJson = json.getJSONObject("numberCounts")

            // Parse operator counts if available
            val operatorCountsJson = if (json.has("operatorCounts")) {
                json.getJSONObject("operatorCounts")
            } else {
                JSONObject()
            }

            val grid = mutableListOf<List<MathCrosswordCell>>()
            for (row in 0 until gridSize) {
                val rowJson = gridJson.getJSONArray(row)
                val rowCells = mutableListOf<MathCrosswordCell>()
                for (col in 0 until gridSize) {
                    val cellJson = rowJson.getJSONObject(col)
                    val cellType = CellType.valueOf(cellJson.getString("cellType"))

                    rowCells.add(MathCrosswordCell(
                        row = row,
                        col = col,
                        value = cellJson.getString("value"),
                        isFixed = cellJson.getBoolean("isFixed"),
                        cellType = cellType
                    ))
                }
                grid.add(rowCells)
            }

            val availableNumbers = mutableListOf<String>()
            val numberCountsIterator = numberCountsJson.keys()
            while (numberCountsIterator.hasNext()) {
                val number = numberCountsIterator.next()
                val count = numberCountsJson.getInt(number)
                repeat(count) {
                    availableNumbers.add(number)
                }
            }

            val availableOperators = mutableListOf<String>()
            val operatorCountsIterator = operatorCountsJson.keys()
            while (operatorCountsIterator.hasNext()) {
                val operator = operatorCountsIterator.next()
                val count = operatorCountsJson.getInt(operator)
                repeat(count) {
                    availableOperators.add(operator)
                }
            }

            val equations = mutableListOf<Equation>()
            if (json.has("equations")) {
                val equationsJson = json.getJSONArray("equations")
                for (i in 0 until equationsJson.length()) {
                    val eqJson = equationsJson.getJSONObject(i)

                    val startRow = eqJson.getInt("startRow")
                    val startCol = eqJson.getInt("startCol")
                    val isHorizontal = eqJson.getBoolean("isHorizontal")

                    val cells = (0..4).map { index ->
                        if (isHorizontal) {
                            Pair(startRow, startCol + index)
                        } else {
                            Pair(startRow + index, startCol)
                        }
                    }

                    equations.add(Equation(
                        cells = cells,
                        isHorizontal = isHorizontal,
                        result = eqJson.getInt("result"),
                        operands = listOf(eqJson.getInt("operand1"), eqJson.getInt("operand2")),
                        operator = eqJson.getString("operator")
                    ))
                }
            }

            // Generate answer data for the give up functionality
            val (_, answerDataStr) = MathCrosswordGenerator.generatePuzzle(difficulty)
            val answerDataJson = try { JSONObject(answerDataStr) } catch (e: Exception) { null }

            return MathCrosswordPuzzleResult(
                puzzle = MathCrosswordPuzzle(
                    grid = grid,
                    size = gridSize,
                    equations = equations,
                    availableNumbers = availableNumbers,
                    availableOperators = availableOperators
                ),
                answerData = answerDataJson
            )
        } else {
            val (puzzleDataStr, answerDataStr) = MathCrosswordGenerator.generatePuzzle(difficulty)
            val answerDataJson = try { JSONObject(answerDataStr) } catch (e: Exception) { null }
            val recursiveResult = parseMathCrosswordData(puzzleDataStr, difficulty)
            return recursiveResult.copy(answerData = answerDataJson)
        }
    } catch (e: Exception) {
        println("Error parsing puzzle data: ${e.message}")
        val (puzzleDataStr, answerDataStr) = MathCrosswordGenerator.generatePuzzle(difficulty)
        val answerDataJson = try { JSONObject(answerDataStr) } catch (e: Exception) { null }
        val recursiveResult = parseMathCrosswordData(puzzleDataStr, difficulty)
        return recursiveResult.copy(answerData = answerDataJson)
    }
}

private fun validateSpecificEquation(grid: List<List<MathCrosswordCell>>, equation: Equation): Boolean {
    try {
        val cells = equation.cells.map { (row, col) ->
            if (row < grid.size && col < grid[row].size) {
                grid[row][col]
            } else {
                return false
            }
        }

        if (cells.size != 5) return false

        return validateEquationCells(cells)
    } catch (e: Exception) {
        return false
    }
}

fun parseTimer(timer: String): Int {
    val parts = timer.split(":")
    return if (parts.size == 2) {
        val minutes = parts[0].toIntOrNull() ?: 0
        val seconds = parts[1].toIntOrNull() ?: 0
        minutes * 60 + seconds
    } else {
        120 // Default 2 minutes
    }
}

fun mcformatTime(seconds: Int): String {
    val minutes = seconds / 60
    val remainingSeconds = seconds % 60
    return String.format("%02d:%02d", minutes, remainingSeconds)
}