package com.kreativekoala.riddleverse

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.kreativekoala.riddleverse.ui.theme.RiddleVerseTheme
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Color
import androidx.compose.material.icons.Icons
import androidx.compose.material3.TopAppBar
import androidx.compose.foundation.Image
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalContext
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.ui.res.stringResource


class LeaderboardActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            RiddleVerseTheme {
                LeaderboardScreen()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LeaderboardScreen() {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var topUsers by remember { mutableStateOf<List<User>>(emptyList()) }
    var allUsers by remember { mutableStateOf<List<User>>(emptyList()) }

    // Fetch leaderboard on launch
    LaunchedEffect(Unit) {
        coroutineScope.launch(Dispatchers.IO) {
            try {
                val request = Request.Builder()
                    .url("https://puzzleverseai.com/leaderboard")
                    .build()

                val client = OkHttpClient()
                val response = client.newCall(request).execute()
                val responseBody = response.body?.string()

                if (!response.isSuccessful || responseBody.isNullOrEmpty()) {
                    Log.e("Leaderboard", "❌ Failed to fetch leaderboard: ${response.code}")
                    return@launch
                }

                val root = JSONObject(responseBody)
                if (!root.optBoolean("success", false)) {
                    Log.e("Leaderboard", "❌ Backend returned unsuccessful response")
                    return@launch
                }

                val leaderboardArray = root.optJSONArray("leaderboard") ?: JSONArray()
                val fetchedUsers = mutableListOf<User>()

                val currentUserEmail = FirebaseAuth.getInstance().currentUser?.email
                for (i in 0 until leaderboardArray.length()) {
                    val obj = leaderboardArray.getJSONObject(i)
                    // Use masked name from backend for privacy, compare userId for highlighting current user
                    val userId = obj.optString("userId", "")
                    fetchedUsers.add(
                        User(
                            name = obj.optString("name", "Unknown"),
                            score = "${obj.optInt("score", 0)} pts",
                            isHighlighted = userId == currentUserEmail,
                            avatarNumber = (1..9).random()
                        )
                    )
                }

                val top = fetchedUsers.take(3)
                val rest = fetchedUsers.drop(3)

                withContext(Dispatchers.Main) {
                    topUsers = top
                    allUsers = rest
                }

            } catch (e: Exception) {
                Log.e("Leaderboard", "❌ Exception: ${e.localizedMessage}", e)
            }
        }
    }


    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
            .systemBarsPadding()
            .padding(horizontal = 16.dp)
    ) {
        TopAppBar(
            title = { Text(stringResource(R.string.leaderboard)) },
            navigationIcon = {
                IconButton(onClick = {
                    val intent = Intent(context, HomeActivity::class.java)
                    intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                    context.startActivity(intent)
                }) {
                    Icon(Icons.Default.ArrowBack, contentDescription = stringResource(R.string.back))
                }
            }
        )

        // Show loading state if topUsers is empty
        if (topUsers.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp) // Optional spacing between avatars
            ) {
                val fallbackUser = @androidx.compose.runtime.Composable { User(name = "", score = "0 pts", avatarNumber = (1..9).random()) }
                listOf(
                    topUsers.getOrNull(1) ?: fallbackUser(),
                    topUsers.getOrNull(0) ?: fallbackUser(),
                    topUsers.getOrNull(2) ?: fallbackUser()
                ).forEachIndexed { index, user ->
                    Box(
                        modifier = Modifier
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        LeaderboardAvatar(
                            user = user,
                            rank = (index + 1).toString(),
                            crown = index == 1 // Crown for the center (rank 1)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Surface(
                shape = RoundedCornerShape(30.dp),
                color = Color(0xFF8A4DFF),
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState())
                ) {
                    allUsers.forEachIndexed { index, user ->
                        LeaderboardListItem(user, index + 4)
                    }
                }
            }
        }
    }
}

@Composable
fun getAvatarPainter(avatarNumber: Int): Painter {
    val context = LocalContext.current
    val resId = context.resources.getIdentifier("avatar_$avatarNumber", "drawable", context.packageName)
    return painterResource(id = resId)
}

data class User(
    val name: String,
    val score: String,
    val avatarNumber: Int = (1..9).random(),
    val isHighlighted: Boolean = false
)

@Composable
fun LeaderboardAvatar(user: User, rank: String, crown: Boolean = false) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(contentAlignment = Alignment.TopCenter) {
            /*if (crown) {
                Icon(
                    painter = painterResource(R.drawable.ic_crown),
                    contentDescription = null,
                    tint = Color(0xFFFF7043),
                    modifier = Modifier.size(32.dp)
                )
            }*/

            Image(
                painter = getAvatarPainter(user.avatarNumber),
                contentDescription = null,
                modifier = Modifier
                    .size(72.dp)
                    .clip(CircleShape)
                    .border(2.dp, Color(0xFFFF7043), CircleShape)
            )
        }
        Text(user.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(user.score, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun LeaderboardListItem(user: User, rank: Int) {
    val bgColor = if (user.isHighlighted) Color(0xFFFFA726) else Color.White

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(bgColor)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("$rank", fontWeight = FontWeight.Bold, modifier = Modifier.width(24.dp))
        Image(
            painter = painterResource( R.drawable.avatar_2),
            contentDescription = null,
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(user.name, modifier = Modifier.weight(1f))
        Text(user.score, fontWeight = FontWeight.Bold)
    }
}
