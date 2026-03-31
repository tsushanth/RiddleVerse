//
//  ColorTextMatchingPuzzleView.swift
//  PuzzleForge
//
//  Created by Sushanth Tiruvaipati on 7/27/25.
//


//
//  ColorTextMatchingPuzzleView.swift
//  PuzzleForge
//
//  Created by Assistant on 1/27/25.
//

//
//  ColorTextMatchingPuzzleView.swift
//  PuzzleForge
//
//  Enhanced with adaptive difficulty progression
//

import SwiftUI
import Foundation
import AVFoundation
extension LocalMemoryPuzzleGenerator {
    static func generateColorTextMatching(difficulty: String) -> (String, String) {
        let totalQuestions: Int = {
            switch difficulty.lowercased() {
            case "easy": return 10
            case "medium": return 15
            case "hard": return 20
            default: return 15
            }
        }()
        
        let instructions: [String] = [
            "Look at the top card showing a color name (meaning)",
            "Look at the bottom card showing text in a color",
            "Decide if the meaning matches the actual text color",
            "Focus on the COLOR of the text, not what the word says"
        ]
        
        let colorTextData: [String: Any] = [
            "type": "color_text_matching",
            "difficulty": difficulty,
            "totalQuestions": totalQuestions,
            "description": "Match color meanings with actual text colors",
            "instructions": instructions
        ]
        
        guard let questionData = try? JSONSerialization.data(withJSONObject: colorTextData),
              let questionString = String(data: questionData, encoding: .utf8) else {
            print("❌ Failed to serialize color text matching data")
            return ("", "")
        }
        
        return (questionString, "color_text_matching_complete")
    }
}


// MARK: - Color Text Matching Difficulty Manager
class ColorTextMatchingDifficultyManager: ObservableObject {
    
    struct DifficultyLevel {
        let name: String
        let responseTimeLimit: Float
        let colorComplexity: Int
        let conflictFrequency: Float
        let chainLength: Int
        let livesAllowed: Int
        let basePoints: Int
        let visualHints: Bool
        let showProgressIndicator: Bool
        let adaptiveSpeedAdjustment: Bool
        
        static let beginner = DifficultyLevel(name: "Beginner", responseTimeLimit: 8.0, colorComplexity: 4, conflictFrequency: 0.3, chainLength: 8, livesAllowed: 4, basePoints: 10, visualHints: true, showProgressIndicator: true, adaptiveSpeedAdjustment: false)
        static let easy = DifficultyLevel(name: "Easy", responseTimeLimit: 6.0, colorComplexity: 6, conflictFrequency: 0.4, chainLength: 10, livesAllowed: 3, basePoints: 15, visualHints: true, showProgressIndicator: true, adaptiveSpeedAdjustment: true)
        static let medium = DifficultyLevel(name: "Medium", responseTimeLimit: 4.0, colorComplexity: 8, conflictFrequency: 0.5, chainLength: 12, livesAllowed: 3, basePoints: 20, visualHints: false, showProgressIndicator: true, adaptiveSpeedAdjustment: true)
        static let hard = DifficultyLevel(name: "Hard", responseTimeLimit: 3.0, colorComplexity: 10, conflictFrequency: 0.6, chainLength: 15, livesAllowed: 2, basePoints: 25, visualHints: false, showProgressIndicator: false, adaptiveSpeedAdjustment: true)
        static let expert = DifficultyLevel(name: "Expert", responseTimeLimit: 2.5, colorComplexity: 12, conflictFrequency: 0.7, chainLength: 18, livesAllowed: 2, basePoints: 30, visualHints: false, showProgressIndicator: false, adaptiveSpeedAdjustment: true)
        static let master = DifficultyLevel(name: "Master", responseTimeLimit: 2.0, colorComplexity: 15, conflictFrequency: 0.8, chainLength: 20, livesAllowed: 1, basePoints: 40, visualHints: false, showProgressIndicator: false, adaptiveSpeedAdjustment: true)
        
        static let allLevels = [beginner, easy, medium, hard, expert, master]
    }
    
    struct PlayerPerformance: Codable {
        let accuracy: Float
        let averageResponseTime: Float
        let streakLength: Int
        let livesRemaining: Int
        let gameScore: Int
        let difficulty: String
        let colorProcessingComplexity: Float
        let questionsCompleted: Int
    }
    
    struct AdaptationResult {
        let level: DifficultyLevel
        let adjustmentReason: String
        let confidenceScore: Float
        let shouldNotify: Bool
    }
    
    @Published var currentDifficulty: DifficultyLevel = .easy
    private var performanceHistory: [PlayerPerformance] = []
    private let maxHistorySize = 10
    
    init() {
        loadSavedDifficulty()
    }
    
    func recordPerformance(_ performance: PlayerPerformance) -> AdaptationResult {
        performanceHistory.append(performance)
        if performanceHistory.count > maxHistorySize {
            performanceHistory.removeFirst()
        }
        
        savePerformanceHistory()
        
        let result = analyzeAndAdapt()
        if result.level.name != currentDifficulty.name {
            currentDifficulty = result.level
            saveDifficulty()
        }
        
        return result
    }
    
    func getDifficultyProgress() -> (current: Int, total: Int, name: String) {
        let currentIndex = DifficultyLevel.allLevels.firstIndex { $0.name == currentDifficulty.name } ?? 1
        return (current: currentIndex + 1, total: DifficultyLevel.allLevels.count, name: currentDifficulty.name)
    }
    
    private func analyzeAndAdapt() -> AdaptationResult {
        guard performanceHistory.count >= 3 else {
            return AdaptationResult(
                level: currentDifficulty,
                adjustmentReason: "Gathering color processing data...",
                confidenceScore: 0.0,
                shouldNotify: false
            )
        }
        
        let recentWindow = Array(performanceHistory.suffix(5))
        let avgAccuracy = recentWindow.map { $0.accuracy }.reduce(0, +) / Float(recentWindow.count)
        let avgResponseTime = recentWindow.map { $0.averageResponseTime }.reduce(0, +) / Float(recentWindow.count)
        let consistentSuccess = recentWindow.filter { $0.accuracy >= 0.85 }.count >= 3
        let consistentFailure = recentWindow.filter { $0.accuracy <= 0.4 }.count >= 3
        
        let currentIndex = DifficultyLevel.allLevels.firstIndex { $0.name == currentDifficulty.name } ?? 1
        let expectedTime = currentDifficulty.responseTimeLimit
        
        var newLevel = currentDifficulty
        var reason = "Color processing stable at current level"
        var confidence: Float = 0.3
        
        // Check for advancement - require both speed and accuracy for color processing
        if avgAccuracy >= 0.85 && consistentSuccess && avgResponseTime < expectedTime * 0.7 && currentIndex < DifficultyLevel.allLevels.count - 1 {
            newLevel = DifficultyLevel.allLevels[currentIndex + 1]
            reason = "Excellent color processing speed - advancing to \(newLevel.name)"
            confidence = 0.8
        }
        // Check for reduction
        else if avgAccuracy <= 0.3 && consistentFailure && currentIndex > 0 {
            newLevel = DifficultyLevel.allLevels[currentIndex - 1]
            reason = "Building color recognition skills at \(newLevel.name) level"
            confidence = 0.7
        }
        
        return AdaptationResult(
            level: newLevel,
            adjustmentReason: reason,
            confidenceScore: confidence,
            shouldNotify: confidence > 0.5
        )
    }
    
    private func loadSavedDifficulty() {
        if let savedName = UserDefaults.standard.string(forKey: "color_text_matching_difficulty"),
           let level = DifficultyLevel.allLevels.first(where: { $0.name == savedName }) {
            currentDifficulty = level
        }
        loadPerformanceHistory()
    }
    
    private func saveDifficulty() {
        UserDefaults.standard.set(currentDifficulty.name, forKey: "color_text_matching_difficulty")
    }
    
    private func savePerformanceHistory() {
        let encoder = JSONEncoder()
        if let encoded = try? encoder.encode(performanceHistory) {
            UserDefaults.standard.set(encoded, forKey: "color_text_matching_performance_history")
        }
    }
    
    private func loadPerformanceHistory() {
        if let data = UserDefaults.standard.data(forKey: "color_text_matching_performance_history") {
            let decoder = JSONDecoder()
            if let decoded = try? decoder.decode([PlayerPerformance].self, from: data) {
                performanceHistory = decoded
            }
        }
    }
}

// MARK: - Enhanced Data Models
struct AdaptiveColorTextStep {
    let step: Int
    let meaningColorName: String
    let textColorName: String
    let textDisplayColor: Color
    let isMatch: Bool
    let difficulty: Float
    let expectedResponseTime: Float
    let isConflictQuestion: Bool
}

struct AdaptiveColorTextConfig {
    let difficulty: ColorTextMatchingDifficultyManager.DifficultyLevel
    let totalQuestions: Int
    let description: String
    let adaptiveFeatures: [String]
}

struct AdaptiveGameColors {
    static let colorMap: [String: Color] = [
        "red": Color(red: 0.9, green: 0.24, blue: 0.24),
        "blue": Color(red: 0.19, green: 0.51, blue: 0.81),
        "green": Color(red: 0.22, green: 0.63, blue: 0.41),
        "yellow": Color(red: 0.84, green: 0.62, blue: 0.18),
        "purple": Color(red: 0.50, green: 0.35, blue: 0.84),
        "orange": Color(red: 0.87, green: 0.42, blue: 0.13),
        "pink": Color(red: 0.83, green: 0.25, blue: 0.55),
        "brown": Color(red: 0.55, green: 0.27, blue: 0.07),
        "gray": Color(red: 0.45, green: 0.50, blue: 0.59),
        "black": Color(red: 0.18, green: 0.21, blue: 0.28),
        "cyan": Color(red: 0.09, green: 0.57, blue: 0.70),
        "lime": Color(red: 0.40, green: 0.64, blue: 0.05),
        "indigo": Color(red: 0.26, green: 0.22, blue: 0.79),
        "teal": Color(red: 0.06, green: 0.46, blue: 0.43),
        "rose": Color(red: 0.88, green: 0.11, blue: 0.28)
    ]
    
    static let colorNames = Array(colorMap.keys)
    
    static func getColorsForComplexity(_ complexity: Int) -> [String] {
        return Array(colorNames.prefix(min(complexity, colorNames.count)))
    }
}

// MARK: - Main View (Enhanced with adaptive difficulty)
struct ColorTextMatchingPuzzleView: View {
    let difficulty: String
    let timer: String
    let hearts: Int
    let level: String
    let puzzleData: String
    let correctAnswer: String
    
    let onSubmitAnswer: (Bool) -> Void
    let fetchNextPuzzle: (Int) -> Void
    let onBack: () -> Void
    
    // Difficulty management
    @StateObject private var difficultyManager = ColorTextMatchingDifficultyManager()
    @State private var currentConfig: AdaptiveColorTextConfig
    @State private var adaptationResult: ColorTextMatchingDifficultyManager.AdaptationResult?
    @State private var showAdaptationNotification = false
    
    // Game state
    @StateObject private var feedbackManager = UnifiedFeedbackManager()
    @StateObject private var progressionManager = ProgressionManager()
    @State private var gameState: GameState = .instructions
    @State private var currentQuestionIndex = 0
    @State private var currentScore = 0
    @State private var showFeedback = false
    @State private var lastAnswerCorrect = false
    @State private var userAnswers: [Int] = []
    @State private var timeRemaining: Int
    @State private var questionTimeRemaining: Float = 0
    @State private var sequence: [AdaptiveColorTextStep] = []
    @State private var correctAnswers: [Int] = []
    @State private var currentLives: Int
    @State private var currentStreak = 0
    @State private var gamesPlayedThisSession = 0
    
    // Performance tracking
    @State private var questionStartTime = Date()
    @State private var sessionStartTime = Date()
    @State private var responseTimes: [TimeInterval] = []
    @State private var correctCount = 0
    @State private var totalQuestions = 0
    
    // Timer
    @State private var questionTimer: Timer?
    @State private var gameTimer: Timer?
    
    enum GameState {
        case instructions, playing, completed
    }
    
    init(difficulty: String, timer: String, hearts: Int, level: String, puzzleData: String, correctAnswer: String, onSubmitAnswer: @escaping (Bool) -> Void, fetchNextPuzzle: @escaping (Int) -> Void, onBack: @escaping () -> Void) {
        self.difficulty = difficulty
        self.timer = timer
        self.hearts = hearts
        self.level = level
        self.puzzleData = puzzleData
        self.correctAnswer = correctAnswer
        self.onSubmitAnswer = onSubmitAnswer
        self.fetchNextPuzzle = fetchNextPuzzle
        self.onBack = onBack
        
        // Parse timer
        let parts = timer.split(separator: ":")
        let minutes = Int(parts.first ?? "1") ?? 1
        let seconds = Int(parts.last ?? "30") ?? 30
        self._timeRemaining = State(initialValue: minutes * 60 + seconds)
        
        // Initialize with current difficulty
        let initialDifficulty = ColorTextMatchingDifficultyManager.DifficultyLevel.easy
        self._currentConfig = State(initialValue: AdaptiveColorTextConfig(
            difficulty: initialDifficulty,
            totalQuestions: initialDifficulty.chainLength,
            description: "Extended time with visual hints and basic colors",
            adaptiveFeatures: [
                "Time pressure adapts to your skill level",
                "Color complexity increases with performance",
                "Visual hints available for beginners",
                "Progressive difficulty adjustment",
                "Performance tracking for optimal challenge"
            ]
        ))
        self._currentLives = State(initialValue: initialDifficulty.livesAllowed)
    }
    
    var body: some View {
        GeometryReader { geometry in
            ZStack {
                // Wood-like brown background
                Color(red: 0.55, green: 0.35, blue: 0.24)
                    .ignoresSafeArea()
                
                VStack(spacing: 0) {
                    // Enhanced adaptive header
                    adaptiveHeaderView
                    
                    // Adaptation notification
                    if showAdaptationNotification, let result = adaptationResult {
                        adaptationNotificationView(result: result)
                            .transition(.move(edge: .top).combined(with: .opacity))
                    }
                    
                    Spacer(minLength: 32)
                    
                    // Main content
                    Group {
                        switch gameState {
                        case .instructions:
                            adaptiveInstructionsView
                        case .playing:
                            adaptiveGameView
                        case .completed:
                            adaptiveCompletionView
                        }
                    }
                    
                    Spacer()
                }
                .padding(.horizontal, 16)
                .padding(.top, 80)
                .padding(.bottom, 32)
            }
        }
        .onAppear {
            setupAdaptivePuzzle()
        }
        .onDisappear {
            questionTimer?.invalidate()
            gameTimer?.invalidate()
        }
    }
    
    // MARK: - View Components
    
    private var adaptiveHeaderView: some View {
        VStack(spacing: 12) {
            HStack {
                // Back button
                Button(action: onBack) {
                    Image(systemName: "arrow.left")
                        .font(.headline)
                        .fontWeight(.bold)
                        .foregroundColor(.white)
                        .frame(width: 48, height: 48)
                        .background(Color.blue)
                        .cornerRadius(8)
                }
                
                Spacer()
                
                // Level and difficulty info
                VStack(spacing: 4) {
                    Text("Level \(progressionManager.currentLevel.level)")
                        .font(.caption)
                        .fontWeight(.bold)
                        .foregroundColor(.white)
                    
                    Text(currentConfig.difficulty.name)
                        .font(.caption)
                        .fontWeight(.medium)
                        .foregroundColor(.cyan)
                }
                
                Spacer()
                
                // Timer and Score
                HStack(spacing: 16) {
                    Text("TIME \(String(format: "%d:%02d", timeRemaining / 60, timeRemaining % 60))")
                        .font(.caption)
                        .fontWeight(.bold)
                        .foregroundColor(Color(red: 0.12, green: 0.23, blue: 0.54))
                        .padding(.horizontal, 12)
                        .padding(.vertical, 6)
                        .background(Color.white.opacity(0.9))
                        .cornerRadius(8)
                    
                    Text("SCORE \(currentScore)")
                        .font(.caption)
                        .fontWeight(.bold)
                        .foregroundColor(Color(red: 0.12, green: 0.23, blue: 0.54))
                        .padding(.horizontal, 12)
                        .padding(.vertical, 6)
                        .background(Color.white.opacity(0.9))
                        .cornerRadius(8)
                }
            }
            
            // Lives and streak info
            HStack {
                // Lives display
                HStack(spacing: 4) {
                    ForEach(0..<currentConfig.difficulty.livesAllowed, id: \.self) { index in
                        Image(systemName: index < currentLives ? "heart.fill" : "heart")
                            .font(.system(size: 16))
                            .foregroundColor(index < currentLives ? .red : .gray)
                    }
                }
                
                Spacer()
                
                if currentStreak > 0 {
                    Text("🔥 \(currentStreak)")
                        .font(.caption)
                        .fontWeight(.bold)
                        .foregroundColor(.orange)
                }
                
                Spacer()
                
                // Adaptive features indicators
                HStack(spacing: 8) {
                    if currentConfig.difficulty.visualHints {
                        Text("💡")
                            .font(.caption)
                    }
                    if currentConfig.difficulty.adaptiveSpeedAdjustment {
                        Text("⚡")
                            .font(.caption)
                    }
                }
            }
            
            // Question timer progress bar (only during gameplay)
            if gameState == .playing && currentConfig.difficulty.showProgressIndicator {
                let progress = questionTimeRemaining / currentConfig.difficulty.responseTimeLimit
                GeometryReader { geometry in
                    ZStack(alignment: .leading) {
                        Rectangle()
                            .fill(Color.white.opacity(0.3))
                            .frame(height: 4)
                        
                        Rectangle()
                            .fill(progress > 0.5 ? Color.green : progress > 0.25 ? Color.orange : Color.red)
                            .frame(width: geometry.size.width * CGFloat(progress), height: 4)
                    }
                }
                .frame(height: 4)
            }
        }
    }
    
    private func adaptationNotificationView(result: ColorTextMatchingDifficultyManager.AdaptationResult) -> some View {
        HStack {
            VStack(alignment: .leading, spacing: 4) {
                HStack {
                    Image(systemName: "brain.head.profile")
                        .foregroundColor(.white)
                        .font(.caption)
                    
                    Text("Color Challenge Adapted!")
                        .font(.caption)
                        .fontWeight(.semibold)
                        .foregroundColor(.white)
                }
                
                Text(result.adjustmentReason)
                    .font(.caption2)
                    .foregroundColor(.white.opacity(0.9))
                    .lineLimit(2)
            }
            
            Spacer()
            
            Text("→ \(result.level.name)")
                .font(.caption)
                .fontWeight(.bold)
                .foregroundColor(.white)
            
            Button(action: { showAdaptationNotification = false }) {
                Image(systemName: "xmark")
                    .foregroundColor(.white)
                    .font(.caption)
            }
        }
        .padding(12)
        .background(
            LinearGradient(
                colors: [Color(red: 0.31, green: 0.76, blue: 0.97), Color(red: 0.55, green: 0.29, blue: 0.62)],
                startPoint: .leading,
                endPoint: .trailing
            )
        )
        .cornerRadius(12)
    }
    
    private var adaptiveInstructionsView: some View {
        VStack(spacing: 20) {
            ScrollView {
                VStack(spacing: 16) {
                    Text("Adaptive Color-Text Matching")
                        .font(.title)
                        .fontWeight(.bold)
                        .foregroundColor(.white)
                        .multilineTextAlignment(.center)
                    
                    Text(currentConfig.description)
                        .font(.subheadline)
                        .foregroundColor(.white.opacity(0.9))
                        .multilineTextAlignment(.center)
                        .fontWeight(.medium)
                    
                    Text("Compare the color name meaning with the actual text color and decide if they match.")
                        .font(.body)
                        .foregroundColor(.white.opacity(0.9))
                        .multilineTextAlignment(.center)
                    
                    // Enhanced example with adaptive features
                    VStack(spacing: 16) {
                        Text("Adaptive Example")
                            .font(.headline)
                            .fontWeight(.bold)
                            .foregroundColor(.white)
                        
                        VStack(spacing: 20) {
                            // Show timer if progress indicator is enabled
                            if currentConfig.difficulty.showProgressIndicator {
                                VStack(spacing: 4) {
                                    ProgressView(value: 0.7)
                                        .progressViewStyle(LinearProgressViewStyle(tint: Color.green))
                                        .frame(height: 4)
                                        .background(Color.white.opacity(0.3))
                                        .cornerRadius(2)
                                    
                                    Text("⏱️ \(String(format: "%.1f", currentConfig.difficulty.responseTimeLimit))s per question")
                                        .font(.caption2)
                                        .foregroundColor(.gray)
                                }
                            }
                            
                            // Example meaning card
                            VStack(spacing: 8) {
                                Text("meaning")
                                    .font(.caption)
                                    .foregroundColor(.gray)
                                    .padding(.horizontal, 6)
                                    .padding(.vertical, 2)
                                    .background(Color.gray.opacity(0.2))
                                    .cornerRadius(4)
                                
                                RoundedRectangle(cornerRadius: 12)
                                    .fill(Color.white)
                                    .frame(height: 50)
                                    .overlay(
                                        Text("blue")
                                            .font(.title3)
                                            .fontWeight(.bold)
                                            .foregroundColor(.black)
                                    )
                            }
                            
                            // Example text color card with adaptive hints
                            VStack(spacing: 8) {
                                RoundedRectangle(cornerRadius: 12)
                                    .fill(Color.white)
                                    .frame(height: 50)
                                    .overlay(
                                        Text("red")
                                            .font(.title3)
                                            .fontWeight(.bold)
                                            .foregroundColor(.blue)
                                    )
                                
                                HStack(spacing: 4) {
                                    Text("text color")
                                        .font(.caption)
                                        .foregroundColor(.gray)
                                        .padding(.horizontal, 6)
                                        .padding(.vertical, 2)
                                        .background(Color.gray.opacity(0.2))
                                        .cornerRadius(4)
                                    
                                    if currentConfig.difficulty.visualHints {
                                        Text("👀 Focus here!")
                                            .font(.caption2)
                                            .foregroundColor(.orange)
                                    }
                                }
                            }
                            
                            Text("Answer: YES (both are blue)")
                                .font(.subheadline)
                                .fontWeight(.bold)
                                .foregroundColor(.green)
                        }
                        .padding()
                        .background(Color.white.opacity(0.1))
                        .cornerRadius(12)
                    }
                    
                    // Adaptive features explanation
                    VStack(alignment: .leading, spacing: 8) {
                        Text("🎯 Adaptive Features")
                            .font(.subheadline)
                            .fontWeight(.bold)
                            .foregroundColor(.white)
                        
                        ForEach(currentConfig.adaptiveFeatures, id: \.self) { feature in
                            Text("• \(feature)")
                                .font(.caption)
                                .foregroundColor(.white.opacity(0.9))
                        }
                        
                        Text("💡 Focus on the actual color of the text in the bottom card, not what the word says!")
                            .font(.caption)
                            .fontWeight(.medium)
                            .foregroundColor(.yellow)
                            .multilineTextAlignment(.center)
                    }
                    .padding()
                    .background(Color.white.opacity(0.1))
                    .cornerRadius(12)
                }
            }
            
            Button("START ADAPTIVE CHALLENGE") {
                gameState = .playing
                startGame()
            }
            .font(.headline)
            .fontWeight(.bold)
            .foregroundColor(.white)
            .frame(maxWidth: .infinity)
            .frame(height: 56)
            .background(Color.green)
            .cornerRadius(12)
        }
    }
    
    private var adaptiveGameView: some View {
        VStack(spacing: 32) {
            // Enhanced progress indicator
            HStack(spacing: 12) {
                Circle()
                    .fill(Color.green)
                    .frame(width: 48, height: 48)
                    .overlay(
                        Text("\(currentQuestionIndex + 1)")
                            .font(.headline)
                            .fontWeight(.bold)
                            .foregroundColor(.white)
                    )
                
                if currentConfig.difficulty.showProgressIndicator {
                    VStack {
                        Text("\(currentQuestionIndex + 1) / \(currentConfig.totalQuestions)")
                            .font(.caption)
                            .foregroundColor(.white.opacity(0.8))
                        ProgressView(value: Double(currentQuestionIndex + 1), total: Double(currentConfig.totalQuestions))
                            .progressViewStyle(LinearProgressViewStyle(tint: Color.green))
                            .frame(width: 100)
                    }
                }
                
                // Conflict question indicator
                if currentQuestionIndex < sequence.count && sequence[currentQuestionIndex].isConflictQuestion && currentConfig.difficulty.visualHints {
                    Text("⚠️")
                        .font(.title2)
                }
            }
            
            // Enhanced question with difficulty context
            VStack(spacing: 8) {
                Text("Does the meaning match the text color?")
                    .font(.title3)
                    .fontWeight(.medium)
                    .foregroundColor(.white)
                    .multilineTextAlignment(.center)
                
                if currentQuestionIndex < sequence.count && currentConfig.difficulty.visualHints && sequence[currentQuestionIndex].difficulty > 0.7 {
                    Text("🔥 Challenge Question")
                        .font(.caption)
                        .foregroundColor(.orange)
                }
            }
            
            if currentQuestionIndex < sequence.count {
                let currentStep = sequence[currentQuestionIndex]
                
                // Meaning card (top) with enhanced animations
                VStack(spacing: 8) {
                    Text("meaning")
                        .font(.caption)
                        .foregroundColor(.white.opacity(0.8))
                        .padding(.horizontal, 8)
                        .padding(.vertical, 4)
                        .background(Color.white.opacity(0.2))
                        .cornerRadius(4)
                    
                    RoundedRectangle(cornerRadius: 12)
                        .fill(Color.white)
                        .frame(height: 80)
                        .overlay(
                            Text(currentStep.meaningColorName)
                                .font(.title)
                                .fontWeight(.bold)
                                .foregroundColor(.black)
                        )
                        .scaleEffect(showFeedback ? 0.95 : 1.0)
                        .animation(.easeInOut(duration: 0.2), value: showFeedback)
                }
                .padding(.horizontal, 32)
                
                // Text color card (bottom) with adaptive hints
                VStack(spacing: 8) {
                    RoundedRectangle(cornerRadius: 12)
                        .fill(Color.white)
                        .frame(height: 80)
                        .overlay(
                            Text(currentStep.textColorName)
                                .font(.title)
                                .fontWeight(.bold)
                                .foregroundColor(currentStep.textDisplayColor)
                        )
                        .scaleEffect(showFeedback ? 0.95 : 1.0)
                        .animation(.easeInOut(duration: 0.2), value: showFeedback)
                    
                    HStack(spacing: 4) {
                        Text("text color")
                            .font(.caption)
                            .foregroundColor(.white.opacity(0.8))
                            .padding(.horizontal, 8)
                            .padding(.vertical, 4)
                            .background(Color.white.opacity(0.2))
                            .cornerRadius(4)
                        
                        if currentConfig.difficulty.visualHints && !currentStep.isMatch {
                            Text("👀")
                                .font(.caption)
                        }
                    }
                }
                .padding(.horizontal, 32)
            }
            
            // Enhanced feedback
            if showFeedback {
                Circle()
                    .fill(lastAnswerCorrect ? Color.green : Color.red)
                    .frame(width: 120, height: 120)
                    .overlay(
                        Text(lastAnswerCorrect ? "✓" : "✗")
                            .font(.system(size: 60))
                            .fontWeight(.bold)
                            .foregroundColor(.white)
                    )
                    .scaleEffect(showFeedback ? 1.0 : 0.1)
                    .animation(.spring(response: 0.5, dampingFraction: 0.6), value: showFeedback)
            } else {
                // Enhanced answer buttons with adaptive hints
                HStack(spacing: 16) {
                    Button("NO") {
                        handleAnswer(false)
                    }
                    .font(.title2)
                    .fontWeight(.bold)
                    .foregroundColor(.white)
                    .frame(maxWidth: .infinity)
                    .frame(height: 64)
                    .background(
                        (currentConfig.difficulty.visualHints && currentQuestionIndex < sequence.count && !sequence[currentQuestionIndex].isMatch) ?
                        Color.green : Color.blue
                    )
                    .cornerRadius(12)
                    
                    Button("YES") {
                        handleAnswer(true)
                    }
                    .font(.title2)
                    .fontWeight(.bold)
                    .foregroundColor(.white)
                    .frame(maxWidth: .infinity)
                    .frame(height: 64)
                    .background(
                        (currentConfig.difficulty.visualHints && currentQuestionIndex < sequence.count && sequence[currentQuestionIndex].isMatch) ?
                        Color.green : Color.blue
                    )
                    .cornerRadius(12)
                }
                .padding(.horizontal, 20)
            }
        }
    }
    
    private var adaptiveCompletionView: some View {
        VStack(spacing: 32) {
            Text("Adaptive Challenge Complete!")
                .font(.largeTitle)
                .fontWeight(.bold)
                .foregroundColor(.white)
                .multilineTextAlignment(.center)
            
            VStack(spacing: 16) {
                Text("Final Score")
                    .font(.headline)
                    .fontWeight(.bold)
                    .foregroundColor(.white)
                
                Text("\(currentScore) / \(currentConfig.totalQuestions * currentConfig.difficulty.basePoints)")
                    .font(.title)
                    .fontWeight(.bold)
                    .foregroundColor(.green)
                
                HStack(spacing: 30) {
                    VStack {
                        Text("Accuracy")
                            .font(.caption)
                            .foregroundColor(.gray)
                        Text("\(totalQuestions > 0 ? (correctCount * 100) / totalQuestions : 0)%")
                            .font(.subheadline)
                            .fontWeight(.bold)
                            .foregroundColor(.white)
                    }
                    
                    VStack {
                        Text("Avg Time")
                            .font(.caption)
                            .foregroundColor(.gray)
                        Text("\(String(format: "%.1f", responseTimes.isEmpty ? 0 : responseTimes.reduce(0, +) / Double(responseTimes.count)))s")
                            .font(.subheadline)
                            .fontWeight(.bold)
                            .foregroundColor(.white)
                    }
                    
                    VStack {
                        Text("Streak")
                            .font(.caption)
                            .foregroundColor(.gray)
                        Text("\(currentStreak)")
                            .font(.subheadline)
                            .fontWeight(.bold)
                            .foregroundColor(.orange)
                    }
                }
                
                VStack(spacing: 4) {
                    Text("Mode: \(currentConfig.difficulty.name)")
                        .font(.subheadline)
                        .foregroundColor(.cyan)
                        .fontWeight(.medium)
                    
                    Text("Colors Used: \(currentConfig.difficulty.colorComplexity)")
                        .font(.caption)
                        .foregroundColor(.gray)
                }
            }
            .padding(24)
            .background(Color.white.opacity(0.1))
            .cornerRadius(12)
            
            Button("CONTINUE") {
                let finalScore = currentScore
                let maxScore = currentConfig.totalQuestions * currentConfig.difficulty.basePoints
                let isSuccess = finalScore >= (maxScore * 60) / 100 // 60% threshold
                onSubmitAnswer(isSuccess)
                fetchNextPuzzle(finalScore)
            }
            .font(.headline)
            .fontWeight(.bold)
            .foregroundColor(.white)
            .frame(maxWidth: .infinity)
            .frame(height: 56)
            .background(Color.green)
            .cornerRadius(12)
        }
    }
    
    // MARK: - Game Logic
    
    private func setupAdaptivePuzzle() {
        let currentDifficulty = difficultyManager.currentDifficulty
        currentConfig = AdaptiveColorTextConfig(
            difficulty: currentDifficulty,
            totalQuestions: currentDifficulty.chainLength,
            description: generateDescription(for: currentDifficulty),
            adaptiveFeatures: [
                "Time pressure adapts to your skill level",
                "Color complexity increases with performance",
                "Visual hints available for beginners",
                "Progressive difficulty adjustment",
                "Performance tracking for optimal challenge"
            ]
        )
        currentLives = currentDifficulty.livesAllowed
        sessionStartTime = Date()
        
        generateAdaptivePuzzleData()
        
        print("🎨 ADAPTIVE: Set up color-text matching with difficulty: \(currentDifficulty.name)")
        print("🎨 ADAPTIVE: Questions: \(currentConfig.totalQuestions), Color complexity: \(currentDifficulty.colorComplexity)")
    }
    
    private func generateDescription(for difficulty: ColorTextMatchingDifficultyManager.DifficultyLevel) -> String {
        switch difficulty.name {
        case "Beginner":
            return "Extended time with visual hints and basic colors"
        case "Easy":
            return "Moderate time with hints and adaptive pacing"
        case "Medium":
            return "Standard timing with increased complexity"
        case "Hard":
            return "Fast pace with maximum color variety"
        case "Expert":
            return "Elite challenge with extreme time pressure"
        case "Master":
            return "Ultimate test of color processing speed"
        default:
            return "Adaptive color processing challenge"
        }
    }
    
    private func startGame() {
        startGameTimer()
        startQuestionTimer()
    }
    
    private func startGameTimer() {
        gameTimer = Timer.scheduledTimer(withTimeInterval: 1.0, repeats: true) { _ in
            if gameState == .playing && timeRemaining > 0 {
                timeRemaining -= 1
            } else if timeRemaining <= 0 && gameState == .playing {
                gameState = .completed
                questionTimer?.invalidate()
            }
        }
    }
    
    private func startQuestionTimer() {
        questionStartTime = Date()
        questionTimeRemaining = currentConfig.difficulty.responseTimeLimit
        
        questionTimer?.invalidate()
        questionTimer = Timer.scheduledTimer(withTimeInterval: 0.1, repeats: true) { _ in
            if gameState == .playing && !showFeedback {
                questionTimeRemaining -= 0.1
                
                if questionTimeRemaining <= 0 {
                    // Auto-fail on timeout
                    handleTimeout()
                }
            }
        }
    }
    
    private func handleTimeout() {
        let responseTime = Date().timeIntervalSince(questionStartTime)
        responseTimes.append(responseTime)
        
        userAnswers.append(-1) // -1 indicates timeout
        lastAnswerCorrect = false
        currentLives = max(0, currentLives - 1)
        currentStreak = 0
        totalQuestions += 1
        
        if currentQuestionIndex < sequence.count {
            let currentStep = sequence[currentQuestionIndex]
            recordPerformanceAndAdapt(isCorrect: false, responseTime: responseTime, questionDifficulty: currentStep.difficulty)
        }
        
        showFeedback = true
        hapticFeedback(isCorrect: false)
        
        DispatchQueue.main.asyncAfter(deadline: .now() + 1.5) {
            advanceToNextQuestion()
        }
    }
    
    private func generateAdaptivePuzzleData() {
        sequence = []
        correctAnswers = []
        
        let availableColors = AdaptiveGameColors.getColorsForComplexity(currentConfig.difficulty.colorComplexity)
        
        for i in 0..<currentConfig.totalQuestions {
            let meaningColorName = availableColors.randomElement()!
            let textColorName = availableColors.randomElement()!
            
            // Determine if this should be a conflict question
            let isConflictQuestion = Float.random(in: 0...1) < currentConfig.difficulty.conflictFrequency
            
            // Decide if this should be a match or not
            let shouldMatch = if isConflictQuestion {
                Float.random(in: 0...1) < 0.3 // Lower chance of match in conflict questions
            } else {
                Float.random(in: 0...1) < 0.6 // Higher chance of match in normal questions
            }
            
            let actualTextDisplayColor: Color
            if shouldMatch {
                actualTextDisplayColor = AdaptiveGameColors.colorMap[meaningColorName] ?? .black
            } else {
                let differentColor = availableColors.filter { $0 != meaningColorName }.randomElement() ?? "red"
                actualTextDisplayColor = AdaptiveGameColors.colorMap[differentColor] ?? .black
            }
            // Calculate question difficulty
            let baselineDifficulty: Float = if isConflictQuestion {
                0.8
            } else if shouldMatch {
                0.4
            } else {
                0.6
            }
            
            let colorSimilarityPenalty: Float = meaningColorName.count == textColorName.count ? 0.2 : 0
            let questionDifficulty = min(1.0, baselineDifficulty + colorSimilarityPenalty)
            
            // Expected response time based on difficulty
            let expectedTime = currentConfig.difficulty.responseTimeLimit * (0.5 + questionDifficulty * 0.5)
            
            let step = AdaptiveColorTextStep(
                step: i + 1,
                meaningColorName: meaningColorName,
                textColorName: textColorName,
                textDisplayColor: actualTextDisplayColor,
                isMatch: shouldMatch,
                difficulty: questionDifficulty,
                expectedResponseTime: expectedTime,
                isConflictQuestion: isConflictQuestion
            )
            
            sequence.append(step)
            correctAnswers.append(shouldMatch ? 1 : 0)
        }
    }
    
    private func handleAnswer(_ isMatch: Bool) {
        guard !showFeedback && currentQuestionIndex < sequence.count else { return }
        
        questionTimer?.invalidate()
        
        let responseTime = Date().timeIntervalSince(questionStartTime)
        responseTimes.append(responseTime)
        
        let userAnswer = isMatch ? 1 : 0
        let correctAnswer = correctAnswers[currentQuestionIndex]
        let isCorrect = userAnswer == correctAnswer
        let currentStep = sequence[currentQuestionIndex]
        
        userAnswers.append(userAnswer)
        lastAnswerCorrect = isCorrect
        totalQuestions += 1
        
        if isCorrect {
            // Calculate adaptive score
            let timeBonus = calculateTimeBonus(responseTime: responseTime, expectedTime: TimeInterval(currentStep.expectedResponseTime))
            let difficultyBonus = Int(currentStep.difficulty * 20)
            let questionScore = currentConfig.difficulty.basePoints + timeBonus + difficultyBonus
            currentScore += questionScore
            currentStreak += 1
            correctCount += 1
        } else {
            currentLives = max(0, currentLives - 1)
            currentStreak = 0
        }
        
        recordPerformanceAndAdapt(isCorrect: isCorrect, responseTime: responseTime, questionDifficulty: currentStep.difficulty)
        
        showFeedback = true
        hapticFeedback(isCorrect: isCorrect)
        
        DispatchQueue.main.asyncAfter(deadline: .now() + 1.5) {
            advanceToNextQuestion()
        }
    }
    
    private func advanceToNextQuestion() {
        showFeedback = false
        
        if currentLives <= 0 {
            gameState = .completed
            questionTimer?.invalidate()
            gameTimer?.invalidate()
        } else if currentQuestionIndex < currentConfig.totalQuestions - 1 {
            currentQuestionIndex += 1
            gamesPlayedThisSession += 1
            startQuestionTimer()
        } else {
            gameState = .completed
            questionTimer?.invalidate()
            gameTimer?.invalidate()
        }
    }
    
    private func recordPerformanceAndAdapt(isCorrect: Bool, responseTime: TimeInterval, questionDifficulty: Float) {
        let colorComplexity = Float(currentConfig.difficulty.colorComplexity) + questionDifficulty
        
        let performance = ColorTextMatchingDifficultyManager.PlayerPerformance(
            accuracy: isCorrect ? 1.0 : 0.0,
            averageResponseTime: Float(responseTime),
            streakLength: currentStreak,
            livesRemaining: currentLives,
            gameScore: currentScore,
            difficulty: currentConfig.difficulty.name,
            colorProcessingComplexity: colorComplexity,
            questionsCompleted: currentQuestionIndex + 1
        )
        
        let result = difficultyManager.recordPerformance(performance)
        adaptationResult = result
        
        if result.shouldNotify {
            showAdaptationNotification = true
            
            DispatchQueue.main.asyncAfter(deadline: .now() + 4) {
                showAdaptationNotification = false
            }
        }
    }
    
    private func calculateTimeBonus(responseTime: TimeInterval, expectedTime: TimeInterval) -> Int {
        let ratio = responseTime / expectedTime
        
        switch ratio {
        case ...0.5:
            return 20 // Very fast
        case ...0.75:
            return 15 // Fast
        case ...1.0:
            return 10 // On time
        case ...1.5:
            return 5 // Slow
        default:
            return 0 // Very slow
        }
    }
    
    private func hapticFeedback(isCorrect: Bool) {
        let impactFeedback = UIImpactFeedbackGenerator(style: isCorrect ? .light : .heavy)
        impactFeedback.impactOccurred()
        
        if isCorrect {
            AudioServicesPlaySystemSound(1054) // Success sound
        } else {
            AudioServicesPlaySystemSound(1053) // Error sound
        }
    }
}
