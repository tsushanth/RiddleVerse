
//
//  PuzzleCompletionView+DifficultyIntegration.swift
//  PuzzleForge
//
//  Enhanced completion view with difficulty progression
//

import SwiftUI

extension PuzzleCompletionView {
    
    // Add this method to your existing PuzzleCompletionView
    @ViewBuilder
    func difficultyProgressSection(for puzzleType: String, with stats: SessionStatistics) -> some View {
        // Only show for adaptive puzzles (Memory Squares for now)
        if puzzleType.lowercased().contains("memory") && puzzleType.lowercased().contains("squares") {
            VStack(spacing: 16) {
                difficultyHeaderView
                
                let difficultyInfo = getCompletionDifficultyInfo(for: stats)
                currentLevelDisplayView(info: difficultyInfo)
                
                if difficultyInfo.wasAdapted {
                    adaptationNotificationView(info: difficultyInfo)
                }
                
                performanceInsightsView(info: difficultyInfo)
            }
            .padding()
            .background(Color(.systemGray6))
            .cornerRadius(16)
        }
    }
    
    private var difficultyHeaderView: some View {
        HStack {
            Text("🎯 DIFFICULTY PROGRESSION")
                .font(.caption)
                .fontWeight(.bold)
                .foregroundColor(.gray)
            
            Spacer()
            
            Image(systemName: "brain.head.profile")
                .foregroundColor(.purple)
                .font(.caption)
        }
    }
    
    private func currentLevelDisplayView(info: CompletionDifficultyInfo) -> some View {
        VStack(spacing: 12) {
            // Current level and progress
            HStack {
                VStack(alignment: .leading, spacing: 4) {
                    Text("Current Level")
                        .font(.caption)
                        .foregroundColor(.secondary)
                    
                    Text(info.currentLevel)
                        .font(.title2)
                        .fontWeight(.bold)
                        .foregroundColor(.primary)
                }
                
                Spacer()
                
                VStack(alignment: .trailing, spacing: 4) {
                    Text("Progress")
                        .font(.caption)
                        .foregroundColor(.secondary)
                    
                    Text("\(info.currentIndex)/\(info.totalLevels)")
                        .font(.title3)
                        .fontWeight(.semibold)
                        .foregroundColor(.blue)
                }
            }
            
            // Progress bar with animation
            ProgressView(value: Double(info.currentIndex), total: Double(info.totalLevels))
                .tint(.blue)
                .scaleEffect(y: 2)
                .animation(.easeInOut(duration: 1.0), value: info.currentIndex)
            
            // Level characteristics
            LazyVGrid(columns: Array(repeating: GridItem(.flexible()), count: 3), spacing: 8) {
                CharacteristicView(label: "Grid", value: "\(info.gridSize)×\(info.gridSize)", color: .blue)
                CharacteristicView(label: "Targets", value: "\(info.targetCount)", color: .green)
                CharacteristicView(label: "Memory", value: "\(info.memorizeTime)s", color: .orange)
            }
        }
    }
    
    private func adaptationNotificationView(info: CompletionDifficultyInfo) -> some View {
        VStack(spacing: 12) {
            HStack {
                Image(systemName: info.adaptationIcon)
                    .foregroundColor(.white)
                    .font(.title3)
                
                Text("Difficulty Adapted!")
                    .font(.headline)
                    .fontWeight(.bold)
                    .foregroundColor(.white)
                
                Spacer()
            }
            
            Text(info.adaptationReason)
                .font(.subheadline)
                .foregroundColor(.white.opacity(0.9))
                .multilineTextAlignment(.leading)
            
            if let nextLevel = info.nextLevel {
                HStack {
                    Text("Next Level:")
                        .font(.subheadline)
                        .foregroundColor(.white.opacity(0.8))
                    
                    Text(nextLevel)
                        .font(.subheadline)
                        .fontWeight(.bold)
                        .foregroundColor(.white)
                    
                    Spacer()
                }
            }
        }
        .padding()
        .background(
            LinearGradient(
                colors: info.wasPromoted ? [.green, .blue] : [.orange, .red],
                startPoint: .leading,
                endPoint: .trailing
            )
        )
        .cornerRadius(12)
    }
    
    private func performanceInsightsView(info: CompletionDifficultyInfo) -> some View {
        VStack(alignment: .leading, spacing: 10) {
            Text("📈 Performance Insights")
                .font(.subheadline)
                .fontWeight(.semibold)
                .foregroundColor(.primary)
            
            ForEach(info.insights, id: \.self) { insight in
                HStack(alignment: .top, spacing: 8) {
                    Circle()
                        .fill(Color.blue)
                        .frame(width: 4, height: 4)
                        .padding(.top, 6)
                    
                    Text(insight)
                        .font(.caption)
                        .foregroundColor(.secondary)
                        .fixedSize(horizontal: false, vertical: true)
                    
                    Spacer()
                }
            }
            
            // Next session preview
            if let nextLevel = info.nextLevel {
                nextSessionPreview(nextLevel: nextLevel, info: info)
            }
        }
    }
    
    private func nextSessionPreview(nextLevel: String, info: CompletionDifficultyInfo) -> some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack {
                Text("🔮 Next Session Preview")
                    .font(.caption)
                    .fontWeight(.semibold)
                    .foregroundColor(.blue)
                Spacer()
            }
            
            if info.wasPromoted {
                VStack(alignment: .leading, spacing: 4) {
                    Text("You'll be playing at \(nextLevel) difficulty:")
                        .font(.caption)
                        .foregroundColor(.secondary)
                    
                    HStack {
                        Text("• Larger grid (\(info.nextGridSize ?? info.gridSize + 1)×\(info.nextGridSize ?? info.gridSize + 1))")
                            .font(.caption2)
                            .foregroundColor(.secondary)
                        Spacer()
                    }
                    
                    HStack {
                        Text("• More targets (\(info.nextTargetCount ?? info.targetCount + 1))")
                            .font(.caption2)
                            .foregroundColor(.secondary)
                        Spacer()
                    }
                    
                    HStack {
                        Text("• Faster pace (\(info.nextMemorizeTime ?? max(1, info.memorizeTime - 1))s memory time)")
                            .font(.caption2)
                            .foregroundColor(.secondary)
                        Spacer()
                    }
                }
            } else {
                Text("Keep practicing at \(info.currentLevel) to master it!")
                    .font(.caption)
                    .foregroundColor(.secondary)
            }
        }
        .padding()
        .background(Color.blue.opacity(0.05))
        .cornerRadius(8)
    }
    
    private func CharacteristicView(label: String, value: String, color: Color) -> some View {
        VStack(spacing: 4) {
            Text(value)
                .font(.subheadline)
                .fontWeight(.bold)
                .foregroundColor(color)
            
            Text(label)
                .font(.caption2)
                .foregroundColor(.secondary)
        }
        .frame(maxWidth: .infinity)
        .padding(.vertical, 8)
        .background(color.opacity(0.1))
        .cornerRadius(8)
    }
    
    // Get difficulty information for completion screen
    private func getCompletionDifficultyInfo(for stats: SessionStatistics) -> CompletionDifficultyInfo {
        let tempManager = MemorySquaresDifficultyManager()
        let currentDifficulty = tempManager.currentDifficulty
        let progress = tempManager.getDifficultyProgress()
        
        // Check if difficulty was adapted (from UserDefaults)
        let adaptationReason = UserDefaults.standard.string(forKey: "last_adaptation_reason") ?? ""
        let newDifficultyName = UserDefaults.standard.string(forKey: "last_new_difficulty") ?? ""
        let adaptationConfidence = UserDefaults.standard.float(forKey: "last_adaptation_confidence")
        
        let wasAdapted = !adaptationReason.isEmpty && adaptationConfidence > 0.5
        let wasPromoted = wasAdapted && !adaptationReason.lowercased().contains("reducing")
        
        // Get next level info
        let allLevels = MemorySquaresDifficultyManager.DifficultyLevel.allLevels
        let nextLevel = progress.current < progress.total ? allLevels[progress.current].name : nil
        let nextLevelData = progress.current < progress.total ? allLevels[progress.current] : nil
        
        // Generate insights based on session performance
        let insights = generateCompletionInsights(for: stats)
        
        return CompletionDifficultyInfo(
            currentLevel: currentDifficulty.name,
            currentIndex: progress.current,
            totalLevels: progress.total,
            gridSize: currentDifficulty.gridSize,
            targetCount: currentDifficulty.targetCount,
            memorizeTime: currentDifficulty.memorizeTime,
            timeLimit: currentDifficulty.timeLimit,
            livesAllowed: currentDifficulty.livesAllowed,
            wasAdapted: wasAdapted,
            wasPromoted: wasPromoted,
            adaptationReason: adaptationReason.isEmpty ? "Performance stable" : adaptationReason,
            adaptationIcon: wasPromoted ? "arrow.up.circle.fill" : (wasAdapted ? "arrow.down.circle.fill" : "checkmark.circle"),
            nextLevel: nextLevel,
            nextGridSize: nextLevelData?.gridSize,
            nextTargetCount: nextLevelData?.targetCount,
            nextMemorizeTime: nextLevelData?.memorizeTime,
            insights: insights
        )
    }
    
    private func generateCompletionInsights(for stats: SessionStatistics) -> [String] {
        var insights: [String] = []
        
        let accuracy = stats.totalAnswers > 0 ? Float(stats.correctAnswers) / Float(stats.totalAnswers) : 0
        
        if accuracy >= 0.9 {
            insights.append("🌟 Exceptional accuracy! You're mastering this level.")
        } else if accuracy >= 0.7 {
            insights.append("💪 Good performance! Keep improving your pattern recognition.")
        } else {
            insights.append("🎯 Focus on memorization techniques - try creating mental stories!")
        }
        
        if stats.bestStreak > 3 {
            insights.append("🔥 Amazing streak! You're in the zone!")
        }
        
        let timePerPuzzle = stats.totalAnswers > 0 ? stats.totalTimeSeconds / stats.totalAnswers : 0
        if timePerPuzzle < 30 {
            insights.append("⚡ Lightning fast responses! Consider advancing difficulty.")
        } else if timePerPuzzle > 60 {
            insights.append("⏰ Take your time to study the pattern carefully.")
        }
        
        // Add motivational insight
        insights.append("🧠 Each game strengthens your visual memory and pattern recognition!")
        
        return insights
    }
}

// MARK: - Supporting Data Structure

struct CompletionDifficultyInfo {
    let currentLevel: String
    let currentIndex: Int
    let totalLevels: Int
    let gridSize: Int
    let targetCount: Int
    let memorizeTime: Int
    let timeLimit: Int
    let livesAllowed: Int
    let wasAdapted: Bool
    let wasPromoted: Bool
    let adaptationReason: String
    let adaptationIcon: String
    let nextLevel: String?
    let nextGridSize: Int?
    let nextTargetCount: Int?
    let nextMemorizeTime: Int?
    let insights: [String]
}
