//
//  UserScoreService.swift
//  PuzzleForge
//
//  Created by Sushanth Tiruvaipati on 9/17/25.
//


//
//  UserScoreService.swift
//  PuzzleForge
//
//  Created by Sushanth Tiruvaipati on 5/23/25.
//

import SwiftUI
import FirebaseAuth

class UserScoreService: ObservableObject {
    @Published var currentScore: Int = 0
    @Published var isLoading: Bool = false
    
    func fetchUserScore() {
        guard let user = Auth.auth().currentUser,
              let email = user.email else { return }
        
        guard let url = URL(string: "https://puzzleverseai.com/get-score?userId=\(email)") else { return }
        
        isLoading = true
        
        URLSession.shared.dataTask(with: url) { data, response, error in
            DispatchQueue.main.async {
                self.isLoading = false
                
                if let error = error {
                    print("❌ Failed to fetch score: \(error.localizedDescription)")
                    return
                }
                
                guard let data = data,
                      let json = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
                      let score = json["score"] as? Int else {
                    print("❌ Failed to parse score response")
                    return
                }
                
                self.currentScore = score
                print("✅ Fetched user score: \(score)")
            }
        }.resume()
    }
}