//
//  LeaderboardViewModel.swift
//  PuzzleForge
//
//  Created by Sushanth Tiruvaipati on 6/2/25.
//
import SwiftUI
import Foundation
import FirebaseAuth

class LeaderboardViewModel: ObservableObject {
    @Published var topThree: [User] = []
    @Published var others: [User] = []
    @Published var isLoading = true
    
    private let userIcons = [
            "person.fill", "face.smiling", "gamecontroller.fill",
            "brain.head.profile", "sparkles", "bolt.fill",
            "flame.fill", "star.fill", "heart.fill", "diamond.fill",
            "crown.fill", "shield.fill", "rocket.fill", "wand.and.stars"
        ]
        
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

    func fetchLeaderboard() {
        print("🔍 Fetching leaderboard data...")
        
        guard let url = URL(string: "https://puzzleverseai.com/leaderboard") else {
            print("❌ Invalid URL")
            return
        }

        URLSession.shared.dataTask(with: url) { data, response, error in
            if let error = error {
                print("❌ Network error: \(error)")
                return
            }
            
            guard let httpResponse = response as? HTTPURLResponse else {
                print("❌ Invalid response")
                return
            }
            
            print("🔍 HTTP Status: \(httpResponse.statusCode)")
            
            guard let data = data else {
                print("❌ No data received")
                return
            }
            
            print("🔍 Raw response: \(String(data: data, encoding: .utf8) ?? "Unable to decode")")
            
            do {
                let json = try JSONSerialization.jsonObject(with: data) as? [String: Any]
                
                // Check for success field like Android does
                guard let success = json?["success"] as? Bool, success else {
                    print("❌ Backend returned unsuccessful response")
                    return
                }
                
                let leaderboard = json?["leaderboard"] as? [[String: Any]] ?? []
                print("🔍 Leaderboard count: \(leaderboard.count)")

                let currentEmail = Auth.auth().currentUser?.email ?? "unknown"
                print("🔍 Current user email: \(currentEmail)")

                let users: [User] = leaderboard.enumerated().compactMap { (i, obj) in
                    guard let name = obj["name"] as? String,
                          let score = obj["score"] as? Int else {
                        return nil
                    }

                    let randomIcon = self.userIcons.randomElement() ?? "person.fill"
                    let randomColor = self.userColors.randomElement() ?? Color.blue

                    return User(
                        name: name,
                        score: "\(score) pts",
                        isHighlighted: name.lowercased() == currentEmail.lowercased(),
                        avatarNumber: Int.random(in: 1...9),
                        rank: i + 1,
                        iconName: randomIcon,
                        backgroundColor: randomColor
                    )
                }

                DispatchQueue.main.async {
                    print("🔍 Setting topThree: \(users.prefix(3).count) users")
                    print("🔍 Setting others: \(users.dropFirst(3).count) users")
                    
                    self.topThree = Array(users.prefix(3))
                    self.others = Array(users.dropFirst(3))
                    self.isLoading = false
                    
                    print("🔍 Final state - topThree: \(self.topThree.count), others: \(self.others.count)")
                }

            } catch {
                print("❌ Failed to parse leaderboard: \(error)")
                DispatchQueue.main.async {
                    self.isLoading = false
                }
            }
        }.resume()
    }

}
