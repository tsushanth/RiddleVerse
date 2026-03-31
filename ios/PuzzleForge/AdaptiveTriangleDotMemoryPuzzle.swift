//
//  AdaptiveTriangleDotMemoryPuzzle.swift
//  PuzzleForge
//
//  iOS implementation of Adaptive Triangle Dot Memory Game
//

import SwiftUI
import FirebaseAuth

// MARK: - Data Models

struct TriangleDotMemoryStep: Identifiable {
    let id = UUID()
    let step: Int
    let redDotPosition: Int // 0 = top, 1 = bottom-left, 2 = bottom-right
    let isFirstStep: Bool
}

struct TriangleDotMemoryPuzzleData {
    let sequence: [TriangleDotMemoryStep]
    let instructions: TriangleDotMemoryInstructions
    let totalSteps: Int
    let totalQuestions: Int
    let difficulty: String
}

struct TriangleDotMemoryInstructions {
    let title: String
    let description: String
    let steps: [String]
    let tip: String
}

struct TriangleDotMemoryAnswerData {
    let correctAnswers: [Int]
    let totalQuestions: Int
    let maxScore: Int
}

// MARK: - Difficulty Level (Simplified from Android DifficultyManager)

struct TriangleDotMemoryDifficultyLevel {
    let name: String
    let index: Int
    let description: String
    let timeLimit: Int
    let livesAllowed: Int
    
    static let beginner = TriangleDotMemoryDifficultyLevel(
        name: "Beginner",
        index: 0,
        description: "Perfect for learning",
        timeLimit: 120,
        livesAllowed: 5
    )
    
    static let easy = TriangleDotMemoryDifficultyLevel(
        name: "Easy",
        index: 1,
        description: "Casual practice",
        timeLimit: 100,
        livesAllowed: 4
    )
    
    static let medium = TriangleDotMemoryDifficultyLevel(
        name: "Medium",
        index: 2,
        description: "Balanced challenge",
        timeLimit: 80,
        livesAllowed: 3
    )
    
    static let hard = TriangleDotMemoryDifficultyLevel(
        name: "Hard",
        index: 3,
        description: "Expert level",
        timeLimit: 60,
        livesAllowed: 2
    )
    
    static let expert = TriangleDotMemoryDifficultyLevel(
        name: "Expert",
        index: 4,
        description: "Master challenge",
        timeLimit: 45,
        livesAllowed: 1
    )
    
    static func fromString(_ difficulty: String) -> TriangleDotMemoryDifficultyLevel {
        switch difficulty.lowercased() {
        case "beginner": return .beginner
        case "easy": return .easy
        case "medium": return .medium
        case "hard": return .hard
        case "expert": return .expert
        default: return .medium
        }
    }
}

// MARK: - Triangle Dot Pattern View

struct TriangleDotMemoryPattern: View {
    let redDotPosition: Int
    
    var body: some View {
        GeometryReader { geometry in
            let size = min(geometry.size.width, geometry.size.height)
            let centerX = geometry.size.width / 2
            let centerY = geometry.size.height / 2
            
            // Triangle points
            let topPoint = CGPoint(x: centerX, y: centerY - size * 0.35)
            let bottomLeft = CGPoint(x: centerX - size * 0.35, y: centerY + size * 0.25)
            let bottomRight = CGPoint(x: centerX + size * 0.35, y: centerY + size * 0.25)
            
            ZStack {
                // Draw triangle outline
                Path { path in
                    path.move(to: topPoint)
                    path.addLine(to: bottomLeft)
                    path.addLine(to: bottomRight)
                    path.closeSubpath()
                }
                .stroke(Color.blue.opacity(0.3), lineWidth: 3)
                
                // Orange dots at each vertex
                Circle()
                    .fill(Color.orange)
                    .frame(width: size * 0.12, height: size * 0.12)
                    .position(topPoint)
                
                Circle()
                    .fill(Color.orange)
                    .frame(width: size * 0.12, height: size * 0.12)
                    .position(bottomLeft)
                
                Circle()
                    .fill(Color.orange)
                    .frame(width: size * 0.12, height: size * 0.12)
                    .position(bottomRight)
                
                // Red dot at selected position
                Circle()
                    .fill(Color.red)
                    .frame(width: size * 0.15, height: size * 0.15)
                    .position(redDotPosition == 0 ? topPoint :
                             redDotPosition == 1 ? bottomLeft : bottomRight)
            }
        }
    }
}

// MARK: - Main Game Screen

struct AdaptiveTriangleDotMemoryPuzzleScreen: View {
    let initialDifficulty: String
    let onComplete: (Bool, Int) -> Void
    let onBack: () -> Void
    
    @State private var currentDifficultyLevel: TriangleDotMemoryDifficultyLevel
    @State private var puzzleData: TriangleDotMemoryPuzzleData
    @State private var answerData: TriangleDotMemoryAnswerData
    
    @State private var gameState: GameState = .instructions
    @State private var currentQuestionIndex = 0
    @State private var currentScore = 0
    @State private var totalScore = 0
    @State private var showFeedback = false
    @State private var lastAnswerCorrect = false
    @State private var userAnswers: [Int] = []
    @State private var skipFirstComparisonDone = false
    @State private var currentStreak = 0
    @State private var gamesPlayedThisSession = 0
    
    @State private var timeRemaining: Int
    @State private var currentHearts: Int
    @State private var timer: Timer?
    
    @State private var sessionStartTime = Date()
    @State private var correctAnswers = 0
    @State private var totalAnswers = 0
    
    enum GameState {
        case instructions
        case playing
        case completed
    }
    
    init(initialDifficulty: String, onComplete: @escaping (Bool, Int) -> Void, onBack: @escaping () -> Void) {
        self.initialDifficulty = initialDifficulty
        self.onComplete = onComplete
        self.onBack = onBack
        
        let difficulty = TriangleDotMemoryDifficultyLevel.fromString(initialDifficulty)
        _currentDifficultyLevel = State(initialValue: difficulty)
        
        let puzzle = Self.generatePuzzleData(difficulty: difficulty)
        _puzzleData = State(initialValue: puzzle)
        _answerData = State(initialValue: Self.generateAnswerData(puzzleData: puzzle))
        
        _timeRemaining = State(initialValue: difficulty.timeLimit)
        _currentHearts = State(initialValue: difficulty.livesAllowed)
    }
    
    var body: some View {
        ZStack {
            Color(red: 0.12, green: 0.23, blue: 0.54)
                .ignoresSafeArea()
            
            VStack(spacing: 0) {
                // Top Bar
                if gameState != .instructions {
                    topBar
                        .padding(.horizontal)
                        .padding(.top, 16)
                }
                
                // Main Content
                ScrollView {
                    switch gameState {
                    case .instructions:
                        instructionsView
                            .padding()
                    case .playing:
                        gamePlayView
                            .padding()
                    case .completed:
                        completionView
                            .padding()
                    }
                }
            }
        }
        .navigationBarHidden(true)
        .onAppear {
            sessionStartTime = Date()
        }
        .onDisappear {
            timer?.invalidate()
        }
    }
    
    // MARK: - Top Bar
    
    private var topBar: some View {
        VStack(spacing: 12) {
            HStack {
                // Back button
                Button(action: onBack) {
                    ZStack {
                        RoundedRectangle(cornerRadius: 8)
                            .fill(Color(red: 0.23, green: 0.51, blue: 0.96))
                            .frame(width: 48, height: 48)
                        
                        Text("||")
                            .font(.system(size: 20, weight: .bold))
                            .foregroundColor(.white)
                    }
                }
                
                VStack(alignment: .leading, spacing: 2) {
                    Text("Level \(currentQuestionIndex + 1)")
                        .font(.system(size: 14, weight: .medium))
                        .foregroundColor(.white)
                    
                    Text(currentDifficultyLevel.name.uppercased())
                        .font(.system(size: 12, weight: .bold))
                        .foregroundColor(.cyan)
                }
                
                Spacer()
                
                // Timer and Score
                HStack(spacing: 12) {
                    // Timer
                    HStack(spacing: 4) {
                        Text("TIME")
                            .font(.system(size: 12, weight: .bold))
                        Text(formatTime(timeRemaining))
                            .font(.system(size: 14, weight: .bold))
                    }
                    .padding(.horizontal, 12)
                    .padding(.vertical, 6)
                    .background(Color.white.opacity(0.9))
                    .foregroundColor(Color(red: 0.12, green: 0.23, blue: 0.54))
                    .cornerRadius(8)
                    
                    // Score
                    if totalScore > 0 {
                        HStack(spacing: 4) {
                            Text("SCORE")
                                .font(.system(size: 12, weight: .bold))
                            Text("\(totalScore)")
                                .font(.system(size: 14, weight: .bold))
                        }
                        .padding(.horizontal, 12)
                        .padding(.vertical, 6)
                        .background(Color.white.opacity(0.9))
                        .foregroundColor(Color(red: 0.12, green: 0.23, blue: 0.54))
                        .cornerRadius(8)
                    }
                }
            }
            
            // Lives
            HStack(spacing: 4) {
                ForEach(0..<currentDifficultyLevel.livesAllowed, id: \.self) { index in
                    Text(index < currentHearts ? "❤️" : "🤍")
                        .font(.system(size: 20))
                }
            }
        }
    }
    
    // MARK: - Instructions View
    
    private var instructionsView: some View {
        VStack(spacing: 24) {
            Text(puzzleData.instructions.title)
                .font(.system(size: 28, weight: .bold))
                .foregroundColor(.white)
                .multilineTextAlignment(.center)
            
            Text(currentDifficultyLevel.description)
                .font(.system(size: 16, weight: .medium))
                .foregroundColor(.cyan)
                .multilineTextAlignment(.center)
            
            Text(puzzleData.instructions.description)
                .font(.system(size: 16))
                .foregroundColor(.white.opacity(0.9))
                .multilineTextAlignment(.center)
            
            // Example Pattern
            VStack(spacing: 16) {
                Text("Example Pattern")
                    .font(.system(size: 18, weight: .bold))
                    .foregroundColor(Color(red: 0.12, green: 0.23, blue: 0.54))
                
                TriangleDotMemoryPattern(redDotPosition: 1)
                    .frame(width: 120, height: 120)
                
                Text("Red dot at bottom-left")
                    .font(.system(size: 12))
                    .foregroundColor(Color(red: 0.12, green: 0.23, blue: 0.54))
            }
            .padding()
            .background(Color.white.opacity(0.9))
            .cornerRadius(16)
            
            // Instructions List
            VStack(alignment: .leading, spacing: 12) {
                ForEach(puzzleData.instructions.steps, id: \.self) { step in
                    HStack(alignment: .top, spacing: 8) {
                        Text("•")
                            .font(.system(size: 14))
                            .foregroundColor(Color(red: 0.12, green: 0.23, blue: 0.54))
                        Text(step)
                            .font(.system(size: 14))
                            .foregroundColor(Color(red: 0.12, green: 0.23, blue: 0.54))
                    }
                }
                
                Divider()
                    .background(Color(red: 0.12, green: 0.23, blue: 0.54).opacity(0.3))
                
                HStack(alignment: .top, spacing: 8) {
                    Text("💡")
                        .font(.system(size: 14))
                    Text(puzzleData.instructions.tip)
                        .font(.system(size: 14, weight: .medium))
                        .foregroundColor(Color(red: 0.12, green: 0.23, blue: 0.54))
                }
                
                Divider()
                    .background(Color(red: 0.12, green: 0.23, blue: 0.54).opacity(0.3))
                
                Text("⚡ Difficulty: \(currentDifficultyLevel.name) • Time Limit: \(currentDifficultyLevel.timeLimit)s")
                    .font(.system(size: 12, weight: .medium))
                    .foregroundColor(.gray)
            }
            .padding()
            .background(Color.white.opacity(0.9))
            .cornerRadius(16)
            
            // Start Button
            Button(action: startGame) {
                Text("START GAME")
                    .font(.system(size: 18, weight: .bold))
                    .foregroundColor(.white)
                    .frame(maxWidth: .infinity)
                    .frame(height: 56)
                    .background(Color(red: 0.06, green: 0.73, blue: 0.51))
                    .cornerRadius(12)
            }
            .padding(.top, 16)
        }
    }
    
    // MARK: - Game Play View
    
    private var gamePlayView: some View {
        VStack(spacing: 32) {
            // Progress Card
            HStack {
                ZStack {
                    Circle()
                        .fill(Color(red: 0.06, green: 0.73, blue: 0.51))
                        .frame(width: 48, height: 48)
                    
                    Text("\(currentQuestionIndex + 1)")
                        .font(.system(size: 18, weight: .bold))
                        .foregroundColor(.white)
                }
                
                VStack {
                    Text("\(currentQuestionIndex + 1) / \(puzzleData.totalQuestions)")
                        .font(.system(size: 14, weight: .medium))
                        .foregroundColor(.white)
                    
                    Text(currentDifficultyLevel.name)
                        .font(.system(size: 12, weight: .bold))
                        .foregroundColor(.cyan)
                }
                
                Spacer()
                
                // Progress Bar
                GeometryReader { geometry in
                    ZStack(alignment: .leading) {
                        RoundedRectangle(cornerRadius: 3)
                            .fill(Color.white.opacity(0.3))
                            .frame(width: 80, height: 6)
                        
                        RoundedRectangle(cornerRadius: 3)
                            .fill(Color(red: 0.06, green: 0.73, blue: 0.51))
                            .frame(
                                width: 80 * CGFloat(currentQuestionIndex + 1) / CGFloat(puzzleData.totalQuestions),
                                height: 6
                            )
                    }
                }
                .frame(width: 80, height: 6)
            }
            .padding()
            .background(Color.white.opacity(0.1))
            .cornerRadius(12)
            
            // Triangle Pattern
            ZStack {
                RoundedRectangle(cornerRadius: 16)
                    .fill(Color.white)
                    .frame(width: 240, height: 240)
                
                TriangleDotMemoryPattern(redDotPosition: puzzleData.sequence[currentQuestionIndex].redDotPosition)
                    .frame(width: 160, height: 160)
            }
            .animation(.easeInOut(duration: 0.3), value: currentQuestionIndex)
            
            if currentQuestionIndex >= 1 {
                Text("Does this pattern match the\nprevious pattern?")
                    .font(.system(size: 18, weight: .medium))
                    .foregroundColor(.white)
                    .multilineTextAlignment(.center)
                    .lineSpacing(4)
            }
            
            Spacer()
            
            // Feedback Animation
            if showFeedback {
                ZStack {
                    Circle()
                        .fill(lastAnswerCorrect ? Color(red: 0.06, green: 0.73, blue: 0.51) : Color(red: 0.94, green: 0.27, blue: 0.27))
                        .frame(width: 120, height: 120)
                    
                    Text(lastAnswerCorrect ? "✓" : "✗")
                        .font(.system(size: 60, weight: .bold))
                        .foregroundColor(.white)
                }
                .transition(.scale.combined(with: .opacity))
            }
            
            // Answer Buttons
            if !showFeedback && currentQuestionIndex >= 1 {
                HStack(spacing: 16) {
                    Button(action: { submitAnswer(false) }) {
                        Text("NO")
                            .font(.system(size: 20, weight: .bold))
                            .foregroundColor(.white)
                            .frame(maxWidth: .infinity)
                            .frame(height: 64)
                            .background(Color(red: 0.23, green: 0.51, blue: 0.96))
                            .cornerRadius(12)
                    }
                    
                    Button(action: { submitAnswer(true) }) {
                        Text("YES")
                            .font(.system(size: 20, weight: .bold))
                            .foregroundColor(.white)
                            .frame(maxWidth: .infinity)
                            .frame(height: 64)
                            .background(Color(red: 0.23, green: 0.51, blue: 0.96))
                            .cornerRadius(12)
                    }
                }
            }
        }
    }
    
    // MARK: - Completion View
    
    private var completionView: some View {
        VStack(spacing: 32) {
            Text("Memory Challenge Complete!")
                .font(.system(size: 28, weight: .bold))
                .foregroundColor(.white)
                .multilineTextAlignment(.center)
            
            VStack(spacing: 24) {
                Text("Final Results")
                    .font(.system(size: 18, weight: .bold))
                    .foregroundColor(Color(red: 0.12, green: 0.23, blue: 0.54))
                
                HStack(spacing: 32) {
                    VStack(spacing: 8) {
                        Text("\(currentScore)")
                            .font(.system(size: 24, weight: .bold))
                            .foregroundColor(Color(red: 0.06, green: 0.73, blue: 0.51))
                        
                        Text("Session Score")
                            .font(.system(size: 12))
                            .foregroundColor(Color(red: 0.12, green: 0.23, blue: 0.54))
                    }
                    
                    VStack(spacing: 8) {
                        Text("\(totalScore)")
                            .font(.system(size: 24, weight: .bold))
                            .foregroundColor(Color(red: 0.13, green: 0.59, blue: 0.95))
                        
                        Text("Total Score")
                            .font(.system(size: 12))
                            .foregroundColor(Color(red: 0.12, green: 0.23, blue: 0.54))
                    }
                }
                
                Divider()
                
                HStack(spacing: 32) {
                    VStack(spacing: 8) {
                        Text("\(correctAnswers) / \(puzzleData.totalQuestions)")
                            .font(.system(size: 16, weight: .bold))
                            .foregroundColor(Color(red: 0.12, green: 0.23, blue: 0.54))
                        
                        Text("Correct Answers")
                            .font(.system(size: 12))
                            .foregroundColor(Color(red: 0.12, green: 0.23, blue: 0.54))
                    }
                    
                    VStack(spacing: 8) {
                        let percentage = (Double(correctAnswers) / Double(puzzleData.totalQuestions) * 100).rounded()
                        Text("\(Int(percentage))%")
                            .font(.system(size: 16, weight: .bold))
                            .foregroundColor(Color(red: 0.12, green: 0.23, blue: 0.54))
                        
                        Text("Accuracy")
                            .font(.system(size: 12))
                            .foregroundColor(Color(red: 0.12, green: 0.23, blue: 0.54))
                    }
                }
                
                Divider()
                
                VStack(spacing: 8) {
                    Text("Difficulty: \(currentDifficultyLevel.name)")
                        .font(.system(size: 14, weight: .medium))
                        .foregroundColor(.gray)
                    
                    if currentStreak > 0 {
                        Text("Best Streak: \(currentStreak)")
                            .font(.system(size: 14, weight: .medium))
                            .foregroundColor(Color(red: 1.0, green: 0.44, blue: 0.0))
                    }
                }
            }
            .padding(24)
            .background(Color.white.opacity(0.9))
            .cornerRadius(16)
            
            Spacer()
            
            Button(action: completeGame) {
                Text("CONTINUE")
                    .font(.system(size: 18, weight: .bold))
                    .foregroundColor(.white)
                    .frame(maxWidth: .infinity)
                    .frame(height: 56)
                    .background(Color(red: 0.06, green: 0.73, blue: 0.51))
                    .cornerRadius(12)
            }
        }
    }
    
    // MARK: - Game Logic
    
    private func startGame() {
        gameState = .playing
        sessionStartTime = Date()
        startTimer()
        
        // Show first pattern for 2 seconds before starting comparisons
        DispatchQueue.main.asyncAfter(deadline: .now() + 2.0) {
            if currentQuestionIndex == 0 && !skipFirstComparisonDone {
                currentQuestionIndex = 1
                skipFirstComparisonDone = true
            }
        }
    }
    
    private func startTimer() {
        timer?.invalidate()
        timer = Timer.scheduledTimer(withTimeInterval: 1.0, repeats: true) { _ in
            if timeRemaining > 0 && gameState == .playing {
                timeRemaining -= 1
            } else if timeRemaining <= 0 {
                gameState = .completed
                timer?.invalidate()
            }
        }
    }
    
    private func submitAnswer(_ isSame: Bool) {
        guard currentQuestionIndex >= 1 else { return }
        
        let userAnswer = isSame ? 1 : 0
        let correctAnswerIndex = currentQuestionIndex - 1
        
        guard correctAnswerIndex < answerData.correctAnswers.count else { return }
        
        let correctAnswer = answerData.correctAnswers[correctAnswerIndex]
        let isCorrect = userAnswer == correctAnswer
        
        userAnswers.append(userAnswer)
        lastAnswerCorrect = isCorrect
        totalAnswers += 1
        
        if isCorrect {
            correctAnswers += 1
            currentStreak += 1
            let questionScore = calculateScore(isCorrect: true, questionIndex: currentQuestionIndex)
            currentScore += questionScore
            totalScore += questionScore
        } else {
            currentStreak = 0
            currentHearts = max(0, currentHearts - 1)
            
            if currentHearts <= 0 {
                gameState = .completed
                timer?.invalidate()
                return
            }
        }
        
        withAnimation {
            showFeedback = true
        }
        
        DispatchQueue.main.asyncAfter(deadline: .now() + 1.5) {
            withAnimation {
                showFeedback = false
            }
            
            if currentQuestionIndex < puzzleData.totalQuestions {
                currentQuestionIndex += 1
            } else {
                gameState = .completed
                timer?.invalidate()
            }
        }
    }
    
    private func calculateScore(isCorrect: Bool, questionIndex: Int) -> Int {
        guard isCorrect else { return 0 }
        
        let baseScore = 15
        let timeBonus = min(5, timeRemaining / 10)
        let streakBonus = min(10, currentStreak * 2)
        
        return baseScore + timeBonus + streakBonus
    }
    
    private func completeGame() {
        let finalScore = (Double(currentScore) / Double(answerData.maxScore)) * 100
        onComplete(finalScore >= 60, totalScore)
    }
    
    private func formatTime(_ seconds: Int) -> String {
        let minutes = seconds / 60
        let secs = seconds % 60
        return String(format: "%d:%02d", minutes, secs)
    }
    
    // MARK: - Static Helper Functions
    
    static func generatePuzzleData(difficulty: TriangleDotMemoryDifficultyLevel) -> TriangleDotMemoryPuzzleData {
        let totalQuestions: Int
        switch difficulty.index {
        case 0: totalQuestions = 6
        case 1: totalQuestions = 8
        case 2: totalQuestions = 12
        case 3: totalQuestions = 16
        case 4: totalQuestions = 20
        default: totalQuestions = 8
        }
        
        var sequence: [TriangleDotMemoryStep] = []
        for i in 0..<totalQuestions {
            sequence.append(
                TriangleDotMemoryStep(
                    step: i + 1,
                    redDotPosition: Int.random(in: 0...2),
                    isFirstStep: i == 0
                )
            )
        }
        
        let instructions = TriangleDotMemoryInstructions(
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
        
        return TriangleDotMemoryPuzzleData(
            sequence: sequence,
            instructions: instructions,
            totalSteps: totalQuestions,
            totalQuestions: totalQuestions - 1,
            difficulty: difficulty.name
        )
    }
    
    static func generateAnswerData(puzzleData: TriangleDotMemoryPuzzleData) -> TriangleDotMemoryAnswerData {
        var correctAnswers: [Int] = []
        
        for i in 1..<puzzleData.sequence.count {
            let currentPos = puzzleData.sequence[i].redDotPosition
            let previousPos = puzzleData.sequence[i - 1].redDotPosition
            correctAnswers.append(currentPos == previousPos ? 1 : 0)
        }
        
        return TriangleDotMemoryAnswerData(
            correctAnswers: correctAnswers,
            totalQuestions: puzzleData.totalQuestions,
            maxScore: puzzleData.totalQuestions * 15
        )
    }
}

// MARK: - HomeView Integration Extension

extension HomeView {
    
    @ViewBuilder
    var triangleDotMemoryPuzzleDestination: some View {
        if let puzzle = selectedPuzzle {
            AdaptiveTriangleDotMemoryPuzzleScreen(
                initialDifficulty: puzzle.difficulty ?? "Medium",
                onComplete: { success, score in
                    currentScore = score
                    handlePuzzleCompletion(for: puzzle)
                },
                onBack: {
                    // Handle back navigation
                    if isInActiveSession {
                        // Don't clear puzzle if in active session
                        navigationState = .none
                    } else {
                        selectedPuzzle = nil
                        navigationState = .none
                    }
                }
            )
        } else {
            Text("Loading puzzle...")
                .foregroundColor(.white)
        }
    }
}


// MARK: - Local Puzzle Generator Integration

extension LocalMemoryPuzzleGenerator {
    
    static func generateTriangleDotMemory(difficulty: String) -> (String, String) {
        // Generate puzzle data as JSON string
        let difficultyLevel = TriangleDotMemoryDifficultyLevel.fromString(difficulty)
        let puzzleData = AdaptiveTriangleDotMemoryPuzzleScreen.generatePuzzleData(difficulty: difficultyLevel)
        let answerData = AdaptiveTriangleDotMemoryPuzzleScreen.generateAnswerData(puzzleData: puzzleData)
        
        // Convert to JSON strings
        let questionJson: [String: Any] = [
            "puzzleType": "triangleDotMemory",
            "difficulty": difficulty,
            "totalQuestions": puzzleData.totalQuestions,
            "timeLimit": difficultyLevel.timeLimit
        ]
        
        let answerJson: [String: Any] = [
            "correctAnswers": answerData.correctAnswers,
            "maxScore": answerData.maxScore
        ]
        
        let questionData = try? JSONSerialization.data(withJSONObject: questionJson)
        let answerDataJson = try? JSONSerialization.data(withJSONObject: answerJson)
        
        let questionString = questionData.flatMap { String(data: $0, encoding: .utf8) } ?? "{}"
        let answerString = answerDataJson.flatMap { String(data: $0, encoding: .utf8) } ?? "{}"
        
        return (questionString, answerString)
    }
}

// MARK: - Integration Instructions

/*
 HOW TO INTEGRATE INTO HOMEVIEW:
 
 1. Add to PuzzleNavigationType enum (in your existing enum definition):
    case triangleDotMemory
 
 2. Add to your HomeView's isLocalMemoryPuzzle function:
    case "triangledotmemory", "triangle_dot_memory":
        return true
 
 3. Add to your HomeView's generateLocalMemoryData function:
    case "triangledotmemory", "triangle_dot_memory":
        return LocalMemoryPuzzleGenerator.generateTriangleDotMemory(difficulty: difficulty)
 
 4. Add to your HomeView's fetchLocalMemoryPuzzle function's navigation state section:
    else if category == "triangledotmemory" || category == "triangle_dot_memory" {
        self.navigationState = .triangleDotMemory
    }
 
 5. Add to your HomeView's puzzleDestination switch statement:
    case .triangleDotMemory:
        triangleDotMemoryPuzzleDestination
 
 6. Add to CategoryData.allCategories array:
    CategoryData(
        category: "triangledotmemory",
        title: "Triangle Dot Memory",
        subtitle: "Remember dot positions",
        icon: "triangle.fill",
        backgroundColor: .purple,
        tags: [.memory, .visual]
    )
 
 7. Add to determineNavigationType function (for custom puzzles):
    case "triangledotmemory", "triangle_dot_memory":
        return .triangleDotMemory
*/
