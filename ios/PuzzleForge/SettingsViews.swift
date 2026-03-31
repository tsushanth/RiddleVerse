//
//  ProfileSectionView.swift
//  PuzzleForge
//
//  Created by Sushanth Tiruvaipati on 9/17/25.
//


//
//  SettingsViews.swift
//  PuzzleForge
//

import SwiftUI
import FirebaseAuth

// MARK: - Profile Section
struct ProfileSectionView: View {
    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text("Profile")
                .font(.headline)
            
            HStack {
                Image(systemName: "person.circle.fill")
                    .font(.system(size: 40))
                    .foregroundColor(.blue)
                
                VStack(alignment: .leading) {
                    Text(Auth.auth().currentUser?.displayName ?? Auth.auth().currentUser?.email ?? "Guest")
                        .font(.title3)
                        .fontWeight(.semibold)
                    
                    if let email = Auth.auth().currentUser?.email {
                        Text(email)
                            .font(.caption)
                            .foregroundColor(.gray)
                    }
                }
                Spacer()
            }
            .padding()
            .background(Color(.systemGray6))
            .cornerRadius(12)
        }
    }
}

// MARK: - App Features Section
struct AppFeaturesSectionView: View {
    let onLeaderboard: () -> Void
    let onAchievements: () -> Void
    let onRateApp: () -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text("App Features")
                .font(.headline)

            VStack(spacing: 0) {
                SettingsRowView(icon: "trophy.fill", title: "Leaderboard", action: onLeaderboard)
                Divider()
                SettingsRowView(icon: "rosette", title: "Achievements", action: onAchievements)
                Divider()
                SettingsRowView(icon: "star.fill", title: "Rate RiddleVerse", action: onRateApp, iconColor: .orange)
            }
            .background(Color(.systemGray6))
            .cornerRadius(12)
        }
    }
}

// MARK: - Account Section
struct AccountSectionView: View {
    let onSignOut: () -> Void
    let onSignIn: () -> Void
    let onSignUp: () -> Void
    let onNotificationSettings: () -> Void
    
    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text("Account")
                .font(.headline)
            
            if Auth.auth().currentUser != nil {
                Button(action: onSignOut) {
                    HStack {
                        Image(systemName: "arrow.backward.circle")
                            .foregroundColor(.red)
                        Text("Sign Out")
                            .foregroundColor(.red)
                        Spacer()
                    }
                    .padding()
                    .background(Color(.systemGray6))
                    .cornerRadius(12)
                }
            } else {
                VStack(spacing: 0) {
                    Button(action: onSignIn) {
                        HStack {
                            Image(systemName: "person.crop.circle.fill")
                                .foregroundColor(.blue)
                            Text("Sign In")
                            Spacer()
                        }
                        .padding()
                    }
                    Divider()
                    Button(action: onSignUp) {
                        HStack {
                            Image(systemName: "person.badge.plus")
                                .foregroundColor(.green)
                            Text("Create Account")
                            Spacer()
                        }
                        .padding()
                    }
                    Divider()
                    Button(action: onNotificationSettings) {
                        HStack {
                            Image(systemName: "bell.badge")
                                .foregroundColor(.orange)
                            Text("Notification Settings")
                            Spacer()
                        }
                        .padding()
                    }
                }
                .background(Color(.systemGray6))
                .cornerRadius(12)
            }
        }
    }
}

// MARK: - Account Deletion Section
struct AccountDeletionSection: View {
    @Binding var isSignedOut: Bool
    @State private var showDeleteConfirmation = false
    @State private var isDeleting = false
    @State private var deleteError: String?
    
    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text("Account Management")
                .font(.headline)
            
            Button("Delete Account") {
                showDeleteConfirmation = true
            }
            .overlay(
                Group {
                    if isDeleting {
                        ProgressView()
                            .scaleEffect(0.8)
                    }
                }
            )
            .disabled(isDeleting)
            .foregroundColor(.red)
            .padding()
            .frame(maxWidth: .infinity)
            .background(Color(.systemGray6))
            .cornerRadius(12)
        }
        .alert("Delete Account", isPresented: $showDeleteConfirmation) {
            Button("Cancel", role: .cancel) { }
            Button("Delete", role: .destructive) {
                deleteAccount()
            }
        } message: {
            Text("This will permanently delete your account and all associated data. This action cannot be undone.")
        }
    }
    
    private func deleteAccount() {
        do {
            try Auth.auth().signOut()
            isSignedOut = true
            
            if let user = Auth.auth().currentUser {
                deleteUserAsync(userId: user.uid, email: user.email ?? "")
            }
        } catch {
            print("Signout failed: \(error.localizedDescription)")
        }
    }

    private func deleteUserAsync(userId: String, email: String) {
        Task {
            try? await Auth.auth().currentUser?.delete()
            
            guard let url = URL(string: "https://puzzleverseai.com/delete-user-account") else { return }
            var request = URLRequest(url: url)
            request.httpMethod = "DELETE"
            request.setValue("application/json", forHTTPHeaderField: "Content-Type")
            request.httpBody = try? JSONSerialization.data(withJSONObject: ["userId": userId, "email": email])
            try? await URLSession.shared.data(for: request)
        }
    }
}

// MARK: - Settings Row View
struct SettingsRowView: View {
    let icon: String
    let title: String
    let action: () -> Void
    var iconColor: Color = .blue
    
    var body: some View {
        Button(action: action) {
            HStack {
                Image(systemName: icon)
                    .foregroundColor(iconColor)
                    .frame(width: 30)
                Text(title)
                    .foregroundColor(.primary)
                Spacer()
                Image(systemName: "chevron.right")
                    .foregroundColor(.gray)
                    .font(.caption)
            }
            .padding()
        }
    }
}

// MARK: - Streak Stats Section
struct StreakStatsSection: View {
    @State private var dailyData: DailyData?
    
    var body: some View {
        VStack(alignment: .leading, spacing: 16) {
            Text("Daily Streak Stats")
                .font(.headline)
                .fontWeight(.semibold)
            
            if let data = dailyData {
                VStack(spacing: 16) {
                    HStack {
                        VStack(alignment: .leading, spacing: 4) {
                            HStack(spacing: 8) {
                                Text("🔥")
                                    .font(.title)
                                Text("\(data.currentStreak)")
                                    .font(.title)
                                    .fontWeight(.bold)
                                    .foregroundColor(.orange)
                                Text("day\(data.currentStreak == 1 ? "" : "s")")
                                    .font(.headline)
                                    .foregroundColor(.secondary)
                            }
                            Text("Current Streak")
                                .font(.caption)
                                .foregroundColor(.secondary)
                        }
                        
                        Spacer()
                        
                        VStack(alignment: .trailing, spacing: 4) {
                            HStack(spacing: 4) {
                                Text("\(data.coins)")
                                    .font(.title2)
                                    .fontWeight(.bold)
                                    .foregroundColor(Color(red: 1.0, green: 0.84, blue: 0.0))
                                Image(systemName: "dollarsign.circle.fill")
                                    .foregroundColor(Color(red: 1.0, green: 0.84, blue: 0.0))
                            }
                            Text("Total Coins")
                                .font(.caption)
                                .foregroundColor(.secondary)
                        }
                    }
                    
                    Divider()
                    
                    HStack {
                        VStack {
                            Text("\(data.totalDaysPlayed)")
                                .font(.title3)
                                .fontWeight(.bold)
                            Text("Days Played")
                                .font(.caption2)
                                .foregroundColor(.secondary)
                        }
                        
                        Spacer()
                        
                        VStack {
                            Text("\(data.bestStreak)")
                                .font(.title3)
                                .fontWeight(.bold)
                                .foregroundColor(.orange)
                            Text("Best Streak")
                                .font(.caption2)
                                .foregroundColor(.secondary)
                        }
                        
                        Spacer()
                        
                        VStack {
                            Text("\(getDaysUntilNextMilestone(data.currentStreak))")
                                .font(.title3)
                                .fontWeight(.bold)
                                .foregroundColor(.purple)
                            Text("To Milestone")
                                .font(.caption2)
                                .foregroundColor(.secondary)
                        }
                    }
                }
            } else {
                ProgressView()
                    .frame(height: 80)
            }
        }
        .padding()
        .background(Color(.systemGray6))
        .cornerRadius(12)
        .onAppear {
            loadDailyData()
        }
        .onReceive(NotificationCenter.default.publisher(for: NSNotification.Name("CoinsChanged"))) { _ in
            loadDailyData()
        }
    }
    
    private func loadDailyData() {
        dailyData = DailyStreakManager.shared.getDailyData()
    }
    
    private func getDaysUntilNextMilestone(_ currentStreak: Int) -> Int {
        let milestones = [7, 14, 30, 50, 100]
        for milestone in milestones {
            if currentStreak < milestone {
                return milestone - currentStreak
            }
        }
        return 0
    }
}