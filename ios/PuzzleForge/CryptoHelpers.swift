//
//  CryptoHelpers.swift
//  PuzzleForge
//
//  Created by Sushanth Tiruvaipati on 9/17/25.
//


//
//  CryptoHelpers.swift
//  PuzzleForge
//

import Foundation

// MARK: - Crypto Puzzle Generation Functions
func generateCryptoPuzzleData(text: String, difficulty: String, answer: String) -> [String: Any] {
    let cleanText = text.uppercased().filter { $0.isLetter || $0.isWhitespace }

    let frequencyMap = createFrequencyMap(text: cleanText)
    
    let removalPercentage: Double
    switch difficulty.lowercased() {
    case "easy": removalPercentage = 0.5
    case "medium": removalPercentage = 0.6
    case "hard": removalPercentage = 0.7
    case "expert": removalPercentage = 0.8
    default: removalPercentage = 0.6
    }
    
    let hiddenLetters = selectCharactersToHide(frequencyMap: frequencyMap, removalPercentage: removalPercentage)
    let numberMapping = createNumberMapping(hiddenLetters: hiddenLetters)
    let revealedLetters = selectRevealedLetters(hiddenLetters: hiddenLetters, difficulty: difficulty)
    let timeLimit = calculateCryptoTimeLimit(for: cleanText, difficulty: difficulty)
    let targetWord = answer.isEmpty ? nil : answer
    
    print("🔍 CRYPTO: Generated puzzle with Android logic:")
    print("📊 CRYPTO: Hidden letters: \(hiddenLetters.count)/\(frequencyMap.count) (\(Int(Double(hiddenLetters.count)/Double(frequencyMap.count)*100))%)")
    print("📊 CRYPTO: Revealed hints: \(revealedLetters.count)")
    print("📊 CRYPTO: Target word: \(targetWord ?? "none")")
    
    return [
        "originalText": cleanText,
        "numberMapping": numberMapping,
        "revealedLetters": Array(revealedLetters).map { String($0) },
        "hiddenLetters": Array(hiddenLetters).map { String($0) },
        "targetWord": targetWord ?? "",
        "difficulty": difficulty,
        "timeLimit": timeLimit,
        "instructions": "Decode the hidden message by figuring out which number represents which letter."
    ]
}

private func createFrequencyMap(text: String) -> [Character: Int] {
    var frequencyMap: [Character: Int] = [:]
    
    for char in text {
        if char.isLetter {
            let upperChar = char.uppercased().first!
            frequencyMap[upperChar] = (frequencyMap[upperChar] ?? 0) + 1
        }
    }
    
    return frequencyMap
}

private func selectCharactersToHide(frequencyMap: [Character: Int], removalPercentage: Double) -> Set<Character> {
    let sortedByFrequency = frequencyMap.sorted { $0.value > $1.value }.map { $0.key }
    
    var hiddenLetters = Set<Character>()
    
    let guaranteedHidden: [Character] = ["E", "T", "A", "O", "I", "N", "S", "H", "R"]
    
    for letter in guaranteedHidden {
        if frequencyMap.keys.contains(letter) {
            hiddenLetters.insert(letter)
        }
    }
    
    let totalUniqueLetters = frequencyMap.count
    let targetHiddenCount = Int(Double(totalUniqueLetters) * removalPercentage)
    let remainingToHide = max(0, targetHiddenCount - hiddenLetters.count)
    
    let additionalLetters = Array(sortedByFrequency.prefix(remainingToHide + hiddenLetters.count))
    for letter in additionalLetters {
        hiddenLetters.insert(letter)
    }
    
    var finalHiddenLetters = Set<Character>()
    for letter in hiddenLetters {
        let randomChance = Double.random(in: 0...1)
        switch randomChance {
        case 0..<0.7:
            finalHiddenLetters.insert(letter)
        case 0.7..<0.9:
            if (frequencyMap[letter] ?? 0) > 2 {
                finalHiddenLetters.insert(letter)
            }
        default:
            break
        }
    }
    
    if finalHiddenLetters.count < 3 {
        for letter in Array(sortedByFrequency.prefix(5)) {
            finalHiddenLetters.insert(letter)
        }
    }
    
    return finalHiddenLetters
}

private func createNumberMapping(hiddenLetters: Set<Character>) -> [String: Int] {
    var numberMapping: [String: Int] = [:]
    var availableNumbers = Array(1...26)
    availableNumbers.shuffle()
    
    let hiddenLettersArray = Array(hiddenLetters)
    for (index, letter) in hiddenLettersArray.enumerated() {
        if index < availableNumbers.count {
            numberMapping[String(letter)] = availableNumbers[index]
        }
    }
    
    return numberMapping
}

private func selectRevealedLetters(hiddenLetters: Set<Character>, difficulty: String) -> Set<Character> {
    let revealPercentage: Double
    switch difficulty.lowercased() {
    case "easy": revealPercentage = 0.4
    case "medium": revealPercentage = 0.25
    case "hard": revealPercentage = 0.15
    case "expert": revealPercentage = 0.1
    default: revealPercentage = 0.25
    }
    
    let revealCount = Int(Double(hiddenLetters.count) * revealPercentage)
    if revealCount == 0 {
        return Set<Character>()
    }
    
    let sortedHiddenLetters = Array(hiddenLetters).shuffled()
    
    return Set(Array(sortedHiddenLetters.prefix(revealCount)))
}

private func calculateCryptoTimeLimit(for text: String, difficulty: String) -> Int {
    let baseTimePerWord = 30
    let wordCount = text.split(separator: " ").count
    let letterCount = text.filter { $0.isLetter }.count
    
    let difficultyMultiplier: Double
    switch difficulty.lowercased() {
    case "easy": difficultyMultiplier = 1.5
    case "medium": difficultyMultiplier = 1.2
    case "hard": difficultyMultiplier = 1.0
    case "expert": difficultyMultiplier = 0.8
    default: difficultyMultiplier = 1.2
    }
    
    let calculatedTime = Int(Double(wordCount * baseTimePerWord + letterCount * 2) * difficultyMultiplier)
    return max(calculatedTime, 180)
}