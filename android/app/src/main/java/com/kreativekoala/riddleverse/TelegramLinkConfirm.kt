package com.kreativekoala.riddleverse

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.stringResource
import com.kreativekoala.riddleverse.ui.theme.RvGrape
import com.kreativekoala.riddleverse.ui.theme.RvViolet

/** Asks before a Telegram /link code is redeemed (the link may have been sent by someone else). */
@Composable
fun TelegramLinkConfirm(onConfirm: () -> Unit, onCancel: () -> Unit) {
    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(RvGrape, RvViolet)))) {
        AlertDialog(
            onDismissRequest = onCancel,
            title = { Text(stringResource(R.string.tg_link_confirm_title)) },
            text = { Text(stringResource(R.string.tg_link_confirm_body)) },
            confirmButton = { TextButton(onClick = onConfirm) { Text(stringResource(R.string.tg_link_confirm_yes)) } },
            dismissButton = { TextButton(onClick = onCancel) { Text(stringResource(R.string.tg_link_confirm_no)) } },
        )
    }
}
