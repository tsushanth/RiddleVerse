package com.kreativekoala.riddleverse

import com.kreativekoala.riddleverse.ui.theme.*
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private const val TELEGRAM_PREFS = "telegram_bot_banner"
private const val TELEGRAM_DISMISSED_KEY = "banner_dismissed"
private const val TELEGRAM_BOT_URL = "https://t.me/Riddleverse_bot"

@Composable
fun TelegramBotBanner() {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences(TELEGRAM_PREFS, Context.MODE_PRIVATE) }
    var dismissed by remember { mutableStateOf(prefs.getBoolean(TELEGRAM_DISMISSED_KEY, false)) }

    AnimatedVisibility(
        visible = !dismissed,
        enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
        exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut()
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(
                    Brush.horizontalGradient(
                        listOf(Color(0xFF0088CC), Color(0xFF00AAEE))
                    )
                )
                .clickable {
                    context.startActivity(
                        Intent(Intent.ACTION_VIEW, Uri.parse(TELEGRAM_BOT_URL))
                    )
                }
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("✈️", fontSize = 26.sp)

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Play RiddleVerse on Telegram!",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = RvOnTone
                    )
                    Text(
                        text = "Quiz your group chat • @Riddleverse_bot",
                        fontSize = 12.sp,
                        color = RvOnTone.copy(alpha = 0.9f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                IconButton(
                    onClick = {
                        dismissed = true
                        prefs.edit().putBoolean(TELEGRAM_DISMISSED_KEY, true).apply()
                    },
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Dismiss",
                        tint = RvOnTone.copy(alpha = 0.9f),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}
