//
//  UserPuzzleStats.swift
//  PuzzleForge
//
//  Created by Sushanth Tiruvaipati on 8/3/25.
//


import Foundation
import FirebaseAuth

// MARK: - UI Data Model (from Android)
struct PuzzleStatistics {
    let highScore: Int
    let difficulty: String
    let timesTrained: Float
    let wins: Int
    let topScores: [Int]
    let totalPlays: Int
    let averageScore: Int
    let winRate: Float
    let longestStreak: Int
    let totalTimeSpent: Float
}

