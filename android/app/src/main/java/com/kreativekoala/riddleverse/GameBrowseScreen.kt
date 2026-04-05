package com.kreativekoala.riddleverse

import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.File
import java.util.UUID
import java.util.zip.ZipInputStream
import coil.compose.AsyncImage
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale

data class BrowseGameData(
    val id: String,
    val title: String,
    val creatorId: String,
    val creatorName: String,
    val playCount: Int,
    val initialPrompt: String,
    val createdAt: String,
    val status: String = "published",
    val thumbnailUrl: String? = null
)

@Composable
fun GameBrowseContent(context: android.content.Context) {
    val TAG = "GameBrowse"
    val PAGE_SIZE = 20
    var games by remember { mutableStateOf<List<BrowseGameData>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var isLoadingMore by remember { mutableStateOf(false) }
    var sortBy by remember { mutableStateOf("newest") }
    var showMyGames by remember { mutableStateOf(false) }
    var hasMore by remember { mutableStateOf(false) }
    var currentOffset by remember { mutableIntStateOf(0) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var downloadingId by remember { mutableStateOf<String?>(null) }
    var deletingIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var selectedGame by remember { mutableStateOf<BrowseGameData?>(null) }
    var bundleDirPath by remember { mutableStateOf<String?>(null) }
    var showGamePlay by remember { mutableStateOf(false) }
    var showTweakEditor by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()
    val currentUserId = FirebaseAuth.getInstance().currentUser?.uid

    // Full screen tweak editor
    if (showTweakEditor && selectedGame != null) {
        GameTweakScreen(
            game = selectedGame!!,
            onClose = { showTweakEditor = false }
        )
        return
    }

    // Full screen game play
    if (showGamePlay && selectedGame != null && bundleDirPath != null) {
        BrowseGamePlayScreen(
            game = selectedGame!!,
            bundleDir = bundleDirPath!!,
            onClose = { showGamePlay = false }
        )
        return
    }

    fun fetchGames(loadMore: Boolean = false) {
        if (loadMore) {
            isLoadingMore = true
        } else {
            isLoading = true
            currentOffset = 0
        }
        errorMessage = null
        val offset = if (loadMore) currentOffset else 0

        coroutineScope.launch {
            withContext(Dispatchers.IO) {
                try {
                    val request = Request.Builder()
                        .url("https://puzzleverseai.com/api/game-creation/browse?sort=$sortBy&limit=$PAGE_SIZE&offset=$offset${if (showMyGames && currentUserId != null) "&creatorId=$currentUserId" else ""}")
                        .get()
                        .build()

                    val response = HttpClientProvider.client.newCall(request).execute()
                    val body = response.body?.string()
                    response.close()

                    if (body != null) {
                        val json = JSONObject(body)
                        val gamesArray = json.optJSONArray("games")
                        val serverHasMore = json.optBoolean("hasMore", false)
                        val parsed = mutableListOf<BrowseGameData>()

                        if (gamesArray != null) {
                            for (i in 0 until gamesArray.length()) {
                                val g = gamesArray.getJSONObject(i)
                                parsed.add(
                                    BrowseGameData(
                                        id = g.optString("id", ""),
                                        title = g.optString("title", "Untitled"),
                                        creatorId = g.optString("creator_id", ""),
                                        creatorName = g.optString("creator_name", "Anonymous"),
                                        playCount = g.optInt("play_count", 0),
                                        initialPrompt = g.optString("description", ""),
                                        createdAt = g.optString("created_at", ""),
                                        status = g.optString("status", "published"),
                                        thumbnailUrl = g.optString("initial_screenshot_url", "").ifEmpty { null }
                                    )
                                )
                            }
                        }

                        withContext(Dispatchers.Main) {
                            if (loadMore) {
                                games = games + parsed
                            } else {
                                games = parsed
                            }
                            hasMore = serverHasMore
                            currentOffset = offset + parsed.size
                            isLoading = false
                            isLoadingMore = false
                        }
                    } else {
                        withContext(Dispatchers.Main) {
                            errorMessage = "Empty response from server"
                            isLoading = false
                            isLoadingMore = false
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Fetch games error", e)
                    withContext(Dispatchers.Main) {
                        errorMessage = "Failed to load games"
                        isLoading = false
                        isLoadingMore = false
                    }
                }
            }
        }
    }

    fun downloadAndPlay(game: BrowseGameData) {
        if (downloadingId != null) return

        // Launch game in Chrome Custom Tab — hosted at puzzleverseai.com
        val userId = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid
        ChromeGameLauncher.launchGame(context, game.id, userId)
        return

        // Legacy local WebView path (kept for fallback if needed)
        @Suppress("UNREACHABLE_CODE")
        downloadingId = game.id
        errorMessage = null

        coroutineScope.launch {
            withContext(Dispatchers.IO) {
                try {
                    val request = Request.Builder()
                        .url("https://puzzleverseai.com/api/game-creation/${game.id}")
                        .get()
                        .build()

                    val response = HttpClientProvider.client.newCall(request).execute()
                    val responseBody = response.body?.string()
                    response.close()

                    if (responseBody == null) {
                        withContext(Dispatchers.Main) {
                            errorMessage = "Failed to load game"
                            downloadingId = null
                        }
                        return@withContext
                    }

                    val json = JSONObject(responseBody)
                    val gameData = json.optJSONObject("game")
                    val base64Bundle = gameData?.optString("bundle", null)

                    if (base64Bundle.isNullOrEmpty()) {
                        withContext(Dispatchers.Main) {
                            errorMessage = "Game bundle not available"
                            downloadingId = null
                        }
                        return@withContext
                    }

                    val zipBytes = android.util.Base64.decode(base64Bundle, android.util.Base64.DEFAULT)
                    val gameDir = File(context.cacheDir, "browse-games/${game.id}")
                    if (gameDir.exists()) gameDir.deleteRecursively()
                    gameDir.mkdirs()

                    val zipStream = ZipInputStream(ByteArrayInputStream(zipBytes))
                    var entry = zipStream.nextEntry
                    while (entry != null) {
                        val outFile = File(gameDir, entry.name)
                        if (!outFile.canonicalPath.startsWith(gameDir.canonicalPath)) {
                            zipStream.closeEntry(); entry = zipStream.nextEntry; continue
                        }
                        if (entry.isDirectory) outFile.mkdirs()
                        else { outFile.parentFile?.mkdirs(); outFile.outputStream().use { zipStream.copyTo(it) } }
                        zipStream.closeEntry(); entry = zipStream.nextEntry
                    }
                    zipStream.close()

                    val indexFile = File(gameDir, "index.html")
                    if (!indexFile.exists()) {
                        withContext(Dispatchers.Main) { errorMessage = "Game bundle missing index.html"; downloadingId = null }
                        return@withContext
                    }

                    withContext(Dispatchers.Main) {
                        selectedGame = game; bundleDirPath = gameDir.absolutePath; downloadingId = null; showGamePlay = true
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Download error", e)
                    withContext(Dispatchers.Main) { errorMessage = "Failed to load game: ${e.message}"; downloadingId = null }
                }
            }
        }
    }

    fun deleteGame(game: BrowseGameData) {
        // Optimistic removal
        games = games.filter { it.id != game.id }
        deletingIds = deletingIds + game.id
        coroutineScope.launch {
            withContext(Dispatchers.IO) {
                try {
                    val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return@withContext
                    val request = Request.Builder()
                        .url("https://puzzleverseai.com/api/game-creation/${game.id}?userId=$userId")
                        .delete()
                        .build()
                    val response = HttpClientProvider.client.newCall(request).execute()
                    val body = response.body?.string()
                    response.close()
                    Log.d(TAG, "Delete ${game.id} → ${response.code}: $body")
                    if (!response.isSuccessful) {
                        // Restore on failure
                        withContext(Dispatchers.Main) {
                            games = (games + game).sortedByDescending { it.createdAt }
                            errorMessage = "Failed to delete game"
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Delete error", e)
                    withContext(Dispatchers.Main) {
                        games = (games + game).sortedByDescending { it.createdAt }
                        errorMessage = "Failed to delete game"
                    }
                } finally {
                    withContext(Dispatchers.Main) {
                        deletingIds = deletingIds - game.id
                    }
                }
            }
        }
    }

    LaunchedEffect(sortBy, showMyGames) {
        fetchGames()
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Header with sort
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.community_games),
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            // My Games filter
            if (currentUserId != null) {
                Surface(
                    onClick = { showMyGames = !showMyGames },
                    shape = RoundedCornerShape(20.dp),
                    color = if (showMyGames) Color(0xFFFF8C00) else Color(0xFFFF8C00).copy(alpha = 0.15f)
                ) {
                    Text(
                        text = stringResource(R.string.my_games),
                        fontSize = 12.sp,
                        color = if (showMyGames) Color.White else Color(0xFFFF8C00),
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
            }

            // Sort button
            var showSortMenu by remember { mutableStateOf(false) }
            Box {
                Surface(
                    onClick = { showSortMenu = true },
                    shape = RoundedCornerShape(20.dp),
                    color = Color(0xFFFF8C00).copy(alpha = 0.15f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Sort,
                            contentDescription = stringResource(R.string.sort_by),
                            tint = Color(0xFFFF8C00),
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (sortBy == "newest") stringResource(R.string.newest) else stringResource(R.string.most_played),
                            fontSize = 12.sp,
                            color = Color(0xFFFF8C00),
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }
                DropdownMenu(
                    expanded = showSortMenu,
                    onDismissRequest = { showSortMenu = false }
                ) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.newest)) },
                        onClick = { sortBy = "newest"; showSortMenu = false },
                        leadingIcon = {
                            if (sortBy == "newest") Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.most_played)) },
                        onClick = { sortBy = "popular"; showSortMenu = false },
                        leadingIcon = {
                            if (sortBy == "popular") Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                        }
                    )
                }
            }
            } // end Row
        }

        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Color.White)
            }
        } else if (games.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.SportsEsports,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.3f),
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(stringResource(R.string.no_games_yet), fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White.copy(alpha = 0.5f))
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(stringResource(R.string.be_first_to_create), fontSize = 13.sp, color = Color.White.copy(alpha = 0.3f))
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(games, key = { it.id }) { game ->
                    val isOwner = game.creatorId == currentUserId
                    BrowseGameCard(
                        game = game,
                        isDownloading = downloadingId == game.id,
                        isDeleting = deletingIds.contains(game.id),
                        isOwner = isOwner,
                        onPlay = { downloadAndPlay(game) },
                        onEdit = if (isOwner) ({ selectedGame = game; showTweakEditor = true }) else null,
                        onDelete = if (isOwner) ({ deleteGame(game) }) else null
                    )
                }

                // Load more button
                if (hasMore) {
                    item {
                        Box(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            if (isLoadingMore) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(24.dp),
                                    color = Color.White,
                                    strokeWidth = 2.dp
                                )
                            } else {
                                OutlinedButton(
                                    onClick = { fetchGames(loadMore = true) },
                                    shape = RoundedCornerShape(20.dp),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFFF8C00)),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFF8C00).copy(alpha = 0.3f))
                                ) {
                                    Icon(Icons.Default.ExpandMore, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(stringResource(R.string.load_more), fontSize = 13.sp)
                                }
                            }
                        }
                    }
                }
            }
        }

        // Error
        if (errorMessage != null) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.Red.copy(alpha = 0.15f)),
                shape = RoundedCornerShape(8.dp)
            ) {
                Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Warning, contentDescription = null, tint = Color.Red, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(errorMessage!!, fontSize = 13.sp, color = Color.White.copy(alpha = 0.7f))
                }
            }
        }
    }
}

// Share game to Telegram (or fallback to share sheet)
private fun shareGameToTelegram(context: android.content.Context, game: BrowseGameData) {
    val gameUrl = "${BuildConfig.API_BASE_URL}/api/game-creation/${game.id}"
    val shareText = "Let's play ${game.title} on RiddleVerse! $gameUrl"
    val encodedText = Uri.encode(shareText)

    // Try Telegram deep link first
    val tgIntent = Intent(Intent.ACTION_VIEW, Uri.parse("tg://msg?text=$encodedText"))
    try {
        context.startActivity(tgIntent)
    } catch (e: Exception) {
        // Fallback: standard Android share sheet
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, shareText)
        }
        context.startActivity(Intent.createChooser(shareIntent, "Share game"))
    }
}

@Composable
private fun BrowseGameCard(
    game: BrowseGameData,
    isDownloading: Boolean,
    isDeleting: Boolean = false,
    isOwner: Boolean = false,
    onPlay: () -> Unit,
    onEdit: (() -> Unit)? = null,
    onDelete: (() -> Unit)? = null
) {
    val context = LocalContext.current
    var promptExpanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.06f)),
        shape = RoundedCornerShape(16.dp),
        border = CardDefaults.outlinedCardBorder().copy(
            brush = Brush.linearGradient(listOf(Color.White.copy(alpha = 0.1f), Color.White.copy(alpha = 0.05f)))
        )
    ) {
        Column {
            // Thumbnail preview
            if (game.thumbnailUrl != null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                        .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
                ) {
                    AsyncImage(
                        model = game.thumbnailUrl,
                        contentDescription = game.title,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                    // Play overlay
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.PlayCircle,
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.8f),
                            modifier = Modifier.size(48.dp)
                        )
                    }
                }
            }

            Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                // Game icon (shown only when no thumbnail)
                if (game.thumbnailUrl == null) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .background(Color(0xFFFF8C00).copy(alpha = 0.15f), RoundedCornerShape(10.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.SportsEsports,
                            contentDescription = null,
                            tint = Color(0xFFFF8C00),
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                }

                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = game.title,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        if (game.status == "draft") {
                            Spacer(modifier = Modifier.width(6.dp))
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = Color(0xFFFF6B6B).copy(alpha = 0.2f)
                            ) {
                                Text(
                                    text = stringResource(R.string.draft),
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color(0xFFFF6B6B),
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "by ${game.creatorName}",
                        fontSize = 12.sp,
                        color = Color.White.copy(alpha = 0.5f)
                    )
                }

                // Play count
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color.White.copy(alpha = 0.08f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.PlayArrow,
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.5f),
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "${game.playCount}",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color.White.copy(alpha = 0.5f)
                        )
                    }
                }
            }

            // Prompt preview — tap to expand
            if (game.initialPrompt.isNotEmpty()) {
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = game.initialPrompt,
                    fontSize = 12.sp,
                    color = Color.White.copy(alpha = 0.4f),
                    maxLines = if (promptExpanded) Int.MAX_VALUE else 2,
                    overflow = if (promptExpanded) TextOverflow.Clip else TextOverflow.Ellipsis,
                    modifier = Modifier.clickable { promptExpanded = !promptExpanded }
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Action buttons row
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                // Play button
                Button(
                    onClick = onPlay,
                    enabled = !isDownloading,
                    modifier = Modifier.weight(1f).height(40.dp),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFFF8C00),
                        disabledContainerColor = Color(0xFFFF8C00).copy(alpha = 0.5f)
                    )
                ) {
                    if (isDownloading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            color = Color.White,
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.loading), color = Color.White, fontSize = 14.sp)
                    } else {
                        Icon(Icons.Default.PlayCircle, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.play), color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    }
                }

                // Edit button (only for game creator)
                if (isOwner && onEdit != null) {
                    Button(
                        onClick = onEdit,
                        modifier = Modifier.height(40.dp),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF7B2FBE)),
                        contentPadding = PaddingValues(horizontal = 16.dp)
                    ) {
                        Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(stringResource(R.string.edit), color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    }
                }

                // Delete button (only for game creator)
                if (isOwner && onDelete != null) {
                    IconButton(
                        onClick = onDelete,
                        enabled = !isDeleting,
                        modifier = Modifier
                            .size(40.dp)
                            .background(Color(0xFFE53935).copy(alpha = 0.15f), RoundedCornerShape(10.dp))
                    ) {
                        if (isDeleting) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color(0xFFE53935), strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color(0xFFE53935), modifier = Modifier.size(18.dp))
                        }
                    }
                }

                // Share to Telegram button
                IconButton(
                    onClick = { shareGameToTelegram(context, game) },
                    modifier = Modifier
                        .size(40.dp)
                        .background(Color(0xFF0088CC).copy(alpha = 0.15f), RoundedCornerShape(10.dp))
                ) {
                    Icon(Icons.Default.Share, contentDescription = "Share to Telegram", tint = Color(0xFF0088CC), modifier = Modifier.size(18.dp))
                }
            }
        }
        }
    }
}

// MARK: - Game Play Screen (for browsed games)

@Composable
fun BrowseGamePlayScreen(
    game: BrowseGameData,
    bundleDir: String,
    onClose: () -> Unit
) {
    val coinManager = remember { CoinManager.shared }
    var currentScore by remember { mutableIntStateOf(0) }
    var gameOver by remember { mutableStateOf(false) }
    var showLeaderboard by remember { mutableStateOf(false) }
    var showCoinStore by remember { mutableStateOf(false) }
    var scoreSubmitted by remember { mutableStateOf(false) }
    var webViewKey by remember { mutableStateOf(UUID.randomUUID().toString()) }
    var leaderboard by remember { mutableStateOf<List<LeaderboardPlayerData>>(emptyList()) }
    var userRank by remember { mutableStateOf<Int?>(null) }
    var isLoadingLeaderboard by remember { mutableStateOf(false) }
    val playStartTime by remember { mutableStateOf(System.currentTimeMillis()) }
    val coroutineScope = rememberCoroutineScope()
    val context = androidx.compose.ui.platform.LocalContext.current

    // Difficulty escalation state
    var currentDifficultyLevel by remember { mutableIntStateOf(1) }
    var currentBundleDir by remember { mutableStateOf(bundleDir) }
    var isGeneratingHarder by remember { mutableStateOf(false) }
    var generationPhase by remember { mutableStateOf("") }
    var generationProgress by remember { mutableStateOf(0.0) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    // Customize state
    var showCustomizeDialog by remember { mutableStateOf(false) }
    var isCustomizing by remember { mutableStateOf(false) }
    var customizePhase by remember { mutableStateOf("") }
    var customizeProgress by remember { mutableStateOf(0.0) }

    // Feedback on close
    var showFeedbackDialog by remember { mutableStateOf(false) }
    var showFeedbackPrompt by remember { mutableStateOf(false) }
    var feedbackText by remember { mutableStateOf("") }
    val gameErrors = remember { mutableStateListOf<String>() }
    var isFixing by remember { mutableStateOf(false) }
    var fixPhase by remember { mutableStateOf("") }
    var fixProgress by remember { mutableStateOf(0f) }

    // Replay gate — shown when user reopens a game they've already played
    val prefs = context.getSharedPreferences("riddleverse_played", android.content.Context.MODE_PRIVATE)
    var showReplayGate by remember { mutableStateOf(false) }
    var showHowToPlay by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        coinManager.fetchBalance()
        coinManager.loadCoinProducts()
        if (prefs.getBoolean("played_${game.id}", false)) {
            showReplayGate = true
        }
    }

    // Intercept back gesture — block during active gameplay to prevent accidental exits
    BackHandler(enabled = true) {
        if (gameOver) {
            if (gameErrors.isNotEmpty() || game.playCount < 3) {
                showFeedbackDialog = true
            } else {
                onClose()
            }
        }
        // During active gameplay, consume the back gesture without doing anything
    }

    fun submitScore(score: Int) {
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return
        if (score <= 0) return
        val timeTaken = System.currentTimeMillis() - playStartTime

        coroutineScope.launch {
            withContext(Dispatchers.IO) {
                try {
                    val json = JSONObject().apply {
                        put("userId", userId)
                        put("timeTaken", timeTaken)
                        put("score", score)
                    }

                    val body = json.toString().toRequestBody("application/json".toMediaTypeOrNull())
                    val request = Request.Builder()
                        .url("https://puzzleverseai.com/api/leaderboard/puzzle/${game.id}")
                        .addHeader("Content-Type", "application/json")
                        .post(body)
                        .build()

                    val response = HttpClientProvider.client.newCall(request).execute()
                    if (response.isSuccessful) {
                        withContext(Dispatchers.Main) { scoreSubmitted = true }
                    }
                    response.close()
                } catch (e: Exception) {
                    Log.e("GamePlay", "Submit score error", e)
                }
            }
        }
    }

    fun fetchLeaderboard() {
        isLoadingLeaderboard = true
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: ""

        coroutineScope.launch {
            withContext(Dispatchers.IO) {
                try {
                    val request = Request.Builder()
                        .url("https://puzzleverseai.com/api/leaderboard/puzzle/${game.id}?userId=$userId")
                        .get()
                        .build()

                    val response = HttpClientProvider.client.newCall(request).execute()
                    val responseBody = response.body?.string()
                    response.close()

                    if (responseBody != null) {
                        val json = JSONObject(responseBody)
                        val playersArray = json.optJSONArray("topPlayers")
                        val parsed = mutableListOf<LeaderboardPlayerData>()

                        if (playersArray != null) {
                            for (i in 0 until playersArray.length()) {
                                val p = playersArray.getJSONObject(i)
                                parsed.add(
                                    LeaderboardPlayerData(
                                        rank = p.optInt("rank", i + 1),
                                        playerName = p.optString("playerName", "Unknown"),
                                        score = p.optInt("score", 0),
                                        userId = p.optString("userId", ""),
                                        isCurrentUser = p.optString("userId", "") == userId
                                    )
                                )
                            }
                        }

                        val rank = if (json.has("userRank") && !json.isNull("userRank")) json.optInt("userRank") else null

                        withContext(Dispatchers.Main) {
                            leaderboard = parsed
                            userRank = rank
                            isLoadingLeaderboard = false
                        }
                    }
                } catch (e: Exception) {
                    Log.e("GamePlay", "Fetch leaderboard error", e)
                    withContext(Dispatchers.Main) { isLoadingLeaderboard = false }
                }
            }
        }
    }

    fun handleContinue() {
        // Replay costs coins — creator gets 55% share
        val currentUserId = FirebaseAuth.getInstance().currentUser?.uid
        val creatorId = if (game.creatorId != currentUserId) game.creatorId else null
        if (!coinManager.canContinue) {
            showCoinStore = true
            return
        }
        coinManager.spendForContinue(game.id, creatorId) { success ->
            if (success) {
                gameOver = false
                showReplayGate = false
                scoreSubmitted = false
                webViewKey = UUID.randomUUID().toString()
            } else {
                showCoinStore = true
            }
        }
    }

    fun extractAndPlayBundle(base64Bundle: String, level: Int) {
        coroutineScope.launch {
            withContext(Dispatchers.IO) {
                try {
                    val zipBytes = android.util.Base64.decode(base64Bundle, android.util.Base64.DEFAULT)
                    val gameDir = File(context.cacheDir, "browse-games/${game.id}-lv$level")
                    if (gameDir.exists()) gameDir.deleteRecursively()
                    gameDir.mkdirs()

                    val zipStream = ZipInputStream(ByteArrayInputStream(zipBytes))
                    var entry = zipStream.nextEntry
                    while (entry != null) {
                        val outFile = File(gameDir, entry.name)
                        if (!outFile.canonicalPath.startsWith(gameDir.canonicalPath)) {
                            zipStream.closeEntry()
                            entry = zipStream.nextEntry
                            continue
                        }
                        if (entry.isDirectory) {
                            outFile.mkdirs()
                        } else {
                            outFile.parentFile?.mkdirs()
                            outFile.outputStream().use { out -> zipStream.copyTo(out) }
                        }
                        zipStream.closeEntry()
                        entry = zipStream.nextEntry
                    }
                    zipStream.close()

                    withContext(Dispatchers.Main) {
                        currentBundleDir = gameDir.absolutePath
                        currentDifficultyLevel = level
                        isGeneratingHarder = false
                        gameOver = false
                        scoreSubmitted = false
                        webViewKey = UUID.randomUUID().toString()
                    }
                } catch (e: Exception) {
                    Log.e("GamePlay", "Extract bundle error", e)
                    withContext(Dispatchers.Main) { isGeneratingHarder = false }
                }
            }
        }
    }

    fun spendAndPlay(bundle: String, level: Int) {
        generationPhase = "Spending ${CoinManager.difficultyCost(level)} coins..."
        val currentUserId = FirebaseAuth.getInstance().currentUser?.uid
        val creatorId = if (game.creatorId != currentUserId) game.creatorId else null
        coinManager.spendForHarderChallenge(
            gameId = game.id,
            difficultyLevel = level,
            creatorId = creatorId
        ) { success ->
            if (success) {
                extractAndPlayBundle(bundle, level)
            } else {
                isGeneratingHarder = false
                gameOver = true
                errorMessage = "Payment failed. Please try again."
            }
        }
    }

    fun generateHarderVariant(level: Int) {
        isGeneratingHarder = true
        generationPhase = "Starting generation..."
        generationProgress = 0.0

        coroutineScope.launch {
            withContext(Dispatchers.IO) {
                try {
                    val userId = FirebaseAuth.getInstance().currentUser?.uid ?: ""
                    val json = JSONObject().apply {
                        put("stream", true)
                        put("userId", userId)
                    }
                    val body = json.toString().toRequestBody("application/json".toMediaTypeOrNull())
                    val request = Request.Builder()
                        .url("https://puzzleverseai.com/api/game-creation/${game.id}/difficulty/$level/generate")
                        .addHeader("Content-Type", "application/json")
                        .addHeader("Accept", "text/event-stream")
                        .post(body)
                        .build()

                    // Use extended timeout for long-running SSE generation
                    val sseClient = HttpClientProvider.client.newBuilder()
                        .readTimeout(java.time.Duration.ofMinutes(10))
                        .build()
                    val response = sseClient.newCall(request).execute()
                    val source = response.body?.source() ?: run {
                        withContext(Dispatchers.Main) {
                            isGeneratingHarder = false
                            gameOver = true
                            errorMessage = "No response from build server"
                        }
                        return@withContext
                    }

                    var bundleResult: String? = null

                    while (!source.exhausted()) {
                        val line = source.readUtf8Line() ?: break
                        if (line.startsWith(":")) continue // Skip SSE comments (heartbeats)
                        if (!line.startsWith("data: ")) continue
                        val data = line.removePrefix("data: ").trim()
                        if (data.isEmpty() || data == "[DONE]") continue

                        try {
                            val event = JSONObject(data)
                            val type = event.optString("type", "")
                            val message = event.optString("message", "")
                            val progressPct = event.optDouble("progressPercent", -1.0)
                            val endPct = event.optDouble("progressEndPct", -1.0)

                            if (type == "result") {
                                val bundle = event.optString("bundle", "")
                                if (bundle.isNotEmpty()) bundleResult = bundle
                            } else if (type == "error") {
                                val errorMsg = event.optString("error", "Generation failed")
                                withContext(Dispatchers.Main) {
                                    isGeneratingHarder = false
                                    gameOver = true
                                    errorMessage = errorMsg
                                }
                                response.close()
                                return@withContext
                            } else if (type == "status") {
                                withContext(Dispatchers.Main) {
                                    if (message.isNotEmpty()) generationPhase = message
                                    if (progressPct >= 0) generationProgress = progressPct / 100.0
                                    else if (endPct >= 0) generationProgress = endPct / 100.0
                                }
                            }
                        } catch (_: Exception) {}
                    }

                    response.close()

                    if (bundleResult != null) {
                        // Generation succeeded — now spend coins then play
                        withContext(Dispatchers.Main) {
                            spendAndPlay(bundleResult, level)
                        }
                    } else {
                        withContext(Dispatchers.Main) {
                            isGeneratingHarder = false
                            gameOver = true
                            errorMessage = "Generation completed but no game bundle received"
                        }
                    }
                } catch (e: Exception) {
                    Log.e("GamePlay", "Generate harder error", e)
                    withContext(Dispatchers.Main) {
                        isGeneratingHarder = false
                        gameOver = true
                        errorMessage = "Failed to generate: ${e.message}"
                    }
                }
            }
        }
    }

    fun pollForVariant(level: Int) {
        coroutineScope.launch {
            withContext(Dispatchers.IO) {
                var attempts = 0
                while (attempts < 60) { // Poll for up to 10 minutes
                    kotlinx.coroutines.delay(10_000)
                    attempts++
                    try {
                        val request = Request.Builder()
                            .url("https://puzzleverseai.com/api/game-creation/${game.id}/difficulty/$level")
                            .get()
                            .build()

                        val response = HttpClientProvider.client.newCall(request).execute()
                        val responseBody = response.body?.string()
                        response.close()

                        if (responseBody != null) {
                            val json = JSONObject(responseBody)
                            val variantObj = json.optJSONObject("variant")
                            val bundle = variantObj?.optString("bundle", "") ?: ""
                            if (bundle.isNotEmpty()) {
                                withContext(Dispatchers.Main) {
                                    spendAndPlay(bundle, level)
                                }
                                return@withContext
                            }
                        }
                    } catch (_: Exception) {}
                }
                withContext(Dispatchers.Main) {
                    isGeneratingHarder = false
                    gameOver = true
                    errorMessage = "Generation timed out. Please try again."
                }
            }
        }
    }

    fun checkAndPlayDifficulty(level: Int) {
        coroutineScope.launch {
            withContext(Dispatchers.IO) {
                try {
                    val request = Request.Builder()
                        .url("https://puzzleverseai.com/api/game-creation/${game.id}/difficulty/$level")
                        .get()
                        .build()

                    val response = HttpClientProvider.client.newCall(request).execute()
                    val responseBody = response.body?.string()
                    response.close()

                    if (responseBody != null) {
                        val json = JSONObject(responseBody)
                        val generating = json.optBoolean("generating", false)
                        val exists = json.optBoolean("exists", false)
                        // Bundle is nested inside "variant" object
                        val variantObj = json.optJSONObject("variant")
                        val bundle = variantObj?.optString("bundle", "") ?: ""

                        if (exists && bundle.isNotEmpty()) {
                            // Variant ready — spend coins then play
                            withContext(Dispatchers.Main) {
                                spendAndPlay(bundle, level)
                            }
                        } else if (generating) {
                            // Another user is generating — poll
                            withContext(Dispatchers.Main) {
                                generationPhase = "Another player is generating this level..."
                                generationProgress = 0.5
                            }
                            pollForVariant(level)
                        } else if (!exists) {
                            // Need to generate — coins spent after success
                            withContext(Dispatchers.Main) {
                                generateHarderVariant(level)
                            }
                        } else {
                            withContext(Dispatchers.Main) {
                                isGeneratingHarder = false
                                gameOver = true
                                errorMessage = "Could not load harder version"
                            }
                        }
                    } else {
                        withContext(Dispatchers.Main) {
                            isGeneratingHarder = false
                            gameOver = true
                            errorMessage = "Failed to check variant availability"
                        }
                    }
                } catch (e: Exception) {
                    Log.e("GamePlay", "Check difficulty error", e)
                    withContext(Dispatchers.Main) {
                        isGeneratingHarder = false
                        gameOver = true
                        errorMessage = "Connection error: ${e.message}"
                    }
                }
            }
        }
    }

    fun handleHarderChallenge() {
        val nextLevel = currentDifficultyLevel + 1
        if (nextLevel > CoinManager.MAX_DIFFICULTY_LEVEL) return

        if (!coinManager.canAffordDifficulty(nextLevel)) {
            showCoinStore = true
            return
        }

        isGeneratingHarder = true
        generationPhase = "Checking availability..."
        generationProgress = 0.0
        errorMessage = null

        // Check variant first — coins are spent only after variant is ready
        checkAndPlayDifficulty(nextLevel)
    }

    fun handleCustomize(description: String, newTitle: String) {
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return

        if (!coinManager.canAffordCustomize()) {
            showCoinStore = true
            return
        }

        showCustomizeDialog = false
        isCustomizing = true
        customizePhase = "Preparing customization..."
        customizeProgress = 0.0
        errorMessage = null

        coroutineScope.launch {
            try {
                val url = "https://puzzleverseai.com/api/game-creation/${game.id}/customize"
                val jsonBody = JSONObject().apply {
                    put("userId", userId)
                    put("customizeDescription", description)
                    put("newTitle", newTitle.ifBlank { "My ${game.title}" })
                }

                val requestBody = jsonBody.toString().toRequestBody("application/json".toMediaTypeOrNull())
                val request = Request.Builder()
                    .url(url)
                    .addHeader("Content-Type", "application/json")
                    .addHeader("Accept", "text/event-stream")
                    .post(requestBody)
                    .build()

                withContext(Dispatchers.IO) {
                    // Use extended timeout for long-running SSE generation
                    val sseClient = HttpClientProvider.client.newBuilder()
                        .readTimeout(java.time.Duration.ofMinutes(10))
                        .build()
                    val response = sseClient.newCall(request).execute()
                    val source = response.body?.source()

                    if (source == null) {
                        withContext(Dispatchers.Main) {
                            isCustomizing = false
                            gameOver = true
                            errorMessage = "Connection failed"
                        }
                        return@withContext
                    }

                    var gotResult = false
                    var resultBundle: String? = null

                    try {
                        while (!source.exhausted()) {
                            val line = source.readUtf8Line() ?: break

                            if (line.startsWith(":")) continue // heartbeat
                            if (!line.startsWith("data: ")) continue

                            val eventData = line.removePrefix("data: ").trim()
                            if (eventData.isEmpty() || eventData == "[DONE]") continue
                            try {
                                val json = JSONObject(eventData)
                                val type = json.optString("type", "")

                                when (type) {
                                    "status" -> {
                                        val msg = json.optString("message", "")
                                        val pct = json.optDouble("progressPercent", 0.0) / 100.0
                                        withContext(Dispatchers.Main) {
                                            customizePhase = msg
                                            customizeProgress = pct
                                        }
                                    }
                                    "result" -> {
                                        gotResult = true
                                        resultBundle = json.optString("bundle", "")
                                    }
                                    "error" -> {
                                        val errorMsg = json.optString("error", "Customization failed")
                                        withContext(Dispatchers.Main) {
                                            isCustomizing = false
                                            gameOver = true
                                            errorMessage = errorMsg
                                        }
                                        return@withContext
                                    }
                                }
                            } catch (_: Exception) {}
                        }
                    } finally {
                        source.close()
                        response.close()
                    }

                    if (gotResult && !resultBundle.isNullOrEmpty()) {
                        // Spend coins after success
                        withContext(Dispatchers.Main) {
                            customizePhase = "Spending ${CoinManager.CUSTOMIZE_COST} coins..."
                        }

                        val currentUserId = FirebaseAuth.getInstance().currentUser?.uid
                        val creatorId = if (game.creatorId != currentUserId) game.creatorId else null
                        coinManager.spendForCustomize(gameId = game.id, creatorId = creatorId) { success ->
                            if (success) {
                                // Extract and play the customized game
                                coroutineScope.launch {
                                    try {
                                        val bundleBytes = android.util.Base64.decode(resultBundle, android.util.Base64.DEFAULT)
                                        val tempZip = java.io.File(context.cacheDir, "customize_${System.currentTimeMillis()}.zip")
                                        tempZip.writeBytes(bundleBytes)
                                        val extractDir = java.io.File(context.cacheDir, "customize_${System.currentTimeMillis()}")
                                        extractDir.mkdirs()
                                        val zipInputStream = java.util.zip.ZipInputStream(java.io.FileInputStream(tempZip))
                                        var entry = zipInputStream.nextEntry
                                        while (entry != null) {
                                            val file = java.io.File(extractDir, entry.name)
                                            if (entry.isDirectory) {
                                                file.mkdirs()
                                            } else {
                                                file.parentFile?.mkdirs()
                                                file.outputStream().use { out ->
                                                    zipInputStream.copyTo(out)
                                                }
                                            }
                                            zipInputStream.closeEntry()
                                            entry = zipInputStream.nextEntry
                                        }
                                        zipInputStream.close()
                                        tempZip.delete()

                                        withContext(Dispatchers.Main) {
                                            currentBundleDir = extractDir.absolutePath
                                            currentDifficultyLevel = 1
                                            gameOver = false
                                            scoreSubmitted = false
                                            isCustomizing = false
                                            webViewKey = UUID.randomUUID().toString()
                                        }
                                    } catch (e: Exception) {
                                        withContext(Dispatchers.Main) {
                                            isCustomizing = false
                                            gameOver = true
                                            errorMessage = "Failed to load customized game"
                                        }
                                    }
                                }
                            } else {
                                isCustomizing = false
                                gameOver = true
                                errorMessage = "Payment failed. Please try again."
                            }
                        }
                    } else if (!gotResult) {
                        withContext(Dispatchers.Main) {
                            isCustomizing = false
                            gameOver = true
                            errorMessage = "Customization failed — no result received"
                        }
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    isCustomizing = false
                    gameOver = true
                    errorMessage = "Customization error: ${e.localizedMessage}"
                }
            }
        }
    }

    // Leaderboard bottom sheet
    if (showLeaderboard) {
        LeaderboardBottomSheet(
            gameTitle = game.title,
            players = leaderboard,
            userRank = userRank,
            isLoading = isLoadingLeaderboard,
            onDismiss = { showLeaderboard = false }
        )
    }

    // Coin store bottom sheet
    if (showCoinStore) {
        CoinStoreBottomSheet(
            coinManager = coinManager,
            context = context,
            onDismiss = { showCoinStore = false }
        )
    }

    // Customize dialog
    if (showCustomizeDialog) {
        CustomizeGameDialog(
            gameTitle = game.title,
            onDismiss = { showCustomizeDialog = false },
            onSubmit = { description, newTitle ->
                handleCustomize(description, newTitle)
            }
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF1A1A2E))
    ) {
        // Fullscreen WebView — nothing else on screen while playing
        key(webViewKey) {
            GameWebView(
                bundleDir = currentBundleDir,
                modifier = Modifier.fillMaxSize(),
                onScoreReceived = { score -> currentScore = score },
                onGameOver = { score ->
                    currentScore = score
                    gameOver = true
                    submitScore(score)
                    prefs.edit().putBoolean("played_${game.id}", true).apply()
                },
                onGameError = { error ->
                    if (error !in gameErrors) {
                        gameErrors.add(error)
                    }
                }
            )
        }

        // Floating close button (top-left)
        IconButton(
            onClick = {
                if (gameErrors.isNotEmpty() || game.playCount < 3) {
                    showFeedbackDialog = true
                } else {
                    onClose()
                }
            },
            modifier = Modifier
                .statusBarsPadding()
                .padding(start = 8.dp, top = 4.dp)
                .align(Alignment.TopStart)
        ) {
            Icon(
                Icons.Default.Close,
                contentDescription = stringResource(R.string.close),
                tint = Color.White,
                modifier = Modifier
                    .size(28.dp)
                    .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                    .padding(4.dp)
            )
        }

        // How to Play dialog
        if (showHowToPlay) {
            AlertDialog(
                onDismissRequest = { showHowToPlay = false },
                title = { Text("How to Play", color = Color.White, fontWeight = FontWeight.Bold) },
                text = { Text(game.initialPrompt, color = Color.White.copy(alpha = 0.85f), fontSize = 14.sp) },
                confirmButton = {
                    TextButton(onClick = { showHowToPlay = false }) {
                        Text("Got it!", color = Color(0xFFFF8C00), fontWeight = FontWeight.SemiBold)
                    }
                },
                containerColor = Color(0xFF1A1A2E),
                titleContentColor = Color.White
            )
        }

        // Generation progress overlay
        if (isGeneratingHarder) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.85f)),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(40.dp)
                ) {
                    Text(
                        text = "\uD83D\uDD25",
                        fontSize = 48.sp
                    )
                    Spacer(modifier = Modifier.height(20.dp))
                    Text(
                        text = stringResource(R.string.generating_harder_challenge),
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Lv.${currentDifficultyLevel + 1}",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFFF4500)
                    )
                    Spacer(modifier = Modifier.height(24.dp))

                    // Progress bar
                    LinearProgressIndicator(
                        progress = { generationProgress.toFloat().coerceIn(0f, 1f) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp),
                        color = Color(0xFFFF4500),
                        trackColor = Color.White.copy(alpha = 0.15f)
                    )

                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = generationPhase,
                        fontSize = 13.sp,
                        color = Color.White.copy(alpha = 0.6f)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.this_may_take_2_5_minutes),
                        fontSize = 12.sp,
                        color = Color.White.copy(alpha = 0.3f)
                    )
                }
            }
        }

        // Customization progress overlay
        if (isCustomizing) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.85f)),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(40.dp)
                ) {
                    Text(text = "\u2728", fontSize = 48.sp)
                    Spacer(modifier = Modifier.height(20.dp))
                    Text(
                        text = stringResource(R.string.customizing_game),
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.height(24.dp))

                    LinearProgressIndicator(
                        progress = { customizeProgress.toFloat().coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth().height(6.dp),
                        color = Color(0xFF9C27B0),
                        trackColor = Color.White.copy(alpha = 0.15f)
                    )

                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = customizePhase,
                        fontSize = 13.sp,
                        color = Color.White.copy(alpha = 0.6f)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.this_may_take_2_5_minutes),
                        fontSize = 12.sp,
                        color = Color.White.copy(alpha = 0.3f)
                    )
                }
            }
        }

        // Game over overlay (hidden when generating harder variant or customizing)
        // Replay gate — shown when user opens a game they've already played
        if (showReplayGate) {
            Box(
                modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.85f)),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth().padding(40.dp)
                ) {
                    Text("🎮", fontSize = 48.sp)
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Play Again?", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "You've already played this game.\nSpend ${CoinManager.CONTINUE_COST} coins to play again.",
                        fontSize = 15.sp, color = Color.White.copy(alpha = 0.7f),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    Button(
                        onClick = { handleContinue() },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF3366FF))
                    ) {
                        Icon(Icons.Default.PlayCircle, contentDescription = null, tint = Color.White)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Play Again", color = Color.White, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.weight(1f))
                        Surface(shape = RoundedCornerShape(8.dp), color = Color.White.copy(alpha = 0.2f)) {
                            Row(modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Star, contentDescription = null, tint = Color.Yellow, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("${CoinManager.CONTINUE_COST}", color = Color.Yellow, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    TextButton(onClick = { onClose() }) {
                        Text("Back", color = Color.White.copy(alpha = 0.6f))
                    }
                }
            }
        }

        if (gameOver && !isGeneratingHarder && !isCustomizing) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.7f)),
                contentAlignment = Alignment.TopCenter
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .navigationBarsPadding()
                        .padding(horizontal = 40.dp, vertical = 40.dp)
                ) {
                    Text(
                        text = stringResource(R.string.game_over),
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = "$currentScore",
                        fontSize = 64.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFFF8C00)
                    )

                    Text(
                        text = stringResource(R.string.points),
                        fontSize = 18.sp,
                        color = Color.White.copy(alpha = 0.6f)
                    )

                    if (scoreSubmitted) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF4CAF50), modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(stringResource(R.string.score_submitted), fontSize = 13.sp, color = Color(0xFF4CAF50))
                        }
                    }

                    // Error message in game over overlay
                    if (errorMessage != null) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color.Red.copy(alpha = 0.15f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Warning, contentDescription = null, tint = Color.Red, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(errorMessage!!, fontSize = 12.sp, color = Color.White.copy(alpha = 0.7f))
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Coin balance display
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                        modifier = Modifier
                            .background(
                                Color.Yellow.copy(alpha = 0.15f),
                                RoundedCornerShape(20.dp)
                            )
                            .padding(horizontal = 16.dp, vertical = 6.dp)
                    ) {
                        Icon(
                            Icons.Default.Star,
                            contentDescription = null,
                            tint = Color.Yellow,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            "${CoinManager.shared.balance} ${stringResource(R.string.coins_label)}",
                            color = Color.Yellow,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    if (CoinManager.shared.balance < 20) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            stringResource(R.string.earn_coins_tip),
                            fontSize = 11.sp,
                            color = Color.White.copy(alpha = 0.4f)
                        )
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    // Play Again (costs coins — creator gets share)
                    Button(
                        onClick = { handleContinue() },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF3366FF)
                        )
                    ) {
                        Icon(Icons.Default.PlayCircle, contentDescription = null, tint = Color.White)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.play_again), color = Color.White, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.weight(1f))
                        // Coin cost badge
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .background(Color.White.copy(alpha = 0.2f), RoundedCornerShape(8.dp))
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Icon(
                                Icons.Default.Star,
                                contentDescription = null,
                                tint = Color.Yellow,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                "${CoinManager.CONTINUE_COST}",
                                color = Color.Yellow,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    // Harder Challenge button (hidden at max level)
                    if (currentDifficultyLevel < CoinManager.MAX_DIFFICULTY_LEVEL) {
                        Spacer(modifier = Modifier.height(12.dp))

                        val nextLevel = currentDifficultyLevel + 1
                        val harderCost = CoinManager.difficultyCost(nextLevel)
                        val difficultyLabel = when (nextLevel) {
                            2 -> stringResource(R.string.try_harder_version)
                            3 -> stringResource(R.string.try_hard_version)
                            4 -> stringResource(R.string.try_very_hard_version)
                            5 -> stringResource(R.string.try_expert_version)
                            else -> stringResource(R.string.try_harder_version)
                        }

                        Button(
                            onClick = { handleHarderChallenge() },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color.Transparent
                            ),
                            contentPadding = PaddingValues(0.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(
                                        Brush.horizontalGradient(
                                            listOf(Color(0xFFFF4500), Color(0xFFFF8C00))
                                        ),
                                        RoundedCornerShape(12.dp)
                                    )
                                    .padding(horizontal = 16.dp, vertical = 12.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "\uD83D\uDD25",
                                        fontSize = 18.sp
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = difficultyLabel,
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.sp
                                    )
                                    Spacer(modifier = Modifier.weight(1f))
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = Color.Black.copy(alpha = 0.3f)
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                Icons.Default.Circle,
                                                contentDescription = null,
                                                tint = Color(0xFFFFD700),
                                                modifier = Modifier.size(8.dp)
                                            )
                                            Spacer(modifier = Modifier.width(3.dp))
                                            Text(
                                                text = "$harderCost",
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = if (coinManager.canAffordDifficulty(nextLevel)) Color(0xFFFFD700) else Color.Red
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Customize button
                    Spacer(modifier = Modifier.height(12.dp))

                    Button(
                        onClick = {
                            if (!coinManager.canAffordCustomize()) {
                                showCoinStore = true
                            } else {
                                showCustomizeDialog = true
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.Transparent
                        ),
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    Brush.horizontalGradient(
                                        listOf(Color(0xFF9C27B0), Color(0xFF6A1B9A))
                                    ),
                                    RoundedCornerShape(12.dp)
                                )
                                .padding(horizontal = 16.dp, vertical = 12.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(text = "\u2728", fontSize = 18.sp)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = stringResource(R.string.customize),
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp
                                )
                                Spacer(modifier = Modifier.weight(1f))
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = Color.Black.copy(alpha = 0.3f)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            Icons.Default.Circle,
                                            contentDescription = null,
                                            tint = Color(0xFFFFD700),
                                            modifier = Modifier.size(8.dp)
                                        )
                                        Spacer(modifier = Modifier.width(3.dp))
                                        Text(
                                            text = "${CoinManager.CUSTOMIZE_COST}",
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (coinManager.canAffordCustomize()) Color(0xFFFFD700) else Color.Red
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Get coins button if can't afford harder version or customize
                    val harderCostNeeded = if (currentDifficultyLevel < CoinManager.MAX_DIFFICULTY_LEVEL) CoinManager.difficultyCost(currentDifficultyLevel + 1) else Int.MAX_VALUE
                    if (coinManager.balance < harderCostNeeded || !coinManager.canAffordCustomize()) {
                        OutlinedButton(
                            onClick = { showCoinStore = true },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFFFD700)),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFFD700).copy(alpha = 0.3f))
                        ) {
                            Icon(Icons.Default.AddCircle, contentDescription = null, tint = Color(0xFFFFD700))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(stringResource(R.string.get_coins), fontWeight = FontWeight.Bold, color = Color(0xFFFFD700))
                        }

                        Spacer(modifier = Modifier.height(12.dp))
                    }

                    Button(
                        onClick = {
                            showLeaderboard = true
                            fetchLeaderboard()
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF8C00))
                    ) {
                        Icon(Icons.Default.EmojiEvents, contentDescription = null, tint = Color.White)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.view_leaderboard), color = Color.White, fontWeight = FontWeight.Bold)
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedButton(
                        onClick = {
                            if (gameErrors.isNotEmpty() || game.playCount < 3) {
                                showFeedbackDialog = true
                            } else {
                                onClose()
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
                    ) {
                        Text(stringResource(R.string.done), fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // Fix progress overlay
        if (isFixing) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.85f)),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    CircularProgressIndicator(color = Color.White)
                    Text("Fixing game...", color = Color.White, fontWeight = FontWeight.Bold)
                    if (fixPhase.isNotEmpty()) {
                        Text(fixPhase, color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp)
                    }
                    if (fixProgress > 0f) {
                        LinearProgressIndicator(
                            progress = { fixProgress / 100f },
                            modifier = Modifier.width(200.dp),
                            color = Color(0xFF6B5CE7)
                        )
                    }
                }
            }
        }
    }

    // Feedback dialog — "Did this game work?"
    if (showFeedbackDialog) {
        AlertDialog(
            onDismissRequest = { showFeedbackDialog = false },
            title = { Text(stringResource(R.string.did_this_game_work)) },
            confirmButton = {
                TextButton(onClick = {
                    showFeedbackDialog = false
                    onClose()
                }) {
                    Text(stringResource(R.string.yes_it_works))
                }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = { showFeedbackDialog = false }) {
                        Text(stringResource(R.string.cancel))
                    }
                    TextButton(onClick = {
                        showFeedbackDialog = false
                        showFeedbackPrompt = true
                    }) {
                        Text(stringResource(R.string.no_it_has_issues))
                    }
                }
            }
        )
    }

    // Feedback text prompt — "What's wrong?"
    if (showFeedbackPrompt) {
        AlertDialog(
            onDismissRequest = { showFeedbackPrompt = false },
            title = { Text(stringResource(R.string.whats_wrong)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Brief description — even a couple words helps", fontSize = 13.sp, color = Color.Gray)
                    OutlinedTextField(
                        value = feedbackText,
                        onValueChange = { feedbackText = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("e.g. buttons don't work, blank screen") },
                        singleLine = false,
                        maxLines = 3
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    showFeedbackPrompt = false
                    var description = feedbackText.trim()
                    if (gameErrors.isNotEmpty()) {
                        description += "\nDetected errors: " + gameErrors.joinToString("; ")
                    }
                    if (description.isBlank()) {
                        description = "The game doesn't work properly. Please fix all issues."
                    }
                    coroutineScope.launch {
                        fixGame(
                            gameId = game.id,
                            description = description,
                            currentBundleDir = currentBundleDir,
                            onProgress = { phase, pct ->
                                fixPhase = phase
                                fixProgress = pct
                            },
                            onStart = { isFixing = true },
                            onSuccess = { dir ->
                                currentBundleDir = dir
                                gameErrors.clear()
                                feedbackText = ""
                                gameOver = false
                                scoreSubmitted = false
                                currentScore = 0
                                isFixing = false
                                webViewKey = UUID.randomUUID().toString()
                            },
                            onError = {
                                isFixing = false
                                onClose()
                            }
                        )
                    }
                }) {
                    Text(stringResource(R.string.fix_it))
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showFeedbackPrompt = false
                    onClose()
                }) {
                    Text(stringResource(R.string.just_close))
                }
            }
        )
    }
}

// MARK: - Fix Game Flow (uses /:gameId/customize)

private suspend fun fixGame(
    gameId: String,
    description: String,
    currentBundleDir: String,
    onProgress: (String, Float) -> Unit,
    onStart: () -> Unit,
    onSuccess: (String) -> Unit,
    onError: (String) -> Unit
) {
    withContext(Dispatchers.Main) { onStart() }

    withContext(Dispatchers.IO) {
        try {
            val userId = FirebaseAuth.getInstance().currentUser?.uid ?: "anonymous"

            val json = JSONObject().apply {
                put("userId", userId)
                put("customizeDescription", description.trim())
            }

            val body = json.toString().toRequestBody("application/json".toMediaTypeOrNull())
            val request = Request.Builder()
                .url("https://puzzleverseai.com/api/game-creation/$gameId/customize")
                .addHeader("Content-Type", "application/json")
                .addHeader("Accept", "text/event-stream")
                .post(body)
                .build()

            val client = HttpClientProvider.client.newBuilder()
                .protocols(listOf(okhttp3.Protocol.HTTP_1_1))
                .readTimeout(java.time.Duration.ofMinutes(10))
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                response.close()
                withContext(Dispatchers.Main) { onError("Server error (${response.code})") }
                return@withContext
            }

            val source = response.body?.source() ?: run {
                withContext(Dispatchers.Main) { onError("Empty response") }
                return@withContext
            }

            var newBundle: String? = null
            var sseBuffer = ""

            while (!source.exhausted()) {
                val line = source.readUtf8Line() ?: break
                sseBuffer += line + "\n"

                while (sseBuffer.contains("\n\n")) {
                    val idx = sseBuffer.indexOf("\n\n")
                    val event = sseBuffer.substring(0, idx)
                    sseBuffer = sseBuffer.substring(idx + 2)

                    for (eventLine in event.split("\n")) {
                        if (!eventLine.startsWith("data: ")) continue
                        val jsonStr = eventLine.substring(6)

                        try {
                            val eventJson = JSONObject(jsonStr)
                            when (eventJson.optString("type")) {
                                "status" -> {
                                    val message = eventJson.optString("message", "")
                                    val pct = eventJson.optDouble("progressPercent", 0.0).toFloat()
                                    withContext(Dispatchers.Main) { onProgress(message, pct) }
                                }
                                "result" -> {
                                    newBundle = eventJson.optString("bundle", null)
                                }
                                "error" -> {
                                    response.close()
                                    withContext(Dispatchers.Main) { onError("Fix failed") }
                                    return@withContext
                                }
                            }
                        } catch (_: Exception) {}
                    }
                }
            }

            response.close()

            val bundle = newBundle
            if (bundle == null) {
                withContext(Dispatchers.Main) { onError("No bundle received") }
                return@withContext
            }

            // Extract new bundle
            val zipData = android.util.Base64.decode(bundle, android.util.Base64.DEFAULT)
            val gamesDir = File(currentBundleDir).parentFile ?: File(currentBundleDir)
            val newDir = File(gamesDir, UUID.randomUUID().toString())
            newDir.mkdirs()

            val zipStream = ZipInputStream(ByteArrayInputStream(zipData))
            var entry = zipStream.nextEntry
            while (entry != null) {
                val outFile = File(newDir, entry.name)
                if (!outFile.canonicalPath.startsWith(newDir.canonicalPath)) {
                    zipStream.closeEntry()
                    entry = zipStream.nextEntry
                    continue
                }
                if (entry.isDirectory) {
                    outFile.mkdirs()
                } else {
                    outFile.parentFile?.mkdirs()
                    outFile.outputStream().use { out -> zipStream.copyTo(out) }
                }
                zipStream.closeEntry()
                entry = zipStream.nextEntry
            }
            zipStream.close()

            withContext(Dispatchers.Main) { onSuccess(newDir.absolutePath) }
        } catch (e: Exception) {
            Log.e("GamePlay", "Fix game error", e)
            withContext(Dispatchers.Main) { onError(e.localizedMessage ?: "Unknown error") }
        }
    }
}

// MARK: - Coin Store Bottom Sheet

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CoinStoreBottomSheet(
    coinManager: CoinManager,
    context: android.content.Context,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color(0xFF1A1A2E),
        contentColor = Color.White
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Coin Store",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            // Current balance
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(bottom = 20.dp)
            ) {
                Icon(
                    Icons.Default.Circle,
                    contentDescription = null,
                    tint = Color(0xFFFFD700),
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "${coinManager.balance}",
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "coins",
                    fontSize = 14.sp,
                    color = Color.White.copy(alpha = 0.5f)
                )
            }

            // Coin packs
            CoinManager.COIN_PACKS.forEach { pack ->
                val price = coinManager.getProductPrice(pack.productId) ?: "..."

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.06f)),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Circle,
                            contentDescription = null,
                            tint = Color(0xFFFFD700),
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = pack.label,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            if (pack.coins == 1200) {
                                Text(
                                    text = "Best Value",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF4CAF50)
                                )
                            }
                        }
                        Button(
                            onClick = {
                                val activity = context as? android.app.Activity
                                if (activity != null) {
                                    coinManager.launchPurchase(activity, pack)
                                }
                            },
                            enabled = !coinManager.isPurchasing,
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF8C00)),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                        ) {
                            Text(
                                text = price,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                    }
                }
            }

            // Purchase feedback
            if (coinManager.purchaseMessage != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF4CAF50), modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(coinManager.purchaseMessage!!, fontSize = 13.sp, color = Color(0xFF4CAF50))
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Coins are used to continue games and support creators.",
                fontSize = 12.sp,
                color = Color.White.copy(alpha = 0.3f)
            )
        }
    }
}

// MARK: - Leaderboard Bottom Sheet

data class LeaderboardPlayerData(
    val rank: Int,
    val playerName: String,
    val score: Int,
    val userId: String,
    val isCurrentUser: Boolean
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LeaderboardBottomSheet(
    gameTitle: String,
    players: List<LeaderboardPlayerData>,
    userRank: Int?,
    isLoading: Boolean,
    onDismiss: () -> Unit
) {
    val screenHeight = LocalConfiguration.current.screenHeightDp.dp
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color(0xFF1A1A2E),
        contentColor = Color.White
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 32.dp)
        ) {
            Text(
                text = gameTitle,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            if (isLoading) {
                Box(
                    modifier = Modifier.fillMaxWidth().height(screenHeight * 0.22f),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = Color.White)
                }
            } else if (players.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxWidth().height(screenHeight * 0.22f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.EmojiEvents,
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.3f),
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(stringResource(R.string.no_scores_yet), fontSize = 16.sp, color = Color.White.copy(alpha = 0.5f))
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(stringResource(R.string.play_to_set_score), fontSize = 13.sp, color = Color.White.copy(alpha = 0.3f))
                    }
                }
            } else {
                // User rank card
                if (userRank != null) {
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFFF8C00).copy(alpha = 0.12f)),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(Icons.Default.Person, contentDescription = null, tint = Color(0xFFFF8C00), modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Your rank: #$userRank", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = Color(0xFFFF8C00))
                        }
                    }
                }

                // Player list
                players.forEach { player ->
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (player.isCurrentUser) Color(0xFFFF8C00).copy(alpha = 0.15f) else Color.White.copy(alpha = 0.06f)
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = rankEmoji(player.rank),
                                fontSize = if (player.rank <= 3) 24.sp else 14.sp,
                                modifier = Modifier.width(40.dp)
                            )

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = player.playerName,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = if (player.isCurrentUser) Color(0xFFFF8C00) else Color.White,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            Text(
                                text = "${player.score}",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFFF8C00)
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun rankEmoji(rank: Int): String {
    return when (rank) {
        1 -> "\uD83E\uDD47"
        2 -> "\uD83E\uDD48"
        3 -> "\uD83E\uDD49"
        else -> "#$rank"
    }
}

// ============================================
// Game Tweak Editor Screen
// ============================================

data class GameVersionData(
    val sha: String,
    val shortSha: String,
    val message: String,
    val date: String,
    val author: String
)

@Composable
fun GameTweakScreen(
    game: BrowseGameData,
    onClose: () -> Unit
) {
    val TAG = "GameTweak"
    val coinManager = remember { CoinManager.shared }
    var versions by remember { mutableStateOf<List<GameVersionData>>(emptyList()) }
    var isLoadingVersions by remember { mutableStateOf(true) }
    var tweakText by remember { mutableStateOf("") }
    var isTweaking by remember { mutableStateOf(false) }
    var tweakPhase by remember { mutableStateOf("") }
    var tweakProgress by remember { mutableStateOf(0f) }
    var freeTweaksRemaining by remember { mutableIntStateOf(5) }
    var tweakCost by remember { mutableIntStateOf(10) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var successMessage by remember { mutableStateOf<String?>(null) }
    var showCoinStore by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()
    val context = androidx.compose.ui.platform.LocalContext.current

    fun fetchVersions() {
        isLoadingVersions = true
        errorMessage = null

        coroutineScope.launch {
            withContext(Dispatchers.IO) {
                try {
                    Log.d(TAG, "Fetching versions for game ${game.id}")
                    val request = Request.Builder()
                        .url("https://puzzleverseai.com/api/game-creation/${game.id}/versions")
                        .get()
                        .build()

                    val response = HttpClientProvider.client.newCall(request).execute()
                    val body = response.body?.string()
                    response.close()

                    if (body != null) {
                        val json = JSONObject(body)
                        val remaining = json.optInt("freeTweaksRemaining", 5)
                        val cost = json.optInt("tweakCost", 10)
                        val versionsArray = json.optJSONArray("versions")
                        val parsed = mutableListOf<GameVersionData>()

                        if (versionsArray != null) {
                            for (i in 0 until versionsArray.length()) {
                                val v = versionsArray.getJSONObject(i)
                                parsed.add(
                                    GameVersionData(
                                        sha = v.optString("sha", ""),
                                        shortSha = v.optString("shortSha", v.optString("sha", "").take(7)),
                                        message = v.optString("message", ""),
                                        date = v.optString("date", ""),
                                        author = v.optString("author", "")
                                    )
                                )
                            }
                        }

                        Log.d(TAG, "Loaded ${parsed.size} versions, $remaining free tweaks")

                        withContext(Dispatchers.Main) {
                            versions = parsed
                            freeTweaksRemaining = remaining
                            tweakCost = cost
                            isLoadingVersions = false
                        }
                    } else {
                        withContext(Dispatchers.Main) {
                            errorMessage = "Empty response"
                            isLoadingVersions = false
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Fetch versions error", e)
                    withContext(Dispatchers.Main) {
                        errorMessage = "Failed to load version history"
                        isLoadingVersions = false
                    }
                }
            }
        }
    }

    fun submitTweak() {
        val description = tweakText.trim()
        if (description.isEmpty()) return
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return

        if (freeTweaksRemaining <= 0 && coinManager.balance < tweakCost) {
            showCoinStore = true
            return
        }

        Log.d(TAG, "Submitting tweak: \"$description\"")

        isTweaking = true
        tweakPhase = "Preparing..."
        tweakProgress = 0f
        errorMessage = null
        successMessage = null

        coroutineScope.launch {
            withContext(Dispatchers.IO) {
                try {
                    val json = JSONObject().apply {
                        put("userId", userId)
                        put("tweakDescription", description)
                    }

                    val body = json.toString().toRequestBody("application/json".toMediaTypeOrNull())
                    val request = Request.Builder()
                        .url("https://puzzleverseai.com/api/game-creation/${game.id}/tweak")
                        .addHeader("Content-Type", "application/json")
                        .addHeader("Accept", "text/event-stream")
                        .post(body)
                        .build()

                    val sseClient = HttpClientProvider.client.newBuilder()
                        .readTimeout(java.time.Duration.ofMinutes(10))
                        .build()
                    val response = sseClient.newCall(request).execute()
                    val source = response.body?.source() ?: run {
                        withContext(Dispatchers.Main) {
                            isTweaking = false
                            errorMessage = "Connection failed"
                        }
                        return@withContext
                    }

                    var gotResult = false

                    while (!source.exhausted()) {
                        val line = source.readUtf8Line() ?: break
                        if (!line.startsWith("data: ")) continue
                        val data = line.removePrefix("data: ").trim()
                        if (data.isEmpty()) continue

                        try {
                            val event = JSONObject(data)
                            val type = event.optString("type", "")

                            when (type) {
                                "status" -> {
                                    val message = event.optString("message", "")
                                    val pct = event.optDouble("progressPercent", 0.0).toFloat()
                                    withContext(Dispatchers.Main) {
                                        tweakPhase = message
                                        tweakProgress = pct / 100f
                                    }
                                }
                                "result" -> {
                                    gotResult = true
                                    val remaining = event.optInt("freeTweaksRemaining", freeTweaksRemaining)
                                    val coinsSpent = event.optInt("coinsSpent", 0)
                                    Log.d(TAG, "Tweak applied (coins=$coinsSpent)")
                                    withContext(Dispatchers.Main) {
                                        isTweaking = false
                                        tweakText = ""
                                        freeTweaksRemaining = remaining
                                        if (coinsSpent > 0) coinManager.fetchBalance()
                                        successMessage = "Tweak applied! Your game has been updated."
                                        fetchVersions()
                                    }
                                    break
                                }
                                "error" -> {
                                    val errorMsg = event.optString("error", "Unknown error")
                                    Log.e(TAG, "Tweak error: $errorMsg")
                                    withContext(Dispatchers.Main) {
                                        isTweaking = false
                                        errorMessage = errorMsg
                                    }
                                    break
                                }
                            }
                        } catch (_: Exception) {}
                    }

                    response.close()

                    if (!gotResult) {
                        withContext(Dispatchers.Main) {
                            isTweaking = false
                            if (errorMessage == null) errorMessage = "Tweak failed - no result received"
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Tweak error", e)
                    withContext(Dispatchers.Main) {
                        isTweaking = false
                        errorMessage = "Failed: ${e.message}"
                    }
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        coinManager.fetchBalance()
        fetchVersions()
    }

    // Coin store
    if (showCoinStore) {
        CoinStoreBottomSheet(
            coinManager = coinManager,
            context = context,
            onDismiss = { showCoinStore = false }
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF1A1A2E))
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Top bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onClose) {
                    Icon(Icons.Default.Close, contentDescription = stringResource(R.string.close), tint = Color.White)
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Edit: ${game.title}",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (freeTweaksRemaining > 0) {
                        Text(
                            text = "$freeTweaksRemaining free edit${if (freeTweaksRemaining == 1) "" else "s"} left",
                            fontSize = 11.sp,
                            color = Color(0xFF4CAF50)
                        )
                    } else {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Circle, contentDescription = null, tint = Color(0xFFFFD700), modifier = Modifier.size(8.dp))
                            Spacer(modifier = Modifier.width(3.dp))
                            Text("$tweakCost coins per edit", fontSize = 11.sp, color = Color(0xFFFFD700))
                        }
                    }
                }

                // Coin balance
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFFFFD700).copy(alpha = 0.15f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Circle, contentDescription = null, tint = Color(0xFFFFD700), modifier = Modifier.size(10.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("${coinManager.balance}", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFFFFD700))
                    }
                }
            }

            HorizontalDivider(color = Color.White.copy(alpha = 0.1f))

            // Version history
            if (isLoadingVersions) {
                Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = Color.White)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Loading version history...", fontSize = 13.sp, color = Color.White.copy(alpha = 0.4f))
                    }
                }
            } else if (versions.isEmpty()) {
                Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(40.dp)) {
                        Icon(Icons.Default.Search, contentDescription = null, tint = Color.White.copy(alpha = 0.3f), modifier = Modifier.size(40.dp))
                        Spacer(modifier = Modifier.height(12.dp))
                        Text("No version history yet", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White.copy(alpha = 0.5f))
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("Describe your change below to start editing", fontSize = 13.sp, color = Color.White.copy(alpha = 0.3f))
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(versions, key = { it.sha }) { version ->
                        VersionCard(version = version)
                    }
                }
            }

            // Messages
            if (errorMessage != null) {
                Card(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.Red.copy(alpha = 0.15f)),
                    shape = RoundedCornerShape(8.dp),
                    onClick = { errorMessage = null }
                ) {
                    Row(modifier = Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Warning, contentDescription = null, tint = Color.Red, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(errorMessage!!, fontSize = 13.sp, color = Color.White.copy(alpha = 0.8f))
                    }
                }
            }

            if (successMessage != null) {
                Card(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF4CAF50).copy(alpha = 0.15f)),
                    shape = RoundedCornerShape(8.dp),
                    onClick = { successMessage = null }
                ) {
                    Row(modifier = Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF4CAF50), modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(successMessage!!, fontSize = 13.sp, color = Color.White.copy(alpha = 0.8f))
                    }
                }
            }

            // Tweak input bar
            HorizontalDivider(color = Color.White.copy(alpha = 0.1f))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF141422))
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = tweakText,
                    onValueChange = { tweakText = it },
                    placeholder = { Text("Describe your change...", color = Color.White.copy(alpha = 0.3f)) },
                    modifier = Modifier.weight(1f),
                    enabled = !isTweaking,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color.Transparent,
                        unfocusedBorderColor = Color.Transparent,
                        focusedContainerColor = Color.White.copy(alpha = 0.08f),
                        unfocusedContainerColor = Color.White.copy(alpha = 0.08f),
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        cursorColor = Color(0xFFFF8C00)
                    ),
                    shape = RoundedCornerShape(20.dp),
                    singleLine = true,
                    textStyle = androidx.compose.ui.text.TextStyle(fontSize = 14.sp)
                )

                Spacer(modifier = Modifier.width(12.dp))

                val canSend = tweakText.trim().isNotEmpty() && !isTweaking
                IconButton(
                    onClick = { submitTweak() },
                    enabled = canSend,
                    modifier = Modifier
                        .size(44.dp)
                        .background(
                            if (canSend) Color(0xFFFF8C00).copy(alpha = 0.2f) else Color.White.copy(alpha = 0.05f),
                            CircleShape
                        )
                ) {
                    Icon(
                        Icons.Default.Send,
                        contentDescription = "Send",
                        tint = if (canSend) Color(0xFFFF8C00) else Color.White.copy(alpha = 0.2f)
                    )
                }
            }
        }

        // Generation overlay
        if (isTweaking) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.85f)),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(40.dp)
                ) {
                    Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = Color(0xFFFF8C00), modifier = Modifier.size(48.dp))
                    Spacer(modifier = Modifier.height(20.dp))
                    Text("Applying Edit", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    Spacer(modifier = Modifier.height(24.dp))
                    LinearProgressIndicator(
                        progress = { tweakProgress.coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth().height(6.dp),
                        color = Color(0xFFFF8C00),
                        trackColor = Color.White.copy(alpha = 0.15f)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(tweakPhase, fontSize = 13.sp, color = Color.White.copy(alpha = 0.6f))
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("This may take a minute...", fontSize = 12.sp, color = Color.White.copy(alpha = 0.3f))
                }
            }
        }
    }
}

@Composable
private fun VersionCard(version: GameVersionData) {
    val isInitial = version.message == "Initial game creation"
    val displayMsg = if (version.message.startsWith("Tweak: ")) {
        version.message.removePrefix("Tweak: ")
    } else {
        version.message
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.04f)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.Top
        ) {
            // Version indicator dot
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.width(16.dp).padding(top = 4.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .background(
                            if (isInitial) Color(0xFF4CAF50) else Color(0xFFFF8C00),
                            CircleShape
                        )
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = displayMsg,
                    fontSize = 14.sp,
                    color = Color.White,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row {
                    Text(
                        text = version.shortSha,
                        fontSize = 11.sp,
                        color = Color.White.copy(alpha = 0.3f),
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                    )
                }
            }

            if (isInitial) {
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = Color(0xFF4CAF50).copy(alpha = 0.15f)
                ) {
                    Text(
                        text = "Original",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFF4CAF50),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }
        }
    }
}

// MARK: - Customize Game Dialog

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CustomizeGameDialog(
    gameTitle: String,
    onDismiss: () -> Unit,
    onSubmit: (description: String, newTitle: String) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var description by remember { mutableStateOf("") }
    var newTitle by remember { mutableStateOf("My $gameTitle") }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color(0xFF1A1A2E),
        contentColor = Color.White
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp)
        ) {
            Text(
                text = "Customize This Game",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "Describe what you'd like to change. A new game will be created under your name.",
                fontSize = 13.sp,
                color = Color.White.copy(alpha = 0.5f)
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Game name field
            Text("Game Name", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = Color.White.copy(alpha = 0.7f))
            Spacer(modifier = Modifier.height(6.dp))
            OutlinedTextField(
                value = newTitle,
                onValueChange = { newTitle = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color(0xFF9C27B0),
                    unfocusedBorderColor = Color.White.copy(alpha = 0.2f),
                    focusedContainerColor = Color.White.copy(alpha = 0.05f),
                    unfocusedContainerColor = Color.White.copy(alpha = 0.05f),
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    cursorColor = Color(0xFF9C27B0)
                ),
                shape = RoundedCornerShape(10.dp)
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Description field
            Text("What would you like to change?", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = Color.White.copy(alpha = 0.7f))
            Spacer(modifier = Modifier.height(6.dp))
            OutlinedTextField(
                value = description,
                onValueChange = { if (it.length <= 500) description = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp),
                placeholder = { Text("e.g., Make it space-themed with asteroids instead of blocks...", color = Color.White.copy(alpha = 0.3f)) },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color(0xFF9C27B0),
                    unfocusedBorderColor = Color.White.copy(alpha = 0.2f),
                    focusedContainerColor = Color.White.copy(alpha = 0.05f),
                    unfocusedContainerColor = Color.White.copy(alpha = 0.05f),
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    cursorColor = Color(0xFF9C27B0)
                ),
                shape = RoundedCornerShape(10.dp)
            )

            Text(
                text = "${description.length}/500",
                fontSize = 11.sp,
                color = Color.White.copy(alpha = 0.3f),
                modifier = Modifier
                    .align(Alignment.End)
                    .padding(top = 4.dp)
            )

            Spacer(modifier = Modifier.height(20.dp))

            // Submit button
            Button(
                onClick = { onSubmit(description, newTitle) },
                modifier = Modifier.fillMaxWidth(),
                enabled = description.trim().isNotEmpty(),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.Transparent,
                    disabledContainerColor = Color.White.copy(alpha = 0.1f)
                ),
                contentPadding = PaddingValues(0.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            if (description.trim().isNotEmpty())
                                Brush.horizontalGradient(listOf(Color(0xFF9C27B0), Color(0xFF6A1B9A)))
                            else
                                Brush.horizontalGradient(listOf(Color.Gray.copy(alpha = 0.3f), Color.Gray.copy(alpha = 0.3f))),
                            RoundedCornerShape(12.dp)
                        )
                        .padding(vertical = 14.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(text = "\u2728", fontSize = 16.sp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Create Customized Game", fontWeight = FontWeight.Bold, color = Color.White)
                        Spacer(modifier = Modifier.width(8.dp))
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color.Black.copy(alpha = 0.3f)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.Circle,
                                    contentDescription = null,
                                    tint = Color(0xFFFFD700),
                                    modifier = Modifier.size(8.dp)
                                )
                                Spacer(modifier = Modifier.width(3.dp))
                                Text("${CoinManager.CUSTOMIZE_COST}", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFFFFD700))
                            }
                        }
                    }
                }
            }
        }
    }
}
