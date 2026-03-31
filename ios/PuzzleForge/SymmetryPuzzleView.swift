//
//  AdaptiveSymmetryPuzzleData.swift
//  PuzzleForge
//
//  Created by Sushanth Tiruvaipati on 8/17/25.
//


//
//  AdaptiveSymmetryPuzzleView.swift
//  PuzzleForge
//
//  iOS Adaptive Symmetry Puzzle with unified difficulty management
//

import SwiftUI
import Foundation
import Combine

// MARK: - Supporting Data Models
struct AdaptiveSymmetryPuzzleData {
    let gridSize: Int
    let leftPattern: [[Bool]]
    let correctRightPattern: [[Bool]]
    let difficulty: String
    let questionNumber: Int
    let isMirror: Bool
    let mirrorType: String
}

// MARK: - Main Adaptive Symmetry View
struct AdaptiveSymmetryPuzzleView: View {
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
    @State private var userRightPattern: [[Bool]] = []
    @State private var showFeedback = false
    @State private var isCorrect = false
    @State private var feedbackMessage = ""
    @State private var score = 0
    @State private var totalScore = 0
    @State private var timeRemaining = 60
    @State private var isTimerRunning = true
    @State private var currentHearts = 3
    @State private var puzzleStartTime = Date()
    @State private var requiredCells = 0
    @State private var currentStreak = 0
    @State private var gamesPlayedThisSession = 0
    
    // Session tracking
    @State private var sessionStartTime = Date()
    @State private var correctAnswers = 0
    @State private var totalAnswers = 0
    
    // Session completion state
    @State private var sessionResult: UnifiedSessionCompletionHandler.SessionResult?
    @State private var gameCompleted = false
    
    // Puzzle data
    @State private var puzzleData: AdaptiveSymmetryPuzzleData?
    
    var body: some View {
        ZStack {
            Color(red: 0.96, green: 0.96, blue: 0.96)
                .ignoresSafeArea()
            
            VStack(spacing: 16) {
                // Unified header
                AdaptiveUnifiedHeader(
                    puzzleType: "symmetry",
                    currentDifficulty: currentDifficultyLevel,
                    score: totalScore,
                    challengeNumber: totalAnswers + 1,
                    totalChallenges: 10,
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
                    onPause: { isTimerRunning.toggle() },
                    onHint: { showHint() }
                )
                
                // Adaptation notification
                UnifiedAdaptationNotification(
                    adaptationInfo: adaptationInfo,
                    puzzleType: "symmetry",
                    visible: showAdaptationNotification,
                    onDismiss: { showAdaptationNotification = false }
                )
                
                // Title and mirror indicator
                titleSection
                
                // Progress info
                if totalScore > 0 || gamesPlayedThisSession > 0 {
                    progressSection
                }
                
                Spacer().frame(height: 32)
                
                // Main grid section
                gridSection
                
                Spacer().frame(height: 32)
                
                // Feedback section
                if showFeedback {
                    feedbackSection
                }
                
                Spacer()
            }
            .padding(.horizontal, 16)
        }
        .navigationBarHidden(true)
        .onAppear {
            setupPuzzle()
        }
    }
    
    // MARK: - UI Sections
    private var titleSection: some View {
        VStack(spacing: 8) {
            HStack(alignment: .center) {
                Text("Symmetry")
                    .font(.title2)
                    .fontWeight(.bold)
                    .foregroundColor(.primary)
                
                Spacer().frame(width: 8)
                
                // Mirror indicator
                if let data = puzzleData {
                    Label(data.isMirror ? "🪞 MIRROR" : "📋 COPY", systemImage: "")
                        .font(.caption)
                        .fontWeight(.bold)
                        .foregroundColor(data.isMirror ? .blue : .purple)
                        .padding(.horizontal, 8)
                        .padding(.vertical, 4)
                        .background(
                            RoundedRectangle(cornerRadius: 8)
                                .fill(data.isMirror ? Color.blue.opacity(0.1) : Color.purple.opacity(0.1))
                        )
                }
            }
            
            // Dynamic instruction
            if let data = puzzleData {
                Text(getInstructionText(data: data))
                    .font(.subheadline)
                    .foregroundColor(.secondary)
                    .multilineTextAlignment(.center)
                    .padding(.horizontal, 32)
                    .padding(.vertical, 4)
            }
            
            Text(currentDifficultyLevel.description)
                .font(.caption)
                .foregroundColor(.blue)
                .fontWeight(.medium)
                .multilineTextAlignment(.center)
                .padding(.horizontal, 32)
                .padding(.vertical, 2)
        }
    }
    
    private var progressSection: some View {
        HStack {
            if totalScore > 0 {
                VStack(alignment: .center) {
                    Text("SCORE")
                        .font(.caption2)
                        .fontWeight(.bold)
                        .foregroundColor(.gray)
                    Text("\(totalScore)")
                        .font(.subheadline)
                        .fontWeight(.bold)
                        .foregroundColor(.blue)
                }
            }
            
            Spacer()
            
            if let data = puzzleData {
                VStack(alignment: .center) {
                    Text("GRID")
                        .font(.caption2)
                        .fontWeight(.bold)
                        .foregroundColor(.gray)
                    Text("\(data.gridSize)×\(data.gridSize)")
                        .font(.subheadline)
                        .foregroundColor(.secondary)
                }
                
                Spacer()
                
                // Cell counter
                VStack(alignment: .center) {
                    Text("CELLS")
                        .font(.caption2)
                        .fontWeight(.bold)
                        .foregroundColor(.gray)
                    Text("\(selectedCells())/\(requiredCells)")
                        .font(.subheadline)
                        .fontWeight(.medium)
                        .foregroundColor(selectedCells() == requiredCells ? .green : .secondary)
                }
            }
            
            if currentStreak > 0 {
                Spacer()
                
                VStack(alignment: .center) {
                    Text("STREAK")
                        .font(.caption2)
                        .fontWeight(.bold)
                        .foregroundColor(.gray)
                    Text("🔥 \(currentStreak)")
                        .font(.subheadline)
                        .fontWeight(.bold)
                        .foregroundColor(.orange)
                }
            }
        }
        .padding(12)
        .background(
            RoundedRectangle(cornerRadius: 8)
                .fill(Color.white.opacity(0.9))
        )
    }
    
    private var gridSection: some View {
        HStack(alignment: .center, spacing: 20) {
            if let data = puzzleData {
                // Left grid (pattern to copy/mirror)
                VStack(alignment: .center) {
                    Text(data.isMirror ? "Mirror this:" : "Copy this:")
                        .font(.caption)
                        .foregroundColor(.secondary)
                        .padding(.bottom, 8)
                    
                    SymmetryGridView(
                        pattern: data.leftPattern,
                        isInteractive: false,
                        difficulty: currentDifficultyLevel
                    ) { _, _ in }
                }
                
                // Divider with mirror indicator
                VStack(alignment: .center) {
                    if data.isMirror {
                        Text(getMirrorIndicator(data: data))
                            .font(.title3)
                            .foregroundColor(.blue)
                            .padding(.bottom, 4)
                    }
                    
                    Rectangle()
                        .fill(data.isMirror ? Color.blue : Color.gray.opacity(0.5))
                        .frame(width: 2, height: data.isMirror ? 100 : 120)
                }
                
                // Right grid (user input)
                VStack(alignment: .center) {
                    Text("To here:")
                        .font(.caption)
                        .foregroundColor(.secondary)
                        .padding(.bottom, 8)
                    
                    SymmetryGridView(
                        pattern: userRightPattern,
                        isInteractive: !showFeedback,
                        difficulty: currentDifficultyLevel
                    ) { row, col in
                        toggleCell(row: row, col: col)
                    }
                }
            }
        }
    }
    
    private var feedbackSection: some View {
        VStack(spacing: 8) {
            Text(feedbackMessage)
                .font(.headline)
                .fontWeight(.bold)
                .foregroundColor(isCorrect ? Color(red: 0.18, green: 0.49, blue: 0.2) : Color(red: 0.9, green: 0.39, blue: 0.0))
                .multilineTextAlignment(.center)
            
            if isCorrect {
                Text("+\(score) pts")
                    .font(.title3)
                    .fontWeight(.bold)
                    .foregroundColor(Color(red: 0.18, green: 0.49, blue: 0.2))
                
                Text("Difficulty: \(currentDifficultyLevel.name) • Speed Bonus! ⚡")
                    .font(.caption)
                    .foregroundColor(Color(red: 0.18, green: 0.49, blue: 0.2))
                    .fontWeight(.medium)
            }
        }
        .padding(16)
        .background(
            RoundedRectangle(cornerRadius: 12)
                .fill(isCorrect ? Color.green.opacity(0.1) : Color.orange.opacity(0.1))
        )
        .transition(.scale.combined(with: .opacity))
    }
    
    // MARK: - Game Logic
    private func setupPuzzle() {
        currentDifficultyLevel = difficultyManager.getCurrentDifficulty(for: "symmetry")
        currentHearts = currentDifficultyLevel.livesAllowed
        timeRemaining = currentDifficultyLevel.timeLimit
        
        generatePuzzle()
        
        Task {
            competitiveInsight = await competitiveManager.getCompetitiveInsight(
                userId: "user123",
                puzzleType: "symmetry",
                difficulty: currentDifficultyLevel.name
            )
        }
    }
    
    private func generatePuzzle() {
        puzzleData = generateAdaptiveSymmetryPuzzle(currentDifficultyLevel)
        
        guard let data = puzzleData else { return }
        
        // Initialize user pattern
        userRightPattern = Array(repeating: Array(repeating: false, count: data.gridSize), count: data.gridSize)
        requiredCells = data.leftPattern.flatMap { $0 }.filter { $0 }.count
        puzzleStartTime = Date()
        timeRemaining = currentDifficultyLevel.timeLimit
        
        if gamesPlayedThisSession > 0 {
            currentHearts = currentDifficultyLevel.livesAllowed
        }
        
        startTimer()
    }
    
    private func generateAdaptiveSymmetryPuzzle(_ difficulty: DifficultyManager.DifficultyLevel) -> AdaptiveSymmetryPuzzleData {
        let gridSize = max(3, min(6, difficulty.rawValue + 3))
        
        let isMirror: Bool = {
            switch difficulty {
            case .beginner: return false
            case .easy: return Float.random(in: 0...1) < 0.3
            case .medium: return Float.random(in: 0...1) < 0.5
            case .hard: return Float.random(in: 0...1) < 0.7
            case .expert: return Float.random(in: 0...1) < 0.8
            }
        }()
        
        let mirrorType: String = {
            if !isMirror { return "none" }
            switch difficulty {
            case .beginner, .easy: return "horizontal"
            case .medium, .hard, .expert: return ["horizontal", "vertical"].randomElement()!
            }
        }()
        
        // Generate left pattern
        let leftPattern = generateAdaptiveSymmetryPattern(gridSize: gridSize, difficulty: difficulty, isMirror: isMirror)
        
        // Generate correct right pattern
        let correctRightPattern: [[Bool]]
        if isMirror {
            switch mirrorType {
            case "horizontal":
                correctRightPattern = mirrorPatternHorizontally(leftPattern)
            case "vertical":
                correctRightPattern = mirrorPatternVertically(leftPattern)
            default:
                correctRightPattern = leftPattern
            }
        } else {
            correctRightPattern = leftPattern
        }
        
        return AdaptiveSymmetryPuzzleData(
            gridSize: gridSize,
            leftPattern: leftPattern,
            correctRightPattern: correctRightPattern,
            difficulty: difficulty.name,
            questionNumber: 1,
            isMirror: isMirror,
            mirrorType: mirrorType
        )
    }
    
    private func generateAdaptiveSymmetryPattern(gridSize: Int, difficulty: DifficultyManager.DifficultyLevel, isMirror: Bool) -> [[Bool]] {
        var pattern = Array(repeating: Array(repeating: false, count: gridSize), count: gridSize)
        
        let fillDensity: Float = {
            switch difficulty {
            case .beginner: return isMirror ? 0.25 : 0.30
            case .easy: return isMirror ? 0.30 : 0.35
            case .medium: return isMirror ? 0.35 : 0.40
            case .hard: return isMirror ? 0.40 : 0.45
            case .expert: return isMirror ? 0.45 : 0.50
            }
        }()
        
        let totalCells = gridSize * gridSize
        let targetFilledCells = max(1, min(totalCells - 1, Int(Float(totalCells) * fillDensity)))
        
        // Generate random pattern
        var positions: [(Int, Int)] = []
        for row in 0..<gridSize {
            for col in 0..<gridSize {
                positions.append((row, col))
            }
        }
        positions.shuffle()
        
        for i in 0..<min(targetFilledCells, positions.count) {
            let (row, col) = positions[i]
            pattern[row][col] = true
        }
        
        return pattern
    }
    
    private func mirrorPatternHorizontally(_ pattern: [[Bool]]) -> [[Bool]] {
        return pattern.map { $0.reversed() }
    }
    
    private func mirrorPatternVertically(_ pattern: [[Bool]]) -> [[Bool]] {
        return pattern.reversed()
    }
    
    private func toggleCell(row: Int, col: Int) {
        guard !showFeedback else { return }
        userRightPattern[row][col].toggle()
        
        // Auto-check when required number of cells selected
        if selectedCells() == requiredCells && requiredCells > 0 {
            checkAnswer()
        }
    }
    
    private func selectedCells() -> Int {
        return userRightPattern.flatMap { $0 }.filter { $0 }.count
    }
    
    private func checkAnswer() {
        guard let data = puzzleData else { return }
        
        isTimerRunning = false
        let timeSpent = Date().timeIntervalSince(puzzleStartTime)
        let correct = checkSymmetryPattern(data.correctRightPattern, userRightPattern)
        isCorrect = correct
        
        totalAnswers += 1
        
        if correct {
            correctAnswers += 1
            let newStreak = currentStreak + 1
            score = calculateScore(isCorrect: true, timeSpent: timeSpent)
            totalScore += score
            currentStreak = newStreak
            gamesPlayedThisSession += 1
            
            feedbackMessage = {
                switch timeSpent {
                case ..<1.0: return "Lightning fast! ⚡️"
                case ..<2.0: return "Super quick! 🚀"
                case ..<4.0: return "Nice speed! ✨"
                default: return "Perfect! 🎯"
                }
            }()
            
            recordPerformance(true, timeSpent * 1000)
        } else {
            currentHearts = max(0, currentHearts - 1)
            currentStreak = 0
            feedbackMessage = timeSpent < 1.0 ? "Too fast! Look closer 👀" : "Not quite right 🤔"
            
            recordPerformance(false, timeSpent * 1000)
        }
        
        showFeedback = true
        onAnswerSubmitted(correct)
        
        DispatchQueue.main.asyncAfter(deadline: .now() + (correct ? 1.2 : 1.8)) {
            if correct || currentHearts <= 0 {
                gameCompleted = true
            } else {
                // Reset for retry
                userRightPattern = Array(repeating: Array(repeating: false, count: data.gridSize), count: data.gridSize)
                showFeedback = false
                isTimerRunning = true
                puzzleStartTime = Date()
            }
        }
    }
    
    private func checkSymmetryPattern(_ correctPattern: [[Bool]], _ userPattern: [[Bool]]) -> Bool {
        guard correctPattern.count == userPattern.count else { return false }
        
        for (rowIndex, row) in correctPattern.enumerated() {
            guard row.count == userPattern[rowIndex].count else { return false }
            for (colIndex, cell) in row.enumerated() {
                if cell != userPattern[rowIndex][colIndex] {
                    return false
                }
            }
        }
        return true
    }
    
    private func calculateScore(isCorrect: Bool, timeSpent: TimeInterval) -> Int {
        guard isCorrect else { return 0 }
        
        let baseScore = currentDifficultyLevel.basePoints
        let timeBonus = max(0, Int(Double(currentDifficultyLevel.timeLimit) - timeSpent)) * 2
        let streakBonus = currentStreak * 10
        
        return baseScore + timeBonus + streakBonus
    }
    
    private func recordPerformance(_ isCorrect: Bool, _ timeSpent: Double) {
        if let config = difficultyManager.recordPerformance(
            puzzleType: "symmetry",
            isCorrect: isCorrect,
            timeSpent: timeSpent / 1000.0,
            difficulty: currentDifficultyLevel,
            streak: currentStreak,
            livesRemaining: currentHearts,
            gameScore: totalScore,
            challengesCompleted: gamesPlayedThisSession + 1
        ) {
            if config.shouldNotify && config.level != currentDifficultyLevel {
                adaptationInfo = config
                currentDifficultyLevel = config.level
                showAdaptationNotification = true
            }
        }
    }
    
    private func startTimer() {
        Timer.scheduledTimer(withTimeInterval: 1.0, repeats: true) { timer in
            if timeRemaining > 0 && isTimerRunning {
                timeRemaining -= 1
            } else if timeRemaining <= 0 && isTimerRunning {
                timer.invalidate()
                isTimerRunning = false
                showFeedback = true
                isCorrect = false
                feedbackMessage = "Time's up! ⏰"
                currentStreak = 0
                totalAnswers += 1
                
                recordPerformance(false, Double(currentDifficultyLevel.timeLimit * 1000))
                onAnswerSubmitted(false)
                
                gameCompleted = true
            }
        }
    }
    
    private func showHint() {
        guard let data = puzzleData else { return }
        
        let cellsNeeded = requiredCells - selectedCells()
        feedbackMessage = "Hint: You need to select \(cellsNeeded) more cells"
        
        showFeedback = true
        DispatchQueue.main.asyncAfter(deadline: .now() + 2.0) {
            showFeedback = false
        }
    }
    
    // MARK: - Helper Functions
    private func getInstructionText(data: AdaptiveSymmetryPuzzleData) -> String {
        if data.isMirror {
            switch data.mirrorType {
            case "horizontal": return "Create a horizontal mirror reflection ↔️"
            case "vertical": return "Create a vertical mirror reflection ↕️"
            default: return "Copy the exact pattern"
            }
        } else {
            return "Copy the exact pattern as fast as possible!"
        }
    }
    
    private func getMirrorIndicator(data: AdaptiveSymmetryPuzzleData) -> String {
        switch data.mirrorType {
        case "horizontal": return "↔️"
        case "vertical": return "↕️"
        default: return "="
        }
    }
    
    private func formatTime(_ seconds: Int) -> String {
        return String(format: "%02d:%02d", seconds / 60, seconds % 60)
    }
}

// MARK: - Symmetry Grid Component
struct SymmetryGridView: View {
    let pattern: [[Bool]]
    let isInteractive: Bool
    let difficulty: DifficultyManager.DifficultyLevel
    let onCellClick: (Int, Int) -> Void
    
    private var cellSize: CGFloat {
        let gridSize = pattern.count
        switch gridSize {
        case ...3: return 35
        case 4: return 28
        case 5: return 22
        default: return 18
        }
    }
    
    var body: some View {
        VStack(spacing: 1) {
            ForEach(0..<pattern.count, id: \.self) { rowIndex in
                HStack(spacing: 1) {
                    ForEach(0..<(pattern.isEmpty ? 0 : pattern[rowIndex].count), id: \.self) { colIndex in
                        Rectangle()
                            .fill(cellColor(row: rowIndex, col: colIndex))
                            .frame(width: cellSize, height: cellSize)
                            .overlay(
                                Rectangle()
                                    .stroke(Color.gray.opacity(0.3), lineWidth: 1)
                            )
                            .onTapGesture {
                                if isInteractive {
                                    onCellClick(rowIndex, colIndex)
                                }
                            }
                    }
                }
            }
        }
        .background(Color.white)
        .cornerRadius(8)
        .overlay(
            RoundedRectangle(cornerRadius: 8)
                .stroke(Color.gray.opacity(0.3), lineWidth: 2)
        )
        .padding(8)
    }
    
    private func cellColor(row: Int, col: Int) -> Color {
        guard row < pattern.count && col < pattern[row].count else {
            return Color.gray.opacity(0.1)
        }
        
        return pattern[row][col] ? 
            Color.black.opacity(0.8) : 
            Color.gray.opacity(0.1)
    }
}

// MARK: - Session Completion Integration
extension AdaptiveSymmetryPuzzleView {
    private var sessionCompletionHandler: some View {
        Group {
            if gameCompleted {
                UnifiedSessionCompletionHandler(
                    puzzleType: "symmetry",
                    sessionScore: totalScore,
                    sessionStats: SessionStatistics(
                        correctAnswers: correctAnswers,
                        totalAnswers: totalAnswers,
                        totalTimeSeconds: Int(Date().timeIntervalSince(sessionStartTime)),
                        bestStreak: currentStreak,
                        currentStreak: currentStreak,
                        totalScore: totalScore,
                        individualTimes: [],
                        puzzleType: "symmetry"
                    ),
                    currentDifficulty: currentDifficultyLevel
                ) { result in
                    sessionResult = result
                    onNextPuzzle()
                }
            }
        }
    }
}