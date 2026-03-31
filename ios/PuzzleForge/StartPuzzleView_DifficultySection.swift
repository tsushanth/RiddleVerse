//
//  DifficultyInfo.swift
//  PuzzleForge
//
//  Created by Sushanth Tiruvaipati on 8/17/25.
//


//
//  StartPuzzleView+DifficultySection.swift
//  PuzzleForge
//
//  Enhanced difficulty section for StartPuzzleView
//

import SwiftUI

extension StartPuzzleView {
    
    // Add this to your existing statistics section in StartPuzzleView
    @ViewBuilder
    func difficultyProgressSection() -> some View {
        // Only show for Memory Squares puzzles
        if puzzleType.lowercased().contains("memory") && puzzleType.lowercased().contains("squares") {
            VStack(alignment: .leading, spacing: 16) {
                Text("DIFFICULTY PROGRESSION")
                    .font(.caption)
                    .fontWeight(.bold)
                    .foregroundColor(.gray)
                
                let difficultyInfo = getMemorySquaresDifficultyInfo()
                
                // Current level display
                HStack {
                    VStack(alignment: .leading, spacing: 4) {
                        Text("Current Level")
                            .font(.caption)
                            .foregroundColor(.secondary)
                        
                        Text(difficultyInfo.currentLevel)
                            .font(.title3)
                            .fontWeight(.semibold)
                            .foregroundColor(.primary)
                    }
                    
                    Spacer()
                    
                    VStack(alignment: .trailing, spacing: 4) {
                        Text("Progress")
                            .font(.caption)
                            .foregroundColor(.secondary)
                        
                        Text("\(difficultyInfo.currentIndex)/\(difficultyInfo.totalLevels)")
                            .font(.title3)
                            .fontWeight(.semibold)
                            .foregroundColor(.blue)
                    }
                }
                
                // Progress bar
                ProgressView(value: Double(difficultyInfo.currentIndex), total: Double(difficultyInfo.totalLevels))
                    .tint(.blue)
                    .scaleEffect(y: 1.5)
                
                // Status and next level info
                VStack(alignment: .leading, spacing: 8) {
                    HStack {
                        Image(systemName: difficultyInfo.statusIcon)
                            .foregroundColor(difficultyInfo.statusColor)
                            .font(.caption)
                        
                        Text(difficultyInfo.statusMessage)
                            .font(.caption)
                            .foregroundColor(difficultyInfo.statusColor)
                            .fontWeight(.medium)
                        
                        Spacer()
                    }
                    
                    if let nextLevel = difficultyInfo.nextLevel {
                        HStack {
                            Text("Next:")
                                .font(.caption2)
                                .foregroundColor(.secondary)
                            
                            Text(nextLevel)
                                .font(.caption2)
                                .foregroundColor(.blue)
                                .fontWeight(.semibold)
                            
                            Spacer()
                        }
                    }
                }
                
                // Level details
                VStack(alignment: .leading, spacing: 6) {
                    Text("CURRENT LEVEL DETAILS")
                        .font(.caption2)
                        .fontWeight(.bold)
                        .foregroundColor(.gray)
                    
                    HStack {
                        DetailItem(label: "Grid Size", value: "\(difficultyInfo.gridSize)×\(difficultyInfo.gridSize)")
                        Spacer()
                        DetailItem(label: "Targets", value: "\(difficultyInfo.targetCount)")
                        Spacer()
                        DetailItem(label: "Memory Time", value: "\(difficultyInfo.memorizeTime)s")
                    }
                    
                    HStack {
                        DetailItem(label: "Lives", value: "\(difficultyInfo.livesAllowed)")
                        Spacer()
                        DetailItem(label: "Time Limit", value: formatTime(difficultyInfo.timeLimit))
                        Spacer()
                        DetailItem(label: "Base Points", value: "\(difficultyInfo.basePoints)")
                    }
                }
                .padding(.top, 8)
            }
            .padding()
            .background(Color(.systemGray6))
            .cornerRadius(12)
        }
    }
    
    // Helper view for detail items
    private func DetailItem(label: String, value: String) -> some View {
        VStack(alignment: .leading, spacing: 2) {
            Text(label)
                .font(.caption2)
                .foregroundColor(.secondary)
            
            Text(value)
                .font(.caption)
                .fontWeight(.semibold)
                .foregroundColor(.primary)
        }
    }
    
    // Get difficulty information for Memory Squares
    private func getMemorySquaresDifficultyInfo() -> DifficultyInfo {
        // Create a temporary difficulty manager to get current info
        let tempManager = MemorySquaresDifficultyManager()
        let currentDifficulty = tempManager.currentDifficulty
        let progress = tempManager.getDifficultyProgress()
        
        // Determine status based on performance (simplified)
        let recentPerformance = UserDefaults.standard.float(forKey: "memory_squares_recent_accuracy")
        let statusInfo = getStatusInfo(accuracy: recentPerformance, currentIndex: progress.current, totalLevels: progress.total)
        
        // Get next level name
        let allLevels = MemorySquaresDifficultyManager.DifficultyLevel.allLevels
        let nextLevel = progress.current < progress.total ? allLevels[progress.current].name : nil
        
        return DifficultyInfo(
            currentLevel: currentDifficulty.name,
            currentIndex: progress.current,
            totalLevels: progress.total,
            statusMessage: statusInfo.message,
            statusIcon: statusInfo.icon,
            statusColor: statusInfo.color,
            nextLevel: nextLevel,
            gridSize: currentDifficulty.gridSize,
            targetCount: currentDifficulty.targetCount,
            memorizeTime: currentDifficulty.memorizeTime,
            timeLimit: currentDifficulty.timeLimit,
            livesAllowed: currentDifficulty.livesAllowed,
            basePoints: currentDifficulty.basePoints
        )
    }
    
    private func getStatusInfo(accuracy: Float, currentIndex: Int, totalLevels: Int) -> (message: String, icon: String, color: Color) {
        if accuracy >= 0.85 && currentIndex < totalLevels {
            return ("Ready to advance! 🚀", "arrow.up.circle.fill", .green)
        } else if accuracy <= 0.3 && currentIndex > 1 {
            return ("Building confidence 💪", "heart.fill", .orange)
        } else if currentIndex == totalLevels {
            return ("Master level achieved! 🏆", "crown.fill", .yellow)
        } else {
            return ("Progressing well ✨", "checkmark.circle", .blue)
        }
    }
    
    private func formatTime(_ seconds: Int) -> String {
        let minutes = seconds / 60
        let remainingSeconds = seconds % 60
        if minutes > 0 {
            return "\(minutes):\(String(format: "%02d", remainingSeconds))"
        } else {
            return "\(remainingSeconds)s"
        }
    }
}

// MARK: - Supporting Data Structure

struct DifficultyInfo {
    let currentLevel: String
    let currentIndex: Int
    let totalLevels: Int
    let statusMessage: String
    let statusIcon: String
    let statusColor: Color
    let nextLevel: String?
    let gridSize: Int
    let targetCount: Int
    let memorizeTime: Int
    let timeLimit: Int
    let livesAllowed: Int
    let basePoints: Int
}
