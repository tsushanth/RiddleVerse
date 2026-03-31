//
//  FeedbackConfig.swift
//  PuzzleForge
//
//  Created by Sushanth Tiruvaipati on 6/18/25.
//


import SwiftUI
import AVFoundation

// MARK: - Enhanced Feedback Configuration
struct FeedbackConfig {
    let isCorrect: Bool
    let userAnswer: String
    let correctAnswer: String
    let showAnswerComparison: Bool
    let customMessage: String?
    let autoHideDuration: TimeInterval
    let scoreConfig: ScoreUpdateConfig?
    let onFeedbackComplete: () -> Void
    
    init(
        isCorrect: Bool,
        userAnswer: String,
        correctAnswer: String,
        showAnswerComparison: Bool = true,
        customMessage: String? = nil,
        autoHideDuration: TimeInterval = 3.0,
        scoreConfig: ScoreUpdateConfig? = nil,
        onFeedbackComplete: @escaping () -> Void = {}
    ) {
        self.isCorrect = isCorrect
        self.userAnswer = userAnswer
        self.correctAnswer = correctAnswer
        self.showAnswerComparison = showAnswerComparison
        self.customMessage = customMessage
        self.autoHideDuration = autoHideDuration
        self.scoreConfig = scoreConfig
        self.onFeedbackComplete = onFeedbackComplete
    }
}

// MARK: - Enhanced Feedback State Manager
class FeedbackState: ObservableObject {
    @Published var isVisible = false
    @Published var config: FeedbackConfig?
    
    func showFeedback(_ feedbackConfig: FeedbackConfig) {
        config = FeedbackConfig(
            isCorrect: feedbackConfig.isCorrect,
            userAnswer: feedbackConfig.userAnswer,
            correctAnswer: feedbackConfig.correctAnswer,
            showAnswerComparison: feedbackConfig.showAnswerComparison,
            customMessage: feedbackConfig.customMessage,
            autoHideDuration: feedbackConfig.autoHideDuration,
            scoreConfig: feedbackConfig.scoreConfig,
            onFeedbackComplete: {
                self.isVisible = false
                feedbackConfig.onFeedbackComplete()
            }
        )
        isVisible = true
    }
    
    func hideFeedback() {
        isVisible = false
    }
}

// MARK: - Universal Feedback Overlay
struct UniversalFeedbackOverlay: View {
    let config: FeedbackConfig
    let isVisible: Bool
    
    @StateObject private var progressionManager = ProgressionManager()
    @State private var progressionResult: ProgressionResult?
    @State private var isUpdatingScore = false
    @State private var iconScale: CGFloat = 0.1
    
    var body: some View {
        ZStack {
            if isVisible {
                // Background overlay
                Color.black.opacity(0.8)
                .ignoresSafeArea()
                .transition(.opacity)
                .onTapGesture {
                    print("🟡 Feedback background tapped - dismissing")
                    config.onFeedbackComplete()
                }
                
                // Feedback card
                VStack(spacing: 0) {
                    enhancedProgressionFeedbackContent
                }
                .frame(maxWidth: UIScreen.main.bounds.width * 0.9)
                .background(config.isCorrect ? Color.green.opacity(0.1) : Color.red.opacity(0.1))
                .cornerRadius(20)
                .overlay(
                    RoundedRectangle(cornerRadius: 20)
                        .stroke(config.isCorrect ? Color.green.opacity(0.3) : Color.red.opacity(0.3), lineWidth: 2)
                )
                .scaleEffect(isVisible ? 1.0 : 0.8)
                .transition(.scale.combined(with: .opacity))
                .onTapGesture {
                    // Also allow tapping the card itself to dismiss
                    print("🟡 Feedback card tapped - dismissing")
                    config.onFeedbackComplete()
                }
            }
        }
        .onAppear {
            if isVisible && config.scoreConfig != nil {
                processProgression()
            }
            
            // Play sound when feedback becomes visible
            if isVisible {
                if config.isCorrect {
                    playSound(named: "correct")
                } else {
                    playSound(named: "buzz")
                }
            }
            
            // Animate icon
            withAnimation(.spring(response: 0.5, dampingFraction: 0.6)) {
                iconScale = 1.0
            }
            
            // Auto-hide after duration
            DispatchQueue.main.asyncAfter(deadline: .now() + config.autoHideDuration) {
                config.onFeedbackComplete()
            }
        }
    }
    
    private var enhancedProgressionFeedbackContent: some View {
        VStack(spacing: 16) {
            // Animated icon
            Image(systemName: config.isCorrect ? "checkmark.circle.fill" : "xmark.circle.fill")
                .font(.system(size: 60))
                .foregroundColor(config.isCorrect ? .green : .red)
                .scaleEffect(iconScale)
                .animation(.spring(response: 0.5, dampingFraction: 0.6), value: iconScale)
            
            // Main result message
            Text(config.customMessage ?? (config.isCorrect ? "Correct!" : "Incorrect!"))
                .font(.system(size: 28, weight: .bold))
                .foregroundColor(config.isCorrect ? Color.green.opacity(0.8) : Color.red.opacity(0.8))
                .multilineTextAlignment(.center)
            
            // Progression content (only for correct answers)
            if config.isCorrect, let result = progressionResult {
                ProgressionContentView(progressionResult: result)
            }
            
            // Answer comparison (if enabled)
            if config.showAnswerComparison {
                AnswerComparisonView(config: config)
            }
            
            // Encouragement for wrong answers
            if !config.isCorrect {
                Text("Keep trying! You've got this! 💪")
                    .font(.system(size: 16, weight: .medium))
                    .foregroundColor(.secondary)
                    .multilineTextAlignment(.center)
            }
            
            // Score updating indicator
            if isUpdatingScore {
                ScoreUpdatingIndicator()
            }
            
            // Tap to dismiss hint (only for rapid games like SwipeWord)
            Text("Tap to continue")
                .font(.system(size: 12))
                .foregroundColor(.secondary)
                .opacity(0.7)
                .padding(.top, 8)
        }
        .padding(24)
    }
    
    private func processProgression() {
        guard let scoreConfig = config.scoreConfig else { return }
        
        progressionResult = progressionManager.processPuzzleCompletion(
            isCorrect: config.isCorrect,
            config: scoreConfig
        )
        
        AnalyticsManager.shared.track(.puzzleComplete(
            type: scoreConfig.puzzleType,
            difficulty: scoreConfig.difficulty,
            isCorrect: config.isCorrect,
            timeSpent: TimeInterval(scoreConfig.totalTime - scoreConfig.timeRemaining),
            score: progressionResult?.scoreBreakdown.totalScore ?? 0,
            questionIndex: 0, // You'll need to pass this from the puzzle view
            totalQuestions: 1  // You'll need to pass this from the puzzle view
        ))
        // Track level ups and achievements
        if let result = progressionResult {
            if let levelUp = result.levelUp {
                progressionManager.trackLevelUp(
                    oldLevel: levelUp.oldLevel,
                    newLevel: levelUp.newLevel,
                    totalXP: result.totalXPGained
                )
            }
            
            result.newAchievements.forEach { achievement in
                progressionManager.trackAchievementUnlocked(achievement)
            }
            
            progressionManager.trackStreakUpdate(result.streakInfo)
        }
        
        // Update backend score asynchronously
        if config.isCorrect, let result = progressionResult {
            isUpdatingScore = true
            Task {
                await updateBackendScore(scoreIncrease: result.scoreBreakdown.totalScore)
                await MainActor.run {
                    isUpdatingScore = false
                }
            }
        }
    }
    
    private func updateBackendScore(scoreIncrease: Int) async {
        // Implementation would match the backend update logic from PuzzleFeedbackManager
        print("Updating backend score: +\(scoreIncrease)")
    }
}

// MARK: - Progression Content Views
struct ProgressionContentView: View {
    let progressionResult: ProgressionResult
    
    var body: some View {
        VStack(spacing: 12) {
            // Score breakdown
            ScoreBreakdownView(scoreBreakdown: progressionResult.scoreBreakdown)
            
            // Level up notification
            if let levelUp = progressionResult.levelUp {
                LevelUpView(levelUp: levelUp)
            }
            
            // Streak display
            if progressionResult.streakInfo.currentStreak > 0 {
                StreakView(streakInfo: progressionResult.streakInfo)
            }
            
            // New achievements
            if !progressionResult.newAchievements.isEmpty {
                NewAchievementsView(achievements: progressionResult.newAchievements)
            }
        }
    }
}

struct ScoreBreakdownView: View {
    let scoreBreakdown: ScoreBreakdown
    
    var body: some View {
        VStack(spacing: 6) {
            // Total score display
            Text("+\(scoreBreakdown.totalScore) points")
                .font(.system(size: 24, weight: .bold))
                .foregroundColor(.green.opacity(0.8))
            
            // Breakdown details
            if scoreBreakdown.timeBonus > 0 || scoreBreakdown.streakBonus > 0 || scoreBreakdown.difficultyMultiplier > 1.0 {
                VStack(spacing: 4) {
                    ScoreRowView(label: "Base Score", value: scoreBreakdown.baseScore, color: .green.opacity(0.8))
                    
                    if scoreBreakdown.timeBonus > 0 {
                        ScoreRowView(label: "Time Bonus", value: scoreBreakdown.timeBonus, color: .blue)
                    }
                    
                    if scoreBreakdown.streakBonus > 0 {
                        ScoreRowView(label: "Streak Bonus", value: scoreBreakdown.streakBonus, color: .orange)
                    }
                    
                    if scoreBreakdown.difficultyMultiplier > 1.0 {
                        Text("\(Int(scoreBreakdown.difficultyMultiplier * 100))% difficulty")
                            .font(.system(size: 12, weight: .medium))
                            .foregroundColor(.purple)
                    }
                }
                .padding(12)
                .background(Color.white.opacity(0.7))
                .cornerRadius(12)
            }
            
            // XP gained
            Text("+\(scoreBreakdown.xpGained) XP")
                .font(.system(size: 16, weight: .medium))
                .foregroundColor(.blue)
        }
    }
}

struct LevelUpView: View {
    let levelUp: LevelUpInfo
    
    var body: some View {
        VStack(spacing: 8) {
            Image(systemName: "star.fill")
                .font(.system(size: 32))
                .foregroundColor(.yellow)
            
            Text("LEVEL UP!")
                .font(.system(size: 20, weight: .bold))
                .foregroundColor(.orange)
            
            Text("Level \(levelUp.oldLevel) → \(levelUp.newLevel)")
                .font(.system(size: 16, weight: .medium))
                .foregroundColor(.primary)
            
            Text("+\(levelUp.rewardXP) bonus points!")
                .font(.system(size: 14))
                .foregroundColor(.green.opacity(0.8))
        }
        .padding(16)
        .background(Color.yellow.opacity(0.1))
        .cornerRadius(12)
    }
}

struct StreakView: View {
    let streakInfo: StreakInfo
    
    var body: some View {
        if streakInfo.currentStreak >= 3 {
            HStack(spacing: 8) {
                Text("🔥")
                    .font(.system(size: 24))
                
                Text("\(streakInfo.currentStreak) streak!")
                    .font(.system(size: 18, weight: .bold))
                    .foregroundColor(.orange)
                
                if streakInfo.streakMultiplier > 1.0 {
                    Text("\(Int(streakInfo.streakMultiplier * 100))% bonus")
                        .font(.system(size: 14))
                        .foregroundColor(.orange.opacity(0.8))
                }
            }
        }
    }
}

struct NewAchievementsView: View {
    let achievements: [Achievement]
    
    var body: some View {
        VStack(spacing: 8) {
            Text("🏆 Achievement\(achievements.count > 1 ? "s" : "") Unlocked!")
                .font(.system(size: 18, weight: .bold))
                .foregroundColor(.purple)
            
            ForEach(Array(achievements.prefix(2).enumerated()), id: \.offset) { _, achievement in
                HStack(spacing: 12) {
                    Text(achievement.icon)
                        .font(.system(size: 24))
                    
                    VStack(alignment: .leading, spacing: 2) {
                        Text(achievement.title)
                            .font(.system(size: 14, weight: .bold))
                            .foregroundColor(.purple.opacity(0.8))
                        
                        Text(achievement.description)
                            .font(.system(size: 12))
                            .foregroundColor(.purple.opacity(0.6))
                    }
                    
                    Spacer()
                }
                .padding(12)
                .background(Color.purple.opacity(0.1))
                .cornerRadius(8)
            }
            
            if achievements.count > 2 {
                Text("+\(achievements.count - 2) more achievements!")
                    .font(.system(size: 12))
                    .foregroundColor(.purple)
            }
        }
    }
}

struct AnswerComparisonView: View {
    let config: FeedbackConfig
    
    var body: some View {
        VStack(spacing: 8) {
            Text("Your answer: \(config.userAnswer)")
                .font(.system(size: 16))
                .foregroundColor(.primary)
            
            Text("Correct answer: \(config.correctAnswer)")
                .font(.system(size: 16, weight: .medium))
                .foregroundColor(config.isCorrect ? .green.opacity(0.8) : .red.opacity(0.8))
        }
    }
}

struct ScoreUpdatingIndicator: View {
    var body: some View {
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

struct ScoreRowView: View {
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

// MARK: - Confetti Animation
struct ConfettiAnimation: View {
    @State private var isVisible = true
    let duration: TimeInterval
    
    init(duration: TimeInterval = 3.0) {
        self.duration = duration
    }
    
    var body: some View {
        ZStack {
            if isVisible {
                VStack {
                    Spacer()
                    Text("🎉✨🎊🌟💫")
                        .font(.system(size: 48))
                        .padding(16)
                    Spacer()
                }
                .transition(.opacity)
            }
        }
        .onAppear {
            DispatchQueue.main.asyncAfter(deadline: .now() + duration) {
                withAnimation(.easeOut(duration: 0.5)) {
                    isVisible = false
                }
            }
        }
    }
}

// MARK: - View Extensions
extension View {
    func withUniversalFeedback(_ feedbackState: FeedbackState) -> some View {
        self.overlay(
            Group {
                if let config = feedbackState.config {
                    UniversalFeedbackOverlay(
                        config: config,
                        isVisible: feedbackState.isVisible
                    )
                    .animation(.spring(response: 0.6, dampingFraction: 0.8), value: feedbackState.isVisible)
                }
            }
        )
    }
}

// MARK: - Sound Playing Function
func playSound(named name: String) {
    guard let soundURL = Bundle.main.url(forResource: name, withExtension: "mp3") else {
        print("❌ Sound file \(name).mp3 not found")
        return
    }
    
    do {
        let audioPlayer = try AVAudioPlayer(contentsOf: soundURL)
        audioPlayer.play()
    } catch {
        print("❌ Failed to play \(name): \(error)")
    }
}
