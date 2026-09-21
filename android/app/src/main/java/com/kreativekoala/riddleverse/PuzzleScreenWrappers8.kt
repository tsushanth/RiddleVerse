package com.kreativekoala.riddleverse

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Help
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kreativekoala.riddleverse.ui.theme.RvInkSoft
import com.kreativekoala.riddleverse.ui.theme.RvOnTone
import com.kreativekoala.riddleverse.ui.theme.RvSky
import com.kreativekoala.riddleverse.ui.theme.RvSuccess
import com.kreativekoala.riddleverse.ui.theme.RvInk

@Composable
fun AveragesScreenWrapper(
    currentPuzzle: Puzzle,
    viewModel: PuzzleViewModel,
    onBack: () -> Unit,
    handlePuzzleCompletion: (Boolean, Boolean, Int) -> Unit
) {
    // Simply call the adaptive version with the correct parameters
    AdaptiveAveragePuzzleScreen(
        initialDifficulty = currentPuzzle.difficulty ?: "Medium",
        timer = when (currentPuzzle.difficulty?.lowercase()) {
            "easy" -> "2:00"
            "medium" -> "1:30"
            "hard" -> "1:15"
            "expert" -> "1:00"
            else -> "1:30"
        },
        onSubmitAnswer = { answer ->
            Log.d("AveragesWrapper", "📝 Average answer submitted: $answer")
        },
        fetchNextPuzzle = { finalScore ->
            Log.d("AveragesWrapper", "🔄 Average puzzle completed with score: $finalScore")
            handlePuzzleCompletion(true, true, finalScore)
        },
        onBack = onBack
    )
}

@Composable
fun DivisionScreenWrapper(
    currentPuzzle: Puzzle,
    viewModel: PuzzleViewModel,
    onBack: () -> Unit,
    handlePuzzleCompletion: (Boolean, Boolean, Int) -> Unit
) {
    val TAG = "DivisionWrapper"
    Log.d(TAG, "🎯 Using Adaptive Division puzzle system")

    // Use the adaptive division screen directly - it handles its own puzzle generation
    DivisionPuzzleScreen(
        initialDifficulty = currentPuzzle.difficulty ?: "Medium",
        timer = when (currentPuzzle.difficulty?.lowercase()) {
            "easy" -> "2:00"
            "medium" -> "1:30"
            "hard" -> "1:15"
            "expert" -> "1:00"
            else -> "1:30"
        },
        onSubmitAnswer = { answer ->
            Log.d(TAG, "📝 Division answer submitted: $answer")
            // The adaptive screen handles correctness checking internally
        },
        fetchNextPuzzle = { finalScore ->
            Log.d(TAG, "🎯 Division puzzle completed with total score: $finalScore")
            handlePuzzleCompletion(true, true, finalScore)
        },
        onBack = onBack
    )
}


@Composable
fun CryptoWordScreenWithTimer(
    puzzleData: CryptoPuzzleData,
    difficulty: String,
    timer: String,
    hearts: Int,
    level: String,
    onBack: () -> Unit,
    onComplete: (Boolean, Int) -> Unit
) {
    var userMapping by remember { mutableStateOf(mutableMapOf<Int, Char>()) }
    var selectedNumber by remember { mutableStateOf<Int?>(null) }
    var isComplete by remember { mutableStateOf(false) }
    var showHint by remember { mutableStateOf(false) }
    var showCompletionDialog by remember { mutableStateOf(false) }
    var finalScore by remember { mutableStateOf(0) }
    var currentHearts by remember { mutableStateOf(hearts) }
    var showWrongFeedback by remember { mutableStateOf(false) }
    var gameOver by remember { mutableStateOf(false) }

    // Initialize revealed letters in user mapping
    LaunchedEffect(puzzleData) {
        val initialMapping = mutableMapOf<Int, Char>()
        puzzleData.revealedLetters.forEach { letter ->
            val number = puzzleData.numberMapping[letter]
            if (number != null) {
                initialMapping[number] = letter
            }
        }
        userMapping = initialMapping
    }

    // Check if puzzle is complete
    LaunchedEffect(userMapping) {
        val cryptoLetters = puzzleData.numberMapping.keys
        val allCryptoLettersDecoded = cryptoLetters.all { letter ->
            val number = puzzleData.numberMapping[letter]
            number != null && userMapping[number] == letter
        }

        if (allCryptoLettersDecoded && !isComplete) {
            isComplete = true

            // Calculate score based on difficulty and completion
            val baseScore = 100
            val difficultyMultiplier = when (difficulty.lowercase()) {
                "easy" -> 1.0
                "medium" -> 1.2
                "hard" -> 1.5
                "expert" -> 2.0
                else -> 1.2
            }

            val complexityBonus = (puzzleData.numberMapping.size / 26.0) * 50
            finalScore = ((baseScore * difficultyMultiplier) + complexityBonus).toInt()

            // Show completion dialog after a brief delay
            kotlinx.coroutines.delay(1000)
            showCompletionDialog = true
        }
    }

    // Handle wrong feedback animation
    LaunchedEffect(showWrongFeedback) {
        if (showWrongFeedback) {
            kotlinx.coroutines.delay(1000) // Show X for 1 second
            showWrongFeedback = false
        }
    }

    // Handle game over
    LaunchedEffect(currentHearts) {
        if (currentHearts <= 0) {
            gameOver = true
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(top = 24.dp)) {
        // Header with game info
        CryptoPuzzleHeader(
            difficulty = difficulty,
            timer = timer,
            hearts = currentHearts, // Use current hearts
            level = level,
            onBack = onBack,
            onHint = { showHint = true }
        )

        // Wrong answer feedback
        if (showWrongFeedback) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFFFCDD2)) // Light red
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "❌ Wrong letter! Try again.",
                        style = MaterialTheme.typography.titleMedium,
                        color = Color(0xFFD32F2F), // Red text
                        textAlign = TextAlign.Center
                    )
                }
            }
        }

        // Success message (compact)
        if (isComplete) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
            ) {
                Text(
                    text = "🎉 Puzzle Solved! Score: $finalScore",
                    modifier = Modifier.padding(12.dp),
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center
                )
            }
        }

        // Main puzzle area - takes remaining space above keyboard
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(vertical = 16.dp),
            contentAlignment = Alignment.Center
        ) {
            CryptoPuzzleDisplay(
                originalText = puzzleData.originalText,
                numberMapping = puzzleData.numberMapping,
                userMapping = userMapping,
                selectedNumber = selectedNumber,
                onNumberSelected = { selectedNumber = if (selectedNumber == it) null else it },
                cryptoLetters = puzzleData.numberMapping.keys
            )
        }

        // Keyboard fixed at bottom
        LetterSelectionKeyboard(
            selectedNumber = selectedNumber,
            userMapping = userMapping,
            numberMapping = puzzleData.numberMapping,
            onLetterSelected = { letter ->
                selectedNumber?.let { number ->
                    val correctLetter = puzzleData.numberMapping.entries
                        .find { it.value == number }?.key

                    if (correctLetter == letter) {
                        // Correct answer - update mapping
                        val updatedMapping = userMapping.toMutableMap()
                        updatedMapping.entries.removeAll { it.value == letter }
                        updatedMapping[number] = letter
                        userMapping = updatedMapping
                        selectedNumber = null
                    } else {
                        // Wrong answer - show feedback and lose heart
                        showWrongFeedback = true
                        currentHearts = maxOf(0, currentHearts - 1)
                        selectedNumber = null
                    }
                }
            }
        )
    }

    // Hint dialog
    if (showHint && puzzleData.targetWord != null) {
        AlertDialog(
            onDismissRequest = { showHint = false },
            confirmButton = {
                Button(onClick = { showHint = false }) {
                    Text("OK")
                }
            },
            title = { Text("💡 Hint") },
            text = { Text("The message contains: ${puzzleData.targetWord}") }
        )
    }

    // Completion dialog
    if (showCompletionDialog) {
        QuoteRevealDialog(
            originalText = puzzleData.originalText,
            hintWithAuthor = puzzleData.targetWord, // This contains "Quote by \"Author\""
            finalScore = finalScore,
            onContinue = {
                showCompletionDialog = false
                onComplete(true, finalScore)
            }
        )
    }

    // Game Over dialog
    if (gameOver) {
        AlertDialog(
            onDismissRequest = { },
            confirmButton = {
                Button(
                    onClick = {
                        onComplete(false, 0) // Failed with 0 score
                    }
                ) {
                    Text("Try Again")
                }
            },
            dismissButton = {
                Button(onClick = onBack) {
                    Text("Back")
                }
            },
            title = { Text("💔 Game Over") },
            text = {
                Text("You've lost all your hearts! Better luck next time.")
            }
        )
    }
}

@Composable
private fun QuoteRevealDialog(
    originalText: String,
    hintWithAuthor: String?,
    finalScore: Int,
    onContinue: () -> Unit
) {
    // Parse author from hint format: "Quote by \"Author Name\""
    val author = if (hintWithAuthor?.contains("Quote by ") == true) {
        hintWithAuthor.substringAfter("Quote by ")
            .trim()
            .removeSurrounding("\"")
    } else {
        "Unknown"
    }

    AlertDialog(
        onDismissRequest = { },
        confirmButton = {
            Button(
                onClick = onContinue,
                modifier = Modifier.padding(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = RvSuccess
                )
            ) {
                Text(
                    text = "Continue",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = RvOnTone
                )
            }
        },
        title = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "🎉",
                    style = MaterialTheme.typography.displayLarge,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                Text(
                    text = "Puzzle Complete!",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "Score: $finalScore points",
                    style = MaterialTheme.typography.titleMedium,
                    color = RvSuccess,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Decorative top border
                Box(
                    modifier = Modifier
                        .width(60.dp)
                        .height(3.dp)
                        .background(
                            Color(0xFFE1BEE7),
                            RoundedCornerShape(2.dp)
                        )
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Quote text with beautiful formatting
                Text(
                    text = "\"${originalText.lowercase().split(" ").joinToString(" ") { word ->
                        word.replaceFirstChar { it.uppercase() }
                    }}\"",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center,
                    lineHeight = 32.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(horizontal = 8.dp)
                )

                Spacer(modifier = Modifier.height(20.dp))

                // Author attribution with elegant styling
                if (author != "Unknown") {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Box(
                            modifier = Modifier
                                .width(30.dp)
                                .height(1.dp)
                                .background(RvInkSoft)
                        )
                        Text(
                            text = "  $author  ",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Medium,
                            color = RvInkSoft,
                            fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                        )
                        Box(
                            modifier = Modifier
                                .width(30.dp)
                                .height(1.dp)
                                .background(RvInkSoft)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Decorative bottom border
                Box(
                    modifier = Modifier
                        .width(60.dp)
                        .height(3.dp)
                        .background(
                            Color(0xFFE1BEE7),
                            RoundedCornerShape(2.dp)
                        )
                )
            }
        },
        modifier = Modifier.fillMaxWidth(0.95f),
        shape = RoundedCornerShape(20.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 12.dp
    )
}

@Composable
private fun CryptoPuzzleHeader(
    difficulty: String,
    timer: String,
    hearts: Int,
    level: String,
    onBack: () -> Unit,
    onHint: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 16.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Back button
        IconButton(onClick = onBack) {
            Icon(Icons.Default.ArrowBack, contentDescription = "Back")
        }

        // Game info row
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Difficulty
            Text(
                text = difficulty,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )

            // Timer
            Text(
                text = timer,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )

            // Hearts
            Row {
                repeat(hearts) {
                    Text(
                        text = "❤️",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            // Level
            Text(
                text = level,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
        }

        // Hint button
        IconButton(onClick = onHint) {
            Icon(Icons.Default.Help, contentDescription = "Hint")
        }
    }
}

@Composable
fun CryptoPuzzleDisplay(
    originalText: String,
    numberMapping: Map<Char, Int>,
    userMapping: Map<Int, Char>,
    selectedNumber: Int?,
    onNumberSelected: (Int) -> Unit,
    cryptoLetters: Set<Char> = numberMapping.keys // Default to numberMapping keys
) {
    val words = originalText.split(" ")

    // Group words into rows - aim for 2-3 words per row based on length
    val wordRows = mutableListOf<List<String>>()
    var currentRow = mutableListOf<String>()
    var currentRowLength = 0

    words.forEach { word ->
        // Estimate if adding this word would make the row too long
        val estimatedLength = currentRowLength + word.length + if (currentRow.isNotEmpty()) 1 else 0

        if (currentRow.isEmpty() || (estimatedLength <= 12 && currentRow.size < 3)) {
            currentRow.add(word)
            currentRowLength = estimatedLength
        } else {
            // Start new row
            wordRows.add(currentRow.toList())
            currentRow = mutableListOf(word)
            currentRowLength = word.length
        }
    }

    // Add the last row if it's not empty
    if (currentRow.isNotEmpty()) {
        wordRows.add(currentRow)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp) // Compact spacing between rows
    ) {
        wordRows.forEach { rowWords ->
            MultiWordRowDisplay(
                words = rowWords,
                numberMapping = numberMapping,
                userMapping = userMapping,
                selectedNumber = selectedNumber,
                onNumberSelected = onNumberSelected,
                cryptoLetters = cryptoLetters // Pass the actual crypto letters
            )
        }
    }
}

@Composable
private fun MultiWordRowDisplay(
    words: List<String>,
    numberMapping: Map<Char, Int>,
    userMapping: Map<Int, Char>,
    selectedNumber: Int?,
    onNumberSelected: (Int) -> Unit,
    cryptoLetters: Set<Char>, // Remove default value, require it to be passed
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        // Letters row
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            words.forEachIndexed { wordIndex, word ->
                if (wordIndex > 0) {
                    // Space between words
                    Spacer(modifier = Modifier.width(16.dp))
                }

                // Each word with consistent character spacing
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    word.forEach { char ->
                        if (char.isLetter()) {
                            val upperChar = char.uppercaseChar()
                            val isCryptoLetter = cryptoLetters.contains(upperChar)

                            val displayText = if (isCryptoLetter) {
                                val number = numberMapping[upperChar]
                                number?.let { userMapping[it] }?.toString() ?: "_"
                            } else {
                                upperChar.toString()
                            }

                            Text(
                                text = displayText,
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.Bold,
                                color = RvInk,
                                modifier = Modifier.width(28.dp), // Fixed width for alignment
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }
        }

        // Numbers row with identical structure
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            words.forEachIndexed { wordIndex, word ->
                if (wordIndex > 0) {
                    // Identical space between words
                    Spacer(modifier = Modifier.width(16.dp))
                }

                // Each word with identical character spacing
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    word.forEach { char ->
                        if (char.isLetter()) {
                            val upperChar = char.uppercaseChar()
                            val isCryptoLetter = cryptoLetters.contains(upperChar)

                            if (isCryptoLetter) {
                                val number = numberMapping[upperChar]
                                Box(
                                    modifier = Modifier
                                        .width(28.dp) // Same width as letters
                                        .height(32.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(32.dp)
                                            .background(
                                                color = if (selectedNumber == number)
                                                    RvSuccess
                                                else
                                                    MaterialTheme.colorScheme.surface,
                                                shape = RoundedCornerShape(6.dp)
                                            )
                                            .border(
                                                1.5.dp,
                                                if (selectedNumber == number)
                                                    RvSuccess
                                                else
                                                    MaterialTheme.colorScheme.outline,
                                                RoundedCornerShape(6.dp)
                                            )
                                            .clickable {
                                                number?.let { onNumberSelected(it) }
                                            },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = number?.toString() ?: "",
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = if (selectedNumber == number)
                                                RvOnTone
                                            else
                                                MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                }
                            } else {
                                // Empty space for non-crypto letters with same width
                                Spacer(modifier = Modifier.width(28.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun LetterSelectionKeyboard(
    selectedNumber: Int?,
    userMapping: Map<Int, Char>,
    numberMapping: Map<Char, Int>,
    onLetterSelected: (Char) -> Unit,
    modifier: Modifier = Modifier
) {
    val letters = listOf(
        "QWERTYUIOP",
        "ASDFGHJKL",
        "ZXCVBNM"
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(
                MaterialTheme.colorScheme.surface,
                RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
            )
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = if (selectedNumber != null) "Choose a letter." else "Select a number first",
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(bottom = 16.dp),
            textAlign = TextAlign.Center
        )

        letters.forEach { row ->
            Row(
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.padding(vertical = 4.dp)
            ) {
                row.forEach { letter ->
                    val isUsed = userMapping.containsValue(letter)
                    val isCorrectMapping = numberMapping[letter] == selectedNumber

                    Button(
                        onClick = { onLetterSelected(letter) },
                        enabled = selectedNumber != null && (!isUsed || isCorrectMapping),
                        modifier = Modifier
                            .padding(horizontal = 3.dp)
                            .size(width = 32.dp, height = 48.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = when {
                                !isUsed && selectedNumber != null -> RvSky // Bright blue for available
                                isUsed -> RvSuccess // Green for used
                                else -> Color(0xFF424242) // Dark gray for inactive
                            },
                            disabledContainerColor = RvInkSoft // Medium gray for disabled
                        ),
                        contentPadding = PaddingValues(0.dp),
                        elevation = ButtonDefaults.buttonElevation(
                            defaultElevation = 2.dp,
                            pressedElevation = 8.dp
                        )
                    ) {
                        Text(
                            text = letter.toString(),
                            style = MaterialTheme.typography.titleMedium, // Larger, bolder font
                            fontWeight = FontWeight.Bold, // Make letters bold
                            color = when {
                                !isUsed && selectedNumber != null -> RvOnTone // White on blue
                                isUsed -> RvOnTone // White on green
                                selectedNumber == null -> RvInkSoft // Light gray when inactive
                                else -> RvOnTone
                            }
                        )
                    }
                }
            }
        }
    }
}

data class CryptoPuzzleData(
    val originalText: String,
    val numberMapping: Map<Char, Int>, // letter to number mapping
    val revealedLetters: Set<Char>, // letters that are shown initially
    val targetWord: String? = null // optional target word for hints
)



private fun decodeText(
    originalText: String,
    numberMapping: Map<Char, Int>,
    userMapping: Map<Int, Char>
): String {
    return originalText.map { char ->
        if (char.isLetter()) {
            val number = numberMapping[char.uppercaseChar()]
            userMapping[number] ?: '_'
        } else {
            char
        }
    }.joinToString("")
}