// AdaptiveCryptoPuzzleScreen.kt
package com.kreativekoala.riddleverse

import com.kreativekoala.riddleverse.ui.theme.*
import android.util.Log
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Help
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.max

// Adaptive crypto puzzle configuration
data class AdaptiveCryptoConfig(
    val autoRevealAllInstances: Boolean, // Whether to reveal all instances of a letter when one is solved
    val lockedPositionsRatio: Float, // Ratio of positions that remain locked until dependencies are met
    val dependencyChainLength: Int, // How many adjacent letters must be revealed to unlock a position
    val hintRevealCount: Int, // Number of initial hints given
    val name: String,
    val description: String
)

// Enhanced crypto puzzle data with adaptive features
data class AdaptiveCryptoPuzzleData(
    val originalText: String,
    val numberMapping: Map<Char, Int>,
    val revealedLetters: Set<Char>,
    val targetWord: String? = null,
    val lockedPositions: Set<Int> = emptySet(), // Position indices that are locked
    val positionDependencies: Map<Int, Set<Int>> = emptyMap() // Position -> required positions to unlock
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdaptiveCryptoWordScreenWithTimer(
    initialPuzzleData: CryptoPuzzleData,
    initialDifficulty: String,
    timer: String,
    hearts: Int,
    level: String,
    onBack: () -> Unit,
    onComplete: (Boolean, Int) -> Unit
) {
    val TAG = "AdaptiveCryptoPuzzle"

    // Adaptive difficulty manager
    val difficultyManager = remember { DifficultyManager() }
    var currentDifficultyLevel by remember {
        mutableStateOf(difficultyManager.getCurrentDifficulty("cryptoPuzzle"))
    }
    var adaptationInfo by remember { mutableStateOf<DifficultyManager.AdaptiveConfig?>(null) }
    var showAdaptationNotification by remember { mutableStateOf(false) }

    // ✅ NEW: Competitive ranking state
    val currentUser = FirebaseAuth.getInstance().currentUser
    var competitiveInsight by remember { mutableStateOf<CompetitiveRankingManager.CompetitiveInsight?>(null) }

    LaunchedEffect(currentDifficultyLevel) {
        if (currentUser != null) {
            try {
                val adaptiveManager = UnifiedAdaptiveManager.getInstance()
                competitiveInsight = adaptiveManager.getCompetitiveInsight(
                    userId = currentUser.uid,
                    puzzleType = "cryptoPuzzle",
                    difficulty = currentDifficultyLevel.name
                )
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load competitive insight", e)
            }
        }
    }

    // Generate adaptive configuration
    val adaptiveConfig = remember(currentDifficultyLevel) {
        generateAdaptiveCryptoConfig(currentDifficultyLevel)
    }

    // Convert initial puzzle data to adaptive format
    val adaptivePuzzleData = remember(initialPuzzleData, adaptiveConfig) {
        convertToAdaptivePuzzleData(initialPuzzleData, adaptiveConfig)
    }

    var userMapping by remember { mutableStateOf(mutableMapOf<Int, Char>()) }
    var selectedNumber by remember { mutableStateOf<Int?>(null) }
    var isComplete by remember { mutableStateOf(false) }
    var showHint by remember { mutableStateOf(false) }
    var showCompletionDialog by remember { mutableStateOf(false) }
    var finalScore by remember { mutableStateOf(0) }
    var currentHearts by remember { mutableStateOf(hearts) }
    var showWrongFeedback by remember { mutableStateOf(false) }
    var gameOver by remember { mutableStateOf(false) }
    var currentStreak by remember { mutableStateOf(0) }
    var gamesPlayedThisSession by remember { mutableStateOf(0) }
    var startTime by remember { mutableLongStateOf(System.currentTimeMillis()) }

    // ✅ NEW: Session tracking
    var sessionStartTime by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var correctAnswers by remember { mutableStateOf(0) }
    var totalAnswers by remember { mutableStateOf(0) }

    // Track locked positions that are currently locked
    var currentlyLockedPositions by remember {
        mutableStateOf(adaptivePuzzleData.lockedPositions.toMutableSet())
    }

    val feedbackManager = rememberUnifiedFeedbackManager()
    val currentLevel = feedbackManager.getCurrentLevel()
    val streakInfo = feedbackManager.getStreakInfo()

    val haptics = LocalHapticFeedback.current
    // Initialize revealed letters in user mapping
    LaunchedEffect(adaptivePuzzleData) {
        val initialMapping = mutableMapOf<Int, Char>()
        adaptivePuzzleData.revealedLetters.forEach { letter ->
            val number = adaptivePuzzleData.numberMapping[letter]
            if (number != null) {
                initialMapping[number] = letter
            }
        }
        userMapping = initialMapping
        startTime = System.currentTimeMillis()
        sessionStartTime = System.currentTimeMillis()
    }

    // Function to check if a position should be unlocked
    fun checkPositionUnlock(position: Int) {
        if (position in currentlyLockedPositions) {
            val dependencies = adaptivePuzzleData.positionDependencies[position] ?: emptySet()
            val allDependenciesMet = dependencies.all { depPos ->
                // Check if the dependency position has been revealed
                val charAtPos = getCharacterAtPosition(adaptivePuzzleData.originalText, depPos)
                val number = adaptivePuzzleData.numberMapping[charAtPos]
                number != null && userMapping.containsKey(number)
            }

            if (allDependenciesMet) {
                currentlyLockedPositions.remove(position)
                Log.d(TAG, "🔓 Position $position unlocked! Dependencies met: $dependencies")
            }
        }
    }

    // Enhanced letter placement with adaptive rules
    fun placeLetter(number: Int, letter: Char): Boolean {
        val correctLetter = adaptivePuzzleData.numberMapping.entries
            .find { it.value == number }?.key

        if (correctLetter == letter) {
            val updatedMapping = userMapping.toMutableMap()

            if (adaptiveConfig.autoRevealAllInstances) {
                // Easy mode: reveal all instances of this letter
                updatedMapping.entries.removeAll { it.value == letter }
                updatedMapping[number] = letter
            } else {
                // Advanced mode: only reveal this specific instance
                updatedMapping[number] = letter
            }

            userMapping = updatedMapping

            // Check if any locked positions can now be unlocked
            currentlyLockedPositions.toList().forEach { lockedPos ->
                checkPositionUnlock(lockedPos)
            }

            return true
        }
        return false
    }

    // ✅ UPDATED: Enhanced performance recording
    fun recordAdaptivePerformance(
        isCorrect: Boolean,
        timeSpent: Long,
        streak: Int,
        livesRemaining: Int
    ) {
        recordUnifiedPerformance(
            difficultyManager = difficultyManager,
            isCorrect = isCorrect,
            timeSpent = timeSpent,
            streak = streak,
            livesRemaining = livesRemaining,
            difficulty = currentDifficultyLevel,
            challengeComplexity = adaptivePuzzleData.numberMapping.size,
            totalScore = finalScore,
            puzzleType = "cryptoPuzzle"
        ) { config ->
            adaptationInfo = config
            if (config.confidenceScore > 0.5f) {
                currentDifficultyLevel = config.level
                showAdaptationNotification = SHOW_ADAPTATION_NOTICES
            }
        }
    }

    // Check if puzzle is complete
    LaunchedEffect(userMapping) {
        val cryptoLetters = adaptivePuzzleData.numberMapping.keys
        val allCryptoLettersDecoded = cryptoLetters.all { letter ->
            val number = adaptivePuzzleData.numberMapping[letter]
            number != null && userMapping[number] == letter
        }

        if (allCryptoLettersDecoded && !isComplete) {
            isComplete = true
            val timeSpent = System.currentTimeMillis() - startTime
            currentStreak++
            gamesPlayedThisSession++
            correctAnswers++
            totalAnswers++

            // ✅ UPDATED: Use unified score calculation
            finalScore = calculateUnifiedAdaptiveScore(
                isCorrect = true,
                timeSpent = timeSpent,
                difficulty = currentDifficultyLevel,
                challengeComplexity = adaptivePuzzleData.numberMapping.size,
                currentStreak = currentStreak,
                challengesCompleted = 1,
                timeLimit = currentDifficultyLevel.timeLimit,
                puzzleType = "cryptoPuzzle"
            )

            // Record successful performance
            recordAdaptivePerformance(
                isCorrect = true,
                timeSpent = timeSpent,
                streak = currentStreak,
                livesRemaining = currentHearts
            )

            // Show completion dialog after a brief delay
            delay(1000)
            showCompletionDialog = true
        }
    }

    // Handle wrong feedback animation
    LaunchedEffect(showWrongFeedback) {
        if (showWrongFeedback) {
            delay(1000)
            showWrongFeedback = false
        }
    }

    // Handle game over
    LaunchedEffect(currentHearts) {
        if (currentHearts <= 0) {
            gameOver = true
            recordAdaptivePerformance(
                isCorrect = false,
                timeSpent = System.currentTimeMillis() - startTime,
                streak = 0,
                livesRemaining = 0
            )
        }
    }

    // Fit-to-screen: HUD on top, puzzle text taking the remaining space (cells are sized to fit),
    // letter keyboard pinned at the bottom. Landscape/wide: puzzle left, keyboard right.
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val wide = maxWidth > maxHeight
        val compact = wide || maxHeight < 720.dp

        val hud: @Composable () -> Unit = {
            if (compact) {
                CryptoCompactHud(
                    timer = timer,
                    hearts = currentHearts,
                    onBack = onBack,
                    onHint = {
                        showHint = !showHint
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    }
                )
            } else {
                Column(modifier = Modifier.padding(top = 24.dp)) {
                    AdaptiveUnifiedHeader(
                        level = currentLevel,
                        streakInfo = streakInfo,
                        timer = timer,
                        lives = currentHearts,
                        currentDifficulty = currentDifficultyLevel,
                        score = finalScore,
                        puzzleType = "cryptoPuzzle",
                        competitiveInsight = competitiveInsight,
                        onBack = onBack,
                        onHint = {
                            showHint = !showHint
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        }
                    )
                }
            }
        }

        val status: @Composable () -> Unit = {
            UnifiedAdaptationNotification(
                adaptationInfo = adaptationInfo,
                puzzleType = "cryptoPuzzle",
                visible = showAdaptationNotification,
                onDismiss = { showAdaptationNotification = false }
            )

            if (showWrongFeedback) {
                Card(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFFFCDD2))
                ) {
                    Row(
                        modifier = Modifier.padding(8.dp).fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = "❌ Wrong letter! Try again.",
                            style = MaterialTheme.typography.titleMedium,
                            color = Color(0xFFB71C1C),
                            textAlign = TextAlign.Center,
                            maxLines = 2
                        )
                    }
                }
            }

            if (isComplete) {
                Card(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                ) {
                    Text(
                        text = "🎉 Puzzle Solved! Score: $finalScore",
                        modifier = Modifier.padding(8.dp).fillMaxWidth(),
                        style = MaterialTheme.typography.titleMedium,
                        textAlign = TextAlign.Center,
                        maxLines = 2
                    )
                }
            }
        }

        val puzzle: @Composable (Modifier) -> Unit = { m ->
            Box(modifier = m.padding(horizontal = 8.dp, vertical = 4.dp).testTag("crypto_puzzle")) {
                AdaptiveCryptoPuzzleDisplay(
                    originalText = adaptivePuzzleData.originalText,
                    numberMapping = adaptivePuzzleData.numberMapping,
                    userMapping = userMapping,
                    selectedNumber = selectedNumber,
                    lockedPositions = currentlyLockedPositions,
                    onNumberSelected = { selectedNumber = if (selectedNumber == it) null else it },
                    cryptoLetters = adaptivePuzzleData.numberMapping.keys
                )
            }
        }

        val keyboard: @Composable (Modifier) -> Unit = { m ->
            AdaptiveLetterSelectionKeyboard(
                modifier = m.testTag("crypto_keyboard"),
                selectedNumber = selectedNumber,
                userMapping = userMapping,
                numberMapping = adaptivePuzzleData.numberMapping,
                adaptiveConfig = adaptiveConfig,
                onLetterSelected = { letter ->
                    selectedNumber?.let { number ->
                        totalAnswers++
                        if (placeLetter(number, letter)) {
                            // Correct answer
                            selectedNumber = null
                            currentStreak++
                            correctAnswers++
                        } else {
                            // Wrong answer
                            showWrongFeedback = true
                            currentHearts = maxOf(0, currentHearts - 1)
                            currentStreak = 0
                            selectedNumber = null

                            recordAdaptivePerformance(
                                isCorrect = false,
                                timeSpent = System.currentTimeMillis() - startTime,
                                streak = 0,
                                livesRemaining = currentHearts
                            )
                        }
                    }
                }
            )
        }

        if (wide) {
            Column(
                modifier = Modifier.align(Alignment.TopCenter).widthIn(max = 1100.dp).fillMaxSize()
            ) {
                hud()
                Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
                        status()
                        puzzle(Modifier.weight(1f).fillMaxWidth())
                    }
                    Box(
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                        contentAlignment = Alignment.BottomCenter
                    ) { keyboard(Modifier.widthIn(max = 520.dp)) }
                }
            }
        } else {
            Column(
                modifier = Modifier.align(Alignment.TopCenter).widthIn(max = 640.dp).fillMaxSize()
            ) {
                hud()
                status()
                puzzle(Modifier.weight(1f).fillMaxWidth())
                keyboard(Modifier)
            }
        }
    }

    // Hint dialog
    if (showHint && adaptivePuzzleData.targetWord != null) {
        AlertDialog(
            onDismissRequest = { showHint = false },
            confirmButton = {
                Button(onClick = { showHint = false }) {
                    Text(stringResource(R.string.ok))
                }
            },
            title = { Text(stringResource(R.string.hint)) },
            text = { Text("The message contains: ${adaptivePuzzleData.targetWord}") }
        )
    }

    // Completion dialog
    if (showCompletionDialog) {
        AdaptiveQuoteRevealDialog(
            originalText = adaptivePuzzleData.originalText,
            hintWithAuthor = adaptivePuzzleData.targetWord,
            finalScore = finalScore,
            adaptiveConfig = adaptiveConfig,
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
                        onComplete(false, 0)
                    }
                ) {
                    Text(stringResource(R.string.try_again))
                }
            },
            dismissButton = {
                Button(onClick = onBack) {
                    Text(stringResource(R.string.back))
                }
            },
            title = { Text(stringResource(R.string.game_over)) },
            text = {
                Text("You've lost all your hearts! Better luck next time.")
            }
        )
    }

    // ✅ NEW: Session completion handling
    if (isComplete || gameOver) {
        UnifiedSessionCompletionHandler(
            puzzleType = "cryptoPuzzle",
            sessionScore = finalScore,
            sessionStats = SessionStatistics(
                correctAnswers = correctAnswers,
                totalAnswers = totalAnswers,
                totalTimeSeconds = ((System.currentTimeMillis() - sessionStartTime) / 1000).toInt(),
                bestStreak = currentStreak,
                winRate = if (totalAnswers > 0) correctAnswers.toFloat() / totalAnswers else 0f,
                totalScore = finalScore, // Fixed: Use the final score from completion
                averageTimePerPuzzle = ((System.currentTimeMillis() - sessionStartTime) / 1000).toInt(), // Fixed: Time for this crypto puzzle
                currentStreak = currentStreak, // Fixed: Use the current streak value
                individualTimes = listOf(((System.currentTimeMillis() - startTime) / 1000).toInt()) // Fixed: List with puzzle completion time
            ),
            currentDifficulty = currentDifficultyLevel
        ) { result ->
            // Session completion handled
        }
    }
}

@Composable
private fun AdaptiveCryptoPuzzleHeader(
    level: UserLevel,
    streakInfo: StreakInfo,
    difficulty: String,
    timer: String,
    hearts: Int,
    adaptiveConfig: AdaptiveCryptoConfig,
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
        IconButton(onClick = onBack) {
            Icon(Icons.Default.ArrowBack, contentDescription = stringResource(R.string.back))
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Level ${level.level}",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = Color.Gray
            )
            Text(
                text = adaptiveConfig.name,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = RvSky
            )
            Text(
                text = timer,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
            if (!adaptiveConfig.autoRevealAllInstances || adaptiveConfig.lockedPositionsRatio > 0) {
                Text(
                    text = if (adaptiveConfig.autoRevealAllInstances) "🔒 Locked Letters" else "🎯 Single Reveal",
                    fontSize = 12.sp,
                    color = RvFlame
                )
            }
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row {
                repeat(hearts) {
                    Text(
                        text = "❤️",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
            if (streakInfo.currentStreak > 0) {
                Text(
                    text = "🔥 ${streakInfo.currentStreak}",
                    fontSize = 12.sp,
                    color = RvFlame
                )
            }
        }

        IconButton(onClick = onHint) {
            Icon(Icons.Default.Help, contentDescription = stringResource(R.string.hint))
        }
    }
}

@Composable
fun AdaptiveCryptoPuzzleDisplay(
    originalText: String,
    numberMapping: Map<Char, Int>,
    userMapping: Map<Int, Char>,
    selectedNumber: Int?,
    lockedPositions: Set<Int>,
    onNumberSelected: (Int) -> Unit,
    cryptoLetters: Set<Char> = numberMapping.keys
) {
    val words = originalText.split(" ").filter { it.isNotEmpty() }

    BoxWithConstraints(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        val availW = maxWidth
        val availH = if (constraints.hasBoundedHeight) maxHeight else 100000.dp

        // Pick the biggest cell size (36dp down to 20dp) whose word-wrapped rows fit the area.
        fun pack(cell: Dp): List<List<String>> {
            val letterW = cell + 4.dp
            val gap = cell / 2
            val rows = mutableListOf<List<String>>()
            var cur = mutableListOf<String>()
            var curW = 0.dp
            words.forEach { w ->
                val ww = letterW * w.count { it.isLetter() }
                if (cur.isEmpty()) { cur.add(w); curW = ww }
                else if (curW + gap + ww <= availW) { cur.add(w); curW += gap + ww }
                else { rows.add(cur.toList()); cur = mutableListOf(w); curW = ww }
            }
            if (cur.isNotEmpty()) rows.add(cur)
            return rows
        }
        fun rowsHeight(cell: Dp, rows: Int) = (cell * 2 + 6.dp) * rows + 12.dp * (rows - 1).coerceAtLeast(0)

        var cell = 36.dp
        var wordRows = pack(cell)
        while (cell > 20.dp && rowsHeight(cell, wordRows.size) > availH) {
            cell -= 2.dp
            wordRows = pack(cell)
        }
        val needsScroll = rowsHeight(cell, wordRows.size) > availH   // last-resort safety net only

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .then(if (needsScroll) Modifier.verticalScroll(rememberScrollState()) else Modifier),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            var currentPosition = 0
            wordRows.forEach { rowWords ->
                AdaptiveMultiWordRowDisplay(
                    words = rowWords,
                    numberMapping = numberMapping,
                    userMapping = userMapping,
                    selectedNumber = selectedNumber,
                    lockedPositions = lockedPositions,
                    startPosition = currentPosition,
                    onNumberSelected = onNumberSelected,
                    cryptoLetters = cryptoLetters,
                    cell = cell
                )
                currentPosition += rowWords.sumOf { it.length + 1 } // +1 for space
            }
        }
    }
}

@Composable
private fun CryptoCompactHud(
    timer: String,
    hearts: Int,
    onBack: () -> Unit,
    onHint: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
            Icon(Icons.Default.ArrowBack, contentDescription = stringResource(R.string.back), tint = RvInk)
        }
        Text(text = timer, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = RvInk, maxLines = 1)
        Spacer(Modifier.weight(1f))
        Row {
            repeat(hearts.coerceIn(0, 5)) { Text(text = "❤️", fontSize = 16.sp, maxLines = 1) }
        }
        IconButton(onClick = onHint, modifier = Modifier.size(48.dp)) {
            Icon(Icons.Default.Help, contentDescription = stringResource(R.string.hint), tint = RvInk)
        }
    }
}

@Composable
private fun AdaptiveMultiWordRowDisplay(
    words: List<String>,
    numberMapping: Map<Char, Int>,
    userMapping: Map<Int, Char>,
    selectedNumber: Int?,
    lockedPositions: Set<Int>,
    startPosition: Int,
    onNumberSelected: (Int) -> Unit,
    cryptoLetters: Set<Char>,
    cell: Dp,
    modifier: Modifier = Modifier
) {
    val fontScale = LocalDensity.current.fontScale
    // Sizes are set in dp-derived sp so a large system font scale cannot break the fit.
    val letterSp = (cell.value * 0.7f / fontScale).sp
    val numberSp = (cell.value * 0.45f / fontScale).sp
    val wordGap = cell / 2

    @Composable
    fun WordsRow(content: @Composable (word: String, startPos: Int) -> Unit) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            var pos = startPosition
            words.forEachIndexed { wordIndex, word ->
                if (wordIndex > 0) {
                    Spacer(modifier = Modifier.width(wordGap))
                    pos++ // Account for space
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) { content(word, pos) }
                pos += word.count { it.isLetter() }
            }
        }
    }

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        // Letters row
        WordsRow { word, startPos ->
            var currentPos = startPos
            word.forEach { char ->
                if (char.isLetter()) {
                    val upperChar = char.uppercaseChar()
                    val isCryptoLetter = cryptoLetters.contains(upperChar)
                    val isLocked = currentPos in lockedPositions

                    val displayText = when {
                        isLocked -> "🔒"
                        isCryptoLetter -> {
                            val number = numberMapping[upperChar]
                            number?.let { userMapping[it] }?.toString() ?: "_"
                        }
                        else -> upperChar.toString()
                    }

                    Text(
                        text = displayText,
                        fontSize = letterSp,
                        fontWeight = FontWeight.Bold,
                        color = when {
                            isLocked -> RvFlame
                            isCryptoLetter && userMapping.containsKey(numberMapping[upperChar]) -> GameSuccessText
                            else -> RvInk
                        },
                        modifier = Modifier.width(cell),
                        textAlign = TextAlign.Center,
                        maxLines = 1
                    )
                    currentPos++
                }
            }
        }

        // Numbers row
        WordsRow { word, startPos ->
            var currentPos = startPos
            word.forEach { char ->
                if (char.isLetter()) {
                    val upperChar = char.uppercaseChar()
                    val isCryptoLetter = cryptoLetters.contains(upperChar)
                    val isLocked = currentPos in lockedPositions

                    if (isCryptoLetter && !isLocked) {
                        val number = numberMapping[upperChar]
                        val selected = selectedNumber == number
                        Box(
                            modifier = Modifier
                                .size(cell)
                                .background(
                                    color = if (selected) GameSuccessText else MaterialTheme.colorScheme.surface,
                                    shape = RoundedCornerShape(6.dp)
                                )
                                .border(
                                    if (selected) 3.dp else 1.5.dp,
                                    if (selected) GameSuccessText else MaterialTheme.colorScheme.outline,
                                    RoundedCornerShape(6.dp)
                                )
                                .clickable { number?.let { onNumberSelected(it) } },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = number?.toString() ?: "",
                                fontSize = numberSp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                color = if (selected) Color.White else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    } else if (isLocked) {
                        Box(modifier = Modifier.size(cell), contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Default.Lock,
                                contentDescription = "Locked",
                                tint = RvFlame,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    } else {
                        Spacer(modifier = Modifier.size(cell))
                    }
                    currentPos++
                }
            }
        }
    }
}

@Composable
fun AdaptiveLetterSelectionKeyboard(
    selectedNumber: Int?,
    userMapping: Map<Int, Char>,
    numberMapping: Map<Char, Int>,
    adaptiveConfig: AdaptiveCryptoConfig,
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
            .padding(horizontal = 8.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = if (selectedNumber != null) "Choose a letter." else "Select a number first",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        if (!adaptiveConfig.autoRevealAllInstances) {
            Text(
                text = "🎯 Advanced mode: Only reveals single instances",
                fontSize = 12.sp,
                color = RvSkyEdge,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        letters.forEach { row ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally),
                modifier = Modifier.widthIn(max = 480.dp).fillMaxWidth().padding(vertical = 2.dp)
            ) {
                row.forEach { letter ->
                    val isUsed = if (adaptiveConfig.autoRevealAllInstances) {
                        userMapping.containsValue(letter)
                    } else {
                        // In single reveal mode, check if this specific mapping exists
                        selectedNumber?.let { userMapping[it] == letter } ?: false
                    }
                    val isCorrectMapping = numberMapping[letter] == selectedNumber

                    Button(
                        onClick = { onLetterSelected(letter) },
                        enabled = selectedNumber != null && (!isUsed || isCorrectMapping),
                        // 10 keys share the width (a fixed 32dp key overflowed 320-360dp phones).
                        modifier = Modifier.weight(1f).height(48.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = when {
                                !isUsed && selectedNumber != null -> RvSkyEdge
                                isUsed -> GameSuccessText
                                else -> Color(0xFF424242)
                            },
                            disabledContainerColor = Color(0xFF757575)
                        ),
                        contentPadding = PaddingValues(0.dp),
                        elevation = ButtonDefaults.buttonElevation(
                            defaultElevation = 2.dp,
                            pressedElevation = 8.dp
                        )
                    ) {
                        Text(
                            text = letter.toString(),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            color = Color.White
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AdaptiveQuoteRevealDialog(
    originalText: String,
    hintWithAuthor: String?,
    finalScore: Int,
    adaptiveConfig: AdaptiveCryptoConfig,
    onContinue: () -> Unit
) {
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
                    color = RvInk
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
                    text = "Adaptive Puzzle Complete!",
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
                Text(
                    text = adaptiveConfig.name,
                    style = MaterialTheme.typography.bodySmall,
                    color = RvSky,
                    modifier = Modifier.padding(top = 2.dp)
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
                                .background(RvDisabled)
                        )
                        Text(
                            text = "  $author  ",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Medium,
                            color = RvInkSoft,
                            fontStyle = FontStyle.Italic
                        )
                        Box(
                            modifier = Modifier
                                .width(30.dp)
                                .height(1.dp)
                                .background(RvDisabled)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

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

// Helper functions
fun generateAdaptiveCryptoConfig(difficulty: DifficultyManager.DifficultyLevel): AdaptiveCryptoConfig {
    return when (difficulty.index) {
        0 -> AdaptiveCryptoConfig( // Beginner
            autoRevealAllInstances = true,
            lockedPositionsRatio = 0f,
            dependencyChainLength = 0,
            hintRevealCount = 40, // 40% of letters revealed
            name = "Beginner",
            description = "All instances of letters revealed automatically"
        )
        1 -> AdaptiveCryptoConfig( // Easy
            autoRevealAllInstances = true,
            lockedPositionsRatio = 0.1f, // 10% of positions locked
            dependencyChainLength = 1,
            hintRevealCount = 30,
            name = "Easy",
            description = "Auto-reveal with some locked positions"
        )
        2 -> AdaptiveCryptoConfig( // Medium
            autoRevealAllInstances = false, // No auto-reveal!
            lockedPositionsRatio = 0.15f,
            dependencyChainLength = 2,
            hintRevealCount = 25,
            name = "Medium",
            description = "Single letter reveal mode"
        )
        3 -> AdaptiveCryptoConfig( // Hard
            autoRevealAllInstances = false,
            lockedPositionsRatio = 0.25f,
            dependencyChainLength = 3,
            hintRevealCount = 15,
            name = "Hard",
            description = "Single reveal with dependency chains"
        )
        4 -> AdaptiveCryptoConfig( // Expert
            autoRevealAllInstances = false,
            lockedPositionsRatio = 0.35f,
            dependencyChainLength = 4,
            hintRevealCount = 10,
            name = "Expert",
            description = "Maximum challenge with complex dependencies"
        )
        else -> AdaptiveCryptoConfig(
            autoRevealAllInstances = false,
            lockedPositionsRatio = 0.15f,
            dependencyChainLength = 2,
            hintRevealCount = 25,
            name = "Medium",
            description = "Single letter reveal mode"
        )
    }
}

fun convertToAdaptivePuzzleData(
    originalData: CryptoPuzzleData,
    config: AdaptiveCryptoConfig
): AdaptiveCryptoPuzzleData {
    val positions = mutableListOf<Int>()
    var currentPos = 0

    // Map character positions
    originalData.originalText.forEach { char ->
        if (char.isLetter() && originalData.numberMapping.containsKey(char.uppercaseChar())) {
            positions.add(currentPos)
        }
        if (char.isLetter() || char.isWhitespace()) {
            currentPos++
        }
    }

    // Select positions to lock based on configuration
    val totalPositions = positions.size
    val numToLock = (totalPositions * config.lockedPositionsRatio).toInt()
    val lockedPositions = if (numToLock > 0) {
        positions.shuffled().take(numToLock).toSet()
    } else {
        emptySet()
    }

    // Create dependencies - each locked position depends on nearby revealed positions
    val positionDependencies = mutableMapOf<Int, Set<Int>>()
    lockedPositions.forEach { lockedPos ->
        val dependencies = positions.filter { pos ->
            pos != lockedPos &&
                    abs(pos - lockedPos) <= config.dependencyChainLength &&
                    pos !in lockedPositions
        }.take(config.dependencyChainLength).toSet()

        if (dependencies.isNotEmpty()) {
            positionDependencies[lockedPos] = dependencies
        }
    }

    return AdaptiveCryptoPuzzleData(
        originalText = originalData.originalText,
        numberMapping = originalData.numberMapping,
        revealedLetters = originalData.revealedLetters,
        targetWord = originalData.targetWord,
        lockedPositions = lockedPositions,
        positionDependencies = positionDependencies
    )
}

fun getCharacterAtPosition(text: String, position: Int): Char {
    var currentPos = 0
    text.forEach { char ->
        if (char.isLetter() || char.isWhitespace()) {
            if (currentPos == position) {
                return char.uppercaseChar()
            }
            currentPos++
        }
    }
    return ' '
}

fun calculateAdaptiveCryptoScore(
    timeSpent: Long,
    difficulty: DifficultyManager.DifficultyLevel,
    livesRemaining: Int,
    streak: Int,
    puzzleComplexity: Int,
    adaptiveConfig: AdaptiveCryptoConfig
): Int {
    val baseScore = difficulty.basePoints

    // Adaptive difficulty bonus
    val adaptiveMultiplier = when {
        !adaptiveConfig.autoRevealAllInstances -> 2.0f // Major bonus for single-reveal mode
        adaptiveConfig.lockedPositionsRatio > 0.2f -> 1.5f // Bonus for significant locking
        adaptiveConfig.lockedPositionsRatio > 0.1f -> 1.3f // Medium bonus
        else -> 1.0f
    }

    // Time bonus
    val timeBonus = max(0, (300 - timeSpent / 1000).toInt()) * 2

    // Lives bonus
    val livesBonus = livesRemaining * 50

    // Streak bonus
    val streakBonus = streak * 25

    // Complexity bonus
    val complexityBonus = puzzleComplexity * 10

    val finalScore = ((baseScore * adaptiveMultiplier).toInt() +
            timeBonus + livesBonus + streakBonus + complexityBonus)

    return max(finalScore, baseScore / 2)
}