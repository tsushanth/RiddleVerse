package com.kreativekoala.riddleverse

import android.app.Application
import android.util.Log
import com.revenuecat.purchases.Purchases
import com.revenuecat.purchases.PurchasesConfiguration
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * Configures RevenueCat off the main thread.
 *
 * Play vitals ANR clusters for 174-184 are dominated by `MyApplication.onCreate` blocked inside
 * `Purchases.configure` (class init, DeviceCache/SharedPreferences, Blockstore, BillingClient build),
 * which holds the main thread during cold start (and during background process starts, e.g. the
 * datatransport JobInfoSchedulerService) on slow or I/O-contended devices.
 *
 * Purchases.configure is safe off the main thread: its main-thread-only work (lifecycle observer,
 * callbacks) is re-dispatched to the main looper by the SDK itself.
 *
 * All work that needs the SDK goes through this object so nothing can see an unconfigured SDK:
 *  - [whenReady] queues the block behind the configure task on the same single thread (non-blocking
 *    for the caller; use it for automatic/startup work).
 *  - [instance] returns the SDK, waiting (bounded) only if configure has not finished yet; use it
 *    for user-initiated calls, which in practice happen long after startup.
 */
object RevenueCatBootstrap {
    private const val TAG = "RevenueCatBootstrap"
    private const val API_KEY = "goog_ITYXDzvdjnVZQUeqwrwkmNvRIvL"
    private const val WAIT_SECONDS = 15L

    private val configured = CountDownLatch(1)
    private val executor = Executors.newSingleThreadExecutor { r ->
        Thread(r, "rc-configure").apply { isDaemon = true }
    }
    @Volatile private var started = false

    /** Call once from Application.onCreate. Returns immediately. */
    @Synchronized
    fun start(app: Application) {
        if (started) return
        started = true
        executor.execute {
            try {
                if (!Purchases.isConfigured) {
                    Purchases.configure(PurchasesConfiguration.Builder(app, API_KEY).build())
                }
            } catch (t: Throwable) {
                Log.e(TAG, "Purchases.configure failed", t)
            } finally {
                configured.countDown()
            }
        }
    }

    /** The configured SDK. Blocks (up to [WAIT_SECONDS]) only if configure is still running. */
    val instance: Purchases
        get() {
            if (!Purchases.isConfigured) {
                try { configured.await(WAIT_SECONDS, TimeUnit.SECONDS) } catch (_: InterruptedException) { Thread.currentThread().interrupt() }
            }
            return Purchases.sharedInstance
        }

    /**
     * Runs [block] on the configure thread once the SDK is configured; never blocks the caller.
     * [onFailure] runs (on that thread) if the SDK is unavailable or [block] throws.
     */
    fun whenReady(onFailure: (() -> Unit)? = null, block: (Purchases) -> Unit) {
        try {
            executor.execute {
                try {
                    block(Purchases.sharedInstance)
                } catch (t: Throwable) {
                    Log.w(TAG, "RevenueCat call failed: ${t.message}")
                    try { onFailure?.invoke() } catch (_: Throwable) { }
                }
            }
        } catch (t: Throwable) {
            try { onFailure?.invoke() } catch (_: Throwable) { }
        }
    }
}
