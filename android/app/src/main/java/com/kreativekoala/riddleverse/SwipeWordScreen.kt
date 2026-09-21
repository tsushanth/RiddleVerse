package com.kreativekoala.riddleverse
import com.kreativekoala.riddleverse.ui.theme.*
import android.annotation.SuppressLint
import android.util.Log
import android.widget.Toast
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.layout.*
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.FractionalThreshold
import androidx.compose.material.rememberSwipeableState
import androidx.compose.material.swipeable
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntOffset
import com.google.gson.Gson
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.ui.res.stringResource
import kotlin.math.roundToInt

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterialApi::class)
@Composable
fun SwipeWordScreen(
    questionData: WordConnotationQuestion,
    onPuzzleCompleted: (Int) -> Unit = {},
    onBackToHome: () -> Unit = {}
) {
    val allWords = remember {
        (questionData.positiveWords.map { it to true } + questionData.negativeWords.map { it to false })
            .shuffled()
            .toMutableStateList()
    }

    val currentWord = remember { mutableStateOf<Pair<String, Boolean>?>(allWords.firstOrNull()) }
    val timeLeft = remember { mutableStateOf(60) }
    val score = remember { mutableStateOf(0) }
    val showCompletionDialog = remember { mutableStateOf(false) }
    val context = LocalContext.current
    val currentIndex = remember { mutableStateOf(0) }
    val feedbackManager = rememberUnifiedFeedbackManager()
    val currentLevel = feedbackManager.getCurrentLevel()
    val streakInfo = feedbackManager.getStreakInfo()

    // ✅ NEW: Track completion state to prevent multiple completion calls
    var isCompleted by remember { mutableStateOf(false) }
    var isProcessingCompletion by remember { mutableStateOf(false) }

    val offsetX = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()

    // Timer
    LaunchedEffect(Unit) {
        while (timeLeft.value > 0 && allWords.isNotEmpty() && !isCompleted) {
            delay(1000)
            timeLeft.value -= 1
        }

        if (timeLeft.value == 0 && !isCompleted) {
            isCompleted = true
            feedbackManager.showFeedback(
                puzzleType = "wordconnotation",
                isCorrect = false,
                userAnswer = "Time up",
                correctAnswer = "Complete all words",
                timeSpent = 60000L, // Convert to milliseconds
                difficulty = "Easy",
                timeRemaining = 0,
                totalTime = 60,
                onComplete = {
                    onPuzzleCompleted(10)
                }
            )
        }
    }

    // Swipeable state
    val swipeState = rememberSwipeableState(0)
    val anchors = mapOf(0f to 0, -300f to -1, 300f to 1)

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.linearGradient(listOf(Color(0xFFF7E6D4), Color(0xFFEBD3C0)))),
        contentAlignment = Alignment.TopCenter
    ) {
        val compact = maxHeight < 600.dp
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .widthIn(max = 640.dp)
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = if (compact) 4.dp else 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Top Bar with Back Button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBackToHome) {
                    Icon(
                        Icons.Default.ArrowBack,
                        contentDescription = stringResource(R.string.back_to_home),
                        tint = RvInk
                    )
                }

                Column(
                    modifier = Modifier.weight(1f).padding(horizontal = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = questionData.topic.uppercase(),
                        color = RvInk,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (!compact) {
                        Text(
                            text = "Swipe the word left or right",
                            color = RvInk,
                            fontSize = 14.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text("${stringResource(R.string.score_label)}: ${score.value}", color = RvInk, fontWeight = FontWeight.Bold, fontSize = 14.sp, maxLines = 1)
                    Text("${stringResource(R.string.time_label)}: ${timeLeft.value}s", color = RvInk, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                }
            }

            Spacer(modifier = Modifier.height(if (compact) 4.dp else 8.dp))

            Divider(color = RvInk, thickness = 1.dp)

            // Main Word Card with swipe (takes the remaining space, centred)
            if (currentWord.value != null && !isCompleted) {
              Box(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentAlignment = Alignment.Center
              ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 96.dp, max = 180.dp)
                        .fillMaxHeight(0.9f)
                        .swipeable(
                            state = swipeState,
                            anchors = anchors,
                            thresholds = { _, _ -> FractionalThreshold(0.3f) },
                            orientation = Orientation.Horizontal
                        )
                        .offset { IntOffset(swipeState.offset.value.roundToInt(), 0) }
                        .background(Color(0xFF4D4036), shape = RoundedCornerShape(12.dp))
                        .padding(horizontal = 24.dp, vertical = 16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = currentWord.value!!.first,
                        color = RvOnTone,
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
              }

                // Check swipe state
                LaunchedEffect(swipeState.currentValue) {
                    if (swipeState.currentValue != 0 && !isProcessingCompletion) {
                        isProcessingCompletion = true

                        val isPositiveSwipe = swipeState.currentValue == 1
                        val expectedPositive = currentWord.value!!.second
                        val isCorrect = isPositiveSwipe == expectedPositive

                        if (isCorrect) {
                            score.value += 1
                        }

                        val wordText = currentWord.value!!.first
                        val userAction = if (isPositiveSwipe) "Positive" else "Negative"
                        val correctAction = if (expectedPositive) "Positive" else "Negative"

                        // Remove current word first
                        allWords.remove(currentWord.value)
                        val isLastWord = allWords.isEmpty()

                        // Show universal feedback
                        feedbackManager.showFeedback(
                            puzzleType = "wordGame",
                            isCorrect = isCorrect,
                            userAnswer = "Swiped ${if (isPositiveSwipe) "Positive" else "Negative"}",
                            correctAnswer = "$wordText is ${if (expectedPositive) "Positive" else "Negative"}",
                            timeSpent = (60 - timeLeft.value) * 1000L,
                            difficulty = "Medium",
                            timeRemaining = timeLeft.value,
                            totalTime = 60,
                            onComplete = {
                                // ✅ FIXED: Handle completion properly
                                if (isLastWord) {
                                    // This was the last word - complete the puzzle
                                    isCompleted = true
                                    onPuzzleCompleted(score.value * 10) // Better scoring based on actual performance
                                } else {
                                    // More words remain - continue to next word
                                    currentWord.value = allWords.firstOrNull()
                                    scope.launch {
                                        swipeState.snapTo(0)
                                        isProcessingCompletion = false
                                    }
                                }
                            }
                        )
                    }
                }
            } else if (isCompleted) {
                // ✅ Show completion state
                Box(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.CheckCircle,
                            contentDescription = stringResource(R.string.completed),
                            tint = RvSuccess,
                            modifier = Modifier.size(64.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = stringResource(R.string.puzzle_completed),
                            color = RvInk,
                            fontWeight = FontWeight.Bold,
                            fontSize = 24.sp
                        )
                        Text(
                            text = "${stringResource(R.string.final_score)}: ${score.value}",
                            color = RvInk,
                            fontSize = 18.sp
                        )
                    }
                }
            } else {
                // No current word but not completed - should not happen with fixed logic
                Box(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = RvInk)
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Loading next word...",
                            color = RvInk,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(if (compact) 4.dp else 16.dp))

            // Swipe Directions - only show if not completed
            if (!isCompleted) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("SWIPE LEFT", color = RvInk, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Text("(Negative)", color = RvInk, fontSize = 14.sp)
                        Icon(Icons.Default.ArrowBack, contentDescription = "Swipe left", tint = RvInk, modifier = Modifier.size(if (compact) 20.dp else 24.dp))
                    }

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("SWIPE RIGHT", color = RvInk, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Text("(Positive)", color = RvInk, fontSize = 14.sp)
                        Icon(Icons.Default.ArrowForward, contentDescription = "Swipe right", tint = RvInk, modifier = Modifier.size(if (compact) 20.dp else 24.dp))
                    }
                }
            }

            Spacer(modifier = Modifier.height(if (compact) 4.dp else 12.dp))

            // Hint - only show if not completed
            if (!isCompleted) {
                Text(
                    text = "💡 ${questionData.hint}",
                    fontSize = 14.sp,
                    fontStyle = FontStyle.Italic,
                    color = RvInk,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
            }
        }

        EnhancedUniversalFeedback(feedbackManager)
    }
}

data class WordConnotationQuestion(
    val topic: String,
    val positiveWords: List<String>,
    val negativeWords: List<String>,
    val hint: String
)