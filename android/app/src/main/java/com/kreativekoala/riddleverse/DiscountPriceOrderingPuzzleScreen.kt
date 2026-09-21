package com.kreativekoala.riddleverse

import android.util.Log
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import com.kreativekoala.riddleverse.ui.theme.RvSun
import com.kreativekoala.riddleverse.ui.theme.RvSurface
import com.kreativekoala.riddleverse.ui.theme.RvViolet
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import com.kreativekoala.riddleverse.ui.theme.RvFlame
import com.kreativekoala.riddleverse.ui.theme.RvInk
import com.kreativekoala.riddleverse.ui.theme.RvInkSoft
import com.kreativekoala.riddleverse.ui.theme.RvOnTone
import com.kreativekoala.riddleverse.ui.theme.RvOutline
import com.kreativekoala.riddleverse.ui.theme.RvSurfaceRaised

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

    // Fit-to-screen: HUD fixed on top, item grid takes the remaining space, progress at the
    // bottom. No scrolling; landscape / wide uses two panes.
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(RvSurface)
    ) {
        val wide = maxWidth > maxHeight || maxWidth >= 600.dp
        val compact = maxHeight < 700.dp
        val pad = if (compact) 8.dp else 16.dp

        val hud: @Composable () -> Unit = {
            if (compact) {
                CompactDiscountHud(
                    timer = String.format("%d:%02d", timeRemaining / 60, timeRemaining % 60),
                    hearts = currentHearts,
                    level = currentLevel,
                    onBack = onBack
                )
            } else {
                EnhancedDiscountTopGameBar(
                    level = currentLevel,
                    streakInfo = streakInfo,
                    timer = String.format("%d:%02d", timeRemaining / 60, timeRemaining % 60),
                    hearts = currentHearts, // Use current hearts instead of initial hearts
                    onBack = onBack,
                    modifier = Modifier
                )
            }
        }
        val info: @Composable () -> Unit = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = round,
                    fontSize = if (compact) 18.sp else 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = RvInk,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                Text(
                    text = "Score: $score",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    color = RvInk,
                    maxLines = 1,
                    modifier = Modifier.testTag("hud_score")
                )
                if (attempts > 0) {
                    Text(
                        text = "Attempt: $attempts",
                        fontSize = 14.sp,
                        color = RvInkSoft,
                        maxLines = 1
                    )
                }
            }
        }
        val progress: @Composable () -> Unit = {
            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp)) {
                Text(
                    text = "${selectedOrder.size}/${items.size} items selected",
                    fontSize = 14.sp,
                    color = RvInkSoft,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(4.dp))
                LinearProgressIndicator(
                    progress = selectedOrder.size.toFloat() / items.size.toFloat(),
                    modifier = Modifier.fillMaxWidth().height(6.dp),
                    color = RvViolet,
                    trackColor = RvOutline
                )
            }
        }

        if (wide) {
            Row(
                modifier = Modifier.fillMaxSize().padding(pad),
                horizontalArrangement = Arrangement.spacedBy(pad)
            ) {
                Column(
                    modifier = Modifier.weight(0.8f).fillMaxHeight(),
                    verticalArrangement = Arrangement.spacedBy(pad)
                ) {
                    hud()
                    DiscountInstructionsCard(modifier = Modifier.fillMaxWidth(), compact = compact)
                    info()
                    Spacer(Modifier.weight(1f))
                    progress()
                }
                ItemsGrid(
                    items = gameItems,
                    onItemClick = ::selectItem,
                    modifier = Modifier.weight(1.2f).fillMaxHeight()
                )
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(pad)
                    .widthIn(max = 640.dp)
                    .align(Alignment.TopCenter),
                verticalArrangement = Arrangement.spacedBy(if (compact) 4.dp else 12.dp)
            ) {
                hud()
                DiscountInstructionsCard(modifier = Modifier.fillMaxWidth(), compact = compact)
                info()
                ItemsGrid(
                    items = gameItems,
                    onItemClick = ::selectItem,
                    modifier = Modifier.fillMaxWidth().weight(1f)
                )
                progress()
            }
        }

        EnhancedUniversalFeedback(feedbackManager)
    }
}

/** Single-row HUD for short screens: back, level, hearts, timer. */
@Composable
private fun CompactDiscountHud(
    timer: String,
    hearts: Int,
    level: UserLevel,
    onBack: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().statusBarsPadding(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
            Icon(
                imageVector = Icons.Default.ArrowBack,
                contentDescription = stringResource(R.string.back),
                tint = RvInk
            )
        }
        Text(
            text = "Level ${level.level}",
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = RvInk,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        Row(horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically) {
            repeat(hearts) {
                Icon(
                    imageVector = Icons.Default.Favorite,
                    contentDescription = null,
                    tint = RvInk,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
        Text(
            text = timer,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = RvInk,
            maxLines = 1,
            modifier = Modifier.padding(start = 8.dp, end = 8.dp).testTag("hud_timer")
        )
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
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = stringResource(R.string.back),
                            tint = RvInk,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                } else {
                    Icon(
                        imageVector = Icons.Default.Pause,
                        contentDescription = null,
                        tint = RvInkSoft,
                        modifier = Modifier.size(24.dp)
                    )
                }

                Column {
                    Text(
                        text = "Level ${level.level}",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = RvInk
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
                        tint = RvInk,
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
                    color = RvInk
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
    modifier: Modifier = Modifier,
    compact: Boolean = false
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = RvSurfaceRaised
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Text(
            text = "TAP THE ITEMS IN ORDER FROM\nLEAST EXPENSIVE TO MOST\nEXPENSIVE.",
            fontSize = if (compact) 14.sp else 16.sp,
            fontWeight = FontWeight.Bold,
            color = RvInk,
            modifier = Modifier
                .fillMaxWidth()
                .padding(if (compact) 8.dp else 16.dp),
            textAlign = TextAlign.Center,
            lineHeight = if (compact) 18.sp else 20.sp,
            maxLines = if (compact) 4 else 5,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun ItemsGrid(
    items: List<PriceItem>,
    onItemClick: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    // Cards are sized from the space the grid is given (no scrolling, no lazy layouts).
    BoxWithConstraints(modifier = modifier, contentAlignment = Alignment.Center) {
        val n = items.size.coerceAtLeast(1)
        val gap = if (maxHeight < 360.dp) 8.dp else 12.dp
        val cols = if (n <= 4 || maxWidth < 360.dp) 2 else 3
        val rows = (n + cols - 1) / cols
        val cardW = ((maxWidth - gap * (cols - 1)) / cols).coerceAtMost(220.dp)
        val cardH = ((maxHeight - gap * (rows - 1)) / rows).coerceAtMost(240.dp)
        Column(
            verticalArrangement = Arrangement.spacedBy(gap),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            items.chunked(cols).forEach { rowItems ->
                Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                    rowItems.forEach { item ->
                        PriceItemCard(
                            item = item,
                            onClick = { onItemClick(item.id) },
                            modifier = Modifier.size(cardW, cardH)
                        )
                    }
                }
            }
        }
    }
}

/** Text size that follows the system font scale only up to [cap]; keeps tight cards from overflowing. */
@Composable
internal fun aCapSp(base: Float, cap: Float = 1.3f): androidx.compose.ui.unit.TextUnit {
    val fs = LocalDensity.current.fontScale
    return (base * minOf(fs, cap) / fs).sp
}

@Composable
fun PriceItemCard(
    item: PriceItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.clickable { onClick() }.testTag("price_card"),
        colors = CardDefaults.cardColors(
            containerColor = if (item.isSelected) RvOutline else RvSurfaceRaised
        ),
        border = androidx.compose.foundation.BorderStroke(
            width = if (item.isSelected) 3.dp else 1.dp,
            color = if (item.isSelected) RvInk else RvOutline
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val rowLayout = maxHeight < 130.dp
            val hasDiscount = item.discountPercentage != null && item.discountPercentage > 0

            // Selection order indicator (number badge, so selection is not colour-only)
            if (item.isSelected && item.selectionOrder != null) {
                Box(
                    modifier = Modifier
                        .padding(6.dp)
                        .size(28.dp)
                        .background(RvInk, CircleShape)
                        .align(Alignment.TopEnd),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = item.selectionOrder.toString(),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = RvOnTone,
                        textAlign = TextAlign.Center
                    )
                }
            }

            val discountTag: @Composable () -> Unit = {
                if (hasDiscount) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = RvSun),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "${item.discountPercentage}%",
                                fontSize = aCapSp(14f),
                                fontWeight = FontWeight.Bold,
                                color = RvInk,
                                maxLines = 1
                            )
                            Text(
                                text = "DISCOUNT",
                                fontSize = aCapSp(12f),
                                fontWeight = FontWeight.Bold,
                                color = RvInk,
                                maxLines = 1
                            )
                        }
                    }
                }
            }

            if (rowLayout) {
                Row(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 10.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)
                ) {
                    Text(text = item.icon, fontSize = aCapSp(32f))
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = String.format("%.2f", item.originalPrice),
                            fontSize = aCapSp(20f),
                            fontWeight = FontWeight.Bold,
                            color = RvInk,
                            maxLines = 1
                        )
                        discountTag()
                    }
                }
            } else {
                Column(
                    modifier = Modifier.fillMaxSize().padding(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = item.icon,
                        fontSize = aCapSp(40f),
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    Text(
                        text = String.format("%.2f", item.originalPrice),
                        fontSize = aCapSp(24f),
                        fontWeight = FontWeight.Bold,
                        color = RvInk,
                        maxLines = 1
                    )
                    if (hasDiscount) Spacer(modifier = Modifier.height(8.dp))
                    discountTag()
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