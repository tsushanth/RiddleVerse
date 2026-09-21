package com.kreativekoala.riddleverse.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Solid flat fill with a darker "lip" underneath, giving a pressable, toy-like look. */
fun Modifier.rvChunky(tone: RvTone, corner: Dp = 20.dp, lip: Dp = 4.dp): Modifier =
    this
        .background(tone.edge, RoundedCornerShape(corner))
        .padding(bottom = lip)
        .background(tone.fill, RoundedCornerShape(corner))

/** Small pill label (status, streak, level). */
@Composable
fun RvPill(text: String, tone: RvTone, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .background(tone.fill, RoundedCornerShape(50))
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Text(text = text, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold)
    }
}

/** Chunky primary button. Content lambda receives a centered Row. */
@Composable
fun RvChunkyButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tone: RvTone = RvToneViolet,
    content: @Composable RowScope.() -> Unit
) {
    Row(
        modifier = modifier
            .rvChunky(tone, corner = 18.dp)
            .clickable(onClick = onClick)
            .padding(PaddingValues(horizontal = 20.dp, vertical = 14.dp)),
        verticalAlignment = Alignment.CenterVertically,
        content = content
    )
}
