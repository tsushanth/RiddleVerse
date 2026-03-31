package com.kreativekoala.riddleverse

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.random.Random

/**
 * Tutorial screen for Antonym Balloon puzzles
 */
@Composable
fun AntonymBalloonTutorialScreen(
    onTutorialComplete: () -> Unit,
    onTutorialSkipped: () -> Unit,
    onBack: () -> Unit
) {
    val tutorialManager = remember { AntonymBalloonTutorialManager() }
    val steps = tutorialManager.getTutorialSteps()
    var currentStepIndex by remember { mutableStateOf(0) }
    var tutorialState by remember { mutableStateOf(TutorialState.ACTIVE) }

    // Sample tutorial data
    val samplePairs = listOf(
        AntonymPair("hot", "cold", 1),
        AntonymPair("big", "small", 2),
        AntonymPair("happy", "sad", 3)
    )

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
            advanceStep()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // Tutorial version of the balloon screen
        TutorialAntonymBalloonContent(
            currentStep = steps.getOrNull(currentStepIndex),
            samplePairs = samplePairs,
            onTutorialAction = ::handleTutorialAction,
            onBack = onBack
        )

        // Tutorial overlay
        if (tutorialState == TutorialState.ACTIVE && currentStepIndex < steps.size) {
            val currentStep = steps[currentStepIndex]

            if (currentStep.interactionRequired) {
                MinimalInteractiveOverlay(
                    currentStep = currentStep,
                    totalSteps = steps.size,
                    currentStepNumber = currentStepIndex + 1,
                    onNext = { advanceStep() },
                    onSkip = { skipTutorial() }
                )
            } else {
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
 * Interactive tutorial version of the balloon screen
 */
@Composable
fun TutorialAntonymBalloonContent(
    currentStep: TutorialStep?,
    samplePairs: List<AntonymPair>,
    onTutorialAction: (String) -> Unit,
    onBack: () -> Unit
) {
    // Tutorial game state
    var balloons by remember { mutableStateOf<List<BalloonItem>>(emptyList()) }
    var selectedBalloon by remember { mutableStateOf<BalloonItem?>(null) }
    var matchedPairs by remember { mutableStateOf(0) }

    val configuration = LocalConfiguration.current
    val screenWidth = configuration.screenWidthDp
    val screenHeight = configuration.screenHeightDp

    // Setup balloons when component loads
    LaunchedEffect(samplePairs) {
        setupTutorialBalloons(samplePairs, screenWidth, screenHeight) { balloonList ->
            balloons = balloonList
        }
    }

    // Handle balloon clicks
    fun handleBalloonClick(balloon: BalloonItem) {
        if (balloon.isBurst) return

        if (selectedBalloon == null) {
            // First balloon selected
            selectedBalloon = balloon
            balloons = balloons.map {
                if (it.id == balloon.id) it.copy(isSelected = true)
                else it.copy(isSelected = false)
            }
            onTutorialAction("select_balloon")
        } else if (selectedBalloon?.id == balloon.id) {
            // Same balloon clicked - deselect
            selectedBalloon = null
            balloons = balloons.map { it.copy(isSelected = false) }
        } else {
            // Second balloon selected - check for match
            val firstBalloon = selectedBalloon!!
            if (firstBalloon.pairId == balloon.pairId) {
                // Correct match!
                balloons = balloons.map {
                    if (it.pairId == balloon.pairId) it.copy(isBurst = true, isSelected = false)
                    else it.copy(isSelected = false)
                }
                matchedPairs += 1
                selectedBalloon = null
                onTutorialAction("match_balloons")
            } else {
                // Incorrect match - just deselect for tutorial
                selectedBalloon = null
                balloons = balloons.map { it.copy(isSelected = false) }
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // Background
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    brush = androidx.compose.ui.graphics.Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFFE4C5A0),
                            Color(0xFFD4A574)
                        )
                    )
                )
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
        ) {
            // Top bar with tutorial highlighting
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(
                        if (currentStep?.targetComponent == "timer") {
                            Modifier.tutorialHighlight()
                        } else Modifier
                    ),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Default.ArrowBack, contentDescription = stringResource(R.string.back), tint = Color.Black)
                }

                Text(
                    text = "TUTORIAL",
                    fontWeight = FontWeight.Bold,
                    color = Color.Black
                )

                // Hearts (tutorial version)
                Row {
                    repeat(3) { index ->
                        Icon(
                            Icons.Default.Favorite,
                            contentDescription = null,
                            tint = Color.Red,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Timer and progress with highlighting
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(
                        if (currentStep?.targetComponent == "timer") {
                            Modifier.tutorialHighlight()
                        } else Modifier
                    ),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Timer (frozen for tutorial)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .background(Color.White.copy(alpha = 0.9f), RoundedCornerShape(20.dp))
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Icon(Icons.Default.Timer, contentDescription = null, tint = Color(0xFF8A4DFF))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "2:00",
                        color = Color.Black,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Level
                Text(
                    text = "Tutorial",
                    color = Color.Black,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Game instructions with highlighting
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(
                        if (currentStep?.targetComponent == "progress") {
                            Modifier.tutorialHighlight()
                        } else Modifier
                    ),
                colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.9f))
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "🎈 Match Antonym Pairs 🎈",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF8A4DFF)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Tap balloons to match opposite words",
                        fontSize = 14.sp,
                        color = Color.Gray,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        text = "Progress: $matchedPairs / ${samplePairs.size} pairs",
                        fontSize = 12.sp,
                        color = Color.Gray
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Balloons area with highlighting
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .then(
                        if (currentStep?.targetComponent == "balloons") {
                            Modifier.tutorialHighlight()
                        } else Modifier
                    )
            ) {
                balloons.forEach { balloon ->
                    key(balloon.id + balloon.isSelected) {
                        androidx.compose.animation.AnimatedVisibility(
                            visible = !balloon.isBurst,
                            exit = scaleOut(animationSpec = tween(300)) + fadeOut(),
                            modifier = Modifier.offset(balloon.x.dp, balloon.y.dp)
                        ) {
                            TutorialBalloonView(
                                balloon = balloon,
                                isHighlighted = currentStep?.targetComponent == "balloons" &&
                                        currentStep.interactionRequired,
                                onClick = { handleBalloonClick(balloon) }
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Tutorial version of balloon view with enhanced highlighting
 */
@Composable
fun TutorialBalloonView(
    balloon: BalloonItem,
    isHighlighted: Boolean = false,
    onClick: () -> Unit
) {
    val scale by animateFloatAsState(
        targetValue = if (balloon.isSelected) 1.2f else if (isHighlighted) 1.05f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "balloonScale"
    )

    val floatOffset by rememberInfiniteTransition(label = "float").animateFloat(
        initialValue = 0f,
        targetValue = 10f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ), label = "floatAnimation"
    )

    // Pulsing effect for highlighted balloons
    val pulseAlpha by animateFloatAsState(
        targetValue = if (isHighlighted) 0.8f else 0.3f,
        animationSpec = if (isHighlighted) {
            infiniteRepeatable(
                animation = tween(1000, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            )
        } else {
            tween(300) // Simple transition when not highlighted
        },
        label = "pulseAnimation"
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable { onClick() }
    ) {
        // Balloon with enhanced highlighting
        Box(
            modifier = Modifier
                .size(80.dp)
                .scale(scale)
                .offset(y = floatOffset.dp)
                .background(balloon.color, CircleShape)
                .border(
                    width = when {
                        balloon.isSelected -> 4.dp
                        isHighlighted -> 3.dp
                        else -> 1.dp
                    },
                    color = when {
                        balloon.isSelected -> Color.White
                        isHighlighted -> Color(0xFF7B1FA2).copy(alpha = pulseAlpha)
                        else -> Color.LightGray
                    },
                    shape = CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = balloon.word,
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
        }

        // String
        Box(
            modifier = Modifier
                .width(2.dp)
                .height(20.dp)
                .background(Color.Black.copy(alpha = 0.6f))
        )
    }
}

/**
 * Setup balloons for tutorial with specific positioning
 */
private fun setupTutorialBalloons(
    pairs: List<AntonymPair>,
    screenWidth: Int,
    screenHeight: Int,
    onComplete: (List<BalloonItem>) -> Unit
) {
    val balloonColors = listOf(
        Color(0xFFE91E63), // Pink
        Color(0xFF2196F3), // Blue
        Color(0xFF4CAF50), // Green
        Color(0xFFFF9800), // Orange
        Color(0xFF9C27B0), // Purple
        Color(0xFF00BCD4)  // Cyan
    )

    val allWords = mutableListOf<String>()
    pairs.forEach { pair ->
        allWords.add(pair.word1)
        allWords.add(pair.word2)
    }

    // Create a more organized layout for tutorial
    val balloonSize = 180
    val horizontalPadding = 40
    val verticalPadding = 100
    val availableWidth = (screenWidth - horizontalPadding - balloonSize).coerceAtLeast(400)
    val availableHeight = (screenHeight - verticalPadding - balloonSize).coerceAtLeast(300)

    // Create grid-like positioning for tutorial clarity
    val columns = 3
    val rows = 2
    val cellWidth = availableWidth / columns
    val cellHeight = availableHeight / rows

    val balloons = allWords.mapIndexed { index, word ->
        val pair = pairs.find { it.word1 == word || it.word2 == word }!!
        val colorIndex = pair.id % balloonColors.size

        // Grid positioning with some randomness
        val col = index % columns
        val row = index / columns
        val baseX = col * cellWidth + cellWidth / 4
        val baseY = row * cellHeight + cellHeight / 4

        // Add some randomness within the cell
        val randomOffsetX = (-20..20).random().toFloat()
        val randomOffsetY = (-20..20).random().toFloat()

        val finalX = (baseX + randomOffsetX).coerceIn(0f, availableWidth.toFloat())
        val finalY = (baseY + randomOffsetY).coerceIn(0f, availableHeight.toFloat())

        BalloonItem(
            word = word,
            pairId = pair.id,
            id = "${word}_$index",
            x = finalX,
            y = finalY,
            color = balloonColors[colorIndex]
        )
    }

    onComplete(balloons)
}
