//
//  AppDelegate.swift
//  PuzzleForge
//
//  Created by Sushanth Tiruvaipati on 6/25/25.
//


//
//  AppDelegate+Notifications.swift
//  PuzzleForge
//
//  Created by Assistant on 6/25/25.
//

import UIKit
import Firebase
import FirebaseMessaging

class AppDelegate: NSObject, UIApplicationDelegate {
    
    func application(
        _ application: UIApplication,
        didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]?
    ) -> Bool {
        print("=== APP STARTED - LOGS SHOULD APPEAR ===")
        
        // Configure Firebase (if not already done in App.swift)
        // FirebaseApp.configure()
        configureCampaignMeasurement()
        
        // Set up notifications
        setupNotifications(application: application)        
        return true
    }
    
    private func configureCampaignMeasurement() {
        // Enable analytics collection for all builds (production and debug)
        Analytics.setAnalyticsCollectionEnabled(true)

        #if DEBUG
        print("🎯 Firebase Analytics enabled (DEBUG mode)")
        #else
        print("🎯 Firebase Analytics enabled (RELEASE mode)")
        #endif

        // Set default campaign parameters
        Analytics.setDefaultEventParameters([
            "app_version": Bundle.main.infoDictionary?["CFBundleShortVersionString"] as? String ?? "1.0.0",
            "platform": "ios"
        ])

        print("🎯 Campaign measurement configured in AppDelegate")
    }
    
    // MARK: - Notification Setup
    func setupNotifications(application: UIApplication) {
        // Initialize NotificationManager
        let _ = NotificationManager.shared
        
        // Request FCM token
        NotificationManager.shared.getFCMToken()
    }
    
    // MARK: - Remote Notification Registration
    func application(
        _ application: UIApplication,
        didRegisterForRemoteNotificationsWithDeviceToken deviceToken: Data
    ) {
        print("📱 Device token registered: \(deviceToken)")
        
        // Set the APNs token for FCM
        Messaging.messaging().apnsToken = deviceToken
    }
    
    func application(
        _ application: UIApplication,
        didFailToRegisterForRemoteNotificationsWithError error: Error
    ) {
        print("❌ Failed to register for remote notifications: \(error)")
    }
    
    func application(_ application: UIApplication, continue userActivity: NSUserActivity, restorationHandler: @escaping ([UIUserActivityRestoring]?) -> Void) -> Bool {
            
        // Track campaign attribution from dynamic links
        if let url = userActivity.webpageURL {
            Analytics.logEvent("dynamic_link_opened", parameters: [
                "link_url": url.absoluteString,
                "source": userActivity.activityType
            ])
        }
        
        return true
    }
}

// MARK: - Handle background app refresh and notification interactions
extension AppDelegate {
    
    // Handle notification when app is killed/background
    func application(
        _ application: UIApplication,
        didReceiveRemoteNotification userInfo: [AnyHashable: Any],
        fetchCompletionHandler completionHandler: @escaping (UIBackgroundFetchResult) -> Void
    ) {
        print("📱 Background notification received: \(userInfo)")
        
        // Let FCM handle the notification
        Messaging.messaging().appDidReceiveMessage(userInfo)
        
        // Track the notification
        if let type = userInfo["type"] as? String {
            AnalyticsManager.shared.trackSafely(AnalyticsEvent("background_notification_received", parameters: [
                "type": type
            ]))
        }
        
        completionHandler(.newData)
    }
}
