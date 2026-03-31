//
//  LeaderboardView.swift
//  PuzzleForge
//
//  Created by Sushanth Tiruvaipati on 5/23/25.
//


import SwiftUI

struct LeaderboardView: View {
    let currentUserId: String
    @StateObject private var viewModel = LeaderboardViewModel()
    
    // Random icons for users
    private let userIcons = [
        "person.fill", "face.smiling", "gamecontroller.fill",
        "brain.head.profile", "sparkles", "bolt.fill",
        "flame.fill", "star.fill", "heart.fill", "diamond.fill",
        "crown.fill", "shield.fill", "rocket.fill", "wand.and.stars"
    ]
    
    // Random background colors
    private let userColors = [
        Color(red: 1.0, green: 0.6, blue: 0.8),  // Pink
        Color(red: 0.6, green: 0.8, blue: 1.0),  // Light Blue
        Color(red: 0.8, green: 1.0, blue: 0.6),  // Light Green
        Color(red: 1.0, green: 0.8, blue: 0.4),  // Orange
        Color(red: 0.9, green: 0.7, blue: 1.0),  // Lavender
        Color(red: 0.7, green: 1.0, blue: 0.9),  // Mint
        Color(red: 1.0, green: 0.9, blue: 0.6),  // Yellow
        Color(red: 0.8, green: 0.6, blue: 1.0)   // Purple
    ]

    
    var body: some View {
        ZStack {
            // Colorful gradient background
            LinearGradient(
                gradient: Gradient(colors: [
                    Color(red: 0.4, green: 0.6, blue: 1.0),
                    Color(red: 0.8, green: 0.4, blue: 1.0)
                ]),
                startPoint: .topLeading,
                endPoint: .bottomTrailing
            )
            .ignoresSafeArea()
            
            VStack(spacing: 24) {
                titleView
                topThreeView(viewModel.topThree)
                leaderboardListView
                Spacer()
            }
        }
        .preferredColorScheme(.light)
        .onAppear {
            viewModel.fetchLeaderboard()
        }
    }
    
    // MARK: - Subviews
    
    private var titleView: some View {
        HStack {
            Image(systemName: "trophy.fill")
                .font(.title)
                .foregroundColor(.yellow)
            
            Text("Leaderboard")
                .font(.title)
                .fontWeight(.bold)
                .foregroundColor(.white)
                .shadow(radius: 2)
            
            Image(systemName: "star.fill")
                .font(.title)
                .foregroundColor(.yellow)
        }
        .padding(.top)
    }
    
    private var leaderboardListView: some View {
        ScrollView {
            VStack(spacing: 12) {
                ForEach(viewModel.others, id: \.id) { user in
                    leaderboardRow(for: user)
                }
            }
        }
    }
    
    private func leaderboardRow(for user: User) -> some View {
        HStack(spacing: 16) {
            // Rank badge
            ZStack {
                Circle()
                    .fill(Color.white.opacity(0.2))
                    .frame(width: 32, height: 32)
                
                Text("\(user.rank)")
                    .font(.subheadline)
                    .fontWeight(.bold)
                    .foregroundColor(.white)
            }
            
            // User avatar with random icon
            ZStack {
                Circle()
                    .fill(user.backgroundColor)
                    .frame(width: 44, height: 44)
                    .shadow(radius: 3)
                
                Image(systemName: user.iconName)
                    .font(.title3)
                    .foregroundColor(.white)
            }
            
            VStack(alignment: .leading, spacing: 2) {
                Text(user.name)
                    .font(.subheadline)
                    .fontWeight(.medium)
                    .foregroundColor(.white)
                    .lineLimit(1)
                
                Text("Rank #\(user.rank)")
                    .font(.caption)
                    .foregroundColor(.white.opacity(0.8))
            }
            
            Spacer()
            
            // Score badge
            ZStack {
                Capsule()
                    .fill(Color.white.opacity(0.2))
                    .frame(height: 28)
                
                Text(user.score)
                    .font(.subheadline)
                    .fontWeight(.bold)
                    .foregroundColor(.white)
                    .padding(.horizontal, 12)
            }
        }
        .padding(.horizontal, 20)
        .padding(.vertical, 12)
        .background(
            RoundedRectangle(cornerRadius: 16)
                .fill(user.id.uuidString == currentUserId ?
                      Color.yellow.opacity(0.3) :
                      Color.white.opacity(0.15))
                .overlay(
                    RoundedRectangle(cornerRadius: 16)
                        .stroke(user.id.uuidString == currentUserId ?
                               Color.yellow : Color.white.opacity(0.3),
                               lineWidth: 2)
                )
        )
        .padding(.horizontal, 16)
    }
    
    private func rowBackground(for user: User) -> some View {
        RoundedRectangle(cornerRadius: 12)
            .fill(user.id.uuidString == currentUserId ? Color.orange : Color.white)
            .overlay(
                RoundedRectangle(cornerRadius: 12)
                    .stroke(Color.purple.opacity(0.3), lineWidth: 1)
            )
    }
    
    @ViewBuilder
    func topThreeView(_ users: [User]) -> some View {
        HStack(alignment: .bottom, spacing: 20) {
            ForEach(users.sorted(by: { $0.rank < $1.rank }), id: \.id) { user in
                TopUserView(user: user)
            }
        }
    }
}

// MARK: - Top User View

struct TopUserView: View {
    let user: User
    
    var body: some View {
        VStack(spacing: 8) {
            // Crown for first place
            if user.rank == 1 {
                Image(systemName: "crown.fill")
                    .font(.title)
                    .foregroundColor(.yellow)
                    .shadow(radius: 2)
            }
            
            // Colorful avatar
            ZStack {
                Circle()
                    .fill(user.backgroundColor)
                    .frame(width: 70, height: 70)
                    .shadow(radius: 5)
                
                Circle()
                    .strokeBorder(Color.white, lineWidth: 3)
                    .frame(width: 70, height: 70)
                
                Image(systemName: user.iconName)
                    .font(.title2)
                    .foregroundColor(.white)
            }
            
            Text(user.name)
                .font(.caption)
                .fontWeight(.medium)
                .foregroundColor(.white)
                .lineLimit(1)
                .shadow(radius: 1)
            
            Text(user.score)
                .font(.caption2)
                .fontWeight(.bold)
                .foregroundColor(.white.opacity(0.9))
                .shadow(radius: 1)
        }
    }
}
