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

// Extension function for tutorial highlighting
fun Modifier.cryptoTutorialHighlight(): Modifier {
    return this.then(
        Modifier.border(
            width = 3.dp,
            color = Color(0xFF4CAF50).copy(alpha = 0.8f),
            shape = RoundedCornerShape(8.dp)
        )
    )
}

/**
 * Tutorial screen for Crypto puzzles
 */
@Composable
fun CryptoPuzzleTutorialScreen(
    onTutorialComplete: () -> Unit,
    onTutorialSkipped: () -> Unit,
    onBack: () -> Unit
) {
    val tutorialManager = remember { CryptoPuzzleTutorialManager() }
    val steps = tutorialManager.getTutorialSteps()
    var currentStepIndex by remember { mutableStateOf(0) }
    var tutorialState by remember { mutableStateOf(TutorialState.ACTIVE) }

    // Sample tutorial crypto data
    val sampleCryptoData = createSampleCryptoData()

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
        // Tutorial version of the crypto screen
        TutorialCryptoContent(
            currentStep = steps.getOrNull(currentStepIndex),
            sampleCryptoData = sampleCryptoData,
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
 * Interactive tutorial version of the crypto screen
 */
@Composable
fun TutorialCryptoContent(
    currentStep: TutorialStep?,
    sampleCryptoData: CryptoPuzzleData,
    onTutorialAction: (String) -> Unit,
    onBack: () -> Unit
) {
    val haptics = LocalHapticFeedback.current

    // Tutorial state management
    var userMapping by remember { mutableStateOf(mutableMapOf<Int, Char>()) }
    var selectedNumber by remember { mutableStateOf<Int?>(null) }
    var isComplete by remember { mutableStateOf(false) }
    var showHint by remember { mutableStateOf(false) }
    var currentHearts by remember { mutableStateOf(3) }
    var showWrongFeedback by remember { mutableStateOf(false) }

    // Initialize revealed letters in user mapping
    LaunchedEffect(sampleCryptoData) {
        val initialMapping = mutableMapOf<Int, Char>()
        sampleCryptoData.revealedLetters.forEach { letter ->
            val number = sampleCryptoData.numberMapping[letter]
            if (number != null) {
                initialMapping[number] = letter
            }
        }
        userMapping = initialMapping
    }

    // Check if puzzle is complete
    LaunchedEffect(userMapping) {
        val cryptoLetters = sampleCryptoData.numberMapping.keys
        val allCryptoLettersDecoded = cryptoLetters.all { letter ->
            val number = sampleCryptoData.numberMapping[letter]
            number != null && userMapping[number] == letter
        }

        if (allCryptoLettersDecoded && !isComplete) {
            isComplete = true
            onTutorialAction("puzzle_complete")
        }
    }

    // Handle wrong feedback animation
    LaunchedEffect(showWrongFeedback) {
        if (showWrongFeedback) {
            kotlinx.coroutines.delay(1000)
            showWrongFeedback = false
        }
    }

    Column(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
        // Header with game info and highlighting
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 16.dp)
                .then(
                    if (currentStep?.targetComponent == "header") {
                        Modifier.cryptoTutorialHighlight()
                    } else Modifier
                ),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Back button
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, contentDescription = stringResource(R.string.back))
            }

            // Game info row
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Difficulty
                Text(
                    text = "Tutorial",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )

                // Timer
                Text(
                    text = "∞:∞",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )

                // Hearts
                Row {
                    repeat(currentHearts) {
                        Text(
                            text = "❤️",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }

                // Level
                Text(
                    text = "Tutorial",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
            }

            // Hint button
            IconButton(
                onClick = {
                    showHint = true
                    onTutorialAction("hint_clicked")
                }
            ) {
                Icon(Icons.Default.Help, contentDescription = stringResource(R.string.hint))
            }
        }

        // Wrong answer feedback
        if (showWrongFeedback) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFFFCDD2))
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "❌ Wrong letter! Try again.",
                        style = MaterialTheme.typography.titleMedium,
                        color = Color(0xFFD32F2F),
                        textAlign = TextAlign.Center
                    )
                }
            }
        }

        // Success message
        if (isComplete) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
            ) {
                Text(
                    text = "🎉 Puzzle Solved! You're a crypto master!",
                    modifier = Modifier.padding(12.dp),
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center
                )
            }
        }

        // Main puzzle area with highlighting
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(vertical = 16.dp)
                .then(
                    if (currentStep?.targetComponent == "puzzle") {
                        Modifier.cryptoTutorialHighlight()
                    } else Modifier
                ),
            contentAlignment = Alignment.Center
        ) {
            TutorialCryptoPuzzleDisplay(
                originalText = sampleCryptoData.originalText,
                numberMapping = sampleCryptoData.numberMapping,
                userMapping = userMapping,
                selectedNumber = selectedNumber,
                onNumberSelected = { number ->
                    selectedNumber = if (selectedNumber == number) null else number
                    onTutorialAction("number_selected")
                },
                cryptoLetters = sampleCryptoData.numberMapping.keys,
                isHighlighted = currentStep?.targetComponent == "puzzle"
            )
        }

        // Keyboard with highlighting
        Box(
            modifier = Modifier.then(
                if (currentStep?.targetComponent == "keyboard") {
                    Modifier.cryptoTutorialHighlight()
                } else Modifier
            )
        ) {
            TutorialLetterSelectionKeyboard(
                selectedNumber = selectedNumber,
                userMapping = userMapping,
                numberMapping = sampleCryptoData.numberMapping,
                onLetterSelected = { letter ->
                    selectedNumber?.let { number ->
                        val correctLetter = sampleCryptoData.numberMapping.entries
                            .find { it.value == number }?.key

                        if (correctLetter == letter) {
                            // Correct answer - update mapping
                            val updatedMapping = userMapping.toMutableMap()
                            updatedMapping.entries.removeAll { it.value == letter }
                            updatedMapping[number] = letter
                            userMapping = updatedMapping
                            selectedNumber = null
                            onTutorialAction("correct_letter")
                        } else {
                            // Wrong answer - show feedback and lose heart
                            showWrongFeedback = true
                            currentHearts = maxOf(0, currentHearts - 1)
                            selectedNumber = null
                            onTutorialAction("wrong_letter")
                        }
                    }
                },
                isHighlighted = currentStep?.targetComponent == "keyboard"
            )
        }
    }

    // Hint dialog
    if (showHint && sampleCryptoData.targetWord != null) {
        AlertDialog(
            onDismissRequest = { showHint = false },
            confirmButton = {
                Button(onClick = { showHint = false }) {
                    Text(stringResource(R.string.ok))
                }
            },
            title = { Text("💡 Hint") },
            text = { Text("The message contains: ${sampleCryptoData.targetWord}") }
        )
    }
}

/**
 * Tutorial version of crypto puzzle display
 */
@Composable
private fun TutorialCryptoPuzzleDisplay(
    originalText: String,
    numberMapping: Map<Char, Int>,
    userMapping: Map<Int, Char>,
    selectedNumber: Int?,
    onNumberSelected: (Int) -> Unit,
    cryptoLetters: Set<Char>,
    isHighlighted: Boolean = false
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
        label = "puzzlePulse"
    )

    Box(
        modifier = Modifier
            .alpha(pulseAlpha)
            .padding(horizontal = 16.dp)
    ) {
        CryptoPuzzleDisplay(
            originalText = originalText,
            numberMapping = numberMapping,
            userMapping = userMapping,
            selectedNumber = selectedNumber,
            onNumberSelected = onNumberSelected,
            cryptoLetters = cryptoLetters
        )
    }
}

/**
 * Tutorial version of letter selection keyboard
 */
@Composable
private fun TutorialLetterSelectionKeyboard(
    selectedNumber: Int?,
    userMapping: Map<Int, Char>,
    numberMapping: Map<Char, Int>,
    onLetterSelected: (Char) -> Unit,
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
        label = "keyboardPulse"
    )

    Box(
        modifier = modifier.alpha(pulseAlpha)
    ) {
        LetterSelectionKeyboard(
            selectedNumber = selectedNumber,
            userMapping = userMapping,
            numberMapping = numberMapping,
            onLetterSelected = onLetterSelected
        )
    }
}

/**
 * Create sample crypto data for tutorial
 */
fun createSampleCryptoData(): CryptoPuzzleData {
    // Simple message: "HELLO WORLD"
    val originalText = "HELLO WORLD"

    // Create a simple number mapping for tutorial
    val numberMapping = mapOf(
        'H' to 8,
        'E' to 5,
        'L' to 12,
        'O' to 15,
        'W' to 23,
        'R' to 18,
        'D' to 4
    )

    // Reveal one letter to help users understand
    val revealedLetters = setOf('E')

    return CryptoPuzzleData(
        originalText = originalText,
        numberMapping = numberMapping,
        revealedLetters = revealedLetters,
        targetWord = "Quote by \"Tutorial\""
    )
}

/**
 * Tutorial manager for crypto puzzles
 */
class CryptoPuzzleTutorialManager {
    fun getTutorialSteps(): List<TutorialStep> =
        listOf(
            TutorialStep(
                title = "Welcome to Crypto Puzzles! 🔐",
                description = "Learn how to decode secret messages by cracking the letter-to-number cipher. Each letter is represented by a unique number!",
                targetComponent = "puzzle",
                id = "" // Informational - introduction
            ),
            TutorialStep(
                title = "Understanding the Cipher 🔢",
                description = "Look at the puzzle above. Each letter in the secret message has been replaced with a number. The same letter always uses the same number!",
                targetComponent = "puzzle",
                id = "" // Informational - explaining the cipher
            ),
            TutorialStep(
                title = "Your Mission 🕵️",
                description = "Your job is to figure out which number represents which letter. The message reads 'HELLO WORLD' but you need to decode it!",
                targetComponent = "puzzle",
                id = "" // Informational - explaining the goal
            ),
            TutorialStep(
                title = "Free Letters Help You Start 💡",
                description = "Notice that some letters are already revealed for you! The letter 'E' is shown to help you get started. This is your first clue!",
                targetComponent = "puzzle",
                id = "" // Informational - explaining revealed letters
            ),
            TutorialStep(
                title = "Selecting Numbers 👆",
                description = "To decode a letter, first tap on any number box in the puzzle. The selected number will be highlighted in green!",
                targetComponent = "puzzle",
                id = "number_selected" // INTERACTIVE - user must select a number
            ),
            TutorialStep(
                title = "The Letter Keyboard ⌨️",
                description = "Great! Now look at the keyboard below. This is where you choose which letter the selected number represents. Try guessing a letter!",
                targetComponent = "keyboard",
                id = "" // Informational - explaining keyboard
            ),
            TutorialStep(
                title = "Making Your First Guess 🎯",
                description = "Tap any letter on the keyboard to make your guess. If you're right, the letter will appear! If wrong, you'll lose a heart. Try letter 'H' for number 8!",
                targetComponent = "keyboard",
                id = "correct_letter" // INTERACTIVE - user must make a correct guess
            ),
            TutorialStep(
                title = "Excellent Work! ✨",
                description = "Perfect! When you guess correctly, the letter appears in all places where that number occurs. Notice how your hearts stay safe with correct guesses!",
                targetComponent = "puzzle",
                id = "" // Informational - celebrating success
            ),
            TutorialStep(
                title = "Learn from Mistakes 💪",
                description = "Don't worry about wrong guesses - they help you learn! Wrong answers will show a red message and cost you a heart, but you can keep trying!",
                targetComponent = "keyboard",
                id = "wrong_letter" // INTERACTIVE - let user experience a wrong guess
            ),
            TutorialStep(
                title = "Pattern Recognition 🧩",
                description = "Look for patterns! Short words like 'HELLO' can help you figure out common letters. Double letters (like 'LL') are great clues too!",
                targetComponent = "puzzle",
                id = "" // Informational - teaching strategy
            ),
            TutorialStep(
                title = "Using Hints Wisely 🆘",
                description = "If you get stuck, tap the help button (?) in the top corner. It will give you a hint about what word or phrase to look for!",
                targetComponent = "header",
                id = "hint_clicked" // INTERACTIVE - user should try the hint
            ),
            TutorialStep(
                title = "Complete the Message 🏁",
                description = "Keep decoding until you've revealed the entire message! Each correct letter brings you closer to solving the puzzle!",
                targetComponent = "puzzle",
                id = "puzzle_complete" // INTERACTIVE - user should complete the puzzle
            ),
            TutorialStep(
                title = "You're a Code Breaker! 🎉",
                description = "Fantastic! You've mastered crypto puzzles! Use pattern recognition, logical deduction, and hints to crack any secret message. Happy decoding! 🔐✨",
                targetComponent = "puzzle",
                id = "" // Informational - congratulations
            )
        )
}