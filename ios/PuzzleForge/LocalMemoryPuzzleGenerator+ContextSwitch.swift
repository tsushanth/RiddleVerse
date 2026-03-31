//
//  ContextSwitchParsedData.swift
//  PuzzleForge
//
//  Created by Sushanth Tiruvaipati on 7/26/25.
//


// Extension to your existing LocalMemoryPuzzleGenerator.swift file
import Foundation

extension LocalMemoryPuzzleGenerator {
    
    static func generateContextSwitch(difficulty: String) -> (String, String) {
        return LocalContextSwitchPuzzleGenerator.generateContextSwitchPuzzle(difficulty: difficulty)
    }
}

// MARK: - Puzzle Data Extensions for Context Switch

extension Puzzle {
    var contextSwitchPuzzleData: ContextSwitchParsedData? {
        guard let data = question.data(using: .utf8) else { return nil }
        
        do {
            let json = try JSONSerialization.jsonObject(with: data) as? [String: Any]
            guard let puzzleData = json else { return nil }
            
            // Parse memory items
            guard let memoryItems = puzzleData["memoryItems"] as? [String] else { return nil }
            
            // Parse recognition items
            guard let recognitionItems = puzzleData["recognitionItems"] as? [String] else { return nil }
            
            // Parse correct answers
            guard let correctAnswers = puzzleData["correctAnswers"] as? [String] else { return nil }
            
            // Parse interference task
            guard let interferenceTaskObj = puzzleData["interferenceTask"] as? [String: Any],
                  let taskType = interferenceTaskObj["type"] as? String,
                  let taskItems = interferenceTaskObj["items"] as? [String],
                  let taskInstruction = interferenceTaskObj["instruction"] as? String,
                  let taskComplexity = interferenceTaskObj["complexity"] as? String else { return nil }
            
            let interferenceTask = ContextSwitchInterferenceTask(
                type: taskType,
                items: taskItems,
                instruction: taskInstruction,
                complexity: taskComplexity
            )
            
            // Parse metadata
            let metadata = puzzleData["metadata"] as? [String: Any]
            let category = puzzleData["category"] as? String ?? "unknown"
            let difficulty = puzzleData["difficulty"] as? String ?? "medium"
            
            return ContextSwitchParsedData(
                memoryItems: memoryItems,
                recognitionItems: recognitionItems,
                correctAnswers: correctAnswers,
                interferenceTask: interferenceTask,
                category: category,
                difficulty: difficulty,
                metadata: metadata
            )
        } catch {
            print("❌ CONTEXT_SWITCH: Failed to parse puzzle data - \(error)")
            return nil
        }
    }
}

// MARK: - Data Models for Context Switch

struct ContextSwitchParsedData {
    let memoryItems: [String]
    let recognitionItems: [String]
    let correctAnswers: [String]
    let interferenceTask: ContextSwitchInterferenceTask
    let category: String
    let difficulty: String
    let metadata: [String: Any]?
}

struct ContextSwitchInterferenceTask {
    let type: String // "number_sort", "word_alphabetize", "simple_math", "color_sequence", "pattern_match", "category_sort"
    let items: [String]
    let instruction: String
    let complexity: String // "simple", "moderate", "complex"
}
