package com.kreativekoala.riddleverse

import android.annotation.TargetApi
import android.content.Context
import android.content.Intent
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.kreativekoala.riddleverse.ui.theme.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import okhttp3.*
import okio.IOException
import org.json.JSONObject
import com.google.gson.Gson
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import java.net.URLEncoder
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.compose.setContent
import androidx.annotation.RequiresApi
import androidx.compose.foundation.BorderStroke
import com.kreativekoala.riddleverse.ui.theme.RiddleVerseTheme
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.navigation.NavHostController
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import androidx.navigation.compose.rememberNavController
import kotlinx.coroutines.delay
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.TimeZone

enum class PuzzleFilter(val displayName: String) {
    ALL("All"),
    IMAGE_PUZZLE("Image Puzzle"),
    MUSIC_PUZZLE("Music Puzzle"),
    CROSSWORD("Crossword"),
    WORD_SEARCH("Word Search"),
    MULTIPLE_CHOICE("Multiple Choice"),
    ANAGRAM("Anagram"),
    RIDDLES("Riddles"),
    TRIVIA("Trivia"),
    PUZZLES("Puzzles"),
    WORD_GAMES("Word Games"),
    EASY("Easy"),
    MEDIUM("Medium"),
    HARD("Hard"),
    FEATURED("Featured"),
    TRENDING("Trending"),
}

@Composable
fun EnhancedCustomPuzzlesTabWithSearch(
    allPuzzles: List<CustomPuzzle>,
    yourPuzzles: List<CustomPuzzle>,
    featuredPuzzles: List<CustomPuzzle>,
    trendingPuzzles: List<CustomPuzzle>,
    isLoading: Boolean,
    isLoadingMore: Boolean,
    onCreatePuzzle: () -> Unit,
    onPuzzleSelected: (CustomPuzzle) -> Unit,
    onLoadMore: (Boolean) -> Unit,
    onRefresh: () -> Unit,
    onViewLeaderboard: (String) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedFilter by remember { mutableStateOf(PuzzleFilter.ALL) }
    var selectedSort by remember { mutableStateOf(PuzzleSort.NEWEST) }
    var selectedTab by remember { mutableStateOf(CustomPuzzleTab.BROWSE) }
    var showSortSheet by remember { mutableStateOf(false) }
    val context = LocalContext.current

    val prefs = remember { context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE) }
    var showNewFeatureBadge by remember {
        mutableStateOf(prefs.getBoolean("show_custom_puzzles_badge", true))
    }

    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        CustomPuzzlesHeader(
            onCreatePuzzle = onCreatePuzzle,
            onRefresh = onRefresh,
            onSortClick = { showSortSheet = true } // NEW - actually works
        )

        if (showSortSheet) {
            SortBottomSheet(
                selectedSort = selectedSort,
                onSortSelected = {
                    selectedSort = it
                    showSortSheet = false
                },
                onDismiss = { showSortSheet = false }
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        SearchBar(
            query = searchQuery,
            onQueryChange = { searchQuery = it }
        )

        Spacer(modifier = Modifier.height(12.dp))

        FilterChipsRow(
            selectedFilter = selectedFilter,
            onFilterSelected = { selectedFilter = it }
        )

        Spacer(modifier = Modifier.height(16.dp))

        CustomPuzzleTabRow(
            selectedTab = selectedTab,
            onTabSelected = { selectedTab = it }
        )

        Spacer(modifier = Modifier.height(16.dp))

        if (isLoading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else {
            val puzzlesToShow = when (selectedTab) {
                CustomPuzzleTab.BROWSE -> allPuzzles
                CustomPuzzleTab.YOUR_PUZZLES -> yourPuzzles
                CustomPuzzleTab.FEATURED -> featuredPuzzles
            }

            val filteredPuzzles = filterAndSortPuzzles(
                puzzles = puzzlesToShow,
                searchQuery = searchQuery,
                filter = selectedFilter,
                sort = selectedSort
            )

            PuzzlesList(
                puzzles = filteredPuzzles,
                isLoadingMore = isLoadingMore,
                onPuzzleSelected = onPuzzleSelected,
                onViewLeaderboard = onViewLeaderboard,
                onLoadMore = { onLoadMore(selectedTab == CustomPuzzleTab.YOUR_PUZZLES) }
            )
        }
    }
}

@Composable
fun FilterChipsRow(
    selectedFilter: PuzzleFilter,
    onFilterSelected: (PuzzleFilter) -> Unit
) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(horizontal = 16.dp)
    ) {
        items(PuzzleFilter.values()) { filter ->
            FilterChip(
                selected = selectedFilter == filter,
                onClick = { onFilterSelected(filter) },
                label = {
                    Text(
                        text = filter.displayName,
                        fontSize = 12.sp,
                        fontWeight = if (selectedFilter == filter) FontWeight.Bold else FontWeight.Normal
                    )
                },
                leadingIcon = {
                    Icon(
                        imageVector = getFilterIcon(filter),
                        contentDescription = filter.displayName,
                        modifier = Modifier.size(16.dp)
                    )
                },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = RvViolet,
                    selectedLabelColor = RvOnTone,
                    selectedLeadingIconColor = RvOnTone
                )
            )
        }
    }
}

fun getFilterIcon(filter: PuzzleFilter): ImageVector {
    return when (filter) {
        PuzzleFilter.ALL -> Icons.Default.FilterList
        PuzzleFilter.WORD_SEARCH -> Icons.Default.Search
        PuzzleFilter.CROSSWORD -> Icons.Default.GridOn
        PuzzleFilter.MULTIPLE_CHOICE -> Icons.Default.ChecklistRtl
        PuzzleFilter.ANAGRAM -> Icons.Default.Shuffle
        PuzzleFilter.RIDDLES -> Icons.Default.Psychology
        PuzzleFilter.TRIVIA -> Icons.Default.Quiz
        PuzzleFilter.PUZZLES -> Icons.Default.Extension
        PuzzleFilter.WORD_GAMES -> Icons.Default.TextFields
        PuzzleFilter.EASY -> Icons.Default.SentimentSatisfied
        PuzzleFilter.MEDIUM -> Icons.Default.SentimentNeutral
        PuzzleFilter.HARD -> Icons.Default.SentimentVeryDissatisfied
        PuzzleFilter.FEATURED -> Icons.Default.Star
        PuzzleFilter.TRENDING -> Icons.Default.TrendingUp
        PuzzleFilter.IMAGE_PUZZLE -> Icons.Default.Image
        PuzzleFilter.MUSIC_PUZZLE -> Icons.Default.MusicNote

    }
}

fun filterAndSortPuzzles(
    puzzles: List<CustomPuzzle>,
    searchQuery: String,
    filter: PuzzleFilter,
    sort: PuzzleSort
): List<CustomPuzzle> {
    var filtered = puzzles

    // Apply search filter
    if (searchQuery.isNotBlank()) {
        filtered = filtered.filter { puzzle ->
            puzzle.name.contains(searchQuery, ignoreCase = true) ||
                    puzzle.creator.contains(searchQuery, ignoreCase = true) ||
                    puzzle.topic?.contains(searchQuery, ignoreCase = true) == true ||
                    puzzle.format.contains(searchQuery, ignoreCase = true)
        }
    }

    // Apply category filter
    filtered = when (filter) {
        PuzzleFilter.ALL -> filtered
        PuzzleFilter.RIDDLES -> filtered.filter { it.name.contains("riddle", ignoreCase = true) }
        PuzzleFilter.TRIVIA -> filtered.filter { it.name.contains("trivia", ignoreCase = true) }
        PuzzleFilter.PUZZLES -> filtered.filter { it.name.contains("puzzle", ignoreCase = true) }
        PuzzleFilter.WORD_GAMES -> filtered.filter {
            it.format.contains("word", ignoreCase = true) ||
                    it.format.contains("anagram", ignoreCase = true)
        }
        PuzzleFilter.IMAGE_PUZZLE -> filtered.filter {
            it.format.contains("Image Puzzle", ignoreCase = true)
        }
        PuzzleFilter.MUSIC_PUZZLE -> filtered.filter {
            it.format.contains("Music Puzzle", ignoreCase = true)
        }
        PuzzleFilter.EASY -> filtered.filter { it.difficulty == "Easy" }
        PuzzleFilter.MEDIUM -> filtered.filter { it.difficulty == "Medium" }
        PuzzleFilter.HARD -> filtered.filter { it.difficulty == "Hard" }
        PuzzleFilter.FEATURED -> filtered.filter { it.playCount > 10 && it.averageRating > 4.0 }
        PuzzleFilter.TRENDING -> filtered.filter { it.playCount > 5 }
        PuzzleFilter.CROSSWORD -> filtered.filter {
            it.format.contains("crossword", ignoreCase = true)
        }
        PuzzleFilter.MULTIPLE_CHOICE -> filtered.filter {
            it.format.contains("multiple choice", ignoreCase = true)
        }
        PuzzleFilter.WORD_SEARCH -> filtered.filter {
            it.format.contains("word search", ignoreCase = true)
        }
        PuzzleFilter.ANAGRAM -> filtered.filter {
            it.format.contains("anagram", ignoreCase = true)
        }
    }

    // Apply sorting
    return when (sort) {
        PuzzleSort.NEWEST -> filtered.sortedByDescending { it.createdAt }
        PuzzleSort.OLDEST -> filtered.sortedBy { it.createdAt }
        PuzzleSort.MOST_PLAYED -> filtered.sortedByDescending { it.playCount }
        PuzzleSort.HIGHEST_RATED -> filtered.sortedByDescending { it.averageRating }
        PuzzleSort.ALPHABETICAL -> filtered.sortedBy { it.name }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SortBottomSheet(
    selectedSort: PuzzleSort,
    onSortSelected: (PuzzleSort) -> Unit,
    onDismiss: () -> Unit
) {
    val bottomSheetState = rememberModalBottomSheetState()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = bottomSheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = stringResource(R.string.sort_by),
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            PuzzleSort.values().forEach { sort ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSortSelected(sort) }
                        .padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = selectedSort == sort,
                        onClick = { onSortSelected(sort) }
                    )

                    Spacer(modifier = Modifier.width(12.dp))

                    Text(
                        text = sort.displayName,
                        fontSize = 16.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
fun PuzzlesList(
    puzzles: List<CustomPuzzle>,
    isLoadingMore: Boolean,
    onPuzzleSelected: (CustomPuzzle) -> Unit,
    onViewLeaderboard: (String) -> Unit,
    onLoadMore: () -> Unit
) {
    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(vertical = 8.dp)
    ) {
        itemsIndexed(puzzles) { index, puzzle ->
            EnhancedPuzzleCard(
                puzzle = puzzle,
                isOwner = false,
                onClick = { onPuzzleSelected(puzzle) },
                onLeaderboardClick = { onViewLeaderboard(puzzle.id) }
            )

            // Load more when near end
            if (index >= puzzles.size - 3) {
                LaunchedEffect(index) {
                    onLoadMore()
                }
            }
        }

        if (isLoadingMore) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                }
            }
        }
    }
}

@Composable
fun EnhancedPuzzleCard(
    puzzle: CustomPuzzle,
    isOwner: Boolean,
    onClick: () -> Unit,
    onLeaderboardClick: () -> Unit
) {
    val puzzleIcon = getPuzzleIcon(puzzle.topic, puzzle.format)
    val themeColors = puzzle.themeColors ?: generateThemeFromTopic(puzzle.topic)
    val headerTones = listOf(RvToneViolet, RvToneSky, RvToneMint, RvToneCoral, RvToneGrape)
    val headerTone = headerTones[Math.floorMod(puzzle.topic.hashCode(), headerTones.size)]

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = androidx.compose.foundation.BorderStroke(2.dp, RvOutline),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = RvSurface
        )
    ) {
        Column {
            // Header with theme background
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(80.dp)
                    .background(headerTone.fill)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // Puzzle Icon
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .background(
                                    color = RvOnTone.copy(alpha = 0.2f),
                                    shape = RoundedCornerShape(16.dp)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = puzzleIcon,
                                contentDescription = puzzle.name,
                                tint = RvOnTone,
                                modifier = Modifier.size(28.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Column {
                            Text(
                                text = puzzle.name,
                                color = RvOnTone,
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )

                            Row(
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (themeColors.emoji != null) {
                                    Text(
                                        text = themeColors.emoji,
                                        fontSize = 12.sp,
                                        modifier = Modifier.padding(end = 4.dp)
                                    )
                                }

                                Text(
                                    text = "${puzzle.numPuzzles} ${puzzle.format}",
                                    color = RvOnTone.copy(alpha = 0.9f),
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }

                    // Status and Owner Badge
                    Column(
                        horizontalAlignment = Alignment.End
                    ) {
                        if (isOwner) {
                            Badge(
                                containerColor = RvOnTone.copy(alpha = 0.2f),
                                contentColor = RvOnTone
                            ) {
                                Text(
                                    text = "YOURS",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        if (puzzle.status != "completed") {
                            Spacer(modifier = Modifier.height(4.dp))
                            StatusBadge(status = puzzle.status)
                        }
                    }
                }
            }

            // Content Area
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                // Puzzle Stats Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Difficulty Badge
                        puzzle.difficulty?.let { difficulty ->
                            DifficultyBadge(difficulty = difficulty)
                            Spacer(modifier = Modifier.width(8.dp))
                        }

                        // Rating
                        if (puzzle.averageRating > 0) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Star,
                                    contentDescription = "Rating",
                                    tint = RvSunEdge,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = String.format("%.1f", puzzle.averageRating),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }

                    // Play Count
                    if (puzzle.playCount > 0) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = "Plays",
                                tint = RvInkSoft,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "${puzzle.playCount} plays",
                                fontSize = 12.sp,
                                color = RvInkSoft
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Creator and Date
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = "Creator",
                            tint = RvInkSoft,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "By ${puzzle.creator}",
                            fontSize = 12.sp,
                            color = RvInkSoft
                        )
                    }

                    Text(
                        text = formatDate(puzzle.updatedAt),
                        fontSize = 12.sp,
                        color = RvInkSoft
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Action Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Play Button
                    Button(
                        onClick = onClick,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(android.graphics.Color.parseColor(themeColors.primaryColor))
                        ),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = stringResource(R.string.play),
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(stringResource(R.string.play))
                    }

                    // Leaderboard Button
                    OutlinedButton(
                        onClick = onLeaderboardClick,
                        modifier = Modifier.weight(0.6f),
                        border = BorderStroke(
                            1.dp,
                            Color(android.graphics.Color.parseColor(themeColors.primaryColor))
                        ),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Leaderboard,
                            contentDescription = stringResource(R.string.leaderboard),
                            modifier = Modifier.size(16.dp),
                            tint = Color(android.graphics.Color.parseColor(themeColors.primaryColor))
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun StatItem(
    value: String,
    label: String,
    icon: ImageVector,
    color: Color
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = color,
            modifier = Modifier.size(24.dp)
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = value,
            fontWeight = FontWeight.Bold,
            fontSize = 20.sp,
            color = color
        )

        Text(
            text = label,
            fontSize = 12.sp,
            color = RvInkSoft
        )
    }
}

@Composable
fun SectionHeader(
    title: String,
    subtitle: String? = null,
    icon: ImageVector? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        icon?.let {
            Icon(
                imageVector = it,
                contentDescription = title,
                tint = RvViolet,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
        }

        Column {
            Text(
                text = title,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = RvInk
            )

            subtitle?.let {
                Text(
                    text = it,
                    fontSize = 14.sp,
                    color = RvInkSoft
                )
            }
        }
    }
}

@Composable
fun StatusBadge(status: String) {
    val (backgroundColor, textColor, text) = when (status) {
        "pending" -> Triple(RvWarning.copy(alpha = 0.2f), RvSunEdge, "Processing")
        "failed" -> Triple(RvError.copy(alpha = 0.15f), RvErrorEdge, "Failed")
        "completed" -> Triple(RvSuccess.copy(alpha = 0.15f), RvSuccessEdge, "Ready")
        else -> Triple(RvSurface, RvInkSoft, status)
    }

    Badge(
        containerColor = backgroundColor,
        contentColor = textColor
    ) {
        Text(
            text = text.uppercase(),
            fontSize = 8.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun DifficultyBadge(difficulty: String) {
    val (backgroundColor, textColor) = when (difficulty.lowercase()) {
        "easy" -> Pair(RvSuccess.copy(alpha = 0.15f), RvSuccessEdge)
        "medium" -> Pair(RvWarning.copy(alpha = 0.2f), RvSunEdge)
        "hard" -> Pair(RvError.copy(alpha = 0.15f), RvErrorEdge)
        else -> Pair(RvSurface, RvInkSoft)
    }

    Badge(
        containerColor = backgroundColor,
        contentColor = textColor
    ) {
        Text(
            text = difficulty.uppercase(),
            fontSize = 8.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

// Helper Functions
fun getPuzzleIcon(topic: String?, format: String): ImageVector {
    // Topic-based icons
    topic?.let { topicName ->
        return when (topicName.lowercase()) {
            "math", "mathematics" -> Icons.Default.Calculate
            "science" -> Icons.Default.Science
            "history" -> Icons.Default.HistoryEdu
            "geography" -> Icons.Default.Public
            "sports" -> Icons.Default.SportsBaseball
            "music" -> Icons.Default.MusicNote
            "movies", "cinema" -> Icons.Default.Movie
            "books", "literature" -> Icons.Default.MenuBook
            "technology" -> Icons.Default.Computer
            "nature" -> Icons.Default.Nature
            "food" -> Icons.Default.Restaurant
            "art" -> Icons.Default.Palette
            else -> getFormatIcon(format)
        }
    }

    return getFormatIcon(format)
}

fun getFormatIcon(format: String): ImageVector {
    return when (format.lowercase()) {
        "riddle", "riddles" -> Icons.Default.Psychology
        "trivia" -> Icons.Default.Quiz
        "puzzle" -> Icons.Default.Extension
        "brain teaser" -> Icons.Default.Lightbulb
        "word puzzle" -> Icons.Default.TextFields
        "logic puzzle" -> Icons.Default.AccountTree
        "crossword" -> Icons.Default.GridOn
        "sudoku" -> Icons.Default.Grid3x3
        "anagram" -> Icons.Default.Shuffle
        "multiple choice" -> Icons.Default.ChecklistRtl
        else -> Icons.Default.Extension
    }
}

fun generateThemeFromTopic(topic: String?): PuzzleTheme {
    return when (topic?.lowercase()) {
        "math", "mathematics" -> PuzzleTheme(
            primaryColor = "#667eea",
            secondaryColor = "#764ba2",
            emoji = "🔢"
        )
        "science" -> PuzzleTheme(
            primaryColor = "#4ECDC4",
            secondaryColor = "#44A08D",
            emoji = "🔬"
        )
        "history" -> PuzzleTheme(
            primaryColor = "#FFE066",
            secondaryColor = "#FF9472",
            emoji = "🏛️"
        )
        "geography" -> PuzzleTheme(
            primaryColor = "#74B9FF",
            secondaryColor = "#0984E3",
            emoji = "🌍"
        )
        "sports" -> PuzzleTheme(
            primaryColor = "#00B894",
            secondaryColor = "#00CEC9",
            emoji = "⚽"
        )
        "music" -> PuzzleTheme(
            primaryColor = "#A29BFE",
            secondaryColor = "#6C5CE7",
            emoji = "🎵"
        )
        "movies", "cinema" -> PuzzleTheme(
            primaryColor = "#FF6B6B",
            secondaryColor = "#FF8E8E",
            emoji = "🎬"
        )
        "books", "literature" -> PuzzleTheme(
            primaryColor = "#A8E6CF",
            secondaryColor = "#7FD8BE",
            emoji = "📚"
        )
        "technology" -> PuzzleTheme(
            primaryColor = "#FD79A8",
            secondaryColor = "#E84393",
            emoji = "💻"
        )
        "nature" -> PuzzleTheme(
            primaryColor = "#00B894",
            secondaryColor = "#55A3FF",
            emoji = "🌿"
        )
        "food" -> PuzzleTheme(
            primaryColor = "#FDCB6E",
            secondaryColor = "#E17055",
            emoji = "🍕"
        )
        "art" -> PuzzleTheme(
            primaryColor = "#FD79A8",
            secondaryColor = "#FDCB6E",
            emoji = "🎨"
        )
        else -> PuzzleTheme(
            primaryColor = "#667eea",
            secondaryColor = "#764ba2",
            emoji = "🧩"
        )
    }
}

// Enhanced data fetching functions
fun fetchPuzzleLeaderboard(
    puzzleId: String,
    onResult: (PuzzleLeaderboard?) -> Unit
) {
    val url = "https://puzzleverseai.com/get-puzzle-leaderboard/$puzzleId"
    val client = OkHttpClient()
    val request = Request.Builder().url(url).build()

    client.newCall(request).enqueue(object : Callback {
        override fun onFailure(call: Call, e: IOException) {
            Log.e("PuzzleLeaderboard", "❌ Failed to fetch leaderboard", e)
            Handler(Looper.getMainLooper()).post {
                onResult(null)
            }
        }

        override fun onResponse(call: Call, response: Response) {
            try {
                val body = response.body?.string()
                if (response.isSuccessful && body != null) {
                    val json = JSONObject(body)
                    Log.d("PuzzleLeaderboard", "✅ API response: $body")
                    if (json.getBoolean("success")) {
                        val topPlayersArray = json.getJSONArray("topPlayers")
                        val topPlayers = mutableListOf<LeaderboardEntry>()

                        for (i in 0 until topPlayersArray.length()) {
                            val playerJson = topPlayersArray.getJSONObject(i)
                            topPlayers.add(
                                LeaderboardEntry(
                                    rank = playerJson.getInt("rank"),
                                    playerName = playerJson.getString("playerName"),
                                    score = playerJson.getInt("score"),
                                    timeTaken = playerJson.optLong("timeTaken", 0),
                                    userId = playerJson.optString("userId", ""),
                                    badgeIcon = playerJson.optString("badgeIcon", "🏆")
                                )
                            )
                        }

                        val statistics = if (json.has("statistics")) {
                            val statsJson = json.getJSONObject("statistics")
                            LeaderboardStatistics(
                                totalPlayers = statsJson.optInt("totalPlayers", 0),
                                highestScore = statsJson.optInt("highestScore").takeIf { it > 0 },
                                lowestScore = statsJson.optInt("lowestScore").takeIf { it > 0 },
                                averageScore = statsJson.optDouble("averageScore", 0.0),
                                fastestTime = statsJson.optLong("fastestTime").takeIf { it > 0 },
                                slowestTime = statsJson.optLong("slowestTime").takeIf { it > 0 },
                                averageTime = statsJson.optLong("averageTime", 0)
                            )
                        } else null

                        val leaderboard = PuzzleLeaderboard(
                            puzzleId = puzzleId,
                            topPlayers = topPlayers,
                            userRank = json.optInt("userRank").takeIf { it > 0 },
                            userScore = json.optInt("userScore").takeIf { it > 0 },
                            totalPlayers = json.optInt("totalPlayers", 0),
                            puzzleName = json.optString("puzzleName", "Custom Puzzle"),        // ← Add this
                            puzzleCreator = json.optString("puzzleCreator", "Unknown"),       // ← Add this
                            isEmpty = json.optBoolean("isEmpty", false),
                            statistics = statistics
                        )

                        Handler(Looper.getMainLooper()).post {
                            onResult(leaderboard)
                        }
                    } else {
                        Log.e("PuzzleLeaderboard", "❌ API returned success=false: ${json.optString("error")}")
                        Handler(Looper.getMainLooper()).post {
                            onResult(null)
                        }
                    }
                } else {
                    Log.e("PuzzleLeaderboard", "❌ API request failed: ${response.code}")
                    Handler(Looper.getMainLooper()).post {
                        onResult(null)
                    }
                }
            } catch (e: Exception) {
                Log.e("PuzzleLeaderboard", "❌ Error parsing leaderboard response", e)
                Handler(Looper.getMainLooper()).post {
                    onResult(null)
                }
            }
        }
    })
}


fun fetchEnhancedCustomPuzzles(
    context: Context,
    userEmail: String? = null,
    offset: Int = 0,
    filter: String = "all",
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
            Log.e("CustomPuzzles", "❌ Failed to fetch custom puzzles", e)
            Handler(Looper.getMainLooper()).post {
                onResult(emptyList())
            }
        }

        override fun onResponse(call: Call, response: Response) {
            try {
                val body = response.body?.string()
                if (response.isSuccessful && body != null) {
                    val json = JSONObject(body)
                    val puzzleArray = json.getJSONArray("puzzleData")

                    val puzzles = mutableListOf<CustomPuzzle>()
                    for (i in 0 until puzzleArray.length()) {
                        val obj = puzzleArray.getJSONObject(i)

                        // Parse theme colors if available
                        val themeColors = if (obj.has("themeColors")) {
                            val themeJson = obj.getJSONObject("themeColors")
                            PuzzleTheme(
                                primaryColor = themeJson.getString("primaryColor"),
                                secondaryColor = themeJson.getString("secondaryColor"),
                                backgroundPattern = themeJson.optString("backgroundPattern"),
                                emoji = themeJson.optString("emoji")
                            )
                        } else null

                        val puzzle = CustomPuzzle(
                            id = obj.getString("id"),
                            name = obj.getString("name"),
                            numPuzzles = obj.getString("numPuzzles"),
                            format = obj.getString("format"),
                            creator = obj.getString("creator"),
                            status = obj.getString("status"),
                            topic = obj.optString("topic"),
                            themeColors = themeColors,
                            playCount = obj.optInt("playCount", 0),
                            averageRating = obj.optDouble("averageRating", 0.0).toFloat(),
                            completionCount = obj.optInt("completionCount", 0),
                            difficulty = obj.optString("difficulty"),
                            createdAt = parseIsoDateToMillis(obj.optString("createdAt", "")),
                            updatedAt = parseIsoDateToMillis(obj.optString("updatedAt", ""))
                        )
                        puzzles.add(puzzle)
                    }

                    Handler(Looper.getMainLooper()).post {
                        onResult(puzzles)
                    }
                } else {
                    Log.e("CustomPuzzles", "❌ API request failed: ${response.code}")
                    Handler(Looper.getMainLooper()).post {
                        onResult(emptyList())
                    }
                }
            } catch (e: Exception) {
                Log.e("CustomPuzzles", "❌ Error parsing custom puzzles response", e)
                Handler(Looper.getMainLooper()).post {
                    onResult(emptyList())
                }
            }
        }
    })
}

fun fetchFeaturedPuzzles(
    limit: Int = 5,
    onResult: (List<CustomPuzzle>) -> Unit
) {
    val url = "https://puzzleverseai.com/get-featured-puzzles?limit=$limit"
    val client = OkHttpClient()
    val request = Request.Builder().url(url).build()

    client.newCall(request).enqueue(object : Callback {
        override fun onFailure(call: Call, e: IOException) {
            Log.e("FeaturedPuzzles", "❌ Failed to fetch featured puzzles", e)
            Handler(Looper.getMainLooper()).post {
                onResult(emptyList())
            }
        }

        override fun onResponse(call: Call, response: Response) {
            try {
                val body = response.body?.string()
                if (response.isSuccessful && body != null) {
                    val json = JSONObject(body)

                    val success = json.optBoolean("success", json.has("featuredPuzzles"))

                    if (success) {
                        val featuredArray = json.getJSONArray("featuredPuzzles")
                        val puzzles = mutableListOf<CustomPuzzle>()

                        for (i in 0 until featuredArray.length()) {
                            val obj = featuredArray.getJSONObject(i)

                            // Parse theme colors if available
                            val themeColors = if (obj.has("themeColors")) {
                                val themeJson = obj.getJSONObject("themeColors")
                                PuzzleTheme(
                                    primaryColor = themeJson.getString("primaryColor"),
                                    secondaryColor = themeJson.getString("secondaryColor"),
                                    backgroundPattern = themeJson.optString("backgroundPattern"),
                                    emoji = themeJson.optString("emoji","🧩")
                                )
                            } else null

                            val puzzle = CustomPuzzle(
                                id = obj.getString("id"),
                                name = obj.getString("name"),
                                numPuzzles = obj.getString("numPuzzles"),
                                format = obj.getString("format"),
                                creator = obj.getString("creator"),
                                status = obj.optString("status", "completed"),
                                topic = obj.optString("topic"),
                                themeColors = themeColors,
                                playCount = obj.optInt("playCount", 0),
                                averageRating = obj.optDouble("averageRating", 0.0).toFloat(),
                                completionCount = obj.optInt("completionCount", 0),
                                difficulty = obj.optString("difficulty"),
                                createdAt = parseIsoDateToMillis(obj.optString("createdAt", "")),
                                updatedAt = parseIsoDateToMillis(obj.optString("updatedAt", ""))
                            )
                            puzzles.add(puzzle)
                        }

                        Handler(Looper.getMainLooper()).post {
                            onResult(puzzles)
                        }
                    } else {
                        Log.e("FeaturedPuzzles", "❌ API returned success=false: ${json.optString("error")}")
                        Handler(Looper.getMainLooper()).post {
                            onResult(emptyList())
                        }
                    }
                } else {
                    Log.e("FeaturedPuzzles", "❌ API request failed: ${response.code}")
                    Handler(Looper.getMainLooper()).post {
                        onResult(emptyList())
                    }
                }
            } catch (e: Exception) {
                Log.e("FeaturedPuzzles", "❌ Error parsing featured puzzles response", e)
                Handler(Looper.getMainLooper()).post {
                    onResult(emptyList())
                }
            }
        }
    })
}

// Add this new function for updating puzzle leaderboard:

fun updatePuzzleLeaderboard(
    puzzleId: String,
    userId: String,
    timeTaken: Long,
    score: Int,
    onResult: (Boolean, String?) -> Unit
) {
    val url = "https://puzzleverseai.com/update-puzzle-leaderboard"
    val client = OkHttpClient()

    val json = JSONObject().apply {
        put("puzzleId", puzzleId)
        put("userId", userId)
        put("timeTaken", timeTaken)
        put("score", score)
    }

    val requestBody = json.toString().toRequestBody("application/json".toMediaTypeOrNull())
    val request = Request.Builder()
        .url(url)
        .post(requestBody)
        .build()

    client.newCall(request).enqueue(object : Callback {
        override fun onFailure(call: Call, e: IOException) {
            Log.e("LeaderboardUpdate", "❌ Failed to update leaderboard", e)
            Handler(Looper.getMainLooper()).post {
                onResult(false, "Network error: ${e.message}")
            }
        }

        override fun onResponse(call: Call, response: Response) {
            try {
                val body = response.body?.string()
                if (response.isSuccessful && body != null) {
                    val json = JSONObject(body)

                    if (json.getBoolean("success")) {
                        Log.d("LeaderboardUpdate", "✅ Leaderboard updated successfully")
                        Handler(Looper.getMainLooper()).post {
                            onResult(true, json.optString("message"))
                        }
                    } else {
                        val errorMsg = json.optString("error", "Unknown error")
                        Log.e("LeaderboardUpdate", "❌ Update failed: $errorMsg")
                        Handler(Looper.getMainLooper()).post {
                            onResult(false, errorMsg)
                        }
                    }
                } else {
                    Log.e("LeaderboardUpdate", "❌ API request failed: ${response.code}")
                    Handler(Looper.getMainLooper()).post {
                        onResult(false, "HTTP ${response.code}")
                    }
                }
            } catch (e: Exception) {
                Log.e("LeaderboardUpdate", "❌ Error parsing update response", e)
                Handler(Looper.getMainLooper()).post {
                    onResult(false, "Parse error: ${e.message}")
                }
            }
        }
    })
}