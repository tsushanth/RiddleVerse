package com.kreativekoala.riddleverse

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Icon
import androidx.compose.material.LocalContentColor
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun BadgedIcon(
    icon: ImageVector,
    contentDescription: String? = null,
    showBadge: Boolean = true,
    badgeType: BadgeType = BadgeType.NEW,
    modifier: Modifier = Modifier,
    tint: Color = LocalContentColor.current
) {
    Box(modifier = modifier) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = tint
        )

        if (showBadge) {
            when (badgeType) {
                BadgeType.NEW -> {
                    // "NEW" badge
                    Card(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .offset(x = 8.dp, y = (-8).dp),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = "NEW",
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold,
                            color = androidx.compose.ui.graphics.Color.White,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                        )
                    }
                }
                BadgeType.BULB -> {
                    // Light bulb indicator
                    Icon(
                        imageVector = Icons.Default.Lightbulb,
                        contentDescription = "New Feature",
                        tint = Color(0xFFFFD700), // Gold color
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .offset(x = 6.dp, y = (-6).dp)
                            .size(16.dp)
                            .background(
                                androidx.compose.ui.graphics.Color.White,
                                CircleShape
                            )
                            .padding(2.dp)
                    )
                }
                BadgeType.THOUGHT_BUBBLE -> {
                    // Thought bubble using chat icon
                    Icon(
                        imageVector = Icons.Default.ChatBubble,
                        contentDescription = "New Feature",
                        tint = Color(0xFF4CAF50),
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .offset(x = 6.dp, y = (-6).dp)
                            .size(16.dp)
                            .background(
                                androidx.compose.ui.graphics.Color.White,
                                CircleShape
                            )
                            .padding(2.dp)
                    )
                }
                BadgeType.DOT -> {
                    // Simple red dot
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .offset(x = 4.dp, y = (-4).dp)
                            .size(8.dp)
                            .background(Color(0xFFFF4444), CircleShape)
                    )
                }
            }
        }
    }
}

enum class BadgeType {
    NEW, BULB, THOUGHT_BUBBLE, DOT
}