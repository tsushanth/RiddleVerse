//
//  PuzzleForgeApp.swift
//  PuzzleForge
//
//  Created by Sushanth Tiruvaipati on 5/23/25.
//

import SwiftUI
import Firebase
import GoogleMobileAds
import FBSDKCoreKit
import FirebaseAuth
import AdSupport
import AppTrackingTransparency
import RevenueCat
import TikTokBusinessSDK

@main
struct PuzzleForgeApp: App {
    @StateObject private var appLifecycleManager = AppLifecycleManager()
    @StateObject private var notificationManager = NotificationManager.shared
    @StateObject private var authStateManager = AuthStateManager()
    @StateObject private var subscriptionManager = SubscriptionManager.shared
    
    @UIApplicationDelegateAdaptor(AppDelegate.self) var delegate
    
    init() {
        // Configure Firebase first
        FirebaseApp.configure()

        // Initialize TikTok Events SDK
        TikTokHelper.shared.initialize()

        // Configure RevenueCat
        Purchases.configure(withAPIKey: "appl_tZKpnerYPqsLVwlYzmvgIywpjnP")

        // Configure Firebase Analytics for campaign measurement
        configureCampaignTracking()
        
        // Initialize Google Mobile Ads
        MobileAds.shared.start { status in
            print("Google Mobile Ads SDK initialized with status: \(status)")
        }
        
        // Initialize Facebook SDK
        ApplicationDelegate.shared.application(
            UIApplication.shared,
            didFinishLaunchingWithOptions: nil
        )
        
        // EXPLICITLY INITIALIZE ANALYTICS EARLY
        let _ = AnalyticsManager.shared
        print("📊 Analytics Manager pre-initialized in app launch")
        
        // Track app install on first launch with enhanced attribution
        trackAppInstallWithAttribution()

        // Request ATT on every launch if not yet determined
        requestTrackingPermission()

        if let currentUser = Auth.auth().currentUser {
            Purchases.shared.logIn(currentUser.uid) { _, _, _ in }
            Task.detached(priority: .medium) {
                //await PuzzleQueueManager.shared.initializeQueues()
            }
        }
    }
    
    private func configureCampaignTracking() {
        // Enable advertising ID collection
        Analytics.setAnalyticsCollectionEnabled(true)
        
        // Set session timeout for better campaign attribution
        Analytics.setSessionTimeoutInterval(1800) // 30 minutes
        
        // Enable automatic screen tracking
        //Analytics.setScreenName("app_launch", screenClass: "PuzzleForgeApp")
        
        print("🎯 Campaign tracking configured for iOS app campaigns")
    }
    
    private func trackAppInstallWithAttribution() {
        let hasLaunchedBefore = UserDefaults.standard.bool(forKey: "HasLaunchedBefore")
        
        if !hasLaunchedBefore {
            // Track install with Firebase Analytics (Google Ads will auto-populate source)
            Analytics.logEvent("app_install", parameters: [
                "platform": "ios",
                "timestamp": Date().timeIntervalSince1970,
                "first_launch": true
            ])
            
            // Track as conversion event with value
            Analytics.logEvent("install_conversion", parameters: [
                "value": 10.0, // Assign value to installs
                "currency": "USD",
                "platform": "ios"
            ])
            
            // Facebook tracking (existing)
            AppEvents.shared.logEvent(AppEvents.Name("fb_mobile_first_day_retention"))
            
            // Custom analytics (existing)
            AnalyticsManager.shared.trackSafely(AnalyticsEvent("app_install", parameters: [
                "install_date": Date().timeIntervalSince1970,
                "platform": "ios",
                "attribution_source": "unknown" // Google Ads will populate this
            ]))
            
            UserDefaults.standard.set(true, forKey: "HasLaunchedBefore")
            UserDefaults.standard.set(Date(), forKey: "app_install_date")
            
            print("🎯 Enhanced app install tracking completed")
        }
        
        AppEvents.shared.activateApp()
    }
    
    private func requestTrackingPermission() {
        if #available(iOS 14.5, *) {
            // Only prompt if not yet determined
            guard ATTrackingManager.trackingAuthorizationStatus == .notDetermined else { return }
            // Delay to ensure app's first view is visible (required by Apple)
            DispatchQueue.main.asyncAfter(deadline: .now() + 1.0) {
                ATTrackingManager.requestTrackingAuthorization { status in
                    DispatchQueue.main.async {
                        switch status {
                        case .authorized:
                            print("🎯 Tracking authorized - full campaign attribution available")
                            Analytics.logEvent("tracking_permission_granted", parameters: [:])
                        case .denied:
                            print("🎯 Tracking denied - limited attribution available")
                            Analytics.logEvent("tracking_permission_denied", parameters: [:])
                        case .notDetermined:
                            print("🎯 Tracking permission not determined")
                        case .restricted:
                            print("🎯 Tracking restricted")
                        @unknown default:
                            break
                        }
                    }
                }
            }
        }
    }
    
    var body: some Scene {
        WindowGroup {
            Group {
                if authStateManager.isLoading {
                    // Show loading screen while checking auth state
                    MainLoadingView()
                } else if authStateManager.canAccessHome {
                    // User is signed in or browsing as guest
                    HomeView()
                } else {
                    // User is not signed in, show welcome flow
                    WelcomeView()
                }
            }
            .environmentObject(notificationManager)
            .environmentObject(authStateManager)
            .environmentObject(subscriptionManager)
            .onAppear {
                setupNotificationsOnAppear()
            }
            .onOpenURL { url in
                ApplicationDelegate.shared.application(
                    UIApplication.shared,
                    open: url,
                    sourceApplication: nil,
                    annotation: [UIApplication.OpenURLOptionsKey.annotation]
                )
            }
            .onReceive(NotificationCenter.default.publisher(for: UIApplication.didBecomeActiveNotification)) { _ in
                // Track app activation each time app becomes active
                AppEvents.shared.activateApp()
                trackSessionStart()
                
                // SAFE ANALYTICS TRACKING
                AnalyticsManager.shared.trackSafely(.appOpen())
                
                notificationManager.getFCMToken()
            }
            .onReceive(NotificationCenter.default.publisher(for: NSNotification.Name("NavigateToDailyPuzzles"))) { notification in
                handleDailyPuzzleNavigation(notification: notification)
            }
            .onReceive(authStateManager.$isSignedIn) { isSignedIn in
                if isSignedIn {
                    if let uid = Auth.auth().currentUser?.uid {
                        Purchases.shared.logIn(uid) { _, _, _ in }
                    }
                    Task.detached(priority: .medium) {
                        print("User signed in, initializing puzzle queues...")
                        await PuzzleQueueManager.shared.initializeQueues()
                    }
                } else {
                    // Clear queues when user signs out for privacy
                    PuzzleQueueManager.shared.clearAllQueues()
                    print("User signed out, cleared puzzle queues")
                }
            }
            .onReceive(authStateManager.$isGuestMode) { isGuest in
                if isGuest {
                    Task.detached(priority: .medium) {
                        print("Guest mode active, initializing puzzle queues...")
                        await PuzzleQueueManager.shared.initializeQueues()
                    }
                }
            }
        }
    }
    
    private func trackSessionStart() {
        // Track session start with user properties for campaign optimization
        Analytics.logEvent(AnalyticsEventAppOpen, parameters: [
            "session_start": true,
            "timestamp": Date().timeIntervalSince1970
        ])
        
        // Update user properties for better campaign targeting
        updateUserPropertiesForCampaigns()
    }

    private func updateUserPropertiesForCampaigns() {
        // Set user properties that help with campaign optimization
        Analytics.setUserProperty("ios", forName: "platform")
        Analytics.setUserProperty(UIDevice.current.model, forName: "device_model")
        Analytics.setUserProperty(UIDevice.current.systemVersion, forName: "ios_version")
        
        // Calculate days since install
        if let installDate = UserDefaults.standard.object(forKey: "app_install_date") as? Date {
            let daysSinceInstall = Calendar.current.dateComponents([.day], from: installDate, to: Date()).day ?? 0
            Analytics.setUserProperty("\(daysSinceInstall)", forName: "days_since_install")
            
            // Track retention milestones
            if [1, 3, 7, 14, 30].contains(daysSinceInstall) {
                Analytics.logEvent("retention_milestone", parameters: [
                    "days_since_install": daysSinceInstall,
                    "milestone_type": "day_\(daysSinceInstall)",
                    "value": Double(daysSinceInstall * 2), // Assign increasing value to longer retention
                    "currency": "USD"
                ])
            }
        }
    }
    
    private func setupNotificationsOnAppear() {
        if !notificationManager.isNotificationPermissionGranted {
            notificationManager.requestNotificationPermission()
        }
        notificationManager.getFCMToken()
    }
    
    private func handleDailyPuzzleNavigation(notification: Notification) {
        guard let userInfo = notification.userInfo else { return }
        
        let topics = userInfo["topics"] as? String ?? ""
        let puzzleCount = userInfo["puzzleCount"] as? String ?? ""
        
        print("📅 App-level navigation to daily puzzles - Topics: \(topics), Count: \(puzzleCount)")
        
        AnalyticsManager.shared.trackSafely(AnalyticsEvent("daily_puzzle_navigation", parameters: [
            "source": "app_notification_handler",
            "topics": topics,
            "puzzle_count": puzzleCount
        ]))
        
        if notification.userInfo?["fromPushNotification"] as? Bool == true {
            DispatchQueue.main.asyncAfter(deadline: .now() + 0.5) {
                print("📱 Navigated to daily puzzles from push notification")
            }
        }
    }
    
    private func trackAppInstallIfFirstLaunch() {
        let hasLaunchedBefore = UserDefaults.standard.bool(forKey: "HasLaunchedBefore")
        
        if !hasLaunchedBefore {
            AppEvents.shared.logEvent(AppEvents.Name("fb_mobile_first_day_retention"))
            UserDefaults.standard.set(true, forKey: "HasLaunchedBefore")
            print("Facebook: App install tracked")
            
            AnalyticsManager.shared.trackSafely(AnalyticsEvent("app_install", parameters: [
                "install_date": Date().timeIntervalSince1970,
                "platform": "ios"
            ]))
            
            AnalyticsManager.shared.trackSafely(AnalyticsEvent("notification_permission_requested", parameters: [
                "trigger": "first_launch"
            ]))
        }
        
        AppEvents.shared.activateApp()
        print("Facebook: App activation tracked")
    }
}

// MARK: - Auth State Manager
class AuthStateManager: ObservableObject {
    @Published var isSignedIn = false
    @Published var isGuestMode = false
    @Published var isLoading = true
    @Published var currentUser: FirebaseAuth.User?
    @Published var showSignInPrompt = false

    private var authStateHandle: AuthStateDidChangeListenerHandle?

    /// True if user is authenticated OR browsing as guest
    var canAccessHome: Bool {
        isSignedIn || isGuestMode
    }

    init() {
        // Listen for auth state changes
        authStateHandle = Auth.auth().addStateDidChangeListener { [weak self] auth, user in
            DispatchQueue.main.async {
                self?.currentUser = user
                self?.isSignedIn = user != nil
                if user != nil {
                    self?.isGuestMode = false // Clear guest mode on real sign-in
                }
                self?.isLoading = false
            }
        }
    }

    deinit {
        if let handle = authStateHandle {
            Auth.auth().removeStateDidChangeListener(handle)
        }
    }

    func enterGuestMode() {
        isGuestMode = true
    }

    func requireSignIn() -> Bool {
        if isSignedIn { return true }
        showSignInPrompt = true
        return false
    }

    func signOut() {
        do {
            try Auth.auth().signOut()
            isGuestMode = false
        } catch {
            print("Error signing out: \(error.localizedDescription)")
        }
    }
}

// MARK: - Loading View
struct MainLoadingView: View {
    var body: some View {
        ZStack {
            LinearGradient(
                gradient: Gradient(colors: [Color.purple.opacity(0.9), Color.purple]),
                startPoint: .topLeading,
                endPoint: .bottomTrailing
            )
            .ignoresSafeArea()
            
            VStack(spacing: 20) {
                // App logo or branding
                HStack(spacing: 16) {
                    Image(systemName: "questionmark.circle.fill")
                        .resizable()
                        .frame(width: 60, height: 60)
                        .foregroundColor(.pink)
                    
                    Image(systemName: "questionmark.circle.fill")
                        .resizable()
                        .frame(width: 80, height: 80)
                        .foregroundColor(.orange)
                    
                    Image(systemName: "questionmark.circle.fill")
                        .resizable()
                        .frame(width: 60, height: 60)
                        .foregroundColor(.purple.opacity(0.7))
                }
                
                Text("PUZZLE VERSE")
                    .font(.title)
                    .fontWeight(.black)
                    .foregroundColor(.white)
                
                ProgressView()
                    .scaleEffect(1.5)
                    .tint(.white)
                
                Text("Loading...")
                    .foregroundColor(.white.opacity(0.8))
                    .font(.subheadline)
            }
        }
    }
}
