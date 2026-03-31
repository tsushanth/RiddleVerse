//
//  DifficultySettingsManager.swift
//  PuzzleForge
//
//  Created by Sushanth Tiruvaipati on 6/17/25.
//


//
//  DifficultySettingsManager.swift
//  PuzzleForge
//
//  Created by Assistant on 6/17/25.
//

import Foundation
import SwiftUI

class DifficultySettingsManager: ObservableObject {
    @Published var selectedDifficulty: String = "Easy"
    
    private let userDefaults = UserDefaults.standard
    private let difficultyKey = "user_difficulty_preference"
    
    init() {
        loadDifficulty()
    }
    
    func saveDifficulty(_ difficulty: String) {
        selectedDifficulty = difficulty
        userDefaults.set(difficulty, forKey: difficultyKey)
        print("💾 Saved difficulty preference: \(difficulty)")
    }
    
    private func loadDifficulty() {
        selectedDifficulty = userDefaults.string(forKey: difficultyKey) ?? "Easy"
        print("📱 Loaded difficulty preference: \(selectedDifficulty)")
    }
    
    func resetToDefault() {
        saveDifficulty("Easy")
    }
}

// MARK: - Difficulty Settings View Component
struct DifficultySettingsView: View {
    @StateObject private var difficultyManager = DifficultySettingsManager()
    
    let difficulties = ["Easy", "Medium", "Hard"]
    
    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            HStack {
                Image(systemName: "slider.horizontal.3")
                    .foregroundColor(.orange)
                    .frame(width: 30)
                Text("Default Difficulty")
                    .font(.headline)
                Spacer()
            }
            
            Text("Set your preferred difficulty level for all puzzles")
                .font(.caption)
                .foregroundColor(.gray)
                .padding(.leading, 30)
            
            VStack(spacing: 0) {
                ForEach(difficulties, id: \.self) { difficulty in
                    Button(action: {
                        difficultyManager.saveDifficulty(difficulty)
                    }) {
                        HStack {
                            Text(difficulty)
                                .foregroundColor(.primary)
                            
                            Spacer()
                            
                            if difficultyManager.selectedDifficulty == difficulty {
                                Image(systemName: "checkmark")
                                    .foregroundColor(.orange)
                                    .font(.system(size: 16, weight: .bold))
                            }
                        }
                        .padding()
                    }
                    
                    if difficulty != difficulties.last {
                        Divider()
                    }
                }
            }
            .background(Color(.systemGray6))
            .cornerRadius(12)
            
            // Info text
            HStack {
                Image(systemName: "info.circle")
                    .foregroundColor(.blue)
                    .font(.caption)
                Text("You can still change difficulty when starting any puzzle")
                    .font(.caption2)
                    .foregroundColor(.gray)
            }
            .padding(.leading, 30)
            .padding(.top, 4)
        }
    }
}