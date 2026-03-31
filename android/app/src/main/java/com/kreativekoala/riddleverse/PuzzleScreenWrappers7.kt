package com.kreativekoala.riddleverse

import android.util.Log
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.json.JSONObject

// We need to add the remaining puzzle screen wrappers that were in the original huge file

@Composable
fun MathComparisonScreenWrapper(
    currentPuzzle: Puzzle,
    viewModel: PuzzleViewModel,
    onBack: () -> Unit,
    handlePuzzleCompletion: (Boolean, Boolean, Int) -> Unit
) {
    Log.d("PuzzleScreen", "🔢 Showing AdaptiveMathComparisonPuzzleScreen")

    // For adaptive version, we don't need complex client-side generation
    // The adaptive system handles puzzle generation internally
    val difficulty = currentPuzzle.difficulty ?: "Medium"

    // Calculate timer based on difficulty for adaptive challenges
    val timerText = when (difficulty.lowercase()) {
        "easy" -> "4:00"     // More time for adaptive easy mode
        "medium" -> "3:30"   // Standard adaptive time
        "hard" -> "3:00"     // Challenging but manageable for adaptive hard
        else -> "3:30"
    }

    // Parse any existing puzzle data (optional - adaptive system will override)
    val parsedData = remember(currentPuzzle) {
        try {
            if (currentPuzzle.question.startsWith("{")) {
                val questionData = JSONObject(currentPuzzle.question)
                val timeLimit = questionData.optInt("timeLimit", 210000) // 3:30 default in ms

                // Convert milliseconds to MM:SS format if provided
                val customTimerText = "${timeLimit / 60000}:${String.format("%02d", (timeLimit % 60000) / 1000)}"

                mapOf(
                    "difficulty" to difficulty,
                    "timerText" to customTimerText,
                    "puzzleType" to "adaptive_math_comparison",
                    "success" to true
                )
            } else {
                // Default adaptive configuration
                mapOf(
                    "difficulty" to difficulty,
                    "timerText" to timerText,
                    "puzzleType" to "adaptive_math_comparison",
                    "success" to true
                )
            }
        } catch (e: Exception) {
            Log.e("PuzzleScreen", "❌ Failed to parse adaptive math comparison data, using defaults", e)
            mapOf(
                "difficulty" to difficulty,
                "timerText" to timerText,
                "puzzleType" to "adaptive_math_comparison",
                "success" to true
            )
        }
    }

    val isValidData = parsedData["success"] as? Boolean ?: false

    if (isValidData) {
        val finalDifficulty = parsedData["difficulty"] as String
        val finalTimerText = parsedData["timerText"] as String

        Log.d("PuzzleScreen", "🎯 Starting adaptive math comparison with difficulty: $finalDifficulty, timer: $finalTimerText")

        // Use the adaptive version - note the different parameter signature
        AdaptiveMathComparisonPuzzleScreen(
            initialDifficulty = finalDifficulty,
            timer = finalTimerText,
            hearts = 3,
            level = "${if (viewModel.isCustomPuzzleFlow) viewModel.currentQuestionNumber else viewModel.currentPuzzleNumber}/${if (viewModel.isCustomPuzzleFlow) viewModel.totalQuestions else viewModel.targetPuzzleCount}",
            onSubmitAnswer = { isCorrect ->
                Log.d("PuzzleScreen", "📝 Adaptive math comparison result submitted: $isCorrect")
            },
            fetchNextPuzzle = { finalScore ->
                Log.d("PuzzleScreen", "🔄 Fetching next puzzle after adaptive math comparison completion")
                handlePuzzleCompletion(true, true, finalScore)
            },
            onBack = onBack
        )
    } else {
        LaunchedEffect(Unit) {
            Log.e("PuzzleScreen", "❌ Invalid adaptive math comparison puzzle data, moving to next puzzle")
            handlePuzzleCompletion(false, false, 0)
        }

        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator()
                Spacer(modifier = Modifier.height(16.dp))
                Text("Loading adaptive math comparison puzzle...")
            }
        }
    }
}

@Composable
fun WordSearchScreenWrapper(
    currentPuzzle: Puzzle,
    viewModel: PuzzleViewModel,
    onBack: () -> Unit,
    handlePuzzleCompletion: (Boolean, Boolean, Int) -> Unit
) {
    Log.d("PuzzleScreen", "🔍 Showing WordSearchPuzzleScreen")

    val parsedData = remember(currentPuzzle) {
        try {
            val questionString = currentPuzzle.question.trim()

            val wordSearchData = if (questionString.startsWith("{\"question\":")) {
                Log.d("PuzzleScreen", "📊 Detected double-nested JSON format")
                val outerJson = JSONObject(questionString)
                val innerQuestionString = outerJson.getString("question")
                JSONObject(innerQuestionString)
            } else if (questionString.startsWith("{\"matrix\":") ||
                questionString.startsWith("{") && questionString.contains("matrix")) {
                Log.d("PuzzleScreen", "📊 Detected direct word search JSON format")
                JSONObject(questionString)
            } else {
                Log.d("PuzzleScreen", "📊 Attempting direct JSON parse")
                JSONObject(questionString)
            }

            val matrix = wordSearchData.getJSONArray("matrix")
            val wordsArray = wordSearchData.getJSONArray("words")
            val width = wordSearchData.getInt("width")
            val height = wordSearchData.getInt("height")
            val instructions = wordSearchData.optString("instructions", "Find all hidden words")

            val words = mutableListOf<String>()
            for (i in 0 until wordsArray.length()) {
                val wordObj = wordsArray.getJSONObject(i)
                words.add(wordObj.getString("word"))
            }

            val difficulty = currentPuzzle.difficulty ?: "Easy"
            val wordCount = words.size
            val timerText = when (difficulty.lowercase()) {
                "easy" -> "${(wordCount * 30 + 60) / 60}:${String.format("%02d", (wordCount * 30 + 60) % 60)}"
                "medium" -> "${(wordCount * 25 + 45) / 60}:${String.format("%02d", (wordCount * 25 + 45) % 60)}"
                "hard" -> "${(wordCount * 20 + 30) / 60}:${String.format("%02d", (wordCount * 20 + 30) % 60)}"
                else -> "${(wordCount * 25 + 45) / 60}:${String.format("%02d", (wordCount * 25 + 45) % 60)}"
            }

            mapOf(
                "gridWidth" to width,
                "gridHeight" to height,
                "wordCount" to words.size,
                "words" to words,
                "difficulty" to difficulty,
                "timer" to timerText,
                "puzzleData" to wordSearchData.toString(),
                "success" to true
            )
        } catch (e: Exception) {
            Log.e("PuzzleScreen", "❌ Failed to parse word search puzzle data", e)
            mapOf("success" to false)
        }
    }

    val isValidData = parsedData["success"] as? Boolean ?: false

    if (isValidData) {
        val difficulty = parsedData["difficulty"] as String
        val timerText = parsedData["timer"] as String
        val puzzleData = parsedData["puzzleData"] as String

        WordSearchPuzzleScreen(
            difficulty = difficulty,
            timer = timerText,
            puzzleData = puzzleData,
            onSubmitAnswer = { isCorrect ->
                Log.d("PuzzleScreen", "📝 Word search completed: $isCorrect")
            },
            fetchNextPuzzle = { finalScore ->
                Log.d("PuzzleScreen", "🔄 Fetching next puzzle after word search completion")
                handlePuzzleCompletion(true, true, finalScore)
            },
            onBack = onBack
        )
    } else {
        LaunchedEffect(Unit) {
            Log.e("PuzzleScreen", "❌ Invalid word search puzzle data, moving to next puzzle")
            handlePuzzleCompletion(false, false, 0)
        }

        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator()
                Spacer(modifier = Modifier.height(16.dp))
                Text("Loading next puzzle...")
            }
        }
    }
}

@Composable
fun MathEstimationScreenWrapper(
    currentPuzzle: Puzzle,
    viewModel: PuzzleViewModel,
    onBack: () -> Unit,
    handlePuzzleCompletion: (Boolean, Boolean, Int) -> Unit
) {
    val TAG = "AdaptiveMathEstimationWrapper"
    Log.d(TAG, "🎯 Showing AdaptiveMathEstimationScreen")

    // For adaptive version, we don't need complex client-side generation
    // The adaptive system handles puzzle generation internally
    val difficulty = currentPuzzle.difficulty ?: "Medium"

    // Calculate timer based on difficulty for adaptive challenges
    val timerText = when (difficulty.lowercase()) {
        "easy" -> "3:00"     // More time for adaptive easy mode
        "medium" -> "2:30"   // Standard adaptive time
        "hard" -> "2:00"     // Challenging but manageable for adaptive hard
        else -> "2:30"
    }

    // Parse any existing puzzle data (optional - adaptive system will override)
    val parsedData = remember(currentPuzzle) {
        try {
            if (currentPuzzle.question.startsWith("{")) {
                val questionData = JSONObject(currentPuzzle.question)
                val timeLimit = questionData.optInt("timeLimit", 150) // 2:30 default in seconds

                // Convert seconds to MM:SS format if provided
                val customTimerText = "${timeLimit / 60}:${String.format("%02d", timeLimit % 60)}"

                mapOf(
                    "difficulty" to difficulty,
                    "timerText" to customTimerText,
                    "puzzleType" to "adaptive_math_estimation",
                    "success" to true
                )
            } else {
                // Default adaptive configuration
                mapOf(
                    "difficulty" to difficulty,
                    "timerText" to timerText,
                    "puzzleType" to "adaptive_math_estimation",
                    "success" to true
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to parse adaptive math estimation data, using defaults", e)
            mapOf(
                "difficulty" to difficulty,
                "timerText" to timerText,
                "puzzleType" to "adaptive_math_estimation",
                "success" to true
            )
        }
    }

    val isValidData = parsedData["success"] as? Boolean ?: false

    if (isValidData) {
        val finalDifficulty = parsedData["difficulty"] as String
        val finalTimerText = parsedData["timerText"] as String

        Log.d(TAG, "🎯 Starting adaptive math estimation with difficulty: $finalDifficulty, timer: $finalTimerText")

        // Use the adaptive version - note the different parameter signature
        AdaptiveMathEstimationScreen(
            initialDifficulty = finalDifficulty,
            timer = finalTimerText,
            onSubmitAnswer = { estimate ->
                Log.d(TAG, "🎯 Adaptive math estimation submitted: $estimate")
            },
            fetchNextPuzzle = { finalScore ->
                Log.d(TAG, "🎯 Moving to next puzzle after adaptive math estimation with score: $finalScore")
                handlePuzzleCompletion(true, true, finalScore)
            },
            onBack = onBack
        )
    } else {
        LaunchedEffect(Unit) {
            Log.e(TAG, "❌ Invalid adaptive math estimation puzzle data, moving to next puzzle")
            handlePuzzleCompletion(false, false, 0)
        }

        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator()
                Spacer(modifier = Modifier.height(16.dp))
                Text("Loading adaptive math estimation puzzle...")
            }
        }
    }
}

fun calculateChartBounds(numbers: List<Double>): Pair<Double, Double> {
    if (numbers.isEmpty()) return Pair(0.0, 100.0)

    // Calculate the actual sum
    val actualSum = numbers.sum()

    // Create estimation range around the sum (not the individual numbers!)
    // Users typically estimate within 50% to 150% of actual value
    val estimationMin = maxOf(0.0, actualSum * 0.5)
    val estimationMax = actualSum * 1.5

    // Round to nice scale numbers
    val roundedMin = when {
        estimationMax <= 20 -> 0.0
        estimationMax <= 50 -> (kotlin.math.floor(estimationMin / 10) * 10).coerceAtLeast(0.0)
        estimationMax <= 100 -> (kotlin.math.floor(estimationMin / 10) * 10).coerceAtLeast(0.0)
        estimationMax <= 500 -> (kotlin.math.floor(estimationMin / 25) * 25).coerceAtLeast(0.0)
        else -> (kotlin.math.floor(estimationMin / 50) * 50).coerceAtLeast(0.0)
    }

    val roundedMax = when {
        estimationMax <= 20 -> kotlin.math.ceil(estimationMax / 5) * 5
        estimationMax <= 50 -> kotlin.math.ceil(estimationMax / 10) * 10
        estimationMax <= 100 -> kotlin.math.ceil(estimationMax / 10) * 10
        estimationMax <= 500 -> kotlin.math.ceil(estimationMax / 25) * 25
        else -> kotlin.math.ceil(estimationMax / 50) * 50
    }

    Log.d("EstimationBounds", "Numbers: $numbers")
    Log.d("EstimationBounds", "Actual sum: $actualSum")
    Log.d("EstimationBounds", "Estimation range: $estimationMin to $estimationMax")
    Log.d("EstimationBounds", "Chart bounds: $roundedMin to $roundedMax")

    return Pair(roundedMin, roundedMax)
}

/**
 * Generate chart data points with improved positioning
 */
fun generateChartDataPoints(numbers: List<Double>, minValue: Double, maxValue: Double): List<ChartDataPoint> {
    // Individual numbers are displayed as reference only
    // They are NOT positioned on the sum estimation scale

    return numbers.mapIndexed { index, value ->
        ChartDataPoint(
            value = value,           // The individual number to display
            yPosition = 0.5f,        // Fixed position or arrange them nicely
            index = index
        )
    }
}

/**
 * Generate incorrect tip amounts for tip bubble puzzles
 */
fun generateIncorrectTipAmounts(correctAmount: Double, tipPercentage: Double, count: Int = 3): List<Double> {
    val variations = listOf(
        correctAmount * 0.5,        // 50% of correct
        correctAmount * 1.5,        // 150% of correct
        correctAmount * 0.75,       // 75% of correct
        correctAmount * 1.25,       // 125% of correct
        correctAmount + 5.0,        // Add $5
        correctAmount - 3.0,        // Subtract $3
        correctAmount * 2.0,        // Double
        correctAmount / 2.0,        // Half
        (tipPercentage + 5) / 100 * (correctAmount * 100 / tipPercentage), // +5% tip
        (tipPercentage - 5) / 100 * (correctAmount * 100 / tipPercentage)  // -5% tip
    )

    return variations.shuffled().take(count).map { it.coerceAtLeast(0.01) }
}

/**
 * Generate PriceItem list from DiscountItem list
 */
fun convertToPriceItems(discountItems: List<DiscountItem>): List<PriceItem> {
    return discountItems.map { item ->
        PriceItem(
            id = item.id,
            name = item.name,
            icon = item.icon,
            originalPrice = item.originalPrice,
            discountPercentage = item.discountPercentage,
            finalPrice = item.finalPrice,
            isSelected = false,
            selectionOrder = null
        )
    }
}

/**
 * Generate TipBubble list for tip calculation puzzles
 */
fun generateTipBubbles(
    correctTipAmount: Double,
    billAmount: Double,
    tipPercentage: Double,
    screenWidth: Float,
    screenHeight: Float
): List<TipBubble> {
    val bubbles = mutableListOf<TipBubble>()

    // Add correct bubble
    bubbles.add(
        TipBubble(
            tipAmount = correctTipAmount,
            billAmount = billAmount,
            isCorrect = true,
            positionX = 0f,
            positionY = screenHeight + 150f,
            speed = 3f
        )
    )

    // Add incorrect bubbles
    val incorrectAmounts = generateIncorrectTipAmounts(correctTipAmount, tipPercentage, 3)
    incorrectAmounts.forEach { incorrectAmount ->
        bubbles.add(
            TipBubble(
                tipAmount = incorrectAmount,
                billAmount = billAmount,
                isCorrect = false,
                positionX = 0f,
                positionY = screenHeight + 150f,
                speed = 3f
            )
        )
    }

    // Shuffle and position bubbles
    val shuffledBubbles = bubbles.shuffled()
    val bubbleSize = 120f
    val padding = 20f
    val availableWidth = screenWidth - (2 * padding) - (4 * bubbleSize)
    val spacingBetween = availableWidth / 3f

    return shuffledBubbles.mapIndexed { index, bubble ->
        val x = padding + index * (bubbleSize + spacingBetween)
        bubble.copy(
            positionX = x,
            positionY = screenHeight + 150f + (index * 300f)
        )
    }
}

/**
 * Generate value buttons for division puzzles
 */
fun generateValueButtons(maxAnswer: Int): List<ValueButton> {
    return when {
        maxAnswer <= 50 -> listOf(
            ValueButton(10, 0, 10),
            ValueButton(5, 0, 10),
            ValueButton(1, 0, 50)
        )
        maxAnswer <= 200 -> listOf(
            ValueButton(50, 0, 5),
            ValueButton(10, 0, 20),
            ValueButton(5, 0, 10),
            ValueButton(1, 0, 50)
        )
        maxAnswer <= 500 -> listOf(
            ValueButton(100, 0, 10),
            ValueButton(50, 0, 10),
            ValueButton(10, 0, 20),
            ValueButton(5, 0, 10),
            ValueButton(1, 0, 50)
        )
        else -> listOf(
            ValueButton(100, 0, 20),
            ValueButton(50, 0, 20),
            ValueButton(10, 0, 50),
            ValueButton(5, 0, 20),
            ValueButton(1, 0, 100)
        )
    }
}

/**
 * Validate puzzle generation parameters
 */
object PuzzleValidation {

    fun validateEstimationPuzzle(puzzle: MathEstimationPuzzle): Boolean {
        return puzzle.numbers.isNotEmpty() &&
                puzzle.numbers.size <= 6 &&
                puzzle.sum > 0 &&
                puzzle.numbers.all { it > 0 }
    }

    fun validateTippingPuzzle(puzzle: MathTippingPuzzle): Boolean {
        return puzzle.billAmount > 0 &&
                puzzle.tipPercentage > 0 &&
                puzzle.tipAmount > 0 &&
                puzzle.tipPercentage <= 50 // Reasonable tip range
    }

    fun validatePercentagePuzzle(puzzle: PercentagePuzzle): Boolean {
        return puzzle.total > 0 &&
                puzzle.percentage in 1..100 &&
                puzzle.answer >= 0
    }



    fun validateAveragePuzzle(puzzle: AveragePuzzle): Boolean {
        return puzzle.numbers.isNotEmpty() &&
                puzzle.numbers.size <= 6 &&
                puzzle.average > 0 &&
                puzzle.numbers.all { it > 0 }
    }


    fun validatePurchasingPuzzle(puzzle: PurchasingPuzzle): Boolean {
        val validFrequencies = listOf("weekly", "biweekly", "monthly", "quarterly", "yearly")
        return puzzle.payment > 0 &&
                puzzle.yearlyTotal > 0 &&
                validFrequencies.contains(puzzle.frequency)
    }

    fun validateDiscountsPuzzle(puzzle: DiscountsPuzzle): Boolean {
        return puzzle.items.isNotEmpty() &&
                puzzle.items.size >= 3 &&
                puzzle.correctOrder.size == puzzle.items.size &&
                puzzle.items.all { it.originalPrice > 0 && it.finalPrice > 0 && it.finalPrice <= it.originalPrice }
    }

    fun validateConversionPuzzle(puzzle: ConversionPuzzle): Boolean {
        return puzzle.value1 > 0 &&
                puzzle.value2 > 0 &&
                puzzle.unit1.isNotEmpty() &&
                puzzle.unit2.isNotEmpty() &&
                listOf("equal", "not equal").contains(puzzle.comparison)
    }
}

/**
 * Generate sample puzzle data for testing
 */
object SamplePuzzleData {

    fun getSampleEstimationPuzzle(): MathEstimationPuzzle {
        return MathEstimationPuzzle(
            numbers = listOf(25.0, 38.0, 42.0, 19.0),
            sum = 124.0,
            difficulty = "Medium",
            hint = "Round each number to make estimation easier"
        )
    }

    fun getSampleTippingPuzzle(): MathTippingPuzzle {
        return MathTippingPuzzle(
            billAmount = 45.75,
            tipPercentage = 18.0,
            tipAmount = 8.24,
            isCorrect = true,
            difficulty = "Medium",
            hint = "Calculate 18% tip on $45.75"
        )
    }

    fun getSamplePercentagePuzzle(): PercentagePuzzle {
        return PercentagePuzzle(
            total = 240,
            percentage = 15,
            answer = 36.0,
            difficulty = "Medium",
            hint = "What is 15% of 240?"
        )
    }


    fun getSampleAveragePuzzle(): AveragePuzzle {
        return AveragePuzzle(
            numbers = listOf(15, 23, 31, 19),
            average = 22.0,
            difficulty = "Medium",
            hint = "Add all numbers together and divide by how many numbers there are"
        )
    }
}


@Composable
fun TipBubbleScreenWrapper(
    currentPuzzle: Puzzle,
    viewModel: PuzzleViewModel,
    onBack: () -> Unit,
    handlePuzzleCompletion: (Boolean, Boolean, Int) -> Unit
) {
    val TAG = "TipBubbleWrapper"
    Log.d(TAG, "🎯 Using Adaptive Tip Bubble puzzle system")

    // Use the adaptive tip bubble screen directly - it handles its own puzzle generation
    TipBubblePuzzleScreen(
        initialDifficulty = currentPuzzle.difficulty ?: "Medium",
        timer = currentPuzzle.timer ?: "1:30",
        onSubmitAnswer = { isCorrect ->
            Log.d(TAG, "🎯 User submitted answer - correct: $isCorrect")
            // The adaptive screen handles correctness checking internally
        },
        fetchNextPuzzle = { finalScore ->
            Log.d(TAG, "🎯 Moving to next puzzle with score: $finalScore")
            handlePuzzleCompletion(true, true, finalScore)
        },
        onBack = onBack
    )
}

/**
 * Simple loading screen component
 */
@Composable
fun LoadingScreen(message: String = "Loading...") {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            CircularProgressIndicator()
            Spacer(modifier = Modifier.height(16.dp))
            Text(message)
        }
    }
}