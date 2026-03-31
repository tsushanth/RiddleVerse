//
//  AdaptiveAveragePuzzleView.swift
//  PuzzleForge
//
//  Enhanced adaptive average puzzle with difficulty progression
//

import SwiftUI
import AVFoundation

// MARK: - Average Puzzle Difficulty Manager
class AveragePuzzleDifficultyManager: ObservableObject {
    
    struct DifficultyLevel {
        let name: String
        let numberCount: Int
        let numberRange: ClosedRange<Int>
        let timeLimit: Int
        let livesAllowed: Int
        let basePoints: Int
        let allowDecimals: Bool
        let tolerance: Double
        
        static let beginner = DifficultyLevel(name: "Beginner", numberCount: 2, numberRange: 10...50, timeLimit: 90, livesAllowed: 4, basePoints: 10, allowDecimals: false, tolerance: 1.0)
        static let easy = DifficultyLevel(name: "Easy", numberCount: 3, numberRange: 10...99, timeLimit: 75, livesAllowed: 3, basePoints: 15, allowDecimals: false, tolerance: 0.5)
        static let medium = DifficultyLevel(name: "Medium", numberCount: 4, numberRange: 20...150, timeLimit: 60, livesAllowed: 3, basePoints: 20, allowDecimals: true, tolerance: 0.5)
        static let hard = DifficultyLevel(name: "Hard", numberCount: 5, numberRange: 50...300, timeLimit: 45, livesAllowed: 2, basePoints: 25, allowDecimals: true, tolerance: 0.25)
        static let expert = DifficultyLevel(name: "Expert", numberCount: 6, numberRange: 100...500, timeLimit: 30, livesAllowed: 2, basePoints: 30, allowDecimals: true, tolerance: 0.25)
        static let master = DifficultyLevel(name: "Master", numberCount: 7, numberRange: 200...999, timeLimit: 20, livesAllowed: 1, basePoints: 40, allowDecimals: true, tolerance: 0.1)
        
        static let allLevels = [beginner, easy, medium, hard, expert, master]
    }
    
    struct PlayerPerformance: Codable {
        let accuracy: Float
        let timeSpent: Float
        let streakLength: Int
        let livesRemaining: Int
        let gameScore: Int
        let difficulty: String
        let averageComplexity: Float // Based on number count and range
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
                adjustmentReason: "Gathering performance data...",
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
        
        var newLevel = currentDifficulty
        var reason = "Performance stable at current level"
        var confidence: Float = 0.3
        
        // Check for advancement
        if avgAccuracy >= 0.85 && consistentSuccess && avgTime < Float(currentDifficulty.timeLimit) * 0.5 && currentIndex < DifficultyLevel.allLevels.count - 1 {
            newLevel = DifficultyLevel.allLevels[currentIndex + 1]
            reason = "Excellent speed and accuracy - advancing to \(newLevel.name)"
            confidence = 0.8
        }
        // Check for reduction
        else if avgAccuracy <= 0.3 && consistentFailure && currentIndex > 0 {
            newLevel = DifficultyLevel.allLevels[currentIndex - 1]
            reason = "Building confidence at \(newLevel.name) level"
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
        if let savedName = UserDefaults.standard.string(forKey: "average_puzzle_difficulty"),
           let level = DifficultyLevel.allLevels.first(where: { $0.name == savedName }) {
            currentDifficulty = level
        }
        loadPerformanceHistory()
    }
    
    private func saveDifficulty() {
        UserDefaults.standard.set(currentDifficulty.name, forKey: "average_puzzle_difficulty")
    }
    
    private func savePerformanceHistory() {
        let encoder = JSONEncoder()
        if let encoded = try? encoder.encode(performanceHistory) {
            UserDefaults.standard.set(encoded, forKey: "average_puzzle_performance_history")
        }
    }
    
    private func loadPerformanceHistory() {
        if let data = UserDefaults.standard.data(forKey: "average_puzzle_performance_history") {
            let decoder = JSONDecoder()
            if let decoded = try? decoder.decode([PlayerPerformance].self, from: data) {
                performanceHistory = decoded
            }
        }
    }
}

// MARK: - Enhanced Average Puzzle Data
struct EnhancedAveragePuzzleData {
    let numbers: [Int]
    let average: Double
    let difficulty: AveragePuzzleDifficultyManager.DifficultyLevel
    let hint: String
    let tolerance: Double
    
    init(difficultyLevel: AveragePuzzleDifficultyManager.DifficultyLevel) {
        self.difficulty = difficultyLevel
        
        // Generate numbers based on difficulty
        var generatedNumbers: [Int] = []
        for _ in 0..<difficultyLevel.numberCount {
            generatedNumbers.append(Int.random(in: difficultyLevel.numberRange))
        }
        
        self.numbers = generatedNumbers
        self.average = Double(generatedNumbers.reduce(0, +)) / Double(generatedNumbers.count)
        self.tolerance = difficultyLevel.tolerance
        
        // Generate contextual hint
        if difficultyLevel.numberCount <= 3 {
            self.hint = "Add all numbers and divide by \(difficultyLevel.numberCount)"
        } else {
            self.hint = "Find the sum of all \(difficultyLevel.numberCount) numbers, then divide by \(difficultyLevel.numberCount)"
        }
    }
    
    init(from originalData: AveragePuzzleData, difficultyLevel: AveragePuzzleDifficultyManager.DifficultyLevel) {
        // Use original data but apply difficulty constraints
        self.difficulty = difficultyLevel
        self.numbers = Array(originalData.numbers.prefix(difficultyLevel.numberCount))
        self.average = Double(numbers.reduce(0, +)) / Double(numbers.count)
        self.tolerance = difficultyLevel.tolerance
        self.hint = originalData.hint.isEmpty ? "Calculate the average of these numbers" : originalData.hint
    }
}

// MARK: - Average Puzzle View (Enhanced with adaptive difficulty)
struct AveragePuzzleView: View {
    let puzzle: Puzzle
    let questionIndex: Int
    let totalQuestions: Int
    let onAnswerSubmitted: (Double, Bool) -> Void
    let onNextPuzzle: () -> Void
    let onExit: () -> Void
    let onBack: () -> Void
    
    // Difficulty management
    @StateObject private var difficultyManager = AveragePuzzleDifficultyManager()
    @State private var currentPuzzleData: EnhancedAveragePuzzleData
    @State private var adaptationResult: AveragePuzzleDifficultyManager.AdaptationResult?
    @State private var showAdaptationNotification = false
    
    // Game state
    @StateObject private var feedbackManager = UnifiedFeedbackManager()
    @StateObject private var progressionManager = ProgressionManager()
    @State private var timer: PuzzleTimer
    @State private var currentInput = ""
    @State private var isAnswered = false
    @State private var currentLives: Int
    @State private var totalScore = 0
    @State private var currentStreak = 0
    @State private var gamesPlayedThisSession = 0
    
    // Performance tracking
    @State private var gameStartTime = Date()
    @State private var correctAnswers = 0
    @State private var totalAnswers = 0
    
    init(puzzle: Puzzle, questionIndex: Int, totalQuestions: Int, onAnswerSubmitted: @escaping (Double, Bool) -> Void, onNextPuzzle: @escaping () -> Void, onExit: @escaping () -> Void, onBack: @escaping () -> Void) {
        self.puzzle = puzzle
        self.questionIndex = questionIndex
        self.totalQuestions = totalQuestions
        self.onAnswerSubmitted = onAnswerSubmitted
        self.onNextPuzzle = onNextPuzzle
        self.onExit = onExit
        self.onBack = onBack
        
        // Initialize with current difficulty
        let initialDifficulty = AveragePuzzleDifficultyManager.DifficultyLevel.easy
        self._currentPuzzleData = State(initialValue: EnhancedAveragePuzzleData(difficultyLevel: initialDifficulty))
        self._currentLives = State(initialValue: initialDifficulty.livesAllowed)
        self._timer = State(initialValue: PuzzleTimer(totalTime: initialDifficulty.timeLimit))
    }
    
    var body: some View {
        GeometryReader { geometry in
            ZStack {
                // Background
                Color.black.ignoresSafeArea()
                
                // Mountain landscape background
                Canvas { context, size in
                    drawMountainLandscape(context: context, size: size)
                }
                .ignoresSafeArea()
                
                // Geometric overlay
                Canvas { context, size in
                    drawGeometricShapes(context: context, size: size)
                }
                .blur(radius: 0.5)
                .ignoresSafeArea()
                
                VStack(spacing: 0) {
                    // Enhanced top bar with adaptive difficulty
                    adaptiveTopGameBar
                        .padding(.horizontal, 16)
                    
                    // Adaptation notification
                    if showAdaptationNotification, let result = adaptationResult {
                        adaptationNotificationView(result: result)
                            .transition(.move(edge: .top).combined(with: .opacity))
                    }
                    
                    // Enhanced game info
                    if totalScore > 0 || gamesPlayedThisSession > 0 {
                        enhancedGameInfoView
                    }
                    
                    Spacer(minLength: 40)
                    
                    // Adaptive puzzle header
                    adaptivePuzzleHeader
                        .padding(.horizontal, 20)
                    
                    Spacer()
                    
                    // Calculator interface
                    adaptiveCalculatorGrid
                        .padding(.horizontal, 20)
                        .padding(.bottom, 30)
                }
            }
        }
        .withUnifiedFeedback(feedbackManager)
        .background(Color.black)
        .navigationBarHidden(true)
        .onAppear {
            setupAdaptivePuzzle()
        }
        .onDisappear {
            timer.stop()
        }
        .onChange(of: timer.timeRemaining) { newTime in
            if newTime <= 0 && !isAnswered && !feedbackManager.isShowingFeedback {
                handleTimeUp()
            }
        }
    }
    
    // MARK: - View Components
    
    private var adaptiveTopGameBar: some View {
        HStack {
            // Left side: Back button, pause, and level
            HStack(spacing: 12) {
                Button(action: onBack) {
                    Image(systemName: "arrow.left")
                        .foregroundColor(.white)
                        .font(.system(size: 24))
                }
                
                Image(systemName: "pause.fill")
                    .foregroundColor(.white.opacity(0.7))
                    .font(.system(size: 24))
                
                VStack(alignment: .leading, spacing: 4) {
                    Text("Level \(progressionManager.currentLevel.level)")
                        .font(.system(size: 20, weight: .bold))
                        .foregroundColor(.white)
                    
                    // Adaptive difficulty indicator
                    Text(currentPuzzleData.difficulty.name)
                        .font(.system(size: 12, weight: .medium))
                        .foregroundColor(.cyan)
                    
                    LevelProgressBar(level: progressionManager.currentLevel)
                        .frame(width: 120)
                }
            }
            
            Spacer()
            
            // Center: Adaptive lives display
            HStack(spacing: 4) {
                ForEach(0..<currentPuzzleData.difficulty.livesAllowed, id: \.self) { index in
                    Text(index < currentLives ? "❤️" : "🤍")
                        .font(.system(size: 16))
                }
            }
            
            Spacer()
            
            // Right side: Timer and streak
            VStack(alignment: .trailing, spacing: 4) {
                Text(timer.formattedTime)
                    .font(.system(size: 24, weight: .bold))
                    .foregroundColor(timer.timeRemaining <= 30 ? .red : .white)
                
                if currentStreak > 0 {
                    StreakDisplay(streakInfo: progressionManager.streakInfo)
                }
            }
        }
    }
    
    private func adaptationNotificationView(result: AveragePuzzleDifficultyManager.AdaptationResult) -> some View {
        HStack {
            VStack(alignment: .leading, spacing: 4) {
                HStack {
                    Image(systemName: "brain.head.profile")
                        .foregroundColor(.white)
                        .font(.caption)
                    
                    Text("Difficulty Adapted!")
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
                colors: [.blue, .purple],
                startPoint: .leading,
                endPoint: .trailing
            )
        )
        .cornerRadius(12)
        .padding(.horizontal, 20)
    }
    
    private var enhancedGameInfoView: some View {
        HStack {
            VStack(alignment: .leading) {
                Text("PUZZLE \(questionIndex + 1)/\(totalQuestions)")
                    .font(.caption)
                    .fontWeight(.bold)
                    .foregroundColor(.black)
                
                if currentStreak > 0 {
                    Text("🔥 \(currentStreak)")
                        .font(.caption2)
                        .foregroundColor(.orange)
                }
            }
            
            Spacer()
            
            VStack(alignment: .center) {
                Text("SCORE")
                    .font(.caption)
                    .fontWeight(.bold)
                    .foregroundColor(.black)
                
                Text("\(totalScore)")
                    .font(.headline)
                    .fontWeight(.bold)
                    .foregroundColor(.green)
            }
            
            Spacer()
            
            VStack(alignment: .trailing) {
                Text(currentPuzzleData.difficulty.name.uppercased())
                    .font(.caption)
                    .fontWeight(.bold)
                    .foregroundColor(.black)
                
                Text("\(currentPuzzleData.numbers.count) numbers")
                    .font(.caption2)
                    .foregroundColor(.gray)
            }
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 12)
        .background(
            RoundedRectangle(cornerRadius: 12)
                .fill(Color.white.opacity(0.9))
        )
        .padding(.horizontal, 20)
    }
    
    private var adaptivePuzzleHeader: some View {
        VStack(spacing: 30) {
            Text("FIND THE AVERAGE")
                .font(.system(size: 20, weight: .bold))
                .foregroundColor(.white.opacity(0.9))
                .tracking(1)
            
            Text(currentPuzzleData.difficulty.name)
                .font(.system(size: 14, weight: .medium))
                .foregroundColor(.cyan)
            
            // Adaptive number layout based on count
            adaptiveNumberLayout
            
            // Show hint
            if !currentPuzzleData.hint.isEmpty {
                Text("💡 \(currentPuzzleData.hint)")
                    .font(.system(size: 14, weight: .medium))
                    .foregroundColor(.white.opacity(0.7))
                    .multilineTextAlignment(.center)
                    .padding(.horizontal, 20)
            }
        }
    }
    
    @ViewBuilder
    private var adaptiveNumberLayout: some View {
        let numbers = currentPuzzleData.numbers
        
        switch numbers.count {
        case 1...3:
            // Single row
            HStack(spacing: 20) {
                ForEach(numbers, id: \.self) { number in
                    Text("\(number)")
                        .font(.system(size: 42, weight: .bold))
                        .foregroundColor(.white)
                }
            }
            
        case 4:
            // 2x2 grid
            VStack(spacing: 16) {
                HStack(spacing: 32) {
                    ForEach(numbers.prefix(2), id: \.self) { number in
                        Text("\(number)")
                            .font(.system(size: 38, weight: .bold))
                            .foregroundColor(.white)
                    }
                }
                HStack(spacing: 32) {
                    ForEach(numbers.suffix(2), id: \.self) { number in
                        Text("\(number)")
                            .font(.system(size: 38, weight: .bold))
                            .foregroundColor(.white)
                    }
                }
            }
            
        case 5...6:
            // Grid layout with 3 columns
            let rows = Int(ceil(Double(numbers.count) / 3.0))
            VStack(spacing: 12) {
                ForEach(0..<rows, id: \.self) { row in
                    HStack(spacing: 24) {
                        let startIndex = row * 3
                        let endIndex = min(startIndex + 3, numbers.count)
                        
                        ForEach(startIndex..<endIndex, id: \.self) { index in
                            Text("\(numbers[index])")
                                .font(.system(size: 32, weight: .bold))
                                .foregroundColor(.white)
                        }
                    }
                }
            }
            
        default:
            // Compact grid for 7+ numbers
            let rows = Int(ceil(Double(numbers.count) / 4.0))
            VStack(spacing: 10) {
                ForEach(0..<rows, id: \.self) { row in
                    HStack(spacing: 20) {
                        let startIndex = row * 4
                        let endIndex = min(startIndex + 4, numbers.count)
                        
                        ForEach(startIndex..<endIndex, id: \.self) { index in
                            Text("\(numbers[index])")
                                .font(.system(size: 28, weight: .bold))
                                .foregroundColor(.white)
                        }
                    }
                }
            }
        }
    }
    
    private var adaptiveCalculatorGrid: some View {
        VStack(spacing: 16) {
            // Answer display
            answerDisplay
            
            // Number grid
            numberGrid
            
            // Bottom row with adaptive decimal support
            bottomRow
            
            // Submit button
            submitButton
        }
    }
    
    private var answerDisplay: some View {
        RoundedRectangle(cornerRadius: 8)
            .stroke(Color.white.opacity(0.3), lineWidth: 2)
            .frame(height: 60)
            .overlay(
                Text(currentInput.isEmpty ? "0" : currentInput)
                    .font(.title2)
                    .fontWeight(.bold)
                    .foregroundColor(.white)
            )
    }
    
    private var numberGrid: some View {
        VStack(spacing: 16) {
            HStack(spacing: 16) {
                numberButton(1)
                numberButton(2)
                numberButton(3)
            }
            HStack(spacing: 16) {
                numberButton(4)
                numberButton(5)
                numberButton(6)
            }
            HStack(spacing: 16) {
                numberButton(7)
                numberButton(8)
                numberButton(9)
            }
        }
    }
    
    private var bottomRow: some View {
        HStack(spacing: 16) {
            clearButton
            zeroButton
            if currentPuzzleData.difficulty.allowDecimals {
                decimalButton
            } else {
                // Placeholder to maintain layout
                AverageCalculatorButton(
                    text: "",
                    action: {},
                    isDisabled: true
                )
                .opacity(0)
            }
        }
    }
    
    private var submitButton: some View {
        Button(action: submitAnswer) {
            RoundedRectangle(cornerRadius: 8)
                .fill(isInputDisabled ? Color.gray.opacity(0.3) : Color.white.opacity(0.2))
                .frame(height: 56)
                .overlay(
                    Text("SUBMIT")
                        .font(.system(size: 18, weight: .bold))
                        .foregroundColor(isInputDisabled ? .gray : .white)
                )
        }
        .disabled(isInputDisabled || currentInput.isEmpty)
    }
    
    // MARK: - Button Components
    
    private func numberButton(_ number: Int) -> some View {
        AverageCalculatorButton(
            text: "\(number)",
            action: { addNumberToInput(number) },
            isDisabled: isInputDisabled
        )
    }
    
    private var clearButton: some View {
        AverageCalculatorButton(
            text: "✕",
            action: clearInput,
            isSpecial: true,
            isDisabled: isInputDisabled
        )
    }
    
    private var zeroButton: some View {
        AverageCalculatorButton(
            text: "0",
            action: addZeroToInput,
            isDisabled: isInputDisabled
        )
    }
    
    private var decimalButton: some View {
        AverageCalculatorButton(
            text: ".",
            action: addDecimalToInput,
            isDisabled: isInputDisabled
        )
    }
    
    // MARK: - Helper Properties
    
    private var isInputDisabled: Bool {
        isAnswered || feedbackManager.isShowingFeedback
    }
    
    // MARK: - Game Logic
    
    private func setupAdaptivePuzzle() {
        let currentDifficulty = difficultyManager.currentDifficulty
        
        // Try to use original puzzle data if compatible, otherwise generate new
        if let originalData = puzzle.averagePuzzleData,
           originalData.numbers.count >= currentDifficulty.numberCount {
            currentPuzzleData = EnhancedAveragePuzzleData(from: originalData, difficultyLevel: currentDifficulty)
        } else {
            currentPuzzleData = EnhancedAveragePuzzleData(difficultyLevel: currentDifficulty)
        }
        
        currentLives = currentPuzzleData.difficulty.livesAllowed
        // Create a new timer with the correct time limit for this difficulty
        timer.stop()
        timer = PuzzleTimer(totalTime: currentPuzzleData.difficulty.timeLimit)
        timer.start()
        gameStartTime = Date()
        
        print("🧮 ADAPTIVE: Set up average puzzle with difficulty: \(currentDifficulty.name)")
        print("🧮 ADAPTIVE: Numbers: \(currentPuzzleData.numbers), Average: \(currentPuzzleData.average)")
    }
    
    private func addNumberToInput(_ number: Int) {
        if !isInputDisabled && currentInput.count < 6 {
            hapticFeedback()
            currentInput += "\(number)"
        }
    }
    
    private func clearInput() {
        if !isInputDisabled {
            hapticFeedback()
            currentInput = ""
        }
    }
    
    private func addZeroToInput() {
        if !isInputDisabled && currentInput.count < 6 {
            hapticFeedback()
            if currentInput.isEmpty {
                currentInput = "0"
            } else {
                currentInput += "0"
            }
        }
    }
    
    private func addDecimalToInput() {
        if !isInputDisabled && !currentInput.contains(".") && !currentInput.isEmpty {
            hapticFeedback()
            currentInput += "."
        }
    }
    
    private func submitAnswer() {
        guard !isInputDisabled && !currentInput.isEmpty else { return }
        
        hapticFeedback()
        guard let answer = Double(currentInput) else { return }
        
        let timeSpent = Date().timeIntervalSince(gameStartTime)
        let isCorrect = abs(answer - currentPuzzleData.average) <= currentPuzzleData.tolerance
        
        isAnswered = true
        totalAnswers += 1
        
        if isCorrect {
            correctAnswers += 1
            currentStreak += 1
            let score = calculateAdaptiveScore(isCorrect: true, timeSpent: timeSpent)
            totalScore += score
        } else {
            currentStreak = 0
            currentLives = max(0, currentLives - 1)
        }
        
        recordPerformanceAndAdapt(isCorrect: isCorrect, timeSpent: timeSpent)
        
        // Show feedback
        feedbackManager.showFeedback(
            puzzleType: "average",
            isCorrect: isCorrect,
            userAnswer: String(format: currentPuzzleData.difficulty.allowDecimals ? "%.1f" : "%.0f", answer),
            correctAnswer: String(format: currentPuzzleData.difficulty.allowDecimals ? "%.1f" : "%.0f", currentPuzzleData.average),
            timeSpent: timeSpent,
            difficulty: currentPuzzleData.difficulty.name,
            timeRemaining: timer.timeRemaining,
            totalTime: timer.totalTime,
            onComplete: {
                gamesPlayedThisSession += 1
                onAnswerSubmitted(answer, isCorrect)
                if isCorrect {
                    onNextPuzzle()
                } else {
                    resetState()
                }
            }
        )
    }
    
    private func handleTimeUp() {
        let finalAnswer = Double(currentInput) ?? 0.0
        let timeSpent = Date().timeIntervalSince(gameStartTime)
        let isCorrect = abs(finalAnswer - currentPuzzleData.average) <= currentPuzzleData.tolerance
        
        isAnswered = true
        totalAnswers += 1
        
        if !isCorrect {
            currentLives = max(0, currentLives - 1)
            currentStreak = 0
        } else {
            correctAnswers += 1
        }
        
        recordPerformanceAndAdapt(isCorrect: isCorrect, timeSpent: timeSpent)
        
        feedbackManager.showFeedback(
            puzzleType: "average",
            isCorrect: isCorrect,
            userAnswer: String(format: currentPuzzleData.difficulty.allowDecimals ? "%.1f" : "%.0f", finalAnswer),
            correctAnswer: String(format: currentPuzzleData.difficulty.allowDecimals ? "%.1f" : "%.0f", currentPuzzleData.average),
            timeSpent: timeSpent,
            difficulty: currentPuzzleData.difficulty.name,
            timeRemaining: 0,
            totalTime: timer.totalTime,
            onComplete: {
                gamesPlayedThisSession += 1
                onAnswerSubmitted(finalAnswer, isCorrect)
                onNextPuzzle()
            }
        )
    }
    
    private func recordPerformanceAndAdapt(isCorrect: Bool, timeSpent: TimeInterval) {
        let complexity = Float(currentPuzzleData.numbers.count) * Float(currentPuzzleData.numbers.max() ?? 100) / 1000.0
        
        let performance = AveragePuzzleDifficultyManager.PlayerPerformance(
            accuracy: isCorrect ? 1.0 : 0.0,
            timeSpent: Float(timeSpent),
            streakLength: currentStreak,
            livesRemaining: currentLives,
            gameScore: totalScore,
            difficulty: currentPuzzleData.difficulty.name,
            averageComplexity: complexity
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
        let basePoints = currentPuzzleData.difficulty.basePoints
        let timeBonusMultiplier = max(0.1, 1.0 - (timeSpent / Double(currentPuzzleData.difficulty.timeLimit)))
        let complexityBonus = currentPuzzleData.numbers.count > 3 ? basePoints / 2 : 0
        let streakBonus = Int(Float(basePoints) * Float(currentStreak) * 0.1)
        
        return Int(Double(basePoints) * timeBonusMultiplier) + complexityBonus + streakBonus
    }
    
    private func resetState() {
        isAnswered = false
        currentInput = ""
        // Create a new timer for the reset
        timer = PuzzleTimer(totalTime: currentPuzzleData.difficulty.timeLimit)
        timer.start()
        gameStartTime = Date()
    }
    
    private func hapticFeedback() {
        let impactFeedback = UIImpactFeedbackGenerator(style: .medium)
        impactFeedback.impactOccurred()
    }
}

// MARK: - Calculator Button (Enhanced for adaptive use)
struct AverageCalculatorButton: View {
    let text: String
    let action: () -> Void
    var isSpecial: Bool = false
    var isInput: Bool = false
    var isDisabled: Bool = false
    
    var body: some View {
        Button(action: action) {
            VStack(spacing: 4) {
                Text(text)
                    .font(.system(size: 20, weight: .bold))
                    .foregroundColor(.white)
                    .lineLimit(1)
                    .minimumScaleFactor(0.5)
                
                if isInput && text != "0" {
                    Rectangle()
                        .fill(Color.white)
                        .frame(width: 30, height: 2)
                }
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity)
            .aspectRatio(1, contentMode: .fit)
            .background(
                isInput ? Color.white.opacity(0.1) :
                isSpecial ? Color.white.opacity(0.1) :
                Color.clear
            )
            .overlay(
                RoundedRectangle(cornerRadius: 8)
                    .stroke(
                        Color.white.opacity(isInput ? 0.6 : 0.3),
                        lineWidth: isInput ? 2 : 1
                    )
            )
        }
        .buttonStyle(PlainButtonStyle())
        .disabled(isDisabled)
        .opacity(isDisabled ? 0.5 : 1.0)
    }
}

// MARK: - Canvas Drawing Functions (from your original implementation)
func drawMountainLandscape(context: GraphicsContext, size: CGSize) {
    // Sky gradient
    let skyGradient = Gradient(colors: [
        Color(red: 0.29, green: 0.29, blue: 0.29),
        Color(red: 0.55, green: 0.29, blue: 0.62),
        Color(red: 0.91, green: 0.66, blue: 0.49)
    ])
    
    let skyRect = CGRect(x: 0, y: 0, width: size.width, height: size.height * 0.7)
    context.fill(
        Path(skyRect),
        with: .linearGradient(
            skyGradient,
            startPoint: CGPoint(x: 0, y: 0),
            endPoint: CGPoint(x: 0, y: size.height * 0.7)
        )
    )
    
    // Mountain layers
    let mountainLayer1Points = [
        CGPoint(x: 0, y: size.height * 0.8),
        CGPoint(x: size.width * 0.2, y: size.height * 0.4),
        CGPoint(x: size.width * 0.4, y: size.height * 0.6),
        CGPoint(x: size.width * 0.6, y: size.height * 0.3),
        CGPoint(x: size.width * 0.8, y: size.height * 0.5),
        CGPoint(x: size.width, y: size.height * 0.7),
        CGPoint(x: size.width, y: size.height),
        CGPoint(x: 0, y: size.height)
    ]
    
    let mountainLayer2Points = [
        CGPoint(x: 0, y: size.height * 0.9),
        CGPoint(x: size.width * 0.3, y: size.height * 0.6),
        CGPoint(x: size.width * 0.7, y: size.height * 0.4),
        CGPoint(x: size.width, y: size.height * 0.8),
        CGPoint(x: size.width, y: size.height),
        CGPoint(x: 0, y: size.height)
    ]
    
    let mountainColors = [
        Color.black.opacity(0.6),
        Color.black.opacity(0.8)
    ]
    
    let mountainLayers = [mountainLayer1Points, mountainLayer2Points]
    
    for (index, points) in mountainLayers.enumerated() {
        var path = Path()
        path.move(to: points[0])
        for point in points.dropFirst() {
            path.addLine(to: point)
        }
        path.closeSubpath()
        
        context.fill(path, with: .color(mountainColors[index]))
    }
    
    // Trees
    let treePositions = [
        CGPoint(x: size.width * 0.1, y: size.height * 0.7),
        CGPoint(x: size.width * 0.15, y: size.height * 0.75),
        CGPoint(x: size.width * 0.85, y: size.height * 0.65),
        CGPoint(x: size.width * 0.9, y: size.height * 0.8)
    ]
    
    for position in treePositions {
        var treePath = Path()
        treePath.move(to: CGPoint(x: position.x, y: position.y + 100))
        treePath.addLine(to: CGPoint(x: position.x - 30, y: position.y + 100))
        treePath.addLine(to: CGPoint(x: position.x, y: position.y))
        treePath.addLine(to: CGPoint(x: position.x + 30, y: position.y + 100))
        treePath.closeSubpath()
        
        context.fill(treePath, with: .color(Color(red: 0.05, green: 0.11, blue: 0.16)))
    }
}

func drawGeometricShapes(context: GraphicsContext, size: CGSize) {
    let centerX = size.width / 2
    let centerY = size.height / 2
    
    // Interconnected triangles and lines
    let triangleSize: CGFloat = 100
    let triangleOffsets = [
        CGPoint(x: centerX - triangleSize, y: centerY - 50),
        CGPoint(x: centerX + triangleSize, y: centerY - 50),
        CGPoint(x: centerX, y: centerY + triangleSize)
    ]
    
    // Draw connecting lines
    for start in triangleOffsets {
        for end in triangleOffsets {
            if start != end {
                var linePath = Path()
                linePath.move(to: start)
                linePath.addLine(to: end)
                
                context.stroke(
                    linePath,
                    with: .color(.white.opacity(0.1)),
                    lineWidth: 1
                )
            }
        }
    }
    
    // Draw triangular outlines
    for center in triangleOffsets {
        var trianglePath = Path()
        trianglePath.move(to: CGPoint(x: center.x, y: center.y - 30))
        trianglePath.addLine(to: CGPoint(x: center.x - 25, y: center.y + 15))
        trianglePath.addLine(to: CGPoint(x: center.x + 25, y: center.y + 15))
        trianglePath.closeSubpath()
        
        context.stroke(
            trianglePath,
            with: .color(.white.opacity(0.15)),
            lineWidth: 2
        )
    }
    
    // Central circle
    let circleRect = CGRect(
        x: centerX - 60,
        y: centerY - 60,
        width: 120,
        height: 120
    )
    context.stroke(
        Path(ellipseIn: circleRect),
        with: .color(.white.opacity(0.1)),
        lineWidth: 1
    )
    
    // Cross lines
    var horizontalLine = Path()
    horizontalLine.move(to: CGPoint(x: centerX - 80, y: centerY))
    horizontalLine.addLine(to: CGPoint(x: centerX + 80, y: centerY))
    
    var verticalLine = Path()
    verticalLine.move(to: CGPoint(x: centerX, y: centerY - 80))
    verticalLine.addLine(to: CGPoint(x: centerX, y: centerY + 80))
    
    context.stroke(horizontalLine, with: .color(.white.opacity(0.2)), lineWidth: 2)
    context.stroke(verticalLine, with: .color(.white.opacity(0.2)), lineWidth: 2)
}
