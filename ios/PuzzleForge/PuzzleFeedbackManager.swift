//
//  PuzzleFeedbackManager.swift
//  PuzzleForge
//
//  Updated to match progression system models exactly
//

import SwiftUI
import AVFoundation
import FirebaseAuth

// MARK: - Score Update Configuration (Matches Android exactly)
struct ScoreUpdateConfig {
    let puzzleType: String
    let difficulty: String
    let timeRemaining: Int
    let totalTime: Int
    let hintsUsed: Int
    let baseScore: Int?
    let isCustomPuzzle: Bool
    
    init(puzzleType: String, difficulty: String, timeRemaining: Int, totalTime: Int, hintsUsed: Int = 0, isCustomPuzzle: Bool = false, baseScore: Int? = nil) {
        self.puzzleType = puzzleType
        self.difficulty = difficulty
        self.timeRemaining = timeRemaining
        self.totalTime = totalTime
        self.baseScore = baseScore
        self.hintsUsed = hintsUsed
        self.isCustomPuzzle = isCustomPuzzle
    }
}

// MARK: - Progression Manager (Matches Android logic exactly)
class ProgressionManager: ObservableObject {
    @Published var currentLevel: UserLevel = UserLevel(level: 1, currentXP: 0, xpToNextLevel: 100, totalXP: 0)
    @Published var streakInfo: StreakInfo = StreakInfo(currentStreak: 0, bestStreak: 0, streakMultiplier: 1.0, dailyStreak: 0, hasDailyStreakBonus: false)
    
    private let userDefaults = UserDefaults.standard
    
    // Achievement definitions (same as Android)
    private static let achievementDefinitions: [Achievement] = [
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
    
    // Base scores (exactly matching Android)
    private static let baseScores: [String: [String: Int]] = [
        "math": ["Easy": 10, "Medium": 15, "Hard": 20],
        "average": ["Easy": 15, "Medium": 20, "Hard": 25],
        "division": ["Easy": 20, "Medium": 25, "Hard": 30],
        "estimation": ["Easy": 18, "Medium": 23, "Hard": 28],
        "percentage": ["Easy": 16, "Medium": 21, "Hard": 26],
        "discounts": ["Easy": 22, "Medium": 27, "Hard": 32],
        "purchasing": ["Easy": 19, "Medium": 24, "Hard": 29],
        "conversion": ["Easy": 17, "Medium": 22, "Hard": 27],
        "anagram": ["Easy": 15, "Medium": 20, "Hard": 25],
        "trivia": ["Easy": 12, "Medium": 17, "Hard": 22],
        "storyPuzzle": ["Easy": 14, "Medium": 19, "Hard": 24]
    ]
    
    // Difficulty multipliers (exactly matching Android)
    private static let difficultyMultipliers: [String: Float] = [
        "Easy": 1.0,
        "Medium": 1.25,
        "Hard": 1.5
    ]
    
    init() {
        loadProgressionData()
    }
    
    // MARK: - Score Calculation (Matches Android exactly)
    private func getBaseScore(puzzleType: String, difficulty: String) -> Int {
        return Self.baseScores[puzzleType.lowercased()]?[difficulty] ?? 15
    }
    
    private func calculateTimeBonus(timeRemaining: Int, totalTime: Int, baseScore: Int) -> Int {
        guard timeRemaining > 0 && totalTime > 0 else { return 0 }
        
        let timePercentage = Double(timeRemaining) / Double(totalTime)
        let maxBonus = Double(baseScore) * 0.5 // Max 50% bonus
        
        // Exponential curve for better rewards (matching Android pow calculation)
        let bonus = maxBonus * pow(timePercentage, 0.7)
        
        return Int(bonus)
    }
    
    private func calculateStreakBonus(streak: Int, baseScore: Int) -> Int {
        switch streak {
        case 10...: return Int(Double(baseScore) * 0.5) // 50% bonus for 10+ streak
        case 5...: return Int(Double(baseScore) * 0.3)  // 30% bonus for 5+ streak
        case 3...: return Int(Double(baseScore) * 0.2)  // 20% bonus for 3+ streak
        default: return 0
        }
    }
    
    private func getDifficultyMultiplier(difficulty: String) -> Float {
        return Self.difficultyMultipliers[difficulty] ?? 1.0
    }
    
    // XP calculation (matching Android)
    private func getXPRequiredForLevel(_ level: Int) -> Int {
        return Int(100.0 * Double(level) * (1.0 + Double(level) * 0.1))
    }
    
    private func calculateLevelFromXP(_ totalXP: Int) -> Int {
        var level = 1
        var xpNeeded = 0
        
        while xpNeeded <= totalXP {
            xpNeeded += getXPRequiredForLevel(level)
            if xpNeeded <= totalXP {
                level += 1
            }
        }
        
        return level
    }
    
    // MARK: - Daily Streak Management
    private func updateDailyStreak() -> Int {
        let today = Calendar.current.startOfDay(for: Date())
        let yesterday = Calendar.current.date(byAdding: .day, value: -1, to: today)!
        
        let todayKey = "played_today_\(today.timeIntervalSince1970)"
        let yesterdayKey = "played_today_\(yesterday.timeIntervalSince1970)"
        
        let playedToday = userDefaults.bool(forKey: todayKey)
        let playedYesterday = userDefaults.bool(forKey: yesterdayKey)
        
        var currentDailyStreak = userDefaults.integer(forKey: "daily_streak")
        
        if !playedToday {
            // First play today
            userDefaults.set(true, forKey: todayKey)
            
            if playedYesterday || currentDailyStreak == 0 {
                // Continue or start streak
                currentDailyStreak += 1
            } else {
                // Streak broken, restart
                currentDailyStreak = 1
            }
            
            userDefaults.set(currentDailyStreak, forKey: "daily_streak")
            
            // Update best daily streak
            let bestDailyStreak = userDefaults.integer(forKey: "best_daily_streak")
            if currentDailyStreak > bestDailyStreak {
                userDefaults.set(currentDailyStreak, forKey: "best_daily_streak")
            }
        }
        
        return currentDailyStreak
    }
    
    // MARK: - Progression Processing (Main method)
    func processPuzzleCompletion(isCorrect: Bool, config: ScoreUpdateConfig) -> ProgressionResult {
        let oldLevel = currentLevel
        let oldStreak = streakInfo
        
        // Update daily streak
        let newDailyStreak = updateDailyStreak()
        
        // Update question streak (exactly matching Android logic)
        let newStreakInfo: StreakInfo
        if isCorrect {
            let newCurrent = oldStreak.currentStreak + 1
            let newBest = max(newCurrent, oldStreak.bestStreak)
            
            userDefaults.set(newCurrent, forKey: "current_streak")
            userDefaults.set(newBest, forKey: "best_streak")
            
            let multiplier: Float = {
                switch newCurrent {
                case 10...: return 2.0
                case 5...: return 1.5
                case 3...: return 1.25
                default: return 1.0
                }
            }()
            
            newStreakInfo = StreakInfo(
                currentStreak: newCurrent,
                bestStreak: newBest,
                streakMultiplier: multiplier,
                dailyStreak: newDailyStreak,
                hasDailyStreakBonus: newDailyStreak >= 3
            )
        } else {
            userDefaults.set(0, forKey: "current_streak")
            newStreakInfo = StreakInfo(
                currentStreak: 0,
                bestStreak: oldStreak.bestStreak,
                streakMultiplier: 1.0,
                dailyStreak: newDailyStreak,
                hasDailyStreakBonus: newDailyStreak >= 3
            )
        }
        
        // Update the published property
        DispatchQueue.main.async {
            self.streakInfo = newStreakInfo
        }
        
        // Calculate score breakdown (exactly matching Android)
        let scoreBreakdown: ScoreBreakdown
        if isCorrect {
            scoreBreakdown = calculateScoreBreakdown(config: config, streakInfo: newStreakInfo)
        } else {
            scoreBreakdown = ScoreBreakdown(baseScore: 0, timeBonus: 0, streakBonus: 0, difficultyMultiplier: 1.0, totalScore: 0, xpGained: 0)
        }
        
        // Update XP and check for level up
        var levelUpInfo: LevelUpInfo? = nil
        if isCorrect {
            let newTotalXP = oldLevel.totalXP + scoreBreakdown.xpGained
            userDefaults.set(newTotalXP, forKey: "total_xp")
            
            let newLevel = getCurrentLevel()
            DispatchQueue.main.async {
                self.currentLevel = newLevel
            }
            
            if newLevel.level > oldLevel.level {
                let rewardXP = newLevel.level * 50 // Level up reward
                levelUpInfo = LevelUpInfo(oldLevel: oldLevel.level, newLevel: newLevel.level, rewardXP: rewardXP)
            }
        }
        
        // Check for new achievements
        let newAchievements = checkForNewAchievements(isCorrect: isCorrect, config: config, streakInfo: newStreakInfo, scoreBreakdown: scoreBreakdown)
        
        // Update puzzle-specific stats
        if isCorrect {
            updatePuzzleStats(config: config)
        }
        
        return ProgressionResult(
            scoreBreakdown: scoreBreakdown,
            levelUp: levelUpInfo,
            newAchievements: newAchievements,
            streakInfo: newStreakInfo,
            totalXPGained: scoreBreakdown.xpGained,
            isCorrect: isCorrect
        )
    }
    
    private func calculateScoreBreakdown(config: ScoreUpdateConfig, streakInfo: StreakInfo) -> ScoreBreakdown {
        let baseScore = config.baseScore ?? getBaseScore(puzzleType: config.puzzleType, difficulty: config.difficulty)
        let timeBonus = calculateTimeBonus(timeRemaining: config.timeRemaining, totalTime: config.totalTime, baseScore: baseScore)
        let difficultyMultiplier = getDifficultyMultiplier(difficulty: config.difficulty)
        let streakBonus = calculateStreakBonus(streak: streakInfo.currentStreak, baseScore: baseScore)
        
        let subtotal = Float(baseScore + timeBonus + streakBonus)
        let totalScore = Int(subtotal * difficultyMultiplier)
        
        // XP is same as score (matching Android)
        let xpGained = totalScore
        
        return ScoreBreakdown(
            baseScore: baseScore,
            timeBonus: timeBonus,
            streakBonus: streakBonus,
            difficultyMultiplier: difficultyMultiplier,
            totalScore: totalScore,
            xpGained: xpGained
        )
    }
    
    public func getCurrentLevel() -> UserLevel {
        let totalXP = userDefaults.integer(forKey: "total_xp")
        let level = calculateLevelFromXP(totalXP)
        let xpForCurrentLevel = level > 1 ? getXPRequiredForLevel(level - 1) : 0
        let xpForNextLevel = getXPRequiredForLevel(level)
        let currentXP = totalXP - xpForCurrentLevel
        let xpToNext = xpForNextLevel - xpForCurrentLevel
        
        return UserLevel(
            level: level,
            currentXP: currentXP,
            xpToNextLevel: xpToNext,
            totalXP: totalXP
        )
    }
    
    private func loadProgressionData() {
        currentLevel = getCurrentLevel()
        
        let currentStreak = userDefaults.integer(forKey: "current_streak")
        let bestStreak = userDefaults.integer(forKey: "best_streak")
        let dailyStreak = userDefaults.integer(forKey: "daily_streak")
        
        let multiplier: Float = {
            switch currentStreak {
            case 10...: return 2.0
            case 5...: return 1.5
            case 3...: return 1.25
            default: return 1.0
            }
        }()
        
        streakInfo = StreakInfo(
            currentStreak: currentStreak,
            bestStreak: bestStreak,
            streakMultiplier: multiplier,
            dailyStreak: dailyStreak,
            hasDailyStreakBonus: dailyStreak >= 3
        )
    }
    
    // MARK: - Achievement System (Matching Android logic)
    private func checkForNewAchievements(isCorrect: Bool, config: ScoreUpdateConfig, streakInfo: StreakInfo, scoreBreakdown: ScoreBreakdown) -> [Achievement] {
        var newAchievements: [Achievement] = []
        
        guard isCorrect else { return newAchievements }
        
        // Update total puzzles solved
        let totalPuzzles = userDefaults.integer(forKey: "total_puzzles_solved") + 1
        userDefaults.set(totalPuzzles, forKey: "total_puzzles_solved")
        
        // Check first correct achievement
        if totalPuzzles == 1 && !isAchievementUnlocked("first_correct") {
            newAchievements.append(unlockAchievement("first_correct"))
        }
        
        // Check streak achievements
        if streakInfo.currentStreak >= 3 && !isAchievementUnlocked("streak_3") {
            newAchievements.append(unlockAchievement("streak_3"))
        }
        if streakInfo.currentStreak >= 10 && !isAchievementUnlocked("streak_10") {
            newAchievements.append(unlockAchievement("streak_10"))
        }
        
        // Check speed achievements
        if config.timeRemaining > (config.totalTime - 30) && !isAchievementUnlocked("speed_demon") {
            let speedCount = userDefaults.integer(forKey: "speed_puzzles") + 1
            userDefaults.set(speedCount, forKey: "speed_puzzles")
            
            if speedCount >= 5 {
                newAchievements.append(unlockAchievement("speed_demon"))
            }
        }
        
        // Check math master achievement
        if config.puzzleType.lowercased().contains("math") {
            let mathCount = userDefaults.integer(forKey: "math_puzzles_solved") + 1
            userDefaults.set(mathCount, forKey: "math_puzzles_solved")
            
            if mathCount >= 25 && !isAchievementUnlocked("math_master") {
                newAchievements.append(unlockAchievement("math_master"))
            }
        }
        
        // Check level achievements
        if currentLevel.level >= 5 && !isAchievementUnlocked("level_5") {
            newAchievements.append(unlockAchievement("level_5"))
        }
        if currentLevel.level >= 10 && !isAchievementUnlocked("level_10") {
            newAchievements.append(unlockAchievement("level_10"))
        }
        
        // Check high score achievement
        if scoreBreakdown.totalScore >= 100 && !isAchievementUnlocked("hundred_points") {
            newAchievements.append(unlockAchievement("hundred_points"))
        }
        
        // Check daily streak achievement
        if streakInfo.dailyStreak >= 7 && !isAchievementUnlocked("perfect_week") {
            newAchievements.append(unlockAchievement("perfect_week"))
        }
        
        return newAchievements
    }
    
    private func updatePuzzleStats(config: ScoreUpdateConfig) {
        // Track puzzle type counts
        let puzzleKey = "\(config.puzzleType)_count"
        let currentCount = userDefaults.integer(forKey: puzzleKey)
        userDefaults.set(currentCount + 1, forKey: puzzleKey)
        
        // Track difficulty stats
        let difficultyKey = "\(config.difficulty)_solved"
        let difficultyCount = userDefaults.integer(forKey: difficultyKey)
        userDefaults.set(difficultyCount + 1, forKey: difficultyKey)
    }
    
    private func isAchievementUnlocked(_ achievementId: String) -> Bool {
        return userDefaults.bool(forKey: "achievement_\(achievementId)")
    }
    
    private func unlockAchievement(_ achievementId: String) -> Achievement {
        userDefaults.set(true, forKey: "achievement_\(achievementId)")
        return Self.achievementDefinitions.first { $0.id == achievementId }?.copyWith(isUnlocked: true)
            ?? Achievement(
                id: achievementId,
                title: "Unknown",
                description: "Unknown achievement",
                icon: "🏆",
                isUnlocked: true,
                progress: 1,
                maxProgress: 1,
                category: "Special",
                xpReward: 10,
                unlockedDate: Date().timeIntervalSince1970
            )
    }
    
    func getAllAchievements() -> [Achievement] {
        return Self.achievementDefinitions.map { achievement in
            let isUnlocked = isAchievementUnlocked(achievement.id)
            var progress = isUnlocked ? achievement.maxProgress : 0
            
            // Calculate progress for incomplete achievements
            if !isUnlocked {
                switch achievement.id {
                case "streak_3":
                    progress = min(streakInfo.currentStreak, 3)
                case "streak_10":
                    progress = min(streakInfo.currentStreak, 10)
                case "speed_demon":
                    progress = min(userDefaults.integer(forKey: "speed_puzzles"), 5)
                case "math_master":
                    progress = min(userDefaults.integer(forKey: "math_puzzles_solved"), 25)
                case "level_5":
                    progress = min(currentLevel.level, 5)
                case "level_10":
                    progress = min(currentLevel.level, 10)
                case "perfect_week":
                    progress = min(streakInfo.dailyStreak, 7)
                default:
                    progress = 0
                }
            }
            
            return achievement.copyWith(isUnlocked: isUnlocked, progress: progress)
        }
    }
    
    func resetProgression() {
        let defaults = UserDefaults.standard
        let dictionary = defaults.dictionaryRepresentation()
        dictionary.keys.forEach { key in
            if key.hasPrefix("achievement_") || key.hasPrefix("total_") ||
               key.hasPrefix("current_") || key.hasPrefix("best_") ||
               key.hasPrefix("daily_") || key.hasPrefix("played_today_") ||
               key.contains("_count") || key.contains("_solved") {
                defaults.removeObject(forKey: key)
            }
        }
        loadProgressionData()
    }
}

// MARK: - Enhanced Feedback Manager
class PuzzleFeedbackManager: ObservableObject {
    @Published var isShowingFeedback = false
    @Published var feedbackMessage = ""
    @Published var isCorrect = false
    @Published var showProgressionDetails = false
    @Published var progressionResult: ProgressionResult?
    
    private let progressionManager: ProgressionManager
    private let soundManager = SoundFeedbackManager()
    
    init(progressionManager: ProgressionManager = ProgressionManager()) {
        self.progressionManager = progressionManager
    }
    
    func showFeedback(
        isCorrect: Bool,
        userAnswer: String,
        correctAnswer: String,
        config: ScoreUpdateConfig,
        customMessage: String? = nil,
        duration: TimeInterval = 3.0,
        onComplete: @escaping () -> Void = {}
    ) {
        // Process progression
        let result = progressionManager.processPuzzleCompletion(isCorrect: isCorrect, config: config)
        
        // Update UI
        self.isCorrect = isCorrect
        self.progressionResult = result
        self.showProgressionDetails = isCorrect && (result.levelUp != nil || !result.newAchievements.isEmpty)
        
        // Generate feedback message
        if let customMessage = customMessage {
            self.feedbackMessage = customMessage
        } else if isCorrect {
            if let levelUp = result.levelUp {
                self.feedbackMessage = "🎉 Level Up! Now Level \(levelUp.newLevel)! +\(result.totalXPGained) XP"
            } else {
                self.feedbackMessage = "Correct! +\(result.totalXPGained) XP"
            }
        } else {
            self.feedbackMessage = "Try again! Keep practicing! 💪"
        }
        
        // Play sound effect
        let soundEffect = determineSoundEffect(result: result, isCorrect: isCorrect)
        soundManager.playSound(soundEffect)
        
        // Show feedback
        isShowingFeedback = true
        
        // Auto-hide after duration
        DispatchQueue.main.asyncAfter(deadline: .now() + duration) {
            self.hideFeedback()
            onComplete()
        }
    }
    
    func hideFeedback() {
        isShowingFeedback = false
        showProgressionDetails = false
        progressionResult = nil
    }
    
    private func determineSoundEffect(result: ProgressionResult, isCorrect: Bool) -> SoundEffect {
        guard isCorrect else { return .incorrect }
        
        // Priority order for sound selection
        if result.levelUp != nil {
            return .levelUp
        } else if !result.newAchievements.isEmpty {
            return .achievement
        } else if result.streakInfo.currentStreak >= 10 {
            return .streakLarge
        } else if result.streakInfo.currentStreak >= 5 {
            return .streakMedium
        } else if result.streakInfo.currentStreak >= 3 {
            return .streakSmall
        } else if result.scoreBreakdown.timeBonus > result.scoreBreakdown.baseScore / 2 {
            return .perfect
        } else {
            return .correct
        }
    }
}


class SoundFeedbackManager {
    private var audioPlayers: [SoundEffect: AVAudioPlayer] = [:]
    
    init() {
        setupAudioPlayers()
    }
    
    private func setupAudioPlayers() {
        for effect in SoundEffect.allCases {
            if let url = Bundle.main.url(forResource: effect.rawValue, withExtension: "mp3") {
                do {
                    let player = try AVAudioPlayer(contentsOf: url)
                    player.prepareToPlay()
                    audioPlayers[effect] = player
                } catch {
                    print("⚠️ Failed to load sound: \(effect.rawValue)")
                }
            }
        }
    }
    
    func playSound(_ effect: SoundEffect) {
        if let player = audioPlayers[effect] {
            player.stop()
            player.currentTime = 0
            player.play()
        } else {
            // Fallback to system sounds
            playSystemSound(for: effect)
        }
    }
    
    private func playSystemSound(for effect: SoundEffect) {
        switch effect {
        case .correct, .perfect, .streakSmall, .streakMedium, .streakLarge:
            AudioServicesPlaySystemSound(1016) // Positive click
        case .incorrect:
            AudioServicesPlaySystemSound(1053) // Error / negative
        case .levelUp, .achievement:
            AudioServicesPlaySystemSound(1017) // "Tada" sound
        case .dailyStreak:
            AudioServicesPlaySystemSound(1005) // Calendar alert (celebratory)
        case .timeBonus:
            AudioServicesPlaySystemSound(1022) // Timer complete / bonus-style
        }
    }
}

// MARK: - Helper Extensions
extension Achievement {
    func copyWith(isUnlocked: Bool? = nil, progress: Int? = nil) -> Achievement {
        return Achievement(
            id: self.id,
            title: self.title,
            description: self.description,
            icon: self.icon,
            isUnlocked: isUnlocked ?? self.isUnlocked,
            progress: progress ?? self.progress,
            maxProgress: self.maxProgress,
            category: self.category,
            xpReward: self.xpReward,
            unlockedDate: (isUnlocked ?? self.isUnlocked) ? Date().timeIntervalSince1970 : self.unlockedDate
        )
    }
}

// MARK: - SwiftUI Integration
struct FeedbackOverlay: View {
    @ObservedObject var feedbackManager: PuzzleFeedbackManager
    
    var body: some View {
        if feedbackManager.isShowingFeedback {
            ZStack {
                Color.black.opacity(0.3)
                    .ignoresSafeArea()
                
                VStack(spacing: 16) {
                    // Main feedback
                    Text(feedbackManager.isCorrect ? "✅" : "❌")
                        .font(.system(size: 60))
                    
                    Text(feedbackManager.feedbackMessage)
                        .font(.title2)
                        .fontWeight(.bold)
                        .foregroundColor(.white)
                        .multilineTextAlignment(.center)
                    
                    // Progression details
                    if feedbackManager.showProgressionDetails,
                       let result = feedbackManager.progressionResult {
                        ProgressionDetailsView(result: result)
                    }
                }
                .padding(24)
                .background(
                    RoundedRectangle(cornerRadius: 16)
                        .fill(Color(.systemBackground))
                        .shadow(radius: 20)
                )
                .padding(.horizontal, 40)
            }
            .transition(.scale.combined(with: .opacity))
            .animation(.easeInOut(duration: 0.3), value: feedbackManager.isShowingFeedback)
        }
    }
}
