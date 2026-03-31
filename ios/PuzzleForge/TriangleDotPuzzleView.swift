//
//  TriangleDotInstructions.swift
//  PuzzleForge
//
//  Created by Sushanth Tiruvaipati on 8/17/25.
//


//
//  AdaptiveTriangleDotMemoryPuzzleView.swift
//  PuzzleForge
//
//  iOS Adaptive Triangle Dot Memory with unified difficulty management
//

import SwiftUI
import Foundation
import Combine

// MARK: - Supporting Data Models
struct TriangleDotInstructions {
    let title: String
    let description: String
    let steps: [String]
    let tip: String
}

struct TriangleDotStep {
    let step: Int
    let redDotPosition: Int  // 0: top, 1: bottom-left, 2: bottom-right
    let isFirstStep: Bool
}

struct TriangleDotPuzzleData {
    let sequence: [TriangleDotStep]
    let instructions: TriangleDotInstructions
    let totalSteps: Int
    let totalQuestions: Int
    let difficulty: String
}

struct TriangleDotAnswerData {
    let correctAnswers: [Int]  // 1 for same, 0 for different
    let totalQuestions: Int
    let maxScore: Int
}

// MARK: - Main Adaptive Triangle Dot View
struct AdaptiveTriangleDotMemoryPuzzleView: View {
    let puzzle: Puzzle
    let questionIndex: Int
    let totalQuestions: Int
    let onAnswerSubmitted: (Bool) -> Void
    let onNextPuzzle: () -> Void
    let onExit: () -> Void
    
    // Dependency injection
    @StateObject private var difficultyManager = DifficultyManager.shared
    @StateObject private var competitiveManager = CompetitiveRankingManager.shared
    
    // Adaptive difficulty state
    @State private var currentDifficultyLevel = DifficultyManager.DifficultyLevel.medium
    @State private var adaptationInfo: DifficultyManager.AdaptiveConfig?
    @State private var showAdaptationNotification = false
    @State private var competitiveInsight: CompetitiveInsight?
    
    // Game state
    @State private var gameState = "instructions"  // "instructions", "playing", "completed"
    @State private var currentQuestionIndex = 0
    @State private var currentScore = 0
    @State private var totalScore = 0
    @State private var showFeedback = false
    @State private var lastAnswerCorrect = false
    @State private var userAnswers: [Int] = []
    @State private var skipFirstComparisonDone = false
    @State private var currentStreak = 0
    @State private var gamesPlayedThisSession = 0
    
    // Session tracking
    @State private var sessionStartTime = Date()
    @State private var correctAnswers = 0
    @State private var totalAnswers = 0
    
    // Adaptive timing
    @State private var timeRemaining = 60
    @State private var currentHearts = 3
    
    // Puzzle data
    @State private var puzzleData: TriangleDotPuzzleData?
    @State private var answerData: TriangleDotAnswerData?
    
    var body: some View {
        ZStack {
            Color(red: 0.12, green: 0.23, blue: 0.54)
                .ignoresSafeArea()
            
            VStack(spacing: 16) {
                Spacer().frame(height: 80)
                
                // Unified header
                AdaptiveUnifiedHeader(
                    puzzleType: "triangledotmemory",
                    currentDifficulty: currentDifficultyLevel,
                    score: totalScore,
                    challengeNumber: currentQuestionIndex + 1,
                    totalChallenges: puzzleData?.totalQuestions ?? 10,
                    timer: formatTime(timeRemaining),
                    lives: currentHearts,
                    competitiveInsight: competitiveInsight,
                    level: UserLevel(level: 5, currentXP: 750, xpToNextLevel: 1000, totalXP: 3250),
                    streakInfo: StreakInfo(
                        currentStreak: currentStreak, 
                        bestStreak: 12, 
                        streakMultiplier: currentStreak >= 3 ? 1.5 : 1.0,
                        dailyStreak: 3,
                        hasDailyStreakBonus: true
                    ),
                    onBack: onExit,
                    onPause: { /* Memory games don't pause */ },
                    onHint: { /* No hints for memory tasks */ }
                )
                
                // Adaptation notification
                UnifiedAdaptationNotification(
                    adaptationInfo: adaptationInfo,
                    puzzleType: "triangledotmemory",
                    visible: showAdaptationNotification,
                    onDismiss: { showAdaptationNotification = false }
                )
                
                // Main content based on game state
                switch gameState {
                case "instructions":
                    instructionsView
                case "playing":
                    gameView
                case "completed":
                    completionView
                default:
                    EmptyView()
                }
                
                Spacer()
            }
            .padding(.horizontal, 16)
            .padding(.bottom, 32)
        }
        .navigationBarHidden(true)
        .onAppear {
            setupGame()
        }
    }
    
    // MARK: - Instructions View
    private var instructionsView: some View {
        VStack(spacing: 20) {
            if let data = puzzleData {
                Text(data.instructions.title)
                    .font(.title2)
                    .fontWeight(.bold)
                    .foregroundColor(.white)
                    .multilineTextAlignment(.center)
                
                Text(currentDifficultyLevel.description)
                    .font(.subheadline)
                    .fontWeight(.medium)
                    .foregroundColor(.cyan)
                    .multilineTextAlignment(.center)
                
                Text(data.instructions.description)
                    .font(.subheadline)
                    .foregroundColor(.white.opacity(0.9))
                    .multilineTextAlignment(.center)
                
                Spacer().frame(height: 24)
                
                // Example triangle
                VStack(spacing: 16) {
                    Text("Example Pattern")
                        .font(.headline)
                        .fontWeight(.bold)
                        .foregroundColor(.white)
                    
                    TriangleDotPatternView(redDotPosition: 1)
                        .frame(width: 120, height: 120)
                    
                    Text("Red dot at bottom-left")
                        .font(.caption)
                        .foregroundColor(.white.opacity(0.8))
                }
                .padding()
                .background(
                    RoundedRectangle(cornerRadius: 12)
                        .fill(Color.white.opacity(0.1))
                )
                
                Spacer().frame(height: 24)
                
                // Instructions list
                VStack(alignment: .leading, spacing: 8) {
                    ForEach(data.instructions.steps, id: \.self) { step in
                        HStack(alignment: .top) {
                            Text("•")
                                .foregroundColor(.white)
                                .fontWeight(.bold)
                            Text(step)
                                .font(.subheadline)
                                .foregroundColor(.white)
                        }
                    }
                    
                    Spacer().frame(height: 12)
                    
                    HStack(alignment: .top) {
                        Text("💡")
                        Text(data.instructions.tip)
                            .font(.subheadline)
                            .foregroundColor(.white)
                            .fontWeight(.medium)
                    }
                    
                    Spacer().frame(height: 8)
                    
                    Text("⚡ Difficulty: \(currentDifficultyLevel.name) • Time Limit: \(currentDifficultyLevel.timeLimit)s")
                        .font(.caption)
                        .foregroundColor(.white.opacity(0.6))
                        .fontWeight(.medium)
                }
                .padding()
                .background(
                    RoundedRectangle(cornerRadius: 12)
                        .fill(Color.white.opacity(0.1))
                )
                
                Spacer()
                
                Button(action: startGame) {
                    Text("START GAME")
                        .font(.headline)
                        .fontWeight(.bold)
                        .foregroundColor(.white)
                        .frame(maxWidth: .infinity)
                        .padding()
                        .background(Color(red: 0.07, green: 0.73, blue: 0.51))
                        .cornerRadius(12)
                }
            }
        }
    }
    
    // MARK: - Game View
    private var gameView: some View {
        VStack(spacing: 20) {
            if let data = puzzleData {
                // Progress indicator
                progressSection(data: data)
                
                Spacer()
                
                // Current triangle pattern
                triangleSection(data: data)
                
                Spacer()
                
                // Answer buttons (only after first step)
                if currentQuestionIndex >= 1 {
                    answerButtonsSection
                }
                
                // Feedback overlay
                if showFeedback {
                    feedbackOverlay
                }
            }
        }
    }
    
    private func progressSection(data: TriangleDotPuzzleData) -> some View {
        HStack {
            // Question number indicator
            Circle()
                .fill(Color(red: 0.07, green: 0.73, blue: 0.51))
                .frame(width: 48, height: 48)
                .overlay(
                    Text("\(currentQuestionIndex + 1)")
                        .foregroundColor(.white)
                        .fontWeight(.bold)
                        .font(.headline)
                )
            
            Spacer()
            
            VStack(alignment: .center) {
                Text("\(currentQuestionIndex + 1) / \(data.totalQuestions)")
                    .foregroundColor(.white)
                    .font(.subheadline)
                    .fontWeight(.medium)
                
                Text(currentDifficultyLevel.name)
                    .foregroundColor(.cyan)
                    .font(.caption)
                    .fontWeight(.bold)
            }
            
            Spacer()
            
            // Progress bar
            ProgressView(value: Double(currentQuestionIndex + 1), total: Double(data.totalQuestions))
                .frame(width: 80, height: 6)
                .scaleEffect(x: 1, y: 2, anchor: .center)
                .progressViewStyle(LinearProgressViewStyle(tint: Color(red: 0.07, green: 0.73, blue: 0.51)))
        }
        .padding()
        .background(
            RoundedRectangle(cornerRadius: 12)
                .fill(Color.white.opacity(0.1))
        )
    }
    
    private func triangleSection(data: TriangleDotPuzzleData) -> some View {
        VStack(spacing: 16) {
            if currentQuestionIndex >= 2 {
                Text("Does this pattern match the\nprevious pattern?")
                    .font(.headline)
                    .foregroundColor(.white)
                    .multilineTextAlignment(.center)
                    .lineSpacing(4)
            }
            
            // Triangle pattern with animation
            if currentQuestionIndex < data.sequence.count {
                TriangleDotPatternView(redDotPosition: data.sequence[currentQuestionIndex].redDotPosition)
                    .frame(width: 160, height: 160)
                    .background(
                        RoundedRectangle(cornerRadius: 16)
                            .fill(Color.white)
                    )
                    .animation(.spring(response: 0.3), value: currentQuestionIndex)
            }
        }
    }
    
    private var answerButtonsSection: some View {
        HStack(spacing: 16) {
            Button(action: { handleAnswer(false) }) {
                Text("NO")
                    .font(.title3)
                    .fontWeight(.bold)
                    .foregroundColor(.white)
                    .frame(maxWidth: .infinity)
                    .padding()
                    .background(Color(red: 0.23, green: 0.51, blue: 0.96))
                    .cornerRadius(12)
            }
            .disabled(showFeedback)
            
            Button(action: { handleAnswer(true) }) {
                Text("YES")
                    .font(.title3)
                    .fontWeight(.bold)
                    .foregroundColor(.white)
                    .frame(maxWidth: .infinity)
                    .padding()
                    .background(Color(red: 0.23, green: 0.51, blue: 0.96))
                    .cornerRadius(12)
            }
            .disabled(showFeedback)
        }
    }
    
    private var feedbackOverlay: some View {
        VStack(spacing: 16) {
            Circle()
                .fill(lastAnswerCorrect ? Color(red: 0.07, green: 0.73, blue: 0.51) : Color(red: 0.94, green: 0.27, blue: 0.27))
                .frame(width: 120, height: 120)
                .overlay(
                    Text(lastAnswerCorrect ? "✓" : "✗")
                        .font(.system(size: 60))
                        .foregroundColor(.white)
                        .fontWeight(.bold)
                )
                .scaleEffect(showFeedback ? 1.0 : 0.5)
                .opacity(showFeedback ? 1.0 : 0.0)
                .animation(.spring(), value: showFeedback)
        }
    }
    
    // MARK: - Completion View
    private var completionView: some View {
        VStack(spacing: 20) {
            Text("Memory Challenge Complete!")
                .font(.title2)
                .fontWeight(.bold)
                .foregroundColor(.white)
                .multilineTextAlignment(.center)
            
            Spacer().frame(height: 32)
            
            VStack(spacing: 16) {
                Text("Final Results")
                    .font(.headline)
                    .fontWeight(.bold)
                    .foregroundColor(.white)
                
                HStack {
                    VStack {
                        Text("\(currentScore)")
                            .font(.title)
                            .fontWeight(.bold)
                            .foregroundColor(Color(red: 0.07, green: 0.73, blue: 0.51))
                        Text("Session Score")
                            .font(.caption)
                            .foregroundColor(.white)
                    }
                    
                    Spacer()
                    
                    VStack {
                        Text("\(totalScore)")
                            .font(.title)
                            .fontWeight(.bold)
                            .foregroundColor(.blue)
                        Text("Total Score")
                            .font(.caption)
                            .foregroundColor(.white)
                    }
                }
                
                HStack {
                    VStack {
                        Text("\(correctAnswers) / \(puzzleData?.totalQuestions ?? 0)")
                            .font(.subheadline)
                            .fontWeight(.bold)
                            .foregroundColor(.white)
                        Text("Correct Answers")
                            .font(.caption)
                            .foregroundColor(.white)
                    }
                    
                    Spacer()
                    
                    VStack {
                        let percentage = puzzleData?.totalQuestions ?? 0 > 0 ? 
                            Int(Double(correctAnswers) / Double(puzzleData!.totalQuestions) * 100) : 0
                        Text("\(percentage)%")
                            .font(.subheadline)
                            .fontWeight(.bold)
                            .foregroundColor(.white)
                        Text("Accuracy")
                            .font(.caption)
                            .foregroundColor(.white)
                    }
                }
                
                Text("Difficulty: \(currentDifficultyLevel.name)")
                    .font(.subheadline)
                    .foregroundColor(.white.opacity(0.8))
                    .fontWeight(.medium)
                
                if currentStreak > 0 {
                    Text("Best Streak: \(currentStreak)")
                        .font(.subheadline)
                        .foregroundColor(.orange)
                        .fontWeight(.medium)
                }
            }
            .padding(24)
            .background(
                RoundedRectangle(cornerRadius: 12)
                    .fill(Color.white.opacity(0.1))
            )
            
            Spacer()
            
            Button(action: {
                onAnswerSubmitted(correctAnswers > 0)
                onNextPuzzle()
            }) {
                Text("CONTINUE")
                    .font(.headline)
                    .fontWeight(.bold)
                    .foregroundColor(.white)
                    .frame(maxWidth: .infinity)
                    .padding()
                    .background(Color(red: 0.07, green: 0.73, blue: 0.51))
                    .cornerRadius(12)
            }
        }
    }
    
    // MARK: - Game Logic
    private func setupGame() {
        currentDifficultyLevel = difficultyManager.getCurrentDifficulty(for: "triangleDot")
        currentHearts = currentDifficultyLevel.livesAllowed
        timeRemaining = currentDifficultyLevel.timeLimit
        
        generatePuzzleData()
        
        Task {
            competitiveInsight = await competitiveManager.getCompetitiveInsight(
                userId: "user123",
                puzzleType: "triangleDot",
                difficulty: currentDifficultyLevel.name
            )
        }
    }
    
    private func generatePuzzleData() {
        puzzleData = generateAdaptiveTriangleDotPuzzleData(currentDifficultyLevel)
        
        if let data = puzzleData {
            answerData = generateAdaptiveTriangleDotAnswers(data)
        }
    }
    
    private func generateAdaptiveTriangleDotPuzzleData(_ difficulty: DifficultyManager.DifficultyLevel) -> TriangleDotPuzzleData {
        let totalQuestions: Int = {
            switch difficulty {
            case .beginner: return 6
            case .easy: return 8
            case .medium: return 12
            case .hard: return 16
            case .expert: return 20
            }
        }()
        
        var sequence: [TriangleDotStep] = []
        
        // Generate random sequence
        for i in 0..<totalQuestions {
            sequence.append(
                TriangleDotStep(
                    step: i + 1,
                    redDotPosition: Int.random(in: 0...2),
                    isFirstStep: i == 0
                )
            )
        }
        
        let instructions = TriangleDotInstructions(
            title: "Adaptive Triangle Dot Memory",
            description: "Remember the position of the red dot in each triangle pattern and compare it to the previous one.",
            steps: [
                "Watch each triangle pattern carefully",
                "Note the position of the red dot (top, bottom-left, or bottom-right)",
                "Compare the current pattern to the previous one",
                "Tap YES if the red dot is in the same position",
                "Tap NO if the red dot moved to a different position"
            ],
            tip: "Focus on the red dot's position rather than the orange dots"
        )
        
        return TriangleDotPuzzleData(
            sequence: sequence,
            instructions: instructions,
            totalSteps: totalQuestions,
            totalQuestions: totalQuestions - 1, // -1 because first step is just shown
            difficulty: difficulty.name
        )
    }
    
    private func generateAdaptiveTriangleDotAnswers(_ puzzleData: TriangleDotPuzzleData) -> TriangleDotAnswerData {
        var correctAnswers: [Int] = []
        
        // Generate correct answers based on sequence
        for i in 1..<puzzleData.sequence.count {
            let currentPos = puzzleData.sequence[i].redDotPosition
            let previousPos = puzzleData.sequence[i - 1].redDotPosition
            
            // 1 if same position, 0 if different
            correctAnswers.append(currentPos == previousPos ? 1 : 0)
        }
        
        return TriangleDotAnswerData(
            correctAnswers: correctAnswers,
            totalQuestions: puzzleData.totalQuestions,
            maxScore: puzzleData.totalQuestions * 15 // 15 points per correct answer
        )
    }
    
    private func startGame() {
        gameState = "playing"
        sessionStartTime = Date()
        startGameTimer()
        
        // Show first step, then advance automatically
        DispatchQueue.main.asyncAfter(deadline: .now() + 2.0) {
            if currentQuestionIndex == 0 && !skipFirstComparisonDone {
                currentQuestionIndex += 1
                skipFirstComparisonDone = true
            }
        }
    }
    
    private func handleAnswer(_ isSame: Bool) {
        guard currentQuestionIndex >= 1,
              let answers = answerData,
              currentQuestionIndex - 1 < answers.correctAnswers.count else { return }
        
        let userAnswer = isSame ? 1 : 0
        let correctAnswerIndex = currentQuestionIndex - 1
        let correctAnswer = answers.correctAnswers[correctAnswerIndex]
        let isCorrect = userAnswer == correctAnswer
        
        userAnswers.append(userAnswer)
        lastAnswerCorrect = isCorrect
        totalAnswers += 1
        
        if isCorrect {
            correctAnswers += 1
            currentStreak += 1
            let questionScore = calculateScore(
                isCorrect: true,
                questionIndex: currentQuestionIndex,
                timeRemaining: timeRemaining
            )
            currentScore += questionScore
            totalScore += questionScore
        } else {
            currentStreak = 0
            currentHearts = max(0, currentHearts - 1)
            
            if currentHearts <= 0 {
                gameState = "completed"
                gamesPlayedThisSession += 1
                
                recordPerformance(
                    correctCount: correctAnswers,
                    totalQuestions: currentQuestionIndex,
                    avgResponseTime: 3.0,
                    streak: 0,
                    livesRemaining: 0
                )
                return
            }
        }
        
        showFeedback = true
        
        DispatchQueue.main.asyncAfter(deadline: .now() + 1.5) {
            showFeedback = false
            
            if let data = puzzleData, currentQuestionIndex < data.totalQuestions - 1 {
                currentQuestionIndex += 1
            } else {
                gameState = "completed"
                gamesPlayedThisSession += 1
                
                recordPerformance(
                    correctCount: correctAnswers,
                    totalQuestions: puzzleData?.totalQuestions ?? 0,
                    avgResponseTime: 2.0,
                    streak: currentStreak,
                    livesRemaining: currentHearts
                )
            }
        }
    }
    
    private func calculateScore(isCorrect: Bool, questionIndex: Int, timeRemaining: Int) -> Int {
        guard isCorrect else { return 0 }
        
        let baseScore = currentDifficultyLevel.basePoints
        let difficultyMultiplier = currentDifficultyLevel.rawValue + 1
        let timeBonus = max(0, timeRemaining) * 2
        let questionBonus = questionIndex * 5 // More points for later questions
        
        return baseScore * difficultyMultiplier + timeBonus + questionBonus
    }
    
    private func recordPerformance(
        correctCount: Int,
        totalQuestions: Int,
        avgResponseTime: Float,
        streak: Int,
        livesRemaining: Int
    ) {
        if let config = difficultyManager.recordPerformance(
            puzzleType: "triangledotmemory",
            isCorrect: correctCount > 0,
            timeSpent: TimeInterval(avgResponseTime),
            difficulty: currentDifficultyLevel,
            streak: streak,
            livesRemaining: livesRemaining,
            gameScore: totalScore,
            challengesCompleted: totalQuestions
        ) {
            if config.shouldNotify && config.level != currentDifficultyLevel {
                adaptationInfo = config
                currentDifficultyLevel = config.level
                showAdaptationNotification = true
            }
        }
    }
    
    private func startGameTimer() {
        Timer.scheduledTimer(withTimeInterval: 1.0, repeats: true) { timer in
            if gameState == "playing" && timeRemaining > 0 {
                timeRemaining -= 1
            } else if timeRemaining <= 0 {
                timer.invalidate()
                gameState = "completed"
            }
        }
    }
    
    private func formatTime(_ seconds: Int) -> String {
        return String(format: "%d:%02d", seconds / 60, seconds % 60)
    }
}

// MARK: - Triangle Dot Pattern View
struct TriangleDotPatternView: View {
    let redDotPosition: Int  // 0: top, 1: bottom-left, 2: bottom-right
    
    var body: some View {
        ZStack {
            // Triangle outline
            DotTriangleShape()
                .stroke(Color.black, lineWidth: 3)
            
            // Orange dots at all positions
            Circle()
                .fill(Color.orange)
                .frame(width: 12, height: 12)
                .position(topPosition)
            
            Circle()
                .fill(Color.orange)
                .frame(width: 12, height: 12)
                .position(bottomLeftPosition)
            
            Circle()
                .fill(Color.orange)
                .frame(width: 12, height: 12)
                .position(bottomRightPosition)
            
            // Red dot at specified position
            Circle()
                .fill(Color.red)
                .frame(width: 16, height: 16)
                .position(redDotPos)
        }
    }
    
    private var topPosition: CGPoint {
        CGPoint(x: 80, y: 25)
    }
    
    private var bottomLeftPosition: CGPoint {
        CGPoint(x: 40, y: 125)
    }
    
    private var bottomRightPosition: CGPoint {
        CGPoint(x: 120, y: 125)
    }
    
    private var redDotPos: CGPoint {
        switch redDotPosition {
        case 0: return topPosition
        case 1: return bottomLeftPosition
        case 2: return bottomRightPosition
        default: return topPosition
        }
    }
}

// MARK: - Triangle Shape
struct DotTriangleShape: Shape {
    func path(in rect: CGRect) -> Path {
        var path = Path()
        
        let topPoint = CGPoint(x: rect.midX, y: rect.minY + 20)
        let bottomLeftPoint = CGPoint(x: rect.minX + 20, y: rect.maxY - 20)
        let bottomRightPoint = CGPoint(x: rect.maxX - 20, y: rect.maxY - 20)
        
        path.move(to: topPoint)
        path.addLine(to: bottomLeftPoint)
        path.addLine(to: bottomRightPoint)
        path.addLine(to: topPoint)
        
        return path
    }
}

// MARK: - Session Completion Integration
extension AdaptiveTriangleDotMemoryPuzzleView {
    private var sessionCompletionHandler: some View {
        Group {
            if gameState == "completed" {
                UnifiedSessionCompletionHandler(
                    puzzleType: "triangleDot",
                    sessionScore: totalScore,
                    sessionStats: SessionStatistics(
                        correctAnswers: correctAnswers,
                        totalAnswers: totalAnswers,
                        totalTimeSeconds: Int(Date().timeIntervalSince(sessionStartTime)),
                        bestStreak: currentStreak,
                        currentStreak: currentStreak,
                        totalScore: totalScore,
                        individualTimes: [],
                        puzzleType: "triangleDot"
                    ),
                    currentDifficulty: currentDifficultyLevel
                ) { result in
                    // Session completion handled
                }
            }
        }
    }
}
