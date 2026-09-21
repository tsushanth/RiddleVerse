package com.kreativekoala.riddleverse

import android.content.Context
import android.util.Log
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.platform.testTag
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.*
import androidx.compose.ui.res.stringResource
import kotlin.random.Random
import androidx.compose.ui.input.key.*
import androidx.compose.ui.focus.*
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.focus.*
import androidx.compose.ui.input.key.*
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.draw.alpha
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.snapshots.SnapshotStateList
import java.util.Locale
// ============================================================================
// UPDATED LETTERSET SCREEN WITH DATABASE INTEGRATION
// ============================================================================

// Add these imports to your existing LetterSetPuzzleScreen.kt
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.launch
import com.kreativekoala.riddleverse.ui.theme.RvInk
import com.kreativekoala.riddleverse.ui.theme.RvInkSoft
import com.kreativekoala.riddleverse.ui.theme.RvOnTone
import com.kreativekoala.riddleverse.ui.theme.RvOutline
import com.kreativekoala.riddleverse.ui.theme.RvSky
import com.kreativekoala.riddleverse.ui.theme.RvSuccess
import com.kreativekoala.riddleverse.ui.theme.RvSurface
import com.kreativekoala.riddleverse.ui.theme.RvSurfaceRaised
import com.kreativekoala.riddleverse.ui.theme.RvScrim

// Replace your LetterSetScreenWrapper with this enhanced version:

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LetterSetScreenWrapper(
    currentPuzzle: Puzzle,
    viewModel: PuzzleViewModel,
    onBack: () -> Unit,
    handlePuzzleCompletion: (Boolean, Boolean) -> Unit
) {
    val context = LocalContext.current
    val letterSetPuzzle = remember {
        parseLetterSetPuzzle(currentPuzzle)
    }

    if (letterSetPuzzle != null) {
        // Use the enhanced version with database support
        EnhancedLetterSetGameScreen(
            puzzle = letterSetPuzzle,
            context = context,
            onBack = onBack,
            onComplete = { success, timeBonus ->
                handlePuzzleCompletion(success, timeBonus)
            }
        )
    } else {
        // Fallback error screen
        ErrorScreen(
            message = "Failed to parse letter set puzzle data",
            onBack = onBack
        )
    }
}

// Enhanced game screen that integrates both server data and local database
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EnhancedLetterSetGameScreen(
    puzzle: LetterSetPuzzle,
    context: Context,
    onBack: () -> Unit,
    onComplete: (Boolean, Boolean) -> Unit
) {
    val TAG = "EnhancedLetterSetGame"

    // Word validator state
    var wordValidator by remember { mutableStateOf<LocalWordValidator?>(null) }
    var isValidatorLoading by remember { mutableStateOf(true) }
    var validatorError by remember { mutableStateOf<String?>(null) }

    // Game state
    var timeLeft by remember { mutableStateOf(puzzle.timeLimit) }
    val foundWords: SnapshotStateList<FoundWord> = remember { mutableStateListOf() }
    var currentWord by remember { mutableStateOf("") }
    var score by remember { mutableStateOf(0) }
    var gameCompleted by remember { mutableStateOf(false) }
    var showCelebration by remember { mutableStateOf(false) }
    var hints by remember { mutableStateOf<List<String>>(emptyList()) }
    var discoveredWords by remember { mutableStateOf(0) } // Words not in server data
    var showEarlyFinishDialog by remember { mutableStateOf(false) }

    // Create word lookup map from puzzle data
    val puzzleWordMap = remember(puzzle) {
        puzzle.allWords.associateBy { normalizeWord(it.word) }
    }

    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()

    // Initialize word validator
    LaunchedEffect(context) {
        try {
            Log.d(TAG, "🚀 Initializing word validator...")
            wordValidator = LocalWordValidator.getInstance(context)

            // Generate hints from local database
            wordValidator?.let { validator ->
                hints = validator.getHints(puzzle.letterSet, 5)
                Log.d(TAG, "💡 Generated ${hints.size} hints: ${hints.take(3)}")
            }

            isValidatorLoading = false
            Log.d(TAG, "✅ Word validator initialized successfully!")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to initialize word validator", e)
            validatorError = "Failed to load dictionary: ${e.message}"
            isValidatorLoading = false
        }
    }

    // Timer
    LaunchedEffect(gameCompleted) {
        if (!gameCompleted) {
            while (timeLeft > 0) {
                delay(1000)
                timeLeft--

                // Optional: Show time warnings
                when (timeLeft) {
                    60 -> Log.d(TAG, "⏰ 1 minute remaining!")
                    30 -> Log.d(TAG, "⏰ 30 seconds remaining!")
                    10 -> Log.d(TAG, "⏰ 10 seconds remaining!")
                }
            }

            if (timeLeft == 0) {
                Log.d(TAG, "⏰ TIME'S UP! Final score: $score, Words: ${foundWords.size}")
                gameCompleted = true
                showCelebration = true

                // Determine success based on whether they hit at least bronze target
                val success = foundWords.size >= puzzle.targets.bronze
                val timeBonus = false // No time bonus since time ran out

                scope.launch {
                    delay(3000) // Show celebration for 3 seconds
                    onComplete(success, timeBonus)
                }
            }
        }
    }

    // Enhanced word submission with database fallback
    fun submitWord() {
        if (wordValidator == null) {
            Log.w(TAG, "⚠️ Word validator not ready")
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            return
        }

        scope.launch {
            val key = normalizeWord(currentWord)
            Log.d(TAG, "🎯 Submitting: '$currentWord' -> '$key'")

            if (key.length < 3) {
                Log.d(TAG, "📏 Too short: ${key.length}")
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                currentWord = ""
                return@launch
            }

            // Check if already found
            if (foundWords.any { it.key == key }) {
                Log.d(TAG, "🔄 Already found: $key")
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                currentWord = ""
                return@launch
            }

            try {
                // Use enhanced validator that checks both server data and local database
                val result = wordValidator!!.validateWord(
                    word = key,
                    letterSet = puzzle.letterSet,
                    usedWords = foundWords.map { it.key }.toSet(),
                    puzzleWords = puzzleWordMap
                )

                if (result.valid && result.word != null) {
                    // SUCCESS!
                    val foundWord = FoundWord(
                        key = result.word,
                        display = result.word,
                        points = result.points
                    )

                    foundWords.add(foundWord)
                    score += result.points

                    // Track discoveries
                    if (result.isNewDiscovery) {
                        discoveredWords++
                        Log.d(TAG, "🎉 NEW DISCOVERY: ${result.word} (+${result.points} pts)")
                    } else {
                        Log.d(TAG, "✅ Server word: ${result.word} (+${result.points} pts)")
                    }

                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)

                    // ✅ REMOVED AUTO-COMPLETION - Let players continue until time runs out!
                    // Just show achievement notifications instead
                    when {
                        foundWords.size == puzzle.targets.gold && !gameCompleted -> {
                            // Show gold achievement but keep playing
                            Log.d(TAG, "🥇 GOLD TARGET REACHED! Keep going for bonus points!")
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        }
                        foundWords.size == puzzle.targets.silver && !gameCompleted -> {
                            Log.d(TAG, "🥈 SILVER TARGET REACHED! Going for gold!")
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        }
                        foundWords.size == puzzle.targets.bronze && !gameCompleted -> {
                            Log.d(TAG, "🥉 BRONZE TARGET REACHED! Going for silver!")
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        }
                    }
                } else {
                    // Invalid word
                    Log.d(TAG, "❌ Invalid: ${result.reason} [${result.source}]")
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                }
            } catch (e: Exception) {
                Log.e(TAG, "💥 Validation error", e)
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            }

            currentWord = ""
        }
    }

    fun resetWord() {
        currentWord = ""
        Log.d(TAG, "🔄 Reset word")
    }

    fun addLetter(letter: String) {
        currentWord += letter
        Log.d(TAG, "📝 '$letter' -> '$currentWord'")
    }

    fun removeLetter() {
        if (currentWord.isNotEmpty()) {
            currentWord = currentWord.dropLast(1)
            Log.d(TAG, "⌫ -> '$currentWord'")
        }
    }

    // Show loading screen while initializing
    if (isValidatorLoading) {
        LoadingScreen("Loading dictionary...\nPreparing ${puzzle.letterSet} puzzle")
        return
    }

    // Show error screen if initialization failed
    if (validatorError != null) {
        ErrorScreen(
            message = validatorError!!,
            onBack = onBack
        )
        return
    }

    // Main game UI (fit-to-screen: HUD on top, word + found words in the middle, keys + actions pinned below)
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(Color(0xFF4A4A6B), Color(0xFF5A5A7A))
                )
            )
    ) {
        val compact = maxHeight < 600.dp
        val landscape = maxWidth > maxHeight
        val gutter = if (compact) 12.dp else 16.dp
        val contentWidth = if (landscape) minOf(maxWidth, 1000.dp) else minOf(maxWidth, 640.dp)
        val paneWidth = if (landscape) (contentWidth - gutter * 2 - 16.dp) / 2 else contentWidth - gutter * 2
        val keyGap = 8.dp
        val keyCols = 3
        val keySize = minOf(if (compact) 56.dp else 72.dp, (paneWidth - keyGap * (keyCols - 1)) / keyCols).coerceAtLeast(48.dp)
        val goldText = Color(0xFFFFD700)
        val urgentText = Color(0xFFFFB4AB)

        val hud: @Composable () -> Unit = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Leave / pause button
                IconButton(
                    onClick = onBack,
                    modifier = Modifier
                        .size(48.dp)
                        .background(RvSurface, RoundedCornerShape(8.dp))
                ) {
                    Icon(
                        imageVector = Icons.Default.ArrowBack,
                        contentDescription = stringResource(R.string.back),
                        tint = RvInk
                    )
                }

                Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = puzzle.letterSet,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = RvOnTone,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = String.format("%02d:%02d", timeLeft / 60, timeLeft % 60),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (timeLeft < 60) urgentText else goldText,
                        maxLines = 1
                    )
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "${stringResource(R.string.score_label)}: $score",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = RvOnTone,
                        maxLines = 1
                    )
                    if (discoveredWords > 0 && !compact) {
                        Text(
                            text = "🎉 +$discoveredWords new",
                            fontSize = 12.sp,
                            color = goldText,
                            maxLines = 1
                        )
                    }

                    // Early finish (only once the bronze target is reached)
                    if (foundWords.size >= puzzle.targets.bronze) {
                        TextButton(
                            onClick = { showEarlyFinishDialog = true },
                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp),
                            modifier = Modifier.heightIn(min = 48.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.finish_early),
                                fontSize = 12.sp,
                                color = RvOnTone,
                                maxLines = 1
                            )
                        }
                    }
                }
            }

            // Progress toward the medals
            if (foundWords.size > 0) {
                val progress = foundWords.size.toFloat() / puzzle.targets.gold

                Column {
                    LinearProgressIndicator(
                        progress = minOf(progress, 1f),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp),
                        color = when {
                            foundWords.size >= puzzle.targets.gold -> Color(0xFFFFD700)
                            foundWords.size >= puzzle.targets.silver -> Color(0xFFC0C0C0)
                            foundWords.size >= puzzle.targets.bronze -> Color(0xFFCD7F32)
                            else -> RvSky
                        }
                    )

                    if (!compact) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "🥉 ${puzzle.targets.bronze}",
                                fontSize = 12.sp,
                                color = if (foundWords.size >= puzzle.targets.bronze) Color(0xFFFFB067) else RvOnTone.copy(alpha = 0.8f),
                                fontWeight = if (foundWords.size >= puzzle.targets.bronze) FontWeight.Bold else FontWeight.Normal
                            )
                            Text(
                                text = "🥈 ${puzzle.targets.silver}",
                                fontSize = 12.sp,
                                color = if (foundWords.size >= puzzle.targets.silver) Color(0xFFE0E0E0) else RvOnTone.copy(alpha = 0.8f),
                                fontWeight = if (foundWords.size >= puzzle.targets.silver) FontWeight.Bold else FontWeight.Normal
                            )
                            Text(
                                text = "🥇 ${puzzle.targets.gold}",
                                fontSize = 12.sp,
                                color = if (foundWords.size >= puzzle.targets.gold) goldText else RvOnTone.copy(alpha = 0.8f),
                                fontWeight = if (foundWords.size >= puzzle.targets.gold) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }
                }
            }
        }

        val wordBox: @Composable () -> Unit = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = if (compact) 48.dp else 64.dp)
                    .background(RvSurface, RoundedCornerShape(12.dp))
                    .padding(horizontal = 8.dp, vertical = 8.dp)
                    .testTag("ls_word"),
                contentAlignment = Alignment.Center
            ) {
                val display = currentWord.uppercase()
                Text(
                    text = if (display.isEmpty()) {
                        "TAP LETTERS TO FORM WORDS"
                    } else {
                        display
                    },
                    fontSize = if (display.isEmpty()) 14.sp else 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = RvInk,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        val foundArea: @Composable () -> Unit = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Found Words (${foundWords.size})",
                        fontSize = 14.sp,
                        color = RvOnTone,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )

                    Text(
                        text = "Target: ${puzzle.targets.bronze}+",
                        fontSize = 12.sp,
                        color = RvOnTone.copy(alpha = 0.85f),
                        maxLines = 1
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                if (foundWords.isEmpty()) {
                    Text(
                        text = "No words found yet",
                        fontSize = 14.sp,
                        color = RvOnTone.copy(alpha = 0.85f),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )

                    if (hints.isNotEmpty() && !compact) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "💡 Try: ${hints.take(3).joinToString(", ")}",
                            fontSize = 12.sp,
                            color = RvOnTone.copy(alpha = 0.85f),
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth(),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                } else {
                    // secondary list of chips (newest last); the keys and actions never depend on it
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(horizontal = 0.dp)
                    ) {
                        items(foundWords, key = { it.key }) { fw ->
                            // Check if this was a discovery
                            val isDiscovery = !puzzleWordMap.containsKey(fw.key)

                            Box(
                                modifier = Modifier
                                    .background(
                                        if (isDiscovery) {
                                            Color(0xFFFFD700).copy(alpha = 0.4f) // Gold for discoveries
                                        } else {
                                            RvSuccess.copy(alpha = 0.4f) // Green for server words
                                        },
                                        RoundedCornerShape(16.dp)
                                    )
                                    .padding(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        if (isDiscovery) {
                                            Text("🎉", fontSize = 12.sp)
                                            Spacer(modifier = Modifier.width(2.dp))
                                        }
                                        Text(
                                            text = fw.display,
                                            fontSize = 14.sp,
                                            color = RvOnTone,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1
                                        )
                                    }
                                    Text(
                                        text = "${fw.points}pts",
                                        fontSize = 12.sp,
                                        color = RvOnTone.copy(alpha = 0.9f),
                                        maxLines = 1
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        val keysBlock: @Composable () -> Unit = {
            CompositionLocalProvider(LocalLsKeySize provides keySize) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(keyGap)
                ) {
                    puzzle.letters.chunked(keyCols).forEach { rowLetters ->
                        Row(horizontalArrangement = Arrangement.spacedBy(keyGap)) {
                            rowLetters.forEach { letter ->
                                LetterKey(
                                    letter = letter,
                                    onClick = { addLetter(letter) }
                                )
                            }
                        }
                    }
                }
            }
        }

        val actionsBlock: @Composable () -> Unit = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                LsActionButton(
                    icon = Icons.Default.Refresh,
                    description = stringResource(R.string.clear),
                    onClick = ::resetWord,
                    backgroundColor = Color(0xFF495057),
                    enabled = currentWord.isNotEmpty(),
                    modifier = Modifier.weight(1f)
                )
                // primary action: submit the word
                LsActionButton(
                    icon = Icons.Default.KeyboardReturn,
                    description = stringResource(R.string.submit),
                    onClick = ::submitWord,
                    backgroundColor = Color(0xFF0058C7),
                    enabled = currentWord.length >= 3,
                    modifier = Modifier.weight(1.6f)
                )
                LsActionButton(
                    icon = Icons.Default.Backspace,
                    description = "Backspace",
                    onClick = ::removeLetter,
                    backgroundColor = Color(0xFFB02A37),
                    enabled = currentWord.isNotEmpty(),
                    modifier = Modifier.weight(1f)
                )
            }
        }

        if (landscape) {
            Column(
                modifier = Modifier
                    .widthIn(max = contentWidth)
                    .fillMaxSize()
                    .align(Alignment.TopCenter)
                    .padding(horizontal = gutter, vertical = 4.dp)
            ) {
                hud()
                Spacer(Modifier.height(4.dp))
                Row(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Column(
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        wordBox()
                        foundArea()
                    }
                    Column(
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                        verticalArrangement = Arrangement.SpaceBetween,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        keysBlock()
                        actionsBlock()
                    }
                }
                Spacer(Modifier.height(4.dp))
            }
        } else {
            Column(
                modifier = Modifier
                    .widthIn(max = contentWidth)
                    .fillMaxSize()
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(horizontal = gutter, vertical = if (compact) 4.dp else 12.dp)
            ) {
                hud()
                Spacer(Modifier.height(if (compact) 4.dp else 8.dp))
                wordBox()
                Spacer(Modifier.height(if (compact) 4.dp else 8.dp))
                // flexible area: found words; everything below is pinned
                Box(modifier = Modifier.weight(1f).fillMaxWidth()) { foundArea() }
                Spacer(Modifier.height(if (compact) 4.dp else 8.dp))
                keysBlock()
                Spacer(Modifier.height(if (compact) 8.dp else 12.dp))
                actionsBlock()
                Spacer(Modifier.height(if (compact) 4.dp else 8.dp))
            }
        }

        // Celebration overlay
        if (showCelebration) {
            EnhancedCelebration(
                score = score,
                wordsFound = foundWords.size,
                discoveredWords = discoveredWords,
                timeBonus = timeLeft > puzzle.timeLimit * 0.5,
                targets = puzzle.targets
            )
        }

        if (showEarlyFinishDialog) {
            AlertDialog(
                onDismissRequest = { showEarlyFinishDialog = false },
                confirmButton = {
                    TextButton(
                        onClick = {
                            showEarlyFinishDialog = false
                            gameCompleted = true
                            showCelebration = true

                            val success = foundWords.size >= puzzle.targets.bronze
                            val timeBonus = timeLeft > puzzle.timeLimit * 0.5

                            scope.launch {
                                delay(2000)
                                onComplete(success, timeBonus)
                            }
                        }
                    ) {
                        Text("Yes, Finish")
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = { showEarlyFinishDialog = false }
                    ) {
                        Text("Keep Playing")
                    }
                },
                title = {
                    Text("Finish Early?")
                },
                text = {
                    Column {
                        Text("You've found ${foundWords.size} words so far!")
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = when {
                                foundWords.size >= puzzle.targets.gold -> "🥇 Gold level achieved! Amazing work!"
                                foundWords.size >= puzzle.targets.silver -> "🥈 Silver level achieved! Going for gold?"
                                foundWords.size >= puzzle.targets.bronze -> "🥉 Bronze level achieved! Want to continue?"
                                else -> "Keep playing to reach bronze target (${puzzle.targets.bronze} words)"
                            },
                            fontSize = 14.sp
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        if (timeLeft > 60) {
                            Text(
                                text = "You still have ${String.format("%02d:%02d", timeLeft / 60, timeLeft % 60)} remaining!",
                                fontSize = 12.sp,
                                color = RvInkSoft
                            )
                        }
                    }
                }
            )
        }
    }
}

// Enhanced celebration with discovery tracking
@Composable
fun EnhancedCelebration(
    score: Int,
    wordsFound: Int,
    discoveredWords: Int,
    timeBonus: Boolean,
    targets: LetterSetTargets
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(RvScrim),
        contentAlignment = Alignment.Center
    ) {
        Card(
            colors = CardDefaults.cardColors(containerColor = RvSurfaceRaised),
            shape = RoundedCornerShape(20.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 16.dp)
        ) {
            Column(
                modifier = Modifier.padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                val (medal, title, message) = when {
                    wordsFound >= targets.gold -> Triple("🥇", "GOLD ACHIEVED!", "Outstanding performance!")
                    wordsFound >= targets.silver -> Triple("🥈", "SILVER ACHIEVED!", "Excellent work!")
                    wordsFound >= targets.bronze -> Triple("🥉", "BRONZE ACHIEVED!", "Great job!")
                    else -> Triple("🎯", "TIME'S UP!", "Good effort!")
                }

                Text(text = medal, fontSize = 48.sp)

                Text(
                    text = title,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = RvSuccess
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = message,
                    fontSize = 16.sp,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Found $wordsFound words!",
                    fontSize = 16.sp,
                    textAlign = TextAlign.Center,
                    fontWeight = FontWeight.Bold
                )

                if (discoveredWords > 0) {
                    Text(
                        text = "🎉 Including $discoveredWords new discoveries!",
                        fontSize = 14.sp,
                        color = Color(0xFFFFD700),
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                }

                if (timeBonus) {
                    Text(
                        text = "⚡ Time Bonus Earned!",
                        fontSize = 14.sp,
                        color = RvSky,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "${stringResource(R.string.final_score)}: $score",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = RvInk
                )
            }
        }
    }
}

private fun normalizeWord(s: String): String =
    s.trim()
        .uppercase(Locale.ROOT)
        .filter { it.isLetter() } // letters only (both input and dict)

data class FoundWord(val key: String, val display: String, val points: Int)

data class WordSearchPuzzle(
    val grid: Array<Array<Char>>,
    val words: List<String>,
    val gridSize: Int = 15,
    val theme: String = "Nature",
    val difficulty: String = "Medium",
    val timeLimit: Int = 300 // 5 minutes
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as WordSearchPuzzle
        return grid.contentDeepEquals(other.grid) &&
                words == other.words &&
                gridSize == other.gridSize
    }

    override fun hashCode(): Int {
        var result = grid.contentDeepHashCode()
        result = 31 * result + words.hashCode()
        result = 31 * result + gridSize
        return result
    }
}

data class WordPosition(
    val word: String,
    val startRow: Int,
    val startCol: Int,
    val direction: LDirection,
    val positions: List<Pair<Int, Int>>
)

enum class LDirection {
    HORIZONTAL, VERTICAL, DIAGONAL_DOWN_RIGHT, DIAGONAL_DOWN_LEFT,
    HORIZONTAL_REVERSE, VERTICAL_REVERSE, DIAGONAL_UP_RIGHT, DIAGONAL_UP_LEFT
}

data class SelectedPath(
    val startPos: Pair<Int, Int>,
    val endPos: Pair<Int, Int>,
    val positions: List<Pair<Int, Int>>,
    val word: String = ""
)


/** Key size chosen by the game screen so the 3-column key pad always fits its pane. */
internal val LocalLsKeySize = compositionLocalOf { 72.dp }

@Composable fun LetterKey( letter: String, onClick: () -> Unit ) {
    val keySize = LocalLsKeySize.current
    Box(
        modifier = Modifier
            .size(keySize)
            .background(RvSurface, RoundedCornerShape(8.dp))
            .border(2.dp, RvOutline, RoundedCornerShape(8.dp))
            .clickable { onClick() }
            .testTag("ls_key"),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = letter.uppercase(),
            fontSize = with(LocalDensity.current) { (keySize * 0.42f).toSp() }, // glyph follows the key, not the font scale
            fontWeight = FontWeight.Bold,
            color = RvInk
        )
    }
}

/** Action key with a description for screen readers (used by the game screen). */
@Composable
private fun LsActionButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    onClick: () -> Unit,
    backgroundColor: Color,
    enabled: Boolean,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .height(56.dp)
            .background(
                if (enabled) backgroundColor else RvOutline,
                RoundedCornerShape(12.dp)
            )
            .clickable(enabled = enabled) { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = description,
            tint = if (enabled) RvOnTone else RvInkSoft,
            modifier = Modifier.size(28.dp)
        )
    }
} @Composable fun ActionButton( icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit, backgroundColor: Color, enabled: Boolean = true ) { Box( modifier = Modifier .size(60.dp) .background( if (enabled) backgroundColor else backgroundColor.copy(alpha = 0.5f), RoundedCornerShape(8.dp) ) .clickable(enabled = enabled) { onClick() }, contentAlignment = Alignment.Center ) { Icon( imageVector = icon, contentDescription = null, tint = RvSurfaceRaised, modifier = Modifier.size(24.dp) ) } }


@Composable
fun LetterSetHeader(
    timeLeft: Int,
    score: Int,
    letterSet: String,
    onBack: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth().statusBarsPadding(),
        colors = CardDefaults.cardColors(
            containerColor = RvSurfaceRaised
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier
                    .size(40.dp)
                    .background(Color(0xFFF0F0F0), CircleShape)
            ) {
                Icon(
                    imageVector = Icons.Default.ArrowBack,
                    contentDescription = stringResource(R.string.back),
                    tint = RvInk
                )
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "Letter Set: $letterSet",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = RvInk
                )
                Text(
                    text = "${stringResource(R.string.score_label)}: $score",
                    fontSize = 14.sp,
                    color = RvInkSoft
                )
            }

            Text(
                text = String.format("%02d:%02d", timeLeft / 60, timeLeft % 60),
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = if (timeLeft < 60) Color.Red else RvInk
            )
        }
    }
}

@Composable
fun LetterSetProgress(
    progress: Float,
    targets: LetterSetTargets,
    foundWords: Int
) {
    Column {
        LinearProgressIndicator(
            progress = progress,
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp)),
            color = when {
                foundWords >= targets.gold -> Color(0xFFFFD700) // Gold
                foundWords >= targets.silver -> Color(0xFFC0C0C0) // Silver
                foundWords >= targets.bronze -> Color(0xFFCD7F32) // Bronze
                else -> RvSky
            },
            trackColor = RvOutline
        )

        Spacer(modifier = Modifier.height(4.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Bronze: ${targets.bronze}",
                fontSize = 10.sp,
                color = RvInk
            )
            Text(
                text = "Silver: ${targets.silver}",
                fontSize = 10.sp,
                color = RvInk
            )
            Text(
                text = "Gold: ${targets.gold}",
                fontSize = 10.sp,
                color = RvInk
            )
        }
    }
}

@Composable
fun LetterTiles(
    letters: List<String>,
    selectedIndices: List<Int>,
    onLetterSelected: (Int) -> Unit
) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(horizontal = 16.dp)
    ) {
        itemsIndexed(letters) { index, letter ->
            val isSelected = index in selectedIndices

            Card(
                modifier = Modifier
                    .size(64.dp)
                    .clickable { onLetterSelected(index) },
                colors = CardDefaults.cardColors(
                    containerColor = if (isSelected) RvSky else RvSurfaceRaised
                ),
                elevation = CardDefaults.cardElevation(
                    defaultElevation = if (isSelected) 8.dp else 4.dp
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = letter,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isSelected) RvOnTone else RvInk
                    )
                }
            }
        }
    }
}

@Composable
fun FoundWordsList(
    foundWords: Set<String>,
    allWords: List<LetterSetWord>,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Text(
            text = "Found Words (${foundWords.size})",
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = RvInk
        )

        Spacer(modifier = Modifier.height(8.dp))

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            items(foundWords.toList()) { word ->
                val wordData = allWords.find { it.word.uppercase() == word.uppercase() }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            RvSuccess.copy(alpha = 0.1f),
                            RoundedCornerShape(4.dp)
                        )
                        .padding(8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = word,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = RvInk
                    )

                    Text(
                        text = "${wordData?.points ?: 0} pts",
                        fontSize = 12.sp,
                        color = RvInkSoft
                    )
                }
            }
        }
    }
}

@Composable
fun LetterSetCelebration(
    score: Int,
    wordsFound: Int,
    timeBonus: Boolean,
    targets: LetterSetTargets
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(RvScrim),
        contentAlignment = Alignment.Center
    ) {
        Card(
            colors = CardDefaults.cardColors(containerColor = RvSurfaceRaised),
            shape = RoundedCornerShape(20.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 16.dp)
        ) {
            Column(
                modifier = Modifier.padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                val medal = when {
                    wordsFound >= targets.gold -> "🥇"
                    wordsFound >= targets.silver -> "🥈"
                    wordsFound >= targets.bronze -> "🥉"
                    else -> "🎉"
                }

                Text(
                    text = medal,
                    fontSize = 48.sp
                )

                Text(
                    text = stringResource(R.string.great_job),
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = RvSuccess
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "You found $wordsFound words!",
                    fontSize = 16.sp,
                    textAlign = TextAlign.Center
                )

                if (timeBonus) {
                    Text(
                        text = "⚡ Time Bonus!",
                        fontSize = 14.sp,
                        color = RvSky,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "${stringResource(R.string.score_label)}: $score",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = RvInk
                )
            }
        }
    }
}




fun calculateWordPositions(
    startRow: Int,
    startCol: Int,
    wordLength: Int,
    direction: LDirection,
    gridSize: Int
): List<Pair<Int, Int>> {
    val positions = mutableListOf<Pair<Int, Int>>()

    val (dRow, dCol) = when (direction) {
        LDirection.HORIZONTAL -> 0 to 1
        LDirection.VERTICAL -> 1 to 0
        LDirection.DIAGONAL_DOWN_RIGHT -> 1 to 1
        LDirection.DIAGONAL_DOWN_LEFT -> 1 to -1
        LDirection.HORIZONTAL_REVERSE -> 0 to -1
        LDirection.VERTICAL_REVERSE -> -1 to 0
        LDirection.DIAGONAL_UP_RIGHT -> -1 to 1
        LDirection.DIAGONAL_UP_LEFT -> -1 to -1
    }

    for (i in 0 until wordLength) {
        val row = startRow + i * dRow
        val col = startCol + i * dCol

        if (row in 0 until gridSize && col in 0 until gridSize) {
            positions.add(row to col)
        } else {
            return emptyList() // Invalid position
        }
    }

    return positions
}

fun isValidPlacement(
    grid: Array<Array<Char>>,
    word: String,
    positions: List<Pair<Int, Int>>
): Boolean {
    return positions.indices.all { i ->
        val (row, col) = positions[i]
        grid[row][col] == ' ' || grid[row][col] == word[i]
    }
}

fun fillEmptySpaces(grid: Array<Array<Char>>, gridSize: Int) {
    val letters = "ABCDEFGHIJKLMNOPQRSTUVWXYZ"
    for (row in 0 until gridSize) {
        for (col in 0 until gridSize) {
            if (grid[row][col] == ' ') {
                grid[row][col] = letters.random()
            }
        }
    }
}

fun findWordPositions(grid: Array<Array<Char>>, words: List<String>): Map<String, WordPosition> {
    val positions = mutableMapOf<String, WordPosition>()

    words.forEach { word ->
        val position = findWordInGrid(grid, word.uppercase())
        if (position != null) {
            positions[word.uppercase()] = position
        }
    }

    return positions
}

fun findWordInGrid(grid: Array<Array<Char>>, word: String): WordPosition? {
    val gridSize = grid.size

    for (row in 0 until gridSize) {
        for (col in 0 until gridSize) {
            for (direction in LDirection.values()) {
                val positions = calculateWordPositions(row, col, word.length, direction, gridSize)
                if (positions.size == word.length) {
                    val foundWord = positions.map { (r, c) -> grid[r][c] }.joinToString("")
                    if (foundWord == word || foundWord == word.reversed()) {
                        return WordPosition(word, row, col, direction, positions)
                    }
                }
            }
        }
    }

    return null
}

fun calculatePath(start: Pair<Int, Int>, end: Pair<Int, Int>, gridSize: Int): List<Pair<Int, Int>> {
    val (startRow, startCol) = start
    val (endRow, endCol) = end

    val rowDiff = endRow - startRow
    val colDiff = endCol - startCol

    val steps = maxOf(abs(rowDiff), abs(colDiff))
    if (steps == 0) return listOf(start)

    val rowStep = if (rowDiff == 0) 0 else rowDiff / steps
    val colStep = if (colDiff == 0) 0 else colDiff / steps

    // Only allow straight lines and diagonals
    if (abs(rowStep) > 1 || abs(colStep) > 1 || (rowDiff != 0 && colDiff != 0 && abs(rowDiff) != abs(colDiff))) {
        return listOf(start)
    }

    val positions = mutableListOf<Pair<Int, Int>>()
    for (i in 0..steps) {
        val row = startRow + i * rowStep
        val col = startCol + i * colStep
        if (row in 0 until gridSize && col in 0 until gridSize) {
            positions.add(row to col)
        }
    }

    return positions
}

fun extractWord(grid: Array<Array<Char>>, positions: List<Pair<Int, Int>>): String {
    return positions.map { (row, col) -> grid[row][col] }.joinToString("")
}

fun calculateWordSearchScore(wordsFound: Int, timeLeft: Int, timeLimit: Int, timeBonus: Boolean): Int {
    val baseScore = wordsFound * 100
    val timeScore = (timeLeft.toFloat() / timeLimit * 50).toInt()
    val bonus = if (timeBonus) 200 else 0

    return baseScore + timeScore + bonus
}

// Add these data classes to LetterSetPuzzleScreen.kt

data class LetterSetPuzzle(
    val letterSet: String,
    val letters: List<String>,
    val allWords: List<LetterSetWord>,
    val keyWord: String?,
    val difficulty: String,
    val timeLimit: Int,
    val scoring: LetterSetScoring,
    val targets: LetterSetTargets,
    val metadata: LetterSetMetadata
)

data class LetterSetWord(
    val word: String,
    val length: Int,
    val points: Int,
    val rarity: String,
    val frequency: Long,
    val usesAllLetters: Boolean
)

data class LetterSetScoring(
    val basePointsPerWord: Int,
    val lengthMultiplier: Map<String, Double>,
    val rarityBonus: Map<String, Int>,
    val allLettersBonus: Int,
    val speedBonus: Map<String, Int>?
)

data class LetterSetTargets(
    val bronze: Int,
    val silver: Int,
    val gold: Int
)

data class LetterSetMetadata(
    val generatedAt: String,
    val totalWords: Int,
    val source: String,
    val expectedDifficulty: String,
    val letterSetSource: String,
    val keyWordFound: Boolean
)

// Replace the LetterSetScreenWrapper function with this:



// Add this parsing function:

fun parseLetterSetPuzzle(puzzle: Puzzle): LetterSetPuzzle? {
    return try {
        val jsonString = puzzle.question ?: return null
        val gson = com.google.gson.Gson()

        // Parse the JSON string into our data structure
        val puzzleData = gson.fromJson(jsonString, LetterSetPuzzle::class.java)

        Log.d("LetterSetParser", "Successfully parsed letter set: ${puzzleData.letterSet}")
        Log.d("LetterSetParser", "Total words: ${puzzleData.allWords.size}")
        Log.d("LetterSetParser", "Key word: ${puzzleData.keyWord}")

        puzzleData
    } catch (e: Exception) {
        Log.e("LetterSetParser", "Failed to parse letter set puzzle", e)
        null
    }
}

// Add error screen component:

@Composable
fun ErrorScreen(
    message: String,
    onBack: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.Error,
            contentDescription = "Error",
            modifier = Modifier.size(64.dp),
            tint = Color.Red
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Oops!",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = message,
            fontSize = 16.sp,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(24.dp))

        Button(onClick = onBack) {
            Text("Go Back")
        }
    }
}