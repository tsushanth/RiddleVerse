package com.kreativekoala.riddleverse

import android.content.Context
import org.json.JSONObject
import timber.log.Timber

/**
 * TikTok Business SDK integration for install attribution and event tracking.
 * Mirrors the iOS TikTokHelper.swift implementation.
 */
object TikTokHelper {
    private const val TAG = "TikTokHelper"
    private const val APP_ID = "7610536667425865746"

    /**
     * Called once from [AppStartup] (background thread) at app launch.
     */
    fun initialize(context: Context) {
        try {
            val config = com.tiktok.TikTokBusinessSdk.TTConfig(context)
                .setAppId(APP_ID)
                .setTTAppId(APP_ID)
                .setLogLevel(com.tiktok.TikTokBusinessSdk.LogLevel.INFO)

            com.tiktok.TikTokBusinessSdk.initializeSdk(config)
            Timber.d("TikTok SDK initialized with appId: $APP_ID")
        } catch (e: Exception) {
            Timber.e(e, "Failed to initialize TikTok SDK")
        }
    }

    /**
     * Store attribution data so RevenueCat subscriber attributes can be set.
     * Call after TikTok SDK init when attribution callback fires.
     */
    fun storeAttribution(context: Context, source: String, campaign: String? = null, adGroup: String? = null, creative: String? = null) {
        val prefs = context.getSharedPreferences("install_attribution", Context.MODE_PRIVATE)
        prefs.edit()
            .putString("media_source", source)
            .apply { campaign?.let { putString("campaign", it) } }
            .apply { adGroup?.let { putString("ad_group", it) } }
            .apply { creative?.let { putString("creative", it) } }
            .apply()
        Timber.d("Attribution stored: source=$source, campaign=$campaign")
    }

    /**
     * Track a custom event with optional properties.
     */
    fun trackEvent(eventName: String, properties: Map<String, Any> = emptyMap()) {
        // The SDK is initialised on the startup thread: queue behind it so an early event is neither lost nor sent to an
        // uninitialised SDK, and keep the SDK call off the main thread.
        AppStartup.post {
            if (AppStartup.ensureTikTok()) trackEventNow(eventName, properties)
        }
    }

    private fun trackEventNow(eventName: String, properties: Map<String, Any>) {
        try {
            if (properties.isEmpty()) {
                com.tiktok.TikTokBusinessSdk.trackEvent(eventName)
            } else {
                val jsonProps = JSONObject(properties)
                com.tiktok.TikTokBusinessSdk.trackEvent(eventName, jsonProps)
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to track TikTok event: $eventName")
        }
    }
}
