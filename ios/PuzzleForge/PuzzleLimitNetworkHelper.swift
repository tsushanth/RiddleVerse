//
//  PuzzleLimitNetworkHelper.swift
//  PuzzleForge
//
//  Created by Sushanth Tiruvaipati on 8/28/25.
//

import SwiftUI
import Foundation
import FirebaseAuth

// MARK: - Network Helper for Puzzle Limits
class PuzzleLimitNetworkHelper {
    static let shared = PuzzleLimitNetworkHelper()
    
    private init() {}
    
    func checkLimitBeforeFetch(
            puzzleType: String,
            onLimitReached: @escaping (LimitInfo) -> Void
        ) -> Bool {
        let limitManager = RegenerationLimitManager.shared
        
        if let cachedLimit = limitManager.getLimitInfo(puzzleType: puzzleType) {
            print("Found cached limit info for \(puzzleType), blocking fetch")
            onLimitReached(cachedLimit)
            return true // Limit reached
        }
        
        return false // Can proceed with fetch
    }
        
    /**
     * Parse API response to check for limit info
     * Call this from your existing network response handlers
     */
    func checkForLimitInResponse(
        data: Data,
        puzzleType: String,
        onLimitReached: @escaping (LimitInfo) -> Void
    ) -> Bool {
        do {
            guard let json = try JSONSerialization.jsonObject(with: data) as? [String: Any] else {
                return false
            }
            
            let success = json["success"] as? Bool ?? true
            let regenerationBlocked = json["regenerationBlocked"] as? Bool ?? false
            
            if !success || regenerationBlocked {
                if let limitInfoJson = json["limitInfo"] as? [String: Any] {
                    let limitInfo = parseLimitInfo(from: limitInfoJson)
                    
                    // Cache the limit info
                    RegenerationLimitManager.shared.saveLimitInfo(puzzleType: puzzleType, limitInfo: limitInfo)
                    
                    onLimitReached(limitInfo)
                    return true // Limit reached
                }
            }
            
            return false // No limit
        } catch {
            print("Error checking for limit in response: \(error)")
            return false
        }
    }
    
    
    /**
     * Handle unsuccessful API responses (no puzzles, auto-generation, etc.)
     */
    private func handleUnsuccessfulResponse(
        json: [String: Any]?,
        puzzleType: String,
        onSuccess: @escaping () -> Void,
        onError: @escaping (String) -> Void
    ) {
        let autoGenerating = json?["generatingMore"] as? Bool ?? false
        
        if autoGenerating {
            print("Auto-generation in progress for \(puzzleType)")
            // In a real implementation, you'd navigate to a "generation in progress" screen
            // For now, we'll just call onSuccess to indicate the flow completed
            onSuccess()
        } else {
            print("No puzzles available for \(puzzleType)")
            // In a real implementation, you'd navigate to a "no puzzles found" screen
            onError("No more puzzles available for this type")
        }
    }
    
    /**
     * Parse LimitInfo from JSON response
     */
    private func parseLimitInfo(from json: [String: Any]) -> LimitInfo {
        let limits = json["limits"] as? [String: Any] ?? [:]
        
        return LimitInfo(
            reason: json["reason"] as? String ?? "limit_exceeded",
            dailyUsed: limits["dailyUsed"] as? Int ?? 0,
            dailyLimit: limits["dailyLimit"] as? Int ?? 10,
            monthlyUsed: limits["monthlyUsed"] as? Int ?? 0,
            monthlyLimit: limits["monthlyLimit"] as? Int ?? 100,
            resetTime: json["resetTime"] as? String ?? "",
            upgradeUrl: json["upgradeUrl"] as? String ?? "/upgrade",
            message: json["message"] as? String ?? "Generation limit reached"
        )
    }
    
    /**
     * Start puzzle activity with group context
     */
    private func startPuzzleActivity(
        puzzle: PuzzleData,
        screenType: String,
        difficulty: String,
        targetPuzzleCount: Int,
        sourceGroupId: String?,
        sourceGroupName: String?,
        puzzleType: String,
        source: String,
        onSuccess: @escaping () -> Void
    ) {
        // In a real SwiftUI app, you'd use NavigationLink or present a new view
        // This is where you'd navigate to your PuzzleView with the puzzle data
        
        print("Starting puzzle activity:")
        print("  Type: \(puzzleType)")
        print("  Difficulty: \(difficulty)")
        print("  Screen Type: \(screenType)")
        print("  Source Group: \(sourceGroupName ?? "none")")
        print("  Target Count: \(targetPuzzleCount)")
        
        // For demonstration, we'll just call onSuccess
        // In your real implementation, replace this with actual navigation
        onSuccess()
    }
    
    
    
    /**
     * Show puzzle rejection dialog (implement based on your app's dialog system)
     */
    private func showPuzzleRejectionDialog(
        puzzleName: String,
        creator: String,
        format: String,
        status: String,
        onDismiss: @escaping () -> Void
    ) {
        let title = status == "failed" ? "Topic Rejected" : "Puzzle Regenerating"
        let message = "This topic has been flagged by our content review system as potentially inappropriate. This is an automated process and may occasionally flag content incorrectly.\n\nPuzzle: \"\(puzzleName)\"\nCreator: \(creator)\nFormat: \(format)"
        
        // In a real SwiftUI app, you'd present an alert or sheet
        print("Would show rejection dialog:")
        print("Title: \(title)")
        print("Message: \(message)")
        
        // For now, just call the dismiss handler
        onDismiss()
    }
    
    /**
     * Check auto-generation status for a puzzle type
     */
    func checkAutoGenerationStatus(
        puzzleType: String,
        difficulty: String,
        onStatusChecked: @escaping (Bool) -> Void
    ) {
        guard let currentUser = Auth.auth().currentUser,
              let userId = currentUser.uid.addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed),
              let email = currentUser.email?.addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed) else {
            onStatusChecked(false)
            return
        }
        
        let urlString = "https://puzzleverseai.com/fetch-next-puzzle-ios/\(puzzleType)?userId=\(userId)&email=\(email)&difficulty=\(difficulty.lowercased())"
        
        guard let url = URL(string: urlString) else {
            onStatusChecked(false)
            return
        }
        
        URLSession.shared.dataTask(with: url) { data, response, error in
            DispatchQueue.main.async {
                guard error == nil,
                      let data = data,
                      let json = try? JSONSerialization.jsonObject(with: data) as? [String: Any] else {
                    onStatusChecked(false)
                    return
                }
                
                let autoGenerating = json["autoGenerating"] as? Bool ?? false
                onStatusChecked(autoGenerating)
            }
        }.resume()
    }
}

// MARK: - Force Generation Loading View
struct ForceGenerationLoadingView: View {
    let puzzleType: String
    let onGenerationComplete: () -> Void
    let onBack: () -> Void
    
    @State private var isChecking = true
    @State private var checkAttempts = 0
    
    private let maxAttempts = 12 // Check for 2 minutes (12 * 10 seconds)
    
    var body: some View {
        ZStack {
            LinearGradient(
                colors: [Color.blue, Color.blue.opacity(0.7)],
                startPoint: .top,
                endPoint: .bottom
            )
            .ignoresSafeArea()
            
            VStack(spacing: 24) {
                ProgressView()
                    .scaleEffect(2.0)
                    .progressViewStyle(CircularProgressViewStyle(tint: .white))
                
                VStack(spacing: 16) {
                    Text("Checking Puzzle Availability")
                        .font(.title2)
                        .fontWeight(.bold)
                        .foregroundColor(.white)
                    
                    Text("Verifying that new \(getPuzzleDisplayName(puzzleType)) puzzles are ready...")
                        .font(.subheadline)
                        .foregroundColor(.white.opacity(0.8))
                        .multilineTextAlignment(.center)
                    
                    Text("Attempt \(checkAttempts + 1) of \(maxAttempts)")
                        .font(.caption)
                        .foregroundColor(.white.opacity(0.6))
                }
                .padding(24)
                .background(Color.white.opacity(0.1))
                .cornerRadius(16)
                
                Button("Return Home") {
                    onBack()
                }
                .font(.headline)
                .foregroundColor(.white)
                .padding(.horizontal, 24)
                .padding(.vertical, 12)
                .overlay(
                    RoundedRectangle(cornerRadius: 25)
                        .stroke(Color.white, lineWidth: 2)
                )
            }
            .padding(20)
        }
        .onAppear {
            startPeriodicCheck()
        }
    }
    
    private func startPeriodicCheck() {
        Timer.scheduledTimer(withTimeInterval: 10.0, repeats: true) { timer in
            guard checkAttempts < maxAttempts && isChecking else {
                timer.invalidate()
                if checkAttempts >= maxAttempts {
                    // Assume generation is done after max attempts
                    isChecking = false
                    onGenerationComplete()
                }
                return
            }
            
            checkAttempts += 1
            
            PuzzleLimitNetworkHelper.shared.checkAutoGenerationStatus(
                puzzleType: puzzleType,
                difficulty: "Medium"
            ) { autoGenerating in
                if !autoGenerating {
                    // Generation likely complete
                    timer.invalidate()
                    isChecking = false
                    onGenerationComplete()
                }
            }
        }
    }
    
    private func getPuzzleDisplayName(_ puzzleType: String) -> String {
        switch puzzleType.lowercased() {
        case "crossword": return "Crossword Puzzles"
        case "crypto": return "Crypto Puzzles"
        case "wordsnake": return "Word Snake Puzzles"
        case "math": return "Math Problems"
        case "trivia": return "Trivia"
        default: return puzzleType.capitalized
        }
    }
}
