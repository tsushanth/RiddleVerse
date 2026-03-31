package com.kreativekoala.riddleverse

import android.util.Log
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import org.json.JSONArray

@Composable
fun PercentageScreenWrapper(
    currentPuzzle: Puzzle,
    viewModel: PuzzleViewModel,
    onBack: () -> Unit,
    handlePuzzleCompletion: (Boolean, Boolean, Int) -> Unit
) {
    val TAG = "PercentageWrapper"
    Log.d(TAG, "🎯 Using Adaptive Percentage puzzle system")

    // Use the adaptive percentage screen directly - it handles its own puzzle generation
    AdaptivePercentagePuzzleScreen(
        initialDifficulty = currentPuzzle.difficulty ?: "Medium",
        timer = currentPuzzle.timer ?: "1:30",
        onSubmitAnswer = { answer ->
            Log.d(TAG, "📝 Percentage answer submitted: $answer")
            // The adaptive screen handles correctness checking internally
            // We just need to acknowledge the submission
        },
        fetchNextPuzzle = { finalScore ->
            Log.d(TAG, "🔄 Adaptive puzzle completed with score: $finalScore")
            handlePuzzleCompletion(true, true, finalScore)
        },
        onBack = onBack
    )
}

@Composable
fun DiscountPriceScreenWrapper(
    currentPuzzle: Puzzle,
    viewModel: PuzzleViewModel,
    onBack: () -> Unit,
    handlePuzzleCompletion: (Boolean, Boolean, Int) -> Unit
) {
    val TAG = "DiscountWrapper"
    Log.d(TAG, "🎯 Generating Discount puzzle client-side")

    // Initialize the client-side generator
    val mathGenerator = remember { MathPuzzleGenerators() }

    // State for the generated puzzle data
    var discountPuzzle by remember { mutableStateOf<DiscountsPuzzle?>(null) }
    var isLoading by remember { mutableStateOf(true) }

    // Generate puzzle data on first composition
    LaunchedEffect(currentPuzzle.puzzleId) {
        try {
            val difficulty = currentPuzzle.difficulty ?: "Medium"
            val generatedPuzzle = mathGenerator.generateDiscounts(difficulty)

            if (generatedPuzzle != null) {
                discountPuzzle = generatedPuzzle
                Log.d(TAG, "✅ Generated discount puzzle with ${generatedPuzzle.items.size} items")
                generatedPuzzle.items.forEach { item ->
                    Log.d(TAG, "  ${item.name}: $${item.originalPrice} -> $${item.finalPrice} (${item.discountPercentage ?: 0}% off)")
                }
                Log.d(TAG, "  Correct order: ${generatedPuzzle.correctOrder}")
                isLoading = false
            } else {
                Log.e(TAG, "❌ Failed to generate discount puzzle")
                handlePuzzleCompletion(false, false, 0)
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error generating discount puzzle: ${e.message}")
            handlePuzzleCompletion(false, false, 0)
        }
    }

    // Show loading state while generating
    if (isLoading || discountPuzzle == null) {
        LoadingScreen(message = "Generating discount puzzle...")
        return
    }

    // Convert generated DiscountItem to PriceItem for screen compatibility
    val priceItems = discountPuzzle!!.items.map { discountItem ->
        PriceItem(
            id = discountItem.id,
            name = discountItem.name,
            icon = discountItem.icon,
            originalPrice = discountItem.originalPrice,
            discountPercentage = discountItem.discountPercentage,
            finalPrice = discountItem.finalPrice
        )
    }

    PriceOrderingPuzzleScreen(
        difficulty = currentPuzzle.difficulty ?: "Medium",
        timer = when (currentPuzzle.difficulty?.lowercase()) {
            "easy" -> "2:30"
            "medium" -> "2:00"
            "hard" -> "1:45"
            "expert" -> "1:30"
            else -> "2:00"
        },
        hearts = 3,
        round = "ROUND ${if (viewModel.isCustomPuzzleFlow) viewModel.currentQuestionNumber else viewModel.currentPuzzleNumber} of ${if (viewModel.isCustomPuzzleFlow) viewModel.totalQuestions else viewModel.targetPuzzleCount}",

        // Use generated data
        items = priceItems,
        expectedOrder = discountPuzzle!!.correctOrder,

        onSubmitAnswer = { selectedOrder ->
            Log.d(TAG, "📝 Discount order submitted: $selectedOrder")
            val isCorrect = selectedOrder == discountPuzzle!!.correctOrder
            Log.d(TAG, "🎯 Order result: correct=$isCorrect")
            Log.d(TAG, "📊 Expected: ${discountPuzzle!!.correctOrder}")
            Log.d(TAG, "📊 User answer: $selectedOrder")

            // Log the actual price comparison for debugging
            selectedOrder.forEachIndexed { index, itemId ->
                val item = priceItems.find { it.id == itemId }
                Log.d(TAG, "  ${index + 1}. ${item?.name}: $${item?.finalPrice}")
            }
        },
        fetchNextPuzzle = { finalScore ->
            Log.d(TAG, "🔄 Discount puzzle completed with score: $finalScore")
            handlePuzzleCompletion(true, true, finalScore)
        },
        onBack = onBack
    )
}

@Composable
fun SubscriptionScreenWrapper(
    currentPuzzle: Puzzle,
    viewModel: PuzzleViewModel,
    onBack: () -> Unit,
    handlePuzzleCompletion: (Boolean, Boolean, Int) -> Unit
) {
    val TAG = "SubscriptionWrapper"
    Log.d(TAG, "🎯 Using Adaptive Subscription puzzle system")

    // Use the adaptive subscription screen directly - it handles its own puzzle generation
    AdaptiveSubscriptionPuzzleScreen(
        initialDifficulty = currentPuzzle.difficulty ?: "Medium",
        timer = when (currentPuzzle.difficulty?.lowercase()) {
            "easy" -> "2:30"
            "medium" -> "2:00"
            "hard" -> "1:45"
            "expert" -> "1:30"
            else -> "2:00"
        },
        onSubmitAnswer = { answer ->
            Log.d(TAG, "📝 Subscription answer submitted: $answer")
            // The adaptive screen handles correctness checking internally
        },
        fetchNextPuzzle = { finalScore ->
            Log.d(TAG, "🔄 Adaptive subscription puzzle completed with score: $finalScore")
            handlePuzzleCompletion(true, true, finalScore)
        },
        onBack = onBack
    )
}

@Composable
fun ConversionScreenWrapper(
    currentPuzzle: Puzzle,
    viewModel: PuzzleViewModel,
    onBack: () -> Unit,
    handlePuzzleCompletion: (Boolean, Boolean, Int) -> Unit
) {
    val TAG = "ConversionWrapper"
    Log.d(TAG, "🎯 Using Adaptive Conversion puzzle system")

    // Use the adaptive conversion screen directly - it handles its own puzzle generation
    AdaptiveConversionPuzzleScreen(
        initialDifficulty = currentPuzzle.difficulty ?: "Medium",
        timer = when (currentPuzzle.difficulty?.lowercase()) {
            "easy" -> "2:00"
            "medium" -> "1:45"
            "hard" -> "1:30"
            "expert" -> "1:15"
            else -> "1:45"
        },
        onSubmitAnswer = { answer ->
            Log.d(TAG, "📝 Conversion answer submitted: $answer")
            // The adaptive screen handles correctness checking internally
        },
        fetchNextPuzzle = { finalScore ->
            Log.d(TAG, "🔄 Adaptive conversion puzzle completed with score: $finalScore")
            handlePuzzleCompletion(true, true, finalScore)
        },
        onBack = onBack
    )
}

@Composable
fun SubtractionScreenWrapper(
    currentPuzzle: Puzzle,
    viewModel: PuzzleViewModel,
    onBack: () -> Unit,
    handlePuzzleCompletion: (Boolean, Boolean, Int) -> Unit
) {
    val TAG = "SubtractionWrapper"
    Log.d(TAG, "🎯 Using Adaptive Subtraction puzzle system")

    // Use the adaptive subtraction screen directly - it handles its own puzzle generation
    AdaptiveSubtractionPuzzleScreen(
        initialDifficulty = currentPuzzle.difficulty ?: "Medium",
        timer = when (currentPuzzle.difficulty?.lowercase()) {
            "easy" -> "2:00"
            "medium" -> "1:30"
            "hard" -> "1:15"
            "expert" -> "1:00"
            else -> "1:30"
        },
        onSubmitAnswer = { answer ->
            Log.d(TAG, "📝 Subtraction answer submitted: $answer")
            // The adaptive screen handles correctness checking internally
        },
        fetchNextPuzzle = { finalScore ->
            Log.d(TAG, "🔄 Adaptive subtraction puzzle completed with score: $finalScore")
            handlePuzzleCompletion(true, true, finalScore)
        },
        onBack = onBack
    )
}

// Helper function
private fun generateSubscriptionOptionsFromCorrect(correctAnswer: Double): List<Int> {
    val options = mutableListOf<Int>()
    options.add(correctAnswer.toInt())

    val variations = listOf(
        (correctAnswer * 0.8).toInt(),
        (correctAnswer * 1.2).toInt(),
        (correctAnswer * 0.6).toInt()
    )

    variations.forEach { variation ->
        if (variation != correctAnswer.toInt() && !options.contains(variation) && variation > 0) {
            options.add(variation)
        }
    }

    return options.take(4).shuffled()
}