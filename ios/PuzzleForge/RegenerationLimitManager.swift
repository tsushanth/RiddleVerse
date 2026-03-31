//
//  RegenerationLimitManager.swift
//  PuzzleForge
//
//  Created by Sushanth Tiruvaipati on 8/28/25.
//

import Foundation
import SwiftUI

// MARK: - Regeneration Limit Manager
class RegenerationLimitManager: ObservableObject {
    static let shared = RegenerationLimitManager()
    
    private let userDefaults = UserDefaults.standard
    private let keyPrefix = "limit_info_"
    
    private init() {}
    
    // MARK: - Public Methods
    
    /**
     * Save limit information for a specific puzzle type
     */
    func saveLimitInfo(puzzleType: String, limitInfo: LimitInfo) {
        do {
            let data = try JSONEncoder().encode(limitInfo)
            let key = keyPrefix + puzzleType
            userDefaults.set(data, forKey: key)
            userDefaults.set(Date().timeIntervalSince1970, forKey: key + "_timestamp")
            
            print("RegenerationLimitManager: Saved limit info for \(puzzleType)")
        } catch {
            print("RegenerationLimitManager: Failed to save limit info: \(error)")
        }
    }
    
    /**
     * Get limit information for a specific puzzle type
     */
    func getLimitInfo(puzzleType: String) -> LimitInfo? {
        let key = keyPrefix + puzzleType
        let timestampKey = key + "_timestamp"
        
        guard let data = userDefaults.data(forKey: key) else {
            return nil
        }
        
        let timestamp = userDefaults.double(forKey: timestampKey)
        let currentTime = Date().timeIntervalSince1970
        
        // Check if limit info is still relevant (within 24 hours)
        if currentTime - timestamp > 24 * 60 * 60 {
            clearLimitInfo(puzzleType: puzzleType)
            return nil
        }
        
        do {
            let limitInfo = try JSONDecoder().decode(LimitInfo.self, from: data)
            return limitInfo
        } catch {
            print("RegenerationLimitManager: Failed to decode limit info: \(error)")
            clearLimitInfo(puzzleType: puzzleType)
            return nil
        }
    }
    
    /**
     * Clear limit information for a specific puzzle type
     */
    func clearLimitInfo(puzzleType: String) {
        let key = keyPrefix + puzzleType
        let timestampKey = key + "_timestamp"
        
        userDefaults.removeObject(forKey: key)
        userDefaults.removeObject(forKey: timestampKey)
        
        print("RegenerationLimitManager: Cleared limit info for \(puzzleType)")
    }
    
    /**
     * Check if a puzzle type has active limits
     */
    func hasActiveLimits(puzzleType: String) -> Bool {
        return getLimitInfo(puzzleType: puzzleType) != nil
    }
    
    /**
     * Get all puzzle types with active limits
     */
    func getAllActiveLimits() -> [String: LimitInfo] {
        var activeLimits: [String: LimitInfo] = [:]
        
        // This is a simplified approach - in a real app you might want to track
        // which puzzle types have been limited
        let allKeys = userDefaults.dictionaryRepresentation().keys
        
        for key in allKeys {
            if key.hasPrefix(keyPrefix) && !key.hasSuffix("_timestamp") {
                let puzzleType = String(key.dropFirst(keyPrefix.count))
                if let limitInfo = getLimitInfo(puzzleType: puzzleType) {
                    activeLimits[puzzleType] = limitInfo
                }
            }
        }
        
        return activeLimits
    }
    
    // MARK: - Utility Methods
    
    private func getPuzzleDisplayName(_ puzzleType: String) -> String {
        switch puzzleType.lowercased() {
        case "crossword": return "Crossword Puzzles"
        case "crypto": return "Crypto Puzzles"
        case "wordsnake", "word_snake": return "Word Snake Puzzles"
        case "math": return "Math Problems"
        case "trivia": return "Trivia"
        case "synonyms": return "Synonyms"
        case "antonyms": return "Antonyms"
        case "memorysquares": return "Memory Squares"
        case "mathestimation": return "Math Estimation"
        case "division": return "Division"
        case "anagram": return "Anagram"
        case "average": return "Average Calculations"
        case "percentages": return "Percentage Calculations"
        case "discounts": return "Discount Calculations"
        case "purchasing": return "Subscription Calculations"
        case "conversion": return "Unit Conversions"
        case "subtraction": return "Subtraction"
        case "mathtipping": return "Tip Calculations"
        default: return puzzleType.capitalized
        }
    }
}

// MARK: - SwiftUI Components

/**
 * Enhanced error message display component for regeneration limits
 */
struct RegenerationLimitCard: View {
    let puzzleType: String
    let limitInfo: LimitInfo
    let onDismiss: () -> Void
    let onUpgrade: () -> Void
    
    var body: some View {
        VStack(spacing: 16) {
            // Header with icon and dismiss button
            HStack {
                Image(systemName: "info.circle.fill")
                    .foregroundColor(Color.blue)
                    .font(.title2)
                
                Spacer()
                
                Button(action: onDismiss) {
                    Image(systemName: "xmark")
                        .foregroundColor(.gray)
                        .font(.caption)
                }
            }
            
            // Title and message
            VStack(alignment: .leading, spacing: 8) {
                Text(getLimitTitle(for: limitInfo.reason))
                    .font(.headline)
                    .fontWeight(.bold)
                    .foregroundColor(.white)
                
                Text(getEnhancedLimitMessage(puzzleType: puzzleType, limitInfo: limitInfo))
                    .font(.subheadline)
                    .foregroundColor(.gray)
                    .fixedSize(horizontal: false, vertical: true)
            }
            
            // Usage progress bars
            VStack(spacing: 12) {
                if limitInfo.reason.contains("daily") {
                    UsageProgressBar(
                        label: "Daily Usage",
                        used: limitInfo.dailyUsed,
                        total: limitInfo.dailyLimit,
                        color: Color.blue
                    )
                }
                
                if limitInfo.reason.contains("monthly") {
                    UsageProgressBar(
                        label: "Monthly Usage",
                        used: limitInfo.monthlyUsed,
                        total: limitInfo.monthlyLimit,
                        color: Color.blue
                    )
                }
            }
            
            // Reset time info
            if !limitInfo.resetTime.isEmpty {
                HStack {
                    Text("Resets: \(formatResetTime(limitInfo.resetTime))")
                        .font(.caption)
                        .foregroundColor(.gray)
                    Spacer()
                }
            }
            
            // Action buttons
            HStack(spacing: 12) {
                if limitInfo.reason != "rapid_fire_detected" {
                    Button(action: onUpgrade) {
                        Text("Upgrade to Premium")
                            .font(.caption)
                            .fontWeight(.medium)
                    }
                    .buttonStyle(.borderedProminent)
                    .controlSize(.small)
                    .tint(Color.blue)
                }
                
                Button(action: onDismiss) {
                    Text(limitInfo.reason == "rapid_fire_detected" ? "Got it" : "Play Available")
                        .font(.caption)
                        .fontWeight(.medium)
                }
                .buttonStyle(.bordered)
                .controlSize(.small)
                .tint(.gray)
            }
        }
        .padding(16)
        .background(Color.blue.opacity(0.1))
        .overlay(
            RoundedRectangle(cornerRadius: 12)
                .stroke(Color.blue.opacity(0.3), lineWidth: 1)
        )
        .cornerRadius(12)
    }
    
    // MARK: - Helper Methods
    
    private func getLimitTitle(for reason: String) -> String {
        switch reason {
        case "daily_limit_exceeded":
            return "Daily Generation Limit Reached"
        case "monthly_limit_exceeded":
            return "Monthly Generation Limit Reached"
        case "rapid_fire_detected":
            return "Rate Limit Active"
        default:
            return "Generation Limited"
        }
    }
    
    private func getEnhancedLimitMessage(puzzleType: String, limitInfo: LimitInfo) -> String {
        let displayName = getPuzzleDisplayName(puzzleType)
        
        switch limitInfo.reason {
        case "daily_limit_exceeded":
            return "You've used all \(limitInfo.dailyLimit) daily puzzle generations for \(displayName). You can still play from existing puzzles, or upgrade for unlimited access."
            
        case "monthly_limit_exceeded":
            return "You've reached your monthly limit of \(limitInfo.monthlyLimit) puzzle generations. Consider upgrading for unlimited monthly access to fresh puzzles."
            
        case "rapid_fire_detected":
            return "Please slow down! You're generating puzzles too quickly. Wait a moment before trying again to help us manage server resources."
            
        default:
            return "Puzzle generation is temporarily limited. You can still play available puzzles or try again later."
        }
    }
    
    private func getPuzzleDisplayName(_ puzzleType: String) -> String {
        switch puzzleType.lowercased() {
        case "crossword": return "Crossword Puzzles"
        case "crypto": return "Crypto Puzzles"
        case "wordsnake", "word_snake": return "Word Snake Puzzles"
        case "math": return "Math Problems"
        case "trivia": return "Trivia"
        case "synonyms": return "Synonyms"
        case "antonyms": return "Antonyms"
        case "memorysquares": return "Memory Squares"
        case "mathestimation": return "Math Estimation"
        case "division": return "Division"
        case "anagram": return "Anagram"
        case "average": return "Average Calculations"
        case "percentages": return "Percentage Calculations"
        case "discounts": return "Discount Calculations"
        case "purchasing": return "Subscription Calculations"
        case "conversion": return "Unit Conversions"
        case "subtraction": return "Subtraction"
        case "mathtipping": return "Tip Calculations"
        default: return puzzleType.capitalized
        }
    }
}

/**
 * Usage progress bar component
 */
struct UsageProgressBar: View {
    let label: String
    let used: Int
    let total: Int
    let color: Color
    
    var body: some View {
        VStack(spacing: 4) {
            HStack {
                Text(label)
                    .font(.caption)
                    .foregroundColor(.gray)
                
                Spacer()
                
                Text("\(used)/\(total)")
                    .font(.caption)
                    .fontWeight(.medium)
                    .foregroundColor(.white)
            }
            
            GeometryReader { geometry in
                ZStack(alignment: .leading) {
                    // Track
                    Rectangle()
                        .fill(Color.gray.opacity(0.3))
                        .frame(height: 4)
                        .cornerRadius(2)
                    
                    // Progress
                    Rectangle()
                        .fill(color)
                        .frame(width: geometry.size.width * progress, height: 4)
                        .cornerRadius(2)
                }
            }
            .frame(height: 4)
        }
    }
    
    private var progress: CGFloat {
        guard total > 0 else { return 0 }
        return min(1.0, CGFloat(used) / CGFloat(total))
    }
}
