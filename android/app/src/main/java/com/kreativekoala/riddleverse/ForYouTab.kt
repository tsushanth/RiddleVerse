package com.kreativekoala.riddleverse

import android.content.Context
import android.content.Intent
import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.kreativekoala.riddleverse.ui.theme.*
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.Image
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import com.google.firebase.auth.FirebaseAuth
import com.kreativekoala.riddleverse.QuizCategories.getQuizCategoryForType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun ForYouTab(
    onPuzzleTypeSelected: (String) -> Unit,
    onRefresh: () -> Unit,
    onNavigateToCreate: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("riddleverse_prefs", Context.MODE_PRIVATE) }
    var showEarnBanner by remember { mutableStateOf(!prefs.getBoolean("earn_banner_dismissed", false)) }
    val screenHeight = LocalConfiguration.current.screenHeightDp.dp
    val coroutineScope = rememberCoroutineScope()

    // Single puzzle queue manager instance
    val puzzleQueueManager = remember { PuzzleQueueManager.getInstance(context) }
    val userStatsManager = remember { UserStatsManager.getInstance(context) }

    // State for content sections
    var userFavorites by remember { mutableStateOf<List<UserFavoritePuzzle>>(emptyList()) }
    var trendingPuzzles by remember { mutableStateOf<List<TrendingPuzzle>>(emptyList()) }
    var recommendedPuzzles by remember { mutableStateOf<List<RecommendedPuzzle>>(emptyList()) }
    var puzzleGroups by remember { mutableStateOf<List<PuzzleGroup>>(emptyList()) }
    var recentPuzzleTypes by remember { mutableStateOf<List<RecentPuzzleType>>(emptyList()) }
    var dailyQuickActions by remember { mutableStateOf<List<RecentPuzzleType>>(emptyList()) }
    var availablePuzzleTypes by remember { mutableStateOf<Set<String>>(emptySet()) }
    var isLoading by remember { mutableStateOf(true) }

    // Initialize content and fetch today's puzzles
    LaunchedEffect(Unit) {
        try {
            isLoading = true

            // Get recent puzzles and daily quick actions first
            recentPuzzleTypes = RecentPuzzlesManager.getRecentPuzzleTypes(context)
            dailyQuickActions = RecentPuzzlesManager.getDailyQuickActions(context)

            // Get user favorites (both offline + server types)
            userFavorites = getUserTopFavorites(userStatsManager)

            // Get trending puzzles (mix of offline + server types)
            trendingPuzzles = getGlobalTrending()

            // Get recommendations based on favorites
            recommendedPuzzles = getPersonalizedRecommendations(userFavorites, userStatsManager)

            // Get puzzle groups
            val userId = FirebaseAuth.getInstance().currentUser?.email ?: ""
            puzzleGroups = HardcodedPuzzleGroups.getAllGroups(context, userId)

            // Get currently available puzzle types
            availablePuzzleTypes = puzzleQueueManager.getAvailablePuzzleTypes()

            // Tell PuzzleQueueManager to fetch today's puzzles for all needed types
            val neededUserFavorites = userFavorites.map { it.puzzleType }
            val neededTrending = trendingPuzzles.map { it.puzzleType }
            val neededRecommended = recommendedPuzzles.map { it.puzzleType }

            puzzleQueueManager.fetchTodaysPuzzlesForForYou(
                userFavorites = neededUserFavorites,
                trending = neededTrending,
                recommended = neededRecommended
            )

            Log.d("ForYouTab", "Requested today's puzzles: favorites=$neededUserFavorites, trending=$neededTrending, recommended=$neededRecommended")
            Log.d("ForYouTab", "Recent puzzles: ${recentPuzzleTypes.map { it.puzzleType }}")
            Log.d("ForYouTab", "Daily quick actions: ${dailyQuickActions.map { it.puzzleType }}")

        } catch (e: Exception) {
            Log.e("ForYouTab", "Error initializing For You tab", e)
        } finally {
            isLoading = false
        }
    }

    // Listen for availability changes
    LaunchedEffect(Unit) {
        val listener: (String, Boolean) -> Unit = { puzzleType, isAvailable ->
            if (isAvailable) {
                availablePuzzleTypes = availablePuzzleTypes + puzzleType
                Log.d("ForYouTab", "Puzzle type now available: $puzzleType")
            } else {
                availablePuzzleTypes = availablePuzzleTypes - puzzleType
            }
        }

        puzzleQueueManager.addAvailabilityListener(listener)
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
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
                        text = stringResource(R.string.tab_for_you),
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold,
                        color = RvInk
                    )
                    Text(
                        text = "${availablePuzzleTypes.size} puzzle types ready",
                        fontSize = 14.sp,
                        color = if (availablePuzzleTypes.isNotEmpty()) RvSuccessEdge else RvInkSoft
                    )
                }

                IconButton(
                    onClick = {
                        onRefresh()
                        // Re-fetch today's puzzles for current sections
                        coroutineScope.launch {
                            val neededTypes = (userFavorites.map { it.puzzleType } +
                                    trendingPuzzles.map { it.puzzleType } +
                                    recommendedPuzzles.map { it.puzzleType }).distinct()
                            puzzleQueueManager.fetchTodaysPuzzlesForForYou(neededTypes, emptyList(), emptyList())
                        }
                    },
                    modifier = Modifier
                        .size(40.dp)
                        .background(
                            color = MaterialTheme.colorScheme.primary,
                            shape = RoundedCornerShape(20.dp)
                        )
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = stringResource(R.string.reset),
                        tint = RvOnTone
                    )
                }
            }
        }

        // Loading state
        if (isLoading) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(screenHeight * 0.22f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(32.dp),
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "Loading your personalized puzzles...",
                            fontSize = 14.sp,
                            color = RvInkSoft
                        )
                    }
                }
            }
        } else {
            // Telegram bot banner
            item {
                TelegramBotBanner()
            }

            // Earn money banner
            if (showEarnBanner) {
                item {
                    EarnMoneyBanner(
                        onTap = { onNavigateToCreate?.invoke() },
                        onDismiss = {
                            showEarnBanner = false
                            prefs.edit().putBoolean("earn_banner_dismissed", true).apply()
                        }
                    )
                }
            }

            // Quick Actions
            item {
                QuickActionsRow(
                    userFavorites = userFavorites,
                    recentPuzzleTypes = recentPuzzleTypes,
                    dailyQuickActions = dailyQuickActions,
                    availablePuzzleTypes = availablePuzzleTypes,
                    onPuzzleSelected = { puzzleType ->
                        // Track as recent puzzle
                        RecentPuzzlesManager.addRecentPuzzle(context, puzzleType)

                        coroutineScope.launch {
                            val puzzle = puzzleQueueManager.getNextPuzzleForPlay(puzzleType)
                            if (puzzle != null) {
                                onPuzzleTypeSelected(puzzleType)
                            }
                        }
                    },
                    onRandomSelected = {
                        if (availablePuzzleTypes.isNotEmpty()) {
                            val randomType = availablePuzzleTypes.random()

                            // Track random selection as recent
                            RecentPuzzlesManager.addRecentPuzzle(context, randomType)

                            coroutineScope.launch {
                                val puzzle = puzzleQueueManager.getNextPuzzleForPlay(randomType)
                                if (puzzle != null) {
                                    onPuzzleTypeSelected(randomType)
                                }
                            }
                        }
                    }
                )
            }

            // Your Favorites
            if (userFavorites.isNotEmpty()) {
                item {
                    FavoritesSection(
                        favorites = userFavorites,
                        availablePuzzleTypes = availablePuzzleTypes,
                        onPuzzleSelected = { puzzleType ->
                            coroutineScope.launch {
                                val puzzle = puzzleQueueManager.getNextPuzzleForPlay(puzzleType)
                                if (puzzle != null) {
                                    onPuzzleTypeSelected(puzzleType)
                                }
                            }
                        }
                    )
                }
            }

            // Puzzle Groups Section
            if (puzzleGroups.isNotEmpty()) {
                item {
                    PuzzleGroupsSection(
                        groups = puzzleGroups,
                        availablePuzzleTypes = availablePuzzleTypes,
                        onGroupSelected = { group ->
                            // Navigate to PuzzleGroupActivity
                            val intent = Intent(context, PuzzleGroupActivity::class.java).apply {
                                putExtra("groupId", group.id)
                                putExtra("groupName", group.name)
                                putExtra("puzzleTypes", group.puzzleTypes.toTypedArray())
                                putExtra("completedTypes", group.completedTypes.toTypedArray())
                                putExtra("difficulty", group.difficulty)
                            }
                            context.startActivity(intent)
                        }
                    )
                }
            }

            // Trending Now
            item {
                TrendingSection(
                    trending = trendingPuzzles,
                    availablePuzzleTypes = availablePuzzleTypes,
                    onPuzzleSelected = { puzzleType ->
                        coroutineScope.launch {
                            val puzzle = puzzleQueueManager.getNextPuzzleForPlay(puzzleType)
                            if (puzzle != null) {
                                onPuzzleTypeSelected(puzzleType)
                            }
                        }
                    }
                )
            }

            // Recommended for You
            if (recommendedPuzzles.isNotEmpty()) {
                item {
                    RecommendedSection(
                        recommended = recommendedPuzzles,
                        availablePuzzleTypes = availablePuzzleTypes,
                        onPuzzleSelected = { puzzleType ->
                            coroutineScope.launch {
                                val puzzle = puzzleQueueManager.getNextPuzzleForPlay(puzzleType)
                                if (puzzle != null) {
                                    onPuzzleTypeSelected(puzzleType)
                                }
                            }
                        }
                    )
                }
            }
        }
    }
}

private fun calculateRecencyScore(lastPlayedTimestamp: Long): Double {
    val daysSinceLastPlayed = (System.currentTimeMillis() - lastPlayedTimestamp) / (24 * 60 * 60 * 1000)
    return when {
        daysSinceLastPlayed <= 1 -> 10.0
        daysSinceLastPlayed <= 7 -> 8.0
        daysSinceLastPlayed <= 30 -> 5.0
        else -> 2.0
    }
}

// Data class for recent puzzle types
data class RecentPuzzleType(
    val puzzleType: String,
    val lastPlayed: Long
) {
    fun getQuizCategory(): QuizCategory? {
        return getQuizCategoryForType(puzzleType)
    }
}

@Composable
fun QuickActionsRow(
    userFavorites: List<UserFavoritePuzzle>,
    recentPuzzleTypes: List<RecentPuzzleType>,
    dailyQuickActions: List<RecentPuzzleType>,
    availablePuzzleTypes: Set<String>,
    onPuzzleSelected: (String) -> Unit,
    onRandomSelected: () -> Unit
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.quick_actions),
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = RvInk
            )

            Text(
                text = stringResource(R.string.start_playing_instantly),
                fontSize = 12.sp,
                color = RvInkSoft
            )
        }

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Random challenge button
            item {
                RandomChallengeCard(
                    isEnabled = availablePuzzleTypes.isNotEmpty(),
                    onClick = onRandomSelected
                )
            }

            // Daily quick actions first (these are specifically chosen for today)
            items(dailyQuickActions.filter { availablePuzzleTypes.contains(it.puzzleType) }.take(2)) { quickAction ->
                DailyQuickActionCard(
                    puzzleType = quickAction.puzzleType,
                    onClick = { onPuzzleSelected(quickAction.puzzleType) }
                )
            }

            // Then user favorites (only show available ones, avoid duplicates)
            val usedTypes = dailyQuickActions.map { it.puzzleType }.toSet()
            items(userFavorites.filter {
                availablePuzzleTypes.contains(it.puzzleType) && !usedTypes.contains(it.puzzleType)
            }.take(2)) { favorite ->
                FavoriteQuickCard(
                    favorite = favorite,
                    onClick = { onPuzzleSelected(favorite.puzzleType) }
                )
            }

            // Finally recent puzzles (if we still have space, avoid duplicates)
            val allUsedTypes = usedTypes + userFavorites.map { it.puzzleType }.toSet()
            items(recentPuzzleTypes.filter {
                availablePuzzleTypes.contains(it.puzzleType) && !allUsedTypes.contains(it.puzzleType)
            }.take(2)) { recent ->
                RecentQuickCard(
                    puzzleType = recent.puzzleType,
                    onClick = { onPuzzleSelected(recent.puzzleType) }
                )
            }
        }
    }
}

@Composable
fun FavoritesSection(
    favorites: List<UserFavoritePuzzle>,
    availablePuzzleTypes: Set<String>,
    onPuzzleSelected: (String) -> Unit
) {
    val availableFavorites = favorites.filter { availablePuzzleTypes.contains(it.puzzleType) }

    if (availableFavorites.isNotEmpty()) {
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = stringResource(R.string.your_favorites),
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = RvInk
                    )
                    Text(
                        text = "${availableFavorites.size}/${favorites.size} ready",
                        fontSize = 12.sp,
                        color = if (availableFavorites.isNotEmpty()) RvSuccessEdge else RvSunEdge
                    )
                }

                Box(
                    modifier = Modifier
                        .background(RvSky, RoundedCornerShape(16.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "FAVORITES",
                        color = RvOnTone,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(availableFavorites) { favorite ->
                    PuzzleCard(
                        puzzleType = favorite.puzzleType,
                        subtitle = "${favorite.totalPlays} plays • ${(favorite.winRate * 100).toInt()}% win",
                        backgroundColor = RvSky,
                        onClick = { onPuzzleSelected(favorite.puzzleType) }
                    )
                }
            }
        }
    }
}

@Composable
fun TrendingSection(
    trending: List<TrendingPuzzle>,
    availablePuzzleTypes: Set<String>,
    onPuzzleSelected: (String) -> Unit
) {
    val context = LocalContext.current
    val availableTrending = trending.filter { availablePuzzleTypes.contains(it.puzzleType) }

    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable {
                    // Show toast indicating all trending puzzles are displayed
                    Toast.makeText(
                        context,
                        "Showing all ${trending.size} trending puzzles",
                        Toast.LENGTH_SHORT
                    ).show()
                },
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = stringResource(R.string.trending_now),
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = RvInk
                )
                Text(
                    text = "${availableTrending.size}/${trending.size} ready • Tap to see all",
                    fontSize = 12.sp,
                    color = if (availableTrending.isNotEmpty()) RvSuccessEdge else RvSunEdge
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Box(
                    modifier = Modifier
                        .background(RvSuccessEdge, RoundedCornerShape(16.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "TRENDING",
                        color = RvOnTone,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = "View all trending",
                    tint = RvSuccessEdge,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(availableTrending) { trending ->
                PuzzleCard(
                    puzzleType = trending.puzzleType,
                    subtitle = "#${trending.rank} • ${trending.recentStarts} recent plays",
                    backgroundColor = RvSuccessEdge,
                    showRankBadge = true,
                    rank = trending.rank,
                    onClick = { onPuzzleSelected(trending.puzzleType) }
                )
            }
        }
    }
}

@Composable
fun RecommendedSection(
    recommended: List<RecommendedPuzzle>,
    availablePuzzleTypes: Set<String>,
    onPuzzleSelected: (String) -> Unit
) {
    val availableRecommended = recommended.filter { availablePuzzleTypes.contains(it.puzzleType) }

    if (availableRecommended.isNotEmpty()) {
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = stringResource(R.string.recommended_for_you),
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = RvInk
                    )
                    Text(
                        text = "${availableRecommended.size}/${recommended.size} ready",
                        fontSize = 12.sp,
                        color = if (availableRecommended.isNotEmpty()) RvSuccessEdge else RvSunEdge
                    )
                }

                Box(
                    modifier = Modifier
                        .background(RvGrape, RoundedCornerShape(16.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "FOR YOU",
                        color = RvOnTone,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(availableRecommended) { recommended ->
                    PuzzleCard(
                        puzzleType = recommended.puzzleType,
                        subtitle = recommended.reason,
                        backgroundColor = RvGrape,
                        showRecBadge = true,
                        onClick = { onPuzzleSelected(recommended.puzzleType) }
                    )
                }
            }
        }
    }
}

@Composable
fun PuzzleCard(
    puzzleType: String,
    subtitle: String,
    backgroundColor: Color,
    showRankBadge: Boolean = false,
    rank: Int = 0,
    showRecBadge: Boolean = false,
    onClick: () -> Unit
) {
    val category = getQuizCategoryForType(puzzleType)
    val imageResource = getImageResourceForCategory(puzzleType)

    Card(
        modifier = Modifier
            .width(140.dp)
            .height(120.dp)
            .clickable { onClick() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = RvSurface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Image section (top portion)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                // Background gradient
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            brush = Brush.linearGradient(
                                listOf(backgroundColor, backgroundColor.copy(alpha = 0.8f))
                            ),
                            shape = RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp)
                        )
                )

                // Show image if available
                if (imageResource != null) {
                    Image(
                        painter = painterResource(id = imageResource),
                        contentDescription = "${category?.title ?: puzzleType} preview",
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp)),
                        contentScale = ContentScale.Crop,
                        alpha = 0.95f
                    )
                } else {
                    // Show icon as placeholder when no image
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = category?.icon ?: Icons.Default.Extension,
                            contentDescription = null,
                            tint = RvOnTone,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }

                // Badges overlay
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp),
                    horizontalArrangement = Arrangement.End
                ) {
                    if (showRankBadge) {
                        Box(
                            modifier = Modifier
                                .background(RvCanvas.copy(alpha = 0.9f), RoundedCornerShape(4.dp))
                                .padding(horizontal = 4.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "#$rank",
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Bold,
                                color = backgroundColor
                            )
                        }
                    } else if (showRecBadge) {
                        Box(
                            modifier = Modifier
                                .background(RvCanvas.copy(alpha = 0.9f), RoundedCornerShape(4.dp))
                                .padding(horizontal = 4.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "REC",
                                fontSize = 7.sp,
                                fontWeight = FontWeight.Bold,
                                color = backgroundColor
                            )
                        }
                    }
                }
            }

            // Text section (bottom portion)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(RvSurface)
                    .padding(8.dp)
            ) {
                Text(
                    text = category?.title ?: puzzleType,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = RvInk,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Text(
                    text = subtitle,
                    fontSize = 9.sp,
                    color = RvInkSoft,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
fun FavoriteQuickCard(
    favorite: UserFavoritePuzzle,
    onClick: () -> Unit
) {
    val category = getQuizCategoryForType(favorite.puzzleType)
    val imageResource = getImageResourceForCategory(favorite.puzzleType)

    Card(
        modifier = Modifier
            .size(80.dp)
            .clickable { onClick() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = RvSurface),
        border = androidx.compose.foundation.BorderStroke(2.dp, RvOutline),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // Background gradient
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        brush = Brush.linearGradient(
                            listOf(RvSky, RvSkyEdge)
                        )
                    )
            )

            // Show image if available
            if (imageResource != null) {
                Image(
                    painter = painterResource(id = imageResource),
                    contentDescription = "${category?.title ?: favorite.puzzleType} preview",
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(16.dp)),
                    contentScale = ContentScale.Crop,
                    alpha = 1f
                )
            }

            // Overlay with icon and text
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = if (imageResource != null) 4.dp else 0.dp),
                contentAlignment = if (imageResource != null) Alignment.TopCenter else Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    if (imageResource == null) {
                        Icon(
                            imageVector = category?.icon ?: Icons.Default.Star,
                            contentDescription = null,
                            tint = RvOnTone,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    if (imageResource == null) {
                    Text(
                            text = category?.title?.split(" ")?.firstOrNull() ?: favorite.puzzleType,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = RvOnTone,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
            // Clean label band under the image
            if (imageResource != null) {
                Text(
                    text = category?.title?.split(" ")?.firstOrNull() ?: favorite.puzzleType,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = RvInk,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .background(RvSurfaceRaised)
                        .padding(horizontal = 2.dp, vertical = 3.dp)
                )
            }
        }
    }
}

@Composable
fun RandomChallengeCard(
    isEnabled: Boolean,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .size(80.dp)
            .clickable(enabled = isEnabled) { onClick() },
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    brush = if (isEnabled) {
                        Brush.linearGradient(listOf(RvViolet, RvGrape))
                    } else {
                        Brush.linearGradient(listOf(RvInkSoft, RvInkSoft))
                    }
                ),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Icon(
                    imageVector = if (isEnabled) Icons.Default.Shuffle else Icons.Default.HourglassEmpty,
                    contentDescription = "Random Challenge",
                    tint = RvOnTone,
                    modifier = Modifier.size(20.dp)
                )

                Text(
                    text = if (isEnabled) "Surprise" else stringResource(R.string.loading),
                    color = RvOnTone,
                    fontSize = 8.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )

                Text(
                    text = if (isEnabled) "Me!" else "...",
                    color = RvOnTone,
                    fontSize = 8.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
fun PuzzleGroupsSection(
    groups: List<PuzzleGroup>,
    availablePuzzleTypes: Set<String>,
    onGroupSelected: (PuzzleGroup) -> Unit
) {
    val context = LocalContext.current

    // Filter groups to only show those with at least one available puzzle
    val availableGroups = groups.filter { group ->
        group.puzzleTypes.any { puzzleType ->
            availablePuzzleTypes.contains(puzzleType)
        }
    }

    if (availableGroups.isNotEmpty()) {
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        // Show toast indicating where to view all collections
                        Toast.makeText(
                            context,
                            "Viewing all ${groups.size} puzzle collections",
                            Toast.LENGTH_SHORT
                        ).show()
                    },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = stringResource(R.string.puzzle_collections),
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = RvInk
                    )
                    Text(
                        text = "${availableGroups.size} collections ready • Tap to see all",
                        fontSize = 12.sp,
                        color = if (availableGroups.isNotEmpty()) RvSuccessEdge else RvSunEdge
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .background(RvViolet, RoundedCornerShape(16.dp))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "COLLECTIONS",
                            color = RvOnTone,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Icon(
                        imageVector = Icons.Default.ChevronRight,
                        contentDescription = "View all collections",
                        tint = RvViolet,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(availableGroups) { group ->
                    PuzzleGroupCard(
                        group = group,
                        availablePuzzleTypes = availablePuzzleTypes,
                        onClick = { onGroupSelected(group) }
                    )
                }
            }
        }
    }
}

@Composable
fun PuzzleGroupCard(
    group: PuzzleGroup,
    availablePuzzleTypes: Set<String>,
    onClick: () -> Unit
) {
    val availablePuzzlesInGroup = group.puzzleTypes.count { availablePuzzleTypes.contains(it) }
    val totalPuzzlesInGroup = group.puzzleTypes.size
    val progress = if (group.totalTypes > 0) {
        group.completedCount.toFloat() / group.totalTypes.toFloat()
    } else 0f

    Card(
        modifier = Modifier
            .width(160.dp)
            .height(120.dp)
            .clickable { onClick() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = RvSurface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        brush = Brush.linearGradient(
                            listOf(RvViolet, RvGrape)
                        ),
                        shape = RoundedCornerShape(16.dp)
                    )
            )

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(12.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    Icon(
                        imageVector = when (group.category) {
                            "Math" -> Icons.Default.Calculate
                            "Memory" -> Icons.Default.Psychology
                            "English" -> Icons.Default.Spellcheck
                            "Logic" -> Icons.Default.Extension
                            else -> Icons.Default.Extension
                        },
                        contentDescription = null,
                        tint = RvOnTone,
                        modifier = Modifier.size(20.dp)
                    )

                    Column(
                        horizontalAlignment = Alignment.End
                    ) {
                        Text(
                            text = "$availablePuzzlesInGroup/$totalPuzzlesInGroup",
                            fontSize = 10.sp,
                            color = RvOnTone.copy(alpha = 0.8f),
                            fontWeight = FontWeight.Medium
                        )

                        if (group.completedCount > 0) {
                            Box(
                                modifier = Modifier
                                    .background(RvOnTone.copy(alpha = 0.2f), RoundedCornerShape(4.dp))
                                    .padding(horizontal = 4.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = "${group.completedCount}/${group.totalTypes}",
                                    fontSize = 8.sp,
                                    color = RvOnTone,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }

                // Content
                Column(
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = group.name,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = RvOnTone,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    if (progress > 0) {
                        LinearProgressIndicator(
                            progress = progress,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(3.dp)
                                .clip(RoundedCornerShape(1.5.dp)),
                            color = RvOnTone,
                            trackColor = RvOnTone.copy(alpha = 0.3f)
                        )
                    }

                    Text(
                        text = "${group.puzzleTypes.size} puzzle types • ${group.difficulty}",
                        fontSize = 10.sp,
                        color = RvOnTone.copy(alpha = 0.8f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

// Data Classes (keep existing ones)
data class TrendingPuzzle(
    val puzzleType: String,
    val rank: Int,
    val recentStarts: Int,
    val growthRate: Float
)

data class RecommendedPuzzle(
    val puzzleType: String,
    val reason: String,
    val similarityScore: Double
)

data class UserFavoritePuzzle(
    val puzzleType: String,
    val totalPlays: Int,
    val winRate: Float,
    val lastPlayedTimestamp: Long,
    val priorityScore: Double
)

// Helper functions (simplified without other managers)
suspend fun getUserTopFavorites(userStatsManager: UserStatsManager): List<UserFavoritePuzzle> {
    return withContext(Dispatchers.IO) {
        val allStats = userStatsManager.getAllUserStats()

        allStats.filter { it.value.totalPlays > 0 }
            .map { (puzzleType, stats) ->
                val recencyScore = calculateRecencyScore(stats.lastPlayedTimestamp)
                val engagementScore = stats.totalPlays * stats.winRate
                val priorityScore = (recencyScore * 0.4) + (engagementScore * 0.6)

                UserFavoritePuzzle(
                    puzzleType = puzzleType,
                    totalPlays = stats.totalPlays,
                    winRate = stats.winRate,
                    lastPlayedTimestamp = stats.lastPlayedTimestamp,
                    priorityScore = priorityScore
                )
            }
            .sortedByDescending { it.priorityScore }
            .take(3)
    }
}

suspend fun getGlobalTrending(): List<TrendingPuzzle> {
    return withContext(Dispatchers.IO) {
        // Mix of offline and server types - prioritize offline for reliability
        listOf(
            TrendingPuzzle("realorai", 1, 300, 0.20f),
            TrendingPuzzle("memorysquares", 2, 593, 0.15f),  // offline
            TrendingPuzzle("math", 3, 450, 0.23f),
            TrendingPuzzle("colormatching", 4, 380, 0.18f),  // offline
            TrendingPuzzle("anagram", 5, 320, 0.12f),
            TrendingPuzzle("triangledotmemory", 6, 285, 0.10f)  // offline
        )
    }
}

suspend fun getPersonalizedRecommendations(
    userFavorites: List<UserFavoritePuzzle>,
    userStatsManager: UserStatsManager
): List<RecommendedPuzzle> {
    return withContext(Dispatchers.IO) {
        val recommendations = mutableListOf<RecommendedPuzzle>()

        if (userFavorites.isNotEmpty()) {
            // Use PuzzleRecommendations to get smart suggestions based on user's favorites
            userFavorites.forEach { favorite ->
                val suggestedTypes = PuzzleRecommendations.getRecommendationsFor(favorite.puzzleType)
                suggestedTypes.forEach { suggestedType ->
                    if (recommendations.none { it.puzzleType == suggestedType }) {
                        val category = getQuizCategoryForType(favorite.puzzleType)
                        recommendations.add(RecommendedPuzzle(
                            puzzleType = suggestedType,
                            reason = "Based on ${category?.title ?: favorite.puzzleType}",
                            similarityScore = 0.8
                        ))
                    }
                }
            }
        }

        // If we still don't have enough, add guaranteed reliable puzzles
        if (recommendations.size < 4) {
            val reliableRecommendations = listOf(
                RecommendedPuzzle("memorysquares", "Great for memory training", 0.9),
                RecommendedPuzzle("colormatching", "Quick visual challenge", 0.8),
                RecommendedPuzzle("triangledotmemory", "Pattern recognition", 0.7),
                RecommendedPuzzle("symbolswipe", "Fast-paced reaction game", 0.6),
                RecommendedPuzzle("mathcomparison", "Number sense training", 0.5)
            )

            reliableRecommendations.forEach { reliableRec ->
                if (recommendations.none { it.puzzleType == reliableRec.puzzleType } &&
                    recommendations.size < 4) {
                    recommendations.add(reliableRec)
                }
            }
        }

        recommendations.take(4)
    }
}

@Composable
fun DailyQuickActionCard(
    puzzleType: String,
    onClick: () -> Unit
) {
    val category = getQuizCategoryForType(puzzleType)
    val imageResource = getImageResourceForCategory(puzzleType)

    Card(
        modifier = Modifier
            .size(80.dp)
            .clickable { onClick() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = RvSurface),
        border = androidx.compose.foundation.BorderStroke(2.dp, RvOutline),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // Background gradient
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        brush = Brush.linearGradient(
                            listOf(RvSuccess, RvSuccessEdge)
                        )
                    )
            )

            // Show image if available
            if (imageResource != null) {
                Image(
                    painter = painterResource(id = imageResource),
                    contentDescription = "${category?.title ?: puzzleType} preview",
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(16.dp)),
                    contentScale = ContentScale.Crop,
                    alpha = 1f
                )
            }

            // Overlay content
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = if (imageResource != null) 4.dp else 0.dp),
                contentAlignment = if (imageResource != null) Alignment.TopCenter else Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    // Small "TODAY" badge at top
                    Box(
                        modifier = Modifier
                            .background(RvCanvas.copy(alpha = 0.9f), RoundedCornerShape(4.dp))
                            .padding(horizontal = 4.dp, vertical = 1.dp)
                    ) {
                        Text(
                            text = "TODAY",
                            color = RvSuccessEdge,
                            fontSize = 6.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    if (imageResource == null) {
                        Icon(
                            imageVector = category?.icon ?: Icons.Default.Today,
                            contentDescription = null,
                            tint = RvOnTone,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    if (imageResource == null) {
                    Text(
                            text = category?.title?.split(" ")?.firstOrNull() ?: puzzleType,
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold,
                            color = RvOnTone,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun RecentQuickCard(
    puzzleType: String,
    onClick: () -> Unit
) {
    val category = getQuizCategoryForType(puzzleType)
    val imageResource = getImageResourceForCategory(puzzleType)

    Card(
        modifier = Modifier
            .size(80.dp)
            .clickable { onClick() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = RvSurface),
        border = androidx.compose.foundation.BorderStroke(2.dp, RvOutline),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // Background gradient
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        brush = Brush.linearGradient(
                            listOf(RvGrape, RvGrapeEdge)
                        )
                    )
            )

            // Show image if available
            if (imageResource != null) {
                Image(
                    painter = painterResource(id = imageResource),
                    contentDescription = "${category?.title ?: puzzleType} preview",
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(16.dp)),
                    contentScale = ContentScale.Crop,
                    alpha = 1f
                )
            }

            // Overlay content
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = if (imageResource != null) 4.dp else 0.dp),
                contentAlignment = if (imageResource != null) Alignment.TopCenter else Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    // Small "RECENT" badge at top
                    Box(
                        modifier = Modifier
                            .background(RvCanvas.copy(alpha = 0.9f), RoundedCornerShape(4.dp))
                            .padding(horizontal = 4.dp, vertical = 1.dp)
                    ) {
                        Text(
                            text = "RECENT",
                            color = RvGrape,
                            fontSize = 6.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    if (imageResource == null) {
                        Icon(
                            imageVector = category?.icon ?: Icons.Default.History,
                            contentDescription = null,
                            tint = RvOnTone,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    if (imageResource == null) {
                    Text(
                            text = category?.title?.split(" ")?.firstOrNull() ?: puzzleType,
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold,
                            color = RvOnTone,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
            // Clean label band under the image
            if (imageResource != null) {
                Text(
                    text = category?.title?.split(" ")?.firstOrNull() ?: puzzleType,
                    fontSize = 8.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = RvInk,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .background(RvSurfaceRaised)
                        .padding(horizontal = 2.dp, vertical = 3.dp)
                )
            }
        }
    }
}

@Composable
fun EarnMoneyBanner(
    onTap: () -> Unit,
    onDismiss: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(
                Brush.horizontalGradient(
                    colors = listOf(RvSuccess, RvSuccessEdge)
                )
            ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .clickable { onTap() }
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(text = "💰", fontSize = 32.sp)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Earn real cash on RiddleVerse",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = RvOnTone
                )
                Text(
                    text = "Create a game → players find it → you get paid.",
                    fontSize = 12.sp,
                    color = RvOnTone.copy(alpha = 0.85f)
                )
            }
            Icon(
                imageVector = Icons.Default.ArrowForward,
                contentDescription = null,
                tint = RvOnTone.copy(alpha = 0.9f),
                modifier = Modifier.size(22.dp)
            )
        }
        IconButton(onClick = onDismiss) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "Dismiss",
                tint = RvOnTone.copy(alpha = 0.7f),
                modifier = Modifier.size(16.dp)
            )
        }
    }
}
