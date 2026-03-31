package com.kreativekoala.riddleverse

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Request
import org.json.JSONObject
import java.io.ByteArrayOutputStream

@Composable
fun GameCreationScreen(
    context: android.content.Context,
    generationVM: GameGenerationViewModel = androidx.lifecycle.viewmodel.compose.viewModel(),
    initialTab: Int = 1
) {
    val TAG = "GameCreation"
    var selectedTab by remember { mutableIntStateOf(initialTab) } // Default to Explore tab
    var promptText by remember { mutableStateOf("") }
    var isListening by remember { mutableStateOf(false) }
    var notifyConfirmed by remember { mutableStateOf(false) }
    var selectedImageBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var selectedImageBase64 by remember { mutableStateOf<String?>(null) }
    var showCoinStore by remember { mutableStateOf(false) }
    var showSubscriptionDialog by remember { mutableStateOf(false) }
    val coinManager = remember { CoinManager.shared }
    val coroutineScope = rememberCoroutineScope()

    val speechHelper = remember { SpeechRecognizerHelper(context) }

    // Keep screen on during generation to prevent SSE connection loss
    val view = LocalView.current
    DisposableEffect(generationVM.isGenerating) {
        if (generationVM.isGenerating) view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }

    // Permission launcher for microphone
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            speechHelper.startListening(
                onResult = { text -> promptText = text },
                onError = { err -> Log.e(TAG, "Speech error: $err") },
                onListening = { listening -> isListening = listening }
            )
        }
    }

    // Image picker launcher
    val imagePickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            coroutineScope.launch(Dispatchers.IO) {
                try {
                    val inputStream = context.contentResolver.openInputStream(uri)
                    val original = BitmapFactory.decodeStream(inputStream)
                    inputStream?.close()
                    if (original != null) {
                        // Resize to max 1024px on longest side
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
                        // Compress to JPEG
                        val baos = ByteArrayOutputStream()
                        resized.compress(Bitmap.CompressFormat.JPEG, 70, baos)
                        val base64 = Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP)
                        withContext(Dispatchers.Main) {
                            selectedImageBitmap = resized
                            selectedImageBase64 = base64
                        }
                        Log.d(TAG, "Image selected: ${resized.width}x${resized.height}, ${baos.size()} bytes")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to process image", e)
                }
            }
        }
    }

    data class Suggestion(val label: String, val prompt: String)

    // Fallback suggestions in case API is unreachable
    val fallbackSuggestions = listOf(
        Suggestion("Snake game", "A classic snake game with neon glow effects. Swipe to change direction. Speed increases every 5 points. Walls and self-bite end the game."),
        Suggestion("Brick breaker", "A brick breaker game with colorful bricks. Drag paddle to bounce ball. Power-ups: wider paddle, multi-ball. 3 lives."),
        Suggestion("Memory cards", "A memory card matching game with 6 pairs in a 3x4 grid. Tap to flip and find matches. Track moves and time."),
        Suggestion("Whack-a-mole", "Whack-a-mole with emoji characters on a 3x3 grid. Tap moles to score. Golden moles = bonus. 30 second timer."),
        Suggestion("Space invaders", "Space invaders — drag spaceship, tap to shoot. Aliens descend and shoot back. Wave system gets harder."),
        Suggestion("Trivia quiz", "Science trivia with 10 questions, 4 options each. 15-second timer. Faster = more points.")
    )

    var suggestions by remember { mutableStateOf(fallbackSuggestions) }

    // Fetch dynamic suggestions from server
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            try {
                val request = Request.Builder()
                    .url("https://puzzleverseai.com/api/game-creation/suggestions?count=6")
                    .get()
                    .build()
                val response = HttpClientProvider.client.newCall(request).execute()
                val body = response.body?.string()
                response.close()
                if (body != null) {
                    val json = JSONObject(body)
                    val arr = json.optJSONArray("suggestions")
                    if (arr != null && arr.length() > 0) {
                        val fetched = (0 until arr.length()).map { i ->
                            val s = arr.getJSONObject(i)
                            Suggestion(s.optString("label", ""), s.optString("prompt", ""))
                        }.filter { it.label.isNotEmpty() && it.prompt.isNotEmpty() }
                        if (fetched.isNotEmpty()) {
                            withContext(Dispatchers.Main) { suggestions = fetched }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to fetch suggestions, using defaults", e)
            }
        }
    }

    // Prompt quality helpers
    val trimmedLength = promptText.trim().length
    val qualityLevel = when {
        trimmedLength == 0 -> 0
        trimmedLength < 30 -> 0
        trimmedLength < 80 -> 1
        trimmedLength < 150 -> 2
        else -> 3
    }
    val qualityColor = when (qualityLevel) {
        0 -> if (promptText.isEmpty()) Color.White.copy(alpha = 0.3f) else Color.Red
        1 -> Color(0xFFFF8C00)
        2 -> Color.Yellow
        else -> Color(0xFF4CAF50)
    }
    val qualityLabel = when (qualityLevel) {
        0 -> if (promptText.isEmpty()) "0/500" else "Too short"
        1 -> "Basic"
        2 -> "Good"
        else -> "Great detail!"
    }
    val qualityProgress = (trimmedLength.toFloat() / 150f).coerceAtMost(1f)
    val qualityHint = when (qualityLevel) {
        0 -> "Tip: Describe gameplay mechanics, visual style, and rules for best results"
        1 -> "Add more detail \u2014 describe controls, scoring, difficulty, and visual style"
        else -> ""
    }

    DisposableEffect(Unit) {
        onDispose { speechHelper.destroy() }
    }

    // Preview is handled at HomeScreen level for true fullscreen

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF1A1A2E))
            .statusBarsPadding()
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Tab row
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = Color(0xFF1A1A2E),
                contentColor = Color.White,
                divider = {}
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(stringResource(R.string.create), fontWeight = if (selectedTab == 0) FontWeight.Bold else FontWeight.Normal)
                            Spacer(modifier = Modifier.width(4.dp))
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = Color(0xFFFF4500)
                            ) {
                                Text(
                                    text = "NEW",
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                )
                            }
                        }
                    },
                    selectedContentColor = Color(0xFFFF8C00),
                    unselectedContentColor = Color.White.copy(alpha = 0.5f)
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text(stringResource(R.string.explore), fontWeight = if (selectedTab == 1) FontWeight.Bold else FontWeight.Normal) },
                    selectedContentColor = Color(0xFFFF8C00),
                    unselectedContentColor = Color.White.copy(alpha = 0.5f)
                )
            }

            if (selectedTab == 1) {
                GameBrowseContent(context)
            } else {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(20.dp))

            // Earn money banner (reuses ForYouTab composable)
            val context2 = context
            val prefs2 = remember { context2.getSharedPreferences("riddleverse_prefs", android.content.Context.MODE_PRIVATE) }
            var showCreateBanner by remember { mutableStateOf(!prefs2.getBoolean("earn_banner_dismissed", false)) }
            if (showCreateBanner) {
                EarnMoneyBanner(
                    onTap = { /* already on create tab */ },
                    onDismiss = {
                        prefs2.edit().putBoolean("earn_banner_dismissed", true).apply()
                        showCreateBanner = false
                    }
                )
                Spacer(modifier = Modifier.height(16.dp))
            }

            // Header
            Text(
                text = stringResource(R.string.create_a_game),
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.describe_game_prompt),
                fontSize = 14.sp,
                color = Color.White.copy(alpha = 0.6f),
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Input field
            OutlinedTextField(
                value = promptText,
                onValueChange = { if (it.length <= 500) promptText = it },
                placeholder = { Text(stringResource(R.string.describe_game_placeholder), color = Color.White.copy(alpha = 0.3f)) },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 120.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedBorderColor = Color(0xFFFF8C00),
                    unfocusedBorderColor = Color.White.copy(alpha = 0.15f),
                    cursorColor = Color(0xFFFF8C00)
                ),
                shape = RoundedCornerShape(16.dp)
            )

            // Voice + image + quality indicator row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
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
                                    onResult = { text -> promptText = text },
                                    onError = { err -> Log.e(TAG, "Speech error: $err") },
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
                            text = if (isListening) "Listening..." else "Voice",
                            fontSize = 12.sp,
                            color = Color.White.copy(alpha = 0.7f)
                        )
                    }
                }

                // Image attachment button
                Surface(
                    modifier = Modifier.clickable {
                        imagePickerLauncher.launch("image/*")
                    },
                    shape = CircleShape,
                    color = if (selectedImageBase64 != null) Color(0xFFFF8C00).copy(alpha = 0.2f) else Color.White.copy(alpha = 0.08f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Image,
                            contentDescription = "Attach image",
                            tint = Color(0xFFFF8C00),
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (selectedImageBase64 != null) "Image added" else "Reference",
                            fontSize = 12.sp,
                            color = Color.White.copy(alpha = 0.7f)
                        )
                    }
                }
                } // end left row

                // Quality indicator
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .background(qualityColor, CircleShape)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = qualityLabel,
                        fontSize = 11.sp,
                        color = qualityColor
                    )
                }
            }

            // Quality progress bar
            if (promptText.isNotEmpty()) {
                Spacer(modifier = Modifier.height(6.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp)
                        .background(Color.White.copy(alpha = 0.1f), RoundedCornerShape(2.dp))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(qualityProgress)
                            .height(3.dp)
                            .background(qualityColor, RoundedCornerShape(2.dp))
                    )
                }

                if (qualityLevel < 2 && qualityHint.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = qualityHint,
                        fontSize = 11.sp,
                        color = Color.White.copy(alpha = 0.4f)
                    )
                }
            }

            // Image preview thumbnail
            if (selectedImageBitmap != null) {
                Spacer(modifier = Modifier.height(12.dp))
                Box(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .background(Color.White.copy(alpha = 0.06f), RoundedCornerShape(12.dp))
                            .padding(8.dp)
                    ) {
                        Image(
                            bitmap = selectedImageBitmap!!.asImageBitmap(),
                            contentDescription = stringResource(R.string.reference_image),
                            modifier = Modifier
                                .size(56.dp)
                                .clip(RoundedCornerShape(8.dp)),
                            contentScale = ContentScale.Crop
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.reference_image),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                color = Color.White.copy(alpha = 0.8f)
                            )
                            Text(
                                text = "AI will use this as visual context",
                                fontSize = 11.sp,
                                color = Color.White.copy(alpha = 0.4f)
                            )
                        }
                        IconButton(
                            onClick = {
                                selectedImageBitmap = null
                                selectedImageBase64 = null
                            },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "Remove image",
                                tint = Color.White.copy(alpha = 0.5f),
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Suggestion chips
            Text(
                text = stringResource(R.string.try_these_ideas),
                fontSize = 12.sp,
                color = Color.White.copy(alpha = 0.5f),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp)
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                suggestions.forEach { suggestion ->
                    Surface(
                        modifier = Modifier.clickable { promptText = suggestion.prompt },
                        shape = RoundedCornerShape(20.dp),
                        color = Color.White.copy(alpha = 0.08f),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.15f))
                    ) {
                        Text(
                            text = suggestion.label,
                            fontSize = 12.sp,
                            color = Color.White.copy(alpha = 0.8f),
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(28.dp))

            // Progress card (shown during generation)
            if (generationVM.isGenerating) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.06f)),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                color = Color(0xFFFF8C00),
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = generationVM.buildPhase.ifEmpty { "Connecting..." },
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                )
                                if (generationVM.buildDetail.isNotEmpty()) {
                                    Text(
                                        text = generationVM.buildDetail,
                                        color = Color.White.copy(alpha = 0.7f),
                                        fontSize = 11.sp
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Progress bar
                        LinearProgressIndicator(
                            progress = generationVM.progressPercent / 100f,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp),
                            color = Color(0xFFFF8C00),
                            trackColor = Color.White.copy(alpha = 0.15f)
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        // Percentage + ETA
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "${generationVM.progressPercent.toInt()}%",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            if (generationVM.estimatedSecondsRemaining > 0) {
                                Text(
                                    text = formatETA(generationVM.estimatedSecondsRemaining),
                                    fontSize = 12.sp,
                                    color = Color.White.copy(alpha = 0.6f)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        if (notifyConfirmed) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("🔔", fontSize = 14.sp)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    "We'll notify you when it's ready!",
                                    fontSize = 12.sp,
                                    color = Color(0xFF27AE60)
                                )
                            }
                        } else {
                            OutlinedButton(
                                onClick = { notifyConfirmed = true },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(10.dp),
                                border = BorderStroke(1.dp, Color(0xFFFF8C00).copy(alpha = 0.5f))
                            ) {
                                Text("\uD83D\uDD14  " + stringResource(R.string.notify_me_when_done), color = Color(0xFFFF8C00), fontSize = 13.sp)
                            }
                        }
                    }
                }
            }

            // Generate button
            Button(
                onClick = {
                    notifyConfirmed = false
                    generationVM.startGeneration(prompt = promptText, context = context, imageBase64 = selectedImageBase64)
                },
                enabled = promptText.isNotBlank() && promptText.length <= 500 && !generationVM.isGenerating,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFFF8C00),
                    disabledContainerColor = Color(0xFFFF8C00).copy(alpha = 0.4f)
                )
            ) {
                if (generationVM.isGenerating) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = Color.White,
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.generating), color = Color.White, fontWeight = FontWeight.Bold)
                } else {
                    Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = Color.White)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.generate_game), color = Color.White, fontWeight = FontWeight.Bold)
                }
            }

            // Error message
            val currentError = generationVM.errorMessage
            if (currentError != null) {
                val isQuotaError = currentError.contains("usage limit", ignoreCase = true) ||
                        currentError.contains("resets", ignoreCase = true)

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isQuotaError)
                            Color(0xFFFF8C00).copy(alpha = 0.15f)
                        else
                            Color.Red.copy(alpha = 0.15f)
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (isQuotaError) "\u23F0" else "\u26A0\uFE0F",
                            fontSize = 24.sp
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = if (isQuotaError) "Usage Limit Reached" else "Error",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = Color.White
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = currentError,
                                fontSize = 13.sp,
                                color = Color.White.copy(alpha = 0.8f)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(40.dp))
        }
            } // end else (Create tab)
        } // end outer Column

        // Rate limit upsell dialog
        if (generationVM.showRateLimitUpsell) {
            Box(
                modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.7f)).clickable { generationVM.showRateLimitUpsell = false },
                contentAlignment = Alignment.Center
            ) {
                Card(
                    modifier = Modifier.fillMaxWidth().padding(24.dp).clickable(enabled = false) {},
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E3A))
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("\u23F0", fontSize = 44.sp)
                        Spacer(Modifier.height(12.dp))
                        Text(stringResource(R.string.free_generations_used_up), fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Color.White)
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "You've used all 5 free generations this hour.\nSpend ${generationVM.rateLimitCoinCost} coins to create this game now.",
                            color = Color.White.copy(alpha = 0.7f), fontSize = 14.sp, textAlign = TextAlign.Center
                        )
                        Spacer(Modifier.height(20.dp))

                        if (coinManager.balance >= generationVM.rateLimitCoinCost) {
                            Button(
                                onClick = { generationVM.showRateLimitUpsell = false; generationVM.startGeneration(prompt = promptText, context = context, imageBase64 = selectedImageBase64, useCoins = true) },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(14.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                                contentPadding = PaddingValues(0.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().background(
                                        brush = androidx.compose.ui.graphics.Brush.horizontalGradient(listOf(Color(0xFFFF8C00), Color(0xFFFF4444))),
                                        shape = RoundedCornerShape(14.dp)
                                    ).padding(vertical = 14.dp),
                                    horizontalArrangement = Arrangement.Center,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("\u2B50", fontSize = 16.sp)
                                    Spacer(Modifier.width(8.dp))
                                    Text("Use ${generationVM.rateLimitCoinCost} Coins", fontWeight = FontWeight.Bold, color = Color.White)
                                    Spacer(Modifier.width(8.dp))
                                    Text("(${coinManager.balance} available)", fontSize = 12.sp, color = Color.White.copy(alpha = 0.6f))
                                }
                            }
                            Spacer(Modifier.height(8.dp))
                        }

                        Button(
                            onClick = { generationVM.showRateLimitUpsell = false; showCoinStore = true },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2196F3))
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("\uD83D\uDCB0", fontSize = 16.sp)
                                Spacer(Modifier.width(8.dp))
                                Text(stringResource(R.string.buy_coins), fontWeight = FontWeight.SemiBold, color = Color.White)
                            }
                        }
                        Spacer(Modifier.height(8.dp))

                        Button(
                            onClick = { generationVM.showRateLimitUpsell = false; showSubscriptionDialog = true },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                            contentPadding = PaddingValues(0.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().background(
                                    brush = androidx.compose.ui.graphics.Brush.horizontalGradient(listOf(Color(0xFF9C27B0), Color(0xFF2196F3))),
                                    shape = RoundedCornerShape(14.dp)
                                ).padding(vertical = 14.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("\uD83D\uDC51", fontSize = 16.sp)
                                Spacer(Modifier.width(8.dp))
                                Column {
                                    Text(stringResource(R.string.subscribe_for_unlimited), fontWeight = FontWeight.SemiBold, color = Color.White, fontSize = 14.sp)
                                    Text(stringResource(R.string.get_unlimited_generations), fontSize = 10.sp, color = Color.White.copy(alpha = 0.6f))
                                }
                            }
                        }
                        Spacer(Modifier.height(12.dp))

                        TextButton(onClick = { generationVM.showRateLimitUpsell = false }) {
                            Text(stringResource(R.string.maybe_later), color = Color.White.copy(alpha = 0.5f))
                        }
                    }
                }
            }
        }

        // Coin store dialog
        if (showCoinStore) {
            AlertDialog(
                onDismissRequest = { showCoinStore = false },
                title = { Text(stringResource(R.string.buy_coins), fontWeight = FontWeight.Bold) },
                text = {
                    Column {
                        CoinManager.COIN_PACKS.forEach { pack ->
                            Button(
                                onClick = {
                                    (context as? android.app.Activity)?.let { activity ->
                                        coinManager.launchPurchase(activity, pack)
                                    }
                                    showCoinStore = false
                                },
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text(pack.label)
                            }
                        }
                    }
                },
                confirmButton = {},
                dismissButton = { TextButton(onClick = { showCoinStore = false }) { Text(stringResource(R.string.cancel)) } }
            )
        }

        // Subscription dialog
        if (showSubscriptionDialog) {
            SubscriptionUpgradeDialog(
                paywallContext = "game_creation",
                onDismiss = { showSubscriptionDialog = false },
                onUpgrade = { _ -> showSubscriptionDialog = false }
            )
        }

        // Hard paywall — lifetime free generations used up
        if (generationVM.showHardPaywall) {
            SubscriptionUpgradeDialog(
                paywallContext = "hard_paywall_lifetime_limit",
                onDismiss = { generationVM.showHardPaywall = false },
                onUpgrade = { _ -> generationVM.showHardPaywall = false }
            )
        }
    } // end Box
}

private fun formatETA(seconds: Int): String {
    return if (seconds < 60) {
        "~${seconds}s remaining"
    } else {
        val min = seconds / 60
        val sec = seconds % 60
        "~${min}m ${sec}s remaining"
    }
}

private suspend fun saveGame(title: String, bundle: String, prompt: String) {
    withContext(Dispatchers.IO) {
        try {
            val json = JSONObject().apply {
                put("title", title)
                put("bundle", bundle)
                put("creatorId", FirebaseAuth.getInstance().currentUser?.uid ?: "anonymous")
                put("creatorName", FirebaseAuth.getInstance().currentUser?.displayName
                    ?: FirebaseAuth.getInstance().currentUser?.email?.substringBefore("@")
                    ?: "Anonymous")
                put("initialPrompt", prompt)
            }

            val body = json.toString().toRequestBody("application/json".toMediaTypeOrNull())
            val request = Request.Builder()
                .url("https://puzzleverseai.com/api/game-creation/save")
                .addHeader("Content-Type", "application/json")
                .post(body)
                .build()

            HttpClientProvider.client.newCall(request).execute()
        } catch (e: Exception) {
            Log.e("GameCreation", "Save error", e)
        }
    }
}
