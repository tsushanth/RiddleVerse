//
//  NotificationManager.swift
//  PuzzleForge
//
//  Created by Sushanth Tiruvaipati on 6/25/25.
//


//
//  NotificationManager.swift
//  PuzzleForge
//
//  Created by Assistant on 6/25/25.
//

import Foundation
import Firebase
import FirebaseMessaging
import UserNotifications
import UIKit

// Add to NotificationManager.swift

extension NotificationManager {
    
    // MARK: - App Badge Management
    func updateAppBadge(count: Int) {
        DispatchQueue.main.async {
            UIApplication.shared.applicationIconBadgeNumber = count
        }
    }
    
    func clearAppBadge() {
        updateAppBadge(count: 0)
    }
    
    // MARK: - Notification Categories
    func setupNotificationCategories() {
        let puzzleCompleteAction = UNNotificationAction(
            identifier: "VIEW_SCORE",
            title: "View Score",
            options: [.foreground]
        )
        
        let dailyPuzzleAction = UNNotificationAction(
            identifier: "START_PUZZLE",
            title: "Start Puzzle",
            options: [.foreground]
        )
        
        let puzzleCompleteCategory = UNNotificationCategory(
            identifier: "PUZZLE_COMPLETE",
            actions: [puzzleCompleteAction],
            intentIdentifiers: [],
            options: []
        )
        
        let dailyPuzzleCategory = UNNotificationCategory(
            identifier: "DAILY_PUZZLE",
            actions: [dailyPuzzleAction],
            intentIdentifiers: [],
            options: []
        )
        
        UNUserNotificationCenter.current().setNotificationCategories([
            puzzleCompleteCategory,
            dailyPuzzleCategory
        ])
    }
    
    // MARK: - Production Notification Handling
    func handleNotificationResponse(_ response: UNNotificationResponse) {
        let userInfo = response.notification.request.content.userInfo
        
        switch response.actionIdentifier {
        case "VIEW_SCORE":
            // Navigate to leaderboard or achievements
            NotificationCenter.default.post(name: NSNotification.Name("NavigateToLeaderboard"), object: nil)
            
        case "START_PUZZLE":
            // Navigate to daily puzzles
            NotificationCenter.default.post(name: NSNotification.Name("NavigateToDailyPuzzles"), object: nil)
            
        default:
            // Handle default notification tap
            break
        }
        
        // Clear badge when user interacts
        clearAppBadge()
    }
    
    // MARK: - Smart Notification Timing
    func scheduleSmartReminder() {
        // Only notify if user hasn't played today
        let lastPlayDate = UserDefaults.standard.object(forKey: "last_play_date") as? Date
        let today = Calendar.current.startOfDay(for: Date())
        
        if let lastPlay = lastPlayDate {
            let lastPlayDay = Calendar.current.startOfDay(for: lastPlay)
            if lastPlayDay >= today {
                print("User already played today, skipping reminder")
                return
            }
        }
        
        // Schedule for optimal engagement time (typically 7-9 PM)
        var dateComponents = DateComponents()
        dateComponents.hour = 19 // 7 PM
        dateComponents.minute = 0
        
        let content = UNMutableNotificationContent()
        content.title = "🧩 Daily Brain Training"
        content.body = "Ready for today's puzzle challenge?"
        content.categoryIdentifier = "DAILY_PUZZLE"
        content.sound = .default
        
        let trigger = UNCalendarNotificationTrigger(dateMatching: dateComponents, repeats: true)
        let request = UNNotificationRequest(identifier: "daily_reminder", content: content, trigger: trigger)
        
        UNUserNotificationCenter.current().add(request) { error in
            if let error = error {
                print("Failed to schedule daily reminder: \(error)")
            } else {
                print("Daily reminder scheduled for 7 PM")
            }
        }
    }
}

class NotificationManager: NSObject, ObservableObject {
    static let shared = NotificationManager()
    
    @Published var isNotificationPermissionGranted = false
    @Published var fcmToken: String?
    
    private let serverURL = "https://puzzleverseai.com" // Replace with your actual server URL
    
    override init() {
        super.init()
        setupFirebaseMessaging()
    }
    
    // MARK: - Setup
    func setupFirebaseMessaging() {
        // Set messaging delegate
        Messaging.messaging().delegate = self
        
        // Set UNUserNotificationCenter delegate for foreground notifications
        UNUserNotificationCenter.current().delegate = self
        
        // Request notification permissions
        requestNotificationPermission()
    }
    
    // MARK: - Permission Request
    func requestNotificationPermission() {
        UNUserNotificationCenter.current().requestAuthorization(
            options: [.alert, .sound, .badge]
        ) { [weak self] granted, error in
            DispatchQueue.main.async {
                self?.isNotificationPermissionGranted = granted
                print("📱 Notification permission granted: \(granted)")
                
                if granted {
                    // Register for remote notifications
                    DispatchQueue.main.async {
                        UIApplication.shared.registerForRemoteNotifications()
                    }
                } else if let error = error {
                    print("❌ Notification permission error: \(error)")
                }
            }
        }
    }
    
    // MARK: - FCM Token Management
    func getFCMToken() {
        Messaging.messaging().token { [weak self] token, error in
            if let error = error {
                print("❌ Error fetching FCM token: \(error)")
                return
            }
            
            guard let token = token else {
                print("❌ FCM token is nil")
                return
            }
            
            DispatchQueue.main.async {
                self?.fcmToken = token
                print("✅ FCM Token received: \(token.prefix(20))...")
                
                // Store token for current user if available
                self?.registerTokenWithServer(token: token)
            }
        }
    }
    
    // MARK: - Server Integration
    func registerTokenWithServer(token: String) {
        // Get current user email (you'll need to implement this based on your auth system)
        guard let userEmail = getCurrentUserEmail() else {
            print("⚠️ No user email available, storing token locally for later")
            UserDefaults.standard.set(token, forKey: "pending_fcm_token")
            return
        }
        
        registerTokenForUser(userEmail: userEmail, fcmToken: token)
    }
    
    func registerTokenForUser(userEmail: String, fcmToken: String) {
        guard let url = URL(string: "\(serverURL)/register-fcm-token") else {
            print("❌ Invalid server URL")
            return
        }
        
        let payload = [
            "userEmail": userEmail,
            "fcmToken": fcmToken,
            "platform": "ios"
        ]
        
        var request = URLRequest(url: url)
        request.httpMethod = "POST"
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        
        do {
            request.httpBody = try JSONSerialization.data(withJSONObject: payload)
        } catch {
            print("❌ Error encoding FCM token payload: \(error)")
            return
        }
        
        URLSession.shared.dataTask(with: request) { data, response, error in
            if let error = error {
                print("❌ Failed to register FCM token: \(error)")
                return
            }
            
            if let httpResponse = response as? HTTPURLResponse {
                if httpResponse.statusCode == 200 {
                    print("✅ FCM token registered successfully with server")
                    // Clear any pending token
                    UserDefaults.standard.removeObject(forKey: "pending_fcm_token")
                } else {
                    print("❌ Failed to register FCM token. Status code: \(httpResponse.statusCode)")
                }
            }
        }.resume()
    }
    
    // MARK: - User Session Management
    func onUserSignIn(userEmail: String) {
        // Register current FCM token for this user
        if let token = fcmToken {
            registerTokenForUser(userEmail: userEmail, fcmToken: token)
        }
        
        // Check for any pending token
        if let pendingToken = UserDefaults.standard.string(forKey: "pending_fcm_token") {
            registerTokenForUser(userEmail: userEmail, fcmToken: pendingToken)
        }
    }
    
    func onUserSignOut() {
        // Optionally deactivate the token on the server
        // For now, we'll just clear local references
        fcmToken = nil
    }
    
    // MARK: - Utility
    private func getCurrentUserEmail() -> String? {
        // TODO: Implement this based on your authentication system
        // This should return the current logged-in user's email
        // For example, if using Firebase Auth:
        // return Auth.auth().currentUser?.email
        
        // Placeholder implementation
        return UserDefaults.standard.string(forKey: "current_user_email")
    }
    
    // MARK: - Test Notification
    func sendTestNotification() {
        guard let userEmail = getCurrentUserEmail() else {
            print("❌ No user email for test notification")
            return
        }
        
        guard let url = URL(string: "\(serverURL)/test-notification") else {
            print("❌ Invalid server URL for test")
            return
        }
        
        let payload = [
            "userEmail": userEmail,
            "title": "🧪 Test Notification",
            "body": "This is a test notification from RiddleVerse!"
        ]
        
        var request = URLRequest(url: url)
        request.httpMethod = "POST"
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        
        do {
            request.httpBody = try JSONSerialization.data(withJSONObject: payload)
        } catch {
            print("❌ Error encoding test notification payload: \(error)")
            return
        }
        
        URLSession.shared.dataTask(with: request) { data, response, error in
            if let error = error {
                print("❌ Failed to send test notification: \(error)")
                return
            }
            
            if let httpResponse = response as? HTTPURLResponse {
                print("📱 Test notification response: \(httpResponse.statusCode)")
            }
        }.resume()
    }
}

// MARK: - MessagingDelegate
extension NotificationManager: MessagingDelegate {
    func messaging(_ messaging: Messaging, didReceiveRegistrationToken fcmToken: String?) {
        guard let fcmToken = fcmToken else { return }
        
        print("✅ FCM token refreshed: \(fcmToken.prefix(20))...")
        
        DispatchQueue.main.async {
            self.fcmToken = fcmToken
            // Re-register with server when token refreshes
            self.registerTokenWithServer(token: fcmToken)
        }
    }
}

// MARK: - UNUserNotificationCenterDelegate
extension NotificationManager: UNUserNotificationCenterDelegate {
    // Handle notifications when app is in foreground
    func userNotificationCenter(
        _ center: UNUserNotificationCenter,
        willPresent notification: UNNotification,
        withCompletionHandler completionHandler: @escaping (UNNotificationPresentationOptions) -> Void
    ) {
        let userInfo = notification.request.content.userInfo
        
        // Print notification details
        print("📱 Foreground notification received: \(userInfo)")
        
        // Show notification even when app is in foreground
        completionHandler([.alert, .sound, .badge])
        
        // Track notification received
        handleNotificationReceived(userInfo: userInfo)
    }
    
    // Handle notification tap when app is background/closed
    func userNotificationCenter(
        _ center: UNUserNotificationCenter,
        didReceive response: UNNotificationResponse,
        withCompletionHandler completionHandler: @escaping () -> Void
    ) {
        let userInfo = response.notification.request.content.userInfo
        
        print("📱 Notification tapped: \(userInfo)")
        
        // Handle notification tap
        handleNotificationTapped(userInfo: userInfo)
        
        completionHandler()
    }
    
    // MARK: - Notification Handling
    private func handleNotificationReceived(userInfo: [AnyHashable: Any]) {
        // Extract custom data
        if let type = userInfo["type"] as? String {
            print("📱 Notification type: \(type)")
            
            // Track analytics
            AnalyticsManager.shared.trackSafely(AnalyticsEvent("notification_received", parameters: [
                "type": type,
                "source": "fcm"
            ]))
        }
    }
    
    private func handleNotificationTapped(userInfo: [AnyHashable: Any]) {
        // Extract custom data and handle navigation
        if let type = userInfo["type"] as? String {
            print("📱 Handling notification tap for type: \(type)")
            
            switch type {
            case "daily_puzzles":
                // Navigate to daily puzzles
                handleDailyPuzzleNotification(userInfo: userInfo)
            case "test":
                // Handle test notification
                print("📱 Test notification tapped")
            default:
                print("📱 Unknown notification type: \(type)")
            }
            
            // Track analytics
            AnalyticsManager.shared.trackSafely(AnalyticsEvent("notification_tapped", parameters: [
                "type": type,
                "source": "fcm"
            ]))
        }
    }
    
    private func handleDailyPuzzleNotification(userInfo: [AnyHashable: Any]) {
        // Extract topics and puzzle count
        if let topicsString = userInfo["topics"] as? String,
           let puzzleCountString = userInfo["puzzleCount"] as? String {
            
            print("📅 Daily puzzles available - Topics: \(topicsString), Count: \(puzzleCountString)")
            
            // You can post a notification to navigate to daily puzzles view
            NotificationCenter.default.post(
                name: NSNotification.Name("NavigateToDailyPuzzles"),
                object: nil,
                userInfo: [
                    "topics": topicsString,
                    "puzzleCount": puzzleCountString
                ]
            )
        }
    }
}
