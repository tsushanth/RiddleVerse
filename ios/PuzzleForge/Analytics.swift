//
//  AnalyticsEvent.swift
//  PuzzleForge
//
//  Created by Sushanth Tiruvaipati on 6/19/25.
//

import SwiftUI
import FirebaseAnalytics
import FirebaseAuth
import RatingKit
import Foundation

// MARK: - Analytics Events Configuration
struct AnalyticsEvent {
    let name: String
    let parameters: [String: Any]?
    
    init(_ name: String, parameters: [String: Any]? = nil) {
        self.name = name
        self.parameters = parameters
    }
}

// MARK: - User Session (Firebase-only tracking)
struct FirebaseSession {
    let sessionId: String
    let startedAt: Date
    var endedAt: Date?
    var durationSeconds: Int?
    var puzzlesSolved: Int = 0
    var totalScore: Int = 0
    let appVersion: String
    let deviceInfo: [String: String]
}

class AnalyticsManager: ObservableObject {
    static let shared = AnalyticsManager()
    
    // Firebase-only tracking
    private var currentSession: FirebaseSession?
    private var sessionTimer: Timer?
    
    @Published var isInitialized = false
    private var sessionStartTime: Date?
    private var sessionPuzzlesSolved = 0
    private var sessionTotalScore = 0
    
    private var _hasInitialized = false
    private let initializationQueue = DispatchQueue(label: "analytics.initialization", qos: .userInitiated)
    
    private init() {
        initializationQueue.async {
            self.initializeAnalytics()
        }
    }
    
    private func initializeAnalytics() {
        guard !_hasInitialized else { return }
        
        _hasInitialized = true
        
        // Initialize Firebase Analytics (should already be configured from login setup)
        Analytics.setAnalyticsCollectionEnabled(true)
        Analytics.setSessionTimeoutInterval(1800) // 30 minutes
        
        DispatchQueue.main.async {
            self.isInitialized = true
            print("🔥 Firebase Analytics initialized successfully")
        }
        
        track(.appOpen())
        startSession()
        
        // Set user properties for Google Ads optimization
        setUserProperties()
    }
    
    // MARK: - Main Tracking Method (Firebase-only)
    func track(_ event: AnalyticsEvent) {
        guard _hasInitialized else {
            print("⚠️ Analytics not initialized, queuing event: \(event.name)")
            DispatchQueue.main.asyncAfter(deadline: .now() + 1.0) {
                self.track(event)
            }
            return
        }
        
        Task {
            await trackToFirebase(event)
            await processSpecialEvent(event)
        }
    }
    
    // MARK: - Firebase Analytics Tracking
    private func trackToFirebase(_ event: AnalyticsEvent) async {
        await MainActor.run {
            let firebaseEventName = convertToFirebaseEventName(event.name)
            let firebaseParams = convertToFirebaseParameters(event.parameters)
            
            Analytics.logEvent(firebaseEventName, parameters: firebaseParams)
            TikTokHelper.shared.trackEvent(firebaseEventName, properties: firebaseParams)
            print("🔥 Firebase Event: \(firebaseEventName)")
            
            if !firebaseParams.isEmpty {
                print("🔥 Parameters: \(firebaseParams)")
            }
        }
    }
    
    private func convertToFirebaseEventName(_ eventName: String) -> String {
        // Convert custom event names to Firebase standard events where possible
        switch eventName {
        case "puzzle_complete":
            return AnalyticsEventLevelEnd
        case "puzzle_start":
            return AnalyticsEventLevelStart
        case "tutorial_complete":
            return AnalyticsEventTutorialComplete
        case "tutorial_start":
            return AnalyticsEventTutorialBegin
        case "user_registration":
            return AnalyticsEventSignUp
        case "user_login":
            return AnalyticsEventLogin
        case "achievement_unlocked":
            return AnalyticsEventUnlockAchievement
        case "level_up":
            return AnalyticsEventLevelUp
        case "subscription_purchase":
            return AnalyticsEventPurchase
        case "share_content":
            return AnalyticsEventShare
        case "screen_view":
            return AnalyticsEventScreenView
        case "session_start":
            return AnalyticsEventAppOpen
        case "session_end":
            return "session_end" // Custom event
        case "app_open":
            return AnalyticsEventAppOpen
        case "app_background":
            return "app_background" // Custom event
        case "puzzle_abandoned":
            return "puzzle_abandoned" // Custom event
        case "puzzle_hint_used":
            return "puzzle_hint_used" // Custom event
        case "streak_update":
            return "streak_update" // Custom event
        case "user_engagement":
            return "user_engagement" // Custom event
        default:
            // Custom events: ensure they follow Firebase naming rules
            return sanitizeFirebaseEventName(eventName)
        }
    }
    
    private func sanitizeFirebaseEventName(_ eventName: String) -> String {
        return eventName
            .lowercased()
            .replacingOccurrences(of: " ", with: "_")
            .replacingOccurrences(of: "-", with: "_")
            .filter { $0.isLetter || $0.isNumber || $0 == "_" }
            .prefix(40) // Firebase event name limit
            .description
    }
    
    private func convertToFirebaseParameters(_ params: [String: Any]?) -> [String: Any] {
        guard let params = params else { return [:] }
        
        var firebaseParams: [String: Any] = [:]
        
        for (key, value) in params {
            let firebaseKey = convertToFirebaseParameterKey(key)
            
            // Ensure parameter values are Firebase-compatible
            if let stringValue = value as? String {
                firebaseParams[firebaseKey] = stringValue.prefix(100).description // Firebase string limit
            } else if let numberValue = value as? NSNumber {
                firebaseParams[firebaseKey] = numberValue
            } else if let boolValue = value as? Bool {
                firebaseParams[firebaseKey] = boolValue
            } else {
                firebaseParams[firebaseKey] = String(describing: value).prefix(100).description
            }
        }
        
        return firebaseParams
    }
    
    private func convertToFirebaseParameterKey(_ key: String) -> String {
        // Convert parameter keys to Firebase standard parameters where possible
        switch key {
        case "puzzle_type":
            return AnalyticsParameterItemCategory
        case "difficulty":
            return AnalyticsParameterLevel
        case "score":
            return AnalyticsParameterScore
        case "is_correct":
            return AnalyticsParameterSuccess
        case "time_spent_seconds":
            return "time_spent"
        case "session_duration_seconds":
            return "session_duration"
        case "screen_name":
            return AnalyticsParameterScreenName
        case "content_type":
            return AnalyticsParameterContentType
        case "share_method":
            return AnalyticsParameterMethod
        case "subscription_tier":
            return AnalyticsParameterItemName
        case "price":
            return AnalyticsParameterValue
        case "currency":
            return AnalyticsParameterCurrency
        case "achievement_id":
            return AnalyticsParameterAchievementID
        default:
            // Ensure parameter keys follow Firebase rules
            return sanitizeFirebaseParameterKey(key)
        }
    }
    
    private func sanitizeFirebaseParameterKey(_ key: String) -> String {
        return key
            .lowercased()
            .replacingOccurrences(of: " ", with: "_")
            .replacingOccurrences(of: "-", with: "_")
            .filter { $0.isLetter || $0.isNumber || $0 == "_" }
            .prefix(40)
            .description
    }
    
    // MARK: - Google Ads Conversion Events
    func trackConversion(_ event: AnalyticsEvent, value: Double? = nil) {
        Task {
            var parameters = event.parameters ?? [:]
            
            if let conversionValue = value {
                parameters["value"] = conversionValue
                parameters["currency"] = "USD"
            }
            
            // Track to Firebase with conversion value
            await MainActor.run {
                let firebaseEventName = convertToFirebaseEventName(event.name)
                var firebaseParams = convertToFirebaseParameters(parameters)
                
                // Add conversion-specific parameters
                if let value = value {
                    firebaseParams[AnalyticsParameterValue] = value
                    firebaseParams[AnalyticsParameterCurrency] = "USD"
                }
                
                Analytics.logEvent(firebaseEventName, parameters: firebaseParams)
                print("💰 Firebase Conversion Event: \(firebaseEventName) with value: \(value ?? 0)")
            }
        }
    }
    
    // MARK: - Session Management
    func startSession() {
        Task {
            await MainActor.run {
                sessionStartTime = Date()
                sessionPuzzlesSolved = 0
                sessionTotalScore = 0
            }
            
            let sessionId = UUID().uuidString
            let session = FirebaseSession(
                sessionId: sessionId,
                startedAt: Date(),
                appVersion: getAppVersion(),
                deviceInfo: getDeviceInfo()
            )
            
            await MainActor.run {
                currentSession = session
            }
            
            trackSafely(.sessionStart())
            
            // Log session start to Firebase with custom parameters
            await trackToFirebase(AnalyticsEvent("session_start", parameters: [
                "session_id": sessionId,
                "app_version": session.appVersion,
                "device_model": session.deviceInfo["device_model"] ?? "unknown"
            ]))
        }
    }
    
    func endSession() {
        Task {
            guard let startTime = sessionStartTime,
                  var session = currentSession else { return }
            
            let duration = Date().timeIntervalSince(startTime)
            session.endedAt = Date()
            session.durationSeconds = Int(duration)
            session.puzzlesSolved = sessionPuzzlesSolved
            session.totalScore = sessionTotalScore
            
            trackSafely(.sessionEnd(
                duration: duration,
                puzzlesSolved: sessionPuzzlesSolved,
                totalScore: sessionTotalScore
            ))
            
            // Track engagement for Google Ads optimization
            trackUserEngagement(sessionDuration: duration, puzzlesSolved: sessionPuzzlesSolved)
            
            await MainActor.run {
                sessionStartTime = nil
                currentSession = nil
            }
        }
    }
    
    func puzzleCompleted(score: Int) {
        Task {
            await MainActor.run {
                sessionPuzzlesSolved += 1
                sessionTotalScore += score
                RatingKit.shared.trackAction()
            }
        }
    }
    
    // MARK: - Campaign Attribution Events
    func trackPuzzleCompleted(puzzleType: String, difficulty: String, score: Int) {
        // This is a key conversion event for Google Ads
        track(.puzzleComplete(
            type: puzzleType,
            difficulty: difficulty,
            isCorrect: true,
            timeSpent: 0,
            score: score,
            questionIndex: 0,
            totalQuestions: 1
        ))
        
        // Track as conversion with value
        trackConversion(.puzzleComplete(
            type: puzzleType,
            difficulty: difficulty,
            isCorrect: true,
            timeSpent: 0,
            score: score,
            questionIndex: 0,
            totalQuestions: 1
        ), value: Double(score) * 0.01) // Convert score to monetary value
    }
    
    func trackUserEngagement(sessionDuration: TimeInterval, puzzlesSolved: Int) {
        // Important for Google Ads optimization
        let event = AnalyticsEvent.userEngagement(
            duration: sessionDuration,
            screensViewed: 5,
            actionsPerformed: puzzlesSolved
        )
        
        track(event)
        
        // If highly engaged, track as conversion
        if sessionDuration > 300 && puzzlesSolved > 3 { // 5+ minutes, 3+ puzzles
            trackConversion(event, value: sessionDuration / 60.0) // Value based on minutes engaged
        }
    }
    
    func trackTutorialComplete() {
        track(.tutorialComplete(duration: 0))
        
        // Tutorial completion is a strong conversion signal
        trackConversion(.tutorialComplete(duration: 0), value: 5.0)
    }
    
    func trackDailyPuzzleCompleted(count: Int) {
        let event = AnalyticsEvent("daily_puzzle_completed", parameters: [
            "puzzles_completed": count,
            "engagement_level": count > 3 ? "high" : "medium"
        ])
        
        track(event)
        
        // Daily engagement is valuable
        trackConversion(event, value: Double(count) * 2.0)
    }
    
    func trackRetentionMilestone(day: Int) {
        let event = AnalyticsEvent("retention_milestone", parameters: [
            "day": day,
            "milestone_type": "day_\(day)_retention"
        ])
        
        track(event)
        
        // Retention milestones are valuable conversions
        let value = day == 1 ? 15.0 : day == 7 ? 25.0 : 40.0
        trackConversion(event, value: value)
    }
    
    // MARK: - User Properties for Audience Building
    func setUserProperties() {
        Task {
            await MainActor.run {
                // Set user properties for audience segmentation in Google Ads
                if let user = Auth.auth().currentUser {
                    Analytics.setUserID(user.uid)
                    
                    Analytics.setUserProperty("true", forName: "is_registered_user")
                    Analytics.setUserProperty(getAppVersion(), forName: "app_version")
                    Analytics.setUserProperty(UIDevice.current.model, forName: "device_type")
                    
                    // Set custom properties based on user behavior
                    let puzzlesSolved = UserDefaults.standard.integer(forKey: "total_puzzles_solved")
                    let userLevel = getUserLevel(puzzlesSolved: puzzlesSolved)
                    Analytics.setUserProperty(userLevel, forName: "user_level")
                    
                    print("🔥 Firebase user properties set for Google Ads targeting")
                }
            }
        }
    }
    
    private func getUserLevel(puzzlesSolved: Int) -> String {
        switch puzzlesSolved {
        case 0...10: return "beginner"
        case 11...50: return "intermediate"
        case 51...100: return "advanced"
        default: return "expert"
        }
    }
    
    // MARK: - Enhanced Events for Google Ads
    func trackAppInstall() {
        // Track first app open as install
        if !UserDefaults.standard.bool(forKey: "has_tracked_install") {
            track(AnalyticsEvent("app_install", parameters: [
                "install_source": "unknown", // Google Ads will populate this
                "timestamp": Date().timeIntervalSince1970
            ]))
            
            trackConversion(AnalyticsEvent("app_install"), value: 10.0) // Assign value to installs
            
            UserDefaults.standard.set(true, forKey: "has_tracked_install")
            print("💰 App install conversion tracked")
        }
    }
    
    // MARK: - Conversion tracking extensions
    func trackTutorialCompletionConversion() {
        Analytics.logEvent(AnalyticsEventTutorialComplete, parameters: [
            "completion_time": Date().timeIntervalSince1970,
            "value": 5.0, // Tutorial completion value
            "currency": "USD"
        ])
        
        track(AnalyticsEvent("tutorial_complete_conversion", parameters: [
            "conversion_type": "onboarding",
            "value": 5.0
        ]))
    }
    
    func trackFirstPuzzleConversion(puzzleType: String, score: Int) {
        let hasCompletedFirstPuzzle = UserDefaults.standard.bool(forKey: "has_completed_first_puzzle")
        
        if !hasCompletedFirstPuzzle {
            Analytics.logEvent("first_puzzle_complete", parameters: [
                "puzzle_type": puzzleType,
                "score": score,
                "value": 8.0, // First puzzle completion value
                "currency": "USD"
            ])
            
            UserDefaults.standard.set(true, forKey: "has_completed_first_puzzle")
        }
    }
    
    func trackEngagementConversion(sessionDuration: TimeInterval, puzzlesSolved: Int) {
        let engagementScore = calculateEngagementScore(duration: sessionDuration, puzzles: puzzlesSolved)
        
        if engagementScore >= 0.7 { // High engagement threshold
            Analytics.logEvent("high_engagement_session", parameters: [
                "session_duration": sessionDuration,
                "puzzles_solved": puzzlesSolved,
                "engagement_score": engagementScore,
                "value": engagementScore * 10.0, // Scale engagement to monetary value
                "currency": "USD"
            ])
        }
    }
    
    private func calculateEngagementScore(duration: TimeInterval, puzzles: Int) -> Double {
        let timeScore = min(duration / 1800.0, 1.0) // Normalize to 30 minutes max
        let puzzleScore = min(Double(puzzles) / 10.0, 1.0) // Normalize to 10 puzzles max
        return (timeScore + puzzleScore) / 2.0
    }
    
    // MARK: - Helper Methods
    func trackSafely(_ event: AnalyticsEvent) {
        do {
            track(event)
        } catch {
            print("❌ Failed to track event \(event.name): \(error)")
        }
    }
    
    private func processSpecialEvent(_ event: AnalyticsEvent) async {
        // Handle special events that need additional processing
        switch event.name {
        case "puzzle_complete":
            if let score = event.parameters?["score"] as? Int {
                puzzleCompleted(score: score)
            }
        case "tutorial_complete":
            // Mark tutorial as completed for conversion tracking
            await MainActor.run {
                UserDefaults.standard.set(true, forKey: "tutorial_completed")
            }
        case "level_up":
            // Track level up as conversion
            if let newLevel = event.parameters?["new_level"] as? Int {
                trackConversion(event, value: Double(newLevel * 10))
            }
        default:
            break
        }
    }
    
    private func getAppVersion() -> String {
        return Bundle.main.infoDictionary?["CFBundleShortVersionString"] as? String ?? "1.0.0"
    }
    
    private func getDeviceInfo() -> [String: String] {
        return [
            "device_model": UIDevice.current.model,
            "os_version": UIDevice.current.systemVersion,
            "app_version": getAppVersion()
        ]
    }
}

// MARK: - Analytics Event Definitions (Same API, Firebase implementation)
extension AnalyticsEvent {
    static func userEngagement(
        duration: TimeInterval,
        screensViewed: Int,
        actionsPerformed: Int
    ) -> AnalyticsEvent {
        AnalyticsEvent("user_engagement", parameters: [
            "session_duration": duration,
            "screens_viewed": screensViewed,
            "actions_performed": actionsPerformed,
            "engagement_score": calculateEngagementScore(duration, screensViewed, actionsPerformed)
        ])
    }
    
    static func puzzleStrategy(
        type: String,
        strategy: String,
        success: Bool,
        timeEfficiency: Double
    ) -> AnalyticsEvent {
        AnalyticsEvent("puzzle_strategy", parameters: [
            "puzzle_type": type,
            "strategy_type": strategy,
            "strategy_success": success,
            "time_efficiency": timeEfficiency
        ])
    }
    
    private static func calculateEngagementScore(
        _ duration: TimeInterval,
        _ screens: Int,
        _ actions: Int
    ) -> Double {
        // Custom engagement scoring logic
        let baseScore = min(duration / 60.0, 10.0) // Max 10 points for time
        let screenScore = min(Double(screens) * 0.5, 5.0) // Max 5 points for screens
        let actionScore = min(Double(actions) * 0.2, 5.0) // Max 5 points for actions
        return baseScore + screenScore + actionScore
    }

    static func appOpen() -> AnalyticsEvent {
        AnalyticsEvent("app_open")
    }
    
    static func appBackground() -> AnalyticsEvent {
        AnalyticsEvent("app_background")
    }
    
    static func appCrash(error: String) -> AnalyticsEvent {
        AnalyticsEvent("app_crash", parameters: [
            "error_message": error,
            "timestamp": Date().timeIntervalSince1970
        ])
    }
    
    // MARK: - User Registration & Authentication
    static func userRegistration(method: String) -> AnalyticsEvent {
        AnalyticsEvent("user_registration", parameters: [
            "method": method, // "email", "google", "apple"
            "timestamp": Date().timeIntervalSince1970
        ])
    }
    
    static func userLogin(method: String) -> AnalyticsEvent {
        AnalyticsEvent("user_login", parameters: [
            "method": method,
            "timestamp": Date().timeIntervalSince1970
        ])
    }
    
    static func userLogout() -> AnalyticsEvent {
        AnalyticsEvent("user_logout")
    }
    
    // MARK: - Onboarding Events
    static func tutorialStart() -> AnalyticsEvent {
        AnalyticsEvent("tutorial_start")
    }
    
    static func tutorialStep(step: Int, stepName: String) -> AnalyticsEvent {
        AnalyticsEvent("tutorial_step", parameters: [
            "step_number": step,
            "step_name": stepName
        ])
    }
    
    static func tutorialComplete(duration: TimeInterval) -> AnalyticsEvent {
        AnalyticsEvent("tutorial_complete", parameters: [
            "duration_seconds": duration,
            "completion_rate": 1.0
        ])
    }
    
    static func tutorialSkip(step: Int) -> AnalyticsEvent {
        AnalyticsEvent("tutorial_skip", parameters: [
            "skipped_at_step": step
        ])
    }
    
    // MARK: - Puzzle Events
    static func puzzleStart(type: String, difficulty: String, questionIndex: Int) -> AnalyticsEvent {
        AnalyticsEvent("puzzle_start", parameters: [
            "puzzle_type": type,
            "difficulty": difficulty,
            "question_index": questionIndex,
            "session_id": UUID().uuidString
        ])
    }
    
    static func puzzleComplete(
        type: String,
        difficulty: String,
        isCorrect: Bool,
        timeSpent: TimeInterval,
        score: Int,
        questionIndex: Int,
        totalQuestions: Int
    ) -> AnalyticsEvent {
        AnalyticsEvent("puzzle_complete", parameters: [
            "puzzle_type": type,
            "difficulty": difficulty,
            "is_correct": isCorrect,
            "time_spent_seconds": timeSpent,
            "score": score,
            "question_index": questionIndex,
            "total_questions": totalQuestions,
            "accuracy": isCorrect ? 1.0 : 0.0
        ])
    }
    
    static func puzzleAbandoned(type: String, difficulty: String, timeSpent: TimeInterval, questionIndex: Int) -> AnalyticsEvent {
        AnalyticsEvent("puzzle_abandoned", parameters: [
            "puzzle_type": type,
            "difficulty": difficulty,
            "time_spent_seconds": timeSpent,
            "question_index": questionIndex,
            "abandon_reason": "user_exit"
        ])
    }
    
    static func puzzleHintUsed(type: String, difficulty: String, questionIndex: Int) -> AnalyticsEvent {
        AnalyticsEvent("puzzle_hint_used", parameters: [
            "puzzle_type": type,
            "difficulty": difficulty,
            "question_index": questionIndex
        ])
    }
    
    // MARK: - Progression Events
    static func levelUp(oldLevel: Int, newLevel: Int, totalXP: Int) -> AnalyticsEvent {
        AnalyticsEvent("level_up", parameters: [
            "old_level": oldLevel,
            "new_level": newLevel,
            "total_xp": totalXP,
            "xp_gained": newLevel - oldLevel
        ])
    }
    
    static func achievementUnlocked(achievementId: String, achievementName: String) -> AnalyticsEvent {
        AnalyticsEvent("achievement_unlocked", parameters: [
            "achievement_id": achievementId,
            "achievement_name": achievementName
        ])
    }
    
    static func streakUpdate(currentStreak: Int, bestStreak: Int, streakMultiplier: Float) -> AnalyticsEvent {
        AnalyticsEvent("streak_update", parameters: [
            "current_streak": currentStreak,
            "best_streak": bestStreak,
            "streak_multiplier": streakMultiplier,
            "is_new_best": currentStreak > bestStreak
        ])
    }
    
    // MARK: - Session Events
    static func sessionStart() -> AnalyticsEvent {
        AnalyticsEvent("session_start", parameters: [
            "timestamp": Date().timeIntervalSince1970
        ])
    }
    
    static func sessionEnd(duration: TimeInterval, puzzlesSolved: Int, totalScore: Int) -> AnalyticsEvent {
        AnalyticsEvent("session_end", parameters: [
            "session_duration_seconds": duration,
            "puzzles_solved": puzzlesSolved,
            "total_score": totalScore,
            "avg_score_per_puzzle": puzzlesSolved > 0 ? Double(totalScore) / Double(puzzlesSolved) : 0
        ])
    }
    
    // MARK: - Social Events
    static func shareContent(type: String, method: String, content: String) -> AnalyticsEvent {
        AnalyticsEvent("share_content", parameters: [
            "content_type": type, // "achievement", "score", "level_up"
            "share_method": method, // "twitter", "facebook", "copy_link"
            "content_id": content
        ])
    }
    
    static func leaderboardView(boardType: String) -> AnalyticsEvent {
        AnalyticsEvent("leaderboard_view", parameters: [
            "board_type": boardType // "weekly", "global", "friends"
        ])
    }
    
    // MARK: - Monetization Events
    static func adImpression(adType: String, placement: String) -> AnalyticsEvent {
        AnalyticsEvent("ad_impression", parameters: [
            "ad_type": adType, // "banner", "interstitial", "rewarded"
            "ad_placement": placement // "home", "between_puzzles", "settings"
        ])
    }
    
    static func adClick(adType: String, placement: String) -> AnalyticsEvent {
        AnalyticsEvent("ad_click", parameters: [
            "ad_type": adType,
            "ad_placement": placement
        ])
    }
    
    static func subscriptionView(source: String) -> AnalyticsEvent {
        AnalyticsEvent("subscription_view", parameters: [
            "view_source": source // "paywall", "settings", "level_up"
        ])
    }
    
    static func subscriptionPurchase(tier: String, price: Double, currency: String) -> AnalyticsEvent {
        AnalyticsEvent("subscription_purchase", parameters: [
            "subscription_tier": tier,
            "price": price,
            "currency": currency,
            "purchase_method": "in_app"
        ])
    }
    
    // MARK: - User Behavior Events
    static func difficultyChanged(from: String, to: String, puzzleType: String) -> AnalyticsEvent {
        AnalyticsEvent("difficulty_changed", parameters: [
            "old_difficulty": from,
            "new_difficulty": to,
            "puzzle_type": puzzleType
        ])
    }
    
    static func settingsView() -> AnalyticsEvent {
        AnalyticsEvent("settings_view")
    }
    
    static func helpView(section: String) -> AnalyticsEvent {
        AnalyticsEvent("help_view", parameters: [
            "help_section": section
        ])
    }
    
    // Screen navigation events
    static func screenNavigation(from: String, to: String) -> AnalyticsEvent {
        AnalyticsEvent("screen_navigation", parameters: [
            "from_screen": from,
            "to_screen": to,
            "timestamp": Date().timeIntervalSince1970
        ])
    }
    
    // Puzzle progression events
    static func puzzleProgression(
        fromPuzzle: String,
        toPuzzle: String,
        progress: Double
    ) -> AnalyticsEvent {
        AnalyticsEvent("puzzle_progression", parameters: [
            "from_puzzle": fromPuzzle,
            "to_puzzle": toPuzzle,
            "progress_percentage": progress
        ])
    }
    
    // MARK: - Error Events
    static func networkError(endpoint: String, errorCode: Int, errorMessage: String) -> AnalyticsEvent {
        AnalyticsEvent("network_error", parameters: [
            "endpoint": endpoint,
            "error_code": errorCode,
            "error_message": errorMessage,
            "timestamp": Date().timeIntervalSince1970
        ])
    }
    
    static func puzzleLoadError(puzzleType: String, errorMessage: String) -> AnalyticsEvent {
        AnalyticsEvent("puzzle_load_error", parameters: [
            "puzzle_type": puzzleType,
            "error_message": errorMessage
        ])
    }
}

// MARK: - Session Manager (Firebase-only)
class AnalyticsSessionManager: ObservableObject {
    static let shared = AnalyticsSessionManager()
        
    // Track what has been logged to prevent duplicates
    @Published private var loggedEvents: Set<String> = []
    private var currentSessionId: String = UUID().uuidString
    private var lastAppOpenTime: Date?
    
    // Session tracking
    private var hasLoggedSessionStart = false
    private var puzzleSessionMap: [String: String] = [:] // puzzleId -> sessionId
    
    private init() {}
    
    // MARK: - Safe Analytics Wrapper Methods
    private func safeTrack(_ event: AnalyticsEvent) {
        AnalyticsManager.shared.trackSafely(event)
    }
    
    // MARK: - App Lifecycle Events
    func logAppOpenIfNeeded() {
        let now = Date()
        
        // Only log app open if:
        // 1. Haven't logged it in this session, OR
        // 2. Last app open was more than 30 minutes ago (new session)
        if lastAppOpenTime == nil ||
           (lastAppOpenTime != nil && now.timeIntervalSince(lastAppOpenTime!) > 1800) {
            
            safeTrack(.appOpen())
            lastAppOpenTime = now
            currentSessionId = UUID().uuidString // New session ID
            loggedEvents.removeAll() // Clear previous session events
            hasLoggedSessionStart = false
            print("🔥 App open logged - New session: \(currentSessionId)")
        } else {
            print("🔥 App open skipped - Recent session active")
        }
    }
    
    func logSessionStartIfNeeded() {
        if !hasLoggedSessionStart {
            safeTrack(.sessionStart())
            hasLoggedSessionStart = true
            print("🔥 Session start logged")
        }
    }
    
    // MARK: - Puzzle Events with Deduplication
    func logPuzzleStartIfNeeded(
        puzzleId: String,
        puzzleType: String,
        difficulty: String,
        questionIndex: Int
    ) {
        let eventKey = "puzzle_start_\(puzzleId)_\(questionIndex)"
        
        guard !loggedEvents.contains(eventKey) else {
            print("🔥 Puzzle start skipped - Already logged for \(puzzleId) at index \(questionIndex)")
            return
        }
        
        // Log the event
        safeTrack(.puzzleStart(
            type: puzzleType,
            difficulty: difficulty,
            questionIndex: questionIndex
        ))
        
        // Track this specific puzzle session
        puzzleSessionMap[puzzleId] = currentSessionId
        loggedEvents.insert(eventKey)
        
        print("🔥 Puzzle start logged - Type: \(puzzleType), Puzzle: \(puzzleId), Index: \(questionIndex)")
    }
    
    func logPuzzleCompleteIfNeeded(
        puzzleId: String,
        puzzleType: String,
        difficulty: String,
        isCorrect: Bool,
        timeSpent: TimeInterval,
        score: Int,
        questionIndex: Int,
        totalQuestions: Int
    ) {
        let eventKey = "puzzle_complete_\(puzzleId)_\(questionIndex)"
        
        guard !loggedEvents.contains(eventKey) else {
            print("🔥 Puzzle complete skipped - Already logged for \(puzzleId)")
            return
        }
        
        safeTrack(.puzzleComplete(
            type: puzzleType,
            difficulty: difficulty,
            isCorrect: isCorrect,
            timeSpent: timeSpent,
            score: score,
            questionIndex: questionIndex,
            totalQuestions: totalQuestions
        ))
        
        loggedEvents.insert(eventKey)
        print("🔥 Puzzle complete logged - \(puzzleId), Correct: \(isCorrect)")
    }
    
    func logPuzzleAbandonedIfNeeded(
        puzzleId: String,
        puzzleType: String,
        difficulty: String,
        timeSpent: TimeInterval,
        questionIndex: Int
    ) {
        let eventKey = "puzzle_abandoned_\(puzzleId)_\(questionIndex)"
        
        guard !loggedEvents.contains(eventKey) else {
            print("🔥 Puzzle abandoned skipped - Already logged for \(puzzleId)")
            return
        }
        
        AnalyticsManager.shared.track(.puzzleAbandoned(
            type: puzzleType,
            difficulty: difficulty,
            timeSpent: timeSpent,
            questionIndex: questionIndex
        ))
        
        loggedEvents.insert(eventKey)
        print("🔥 Puzzle abandoned logged - \(puzzleId)")
    }
    
    // MARK: - User Actions with Deduplication
    func logUserActionIfNeeded(
        action: String,
        context: String = "",
        puzzleId: String? = nil
    ) {
        let contextSuffix = context.isEmpty ? "" : "_\(context)"
        let puzzleSuffix = puzzleId != nil ? "_\(puzzleId!)" : ""
        let eventKey = "user_action_\(action)\(contextSuffix)\(puzzleSuffix)"
        
        // Actions can be logged multiple times but not within 5 seconds
        let timeBasedKey = "\(eventKey)_\(Int(Date().timeIntervalSince1970 / 5))"
        
        guard !loggedEvents.contains(timeBasedKey) else {
            print("🔥 User action skipped - Recently logged: \(action)")
            return
        }
        
        var parameters: [String: Any] = [
            "action": action,
            "session_id": currentSessionId,
            "timestamp": Date().timeIntervalSince1970
        ]
        
        if !context.isEmpty {
            parameters["context"] = context
        }
        
        if let puzzleId = puzzleId {
            parameters["puzzle_id"] = puzzleId
        }
        
        safeTrack(AnalyticsEvent("user_action", parameters: parameters))
        loggedEvents.insert(timeBasedKey)
        print("🔥 User action logged - \(action)")
    }
    
    // MARK: - Screen View Events with Deduplication
    func logScreenViewIfNeeded(screenName: String) {
        let eventKey = "screen_view_\(screenName)"
        
        // Screen views can be logged multiple times but not within the same minute
        let timeBasedKey = "\(eventKey)_\(Int(Date().timeIntervalSince1970 / 60))"
        
        guard !loggedEvents.contains(timeBasedKey) else {
            print("🔥 Screen view skipped - Recently logged for \(screenName)")
            return
        }
        
        AnalyticsManager.shared.track(AnalyticsEvent("screen_view", parameters: [
            "screen_name": screenName,
            "session_id": currentSessionId,
            "timestamp": Date().timeIntervalSince1970
        ]))
        
        loggedEvents.insert(timeBasedKey)
        print("🔥 Screen view logged - \(screenName)")
    }
    
    // MARK: - Reset Methods
    func resetSession() {
        currentSessionId = UUID().uuidString
        loggedEvents.removeAll()
        hasLoggedSessionStart = false
        puzzleSessionMap.removeAll()
        lastAppOpenTime = nil
        print("🔥 Analytics session reset")
    }
    
    func getCurrentSessionId() -> String {
        return currentSessionId
    }
    
    // MARK: - Cleanup Methods
    func cleanupOldEvents() {
        // Remove events older than 1 hour to prevent memory buildup
        let cutoffTime = Date().timeIntervalSince1970 - 3600
        
        loggedEvents = loggedEvents.filter { eventKey in
            // Extract timestamp if it exists in the key
            let components = eventKey.components(separatedBy: "_")
            if let lastComponent = components.last,
               let timestamp = Double(lastComponent) {
                return timestamp > cutoffTime
            }
            return true // Keep events without timestamps
        }
    }
}

// MARK: - Campaign Tracker (Firebase-only)
class CampaignTracker: ObservableObject {
    static let shared = CampaignTracker()
    
    private init() {}
    
    // Track key conversion events for Google Ads optimization
    func trackKeyConversions() {
        // Tutorial completion
        AnalyticsManager.shared.trackTutorialComplete()
        
        // Daily engagement
        let dailyPuzzles = UserDefaults.standard.integer(forKey: "daily_puzzles_completed")
        if dailyPuzzles > 0 {
            AnalyticsManager.shared.trackDailyPuzzleCompleted(count: dailyPuzzles)
        }
        
        // Retention tracking
        let daysActive = calculateDaysActive()
        if [1, 3, 7, 14, 30].contains(daysActive) {
            AnalyticsManager.shared.trackRetentionMilestone(day: daysActive)
        }
    }
    
    private func calculateDaysActive() -> Int {
        guard let firstOpen = UserDefaults.standard.object(forKey: "first_app_open") as? Date else {
            UserDefaults.standard.set(Date(), forKey: "first_app_open")
            return 1
        }
        
        let daysSinceFirstOpen = Calendar.current.dateComponents([.day], from: firstOpen, to: Date()).day ?? 1
        return max(1, daysSinceFirstOpen)
    }
}

// MARK: - SwiftUI View Extensions
extension View {
    func trackScreenViewOnce(_ screenName: String) -> some View {
        self.onAppear {
            AnalyticsManager.shared.trackSafely(AnalyticsEvent("screen_view", parameters: [
                "screen_name": screenName,
                "timestamp": Date().timeIntervalSince1970
            ]))
        }
    }
    
    func trackPuzzleViewOnce(
        puzzleId: String,
        puzzleType: String,
        difficulty: String,
        questionIndex: Int
    ) -> some View {
        self.onAppear {
            AnalyticsSessionManager.shared.logPuzzleStartIfNeeded(
                puzzleId: puzzleId,
                puzzleType: puzzleType,
                difficulty: difficulty,
                questionIndex: questionIndex
            )
        }
    }
    
    func trackUserAction(
        action: String,
        context: String = "",
        puzzleId: String? = nil
    ) -> some View {
        self.onTapGesture {
            AnalyticsSessionManager.shared.logUserActionIfNeeded(
                action: action,
                context: context,
                puzzleId: puzzleId
            )
        }
    }
    
    func trackScreenView(_ screenName: String) -> some View {
        self.onAppear {
            AnalyticsManager.shared.trackSafely(AnalyticsEvent("screen_view", parameters: [
                "screen_name": screenName,
                "timestamp": Date().timeIntervalSince1970
            ]))
        }
    }
    
    func trackButtonTap(_ buttonName: String, context: String = "") -> some View {
        self.onTapGesture {
            var parameters: [String: Any] = ["button_name": buttonName]
            if !context.isEmpty {
                parameters["context"] = context
            }
            AnalyticsManager.shared.trackSafely(AnalyticsEvent("button_tap", parameters: parameters))
        }
    }
    
    func withPuzzleAnalytics(
        puzzleType: String,
        difficulty: String,
        questionIndex: Int,
        totalQuestions: Int
    ) -> some View {
        self
            .onAppear {
                AnalyticsManager.shared.track(.puzzleStart(
                    type: puzzleType,
                    difficulty: difficulty,
                    questionIndex: questionIndex
                ))
            }
    }
}

// MARK: - App Lifecycle Manager
class AppAnalyticsLifecycleManager: ObservableObject {
    init() {
        setupNotifications()
    }
    
    private func setupNotifications() {
        NotificationCenter.default.addObserver(
            forName: UIApplication.didBecomeActiveNotification,
            object: nil,
            queue: .main
        ) { _ in
            AnalyticsSessionManager.shared.logAppOpenIfNeeded()
            AnalyticsSessionManager.shared.logSessionStartIfNeeded()
        }
        
        NotificationCenter.default.addObserver(
            forName: UIApplication.didEnterBackgroundNotification,
            object: nil,
            queue: .main
        ) { _ in
            AnalyticsManager.shared.track(.appBackground())
        }
        
        NotificationCenter.default.addObserver(
            forName: UIApplication.willTerminateNotification,
            object: nil,
            queue: .main
        ) { _ in
            AnalyticsManager.shared.endSession()
        }
    }
}

// MARK: - App Lifecycle Integration
class AppLifecycleManager: ObservableObject {
    init() {
        NotificationCenter.default.addObserver(
            forName: UIApplication.didBecomeActiveNotification,
            object: nil,
            queue: .main
        ) { _ in
            AnalyticsManager.shared.startSession()
        }
        
        NotificationCenter.default.addObserver(
            forName: UIApplication.didEnterBackgroundNotification,
            object: nil,
            queue: .main
        ) { _ in
            AnalyticsManager.shared.endSession()
            AnalyticsManager.shared.track(.appBackground())
        }
    }
}

// MARK: - Analytics Integration with Existing Systems
extension ProgressionManager {
    func trackLevelUp(oldLevel: Int, newLevel: Int, totalXP: Int) {
        AnalyticsManager.shared.track(.levelUp(
            oldLevel: oldLevel,
            newLevel: newLevel,
            totalXP: totalXP
        ))
        
        // Track as conversion event for ad optimization
        AnalyticsManager.shared.trackConversion(.levelUp(
            oldLevel: oldLevel,
            newLevel: newLevel,
            totalXP: totalXP
        ), value: Double(newLevel * 10)) // Assign monetary value to level ups
    }
    
    func trackAchievementUnlocked(_ achievement: Achievement) {
        AnalyticsManager.shared.track(.achievementUnlocked(
            achievementId: achievement.id,
            achievementName: achievement.title
        ))
    }
    
    func trackStreakUpdate(_ streakInfo: StreakInfo) {
        AnalyticsManager.shared.track(.streakUpdate(
            currentStreak: streakInfo.currentStreak,
            bestStreak: streakInfo.bestStreak,
            streakMultiplier: streakInfo.streakMultiplier
        ))
    }
}
