package com.kreativekoala.riddleverse

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private const val PREFS_NAME = "web_promo_banner"
private const val KEY_DISMISSED = "banner_dismissed"

@Composable
fun WebPromoBanner() {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE) }
    var dismissed by remember { mutableStateOf(prefs.getBoolean(KEY_DISMISSED, false)) }

    AnimatedVisibility(
        visible = !dismissed,
        enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
        exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF7C4DFF).copy(alpha = 0.15f))
                .clickable {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://riddleverse.com"))
                    context.startActivity(intent)
                }
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Also available on iOS & Web \u2192 riddleverse.com",
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = Color(0xFFB388FF),
                modifier = Modifier.weight(1f)
            )

            IconButton(
                onClick = {
                    dismissed = true
                    prefs.edit().putBoolean(KEY_DISMISSED, true).apply()
                },
                modifier = Modifier.size(20.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Dismiss",
                    tint = Color.Gray,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}
