//
//  ProgressionEngine.swift
//  PuzzleForge
//
//  Created by Sushanth Tiruvaipati on 8/6/25.
//

import Foundation

class ProgressionEngine: ObservableObject {
    private let userStatsManager: UserStatsManager
    
    init(userStatsManager: UserStatsManager = .shared) {
        self.userStatsManager = userStatsManager
    }
    
    // MARK: - Level Management
    
    func getCurrentLevel() -> UserLevel {
        let globalStats = userStatsManager.getGlobalStats()
        let totalXP = globalStats.totalXP
        
        // Calculate level based on XP
        let level = calculateLevel(from: totalXP)
        let xpForCurrentLevel = calculateXPForLevel(level)
        let xpForNextLevel = calculateXPForLevel(level + 1)
        let xpToNextLevel = xpForNextLevel - totalXP
        
        return UserLevel(
            level: level,
            currentXP: totalXP - xpForCurrentLevel,
            xpToNextLevel: xpToNextLevel,
            totalXP: totalXP,
            xpForCurrentLevel: xpForCurrentLevel,
            xpForNextLevel: xpForNextLevel
        )
    }
    
    private func calculateLevel(from xp: Int) -> Int {
        // Simple level calculation: every 100 XP = 1 level
        return max(1, xp / 100)
    }
    
    private func calculateXPForLevel(_ level: Int) -> Int {
        // XP required to reach this level
        return (level - 1) * 100
    }
    
    // MARK: - Streak Management
    
    func getCurrentStreak() -> Int {
        return UserDefaults.standard.integer(forKey: "current_question_streak")
    }
    
    func getBestStreak() -> Int {
        return UserDefaults.standard.integer(forKey: "best_question_streak")
    }
    
    func getCurrentDailyStreak() -> Int {
        return UserDefaults.standard.integer(forKey: "current_daily_streak")
    }
    
    func updateStreak(isCorrect: Bool) {
        if isCorrect {
            let currentStreak = getCurrentStreak() + 1
            let bestStreak = getBestStreak()
            
            UserDefaults.standard.set(currentStreak, forKey: "current_question_streak")
            
            if currentStreak > bestStreak {
                UserDefaults.standard.set(currentStreak, forKey: "best_question_streak")
            }
        } else {
            UserDefaults.standard.set(0, forKey: "current_question_streak")
        }
    }
    
    func updateDailyStreak() {
        let today = DateFormatter.dailyFormat.string(from: Date())
        let lastPlayDate = UserDefaults.standard.string(forKey: "last_daily_play_date") ?? ""
        let yesterday = DateFormatter.dailyFormat.string(from: Calendar.current.date(byAdding: .day, value: -1, to: Date())!)
        
        if lastPlayDate == yesterday {
            // Continued streak
            let currentDailyStreak = getCurrentDailyStreak() + 1
            UserDefaults.standard.set(currentDailyStreak, forKey: "current_daily_streak")
            
            let bestDailyStreak = UserDefaults.standard.integer(forKey: "best_daily_streak")
            if currentDailyStreak > bestDailyStreak {
                UserDefaults.standard.set(currentDailyStreak, forKey: "best_daily_streak")
            }
        } else if lastPlayDate != today {
            // New streak or broken streak
            UserDefaults.standard.set(1, forKey: "current_daily_streak")
        }
        
        UserDefaults.standard.set(today, forKey: "last_daily_play_date")
    }
    
    // MARK: - Achievement Management
    
    func getAllAchievements() -> [Achievement] {
        return ProgressionConstants.achievementDefinitions.map { template in
            var achievement = template
            let isUnlocked = userStatsManager.isAchievementUnlocked(achievementId: template.id)
            achievement.isUnlocked = isUnlocked
            
            // Calculate progress based on achievement type
            achievement.progress = calculateAchievementProgress(for: template)
            
            return achievement
        }
    }
    
    private func calculateAchievementProgress(for achievement: Achievement) -> Int {
        let globalStats = userStatsManager.getGlobalStats()
        let currentLevel = getCurrentLevel().level
        let bestStreak = getBestStreak()
        let bestDailyStreak = UserDefaults.standard.integer(forKey: "best_daily_streak")
        
        switch achievement.id {
        case "first_correct", "first_game":
            return min(globalStats.totalGamesPlayed, 1)
        case "ten_games":
            return min(globalStats.totalGamesPlayed, 10)
        case "fifty_games":
            return min(globalStats.totalGamesPlayed, 50)
        case "hundred_games":
            return min(globalStats.totalGamesPlayed, 100)
        case "thousand_xp":
            return min(globalStats.totalXP, 1000)
        case "five_thousand_xp":
            return min(globalStats.totalXP, 5000)
        case "ten_hours":
            return min(Int(globalStats.totalTimePlayedHours), 10)
        case "streak_3", "five_streak":
            return min(bestStreak, achievement.maxProgress)
        case "streak_10":
            return min(bestStreak, 10)
        case "streak_25":
            return min(bestStreak, 25)
        case "perfect_week", "seven_day_streak":
            return min(bestDailyStreak, 7)
        case "daily_streak_30", "thirty_day_streak":
            return min(bestDailyStreak, 30)
        case "level_5":
            return min(currentLevel, 5)
        case "level_10":
            return min(currentLevel, 10)
        case "level_20":
            return min(currentLevel, 20)
        default:
            return 0
        }
    }
    
    func checkForNewAchievements() -> [Achievement] {
        let allAchievements = getAllAchievements()
        var newAchievements: [Achievement] = []
        
        for achievement in allAchievements {
            if achievement.progress >= achievement.maxProgress && !achievement.isUnlocked {
                userStatsManager.unlockAchievement(achievementId: achievement.id)
                var unlockedAchievement = achievement
                unlockedAchievement.isUnlocked = true
                newAchievements.append(unlockedAchievement)
            }
        }
        
        return newAchievements
    }
    
    // MARK: - Weekly Challenge
    
    func getWeeklyChallengeInfo() -> WeeklyChallengeInfo {
        let weeklyData = userStatsManager.getWeeklyActivityData()
        return WeeklyChallengeInfo(
            weeklyGoal: 25,
            weeklyProgress: weeklyData.weeklyProgress,
            timeUntilReset: weeklyData.timeUntilReset,
            weeklyReward: 250, // 25 * 10 XP
            currentWeek: weeklyData.currentWeek
        )
    }
    
    // MARK: - Progression Processing
    
    func processGameResult(
        puzzleType: String,
        score: Int,
        timeSpent: Int,
        isCorrect: Bool,
        difficulty: String
    ) -> ProgressionResult {
        // Record the game data
        userStatsManager.recordPuzzleCompletion(
            puzzleType: puzzleType,
            score: score,
            timeSpentSeconds: timeSpent,
            isCorrect: isCorrect,
            difficulty: difficulty
        )
        
        // Update streaks
        updateStreak(isCorrect: isCorrect)
        if isCorrect {
            updateDailyStreak()
        }
        
        // Calculate XP and level progression
        let xpGained = calculateXP(score: score, isCorrect: isCorrect, difficulty: difficulty)
        let oldLevel = getCurrentLevel()
        
        userStatsManager.recordSessionData(xpGained: xpGained, timeSpentSeconds: timeSpent)
        
        let newLevel = getCurrentLevel()
        let levelUp = oldLevel.level != newLevel.level ?
            LevelUpInfo(oldLevel: oldLevel.level, newLevel: newLevel.level, rewardXP: (newLevel.level - oldLevel.level) * 50) : nil
        
        // Check for new achievements
        let newAchievements = checkForNewAchievements()
        
        // Create score breakdown
        let scoreBreakdown = createScoreBreakdown(
            baseScore: score,
            isCorrect: isCorrect,
            difficulty: difficulty,
            streak: getCurrentStreak()
        )
        
        // Update activity tracking
        userStatsManager.recordDailyActivity()
        userStatsManager.recordWeeklyActivity()
        
        return ProgressionResult(
            scoreBreakdown: scoreBreakdown,
            levelUp: levelUp,
            newAchievements: newAchievements,
            streakInfo: getStreakInfo(),
            totalXPGained: xpGained,
            isCorrect: isCorrect
        )
    }
    
    private func calculateXP(score: Int, isCorrect: Bool, difficulty: String) -> Int {
        guard isCorrect else { return 0 }
        
        let baseXP = score / 10 // Convert score to XP
        let difficultyMultiplier = ProgressionConstants.difficultyMultipliers[difficulty] ?? 1.0
        let streakBonus = getStreakMultiplier(streak: getCurrentStreak())
        
        return Int(Float(baseXP) * difficultyMultiplier * streakBonus)
    }
    
    private func getStreakMultiplier(streak: Int) -> Float {
        switch streak {
        case 10...: return 2.0
        case 5..<10: return 1.5
        case 3..<5: return 1.25
        default: return 1.0
        }
    }
    
    private func createScoreBreakdown(baseScore: Int, isCorrect: Bool, difficulty: String, streak: Int) -> ScoreBreakdown {
        let timeBonus = 0 // Can be implemented based on time
        let streakBonus = isCorrect ? Int(Float(baseScore) * (getStreakMultiplier(streak: streak) - 1.0)) : 0
        let difficultyMultiplier = ProgressionConstants.difficultyMultipliers[difficulty] ?? 1.0
        let totalScore = Int(Float(baseScore + timeBonus + streakBonus) * difficultyMultiplier)
        let xpGained = calculateXP(score: baseScore, isCorrect: isCorrect, difficulty: difficulty)
        
        return ScoreBreakdown(
            baseScore: baseScore,
            timeBonus: timeBonus,
            streakBonus: streakBonus,
            difficultyMultiplier: difficultyMultiplier,
            totalScore: totalScore,
            xpGained: xpGained
        )
    }
    
    private func getStreakInfo() -> StreakInfo {
        return StreakInfo(
            currentStreak: getCurrentStreak(),
            bestStreak: getBestStreak(),
            streakMultiplier: getStreakMultiplier(streak: getCurrentStreak()),
            dailyStreak: getCurrentDailyStreak(),
            hasDailyStreakBonus: getCurrentDailyStreak() >= 3
        )
    }
    
    // MARK: - Data Refresh Methods
    
    func refreshProgress() {
        // Refresh any cached progression data
        // This is mainly for when data changes externally
        print("🔄 Refreshing progression data...")
        
        // Recalculate any derived data if needed
        objectWillChange.send()
    }
    
    // MARK: - Utility Methods
    
    func resetAllProgress() {
        // Reset all progression-related data
        UserDefaults.standard.removeObject(forKey: "current_question_streak")
        UserDefaults.standard.removeObject(forKey: "best_question_streak")
        UserDefaults.standard.removeObject(forKey: "current_daily_streak")
        UserDefaults.standard.removeObject(forKey: "best_daily_streak")
        UserDefaults.standard.removeObject(forKey: "last_daily_play_date")
        
        // Reset user stats as well
        userStatsManager.resetProgressionData()
        
        print("✅ All progression data reset")
    }
    
    // MARK: - Debug Methods
    
    func getDebugInfo() -> [String: Any] {
        let level = getCurrentLevel()
        return [
            "currentLevel": level.level,
            "totalXP": level.totalXP,
            "currentStreak": getCurrentStreak(),
            "bestStreak": getBestStreak(),
            "dailyStreak": getCurrentDailyStreak(),
            "unlockedAchievements": userStatsManager.getUnlockedAchievements().count,
            "totalAchievements": ProgressionConstants.achievementDefinitions.count
        ]
    }
}
