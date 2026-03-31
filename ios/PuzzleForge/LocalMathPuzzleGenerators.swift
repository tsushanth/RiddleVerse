//
//  LocalMathPuzzleGenerators.swift
//  PuzzleForge
//
//  Created by Sushanth Tiruvaipati on 8/2/25.
//


import Foundation

struct LocalMathPuzzleGenerators {
    
    // MARK: - Math Tipping Generator
    static func generateMathTipping(difficulty: String) -> (String, String) {
        let (billAmount, tipPercentage) = generateTippingParams(for: difficulty)
        
        // Calculate correct tip amount
        let correctTipAmount = round(billAmount * (tipPercentage / 100.0) * 100) / 100
        
        // Create question JSON matching TipBubblePuzzleView format
        let questionData: [String: Any] = [
            "billAmount": billAmount,
            "tipPercentage": tipPercentage,
            "tipAmount": correctTipAmount, // Use tipAmount to match API format
            "isCorrect": true,
            "difficulty": difficulty,
            "hint": "Calculate \(Int(tipPercentage.rounded()))% tip on $\(String(format: "%.2f", billAmount))"
        ]
        
        // Create answer (just the tip amount as string)
        let answerString = String(format: "%.2f", correctTipAmount)
        
        // Convert to JSON strings
        guard let questionJSON = try? JSONSerialization.data(withJSONObject: questionData),
              let questionString = String(data: questionJSON, encoding: .utf8) else {
            return ("", "")
        }
        
        print("🔵 TIPPING: Generated bill: $\(billAmount), tip: \(tipPercentage)%, amount: $\(correctTipAmount)")
        
        return (questionString, answerString)
    }
    
    private static func generateTippingParams(for difficulty: String) -> (billAmount: Double, tipPercentage: Double) {
        let billRange: (min: Double, max: Double)
        let tipOptions: [Double]
        
        switch difficulty.lowercased() {
        case "easy":
            billRange = (min: 10.0, max: 100.0)
            tipOptions = [15.0, 18.0, 20.0]
        case "medium":
            billRange = (min: 50.0, max: 300.0)
            tipOptions = [15.0, 18.0, 20.0, 22.0, 25.0]
        case "hard":
            billRange = (min: 200.0, max: 800.0)
            tipOptions = [15.0, 18.0, 20.0, 22.0, 25.0, 28.0, 30.0]
        default:
            billRange = (min: 25.0, max: 150.0)
            tipOptions = [15.0, 18.0, 20.0]
        }
        
        let billAmount = round(Double.random(in: billRange.min...billRange.max) * 100) / 100
        let tipPercentage = tipOptions.randomElement()!
        
        return (billAmount, tipPercentage)
    }
    
    // MARK: - Math Estimation Generator
    static func generateMathEstimation(difficulty: String) -> (String, String) {
        let (numbers, dataPoints) = generateEstimationData(for: difficulty)
        let correctSum = round(numbers.reduce(0, +) * 100) / 100
        let tolerance = calculateTolerance(for: correctSum, difficulty: difficulty)
        
        // Create question JSON matching EstimationPuzzleView format
        let questionData: [String: Any] = [
            "dataPoints": dataPoints.map { point in
                [
                    "value": point.value,
                    "yPosition": point.yPosition
                ]
            },
            "correctSum": correctSum,
            "tolerance": tolerance,
            "difficulty": difficulty,
            "hint": "Add up all the values shown on the chart"
        ]
        
        // Create answer (just the sum as string)
        let answerString = String(format: "%.2f", correctSum)
        
        // Convert to JSON strings
        guard let questionJSON = try? JSONSerialization.data(withJSONObject: questionData),
              let questionString = String(data: questionJSON, encoding: .utf8) else {
            return ("", "")
        }
        
        print("🔵 ESTIMATION: Generated \(numbers.count) numbers, sum: \(correctSum), tolerance: ±\(tolerance)")
        
        return (questionString, answerString)
    }
    
    // MARK: - Purchasing Generator
    static func generatePurchasing(difficulty: String) -> (String, String) {
        let (payment, frequency, purpose) = generatePurchasingParams(for: difficulty)
        let yearlyTotal = calculateYearlyTotal(payment: payment, frequency: frequency)
        
        // Create question JSON matching SubscriptionPuzzleView format
        let questionData: [String: Any] = [
            "payment": payment,
            "frequency": frequency,
            "purpose": purpose,
            "yearlyTotal": yearlyTotal,
            "difficulty": difficulty,
            "hint": "Multiply the \(frequency) payment by the number of periods in a year"
        ]
        
        // Create answer (yearly total as string)
        let answerString = String(format: "%.0f", yearlyTotal)
        
        // Convert to JSON strings
        guard let questionJSON = try? JSONSerialization.data(withJSONObject: questionData),
              let questionString = String(data: questionJSON, encoding: .utf8) else {
            return ("", "")
        }
        
        print("🔵 PURCHASING: Generated $\(payment) \(frequency) = $\(yearlyTotal)/year for \(purpose)")
        
        return (questionString, answerString)
    }
    
    // MARK: - Division Generator
    static func generateDivision(difficulty: String) -> (String, String) {
        let (problems, firstProblem) = generateDivisionData(for: difficulty)
        
        // Create question JSON matching DivisionPuzzleView format
        let questionData: [String: Any] = [
            "problems": problems,
            "difficulty": difficulty,
            "hint": "Think about multiplication tables to help with division"
        ]
        
        // Create answer (quotient of first problem as string)
        let answerString = String(firstProblem.quotient)
        
        // Convert to JSON strings
        guard let questionJSON = try? JSONSerialization.data(withJSONObject: questionData),
              let questionString = String(data: questionJSON, encoding: .utf8) else {
            return ("", "")
        }
        
        print("🔵 DIVISION: Generated \(problems.count) problems, first: \(firstProblem.dividend)÷\(firstProblem.divisor)=\(firstProblem.quotient)")
        
        return (questionString, answerString)
    }
    
    // MARK: - Average Generator  
    static func generateAverage(difficulty: String) -> (String, String) {
        let numbers = generateAverageNumbers(for: difficulty)
        let average = calculateAverage(numbers: numbers)
        
        // Create question JSON matching AveragePuzzleView format
        let questionData: [String: Any] = [
            "numbers": numbers,
            "average": average,
            "difficulty": difficulty,
            "hint": "Add all numbers together and divide by how many numbers there are"
        ]
        
        // Create answer (average as string)
        let answerString = String(format: "%.1f", average)
        
        // Convert to JSON strings
        guard let questionJSON = try? JSONSerialization.data(withJSONObject: questionData),
              let questionString = String(data: questionJSON, encoding: .utf8) else {
            return ("", "")
        }
        
        print("🔵 AVERAGE: Generated numbers \(numbers), average: \(average)")
        
        return (questionString, answerString)
    }
    
    // MARK: - Percentages Generator
    static func generatePercentages(difficulty: String) -> (String, String) {
        let (percentage, baseNumber) = generatePercentageParams(for: difficulty)
        let correctAnswer = calculatePercentage(percentage: percentage, of: baseNumber)
        
        // Create question JSON matching PercentagePuzzleView format
        let questionData: [String: Any] = [
            "percentage": percentage,
            "baseNumber": baseNumber,
            "correctAnswer": correctAnswer,
            "difficulty": difficulty,
            "hint": getPercentageHint(for: percentage)
        ]
        
        // Create answer (result as string)
        let answerString = String(correctAnswer)
        
        // Convert to JSON strings
        guard let questionJSON = try? JSONSerialization.data(withJSONObject: questionData),
              let questionString = String(data: questionJSON, encoding: .utf8) else {
            return ("", "")
        }
        
        print("🔵 PERCENTAGE: Generated \(percentage)% of \(baseNumber) = \(correctAnswer)")
        
        return (questionString, answerString)
    }
    
    private static func generateEstimationData(for difficulty: String) -> (numbers: [Double], dataPoints: [EstimationDataPoint]) {
        let (numberCount, numberRange, useDecimals) = getEstimationParams(for: difficulty)
        
        var numbers: [Double] = []
        var dataPoints: [EstimationDataPoint] = []
        
        // Generate numbers
        for i in 0..<numberCount {
            let number: Double
            if useDecimals {
                let whole = Double.random(in: numberRange.min...numberRange.max)
                let decimal = Double.random(in: 0...0.99)
                number = round((whole + decimal) * 100) / 100
            } else {
                number = Double(Int.random(in: Int(numberRange.min)...Int(numberRange.max)))
            }
            
            numbers.append(number)
            
            // Create data point with position
            let yPosition = 0.2 + (Double(i) * 0.15) // Spread points vertically
            let dataPoint = EstimationDataPoint(
                value: number,
                yPosition: Float(min(yPosition, 0.9)) // Cap at 90% height
            )
            dataPoints.append(dataPoint)
        }
        
        return (numbers, dataPoints)
    }
    
    private static func getEstimationParams(for difficulty: String) -> (count: Int, range: (min: Double, max: Double), useDecimals: Bool) {
        switch difficulty.lowercased() {
        case "easy":
            return (
                count: Int.random(in: 3...4),
                range: (min: 1.0, max: 20.0),
                useDecimals: false
            )
        case "medium":
            return (
                count: Int.random(in: 4...5),
                range: (min: 5.0, max: 50.0),
                useDecimals: true
            )
        case "hard":
            return (
                count: Int.random(in: 5...6),
                range: (min: 10.0, max: 100.0),
                useDecimals: true
            )
        default:
            return (
                count: 4,
                range: (min: 5.0, max: 30.0),
                useDecimals: false
            )
        }
    }
    
    private static func calculateTolerance(for sum: Double, difficulty: String) -> Double {
        let basePercent: Double
        switch difficulty.lowercased() {
        case "easy":
            basePercent = 0.15 // 15% tolerance
        case "medium":
            basePercent = 0.12 // 12% tolerance
        case "hard":
            basePercent = 0.10 // 10% tolerance
        default:
            basePercent = 0.12
        }
        
        let calculatedTolerance = sum * basePercent
        return max(calculatedTolerance, 1.0) // Minimum tolerance of 1.0
    }
    
    // MARK: - Purchasing Helper Methods
    private static func generatePurchasingParams(for difficulty: String) -> (payment: Double, frequency: String, purpose: String) {
        let frequencies = ["weekly", "biweekly", "monthly", "quarterly"]
        let frequency = frequencies.randomElement()!
        
        let purposes = [
            "Gym membership", "Streaming service", "Coffee subscription", 
            "Magazine subscription", "Software license", "Insurance premium",
            "Phone plan", "Internet service", "Music streaming", "Cloud storage"
        ]
        let purpose = purposes.randomElement()!
        
        let paymentRange: (min: Double, max: Double)
        switch difficulty.lowercased() {
        case "easy":
            paymentRange = (min: 10.0, max: 100.0)
        case "medium":
            paymentRange = (min: 50.0, max: 300.0)
        case "hard":
            paymentRange = (min: 200.0, max: 800.0)
        default:
            paymentRange = (min: 25.0, max: 200.0)
        }
        
        let payment = round(Double.random(in: paymentRange.min...paymentRange.max) * 100) / 100
        
        return (payment, frequency, purpose)
    }
    
    private static func calculateYearlyTotal(payment: Double, frequency: String) -> Double {
        let multiplier: Double
        switch frequency.lowercased() {
        case "weekly":
            multiplier = 52
        case "biweekly":
            multiplier = 26
        case "monthly":
            multiplier = 12
        case "quarterly":
            multiplier = 4
        case "yearly":
            multiplier = 1
        default:
            multiplier = 12
        }
        
        return round(payment * multiplier * 100) / 100
    }
    
    // MARK: - Division Helper Methods
    private static func generateDivisionData(for difficulty: String) -> (problems: [[Int]], firstProblem: (dividend: Int, divisor: Int, quotient: Int)) {
        let problemCount = Int.random(in: 2...4)
        var problems: [[Int]] = []
        
        let (dividendRange, divisorRange) = getDivisionRanges(for: difficulty)
        
        for _ in 0..<problemCount {
            let divisor = Int.random(in: divisorRange.min...divisorRange.max)
            let quotient = Int.random(in: 5...50) // Ensure reasonable quotients
            let dividend = divisor * quotient // Ensure clean division
            
            problems.append([dividend, divisor, quotient])
        }
        
        let firstProblem = problems[0]
        return (problems, (dividend: firstProblem[0], divisor: firstProblem[1], quotient: firstProblem[2]))
    }
    
    private static func getDivisionRanges(for difficulty: String) -> (dividend: (min: Int, max: Int), divisor: (min: Int, max: Int)) {
        switch difficulty.lowercased() {
        case "easy":
            return (dividend: (min: 50, max: 200), divisor: (min: 2, max: 10))
        case "medium":
            return (dividend: (min: 200, max: 500), divisor: (min: 5, max: 15))
        case "hard":
            return (dividend: (min: 500, max: 1500), divisor: (min: 10, max: 25))
        default:
            return (dividend: (min: 100, max: 400), divisor: (min: 3, max: 12))
        }
    }
    
    // MARK: - Average Helper Methods
    private static func generateAverageNumbers(for difficulty: String) -> [Int] {
        let count = getAverageNumberCount(for: difficulty)
        let range = getAverageNumberRange(for: difficulty)
        
        // Generate target average first
        let targetAverage = Int.random(in: range.min...range.max)
        
        var numbers: [Int] = []
        var sum = 0
        
        // Generate first n-1 numbers around the target
        for _ in 0..<(count - 1) {
            let deviation = Int.random(in: -15...15)
            let number = max(range.min, min(range.max, targetAverage + deviation))
            numbers.append(number)
            sum += number
        }
        
        // Calculate last number to hit target average
        let lastNumber = (targetAverage * count) - sum
        
        // Ensure last number is in reasonable range
        if lastNumber >= range.min && lastNumber <= range.max {
            numbers.append(lastNumber)
        } else {
            // Adjust approach if last number is out of range
            let adjustment = targetAverage + Int.random(in: -10...10)
            numbers.append(max(range.min, min(range.max, adjustment)))
        }
        
        return numbers
    }
    
    private static func getAverageNumberCount(for difficulty: String) -> Int {
        switch difficulty.lowercased() {
        case "easy":
            return Int.random(in: 3...4)
        case "medium":
            return Int.random(in: 4...5)
        case "hard":
            return Int.random(in: 5...6)
        default:
            return 4
        }
    }
    
    private static func getAverageNumberRange(for difficulty: String) -> (min: Int, max: Int) {
        switch difficulty.lowercased() {
        case "easy":
            return (min: 10, max: 50)
        case "medium":
            return (min: 20, max: 100)
        case "hard":
            return (min: 50, max: 200)
        default:
            return (min: 15, max: 75)
        }
    }
    
    private static func calculateAverage(numbers: [Int]) -> Double {
        let sum = numbers.reduce(0, +)
        return round(Double(sum) / Double(numbers.count) * 10) / 10 // Round to 1 decimal place
    }
    
    // MARK: - Percentages Helper Methods
    private static func generatePercentageParams(for difficulty: String) -> (percentage: Int, baseNumber: Int) {
        let percentageOptions = getPercentageOptions(for: difficulty)
        let percentage = percentageOptions.randomElement()!
        
        let baseNumberRange = getBaseNumberRange(for: difficulty)
        let baseNumber = Int.random(in: baseNumberRange.min...baseNumberRange.max)
        
        return (percentage, baseNumber)
    }
    
    private static func getPercentageOptions(for difficulty: String) -> [Int] {
        switch difficulty.lowercased() {
        case "easy":
            return [10, 20, 25, 50]
        case "medium":
            return [5, 10, 15, 20, 25, 30, 40, 50]
        case "hard":
            return [5, 8, 12, 15, 18, 22, 27, 33, 45, 60, 75]
        default:
            return [10, 20, 25, 50]
        }
    }
    
    private static func getBaseNumberRange(for difficulty: String) -> (min: Int, max: Int) {
        switch difficulty.lowercased() {
        case "easy":
            return (min: 100, max: 1000)
        case "medium":
            return (min: 500, max: 5000)
        case "hard":
            return (min: 1000, max: 10000)
        default:
            return (min: 200, max: 2000)
        }
    }
    
    private static func calculatePercentage(percentage: Int, of baseNumber: Int) -> Int {
        return Int(round(Double(baseNumber) * Double(percentage) / 100.0))
    }
    
    private static func getPercentageHint(for percentage: Int) -> String {
        switch percentage {
        case 10:
            return "Multiply by 0.10 or divide by 10"
        case 20:
            return "Multiply by 0.20 or divide by 5"
        case 25:
            return "Multiply by 0.25 or divide by 4"
        case 50:
            return "Multiply by 0.50 or divide by 2"
        case 5:
            return "Multiply by 0.05 or divide by 20"
        default:
            return "Multiply the number by \(Double(percentage)/100.0)"
        }
    }
}

// MARK: - Helper Data Structures
struct EstimationDataPoint {
    let value: Double
    let yPosition: Float
}