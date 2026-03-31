import SwiftUI
import Foundation

// MARK: - Data Models
struct NumberSumConfig {
    let targetSum: Int
    let correctNumbers: [Int]
    let distractorNumbers: [Int]
    let timeLimit: Int
    let name: String
}

struct NumberTile: Identifiable {
    let id: Int
    let number: Int
    let isCorrect: Bool
    var isSelected: Bool = false
    var showFeedback: Bool = false
    var feedbackType: FeedbackType = .none
}

enum FeedbackType {
    case none
    case correct
    case wrong
}

struct NumberSumPuzzleData: Codable {
    let targetSum: Int
    let correctNumbers: [Int]
    let allNumbers: [Int]
    let difficulty: String
    let timeLimit: Int
    let totalRounds: Int
    let scoring: ScoringConfig
    
    struct ScoringConfig: Codable {
        let pointsPerCorrect: Int
        let timeBonus: Int
        let roundBonus: Int
    }
}

// MARK: - Main View
//
//  AdaptiveNumberSumPuzzleView.swift
//  PuzzleForge
//
//  Created by Assistant on 8/17/25.
//

import SwiftUI
import Foundation
import Combine

// Import the existing DifficultyManager
// (Use the existing DifficultyManager.swift)

// MARK: - Adaptive Number Tile with Enhanced Features
struct AdaptiveNumberTile: Identifiable {
    let id: Int
    let number: Int
    let isCorrect: Bool
    var isSelected: Bool = false
    var showFeedback: Bool = false
    var feedbackType: AdaptiveFeedbackType = .none
    var animationScale: CGFloat = 1.0
}

enum AdaptiveFeedbackType {
    case none
    case correct
    case wrong
    case hint
}

// MARK: - Adaptive Sum Configuration
struct AdaptiveNumberSumConfig {
    let targetSum: Int
    let correctNumbers: [Int]
    let distractorNumbers: [Int]
    let name: String
    let timeLimit: Int
    let complexity: Int
}

// MARK: - Enhanced Sum Puzzle Data for Generation
struct AdaptiveNumberSumPuzzleData: Codable {
    let targetSum: Int
    let correctNumbers: [Int]
    let allNumbers: [Int]
    let difficulty: String
    let timeLimit: Int
    let totalRounds: Int
    let scoring: ScoringConfig
    
    struct ScoringConfig: Codable {
        let pointsPerCorrect: Int
        let timeBonus: Int
        let roundBonus: Int
    }
}

// MARK: - Main Adaptive Number Sum Puzzle View
struct NumberSumPuzzleView: View {
    let puzzle: Puzzle
    let questionIndex: Int
    let totalQuestions: Int
    let onAnswerSubmitted: (Bool) -> Void
    let onNextPuzzle: () -> Void
    let onExit: () -> Void
    
    // Adaptive difficulty management using existing DifficultyManager
    @StateObject private var difficultyManager = DifficultyManager.shared
    @State private var adaptationInfo: DifficultyManager.AdaptiveConfig?
    @State private var showAdaptationNotification = false
    
    // Current difficulty level
    private var currentDifficulty: DifficultyManager.DifficultyLevel {
        difficultyManager.getCurrentDifficulty(for: "numberSum")
    }
    
    // Competitive features (placeholder)
    @State private var competitiveInsight: CompetitiveInsight?
    
    // Game state
    @State private var timeLeft = 120
    @State private var isPaused = false
    @State private var currentHearts = 3
    @State private var gameCompleted = false
    @State private var gameStarted = false
    @State private var currentScore = 0
    @State private var totalScore = 0
    @State private var currentRound = 1
    @State private var showCompletionMessage = false
    @State private var completionMessage = ""
    @State private var currentStreak = 0
    @State private var gamesPlayedThisSession = 0
    
    // Session tracking
    @State private var sessionStartTime = Date()
    @State private var correctAttempts = 0
    @State private var totalAttempts = 0
    
    // Puzzle state
    @State private var currentConfig: AdaptiveNumberSumConfig?
    @State private var numberTiles: [AdaptiveNumberTile] = []
    @State private var selectedTileIds: [Int] = []
    @State private var currentSum = 0
    @State private var showWrongFeedback = false
    @State private var puzzleCompleted = false
    @State private var roundStartTime = Date()
    
    // Feedback system
    @StateObject private var feedbackManager = UnifiedFeedbackManager()
    @State private var showHint = false
    @State private var showInstructions = true
    
    private let columns = Array(repeating: GridItem(.flexible(), spacing: 16), count: 3)
    
    var body: some View {
        ZStack {
            Color(red: 0.11, green: 0.37, blue: 0.13) // Dark green background
                .ignoresSafeArea()
            
            VStack(spacing: 0) {
                // Adaptive header
                adaptiveHeaderView
                
                // Adaptation notification
                if showAdaptationNotification {
                    adaptationNotificationView
                }
                
                if showInstructions {
                    instructionsView
                } else if gameStarted && !gameCompleted {
                    gameAreaView
                } else if gameCompleted {
                    completionView
                }
                
                if showCompletionMessage {
                    completionMessageOverlay
                }
            }
        }
        .navigationBarBackButtonHidden(true)
        .onAppear {
            initializeAdaptivePuzzle()
        }
        .onReceive(Timer.publish(every: 1, on: .main, in: .common).autoconnect()) { _ in
            if gameStarted && !isPaused && !gameCompleted && timeLeft > 0 {
                timeLeft -= 1
                if timeLeft == 0 {
                    handleGameOver(success: false)
                }
            }
        }
    }
    
    // MARK: - Adaptive Header View
    private var adaptiveHeaderView: some View {
        VStack(spacing: 12) {
            HStack {
                Button(action: onExit) {
                    HStack(spacing: 8) {
                        Image(systemName: "arrow.left")
                            .font(.title2)
                            .foregroundColor(.white)
                        Text("Back")
                            .font(.headline)
                            .foregroundColor(.white)
                    }
                }
                
                Spacer()
                
                if gameStarted {
                    HStack(spacing: 16) {
                        Button(action: { isPaused.toggle() }) {
                            Image(systemName: isPaused ? "play.fill" : "pause.fill")
                                .font(.title2)
                                .foregroundColor(.white)
                                .frame(width: 44, height: 44)
                                .background(Color.white.opacity(0.2))
                                .cornerRadius(22)
                        }
                        
                        Button(action: { showHint.toggle() }) {
                            Image(systemName: "lightbulb.fill")
                                .font(.title2)
                                .foregroundColor(.yellow)
                                .frame(width: 44, height: 44)
                                .background(Color.white.opacity(0.2))
                                .cornerRadius(22)
                        }
                    }
                }
            }
            
            // Game info with adaptive difficulty display
            HStack {
                // Timer
                VStack(alignment: .leading, spacing: 4) {
                    Text("TIME")
                        .font(.caption)
                        .foregroundColor(.white.opacity(0.7))
                    Text(formatTime(timeLeft))
                        .font(.title2)
                        .fontWeight(.bold)
                        .foregroundColor(timeLeft <= 30 ? .red : .white)
                }
                
                Spacer()
                
                // Adaptive difficulty and level info
                VStack(alignment: .center, spacing: 4) {
                    Text("Level \(feedbackManager.currentLevel.level) • \(currentDifficulty.name)")
                        .font(.caption)
                        .foregroundColor(.cyan)
                        .fontWeight(.medium)
                    Text("Score: \(totalScore)")
                        .font(.headline)
                        .fontWeight(.bold)
                        .foregroundColor(.white)
                    
                    if currentStreak > 0 {
                        Text("🔥 Streak: \(currentStreak)")
                            .font(.caption)
                            .foregroundColor(.orange)
                            .fontWeight(.bold)
                    }
                }
                
                Spacer()
                
                // Hearts and challenge info
                VStack(alignment: .trailing, spacing: 4) {
                    Text("Round \(currentRound)")
                        .font(.caption)
                        .foregroundColor(.white.opacity(0.7))
                    HStack(spacing: 2) {
                        ForEach(0..<currentHearts, id: \.self) { _ in
                            Text("❤️")
                                .font(.caption)
                        }
                    }
                }
            }
        }
        .padding()
        .background(
            RoundedRectangle(cornerRadius: 16)
                .fill(Color.black.opacity(0.3))
                .overlay(
                    RoundedRectangle(cornerRadius: 16)
                        .stroke(Color.white.opacity(0.2), lineWidth: 1)
                )
        )
        .padding(.horizontal)
        .padding(.top)
    }
    
    // MARK: - Adaptation Notification View
    private var adaptationNotificationView: some View {
        VStack {
            if let adaptation = adaptationInfo {
                HStack {
                    Image(systemName: "chart.line.uptrend.xyaxis")
                        .foregroundColor(.white)
                        .font(.title2)
                    
                    VStack(alignment: .leading, spacing: 4) {
                        Text("Math Challenge Adapted!")
                            .font(.headline)
                            .fontWeight(.bold)
                            .foregroundColor(.white)
                        
                        Text(adaptationInfo?.recommendedAdjustment ?? "")
                            .font(.caption)
                            .foregroundColor(.white.opacity(0.9))
                    }
                    
                    Spacer()
                    
                    Button(action: { showAdaptationNotification = false }) {
                        Image(systemName: "xmark")
                            .foregroundColor(.white)
                            .font(.caption)
                    }
                }
                .padding()
                .background(
                    RoundedRectangle(cornerRadius: 12)
                        .fill(Color.blue.opacity(0.8))
                )
                .padding(.horizontal)
            }
        }
        .transition(.move(edge: .top).combined(with: .opacity))
        .animation(.spring(), value: showAdaptationNotification)
    }
    
    // MARK: - Instructions View
    private var instructionsView: some View {
        VStack(spacing: 24) {
            VStack(spacing: 16) {
                Text("🧮")
                    .font(.system(size: 60))
                
                Text("Adaptive Number Sum")
                    .font(.title)
                    .fontWeight(.bold)
                    .foregroundColor(.white)
                
                Text("Select numbers that add up\nto the target sum")
                    .font(.headline)
                    .foregroundColor(.white.opacity(0.9))
                    .multilineTextAlignment(.center)
                    .lineSpacing(4)
                
                if let config = currentConfig {
                    VStack(spacing: 8) {
                        Text("\(config.name) Difficulty")
                            .font(.subheadline)
                            .fontWeight(.bold)
                            .foregroundColor(.cyan)
                        
                        Text("Target: \(config.targetSum) • Select \(config.correctNumbers.count) numbers")
                            .font(.caption)
                            .foregroundColor(.white.opacity(0.7))
                            .multilineTextAlignment(.center)
                        
                        Text(currentDifficulty.description)
                            .font(.caption)
                            .foregroundColor(.white.opacity(0.6))
                            .multilineTextAlignment(.center)
                    }
                }
            }
            .padding(32)
            .background(
                RoundedRectangle(cornerRadius: 20)
                    .fill(Color.white.opacity(0.15))
                    .overlay(
                        RoundedRectangle(cornerRadius: 20)
                            .stroke(Color.white.opacity(0.3), lineWidth: 1)
                    )
            )
            
            Button(action: startAdaptiveGame) {
                Text("START ADAPTIVE CHALLENGE")
                    .font(.title3)
                    .fontWeight(.bold)
                    .foregroundColor(.white)
                    .frame(maxWidth: .infinity)
                    .frame(height: 56)
                    .background(
                        LinearGradient(
                            colors: [Color.green, Color.blue],
                            startPoint: .leading,
                            endPoint: .trailing
                        )
                    )
                    .cornerRadius(28)
                    .shadow(color: .black.opacity(0.3), radius: 10, x: 0, y: 5)
            }
            .padding(.horizontal, 32)
        }
        .padding(.top, 40)
    }
    
    // MARK: - Game Area View
    private var gameAreaView: some View {
        VStack(spacing: 16) {
            Spacer(minLength: 16)
            
            // Target Sum Display with adaptive styling
            targetSumDisplay
            
            // Adaptive hint display
            if showHint {
                hintDisplay
            }
            
            Spacer(minLength: 24)
            
            // Number Grid
            LazyVGrid(columns: columns, spacing: 16) {
                ForEach(numberTiles) { tile in
                    AdaptiveNumberTileView(
                        tile: tile,
                        difficulty: difficultyManager.getCurrentDifficulty(for: "numbersum"),
                        onClick: {
                            handleAdaptiveNumberClick(tileId: tile.id, number: tile.number, isCorrect: tile.isCorrect)
                        }
                    )
                }
            }
            .padding(.horizontal, 24)
            
            Spacer()
        }
    }
    
    // MARK: - Target Sum Display
    private var targetSumDisplay: some View {
        VStack(spacing: 8) {
            Text("Target")
                .font(.system(size: 16, weight: .medium))
                .foregroundColor(.white)
            
            Text("\(currentConfig?.targetSum ?? 0)")
                .font(.system(size: 48, weight: .bold))
                .foregroundColor(Color(red: 0.3, green: 0.69, blue: 0.31))
            
            HStack(spacing: 16) {
                if currentSum > 0 {
                    Text("Current: \(currentSum)")
                        .font(.system(size: 14))
                        .foregroundColor(.white.opacity(0.8))
                }
                
                if let config = currentConfig {
                    Text("\(config.name) • \(config.correctNumbers.count) numbers")
                        .font(.system(size: 12))
                        .foregroundColor(.cyan)
                        .fontWeight(.medium)
                }
            }
        }
        .padding(24)
        .background(Color(red: 0.18, green: 0.49, blue: 0.20))
        .cornerRadius(16)
        .padding(.horizontal, 32)
    }
    
    // MARK: - Hint Display
    private var hintDisplay: some View {
        VStack(spacing: 8) {
            Text("💡 Adaptive Hint")
                .font(.headline)
                .fontWeight(.bold)
                .foregroundColor(.yellow)
            
            if let config = currentConfig {
                Text("Look for combinations that sum to \(config.targetSum)")
                    .font(.subheadline)
                    .foregroundColor(.white.opacity(0.9))
                    .multilineTextAlignment(.center)
                
                Text("Need \(config.correctNumbers.count) numbers")
                    .font(.caption)
                    .foregroundColor(.white.opacity(0.7))
            }
        }
        .padding()
        .background(
            RoundedRectangle(cornerRadius: 12)
                .fill(Color.black.opacity(0.7))
        )
        .padding(.horizontal, 32)
        .transition(.opacity.combined(with: .move(edge: .top)))
    }
    
    // MARK: - Completion View
    private var completionView: some View {
        VStack(spacing: 24) {
            Text("🎉")
                .font(.system(size: 80))
            
            Text("Adaptive Challenge Complete!")
                .font(.title)
                .fontWeight(.bold)
                .foregroundColor(.white)
            
            VStack(spacing: 8) {
                Text("Final Score: \(totalScore)")
                    .font(.title2)
                    .foregroundColor(.white.opacity(0.9))
                
                Text("Correct: \(correctAttempts)/\(totalAttempts)")
                    .font(.headline)
                    .foregroundColor(.white.opacity(0.8))
                
                if currentStreak > 0 {
                    Text("Best Streak: \(currentStreak)")
                        .font(.headline)
                        .foregroundColor(.orange)
                }
                
                Text("Difficulty: \(currentDifficulty.name)")
                    .font(.subheadline)
                    .foregroundColor(.cyan)
            }
            
            Button(action: onNextPuzzle) {
                Text("CONTINUE")
                    .font(.title3)
                    .fontWeight(.bold)
                    .foregroundColor(.white)
                    .frame(maxWidth: .infinity)
                    .frame(height: 56)
                    .background(Color.green)
                    .cornerRadius(28)
            }
            .padding(.horizontal, 32)
        }
    }
    
    // MARK: - Completion Message Overlay
    private var completionMessageOverlay: some View {
        VStack {
            Spacer()
            
            Text(completionMessage)
                .font(.headline)
                .fontWeight(.bold)
                .foregroundColor(.white)
                .padding()
                .background(Color(red: 0.3, green: 0.69, blue: 0.31).opacity(0.9))
                .cornerRadius(12)
                .padding(.horizontal)
            
            Spacer()
        }
        .transition(.move(edge: .bottom).combined(with: .opacity))
        .animation(.spring(), value: showCompletionMessage)
    }
    
    // MARK: - Helper Methods
    
    private func initializeAdaptivePuzzle() {
        timeLeft = currentDifficulty.timeLimit
        currentHearts = currentDifficulty.livesAllowed
        
        generateAdaptiveConfiguration()
        
        // Load competitive insight (placeholder)
        competitiveInsight = CompetitiveInsight(
            percentile: 85,
            ranking: "Gold",
            improvement: "Top 15% this week!",
            globalAverage: 76.8,
            userScore: 84.2
        )
    }
    
    private func startAdaptiveGame() {
        withAnimation {
            showInstructions = false
            gameStarted = true
        }
        generateAdaptivePuzzle()
        sessionStartTime = Date()
    }
    
    private func generateAdaptiveConfiguration() {
        let (correctNumbers, targetSum) = generateAdaptiveNumbers(difficulty: currentDifficulty)
        let distractors = generateAdaptiveDistractors(targetSum: targetSum, difficulty: currentDifficulty, excludeNumbers: correctNumbers)
        
        currentConfig = AdaptiveNumberSumConfig(
            targetSum: targetSum,
            correctNumbers: correctNumbers,
            distractorNumbers: distractors,
            name: currentDifficulty.name,
            timeLimit: currentDifficulty.timeLimit,
            complexity: correctNumbers.count
        )
    }
    
    private func generateAdaptiveNumbers(difficulty: DifficultyManager.DifficultyLevel) -> ([Int], Int) {
        let (count, minRange, maxRange) = getAdaptiveParams(difficulty)
        
        var numbers: [Int] = []
        for _ in 0..<count {
            let number = Int.random(in: minRange...maxRange)
            numbers.append(number)
        }
        
        // Ensure uniqueness
        numbers = makeUnique(numbers: numbers, range: minRange...maxRange)
        let targetSum = numbers.reduce(0, +)
        
        return (numbers.sorted(), targetSum)
    }
    
    private func getAdaptiveParams(_ difficulty: DifficultyManager.DifficultyLevel) -> (count: Int, minRange: Int, maxRange: Int) {
        switch difficulty {
        case .beginner:
            return (2, 1, 15)
        case .easy:
            return (2, 2, 18)
        case .medium:
            return (3, 3, 22)
        case .hard:
            return (3, 4, 25)
        case .expert:
            return (4, 5, 30)
        }
    }
    
    private func makeUnique(numbers: [Int], range: ClosedRange<Int>) -> [Int] {
        var uniqueNumbers: [Int] = []
        var used = Set<Int>()
        
        for number in numbers {
            var candidate = number
            var attempts = 0
            
            while used.contains(candidate) && attempts < 20 {
                if attempts % 2 == 0 {
                    candidate = min(number + (attempts / 2) + 1, range.upperBound)
                } else {
                    candidate = max(number - (attempts / 2) - 1, range.lowerBound)
                }
                attempts += 1
            }
            
            uniqueNumbers.append(candidate)
            used.insert(candidate)
        }
        
        return uniqueNumbers
    }
    
    private func generateAdaptiveDistractors(targetSum: Int, difficulty: DifficultyManager.DifficultyLevel, excludeNumbers: [Int]) -> [Int] {
        let distractorCount = (difficulty == .expert || difficulty == .hard) ? 4 : 5
        var distractors: [Int] = []
        let excludeSet = Set(excludeNumbers)
        let (_, minRange, maxRange) = getAdaptiveParams(difficulty)
        
        // Strategy 1: Near-miss numbers
        let nearMissTargets = [
            targetSum - 1, targetSum + 1,
            targetSum - 2, targetSum + 2,
            targetSum / 2,
            min(targetSum * 2, maxRange * 2)
        ].filter { $0 > 0 && $0 <= maxRange * 2 }
        
        for target in nearMissTargets.prefix(2) {
            if distractors.count >= distractorCount { break }
            if !excludeSet.contains(target) && !distractors.contains(target) {
                distractors.append(target)
            }
        }
        
        // Strategy 2: Random numbers from expanded ranges
        while distractors.count < distractorCount {
            let candidate = Int.random(in: (maxRange + 5)...(maxRange + 20))
            if !excludeSet.contains(candidate) && !distractors.contains(candidate) {
                distractors.append(candidate)
            }
        }
        
        return Array(distractors.prefix(distractorCount)).shuffled()
    }
    
    private func generateAdaptivePuzzle() {
        guard let config = currentConfig else { return }
        
        let allNumbers = (config.correctNumbers + config.distractorNumbers).shuffled()
        numberTiles = allNumbers.enumerated().map { index, number in
            AdaptiveNumberTile(
                id: index,
                number: number,
                isCorrect: config.correctNumbers.contains(number)
            )
        }
        
        selectedTileIds = []
        currentSum = 0
        showWrongFeedback = false
        puzzleCompleted = false
        roundStartTime = Date()
        
        print("🧮 New adaptive puzzle generated:")
        print("Target: \(config.targetSum)")
        print("Correct numbers: \(config.correctNumbers)")
        print("Distractors: \(config.distractorNumbers)")
        print("Difficulty: \(currentDifficulty.name)")
    }
    
    private func handleAdaptiveNumberClick(tileId: Int, number: Int, isCorrect: Bool) {
        guard !isPaused && !gameCompleted else { return }
        
        if selectedTileIds.contains(tileId) {
            // Deselect tile
            selectedTileIds.removeAll { $0 == tileId }
            currentSum -= number
            
            // Update tile state
            if let index = numberTiles.firstIndex(where: { $0.id == tileId }) {
                numberTiles[index].isSelected = false
                numberTiles[index].showFeedback = false
                numberTiles[index].feedbackType = .none
            }
        } else {
            // Select tile
            selectedTileIds.append(tileId)
            currentSum += number
            
            // Update tile state
            if let index = numberTiles.firstIndex(where: { $0.id == tileId }) {
                numberTiles[index].isSelected = true
            }
            
            // Check if we have selected the required number of tiles
            let requiredCount = currentConfig?.correctNumbers.count ?? 2
            
            if selectedTileIds.count == requiredCount {
                // Check solution
                checkAdaptiveSolutionAndProvideFeedback()
            } else if selectedTileIds.count > requiredCount {
                // Too many selected - automatically deselect the last one
                selectedTileIds.removeLast()
                currentSum -= number
                
                if let index = numberTiles.firstIndex(where: { $0.id == tileId }) {
                    numberTiles[index].isSelected = false
                }
            }
        }
    }
    
    private func checkAdaptiveSolutionAndProvideFeedback() {
        guard let config = currentConfig else { return }
        
        let selectedNumbers = selectedTileIds.compactMap { id in
            numberTiles.first { $0.id == id }?.number
        }
        
        let selectedSum = selectedNumbers.reduce(0, +)
        let requiredCount = config.correctNumbers.count
        
        totalAttempts += 1
        let timeSpent = Date().timeIntervalSince(roundStartTime)
        
        if selectedSum == config.targetSum && selectedNumbers.count == requiredCount {
            // CORRECT SOLUTION!
            correctAttempts += 1
            
            // Mark all selected tiles as correct
            for tileId in selectedTileIds {
                if let index = numberTiles.firstIndex(where: { $0.id == tileId }) {
                    numberTiles[index].showFeedback = true
                    numberTiles[index].feedbackType = .correct
                }
            }
            
            // Calculate adaptive score
            let score = calculateAdaptiveScore(isCorrect: true, timeSpent: timeSpent)
            currentScore += score
            totalScore += score
            currentStreak += 1
            gamesPlayedThisSession += 1
            
            // Record performance
            recordAdaptivePerformance(isCorrect: true, timeSpent: timeSpent)
            
            // Complete the puzzle
            DispatchQueue.main.asyncAfter(deadline: .now() + 0.5) {
                self.completeAdaptivePuzzle()
            }
            
        } else {
            // WRONG SOLUTION!
            // Mark all selected tiles as wrong and lose a heart
            for tileId in selectedTileIds {
                if let index = numberTiles.firstIndex(where: { $0.id == tileId }) {
                    numberTiles[index].showFeedback = true
                    numberTiles[index].feedbackType = .wrong
                }
            }
            
            // Lose a heart and reset streak
            currentHearts = max(0, currentHearts - 1)
            currentStreak = 0
            
            // Record poor performance
            recordAdaptivePerformance(isCorrect: false, timeSpent: timeSpent)
            
            // Clear wrong feedback and deselect after delay
            DispatchQueue.main.asyncAfter(deadline: .now() + 1.5) {
                self.clearWrongSelection()
            }
            
            // Check for game over
            if currentHearts == 0 {
                DispatchQueue.main.asyncAfter(deadline: .now() + 1.5) {
                    self.handleGameOver(success: false)
                }
            }
        }
    }
    
    private func clearWrongSelection() {
        // Clear all selections and feedback
        for tileId in selectedTileIds {
            if let index = numberTiles.firstIndex(where: { $0.id == tileId }) {
                numberTiles[index].isSelected = false
                numberTiles[index].showFeedback = false
                numberTiles[index].feedbackType = .none
            }
        }
        
        selectedTileIds.removeAll()
        currentSum = 0
    }
    
    private func completeAdaptivePuzzle() {
        let bonusPoints = timeLeft * 5 + (selectedTileIds.count * 10)
        totalScore += bonusPoints
        
        completionMessage = "🎉 Perfect! +\(bonusPoints) points"
        showCompletionMessage = true
        
        // Hide completion message and move to next round
        DispatchQueue.main.asyncAfter(deadline: .now() + 2.0) {
            self.showCompletionMessage = false
            self.currentRound += 1
            self.timeLeft += 30 // Bonus time
            
            // Complete the puzzle
            self.handleGameOver(success: true)
        }
    }
    
    private func recordAdaptivePerformance(isCorrect: Bool, timeSpent: TimeInterval) {
        let adaptation = difficultyManager.recordPerformance(
            puzzleType: "numberSum",
            isCorrect: isCorrect,
            timeSpent: timeSpent,
            difficulty: currentDifficulty
        )
        
        if let adaptation = adaptation, adaptation.confidenceScore > 0.5 {
            adaptationInfo = adaptation
            showAdaptationNotification = true
            
            // Update time and hearts for new difficulty
            timeLeft = currentDifficulty.timeLimit
            currentHearts = currentDifficulty.livesAllowed
            
            // Regenerate puzzle with new difficulty
            generateAdaptiveConfiguration()
            generateAdaptivePuzzle()
            
            // Hide notification after delay
            DispatchQueue.main.asyncAfter(deadline: .now() + 3.0) {
                showAdaptationNotification = false
            }
        }
    }
    
    private func calculateAdaptiveScore(isCorrect: Bool, timeSpent: TimeInterval) -> Int {
        guard isCorrect else { return 0 }
        
        let basePoints = 100 // Base points for number sum
        
        // Complexity multiplier based on number count
        let complexityMultiplier = 1.0 + Float(currentConfig?.correctNumbers.count ?? 2) * 0.2
        
        // Time bonus (faster = better)
        let timeBonus = max(0, Int((15.0 - timeSpent) * 5))
        
        // Streak bonus
        let streakBonus = currentStreak * 15
        
        // Difficulty multiplier
        let difficultyMultiplier: Float = {
            switch currentDifficulty {
            case .beginner: return 0.8
            case .easy: return 1.0
            case .medium: return 1.2
            case .hard: return 1.5
            case .expert: return 2.0
            }
        }()
        
        let finalScore = Int(Float(basePoints + timeBonus + streakBonus) * difficultyMultiplier * complexityMultiplier)
        
        return max(finalScore, basePoints / 4)
    }
    
    private func handleGameOver(success: Bool) {
        gameCompleted = true
        
        // Create session statistics
        let sessionStats = SessionStatistics(
            correctAnswers: correctAttempts,
            totalAnswers: totalAttempts,
            totalTimeSeconds: Int(Date().timeIntervalSince(sessionStartTime)),
            bestStreak: currentStreak,
            currentStreak: currentStreak,
            totalScore: totalScore,
            individualTimes: [],
            puzzleType: "numbersum"
        )
        
        onAnswerSubmitted(success)
        
        DispatchQueue.main.asyncAfter(deadline: .now() + 1.0) {
            if success {
                onNextPuzzle()
            } else {
                onExit()
            }
        }
    }
    
    private func formatTime(_ seconds: Int) -> String {
        let minutes = seconds / 60
        let remainingSeconds = seconds % 60
        return String(format: "%02d:%02d", minutes, remainingSeconds)
    }
}

// MARK: - Adaptive Number Tile Component
struct AdaptiveNumberTileView: View {
    let tile: AdaptiveNumberTile
    let difficulty: DifficultyManager.DifficultyLevel
    let onClick: () -> Void
    
    @State private var scale: CGFloat = 1.0
    
    // Adaptive sizing based on difficulty
    private var tileSize: CGFloat {
        switch difficulty {
        case .beginner: return 85  // Beginner - larger
        case .easy: return 82      // Easy
        case .medium: return 80    // Medium
        case .hard: return 78      // Hard
        case .expert: return 75    // Expert - smaller
        }
    }
    
    private var fontSize: CGFloat {
        switch difficulty {
        case .beginner: return 30  // Beginner
        case .easy: return 28      // Easy
        case .medium: return 26    // Medium
        case .hard: return 24      // Hard
        case .expert: return 22    // Expert
        }
    }
    
    var backgroundColor: Color {
        switch tile.feedbackType {
        case .wrong:
            return .red
        case .correct:
            return Color(red: 0.3, green: 0.69, blue: 0.31)
        case .hint:
            return .yellow.opacity(0.3)
        case .none:
            return tile.isSelected ? Color(red: 0.3, green: 0.69, blue: 0.31) : .white
        }
    }
    
    var textColor: Color {
        switch tile.feedbackType {
        case .wrong:
            return .white
        case .correct:
            return .white
        case .hint:
            return .black
        case .none:
            return tile.isSelected ? .white : .black
        }
    }
    
    var body: some View {
        Button(action: {
            withAnimation(.easeInOut(duration: 0.1)) {
                scale = 0.95
            }
            
            DispatchQueue.main.asyncAfter(deadline: .now() + 0.1) {
                withAnimation(.easeInOut(duration: 0.1)) {
                    scale = 1.0
                }
            }
            
            onClick()
        }) {
            ZStack {
                RoundedRectangle(cornerRadius: 12)
                    .fill(backgroundColor)
                    .frame(width: tileSize, height: tileSize)
                
                if tile.showFeedback && tile.feedbackType == .wrong {
                    Image(systemName: "xmark")
                        .font(.system(size: fontSize, weight: .bold))
                        .foregroundColor(.white)
                } else if tile.showFeedback && tile.feedbackType == .correct {
                    Image(systemName: "checkmark")
                        .font(.system(size: fontSize, weight: .bold))
                        .foregroundColor(.white)
                } else {
                    Text("\(tile.number)")
                        .font(.system(size: fontSize, weight: .bold))
                        .foregroundColor(textColor)
                }
            }
        }
        .scaleEffect(scale)
        .buttonStyle(PlainButtonStyle())
    }
}

extension LocalMemoryPuzzleGenerator {
    
    static func generateNumberSum(difficulty: String) -> (String, String) {
        print("ðŸ§® Generating number sum puzzle with difficulty: \(difficulty)")
        
        do {
            let config = getNumberSumDifficultyConfig(for: difficulty)
            let (correctNumbers, targetSum) = generateVariedNumbers(difficulty: difficulty)
            let distractors = generateVariedDistractors(
                targetSum: targetSum,
                difficulty: difficulty,
                excludeNumbers: correctNumbers
            )
            let allNumbers = (correctNumbers + distractors).shuffled()
            
            let questionData = NumberSumPuzzleData(
                targetSum: targetSum,
                correctNumbers: correctNumbers, // Still store one valid solution for reference
                allNumbers: allNumbers,
                difficulty: difficulty,
                timeLimit: config.timeLimit,
                totalRounds: config.totalRounds,
                scoring: NumberSumPuzzleData.ScoringConfig(
                    pointsPerCorrect: config.pointsPerCorrect,
                    timeBonus: config.timeBonus,
                    roundBonus: config.roundBonus
                )
            )
            
            let answerData = NumberSumAnswer(
                targetSum: targetSum,
                correctNumbers: correctNumbers,
                allNumbers: allNumbers,
                maxScore: calculateMaxScore(correctNumbers: correctNumbers, config: config)
            )
            
            let questionJSON = try JSONEncoder().encode(questionData)
            let answerJSON = try JSONEncoder().encode(answerData)
            
            guard let questionString = String(data: questionJSON, encoding: .utf8),
                  let answerString = String(data: answerJSON, encoding: .utf8) else {
                throw NSError(domain: "JSONError", code: 1, userInfo: [NSLocalizedDescriptionKey: "Failed to encode JSON"])
            }
            
            print("âœ… Successfully generated number sum puzzle")
            print("ðŸŽ¯ Target sum: \(targetSum)")
            print("ðŸ“Š Reference solution: \(correctNumbers)")
            print("ðŸŽ² All numbers: \(allNumbers)")
            
            return (questionString, answerString)
            
        } catch {
            print("âŒ Failed to generate number sum puzzle: \(error)")
            return generateFallbackNumberSum()
        }
    }
    
    // MARK: - Simplified Number Generation (No Uniqueness Required)
    
    private static func generateVariedNumbers(difficulty: String) -> ([Int], Int) {
        let (count, minRange, maxRange) = getExpandedDifficultyParams(difficulty)
        
        // Rotate between different generation strategies for variety
        let strategy = Int.random(in: 0...3)
        
        switch strategy {
        case 0:
            return generateRandomNumbers(count: count, minRange: minRange, maxRange: maxRange)
        case 1:
            return generateWeightedNumbers(count: count, minRange: minRange, maxRange: maxRange)
        case 2:
            return generateMixedRangeNumbers(count: count, difficulty: difficulty)
        default:
            return generatePatternNumbers(count: count, minRange: minRange, maxRange: maxRange)
        }
    }
    
    private static func generateRandomNumbers(count: Int, minRange: Int, maxRange: Int) -> ([Int], Int) {
        var numbers: [Int] = []
        
        for _ in 0..<count {
            let number = Int.random(in: minRange...maxRange)
            numbers.append(number)
        }
        
        // Ensure no duplicates by adjusting duplicates slightly
        numbers = makeUnique(numbers: numbers, range: minRange...maxRange)
        
        let targetSum = numbers.reduce(0, +)
        return (numbers.sorted(), targetSum)
    }
    
    private static func generateWeightedNumbers(count: Int, minRange: Int, maxRange: Int) -> ([Int], Int) {
        let range = maxRange - minRange + 1
        let third = range / 3
        
        let smallRange = minRange...(minRange + third)
        let mediumRange = (minRange + third)...(maxRange - third)
        let largeRange = (maxRange - third)...maxRange
        
        var numbers: [Int] = []
        
        for _ in 0..<count {
            let rangeChoice = Int.random(in: 0...2)
            let selectedRange: ClosedRange<Int>
            
            switch rangeChoice {
            case 0: selectedRange = smallRange
            case 1: selectedRange = mediumRange
            default: selectedRange = largeRange
            }
            
            numbers.append(Int.random(in: selectedRange))
        }
        
        numbers = makeUnique(numbers: numbers, range: minRange...maxRange)
        let targetSum = numbers.reduce(0, +)
        return (numbers.sorted(), targetSum)
    }
    
    private static func generateMixedRangeNumbers(count: Int, difficulty: String) -> ([Int], Int) {
        let ranges = getMixedRanges(difficulty: difficulty)
        var numbers: [Int] = []
        
        for i in 0..<count {
            let rangeIndex = i % ranges.count
            let selectedRange = ranges[rangeIndex]
            numbers.append(Int.random(in: selectedRange))
        }
        
        numbers = makeUnique(numbers: numbers, range: 1...50) // Broader range for uniqueness
        let targetSum = numbers.reduce(0, +)
        return (numbers.sorted(), targetSum)
    }
    
    private static func generatePatternNumbers(count: Int, minRange: Int, maxRange: Int) -> ([Int], Int) {
        let patterns = [
            // Arithmetic progression with random start and gap
            {
                let start = Int.random(in: minRange...(minRange + 3))
                let gap = Int.random(in: 2...4)
                var sequence: [Int] = []
                var current = start
                for _ in 0..<count {
                    sequence.append(min(current, maxRange))
                    current += gap
                }
                return sequence
            },
            // Random multiples of small numbers
            {
                let base = Int.random(in: 2...4)
                var multiples: [Int] = []
                for i in 1...count {
                    let multiple = base * i + Int.random(in: 0...2)
                    multiples.append(min(multiple, maxRange))
                }
                return multiples
            },
            // Mixed small and large numbers
            {
                var mixed: [Int] = []
                for i in 0..<count {
                    if i % 2 == 0 {
                        mixed.append(Int.random(in: minRange...(minRange + 5)))
                    } else {
                        mixed.append(Int.random(in: (maxRange - 5)...maxRange))
                    }
                }
                return mixed
            }
        ]
        
        let selectedPattern = patterns.randomElement()!
        let numbers = makeUnique(numbers: selectedPattern(), range: minRange...maxRange)
        let targetSum = numbers.reduce(0, +)
        return (numbers.sorted(), targetSum)
    }
    
    // MARK: - Helper Functions
    
    private static func getExpandedDifficultyParams(_ difficulty: String) -> (count: Int, minRange: Int, maxRange: Int) {
        switch difficulty.lowercased() {
        case "easy":
            return (2, 1, 15)   // Was 1-8, now much wider
        case "medium":
            return (3, 2, 18)   // Was 2-10, now wider
        case "hard":
            return (3, 3, 22)   // Was 3-15, now wider
        case "expert":
            return (4, 4, 25)   // Was 4-18, now wider
        default:
            return (3, 2, 18)
        }
    }
    
    private static func getMixedRanges(difficulty: String) -> [ClosedRange<Int>] {
        switch difficulty.lowercased() {
        case "easy":
            return [1...8, 6...12, 10...15]
        case "medium":
            return [2...10, 8...15, 12...20]
        case "hard":
            return [3...12, 10...18, 15...25]
        case "expert":
            return [4...15, 12...22, 18...30, 20...35]
        default:
            return [2...10, 8...15, 12...20]
        }
    }
    
    private static func makeUnique(numbers: [Int], range: ClosedRange<Int>) -> [Int] {
        var uniqueNumbers: [Int] = []
        var used = Set<Int>()
        
        for number in numbers {
            var candidate = number
            var attempts = 0
            
            // Find a unique number close to the original
            while used.contains(candidate) && attempts < 20 {
                if attempts % 2 == 0 {
                    candidate = min(number + (attempts / 2) + 1, range.upperBound)
                } else {
                    candidate = max(number - (attempts / 2) - 1, range.lowerBound)
                }
                attempts += 1
            }
            
            uniqueNumbers.append(candidate)
            used.insert(candidate)
        }
        
        return uniqueNumbers
    }
    
    // MARK: - Improved Distractor Generation
    
    private static func generateVariedDistractors(targetSum: Int, difficulty: String, excludeNumbers: [Int]) -> [Int] {
        let distractorCount = difficulty.lowercased() == "expert" ? 4 : 5
        var distractors: [Int] = []
        let excludeSet = Set(excludeNumbers)
        let (_, minRange, maxRange) = getExpandedDifficultyParams(difficulty)
        
        // Strategy 1: Numbers that create interesting near-misses
        let nearMissTargets = [
            targetSum - 1, targetSum + 1,
            targetSum - 2, targetSum + 2,
            targetSum / 2, // Half target
            targetSum * 2  // Double target (if reasonable)
        ].filter { $0 > 0 && $0 <= maxRange * 2 }
        
        for target in nearMissTargets.prefix(2) {
            if distractors.count >= distractorCount { break }
            
            if !excludeSet.contains(target) && !distractors.contains(target) {
                distractors.append(target)
            }
        }
        
        // Strategy 2: Random numbers from expanded ranges
        let expandedRanges = [
            max(1, minRange - 3)...minRange,     // Below normal range
            minRange...maxRange,                  // Normal range
            maxRange...(maxRange + 8),           // Above normal range
            (targetSum + 5)...(targetSum + 15)   // Much larger numbers
        ]
        
        for range in expandedRanges {
            if distractors.count >= distractorCount { break }
            
            for _ in 0..<2 { // Try 2 numbers from each range
                if distractors.count >= distractorCount { break }
                
                let candidate = Int.random(in: range)
                if !excludeSet.contains(candidate) &&
                   !distractors.contains(candidate) &&
                   candidate != targetSum {
                    distractors.append(candidate)
                    break
                }
            }
        }
        
        // Strategy 3: Mathematically interesting numbers
        let interestingNumbers = [
            targetSum + Int.random(in: 10...20),  // Much larger
            max(1, targetSum - Int.random(in: 5...10)), // Moderately smaller
            Int.random(in: 1...5),                // Very small
            Int.random(in: 25...40)               // Large random
        ]
        
        for candidate in interestingNumbers {
            if distractors.count >= distractorCount { break }
            
            if !excludeSet.contains(candidate) &&
               !distractors.contains(candidate) &&
               candidate != targetSum {
                distractors.append(candidate)
            }
        }
        
        // Final fallback: ensure we have enough distractors
        while distractors.count < distractorCount {
            let candidate = Int.random(in: (maxRange + 10)...(maxRange + 30))
            if !excludeSet.contains(candidate) && !distractors.contains(candidate) {
                distractors.append(candidate)
            }
        }
        
        return Array(distractors.prefix(distractorCount)).shuffled()
    }
    
    // MARK: - Fallback Generation
    
    private static func generateFallbackNumberSum() -> (String, String) {
        print("ðŸ”„ Generating fallback number sum puzzle")
        
        let questionData = NumberSumPuzzleData(
            targetSum: 15,
            correctNumbers: [4, 11],
            allNumbers: [4, 11, 3, 7, 18, 25],
            difficulty: "easy",
            timeLimit: 120,
            totalRounds: 3,
            scoring: NumberSumPuzzleData.ScoringConfig(pointsPerCorrect: 10, timeBonus: 5, roundBonus: 50)
        )
        
        let answerData = NumberSumAnswer(
            targetSum: 15,
            correctNumbers: [4, 11],
            allNumbers: [4, 11, 3, 7, 18, 25],
            maxScore: 670
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
    
    // MARK: - Difficulty Configuration
    
    private static func getNumberSumDifficultyConfig(for difficulty: String) -> NumberSumDifficultyConfig {
        switch difficulty.lowercased() {
        case "easy":
            return NumberSumDifficultyConfig(timeLimit: 120, totalRounds: 3, pointsPerCorrect: 10, timeBonus: 5, roundBonus: 50)
        case "medium":
            return NumberSumDifficultyConfig(timeLimit: 100, totalRounds: 4, pointsPerCorrect: 15, timeBonus: 5, roundBonus: 75)
        case "hard":
            return NumberSumDifficultyConfig(timeLimit: 80, totalRounds: 5, pointsPerCorrect: 20, timeBonus: 5, roundBonus: 100)
        case "expert":
            return NumberSumDifficultyConfig(timeLimit: 60, totalRounds: 6, pointsPerCorrect: 25, timeBonus: 5, roundBonus: 150)
        default:
            return getNumberSumDifficultyConfig(for: "medium")
        }
    }
    
    private static func calculateMaxScore(correctNumbers: [Int], config: NumberSumDifficultyConfig) -> Int {
        let baseScore = correctNumbers.count * config.pointsPerCorrect
        let maxTimeBonus = config.timeLimit * config.timeBonus
        let roundBonus = config.roundBonus
        return baseScore + maxTimeBonus + roundBonus
    }
}


private struct NumberSumDifficultyConfig {
    let timeLimit: Int
    let totalRounds: Int
    let pointsPerCorrect: Int
    let timeBonus: Int
    let roundBonus: Int
}

struct NumberSumAnswer: Codable {
    let targetSum: Int
    let correctNumbers: [Int]
    let allNumbers: [Int]
    let maxScore: Int
}

