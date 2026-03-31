//
//  User.swift
//  PuzzleForge
//
//  Created by Sushanth Tiruvaipati on 5/23/25.
//
import SwiftUI

struct User: Identifiable {
    let id = UUID()
    let name: String
    let score: String
    let isHighlighted: Bool
    let avatarNumber: Int
    let rank: Int
    let iconName: String // Add this
    let backgroundColor: Color // Add this
}
