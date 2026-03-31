//
//  MemorySquaresPuzzleView.swift
//  PuzzleForge
//
//  Enhanced with adaptive difficulty (Direct replacement)
//

import SwiftUI
import AVFoundation

// MARK: - Missing Types (Add these if they don't exist elsewhere)

// Define the missing MemoryGamePhase enum
enum MemoryGamePhase {
    case countdown
    case memorize
    case recall
    case feedback
}

// Define the missing MemorySquaresPuzzleData struct
struct MemorySquaresPuzzleData {
    let gridSize: Int
    let targetCount: Int
    let memorizeTime: Int
    let timeLimit: Int
    let livesAllowed: Int
    let targetPositions: Set<GridPosition>
    
    struct GridPosition: Hashable {
        let row: Int
        let col: Int
    }
}

// MARK: - Difficulty Management
class MemorySquaresDifficultyManager: ObservableObject {
    
    struct DifficultyLevel {
        let name: String
        let gridSize: Int
        let targetCount: Int
        let memorizeTime: Int
        let timeLimit: Int
        let livesAllowed: Int
        let basePoints: Int
        
        static let beginner = DifficultyLevel(name: "Beginner", gridSize: 3, targetCount: 2, memorizeTime: 4, timeLimit: 120, livesAllowed: 4, basePoints: 10)
        static let easy = DifficultyLevel(name: "Easy", gridSize: 4, targetCount: 3, memorizeTime: 3, timeLimit: 90, livesAllowed: 3, basePoints: 15)
        static let medium = DifficultyLevel(name: "Medium", gridSize: 4, targetCount: 4, memorizeTime: 3, timeLimit: 75, livesAllowed: 3, basePoints: 20)
        static let hard = DifficultyLevel(name: "Hard", gridSize: 5, targetCount: 5, memorizeTime: 2, timeLimit: 60, livesAllowed: 2, basePoints: 25)
        static let expert = DifficultyLevel(name: "Expert", gridSize: 5, targetCount: 6, memorizeTime: 2, timeLimit: 45, livesAllowed: 2, basePoints: 30)
        static let master = DifficultyLevel(name: "Master", gridSize: 6, targetCount: 7, memorizeTime: 1, timeLimit: 30, livesAllowed: 1, basePoints: 40)
        
        static let allLevels = [beginner, easy, medium, hard, expert, master]
    }
    
    // Make PlayerPerformance Codable for proper JSON encoding
    struct PlayerPerformance: Codable {
        let accuracy: Float
        let timeSpent: Float
        let streakLength: Int
        let livesRemaining: Int
        let gameScore: Int
        let difficulty: String
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
        let consistentSuccess = recentWindow.filter { $0.accuracy >= 0.8 }.count >= 3
        let consistentFailure = recentWindow.filter { $0.accuracy <= 0.4 }.count >= 3
        
        let currentIndex = DifficultyLevel.allLevels.firstIndex { $0.name == currentDifficulty.name } ?? 1
        
        var newLevel = currentDifficulty
        var reason = "Performance stable at current level"
        var confidence: Float = 0.3
        
        // Check for advancement
        if avgAccuracy >= 0.85 && consistentSuccess && currentIndex < DifficultyLevel.allLevels.count - 1 {
            newLevel = DifficultyLevel.allLevels[currentIndex + 1]
            reason = "Excellent performance - advancing to \(newLevel.name)"
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
        if let savedName = UserDefaults.standard.string(forKey: "memory_squares_difficulty"),
           let level = DifficultyLevel.allLevels.first(where: { $0.name == savedName }) {
            currentDifficulty = level
        }
        loadPerformanceHistory()
    }
    
    private func saveDifficulty() {
        UserDefaults.standard.set(currentDifficulty.name, forKey: "memory_squares_difficulty")
    }
    
    private func savePerformanceHistory() {
        let encoder = JSONEncoder()
        // Use the Codable PlayerPerformance directly
        if let encoded = try? encoder.encode(performanceHistory) {
            UserDefaults.standard.set(encoded, forKey: "memory_squares_performance_history")
        }
    }
    
    private func loadPerformanceHistory() {
        if let data = UserDefaults.standard.data(forKey: "memory_squares_performance_history") {
            let decoder = JSONDecoder()
            if let decoded = try? decoder.decode([PlayerPerformance].self, from: data) {
                performanceHistory = decoded
            }
        }
    }
}

// MARK: - Enhanced Memory Squares Puzzle Data
struct EnhancedMemorySquaresPuzzleData {
    let gridSize: Int
    let targetCount: Int
    let memorizeTime: Int
    let timeLimit: Int
    let livesAllowed: Int
    let difficulty: MemorySquaresDifficultyManager.DifficultyLevel
    let targetPositions: Set<GridPosition>
    
    struct GridPosition: Hashable {
        let row: Int
        let col: Int
    }
    
    init(from originalData: MemorySquaresPuzzleData, difficultyLevel: MemorySquaresDifficultyManager.DifficultyLevel) {
        self.gridSize = difficultyLevel.gridSize
        self.targetCount = difficultyLevel.targetCount
        self.memorizeTime = difficultyLevel.memorizeTime
        self.timeLimit = difficultyLevel.timeLimit
        self.livesAllowed = difficultyLevel.livesAllowed
        self.difficulty = difficultyLevel
        
        // Generate new target positions based on adaptive difficulty
        var positions = Set<GridPosition>()
        while positions.count < targetCount {
            let row = Int.random(in: 0..<gridSize)
            let col = Int.random(in: 0..<gridSize)
            positions.insert(GridPosition(row: row, col: col))
        }
        self.targetPositions = positions
    }
    
    init(difficultyLevel: MemorySquaresDifficultyManager.DifficultyLevel) {
        self.gridSize = difficultyLevel.gridSize
        self.targetCount = difficultyLevel.targetCount
        self.memorizeTime = difficultyLevel.memorizeTime
        self.timeLimit = difficultyLevel.timeLimit
        self.livesAllowed = difficultyLevel.livesAllowed
        self.difficulty = difficultyLevel
        
        // Generate random target positions
        var positions = Set<GridPosition>()
        while positions.count < targetCount {
            let row = Int.random(in: 0..<gridSize)
            let col = Int.random(in: 0..<gridSize)
            positions.insert(GridPosition(row: row, col: col))
        }
        self.targetPositions = positions
    }
}

// MARK: - Enhanced Memory Squares Puzzle View (Drop-in replacement)
struct MemorySquaresPuzzleView: View {
    let puzzle: Puzzle
    let questionIndex: Int
    let totalQuestions: Int
    let onAnswerSubmitted: (Bool) -> Void
    let onNextPuzzle: () -> Void
    let onExit: () -> Void
    
    // Difficulty management
    @StateObject private var difficultyManager = MemorySquaresDifficultyManager()
    @State private var currentPuzzleData: EnhancedMemorySquaresPuzzleData
    @State private var adaptationResult: MemorySquaresDifficultyManager.AdaptationResult?
    @State private var showAdaptationNotification = false
    
    // Game state
    @State private var gamePhase: MemoryGamePhase = .countdown
    @State private var currentLives: Int
    @State private var countdownTime = 3
    @State private var memorizeTime: Int
    @State private var timeRemaining: Int
    @State private var selectedCells: Set<EnhancedMemorySquaresPuzzleData.GridPosition> = []
    @State private var showFeedback = false
    @State private var isCorrect = false
    @State private var gameCompleted = false
    @State private var incorrectSelections: Set<EnhancedMemorySquaresPuzzleData.GridPosition> = []
    
    // Performance tracking
    @State private var totalScore = 0
    @State private var correctSelections = 0
    @State private var wrongSelections = 0
    @State private var gameStartTime = Date()
    @State private var currentStreak = 0
    @State private var gamesPlayedThisSession = 0
    
    // Timer
    @State private var timer: Timer?
    
    init(puzzle: Puzzle, questionIndex: Int, totalQuestions: Int, onAnswerSubmitted: @escaping (Bool) -> Void, onNextPuzzle: @escaping () -> Void, onExit: @escaping () -> Void) {
        self.puzzle = puzzle
        self.questionIndex = questionIndex
        self.totalQuestions = totalQuestions
        self.onAnswerSubmitted = onAnswerSubmitted
        self.onNextPuzzle = onNextPuzzle
        self.onExit = onExit
        
        // Initialize with current difficulty
        let initialDifficulty = MemorySquaresDifficultyManager.DifficultyLevel.easy
        self._currentPuzzleData = State(initialValue: EnhancedMemorySquaresPuzzleData(difficultyLevel: initialDifficulty))
        self._currentLives = State(initialValue: initialDifficulty.livesAllowed)
        self._memorizeTime = State(initialValue: initialDifficulty.memorizeTime)
        self._timeRemaining = State(initialValue: initialDifficulty.timeLimit)
    }
    
    var body: some View {
        GeometryReader { geometry in
            ZStack {
                // Background
                Color(red: 0.55, green: 0.43, blue: 0.39)
                    .ignoresSafeArea()
                
                VStack(spacing: 20) {
                    // Top bar with difficulty indicator
                    topBarView
                    
                    // Adaptation notification
                    if showAdaptationNotification, let result = adaptationResult {
                        adaptationNotificationView(result: result)
                            .transition(.move(edge: .top).combined(with: .opacity))
                    }
                    
                    // Game info with enhanced stats
                    gameInfoView
                    
                    // Phase indicator
                    phaseIndicatorView
                    
                    // Memory squares grid
                    memoryGridView(geometry: geometry)
                    
                    // Lives indicator
                    livesIndicatorView
                    
                    Spacer()
                    
                    // Action buttons
                    if gamePhase == .recall {
                        actionButtonsView
                    }
                }
            }
        }
        .onAppear {
            setupEnhancedPuzzle()
        }
        .onDisappear {
            timer?.invalidate()
        }
        .alert("Game Result", isPresented: $showFeedback) {
            Button(isCorrect ? "Continue" : "Next Puzzle") {
                showFeedback = false
                gameCompleted = true
                onAnswerSubmitted(isCorrect)
                onNextPuzzle()
            }
        } message: {
            enhancedFeedbackMessage
        }
    }
    
    // MARK: - View Components
    
    private var topBarView: some View {
        HStack {
            Button(action: onExit) {
                Image(systemName: "arrow.left")
                    .font(.title2)
                    .foregroundColor(.white)
            }
            
            Spacer()
            
            VStack {
                Text("Memory Squares")
                    .font(.headline)
                    .fontWeight(.bold)
                    .foregroundColor(.white)
                
                // Difficulty progress indicator
                let progress = difficultyManager.getDifficultyProgress()
                HStack(spacing: 4) {
                    Text(progress.name)
                        .font(.caption)
                        .fontWeight(.semibold)
                        .foregroundColor(.cyan)
                    
                    // Progress dots
                    HStack(spacing: 2) {
                        ForEach(1...progress.total, id: \.self) { index in
                            Circle()
                                .fill(index <= progress.current ? Color.cyan : Color.white.opacity(0.3))
                                .frame(width: 4, height: 4)
                        }
                    }
                }
            }
            
            Spacer()
            
            Text(formatTime(timeRemaining))
                .font(.headline)
                .fontWeight(.bold)
                .foregroundColor(timeRemaining <= 30 ? .red : .white)
        }
        .padding(.horizontal, 20)
        .padding(.top, 10)
    }
    
    private func adaptationNotificationView(result: MemorySquaresDifficultyManager.AdaptationResult) -> some View {
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
    
    private var gameInfoView: some View {
        HStack {
            VStack(alignment: .leading) {
                Text("TRIAL \(questionIndex + 1)/\(totalQuestions)")
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
                
                Text("\(currentPuzzleData.gridSize)×\(currentPuzzleData.gridSize) • \(currentPuzzleData.targetCount)")
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
    
    private var phaseIndicatorView: some View {
        VStack(spacing: 8) {
            switch gamePhase {
            case .countdown:
                Text("Get Ready!")
                    .font(.title2)
                    .fontWeight(.bold)
                    .foregroundColor(.white)
                
                Text("\(countdownTime)")
                    .font(.system(size: 48, weight: .bold))
                    .foregroundColor(.white)
                
            case .memorize:
                Text("Memorize \(currentPuzzleData.targetCount) squares!")
                    .font(.title3)
                    .fontWeight(.bold)
                    .foregroundColor(.white)
                
                Text("\(memorizeTime)s remaining")
                    .font(.subheadline)
                    .foregroundColor(memorizeTime <= 1 ? .red : .white.opacity(0.8))
                
            case .recall:
                Text("Click the squares you remember!")
                    .font(.title3)
                    .fontWeight(.bold)
                    .foregroundColor(.white)
                
                Text("Found: \(selectedCells.intersection(currentPuzzleData.targetPositions).count)/\(currentPuzzleData.targetCount)")
                    .font(.subheadline)
                    .foregroundColor(.white.opacity(0.8))
                
            case .feedback:
                Text(isCorrect ? "Perfect!" : "Try Again!")
                    .font(.title2)
                    .fontWeight(.bold)
                    .foregroundColor(isCorrect ? .green : .red)
            }
        }
    }
    
    private func memoryGridView(geometry: GeometryProxy) -> some View {
        let gridSize = min(geometry.size.width - 40, 280)
        let cellSize = (gridSize - CGFloat(currentPuzzleData.gridSize - 1) * 4) / CGFloat(currentPuzzleData.gridSize)
        
        return VStack(spacing: 4) {
            ForEach(0..<currentPuzzleData.gridSize, id: \.self) { row in
                HStack(spacing: 4) {
                    ForEach(0..<currentPuzzleData.gridSize, id: \.self) { col in
                        memoryCellView(row: row, col: col, cellSize: cellSize)
                    }
                }
            }
        }
        .padding(8)
        .background(
            RoundedRectangle(cornerRadius: 12)
                .fill(Color.black.opacity(0.8))
        )
        .overlay(
            Group {
                if gamePhase == .countdown {
                    countdownOverlayView(gridSize: gridSize)
                }
            }
        )
    }
    
    private func memoryCellView(row: Int, col: Int, cellSize: CGFloat) -> some View {
        let position = EnhancedMemorySquaresPuzzleData.GridPosition(row: row, col: col)
        let isTarget = currentPuzzleData.targetPositions.contains(position)
        let isSelected = selectedCells.contains(position)
        let isIncorrect = incorrectSelections.contains(position)
        let showHighlight = gamePhase == .memorize && isTarget
        
        return Rectangle()
            .fill(cellColor(isTarget: isTarget, isSelected: isSelected, isIncorrect: isIncorrect, showHighlight: showHighlight))
            .frame(width: cellSize, height: cellSize)
            .cornerRadius(4)
            .overlay(
                overlayContent(isSelected: isSelected, isTarget: isTarget, isIncorrect: isIncorrect, cellSize: cellSize)
            )
            .onTapGesture {
                onCellTapped(position: position)
            }
            .animation(.easeInOut(duration: 0.2), value: gamePhase)
            .animation(.easeInOut(duration: 0.2), value: isSelected)
    }
    
    @ViewBuilder
    private func overlayContent(isSelected: Bool, isTarget: Bool, isIncorrect: Bool, cellSize: CGFloat) -> some View {
        Group {
            if isSelected && isTarget {
                Text("✓")
                    .foregroundColor(.white)
                    .font(.system(size: cellSize * 0.4, weight: .bold))
            } else if isIncorrect {
                Text("✗")
                    .foregroundColor(.white)
                    .font(.system(size: cellSize * 0.4, weight: .bold))
            } else if gamePhase == .feedback && isTarget && !isSelected {
                Text("?")
                    .foregroundColor(.white)
                    .font(.system(size: cellSize * 0.4, weight: .bold))
            }
        }
    }
    
    private func cellColor(isTarget: Bool, isSelected: Bool, isIncorrect: Bool, showHighlight: Bool) -> Color {
        switch true {
        case showHighlight:
            return Color(red: 0.0, green: 0.737, blue: 0.831)
        case isSelected && isTarget:
            return Color(red: 0.298, green: 0.686, blue: 0.314)
        case isIncorrect:
            return Color(red: 0.957, green: 0.263, blue: 0.212)
        case gamePhase == .feedback && isTarget:
            return Color(red: 0.0, green: 0.737, blue: 0.831)
        default:
            return Color(red: 0.427, green: 0.298, blue: 0.255)
        }
    }
    
    private func countdownOverlayView(gridSize: CGFloat) -> some View {
        RoundedRectangle(cornerRadius: 12)
            .fill(Color.black.opacity(0.8))
            .frame(width: gridSize, height: gridSize)
            .overlay(
                VStack(spacing: 16) {
                    Text("Get Ready!")
                        .font(.title2)
                        .fontWeight(.bold)
                        .foregroundColor(.white)
                    
                    Text("\(countdownTime)")
                        .font(.system(size: 72, weight: .bold))
                        .foregroundColor(.cyan)
                    
                    Text("Remember \(currentPuzzleData.targetCount) squares in \(currentPuzzleData.memorizeTime)s!")
                        .font(.subheadline)
                        .foregroundColor(.white.opacity(0.8))
                        .multilineTextAlignment(.center)
                }
            )
    }
    
    private var livesIndicatorView: some View {
        HStack(spacing: 8) {
            ForEach(0..<currentPuzzleData.livesAllowed, id: \.self) { index in
                Image(systemName: "heart.fill")
                    .foregroundColor(index < currentLives ? .red : .gray)
                    .font(.system(size: 20))
            }
        }
    }
    
    private var actionButtonsView: some View {
        HStack(spacing: 12) {
            Button("Clear") {
                selectedCells.removeAll()
                incorrectSelections.removeAll()
                correctSelections = 0
                wrongSelections = 0
                playTapSound()
            }
            .font(.system(size: 16, weight: .semibold))
            .foregroundColor(.white)
            .frame(maxWidth: .infinity)
            .padding(.vertical, 12)
            .background(
                RoundedRectangle(cornerRadius: 8)
                    .stroke(Color.white, lineWidth: 2)
            )
            
            Button("Submit") {
                submitAnswer()
            }
            .font(.system(size: 16, weight: .semibold))
            .foregroundColor(.white)
            .frame(maxWidth: .infinity)
            .padding(.vertical, 12)
            .background(
                RoundedRectangle(cornerRadius: 8)
                    .fill(Color(red: 0.31, green: 0.764, blue: 0.969))
            )
        }
        .padding(.horizontal, 20)
    }
    
    private var enhancedFeedbackMessage: Text {
        let correctCount = selectedCells.intersection(currentPuzzleData.targetPositions).count
        let accuracy = currentPuzzleData.targetCount > 0 ? (correctCount * 100) / currentPuzzleData.targetCount : 0
        
        var message = ""
        
        if isCorrect {
            message = "Perfect! You remembered all \(currentPuzzleData.targetCount) squares!\n\nFinal Score: \(totalScore)"
            if currentStreak > 1 {
                message += "\nStreak: \(currentStreak) 🔥"
            }
        } else {
            message = "You found \(correctCount)/\(currentPuzzleData.targetCount) squares (\(accuracy)%)\n\nScore: \(totalScore)"
            if currentLives > 0 {
                message += "\nLives remaining: \(currentLives)"
            }
        }
        
        // Add adaptation info if available
        if let result = adaptationResult, result.shouldNotify {
            message += "\n\n🎯 Difficulty adapted to \(result.level.name)!"
        }
        
        return Text(message)
    }
    
    // MARK: - Game Logic
    
    private func setupEnhancedPuzzle() {
        let currentDifficulty = difficultyManager.currentDifficulty
        
        // Create new puzzle data with adaptive difficulty
        currentPuzzleData = EnhancedMemorySquaresPuzzleData(difficultyLevel: currentDifficulty)
        
        currentLives = currentPuzzleData.livesAllowed
        memorizeTime = currentPuzzleData.memorizeTime
        timeRemaining = currentPuzzleData.timeLimit
        
        print("🧠 ENHANCED: Set up puzzle with difficulty: \(currentDifficulty.name)")
        print("🧠 ENHANCED: Grid: \(currentPuzzleData.gridSize)×\(currentPuzzleData.gridSize), Targets: \(currentPuzzleData.targetCount)")
        
        startCountdown()
    }
    
    private func startCountdown() {
        gamePhase = .countdown
        countdownTime = 3
        
        timer = Timer.scheduledTimer(withTimeInterval: 1.0, repeats: true) { _ in
            countdownTime -= 1
            playTickSound()
            
            if countdownTime <= 0 {
                timer?.invalidate()
                startMemorizePhase()
            }
        }
    }
    
    private func startMemorizePhase() {
        gamePhase = .memorize
        memorizeTime = currentPuzzleData.memorizeTime
        
        timer = Timer.scheduledTimer(withTimeInterval: 1.0, repeats: true) { _ in
            memorizeTime -= 1
            playTickSound()
            
            if memorizeTime <= 0 {
                timer?.invalidate()
                startRecallPhase()
            }
        }
    }
    
    private func startRecallPhase() {
        gamePhase = .recall
        gameStartTime = Date()
        
        timer = Timer.scheduledTimer(withTimeInterval: 1.0, repeats: true) { _ in
            timeRemaining -= 1
            
            if timeRemaining <= 0 {
                timer?.invalidate()
                handleTimeUp()
            }
        }
    }
    
    private func onCellTapped(position: EnhancedMemorySquaresPuzzleData.GridPosition) {
        guard gamePhase == .recall && !gameCompleted else { return }
        
        let isTarget = currentPuzzleData.targetPositions.contains(position)
        
        if selectedCells.contains(position) {
            selectedCells.remove(position)
            incorrectSelections.remove(position)
            if isTarget {
                correctSelections -= 1
            } else {
                wrongSelections -= 1
            }
            playTapSound()
        } else {
            if isTarget {
                selectedCells.insert(position)
                correctSelections += 1
                playSuccessSound()
                
                if selectedCells.count == currentPuzzleData.targetCount &&
                   selectedCells.isSubset(of: currentPuzzleData.targetPositions) {
                    handleCorrectCompletion()
                }
            } else {
                incorrectSelections.insert(position)
                selectedCells.insert(position)
                currentLives -= 1
                wrongSelections += 1
                playErrorSound()
                
                if currentLives <= 0 {
                    handleGameOver()
                } else {
                    DispatchQueue.main.asyncAfter(deadline: .now() + 0.8) {
                        selectedCells.remove(position)
                        incorrectSelections.remove(position)
                        wrongSelections -= 1
                    }
                }
            }
        }
    }
    
    private func submitAnswer() {
        let correctCount = selectedCells.intersection(currentPuzzleData.targetPositions).count
        let incorrectCount = selectedCells.count - correctCount
        
        isCorrect = correctCount == currentPuzzleData.targetCount && incorrectCount == 0
        
        if isCorrect {
            handleCorrectCompletion()
        } else {
            handleIncorrectSubmission()
        }
    }
    
    private func handleCorrectCompletion() {
        timer?.invalidate()
        
        let timeSpent = Date().timeIntervalSince(gameStartTime)
        let newStreak = currentStreak + 1
        
        totalScore = calculateEnhancedScore(
            isCorrect: true,
            correctCount: correctSelections,
            wrongCount: wrongSelections,
            timeSpent: timeSpent,
            currentStreak: newStreak
        )
        
        recordPerformanceAndAdapt(
            accuracy: 1.0,
            timeSpent: timeSpent,
            streak: newStreak,
            score: totalScore
        )
        
        currentStreak = newStreak
        gamesPlayedThisSession += 1
        isCorrect = true
        gamePhase = .feedback
        showFeedback = true
    }
    
    private func handleIncorrectSubmission() {
        timer?.invalidate()
        
        let timeSpent = Date().timeIntervalSince(gameStartTime)
        let correctCount = selectedCells.intersection(currentPuzzleData.targetPositions).count
        let accuracy = currentPuzzleData.targetCount > 0 ? Float(correctCount) / Float(currentPuzzleData.targetCount) : 0
        
        totalScore = calculateEnhancedScore(
            isCorrect: false,
            correctCount: correctCount,
            wrongCount: wrongSelections,
            timeSpent: timeSpent,
            currentStreak: 0
        )
        
        recordPerformanceAndAdapt(
            accuracy: accuracy,
            timeSpent: timeSpent,
            streak: 0,
            score: totalScore
        )
        
        currentStreak = 0
        gamesPlayedThisSession += 1
        isCorrect = false
        gamePhase = .feedback
        showFeedback = true
    }
    
    private func handleGameOver() {
        timer?.invalidate()
        handleIncorrectSubmission()
    }
    
    private func handleTimeUp() {
        timer?.invalidate()
        handleIncorrectSubmission()
    }
    
    private func recordPerformanceAndAdapt(accuracy: Float, timeSpent: TimeInterval, streak: Int, score: Int) {
        let performance = MemorySquaresDifficultyManager.PlayerPerformance(
            accuracy: accuracy,
            timeSpent: Float(timeSpent),
            streakLength: streak,
            livesRemaining: currentLives,
            gameScore: score,
            difficulty: currentPuzzleData.difficulty.name
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
    
    private func calculateEnhancedScore(isCorrect: Bool, correctCount: Int, wrongCount: Int, timeSpent: TimeInterval, currentStreak: Int) -> Int {
        let basePointsPerCell = currentPuzzleData.difficulty.basePoints
        let correctScore = correctCount * basePointsPerCell
        
        let complexityMultiplier = 1.0 + Float(currentPuzzleData.gridSize - 3) * 0.1
        let memoryLoadBonus = Int(Float(correctScore) * Float(currentPuzzleData.targetCount - 2) * 0.15)
        let streakMultiplier = 1.0 + Float(currentStreak) * 0.1
        let perfectBonus = (isCorrect && wrongCount == 0) ? Int(Float(correctScore) * 0.5) : 0
        
        let speedBonus: Int
        switch timeSpent {
        case ...8: speedBonus = Int(Float(correctScore) * 0.5)
        case ...15: speedBonus = Int(Float(correctScore) * 0.3)
        case ...25: speedBonus = Int(Float(correctScore) * 0.1)
        default: speedBonus = 0
        }
        
        let livesBonus = currentLives * (basePointsPerCell / 2)
        let wrongPenalty = wrongCount * basePointsPerCell
        
        let finalScore = Int(Float(correctScore) * complexityMultiplier * streakMultiplier) +
                        memoryLoadBonus + perfectBonus + speedBonus + livesBonus - wrongPenalty
        
        return max(finalScore, correctScore / 4)
    }
    
    // MARK: - Helper Methods
    
    private func formatTime(_ seconds: Int) -> String {
        return "\(seconds / 60):\(String(format: "%02d", seconds % 60))"
    }
    
    // MARK: - Sound Effects
    private func playTapSound() {
        AudioServicesPlaySystemSound(1104)
    }
    
    private func playTickSound() {
        AudioServicesPlaySystemSound(1103)
    }
    
    private func playSuccessSound() {
        AudioServicesPlaySystemSound(1054)
    }
    
    private func playErrorSound() {
        AudioServicesPlaySystemSound(1053)
    }
}
