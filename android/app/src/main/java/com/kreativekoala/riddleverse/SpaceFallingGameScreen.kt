package com.kreativekoala.riddleverse

import com.kreativekoala.riddleverse.ui.theme.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.Image
import kotlinx.coroutines.delay
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Timer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.platform.testTag
import android.os.Handler
import android.os.Looper
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource

@Composable
fun FallingGameScreen(
    puzzleId: String,
    question: String,
    difficulty: String = "Easy",
    options: List<String>,
    correctAnswer: String,
    selectedOption: String?,
    timerSeconds: Int = 60,
    questionNumber: Int = 1,
    totalQuestions: Int = 1,
    onOptionSelected: (String) -> Unit,
    onContinue: (String?) -> Unit,
    onCorrectAnswer: (Boolean) -> Unit,
    onHint: () -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val rocketHeight = 100.dp
    val screenHeight = LocalConfiguration.current.screenHeightDp.dp
    val density = LocalDensity.current
    val screenHeightPx = with(density) { screenHeight.toPx() }
    val rocketHeightPx = with(density) { rocketHeight.toPx() }

    var rocketY by remember { mutableStateOf(0f) }
    var lives by remember { mutableStateOf(3) }
    var gameScore by remember { mutableStateOf(0) }
    var remainingTime by remember { mutableIntStateOf(timerSeconds) }
    var isGameActive by remember { mutableStateOf(true) }
    var showResult by remember { mutableStateOf(false) }
    var isCorrect by remember { mutableStateOf(false) }
    var selectedAnswer by remember { mutableStateOf<String?>(null) }

    // Game timer
    LaunchedEffect(Unit) {
        while (remainingTime > 0 && isGameActive && lives > 0) {
            delay(1000L)
            remainingTime--
        }
        if (remainingTime <= 0 || lives <= 0) {
            isGameActive = false
        }
    }

    // Rocket falling animation
    LaunchedEffect(isGameActive) {
        if (isGameActive) {
            while (rocketY < screenHeightPx - rocketHeightPx && isGameActive && lives > 0) {
                val progress = rocketY / (screenHeightPx - rocketHeightPx)
                val speed = 3f * (1f - progress * 0.5f).coerceAtLeast(0.5f) // Slower falling

                rocketY += speed
                delay(16L) // ~60 FPS
            }
            // If rocket reaches bottom without answer, lose a life
            if (rocketY >= screenHeightPx - rocketHeightPx && isGameActive && selectedAnswer == null) {
                lives--
                rocketY = 0f // Reset rocket position
            }
        }
    }

    val onOption: (String) -> Unit = { option ->
        if (selectedAnswer == null && isGameActive) {
            selectedAnswer = option
            onOptionSelected(option)
            isCorrect = option == correctAnswer
            showResult = true
            isGameActive = false

            if (isCorrect) {
                gameScore += 10
                onCorrectAnswer(remainingTime > 0)
            } else {
                lives--
            }

            // Auto-continue after showing result
            Handler(Looper.getMainLooper()).postDelayed({
                onContinue(option)
            }, 2500)
        }
    }

    // HUD, question, falling region and answers are laid out from the measured size so that the
    // answer buttons and the hint never depend on leftover space (no scrolling).
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(
                // Gradient space background
                androidx.compose.ui.graphics.Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF000428),
                        Color(0xFF004e92)
                    )
                )
            )
            .statusBarsPadding()
    ) {
        val wide = maxWidth > maxHeight
        val compact = maxHeight < 700.dp
        val pad = if (maxHeight < 600.dp) 12.dp else 16.dp
        val showOptions = !showResult && isGameActive && lives > 0
        val fallFraction = (rocketY / (screenHeightPx - rocketHeightPx)).coerceIn(0f, 1f)

        val hud: @Composable () -> Unit = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.Default.ArrowBack,
                            contentDescription = stringResource(R.string.back),
                            tint = RvOnTone,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Column {
                        Text(
                            "Question $questionNumber/$totalQuestions",
                            color = RvOnTone,
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            difficulty,
                            color = RvOnTone.copy(alpha = 0.85f),
                            fontSize = 12.sp,
                            maxLines = 1
                        )
                    }
                }

                Text(
                    "Score: $gameScore",
                    color = RvOnTone,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    modifier = Modifier.padding(horizontal = 8.dp)
                )

                Column(horizontalAlignment = Alignment.End) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Timer,
                            contentDescription = "Timer",
                            tint = RvOnTone,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            String.format("%d:%02d", remainingTime / 60, remainingTime % 60),
                            color = RvOnTone,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1
                        )
                    }
                    Row {
                        repeat(lives) {
                            Icon(
                                Icons.Default.Favorite,
                                contentDescription = "Life",
                                tint = Color(0xFFFF8A80),
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }
        }

        val questionCard: @Composable () -> Unit = {
            Card(
                modifier = Modifier.fillMaxWidth().testTag("space_question"),
                colors = CardDefaults.cardColors(containerColor = RvCanvas.copy(alpha = 0.92f)),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(if (compact) 12.dp else 20.dp).fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    if (!compact) {
                        Text(
                            "Quick! Answer before it crashes!",
                            color = RvInkSoft,
                            fontSize = 12.sp,
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(Modifier.height(8.dp))
                    }
                    Text(
                        question,
                        color = RvInk,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        maxLines = 4,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        // The rocket falls through this region; its speed/timing still comes from the game logic,
        // only the distance is mapped onto the space that is actually free.
        val fallRegion: @Composable (Modifier) -> Unit = { regionModifier ->
            BoxWithConstraints(modifier = regionModifier.testTag("space_fall_region")) {
                val rocketSize = minOf(80.dp, maxHeight).coerceAtLeast(32.dp)
                Box(
                    modifier = Modifier
                        .offset(y = ((maxHeight - rocketSize).coerceAtLeast(0.dp)) * fallFraction)
                        .align(Alignment.TopCenter)
                        .size(rocketSize)
                        .clip(CircleShape)
                        .background(
                            androidx.compose.ui.graphics.Brush.radialGradient(
                                colors = listOf(Color(0xFFFFD700), Color(0xFFFF8C00))
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text("🚀", fontSize = (rocketSize.value * 0.4f).sp)
                }
            }
        }

        val optionsPane: @Composable (Boolean) -> Unit = { twoColumns ->
            Column(
                modifier = Modifier.fillMaxWidth().testTag("space_options"),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                val optionButton: @Composable (String, Modifier) -> Unit = { option, m ->
                    Button(
                        onClick = { onOption(option) },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1565C0)),
                        shape = RoundedCornerShape(28.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                        border = androidx.compose.foundation.BorderStroke(2.dp, RvOnTone.copy(alpha = 0.6f)),
                        modifier = m.heightIn(min = 56.dp)
                    ) {
                        Text(
                            option,
                            fontSize = 16.sp,
                            color = RvOnTone,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                if (twoColumns) {
                    options.chunked(2).forEach { pair ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            pair.forEach { option -> optionButton(option, Modifier.weight(1f)) }
                            if (pair.size == 1) Spacer(Modifier.weight(1f))
                        }
                    }
                } else {
                    options.forEach { option -> optionButton(option, Modifier.fillMaxWidth()) }
                }

                // Hint Button
                OutlinedButton(
                    onClick = onHint,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = RvOnTone),
                    border = androidx.compose.foundation.BorderStroke(2.dp, RvOnTone),
                    shape = RoundedCornerShape(24.dp),
                    enabled = isGameActive,
                    modifier = Modifier.heightIn(min = 48.dp)
                ) {
                    Text("💡 ${stringResource(R.string.hint)}", fontSize = 14.sp, maxLines = 1)
                }
            }
        }

        if (wide) {
            Row(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .widthIn(max = 1100.dp)
                    .fillMaxSize()
                    .padding(pad),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
                    hud()
                    Spacer(Modifier.height(8.dp))
                    questionCard()
                    Spacer(Modifier.height(8.dp))
                    fallRegion(Modifier.weight(1f).fillMaxWidth())
                }
                Box(
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    contentAlignment = Alignment.Center
                ) {
                    if (showOptions) optionsPane(false)
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .widthIn(max = 640.dp)
                    .fillMaxSize()
                    .padding(pad)
            ) {
                hud()
                Spacer(Modifier.height(8.dp))
                questionCard()
                fallRegion(Modifier.weight(1f).fillMaxWidth().padding(vertical = 8.dp))
                if (showOptions) optionsPane(compact)
            }
        }

        // Result Display
        if (showResult) {
            Card(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(32.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (isCorrect) Color(0xFF4CAF50) else Color(0xFFE57373)
                )
            ) {
                Text(
                    text = if (isCorrect) "🎉 Correct! +10 Points" else "❌ Wrong! Answer: $correctAnswer",
                    color = RvInk,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(16.dp)
                )
            }
        }

        // Game Over Screen
        if (!isGameActive && lives <= 0 && !showResult) {
            Card(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(32.dp),
                colors = CardDefaults.cardColors(
                    containerColor = RvCanvas.copy(alpha = 0.8f)
                )
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        "💥 Game Over!",
                        color = Color.Red,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "${stringResource(R.string.final_score)}: $gameScore",
                        color = RvInk,
                        fontSize = 16.sp
                    )
                    Spacer(Modifier.height(16.dp))
                    Button(
                        onClick = { onContinue(null) },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF1E88E5)
                        )
                    ) {
                        Text(stringResource(R.string.continue_label), color = Color.White)
                    }
                }
            }
        }

        // Time's Up Screen
        if (!isGameActive && remainingTime <= 0 && !showResult) {
            Card(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(32.dp),
                colors = CardDefaults.cardColors(
                    containerColor = RvCanvas.copy(alpha = 0.8f)
                )
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        "⏰ Time's Up!",
                        color = Color(0xFFFF9800),
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Answer: $correctAnswer",
                        color = RvInk,
                        fontSize = 16.sp,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(16.dp))
                    Button(
                        onClick = { onContinue(null) },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF1E88E5)
                        )
                    ) {
                        Text(stringResource(R.string.continue_label), color = Color.White)
                    }
                }
            }
        }
    }
}
