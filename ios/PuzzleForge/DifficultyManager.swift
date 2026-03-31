//
//  DifficultyManager.swift
//  PuzzleForge
//
//  Created by Sushanth Tiruvaipati on 8/17/25.
//


import SwiftUI
import Foundation
import Combine

// MARK: - Unified Difficulty Management
class DifficultyManager: ObservableObject {
    static let shared = DifficultyManager()
    
    enum DifficultyLevel: Int, CaseIterable {
        case beginner = 0, easy = 1, medium = 2, hard = 3, expert = 4
        
        var name: String {
            switch self {
            case .beginner: return "Beginner"
            case .easy: return "Easy"
            case .medium: return "Medium"
            case .hard: return "Hard"
            case .expert: return "Expert"
            }
        }
        
        var description: String {
            switch self {
            case .beginner: return "Perfect for learning the basics"
            case .easy: return "Gentle introduction with more time"
            case .medium: return "Balanced challenge for most players"
            case .hard: return "Requires focus and quick thinking"
            case .expert: return "Ultimate test of skill and speed"
            }
        }
        
        var timeLimit: Int {
            switch self {
            case .beginner: return 240  // Math puzzles need more time
            case .easy: return 210
            case .medium: return 180
            case .hard: return 150
            case .expert: return 120
            }
        }
        
        var livesAllowed: Int {
            switch self {
            case .beginner: return 5
            case .easy: return 4
            case .medium: return 3
            case .hard: return 2
            case .expert: return 1
            }
        }
        
        var basePoints: Int {
            switch self {
            case .beginner: return 30
            case .easy: return 50
            case .medium: return 100
            case .hard: return 150
            case .expert: return 200
            }
        }
        
        // Math-specific cognitive properties
        var numericalComplexity: Int {
            switch self {
            case .beginner: return 1
            case .easy: return 2
            case .medium: return 3
            case .hard: return 4
            case .expert: return 5
            }
        }
        
        var workingMemoryDemand: Bool {
            return self != .beginner
        }
        
        var proceduralComplexity: Bool {
            return rawValue >= DifficultyLevel.medium.rawValue
        }
        
        var abstractReasoning: Bool {
            return rawValue >= DifficultyLevel.hard.rawValue
        }
        
        var speedPressure: Int {
            return rawValue + 1
        }
        
        var errorInduction: Bool {
            return rawValue >= DifficultyLevel.medium.rawValue
        }
    }
    
    struct AdaptiveConfig {
        let level: DifficultyLevel
        let confidenceScore: Float
        let recommendedAdjustment: String
        let performanceMetrics: PerformanceMetrics
        let shouldNotify: Bool
        
        // Legacy compatibility
        var adjustmentReason: String { return recommendedAdjustment }
    }
    
    struct PerformanceMetrics {
        let accuracy: Float
        let averageTime: Float
        let streak: Int
        let totalAttempts: Int
        let cognitiveLoad: Float?
        let challengesCompleted: Int?
    }
    
    @Published private var currentDifficulties: [String: DifficultyLevel] = [:]
    private var performanceHistory: [String: [PerformanceRecord]] = [:]
    private let maxHistorySize = 10
    
    private struct PerformanceRecord {
        let accuracy: Float
        let timeSpent: Float
        let streakLength: Int
        let livesRemaining: Int
        let gameScore: Int
        let cognitiveLoad: Float
        let challengesCompleted: Int
        let timestamp: Date
    }
    
    init() {
        loadSavedDifficulties()
    }
    
    func getCurrentDifficulty(for puzzleType: String) -> DifficultyLevel {
        return currentDifficulties[puzzleType] ?? .medium
    }
    
    func updateDifficulty(for puzzleType: String, to level: DifficultyLevel) {
        currentDifficulties[puzzleType] = level
        saveDifficulties()
    }
    
    // Enhanced performance recording with cognitive analysis
    func recordPerformance(
        puzzleType: String,
        isCorrect: Bool,
        timeSpent: TimeInterval,
        difficulty: DifficultyLevel,
        streak: Int = 0,
        livesRemaining: Int = 0,
        gameScore: Int = 0,
        challengesCompleted: Int = 1
    ) -> AdaptiveConfig? {
        
        // Calculate cognitive load based on puzzle type and performance
        let cognitiveLoad = calculateCognitiveLoad(
            puzzleType: puzzleType,
            difficulty: difficulty,
            timeSpent: timeSpent,
            isCorrect: isCorrect
        )
        
        // Create performance record
        let record = PerformanceRecord(
            accuracy: isCorrect ? 1.0 : 0.0,
            timeSpent: Float(timeSpent),
            streakLength: streak,
            livesRemaining: livesRemaining,
            gameScore: gameScore,
            cognitiveLoad: cognitiveLoad,
            challengesCompleted: challengesCompleted,
            timestamp: Date()
        )
        
        // Add to history
        if performanceHistory[puzzleType] == nil {
            performanceHistory[puzzleType] = []
        }
        performanceHistory[puzzleType]?.append(record)
        
        // Trim history if needed
        if performanceHistory[puzzleType]?.count ?? 0 > maxHistorySize {
            performanceHistory[puzzleType]?.removeFirst()
        }
        
        // Analyze and adapt
        let adaptationResult = analyzeAndAdapt(for: puzzleType)
        
        if adaptationResult.level != difficulty {
            currentDifficulties[puzzleType] = adaptationResult.level
            saveDifficulties()
        }
        
        return adaptationResult
    }
    
    private func analyzeAndAdapt(for puzzleType: String) -> AdaptiveConfig {
        guard let history = performanceHistory[puzzleType],
              history.count >= 3 else {
            let currentLevel = getCurrentDifficulty(for: puzzleType)
            return AdaptiveConfig(
                level: currentLevel,
                confidenceScore: 0.0,
                recommendedAdjustment: "Building confidence with \(puzzleType)...",
                performanceMetrics: PerformanceMetrics(
                    accuracy: 0.0,
                    averageTime: 0.0,
                    streak: 0,
                    totalAttempts: 0,
                    cognitiveLoad: nil,
                    challengesCompleted: nil
                ),
                shouldNotify: false
            )
        }
        
        let currentLevel = getCurrentDifficulty(for: puzzleType)
        let recentWindow = Array(history.suffix(5))
        
        // Calculate metrics
        let avgAccuracy = recentWindow.map { $0.accuracy }.reduce(0, +) / Float(recentWindow.count)
        let avgTime = recentWindow.map { $0.timeSpent }.reduce(0, +) / Float(recentWindow.count)
        let avgCognitiveLoad = recentWindow.map { $0.cognitiveLoad }.reduce(0, +) / Float(recentWindow.count)
        let consistentSuccess = recentWindow.filter { $0.accuracy >= 0.85 }.count >= 3
        let consistentFailure = recentWindow.filter { $0.accuracy <= 0.4 }.count >= 3
        
        let expectedTime = Float(currentLevel.timeLimit) / Float(6) // Expected time per challenge
        
        var newLevel = currentLevel
        var reason = "Performance stable at \(currentLevel.name)"
        var confidence: Float = 0.3
        var shouldNotify = false
        
        // Advanced adaptation logic based on puzzle type
        if puzzleType == "mathComparison" {
            // Math-specific adaptation
            if avgAccuracy >= 0.85 && consistentSuccess && avgTime < expectedTime * 0.6 &&
               avgCognitiveLoad < 0.7 && currentLevel != .expert {
                newLevel = DifficultyLevel(rawValue: currentLevel.rawValue + 1) ?? currentLevel
                reason = "🎯 Excellent mathematical reasoning - advancing to \(newLevel.name)"
                confidence = 0.8
                shouldNotify = true
            } else if avgAccuracy <= 0.3 && consistentFailure && currentLevel != .beginner {
                newLevel = DifficultyLevel(rawValue: currentLevel.rawValue - 1) ?? currentLevel
                reason = "🔧 Building confidence at \(newLevel.name) level"
                confidence = 0.7
                shouldNotify = true
            }
        } else {
            // General adaptation for other puzzle types
            if avgAccuracy >= 0.8 && avgTime < expectedTime * 0.7 && currentLevel != .expert {
                newLevel = DifficultyLevel(rawValue: currentLevel.rawValue + 1) ?? currentLevel
                reason = "🚀 Excellent performance - increasing challenge to \(newLevel.name)"
                confidence = 0.75
                shouldNotify = true
            } else if avgAccuracy <= 0.4 && currentLevel != .beginner {
                newLevel = DifficultyLevel(rawValue: currentLevel.rawValue - 1) ?? currentLevel
                reason = "📚 Adjusting to \(newLevel.name) for better learning"
                confidence = 0.65
                shouldNotify = true
            }
        }
        
        let metrics = PerformanceMetrics(
            accuracy: avgAccuracy,
            averageTime: avgTime,
            streak: recentWindow.last?.streakLength ?? 0,
            totalAttempts: recentWindow.count,
            cognitiveLoad: avgCognitiveLoad,
            challengesCompleted: recentWindow.map { $0.challengesCompleted }.reduce(0, +)
        )
        
        return AdaptiveConfig(
            level: newLevel,
            confidenceScore: confidence,
            recommendedAdjustment: reason,
            performanceMetrics: metrics,
            shouldNotify: shouldNotify
        )
    }
    
    private func calculateCognitiveLoad(
        puzzleType: String,
        difficulty: DifficultyLevel,
        timeSpent: TimeInterval,
        isCorrect: Bool
    ) -> Float {
        let baseLoad = Float(difficulty.rawValue + 1) * 0.2 // 0.2 to 1.0
        let timeStress = min(Float(timeSpent) / Float(difficulty.timeLimit), 1.0)
        let accuracyPenalty = isCorrect ? 0.0 : 0.3
        
        // Puzzle-specific adjustments
        let puzzleMultiplier: Float = {
            switch puzzleType {
            case "mathComparison": return 1.2 // Math requires more cognitive load
            case "pinballDeflector": return 1.1 // Physics reasoning
            case "numberSequence": return 0.9 // Pattern recognition
            case "numbersum": return 1.0 // Standard
            default: return 1.0
            }
        }()
        
        return min((baseLoad + timeStress + Float(accuracyPenalty)) * puzzleMultiplier, 1.0)
    }
    
    private func loadSavedDifficulties() {
        if let data = UserDefaults.standard.data(forKey: "unified_difficulty_levels"),
           let decoded = try? JSONDecoder().decode([String: Int].self, from: data) {
            for (puzzleType, rawValue) in decoded {
                if let level = DifficultyLevel(rawValue: rawValue) {
                    currentDifficulties[puzzleType] = level
                }
            }
        }
    }
    
    private func saveDifficulties() {
        let encoded = currentDifficulties.mapValues { $0.rawValue }
        if let data = try? JSONEncoder().encode(encoded) {
            UserDefaults.standard.set(data, forKey: "unified_difficulty_levels")
        }
    }
    
    // MARK: - Legacy compatibility methods
    
    @available(*, deprecated, message: "Use recordPerformance with enhanced parameters")
    func recordPerformance(
        puzzleType: String,
        isCorrect: Bool,
        timeSpent: TimeInterval,
        difficulty: DifficultyLevel
    ) -> AdaptiveConfig? {
        return recordPerformance(
            puzzleType: puzzleType,
            isCorrect: isCorrect,
            timeSpent: timeSpent,
            difficulty: difficulty,
            streak: 0,
            livesRemaining: 0,
            gameScore: 0,
            challengesCompleted: 1
        )
    }
}

// MARK: - Competitive Insights
struct CompetitiveInsight {
    let percentile: Int
    let ranking: String
    let improvement: String
    let globalAverage: Float
    let userScore: Float
}

class CompetitiveRankingManager: ObservableObject {
    static let shared = CompetitiveRankingManager()
    
    func getCompetitiveInsight(
        userId: String,
        puzzleType: String,
        difficulty: String
    ) async -> CompetitiveInsight? {
        // Simulated competitive data - in real app would fetch from server
        return CompetitiveInsight(
            percentile: Int.random(in: 40...95),
            ranking: ["Bronze", "Silver", "Gold", "Platinum", "Diamond"].randomElement()!,
            improvement: ["+5%", "+12%", "+3%", "+18%"].randomElement()!,
            globalAverage: Float.random(in: 65...85),
            userScore: Float.random(in: 70...95)
        )
    }
}

// MARK: - Session Statistics
struct SessionStatistics {
    let correctAnswers: Int
    let totalAnswers: Int
    let totalTimeSeconds: Int
    let bestStreak: Int
    let currentStreak: Int
    let totalScore: Int
    let individualTimes: [Int]
    let puzzleType: String
    
    // Computed properties for derived values
    var winRate: Float {
        return totalAnswers > 0 ? Float(correctAnswers) / Float(totalAnswers) : 0.0
    }
    
    var averageTimePerPuzzle: Int {
        return totalAnswers > 0 ? totalTimeSeconds / totalAnswers : 0
    }
    
    // Optional: Average time from individual times if available
    var averageTimeFromIndividualTimes: Float {
        return individualTimes.isEmpty ? 0.0 : Float(individualTimes.reduce(0, +)) / Float(individualTimes.count)
    }
    
    // Optional: Success rate as percentage
    var winRatePercentage: Int {
        return Int(winRate * 100)
    }
}

// MARK: - Unified Header Component
struct AdaptiveUnifiedHeader: View {
    let puzzleType: String
    let currentDifficulty: DifficultyManager.DifficultyLevel
    let score: Int
    let challengeNumber: Int
    let totalChallenges: Int
    let timer: String
    let lives: Int
    let competitiveInsight: CompetitiveInsight?
    let level: UserLevel?
    let streakInfo: StreakInfo?
    let onBack: () -> Void
    let onPause: () -> Void
    let onHint: () -> Void
    
    var body: some View {
        VStack(spacing: 12) {
            // Top row with navigation and key info
            HStack {
                Button(action: onBack) {
                    HStack(spacing: 6) {
                        Image(systemName: "arrow.left")
                            .font(.headline)
                        Text("Back")
                            .font(.subheadline)
                    }
                    .foregroundColor(.white)
                }
                
                Spacer()
                
                // Center: Difficulty and progress
                VStack(alignment: .center, spacing: 2) {
                    Text(currentDifficulty.name.uppercased())
                        .font(.caption)
                        .fontWeight(.bold)
                        .foregroundColor(.cyan)
                    
                    Text("\(challengeNumber)/\(totalChallenges)")
                        .font(.caption)
                        .foregroundColor(.white.opacity(0.8))
                    
                    // Show streak if available
                    if let streakInfo = streakInfo, streakInfo.currentStreak > 0 {
                        Text("🔥 \(streakInfo.currentStreak)")
                            .font(.caption2)
                            .foregroundColor(.orange)
                            .fontWeight(.bold)
                    }
                }
                
                Spacer()
                
                HStack(spacing: 8) {
                    Button(action: onHint) {
                        Image(systemName: "lightbulb.fill")
                            .font(.caption)
                            .foregroundColor(.yellow)
                            .frame(width: 28, height: 28)
                            .background(Color.white.opacity(0.2))
                            .cornerRadius(14)
                    }
                    
                    Button(action: onPause) {
                        Image(systemName: "pause.fill")
                            .font(.caption)
                            .foregroundColor(.white)
                            .frame(width: 28, height: 28)
                            .background(Color.white.opacity(0.2))
                            .cornerRadius(14)
                    }
                }
            }
            
            // Stats row
            HStack {
                // Level and Timer
                VStack(alignment: .leading, spacing: 2) {
                    if let level = level {
                        Text("LEVEL \(level.level)")
                            .font(.caption2)
                            .foregroundColor(.white.opacity(0.7))
                        
                        // Level progress bar
                        LevelProgressBar(level: level)
                            .frame(width: 60, height: 4)
                    }
                    
                    Text("TIME")
                        .font(.caption2)
                        .foregroundColor(.white.opacity(0.7))
                    Text(timer)
                        .font(.subheadline)
                        .fontWeight(.bold)
                        .foregroundColor(isTimerCritical(timer) ? .red : .white)
                }
                
                Spacer()
                
                // Score
                if score > 0 {
                    VStack(alignment: .center, spacing: 2) {
                        Text("SCORE")
                            .font(.caption2)
                            .foregroundColor(.white.opacity(0.7))
                        Text("\(score)")
                            .font(.subheadline)
                            .fontWeight(.bold)
                            .foregroundColor(Color(red: 1.0, green: 0.92, blue: 0.23))
                    }
                    
                    Spacer()
                }
                
                // Competitive insight
                if let insight = competitiveInsight {
                    VStack(alignment: .center, spacing: 2) {
                        Text("RANK")
                            .font(.caption2)
                            .foregroundColor(.white.opacity(0.7))
                        Text(insight.ranking)
                            .font(.caption)
                            .fontWeight(.bold)
                            .foregroundColor(.yellow)
                        
                        Text("\(insight.percentile)%")
                            .font(.caption2)
                            .foregroundColor(.white.opacity(0.6))
                    }
                    
                    Spacer()
                }
                
                // Lives
                VStack(alignment: .trailing, spacing: 2) {
                    Text("LIVES")
                        .font(.caption2)
                        .foregroundColor(.white.opacity(0.7))
                    HStack(spacing: 2) {
                        ForEach(0..<currentDifficulty.livesAllowed, id: \.self) { index in
                            Text(index < lives ? "❤️" : "🤍")
                                .font(.caption)
                        }
                    }
                }
            }
        }
        .padding()
        .background(
            RoundedRectangle(cornerRadius: 12)
                .fill(Color.black.opacity(0.3))
                .overlay(
                    RoundedRectangle(cornerRadius: 12)
                        .stroke(Color.white.opacity(0.2), lineWidth: 1)
                )
        )
        .padding(.horizontal)
    }
    
    private func isTimerCritical(_ timer: String) -> Bool {
        let components = timer.split(separator: ":")
        guard components.count == 2,
              let minutes = Int(components[0]),
              let seconds = Int(components[1]) else { return false }
        
        let totalSeconds = minutes * 60 + seconds
        return totalSeconds <= 30
    }
}

// MARK: - Adaptation Notification
struct UnifiedAdaptationNotification: View {
    let adaptationInfo: DifficultyManager.AdaptiveConfig?
    let puzzleType: String
    let visible: Bool
    let onDismiss: () -> Void
    
    var body: some View {
        if visible, let info = adaptationInfo {
            VStack(spacing: 8) {
                HStack {
                    Image(systemName: "chart.line.uptrend.xyaxis")
                        .foregroundColor(.blue)
                    
                    Text("Difficulty Adjusted")
                        .font(.headline)
                        .fontWeight(.bold)
                        .foregroundColor(.primary)
                    
                    Spacer()
                    
                    Button(action: onDismiss) {
                        Image(systemName: "xmark")
                            .foregroundColor(.secondary)
                    }
                }
                
                Text(info.recommendedAdjustment)
                    .font(.subheadline)
                    .foregroundColor(.secondary)
                    .multilineTextAlignment(.leading)
                
                HStack {
                    Text("New Level: \(info.level.name)")
                        .font(.caption)
                        .foregroundColor(.blue)
                        .fontWeight(.medium)
                    
                    Spacer()
                    
                    Text("Confidence: \(Int(info.confidenceScore * 100))%")
                        .font(.caption)
                        .foregroundColor(.secondary)
                }
            }
            .padding()
            .background(
                RoundedRectangle(cornerRadius: 12)
                    .fill(Color.blue.opacity(0.1))
                    .overlay(
                        RoundedRectangle(cornerRadius: 12)
                            .stroke(Color.blue.opacity(0.3), lineWidth: 1)
                    )
            )
            .padding(.horizontal)
            .transition(.slide.combined(with: .opacity))
            .animation(.spring(), value: visible)
        }
    }
}

// MARK: - Session Completion Handler
struct UnifiedSessionCompletionHandler: View {
    let puzzleType: String
    let sessionScore: Int
    let sessionStats: SessionStatistics
    let currentDifficulty: DifficultyManager.DifficultyLevel
    let onComplete: (SessionResult) -> Void
    
    struct SessionResult {
        let shouldShowRanking: Bool
        let finalScore: Int
        let achievements: [String]
    }
    
    @State private var showCompletion = false
    
    var body: some View {
        if showCompletion {
            completionOverlay
        } else {
            Color.clear
                .onAppear {
                    DispatchQueue.main.asyncAfter(deadline: .now() + 0.5) {
                        showCompletion = true
                    }
                }
        }
    }
    
    private var completionOverlay: some View {
        ZStack {
            Color.black.opacity(0.7)
                .ignoresSafeArea()
            
            VStack(spacing: 20) {
                Text("Session Complete!")
                    .font(.title2)
                    .fontWeight(.bold)
                    .foregroundColor(.white)
                
                VStack(spacing: 12) {
                    HStack {
                        Text("Final Score:")
                            .foregroundColor(.white.opacity(0.8))
                        Spacer()
                        Text("\(sessionScore)")
                            .fontWeight(.bold)
                            .foregroundColor(.yellow)
                    }
                    
                    HStack {
                        Text("Accuracy:")
                            .foregroundColor(.white.opacity(0.8))
                        Spacer()
                        Text("\(Int(sessionStats.winRate * 100))%")
                            .fontWeight(.bold)
                            .foregroundColor(.green)
                    }
                    
                    HStack {
                        Text("Best Streak:")
                            .foregroundColor(.white.opacity(0.8))
                        Spacer()
                        Text("\(sessionStats.bestStreak)")
                            .fontWeight(.bold)
                            .foregroundColor(.orange)
                    }
                }
                .padding()
                .background(
                    RoundedRectangle(cornerRadius: 12)
                        .fill(Color.white.opacity(0.1))
                )
                
                Button(action: {
                    let result = SessionResult(
                        shouldShowRanking: sessionStats.winRate > 0.7,
                        finalScore: sessionScore,
                        achievements: generateAchievements()
                    )
                    onComplete(result)
                }) {
                    Text("Continue")
                        .font(.headline)
                        .fontWeight(.bold)
                        .foregroundColor(.white)
                        .frame(maxWidth: .infinity)
                        .padding()
                        .background(Color.blue)
                        .cornerRadius(12)
                }
            }
            .padding()
            .background(
                RoundedRectangle(cornerRadius: 16)
                    .fill(Color.black.opacity(0.9))
            )
            .padding()
        }
        .transition(.scale.combined(with: .opacity))
        .animation(.spring(), value: showCompletion)
    }
    
    private func generateAchievements() -> [String] {
        var achievements: [String] = []
        
        if sessionStats.winRate >= 0.9 {
            achievements.append("Perfect Performance")
        }
        if sessionStats.bestStreak >= 10 {
            achievements.append("Streak Master")
        }
        if currentDifficulty == .expert {
            achievements.append("Expert Level")
        }
        
        return achievements
    }
}
