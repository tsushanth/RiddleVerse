package com.kreativekoala.riddleverse

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import com.google.gson.Gson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlin.math.sqrt

class PuzzleViewModel : ViewModel() {
    // Existing state properties
    private var _currentPuzzleIndex = 0
    private var _totalPuzzles = 1
    private var _score = 0
    private var _startTime = System.currentTimeMillis()
    private var _puzzles = emptyList<Puzzle>()
    var screenType: PuzzleScreenType = PuzzleScreenType.QA
    private var _customPuzzleSet: PuzzleData? = null
    private var _localScore = 0
    private var _leaderboard = emptyList<LeaderboardEntry>()

    private var _targetPuzzleCount = 5  // For AI-generated puzzles
    private var _currentPuzzleNumber = 1  // For AI-generated puzzles
    private var _isCustomPuzzleFlow = false  // Determines which flow to use

    private var _totalScore = mutableIntStateOf(0)
    val totalScore: Int get() = _totalScore.intValue

    // 🚀 NEW: Enhanced score and timing tracking
    private var _currentPuzzleScore = mutableIntStateOf(0)
    private var _sessionScore = mutableIntStateOf(0)
    private var _correctAnswers = mutableIntStateOf(0)
    private var _totalAnswers = mutableIntStateOf(0)

    // Timing tracking
    private var _sessionStartTime = mutableLongStateOf(System.currentTimeMillis())
    private var _currentPuzzleStartTime = mutableLongStateOf(System.currentTimeMillis())
    private var _individualPuzzleTimes = mutableListOf<Int>()

    // Performance tracking
    private var _streakCount = mutableIntStateOf(0)
    private var _bestStreakThisSession = mutableIntStateOf(0)

    // Existing UI state
    var showHintDialog by mutableStateOf(false)
        private set
    var selectedOption by mutableStateOf("")
        private set

    // 🚀 NEW: Getters for score and timing data
    fun getCurrentScore(): Int = _sessionScore.intValue
    fun getCurrentPuzzleScore(): Int = _currentPuzzleScore.intValue
    fun getCorrectAnswerCount(): Int = _correctAnswers.intValue
    fun getTotalAnswerCount(): Int = _totalAnswers.intValue
    fun getSessionStartTime(): Long = _sessionStartTime.longValue
    fun getCurrentPuzzleStartTime(): Long = _currentPuzzleStartTime.longValue
    fun getSessionTimeSeconds(): Int = ((System.currentTimeMillis() - _sessionStartTime.longValue) / 1000).toInt()
    fun getCurrentPuzzleTimeSeconds(): Int = ((System.currentTimeMillis() - _currentPuzzleStartTime.longValue) / 1000).toInt()
    fun getStreakCount(): Int = _streakCount.intValue
    fun getBestStreakThisSession(): Int = _bestStreakThisSession.intValue

    // 🚀 NEW: Score calculation based on difficulty, time, and correctness
    fun calculatePuzzleScore(difficulty: String, timeSpentSeconds: Int, isCorrect: Boolean, inTime: Boolean): Int {
        if (!isCorrect) return 0

        val basePoints = when (difficulty.lowercase()) {
            "easy" -> 50
            "medium" -> 100
            "hard" -> 150
            "expert" -> 200
            else -> 100
        }

        // Time bonus (faster completion = more points)
        val timeBonus = when {
            timeSpentSeconds <= 10 -> (basePoints * 0.6).toInt() // 60% bonus for very fast
            timeSpentSeconds <= 20 -> (basePoints * 0.4).toInt() // 40% bonus for fast
            timeSpentSeconds <= 30 -> (basePoints * 0.2).toInt() // 20% bonus for good time
            timeSpentSeconds <= 45 -> (basePoints * 0.1).toInt() // 10% bonus for decent time
            else -> 0 // No bonus for slow completion
        }

        // In-time penalty/bonus
        val inTimeModifier = if (inTime) 0 else -(basePoints * 0.15).toInt() // -15% penalty for overtime

        // Streak bonus
        val streakBonus = when {
            _streakCount.intValue >= 10 -> (basePoints * 0.3).toInt() // 30% bonus for 10+ streak
            _streakCount.intValue >= 5 -> (basePoints * 0.2).toInt()  // 20% bonus for 5+ streak
            _streakCount.intValue >= 3 -> (basePoints * 0.1).toInt()  // 10% bonus for 3+ streak
            else -> 0
        }

        val finalScore = basePoints + timeBonus + inTimeModifier + streakBonus

        Log.d("PuzzleViewModel", "🧮 Score calculation:")
        Log.d("PuzzleViewModel", "  Base: $basePoints ($difficulty)")
        Log.d("PuzzleViewModel", "  Time bonus: $timeBonus (${timeSpentSeconds}s)")
        Log.d("PuzzleViewModel", "  In-time modifier: $inTimeModifier")
        Log.d("PuzzleViewModel", "  Streak bonus: $streakBonus (${_streakCount.intValue} streak)")
        Log.d("PuzzleViewModel", "  Final: $finalScore")

        return maxOf(10, finalScore) // Minimum 10 points for any correct answer
    }

    // 🚀 NEW: Update score when puzzle is completed
    fun updateScore(points: Int) {
        _currentPuzzleScore.intValue = points
        _sessionScore.intValue += points
        _totalScore.intValue += points
        Log.d("PuzzleViewModel", "📊 Score updated: +$points = ${_sessionScore.intValue} session, ${_totalScore.intValue} total")
    }

    // 🚀 NEW: Reset current puzzle timing and score
    fun startNewPuzzle() {
        _currentPuzzleStartTime.longValue = System.currentTimeMillis()
        _currentPuzzleScore.intValue = 0
        Log.d("PuzzleViewModel", "⏰ New puzzle started at ${_currentPuzzleStartTime.longValue}")
    }

    // 🚀 NEW: Record individual puzzle completion time
    private fun recordPuzzleTime() {
        val puzzleTime = getCurrentPuzzleTimeSeconds()
        _individualPuzzleTimes.add(puzzleTime)
        Log.d("PuzzleViewModel", "⏱️ Puzzle completed in ${puzzleTime}s")
    }

    // 🚀 NEW: Get performance statistics for session
    fun getSessionStats(): SessionStatistics {
        val totalTime = getSessionTimeSeconds()
        val avgTimePerPuzzle = if (_totalAnswers.intValue > 0) totalTime / _totalAnswers.intValue else 0
        val winRate = if (_totalAnswers.intValue > 0) _correctAnswers.intValue.toFloat() / _totalAnswers.intValue.toFloat() else 0f

        return SessionStatistics(
            totalScore = _sessionScore.intValue,
            correctAnswers = _correctAnswers.intValue,
            totalAnswers = _totalAnswers.intValue,
            winRate = winRate,
            totalTimeSeconds = totalTime,
            averageTimePerPuzzle = avgTimePerPuzzle,
            bestStreak = _bestStreakThisSession.intValue,
            currentStreak = _streakCount.intValue,
            individualTimes = _individualPuzzleTimes.toList()
        )
    }

    // 🚀 NEW: Calculate consistency score for performance analysis
    fun getConsistencyScore(): Double {
        if (_individualPuzzleTimes.size < 2) return 1.0

        val times = _individualPuzzleTimes.map { it.toDouble() }
        val average = times.average()
        val variance = times.map { (it - average) * (it - average) }.average()
        val standardDeviation = sqrt(variance)

        // Lower standard deviation relative to mean = higher consistency (0-1 scale)
        return maxOf(0.0, 1.0 - (standardDeviation / average))
    }

    // Existing properties with getters
    val hasMoreCustomPuzzles: Boolean get() = _currentPuzzleIndex < _puzzles.size
    val isLastAIPuzzle: Boolean get() = _currentPuzzleNumber >= _targetPuzzleCount
    val isCustomPuzzleFlow: Boolean get() = _isCustomPuzzleFlow
    val targetPuzzleCount: Int get() = _targetPuzzleCount
    val currentPuzzleNumber: Int get() = _currentPuzzleNumber

    // 🔧 UPDATED: Initialize with enhanced timing
    fun initializeFromIntent(intent: Intent, context: Context) {
        Log.d("PuzzleViewModel", "🎯 initializeFromIntent called")

        try {
            _localScore = UserScoreStore.load(context)

            // Initialize session timing
            _sessionStartTime.longValue = intent.getLongExtra("sessionStartTime", System.currentTimeMillis())
            _currentPuzzleStartTime.longValue = intent.getLongExtra("questionStartTime", System.currentTimeMillis())

            // Restore cumulative session stats from previous puzzle activities
            _correctAnswers.intValue = intent.getIntExtra("cumulativeCorrectAnswers", 0)
            _totalAnswers.intValue = intent.getIntExtra("cumulativeTotalAnswers", 0)
            _sessionScore.intValue = intent.getIntExtra("cumulativeSessionScore", 0)
            _bestStreakThisSession.intValue = intent.getIntExtra("cumulativeBestStreak", 0)
            _streakCount.intValue = intent.getIntExtra("cumulativeCurrentStreak", 0)

            Log.d("PuzzleViewModel", "📊 Restored cumulative stats: correct=${_correctAnswers.intValue}/${_totalAnswers.intValue}, score=${_sessionScore.intValue}, bestStreak=${_bestStreakThisSession.intValue}")

            val puzzleJson = intent.getStringExtra("puzzle")
            val puzzleListJson = intent.getStringExtra("puzzleList")

            Log.d("PuzzleViewModel", "📄 Intent analysis:")
            Log.d("PuzzleViewModel", "   puzzleJson: ${if (puzzleJson != null) "Present (AI-generated)" else "NULL"}")
            Log.d("PuzzleViewModel", "   puzzleListJson: ${if (puzzleListJson != null) "Present (Custom)" else "NULL"}")

            _currentPuzzleIndex = intent.getIntExtra("puzzleIndex", 0)
            val customPuzzleSetJson = intent.getStringExtra("customPuzzleSet")
            _score = intent.getIntExtra("score", 0)
            _startTime = intent.getLongExtra("startTime", System.currentTimeMillis())
            val screenTypeStr = intent.getStringExtra("screenType") ?: "qa"

            screenType = PuzzleScreenType.fromString(screenTypeStr)
            _customPuzzleSet = customPuzzleSetJson?.let { Gson().fromJson(it, PuzzleData::class.java) }
            _leaderboard = _customPuzzleSet?.leaderboard ?: emptyList()

            if (puzzleListJson != null) {
                // CUSTOM PUZZLE FLOW
                Log.d("PuzzleViewModel", "🎨 Using CUSTOM PUZZLE flow")
                _isCustomPuzzleFlow = true
                _puzzles = Gson().fromJson(puzzleListJson, Array<Puzzle>::class.java).toList()
                _totalPuzzles = _puzzles.size
                Log.d("PuzzleViewModel", "✅ Custom puzzle list: ${_puzzles.size} puzzles")
                Log.d("PuzzleViewModel", "📊 Current: ${currentQuestionNumber} of ${totalQuestions}")

            } else if (puzzleJson != null) {
                // AI-GENERATED PUZZLE FLOW
                Log.d("PuzzleViewModel", "🤖 Using AI-GENERATED PUZZLE flow")
                _isCustomPuzzleFlow = false
                _puzzles = listOf(Gson().fromJson(puzzleJson, Puzzle::class.java))
                _totalPuzzles = 1 // Always 1 for AI flow since we fetch individually

                // AI puzzle tracking
                _targetPuzzleCount = intent.getIntExtra("targetPuzzleCount", 5)
                _currentPuzzleNumber = intent.getIntExtra("currentPuzzleNumber", 1)

                Log.d("PuzzleViewModel", "✅ AI puzzle loaded")
                Log.d("PuzzleViewModel", "📊 Progress: ${_currentPuzzleNumber} of ${_targetPuzzleCount}")

            } else {
                Log.w("PuzzleViewModel", "⚠️ No puzzle data found in intent!")
            }

            Log.d("PuzzleViewModel", "🎯 Final state:")
            Log.d("PuzzleViewModel", "   Flow type: ${if (_isCustomPuzzleFlow) "CUSTOM" else "AI-GENERATED"}")
            Log.d("PuzzleViewModel", "   Screen type: $screenType")
            Log.d("PuzzleViewModel", "   Session start: ${_sessionStartTime.longValue}")

        } catch (e: Exception) {
            Log.e("PuzzleViewModel", "❌ Error in initializeFromIntent", e)
            throw e
        }
    }

    // Existing getters
    fun getCurrentPuzzle(): Puzzle? = _puzzles.getOrNull(_currentPuzzleIndex)
    val localScore: Int get() = _localScore
    val leaderboard: List<LeaderboardEntry> get() = _leaderboard
    val currentQuestionNumber: Int get() = _currentPuzzleIndex + 1
    val totalQuestions: Int get() = _totalPuzzles

    // Existing actions
    fun moveToNextCustomPuzzle(): Boolean {
        if (!_isCustomPuzzleFlow) {
            Log.w("PuzzleViewModel", "⚠️ moveToNextCustomPuzzle called on AI flow!")
            return false
        }
        _currentPuzzleIndex++
        Log.d("PuzzleViewModel", "📈 Moved to custom puzzle ${currentQuestionNumber} of ${totalQuestions}")

        // 🚀 NEW: Start timing for next puzzle
        if (_currentPuzzleIndex < _totalPuzzles) {
            startNewPuzzle()
        }

        return _currentPuzzleIndex < _totalPuzzles
    }

    fun moveToNextAIPuzzle() {
        if (_isCustomPuzzleFlow) {
            Log.w("PuzzleViewModel", "⚠️ moveToNextAIPuzzle called on custom flow!")
            return
        }
        _currentPuzzleNumber++
        Log.d("PuzzleViewModel", "📈 Moved to AI puzzle ${_currentPuzzleNumber} of ${_targetPuzzleCount}")

        // 🚀 NEW: Start timing for next puzzle
        startNewPuzzle()
    }

    fun addScore(points: Int) {
        _score += points
        _localScore += points
    }

    fun toggleHintDialog() {
        showHintDialog = !showHintDialog
    }

    fun updateSelectedOption(option: String) {
        selectedOption = option
    }

    // 🔧 UPDATED: Enhanced puzzle completion with scoring and stats
    fun handlePuzzleCompletion(context: Context, isCorrect: Boolean, inTime: Boolean = true) {
        // MEDIA CLEANUP - Do this first to avoid blocking the flow
        CoroutineScope(Dispatchers.IO + SupervisorJob()).launch {
            try {
                val currentPuzzleId = getCurrentPuzzle()?.puzzleId
                if (currentPuzzleId != null) {
                    Log.d("PuzzleViewModel", "🧹 Cleaning up media for completed puzzle: $currentPuzzleId")
                    MediaCacheManager.getInstance(context).cleanupPuzzleMedia(currentPuzzleId)
                    Log.d("PuzzleViewModel", "✅ Media cleanup completed for puzzle: $currentPuzzleId")
                } else {
                    Log.w("PuzzleViewModel", "⚠️ No puzzle ID found for media cleanup")
                }
            } catch (e: Exception) {
                Log.e("PuzzleViewModel", "❌ Error during media cleanup", e)
                // Don't let cleanup errors affect puzzle completion flow
            }
        }

        // Record timing
        recordPuzzleTime()
        _totalAnswers.intValue++

        if (isCorrect) {
            _correctAnswers.intValue++
            _streakCount.intValue++

            // Update best streak
            if (_streakCount.intValue > _bestStreakThisSession.intValue) {
                _bestStreakThisSession.intValue = _streakCount.intValue
            }

            // Calculate and award score
            val currentPuzzle = getCurrentPuzzle()
            val difficulty = currentPuzzle?.difficulty ?: "Medium"
            val timeSpent = getCurrentPuzzleTimeSeconds()

            val puzzleScore = calculatePuzzleScore(difficulty, timeSpent, isCorrect, inTime)
            updateScore(puzzleScore)

            // Don't add to global score for custom puzzles (existing logic)
            if (!_isCustomPuzzleFlow) {
                addScore(10) // Only add to global score for AI puzzles
                if (inTime) addScore(2)
                UserScoreStore.save(context, _localScore + 12)
            }

            Log.d("PuzzleViewModel", "✅ Puzzle completed successfully:")
            Log.d("PuzzleViewModel", "  Score: +$puzzleScore")
            Log.d("PuzzleViewModel", "  Time: ${timeSpent}s")
            Log.d("PuzzleViewModel", "  Streak: ${_streakCount.intValue}")

            // Record in UserStatsManager
            try {
                val puzzleType = currentPuzzle?.puzzleType ?: "unknown"
                UserStatsManager.getInstance(context).recordPuzzleCompletion(
                    puzzleType = puzzleType,
                    score = puzzleScore,
                    difficulty = difficulty,
                    timeSpentSeconds = timeSpent,
                    isWin = true
                )
                Log.d("PuzzleViewModel", "📊 Stats recorded in UserStatsManager")
            } catch (e: Exception) {
                Log.e("PuzzleViewModel", "❌ Failed to record stats", e)
            }

        } else {
            // Reset streak on incorrect answer
            _streakCount.intValue = 0

            // Still record the attempt in UserStatsManager
            try {
                val currentPuzzle = getCurrentPuzzle()
                val puzzleType = currentPuzzle?.puzzleType ?: "unknown"
                val difficulty = currentPuzzle?.difficulty ?: "Medium"
                val timeSpent = getCurrentPuzzleTimeSeconds()

                UserStatsManager.getInstance(context).recordPuzzleCompletion(
                    puzzleType = puzzleType,
                    score = 0,
                    difficulty = difficulty,
                    timeSpentSeconds = timeSpent,
                    isWin = false
                )
                Log.d("PuzzleViewModel", "📊 Failed attempt recorded in UserStatsManager")
            } catch (e: Exception) {
                Log.e("PuzzleViewModel", "❌ Failed to record failed attempt", e)
            }

            Log.d("PuzzleViewModel", "❌ Puzzle failed - streak reset")
        }

        // Analytics tracking
        try {
            AnalyticsManager.getInstance()?.track(AnalyticsEvent("puzzle_completed_enhanced", mapOf(
                "is_correct" to isCorrect,
                "in_time" to inTime,
                "score_earned" to if (isCorrect) _currentPuzzleScore.intValue else 0,
                "time_spent" to getCurrentPuzzleTimeSeconds(),
                "streak" to _streakCount.intValue,
                "session_score" to _sessionScore.intValue,
                "puzzle_type" to (getCurrentPuzzle()?.puzzleType ?: "unknown"),
                "difficulty" to (getCurrentPuzzle()?.difficulty ?: "unknown")
            )))
        } catch (e: Exception) {
            Log.e("PuzzleViewModel", "❌ Analytics tracking failed", e)
        }
    }

    // Existing methods
    fun getNextPuzzleIntent(context: Context): Intent? {
        Log.w("PuzzleViewModel", "⚠️ getNextPuzzleIntent called - consider using flow-specific methods!")
        return if (_isCustomPuzzleFlow) {
            getNextCustomPuzzleIntent(context)
        } else {
            null
        }
    }

    fun getNextCustomPuzzleIntent(context: Context): Intent? {
        if (!_isCustomPuzzleFlow) {
            Log.w("PuzzleViewModel", "⚠️ getNextCustomPuzzleIntent called on AI flow!")
            return null
        }

        _currentPuzzleIndex++
        Log.d("PuzzleViewModel", "📈 Moved to custom puzzle ${currentQuestionNumber} of ${totalQuestions}")

        return if (_currentPuzzleIndex < _totalPuzzles) {
            Log.d("PuzzleViewModel", "🔄 Creating intent for next custom puzzle")
            Intent(context, PuzzleActivity::class.java).apply {
                putExtra("puzzleList", Gson().toJson(_puzzles))
                putExtra("puzzleIndex", _currentPuzzleIndex)
                putExtra("customPuzzleSet", Gson().toJson(_customPuzzleSet))
                putExtra("score", _score)
                putExtra("startTime", _startTime)
                putExtra("screenType", screenType.typeName)
                putExtra("isCustomPuzzle", true)

                // Add timing data
                putExtra("sessionStartTime", _sessionStartTime.longValue)
                putExtra("questionStartTime", System.currentTimeMillis()) // New puzzle starts now

                // Carry cumulative session stats to next activity
                putExtra("cumulativeCorrectAnswers", _correctAnswers.intValue)
                putExtra("cumulativeTotalAnswers", _totalAnswers.intValue)
                putExtra("cumulativeSessionScore", _sessionScore.intValue)
                putExtra("cumulativeBestStreak", _bestStreakThisSession.intValue)
                putExtra("cumulativeCurrentStreak", _streakCount.intValue)

                val currentActivity = context as? PuzzleActivity
                val originalCustomPuzzleId = currentActivity?.intent?.getStringExtra("customPuzzleId")
                putExtra("customPuzzleId", originalCustomPuzzleId)

                Log.d("PuzzleViewModel", "🎨 Preserving custom puzzle flags:")
                Log.d("PuzzleViewModel", "  isCustomPuzzle: true")
                Log.d("PuzzleViewModel", "  customPuzzleId: $originalCustomPuzzleId")
            }
        } else {
            Log.d("PuzzleViewModel", "🏁 No more custom puzzles - completed all ${_totalPuzzles}")
            null
        }
    }
}

// 🚀 NEW: Data class for session statistics
data class SessionStatistics(
    val totalScore: Int,
    val correctAnswers: Int,
    val totalAnswers: Int,
    val winRate: Float,
    val totalTimeSeconds: Int,
    val averageTimePerPuzzle: Int,
    val bestStreak: Int,
    val currentStreak: Int,
    val individualTimes: List<Int>
)