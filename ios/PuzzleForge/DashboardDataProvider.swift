//
//  DashboardDataProvider.swift
//  PuzzleForge
//
//  Created by Sushanth Tiruvaipati on 8/6/25.
//

import Foundation

class DashboardDataProvider: ObservableObject {
    private let userStatsManager: UserStatsManager
    private let progressionEngine: ProgressionEngine
    
    init(userStatsManager: UserStatsManager = .shared, progressionEngine: ProgressionEngine = ProgressionEngine()) {
        self.userStatsManager = userStatsManager
        self.progressionEngine = progressionEngine
    }
    
    // MARK: - Main Dashboard Data
    
    func getDashboardData() -> DashboardData {
        print("📊 Preparing dashboard data")
        
        return DashboardData(
            tierInfo: getTierInfo(),
            levelInfo: getUserLevel(),
            streakInfo: getStreakDisplayInfo(),
            weeklyChallenge: getWeeklyChallenge(),
            achievements: getAchievementsData(),
            puzzlePerformance: getPuzzlePerformanceData(),
            timeAnalytics: getTimeAnalyticsData(),
            seasonalEvent: getCurrentSeasonalEventInfo(),
            dailyStats: getDailyActivityStats(),
            globalStats: getGlobalStatsData()
        )
    }
    
    // MARK: - Tier Information
    
    func getTierInfo() -> TierInfo {
        let currentLevel = progressionEngine.getCurrentLevel()
        let tier = determineTier(level: currentLevel.level, xp: currentLevel.totalXP)
        let nextTier = determineNextTier(currentTier: tier)
        
        return TierInfo(
            currentTier: tier,
            nextTier: nextTier,
            pointsInTier: currentLevel.totalXP % 1000, // Simplified logic
            pointsToNextTier: 1000, // Simplified logic
            progressPercentage: Float(currentLevel.totalXP % 1000) / 1000.0,
            nextTierRewards: getNextTierRewards(tier: nextTier),
            currentLevel: currentLevel.level,
            totalXP: currentLevel.totalXP
        )
    }
    
    func getUserLevel() -> UserLevel {
        return progressionEngine.getCurrentLevel()
    }
    
    // MARK: - Weekly Challenge
    
    func getWeeklyChallenge() -> WeeklyChallengeInfo {
        return progressionEngine.getWeeklyChallengeInfo()
    }
    
    // MARK: - Achievement System
    
    func getAchievementsData() -> [Achievement] {
        // Get achievements from progression engine
        return progressionEngine.getAllAchievements()
    }
    
    // MARK: - Streak Information
    
    private func getStreakDisplayInfo() -> StreakDisplayInfo {
        let questionStreak = progressionEngine.getCurrentStreak()
        let bestStreak = progressionEngine.getBestStreak()
        let dailyStreak = progressionEngine.getCurrentDailyStreak()
        let dailyData = userStatsManager.getDailyActivityData()
        
        return StreakDisplayInfo(
            currentQuestionStreak: questionStreak,
            bestQuestionStreak: bestStreak,
            currentDailyStreak: dailyStreak,
            longestDailyStreak: getBestDailyStreakFromPrefs(),
            hasPlayedToday: dailyData.hasPlayedToday,
            streakActive: questionStreak > 0 || dailyStreak > 0,
            questionStreakMultiplier: getQuestionStreakMultiplier(streak: questionStreak),
            dailyStreakBonus: getDailyStreakBonus(dailyStreak: dailyStreak),
            streakMessage: generateStreakMessage(questionStreak: questionStreak, dailyStreak: dailyStreak, hasPlayedToday: dailyData.hasPlayedToday)
        )
    }
    
    private func getBestDailyStreakFromPrefs() -> Int {
        return UserDefaults.standard.integer(forKey: "best_daily_streak")
    }
    
    private func getQuestionStreakMultiplier(streak: Int) -> Float {
        switch streak {
        case 10...: return 2.0
        case 5..<10: return 1.5
        case 3..<5: return 1.25
        default: return 1.0
        }
    }
    
    private func getDailyStreakBonus(dailyStreak: Int) -> Float {
        switch dailyStreak {
        case 30...: return 2.0
        case 14..<30: return 1.75
        case 7..<14: return 1.5
        case 3..<7: return 1.25
        default: return 1.0
        }
    }
    
    private func generateStreakMessage(questionStreak: Int, dailyStreak: Int, hasPlayedToday: Bool) -> String {
        switch true {
        case dailyStreak >= 30:
            return "🔥 Incredible! \(dailyStreak) days strong!"
        case dailyStreak >= 7:
            return "🎉 Amazing streak! \(dailyStreak) days!"
        case questionStreak >= 10:
            return "⚡ You're unstoppable! \(questionStreak) in a row!"
        case questionStreak >= 3:
            return "🚀 On fire! \(questionStreak) correct answers!"
        case hasPlayedToday:
            return "✨ Great job playing today!"
        case dailyStreak > 0:
            return "💪 Keep your \(dailyStreak)-day streak alive!"
        default:
            return "🎯 Start your streak today!"
        }
    }
    
    // MARK: - Puzzle Performance Data
    
    private func getPuzzlePerformanceData() -> [PuzzlePerformanceInfo] {
        let allStats = userStatsManager.getAllUserStats()
        
        return allStats.compactMap { (puzzleType, stats) in
            let averageTime = stats.totalPlays > 0 ? Float(stats.totalTimeSpentSeconds) / Float(stats.totalPlays) : 0
            
            return PuzzlePerformanceInfo(
                puzzleType: formatPuzzleTypeName(puzzleType: puzzleType),
                totalSolved: stats.wins,
                totalPlayed: stats.totalPlays,
                accuracy: stats.winRate,
                averageTime: averageTime,
                highScore: stats.highScore,
                totalTime: stats.totalTimeSpentHours,
                recentActivity: formatLastPlayed(timestamp: TimeInterval(stats.lastPlayedTimestamp)),
                progressStatus: calculateProgressStatus(stats: stats),
                difficultyRecommendation: recommendDifficulty(stats: stats),
                improvementTip: generateImprovementTip(puzzleType: puzzleType, stats: stats)
            )
        }.sorted { $0.totalSolved > $1.totalSolved }
    }
    
    private func formatPuzzleTypeName(puzzleType: String) -> String {
        return puzzleType.replacingOccurrences(of: "([a-z])([A-Z])", with: "$1 $2", options: .regularExpression)
            .capitalized
            .replacingOccurrences(of: "wordsearch", with: "Word Search")
            .replacingOccurrences(of: "wordsnake", with: "Word Snake")
    }
    
    private func calculateProgressStatus(stats: UserPuzzleStats) -> String {
        switch true {
        case stats.totalPlays == 0:
            return "Not Started"
        case stats.winRate >= 0.9:
            return "Mastered"
        case stats.winRate >= 0.75:
            return "Advanced"
        case stats.winRate >= 0.5:
            return "Improving"
        default:
            return "Learning"
        }
    }
    
    private func recommendDifficulty(stats: UserPuzzleStats) -> String {
        switch true {
        case stats.winRate >= 0.85 && stats.totalPlays >= 10:
            return "Hard"
        case stats.winRate >= 0.7 && stats.totalPlays >= 5:
            return "Medium"
        case stats.winRate < 0.6:
            return "Easy"
        default:
            return "Medium"
        }
    }
    
    private func generateImprovementTip(puzzleType: String, stats: UserPuzzleStats) -> String {
        switch true {
        case stats.totalPlays == 0:
            return "Try your first \(formatPuzzleTypeName(puzzleType: puzzleType)) puzzle!"
        case stats.winRate < 0.5:
            return "Practice makes perfect! Try easier difficulty."
        case stats.winRate >= 0.85:
            return "You're excelling! Ready for harder challenges?"
        default:
            return "Keep practicing to improve your accuracy!"
        }
    }
    
    // MARK: - Time Analytics Data
    
    private func getTimeAnalyticsData() -> TimeAnalyticsInfo {
        let globalStats = userStatsManager.getGlobalStats()
        let allStats = userStatsManager.getAllUserStats()
        
        let averageSessionTime = globalStats.totalGamesPlayed > 0 ?
            Float(globalStats.totalTimePlayedSeconds) / Float(globalStats.totalGamesPlayed) : 0
        
        let fastestPuzzleTypeEntry = allStats.min { (first, second) in
            let firstAvg = first.value.totalPlays > 0 ? Float(first.value.totalTimeSpentSeconds) / Float(first.value.totalPlays) : Float.greatestFiniteMagnitude
            let secondAvg = second.value.totalPlays > 0 ? Float(second.value.totalTimeSpentSeconds) / Float(second.value.totalPlays) : Float.greatestFiniteMagnitude
            return firstAvg < secondAvg
        }
        
        let fastestPuzzleTypeName = fastestPuzzleTypeEntry != nil ? formatPuzzleTypeName(puzzleType: fastestPuzzleTypeEntry!.key) : "None"
        
        let productiveHours = getProductiveHours()
        
        return TimeAnalyticsInfo(
            totalHours: globalStats.totalTimePlayedHours,
            averageSessionTime: averageSessionTime,
            totalSessions: globalStats.totalGamesPlayed,
            fastestPuzzleType: fastestPuzzleTypeName,
            mostProductiveHour: productiveHours.0,
            dailyAverageMinutes: calculateDailyAverageMinutes(),
            timeDistribution: getTimeDistribution(),
            efficiency: calculateEfficiency(),
            timeMessage: generateTimeMessage(totalHours: globalStats.totalTimePlayedHours)
        )
    }
    
    private func getProductiveHours() -> (String, String) {
        // This would need hourly tracking to implement fully
        // For now, return generic productive hours
        return ("7-9 PM", "Most active time")
    }
    
    private func calculateDailyAverageMinutes() -> Float {
        let globalStats = userStatsManager.getGlobalStats()
        let daysSinceJoin = max(Float((Date().timeIntervalSince1970 - globalStats.joinDate) / (24 * 60 * 60)), 1.0)
        return Float(globalStats.totalTimePlayedSeconds / 60.0) / daysSinceJoin
    }
    
    private func getTimeDistribution() -> [String: Float] {
        let allStats = userStatsManager.getAllUserStats()
        let totalTime = allStats.values.reduce(0) { $0 + $1.totalTimeSpentSeconds }
        
        guard totalTime > 0 else { return [:] }
        
        var distribution: [String: Float] = [:]
        for (puzzleType, stats) in allStats {
            let formattedName = formatPuzzleTypeName(puzzleType: puzzleType)
            let percentage = Float(stats.totalTimeSpentSeconds) / Float(totalTime) * 100.0
            distribution[formattedName] = percentage
        }
        
        return distribution
    }
    
    private func calculateEfficiency() -> Float {
        let globalStats = userStatsManager.getGlobalStats()
        let allStats = userStatsManager.getAllUserStats()
        
        guard !allStats.isEmpty && globalStats.totalTimePlayedSeconds > 0 else { return 0 }
        
        let avgWinRate = Float(allStats.values.map { $0.winRate }.reduce(0, +) / Float(allStats.count))
        let avgXPPerMinute = min(Float(globalStats.totalXP) / Float(globalStats.totalTimePlayedSeconds / 60.0), 100.0)
        
        return min((avgWinRate * 50.0) + (avgXPPerMinute * 0.5), 100.0)
    }
    
    private func generateTimeMessage(totalHours: Float) -> String {
        let days = Int(totalHours / 24)
        let hours = Int(totalHours.truncatingRemainder(dividingBy: 24))
        
        switch totalHours {
        case 100...:
            return "🏆 Over \(days) days of brain training!"
        case 24..<100:
            return "🎯 \(days) days and \(hours)h of mental exercise!"
        case 10..<24:
            return "🧠 \(Int(totalHours)) hours of puzzle solving!"
        case 1..<10:
            return "⚡ \(Int(totalHours)) hours of cognitive training!"
        default:
            return "🚀 Start your puzzle journey!"
        }
    }
    
    // MARK: - Seasonal Events
    
    func getCurrentSeasonalEventInfo() -> SeasonalEventInfo? {
        let currentMonth = Calendar.current.component(.month, from: Date())
        
        switch currentMonth {
        case 10: // October
            return SeasonalEventInfo(
                eventName: "Halloween Challenge",
                description: "Complete spooky puzzles to earn Halloween rewards!",
                progress: getSeasonalProgress(eventType: "halloween"),
                maxProgress: 31,
                endTime: getEndOfMonth(month: 10),
                theme: "🎃",
                rewards: ["🎃 Halloween Badge", "👻 Spooky Theme", "🍬 Bonus XP"]
            )
        case 12: // December
            return SeasonalEventInfo(
                eventName: "Winter Wonderland",
                description: "Solve puzzles to unlock winter themes and rewards!",
                progress: getSeasonalProgress(eventType: "winter"),
                maxProgress: 25,
                endTime: getEndOfMonth(month: 12),
                theme: "❄️",
                rewards: ["❄️ Winter Badge", "🎄 Holiday Theme", "⛄ Snow Effects"]
            )
        case 2: // February
            return SeasonalEventInfo(
                eventName: "Love & Logic",
                description: "Share the love of puzzles this Valentine's month!",
                progress: getSeasonalProgress(eventType: "valentine"),
                maxProgress: 14,
                endTime: getEndOfMonth(month: 2),
                theme: "💕",
                rewards: ["💕 Love Badge", "🌹 Romance Theme", "💝 Double XP"]
            )
        default:
            return nil
        }
    }
    
    private func getSeasonalProgress(eventType: String) -> Int {
        return UserDefaults.standard.integer(forKey: "seasonal_\(eventType)")
    }
    
    private func getEndOfMonth(month: Int) -> TimeInterval {
        let calendar = Calendar.current
        var components = calendar.dateComponents([.year], from: Date())
        components.month = month
        components.day = calendar.range(of: .day, in: .month, for: calendar.date(from: components)!)?.upperBound
        components.hour = 23
        components.minute = 59
        components.second = 59
        
        return calendar.date(from: components)?.timeIntervalSince1970 ?? Date().timeIntervalSince1970
    }
    
    // MARK: - Daily Statistics
    
    func getDailyActivityStats() -> DailyStatsInfo {
        let dailyData = userStatsManager.getDailyActivityData()
        let weeklyData = userStatsManager.getWeeklyActivityData()
        
        return DailyStatsInfo(
            todayCount: dailyData.todayCount,
            yesterdayCount: dailyData.yesterdayCount,
            weeklyProgress: weeklyData.weeklyProgress,
            hasPlayedToday: dailyData.hasPlayedToday,
            dailyGoal: 5, // Can be made configurable
            weeklyGoal: 25,
            dailyStreak: progressionEngine.getCurrentDailyStreak(),
            motivationMessage: generateDailyMotivation(dailyData: dailyData, weeklyData: weeklyData)
        )
    }
    
    private func generateDailyMotivation(dailyData: DailyActivityData, weeklyData: WeeklyActivityData) -> String {
        switch true {
        case !dailyData.hasPlayedToday:
            return "🌟 Start your daily puzzle journey!"
        case dailyData.todayCount >= 10:
            return "🔥 Amazing! \(dailyData.todayCount) puzzles today!"
        case dailyData.todayCount >= 5:
            return "💪 Great work! \(dailyData.todayCount) completed today!"
        case weeklyData.weeklyProgress >= 20:
            return "🎯 Almost at your weekly goal!"
        default:
            return "✨ Keep going! You're doing great!"
        }
    }
    
    // MARK: - Global Statistics Formatting
    
    private func getGlobalStatsData() -> GlobalStatsInfo {
        let globalStats = userStatsManager.getGlobalStats()
        let levelInfo = progressionEngine.getCurrentLevel()
        
        return GlobalStatsInfo(
            totalXP: globalStats.totalXP,
            currentLevel: levelInfo.level,
            totalGamesPlayed: globalStats.totalGamesPlayed,
            totalTimeHours: globalStats.totalTimePlayedHours,
            averageXPPerGame: globalStats.averageXPPerGame,
            memberSince: formatJoinDate(timestamp: globalStats.joinDate),
            nextLevelProgress: levelInfo.progressPercentage,
            xpToNextLevel: levelInfo.xpToNextLevel,
            rank: calculateApproximateRank(totalXP: globalStats.totalXP),
            lifetimeStats: generateLifetimeStats(globalStats: globalStats)
        )
    }
    
    private func formatJoinDate(timestamp: TimeInterval) -> String {
        let formatter = DateFormatter()
        formatter.dateStyle = .medium
        return formatter.string(from: Date(timeIntervalSince1970: timestamp))
    }
    
    private func calculateApproximateRank(totalXP: Int) -> String {
        switch totalXP {
        case 10000...:
            return "Top 1%"
        case 5000..<10000:
            return "Top 5%"
        case 2000..<5000:
            return "Top 10%"
        case 1000..<2000:
            return "Top 25%"
        case 500..<1000:
            return "Top 50%"
        default:
            return "Getting Started"
        }
    }
    
    private func generateLifetimeStats(globalStats: GlobalUserStats) -> [String] {
        var stats: [String] = []
        
        if globalStats.totalGamesPlayed >= 100 {
            stats.append("🎮 \(globalStats.totalGamesPlayed)+ games played")
        }
        
        if globalStats.totalTimePlayedHours >= 10 {
            stats.append("⏰ \(Int(globalStats.totalTimePlayedHours))+ hours of brain training")
        }
        
        if globalStats.totalXP >= 1000 {
            stats.append("⭐ \(globalStats.totalXP)+ XP earned")
        }
        
        let daysSinceJoin = Int((Date().timeIntervalSince1970 - globalStats.joinDate) / (24 * 60 * 60))
        if daysSinceJoin >= 30 {
            stats.append("📅 \(daysSinceJoin) days of puzzle solving")
        }
        
        return stats.isEmpty ? ["🚀 Just getting started!"] : stats
    }
    
    // MARK: - Tier Helper Methods
    
    private func determineTier(level: Int, xp: Int) -> String {
        switch xp {
        case 0..<1000: return "Bronze"
        case 1000..<2500: return "Silver"
        case 2500..<5000: return "Gold"
        case 5000..<10000: return "Platinum"
        case 10000..<20000: return "Diamond"
        default: return "Master"
        }
    }
    
    private func determineNextTier(currentTier: String) -> String {
        switch currentTier {
        case "Bronze": return "Silver"
        case "Silver": return "Gold"
        case "Gold": return "Platinum"
        case "Platinum": return "Diamond"
        case "Diamond": return "Master"
        default: return "Master"
        }
    }
    
    private func getNextTierRewards(tier: String) -> [String] {
        switch tier {
        case "Silver": return ["🥈 Silver Badge", "✨ Silver Theme"]
        case "Gold": return ["🥇 Gold Badge", "🌟 Gold Theme", "⚡ Speed Boost"]
        case "Platinum": return ["💎 Platinum Badge", "🏆 Premium Theme", "🔥 XP Multiplier"]
        case "Diamond": return ["💠 Diamond Badge", "👑 Elite Theme", "🌈 Rainbow Effects"]
        case "Master": return ["👑 Master Crown", "🎭 Master Theme", "⭐ Ultimate Powers"]
        default: return []
        }
    }
    
    // MARK: - Utility Methods
    
    private func formatLastPlayed(timestamp: TimeInterval) -> String {
        guard timestamp > 0 else { return "Never" }
        
        let now = Date().timeIntervalSince1970
        let diff = now - timestamp
        
        switch diff {
        case 0..<60:
            return "Just now"
        case 60..<3600:
            return "\(Int(diff / 60))m ago"
        case 3600..<86400:
            return "\(Int(diff / 3600))h ago"
        case 86400..<604800:
            return "\(Int(diff / 86400))d ago"
        default:
            let formatter = DateFormatter()
            formatter.dateFormat = "MMM dd"
            return formatter.string(from: Date(timeIntervalSince1970: timestamp))
        }
    }
    
    // MARK: - Data Refresh Methods
    
    func refreshData() -> DashboardData {
        print("🔄 Refreshing dashboard data")
        return getDashboardData()
    }
    
    func refreshAllData() {
        // Trigger any necessary cache refreshes
        userStatsManager.refreshStats()
        progressionEngine.refreshProgress()
    }
    
    func updateSeasonalProgress(_ eventType: String, progress: Int) {
        UserDefaults.standard.set(progress, forKey: "seasonal_\(eventType)")
    }
    
    // MARK: - Additional Analytics Methods
    
    func getDetailedPuzzleStats() -> [String: [String: Any]] {
        let allStats = userStatsManager.getAllUserStats()
        var detailedStats: [String: [String: Any]] = [:]
        
        for (puzzleType, stats) in allStats {
            detailedStats[puzzleType] = [
                "totalSolved": stats.wins,
                "totalPlayed": stats.totalPlays,
                "accuracy": stats.winRate,
                "averageTime": stats.totalPlays > 0 ? Float(stats.totalTimeSpentSeconds) / Float(stats.totalPlays) : 0,
                "highScore": stats.highScore,
                "totalTimeHours": stats.totalTimeSpentHours,
                "lastPlayed": stats.lastPlayedTimestamp,
                "progressStatus": calculateProgressStatus(stats: stats),
                "improvementTip": generateImprovementTip(puzzleType: puzzleType, stats: stats)
            ]
        }
        
        return detailedStats
    }
    
    func getTimeAnalyticsDetailed() -> [String: Any] {
        let timeAnalytics = getTimeAnalyticsData()
        
        return [
            "totalHours": timeAnalytics.totalHours,
            "averageSessionTime": timeAnalytics.averageSessionTime,
            "totalSessions": timeAnalytics.totalSessions,
            "efficiency": timeAnalytics.efficiency,
            "dailyAverageMinutes": timeAnalytics.dailyAverageMinutes,
            "timeDistribution": timeAnalytics.timeDistribution,
            "mostProductiveHour": timeAnalytics.mostProductiveHour,
            "fastestPuzzleType": timeAnalytics.fastestPuzzleType
        ]
    }
    
    func exportUserProgress() -> [String: Any] {
        let dashboardData = getDashboardData()
        
        return [
            "exportDate": Date().timeIntervalSince1970,
            "userStats": [
                "totalXP": dashboardData.globalStats.totalXP,
                "currentLevel": dashboardData.globalStats.currentLevel,
                "totalGamesPlayed": dashboardData.globalStats.totalGamesPlayed,
                "totalTimeHours": dashboardData.globalStats.totalTimeHours,
                "memberSince": dashboardData.globalStats.memberSince,
                "rank": dashboardData.globalStats.rank
            ],
            "streakStats": [
                "currentQuestionStreak": dashboardData.streakInfo.currentQuestionStreak,
                "bestQuestionStreak": dashboardData.streakInfo.bestQuestionStreak,
                "currentDailyStreak": dashboardData.streakInfo.currentDailyStreak,
                "longestDailyStreak": dashboardData.streakInfo.longestDailyStreak
            ],
            "achievements": dashboardData.achievements.map { achievement in
                [
                    "id": achievement.id,
                    "name": achievement.title,
                    "isUnlocked": achievement.isUnlocked,
                    "progress": achievement.progress,
                    "maxProgress": achievement.maxProgress,
                    "category": achievement.category
                ]
            },
            "puzzlePerformance": dashboardData.puzzlePerformance.map { puzzle in
                [
                    "puzzleType": puzzle.puzzleType,
                    "totalSolved": puzzle.totalSolved,
                    "totalPlayed": puzzle.totalPlayed,
                    "accuracy": puzzle.accuracy,
                    "averageTime": puzzle.averageTime,
                    "highScore": puzzle.highScore
                ]
            }
        ]
    }
}
