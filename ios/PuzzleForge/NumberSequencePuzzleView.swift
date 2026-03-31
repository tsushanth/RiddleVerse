//
//  NumberSequencePuzzleView.swift
//  PuzzleForge
//
//  Created by Assistant on 7/20/25.
//

import SwiftUI
import Foundation

// MARK: - Data Models
struct NumberPosition: Identifiable {
    let id = UUID()
    let number: Int
    let x: CGFloat
    let y: CGFloat
    let color: Color
    var isVisible: Bool = true
}

struct DifficultyConfig {
    let numberCount: Int
    let numberRange: ClosedRange<Int>
    let timePerNumber: Int // seconds
    let name: String
}

struct NumberSequencePuzzleData {
    let targetSequence: [Int]
    let difficulty: String
    let timeLimit: Int
    let numberCount: Int
    let numberRange: ClosedRange<Int>
    let timePerNumber: Int
}

// MARK: - Local Memory Puzzle Generator Extension
extension LocalMemoryPuzzleGenerator {
    static func generateNumberSequence(difficulty: String) -> (String, String) {
        let configs: [String: DifficultyConfig] = [
            "Easy": DifficultyConfig(numberCount: 5, numberRange: 1...20, timePerNumber: 15, name: "Easy"),
            "Medium": DifficultyConfig(numberCount: 7, numberRange: 1...50, timePerNumber: 12, name: "Medium"),
            "Hard": DifficultyConfig(numberCount: 9, numberRange: 1...100, timePerNumber: 10, name: "Hard"),
            "Expert": DifficultyConfig(numberCount: 12, numberRange: 1...200, timePerNumber: 8, name: "Expert")
        ]
        
        let config = configs[difficulty] ?? configs["Medium"]!
        let totalTime = config.numberCount * config.timePerNumber
        
        // Generate unique random numbers
        var randomNumbers = Set<Int>()
        while randomNumbers.count < config.numberCount {
            randomNumbers.insert(Int.random(in: config.numberRange))
        }
        
        let numbers = Array(randomNumbers).shuffled()
        let targetSequence = numbers.sorted()
        
        let puzzleData = NumberSequencePuzzleData(
            targetSequence: targetSequence,
            difficulty: difficulty,
            timeLimit: totalTime,
            numberCount: config.numberCount,
            numberRange: config.numberRange,
            timePerNumber: config.timePerNumber
        )
        
        // Convert to JSON for question data
        let questionData: [String: Any] = [
            "numbers": numbers,
            "targetSequence": targetSequence,
            "difficulty": difficulty,
            "timeLimit": totalTime,
            "numberCount": config.numberCount,
            "numberRangeMin": config.numberRange.lowerBound,
            "numberRangeMax": config.numberRange.upperBound,
            "timePerNumber": config.timePerNumber
        ]
        
        guard let questionJSON = try? JSONSerialization.data(withJSONObject: questionData),
              let questionString = String(data: questionJSON, encoding: .utf8) else {
            return ("", "")
        }
        
        // Answer is the correct sequence as a comma-separated string
        let answerString = targetSequence.map { String($0) }.joined(separator: ",")
        
        return (questionString, answerString)
    }
}

// MARK: - Puzzle Data Extension
extension Puzzle {
    var numberSequencePuzzleData: NumberSequencePuzzleData? {
        guard let data = question.data(using: .utf8),
              let json = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
              let numbers = json["numbers"] as? [Int],
              let targetSequence = json["targetSequence"] as? [Int],
              let difficulty = json["difficulty"] as? String,
              let timeLimit = json["timeLimit"] as? Int,
              let numberCount = json["numberCount"] as? Int,
              let rangeMin = json["numberRangeMin"] as? Int,
              let rangeMax = json["numberRangeMax"] as? Int,
              let timePerNumber = json["timePerNumber"] as? Int else {
            return nil
        }
        
        return NumberSequencePuzzleData(
            targetSequence: targetSequence,
            difficulty: difficulty,
            timeLimit: timeLimit,
            numberCount: numberCount,
            numberRange: rangeMin...rangeMax,
            timePerNumber: timePerNumber
        )
    }
}

// MARK: - Main Number Sequence Puzzle View
struct AdaptiveNumberSequenceConfig {
    let numberCount: Int
    let numberRange: ClosedRange<Int>
    let timePerNumber: Int
    let name: String
}

// MARK: - Enhanced Number Position with Adaptive Features
struct AdaptiveNumberPosition: Identifiable {
    let id = UUID()
    let number: Int
    let x: CGFloat
    let y: CGFloat
    let color: Color
    var isVisible: Bool = true
    var scale: CGFloat = 1.0
    var opacity: Double = 1.0
}


// MARK: - Main Adaptive Number Sequence Puzzle View
struct NumberSequencePuzzleView: View {
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
        difficultyManager.getCurrentDifficulty(for: "numberSequence")
    }
    
    // Competitive features (placeholder)
    @State private var competitiveInsight: CompetitiveInsight?
    
    // Game state
    @State private var timeLeft: Int = 60
    @State private var isPaused = false
    @State private var currentHearts = 3
    @State private var gameCompleted = false
    @State private var gameStarted = false
    @State private var showFeedback = false
    @State private var feedbackMessage = ""
    @State private var feedbackColor = Color.red
    @State private var currentStreak = 0
    @State private var gamesPlayedThisSession = 0
    
    // Session tracking
    @State private var sessionStartTime = Date()
    @State private var correctRounds = 0
    @State private var totalRounds = 0
    
    // Puzzle state
    @State private var currentRoundNumbers: [Int] = []
    @State private var sortedTargetNumbers: [Int] = []
    @State private var nextExpectedIndex = 0
    @State private var numbersVisible: [Int: Bool] = [:]
    @State private var numberPositions: [AdaptiveNumberPosition] = []
    @State private var showWrongFeedback = false
    @State private var wrongFeedbackOffset = CGSize.zero
    @State private var currentRound = 1
    @State private var totalScore = 0
    @State private var roundStartTime = Date()
    
    // Feedback system
    @StateObject private var feedbackManager = UnifiedFeedbackManager()
    @State private var showHint = false
    
    // Animation states
    @State private var wrongScale: CGFloat = 1.0
    @State private var showInstructions = true
    
    // Adaptive configuration
    private var adaptiveConfig: AdaptiveNumberSequenceConfig {
        generateAdaptiveConfig(difficulty: currentDifficulty)
    }
    
    
    
    private let colors: [Color] = [
        .blue, .green, .pink, .orange, .purple, .cyan,
        Color(red: 1.0, green: 0.35, blue: 0.13),
        Color(red: 0.48, green: 0.33, blue: 0.29),
        Color(red: 0.38, green: 0.49, blue: 0.55),
        Color(red: 0.55, green: 0.76, blue: 0.29),
        Color(red: 1.0, green: 0.42, blue: 0.21),
        Color(red: 0.42, green: 0.36, blue: 0.90)
    ]
    
    var body: some View {
        ZStack {
            // Background gradient
            LinearGradient(
                colors: [
                    Color(red: 0.11, green: 0.37, blue: 0.13),
                    Color(red: 0.15, green: 0.47, blue: 0.17)
                ],
                startPoint: .top,
                endPoint: .bottom
            )
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
                
                Spacer()
            }
            
            // Feedback overlay
            if showFeedback {
                feedbackOverlay
            }
            
            // Wrong feedback animation
            if showWrongFeedback {
                wrongFeedbackView
            }
        }
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
                    Text(String(format: "%02d:%02d", timeLeft / 60, timeLeft % 60))
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
                        Text("Difficulty Adapted!")
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
                Text("🔢")
                    .font(.system(size: 60))
                
                Text("Adaptive Number Sequence")
                    .font(.title)
                    .fontWeight(.bold)
                    .foregroundColor(.white)
                
                Text("Tap numbers in ascending order\n(smallest to largest)")
                    .font(.headline)
                    .foregroundColor(.white.opacity(0.9))
                    .multilineTextAlignment(.center)
                    .lineSpacing(4)
                
                VStack(spacing: 8) {
                    Text("\(adaptiveConfig.name) Difficulty")
                        .font(.subheadline)
                        .fontWeight(.bold)
                        .foregroundColor(.cyan)
                    
                    Text("Range: \(adaptiveConfig.numberRange.lowerBound)-\(adaptiveConfig.numberRange.upperBound) • \(adaptiveConfig.numberCount) numbers per round")
                        .font(.caption)
                        .foregroundColor(.white.opacity(0.7))
                        .multilineTextAlignment(.center)
                    
                                            Text(currentDifficulty.description)
                        .font(.caption)
                        .foregroundColor(.white.opacity(0.6))
                        .multilineTextAlignment(.center)
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
        GeometryReader { geometry in
            ZStack {
                // Adaptive grid background
                Canvas { context, size in
                    let gridSize: CGFloat = 40
                    let gridColor = Color.white.opacity(0.1)
                    
                    for x in stride(from: 0, through: size.width, by: gridSize) {
                        context.stroke(
                            Path { path in
                                path.move(to: CGPoint(x: x, y: 0))
                                path.addLine(to: CGPoint(x: x, y: size.height))
                            },
                            with: .color(gridColor),
                            lineWidth: 1
                        )
                    }
                    
                    for y in stride(from: 0, through: size.height, by: gridSize) {
                        context.stroke(
                            Path { path in
                                path.move(to: CGPoint(x: 0, y: y))
                                path.addLine(to: CGPoint(x: size.width, y: y))
                            },
                            with: .color(gridColor),
                            lineWidth: 1
                        )
                    }
                }
                
                // Adaptive hint overlay
                if showHint && nextExpectedIndex < sortedTargetNumbers.count {
                    VStack {
                        Text("💡 Next number: \(sortedTargetNumbers[nextExpectedIndex])")
                            .font(.headline)
                            .fontWeight(.bold)
                            .foregroundColor(.yellow)
                            .padding()
                            .background(
                                RoundedRectangle(cornerRadius: 12)
                                    .fill(Color.black.opacity(0.7))
                            )
                        Spacer()
                    }
                    .transition(.opacity)
                }
                
                // Adaptive number sizing based on difficulty
                ForEach(numberPositions.filter { numbersVisible[$0.number] == true }) { position in
                    AdaptiveNumberView(
                        position: position,
                        difficulty: difficultyManager.getCurrentDifficulty(for: "numbersequence"),
                        onTap: {
                            handleAdaptiveNumberTap(position, in: geometry)
                        }
                    )
                }
            }
        }
        .padding()
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
                
                Text("Rounds Completed: \(correctRounds)/\(totalRounds)")
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
    
    // MARK: - Feedback Overlay
    private var feedbackOverlay: some View {
        VStack {
            Spacer()
            
            Text(feedbackMessage)
                .font(.headline)
                .fontWeight(.bold)
                .foregroundColor(.white)
                .padding()
                .background(
                    RoundedRectangle(cornerRadius: 12)
                        .fill(feedbackColor.opacity(0.9))
                )
                .padding(.horizontal)
            
            Spacer()
        }
        .transition(.opacity.combined(with: .scale))
    }
    
    // MARK: - Wrong Feedback View
    private var wrongFeedbackView: some View {
        Image(systemName: "xmark.circle.fill")
            .font(.system(size: 40))
            .foregroundColor(.red)
            .scaleEffect(wrongScale)
            .offset(wrongFeedbackOffset)
            .animation(.easeInOut(duration: 0.3), value: wrongScale)
    }
    
    // MARK: - Helper Methods
    
    private func initializeAdaptivePuzzle() {
        timeLeft = currentDifficulty.timeLimit
        currentHearts = currentDifficulty.livesAllowed
        generateAdaptiveRound()
        
        // Load competitive insight (placeholder)
        competitiveInsight = CompetitiveInsight(
            percentile: 75,
            ranking: "Silver",
            improvement: "Top 25% this week!",
            globalAverage: 78.5,
            userScore: 82.3
        )
    }
    
    private func startAdaptiveGame() {
        withAnimation {
            showInstructions = false
            gameStarted = true
        }
        generateAdaptivePositions()
        sessionStartTime = Date()
    }
    
    private func generateAdaptiveRound() {
        let config = adaptiveConfig
        
        // Generate unique random numbers within adaptive range
        var randomNumbers = Set<Int>()
        while randomNumbers.count < config.numberCount {
            randomNumbers.insert(Int.random(in: config.numberRange))
        }
        
        let numbers = Array(randomNumbers).shuffled()
        currentRoundNumbers = numbers
        sortedTargetNumbers = numbers.sorted()
        nextExpectedIndex = 0
        numbersVisible = numbers.reduce(into: [:]) { result, number in
            result[number] = true
        }
        roundStartTime = Date()
        
        print("🔢 New adaptive round \(currentRound):")
        print("🔢 Numbers: \(numbers)")
        print("🔢 Target order: \(sortedTargetNumbers)")
        print("🔢 Difficulty: \(currentDifficulty.name)")
    }
    
    private func generateAdaptivePositions() {
        var positions: [AdaptiveNumberPosition] = []
        var usedPositions: [CGPoint] = []
        
        for (index, number) in currentRoundNumbers.enumerated() {
            var position: CGPoint
            var attempts = 0
            
            // Find a position that doesn't overlap
            repeat {
                position = CGPoint(
                    x: CGFloat.random(in: 0.15...0.85),
                    y: CGFloat.random(in: 0.2...0.8)
                )
                attempts += 1
            } while usedPositions.contains { pos in
                abs(pos.x - position.x) < 0.15 && abs(pos.y - position.y) < 0.15
            } && attempts < 50
            
            usedPositions.append(position)
            
            positions.append(
                AdaptiveNumberPosition(
                    number: number,
                    x: position.x,
                    y: position.y,
                    color: colors[index % colors.count],
                    isVisible: true
                )
            )
        }
        
        numberPositions = positions
    }
    
    private func handleAdaptiveNumberTap(_ position: AdaptiveNumberPosition, in geometry: GeometryProxy) {
        guard !isPaused else { return }
        
        let expectedNumber = sortedTargetNumbers[safe: nextExpectedIndex]
        
        if position.number == expectedNumber {
            // Correct number tapped
            numbersVisible[position.number] = false
            nextExpectedIndex += 1
            
            // Add score for correct tap
            let tapScore = calculateAdaptiveScore(isCorrect: true, timeSpent: Date().timeIntervalSince(roundStartTime))
            totalScore += tapScore
            
            // Check if round is completed
            if nextExpectedIndex >= sortedTargetNumbers.count {
                handleAdaptiveRoundComplete()
            }
        } else {
            // Wrong number tapped
            handleAdaptiveWrongTap(position, in: geometry)
        }
    }
    
    private func handleAdaptiveWrongTap(_ position: AdaptiveNumberPosition, in geometry: GeometryProxy) {
        currentHearts = max(0, currentHearts - 1)
        currentStreak = 0
        totalRounds += 1
        
        // Record poor performance
        recordAdaptivePerformance(isCorrect: false, timeSpent: Date().timeIntervalSince(roundStartTime))
        
        // Calculate screen position for feedback
        let screenX = geometry.size.width * position.x
        let screenY = geometry.size.height * position.y
        wrongFeedbackOffset = CGSize(width: screenX - geometry.size.width / 2, height: screenY - geometry.size.height / 2)
        
        withAnimation(.easeInOut(duration: 0.3)) {
            showWrongFeedback = true
            wrongScale = 1.5
        }
        
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.8) {
            withAnimation {
                showWrongFeedback = false
                wrongScale = 1.0
            }
        }
        
        if currentHearts == 0 {
            handleGameOver(success: false)
        }
    }
    
    private func handleAdaptiveRoundComplete() {
        correctRounds += 1
        totalRounds += 1
        currentStreak += 1
        gamesPlayedThisSession += 1
        
        let timeSpent = Date().timeIntervalSince(roundStartTime)
        let roundScore = calculateAdaptiveScore(isCorrect: true, timeSpent: timeSpent)
        totalScore += roundScore
        
        // Record successful performance
        recordAdaptivePerformance(isCorrect: true, timeSpent: timeSpent)
        
        feedbackMessage = "🎉 Round \(currentRound) Complete! +\(roundScore) points"
        feedbackColor = Color.green
        
        withAnimation {
            showFeedback = true
        }
        
        DispatchQueue.main.asyncAfter(deadline: .now() + 1.5) {
            withAnimation {
                showFeedback = false
            }
            
            // Start next round
            currentRound += 1
            generateAdaptiveRound()
            generateAdaptivePositions()
            
            // Add adaptive bonus time
            let bonusTime = adaptiveConfig.timePerNumber * 2
            timeLeft += bonusTime
        }
    }
    
    private func recordAdaptivePerformance(isCorrect: Bool, timeSpent: TimeInterval) {
        let adaptation = difficultyManager.recordPerformance(
            puzzleType: "numberSequence",
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
            
            // Hide notification after delay
            DispatchQueue.main.asyncAfter(deadline: .now() + 3.0) {
                showAdaptationNotification = false
            }
        }
    }
    
    private func calculateAdaptiveScore(isCorrect: Bool, timeSpent: TimeInterval) -> Int {
        guard isCorrect else { return 0 }
        
        let basePoints = 100 // Base points for number sequence
        
        // Time bonus
        let timeBonus = max(0, Int((10.0 - timeSpent) * 5))
        
        // Streak bonus
        let streakBonus = currentStreak * 10
        
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
        
        let finalScore = Int(Float(basePoints + timeBonus + streakBonus) * difficultyMultiplier)
        
        return max(finalScore, basePoints / 4)
    }
    
    private func handleGameOver(success: Bool) {
        gameCompleted = true
        onAnswerSubmitted(success)
        
        // Create session statistics
        let sessionStats = SessionStatistics(
            correctAnswers: correctRounds,
            totalAnswers: totalRounds,
            totalTimeSeconds: Int(Date().timeIntervalSince(sessionStartTime)),
            bestStreak: currentStreak,
            currentStreak: currentStreak,
            totalScore: totalScore,
            individualTimes: [],
            puzzleType: "numbersequence" // Replace with actual puzzle type
        )
        
        if success {
            feedbackMessage = "🎉 Adaptive Challenge Complete! Final Score: \(totalScore)"
            feedbackColor = Color.green
        } else {
            feedbackMessage = "💔 Game Over! Final Score: \(totalScore)"
            feedbackColor = Color.red
        }
        
        withAnimation {
            showFeedback = true
        }
        
        DispatchQueue.main.asyncAfter(deadline: .now() + 2.0) {
            if success {
                onNextPuzzle()
            } else {
                onExit()
            }
        }
    }
    
    private func generateAdaptiveConfig(difficulty: DifficultyManager.DifficultyLevel) -> AdaptiveNumberSequenceConfig {
        switch difficulty {
        case .beginner:
            return AdaptiveNumberSequenceConfig(
                numberCount: 4,
                numberRange: 1...10,
                timePerNumber: 8,
                name: "Beginner"
            )
        case .easy:
            return AdaptiveNumberSequenceConfig(
                numberCount: 5,
                numberRange: 1...20,
                timePerNumber: 6,
                name: "Easy"
            )
        case .medium:
            return AdaptiveNumberSequenceConfig(
                numberCount: 7,
                numberRange: 1...50,
                timePerNumber: 5,
                name: "Medium"
            )
        case .hard:
            return AdaptiveNumberSequenceConfig(
                numberCount: 9,
                numberRange: 1...100,
                timePerNumber: 4,
                name: "Hard"
            )
        case .expert:
            return AdaptiveNumberSequenceConfig(
                numberCount: 12,
                numberRange: 1...200,
                timePerNumber: 3,
                name: "Expert"
            )
        }
    }
}

// MARK: - Adaptive Number View Component
struct AdaptiveNumberView: View {
    let position: AdaptiveNumberPosition
    let difficulty: DifficultyManager.DifficultyLevel
    let onTap: () -> Void
    
    @State private var scale: CGFloat = 1.0
    
    // Adaptive sizing based on difficulty
    private var numberSize: CGFloat {
        switch difficulty {
        case .beginner: return 70  // Beginner - larger
        case .easy: return 65      // Easy
        case .medium: return 60    // Medium
        case .hard: return 55      // Hard
        case .expert: return 50    // Expert - smaller
        }
    }
    
    private var fontSize: CGFloat {
        switch difficulty {
        case .beginner: return 28  // Beginner
        case .easy: return 26      // Easy
        case .medium: return 24    // Medium
        case .hard: return 22      // Hard
        case .expert: return 20    // Expert
        }
    }
    
    var body: some View {
        GeometryReader { geometry in
            Button(action: {
                withAnimation(.easeInOut(duration: 0.1)) {
                    scale = 0.9
                }
                
                DispatchQueue.main.asyncAfter(deadline: .now() + 0.1) {
                    withAnimation(.easeInOut(duration: 0.1)) {
                        scale = 1.0
                    }
                }
                
                onTap()
            }) {
                Text("\(position.number)")
                    .font(.system(size: fontSize, weight: .bold))
                    .foregroundColor(.white)
                    .frame(width: numberSize, height: numberSize)
                    .background(
                        Circle()
                            .fill(position.color)
                            .shadow(color: .black.opacity(0.3), radius: 4, x: 0, y: 2)
                    )
            }
            .buttonStyle(PlainButtonStyle())
            .scaleEffect(scale)
            .position(
                x: geometry.size.width * position.x,
                y: geometry.size.height * position.y
            )
        }
    }
}

// MARK: - Array Extension for Safe Access
extension Array {
    subscript(safe index: Index) -> Element? {
        return indices.contains(index) ? self[index] : nil
    }
}
