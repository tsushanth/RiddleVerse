//
//  AntonymPuzzleData.swift
//  PuzzleForge
//
//  Created by Sushanth Tiruvaipati on 7/12/25.
//


//
//  Puzzle+AntonymData.swift
//  PuzzleForge
//
//  Created by Assistant on 12/7/25.
//

import Foundation

// MARK: - Antonym Data Models
struct AntonymPuzzleData {
    let pairs: [AntonymPairData]
    let difficulty: String
    let hint: String
    
    var count: Int {
        return pairs.count
    }
}

struct AntonymPairData: Identifiable {
    let id: Int
    let word1: String
    let word2: String
}

// MARK: - Puzzle Extension for Antonym Data
extension Puzzle {
    
    /// Computed property to extract and parse antonym puzzle data from the question field
    var antonymPuzzleData: [AntonymPairData]? {
        guard !question.isEmpty else {
            print("❌ ANTONYM: Empty question string")
            return nil
        }
        
        do {
            let pairs = try parseAntonymData(question)
            print("✅ ANTONYM: Successfully parsed \(pairs.count) antonym pairs")
            return pairs
        } catch {
            print("❌ ANTONYM: Failed to parse antonym data: \(error)")
            return nil
        }
    }
    
    /// Parse antonym data from JSON string
    private func parseAntonymData(_ puzzleData: String) throws -> [AntonymPairData] {
        let trimmedData = puzzleData.trimmingCharacters(in: .whitespacesAndNewlines)
        
        guard let data = trimmedData.data(using: .utf8) else {
            throw AntonymParsingError.invalidData
        }
        
        if trimmedData.hasPrefix("[") {
            // JSON array format: [["wet","dry"],["near","far"],...]
            print("📊 ANTONYM: Detected JSON array format")
            let pairsArray = try JSONSerialization.jsonObject(with: data) as! [[String]]
            
            return pairsArray.enumerated().compactMap { index, pair in
                guard pair.count >= 2 else {
                    print("⚠️ ANTONYM: Skipping invalid pair at index \(index): \(pair)")
                    return nil
                }
                return AntonymPairData(id: index, word1: pair[0], word2: pair[1])
            }
            
        } else if trimmedData.hasPrefix("{") {
            // JSON object format: {"pairs": [{"word1":"hot","word2":"cold"}]}
            print("📊 ANTONYM: Detected JSON object format")
            let json = try JSONSerialization.jsonObject(with: data) as! [String: Any]
            
            if let pairsArray = json["pairs"] as? [[String: String]] {
                return pairsArray.enumerated().compactMap { index, pair in
                    guard let word1 = pair["word1"], let word2 = pair["word2"] else {
                        print("⚠️ ANTONYM: Skipping invalid pair at index \(index): \(pair)")
                        return nil
                    }
                    return AntonymPairData(id: index, word1: word1, word2: word2)
                }
            } else {
                throw AntonymParsingError.missingPairsKey
            }
            
        } else {
            throw AntonymParsingError.unknownFormat
        }
    }
    
    /// Check if this puzzle contains valid antonym data
    var hasValidAntonymData: Bool {
        return antonymPuzzleData != nil && !antonymPuzzleData!.isEmpty
    }
    
    /// Get formatted display text for antonym pairs (for debugging/logging)
    var antonymPairsDisplayText: String {
        guard let pairs = antonymPuzzleData else { return "No antonym data" }
        return pairs.map { "\($0.word1) ↔ \($0.word2)" }.joined(separator: ", ")
    }
}

// MARK: - Error Types
enum AntonymParsingError: LocalizedError {
    case invalidData
    case unknownFormat
    case missingPairsKey
    case invalidPairStructure
    
    var errorDescription: String? {
        switch self {
        case .invalidData:
            return "Invalid data format for antonym puzzle"
        case .unknownFormat:
            return "Unknown JSON format for antonym puzzle"
        case .missingPairsKey:
            return "Missing 'pairs' key in antonym puzzle data"
        case .invalidPairStructure:
            return "Invalid pair structure in antonym puzzle data"
        }
    }
}
