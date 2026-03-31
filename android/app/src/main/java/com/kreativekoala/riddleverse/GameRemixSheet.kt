package com.kreativekoala.riddleverse

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import android.util.Log
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.util.zip.ZipInputStream

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GameRemixSheet(
    puzzleType: String,
    difficulty: String,
    onDismiss: () -> Unit,
    genViewModel: GameGenerationViewModel? = null
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val displayName = puzzleType
        .replace("_", " ")
        .split(" ")
        .joinToString(" ") { it.replaceFirstChar { c -> c.uppercase() } }

    val currentUserName = FirebaseAuth.getInstance().currentUser?.displayName
        ?: FirebaseAuth.getInstance().currentUser?.email?.substringBefore("@")
        ?: "Player"
    var gameTitle by remember { mutableStateOf("$displayName Custom $currentUserName") }
    var remixDescription by remember { mutableStateOf("") }
    var showGamePlay by remember { mutableStateOf(false) }
    var isListening by remember { mutableStateOf(false) }
    var selectedImageBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var selectedImageBase64 by remember { mutableStateOf<String?>(null) }
    var showRateLimitDialog by remember { mutableStateOf(false) }
    var showCoinStore by remember { mutableStateOf(false) }
    var showSubscriptionDialog by remember { mutableStateOf(false) }
    val coinManager = remember { CoinManager.shared }

    val speechHelper = remember { SpeechRecognizerHelper(context) }

    // Use the ViewModel for generation state (persists across navigation)
    val vm = genViewModel ?: remember { GameGenerationViewModel() }

    // Mic permission launcher
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            speechHelper.startListening(
                onResult = { text -> remixDescription = text },
                onError = { err -> Log.e("GameRemix", "Speech error: $err") },
                onListening = { listening -> isListening = listening }
            )
        }
    }

    // Image picker launcher
    val imagePickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            coroutineScope.launch(Dispatchers.IO) {
                try {
                    val inputStream = context.contentResolver.openInputStream(uri)
                    val original = BitmapFactory.decodeStream(inputStream)
                    inputStream?.close()
                    if (original != null) {
                        val maxDim = 1024
                        val scale = if (original.width > original.height) {
                            maxDim.toFloat() / original.width
                        } else {
                            maxDim.toFloat() / original.height
                        }.coerceAtMost(1f)
                        val resized = if (scale < 1f) {
                            Bitmap.createScaledBitmap(
                                original,
                                (original.width * scale).toInt(),
                                (original.height * scale).toInt(),
                                true
                            )
                        } else original
                        val baos = ByteArrayOutputStream()
                        resized.compress(Bitmap.CompressFormat.JPEG, 70, baos)
                        val base64 = Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP)
                        withContext(Dispatchers.Main) {
                            selectedImageBitmap = resized
                            selectedImageBase64 = base64
                        }
                    }
                } catch (e: Exception) {
                    Log.e("GameRemix", "Failed to process image", e)
                }
            }
        }
    }

    // Quality level computation
    val descLen = remixDescription.trim().length
    val qualityLevel = when {
        descLen == 0 -> 0
        descLen < 30 -> 0
        descLen < 80 -> 1
        descLen < 150 -> 2
        else -> 3
    }
    val qualityColor = when (qualityLevel) {
        0 -> if (remixDescription.isEmpty()) Color.White.copy(alpha = 0.3f) else Color.Red
        1 -> Color(0xFFFF9800)
        2 -> Color.Yellow
        else -> Color(0xFF4CAF50)
    }
    val qualityLabel = when (qualityLevel) {
        0 -> if (remixDescription.isEmpty()) "0/500" else "Too short"
        1 -> "Basic"
        2 -> "Good"
        else -> "Great detail!"
    }
    val qualityProgress = (descLen.toFloat() / 150f).coerceAtMost(1f)

    val canSubmit = remixDescription.trim().isNotEmpty()
            && remixDescription.length <= 500
            && !vm.isGenerating

    BackHandler {
        if (showGamePlay) {
            showGamePlay = false
            onDismiss()
        } else if (!vm.isGenerating) {
            onDismiss()
        }
    }

    // Track that we're on the remix sheet
    DisposableEffect(Unit) {
        vm.isOnRemixSheet = true
        onDispose {
            vm.isOnRemixSheet = false
            speechHelper.destroy()
        }
    }

    // Watch for generation completion while on sheet
    LaunchedEffect(vm.showPreview) {
        if (vm.showPreview && vm.isRemixGeneration) {
            vm.showPreview = false
            val gameId = vm.remixResultGameId ?: ""
            coinManager.spendForRemix(gameId = gameId, creatorId = null) { }
            showGamePlay = true
        }
    }

    // Watch for rate limit upsell
    LaunchedEffect(vm.showRateLimitUpsell) {
        if (vm.showRateLimitUpsell && vm.isRemixGeneration) {
            vm.showRateLimitUpsell = false
            showRateLimitDialog = true
        }
    }

    fun submitRemix(useCoins: Boolean = false) {
        val desc = remixDescription.trim()
        if (desc.isEmpty()) return

        val effectiveTitle = gameTitle.trim().ifEmpty { "My $displayName Game" }

        vm.isOnRemixSheet = true
        vm.startRemixGeneration(
            puzzleType = puzzleType,
            difficulty = difficulty,
            remixDescription = desc,
            title = effectiveTitle,
            context = context,
            imageBase64 = selectedImageBase64,
            useCoins = useCoins
        )
    }

    // If showing game play, render WebView full screen
    if (showGamePlay && vm.bundleDirPath != null) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF1A1A2E))
        ) {
            AndroidView(
                factory = { ctx ->
                    WebView(ctx).apply {
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.allowFileAccess = true
                        settings.loadWithOverviewMode = true
                        settings.useWideViewPort = true
                        webViewClient = WebViewClient()
                        loadUrl("file://${vm.bundleDirPath}/index.html")
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
            IconButton(
                onClick = { showGamePlay = false; onDismiss() },
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(top = 48.dp, start = 16.dp)
                    .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(50))
            ) {
                Icon(
                    imageVector = Icons.Default.ArrowBack,
                    contentDescription = stringResource(R.string.back),
                    tint = Color.White
                )
            }
        }
        return
    }

    // Remix input form
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF1A1A2E))
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Top bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { if (!vm.isGenerating) onDismiss() }) {
                    Icon(Icons.Default.Close, contentDescription = stringResource(R.string.close), tint = Color.White)
                }
                Spacer(modifier = Modifier.weight(1f))
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Remix: $displayName", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                    Text(stringResource(R.string.create_your_version), fontSize = 11.sp, color = Color.White.copy(alpha = 0.5f))
                }
                Spacer(modifier = Modifier.weight(1f))
                Spacer(modifier = Modifier.size(48.dp))
            }

            HorizontalDivider(color = Color.White.copy(alpha = 0.1f))

            // Scrollable content
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                // Marketing card
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF4CAF50).copy(alpha = 0.1f)),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Icon(Icons.Default.Star, null, tint = Color(0xFF4CAF50), modifier = Modifier.size(36.dp))
                        Column {
                            Text("Create & Earn Real Money", fontWeight = FontWeight.Bold, color = Color.White)
                            Text("Earn coins when others play — cash out via Stripe!", fontSize = 12.sp, color = Color.White.copy(alpha = 0.6f))
                            Text("You earn 55% of every coin spent on your game", fontSize = 10.sp, color = Color(0xFF4CAF50).copy(alpha = 0.8f))
                        }
                    }
                }

                // Title field
                Column {
                    Text(stringResource(R.string.game_title), fontSize = 13.sp, fontWeight = FontWeight.Medium, color = Color.White.copy(alpha = 0.5f))
                    Spacer(modifier = Modifier.height(6.dp))
                    OutlinedTextField(
                        value = gameTitle, onValueChange = { gameTitle = it },
                        modifier = Modifier.fillMaxWidth(), singleLine = true,
                        placeholder = { Text("My $displayName Game", color = Color.White.copy(alpha = 0.25f)) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFF9C27B0), unfocusedBorderColor = Color.White.copy(alpha = 0.2f),
                            focusedContainerColor = Color.White.copy(alpha = 0.05f), unfocusedContainerColor = Color.White.copy(alpha = 0.05f),
                            focusedTextColor = Color.White, unfocusedTextColor = Color.White, cursorColor = Color(0xFF9C27B0)
                        ),
                        shape = RoundedCornerShape(12.dp)
                    )
                }

                // Description field
                Column {
                    Text(stringResource(R.string.describe_your_version), fontSize = 13.sp, fontWeight = FontWeight.Medium, color = Color.White.copy(alpha = 0.5f))
                    Spacer(modifier = Modifier.height(6.dp))
                    OutlinedTextField(
                        value = remixDescription,
                        onValueChange = { if (it.length <= 500) remixDescription = it },
                        modifier = Modifier.fillMaxWidth().height(120.dp),
                        placeholder = {
                            Text("Describe gameplay changes, visual style, theme, difficulty tweaks, scoring rules...",
                                color = Color.White.copy(alpha = 0.25f))
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFF9C27B0), unfocusedBorderColor = Color.White.copy(alpha = 0.2f),
                            focusedContainerColor = Color.White.copy(alpha = 0.05f), unfocusedContainerColor = Color.White.copy(alpha = 0.05f),
                            focusedTextColor = Color.White, unfocusedTextColor = Color.White, cursorColor = Color(0xFF9C27B0)
                        ),
                        shape = RoundedCornerShape(12.dp)
                    )

                    // Voice + Photo + Quality row
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            // Voice button
                            Surface(
                                modifier = Modifier.clickable {
                                    if (isListening) {
                                        speechHelper.stopListening()
                                        isListening = false
                                    } else {
                                        val hasPerm = ContextCompat.checkSelfPermission(
                                            context, Manifest.permission.RECORD_AUDIO
                                        ) == PackageManager.PERMISSION_GRANTED
                                        if (hasPerm) {
                                            speechHelper.startListening(
                                                onResult = { text -> remixDescription = text },
                                                onError = { err -> Log.e("GameRemix", "Speech error: $err") },
                                                onListening = { listening -> isListening = listening }
                                            )
                                        } else {
                                            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                        }
                                    }
                                },
                                shape = CircleShape,
                                color = if (isListening) Color.Red.copy(alpha = 0.2f) else Color.White.copy(alpha = 0.08f)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = if (isListening) Icons.Default.Mic else Icons.Default.MicOff,
                                        contentDescription = "Voice",
                                        tint = if (isListening) Color.Red else Color(0xFFFF8C00),
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        if (isListening) "Listening..." else "Voice",
                                        fontSize = 12.sp, color = Color.White.copy(alpha = 0.7f)
                                    )
                                }
                            }

                            // Photo button
                            Surface(
                                modifier = Modifier.clickable { imagePickerLauncher.launch("image/*") },
                                shape = CircleShape,
                                color = if (selectedImageBase64 != null) Color(0xFFFF8C00).copy(alpha = 0.2f) else Color.White.copy(alpha = 0.08f)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.Image, "Attach image", tint = Color(0xFFFF8C00), modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        if (selectedImageBase64 != null) "Image added" else "Reference",
                                        fontSize = 12.sp, color = Color.White.copy(alpha = 0.7f)
                                    )
                                }
                            }
                        }

                        // Quality indicator
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(modifier = Modifier.size(8.dp).background(qualityColor, CircleShape))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(qualityLabel, fontSize = 11.sp, color = qualityColor)
                        }
                    }

                    // Quality progress bar
                    if (remixDescription.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Box(modifier = Modifier.fillMaxWidth().height(3.dp).background(Color.White.copy(alpha = 0.1f), RoundedCornerShape(2.dp))) {
                            Box(modifier = Modifier.fillMaxWidth(qualityProgress).height(3.dp).background(qualityColor, RoundedCornerShape(2.dp)))
                        }
                        if (qualityLevel < 2) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                when (qualityLevel) {
                                    0 -> "Tip: Describe gameplay mechanics, visual style, and rules for best results"
                                    else -> "Add more detail — describe controls, scoring, difficulty, and visual style"
                                },
                                fontSize = 11.sp, color = Color.White.copy(alpha = 0.4f)
                            )
                        }
                    }
                }

                // Image preview
                selectedImageBitmap?.let { bitmap ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color.White.copy(alpha = 0.06f), RoundedCornerShape(12.dp))
                            .padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Image(
                            bitmap = bitmap.asImageBitmap(),
                            contentDescription = "Reference",
                            modifier = Modifier.size(56.dp).clip(RoundedCornerShape(8.dp)),
                            contentScale = ContentScale.Crop
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(stringResource(R.string.reference_image), fontSize = 12.sp, fontWeight = FontWeight.Medium, color = Color.White.copy(alpha = 0.8f))
                            Text("AI will use this as visual context", fontSize = 11.sp, color = Color.White.copy(alpha = 0.4f))
                        }
                        IconButton(onClick = { selectedImageBitmap = null; selectedImageBase64 = null }) {
                            Icon(Icons.Default.Close, "Remove", tint = Color.White.copy(alpha = 0.5f), modifier = Modifier.size(16.dp))
                        }
                    }
                }

                // Quick Ideas
                Column {
                    Text("Quick Ideas", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = Color.White.copy(alpha = 0.5f))
                    Spacer(modifier = Modifier.height(8.dp))
                    val suggestions = listOf("Make it harder", "Space theme", "Add a timer", "More questions", "Neon colors", "Kids friendly")
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        suggestions.take(3).forEach { s ->
                            RemixChip(s) { remixDescription = if (remixDescription.isEmpty()) s else "$remixDescription, ${s.lowercase()}" }
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        suggestions.drop(3).forEach { s ->
                            RemixChip(s) { remixDescription = if (remixDescription.isEmpty()) s else "$remixDescription, ${s.lowercase()}" }
                        }
                    }
                }

                // Error message
                vm.errorMessage?.let { error ->
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color.Red.copy(alpha = 0.15f)),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth().clickable { vm.errorMessage = null }
                    ) {
                        Row(modifier = Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Icon(Icons.Default.Warning, null, tint = Color.Red, modifier = Modifier.size(16.dp))
                            Text(error, fontSize = 12.sp, color = Color.White.copy(alpha = 0.8f))
                        }
                    }
                }
            }

            // Bottom create button
            HorizontalDivider(color = Color.White.copy(alpha = 0.1f))
            Button(
                onClick = {
                    if (!coinManager.canAffordRemix) {
                        showCoinStore = true
                    } else {
                        submitRemix()
                    }
                },
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                enabled = canSubmit, shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent, disabledContainerColor = Color.Transparent),
                contentPadding = PaddingValues(0.dp)
            ) {
                Box(
                    modifier = Modifier.fillMaxWidth().background(
                        if (canSubmit) Brush.horizontalGradient(listOf(Color(0xFF9C27B0), Color(0xFF2196F3)))
                        else Brush.horizontalGradient(listOf(Color.Gray.copy(alpha = 0.3f), Color.Gray.copy(alpha = 0.3f))),
                        RoundedCornerShape(14.dp)
                    ).padding(vertical = 16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.AutoFixHigh, null, tint = Color.White)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.create_game), fontWeight = FontWeight.Bold, color = Color.White)
                    }
                }
            }
        }

        // Generation progress overlay
        if (vm.isGenerating && vm.isRemixGeneration) {
            Box(
                modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.85f)).clickable(enabled = false) {},
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(24.dp),
                    modifier = Modifier.padding(40.dp)
                ) {
                    Spacer(modifier = Modifier.weight(1f))

                    Icon(Icons.Default.AutoFixHigh, null, tint = Color(0xFFFF9800), modifier = Modifier.size(48.dp))
                    Text(stringResource(R.string.creating_your_game), fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    LinearProgressIndicator(
                        progress = (vm.progressPercent / 100f),
                        modifier = Modifier.width(250.dp),
                        color = Color(0xFFFF9800)
                    )
                    Text(vm.buildPhase, fontSize = 14.sp, color = Color.White.copy(alpha = 0.7f))
                    Text(stringResource(R.string.this_may_take_2_5_minutes), fontSize = 12.sp, color = Color.White.copy(alpha = 0.4f))

                    // Notify Me When Done button
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = {
                            vm.isOnRemixSheet = false
                            onDismiss()
                        },
                        shape = RoundedCornerShape(50),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.3f))
                    ) {
                        Icon(Icons.Default.Notifications, null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.notify_me_when_done), fontWeight = FontWeight.SemiBold)
                    }

                    Text(
                        "You can leave this screen \u2014 we'll notify you!",
                        fontSize = 11.sp, color = Color.White.copy(alpha = 0.35f)
                    )
                    Text(
                        "Your game will appear in Explore > Custom Games",
                        fontSize = 11.sp, color = Color.White.copy(alpha = 0.35f)
                    )

                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }

        // Rate limit upsell dialog
        if (showRateLimitDialog) {
            Box(
                modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.7f)).clickable { showRateLimitDialog = false },
                contentAlignment = Alignment.Center
            ) {
                Card(
                    modifier = Modifier.fillMaxWidth().padding(24.dp).clickable(enabled = false) {},
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A2E))
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Icon(Icons.Default.Schedule, null, tint = Color(0xFFFF9800), modifier = Modifier.size(48.dp))
                        Text(stringResource(R.string.free_generations_used_up), fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        Text(
                            "You've used all 5 free generations this hour.\nSpend ${vm.rateLimitCoinCost} coins to create this game now.",
                            fontSize = 14.sp, color = Color.White.copy(alpha = 0.7f),
                            textAlign = TextAlign.Center
                        )

                        // Use coins button
                        val coinBalance = coinManager.balance
                        if (coinBalance >= vm.rateLimitCoinCost) {
                            Button(
                                onClick = { showRateLimitDialog = false; submitRemix(useCoins = true) },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(14.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                                contentPadding = PaddingValues(0.dp)
                            ) {
                                Box(
                                    modifier = Modifier.fillMaxWidth().background(
                                        Brush.horizontalGradient(listOf(Color(0xFFFF9800), Color(0xFFF44336))),
                                        RoundedCornerShape(14.dp)
                                    ).padding(vertical = 14.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.Star, null, tint = Color.Yellow, modifier = Modifier.size(20.dp))
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("Use ${vm.rateLimitCoinCost} Coins", fontWeight = FontWeight.Bold, color = Color.White)
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("($coinBalance available)", fontSize = 11.sp, color = Color.White.copy(alpha = 0.6f))
                                    }
                                }
                            }
                        }

                        // Buy coins button
                        Button(
                            onClick = { showRateLimitDialog = false; showCoinStore = true },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2196F3))
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.ShoppingCart, null, tint = Color.White, modifier = Modifier.size(20.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(stringResource(R.string.buy_coins), fontWeight = FontWeight.SemiBold, color = Color.White)
                            }
                        }

                        // Subscribe button
                        Button(
                            onClick = { showRateLimitDialog = false; showSubscriptionDialog = true },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                            contentPadding = PaddingValues(0.dp)
                        ) {
                            Box(
                                modifier = Modifier.fillMaxWidth().background(
                                    Brush.horizontalGradient(listOf(Color(0xFF9C27B0), Color(0xFF2196F3))),
                                    RoundedCornerShape(14.dp)
                                ).padding(vertical = 14.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.Star, null, tint = Color.Yellow, modifier = Modifier.size(20.dp))
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(stringResource(R.string.subscribe_for_unlimited), fontWeight = FontWeight.Bold, color = Color.White)
                                    }
                                    Text("Unlimited generations + monthly coins", fontSize = 11.sp, color = Color.White.copy(alpha = 0.6f))
                                }
                            }
                        }

                        TextButton(onClick = { showRateLimitDialog = false }) {
                            Text(stringResource(R.string.maybe_later), color = Color.White.copy(alpha = 0.5f))
                        }
                    }
                }
            }
        }

        // Coin store - simple purchase dialog
        if (showCoinStore) {
            AlertDialog(
                onDismissRequest = { showCoinStore = false },
                containerColor = Color(0xFF1A1A2E),
                title = { Text(stringResource(R.string.buy_coins), color = Color.White, fontWeight = FontWeight.Bold) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("Purchase coins to create more games:", color = Color.White.copy(alpha = 0.7f), fontSize = 14.sp)
                        CoinManager.COIN_PACKS.forEach { pack ->
                            Button(
                                onClick = {
                                    showCoinStore = false
                                    (context as? android.app.Activity)?.let { activity ->
                                        coinManager.launchPurchase(activity, pack)
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2196F3))
                            ) {
                                Text("${pack.coins} Coins", fontWeight = FontWeight.SemiBold, color = Color.White)
                            }
                        }
                    }
                },
                confirmButton = {},
                dismissButton = {
                    TextButton(onClick = { showCoinStore = false }) {
                        Text(stringResource(R.string.cancel), color = Color.White.copy(alpha = 0.5f))
                    }
                }
            )
        }

        // Subscription upgrade dialog
        if (showSubscriptionDialog) {
            SubscriptionUpgradeDialog(
                paywallContext = "game_creation",
                onDismiss = { showSubscriptionDialog = false },
                onUpgrade = { tier ->
                    showSubscriptionDialog = false
                }
            )
        }
    }
}

@Composable
private fun RemixChip(text: String, onClick: () -> Unit) {
    Surface(onClick = onClick, shape = RoundedCornerShape(16.dp), color = Color.White.copy(alpha = 0.1f)) {
        Text(text, fontSize = 12.sp, color = Color.White.copy(alpha = 0.8f), modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp))
    }
}
