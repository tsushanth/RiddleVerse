//
//  SymmetryPuzzle.swift
//  PuzzleForge
//
//  Complete Symmetry Puzzle Implementation
//  Adaptive difficulty with mirror/copy patterns
//

import SwiftUI
import FirebaseAuth
import Foundation

extension LocalMemoryPuzzleGenerator {
    
    static func generateSymmetry(difficulty: String) -> (String, String) {
        // Determine progression parameters (simplified for initial implementation)
        let consecutiveCorrect = 0
        let currentQuestion = 1
        
        // Determine grid size based on difficulty
        let gridSize = determineSymmetryGridSize(difficulty: difficulty, consecutiveCorrect: consecutiveCorrect)
        
        // Determine if this should be a mirror puzzle
        let isMirror = shouldUseSymmetryMirror(difficulty: difficulty, consecutiveCorrect: consecutiveCorrect, questionNumber: currentQuestion)
        
        // Determine mirror type
        let mirrorType: String
        if isMirror {
            switch difficulty.lowercased() {
            case "easy":
                mirrorType = "horizontal"
            case "medium", "hard":
                mirrorType = Bool.random() ? "horizontal" : "vertical"
            default:
                mirrorType = "horizontal"
            }
        } else {
            mirrorType = "none"
        }
        
        print("🎯 Generating symmetry puzzle:")
        print("   Difficulty: \(difficulty)")
        print("   Grid size: \(gridSize)x\(gridSize)")
        print("   Is mirror: \(isMirror)")
        print("   Mirror type: \(mirrorType)")
        
        // Generate the left pattern (what user needs to copy/mirror)
        let leftPattern = generateSymmetryPattern(gridSize: gridSize, difficulty: difficulty, isMirror: isMirror)
        
        // Generate the correct right pattern based on mirror mode
        let correctRightPattern: [[Bool]]
        if isMirror {
            if mirrorType == "horizontal" {
                correctRightPattern = mirrorPatternHorizontally(leftPattern)
            } else if mirrorType == "vertical" {
                correctRightPattern = mirrorPatternVertically(leftPattern)
            } else {
                correctRightPattern = leftPattern
            }
        } else {
            correctRightPattern = leftPattern // exact copy
        }
        
        // Create the puzzle data JSON
        let questionJson: [String: Any] = [
            "difficulty": difficulty,
            "gridSize": gridSize,
            "questionNumber": currentQuestion,
            "consecutiveCorrect": consecutiveCorrect,
            "leftPattern": leftPattern,
            "isMirror": isMirror,
            "mirrorType": mirrorType,
            "maxQuestions": getSymmetryMaxQuestions(difficulty: difficulty),
            "instructions": isMirror ? "Create a \(mirrorType) mirror reflection" : "Copy the exact pattern",
            "gameType": "symmetry"
        ]
        
        // Create the answer JSON
        let answerJson: [String: Any] = [
            "correctPattern": correctRightPattern,
            "gridSize": gridSize,
            "isMirror": isMirror,
            "mirrorType": mirrorType,
            "expectedScore": calculateSymmetryExpectedScore(gridSize: gridSize, isMirror: isMirror)
        ]
        
        let filledCells = countSymmetryFilledCells(pattern: leftPattern)
        print("✅ Generated \(isMirror ? "mirror" : "copy") puzzle with \(filledCells) filled cells")
        
        // Convert to JSON strings
        guard let questionData = try? JSONSerialization.data(withJSONObject: questionJson, options: []),
              let answerData = try? JSONSerialization.data(withJSONObject: answerJson, options: []),
              let questionString = String(data: questionData, encoding: .utf8),
              let answerString = String(data: answerData, encoding: .utf8) else {
            return ("{}", "{}")
        }
        
        return (questionString, answerString)
    }
    
    // MARK: - Grid Size Determination
    
    private static func determineSymmetryGridSize(difficulty: String, consecutiveCorrect: Int) -> Int {
        switch difficulty.lowercased() {
        case "easy":
            if consecutiveCorrect >= 10 {
                return 4
            } else if consecutiveCorrect >= 5 {
                return 3
            } else {
                return 3
            }
            
        case "medium":
            if consecutiveCorrect >= 15 {
                return 5
            } else if consecutiveCorrect >= 8 {
                return 4
            } else if consecutiveCorrect >= 3 {
                return 3
            } else {
                return 3
            }
            
        case "hard":
            if consecutiveCorrect >= 20 {
                return 6
            } else if consecutiveCorrect >= 12 {
                return 5
            } else if consecutiveCorrect >= 6 {
                return 4
            } else if consecutiveCorrect >= 2 {
                return 3
            } else {
                return 3
            }
            
        default:
            return 3
        }
    }
    
    // MARK: - Mirror Mode Determination
    
    private static func shouldUseSymmetryMirror(difficulty: String, consecutiveCorrect: Int, questionNumber: Int) -> Bool {
        // TESTING VERSION - 50% chance for mirrors to make them appear more frequently
        if questionNumber <= 3 {
            return questionNumber % 2 == 0 // Every other question for first 3
        }
        
        switch difficulty.lowercased() {
        case "easy":
            if consecutiveCorrect >= 1 {
                return (questionNumber % 3) == 0 // Every 3rd question is a mirror
            } else {
                return questionNumber % 4 == 0 // Every 4th question
            }
            
        case "medium":
            if consecutiveCorrect >= 1 {
                return (questionNumber % 2) == 0 // Every other question is a mirror
            } else {
                return (questionNumber % 3) == 0 // Every 3rd question
            }
            
        case "hard":
            if consecutiveCorrect >= 1 {
                return (questionNumber % 3) != 0 // 2 out of 3 questions are mirrors
            } else {
                return (questionNumber % 2) == 0 // Every other question
            }
            
        default:
            return (questionNumber % 2) == 0 // 50% chance
        }
    }
    
    // MARK: - Pattern Generation
    
    private static func generateSymmetryPattern(gridSize: Int, difficulty: String, isMirror: Bool) -> [[Bool]] {
        var pattern = Array(repeating: Array(repeating: false, count: gridSize), count: gridSize)
        
        // Determine fill density based on difficulty
        let fillDensity: Double
        switch difficulty.lowercased() {
        case "easy":
            fillDensity = isMirror ? 0.25 : 0.30
        case "medium":
            fillDensity = isMirror ? 0.35 : 0.40
        case "hard":
            fillDensity = isMirror ? 0.45 : 0.50
        default:
            fillDensity = 0.30
        }
        
        // Calculate target number of filled cells
        let totalCells = gridSize * gridSize
        let targetFilledCells = max(1, min(totalCells - 1, Int(Double(totalCells) * fillDensity)))
        
        // Create patterns optimized for the type of puzzle
        if isMirror && Double.random(in: 0...1) < 0.6 {
            // 60% chance for asymmetric patterns that look good when mirrored
            createAsymmetricSymmetryPattern(&pattern, targetCells: targetFilledCells, gridSize: gridSize)
        } else if Double.random(in: 0...1) < 0.4 {
            // 40% chance for structured patterns (easier to recognize quickly)
            createStructuredSymmetryPattern(&pattern, targetCells: targetFilledCells, gridSize: gridSize)
        } else {
            // Random patterns
            createRandomSymmetryPattern(&pattern, targetCells: targetFilledCells, gridSize: gridSize)
        }
        
        return pattern
    }
    
    // MARK: - Pattern Creation Methods
    
    private static func createAsymmetricSymmetryPattern(_ pattern: inout [[Bool]], targetCells: Int, gridSize: Int) {
        let patternTypes: [String]
        switch gridSize {
        case 3:
            patternTypes = ["L_shape", "step", "corner_cluster", "diagonal"]
        case 4:
            patternTypes = ["L_shape", "step", "corner_cluster", "diagonal", "zigzag"]
        case 5:
            patternTypes = ["L_shape", "step", "spiral", "diagonal", "zigzag"]
        case 6:
            patternTypes = ["L_shape", "step", "spiral", "diagonal", "zigzag", "wave"]
        default:
            patternTypes = ["L_shape", "step", "diagonal"]
        }
        
        guard let selectedPattern = patternTypes.randomElement() else { return }
        
        switch selectedPattern {
        case "L_shape":
            let startRow = Int.random(in: 0..<(gridSize - 2))
            let startCol = Int.random(in: 0..<(gridSize - 2))
            let armLength = Int.random(in: 2..<(gridSize - startRow))
            
            // Vertical arm
            for i in 0..<armLength where startRow + i < gridSize {
                pattern[startRow + i][startCol] = true
            }
            // Horizontal arm
            for i in 0..<armLength where startCol + i < gridSize {
                pattern[startRow][startCol + i] = true
            }
            
        case "step":
            for i in 0..<(gridSize - 1) {
                if i < gridSize && i + 1 < gridSize {
                    pattern[i][i] = true
                    pattern[i + 1][i] = true
                }
            }
            
        case "diagonal":
            let offset = Int.random(in: -1...1)
            for i in 0..<gridSize {
                let col = i + offset
                if col >= 0 && col < gridSize {
                    pattern[i][col] = true
                }
            }
            
        default:
            createRandomSymmetryPattern(&pattern, targetCells: targetCells, gridSize: gridSize)
        }
        
        adjustSymmetryPatternToTarget(&pattern, targetCells: targetCells, gridSize: gridSize)
    }
    
    private static func createStructuredSymmetryPattern(_ pattern: inout [[Bool]], targetCells: Int, gridSize: Int) {
        let patternTypes: [String]
        switch gridSize {
        case 3:
            patternTypes = ["cross", "corners", "line", "L_shape"]
        case 4:
            patternTypes = ["cross", "corners", "diamond", "line", "square"]
        case 5:
            patternTypes = ["plus", "corners", "diamond", "cross", "border"]
        case 6:
            patternTypes = ["plus", "diamond", "border", "cross", "corners"]
        default:
            patternTypes = ["cross", "corners", "line"]
        }
        
        guard let selectedPattern = patternTypes.randomElement() else { return }
        
        switch selectedPattern {
        case "cross":
            let center = gridSize / 2
            for row in 0..<gridSize {
                pattern[row][center] = true
            }
            for col in 0..<gridSize {
                pattern[center][col] = true
            }
            
        case "corners":
            pattern[0][0] = true
            pattern[0][gridSize - 1] = true
            pattern[gridSize - 1][0] = true
            pattern[gridSize - 1][gridSize - 1] = true
            
        case "diamond":
            let center = gridSize / 2
            for i in 0..<gridSize {
                for j in 0..<gridSize {
                    if abs(i - center) + abs(j - center) == center {
                        pattern[i][j] = true
                    }
                }
            }
            
        case "line":
            let isVertical = Bool.random()
            let linePos = Int.random(in: 0..<gridSize)
            if isVertical {
                for row in 0..<gridSize {
                    pattern[row][linePos] = true
                }
            } else {
                for col in 0..<gridSize {
                    pattern[linePos][col] = true
                }
            }
            
        default:
            createRandomSymmetryPattern(&pattern, targetCells: targetCells, gridSize: gridSize)
        }
        
        adjustSymmetryPatternToTarget(&pattern, targetCells: targetCells, gridSize: gridSize)
    }
    
    private static func createRandomSymmetryPattern(_ pattern: inout [[Bool]], targetCells: Int, gridSize: Int) {
        var positions: [(Int, Int)] = []
        for row in 0..<gridSize {
            for col in 0..<gridSize {
                positions.append((row, col))
            }
        }
        positions.shuffle()
        
        for i in 0..<min(targetCells, positions.count) {
            let (row, col) = positions[i]
            pattern[row][col] = true
        }
    }
    
    private static func adjustSymmetryPatternToTarget(_ pattern: inout [[Bool]], targetCells: Int, gridSize: Int) {
        let currentCount = pattern.flatMap { $0 }.filter { $0 }.count
        
        if currentCount < targetCells {
            // Add more cells
            var emptyCells: [(Int, Int)] = []
            for row in 0..<gridSize {
                for col in 0..<gridSize {
                    if !pattern[row][col] {
                        emptyCells.append((row, col))
                    }
                }
            }
            emptyCells.shuffle()
            
            let toAdd = min(targetCells - currentCount, emptyCells.count)
            for i in 0..<toAdd {
                let (row, col) = emptyCells[i]
                pattern[row][col] = true
            }
        } else if currentCount > targetCells {
            // Remove some cells
            var filledCells: [(Int, Int)] = []
            for row in 0..<gridSize {
                for col in 0..<gridSize {
                    if pattern[row][col] {
                        filledCells.append((row, col))
                    }
                }
            }
            filledCells.shuffle()
            
            let toRemove = currentCount - targetCells
            for i in 0..<toRemove {
                let (row, col) = filledCells[i]
                pattern[row][col] = false
            }
        }
    }
    
    // MARK: - Mirror Transformations
    
    private static func mirrorPatternHorizontally(_ pattern: [[Bool]]) -> [[Bool]] {
        return pattern.map { $0.reversed() }
    }
    
    private static func mirrorPatternVertically(_ pattern: [[Bool]]) -> [[Bool]] {
        return pattern.reversed()
    }
    
    // MARK: - Helper Functions
    
    private static func countSymmetryFilledCells(pattern: [[Bool]]) -> Int {
        return pattern.flatMap { $0 }.filter { $0 }.count
    }
    
    private static func calculateSymmetryExpectedScore(gridSize: Int, isMirror: Bool) -> Int {
        let baseScore = gridSize * gridSize * 15 // 15 points per cell
        let mirrorBonus = isMirror ? Int(Double(baseScore) * 0.5) : 0 // 50% bonus for mirror puzzles
        return baseScore + mirrorBonus
    }
    
    private static func getSymmetryMaxQuestions(difficulty: String) -> Int {
        switch difficulty.lowercased() {
        case "easy": return 30
        case "medium": return 40
        case "hard": return 50
        default: return 40
        }
    }
}
// MARK: - Models

struct SymmetryPuzzleData {
    let gridSize: Int
    let leftPattern: [[Bool]]
    let correctRightPattern: [[Bool]]
    let difficulty: String
    let isMirror: Bool
    let mirrorType: String // "horizontal", "vertical", or "none"
}

struct SymmetryDifficultyLevel {
    let index: Int
    let name: String
    let description: String
    let gridSize: Int
    let timeLimit: Int
    let livesAllowed: Int
    let scoreMultiplier: Double
    let mirrorProbability: Double
    
    static let levels: [SymmetryDifficultyLevel] = [
        SymmetryDifficultyLevel(index: 0, name: "Beginner", description: "3x3 • Copy only", gridSize: 3, timeLimit: 45, livesAllowed: 5, scoreMultiplier: 1.0, mirrorProbability: 0.0),
        SymmetryDifficultyLevel(index: 1, name: "Easy", description: "3x3 • Some mirrors", gridSize: 3, timeLimit: 40, livesAllowed: 4, scoreMultiplier: 1.2, mirrorProbability: 0.3),
        SymmetryDifficultyLevel(index: 2, name: "Medium", description: "4x4 • Mixed patterns", gridSize: 4, timeLimit: 35, livesAllowed: 3, scoreMultiplier: 1.5, mirrorProbability: 0.5),
        SymmetryDifficultyLevel(index: 3, name: "Hard", description: "5x5 • More mirrors", gridSize: 5, timeLimit: 30, livesAllowed: 3, scoreMultiplier: 2.0, mirrorProbability: 0.7),
        SymmetryDifficultyLevel(index: 4, name: "Expert", description: "6x6 • Master level", gridSize: 6, timeLimit: 25, livesAllowed: 2, scoreMultiplier: 2.5, mirrorProbability: 0.8)
    ]
    
    static func getLevel(index: Int) -> SymmetryDifficultyLevel {
        return levels[min(max(index, 0), levels.count - 1)]
    }
}

struct SymmetryAdaptationInfo {
    let previousLevel: SymmetryDifficultyLevel
    let newLevel: SymmetryDifficultyLevel
    let reason: String
    let confidenceScore: Float
}

// MARK: - View Model

class SymmetryPuzzleViewModel: ObservableObject {
    @Published var currentDifficulty: SymmetryDifficultyLevel
    @Published var puzzleData: SymmetryPuzzleData
    @Published var userRightPattern: [[Bool]]
    @Published var adaptationInfo: SymmetryAdaptationInfo?
    
    private var performanceHistory: [(isCorrect: Bool, timeSpent: Double, streak: Int)] = []
    
    init() {
        // Start at Easy difficulty
        let initialDifficulty = SymmetryDifficultyLevel.levels[1]
        self.currentDifficulty = initialDifficulty
        self.puzzleData = SymmetryPuzzleViewModel.generatePuzzle(for: initialDifficulty)
        self.userRightPattern = Array(repeating: Array(repeating: false, count: initialDifficulty.gridSize), count: initialDifficulty.gridSize)
    }
    
    func toggleCell(row: Int, col: Int) {
        userRightPattern[row][col].toggle()
    }
    
    func resetUserPattern() {
        userRightPattern = Array(repeating: Array(repeating: false, count: currentDifficulty.gridSize), count: currentDifficulty.gridSize)
    }
    
    func generateNewPuzzle() {
        puzzleData = SymmetryPuzzleViewModel.generatePuzzle(for: currentDifficulty)
        resetUserPattern()
    }
    
    func recordPerformance(isCorrect: Bool, timeSpent: Double, streak: Int) {
        performanceHistory.append((isCorrect, timeSpent, streak))
        
        // Keep only last 10 performances
        if performanceHistory.count > 10 {
            performanceHistory.removeFirst()
        }
        
        // Check if we should adapt difficulty
        if performanceHistory.count >= 3 {
            checkForAdaptation()
        }
    }
    
    private func checkForAdaptation() {
        let recentPerformances = Array(performanceHistory.suffix(5))
        let correctCount = recentPerformances.filter { $0.isCorrect }.count
        let avgTime = recentPerformances.map { $0.timeSpent }.reduce(0, +) / Double(recentPerformances.count)
        let maxTime = Double(currentDifficulty.timeLimit)
        
        let successRate = Double(correctCount) / Double(recentPerformances.count)
        let speedScore = 1.0 - (avgTime / maxTime)
        let confidenceScore = Float((successRate + speedScore) / 2.0)
        
        var shouldIncrease = false
        var shouldDecrease = false
        var reason = ""
        
        // Increase difficulty if performing well
        if successRate >= 0.8 && speedScore > 0.3 && currentDifficulty.index < SymmetryDifficultyLevel.levels.count - 1 {
            shouldIncrease = true
            reason = "Great performance! Moving to harder puzzles"
        }
        // Decrease difficulty if struggling
        else if successRate <= 0.4 && currentDifficulty.index > 0 {
            shouldDecrease = true
            reason = "Let's try slightly easier puzzles"
        }
        
        if shouldIncrease || shouldDecrease {
            let previousLevel = currentDifficulty
            let newIndex = shouldIncrease ? currentDifficulty.index + 1 : currentDifficulty.index - 1
            currentDifficulty = SymmetryDifficultyLevel.getLevel(index: newIndex)
            
            adaptationInfo = SymmetryAdaptationInfo(
                previousLevel: previousLevel,
                newLevel: currentDifficulty,
                reason: reason,
                confidenceScore: confidenceScore
            )
            
            generateNewPuzzle()
        }
    }
    
    static func generatePuzzle(for difficulty: SymmetryDifficultyLevel) -> SymmetryPuzzleData {
        let gridSize = difficulty.gridSize
        let isMirror = Double.random(in: 0...1) < difficulty.mirrorProbability
        
        let mirrorType: String
        if isMirror {
            if difficulty.index <= 1 {
                mirrorType = "horizontal"
            } else {
                mirrorType = Bool.random() ? "horizontal" : "vertical"
            }
        } else {
            mirrorType = "none"
        }
        
        // Generate left pattern
        let leftPattern = generatePattern(gridSize: gridSize, difficulty: difficulty, isMirror: isMirror)
        
        // Generate correct right pattern
        let correctRightPattern: [[Bool]]
        if isMirror {
            correctRightPattern = mirrorType == "horizontal" ? mirrorHorizontally(leftPattern) : mirrorVertically(leftPattern)
        } else {
            correctRightPattern = leftPattern
        }
        
        return SymmetryPuzzleData(
            gridSize: gridSize,
            leftPattern: leftPattern,
            correctRightPattern: correctRightPattern,
            difficulty: difficulty.name,
            isMirror: isMirror,
            mirrorType: mirrorType
        )
    }
    
    private static func generatePattern(gridSize: Int, difficulty: SymmetryDifficultyLevel, isMirror: Bool) -> [[Bool]] {
        var pattern = Array(repeating: Array(repeating: false, count: gridSize), count: gridSize)
        
        let fillDensity: Double = isMirror ? 0.25 + Double(difficulty.index) * 0.05 : 0.30 + Double(difficulty.index) * 0.05
        let totalCells = gridSize * gridSize
        let targetFilledCells = Int(Double(totalCells) * fillDensity)
        
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
    
    private static func mirrorHorizontally(_ pattern: [[Bool]]) -> [[Bool]] {
        return pattern.reversed()
    }
    
    private static func mirrorVertically(_ pattern: [[Bool]]) -> [[Bool]] {
        return pattern.map { $0.reversed() }
    }
    
    func checkAnswer() -> Bool {
        return userRightPattern == puzzleData.correctRightPattern
    }
}

// MARK: - Main View

struct SymmetryPuzzleView: View {
    let timer: String
    let hearts: Int
    let level: String
    let onSubmitAnswer: (Bool) -> Void
    let fetchNextPuzzle: (Int) -> Void
    let onBack: () -> Void
    
    @StateObject private var viewModel = SymmetryPuzzleViewModel()
    @State private var showAdaptationNotification = false
    @State private var showFeedback = false
    @State private var isCorrect = false
    @State private var feedbackMessage = ""
    @State private var score = 0
    @State private var totalScore = 0
    @State private var timeRemaining = 30
    @State private var isTimerRunning = true
    @State private var currentHearts = 3
    @State private var puzzleStartTime = Date()
    @State private var currentStreak = 0
    @State private var gamesPlayedThisSession = 0
    @State private var sessionStartTime = Date()
    @State private var correctAnswers = 0
    @State private var totalAnswers = 0
    @State private var gameCompleted = false
    
    var body: some View {
        ZStack {
            Color(red: 0.96, green: 0.96, blue: 0.96)
                .ignoresSafeArea()
            
            VStack(spacing: 16) {
                // Header
                headerView
                
                Spacer().frame(height: 8)
                
                // Adaptation notification
                if showAdaptationNotification, let info = viewModel.adaptationInfo {
                    adaptationNotificationView(info: info)
                        .transition(.move(edge: .top).combined(with: .opacity))
                }
                
                // Title and mode indicator
                titleSection
                
                // Instructions
                instructionText
                
                // Progress card
                if totalScore > 0 || gamesPlayedThisSession > 0 {
                    progressCard
                }
                
                Spacer().frame(height: 16)
                
                // Main puzzle grids
                puzzleGridsSection
                
                Spacer().frame(height: 16)
                
                // Feedback section
                if showFeedback {
                    feedbackView
                }
                
                Spacer()
            }
            .padding(.horizontal, 16)
        }
        .navigationBarHidden(true)
        .onAppear {
            setupPuzzle()
        }
        .onChange(of: viewModel.userRightPattern) { _ in
            checkAutoSubmit()
        }
    }
    
    // MARK: - Header
    
    private var headerView: some View {
        HStack {
            // Back button and level
            HStack(spacing: 12) {
                Button(action: onBack) {
                    Image(systemName: "chevron.left")
                        .font(.system(size: 20, weight: .medium))
                        .foregroundColor(Color(red: 0.18, green: 0.18, blue: 0.18))
                }
                
                VStack(alignment: .leading, spacing: 2) {
                    Text("Level \(level)")
                        .font(.system(size: 14, weight: .medium))
                        .foregroundColor(.gray)
                    
                    Text(viewModel.currentDifficulty.name)
                        .font(.system(size: 12, weight: .bold))
                        .foregroundColor(Color(red: 0.13, green: 0.59, blue: 0.95))
                }
            }
            
            Spacer()
            
            // Timer
            Text(formatTime(timeRemaining))
                .font(.system(size: 14, weight: .medium))
                .foregroundColor(Color(red: 0.13, green: 0.59, blue: 0.95))
            
            Spacer().frame(width: 16)
            
            // Hearts
            HStack(spacing: 4) {
                ForEach(0..<viewModel.currentDifficulty.livesAllowed, id: \.self) { index in
                    Text(index < currentHearts ? "❤️" : "🤍")
                        .font(.system(size: 16))
                }
            }
        }
    }
    
    // MARK: - Title Section
    
    private var titleSection: some View {
        HStack(spacing: 8) {
            Text("Symmetry")
                .font(.system(size: 28, weight: .bold))
                .foregroundColor(Color(red: 0.18, green: 0.18, blue: 0.18))
            
            // Mirror/Copy indicator
            HStack(spacing: 6) {
                Text(viewModel.puzzleData.isMirror ? "🪞 MIRROR" : "📋 COPY")
                    .font(.system(size: 12, weight: .bold))
                    .foregroundColor(viewModel.puzzleData.isMirror ? Color(red: 0.10, green: 0.46, blue: 0.82) : Color(red: 0.48, green: 0.12, blue: 0.64))
            }
            .padding(.horizontal, 8)
            .padding(.vertical, 4)
            .background(
                RoundedRectangle(cornerRadius: 8)
                    .fill(viewModel.puzzleData.isMirror ? Color(red: 0.89, green: 0.95, blue: 0.99) : Color(red: 0.95, green: 0.90, blue: 0.96))
            )
            
            Spacer()
        }
    }
    
    // MARK: - Instructions
    
    private var instructionText: some View {
        VStack(alignment: .leading, spacing: 4) {
            if viewModel.puzzleData.isMirror {
                switch viewModel.puzzleData.mirrorType {
                case "horizontal":
                    Text("Create a horizontal mirror reflection ↔️")
                        .font(.system(size: 14))
                        .foregroundColor(.gray)
                case "vertical":
                    Text("Create a vertical mirror reflection ↕️")
                        .font(.system(size: 14))
                        .foregroundColor(.gray)
                default:
                    Text("Copy the exact pattern")
                        .font(.system(size: 14))
                        .foregroundColor(.gray)
                }
            } else {
                Text("Copy the exact pattern as fast as possible!")
                    .font(.system(size: 14))
                    .foregroundColor(.gray)
            }
            
            Text(viewModel.currentDifficulty.description)
                .font(.system(size: 12, weight: .medium))
                .foregroundColor(Color(red: 0.13, green: 0.59, blue: 0.95))
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }
    
    // MARK: - Progress Card
    
    private var progressCard: some View {
        HStack {
            if totalScore > 0 {
                VStack {
                    Text("SCORE")
                        .font(.system(size: 10, weight: .bold))
                        .foregroundColor(.gray)
                    Text("\(totalScore)")
                        .font(.system(size: 16, weight: .bold))
                        .foregroundColor(Color(red: 0.13, green: 0.59, blue: 0.95))
                }
            }
            
            Spacer()
            
            VStack {
                Text(viewModel.currentDifficulty.name.uppercased())
                    .font(.system(size: 10, weight: .bold))
                    .foregroundColor(Color(red: 0.13, green: 0.59, blue: 0.95))
                Text("\(viewModel.puzzleData.gridSize)×\(viewModel.puzzleData.gridSize)")
                    .font(.system(size: 12))
                    .foregroundColor(.gray)
            }
            
            Spacer()
            
            VStack {
                Text("CELLS")
                    .font(.system(size: 10, weight: .bold))
                    .foregroundColor(.gray)
                let selectedCount = viewModel.userRightPattern.flatMap { $0 }.filter { $0 }.count
                let requiredCount = viewModel.puzzleData.leftPattern.flatMap { $0 }.filter { $0 }.count
                Text("\(selectedCount) / \(requiredCount)")
                    .font(.system(size: 14, weight: .medium))
                    .foregroundColor(selectedCount == requiredCount ? Color(red: 0.30, green: 0.69, blue: 0.31) : .gray)
            }
            
            if currentStreak > 0 {
                Spacer()
                
                VStack {
                    Text("STREAK")
                        .font(.system(size: 10, weight: .bold))
                        .foregroundColor(.gray)
                    Text("🔥 \(currentStreak)")
                        .font(.system(size: 14, weight: .bold))
                        .foregroundColor(Color(red: 1.0, green: 0.44, blue: 0.0))
                }
            }
        }
        .padding(12)
        .background(
            RoundedRectangle(cornerRadius: 8)
                .fill(Color.white.opacity(0.9))
        )
    }
    
    // MARK: - Puzzle Grids
    
    private var puzzleGridsSection: some View {
        HStack(spacing: 20) {
            // Left grid (pattern to copy/mirror)
            VStack(alignment: .center, spacing: 8) {
                Text(viewModel.puzzleData.isMirror ? "Mirror this:" : "Copy this:")
                    .font(.system(size: 12))
                    .foregroundColor(.gray)
                
                SymmetryGrid(
                    pattern: viewModel.puzzleData.leftPattern,
                    isInteractive: false,
                    gridSize: viewModel.puzzleData.gridSize,
                    onCellTap: { _, _ in }
                )
            }
            
            // Divider with mirror indicator
            VStack {
                if viewModel.puzzleData.isMirror {
                    Text(viewModel.puzzleData.mirrorType == "horizontal" ? "↔️" : "↕️")
                        .font(.system(size: 20))
                        .foregroundColor(Color(red: 0.13, green: 0.59, blue: 0.95))
                        .padding(.bottom, 4)
                }
                
                Rectangle()
                    .fill(viewModel.puzzleData.isMirror ? Color(red: 0.13, green: 0.59, blue: 0.95) : Color(red: 0.88, green: 0.88, blue: 0.88))
                    .frame(width: 2, height: viewModel.puzzleData.isMirror ? 100 : 120)
            }
            
            // Right grid (user input)
            VStack(alignment: .center, spacing: 8) {
                Text("To here:")
                    .font(.system(size: 12))
                    .foregroundColor(.gray)
                
                SymmetryGrid(
                    pattern: viewModel.userRightPattern,
                    isInteractive: !showFeedback,
                    gridSize: viewModel.puzzleData.gridSize,
                    onCellTap: { row, col in
                        if !showFeedback {
                            viewModel.toggleCell(row: row, col: col)
                        }
                    }
                )
            }
        }
    }
    
    // MARK: - Feedback View
    
    private var feedbackView: some View {
        VStack(spacing: 8) {
            Text(feedbackMessage)
                .font(.system(size: 18, weight: .bold))
                .foregroundColor(isCorrect ? Color(red: 0.18, green: 0.49, blue: 0.20) : Color(red: 0.90, green: 0.32, blue: 0.0))
                .multilineTextAlignment(.center)
            
            if isCorrect {
                Text("+\(score) pts")
                    .font(.system(size: 20, weight: .bold))
                    .foregroundColor(Color(red: 0.18, green: 0.49, blue: 0.20))
                
                Text("Difficulty: \(viewModel.currentDifficulty.name) • Speed Bonus! ⚡")
                    .font(.system(size: 12, weight: .medium))
                    .foregroundColor(Color(red: 0.18, green: 0.49, blue: 0.20))
            }
        }
        .padding(16)
        .frame(maxWidth: .infinity)
        .background(
            RoundedRectangle(cornerRadius: 12)
                .fill(isCorrect ? Color(red: 0.91, green: 0.96, blue: 0.91) : Color(red: 1.0, green: 0.95, blue: 0.88))
        )
    }
    
    // MARK: - Adaptation Notification
    
    private func adaptationNotificationView(info: SymmetryAdaptationInfo) -> some View {
        HStack(spacing: 12) {
            Image(systemName: "arrow.up.forward.circle.fill")
                .font(.system(size: 24))
                .foregroundColor(Color(red: 0.13, green: 0.59, blue: 0.95))
            
            VStack(alignment: .leading, spacing: 4) {
                Text("Difficulty Updated!")
                    .font(.system(size: 14, weight: .bold))
                    .foregroundColor(Color(red: 0.18, green: 0.18, blue: 0.18))
                
                Text("\(info.previousLevel.name) → \(info.newLevel.name)")
                    .font(.system(size: 12))
                    .foregroundColor(.gray)
                
                Text(info.reason)
                    .font(.system(size: 11))
                    .foregroundColor(Color(red: 0.13, green: 0.59, blue: 0.95))
            }
            
            Spacer()
            
            Button(action: {
                withAnimation {
                    showAdaptationNotification = false
                }
            }) {
                Image(systemName: "xmark.circle.fill")
                    .foregroundColor(.gray)
            }
        }
        .padding(12)
        .background(
            RoundedRectangle(cornerRadius: 12)
                .fill(Color.white)
                .shadow(color: .black.opacity(0.1), radius: 8, x: 0, y: 4)
        )
    }
    
    // MARK: - Helper Functions
    
    private func setupPuzzle() {
        timeRemaining = viewModel.currentDifficulty.timeLimit
        currentHearts = viewModel.currentDifficulty.livesAllowed
        puzzleStartTime = Date()
        isTimerRunning = true
        
        // Start timer
        Timer.scheduledTimer(withTimeInterval: 1.0, repeats: true) { timer in
            if isTimerRunning && timeRemaining > 0 {
                timeRemaining -= 1
            } else if timeRemaining <= 0 {
                timer.invalidate()
                handleTimeout()
            }
        }
    }
    
    private func checkAutoSubmit() {
        if showFeedback { return }
        
        let selectedCount = viewModel.userRightPattern.flatMap { $0 }.filter { $0 }.count
        let requiredCount = viewModel.puzzleData.leftPattern.flatMap { $0 }.filter { $0 }.count
        
        if selectedCount == requiredCount && requiredCount > 0 {
            submitAnswer()
        }
    }
    
    private func submitAnswer() {
        isTimerRunning = false
        let timeSpent = Date().timeIntervalSince(puzzleStartTime)
        let correct = viewModel.checkAnswer()
        isCorrect = correct
        totalAnswers += 1
        
        if correct {
            correctAnswers += 1
            currentStreak += 1
            gamesPlayedThisSession += 1
            
            // Calculate score
            score = calculateScore(timeSpent: timeSpent)
            totalScore += score
            
            // Feedback message
            if timeSpent < 1.0 {
                feedbackMessage = "Lightning fast! ⚡️"
            } else if timeSpent < 2.0 {
                feedbackMessage = "Super quick! 🚀"
            } else if timeSpent < 4.0 {
                feedbackMessage = "Nice speed! ✨"
            } else {
                feedbackMessage = "Perfect! 🎯"
            }
            
            viewModel.recordPerformance(isCorrect: true, timeSpent: timeSpent, streak: currentStreak)
            
            // Show adaptation notification if needed
            if viewModel.adaptationInfo != nil {
                DispatchQueue.main.asyncAfter(deadline: .now() + 0.3) {
                    withAnimation {
                        showAdaptationNotification = true
                    }
                }
            }
        } else {
            currentHearts = max(0, currentHearts - 1)
            currentStreak = 0
            
            feedbackMessage = timeSpent < 1.0 ? "Too fast! Look closer 👀" : "Not quite right 🤔"
            
            viewModel.recordPerformance(isCorrect: false, timeSpent: timeSpent, streak: 0)
        }
        
        withAnimation {
            showFeedback = true
        }
        
        onSubmitAnswer(correct)
        
        // Move to next puzzle or end game
        DispatchQueue.main.asyncAfter(deadline: .now() + (correct ? 1.2 : 1.8)) {
            if correct {
                // ✅ Properly fetch next puzzle via handlePuzzleCompletion
                gameCompleted = true
                self.fetchNextPuzzle(1)  // This triggers HomeView's handlePuzzleCompletion
            } else if currentHearts <= 0 {
                // Game over - end session
                gameCompleted = true
                self.fetchNextPuzzle(0)  // End session with 0 to indicate failure
            } else {
                // Wrong answer but still has hearts - try again with same puzzle
                nextPuzzle()
            }
        }
    }
    
    private func handleTimeout() {
        isCorrect = false
        feedbackMessage = "Time's up! ⏰"
        currentStreak = 0
        totalAnswers += 1
        
        withAnimation {
            showFeedback = true
        }
        
        viewModel.recordPerformance(isCorrect: false, timeSpent: Double(viewModel.currentDifficulty.timeLimit), streak: 0)
        onSubmitAnswer(false)
        
        DispatchQueue.main.asyncAfter(deadline: .now() + 1.8) {
            gameCompleted = true
            self.fetchNextPuzzle(0)  // End session due to timeout
        }
    }
    
    private func nextPuzzle() {
        showFeedback = false
        viewModel.generateNewPuzzle()
        setupPuzzle()
    }
    
    private func calculateScore(timeSpent: TimeInterval) -> Int {
        let baseScore = 100
        let timeBonus = max(0, Int((Double(viewModel.currentDifficulty.timeLimit) - timeSpent) * 10))
        let difficultyBonus = Int(Double(baseScore) * viewModel.currentDifficulty.scoreMultiplier)
        let streakBonus = currentStreak * 50
        
        return baseScore + timeBonus + difficultyBonus + streakBonus
    }
    
    private func formatTime(_ seconds: Int) -> String {
        let minutes = seconds / 60
        let secs = seconds % 60
        return String(format: "%d:%02d", minutes, secs)
    }
}

// MARK: - Grid Component

struct SymmetryGrid: View {
    let pattern: [[Bool]]
    let isInteractive: Bool
    let gridSize: Int
    let onCellTap: (Int, Int) -> Void
    
    private var cellSize: CGFloat {
        switch gridSize {
        case ...3: return 35
        case 4: return 28
        case 5: return 22
        case 6...: return 18
        default: return 20
        }
    }
    
    var body: some View {
        VStack(spacing: 1) {
            ForEach(0..<gridSize, id: \.self) { row in
                HStack(spacing: 1) {
                    ForEach(0..<gridSize, id: \.self) { col in
                        cellView(row: row, col: col)
                    }
                }
            }
        }
        .padding(8)
        .background(
            RoundedRectangle(cornerRadius: 8)
                .fill(Color.white)
                .overlay(
                    RoundedRectangle(cornerRadius: 8)
                        .stroke(Color(red: 0.88, green: 0.88, blue: 0.88), lineWidth: 2)
                )
        )
    }
    
    private func cellView(row: Int, col: Int) -> some View {
        let isSelected = pattern[row][col]
        
        return Rectangle()
            .fill(isSelected ? Color(red: 0.26, green: 0.26, blue: 0.26) : Color(red: 0.96, green: 0.96, blue: 0.96))
            .frame(width: cellSize, height: cellSize)
            .cornerRadius(4)
            .overlay(
                RoundedRectangle(cornerRadius: 4)
                    .stroke(Color(red: 0.88, green: 0.88, blue: 0.88), lineWidth: 1)
            )
            .onTapGesture {
                if isInteractive {
                    onCellTap(row, col)
                }
            }
    }
}

// MARK: - Preview

struct SymmetryPuzzleView_Previews: PreviewProvider {
    static var previews: some View {
        SymmetryPuzzleView(
            timer: "0:30",
            hearts: 3,
            level: "1",
            onSubmitAnswer: { _ in },
            fetchNextPuzzle: { _ in },
            onBack: { }
        )
    }
}
