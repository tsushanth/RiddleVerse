package com.kreativekoala.riddleverse

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.text.style.TextOverflow
import com.kreativekoala.riddleverse.ui.theme.RvCanvas
import com.kreativekoala.riddleverse.ui.theme.RvError
import com.kreativekoala.riddleverse.ui.theme.RvGrape
import com.kreativekoala.riddleverse.ui.theme.RvInk
import com.kreativekoala.riddleverse.ui.theme.RvOnTone
import com.kreativekoala.riddleverse.ui.theme.RvOutline
import com.kreativekoala.riddleverse.ui.theme.RvSky
import com.kreativekoala.riddleverse.ui.theme.RvSuccess
import com.kreativekoala.riddleverse.ui.theme.RvSuccessEdge
import com.kreativekoala.riddleverse.ui.theme.RvSunEdge
import com.kreativekoala.riddleverse.ui.theme.RvSurface
import com.kreativekoala.riddleverse.ui.theme.RvSurfaceRaised
import com.kreativekoala.riddleverse.ui.theme.RvViolet
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.AdSize
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.delay



@Composable
fun MultipleChoicePuzzleScreen(
    puzzleId: String,
    question: String,
    difficulty: String,
    options: List<String>,
    selectedOption: String?,
    correctAnswer: String,
    timerSeconds: Int,
    questionNumber: Int,
    totalQuestions: Int,
    onOptionSelected: (String) -> Unit,
    onContinue: (Any?) -> Unit,
    onCorrectAnswer: (Boolean) -> Unit,
    onHint: () -> Unit,
    onBack: () -> Unit = {},
    puzzleType: String = "default",
    useEnhancedTheme: Boolean = false
) {
    val context = LocalContext.current
    var timeLeft by remember { mutableIntStateOf(timerSeconds) }
    var showResult by remember { mutableStateOf(false) }
    var isCorrectAnswer by remember { mutableStateOf<Boolean?>(null) }
    var showInTimeBonus by remember { mutableStateOf(false) }
    var isAnswered by remember { mutableStateOf(false) }
    val correctSound = R.raw.correct
    val incorrectSound = R.raw.buzz
    var expanded by remember { mutableStateOf(false) }
    var recipientEmail by remember { mutableStateOf("") }
    var recipientName by remember { mutableStateOf("") }
    val feedbackManager = rememberUnifiedFeedbackManager()
    // REMOVED: showShareDialog state variable
    var showCustomShareDialog by remember { mutableStateOf(false) }
    // REMOVED: shareableContent state variable

    val progress by animateFloatAsState(
        targetValue = timeLeft / timerSeconds.toFloat(),
        animationSpec = tween(durationMillis = 500),
        label = "progressAnim"
    )

    LaunchedEffect(key1 = Unit) {
        while (timeLeft > 0 && !isAnswered) {
            delay(1000L)
            timeLeft -= 1
        }
    }

    // Theme colors based on puzzle type
    val themeColors = if (useEnhancedTheme) {
        when (puzzleType.lowercase()) {
            "sentencetransitions" -> ThemeColors(
                background = SolidColor(RvCanvas),
                primary = RvSuccess,
                secondary = RvSky,
                surface = RvSurface,
                onSurface = RvInk,
                accent = RvGrape
            )
            else -> getDefaultThemeColors()
        }
    } else {
        getDefaultThemeColors()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                if (useEnhancedTheme) themeColors.background
                else SolidColor(RvCanvas)
            )
    ) {
        // Background effects for enhanced theme
        if (useEnhancedTheme) {
            FloatingParticles()
        }

        BoxWithConstraints(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.TopCenter
        ) {
            val compact = maxHeight < 600.dp
            val wide = maxWidth > maxHeight && maxWidth >= 560.dp
            val gap = if (compact) 8.dp else 16.dp
            val optionsContent: @Composable () -> Unit = {
                EnhancedOptionsSection(
                    options = options,
                    selectedOption = selectedOption,
                    correctAnswer = correctAnswer,
                    isAnswered = isAnswered,
                    useEnhancedTheme = useEnhancedTheme,
                    themeColors = themeColors,
                    onOptionSelected = { option ->
                        if (!isAnswered) {
                            onOptionSelected(option)
                            isAnswered = true
                            val isCorrect = option == correctAnswer

                            // UPDATED: Removed automatic sharing after correct answers
                            feedbackManager.showFeedback(
                                puzzleType = puzzleType,
                                isCorrect = isCorrect,
                                userAnswer = option,
                                correctAnswer = correctAnswer,
                                timeSpent = (timerSeconds - timeLeft) * 1000L,
                                difficulty = difficulty,
                                timeRemaining = timeLeft,
                                totalTime = timerSeconds,
                                onComplete = {
                                    // Simply continue without showing share dialog
                                    onContinue(true)
                                }
                            )
                        }
                    }
                )
            }
            val questionContent: @Composable () -> Unit = {
                EnhancedQuestionCard(
                    question = question,
                    questionNumber = questionNumber,
                    totalQuestions = totalQuestions,
                    puzzleType = puzzleType,
                    useEnhancedTheme = useEnhancedTheme,
                    themeColors = themeColors
                )
            }
            val resultContent: @Composable () -> Unit = {
                AnimatedVisibility(visible = showResult) {
                    EnhancedResultDisplay(
                        isCorrectAnswer = isCorrectAnswer,
                        correctAnswer = correctAnswer,
                        useEnhancedTheme = useEnhancedTheme,
                        themeColors = themeColors
                    )
                }
                AnimatedVisibility(
                    visible = showInTimeBonus,
                    enter = fadeIn(animationSpec = tween(500)) + scaleIn(),
                    exit = fadeOut(animationSpec = tween(300))
                ) {
                    Text(
                        "+2 In-Time Bonus!",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (useEnhancedTheme) themeColors.accent else RvSuccessEdge,
                        modifier = Modifier.fillMaxWidth()
                            .wrapContentWidth(Alignment.CenterHorizontally)
                    )
                }
            }

            // Fit-to-screen: fixed header/timer on top, question + options in the middle,
            // hint pinned at the bottom. The inner scroll is only an invisible last-resort net.
            Column(
                modifier = Modifier
                    .fillMaxHeight()
                    .widthIn(max = 720.dp)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = if (compact) 8.dp else 16.dp)
            ) {
                EnhancedHeaderRow(
                    questionNumber = questionNumber,
                    totalQuestions = totalQuestions,
                    onBack = onBack,
                    onMenuExpanded = { expanded = it },
                    expanded = expanded,
                    onSharePuzzle = { showCustomShareDialog = true },
                    useEnhancedTheme = useEnhancedTheme,
                    themeColors = themeColors
                )
                Spacer(modifier = Modifier.height(if (compact) 4.dp else 8.dp))
                EnhancedTimerBar(
                    timeLeft = timeLeft,
                    progress = progress,
                    useEnhancedTheme = useEnhancedTheme,
                    themeColors = themeColors
                )
                Spacer(modifier = Modifier.height(gap))

                if (wide) {
                    Row(
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Column(
                            modifier = Modifier.weight(1f).fillMaxHeight()
                                .verticalScroll(rememberScrollState())
                        ) {
                            questionContent()
                            resultContent()
                        }
                        Column(
                            modifier = Modifier.weight(1f).fillMaxHeight()
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.Center
                        ) {
                            optionsContent()
                        }
                    }
                } else {
                    Column(
                        modifier = Modifier.weight(1f).fillMaxWidth()
                            .verticalScroll(rememberScrollState())
                    ) {
                        questionContent()
                        Spacer(modifier = Modifier.height(gap))
                        optionsContent()
                        resultContent()
                    }
                }

                Spacer(modifier = Modifier.height(if (compact) 4.dp else 8.dp))
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    EnhancedHintButton(
                        onHint = onHint,
                        useEnhancedTheme = useEnhancedTheme,
                        themeColors = themeColors
                    )
                }
            }
        }

        // Keep the feedback overlay positioned absolutely
        EnhancedUniversalFeedback(feedbackManager)
    }

    // REMOVED: Automatic social share dialog - only keep the custom puzzle share dialog
    // Users can still manually share puzzles via the menu, but no automatic sharing after correct answers

    if (showCustomShareDialog) {
        CustomPuzzleShareDialog(
            onDismiss = { showCustomShareDialog = false },
            onShare = { email, name ->
                sharePuzzle(
                    puzzleId = puzzleId,
                    recipientEmail = email,
                    recipientName = name,
                    senderId = FirebaseAuth.getInstance().currentUser?.displayName
                        ?: FirebaseAuth.getInstance().currentUser?.email
                        ?: "anonymous"
                )
                showCustomShareDialog = false
            }
        )
    }
}

// Theme Colors Data Class
data class ThemeColors(
    val background: Brush,
    val primary: Color,
    val secondary: Color,
    val surface: Color,
    val onSurface: Color,
    val accent: Color
)

fun getDefaultThemeColors() = ThemeColors(
    background = SolidColor(RvCanvas),
    primary = RvViolet,
    secondary = RvViolet,
    surface = RvSurfaceRaised,
    onSurface = RvInk,
    accent = RvSunEdge
)

// Enhanced Components
@Composable
fun EnhancedHeaderRow(
    questionNumber: Int,
    totalQuestions: Int,
    onBack: () -> Unit,
    onMenuExpanded: (Boolean) -> Unit,
    expanded: Boolean,
    onSharePuzzle: () -> Unit,
    useEnhancedTheme: Boolean,
    themeColors: ThemeColors
) {
    Row(
        modifier = Modifier.statusBarsPadding().fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onBack) {
            Icon(
                Icons.Default.ArrowBack,
                contentDescription = "Back",
                tint = RvInk
            )
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                "Question $questionNumber of $totalQuestions",
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = RvInk
            )
            LinearProgressIndicator(
                progress = questionNumber.toFloat() / totalQuestions.toFloat(),
                modifier = Modifier
                    .width(120.dp)
                    .padding(top = 4.dp),
                color = if (useEnhancedTheme) themeColors.accent else RvViolet,
                trackColor = if (useEnhancedTheme)
                    RvOutline
                else
                    RvOutline
            )
        }

        Box {
            IconButton(onClick = { onMenuExpanded(true) }) {
                Icon(
                    Icons.Default.MoreVert,
                    contentDescription = "Settings",
                    tint = RvInk
                )
            }

            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { onMenuExpanded(false) }
            ) {
                DropdownMenuItem(
                    onClick = {
                        onMenuExpanded(false)
                        onSharePuzzle()
                    },
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.Email,
                                contentDescription = "Share Puzzle",
                                modifier = Modifier.size(20.dp),
                                tint = RvSuccess
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Share Puzzle")
                        }
                    }
                )
            }
        }
    }
}

@Composable
fun EnhancedTimerBar(
    timeLeft: Int,
    progress: Float,
    useEnhancedTheme: Boolean,
    themeColors: ThemeColors
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(24.dp)
            .clip(RoundedCornerShape(50))
            .background(
                if (useEnhancedTheme)
                    RvSurface
                else
                    RvSurface
            ),
        contentAlignment = Alignment.Center
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth(progress.coerceIn(0f, 1f))
                .fillMaxHeight()
                .background(
                    if (useEnhancedTheme) themeColors.secondary.copy(alpha = 0.35f) else RvViolet.copy(alpha = 0.3f)
                ),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "00:${String.format("%02d", timeLeft)}",
                color = RvInk,
                fontWeight = FontWeight.Bold
            )
        }
        Icon(
            imageVector = Icons.Default.Timer,
            contentDescription = null,
            tint = RvInk,
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 8.dp)
        )
    }
}

@Composable
fun EnhancedQuestionCard(
    question: String,
    questionNumber: Int,
    totalQuestions: Int,
    puzzleType: String,
    useEnhancedTheme: Boolean,
    themeColors: ThemeColors
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp)),
        colors = CardDefaults.cardColors(
            containerColor = if (useEnhancedTheme) themeColors.surface else RvViolet
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            // Enhanced question text for sentence transitions
            if (puzzleType.lowercase() == "sentencetransitions" && useEnhancedTheme) {
                val parts = question.split("_______")
                if (parts.size == 2) {
                    Text(
                        parts[0],
                        color = themeColors.onSurface,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Normal,
                        lineHeight = 26.sp
                    )

                    Box(
                        modifier = Modifier
                            .padding(vertical = 8.dp)
                            .background(
                                themeColors.primary.copy(alpha = 0.1f),
                                RoundedCornerShape(8.dp)
                            )
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        Text(
                            "[ Choose transition word ]",
                            color = themeColors.primary,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            fontStyle = FontStyle.Italic
                        )
                    }

                    Text(
                        parts[1],
                        color = themeColors.onSurface,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Normal,
                        lineHeight = 26.sp
                    )
                } else {
                    Text(
                        question,
                        color = themeColors.onSurface,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Medium,
                        lineHeight = 26.sp
                    )
                }
            } else {
                Text(
                    question,
                    color = if (useEnhancedTheme) themeColors.onSurface else RvOnTone,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Medium,
                    lineHeight = 26.sp
                )
            }
        }
    }
}

@Composable
fun EnhancedOptionsSection(
    options: List<String>,
    selectedOption: String?,
    correctAnswer: String,
    isAnswered: Boolean,
    useEnhancedTheme: Boolean,
    themeColors: ThemeColors,
    onOptionSelected: (String) -> Unit
) {
    Column {
        options.forEach { option ->
            val isCorrect = option == correctAnswer
            val isSelected = option == selectedOption

            val backgroundColor = when {
                isSelected && isCorrect -> if (useEnhancedTheme)
                    themeColors.primary.copy(alpha = 0.1f) else RvSuccess.copy(alpha = 0.15f)
                isSelected && !isCorrect -> if (useEnhancedTheme)
                    RvError.copy(alpha = 0.15f) else RvError.copy(alpha = 0.15f)
                else -> if (useEnhancedTheme) themeColors.surface else RvSurfaceRaised
            }

            val borderColor = when {
                isSelected && isCorrect -> if (useEnhancedTheme)
                    themeColors.primary else RvSuccess
                isSelected && !isCorrect -> RvError
                else -> if (useEnhancedTheme)
                    themeColors.primary.copy(alpha = 0.5f) else RvViolet
            }

            val icon = when {
                isSelected && isCorrect -> Icons.Default.Check
                isSelected && !isCorrect -> Icons.Default.Close
                else -> null
            }

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .clickable { onOptionSelected(option) },
                shape = RoundedCornerShape(if (useEnhancedTheme) 16.dp else 40.dp),
                border = BorderStroke(2.dp, borderColor),
                colors = CardDefaults.cardColors(containerColor = backgroundColor),
                elevation = CardDefaults.cardElevation(
                    defaultElevation = 0.dp
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 56.dp)
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        option,
                        modifier = Modifier.weight(1f),
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                        color = if (useEnhancedTheme) themeColors.onSurface else RvInk,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium
                    )
                    icon?.let {
                        Icon(
                            it,
                            contentDescription = null,
                            tint = borderColor,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun EnhancedResultDisplay(
    isCorrectAnswer: Boolean?,
    correctAnswer: String,
    useEnhancedTheme: Boolean,
    themeColors: ThemeColors
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (isCorrectAnswer == true) {
            Text(
                "\uD83C\uDF89 Excellent!",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = if (useEnhancedTheme) themeColors.primary else RvSuccess
            )
            if (useEnhancedTheme) {
                ConfettiAnimation()
            }
        } else if (isCorrectAnswer == false) {
            Text(
                "\u274C Incorrect! Correct: $correctAnswer",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = RvError,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
fun EnhancedHintButton(
    onHint: () -> Unit,
    useEnhancedTheme: Boolean,
    themeColors: ThemeColors
) {
    Card(
        modifier = Modifier
            .wrapContentSize()
            .clickable { onHint() },
        shape = RoundedCornerShape(50),
        border = BorderStroke(
            1.dp,
            if (useEnhancedTheme) themeColors.accent else RvSunEdge
        ),
        colors = CardDefaults.cardColors(
            containerColor = if (useEnhancedTheme)
                themeColors.surface else Color.Transparent
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = 0.dp
        )
    ) {
        Row(
            modifier = Modifier.heightIn(min = 48.dp).padding(horizontal = 20.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.Lightbulb,
                contentDescription = null,
                tint = if (useEnhancedTheme) themeColors.accent else RvSunEdge,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                "Need a Hint?",
                color = if (useEnhancedTheme) themeColors.accent else RvSunEdge,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
fun FloatingParticles() {
    // Add floating animation particles for enhanced theme
    val infiniteTransition = rememberInfiniteTransition(label = "particles")

    repeat(6) { index ->
        val animatedY by infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 3000 + index * 500, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "particleY$index"
        )

        val animatedX by infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 4000 + index * 300, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "particleX$index"
        )

        Box(
            modifier = Modifier
                .offset(
                    x = (animatedX * 100).dp,
                    y = (animatedY * 200).dp
                )
                .size(4.dp)
                .background(
                    RvGrape.copy(alpha = 0.3f),
                    CircleShape
                )
        )
    }
}

fun calculateCurrentScore(timeLeft: Int): Int {
    return when {
        timeLeft > 20 -> 100
        timeLeft > 10 -> 75
        timeLeft > 5 -> 50
        else -> 25
    }
}