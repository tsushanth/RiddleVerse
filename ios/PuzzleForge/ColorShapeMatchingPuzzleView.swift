//
//  ColorShapeMatchingPuzzleView.swift
//  PuzzleForge
//
//  Enhanced with adaptive difficulty progression
//

import SwiftUI
import Foundation
import AVFoundation

extension LocalMemoryPuzzleGenerator {
    
    // MARK: - Color Shape Matching Generator
    static func generateColorShapeMatching(difficulty: String) -> (String, String) {
        let config = getColorShapeConfig(for: difficulty)
        
        let questionData: [String: Any] = [
            "puzzleType": "colorShapeMatching",
            "difficulty": difficulty,
            "totalChallenges": config.totalChallenges,
            "timeLimit": config.timeLimit,
            "instructions": [
                "title": "Color Shape Matching",
                "description": "Compare the text color with the shape color",
                "steps": [
                    "Look at the color word displayed",
                    "Look at the shape and its color",
                    "Decide if the text color matches the shape color",
                    "Tap YES if they match, NO if they don't"
                ],
                "tip": "Pay attention to the color of the text, not just the word itself!"
            ]
        ]
        
        let answerData: [String: Any] = [
            "puzzleType": "colorShapeMatching",
            "scorePerCorrect": 10,
            "maxScore": config.totalChallenges * 10
        ]
        
        guard let questionJSON = try? JSONSerialization.data(withJSONObject: questionData),
              let answerJSON = try? JSONSerialization.data(withJSONObject: answerData),
              let questionString = String(data: questionJSON, encoding: .utf8),
              let answerString = String(data: answerJSON, encoding: .utf8) else {
            return ("", "")
        }
        
        print("ðŸŽ¨ Generated Color Shape Matching:")
        print("  - Difficulty: \(difficulty)")
        print("  - Total challenges: \(config.totalChallenges)")
        print("  - Time limit: \(config.timeLimit) seconds")
        
        return (questionString, answerString)
    }
    
    // MARK: - Image Vortex Generator
    static func generateImageVortex(difficulty: String) -> (String, String) {
        let config = getImageVortexConfig(for: difficulty)
        
        let questionData: [String: Any] = [
            "puzzleType": "imageVortex",
            "difficulty": difficulty,
            "targetLevel": config.targetLevel,
            "timeLimit": config.timeLimit,
            "instructions": [
                "title": "Image Vortex",
                "description": "Find the newest image that appears on screen",
                "steps": [
                    "Watch as new images appear on the screen",
                    "Remember which images you've seen before",
                    "Tap the image that just appeared (the NEW one)",
                    "Complete all levels to finish"
                ],
                "tip": "Focus on identifying which image is the most recent addition!"
            ],
            "availableEmojis": [
                "ðŸ¶", "ðŸ±", "ðŸ­", "ðŸ¹", "ðŸ°", "ðŸ¦Š", "ðŸ»", "ðŸ¼", "ðŸ¨", "ðŸ¯",
                "ðŸ¦", "ðŸ®", "ðŸ·", "ðŸ¸", "ðŸµ", "ðŸ™ˆ", "ðŸ™‰", "ðŸ™Š", "ðŸ’", "ðŸ”",
                "ðŸŽ", "ðŸŠ", "ðŸ‹", "ðŸŒ", "ðŸ‰", "ðŸ‡", "ðŸ“", "ðŸˆ", "ðŸ’", "ðŸ‘",
                "âš½", "ðŸ€", "ðŸˆ", "âš¾", "ðŸŽ¾", "ðŸ", "ðŸ‰", "ðŸŽ±", "ðŸ“", "ðŸ¸"
            ]
        ]
        
        let answerData: [String: Any] = [
            "puzzleType": "imageVortex",
            "scorePerCorrect": 10,
            "maxScore": config.targetLevel * 10,
            "targetLevel": config.targetLevel
        ]
        
        guard let questionJSON = try? JSONSerialization.data(withJSONObject: questionData),
              let answerJSON = try? JSONSerialization.data(withJSONObject: answerData),
              let questionString = String(data: questionJSON, encoding: .utf8),
              let answerString = String(data: answerJSON, encoding: .utf8) else {
            return ("", "")
        }
        
        print("ðŸŒªï¸ Generated Image Vortex:")
        print("  - Difficulty: \(difficulty)")
        print("  - Target level: \(config.targetLevel)")
        print("  - Time limit: \(config.timeLimit) seconds")
        
        return (questionString, answerString)
    }
    
    // MARK: - Configuration Helpers
    
    private static func getColorShapeConfig(for difficulty: String) -> ColorShapeConfig {
        switch difficulty.lowercased() {
        case "easy":
            return ColorShapeConfig(totalChallenges: 8, timeLimit: 180) // 3 minutes
        case "medium":
            return ColorShapeConfig(totalChallenges: 10, timeLimit: 150) // 2.5 minutes
        case "hard":
            return ColorShapeConfig(totalChallenges: 12, timeLimit: 120) // 2 minutes
        default:
            return ColorShapeConfig(totalChallenges: 10, timeLimit: 150)
        }
    }
    
    private static func getImageVortexConfig(for difficulty: String) -> ImageVortexConfig {
        switch difficulty.lowercased() {
        case "easy":
            return ImageVortexConfig(targetLevel: 8, timeLimit: 180) // 3 minutes
        case "medium":
            return ImageVortexConfig(targetLevel: 10, timeLimit: 150) // 2.5 minutes
        case "hard":
            return ImageVortexConfig(targetLevel: 12, timeLimit: 120) // 2 minutes
        default:
            return ImageVortexConfig(targetLevel: 10, timeLimit: 150)
        }
    }
    
    // MARK: - Configuration Structs
    
    private struct ColorShapeConfig {
        let totalChallenges: Int
        let timeLimit: Int
    }
    
    private struct ImageVortexConfig {
        let targetLevel: Int
        let timeLimit: Int
    }
}

// MARK: - Color Shape Matching Difficulty Manager
class ColorShapeMatchingDifficultyManager: ObservableObject {
    
    struct DifficultyLevel {
        let name: String
        let totalChallenges: Int
        let timeLimit: Int
        let livesAllowed: Int
        let basePoints: Int
        let complexityLevel: Int
        let colorDistractorChance: Float
        
        static let beginner = DifficultyLevel(name: "Beginner", totalChallenges: 8, timeLimit: 180, livesAllowed: 4, basePoints: 10, complexityLevel: 1, colorDistractorChance: 0.3)
        static let easy = DifficultyLevel(name: "Easy", totalChallenges: 10, timeLimit: 150, livesAllowed: 3, basePoints: 15, complexityLevel: 2, colorDistractorChance: 0.4)
        static let medium = DifficultyLevel(name: "Medium", totalChallenges: 12, timeLimit: 120, livesAllowed: 3, basePoints: 20, complexityLevel: 3, colorDistractorChance: 0.5)
        static let hard = DifficultyLevel(name: "Hard", totalChallenges: 15, timeLimit: 90, livesAllowed: 2, basePoints: 25, complexityLevel: 4, colorDistractorChance: 0.6)
        static let expert = DifficultyLevel(name: "Expert", totalChallenges: 18, timeLimit: 75, livesAllowed: 2, basePoints: 30, complexityLevel: 5, colorDistractorChance: 0.7)
        static let master = DifficultyLevel(name: "Master", totalChallenges: 20, timeLimit: 60, livesAllowed: 1, basePoints: 40, complexityLevel: 6, colorDistractorChance: 0.8)
        
        static let allLevels = [beginner, easy, medium, hard, expert, master]
    }
    
    struct PlayerPerformance: Codable {
        let accuracy: Float
        let timeSpent: Float
        let streakLength: Int
        let livesRemaining: Int
        let gameScore: Int
        let difficulty: String
        let visualComplexity: Float
        let challengesCompleted: Int
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
                adjustmentReason: "Gathering visual processing data...",
                confidenceScore: 0.0,
                shouldNotify: false
            )
        }
        
        let recentWindow = Array(performanceHistory.suffix(5))
        let avgAccuracy = recentWindow.map { $0.accuracy }.reduce(0, +) / Float(recentWindow.count)
        let avgTime = recentWindow.map { $0.timeSpent }.reduce(0, +) / Float(recentWindow.count)
        let consistentSuccess = recentWindow.filter { $0.accuracy >= 0.85 }.count >= 3
        let consistentFailure = recentWindow.filter { $0.accuracy <= 0.4 }.count >= 3
        
        let currentIndex = DifficultyLevel.allLevels.firstIndex { $0.name == currentDifficulty.name } ?? 1
        let expectedTime = Float(currentDifficulty.timeLimit) / Float(currentDifficulty.totalChallenges)
        
        var newLevel = currentDifficulty
        var reason = "Visual processing stable at current level"
        var confidence: Float = 0.3
        
        // Check for advancement - require both speed and accuracy
        if avgAccuracy >= 0.85 && consistentSuccess && avgTime < expectedTime * 0.6 && currentIndex < DifficultyLevel.allLevels.count - 1 {
            newLevel = DifficultyLevel.allLevels[currentIndex + 1]
            reason = "Excellent visual processing - advancing to \(newLevel.name)"
            confidence = 0.8
        }
        // Check for reduction
        else if avgAccuracy <= 0.3 && consistentFailure && currentIndex > 0 {
            newLevel = DifficultyLevel.allLevels[currentIndex - 1]
            reason = "Building visual confidence at \(newLevel.name) level"
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
        if let savedName = UserDefaults.standard.string(forKey: "color_shape_matching_difficulty"),
           let level = DifficultyLevel.allLevels.first(where: { $0.name == savedName }) {
            currentDifficulty = level
        }
        loadPerformanceHistory()
    }
    
    private func saveDifficulty() {
        UserDefaults.standard.set(currentDifficulty.name, forKey: "color_shape_matching_difficulty")
    }
    
    private func savePerformanceHistory() {
        let encoder = JSONEncoder()
        if let encoded = try? encoder.encode(performanceHistory) {
            UserDefaults.standard.set(encoded, forKey: "color_shape_matching_performance_history")
        }
    }
    
    private func loadPerformanceHistory() {
        if let data = UserDefaults.standard.data(forKey: "color_shape_matching_performance_history") {
            let decoder = JSONDecoder()
            if let decoded = try? decoder.decode([PlayerPerformance].self, from: data) {
                performanceHistory = decoded
            }
        }
    }
}

// MARK: - Enhanced Data Models
struct AdaptiveColorShapeChallenge {
    let shapeColor: Color
    let shapeType: ShapeType
    let colorNameText: String?
    let colorNameFontColor: Color
    let isCorrectMatch: Bool
    let complexityLevel: Int
}

struct EnhancedColorShapeMatchingPuzzleData {
    let difficulty: ColorShapeMatchingDifficultyManager.DifficultyLevel
    let totalChallenges: Int
    let timeLimit: Int
    let complexityLevel: Int
    
    init(difficultyLevel: ColorShapeMatchingDifficultyManager.DifficultyLevel) {
        self.difficulty = difficultyLevel
        self.totalChallenges = difficultyLevel.totalChallenges
        self.timeLimit = difficultyLevel.timeLimit
        self.complexityLevel = difficultyLevel.complexityLevel
    }
}

// MARK: - Main View (Enhanced with adaptive difficulty)
struct ColorShapeMatchingPuzzleView: View {
    let puzzle: Puzzle
    let questionIndex: Int
    let totalQuestions: Int
    let onAnswerSubmitted: (Bool, Bool) -> Void
    let onNextPuzzle: () -> Void
    let onExit: () -> Void
    
    // Difficulty management
    @StateObject private var difficultyManager = ColorShapeMatchingDifficultyManager()
    @State private var currentPuzzleData: EnhancedColorShapeMatchingPuzzleData
    @State private var adaptationResult: ColorShapeMatchingDifficultyManager.AdaptationResult?
    @State private var showAdaptationNotification = false
    
    // Game state
    @StateObject private var feedbackManager = UnifiedFeedbackManager()
    @StateObject private var progressionManager = ProgressionManager()
    @State private var currentChallenge: AdaptiveColorShapeChallenge
    @State private var challengeNumber = 1
    @State private var selectedAnswer: Bool? = nil
    @State private var showFeedback = false
    @State private var isCorrect = false
    @State private var score = 0
    @State private var timeLeft: Int
    @State private var currentLives: Int
    @State private var gameCompleted = false
    @State private var timer: Timer?
    @State private var currentStreak = 0
    @State private var gamesPlayedThisSession = 0
    
    // Performance tracking
    @State private var challengeStartTime = Date()
    @State private var sessionStartTime = Date()
    @State private var correctAnswers = 0
    @State private var totalAnswers = 0
    
    init(puzzle: Puzzle, questionIndex: Int, totalQuestions: Int, onAnswerSubmitted: @escaping (Bool, Bool) -> Void, onNextPuzzle: @escaping () -> Void, onExit: @escaping () -> Void) {
        self.puzzle = puzzle
        self.questionIndex = questionIndex
        self.totalQuestions = totalQuestions
        self.onAnswerSubmitted = onAnswerSubmitted
        self.onNextPuzzle = onNextPuzzle
        self.onExit = onExit
        
        // Initialize with current difficulty
        let initialDifficulty = ColorShapeMatchingDifficultyManager.DifficultyLevel.easy
        self._currentPuzzleData = State(initialValue: EnhancedColorShapeMatchingPuzzleData(difficultyLevel: initialDifficulty))
        self._currentLives = State(initialValue: initialDifficulty.livesAllowed)
        self._timeLeft = State(initialValue: initialDifficulty.timeLimit)
        self._currentChallenge = State(initialValue: Self.generateAdaptiveChallenge(config: initialDifficulty))
    }
    
    var body: some View {
        GeometryReader { geometry in
            ZStack {
                Color(red: 0.96, green: 0.96, blue: 0.96)
                    .ignoresSafeArea()
                
                VStack(spacing: 16) {
                    // Enhanced adaptive header
                    adaptiveHeaderView
                    
                    // Adaptation notification
                    if showAdaptationNotification, let result = adaptationResult {
                        adaptationNotificationView(result: result)
                            .transition(.move(edge: .top).combined(with: .opacity))
                    }
                    
                    Spacer(minLength: 20)
                    
                    // Enhanced progress indicator
                    adaptiveProgressView
                    
                    Spacer(minLength: 32)
                    
                    // Enhanced instructions with difficulty context
                    adaptiveInstructionsView
                    
                    Spacer(minLength: 32)
                    
                    // Color name text
                    if let colorText = currentChallenge.colorNameText {
                        Text(colorText)
                            .font(.system(size: 32, weight: .bold))
                            .foregroundColor(currentChallenge.colorNameFontColor)
                            .multilineTextAlignment(.center)
                        
                        Spacer(minLength: 24)
                    }
                    
                    // Enhanced shape display
                    adaptiveShapeDisplayView
                    
                    Spacer()
                    
                    // Answer buttons or feedback
                    if !showFeedback {
                        adaptiveAnswerButtonsView
                    } else {
                        adaptiveFeedbackView
                    }
                    
                    Spacer(minLength: 16)
                }
                .padding(16)
            }
        }
        .navigationBarHidden(true)
        .onAppear {
            setupAdaptivePuzzle()
        }
        .onDisappear {
            timer?.invalidate()
        }
        .onChange(of: showFeedback) { feedback in
            if feedback {
                DispatchQueue.main.asyncAfter(deadline: .now() + 1.5) {
                    handleFeedbackComplete()
                }
            }
        }
    }
    
    // MARK: - View Components
    
    private var adaptiveHeaderView: some View {
        VStack(spacing: 12) {
            // Top row: Back button, Level, Hearts
            HStack {
                Button(action: onExit) {
                    Image(systemName: "arrow.left")
                        .font(.system(size: 20, weight: .medium))
                        .foregroundColor(Color(red: 0.2, green: 0.2, blue: 0.2))
                        .frame(width: 40, height: 40)
                        .background(Color(red: 0.94, green: 0.94, blue: 0.94))
                        .clipShape(Circle())
                }
                
                Spacer()
                
                VStack(spacing: 4) {
                    Text("Level \(progressionManager.currentLevel.level)")
                        .font(.system(size: 16, weight: .bold))
                        .foregroundColor(Color(red: 0.2, green: 0.2, blue: 0.2))
                    
                    Text("Challenge \(challengeNumber)/\(currentPuzzleData.totalChallenges)")
                        .font(.system(size: 12))
                        .foregroundColor(Color(red: 0.6, green: 0.6, blue: 0.6))
                }
                
                Spacer()
                
                HStack(spacing: 4) {
                    ForEach(0..<currentPuzzleData.difficulty.livesAllowed, id: \.self) { index in
                        Image(systemName: index < currentLives ? "heart.fill" : "heart")
                            .font(.system(size: 20))
                            .foregroundColor(index < currentLives ? Color(red: 0.9, green: 0.24, blue: 0.24) : Color(red: 0.8, green: 0.8, blue: 0.8))
                    }
                }
            }
            
            // Bottom row: Difficulty, Timer, and Score
            HStack {
                AdaptiveDifficultyBadgeView(difficulty: currentPuzzleData.difficulty.name)
                
                Spacer()
                
                if currentStreak > 0 {
                    Text("🔥 \(currentStreak)")
                        .font(.system(size: 12, weight: .bold))
                        .foregroundColor(.orange)
                }
                
                Spacer()
                
                Text("Score: \(score)")
                    .font(.system(size: 12, weight: .bold))
                    .foregroundColor(Color(red: 0.22, green: 0.63, blue: 0.41))
                
                Spacer()
                
                AdaptiveTimerDisplayView(timer: formatTime(timeLeft))
            }
        }
        .padding(16)
        .background(Color.white)
        .cornerRadius(16)
        .shadow(color: .black.opacity(0.1), radius: 4, x: 0, y: 2)
    }
    
    private func adaptationNotificationView(result: ColorShapeMatchingDifficultyManager.AdaptationResult) -> some View {
        HStack {
            VStack(alignment: .leading, spacing: 4) {
                HStack {
                    Image(systemName: "brain.head.profile")
                        .foregroundColor(.white)
                        .font(.caption)
                    
                    Text("Visual Challenge Adapted!")
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
    
    private var adaptiveProgressView: some View {
        VStack(spacing: 8) {
            HStack {
                Text("Progress")
                    .font(.system(size: 14, weight: .medium))
                    .foregroundColor(Color(red: 0.4, green: 0.4, blue: 0.4))
                
                Spacer()
                
                Text("Difficulty: \(currentPuzzleData.difficulty.name)")
                    .font(.system(size: 12, weight: .medium))
                    .foregroundColor(Color(red: 0.31, green: 0.76, blue: 0.97))
            }
            
            ProgressView(value: Double(challengeNumber), total: Double(currentPuzzleData.totalChallenges))
                .progressViewStyle(LinearProgressViewStyle(tint: Color(red: 0.3, green: 0.69, blue: 0.31)))
                .frame(height: 4)
                .background(Color(red: 0.88, green: 0.88, blue: 0.88))
                .cornerRadius(2)
        }
    }
    
    private var adaptiveInstructionsView: some View {
        VStack(spacing: 8) {
            Text("Does the text meaning match the shape color?")
                .font(.system(size: 18, weight: .bold))
                .foregroundColor(Color(red: 0.2, green: 0.2, blue: 0.2))
                .multilineTextAlignment(.center)
            
            if currentPuzzleData.complexityLevel >= 3 {
                Text("💡 Focus on the word meaning, not the text color!")
                    .font(.system(size: 14, weight: .medium))
                    .foregroundColor(Color(red: 0.55, green: 0.29, blue: 0.62))
                    .multilineTextAlignment(.center)
            }
        }
    }
    
    private var adaptiveShapeDisplayView: some View {
        RoundedRectangle(cornerRadius: 16)
            .fill(Color.white)
            .frame(width: 200, height: 200)
            .overlay(
                ShapeView(
                    shapeType: currentChallenge.shapeType,
                    color: currentChallenge.shapeColor
                )
                .frame(width: 120, height: 120)
            )
            .shadow(color: .black.opacity(0.1), radius: 4, x: 0, y: 2)
            .overlay(
                // Complexity indicator for higher levels
                Group {
                    if currentPuzzleData.complexityLevel >= 4 {
                        VStack {
                            Spacer()
                            HStack {
                                Spacer()
                                Text("L\(currentPuzzleData.complexityLevel)")
                                    .font(.system(size: 10, weight: .bold))
                                    .foregroundColor(.white)
                                    .padding(4)
                                    .background(Color.black.opacity(0.5))
                                    .cornerRadius(4)
                                    .padding(8)
                            }
                        }
                    }
                }
            )
    }
    
    private var adaptiveAnswerButtonsView: some View {
        HStack(spacing: 16) {
            // No button
            Button(action: { submitAnswer(false) }) {
                HStack {
                    Text("✗")
                        .font(.system(size: 24))
                        .foregroundColor(.white)
                    Spacer().frame(width: 8)
                    Text("NO")
                        .font(.system(size: 18, weight: .bold))
                        .foregroundColor(.white)
                }
                .frame(maxWidth: .infinity)
                .frame(height: 60)
                .background(Color(red: 0.9, green: 0.24, blue: 0.24))
                .cornerRadius(30)
            }
            .scaleEffect(showFeedback ? 0.95 : 1.0)
            .animation(.easeInOut(duration: 0.1), value: showFeedback)
            
            // Yes button
            Button(action: { submitAnswer(true) }) {
                HStack {
                    Text("✓")
                        .font(.system(size: 24))
                        .foregroundColor(.white)
                    Spacer().frame(width: 8)
                    Text("YES")
                        .font(.system(size: 18, weight: .bold))
                        .foregroundColor(.white)
                }
                .frame(maxWidth: .infinity)
                .frame(height: 60)
                .background(Color(red: 0.22, green: 0.63, blue: 0.41))
                .cornerRadius(30)
            }
            .scaleEffect(showFeedback ? 0.95 : 1.0)
            .animation(.easeInOut(duration: 0.1), value: showFeedback)
        }
    }
    
    private var adaptiveFeedbackView: some View {
        RoundedRectangle(cornerRadius: 16)
            .fill(isCorrect ? Color(red: 0.9, green: 1.0, blue: 0.98) : Color(red: 1.0, green: 0.96, blue: 0.96))
            .frame(height: 100)
            .overlay(
                VStack(spacing: 8) {
                    Text(isCorrect ? "✅ Correct!" : "❌ Incorrect")
                        .font(.system(size: 20, weight: .bold))
                        .foregroundColor(isCorrect ? Color(red: 0.22, green: 0.63, blue: 0.41) : Color(red: 0.9, green: 0.24, blue: 0.24))
                    
                    HStack {
                        Text("Score: \(score)")
                            .font(.system(size: 14))
                            .foregroundColor(Color(red: 0.4, green: 0.4, blue: 0.4))
                        
                        if currentStreak > 1 {
                            Text("• Streak: \(currentStreak) 🔥")
                                .font(.system(size: 14))
                                .foregroundColor(.orange)
                        }
                    }
                }
            )
            .scaleEffect(showFeedback ? 1.0 : 0.8)
            .opacity(showFeedback ? 1.0 : 0.0)
            .animation(.spring(response: 0.3, dampingFraction: 0.6), value: showFeedback)
    }
    
    // MARK: - Game Logic
    
    private func setupAdaptivePuzzle() {
        let currentDifficulty = difficultyManager.currentDifficulty
        currentPuzzleData = EnhancedColorShapeMatchingPuzzleData(difficultyLevel: currentDifficulty)
        currentLives = currentDifficulty.livesAllowed
        timeLeft = currentDifficulty.timeLimit
        currentChallenge = Self.generateAdaptiveChallenge(config: currentDifficulty)
        
        sessionStartTime = Date()
        challengeStartTime = Date()
        
        print("🎨 ADAPTIVE: Set up color shape matching with difficulty: \(currentDifficulty.name)")
        print("🎨 ADAPTIVE: Challenges: \(currentDifficulty.totalChallenges), Complexity: \(currentDifficulty.complexityLevel)")
        
        startTimer()
    }
    
    private func submitAnswer(_ answer: Bool) {
        guard !showFeedback else { return }
        
        selectedAnswer = answer
        isCorrect = answer == currentChallenge.isCorrectMatch
        
        let timeSpent = Date().timeIntervalSince(challengeStartTime)
        totalAnswers += 1
        
        if isCorrect {
            correctAnswers += 1
            currentStreak += 1
            let newScore = calculateAdaptiveScore(isCorrect: true, timeSpent: timeSpent)
            score += newScore
        } else {
            currentStreak = 0
            currentLives = max(0, currentLives - 1)
        }
        
        recordPerformanceAndAdapt(isCorrect: isCorrect, timeSpent: timeSpent)
        
        showFeedback = true
        hapticFeedback(isCorrect: isCorrect)
        onAnswerSubmitted(answer, isCorrect)
    }
    
    private func handleFeedbackComplete() {
        showFeedback = false
        selectedAnswer = nil
        
        if currentLives <= 0 {
            gameCompleted = true
            timer?.invalidate()
            onNextPuzzle()
        } else if challengeNumber >= currentPuzzleData.totalChallenges {
            gameCompleted = true
            timer?.invalidate()
            onNextPuzzle()
        } else {
            challengeNumber += 1
            currentChallenge = Self.generateAdaptiveChallenge(config: currentPuzzleData.difficulty)
            challengeStartTime = Date()
            gamesPlayedThisSession += 1
        }
    }
    
    private func startTimer() {
        timer = Timer.scheduledTimer(withTimeInterval: 1.0, repeats: true) { _ in
            if timeLeft > 0 && !gameCompleted {
                timeLeft -= 1
            } else if timeLeft == 0 {
                timer?.invalidate()
                // Time's up - record poor performance and complete
                recordPerformanceAndAdapt(isCorrect: false, timeSpent: Double(currentPuzzleData.timeLimit))
                gameCompleted = true
                onAnswerSubmitted(false, false)
                onNextPuzzle()
            }
        }
    }
    
    private func recordPerformanceAndAdapt(isCorrect: Bool, timeSpent: TimeInterval) {
        let visualComplexity = Float(currentChallenge.complexityLevel) + currentPuzzleData.difficulty.colorDistractorChance
        
        let performance = ColorShapeMatchingDifficultyManager.PlayerPerformance(
            accuracy: isCorrect ? 1.0 : 0.0,
            timeSpent: Float(timeSpent),
            streakLength: currentStreak,
            livesRemaining: currentLives,
            gameScore: score,
            difficulty: currentPuzzleData.difficulty.name,
            visualComplexity: visualComplexity,
            challengesCompleted: challengeNumber
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
    
    private func calculateAdaptiveScore(isCorrect: Bool, timeSpent: TimeInterval) -> Int {
        guard isCorrect else { return 0 }
        
        let basePoints = currentPuzzleData.difficulty.basePoints
        let complexityMultiplier = 1.0 + Float(currentChallenge.complexityLevel - 1) * 0.2
        
        // Speed bonus based on expected time per challenge
        let expectedTime = Double(currentPuzzleData.timeLimit) / Double(currentPuzzleData.totalChallenges)
        let speedBonus: Int
        
        if timeSpent <= expectedTime * 0.3 {
            speedBonus = Int(Float(basePoints) * 0.3)
        } else if timeSpent <= expectedTime * 0.5 {
            speedBonus = Int(Float(basePoints) * 0.2)
        } else if timeSpent <= expectedTime * 0.7 {
            speedBonus = Int(Float(basePoints) * 0.1)
        } else {
            speedBonus = 0
        }
        
        let streakMultiplier = 1.0 + Float(currentStreak) * 0.1
        let visualProcessingBonus = Int(Float(basePoints) * 0.2 * complexityMultiplier)
        
        let finalScore = Int(Float(basePoints) * complexityMultiplier * streakMultiplier) + speedBonus + visualProcessingBonus
        
        return max(finalScore, basePoints / 4)
    }
    
    private func formatTime(_ seconds: Int) -> String {
        let minutes = seconds / 60
        let remainingSeconds = seconds % 60
        return String(format: "%d:%02d", minutes, remainingSeconds)
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
    
    // MARK: - Challenge Generation
    
    static func generateAdaptiveChallenge(config: ColorShapeMatchingDifficultyManager.DifficultyLevel) -> AdaptiveColorShapeChallenge {
        let colors: [(Color, String)] = [
            (.red, "RED"),
            (.blue, "BLUE"),
            (.green, "GREEN"),
            (.yellow, "YELLOW"),
            (.purple, "PURPLE"),
            (Color(red: 1.0, green: 0.65, blue: 0), "ORANGE"),
            (Color(red: 1.0, green: 0.75, blue: 0.8), "PINK"),
            (Color(red: 0.55, green: 0.27, blue: 0.07), "BROWN")
        ]
        
        let shapes = ShapeType.allCases
        
        let shapeColorPair = colors.randomElement()!
        let shapeColor = shapeColorPair.0
        let shapeColorName = shapeColorPair.1
        let shapeType = shapes.randomElement()!
        
        // Generate challenge based on complexity level
        let scenario: Int
        switch config.complexityLevel {
        case 1:
            scenario = 0 // Always matches for beginners
        case 2:
            scenario = Int.random(in: 0..<2) // 50/50 for easy
        default:
            scenario = Int.random(in: 0..<3) // Full complexity for medium+
        }
        
        switch scenario {
        case 0:
            // Correct color name in correct font color (MATCH)
            return AdaptiveColorShapeChallenge(
                shapeColor: shapeColor,
                shapeType: shapeType,
                colorNameText: shapeColorName,
                colorNameFontColor: shapeColor,
                isCorrectMatch: true,
                complexityLevel: config.complexityLevel
            )
        case 1:
            // Correct color name in wrong font color (STILL A MATCH - text content matters)
            let wrongFontColor = colors.filter { $0.0 != shapeColor }.randomElement()!.0
            return AdaptiveColorShapeChallenge(
                shapeColor: shapeColor,
                shapeType: shapeType,
                colorNameText: shapeColorName,
                colorNameFontColor: wrongFontColor,
                isCorrectMatch: true,
                complexityLevel: config.complexityLevel
            )
        default:
            // Wrong color name (NO MATCH)
            let wrongColorName = colors.filter { $0.1 != shapeColorName }.randomElement()!.1
            let fontColor = colors.randomElement()!.0
            return AdaptiveColorShapeChallenge(
                shapeColor: shapeColor,
                shapeType: shapeType,
                colorNameText: wrongColorName,
                colorNameFontColor: fontColor,
                isCorrectMatch: false,
                complexityLevel: config.complexityLevel
            )
        }
    }
}

// MARK: - Enhanced UI Components

struct AdaptiveDifficultyBadgeView: View {
    let difficulty: String
    
    private var badgeColors: (background: Color, text: Color) {
        switch difficulty.lowercased() {
        case "beginner":
            return (Color(red: 0.85, green: 0.98, blue: 0.85), Color(red: 0.15, green: 0.55, blue: 0.15))
        case "easy":
            return (Color(red: 0.9, green: 1.0, blue: 0.98), Color(red: 0.22, green: 0.63, blue: 0.41))
        case "medium":
            return (Color(red: 1.0, green: 0.95, blue: 0.8), Color(red: 0.84, green: 0.62, blue: 0.18))
        case "hard":
            return (Color(red: 1.0, green: 0.84, blue: 0.84), Color(red: 0.9, green: 0.24, blue: 0.24))
        case "expert":
            return (Color(red: 0.95, green: 0.85, blue: 1.0), Color(red: 0.55, green: 0.29, blue: 0.62))
        case "master":
            return (Color(red: 1.0, green: 0.95, blue: 0.7), Color(red: 0.8, green: 0.6, blue: 0.0))
        default:
            return (Color(red: 0.94, green: 0.94, blue: 0.94), Color(red: 0.4, green: 0.4, blue: 0.4))
        }
    }
    
    var body: some View {
        Text(difficulty.uppercased())
            .font(.system(size: 12, weight: .bold))
            .foregroundColor(badgeColors.text)
            .padding(.horizontal, 12)
            .padding(.vertical, 6)
            .background(badgeColors.background)
            .overlay(
                RoundedRectangle(cornerRadius: 20)
                    .stroke(badgeColors.text.opacity(0.3), lineWidth: 1)
            )
            .cornerRadius(20)
    }
}

struct AdaptiveTimerDisplayView: View {
    let timer: String
    
    private var timerColors: (background: Color, text: Color) {
        if timer.hasPrefix("0:") {
            let seconds = Int(timer.dropFirst(2)) ?? 0
            if seconds <= 30 {
                return (Color(red: 1.0, green: 0.84, blue: 0.84), Color(red: 0.9, green: 0.24, blue: 0.24))
            }
        }
        if timer.hasPrefix("1:") {
            return (Color(red: 1.0, green: 0.95, blue: 0.8), Color(red: 0.84, green: 0.62, blue: 0.18))
        }
        return (Color(red: 0.9, green: 1.0, blue: 0.98), Color(red: 0.22, green: 0.63, blue: 0.41))
    }
    
    var body: some View {
        HStack(spacing: 6) {
            Text("⏰")
                .font(.system(size: 14))
            
            Text(timer)
                .font(.system(size: 14, weight: .bold))
                .foregroundColor(timerColors.text)
        }
        .padding(.horizontal, 12)
        .padding(.vertical, 6)
        .background(timerColors.background)
        .overlay(
            RoundedRectangle(cornerRadius: 20)
                .stroke(timerColors.text.opacity(0.3), lineWidth: 1)
        )
        .cornerRadius(20)
    }
}

// Keep existing ShapeView and custom shapes from original implementation...
struct ShapeView: View {
    let shapeType: ShapeType
    let color: Color
    
    var body: some View {
        switch shapeType {
        case .circle:
            Circle()
                .fill(color)
        case .square:
            Rectangle()
                .fill(color)
        case .triangle:
            TriangleShape()
                .fill(color)
        case .hexagon:
            HexagonShape()
                .fill(color)
        case .diamond:
            DiamondShape()
                .fill(color)
        case .star:
            StarShape()
                .fill(color)
        }
    }
}

// Keep existing shape implementations from original file...
enum ShapeType: CaseIterable {
    case circle, square, triangle, hexagon, diamond, star
}

struct TriangleShape: Shape {
    func path(in rect: CGRect) -> Path {
        var path = Path()
        path.move(to: CGPoint(x: rect.midX, y: rect.minY))
        path.addLine(to: CGPoint(x: rect.minX, y: rect.maxY))
        path.addLine(to: CGPoint(x: rect.maxX, y: rect.maxY))
        path.closeSubpath()
        return path
    }
}

struct HexagonShape: Shape {
    func path(in rect: CGRect) -> Path {
        var path = Path()
        let center = CGPoint(x: rect.midX, y: rect.midY)
        let radius = min(rect.width, rect.height) / 2
        
        for i in 0..<6 {
            let angle = Double(i) * Double.pi / 3 - Double.pi / 2
            let x = center.x + radius * cos(angle)
            let y = center.y + radius * sin(angle)
            
            if i == 0 {
                path.move(to: CGPoint(x: x, y: y))
            } else {
                path.addLine(to: CGPoint(x: x, y: y))
            }
        }
        path.closeSubpath()
        return path
    }
}

struct DiamondShape: Shape {
    func path(in rect: CGRect) -> Path {
        var path = Path()
        path.move(to: CGPoint(x: rect.midX, y: rect.minY))
        path.addLine(to: CGPoint(x: rect.maxX, y: rect.midY))
        path.addLine(to: CGPoint(x: rect.midX, y: rect.maxY))
        path.addLine(to: CGPoint(x: rect.minX, y: rect.midY))
        path.closeSubpath()
        return path
    }
}

struct StarShape: Shape {
    func path(in rect: CGRect) -> Path {
        var path = Path()
        let center = CGPoint(x: rect.midX, y: rect.midY)
        let outerRadius = min(rect.width, rect.height) / 2
        let innerRadius = outerRadius * 0.4
        let points = 5
        
        for i in 0..<points * 2 {
            let angle = Double(i) * Double.pi / Double(points) - Double.pi / 2
            let radius = i % 2 == 0 ? outerRadius : innerRadius
            let x = center.x + radius * cos(angle)
            let y = center.y + radius * sin(angle)
            
            if i == 0 {
                path.move(to: CGPoint(x: x, y: y))
            } else {
                path.addLine(to: CGPoint(x: x, y: y))
            }
        }
        path.closeSubpath()
        return path
    }
}
