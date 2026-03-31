//
//  PurchasingPuzzleData.swift
//  PuzzleForge
//
//  Created by Sushanth Tiruvaipati on 8/2/25.
//


// Add these extensions to your existing Puzzle model or create a new file

import Foundation

// MARK: - Purchasing Puzzle Data Extension
extension Puzzle {
    var purchasingPuzzleData: PurchasingPuzzleData? {
        guard let questionData = question.data(using: .utf8) else {
            print("🔴 PURCHASING: Failed to convert question to data")
            return nil
        }
        
        do {
            if let json = try JSONSerialization.jsonObject(with: questionData) as? [String: Any] {
                print("🔍 PURCHASING: Parsing JSON keys: \(json.keys)")
                
                guard let payment = json["payment"] as? Double,
                      let frequency = json["frequency"] as? String,
                      let purpose = json["purpose"] as? String,
                      let yearlyTotal = json["yearlyTotal"] as? Double else {
                    print("🔴 PURCHASING: Missing required fields")
                    return nil
                }
                
                let difficulty = json["difficulty"] as? String ?? "Medium"
                let hint = json["hint"] as? String ?? "Multiply the payment by the number of periods in a year"
                
                print("🟢 PURCHASING: Successfully parsed - Payment: $\(payment) \(frequency) = $\(yearlyTotal)/year")
                
                return PurchasingPuzzleData(
                    payment: payment,
                    frequency: frequency,
                    purpose: purpose,
                    yearlyTotal: yearlyTotal,
                    difficulty: difficulty,
                    hint: hint
                )
            }
        } catch {
            print("🔴 PURCHASING: JSON parsing error: \(error)")
        }
        
        // Fallback sample data
        print("🔴 PURCHASING: Using fallback sample data")
        return PurchasingPuzzleData(
            payment: 75.0,
            frequency: "biweekly",
            purpose: "Gym membership",
            yearlyTotal: 1950.0,
            difficulty: "Medium",
            hint: "Multiply the biweekly payment by 26"
        )
    }
}

// MARK: - Division Puzzle Data Extension
extension Puzzle {
    var divisionPuzzleData: DivisionPuzzleData? {
        guard let questionData = question.data(using: .utf8) else {
            print("🔴 DIVISION: Failed to convert question to data")
            return nil
        }
        
        do {
            if let json = try JSONSerialization.jsonObject(with: questionData) as? [String: Any] {
                print("🔍 DIVISION: Parsing JSON keys: \(json.keys)")
                
                guard let problemsArray = json["problems"] as? [[Int]],
                      !problemsArray.isEmpty,
                      problemsArray[0].count >= 3 else {
                    print("🔴 DIVISION: Invalid problems array")
                    return nil
                }
                
                let firstProblem = problemsArray[0]
                let dividend = firstProblem[0]
                let divisor = firstProblem[1]
                let correctAnswer = firstProblem[2]
                
                let difficulty = json["difficulty"] as? String ?? "Medium"
                let hint = json["hint"] as? String ?? "Think about multiplication tables"
                
                print("🟢 DIVISION: Successfully parsed - \(dividend)÷\(divisor)=\(correctAnswer)")
                
                return DivisionPuzzleData(
                    dividend: dividend,
                    divisor: divisor,
                    correctAnswer: correctAnswer,
                    allProblems: problemsArray,
                    difficulty: difficulty,
                    hint: hint
                )
            }
        } catch {
            print("🔴 DIVISION: JSON parsing error: \(error)")
        }
        
        // Fallback sample data
        print("🔴 DIVISION: Using fallback sample data")
        return DivisionPuzzleData(
            dividend: 872,
            divisor: 8,
            correctAnswer: 109,
            allProblems: [[872, 8, 109]],
            difficulty: "Medium",
            hint: "Think about multiplication tables"
        )
    }
}

// MARK: - Average Puzzle Data Extension
extension Puzzle {
    var averagePuzzleData: AveragePuzzleData? {
        guard let questionData = question.data(using: .utf8) else {
            print("🔴 AVERAGE: Failed to convert question to data")
            return nil
        }
        
        do {
            if let json = try JSONSerialization.jsonObject(with: questionData) as? [String: Any] {
                print("🔍 AVERAGE: Parsing JSON keys: \(json.keys)")
                
                guard let numbersArray = json["numbers"] as? [Int],
                      let average = json["average"] as? Double else {
                    print("🔴 AVERAGE: Missing required fields")
                    return nil
                }
                
                let difficulty = json["difficulty"] as? String ?? "Medium"
                let hint = json["hint"] as? String ?? "Add all numbers and divide by count"
                
                print("🟢 AVERAGE: Successfully parsed - Numbers: \(numbersArray), Average: \(average)")
                
                return AveragePuzzleData(
                    numbers: numbersArray,
                    average: average,
                    difficulty: difficulty,
                    hint: hint
                )
            }
        } catch {
            print("🔴 AVERAGE: JSON parsing error: \(error)")
        }
        
        // Fallback sample data
        print("🔴 AVERAGE: Using fallback sample data")
        return AveragePuzzleData(
            numbers: [25, 59, 33],
            average: 39.0,
            difficulty: "Easy",
            hint: "Add all numbers together and divide by how many numbers there are"
        )
    }
}

// MARK: - Percentage Puzzle Data Extension
extension Puzzle {
    var percentagePuzzleData: PercentagePuzzleData? {
        guard let questionData = question.data(using: .utf8) else {
            print("🔴 PERCENTAGE: Failed to convert question to data")
            return nil
        }
        
        do {
            if let json = try JSONSerialization.jsonObject(with: questionData) as? [String: Any] {
                print("🔍 PERCENTAGE: Parsing JSON keys: \(json.keys)")
                
                guard let percentage = json["percentage"] as? Int,
                      let baseNumber = json["baseNumber"] as? Int,
                      let correctAnswer = json["correctAnswer"] as? Int else {
                    print("🔴 PERCENTAGE: Missing required fields")
                    return nil
                }
                
                let difficulty = json["difficulty"] as? String ?? "Medium"
                let hint = json["hint"] as? String ?? "Multiply the number by the percentage"
                
                print("🟢 PERCENTAGE: Successfully parsed - \(percentage)% of \(baseNumber) = \(correctAnswer)")
                
                return PercentagePuzzleData(
                    percentage: percentage,
                    baseNumber: baseNumber,
                    correctAnswer: correctAnswer,
                    difficulty: difficulty,
                    hint: hint
                )
            }
        } catch {
            print("🔴 PERCENTAGE: JSON parsing error: \(error)")
        }
        
        // Fallback sample data
        print("🔴 PERCENTAGE: Using fallback sample data")
        return PercentagePuzzleData(
            percentage: 10,
            baseNumber: 6700,
            correctAnswer: 670,
            difficulty: "Easy",
            hint: "Multiply by 0.10 or divide by 10"
        )
    }
}
