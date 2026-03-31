//
//  FeedbackManager.swift
//  PuzzleForge
//
//  Created by Sushanth Tiruvaipati on 8/6/25.
//

import Foundation
import SwiftUI
import AVFoundation

class FeedbackManager: ObservableObject {
    private let progressionEngine: ProgressionEngine
    private let soundManager: SoundManager
    
    // UI State management
    @Published var isShowingFeedback: Bool = false
    @Published var isProcessing: Bool = false
    @Published var animateIcon: Bool = false
    @Published var showConfetti: Bool = false
    @Published var currentFeedback: FeedbackData?
    
    init(progressionEngine: ProgressionEngine = ProgressionEngine()) {
        self.progressionEngine = progressionEngine
        self.soundManager = SoundManager()
    }
    
    // MARK: - Main Feedback Interface
    
    func showPuzzleCompletionFeedback(
        puzzleType: String,
        isCorrect: Bool,
        userAnswer: String,
        correctAnswer: String,
        score: Int = 0,
        timeSpentSeconds: Int,
        timeRemaining: Int = 0,
        totalTime: Int = 60,
        difficulty: String = "Medium",
        hintsUsed: Int = 0,
        customMessage: String? = nil,
        onComplete: @escaping () -> Void = {}
    ) {
        
        print("🎭 Showing feedback: \(puzzleType), correct=\(isCorrect)")
        
        isProcessing = true
        
        // Process progression through engine
        let progressionResult = progressionEngine.processGameResult(
            puzzleType: puzzleType,
            score: score,
            timeSpent: timeSpentSeconds,
            isCorrect: isCorrect,
            difficulty: difficulty
        )
        isProcessing = false
        
        // Create feedback data
        let feedbackData = FeedbackData(
            isCorrect: isCorrect,
            userAnswer: userAnswer,
            correctAnswer: correctAnswer,
            progressionResult: progressionResult,
            puzzleType: puzzleType,
            customMessage: customMessage,
            onComplete: onComplete
        )
        
        // Display the feedback
        displayFeedback(feedbackData: feedbackData)
    }
    
    func showSimpleFeedback(
        isCorrect: Bool,
        userAnswer: String,
        correctAnswer: String,
        message: String? = nil,
        autoHideDuration: TimeInterval = 3.0,
        onComplete: @escaping () -> Void = {}
    ) {
        print("🎭 Showing simple feedback: correct=\(isCorrect)")
        
        let feedbackData = FeedbackData(
            isCorrect: isCorrect,
            userAnswer: userAnswer,
            correctAnswer: correctAnswer,
            progressionResult: nil,
            puzzleType: "",
            customMessage: message,
            onComplete: onComplete
        )
        
        displayFeedback(feedbackData: feedbackData, autoHideDuration: autoHideDuration)
    }
    
    // MARK: - Private Feedback Display Logic
    
    private func displayFeedback(
        feedbackData: FeedbackData,
        autoHideDuration: TimeInterval = 0
    ) {
        print("🎬 Displaying feedback UI")
        
        // Set feedback data and show UI
        currentFeedback = feedbackData
        isShowingFeedback = true
        
        // Play appropriate sound effect
        let soundEffect = determineSoundEffect(feedbackData: feedbackData)
        soundManager.playSound(effect: soundEffect)
        
        // Start icon animation
        animateIcon = true
        
        // Show confetti for special achievements
        if shouldShowConfetti(feedbackData: feedbackData) {
            showConfetti = true
        }
        
        // Determine auto-hide duration
        let duration = autoHideDuration > 0 ? autoHideDuration : calculateAutoHideDuration(feedbackData: feedbackData)
        
        // Schedule auto-hide
        DispatchQueue.main.asyncAfter(deadline: .now() + duration) {
            self.hideFeedback()
        }
    }
    
    func hideFeedback() {
        print("🚪 Hiding feedback")
        
        let feedback = currentFeedback
        
        // Reset UI state
        isShowingFeedback = false
        animateIcon = false
        showConfetti = false
        
        // Call completion handler
        feedback?.onComplete?()
        
        // Clear current feedback
        currentFeedback = nil
    }
    
    // MARK: - Feedback Customization Logic
    
    private func determineSoundEffect(feedbackData: FeedbackData) -> SoundEffect {
        guard feedbackData.isCorrect else { return .incorrect }
        
        let progressionResult = feedbackData.progressionResult
        
        // Priority order for sound selection
        switch true {
        case progressionResult?.levelUp != nil:
            return .levelUp
        case progressionResult?.newAchievements.isEmpty == false:
            return .achievement
        case (progressionResult?.streakInfo.dailyStreak ?? 0) >= 7:
            return .dailyStreak
        case (progressionResult?.streakInfo.currentStreak ?? 0) >= 10:
            return .streakLarge
        case (progressionResult?.streakInfo.currentStreak ?? 0) >= 5:
            return .streakMedium
        case (progressionResult?.streakInfo.currentStreak ?? 0) >= 3:
            return .streakSmall
        case (progressionResult?.scoreBreakdown.timeBonus ?? 0) > Int((progressionResult?.scoreBreakdown.baseScore ?? 0) * 40 / 100):
            return .perfect
        case (progressionResult?.scoreBreakdown.timeBonus ?? 0) > 0:
            return .timeBonus
        default:
            return .correct
        }
    }
    
    private func shouldShowConfetti(feedbackData: FeedbackData) -> Bool {
        guard feedbackData.isCorrect else { return false }
        
        guard let progressionResult = feedbackData.progressionResult else { return false }
        
        return progressionResult.levelUp != nil ||
               !progressionResult.newAchievements.isEmpty ||
               progressionResult.streakInfo.currentStreak >= 5 ||
               progressionResult.streakInfo.dailyStreak >= 7
    }
    
    private func calculateAutoHideDuration(feedbackData: FeedbackData) -> TimeInterval {
        guard feedbackData.isCorrect else { return 2.0 }
        
        guard let progressionResult = feedbackData.progressionResult else { return 3.0 }
        
        // Longer display for special achievements
        switch true {
        case progressionResult.levelUp != nil:
            return 5.0
        case !progressionResult.newAchievements.isEmpty:
            return 4.5
        case shouldShowConfetti(feedbackData: feedbackData):
            return 4.0
        case progressionResult.streakInfo.currentStreak >= 3:
            return 3.5
        default:
            return 3.0
        }
    }
    
    // MARK: - Sound Management
    
    func playSound(effect: SoundEffect) {
        soundManager.playSound(effect: effect)
    }
}

// MARK: - Sound Manager

private class SoundManager {
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
            } else {
                print("⚠️ Sound file not found: \(effect.rawValue).mp3")
            }
        }
    }
    
    func playSound(effect: SoundEffect) {
        if let player = audioPlayers[effect] {
            player.stop()
            player.currentTime = 0
            player.play()
            print("🔊 Playing sound: \(effect.rawValue)")
        } else {
            playFallbackSound(effect: effect)
        }
    }
    
    private func playFallbackSound(effect: SoundEffect) {
        // Use system sounds as fallback
        switch effect {
        case .correct, .perfect, .timeBonus:
            AudioServicesPlaySystemSound(1016) // Positive sound
        case .incorrect:
            AudioServicesPlaySystemSound(1053) // Negative sound
        case .levelUp, .achievement:
            AudioServicesPlaySystemSound(1017) // Achievement sound
        case .streakSmall, .streakMedium, .streakLarge, .dailyStreak:
            AudioServicesPlaySystemSound(1016) // Positive sound
        }
    }
}

// MARK: - SwiftUI Components

struct FeedbackOverlayView: View {
    @ObservedObject var feedbackManager: FeedbackManager
    
    var body: some View {
        ZStack {
            if feedbackManager.isShowingFeedback, let feedback = feedbackManager.currentFeedback {
                Color.black.opacity(0.3)
                    .ignoresSafeArea()
                    .onTapGesture {
                        feedbackManager.hideFeedback()
                    }
                
                VStack(spacing: 20) {
                    // Main feedback card
                    FeedbackCard(
                        feedback: feedback,
                        animateIcon: feedbackManager.animateIcon
                    )
                    .scaleEffect(feedbackManager.animateIcon ? 1.1 : 1.0)
                    .animation(.spring(response: 0.5, dampingFraction: 0.8), value: feedbackManager.animateIcon)
                }
                .transition(.scale.combined(with: .opacity))
                .animation(.easeInOut(duration: 0.3), value: feedbackManager.isShowingFeedback)
            }
            
            // Confetti overlay
            if feedbackManager.showConfetti {
                ConfettiView()
                    .ignoresSafeArea()
                    .allowsHitTesting(false)
            }
        }
    }
}

struct FeedbackCard: View {
    let feedback: FeedbackData
    let animateIcon: Bool
    
    var body: some View {
        VStack(spacing: 16) {
            // Icon
            Text(feedback.isCorrect ? "✅" : "❌")
                .font(.system(size: 60))
                .rotationEffect(.degrees(animateIcon ? 360 : 0))
                .animation(.easeInOut(duration: 0.8), value: animateIcon)
            
            // Main message
            Text(feedback.mainMessage)
                .font(.title2)
                .fontWeight(.bold)
                .foregroundColor(.primary)
                .multilineTextAlignment(.center)
            
            // Answer details
            if feedback.userAnswer != feedback.correctAnswer {
                VStack(spacing: 8) {
                    HStack {
                        Text("Your answer:")
                            .font(.caption)
                            .foregroundColor(.secondary)
                        Spacer()
                        Text(feedback.userAnswer)
                            .font(.subheadline)
                            .fontWeight(.medium)
                    }
                    
                    HStack {
                        Text("Correct answer:")
                            .font(.caption)
                            .foregroundColor(.secondary)
                        Spacer()
                        Text(feedback.correctAnswer)
                            .font(.subheadline)
                            .fontWeight(.medium)
                            .foregroundColor(.green)
                    }
                }
                .padding()
                .background(Color.gray.opacity(0.1))
                .cornerRadius(8)
            }
            
            // Progression details
            if feedback.showProgressionDetails, let result = feedback.progressionResult {
                ProgressionDetailsView(result: result)
            }
        }
        .padding(24)
        .background(
            RoundedRectangle(cornerRadius: 16)
                .fill(Color(.systemBackground))
                .shadow(color: .black.opacity(0.2), radius: 20, x: 0, y: 10)
        )
        .padding(.horizontal, 40)
    }
}

struct ProgressionDetailsView: View {
    let result: ProgressionResult
    
    var body: some View {
        VStack(spacing: 12) {
            Divider()
            
            // Score breakdown
            VStack(spacing: 8) {
                Text("Score Breakdown")
                    .font(.headline)
                    .foregroundColor(.primary)
                
                HStack {
                    Text("Base Score:")
                    Spacer()
                    Text("\(result.scoreBreakdown.baseScore)")
                        .fontWeight(.medium)
                }
                
                if result.scoreBreakdown.timeBonus > 0 {
                    HStack {
                        Text("Time Bonus:")
                        Spacer()
                        Text("+\(result.scoreBreakdown.timeBonus)")
                            .fontWeight(.medium)
                            .foregroundColor(.blue)
                    }
                }
                
                if result.scoreBreakdown.streakBonus > 0 {
                    HStack {
                        Text("Streak Bonus:")
                        Spacer()
                        Text("+\(result.scoreBreakdown.streakBonus)")
                            .fontWeight(.medium)
                            .foregroundColor(.orange)
                    }
                }
                
                if result.scoreBreakdown.difficultyMultiplier > 1.0 {
                    HStack {
                        Text("Difficulty Multiplier:")
                        Spacer()
                        Text("×\(String(format: "%.1f", result.scoreBreakdown.difficultyMultiplier))")
                            .fontWeight(.medium)
                            .foregroundColor(.purple)
                    }
                }
                
                Divider()
                
                HStack {
                    Text("Total XP:")
                        .fontWeight(.bold)
                    Spacer()
                    Text("+\(result.totalXPGained)")
                        .font(.title3)
                        .fontWeight(.bold)
                        .foregroundColor(.green)
                }
            }
            .font(.subheadline)
            
            // Level up notification
            if let levelUp = result.levelUp {
                VStack(spacing: 4) {
                    Text("🎉 LEVEL UP! 🎉")
                        .font(.headline)
                        .fontWeight(.bold)
                        .foregroundColor(.yellow)
                    
                    Text("Level \(levelUp.oldLevel) → \(levelUp.newLevel)")
                        .font(.subheadline)
                        .fontWeight(.medium)
                }
                .padding()
                .background(
                    RoundedRectangle(cornerRadius: 8)
                        .fill(Color.yellow.opacity(0.2))
                )
            }
            
            // New achievements
            if !result.newAchievements.isEmpty {
                VStack(spacing: 8) {
                    Text("🏆 New Achievements!")
                        .font(.headline)
                        .fontWeight(.bold)
                        .foregroundColor(.orange)
                    
                    ForEach(result.newAchievements, id: \.id) { achievement in
                        HStack {
                            Text(achievement.icon)
                                .font(.title2)
                            
                            VStack(alignment: .leading, spacing: 2) {
                                Text(achievement.title)
                                    .font(.subheadline)
                                    .fontWeight(.medium)
                                
                                Text(achievement.description)
                                    .font(.caption)
                                    .foregroundColor(.secondary)
                            }
                            
                            Spacer()
                        }
                        .padding(8)
                        .background(
                            RoundedRectangle(cornerRadius: 6)
                                .fill(Color.orange.opacity(0.1))
                        )
                    }
                }
            }
            
            // Streak info
            if result.streakInfo.currentStreak > 1 {
                VStack(spacing: 4) {
                    Text("🔥 Streak: \(result.streakInfo.currentStreak)")
                        .font(.subheadline)
                        .fontWeight(.medium)
                        .foregroundColor(.red)
                    
                    if result.streakInfo.streakMultiplier > 1.0 {
                        Text("Score multiplier: ×\(String(format: "%.1f", result.streakInfo.streakMultiplier))")
                            .font(.caption)
                            .foregroundColor(.secondary)
                    }
                }
                .padding(8)
                .background(
                    RoundedRectangle(cornerRadius: 6)
                        .fill(Color.red.opacity(0.1))
                )
            }
        }
    }
}

struct ConfettiView: View {
    @State private var animate = false
    
    var body: some View {
        ZStack {
            ForEach(0..<50, id: \.self) { _ in
                Circle()
                    .fill(Color.random)
                    .frame(width: 8, height: 8)
                    .position(
                        x: CGFloat.random(in: 0...UIScreen.main.bounds.width),
                        y: animate ? UIScreen.main.bounds.height + 50 : -50
                    )
                    .animation(
                        .linear(duration: Double.random(in: 2...4))
                        .delay(Double.random(in: 0...1)),
                        value: animate
                    )
            }
        }
        .onAppear {
            animate = true
        }
    }
}

extension Color {
    static var random: Color {
        return Color(
            red: .random(in: 0...1),
            green: .random(in: 0...1),
            blue: .random(in: 0...1)
        )
    }
}

// MARK: - Compose Integration Helpers

@MainActor
class SimpleFeedbackState: ObservableObject {
    @Published var isVisible: Bool = false
    @Published var message: String = ""
    @Published var isCorrect: Bool = false
    
    func showFeedback(message: String, isCorrect: Bool, duration: TimeInterval = 2.0) {
        self.message = message
        self.isCorrect = isCorrect
        self.isVisible = true
        
        // Auto-hide
        DispatchQueue.main.asyncAfter(deadline: .now() + duration) {
            self.isVisible = false
        }
    }
    
    func hideFeedback() {
        isVisible = false
    }
}
