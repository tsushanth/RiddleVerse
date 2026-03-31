package com.kreativekoala.riddleverse

import android.util.Log
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

data class PriceItem(
    val id: Int,
    val name: String,
    val icon: String, // Unicode emoji for the icon
    val originalPrice: Double,
    val discountPercentage: Int? = null, // null means no discount
    val finalPrice: Double, // Remove default calculation, make it explicit
    var isSelected: Boolean = false,
    var selectionOrder: Int? = null
) {
    // Helper function to get display text with discount info
    fun getDisplayText(): String {
        return if (discountPercentage != null && discountPercentage > 0) {
            "$icon $${String.format("%.2f", finalPrice)} (was $${String.format("%.2f", originalPrice)}, ${discountPercentage}% off)"
        } else {
            "$icon $${String.format("%.2f", finalPrice)}"
        }
    }

    // Helper function to calculate final price
    companion object {
        fun calculateFinalPrice(originalPrice: Double, discountPercentage: Int?): Double {
            return if (discountPercentage != null && discountPercentage > 0) {
                originalPrice * (1.0 - discountPercentage / 100.0)
            } else {
                originalPrice
            }
        }
    }
}

@Composable
fun PriceOrderingPuzzleScreen(
    difficulty: String = "Medium",
    timer: String = "2:00",
    hearts: Int = 4,
    round: String,
    items: List<PriceItem>,
    expectedOrder: List<Int>? = null,
    onSubmitAnswer: (List<Int>) -> Unit,
    fetchNextPuzzle: (Int) -> Unit,
    onBack: () -> Unit
) {
    // Score tracking state
    var score by remember { mutableStateOf(0) }
    var currentHearts by remember { mutableStateOf(hearts) }
    var attempts by remember { mutableStateOf(0) }

    // Existing state
    var showFeedback by remember { mutableStateOf(false) }
    var isCorrect by remember { mutableStateOf(false) }
    var gameItems by remember { mutableStateOf(items) }
    var selectedOrder by remember { mutableStateOf<List<Int>>(emptyList()) }
    var isGameComplete by remember { mutableStateOf(false) }
    val feedbackManager = rememberUnifiedFeedbackManager()
    val haptics = LocalHapticFeedback.current

    val currentLevel = feedbackManager.getCurrentLevel()
    val streakInfo = feedbackManager.getStreakInfo()

    // Timer calculation
    val totalTimeSeconds = remember(timer) {
        val parts = timer.split(":")
        if (parts.size == 2) {
            (parts[0].toIntOrNull() ?: 0) * 60 + (parts[1].toIntOrNull() ?: 0)
        } else {
            120 // Default 2 minutes
        }
    }

    var timeRemaining by remember { mutableStateOf(totalTimeSeconds) }

    // Timer countdown effect
    LaunchedEffect(timeRemaining, isGameComplete) {
        if (timeRemaining > 0 && !isGameComplete) {
            delay(1000L)
            timeRemaining--
        } else if (timeRemaining == 0) {
            // Time's up - complete with current score
            isGameComplete = true
            fetchNextPuzzle(score)
        }
    }

    // Use expected order from server if provided, otherwise calculate from final prices
    val correctOrder = expectedOrder ?: items.sortedBy { it.finalPrice }.map { it.id }

    // Calculate score based on performance
    fun calculateScore(isCorrectOrder: Boolean, timeSpent: Int, attemptCount: Int): Int {
        if (!isCorrectOrder) return 0

        // Base points by difficulty
        val basePoints = when (difficulty.lowercase()) {
            "easy" -> 50
            "medium" -> 75
            "hard" -> 100
            "expert" -> 125
            else -> 75
        }

        // Time bonus (faster completion = more points)
        val timeBonus = when {
            timeSpent <= totalTimeSeconds * 0.5 -> basePoints * 0.5 // 50% bonus for completing in half time
            timeSpent <= totalTimeSeconds * 0.7 -> basePoints * 0.3 // 30% bonus for completing in 70% time
            timeSpent <= totalTimeSeconds * 0.9 -> basePoints * 0.1 // 10% bonus for completing in 90% time
            else -> 0.0
        }.toInt()

        // Attempt penalty (first try = no penalty, each additional attempt reduces score)
        val attemptPenalty = when (attemptCount) {
            1 -> 0
            2 -> basePoints * 0.2 // 20% penalty for second attempt
            3 -> basePoints * 0.4 // 40% penalty for third attempt
            else -> basePoints * 0.6 // 60% penalty for 4+ attempts
        }.toInt()

        // Perfect order bonus (additional points for getting it right)
        val perfectBonus = basePoints * 0.2 // 20% bonus for correct order

        val totalScore = basePoints + timeBonus + perfectBonus.toInt() - attemptPenalty

        Log.d("PriceOrdering", "🏆 Score calculation:")
        Log.d("PriceOrdering", "  Base points: $basePoints")
        Log.d("PriceOrdering", "  Time bonus: $timeBonus (time spent: ${timeSpent}s)")
        Log.d("PriceOrdering", "  Perfect bonus: ${perfectBonus.toInt()}")
        Log.d("PriceOrdering", "  Attempt penalty: $attemptPenalty (attempt #$attemptCount)")
        Log.d("PriceOrdering", "  Total score: $totalScore")

        return maxOf(totalScore, 10) // Minimum 10 points for correct answer
    }

    // Reset state function
    fun resetState() {
        gameItems = items.map { it.copy(isSelected = false, selectionOrder = null) }
        selectedOrder = emptyList()
        isGameComplete = false
        // Don't reset score or hearts - keep them for cumulative tracking
    }

    // Handle item selection
    fun selectItem(itemId: Int) {
        if (isGameComplete) return

        haptics.performHapticFeedback(HapticFeedbackType.LongPress)

        val item = gameItems.find { it.id == itemId } ?: return

        if (item.isSelected) {
            // Deselect item and all items selected after it
            val orderToRemove = item.selectionOrder ?: return
            gameItems = gameItems.map { gameItem ->
                when {
                    gameItem.id == itemId -> gameItem.copy(isSelected = false, selectionOrder = null)
                    (gameItem.selectionOrder ?: 0) > orderToRemove -> gameItem.copy(isSelected = false, selectionOrder = null)
                    else -> gameItem
                }
            }
            selectedOrder = selectedOrder.filter { id ->
                val gameItem = gameItems.find { it.id == id }
                gameItem?.selectionOrder != null && gameItem.selectionOrder!! <= orderToRemove
            }.take(orderToRemove - 1)
        } else {
            // Select item
            val newOrder = selectedOrder.size + 1
            gameItems = gameItems.map { gameItem ->
                if (gameItem.id == itemId) {
                    gameItem.copy(isSelected = true, selectionOrder = newOrder)
                } else {
                    gameItem
                }
            }
            selectedOrder = selectedOrder + itemId
        }

        // Check if all items are selected
        if (selectedOrder.size == items.size) {
            isGameComplete = true
            attempts++
            val isCorrectOrder = selectedOrder == correctOrder
            val timeSpent = totalTimeSeconds - timeRemaining

            // Calculate score for this attempt
            val attemptScore = calculateScore(isCorrectOrder, timeSpent, attempts)

            if (isCorrectOrder) {
                score += attemptScore
                isCorrect = true
                Log.d("PriceOrdering", "✅ Correct order! Final score: $score")
            } else {
                currentHearts = maxOf(0, currentHearts - 1)
                isCorrect = false
                Log.d("PriceOrdering", "❌ Incorrect order. Hearts remaining: $currentHearts")

                // If no hearts left, end the game
                if (currentHearts == 0) {
                    Log.d("PriceOrdering", "💔 No hearts remaining. Game over with score: $score")
                    fetchNextPuzzle(score)
                    return
                }
            }

            // Create enhanced feedback strings with discount information
            val userAnswerText = selectedOrder.mapIndexed { index, id ->
                val item = items.find { it.id == id }
                "${index + 1}. ${item?.getDisplayText() ?: "Unknown"}"
            }.joinToString("\n")

            val correctAnswerText = correctOrder.mapIndexed { index, id ->
                val item = items.find { it.id == id }
                "${index + 1}. ${item?.getDisplayText() ?: "Unknown"}"
            }.joinToString("\n")

            Log.d("PriceOrdering", "📝 User answer:\n$userAnswerText")
            Log.d("PriceOrdering", "✅ Correct answer:\n$correctAnswerText")

            // Use universal feedback system with enhanced display
            feedbackManager.showFeedback(
                puzzleType = "discounts",
                isCorrect = isCorrectOrder,
                userAnswer = userAnswerText,
                correctAnswer = correctAnswerText,
                timeSpent = timeSpent * 1000L, // Convert to milliseconds
                difficulty = difficulty,
                timeRemaining = timeRemaining,
                totalTime = totalTimeSeconds,
                onComplete = {
                    if (isCorrectOrder) {
                        Log.d("PriceOrdering", "🎯 Puzzle completed successfully! Calling fetchNextPuzzle with score: $score")
                        fetchNextPuzzle(score) // Pass total cumulative score
                    } else {
                        Log.d("PriceOrdering", "🔄 Incorrect answer, allowing retry...")
                        resetState() // Allow retry
                    }
                }
            )

            onSubmitAnswer(selectedOrder)
        }
    }

    // Rest of the composable remains the same, but update the top bar to show current hearts
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF4A90E2),
                        Color(0xFF357ABD),
                        Color(0xFF7B68EE)
                    )
                )
            )
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // Top Bar - pass current hearts and formatted timer
            EnhancedDiscountTopGameBar(
                level = currentLevel,
                streakInfo = streakInfo,
                timer = String.format("%d:%02d", timeRemaining / 60, timeRemaining % 60),
                hearts = currentHearts, // Use current hearts instead of initial hearts
                onBack = onBack,
                modifier = Modifier.padding(16.dp)
            )

            // Instructions
            DiscountInstructionsCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 20.dp)
            )

            // Round indicator with score
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = round,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White.copy(alpha = 0.7f),
                    textAlign = TextAlign.Center
                )

                if (score > 0) {
                    Text(
                        text = "Score: $score",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color.White.copy(alpha = 0.9f),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }

                if (attempts > 0) {
                    Text(
                        text = "Attempt: $attempts",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Normal,
                        color = Color.White.copy(alpha = 0.6f),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
            }

            // Items grid
            ItemsGrid(
                items = gameItems,
                onItemClick = ::selectItem,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = 20.dp)
            )

            // Progress bar showing completion progress
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 40.dp, vertical = 30.dp)
            ) {
                Text(
                    text = "${selectedOrder.size}/${items.size} items selected",
                    fontSize = 12.sp,
                    color = Color.White.copy(alpha = 0.7f),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(8.dp))

                LinearProgressIndicator(
                    progress = selectedOrder.size.toFloat() / items.size.toFloat(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp),
                    color = Color.White,
                    trackColor = Color.White.copy(alpha = 0.3f)
                )
            }
        }

        EnhancedUniversalFeedback(feedbackManager)
    }
}

@Composable
fun EnhancedDiscountTopGameBar(
    level: UserLevel,
    streakInfo: StreakInfo,
    timer: String,
    hearts: Int,
    onBack: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.statusBarsPadding()) {
        // First row: Back button and main stats
        Row(
            modifier = Modifier
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            // Left side: Back button and level
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (onBack != null) {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.size(44.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = stringResource(R.string.back),
                            tint = Color.White,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                } else {
                    Icon(
                        imageVector = Icons.Default.Pause,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.7f),
                        modifier = Modifier.size(24.dp)
                    )
                }

                Column {
                    Text(
                        text = "Level ${level.level}",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )

                    // Level progress bar
                    LevelProgressBar(
                        level = level,
                        modifier = Modifier.width(120.dp)
                    )
                }
            }

            // Center: Hearts
            Row(
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                repeat(hearts) {
                    Icon(
                        imageVector = Icons.Default.Favorite,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            // Right side: Timer and streak
            Column(
                horizontalAlignment = Alignment.End
            ) {
                Text(
                    text = timer,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )

                // Streak display
                if (streakInfo.currentStreak > 0) {
                    StreakDisplay(
                        streakInfo = streakInfo,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun DiscountInstructionsCard(
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = Color.White.copy(alpha = 0.9f)
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Text(
            text = "TAP THE ITEMS IN ORDER FROM\nLEAST EXPENSIVE TO MOST\nEXPENSIVE.",
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF2C3E50),
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            textAlign = TextAlign.Center,
            lineHeight = 20.sp
        )
    }
}

@Composable
fun ItemsGrid(
    items: List<PriceItem>,
    onItemClick: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(20.dp),
        contentPadding = PaddingValues(vertical = 20.dp)
    ) {
        // Group items into rows of 2
        val chunkedItems = items.chunked(2)
        items(chunkedItems) { rowItems ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                rowItems.forEach { item ->
                    PriceItemCard(
                        item = item,
                        onClick = { onItemClick(item.id) },
                        modifier = Modifier.weight(1f)
                    )
                }

                // Fill remaining space if odd number of items
                if (rowItems.size == 1) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
fun PriceItemCard(
    item: PriceItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val cardColor = when {
        item.isSelected -> Color.White.copy(alpha = 0.3f)
        else -> Color.Transparent
    }

    val borderColor = when {
        item.isSelected -> Color.White
        else -> Color.White.copy(alpha = 0.3f)
    }

    Card(
        modifier = modifier
            .aspectRatio(0.8f)
            .clickable { onClick() },
        colors = CardDefaults.cardColors(
            containerColor = cardColor
        ),
        border = androidx.compose.foundation.BorderStroke(
            width = if (item.isSelected) 3.dp else 1.dp,
            color = borderColor
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Box(
            modifier = Modifier.fillMaxSize()
        ) {
            // Selection order indicator
            if (item.isSelected && item.selectionOrder != null) {
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .background(
                            Color.White,
                            CircleShape
                        )
                        .align(Alignment.TopEnd)
                        .offset((-8).dp, 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = item.selectionOrder.toString(),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF4A90E2),
                        textAlign = TextAlign.Center
                    )
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                // Icon
                Text(
                    text = item.icon,
                    fontSize = 40.sp,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                // Original price (always shown prominently)
                Text(
                    text = "${String.format("%.2f", item.originalPrice)}",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )

                // Discount tag if applicable
                if (item.discountPercentage != null && item.discountPercentage > 0) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = Color(0xFFFF6B35)
                        ),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "${item.discountPercentage}%",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Text(
                                text = "DISCOUNT",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                    }
                    }
                }
            }
        }
    }

@Composable
fun PriceOrderingPuzzlePreview() {
    PriceOrderingPuzzleScreen(
        difficulty = "Medium",
        timer = "2:00",
        hearts = 4,
        round = "ROUND 1 of 5",
        items = listOf(
            PriceItem(
                id = 1,
                name = "Bowl",
                icon = "🥣",
                originalPrice = 70.00,
                discountPercentage = 50,
                finalPrice = PriceItem.calculateFinalPrice(70.00, 50),
                isSelected = false,
                selectionOrder = null
            ),
            PriceItem(
                id = 2,
                name = "Soccer Ball",
                icon = "⚽",
                originalPrice = 36.00,
                discountPercentage = null,
                finalPrice = PriceItem.calculateFinalPrice(36.00, null),
                isSelected = false,
                selectionOrder = null
            ),
            PriceItem(
                id = 3,
                name = "Book",
                icon = "📚",
                originalPrice = 25.00,
                discountPercentage = null,
                finalPrice = PriceItem.calculateFinalPrice(25.00, null),
                isSelected = false,
                selectionOrder = null
            ),
            PriceItem(
                id = 4,
                name = "T-Shirt",
                icon = "👕",
                originalPrice = 60.00,
                discountPercentage = 20,
                finalPrice = PriceItem.calculateFinalPrice(60.00, 20),
                isSelected = false,
                selectionOrder = null
            )
        ),
        onSubmitAnswer = { order ->
            println("Selected order: $order")
        },
        fetchNextPuzzle = {
            println("Next puzzle")
        },
        onBack = {}
    )
}