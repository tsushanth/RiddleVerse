package com.kreativekoala.riddleverse

import com.kreativekoala.riddleverse.ui.theme.*
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import androidx.compose.ui.res.stringResource
import com.kreativekoala.riddleverse.ui.theme.RiddleVerseTheme

fun formatScore(score: Int): String {
    return when {
        score >= 1000000 -> "${(score / 1000000.0).format(1)}M"
        score >= 1000 -> "${(score / 1000.0).format(1)}K"
        else -> score.toString()
    }
}

fun formatLeaderboardTime(timeInMs: Long): String {
    if (timeInMs <= 0) return "N/A"

    val seconds = timeInMs / 1000
    val minutes = seconds / 60
    val remainingSeconds = seconds % 60

    return if (minutes > 0) {
        "${minutes}:${remainingSeconds.toString().padStart(2, '0')}"
    } else {
        "${remainingSeconds}s"
    }
}

private fun Double.format(digits: Int) = "%.${digits}f".format(this)

class PuzzleLeaderboardActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Extract data from intent
        val puzzleId = intent.getStringExtra("puzzleId") ?: ""
        val puzzleName = intent.getStringExtra("puzzleName") ?: "Custom Puzzle"
        val puzzleCreator = intent.getStringExtra("puzzleCreator") ?: "Unknown"
        val totalPlayers = intent.getIntExtra("totalPlayers", 0)
        val userRank = intent.getIntExtra("userRank", 0)
        val userScore = intent.getIntExtra("userScore", 0)
        val isEmpty = intent.getBooleanExtra("isEmpty", false)

        // Parse JSON data
        val gson = Gson()
        val topPlayersJson = intent.getStringExtra("topPlayersJson") ?: "[]"
        val statisticsJson = intent.getStringExtra("statisticsJson")

        val topPlayersType = object : TypeToken<List<LeaderboardEntry>>() {}.type
        val topPlayers: List<LeaderboardEntry> = try {
            gson.fromJson(topPlayersJson, topPlayersType) ?: emptyList()
        } catch (e: Exception) {
            Log.e("PuzzleLeaderboard", "Failed to parse top players", e)
            emptyList()
        }

        val statistics: LeaderboardStatistics? = if (statisticsJson != null) {
            try {
                gson.fromJson(statisticsJson, LeaderboardStatistics::class.java)
            } catch (e: Exception) {
                Log.e("PuzzleLeaderboard", "Failed to parse statistics", e)
                null
            }
        } else null

        setContent {
            RiddleVerseTheme {
                PuzzleLeaderboardScreen(
                    puzzleId = puzzleId,
                    puzzleName = puzzleName,
                    puzzleCreator = puzzleCreator,
                    topPlayers = topPlayers,
                    totalPlayers = totalPlayers,
                    userRank = userRank,
                    userScore = userScore,
                    statistics = statistics,
                    isEmpty = isEmpty,
                    onBackPressed = { finish() }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PuzzleLeaderboardScreen(
    puzzleId: String,
    puzzleName: String,
    puzzleCreator: String,
    topPlayers: List<LeaderboardEntry>,
    totalPlayers: Int,
    userRank: Int,
    userScore: Int,
    statistics: LeaderboardStatistics?,
    isEmpty: Boolean,
    onBackPressed: () -> Unit
) {
    var selectedTab by remember { mutableStateOf(0) }
    val tabs = listOf("Rankings", "Statistics")

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = puzzleName,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "by $puzzleCreator",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBackPressed) {
                        Icon(Icons.Default.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // User's Performance Card (if user has played)
            if (userRank > 0 && userScore > 0) {
                UserPerformanceCard(
                    rank = userRank,
                    score = userScore,
                    totalPlayers = totalPlayers,
                    modifier = Modifier.padding(16.dp)
                )
            }

            // Tab Row
            TabRow(
                selectedTabIndex = selectedTab,
                modifier = Modifier.fillMaxWidth()
            ) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = { Text(title) }
                    )
                }
            }

            // Tab Content
            when (selectedTab) {
                0 -> {
                    // Rankings Tab
                    if (isEmpty || topPlayers.isEmpty()) {
                        EmptyLeaderboardState(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(16.dp)
                        )
                    } else {
                        LeaderboardRankings(
                            topPlayers = topPlayers,
                            totalPlayers = totalPlayers,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
                1 -> {
                    // Statistics Tab
                    if (statistics != null && !isEmpty) {
                        LeaderboardStatistics(
                            statistics = statistics,
                            totalPlayers = totalPlayers,
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(16.dp)
                        )
                    } else {
                        EmptyStatisticsState(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(16.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun UserPerformanceCard(
    rank: Int,
    score: Int,
    totalPlayers: Int,
    modifier: Modifier = Modifier
) {
    val percentile = if (totalPlayers > 0) {
        ((totalPlayers - rank + 1).toDouble() / totalPlayers.toDouble()) * 100
    } else 0.0

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = stringResource(R.string.your_performance),
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                PerformanceItem(
                    label = stringResource(R.string.rank_label),
                    value = "#$rank",
                    icon = "🏆"
                )

                PerformanceItem(
                    label = stringResource(R.string.score_label),
                    value = formatScore(score),
                    icon = "⭐"
                )

                PerformanceItem(
                    label = stringResource(R.string.percentile_label),
                    value = "${percentile.toInt()}%",
                    icon = "📊"
                )
            }
        }
    }
}

@Composable
fun PerformanceItem(
    label: String,
    value: String,
    icon: String
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = icon,
            fontSize = 24.sp
        )
        Text(
            text = value,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onPrimaryContainer
        )
        Text(
            text = label,
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
        )
    }
}

@Composable
fun LeaderboardRankings(
    topPlayers: List<LeaderboardEntry>,
    totalPlayers: Int,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Header
        item {
            Text(
                text = "Top Players ($totalPlayers total)",
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }

        // Top 3 players with special styling
        itemsIndexed(topPlayers.take(3)) { index, player ->
            TopPlayerCard(
                player = player,
                rank = index + 1
            )
        }

        // Remaining players
        if (topPlayers.size > 3) {
            item {
                Text(
                    text = stringResource(R.string.other_top_players),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }

            itemsIndexed(topPlayers.drop(3)) { index, player ->
                RegularPlayerCard(
                    player = player,
                    rank = index + 4
                )
            }
        }
    }
}

@Composable
fun TopPlayerCard(
    player: LeaderboardEntry,
    rank: Int
) {
    val gradient = when (rank) {
        1 -> Brush.horizontalGradient(colors = listOf(RvSun, RvSun)) // Gold
        2 -> Brush.horizontalGradient(colors = listOf(Color(0xFFC0C0C0), RvInkSoft)) // Silver
        3 -> Brush.horizontalGradient(colors = listOf(Color(0xFFCD7F32), Color(0xFF8B4513))) // Bronze
        else -> Brush.horizontalGradient(colors = listOf(Color.Gray, Color.DarkGray))
    }

    val rankIcon = when (rank) {
        1 -> "🥇"
        2 -> "🥈"
        3 -> "🥉"
        else -> "🏆"
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(gradient)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Rank Icon
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .background(RvSurface, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = rankIcon,
                        fontSize = 24.sp
                    )
                }

                Spacer(modifier = Modifier.width(16.dp))

                // Player Info
                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = player.playerName,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = RvInk
                    )
                    if (player.timeTaken != null && player.timeTaken > 0) {
                        Text(
                            text = "Time: ${formatLeaderboardTime(player.timeTaken)}",
                            fontSize = 12.sp,
                            color = RvInkSoft.copy(alpha = 0.8f)
                        )
                    }
                }

                // Score
                Text(
                    text = formatScore(player.score),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = RvInk
                )
            }
        }
    }
}

@Composable
fun RegularPlayerCard(
    player: LeaderboardEntry,
    rank: Int
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Rank
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .background(
                        MaterialTheme.colorScheme.primaryContainer,
                        CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "#$rank",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Player Info
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = player.playerName,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                )
                if (player.timeTaken != null && player.timeTaken > 0) {
                    Text(
                        text = "Time: ${formatLeaderboardTime(player.timeTaken)}",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Score
            Text(
                text = formatScore(player.score),
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
fun LeaderboardStatistics(
    statistics: LeaderboardStatistics,
    totalPlayers: Int,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text(
                text = stringResource(R.string.puzzle_statistics),
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }

        item {
            StatisticsOverviewCard(
                statistics = statistics,
                totalPlayers = totalPlayers
            )
        }

        item {
            StatisticsDetailsCard(statistics = statistics)
        }

        item {
            RecordsCard(statistics = statistics)
        }
    }
}

@Composable
fun StatisticsOverviewCard(
    statistics: LeaderboardStatistics,
    totalPlayers: Int
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = stringResource(R.string.overview),
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                StatisticItem(
                    label = stringResource(R.string.total_players),
                    value = totalPlayers.toString(),
                    icon = "👥"
                )

                StatisticItem(
                    label = "Completions",
                    value = statistics.totalCompletions.toString(),
                    icon = "✅"
                )

                StatisticItem(
                    label = stringResource(R.string.avg_score),
                    value = "%.1f".format(statistics.averageScore),
                    icon = "⭐"
                )
            }
        }
    }
}

@Composable
fun StatisticItem(
    label: String,
    value: String,
    icon: String
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = icon,
            fontSize = 20.sp
        )
        Text(
            text = value,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = label,
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
fun StatisticsDetailsCard(statistics: LeaderboardStatistics) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = stringResource(R.string.detailed_statistics),
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            StatisticsRow(
                icon = "⏱️",
                label = "Average Time",
                value = formatLeaderboardTime(statistics.averageTime)
            )

            StatisticsRow(
                icon = "🎯",
                label = "Average Score",
                value = "%.1f points".format(statistics.averageScore)
            )

            StatisticsRow(
                icon = "📊",
                label = "Total Completions",
                value = "${statistics.totalCompletions} times"
            )
        }
    }
}

@Composable
fun RecordsCard(statistics: LeaderboardStatistics) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = stringResource(R.string.records),
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            if (statistics.fastestTime != null && statistics.fastestTime > 0) {
                StatisticsRow(
                    icon = "⚡",
                    label = stringResource(R.string.fastest_time),
                    value = formatLeaderboardTime(statistics.fastestTime)
                )
            }

            if (statistics.highestScore != null && statistics.highestScore > 0) {
                StatisticsRow(
                    icon = "🏆",
                    label = stringResource(R.string.highest_score),
                    value = formatScore(statistics.highestScore)
                )
            }

            if ((statistics.fastestTime == null || statistics.fastestTime <= 0) &&
                (statistics.highestScore == null || statistics.highestScore <= 0)) {
                Text(
                    text = "No records available yet",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }
        }
    }
}

@Composable
fun StatisticsRow(
    icon: String,
    label: String,
    value: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = icon,
            fontSize = 18.sp,
            modifier = Modifier.padding(end = 12.dp)
        )

        Text(
            text = label,
            fontSize = 14.sp,
            modifier = Modifier.weight(1f)
        )

        Text(
            text = value,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.primary
        )
    }
}

@Composable
fun EmptyLeaderboardState(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.EmojiEvents,
            contentDescription = "No leaderboard data",
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = stringResource(R.string.no_players_yet),
            fontSize = 20.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Text(
            text = "Be the first to complete this puzzle and claim the top spot!",
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 32.dp, vertical = 8.dp)
        )
    }
}

@Composable
fun EmptyStatisticsState(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.BarChart,
            contentDescription = "No statistics data",
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "No Statistics Available",
            fontSize = 20.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Text(
            text = "Statistics will appear once players start completing this puzzle.",
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 32.dp, vertical = 8.dp)
        )
    }
}