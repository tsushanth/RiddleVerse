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
import com.kreativekoala.riddleverse.ui.theme.*
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
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale

/**
 * Local cache of the last successfully-fetched games list, keyed by sort mode. Falls back
 * to this on fetch failure so a backend/network blip shows stale-but-real games instead of
 * an empty "no games yet" state (which reads as "there are no community games" rather than
 * "we couldn't reach the server right now").
 */
private object GamesCache {
    private const val PREFS = "riddleverse_games_cache"
    private const val KEY_PREFIX = "games_"

    fun save(context: android.content.Context, sortKey: String, games: List<BrowseGameData>) {
        if (games.isEmpty()) return
        val prefs = context.getSharedPreferences(PREFS, android.content.Context.MODE_PRIVATE)
        val arr = org.json.JSONArray()
        games.forEach { g ->
            arr.put(org.json.JSONObject().apply {
                put("id", g.id)
                put("title", g.title)
                put("creator_id", g.creatorId)
                put("creator_name", g.creatorName)
                put("play_count", g.playCount)
                put("description", g.initialPrompt)
                put("created_at", g.createdAt)
                put("status", g.status)
                put("initial_screenshot_url", g.thumbnailUrl ?: "")
                put("series_id", g.seriesId ?: "")
                put("level_index", g.levelIndex)
                put("level_count", g.levelCount)
                put("trending_score", g.trendingScore)
            })
        }
        prefs.edit().putString(KEY_PREFIX + sortKey, arr.toString()).apply()
    }

    fun load(context: android.content.Context, sortKey: String): List<BrowseGameData>? {
        val prefs = context.getSharedPreferences(PREFS, android.content.Context.MODE_PRIVATE)
        val json = prefs.getString(KEY_PREFIX + sortKey, null) ?: return null
        return try {
            val arr = org.json.JSONArray(json)
            (0 until arr.length()).map { i ->
                val g = arr.getJSONObject(i)
                BrowseGameData(
                    id = g.optString("id", ""),
                    title = g.optString("title", "Untitled"),
                    creatorId = g.optString("creator_id", ""),
                    creatorName = g.optString("creator_name", "Anonymous"),
                    playCount = g.optInt("play_count", 0),
                    initialPrompt = g.optString("description", ""),
                    createdAt = g.optString("created_at", ""),
                    status = g.optString("status", "published"),
                    thumbnailUrl = g.optString("initial_screenshot_url", "").ifEmpty { null },
                    seriesId = g.optString("series_id", "").ifEmpty { null },
                    levelIndex = g.optInt("level_index", 1),
                    levelCount = g.optInt("level_count", 1),
                    trendingScore = g.optDouble("trending_score", 0.0)
                )
            }
        } catch (_: Exception) {
            null
        }
    }
}

data class BrowseGameData(
    val id: String,
    val title: String,
    val creatorId: String,
    val creatorName: String,
    val playCount: Int,
    val initialPrompt: String,
    val createdAt: String,
    val status: String = "published",
    val thumbnailUrl: String? = null,
    // Feature A / C additions — defaulted so pre-migration server responses still parse.
    val seriesId: String? = null,
    val levelIndex: Int = 1,
    val levelCount: Int = 1,
    val trendingScore: Double = 0.0
)

@Composable
fun GameBrowseContent(context: android.content.Context) {
    val TAG = "GameBrowse"
    val PAGE_SIZE = 20
    var games by remember { mutableStateOf<List<BrowseGameData>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var isLoadingMore by remember { mutableStateOf(false) }
    var sortBy by remember { mutableStateOf("trending") }   // Feature C: default flipped from "newest"
    var newReleases by remember { mutableStateOf<List<BrowseGameData>>(emptyList()) }   // Feature C: carousel above main grid
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
    var coinGateGame by remember { mutableStateOf<BrowseGameData?>(null) }
    var isCoinGateSpending by remember { mutableStateOf(false) }
    val coinManager = CoinManager.shared
    val playCountPrefs = remember { context.getSharedPreferences("riddleverse_play_counts", android.content.Context.MODE_PRIVATE) }
    val FREE_PLAYS_PER_GAME = 3

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
                    val isSuccessful = response.isSuccessful
                    val body = response.body?.string()
                    response.close()

                    // A non-2xx status (e.g. a 500 returning a parseable {"error": "..."} body)
                    // must fall into the cache-fallback path below, same as a null body — otherwise
                    // it silently renders as an empty games list instead of the last-known-good data.
                    if (body != null && isSuccessful) {
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
                                        thumbnailUrl = g.optString("initial_screenshot_url", "").ifEmpty { null },
                                        seriesId = g.optString("series_id", "").ifEmpty { null },
                                        levelIndex = g.optInt("level_index", 1),
                                        levelCount = g.optInt("level_count", 1),
                                        trendingScore = g.optDouble("trending_score", 0.0)
                                    )
                                )
                            }
                        }

                        if (!loadMore) {
                            GamesCache.save(context, sortBy, parsed)
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
                    } else if (!loadMore) {
                        // Empty/failed response on an initial (non-paginated) load — fall back
                        // to the last successfully-cached list rather than showing "no games".
                        val cached = GamesCache.load(context, sortBy)
                        withContext(Dispatchers.Main) {
                            if (cached != null) {
                                games = cached
                                errorMessage = "Showing saved games — couldn't refresh right now"
                            } else {
                                errorMessage = "Empty response from server"
                            }
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
                    val cached = if (!loadMore) GamesCache.load(context, sortBy) else null
                    withContext(Dispatchers.Main) {
                        if (cached != null) {
                            games = cached
                            errorMessage = "Showing saved games — couldn't refresh right now"
                        } else {
                            errorMessage = "Failed to load games"
                        }
                        isLoading = false
                        isLoadingMore = false
                    }
                }
            }
        }
    }

    fun downloadAndPlay(game: BrowseGameData) {
        if (downloadingId != null) return

        val isOwner = game.creatorId == currentUserId
        val userId = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid

        if (!isOwner) {
            val playCount = playCountPrefs.getInt("plays_${game.id}", 0)
            if (playCount >= FREE_PLAYS_PER_GAME) {
                // Free plays exhausted — need coins
                if (!coinManager.canContinue) {
                    coinGateGame = game
                    return
                }
                val creatorId = game.creatorId.takeIf { it.isNotEmpty() }
                coinManager.spendForContinue(game.id, creatorId) { success ->
                    if (success) {
                        ChromeGameLauncher.launchGame(context, game.id, userId)
                    } else {
                        coinGateGame = game
                    }
                }
                return
            }
            // Free play — increment counter
            playCountPrefs.edit().putInt("plays_${game.id}", playCount + 1).apply()
        }

        // Launch game in Chrome Custom Tab — hosted at puzzleverseai.com
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

    // Feature C: pull the carousel data once on first composition
    fun fetchNewReleases() {
        coroutineScope.launch {
            withContext(Dispatchers.IO) {
                try {
                    val request = Request.Builder()
                        .url("https://puzzleverseai.com/api/game-creation/browse?sort=newest&limit=10&offset=0")
                        .get()
                        .build()
                    val response = HttpClientProvider.client.newCall(request).execute()
                    val body = response.body?.string()
                    response.close()
                    if (body != null) {
                        val json = JSONObject(body)
                        val arr = json.optJSONArray("games") ?: return@withContext
                        val parsed = mutableListOf<BrowseGameData>()
                        for (i in 0 until arr.length()) {
                            val g = arr.getJSONObject(i)
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
                                    thumbnailUrl = g.optString("initial_screenshot_url", "").ifEmpty { null },
                                    seriesId = g.optString("series_id", "").ifEmpty { null },
                                    levelIndex = g.optInt("level_index", 1),
                                    levelCount = g.optInt("level_count", 1),
                                    trendingScore = g.optDouble("trending_score", 0.0)
                                )
                            )
                        }
                        withContext(Dispatchers.Main) { newReleases = parsed }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "fetchNewReleases error", e)
                }
            }
        }
    }

    LaunchedEffect(sortBy, showMyGames) {
        fetchGames()
        coinManager.fetchBalance()
        coinManager.loadCoinProducts()
    }
    LaunchedEffect(Unit) { fetchNewReleases() }

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
                color = RvInk
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            // Manual refresh — the only other trigger for fetchGames() is switching sort/tab
            // (LaunchedEffect(sortBy, showMyGames) below), so without this there was no way
            // for a user to force a fresh pull past a cached/stale list.
            IconButton(
                onClick = { if (!isLoading) fetchGames() },
                enabled = !isLoading,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    Icons.Default.Refresh,
                    contentDescription = stringResource(R.string.refresh),
                    tint = RvViolet,
                    modifier = Modifier.size(20.dp)
                )
            }

            // My Games filter
            if (currentUserId != null) {
                Surface(
                    onClick = { showMyGames = !showMyGames },
                    shape = RoundedCornerShape(20.dp),
                    color = if (showMyGames) RvViolet else RvViolet.copy(alpha = 0.15f)
                ) {
                    Text(
                        text = stringResource(R.string.my_games),
                        fontSize = 12.sp,
                        color = if (showMyGames) RvOnTone else RvViolet,
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
                    color = RvViolet.copy(alpha = 0.15f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Sort,
                            contentDescription = stringResource(R.string.sort_by),
                            tint = RvViolet,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = when (sortBy) {
                                "trending" -> "Trending"
                                "popular"  -> stringResource(R.string.most_played)
                                else       -> stringResource(R.string.newest)
                            },
                            fontSize = 12.sp,
                            color = RvViolet,
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
                        text = { Text("Trending") },
                        onClick = { sortBy = "trending"; showSortMenu = false },
                        leadingIcon = {
                            if (sortBy == "trending") Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.most_played)) },
                        onClick = { sortBy = "popular"; showSortMenu = false },
                        leadingIcon = {
                            if (sortBy == "popular") Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.newest)) },
                        onClick = { sortBy = "newest"; showSortMenu = false },
                        leadingIcon = {
                            if (sortBy == "newest") Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                        }
                    )
                }
            }
            } // end Row
        }

        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = RvInk)
            }
        } else if (games.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.SportsEsports,
                        contentDescription = null,
                        tint = RvInkSoft,
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(stringResource(R.string.no_games_yet), fontSize = 18.sp, fontWeight = FontWeight.Bold, color = RvInkSoft)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(stringResource(R.string.be_first_to_create), fontSize = 13.sp, color = RvInkSoft)
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Feature C: New Releases carousel — only on default Trending view of community feed
                if (sortBy == "trending" && !showMyGames && newReleases.isNotEmpty()) {
                    item(key = "new_releases_carousel") {
                        Column {
                            Text(
                                text = "New Releases",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = RvInk,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                            androidx.compose.foundation.lazy.LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                items(newReleases, key = { "carousel-${it.id}" }) { rel ->
                                    Column(
                                        modifier = Modifier
                                            .width(140.dp)
                                            .clickable { downloadAndPlay(rel) }
                                    ) {
                                        if (rel.thumbnailUrl != null) {
                                            AsyncImage(
                                                model = rel.thumbnailUrl,
                                                contentDescription = rel.title,
                                                contentScale = ContentScale.Crop,
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .height(100.dp)
                                                    .clip(RoundedCornerShape(16.dp))
                                            )
                                        } else {
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .height(100.dp)
                                                    .clip(RoundedCornerShape(16.dp))
                                                    .background(RvSurface),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(
                                                    Icons.Default.SportsEsports,
                                                    contentDescription = null,
                                                    tint = RvInkSoft
                                                )
                                            }
                                        }
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = rel.title,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = RvInk,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

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
                                    color = RvInk,
                                    strokeWidth = 2.dp
                                )
                            } else {
                                OutlinedButton(
                                    onClick = { fetchGames(loadMore = true) },
                                    shape = RoundedCornerShape(20.dp),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = RvViolet),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, RvViolet.copy(alpha = 0.3f))
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

        // Coin gate dialog — shown when free plays are exhausted
        if (coinGateGame != null) {
            val game = coinGateGame!!
            Dialog(
                onDismissRequest = { if (!isCoinGateSpending) coinGateGame = null },
                properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(RvScrim),
                    contentAlignment = Alignment.Center
                ) {
                    Card(
                        modifier = Modifier
                            .padding(horizontal = 32.dp)
                            .fillMaxWidth(),
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.cardColors(containerColor = RvCanvas),
                        elevation = CardDefaults.cardElevation(defaultElevation = 16.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(24.dp).fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text("\uD83C\uDFAE", fontSize = 40.sp)
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "Free Plays Used Up",
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                                color = RvInk
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "You've used your $FREE_PLAYS_PER_GAME free plays for \"${game.title}\". Spend coins to keep playing!",
                                fontSize = 14.sp,
                                color = RvInkSoft,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            // Coin balance
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .background(Color.Yellow.copy(alpha = 0.12f), RoundedCornerShape(20.dp))
                                    .padding(horizontal = 16.dp, vertical = 6.dp)
                            ) {
                                Icon(Icons.Default.Star, contentDescription = null, tint = Color.Yellow, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("${coinManager.balance} coins", color = Color.Yellow, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                            }
                            Spacer(modifier = Modifier.height(20.dp))
                            Button(
                                onClick = {
                                    if (!coinManager.canContinue) return@Button
                                    isCoinGateSpending = true
                                    val uid = FirebaseAuth.getInstance().currentUser?.uid
                                    val creatorId = game.creatorId.takeIf { it.isNotEmpty() }
                                    coinManager.spendForContinue(game.id, creatorId) { success ->
                                        isCoinGateSpending = false
                                        if (success) {
                                            coinGateGame = null
                                            ChromeGameLauncher.launchGame(context, game.id, uid)
                                        }
                                    }
                                },
                                enabled = coinManager.canContinue && !isCoinGateSpending,
                                modifier = Modifier.fillMaxWidth().height(52.dp),
                                shape = RoundedCornerShape(14.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (coinManager.canContinue) RvViolet else RvDisabled.copy(alpha = 0.5f)
                                )
                            ) {
                                if (isCoinGateSpending) {
                                    CircularProgressIndicator(modifier = Modifier.size(20.dp), color = RvOnTone, strokeWidth = 2.dp)
                                } else {
                                    Text(
                                        text = "\uD83D\uDD04  Play — \uD83E\uDE99 ${CoinManager.CONTINUE_COST} coins",
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = RvOnTone
                                    )
                                }
                            }
                            if (!coinManager.canContinue) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Not enough coins — need ${CoinManager.CONTINUE_COST}",
                                    fontSize = 12.sp,
                                    color = RvError,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            TextButton(onClick = { coinGateGame = null }, enabled = !isCoinGateSpending) {
                                Text("Maybe Later", color = RvInkSoft)
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
                colors = CardDefaults.cardColors(containerColor = RvError.copy(alpha = 0.15f)),
                shape = RoundedCornerShape(16.dp)
            ) {
                Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Warning, contentDescription = null, tint = RvError, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(errorMessage!!, fontSize = 13.sp, color = RvInkSoft)
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
        colors = CardDefaults.cardColors(containerColor = RvSurface),
        shape = RoundedCornerShape(16.dp),
        border = CardDefaults.outlinedCardBorder().copy(
            brush = Brush.linearGradient(listOf(RvSurface, RvSurface))
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
                        .clickable { onPlay() }
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
                            .background(RvInk.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.PlayCircle,
                            contentDescription = null,
                            tint = RvOnTone.copy(alpha = 0.8f),
                            modifier = Modifier.size(48.dp)
                        )
                    }
                    // Feature A: "X levels" series badge (top-right)
                    if (game.levelCount > 1) {
                        Surface(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(8.dp),
                            shape = RoundedCornerShape(20.dp),
                            color = RvInk.copy(alpha = 0.65f)
                        ) {
                            Text(
                                text = "${game.levelCount} levels",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = RvOnTone,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
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
                            .background(RvViolet.copy(alpha = 0.15f), RoundedCornerShape(16.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.SportsEsports,
                            contentDescription = null,
                            tint = RvViolet,
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
                            color = RvInk,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        if (game.status == "draft") {
                            Spacer(modifier = Modifier.width(6.dp))
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = RvError.copy(alpha = 0.2f)
                            ) {
                                Text(
                                    text = stringResource(R.string.draft),
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = RvError,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "by ${game.creatorName}",
                        fontSize = 12.sp,
                        color = RvInkSoft
                    )
                }

                // Play count
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = RvSurface
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.PlayArrow,
                            contentDescription = null,
                            tint = RvInkSoft,
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "${game.playCount}",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = RvInkSoft
                        )
                    }
                }
            }

            // Prompt preview — tap to expand. Hide if description duplicates the title
            // (server falls back to title=prompt.substring(0,100), description=prompt.substring(0,500),
            // so title is always a prefix of description when no explicit title is set).
            val showDescription = run {
                val d = game.initialPrompt.trim()
                if (d.isEmpty()) return@run false
                val t = game.title.trim().removeSuffix("…").removeSuffix("...").trim()
                if (t.isEmpty()) return@run true
                !(d.equals(t, ignoreCase = true) || d.startsWith(t, ignoreCase = true))
            }
            if (showDescription) {
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = game.initialPrompt,
                    fontSize = 12.sp,
                    color = RvInkSoft,
                    maxLines = if (promptExpanded) Int.MAX_VALUE else 2,
                    overflow = if (promptExpanded) TextOverflow.Clip else TextOverflow.Ellipsis,
                    modifier = Modifier.clickable { promptExpanded = !promptExpanded }
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Action row: Play (if no thumbnail) + owner/share controls right-aligned
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Play button — shown only when no thumbnail (thumbnail itself is clickable)
                if (game.thumbnailUrl == null) {
                    Button(
                        onClick = onPlay,
                        enabled = !isDownloading,
                        modifier = Modifier.height(40.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = RvViolet,
                            disabledContainerColor = RvViolet.copy(alpha = 0.5f)
                        )
                    ) {
                        if (isDownloading) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), color = RvOnTone, strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(stringResource(R.string.loading), color = RvOnTone, fontSize = 14.sp)
                        } else {
                            Icon(Icons.Default.PlayCircle, contentDescription = null, tint = RvOnTone, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(stringResource(R.string.play), color = RvOnTone, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                        }
                    }
                } else {
                    Spacer(modifier = Modifier.width(0.dp))
                }

                // Right-side controls: consistent icon-only buttons
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    // Edit (owner only)
                    if (isOwner && onEdit != null) {
                        IconButton(
                            onClick = onEdit,
                            modifier = Modifier
                                .size(40.dp)
                                .background(RvGrape.copy(alpha = 0.2f), RoundedCornerShape(16.dp))
                        ) {
                            Icon(Icons.Default.AutoAwesome, contentDescription = "Edit", tint = RvGrape, modifier = Modifier.size(18.dp))
                        }
                    }

                    // Delete (owner only)
                    if (isOwner && onDelete != null) {
                        IconButton(
                            onClick = onDelete,
                            enabled = !isDeleting,
                            modifier = Modifier
                                .size(40.dp)
                                .background(RvError.copy(alpha = 0.15f), RoundedCornerShape(16.dp))
                        ) {
                            if (isDeleting) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), color = RvError, strokeWidth = 2.dp)
                            } else {
                                Icon(Icons.Default.Delete, contentDescription = "Delete", tint = RvError, modifier = Modifier.size(18.dp))
                            }
                        }
                    }

                    // Share
                    IconButton(
                        onClick = { shareGameToTelegram(context, game) },
                        modifier = Modifier
                            .size(40.dp)
                            .background(RvSky.copy(alpha = 0.15f), RoundedCornerShape(16.dp))
                    ) {
                        Icon(Icons.Default.Share, contentDescription = "Share", tint = RvSky, modifier = Modifier.size(18.dp))
                    }
                }
            }

            // Leaderboard section
            GameCardLeaderboard(gameId = game.id)

            // Edit history section
            GameCardEdits(gameId = game.id)
        }
        }
    }
}

@Composable
fun GameCardLeaderboard(gameId: String) {
    var expanded by remember { mutableStateOf(false) }
    var entries by remember { mutableStateOf<List<GameScoreEntry>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    val currentUserId = remember { com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid ?: "" }
    val coroutineScope = rememberCoroutineScope()

    fun load() {
        if (loading) return
        loading = true
        coroutineScope.launch {
            withContext(kotlinx.coroutines.Dispatchers.IO) {
                try {
                    val url = "https://puzzleverseai.com/api/chat-scores/$gameId/_app_?platform=app"
                    val req = Request.Builder().url(url).get().build()
                    val resp = HttpClientProvider.client.newCall(req).execute()
                    val body = resp.body?.string(); resp.close()
                    if (body != null) {
                        val json = org.json.JSONObject(body)
                        val arr = json.optJSONArray("scores") ?: org.json.JSONArray()
                        entries = (0 until arr.length()).map { i ->
                            val s = arr.getJSONObject(i)
                            GameScoreEntry(s.optString("username","Player"), s.optInt("score",0), s.optString("userId",""))
                        }
                    }
                } catch (_: Exception) {}
                loading = false
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                expanded = !expanded
                if (expanded && entries.isEmpty()) load()
            }
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("🏆 Leaderboard", fontSize = 13.sp, color = RvSunEdge, fontWeight = FontWeight.SemiBold)
            androidx.compose.material3.Icon(
                if (expanded) androidx.compose.material.icons.Icons.Default.KeyboardArrowUp
                else androidx.compose.material.icons.Icons.Default.KeyboardArrowDown,
                contentDescription = null,
                tint = RvInkSoft,
                modifier = Modifier.size(18.dp)
            )
        }
        if (expanded) {
            Spacer(Modifier.height(8.dp))
            if (loading) {
                androidx.compose.material3.CircularProgressIndicator(
                    modifier = Modifier.size(18.dp).align(Alignment.CenterHorizontally),
                    color = RvViolet, strokeWidth = 2.dp
                )
            } else if (entries.isEmpty()) {
                Text("No scores yet — be the first!", fontSize = 12.sp, color = RvInkSoft)
            } else {
                val medals = listOf("🥇","🥈","🥉")
                entries.take(5).forEachIndexed { i, e ->
                    val isMe = e.userId == currentUserId
                    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(medals.getOrElse(i){"${i+1}."}, fontSize = 13.sp, modifier = Modifier.width(24.dp))
                        Text(e.username, fontSize = 12.sp, color = if(isMe) RvViolet else RvInk,
                            fontWeight = if(isMe) FontWeight.Bold else FontWeight.Normal,
                            modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("${e.score}", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = RvSunEdge)
                    }
                }
            }
        }
    }
}

data class GameEditVersion(
    val sha: String,
    val shortSha: String,
    val message: String,
    val date: String,
    val author: String
) {
    val isInitial: Boolean get() = message == "Initial game creation"
    val displayMessage: String get() = if (message.startsWith("Tweak: ")) message.removePrefix("Tweak: ") else message
}

private fun relativeTimeFromIso(isoDate: String): String {
    if (isoDate.isEmpty()) return ""
    return try {
        val instant = java.time.OffsetDateTime.parse(isoDate).toInstant()
        val seconds = java.time.Duration.between(instant, java.time.Instant.now()).seconds
        when {
            seconds < 60 -> "just now"
            seconds < 3600 -> "${seconds / 60}m ago"
            seconds < 86400 -> "${seconds / 3600}h ago"
            else -> "${seconds / 86400}d ago"
        }
    } catch (_: Exception) {
        ""
    }
}

@Composable
fun GameCardEdits(gameId: String) {
    var expanded by remember { mutableStateOf(false) }
    var versions by remember { mutableStateOf<List<GameEditVersion>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var fetched by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    fun load() {
        if (loading) return
        loading = true
        coroutineScope.launch {
            withContext(Dispatchers.IO) {
                try {
                    val url = "https://puzzleverseai.com/api/game-creation/$gameId/versions"
                    val req = Request.Builder().url(url).get().build()
                    val resp = HttpClientProvider.client.newCall(req).execute()
                    val body = resp.body?.string(); resp.close()
                    if (body != null) {
                        val json = JSONObject(body)
                        val arr = json.optJSONArray("versions")
                        val parsed = mutableListOf<GameEditVersion>()
                        if (arr != null) {
                            for (i in 0 until arr.length()) {
                                val v = arr.getJSONObject(i)
                                val sha = v.optString("sha", "")
                                parsed.add(
                                    GameEditVersion(
                                        sha = sha,
                                        shortSha = v.optString("shortSha", sha.take(7)),
                                        message = v.optString("message", ""),
                                        date = v.optString("date", ""),
                                        author = v.optString("author", "")
                                    )
                                )
                            }
                        }
                        versions = parsed
                    }
                } catch (_: Exception) {}
                loading = false
                fetched = true
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                expanded = !expanded
                if (expanded && !fetched) load()
            }
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("✨ Edits", fontSize = 13.sp, color = RvGrape, fontWeight = FontWeight.SemiBold)
                if (fetched && versions.isNotEmpty()) {
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "(${versions.count { !it.isInitial }})",
                        fontSize = 11.sp,
                        color = RvInkSoft
                    )
                }
            }
            Icon(
                if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                contentDescription = null,
                tint = RvInkSoft,
                modifier = Modifier.size(18.dp)
            )
        }
        if (expanded) {
            Spacer(Modifier.height(8.dp))
            when {
                loading -> CircularProgressIndicator(
                    modifier = Modifier
                        .size(18.dp)
                        .align(Alignment.CenterHorizontally),
                    color = RvGrape,
                    strokeWidth = 2.dp
                )
                versions.isEmpty() -> Text(
                    "No edits yet",
                    fontSize = 12.sp,
                    color = RvInkSoft
                )
                else -> versions.forEach { v ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Box(
                            modifier = Modifier
                                .padding(top = 5.dp)
                                .size(8.dp)
                                .background(
                                    if (v.isInitial) RvSuccessEdge else RvGrape,
                                    CircleShape
                                )
                        )
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                v.displayMessage,
                                fontSize = 12.sp,
                                color = RvInk,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(Modifier.height(2.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (v.shortSha.isNotEmpty()) {
                                    Text(
                                        v.shortSha,
                                        fontSize = 10.sp,
                                        color = RvInkSoft,
                                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                                    )
                                }
                                val ago = relativeTimeFromIso(v.date)
                                if (ago.isNotEmpty()) {
                                    Spacer(Modifier.width(8.dp))
                                    Text(ago, fontSize = 10.sp, color = RvInkSoft)
                                }
                                if (v.isInitial) {
                                    Spacer(Modifier.width(8.dp))
                                    Surface(
                                        shape = RoundedCornerShape(4.dp),
                                        color = RvSuccessEdge.copy(alpha = 0.15f)
                                    ) {
                                        Text(
                                            "Original",
                                            fontSize = 9.sp,
                                            color = RvSuccessEdge,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
                                }
                            }
                        }
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
            .background(RvCanvas)
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
                tint = RvOnTone,
                modifier = Modifier
                    .size(28.dp)
                    .background(RvInk.copy(alpha = 0.5f), CircleShape)
                    .padding(4.dp)
            )
        }

        // How to Play dialog
        if (showHowToPlay) {
            AlertDialog(
                onDismissRequest = { showHowToPlay = false },
                title = { Text("How to Play", color = RvInk, fontWeight = FontWeight.Bold) },
                text = { Text(game.initialPrompt, color = RvInk, fontSize = 14.sp) },
                confirmButton = {
                    TextButton(onClick = { showHowToPlay = false }) {
                        Text("Got it!", color = RvViolet, fontWeight = FontWeight.SemiBold)
                    }
                },
                containerColor = RvCanvas,
                titleContentColor = RvInk
            )
        }

        // Generation progress overlay
        if (isGeneratingHarder) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(RvCanvas.copy(alpha = 0.95f)),
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
                        color = RvInk
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Lv.${currentDifficultyLevel + 1}",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = RvError
                    )
                    Spacer(modifier = Modifier.height(24.dp))

                    // Progress bar
                    LinearProgressIndicator(
                        progress = { generationProgress.toFloat().coerceIn(0f, 1f) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp),
                        color = RvError,
                        trackColor = RvOutline
                    )

                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = generationPhase,
                        fontSize = 13.sp,
                        color = RvInkSoft
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.this_may_take_2_5_minutes),
                        fontSize = 12.sp,
                        color = RvInkSoft
                    )
                }
            }
        }

        // Customization progress overlay
        if (isCustomizing) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(RvCanvas.copy(alpha = 0.95f)),
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
                        color = RvInk
                    )
                    Spacer(modifier = Modifier.height(24.dp))

                    LinearProgressIndicator(
                        progress = { customizeProgress.toFloat().coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth().height(6.dp),
                        color = RvGrape,
                        trackColor = RvOutline
                    )

                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = customizePhase,
                        fontSize = 13.sp,
                        color = RvInkSoft
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.this_may_take_2_5_minutes),
                        fontSize = 12.sp,
                        color = RvInkSoft
                    )
                }
            }
        }

        // Game over overlay (hidden when generating harder variant or customizing)
        // Replay gate — shown when user opens a game they've already played
        if (showReplayGate) {
            Box(
                modifier = Modifier.fillMaxSize().background(RvCanvas.copy(alpha = 0.95f)),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth().padding(40.dp)
                ) {
                    Text("🎮", fontSize = 48.sp)
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Play Again?", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = RvInk)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "You've already played this game.\nSpend ${CoinManager.CONTINUE_COST} coins to play again.",
                        fontSize = 15.sp, color = RvInkSoft,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    Button(
                        onClick = { handleContinue() },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = RvViolet)
                    ) {
                        Icon(Icons.Default.PlayCircle, contentDescription = null, tint = RvOnTone)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Play Again", color = RvOnTone, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.weight(1f))
                        Surface(shape = RoundedCornerShape(16.dp), color = RvOnTone.copy(alpha = 0.2f)) {
                            Row(modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Star, contentDescription = null, tint = Color.Yellow, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("${CoinManager.CONTINUE_COST}", color = Color.Yellow, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    TextButton(onClick = { onClose() }) {
                        Text("Back", color = RvInkSoft)
                    }
                }
            }
        }

        if (gameOver && !isGeneratingHarder && !isCustomizing) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(RvCanvas.copy(alpha = 0.95f)),
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
                        color = RvInk
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = "$currentScore",
                        fontSize = 64.sp,
                        fontWeight = FontWeight.Bold,
                        color = RvViolet
                    )

                    Text(
                        text = stringResource(R.string.points),
                        fontSize = 18.sp,
                        color = RvInkSoft
                    )

                    if (scoreSubmitted) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = RvSuccessEdge, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(stringResource(R.string.score_submitted), fontSize = 13.sp, color = RvSuccessEdge)
                        }
                    }

                    // Error message in game over overlay
                    if (errorMessage != null) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = RvError.copy(alpha = 0.15f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Warning, contentDescription = null, tint = RvError, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(errorMessage!!, fontSize = 12.sp, color = RvInkSoft)
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
                            color = RvInkSoft
                        )
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    // Play Again (costs coins — creator gets share)
                    Button(
                        onClick = { handleContinue() },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = RvViolet
                        )
                    ) {
                        Icon(Icons.Default.PlayCircle, contentDescription = null, tint = RvOnTone)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.play_again), color = RvOnTone, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.weight(1f))
                        // Coin cost badge
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .background(RvOnTone.copy(alpha = 0.2f), RoundedCornerShape(16.dp))
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
                            shape = RoundedCornerShape(16.dp),
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
                                            listOf(RvCoral, RvCoralEdge)
                                        ),
                                        RoundedCornerShape(16.dp)
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
                                        color = RvOnTone,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.sp
                                    )
                                    Spacer(modifier = Modifier.weight(1f))
                                    Surface(
                                        shape = RoundedCornerShape(16.dp),
                                        color = RvInk.copy(alpha = 0.3f)
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                Icons.Default.Circle,
                                                contentDescription = null,
                                                tint = RvSunEdge,
                                                modifier = Modifier.size(8.dp)
                                            )
                                            Spacer(modifier = Modifier.width(3.dp))
                                            Text(
                                                text = "$harderCost",
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = if (coinManager.canAffordDifficulty(nextLevel)) RvSunEdge else RvError
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
                        shape = RoundedCornerShape(16.dp),
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
                                        listOf(RvGrape, RvGrapeEdge)
                                    ),
                                    RoundedCornerShape(16.dp)
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
                                    color = RvOnTone,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp
                                )
                                Spacer(modifier = Modifier.weight(1f))
                                Surface(
                                    shape = RoundedCornerShape(16.dp),
                                    color = RvInk.copy(alpha = 0.3f)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            Icons.Default.Circle,
                                            contentDescription = null,
                                            tint = RvSunEdge,
                                            modifier = Modifier.size(8.dp)
                                        )
                                        Spacer(modifier = Modifier.width(3.dp))
                                        Text(
                                            text = "${CoinManager.CUSTOMIZE_COST}",
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (coinManager.canAffordCustomize()) RvSunEdge else RvError
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
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = RvSunEdge),
                            border = androidx.compose.foundation.BorderStroke(1.dp, RvSunEdge.copy(alpha = 0.3f))
                        ) {
                            Icon(Icons.Default.AddCircle, contentDescription = null, tint = RvSunEdge)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(stringResource(R.string.get_coins), fontWeight = FontWeight.Bold, color = RvSunEdge)
                        }

                        Spacer(modifier = Modifier.height(12.dp))
                    }

                    Button(
                        onClick = {
                            showLeaderboard = true
                            fetchLeaderboard()
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = RvViolet)
                    ) {
                        Icon(Icons.Default.EmojiEvents, contentDescription = null, tint = RvOnTone)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.view_leaderboard), color = RvOnTone, fontWeight = FontWeight.Bold)
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
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = RvInk)
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
                    .background(RvCanvas.copy(alpha = 0.95f)),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    CircularProgressIndicator(color = RvInk)
                    Text("Fixing game...", color = RvInk, fontWeight = FontWeight.Bold)
                    if (fixPhase.isNotEmpty()) {
                        Text(fixPhase, color = RvInkSoft, fontSize = 12.sp)
                    }
                    if (fixProgress > 0f) {
                        LinearProgressIndicator(
                            progress = { fixProgress / 100f },
                            modifier = Modifier.width(200.dp),
                            color = RvViolet
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
                    Text("Brief description — even a couple words helps", fontSize = 13.sp, color = RvInkSoft)
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
        containerColor = RvCanvas,
        contentColor = RvInk
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
                color = RvInk,
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
                    tint = RvSunEdge,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "${coinManager.balance}",
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Bold,
                    color = RvInk
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "coins",
                    fontSize = 14.sp,
                    color = RvInkSoft
                )
            }

            // Coin packs
            CoinManager.COIN_PACKS.forEach { pack ->
                val price = coinManager.getProductPrice(pack.productId) ?: "..."

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp),
                    colors = CardDefaults.cardColors(containerColor = RvSurface),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Circle,
                            contentDescription = null,
                            tint = RvSunEdge,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = pack.label,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = RvInk
                            )
                            if (pack.coins == 1200) {
                                Text(
                                    text = "Best Value",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = RvSuccessEdge
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
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = RvViolet),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                        ) {
                            Text(
                                text = price,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = RvOnTone
                            )
                        }
                    }
                }
            }

            // Purchase feedback
            if (coinManager.purchaseMessage != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = RvSuccessEdge, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(coinManager.purchaseMessage!!, fontSize = 13.sp, color = RvSuccessEdge)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Coins are used to continue games and support creators.",
                fontSize = 12.sp,
                color = RvInkSoft
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
        containerColor = RvCanvas,
        contentColor = RvInk
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
                color = RvInk,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            if (isLoading) {
                Box(
                    modifier = Modifier.fillMaxWidth().height(screenHeight * 0.22f),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = RvInk)
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
                            tint = RvInkSoft,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(stringResource(R.string.no_scores_yet), fontSize = 16.sp, color = RvInkSoft)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(stringResource(R.string.play_to_set_score), fontSize = 13.sp, color = RvInkSoft)
                    }
                }
            } else {
                // User rank card
                if (userRank != null) {
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                        colors = CardDefaults.cardColors(containerColor = RvViolet.copy(alpha = 0.12f)),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(Icons.Default.Person, contentDescription = null, tint = RvViolet, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Your rank: #$userRank", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = RvViolet)
                        }
                    }
                }

                // Player list
                players.forEach { player ->
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (player.isCurrentUser) RvViolet.copy(alpha = 0.15f) else RvSurface
                        ),
                        shape = RoundedCornerShape(16.dp)
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
                                    color = if (player.isCurrentUser) RvViolet else RvInk,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            Text(
                                text = "${player.score}",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = RvViolet
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
            .background(RvCanvas)
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
                    Icon(Icons.Default.Close, contentDescription = stringResource(R.string.close), tint = RvInk)
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Edit: ${game.title}",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = RvInk,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (freeTweaksRemaining > 0) {
                        Text(
                            text = "$freeTweaksRemaining free edit${if (freeTweaksRemaining == 1) "" else "s"} left",
                            fontSize = 11.sp,
                            color = RvSuccessEdge
                        )
                    } else {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Circle, contentDescription = null, tint = RvSunEdge, modifier = Modifier.size(8.dp))
                            Spacer(modifier = Modifier.width(3.dp))
                            Text("$tweakCost coins per edit", fontSize = 11.sp, color = RvSunEdge)
                        }
                    }
                }

                // Coin balance
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = RvSunEdge.copy(alpha = 0.15f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Circle, contentDescription = null, tint = RvSunEdge, modifier = Modifier.size(10.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("${coinManager.balance}", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = RvSunEdge)
                    }
                }
            }

            HorizontalDivider(color = RvOutline)

            // Version history
            if (isLoadingVersions) {
                Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = RvInk)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Loading version history...", fontSize = 13.sp, color = RvInkSoft)
                    }
                }
            } else if (versions.isEmpty()) {
                Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(40.dp)) {
                        Icon(Icons.Default.Search, contentDescription = null, tint = RvInkSoft, modifier = Modifier.size(40.dp))
                        Spacer(modifier = Modifier.height(12.dp))
                        Text("No version history yet", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = RvInkSoft)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("Describe your change below to start editing", fontSize = 13.sp, color = RvInkSoft)
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
                    colors = CardDefaults.cardColors(containerColor = RvError.copy(alpha = 0.15f)),
                    shape = RoundedCornerShape(16.dp),
                    onClick = { errorMessage = null }
                ) {
                    Row(modifier = Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Warning, contentDescription = null, tint = RvError, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(errorMessage!!, fontSize = 13.sp, color = RvInk)
                    }
                }
            }

            if (successMessage != null) {
                Card(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                    colors = CardDefaults.cardColors(containerColor = RvSuccessEdge.copy(alpha = 0.15f)),
                    shape = RoundedCornerShape(16.dp),
                    onClick = { successMessage = null }
                ) {
                    Row(modifier = Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = RvSuccessEdge, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(successMessage!!, fontSize = 13.sp, color = RvInk)
                    }
                }
            }

            // Tweak input bar
            HorizontalDivider(color = RvOutline)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(RvSurface)
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = tweakText,
                    onValueChange = { tweakText = it },
                    placeholder = { Text("Describe your change...", color = RvInkSoft) },
                    modifier = Modifier.weight(1f),
                    enabled = !isTweaking,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color.Transparent,
                        unfocusedBorderColor = Color.Transparent,
                        focusedContainerColor = RvSurface,
                        unfocusedContainerColor = RvSurface,
                        focusedTextColor = RvInk,
                        unfocusedTextColor = RvInk,
                        cursorColor = RvViolet
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
                            if (canSend) RvViolet.copy(alpha = 0.2f) else RvSurface,
                            CircleShape
                        )
                ) {
                    Icon(
                        Icons.Default.Send,
                        contentDescription = "Send",
                        tint = if (canSend) RvViolet else RvDisabled
                    )
                }
            }
        }

        // Generation overlay
        if (isTweaking) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(RvCanvas.copy(alpha = 0.95f)),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(40.dp)
                ) {
                    Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = RvViolet, modifier = Modifier.size(48.dp))
                    Spacer(modifier = Modifier.height(20.dp))
                    Text("Applying Edit", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = RvInk)
                    Spacer(modifier = Modifier.height(24.dp))
                    LinearProgressIndicator(
                        progress = { tweakProgress.coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth().height(6.dp),
                        color = RvViolet,
                        trackColor = RvOutline
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(tweakPhase, fontSize = 13.sp, color = RvInkSoft)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("This may take a minute...", fontSize = 12.sp, color = RvInkSoft)
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
        colors = CardDefaults.cardColors(containerColor = RvSurface),
        shape = RoundedCornerShape(16.dp)
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
                            if (isInitial) RvSuccessEdge else RvViolet,
                            CircleShape
                        )
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = displayMsg,
                    fontSize = 14.sp,
                    color = RvInk,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row {
                    Text(
                        text = version.shortSha,
                        fontSize = 11.sp,
                        color = RvInkSoft,
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                    )
                }
            }

            if (isInitial) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = RvSuccessEdge.copy(alpha = 0.15f)
                ) {
                    Text(
                        text = "Original",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = RvSuccessEdge,
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
        containerColor = RvCanvas,
        contentColor = RvInk
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
                color = RvInk
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "Describe what you'd like to change. A new game will be created under your name.",
                fontSize = 13.sp,
                color = RvInkSoft
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Game name field
            Text("Game Name", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = RvInkSoft)
            Spacer(modifier = Modifier.height(6.dp))
            OutlinedTextField(
                value = newTitle,
                onValueChange = { newTitle = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = RvGrape,
                    unfocusedBorderColor = RvOutline,
                    focusedContainerColor = RvSurface,
                    unfocusedContainerColor = RvSurface,
                    focusedTextColor = RvInk,
                    unfocusedTextColor = RvInk,
                    cursorColor = RvGrape
                ),
                shape = RoundedCornerShape(16.dp)
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Description field
            Text("What would you like to change?", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = RvInkSoft)
            Spacer(modifier = Modifier.height(6.dp))
            OutlinedTextField(
                value = description,
                onValueChange = { if (it.length <= 500) description = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp),
                placeholder = { Text("e.g., Make it space-themed with asteroids instead of blocks...", color = RvInkSoft) },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = RvGrape,
                    unfocusedBorderColor = RvOutline,
                    focusedContainerColor = RvSurface,
                    unfocusedContainerColor = RvSurface,
                    focusedTextColor = RvInk,
                    unfocusedTextColor = RvInk,
                    cursorColor = RvGrape
                ),
                shape = RoundedCornerShape(16.dp)
            )

            Text(
                text = "${description.length}/500",
                fontSize = 11.sp,
                color = RvInkSoft,
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
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.Transparent,
                    disabledContainerColor = RvSurface
                ),
                contentPadding = PaddingValues(0.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            if (description.trim().isNotEmpty())
                                Brush.horizontalGradient(listOf(RvGrape, RvGrapeEdge))
                            else
                                Brush.horizontalGradient(listOf(RvDisabled.copy(alpha = 0.3f), RvDisabled.copy(alpha = 0.3f))),
                            RoundedCornerShape(16.dp)
                        )
                        .padding(vertical = 14.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(text = "\u2728", fontSize = 16.sp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Create Customized Game", fontWeight = FontWeight.Bold, color = RvOnTone)
                        Spacer(modifier = Modifier.width(8.dp))
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = RvInk.copy(alpha = 0.3f)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.Circle,
                                    contentDescription = null,
                                    tint = RvSunEdge,
                                    modifier = Modifier.size(8.dp)
                                )
                                Spacer(modifier = Modifier.width(3.dp))
                                Text("${CoinManager.CUSTOMIZE_COST}", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = RvSunEdge)
                            }
                        }
                    }
                }
            }
        }
    }
}
