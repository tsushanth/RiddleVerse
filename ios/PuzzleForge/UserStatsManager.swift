//
//  UserStatsManager.swift
//  PuzzleForge
//
//  Created by Sushanth Tiruvaipati on 8/6/25.
//

import Foundation
import FirebaseAuth

// MARK: - UserPuzzleStats Model (missing from ProgressionModels)
struct UserPuzzleStats: Codable {
    let totalPlays: Int
    let wins: Int
    let highScore: Int
    let totalScore: Int
    let topScores: [Int]
    let totalTimeSpentSeconds: Int
    let currentStreak: Int
    let longestStreak: Int
    let lastPlayedTimestamp: Int64
    
    // Computed properties
    var winRate: Float {
        return totalPlays > 0 ? Float(wins) / Float(totalPlays) : 0.0
    }
    
    var averageScore: Int {
        return totalPlays > 0 ? totalScore / totalPlays : 0
    }
    
    var totalTimeSpentHours: Float {
        return Float(totalTimeSpentSeconds) / 3600.0
    }
    
    // Default initializer
    init() {
        self.totalPlays = 0
        self.wins = 0
        self.highScore = 0
        self.totalScore = 0
        self.topScores = []
        self.totalTimeSpentSeconds = 0
        self.currentStreak = 0
        self.longestStreak = 0
        self.lastPlayedTimestamp = 0
    }
    
    init(totalPlays: Int, wins: Int, highScore: Int, totalScore: Int, topScores: [Int], totalTimeSpentSeconds: Int, currentStreak: Int, longestStreak: Int, lastPlayedTimestamp: Int64) {
        self.totalPlays = totalPlays
        self.wins = wins
        self.highScore = highScore
        self.totalScore = totalScore
        self.topScores = topScores
        self.totalTimeSpentSeconds = totalTimeSpentSeconds
        self.currentStreak = currentStreak
        self.longestStreak = longestStreak
        self.lastPlayedTimestamp = lastPlayedTimestamp
    }
}

class UserStatsManager: ObservableObject {
    static let shared = UserStatsManager()
    
    private let userDefaults = UserDefaults.standard
    private let encoder = JSONEncoder()
    private let decoder = JSONDecoder()
    
    private init() {
        migrateUserDataIfNeeded()
    }
    
    // MARK: - User ID Management
    
    private func getUserId() -> String {
        return Auth.auth().currentUser?.uid ?? "guest_user"
    }
    
    private func getKey(for puzzleType: String) -> String {
        return "\(getUserId())_\(puzzleType)"
    }
    
    // MARK: - Core Data Recording Methods
    
    func recordPuzzleCompletion(
        puzzleType: String,
        score: Int,
        timeSpentSeconds: Int,
        isCorrect: Bool,
        difficulty: String
    ) {
        let existingStats = getPuzzleStats(puzzleType: puzzleType)
        
        // Update puzzle-specific stats
        let updatedStats = UserPuzzleStats(
            totalPlays: existingStats.totalPlays + 1,
            wins: isCorrect ? existingStats.wins + 1 : existingStats.wins,
            highScore: max(existingStats.highScore, score),
            totalScore: existingStats.totalScore + score,
            topScores: updateTopScores(existingStats.topScores, newScore: score),
            totalTimeSpentSeconds: existingStats.totalTimeSpentSeconds + Int(TimeInterval(timeSpentSeconds)),
            currentStreak: isCorrect ? existingStats.currentStreak + 1 : 0,
            longestStreak: isCorrect ? max(existingStats.longestStreak, existingStats.currentStreak + 1) : existingStats.longestStreak,
            lastPlayedTimestamp: Int64(Date().timeIntervalSince1970)
        )
        
        // Save updated stats
        savePuzzleStats(puzzleType: puzzleType, stats: updatedStats)
        
        // Update global stats
        updateGlobalStats(score: score, timeSpentSeconds: timeSpentSeconds, isWin: isCorrect)
        
        print("📊 Recorded puzzle completion: \(puzzleType), score: \(score), correct: \(isCorrect)")
    }
    
    func recordSessionData(xpGained: Int, timeSpentSeconds: Int) {
        var globalStats = getGlobalStats()
        globalStats.totalXP += xpGained
        globalStats.totalTimePlayedSeconds += TimeInterval(timeSpentSeconds)
        
        saveGlobalStats(globalStats)
        
        print("💫 Added \(xpGained) XP to user stats")
    }
    
    func recordDailyActivity() {
        let today = Calendar.current.startOfDay(for: Date())
        let todayKey = "daily_activity_\(today.timeIntervalSince1970)"
        
        var dailyData = getDailyActivityData()
        dailyData.hasPlayedToday = true
        dailyData.todayCount += 1
        dailyData.lastActiveDate = DateFormatter.dailyFormat.string(from: Date())
        
        // Check yesterday's activity
        let yesterday = Calendar.current.date(byAdding: .day, value: -1, to: today)!
        let yesterdayKey = "daily_activity_\(yesterday.timeIntervalSince1970)"
        dailyData.hasPlayedYesterday = UserDefaults.standard.bool(forKey: yesterdayKey)
        
        saveDailyActivityData(dailyData)
        UserDefaults.standard.set(true, forKey: todayKey)
        
        print("📅 Updated daily activity: \(dailyData.todayCount) puzzles today")
    }
    
    func recordWeeklyActivity() {
        let calendar = Calendar.current
        let currentWeek = calendar.component(.weekOfYear, from: Date())
        
        var weeklyData = getWeeklyActivityData()
        
        // Reset if new week
        if weeklyData.currentWeek != currentWeek {
            weeklyData.weeklyProgress = 0
            weeklyData.currentWeek = currentWeek
            
            // Calculate time until next reset (Sunday night)
            let nextSunday = calendar.nextDate(after: Date(), matching: DateComponents(weekday: 1), matchingPolicy: .nextTime)!
            weeklyData.timeUntilReset = nextSunday.timeIntervalSince(Date())
        }
        
        weeklyData.weeklyProgress += 1
        saveWeeklyActivityData(weeklyData)
        
        print("🗓️ Updated weekly activity: \(weeklyData.weeklyProgress) puzzles this week")
    }
    
    // MARK: - Data Retrieval Methods
    
    func getAllUserStats() -> [String: UserPuzzleStats] {
        let userId = getUserId()
        var allStats: [String: UserPuzzleStats] = [:]
        
        // Get all keys for this user
        let allKeys = userDefaults.dictionaryRepresentation().keys
            .filter { $0.hasPrefix("\(userId)_") && !$0.contains("global_") && !$0.contains("daily_") && !$0.contains("weekly_") }
        
        for key in allKeys {
            let puzzleType = String(key.dropFirst("\(userId)_".count))
            let stats = getPuzzleStats(puzzleType: puzzleType)
            if stats.totalPlays > 0 {
                allStats[puzzleType] = stats
            }
        }
        
        return allStats
    }
    
    func getPuzzleStats(puzzleType: String) -> UserPuzzleStats {
        let key = getKey(for: puzzleType)
        
        guard let data = userDefaults.data(forKey: key) else {
            return UserPuzzleStats() // Return default stats
        }
        
        do {
            return try decoder.decode(UserPuzzleStats.self, from: data)
        } catch {
            print("❌ Failed to decode stats for \(puzzleType): \(error)")
            return UserPuzzleStats() // Return default stats
        }
    }
    
    func getGlobalStats() -> GlobalUserStats {
        let key = "global_user_stats_\(getUserId())"
        
        if let data = UserDefaults.standard.data(forKey: key),
           let stats = try? JSONDecoder().decode(GlobalUserStats.self, from: data) {
            return stats
        }
        
        // Return default stats for new users
        return GlobalUserStats()
    }
    
    func getDailyActivityData() -> DailyActivityData {
        let key = "daily_activity_data_\(getUserId())"
        let today = DateFormatter.dailyFormat.string(from: Date())
        
        if let data = UserDefaults.standard.data(forKey: key),
           let activityData = try? JSONDecoder().decode(DailyActivityData.self, from: data) {
            
            // Reset counts if it's a new day
            if activityData.lastActiveDate != today {
                return DailyActivityData(
                    todayCount: 0,
                    yesterdayCount: activityData.lastActiveDate == getYesterdayString() ? activityData.todayCount : 0,
                    hasPlayedToday: false,
                    hasPlayedYesterday: activityData.lastActiveDate == getYesterdayString(),
                    lastActiveDate: today
                )
            }
            
            return activityData
        }
        
        return DailyActivityData(lastActiveDate: today)
    }
    
    func getWeeklyActivityData() -> WeeklyActivityData {
        let key = "weekly_activity_data_\(getUserId())"
        let currentWeek = Calendar.current.component(.weekOfYear, from: Date())
        
        if let data = UserDefaults.standard.data(forKey: key),
           let activityData = try? JSONDecoder().decode(WeeklyActivityData.self, from: data) {
            
            // Reset if new week
            if activityData.currentWeek != currentWeek {
                let calendar = Calendar.current
                let nextSunday = calendar.nextDate(after: Date(), matching: DateComponents(weekday: 1), matchingPolicy: .nextTime)!
                let timeUntilReset = nextSunday.timeIntervalSince(Date())
                
                return WeeklyActivityData(
                    weeklyProgress: 0,
                    currentWeek: currentWeek,
                    timeUntilReset: timeUntilReset
                )
            }
            
            return activityData
        }
        
        // Calculate time until next week reset
        let calendar = Calendar.current
        let nextSunday = calendar.nextDate(after: Date(), matching: DateComponents(weekday: 1), matchingPolicy: .nextTime)!
        let timeUntilReset = nextSunday.timeIntervalSince(Date())
        
        return WeeklyActivityData(
            weeklyProgress: 0,
            currentWeek: currentWeek,
            timeUntilReset: timeUntilReset
        )
    }
    
    // MARK: - Achievement System
    
    func isAchievementUnlocked(achievementId: String) -> Bool {
        return UserDefaults.standard.bool(forKey: "achievement_\(achievementId)_\(getUserId())")
    }
    
    func unlockAchievement(achievementId: String) {
        UserDefaults.standard.set(true, forKey: "achievement_\(achievementId)_\(getUserId())")
        UserDefaults.standard.set(Date().timeIntervalSince1970, forKey: "achievement_\(achievementId)_unlocked_at_\(getUserId())")
        
        print("🏆 Achievement unlocked: \(achievementId)")
        
        // Post notification for achievement unlocked
        NotificationCenter.default.post(
            name: NSNotification.Name("AchievementUnlocked"),
            object: nil,
            userInfo: ["achievementId": achievementId]
        )
    }
    
    func getUnlockedAchievements() -> [String] {
        return ProgressionConstants.achievementDefinitions.compactMap { achievement in
            isAchievementUnlocked(achievementId: achievement.id) ? achievement.id : nil
        }
    }
    
    // MARK: - Data Refresh Methods (Added missing methods)
    
    func refreshStats() {
        // Refresh internal caches or reload data if needed
        // For now, this is a no-op since we read from UserDefaults directly
        print("🔄 Refreshing user stats...")
        
        // Validate data integrity
        validateDataIntegrity()
    }
    
    private func validateDataIntegrity() {
        let globalStats = getGlobalStats()
        let allPuzzleStats = getAllUserStats()
        
        // Check if global stats match sum of puzzle stats
        let totalPuzzlePlays = allPuzzleStats.values.reduce(0) { $0 + $1.totalPlays }
        
        if abs(globalStats.totalGamesPlayed - totalPuzzlePlays) > 1 {
            print("⚠️ Data integrity warning: Global games (\(globalStats.totalGamesPlayed)) != sum of puzzle plays (\(totalPuzzlePlays))")
        }
    }
    
    // MARK: - Legacy Methods (for backward compatibility)
    
    /// Get difficulty rating for a puzzle type
    func getDifficultyRating(_ puzzleType: String, difficulty: String) -> String {
        let stats = getPuzzleStats(puzzleType: puzzleType)
        let averageScore = stats.averageScore
        
        let rating: Int
        switch difficulty.lowercased() {
        case "easy":
            rating = min(400, max(50, averageScore / 2))
        case "medium":
            rating = min(400, max(100, averageScore / 2))
        case "hard":
            rating = min(400, max(150, averageScore))
        default:
            rating = 200
        }
        
        return "\(rating)/400"
    }
    
    /// Get formatted statistics for display
    func getFormattedStats(for puzzleType: String) -> (plays: String, winRate: String, avgScore: String, bestStreak: String) {
        let stats = getPuzzleStats(puzzleType: puzzleType)
        
        return (
            plays: "\(stats.totalPlays)",
            winRate: String(format: "%.0f%%", stats.winRate * 100),
            avgScore: "\(stats.averageScore)",
            bestStreak: "\(stats.longestStreak)"
        )
    }
    
    /// Check if user is improving
    func isUserImproving(for puzzleType: String) -> Bool {
        let stats = getPuzzleStats(puzzleType: puzzleType)
        guard stats.totalPlays >= 3 else { return false }
        
        // Simple check: current streak > 0 and recent average is above overall average
        return stats.currentStreak > 0 && stats.topScores.prefix(3).reduce(0, +) / 3 > stats.averageScore
    }
    
    /// Get skill level based on performance
    func getSkillLevel(for puzzleType: String) -> String {
        let stats = getPuzzleStats(puzzleType: puzzleType)
        let avgScore = stats.averageScore
        
        switch avgScore {
        case 80...Int.max: return "Expert"
        case 60..<80: return "Advanced"
        case 40..<60: return "Intermediate"
        case 20..<40: return "Beginner"
        default: return "Novice"
        }
    }
    
    /// Clear all stats (for testing or reset)
    func clearAllStats() {
        let userId = getUserId()
        let allKeys = userDefaults.dictionaryRepresentation().keys
            .filter { $0.hasPrefix("\(userId)_") }
        
        for key in allKeys {
            userDefaults.removeObject(forKey: key)
        }
        
        print("🗑️ Cleared all stats for user: \(userId)")
    }
    
    /// Import stats from external source
    func importStats(puzzleType: String, stats: UserPuzzleStats) {
        savePuzzleStats(puzzleType: puzzleType, stats: stats)
        print("📥 Imported stats for \(puzzleType)")
    }
    
    // MARK: - Helper Methods
    
    private func getYesterdayString() -> String {
        let yesterday = Calendar.current.date(byAdding: .day, value: -1, to: Date())!
        return DateFormatter.dailyFormat.string(from: yesterday)
    }
    
    // MARK: - Data Persistence Methods
    
    private func savePuzzleStats(puzzleType: String, stats: UserPuzzleStats) {
        let key = getKey(for: puzzleType)
        
        do {
            let data = try encoder.encode(stats)
            userDefaults.set(data, forKey: key)
        } catch {
            print("❌ Failed to save stats for \(puzzleType): \(error)")
        }
    }
    
    private func saveGlobalStats(_ stats: GlobalUserStats) {
        let key = "global_user_stats_\(getUserId())"
        
        if let data = try? JSONEncoder().encode(stats) {
            UserDefaults.standard.set(data, forKey: key)
        }
    }
    
    private func saveDailyActivityData(_ data: DailyActivityData) {
        let key = "daily_activity_data_\(getUserId())"
        
        if let encodedData = try? JSONEncoder().encode(data) {
            UserDefaults.standard.set(encodedData, forKey: key)
        }
    }
    
    private func saveWeeklyActivityData(_ data: WeeklyActivityData) {
        let key = "weekly_activity_data_\(getUserId())"
        
        if let encodedData = try? JSONEncoder().encode(data) {
            UserDefaults.standard.set(encodedData, forKey: key)
        }
    }
    
    private func updateGlobalStats(score: Int, timeSpentSeconds: Int, isWin: Bool) {
        var globalStats = getGlobalStats()
        
        globalStats.totalGamesPlayed += 1
        globalStats.totalTimePlayedSeconds += TimeInterval(timeSpentSeconds)
        
        if isWin {
            globalStats.totalXP += score
        }
        
        saveGlobalStats(globalStats)
    }
    
    private func updateTopScores(_ currentTopScores: [Int], newScore: Int) -> [Int] {
        return (currentTopScores + [newScore])
            .sorted(by: >)
            .prefix(10)
            .map { $0 }
    }
    
    // MARK: - Migration and Maintenance
    
    private func migrateUserDataIfNeeded() {
        // Check if migration has been done before
        let migrationKey = "progression_migration_v1_completed_\(getUserId())"
        guard !UserDefaults.standard.bool(forKey: migrationKey) else {
            return
        }
        
        print("🔄 Starting user data migration...")
        
        // Set join date if not set
        var globalStats = getGlobalStats()
        if globalStats.joinDate == 0 {
            globalStats.joinDate = Date().timeIntervalSince1970
            saveGlobalStats(globalStats)
        }
        
        // Mark migration as complete
        UserDefaults.standard.set(true, forKey: migrationKey)
        print("✅ Migration completed successfully")
    }
    
    func resetProgressionData() {
        print("⚠️ Resetting all progression data...")
        
        let userId = getUserId()
        
        // Reset puzzle stats
        let allKeys = userDefaults.dictionaryRepresentation().keys
            .filter { $0.hasPrefix("\(userId)_") }
        
        for key in allKeys {
            UserDefaults.standard.removeObject(forKey: key)
        }
        
        // Reset global stats
        UserDefaults.standard.removeObject(forKey: "global_user_stats_\(userId)")
        
        // Reset activity data
        UserDefaults.standard.removeObject(forKey: "daily_activity_data_\(userId)")
        UserDefaults.standard.removeObject(forKey: "weekly_activity_data_\(userId)")
        
        // Reset achievements
        for achievement in ProgressionConstants.achievementDefinitions {
            UserDefaults.standard.removeObject(forKey: "achievement_\(achievement.id)_\(userId)")
            UserDefaults.standard.removeObject(forKey: "achievement_\(achievement.id)_unlocked_at_\(userId)")
        }
        
        // Reset streaks
        UserDefaults.standard.removeObject(forKey: "current_streak")
        UserDefaults.standard.removeObject(forKey: "best_streak")
        UserDefaults.standard.removeObject(forKey: "daily_streak")
        UserDefaults.standard.removeObject(forKey: "best_daily_streak")
        
        // Reset migration flag
        UserDefaults.standard.removeObject(forKey: "progression_migration_v1_completed_\(userId)")
        
        print("✅ All progression data reset")
    }
}
