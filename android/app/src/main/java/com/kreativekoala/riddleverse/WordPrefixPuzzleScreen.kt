package com.kreativekoala.riddleverse

import android.util.Log
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONObject
import kotlin.math.*

data class WordPrefixWord(
    val word: String,
    val length: Int,
    val points: Int,
    val rarity: String,
    val frequency: Double,
    val found: Boolean = false
)

data class WordPrefixGameData(
    val prefix: String,
    val allWords: List<WordPrefixWord>,
    val timeLimit: Int,
    val targets: Map<String, Int>, // bronze, silver, gold
    val totalWords: Int
)

data class WordPrefixValidationResult(
    val isValid: Boolean,
    val isInPuzzleSet: Boolean = false,
    val isInDictionary: Boolean = false,
    val suggestions: List<String> = emptyList(),
    val message: String = "",
    val wordData: WordPrefixWord? = null
)

@Composable
fun WordPrefixPuzzleScreen(
    difficulty: String = "Easy",
    timer: String = "1:30",
    hearts: Int = 3,
    level: String,
    puzzleData: String,
    prefix: String? = null,
    onSubmitAnswer: (Boolean) -> Unit,
    fetchNextPuzzle: (Int) -> Unit,
    onBack: () -> Unit = {}
) {
    val context = LocalContext.current

    // Dictionary integration state
    var wordValidator by remember { mutableStateOf<LocalWordValidator?>(null) }
    var isValidatorLoading by remember { mutableStateOf(true) }

    // Enhanced feedback state
    var currentValidation by remember { mutableStateOf<WordPrefixValidationResult?>(null) }
    var showSuggestions by remember { mutableStateOf(false) }
    var discoveredWords by remember { mutableStateOf(mutableSetOf<String>()) }
    var validButNotInPuzzle by remember { mutableStateOf(mutableSetOf<String>()) }

    // Original game state
    var totalScore by remember { mutableStateOf(0) }
    var wordsFoundCount by remember { mutableStateOf(0) }
    var currentHearts by remember { mutableStateOf(hearts) }
    var streakCount by remember { mutableStateOf(0) }
    var wrongAttempts by remember { mutableStateOf(0) }
    var gameData by remember { mutableStateOf<WordPrefixGameData?>(null) }
    var currentInput by remember { mutableStateOf("") }
    var foundWords by remember { mutableStateOf(mutableSetOf<String>()) }
    var currentLevel by remember { mutableStateOf("bronze") }
    var isCompleted by remember { mutableStateOf(false) }
    var showHint by remember { mutableStateOf(false) }
    var lastWordAnimation by remember { mutableStateOf(false) }
    var gameStartTime by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var showCompletionScreen by remember { mutableStateOf(false) }
    var showCompletionDialog by remember { mutableStateOf(false) }
    var userWantsToFinish by remember { mutableStateOf(false) }

    val totalTimeSeconds = remember(timer, difficulty) {
        when (difficulty.lowercase()) {
            "easy" -> 90
            "medium" -> 120
            "hard" -> 150
            "expert" -> 180
            else -> 90
        }
    }

    var timeRemaining by remember { mutableStateOf(totalTimeSeconds) }
    var displayTimer by remember { mutableStateOf(formatTime(totalTimeSeconds)) }

    fun formatTime(seconds: Int): String {
        return "${seconds / 60}:${String.format("%02d", seconds % 60)}"
    }

    val keyboardController = LocalSoftwareKeyboardController.current
    val haptics = LocalHapticFeedback.current
    val feedbackManager = rememberUnifiedFeedbackManager()
    val userLevel = feedbackManager.getCurrentLevel()
    val streakInfo = feedbackManager.getStreakInfo()
    val scope = rememberCoroutineScope()

    // Initialize word validator
    LaunchedEffect(context) {
        try {
            Log.d("WordPrefix", "Initializing dictionary validator...")
            wordValidator = LocalWordValidator.getInstance(context)
            isValidatorLoading = false
            Log.d("WordPrefix", "Dictionary validator initialized successfully!")
        } catch (e: Exception) {
            Log.e("WordPrefix", "Failed to initialize dictionary validator", e)
            isValidatorLoading = false
        }
    }

    // Enhanced word validation function
    suspend fun validateWordInput(input: String, prefix: String): WordPrefixValidationResult {
        if (input.trim().isEmpty()) {
            return WordPrefixValidationResult(false, message = "Enter some letters")
        }

        val fullWord = (prefix + input.trim()).lowercase()

        // Check if already found
        if (foundWords.contains(fullWord)) {
            return WordPrefixValidationResult(false, message = "Already found!")
        }

        // Check if in puzzle set first
        val puzzleWord = gameData?.allWords?.find { it.word.lowercase() == fullWord }
        if (puzzleWord != null) {
            return WordPrefixValidationResult(
                isValid = true,
                isInPuzzleSet = true,
                message = "Perfect!",
                wordData = puzzleWord
            )
        }

        // Check dictionary for valid but not-in-puzzle words
        wordValidator?.let { validator ->
            try {
                val isValidWord = validator.isValidWord(fullWord)
                if (isValidWord) {
                    return WordPrefixValidationResult(
                        isValid = true,
                        isInDictionary = true,
                        message = "Valid word, but not in this puzzle",
                        suggestions = emptyList()
                    )
                } else {
                    val suggestions = validator.getSuggestions(fullWord, 3)
                        .filter { it.startsWith(prefix, ignoreCase = true) }
                        .take(2)

                    return WordPrefixValidationResult(
                        isValid = false,
                        suggestions = suggestions,
                        message = if (suggestions.isNotEmpty()) "Not a word. Did you mean:" else "Not a valid word"
                    )
                }
            } catch (e: Exception) {
                Log.e("WordPrefix", "Error validating word: $fullWord", e)
            }
        }

        return WordPrefixValidationResult(false, message = "Not a valid word")
    }

    // Real-time validation as user types
    LaunchedEffect(currentInput, gameData) {
        if (currentInput.isNotEmpty() && gameData != null) {
            delay(300) // Debounce
            currentValidation = validateWordInput(currentInput, gameData!!.prefix)
        } else {
            currentValidation = null
        }
    }

    fun completeGame() {
        if (isCompleted) return
        isCompleted = true
        val timeSpent = totalTimeSeconds - timeRemaining

        Log.d("WordPrefix", "Game completed! Score: $totalScore, Found: $wordsFoundCount, Discovered: ${discoveredWords.size}")

        val timeBonusScore = calculateTimeBonusScore(timeSpent, totalTimeSeconds, wordsFoundCount)
        val discoveryBonus = discoveredWords.size * 25
        totalScore += timeBonusScore + discoveryBonus

        Log.d("WordPrefix", "Final score: $totalScore (time bonus: $timeBonusScore, discovery bonus: $discoveryBonus)")
        showCompletionScreen = true
    }

    // Timer countdown
    LaunchedEffect(timeRemaining, userWantsToFinish) {
        if (timeRemaining > 0 && gameData != null && !isCompleted && !userWantsToFinish) {
            delay(1000L)
            timeRemaining -= 1
            displayTimer = formatTime(timeRemaining)

            if (timeRemaining == 30) {
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            }
        }

        if ((timeRemaining <= 0 || userWantsToFinish) && !isCompleted) {
            completeGame()
        }
    }

    fun calculateWordScore(word: WordPrefixWord, streak: Int, timeElapsed: Int, isDiscovery: Boolean = false): Int {
        val basePoints = word.points

        val difficultyMultiplier = when (difficulty.lowercase()) {
            "easy" -> 1.0f
            "medium" -> 1.2f
            "hard" -> 1.5f
            "expert" -> 1.8f
            else -> 1.2f
        }

        val streakMultiplier = when {
            streak >= 5 -> 1.5f
            streak >= 3 -> 1.3f
            streak >= 2 -> 1.1f
            else -> 1.0f
        }

        val rarityBonus = when (word.rarity.lowercase()) {
            "rare" -> basePoints * 0.5f
            "uncommon" -> basePoints * 0.3f
            else -> 0f
        }

        val timeEfficiencyBonus = when {
            timeElapsed <= 3 -> basePoints * 0.3f
            timeElapsed <= 5 -> basePoints * 0.2f
            timeElapsed <= 8 -> basePoints * 0.1f
            else -> 0f
        }

        val discoveryBonus = if (isDiscovery) basePoints * 0.8f else 0f

        val totalPoints = ((basePoints * difficultyMultiplier * streakMultiplier) +
                rarityBonus + timeEfficiencyBonus + discoveryBonus).toInt()

        return maxOf(totalPoints, basePoints)
    }

    fun calculateAchievementBonus(targetReached: String): Int {
        val baseBonus = when (difficulty.lowercase()) {
            "easy" -> 50
            "medium" -> 75
            "hard" -> 100
            "expert" -> 125
            else -> 75
        }

        return when (targetReached) {
            "bronze" -> baseBonus
            "silver" -> (baseBonus * 1.5f).toInt()
            "gold" -> baseBonus * 2
            else -> 0
        }
    }

    // Enhanced submit word function
    fun submitWord() {
        scope.launch {
            gameData?.let { data ->
                val validation = validateWordInput(currentInput, data.prefix)
                val fullWord = (data.prefix + currentInput.trim()).lowercase()
                val currentTime = System.currentTimeMillis()
                val wordFindTime = ((currentTime - gameStartTime) / 1000).toInt()

                when {
                    validation.isInPuzzleSet && validation.wordData != null -> {
                        val wordScore = calculateWordScore(validation.wordData, streakCount, wordFindTime % 10)

                        foundWords.add(fullWord)
                        wordsFoundCount++
                        streakCount++
                        totalScore += wordScore
                        wrongAttempts = 0

                        Log.d("WordPrefix", "Puzzle word found: $fullWord, Score: +$wordScore")

                        currentInput = ""
                        currentValidation = null
                        lastWordAnimation = true
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)

                        scope.launch {
                            delay(500)
                            lastWordAnimation = false
                        }
                    }

                    validation.isInDictionary -> {
                        if (!validButNotInPuzzle.contains(fullWord)) {
                            validButNotInPuzzle.add(fullWord)
                            discoveredWords.add(fullWord)

                            val tempWord = WordPrefixWord(
                                word = fullWord,
                                length = fullWord.length,
                                points = fullWord.length * 5,
                                rarity = "common",
                                frequency = 100.0
                            )

                            val discoveryScore = calculateWordScore(tempWord, streakCount, wordFindTime % 10, true)
                            totalScore += discoveryScore
                            streakCount++

                            Log.d("WordPrefix", "Dictionary word discovered: $fullWord, Score: +$discoveryScore")
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        } else {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        }

                        currentInput = ""
                        currentValidation = null
                    }

                    else -> {
                        streakCount = 0
                        wrongAttempts++

                        if (wrongAttempts >= 3) {
                            currentHearts = maxOf(0, currentHearts - 1)
                            wrongAttempts = 0

                            if (currentHearts == 0) {
                                completeGame()
                                return@launch
                            }
                        }

                        Log.d("WordPrefix", "Invalid word: $fullWord")

                        if (validation.suggestions.isNotEmpty()) {
                            showSuggestions = true
                            scope.launch {
                                delay(2000)
                                showSuggestions = false
                            }
                        }

                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        currentInput = ""
                        currentValidation = null
                    }
                }
            }
        }
    }

    // Parse puzzle data
    LaunchedEffect(puzzleData, prefix) {
        try {
            if (prefix != null) {
                val answerData = JSONObject(puzzleData)
                val timeLimit = answerData.getInt("timeLimit")
                val allWordsArray = answerData.getJSONArray("allWords")
                val targetsJson = answerData.getJSONObject("targets")
                val metadataJson = answerData.getJSONObject("metadata")
                val totalWords = metadataJson.getInt("totalWords")

                val words = mutableListOf<WordPrefixWord>()
                for (i in 0 until allWordsArray.length()) {
                    val wordObj = allWordsArray.getJSONObject(i)
                    words.add(
                        WordPrefixWord(
                            word = wordObj.getString("word"),
                            length = wordObj.getInt("length"),
                            points = wordObj.getInt("points"),
                            rarity = wordObj.getString("rarity"),
                            frequency = wordObj.getDouble("frequency")
                        )
                    )
                }

                val targets = mapOf(
                    "bronze" to targetsJson.getInt("bronze"),
                    "silver" to targetsJson.getInt("silver"),
                    "gold" to targetsJson.getInt("gold")
                )

                gameData = WordPrefixGameData(
                    prefix = prefix,
                    allWords = words,
                    timeLimit = timeLimit,
                    targets = targets,
                    totalWords = totalWords
                )
                return@LaunchedEffect
            }

            val puzzleJson = JSONObject(puzzleData)
            val questionData = JSONObject(puzzleJson.getString("question"))
            val extractedPrefix = questionData.getString("question")
            val answerData = JSONObject(puzzleJson.getString("answer"))

            val timeLimit = answerData.getInt("timeLimit")
            val allWordsArray = answerData.getJSONArray("allWords")
            val targetsJson = answerData.getJSONObject("targets")
            val metadataJson = answerData.getJSONObject("metadata")
            val totalWords = metadataJson.getInt("totalWords")

            val words = mutableListOf<WordPrefixWord>()
            for (i in 0 until allWordsArray.length()) {
                val wordObj = allWordsArray.getJSONObject(i)
                words.add(
                    WordPrefixWord(
                        word = wordObj.getString("word"),
                        length = wordObj.getInt("length"),
                        points = wordObj.getInt("points"),
                        rarity = wordObj.getString("rarity"),
                        frequency = wordObj.getDouble("frequency")
                    )
                )
            }

            val targets = mapOf(
                "bronze" to targetsJson.getInt("bronze"),
                "silver" to targetsJson.getInt("silver"),
                "gold" to targetsJson.getInt("gold")
            )

            gameData = WordPrefixGameData(
                prefix = extractedPrefix,
                allWords = words,
                timeLimit = timeLimit,
                targets = targets,
                totalWords = totalWords
            )
        } catch (e: Exception) {
            Log.e("WordPrefix", "Error parsing puzzle data", e)
        }
    }

    // Level progression check
    LaunchedEffect(foundWords.size) {
        gameData?.let { data ->
            val previousLevel = currentLevel
            val newLevel = when {
                foundWords.size >= data.targets["gold"]!! -> "gold"
                foundWords.size >= data.targets["silver"]!! -> "silver"
                foundWords.size >= data.targets["bronze"]!! -> "bronze"
                else -> "none"
            }

            if (newLevel != previousLevel && newLevel != "none") {
                currentLevel = newLevel
                val achievementBonus = calculateAchievementBonus(newLevel)
                totalScore += achievementBonus

                Log.d("WordPrefix", "Achievement: $newLevel! Bonus: $achievementBonus")
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            }

            if (newLevel == "gold" && !showCompletionDialog) {
                showCompletionDialog = true
            }
        }
    }

    fun calculateTimeBonusScore(timeSpent: Int, totalTime: Int, wordsFound: Int): Int {
        if (wordsFound == 0) return 0
        val timeRatio = timeSpent.toFloat() / totalTime
        val baseBonus = wordsFound * 5

        return when {
            timeRatio <= 0.5f -> baseBonus * 2
            timeRatio <= 0.7f -> (baseBonus * 1.5f).toInt()
            timeRatio <= 0.9f -> baseBonus
            else -> (baseBonus * 0.5f).toInt()
        }
    }

    // Show loading if dictionary is initializing
    if (isValidatorLoading) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color(0xFF6A5ACD), Color(0xFF483D8B))
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator(color = Color.White)
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Loading dictionary...",
                    color = Color.White,
                    fontSize = 16.sp
                )
            }
        }
        return
    }

    // Main UI
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF6A5ACD),
                        Color(0xFF483D8B),
                        Color(0xFF2E8B57)
                    )
                )
            )
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            Spacer(modifier = Modifier.height(24.dp))

            WordPrefixTopBar(
                level = userLevel,
                streakInfo = streakInfo,
                timer = displayTimer,
                hearts = currentHearts,
                roundLevel = level,
                timeRemaining = timeRemaining,
                totalTime = totalTimeSeconds,
                onBack = onBack,
                onHint = { showHint = true },
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )

            gameData?.let { data ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp)
                ) {
                    Spacer(modifier = Modifier.height(8.dp))

                    EnhancedStatsRow(
                        foundWords = foundWords.size,
                        totalWords = data.totalWords,
                        totalScore = totalScore,
                        streakCount = streakCount,
                        discoveredWords = discoveredWords.size,
                        onFinish = { userWantsToFinish = true }
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    EnhancedWordInputDisplay(
                        prefix = data.prefix,
                        currentInput = currentInput,
                        validation = currentValidation,
                        onSubmit = { submitWord() },
                        modifier = Modifier.fillMaxWidth()
                    )

                    if (showSuggestions && currentValidation?.suggestions?.isNotEmpty() == true) {
                        Spacer(modifier = Modifier.height(8.dp))

                        SuggestionsRow(
                            suggestions = currentValidation!!.suggestions,
                            prefix = data.prefix,
                            onSuggestionClick = { suggestion ->
                                currentInput = suggestion.removePrefix(data.prefix)
                                showSuggestions = false
                            }
                        )
                    }

                    WordTreeProgress(
                        foundWordsCount = foundWords.size,
                        targets = data.targets,
                        currentLevel = currentLevel,
                        lastWordAnimation = lastWordAnimation,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(120.dp)
                    )

                    if (foundWords.isNotEmpty() || discoveredWords.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(12.dp))

                        EnhancedFoundWordsSection(
                            puzzleWords = data.allWords.filter { foundWords.contains(it.word.lowercase()) },
                            discoveryWords = discoveredWords.toList(),
                            streakCount = streakCount
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        EnhancedStatsSection(
                            foundWords = foundWords.size,
                            totalWords = data.totalWords,
                            score = totalScore,
                            currentLevel = currentLevel,
                            targets = data.targets,
                            streak = streakCount,
                            discoveries = discoveredWords.size,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                }

                CustomAlphabetKeyboard(
                    onKeyPress = { letter ->
                        if (currentInput.length < 15) {
                            currentInput += letter
                        }
                    },
                    onBackspace = {
                        if (currentInput.isNotEmpty()) {
                            currentInput = currentInput.dropLast(1)
                        }
                    },
                    onSubmit = { submitWord() },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        EnhancedUniversalFeedback(feedbackManager)

        if (showCompletionDialog && !isCompleted) {
            AlertDialog(
                onDismissRequest = { showCompletionDialog = false },
                confirmButton = {
                    TextButton(
                        onClick = {
                            userWantsToFinish = true
                            showCompletionDialog = false
                        }
                    ) {
                        Text("Finish Now", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = { showCompletionDialog = false }
                    ) {
                        Text("Keep Playing", color = Color.White)
                    }
                },
                title = {
                    Text("Gold Level Reached!", color = Color.White, fontWeight = FontWeight.Bold)
                },
                text = {
                    Column {
                        Text(
                            "Congratulations! You've reached the Gold level!",
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "Current Score: $totalScore",
                            color = Color.Yellow,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "You can continue playing to find more words, or finish now with your current score.",
                            color = Color.White.copy(alpha = 0.9f),
                            fontSize = 14.sp
                        )
                    }
                },
                containerColor = Color(0xFF483D8B)
            )
        }

        if (showCompletionScreen) {
            WordPrefixCompletionScreen(
                gameData = gameData!!,
                foundWords = foundWords,
                discoveredWords = discoveredWords,
                totalScore = totalScore,
                wordsFoundCount = wordsFoundCount,
                currentLevel = currentLevel,
                difficulty = difficulty,
                timeSpent = totalTimeSeconds - timeRemaining,
                totalTime = totalTimeSeconds,
                onContinue = {
                    showCompletionScreen = false
                    if (foundWords.isEmpty()) {
                        fetchNextPuzzle(0)
                        onSubmitAnswer(false)
                    } else {
                        fetchNextPuzzle(totalScore)
                        onSubmitAnswer(true)
                    }
                }
            )
        }

        if (showHint) {
            AlertDialog(
                onDismissRequest = { showHint = false },
                confirmButton = {
                    TextButton(onClick = { showHint = false }) {
                        Text("Got it!", color = Color.White)
                    }
                },
                title = {
                    Text("How to Play", color = Color.White, fontWeight = FontWeight.Bold)
                },
                text = {
                    Column {
                        Text(
                            "Find words starting with \"${gameData?.prefix?.uppercase()}\"\n",
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "Targets:\nBronze: ${gameData?.targets?.get("bronze")} words\nSilver: ${gameData?.targets?.get("silver")} words\nGold: ${gameData?.targets?.get("gold")} words\n",
                            color = Color.White
                        )
                        Text(
                            "Scoring:\n• Longer words = more points\n• Rare words = bonus points\n• Word streaks = multiplier bonus\n• Dictionary discoveries = extra bonus\n",
                            color = Color.White.copy(alpha = 0.9f),
                            fontSize = 12.sp
                        )
                        Text(
                            "Tips:\n• Green = puzzle words (full points)\n• Blue = dictionary words (bonus points)\n• Orange = suggestions for typos",
                            color = Color.Yellow,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                },
                containerColor = Color(0xFF483D8B)
            )
        }
    }
}

@Composable
fun EnhancedStatsRow(
    foundWords: Int,
    totalWords: Int,
    totalScore: Int,
    streakCount: Int,
    discoveredWords: Int,
    onFinish: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = Color.White.copy(alpha = 0.1f)
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "${foundWords}/${totalWords}",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "Score: $totalScore",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.Yellow
                )

                if (discoveredWords > 0) {
                    Text(
                        text = "+$discoveredWords bonus words",
                        fontSize = 10.sp,
                        color = Color.Cyan
                    )
                }
            }

            if (streakCount > 1) {
                Text(
                    text = "$streakCount",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFFF9800)
                )
            }

            Button(
                onClick = onFinish,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF4CAF50)
                ),
                modifier = Modifier.height(32.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
            ) {
                Text(
                    text = stringResource(R.string.im_done),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
        }
    }
}

@Composable
fun EnhancedWordInputDisplay(
    prefix: String,
    currentInput: String,
    validation: WordPrefixValidationResult?,
    onSubmit: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = Color.White.copy(alpha = 0.9f)
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = prefix.uppercase(),
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF483D8B)
                    )

                    Text(
                        text = currentInput.uppercase(),
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold,
                        color = when {
                            validation?.isInPuzzleSet == true -> Color(0xFF4CAF50)
                            validation?.isInDictionary == true -> Color(0xFF2196F3)
                            validation?.isValid == false && validation.suggestions.isNotEmpty() -> Color(0xFFFF9800)
                            validation?.isValid == false -> Color(0xFFE53935)
                            else -> Color(0xFF2E8B57)
                        }
                    )

                    if (currentInput.isEmpty()) {
                        Text(
                            text = "|",
                            fontSize = 28.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF2E8B57).copy(alpha = 0.7f)
                        )
                    }
                }

                IconButton(
                    onClick = onSubmit,
                    modifier = Modifier
                        .size(44.dp)
                        .background(
                            when {
                                validation?.isInPuzzleSet == true -> Color(0xFF4CAF50)
                                validation?.isInDictionary == true -> Color(0xFF2196F3)
                                else -> Color(0xFF00C851)
                            },
                            CircleShape
                        )
                ) {
                    Icon(
                        imageVector = Icons.Default.Send,
                        contentDescription = stringResource(R.string.submit),
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            if (validation != null && currentInput.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            when {
                                validation.isInPuzzleSet -> Color(0xFF4CAF50).copy(alpha = 0.1f)
                                validation.isInDictionary -> Color(0xFF2196F3).copy(alpha = 0.1f)
                                validation.suggestions.isNotEmpty() -> Color(0xFFFF9800).copy(alpha = 0.1f)
                                else -> Color(0xFFE53935).copy(alpha = 0.1f)
                            }
                        )
                        .padding(horizontal = 20.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = when {
                            validation.isInPuzzleSet -> Icons.Default.CheckCircle
                            validation.isInDictionary -> Icons.Default.Info
                            validation.suggestions.isNotEmpty() -> Icons.Default.Lightbulb
                            else -> Icons.Default.Error
                        },
                        contentDescription = null,
                        tint = when {
                            validation.isInPuzzleSet -> Color(0xFF4CAF50)
                            validation.isInDictionary -> Color(0xFF2196F3)
                            validation.suggestions.isNotEmpty() -> Color(0xFFFF9800)
                            else -> Color(0xFFE53935)
                        },
                        modifier = Modifier.size(16.dp)
                    )

                    Text(
                        text = validation.message,
                        fontSize = 12.sp,
                        color = Color(0xFF666666),
                        modifier = Modifier.weight(1f)
                    )

                    if (validation.wordData?.points != null) {
                        Text(
                            text = "+${validation.wordData.points}pts",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF4CAF50)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun SuggestionsRow(
    suggestions: List<String>,
    prefix: String,
    onSuggestionClick: (String) -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFFFF9800).copy(alpha = 0.1f)
        ),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = "Did you mean:",
                fontSize = 12.sp,
                color = Color.White.copy(alpha = 0.8f),
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(4.dp))

            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(suggestions) { suggestion ->
                    Button(
                        onClick = { onSuggestionClick(suggestion) },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFFF9800)
                        ),
                        shape = RoundedCornerShape(16.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = suggestion.uppercase(),
                            fontSize = 12.sp,
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun EnhancedFoundWordsSection(
    puzzleWords: List<WordPrefixWord>,
    discoveryWords: List<String>,
    streakCount: Int
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "Found Words (${puzzleWords.size + discoveryWords.size}):",
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (discoveryWords.isNotEmpty()) {
                Text(
                    text = "${discoveryWords.size} bonus",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color.Cyan
                )
            }

            if (streakCount > 0) {
                Text(
                    text = "Streak: $streakCount",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color(0xFFFF9800)
                )
            }
        }
    }

    Spacer(modifier = Modifier.height(6.dp))

    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        state = rememberLazyListState()
    ) {
        items(puzzleWords.reversed()) { word ->
            CompactFoundWordChip(word = word)
        }

        items(discoveryWords.reversed()) { word ->
            CompactDiscoveryWordChip(word = word)
        }

        item {
            Spacer(modifier = Modifier.width(8.dp))
        }
    }
}

@Composable
fun CompactDiscoveryWordChip(word: String) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = Color.Cyan.copy(alpha = 0.8f)
        ),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = word.uppercase(),
                    color = Color.White,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Text(
                text = "+${word.length * 5}",
                color = Color.White,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
fun EnhancedStatsSection(
    foundWords: Int,
    totalWords: Int,
    score: Int,
    currentLevel: String,
    targets: Map<String, Int>,
    streak: Int,
    discoveries: Int,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.1f)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            StatItem(
                label = "Found",
                value = "$foundWords/$totalWords",
                color = Color.White
            )

            StatItem(
                label = "Score",
                value = score.toString(),
                color = Color.Yellow
            )

            if (discoveries > 0) {
                StatItem(
                    label = "Bonus",
                    value = "$discoveries",
                    color = Color.Cyan
                )
            }

            if (streak > 0) {
                StatItem(
                    label = "Streak",
                    value = "$streak",
                    color = Color(0xFFFF9800)
                )
            }

            StatItem(
                label = "Next Target",
                value = when (currentLevel) {
                    "gold" -> "MAX!"
                    "silver" -> "${targets["gold"]}"
                    "bronze" -> "${targets["silver"]}"
                    else -> "${targets["bronze"]}"
                },
                color = when (currentLevel) {
                    "gold" -> Color.Yellow
                    "silver" -> Color(0xFFC0C0C0)
                    else -> Color(0xFFCD7F32)
                }
            )
        }
    }
}

@Composable
fun CompactFoundWordChip(word: WordPrefixWord) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = when (word.rarity) {
                "rare" -> Color(0xFFFF6B35)
                "uncommon" -> Color(0xFF4ECDC4)
                else -> Color(0xFF45B7D1)
            }
        ),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = word.word.uppercase(),
                color = Color.White,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold
            )

            Text(
                text = "+${word.points}",
                color = Color.White,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
fun CustomAlphabetKeyboard(
    onKeyPress: (String) -> Unit,
    onBackspace: () -> Unit,
    onSubmit: () -> Unit,
    modifier: Modifier = Modifier
) {
    val keyboardRows = listOf(
        listOf("Q", "W", "E", "R", "T", "Y", "U", "I", "O", "P"),
        listOf("A", "S", "D", "F", "G", "H", "J", "K", "L"),
        listOf("Z", "X", "C", "V", "B", "N", "M")
    )

    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = Color(0xFF2D1B69)),
        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            keyboardRows.take(2).forEach { row ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    row.forEach { letter ->
                        KeyboardKey(
                            text = letter,
                            onClick = { onKeyPress(letter.lowercase()) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                keyboardRows[2].forEach { letter ->
                    KeyboardKey(
                        text = letter,
                        onClick = { onKeyPress(letter.lowercase()) },
                        modifier = Modifier.weight(0.8f)
                    )
                }

                KeyboardKey(
                    text = "⌫",
                    onClick = onBackspace,
                    modifier = Modifier.weight(1.2f),
                    backgroundColor = Color(0xFFFF6B35)
                )

                KeyboardKey(
                    text = "ENTER",
                    onClick = onSubmit,
                    modifier = Modifier.weight(1.6f),
                    backgroundColor = Color(0xFF00C851),
                    fontSize = 12.sp
                )
            }
        }
    }
}

@Composable
fun WordPrefixTopBar(
    level: UserLevel,
    streakInfo: StreakInfo,
    timer: String,
    hearts: Int,
    roundLevel: String,
    timeRemaining: Int = 0,
    totalTime: Int = 120,
    onBack: () -> Unit = {},
    onHint: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.statusBarsPadding().fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier.size(40.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.ArrowBack,
                    contentDescription = stringResource(R.string.back),
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
            }

            IconButton(
                onClick = onHint,
                modifier = Modifier.size(40.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Lightbulb,
                    contentDescription = stringResource(R.string.hint),
                    tint = Color.Yellow,
                    modifier = Modifier.size(24.dp)
                )
            }

            Column {
                Text(
                    text = "Level ${level.level}",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                LevelProgressBar(
                    level = level,
                    modifier = Modifier.width(80.dp)
                )
            }
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                repeat(5) { index ->
                    Icon(
                        imageVector = Icons.Default.Favorite,
                        contentDescription = "Heart",
                        tint = if (index < hearts) Color.Red else Color.White.copy(alpha = 0.3f),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Text(
                text = timer,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = when {
                    timeRemaining <= 10 -> Color.Red
                    timeRemaining <= 30 -> Color(0xFFFF6B35)
                    timeRemaining.toFloat() / totalTime <= 0.25f -> Color.Yellow
                    else -> Color.White
                },
                modifier = Modifier.padding(top = 4.dp)
            )
        }

        Column(
            horizontalAlignment = Alignment.End
        ) {
            Text(
                text = roundLevel,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            if (streakInfo.currentStreak > 0) {
                StreakDisplay(
                    streakInfo = streakInfo,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
    }
}

@Composable
fun KeyboardKey(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    backgroundColor: Color = Color(0xFF6A4C93),
    fontSize: TextUnit = 16.sp
) {
    val haptics = LocalHapticFeedback.current

    Button(
        onClick = {
            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            onClick()
        },
        modifier = modifier.fillMaxHeight(),
        colors = ButtonDefaults.buttonColors(
            containerColor = backgroundColor
        ),
        shape = RoundedCornerShape(12.dp),
        contentPadding = PaddingValues(4.dp)
    ) {
        Text(
            text = text,
            fontSize = fontSize,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )
    }
}

@Composable
fun WordTreeProgress(
    foundWordsCount: Int,
    targets: Map<String, Int>,
    currentLevel: String,
    lastWordAnimation: Boolean,
    modifier: Modifier = Modifier
) {
    val bronzeTarget = targets["bronze"] ?: 19
    val silverTarget = targets["silver"] ?: 37
    val goldTarget = targets["gold"] ?: 56

    val treeScale by animateFloatAsState(
        targetValue = if (lastWordAnimation) 1.1f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "tree_scale"
    )

    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(120.dp)
                    .scale(treeScale),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .width(12.dp)
                        .height(40.dp)
                        .align(Alignment.BottomCenter)
                        .background(
                            Color(0xFF8B4513),
                            RoundedCornerShape(6.dp)
                        )
                )

                val crownSize = 80 + (foundWordsCount * 2).coerceAtMost(40)
                val crownColor = when (currentLevel) {
                    "gold" -> Color(0xFFFFD700)
                    "silver" -> Color(0xFFC0C0C0)
                    "bronze" -> Color(0xFFCD7F32)
                    else -> Color(0xFF228B22)
                }

                Box(
                    modifier = Modifier
                        .size(crownSize.dp)
                        .align(Alignment.TopCenter)
                        .background(
                            Brush.radialGradient(
                                colors = listOf(
                                    crownColor.copy(alpha = 0.8f),
                                    Color(0xFF228B22)
                                )
                            ),
                            CircleShape
                        )
                ) {
                    val positions = remember { generateTreePositions(goldTarget) }
                    positions.take(foundWordsCount).forEachIndexed { index, position ->
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .offset(
                                    x = (position.first * (crownSize - 12)).dp,
                                    y = (position.second * (crownSize - 12)).dp
                                )
                                .background(
                                    Color.Yellow,
                                    CircleShape
                                )
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "${foundWordsCount} words found",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )

            Text(
                text = when (currentLevel) {
                    "gold" -> "Gold Level!"
                    "silver" -> "Silver Level"
                    "bronze" -> "Bronze Level"
                    else -> "Keep finding words..."
                },
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = when (currentLevel) {
                    "gold" -> Color.Yellow
                    "silver" -> Color(0xFFC0C0C0)
                    "bronze" -> Color(0xFFCD7F32)
                    else -> Color.White.copy(alpha = 0.7f)
                }
            )
        }
    }
}

@Composable
fun StatItem(
    label: String,
    value: String,
    color: Color
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = value,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = color
        )
        Text(
            text = label,
            fontSize = 12.sp,
            color = Color.White.copy(alpha = 0.7f)
        )
    }
}

fun generateTreePositions(count: Int): List<Pair<Float, Float>> {
    val positions = mutableListOf<Pair<Float, Float>>()
    repeat(count) { i ->
        val angle = (i * 2 * Math.PI / count).toFloat()
        val radius = (0.2f + (i % 3) * 0.2f)
        val x = 0.5f + radius * cos(angle)
        val y = 0.5f + radius * sin(angle)
        positions.add(
            Pair(
                x.coerceIn(0.1f, 0.9f),
                y.coerceIn(0.1f, 0.9f)
            )
        )
    }
    return positions
}

@Composable
fun WordPrefixCompletionScreen(
    gameData: WordPrefixGameData,
    foundWords: Set<String>,
    discoveredWords: Set<String>,
    totalScore: Int,
    wordsFoundCount: Int,
    currentLevel: String,
    difficulty: String,
    timeSpent: Int,
    totalTime: Int,
    onContinue: () -> Unit
) {
    val sortedWords = gameData.allWords.sortedWith(compareByDescending<WordPrefixWord> { word ->
        foundWords.contains(word.word.lowercase())
    }.thenByDescending { it.points })

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.9f)),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.9f)
                .padding(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = Color(0xFF483D8B)
            ),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = when (currentLevel) {
                                "gold" -> "Gold Level!"
                                "silver" -> "Silver Level!"
                                "bronze" -> "Bronze Level!"
                                else -> "Good Try!"
                            },
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                            color = when (currentLevel) {
                                "gold" -> Color.Yellow
                                "silver" -> Color(0xFFC0C0C0)
                                "bronze" -> Color(0xFFCD7F32)
                                else -> Color.White
                            }
                        )

                        Text(
                            text = "Words starting with \"${gameData.prefix.uppercase()}\"",
                            fontSize = 16.sp,
                            color = Color.White.copy(alpha = 0.8f),
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }

                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = stringResource(R.string.final_score),
                            fontSize = 14.sp,
                            color = Color.White.copy(alpha = 0.8f)
                        )
                        Text(
                            text = totalScore.toString(),
                            fontSize = 28.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.Yellow
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = Color.White.copy(alpha = 0.1f)
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        StatItem(
                            label = stringResource(R.string.found),
                            value = "$wordsFoundCount/${gameData.totalWords}",
                            color = Color.White
                        )

                        StatItem(
                            label = "Bonus Words",
                            value = "${discoveredWords.size}",
                            color = Color.Cyan
                        )

                        StatItem(
                            label = stringResource(R.string.time_used),
                            value = "${timeSpent / 60}:${String.format("%02d", timeSpent % 60)}",
                            color = Color(0xFFFF9800)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "All ${gameData.totalWords} Words",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(12.dp)
                                    .background(Color(0xFF4CAF50), CircleShape)
                            )
                            Text(
                                text = "Found ($wordsFoundCount)",
                                fontSize = 12.sp,
                                color = Color.White.copy(alpha = 0.8f)
                            )
                        }

                        if (discoveredWords.isNotEmpty()) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(12.dp)
                                        .background(Color.Cyan, CircleShape)
                                )
                                Text(
                                    text = "Bonus (${discoveredWords.size})",
                                    fontSize = 12.sp,
                                    color = Color.White.copy(alpha = 0.8f)
                                )
                            }
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(12.dp)
                                    .background(Color.Gray.copy(alpha = 0.5f), CircleShape)
                            )
                            Text(
                                text = "Missed (${gameData.totalWords - wordsFoundCount})",
                                fontSize = 12.sp,
                                color = Color.White.copy(alpha = 0.8f)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val wordRows = sortedWords.chunked(2)

                    items(wordRows) { rowWords ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            rowWords.forEach { word ->
                                CompletionWordCard(
                                    word = word,
                                    isFound = foundWords.contains(word.word.lowercase()),
                                    modifier = Modifier.weight(1f)
                                )
                            }

                            if (rowWords.size == 1) {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }

                    if (discoveredWords.isNotEmpty()) {
                        item {
                            Text(
                                text = stringResource(R.string.bonus_words_found),
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.Cyan,
                                modifier = Modifier.padding(top = 16.dp, bottom = 8.dp)
                            )
                        }

                        val discoveryRows = discoveredWords.toList().chunked(3)
                        items(discoveryRows) { rowWords ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                rowWords.forEach { word ->
                                    CompletionDiscoveryWordCard(
                                        word = word,
                                        modifier = Modifier.weight(1f)
                                    )
                                }

                                // Fill remaining space
                                repeat(3 - rowWords.size) {
                                    Spacer(modifier = Modifier.weight(1f))
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = onContinue,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF4CAF50)
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = stringResource(R.string.next_puzzle),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }
        }
    }
}

@Composable
fun CompletionWordCard(
    word: WordPrefixWord,
    isFound: Boolean,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = if (isFound) {
                when (word.rarity.lowercase()) {
                    "rare" -> Color(0xFF4CAF50).copy(alpha = 0.9f)
                    "uncommon" -> Color(0xFF2196F3).copy(alpha = 0.9f)
                    else -> Color(0xFF4CAF50).copy(alpha = 0.7f)
                }
            } else {
                Color.Gray.copy(alpha = 0.3f)
            }
        ),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = word.word.uppercase(),
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = if (isFound) Color.White else Color.White.copy(alpha = 0.5f)
            )

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "+${word.points}",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isFound) Color.Yellow else Color.White.copy(alpha = 0.4f)
                )

                if (word.rarity.lowercase() != "common") {
                    Text(
                        text = when (word.rarity.lowercase()) {
                            "rare" -> "★"
                            "uncommon" -> "☆"
                            else -> ""
                        },
                        fontSize = 12.sp,
                        color = if (isFound) Color.White else Color.White.copy(alpha = 0.4f)
                    )
                }
            }

            Text(
                text = "${word.length} letters",
                fontSize = 10.sp,
                color = if (isFound) Color.White.copy(alpha = 0.8f) else Color.White.copy(alpha = 0.4f)
            )
        }
    }
}

@Composable
fun CompletionDiscoveryWordCard(
    word: String,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = Color.Cyan.copy(alpha = 0.8f)
        ),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = "★",
                    fontSize = 10.sp,
                    color = Color.White
                )
                Text(
                    text = word.uppercase(),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }

            Text(
                text = "+${word.length * 5}",
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        }
    }
}

// Helper functions
fun calculateTimeBonusScore(timeSpent: Int, totalTime: Int, wordsFound: Int): Int {
    if (wordsFound == 0) return 0

    val timeRatio = timeSpent.toFloat() / totalTime
    val baseBonus = wordsFound * 5

    return when {
        timeRatio <= 0.5f -> baseBonus * 2
        timeRatio <= 0.7f -> (baseBonus * 1.5f).toInt()
        timeRatio <= 0.9f -> baseBonus
        else -> (baseBonus * 0.5f).toInt()
    }
}