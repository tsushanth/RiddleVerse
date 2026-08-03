package com.kreativekoala.riddleverse

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONObject

private const val API_BASE = "https://puzzleverseai.com"

data class GameScoreEntry(val username: String, val score: Int, val userId: String)

/**
 * Shown when a Chrome Custom Tab game completes and deep links back.
 * Displays the score and offers Play Again (10 coins) or dismiss.
 */
@Composable
fun GameCompleteDialog(
    result: ChromeGameLauncher.GameCompleteResult,
    onPlayAgain: (ChromeGameLauncher.GameCompleteResult) -> Unit,
    onDismiss: () -> Unit
) {
    val coinBalance = CoinManager.shared.balance
    val canAfford = coinBalance >= CoinManager.CONTINUE_COST
    val currentUserId = FirebaseAuth.getInstance().currentUser?.uid ?: ""
    var leaderboard by remember { mutableStateOf<List<GameScoreEntry>>(emptyList()) }
    var leaderboardLoading by remember { mutableStateOf(true) }

    LaunchedEffect(result.gameId) {
        withContext(Dispatchers.IO) {
            try {
                val url = "$API_BASE/api/chat-scores/${result.gameId}/_app_?platform=app"
                val request = Request.Builder().url(url).get().build()
                val response = HttpClientProvider.client.newCall(request).execute()
                val body = response.body?.string()
                response.close()
                if (body != null) {
                    val json = JSONObject(body)
                    val scores = json.optJSONArray("scores") ?: org.json.JSONArray()
                    val entries = (0 until scores.length()).map { i ->
                        val s = scores.getJSONObject(i)
                        GameScoreEntry(
                            username = s.optString("username", "Player"),
                            score = s.optInt("score", 0),
                            userId = s.optString("userId", "")
                        )
                    }
                    leaderboard = entries
                }
            } catch (e: Exception) {
                // Silently ignore — leaderboard is optional
            } finally {
                leaderboardLoading = false
            }
        }
    }

    val scoreScale by animateFloatAsState(
        targetValue = 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "score_scale"
    )

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.7f)),
            contentAlignment = Alignment.Center
        ) {
            Card(
                modifier = Modifier
                    .padding(horizontal = 32.dp)
                    .fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFF1A1A2E)
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 16.dp)
            ) {
                Column(
                    modifier = Modifier
                        .padding(24.dp)
                        .fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Trophy icon
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.linearGradient(
                                    colors = listOf(Color(0xFFFFD700), Color(0xFFFF8C00))
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "\uD83C\uDFC6",
                            fontSize = 36.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = "Game Complete!",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Score display
                    Box(
                        modifier = Modifier
                            .scale(scoreScale)
                            .clip(RoundedCornerShape(16.dp))
                            .background(
                                Brush.linearGradient(
                                    colors = listOf(
                                        Color(0xFF6C63FF).copy(alpha = 0.3f),
                                        Color(0xFF9C27B0).copy(alpha = 0.3f)
                                    )
                                )
                            )
                            .padding(horizontal = 32.dp, vertical = 16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "Score",
                                fontSize = 14.sp,
                                color = Color.White.copy(alpha = 0.7f)
                            )
                            Text(
                                text = "${result.score}",
                                fontSize = 48.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color.White
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    // Leaderboard section
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color.White.copy(alpha = 0.05f))
                            .padding(12.dp)
                    ) {
                        Text(
                            text = "🏆 Leaderboard",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFFFD700),
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        if (leaderboardLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp).align(Alignment.CenterHorizontally),
                                color = Color(0xFFFF8C00),
                                strokeWidth = 2.dp
                            )
                        } else if (leaderboard.isEmpty()) {
                            Text(
                                text = "Be the first on the leaderboard!",
                                fontSize = 12.sp,
                                color = Color.White.copy(alpha = 0.5f),
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth()
                            )
                        } else {
                            val medals = listOf("🥇", "🥈", "🥉")
                            leaderboard.take(5).forEachIndexed { i, entry ->
                                val isMe = entry.userId == currentUserId
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 3.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = medals.getOrElse(i) { "${i + 1}." },
                                        fontSize = 14.sp,
                                        modifier = Modifier.width(28.dp)
                                    )
                                    Text(
                                        text = entry.username,
                                        fontSize = 13.sp,
                                        color = if (isMe) Color(0xFFFF8C00) else Color.White,
                                        fontWeight = if (isMe) FontWeight.Bold else FontWeight.Normal,
                                        modifier = Modifier.weight(1f),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "${entry.score}",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFFFFD700)
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Play Again button
                    Button(
                        onClick = { onPlayAgain(result) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (canAfford) Color(0xFF6C63FF) else Color.Gray.copy(alpha = 0.5f)
                        )
                    ) {
                        Text(
                            text = "\uD83D\uDD04  Play Again",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        // Coin cost badge
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color.White.copy(alpha = 0.2f))
                                .padding(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "\uD83E\uDE99 ${CoinManager.CONTINUE_COST}",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (canAfford) Color(0xFFFFD700) else Color.Red.copy(alpha = 0.8f)
                            )
                        }
                    }

                    if (!canAfford) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "You have $coinBalance coins — need ${CoinManager.CONTINUE_COST}",
                            fontSize = 12.sp,
                            color = Color(0xFFFF6B6B),
                            textAlign = TextAlign.Center
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Back to Home button
                    TextButton(
                        onClick = onDismiss,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "Back to Home",
                            fontSize = 15.sp,
                            color = Color.White.copy(alpha = 0.7f)
                        )
                    }
                }
            }
        }
    }
}
