//
//  MathComparisonPuzzleView.swift
//  PuzzleForge
//
//  iOS implementation of Math Comparison puzzle - FIXED VERSION with consistent multiplication symbol
//

import SwiftUI
import Foundation

// MARK: - Math Comparison Data Structures
struct MathComparisonData: Codable {
    let sequence: [ComparisonPair]
    let totalPairs: Int
    let difficulty: String
    let timeLimit: Int
    let scoring: ScoringConfig
    let instructions: String
    let metadata: PuzzleMetadata
    
    struct ComparisonPair: Codable {
        let pairNumber: Int
        let leftValue: String
        let rightValue: String
        let leftNumeric: Double
        let rightNumeric: Double
        let correctAnswer: String // "left", "right", or "equal"
        let operationType: String
        let difficulty: Double
    }
    
    struct ScoringConfig: Codable {
        let pointsPerCorrect: Int
        let timeBonus: Bool
        let streakMultiplier: Double
    }
    
    struct PuzzleMetadata: Codable {
        let generatedAt: String
        let difficulty: String
        let sequenceLength: Int
        let operationTypes: [String: Int]
    }
}

struct MathComparisonAnswer: Codable {
    let correctAnswers: [String]
    let scoring: MathComparisonData.ScoringConfig
    let maxScore: Int
}

// MARK: - Math Comparison Generator Extension
extension LocalMemoryPuzzleGenerator {
    
    static func generateMathComparison(difficulty: String) -> (String, String) {
        print("🔢 Generating math comparison puzzle with difficulty: \(difficulty)")
        
        do {
            // Get difficulty configuration
            let config = getMathComparisonDifficultyConfig(for: difficulty)
            
            // Generate puzzle sequence
            let puzzleSequence = generateMathComparisonSequence(config: config)
            
            // Create question data
            let questionData = createMathComparisonQuestionData(
                sequence: puzzleSequence,
                config: config,
                difficulty: difficulty
            )
            
            // Create answer data
            let answerData = createMathComparisonAnswerData(
                sequence: puzzleSequence,
                config: config
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
            
            print("✅ Successfully generated math comparison puzzle")
            print("📊 Total pairs: \(puzzleSequence.count)")
            print("⏱️ Time limit: \(config.timeLimit)ms")
            
            return (questionString, answerString)
            
        } catch {
            print("❌ Failed to generate math comparison puzzle: \(error)")
            return generateFallbackMathComparison()
        }
    }
    
    // MARK: - Difficulty Configuration
    private static func getMathComparisonDifficultyConfig(for difficulty: String) -> MathComparisonConfig {
        switch difficulty.lowercased() {
        case "easy":
            return MathComparisonConfig(
                sequenceLength: 8,
                numberRange: (1, 20),
                operations: ["addition", "subtraction", "simple_multiplication"],
                allowDecimals: false,
                allowNegatives: false,
                timeLimit: 60000, // 60 seconds
                scoring: MathComparisonData.ScoringConfig(
                    pointsPerCorrect: 100,
                    timeBonus: true,
                    streakMultiplier: 1.1
                )
            )
        case "medium":
            return MathComparisonConfig(
                sequenceLength: 12,
                numberRange: (1, 50),
                operations: ["addition", "subtraction", "multiplication", "division", "mixed"],
                allowDecimals: true,
                allowNegatives: false,
                timeLimit: 90000, // 90 seconds
                scoring: MathComparisonData.ScoringConfig(
                    pointsPerCorrect: 150,
                    timeBonus: true,
                    streakMultiplier: 1.2
                )
            )
        case "hard":
            return MathComparisonConfig(
                sequenceLength: 15,
                numberRange: (1, 100),
                operations: ["multiplication", "division", "mixed", "powers", "fractions"],
                allowDecimals: true,
                allowNegatives: true,
                timeLimit: 120000, // 120 seconds
                scoring: MathComparisonData.ScoringConfig(
                    pointsPerCorrect: 200,
                    timeBonus: true,
                    streakMultiplier: 1.3
                )
            )
        case "expert":
            return MathComparisonConfig(
                sequenceLength: 20,
                numberRange: (1, 200),
                operations: ["multiplication", "division", "mixed", "powers", "fractions"],
                allowDecimals: true,
                allowNegatives: true,
                timeLimit: 150000, // 150 seconds
                scoring: MathComparisonData.ScoringConfig(
                    pointsPerCorrect: 250,
                    timeBonus: true,
                    streakMultiplier: 1.4
                )
            )
        default:
            return getMathComparisonDifficultyConfig(for: "medium")
        }
    }
    
    // MARK: - Sequence Generation (FIXED)
    private static func generateMathComparisonSequence(config: MathComparisonConfig) -> [MathComparisonData.ComparisonPair] {
        var sequence: [MathComparisonData.ComparisonPair] = []
        var usedExpressions: Set<String> = []
        
        for i in 0..<config.sequenceLength {
            let progressRatio = Double(i) / Double(config.sequenceLength)
            let currentConfig = getProgressiveMathConfig(baseConfig: config, progressRatio: progressRatio)
            
            var attempts = 0
            var pair: MathComparisonData.ComparisonPair
            
            repeat {
                pair = generateMathComparisonPair(config: currentConfig, pairNumber: i + 1)
                attempts += 1
                
                // Ensure expressions are unique
                let expressionKey = "\(pair.leftValue)|\(pair.rightValue)"
                let reverseKey = "\(pair.rightValue)|\(pair.leftValue)"
                
                if !usedExpressions.contains(expressionKey) && !usedExpressions.contains(reverseKey) {
                    usedExpressions.insert(expressionKey)
                    break
                }
                
                if attempts > 20 {
                    print("⚠️ Max attempts reached for unique expressions, using current pair")
                    break
                }
            } while attempts < 20
            
            sequence.append(pair)
        }
        
        return sequence
    }
    
    private static func getProgressiveMathConfig(baseConfig: MathComparisonConfig, progressRatio: Double) -> MathComparisonConfig {
        // Increase number range as we progress
        let rangeDiff = Double(baseConfig.numberRange.1 - baseConfig.numberRange.0)
        let newMax = Int(Double(baseConfig.numberRange.0) + rangeDiff * (0.3 + progressRatio * 0.7))
        
        return MathComparisonConfig(
            sequenceLength: baseConfig.sequenceLength,
            numberRange: (baseConfig.numberRange.0, newMax),
            operations: baseConfig.operations,
            allowDecimals: baseConfig.allowDecimals,
            allowNegatives: baseConfig.allowNegatives,
            timeLimit: baseConfig.timeLimit,
            scoring: baseConfig.scoring,
            complexity: progressRatio
        )
    }
    
    // MARK: - Pair Generation (FIXED)
    private static func generateMathComparisonPair(config: MathComparisonConfig, pairNumber: Int) -> MathComparisonData.ComparisonPair {
        // Generate two DIFFERENT expressions
        let leftOperationType = selectMathOperation(operations: config.operations, complexity: config.complexity)
        let rightOperationType = selectMathOperation(operations: config.operations, complexity: config.complexity)
        
        let leftValue = generateMathValue(operationType: leftOperationType, config: config)
        var rightValue: String
        var attempts = 0
        
        // Ensure right expression is different from left
        repeat {
            rightValue = generateMathValue(operationType: rightOperationType, config: config)
            attempts += 1
        } while leftValue == rightValue && attempts < 10
        
        // Calculate actual numeric values
        let leftNumeric = evaluateMathExpression(leftValue)
        let rightNumeric = evaluateMathExpression(rightValue)
        
        // Determine correct answer
        let correctAnswer: String
        let tolerance = 0.001 // For floating point comparison
        
        if abs(leftNumeric - rightNumeric) < tolerance {
            correctAnswer = "equal"
        } else if leftNumeric > rightNumeric {
            correctAnswer = "left"
        } else {
            correctAnswer = "right"
        }
        
        let pairDifficulty = calculateMathPairDifficulty(
            leftValue: leftValue,
            rightValue: rightValue,
            operationType: "\(leftOperationType),\(rightOperationType)"
        )
        
        return MathComparisonData.ComparisonPair(
            pairNumber: pairNumber,
            leftValue: leftValue,
            rightValue: rightValue,
            leftNumeric: leftNumeric,
            rightNumeric: rightNumeric,
            correctAnswer: correctAnswer,
            operationType: "\(leftOperationType),\(rightOperationType)",
            difficulty: pairDifficulty
        )
    }
    
    // MARK: - Operation Selection and Generation (IMPROVED)
    private static func selectMathOperation(operations: [String], complexity: Double) -> String {
        let operationWeights: [String: Double] = [
            "addition": 0.1,
            "subtraction": 0.2,
            "simple_multiplication": 0.3,
            "multiplication": 0.5,
            "division": 0.7,
            "mixed": 0.8,
            "powers": 0.9,
            "fractions": 0.95
        ]
        
        // Filter operations appropriate for current complexity
        let suitableOps = operations.filter { op in
            guard let weight = operationWeights[op] else { return true }
            return weight <= (complexity + 0.3)
        }
        
        if suitableOps.isEmpty {
            return operations.first ?? "addition"
        }
        
        return suitableOps.randomElement() ?? "addition"
    }
    
    private static func generateMathValue(operationType: String, config: MathComparisonConfig) -> String {
        switch operationType {
        case "addition":
            return generateMathAddition(range: config.numberRange, allowDecimals: config.allowDecimals)
        case "subtraction":
            return generateMathSubtraction(range: config.numberRange, allowDecimals: config.allowDecimals)
        case "simple_multiplication":
            return generateSimpleMathMultiplication()
        case "multiplication":
            return generateMathMultiplication(range: config.numberRange, allowDecimals: config.allowDecimals)
        case "division":
            return generateMathDivision(range: config.numberRange, allowDecimals: config.allowDecimals)
        case "mixed":
            return generateMixedMathExpression(range: config.numberRange, allowDecimals: config.allowDecimals)
        case "powers":
            return generatePowerMathExpression(range: config.numberRange)
        case "fractions":
            return generateFractionMathExpression(range: config.numberRange)
        default:
            return generateSimpleMathNumber(range: config.numberRange, allowDecimals: config.allowDecimals)
        }
    }
    
    // MARK: - Specific Math Generators (IMPROVED - CONSISTENT MULTIPLICATION SYMBOL)
    private static func generateMathAddition(range: (Int, Int), allowDecimals: Bool) -> String {
        let a = randomMathNumber(range: range, allowDecimals: allowDecimals)
        let b = randomMathNumber(range: range, allowDecimals: allowDecimals)
        
        // Add variation in presentation
        if Bool.random() {
            return "\(formatMathNumber(a)) + \(formatMathNumber(b))"
        } else {
            return "(\(formatMathNumber(a)) + \(formatMathNumber(b)))"
        }
    }
    
    private static func generateMathSubtraction(range: (Int, Int), allowDecimals: Bool) -> String {
        let a = randomMathNumber(range: range, allowDecimals: allowDecimals)
        let maxB = min(a, Double(range.1))
        let b = randomMathNumber(range: (range.0, Int(maxB)), allowDecimals: allowDecimals)
        
        // Add variation in presentation
        if Bool.random() {
            return "\(formatMathNumber(a)) - \(formatMathNumber(b))"
        } else {
            return "(\(formatMathNumber(a)) - \(formatMathNumber(b)))"
        }
    }
    
    private static func generateSimpleMathMultiplication() -> String {
        let a = Int.random(in: 2...9)
        let b = Int.random(in: 2...9)
        
        // Always use × for multiplication - CONSISTENT
        if Bool.random() {
            return "\(a) × \(b)"
        } else {
            return "(\(a) × \(b))"
        }
    }
    
    private static func generateMathMultiplication(range: (Int, Int), allowDecimals: Bool) -> String {
        let maxVal = min(range.1, 15)
        let a = randomMathNumber(range: (range.0, maxVal), allowDecimals: allowDecimals)
        let b = randomMathNumber(range: (range.0, maxVal), allowDecimals: allowDecimals)
        
        // Always use × for multiplication - CONSISTENT
        return "\(formatMathNumber(a)) × \(formatMathNumber(b))"
    }
    
    private static func generateMathDivision(range: (Int, Int), allowDecimals: Bool) -> String {
        let b = Int.random(in: 2...min(range.1, 10))
        let result = randomMathNumber(range: (1, range.1), allowDecimals: allowDecimals)
        let a = Double(b) * result
        
        let symbols = ["÷", "/"]
        let symbol = symbols.randomElement() ?? "÷"
        
        return "\(formatMathNumber(a)) \(symbol) \(b)"
    }
    
    private static func generateMixedMathExpression(range: (Int, Int), allowDecimals: Bool) -> String {
        let operations = ["+", "-", "×"] // Use × consistently for multiplication
        let op1 = operations.randomElement() ?? "+"
        
        let maxVal = min(range.1, 12)
        let a = randomMathNumber(range: (range.0, maxVal), allowDecimals: allowDecimals)
        let b = randomMathNumber(range: (range.0, maxVal), allowDecimals: allowDecimals)
        let c = randomMathNumber(range: (range.0, maxVal), allowDecimals: allowDecimals)
        
        // Create varied mixed expressions - all using × for multiplication
        let expressions = [
            "\(formatMathNumber(a)) \(op1) \(formatMathNumber(b))",
            "(\(formatMathNumber(a)) \(op1) \(formatMathNumber(b))) + \(formatMathNumber(c))",
            "\(formatMathNumber(a)) + (\(formatMathNumber(b)) × \(formatMathNumber(c)))",
            "\(formatMathNumber(a)) × \(formatMathNumber(b)) + \(formatMathNumber(c))"
        ]
        
        return expressions.randomElement() ?? expressions[0]
    }
    
    private static func generatePowerMathExpression(range: (Int, Int)) -> String {
        let base = Int.random(in: 2...min(range.1, 8))
        let exponent = Int.random(in: 2...4)
        
        if exponent == 2 {
            return Bool.random() ? "\(base)^2" : "\(base)²"
        } else if exponent == 3 {
            return Bool.random() ? "\(base)^3" : "\(base)³"
        } else {
            return "\(base)^\(exponent)"
        }
    }
    
    private static func generateFractionMathExpression(range: (Int, Int)) -> String {
        let numerator = Int.random(in: 1...range.1)
        let denominator = Int.random(in: 2...10)
        
        let formats = ["\(numerator)/\(denominator)", "(\(numerator)/\(denominator))"]
        return formats.randomElement() ?? "\(numerator)/\(denominator)"
    }
    
    private static func generateSimpleMathNumber(range: (Int, Int), allowDecimals: Bool) -> String {
        let number = randomMathNumber(range: range, allowDecimals: allowDecimals)
        return formatMathNumber(number)
    }
    
    // MARK: - Helper Methods
    private static func randomMathNumber(range: (Int, Int), allowDecimals: Bool) -> Double {
        let min = Double(range.0)
        let max = Double(range.1)
        
        if allowDecimals && Double.random(in: 0...1) < 0.3 { // 30% chance for decimals
            let randomValue = Double.random(in: min...max)
            return (randomValue * 10).rounded() / 10 // Round to 1 decimal place
        } else {
            return Double(Int.random(in: range.0...range.1))
        }
    }
    
    private static func formatMathNumber(_ number: Double) -> String {
        if number == floor(number) {
            return String(Int(number))
        } else {
            return String(format: "%.1f", number)
        }
    }
    
    private static func evaluateMathExpression(_ expression: String) -> Double {
        // Convert mathematical symbols to evaluatable format - handle × specifically
        var jsExpression = expression
            .replacingOccurrences(of: "×", with: "*")  // Convert × to * for evaluation
            .replacingOccurrences(of: "÷", with: "/")
            .replacingOccurrences(of: "·", with: "*")  // Convert · to * for evaluation (if any remain)
            .replacingOccurrences(of: "^", with: "**")
            .replacingOccurrences(of: "²", with: "**2")
            .replacingOccurrences(of: "³", with: "**3")
        
        // Handle fractions
        let fractionRegex = try! NSRegularExpression(pattern: "(\\d+)/(\\d+)", options: [])
        let range = NSRange(location: 0, length: jsExpression.utf16.count)
        jsExpression = fractionRegex.stringByReplacingMatches(
            in: jsExpression,
            options: [],
            range: range,
            withTemplate: "($1.0/$2.0)"
        )
        
        // Use NSExpression for safe evaluation
        do {
            let expression = NSExpression(format: jsExpression)
            if let result = expression.expressionValue(with: nil, context: nil) as? NSNumber {
                return result.doubleValue
            }
        } catch {
            print("❌ Error evaluating expression \"\(expression)\": \(error)")
        }
        
        return 0.0
    }
    
    private static func calculateMathPairDifficulty(leftValue: String, rightValue: String, operationType: String) -> Double {
        var difficulty = 1.0
        
        // Base difficulty by operation type
        let operationDifficulty: [String: Double] = [
            "addition": 1.0,
            "subtraction": 1.2,
            "simple_multiplication": 1.5,
            "multiplication": 2.0,
            "division": 2.5,
            "mixed": 3.0,
            "powers": 3.5,
            "fractions": 4.0
        ]
        
        // Get highest difficulty from operations
        for op in operationType.split(separator: ",") {
            if let opDiff = operationDifficulty[String(op)] {
                difficulty = max(difficulty, opDiff)
            }
        }
        
        // Increase difficulty for longer expressions
        let avgLength = Double(leftValue.count + rightValue.count) / 2.0
        difficulty *= (1.0 + avgLength * 0.05)
        
        return (difficulty * 10).rounded() / 10 // Round to 1 decimal
    }
    
    // MARK: - Data Creation
    private static func createMathComparisonQuestionData(
        sequence: [MathComparisonData.ComparisonPair],
        config: MathComparisonConfig,
        difficulty: String
    ) -> MathComparisonData {
        let operationTypes = getOperationTypes(sequence: sequence)
        
        return MathComparisonData(
            sequence: sequence,
            totalPairs: sequence.count,
            difficulty: difficulty,
            timeLimit: config.timeLimit,
            scoring: config.scoring,
            instructions: "Compare the values and select which is greater, or select EQUAL if they are the same.",
            metadata: MathComparisonData.PuzzleMetadata(
                generatedAt: ISO8601DateFormatter().string(from: Date()),
                difficulty: difficulty,
                sequenceLength: sequence.count,
                operationTypes: operationTypes
            )
        )
    }
    
    private static func createMathComparisonAnswerData(
        sequence: [MathComparisonData.ComparisonPair],
        config: MathComparisonConfig
    ) -> MathComparisonAnswer {
        let correctAnswers = sequence.map { $0.correctAnswer }
        let maxScore = sequence.count * config.scoring.pointsPerCorrect
        
        return MathComparisonAnswer(
            correctAnswers: correctAnswers,
            scoring: config.scoring,
            maxScore: maxScore
        )
    }
    
    private static func getOperationTypes(sequence: [MathComparisonData.ComparisonPair]) -> [String: Int] {
        var types: [String: Int] = [:]
        for pair in sequence {
            for op in pair.operationType.split(separator: ",") {
                types[String(op), default: 0] += 1
            }
        }
        return types
    }
    
    // MARK: - Fallback (IMPROVED)
    private static func generateFallbackMathComparison() -> (String, String) {
        print("🔄 Generating fallback math comparison puzzle")
        
        let fallbackSequence = [
            MathComparisonData.ComparisonPair(
                pairNumber: 1,
                leftValue: "5 + 3",
                rightValue: "2 × 4",  // Use × consistently
                leftNumeric: 8.0,
                rightNumeric: 8.0,
                correctAnswer: "equal",
                operationType: "addition,multiplication",
                difficulty: 1.0
            ),
            MathComparisonData.ComparisonPair(
                pairNumber: 2,
                leftValue: "15 - 7",
                rightValue: "3 × 3",  // Use × consistently
                leftNumeric: 8.0,
                rightNumeric: 9.0,
                correctAnswer: "right",
                operationType: "subtraction,multiplication",
                difficulty: 1.2
            ),
            MathComparisonData.ComparisonPair(
                pairNumber: 3,
                leftValue: "4²",
                rightValue: "20 - 4",
                leftNumeric: 16.0,
                rightNumeric: 16.0,
                correctAnswer: "equal",
                operationType: "powers,subtraction",
                difficulty: 1.5
            )
        ]
        
        let questionData = MathComparisonData(
            sequence: fallbackSequence,
            totalPairs: 3,
            difficulty: "easy",
            timeLimit: 60000,
            scoring: MathComparisonData.ScoringConfig(
                pointsPerCorrect: 100,
                timeBonus: true,
                streakMultiplier: 1.1
            ),
            instructions: "Compare the values and select which is greater, or select EQUAL if they are the same.",
            metadata: MathComparisonData.PuzzleMetadata(
                generatedAt: ISO8601DateFormatter().string(from: Date()),
                difficulty: "easy",
                sequenceLength: 3,
                operationTypes: ["addition": 1, "subtraction": 2, "multiplication": 2, "powers": 1]
            )
        )
        
        let answerData = MathComparisonAnswer(
            correctAnswers: ["equal", "right", "equal"],
            scoring: questionData.scoring,
            maxScore: 300
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

// MARK: - Supporting Configuration Structure
private struct MathComparisonConfig {
    let sequenceLength: Int
    let numberRange: (Int, Int)
    let operations: [String]
    let allowDecimals: Bool
    let allowNegatives: Bool
    let timeLimit: Int
    let scoring: MathComparisonData.ScoringConfig
    var complexity: Double = 0.0
}

// MARK: - View Models
struct MathComparisonPair {
    let pairNumber: Int
    let leftValue: String
    let rightValue: String
    let leftNumeric: Double
    let rightNumeric: Double
    let correctAnswer: String // "left", "right", "equal"
    let operationType: String
    let difficulty: Double
}

struct MathComparisonPuzzleData {
    let pairs: [MathComparisonPair]
    let difficulty: String
    let timeLimit: Int
}

// MARK: - Main View (FIXED)
struct MathComparisonPuzzleView: View {
    let puzzle: Puzzle
    let questionIndex: Int
    let totalQuestions: Int
    let onAnswerSubmitted: (String, Bool) -> Void
    let onNextPuzzle: () -> Void
    let onExit: () -> Void
    
    @State private var currentPairIndex = 0
    @State private var selectedAnswer: String? = nil
    @State private var showResult = false
    @State private var score = 0
    @State private var livesRemaining = 3
    @State private var isGameComplete = false
    @State private var timeRemaining = 60
    @State private var parsedData: [MathComparisonPair] = []
    @State private var timer: Timer?
    @State private var isViewActive = true
    
    var currentPair: MathComparisonPair? {
        guard currentPairIndex < parsedData.count else { return nil }
        return parsedData[currentPairIndex]
    }
    
    var body: some View {
        GeometryReader { _ in
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
                    headerSection
                    
                    // Lives indicator
                    livesSection
                    
                    // Progress
                    progressSection
                    
                    // Question
                    questionSection
                    
                    if let currentPair = currentPair {
                        // Value comparison cards
                        comparisonSection(for: currentPair)
                        
                        // Equal button
                        equalButtonSection
                        
                        // Result feedback
                        if showResult {
                            resultFeedbackSection
                        }
                    } else {
                        // Loading state
                        loadingSection
                    }
                    
                    Spacer()
                }
                .padding(16)
            }
        }
        .navigationBarHidden(true)
        .onAppear {
            if isViewActive {
                setupPuzzle()
            }
        }
        .onDisappear {
            cleanup()
        }
    }
    
    // MARK: - Setup and Lifecycle
    private func setupPuzzle() {
        guard isViewActive else { return }
        parseData()
        startTimer()
    }
    
    private func cleanup() {
        isViewActive = false
        timer?.invalidate()
        timer = nil
        isGameComplete = true
    }
    
    // MARK: - Header Section
    private var headerSection: some View {
        HStack {
            // Pause button
            Button(action: {
                cleanup()
                onExit()
            }) {
                Text("❚❚")
                    .font(.system(size: 16, weight: .medium))
                    .foregroundColor(.white)
                    .frame(width: 48, height: 48)
                    .background(Color.black.opacity(0.3))
                    .cornerRadius(8)
            }
            
            Spacer()
            
            HStack(spacing: 16) {
                // Timer
                VStack(alignment: .center, spacing: 2) {
                    Text("TIME")
                        .font(.system(size: 10))
                        .foregroundColor(.gray)
                    Text(formatTime(timeRemaining))
                        .font(.system(size: 14, weight: .bold))
                        .foregroundColor(.white)
                }
                .padding(.horizontal, 12)
                .padding(.vertical, 8)
                .background(Color.black.opacity(0.3))
                .cornerRadius(8)
                
                // Score
                VStack(alignment: .center, spacing: 2) {
                    Text("SCORE")
                        .font(.system(size: 10))
                        .foregroundColor(.gray)
                    Text("\(score)")
                        .font(.system(size: 14, weight: .bold))
                        .foregroundColor(.white)
                }
                .padding(.horizontal, 12)
                .padding(.vertical, 8)
                .background(Color.black.opacity(0.3))
                .cornerRadius(8)
            }
        }
    }
    
    // MARK: - Lives Section
    private var livesSection: some View {
        HStack(spacing: 4) {
            ForEach(0..<5, id: \.self) { index in
                Circle()
                    .fill(index < livesRemaining ? Color.green : Color.gray)
                    .frame(width: 16, height: 16)
                    .overlay(
                        Circle()
                            .stroke(Color.white, lineWidth: 2)
                    )
            }
        }
    }
    
    // MARK: - Progress Section
    private var progressSection: some View {
        VStack(spacing: 8) {
            Text("Question \(currentPairIndex + 1) of \(parsedData.count)")
                .font(.system(size: 14))
                .foregroundColor(.white.opacity(0.8))
            
            // Progress bar
            ZStack(alignment: .leading) {
                Rectangle()
                    .fill(Color.black.opacity(0.3))
                    .frame(height: 4)
                    .cornerRadius(2)
                
                Rectangle()
                    .fill(Color.green)
                    .frame(width: progressWidth, height: 4)
                    .cornerRadius(2)
                    .animation(.easeInOut(duration: 0.3), value: currentPairIndex)
            }
        }
    }
    
    private var progressWidth: CGFloat {
        guard !parsedData.isEmpty else { return 0 }
        let progress = CGFloat(currentPairIndex + 1) / CGFloat(parsedData.count)
        return UIScreen.main.bounds.width * 0.9 * progress // Approximate full width
    }
    
    // MARK: - Question Section
    private var questionSection: some View {
        Text("Which value is greater?")
            .font(.system(size: 24, weight: .medium))
            .foregroundColor(.white)
            .multilineTextAlignment(.center)
    }
    
    // MARK: - Loading Section
    private var loadingSection: some View {
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
    
    // MARK: - Comparison Section
    private func comparisonSection(for pair: MathComparisonPair) -> some View {
        VStack(spacing: 16) {
            // Left value
            ComparisonValueCard(
                value: pair.leftValue,
                isSelected: selectedAnswer == "left",
                isCorrect: showResult ? pair.correctAnswer == "left" : nil,
                isWrong: showResult && selectedAnswer == "left" && pair.correctAnswer != "left",
                enabled: !showResult,
                onClick: { handleAnswer("left") }
            )
            
            // Right value
            ComparisonValueCard(
                value: pair.rightValue,
                isSelected: selectedAnswer == "right",
                isCorrect: showResult ? pair.correctAnswer == "right" : nil,
                isWrong: showResult && selectedAnswer == "right" && pair.correctAnswer != "right",
                enabled: !showResult,
                onClick: { handleAnswer("right") }
            )
        }
    }
    
    // MARK: - Equal Button Section
    private var equalButtonSection: some View {
        Button(action: { handleAnswer("equal") }) {
            HStack {
                Text("EQUAL")
                    .font(.system(size: 18, weight: .bold))
                    .foregroundColor(.white)
                
                if showResult && selectedAnswer == "equal" {
                    if currentPair?.correctAnswer == "equal" {
                        Text("✓")
                            .font(.system(size: 20, weight: .bold))
                            .foregroundColor(.white)
                    } else {
                        Text("✗")
                            .font(.system(size: 20, weight: .bold))
                            .foregroundColor(.white)
                    }
                }
            }
            .frame(maxWidth: .infinity)
            .frame(height: 56)
            .background(equalButtonColor)
            .cornerRadius(12)
        }
        .disabled(showResult)
    }
    
    private var equalButtonColor: Color {
        if showResult && selectedAnswer == "equal" {
            return currentPair?.correctAnswer == "equal" ? Color.green : Color.red
        }
        return Color(red: 0.0, green: 0.737, blue: 0.831) // Cyan
    }
    
    // MARK: - Result Feedback Section
    private var resultFeedbackSection: some View {
        VStack {
            let isCorrect = selectedAnswer == currentPair?.correctAnswer
            
            VStack(spacing: 8) {
                Text(isCorrect ? "✅ Correct! +100 points" : "❌ Wrong answer. Lives: \(livesRemaining)")
                    .font(.system(size: 16, weight: .medium))
                    .foregroundColor(.white)
                
                if !isCorrect, let pair = currentPair {
                    Text("\(pair.leftValue) = \(formatDisplayNumber(pair.leftNumeric)), \(pair.rightValue) = \(formatDisplayNumber(pair.rightNumeric))")
                        .font(.system(size: 14))
                        .foregroundColor(.white.opacity(0.8))
                }
            }
            .padding(16)
            .background(
                RoundedRectangle(cornerRadius: 8)
                    .fill(isCorrect ? Color.green.opacity(0.2) : Color.red.opacity(0.2))
            )
        }
        .transition(.opacity.combined(with: .move(edge: .bottom)))
        .animation(.easeInOut(duration: 0.3), value: showResult)
    }
    
    // MARK: - Helper Methods
    private func parseData() {
        guard let data = puzzle.question.data(using: .utf8) else {
            print("❌ Failed to convert question to data")
            generateFallbackData()
            return
        }
        
        do {
            let decoder = JSONDecoder()
            let puzzleJSON = try decoder.decode(MathComparisonData.self, from: data)
            
            parsedData = puzzleJSON.sequence.map { pair in
                MathComparisonPair(
                    pairNumber: pair.pairNumber,
                    leftValue: pair.leftValue,
                    rightValue: pair.rightValue,
                    leftNumeric: pair.leftNumeric,
                    rightNumeric: pair.rightNumeric,
                    correctAnswer: pair.correctAnswer,
                    operationType: pair.operationType,
                    difficulty: pair.difficulty
                )
            }
            
            print("✅ Parsed \(parsedData.count) comparison pairs")
            
        } catch {
            print("❌ Failed to parse puzzle JSON: \(error)")
            generateFallbackData()
        }
    }
    
    private func generateFallbackData() {
        print("🔄 Using fallback math comparison data")
        
        parsedData = [
            MathComparisonPair(
                pairNumber: 1,
                leftValue: "5 + 3",
                rightValue: "2 × 4",  // Use × consistently
                leftNumeric: 8.0,
                rightNumeric: 8.0,
                correctAnswer: "equal",
                operationType: "addition,multiplication",
                difficulty: 1.0
            ),
            MathComparisonPair(
                pairNumber: 2,
                leftValue: "15 - 7",
                rightValue: "3 × 3",  // Use × consistently
                leftNumeric: 8.0,
                rightNumeric: 9.0,
                correctAnswer: "right",
                operationType: "subtraction,multiplication",
                difficulty: 1.2
            )
        ]
    }
    
    private func handleAnswer(_ answer: String) {
        guard !showResult && isViewActive, let currentPair = currentPair else { return }
        
        selectedAnswer = answer
        showResult = true
        
        let isCorrect = answer == currentPair.correctAnswer
        
        if isCorrect {
            score += 100
        } else {
            livesRemaining = max(0, livesRemaining - 1)
        }
        
        onAnswerSubmitted(answer, isCorrect)
        
        // Auto-advance after showing feedback
        DispatchQueue.main.asyncAfter(deadline: .now() + 2.0) {
            guard isViewActive else { return }
            
            if currentPairIndex >= parsedData.count - 1 || livesRemaining <= 0 {
                cleanup()
                onNextPuzzle()
            } else {
                currentPairIndex += 1
                selectedAnswer = nil
                showResult = false
            }
        }
    }
    
    private func startTimer() {
        guard isViewActive else { return }
        
        timer?.invalidate()
        timer = Timer.scheduledTimer(withTimeInterval: 1.0, repeats: true) { _ in
            if isViewActive && !isGameComplete {
                if timeRemaining > 0 {
                    timeRemaining -= 1
                } else {
                    cleanup()
                    onAnswerSubmitted("timeout", false)
                    onNextPuzzle()
                }
            }
        }
    }
    
    private func formatTime(_ seconds: Int) -> String {
        let minutes = seconds / 60
        let remainingSeconds = seconds % 60
        return String(format: "%d:%02d", minutes, remainingSeconds)
    }
    
    private func formatDisplayNumber(_ number: Double) -> String {
        if number == floor(number) {
            return String(Int(number))
        } else {
            return String(format: "%.1f", number)
        }
    }
}

// MARK: - Comparison Value Card (UNCHANGED)
struct ComparisonValueCard: View {
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
                    .foregroundColor(Color(red: 0.13, green: 0.59, blue: 0.95)) // Blue
                    .multilineTextAlignment(.leading)
                
                Spacer()
                
                if isCorrect == true {
                    Text("✓")
                        .font(.system(size: 28, weight: .bold))
                        .foregroundColor(.green)
                } else if isWrong {
                    Text("✗")
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
