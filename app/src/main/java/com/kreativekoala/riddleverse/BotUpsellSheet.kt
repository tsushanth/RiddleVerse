package com.kreativekoala.riddleverse

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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog

// TODO: Replace with actual bot username and phone number
private const val TELEGRAM_BOT_USERNAME = "RiddleVerseBot" // TODO: actual bot username
private const val WHATSAPP_BOT_NUMBER = "1234567890"       // TODO: actual WhatsApp number

/**
 * Bottom-sheet-style dialog shown when a free user hits their daily generation limit.
 * Offers Telegram and WhatsApp bot links as an alternative path to continue.
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
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A2E))
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
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
                            contentDescription = "Close",
                            tint = Color.Gray,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Want more puzzles?",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Continue with our bot for free puzzles beyond your daily limit!",
                    fontSize = 14.sp,
                    color = Color.Gray,
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
                        .height(48.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0088CC)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Telegram", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                }

                Spacer(modifier = Modifier.height(12.dp))

                // WhatsApp button
                Button(
                    onClick = {
                        openWhatsAppBot(context)
                        onDismiss()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF25D366)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("WhatsApp", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "Or upgrade for unlimited in-app access",
                    fontSize = 12.sp,
                    color = Color.Gray,
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
            Toast.makeText(context, "Could not open Telegram. Search for @$TELEGRAM_BOT_USERNAME", Toast.LENGTH_LONG).show()
        }
    }
}

private fun openWhatsAppBot(context: Context) {
    try {
        // TODO: Replace with actual WhatsApp bot number
        val whatsappIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/$WHATSAPP_BOT_NUMBER"))
        context.startActivity(whatsappIntent)
    } catch (e: Exception) {
        Toast.makeText(context, "Could not open WhatsApp", Toast.LENGTH_LONG).show()
    }
}
