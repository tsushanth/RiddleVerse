package com.kreativekoala.riddleverse

import androidx.compose.foundation.verticalScroll
import com.kreativekoala.riddleverse.ui.theme.*
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog

// The live bot is @Riddleverse_bot (same handle as TelegramBotBanner). There is no WhatsApp bot, so no WhatsApp button.
private const val TELEGRAM_BOT_USERNAME = "Riddleverse_bot"

/**
 * Bottom-sheet-style dialog shown when a free user hits their daily generation limit.
 * Offers the Telegram bot as an alternative path to continue.
 */
@Composable
fun BotUpsellSheet(
    onDismiss: () -> Unit
) {
    val context = LocalContext.current

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp, bottomStart = 16.dp, bottomEnd = 16.dp),
            colors = CardDefaults.cardColors(containerColor = RvCanvas)
        ) {
            Column(
                modifier = Modifier.verticalScroll(androidx.compose.foundation.rememberScrollState()).padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Close button row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    IconButton(onClick = onDismiss, modifier = Modifier.size(24.dp)) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = stringResource(R.string.close),
                            tint = RvInkSoft,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = stringResource(R.string.browse_bot_upsell_title),
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = RvInk,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = stringResource(R.string.browse_bot_upsell_body),
                    fontSize = 14.sp,
                    color = RvInkSoft,
                    textAlign = TextAlign.Center,
                    lineHeight = 20.sp
                )

                Spacer(modifier = Modifier.height(24.dp))

                // Telegram button
                Button(
                    onClick = {
                        openTelegramBot(context)
                        onDismiss()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0088CC)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Telegram", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = stringResource(R.string.browse_bot_upsell_upgrade_hint),
                    fontSize = 12.sp,
                    color = RvInkSoft,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

private fun openTelegramBot(context: Context) {
    try {
        // Try native Telegram deep link first
        val telegramIntent = Intent(Intent.ACTION_VIEW, Uri.parse("tg://resolve?domain=$TELEGRAM_BOT_USERNAME"))
        telegramIntent.setPackage("org.telegram.messenger")
        context.startActivity(telegramIntent)
    } catch (e: Exception) {
        try {
            // Fallback to web URL
            val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://t.me/$TELEGRAM_BOT_USERNAME"))
            context.startActivity(webIntent)
        } catch (e2: Exception) {
            Toast.makeText(context, context.getString(R.string.browse_bot_telegram_open_failed, TELEGRAM_BOT_USERNAME), Toast.LENGTH_LONG).show()
        }
    }
}
