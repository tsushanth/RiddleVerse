package com.kreativekoala.riddleverse

import com.kreativekoala.riddleverse.ui.theme.*
import android.content.Intent
import com.revenuecat.purchases.Purchases
import com.revenuecat.purchases.PurchasesConfiguration
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.messaging.FirebaseMessaging
import com.google.gson.Gson
import com.kreativekoala.ratingkit.RatingKit
import com.kreativekoala.riddleverse.ui.theme.RiddleVerseTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject


class MainActivity : AppCompatActivity() {
    companion object {
        private const val TAG = "MainActivity"
        private const val NOTIFICATION_PERMISSION_REQUEST_CODE = 1001
        private const val SERVER_URL = "https://puzzleverseai.com" // Replace with your server URL
    }
    // Use singleton HttpClient to prevent memory leaks
    private val httpClient = HttpClientProvider.client

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        RatingKit.trackAppOpen(this)

        // Defer FCM setup to background thread - not critical for startup
        CoroutineScope(Dispatchers.IO).launch {
            setupFCM()
        }

        // Request notification permissions
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(android.Manifest.permission.POST_NOTIFICATIONS),
                1001
            )
        }

        // Handle deep link to open specific game (riddleverse://game/{gameId})
        if (intent?.data?.scheme == "riddleverse" && intent?.data?.host == "game") {
            val pathSegments = intent.data?.pathSegments
            val gameId = pathSegments?.firstOrNull() ?: intent.data?.getQueryParameter("gameId")
            if (!gameId.isNullOrBlank()) {
                Log.d("MainActivity", "Deep link to game: $gameId")
                val userId = FirebaseAuth.getInstance().currentUser?.uid
                ChromeGameLauncher.launchGame(this, gameId, userId)
                finish()
                return
            }
        }

        // Handle magic link auth from Telegram/WhatsApp bot
        val magicLinkToken = if (intent?.data?.scheme == "riddleverse" && intent?.data?.host == "auth") {
            intent?.data?.getQueryParameter("token")
        } else null

        if (!magicLinkToken.isNullOrBlank()) {
            Log.d("MainActivity", "Magic link auth token received")
            setContent {
                RiddleVerseTheme {
                    DeepLinkLoadingScreen()
                }
            }
            CoroutineScope(Dispatchers.Main).launch {
                val success = MagicLinkAuthManager.signInWithToken(this@MainActivity, magicLinkToken)
                if (success) {
                    val user = FirebaseAuth.getInstance().currentUser
                    if (user != null) {
                        Purchases.sharedInstance.logIn(
                            user.uid,
                            object : com.revenuecat.purchases.interfaces.LogInCallback {
                                override fun onReceived(customerInfo: com.revenuecat.purchases.CustomerInfo, created: Boolean) {
                                    setRevenueCatAttributes(user)
                                }
                                override fun onError(error: com.revenuecat.purchases.PurchasesError) {
                                    Log.e("MainActivity", "RevenueCat logIn error after magic link: ${error.message}")
                                }
                            }
                        )
                    }
                    Toast.makeText(this@MainActivity, "Signed in successfully!", Toast.LENGTH_SHORT).show()
                    startActivity(Intent(this@MainActivity, HomeActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    })
                    finish()
                } else {
                    Toast.makeText(this@MainActivity, "Sign-in failed. Please try again.", Toast.LENGTH_LONG).show()
                    navigateToDefaultScreen()
                }
            }
            return
        }

        val puzzleId = intent?.data?.getQueryParameter("puzzleId")
        Log.d("MainActivity", "Received puzzleId: $puzzleId")

        // Handle deep link puzzle fetching OUTSIDE of setContent to avoid nested setContent calls
        if (!puzzleId.isNullOrBlank()) {
            // Show loading state immediately
            setContent {
                RiddleVerseTheme {
                    DeepLinkLoadingScreen()
                }
            }

            // Fetch puzzle data on background thread
            fetchCustomPuzzleDetail(this, puzzleId) { puzzles, puzzleSetJson ->
                runOnUiThread {
                    if (!puzzles.isNullOrEmpty()) {
                        val intent = Intent(this, PuzzleActivity::class.java).apply {
                            putExtra("puzzleList", Gson().toJson(puzzles))
                            putExtra("puzzleIndex", 0)
                            putExtra("customPuzzleSet", puzzleSetJson)
                        }
                        Log.d("MainActivity", "Launching PuzzleActivity from deep link")
                        startActivity(intent)
                        finish()
                    } else {
                        Log.e("MainActivity", "Failed to fetch puzzle data or empty puzzle list")
                        Toast.makeText(this, "Failed to load puzzle.", Toast.LENGTH_LONG).show()
                        navigateToDefaultScreen()
                    }
                }
            }
        } else {
            navigateToDefaultScreen()
        }
    }

    private fun navigateToDefaultScreen() {
        val currentUser = FirebaseAuth.getInstance().currentUser

        if (currentUser != null) {
            Log.d("MainActivity", "User logged in: ${currentUser.email}, navigating to Home")
            Purchases.sharedInstance.logIn(
                currentUser.uid,
                object : com.revenuecat.purchases.interfaces.LogInCallback {
                    override fun onReceived(customerInfo: com.revenuecat.purchases.CustomerInfo, created: Boolean) {
                        Log.d("MainActivity", "RevenueCat logIn success, created=$created")
                        // Set subscriber attributes on returning login
                        setRevenueCatAttributes(currentUser)
                    }
                    override fun onError(error: com.revenuecat.purchases.PurchasesError) {
                        Log.e("MainActivity", "RevenueCat logIn error: ${error.message}")
                    }
                }
            )
            startActivity(Intent(this, HomeActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            })
            finish()
        } else {
            Log.d("MainActivity", "No user logged in, showing welcome screen")
            setContent {
                RiddleVerseTheme {
                    WelcomeScreen(onGetStartedClick = {
                        startActivity(Intent(this@MainActivity, WelcomeActivity::class.java))
                        finish()
                    })
                }
            }
        }
    }


    private fun setRevenueCatAttributes(user: com.google.firebase.auth.FirebaseUser) {
        try {
            val purchases = Purchases.sharedInstance
            user.email?.let { purchases.setEmail(it) }
            user.displayName?.let { purchases.setDisplayName(it) }

            val prefs = getSharedPreferences("install_attribution", MODE_PRIVATE)
            val mediaSource = prefs.getString("media_source", null)
            val campaign = prefs.getString("campaign", null)

            val attrs = mutableMapOf<String, String>()
            attrs["\$mediaSource"] = mediaSource ?: "organic"
            if (campaign != null) attrs["\$campaign"] = campaign
            purchases.setAttributes(attrs)

            Log.d("MainActivity", "RevenueCat attributes set: mediaSource=${mediaSource ?: "organic"}")
        } catch (e: Exception) {
            Log.e("MainActivity", "Failed to set RevenueCat attributes", e)
        }
    }

    private fun setupFCM() {
        FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
            if (!task.isSuccessful) {
                Log.w(TAG, "Fetching FCM registration token failed", task.exception)
                return@addOnCompleteListener
            }

            // Get new FCM registration token
            val token = task.result
            Log.d(TAG, "FCM Token: $token")

            // Send token to server
            sendTokenToServer(token)
        }
    }

    private fun sendTokenToServer(token: String) {
        val userEmail = getCurrentUserEmail() // Implement this based on your auth system
        val userId = getCurrentUserId()

        if (userEmail == null || userId == null) {
            Log.w(TAG, "User not authenticated, cannot send token to server")
            return
        }

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val requestBody = JSONObject().apply {
                    put("userEmail", userEmail)
                    put("userId", userId)
                    put("fcmToken", token)
                    put("platform", "android")
                }.toString()

                val request = Request.Builder()
                    .url("$SERVER_URL/register-fcm-token")
                    .post(requestBody.toRequestBody("application/json".toMediaType()))
                    .addHeader("Content-Type", "application/json")
                    .build()

                val response = httpClient.newCall(request).execute()

                if (response.isSuccessful) {
                    Log.d(TAG, "FCM token registered successfully")
                } else {
                    Log.e(TAG, "Failed to register FCM token: ${response.code}")
                }

                response.close()

            } catch (e: Exception) {
                Log.e(TAG, "Error registering FCM token", e)
            }
        }
    }

    private fun handleNotificationIntent() {
        val notificationType = intent.getStringExtra("notification_type")
        val openDailyPuzzles = intent.getBooleanExtra("open_daily_puzzles", false)

        when (notificationType) {
            "daily_puzzles" -> {
                if (openDailyPuzzles) {
                    Log.d(TAG, "Opening daily puzzles from notification")
                    // Navigate to daily puzzles section
                    // Implement based on your navigation structure
                }
            }
            "test" -> {
                Log.d(TAG, "Test notification received")
                // Handle test notification
            }
        }
    }


    // Implement this method based on your authentication system
    private fun getCurrentUserEmail(): String? {
        // Return the current user's email
        // This could be from FirebaseAuth, SharedPreferences, etc.
        return com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.email
    }

    private fun getCurrentUserId(): String? {
        // Return the current user's ID
        // This could be from FirebaseAuth, SharedPreferences, etc.
        return com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid
    }

    // Method to manually test notifications
    fun testNotification() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val userEmail = getCurrentUserEmail() ?: return@launch

                val requestBody = JSONObject().apply {
                    put("userEmail", userEmail)
                    put("title", "🧪 Test Notification")
                    put("body", "This is a test notification from PuzzleVerse!")
                }.toString()

                val request = Request.Builder()
                    .url("$SERVER_URL/test-notification")
                    .post(requestBody.toRequestBody("application/json".toMediaType()))
                    .addHeader("Content-Type", "application/json")
                    .build()

                val response = httpClient.newCall(request).execute()

                if (response.isSuccessful) {
                    Log.d(TAG, "Test notification sent successfully")
                } else {
                    Log.e(TAG, "Failed to send test notification: ${response.code}")
                }

                response.close()

            } catch (e: Exception) {
                Log.e(TAG, "Error sending test notification", e)
            }
        }
    }

}

@Composable
fun DeepLinkLoadingScreen() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(RvGrape, RvViolet)
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            androidx.compose.material3.CircularProgressIndicator(
                modifier = Modifier.size(48.dp),
                color = RvInk,
                strokeWidth = 4.dp
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.loading_puzzle),
                fontSize = 18.sp,
                fontWeight = FontWeight.Medium,
                color = RvInk
            )
        }
    }
}

@Composable
fun WelcomeScreen(onGetStartedClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(RvGrape, RvViolet)
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier.padding(32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Replace with Image if needed
            Icon(
                imageVector = Icons.Default.CheckCircle,
                contentDescription = null,
                modifier = Modifier.size(120.dp),
                tint = RvInk
            )

            Spacer(modifier = Modifier.height(32.dp))

            Card(
                shape = RoundedCornerShape(30.dp),
                elevation = CardDefaults.cardElevation(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "RIDDLE VERSE",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        color = RvGrape
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = "🧩 CREATE • SOLVE • CONQUER 🏆",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        color = RvGrape
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = "Craft mind-bending puzzles,\nchallenge friends worldwide &\ndominate the leaderboards!",
                        fontSize = 14.sp,
                        color = Color.Gray,
                        textAlign = TextAlign.Center,
                        lineHeight = 18.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(40.dp))

            val context = LocalContext.current

            Button(
                onClick = {
                    context.startActivity(Intent(context, WelcomeActivity::class.java))
                },
                shape = CircleShape,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF8A65)),
                modifier = Modifier.size(64.dp)
            ) {
                Icon(Icons.Default.ArrowForward, contentDescription = null, tint = Color.White)
            }
        }
    }
}


@Composable
fun WelcomeScreen() {
    val context = LocalContext.current

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(RvGrape, RvViolet)
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier.padding(32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // ... other content ...

            Button(
                onClick = {
                    context.startActivity(Intent(context, AuthActivity::class.java))
                },
                shape = CircleShape,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF8A65)),
                modifier = Modifier.size(64.dp)
            ) {
                Icon(Icons.Default.ArrowForward, contentDescription = null, tint = Color.White)
            }
        }
    }
}
