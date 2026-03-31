//
//  UnifiedFeedbackManager.swift
//  PuzzleForge
//
//  Simplified unified feedback manager to replace PuzzleFeedbackManager
//  This centralizes all puzzle feedback logic in one place
//

import SwiftUI
import AVFoundation
import AudioToolbox
import UserNotifications

// MARK: - Enhanced Sound System
enum FeedbackSoundEffect {
    case correct
    case incorrect
    case streak3
    case streak5
    case streak10
    case dailyStreak
    case levelUp
    case achievement
    case perfect
    case timeBonus
    
    var fileName: String {
        switch self {
        case .correct: return "correct"
        case .incorrect: return "buzz"
        case .streak3: return "streak_small"
        case .streak5: return "streak_medium"
        case .streak10: return "streak_large"
        case .dailyStreak: return "daily_streak"
        case .levelUp: return "level_up"
        case .achievement: return "achievement"
        case .perfect: return "perfect"
        case .timeBonus: return "time_bonus"
        }
    }
}
import FirebaseAuth

// MARK: - Unified Feedback Manager
class UnifiedFeedbackManager: ObservableObject {
    @Published var isShowingFeedback = false
    @Published var isProcessing = false
    @Published var animateIcon = false
    @Published var showConfetti = false
    
    // Current feedback data
    @Published var currentFeedback: EnhancedFeedbackData?
    
    private var audioPlayer: AVAudioPlayer?
    public let progressionManager = ProgressionManager()
    
    init() {
        // Update daily streak when manager is initialized (app start/puzzle session start)
        updateDailyStreak()
    }
    
    var currentLevel: UserLevel {
        return progressionManager.getCurrentLevel()
    }
    
    // MARK: - Main Feedback Method
    func showFeedback(
        puzzleType: String,
        isCorrect: Bool,
        userAnswer: String,
        correctAnswer: String,
        timeSpent: TimeInterval,
        difficulty: String,
        hintsUsed: Int = 0,
        timeRemaining: Int = 0,
        totalTime: Int = 60,
        onComplete: @escaping () -> Void = {}
    ) {
        // Create score config for progression
        let scoreConfig = ScoreUpdateConfig(
            puzzleType: puzzleType,
            difficulty: difficulty,
            timeRemaining: timeRemaining,
            totalTime: totalTime,
            hintsUsed: hintsUsed
        )
        
        // Process progression (this handles question streaks)
        let progressionResult = progressionManager.processPuzzleCompletion(
            isCorrect: isCorrect,
            config: scoreConfig
        )
        
        // Create feedback data with enhanced streak info (including daily streaks)
        let enhancedStreakInfo = getEnhancedStreakInfo(baseStreakInfo: progressionResult.streakInfo)
        
        // Create enhanced progression result with our new streak info
        let enhancedProgressionResult = EnhancedProgressionResult(
            scoreBreakdown: progressionResult.scoreBreakdown,
            levelUp: progressionResult.levelUp,
            newAchievements: progressionResult.newAchievements,
            streakInfo: enhancedStreakInfo,
            totalXPGained: progressionResult.totalXPGained
        )
        
        let feedbackData = EnhancedFeedbackData(
            isCorrect: isCorrect,
            userAnswer: userAnswer,
            correctAnswer: correctAnswer,
            progressionResult: enhancedProgressionResult,
            puzzleType: puzzleType,
            onComplete: onComplete
        )
        
        // Show feedback
        displayFeedback(feedbackData)
        
        // Track analytics
        trackAnalytics(puzzleType: puzzleType, difficulty: difficulty, isCorrect: isCorrect, timeSpent: timeSpent)
        
        // Update backend if correct
        if isCorrect {
            updateBackendScore(scoreIncrease: progressionResult.scoreBreakdown.totalScore)
        }
    }
    
    // MARK: - Daily Streak Management
    private func updateDailyStreak() {
        let calendar = Calendar.current
        let today = Date()
        let lastPlayDate = UserDefaults.standard.object(forKey: "last_play_date") as? Date
        
        var newDailyStreak = 1 // Default for first time or after break
        
        if let lastPlay = lastPlayDate {
            if calendar.isDate(lastPlay, inSameDayAs: today) {
                // Already played today - maintain current streak
                newDailyStreak = UserDefaults.standard.integer(forKey: "daily_streak")
                return // Don't update if already played today
            } else if calendar.isDate(lastPlay, equalTo: calendar.date(byAdding: .day, value: -1, to: today)!, toGranularity: .day) {
                // Played yesterday - extend streak
                let currentDailyStreak = UserDefaults.standard.integer(forKey: "daily_streak")
                newDailyStreak = currentDailyStreak + 1
                print("🔥 Daily streak extended! Day \(newDailyStreak)")
            } else {
                // Streak broken - restart
                let oldStreak = UserDefaults.standard.integer(forKey: "daily_streak")
                if oldStreak > 0 {
                    print("💔 Daily streak broken! Was \(oldStreak) days, restarting...")
                }
                newDailyStreak = 1
            }
        } else {
            // First time playing
            print("🎉 Welcome! Starting your daily streak!")
            newDailyStreak = 1
        }
        
        // Update daily streak and last play date
        UserDefaults.standard.set(newDailyStreak, forKey: "daily_streak")
        UserDefaults.standard.set(today, forKey: "last_play_date")
        
        // Update best daily streak if needed
        let bestDailyStreak = UserDefaults.standard.integer(forKey: "best_daily_streak")
        if newDailyStreak > bestDailyStreak {
            UserDefaults.standard.set(newDailyStreak, forKey: "best_daily_streak")
            print("🏆 New daily streak record! \(newDailyStreak) days!")
        }
        
        // Schedule reminder notification for tomorrow
        scheduleStreakReminderNotification()
    }
    
    private func getEnhancedStreakInfo(baseStreakInfo: StreakInfo) -> EnhancedStreakInfo {
        let dailyStreak = UserDefaults.standard.integer(forKey: "daily_streak")
        let longestDailyStreak = UserDefaults.standard.integer(forKey: "best_daily_streak")
        let lastPlayDate = UserDefaults.standard.object(forKey: "last_play_date") as? Date
        
        // Calculate daily streak bonus (separate from question streak bonus)
        let dailyStreakBonus: Float = {
            switch dailyStreak {
            case 30...: return 2.0  // 100% bonus for 30+ day streak
            case 14...: return 1.75 // 75% bonus for 2+ week streak
            case 7...: return 1.5   // 50% bonus for 1+ week streak
            case 3...: return 1.25  // 25% bonus for 3+ day streak
            default: return 1.0
            }
        }()
        
        return EnhancedStreakInfo(
            questionStreak: baseStreakInfo.currentStreak,
            dailyStreak: dailyStreak,
            longestQuestionStreak: baseStreakInfo.bestStreak,
            longestDailyStreak: longestDailyStreak,
            lastPlayDate: lastPlayDate,
            streakMultiplier: baseStreakInfo.streakMultiplier,
            dailyStreakBonus: dailyStreakBonus
        )
    }
    
    private func scheduleStreakReminderNotification() {
        // Only schedule if user has a streak going
        let dailyStreak = UserDefaults.standard.integer(forKey: "daily_streak")
        guard dailyStreak > 0 else { return }
        
        // Remove any existing notifications
        UNUserNotificationCenter.current().removePendingNotificationRequests(withIdentifiers: ["daily_streak_reminder"])
        
        // Schedule for tomorrow at optimal time (e.g., 7 PM)
        let content = UNMutableNotificationContent()
        content.title = "Keep Your Streak Alive! 🔥"
        content.body = "You're on a \(dailyStreak)-day streak! Solve a puzzle to keep it going."
        content.sound = .default
        
        var dateComponents = DateComponents()
        dateComponents.hour = 19 // 7 PM
        dateComponents.minute = 0
        
        let trigger = UNCalendarNotificationTrigger(dateMatching: dateComponents, repeats: false)
        let request = UNNotificationRequest(identifier: "daily_streak_reminder", content: content, trigger: trigger)
        
        UNUserNotificationCenter.current().add(request) { error in
            if let error = error {
                print("❌ Failed to schedule streak reminder: \(error)")
            } else {
                print("✅ Streak reminder scheduled for tomorrow 7 PM")
            }
        }
    }
    
    // MARK: - Public Daily Streak Access
    func getCurrentDailyStreak() -> Int {
        return UserDefaults.standard.integer(forKey: "daily_streak")
    }
    
    func getBestDailyStreak() -> Int {
        return UserDefaults.standard.integer(forKey: "best_daily_streak")
    }
    
    func hasPlayedToday() -> Bool {
        guard let lastPlayDate = UserDefaults.standard.object(forKey: "last_play_date") as? Date else {
            return false
        }
        return Calendar.current.isDate(lastPlayDate, inSameDayAs: Date())
    }
    
    private func displayFeedback(_ data: EnhancedFeedbackData) {
        currentFeedback = data
        isShowingFeedback = true
        
        // Play enhanced sound based on achievements and context
        let soundEffect = determineSoundEffect(data)
        playSoundEffect(soundEffect)
        
        // Start animations
        withAnimation(.spring(response: 0.5, dampingFraction: 0.6)) {
            animateIcon = true
        }
        
        // Show confetti for special achievements
        if shouldShowConfetti(data) {
            showConfetti = true
        }
        
        // Auto-hide after delay (longer for special achievements)
        let delay = data.isCorrect ? (shouldShowConfetti(data) ? 4.5 : 3.5) : 2.0
        DispatchQueue.main.asyncAfter(deadline: .now() + delay) {
            self.hideFeedback()
        }
    }
    
    // MARK: - Dismiss Feedback
    func hideFeedback() {
        guard let feedback = currentFeedback else { return }
        
        // Reset state
        isShowingFeedback = false
        animateIcon = false
        showConfetti = false
        
        // Call completion handler
        feedback.onComplete()
        
        // Clear current feedback
        currentFeedback = nil
    }
    
    // MARK: - Enhanced Sound System
    private func playSoundEffect(_ effect: FeedbackSoundEffect) {
        playSound(effect.fileName)
    }
    
    private func determineSoundEffect(_ data: EnhancedFeedbackData) -> FeedbackSoundEffect {
        if !data.isCorrect {
            return .incorrect
        }
        
        let result = data.progressionResult
        
        // Priority order for sound selection (highest priority first)
        
        // Level up takes priority
        if result.levelUp != nil {
            return .levelUp
        }
        
        // New achievements
        if !result.newAchievements.isEmpty {
            return .achievement
        }
        
        // Daily streak milestones
        if result.streakInfo.dailyStreak >= 7 && result.streakInfo.hasDailyStreakBonus {
            return .dailyStreak
        }
        
        // Question streak milestones
        if result.streakInfo.questionStreak >= 10 {
            return .streak10
        } else if result.streakInfo.questionStreak >= 5 {
            return .streak5
        } else if result.streakInfo.questionStreak >= 3 {
            return .streak3
        }
        
        // Perfect score (high time bonus)
        if result.scoreBreakdown.timeBonus > Int(Double(result.scoreBreakdown.baseScore) * 0.4) {
            return .perfect
        }
        
        // Time bonus
        if result.scoreBreakdown.timeBonus > 0 {
            return .timeBonus
        }
        
        // Default correct sound
        return .correct
    }
    
    private func shouldShowConfetti(_ data: EnhancedFeedbackData) -> Bool {
        if !data.isCorrect { return false }
        
        let result = data.progressionResult
        
        // Show confetti for special achievements
        return result.levelUp != nil ||
               !result.newAchievements.isEmpty ||
               result.streakInfo.questionStreak >= 5 ||
               result.streakInfo.dailyStreak >= 7
    }
    
    private func playSound(_ soundName: String) {
        // Configure audio session for sound effects
        do {
            try AVAudioSession.sharedInstance().setCategory(.ambient, mode: .default)
            try AVAudioSession.sharedInstance().setActive(true)
        } catch {
            print("❌ Failed to configure audio session: \(error)")
        }
        
        // Try both mp3 and wav extensions
        var soundURL: URL?
        
        // First try .mp3
        if let mp3URL = Bundle.main.url(forResource: soundName, withExtension: "mp3") {
            soundURL = mp3URL
        }
        // Then try .wav
        else if let wavURL = Bundle.main.url(forResource: soundName, withExtension: "wav") {
            soundURL = wavURL
        }
        
        guard let url = soundURL else {
            print("❌ Sound file \(soundName) not found (.mp3 or .wav), using fallback")
            playFallbackSound(for: soundName)
            return
        }
        
        do {
            audioPlayer = try AVAudioPlayer(contentsOf: url)
            audioPlayer?.prepareToPlay()
            audioPlayer?.volume = 1.0
            
            let didPlay = audioPlayer?.play() ?? false
            print(didPlay ? "✅ Playing sound: \(soundName)" : "❌ Failed to start playback: \(soundName)")
        } catch {
            print("❌ Failed to create audio player for \(soundName): \(error)")
            playFallbackSound(for: soundName)
        }
    }
    
    private func playFallbackSound(for soundName: String) {
        switch soundName {
        case "correct", "perfect":
            AudioServicesPlaySystemSound(1057) // Success sound
        case "buzz":
            AudioServicesPlaySystemSound(1053) // Error sound
        case "level_up", "achievement":
            AudioServicesPlaySystemSound(1114) // Achievement sound
        case "streak_small", "streak_medium", "streak_large", "daily_streak":
            AudioServicesPlaySystemSound(1103) // Streak sound
        case "time_bonus":
            AudioServicesPlaySystemSound(1105) // Bonus sound
        default:
            AudioServicesPlaySystemSound(1057) // Default success
        }
    }
    
    // MARK: - Analytics
    private func trackAnalytics(puzzleType: String, difficulty: String, isCorrect: Bool, timeSpent: TimeInterval) {
        AnalyticsManager.shared.track(.puzzleComplete(
            type: puzzleType,
            difficulty: difficulty,
            isCorrect: isCorrect,
            timeSpent: timeSpent,
            score: currentFeedback?.progressionResult.scoreBreakdown.totalScore ?? 0,
            questionIndex: 0, // Can be passed as parameter if needed
            totalQuestions: 1  // Can be passed as parameter if needed
        ))
    }
    
    // MARK: - Backend Score Update
    private func updateBackendScore(scoreIncrease: Int) {
        Task {
            await updateBackendScoreAsync(scoreIncrease: scoreIncrease)
        }
    }
    
    private func updateBackendScoreAsync(scoreIncrease: Int) async {
        await MainActor.run {
            self.isProcessing = true
        }
        
        guard let user = Auth.auth().currentUser,
              let email = user.email else {
            print("❌ No authenticated user found for score update")
            await MainActor.run {
                self.isProcessing = false
            }
            return
        }
        
        let displayName = user.displayName ?? email
        
        guard let url = URL(string: "https://puzzleverseai.com/update-score") else {
            print("❌ Invalid backend URL")
            await MainActor.run {
                self.isProcessing = false
            }
            return
        }
        
        var request = URLRequest(url: url)
        request.httpMethod = "POST"
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        request.timeoutInterval = 10.0
        
        let payload: [String: Any] = [
            "userId": email,
            "name": displayName,
            "score": scoreIncrease
        ]
        
        do {
            request.httpBody = try JSONSerialization.data(withJSONObject: payload)
            
            let (_, response) = try await URLSession.shared.data(for: request)
            
            if let httpResponse = response as? HTTPURLResponse {
                if httpResponse.statusCode == 200 {
                    print("✅ Score updated successfully: +\(scoreIncrease) points for \(email)")
                } else {
                    print("❌ Score update failed with status: \(httpResponse.statusCode)")
                }
            }
            
        } catch {
            print("❌ Failed to update score: \(error.localizedDescription)")
        }
        
        await MainActor.run {
            self.isProcessing = false
        }
    }
}

// MARK: - Enhanced Progression Models

/// Enhanced streak info that includes both question and daily streaks
struct EnhancedStreakInfo {
    let questionStreak: Int           // Consecutive correct answers
    let dailyStreak: Int             // Consecutive days playing
    let longestQuestionStreak: Int
    let longestDailyStreak: Int
    let lastPlayDate: Date?
    let streakMultiplier: Float       // Multiplier for question streaks
    let dailyStreakBonus: Float       // Bonus for daily streaks
    
    var hasQuestionStreakBonus: Bool { questionStreak >= 3 }
    var hasDailyStreakBonus: Bool { dailyStreak >= 3 }
    var isStreakActive: Bool {
        guard let lastPlay = lastPlayDate else { return false }
        return Calendar.current.isDateInToday(lastPlay) || Calendar.current.isDateInYesterday(lastPlay)
    }
}

// Enhanced progression result that uses EnhancedStreakInfo
struct EnhancedProgressionResult {
    let scoreBreakdown: ScoreBreakdown
    let levelUp: LevelUpInfo?
    let newAchievements: [Achievement]
    let streakInfo: EnhancedStreakInfo
    let totalXPGained: Int
}

// MARK: - Enhanced Feedback Data Model
struct EnhancedFeedbackData {
    let isCorrect: Bool
    let userAnswer: String
    let correctAnswer: String
    let progressionResult: EnhancedProgressionResult
    let puzzleType: String
    let onComplete: () -> Void
    
    var feedbackMessage: String {
        if isCorrect {
            return "Correct! +\(progressionResult.scoreBreakdown.totalScore) points"
        } else {
            return "Try again! Keep practicing! 💪"
        }
    }
}

// MARK: - Simple Answer Comparison View
struct SimpleAnswerComparisonView: View {
    let userAnswer: String
    let correctAnswer: String
    
    var body: some View {
        VStack(spacing: 8) {
            Text("Your answer: \(userAnswer)")
                .font(.system(size: 16))
                .foregroundColor(.primary)
            
            Text("Correct answer: \(correctAnswer)")
                .font(.system(size: 16, weight: .medium))
                .foregroundColor(.red.opacity(0.8))
        }
    }
}

// MARK: - Unified Feedback Overlay
struct UnifiedFeedbackOverlay: View {
    @ObservedObject var manager: UnifiedFeedbackManager
    
    var body: some View {
        ZStack {
            if manager.isShowingFeedback, let feedback = manager.currentFeedback {
                backgroundOverlay
                feedbackCard(feedback)
            }
            
            if manager.showConfetti {
                ConfettiView()
                    .allowsHitTesting(false)
            }
        }
    }
    
    private var backgroundOverlay: some View {
        Color.black.opacity(0.8)
            .ignoresSafeArea()
            .onTapGesture {
                manager.hideFeedback()
            }
    }
    
    private func feedbackCard(_ feedback: EnhancedFeedbackData) -> some View {
        VStack(spacing: 16) {
            statusIcon(feedback)
            mainMessage(feedback)
            contentSection(feedback)
            processingIndicator
            continueHint
        }
        .padding(24)
        .frame(maxWidth: UIScreen.main.bounds.width * 0.9)
        .background(cardBackground(feedback))
        .cornerRadius(20)
        .overlay(cardBorder(feedback))
        .scaleEffect(manager.isShowingFeedback ? 1.0 : 0.8)
        .transition(.scale.combined(with: .opacity))
        .onTapGesture {
            manager.hideFeedback()
        }
    }
    
    private func statusIcon(_ feedback: EnhancedFeedbackData) -> some View {
        Image(systemName: feedback.isCorrect ? "checkmark.circle.fill" : "xmark.circle.fill")
            .font(.system(size: 60))
            .foregroundColor(feedback.isCorrect ? .green : .red)
            .scaleEffect(manager.animateIcon ? 1.2 : 1.0)
            .animation(.spring(response: 0.5, dampingFraction: 0.6), value: manager.animateIcon)
    }
    
    private func mainMessage(_ feedback: EnhancedFeedbackData) -> some View {
        Text(feedback.feedbackMessage)
            .font(.system(size: 28, weight: .bold))
            .foregroundColor(feedback.isCorrect ? .green.opacity(0.8) : .red.opacity(0.8))
            .multilineTextAlignment(.center)
    }
    
    @ViewBuilder
    private func contentSection(_ feedback: EnhancedFeedbackData) -> some View {
        if feedback.isCorrect {
            EnhancedProgressionContentView(progressionResult: feedback.progressionResult)
        } else {
            VStack(spacing: 12) {
                SimpleAnswerComparisonView(
                    userAnswer: feedback.userAnswer,
                    correctAnswer: feedback.correctAnswer
                )
                
                Text("Keep trying! You've got this! 💪")
                    .font(.system(size: 16, weight: .medium))
                    .foregroundColor(.secondary)
                    .multilineTextAlignment(.center)
            }
        }
    }
    
    @ViewBuilder
    private var processingIndicator: some View {
        if manager.isProcessing {
            HStack(spacing: 8) {
                ProgressView()
                    .scaleEffect(0.8)
                    .progressViewStyle(CircularProgressViewStyle(tint: .green))
                
                Text("Updating score...")
                    .font(.system(size: 12))
                    .foregroundColor(.secondary)
            }
        }
    }
    
    private var continueHint: some View {
        Text("Tap to continue")
            .font(.system(size: 12))
            .foregroundColor(.secondary)
            .opacity(0.7)
            .padding(.top, 8)
    }
    
    private func cardBackground(_ feedback: EnhancedFeedbackData) -> some View {
        (feedback.isCorrect ? Color.green.opacity(0.1) : Color.red.opacity(0.1))
    }
    
    private func cardBorder(_ feedback: EnhancedFeedbackData) -> some View {
        RoundedRectangle(cornerRadius: 20)
            .stroke(feedback.isCorrect ? Color.green.opacity(0.3) : Color.red.opacity(0.3), lineWidth: 2)
    }
}

// MARK: - Enhanced Progression Content View
struct EnhancedProgressionContentView: View {
    let progressionResult: EnhancedProgressionResult
    
    var body: some View {
        VStack(spacing: 12) {
            // Score breakdown
            ScoreBreakdownCard(scoreBreakdown: progressionResult.scoreBreakdown)
            
            // Enhanced streak information
            if progressionResult.streakInfo.hasQuestionStreakBonus || progressionResult.streakInfo.hasDailyStreakBonus {
                StreakBonusCard(streakInfo: progressionResult.streakInfo)
            }
            
            // Level up notification
            if let levelUp = progressionResult.levelUp {
                LevelUpCard(levelUpReward: levelUp)
            }
            
            // New achievements
            if !progressionResult.newAchievements.isEmpty {
                AchievementsCard(achievements: progressionResult.newAchievements)
            }
        }
    }
}

// MARK: - Streak Bonus Card Component
struct StreakBonusCard: View {
    let streakInfo: EnhancedStreakInfo
    
    var body: some View {
        VStack(spacing: 8) {
            if streakInfo.hasQuestionStreakBonus {
                HStack(spacing: 8) {
                    Text("⚡")
                        .font(.title2)
                    
                    VStack(alignment: .leading) {
                        Text("\(streakInfo.questionStreak) question streak!")
                            .font(.headline)
                            .fontWeight(.bold)
                            .foregroundColor(.yellow)
                        
                        Text("×\(String(format: "%.1f", streakInfo.streakMultiplier)) score multiplier")
                            .font(.caption)
                            .foregroundColor(.yellow.opacity(0.8))
                    }
                }
            }
            
            if streakInfo.hasDailyStreakBonus {
                HStack(spacing: 8) {
                    Text("🔥")
                        .font(.title2)
                    
                    VStack(alignment: .leading) {
                        Text("\(streakInfo.dailyStreak) day streak!")
                            .font(.headline)
                            .fontWeight(.bold)
                            .foregroundColor(.orange)
                        
                        Text("×\(String(format: "%.1f", streakInfo.dailyStreakBonus)) daily bonus")
                            .font(.caption)
                            .foregroundColor(.orange.opacity(0.8))
                    }
                }
            }
        }
        .padding(12)
        .background(Color.yellow.opacity(0.1))
        .cornerRadius(12)
    }
}

// MARK: - Level Up Card Component
struct LevelUpCard: View {
    let levelUpReward: LevelUpInfo
    
    var body: some View {
        VStack(spacing: 8) {
            Text("🌟")
                .font(.system(size: 32))
            
            Text("LEVEL UP!")
                .font(.title)
                .fontWeight(.bold)
                .foregroundColor(.yellow)
            
            Text("Level \(levelUpReward.oldLevel) → \(levelUpReward.newLevel)")
                .font(.headline)
                .foregroundColor(.primary)
            
            Text("+\(levelUpReward.rewardXP) bonus points!")
                .font(.subheadline)
                .foregroundColor(.green)
        }
        .padding(16)
        .background(Color.yellow.opacity(0.2))
        .cornerRadius(16)
    }
}

// MARK: - Score Breakdown Card
struct ScoreBreakdownCard: View {
    let scoreBreakdown: ScoreBreakdown
    
    var body: some View {
        VStack(spacing: 8) {
            Text("+\(scoreBreakdown.totalScore) points")
                .font(.title2)
                .fontWeight(.bold)
                .foregroundColor(.green)
            
            if scoreBreakdown.timeBonus > 0 || scoreBreakdown.streakBonus > 0 {
                VStack(spacing: 4) {
                    UnifiedScoreRowView(label: "Base Score", value: scoreBreakdown.baseScore, color: .green)
                    
                    if scoreBreakdown.timeBonus > 0 {
                        UnifiedScoreRowView(label: "Time Bonus", value: scoreBreakdown.timeBonus, color: .blue)
                    }
                    
                    if scoreBreakdown.streakBonus > 0 {
                        UnifiedScoreRowView(label: "Streak Bonus", value: scoreBreakdown.streakBonus, color: .orange)
                    }
                    
                    if scoreBreakdown.difficultyMultiplier > 1.0 {
                        Text("×\(String(format: "%.1f", scoreBreakdown.difficultyMultiplier)) difficulty bonus")
                            .font(.caption)
                            .foregroundColor(.purple)
                    }
                }
                .padding(12)
                .background(Color.gray.opacity(0.1))
                .cornerRadius(8)
            }
            
            // XP gained
            Text("+\(scoreBreakdown.xpGained) XP")
                .font(.subheadline)
                .foregroundColor(.blue)
        }
    }
}

// MARK: - Score Row View
struct UnifiedScoreRowView: View {
    let label: String
    let value: Int
    let color: Color
    let isTotal: Bool
    
    init(label: String, value: Int, color: Color, isTotal: Bool = false) {
        self.label = label
        self.value = value
        self.color = color
        self.isTotal = isTotal
    }
    
    var body: some View {
        HStack {
            Text(label)
                .font(.system(size: isTotal ? 14 : 12, weight: isTotal ? .bold : .medium))
                .foregroundColor(.primary)
            
            Spacer()
            
            Text("+\(value)")
                .font(.system(size: isTotal ? 14 : 12, weight: isTotal ? .bold : .medium))
                .foregroundColor(color)
        }
    }
}

// MARK: - Achievements Card
struct AchievementsCard: View {
    let achievements: [Achievement]
    
    var body: some View {
        VStack(spacing: 8) {
            Text("🏆 Achievement\(achievements.count > 1 ? "s" : "") Unlocked!")
                .font(.headline)
                .fontWeight(.bold)
                .foregroundColor(.purple)
            
            ForEach(achievements.prefix(2), id: \.id) { achievement in
                HStack(spacing: 12) {
                    Text(achievement.icon)
                        .font(.title2)
                    
                    VStack(alignment: .leading, spacing: 2) {
                        Text(achievement.title)
                            .font(.subheadline)
                            .fontWeight(.bold)
                            .foregroundColor(.purple)
                        
                        Text(achievement.description)
                            .font(.caption)
                            .foregroundColor(.purple.opacity(0.7))
                    }
                    
                    Spacer()
                }
                .padding(8)
                .background(Color.purple.opacity(0.1))
                .cornerRadius(8)
            }
            
            if achievements.count > 2 {
                Text("+\(achievements.count - 2) more achievements!")
                    .font(.caption)
                    .foregroundColor(.purple)
            }
        }
    }
}

// MARK: - View Extension for Easy Integration
extension View {
    func withUnifiedFeedback(_ manager: UnifiedFeedbackManager) -> some View {
        self.overlay(
            UnifiedFeedbackOverlay(manager: manager)
                .animation(.spring(response: 0.6, dampingFraction: 0.8), value: manager.isShowingFeedback)
        )
    }
}
