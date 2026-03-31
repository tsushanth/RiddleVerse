//
//  PuzzleCategory.swift
//  PuzzleForge
//
//  Created by Sushanth Tiruvaipati on 6/12/25.
//


import SwiftUI
import FirebaseAuth

// MARK: - Puzzle Category Configuration
struct PuzzleCategory {
    let id: String
    let title: String
    let subtitle: String
    let imageName: String
    let puzzleType: String
    
    static let categories: [PuzzleCategory] = [
        PuzzleCategory(
            id: "math",
            title: "Math",
            subtitle: "Mathematical Challenges",
            imageName: "function",
            puzzleType: "math"
        ),
        PuzzleCategory(
            id: "storyPuzzle",
            title: "Story Puzzle",
            subtitle: "Narrative Mysteries",
            imageName: "book.fill",
            puzzleType: "storyPuzzle"
        ),
        PuzzleCategory(
            id: "anagram",
            title: "Anagram",
            subtitle: "Word Scrambles",
            imageName: "textformat.abc",
            puzzleType: "anagram"
        ),
        PuzzleCategory(
            id: "trivia",
            title: "Trivia",
            subtitle: "General Knowledge",
            imageName: "questionmark.circle.fill",
            puzzleType: "trivia"
        ),
        PuzzleCategory(
            id: "average",
            title: "Average",
            subtitle: "Calculate Averages",
            imageName: "chart.bar.fill",
            puzzleType: "average"
        )
    ]
}