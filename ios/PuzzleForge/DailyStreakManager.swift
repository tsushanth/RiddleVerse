//
//  DailyData.swift
//  PuzzleForge
//
//  Created by Sushanth Tiruvaipati on 8/25/25.
//


import Foundation
import SwiftUI

// MARK: - Data Models
struct DailyData {
    let lastOpenedDay: String
    let currentStreak: Int
    let coins: Int
    let totalDaysPlayed: Int
    let bestStreak: Int
}

struct StreakReward {
    let coins: Int
    let streakDay: Int
    let specialReward: String?
}

// MARK: - Daily Streak Manager
class DailyStreakManager: ObservableObject {
    static let shared = DailyStreakManager()
    
    private let userDefaults = UserDefaults.standard
    
    // Keys for UserDefaults
    private let lastOpenedKey = "last_opened_day"
    private let currentStreakKey = "current_streak"
    private let coinsKey = "total_coins"
    private let totalDaysKey = "total_days_played"
    private let bestStreakKey = "best_streak"
    
    private init() {}
    
    // MARK: - Date Helpers
    private func getTodayString() -> String {
        let formatter = DateFormatter()
        formatter.dateFormat = "yyyy-MM-dd"
        return formatter.string(from: Date())
    }
    
    func getCurrentCoins() -> Int {
        return getDailyData().coins
    }
    
    private func getYesterdayString() -> String {
        let calendar = Calendar.current
        let yesterday = calendar.date(byAdding: .day, value: -1, to: Date()) ?? Date()
        let formatter = DateFormatter()
        formatter.dateFormat = "yyyy-MM-dd"
        return formatter.string(from: yesterday)
    }
    
    // MARK: - Data Management
    func getDailyData() -> DailyData {
        return DailyData(
            lastOpenedDay: userDefaults.string(forKey: lastOpenedKey) ?? "",
            currentStreak: userDefaults.integer(forKey: currentStreakKey),
            coins: userDefaults.integer(forKey: coinsKey),
            totalDaysPlayed: userDefaults.integer(forKey: totalDaysKey),
            bestStreak: userDefaults.integer(forKey: bestStreakKey)
        )
    }
    
    private func saveDailyData(_ data: DailyData) {
        userDefaults.set(data.lastOpenedDay, forKey: lastOpenedKey)
        userDefaults.set(data.currentStreak, forKey: currentStreakKey)
        userDefaults.set(data.coins, forKey: coinsKey)
        userDefaults.set(data.totalDaysPlayed, forKey: totalDaysKey)
        userDefaults.set(data.bestStreak, forKey: bestStreakKey)
        
        print("💾 Saved daily data: streak=\(data.currentStreak), coins=\(data.coins), days=\(data.totalDaysPlayed)")
    }
    
    // MARK: - Reward Calculation
    func calculateStreakReward(streakDay: Int) -> StreakReward {
        let baseCoins: Int
        switch streakDay {
        case 1...3:
            baseCoins = 10
        case 4...7:
            baseCoins = 15
        case 8...14:
            baseCoins = 20
        case 15...30:
            baseCoins = 25
        default:
            baseCoins = 30
        }
        
        // Bonus coins for milestones
        let bonusCoins: Int
        switch streakDay {
        case 7:
            bonusCoins = 50  // Week bonus
        case 14:
            bonusCoins = 100 // 2 week bonus
        case 30:
            bonusCoins = 200 // Month bonus
        case 50:
            bonusCoins = 300 // 50 day bonus
        case 100:
            bonusCoins = 500 // 100 day bonus
        default:
            bonusCoins = 0
        }
        
        let specialReward: String?
        switch streakDay {
        case 7:
            specialReward = "🎯 Week Warrior!"
        case 14:
            specialReward = "🔥 Two Week Champion!"
        case 30:
            specialReward = "👑 Monthly Master!"
        case 50:
            specialReward = "🏆 Dedication Hero!"
        case 100:
            specialReward = "💎 Century Legend!"
        default:
            specialReward = nil
        }
        
        return StreakReward(
            coins: baseCoins + bonusCoins,
            streakDay: streakDay,
            specialReward: specialReward
        )
    }
    
    // MARK: - Main Processing Function
    /**
     * Main function called on app open
     * Returns: (shouldResetGroups: Bool, streakReward: StreakReward?)
     */
    func processAppOpen() -> (shouldResetGroups: Bool, streakReward: StreakReward?) {
        let currentData = getDailyData()
        let today = getTodayString()
        let yesterday = getYesterdayString()
        
        print("📅 Processing app open:")
        print("  Today: \(today)")
        print("  Yesterday: \(yesterday)")
        print("  Last opened: \(currentData.lastOpenedDay)")
        print("  Current streak: \(currentData.currentStreak)")
        
        switch currentData.lastOpenedDay {
        case today:
            // Same day - no reset needed
            print("📱 Same day open - no changes needed")
            return (false, nil)
            
        case yesterday:
            // Continue streak
            let newStreak = currentData.currentStreak + 1
            let newBestStreak = max(currentData.bestStreak, newStreak)
            let streakReward = calculateStreakReward(streakDay: newStreak)
            
            let updatedData = DailyData(
                lastOpenedDay: today,
                currentStreak: newStreak,
                coins: currentData.coins + streakReward.coins,
                totalDaysPlayed: currentData.totalDaysPlayed + 1,
                bestStreak: newBestStreak
            )
            
            saveDailyData(updatedData)
            
            print("🔥 Streak continued!")
            print("  New streak: \(newStreak)")
            print("  Reward: \(streakReward.coins) coins")
            print("  Special: \(streakReward.specialReward ?? "none")")
            
            return (true, streakReward)
            
        default:
            // Missed day(s) or first time - reset streak
            let streakReward = calculateStreakReward(streakDay: 1)
            
            let updatedData = DailyData(
                lastOpenedDay: today,
                currentStreak: 1, // Reset to 1 (today counts)
                coins: currentData.coins + streakReward.coins,
                totalDaysPlayed: currentData.totalDaysPlayed + 1,
                bestStreak: currentData.bestStreak // Keep best streak
            )
            
            saveDailyData(updatedData)
            
            if currentData.lastOpenedDay.isEmpty {
                print("🎉 Welcome new user!")
            } else {
                print("💔 Streak broken - resetting to day 1")
                print("  Previous streak was: \(currentData.currentStreak)")
            }
            print("  New day reward: \(streakReward.coins) coins")
            
            return (true, streakReward)
        }
    }
    
    // MARK: - Coin Management
    func addCoins(_ amount: Int) {
        let currentData = getDailyData()
        let updatedData = DailyData(
            lastOpenedDay: currentData.lastOpenedDay,
            currentStreak: currentData.currentStreak,
            coins: currentData.coins + amount,
            totalDaysPlayed: currentData.totalDaysPlayed,
            bestStreak: currentData.bestStreak
        )
        saveDailyData(updatedData)
        print("💰 Added \(amount) coins. Total: \(updatedData.coins)")
    }
    
    func spendCoins(_ amount: Int) -> Bool {
        let currentData = getDailyData()
        if currentData.coins >= amount {
            let updatedData = DailyData(
                lastOpenedDay: currentData.lastOpenedDay,
                currentStreak: currentData.currentStreak,
                coins: currentData.coins - amount,
                totalDaysPlayed: currentData.totalDaysPlayed,
                bestStreak: currentData.bestStreak
            )
            saveDailyData(updatedData)
            print("💸 Spent \(amount) coins. Remaining: \(updatedData.coins)")
            return true
        } else {
            print("❌ Not enough coins. Have: \(currentData.coins), Need: \(amount)")
            return false
        }
    }
}

// MARK: - Daily Streak Gift Dialog
struct DailyStreakGiftDialog: View {
    let streakReward: StreakReward
    let onDismiss: () -> Void
    
    @State private var giftOpened = false
    @State private var showReward = false
    @State private var showStreakCelebration = false
    @State private var showCloseButton = false
    
    // Calculate streak progression
    private var previousStreak: Int {
        max(0, streakReward.streakDay - 1)
    }
    
    private var newStreak: Int {
        streakReward.streakDay
    }
    
    var body: some View {
        ZStack {
            // Background overlay
            Color.black.opacity(0.8)
                .ignoresSafeArea()
                .onTapGesture {
                    onDismiss()
                }
            
            // Main dialog
            VStack(spacing: 24) {
                // Title
                Text(giftOpened ? "Congratulations! 🎉" : "Daily Reward!")
                    .font(.title)
                    .fontWeight(.bold)
                    .foregroundColor(.primary)
                    .multilineTextAlignment(.center)
                
                // Current streak info (before opening)
                if !showReward && previousStreak > 0 {
                    HStack {
                        Text("🔥")
                            .font(.title2)
                        Text("Current streak: \(previousStreak) days")
                            .font(.headline)
                            .fontWeight(.medium)
                            .foregroundColor(.purple)
                    }
                    .padding()
                    .background(Color.purple.opacity(0.1))
                    .cornerRadius(12)
                }
                
                // Instructions or confirmation
                Text(showReward ? "Here's your reward for day \(newStreak)!" : 
                     (giftOpened ? "Opening..." : "Tap the gift to open your reward!"))
                    .font(.subheadline)
                    .foregroundColor(.secondary)
                    .multilineTextAlignment(.center)
                
                // Gift Box or Reward Display
                if !showReward {
                    GiftBoxView(isOpened: giftOpened)
                        .scaleEffect(giftOpened ? 1.2 : 1.0)
                        .rotationEffect(.degrees(giftOpened ? 360 : 0))
                        .animation(.spring(dampingFraction: 0.6).delay(giftOpened ? 0 : 0), value: giftOpened)
                        .onTapGesture {
                            if !giftOpened {
                                openGift()
                            }
                        }
                } else {
                    RewardDisplayView(
                        streakReward: streakReward,
                        showStreakCelebration: showStreakCelebration,
                        newStreak: newStreak
                    )
                }
                
                // Action button or instructions
                if showCloseButton {
                    Button(action: {
                        onDismiss()
                    }) {
                        Text("Let's Play! 🎮")
                            .font(.headline)
                            .fontWeight(.bold)
                            .foregroundColor(.white)
                            .frame(maxWidth: .infinity)
                            .padding()
                            .background(Color.green)
                            .cornerRadius(24)
                    }
                    .scaleEffect(showCloseButton ? 1.0 : 0.0)
                    .animation(.spring(dampingFraction: 0.8), value: showCloseButton)
                } else if !giftOpened {
                    Text("👆 Tap the gift above!")
                        .font(.caption)
                        .foregroundColor(.purple)
                        .padding(.horizontal, 12)
                        .padding(.vertical, 6)
                        .background(Color.purple.opacity(0.1))
                        .cornerRadius(8)
                }
            }
            .padding(24)
            .background(
                RoundedRectangle(cornerRadius: 20)
                    .fill(Color(.systemBackground))
                    .shadow(color: .black.opacity(0.2), radius: 16, x: 0, y: 8)
            )
            .padding(.horizontal, 32)
            
            // Close button in top-right corner
            if showCloseButton {
                VStack {
                    HStack {
                        Spacer()
                        Button(action: onDismiss) {
                            Image(systemName: "xmark")
                                .font(.title2)
                                .foregroundColor(.gray)
                                .frame(width: 44, height: 44)
                                .background(Color(.systemGray6))
                                .clipShape(Circle())
                        }
                        .scaleEffect(showCloseButton ? 1.0 : 0.0)
                        .animation(.spring(dampingFraction: 0.8), value: showCloseButton)
                    }
                    .padding(.top, 24)
                    .padding(.trailing, 24)
                    Spacer()
                }
            }
        }
    }
    
    private func openGift() {
        // Haptic feedback
        let impactFeedback = UIImpactFeedbackGenerator(style: .medium)
        impactFeedback.impactOccurred()
        
        withAnimation(.spring(dampingFraction: 0.6)) {
            giftOpened = true
        }
        
        // Show reward after animation
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.5) {
            withAnimation(.spring(dampingFraction: 0.8)) {
                showReward = true
            }
            
            // Haptic feedback for reward reveal
            let successFeedback = UINotificationFeedbackGenerator()
            successFeedback.notificationOccurred(.success)
            
            // Show streak celebration
            DispatchQueue.main.asyncAfter(deadline: .now() + 0.5) {
                withAnimation(.spring(dampingFraction: 0.6)) {
                    showStreakCelebration = true
                }
                
                // Show close button after celebration
                DispatchQueue.main.asyncAfter(deadline: .now() + 3.0) {
                    withAnimation(.spring(dampingFraction: 0.8)) {
                        showCloseButton = true
                    }
                }
            }
        }
    }
}

// MARK: - Gift Box View
struct GiftBoxView: View {
    let isOpened: Bool
    
    var body: some View {
        ZStack {
            // Gift box background
            RoundedRectangle(cornerRadius: 12)
                .fill(
                    LinearGradient(
                        colors: [Color.orange, Color.red],
                        startPoint: .top,
                        endPoint: .bottom
                    )
                )
                .frame(width: 100, height: 100)
                .overlay(
                    RoundedRectangle(cornerRadius: 12)
                        .stroke(Color.yellow, lineWidth: 3)
                )
            
            // Horizontal ribbon
            Rectangle()
                .fill(Color.yellow)
                .frame(width: 100, height: 20)
                .cornerRadius(10)
            
            // Vertical ribbon
            Rectangle()
                .fill(Color.yellow)
                .frame(width: 20, height: 100)
                .cornerRadius(10)
            
            // Bow/Gift icon
            Image(systemName: "gift.fill")
                .font(.title)
                .foregroundColor(.yellow)
        }
        .frame(width: 120, height: 120)
    }
}

// MARK: - Reward Display View
struct RewardDisplayView: View {
    let streakReward: StreakReward
    let showStreakCelebration: Bool
    let newStreak: Int
    
    var body: some View {
        VStack(spacing: 16) {
            // Streak celebration with particles
            if showStreakCelebration {
                StreakCelebrationView(streakDay: newStreak)
                    .scaleEffect(showStreakCelebration ? 1.2 : 1.0)
                    .animation(.spring(dampingFraction: 0.6), value: showStreakCelebration)
                
                Text(newStreak == 1 ? "Day 1 🎯" : "\(newStreak) Day Streak! 🔥")
                    .font(.title2)
                    .fontWeight(.bold)
                    .foregroundColor(.orange)
                    .multilineTextAlignment(.center)
            }
            
            // Coins reward
            HStack(spacing: 8) {
                Image(systemName: "dollarsign.circle.fill")
                    .font(.system(size: 48))
                    .foregroundColor(.yellow)
                    .scaleEffect(showStreakCelebration ? 1.0 : 0.0)
                    .animation(.spring(dampingFraction: 0.8).delay(0.2), value: showStreakCelebration)
                
                Text("+\(streakReward.coins)")
                    .font(.system(size: 36, weight: .bold))
                    .foregroundColor(.yellow)
            }
            
            // Special reward message
            if let specialReward = streakReward.specialReward {
                Text(specialReward)
                    .font(.headline)
                    .fontWeight(.bold)
                    .foregroundColor(.white)
                    .padding()
                    .background(Color.purple)
                    .cornerRadius(16)
                    .multilineTextAlignment(.center)
            }
            
            // Encouragement message
            Text(getEncouragementMessage(for: newStreak))
                .font(.caption)
                .foregroundColor(.secondary)
                .multilineTextAlignment(.center)
        }
    }
    
    private func getEncouragementMessage(for streak: Int) -> String {
        switch streak {
        case 1:
            return "Welcome back! Start your streak journey! 🚀"
        case 2:
            return "Great job! You're building a habit! 💪"
        case 3:
            return "Three days strong! Keep it up! 🎯"
        case 7:
            return "One week complete! You're on fire! 🔥"
        case 14:
            return "Two weeks! Incredible dedication! 🏆"
        case 30:
            return "One month! You're a champion! 👑"
        case 2...6:
            return "Keep the momentum going! 🌟"
        case 8...13:
            return "You're building an amazing streak! ⭐"
        case 15...29:
            return "Outstanding consistency! 💎"
        default:
            return "Legendary streak! You're unstoppable! 🚀"
        }
    }
}

// MARK: - Streak Celebration View
struct StreakCelebrationView: View {
    let streakDay: Int
    
    var body: some View {
        ZStack {
            // Particle effects (simplified)
            ForEach(0..<8, id: \.self) { index in
                let angle = Double(index) * 45.0
                let distance: CGFloat = 40
                
                Circle()
                    .fill(Color.yellow)
                    .frame(width: 8, height: 8)
                    .offset(
                        x: distance * cos(angle * .pi / 180),
                        y: distance * sin(angle * .pi / 180)
                    )
            }
            
            // Main streak circle
            ZStack {
                Circle()
                    .fill(
                        LinearGradient(
                            colors: [Color.orange, Color.red],
                            startPoint: .top,
                            endPoint: .bottom
                        )
                    )
                    .frame(width: 80, height: 80)
                
                VStack(spacing: 2) {
                    Text("🔥")
                        .font(.title2)
                    Text("\(streakDay)")
                        .font(.title)
                        .fontWeight(.bold)
                        .foregroundColor(.white)
                }
            }
        }
        .frame(width: 80, height: 80)
    }
}

// MARK: - Integration with HomeView
extension HomeView {
    func handleDailySystemOnAppOpen() -> StreakReward? {
        let (shouldReset, streakReward) = DailyStreakManager.shared.processAppOpen()
        
        if shouldReset {
            print("🔄 Resetting all puzzle group completion for new day")
            //GroupCompletionManager.resetAllCompletion()
        }
        
        return streakReward
    }
}
