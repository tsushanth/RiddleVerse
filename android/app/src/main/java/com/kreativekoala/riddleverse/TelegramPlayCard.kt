package com.kreativekoala.riddleverse

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kreativekoala.riddleverse.ui.theme.RvInk
import com.kreativekoala.riddleverse.ui.theme.RvInkSoft
import com.kreativekoala.riddleverse.ui.theme.RvOnTone
import com.kreativekoala.riddleverse.ui.theme.RvOutline
import com.kreativekoala.riddleverse.ui.theme.RvSurfaceRaised

/** Which Telegram promo card: after any finished puzzle, or after the daily puzzle (daily reminder in the bot). */
enum class TelegramCardVariant { RESULT, DAILY }

/**
 * "Play with friends on Telegram" card for result screens. [puzzleType] opens that game in the chat (/start g_<type>);
 * the daily variant opens the bot's daily puzzle. Hidden for [TelegramPromo.QUIET_AFTER_OPEN_DAYS] days once the player
 * has opened the bot from any promo.
 */
@Composable
fun TelegramPlayCard(variant: TelegramCardVariant, puzzleType: String? = null, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val visible = remember { TelegramPromo.isCardVisible(context) }
    if (!visible) return
    val start = when (variant) {
        TelegramCardVariant.DAILY -> "daily"
        TelegramCardVariant.RESULT -> puzzleType?.takeIf { Regex("^[a-z]{2,24}$").matches(it) }?.let { "g_$it" }
    }
    val surface = if (variant == TelegramCardVariant.DAILY) "daily_card" else "result_card"
    Card(
        modifier = modifier.fillMaxWidth().clickable { TelegramPromo.open(context, start, surface) },
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = RvSurfaceRaised),
        border = BorderStroke(2.dp, RvOutline),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("✈", fontSize = 22.sp)
                Text(
                    stringResource(if (variant == TelegramCardVariant.DAILY) R.string.tg_daily_title else R.string.tg_result_title),
                    fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, color = RvInk,
                )
            }
            Text(
                stringResource(if (variant == TelegramCardVariant.DAILY) R.string.tg_daily_body else R.string.tg_result_body),
                fontSize = 13.sp, color = RvInkSoft,
            )
            Text(
                stringResource(if (variant == TelegramCardVariant.DAILY) R.string.tg_daily_cta else R.string.tg_result_cta),
                modifier = Modifier.clip(RoundedCornerShape(50)).background(Color(0xFF0088CC)).padding(horizontal = 16.dp, vertical = 8.dp),
                fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, color = RvOnTone,
            )
        }
    }
}
