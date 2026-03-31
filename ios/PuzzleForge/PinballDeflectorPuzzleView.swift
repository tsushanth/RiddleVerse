//
//  DeflectorData.swift
//  PuzzleForge
//
//  Created by Sushanth Tiruvaipati on 7/14/25.
//


import SwiftUI
import Foundation
import Combine

// Add this extension to your Puzzle model file

extension Puzzle {
    var pinballDeflectorPuzzleData: PinballPuzzleData? {
        guard let questionData = question.data(using: .utf8),
              let json = try? JSONSerialization.jsonObject(with: questionData) as? [String: Any] else {
            print("🔴 PINBALL: Failed to parse question as JSON")
            return nil
        }
        
        guard let matrix = json["matrix"] as? [[Int]],
              let matrixSize = json["matrixSize"] as? Int,
              let startPosArray = json["startPosition"] as? [Int],
              let startDirDict = json["startDirection"] as? [String: Any],
              let memoryTime = json["memoryTime"] as? Int,
              startPosArray.count >= 2,
              let startDirName = startDirDict["name"] as? String,
              let startDirDr = startDirDict["dr"] as? Int,
              let startDirDc = startDirDict["dc"] as? Int else {
            print("🔴 PINBALL: Missing required fields in JSON")
            return nil
        }
        
        let startPosition = BallPosition(row: startPosArray[0], col: startPosArray[1])
        let startDirection = BallDirection(dr: startDirDr, dc: startDirDc, name: startDirName)
        
        var deflectors: [DeflectorData] = []
        for i in 0..<matrixSize {
            for j in 0..<matrixSize {
                if i < matrix.count && j < matrix[i].count {
                    let value = matrix[i][j]
                    if value == 1 || value == 2 {
                        deflectors.append(DeflectorData(row: i, col: j, type: value, isVisible: true))
                    }
                }
            }
        }
        
        print("✅ PINBALL: Successfully parsed pinball deflector data")
        print("📊 PINBALL: Matrix size: \(matrixSize)×\(matrixSize)")
        print("📊 PINBALL: Start position: [\(startPosition.row),\(startPosition.col)]")
        print("📊 PINBALL: Start direction: \(startDirection.name)")
        print("📊 PINBALL: Deflectors: \(deflectors.count)")
        print("📊 PINBALL: Memory time: \(memoryTime)ms")
        
        return PinballPuzzleData(
            matrixSize: matrixSize,
            startPosition: startPosition,
            startDirection: startDirection,
            deflectors: deflectors,
            memoryTime: memoryTime,
            difficulty: difficulty
        )
    }
}


struct DeflectorData {
    let row: Int
    let col: Int
    let type: Int // 1 = slash (/), 2 = backslash (\)
    let isVisible: Bool
}

struct BallPosition {
    let row: Int
    let col: Int
}

struct BallDirection {
    let dr: Int
    let dc: Int
    let name: String
}

enum PinballGameState {
    case memorizing     // Show deflectors only - user memorizes positions
    case guessing       // Show start position only - user selects end position
    case showingResult  // Show deflectors + ball path + result
}

struct PinballPuzzleData {
    let matrixSize: Int
    let startPosition: BallPosition
    let startDirection: BallDirection
    let deflectors: [DeflectorData]
    let memoryTime: Int // in milliseconds
    let difficulty: String
}


// MARK: - Adaptive Pinball Configuration
struct AdaptivePinballPuzzleData {
    let matrixSize: Int
    let startPosition: BallPosition
    let startDirection: BallDirection
    let deflectors: [DeflectorData]
    let memoryTime: Int
    let difficulty: String
    let complexity: Int
}

// MARK: - Enhanced Deflector Data with Adaptive Features
struct AdaptiveDeflectorData {
    let row: Int
    let col: Int
    let type: Int // 1 = slash (/), 2 = backslash (\)
    let isVisible: Bool
    var showHint: Bool = false
    var opacity: Double = 1.0
}

// MARK: - Main Adaptive Pinball Deflector Puzzle View
//
//  PinballDeflectorPuzzleView.swift
//  PuzzleForge
//
//  Created by Assistant on 1/15/25.
//

import SwiftUI
import Foundation
import Combine

struct PinballDeflectorPuzzleView: View {
    let puzzle: Puzzle
    let questionIndex: Int
    let totalQuestions: Int
    let onAnswerSubmitted: (BallPosition?, Bool) -> Void
    let onNextPuzzle: () -> Void
    let onExit: () -> Void
    
    // Game state
    @State private var gameState: PinballGameState = .memorizing
    @State private var selectedEndPosition: BallPosition?
    @State private var correctEndPosition: BallPosition?
    @State private var timeLeft: Int = 0
    @State private var ballPath: [BallPosition] = []
    @State private var trajectoryProgress: Double = 0.0
    @State private var isCorrect = false
    @State private var showFeedback = false
    @State private var currentStreak = 0
    @State private var gamesPlayedThisSession = 0
    
    // Session tracking
    @State private var sessionStartTime = Date()
    @State private var correctAttempts = 0
    @State private var totalAttempts = 0
    @State private var totalScore = 0
    @State private var memoryPhaseStartTime = Date()
    @State private var guessingPhaseStartTime = Date()
    
    // Parsed puzzle data
    @State private var puzzleData: PinballPuzzleData?
    
    // Timer management
    @State private var timer: Timer?
    @State private var animationTimer: Timer?
    
    private let boardSize: CGFloat = 300
    
    var body: some View {
        ZStack {
            Color.black
                .ignoresSafeArea()
            
            VStack(spacing: 16) {
                // Header
                headerView
                
                if let data = puzzleData {
                    // Game state indicator
                    gameStateIndicator
                    
                    // Timer (only during memorizing phase)
                    if gameState == .memorizing {
                        memorizationTimerView
                    }
                    
                    Spacer()
                    
                    // Game board
                    PinballGameBoard(
                        puzzleData: data,
                        selectedEndPosition: selectedEndPosition,
                        correctEndPosition: correctEndPosition,
                        ballPath: ballPath,
                        trajectoryProgress: trajectoryProgress,
                        gameState: gameState,
                        boardSize: boardSize,
                        onCellClick: handleCellSelection
                    )
                    
                    Spacer()
                    
                    // Control buttons
                    controlButtons
                } else {
                    ProgressView("Loading puzzle...")
                        .frame(width: boardSize, height: boardSize)
                        .foregroundColor(.white)
                }
            }
            .padding()
        }
        .navigationBarHidden(true)
        .onAppear {
            initializePuzzle()
        }
        .onDisappear {
            timer?.invalidate()
            animationTimer?.invalidate()
        }
        .alert("Challenge Complete", isPresented: $showFeedback) {
            Button("Continue") {
                showFeedback = false
                onAnswerSubmitted(selectedEndPosition, isCorrect)
                onNextPuzzle()
            }
        } message: {
            Text(isCorrect ? "Correct! 🎉" : "Incorrect ❌")
        }
    }
    
    // MARK: - Header View
    private var headerView: some View {
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
                
                VStack {
                    Text("Pinball Deflector")
                        .font(.headline)
                        .fontWeight(.bold)
                        .foregroundColor(.white)
                    
                    Text("Question \(questionIndex + 1) of \(totalQuestions)")
                        .font(.caption)
                        .foregroundColor(.gray)
                }
                
                Spacer()
                
                HStack(spacing: 16) {
                    // Hearts display
                    HStack(spacing: 4) {
                        ForEach(0..<3, id: \.self) { index in
                            Text("❤️")
                                .font(.caption)
                        }
                    }
                }
            }
            
            // Stats display
            if totalScore > 0 || totalAttempts > 0 {
                statsDisplay
            }
        }
        .padding()
        .background(
            RoundedRectangle(cornerRadius: 16)
                .fill(Color.white.opacity(0.1))
        )
    }
    
    // MARK: - Stats Display
    private var statsDisplay: some View {
        HStack {
            VStack(alignment: .leading, spacing: 4) {
                Text("SCORE")
                    .font(.caption)
                    .foregroundColor(.cyan)
                    .fontWeight(.bold)
                
                Text("\(totalScore)")
                    .font(.headline)
                    .foregroundColor(.white)
                    .fontWeight(.bold)
            }
            
            Spacer()
            
            VStack(alignment: .center, spacing: 4) {
                Text("DIFFICULTY")
                    .font(.caption)
                    .foregroundColor(.yellow)
                    .fontWeight(.bold)
                
                if let data = puzzleData {
                    Text("\(data.matrixSize)×\(data.matrixSize) • \(data.deflectors.count) deflectors")
                        .font(.caption2)
                        .foregroundColor(.white.opacity(0.8))
                }
            }
            
            Spacer()
            
            VStack(alignment: .trailing, spacing: 4) {
                if currentStreak > 0 {
                    Text("🔥 \(currentStreak)")
                        .font(.caption)
                        .foregroundColor(.orange)
                        .fontWeight(.bold)
                }
                
                if totalAttempts > 0 {
                    Text("Attempt: \(totalAttempts)")
                        .font(.caption2)
                        .foregroundColor(.white.opacity(0.8))
                }
            }
        }
    }
    
    // MARK: - Game State Indicator
    private var gameStateIndicator: some View {
        Group {
            switch gameState {
            case .memorizing:
                VStack(spacing: 4) {
                    Text("Memorize deflector positions")
                        .font(.title2)
                        .fontWeight(.bold)
                        .foregroundColor(.yellow)
                    
                    if let data = puzzleData {
                        Text("\(data.matrixSize)×\(data.matrixSize) • \(data.deflectors.count) deflectors")
                            .font(.caption)
                            .foregroundColor(.cyan)
                            .fontWeight(.medium)
                    }
                }
                
            case .guessing:
                VStack(spacing: 4) {
                    Text("Predict where the ball will exit")
                        .font(.title2)
                        .fontWeight(.bold)
                        .foregroundColor(.cyan)
                    
                    Text("Trace the ball's path through deflectors")
                        .font(.subheadline)
                        .foregroundColor(.yellow)
                }
                
            case .showingResult:
                VStack(spacing: 4) {
                    Text(isCorrect ? "Correct! 🎉" : "Incorrect ❌")
                        .font(.title)
                        .fontWeight(.bold)
                        .foregroundColor(isCorrect ? .green : .red)
                    
                    if let score = calculateScore() {
                        Text("+\(score) points")
                            .font(.headline)
                            .foregroundColor(.white)
                    }
                }
            }
        }
        .multilineTextAlignment(.center)
        .padding()
    }
    
    // MARK: - Memorization Timer View
    private var memorizationTimerView: some View {
        Text("Time to memorize: \(timeLeft)s")
            .font(.headline)
            .fontWeight(.bold)
            .foregroundColor(.orange)
            .padding()
            .background(Color.orange.opacity(0.1))
            .cornerRadius(8)
    }
    
    // MARK: - Control Buttons
    private var controlButtons: some View {
        HStack(spacing: 20) {
            if gameState == .showingResult {
                Button("Continue Challenge") {
                    onAnswerSubmitted(selectedEndPosition, isCorrect)
                    onNextPuzzle()
                }
                .font(.headline)
                .fontWeight(.semibold)
                .foregroundColor(.white)
                .padding(.horizontal, 24)
                .padding(.vertical, 12)
                .background(Color.blue)
                .cornerRadius(10)
            }
        }
    }
    
    // MARK: - Helper Methods
    
    private func initializePuzzle() {
        parsePuzzleData()
        sessionStartTime = Date()
    }
    
    private func parsePuzzleData() {
        guard let questionData = puzzle.question.data(using: .utf8),
              let json = try? JSONSerialization.jsonObject(with: questionData) as? [String: Any] else {
            print("🔴 Failed to parse pinball puzzle JSON")
            return
        }
        
        guard let matrix = json["matrix"] as? [[Int]],
              let matrixSize = json["matrixSize"] as? Int,
              let startPosArray = json["startPosition"] as? [Int],
              let startDirDict = json["startDirection"] as? [String: Any],
              let memoryTime = json["memoryTime"] as? Int,
              startPosArray.count >= 2,
              let startDirName = startDirDict["name"] as? String,
              let startDirDr = startDirDict["dr"] as? Int,
              let startDirDc = startDirDict["dc"] as? Int else {
            print("🔴 Missing required fields in pinball puzzle JSON")
            return
        }
        
        let startPosition = BallPosition(row: startPosArray[0], col: startPosArray[1])
        let startDirection = BallDirection(dr: startDirDr, dc: startDirDc, name: startDirName)
        
        var deflectors: [DeflectorData] = []
        for i in 0..<matrixSize {
            for j in 0..<matrixSize {
                if i < matrix.count && j < matrix[i].count {
                    let value = matrix[i][j]
                    if value == 1 || value == 2 {
                        deflectors.append(DeflectorData(row: i, col: j, type: value, isVisible: true))
                    }
                }
            }
        }
        
        self.puzzleData = PinballPuzzleData(
            matrixSize: matrixSize,
            startPosition: startPosition,
            startDirection: startDirection,
            deflectors: deflectors,
            memoryTime: memoryTime,
            difficulty: puzzle.difficulty
        )
        
        // Start the game
        startGame()
        
        print("✅ Parsed pinball puzzle:")
        print("   Matrix size: \(matrixSize)×\(matrixSize)")
        print("   Start: [\(startPosition.row),\(startPosition.col)] \(startDirection.name)")
        print("   Deflectors: \(deflectors.count)")
        print("   Memory time: \(memoryTime)ms")
    }
    
    private func startGame() {
        guard let data = puzzleData else { return }
        
        // Start memorization phase
        gameState = .memorizing
        timeLeft = data.memoryTime / 1000
        memoryPhaseStartTime = Date()
        startMemorizationTimer()
        
        print("🎱 Starting pinball challenge:")
        print("   Matrix: \(data.matrixSize)×\(data.matrixSize)")
        print("   Deflectors: \(data.deflectors.count)")
        print("   Memory time: \(data.memoryTime/1000)s")
    }
    
    private func startMemorizationTimer() {
        timer = Timer.scheduledTimer(withTimeInterval: 1.0, repeats: true) { _ in
            if timeLeft > 0 && gameState == .memorizing {
                timeLeft -= 1
            } else if gameState == .memorizing {
                gameState = .guessing
                guessingPhaseStartTime = Date()
                timer?.invalidate()
                print("🎱 Memorization complete, starting guessing phase")
            }
        }
    }
    
    private func handleCellSelection(row: Int, col: Int) {
        guard gameState == .guessing else { return }
        
        // Convert edge position back to actual exit position
        let actualExitPosition: BallPosition
        
        if row == -1 { // Top edge
            actualExitPosition = BallPosition(row: -1, col: col)
        } else if row == puzzleData!.matrixSize { // Bottom edge
            actualExitPosition = BallPosition(row: puzzleData!.matrixSize, col: col)
        } else if col == -1 { // Left edge
            actualExitPosition = BallPosition(row: row, col: -1)
        } else if col == puzzleData!.matrixSize { // Right edge
            actualExitPosition = BallPosition(row: row, col: puzzleData!.matrixSize)
        } else {
            actualExitPosition = BallPosition(row: row, col: col)
        }
        
        selectedEndPosition = actualExitPosition
        print("🎯 User selected exit position: [\(actualExitPosition.row),\(actualExitPosition.col)]")
        
        // Auto-submit answer
        submitAnswer()
    }
    
    private func submitAnswer() {
        guard let selectedPos = selectedEndPosition,
              let data = puzzleData else { return }
        
        totalAttempts += 1
        
        print("🎯 User submitted answer: [\(selectedPos.row),\(selectedPos.col)]")
        
        // Simulate ball path to find correct answer
        let simulatedEndPosition = simulateBallPathAndGetEnd(data: data)
        correctEndPosition = simulatedEndPosition
        
        isCorrect = selectedPos.row == simulatedEndPosition?.row &&
                   selectedPos.col == simulatedEndPosition?.col
        
        if isCorrect {
            correctAttempts += 1
            currentStreak += 1
            
            // Calculate and add score
            if let score = calculateScore() {
                totalScore += score
            }
        } else {
            currentStreak = 0
        }
        
        // Simulate full ball path for visualization
        ballPath = simulateBallPath(data: data)
        gameState = .showingResult
        
        // Start trajectory animation
        startTrajectoryAnimation()
        
        print("🎱 Answer evaluation:")
        print("   Selected: [\(selectedPos.row),\(selectedPos.col)]")
        print("   Correct: [\(simulatedEndPosition?.row ?? -1),\(simulatedEndPosition?.col ?? -1)]")
        print("   Result: \(isCorrect)")
        
        gamesPlayedThisSession += 1
    }
    
    private func calculateScore() -> Int? {
        guard isCorrect, let data = puzzleData else { return nil }
        
        let basePoints = 150
        
        // Complexity multiplier based on matrix size and deflector count
        let complexityMultiplier = 1.0 + Float(data.matrixSize - 3) * 0.2 + Float(data.deflectors.count - 2) * 0.15
        
        // Memory efficiency bonus
        let memoryTime = Date().timeIntervalSince(memoryPhaseStartTime)
        let expectedMemoryTime = Double(data.memoryTime) / 1000.0
        let memoryBonus = memoryTime <= expectedMemoryTime * 0.5 ? Int(Float(basePoints) * 0.4) :
                         memoryTime <= expectedMemoryTime * 0.7 ? Int(Float(basePoints) * 0.2) :
                         memoryTime <= expectedMemoryTime * 0.9 ? Int(Float(basePoints) * 0.1) : 0
        
        // Prediction speed bonus
        let guessingTime = Date().timeIntervalSince(guessingPhaseStartTime)
        let speedBonus = guessingTime <= 5.0 ? Int(Float(basePoints) * 0.3) :
                        guessingTime <= 10.0 ? Int(Float(basePoints) * 0.2) :
                        guessingTime <= 15.0 ? Int(Float(basePoints) * 0.1) : 0
        
        // Streak bonus
        let streakMultiplier = 1.0 + Float(currentStreak) * 0.1
        
        // First attempt bonus
        let attemptBonus = totalAttempts == 1 ? Int(Float(basePoints) * 0.25) :
                          max(0, Int(Float(basePoints) * 0.25 * (1.0 - Float(totalAttempts - 1) * 0.15)))
        
        let finalScore = Int(Float(basePoints) * complexityMultiplier * streakMultiplier) +
                        memoryBonus + speedBonus + attemptBonus
        
        return max(finalScore, basePoints / 4)
    }
    
    private func startTrajectoryAnimation() {
        trajectoryProgress = 0.0
        animationTimer = Timer.scheduledTimer(withTimeInterval: 0.05, repeats: true) { _ in
            if trajectoryProgress < 1.0 {
                trajectoryProgress += 0.02
            } else {
                animationTimer?.invalidate()
                // Show feedback after animation completes
                DispatchQueue.main.asyncAfter(deadline: .now() + 0.5) {
                    showFeedback = true
                }
            }
        }
    }
    
    // MARK: - Ball Physics Simulation (FIXED TO MATCH ANDROID)
    
    private func simulateBallPathAndGetEnd(data: PinballPuzzleData) -> BallPosition? {
        var currentPos = data.startPosition
        var currentDir = data.startDirection
        
        print("🎯 SIMULATION: Starting at [\(currentPos.row),\(currentPos.col)] moving \(currentDir.name)")
        
        for step in 0..<50 {
            print("   Step \(step): at position [\(currentPos.row),\(currentPos.col)] moving \(currentDir.name)")
            
            // Check for deflector at CURRENT position (same as Android)
            if let deflector = data.deflectors.first(where: { $0.row == currentPos.row && $0.col == currentPos.col }) {
                print("   🎯 Hit deflector type \(deflector.type) at [\(currentPos.row),\(currentPos.col)]")
                let newDir = deflectDirection(currentDir, deflectorType: deflector.type)
                print("   🔄 Direction changed from \(currentDir.name) to \(newDir.name)")
                currentDir = newDir
            }
            
            // Calculate next position
            let nextRow = currentPos.row + currentDir.dr
            let nextCol = currentPos.col + currentDir.dc
            
            // Check if ball would exit the grid
            if nextRow < 0 || nextRow >= data.matrixSize || nextCol < 0 || nextCol >= data.matrixSize {
                print("   ✅ Ball exits at [\(nextRow),\(nextCol)] (outside bounds)")
                return BallPosition(row: nextRow, col: nextCol)
            }
            
            currentPos = BallPosition(row: nextRow, col: nextCol)
        }
        
        print("   ⚠️ Max steps reached, stopping at [\(currentPos.row),\(currentPos.col)]")
        return currentPos
    }
    
    private func simulateBallPath(data: PinballPuzzleData) -> [BallPosition] {
        var path: [BallPosition] = []
        var currentPos = data.startPosition
        var currentDir = data.startDirection
        
        path.append(currentPos)
        print("🎾 Starting ball trajectory visualization from [\(currentPos.row),\(currentPos.col)] moving \(currentDir.name)")
        
        for step in 1...50 {
            // Check for deflector at CURRENT position (same as Android)
            if let deflector = data.deflectors.first(where: { $0.row == currentPos.row && $0.col == currentPos.col }) {
                print("   🎯 Hit deflector type \(deflector.type) at [\(currentPos.row),\(currentPos.col)]")
                let newDir = deflectDirection(currentDir, deflectorType: deflector.type)
                print("   🔄 Direction changed from \(currentDir.name) to \(newDir.name)")
                currentDir = newDir
            }
            
            // Calculate next position
            let nextRow = currentPos.row + currentDir.dr
            let nextCol = currentPos.col + currentDir.dc
            
            // Check if ball would exit the grid
            if nextRow < 0 || nextRow >= data.matrixSize || nextCol < 0 || nextCol >= data.matrixSize {
                path.append(BallPosition(row: nextRow, col: nextCol))
                print("   ✅ Ball exits at [\(nextRow),\(nextCol)] - path complete with \(path.count) positions")
                break
            }
            
            currentPos = BallPosition(row: nextRow, col: nextCol)
            path.append(currentPos)
        }
        
        print("🎾 Final trajectory: \(path.count) positions")
        return path
    }
    
    private func deflectDirection(_ direction: BallDirection, deflectorType: Int) -> BallDirection {
        switch deflectorType {
        case 1: // Slash / deflector - EXACTLY like Android
            switch direction.name {
            case "UP":
                return BallDirection(dr: 0, dc: 1, name: "RIGHT")
            case "RIGHT":
                return BallDirection(dr: -1, dc: 0, name: "UP")
            case "DOWN":
                return BallDirection(dr: 0, dc: -1, name: "LEFT")
            case "LEFT":
                return BallDirection(dr: 1, dc: 0, name: "DOWN")
            default:
                return BallDirection(dr: -direction.dc, dc: -direction.dr, name: "deflected_slash")
            }
        case 2: // Backslash \ deflector - EXACTLY like Android
            switch direction.name {
            case "UP":
                return BallDirection(dr: 0, dc: -1, name: "LEFT")
            case "LEFT":
                return BallDirection(dr: -1, dc: 0, name: "UP")
            case "DOWN":
                return BallDirection(dr: 0, dc: 1, name: "RIGHT")
            case "RIGHT":
                return BallDirection(dr: 1, dc: 0, name: "DOWN")
            default:
                return BallDirection(dr: direction.dc, dc: direction.dr, name: "deflected_backslash")
            }
        default:
            return direction
        }
    }
}

// MARK: - Pinball Game Board
struct PinballGameBoard: View {
    let puzzleData: PinballPuzzleData
    let selectedEndPosition: BallPosition?
    let correctEndPosition: BallPosition?
    let ballPath: [BallPosition]
    let trajectoryProgress: Double
    let gameState: PinballGameState
    let boardSize: CGFloat
    let onCellClick: (Int, Int) -> Void
    
    private var cellSize: CGFloat {
        boardSize / CGFloat(puzzleData.matrixSize)
    }
    
    var body: some View {
        ZStack {
            // Main game board
            VStack(spacing: 0) {
                ForEach(0..<puzzleData.matrixSize, id: \.self) { row in
                    HStack(spacing: 0) {
                        ForEach(0..<puzzleData.matrixSize, id: \.self) { col in
                            GameCell(
                                row: row,
                                col: col,
                                cellSize: cellSize,
                                puzzleData: puzzleData,
                                gameState: gameState,
                                selectedEndPosition: selectedEndPosition,
                                ballPath: ballPath,
                                trajectoryProgress: trajectoryProgress
                            )
                        }
                    }
                }
            }
            .frame(width: boardSize, height: boardSize)
            .background(
                RoundedRectangle(cornerRadius: 8)
                    .fill(Color.gray.opacity(0.2))
            )
            .overlay(
                RoundedRectangle(cornerRadius: 8)
                    .stroke(Color.gray, lineWidth: 2)
            )
            
            // Edge positions overlay
            if gameState == .guessing || gameState == .showingResult {
                EdgePositionsOverlay(
                    puzzleData: puzzleData,
                    selectedEndPosition: selectedEndPosition,
                    correctEndPosition: correctEndPosition,
                    gameState: gameState,
                    boardSize: boardSize,
                    onCellClick: onCellClick
                )
            }
        }
    }
}

// MARK: - Game Cell
struct GameCell: View {
    let row: Int
    let col: Int
    let cellSize: CGFloat
    let puzzleData: PinballPuzzleData
    let gameState: PinballGameState
    let selectedEndPosition: BallPosition?
    let ballPath: [BallPosition]
    let trajectoryProgress: Double
    
    private var deflector: DeflectorData? {
        puzzleData.deflectors.first { $0.row == row && $0.col == col }
    }
    
    private var isOnBallPath: Bool {
        ballPath.contains { $0.row == row && $0.col == col }
    }
    
    private var pathIndex: Int? {
        ballPath.firstIndex { $0.row == row && $0.col == col }
    }
    
    var body: some View {
        ZStack {
            Rectangle()
                .fill(Color.clear)
                .frame(width: cellSize, height: cellSize)
                .overlay(
                    Rectangle()
                        .stroke(Color.gray.opacity(0.3), lineWidth: 0.5)
                )
            
            // Show deflectors during memorizing and result phases
            if gameState == .memorizing || gameState == .showingResult {
                if let deflector = deflector {
                    DeflectorView(deflector: deflector, cellSize: cellSize)
                }
            }
            
            // Show ball path during result phase
            if gameState == .showingResult && isOnBallPath {
                if let index = pathIndex,
                   Double(index) / Double(ballPath.count) <= trajectoryProgress {
                    Circle()
                        .fill(Color.orange)
                        .frame(width: cellSize * 0.3, height: cellSize * 0.3)
                        .shadow(color: .orange, radius: 4)
                }
            }
            
            // Show start position
            if puzzleData.startPosition.row == row && puzzleData.startPosition.col == col &&
               (gameState == .guessing || gameState == .showingResult) {
                Circle()
                    .fill(Color.cyan)
                    .frame(width: cellSize * 0.4, height: cellSize * 0.4)
                    .overlay(
                        Text("●")
                            .font(.caption)
                            .fontWeight(.bold)
                            .foregroundColor(.white)
                    )
            }
        }
    }
}

// MARK: - Deflector View
struct DeflectorView: View {
    let deflector: DeflectorData
    let cellSize: CGFloat
    
    private var deflectorWidth: CGFloat {
        cellSize * 0.6
    }
    
    var body: some View {
        ZStack {
            if deflector.type == 1 { // Slash /
                Rectangle()
                    .fill(Color.yellow)
                    .frame(width: deflectorWidth, height: 4)
                    .rotationEffect(.degrees(-45))
            } else if deflector.type == 2 { // Backslash \
                Rectangle()
                    .fill(Color.yellow)
                    .frame(width: deflectorWidth, height: 4)
                    .rotationEffect(.degrees(45))
            }
        }
    }
}

// MARK: - Edge Positions Overlay
struct EdgePositionsOverlay: View {
    let puzzleData: PinballPuzzleData
    let selectedEndPosition: BallPosition?
    let correctEndPosition: BallPosition?
    let gameState: PinballGameState
    let boardSize: CGFloat
    let onCellClick: (Int, Int) -> Void
    
    private var cellSize: CGFloat {
        boardSize / CGFloat(puzzleData.matrixSize)
    }
    
    private var edgePositionSize: CGFloat {
        cellSize * 0.4
    }
    
    var body: some View {
        ZStack {
            // Top edge positions
            ForEach(0..<puzzleData.matrixSize, id: \.self) { col in
                EdgePosition(
                    row: -1,
                    col: col,
                    edgePositionSize: edgePositionSize,
                    puzzleData: puzzleData,
                    selectedEndPosition: selectedEndPosition,
                    correctEndPosition: correctEndPosition,
                    gameState: gameState,
                    onCellClick: onCellClick
                )
                .offset(x: CGFloat(col) * cellSize - boardSize/2 + cellSize/2,
                       y: -boardSize/2 - 30)
            }
            
            // Bottom edge positions
            ForEach(0..<puzzleData.matrixSize, id: \.self) { col in
                EdgePosition(
                    row: puzzleData.matrixSize,
                    col: col,
                    edgePositionSize: edgePositionSize,
                    puzzleData: puzzleData,
                    selectedEndPosition: selectedEndPosition,
                    correctEndPosition: correctEndPosition,
                    gameState: gameState,
                    onCellClick: onCellClick
                )
                .offset(x: CGFloat(col) * cellSize - boardSize/2 + cellSize/2,
                       y: boardSize/2 + 30)
            }
            
            // Left edge positions
            ForEach(0..<puzzleData.matrixSize, id: \.self) { row in
                EdgePosition(
                    row: row,
                    col: -1,
                    edgePositionSize: edgePositionSize,
                    puzzleData: puzzleData,
                    selectedEndPosition: selectedEndPosition,
                    correctEndPosition: correctEndPosition,
                    gameState: gameState,
                    onCellClick: onCellClick
                )
                .offset(x: -boardSize/2 - 30,
                       y: CGFloat(row) * cellSize - boardSize/2 + cellSize/2)
            }
            
            // Right edge positions
            ForEach(0..<puzzleData.matrixSize, id: \.self) { row in
                EdgePosition(
                    row: row,
                    col: puzzleData.matrixSize,
                    edgePositionSize: edgePositionSize,
                    puzzleData: puzzleData,
                    selectedEndPosition: selectedEndPosition,
                    correctEndPosition: correctEndPosition,
                    gameState: gameState,
                    onCellClick: onCellClick
                )
                .offset(x: boardSize/2 + 30,
                       y: CGFloat(row) * cellSize - boardSize/2 + cellSize/2)
            }
        }
    }
}

// MARK: - Edge Position
struct EdgePosition: View {
    let row: Int
    let col: Int
    let edgePositionSize: CGFloat
    let puzzleData: PinballPuzzleData
    let selectedEndPosition: BallPosition?
    let correctEndPosition: BallPosition?
    let gameState: PinballGameState
    let onCellClick: (Int, Int) -> Void
    
    private var isStartPosition: Bool {
        let startEdgePosition = getStartEdgePosition()
        return startEdgePosition?.row == row && startEdgePosition?.col == col
    }
    
    private var isSelected: Bool {
        selectedEndPosition?.row == row && selectedEndPosition?.col == col
    }
    
    private var isCorrectAnswer: Bool {
        correctEndPosition?.row == row && correctEndPosition?.col == col
    }
    
    private var backgroundColor: Color {
        switch gameState {
        case .memorizing:
            return Color.clear
        case .guessing:
            if isStartPosition {
                return Color.cyan
            } else if isSelected {
                return Color.green
            } else {
                return Color.gray.opacity(0.6)
            }
        case .showingResult:
            if isStartPosition {
                return Color.cyan
            } else if isSelected && !isCorrectAnswer {
                return Color.red
            } else if isCorrectAnswer {
                return Color.green
            } else {
                return Color.gray.opacity(0.3)
            }
        }
    }
    
    private var borderColor: Color {
        return backgroundColor == Color.clear ? Color.clear : Color.white
    }
    
    var body: some View {
        Button(action: {
            if gameState == .guessing && !isStartPosition {
                onCellClick(row, col)
            }
        }) {
            Circle()
                .fill(backgroundColor)
                .frame(width: edgePositionSize, height: edgePositionSize)
                .overlay(
                    Circle()
                        .stroke(borderColor, lineWidth: 1)
                )
                .overlay(
                    Group {
                        if isStartPosition && (gameState == .guessing || gameState == .showingResult) {
                            Text("◉")
                                .font(.caption)
                                .fontWeight(.bold)
                                .foregroundColor(.white)
                        } else if isSelected && gameState == .showingResult && !isCorrectAnswer {
                            Text("✗")
                                .font(.caption2)
                                .fontWeight(.bold)
                                .foregroundColor(.white)
                        } else if isCorrectAnswer && gameState == .showingResult {
                            Text("✓")
                                .font(.caption2)
                                .fontWeight(.bold)
                                .foregroundColor(.white)
                        } else if isSelected && gameState == .guessing {
                            Text("?")
                                .font(.caption2)
                                .fontWeight(.bold)
                                .foregroundColor(.white)
                        }
                    }
                )
        }
        .buttonStyle(PlainButtonStyle())
        .disabled(gameState != .guessing || isStartPosition)
    }
    
    private func getStartEdgePosition() -> BallPosition? {
        switch puzzleData.startDirection.name {
        case "RIGHT":
            return BallPosition(row: puzzleData.startPosition.row, col: -1)
        case "LEFT":
            return BallPosition(row: puzzleData.startPosition.row, col: puzzleData.matrixSize)
        case "DOWN":
            return BallPosition(row: -1, col: puzzleData.startPosition.col)
        case "UP":
            return BallPosition(row: puzzleData.matrixSize, col: puzzleData.startPosition.col)
        default:
            return nil
        }
    }
}
