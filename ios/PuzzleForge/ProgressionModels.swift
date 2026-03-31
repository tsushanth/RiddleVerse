//
//  ProgressionModels.swift
//  PuzzleForge
//
//  Created by Sushanth Tiruvaipati on 8/6/25.
//

import Foundation

// MARK: - Core Data Models

struct UserLevel: Codable {
    let level: Int
    let currentXP: Int
    let xpToNextLevel: Int
    let totalXP: Int
    // Added for compatibility with dashboard
    let xpForCurrentLevel: Int
    let xpForNextLevel: Int
    
    var progressPercentage: Float {
        if xpToNextLevel > 0 {
            return Float(currentXP) / Float(xpToNextLevel)
        }
        return 1.0
    }
    
    // Convenience initializer for backward compatibility
    init(level: Int, currentXP: Int, xpToNextLevel: Int, totalXP: Int) {
        self.level = level
        self.currentXP = currentXP
        self.xpToNextLevel = xpToNextLevel
        self.totalXP = totalXP
        self.xpForCurrentLevel = currentXP
        self.xpForNextLevel = xpToNextLevel
    }
    
    init(level: Int, currentXP: Int, xpToNextLevel: Int, totalXP: Int, xpForCurrentLevel: Int, xpForNextLevel: Int) {
        self.level = level
        self.currentXP = currentXP
        self.xpToNextLevel = xpToNextLevel
        self.totalXP = totalXP
        self.xpForCurrentLevel = xpForCurrentLevel
        self.xpForNextLevel = xpForNextLevel
    }
}

struct TierInfo: Codable {
    let currentTier: String
    let nextTier: String
    let pointsInTier: Int
    let pointsToNextTier: Int
    let progressPercentage: Float
    let nextTierRewards: [String]
    let currentLevel: Int
    let totalXP: Int
    
    var isMaxTier: Bool {
        return currentTier == nextTier
    }
}

struct Achievement: Codable, Identifiable {
    let id: String
    let title: String
    let description: String
    let icon: String
    var isUnlocked: Bool = false
    var progress: Int = 0
    let maxProgress: Int
    // Additional properties for enhanced achievement system
    let category: String
    let xpReward: Int
    let unlockedDate: TimeInterval?
    
    var progressPercentage: Float {
        return maxProgress > 0 ? Float(progress) / Float(maxProgress) : 0.0
    }
    
    // Convenience properties for compatibility
    var name: String { return title }
    var emoji: String { return icon }
    
    // MARK: - Initializers
    
    // Simple initializer (existing)
    init(id: String, title: String, description: String, icon: String, maxProgress: Int) {
        self.id = id
        self.title = title
        self.description = description
        self.icon = icon
        self.maxProgress = maxProgress
        self.category = "General"
        self.xpReward = maxProgress * 10
        self.unlockedDate = nil
        self.isUnlocked = false
        self.progress = 0
    }
    
    // Full initializer with all parameters
    init(id: String, title: String, description: String, icon: String, isUnlocked: Bool, progress: Int, maxProgress: Int, category: String, xpReward: Int, unlockedDate: TimeInterval?) {
        self.id = id
        self.title = title
        self.description = description
        self.icon = icon
        self.isUnlocked = isUnlocked
        self.progress = progress
        self.maxProgress = maxProgress
        self.category = category
        self.xpReward = xpReward
        self.unlockedDate = unlockedDate
    }
    
    // Alternative initializer using 'name' and 'emoji' for compatibility
    init(id: String, name: String, description: String, emoji: String, isUnlocked: Bool, progress: Int, maxProgress: Int, category: String, xpReward: Int, unlockedDate: TimeInterval?) {
        self.id = id
        self.title = name
        self.description = description
        self.icon = emoji
        self.isUnlocked = isUnlocked
        self.progress = progress
        self.maxProgress = maxProgress
        self.category = category
        self.xpReward = xpReward
        self.unlockedDate = unlockedDate
    }
    
    // Mutable initializer for updates
    init(from template: Achievement, progress: Int, isUnlocked: Bool) {
        self.id = template.id
        self.title = template.title
        self.description = template.description
        self.icon = template.icon
        self.progress = progress
        self.isUnlocked = isUnlocked
        self.maxProgress = template.maxProgress
        self.category = template.category
        self.xpReward = template.xpReward
        self.unlockedDate = isUnlocked ? Date().timeIntervalSince1970 : nil
    }
}

struct StreakInfo: Codable {
    let currentStreak: Int
    let bestStreak: Int
    let streakMultiplier: Float
    let dailyStreak: Int
    let hasDailyStreakBonus: Bool
    
    var hasStreakBonus: Bool {
        return currentStreak >= 3
    }
}

struct StreakDisplayInfo: Codable {
    let currentQuestionStreak: Int
    let bestQuestionStreak: Int
    let currentDailyStreak: Int
    let longestDailyStreak: Int
    let hasPlayedToday: Bool
    let streakActive: Bool
    let questionStreakMultiplier: Float
    let dailyStreakBonus: Float
    let streakMessage: String
}

struct WeeklyChallengeInfo: Codable {
    let weeklyGoal: Int
    let weeklyProgress: Int
    let timeUntilReset: TimeInterval
    let weeklyReward: Int
    let currentWeek: Int
    
    var isCompleted: Bool {
        return weeklyProgress >= weeklyGoal
    }
    
    var progressPercentage: Float {
        return weeklyGoal > 0 ? Float(weeklyProgress) / Float(weeklyGoal) : 0.0
    }
}

// MARK: - Dashboard Data Models

struct PuzzlePerformanceInfo: Codable, Identifiable {
    let id = UUID()
    let puzzleType: String
    let totalSolved: Int
    let totalPlayed: Int
    let accuracy: Float
    let averageTime: Float
    let highScore: Int
    let totalTime: Float
    let recentActivity: String
    let progressStatus: String
    let difficultyRecommendation: String
    let improvementTip: String
}

struct TimeAnalyticsInfo: Codable {
    let totalHours: Float
    let averageSessionTime: Float
    let totalSessions: Int
    let fastestPuzzleType: String
    let mostProductiveHour: String
    let dailyAverageMinutes: Float
    let timeDistribution: [String: Float]
    let efficiency: Float
    let timeMessage: String
}

struct SeasonalEventInfo: Codable {
    let eventName: String
    let description: String
    let progress: Int
    let maxProgress: Int
    let endTime: TimeInterval
    let theme: String
    let rewards: [String]
    
    var isCompleted: Bool {
        return progress >= maxProgress
    }
    
    var progressPercentage: Float {
        return maxProgress > 0 ? Float(progress) / Float(maxProgress) : 0.0
    }
}

struct DailyStatsInfo: Codable {
    let todayCount: Int
    let yesterdayCount: Int
    let weeklyProgress: Int
    let hasPlayedToday: Bool
    let dailyGoal: Int
    let weeklyGoal: Int
    let dailyStreak: Int
    let motivationMessage: String
}

struct GlobalStatsInfo: Codable {
    let totalXP: Int
    let currentLevel: Int
    let totalGamesPlayed: Int
    let totalTimeHours: Float
    let averageXPPerGame: Int
    let memberSince: String
    let nextLevelProgress: Float
    let xpToNextLevel: Int
    let rank: String
    let lifetimeStats: [String]
}

// MARK: - Supporting Data Types

struct DailyActivityData: Codable {
    var todayCount: Int = 0
    var yesterdayCount: Int = 0
    var hasPlayedToday: Bool = false
    var hasPlayedYesterday: Bool = false
    var lastActiveDate: String = ""
    
    // Default initializer
    init() {
        // Use default values
    }
    
    // Custom initializer
    init(todayCount: Int = 0, yesterdayCount: Int = 0, hasPlayedToday: Bool = false, hasPlayedYesterday: Bool = false, lastActiveDate: String = "") {
        self.todayCount = todayCount
        self.yesterdayCount = yesterdayCount
        self.hasPlayedToday = hasPlayedToday
        self.hasPlayedYesterday = hasPlayedYesterday
        self.lastActiveDate = lastActiveDate
    }
}

struct WeeklyActivityData: Codable {
    var weeklyProgress: Int = 0
    var currentWeek: Int = 0
    var timeUntilReset: TimeInterval = 0
    
    // Default initializer
    init() {
        let currentWeek = Calendar.current.component(.weekOfYear, from: Date())
        self.currentWeek = currentWeek
    }
    
    // Custom initializer
    init(weeklyProgress: Int, currentWeek: Int, timeUntilReset: TimeInterval) {
        self.weeklyProgress = weeklyProgress
        self.currentWeek = currentWeek
        self.timeUntilReset = timeUntilReset
    }
}

struct GlobalUserStats: Codable {
    var totalXP: Int = 0
    var totalGamesPlayed: Int = 0
    var totalTimePlayedSeconds: TimeInterval = 0
    var joinDate: TimeInterval = Date().timeIntervalSince1970
    var bestStreak: Int = 0
    var currentStreak: Int = 0
    
    var averageXPPerGame: Int {
        return totalGamesPlayed > 0 ? totalXP / totalGamesPlayed : 0
    }
    
    var totalTimePlayedHours: Float {
        return Float(totalTimePlayedSeconds / 3600.0)
    }
}

// MARK: - Progression Results

struct ScoreBreakdown: Codable {
    let baseScore: Int
    let timeBonus: Int
    let streakBonus: Int
    let difficultyMultiplier: Float
    let totalScore: Int
    let xpGained: Int
}

struct LevelUpInfo: Codable {
    let oldLevel: Int
    let newLevel: Int
    let rewardXP: Int
}

struct ProgressionResult: Codable {
    let scoreBreakdown: ScoreBreakdown
    let levelUp: LevelUpInfo?
    let newAchievements: [Achievement]
    let streakInfo: StreakInfo
    let totalXPGained: Int
    let isCorrect: Bool
}

// MARK: - Dashboard Data Container

struct DashboardData: Codable {
    let tierInfo: TierInfo
    let levelInfo: UserLevel
    let streakInfo: StreakDisplayInfo
    let weeklyChallenge: WeeklyChallengeInfo
    let achievements: [Achievement]
    let puzzlePerformance: [PuzzlePerformanceInfo]
    let timeAnalytics: TimeAnalyticsInfo
    let seasonalEvent: SeasonalEventInfo?
    let dailyStats: DailyStatsInfo
    let globalStats: GlobalStatsInfo
}

// MARK: - Feedback System

enum SoundEffect: String, CaseIterable {
    case correct = "correct"
    case incorrect = "buzz"
    case streakSmall = "streak_small"
    case streakMedium = "streak_medium"
    case streakLarge = "streak_large"
    case dailyStreak = "daily_streak"
    case levelUp = "level_up"
    case achievement = "achievement"
    case perfect = "perfect"
    case timeBonus = "time_bonus"
}

struct FeedbackData {
    let isCorrect: Bool
    let userAnswer: String
    let correctAnswer: String
    let progressionResult: ProgressionResult?
    let puzzleType: String
    let customMessage: String?
    let onComplete: (() -> Void)?
    
    var mainMessage: String {
        if let customMessage = customMessage {
            return customMessage
        }
        
        if isCorrect {
            if let result = progressionResult {
                return "Correct! +\(result.scoreBreakdown.totalScore) points"
            } else {
                return "Correct!"
            }
        } else {
            return "Try again! Keep practicing! 💪"
        }
    }
    
    var showProgressionDetails: Bool {
        return isCorrect && progressionResult != nil
    }
}

// MARK: - Constants

struct ProgressionConstants {
    static let tierThresholds: [(String, Int)] = [
        ("Bronze", 0),
        ("Silver", 500),
        ("Gold", 1500),
        ("Platinum", 3000),
        ("Diamond", 6000),
        ("Master", 10000)
    ]
    
    static let difficultyMultipliers: [String: Float] = [
        "Easy": 1.0,
        "Medium": 1.25,
        "Hard": 1.5
    ]
    
    static let baseScores: [String: [String: Int]] = [
        "math": ["Easy": 10, "Medium": 15, "Hard": 20],
        "anagram": ["Easy": 15, "Medium": 20, "Hard": 25],
        "trivia": ["Easy": 12, "Medium": 17, "Hard": 22],
        "wordsearch": ["Easy": 14, "Medium": 19, "Hard": 24],
        "crypto": ["Easy": 18, "Medium": 23, "Hard": 28],
        "wordsnake": ["Easy": 16, "Medium": 21, "Hard": 26]
    ]
    
    static let achievementDefinitions: [Achievement] = [
        Achievement(id: "first_correct", title: "First Success", description: "Solve your first puzzle", icon: "🎯", maxProgress: 1),
        Achievement(id: "streak_3", title: "On a Roll", description: "Get 3 correct in a row", icon: "🔥", maxProgress: 3),
        Achievement(id: "streak_10", title: "Unstoppable", description: "Get 10 correct in a row", icon: "⚡", maxProgress: 10),
        Achievement(id: "streak_25", title: "Legendary", description: "Get 25 correct in a row", icon: "🏆", maxProgress: 25),
        Achievement(id: "speed_demon", title: "Speed Demon", description: "Solve 5 puzzles under 30s", icon: "💨", maxProgress: 5),
        Achievement(id: "math_master", title: "Math Master", description: "Solve 25 math puzzles", icon: "🧮", maxProgress: 25),
        Achievement(id: "word_wizard", title: "Word Wizard", description: "Solve 25 word puzzles", icon: "📚", maxProgress: 25),
        Achievement(id: "level_5", title: "Rising Star", description: "Reach level 5", icon: "⭐", maxProgress: 5),
        Achievement(id: "level_10", title: "Expert", description: "Reach level 10", icon: "👑", maxProgress: 10),
        Achievement(id: "level_20", title: "Master", description: "Reach level 20", icon: "💎", maxProgress: 20),
        Achievement(id: "hundred_points", title: "Century", description: "Score 100+ points in one puzzle", icon: "💯", maxProgress: 1),
        Achievement(id: "perfect_week", title: "Perfect Week", description: "Play 7 days in a row", icon: "📅", maxProgress: 7),
        Achievement(id: "daily_streak_30", title: "Month Master", description: "30 day streak", icon: "🗓️", maxProgress: 30),
        // Additional achievements for comprehensive system
        Achievement(id: "first_game", title: "First Steps", description: "Complete your first puzzle", icon: "🎯", maxProgress: 1),
        Achievement(id: "ten_games", title: "Getting Started", description: "Complete 10 puzzles", icon: "⚡", maxProgress: 10),
        Achievement(id: "fifty_games", title: "Puzzle Enthusiast", description: "Complete 50 puzzles", icon: "🎮", maxProgress: 50),
        Achievement(id: "hundred_games", title: "Puzzle Master", description: "Complete 100 puzzles", icon: "🏆", maxProgress: 100),
        Achievement(id: "thousand_xp", title: "Knowledge Seeker", description: "Earn 1,000 XP", icon: "⭐", maxProgress: 1000),
        Achievement(id: "five_thousand_xp", title: "Wisdom Collector", description: "Earn 5,000 XP", icon: "🌟", maxProgress: 5000),
        Achievement(id: "ten_hours", title: "Dedicated Player", description: "Play for 10 hours total", icon: "⏰", maxProgress: 10),
        Achievement(id: "five_streak", title: "On Fire", description: "Get a 5-question streak", icon: "🔥", maxProgress: 5),
        Achievement(id: "seven_day_streak", title: "Weekly Champion", description: "Play 7 days in a row", icon: "📅", maxProgress: 7),
        Achievement(id: "thirty_day_streak", title: "Monthly Legend", description: "Play 30 days in a row", icon: "🏅", maxProgress: 30)
    ]
}

// MARK: - Helper Extensions

extension DateFormatter {
    static let dailyFormat: DateFormatter = {
        let formatter = DateFormatter()
        formatter.dateFormat = "yyyy-MM-dd"
        return formatter
    }()
}

// MARK: - Achievement Category Extensions

extension Achievement {
    enum Category: String, CaseIterable {
        case progress = "Progress"
        case streak = "Streak"
        case daily = "Daily"
        case xp = "XP"
        case time = "Time"
        case puzzleType = "Puzzle Type"
        case skill = "Skill"
        case special = "Special"
        
        var emoji: String {
            switch self {
            case .progress: return "🎯"
            case .streak: return "🔥"
            case .daily: return "📅"
            case .xp: return "⭐"
            case .time: return "⏰"
            case .puzzleType: return "🎮"
            case .skill: return "🏆"
            case .special: return "✨"
            }
        }
    }
    
    var categoryEnum: Category {
        return Category(rawValue: category) ?? .progress
    }
}

// MARK: - Tier Extensions

extension TierInfo {
    static func getTierEmoji(_ tier: String) -> String {
        switch tier {
        case "Bronze": return "🥉"
        case "Silver": return "🥈"
        case "Gold": return "🥇"
        case "Platinum": return "💎"
        case "Diamond": return "💠"
        case "Master": return "👑"
        default: return "🏆"
        }
    }
    
    static func getTierColor(_ tier: String) -> String {
        switch tier {
        case "Bronze": return "#CD7F32"
        case "Silver": return "#C0C0C0"
        case "Gold": return "#FFD700"
        case "Platinum": return "#E5E4E2"
        case "Diamond": return "#B9F2FF"
        case "Master": return "#9C27B0"
        default: return "#808080"
        }
    }
}

// MARK: - Statistics Extensions

extension GlobalUserStats {
    func getDaysSinceJoined() -> Int {
        let daysSince = (Date().timeIntervalSince1970 - joinDate) / (24 * 60 * 60)
        return max(Int(daysSince), 1)
    }
    
    func getAverageDailyMinutes() -> Float {
        let daysSince = Float(getDaysSinceJoined())
        return (Float(totalTimePlayedSeconds) / 60.0) / daysSince
    }
    
    func getEfficiencyScore() -> Float {
        guard totalGamesPlayed > 0, totalTimePlayedSeconds > 0 else { return 0 }
        
        let avgXPPerMinute = Float(totalXP) / (Float(totalTimePlayedSeconds) / 60.0)
        return min(avgXPPerMinute * 2.0, 100.0) // Scale to 0-100
    }
}

// MARK: - Validation Extensions

extension DashboardData {
    func validate() -> Bool {
        // Basic validation checks
        guard tierInfo.currentLevel >= 0,
              levelInfo.level >= 0,
              globalStats.totalGamesPlayed >= 0,
              globalStats.totalXP >= 0 else {
            return false
        }
        
        // Ensure consistency between related data
        guard globalStats.currentLevel == levelInfo.level,
              globalStats.totalXP == levelInfo.totalXP else {
            return false
        }
        
        return true
    }
    
    var isNewUser: Bool {
        return globalStats.totalGamesPlayed == 0
    }
    
    var hasActiveStreaks: Bool {
        return streakInfo.streakActive || streakInfo.currentQuestionStreak > 0
    }
}
