package com.kreativekoala.riddleverse

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.sp

/**
 * Tutorial screen for Discount Price Ordering puzzles
 */
@Composable
fun DiscountPriceTutorialScreen(
    onTutorialComplete: () -> Unit,
    onTutorialSkipped: () -> Unit,
    onBack: () -> Unit
) {
    val tutorialManager = remember { DiscountPriceTutorialManager() }
    val steps = tutorialManager.getTutorialSteps()
    var currentStepIndex by remember { mutableStateOf(0) }
    var tutorialState by remember { mutableStateOf(TutorialState.ACTIVE) }

    // Tutorial data - simple price items with clear examples
    val tutorialItems = remember {
        listOf(
            PriceItem(
                id = 1,
                name = "Book",
                icon = "📚",
                originalPrice = 20.00,
                discountPercentage = null,
                finalPrice = 20.00
            ),
            PriceItem(
                id = 2,
                name = "Shirt",
                icon = "👕",
                originalPrice = 40.00,
                discountPercentage = 50,
                finalPrice = 20.00
            ),
            PriceItem(
                id = 3,
                name = "Ball",
                icon = "⚽",
                originalPrice = 30.00,
                discountPercentage = null,
                finalPrice = 30.00
            ),
            PriceItem(
                id = 4,
                name = "Hat",
                icon = "🎩",
                originalPrice = 50.00,
                discountPercentage = 20,
                finalPrice = 40.00
            )
        )
    }

    // Tutorial interaction state
    var gameItems by remember { mutableStateOf(tutorialItems) }
    var selectedOrder by remember { mutableStateOf<List<Int>>(emptyList()) }
    var userHasSelected by remember { mutableStateOf(false) }

    val haptics = LocalHapticFeedback.current

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
        if (step?.interactionRequired == true && step.expectedAction == action) {
            userHasSelected = true
            // Don't auto-advance, let user manually continue
        }
    }

    // Handle item selection
    fun selectItem(itemId: Int) {
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

        handleTutorialAction("select_item")
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // Tutorial Discount Price content
        TutorialDiscountPriceContent(
            currentStep = steps.getOrNull(currentStepIndex),
            gameItems = gameItems,
            onItemClick = ::selectItem,
            onBack = onBack
        )

        // Tutorial overlay
        if (tutorialState == TutorialState.ACTIVE && currentStepIndex < steps.size) {
            val currentStep = steps[currentStepIndex]

            if (currentStep.interactionRequired) {
                // Custom overlay for interaction step
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.BottomCenter
                ) {
                    // Light background
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.2f))
                    )

                    // Tutorial card at bottom
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(8.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp)
                        ) {
                            // Header
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = currentStep.title,
                                        fontSize = 18.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF333333)
                                    )
                                    Text(
                                        text = "Step ${currentStepIndex + 1} of ${steps.size}",
                                        fontSize = 11.sp,
                                        color = Color.Gray
                                    )
                                }

                                IconButton(onClick = { skipTutorial() }) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = stringResource(R.string.skip_tutorial),
                                        tint = Color.Gray,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            // Progress bar
                            LinearProgressIndicator(
                                progress = { (currentStepIndex + 1).toFloat() / steps.size.toFloat() },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(3.dp),
                                color = Color(0xFF7B1FA2),
                                trackColor = Color(0xFFE1BEE7)
                            )

                            Spacer(modifier = Modifier.height(12.dp))

                            Text(
                                text = currentStep.description,
                                fontSize = 14.sp,
                                color = Color(0xFF666666),
                                lineHeight = 20.sp
                            )

                            Spacer(modifier = Modifier.height(16.dp))

                            // Action buttons
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                TextButton(
                                    onClick = { skipTutorial() },
                                    colors = ButtonDefaults.textButtonColors(contentColor = Color.Gray)
                                ) {
                                    Text(stringResource(R.string.skip), fontSize = 12.sp)
                                }

                                if (userHasSelected) {
                                    // Show Next button after interaction
                                    Button(
                                        onClick = { advanceStep() },
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF7B1FA2)),
                                        shape = RoundedCornerShape(20.dp)
                                    ) {
                                        Text(
                                            text = stringResource(R.string.next),
                                            color = Color.White,
                                            fontSize = 12.sp
                                        )
                                    }
                                } else {
                                    // Show instruction to interact
                                    Text(
                                        text = "👆 Tap an item above",
                                        fontSize = 12.sp,
                                        color = Color(0xFF7B1FA2),
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        }
                    }
                }
            } else {
                // Use standard overlay for non-interactive steps
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

/**
 * Tutorial-specific discount price content
 */
@Composable
fun TutorialDiscountPriceContent(
    currentStep: TutorialStep?,
    gameItems: List<PriceItem>,
    onItemClick: (Int) -> Unit,
    onBack: () -> Unit
) {
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
            modifier = Modifier.fillMaxSize().statusBarsPadding()
        ) {
            // Top Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        Icons.Default.ArrowBack,
                        contentDescription = stringResource(R.string.back),
                        tint = Color.White,
                        modifier = Modifier.size(28.dp)
                    )
                }

                Text(
                    text = "TUTORIAL",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )

                Row {
                    repeat(4) {
                        Icon(
                            Icons.Default.Favorite,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            // Instructions with highlighting
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 20.dp)
                    .then(
                        if (currentStep?.targetComponent == "instructions") {
                            Modifier.tutorialHighlight()
                        } else Modifier
                    ),
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

            // Round indicator
            Text(
                text = "TUTORIAL",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White.copy(alpha = 0.7f),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 20.dp),
                textAlign = TextAlign.Center
            )

            // Items grid with highlighting
            TutorialItemsGrid(
                items = gameItems,
                currentStep = currentStep,
                onItemClick = onItemClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = 20.dp)
                    .then(
                        if (currentStep?.targetComponent == "items") {
                            Modifier.tutorialHighlight()
                        } else Modifier
                    )
            )

            // Progress bar placeholder
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 40.dp, vertical = 30.dp)
                    .height(4.dp)
                    .background(
                        Color.White.copy(alpha = 0.3f),
                        shape = RoundedCornerShape(2.dp)
                    )
            )
        }
    }
}

/**
 * Tutorial version of items grid
 */
@Composable
fun TutorialItemsGrid(
    items: List<PriceItem>,
    currentStep: TutorialStep?,
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
                    TutorialPriceItemCard(
                        item = item,
                        isHighlighted = currentStep?.targetComponent == "items" &&
                                currentStep.interactionRequired,
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

/**
 * Tutorial version of price item card with enhanced highlighting
 */
@Composable
fun TutorialPriceItemCard(
    item: PriceItem,
    isHighlighted: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val cardColor = when {
        item.isSelected -> Color.White.copy(alpha = 0.3f)
        isHighlighted -> Color.White.copy(alpha = 0.1f)
        else -> Color.Transparent
    }

    val borderColor = when {
        item.isSelected -> Color.White
        isHighlighted -> Color.Yellow.copy(alpha = 0.8f)
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
            width = when {
                item.isSelected -> 3.dp
                isHighlighted -> 2.dp
                else -> 1.dp
            },
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

                // Original price
                Text(
                    text = "$${String.format("%.2f", item.originalPrice)}",
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
