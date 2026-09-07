package com.kreativekoala.riddleverse

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.CommonStatusCodes
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.messaging.FirebaseMessaging
import com.revenuecat.purchases.Purchases
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import timber.log.Timber

class AuthActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var googleSignInClient: GoogleSignInClient
    private var isSignInInProgress = false
    private var analyticsManager: AnalyticsManager? = null
    private var sessionManager: AnalyticsSessionManager? = null
    private var signInStartTime: Long = 0

    companion object {
        private const val TAG = "GoogleSignIn"
        private const val RC_SIGN_IN = 9001
    }

    private fun initializeAnalyticsSafely() {
        try {
            // Try to get existing instance first
            if (AnalyticsManager.isInitialized()) {
                analyticsManager = AnalyticsManager.getInstance()
                sessionManager = AnalyticsSessionManager.getInstance()
                Log.d(TAG, "✅ Analytics initialized from existing instance")
            } else {
                // Try to initialize with current context
                analyticsManager = AnalyticsManager.getInstance()
                sessionManager = AnalyticsSessionManager.getInstance()
                Log.d(TAG, "✅ Analytics initialized with context")
            }

            // Track screen view and session
            sessionManager?.logScreenViewIfNeeded("auth_screen")
            sessionManager?.logAppOpenIfNeeded()
            sessionManager?.logSessionStartIfNeeded()

        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to initialize analytics", e)
            // Create null instances - app will continue without analytics
            analyticsManager = null
            sessionManager = null
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        initializeAnalyticsSafely()
        if (!FirebaseApp.getApps(this).isEmpty()) {
            FirebaseApp.initializeApp(this)
        }
        Timber.d("Firebase apps: ${FirebaseApp.getApps(this)}")
        setContentView(R.layout.activity_auth)

        auth = FirebaseAuth.getInstance()

        // Only auto-redirect if NOT coming from a specific activity
        val isComingFromLogin = intent?.getBooleanExtra("from_login", false) == true
        if (auth.currentUser != null) {
            val user = auth.currentUser

            safeTrackAnalytics {
                // TRACK AUTOMATIC ACTIVE USER
                analyticsManager?.track(AnalyticsEvent("active_user", mapOf(
                    "user_id" to (user?.uid ?: "unknown"),
                    "user_email" to (user?.email ?: "unknown"),
                    "method" to "automatic",
                    "is_new_user" to false,
                    "session_id" to (sessionManager?.getCurrentSessionId() ?: "unknown"),
                    "login_type" to "cached_credentials"
                )))

                analyticsManager?.track(AnalyticsEvent.userLogin("automatic"))
                analyticsManager?.setUserId(user?.uid ?: "unknown")
            }
            val isComingFromLogin = intent?.getBooleanExtra("from_login", false) == true
            if (!isComingFromLogin) {
                navigateToMainWithOnboardingCheck()
                return
            }
            navigateToMainWithOnboardingCheck()
            return
        }


        // Check for cached sign-in
        if (auth.currentUser != null) {
            navigateToMainWithOnboardingCheck()
            return
        }

        // Configure Google Sign-In with proper error handling
        try {
            val webClientId = getString(R.string.default_web_client_id).takeIf { it.isNotBlank() }
                ?: throw IllegalStateException("Web Client ID is missing or empty")

            val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken(webClientId)
                .requestEmail()
                .build()

            googleSignInClient = GoogleSignIn.getClient(this, gso)
        } catch (e: Exception) {
            Log.e(TAG, "Google Sign-In configuration failed", e)
            Toast.makeText(this, "Sign-in configuration error", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        findViewById<Button>(R.id.googleSignInButton).setOnClickListener {
            if (!isSignInInProgress) {
                safeTrackAnalytics {
                    sessionManager?.logUserActionIfNeeded(
                        action = "google_signin_button_tap",
                        context = "auth_screen"
                    )

                    analyticsManager?.track(AnalyticsEvent("signin_attempt", mapOf(
                        "method" to "google",
                        "source" to "auth_screen",
                        "user_type" to if (isReturningUser()) "returning" else "new"
                    )))
                }
                startGoogleSignIn()
            }
        }
    }


    private fun safeTrackAnalytics(block: () -> Unit) {
        try {
            if (analyticsManager != null && sessionManager != null) {
                block()
            } else {
                Log.w(TAG, "Analytics not available, skipping tracking")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Analytics tracking failed", e)
        }
    }

    private fun startGoogleSignIn() {
        isSignInInProgress = true
        try {
            val signInIntent = googleSignInClient.signInIntent
            startActivityForResult(signInIntent, RC_SIGN_IN)
            safeTrackAnalytics {
                analyticsManager?.track(AnalyticsEvent("signin_flow_started", mapOf(
                    "method" to "google",
                    "timestamp" to signInStartTime
                )))
            }
        } catch (e: Exception) {
            isSignInInProgress = false
            Log.e(TAG, "Failed to launch sign-in intent", e)
            Toast.makeText(this, "Sign-in initialization failed", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode != RC_SIGN_IN) return

        isSignInInProgress = false

        try {
            val task = GoogleSignIn.getSignedInAccountFromIntent(data)
            val account = task.getResult(ApiException::class.java)
            account?.idToken?.let { firebaseAuthWithGoogle(it) }
        } catch (e: ApiException) {
            handleSignInError(e)
        }
    }

    private fun firebaseAuthWithGoogle(idToken: String) {
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        auth.signInWithCredential(credential)
            .addOnCompleteListener(this) { task ->
                if (task.isSuccessful) {
                    val user = auth.currentUser
                    val isNewUser = task.result?.additionalUserInfo?.isNewUser ?: false

                    // SET USER ID FOR ANALYTICS
                    safeTrackAnalytics {
                        // SET USER ID FOR ANALYTICS
                        user?.uid?.let { userId ->
                            analyticsManager?.setUserId(userId)
                        }

                        // TRACK ACTIVE USER - THIS IS THE KEY METRIC!
                        analyticsManager?.track(AnalyticsEvent("active_user", mapOf(
                            "user_id" to (user?.uid ?: "unknown"),
                            "user_email" to (user?.email ?: "unknown"),
                            "method" to "google",
                            "is_new_user" to isNewUser,
                            "auth_duration_ms" to 5,
                            "session_id" to (sessionManager?.getCurrentSessionId() ?: "unknown"),
                            "device_model" to android.os.Build.MODEL,
                            "app_version" to getAppVersion(),
                            "login_timestamp" to System.currentTimeMillis()
                        )))

                        // Track user type specifically
                        if (isNewUser) {
                            analyticsManager?.track(AnalyticsEvent.userRegistration("google"))
                            analyticsManager?.track(AnalyticsEvent("new_active_user", mapOf(
                                "user_id" to (user?.uid ?: "unknown"),
                                "registration_method" to "google",
                                "onboarding_required" to true
                            )))
                        } else {
                            analyticsManager?.track(AnalyticsEvent("returning_active_user", mapOf(
                                "user_id" to (user?.uid ?: "unknown"),
                                "method" to "google",
                                "last_sign_in" to (user?.metadata?.lastSignInTimestamp ?: 0),
                                "days_since_last_login" to calculateDaysSinceLastLogin(user)
                            )))
                        }

                        // Track successful login
                        analyticsManager?.track(AnalyticsEvent.userLogin("google"))
                    }

                    // Set RevenueCat subscriber attributes for segmentation
                    user?.uid?.let { uid ->
                        Purchases.sharedInstance.logIn(
                            uid,
                            object : com.revenuecat.purchases.interfaces.LogInCallback {
                                override fun onReceived(customerInfo: com.revenuecat.purchases.CustomerInfo, created: Boolean) {
                                    Log.d(TAG, "RevenueCat logIn success after auth, created=$created")
                                }
                                override fun onError(error: com.revenuecat.purchases.PurchasesError) {
                                    Log.e(TAG, "RevenueCat logIn error: ${error.message}")
                                }
                            }
                        )
                        setRevenueCatAttributes(isNewUser)
                    }

                    navigateToMainWithOnboardingCheck()
                } else {
                    Log.w(TAG, "signInWithCredential:failure", task.exception)
                    Toast.makeText(this, "Firebase authentication failed", Toast.LENGTH_SHORT).show()
                }
            }
    }

    private fun isReturningUser(): Boolean {
        val prefs = getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        return prefs.getBoolean("onboarding_completed", false) ||
                prefs.getBoolean("has_used_app_before", false)
    }

    private fun calculateDaysSinceLastLogin(user: com.google.firebase.auth.FirebaseUser?): Int {
        val lastSignIn = user?.metadata?.lastSignInTimestamp ?: return 0
        val daysDiff = (System.currentTimeMillis() - lastSignIn) / (24 * 60 * 60 * 1000)
        return daysDiff.toInt()
    }

    private fun getAppVersion(): String {
        return try {
            packageManager.getPackageInfo(packageName, 0).versionName ?: "1.0.0"
        } catch (e: Exception) {
            "1.0.0"
        }
    }

    private fun handleSignInError(e: ApiException) {
        Log.w(TAG, "Google sign-in failed", e)
        safeTrackAnalytics {
            analyticsManager?.track(AnalyticsEvent("active_user_attempt_failed", mapOf(
                "method" to "google",
                "error_code" to e.statusCode,
                "error_message" to (e.message ?: "unknown"),
                "session_id" to (sessionManager?.getCurrentSessionId() ?: "unknown")
            )))
        }
        when (e.statusCode) {
            CommonStatusCodes.CANCELED -> {
                safeTrackAnalytics {
                    analyticsManager?.track(AnalyticsEvent("signin_cancelled", mapOf(
                        "method" to "google",
                        "cancellation_point" to "google_signin_dialog"
                    )))
                }
                Log.d(TAG, "User canceled sign-in")
                // No need to show toast for user-initiated cancellation
            }
            CommonStatusCodes.DEVELOPER_ERROR -> {
                // Status code 10 - Configuration error
                Toast.makeText(this, "Configuration error. Please check:\n1. Web Client ID\n2. SHA-1 fingerprints\n3. Firebase configuration", Toast.LENGTH_LONG).show()
                Log.e(TAG, "DEVELOPER_ERROR (10) - Check configuration:\n" +
                        "1. Verify R.string.default_web_client_id matches Firebase Console\n" +
                        "2. Ensure SHA-1 fingerprints are registered\n" +
                        "3. Check google-services.json is up-to-date")
            }
            else -> {
                Toast.makeText(this, "Sign-in failed: ${e.statusCode}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun navigateToMain() {
        val intent = Intent(this, HomeActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }

    private fun navigateToMainWithOnboardingCheck() {
        val prefs = getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        val onboardingCompleted = prefs.getBoolean("onboarding_completed", false)

        if (!onboardingCompleted) {
            // First time user - show onboarding
            val intent = Intent(this, WelcomeOnboardingActivity::class.java)
            startActivity(intent)
        } else {
            // Returning user - go straight to home
            navigateToMain() // Use your existing method
        }
        finish()
    }


    /**
     * Set RevenueCat subscriber attributes for acquisition source segmentation.
     * These are used by RevenueCat Experiments, Charts, and the ad-optimizer
     * to correlate ad spend with actual revenue per campaign/source.
     */
    private fun setRevenueCatAttributes(isNewUser: Boolean) {
        try {
            val purchases = Purchases.sharedInstance
            val user = auth.currentUser

            // Standard RevenueCat reserved attributes
            user?.email?.let { purchases.setEmail(it) }
            user?.displayName?.let { purchases.setDisplayName(it) }

            // Acquisition source — check stored install referrer data
            val prefs = getSharedPreferences("install_attribution", Context.MODE_PRIVATE)
            val mediaSource = prefs.getString("media_source", null)
            val campaign = prefs.getString("campaign", null)
            val adGroup = prefs.getString("ad_group", null)
            val creative = prefs.getString("creative", null)

            val attrs = mutableMapOf<String, String>()

            // $mediaSource is a RevenueCat reserved attribute for attribution
            attrs["\$mediaSource"] = mediaSource ?: "organic"
            if (campaign != null) attrs["\$campaign"] = campaign
            if (adGroup != null) attrs["\$adGroup"] = adGroup
            if (creative != null) attrs["\$creative"] = creative

            // Custom attributes for our ad-optimizer integration
            attrs["is_new_user"] = isNewUser.toString()
            attrs["auth_method"] = "google"
            attrs["app_version"] = getAppVersion()

            purchases.setAttributes(attrs)
            Log.d(TAG, "RevenueCat attributes set: mediaSource=${mediaSource ?: "organic"}, campaign=$campaign")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to set RevenueCat attributes", e)
        }
    }

    fun signOut() {
        auth.signOut()
        Purchases.sharedInstance.logOut()
        googleSignInClient.signOut().addOnCompleteListener(this) {
            // Restart auth activity
            val intent = Intent(this, AuthActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            finish()
        }
    }
}