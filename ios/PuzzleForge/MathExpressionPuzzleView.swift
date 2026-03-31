//
//  AdaptiveMathComparisonPuzzleView.swift
//  PuzzleForge
//
//  Enhanced adaptive math comparison puzzle with dynamic difficulty adjustment
//

import SwiftUI
import Foundation

// MARK: - Data Models
struct AdaptiveMathComparisonData: Codable {
    let sequence: [AdaptiveComparisonPair]
    let totalPairs: Int
    let difficulty: String
    let timeLimit: Int
    let adaptiveConfig: AdaptiveConfig
    let instructions: String
    
    struct AdaptiveComparisonPair: Codable {
        let pairNumber: Int
        let leftValue: String
        let rightValue: String
        let leftNumeric: Double
        let rightNumeric: Double
        let correctAnswer: String // "left", "right", or "equal"
        let operationType: String
        let difficulty: Double
        let cognitiveLoad: Int
        let workingMemorySteps: Int
    }
    
    struct AdaptiveConfig: Codable {
        let name: String
        let description: String
        let numericalComplexity: Int
        let workingMemoryDemand: Bool
        let proceduralComplexity: Bool
        let abstractReasoning: Bool
        let speedPressure: Int
        let errorInduction: Bool
    }
}

// MARK: - Adaptive Math Comparison Generator
class AdaptiveMathComparisonGenerator {
    
    static func generateAdaptivePuzzle(difficultyLevel: DifficultyManager.DifficultyLevel) -> (String, String) {
        let config = getDifficultyConfig(for: difficultyLevel)
        let sequenceLength = getSequenceLength(for: config)
        
        let puzzleSequence = generateAdaptiveSequence(config: config, length: sequenceLength)
        
        let questionData = AdaptiveMathComparisonData(
            sequence: puzzleSequence,
            totalPairs: puzzleSequence.count,
            difficulty: config.name,
            timeLimit: calculateTimeLimit(config: config, sequenceLength: sequenceLength),
            adaptiveConfig: config,
            instructions: generateInstructions(for: config)
        )
        
        let correctAnswers = puzzleSequence.map { $0.correctAnswer }
        let maxScore = calculateMaxScore(totalQuestions: puzzleSequence.count, config: config)

        // Calculate cognitive load average step by step
        let cognitiveLoads = puzzleSequence.map { Double($0.cognitiveLoad) }
        let totalCognitiveLoad = cognitiveLoads.reduce(0, +)
        let averageCognitiveLoad = totalCognitiveLoad / Double(puzzleSequence.count)

        // Calculate working memory steps
        let totalWorkingMemorySteps = puzzleSequence.map { $0.workingMemorySteps }.reduce(0, +)

        // Get complexity progression
        let complexityProgression = puzzleSequence.map { $0.difficulty }
        
        let cognitiveMetrics = CognitiveMetrics(
            averageCognitiveLoad: averageCognitiveLoad,
            totalWorkingMemorySteps: totalWorkingMemorySteps,
            complexityProgression: complexityProgression
        )

        // Finally create the answer data
        let answerData = AdaptiveAnswerData(
            correctAnswers: correctAnswers,
            maxScore: maxScore,
            cognitiveMetrics: cognitiveMetrics
        )
        
        do {
            let encoder = JSONEncoder()
            let questionJSON = try encoder.encode(questionData)
            let answerJSON = try encoder.encode(answerData)
            
            return (String(data: questionJSON, encoding: .utf8) ?? "{}",
                    String(data: answerJSON, encoding: .utf8) ?? "{}")
        } catch {
            print("❌ Failed to encode adaptive puzzle: \(error)")
            return generateFallbackPuzzle()
        }
    }
    
    private static func getDifficultyConfig(for level: DifficultyManager.DifficultyLevel) -> AdaptiveMathComparisonData.AdaptiveConfig {
        return AdaptiveMathComparisonData.AdaptiveConfig(
            name: level.name,
            description: "Mathematical reasoning at \(level.name.lowercased()) level",
            numericalComplexity: level.numericalComplexity,
            workingMemoryDemand: level.workingMemoryDemand,
            proceduralComplexity: level.proceduralComplexity,
            abstractReasoning: level.abstractReasoning,
            speedPressure: level.speedPressure,
            errorInduction: level.errorInduction
        )
    }
    
    private static func generateAdaptiveSequence(config: AdaptiveMathComparisonData.AdaptiveConfig, length: Int) -> [AdaptiveMathComparisonData.AdaptiveComparisonPair] {
        var sequence: [AdaptiveMathComparisonData.AdaptiveComparisonPair] = []
        
        for i in 0..<length {
            let progressRatio = Double(i) / Double(length)
            let pair = generateAdaptivePair(config: config, pairNumber: i + 1, progressRatio: progressRatio)
            sequence.append(pair)
        }
        
        return sequence
    }
    
    private static func generateAdaptivePair(config: AdaptiveMathComparisonData.AdaptiveConfig, pairNumber: Int, progressRatio: Double) -> AdaptiveMathComparisonData.AdaptiveComparisonPair {
        let operationType = selectOperation(config: config, progressRatio: progressRatio)
        let numberRange = calculateNumberRange(config: config, progressRatio: progressRatio)
        
        let leftValue = generateValue(operationType: operationType, config: config, range: numberRange)
        let rightValue = generateValue(operationType: operationType, config: config, range: numberRange)
        
        let leftNumeric = evaluateExpression(leftValue)
        let rightNumeric = evaluateExpression(rightValue)
        
        let tolerance = config.errorInduction ? 0.001 : 0.01
        let correctAnswer: String
        if abs(leftNumeric - rightNumeric) < tolerance {
            correctAnswer = "equal"
        } else if leftNumeric > rightNumeric {
            correctAnswer = "left"
        } else {
            correctAnswer = "right"
        }
        
        let cognitiveLoad = calculateCognitiveLoad(leftValue: leftValue, rightValue: rightValue, operationType: operationType, config: config)
        let workingMemorySteps = calculateWorkingMemorySteps(leftValue: leftValue, rightValue: rightValue)
        let difficulty = calculatePairDifficulty(leftValue: leftValue, rightValue: rightValue, operationType: operationType, config: config)
        
        return AdaptiveMathComparisonData.AdaptiveComparisonPair(
            pairNumber: pairNumber,
            leftValue: leftValue,
            rightValue: rightValue,
            leftNumeric: leftNumeric,
            rightNumeric: rightNumeric,
            correctAnswer: correctAnswer,
            operationType: operationType,
            difficulty: difficulty,
            cognitiveLoad: cognitiveLoad,
            workingMemorySteps: workingMemorySteps
        )
    }
    
    // Helper methods for generation logic
    private static func selectOperation(config: AdaptiveMathComparisonData.AdaptiveConfig, progressRatio: Double) -> String {
        let operations: [String]
        switch config.numericalComplexity {
        case 1: operations = ["addition", "subtraction"]
        case 2: operations = ["addition", "subtraction", "simple_multiplication"]
        case 3: operations = ["addition", "subtraction", "multiplication", "division", "mixed"]
        case 4: operations = ["multiplication", "division", "mixed", "fractions"]
        case 5: operations = ["fractions", "mixed", "algebraic"]
        default: operations = ["addition", "subtraction"]
        }
        
        return operations.randomElement() ?? "addition"
    }
    
    private static func calculateNumberRange(config: AdaptiveMathComparisonData.AdaptiveConfig, progressRatio: Double) -> (Int, Int) {
        let baseRanges: [(Int, Int)] = [
            (1, 15),    // Level 1
            (1, 25),    // Level 2
            (1, 50),    // Level 3
            (-50, 100), // Level 4
            (-100, 200) // Level 5
        ]
        
        let baseRange = baseRanges[min(config.numericalComplexity - 1, baseRanges.count - 1)]
        let expansion = Int(progressRatio * Double(baseRange.1 - baseRange.0) * 0.5)
        
        return (baseRange.0, baseRange.1 + expansion)
    }
    
    private static func generateValue(operationType: String, config: AdaptiveMathComparisonData.AdaptiveConfig, range: (Int, Int)) -> String {
        switch operationType {
        case "addition":
            return generateAddition(config: config, range: range)
        case "subtraction":
            return generateSubtraction(config: config, range: range)
        case "simple_multiplication":
            return generateSimpleMultiplication(config: config)
        case "multiplication":
            return generateMultiplication(config: config, range: range)
        case "division":
            return generateDivision(config: config, range: range)
        case "mixed":
            return generateMixedExpression(config: config, range: range)
        case "fractions":
            return generateFractionExpression(config: config, range: range)
        case "algebraic":
            return generateAlgebraicExpression(config: config)
        default:
            return generateSimpleNumber(range: range)
        }
    }
    
    private static func generateAddition(config: AdaptiveMathComparisonData.AdaptiveConfig, range: (Int, Int)) -> String {
        if config.workingMemoryDemand {
            let a = randomNumber(in: range)
            let b = randomNumber(in: range)
            let c = randomNumber(in: (1, 10))
            return "(\(a) + \(b)) + \(c)"
        } else {
            let a = randomNumber(in: range)
            let b = randomNumber(in: range)
            return "\(a) + \(b)"
        }
    }
    
    private static func generateSubtraction(config: AdaptiveMathComparisonData.AdaptiveConfig, range: (Int, Int)) -> String {
        if config.workingMemoryDemand {
            let a = randomNumber(in: range)
            let b = randomNumber(in: (1, abs(a) / 2 + 1))
            let c = randomNumber(in: (1, 10))
            return "(\(a) - \(b)) - \(c)"
        } else {
            let a = randomNumber(in: range)
            let b = randomNumber(in: (range.0, abs(a)))
            return "\(a) - \(b)"
        }
    }
    
    private static func generateSimpleMultiplication(config: AdaptiveMathComparisonData.AdaptiveConfig) -> String {
        let maxVal = config.numericalComplexity <= 2 ? 9 : 12
        let a = randomNumber(in: (2, maxVal))
        let b = randomNumber(in: (2, maxVal))
        return "\(a) × \(b)"
    }
    
    private static func generateMultiplication(config: AdaptiveMathComparisonData.AdaptiveConfig, range: (Int, Int)) -> String {
        let maxVal = min(abs(range.1), config.numericalComplexity >= 4 ? 25 : 15)
        
        if config.workingMemoryDemand {
            let a = randomNumber(in: (2, maxVal))
            let b = randomNumber(in: (2, 8))
            let c = randomNumber(in: (2, 5))
            return "(\(a) × \(b)) + \(c)"
        } else {
            let a = randomNumber(in: (2, maxVal))
            let b = randomNumber(in: (2, maxVal))
            return "\(a) × \(b)"
        }
    }
    
    private static func generateDivision(config: AdaptiveMathComparisonData.AdaptiveConfig, range: (Int, Int)) -> String {
        let b = randomNumber(in: (2, min(abs(range.1), 12)))
        let result = randomNumber(in: (1, abs(range.1)))
        let a = b * result
        
        if config.workingMemoryDemand {
            let c = randomNumber(in: (1, 10))
            return "(\(a) ÷ \(b)) + \(c)"
        } else {
            return "\(a) ÷ \(b)"
        }
    }
    
    private static func generateMixedExpression(config: AdaptiveMathComparisonData.AdaptiveConfig, range: (Int, Int)) -> String {
        let operations = config.proceduralComplexity ? ["+", "-", "×", "÷"] : ["+", "-", "×"]
        let op1 = operations.randomElement() ?? "+"
        let op2 = operations.randomElement() ?? "+"
        let maxVal = min(abs(range.1), 15)
        
        let a = randomNumber(in: (2, maxVal))
        let b = randomNumber(in: (1, maxVal))
        let c = randomNumber(in: (1, maxVal))
        
        if config.workingMemoryDemand {
            return "(\(a) \(op1) \(b)) \(op2) \(c)"
        } else {
            return "\(a) \(op1) \(b) \(op2) \(c)"
        }
    }
    
    private static func generateFractionExpression(config: AdaptiveMathComparisonData.AdaptiveConfig, range: (Int, Int)) -> String {
        let numerator = randomNumber(in: (1, abs(range.1)))
        let denominator = randomNumber(in: (2, 12))
        
        if config.workingMemoryDemand {
            let additional = randomNumber(in: (1, 5))
            return "\(numerator)/\(denominator) + \(additional)"
        } else {
            return "\(numerator)/\(denominator)"
        }
    }
    
    private static func generateAlgebraicExpression(config: AdaptiveMathComparisonData.AdaptiveConfig) -> String {
        let coefficient = randomNumber(in: (2, 8))
        let constant = randomNumber(in: (1, 20))
        let x = randomNumber(in: (1, 10))
        return "\(coefficient)x + \(constant) (x=\(x))"
    }
    
    private static func generateSimpleNumber(range: (Int, Int)) -> String {
        return "\(randomNumber(in: range))"
    }
    
    private static func randomNumber(in range: (Int, Int)) -> Int {
        return Int.random(in: range.0...range.1)
    }
    
    private static func evaluateExpression(_ expression: String) -> Double {
        // Simplified expression evaluator
        // In production, you'd want a more robust parser
        let cleanExpression = expression
            .replacingOccurrences(of: "×", with: "*")
            .replacingOccurrences(of: "÷", with: "/")
        
        // Handle algebraic expressions
        if cleanExpression.contains("x=") {
            let parts = cleanExpression.components(separatedBy: " (x=")
            guard parts.count == 2 else { return 0 }
            let algebraic = parts[0]
            let xValueStr = parts[1].replacingOccurrences(of: ")", with: "")
            guard let xValue = Double(xValueStr) else { return 0 }
            
            let substituted = algebraic.replacingOccurrences(of: "x", with: "*\(xValue)")
            return evaluateArithmetic(substituted)
        }
        
        // Handle fractions
        if cleanExpression.contains("/") && !cleanExpression.contains(" ") {
            let parts = cleanExpression.components(separatedBy: "/")
            if parts.count == 2, let num = Double(parts[0]), let den = Double(parts[1]), den != 0 {
                return num / den
            }
        }
        
        return evaluateArithmetic(cleanExpression)
    }
    
    private static func evaluateArithmetic(_ expression: String) -> Double {
        // Simple arithmetic evaluator using NSExpression
        do {
            let expr = NSExpression(format: expression)
            if let result = expr.expressionValue(with: nil, context: nil) as? NSNumber {
                return result.doubleValue
            }
        } catch {
            print("❌ Error evaluating expression: \(expression)")
        }
        return 0.0
    }
    
    private static func calculateCognitiveLoad(leftValue: String, rightValue: String, operationType: String, config: AdaptiveMathComparisonData.AdaptiveConfig) -> Int {
        var load = 1
        
        // Base load by operation type
        let operationLoads = [
            "addition": 1, "subtraction": 1,
            "multiplication": 2, "division": 2,
            "mixed": 3, "fractions": 3, "algebraic": 4
        ]
        load += operationLoads[operationType] ?? 1
        
        if config.workingMemoryDemand { load += 2 }
        if config.proceduralComplexity { load += 1 }
        if config.abstractReasoning { load += 2 }
        
        return min(load, 10)
    }
    
    private static func calculateWorkingMemorySteps(leftValue: String, rightValue: String) -> Int {
        let leftSteps = leftValue.filter { "+-×÷*/".contains($0) }.count + 1
        let rightSteps = rightValue.filter { "+-×÷*/".contains($0) }.count + 1
        return leftSteps + rightSteps + 1
    }
    
    private static func calculatePairDifficulty(leftValue: String, rightValue: String, operationType: String, config: AdaptiveMathComparisonData.AdaptiveConfig) -> Double {
        var difficulty = 1.0
        
        let operationDifficulties = [
            "addition": 1.0, "subtraction": 1.2,
            "simple_multiplication": 1.5, "multiplication": 2.0,
            "division": 2.5, "mixed": 3.0,
            "fractions": 3.5, "algebraic": 4.0
        ]
        
        difficulty *= operationDifficulties[operationType] ?? 1.0
        difficulty *= (1 + Double(config.numericalComplexity) * 0.3)
        
        if config.workingMemoryDemand { difficulty *= 1.5 }
        if config.proceduralComplexity { difficulty *= 1.3 }
        if config.abstractReasoning { difficulty *= 1.4 }
        if config.errorInduction { difficulty *= 1.2 }
        
        let avgLength = Double(leftValue.count + rightValue.count) / 2.0
        difficulty *= (1 + avgLength * 0.05)
        
        return (difficulty * 10).rounded() / 10
    }
    
    private static func getSequenceLength(for config: AdaptiveMathComparisonData.AdaptiveConfig) -> Int {
        switch config.numericalComplexity {
        case 1: return 6
        case 2: return 8
        case 3: return 12
        case 4: return 15
        case 5: return 18
        default: return 10
        }
    }
    
    private static func calculateTimeLimit(config: AdaptiveMathComparisonData.AdaptiveConfig, sequenceLength: Int) -> Int {
        let baseTimePerPair: Int
        switch config.speedPressure {
        case 1: baseTimePerPair = 8
        case 2: baseTimePerPair = 6
        case 3: baseTimePerPair = 5
        case 4: baseTimePerPair = 4
        case 5: baseTimePerPair = 3
        default: baseTimePerPair = 6
        }
        
        return baseTimePerPair * sequenceLength
    }
    
    private static func generateInstructions(for config: AdaptiveMathComparisonData.AdaptiveConfig) -> String {
        var instructions = "Compare the mathematical expressions and select which is greater, or EQUAL if they are the same."
        var hints: [String] = []
        
        if config.workingMemoryDemand {
            hints.append("Remember to evaluate expressions in parentheses first")
        }
        if config.proceduralComplexity {
            hints.append("Use order of operations: parentheses, multiplication/division, addition/subtraction")
        }
        if config.abstractReasoning {
            hints.append("For algebraic expressions, substitute the given value of x first")
        }
        if config.speedPressure >= 4 {
            hints.append("Work quickly but accurately - time is limited")
        }
        if config.errorInduction {
            hints.append("Be careful with similar-looking numbers and operations")
        }
        
        if !hints.isEmpty {
            instructions += "\n\nTips: " + hints.joined(separator: "; ")
        }
        
        return instructions
    }
    
    private static func calculateMaxScore(totalQuestions: Int, config: AdaptiveMathComparisonData.AdaptiveConfig) -> Int {
        let baseScore = totalQuestions * 30
        let complexityMultiplier: Double
        
        switch config.numericalComplexity {
        case 1: complexityMultiplier = 1.0
        case 2: complexityMultiplier = 1.3
        case 3: complexityMultiplier = 1.6
        case 4: complexityMultiplier = 2.0
        case 5: complexityMultiplier = 2.5
        default: complexityMultiplier = 1.0
        }
        
        var cognitiveBonus = 1.0
        if config.workingMemoryDemand { cognitiveBonus *= 1.3 }
        if config.proceduralComplexity { cognitiveBonus *= 1.2 }
        if config.abstractReasoning { cognitiveBonus *= 1.4 }
        if config.errorInduction { cognitiveBonus *= 1.2 }
        
        let speedMultiplier: Double
        switch config.speedPressure {
        case 1, 2: speedMultiplier = 1.0
        case 3: speedMultiplier = 1.1
        case 4: speedMultiplier = 1.2
        case 5: speedMultiplier = 1.3
        default: speedMultiplier = 1.0
        }
        
        return Int(Double(baseScore) * complexityMultiplier * cognitiveBonus * speedMultiplier)
    }
    
    private static func generateFallbackPuzzle() -> (String, String) {
        let fallbackData = AdaptiveMathComparisonData(
            sequence: [
                AdaptiveMathComparisonData.AdaptiveComparisonPair(
                    pairNumber: 1,
                    leftValue: "5 + 3",
                    rightValue: "2 × 4",
                    leftNumeric: 8.0,
                    rightNumeric: 8.0,
                    correctAnswer: "equal",
                    operationType: "addition,multiplication",
                    difficulty: 1.0,
                    cognitiveLoad: 2,
                    workingMemorySteps: 3
                )
            ],
            totalPairs: 1,
            difficulty: "Easy",
            timeLimit: 60,
            adaptiveConfig: AdaptiveMathComparisonData.AdaptiveConfig(
                name: "Easy",
                description: "Simple arithmetic",
                numericalComplexity: 1,
                workingMemoryDemand: false,
                proceduralComplexity: false,
                abstractReasoning: false,
                speedPressure: 1,
                errorInduction: false
            ),
            instructions: "Compare the values and select which is greater, or select EQUAL if they are the same."
        )
        
        do {
            let encoder = JSONEncoder()
            let questionJSON = try encoder.encode(fallbackData)
            return (String(data: questionJSON, encoding: .utf8) ?? "{}", "{}")
        } catch {
            return ("{}", "{}")
        }
    }
}

// MARK: - Supporting Data Models
struct AdaptiveAnswerData: Codable {
    let correctAnswers: [String]
    let maxScore: Int
    let cognitiveMetrics: CognitiveMetrics
}

struct CognitiveMetrics: Codable {
    let averageCognitiveLoad: Double
    let totalWorkingMemorySteps: Int
    let complexityProgression: [Double]
}

// MARK: - Main View
struct AdaptiveMathComparisonPuzzleView: View {
    let initialDifficulty: String
    let timer: String
    let hearts: Int
    let level: String
    let onSubmitAnswer: (Bool) -> Void
    let fetchNextPuzzle: (Int) -> Void
    let onBack: () -> Void
    
    // Difficulty management
    @StateObject private var difficultyManager = DifficultyManager()
    @State private var currentDifficultyLevel: DifficultyManager.DifficultyLevel = .medium
    @State private var adaptationInfo: DifficultyManager.AdaptiveConfig?
    @State private var showAdaptationNotification = false
    
    @State private var puzzleData: AdaptiveMathComparisonData?
    @State private var currentPairIndex = 0
    @State private var selectedAnswer: String?
    @State private var showResult = false
    @State private var isGameComplete = false
    @State private var totalScore = 0
    @State private var correctAnswers = 0
    @State private var totalAttempts = 0
    @State private var currentStreak = 0
    @State private var bestStreak = 0
    @State private var currentHearts: Int
    @State private var gameStartTime = Date()
    @State private var sessionStartTime = Date()
    @State private var reactionTimes: [TimeInterval] = []
    @State private var pairStartTime = Date()
    // Add these state variables near the top of your AdaptiveMathComparisonPuzzleView
    @State private var currentLevel = UserLevel(level: 1, currentXP: 0, xpToNextLevel: 100, totalXP: 0)

    @State private var streakInfo = StreakInfo(
        currentStreak: 0,
        bestStreak: 0,
        streakMultiplier: 1.0,
        dailyStreak: 0,
        hasDailyStreakBonus: false
    )
    
    @State private var timeRemaining: Int
    @State private var timer_: Timer?
    
    init(initialDifficulty: String, timer: String, hearts: Int, level: String, onSubmitAnswer: @escaping (Bool) -> Void, fetchNextPuzzle: @escaping (Int) -> Void, onBack: @escaping () -> Void) {
        self.initialDifficulty = initialDifficulty
        self.timer = timer
        self.hearts = hearts
        self.level = level
        self.onSubmitAnswer = onSubmitAnswer
        self.fetchNextPuzzle = fetchNextPuzzle
        self.onBack = onBack
        
        self._currentHearts = State(initialValue: hearts)
        self._timeRemaining = State(initialValue: Self.parseTimer(timer))
    }
    
    var currentPair: AdaptiveMathComparisonData.AdaptiveComparisonPair? {
        guard let data = puzzleData, currentPairIndex < data.sequence.count else { return nil }
        return data.sequence[currentPairIndex]
    }
    
    var body: some View {
        GeometryReader { geometry in
            ZStack {
                // Background gradient
                LinearGradient(
                    colors: [Color(red: 0.3, green: 0.69, blue: 0.31), Color(red: 0.18, green: 0.49, blue: 0.2)],
                    startPoint: .top,
                    endPoint: .bottom
                )
                .ignoresSafeArea()
                
                VStack(spacing: 16) {
                    // Header
                    AdaptiveUnifiedHeader(
                        puzzleType: "mathComparison",
                        currentDifficulty: currentDifficultyLevel,
                        score: totalScore,
                        challengeNumber: currentPairIndex + 1,
                        totalChallenges: puzzleData?.totalPairs ?? 0,
                        timer: formatTime(timeRemaining),
                        lives: currentHearts,
                        competitiveInsight: nil,
                        level: currentLevel,
                        streakInfo: streakInfo,
                        onBack: onBack,
                        onPause: {
                            // Add pause functionality
                            //isPaused.toggle()
                        },
                        onHint: {
                            // Add hint functionality
                            //showHint.toggle()
                            // You might want to add haptic feedback here too
                            // haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        }
                    )
                    
                    // Adaptation notification
                    if showAdaptationNotification, let info = adaptationInfo {
                        AdaptationNotificationCard(
                            adaptationInfo: info,
                            onDismiss: { showAdaptationNotification = false }
                        )
                    }
                    
                    // Lives indicator
                    HStack(spacing: 4) {
                        ForEach(0..<hearts, id: \.self) { index in
                            Image(systemName: "heart.fill")
                                .foregroundColor(index < currentHearts ? Color.pink : Color.gray)
                                .font(.system(size: 20))
                        }
                    }
                    
                    // Progress indicator
                    if let data = puzzleData {
                        VStack(spacing: 8) {
                            Text("Question \(currentPairIndex + 1) of \(data.totalPairs)")
                                .font(.system(size: 14))
                                .foregroundColor(.white.opacity(0.8))
                            
                            ProgressView(value: Double(currentPairIndex + 1), total: Double(data.totalPairs))
                                .progressViewStyle(LinearProgressViewStyle(tint: .green))
                                .frame(height: 4)
                                .scaleEffect(x: 1, y: 1, anchor: .center)
                        }
                    }
                    
                    // Question text
                    VStack {
                        Text("Which value is greater?")
                            .font(.system(size: 24, weight: .medium))
                            .foregroundColor(.white)
                            .multilineTextAlignment(.center)
                        
                        if let pair = currentPair {
                            HStack(spacing: 8) {
                                Text("Difficulty: \(String(format: "%.1f", pair.difficulty))/5")
                                    .font(.system(size: 12))
                                    .foregroundColor(.white.opacity(0.7))
                                Text("Load: \(pair.cognitiveLoad)/10")
                                    .font(.system(size: 12))
                                    .foregroundColor(.white.opacity(0.7))
                            }
                        }
                    }
                    
                    if let pair = currentPair {
                        // Value comparison cards
                        VStack(spacing: 16) {
                            AdaptiveComparisonValueCard(
                                value: pair.leftValue,
                                isSelected: selectedAnswer == "left",
                                isCorrect: showResult ? pair.correctAnswer == "left" : nil,
                                isWrong: showResult && selectedAnswer == "left" && pair.correctAnswer != "left",
                                enabled: !showResult,
                                onClick: { handleAnswer("left") }
                            )
                            
                            AdaptiveComparisonValueCard(
                                value: pair.rightValue,
                                isSelected: selectedAnswer == "right",
                                isCorrect: showResult ? pair.correctAnswer == "right" : nil,
                                isWrong: showResult && selectedAnswer == "right" && pair.correctAnswer != "right",
                                enabled: !showResult,
                                onClick: { handleAnswer("right") }
                            )
                        }
                        
                        // Equal button
                        Button(action: { handleAnswer("equal") }) {
                            HStack {
                                Text("EQUAL")
                                    .font(.system(size: 18, weight: .bold))
                                    .foregroundColor(.white)
                                
                                if showResult && selectedAnswer == "equal" {
                                    Image(systemName: pair.correctAnswer == "equal" ? "checkmark" : "xmark")
                                        .font(.system(size: 20, weight: .bold))
                                        .foregroundColor(.white)
                                }
                            }
                            .frame(maxWidth: .infinity)
                            .frame(height: 56)
                            .background(equalButtonColor(for: pair))
                            .cornerRadius(12)
                        }
                        .disabled(showResult)
                        
                        // Result feedback
                        if showResult {
                            let isCorrect = selectedAnswer == pair.correctAnswer
                            
                            VStack(spacing: 8) {
                                Text(isCorrect ? "✅ Correct! +100 points" : "❌ Wrong answer. Lives: \(currentHearts)")
                                    .font(.system(size: 16, weight: .medium))
                                    .foregroundColor(.white)
                                
                                if !isCorrect {
                                    Text("\(pair.leftValue) = \(String(format: "%.1f", pair.leftNumeric)), \(pair.rightValue) = \(String(format: "%.1f", pair.rightNumeric))")
                                        .font(.system(size: 14))
                                        .foregroundColor(.white.opacity(0.8))
                                } else if !reactionTimes.isEmpty {
                                    Text("⚡ \(String(format: "%.1f", reactionTimes.last ?? 0))s")
                                        .font(.system(size: 14))
                                        .foregroundColor(.white.opacity(0.8))
                                }
                            }
                            .padding(16)
                            .background(
                                RoundedRectangle(cornerRadius: 8)
                                    .fill(isCorrect ? Color.green.opacity(0.2) : Color.red.opacity(0.2))
                            )
                            .transition(.opacity.combined(with: .move(edge: .bottom)))
                        }
                    } else {
                        // Loading state
                        VStack(spacing: 16) {
                            ProgressView()
                                .progressViewStyle(CircularProgressViewStyle(tint: .white))
                                .scaleEffect(1.5)
                            
                            Text("Loading math problems...")
                                .font(.system(size: 16))
                                .foregroundColor(.white)
                        }
                        .frame(maxHeight: 200)
                    }
                    
                    Spacer()
                }
                .padding(16)
            }
        }
        .navigationBarHidden(true)
        .onAppear {
            setupPuzzle()
            startTimer()
        }
        .onDisappear {
            stopTimer()
        }
    }
    
    // MARK: - Helper Methods
    private func setupPuzzle() {
        gameStartTime = Date()
        sessionStartTime = Date()
        currentDifficultyLevel = difficultyManager.getCurrentDifficulty(for: "mathComparison")
        
        // This should now work if you update the generator method signature
        let (questionJSON, _) = AdaptiveMathComparisonGenerator.generateAdaptivePuzzle(difficultyLevel: currentDifficultyLevel)
        
        if let data = questionJSON.data(using: .utf8) {
            do {
                puzzleData = try JSONDecoder().decode(AdaptiveMathComparisonData.self, from: data)
                pairStartTime = Date()
            } catch {
                print("❌ Failed to decode puzzle data: \(error)")
                generateFallbackPuzzle()
            }
        }
    }

    private func generateFallbackPuzzle() {
        // Create a simple fallback puzzle
        puzzleData = AdaptiveMathComparisonData(
            sequence: [
                AdaptiveMathComparisonData.AdaptiveComparisonPair(
                    pairNumber: 1,
                    leftValue: "5 + 3",
                    rightValue: "2 × 4",
                    leftNumeric: 8.0,
                    rightNumeric: 8.0,
                    correctAnswer: "equal",
                    operationType: "addition,multiplication",
                    difficulty: 1.0,
                    cognitiveLoad: 2,
                    workingMemorySteps: 3
                )
            ],
            totalPairs: 1,
            difficulty: "Easy",
            timeLimit: 60,
            adaptiveConfig: AdaptiveMathComparisonData.AdaptiveConfig(
                name: "Easy",
                description: "Simple arithmetic",
                numericalComplexity: 1,
                workingMemoryDemand: false,
                proceduralComplexity: false,
                abstractReasoning: false,
                speedPressure: 1,
                errorInduction: false
            ),
            instructions: "Compare the values and select which is greater, or select EQUAL if they are the same."
        )
    }
    
    private func handleAnswer(_ answer: String) {
        guard !showResult, let pair = currentPair, !isGameComplete else { return }
        
        selectedAnswer = answer
        showResult = true
        totalAttempts += 1
        
        let reactionTime = Date().timeIntervalSince(pairStartTime)
        reactionTimes.append(reactionTime)
        
        let isCorrect = answer == pair.correctAnswer
        
        if isCorrect {
            correctAnswers += 1
            currentStreak += 1
            if currentStreak > bestStreak {
                bestStreak = currentStreak
            }
            
            let score = calculateScore(isCorrect: true, timeSpent: reactionTime)
            totalScore += score
        } else {
            currentHearts = max(0, currentHearts - 1)
            currentStreak = 0
        }
        
        // Record performance for adaptive difficulty
        recordPerformance(isCorrect: isCorrect, timeSpent: reactionTime)
        
        // Auto-advance after showing result
        DispatchQueue.main.asyncAfter(deadline: .now() + 2.0) {
            advanceToNextPair()
        }
    }
    
    private func advanceToNextPair() {
        if currentPairIndex >= (puzzleData?.totalPairs ?? 0) - 1 || currentHearts <= 0 {
            completeGame()
        } else {
            currentPairIndex += 1
            selectedAnswer = nil
            showResult = false
            pairStartTime = Date()
        }
    }
    
    private func completeGame() {
        isGameComplete = true
        stopTimer()
        
        let isSuccess = correctAnswers >= Int(Double(puzzleData?.totalPairs ?? 1) * 0.6)
        
        onSubmitAnswer(isSuccess)
        fetchNextPuzzle(totalScore)
    }
    
    private func recordPerformance(isCorrect: Bool, timeSpent: TimeInterval) {
        // Implement adaptive difficulty adjustment logic here
        // This would interface with your DifficultyManager
    }
    
    private func calculateScore(isCorrect: Bool, timeSpent: TimeInterval) -> Int {
        guard isCorrect else { return 0 }
        
        let baseScore = 100
        let timeBonus = max(0, Int((5.0 - timeSpent) * 10))
        let streakBonus = currentStreak > 1 ? currentStreak * 5 : 0
        
        return baseScore + timeBonus + streakBonus
    }
    
    private func equalButtonColor(for pair: AdaptiveMathComparisonData.AdaptiveComparisonPair) -> Color {
        if showResult && selectedAnswer == "equal" {
            return pair.correctAnswer == "equal" ? Color.green : Color.red
        }
        return Color(red: 0.0, green: 0.737, blue: 0.831)
    }
    
    private func startTimer() {
        timer_ = Timer.scheduledTimer(withTimeInterval: 1.0, repeats: true) { _ in
            if timeRemaining > 0 && !isGameComplete {
                timeRemaining -= 1
            } else if timeRemaining == 0 && !isGameComplete {
                // Time's up
                completeGame()
            }
        }
    }
    
    private func stopTimer() {
        timer_?.invalidate()
        timer_ = nil
    }
    
    private func formatTime(_ seconds: Int) -> String {
        let minutes = seconds / 60
        let remainingSeconds = seconds % 60
        return String(format: "%d:%02d", minutes, remainingSeconds)
    }
    
    private static func parseTimer(_ timer: String) -> Int {
        let parts = timer.split(separator: ":")
        if parts.count == 2,
           let minutes = Int(parts[0]),
           let seconds = Int(parts[1]) {
            return minutes * 60 + seconds
        }
        return 60
    }
}

// MARK: - Supporting Views
struct AdaptiveComparisonValueCard: View {
    let value: String
    let isSelected: Bool
    let isCorrect: Bool?
    let isWrong: Bool
    let enabled: Bool
    let onClick: () -> Void
    
    var body: some View {
        Button(action: onClick) {
            HStack {
                Text(value)
                    .font(.system(size: 28, weight: .bold))
                    .foregroundColor(Color(red: 0.13, green: 0.59, blue: 0.95))
                    .multilineTextAlignment(.leading)
                
                Spacer()
                
                if isCorrect == true {
                    Image(systemName: "checkmark")
                        .font(.system(size: 28, weight: .bold))
                        .foregroundColor(.green)
                } else if isWrong {
                    Image(systemName: "xmark")
                        .font(.system(size: 28, weight: .bold))
                        .foregroundColor(.red)
                }
            }
            .padding(24)
            .frame(maxWidth: .infinity)
            .background(backgroundColor)
            .cornerRadius(12)
            .overlay(
                RoundedRectangle(cornerRadius: 12)
                    .stroke(borderColor, lineWidth: 2)
            )
        }
        .disabled(!enabled)
        .scaleEffect(isSelected ? 1.02 : 1.0)
        .animation(.spring(response: 0.3), value: isSelected)
    }
    
    private var backgroundColor: Color {
        if isCorrect == true {
            return Color.green.opacity(0.3)
        } else if isWrong {
            return Color.red.opacity(0.3)
        } else if isSelected {
            return Color.blue.opacity(0.3)
        } else {
            return Color.black.opacity(0.4)
        }
    }
    
    private var borderColor: Color {
        if isCorrect == true {
            return Color.green
        } else if isWrong {
            return Color.red
        } else if isSelected {
            return Color.blue
        } else {
            return Color.black.opacity(0.4)
        }
    }
}


struct AdaptationNotificationCard: View {
    let adaptationInfo: DifficultyManager.AdaptiveConfig  // Changed from AdaptationResult
    let onDismiss: () -> Void
    
    var body: some View {
        HStack {
            Image(systemName: "function")
                .foregroundColor(.white)
                .font(.system(size: 20))
            
            VStack(alignment: .leading) {
                Text("Math Challenge Adapted!")
                    .font(.system(size: 12, weight: .bold))
                    .foregroundColor(.white)
                
                Text(adaptationInfo.recommendedAdjustment)  // Changed from adjustmentReason
                    .font(.system(size: 10))
                    .foregroundColor(.white.opacity(0.9))
            }
            
            Spacer()
            
            Button(action: onDismiss) {
                Image(systemName: "xmark")
                    .foregroundColor(.white)
                    .font(.system(size: 16))
            }
        }
        .padding(12)
        .background(Color(red: 0.3, green: 0.69, blue: 0.95).opacity(0.9))
        .cornerRadius(8)
        .padding(.horizontal)
    }
}
