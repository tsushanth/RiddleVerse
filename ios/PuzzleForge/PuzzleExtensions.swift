//
//  PuzzleNavigationType.swift
//  PuzzleForge
//
//  Created by Sushanth Tiruvaipati on 6/12/25.
//


//
//  PuzzleExtensions.swift
//  PuzzleForge
//
//  Extensions to support Average puzzle type and improve puzzle handling
//

import Foundation

// MARK: - Navigation Types
enum PuzzleNavigationType {
    case qa
    case multipleChoice
    case average
    case division
    case estimation
    case percentage
    case discounts
    case purchasing
    case conversion
    case anagram
    case antonym
    case synonym
    case swipeWord
    case tipBubble
    case subtraction
    case crossword
    case colorshapematching
    case imagevortex
    case mathComparison
    case numberSequence
    case numbersum
    case symbolSwipe
    case uniqueObject
    case wordSearch
    case memoryRetention
    case memorySequencing
    case memoryStory
    case memorySquares
    case wordPrefix
    case memoryPreviousPair
    case memoryPreviousSingle
    case pinballDeflector
    case contextswitch
    case mathCrossword
    case dualTask
    case colorTextMatching
    case geographyCities
    case geographyCountries
    case wordSnake
    case findObject
    case crypto
    case imagePuzzle
    case musicTrack
    case whichIsReal
    case progressiveReveal
    case mathExpression
    case triangleDotMemory
    case imageQuestion
    case symmetry
    case oddoneout
    case flowpuzzle
    case sentencetransitions
    case letterSet
    case waldoPuzzle
    case none
}

// MARK: - Average Puzzle Data Model
struct AveragePuzzleData: Codable {
    let numbers: [Int]
    let average: Double
    let difficulty: String
    let hint: String
}

struct DivisionPuzzleData {
    let dividend: Int
    let divisor: Int
    let correctAnswer: Int
    let allProblems: [[Int]]
    let difficulty: String
    let hint: String
}

struct PercentagePuzzleData: Codable {
    let percentage: Int
    let baseNumber: Int
    let correctAnswer: Int
    let difficulty: String
    let hint: String
}

struct DiscountPuzzleData: Codable {
    let originalPrice: Double
    let discountPercentage: Int
    let finalPrice: Double
    let discountAmount: Double
    let difficulty: String
    let hint: String
}

struct PurchasingPuzzleData: Codable {
    let payment: Double
    let frequency: String
    let purpose: String
    let yearlyTotal: Double
    let difficulty: String
    let hint: String
}

struct ConversionPuzzleData: Codable {
    let value1: Int
    let unit1: String
    let value2: Int
    let unit2: String
    let comparison: String
    let difficulty: String
    let hint: String
}



// MARK: - Puzzle Extensions
extension Puzzle {
    
    var conversionPuzzleData: ConversionPuzzleData? {
        guard let data = question.data(using: .utf8) else {
            print("🔴 CONVERSION: Failed to convert question to data")
            return nil
        }
        
        do {
            let json = try JSONSerialization.jsonObject(with: data) as? [String: Any]
            print("🔍 CONVERSION: Parsed JSON keys: \(json?.keys.joined(separator: ", ") ?? "none")")
            
            guard let json = json,
                  let value1 = json["value1"] as? Int,
                  let unit1 = json["unit1"] as? String,
                  let unit2 = json["unit2"] as? String,
                  let comparison = json["comparison"] as? String,
                  let difficulty = json["difficulty"] as? String else {
                print("🔴 CONVERSION: Missing required fields in JSON")
                print("🔴 CONVERSION: Raw question: \(question)")
                return nil
            }
            
            // Handle value2 which might be Int or Double
            let value2: Int
            if let intValue = json["value2"] as? Int {
                value2 = intValue
            } else if let doubleValue = json["value2"] as? Double {
                value2 = Int(doubleValue.rounded())
            } else {
                print("🔴 CONVERSION: Invalid value2 type")
                return nil
            }
            
            let hint = json["hint"] as? String ?? ""
            
            let result = ConversionPuzzleData(
                value1: value1,
                unit1: unit1,
                value2: value2,
                unit2: unit2,
                comparison: comparison,
                difficulty: difficulty,
                hint: hint
            )
            
            print("✅ CONVERSION: Successfully parsed conversion data")
            print("✅ CONVERSION: \(value1) \(unit1) vs \(value2) \(unit2) (\(comparison))")
            
            return result
        } catch {
            print("🔴 CONVERSION: JSON parsing error: \(error)")
            print("🔴 CONVERSION: Raw question: \(question)")
            return nil
        }
    }
    
    
    /// Check if this puzzle is an average type
    var isAveragePuzzle: Bool {
        return puzzleType == "average"
    }
    
    var discountPuzzleData: DiscountPuzzleData? {
        guard puzzleType == "discounts",
              let questionData = question.data(using: .utf8),
              let json = try? JSONSerialization.jsonObject(with: questionData) as? [String: Any],
              let originalPrice = json["originalPrice"] as? Double,
              let discountPercentage = json["discountPercentage"] as? Int,
              let finalPrice = json["finalPrice"] as? Double,
              let discountAmount = json["discountAmount"] as? Double,
              let difficulty = json["difficulty"] as? String,
              let hint = json["hint"] as? String else {
            return nil
        }
        
        return DiscountPuzzleData(
            originalPrice: originalPrice,
            discountPercentage: discountPercentage,
            finalPrice: finalPrice,
            discountAmount: discountAmount,
            difficulty: difficulty,
            hint: hint
        )
    }
        
        /// Computed property to get parsed estimation puzzle data
    var estimationPuzzleData: EstimationPuzzleData? {
            print("🔵 ESTIMATION: Parsing estimation data from question")
            
            guard let questionData = question.data(using: .utf8) else {
                print("🔴 ESTIMATION: Failed to create data from question string")
                return nil
            }
            
            do {
                if let questionJson = try JSONSerialization.jsonObject(with: questionData) as? [String: Any] {
                    print("🟢 ESTIMATION: Successfully parsed question as JSON object")
                    
                    // Extract values array
                    var values: [Double] = []
                    if let valuesArray = questionJson["values"] as? [Double] {
                        values = valuesArray
                        print("🟢 ESTIMATION: Found values array: \(values)")
                    } else if let dataPoints = questionJson["dataPoints"] as? [[String: Any]] {
                        // Extract values from dataPoints if that's the format
                        values = dataPoints.compactMap { $0["value"] as? Double }
                        print("🟢 ESTIMATION: Extracted values from dataPoints: \(values)")
                    } else {
                        print("🔴 ESTIMATION: No values or dataPoints found in JSON")
                        print("🔴 ESTIMATION: Available keys: \(Array(questionJson.keys))")
                        return nil
                    }
                    
                    guard !values.isEmpty else {
                        print("🔴 ESTIMATION: Values array is empty")
                        return nil
                    }
                    
                    // Get correct sum
                    let correctSum: Double
                    if let sum = questionJson["correctSum"] as? Double {
                        correctSum = sum
                        print("🟢 ESTIMATION: Found correctSum in JSON: \(correctSum)")
                    } else if let answerDouble = Double(answer) {
                        correctSum = answerDouble
                        print("🟢 ESTIMATION: Using answer as correctSum: \(correctSum)")
                    } else {
                        print("🔴 ESTIMATION: No correct sum found. Answer string: '\(answer)'")
                        return nil
                    }
                    
                    let tolerance = questionJson["tolerance"] as? Double ?? max(correctSum * 0.1, 1.0)
                    let hint = questionJson["hint"] as? String ?? "Add up all the values"
                    let chartTitle = questionJson["chartTitle"] as? String ?? "Estimation Challenge"
                    let difficulty = questionJson["difficulty"] as? String ?? "Medium"
                    
                    // Convert values to ChartDataPoint format that EstimationPuzzleView expects
                    let dataPoints = values.enumerated().map { index, value in
                        ChartDataPoint(
                            value: value,
                            yPosition: Float(0.2 + (Double(index) * 0.15))
                        )
                    }
                    
                    print("🟢 ESTIMATION: Created \(dataPoints.count) ChartDataPoints")
                    print("🟢 ESTIMATION: Values: \(values)")
                    print("🟢 ESTIMATION: Correct sum: \(correctSum)")
                    print("🟢 ESTIMATION: Tolerance: \(tolerance)")
                    
                    return EstimationPuzzleData(
                        dataPoints: dataPoints,
                        correctSum: correctSum,
                        tolerance: tolerance,
                        difficulty: difficulty,
                        hint: hint
                    )
                } else {
                    print("🔴 ESTIMATION: Failed to parse as JSON object")
                }
            } catch {
                print("🔴 ESTIMATION: JSON parsing error: \(error)")
            }
            
            return nil
        }
    
        var isPurchasingPuzzle: Bool {
            return puzzleType == "purchasing"
        }
        
        /// Check if this puzzle is a division type
        var isDivisionPuzzle: Bool {
            return puzzleType == "division"
        }
        
        /// Check if this puzzle is an estimation type
        var isEstimationPuzzle: Bool {
            return puzzleType == "estimation"
        }
        
        var isPercentagePuzzle: Bool {
            return puzzleType == "percentage"
        }
    
        var isDiscountPuzzle: Bool {
            return puzzleType == "discounts"
        }
    
        var isConversionPuzzle: Bool {
            return puzzleType == "conversion"
        }
    
        /// Get display question for special puzzle types
        var displayQuestion: String {
            if isAveragePuzzle, let data = averagePuzzleData {
                return "Find the average of: \(data.numbers.map(String.init).joined(separator: ", "))"
            } else if isDivisionPuzzle, let data = divisionPuzzleData {
                return "\(data.dividend) ÷ \(data.divisor) = ?"
            } else if isEstimationPuzzle, let data = estimationPuzzleData {
                return "Estimate the sum of the chart values"
            } else if isPercentagePuzzle, let data = percentagePuzzleData {
                return data.hint
            } else if isDiscountPuzzle, let data = discountPuzzleData {
                return data.hint
            } else if isPurchasingPuzzle, let data = purchasingPuzzleData {
                return "Calculate yearly cost of \(data.purpose)"
            } else if isConversionPuzzle, let data = conversionPuzzleData {
                return "Compare \(data.value1) \(data.unit1) and \(data.value2) \(data.unit2)"
            }
            return question
        }
        
        /// Get hint text for special puzzle types
    var hintText: String {
        if isAveragePuzzle, let data = averagePuzzleData {
            return data.hint
        } else if isDivisionPuzzle, let data = divisionPuzzleData {
            return data.hint
        } else if isEstimationPuzzle, let data = estimationPuzzleData {
            return data.hint
        } else if isPercentagePuzzle, let data = percentagePuzzleData {
            return data.hint
        } else if isDiscountPuzzle, let data = discountPuzzleData {
            return data.hint
        } else if isPurchasingPuzzle, let data = purchasingPuzzleData {
            return data.hint
        }
        return hint
    }
}

// MARK: - Navigation Helper
class PuzzleNavigationHelper {
    static func determineNavigationType(for puzzle: Puzzle) -> PuzzleNavigationType {
        switch puzzle.puzzleType {
        case "average":
            return .average
        default:
            return puzzle.options.isEmpty ? .qa : .multipleChoice
        }
    }
    
    static func determineNavigationType(for puzzleType: String, options: [String]) -> PuzzleNavigationType {
        switch puzzleType {
        case "average":
            return .average
        case "division":
            return .division
        case "estimation":
            return .estimation
        case "percentage":
            return .percentage
        case "discounts":
            return .discounts
        case "purchasing":
            return .purchasing
        case "conversion":
            return .conversion
        case "anagram":
            return .anagram
        case "antonyms", "antonym":
            return .antonym
        case "synonyms", "synonym":
            return .synonym
        case "colorshapematching":
            return .colorshapematching
        case "imagevortex":
            return .imagevortex
        case "wordsearch":
            return .wordSearch
        case "contextswitch", "context_switch":
            return .contextswitch
        case "wordsnake", "word_snake":
            return .wordSnake
        case "mathcrossword":
            return .mathCrossword
        case "imagepuzzle":
            return .imagePuzzle
        case "sentencetransitions":
            return .sentencetransitions 
        default:
            return options.isEmpty ? .qa : .multipleChoice
        }
    }
    
    static func shouldShowTimer(for puzzleType: String) -> Bool {
        switch puzzleType {
        case "average", "math", "division", "estimation", "percentage", "discounts", "purchasing", "conversion":
            return true
        default:
            return false
        }
    }
    
    static func getDefaultTimeLimit(for puzzleType: String) -> Int {
        switch puzzleType {
        case "average":
            return 60 // 1 minute for average puzzles
        case "division":
            return 90 // 1.5 minutes for division puzzles
        case "estimation":
            return 75 // 1.25 minutes for estimation puzzles
        case "percentage":
            return 60 // 1 minute for percentage puzzles
        case "math":
            return 45
        case "trivia":
            return 30
        case "purchasing":
            return 75 // 1.25 minutes for purchasing puzzles
        case "conversion":
            return 60
        default:
            return 30
        }
    }
}

struct EstimationPuzzleData: Codable {
    let dataPoints: [ChartDataPoint]
    let correctSum: Double
    let tolerance: Double
    let difficulty: String
    let hint: String
    
    enum CodingKeys: String, CodingKey {
        case dataPoints, correctSum, tolerance, difficulty, hint
    }
    
    init(dataPoints: [ChartDataPoint], correctSum: Double, tolerance: Double, difficulty: String, hint: String) {
        self.dataPoints = dataPoints
        self.correctSum = correctSum
        self.tolerance = tolerance
        self.difficulty = difficulty
        self.hint = hint
    }
    
    init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        correctSum = try container.decode(Double.self, forKey: .correctSum)
        tolerance = try container.decode(Double.self, forKey: .tolerance)
        difficulty = try container.decode(String.self, forKey: .difficulty)
        hint = try container.decode(String.self, forKey: .hint)
        
        // Decode dataPoints array
        let dataPointsData = try container.decode([[String: Double]].self, forKey: .dataPoints)
        dataPoints = dataPointsData.compactMap { dict in
            guard let value = dict["value"], let yPosition = dict["yPosition"] else { return nil }
            return ChartDataPoint(value: value, yPosition: Float(yPosition))
        }
    }
    
    func encode(to encoder: Encoder) throws {
        var container = encoder.container(keyedBy: CodingKeys.self)
        try container.encode(correctSum, forKey: .correctSum)
        try container.encode(tolerance, forKey: .tolerance)
        try container.encode(difficulty, forKey: .difficulty)
        try container.encode(hint, forKey: .hint)
        
        // Encode dataPoints as array of dictionaries
        let dataPointsData = dataPoints.map { point in
            ["value": point.value, "yPosition": Double(point.yPosition)]
        }
        try container.encode(dataPointsData, forKey: .dataPoints)
    }
}


// MARK: - Scoring Helper
class PuzzleScoring {
    static func calculateScore(for puzzleType: String, 
                             timeRemaining: Int, 
                             totalTime: Int, 
                             difficulty: String) -> Int {
        let baseScore: Int
        
        switch puzzleType {
            case "average":
                baseScore = 15
            case "division":
                baseScore = 20
            case "estimation":
                baseScore = 18
            case "percentage":
                baseScore = 16
            case "math":
                baseScore = 12
            case "trivia":
                baseScore = 10
            case "anagram":
                baseScore = 8
            default:
                baseScore = 10
        }
        
        let difficultyMultiplier: Double
        switch difficulty.lowercased() {
        case "easy":
            difficultyMultiplier = 1.0
        case "medium":
            difficultyMultiplier = 1.5
        case "hard":
            difficultyMultiplier = 2.0
        default:
            difficultyMultiplier = 1.0
        }
        
        // Time bonus calculation
        let timeBonus = Double(timeRemaining) / Double(totalTime) * 0.5 + 0.5 // 0.5 to 1.0 multiplier
        
        return Int(Double(baseScore) * difficultyMultiplier * timeBonus)
    }
}

// MARK: - Timer Helper
class PuzzleTimer: ObservableObject {
    @Published var timeRemaining: Int
    @Published var isRunning: Bool = false
    @Published var formattedTime: String = "0:00"
    
    private var timer: Timer?
    let totalTime: Int
    
    init(totalTime: Int) {
        self.totalTime = totalTime
        self.timeRemaining = totalTime
        updateFormattedTime()
    }
    
    func start() {
        isRunning = true
        timer = Timer.scheduledTimer(withTimeInterval: 1.0, repeats: true) { _ in
            if self.timeRemaining > 0 {
                self.timeRemaining -= 1
                self.updateFormattedTime()
            } else {
                self.stop()
            }
        }
    }
    
    func stop() {
        isRunning = false
        timer?.invalidate()
        timer = nil
    }
    
    func reset() {
        stop()
        timeRemaining = totalTime
        updateFormattedTime()
    }
    
    private func updateFormattedTime() {
        let minutes = timeRemaining / 60
        let seconds = timeRemaining % 60
        formattedTime = String(format: "%d:%02d", minutes, seconds)
    }
    
    deinit {
        timer?.invalidate()
    }
}
