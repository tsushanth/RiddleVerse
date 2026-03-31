package com.kreativekoala.riddleverse

import android.app.Activity
import android.app.Application
import android.app.ActivityManager
import android.content.Context
import android.content.ContentValues.TAG
import android.util.Log
import com.google.firebase.BuildConfig
import com.google.firebase.FirebaseApp
import timber.log.Timber
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import android.os.Bundle
import com.google.android.gms.security.ProviderInstaller
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.messaging.FirebaseMessaging
import com.revenuecat.purchases.Purchases
import com.revenuecat.purchases.PurchasesConfiguration
import com.kreativekoala.paywallkit.manager.ExperimentManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MyApplication : Application() {
    companion object {
        private const val TAG = "MyApplication"
        const val NOTIFICATION_CHANNEL_ID = "puzzle_notifications"
    }

    override fun onCreate() {
        super.onCreate()

        // Initialize Firebase first (required early)
        FirebaseApp.initializeApp(this)

        // Initialize RevenueCat (must be on main thread, before any activity accesses it)
        Purchases.configure(PurchasesConfiguration.Builder(this, "goog_ITYXDzvdjnVZQUeqwrwkmNvRIvL").build())

        // Initialize PaywallKit experiment manager
        ExperimentManager.init(this)

        // Initialize TikTok Events SDK
        TikTokHelper.initialize(this)

        // Plant Timber early for logging
        Timber.plant(Timber.DebugTree())

        // Register activity lifecycle callbacks for cleanup
        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
            override fun onActivityStarted(activity: Activity) {}
            override fun onActivityResumed(activity: Activity) {}
            override fun onActivityPaused(activity: Activity) {}
            override fun onActivityStopped(activity: Activity) {}
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}

            override fun onActivityDestroyed(activity: Activity) {
                Log.d("BinderCleanup", "Activity destroyed: ${activity.javaClass.simpleName}")
                forceBinderCleanup()
            }
        })

        // Defer heavy initializations to background thread
        CoroutineScope(Dispatchers.Default).launch {
            try {
                // CRITICAL FIX: Only initialize Firestore and Analytics in main process
                if (isMainProcess()) {
                    Log.d(TAG, "Main process detected - initializing Firestore and Analytics")

                    // Initialize Firestore with offline persistence ONLY in main process
                    try {
                        val settings = FirebaseFirestoreSettings.Builder()
                            .setPersistenceEnabled(true)
                            .build()
                        FirebaseFirestore.getInstance().firestoreSettings = settings
                        Log.d(TAG, "✅ Firestore initialized with offline persistence")
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to initialize Firestore: ${e.message}")
                        // Fall back to Firestore without offline persistence
                        try {
                            val fallbackSettings = FirebaseFirestoreSettings.Builder()
                                .setPersistenceEnabled(false)
                                .build()
                            FirebaseFirestore.getInstance().firestoreSettings = fallbackSettings
                            Log.d(TAG, "✅ Firestore initialized without offline persistence (fallback)")
                        } catch (fallbackError: Exception) {
                            Log.e(TAG, "Failed to initialize Firestore even without persistence: ${fallbackError.message}")
                        }
                    }

                    // Initialize Analytics Manager ONLY in main process
                    try {
                        Log.d(TAG, "Initializing Analytics Manager...")
                        AnalyticsManager.initialize(this@MyApplication)
                        Log.d(TAG, "✅ Analytics Manager initialized successfully")
                    } catch (e: Exception) {
                        Log.e(TAG, "❌ Failed to initialize Analytics Manager", e)
                    }
                } else {
                    Log.d(TAG, "Secondary process detected - skipping Firestore and Analytics initialization")
                }

                // Initialize Google Play Services on background thread
                initializeGooglePlayServices()

                // Create notification channels (can be done in background)
                createNotificationChannels()

                // Initialize FCM (can be deferred)
                initializeFCM()

                Timber.d("Application initialized successfully")
            } catch (e: Exception) {
                Timber.e(e, "Application initialization failed")
                if (BuildConfig.DEBUG) throw e
            }
        }
    }


    private fun initializeGooglePlayServices() {
        // Already running in background thread from onCreate
        try {
            ProviderInstaller.installIfNeeded(this@MyApplication)
            Log.d(TAG, "✅ Google Play Services security provider initialized")
        } catch (e: Exception) {
            Log.w(TAG, "⚠️ Failed to install Google Play Services security provider: ${e.message}")
        }
    }

    /**
     * Check if this is the main application process
     */
    private fun isMainProcess(): Boolean {
        return try {
            val am = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val processes = am.runningAppProcesses
            val mainProcessName = packageName
            val myPid = android.os.Process.myPid()

            val currentProcess = processes?.find { it.pid == myPid }
            val isMain = currentProcess?.processName == mainProcessName

            Log.d(TAG, "Process check - Current: ${currentProcess?.processName}, Main: $mainProcessName, IsMain: $isMain")
            isMain
        } catch (e: Exception) {
            Log.e(TAG, "Error checking process: ${e.message}")
            true // Default to true if we can't determine
        }
    }

    private fun forceBinderCleanup() {
        try {
            // Only clear Firestore cache in main process
            if (isMainProcess()) {
                FirebaseFirestore.getInstance().clearPersistence()
            }

            // Force garbage collection multiple times
            System.gc()
            System.runFinalization()
            Thread.sleep(100)
            System.gc()

            Log.d("BinderCleanup", "Forced comprehensive binder cleanup")
        } catch (e: Exception) {
            Log.e("BinderCleanup", "Error during forced cleanup", e)
        }
    }

    override fun onLowMemory() {
        super.onLowMemory()
        forceBinderCleanup()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channels = listOf(
                NotificationChannel(
                    NOTIFICATION_CHANNEL_ID,
                    "Daily Puzzle Notifications",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Notifications for new daily puzzles"
                    enableLights(true)
                    enableVibration(true)
                    setShowBadge(true)
                },
                NotificationChannel(
                    "test_notifications",
                    "Test Notifications",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Test notifications"
                    enableLights(true)
                    enableVibration(true)
                }
            )

            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            channels.forEach { channel ->
                notificationManager.createNotificationChannel(channel)
                Log.d(TAG, "Created notification channel: ${channel.id}")
            }
        }
    }

    private fun initializeFCM() {
        FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
            if (!task.isSuccessful) {
                Log.w(TAG, "Fetching FCM registration token failed", task.exception)
                return@addOnCompleteListener
            }

            val token = task.result
            Log.d(TAG, "FCM Token: $token")
        }
    }
}