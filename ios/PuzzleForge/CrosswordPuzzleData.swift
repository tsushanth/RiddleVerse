//
//  CrosswordPuzzleData.swift
//  PuzzleForge
//
//  Created by Sushanth Tiruvaipati on 7/12/25.
//


//
//  Puzzle+Crossword.swift
//  PuzzleForge
//
//  Created by Assistant on [Current Date]
//

import Foundation

// MARK: - Puzzle Extension for Crossword Support
extension Puzzle {
    /// Parsed crossword puzzle data from the question JSON
    var crosswordPuzzleData: CrosswordPuzzleData? {
        guard puzzleType?.lowercased() == "crossword",
              let data = question.data(using: .utf8) else {
            return nil
        }
        
        do {
            let crosswordData = try JSONDecoder().decode(CrosswordPuzzleData.self, from: data)
            return crosswordData
        } catch {
            print("❌ Failed to decode crossword data: \(error)")
            print("❌ Raw question data: \(question)")
            return nil
        }
    }
    
    /// Validates if the puzzle has valid crossword data
    var hasValidCrosswordData: Bool {
        return crosswordPuzzleData != nil
    }
    
    /// Returns a sample crossword puzzle for testing
    static func sampleCrosswordPuzzle() -> Puzzle {
        let sampleJSON = """
        {
            "words": [
                {
                    "word": "APPLE",
                    "hint": "Red or green fruit",
                    "startX": 0,
                    "startY": 0,
                    "direction": "horizontal",
                    "length": 5
                },
                {
                    "word": "PLANE",
                    "hint": "Flying vehicle",
                    "startX": 2,
                    "startY": 0,
                    "direction": "vertical",
                    "length": 5
                },
                {
                    "word": "LEMON",
                    "hint": "Yellow citrus fruit",
                    "startX": 0,
                    "startY": 4,
                    "direction": "horizontal",
                    "length": 5
                }
            ],
            "matrix": [
                ["A", "P", "P", "L", "E"],
                ["_", "_", "L", "_", "_"],
                ["_", "_", "A", "_", "_"],
                ["_", "_", "N", "_", "_"],
                ["L", "E", "M", "O", "N"]
            ]
        }
        """
        
        return Puzzle(
            question: sampleJSON,
            answer: "APPLE,PLANE,LEMON", // Comma-separated correct answers
            hint: "Fill in the crossword puzzle using the given hints",
            options: [],
            format: "crossword",
            puzzleType: "crossword",
            puzzleId: UUID().uuidString,
            id: UUID().uuidString,
            name: "Sample Crossword",
            createdAt: Date().timeIntervalSince1970 * 1000,
            status: "ready",
            difficulty: "Easy"
        )
    }
}

// MARK: - Crossword Validation Helpers
extension Puzzle {
    /// Validates a crossword solution against the correct answers
    func validateCrosswordSolution(_ userAnswers: [String: String]) -> Bool {
        guard let crosswordData = crosswordPuzzleData else { return false }
        
        for (index, wordData) in crosswordData.words.enumerated() {
            let wordKey = "\(index + 1)" // Using 1-based numbering
            guard let userAnswer = userAnswers[wordKey] else { return false }
            
            if userAnswer.uppercased() != wordData.word.uppercased() {
                return false
            }
        }
        
        return true
    }
    
    /// Gets the word count for the crossword puzzle
    var crosswordWordCount: Int {
        return crosswordPuzzleData?.words.count ?? 0
    }
    
    /// Gets the grid dimensions for the crossword puzzle
    var crosswordGridSize: (width: Int, height: Int) {
        guard let matrix = crosswordPuzzleData?.matrix else {
            return (width: 5, height: 5) // Default 5x5 grid
        }
        
        let height = matrix.count
        let width = matrix.first?.count ?? 5
        return (width: width, height: height)
    }
}

// MARK: - Sample Data Generator for Testing
extension CrosswordPuzzleData {
    static func sampleData() -> CrosswordPuzzleData {
        return CrosswordPuzzleData(
            words: [
                CrosswordWordData(
                    word: "SWIFT",
                    hint: "Apple's programming language",
                    startX: 0,
                    startY: 0,
                    direction: "horizontal",
                    length: 5
                ),
                CrosswordWordData(
                    word: "WATER",
                    hint: "Essential liquid for life",
                    startX: 2,
                    startY: 0,
                    direction: "vertical",
                    length: 5
                ),
                CrosswordWordData(
                    word: "TIGER",
                    hint: "Large striped cat",
                    startX: 0,
                    startY: 4,
                    direction: "horizontal",
                    length: 5
                )
            ],
            matrix: [
                ["S", "W", "I", "F", "T"],
                ["_", "_", "A", "_", "_"],
                ["_", "_", "T", "_", "_"],
                ["_", "_", "E", "_", "_"],
                ["T", "I", "G", "E", "R"]
            ]
        )
    }
}
