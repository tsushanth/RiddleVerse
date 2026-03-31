//
//  FacebookAnalytics.swift
//  PuzzleForge
//
//  Created by Sushanth Tiruvaipati on 5/23/25.
//

import Foundation
import FBSDKCoreKit

class FacebookAnalytics {
    
    static let shared = FacebookAnalytics()
    private init() {}
    
    // MARK: - Core App Events
    
    /// Track app activation (call on each app launch)
    func trackAppActivation() {
        AppEvents.shared.activateApp()
        print("Facebook: App activation tracked")
    }
    
    /// Track app install (automatically called on first launch)
    func trackAppInstall() {
        AppEvents.shared.logEvent(AppEvents.Name("fb_mobile_first_day_retention"))
        print("Facebook: App install tracked")
    }
    
    // MARK: - Tutorial Events
    
    /// Track when user starts tutorial
    func trackTutorialStart() {
        AppEvents.shared.logEvent(AppEvents.Name("fb_mobile_tutorial_start"))
        print("Facebook: Tutorial start tracked")
    }
    
    /// Track when user completes tutorial
    func trackTutorialCompletion() {
        AppEvents.shared.logEvent(AppEvents.Name("fb_mobile_tutorial_completion"))
        print("Facebook: Tutorial completion tracked")
    }
    
    // MARK: - User Journey Events
    
    /// Track user registration/login completion
    func trackUserRegistration() {
        AppEvents.shared.logEvent(AppEvents.Name("fb_mobile_complete_registration"))
        print("Facebook: User registration tracked")
    }
    
    /// Track when user taps continue from welcome screen
    func trackWelcomeContinue() {
        AppEvents.shared.logEvent(AppEvents.Name("welcome_continue_tapped"))
        print("Facebook: Welcome continue tracked")
    }
    
    // MARK: - Puzzle-Specific Events
    
    /// Track when user creates a puzzle
    func trackPuzzleCreated(puzzleType: String) {
        AppEvents.shared.logEvent(
            AppEvents.Name("puzzle_created"),
            parameters: [AppEvents.ParameterName("puzzle_type"): puzzleType]
        )
        print("Facebook: Puzzle created tracked - \(puzzleType)")
    }
    
    /// Track when user solves a puzzle
    func trackPuzzleSolved(puzzleType: String, timeSpent: Double, difficulty: String? = nil) {
        var parameters: [AppEvents.ParameterName: Any] = [
            AppEvents.ParameterName("puzzle_type"): puzzleType,
            AppEvents.ParameterName("time_spent"): timeSpent
        ]
        
        if let difficulty = difficulty {
            parameters[AppEvents.ParameterName("difficulty")] = difficulty
        }
        
        AppEvents.shared.logEvent(
            AppEvents.Name("puzzle_solved"),
            parameters: parameters
        )
        print("Facebook: Puzzle solved tracked - \(puzzleType), time: \(timeSpent)s")
    }
    
    /// Track when user shares a puzzle
    func trackPuzzleShared(puzzleType: String) {
        AppEvents.shared.logEvent(
            AppEvents.Name("fb_mobile_content_view"),
            parameters: [
                AppEvents.ParameterName("content_type"): "puzzle",
                AppEvents.ParameterName("puzzle_type"): puzzleType
            ]
        )
        print("Facebook: Puzzle shared tracked - \(puzzleType)")
    }
    
    // MARK: - Engagement Events
    
    /// Track when user views leaderboard
    func trackLeaderboardViewed() {
        AppEvents.shared.logEvent(AppEvents.Name("leaderboard_viewed"))
        print("Facebook: Leaderboard viewed tracked")
    }
    
    /// Track when user challenges a friend
    func trackChallengeFriend() {
        AppEvents.shared.logEvent(AppEvents.Name("challenge_friend"))
        print("Facebook: Challenge friend tracked")
    }
    
    /// Track level completion with score
    func trackLevelCompleted(level: Int, score: Int) {
        AppEvents.shared.logEvent(
            AppEvents.Name("fb_mobile_level_achieved"),
            parameters: [
                AppEvents.ParameterName("level"): level,
                AppEvents.ParameterName("score"): score
            ]
        )
        print("Facebook: Level completed tracked - Level: \(level), Score: \(score)")
    }
    
    // MARK: - Monetization Events (if you add IAP later)
    
    /// Track in-app purchase
    func trackPurchase(amount: Double, currency: String, productId: String) {
        AppEvents.shared.logPurchase(
            amount: amount,
            currency: currency,
            parameters: [AppEvents.ParameterName("product_id"): productId]
        )
        print("Facebook: Purchase tracked - \(currency)\(amount) for \(productId)")
    }
    
    /// Track when user views a product/upgrade screen
    func trackProductViewed(productId: String) {
        AppEvents.shared.logEvent(
            AppEvents.Name("fb_mobile_content_view"),
            parameters: [
                AppEvents.ParameterName("content_type"): "product",
                AppEvents.ParameterName("content_id"): productId
            ]
        )
        print("Facebook: Product viewed tracked - \(productId)")
    }
}
