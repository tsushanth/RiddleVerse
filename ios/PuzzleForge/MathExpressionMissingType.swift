//
//  MathExpressionModels.swift
//  PuzzleForge
//
//  Created for Math Expression Puzzle
//

import Foundation
import SwiftUI
import Combine

extension LocalMemoryPuzzleGenerator {
    
    // MARK: - Math Expression Generator
    static func generateMathExpression(difficulty: String) -> (String, String) {
        let config = getMathExpressionConfig(for: difficulty)
        
        let questionData: [String: Any] = [
            "puzzleType": "mathExpression",
            "difficulty": difficulty,
            "totalChallenges": config.targetExpressions,
            "timeLimit": config.timeLimit,
            "instructions": [
                "title": "Math Expression Puzzle",
                "description": "Solve mathematical expressions by filling in missing elements",
                "steps": [
                    "Look at each falling math expression",
                    "Identify the missing element (number or operator)",
                    "Tap the drop zone to select it",
                    "Use the keyboard to enter your answer",
                    "Press ENTER to submit"
                ],
                "tip": "Work quickly! Expressions fall faster as you progress. Focus on accuracy to build your streak!"
            ]
        ]
        
        let answerData: [String: Any] = [
            "puzzleType": "mathExpression",
            "scorePerCorrect": config.scorePerCorrect,
            "maxScore": config.targetExpressions * config.scorePerCorrect,
            "expressionComplexity": config.expressionComplexity,
            "operationTypes": config.operationTypes,
            "numberRange": [
                "min": config.numberRange.lowerBound,
                "max": config.numberRange.upperBound
            ],
            "fallSpeed": config.fallSpeed
        ]
        
        guard let questionJSON = try? JSONSerialization.data(withJSONObject: questionData),
              let answerJSON = try? JSONSerialization.data(withJSONObject: answerData),
              let questionString = String(data: questionJSON, encoding: .utf8),
              let answerString = String(data: answerJSON, encoding: .utf8) else {
            return ("", "")
        }
        
        print("🧮 Generated Math Expression:")
        print("  - Difficulty: \(difficulty)")
        print("  - Total challenges: \(config.targetExpressions)")
        print("  - Time limit: \(config.timeLimit) seconds")
        print("  - Operations: \(config.operationTypes.joined(separator: ", "))")
        print("  - Number range: \(config.numberRange)")
        print("  - Fall speed: \(config.fallSpeed)")
        
        return (questionString, answerString)
    }
    
    // MARK: - Math Expression Configuration
    private static func getMathExpressionConfig(for difficulty: String) -> MathExpressionConfig {
        switch difficulty.lowercased() {
        case "beginner":
            return MathExpressionConfig(
                targetExpressions: 5,
                expressionComplexity: 1,
                operationTypes: ["+", "-"],
                numberRange: 1...10,
                fallSpeed: 2.0,
                timeLimit: 180, // 3 minutes
                scorePerCorrect: 10
            )
            
        case "easy":
            return MathExpressionConfig(
                targetExpressions: 8,
                expressionComplexity: 2,
                operationTypes: ["+", "-", "×"],
                numberRange: 1...15,
                fallSpeed: 2.5,
                timeLimit: 150, // 2.5 minutes
                scorePerCorrect: 15
            )
            
        case "medium":
            return MathExpressionConfig(
                targetExpressions: 10,
                expressionComplexity: 3,
                operationTypes: ["+", "-", "×"],
                numberRange: 1...25,
                fallSpeed: 3.0,
                timeLimit: 150, // 2.5 minutes
                scorePerCorrect: 20
            )
            
        case "hard":
            return MathExpressionConfig(
                targetExpressions: 12,
                expressionComplexity: 4,
                operationTypes: ["+", "-", "×", "÷"],
                numberRange: 1...50,
                fallSpeed: 3.5,
                timeLimit: 120, // 2 minutes
                scorePerCorrect: 25
            )
            
        case "expert":
            return MathExpressionConfig(
                targetExpressions: 15,
                expressionComplexity: 5,
                operationTypes: ["+", "-", "×", "÷"],
                numberRange: 1...100,
                fallSpeed: 4.0,
                timeLimit: 120, // 2 minutes
                scorePerCorrect: 30
            )
            
        default:
            // Default to medium
            return MathExpressionConfig(
                targetExpressions: 10,
                expressionComplexity: 3,
                operationTypes: ["+", "-", "×"],
                numberRange: 1...25,
                fallSpeed: 3.0,
                timeLimit: 150,
                scorePerCorrect: 20
            )
        }
    }
    
    // MARK: - Configuration Model
    private struct MathExpressionConfig {
        let targetExpressions: Int
        let expressionComplexity: Int
        let operationTypes: [String]
        let numberRange: ClosedRange<Int>
        let fallSpeed: Double
        let timeLimit: Int
        let scorePerCorrect: Int
    }
}
// MARK: - Missing Type Enum
enum MathExpressionMissingType: CaseIterable {
    case leftNumber
    case operator_
    case rightNumber
    case result
    
    var description: String {
        switch self {
        case .leftNumber: return "Left Number"
        case .operator_: return "Operator"
        case .rightNumber: return "Right Number"
        case .result: return "Result"
        }
    }
}

// MARK: - Game State Enum
enum MathExpressionGameState {
    case playing
    case won
    case timeUp
    case lost
}

// MARK: - Math Expression Model
struct MathExpression: Identifiable {
    let id: Int
    let leftNumber: Int
    let operator_: String
    let rightNumber: Int
    let result: Int
    let missingType: MathExpressionMissingType
    let missingValue: String
    var yPosition: CGFloat
    var isCompleted: Bool
    var isCorrect: Bool
    var droppedValue: String?
    
    init(
        id: Int,
        leftNumber: Int,
        operator_: String,
        rightNumber: Int,
        result: Int,
        missingType: MathExpressionMissingType,
        missingValue: String,
        yPosition: CGFloat = 0,
        isCompleted: Bool = false,
        isCorrect: Bool = false,
        droppedValue: String? = nil
    ) {
        self.id = id
        self.leftNumber = leftNumber
        self.operator_ = operator_
        self.rightNumber = rightNumber
        self.result = result
        self.missingType = missingType
        self.missingValue = missingValue
        self.yPosition = yPosition
        self.isCompleted = isCompleted
        self.isCorrect = isCorrect
        self.droppedValue = droppedValue
    }
}

// MARK: - Configuration Model
struct MathExpressionConfig {
    let targetExpressions: Int
    let expressionComplexity: Int
    let operationTypes: [String]
    let numberRange: ClosedRange<Int>
    let fallSpeed: CGFloat
    let name: String
    
    static func generate(for difficulty: String) -> MathExpressionConfig {
        switch difficulty.lowercased() {
        case "beginner", "easy":
            return MathExpressionConfig(
                targetExpressions: 5,
                expressionComplexity: 1,
                operationTypes: ["+", "-"],
                numberRange: 1...10,
                fallSpeed: 2.0,
                name: "Beginner"
            )
        case "medium":
            return MathExpressionConfig(
                targetExpressions: 10,
                expressionComplexity: 3,
                operationTypes: ["+", "-", "×"],
                numberRange: 1...25,
                fallSpeed: 3.0,
                name: "Medium"
            )
        case "hard":
            return MathExpressionConfig(
                targetExpressions: 12,
                expressionComplexity: 4,
                operationTypes: ["+", "-", "×", "÷"],
                numberRange: 1...50,
                fallSpeed: 3.5,
                name: "Hard"
            )
        case "expert":
            return MathExpressionConfig(
                targetExpressions: 15,
                expressionComplexity: 5,
                operationTypes: ["+", "-", "×", "÷"],
                numberRange: 1...100,
                fallSpeed: 4.0,
                name: "Expert"
            )
        default:
            return MathExpressionConfig(
                targetExpressions: 8,
                expressionComplexity: 2,
                operationTypes: ["+", "-", "×"],
                numberRange: 1...15,
                fallSpeed: 2.5,
                name: "Easy"
            )
        }
    }
}

// MARK: - Color Theme
struct MathExpressionColors {
    static let background = Color(hex: "1A2F2A") ?? Color.black
    static let expressionBg = Color(hex: "2D5D4F") ?? Color.gray
    static let numberTile = Color(hex: "4A7C6E") ?? Color.blue
    static let operatorTile = Color(hex: "FF6B35") ?? Color.orange
    static let missingSlot = Color(hex: "1E4A3A") ?? Color.gray
    static let dragTileNumber = Color(hex: "2196F3") ?? Color.blue
    static let dragTileOperator = Color(hex: "FF9800") ?? Color.orange
    static let correctFeedback = Color(hex: "4CAF50") ?? Color.green
    static let incorrectFeedback = Color(hex: "F44336") ?? Color.red
    static let selectedHighlight = Color(hex: "FFEB3B") ?? Color.yellow
}

// MARK: - Expression Generator
class MathExpressionGenerator {
    static func generateExpressions(
        config: MathExpressionConfig,
        count: Int,
        startId: Int = 1,
        baseY: CGFloat = 1000
    ) -> [MathExpression] {
        var expressions: [MathExpression] = []
        
        for index in 0..<count {
            let expression = generateSingleExpression(config: config, id: startId + index)
            var updatedExpression = expression
            updatedExpression.yPosition = baseY + (300 * CGFloat(index))
            expressions.append(updatedExpression)
        }
        
        return expressions
    }
    
    static func generateSingleExpression(config: MathExpressionConfig, id: Int) -> MathExpression {
        let operator_ = config.operationTypes.randomElement() ?? "+"
        var leftNumber: Int
        var rightNumber: Int
        var result: Int
        
        repeat {
            switch operator_ {
            case "+":
                leftNumber = Int.random(in: config.numberRange)
                rightNumber = Int.random(in: config.numberRange)
                result = leftNumber + rightNumber
                
            case "-":
                leftNumber = Int.random(in: (config.numberRange.lowerBound + 5)...config.numberRange.upperBound)
                rightNumber = Int.random(in: config.numberRange.lowerBound...leftNumber)
                result = leftNumber - rightNumber
                
            case "×":
                let maxLeft = min(config.numberRange.upperBound, 25)
                let maxRight = min(config.numberRange.upperBound, 15)
                leftNumber = Int.random(in: config.numberRange.lowerBound...maxLeft)
                rightNumber = Int.random(in: config.numberRange.lowerBound...maxRight)
                result = leftNumber * rightNumber
                
            case "÷":
                result = Int.random(in: max(config.numberRange.lowerBound, 2)...config.numberRange.upperBound)
                rightNumber = Int.random(in: max(config.numberRange.lowerBound, 2)...20)
                leftNumber = result * rightNumber
                
            default:
                leftNumber = 2
                rightNumber = 2
                result = 4
            }
        } while result == 1 || result > config.numberRange.upperBound * 2
        
        let missingType = MathExpressionMissingType.allCases.randomElement() ?? .result
        
        let missingValue: String
        switch missingType {
        case .leftNumber:
            missingValue = "\(leftNumber)"
        case .operator_:
            missingValue = operator_
        case .rightNumber:
            missingValue = "\(rightNumber)"
        case .result:
            missingValue = "\(result)"
        }
        
        return MathExpression(
            id: id,
            leftNumber: leftNumber,
            operator_: operator_,
            rightNumber: rightNumber,
            result: result,
            missingType: missingType,
            missingValue: missingValue
        )
    }
}

// MARK: - Math Expression View
struct MathExpressionView: View {
    let expression: MathExpression
    let isSelected: Bool
    let currentInputValue: String
    let onDropZoneClick: () -> Void
    
    var backgroundColor: Color {
        if expression.isCompleted && expression.isCorrect {
            return MathExpressionColors.correctFeedback
        } else if expression.isCompleted && !expression.isCorrect {
            return MathExpressionColors.incorrectFeedback
        } else {
            return MathExpressionColors.expressionBg
        }
    }
    
    var body: some View {
        HStack(spacing: 12) {
            // Left number
            if expression.missingType == .leftNumber {
                DropZoneView(
                    isEmpty: expression.droppedValue == nil,
                    droppedValue: expression.droppedValue,
                    isSelected: isSelected,
                    currentInputValue: currentInputValue,
                    onClick: onDropZoneClick
                )
            } else {
                MathExprNumberTile(text: "\(expression.leftNumber)")
            }
            
            // Operator
            if expression.missingType == .operator_ {
                DropZoneView(
                    isEmpty: expression.droppedValue == nil,
                    droppedValue: expression.droppedValue,
                    isSelected: isSelected,
                    currentInputValue: currentInputValue,
                    onClick: onDropZoneClick
                )
            } else {
                OperatorTile(operator_: expression.operator_)
            }
            
            // Right number
            if expression.missingType == .rightNumber {
                DropZoneView(
                    isEmpty: expression.droppedValue == nil,
                    droppedValue: expression.droppedValue,
                    isSelected: isSelected,
                    currentInputValue: currentInputValue,
                    onClick: onDropZoneClick
                )
            } else {
                MathExprNumberTile(text: "\(expression.rightNumber)")
            }
            
            // Equals sign
            Text("=")
                .font(.title)
                .fontWeight(.bold)
                .foregroundColor(.white)
            
            // Result
            if expression.missingType == .result {
                DropZoneView(
                    isEmpty: expression.droppedValue == nil,
                    droppedValue: expression.droppedValue,
                    isSelected: isSelected,
                    currentInputValue: currentInputValue,
                    onClick: onDropZoneClick
                )
            } else {
                MathExprNumberTile(text: "\(expression.result)")
            }
        }
        .padding(16)
        .background(backgroundColor)
        .cornerRadius(12)
        .padding(.horizontal)
    }
}

// MARK: - Drop Zone View
struct DropZoneView: View {
    let isEmpty: Bool
    let droppedValue: String?
    let isSelected: Bool
    let currentInputValue: String
    let onClick: () -> Void
    
    var body: some View {
        Button(action: onClick) {
            dropZoneContent
        }
        .buttonStyle(PlainButtonStyle())
    }
    
    private var dropZoneContent: some View {
        ZStack {
            dropZoneBackground
            dropZoneText
        }
    }
    
    private var dropZoneBackground: some View {
        RoundedRectangle(cornerRadius: 8)
            .fill(backgroundColor)
            .frame(width: contentWidth, height: 48)
            .overlay(
                RoundedRectangle(cornerRadius: 8)
                    .stroke(borderColor, lineWidth: isSelected ? 3 : 2)
            )
    }
    
    @ViewBuilder
    private var dropZoneText: some View {
        if let value = droppedValue {
            displayText(value, color: .white)
        } else if isSelected && !currentInputValue.isEmpty {
            displayText(currentInputValue, color: .black)
        } else {
            displayText("?", color: isSelected ? .black : .white.opacity(0.5))
        }
    }
    
    private func displayText(_ text: String, color: Color) -> some View {
        Text(text)
            .font(text.count > 2 ? .body : .title3)
            .fontWeight(.bold)
            .foregroundColor(color)
    }
    
    private var contentWidth: CGFloat {
        let displayText = droppedValue ?? (isSelected ? currentInputValue : "")
        if displayText.count > 1 {
            return CGFloat(displayText.count * 20 + 40)
        }
        return 48
    }
    
    private var backgroundColor: Color {
        if isSelected {
            return MathExpressionColors.selectedHighlight
        } else if !isEmpty {
            return MathExpressionColors.numberTile
        } else {
            return MathExpressionColors.missingSlot
        }
    }
    
    private var borderColor: Color {
        isSelected ? Color.blue : Color.white.opacity(0.5)
    }
}

// MARK: - Number Tile
struct MathExprNumberTile: View {
    let text: String
    
    var body: some View {
        ZStack {
            RoundedRectangle(cornerRadius: 8)
                .fill(MathExpressionColors.numberTile)
                .frame(width: 48, height: 48)
            
            Text(text)
                .font(.title3)
                .fontWeight(.bold)
                .foregroundColor(.white)
        }
    }
}

// MARK: - Operator Tile
struct OperatorTile: View {
    let operator_: String
    
    var body: some View {
        ZStack {
            Circle()
                .fill(MathExpressionColors.operatorTile)
                .frame(width: 48, height: 48)
            
            Text(operator_)
                .font(.title2)
                .fontWeight(.bold)
                .foregroundColor(.white)
        }
    }
}

// MARK: - Type-erased Shape
struct AnyShape: Shape {
    private let _path: (CGRect) -> Path
    
    init<S: Shape>(_ shape: S) {
        _path = { rect in
            shape.path(in: rect)
        }
    }
    
    func path(in rect: CGRect) -> Path {
        _path(rect)
    }
}

// MARK: - Custom Keyboard
struct MathExpressionKeyboard: View {
    let selectedExpressionId: Int?
    let currentInputValue: String
    let onItemSelected: (String) -> Void
    let onEnterPressed: () -> Void
    
    var body: some View {
        VStack(spacing: 0) {
            // Instruction text
            VStack(alignment: .leading, spacing: 8) {
                if selectedExpressionId != nil {
                    Text("Tap numbers/operators below, then press ENTER:")
                        .font(.subheadline)
                        .fontWeight(.medium)
                        .foregroundColor(.yellow)
                    
                    if !currentInputValue.isEmpty {
                        HStack {
                            Spacer()
                            Text("Your answer: \(currentInputValue)")
                                .font(.headline)
                                .fontWeight(.bold)
                                .foregroundColor(.black)
                                .padding(12)
                                .background(Color.yellow.opacity(0.9))
                                .cornerRadius(8)
                            Spacer()
                        }
                    }
                } else {
                    Text("Tap a missing square (?) in an equation above:")
                        .font(.subheadline)
                        .foregroundColor(.white.opacity(0.7))
                }
            }
            .padding()
            .frame(maxWidth: .infinity, alignment: .leading)
            
            // Keyboard buttons
            VStack(spacing: 8) {
                // Row 1: 2-5 and +, -
                HStack(spacing: 8) {
                    ForEach(["2", "3", "4", "5"], id: \.self) { number in
                        MathExprKeyboardButton(
                            text: number,
                            isEnabled: selectedExpressionId != nil,
                            isNumber: true,
                            onClick: { onItemSelected(number) }
                        )
                    }
                    
                    Spacer().frame(width: 16)
                    
                    ForEach(["+", "-"], id: \.self) { operator_ in
                        MathExprKeyboardButton(
                            text: operator_,
                            isEnabled: selectedExpressionId != nil,
                            isNumber: false,
                            onClick: { onItemSelected(operator_) }
                        )
                    }
                }
                
                // Row 2: 6-9 and ×, ÷
                HStack(spacing: 8) {
                    ForEach(["6", "7", "8", "9"], id: \.self) { number in
                        MathExprKeyboardButton(
                            text: number,
                            isEnabled: selectedExpressionId != nil,
                            isNumber: true,
                            onClick: { onItemSelected(number) }
                        )
                    }
                    
                    Spacer().frame(width: 16)
                    
                    ForEach(["×", "÷"], id: \.self) { operator_ in
                        MathExprKeyboardButton(
                            text: operator_,
                            isEnabled: selectedExpressionId != nil,
                            isNumber: false,
                            onClick: { onItemSelected(operator_) }
                        )
                    }
                }
                
                // Row 3: Utility buttons
                HStack(spacing: 8) {
                    // Clear button
                    Button(action: { onItemSelected("CLEAR") }) {
                        Text("C")
                            .font(.body)
                            .fontWeight(.bold)
                            .foregroundColor(.white)
                            .frame(width: 56, height: 56)
                            .background(Color.red)
                            .cornerRadius(8)
                    }
                    .disabled(selectedExpressionId == nil || currentInputValue.isEmpty)
                    .opacity((selectedExpressionId == nil || currentInputValue.isEmpty) ? 0.4 : 1.0)
                    
                    // 0 and 1
                    ForEach(["0", "1"], id: \.self) { number in
                        MathExprKeyboardButton(
                            text: number,
                            isEnabled: selectedExpressionId != nil,
                            isNumber: true,
                            onClick: { onItemSelected(number) }
                        )
                    }
                    
                    Spacer().frame(width: 16)
                    
                    // Backspace button
                    Button(action: { onItemSelected("BACKSPACE") }) {
                        Image(systemName: "delete.left.fill")
                            .font(.title3)
                            .foregroundColor(.white)
                            .frame(width: 56, height: 56)
                            .background(Color.orange)
                            .cornerRadius(8)
                    }
                    .disabled(selectedExpressionId == nil || currentInputValue.isEmpty)
                    .opacity((selectedExpressionId == nil || currentInputValue.isEmpty) ? 0.4 : 1.0)
                }
                
                // Row 4: Enter button
                Button(action: onEnterPressed) {
                    Text("ENTER ↵")
                        .font(.headline)
                        .fontWeight(.bold)
                        .foregroundColor(.white)
                        .frame(maxWidth: .infinity)
                        .frame(height: 56)
                        .background(Color.green)
                        .cornerRadius(8)
                }
                .disabled(selectedExpressionId == nil || currentInputValue.isEmpty)
                .opacity((selectedExpressionId == nil || currentInputValue.isEmpty) ? 0.4 : 1.0)
            }
            .padding()
        }
        .background(Color(hex: "1E4A3A") ?? Color.gray)
    }
}

// MARK: - Keyboard Button
struct MathExprKeyboardButton: View {
    let text: String
    let isEnabled: Bool
    let isNumber: Bool
    let onClick: () -> Void
    
    var body: some View {
        Button(action: onClick) {
            buttonContent
        }
        .disabled(!isEnabled)
    }
    
    private var buttonContent: some View {
        Text(text)
            .font(.title3)
            .fontWeight(.bold)
            .foregroundColor(textColor)
            .frame(width: 56, height: 56)
            .background(backgroundColor)
            .clipShape(buttonShape)
    }
    
    private var textColor: Color {
        isEnabled ? .white : .white.opacity(0.5)
    }
    
    private var backgroundColor: Color {
        let baseColor = isNumber ? MathExpressionColors.dragTileNumber : MathExpressionColors.dragTileOperator
        return isEnabled ? baseColor : baseColor.opacity(0.4)
    }
    
    private var buttonShape: AnyShape {
        if isNumber {
            AnyShape(RoundedRectangle(cornerRadius: 8))
        } else {
            AnyShape(Circle())
        }
    }
}

// MARK: - Game Over Overlay
struct MathExpressionGameOverOverlay: View {
    let gameState: MathExpressionGameState
    let totalScore: Int
    let correctAnswers: Int
    let totalAttempts: Int
    let targetExpressions: Int
    let onTryAgain: () -> Void
    let onContinue: () -> Void
    
    var body: some View {
        ZStack {
            backgroundOverlay
            contentCard
        }
    }
    
    private var backgroundOverlay: some View {
        Color.black.opacity(0.8)
            .ignoresSafeArea()
    }
    
    private var contentCard: some View {
        VStack(spacing: 24) {
            titleSection
            statsSection
            buttonSection
        }
        .padding(32)
        .background(cardBackground)
        .padding(32)
    }
    
    private var titleSection: some View {
        Text(titleText)
            .font(.largeTitle)
            .fontWeight(.bold)
            .foregroundColor(titleColor)
    }
    
    private var statsSection: some View {
        VStack(spacing: 12) {
            scoreText
            solvedText
            accuracyText
        }
    }
    
    private var scoreText: some View {
        Text("Score: \(totalScore)")
            .font(.title2)
            .fontWeight(.bold)
            .foregroundColor(.green)
    }
    
    private var solvedText: some View {
        Text("\(correctAnswers)/\(targetExpressions) expressions solved")
            .font(.headline)
            .foregroundColor(.white)
    }
    
    @ViewBuilder
    private var accuracyText: some View {
        if totalAttempts > 0 {
            let accuracy = calculateAccuracy()
            Text("Accuracy: \(accuracy)%")
                .font(.subheadline)
                .foregroundColor(.gray)
        }
    }
    
    private var buttonSection: some View {
        HStack(spacing: 16) {
            tryAgainButton
            continueButton
        }
    }
    
    private var tryAgainButton: some View {
        Button(action: onTryAgain) {
            Text("Try Again")
                .font(.headline)
                .foregroundColor(.white)
                .frame(maxWidth: .infinity)
                .padding()
                .background(Color.blue)
                .cornerRadius(12)
        }
    }
    
    private var continueButton: some View {
        Button(action: onContinue) {
            Text("Continue")
                .font(.headline)
                .foregroundColor(.white)
                .frame(maxWidth: .infinity)
                .padding()
                .background(Color.green)
                .cornerRadius(12)
        }
    }
    
    private var cardBackground: some View {
        RoundedRectangle(cornerRadius: 20)
            .fill(MathExpressionColors.background)
    }
    
    private func calculateAccuracy() -> Int {
        Int((Double(correctAnswers) / Double(totalAttempts)) * 100)
    }
    
    private var titleText: String {
        switch gameState {
        case .won: return "🎉 Excellent!"
        case .timeUp: return "⏰ Time's Up!"
        case .lost: return "💔 Game Over"
        case .playing: return ""
        }
    }
    
    private var titleColor: Color {
        switch gameState {
        case .won: return .green
        case .timeUp: return .orange
        case .lost: return .red
        case .playing: return .white
        }
    }
}

// MARK: - View Model
class MathExpressionViewModel: ObservableObject {
    @Published var config: MathExpressionConfig
    @Published var timeLeft: Int
    @Published var currentHearts: Int
    @Published var gameState: MathExpressionGameState = .playing
    @Published var isPaused: Bool = false
    @Published var showHint: Bool = false
    @Published var totalScore: Int = 0
    @Published var correctAnswers: Int = 0
    @Published var totalAttempts: Int = 0
    @Published var currentStreak: Int = 0
    @Published var bestStreak: Int = 0
    @Published var expressions: [MathExpression] = []
    @Published var selectedExpressionId: Int?
    @Published var currentInputValue: String = ""
    
    private var nextExpressionId: Int = 1
    private var lastY: CGFloat = 1000
    private var timer: Timer?
    private var animationTimer: Timer?
    private var expressionStartTimes: [Int: Date] = [:]
    private var gameStartTime: Date = Date()
    
    init(difficulty: String) {
        self.config = MathExpressionConfig.generate(for: difficulty)
        self.timeLeft = 150
        self.currentHearts = 3
        setupGame()
    }
    
    func setupGame() {
        generateInitialExpressions()
        startTimers()
    }
    
    private func generateInitialExpressions() {
        let newExpressions = MathExpressionGenerator.generateExpressions(
            config: config,
            count: 3,
            startId: nextExpressionId,
            baseY: lastY
        )
        
        expressions = newExpressions
        nextExpressionId += newExpressions.count
        lastY = newExpressions.map { $0.yPosition }.max() ?? 1000
        
        for expr in newExpressions {
            expressionStartTimes[expr.id] = Date()
        }
    }
    
    private func startTimers() {
        timer = Timer.scheduledTimer(withTimeInterval: 1.0, repeats: true) { [weak self] _ in
            guard let self = self else { return }
            
            if !self.isPaused && self.gameState == .playing {
                if self.timeLeft > 0 {
                    self.timeLeft -= 1
                } else {
                    self.gameState = .timeUp
                    self.stopTimers()
                }
            }
        }
        
        animationTimer = Timer.scheduledTimer(withTimeInterval: 0.05, repeats: true) { [weak self] _ in
            guard let self = self else { return }
            
            if !self.isPaused && self.gameState == .playing {
                self.updateExpressionPositions()
            }
        }
    }
    
    private func stopTimers() {
        timer?.invalidate()
        timer = nil
        animationTimer?.invalidate()
        animationTimer = nil
    }
    
    private func updateExpressionPositions() {
        expressions = expressions.map { expression in
            var updatedExpression = expression
            let newY = expression.yPosition - config.fallSpeed
            updatedExpression.yPosition = newY
            return updatedExpression
        }
        
        expressions = expressions.filter { $0.yPosition > -200 }
        
        if expressions.count < 3 {
            addNewExpression()
        }
    }
    
    private func addNewExpression() {
        let maxY = expressions.map { $0.yPosition }.max() ?? 1000
        let newExpressions = MathExpressionGenerator.generateExpressions(
            config: config,
            count: 1,
            startId: nextExpressionId,
            baseY: maxY + 300
        )
        
        if let newExpr = newExpressions.first {
            expressions.append(newExpr)
            nextExpressionId += 1
            lastY = newExpr.yPosition
            expressionStartTimes[newExpr.id] = Date()
        }
    }
    
    func selectExpression(_ id: Int) {
        if let expression = expressions.first(where: { $0.id == id }), !expression.isCompleted {
            if selectedExpressionId == id {
                selectedExpressionId = nil
                currentInputValue = ""
            } else {
                selectedExpressionId = id
                currentInputValue = ""
            }
        }
    }
    
    func handleAnswerSelection(_ value: String) {
        switch value {
        case "CLEAR":
            currentInputValue = ""
            return
        case "BACKSPACE":
            if !currentInputValue.isEmpty {
                currentInputValue = String(currentInputValue.dropLast())
            }
            return
        default:
            guard let selectedId = selectedExpressionId,
                  let expression = expressions.first(where: { $0.id == selectedId }) else {
                return
            }
            
            let filteredValue: String
            switch expression.missingType {
            case .operator_:
                if ["+", "-", "×", "÷"].contains(value) {
                    filteredValue = value
                } else {
                    filteredValue = currentInputValue
                }
            default:
                if value.allSatisfy({ $0.isNumber }) && currentInputValue.count < 4 {
                    filteredValue = currentInputValue + value
                } else {
                    filteredValue = currentInputValue
                }
            }
            
            currentInputValue = filteredValue
        }
    }
    
    func handleEnterPress() {
        guard let selectedId = selectedExpressionId,
              let index = expressions.firstIndex(where: { $0.id == selectedId }),
              !expressions[index].isCompleted,
              !currentInputValue.isEmpty else {
            return
        }
        
        totalAttempts += 1
        
        let expression = expressions[index]
        let startTime = expressionStartTimes[selectedId] ?? Date()
        let solveTime = Date().timeIntervalSince(startTime)
        
        let isCorrect = currentInputValue == expression.missingValue
        
        expressions[index].droppedValue = currentInputValue
        expressions[index].isCompleted = true
        expressions[index].isCorrect = isCorrect
        
        if isCorrect {
            correctAnswers += 1
            currentStreak += 1
            if currentStreak > bestStreak {
                bestStreak = currentStreak
            }
            
            let score = calculateScore(isCorrect: true, timeSpent: solveTime)
            totalScore += score
            
            if correctAnswers >= config.targetExpressions {
                gameState = .won
                stopTimers()
            }
        } else {
            currentStreak = 0
            currentHearts = max(0, currentHearts - 1)
            
            if currentHearts == 0 {
                gameState = .lost
                stopTimers()
            }
        }
        
        selectedExpressionId = nil
        currentInputValue = ""
        
        DispatchQueue.main.asyncAfter(deadline: .now() + 1.5) { [weak self] in
            guard let self = self else { return }
            self.expressions.removeAll { $0.id == selectedId }
            self.addNewExpression()
        }
    }
    
    private func calculateScore(isCorrect: Bool, timeSpent: TimeInterval) -> Int {
        guard isCorrect else { return 0 }
        
        let baseScore = 100
        let timeBonus = max(0, Int(50 - timeSpent / 2))
        let streakBonus = currentStreak * 10
        
        let difficultyMultiplier: Double
        switch config.name {
        case "Beginner": difficultyMultiplier = 1.0
        case "Easy": difficultyMultiplier = 1.2
        case "Medium": difficultyMultiplier = 1.5
        case "Hard": difficultyMultiplier = 2.0
        case "Expert": difficultyMultiplier = 2.5
        default: difficultyMultiplier = 1.0
        }
        
        let totalScore = Double(baseScore + timeBonus + streakBonus) * difficultyMultiplier
        return Int(totalScore)
    }
    
    func resetGame() {
        timeLeft = 150
        currentHearts = 3
        gameState = .playing
        totalScore = 0
        correctAnswers = 0
        totalAttempts = 0
        currentStreak = 0
        bestStreak = 0
        expressions = []
        selectedExpressionId = nil
        currentInputValue = ""
        nextExpressionId = 1
        lastY = 1000
        expressionStartTimes = [:]
        gameStartTime = Date()
        
        setupGame()
    }
    
    deinit {
        stopTimers()
    }
}

// MARK: - Main Puzzle View
struct MathExpressionPuzzleView: View {
    let initialDifficulty: String
    let onGameComplete: (Bool, Int) -> Void
    let onBack: () -> Void
    
    @StateObject private var viewModel: MathExpressionViewModel
    @Environment(\.presentationMode) var presentationMode
    
    init(
        initialDifficulty: String = "Medium",
        onGameComplete: @escaping (Bool, Int) -> Void,
        onBack: @escaping () -> Void
    ) {
        self.initialDifficulty = initialDifficulty
        self.onGameComplete = onGameComplete
        self.onBack = onBack
        _viewModel = StateObject(wrappedValue: MathExpressionViewModel(difficulty: initialDifficulty))
    }
    
    var body: some View {
        ZStack {
            MathExpressionColors.background
                .ignoresSafeArea()
            
            VStack(spacing: 0) {
                MathExpressionTopBar(
                    timeLeft: viewModel.timeLeft,
                    hearts: viewModel.currentHearts,
                    score: viewModel.totalScore,
                    correctAnswers: viewModel.correctAnswers,
                    targetExpressions: viewModel.config.targetExpressions,
                    isPaused: viewModel.isPaused,
                    onBack: {
                        onBack()
                        presentationMode.wrappedValue.dismiss()
                    },
                    onPause: {
                        viewModel.isPaused.toggle()
                    },
                    onHint: {
                        viewModel.showHint.toggle()
                    }
                )
                
                GeometryReader { geometry in
                    ZStack {
                        ForEach(viewModel.expressions) { expression in
                            if expression.yPosition > -100 {
                                MathExpressionView(
                                    expression: expression,
                                    isSelected: viewModel.selectedExpressionId == expression.id,
                                    currentInputValue: viewModel.selectedExpressionId == expression.id ? viewModel.currentInputValue : "",
                                    onDropZoneClick: {
                                        viewModel.selectExpression(expression.id)
                                    }
                                )
                                .offset(y: expression.yPosition)
                            }
                        }
                    }
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
                }
                
                MathExpressionKeyboard(
                    selectedExpressionId: viewModel.selectedExpressionId,
                    currentInputValue: viewModel.currentInputValue,
                    onItemSelected: { value in
                        viewModel.handleAnswerSelection(value)
                    },
                    onEnterPressed: {
                        viewModel.handleEnterPress()
                    }
                )
            }
            
            if viewModel.gameState != .playing {
                MathExpressionGameOverOverlay(
                    gameState: viewModel.gameState,
                    totalScore: viewModel.totalScore,
                    correctAnswers: viewModel.correctAnswers,
                    totalAttempts: viewModel.totalAttempts,
                    targetExpressions: viewModel.config.targetExpressions,
                    onTryAgain: {
                        viewModel.resetGame()
                    },
                    onContinue: {
                        let isSuccess = viewModel.gameState == .won
                        onGameComplete(isSuccess, viewModel.totalScore)
                        presentationMode.wrappedValue.dismiss()
                    }
                )
            }
        }
        .navigationBarHidden(true)
    }
}

// MARK: - Top Bar
struct MathExpressionTopBar: View {
    let timeLeft: Int
    let hearts: Int
    let score: Int
    let correctAnswers: Int
    let targetExpressions: Int
    let isPaused: Bool
    let onBack: () -> Void
    let onPause: () -> Void
    let onHint: () -> Void
    
    var body: some View {
        VStack(spacing: 12) {
            HStack {
                Button(action: onBack) {
                    Image(systemName: "chevron.left")
                        .font(.title2)
                        .foregroundColor(.white)
                        .padding(8)
                        .background(Color.white.opacity(0.2))
                        .clipShape(Circle())
                }
                
                Spacer()
                
                HStack(spacing: 4) {
                    Image(systemName: "star.fill")
                        .foregroundColor(.yellow)
                    Text("\(score)")
                        .font(.headline)
                        .foregroundColor(.white)
                }
                .padding(.horizontal, 12)
                .padding(.vertical, 6)
                .background(Color.white.opacity(0.2))
                .cornerRadius(20)
                
                Spacer()
                
                Button(action: onPause) {
                    Image(systemName: isPaused ? "play.fill" : "pause.fill")
                        .font(.title2)
                        .foregroundColor(.white)
                        .padding(8)
                        .background(Color.white.opacity(0.2))
                        .clipShape(Circle())
                }
            }
            .padding(.horizontal)
            
            HStack {
                HStack(spacing: 4) {
                    Image(systemName: "clock.fill")
                        .foregroundColor(.white)
                    Text(String(format: "%02d:%02d", timeLeft / 60, timeLeft % 60))
                        .font(.subheadline)
                        .fontWeight(.bold)
                        .foregroundColor(.white)
                }
                .padding(.horizontal, 12)
                .padding(.vertical, 6)
                .background(Color.orange.opacity(0.8))
                .cornerRadius(20)
                
                Spacer()
                
                HStack(spacing: 4) {
                    Image(systemName: "checkmark.circle.fill")
                        .foregroundColor(.green)
                    Text("\(correctAnswers)/\(targetExpressions)")
                        .font(.subheadline)
                        .fontWeight(.bold)
                        .foregroundColor(.white)
                }
                .padding(.horizontal, 12)
                .padding(.vertical, 6)
                .background(Color.white.opacity(0.2))
                .cornerRadius(20)
                
                Spacer()
                
                HStack(spacing: 4) {
                    ForEach(0..<3, id: \.self) { index in
                        Image(systemName: index < hearts ? "heart.fill" : "heart")
                            .foregroundColor(index < hearts ? .red : .gray)
                            .font(.caption)
                    }
                }
                .padding(.horizontal, 12)
                .padding(.vertical, 6)
                .background(Color.white.opacity(0.2))
                .cornerRadius(20)
            }
            .padding(.horizontal)
        }
        .padding(.top, 8)
        .padding(.bottom, 8)
        .background(MathExpressionColors.background.opacity(0.95))
    }
}

// MARK: - Preview
struct MathExpressionPuzzleView_Previews: PreviewProvider {
    static var previews: some View {
        MathExpressionPuzzleView(
            initialDifficulty: "Medium",
            onGameComplete: { _, _ in },
            onBack: {}
        )
    }
}
