//
//  SymbolSwipePuzzleView.swift
//  PuzzleForge
//
//  iOS implementation of Symbol Swipe puzzle from Android
//

import SwiftUI
import Foundation
import Combine

// MARK: - Data Models
struct AnotherGameSymbol {
    let symbol: String
    let color: Color
    let name: String
}

struct AnotherSymbolItem {
    let symbol: AnotherGameSymbol
    let id = UUID()
}

enum AnotherSwipeDirection {
    case left, right
}

struct AnotherSymbolSwipeData {
    let leftSymbol: AnotherGameSymbol
    let rightSymbol: AnotherGameSymbol
    let symbolCount: Int
    let timePerSymbol: Double
    let difficulty: String
}

struct SymbolSwipeGameData: Codable {
    let symbols: SymbolConfiguration
    let sequence: [SymbolSequenceItem]
    let totalSymbols: Int
    let difficulty: String
    let timeLimit: Double
    let instructions: String
    
    struct SymbolConfiguration: Codable {
        let leftSymbol: GeneratedGameSymbol
        let rightSymbol: GeneratedGameSymbol
    }
    
    struct SymbolSequenceItem: Codable {
        let symbol: GeneratedGameSymbol
        let id: Int
        let expectedDirection: String
        let sequenceIndex: Int
    }
    
    struct GeneratedGameSymbol: Codable {
        let name: String
        let color: String
        let shape: String
    }
}

// MARK: - Supporting Data Models
struct GameSymbol {
    let symbol: String
    let color: Color
    let name: String
    let shape: String
}

struct SymbolItem {
    let symbol: GameSymbol
    let id = UUID()
}

enum SwipeDirection {
    case left, right
}

struct AdaptiveSymbolSwipeConfig {
    let symbolCount: Int
    let timePerSymbol: Float
    let totalTime: Int
    let description: String
}

// MARK: - Main Adaptive Symbol Swipe View
struct SymbolSwipePuzzleView: View {
    let puzzle: Puzzle
    let questionIndex: Int
    let totalQuestions: Int
    let onAnswerSubmitted: (Bool) -> Void
    let onNextPuzzle: () -> Void
    let onExit: () -> Void
    
    // Dependency injection
    @StateObject private var difficultyManager = DifficultyManager.shared
    @StateObject private var competitiveManager = CompetitiveRankingManager.shared
    @StateObject private var feedbackManager = UnifiedFeedbackManager()
    
    // Adaptive difficulty state
    @State private var currentDifficultyLevel = DifficultyManager.DifficultyLevel.medium
    @State private var adaptationInfo: DifficultyManager.AdaptiveConfig?
    @State private var showAdaptationNotification = false
    @State private var competitiveInsight: CompetitiveInsight?
    
    // Game state
    @State private var timeLeft = 60
    @State private var isPaused = false
    @State private var currentHearts = 3
    @State private var gameCompleted = false
    @State private var gameStarted = false
    @State private var showFeedback = false
    @State private var feedbackMessage = ""
    @State private var feedbackColor = Color.green
    
    // Symbol configuration
    @State private var leftSymbol: GameSymbol?
    @State private var rightSymbol: GameSymbol?
    @State private var symbolSequence: [SymbolItem] = []
    @State private var currentSymbolIndex = 0
    @State private var score = 0
    @State private var totalScore = 0
    @State private var multiplier = 1
    @State private var consecutiveCorrect = 0
    @State private var gamesPlayedThisSession = 0
    
    // Session tracking
    @State private var sessionStartTime = Date()
    @State private var correctAnswers = 0
    @State private var totalAnswers = 0
    
    // Animation states
    @State private var currentSymbolOffset: CGFloat = 0
    @State private var symbolScale: CGFloat = 1.0
    @State private var dragStarted = false
    
    // Available game symbols
    private let gameSymbols: [GameSymbol] = [
        GameSymbol(symbol: "⭐", color: Color(red: 0.3, green: 0.69, blue: 0.31), name: "Star", shape: "star"),
        GameSymbol(symbol: "●", color: Color(red: 0.13, green: 0.59, blue: 0.95), name: "Circle", shape: "circle"),
        GameSymbol(symbol: "▲", color: Color(red: 1.0, green: 0.6, blue: 0.0), name: "Triangle", shape: "triangle"),
        GameSymbol(symbol: "♦", color: Color(red: 0.91, green: 0.12, blue: 0.39), name: "Diamond", shape: "diamond"),
        GameSymbol(symbol: "■", color: Color(red: 0.61, green: 0.15, blue: 0.69), name: "Square", shape: "square"),
        GameSymbol(symbol: "♥", color: Color(red: 1.0, green: 0.34, blue: 0.13), name: "Heart", shape: "heart"),
        GameSymbol(symbol: "⬡", color: Color(red: 0.0, green: 0.74, blue: 0.83), name: "Hexagon", shape: "hexagon"),
        GameSymbol(symbol: "✚", color: Color(red: 0.47, green: 0.33, blue: 0.28), name: "Cross", shape: "cross")
    ]
    
    var body: some View {
        ZStack {
            Color(red: 0.42, green: 0.11, blue: 0.60)
                .ignoresSafeArea()
            
            VStack(spacing: 0) {
                Spacer().frame(height: 8)
                
                // Unified header
                AdaptiveUnifiedHeader(
                    puzzleType: "symbolSwipe",
                    currentDifficulty: currentDifficultyLevel,
                    score: totalScore,
                    challengeNumber: currentSymbolIndex + 1,
                    totalChallenges: symbolSequence.count,
                    timer: formatTime(timeLeft),
                    lives: currentHearts,
                    competitiveInsight: competitiveInsight,
                    level: UserLevel(level: 5, currentXP: 750, xpToNextLevel: 1000, totalXP: 3250),
                    streakInfo: StreakInfo(
                        currentStreak: consecutiveCorrect,
                        bestStreak: 12,
                        streakMultiplier: consecutiveCorrect >= 3 ? 1.5 : 1.0,
                        dailyStreak: 3,
                        hasDailyStreakBonus: true
                    ),
                    onBack: onExit,
                    onPause: { isPaused.toggle() },
                    onHint: { showHint() }
                )
                
                // Adaptation notification
                UnifiedAdaptationNotification(
                    adaptationInfo: adaptationInfo,
                    puzzleType: "symbolSwipe",
                    visible: showAdaptationNotification,
                    onDismiss: { showAdaptationNotification = false }
                )
                
                if !gameStarted {
                    instructionsSection
                } else if !gameCompleted && currentSymbolIndex < symbolSequence.count {
                    gameAreaSection
                }
                
                if showFeedback {
                    feedbackSection
                }
                
                Spacer()
            }
        }
        .navigationBarHidden(true)
        .onAppear {
            setupGame()
        }
    }
    
    // MARK: - Instructions Section
    private var instructionsSection: some View {
        VStack(spacing: 20) {
            Spacer()
            
            VStack(spacing: 16) {
                Text("🔄 Adaptive Symbol Swipe")
                    .font(.title2)
                    .fontWeight(.bold)
                    .foregroundColor(.white)
                
                Text("When you see the LEFT symbol, swipe LEFT\nWhen you see the RIGHT symbol, swipe RIGHT")
                    .font(.subheadline)
                    .foregroundColor(.white)
                    .multilineTextAlignment(.center)
                
                Text(currentDifficultyLevel.description)
                    .font(.caption)
                    .foregroundColor(.cyan)
                    .multilineTextAlignment(.center)
                
                // Show the two symbols that will be used
                if let leftSymbol = leftSymbol, let rightSymbol = rightSymbol {
                    HStack(spacing: 32) {
                        VStack(spacing: 8) {
                            Text(leftSymbol.symbol)
                                .font(.system(size: 40))
                                .foregroundColor(leftSymbol.color)
                                .frame(width: 60, height: 60)
                                .background(Color.gray.opacity(0.1))
                                .cornerRadius(12)
                            
                            Text("← SWIPE LEFT")
                                .font(.caption)
                                .foregroundColor(.gray)
                        }
                        
                        VStack(spacing: 8) {
                            Text(rightSymbol.symbol)
                                .font(.system(size: 40))
                                .foregroundColor(rightSymbol.color)
                                .frame(width: 60, height: 60)
                                .background(Color.gray.opacity(0.1))
                                .cornerRadius(12)
                            
                            Text("SWIPE RIGHT →")
                                .font(.caption)
                                .foregroundColor(.gray)
                        }
                    }
                }
                
                Button(action: startGame) {
                    Text("START")
                        .font(.headline)
                        .fontWeight(.bold)
                        .foregroundColor(.white)
                        .frame(maxWidth: .infinity)
                        .padding()
                        .background(Color(red: 0.3, green: 0.69, blue: 0.31))
                        .cornerRadius(12)
                }
            }
            .padding()
            .background(Color.white)
            .cornerRadius(16)
            .padding(.horizontal)
            
            Spacer()
        }
    }
    
    // MARK: - Game Area Section
    private var gameAreaSection: some View {
        VStack(spacing: 20) {
            // Reference symbols in corners
            if let leftSymbol = leftSymbol, let rightSymbol = rightSymbol {
                HStack {
                    VStack(spacing: 4) {
                        Text(leftSymbol.symbol)
                            .font(.system(size: 30))
                            .foregroundColor(leftSymbol.color)
                            .frame(width: 60, height: 60)
                            .background(Color.black.opacity(0.3))
                            .cornerRadius(12)
                        
                        Text("LEFT")
                            .font(.caption)
                            .fontWeight(.bold)
                            .foregroundColor(.white.opacity(0.6))
                    }
                    
                    Spacer()
                    
                    VStack(spacing: 4) {
                        Text(rightSymbol.symbol)
                            .font(.system(size: 30))
                            .foregroundColor(rightSymbol.color)
                            .frame(width: 60, height: 60)
                            .background(Color.black.opacity(0.3))
                            .cornerRadius(12)
                        
                        Text("RIGHT")
                            .font(.caption)
                            .fontWeight(.bold)
                            .foregroundColor(.white.opacity(0.6))
                    }
                }
                .padding(.horizontal, 32)
            }
            
            Spacer()
            
            // Current symbol display
            if currentSymbolIndex < symbolSequence.count {
                let currentSymbol = symbolSequence[currentSymbolIndex]
                
                Text(currentSymbol.symbol.symbol)
                    .font(.system(size: 120))
                    .foregroundColor(currentSymbol.symbol.color)
                    .frame(width: 200, height: 200)
                    .background(
                        RoundedRectangle(cornerRadius: 20)
                            .fill(Color.white.opacity(0.95))
                    )
                    .scaleEffect(symbolScale)
                    .offset(x: currentSymbolOffset)
                    .gesture(swipeGesture)
            }
            
            Spacer()
            
            // Progress dots
            progressView
            
            // Lives display
            livesDisplayView
        }
        .padding(.horizontal, 16)
    }
    
    private var progressView: some View {
        HStack(spacing: 6) {
            ForEach(0..<min(symbolSequence.count, 12), id: \.self) { index in
                Circle()
                    .fill(progressColor(for: index))
                    .frame(width: 6, height: 6)
            }
        }
    }
    
    private var livesDisplayView: some View {
        HStack(spacing: 4) {
            ForEach(0..<currentDifficultyLevel.livesAllowed, id: \.self) { index in
                Text(index < currentHearts ? "❤️" : "🤍")
                    .font(.system(size: 16))
                    .opacity(index < currentHearts ? 1.0 : 0.3)
            }
        }
    }
    
    // MARK: - Feedback Section
    private var feedbackSection: some View {
        VStack {
            Text(feedbackMessage)
                .font(.headline)
                .fontWeight(.bold)
                .foregroundColor(.white)
                .padding()
                .background(feedbackColor.opacity(0.9))
                .cornerRadius(12)
                .padding(.horizontal)
        }
        .transition(.move(edge: .bottom).combined(with: .opacity))
    }
    
    // MARK: - Gesture Recognition
    private var swipeGesture: some Gesture {
        DragGesture()
            .onChanged { value in
                if !dragStarted {
                    dragStarted = true
                    withAnimation(.easeInOut(duration: 0.1)) {
                        symbolScale = 1.05
                    }
                }
                currentSymbolOffset = value.translation.width * 0.5
                currentSymbolOffset = max(-300, min(300, currentSymbolOffset))
            }
            .onEnded { value in
                dragStarted = false
                withAnimation(.easeInOut(duration: 0.2)) {
                    symbolScale = 1.0
                }
                
                if abs(currentSymbolOffset) > 100 {
                    let direction: SwipeDirection = currentSymbolOffset > 0 ? .right : .left
                    handleSwipe(direction)
                }
                
                withAnimation(.spring()) {
                    currentSymbolOffset = 0
                }
            }
    }
    
    // MARK: - Game Logic
    private func setupGame() {
        currentDifficultyLevel = difficultyManager.getCurrentDifficulty(for: "symbolSwipe")
        currentHearts = currentDifficultyLevel.livesAllowed
        
        let config = generateAdaptiveSymbolSwipeConfig(currentDifficultyLevel)
        timeLeft = config.totalTime
        
        generateGameSymbols()
        
        Task {
            competitiveInsight = await competitiveManager.getCompetitiveInsight(
                userId: "user123",
                puzzleType: "symbolSwipe",
                difficulty: currentDifficultyLevel.name
            )
        }
    }
    
    private func generateGameSymbols() {
        let shuffledSymbols = gameSymbols.shuffled()
        leftSymbol = shuffledSymbols[0]
        rightSymbol = shuffledSymbols[1]
        
        guard let left = leftSymbol, let right = rightSymbol else { return }
        
        let config = generateAdaptiveSymbolSwipeConfig(currentDifficultyLevel)
        symbolSequence = (1...config.symbolCount).map { index in
            SymbolItem(symbol: Bool.random() ? left : right)
        }
    }
    
    private func generateAdaptiveSymbolSwipeConfig(_ difficulty: DifficultyManager.DifficultyLevel) -> AdaptiveSymbolSwipeConfig {
        switch difficulty {
        case .beginner:
            return AdaptiveSymbolSwipeConfig(
                symbolCount: 10,
                timePerSymbol: 4.0,
                totalTime: 40,
                description: "10 symbols, 4s each - Learn the basics"
            )
        case .easy:
            return AdaptiveSymbolSwipeConfig(
                symbolCount: 15,
                timePerSymbol: 3.0,
                totalTime: 45,
                description: "15 symbols, 3s each - Building speed"
            )
        case .medium:
            return AdaptiveSymbolSwipeConfig(
                symbolCount: 20,
                timePerSymbol: 2.5,
                totalTime: 50,
                description: "20 symbols, 2.5s each - Good pace"
            )
        case .hard:
            return AdaptiveSymbolSwipeConfig(
                symbolCount: 25,
                timePerSymbol: 2.0,
                totalTime: 50,
                description: "25 symbols, 2s each - Fast reflexes"
            )
        case .expert:
            return AdaptiveSymbolSwipeConfig(
                symbolCount: 30,
                timePerSymbol: 1.5,
                totalTime: 45,
                description: "30 symbols, 1.5s each - Lightning speed"
            )
        }
    }
    
    private func startGame() {
        gameStarted = true
        sessionStartTime = Date()
        startTimer()
    }
    
    private func handleSwipe(_ direction: SwipeDirection) {
        guard currentSymbolIndex < symbolSequence.count,
              let left = leftSymbol,
              let right = rightSymbol else { return }
        
        let currentSymbol = symbolSequence[currentSymbolIndex].symbol
        let requiredDirection = getRequiredDirection(symbol: currentSymbol, leftSymbol: left, rightSymbol: right)
        let isCorrect = direction == requiredDirection
        
        totalAnswers += 1
        
        if isCorrect {
            correctAnswers += 1
            consecutiveCorrect += 1
            multiplier = calculateMultiplier()
            
            let symbolScore = calculateScore(isCorrect: true, timeSpent: Date().timeIntervalSince(sessionStartTime))
            let points = symbolScore * multiplier
            score += points
            totalScore += points
            
            feedbackMessage = multiplier > 1 ? "Perfect! +\(points) (x\(multiplier))" : "Correct! +\(points)"
            feedbackColor = Color(red: 0.3, green: 0.69, blue: 0.31)
        } else {
            consecutiveCorrect = 0
            multiplier = 1
            currentHearts = max(0, currentHearts - 1)
            
            feedbackMessage = "Wrong direction! Lives: \(currentHearts)"
            feedbackColor = Color(red: 0.91, green: 0.12, blue: 0.39)
            
            if currentHearts == 0 {
                endGame(success: false)
                return
            }
        }
        
        // Record performance for adaptation
        recordPerformance(isCorrect: isCorrect, timeSpent: Date().timeIntervalSince(sessionStartTime))
        
        showFeedback = true
        currentSymbolIndex += 1
        
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.6) {
            showFeedback = false
            
            if currentSymbolIndex >= symbolSequence.count {
                endGame(success: true)
            }
        }
    }
    
    private func getRequiredDirection(symbol: GameSymbol, leftSymbol: GameSymbol, rightSymbol: GameSymbol) -> SwipeDirection {
        return symbol.name == leftSymbol.name ? .left : .right
    }
    
    private func calculateMultiplier() -> Int {
        switch consecutiveCorrect {
        case 15...: return 5
        case 10..<15: return 4
        case 5..<10: return 3
        case 3..<5: return 2
        default: return 1
        }
    }
    
    private func calculateScore(isCorrect: Bool, timeSpent: TimeInterval) -> Int {
        let baseScore = currentDifficultyLevel.basePoints
        let timeBonus = isCorrect ? max(0, Int(Double(currentDifficultyLevel.timeLimit) - timeSpent)) : 0
        return baseScore + timeBonus
    }
    
    private func recordPerformance(isCorrect: Bool, timeSpent: TimeInterval) {
        if let config = difficultyManager.recordPerformance(
            puzzleType: "symbolSwipe",
            isCorrect: isCorrect,
            timeSpent: timeSpent,
            difficulty: currentDifficultyLevel,
            streak: consecutiveCorrect,
            livesRemaining: currentHearts,
            gameScore: totalScore,
            challengesCompleted: currentSymbolIndex + 1
        ) {
            if config.shouldNotify && config.level != currentDifficultyLevel {
                adaptationInfo = config
                currentDifficultyLevel = config.level
                showAdaptationNotification = true
            }
        }
    }
    
    private func endGame(success: Bool) {
        gameCompleted = true
        gamesPlayedThisSession += 1
        
        onAnswerSubmitted(success)
        
        DispatchQueue.main.asyncAfter(deadline: .now() + 1.0) {
            onNextPuzzle()
        }
    }
    
    private func startTimer() {
        Timer.scheduledTimer(withTimeInterval: 1.0, repeats: true) { timer in
            if gameStarted && !isPaused && !gameCompleted {
                if timeLeft > 0 && currentSymbolIndex < symbolSequence.count {
                    timeLeft -= 1
                } else {
                    timer.invalidate()
                    endGame(success: currentSymbolIndex >= symbolSequence.count)
                }
            }
        }
    }
    
    private func showHint() {
        guard currentSymbolIndex < symbolSequence.count,
              let left = leftSymbol,
              let right = rightSymbol else { return }
        
        let currentSymbol = symbolSequence[currentSymbolIndex].symbol
        let direction = currentSymbol.name == left.name ? "LEFT" : "RIGHT"
        
        feedbackMessage = "Hint: Swipe \(direction) for this symbol"
        feedbackColor = Color.blue
        showFeedback = true
        
        DispatchQueue.main.asyncAfter(deadline: .now() + 1.5) {
            showFeedback = false
        }
    }
    
    private func progressColor(for index: Int) -> Color {
        if index < currentSymbolIndex {
            return Color(red: 0.3, green: 0.69, blue: 0.31)
        } else if index == currentSymbolIndex {
            return Color(red: 1.0, green: 0.92, blue: 0.23)
        } else {
            return Color.white.opacity(0.4)
        }
    }
    
    private func formatTime(_ seconds: Int) -> String {
        return String(format: "%02d:%02d", seconds / 60, seconds % 60)
    }
}

extension LocalMemoryPuzzleGenerator {
    
    // MARK: - Symbol Swipe Data Structures for Generator
    struct GeneratorSymbolSwipeData: Codable {
        let symbols: GeneratorSymbolConfiguration
        let sequence: [GeneratorSymbolItem]
        let totalSymbols: Int
        let difficulty: String
        let timeLimit: Double
        let scoring: GeneratorScoringConfiguration
        let instructions: String
        let metadata: GeneratorSymbolSwipeMetadata
        
        struct GeneratorSymbolConfiguration: Codable {
            let leftSymbol: GeneratorGameSymbol
            let rightSymbol: GeneratorGameSymbol
        }
        
        struct GeneratorSymbolItem: Codable {
            let symbol: GeneratorGameSymbol
            let id: Int
            let expectedDirection: String
            let sequenceIndex: Int
        }
        
        struct GeneratorGameSymbol: Codable {
            let name: String
            let color: String
            let shape: String
        }
        
        struct GeneratorScoringConfiguration: Codable {
            let pointsPerCorrect: Int
            let timeBonus: Bool
            let streakMultiplier: [String: Int]
            let maxStreak: Int
        }
        
        struct GeneratorSymbolSwipeMetadata: Codable {
            let generatedAt: String
            let difficulty: String
            let symbolCount: Int
            let timePerSymbol: Double
            let expectedSwipePattern: [String]
        }
    }
    
    struct GeneratorSymbolSwipeAnswer: Codable {
        let correctSequence: [String]
        let symbolMapping: [String: String]
        let scoring: GeneratorSymbolSwipeData.GeneratorScoringConfiguration
        let maxScore: Int
        let perfectTimeScore: Int
    }
    
    static func generateSymbolSwipe(difficulty: String) -> (String, String) {
        print("ðŸ”„ Generating symbol swipe puzzle with difficulty: \(difficulty)")
        
        do {
            let config = getSymbolSwipeDifficultyConfig(for: difficulty)
            let (leftSymbol, rightSymbol) = generateTwoRandomSymbols()
            let symbolSequence = generateSymbolSequence(
                leftSymbol: leftSymbol,
                rightSymbol: rightSymbol,
                count: config.symbolCount
            )
            
            let questionData = createSymbolSwipeQuestionData(
                leftSymbol: leftSymbol,
                rightSymbol: rightSymbol,
                sequence: symbolSequence,
                config: config,
                difficulty: difficulty
            )
            
            let answerData = createSymbolSwipeAnswerData(
                sequence: symbolSequence,
                leftSymbol: leftSymbol,
                rightSymbol: rightSymbol,
                config: config
            )
            
            let questionJSON = try JSONEncoder().encode(questionData)
            let answerJSON = try JSONEncoder().encode(answerData)
            
            guard let questionString = String(data: questionJSON, encoding: .utf8),
                  let answerString = String(data: answerJSON, encoding: .utf8) else {
                throw NSError(domain: "JSONError", code: 1, userInfo: [NSLocalizedDescriptionKey: "Failed to encode JSON"])
            }
            
            print("âœ… Successfully generated symbol swipe puzzle")
            print("ðŸ“Š Symbol count: \(symbolSequence.count)")
            print("ðŸŽ¯ Left symbol: \(leftSymbol.name), Right symbol: \(rightSymbol.name)")
            
            return (questionString, answerString)
            
        } catch {
            print("âŒ Failed to generate symbol swipe puzzle: \(error)")
            return generateFallbackSymbolSwipe()
        }
    }
    
    private static func getSymbolSwipeDifficultyConfig(for difficulty: String) -> SymbolSwipeConfig {
        switch difficulty.lowercased() {
        case "easy":
            return SymbolSwipeConfig(symbolCount: 15, timePerSymbol: 3.0, pointsPerCorrect: 10, streakMultipliers: [3: 2, 5: 3, 10: 4], maxStreak: 10)
        case "medium":
            return SymbolSwipeConfig(symbolCount: 20, timePerSymbol: 2.5, pointsPerCorrect: 15, streakMultipliers: [3: 2, 5: 3, 8: 4, 12: 5], maxStreak: 12)
        case "hard":
            return SymbolSwipeConfig(symbolCount: 25, timePerSymbol: 2.0, pointsPerCorrect: 20, streakMultipliers: [3: 2, 5: 3, 8: 4, 15: 5], maxStreak: 15)
        case "expert":
            return SymbolSwipeConfig(symbolCount: 30, timePerSymbol: 1.5, pointsPerCorrect: 25, streakMultipliers: [3: 2, 5: 3, 8: 4, 12: 5, 20: 6], maxStreak: 20)
        default:
            return getSymbolSwipeDifficultyConfig(for: "medium")
        }
    }
    
    private static func generateTwoRandomSymbols() -> (GeneratorSymbolSwipeData.GeneratorGameSymbol, GeneratorSymbolSwipeData.GeneratorGameSymbol) {
        let availableSymbols = [
            GeneratorSymbolSwipeData.GeneratorGameSymbol(name: "Star", color: "#4CAF50", shape: "star"),
            GeneratorSymbolSwipeData.GeneratorGameSymbol(name: "Circle", color: "#2196F3", shape: "circle"),
            GeneratorSymbolSwipeData.GeneratorGameSymbol(name: "Triangle", color: "#FF9800", shape: "triangle"),
            GeneratorSymbolSwipeData.GeneratorGameSymbol(name: "Diamond", color: "#E91E63", shape: "diamond"),
            GeneratorSymbolSwipeData.GeneratorGameSymbol(name: "Square", color: "#9C27B0", shape: "square"),
            GeneratorSymbolSwipeData.GeneratorGameSymbol(name: "Heart", color: "#FF5722", shape: "heart"),
            GeneratorSymbolSwipeData.GeneratorGameSymbol(name: "Hexagon", color: "#00BCD4", shape: "hexagon"),
            GeneratorSymbolSwipeData.GeneratorGameSymbol(name: "Cross", color: "#795548", shape: "cross")
        ]
        
        let shuffledSymbols = availableSymbols.shuffled()
        return (shuffledSymbols[0], shuffledSymbols[1])
    }
    
    private static func generateSymbolSequence(
        leftSymbol: GeneratorSymbolSwipeData.GeneratorGameSymbol,
        rightSymbol: GeneratorSymbolSwipeData.GeneratorGameSymbol,
        count: Int
    ) -> [GeneratorSymbolSwipeData.GeneratorSymbolItem] {
        var sequence: [GeneratorSymbolSwipeData.GeneratorSymbolItem] = []
        
        // Create balanced distribution
        let leftCount = count / 2
        let rightCount = count - leftCount
        
        var symbolTypes: [Bool] = []
        symbolTypes.append(contentsOf: Array(repeating: true, count: leftCount))
        symbolTypes.append(contentsOf: Array(repeating: false, count: rightCount))
        symbolTypes.shuffle()
        
        for (index, useLeftSymbol) in symbolTypes.enumerated() {
            let selectedSymbol = useLeftSymbol ? leftSymbol : rightSymbol
            let expectedDirection = useLeftSymbol ? "left" : "right"
            
            let symbolItem = GeneratorSymbolSwipeData.GeneratorSymbolItem(
                symbol: selectedSymbol,
                id: index + 1,
                expectedDirection: expectedDirection,
                sequenceIndex: index
            )
            
            sequence.append(symbolItem)
        }
        
        return sequence
    }
    
    private static func createSymbolSwipeQuestionData(
        leftSymbol: GeneratorSymbolSwipeData.GeneratorGameSymbol,
        rightSymbol: GeneratorSymbolSwipeData.GeneratorGameSymbol,
        sequence: [GeneratorSymbolSwipeData.GeneratorSymbolItem],
        config: SymbolSwipeConfig,
        difficulty: String
    ) -> GeneratorSymbolSwipeData {
        let totalTime = Double(sequence.count) * config.timePerSymbol
        
        let symbolConfiguration = GeneratorSymbolSwipeData.GeneratorSymbolConfiguration(
            leftSymbol: leftSymbol,
            rightSymbol: rightSymbol
        )
        
        let scoringConfig = GeneratorSymbolSwipeData.GeneratorScoringConfiguration(
            pointsPerCorrect: config.pointsPerCorrect,
            timeBonus: true,
            streakMultiplier: config.streakMultipliers.mapKeys { String($0) },
            maxStreak: config.maxStreak
        )
        
        let expectedPattern = sequence.map { $0.expectedDirection }
        
        let metadata = GeneratorSymbolSwipeData.GeneratorSymbolSwipeMetadata(
            generatedAt: ISO8601DateFormatter().string(from: Date()),
            difficulty: difficulty,
            symbolCount: sequence.count,
            timePerSymbol: config.timePerSymbol,
            expectedSwipePattern: expectedPattern
        )
        
        return GeneratorSymbolSwipeData(
            symbols: symbolConfiguration,
            sequence: sequence,
            totalSymbols: sequence.count,
            difficulty: difficulty,
            timeLimit: totalTime,
            scoring: scoringConfig,
            instructions: "When you see the LEFT symbol, swipe LEFT. When you see the RIGHT symbol, swipe RIGHT. Be quick and accurate!",
            metadata: metadata
        )
    }
    
    private static func createSymbolSwipeAnswerData(
        sequence: [GeneratorSymbolSwipeData.GeneratorSymbolItem],
        leftSymbol: GeneratorSymbolSwipeData.GeneratorGameSymbol,
        rightSymbol: GeneratorSymbolSwipeData.GeneratorGameSymbol,
        config: SymbolSwipeConfig
    ) -> GeneratorSymbolSwipeAnswer {
        let correctSequence = sequence.map { $0.expectedDirection }
        let symbolMapping = [leftSymbol.name: "left", rightSymbol.name: "right"]
        
        let scoringConfig = GeneratorSymbolSwipeData.GeneratorScoringConfiguration(
            pointsPerCorrect: config.pointsPerCorrect,
            timeBonus: true,
            streakMultiplier: config.streakMultipliers.mapKeys { String($0) },
            maxStreak: config.maxStreak
        )
        
        let maxMultiplier = config.streakMultipliers.values.max() ?? 1
        let maxScore = sequence.count * config.pointsPerCorrect * maxMultiplier
        let perfectTimeScore = sequence.count * 5
        
        return GeneratorSymbolSwipeAnswer(
            correctSequence: correctSequence,
            symbolMapping: symbolMapping,
            scoring: scoringConfig,
            maxScore: maxScore,
            perfectTimeScore: perfectTimeScore
        )
    }
    
    private static func generateFallbackSymbolSwipe() -> (String, String) {
        print("ðŸ”„ Generating fallback symbol swipe puzzle")
        
        let leftSymbol = GeneratorSymbolSwipeData.GeneratorGameSymbol(name: "Circle", color: "#2196F3", shape: "circle")
        let rightSymbol = GeneratorSymbolSwipeData.GeneratorGameSymbol(name: "Square", color: "#9C27B0", shape: "square")
        
        let fallbackSequence = [
            GeneratorSymbolSwipeData.GeneratorSymbolItem(symbol: leftSymbol, id: 1, expectedDirection: "left", sequenceIndex: 0),
            GeneratorSymbolSwipeData.GeneratorSymbolItem(symbol: rightSymbol, id: 2, expectedDirection: "right", sequenceIndex: 1),
            GeneratorSymbolSwipeData.GeneratorSymbolItem(symbol: leftSymbol, id: 3, expectedDirection: "left", sequenceIndex: 2),
            GeneratorSymbolSwipeData.GeneratorSymbolItem(symbol: rightSymbol, id: 4, expectedDirection: "right", sequenceIndex: 3),
            GeneratorSymbolSwipeData.GeneratorSymbolItem(symbol: leftSymbol, id: 5, expectedDirection: "left", sequenceIndex: 4)
        ]
        
        let questionData = GeneratorSymbolSwipeData(
            symbols: GeneratorSymbolSwipeData.GeneratorSymbolConfiguration(leftSymbol: leftSymbol, rightSymbol: rightSymbol),
            sequence: fallbackSequence,
            totalSymbols: 5,
            difficulty: "easy",
            timeLimit: 15.0,
            scoring: GeneratorSymbolSwipeData.GeneratorScoringConfiguration(pointsPerCorrect: 10, timeBonus: true, streakMultiplier: ["3": 2, "5": 3], maxStreak: 5),
            instructions: "When you see the LEFT symbol, swipe LEFT. When you see the RIGHT symbol, swipe RIGHT. Be quick and accurate!",
            metadata: GeneratorSymbolSwipeData.GeneratorSymbolSwipeMetadata(generatedAt: ISO8601DateFormatter().string(from: Date()), difficulty: "easy", symbolCount: 5, timePerSymbol: 3.0, expectedSwipePattern: ["left", "right", "left", "right", "left"])
        )
        
        let answerData = GeneratorSymbolSwipeAnswer(
            correctSequence: ["left", "right", "left", "right", "left"],
            symbolMapping: ["Circle": "left", "Square": "right"],
            scoring: questionData.scoring,
            maxScore: 150,
            perfectTimeScore: 25
        )
        
        do {
            let questionJSON = try JSONEncoder().encode(questionData)
            let answerJSON = try JSONEncoder().encode(answerData)
            
            let questionString = String(data: questionJSON, encoding: .utf8) ?? "{}"
            let answerString = String(data: answerJSON, encoding: .utf8) ?? "{}"
            
            return (questionString, answerString)
        } catch {
            print("âŒ Failed to encode fallback puzzle: \(error)")
            return ("{}", "{}")
        }
    }
}

// MARK: - Supporting Configuration Structure
private struct SymbolSwipeConfig {
    let symbolCount: Int
    let timePerSymbol: Double
    let pointsPerCorrect: Int
    let streakMultipliers: [Int: Int]
    let maxStreak: Int
}

// MARK: - Dictionary Extension for Key Mapping
private extension Dictionary {
    func mapKeys<T>(_ transform: (Key) throws -> T) rethrows -> [T: Value] {
        var result: [T: Value] = [:]
        for (key, value) in self {
            result[try transform(key)] = value
        }
        return result
    }
}
