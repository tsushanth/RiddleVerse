//
//  SwipeableBlocksPuzzleView.swift
//  PuzzleForge
//
//  Updated to handle completion flow properly
//

import SwiftUI


enum ComparisonResult {
    case leftHeavier    // Left block should be higher
    case rightHeavier   // Right block should be higher
    case equal         // Blocks should be at same level
}

class ConversionDifficultyManager: ObservableObject {
    
    struct DifficultyLevel {
        let name: String
        let timeLimit: Int
        let livesAllowed: Int
        let basePoints: Int
        let difficultyFactor: Double // How close values can be (lower = harder)
        let complexityLevel: Int // 1-5 scale
        let allowedConversions: [ConversionType]
        
        static let tutorial = DifficultyLevel(
            name: "Tutorial",
            timeLimit: 120,
            livesAllowed: 5,
            basePoints: 20,
            difficultyFactor: 0.4,
            complexityLevel: 1,
            allowedConversions: [.distance, .weight]
        )
        
        static let beginner = DifficultyLevel(
            name: "Beginner",
            timeLimit: 100,
            livesAllowed: 4,
            basePoints: 30,
            difficultyFactor: 0.3,
            complexityLevel: 1,
            allowedConversions: [.distance, .weight]
        )
        
        static let easy = DifficultyLevel(
            name: "Easy",
            timeLimit: 90,
            livesAllowed: 4,
            basePoints: 40,
            difficultyFactor: 0.25,
            complexityLevel: 2,
            allowedConversions: [.distance, .weight, .volume]
        )
        
        static let medium = DifficultyLevel(
            name: "Medium",
            timeLimit: 75,
            livesAllowed: 3,
            basePoints: 50,
            difficultyFactor: 0.2,
            complexityLevel: 3,
            allowedConversions: [.distance, .weight, .volume, .time, .temperature]
        )
        
        static let hard = DifficultyLevel(
            name: "Hard",
            timeLimit: 60,
            livesAllowed: 3,
            basePoints: 60,
            difficultyFactor: 0.15,
            complexityLevel: 4,
            allowedConversions: [.temperature, .area, .speed, .volume, .weight]
        )
        
        static let expert = DifficultyLevel(
            name: "Expert",
            timeLimit: 45,
            livesAllowed: 2,
            basePoints: 80,
            difficultyFactor: 0.1,
            complexityLevel: 5,
            allowedConversions: ConversionType.allCases.filter { $0 != .unknown }
        )
        
        static let allLevels = [tutorial, beginner, easy, medium, hard, expert]
        
        var description: String {
            switch name {
            case "Tutorial": return "Learn basic conversions with clear differences"
            case "Beginner": return "Simple unit conversions with moderate differences"
            case "Easy": return "Multiple unit types with noticeable differences"
            case "Medium": return "Varied conversions with closer values"
            case "Hard": return "Complex conversions requiring precision"
            case "Expert": return "All conversion types with minimal differences"
            default: return "Adaptive conversion challenge"
            }
        }
    }
    
    struct PlayerPerformance: Codable {
        let accuracy: Float
        let averageResponseTime: Float
        let streakLength: Int
        let livesRemaining: Int
        let gameScore: Int
        let difficulty: String
        let conversionComplexity: Float
        let dragCount: Int
        let hintUsed: Bool
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
        let avgComplexity = recentWindow.map { $0.conversionComplexity }.reduce(0, +) / Float(recentWindow.count)
        let consistentSuccess = recentWindow.filter { $0.accuracy >= 0.8 && $0.conversionComplexity >= 0.7 }.count >= 3
        let consistentFailure = recentWindow.filter { $0.accuracy <= 0.4 || $0.conversionComplexity <= 0.3 }.count >= 3
        
        let currentIndex = DifficultyLevel.allLevels.firstIndex { $0.name == currentDifficulty.name } ?? 1
        
        var newLevel = currentDifficulty
        var reason = "Performance stable at current level"
        var confidence: Float = 0.3
        
        // Check for advancement
        if avgAccuracy >= 0.85 && avgComplexity >= 0.75 && consistentSuccess && currentIndex < DifficultyLevel.allLevels.count - 1 {
            newLevel = DifficultyLevel.allLevels[currentIndex + 1]
            reason = "Excellent conversion skills - advancing to \(newLevel.name)"
            confidence = 0.8
        }
        // Check for reduction
        else if (avgAccuracy <= 0.3 || avgComplexity <= 0.3) && consistentFailure && currentIndex > 0 {
            newLevel = DifficultyLevel.allLevels[currentIndex - 1]
            reason = "Building conversion skills at \(newLevel.name) level"
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
        if let savedName = UserDefaults.standard.string(forKey: "conversion_difficulty"),
           let level = DifficultyLevel.allLevels.first(where: { $0.name == savedName }) {
            currentDifficulty = level
        }
        loadPerformanceHistory()
    }
    
    private func saveDifficulty() {
        UserDefaults.standard.set(currentDifficulty.name, forKey: "conversion_difficulty")
    }
    
    private func savePerformanceHistory() {
        let encoder = JSONEncoder()
        if let encoded = try? encoder.encode(performanceHistory) {
            UserDefaults.standard.set(encoded, forKey: "conversion_performance_history")
        }
    }
    
    private func loadPerformanceHistory() {
        if let data = UserDefaults.standard.data(forKey: "conversion_performance_history") {
            let decoder = JSONDecoder()
            if let decoded = try? decoder.decode([PlayerPerformance].self, from: data) {
                performanceHistory = decoded
            }
        }
    }
}

// MARK: - Enhanced Conversion Types
enum ConversionType: String, CaseIterable {
    case distance = "distance"
    case weight = "weight"
    case volume = "volume"
    case temperature = "temperature"
    case time = "time"
    case area = "area"
    case speed = "speed"
    case unknown = "unknown"
    
    var displayName: String {
        switch self {
        case .distance: return "Distance"
        case .weight: return "Weight"
        case .volume: return "Volume"
        case .temperature: return "Temperature"
        case .time: return "Time"
        case .area: return "Area"
        case .speed: return "Speed"
        case .unknown: return "Unknown"
        }
    }
    
    var icon: String {
        switch self {
        case .distance: return "📏"
        case .weight: return "⚖️"
        case .volume: return "🧪"
        case .temperature: return "🌡️"
        case .time: return "⏱️"
        case .area: return "📐"
        case .speed: return "🏃‍♂️"
        case .unknown: return "❓"
        }
    }
}

// MARK: - Adaptive Conversion Generator
class AdaptiveConversionGenerator: ObservableObject {
    
    func generateConversionPuzzle(difficulty: ConversionDifficultyManager.DifficultyLevel) -> (WeightBlock, WeightBlock, ComparisonResult) {
        let conversionType = difficulty.allowedConversions.randomElement() ?? .distance
        
        switch conversionType {
        case .distance:
            return generateDistancePuzzle(difficulty: difficulty)
        case .weight:
            return generateWeightPuzzle(difficulty: difficulty)
        case .volume:
            return generateVolumePuzzle(difficulty: difficulty)
        case .temperature:
            return generateTemperaturePuzzle(difficulty: difficulty)
        case .time:
            return generateTimePuzzle(difficulty: difficulty)
        case .area:
            return generateAreaPuzzle(difficulty: difficulty)
        case .speed:
            return generateSpeedPuzzle(difficulty: difficulty)
        case .unknown:
            return generateDistancePuzzle(difficulty: difficulty)
        }
    }
    
    private func generateDistancePuzzle(difficulty: ConversionDifficultyManager.DifficultyLevel) -> (WeightBlock, WeightBlock, ComparisonResult) {
        let units = [
            ("miles", 1.60934, Color.blue),
            ("km", 1.0, Color.green),
            ("feet", 0.0003048, Color.orange),
            ("meters", 0.001, Color.purple)
        ]
        
        let baseValue = Double.random(in: 10...1000)
        let (leftUnit, leftFactor, leftColor) = units.randomElement()!
        let (rightUnit, rightFactor, rightColor) = units.filter { $0.0 != leftUnit }.randomElement()!
        
        let leftValue = Int(baseValue)
        let rightValue = generateAdaptiveValue(
            baseValue: baseValue * leftFactor / rightFactor,
            difficulty: difficulty
        )
        
        let leftBlock = WeightBlock(
            id: 1,
            label: "\(leftValue) \(leftUnit)",
            value: Double(leftValue) * leftFactor,
            unit: leftUnit,
            conversionType: .distance,
            color: leftColor
        )
        
        let rightBlock = WeightBlock(
            id: 2,
            label: "\(rightValue) \(rightUnit)",
            value: Double(rightValue) * rightFactor,
            unit: rightUnit,
            conversionType: .distance,
            color: rightColor
        )
        
        return (leftBlock, rightBlock, determineComparison(leftBlock.value, rightBlock.value))
    }
    
    private func generateWeightPuzzle(difficulty: ConversionDifficultyManager.DifficultyLevel) -> (WeightBlock, WeightBlock, ComparisonResult) {
        let units = [
            ("lbs", 0.453592, Color.red),
            ("kg", 1.0, Color.blue),
            ("grams", 0.001, Color.green),
            ("oz", 0.0283495, Color.orange)
        ]
        
        let baseValue = Double.random(in: 50...5000)
        let (leftUnit, leftFactor, leftColor) = units.randomElement()!
        let (rightUnit, rightFactor, rightColor) = units.filter { $0.0 != leftUnit }.randomElement()!
        
        let leftValue = Int(baseValue)
        let rightValue = generateAdaptiveValue(
            baseValue: baseValue * leftFactor / rightFactor,
            difficulty: difficulty
        )
        
        let leftBlock = WeightBlock(
            id: 1,
            label: "\(leftValue) \(leftUnit)",
            value: Double(leftValue) * leftFactor,
            unit: leftUnit,
            conversionType: .weight,
            color: leftColor
        )
        
        let rightBlock = WeightBlock(
            id: 2,
            label: "\(rightValue) \(rightUnit)",
            value: Double(rightValue) * rightFactor,
            unit: rightUnit,
            conversionType: .weight,
            color: rightColor
        )
        
        return (leftBlock, rightBlock, determineComparison(leftBlock.value, rightBlock.value))
    }
    
    private func generateVolumePuzzle(difficulty: ConversionDifficultyManager.DifficultyLevel) -> (WeightBlock, WeightBlock, ComparisonResult) {
        let units = [
            ("gal", 3.78541, Color.cyan),
            ("liters", 1.0, Color.blue),
            ("cups", 0.236588, Color.yellow),
            ("quarts", 0.946353, Color.purple)
        ]
        
        let baseValue = Double.random(in: 50...2000)
        let (leftUnit, leftFactor, leftColor) = units.randomElement()!
        let (rightUnit, rightFactor, rightColor) = units.filter { $0.0 != leftUnit }.randomElement()!
        
        let leftValue = Int(baseValue)
        let rightValue = generateAdaptiveValue(
            baseValue: baseValue * leftFactor / rightFactor,
            difficulty: difficulty
        )
        
        let leftBlock = WeightBlock(
            id: 1,
            label: "\(leftValue) \(leftUnit)",
            value: Double(leftValue) * leftFactor,
            unit: leftUnit,
            conversionType: .volume,
            color: leftColor
        )
        
        let rightBlock = WeightBlock(
            id: 2,
            label: "\(rightValue) \(rightUnit)",
            value: Double(rightValue) * rightFactor,
            unit: rightUnit,
            conversionType: .volume,
            color: rightColor
        )
        
        return (leftBlock, rightBlock, determineComparison(leftBlock.value, rightBlock.value))
    }
    
    private func generateTemperaturePuzzle(difficulty: ConversionDifficultyManager.DifficultyLevel) -> (WeightBlock, WeightBlock, ComparisonResult) {
        let baseTemp = Double.random(in: -10...100)
        let useFahrenheit = Bool.random()
        
        let leftValue = Int(baseTemp)
        let rightValue: Int
        let leftUnit: String
        let rightUnit: String
        let leftStandardValue: Double
        let rightStandardValue: Double
        
        if useFahrenheit {
            leftUnit = "°F"
            rightUnit = "°C"
            leftStandardValue = (baseTemp - 32) * 5/9 // Convert F to C
            let rightTempC = generateAdaptiveTemperature(baseTemp: leftStandardValue, difficulty: difficulty)
            rightValue = Int(rightTempC)
            rightStandardValue = rightTempC
        } else {
            leftUnit = "°C"
            rightUnit = "°F"
            leftStandardValue = baseTemp
            let rightTempF = generateAdaptiveTemperature(baseTemp: baseTemp * 9/5 + 32, difficulty: difficulty)
            rightValue = Int(rightTempF)
            rightStandardValue = (rightTempF - 32) * 5/9
        }
        
        let leftBlock = WeightBlock(
            id: 1,
            label: "\(leftValue)\(leftUnit)",
            value: leftStandardValue,
            unit: leftUnit,
            conversionType: .temperature,
            color: Color.orange
        )
        
        let rightBlock = WeightBlock(
            id: 2,
            label: "\(rightValue)\(rightUnit)",
            value: rightStandardValue,
            unit: rightUnit,
            conversionType: .temperature,
            color: Color.blue
        )
        
        return (leftBlock, rightBlock, determineComparison(leftStandardValue, rightStandardValue))
    }
    
    private func generateTimePuzzle(difficulty: ConversionDifficultyManager.DifficultyLevel) -> (WeightBlock, WeightBlock, ComparisonResult) {
        let units = [
            ("sec", 1.0, Color.red),
            ("min", 60.0, Color.green),
            ("hours", 3600.0, Color.blue),
            ("days", 86400.0, Color.purple)
        ]
        
        let baseValue = Double.random(in: 60...10800)
        let (leftUnit, leftFactor, leftColor) = units.randomElement()!
        let (rightUnit, rightFactor, rightColor) = units.filter { $0.0 != leftUnit }.randomElement()!
        
        let leftValue = Int(baseValue)
        let rightValue = generateAdaptiveValue(
            baseValue: baseValue * leftFactor / rightFactor,
            difficulty: difficulty
        )
        
        let leftBlock = WeightBlock(
            id: 1,
            label: "\(leftValue) \(leftUnit)",
            value: Double(leftValue) * leftFactor,
            unit: leftUnit,
            conversionType: .time,
            color: leftColor
        )
        
        let rightBlock = WeightBlock(
            id: 2,
            label: "\(rightValue) \(rightUnit)",
            value: Double(rightValue) * rightFactor,
            unit: rightUnit,
            conversionType: .time,
            color: rightColor
        )
        
        return (leftBlock, rightBlock, determineComparison(leftBlock.value, rightBlock.value))
    }
    
    private func generateAreaPuzzle(difficulty: ConversionDifficultyManager.DifficultyLevel) -> (WeightBlock, WeightBlock, ComparisonResult) {
        let units = [
            ("sq ft", 0.092903, Color.brown),
            ("sq m", 1.0, Color.green),
            ("acres", 4046.86, Color.yellow)
        ]
        
        let baseValue = Double.random(in: 100...10000)
        let (leftUnit, leftFactor, leftColor) = units.randomElement()!
        let (rightUnit, rightFactor, rightColor) = units.filter { $0.0 != leftUnit }.randomElement()!
        
        let leftValue = Int(baseValue)
        let rightValue = generateAdaptiveValue(
            baseValue: baseValue * leftFactor / rightFactor,
            difficulty: difficulty
        )
        
        let leftBlock = WeightBlock(
            id: 1,
            label: "\(leftValue) \(leftUnit)",
            value: Double(leftValue) * leftFactor,
            unit: leftUnit,
            conversionType: .area,
            color: leftColor
        )
        
        let rightBlock = WeightBlock(
            id: 2,
            label: "\(rightValue) \(rightUnit)",
            value: Double(rightValue) * rightFactor,
            unit: rightUnit,
            conversionType: .area,
            color: rightColor
        )
        
        return (leftBlock, rightBlock, determineComparison(leftBlock.value, rightBlock.value))
    }
    
    private func generateSpeedPuzzle(difficulty: ConversionDifficultyManager.DifficultyLevel) -> (WeightBlock, WeightBlock, ComparisonResult) {
        let units = [
            ("m/s", 1.0, Color.red),
            ("km/h", 0.277778, Color.blue),
            ("mph", 0.44704, Color.green)
        ]
        
        let baseValue = Double.random(in: 10...100)
        let (leftUnit, leftFactor, leftColor) = units.randomElement()!
        let (rightUnit, rightFactor, rightColor) = units.filter { $0.0 != leftUnit }.randomElement()!
        
        let leftValue = Int(baseValue)
        let rightValue = generateAdaptiveValue(
            baseValue: baseValue * leftFactor / rightFactor,
            difficulty: difficulty
        )
        
        let leftBlock = WeightBlock(
            id: 1,
            label: "\(leftValue) \(leftUnit)",
            value: Double(leftValue) * leftFactor,
            unit: leftUnit,
            conversionType: .speed,
            color: leftColor
        )
        
        let rightBlock = WeightBlock(
            id: 2,
            label: "\(rightValue) \(rightUnit)",
            value: Double(rightValue) * rightFactor,
            unit: rightUnit,
            conversionType: .speed,
            color: rightColor
        )
        
        return (leftBlock, rightBlock, determineComparison(leftBlock.value, rightBlock.value))
    }
    
    private func generateAdaptiveValue(baseValue: Double, difficulty: ConversionDifficultyManager.DifficultyLevel) -> Int {
        let variation = baseValue * difficulty.difficultyFactor * Double.random(in: 0.5...2.0)
        let direction = Bool.random() ? 1.0 : -1.0
        return Int(max(1, baseValue + (direction * variation)))
    }
    
    private func generateAdaptiveTemperature(baseTemp: Double, difficulty: ConversionDifficultyManager.DifficultyLevel) -> Double {
        let variation = baseTemp * difficulty.difficultyFactor * Double.random(in: 0.5...1.5)
        let direction = Bool.random() ? 1.0 : -1.0
        return baseTemp + (direction * variation)
    }
    
    private func determineComparison(_ value1: Double, _ value2: Double) -> ComparisonResult {
        let tolerance = 0.05 // 5% tolerance for "equal"
        let difference = abs(value1 - value2)
        let averageValue = (value1 + value2) / 2
        
        if difference < averageValue * tolerance {
            return .equal
        } else if value1 > value2 {
            return .leftHeavier
        } else {
            return .rightHeavier
        }
    }
}

// MARK: - MODIFY WeightBlock TO ADD THESE PROPERTIES

struct WeightBlock: Equatable {
    let id: Int
    let label: String
    let value: Double // The actual weight value for comparison
    let unit: String // ADD THIS
    let conversionType: ConversionType // ADD THIS
    let color: Color
    var offsetY: CGFloat = 0
    var isDragging: Bool = false
    
    // ADD THIS INIT
    init(id: Int, label: String, value: Double, unit: String, conversionType: ConversionType, color: Color) {
        self.id = id
        self.label = label
        self.value = value
        self.unit = unit
        self.conversionType = conversionType
        self.color = color
    }
    
    // KEEP THE OLD INIT FOR COMPATIBILITY
    init(id: Int, label: String, value: Double, color: Color) {
        self.id = id
        self.label = label
        self.value = value
        self.unit = ""
        self.conversionType = .unknown
        self.color = color
    }
    
    static func == (lhs: WeightBlock, rhs: WeightBlock) -> Bool {
        return lhs.id == rhs.id &&
               lhs.label == rhs.label &&
               lhs.value == rhs.value &&
               lhs.offsetY == rhs.offsetY &&
               lhs.isDragging == rhs.isDragging
    }
}

// MARK: - REPLACE YOUR SwipeableBlocksPuzzleView WITH THIS ENHANCED VERSION

struct SwipeableBlocksPuzzleView: View {
    let puzzle: Puzzle
    let questionIndex: Int
    let totalQuestions: Int
    let onAnswerSubmitted: (ComparisonResult, Bool) -> Void
    let onNextPuzzle: () -> Void
    let onExit: () -> Void
    
    // Adaptive difficulty management
    @StateObject private var difficultyManager = ConversionDifficultyManager()
    @StateObject private var generator = AdaptiveConversionGenerator()
    @State private var adaptationResult: ConversionDifficultyManager.AdaptationResult?
    @State private var showAdaptationNotification = false
    @State private var useAdaptiveGeneration = true // Toggle for adaptive vs. original
    
    @StateObject private var timer = PuzzleTimer(totalTime: 60)
    @State private var adaptiveTimeRemaining: Int = 60
    @State private var adaptiveTimer: Timer?
    @State private var useAdaptiveTimer = false
    @State private var leftBlockState: WeightBlock
    @State private var rightBlockState: WeightBlock
    @State private var correctAnswer: ComparisonResult = .equal
    @State private var showContinueButton = false
    @State private var hasSubmitted = false
    @State private var showFeedback = false
    @State private var isCorrect = false
    @State private var userAnswerText = ""
    @State private var correctAnswerText = ""
    @StateObject private var feedbackManager = UnifiedFeedbackManager()
    @StateObject private var progressionManager = ProgressionManager()
    
    // Enhanced tracking
    @State private var currentLives: Int = 3
    @State private var currentStreak = 0
    @State private var totalScore = 0
    @State private var dragCount = 0
    @State private var hintUsed = false
    @State private var showHint = false
    @State private var gameStartTime = Date()
    
    // Threshold for determining position relationships
    private let positionThreshold: CGFloat = 50
    
    private var conversionData: ConversionPuzzleData? {
        puzzle.conversionPuzzleData
    }
    
    // Calculate the correct answer based on conversion data
    private var correctAnswerFromData: ComparisonResult {
        guard let data = conversionData else { return .equal }
        
        print("🔍 CONVERSION: Determining correct answer for \(data.value1) \(data.unit1) vs \(data.value2) \(data.unit2)")
        print("🔍 CONVERSION: Comparison field says: '\(data.comparison)'")
        
        // Use the comparison field from the generated data
        switch data.comparison.lowercased() {
        case "equal", "approximately equal", "same":
            print("✅ CONVERSION: Should be equal")
            return .equal
        case "not equal":
            // Convert and compare to determine which is greater
            let leftValue = convertToStandardUnit(data.value1, unit: data.unit1)
            let rightValue = convertToStandardUnit(data.value2, unit: data.unit2)
            
            print("🔍 CONVERSION: Converting values - Left: \(leftValue), Right: \(rightValue)")
            
            if leftValue > rightValue {
                print("✅ CONVERSION: Left should be higher")
                return .leftHeavier
            } else {
                print("✅ CONVERSION: Right should be higher")
                return .rightHeavier
            }
        default:
            print("⚠️ CONVERSION: Unknown comparison '\(data.comparison)', defaulting to equal")
            return .equal
        }
    }
    
    init(puzzle: Puzzle, questionIndex: Int, totalQuestions: Int, onAnswerSubmitted: @escaping (ComparisonResult, Bool) -> Void, onNextPuzzle: @escaping () -> Void, onExit: @escaping () -> Void) {
        self.puzzle = puzzle
        self.questionIndex = questionIndex
        self.totalQuestions = totalQuestions
        self.onAnswerSubmitted = onAnswerSubmitted
        self.onNextPuzzle = onNextPuzzle
        self.onExit = onExit
        
        // Initialize blocks based on puzzle data
        if let data = puzzle.conversionPuzzleData {
            let leftBlock = WeightBlock(
                id: 1,
                label: "\(data.value1) \(data.unit1)",
                value: Double(data.value1),
                unit: data.unit1,
                conversionType: .unknown,
                color: Color(red: 0.91, green: 0.12, blue: 0.39)
            )
            let rightBlock = WeightBlock(
                id: 2,
                label: "\(data.value2) \(data.unit2)",
                value: Double(data.value2),
                unit: data.unit2,
                conversionType: .unknown,
                color: Color(red: 0.56, green: 0.79, blue: 0.98)
            )
            self._leftBlockState = State(initialValue: leftBlock)
            self._rightBlockState = State(initialValue: rightBlock)
        } else {
            // Fallback blocks
            let leftBlock = WeightBlock(
                id: 1,
                label: "40 miles",
                value: 40.0,
                unit: "miles",
                conversionType: .distance,
                color: Color(red: 0.91, green: 0.12, blue: 0.39)
            )
            let rightBlock = WeightBlock(
                id: 2,
                label: "60 kilometers",
                value: 60.0,
                unit: "km",
                conversionType: .distance,
                color: Color(red: 0.56, green: 0.79, blue: 0.98)
            )
            self._leftBlockState = State(initialValue: leftBlock)
            self._rightBlockState = State(initialValue: rightBlock)
        }
        
        // Initialize adaptive values
        let initialDifficulty = ConversionDifficultyManager.DifficultyLevel.easy
        self._currentLives = State(initialValue: initialDifficulty.livesAllowed)
    }
    
    var body: some View {
        GeometryReader { geometry in
            ZStack {
                // Background gradient
                backgroundView
                    .ignoresSafeArea()
                
                VStack(spacing: 0) {
                    // Enhanced top bar
                    enhancedTopBar
                        .padding(.horizontal, 20)
                        .padding(.top, 10)
                    
                    // Adaptation notification
                    if showAdaptationNotification, let result = adaptationResult {
                        adaptationNotificationView(result: result)
                            .transition(.move(edge: .top).combined(with: .opacity))
                    }
                    
                    // Instructions
                    instructionsCard
                        .padding(.horizontal, 20)
                        .padding(.vertical, 20)
                    
                    // Hint bubble
                    if showHint {
                        hintBubbleView
                            .transition(.move(edge: .top).combined(with: .opacity))
                    }
                    
                    // Balance scale visual
                    enhancedBalanceScale
                        .frame(height: 100)
                        .padding(.horizontal, 40)
                    
                    // Main content area with draggable blocks
                    blocksArea
                        .frame(maxWidth: .infinity)
                        .padding(.horizontal, 40)
                    
                    Spacer()
                    
                    // Continue/Next button
                    if showContinueButton {
                        continueButton
                            .padding(.horizontal, 40)
                            .padding(.bottom, 40)
                            .transition(.asymmetric(
                                insertion: .move(edge: .bottom).combined(with: .opacity),
                                removal: .move(edge: .bottom).combined(with: .opacity)
                            ))
                    }
                }
                
                // Enhanced feedback overlay
                if showFeedback {
                    enhancedFeedbackOverlay
                        .transition(.asymmetric(
                            insertion: .scale.combined(with: .opacity),
                            removal: .scale.combined(with: .opacity)
                        ))
                }
            }
        }
        .trackPuzzleViewOnce(
            puzzleId: puzzle.puzzleId,
            puzzleType: "conversion",
            difficulty: puzzle.difficulty,
            questionIndex: questionIndex
        )
        .onAppear {
            setupPuzzle()
        }
        .onDisappear {
            timer.stop()
            adaptiveTimer?.invalidate()
        }
        .onChange(of: timer.timeRemaining) { newTime in
            if !useAdaptiveTimer && newTime <= 0 && !hasSubmitted {
                // Time's up - auto submit current position
                submitAnswer()
            }
        }
        .onChange(of: adaptiveTimeRemaining) { newTime in
            if useAdaptiveTimer && newTime <= 0 && !hasSubmitted {
                // Time's up - auto submit current position
                submitAnswer()
            }
        }
        .onChange(of: leftBlockState.offsetY) { _ in checkMovement() }
        .onChange(of: rightBlockState.offsetY) { _ in checkMovement() }
        .navigationBarHidden(true)
    }
    
    // MARK: - Enhanced View Components
    
    private var enhancedTopBar: some View {
        HStack {
            // Left side: Exit button and level
            HStack(spacing: 10) {
                Button(action: onExit) {
                    HStack(spacing: 4) {
                        Image(systemName: "chevron.left")
                        Text("Back")
                    }
                    .foregroundColor(.white)
                }
                
                VStack(alignment: .leading, spacing: 4) {
                    Text("Level \(progressionManager.currentLevel.level)")
                        .font(.system(size: 16, weight: .bold))
                        .foregroundColor(.white)
                    
                    // Show adaptive difficulty
                    Text(difficultyManager.currentDifficulty.name)
                        .font(.caption)
                        .foregroundColor(.cyan)
                    
                    LevelProgressBar(level: progressionManager.currentLevel)
                        .frame(width: 100)
                }
            }
            
            Spacer()
            
            // Center: Lives and score
            VStack(spacing: 4) {
                HStack(spacing: 4) {
                    ForEach(0..<difficultyManager.currentDifficulty.livesAllowed, id: \.self) { index in
                        Image(systemName: index < currentLives ? "heart.fill" : "heart")
                            .font(.system(size: 16))
                            .foregroundColor(index < currentLives ? .red : .gray)
                    }
                }
                
                if totalScore > 0 {
                    Text("Score: \(totalScore)")
                        .font(.caption)
                        .foregroundColor(.green)
                }
            }
            
            Spacer()
            
            // Right side: Timer, streak, and hint
            VStack(alignment: .trailing, spacing: 4) {
                Text(useAdaptiveTimer ? formatAdaptiveTime(adaptiveTimeRemaining) : timer.formattedTime)
                    .font(.title2)
                    .fontWeight(.bold)
                    .foregroundColor(.white)
                
                if currentStreak > 0 {
                    Text("🔥 \(currentStreak)")
                        .font(.caption)
                        .foregroundColor(.orange)
                }
                
                Button(action: {
                    showHint.toggle()
                    if showHint { hintUsed = true }
                }) {
                    Text("💡")
                        .font(.title2)
                }
            }
        }
    }
    
    private func adaptationNotificationView(result: ConversionDifficultyManager.AdaptationResult) -> some View {
        HStack {
            VStack(alignment: .leading, spacing: 4) {
                HStack {
                    Image(systemName: "gear.badge.checkmark")
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
                colors: [Color.blue, Color.purple],
                startPoint: .leading,
                endPoint: .trailing
            )
        )
        .cornerRadius(12)
        .padding(.horizontal, 20)
        .padding(.bottom, 16)
    }
    
    private var hintBubbleView: some View {
        VStack(spacing: 8) {
            HStack {
                Text("💡 Conversion Hint")
                    .font(.headline)
                    .foregroundColor(.white)
                Spacer()
            }
            
            Text(generateHintText())
                .font(.subheadline)
                .foregroundColor(.white.opacity(0.9))
                .multilineTextAlignment(.leading)
        }
        .padding()
        .background(
            RoundedRectangle(cornerRadius: 12)
                .fill(Color.blue.opacity(0.3))
                .overlay(
                    RoundedRectangle(cornerRadius: 12)
                        .stroke(Color.blue.opacity(0.5), lineWidth: 1)
                )
        )
        .padding(.horizontal, 20)
    }
    
    private var enhancedBalanceScale: some View {
        HStack {
            Spacer()
            
            // Left scale indicator
            RoundedRectangle(cornerRadius: 4)
                .fill(Color.white.opacity(0.3))
                .overlay(
                    RoundedRectangle(cornerRadius: 4)
                        .stroke(Color.white.opacity(0.5), lineWidth: 2)
                )
                .frame(width: 40, height: 40)
                .overlay(
                    Image(systemName: "arrow.up")
                        .foregroundColor(.white.opacity(0.7))
                        .font(.system(size: 16))
                )
            
            Spacer()
            
            // Center line with conversion type icon
            HStack {
                Rectangle()
                    .fill(Color.white.opacity(0.5))
                    .frame(width: 40, height: 2)
                
                Text(leftBlockState.conversionType.icon)
                    .font(.title)
                
                Rectangle()
                    .fill(Color.white.opacity(0.5))
                    .frame(width: 40, height: 2)
            }
            
            Spacer()
            
            // Right scale indicator
            RoundedRectangle(cornerRadius: 4)
                .fill(Color.white.opacity(0.3))
                .overlay(
                    RoundedRectangle(cornerRadius: 4)
                        .stroke(Color.white.opacity(0.5), lineWidth: 2)
                )
                .frame(width: 40, height: 40)
                .overlay(
                    Image(systemName: "arrow.up")
                        .foregroundColor(.white.opacity(0.7))
                        .font(.system(size: 16))
                )
            
            Spacer()
        }
    }
    
    private var enhancedFeedbackOverlay: some View {
        VStack(spacing: 20) {
            // Feedback icon and result
            VStack(spacing: 12) {
                Image(systemName: isCorrect ? "checkmark.circle.fill" : "xmark.circle.fill")
                    .font(.system(size: 60))
                    .foregroundColor(isCorrect ? .green : .red)
                
                Text(isCorrect ? "Excellent!" : "Not quite right")
                    .font(.title)
                    .fontWeight(.bold)
                    .foregroundColor(.white)
            }
            
            // Answer explanation
            VStack(spacing: 8) {
                if !isCorrect {
                    Text("Your answer: \(userAnswerText)")
                        .font(.subheadline)
                        .foregroundColor(.white.opacity(0.8))
                }
                
                Text("Correct answer: \(correctAnswerText)")
                    .font(.subheadline)
                    .foregroundColor(.white.opacity(0.9))
                    .fontWeight(.medium)
                
                if isCorrect {
                    let score = calculateEnhancedScore()
                    Text("Score: +\(score) points")
                        .font(.subheadline)
                        .foregroundColor(.green)
                        .fontWeight(.bold)
                }
            }
            
            // Action buttons
            HStack(spacing: 20) {
                if !isCorrect && currentLives > 0 {
                    Button("Try Again") {
                        withAnimation(.easeInOut(duration: 0.3)) {
                            showFeedback = false
                            resetBlocks()
                        }
                    }
                    .font(.headline)
                    .foregroundColor(.white)
                    .padding(.horizontal, 20)
                    .padding(.vertical, 12)
                    .background(Color.orange)
                    .cornerRadius(25)
                }
                
                Button(isCorrect ? "Next Puzzle" : "Continue") {
                    withAnimation(.easeInOut(duration: 0.3)) {
                        showFeedback = false
                        onNextPuzzle()
                    }
                }
                .font(.headline)
                .foregroundColor(.white)
                .padding(.horizontal, 20)
                .padding(.vertical, 12)
                .background(isCorrect ? Color.green : Color.blue)
                .cornerRadius(25)
            }
        }
        .padding(30)
        .background(
            RoundedRectangle(cornerRadius: 20)
                .fill(Color.black.opacity(0.8))
                .overlay(
                    RoundedRectangle(cornerRadius: 20)
                        .stroke(Color.white.opacity(0.2), lineWidth: 1)
                )
        )
        .padding(.horizontal, 40)
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(
            Color.black.opacity(0.4)
                .ignoresSafeArea()
        )
    }
    
    // MARK: - Keep existing views but add enhancements
    
    private var backgroundView: some View {
        LinearGradient(
            colors: [
                Color(red: 0.17, green: 0.17, blue: 0.33),
                Color(red: 0.25, green: 0.25, blue: 0.48),
                Color(red: 0.44, green: 0.44, blue: 0.83)
            ],
            startPoint: .top,
            endPoint: .bottom
        )
    }
    
    private var instructionsCard: some View {
        RoundedRectangle(cornerRadius: 12)
            .fill(Color.white.opacity(0.1))
            .overlay(
                VStack(spacing: 8) {
                    Text("COMPARE \(getConversionTypeDisplayName())")
                        .font(.system(size: 16, weight: .bold))
                        .foregroundColor(.white)
                        .multilineTextAlignment(.center)
                    
                    Text("Drag blocks to compare their values")
                        .font(.system(size: 14, weight: .medium))
                        .foregroundColor(.white.opacity(0.8))
                        .multilineTextAlignment(.center)
                    
                    Text("Higher position = Greater value")
                        .font(.system(size: 14, weight: .medium))
                        .foregroundColor(.white.opacity(0.8))
                        .multilineTextAlignment(.center)
                    
                    Text(difficultyManager.currentDifficulty.description)
                        .font(.caption)
                        .foregroundColor(.cyan.opacity(0.8))
                        .multilineTextAlignment(.center)
                
                    if let data = conversionData, !data.hint.isEmpty {
                        Text("💡 \(data.hint)")
                            .font(.system(size: 12, weight: .medium))
                            .foregroundColor(.white.opacity(0.7))
                            .multilineTextAlignment(.center)
                    }
                }
                .padding(16)
            )
    }
    
    private var blocksArea: some View {
        GeometryReader { geometry in
            ZStack {
                // Left block
                DraggableWeightBlockView(
                    block: leftBlockState,
                    onDragChanged: { translation in
                        if !hasSubmitted && !showFeedback {
                            leftBlockState.offsetY += translation.height
                            leftBlockState.isDragging = true
                            dragCount += 1
                        }
                    },
                    onDragEnded: {
                        leftBlockState.isDragging = false
                        hapticFeedback()
                    }
                )
                .position(
                    x: geometry.size.width * 0.25,
                    y: geometry.size.height * 0.5 + leftBlockState.offsetY
                )
                
                // Right block
                DraggableWeightBlockView(
                    block: rightBlockState,
                    onDragChanged: { translation in
                        if !hasSubmitted && !showFeedback {
                            rightBlockState.offsetY += translation.height
                            rightBlockState.isDragging = true
                            dragCount += 1
                        }
                    },
                    onDragEnded: {
                        rightBlockState.isDragging = false
                        hapticFeedback()
                    }
                )
                .position(
                    x: geometry.size.width * 0.75,
                    y: geometry.size.height * 0.5 + rightBlockState.offsetY
                )
            }
        }
    }
    
    private var continueButton: some View {
        Button(action: submitAnswer) {
            RoundedRectangle(cornerRadius: 28)
                .fill(hasSubmitted ? Color.gray : Color(red: 0.3, green: 0.69, blue: 0.31))
                .frame(height: 56)
                .overlay(
                    Text(hasSubmitted ? "SUBMITTED" : "CONTINUE")
                        .font(.system(size: 18, weight: .bold))
                        .foregroundColor(.white)
                )
        }
        .disabled(hasSubmitted)
    }
    
    // MARK: - Enhanced Helper Methods
    
    private func setupPuzzle() {
        gameStartTime = Date()
        
        if useAdaptiveGeneration && leftBlockState.conversionType == .unknown {
            // Generate new adaptive puzzle
            let (leftBlock, rightBlock, correct) = generator.generateConversionPuzzle(difficulty: difficultyManager.currentDifficulty)
            leftBlockState = leftBlock
            rightBlockState = rightBlock
            correctAnswer = correct
            
            // Use adaptive timer system
            useAdaptiveTimer = true
            adaptiveTimeRemaining = difficultyManager.currentDifficulty.timeLimit
            currentLives = difficultyManager.currentDifficulty.livesAllowed
            
            startAdaptiveTimer()
            
            print("🔧 ADAPTIVE: Generated new puzzle with difficulty: \(difficultyManager.currentDifficulty.name)")
            print("🔧 ADAPTIVE: Time limit set to: \(adaptiveTimeRemaining) seconds")
        } else {
            // Use original puzzle data and timer
            useAdaptiveTimer = false
            correctAnswer = correctAnswerFromData
            timer.start()
            print("🔧 ORIGINAL: Using puzzle data with comparison: \(conversionData?.comparison ?? "unknown")")
        }
        
        print("🔵 CONVERSION: SwipeableBlocksPuzzleView appeared")
        print("🔵 CONVERSION: Correct answer should be: \(correctAnswer)")
    }
    
    private func startAdaptiveTimer() {
        adaptiveTimer?.invalidate()
        adaptiveTimer = Timer.scheduledTimer(withTimeInterval: 1.0, repeats: true) { _ in
            if adaptiveTimeRemaining > 0 && !showFeedback {
                adaptiveTimeRemaining -= 1
            } else if adaptiveTimeRemaining == 0 && !showFeedback {
                adaptiveTimer?.invalidate()
                submitAnswer()
            }
        }
    }
    
    private func submitAnswer() {
        guard !hasSubmitted else { return }
        
        // Stop both timers
        timer.stop()
        adaptiveTimer?.invalidate()
        
        hasSubmitted = true
        let userAnswer = getCurrentAnswer()
        let correct = userAnswer == correctAnswer
        
        // Set feedback data
        isCorrect = correct
        userAnswerText = getCurrentAnswerText(userAnswer)
        correctAnswerText = getCorrectAnswerText()
        
        if correct {
            let score = calculateEnhancedScore()
            totalScore += score
            currentStreak += 1
        } else {
            currentLives = max(0, currentLives - 1)
            currentStreak = 0
        }
        
        // Record performance for adaptive system
        recordPerformanceAndAdapt(isCorrect: correct)
        
        // Show custom feedback
        withAnimation(.easeInOut(duration: 0.5)) {
            showFeedback = true
        }
        
        // Call the callback
        onAnswerSubmitted(userAnswer, correct)
        
        print("🔵 CONVERSION: Answer submitted")
        print("🔵 CONVERSION: User answer: \(userAnswer)")
        print("🔵 CONVERSION: Correct answer: \(correctAnswer)")
        print("🔵 CONVERSION: Is correct: \(correct)")
    }
    
    private func recordPerformanceAndAdapt(isCorrect: Bool) {
        let timeSpent = Date().timeIntervalSince(gameStartTime)
        let conversionComplexity = Float(difficultyManager.currentDifficulty.complexityLevel) / 5.0
        
        let performance = ConversionDifficultyManager.PlayerPerformance(
            accuracy: isCorrect ? 1.0 : 0.0,
            averageResponseTime: Float(timeSpent),
            streakLength: currentStreak,
            livesRemaining: currentLives,
            gameScore: totalScore,
            difficulty: difficultyManager.currentDifficulty.name,
            conversionComplexity: conversionComplexity,
            dragCount: dragCount,
            hintUsed: hintUsed
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
    
    private func resetBlocks() {
        if useAdaptiveGeneration {
            // Generate new adaptive puzzle
            let (leftBlock, rightBlock, correct) = generator.generateConversionPuzzle(difficulty: difficultyManager.currentDifficulty)
            
            withAnimation(.easeInOut(duration: 0.5)) {
                leftBlockState = leftBlock
                rightBlockState = rightBlock
                correctAnswer = correct
                showContinueButton = false
                hasSubmitted = false
                dragCount = 0
                hintUsed = false
                showHint = false
            }
            
            // Reset timer with new duration
            timer.stop()
            useAdaptiveTimer = true
            adaptiveTimeRemaining = difficultyManager.currentDifficulty.timeLimit
            startAdaptiveTimer()
        } else {
            // Reset to original blocks
            if let data = conversionData {
                withAnimation(.easeInOut(duration: 0.5)) {
                    leftBlockState = WeightBlock(
                        id: 1,
                        label: "\(data.value1) \(data.unit1)",
                        value: Double(data.value1),
                        unit: data.unit1,
                        conversionType: .unknown,
                        color: Color(red: 0.91, green: 0.12, blue: 0.39)
                    )
                    rightBlockState = WeightBlock(
                        id: 2,
                        label: "\(data.value2) \(data.unit2)",
                        value: Double(data.value2),
                        unit: data.unit2,
                        conversionType: .unknown,
                        color: Color(red: 0.56, green: 0.79, blue: 0.98)
                    )
                    correctAnswer = correctAnswerFromData
                    showContinueButton = false
                    hasSubmitted = false
                    dragCount = 0
                    hintUsed = false
                    showHint = false
                }
            }
            
            timer.reset()
        }
        
        gameStartTime = Date()
        timer.start()
    }
    
    private func calculateEnhancedScore() -> Int {
        let baseScore = difficultyManager.currentDifficulty.basePoints
        let timeRemaining = useAdaptiveTimer ? adaptiveTimeRemaining : timer.timeRemaining
        let timeBonus = timeRemaining * 2
        let dragPenalty = max(0, dragCount - 3) * 5
        let hintPenalty = hintUsed ? 10 : 0
        let streakBonus = currentStreak * 5
        
        return max(10, baseScore + timeBonus - dragPenalty - hintPenalty + streakBonus)
    }
    
    private func formatAdaptiveTime(_ seconds: Int) -> String {
        let minutes = seconds / 60
        let remainingSeconds = seconds % 60
        return String(format: "%d:%02d", minutes, remainingSeconds)
    }
    
    private func generateHintText() -> String {
        if let data = conversionData, !data.hint.isEmpty {
            return data.hint
        }
        
        switch leftBlockState.conversionType {
        case .distance:
            return "Remember: 1 mile ≈ 1.6 km, 1 meter ≈ 3.3 feet"
        case .weight:
            return "Remember: 1 kg ≈ 2.2 lbs, 1 lb ≈ 16 oz"
        case .volume:
            return "Remember: 1 gallon ≈ 3.8 liters, 1 liter ≈ 4.2 cups"
        case .temperature:
            return "Remember: °F = (°C × 9/5) + 32, °C = (°F - 32) × 5/9"
        case .time:
            return "Remember: 1 hour = 60 minutes = 3600 seconds"
        case .area:
            return "Remember: 1 sq meter ≈ 10.8 sq feet, 1 acre ≈ 4047 sq meters"
        case .speed:
            return "Remember: 1 m/s ≈ 3.6 km/h ≈ 2.2 mph"
        case .unknown:
            return "Compare the values by converting to the same unit"
        }
    }
    
    private func getConversionTypeDisplayName() -> String {
        if leftBlockState.conversionType != .unknown {
            return leftBlockState.conversionType.displayName.uppercased()
        } else if let data = conversionData {
            // Infer from units
            let unit1 = data.unit1.lowercased()
            let unit2 = data.unit2.lowercased()
            
            if unit1.contains("mile") || unit1.contains("km") || unit1.contains("meter") || unit1.contains("feet") {
                return "DISTANCE"
            } else if unit1.contains("lb") || unit1.contains("kg") || unit1.contains("gram") || unit1.contains("oz") {
                return "WEIGHT"
            } else if unit1.contains("gal") || unit1.contains("liter") || unit1.contains("cup") || unit1.contains("quart") {
                return "VOLUME"
            } else if unit1.contains("°") || unit1.contains("fahrenheit") || unit1.contains("celsius") {
                return "TEMPERATURE"
            } else {
                return "UNITS"
            }
        } else {
            return "CONVERSION"
        }
    }
    
    // Keep all existing helper methods unchanged
    private func convertToStandardUnit(_ value: Int, unit: String) -> Double {
        let doubleValue = Double(value)
        print("🔧 CONVERSION: Converting \(value) \(unit)")
        
        switch unit.lowercased() {
        case "miles", "mile":
            let result = doubleValue * 1.609344 // Convert to kilometers
            print("🔧 CONVERSION: \(value) miles = \(result) km")
            return result
        case "kilometers", "km":
            print("🔧 CONVERSION: \(value) km = \(doubleValue) km (no conversion)")
            return doubleValue
        case "feet", "ft":
            let result = doubleValue * 0.3048 // Convert to meters
            print("🔧 CONVERSION: \(value) feet = \(result) meters")
            return result
        case "meters", "m":
            print("🔧 CONVERSION: \(value) meters = \(doubleValue) meters (no conversion)")
            return doubleValue
        case "pounds", "lbs":
            let result = doubleValue * 0.453592 // Convert to kilograms
            print("🔧 CONVERSION: \(value) pounds = \(result) kg")
            return result
        case "kilograms", "kg":
            print("🔧 CONVERSION: \(value) kg = \(doubleValue) kg (no conversion)")
            return doubleValue
        case "grams", "g":
            let result = doubleValue * 0.001 // Convert to kilograms
            print("🔧 CONVERSION: \(value) grams = \(result) kg")
            return result
        case "fahrenheit", "°f":
            let result = (doubleValue - 32) * 5/9 // Convert to Celsius
            print("🔧 CONVERSION: \(value) °F = \(result) °C")
            return result
        case "celsius", "°c":
            print("🔧 CONVERSION: \(value) °C = \(doubleValue) °C (no conversion)")
            return doubleValue
        case "gallons":
            let result = doubleValue * 3.78541 // Convert to liters
            print("🔧 CONVERSION: \(value) gallons = \(result) liters")
            return result
        case "liters":
            print("🔧 CONVERSION: \(value) liters = \(doubleValue) liters (no conversion)")
            return doubleValue
        default:
            print("🔧 CONVERSION: Unknown unit \(unit), using raw value \(doubleValue)")
            return doubleValue
        }
    }
    
    private func getCurrentAnswer() -> ComparisonResult {
        if leftBlockState.offsetY < rightBlockState.offsetY - positionThreshold {
            return .leftHeavier
        } else if rightBlockState.offsetY < leftBlockState.offsetY - positionThreshold {
            return .rightHeavier
        } else {
            return .equal
        }
    }
    
    private func checkMovement() {
        let totalMovement = abs(leftBlockState.offsetY) + abs(rightBlockState.offsetY)
        withAnimation(.easeInOut(duration: 0.3)) {
            showContinueButton = totalMovement > positionThreshold && !hasSubmitted && !showFeedback
        }
    }
    
    private func getCurrentAnswerText(_ answer: ComparisonResult) -> String {
        switch answer {
        case .leftHeavier: return "\(leftBlockState.label) is greater"
        case .rightHeavier: return "\(rightBlockState.label) is greater"
        case .equal: return "Both values are equal"
        }
    }
    
    private func getCorrectAnswerText() -> String {
        switch correctAnswer {
        case .leftHeavier: return "\(leftBlockState.label) is greater"
        case .rightHeavier: return "\(rightBlockState.label) is greater"
        case .equal: return "Both values are approximately equal"
        }
    }
    
    private func calculateScore() -> Int {
        guard let data = conversionData else { return 0 }
        return PuzzleScoring.calculateScore(
            for: "conversion",
            timeRemaining: timer.timeRemaining,
            totalTime: 60,
            difficulty: data.difficulty
        )
    }
    
    private func hapticFeedback() {
        let impactFeedback = UIImpactFeedbackGenerator(style: .medium)
        impactFeedback.impactOccurred()
    }
}

// MARK: - Keep all existing components unchanged (DraggableWeightBlockView, previews, etc.)

struct DraggableWeightBlockView: View {
    let block: WeightBlock
    let onDragChanged: (CGSize) -> Void
    let onDragEnded: () -> Void
    
    var body: some View {
        RoundedRectangle(cornerRadius: 12)
            .fill(block.color)
            .frame(width: 140, height: 80)
            .shadow(
                color: .black.opacity(0.3),
                radius: block.isDragging ? 12 : 6,
                x: 0,
                y: block.isDragging ? 6 : 3
            )
            .overlay(
                Text(block.label)
                    .font(.system(size: 16, weight: .bold))
                    .foregroundColor(.white)
                    .multilineTextAlignment(.center)
                    .lineLimit(2)
            )
            .scaleEffect(block.isDragging ? 1.05 : 1.0)
            .animation(.easeInOut(duration: 0.2), value: block.isDragging)
            .gesture(
                DragGesture()
                    .onChanged { value in
                        onDragChanged(value.translation)
                    }
                    .onEnded { _ in
                        onDragEnded()
                    }
            )
    }
}

// MARK: - Keep existing EnhancedSwipeableBlocksTopGameBar
struct EnhancedSwipeableBlocksTopGameBar: View {
    let level: UserLevel
    let streakInfo: StreakInfo
    let timer: String
    let questionIndex: Int
    let totalQuestions: Int
    let onExit: () -> Void
    
    var body: some View {
        HStack {
            // Left side: Exit button and level
            HStack(spacing: 10) {
                Button(action: onExit) {
                    HStack(spacing: 10) {
                        Image(systemName: "xmark")
                            .foregroundColor(.white.opacity(0.7))
                            .font(.title2)
                        
                        Text("Exit")
                            .font(.title3)
                            .fontWeight(.medium)
                            .foregroundColor(.white.opacity(0.7))
                    }
                }
                
                VStack(alignment: .leading, spacing: 4) {
                    Text("Level \(level.level)")
                        .font(.system(size: 16, weight: .bold))
                        .foregroundColor(.white)
                    
                    LevelProgressBar(level: level)
                        .frame(width: 100)
                }
            }
            
            Spacer()
            
            // Center: Question counter
            Text("\(questionIndex + 1)/\(totalQuestions)")
                .font(.title3)
                .fontWeight(.medium)
                .foregroundColor(.white.opacity(0.8))
            
            Spacer()
            
            // Right side: Timer and streak
            VStack(alignment: .trailing, spacing: 4) {
                Text(timer)
                    .font(.title2)
                    .fontWeight(.bold)
                    .foregroundColor(.white)
                
                if streakInfo.currentStreak > 0 {
                    StreakDisplay(streakInfo: streakInfo)
                }
            }
        }
    }
}

// MARK: - Extension for clamping values
extension Comparable {
    func cclamped(to limits: ClosedRange<Self>) -> Self {
        return min(max(self, limits.lowerBound), limits.upperBound)
    }
}


// MARK: - Preview
struct SwipeableBlocksPuzzleView_Previews: PreviewProvider {
    static var previews: some View {
        let samplePuzzle = Puzzle(
            question: """
            {"value1":40,"unit1":"miles","value2":60,"unit2":"kilometers","comparison":"approximately equal","difficulty":"Easy","hint":"Remember: 1 mile = 1.6 kilometers"}
            """,
            answer: "approximately equal",
            hint: "Remember: 1 mile = 1.6 kilometers",
            options: [],
            format: "conversion",
            puzzleType: "conversion",
            puzzleId: "sample-conversion",
            id: "sample-conversion",
            name: "Conversion Puzzle",
            createdAt: Date().timeIntervalSince1970 * 1000,
            status: "ready",
            difficulty: "Easy"
        )
        
        SwipeableBlocksPuzzleView(
            puzzle: samplePuzzle,
            questionIndex: 0,
            totalQuestions: 1,
            onAnswerSubmitted: { answer, isCorrect in
                print("Answer: \(answer), Correct: \(isCorrect)")
            },
            onNextPuzzle: {
                print("Next puzzle")
            },
            onExit: {
                print("Exit")
            }
        )
    }
}
