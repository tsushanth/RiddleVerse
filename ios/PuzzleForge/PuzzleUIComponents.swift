//
//  PuzzleUIComponents.swift
//  PuzzleForge
//
//  Created by Sushanth Tiruvaipati on 8/6/25.
//

import SwiftUI

// MARK: - Level Progress Bar Component

struct LevelProgressBar: View {
    let level: UserLevel
    let showLabel: Bool
    let compact: Bool
    
    init(level: UserLevel, showLabel: Bool = true, compact: Bool = false) {
        self.level = level
        self.showLabel = showLabel
        self.compact = compact
    }
    
    var body: some View {
        VStack(alignment: .center, spacing: compact ? 2 : 4) {
            if showLabel {
                HStack(spacing: 8) {
                    Text("Level \(level.level)")
                        .font(.system(size: compact ? 12 : 14, weight: .bold))
                        .foregroundColor(.white)
                    
                    if !compact {
                        Text("\(level.currentXP)/\(level.xpToNextLevel) XP")
                            .font(.system(size: 10))
                            .foregroundColor(.white.opacity(0.8))
                    }
                }
            }
            
            // Progress bar with GeometryReader
            GeometryReader { geometry in
                ZStack(alignment: .leading) {
                    Rectangle()
                        .fill(Color.white.opacity(0.3))
                        .frame(height: compact ? 4 : 6)
                        .cornerRadius(compact ? 2 : 3)
                    
                    Rectangle()
                        .fill(Color.cyan)
                        .frame(
                            width: geometry.size.width * CGFloat(level.progressPercentage),
                            height: compact ? 4 : 6
                        )
                        .cornerRadius(compact ? 2 : 3)
                }
            }
            .frame(height: compact ? 4 : 6)
            
            if compact && showLabel {
                Text("\(level.currentXP)/\(level.xpToNextLevel)")
                    .font(.system(size: 8))
                    .foregroundColor(.white.opacity(0.7))
            }
        }
    }
}

// MARK: - Streak Display Component

struct StreakDisplay: View {
    let streakInfo: StreakInfo
    let showDailyStreak: Bool
    let compact: Bool
    
    init(streakInfo: StreakInfo, showDailyStreak: Bool = false, compact: Bool = false) {
        self.streakInfo = streakInfo
        self.showDailyStreak = showDailyStreak
        self.compact = compact
    }
    
    var body: some View {
        if streakInfo.currentStreak > 0 || (showDailyStreak && hasDailyStreak) {
            HStack(spacing: compact ? 6 : 8) {
                // Question/Current streak
                if streakInfo.currentStreak > 0 {
                    HStack(spacing: compact ? 2 : 4) {
                        Text(compact ? "🔥" : "⚡")
                            .font(.system(size: compact ? 12 : 16))
                        
                        Text("\(streakInfo.currentStreak)")
                            .font(.system(size: compact ? 10 : 14, weight: .bold))
                            .foregroundColor(compact ? .orange : Color(red: 1.0, green: 0.435, blue: 0.0))
                        
                        if hasStreakBonus && !compact {
                            Text("×\(String(format: "%.1f", streakMultiplier))")
                                .font(.system(size: 10, weight: .medium))
                                .foregroundColor(Color(red: 1.0, green: 0.596, blue: 0.0))
                        }
                    }
                }
                
                // Daily streak (if available and requested)
                if showDailyStreak && hasDailyStreak {
                    HStack(spacing: 4) {
                        Text("🔥")
                            .font(.system(size: compact ? 14 : 16))
                        
                        Text("\(dailyStreakValue)d")
                            .font(.system(size: compact ? 12 : 14, weight: .bold))
                            .foregroundColor(Color(red: 1.0, green: 0.341, blue: 0.133))
                    }
                }
            }
        }
    }
    
    // MARK: - Computed Properties for StreakInfo Compatibility
    
    private var hasDailyStreak: Bool {
        // Try to access dailyStreak property if it exists
        if let dailyStreak = getDailyStreak() {
            return dailyStreak > 0
        }
        return false
    }
    
    private var dailyStreakValue: Int {
        return getDailyStreak() ?? 0
    }
    
    private var hasStreakBonus: Bool {
        // Try to access hasStreakBonus property if it exists
        return getHasStreakBonus() ?? false
    }
    
    private var streakMultiplier: Float {
        // Try to access streakMultiplier property if it exists
        return getStreakMultiplier() ?? 1.0
    }
    
    // MARK: - Helper Methods for Property Access
    
    private func getDailyStreak() -> Int? {
        // Use Mirror to safely access dailyStreak property if it exists
        let mirror = Mirror(reflecting: streakInfo)
        for child in mirror.children {
            if child.label == "dailyStreak" {
                return child.value as? Int
            }
        }
        return nil
    }
    
    private func getHasStreakBonus() -> Bool? {
        let mirror = Mirror(reflecting: streakInfo)
        for child in mirror.children {
            if child.label == "hasStreakBonus" {
                return child.value as? Bool
            }
        }
        return nil
    }
    
    private func getStreakMultiplier() -> Float? {
        let mirror = Mirror(reflecting: streakInfo)
        for child in mirror.children {
            if child.label == "streakMultiplier" {
                return child.value as? Float
            }
        }
        return nil
    }
}

// MARK: - Enhanced Streak Display

struct EnhancedStreakDisplay: View {
    let streakInfo: StreakDisplayInfo
    let showBonusInfo: Bool
    
    init(streakInfo: StreakDisplayInfo, showBonusInfo: Bool = false) {
        self.streakInfo = streakInfo
        self.showBonusInfo = showBonusInfo
    }
    
    var body: some View {
        HStack(spacing: 12) {
            // Question streak
            if streakInfo.currentQuestionStreak > 0 {
                StreakItem(
                    emoji: "⚡",
                    value: "\(streakInfo.currentQuestionStreak)",
                    label: showBonusInfo ? "×\(String(format: "%.1f", streakInfo.questionStreakMultiplier))" : nil,
                    color: Color(red: 1.0, green: 0.435, blue: 0.0)
                )
            }
            
            // Daily streak
            if streakInfo.currentDailyStreak > 0 {
                StreakItem(
                    emoji: "🔥",
                    value: "\(streakInfo.currentDailyStreak)d",
                    label: showBonusInfo && streakInfo.dailyStreakBonus > 1.0 ? "×\(String(format: "%.1f", streakInfo.dailyStreakBonus))" : nil,
                    color: Color(red: 1.0, green: 0.341, blue: 0.133)
                )
            }
        }
    }
}

private struct StreakItem: View {
    let emoji: String
    let value: String
    let label: String?
    let color: Color
    
    var body: some View {
        HStack(spacing: 4) {
            Text(emoji)
                .font(.system(size: 16))
            
            Text(value)
                .font(.system(size: 14, weight: .bold))
                .foregroundColor(color)
            
            if let label = label {
                Text(label)
                    .font(.system(size: 10, weight: .medium))
                    .foregroundColor(color.opacity(0.8))
            }
        }
    }
}

// MARK: - Puzzle Header Component

struct PuzzleHeader: View {
    let level: UserLevel
    let streakInfo: StreakInfo
    let currentScore: Int
    let backgroundColor: Color
    let onBack: (() -> Void)?
    let title: String?
    
    init(
        level: UserLevel,
        streakInfo: StreakInfo,
        currentScore: Int = 0,
        backgroundColor: Color = .clear,
        onBack: (() -> Void)? = nil,
        title: String? = nil
    ) {
        self.level = level
        self.streakInfo = streakInfo
        self.currentScore = currentScore
        self.backgroundColor = backgroundColor
        self.onBack = onBack
        self.title = title
    }
    
    var body: some View {
        VStack {
            HStack {
                // Left side - Back button and title
                HStack(spacing: 8) {
                    if let onBack = onBack {
                        Button(action: onBack) {
                            Image(systemName: "arrow.left")
                                .font(.system(size: 18, weight: .medium))
                                .foregroundColor(.white)
                        }
                    }
                    
                    if let title = title {
                        Text(title)
                            .font(.system(size: 18, weight: .bold))
                            .foregroundColor(.white)
                    }
                }
                
                Spacer()
                
                // Center - Level Progress
                LevelProgressBar(level: level, compact: true)
                    .frame(width: 120)
                
                Spacer()
                
                // Right side - Streak and Score
                VStack(alignment: .trailing) {
                    if currentScore > 0 {
                        Text("\(currentScore) pts")
                            .font(.system(size: 14, weight: .bold))
                            .foregroundColor(Color(red: 1.0, green: 0.843, blue: 0.0))
                    }
                    
                    StreakDisplay(streakInfo: streakInfo, compact: true)
                }
            }
            .padding(16)
            .background(
                LinearGradient(
                    colors: backgroundColor != .clear ?
                        [backgroundColor, backgroundColor.opacity(0.8)] :
                        [Color.clear, Color.clear],
                    startPoint: .leading,
                    endPoint: .trailing
                )
            )
        }
    }
}

// MARK: - Puzzle Progress Card

struct PuzzleProgressCard: View {
    let questionsAnswered: Int
    let totalQuestions: Int
    let correctAnswers: Int
    let currentScore: Int
    
    var body: some View {
        VStack {
            HStack {
                ProgressStatItem(
                    label: "Progress",
                    value: "\(questionsAnswered)/\(totalQuestions)",
                    icon: "📊",
                    color: Color(red: 0.129, green: 0.588, blue: 0.953)
                )
                
                Spacer()
                
                ProgressStatItem(
                    label: "Correct",
                    value: "\(correctAnswers)",
                    icon: "✅",
                    color: Color(red: 0.298, green: 0.686, blue: 0.314)
                )
                
                Spacer()
                
                ProgressStatItem(
                    label: "Score",
                    value: "\(currentScore)",
                    icon: "⭐",
                    color: Color(red: 1.0, green: 0.596, blue: 0.0)
                )
                
                Spacer()
                
                ProgressStatItem(
                    label: "Accuracy",
                    value: "\(questionsAnswered > 0 ? Int(Float(correctAnswers) / Float(questionsAnswered) * 100) : 0)%",
                    icon: "🎯",
                    color: Color(red: 0.612, green: 0.153, blue: 0.690)
                )
            }
            .padding(16)
            .background(Color.white.opacity(0.9))
            .cornerRadius(12)
            .shadow(color: .black.opacity(0.1), radius: 4, x: 0, y: 2)
        }
    }
}

// MARK: - Progress-specific StatItem (for PuzzleProgressCard)

private struct ProgressStatItem: View {
    let label: String
    let value: String
    let icon: String
    let color: Color
    
    var body: some View {
        VStack(spacing: 4) {
            Text(icon)
                .font(.system(size: 16))
            
            Text(value)
                .font(.system(size: 14, weight: .bold))
                .foregroundColor(color)
            
            Text(label)
                .font(.system(size: 10))
                .foregroundColor(.gray)
        }
    }
}

// MARK: - Achievement Badge Component

struct AchievementBadge: View {
    let achievement: Achievement
    let size: CGFloat
    
    init(achievement: Achievement, size: CGFloat = 60) {
        self.achievement = achievement
        self.size = size
    }
    
    var body: some View {
        VStack(spacing: 4) {
            ZStack {
                Circle()
                    .fill(achievement.isUnlocked ?
                          LinearGradient(colors: [.yellow, .orange], startPoint: .top, endPoint: .bottom) :
                          LinearGradient(colors: [.gray.opacity(0.3)], startPoint: .top, endPoint: .bottom))
                    .frame(width: size, height: size)
                
                Text(achievement.icon)
                    .font(.system(size: size * 0.4))
                    .grayscale(achievement.isUnlocked ? 0 : 1)
                
                if !achievement.isUnlocked && achievement.maxProgress > 1 {
                    Circle()
                        .stroke(Color.blue, lineWidth: 3)
                        .frame(width: size, height: size)
                        .overlay(
                            Text("\(achievement.progress)/\(achievement.maxProgress)")
                                .font(.system(size: size * 0.15, weight: .bold))
                                .foregroundColor(.blue)
                                .background(Color.white.opacity(0.8))
                                .cornerRadius(4)
                                .offset(y: size * 0.3)
                        )
                }
            }
            
            Text(achievement.title)
                .font(.system(size: size * 0.2, weight: .medium))
                .foregroundColor(achievement.isUnlocked ? .primary : .secondary)
                .multilineTextAlignment(.center)
                .lineLimit(2)
        }
        .frame(width: size + 20)
    }
}

// MARK: - Tier Badge Component

struct TierBadge: View {
    let tierInfo: TierInfo
    let size: CGFloat
    
    init(tierInfo: TierInfo, size: CGFloat = 80) {
        self.tierInfo = tierInfo
        self.size = size
    }
    
    private var tierColor: Color {
        switch tierInfo.currentTier.lowercased() {
        case "bronze":
            return Color(red: 0.804, green: 0.498, blue: 0.196)
        case "silver":
            return Color(red: 0.753, green: 0.753, blue: 0.753)
        case "gold":
            return Color(red: 1.0, green: 0.843, blue: 0.0)
        case "platinum":
            return Color(red: 0.898, green: 0.898, blue: 0.898)
        case "diamond":
            return Color(red: 0.722, green: 0.898, blue: 1.0)
        case "master":
            return Color(red: 0.502, green: 0.0, blue: 0.502)
        default:
            return .gray
        }
    }
    
    private var tierIcon: String {
        switch tierInfo.currentTier.lowercased() {
        case "bronze": return "🥉"
        case "silver": return "🥈"
        case "gold": return "🥇"
        case "platinum": return "💎"
        case "diamond": return "💠"
        case "master": return "👑"
        default: return "🏅"
        }
    }
    
    var body: some View {
        VStack(spacing: 8) {
            ZStack {
                Circle()
                    .fill(
                        LinearGradient(
                            colors: [tierColor, tierColor.opacity(0.7)],
                            startPoint: .top,
                            endPoint: .bottom
                        )
                    )
                    .frame(width: size, height: size)
                    .shadow(color: tierColor.opacity(0.3), radius: 8, x: 0, y: 4)
                
                Text(tierIcon)
                    .font(.system(size: size * 0.4))
                
                // Progress ring
                Circle()
                    .stroke(Color.white.opacity(0.3), lineWidth: 4)
                    .frame(width: size - 8, height: size - 8)
                
                Circle()
                    .trim(from: 0, to: CGFloat(tierInfo.progressPercentage))
                    .stroke(Color.white, lineWidth: 4)
                    .frame(width: size - 8, height: size - 8)
                    .rotationEffect(.degrees(-90))
            }
            
            VStack(spacing: 2) {
                Text(tierInfo.currentTier)
                    .font(.system(size: size * 0.2, weight: .bold))
                    .foregroundColor(.primary)
                
                Text("Level \(tierInfo.currentLevel)")
                    .font(.system(size: size * 0.15))
                    .foregroundColor(.secondary)
                
                if tierInfo.progressPercentage < 1.0 {
                    Text("\(tierInfo.pointsInTier)/\(tierInfo.pointsToNextTier + tierInfo.pointsInTier)")
                        .font(.system(size: size * 0.12))
                        .foregroundColor(.secondary)
                }
            }
        }
    }
}

// MARK: - Quick Stats Component

struct QuickStatsCard: View {
    let globalStats: GlobalStatsInfo
    
    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text("Quick Stats")
                .font(.headline)
                .foregroundColor(.primary)
            
            HStack {
                StatItem(label: "Total XP", value: "\(globalStats.totalXP)", emoji: "⭐")
                Spacer()
                StatItem(label: "Games Played", value: "\(globalStats.totalGamesPlayed)", emoji: "🎮")
                Spacer()
                StatItem(label: "Hours Played", value: String(format: "%.1f", globalStats.totalTimeHours), emoji: "⏰")
            }
            
            HStack {
                StatItem(label: "Current Level", value: "\(globalStats.currentLevel)", emoji: "🏆")
                Spacer()
                StatItem(label: "Rank", value: globalStats.rank, emoji: "📊")
                Spacer()
                StatItem(label: "Member Since", value: String(globalStats.memberSince.prefix(6)), emoji: "📅")
            }
        }
        .padding(16)
        .background(Color(.systemGray6))
        .cornerRadius(12)
    }
}
