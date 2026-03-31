package com.kreativekoala.riddleverse

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Publish
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import android.app.Activity
import android.os.Build
import android.view.WindowManager
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.File
import java.util.UUID
import java.util.zip.ZipInputStream

@Composable
fun GamePreviewScreen(
    bundleDir: String,
    bundleBase64: String,
    prompt: String,
    gameId: String?,
    onClose: () -> Unit
) {
    var currentScore by remember { mutableStateOf(0) }
    var gameOver by remember { mutableStateOf(false) }
    val gameErrors = remember { mutableStateListOf<String>() }
    var showErrorBanner by remember { mutableStateOf(false) }
    var showModifySheet by remember { mutableStateOf(false) }
    var modifyDescription by remember { mutableStateOf("") }
    var isModifying by remember { mutableStateOf(false) }
    var modifyPhase by remember { mutableStateOf("") }
    var modifyProgress by remember { mutableStateOf(0f) }
    var showSavedToast by remember { mutableStateOf(true) }
    var isPublishing by remember { mutableStateOf(false) }
    var isPublished by remember { mutableStateOf(false) }
    var publishError by remember { mutableStateOf<String?>(null) }
    var showCloseDialog by remember { mutableStateOf(false) }

    // Active bundle state (can change after modify)
    var activeBundleDir by remember { mutableStateOf(bundleDir) }
    var activeBundleBase64 by remember { mutableStateOf(bundleBase64) }
    var activeGameId by remember { mutableStateOf(gameId) }
    var reloadToken by remember { mutableStateOf(UUID.randomUUID()) }

    val coroutineScope = rememberCoroutineScope()
    val view = LocalView.current

    // Extend game into display cutout area for full-screen experience
    DisposableEffect(Unit) {
        val window = (view.context as? Activity)?.window
        val attrs = window?.attributes
        val original = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) attrs?.layoutInDisplayCutoutMode else null
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && attrs != null) {
            attrs.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            window.attributes = attrs
        }
        onDispose {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && original != null && attrs != null) {
                attrs.layoutInDisplayCutoutMode = original
                window?.attributes = attrs
            }
        }
    }

    // Auto-dismiss saved toast
    LaunchedEffect(showSavedToast) {
        if (showSavedToast) {
            delay(3000)
            showSavedToast = false
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF1A1A2E))
    ) {
        // Fullscreen WebView — nothing else on screen while playing
        key(reloadToken) {
            GameWebView(
                bundleDir = activeBundleDir,
                modifier = Modifier.fillMaxSize(),
                onScoreReceived = { score -> currentScore = score },
                onGameOver = { score ->
                    currentScore = score
                    gameOver = true
                },
                onGameError = { error ->
                    if (error !in gameErrors) {
                        gameErrors.add(error)
                        showErrorBanner = true
                    }
                }
            )
        }

        // Floating close button (top-left)
        IconButton(
            onClick = { if (isPublished) onClose() else showCloseDialog = true },
            modifier = Modifier
                .statusBarsPadding()
                .padding(start = 8.dp, top = 4.dp)
                .align(Alignment.TopStart)
        ) {
            Icon(
                Icons.Default.Close,
                contentDescription = stringResource(R.string.done),
                tint = Color.White,
                modifier = Modifier
                    .size(28.dp)
                    .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                    .padding(4.dp)
            )
        }

        // Modify progress overlay
        if (isModifying) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.7f)),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    CircularProgressIndicator(color = Color.White)
                    Text("Modifying game...", color = Color.White, fontWeight = FontWeight.Bold)
                    if (modifyPhase.isNotEmpty()) {
                        Text(modifyPhase, color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp)
                    }
                    if (modifyProgress > 0f) {
                        LinearProgressIndicator(
                            progress = { modifyProgress / 100f },
                            modifier = Modifier.width(200.dp),
                            color = Color(0xFF6B5CE7)
                        )
                    }
                }
            }
        }

        // Auto-saved confirmation toast
        AnimatedVisibility(
            visible = showSavedToast,
            modifier = Modifier.align(Alignment.BottomCenter),
            enter = slideInVertically { it },
            exit = slideOutVertically { it }
        ) {
            Card(
                modifier = Modifier.padding(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF27AE60)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("Game ready! 🎮", color = Color.White, fontWeight = FontWeight.Bold)
                    Text("Tap 💰 in the top bar to publish and earn real cash!", color = Color(0xFFFFD700), fontSize = 12.sp)
                }
            }
        }

        // Publish error toast
        if (publishError != null) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 64.dp, start = 16.dp, end = 16.dp)
            ) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFB71C1C)),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text(
                        publishError!!,
                        color = Color.White,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }
            }
        }
    }

    // Save-before-leaving dialog
    if (showCloseDialog) {
        AlertDialog(
            onDismissRequest = { showCloseDialog = false },
            title = {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    Text("💰", fontSize = 32.sp)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Publish before you go!", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color.White)
                }
            },
            text = {
                Text(
                    "Players can find your game and you earn real cash — 55% of every coin spent on it. Publish now to go live!",
                    fontSize = 14.sp,
                    color = Color.White.copy(alpha = 0.8f),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showCloseDialog = false
                        if (!isPublishing && !isPublished) {
                            isPublishing = true
                            coroutineScope.launch {
                                publishError = null
                                try {
                                    publishAndShare(
                                        gameId = activeGameId,
                                        bundleBase64 = activeBundleBase64,
                                        prompt = prompt
                                    )
                                    isPublished = true
                                } catch (e: Exception) {
                                    publishError = e.localizedMessage ?: "Failed to publish"
                                }
                                isPublishing = false
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF21BF63)),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("Publish & Earn 💰", fontWeight = FontWeight.Bold, color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showCloseDialog = false; onClose() }) {
                    Text("Leave without saving", color = Color.White.copy(alpha = 0.5f))
                }
            },
            containerColor = Color(0xFF1E1E3A)
        )
    }

    // Modify bottom sheet dialog
    if (showModifySheet) {
        AlertDialog(
            onDismissRequest = { showModifySheet = false },
            title = { Text(stringResource(R.string.modify_game)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Describe what's wrong or what to change:", fontSize = 14.sp, color = Color.Gray)
                    OutlinedTextField(
                        value = modifyDescription,
                        onValueChange = { if (it.length <= 500) modifyDescription = it },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 100.dp),
                        label = { Text("What needs fixing?") }
                    )
                    Text(
                        "${modifyDescription.length}/500",
                        fontSize = 12.sp,
                        color = if (modifyDescription.length > 500) Color.Red else Color.Gray,
                        modifier = Modifier.align(Alignment.End)
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showModifySheet = false
                        coroutineScope.launch {
                            modifyGame(
                                gameId = activeGameId,
                                description = modifyDescription,
                                currentBundleDir = activeBundleDir,
                                onProgress = { phase, pct ->
                                    modifyPhase = phase
                                    modifyProgress = pct
                                },
                                onStart = { isModifying = true },
                                onSuccess = { dir, base64, newGid ->
                                    activeBundleDir = dir
                                    activeBundleBase64 = base64
                                    if (newGid != null) activeGameId = newGid
                                    gameErrors.clear()
                                    showErrorBanner = false
                                    modifyDescription = ""
                                    currentScore = 0
                                    gameOver = false
                                    reloadToken = UUID.randomUUID()
                                    isModifying = false
                                },
                                onError = { error ->
                                    isModifying = false
                                    gameErrors.add("Modify failed: $error")
                                    showErrorBanner = true
                                }
                            )
                        }
                    },
                    enabled = modifyDescription.trim().isNotEmpty() && modifyDescription.length <= 500
                ) {
                    Text(stringResource(R.string.fix_it))
                }
            },
            dismissButton = {
                TextButton(onClick = { showModifySheet = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
}

// MARK: - Publish & Share

private suspend fun publishAndShare(gameId: String?, bundleBase64: String, prompt: String) {
    withContext(Dispatchers.IO) {
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: "anonymous"
        val displayName = FirebaseAuth.getInstance().currentUser?.displayName
            ?: FirebaseAuth.getInstance().currentUser?.email?.substringBefore("@")
            ?: "Anonymous"
        val title = prompt.trim().take(100)

        val json = JSONObject().apply {
            put("title", title)
            put("bundle", bundleBase64)
            put("creatorId", userId)
            put("creatorName", displayName)
            put("initialPrompt", prompt)
            if (!gameId.isNullOrEmpty()) put("gameId", gameId)
        }

        val body = json.toString().toRequestBody("application/json".toMediaTypeOrNull())
        val request = Request.Builder()
            .url("https://puzzleverseai.com/api/game-creation/save")
            .addHeader("Content-Type", "application/json")
            .post(body)
            .build()

        val response = HttpClientProvider.client.newCall(request).execute()
        val responseBody = response.body?.string()
        android.util.Log.d("PublishShare", "Save response ${response.code}: $responseBody")
        response.close()
        if (!response.isSuccessful) {
            throw Exception("Save failed (${response.code}): $responseBody")
        }
    }
}

// MARK: - Modify Flow (uses /:gameId/customize)

private suspend fun modifyGame(
    gameId: String?,
    description: String,
    currentBundleDir: String,
    onProgress: (String, Float) -> Unit,
    onStart: () -> Unit,
    onSuccess: (String, String, String?) -> Unit,
    onError: (String) -> Unit
) {
    if (gameId == null) {
        onError("Cannot modify: game has no ID")
        return
    }

    withContext(Dispatchers.Main) { onStart() }

    withContext(Dispatchers.IO) {
        try {
            val userId = FirebaseAuth.getInstance().currentUser?.uid ?: "anonymous"

            val json = JSONObject().apply {
                put("userId", userId)
                put("customizeDescription", description.trim())
            }

            val body = json.toString().toRequestBody("application/json".toMediaTypeOrNull())
            val request = Request.Builder()
                .url("https://puzzleverseai.com/api/game-creation/$gameId/customize")
                .addHeader("Content-Type", "application/json")
                .addHeader("Accept", "text/event-stream")
                .post(body)
                .build()

            val client = HttpClientProvider.client.newBuilder()
                .protocols(listOf(Protocol.HTTP_1_1))
                .readTimeout(java.time.Duration.ofMinutes(10))
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                val errBody = try { response.body?.string() } catch (_: Exception) { null }
                val errMsg = try {
                    JSONObject(errBody ?: "").optString("error", "Server error")
                } catch (_: Exception) { "Server error (${response.code})" }
                response.close()
                withContext(Dispatchers.Main) { onError(errMsg) }
                return@withContext
            }

            val source = response.body?.source() ?: run {
                withContext(Dispatchers.Main) { onError("Empty response") }
                return@withContext
            }

            var newBundle: String? = null
            var newGameId: String? = null
            var sseBuffer = ""

            while (!source.exhausted()) {
                val line = source.readUtf8Line() ?: break
                sseBuffer += line + "\n"

                while (sseBuffer.contains("\n\n")) {
                    val idx = sseBuffer.indexOf("\n\n")
                    val event = sseBuffer.substring(0, idx)
                    sseBuffer = sseBuffer.substring(idx + 2)

                    for (eventLine in event.split("\n")) {
                        if (!eventLine.startsWith("data: ")) continue
                        val jsonStr = eventLine.substring(6)

                        try {
                            val eventJson = JSONObject(jsonStr)
                            when (eventJson.optString("type")) {
                                "status" -> {
                                    val message = eventJson.optString("message", "")
                                    val pct = eventJson.optDouble("progressPercent", 0.0).toFloat()
                                    withContext(Dispatchers.Main) { onProgress(message, pct) }
                                }
                                "result" -> {
                                    newBundle = eventJson.optString("bundle", null)
                                    newGameId = eventJson.optString("gameId", null)
                                }
                                "error" -> {
                                    val errMsg = eventJson.optString("error", "Unknown error")
                                    response.close()
                                    withContext(Dispatchers.Main) { onError(errMsg) }
                                    return@withContext
                                }
                            }
                        } catch (_: Exception) {}
                    }
                }
            }

            response.close()

            val bundle = newBundle
            if (bundle == null) {
                withContext(Dispatchers.Main) { onError("No bundle received from modification") }
                return@withContext
            }

            // Extract new bundle — derive cache dir from existing bundle dir
            val zipData = android.util.Base64.decode(bundle, android.util.Base64.DEFAULT)
            val gamesDir = File(currentBundleDir).parentFile ?: File(currentBundleDir).also { it.mkdirs() }
            val newDir = File(gamesDir, UUID.randomUUID().toString())
            newDir.mkdirs()

            val zipStream = ZipInputStream(ByteArrayInputStream(zipData))
            var entry = zipStream.nextEntry
            while (entry != null) {
                val outFile = File(newDir, entry.name)
                if (!outFile.canonicalPath.startsWith(newDir.canonicalPath)) {
                    zipStream.closeEntry()
                    entry = zipStream.nextEntry
                    continue
                }
                if (entry.isDirectory) {
                    outFile.mkdirs()
                } else {
                    outFile.parentFile?.mkdirs()
                    outFile.outputStream().use { out -> zipStream.copyTo(out) }
                }
                zipStream.closeEntry()
                entry = zipStream.nextEntry
            }
            zipStream.close()

            withContext(Dispatchers.Main) {
                onSuccess(newDir.absolutePath, bundle, newGameId)
            }
        } catch (e: Exception) {
            withContext(Dispatchers.Main) { onError(e.localizedMessage ?: "Unknown error") }
        }
    }
}
