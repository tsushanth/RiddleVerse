package com.kreativekoala.riddleverse

import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// --- spatial puzzles (feat/puzzles-spatial) --- shared chrome for the spatial puzzle screens.

/** Everything a spatial game screen needs from the normal puzzle flow. */
class SpatialArgs(
    val seed: Long,
    val difficulty: String,
    val level: String,
    val onFinished: (success: Boolean, score: Int) -> Unit,
    val onBack: () -> Unit
)

/** Distinct hues for liquids / pieces. Names double as accessibility labels. */
val SpatialPalette = listOf(
    Color(0xFFE53935), Color(0xFF1E88E5), Color(0xFF43A047), Color(0xFFFDD835),
    Color(0xFF8E24AA), Color(0xFFFB8C00), Color(0xFF00ACC1), Color(0xFFEC407A),
    Color(0xFF6D4C41), Color(0xFF7CB342), Color(0xFF5C6BC0), Color(0xFF90A4AE)
)
val SpatialColorNames = listOf(
    "red", "blue", "green", "yellow", "purple", "orange", "teal", "pink", "brown", "lime", "indigo", "grey"
)

@Composable
fun SpatialLoading(label: String = "Preparing puzzle") {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator()
            Spacer(Modifier.height(12.dp))
            Text(label, color = MaterialTheme.colorScheme.onBackground)
        }
    }
}

@Composable
fun SpatialGameShell(
    title: String,
    args: SpatialArgs,
    status: String,
    controls: @Composable RowScope.() -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    BoxWithConstraints(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
    val sideBySide = com.kreativekoala.riddleverse.ui.rvSideBySide(maxWidth.value, maxHeight.value)
    val tooShort = com.kreativekoala.riddleverse.ui.rvIsShort(maxHeight.value)

    val headerRow: @Composable () -> Unit = {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = args.onBack, modifier = Modifier.size(48.dp)) {
                Icon(Icons.Default.ArrowBack, contentDescription = stringResource(R.string.back))
            }
            Column(Modifier.weight(1f)) {
                Text(title, fontSize = 18.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                Text(
                    "${args.difficulty}  |  ${args.level}",
                    fontSize = 12.sp, maxLines = 1,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                status, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(end = 12.dp).semantics { contentDescription = status },
                maxLines = 1
            )
        }
    }

    if (sideBySide) {
        // Landscape phone: the board gets the whole height on the left (drags/swipes are the game's input, so it must
        // never sit in a scrolling page); header and controls live in a scrolling pane on the right.
        Row(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            Column(Modifier.weight(1f).fillMaxHeight(), content = content)
            Column(
                Modifier.width(com.kreativekoala.riddleverse.ui.RV_SIDE_PANE_WIDTH).fillMaxHeight().verticalScroll(androidx.compose.foundation.rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                headerRow()
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically,
                    content = controls
                )
            }
        }
    } else {
    Column(Modifier.fillMaxSize().then(if (tooShort) Modifier.verticalScroll(androidx.compose.foundation.rememberScrollState()) else Modifier)) {
    Column(
        Modifier
            .then(if (tooShort) Modifier.height(com.kreativekoala.riddleverse.ui.RV_MIN_PAGE_HEIGHT) else Modifier.fillMaxSize())
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        headerRow()
        Column(Modifier.weight(1f).fillMaxWidth(), content = content)
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
            content = controls
        )
    }
    }
    }
    }
}

/** Icon + label action button, >= 48dp touch target. */
@Composable
fun SpatialAction(
    icon: ImageVector,
    label: String,
    enabled: Boolean = true,
    selected: Boolean = false,
    onClick: () -> Unit
) {
    val tint = when {
        !enabled -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
        selected -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.onSurface
    }
    TextButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.heightIn(min = 56.dp).widthIn(min = 56.dp),
        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 4.dp),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, contentDescription = null, tint = tint)
            Text(label, fontSize = 11.sp, color = tint, maxLines = 1, textAlign = TextAlign.Center)
        }
    }
}

@Composable
fun SpatialResultDialog(
    success: Boolean,
    title: String,
    detail: String,
    stars: Int = 0,
    primaryLabel: String,
    onPrimary: () -> Unit,
    secondaryLabel: String? = null,
    onSecondary: () -> Unit = {}
) {
    AlertDialog(
        // Back = the primary button; tapping outside is ignored.
        onDismissRequest = onPrimary,
        properties = androidx.compose.ui.window.DialogProperties(dismissOnBackPress = true, dismissOnClickOutside = false),
        title = {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                if (success && stars > 0) {
                    Text(
                        "★".repeat(stars) + "☆".repeat((3 - stars).coerceAtLeast(0)),
                        fontSize = 28.sp, color = Color(0xFFFFB020),
                        modifier = Modifier.semantics { contentDescription = "$stars of 3 stars" }
                    )
                }
                Text(title, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
            }
        },
        text = { Text(detail, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()) },
        confirmButton = {
            Button(onClick = onPrimary, modifier = Modifier.heightIn(min = 48.dp)) { Text(primaryLabel) }
        },
        dismissButton = secondaryLabel?.let {
            { TextButton(onClick = onSecondary, modifier = Modifier.heightIn(min = 48.dp)) { Text(it) } }
        }
    )
}
