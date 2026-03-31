//
//  AchievementDashboardView.swift
//  PuzzleForge
//
//  Created by Sushanth Tiruvaipati on 6/3/25.
//
import SwiftUI

struct AchievementDashboardView: View {
    @StateObject private var dataProvider = DashboardDataProvider()
    @State private var dashboardData: DashboardData?
    @State private var isLoading = true
    @State private var timeLeft: TimeInterval = 0
    
    var userEmail: String
    
    var body: some View {
        NavigationView {
            Group {
                if isLoading {
                    ProgressView("Loading Dashboard...")
                        .padding()
                } else if let data = dashboardData {
                    ScrollView {
                        LazyVStack(alignment: .leading, spacing: 24) {
                            // Tier Progress Section
                            TierProgressSection(
                                tierInfo: data.tierInfo,
                                globalStats: data.globalStats
                            )
                            
                            // Weekly Challenge Section
                            WeeklyChallengeSection(
                                weeklyChallenge: data.weeklyChallenge
                            )
                            
                            // Seasonal Event Section (if available)
                            if let seasonalEvent = data.seasonalEvent {
                                SeasonalEventSection(event: seasonalEvent)
                            }
                            
                            // Statistics Overview
                            StatisticsOverviewSection(
                                globalStats: data.globalStats,
                                streakInfo: data.streakInfo
                            )
                            
                            // Puzzle Performance Section
                            if !data.puzzlePerformance.isEmpty {
                                PuzzlePerformanceSection(
                                    puzzleStats: data.puzzlePerformance
                                )
                            }
                            
                            // Daily Streak Section
                            DailyStreakSection(streakInfo: data.streakInfo)
                            
                            // Time Analytics Section
                            TimeAnalyticsSection(
                                timeAnalytics: data.timeAnalytics,
                                globalStats: data.globalStats,
                                puzzleStats: data.puzzlePerformance
                            )
                            
                            // Badge Collection Section
                            BadgeCollectionSection(achievements: data.achievements)
                            
                            // Welcome message for new users
                            if data.globalStats.totalGamesPlayed == 0 {
                                WelcomeNewUserSection()
                            }
                        }
                        .padding()
                    }
                } else {
                    AdErrorView {
                        loadDashboardData()
                    }
                }
            }
            .navigationTitle("Progress Dashboard")
            .navigationBarTitleDisplayMode(.large)
        }
        .onAppear {
            loadDashboardData()
        }
        .refreshable {
            loadDashboardData()
        }
    }
    
    private func loadDashboardData() {
        isLoading = true
        
        // Simulate async data loading or replace with actual network call
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.5) {
            dashboardData = dataProvider.getDashboardData()
            isLoading = false
        }
    }
}

// MARK: - Tier Progress Section
struct TierProgressSection: View {
    let tierInfo: TierInfo
    let globalStats: GlobalStatsInfo
    
    var body: some View {
        VStack(alignment: .leading, spacing: 16) {
            Text("🏆 Tier Progress")
                .font(.title2)
                .fontWeight(.bold)
            
            TierProgressCard(tierInfo: tierInfo, globalStats: globalStats)
        }
    }
}

struct TierProgressCard: View {
    let tierInfo: TierInfo
    let globalStats: GlobalStatsInfo
    
    var body: some View {
        VStack(alignment: .leading, spacing: 16) {
            HStack {
                VStack(alignment: .leading) {
                    HStack {
                        Text(getTierEmoji(tier: tierInfo.currentTier))
                            .font(.largeTitle)
                        Text(tierInfo.currentTier)
                            .font(.title)
                            .fontWeight(.bold)
                            .foregroundColor(.white)
                    }
                    Text("Level \(tierInfo.currentLevel)")
                        .font(.subheadline)
                        .foregroundColor(.white.opacity(0.8))
                }
                
                Spacer()
                
                VStack(alignment: .trailing) {
                    Text("\(tierInfo.totalXP) XP")
                        .font(.headline)
                        .fontWeight(.bold)
                        .foregroundColor(.white)
                    
                    if tierInfo.currentTier != tierInfo.nextTier {
                        Text("\(tierInfo.pointsToNextTier - tierInfo.pointsInTier) to \(tierInfo.nextTier)")
                            .font(.caption)
                            .foregroundColor(.white.opacity(0.8))
                    }
                }
            }
            
            if tierInfo.currentTier != tierInfo.nextTier {
                ProgressView(value: tierInfo.progressPercentage)
                    .progressViewStyle(LinearProgressViewStyle(tint: .white))
                    .background(Color.white.opacity(0.3))
                    .cornerRadius(4)
                
                Text("Next Tier Rewards:")
                    .font(.subheadline)
                    .fontWeight(.medium)
                    .foregroundColor(.white)
                
                ScrollView(.horizontal, showsIndicators: false) {
                    HStack(spacing: 8) {
                        ForEach(tierInfo.nextTierRewards, id: \.self) { reward in
                            RewardChip(reward: reward)
                        }
                    }
                    .padding(.horizontal, 4)
                }
            } else {
                Text("🎉 Maximum tier achieved! You're a puzzle master!")
                    .font(.subheadline)
                    .fontWeight(.medium)
                    .foregroundColor(.white)
                    .multilineTextAlignment(.center)
                    .frame(maxWidth: .infinity)
            }
        }
        .padding(20)
        .background(getTierColor(tier: tierInfo.currentTier))
        .cornerRadius(16)
    }
}

// MARK: - Weekly Challenge Section
struct WeeklyChallengeSection: View {
    let weeklyChallenge: WeeklyChallengeInfo
    @State private var timeLeft: TimeInterval = 0
    
    var body: some View {
        VStack(alignment: .leading, spacing: 16) {
            Text("🎯 Weekly Challenge")
                .font(.title2)
                .fontWeight(.bold)
            
            WeeklyChallengeCard(challenge: weeklyChallenge, timeLeft: timeLeft)
        }
        .onAppear {
            timeLeft = weeklyChallenge.timeUntilReset
            startTimer()
        }
    }
    
    private func startTimer() {
        Timer.scheduledTimer(withTimeInterval: 1, repeats: true) { timer in
            if timeLeft > 0 {
                timeLeft -= 1
            } else {
                timer.invalidate()
            }
        }
    }
}

struct WeeklyChallengeCard: View {
    let challenge: WeeklyChallengeInfo
    let timeLeft: TimeInterval
    
    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            HStack {
                Text(challenge.isCompleted ? "🏆 Weekly Challenge Complete!" : "🎯 Weekly Challenge")
                    .font(.headline)
                    .fontWeight(.bold)
                    .foregroundColor(challenge.isCompleted ? .white : Color.green)
                
                Spacer()
                
                Text(formatTimeLeft(timeLeft))
                    .font(.caption)
                    .fontWeight(.medium)
                    .foregroundColor(.orange)
            }
            
            Text("Complete \(challenge.weeklyGoal) puzzles this week")
                .font(.body)
                .foregroundColor(challenge.isCompleted ? .white : .primary)
            
            ProgressView(value: challenge.progressPercentage)
                .progressViewStyle(LinearProgressViewStyle(tint: challenge.isCompleted ? .white : .green))
                .background(Color.gray.opacity(0.3))
                .cornerRadius(4)
            
            HStack {
                Text("\(challenge.weeklyProgress)/\(challenge.weeklyGoal) completed")
                    .font(.caption)
                    .foregroundColor(challenge.isCompleted ? .white : .primary)
                
                Spacer()
                
                Text(challenge.isCompleted ? "✅ \(challenge.weeklyReward) XP earned!" : "Reward: \(challenge.weeklyReward) XP")
                    .font(.caption)
                    .fontWeight(.medium)
                    .foregroundColor(challenge.isCompleted ? .white : .green)
            }
        }
        .padding(20)
        .background(challenge.isCompleted ? Color.green : Color.green.opacity(0.1))
        .cornerRadius(16)
    }
}

// MARK: - Seasonal Event Section
struct SeasonalEventSection: View {
    let event: SeasonalEventInfo
    
    var body: some View {
        VStack(alignment: .leading, spacing: 16) {
            SeasonalEventCard(event: event)
        }
    }
}

struct SeasonalEventCard: View {
    let event: SeasonalEventInfo
    
    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text("\(event.theme) \(event.eventName)")
                .font(.headline)
                .fontWeight(.bold)
                .foregroundColor(event.isCompleted ? .white : .orange)
            
            Text(event.description)
                .font(.subheadline)
                .foregroundColor(event.isCompleted ? .white.opacity(0.9) : .secondary)
            
            ProgressView(value: event.progressPercentage)
                .progressViewStyle(LinearProgressViewStyle(tint: event.isCompleted ? .white : .orange))
                .background(Color.gray.opacity(0.3))
                .cornerRadius(4)
            
            HStack {
                Text(event.isCompleted ? "🎉 Event Complete!" : "\(event.progress)/\(event.maxProgress)")
                    .font(.caption)
                    .foregroundColor(event.isCompleted ? .white : .primary)
                
                Spacer()
                
                Text("Ends \(formatEventTime(event.endTime))")
                    .font(.caption)
                    .foregroundColor(event.isCompleted ? .white.opacity(0.8) : .gray)
            }
            
            // Event Rewards
            Text("Rewards:")
                .font(.caption)
                .fontWeight(.medium)
                .foregroundColor(event.isCompleted ? .white : .primary)
            
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 8) {
                    ForEach(event.rewards, id: \.self) { reward in
                        RewardChip(reward: reward)
                    }
                }
                .padding(.horizontal, 4)
            }
        }
        .padding(20)
        .background(event.isCompleted ? Color.orange : Color.orange.opacity(0.1))
        .cornerRadius(16)
    }
}

// MARK: - Statistics Overview Section
struct StatisticsOverviewSection: View {
    let globalStats: GlobalStatsInfo
    let streakInfo: StreakDisplayInfo
    
    var body: some View {
        VStack(alignment: .leading, spacing: 16) {
            Text("📊 Your Statistics")
                .font(.title2)
                .fontWeight(.bold)
            
            StatisticsOverviewCard(globalStats: globalStats, streakInfo: streakInfo)
        }
    }
}

struct StatisticsOverviewCard: View {
    let globalStats: GlobalStatsInfo
    let streakInfo: StreakDisplayInfo
    
    var body: some View {
        VStack(spacing: 16) {
            // First row
            HStack {
                StatItem(label: "Games", value: "\(globalStats.totalGamesPlayed)", emoji: "🎮")
                StatItem(label: "Level", value: "\(globalStats.currentLevel)", emoji: "🏆")
                StatItem(label: "Total XP", value: "\(globalStats.totalXP)", emoji: "⭐")
                StatItem(label: "Streak", value: "\(streakInfo.currentQuestionStreak)", emoji: "🔥")
            }
            
            // Second row
            HStack {
                StatItem(label: "Hours", value: String(format: "%.1f", globalStats.totalTimeHours), emoji: "⏰")
                StatItem(label: "Avg XP", value: "\(globalStats.averageXPPerGame)", emoji: "📊")
                StatItem(label: "Daily", value: "\(streakInfo.currentDailyStreak)", emoji: "📅")
                StatItem(label: "Rank", value: globalStats.rank, emoji: "🏅")
            }
        }
        .padding(20)
        .background(Color.purple.opacity(0.1))
        .cornerRadius(16)
    }
}

// MARK: - Puzzle Performance Section
struct PuzzlePerformanceSection: View {
    let puzzleStats: [PuzzlePerformanceInfo]
    
    var body: some View {
        VStack(alignment: .leading, spacing: 16) {
            Text("🎮 Puzzle Performance")
                .font(.title2)
                .fontWeight(.bold)
            
            PuzzlePerformanceCard(puzzleStats: puzzleStats)
        }
    }
}

struct PuzzlePerformanceCard: View {
    let puzzleStats: [PuzzlePerformanceInfo]
    
    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            ForEach(Array(puzzleStats.prefix(5).enumerated()), id: \.offset) { index, stat in
                PuzzleStatRow(stat: stat)
                if index < min(4, puzzleStats.count - 1) {
                    Divider()
                }
            }
            
            if puzzleStats.isEmpty {
                Text("Complete some puzzles to see your performance stats!")
                    .font(.subheadline)
                    .foregroundColor(.gray)
                    .multilineTextAlignment(.center)
                    .frame(maxWidth: .infinity)
            }
        }
        .padding(20)
        .background(Color.blue.opacity(0.1))
        .cornerRadius(16)
    }
}

struct PuzzleStatRow: View {
    let stat: PuzzlePerformanceInfo
    
    var body: some View {
        HStack {
            VStack(alignment: .leading) {
                Text(stat.puzzleType)
                    .font(.subheadline)
                    .fontWeight(.medium)
                
                Text("\(stat.totalSolved) solved • \(Int(stat.accuracy * 100))% accuracy • High: \(stat.highScore)")
                    .font(.caption)
                    .foregroundColor(.gray)
            }
            
            Spacer()
            
            Text("\(Int(stat.averageTime))s avg")
                .font(.caption)
                .foregroundColor(.blue)
                .fontWeight(.medium)
        }
    }
}

// MARK: - Daily Streak Section
struct DailyStreakSection: View {
    let streakInfo: StreakDisplayInfo
    
    var body: some View {
        VStack(alignment: .leading, spacing: 16) {
            Text("🔥 Daily Streak")
                .font(.title2)
                .fontWeight(.bold)
            
            DailyStreakCard(streakInfo: streakInfo)
        }
    }
}

struct DailyStreakCard: View {
    let streakInfo: StreakDisplayInfo
    
    var body: some View {
        VStack(spacing: 16) {
            HStack {
                StatItem(
                    label: "Current",
                    value: "\(streakInfo.currentDailyStreak)",
                    emoji: "🔥",
                    textColor: streakInfo.streakActive ? .white : .primary
                )
                StatItem(
                    label: "Best Ever",
                    value: "\(streakInfo.longestDailyStreak)",
                    emoji: "🏆",
                    textColor: streakInfo.streakActive ? .white : .primary
                )
                StatItem(
                    label: "Status",
                    value: streakInfo.hasPlayedToday ? "Active" : "Inactive",
                    emoji: "✅",
                    textColor: streakInfo.streakActive ? .white : .primary
                )
            }
            
            Text(streakInfo.streakMessage)
                .font(.subheadline)
                .multilineTextAlignment(.center)
                .foregroundColor(streakInfo.streakActive ? .white : .gray)
                .frame(maxWidth: .infinity)
        }
        .padding(20)
        .background(streakInfo.streakActive ? Color.orange : Color.orange.opacity(0.1))
        .cornerRadius(16)
    }
}

// MARK: - Time Analytics Section
struct TimeAnalyticsSection: View {
    let timeAnalytics: TimeAnalyticsInfo
    let globalStats: GlobalStatsInfo
    let puzzleStats: [PuzzlePerformanceInfo]
    
    var body: some View {
        VStack(alignment: .leading, spacing: 16) {
            Text("⏱️ Time Analytics")
                .font(.title2)
                .fontWeight(.bold)
            
            TimeAnalyticsCard(
                timeAnalytics: timeAnalytics,
                globalStats: globalStats,
                puzzleStats: puzzleStats
            )
        }
    }
}

struct TimeAnalyticsCard: View {
    let timeAnalytics: TimeAnalyticsInfo
    let globalStats: GlobalStatsInfo
    let puzzleStats: [PuzzlePerformanceInfo]
    
    var body: some View {
        VStack(spacing: 16) {
            HStack {
                StatItem(
                    label: "Total Hours",
                    value: String(format: "%.1f", timeAnalytics.totalHours),
                    emoji: "⏰"
                )
                
                let avgTime = globalStats.totalGamesPlayed > 0 ?
                    Int(timeAnalytics.totalHours * 3600 / Float(globalStats.totalGamesPlayed)) : 0
                
                StatItem(
                    label: "Avg/Game",
                    value: "\(avgTime)s",
                    emoji: "⚡"
                )
                
                StatItem(
                    label: "Fastest Type",
                    value: String(timeAnalytics.fastestPuzzleType.prefix(8)),
                    emoji: "🚀"
                )
            }
            
            if timeAnalytics.totalHours > 0 {
                Text(timeAnalytics.timeMessage)
                    .font(.caption)
                    .multilineTextAlignment(.center)
                    .foregroundColor(.green)
                    .frame(maxWidth: .infinity)
            }
        }
        .padding(20)
        .background(Color.green.opacity(0.1))
        .cornerRadius(16)
    }
}

// MARK: - Badge Collection Section
struct BadgeCollectionSection: View {
    let achievements: [Achievement]
    
    var body: some View {
        VStack(alignment: .leading, spacing: 16) {
            Text("🏅 Achievement Collection")
                .font(.title2)
                .fontWeight(.bold)
            
            BadgeCollectionCard(achievements: achievements)
        }
    }
}

struct BadgeCollectionCard: View {
    let achievements: [Achievement]
    
    var body: some View {
        LazyVGrid(columns: [
            GridItem(.adaptive(minimum: 80))
        ], spacing: 16) {
            ForEach(achievements.prefix(12), id: \.id) { achievement in
                BadgeItem(achievement: achievement)
            }
        }
        .padding(20)
        .background(Color.yellow.opacity(0.1))
        .cornerRadius(16)
    }
}

struct BadgeItem: View {
    let achievement: Achievement
    
    var body: some View {
        VStack(spacing: 8) {
            Text(achievement.icon)
                .font(.largeTitle)
                .opacity(achievement.isUnlocked ? 1.0 : 0.3)
            
            Text(achievement.title)
                .font(.caption)
                .multilineTextAlignment(.center)
                .foregroundColor(achievement.isUnlocked ? .primary : .gray)
        }
        .frame(width: 80, height: 80)
        .background(achievement.isUnlocked ? Color.white : Color.gray.opacity(0.1))
        .cornerRadius(12)
        .shadow(color: .black.opacity(0.1), radius: 2, x: 0, y: 1)
    }
}

// MARK: - Welcome New User Section
struct WelcomeNewUserSection: View {
    var body: some View {
        VStack(spacing: 16) {
            Text("🎉 Welcome to Puzzle Universe!")
                .font(.title3)
                .fontWeight(.bold)
                .foregroundColor(.green)
                .multilineTextAlignment(.center)
            
            Text("Start playing puzzles to unlock your progress dashboard. Every game earns XP, builds streaks, and unlocks achievements!")
                .font(.subheadline)
                .foregroundColor(.secondary)
                .multilineTextAlignment(.center)
            
            Text("🎯 Complete your first puzzle to begin your journey!")
                .font(.caption)
                .fontWeight(.medium)
                .foregroundColor(.green)
                .multilineTextAlignment(.center)
        }
        .padding(20)
        .background(Color.green.opacity(0.1))
        .cornerRadius(16)
    }
}

// MARK: - Helper Views
struct StatItem: View {
    let label: String
    let value: String
    let emoji: String
    let textColor: Color
    
    init(label: String, value: String, emoji: String, textColor: Color = .primary) {
        self.label = label
        self.value = value
        self.emoji = emoji
        self.textColor = textColor
    }
    
    var body: some View {
        VStack(spacing: 4) {
            Text(emoji)
                .font(.title2)
            
            Text(value)
                .font(.headline)
                .fontWeight(.bold)
                .foregroundColor(textColor)
            
            Text(label)
                .font(.caption)
                .foregroundColor(textColor.opacity(0.7))
        }
        .frame(maxWidth: .infinity)
    }
}

struct RewardChip: View {
    let reward: String
    
    var body: some View {
        Text(reward)
            .font(.caption)
            .padding(.horizontal, 8)
            .padding(.vertical, 4)
            .background(Color.white.opacity(0.8))
            .foregroundColor(.black)
            .cornerRadius(12)
    }
}

struct AdErrorView: View {
    let onRetry: () -> Void
    
    var body: some View {
        VStack(spacing: 16) {
            Text("❌ Failed to load dashboard")
                .font(.headline)
                .foregroundColor(.red)
            
            Button("Try Again", action: onRetry)
                .buttonStyle(.borderedProminent)
        }
        .padding()
    }
}

// MARK: - Helper Functions
func getTierColor(tier: String) -> Color {
    switch tier {
    case "Bronze": return Color(red: 0.82, green: 0.41, blue: 0.12)
    case "Silver": return Color(red: 0.75, green: 0.75, blue: 0.75)
    case "Gold": return Color(red: 1.0, green: 0.84, blue: 0.0)
    case "Platinum": return Color(red: 0.6, green: 0.85, blue: 0.9)
    case "Diamond": return Color(red: 0.73, green: 0.95, blue: 1.0)
    case "Master": return Color(red: 0.61, green: 0.15, blue: 0.69)
    default: return Color.gray
    }
}

func getTierEmoji(tier: String) -> String {
    switch tier {
    case "Bronze": return "🥉"
    case "Silver": return "🥈"
    case "Gold": return "🥇"
    case "Platinum": return "💎"
    case "Diamond": return "💠"
    case "Master": return "👑"
    default: return "🏆"
    }
}

func formatTimeLeft(_ timeInterval: TimeInterval) -> String {
    let days = Int(timeInterval) / (24 * 60 * 60)
    let hours = (Int(timeInterval) / (60 * 60)) % 24
    return "\(days)d \(hours)h"
}

func formatEventTime(_ timestamp: TimeInterval) -> String {
    let formatter = DateFormatter()
    formatter.dateFormat = "MMM dd"
    return formatter.string(from: Date(timeIntervalSince1970: timestamp))
}
