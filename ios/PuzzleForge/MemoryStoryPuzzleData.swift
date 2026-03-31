//
//  MemoryStoryPuzzleData.swift
//  PuzzleForge
//
//  Created by Sushanth Tiruvaipati on 7/12/25.
//


import Foundation

// MARK: - Memory Story Data Structure
struct MemoryStoryPuzzleData {
    let storyCard: String
    let question: String
    let options: [String]
    let scenario: String
    let character: String
    let itemCount: Int
    let audioUrl: String?
    let correctItems: [String]
}

// MARK: - Puzzle Extension for Memory Story
extension Puzzle {
    var memoryStoryPuzzleData: MemoryStoryPuzzleData? {
        guard puzzleType?.lowercased().contains("memory") == true ||
              puzzleType?.lowercased().contains("story") == true else {
            return nil
        }
        
        print("🧠 MEMORY_STORY: Parsing memory story puzzle data")
        print("🧠 MEMORY_STORY: Question: \(question)")
        print("🧠 MEMORY_STORY: Answer: \(answer)")
        
        // Parse the question JSON
        guard let questionData = question.data(using: .utf8),
              let questionJson = try? JSONSerialization.jsonObject(with: questionData) as? [String: Any] else {
            print("❌ MEMORY_STORY: Failed to parse question JSON")
            return nil
        }
        
        let storyCard = questionJson["storyCard"] as? String ?? ""
        let questionText = questionJson["question"] as? String ?? ""
        let scenario = questionJson["scenario"] as? String ?? "grocery"
        let character = questionJson["character"] as? String ?? "Someone"
        let itemCount = questionJson["itemCount"] as? Int ?? 3
        let audioUrl = questionJson["audioUrl"] as? String
        
        // Parse options array from question data
        var options: [String] = []
        if let optionsArray = questionJson["options"] as? [Any] {
            for option in optionsArray {
                if let optionString = option as? String {
                    options.append(optionString)
                }
            }
        }
        
        // Parse correct items from answer field (separate JSON array)
        var correctItems: [String] = []
        if let answerData = answer.data(using: .utf8),
           let correctItemsArray = try? JSONSerialization.jsonObject(with: answerData) as? [String] {
            correctItems = correctItemsArray
        } else {
            print("❌ MEMORY_STORY: Failed to parse correct items from answer: \(answer)")
        }
        
        print("📊 MEMORY_STORY: Successfully parsed data:")
        print("   Story: \(storyCard)")
        print("   Question: \(questionText)")
        print("   Options count: \(options.count)")
        print("   Correct items: \(correctItems)")
        print("   Scenario: \(scenario)")
        print("   Audio URL: \(audioUrl ?? "none")")
        
        return MemoryStoryPuzzleData(
            storyCard: storyCard,
            question: questionText,
            options: options,
            scenario: scenario,
            character: character,
            itemCount: itemCount,
            audioUrl: audioUrl,
            correctItems: correctItems
        )
    }
}