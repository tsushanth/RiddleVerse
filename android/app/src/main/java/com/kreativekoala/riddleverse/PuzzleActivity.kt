package com.kreativekoala.riddleverse

import android.app.Activity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kreativekoala.riddleverse.ui.theme.RiddleVerseTheme
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.graphics.graphicsLayer
import com.google.firebase.auth.FirebaseAuth
import com.google.gson.Gson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okio.IOException
import org.json.JSONObject
import com.kreativekoala.ratingkit.RatingKit



class PuzzleActivity : AppCompatActivity() {
    private val viewModel: PuzzleViewModel by viewModels()
    private var localScore = 0
    var startTime: Long = System.currentTimeMillis() // Make it public (remove private)
    private var score: Int = 0
    private var customPuzzleSetJson: String? = null

    override fun onDestroy() {
        super.onDestroy()

        try {
            // Force immediate cleanup
            System.gc()
            System.runFinalization()

            Log.d("ActivityCleanup", "Enhanced cleanup completed for ${this.javaClass.simpleName}")
        } catch (e: Exception) {
            Log.e("ActivityCleanup", "Error during enhanced cleanup", e)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        Log.d("PuzzleActivity", "🎯 onCreate called")

        // Log all intent extras
        val extras = intent.extras
        if (extras != null) {
            Log.d("PuzzleActivity", "📦 Intent extras received:")
            for (key in extras.keySet()) {
                val value = extras.get(key)
                Log.d("PuzzleActivity", "   $key: $value")
            }
        } else {
            Log.w("PuzzleActivity", "⚠️ No intent extras received!")
        }

        super.onCreate(savedInstanceState)

        Log.d("PuzzleActivity", "🔄 Calling viewModel.initializeFromIntent...")

        try {
            viewModel.initializeFromIntent(intent, this)
            Log.d("PuzzleActivity", "✅ viewModel.initializeFromIntent completed")

            // ADD: Check if intent still has group context after viewModel init
            Log.d("PuzzleActivity", "🔍 Intent after viewModel init:")
            Log.d("PuzzleActivity", "  sourceGroupId: ${intent.getStringExtra("sourceGroupId")}")
            Log.d("PuzzleActivity", "  sourceGroupName: ${intent.getStringExtra("sourceGroupName")}")
            Log.d("PuzzleActivity", "  puzzleType: ${intent.getStringExtra("puzzleType")}")
        } catch (e: Exception) {
            Log.e("PuzzleActivity", "❌ Error in viewModel.initializeFromIntent", e)
            finish()
            return
        }

        localScore = UserScoreStore.load(this)
        Log.d("PuzzleActivity", "💰 Local score loaded: $localScore")

        // FIXED: PuzzleActivity.kt - Update the completion LaunchedEffect in onCreate()

        setContent {
            RiddleVerseTheme {
                var showCompletion by remember { mutableStateOf(false) }

                if (showCompletion) {
                    val context = LocalContext.current

                    LaunchedEffect(Unit) {
                        val activity = context as? AppCompatActivity
                        val intent = Intent(context, DailyCompletionActivity::class.java).apply {
                            // Always include these for regular completion
                            putExtra("streakDays", (1..10).random())
                            putExtra("earnedPoints", listOf(100, 200, 250, 300, 500).random())

                            // 🚀 FIXED: Add session statistics and puzzle type
                            try {
                                val sessionStats = viewModel.getSessionStats()
                                val currentPuzzle = viewModel.getCurrentPuzzle()
                                val puzzleType = currentPuzzle?.puzzleType ?: "unknown"

                                Log.d("PuzzleActivity", "📊 Adding session stats to completion intent:")
                                Log.d("PuzzleActivity", "  Session Score: ${sessionStats.totalScore}")
                                Log.d("PuzzleActivity", "  Correct: ${sessionStats.correctAnswers}/${sessionStats.totalAnswers}")
                                Log.d("PuzzleActivity", "  Win Rate: ${(sessionStats.winRate * 100).toInt()}%")
                                Log.d("PuzzleActivity", "  Time: ${sessionStats.totalTimeSeconds}s")
                                Log.d("PuzzleActivity", "  Puzzle Type: $puzzleType")

                                putExtra("sessionStats", Gson().toJson(sessionStats))
                                putExtra("puzzleType", puzzleType)

                            } catch (e: Exception) {
                                Log.e("PuzzleActivity", "❌ Failed to add session stats", e)
                                // Continue without session stats - the completion screen will handle gracefully
                            }

                            // Add group context if available
                            if (activity != null) {
                                val isCustomPuzzle = activity.intent.getBooleanExtra("isCustomPuzzle", false)
                                val customPuzzleId = activity.intent.getStringExtra("customPuzzleId")

                                Log.d("PuzzleActivity", "🔍 Custom puzzle check:")
                                Log.d("PuzzleActivity", "  isCustomPuzzle: $isCustomPuzzle")
                                Log.d("PuzzleActivity", "  customPuzzleId: $customPuzzleId")
                                Log.d("PuzzleActivity", "  viewModel.isCustomPuzzleFlow: ${viewModel.isCustomPuzzleFlow}")
                                Log.d("PuzzleActivity", "  viewModel.currentScore: ${viewModel.getCurrentScore()}")

                                // ADD: Custom puzzle completion data
                                if (isCustomPuzzle && customPuzzleId != null) {
                                    val correctAnswers = viewModel.getCorrectAnswerCount()
                                    val totalQuestions = viewModel.totalQuestions
                                    val timeTaken = System.currentTimeMillis() - startTime
                                    val customPuzzleScore = calculateCustomPuzzleScore(correctAnswers, totalQuestions, timeTaken)

                                    Log.d("PuzzleActivity", "🎨 Adding custom puzzle completion data")
                                    putExtra("isCustomPuzzle", true)
                                    putExtra("customPuzzleId", customPuzzleId)
                                    putExtra("userId", FirebaseAuth.getInstance().currentUser?.email ?: "")
                                    putExtra("timeTaken", System.currentTimeMillis() - startTime)
                                    putExtra("finalScore", customPuzzleScore)
                                    Log.d("PuzzleActivity", "📊 Custom puzzle performance:")
                                    Log.d("PuzzleActivity", "  Correct answers: $correctAnswers/$totalQuestions")
                                    Log.d("PuzzleActivity", "  Time taken: ${timeTaken/1000}s")
                                    Log.d("PuzzleActivity", "  Performance score: $customPuzzleScore")
                                }

                                val sourceGroupId = activity.intent.getStringExtra("sourceGroupId")
                                val sourceGroupName = activity.intent.getStringExtra("sourceGroupName")
                                val puzzleTypeFromIntent = activity.intent.getStringExtra("puzzleType")
                                val currentPuzzleNumber = activity.intent.getIntExtra("currentPuzzleNumber", 0)

                                Log.d("PuzzleActivity", "🚀 Passing group context:")
                                Log.d("PuzzleActivity", "  sourceGroupId: $sourceGroupId")
                                Log.d("PuzzleActivity", "  sourceGroupName: $sourceGroupName")
                                Log.d("PuzzleActivity", "  puzzleType: $puzzleTypeFromIntent")
                                Log.d("PuzzleActivity", "  currentPuzzleNumber: $currentPuzzleNumber")

                                if (sourceGroupId != null) {
                                    putExtra("sourceGroupId", sourceGroupId)
                                    putExtra("sourceGroupName", sourceGroupName)
                                    putExtra("puzzleType", puzzleTypeFromIntent) // Use intent puzzle type or fallback
                                    putExtra("completedPuzzleCount", currentPuzzleNumber)
                                }
                            }
                        }

                        Log.d("PuzzleActivity", "🎯 Starting DailyCompletionActivity with all data")
                        context.startActivity(intent)
                        (context as? Activity)?.finish()
                    }

                    // Show completion message while navigating
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color(0xFF4CAF50)),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                "🎉 Puzzle Type Completed!",
                                color = Color.White,
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            CircularProgressIndicator(color = Color.White)
                        }
                    }
                } else {
                    // Show the main puzzle screen
                    PuzzleScreen(
                        viewModel = viewModel,
                        onComplete = {
                            val sourceGroupId = getIntent().getStringExtra("sourceGroupId")
                            val currentPuzzleNumber = getIntent().getIntExtra("currentPuzzleNumber", 0)
                            Log.d("PuzzleActivity", "🎉 Puzzle completed, showing completion screen, source group id: $sourceGroupId puzzle count $currentPuzzleNumber")

                            RatingKit.trackAction(this@PuzzleActivity)

                            if (sourceGroupId != null && currentPuzzleNumber >= 5) {
                                Log.d("PuzzleActivity", "🎯 Group puzzle completed: $currentPuzzleNumber/5 puzzles done")
                                showCompletion = true
                            } else if (sourceGroupId == null) {
                                Log.d("PuzzleActivity", "🎯 source group id null")
                                showCompletion = true
                            } else {
                                Log.d("PuzzleActivity", "🎯 else clause")
                            }
                        },
                        onBack = {
                            Log.d("PuzzleActivity", "🔙 Back button pressed")
                            finish()
                        }
                    )
                }
            }
        }
    }

    private fun calculateCustomPuzzleScore(correct: Int, total: Int, timeMs: Long): Int {
        val baseScore = correct * 10 // 10 points per correct answer
        val accuracyBonus = if (correct == total) 50 else 0 // Perfect score bonus
        val timeBonus = maxOf(0, (300000 - timeMs) / 10000).toInt() // Time bonus (faster = more points)

        return baseScore + accuracyBonus + timeBonus
    }



    val navigateHome: () -> Unit = {
        val intent = Intent(this@PuzzleActivity, HomeActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        startActivity(intent)
        finish()
    }
}

@Composable
fun LeaderboardView(topScores: List<LeaderboardEntry>) {
    Spacer(modifier = Modifier.height(32.dp))
    Column(modifier = Modifier.padding(16.dp)) {
        Text("🏆 Top 3 Scores", style = MaterialTheme.typography.titleMedium)
        topScores.take(3).forEachIndexed { index, entry ->
            Text("${index + 1}. ${entry.userId}: ${entry.score} pts in ${entry.timeTaken}s")
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QAPuzzleScreen(
    puzzle: Puzzle,
    onBack: () -> Unit = {},
    onSettings: () -> Unit = {},
    onContinue: () -> Unit = {},
    onCorrectAnswer: (inTimeBonus: Boolean) -> Unit
) {
    var userAnswer by remember { mutableStateOf("") }
    var timerValue by remember { mutableIntStateOf(30) }
    val context = LocalContext.current
    var expanded by remember { mutableStateOf(false) }
    var showShareDialog by remember { mutableStateOf(false) }
    var showHintDialog by remember { mutableStateOf(false) }
    var recipientEmail by remember { mutableStateOf("") }
    var recipientName by remember { mutableStateOf("") }
    val snackbarHostState = remember { SnackbarHostState() }
    var isCorrectAnswer by remember { mutableStateOf<Boolean?>(null) }
    var showResult by remember { mutableStateOf(false) }
    var showInTimeBonus by remember { mutableStateOf(false) }
    var isCheckingAnswer by remember { mutableStateOf(false) }


    Box(modifier = Modifier.fillMaxSize()) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .statusBarsPadding()
            .padding(32.dp)
            .imePadding()
    ) {
        // Top Bar
        Row(
            modifier = Modifier
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, contentDescription = stringResource(R.string.back))
            }
            Text(
                puzzle.puzzleType?.uppercase() ?: "PUZZLE",
                fontWeight = FontWeight.Bold
            )
            Box {
                IconButton(onClick = { expanded = true }) {
                    Icon(Icons.Default.MoreVert, contentDescription = "Settings")
                }

                DropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false }
                ) {
                    DropdownMenuItem(onClick = {
                        expanded = false
                        // 🟩 Trigger share logic
                        showShareDialog = true
                    }, text = { Text(stringResource(R.string.share_puzzle)) })
                }
            }
        }
        val totalTime = 30
        val progress by animateFloatAsState(
            targetValue = timerValue / totalTime.toFloat(),
            animationSpec = tween(durationMillis = 500)
        )

        LaunchedEffect(key1 = Unit) {
            while (timerValue > 0) {
                delay(1000L)
                timerValue -= 1
            }
        }

        // Timer
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(30.dp)
                .clip(RoundedCornerShape(50))
                .background(Color(0xFFB388FF).copy(alpha = 0.3f)),
            contentAlignment = Alignment.CenterEnd
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth(progress.coerceIn(0f, 1f))
                    .fillMaxHeight()
                    .background(Color(0xFFB388FF)),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Text(
                    "00:${if (timerValue < 10) "0$timerValue" else "$timerValue"}",
                    color = Color.White
                )
            }
            Icon(
                imageVector = Icons.Default.Timer,
                contentDescription = null,
                modifier = Modifier.padding(end = 8.dp),
                tint = Color.White
            )
        }


        Spacer(modifier = Modifier.height(24.dp))

        // Question Card
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF8A4DFF), shape = RoundedCornerShape(20.dp))
                .padding(16.dp)
        ) {
            Text("Q&A Puzzle", color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp)
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                puzzle.question,
                color = Color.White,
                fontSize = 16.sp
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Answer Input
        OutlinedTextField(
            value = userAnswer,
            onValueChange = { userAnswer = it },
            placeholder = { Text("Type your answer here") },
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(50))
                .border(2.dp, Color(0xFF8A4DFF), RoundedCornerShape(50))
                .background(Color.White),
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Color(0xFF8A4DFF),
                unfocusedBorderColor = Color(0xFF8A4DFF),
                focusedTextColor = Color.Black,
                unfocusedTextColor = Color.Black,
            )
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Continue Button
        // Replace the Continue Button onClick logic in your QAPuzzleScreen with this:

        Button(
            onClick = {
                if (puzzle == null || userAnswer.isEmpty()) {
                    Toast.makeText(context, "Please enter your answer or anything to skip", Toast.LENGTH_SHORT).show()
                } else {
                    isCheckingAnswer = true

                    // Use the extended AnswerMatcher for client-side checking
                    val matchResult = checkQAAnswer(
                        userAnswer = userAnswer,
                        correctAnswer = puzzle.answer,
                        puzzleType = puzzle.puzzleType,
                        puzzleId = puzzle.puzzleId
                    )

                    Log.d("QAPuzzleScreen", "Answer check result: ${matchResult.explanation}")
                    Log.d("QAPuzzleScreen", "  Match type: ${matchResult.matchType}")
                    Log.d("QAPuzzleScreen", "  Confidence: ${(matchResult.confidence * 100).toInt()}%")
                    Log.d("QAPuzzleScreen", "  User: '$userAnswer' vs Correct: '${puzzle.answer}'")

                    // Process the result
                    isCheckingAnswer = false
                    val isCorrect = matchResult.isMatch
                    isCorrectAnswer = isCorrect
                    showResult = true

                    val correctSound = R.raw.correct
                    val incorrectSound = R.raw.buzz
                    val inTime = timerValue > 0

                    if (isCorrect) {
                        playSound(context, correctSound)
                        showInTimeBonus = inTime
                        onCorrectAnswer(inTime)

                        Log.d("QAPuzzleScreen", "✅ Correct answer matched!")
                        Log.d("QAPuzzleScreen", "  Explanation: ${matchResult.explanation}")
                    } else {
                        playSound(context, incorrectSound)

                        Log.d("QAPuzzleScreen", "❌ Answer not matched")
                        Log.d("QAPuzzleScreen", "  Similarity: ${(matchResult.confidence * 100).toInt()}%")
                        Log.d("QAPuzzleScreen", "  Explanation: ${matchResult.explanation}")

                        // Provide helpful feedback based on confidence
                        val feedbackMessage = when {
                            matchResult.confidence > 0.7 -> "Very close! Check your spelling."
                            matchResult.confidence > 0.5 -> "You're on the right track!"
                            matchResult.confidence > 0.3 -> "Getting warmer..."
                            else -> "Not quite right."
                        }

                        // You could show this feedback in the UI if desired
                        Log.d("QAPuzzleScreen", "Feedback: $feedbackMessage")
                    }

                    Handler(Looper.getMainLooper()).postDelayed({
                        showResult = false
                        onContinue()
                    }, 2000)
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp),
            shape = RoundedCornerShape(25),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF8A65)),
            enabled = !isCheckingAnswer
        ) {
            Text(if (isCheckingAnswer) stringResource(R.string.checking) else stringResource(R.string.continue_label))
        }

        AnimatedVisibility(visible = showResult) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (isCorrectAnswer == true) {
                    Text(
                        text = "🎉 Correct!",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF4CAF50)
                    )
                    ConfettiAnimation()
                } else if (isCorrectAnswer == false) {
                    Text(
                        text = "❌ Incorrect!" + " The correct answer is: ${puzzle.answer}",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.Red
                    )
                }
            }
        }

        LaunchedEffect(showInTimeBonus) {
            if (showInTimeBonus) {
                delay(1000)
                showInTimeBonus = false
            }
        }

        AnimatedVisibility(
            visible = showInTimeBonus,
            enter = fadeIn(animationSpec = tween(500)) + scaleIn(),
            exit = fadeOut(animationSpec = tween(300))
        ) {
            Text(
                text = "+2 In-Time Bonus!",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF00C853),
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(top = 8.dp)
            )
        }


        Spacer(modifier = Modifier.height(12.dp))

        // Hint Button
        OutlinedButton(
            onClick = {
                if (!puzzle.hint.isNullOrBlank()) {
                    showHintDialog = true
                } else {
                    Toast.makeText(context, context.getString(R.string.no_hint_available), Toast.LENGTH_SHORT).show()
                }
            },
            modifier = Modifier.align(Alignment.CenterHorizontally),
            shape = RoundedCornerShape(50),
            border = BorderStroke(1.dp, Color(0xFFFF8A65))
        ) {
            Icon(Icons.Default.Lightbulb, contentDescription = null, tint = Color(0xFFFF8A65))
            Spacer(modifier = Modifier.width(4.dp))
            Text(stringResource(R.string.hint), color = Color(0xFFFF8A65))
        }
    }
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 16.dp)
        )
}

    if (showHintDialog) {
        AlertDialog(
            onDismissRequest = { showHintDialog = false },
            confirmButton = {
                Button(onClick = { showHintDialog = false }) { Text(stringResource(R.string.ok)) }
            },
            title = { Text(stringResource(R.string.hint)) },
            text = { Text("💡 ${puzzle.hint}") }
        )
    }

    if (showShareDialog) {
        AlertDialog(
            onDismissRequest = { showShareDialog = false },
            title = { Text(stringResource(R.string.share_puzzle)) },
            text = {
                Column {
                    OutlinedTextField(
                        value = recipientEmail,
                        onValueChange = { recipientEmail = it },
                        label = { Text(stringResource(R.string.recipient_email)) }
                    )
                    OutlinedTextField(
                        value = recipientName,
                        onValueChange = { recipientName = it },
                        label = { Text(stringResource(R.string.recipient_name)) }
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    showShareDialog = false
                    sharePuzzle(
                        puzzleId = puzzle.puzzleId,
                        recipientEmail = recipientEmail,
                        recipientName = recipientName,
                        senderId = FirebaseAuth.getInstance().currentUser?.displayName
                        ?: FirebaseAuth.getInstance().currentUser?.email
                        ?: "anonymous"
                    )
                }) {
                    Text(stringResource(R.string.share))
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showShareDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
}

@Composable
fun ConfettiAnimation() {
    val emojis = listOf("🎊", "🎉", "✨", "🎈", "🥳")
    val infiniteTransition = rememberInfiniteTransition(label = "confetti")

    val yOffset by infiniteTransition.animateFloat(
        initialValue = -30f,
        targetValue = 30f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "yOffset"
    )

    Row(
        modifier = Modifier
            .padding(top = 8.dp)
            .graphicsLayer { translationY = yOffset },
        horizontalArrangement = Arrangement.Center
    ) {
        emojis.shuffled().take(3).forEach { emoji ->
            Text(
                text = emoji,
                fontSize = 28.sp,
                modifier = Modifier.padding(horizontal = 4.dp)
            )
        }
    }
}

fun sharePuzzle(puzzleId: String, recipientEmail: String, recipientName: String, senderId: String) {
    val json = JSONObject().apply {
        put("puzzleId", puzzleId)
        put("recipientEmail", recipientEmail)
        put("recipientName", recipientName)
        put("senderId", senderId)
    }

    Log.d("SharePuzzle", "📤 Sending share request: $json")
    val request = Request.Builder()
        .url("https://puzzleverseai.com/share-riddle")
        .post(RequestBody.create("application/json".toMediaTypeOrNull(), json.toString()))
        .build()

    OkHttpClient().newCall(request).enqueue(object : Callback {
        override fun onFailure(call: Call, e: IOException) {
            Log.e("SharePuzzle", "❌ Failed to share puzzle", e)
        }

        override fun onResponse(call: Call, response: Response) {
            if (response.isSuccessful) {
                Log.d("SharePuzzle", "✅ Puzzle shared successfully")
            } else {
                Log.e("SharePuzzle", "❌ Share failed with code ${response.code}")
            }
        }
    })
}


fun fetchNextPuzzle(
    context: Context,
    type: String = "math",
    difficulty: String = "easy",
    screen: String = "Default",
    onSuccess: (Puzzle, String) -> Unit,
    onComplete: () -> Unit,
    onError: (String) -> Unit
) {
    CoroutineScope(Dispatchers.Main).launch {
        try {
            val puzzleQueueManager = PuzzleQueueManager.getInstance(context)
            val queuedPuzzle = puzzleQueueManager.getNextPuzzleForPlay(type, difficulty)

            if (queuedPuzzle != null) {
                val newPuzzle = queuedPuzzle.toPuzzle()
                val finalScreenType = when {
                    screen != "Default" -> screen
                    type == "antonyms" -> "Antonym Balloon Screen"
                    newPuzzle.options?.isNotEmpty() == true -> "MultipleChoice"
                    else -> "qa"
                }

                onSuccess(newPuzzle, finalScreenType)
            } else {
                onComplete() // No more puzzles available
            }
        } catch (e: Exception) {
            Log.e("fetchNextPuzzle", "Error getting puzzle from queue", e)
            onError("Unable to load puzzle: ${e.message}")
        }
    }
}





