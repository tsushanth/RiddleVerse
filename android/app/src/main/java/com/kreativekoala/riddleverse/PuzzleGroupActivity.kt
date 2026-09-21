package com.kreativekoala.riddleverse

import com.kreativekoala.riddleverse.ui.theme.*
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.auth.FirebaseAuth
import com.kreativekoala.riddleverse.ui.theme.RiddleVerseTheme
import androidx.activity.result.contract.ActivityResultContracts
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.Response
import okio.IOException
import org.json.JSONObject
import java.net.URLEncoder

class PuzzleGroupActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val groupId = intent.getStringExtra("groupId") ?: ""
        val groupName = intent.getStringExtra("groupName") ?: "Puzzle Group"
        val puzzleTypes = intent.getStringArrayExtra("puzzleTypes")?.toList() ?: emptyList()
        val completedTypes = intent.getStringArrayExtra("completedTypes")?.toSet() ?: emptySet()
        val difficulty = intent.getStringExtra("difficulty") ?: "Mixed"

        val startPuzzleActivityLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            Log.d("PuzzleGroupActivity", "🔄 Activity result received:")
            Log.d("PuzzleGroupActivity", "  resultCode: ${result.resultCode}")
            Log.d("PuzzleGroupActivity", "  RESULT_OK: ${Activity.RESULT_OK}")
            Log.d("PuzzleGroupActivity", "  result.data: ${result.data}")

            //if (result.resultCode == Activity.RESULT_OK) {
            Log.d("PuzzleGroupActivity", "✅ Refreshing completion status")
            val userId = FirebaseAuth.getInstance().currentUser?.email ?: ""
            val updatedCompletedTypes = GroupCompletionManager.getCompletedTypes(this@PuzzleGroupActivity, userId, groupId)

            Log.d("PuzzleGroupActivity", "🔄 Updated completed types: $updatedCompletedTypes")
                // Refresh completion status when returning from puzzle
                recreate() // This will refresh the entire screen
            /*} else {
                Log.d("PuzzleGroupActivity", "❌ No refresh needed - result code not OK")
            }*/
        }

        setContent {
            RiddleVerseTheme {
                PuzzleGroupScreen(
                    groupId = groupId,
                    groupName = groupName,
                    puzzleTypes = puzzleTypes,
                    initialCompletedTypes = completedTypes,
                    difficulty = difficulty,
                    onBack = { finish() },
                    onPuzzleTypeSelected = { puzzleType ->
                        // Use launcher instead of direct startActivity
                        val intent = Intent(this@PuzzleGroupActivity, StartPuzzleActivity::class.java).apply {
                            putExtra("puzzleType", puzzleType)
                            putExtra("title", puzzleType.replaceFirstChar { it.uppercaseChar() })
                            putExtra("sourceGroupId", groupId)
                            putExtra("sourceGroupName", groupName)
                        }
                        startPuzzleActivityLauncher.launch(intent)
                    }
                )
            }
        }
    }

}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PuzzleGroupScreen(
    groupId: String,
    groupName: String,
    puzzleTypes: List<String>,
    initialCompletedTypes: Set<String>,
    difficulty: String,
    onBack: () -> Unit,
    onPuzzleTypeSelected: (String) -> Unit
) {
    var completedTypes by remember { mutableStateOf(initialCompletedTypes) }
    var isLoading by remember { mutableStateOf(true) }
    var refreshTrigger by remember { mutableStateOf(0) }

    val context = LocalContext.current
    val userId = FirebaseAuth.getInstance().currentUser?.email ?: ""

    fun refreshCompletion() {
        val updated = GroupCompletionManager.getCompletedTypes(context, userId, groupId)
        completedTypes = updated
    }
    // Load completion status from backend
    LaunchedEffect(groupId, refreshTrigger) {
        try {
            refreshCompletion()
            isLoading = false
        } catch (e: Exception) {
            Log.e("PuzzleGroup", "Failed to load completion status", e)
            isLoading = false
        }
    }

    LaunchedEffect(Unit) {
        Log.d("PuzzleGroupScreen", "🎯 Screen resumed, refreshing completion")
        refreshCompletion()
    }


    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(groupName) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { paddingValues ->
        if (isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else {
            PuzzleGroupContent(
                modifier = Modifier.padding(paddingValues),
                groupId = groupId,
                puzzleTypes = puzzleTypes,
                completedTypes = completedTypes,
                difficulty = difficulty,
                onPuzzleTypeSelected = onPuzzleTypeSelected,
                onCompletionChanged = { newCompletedTypes ->
                    completedTypes = newCompletedTypes
                }
            )
        }
    }
}

@Composable
fun PuzzleGroupContent(
    modifier: Modifier = Modifier,
    groupId: String,
    puzzleTypes: List<String>,
    completedTypes: Set<String>,
    difficulty: String,
    onPuzzleTypeSelected: (String) -> Unit,
    onCompletionChanged: (Set<String>) -> Unit = {} // NEW: Callback for completion changes
) {
    val context = LocalContext.current
    val userId = FirebaseAuth.getInstance().currentUser?.email ?: ""
    var refreshTrigger by remember { mutableStateOf(0) }

    // Listen for completion changes when returning from StartPuzzleActivity
    LaunchedEffect(groupId) {
        // Check for completion updates when groupId changes or composable recomposes
        val updated = GroupCompletionManager.getCompletedTypes(context, userId, groupId)
        if (updated != completedTypes) {
            onCompletionChanged(updated)
        }
    }

    val isGroupCompleted = completedTypes.size == puzzleTypes.size
    val progressPercentage = if (puzzleTypes.isNotEmpty()) {
        (completedTypes.size.toFloat() / puzzleTypes.size.toFloat() * 100).toInt()
    } else 0

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Progress header
        item {
            GroupProgressCard(
                completed = completedTypes.size,
                total = puzzleTypes.size,
                progressPercentage = progressPercentage,
                isCompleted = isGroupCompleted,
                difficulty = difficulty
            )
        }

        // Instructions
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = stringResource(R.string.how_it_works),
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "• Each puzzle type contains 5 challenges\n• Complete all 5 to unlock the next puzzle type\n• Try different types to discover your favorites!",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // Puzzle types list with progress indicators
        itemsIndexed(puzzleTypes) { index, puzzleType ->
            val isCompleted = completedTypes.contains(puzzleType)
            val isUnlocked = index == 0 || completedTypes.contains(puzzleTypes.getOrNull(index - 1) ?: "")
            val isCurrent = isUnlocked && !isCompleted

            PuzzleTypeStepCard(
                index = index,
                puzzleType = puzzleType,
                isCompleted = isCompleted,
                isUnlocked = isUnlocked,
                isCurrent = isCurrent,
                isLast = index == puzzleTypes.size - 1,
                onPuzzleClick = {
                    if (isUnlocked) {
                        onPuzzleTypeSelected(puzzleType)
                    }
                }
            )
        }

        // Completion celebration
        if (isGroupCompleted) {
            item {
                CompletionCard()
            }
        }
    }
}

@Composable
fun GroupProgressCard(
    completed: Int,
    total: Int,
    progressPercentage: Int,
    isCompleted: Boolean,
    difficulty: String
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isCompleted) RvSuccess.copy(alpha = 0.1f) else MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = stringResource(R.string.progress),
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "$completed/$total puzzle types completed",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                if (isCompleted) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = "Completed",
                        tint = RvSuccess,
                        modifier = Modifier.size(32.dp)
                    )
                } else {
                    Text(
                        text = "$progressPercentage%",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            LinearProgressIndicator(
                progress = progressPercentage / 100f,
                modifier = Modifier.fillMaxWidth(),
                color = if (isCompleted) RvSuccess else MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Difficulty: $difficulty • Each type has 5 puzzles",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun PuzzleTypeStepCard(
    index: Int,
    puzzleType: String,
    isCompleted: Boolean,
    isUnlocked: Boolean,
    isCurrent: Boolean,
    isLast: Boolean,
    onPuzzleClick: () -> Unit
) {
    // Get display name and icon for puzzle type (reuse from categories)
    val displayInfo = getPuzzleTypeDisplayInfo(puzzleType)

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Progress indicator column (like in the image)
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.width(24.dp)
        ) {
            // Circle indicator
            Box(
                modifier = Modifier
                    .size(20.dp)
                    .background(
                        color = when {
                            isCompleted -> RvSuccess
                            isCurrent -> MaterialTheme.colorScheme.primary
                            isUnlocked -> MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
                            else -> RvOutline
                        },
                        shape = RoundedCornerShape(10.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                when {
                    isCompleted -> {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = "Completed",
                            tint = RvInk,
                            modifier = Modifier.size(12.dp)
                        )
                    }
                    isCurrent -> {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .background(RvSurfaceRaised, RoundedCornerShape(4.dp))
                        )
                    }
                    isUnlocked -> {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(3.dp))
                        )
                    }
                    else -> {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = "Locked",
                            tint = RvInkSoft,
                            modifier = Modifier.size(10.dp)
                        )
                    }
                }
            }

            // Connecting line (except for last item)
            if (!isLast) {
                Box(
                    modifier = Modifier
                        .width(2.dp)
                        .height(16.dp)
                        .background(RvOutline)
                )
            }
        }

        Spacer(modifier = Modifier.width(16.dp))

        // Puzzle type card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(enabled = isUnlocked) { onPuzzleClick() },
            elevation = CardDefaults.cardElevation(
                defaultElevation = if (isCurrent) 4.dp else 2.dp
            ),
            colors = CardDefaults.cardColors(
                containerColor = when {
                    isCompleted -> RvSuccess.copy(alpha = 0.1f)
                    isCurrent -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                    isUnlocked -> MaterialTheme.colorScheme.surface
                    else -> MaterialTheme.colorScheme.surface.copy(alpha = 0.6f)
                }
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = displayInfo.icon,
                        contentDescription = displayInfo.title,
                        tint = if (isUnlocked) MaterialTheme.colorScheme.primary else RvInkSoft,
                        modifier = Modifier.size(24.dp)
                    )

                    Spacer(modifier = Modifier.width(12.dp))

                    Column {
                        Text(
                            text = displayInfo.title,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (isUnlocked) MaterialTheme.colorScheme.onSurface else RvInkSoft
                        )

                        Text(
                            text = when {
                                isCompleted -> "✓ All 5 puzzles completed"
                                isCurrent -> "← Next to unlock"
                                isUnlocked -> "5 puzzles available"
                                else -> "🔒 Complete previous to unlock"
                            },
                            fontSize = 12.sp,
                            color = when {
                                isCompleted -> RvSuccess
                                isCurrent -> MaterialTheme.colorScheme.primary
                                isUnlocked -> MaterialTheme.colorScheme.onSurfaceVariant
                                else -> RvInkSoft
                            }
                        )
                    }
                }

                if (isUnlocked) {
                    IconButton(
                        onClick = onPuzzleClick,
                        modifier = Modifier
                            .size(40.dp)
                            .background(
                                color = if (isCompleted) {
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                                } else {
                                    MaterialTheme.colorScheme.primary
                                },
                                shape = RoundedCornerShape(20.dp)
                            )
                    ) {
                        Icon(
                            imageVector = if (isCompleted) Icons.Default.Replay else Icons.Default.PlayArrow,
                            contentDescription = if (isCompleted) stringResource(R.string.play_again) else stringResource(R.string.start),
                            tint = if (isCompleted) MaterialTheme.colorScheme.primary else RvOnTone,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun CompletionCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = RvSuccess.copy(alpha = 0.1f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Default.EmojiEvents,
                contentDescription = "Completed",
                tint = RvSun,
                modifier = Modifier.size(48.dp)
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "🎉 Group Completed!",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Amazing! You've mastered all puzzle types in this group. Ready for the next challenge?",
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("🏆", fontSize = 16.sp)
                Text("⭐", fontSize = 16.sp)
                Text("🎯", fontSize = 16.sp)
            }
        }
    }
}

// Helper function to get display info for puzzle types
data class PuzzleTypeDisplayInfo(
    val title: String,
    val icon: ImageVector
)

fun getPuzzleTypeDisplayInfo(puzzleType: String): PuzzleTypeDisplayInfo {
    return when (puzzleType.lowercase()) {
        "math" -> PuzzleTypeDisplayInfo("Math", Icons.Default.Calculate)
        "storypuzzle" -> PuzzleTypeDisplayInfo("Story Puzzle", Icons.Default.MenuBook)
        "anagram" -> PuzzleTypeDisplayInfo("Anagram", Icons.Default.TextFields)
        "antonyms" -> PuzzleTypeDisplayInfo("Antonyms", Icons.Default.BubbleChart)
        "synonyms" -> PuzzleTypeDisplayInfo("Synonyms", Icons.Default.Link)
        "memorysquares" -> PuzzleTypeDisplayInfo("Visual Memory", Icons.Default.GridView)
        "memorystory" -> PuzzleTypeDisplayInfo("Audio Memory", Icons.Default.VolumeUp)
        "trivia" -> PuzzleTypeDisplayInfo("Trivia", Icons.Default.Quiz)
        "average" -> PuzzleTypeDisplayInfo("Average", Icons.Default.BarChart)
        "division" -> PuzzleTypeDisplayInfo("Division", Icons.Default.Percent)
        "mathestimation" -> PuzzleTypeDisplayInfo("Estimation", Icons.Default.TrendingUp)
        "percentages" -> PuzzleTypeDisplayInfo("Percentage", Icons.Default.Percent)
        "discounts" -> PuzzleTypeDisplayInfo("Discounts", Icons.Default.LocalOffer)
        "purchasing" -> PuzzleTypeDisplayInfo("Purchasing", Icons.Default.CreditCard)
        "conversion" -> PuzzleTypeDisplayInfo("Conversion", Icons.Default.SwapHoriz)
        "connotationwords" -> PuzzleTypeDisplayInfo("Word Connotations", Icons.Default.Psychology)
        "subtraction" -> PuzzleTypeDisplayInfo("Subtraction", Icons.Default.Remove)
        "mathtipping" -> PuzzleTypeDisplayInfo("Tip Calculation", Icons.Default.RestaurantMenu)
        else -> PuzzleTypeDisplayInfo(puzzleType.replaceFirstChar { it.uppercaseChar() }, Icons.Default.Extension)
    }
}

// API function to fetch group completion status
fun fetchGroupCompletionStatus(
    userId: String,
    groupId: String,
    onSuccess: (Set<String>) -> Unit,
    onError: (String) -> Unit
) {
    val encodedUserId = URLEncoder.encode(userId, "UTF-8")
    val url = "https://puzzleverseai.com/group-completion-status?userId=$encodedUserId&groupId=$groupId"
    val client = OkHttpClient()
    val request = Request.Builder().url(url).build()

    client.newCall(request).enqueue(object : Callback {
        override fun onFailure(call: Call, e: IOException) {
            Handler(Looper.getMainLooper()).post {
                // If no completion data found, start fresh
                onSuccess(emptySet())
            }
        }

        override fun onResponse(call: Call, response: Response) {
            try {
                val body = response.body?.string()
                if (!response.isSuccessful || body == null) {
                    Handler(Looper.getMainLooper()).post {
                        onSuccess(emptySet())
                    }
                    return
                }

                val json = JSONObject(body)
                val completedArray = json.getJSONArray("completedTypes")
                val completedTypes = mutableSetOf<String>()
                for (i in 0 until completedArray.length()) {
                    completedTypes.add(completedArray.getString(i))
                }

                Handler(Looper.getMainLooper()).post {
                    onSuccess(completedTypes)
                }
            } catch (e: Exception) {
                Handler(Looper.getMainLooper()).post {
                    onError("Failed to parse completion status")
                }
            }
        }
    })
}

fun markPuzzleTypeCompleted(
    context: Context, // Add context parameter
    userId: String,
    groupId: String?,
    puzzleType: String
) {
    if (groupId == null || userId.isEmpty()) return

    try {
        GroupCompletionManager.markTypeCompleted(context, userId, groupId, puzzleType)
        Log.d("GroupCompletion", "Puzzle type '$puzzleType' marked as completed successfully")
    } catch (e: Exception) {
        Log.e("GroupCompletion", "Failed to mark puzzle type as completed", e)
    }
}