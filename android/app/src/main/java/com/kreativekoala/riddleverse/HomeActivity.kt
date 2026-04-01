package com.kreativekoala.riddleverse

import android.annotation.TargetApi
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import okhttp3.*
import okio.IOException
import org.json.JSONObject
import com.google.gson.Gson
import androidx.compose.ui.platform.LocalContext
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.URLEncoder
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import com.kreativekoala.riddleverse.ui.theme.RiddleVerseTheme
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.navigation.NavHostController
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import androidx.navigation.compose.rememberNavController
import com.kreativekoala.riddleverse.QuizCategories.getAllAvailablePuzzleTypes
import java.util.TimeZone
import androidx.compose.foundation.layout.width
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.rememberCoroutineScope
import com.kreativekoala.riddleverse.QuizCategories.getSmartFallbackRecentPuzzles
import com.kreativekoala.riddleverse.QuizCategories.isPuzzleTypeNew
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch


enum class PuzzleSort(val displayName: String) {
    NEWEST("Newest"),
    OLDEST("Oldest"),
    MOST_PLAYED("Most Played"),
    HIGHEST_RATED("Highest Rated"),
    ALPHABETICAL("A-Z")
}

enum class CustomPuzzleTab(val displayName: String) {
    BROWSE("Browse"),
    YOUR_PUZZLES("Your Puzzles"),
    FEATURED("Featured")
}

val dailyTopicColors = listOf(
    Color(0xFF6B73FF), // Soft Purple-Blue
    Color(0xFF50C878), // Emerald Green
    Color(0xFFFF8C69), // Salmon
    Color(0xFF87CEEB)  // Sky Blue
)

// Tab Selection Enum
enum class TabSelection(
    val title: String,
    val icon: ImageVector,
    val hasNewFeatures: Boolean = false
) {
    FOR_YOU("For You", Icons.Default.Star),
    CATEGORIES("Categories", Icons.Default.GridView),
    CREATE_GAME("Games", Icons.Default.SportsEsports),
    CUSTOM_PUZZLES("Puzzles", Icons.Default.Extension),
    SETTINGS("Settings", Icons.Default.Settings)
}

object RecentPuzzlesManager {
    private const val PREFS_NAME = "recent_puzzles"
    private const val KEY_RECENT_TYPES = "recent_types"
    private const val KEY_DAILY_QUICK_ACTIONS = "daily_quick_actions"
    private const val KEY_LAST_DAILY_UPDATE = "last_daily_update"
    private const val MAX_RECENT = 5

    fun getDailyQuickActions(context: Context): List<RecentPuzzleType> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val today = getCurrentDateString()
        val lastUpdate = prefs.getString(KEY_LAST_DAILY_UPDATE, "")

        return if (lastUpdate == today) {
            // Use cached daily actions
            val cachedTypes = prefs.getStringSet(KEY_DAILY_QUICK_ACTIONS, emptySet()) ?: emptySet()
            cachedTypes.map { RecentPuzzleType(it, 0) }
        } else {
            // Generate new daily actions based on yesterday's behavior
            val newDailyActions = generateDailyActions(context)
            prefs.edit()
                .putStringSet(KEY_DAILY_QUICK_ACTIONS, newDailyActions.map { it.puzzleType }.toSet())
                .putString(KEY_LAST_DAILY_UPDATE, today)
                .apply()
            newDailyActions
        }
    }

    private fun getCurrentDateString(): String {
        return SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
    }

    private fun generateDailyActions(context: Context): List<RecentPuzzleType> {
        val recentPuzzles = getRecentPuzzleTypes(context)
        val fallback = getSmartFallbackRecentPuzzles()

        return if (recentPuzzles.size >= 3) {
            recentPuzzles.take(3)
        } else {
            (recentPuzzles + fallback.map { RecentPuzzleType(it, 0) }).take(3)
        }
    }



    fun addRecentPuzzle(context: Context, puzzleType: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val recent = getRecentPuzzleTypes(context).toMutableList()

        // Remove if exists to avoid duplicates
        recent.removeAll { it.puzzleType == puzzleType }

        // Add to front
        recent.add(0, RecentPuzzleType(puzzleType, System.currentTimeMillis()))

        // Keep only last 5
        val recentTypes = recent.take(MAX_RECENT).map { it.puzzleType }

        prefs.edit()
            .putStringSet(KEY_RECENT_TYPES, recentTypes.toSet())
            .apply()

        Log.d("RecentPuzzles", "ðŸ“± Added $puzzleType to recent puzzles: $recentTypes")
    }

    fun getRecentPuzzleTypes(context: Context): List<RecentPuzzleType> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val types = prefs.getStringSet(KEY_RECENT_TYPES, emptySet()) ?: emptySet()
        return types.map { RecentPuzzleType(it, 0) } // Simplified timestamp for now
    }
}

@Composable
fun HomeScreen(navController: NavHostController,
               handleDailyQuizSelected: (String) -> Unit) {
    var selectedTab by remember { mutableStateOf(TabSelection.FOR_YOU) }
    var gameCreationInitialTab by remember { mutableIntStateOf(1) }
    val gameGenerationVM: GameGenerationViewModel = androidx.lifecycle.viewmodel.compose.viewModel()

    // Simplified state - remove complex manager states
    var topics by remember { mutableStateOf<List<String>>(emptyList()) }
    var allQuizzes by remember { mutableStateOf(listOf<CustomPuzzle>()) }
    var yourQuizzes by remember { mutableStateOf(listOf<CustomPuzzle>()) }
    var featuredPuzzles by remember { mutableStateOf(listOf<CustomPuzzle>()) }
    var trendingPuzzles by remember { mutableStateOf(listOf<CustomPuzzle>()) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var currentOffset by remember { mutableStateOf(0) }
    var isLoadingMore by remember { mutableStateOf(false) }

    // Simplified puzzle groups state
    var puzzleGroups by remember { mutableStateOf<List<PuzzleGroup>>(emptyList()) }

    val context = LocalContext.current
    val ratingManager = remember { AppRatingManager(context) }
    var showRatingDialog by remember { mutableStateOf(false) }

    val handlePuzzleTypeSelected: (String) -> Unit = { puzzleType ->
        // Track as recent puzzle
        RecentPuzzlesManager.addRecentPuzzle(context, puzzleType)
        AnalyticsManager.getInstance()?.trackPuzzleEventToFirebase(
            eventName = "puzzle_start",
            puzzleType = puzzleType,
            additionalParams = mapOf(
                "source" to "for_you_tab",
                "context" to "featured_or_recent"
            )
        )

        // Navigate to puzzle
        val intent = Intent(context, StartPuzzleActivity::class.java).apply {
            putExtra("puzzleType", puzzleType)
            putExtra("title", puzzleType.replaceFirstChar { it.uppercaseChar() })
        }
        context.startActivity(intent)
    }

    // Handler for random puzzle selection
    val handleRandomPuzzleSelected: () -> Unit = {
        val availablePuzzleTypes = getAllAvailablePuzzleTypes()
        val randomPuzzleType = availablePuzzleTypes.random()

        AnalyticsManager.getInstance()?.track(AnalyticsEvent("random_puzzle_selected", mapOf(
            "selected_puzzle_type" to randomPuzzleType,
            "source" to "for_you_quick_actions",
            "available_options" to availablePuzzleTypes.size
        )))
        AnalyticsManager.getInstance()?.trackPuzzleEventToFirebase(
            eventName = "puzzle_start",
            puzzleType = randomPuzzleType,
            additionalParams = mapOf(
                "source" to "random_challenge",
                "available_options" to availablePuzzleTypes.size
            )
        )

        handlePuzzleTypeSelected(randomPuzzleType)
    }

    LaunchedEffect(Unit) {
        if (ratingManager.shouldShowRatingPrompt()) {
            showRatingDialog = true
            ratingManager.markRatingPromptShown()
            Log.d("HomeScreen", "Rating dialog triggered based on preferences")
        }

        // Simplified group loading
        try {
            val userId = FirebaseAuth.getInstance().currentUser?.email ?: ""
            puzzleGroups = HardcodedPuzzleGroups.getAllGroups(context, userId)
            Log.d("HomeScreen", "Loaded ${puzzleGroups.size} puzzle groups")
        } catch (e: Exception) {
            Log.e("HomeScreen", "Failed to load puzzle groups", e)
        }
    }

    // Load custom puzzles data when component mounts
    LaunchedEffect(Unit) {
        isLoading = true

        try {
            fetchFeaturedPuzzles(
                limit = 5,
                onResult = { featured ->
                    featuredPuzzles = featured
                    Log.d("HomeScreen", "Featured puzzles loaded: ${featured.size} items")
                }
            )

            fetchEnhancedCustomPuzzles(
                context = context,
                userEmail = null,
                onResult = { newQuizzes ->
                    val uniqueQuizzes = newQuizzes.distinctBy { it.id }
                    allQuizzes = uniqueQuizzes
                    trendingPuzzles = uniqueQuizzes
                        .filter { it.playCount > 0 }
                        .sortedByDescending { it.playCount * it.averageRating }
                        .take(10)
                    Log.d("HomeScreen", "Enhanced puzzles loaded: ${uniqueQuizzes.size} unique items")
                }
            )

            fetchEnhancedCustomPuzzles(
                context = context,
                userEmail = FirebaseAuth.getInstance().currentUser?.displayName,
                onResult = { newQuizzes ->
                    val uniqueQuizzes = newQuizzes.distinctBy { it.id }
                    yourQuizzes = uniqueQuizzes
                    Log.d("HomeScreen", "Your enhanced puzzles loaded: ${uniqueQuizzes.size} unique items")
                }
            )

        } catch (e: Exception) {
            Log.e("HomeScreen", "Failed to load initial data", e)
        } finally {
            isLoading = false
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
    ) {
        // Header with greeting and user info
        HeaderSection()

        // Web/iOS promo banner (dismissable)
        WebPromoBanner()

        // Main Content Area
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            when (selectedTab) {
                TabSelection.FOR_YOU -> ForYouTab(
                    onPuzzleTypeSelected = handlePuzzleTypeSelected,
                    onRefresh = {
                        Log.d("HomeScreen", "Manual refresh triggered")
                        // Refresh will be handled in ForYouTab itself
                    },
                    onNavigateToCreate = {
                        gameCreationInitialTab = 0
                        selectedTab = TabSelection.CREATE_GAME
                    }
                )
                TabSelection.CATEGORIES -> CategoriesTab(
                    topics = topics,
                    onCategorySelected = { category ->
                        val intent = Intent(context, StartPuzzleActivity::class.java).apply {
                            putExtra("puzzleType", category)
                            putExtra("title", category.replaceFirstChar { it.uppercaseChar() })
                        }
                        context.startActivity(intent)
                    }
                )
                TabSelection.CREATE_GAME -> {
                    LaunchedEffect(Unit) { gameGenerationVM.isOnCreateTab = true }
                    androidx.compose.runtime.DisposableEffect(Unit) {
                        onDispose { gameGenerationVM.isOnCreateTab = false }
                    }
                    GameCreationScreen(context = context, generationVM = gameGenerationVM, initialTab = gameCreationInitialTab)
                }
                TabSelection.CUSTOM_PUZZLES -> EnhancedCustomPuzzlesTabWithSearch(
                    allPuzzles = allQuizzes,
                    yourPuzzles = yourQuizzes,
                    featuredPuzzles = featuredPuzzles,
                    trendingPuzzles = trendingPuzzles,
                    isLoading = isLoading,
                    isLoadingMore = isLoadingMore,
                    onCreatePuzzle = {
                        context.startActivity(Intent(context, CreatePuzzleActivity::class.java))
                    },
                    onPuzzleSelected = { puzzle ->
                        val intent = Intent(context, StartPuzzleActivity::class.java).apply {
                            putExtra("puzzleType",  puzzle.format.lowercase())
                            putExtra("title", puzzle.name)
                            putExtra("customPuzzleId", puzzle.id)
                            putExtra("isCustomPuzzle", true)
                        }
                        context.startActivity(intent)
                    },
                    onLoadMore = { isYourPuzzles ->
                        if (!isLoadingMore) {
                            isLoadingMore = true
                            val currentList = if (isYourPuzzles) yourQuizzes else allQuizzes
                            currentOffset = currentList.size

                            fetchEnhancedCustomPuzzles(
                                context = context,
                                userEmail = if (isYourPuzzles) FirebaseAuth.getInstance().currentUser?.displayName else null,
                                offset = currentOffset
                            ) { newPuzzles ->
                                if (newPuzzles.isNotEmpty()) {
                                    if (isYourPuzzles) {
                                        yourQuizzes = yourQuizzes + newPuzzles
                                    } else {
                                        allQuizzes = allQuizzes + newPuzzles
                                    }
                                }
                                isLoadingMore = false
                            }
                        }
                    },
                    onRefresh = {
                        isLoading = true

                        // Reset both lists
                        allQuizzes = emptyList()
                        yourQuizzes = emptyList()
                        featuredPuzzles = emptyList()
                        trendingPuzzles = emptyList()
                        currentOffset = 0

                        fetchEnhancedCustomPuzzles(
                            context = context,
                            userEmail = null,
                            onResult = { newQuizzes ->
                                val uniqueQuizzes = newQuizzes.distinctBy { it.id }
                                allQuizzes = uniqueQuizzes
                                trendingPuzzles = uniqueQuizzes
                                    .filter { it.playCount > 0 }
                                    .sortedByDescending { it.playCount * it.averageRating }
                                    .take(10)
                                Log.d("HomeScreen", "Enhanced puzzles refreshed: ${uniqueQuizzes.size} unique items")
                            }
                        )

                        // Fetch user's enhanced custom puzzles
                        fetchEnhancedCustomPuzzles(
                            context = context,
                            userEmail = FirebaseAuth.getInstance().currentUser?.displayName,
                            onResult = { newQuizzes ->
                                val uniqueQuizzes = newQuizzes.distinctBy { it.id }
                                yourQuizzes = uniqueQuizzes
                                Log.d("HomeScreen", "Your enhanced puzzles refreshed: ${uniqueQuizzes.size} unique items")
                            }
                        )

                        // Refresh featured puzzles
                        fetchFeaturedPuzzles(
                            limit = 5,
                            onResult = { featured ->
                                featuredPuzzles = featured
                                isLoading = false
                            }
                        )
                    },
                    onViewLeaderboard = { puzzleId ->
                        Log.d("HomeScreen", "Opening leaderboard for puzzle: $puzzleId")

                        val userId = FirebaseAuth.getInstance().currentUser?.email
                        fetchPuzzleLeaderboard(
                            puzzleId = puzzleId,
                            onResult = { leaderboard ->
                                if (leaderboard != null) {
                                    val intent = Intent(context, PuzzleLeaderboardActivity::class.java).apply {
                                        putExtra("puzzleId", puzzleId)
                                        putExtra("puzzleName", leaderboard.puzzleName ?: "Custom Puzzle")
                                        putExtra("puzzleCreator", leaderboard.puzzleCreator ?: "Unknown")
                                        putExtra("totalPlayers", leaderboard.totalPlayers)
                                        putExtra("userRank", leaderboard.userRank ?: 0)
                                        putExtra("userScore", leaderboard.userScore ?: 0)
                                        putExtra("isEmpty", leaderboard.isEmpty)

                                        if (leaderboard.statistics != null) {
                                            val gson = Gson()
                                            putExtra("statisticsJson", gson.toJson(leaderboard.statistics))
                                        }

                                        val gson = Gson()
                                        putExtra("topPlayersJson", gson.toJson(leaderboard.topPlayers))
                                        leaderboard.statistics?.let { stats ->
                                            putExtra("statisticsJson", gson.toJson(stats))
                                        }
                                    }
                                    context.startActivity(intent)
                                } else {
                                    Toast.makeText(
                                        context,
                                        "Could not load leaderboard for this puzzle",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            }
                        )
                    }
                )
                TabSelection.SETTINGS -> SettingsTab(
                    onNavigateToLeaderboard = {
                        context.startActivity(Intent(context, LeaderboardActivity::class.java))
                    },
                    onNavigateToTopics = {
                        context.startActivity(Intent(context, PreferredTopicsActivity::class.java))
                    },
                    onNavigateToAchievements = {
                        context.startActivity(Intent(context, TierProgressDashboardActivity::class.java))
                    },
                    onSignOut = {
                        FirebaseAuth.getInstance().signOut()
                        val intent = Intent(context, WelcomeActivity::class.java)
                        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                        context.startActivity(intent)
                    },
                    onSignIn = {
                        context.startActivity(Intent(context, AuthActivity::class.java))
                    },
                    onSignUp = {
                        context.startActivity(Intent(context, AuthActivity::class.java))
                    },
                    onShowRatingDialog = { showRatingDialog = true }
                )
            }
        }

        // Bottom Tab Bar
        BottomTabBar(
            selectedTab = selectedTab,
            onTabSelected = { selectedTab = it },
            isGenerating = gameGenerationVM.isGenerating
        )
    }

    // Game generation completion banner (overlaid on top)
    if (gameGenerationVM.showCompletionBanner) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            GameCompletionBanner(
                onTap = {
                    gameGenerationVM.openCompletedGame()
                    selectedTab = TabSelection.CREATE_GAME
                },
                onDismiss = { gameGenerationVM.dismissBanner() }
            )
        }
    }

    if (showRatingDialog) {
        RatingFlowDialog(
            onDismiss = {
                showRatingDialog = false
                Log.d("HomeScreen", "Rating dialog dismissed")
            },
            onCompleted = {
                showRatingDialog = false
                Log.d("HomeScreen", "Rating dialog completed")
            }
        )
    }

    // Show loading state
    if (isLoading) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator()
        }
    }

    // Show error message
    errorMessage?.let { message ->
        LaunchedEffect(message) {
            Toast.makeText(context, "Error: $message", Toast.LENGTH_LONG).show()
        }
    }
}



@Composable
fun ErrorGameScreen(
    error: String,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    gameName: String
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            Icons.Default.Error,
            contentDescription = stringResource(R.string.game_error),
            modifier = Modifier.size(64.dp),
            tint = Color.Red
        )

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = stringResource(R.string.game_error),
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = error,
            fontSize = 14.sp,
            color = Color.Gray,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(32.dp))

        Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            OutlinedButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, contentDescription = stringResource(R.string.back))
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.go_back))
            }

            Button(
                onClick = onRetry,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF2196F3)
                )
            ) {
                Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.retry))
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.try_again))
            }
        }
    }
}

@Composable
fun HeaderSection() {
    val context = LocalContext.current

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Default.Star, contentDescription = null, tint = Color.Gray)

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            GreetingBasedOnTime()
            Text(
                text = FirebaseAuth.getInstance().currentUser?.displayName ?: "Guest",
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp
            )
            UserScoreView()
        }

        IconButton(onClick = {
            context.startActivity(android.content.Intent(context, LeaderboardActivity::class.java))
        }) {
            Icon(
                imageVector = Icons.Default.Leaderboard,
                contentDescription = "Leaderboard",
                tint = Color(0xFFFF8C00),
                modifier = androidx.compose.ui.Modifier.size(26.dp)
            )
        }
    }
}

// Reactive CategoriesTab that shows puzzles immediately as data becomes available

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CategoriesTab(
    topics: List<String>,
    onCategorySelected: (String) -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    // Use only PuzzleQueueManager - simplified approach
    val puzzleQueueManager = remember { PuzzleQueueManager.getInstance(context) }

    // State for categories and availability
    var selectedFilter by remember { mutableStateOf("All") }
    var availablePuzzleTypes by remember { mutableStateOf<Set<String>>(emptySet()) }

    // Get all categories - always show them all
    val allCategories = QuizCategories.allCategories

    // Initialize with available puzzle types
    LaunchedEffect(Unit) {
        availablePuzzleTypes = puzzleQueueManager.getAvailablePuzzleTypes()

        // Listen for availability changes
        puzzleQueueManager.addAvailabilityListener { puzzleType, isAvailable ->
            if (isAvailable) {
                availablePuzzleTypes = availablePuzzleTypes + puzzleType
            } else {
                availablePuzzleTypes = availablePuzzleTypes - puzzleType
            }
        }
    }

    // Filter categories based on selected filter
    val filteredCategories = when (selectedFilter) {
        "All" -> allCategories
        "Ready" -> allCategories.filter { category ->
            availablePuzzleTypes.contains(category.category)
        }
        "On Demand" -> allCategories.filter { category ->
            !isOfflinePuzzleType(category.category) &&
                    !availablePuzzleTypes.contains(category.category)
        }
        "Offline" -> allCategories.filter { isOfflinePuzzleType(it.category) }
        else -> allCategories.filter { it.filterCategory == selectedFilter }
    }

    // Filter options with counts
    val filterOptions = listOf(
        "All" to allCategories.size,
        "Ready" to allCategories.count { category ->
            availablePuzzleTypes.contains(category.category)
        },
        "On Demand" to allCategories.count { category ->
            !isOfflinePuzzleType(category.category) &&
                    !availablePuzzleTypes.contains(category.category)
        },
        "Offline" to allCategories.count { isOfflinePuzzleType(it.category) },
        "Math" to allCategories.count { it.filterCategory == "Math" },
        "English" to allCategories.count { it.filterCategory == "English" },
        "Memory" to allCategories.count { it.filterCategory == "Memory" },
        "Visual" to allCategories.count { it.filterCategory == "Visual" },
        "General" to allCategories.count { it.filterCategory == "General" }
    )

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        // Header
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = stringResource(R.string.quiz_categories),
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF2E2E2E)
                    )

                    val readyCount = allCategories.count { category ->
                        availablePuzzleTypes.contains(category.category)
                    }

                    Text(
                        text = "$readyCount/${allCategories.size} ready to play",
                        fontSize = 14.sp,
                        color = if (readyCount > 0) Color(0xFF4CAF50) else Color(0xFFFF9800)
                    )
                }

                // Status indicator
                Box(
                    modifier = Modifier
                        .background(
                            Color(0xFF4CAF50),
                            RoundedCornerShape(12.dp)
                        )
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "ALL SHOWN",
                        color = Color.White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // Filter chips
        item {
            FlowRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                filterOptions.forEach { (filter, count) ->
                    val isSelected = selectedFilter == filter

                    FilterChip(
                        selected = isSelected,
                        onClick = { selectedFilter = filter },
                        label = {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                // Add icons for special filters
                                when (filter) {
                                    "Ready" -> Icon(
                                        imageVector = Icons.Default.PlayArrow,
                                        contentDescription = "Ready",
                                        modifier = Modifier.size(14.dp)
                                    )
                                    "On Demand" -> Icon(
                                        imageVector = Icons.Default.CloudDownload,
                                        contentDescription = "On Demand",
                                        modifier = Modifier.size(14.dp)
                                    )
                                    "Offline" -> Icon(
                                        imageVector = Icons.Default.CloudOff,
                                        contentDescription = "Offline",
                                        modifier = Modifier.size(14.dp)
                                    )
                                }

                                Text("$filter ($count)")
                            }
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = when (filter) {
                                "Ready" -> Color(0xFF4CAF50)
                                "On Demand" -> Color(0xFF2196F3)
                                "Offline" -> Color(0xFF607D8B)
                                else -> MaterialTheme.colorScheme.primary
                            },
                            selectedLabelColor = Color.White
                        )
                    )
                }
            }
        }

        // Section title
        item {
            Text(
                text = when (selectedFilter) {
                    "All" -> "All Categories"
                    "Ready" -> "Ready to Play"
                    "On Demand" -> "On Demand (Server-based)"
                    "Offline" -> "Offline Categories"
                    else -> "$selectedFilter Categories"
                },
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(top = 8.dp, bottom = 8.dp)
            )
        }

        // Categories grid
        item {
            if (filteredCategories.isEmpty()) {
                // Empty state
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 40.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Default.Category,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = Color.Gray
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = "No $selectedFilter categories",
                        fontSize = 16.sp,
                        color = Color.Gray,
                        textAlign = TextAlign.Center
                    )
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.height(800.dp)
                ) {
                    items(filteredCategories) { category ->
                        CategoryCard(
                            category = category,
                            isOffline = isOfflinePuzzleType(category.category),
                            isReady = availablePuzzleTypes.contains(category.category),
                            onClick = {
                                // Always allow selection - PuzzleQueueManager will handle on-demand fetching
                                coroutineScope.launch {
                                    if (!availablePuzzleTypes.contains(category.category) &&
                                        !isOfflinePuzzleType(category.category)) {
                                        Log.d("CategoriesTab", "On-demand fetch triggered for ${category.category}")
                                        // PuzzleQueueManager will handle fetching in getNextPuzzleForPlay
                                    }
                                    onCategorySelected(category.category)
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun CategoryCard(
    category: QuizCategory,
    isOffline: Boolean,
    isReady: Boolean,
    onClick: () -> Unit
) {
    val imageResource = getImageResourceForCategory(category.category)
    val isNewCategory = isPuzzleTypeNew(category.category)

    val cardColors = when {
        isReady -> listOf(Color(0xFF4CAF50), Color(0xFF66BB6A))
        isOffline -> listOf(Color(0xFF2196F3), Color(0xFF42A5F5))
        else -> listOf(Color(0xFF6B73FF), Color(0xFF9B59B6))
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(180.dp)
            .clickable { onClick() },
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(
            defaultElevation = if (isReady || isOffline) 6.dp else 3.dp
        ),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Image section (takes most of the space)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                // Background gradient/image
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            brush = Brush.linearGradient(cardColors),
                            shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
                        )
                ) {
                    // If we have an image, show it as background
                    if (imageResource != null) {
                        Image(
                            painter = painterResource(id = imageResource),
                            contentDescription = "${category.title} game preview",
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)),
                            contentScale = ContentScale.Crop,
                            alpha = 0.9f
                        )
                    } else {
                        // Show placeholder content when no image
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            when (category.filterCategory) {
                                "Math" -> MathGamePlaceholder()
                                "Memory" -> MemoryGamePlaceholder()
                                "English" -> LanguageGamePlaceholder()
                                "Logic" -> LogicGamePlaceholder()
                                "Focus" -> FocusGamePlaceholder()
                                "Reaction" -> ReactionGamePlaceholder()
                                else -> GenericGamePlaceholder(category.icon)
                            }
                        }
                    }
                }

                // Top badges row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    // Category icon (only show when no image and in top-left)
                    if (imageResource == null) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .background(
                                    Color.White.copy(alpha = 0.2f),
                                    CircleShape
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = category.icon,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    } else {
                        Spacer(modifier = Modifier.size(40.dp))
                    }

                    // Badges column
                    Column(
                        horizontalAlignment = Alignment.End,
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        // Status badge - prioritized at top
                        Box(
                            modifier = Modifier
                                .background(
                                    Color.White.copy(alpha = 0.9f),
                                    RoundedCornerShape(8.dp)
                                )
                                .padding(horizontal = 6.dp, vertical = 3.dp)
                        ) {
                            Text(
                                text = when {
                                    isReady -> if (isOffline) "OFFLINE" else "READY"
                                    isOffline -> "OFFLINE"
                                    else -> "ON DEMAND"
                                },
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Bold,
                                color = when {
                                    isReady -> Color(0xFF4CAF50)
                                    isOffline -> Color(0xFF2196F3)
                                    else -> Color(0xFF6B73FF)
                                }
                            )
                        }

                        // NEW badge for new categories
                        if (isNewCategory) {
                            Box(
                                modifier = Modifier
                                    .background(
                                        color = Color(0xFFFF4444),
                                        shape = RoundedCornerShape(8.dp)
                                    )
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = "NEW!",
                                    color = Color.White,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }

            // Text section at the bottom (outside the image)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.White)
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = category.title,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF2E2E2E),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Text(
                    text = when {
                        isReady && isOffline -> "Works offline"
                        isReady -> "Ready to play"
                        isOffline -> "No internet needed"
                        else -> "Loads on demand"
                    },
                    fontSize = 11.sp,
                    color = Color(0xFF666666),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 13.sp
                )
            }
        }
    }
}

fun isOfflinePuzzleType(puzzleType: String): Boolean {
    // Define which puzzle types can work without internet connection
    // These are the specific offline puzzle types provided
    val offlinePuzzleTypes = setOf(
        "uniqueobject",
        "discounts",
        "division",
        "contextswitch",
        "mathcrossword",
        "symmetry",
        "trainrouting",
        "pinballdeflector",
        "memorypreviouspair",
        "memoryprevioussingle",
        "geography_countries",
        "geography_cities",
        "colortextmatching",
        "conversion",
        "triangledotmemory",
        "numbersequence",
        "dualcard",
        "mathestimation",
        "mathtipping",
        "percentages",
        "numbersum",
        "symbolswipe",
        "colormatching",
        "imagevortex",
        "memorysquares",
        "mathexpression",
        "purchasing",
        "subtraction",
        "mathcomparison"
    )

    return offlinePuzzleTypes.contains(puzzleType)
}

fun getImageResourceForCategory(categoryType: String): Int? {
    return when (categoryType) {
        "dualcard" -> R.drawable.dual_card_game
        "math" -> R.drawable.math_game
        "memorysquares" -> R.drawable.memory_squares_game
        "anagram" -> R.drawable.anagram_game
        "crossword" -> R.drawable.crossword_game
        "trivia" -> R.drawable.trivia_game
        "storyPuzzle" -> R.drawable.story_puzzle_game
        "antonyms" -> R.drawable.antonyms_game
        "synonyms" -> R.drawable.synonyms_game
        "memorystory" -> R.drawable.memory_story_game
        "memorysequencing" -> R.drawable.timeline_puzzle_game
        "memoryretention" -> R.drawable.listen_learn_game
        "pinballdeflector" -> R.drawable.pinball_physics_game
        "memoryprevioussingle" -> R.drawable.memory_match_game
        "average" -> R.drawable.average_game
        "division" -> R.drawable.division_game
        "mathestimation" -> R.drawable.estimation_game
        "uniqueobject" -> R.drawable.unique_object_game
        "numbersum" -> R.drawable.number_sum_game
        "colormatching" -> R.drawable.color_match_game
        "symbolswipe" -> R.drawable.symbol_swipe_game
        "imagevortex" -> R.drawable.image_vortex_game
        "mathtipping" -> R.drawable.tip_calculation_game
        "wordsearch" -> R.drawable.word_search_game
        "purchasing" -> R.drawable.purchasing_game
        "discounts" -> R.drawable.discounts_game
        "connotationwords" -> R.drawable.word_connotations_game
        "memorypreviouspair" -> R.drawable.memory_pairs_game
        "wordprefix" -> R.drawable.word_prefix_game
        "mathexpression" -> R.drawable.math_expression_game
        "mathcomparison" -> R.drawable.math_comparison_game
        "numbersequence" -> R.drawable.number_sequence_game
        "percentages" -> R.drawable.percentage_game
        "conversion" -> R.drawable.conversion_game
        "subtraction" -> R.drawable.subtraction_game
        "triangledotmemory" -> R.drawable.triangle_dots_game
        "colortextmatching" -> R.drawable.color_text_match_game
        "crypto" -> R.drawable.crypto_game
        "wordsnake" -> R.drawable.word_snake_game
        "geography_cities" -> R.drawable.geography_cities_game
        "geography_countries" -> R.drawable.geography_countries_game
        "find_object" -> R.drawable.find_object_game
        "musicidentification" -> R.drawable.music_identification
        "imagepuzzle" -> R.drawable.image_puzzle
        "symmetry" -> R.drawable.symmetry_game
        "mathcrossword" -> R.drawable.math_crossword
        else -> null // Will show placeholder
    }
}

@Composable
fun MathGamePlaceholder() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "2 + 3",
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )
        Text(
            text = "= ?",
            fontSize = 18.sp,
            fontWeight = FontWeight.Medium,
            color = Color.White.copy(alpha = 0.9f)
        )
    }
}

@Composable
fun MemoryGamePlaceholder() {
    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        modifier = Modifier.size(80.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        items(9) { index ->
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .background(
                        if (index % 3 == 0) Color.White else Color.White.copy(alpha = 0.3f),
                        RoundedCornerShape(4.dp)
                    )
            )
        }
    }
}

@Composable
fun LanguageGamePlaceholder() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "WORD",
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            letterSpacing = 3.sp
        )
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            horizontalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            repeat(4) {
                Box(
                    modifier = Modifier
                        .size(16.dp, 3.dp)
                        .background(Color.White, RoundedCornerShape(1.dp))
                )
            }
        }
    }
}

@Composable
fun LogicGamePlaceholder() {
    Column {
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(20.dp)
                    .background(Color.White, CircleShape)
            )
            Box(
                modifier = Modifier
                    .size(20.dp)
                    .background(Color.White.copy(alpha = 0.8f), RoundedCornerShape(4.dp))
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(20.dp)
                    .background(Color.White.copy(alpha = 0.6f))
            ) {
                Icon(
                    imageVector = Icons.Default.Star,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(16.dp).align(Alignment.Center)
                )
            }
            Text(
                text = "?",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        }
    }
}

@Composable
fun FocusGamePlaceholder() {
    Box(
        contentAlignment = Alignment.Center
    ) {
        // Target circles
        repeat(3) { index ->
            Box(
                modifier = Modifier
                    .size((80 - index * 20).dp)
                    .background(
                        Color.Transparent,
                        CircleShape
                    )
                    .border(
                        width = 3.dp,
                        color = Color.White.copy(alpha = 1f - index * 0.3f),
                        shape = CircleShape
                    )
            )
        }
        // Center dot
        Box(
            modifier = Modifier
                .size(12.dp)
                .background(Color.White, CircleShape)
        )
    }
}

@Composable
fun ReactionGamePlaceholder() {
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Default.ArrowLeft,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(24.dp)
        )
        Icon(
            imageVector = Icons.Default.TouchApp,
            contentDescription = null,
            tint = Color.White.copy(alpha = 0.8f),
            modifier = Modifier.size(20.dp)
        )
        Icon(
            imageVector = Icons.Default.ArrowRight,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(24.dp)
        )
    }
}

@Composable
fun GenericGamePlaceholder(icon: ImageVector) {
    Icon(
        imageVector = icon,
        contentDescription = null,
        tint = Color.White,
        modifier = Modifier.size(40.dp)
    )
}

fun getGradientForCategory(filterCategory: String): Brush {
    return when (filterCategory) {
        "Math" -> Brush.linearGradient(
            colors = listOf(Color(0xFF2196F3), Color(0xFF1976D2))
        )
        "Memory" -> Brush.linearGradient(
            colors = listOf(Color(0xFFFF9800), Color(0xFFF57C00))
        )
        "English" -> Brush.linearGradient(
            colors = listOf(Color(0xFF9C27B0), Color(0xFF7B1FA2))
        )
        "Logic" -> Brush.linearGradient(
            colors = listOf(Color(0xFF4CAF50), Color(0xFF388E3C))
        )
        "Focus" -> Brush.linearGradient(
            colors = listOf(Color(0xFFFF5722), Color(0xFFD32F2F))
        )
        "Reaction" -> Brush.linearGradient(
            colors = listOf(Color(0xFFE91E63), Color(0xFFC2185B))
        )
        "General" -> Brush.linearGradient(
            colors = listOf(Color(0xFF607D8B), Color(0xFF455A64))
        )
        // NEW: Visual category gradient
        "Visual" -> Brush.linearGradient(
            colors = listOf(Color(0xFFFF6B35), Color(0xFF9C27B0))
        )
        else -> Brush.linearGradient(
            colors = listOf(Color(0xFF6B73FF), Color(0xFF5A67D8))
        )
    }
}

@Composable
fun SubscriptionSection() {
    val subscriptionManager = SubscriptionManager.getInstance(LocalContext.current)
    var showUpgradeDialog by remember { mutableStateOf(false) }
    var showCustomerCenter by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (subscriptionManager.hasActiveSubscription()) {
                Color(0xFF1A237E)
            } else {
                Color(0xFF2A2A2A)
            }
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (subscriptionManager.hasActiveSubscription()) {
                            Icons.Default.Star
                        } else {
                            Icons.Default.StarBorder
                        },
                        contentDescription = stringResource(R.string.subscription),
                        tint = if (subscriptionManager.hasActiveSubscription()) {
                            Color(0xFFFFD700)
                        } else {
                            Color.Gray
                        },
                        modifier = Modifier.size(24.dp)
                    )

                    Spacer(modifier = Modifier.width(12.dp))

                    Column {
                        Text(
                            text = stringResource(R.string.subscription),
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )

                        Text(
                            text = subscriptionManager.currentTier.displayName,
                            fontSize = 14.sp,
                            color = if (subscriptionManager.hasActiveSubscription()) {
                                Color(0xFFFFD700)
                            } else {
                                Color.Gray
                            }
                        )
                    }
                }

                // Action button
                when (subscriptionManager.currentTier) {
                    SubscriptionTier.FREE -> {
                        Button(
                            onClick = { showUpgradeDialog = true },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF4CAF50)
                            ),
                            shape = RoundedCornerShape(20.dp)
                        ) {
                            Text(stringResource(R.string.upgrade), fontSize = 12.sp)
                        }
                    }
                    else -> {
                        OutlinedButton(
                            onClick = { showCustomerCenter = true },
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = Color(0xFFFFD700)
                            ),
                            border = ButtonDefaults.outlinedButtonBorder.copy(
                                brush = Brush.linearGradient(listOf(Color(0xFFFFD700), Color(0xFFFFD700)))
                            ),
                            shape = RoundedCornerShape(20.dp)
                        ) {
                            Text(stringResource(R.string.manage), fontSize = 12.sp)
                        }
                    }
                }
            }

            // Show benefits/usage for current tier
            if (subscriptionManager.hasActiveSubscription()) {
                Spacer(modifier = Modifier.height(12.dp))

                // Premium benefits indicator
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = Color(0xFF4CAF50),
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = when (subscriptionManager.currentTier) {
                            SubscriptionTier.PREMIUM, SubscriptionTier.PREMIUM_YEARLY -> "Unlimited generations • No ads • Priority support"
                            else -> ""
                        },
                        fontSize = 12.sp,
                        color = Color.LightGray
                    )
                }
            } else {
                Spacer(modifier = Modifier.height(12.dp))

                // Free tier limitations
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        tint = Color.Gray,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "10 daily generations • 100 monthly generations",
                        fontSize = 12.sp,
                        color = Color.Gray
                    )
                }
            }
        }
    }

    // Upgrade dialog
    if (showUpgradeDialog) {
        SubscriptionUpgradeDialog(
            onDismiss = { showUpgradeDialog = false },
            onUpgrade = { tier ->
                showUpgradeDialog = false
                // Handle successful upgrade
            }
        )
    }

    // Customer Center (subscription management, cancel deflection, win-back)
    if (showCustomerCenter) {
        CustomerCenterDialog(
            onDismiss = { showCustomerCenter = false }
        )
    }
}

@Composable
fun SettingsTab(
    onNavigateToLeaderboard: () -> Unit,
    onNavigateToTopics: () -> Unit,
    onNavigateToAchievements: () -> Unit,
    onSignOut: () -> Unit,
    onSignIn: () -> Unit,
    onSignUp: () -> Unit,
    onShowRatingDialog: () -> Unit
) {
    val isSignedIn = remember { mutableStateOf(FirebaseAuth.getInstance().currentUser != null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            text = stringResource(R.string.tab_settings),
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 20.dp)
        )

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            item {
                ProfileSection()
            }

            if (isSignedIn.value) {
                item {
                    CreatorEarningsSection()
                }
            }

            item {
                SubscriptionSection()
            }

            item {
                CoinPurchaseSection()
            }

            // Progress & Stats section using new architecture
            item {
                ProgressStatsSection()
            }

            item {
                AppFeaturesSection(
                    onNavigateToLeaderboard = onNavigateToLeaderboard,
                    onNavigateToTopics = onNavigateToTopics,
                    onNavigateToAchievements = onNavigateToAchievements
                )
            }

            item {
                RatingSection(onShowRatingDialog = onShowRatingDialog)
            }

            item {
                AccountSection(
                    isSignedIn = isSignedIn.value,
                    onSignOut = onSignOut,
                    onSignIn = onSignIn,
                    onSignUp = onSignUp
                )
            }
        }
    }
}

@Composable
fun RatingSection(onShowRatingDialog: () -> Unit) {
    val context = LocalContext.current
    val ratingManager = remember { AppRatingManager(context) }
    var showFeedbackDialog by remember { mutableStateOf(false) }

    val hasRated = remember { ratingManager.hasUserRated() }
    val isDismissedPermanently = remember { ratingManager.hasUserDismissedPermanently() }
    val hasGivenNegativeFeedback = remember { ratingManager.hasGivenNegativeFeedback() }

    SettingsSection(title = "Support & Feedback") {
        Column {
            SettingsRow(
                icon = Icons.Default.Star,
                title = when {
                    hasRated -> "App Feedback"
                    hasGivenNegativeFeedback -> "Additional Feedback"
                    isDismissedPermanently -> "Rate RiddleVerse"
                    else -> "Rate RiddleVerse"
                },
                subtitle = when {
                    hasRated -> "Share additional feedback or suggestions"
                    hasGivenNegativeFeedback -> "Share more thoughts or suggestions"
                    isDismissedPermanently -> "Changed your mind? Rate us and get rewards!"
                    else -> "Rate us and get exclusive rewards!"
                },
                onClick = {
                    if (isDismissedPermanently) {
                        // Reset permanent dismissal if user manually opens rating
                        val prefs = context.getSharedPreferences("app_rating_prefs", Context.MODE_PRIVATE)
                        prefs.edit().putBoolean("user_dismissed_permanently", false).apply()
                        Log.d("RatingSection", "Reset permanent dismissal - user manually opened rating")
                    }

                    if (hasRated || hasGivenNegativeFeedback) {
                        // Show feedback dialog directly for users who already rated/gave feedback
                        showFeedbackDialog = true
                    } else {
                        // Show normal rating flow
                        onShowRatingDialog()
                    }
                }
            )

            HorizontalDivider(modifier = Modifier.padding(start = 50.dp))

            SettingsRow(
                icon = Icons.Default.BugReport,
                title = "Report a Bug",
                subtitle = "Found something not working? Let us know!",
                onClick = {
                    showFeedbackDialog = true
                }
            )

            // ADD: Contact support option
            HorizontalDivider(modifier = Modifier.padding(start = 50.dp))

            SettingsRow(
                icon = Icons.Default.ContactSupport,
                title = "Contact Support",
                subtitle = "Get help with account or technical issues",
                onClick = {
                    openFeedbackEmail(context)
                }
            )
        }
    }

    // Show feedback dialog when triggered from settings
    if (showFeedbackDialog) {
        FeedbackDialog(
            isPositive = hasRated, // Assume positive if they've already rated
            onDismiss = {
                showFeedbackDialog = false
            },
            onSubmitted = {
                showFeedbackDialog = false
                Toast.makeText(context, "Thank you for your feedback!", Toast.LENGTH_SHORT).show()
            }
        )
    }
}

@Composable
fun ProgressStatsSection() {
    val context = LocalContext.current

    // Initialize your new architecture components
    val userStatsManager = remember { UserStatsManager.getInstance(context) }
    val progressionEngine = remember { ProgressionEngine(context, userStatsManager) }
    val dashboardDataProvider = remember { DashboardDataProvider(context, userStatsManager, progressionEngine) }

    var dashboardData by remember { mutableStateOf<DashboardData?>(null) }
    var isLoading by remember { mutableStateOf(true) }

    // Load dashboard data
    LaunchedEffect(Unit) {
        try {
            dashboardData = dashboardDataProvider.getDashboardData()
        } catch (e: Exception) {
            Log.e("ProgressStats", "Failed to load dashboard data", e)
        } finally {
            isLoading = false
        }
    }

    SettingsSection(title = "Progress & Stats") {
        if (isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(100.dp),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp))
            }
        } else {
            dashboardData?.let { data ->
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Level and XP
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Level ${data.levelInfo.level}",
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )
                            Text(
                                text = "${data.globalStats.totalXP} XP",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        LevelProgressBar(
                            level = data.levelInfo,
                            modifier = Modifier.width(120.dp),
                            compact = true,
                            showLabel = false
                        )
                    }

                    HorizontalDivider()

                    // Current tier
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = stringResource(R.string.current_tier),
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = data.tierInfo.currentTier,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    // Streaks
                    if (data.streakInfo.currentQuestionStreak > 0 || data.streakInfo.currentDailyStreak > 0) {
                        HorizontalDivider()

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = stringResource(R.string.active_streaks),
                                fontWeight = FontWeight.Medium
                            )

                            EnhancedStreakDisplay(
                                streakInfo = data.streakInfo,
                                showBonusInfo = false
                            )
                        }
                    }

                    // Quick stats
                    HorizontalDivider()

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "${data.globalStats.totalGamesPlayed}",
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )
                            Text(
                                text = stringResource(R.string.tab_puzzles),
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "${data.globalStats.totalTimeHours.toInt()}h",
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )
                            Text(
                                text = stringResource(R.string.time_label),
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "${data.achievements.count { it.isUnlocked }}",
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )
                            Text(
                                text = stringResource(R.string.badges),
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            } ?: run {
                // Fallback UI if dashboard data fails to load
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Default.Error,
                        contentDescription = "Error",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(32.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.unable_to_load_progress),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

@Composable
fun ProfileSection() {
    SettingsSection(title = "Profile") {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.AccountCircle,
                contentDescription = "Profile",
                modifier = Modifier.size(40.dp),
                tint = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.width(12.dp))

            Column {
                FirebaseAuth.getInstance().currentUser?.displayName?.let {
                    Text(
                        text = it, // Replace with actual user name
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 16.sp
                    )
                }

                FirebaseAuth.getInstance().currentUser?.email?.let {
                    Text(
                        text = it, // Replace with actual email
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
fun CreatorEarningsSection() {
    val coinManager = CoinManager.shared

    LaunchedEffect(Unit) {
        coinManager.fetchCreatorEarnings()
    }

    if (coinManager.isLoadingEarnings) {
        SettingsSection(title = "Creator Earnings") {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp))
            }
        }
        return
    }

    val earnings = coinManager.creatorEarnings ?: return

    SettingsSection(title = "Creator Earnings") {
        Column(modifier = Modifier.padding(16.dp)) {
            if (earnings.totalCoinsEarned == 0) {
                // Teaser card
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.Default.Star,
                        contentDescription = null,
                        tint = Color(0xFFFFD700),
                        modifier = Modifier.size(32.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = stringResource(R.string.earn_coins_from_games),
                            fontWeight = FontWeight.Medium,
                            fontSize = 14.sp
                        )
                        Text(
                            text = stringResource(R.string.creator_revenue_share),
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                // Summary row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            text = "${earnings.totalCoinsEarned}",
                            fontWeight = FontWeight.Bold,
                            fontSize = 24.sp,
                            color = Color(0xFF4CAF50)
                        )
                        Text(
                            text = stringResource(R.string.coins_earned),
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = "${earnings.totalPlaysMonetized}",
                            fontWeight = FontWeight.Bold,
                            fontSize = 24.sp
                        )
                        Text(
                            text = stringResource(R.string.plays_monetized),
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                if (earnings.gameBreakdown.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(16.dp))
                    HorizontalDivider()
                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = stringResource(R.string.per_game),
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )

                    earnings.gameBreakdown.take(5).forEach { game ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = game.gameTitle,
                                fontSize = 13.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = "${game.totalCoinsEarned} coins",
                                fontSize = 13.sp,
                                color = Color(0xFF4CAF50),
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }

                // Cash Out button
                var showPayoutDialog by remember { mutableStateOf(false) }

                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(12.dp))

                Button(
                    onClick = {
                        coinManager.checkPayoutEligibility()
                        coinManager.fetchPayoutHistory()
                        showPayoutDialog = true
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50)),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text(stringResource(R.string.cash_out_earnings), fontWeight = FontWeight.SemiBold)
                }

                if (showPayoutDialog) {
                    PayoutDialog(onDismiss = { showPayoutDialog = false })
                }
            }
        }
    }
}

@Composable
fun PayoutDialog(onDismiss: () -> Unit) {
    val coinManager = CoinManager.shared
    val context = LocalContext.current
    val eligibility = coinManager.payoutEligibility

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            shape = RoundedCornerShape(16.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.cash_out),
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = stringResource(R.string.close))
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                if (coinManager.isLoadingPayout || eligibility == null) {
                    // Loading
                    CircularProgressIndicator(modifier = Modifier.size(32.dp))
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(stringResource(R.string.checking_eligibility), fontSize = 13.sp, color = Color.Gray)
                } else if (!eligibility.stripeConnected) {
                    // Not connected to Stripe
                    Icon(
                        imageVector = Icons.Default.AccountBalance,
                        contentDescription = null,
                        tint = Color(0xFF6772E5),
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = stringResource(R.string.connect_stripe_to_receive),
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 16.sp,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Set up your Stripe account to cash out your earned coins as real money.",
                        fontSize = 13.sp,
                        color = Color.Gray,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = {
                            coinManager.connectStripe { url ->
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                                context.startActivity(intent)
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6772E5)),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text(stringResource(R.string.connect_stripe_account), fontWeight = FontWeight.SemiBold)
                    }
                } else if (!eligibility.stripePayoutsEnabled) {
                    // Stripe connected but onboarding incomplete
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = null,
                        tint = Color(0xFFFFA726),
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = stringResource(R.string.complete_stripe_setup),
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 16.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Your Stripe account setup is incomplete. Tap below to finish.",
                        fontSize = 13.sp,
                        color = Color.Gray,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = {
                            coinManager.connectStripe { url ->
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                                context.startActivity(intent)
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFA726)),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text(stringResource(R.string.continue_setup), fontWeight = FontWeight.SemiBold)
                    }
                } else if (eligibility.earnedBalance < eligibility.minThreshold) {
                    // Below threshold
                    val needed = eligibility.minThreshold - eligibility.earnedBalance
                    Text(
                        text = "${eligibility.earnedBalance}",
                        fontWeight = FontWeight.Bold,
                        fontSize = 32.sp,
                        color = Color(0xFF4CAF50)
                    )
                    Text(
                        text = "coins available",
                        fontSize = 13.sp,
                        color = Color.Gray
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "You need $needed more coins to reach the minimum payout of ${eligibility.minThreshold} coins.",
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center,
                        color = Color.Gray
                    )
                } else {
                    // Eligible — show payout form
                    val coinsToPayOut = eligibility.earnedBalance
                    val usdAmount = String.format("%.2f", coinsToPayOut * eligibility.conversionRate)

                    Text(
                        text = "$coinsToPayOut",
                        fontWeight = FontWeight.Bold,
                        fontSize = 32.sp,
                        color = Color(0xFF4CAF50)
                    )
                    Text(
                        text = "coins available",
                        fontSize = 13.sp,
                        color = Color.Gray
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "$$usdAmount USD",
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp
                    )
                    Text(
                        text = "at ${eligibility.conversionRate}/coin",
                        fontSize = 12.sp,
                        color = Color.Gray
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    Button(
                        onClick = {
                            coinManager.requestPayout(coinsToPayOut) { success, error ->
                                if (success) {
                                    coinManager.fetchCreatorEarnings()
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !coinManager.isLoadingPayout,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50)),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        if (coinManager.isLoadingPayout) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = Color.White,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Text(stringResource(R.string.request_payout), fontWeight = FontWeight.SemiBold)
                        }
                    }
                }

                // Payout message
                coinManager.payoutMessage?.let { msg ->
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = msg,
                        fontSize = 13.sp,
                        color = if (msg.contains("failed") || msg.contains("error", ignoreCase = true))
                            Color(0xFFE53935) else Color(0xFF4CAF50),
                        textAlign = TextAlign.Center
                    )
                }

                // Payout history
                if (coinManager.payoutHistory.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(16.dp))
                    HorizontalDivider()
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = stringResource(R.string.payout_history),
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp)
                    )

                    coinManager.payoutHistory.take(5).forEach { payout ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "${payout.coinsAmount} coins",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    text = "$${String.format("%.2f", payout.usdAmount)}",
                                    fontSize = 12.sp,
                                    color = Color.Gray
                                )
                            }
                            val statusColor = when (payout.status) {
                                "completed" -> Color(0xFF4CAF50)
                                "pending", "processing" -> Color(0xFFFFA726)
                                "failed" -> Color(0xFFE53935)
                                else -> Color.Gray
                            }
                            Text(
                                text = payout.status.replaceFirstChar { it.uppercase() },
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                color = statusColor
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AppFeaturesSection(
    onNavigateToLeaderboard: () -> Unit,
    onNavigateToTopics: () -> Unit,
    onNavigateToAchievements: () -> Unit
) {
    SettingsSection(title = "App Features") {
        Column {
            SettingsRow(
                icon = Icons.Default.Iron,
                title = stringResource(R.string.leaderboard),
                subtitle = "View rankings and scores",
                onClick = onNavigateToLeaderboard
            )

            HorizontalDivider(modifier = Modifier.padding(start = 50.dp))

            SettingsRow(
                icon = Icons.Default.Tag,
                title = stringResource(R.string.preferred_topics),
                subtitle = "Customize your puzzle preferences",
                onClick = onNavigateToTopics
            )

            HorizontalDivider(modifier = Modifier.padding(start = 50.dp))

            SettingsRow(
                icon = Icons.Default.EmojiEvents,
                title = "Achievements & Progress",
                subtitle = "View achievements, badges, tier progress, and rewards",
                onClick = onNavigateToAchievements
            )
        }
    }
}

@Composable
fun AccountSection(
    isSignedIn: Boolean,
    onSignOut: () -> Unit,
    onSignIn: () -> Unit,
    onSignUp: () -> Unit
) {
    SettingsSection(title = "Account") {
        if (isSignedIn) {
            SettingsRow(
                icon = Icons.Default.Logout,
                title = "Sign Out",
                iconTint = Color.Red,
                titleColor = Color.Red,
                onClick = onSignOut
            )
        } else {
            Column {
                SettingsRow(
                    icon = Icons.Default.Login,
                    title = "Sign In",
                    onClick = onSignIn
                )

                HorizontalDivider(modifier = Modifier.padding(start = 50.dp))

                SettingsRow(
                    icon = Icons.Default.PersonAdd,
                    title = "Create Account",
                    onClick = onSignUp
                )
            }
        }
    }
}

@Composable
fun SettingsSection(
    title: String,
    content: @Composable () -> Unit
) {
    Column {
        Text(
            text = title,
            fontWeight = FontWeight.SemiBold,
            fontSize = 18.sp,
            modifier = Modifier.padding(bottom = 12.dp)
        )

        Card(
            modifier = Modifier.fillMaxWidth(),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            shape = RoundedCornerShape(12.dp)
        ) {
            content()
        }
    }
}

@Composable
fun SettingsRow(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    iconTint: Color = MaterialTheme.colorScheme.primary,
    titleColor: Color = MaterialTheme.colorScheme.onSurface,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = title,
            modifier = Modifier.size(24.dp),
            tint = iconTint
        )

        Spacer(modifier = Modifier.width(16.dp))

        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = title,
                fontWeight = FontWeight.Medium,
                color = titleColor
            )

            subtitle?.let {
                Text(
                    text = it,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Icon(
            imageVector = Icons.Default.ChevronRight,
            contentDescription = "Navigate",
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun GameCompletionBanner(
    onTap: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onTap),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF262640)),
        shape = RoundedCornerShape(14.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.CheckCircle,
                contentDescription = null,
                tint = Color(0xFF4CAF50),
                modifier = Modifier.size(28.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.game_ready),
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    fontSize = 14.sp
                )
                Text(
                    text = "Tap to preview — also in Explore > Custom Games",
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 12.sp
                )
            }
            IconButton(onClick = onDismiss) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = stringResource(R.string.close),
                    tint = Color.White.copy(alpha = 0.6f),
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

@Composable
fun BottomTabBar(
    selectedTab: TabSelection,
    onTabSelected: (TabSelection) -> Unit,
    isGenerating: Boolean = false
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
        shape = RoundedCornerShape(topStart = 0.dp, topEnd = 0.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E2E))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(vertical = 8.dp)
        ) {
            TabSelection.values().forEach { tab ->
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clickable { onTabSelected(tab) }
                        .padding(8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Icon with new indicator
                    Box {
                        Icon(
                            imageVector = tab.icon,
                            contentDescription = tab.title,
                            modifier = Modifier.size(24.dp),
                            tint = if (selectedTab == tab) {
                                Color(0xFFFF8C00)
                            } else {
                                Color.White.copy(alpha = 0.6f)
                            }
                        )

                        // Small red dot indicator
                        if (tab.hasNewFeatures) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .background(
                                        color = Color(0xFFFF4444),
                                        shape = CircleShape
                                    )
                                    .align(Alignment.TopEnd)
                                    .offset(x = 2.dp, y = (-2).dp)
                            )
                        }

                        // Orange dot when generating on another tab
                        if (tab == TabSelection.CREATE_GAME && isGenerating && selectedTab != TabSelection.CREATE_GAME) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .background(
                                        color = Color(0xFFFF8C00),
                                        shape = CircleShape
                                    )
                                    .align(Alignment.TopEnd)
                                    .offset(x = 2.dp, y = (-2).dp)
                            )
                        }

                        // 💰 earn badge on Games tab
                        if (tab == TabSelection.CREATE_GAME) {
                            Text(
                                text = "💰",
                                fontSize = 9.sp,
                                modifier = Modifier
                                    .align(Alignment.BottomEnd)
                                    .offset(x = 4.dp, y = 4.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = when (tab) {
                            TabSelection.FOR_YOU -> stringResource(R.string.tab_for_you)
                            TabSelection.CATEGORIES -> stringResource(R.string.tab_categories)
                            TabSelection.CREATE_GAME -> stringResource(R.string.tab_games)
                            TabSelection.CUSTOM_PUZZLES -> stringResource(R.string.tab_puzzles)
                            TabSelection.SETTINGS -> stringResource(R.string.tab_settings)
                        },
                        fontSize = 10.sp,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = if (selectedTab == tab) {
                            Color(0xFFFF8C00)
                        } else {
                            Color.White.copy(alpha = 0.6f)
                        }
                    )
                }
            }
        }
    }
}

class HomeActivity : AppCompatActivity() {
    // In your main activity where the tab switching code is


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val activityScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
        var dailyStreakReward: StreakReward? = null
        activityScope.launch {
            try {
                AnalyticsSessionManager.getInstance().logAppOpenIfNeeded()
                AnalyticsSessionManager.getInstance().logSessionStartIfNeeded()
                AnalyticsSessionManager.getInstance().logScreenViewIfNeeded("home_screen")

                dailyStreakReward = DailyStreakManager.processAppOpen(this@HomeActivity).second
                val userId = FirebaseAuth.getInstance().currentUser?.email ?: ""
            } catch (e: Exception) {
                Log.e("HomeActivity", "Analytics tracking failed", e)
            }
        }
        val ratingManager = AppRatingManager(this)
        ratingManager.incrementLaunchCount()

        val tutorialPrefs = TutorialPreferences(this)
        val shouldShowOnboarding = !tutorialPrefs.hasCompletedOnboarding()

        setContent {
            RiddleVerseTheme {
                var showOnboarding by remember { mutableStateOf(shouldShowOnboarding) }
                val navController = rememberNavController()
                var showStreakDialog by remember { mutableStateOf(dailyStreakReward != null) }
                val streakReward by remember { mutableStateOf(dailyStreakReward) }

                if (showOnboarding) {
                    // Show onboarding tutorial for first-time users
                    OnboardingScreen(
                        onComplete = {
                            tutorialPrefs.markOnboardingCompleted()
                            showOnboarding = false
                        }
                    )
                } else {
                    // Show normal home screen
                    LaunchedEffect(Unit) {
                        GroupCompletionManager.registerObserver { groupId, userId ->
                            Log.d("HomeActivity", "🔄 Group completion changed for $groupId")
                            // This will trigger refresh of ForYou data
                        }
                    }
                    LaunchedEffect(Unit) {
                        val subscriptionManager = SubscriptionManager.getInstance(this@HomeActivity)
                        subscriptionManager.loadProducts() // This triggers billing client usage
                    }

                    HomeScreen(
                        navController = navController,
                        handleDailyQuizSelected = { topic -> handleDailyQuizSelected(topic)}
                    )
                    if (showStreakDialog && streakReward != null) {
                        DailyStreakGiftDialog(
                            streakReward = streakReward!!,
                            onDismiss = { showStreakDialog = false }
                        )
                    }
                }
            }
        }
    }

    private fun handleDailySystemOnAppOpen(context: Context): StreakReward? {
        val (shouldReset, streakReward) = DailyStreakManager.processAppOpen(context)

        if (shouldReset) {
            Log.d("DailySystem", "🔄 Resetting all puzzle group completion for new day")
            GroupCompletionManager.resetAllCompletion(context)
        }

        return streakReward
    }

    fun handleDailyQuizSelected(topic: String) {
        val currentUser = FirebaseAuth.getInstance().currentUser
        if (currentUser != null) {
            val intent = Intent(this, StartPuzzleActivity::class.java).apply {
                putExtra("isDailyPuzzle", true)
                putExtra("dailyTopic", topic)
                putExtra("userEmail", currentUser.email)
                putExtra("puzzleType", "daily")
                putExtra("title", "$topic Quiz")
            }
            startActivity(intent)
        } else {
            Toast.makeText(this, "Please log in to access daily quizzes", Toast.LENGTH_SHORT).show()
        }
    }
}

@Composable
fun GreetingBasedOnTime() {
    val morningGreeting = stringResource(R.string.good_morning)
    val afternoonGreeting = stringResource(R.string.good_afternoon)
    val eveningGreeting = stringResource(R.string.good_evening)
    val greeting = remember(morningGreeting, afternoonGreeting, eveningGreeting) {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        when (hour) {
            in 5..11 -> morningGreeting
            in 12..17 -> afternoonGreeting
            else -> eveningGreeting
        }
    }

    Text(text = greeting, fontSize = 12.sp, color = Color.Gray)
}

@Composable
fun UserScoreView() {
    val context = LocalContext.current
    val userId = FirebaseAuth.getInstance().currentUser?.email
    var backendScore by remember { mutableStateOf<Int?>(null) }
    var localScoreIncrement by remember { mutableStateOf(0) }
    val scoreManager = remember { ScoreDisplayManager(context) }

    // Fetch backend score (your existing logic)
    LaunchedEffect(userId) {
        if (userId != null) {
            withContext(Dispatchers.IO) {
                val encodedUserId = URLEncoder.encode(userId, "UTF-8")
                val url = "https://puzzleverseai.com/get-score?userId=$encodedUserId"
                val request = Request.Builder().url(url).build()
                val client = OkHttpClient()

                try {
                    val response = client.newCall(request).execute()
                    val body = response.body?.string()
                    Log.d("UserScore", "📥 Response: $body")

                    if (response.isSuccessful && body != null) {
                        val json = JSONObject(body)
                        val fetchedScore = json.optInt("score", 0)

                        withContext(Dispatchers.Main) {
                            backendScore = fetchedScore
                            // Sync local increment when we get fresh backend data
                            localScoreIncrement = 0
                            scoreManager.syncWithBackend()
                        }
                    } else {
                        Log.e("UserScore", "❌ Failed to fetch score")
                    }
                } catch (e: Exception) {
                    Log.e("UserScore", "❌ Exception fetching score: ${e.message}", e)
                }
            }
        }
    }

    // Listen for local score changes (new puzzles completed)
    LaunchedEffect(Unit) {
        // Refresh local increment when screen becomes visible
        localScoreIncrement = scoreManager.getLocalScoreIncrement()
    }

    // Calculate display score (backend + pending local increments)
    val displayScore = (backendScore ?: 0) + localScoreIncrement

    if (backendScore != null || localScoreIncrement > 0) {
        Text(
            text = "🏅 Score: $displayScore" + if (localScoreIncrement > 0) " (+$localScoreIncrement)" else "",
            color = Color(0xFF8A4DFF),
            fontWeight = FontWeight.SemiBold,
            fontSize = 14.sp
        )
    }
}

@Composable
fun SearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    placeholder: String = "Search puzzles..."
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = Modifier.fillMaxWidth(),
        placeholder = {
            Text(
                text = placeholder,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        leadingIcon = {
            Icon(
                Icons.Default.Search,
                contentDescription = "Search",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(
                        Icons.Default.Clear,
                        contentDescription = "Clear",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        shape = RoundedCornerShape(12.dp),
        singleLine = true,
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = Color(0xFF000000),     // Pure black
            unfocusedTextColor = Color(0xFF000000),   // Pure black
            focusedBorderColor = MaterialTheme.colorScheme.primary,
            unfocusedBorderColor = MaterialTheme.colorScheme.outline
        )
    )
}



@Composable
fun CustomPuzzleTabRow(
    selectedTab: CustomPuzzleTab,
    onTabSelected: (CustomPuzzleTab) -> Unit
) {
    TabRow(
        selectedTabIndex = selectedTab.ordinal,
        modifier = Modifier.fillMaxWidth()
    ) {
        CustomPuzzleTab.values().forEach { tab ->
            Tab(
                selected = selectedTab == tab,
                onClick = { onTabSelected(tab) },
                text = { Text(tab.displayName) }
            )
        }
    }
}

@Composable
fun CustomPuzzlesHeader(
    onCreatePuzzle: () -> Unit,
    onRefresh: () -> Unit,
    onSortClick: () -> Unit // Changed from onFilterClick to onSortClick
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = stringResource(R.string.custom_puzzles),
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold
        )

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            IconButton(
                onClick = onSortClick, // Now actually does something
                modifier = Modifier
                    .size(40.dp)
                    .background(
                        color = Color(0xFF9C27B0),
                        shape = RoundedCornerShape(20.dp)
                    )
            ) {
                Icon(
                    imageVector = Icons.Default.Sort, // Changed to Sort icon
                    contentDescription = stringResource(R.string.sort_by),
                    tint = Color.White
                )
            }

            IconButton(
                onClick = onRefresh,
                modifier = Modifier
                    .size(40.dp)
                    .background(
                        color = Color(0xFF43e97b),
                        shape = RoundedCornerShape(20.dp)
                    )
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = "Refresh",
                    tint = Color.White
                )
            }

            IconButton(
                onClick = onCreatePuzzle,
                modifier = Modifier
                    .size(40.dp)
                    .background(
                        color = MaterialTheme.colorScheme.primary,
                        shape = RoundedCornerShape(20.dp)
                    )
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = stringResource(R.string.create),
                    tint = Color.White
                )
            }
        }
    }
}

fun formatDate(timestamp: Long): String {
    val sdf = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
    return sdf.format(Date(timestamp))
}

fun fetchCustomPuzzleDetail(
    context: Context,
    puzzleId: String,
    onResult: (List<Puzzle>?, String?) -> Unit
) {
    val TAG = "FetchCustomPuzzle"
    val url = "https://puzzleverseai.com/fetch-custom-puzzle?puzzleId=$puzzleId"
    val request = Request.Builder().url(url).build()
    val client = OkHttpClient()

    Log.d(TAG, "📡 Sending request to: $url")

    client.newCall(request).enqueue(object : Callback {
        override fun onFailure(call: Call, e: IOException) {
            Log.e(TAG, "❌ Failed to load puzzle", e)
            Handler(Looper.getMainLooper()).post { onResult(null, null) }
        }

        override fun onResponse(call: Call, response: Response) {
            val body = response.body?.string()
            Log.d(TAG, "✅ Raw response body: $body")

            try {
                val json = JSONObject(body ?: "")
                Log.d(TAG, "📦 Parsed top-level JSON")

                if (!json.has("puzzleData")) {
                    Log.e(TAG, "❌ 'puzzleData' field not found in response")
                    Handler(Looper.getMainLooper()).post { onResult(null, null) }
                    return
                }

                val outer = json.getJSONObject("puzzleData")
                Log.d(TAG, "📥 Extracted 'puzzleData': $outer")

                if (!outer.has("puzzleData")) {
                    Log.e(TAG, "❌ Inner 'puzzleData' field not found")
                    Handler(Looper.getMainLooper()).post { onResult(null, null) }
                    return
                }

                val puzzleSet = outer.getJSONObject("puzzleData")
                Log.d(TAG, "📥 Extracted inner 'puzzleData': $puzzleSet")

                val puzzlesArray = puzzleSet.optJSONArray("puzzles")
                if (puzzlesArray == null || puzzlesArray.length() == 0) {
                    Log.e(TAG, "⚠️ No puzzles found in response")
                    Handler(Looper.getMainLooper()).post { onResult(null, null) }
                    return
                }

                val puzzles = mutableListOf<Puzzle>()
                for (i in 0 until puzzlesArray.length()) {
                    val obj = puzzlesArray.getJSONObject(i)
                    val puzzle = Puzzle(
                        puzzleId = obj.optString("puzzleId", ""),
                        question = obj.optString("question", ""),
                        answer = obj.optString("answer", ""),
                        hint = obj.optString("hint", ""),
                        difficulty = null,
                        puzzleType = obj.optString("format", null),
                        options = if (obj.has("options")) {
                            val optArray = obj.getJSONArray("options")
                            List(optArray.length()) { j -> optArray.getString(j) }
                        } else emptyList()
                    )
                    puzzles.add(puzzle)
                    Log.d(TAG, "✅ Parsed puzzle ${i + 1}: $puzzle")
                }

                Handler(Looper.getMainLooper()).post {
                    Log.d(TAG, "🚀 Returning ${puzzles.size} puzzles and full puzzleSet JSON")
                    onResult(puzzles, puzzleSet.toString())
                }

            } catch (e: Exception) {
                Log.e(TAG, "❌ Parsing error: ${e.message}", e)
                Handler(Looper.getMainLooper()).post { onResult(null, null) }
            }
        }
    })
}

data class DailyPuzzle(
    val question: String,
    val answer: String,
    val hint: String,
    val options: List<String>,
    val difficulty: String = "Easy",
    val puzzleId: String
)

fun fetchCustomPuzzles(
    context: Context,
    userEmail: String? = null,
    offset: Int = 0,
    onResult: (List<CustomPuzzle>) -> Unit
) {
    val baseUrl = "https://puzzleverseai.com/list-custom-puzzles?limit=10&offset=$offset"
    val url = if (userEmail != null) {
        val encodedEmail = URLEncoder.encode(userEmail, "UTF-8")
        "$baseUrl&userEmail=$encodedEmail"
    } else {
        baseUrl
    }

    val request = Request.Builder().url(url).build()
    val client = OkHttpClient()

    client.newCall(request).enqueue(object : Callback {
        override fun onFailure(call: Call, e: IOException) {
            Handler(Looper.getMainLooper()).post {
                // Silently fail for custom puzzles - just return empty list
                onResult(emptyList())
            }
        }

        override fun onResponse(call: Call, response: Response) {
            val body = response.body?.string()

            try {
                if (!response.isSuccessful || body == null) {
                    Handler(Looper.getMainLooper()).post {
                        onResult(emptyList())
                    }
                    return
                }

                val json = JSONObject(body)
                val puzzleArray = json.getJSONArray("puzzleData")

                val puzzles = mutableListOf<CustomPuzzle>()
                val seenIds = mutableSetOf<String>()

                for (i in 0 until puzzleArray.length()) {
                    val obj = puzzleArray.getJSONObject(i)
                    val puzzleId = obj.getString("id")

                    if (seenIds.contains(puzzleId)) {
                        continue
                    }

                    seenIds.add(puzzleId)

                    val createdAtString = obj.optString("createdAt", "")
                    val updatedAtString = obj.optString("updatedAt", "")
                    val createdAtMillis = parseIsoDateToMillis(createdAtString)
                    val updatedAtMillis = parseIsoDateToMillis(updatedAtString)

                    val puzzle = CustomPuzzle(
                        id = puzzleId,
                        name = obj.getString("name"),
                        numPuzzles = obj.getString("numPuzzles"),
                        format = obj.getString("format"),
                        creator = obj.getString("creator"),
                        status = obj.getString("status"),
                        createdAt = createdAtMillis,
                        updatedAt = updatedAtMillis
                    )
                    puzzles.add(puzzle)
                }

                Handler(Looper.getMainLooper()).post {
                    onResult(puzzles)
                }
            } catch (e: Exception) {
                Handler(Looper.getMainLooper()).post {
                    onResult(emptyList())
                }
            }
        }
    })
}

// RECOMMENDED: Safe version switching with SimpleDateFormat fallback
fun parseIsoDateToMillis(isoDate: String): Long {
    if (isoDate.isBlank()) return 0L

    return try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // Use modern API for Android O and above (API 26+)
            parseIsoDateModern(isoDate)
        } else {
            // Use SimpleDateFormat for older versions (API < 26)
            parseIsoDateLegacy(isoDate)
        }
    } catch (e: Exception) {
        Log.e("DateParse", "All parsing methods failed for: $isoDate", e)
        // Return current time as final fallback
        System.currentTimeMillis()
    }
}

// Modern parsing for API 26+
@TargetApi(Build.VERSION_CODES.O)
private fun parseIsoDateModern(isoDate: String): Long {
    val formatter = DateTimeFormatter.ISO_OFFSET_DATE_TIME
    val instant = OffsetDateTime.parse(isoDate, formatter).toInstant()
    return instant.toEpochMilli()
}

// RECOMMENDED: SimpleDateFormat approach (more reliable)
private fun parseIsoDateLegacy(isoDate: String): Long {
    return try {
        // Common ISO 8601 patterns your API might return
        val formats = listOf(
            "yyyy-MM-dd'T'HH:mm:ss.SSSXXX",    // 2024-01-15T10:30:45.123+05:30
            "yyyy-MM-dd'T'HH:mm:ssXXX",        // 2024-01-15T10:30:45+05:30
            "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",    // 2024-01-15T10:30:45.123Z
            "yyyy-MM-dd'T'HH:mm:ss'Z'",        // 2024-01-15T10:30:45Z
            "yyyy-MM-dd'T'HH:mm:ss.SSS",       // 2024-01-15T10:30:45.123
            "yyyy-MM-dd'T'HH:mm:ss",           // 2024-01-15T10:30:45
            "yyyy-MM-dd HH:mm:ss",             // 2024-01-15 10:30:45 (fallback)
            "yyyy-MM-dd"                       // 2024-01-15 (date only)
        )

        for (pattern in formats) {
            try {
                val sdf = SimpleDateFormat(pattern, Locale.US)
                // Handle timezone properly
                if (pattern.contains("Z") && !pattern.contains("XXX")) {
                    sdf.timeZone = TimeZone.getTimeZone("UTC")
                }
                return sdf.parse(isoDate)?.time ?: continue
            } catch (e: Exception) {
                // Try next format
                continue
            }
        }

        Log.w("DateParse", "Could not parse date with any format: $isoDate")
        System.currentTimeMillis()

    } catch (e: Exception) {
        Log.e("DateParse", "Legacy date parsing failed: $isoDate", e)
        System.currentTimeMillis()
    }
}

// ALTERNATIVE: Regex-based approach (use only if SimpleDateFormat fails)
// RISK ASSESSMENT: Medium-Low risk for basic ISO dates, but less robust
private fun parseIsoDateRegex(isoDate: String): Long {
    return try {
        // More comprehensive regex for ISO 8601
        val regex = """(\d{4})-(\d{2})-(\d{2})T(\d{2}):(\d{2}):(\d{2})(?:\.(\d{1,3}))?(?:Z|([+-]\d{2}):?(\d{2}))?""".toRegex()
        val matchResult = regex.find(isoDate)

        if (matchResult != null) {
            val groups = matchResult.groupValues
            val year = groups[1].toInt()
            val month = groups[2].toInt() - 1 // Calendar month is 0-based
            val day = groups[3].toInt()
            val hour = groups[4].toInt()
            val minute = groups[5].toInt()
            val second = groups[6].toInt()
            val milliseconds = if (groups[7].isNotEmpty()) {
                // Pad to 3 digits if needed
                groups[7].padEnd(3, '0').take(3).toInt()
            } else 0

            val calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
            calendar.set(year, month, day, hour, minute, second)
            calendar.set(Calendar.MILLISECOND, milliseconds)

            // Handle timezone offset if present
            if (groups[8].isNotEmpty() && groups[9].isNotEmpty()) {
                val offsetHours = groups[8].toInt()
                val offsetMinutes = groups[9].toInt()
                val offsetMillis = (offsetHours * 60 + offsetMinutes) * 60 * 1000
                calendar.timeInMillis - offsetMillis
            } else {
                calendar.timeInMillis
            }
        } else {
            Log.w("DateParse", "Regex could not match date: $isoDate")
            System.currentTimeMillis()
        }
    } catch (e: Exception) {
        Log.e("DateParse", "Regex date parsing failed: $isoDate", e)
        System.currentTimeMillis()
    }
}

// Add these filtering functions
