//
//  LocalMemoryPuzzleGenerator.swift
//  PuzzleForge
//
//  Created by Sushanth Tiruvaipati on 7/19/25.
//


//
//  LocalMemoryPuzzleGenerator.swift
//  PuzzleForge
//
//  Generates local data for memory puzzles when server is unavailable
//

import Foundation

struct LocalMemoryPuzzleGenerator {
    
    static func generatePinballDeflector(difficulty: String) -> (String, String) {
        print("🎱 Generating local pinball deflector puzzle - difficulty: \(difficulty)")
        
        let (questionJson, answerJson) = PinballDeflectorGenerator.generatePuzzle(difficulty: difficulty)
        
        print("✅ LOCAL PINBALL: Generated puzzle successfully")
        print("📊 LOCAL PINBALL: Question length: \(questionJson.count) chars")
        print("📊 LOCAL PINBALL: Answer length: \(answerJson.count) chars")
        
        return (questionJson, answerJson)
    }
    
    static func generateMathTipping(difficulty: String) -> (String, String) {
            return LocalMathPuzzleGenerators.generateMathTipping(difficulty: difficulty)
        }
        
        // MARK: - Math Estimation
        static func generateMathEstimation(difficulty: String) -> (String, String) {
            return LocalMathPuzzleGenerators.generateMathEstimation(difficulty: difficulty)
        }
        
        // MARK: - Purchasing
        static func generatePurchasing(difficulty: String) -> (String, String) {
            return LocalMathPuzzleGenerators.generatePurchasing(difficulty: difficulty)
        }
        
        // MARK: - Division
        static func generateDivision(difficulty: String) -> (String, String) {
            return LocalMathPuzzleGenerators.generateDivision(difficulty: difficulty)
        }
        
        // MARK: - Average
        static func generateAverage(difficulty: String) -> (String, String) {
            return LocalMathPuzzleGenerators.generateAverage(difficulty: difficulty)
        }
        
        // MARK: - Percentages
        static func generatePercentages(difficulty: String) -> (String, String) {
            return LocalMathPuzzleGenerators.generatePercentages(difficulty: difficulty)
        }
    
    static func generateConversion(difficulty: String) -> (String, String) {
        return LocalConversionPuzzleGenerator.generateConversionPuzzle(difficulty: difficulty)
    }
    
    static func generateDiscounts(difficulty: String) -> (String, String) {
        return LocalDiscountsPuzzleGenerator.generateDiscountsPuzzle(difficulty: difficulty)
    }
    
    // MARK: - Memory Previous Single Generator
    static func generateMemoryPreviousSingle(difficulty: String) -> (String, String) {
        let config = getMemorySingleConfig(for: difficulty)
        
        // Generate random binary sequence
        let binarySequence = (0..<config.sequenceLength).map { _ in Int.random(in: 0...1) }
        
        // Create question sequence with timing info
        var questionSequence: [[String: Any]] = []
        for (index, value) in binarySequence.enumerated() {
            questionSequence.append([
                "step": index + 1,
                "value": value,
                "isFirstStep": index == 0
            ])
        }
        
        // Generate correct answers (compare each symbol with previous)
        var answerSequence: [Int] = []
        for i in 1..<binarySequence.count {
            let matches = binarySequence[i] == binarySequence[i-1]
            answerSequence.append(matches ? 1 : 0) // 1 = YES (matches), 0 = NO (different)
        }
        
        let questionData: [String: Any] = [
            "binarySequence": binarySequence,
            "questionSequence": questionSequence,
            "instructions": [
                "title": "Memory Previous Single",
                "description": "Remember the previous symbol and compare it with the current one",
                "steps": [
                    "Watch the sequence of symbols carefully",
                    "For each symbol, decide if it matches the previous one",
                    "Press YES if they match, NO if they don't",
                    "Complete all questions to finish"
                ],
                "tip": "Focus on the previous symbol before the current one appears"
            ],
            "totalSteps": binarySequence.count,
            "totalQuestions": answerSequence.count,
            "difficulty": difficulty
        ]
        
        let answerData: [String: Any] = [
            "answerSequence": answerSequence,
            "totalQuestions": answerSequence.count,
            "maxScore": answerSequence.count * 5 // 5 points per correct answer
        ]
        
        guard let questionJSON = try? JSONSerialization.data(withJSONObject: questionData),
              let answerJSON = try? JSONSerialization.data(withJSONObject: answerData),
              let questionString = String(data: questionJSON, encoding: .utf8),
              let answerString = String(data: answerJSON, encoding: .utf8) else {
            return ("", "")
        }
        
        print("🧠 Generated Memory Previous Single:")
        print("  - Difficulty: \(difficulty)")
        print("  - Sequence length: \(binarySequence.count)")
        print("  - Binary sequence: \(binarySequence)")
        print("  - Questions: \(answerSequence.count)")
        print("  - Answer sequence: \(answerSequence)")
        
        return (questionString, answerString)
    }
    
    // MARK: - Memory Previous Pair Generator
    static func generateMemoryPreviousPair(difficulty: String) -> (String, String) {
        let config = getMemoryPairConfig(for: difficulty)
        
        // Generate a valid sequence that prevents position reversals
        let validSequence = generateValidMemorySequence(
            totalScreens: config.totalScreens,
            maxObjectPool: 12 // Increased from 6 for more variety
        )
        
        var sequence: [[String: Any]] = []
        var linkingNumbers: [[String: Any]] = []
        
        // Convert the valid sequence to the expected format
        for (index, screenData) in validSequence.enumerated() {
            let screenNum = index + 1
            
            if screenData.isFirstScreen {
                // First screen
                sequence.append([
                    "screenNumber": screenNum,
                    "numbers": screenData.numbers,
                    "linkingNumber": NSNull(),
                    "isFirstScreen": true
                ])
            } else {
                // Subsequent screens with linking
                sequence.append([
                    "screenNumber": screenNum,
                    "numbers": screenData.numbers,
                    "linkingNumber": screenData.linkingNumber!,
                    "isFirstScreen": false
                ])
                
                linkingNumbers.append([
                    "screenNumber": screenNum,
                    "linkingNumber": screenData.linkingNumber!
                ])
            }
        }
        
        let questionData: [String: Any] = [
            "sequence": sequence,
            "instructions": [
                "title": "Memory Previous Pair Challenge",
                "description": "Remember the animals from the previous screen and tap the one that matches!",
                "steps": [
                    "Watch the first screen carefully",
                    "For each new screen, tap the animal that appeared in the previous screen",
                    "Complete all screens to finish"
                ],
                "tip": "Focus on remembering which animals you just saw!"
            ],
            "totalScreens": config.totalScreens,
            "objectsPerScreen": 2,
            "maxObjectPool": 12, // Updated to match new pool size
            "difficulty": difficulty
        ]
        
        let answerData: [String: Any] = [
            "linkingNumbers": linkingNumbers,
            "totalCorrectAnswers": linkingNumbers.count,
            "maxScore": linkingNumbers.count * 10 // 10 points per correct answer
        ]
        
        guard let questionJSON = try? JSONSerialization.data(withJSONObject: questionData),
              let answerJSON = try? JSONSerialization.data(withJSONObject: answerData),
              let questionString = String(data: questionJSON, encoding: .utf8),
              let answerString = String(data: answerJSON, encoding: .utf8) else {
            return ("", "")
        }
        
        print("🦁 Generated Memory Previous Pair:")
        print("  - Difficulty: \(difficulty)")
        print("  - Total screens: \(config.totalScreens)")
        print("  - Questions: \(linkingNumbers.count)")
        print("  - Sequence validation: ✅ No position reversals")
        
        return (questionString, answerString)
    }

    // MARK: - Helper Data Structure
    private struct MemoryScreenData {
        let numbers: [Int]
        let linkingNumber: Int?
        let isFirstScreen: Bool
    }

    // MARK: - Sequence Generation with Validation
    private static func generateValidMemorySequence(totalScreens: Int, maxObjectPool: Int) -> [MemoryScreenData] {
        var validSequence: [MemoryScreenData] = []
        var attempts = 0
        let maxAttempts = 100
        
        while validSequence.count < totalScreens && attempts < maxAttempts {
            attempts += 1
            
            if validSequence.isEmpty {
                // Generate first screen
                let firstAnimals = generateRandomAnimals(count: 2, from: maxObjectPool, excluding: [])
                let firstScreen = MemoryScreenData(
                    numbers: firstAnimals,
                    linkingNumber: nil,
                    isFirstScreen: true
                )
                validSequence.append(firstScreen)
            } else {
                // Generate subsequent screen
                let previousScreen = validSequence.last!
                let linkingAnimal = previousScreen.numbers.randomElement()!
                
                // Generate new screen with linking animal
                let newScreen = generateScreenWithLinking(
                    linkingAnimal: linkingAnimal,
                    maxObjectPool: maxObjectPool,
                    previousSequence: validSequence
                )
                
                if let newScreen = newScreen {
                    validSequence.append(newScreen)
                } else {
                    // If we can't generate a valid continuation, restart
                    print("⚠️ Failed to generate valid continuation, restarting sequence generation...")
                    validSequence.removeAll()
                }
            }
        }
        
        if validSequence.count < totalScreens {
            print("⚠️ Warning: Could only generate \(validSequence.count) screens out of \(totalScreens)")
        }
        
        return validSequence
    }

    // MARK: - Screen Generation with Linking
    private static func generateScreenWithLinking(
        linkingAnimal: Int,
        maxObjectPool: Int,
        previousSequence: [MemoryScreenData]
    ) -> MemoryScreenData? {
        
        var attempts = 0
        let maxAttempts = 50
        
        while attempts < maxAttempts {
            attempts += 1
            
            // Generate a new animal different from linking animal
            let availableAnimals = Array(1...maxObjectPool).filter { $0 != linkingAnimal }
            guard let newAnimal = availableAnimals.randomElement() else { continue }
            
            // Create potential new pair (randomly order the animals)
            let newPair = Bool.random() ? [linkingAnimal, newAnimal] : [newAnimal, linkingAnimal]
            
            // Validate against all previous pairs
            if isValidNewPair(newPair, against: previousSequence) {
                return MemoryScreenData(
                    numbers: newPair,
                    linkingNumber: linkingAnimal,
                    isFirstScreen: false
                )
            }
        }
        
        return nil // Failed to generate valid screen
    }

    // MARK: - Pair Validation Logic
    private static func isValidNewPair(_ newPair: [Int], against previousSequence: [MemoryScreenData]) -> Bool {
        for previousScreen in previousSequence {
            let previousPair = previousScreen.numbers
            
            // Check for position reversal: [A,B] vs [B,A]
            if (newPair[0] == previousPair[1] && newPair[1] == previousPair[0]) {
                print("❌ Rejected pair \(newPair) - position reversal of \(previousPair)")
                return false
            }
            
            // Check for identical pairs: [A,B] vs [A,B]
            if (newPair[0] == previousPair[0] && newPair[1] == previousPair[1]) {
                print("❌ Rejected pair \(newPair) - identical to \(previousPair)")
                return false
            }
        }
        
        return true
    }

    // MARK: - Enhanced Random Animal Generation
    private static func generateRandomAnimals(count: Int, from maxPool: Int = 12, excluding: [Int] = []) -> [Int] {
        let availableAnimals = Array(1...maxPool).filter { !excluding.contains($0) }
        guard availableAnimals.count >= count else {
            // Fallback if not enough animals available
            return Array(1...count)
        }
        
        return Array(availableAnimals.shuffled().prefix(count))
    }

    // MARK: - Alternative Generation Method (if the above fails)
    private static func generateMemoryPreviousPairFallback(difficulty: String) -> (String, String) {
        let config = getMemoryPairConfig(for: difficulty)
        
        // Simple fallback that manually creates a known-good sequence
        let fallbackSequence: [MemoryScreenData] = [
            MemoryScreenData(numbers: [1, 2], linkingNumber: nil, isFirstScreen: true),
            MemoryScreenData(numbers: [2, 3], linkingNumber: 2, isFirstScreen: false),
            MemoryScreenData(numbers: [4, 3], linkingNumber: 3, isFirstScreen: false),
            MemoryScreenData(numbers: [4, 5], linkingNumber: 4, isFirstScreen: false),
            MemoryScreenData(numbers: [6, 5], linkingNumber: 5, isFirstScreen: false)
        ]
        
        var sequence: [[String: Any]] = []
        var linkingNumbers: [[String: Any]] = []
        
        let screensToUse = min(fallbackSequence.count, config.totalScreens)
        
        for i in 0..<screensToUse {
            let screenData = fallbackSequence[i]
            let screenNum = i + 1
            
            if screenData.isFirstScreen {
                sequence.append([
                    "screenNumber": screenNum,
                    "numbers": screenData.numbers,
                    "linkingNumber": NSNull(),
                    "isFirstScreen": true
                ])
            } else {
                sequence.append([
                    "screenNumber": screenNum,
                    "numbers": screenData.numbers,
                    "linkingNumber": screenData.linkingNumber!,
                    "isFirstScreen": false
                ])
                
                linkingNumbers.append([
                    "screenNumber": screenNum,
                    "linkingNumber": screenData.linkingNumber!
                ])
            }
        }
        
        let questionData: [String: Any] = [
            "sequence": sequence,
            "instructions": [
                "title": "Memory Previous Pair Challenge",
                "description": "Remember the animals from the previous screen and tap the one that matches!",
                "steps": [
                    "Watch the first screen carefully",
                    "For each new screen, tap the animal that appeared in the previous screen",
                    "Complete all screens to finish"
                ],
                "tip": "Focus on remembering which animals you just saw!"
            ],
            "totalScreens": screensToUse,
            "objectsPerScreen": 2,
            "maxObjectPool": 12,
            "difficulty": difficulty
        ]
        
        let answerData: [String: Any] = [
            "linkingNumbers": linkingNumbers,
            "totalCorrectAnswers": linkingNumbers.count,
            "maxScore": linkingNumbers.count * 10
        ]
        
        guard let questionJSON = try? JSONSerialization.data(withJSONObject: questionData),
              let answerJSON = try? JSONSerialization.data(withJSONObject: answerData),
              let questionString = String(data: questionJSON, encoding: .utf8),
              let answerString = String(data: answerJSON, encoding: .utf8) else {
            return ("", "")
        }
        
        print("🦁 Generated Memory Previous Pair (Fallback):")
        print("  - Difficulty: \(difficulty)")
        print("  - Total screens: \(screensToUse)")
        print("  - Questions: \(linkingNumbers.count)")
        
        return (questionString, answerString)
    }
    
    // MARK: - Configuration Helpers
    
    private static func getMemorySingleConfig(for difficulty: String) -> MemorySingleConfig {
        switch difficulty.lowercased() {
        case "easy":
            return MemorySingleConfig(sequenceLength: 5, speed: 2000) // 2 seconds per symbol
        case "medium":
            return MemorySingleConfig(sequenceLength: 7, speed: 1500) // 1.5 seconds per symbol
        case "hard":
            return MemorySingleConfig(sequenceLength: 10, speed: 1000) // 1 second per symbol
        default:
            return MemorySingleConfig(sequenceLength: 5, speed: 2000)
        }
    }
    
    private static func getMemoryPairConfig(for difficulty: String) -> MemoryPairConfig {
        switch difficulty.lowercased() {
        case "easy":
            return MemoryPairConfig(totalScreens: 4, memoryTime: 3000) // 3 seconds to memorize
        case "medium":
            return MemoryPairConfig(totalScreens: 6, memoryTime: 2500) // 2.5 seconds to memorize
        case "hard":
            return MemoryPairConfig(totalScreens: 8, memoryTime: 2000) // 2 seconds to memorize
        default:
            return MemoryPairConfig(totalScreens: 4, memoryTime: 3000)
        }
    }
    
    private static func generateRandomAnimals(count: Int) -> [Int] {
        var animals: [Int] = []
        while animals.count < count {
            let animal = Int.random(in: 1...6) // Animals 1-6 (Lion, Hippo, Elephant, Tiger, Giraffe, Monkey)
            if !animals.contains(animal) {
                animals.append(animal)
            }
        }
        return animals
    }
    
    // MARK: - Configuration Structs
    
    private struct MemorySingleConfig {
        let sequenceLength: Int
        let speed: Int // milliseconds between symbols
    }
    
    private struct MemoryPairConfig {
        let totalScreens: Int
        let memoryTime: Int // milliseconds to memorize each screen
    }
}
