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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Home banner for the Telegram bot. Dismissing hides it for [TelegramPromo.BANNER_RESHOW_DAYS] days (then it returns once more
 * per cycle); opening the bot hides it for [TelegramPromo.QUIET_AFTER_OPEN_DAYS] days. Text is localized (home_telegram_*).
 */
@Composable
fun TelegramBotBanner() {
    val context = LocalContext.current
    var dismissed by remember { mutableStateOf(!TelegramPromo.isBannerVisible(context)) }

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
                    TelegramPromo.open(context, null, "banner")
                    dismissed = true
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
                        text = stringResource(R.string.home_telegram_title),
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = RvOnTone
                    )
                    Text(
                        text = stringResource(R.string.home_telegram_subtitle),
                        fontSize = 12.sp,
                        color = RvOnTone.copy(alpha = 0.9f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                IconButton(
                    onClick = {
                        dismissed = true
                        TelegramPromo.dismissBanner(context)
                    },
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = stringResource(R.string.welcome_back_dismiss),
                        tint = RvOnTone.copy(alpha = 0.9f),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}
