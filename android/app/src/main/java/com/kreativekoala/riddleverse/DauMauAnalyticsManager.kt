package com.kreativekoala.riddleverse

import android.content.Context
import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.*
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

// MARK: - DAU/MAU Analytics Manager
class DauMauAnalyticsManager private constructor() {

    companion object {
        private const val TAG = "DauMauAnalytics"

        @Volatile
        private var INSTANCE: DauMauAnalyticsManager? = null

        fun getInstance(): DauMauAnalyticsManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: DauMauAnalyticsManager().also { INSTANCE = it }
            }
        }
    }

    private val firestore = FirebaseFirestore.getInstance()
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())



    // MARK: - Track Daily Active User
    fun trackDailyActiveUser(userId: String = getCurrentUserId()) {
        if (userId.isEmpty()) return

        scope.launch {
            try {
                val today = getCurrentDateString()
                val dauDocument = firestore
                    .collection("daily_active_users")
                    .document(today)

                // Use merge to avoid overwriting existing data
                dauDocument.set(
                    mapOf(
                        "users" to mapOf(userId to mapOf(
                            "last_seen" to System.currentTimeMillis(),
                            "session_count" to com.google.firebase.firestore.FieldValue.increment(1)
                        )),
                        "date" to today,
                        "timestamp" to System.currentTimeMillis()
                    ),
                    com.google.firebase.firestore.SetOptions.merge()
                ).addOnSuccessListener {
                    Log.d(TAG, "✅ DAU tracked for user $userId on $today")
                }.addOnFailureListener { e ->
                    Log.e(TAG, "❌ Failed to track DAU: ${e.message}")
                }

            } catch (e: Exception) {
                Log.e(TAG, "❌ Exception tracking DAU: ${e.message}", e)
            }
        }
    }

    // MARK: - Track Monthly Active User
    fun trackMonthlyActiveUser(userId: String = getCurrentUserId()) {
        if (userId.isEmpty()) return

        scope.launch {
            try {
                val currentMonth = getCurrentMonthString()
                val mauDocument = firestore
                    .collection("monthly_active_users")
                    .document(currentMonth)

                mauDocument.set(
                    mapOf(
                        "users" to mapOf(userId to mapOf(
                            "last_seen" to System.currentTimeMillis(),
                            "days_active" to com.google.firebase.firestore.FieldValue.increment(1),
                            "total_sessions" to com.google.firebase.firestore.FieldValue.increment(1)
                        )),
                        "month" to currentMonth,
                        "timestamp" to System.currentTimeMillis()
                    ),
                    com.google.firebase.firestore.SetOptions.merge()
                ).addOnSuccessListener {
                    Log.d(TAG, "✅ MAU tracked for user $userId in $currentMonth")
                }.addOnFailureListener { e ->
                    Log.e(TAG, "❌ Failed to track MAU: ${e.message}")
                }

            } catch (e: Exception) {
                Log.e(TAG, "❌ Exception tracking MAU: ${e.message}", e)
            }
        }
    }

    // MARK: - Get DAU Count
    fun getDauCount(
        date: String = getCurrentDateString(),
        onResult: (Int) -> Unit
    ) {
        firestore.collection("daily_active_users")
            .document(date)
            .get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    val users = document.get("users") as? Map<String, Any>
                    val count = users?.size ?: 0
                    onResult(count)
                    Log.d(TAG, "📊 DAU for $date: $count users")
                } else {
                    onResult(0)
                    Log.d(TAG, "📊 No DAU data for $date")
                }
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "❌ Failed to get DAU count: ${e.message}")
                onResult(0)
            }
    }

    // MARK: - Get MAU Count
    fun getMauCount(
        month: String = getCurrentMonthString(),
        onResult: (Int) -> Unit
    ) {
        firestore.collection("monthly_active_users")
            .document(month)
            .get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    val users = document.get("users") as? Map<String, Any>
                    val count = users?.size ?: 0
                    onResult(count)
                    Log.d(TAG, "📊 MAU for $month: $count users")
                } else {
                    onResult(0)
                    Log.d(TAG, "📊 No MAU data for $month")
                }
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "❌ Failed to get MAU count: ${e.message}")
                onResult(0)
            }
    }

    // MARK: - Get DAU Trend (Last 30 Days)
    fun getDauTrend(onResult: (List<DauMauDataPoint>) -> Unit) {
        val dataPoints = mutableListOf<DauMauDataPoint>()
        val calendar = Calendar.getInstance()
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)

        scope.launch {
            try {
                val deferred = mutableListOf<Deferred<DauMauDataPoint?>>()

                // Get last 30 days
                for (i in 29 downTo 0) {
                    val date = Calendar.getInstance().apply {
                        add(Calendar.DAY_OF_YEAR, -i)
                    }
                    val dateString = dateFormat.format(date.time)

                    val deferred_result = async {
                        try {
                            val document = firestore.collection("daily_active_users")
                                .document(dateString)
                                .get()
                                .await()

                            val users = document.get("users") as? Map<String, Any>
                            val count = users?.size ?: 0

                            DauMauDataPoint(
                                date = dateString,
                                count = count,
                                timestamp = date.timeInMillis
                            )
                        } catch (e: Exception) {
                            Log.w(TAG, "Failed to get DAU for $dateString: ${e.message}")
                            DauMauDataPoint(
                                date = dateString,
                                count = 0,
                                timestamp = date.timeInMillis
                            )
                        }
                    }
                    deferred.add(deferred_result)
                }

                val results = deferred.awaitAll().filterNotNull().sortedBy { it.timestamp }

                withContext(Dispatchers.Main) {
                    onResult(results)
                    Log.d(TAG, "📈 DAU trend loaded: ${results.size} data points")
                }

            } catch (e: Exception) {
                Log.e(TAG, "❌ Failed to get DAU trend: ${e.message}")
                withContext(Dispatchers.Main) {
                    onResult(emptyList())
                }
            }
        }
    }

    // MARK: - Get MAU Trend (Last 12 Months)
    fun getMauTrend(onResult: (List<DauMauDataPoint>) -> Unit) {
        scope.launch {
            try {
                val deferred = mutableListOf<Deferred<DauMauDataPoint?>>()
                val monthFormat = SimpleDateFormat("yyyy-MM", Locale.US)

                // Get last 12 months
                for (i in 11 downTo 0) {
                    val date = Calendar.getInstance().apply {
                        add(Calendar.MONTH, -i)
                    }
                    val monthString = monthFormat.format(date.time)

                    val deferred_result = async {
                        try {
                            val document = firestore.collection("monthly_active_users")
                                .document(monthString)
                                .get()
                                .await()

                            val users = document.get("users") as? Map<String, Any>
                            val count = users?.size ?: 0

                            DauMauDataPoint(
                                date = monthString,
                                count = count,
                                timestamp = date.timeInMillis
                            )
                        } catch (e: Exception) {
                            Log.w(TAG, "Failed to get MAU for $monthString: ${e.message}")
                            DauMauDataPoint(
                                date = monthString,
                                count = 0,
                                timestamp = date.timeInMillis
                            )
                        }
                    }
                    deferred.add(deferred_result)
                }

                val results = deferred.awaitAll().filterNotNull().sortedBy { it.timestamp }

                withContext(Dispatchers.Main) {
                    onResult(results)
                    Log.d(TAG, "📈 MAU trend loaded: ${results.size} data points")
                }

            } catch (e: Exception) {
                Log.e(TAG, "❌ Failed to get MAU trend: ${e.message}")
                withContext(Dispatchers.Main) {
                    onResult(emptyList())
                }
            }
        }
    }

    // MARK: - Advanced Analytics
    fun getRetentionMetrics(onResult: (RetentionMetrics) -> Unit) {
        scope.launch {
            try {
                val sevenDaysAgo = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(7)
                val thirtyDaysAgo = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(30)

                // Get users who were active 7 days ago
                val sevenDayUsers = mutableSetOf<String>()
                val thirtyDayUsers = mutableSetOf<String>()
                val currentUsers = mutableSetOf<String>()

                // Query recent analytics events to find active users
                firestore.collection("puzzle_analytics")
                    .whereGreaterThan("timestamp", thirtyDaysAgo)
                    .orderBy("timestamp", Query.Direction.DESCENDING)
                    .limit(10000)
                    .get()
                    .await()
                    .documents
                    .forEach { doc ->
                        val userId = doc.getString("user_id") ?: return@forEach
                        val timestamp = doc.getLong("timestamp") ?: return@forEach

                        when {
                            timestamp >= sevenDaysAgo -> {
                                currentUsers.add(userId)
                                sevenDayUsers.add(userId)
                                thirtyDayUsers.add(userId)
                            }
                            timestamp >= thirtyDaysAgo -> {
                                thirtyDayUsers.add(userId)
                            }
                        }
                    }

                val sevenDayRetention = if (sevenDayUsers.isNotEmpty()) {
                    (currentUsers.intersect(sevenDayUsers).size.toDouble() / sevenDayUsers.size) * 100
                } else 0.0

                val thirtyDayRetention = if (thirtyDayUsers.isNotEmpty()) {
                    (currentUsers.intersect(thirtyDayUsers).size.toDouble() / thirtyDayUsers.size) * 100
                } else 0.0

                val metrics = RetentionMetrics(
                    sevenDayRetention = sevenDayRetention,
                    thirtyDayRetention = thirtyDayRetention,
                    currentActiveUsers = currentUsers.size,
                    sevenDayActiveUsers = sevenDayUsers.size,
                    thirtyDayActiveUsers = thirtyDayUsers.size
                )

                withContext(Dispatchers.Main) {
                    onResult(metrics)
                    Log.d(TAG, "📊 Retention metrics: 7-day: ${sevenDayRetention}%, 30-day: ${thirtyDayRetention}%")
                }

            } catch (e: Exception) {
                Log.e(TAG, "❌ Failed to get retention metrics: ${e.message}")
                withContext(Dispatchers.Main) {
                    onResult(RetentionMetrics())
                }
            }
        }
    }

    // MARK: - Query Active Users by Date Range
    fun getActiveUsersByDateRange(
        startDate: String,
        endDate: String,
        onResult: (Set<String>) -> Unit
    ) {
        scope.launch {
            try {
                val startTimestamp = SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(startDate)?.time ?: 0L
                val endTimestamp = SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(endDate)?.time ?: System.currentTimeMillis()

                val activeUsers = mutableSetOf<String>()

                firestore.collection("puzzle_analytics")
                    .whereGreaterThanOrEqualTo("timestamp", startTimestamp)
                    .whereLessThanOrEqualTo("timestamp", endTimestamp)
                    .get()
                    .await()
                    .documents
                    .forEach { doc ->
                        val userId = doc.getString("user_id")
                        if (!userId.isNullOrEmpty() && userId != "anonymous") {
                            activeUsers.add(userId)
                        }
                    }

                withContext(Dispatchers.Main) {
                    onResult(activeUsers)
                    Log.d(TAG, "📊 Active users from $startDate to $endDate: ${activeUsers.size}")
                }

            } catch (e: Exception) {
                Log.e(TAG, "❌ Failed to get active users by date range: ${e.message}")
                withContext(Dispatchers.Main) {
                    onResult(emptySet())
                }
            }
        }
    }

    // MARK: - Helper Methods
    private fun getCurrentUserId(): String {
        return FirebaseAuth.getInstance().currentUser?.uid ?: "anonymous"
    }

    private fun getCurrentDateString(): String {
        return SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
    }

    private fun getCurrentMonthString(): String {
        return SimpleDateFormat("yyyy-MM", Locale.US).format(Date())
    }

    // MARK: - Batch Update for Efficiency
    fun trackUserActivityInternal(userId: String = getCurrentUserId()) {
        // Track both DAU and MAU in one call
        trackDailyActiveUser(userId)
        trackMonthlyActiveUser(userId)

        // Also track in your existing analytics - with null safety
        try {
            AnalyticsManager.getInstance()?.track(
                AnalyticsEvent("user_active", mapOf(
                    "user_id" to userId,
                    "date" to getCurrentDateString(),
                    "month" to getCurrentMonthString(),
                    "timestamp" to System.currentTimeMillis()
                ))
            ) ?: Log.w("DauMauAnalytics", "AnalyticsManager not available for user_active event")
        } catch (e: Exception) {
            Log.e("DauMauAnalytics", "Failed to track user_active event: ${e.message}")
        }
    }


    fun trackUserActivity() {
        val analyticsManager = AnalyticsManager.getInstance()
        if (analyticsManager != null) {
            DauMauAnalyticsManager.getInstance()?.trackUserActivityInternal()
        } else {
            Log.w("Analytics", "AnalyticsManager not initialized, tracking DAU/MAU only")
            DauMauAnalyticsManager.getInstance()?.trackUserActivityInternal()
        }
    }
}

// MARK: - Data Classes
data class DauMauDataPoint(
    val date: String,
    val count: Int,
    val timestamp: Long
)

data class RetentionMetrics(
    val sevenDayRetention: Double = 0.0,
    val thirtyDayRetention: Double = 0.0,
    val currentActiveUsers: Int = 0,
    val sevenDayActiveUsers: Int = 0,
    val thirtyDayActiveUsers: Int = 0
)

data class AnalyticsOverview(
    val dauToday: Int = 0,
    val dauYesterday: Int = 0,
    val dauGrowth: Double = 0.0,
    val mauThisMonth: Int = 0,
    val mauLastMonth: Int = 0,
    val mauGrowth: Double = 0.0,
    val totalUsers: Int = 0,
    val newUsersToday: Int = 0,
    val averageSessionDuration: Double = 0.0,
    val retentionMetrics: RetentionMetrics = RetentionMetrics()
)

// MARK: - Analytics Dashboard Manager
class AnalyticsDashboardManager {

    companion object {
        fun getAnalyticsOverview(onResult: (AnalyticsOverview) -> Unit) {
            val dauManager = DauMauAnalyticsManager.getInstance()
            var overview = AnalyticsOverview()
            var completedCalls = 0
            val totalCalls = 6

            fun checkComplete() {
                completedCalls++
                if (completedCalls >= totalCalls) {
                    onResult(overview)
                }
            }

            // Get today's DAU
            dauManager.getDauCount { dauToday ->
                overview = overview.copy(dauToday = dauToday)
                checkComplete()
            }

            // Get yesterday's DAU for growth calculation
            val yesterday = Calendar.getInstance().apply {
                add(Calendar.DAY_OF_YEAR, -1)
            }
            val yesterdayString = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(yesterday.time)

            dauManager.getDauCount(yesterdayString) { dauYesterday ->
                val growth = if (dauYesterday > 0) {
                    ((overview.dauToday - dauYesterday).toDouble() / dauYesterday) * 100
                } else 0.0
                overview = overview.copy(
                    dauYesterday = dauYesterday,
                    dauGrowth = growth
                )
                checkComplete()
            }

            // Get this month's MAU
            dauManager.getMauCount { mauThisMonth ->
                overview = overview.copy(mauThisMonth = mauThisMonth)
                checkComplete()
            }

            // Get last month's MAU for growth calculation
            val lastMonth = Calendar.getInstance().apply {
                add(Calendar.MONTH, -1)
            }
            val lastMonthString = SimpleDateFormat("yyyy-MM", Locale.US).format(lastMonth.time)

            dauManager.getMauCount(lastMonthString) { mauLastMonth ->
                val growth = if (mauLastMonth > 0) {
                    ((overview.mauThisMonth - mauLastMonth).toDouble() / mauLastMonth) * 100
                } else 0.0
                overview = overview.copy(
                    mauLastMonth = mauLastMonth,
                    mauGrowth = growth
                )
                checkComplete()
            }

            // Get retention metrics
            dauManager.getRetentionMetrics { retention ->
                overview = overview.copy(retentionMetrics = retention)
                checkComplete()
            }

            // Get new users today (you can implement this based on user creation dates)
            getNewUsersToday { newUsers ->
                overview = overview.copy(newUsersToday = newUsers)
                checkComplete()
            }
        }

        private fun getNewUsersToday(onResult: (Int) -> Unit) {
            // Implement based on your user registration tracking
            // This is a placeholder - you'd query user creation timestamps
            val today = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())

            FirebaseFirestore.getInstance()
                .collection("users") // Adjust collection name as needed
                .whereGreaterThanOrEqualTo("createdAt", today)
                .get()
                .addOnSuccessListener { snapshot ->
                    onResult(snapshot.size())
                }
                .addOnFailureListener {
                    onResult(0)
                }
        }
    }
}