//
//  UniqueObjectPuzzleView.swift
//  PuzzleForge
//
//  iOS implementation of Unique Object puzzle - FIXED VERSION
//

import SwiftUI
import Foundation

// MARK: - Data Models
struct UniqueObjectData: Codable {
    let objects: [ObjectItem]
    let totalObjects: Int
    let instruction: String
    let shapeMappings: [String: String]
    let colorMappings: [String: String]
    let layout: GridLayout
    
    struct ObjectItem: Codable, Identifiable {
        let id = UUID()
        let shape: Int
        let color: Int
        
        private enum CodingKeys: String, CodingKey {
            case shape, color
        }
    }
    
    struct GridLayout: Codable {
        let rows: Int
        let cols: Int
        let totalCells: Int
    }
}

struct UniqueObjectAnswer: Codable {
    let uniqueObjectIndex: Int
    let uniqueObject: UniqueObjectData.ObjectItem
    let explanation: String
    let scoring: ScoringInfo
    
    struct ScoringInfo: Codable {
        let correctAnswerPoints: Int
        let timeBonus: Bool
        let maxTimeBonus: Int
    }
}

// MARK: - View Models
struct UniqueObjectItem: Identifiable {
    let id = UUID()
    let shape: Int
    let color: Int
    let position: CGPoint
}

struct UniqueObjectPuzzleData {
    let objects: [UniqueObjectItem]
    let totalObjects: Int
    let instruction: String
    let shapeMappings: [String: String]
    let colorMappings: [String: String]
    let layout: ObjectLayout
    let uniqueIndex: Int
}

struct ObjectLayout {
    let rows: Int
    let cols: Int
    let totalCells: Int
}


// MARK: - Main Adaptive Unique Object View
struct UniqueObjectPuzzleView: View {
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
    
    // Score tracking state
    @State private var totalScore = 0
    @State private var attempts = 0
    @State private var gameStartTime = Date()
    @State private var currentHearts = 3
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
    @State private var puzzleData: UniqueObjectPuzzleData?
    
    // Game state
    @State private var selectedIndex: Int?
    @State private var showFeedback = false
    @State private var isCorrect = false
    @State private var isGameActive = true
    
    // Timer state
    @State private var timeRemaining = 90
    @State private var displayTimer = "01:30"
    @State private var gameTimer: Timer?
    
    var body: some View {
        GeometryReader { geometry in
            ZStack {
                Color(red: 0.18, green: 0.11, blue: 0.41)
                    .ignoresSafeArea()
                
                VStack(spacing: 16) {
                    // Unified header (already includes back button)
                    AdaptiveUnifiedHeader(
                        puzzleType: "uniqueObject",
                        currentDifficulty: currentDifficultyLevel,
                        score: totalScore,
                        challengeNumber: attempts,
                        totalChallenges: 1,
                        timer: displayTimer,
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
                        onPause: { /* Visual puzzles don't need pause */ },
                        onHint: { /* Hints would give away the answer */ }
                    )
                    
                    // Adaptation notification
                    UnifiedAdaptationNotification(
                        adaptationInfo: adaptationInfo,
                        puzzleType: "uniqueObject",
                        visible: showAdaptationNotification,
                        onDismiss: { showAdaptationNotification = false }
                    )
                    
                    // Score and progress display
                    if totalScore > 0 || attempts > 0 {
                        progressSection
                    }
                    
                    // Enhanced hearts display
                    livesSection
                    
                    Spacer().frame(height: 24)
                    
                    // Instruction
                    if let data = puzzleData {
                        instructionSection(data: data)
                    }
                    
                    Spacer().frame(height: 32)
                    
                    // Objects with improved positioning
                    gameAreaSection(geometry: geometry)
                    
                    Spacer()
                }
                .padding(.horizontal, 16)
                
                // Brief error feedback for wrong selections
                if showFeedback && !isCorrect && currentHearts > 0 {
                    errorFeedbackOverlay
                }
                
                // Loading state
                if puzzleData == nil {
                    loadingView
                }
            }
        }
        .navigationBarHidden(true)
        .onAppear {
            setupGame()
        }
        .onDisappear {
            cleanupGame()
        }
    }
    
    // MARK: - UI Sections
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
                        .foregroundColor(.white)
                }
            }
            
            Spacer()
            
            if let data = puzzleData {
                VStack(alignment: .center) {
                    Text(currentDifficultyLevel.name.uppercased())
                        .font(.caption2)
                        .fontWeight(.bold)
                        .foregroundColor(.cyan)
                    Text("\(data.objects.count) objects")
                        .font(.caption)
                        .foregroundColor(.white.opacity(0.7))
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
                .fill(Color.white.opacity(0.1))
        )
    }
    
    private var livesSection: some View {
        HStack(spacing: 4) {
            ForEach(0..<currentDifficultyLevel.livesAllowed, id: \.self) { index in
                Text(index < currentHearts ? "❤️" : "🤍")
                    .font(.system(size: 20))
            }
        }
    }
    
    private func instructionSection(data: UniqueObjectPuzzleData) -> some View {
        VStack(spacing: 8) {
            Text(data.instruction)
                .foregroundColor(.white)
                .font(.headline)
                .multilineTextAlignment(.center)
            
            Text(currentDifficultyLevel.description)
                .foregroundColor(.white.opacity(0.7))
                .font(.caption)
                .multilineTextAlignment(.center)
                .padding(.top, 4)
        }
    }
    
    private func gameAreaSection(geometry: GeometryProxy) -> some View {
        let gameAreaHeight = geometry.size.height * 0.6
        let gameAreaWidth = geometry.size.width - 32 // Account for padding
        
        return ZStack {
            // Remove the confusing transparent rectangle
            // RoundedRectangle(cornerRadius: 16)
            //     .fill(Color.black.opacity(0.2))
            //     .frame(height: gameAreaHeight)
            
            if let data = puzzleData {
                ForEach(Array(data.objects.enumerated()), id: \.element.id) { index, object in
                    ObjectItemView(
                        object: object,
                        shapeMappings: data.shapeMappings,
                        colorMappings: data.colorMappings,
                        isSelected: selectedIndex == index,
                        difficulty: currentDifficultyLevel
                    ) {
                        handleObjectTap(index: index)
                    }
                    .position(
                        x: object.position.x * gameAreaWidth,
                        y: object.position.y * gameAreaHeight
                    )
                    .animation(.spring(response: 0.3), value: selectedIndex)
                }
            }
        }
        .frame(height: gameAreaHeight)
    }
    
    private var errorFeedbackOverlay: some View {
        VStack {
            Spacer()
            
            VStack(spacing: 8) {
                Text("❌ Not the unique object")
                    .foregroundColor(.white)
                    .font(.subheadline)
                    .fontWeight(.bold)
                
                Text("Keep looking! Hearts remaining: \(currentHearts)")
                    .foregroundColor(.white)
                    .font(.caption)
                    .multilineTextAlignment(.center)
            }
            .padding(16)
            .background(
                RoundedRectangle(cornerRadius: 12)
                    .fill(Color.red)
            )
            .padding(.horizontal, 16)
            .transition(.move(edge: .bottom).combined(with: .opacity))
        }
    }
    
    private var loadingView: some View {
        VStack(spacing: 16) {
            ProgressView()
                .progressViewStyle(CircularProgressViewStyle(tint: .white))
                .scaleEffect(1.5)
            
            Text("Loading puzzle...")
                .font(.subheadline)
                .foregroundColor(.white)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(Color.black.opacity(0.5))
    }
    
    private func cleanupGame() {
        gameTimer?.invalidate()
        gameTimer = nil
        isGameActive = false
    }

    
    // MARK: - Game Logic
    private func setupGame() {
        currentDifficultyLevel = difficultyManager.getCurrentDifficulty(for: "uniqueObject")
        currentHearts = currentDifficultyLevel.livesAllowed
        timeRemaining = currentDifficultyLevel.timeLimit
        displayTimer = formatTime(timeRemaining)
        
        generatePuzzle()
        
        Task {
            competitiveInsight = await competitiveManager.getCompetitiveInsight(
                userId: "user123",
                puzzleType: "uniqueObject",
                difficulty: currentDifficultyLevel.name
            )
        }
        
        startTimer()
    }
    
    private func generatePuzzle() {
        puzzleData = generateAdaptiveUniqueObjectPuzzle(currentDifficultyLevel)
        
        if gamesPlayedThisSession > 0 {
            isGameActive = true
            showFeedback = false
            selectedIndex = nil
            currentHearts = currentDifficultyLevel.livesAllowed
            gameStartTime = Date()
            timeRemaining = currentDifficultyLevel.timeLimit
            displayTimer = formatTime(currentDifficultyLevel.timeLimit)
        }
    }
    
    private func generateAdaptiveUniqueObjectPuzzle(_ difficulty: DifficultyManager.DifficultyLevel) -> UniqueObjectPuzzleData {
        let objectCount: Int = {
            switch difficulty {
            case .beginner: return 6
            case .easy: return 9
            case .medium: return 12
            case .hard: return 16
            case .expert: return 20
            }
        }()
        
        let shapeRange: Int = {
            switch difficulty {
            case .beginner: return 3
            case .easy: return 4
            case .medium: return 5
            case .hard: return 6
            case .expert: return 6
            }
        }()
        
        let colorRange: Int = {
            switch difficulty {
            case .beginner: return 3
            case .easy: return 4
            case .medium: return 6
            case .hard: return 7
            case .expert: return 8
            }
        }()
        
        // Generate objects with one unique
        var objects: [UniqueObjectItem] = []
        var usedCombinations: Set<String> = []
        
        // Generate improved positions with better distribution
        let positions = generateImprovedPositions(count: objectCount)
        
        // First, create duplicate groups
        let duplicateGroupCount = (objectCount - 1) / 2
        var remainingObjects = objectCount - 1 // Reserve 1 for unique
        
        for group in 1...duplicateGroupCount {
            if remainingObjects < 2 { break }
            
            let groupSize = group == duplicateGroupCount ? remainingObjects : 2
            
            var shape: Int
            var color: Int
            var combination: String
            
            repeat {
                shape = Int.random(in: 0..<shapeRange)
                color = Int.random(in: 0..<colorRange)
                combination = "\(shape)-\(color)"
            } while usedCombinations.contains(combination)
            
            usedCombinations.insert(combination)
            
            for i in 0..<groupSize {
                let positionIndex = objects.count
                objects.append(UniqueObjectItem(
                    shape: shape,
                    color: color,
                    position: positionIndex < positions.count ? positions[positionIndex] : CGPoint(x: 0.5, y: 0.5)
                ))
            }
            remainingObjects -= groupSize
        }
        
        // Add the unique object
        var uniqueShape: Int
        var uniqueColor: Int
        var uniqueCombination: String
        
        repeat {
            uniqueShape = Int.random(in: 0..<shapeRange)
            uniqueColor = Int.random(in: 0..<colorRange)
            uniqueCombination = "\(uniqueShape)-\(uniqueColor)"
        } while usedCombinations.contains(uniqueCombination)
        
        let uniqueIndex = objects.count
        let positionIndex = objects.count
        objects.append(UniqueObjectItem(
            shape: uniqueShape,
            color: uniqueColor,
            position: positionIndex < positions.count ? positions[positionIndex] : CGPoint(x: 0.5, y: 0.5)
        ))
        
        // Shuffle objects while tracking unique position
        objects.shuffle()
        let finalUniqueIndex = objects.firstIndex { object in
            "\(object.shape)-\(object.color)" == uniqueCombination
        } ?? 0
        
        // Create mappings
        let shapeMappings = createShapeMappings(range: shapeRange)
        let colorMappings = createColorMappings(range: colorRange)
        
        let layout = ObjectLayout(
            rows: Int(ceil(sqrt(Double(objects.count)))),
            cols: Int(ceil(sqrt(Double(objects.count)))),
            totalCells: objects.count
        )
        
        return UniqueObjectPuzzleData(
            objects: objects,
            totalObjects: objects.count,
            instruction: "Find the odd one out, and tap on it.",
            shapeMappings: shapeMappings,
            colorMappings: colorMappings,
            layout: layout,
            uniqueIndex: finalUniqueIndex
        )
    }
    
    private func generateImprovedPositions(count: Int) -> [CGPoint] {
        var positions: [CGPoint] = []
        let objectSize: CGFloat = {
            switch currentDifficultyLevel {
            case .beginner: return 0.15  // Larger objects need more space
            case .easy: return 0.13
            case .medium: return 0.11
            case .hard: return 0.10
            case .expert: return 0.09
            }
        }()
        
        let minDistance = objectSize + 0.05  // Buffer between objects
        let maxAttempts = 200
        let margin: CGFloat = 0.1  // Keep objects away from edges
        
        // Use a grid-based approach for better distribution
        if count <= 12 {
            // For smaller counts, use random placement with collision avoidance
            for _ in 0..<count {
                var attempts = 0
                var newPosition: CGPoint
                var validPosition = false
                
                repeat {
                    newPosition = CGPoint(
                        x: CGFloat.random(in: margin...(1.0 - margin)),
                        y: CGFloat.random(in: margin...(1.0 - margin))
                    )
                    
                    validPosition = positions.allSatisfy { position in
                        let distance = sqrt(pow(position.x - newPosition.x, 2) + pow(position.y - newPosition.y, 2))
                        return distance >= minDistance
                    }
                    
                    attempts += 1
                } while !validPosition && attempts < maxAttempts
                
                positions.append(newPosition)
            }
        } else {
            // For larger counts, use grid-based placement with jitter
            let gridSize = Int(ceil(sqrt(Double(count))))
            let cellSize = (1.0 - 2 * margin) / CGFloat(gridSize)
            let jitterAmount: CGFloat = cellSize * 0.2
            
            var gridPositions: [CGPoint] = []
            
            // Generate grid positions
            for row in 0..<gridSize {
                for col in 0..<gridSize {
                    if gridPositions.count >= count { break }
                    
                    let baseX = margin + CGFloat(col) * cellSize + cellSize / 2
                    let baseY = margin + CGFloat(row) * cellSize + cellSize / 2
                    
                    let jitterX = CGFloat.random(in: -jitterAmount...jitterAmount)
                    let jitterY = CGFloat.random(in: -jitterAmount...jitterAmount)
                    
                    let position = CGPoint(
                        x: min(max(baseX + jitterX, margin), 1.0 - margin),
                        y: min(max(baseY + jitterY, margin), 1.0 - margin)
                    )
                    
                    gridPositions.append(position)
                }
                if gridPositions.count >= count { break }
            }
            
            // Shuffle and take the required count
            gridPositions.shuffle()
            positions = Array(gridPositions.prefix(count))
        }
        
        return positions
    }
    
    private func createShapeMappings(range: Int) -> [String: String] {
        let shapeNames = ["circle", "square", "triangle", "diamond", "hexagon", "star"]
        var mappings: [String: String] = [:]
        
        for i in 0..<min(range, shapeNames.count) {
            mappings["\(i)"] = shapeNames[i]
        }
        
        return mappings
    }
    
    private func createColorMappings(range: Int) -> [String: String] {
        let colorNames = ["red", "blue", "yellow", "green", "purple", "orange", "pink", "cyan"]
        var mappings: [String: String] = [:]
        
        for i in 0..<min(range, colorNames.count) {
            mappings["\(i)"] = colorNames[i]
        }
        
        return mappings
    }
    
    private func handleObjectTap(index: Int) {
        guard isGameActive && !showFeedback,
              let data = puzzleData else { return }
        
        selectedIndex = index
        attempts += 1
        totalAnswers += 1
        
        let timeSpent = Date().timeIntervalSince(gameStartTime)
        let answerResult = index == data.uniqueIndex
        isCorrect = answerResult
        
        if isCorrect {
            correctAnswers += 1
            let newStreak = currentStreak + 1
            let score = calculateScore(isCorrect: true, timeSpent: timeSpent)
            totalScore += score
            currentStreak = newStreak
            gamesPlayedThisSession += 1
            
            recordPerformance(isCorrect: true, timeSpent: timeSpent)
            
            cleanupGame() // Clean up timer
            gameCompleted = true
            
            DispatchQueue.main.asyncAfter(deadline: .now() + 1.0) {
                onAnswerSubmitted(true)
                onNextPuzzle()
            }
        } else {
            currentHearts = max(0, currentHearts - 1)
            currentStreak = 0
            
            if currentHearts <= 0 {
                cleanupGame() // Clean up timer
                isGameActive = false
                recordPerformance(isCorrect: false, timeSpent: timeSpent)
                gameCompleted = true
                
                DispatchQueue.main.asyncAfter(deadline: .now() + 1.0) {
                    onAnswerSubmitted(false)
                    onNextPuzzle()
                }
            } else {
                showFeedback = true
                DispatchQueue.main.asyncAfter(deadline: .now() + 1.5) {
                    showFeedback = false
                    selectedIndex = nil
                }
            }
        }
    }
    
    private func calculateScore(isCorrect: Bool, timeSpent: TimeInterval) -> Int {
        guard isCorrect else { return 0 }
        
        let baseScore = currentDifficultyLevel.basePoints
        let complexityMultiplier = puzzleData?.objects.count ?? 10
        let timeBonus = max(0, Int(Double(currentDifficultyLevel.timeLimit) - timeSpent)) * 2
        let streakBonus = currentStreak * 15
        
        return baseScore + complexityMultiplier + timeBonus + streakBonus
    }
    
    private func recordPerformance(isCorrect: Bool, timeSpent: TimeInterval) {
        if let config = difficultyManager.recordPerformance(
            puzzleType: "uniqueObject",
            isCorrect: isCorrect,
            timeSpent: timeSpent,
            difficulty: currentDifficultyLevel,
            streak: currentStreak,
            livesRemaining: currentHearts,
            gameScore: totalScore,
            challengesCompleted: 1
        ) {
            if config.shouldNotify && config.level != currentDifficultyLevel {
                adaptationInfo = config
                currentDifficultyLevel = config.level
                showAdaptationNotification = true
            }
        }
    }
    
    private func startTimer() {
        // Cleanup any existing timer first
        gameTimer?.invalidate()
        
        gameTimer = Timer.scheduledTimer(withTimeInterval: 1.0, repeats: true) { timer in
            if isGameActive && timeRemaining > 0 {
                timeRemaining -= 1
                displayTimer = formatTime(timeRemaining)
            } else if timeRemaining <= 0 {
                cleanupGame()
                
                recordPerformance(isCorrect: false, timeSpent: Double(currentDifficultyLevel.timeLimit))
                onAnswerSubmitted(false)
                onNextPuzzle()
            }
        }
    }

    private func formatTime(_ seconds: Int) -> String {
        return String(format: "%02d:%02d", seconds / 60, seconds % 60)
    }
}

// MARK: - Object Item View
struct ObjectItemView: View {
    let object: UniqueObjectItem
    let shapeMappings: [String: String]
    let colorMappings: [String: String]
    let isSelected: Bool
    let difficulty: DifficultyManager.DifficultyLevel
    let onClick: () -> Void
    
    private var objectSize: CGFloat {
        switch difficulty {
        case .beginner: return 110
        case .easy: return 100
        case .medium: return 90
        case .hard: return 80
        case .expert: return 70
        }
    }
    
    var body: some View {
        Button(action: onClick) {
            ZStack {
                ObjectShapeView(
                    shapeType: getShapeType(),
                    color: getShapeColor(),
                    size: objectSize
                )
                
                if isSelected {
                    RoundedRectangle(cornerRadius: 12)
                        .stroke(Color.yellow, lineWidth: 4)
                        .frame(width: objectSize + 12, height: objectSize + 12)
                        .animation(.easeInOut(duration: 0.2), value: isSelected)
                }
            }
        }
        .buttonStyle(PlainButtonStyle())
        .scaleEffect(isSelected ? 1.1 : 1.0)
        .animation(.spring(response: 0.3), value: isSelected)
    }
    
    private func getShapeType() -> ObjectShapeType {
        let shapeString = shapeMappings["\(object.shape)"] ?? "circle"
        return ObjectShapeType.from(shapeString)
    }
    
    private func getShapeColor() -> Color {
        let colorString = colorMappings["\(object.color)"] ?? "blue"
        return colorFromName(colorString)
    }
    
    private func colorFromName(_ colorName: String) -> Color {
        switch colorName.lowercased() {
        case "red": return Color(red: 0.91, green: 0.12, blue: 0.39)
        case "blue": return Color(red: 0.13, green: 0.59, blue: 0.95)
        case "yellow": return Color(red: 1.0, green: 0.76, blue: 0.03)
        case "green": return Color(red: 0.3, green: 0.69, blue: 0.31)
        case "purple": return Color(red: 0.61, green: 0.15, blue: 0.69)
        case "orange": return Color(red: 1.0, green: 0.6, blue: 0.0)
        case "pink": return Color(red: 0.91, green: 0.12, blue: 0.39)
        case "cyan": return Color(red: 0.0, green: 0.74, blue: 0.83)
        default: return Color(red: 0.13, green: 0.59, blue: 0.95)
        }
    }
}

// MARK: - Shape Types and Views
enum ObjectShapeType {
    case circle, square, triangle, diamond, hexagon, star
    
    static func from(_ string: String) -> ObjectShapeType {
        switch string.lowercased() {
        case "circle": return .circle
        case "square": return .square
        case "triangle": return .triangle
        case "diamond": return .diamond
        case "hexagon": return .hexagon
        case "star": return .star
        default: return .circle
        }
    }
}

struct ObjectShapeView: View {
    let shapeType: ObjectShapeType
    let color: Color
    let size: CGFloat
    
    var body: some View {
        Group {
            switch shapeType {
            case .circle:
                Circle()
                    .fill(color)
                    .frame(width: size, height: size)
            case .square:
                RoundedRectangle(cornerRadius: 8)
                    .fill(color)
                    .frame(width: size, height: size)
            case .triangle:
                TriangleObjectShape()
                    .fill(color)
                    .frame(width: size, height: size)
            case .diamond:
                DiamondObjectShape()
                    .fill(color)
                    .frame(width: size, height: size)
            case .hexagon:
                HexagonObjectShape()
                    .fill(color)
                    .frame(width: size, height: size)
            case .star:
                StarObjectShape()
                    .fill(color)
                    .frame(width: size, height: size)
            }
        }
        .overlay(
            RoundedRectangle(cornerRadius: shapeType == .circle ? size/2 : 8)
                .stroke(Color.white.opacity(0.3), lineWidth: 1)
        )
    }
}

// MARK: - Custom Object Shapes
struct TriangleObjectShape: Shape {
    func path(in rect: CGRect) -> Path {
        var path = Path()
        path.move(to: CGPoint(x: rect.midX, y: rect.minY + 8))
        path.addLine(to: CGPoint(x: rect.minX + 8, y: rect.maxY - 4))
        path.addLine(to: CGPoint(x: rect.maxX - 8, y: rect.maxY - 4))
        path.closeSubpath()
        return path
    }
}

struct DiamondObjectShape: Shape {
    func path(in rect: CGRect) -> Path {
        var path = Path()
        let inset: CGFloat = 8
        path.move(to: CGPoint(x: rect.midX, y: rect.minY + inset))
        path.addLine(to: CGPoint(x: rect.maxX - inset, y: rect.midY))
        path.addLine(to: CGPoint(x: rect.midX, y: rect.maxY - inset))
        path.addLine(to: CGPoint(x: rect.minX + inset, y: rect.midY))
        path.closeSubpath()
        return path
    }
}

struct HexagonObjectShape: Shape {
    func path(in rect: CGRect) -> Path {
        var path = Path()
        let center = CGPoint(x: rect.midX, y: rect.midY)
        let radius = min(rect.width, rect.height) / 2 - 4
        
        for i in 0..<6 {
            let angle = Double(i) * .pi / 3 - .pi / 2
            let x = center.x + radius * cos(angle)
            let y = center.y + radius * sin(angle)
            
            if i == 0 {
                path.move(to: CGPoint(x: x, y: y))
            } else {
                path.addLine(to: CGPoint(x: x, y: y))
            }
        }
        path.closeSubpath()
        return path
    }
}

struct StarObjectShape: Shape {
    func path(in rect: CGRect) -> Path {
        var path = Path()
        let center = CGPoint(x: rect.midX, y: rect.midY)
        let outerRadius = min(rect.width, rect.height) / 2 - 4
        let innerRadius = outerRadius * 0.4
        let points = 5
        
        for i in 0..<points * 2 {
            let angle = Double(i) * .pi / Double(points) - .pi / 2
            let radius = i % 2 == 0 ? outerRadius : innerRadius
            let x = center.x + radius * cos(angle)
            let y = center.y + radius * sin(angle)
            
            if i == 0 {
                path.move(to: CGPoint(x: x, y: y))
            } else {
                path.addLine(to: CGPoint(x: x, y: y))
            }
        }
        path.closeSubpath()
        return path
    }
}

// MARK: - Session Completion Integration
extension UniqueObjectPuzzleView {
    private var sessionCompletionHandler: some View {
        Group {
            if gameCompleted {
                UnifiedSessionCompletionHandler(
                    puzzleType: "uniqueObject",
                    sessionScore: totalScore,
                    sessionStats: SessionStatistics(
                        correctAnswers: correctAnswers,
                        totalAnswers: totalAnswers,
                        totalTimeSeconds: Int(Date().timeIntervalSince(sessionStartTime)),
                        bestStreak: currentStreak,
                        currentStreak: currentStreak,
                        totalScore: totalScore,
                        individualTimes: [Int(Date().timeIntervalSince(gameStartTime))],
                        puzzleType: "uniqueObject"
                    ),
                    currentDifficulty: currentDifficultyLevel
                ) { result in
                    sessionResult = result
                }
            }
        }
    }
}

// MARK: - Puzzle Generator Extension (Fixed)
extension LocalMemoryPuzzleGenerator {
    
    static func generateUniqueObject(difficulty: String) -> (String, String) {
        print("🎯 Generating unique object puzzle with difficulty: \(difficulty)")
        
        do {
            // Get difficulty settings
            let settings = getDifficultySettings(for: difficulty)
            
            // Generate puzzle layout
            let puzzleLayout = generateUniqueObjectLayout(settings: settings)
            
            // Create question data
            let questionData = createUniqueObjectQuestionData(
                objects: puzzleLayout.objects,
                settings: settings
            )
            
            // Create answer data
            let answerData = createUniqueObjectAnswerData(
                uniqueIndex: puzzleLayout.uniqueObjectIndex,
                objects: puzzleLayout.objects
            )
            
            // Convert to JSON strings
            let encoder = JSONEncoder()
            encoder.outputFormatting = .prettyPrinted
            
            let questionJSON = try encoder.encode(questionData)
            let answerJSON = try encoder.encode(answerData)
            
            guard let questionString = String(data: questionJSON, encoding: .utf8),
                  let answerString = String(data: answerJSON, encoding: .utf8) else {
                throw NSError(domain: "JSONError", code: 1, userInfo: [NSLocalizedDescriptionKey: "Failed to encode JSON"])
            }
            
            print("✅ Successfully generated unique object puzzle")
            print("📊 Total objects: \(puzzleLayout.objects.count)")
            print("🎯 Unique object at index: \(puzzleLayout.uniqueObjectIndex)")
            
            return (questionString, answerString)
            
        } catch {
            print("❌ Failed to generate unique object puzzle: \(error)")
            return generateFallbackUniqueObjectPuzzle()
        }
    }
    
    // MARK: - Difficulty Settings
    private static func getDifficultySettings(for difficulty: String) -> UniqueObjectSettings {
        switch difficulty.lowercased() {
        case "easy":
            return UniqueObjectSettings(
                totalObjects: 6,
                minDuplicates: 2,
                maxDuplicates: 2,
                shapeRange: 3,
                colorRange: 3,
                distractorGroups: 1
            )
        case "medium":
            return UniqueObjectSettings(
                totalObjects: 9,
                minDuplicates: 2,
                maxDuplicates: 3,
                shapeRange: 4,
                colorRange: 4,
                distractorGroups: 2
            )
        case "hard":
            return UniqueObjectSettings(
                totalObjects: 12,
                minDuplicates: 2,
                maxDuplicates: 4,
                shapeRange: 5,
                colorRange: 6,
                distractorGroups: 3
            )
        case "expert":
            return UniqueObjectSettings(
                totalObjects: 15,
                minDuplicates: 3,
                maxDuplicates: 5,
                shapeRange: 6,
                colorRange: 8,
                distractorGroups: 4
            )
        default:
            return getDifficultySettings(for: "medium")
        }
    }
    
    // MARK: - Layout Generation (Fixed)
    private static func generateUniqueObjectLayout(settings: UniqueObjectSettings) -> UniqueObjectLayout {
        var objects: [UniqueObjectData.ObjectItem] = []
        var usedCombinations: Set<String> = []
        
        // STEP 1: Generate the unique object first
        let uniqueObject = createRandomUniqueObject(
            usedCombinations: usedCombinations,
            shapeRange: settings.shapeRange,
            colorRange: settings.colorRange
        )
        usedCombinations.insert("\(uniqueObject.shape)-\(uniqueObject.color)")
        
        // STEP 2: Fill remaining slots with duplicate groups
        var remainingSlots = settings.totalObjects - 1
        
        while remainingSlots >= settings.minDuplicates {
            let maxGroupSize = min(settings.maxDuplicates, remainingSlots)
            let groupSize = max(settings.minDuplicates,
                              min(maxGroupSize, settings.minDuplicates + Int.random(in: 0...1)))
            
            // Generate a new combination that doesn't conflict
            var attempts = 0
            var groupObject: UniqueObjectData.ObjectItem
            repeat {
                groupObject = UniqueObjectData.ObjectItem(
                    shape: Int.random(in: 0..<settings.shapeRange),
                    color: Int.random(in: 0..<settings.colorRange)
                )
                attempts += 1
                if attempts > 50 {
                    break
                }
            } while usedCombinations.contains("\(groupObject.shape)-\(groupObject.color)")
            
            usedCombinations.insert("\(groupObject.shape)-\(groupObject.color)")
            
            // Create the duplicate group
            for _ in 0..<groupSize {
                objects.append(groupObject)
            }
            
            remainingSlots -= groupSize
        }
        
        // STEP 3: Add the unique object
        objects.append(uniqueObject)
        let uniqueObjectIndex = objects.count - 1
        
        // STEP 4: Shuffle while tracking unique object position
        var newUniqueIndex = uniqueObjectIndex
        
        for i in stride(from: objects.count - 1, through: 1, by: -1) {
            let j = Int.random(in: 0...i)
            
            if i == newUniqueIndex {
                newUniqueIndex = j
            } else if j == newUniqueIndex {
                newUniqueIndex = i
            }
            
            objects.swapAt(i, j)
        }
        
        // STEP 5: Validate the result
        validateUniqueObjectLayout(objects: objects, uniqueIndex: newUniqueIndex)
        
        return UniqueObjectLayout(
            objects: objects,
            uniqueObjectIndex: newUniqueIndex
        )
    }
    
    // MARK: - Helper Methods (Fixed)
    private static func createRandomUniqueObject(
        usedCombinations: Set<String>,
        shapeRange: Int,
        colorRange: Int
    ) -> UniqueObjectData.ObjectItem {
        var attempts = 0
        var uniqueObject: UniqueObjectData.ObjectItem
        
        repeat {
            uniqueObject = UniqueObjectData.ObjectItem(
                shape: Int.random(in: 0..<shapeRange),
                color: Int.random(in: 0..<colorRange)
            )
            attempts += 1
            
            if attempts > 50 {
                break
            }
        } while usedCombinations.contains("\(uniqueObject.shape)-\(uniqueObject.color)")
        
        return uniqueObject
    }
    
    private static func validateUniqueObjectLayout(objects: [UniqueObjectData.ObjectItem], uniqueIndex: Int) {
        var combinations: [String: Int] = [:]
        
        for object in objects {
            let combo = "\(object.shape)-\(object.color)"
            combinations[combo, default: 0] += 1
        }
        
        let uniqueCombos = combinations.filter { $0.value == 1 }
        
        guard uniqueCombos.count == 1 else {
            print("❌ VALIDATION FAILED: Found \(uniqueCombos.count) unique objects, expected 1")
            return
        }
        
        let uniqueObject = objects[uniqueIndex]
        let expectedCombo = "\(uniqueObject.shape)-\(uniqueObject.color)"
        
        guard uniqueCombos.keys.contains(expectedCombo) else {
            print("❌ VALIDATION FAILED: Unique object combination not found")
            return
        }
        
        print("✅ Validation passed: Unique object \(expectedCombo) at index \(uniqueIndex)")
    }
    
    private static func createUniqueObjectQuestionData(
        objects: [UniqueObjectData.ObjectItem],
        settings: UniqueObjectSettings
    ) -> UniqueObjectData {
        return UniqueObjectData(
            objects: objects,
            totalObjects: objects.count,
            instruction: "Find the odd one out, and tap on it.",
            shapeMappings: getShapeMappings(range: settings.shapeRange),
            colorMappings: getColorMappings(range: settings.colorRange),
            layout: calculateGridLayout(objectCount: objects.count)
        )
    }
    
    private static func createUniqueObjectAnswerData(
        uniqueIndex: Int,
        objects: [UniqueObjectData.ObjectItem]
    ) -> UniqueObjectAnswer {
        return UniqueObjectAnswer(
            uniqueObjectIndex: uniqueIndex,
            uniqueObject: objects[uniqueIndex],
            explanation: "Object at position \(uniqueIndex + 1) is unique",
            scoring: UniqueObjectAnswer.ScoringInfo(
                correctAnswerPoints: 100,
                timeBonus: true,
                maxTimeBonus: 50
            )
        )
    }
    
    private static func getShapeMappings(range: Int) -> [String: String] {
        let shapes = ["circle", "square", "triangle", "diamond", "hexagon", "star"]
        var mappings: [String: String] = [:]
        
        for i in 0..<min(range, shapes.count) {
            mappings["\(i)"] = shapes[i]
        }
        
        return mappings
    }
    
    private static func getColorMappings(range: Int) -> [String: String] {
        let colors = ["red", "blue", "yellow", "green", "purple", "orange", "pink", "cyan"]
        var mappings: [String: String] = [:]
        
        for i in 0..<min(range, colors.count) {
            mappings["\(i)"] = colors[i]
        }
        
        return mappings
    }
    
    private static func calculateGridLayout(objectCount: Int) -> UniqueObjectData.GridLayout {
        let sqrt = Double(objectCount).squareRoot()
        let rows = Int(sqrt.rounded(.up))
        let cols = Int(ceil(Double(objectCount) / Double(rows)))
        
        return UniqueObjectData.GridLayout(
            rows: rows,
            cols: cols,
            totalCells: rows * cols
        )
    }
    
    private static func generateFallbackUniqueObjectPuzzle() -> (String, String) {
        print("🛟 Generating fallback unique object puzzle")
        
        let objects = [
            UniqueObjectData.ObjectItem(shape: 0, color: 0), // red circle
            UniqueObjectData.ObjectItem(shape: 0, color: 0), // red circle (duplicate)
            UniqueObjectData.ObjectItem(shape: 1, color: 1), // blue square
            UniqueObjectData.ObjectItem(shape: 1, color: 1), // blue square (duplicate)
            UniqueObjectData.ObjectItem(shape: 2, color: 2), // yellow triangle (unique)
            UniqueObjectData.ObjectItem(shape: 0, color: 0)  // red circle (another duplicate)
        ]
        
        let questionData = UniqueObjectData(
            objects: objects,
            totalObjects: objects.count,
            instruction: "Find the odd one out, and tap on it.",
            shapeMappings: ["0": "circle", "1": "square", "2": "triangle"],
            colorMappings: ["0": "red", "1": "blue", "2": "yellow"],
            layout: UniqueObjectData.GridLayout(rows: 2, cols: 3, totalCells: 6)
        )
        
        let answerData = UniqueObjectAnswer(
            uniqueObjectIndex: 4,
            uniqueObject: objects[4],
            explanation: "Object at position 5 is unique",
            scoring: UniqueObjectAnswer.ScoringInfo(
                correctAnswerPoints: 100,
                timeBonus: true,
                maxTimeBonus: 50
            )
        )
        
        do {
            let encoder = JSONEncoder()
            let questionJSON = try encoder.encode(questionData)
            let answerJSON = try encoder.encode(answerData)
            
            let questionString = String(data: questionJSON, encoding: .utf8) ?? "{}"
            let answerString = String(data: answerJSON, encoding: .utf8) ?? "{}"
            
            return (questionString, answerString)
        } catch {
            print("❌ Failed to encode fallback puzzle: \(error)")
            return ("{}", "{}")
        }
    }
}

// MARK: - Supporting Data Structures
private struct UniqueObjectSettings {
    let totalObjects: Int
    let minDuplicates: Int
    let maxDuplicates: Int
    let shapeRange: Int
    let colorRange: Int
    let distractorGroups: Int
}

private struct UniqueObjectLayout {
    let objects: [UniqueObjectData.ObjectItem]
    let uniqueObjectIndex: Int
}
