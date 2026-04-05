//
//  HomeView.swift
//  PuzzleForge
//
//  Created by Sushanth Tiruvaipati on 5/23/25.
//

import SwiftUI
import FirebaseAuth
import FirebaseAnalytics
import Combine

extension UserDefaults {
    func getRecentPuzzleTypes() -> [String] {
        return array(forKey: "recent_puzzle_types") as? [String] ?? []
    }
    
    func addRecentPuzzleType(_ puzzleType: String) {
        var recentTypes = getRecentPuzzleTypes()
        
        // Remove if already exists to move to front
        if let index = recentTypes.firstIndex(of: puzzleType) {
            recentTypes.remove(at: index)
        }
        
        // Add to front
        recentTypes.insert(puzzleType, at: 0)
        
        // Keep only last 10
        if recentTypes.count > 10 {
            recentTypes = Array(recentTypes.prefix(10))
        }
        
        set(recentTypes, forKey: "recent_puzzle_types")
    }
}


// Helper extension for better error messages
extension DecodingError.Context {
    var codingPathString: String {
        return codingPath.map { $0.stringValue }.joined(separator: " → ")
    }
}

// MARK: - Greeting Based on Time
struct GreetingBasedOnTime: View {
    var greeting: String {
        let hour = Calendar.current.component(.hour, from: Date())
        switch hour {
        case 5..<12: return "Good Morning ☀️"
        case 12..<17: return "Good Afternoon 🌤️"
        case 17..<21: return "Good Evening 🌇"
        default: return "Good Night 🌙"
        }
    }
    
    var body: some View {
        Text(greeting)
            .font(.caption)
            .foregroundColor(.gray)
    }
}

// MARK: - User Score Display
struct UserScoreView: View {
    @StateObject var scoreService = UserScoreService()
    
    var body: some View {
        HStack(spacing: 4) {
            Text("🏅")
            if scoreService.isLoading {
                ProgressView()
                    .scaleEffect(0.8)
            } else {
                Text("Score: \(scoreService.currentScore)")
                    .font(.system(size: 14, weight: .semibold))
                    .foregroundColor(Color(red: 0.54, green: 0.30, blue: 1.0)) // Purple color like Android
            }
        }
        .onAppear {
            scoreService.fetchUserScore()
            AnalyticsManager.shared.setUserProperties()
            CampaignTracker.shared.trackKeyConversions()
        }
    }
}

// MARK: - Coin Earned Toast Notification
struct CoinEarnedToast: View {
    let amount: Int
    @Binding var isShowing: Bool
    
    var body: some View {
        if isShowing {
            HStack(spacing: 8) {
                Image(systemName: "dollarsign.circle.fill")
                    .foregroundColor(Color(red: 1.0, green: 0.84, blue: 0.0))
                Text("+\(amount) coins")
                    .font(.headline)
                    .fontWeight(.bold)
                    .foregroundColor(.primary)
            }
            .padding(.horizontal, 16)
            .padding(.vertical, 12)
            .background(
                RoundedRectangle(cornerRadius: 25)
                    .fill(Color(.systemBackground))
                    .shadow(color: .black.opacity(0.2), radius: 8, x: 0, y: 4)
            )
            .scaleEffect(isShowing ? 1.0 : 0.0)
            .opacity(isShowing ? 1.0 : 0.0)
            .animation(.spring(dampingFraction: 0.8), value: isShowing)
            .onAppear {
                DispatchQueue.main.asyncAfter(deadline: .now() + 2.0) {
                    withAnimation(.spring(dampingFraction: 0.8)) {
                        isShowing = false
                    }
                }
            }
        }
    }
}

struct HeaderSection: View {
    @State var coins = 0
    @StateObject var subscriptionManager = SubscriptionManager.shared
    @State var showLimitWarning = false
    @State var remainingGenerations = 10
    
    var body: some View {
        VStack(spacing: 12) {
            HStack(alignment: .center, spacing: 12) {
                // Star icon like Android
                Image(systemName: "star.fill")
                    .foregroundColor(.gray)
                    .font(.title2)
                
                VStack(alignment: .leading, spacing: 2) {
                    GreetingBasedOnTime()
                    
                    Text(Auth.auth().currentUser?.displayName ?? Auth.auth().currentUser?.email ?? "Guest")
                        .font(.system(size: 18, weight: .bold))
                        .foregroundColor(.primary)
                    
                    UserScoreView()
                }
                
                Spacer()
                
                // NEW: Coins display
                HStack(spacing: 4) {
                    Image(systemName: "dollarsign.circle.fill")
                        .foregroundColor(Color(red: 1.0, green: 0.84, blue: 0.0))
                    Text("\(coins)")
                        .font(.system(size: 16, weight: .bold))
                        .foregroundColor(Color(red: 1.0, green: 0.84, blue: 0.0))
                }
                .padding(.horizontal, 12)
                .padding(.vertical, 6)
                .background(
                    RoundedRectangle(cornerRadius: 15)
                        .fill(Color.black.opacity(0.1))
                )
                .onAppear {
                    refreshCoins()
                }
                .onReceive(NotificationCenter.default.publisher(for: NSNotification.Name("CoinsChanged"))) { _ in
                    refreshCoins()
                }
            }
            .padding(.horizontal, 20)
            .padding(.top, 10)
            
            if showLimitWarning {
                LimitWarningBanner(
                    remainingGenerations: remainingGenerations,
                    resetTime: getNextResetTime(),
                    onUpgrade: {
                        // Handle upgrade flow trigger
                        NotificationCenter.default.post(name: NSNotification.Name("TriggerUpgradeFlow"), object: nil)
                    }
                )
            }
        }.onAppear {
            refreshCoins()
            checkLimitWarnings()
        }.onReceive(NotificationCenter.default.publisher(for: NSNotification.Name("CoinsChanged"))) { _ in
            refreshCoins()
        }
        .onReceive(NotificationCenter.default.publisher(for: NSNotification.Name("TriggerUpgradeFlow"))) { notification in
            // Handle upgrade trigger from warning banner
            if let tierString = notification.userInfo?["tier"] as? String,
               let tier = SubscriptionTier(rawValue: tierString) {
                // Trigger upgrade flow
            }
        }
    }
    
    private func shouldShowLimitWarning() -> Bool {
        guard subscriptionManager.currentTier == .free else { return false }
        
        // Show warning when user has 3 or fewer generations left
        return remainingGenerations > 0 && remainingGenerations <= 3
    }
    
    private func checkLimitWarnings() {
        // Check remaining generations for the most common puzzle type
        let recentTypes = UserDefaults.standard.getRecentPuzzleTypes()
        let checkType = recentTypes.first ?? "math"
        
        let remaining = subscriptionManager.getRemainingGenerations(puzzleType: checkType)
        remainingGenerations = min(remaining.daily, remaining.monthly == -1 ? remaining.daily : remaining.monthly)
        
        showLimitWarning = shouldShowLimitWarning()
    }
    
    private func getNextResetTime() -> String {
        // Calculate next midnight for daily reset
        let calendar = Calendar.current
        let tomorrow = calendar.date(byAdding: .day, value: 1, to: Date())!
        let nextMidnight = calendar.startOfDay(for: tomorrow)
        
        let formatter = DateFormatter()
        formatter.timeStyle = .short
        return formatter.string(from: nextMidnight)
    }
    
    private func refreshCoins() {
        coins = DailyStreakManager.shared.getCurrentCoins()
    }
}

struct FailedPuzzleDialog: View {
    let puzzleInfo: FailedPuzzleSetInfo
    let message: String
    let puzzleId: String
    let status: String // Add this parameter
    let onDismiss: () -> Void // Remove onRequestRegeneration
    
    var dialogTitle: String {
        switch status {
        case "failed":
            return "Topic Rejected"
        case "regenerating":
            return "Puzzle Regenerating"
        default:
            return "Content Review Required"
        }
    }
    
    var dialogIcon: String {
        switch status {
        case "failed":
            return "hand.raised.fill"
        case "regenerating":
            return "arrow.clockwise"
        default:
            return "exclamationmark.triangle.fill"
        }
    }
    
    var dialogColor: Color {
        switch status {
        case "failed":
            return .red
        case "regenerating":
            return .blue
        default:
            return .orange
        }
    }
    
    var body: some View {
        GeometryReader { geometry in
            ScrollView {
                VStack(spacing: 20) {
                    // Header
                    VStack(spacing: 8) {
                        Image(systemName: dialogIcon)
                            .font(.system(size: 35))
                            .foregroundColor(dialogColor)
                        
                        Text(dialogTitle)
                            .font(.title3)
                            .fontWeight(.bold)
                            .multilineTextAlignment(.center)
                    }
                    
                    // Puzzle Info
                    VStack(spacing: 10) {
                        HStack {
                            VStack(alignment: .leading, spacing: 4) {
                                Text("Puzzle Name:")
                                    .font(.caption)
                                    .foregroundColor(.secondary)
                                Text(puzzleInfo.name)
                                    .font(.subheadline)
                                    .fontWeight(.semibold)
                            }
                            Spacer()
                        }
                        
                        HStack {
                            VStack(alignment: .leading, spacing: 4) {
                                Text("Creator:")
                                    .font(.caption)
                                    .foregroundColor(.secondary)
                                Text(puzzleInfo.creator)
                                    .font(.caption)
                            }
                            
                            Spacer()
                            
                            VStack(alignment: .trailing, spacing: 4) {
                                Text("Format:")
                                    .font(.caption)
                                    .foregroundColor(.secondary)
                                Text(puzzleInfo.format)
                                    .font(.caption)
                            }
                        }
                    }
                    .padding(12)
                    .background(Color.gray.opacity(0.1))
                    .cornerRadius(10)
                    
                    // Message
                    VStack(alignment: .leading, spacing: 6) {
                        Text("What happened?")
                            .font(.subheadline)
                            .fontWeight(.semibold)
                        
                        Text("This topic has been flagged by our content review system as potentially inappropriate. This is an automated process and may occasionally flag content incorrectly.")
                            .font(.caption)
                            .foregroundColor(.secondary)
                            .fixedSize(horizontal: false, vertical: true)
                    }
                    
                    // Single OK Button
                    Button(action: onDismiss) {
                        Text("OK")
                            .font(.subheadline)
                            .fontWeight(.semibold)
                            .foregroundColor(.white)
                            .frame(maxWidth: .infinity)
                            .padding(.vertical, 12)
                            .background(dialogColor)
                            .cornerRadius(10)
                    }
                }
                .padding(20)
            }
            .frame(maxHeight: geometry.size.height * 0.8)
        }
        .background(
            RoundedRectangle(cornerRadius: 16)
                .fill(Color(.systemBackground))
                .shadow(color: .black.opacity(0.2), radius: 15, x: 0, y: 8)
        )
        .padding(.horizontal, 24)
        .padding(.vertical, 40)
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(
            Color.black.opacity(0.4)
                .ignoresSafeArea()
        )
    }
}

// MARK: - More Menu Component
struct MoreMenu: View {
    @Binding var isShowingLeaderboard: Bool
    @Binding var isShowingTopics: Bool
    @Binding var isShowingAchievements: Bool
    @Binding var isShowingCustomePuzzleList: Bool
    @Binding var isSignedOut: Bool
    @Binding var isShowingSignIn: Bool
    @Binding var isShowingSignUp: Bool
    
    @State var showingMenu = false
    
    var body: some View {
        Menu {
            if Auth.auth().currentUser != nil {
                Button {
                    isShowingLeaderboard = true
                } label: {
                    Label("Leaderboard", systemImage: "trophy.fill")
                }
                
                Button {
                    isShowingTopics = true
                } label: {
                    Label("Preferred Topics", systemImage: "tag.fill")
                }
                
                Button {
                    isShowingAchievements = true
                } label: {
                    Label("Progress Dashboard", systemImage: "chart.bar.fill")
                }
                
                Button {
                    isShowingCustomePuzzleList = true
                } label: {
                    Label("Search Custom Puzzles", systemImage: "magnifyingglass")
                }
                
                Divider()
                
                Button(role: .destructive) {
                    do {
                        AnalyticsManager.shared.track(.userLogout())
                        try Auth.auth().signOut()
                        isSignedOut = true
                    } catch {
                        print("❌ Sign out failed: \(error.localizedDescription)")
                    }
                } label: {
                    Label("Sign Out", systemImage: "arrow.backward.circle")
                }
            } else {
                Button {
                    isShowingSignIn = true
                } label: {
                    Label("Sign In", systemImage: "person.crop.circle.fill")
                }
            }
        } label: {
            Image(systemName: "ellipsis")
                .font(.title2)
                .foregroundColor(.gray)
        }
    }
}

// MARK: - Updated Quiz Card View
struct StyledQuizCardView: View {
    let title: String
    let subtitle: String
    let icon: String
    let backgroundColor: Color
    let onTap: () -> Void
    
    var body: some View {
        Button(action: onTap) {
            VStack(spacing: 12) {
                // Icon with background circle
                ZStack {
                    Circle()
                        .fill(Color.white.opacity(0.2))
                        .frame(width: 50, height: 50)
                    
                    Image(systemName: icon)
                        .font(.system(size: 24, weight: .medium))
                        .foregroundColor(.white)
                }
                
                VStack(spacing: 4) {
                    Text(title)
                        .font(.system(size: 14, weight: .semibold))
                        .foregroundColor(.white)
                        .multilineTextAlignment(.center)
                        .lineLimit(2)
                    
                    Text(subtitle)
                        .font(.system(size: 10))
                        .foregroundColor(.white.opacity(0.8))
                        .multilineTextAlignment(.center)
                        .lineLimit(2)
                }
            }
            .frame(maxWidth: .infinity)
            .frame(height: 120)
            .padding(12)
            .background(
                RoundedRectangle(cornerRadius: 12)
                    .fill(backgroundColor)
                    .shadow(color: .black.opacity(0.15), radius: 4, x: 0, y: 2)
            )
        }
        .buttonStyle(PlainButtonStyle())
    }
}

private func isLocalPuzzleType(_ category: String) -> Bool {
    let localTypes: Set<String> = [
        "memoryprevioussingle", "memory_previous_single",
        "memorypreviouspair", "memory_previous_pair",
        "colorshapematching", "color_shape_matching",
        "imagevortex", "image_vortex",
        "mathcomparison", "math_comparison",
        "numbersequence", "number_sequence",
        "numbersum", "number_sum",
        "symbolswipe", "symbol_swipe",
        "uniqueobject", "unique_object",
        "contextswitch", "dualtask", "dual_task",
        "mathcrossword", "math_crossword",
        "colortextmatching", "color_text_matching",
        "geographycities", "geography_cities",
        "geographycountries", "geography_countries",
        "conversion", "discounts", "discount",
        "mathtipping", "math_tipping",
        "mathestimation", "math_estimation",
        "purchasing", "division", "average",
        "percentages", "percentage", "pinballdeflector", "triangledotmemory"
    ]
    return localTypes.contains(category)
}

extension SubscriptionTier: Identifiable {
    var id: String { self.rawValue }
}

struct HomeView: View {
    @EnvironmentObject var authStateManager: AuthStateManager
    @State var selectedPuzzle: Puzzle? = nil
    @State var selectedPuzzleSet: OuterPuzzleData? = nil
    @State var navigationState: PuzzleNavigationType = .none
    @StateObject var viewModel = HomeViewModel()
    @State var showCreatePuzzle = false
    @State var isShowingPuzzleView = false
    @State var isLoadingCustomPuzzle = false
    @State var errorMessage: String?
    @State var isSignedOut = false
    @State var currentQuestionIndex = 0
    @State var showCompletionAnimation = false
    @State var isShowingLeaderboard = false
    @State var topThree: [User] = []
    @State var others: [User] = []
    @State var isShowingSignIn = false
    @State var isShowingSignUp = false
    @State var isShowingTopics = false
    @State var isShowingAchievements = false
    @State var isShowingCustomePuzzleList = false
    @State var selectedCategory: String? = nil
    @State var selectedDifficulty: String = "Easy"
    @State var showStartPuzzleView = false
    @State var isPuzzleLoading = false
    @State var navigationPath = NavigationPath()
    @State var currentSheet: SheetType? = nil
    @StateObject var difficultyManager = DifficultySettingsManager()
    @State var showTutorial = false
    @State var showNotificationSettings: Bool = false
    @State var dailyTopics: [String] = []
    @State var isDailyTopicsLoading = false
    @State var dailyTopicsError: String?
    @State var isShowingDailyPuzzles = false
    @State var currentSubtractionProblemIndex = 0
    @State var showCompletionScreen = false
    @State var completionEarnedPoints = 0
    @State var selectedFilter: CategoryFilter = .all
    @State var showFilterSheet = false
    @State var showFailedPuzzleDialog = false
    @State var failedPuzzleInfo: FailedPuzzleSetInfo?
    @State var failedPuzzleMessage: String = ""
    @State var failedPuzzleId: String = ""
    @State var failedPuzzleStatus: String = "failed"
    @State var showDailyPuzzlesDialog = false
    @State var sessionStartTime: Date?
    @State var showStreakDialog = false
    @State var dailyStreakReward: StreakReward?
    @State var showCoinToast = false
    @State var lastCoinAmount = 0
    @StateObject var userStatsManager = UserStatsManager.shared
    @State var targetPuzzleCount = 5
    @State var completedPuzzleCount = 0
    @State var sessionStats: [SessionStatistics] = []
    @State var isInActiveSession = false
    @State var keepCurrentViewWhileFetchingNext = false
    @State public var showRatingDialog = false
    @State var hasShownRatingThisSession = false
    @State var showLimitDialog = false
    @State var currentLimitInfo: LimitInfo?
    @State var showUpgradeFlow = false
    @State var selectedUpgradeTier: SubscriptionTier?
    @State var upgradeFlowTier: SubscriptionTier?
    @StateObject var subscriptionManager = SubscriptionManager.shared
    @State var selectedPuzzleGroup: PuzzleGroup?
    @State var showPuzzleGroupDetail = false
    @State var currentGroupId: String?
    @State private var showUpdateDialog = false
    @State private var updateResponse: AppVersionResponse?
    @State var showFreeLimitPaywall = false
    @State private var showAppOpenPaywall = false

    // Add this property for destinations to access
    @State var currentScore = 0
    
    
    
    
    var isInChina: Bool {
        if let regionCode = Locale.current.regionCode {
            return regionCode == "CN"
        }
        return false
    }
    
    
    // MARK: - App-Open Paywall
    private func checkAppOpenPaywall() {
        guard !subscriptionManager.hasActiveSubscription() else { return }
        let key = "com.riddleverse.appOpenCount"
        let count = UserDefaults.standard.integer(forKey: key) + 1
        UserDefaults.standard.set(count, forKey: key)

        // Show paywall on opens 1, 3, 5, then every 3rd open
        let shouldShow: Bool
        switch count {
        case 1, 3, 5:
            shouldShow = true
        default:
            shouldShow = count > 5 && (count - 5) % 3 == 0
        }

        if shouldShow {
            DispatchQueue.main.asyncAfter(deadline: .now() + 1.0) {
                showAppOpenPaywall = true

                AnalyticsManager.shared.trackSafely(AnalyticsEvent("app_open_paywall_shown", parameters: [
                    "open_count": count
                ]))
            }
        }
    }

    private func checkAppVersion() {
        // Don't check if already shown for this version (unless forced)
        if SimpleVersionManager.shared.hasShownUpdateForCurrentVersion() {
            return
        }
        
        SimpleVersionManager.shared.checkForUpdate { response in
            guard let response = response else {
                print("❌ Failed to check app version")
                return
            }
            
            print("📱 Version check complete:")
            print("   Current: \(response.currentUserVersion)")
            print("   Latest: \(response.latestVersion)")
            print("   Needs Update: \(response.needsUpdate)")
            
            if response.needsUpdate {
                // Show dialog after a short delay to not overwhelm user
                DispatchQueue.main.asyncAfter(deadline: .now() + 1.5) {
                    self.updateResponse = response
                    self.showUpdateDialog = true
                    
                    // Track analytics
                    AnalyticsManager.shared.trackSafely(AnalyticsEvent("update_available", parameters: [
                        "current_version": response.currentUserVersion,
                        "latest_version": response.latestVersion,
                        "is_forced": response.isForced
                    ]))
                }
            }
        }
    }

    private func handleUpdateAction() {
        guard let response = updateResponse else { return }
        
        // Open App Store
        if let url = URL(string: response.appStoreUrl) {
            UIApplication.shared.open(url)
            
            // Track analytics
            AnalyticsManager.shared.trackSafely(AnalyticsEvent("update_initiated", parameters: [
                "current_version": response.currentUserVersion,
                "latest_version": response.latestVersion
            ]))
        }
        
        // Mark as shown (unless forced, then we'll show again next time)
        if !response.isForced {
            SimpleVersionManager.shared.markUpdateShown()
            showUpdateDialog = false
        }
    }

    private func dismissUpdateDialog() {
        guard let response = updateResponse else { return }
        
        SimpleVersionManager.shared.markUpdateShown()
        showUpdateDialog = false
        
        // Track analytics
        AnalyticsManager.shared.trackSafely(AnalyticsEvent("update_dismissed", parameters: [
            "current_version": response.currentUserVersion,
            "latest_version": response.latestVersion
        ]))
    }



    enum SheetType: Identifiable {
        case startPuzzle(String)
        case createPuzzle
        
        var id: String {
            switch self {
            case .startPuzzle(let category): return "startPuzzle-\(category)"
            case .createPuzzle: return "createPuzzle"
            }
        }
    }
    
    @State var selectedTab: TabSelection = .forYou
    @StateObject private var gameGenerationManager = GameGenerationManager.shared
    @State private var showEarnBanner: Bool = !UserDefaults.standard.bool(forKey: "earn_banner_dismissed")
    @State private var gameTabSection: Int = 1
    

    @State var currentTimer = "0:00"
    
    
    
    var isShowingPuzzle: Binding<Bool> {
        Binding(
            get: {
                let shouldShow =
                    (selectedPuzzle != nil || keepCurrentViewWhileFetchingNext) &&
                    navigationState != .none &&
                    !showCompletionScreen
                
                print("📱 isShowingPuzzle getter: \(shouldShow)")
                print("📱 Debug: selectedPuzzle != nil: \(selectedPuzzle != nil)")
                print("📱 Debug: keepCurrentViewWhileFetchingNext: \(keepCurrentViewWhileFetchingNext)")
                print("📱 Debug: navigationState: \(navigationState)")
                print("📱 Debug: showCompletionScreen: \(showCompletionScreen)")
                print("📱 Debug: isPuzzleLoading: \(isPuzzleLoading)")
                
                return shouldShow
            },
            set: { newValue in
                print("📱 isShowingPuzzle setter called with: \(newValue)")
                if !newValue && !isInActiveSession {
                    print("🧹 Clearing puzzle state from isShowingPuzzle setter")
                    selectedPuzzleSet = nil
                    currentQuestionIndex = 0
                    currentSubtractionProblemIndex = 0
                    isPuzzleLoading = false
                    keepCurrentViewWhileFetchingNext = false
                    
                    // IMPORTANT: Only clear selectedPuzzle and navigationState if not in loading state
                    if !isPuzzleLoading {
                        selectedPuzzle = nil
                        navigationState = .none
                    }
                }
            }
        )
    }

    enum TabSelection: CaseIterable {
        case forYou, categories, createGame, customPuzzles, settings

        var title: String {
            switch self {
            case .forYou: return "For You"
            case .categories: return "Browse"
            case .createGame: return "Games"
            case .customPuzzles: return "Puzzles"
            case .settings: return "Settings"
            }
        }

        var icon: String {
            switch self {
            case .forYou: return "heart.fill"
            case .categories: return "square.grid.3x3.fill"
            case .createGame: return "gamecontroller.fill"
            case .customPuzzles: return "puzzlepiece.fill"
            case .settings: return "gearshape.fill"
            }
        }
    }
    
    struct SimpleUpdateDialog: View {
        let response: AppVersionResponse
        let onUpdate: () -> Void
        let onDismiss: () -> Void
        
        var body: some View {
            ZStack {
                Color.black.opacity(0.4)
                    .ignoresSafeArea()
                    .onTapGesture {
                        if !response.isForced {
                            onDismiss()
                        }
                    }
                
                VStack(spacing: 20) {
                    // Header
                    VStack(spacing: 8) {
                        Image(systemName: response.isForced ? "exclamationmark.triangle.fill" : "arrow.up.circle.fill")
                            .font(.system(size: 50))
                            .foregroundColor(response.isForced ? .orange : .blue)
                        
                        Text(response.isForced ? "Update Required" : "Update Available")
                            .font(.title2)
                            .fontWeight(.bold)
                        
                        Text("Version \(response.latestVersion)")
                            .font(.subheadline)
                            .foregroundColor(.secondary)
                    }
                    
                    // What's New
                    VStack(alignment: .leading, spacing: 12) {
                        Text("What's New:")
                            .font(.headline)
                            .foregroundColor(.primary)
                        
                        ForEach(response.whatsNew, id: \.self) { feature in
                            HStack(alignment: .top, spacing: 8) {
                                Text("•")
                                    .foregroundColor(.blue)
                                Text(feature)
                                    .font(.subheadline)
                                    .foregroundColor(.secondary)
                                    .fixedSize(horizontal: false, vertical: true)
                            }
                        }
                    }
                    .padding()
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .background(Color(.systemGray6))
                    .cornerRadius(12)
                    
                    // Buttons
                    VStack(spacing: 12) {
                        Button(action: onUpdate) {
                            Text("Update Now")
                                .fontWeight(.semibold)
                                .foregroundColor(.white)
                                .frame(maxWidth: .infinity)
                                .padding()
                                .background(Color.blue)
                                .cornerRadius(12)
                        }
                        
                        if !response.isForced {
                            Button(action: onDismiss) {
                                Text("Later")
                                    .font(.subheadline)
                                    .foregroundColor(.secondary)
                            }
                        }
                    }
                }
                .padding(24)
                .background(Color(.systemBackground))
                .cornerRadius(20)
                .shadow(radius: 20)
                .padding(.horizontal, 40)
            }
        }
    }
    
    struct FilterChipView: View {
        let filter: CategoryFilter
        let isSelected: Bool
        let onTap: () -> Void
        
        var body: some View {
            Button(action: onTap) {
                HStack(spacing: 6) {
                    Image(systemName: filter.icon)
                        .font(.caption)
                    Text(filter.rawValue)
                        .font(.caption)
                        .fontWeight(.medium)
                }
                .padding(.horizontal, 12)
                .padding(.vertical, 8)
                .background(
                    RoundedRectangle(cornerRadius: 20)
                        .fill(isSelected ? filter.color : Color.gray.opacity(0.2))
                )
                .foregroundColor(isSelected ? .white : .primary)
                .overlay(
                    RoundedRectangle(cornerRadius: 20)
                        .stroke(isSelected ? filter.color : Color.gray.opacity(0.3), lineWidth: 1)
                )
            }
            .buttonStyle(PlainButtonStyle())
        }
    }

    struct FilterSectionView: View {
        @Binding var selectedFilter: CategoryFilter
        let filteredCount: Int  // Add this parameter
        let onFilterChange: () -> Void
        
        var body: some View {
            VStack(alignment: .leading, spacing: 12) {
                HStack {
                    Text("Filter Categories")
                        .font(.subheadline)
                        .fontWeight(.medium)
                        .foregroundColor(.secondary)
                    
                    Spacer()
                    
                    // Count of visible categories
                    if selectedFilter != .all {
                        Text("\(filteredCount) categories")
                            .font(.caption)
                            .foregroundColor(.secondary)
                    }
                }
                .padding(.horizontal)
                
                ScrollView(.horizontal, showsIndicators: false) {
                    HStack(spacing: 10) {
                        ForEach(CategoryFilter.allCases) { filter in
                            FilterChipView(
                                filter: filter,
                                isSelected: selectedFilter == filter,
                                onTap: {
                                    withAnimation(.easeInOut(duration: 0.2)) {
                                        selectedFilter = filter
                                    }
                                    onFilterChange()
                                }
                            )
                        }
                    }
                    .padding(.horizontal)
                }
            }
        }
    }
    
    private func getFilteredCategories() -> [CategoryData] {
        // Get all categories and filter by selected filter
        let allCategories = CategoryData.allCategories.filter { category in
            selectedFilter == .all || category.tags.contains(selectedFilter)
        }
        
        // Sort alphabetically if A-Z filter is selected
        if selectedFilter == .aToZ {
            return allCategories.sorted { $0.title < $1.title }
        }
        
        return allCategories
    }

    @ViewBuilder
    var categoriesTab: some View {
        VStack(alignment: .leading, spacing: 20) {
            ScrollView {
                LazyVStack(spacing: 20) {
                    FilterSectionView(
                        selectedFilter: $selectedFilter,
                        filteredCount: getFilteredCategories().count,
                        onFilterChange: {
                            let impactFeedback = UIImpactFeedbackGenerator(style: .light)
                            impactFeedback.impactOccurred()
                        }
                    )
                    
                    VStack(alignment: .leading, spacing: 16) {
                        HStack {
                            Text(selectedFilter == .all ? "All Categories" : "\(selectedFilter.rawValue) Categories")
                                .font(.title2)
                                .fontWeight(.semibold)
                            Spacer()
                        }
                        .padding(.horizontal)
                        
                        let filteredCategories = getFilteredCategories()
                        
                        if filteredCategories.isEmpty {
                            VStack(spacing: 12) {
                                Image(systemName: "square.grid.3x3")
                                    .font(.system(size: 40))
                                    .foregroundColor(.gray)
                                Text("No Categories")
                                    .font(.headline)
                                    .foregroundColor(.primary)
                                Text("Adjust your filter to see more categories.")
                                    .font(.caption)
                                    .foregroundColor(.secondary)
                                    .multilineTextAlignment(.center)
                            }
                            .frame(maxWidth: .infinity)
                            .padding(40)
                        } else {
                            LazyVGrid(columns: Array(repeating: GridItem(.adaptive(minimum: 160)), count: 1), spacing: 16) {
                                ForEach(filteredCategories, id: \.category) { categoryData in
                                    Button(action: {
                                        selectedCategory = categoryData.category
                                        selectedDifficulty = difficultyManager.selectedDifficulty
                                        currentSheet = .startPuzzle(categoryData.category)
                                    }) {
                                        CategoryCardWithStatus(
                                            categoryData: categoryData,
                                            onTap: {
                                                selectedCategory = categoryData.category
                                                selectedDifficulty = difficultyManager.selectedDifficulty
                                                currentSheet = .startPuzzle(categoryData.category)
                                            }
                                        )
                                    }
                                    .buttonStyle(PlainButtonStyle())
                                    .transition(.scale.combined(with: .opacity))
                                }
                            }
                            .padding(.horizontal)
                            .animation(.easeInOut(duration: 0.3), value: selectedFilter)
                        }
                    }
                }
            }
        }
    }

    // NEW: Show ALL categories but with status indicators
    private func getAllFilteredCategories() -> [CategoryData] {
        let allCategories = CategoryData.allCategories.filter { category in
            selectedFilter == .all || category.tags.contains(selectedFilter)
        }
        
        // Sort alphabetically if A-Z filter is selected
        if selectedFilter == .aToZ {
            return allCategories.sorted { $0.title < $1.title }
        }
        
        return allCategories
    }

    var loadingView: some View {
        VStack {
            ProgressView()
                .scaleEffect(1.5)
            Text("Loading puzzle...")
                .padding(.top)
                .foregroundColor(.gray)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(Color(.systemBackground))
    }
    
    // Add this component to your HomeView.swift or create a separate file

    struct CategoryCardWithStatus: View {
        let categoryData: CategoryData
        let onTap: () -> Void
        
        @State var isLoading = false
        
        var body: some View {
            Button(action: {
                isLoading = true
                onTap()
            }) {
                VStack(spacing: 12) {
                    // Icon with loading indicator
                    ZStack {
                        Circle()
                            .fill(categoryData.backgroundColor.opacity(0.2))
                            .frame(width: 50, height: 50)
                        
                        if isLoading {
                            ProgressView()
                                .scaleEffect(0.8)
                                .foregroundColor(categoryData.backgroundColor)
                        } else {
                            Image(systemName: categoryData.icon)
                                .font(.system(size: 24, weight: .medium))
                                .foregroundColor(categoryData.backgroundColor)
                        }
                    }
                    
                    VStack(spacing: 4) {
                        Text(categoryData.title)
                            .font(.system(size: 14, weight: .semibold))
                            .foregroundColor(.white)
                            .multilineTextAlignment(.center)
                            .lineLimit(2)
                        
                        Text(isLoading ? "Loading..." : categoryData.subtitle)
                            .font(.system(size: 10))
                            .foregroundColor(.white.opacity(0.8))
                            .multilineTextAlignment(.center)
                            .lineLimit(2)
                    }
                }
                .frame(maxWidth: .infinity)
                .frame(height: 120)
                .padding(12)
                .background(
                    RoundedRectangle(cornerRadius: 12)
                        .fill(categoryData.backgroundColor)
                        .shadow(color: .black.opacity(0.15), radius: 4, x: 0, y: 2)
                )
            }
            .buttonStyle(PlainButtonStyle())
            .disabled(isLoading)
        }
    }

    // MARK: - Supporting Types
    enum PuzzleStatus {
        case available
        case needsFetch
    }
        
    @ViewBuilder
    var customPuzzlesTab: some View {
        VStack(alignment: .leading, spacing: 20) {
            HStack {
                Text("Custom Puzzles")
                    .font(.largeTitle)
                    .fontWeight(.bold)
                
                Spacer()
                
                // Only show the create button if NOT in China
                if !isInChina {
                    Button(action: { print("🔵 Create puzzle button tapped")
                        currentSheet = .createPuzzle }) {
                        Image(systemName: "plus.circle.fill")
                            .font(.title2)
                            .foregroundColor(.orange)
                            .frame(width: 44, height: 44)
                            .contentShape(Rectangle())
                    }
                }
            }
            .padding(.horizontal)
            
            ScrollView {
                LazyVStack(spacing: 16) {
                    // REUSE: Your existing custom puzzle logic, just in list format
                    ForEach(viewModel.customPuzzles.indices, id: \.self) { index in
                        let puzzle = viewModel.customPuzzles[index]
                        
                        CustomPuzzleRowView(puzzle: puzzle) {
                            fetchCustomPuzzleSet(puzzleId: puzzle.id)
                        }
                        
                        if index == viewModel.customPuzzles.count - 1 {
                            ProgressView()
                                .onAppear { viewModel.loadMoreCustomPuzzles() }
                        }
                    }
                }
                .padding(.horizontal)
            }
        }
        .sheet(isPresented: $showFailedPuzzleDialog) {
            if let puzzleInfo = failedPuzzleInfo {
                FailedPuzzleDialog(
                    puzzleInfo: puzzleInfo,
                    message: failedPuzzleMessage,
                    puzzleId: failedPuzzleId,
                    status: failedPuzzleStatus, // Pass the status
                    onDismiss: {
                        showFailedPuzzleDialog = false
                        // Clear the failed puzzle info
                        failedPuzzleInfo = nil
                        failedPuzzleMessage = ""
                        failedPuzzleId = ""
                        failedPuzzleStatus = "failed"
                    }
                )
            }
        }
        .onAppear {
            print("Current region: \(Locale.current.regionCode ?? "Unknown")")
            print("Is in China: \(isInChina)")
            if viewModel.customPuzzles.isEmpty {
                viewModel.fetchCustomPuzzles()
            }
        }
    }
    

    @ViewBuilder
    var forYouTab: some View {
        VStack(alignment: .leading, spacing: 20) {
            ScrollView {
                LazyVStack(spacing: 24) {
                    // Earn money banner
                    if showEarnBanner {
                        EarnMoneyBanner(
                            onTap: { gameTabSection = 0; selectedTab = .createGame },
                            onDismiss: {
                                withAnimation(.easeOut(duration: 0.3)) { showEarnBanner = false }
                                UserDefaults.standard.set(true, forKey: "earn_banner_dismissed")
                            }
                        )
                        .padding(.horizontal)
                        .transition(.move(edge: .top).combined(with: .opacity))
                    }

                    // 1. Quick Actions Section (keep as is)
                    VStack(alignment: .leading, spacing: 12) {
                        Text("Quick Actions")
                            .font(.title2)
                            .fontWeight(.semibold)
                            .padding(.horizontal)
                        
                        ScrollView(.horizontal, showsIndicators: false) {
                            HStack(spacing: 12) {
                                RandomChallengeCard(onClick: {
                                    let allCategories = CategoryData.allCategories.map { $0.category }
                                    if let randomCategory = allCategories.randomElement() {
                                        selectedCategory = randomCategory
                                        selectedDifficulty = difficultyManager.selectedDifficulty
                                        currentSheet = .startPuzzle(randomCategory)
                                    }
                                })
                                
                                ForEach(UserDefaults.standard.getRecentPuzzleTypes().prefix(6), id: \.self) { puzzleType in
                                    if let categoryData = CategoryData.allCategories.first(where: { $0.category == puzzleType }) {
                                        QuickActionPuzzleCard(
                                            puzzleType: puzzleType,
                                            isNew: false,
                                            onTap: {
                                                selectedCategory = puzzleType
                                                selectedDifficulty = difficultyManager.selectedDifficulty
                                                currentSheet = .startPuzzle(puzzleType)
                                            }
                                        )
                                    }
                                }
                            }
                            .padding(.horizontal)
                        }
                    }
                    
                    // 2. Puzzle Groups Section (NEW - replaces Recent Games)
                    PuzzleGroupsSection(
                        groups: GroupCompletionManager.shared.getAllGroups(
                            userId: Auth.auth().currentUser?.email ?? ""
                        ),
                        onGroupSelected: { group in
                            selectedPuzzleGroup = group
                            showPuzzleGroupDetail = true
                        }
                    )
                    
                    // 3. Popular Categories (keep as is)
                    VStack(alignment: .leading, spacing: 12) {
                        Text("Popular Categories")
                            .font(.title2)
                            .fontWeight(.semibold)
                            .padding(.horizontal)
                        
                        ScrollView(.horizontal, showsIndicators: false) {
                            HStack(spacing: 12) {
                                let popularCategories = ["flowpuzzle", "oddoneout", "realorai", "math", "anagram", "trivia", "average", "crossword", "wordprefix"]
                                ForEach(popularCategories, id: \.self) { puzzleType in
                                    if let categoryData = CategoryData.allCategories.first(where: { $0.category == puzzleType }) {
                                        CompactPuzzleCard(
                                            title: categoryData.title,
                                            subtitle: categoryData.subtitle,
                                            backgroundColor: categoryData.backgroundColor,
                                            icon: categoryData.icon,
                                            onClick: {
                                                selectedCategory = puzzleType
                                                selectedDifficulty = difficultyManager.selectedDifficulty
                                                currentSheet = .startPuzzle(puzzleType)
                                            }
                                        )
                                    }
                                }
                            }
                            .padding(.horizontal)
                        }
                    }
                }
                .padding(.top, 10)
            }
        }
        .onAppear {
            refreshUserEngagementData()
        }
    }
    
    struct CompactPuzzleCard: View {
        let title: String
        let subtitle: String
        let backgroundColor: Color
        let icon: String
        let onClick: () -> Void
        
        var body: some View {
            Button(action: onClick) {
                HStack(spacing: 12) {
                    Image(systemName: icon)
                        .font(.system(size: 20, weight: .medium))
                        .foregroundColor(.white)
                        .frame(width: 40, height: 40)
                        .background(Circle().fill(Color.white.opacity(0.2)))
                    
                    VStack(alignment: .leading, spacing: 2) {
                        Text(title)
                            .font(.system(size: 14, weight: .semibold))
                            .foregroundColor(.white)
                            .lineLimit(1)
                        
                        Text(subtitle)
                            .font(.system(size: 11))
                            .foregroundColor(.white.opacity(0.8))
                            .lineLimit(2)
                    }
                    
                    Spacer()
                }
                .padding(12)
                .frame(width: 200, height: 70)
                .background(
                    RoundedRectangle(cornerRadius: 12)
                        .fill(backgroundColor)
                        .shadow(color: .black.opacity(0.15), radius: 3, x: 0, y: 2)
                )
            }
            .buttonStyle(PlainButtonStyle())
        }
    }

    private func getAvailableRecentPuzzleTypes() -> [String] {
        return UserDefaults.standard.getRecentPuzzleTypes()
    }


    struct EarnMoneyBanner: View {
        let onTap: () -> Void
        let onDismiss: () -> Void

        var body: some View {
            HStack(spacing: 0) {
                Button(action: onTap) {
                    HStack(spacing: 12) {
                        Text("💰")
                            .font(.system(size: 32))

                        VStack(alignment: .leading, spacing: 3) {
                            Text("Earn real cash on RiddleVerse")
                                .font(.subheadline)
                                .fontWeight(.bold)
                                .foregroundColor(.white)
                            Text("Create a game → players find it → you get paid.")
                                .font(.caption)
                                .foregroundColor(.white.opacity(0.85))
                        }

                        Spacer()

                        Image(systemName: "arrow.right.circle.fill")
                            .font(.title2)
                            .foregroundColor(.white.opacity(0.9))
                    }
                    .padding(.leading, 16)
                    .padding(.trailing, 8)
                    .padding(.vertical, 14)
                }
                .buttonStyle(.plain)

                Button(action: onDismiss) {
                    Image(systemName: "xmark")
                        .font(.caption2.bold())
                        .foregroundColor(.white.opacity(0.7))
                        .padding(.horizontal, 12)
                        .frame(maxHeight: .infinity)
                }
            }
            .background(
                LinearGradient(
                    colors: [Color(red: 0.13, green: 0.75, blue: 0.39), Color(red: 0.06, green: 0.55, blue: 0.28)],
                    startPoint: .leading,
                    endPoint: .trailing
                )
            )
            .cornerRadius(14)
        }
    }

    struct ForYouLoadingSection: View {
        var body: some View {
            VStack(spacing: 8) {
                HStack {
                    ProgressView()
                        .scaleEffect(0.8)
                    
                    Text("Loading personalized puzzles...")
                        .font(.caption)
                        .foregroundColor(.secondary)
                    
                    Spacer()
                }
            }
            .padding()
            .background(Color.gray.opacity(0.1))
            .cornerRadius(8)
        }
    }

    struct ForYouNoPuzzlesAvailableSection: View {
        var body: some View {
            VStack(spacing: 16) {
                Image(systemName: "wifi.exclamationmark")
                    .font(.system(size: 40))
                    .foregroundColor(.orange)
                
                Text("Puzzles Loading")
                    .font(.title2)
                    .fontWeight(.semibold)
                
                Text("We're fetching your personalized puzzles. This usually takes just a few moments.")
                    .font(.subheadline)
                    .foregroundColor(.secondary)
                    .multilineTextAlignment(.center)
                
                ProgressView()
                    .scaleEffect(1.2)
            }
            .frame(maxWidth: .infinity)
            .padding(32)
            .background(Color.orange.opacity(0.1))
            .cornerRadius(16)
        }
    }

    // MARK: - Enhanced For You Components

    struct ForYouEnhancedQuickActionsSection: View {
        let recentPuzzleTypes: [String]
        let onPuzzleTap: (String) -> Void
        let onRandomTap: () -> Void
        
        @State var showNewHint = true
        
        var body: some View {
            VStack(alignment: .leading, spacing: 12) {
                // Section Header with motivation
                HStack {
                    VStack(alignment: .leading, spacing: 4) {
                        Text("Quick Actions")
                            .font(.title2)
                            .fontWeight(.semibold)
                            .foregroundColor(Color(red: 0.18, green: 0.18, blue: 0.18))
                        
                        if showNewHint {
                            Text("Tap any to start earning points!")
                                .font(.caption)
                                .foregroundColor(Color(red: 0.42, green: 0.45, blue: 1.0))
                                .fontWeight(.medium)
                        }
                    }
                    
                    Spacer()
                    
                    // Progress indicator
                    HStack(spacing: 4) {
                        Text("\(recentPuzzleTypes.count)/50 tried")
                            .font(.caption2)
                            .fontWeight(.bold)
                            .foregroundColor(.white)
                            .padding(.horizontal, 8)
                            .padding(.vertical, 4)
                            .background(Color(red: 0.42, green: 0.45, blue: 1.0))
                            .cornerRadius(12)
                    }
                }
                
                ScrollView(.horizontal, showsIndicators: false) {
                    HStack(spacing: 12) {
                        // Random Challenge Button
                        RandomChallengeCard(onClick: onRandomTap)
                        
                        // Recent/Recommended puzzle types
                        ForEach(getRecommendedPuzzleTypes(), id: \.self) { puzzleType in
                            QuickActionPuzzleCard(
                                puzzleType: puzzleType,
                                isNew: isNewPuzzleType(puzzleType),
                                onTap: { onPuzzleTap(puzzleType) }
                            )
                        }
                    }
                    .padding(.horizontal, 4)
                }
            }
        }
        
        private func getRecommendedPuzzleTypes() -> [String] {
            if recentPuzzleTypes.count >= 3 {
                return Array(recentPuzzleTypes.prefix(6))
            } else {
                // Smart fallback with rotation
                let allTypes = CategoryData.allCategories.map { $0.category }
                let rotatingTypes = getRotatingCategorySelection(from: allTypes)
                return (recentPuzzleTypes + rotatingTypes).prefix(6).map { $0 }
            }
        }
        
        private func getRotatingCategorySelection(from types: [String]) -> [String] {
            let currentHour = Calendar.current.component(.hour, from: Date())
            let shuffled = types.shuffled()
            let startIndex = currentHour % types.count
            return Array(shuffled.dropFirst(startIndex).prefix(6))
        }
        
        private func isNewPuzzleType(_ puzzleType: String) -> Bool {
            let newTypes = ["pinballdeflector", "memoryprevioussingle", "memorypreviouspair", "wordsearch", "dualtask", "colortextmatching"]
            return newTypes.contains(puzzleType)
        }
    }

    // MARK: - Supporting Components that may be missing

    struct RandomChallengeCard: View {
        let onClick: () -> Void
        
        var body: some View {
            Button(action: onClick) {
                VStack(spacing: 8) {
                    Image(systemName: "shuffle")
                        .font(.system(size: 20, weight: .medium))
                        .foregroundColor(.white)
                    
                    Text("Random")
                        .font(.system(size: 12, weight: .semibold))
                        .foregroundColor(.white)
                        .multilineTextAlignment(.center)
                        .lineLimit(1)
                    
                    Text("Surprise me!")
                        .font(.system(size: 9))
                        .foregroundColor(.white.opacity(0.8))
                        .multilineTextAlignment(.center)
                        .lineLimit(2)
                }
                .frame(width: 80, height: 80)
                .padding(8)
                .background(
                    RoundedRectangle(cornerRadius: 10)
                        .fill(LinearGradient(
                            colors: [.orange, .red],
                            startPoint: .topLeading,
                            endPoint: .bottomTrailing
                        ))
                        .shadow(color: .black.opacity(0.15), radius: 3, x: 0, y: 2)
                )
            }
            .buttonStyle(PlainButtonStyle())
        }
    }

    struct QuickActionPuzzleCard: View {
        let puzzleType: String
        let isNew: Bool
        let onTap: () -> Void
        
        var categoryData: CategoryData? {
            CategoryData.allCategories.first { $0.category == puzzleType }
        }
        
        var body: some View {
            Button(action: onTap) {
                VStack(spacing: 8) {
                    ZStack {
                        Image(systemName: categoryData?.icon ?? "puzzlepiece.fill")
                            .font(.system(size: 20, weight: .medium))
                            .foregroundColor(.white)
                        
                        if isNew {
                            VStack {
                                HStack {
                                    Spacer()
                                    Circle()
                                        .fill(.green)
                                        .frame(width: 8, height: 8)
                                }
                                Spacer()
                            }
                        }
                    }
                    
                    Text(categoryData?.title ?? puzzleType.capitalized)
                        .font(.system(size: 12, weight: .semibold))
                        .foregroundColor(.white)
                        .multilineTextAlignment(.center)
                        .lineLimit(1)
                    
                    Text(isNew ? "New!" : "Play now")
                        .font(.system(size: 9))
                        .foregroundColor(.white.opacity(0.8))
                        .multilineTextAlignment(.center)
                        .lineLimit(2)
                }
                .frame(width: 80, height: 80)
                .padding(8)
                .background(
                    RoundedRectangle(cornerRadius: 10)
                        .fill(categoryData?.backgroundColor ?? .blue)
                        .shadow(color: .black.opacity(0.15), radius: 3, x: 0, y: 2)
                )
            }
            .buttonStyle(PlainButtonStyle())
        }
    }
    


    // MARK: - Enhanced Quick Actions Section
    struct EnhancedQuickActionsSection: View {
        let recentPuzzleTypes: [String]
        let onPuzzleTap: (String) -> Void
        let onRandomTap: () -> Void
        
        @State var showNewHint = true
        
        var body: some View {
            VStack(alignment: .leading, spacing: 12) {
                // Section Header with motivation
                HStack {
                    VStack(alignment: .leading, spacing: 4) {
                        Text("⚡ Quick Actions")
                            .font(.title2)
                            .fontWeight(.semibold)
                            .foregroundColor(Color(red: 0.18, green: 0.18, blue: 0.18))
                        
                        if showNewHint {
                            Text("Tap any to start earning points!")
                                .font(.caption)
                                .foregroundColor(Color(red: 0.42, green: 0.45, blue: 1.0))
                                .fontWeight(.medium)
                        }
                    }
                    
                    Spacer()
                    
                    // Progress indicator
                    HStack(spacing: 4) {
                        Text("\(recentPuzzleTypes.count)/50 tried")
                            .font(.caption2)
                            .fontWeight(.bold)
                            .foregroundColor(.white)
                            .padding(.horizontal, 8)
                            .padding(.vertical, 4)
                            .background(Color(red: 0.42, green: 0.45, blue: 1.0))
                            .cornerRadius(12)
                    }
                }
                
                ScrollView(.horizontal, showsIndicators: false) {
                    HStack(spacing: 12) {
                        // Random Challenge Button
                        RandomChallengeCard(onClick: onRandomTap)
                        
                        // Recent/Recommended puzzle types
                        ForEach(getRecommendedPuzzleTypes(), id: \.self) { puzzleType in
                            QuickActionPuzzleCard(
                                puzzleType: puzzleType,
                                isNew: isNewPuzzleType(puzzleType),
                                onTap: { onPuzzleTap(puzzleType) }
                            )
                        }
                    }
                    .padding(.horizontal, 4)
                }
            }
        }
        
        private func getRecommendedPuzzleTypes() -> [String] {
            if recentPuzzleTypes.count >= 3 {
                return Array(recentPuzzleTypes.prefix(6))
            } else {
                // Smart fallback with rotation
                let allTypes = CategoryData.allCategories.map { $0.category }
                let rotatingTypes = getRotatingCategorySelection(from: allTypes)
                return (recentPuzzleTypes + rotatingTypes).prefix(6).map { $0 }
            }
        }
        
        private func getRotatingCategorySelection(from types: [String]) -> [String] {
            let currentHour = Calendar.current.component(.hour, from: Date())
            let shuffled = types.shuffled()
            let startIndex = currentHour % types.count
            return Array(shuffled.dropFirst(startIndex).prefix(6))
        }
        
        private func isNewPuzzleType(_ puzzleType: String) -> Bool {
            let newTypes = ["pinballdeflector", "memoryprevioussingle", "memorypreviouspair", "wordsearch", "dualtask", "colortextmatching"]
            return newTypes.contains(puzzleType)
        }
    }

    // No Puzzles Available Section
    struct NoPuzzlesAvailableSection: View {
        var body: some View {
            VStack(spacing: 16) {
                Image(systemName: "wifi.exclamationmark")
                    .font(.system(size: 40))
                    .foregroundColor(.orange)
                
                Text("Puzzles Loading")
                    .font(.title2)
                    .fontWeight(.semibold)
                
                Text("We're fetching your puzzles from our servers. This usually takes just a few moments.")
                    .font(.subheadline)
                    .foregroundColor(.secondary)
                    .multilineTextAlignment(.center)
                
                ProgressView()
                    .scaleEffect(1.2)
            }
            .frame(maxWidth: .infinity)
            .padding(32)
            .background(Color.orange.opacity(0.1))
            .cornerRadius(16)
        }
    }

    // MARK: - Helper functions and views
    private func getCompletedTodayCount() -> Int {
        let today = Calendar.current.startOfDay(for: Date())
        let key = "completed_puzzles_\(today.timeIntervalSince1970)"
        return UserDefaults.standard.integer(forKey: key)
    }

    private func getUserEngagementLevel() -> String {
        let completedCount = UserDefaults.standard.integer(forKey: "completed_categories_count")
        if completedCount < 5 {
            return "beginner"
        } else if completedCount < 15 {
            return "intermediate"
        } else {
            return "advanced"
        }
    }
    

    private func getCompletedCategoriesCount() -> Int {
        return UserDefaults.standard.integer(forKey: "completed_categories_count")
    }

    private func getLastDailyGenerationTime() -> Date? {
        let timestamp = UserDefaults.standard.double(forKey: "last_daily_generation_time")
        return timestamp > 0 ? Date(timeIntervalSince1970: timestamp) : nil
    }

    private func generateFreshDailyPuzzles() {
        // Implement fresh puzzle generation
        guard let userEmail = Auth.auth().currentUser?.email else {
            dailyTopicsError = "Please sign in to generate fresh puzzles"
            return
        }
        
        isDailyTopicsLoading = true
        
        // Simulate API call - replace with actual implementation
        DispatchQueue.main.asyncAfter(deadline: .now() + 3) {
            UserDefaults.standard.set(Date().timeIntervalSince1970, forKey: "last_daily_generation_time")
            isDailyTopicsLoading = false
            loadDailyTopics()
        }
    }

    private func refreshUserEngagementData() {
        // Refresh user engagement metrics
        let recentTypes = UserDefaults.standard.getRecentPuzzleTypes()
        UserDefaults.standard.set(recentTypes.count, forKey: "completed_categories_count")
    }

    
        @ViewBuilder
        var settingsTab: some View {
            VStack(alignment: .leading, spacing: 20) {
                Text("Settings")
                    .font(.largeTitle)
                    .fontWeight(.bold)
                    .padding(.horizontal)
                
                ScrollView {
                    VStack(spacing: 20) {
                        // Profile Section
                        ProfileSectionView()

                        // Streak & Coins
                        StreakStatsSection()

                        if Auth.auth().currentUser != nil {
                            CreatorEarningsSectionView()
                                .padding(.horizontal)
                        }

                        DifficultySettingsView()
                                            .padding(.horizontal)
                        #if DEBUG
                        VStack(alignment: .leading, spacing: 12) {
                            Text("Debug & Testing")
                                .font(.headline)
                                .padding(.horizontal)
                            
                            NavigationLink(destination: NotificationTestDebugView()) {
                                HStack {
                                    Image(systemName: "bell.badge")
                                        .foregroundColor(.orange)
                                        .frame(width: 30)
                                    Text("Test Notifications")
                                        .foregroundColor(.primary)
                                    Spacer()
                                    Image(systemName: "chevron.right")
                                        .foregroundColor(.gray)
                                        .font(.caption)
                                }
                                .padding()
                                .background(Color(.systemGray6))
                                .cornerRadius(12)
                            }
                            .padding(.horizontal)
                        }
                        #endif
                        
                        // App Features Section
                        AppFeaturesSectionView(
                            onLeaderboard: {
                                if authStateManager.requireSignIn() {
                                    isShowingLeaderboard = true
                                }
                            },
                            onAchievements: {
                                if authStateManager.requireSignIn() {
                                    isShowingAchievements = true
                                }
                            },
                            onRateApp: { showRatingDialog = true },
                            onUpgradeSubscription: {
                                upgradeFlowTier = .premium
                            },
                            subscriptionManager: subscriptionManager
                        )
                        
                        // Account Section
                        AccountSectionView(
                            onSignOut: {
                                try? Auth.auth().signOut()
                                isSignedOut = true
                            },
                            onSignIn: { isShowingSignIn = true },
                            onSignUp: { isShowingSignUp = true },
                            onNotificationSettings: {showNotificationSettings = true}
                        )
                        if Auth.auth().currentUser != nil {
                            AccountDeletionSection(isSignedOut: $isSignedOut)
                        }
                    }
                    .padding(.horizontal)
                }.sheet(isPresented: $showNotificationSettings) {
                    NotificationSettingsView()
                }.sheet(isPresented: $showRatingDialog) {  // Add this sheet
                    AggressiveRatingDialog(
                        onDismiss: { showRatingDialog = false },
                        onCompleted: { showRatingDialog = false }
                    )
                }.sheet(item: $upgradeFlowTier) { tier in
                    RemotePaywallView(
                        context: .limitReached,
                        targetTier: tier,
                        onSuccess: { upgradeFlowTier = nil },
                        onCancel: { upgradeFlowTier = nil }
                    )
                }
            }
        }
    
        struct CustomPuzzleRowView: View {
            let puzzle: CustomPuzzle
            let onTap: () -> Void
            
            var body: some View {
                HStack {
                    Image(systemName: "puzzlepiece.fill")
                        .foregroundColor(.orange)
                        .frame(width: 40, height: 40)
                        .background(Color.orange.opacity(0.1))
                        .cornerRadius(8)
                    
                    VStack(alignment: .leading, spacing: 4) {
                        Text(puzzle.name)
                            .font(.headline)
                        Text("\(puzzle.format) • \(puzzle.createdAtString)")
                            .font(.caption)
                            .foregroundColor(.gray)
                        Text("By \(puzzle.creator)")
                            .font(.caption2)
                            .foregroundColor(.blue)
                    }
                    
                    Spacer()
                    
                    Image(systemName: "chevron.right")
                        .foregroundColor(.gray)
                }
                .padding()
                .background(Color(.systemGray6))
                .cornerRadius(12)
                .onTapGesture(perform: onTap)
            }
        }

        struct ProfileSectionView: View {
            var body: some View {
                VStack(alignment: .leading, spacing: 12) {
                    Text("Profile")
                        .font(.headline)
                    
                    HStack {
                        Image(systemName: "person.circle.fill")
                            .font(.system(size: 40))
                            .foregroundColor(.blue)
                        
                        VStack(alignment: .leading) {
                            Text(Auth.auth().currentUser?.displayName ?? Auth.auth().currentUser?.email ?? "Guest")
                                .font(.title3)
                                .fontWeight(.semibold)
                            
                            if let email = Auth.auth().currentUser?.email {
                                Text(email)
                                    .font(.caption)
                                    .foregroundColor(.gray)
                            }
                        }
                        Spacer()
                    }
                    .padding()
                    .background(Color(.systemGray6))
                    .cornerRadius(12)
                }
            }
        }

        struct CreatorEarningsSectionView: View {
            @StateObject private var coinManager = CoinManager.shared
            @State private var showPayoutSheet = false

            var body: some View {
                VStack(alignment: .leading, spacing: 12) {
                    Text("Creator Earnings")
                        .font(.headline)

                    if coinManager.isLoadingEarnings {
                        HStack {
                            Spacer()
                            ProgressView()
                            Spacer()
                        }
                        .padding()
                        .background(Color(.systemGray6))
                        .cornerRadius(12)
                    } else if let earnings = coinManager.creatorEarnings, earnings.totalCoinsEarned > 0 {
                        VStack(spacing: 12) {
                            HStack {
                                VStack(alignment: .leading) {
                                    Text("\(earnings.totalCoinsEarned)")
                                        .font(.title)
                                        .fontWeight(.bold)
                                        .foregroundColor(.green)
                                    Text("Coins Earned")
                                        .font(.caption)
                                        .foregroundColor(.gray)
                                }
                                Spacer()
                                VStack(alignment: .trailing) {
                                    Text("\(earnings.totalPlaysMonetized)")
                                        .font(.title)
                                        .fontWeight(.bold)
                                    Text("Plays Monetized")
                                        .font(.caption)
                                        .foregroundColor(.gray)
                                }
                            }

                            if !earnings.gameBreakdown.isEmpty {
                                Divider()

                                Text("Per Game")
                                    .font(.subheadline)
                                    .fontWeight(.semibold)

                                ForEach(earnings.gameBreakdown.prefix(5)) { game in
                                    HStack {
                                        Text(game.gameTitle)
                                            .font(.subheadline)
                                            .lineLimit(1)
                                        Spacer()
                                        Text("\(game.totalCoinsEarned) coins")
                                            .font(.subheadline)
                                            .foregroundColor(.green)
                                            .fontWeight(.medium)
                                    }
                                }
                            }

                            Divider()

                            Button(action: {
                                coinManager.checkPayoutEligibility()
                                coinManager.fetchPayoutHistory()
                                showPayoutSheet = true
                            }) {
                                HStack {
                                    Image(systemName: "dollarsign.circle.fill")
                                    Text("Cash Out Earnings")
                                        .fontWeight(.semibold)
                                }
                                .frame(maxWidth: .infinity)
                                .padding(.vertical, 10)
                                .background(Color.green)
                                .foregroundColor(.white)
                                .cornerRadius(10)
                            }
                        }
                        .padding()
                        .background(Color(.systemGray6))
                        .cornerRadius(12)
                    } else {
                        // Teaser / 0 earnings - tappable to open payouts
                        Button(action: {
                            coinManager.checkPayoutEligibility()
                            showPayoutSheet = true
                        }) {
                            HStack(spacing: 12) {
                                Image(systemName: "banknote.fill")
                                    .font(.title2)
                                    .foregroundColor(.green)
                                VStack(alignment: .leading, spacing: 2) {
                                    Text("Earn real cash from your games! 💰")
                                        .font(.subheadline)
                                        .fontWeight(.medium)
                                        .foregroundColor(.primary)
                                    Text("You get 55% of coins spent on your games. Cash out anytime via Stripe.")
                                        .font(.caption)
                                        .foregroundColor(.gray)
                                }
                                Spacer()
                                Image(systemName: "chevron.right")
                                    .foregroundColor(.gray)
                                    .font(.caption)
                            }
                            .padding()
                            .frame(maxWidth: .infinity, alignment: .leading)
                            .background(Color(.systemGray6))
                            .cornerRadius(12)
                        }
                    }
                }
                .onAppear {
                    coinManager.fetchCreatorEarnings()
                }
                .sheet(isPresented: $showPayoutSheet) {
                    PayoutSheetView()
                }
            }
        }

        struct PayoutSheetView: View {
            @StateObject private var coinManager = CoinManager.shared
            @Environment(\.dismiss) private var dismiss
            @Environment(\.scenePhase) private var scenePhase

            var body: some View {
                NavigationView {
                    ScrollView {
                        VStack(spacing: 20) {
                            if coinManager.isLoadingPayout || coinManager.payoutEligibility == nil {
                                // Loading
                                VStack(spacing: 12) {
                                    ProgressView()
                                    Text("Checking eligibility...")
                                        .font(.caption)
                                        .foregroundColor(.gray)
                                }
                                .frame(maxWidth: .infinity)
                                .padding(40)
                            } else if let eligibility = coinManager.payoutEligibility {
                                if !eligibility.stripeConnected {
                                    // Connect Stripe
                                    stripeConnectView
                                } else if !eligibility.stripePayoutsEnabled {
                                    // Complete onboarding
                                    stripeIncompleteView
                                } else if eligibility.earnedBalance < eligibility.minThreshold {
                                    // Below threshold
                                    belowThresholdView(eligibility: eligibility)
                                } else {
                                    // Eligible
                                    payoutFormView(eligibility: eligibility)
                                }
                            }

                            // Payout message
                            if let msg = coinManager.payoutMessage {
                                Text(msg)
                                    .font(.subheadline)
                                    .foregroundColor(msg.contains("failed") || msg.lowercased().contains("error") ? .red : .green)
                                    .multilineTextAlignment(.center)
                                    .padding(.horizontal)
                            }

                            // Payout history
                            if !coinManager.payoutHistory.isEmpty {
                                payoutHistoryView
                            }
                        }
                        .padding()
                    }
                    .navigationTitle("Cash Out")
                    .navigationBarTitleDisplayMode(.inline)
                    .toolbar {
                        ToolbarItem(placement: .navigationBarTrailing) {
                            Button("Done") { dismiss() }
                        }
                    }
                }
                .onChange(of: scenePhase) { newPhase in
                    if newPhase == .active {
                        coinManager.checkStripeStatus()
                        coinManager.checkPayoutEligibility()
                    }
                }
            }

            private var stripeConnectView: some View {
                VStack(spacing: 16) {
                    Image(systemName: "building.columns.fill")
                        .font(.system(size: 48))
                        .foregroundColor(Color(red: 0.40, green: 0.45, blue: 0.90))

                    Text("Connect Stripe to receive payouts")
                        .font(.headline)
                        .multilineTextAlignment(.center)

                    Text("Set up your Stripe account to cash out your earned coins as real money.")
                        .font(.subheadline)
                        .foregroundColor(.gray)
                        .multilineTextAlignment(.center)

                    Button(action: {
                        coinManager.connectStripe { url in
                            if let url = url {
                                UIApplication.shared.open(url)
                            }
                        }
                    }) {
                        HStack {
                            if coinManager.isLoadingPayout {
                                ProgressView()
                                    .tint(.white)
                            } else {
                                Text("Connect Stripe Account")
                                    .fontWeight(.semibold)
                            }
                        }
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 12)
                        .background(Color(red: 0.40, green: 0.45, blue: 0.90))
                        .foregroundColor(.white)
                        .cornerRadius(10)
                    }
                    .disabled(coinManager.isLoadingPayout)
                }
                .padding()
                .background(Color(.systemGray6))
                .cornerRadius(12)
            }

            private var stripeIncompleteView: some View {
                VStack(spacing: 16) {
                    Image(systemName: "exclamationmark.triangle.fill")
                        .font(.system(size: 48))
                        .foregroundColor(.orange)

                    Text("Complete Stripe Setup")
                        .font(.headline)

                    Text("Your Stripe account setup is incomplete. Tap below to finish.")
                        .font(.subheadline)
                        .foregroundColor(.gray)
                        .multilineTextAlignment(.center)

                    Button(action: {
                        coinManager.connectStripe { url in
                            if let url = url {
                                UIApplication.shared.open(url)
                            }
                        }
                    }) {
                        Text("Continue Setup")
                            .fontWeight(.semibold)
                            .frame(maxWidth: .infinity)
                            .padding(.vertical, 12)
                            .background(Color.orange)
                            .foregroundColor(.white)
                            .cornerRadius(10)
                    }
                }
                .padding()
                .background(Color(.systemGray6))
                .cornerRadius(12)
            }

            private func belowThresholdView(eligibility: CoinManager.PayoutEligibility) -> some View {
                let needed = eligibility.minThreshold - eligibility.earnedBalance
                return VStack(spacing: 12) {
                    Text("\(eligibility.earnedBalance)")
                        .font(.system(size: 36, weight: .bold))
                        .foregroundColor(.green)

                    Text("coins available")
                        .font(.caption)
                        .foregroundColor(.gray)

                    Text("You need \(needed) more coins to reach the minimum payout of \(eligibility.minThreshold) coins.")
                        .font(.subheadline)
                        .foregroundColor(.gray)
                        .multilineTextAlignment(.center)
                }
                .padding()
                .frame(maxWidth: .infinity)
                .background(Color(.systemGray6))
                .cornerRadius(12)
            }

            private func payoutFormView(eligibility: CoinManager.PayoutEligibility) -> some View {
                let coinsToPayOut = eligibility.earnedBalance
                let usdAmount = Double(coinsToPayOut) * eligibility.conversionRate

                return VStack(spacing: 12) {
                    Text("\(coinsToPayOut)")
                        .font(.system(size: 36, weight: .bold))
                        .foregroundColor(.green)

                    Text("coins available")
                        .font(.caption)
                        .foregroundColor(.gray)

                    Text(String(format: "$%.2f USD", usdAmount))
                        .font(.title2)
                        .fontWeight(.bold)

                    Text(String(format: "at $%.3f/coin", eligibility.conversionRate))
                        .font(.caption)
                        .foregroundColor(.gray)

                    Button(action: {
                        coinManager.requestPayout(coinsAmount: coinsToPayOut) { success, _ in
                            if success {
                                coinManager.fetchCreatorEarnings()
                            }
                        }
                    }) {
                        HStack {
                            if coinManager.isLoadingPayout {
                                ProgressView()
                                    .tint(.white)
                            } else {
                                Text("Request Payout")
                                    .fontWeight(.semibold)
                            }
                        }
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 12)
                        .background(Color.green)
                        .foregroundColor(.white)
                        .cornerRadius(10)
                    }
                    .disabled(coinManager.isLoadingPayout)
                }
                .padding()
                .frame(maxWidth: .infinity)
                .background(Color(.systemGray6))
                .cornerRadius(12)
            }

            private var payoutHistoryView: some View {
                VStack(alignment: .leading, spacing: 8) {
                    Divider()
                    Text("Payout History")
                        .font(.subheadline)
                        .fontWeight(.semibold)

                    ForEach(coinManager.payoutHistory.prefix(5)) { payout in
                        HStack {
                            VStack(alignment: .leading) {
                                Text("\(payout.coinsAmount) coins")
                                    .font(.subheadline)
                                    .fontWeight(.medium)
                                Text(String(format: "$%.2f", payout.usdAmount))
                                    .font(.caption)
                                    .foregroundColor(.gray)
                            }
                            Spacer()
                            Text(payout.status.capitalized)
                                .font(.caption)
                                .fontWeight(.medium)
                                .foregroundColor(payoutStatusColor(payout.status))
                        }
                    }
                }
            }

            private func payoutStatusColor(_ status: String) -> Color {
                switch status {
                case "completed": return .green
                case "pending", "processing": return .orange
                case "failed": return .red
                default: return .gray
                }
            }
        }

        struct AppFeaturesSectionView: View {
            let onLeaderboard: () -> Void
            let onAchievements: () -> Void
            let onRateApp: () -> Void
            let onUpgradeSubscription: () -> Void

            let subscriptionManager: SubscriptionManager

            @State private var showCoinStore = false

            var body: some View {
                VStack(alignment: .leading, spacing: 12) {
                    Text("App Features")
                        .font(.headline)

                    VStack(spacing: 0) {
                        SettingsRowView(
                            icon: "crown.fill",
                            title: subscriptionManager.currentTier == .free ? "Upgrade to Premium" : "Manage Subscription",
                            action: {
                                AnalyticsManager.shared.trackSafely(AnalyticsEvent("subscription_button_clicked", parameters: [
                                    "source": "settings_tab",
                                    "current_tier": subscriptionManager.currentTier.rawValue
                                ]))
                                onUpgradeSubscription()
                            },
                            iconColor: .purple
                        )
                        Divider()

                        SettingsRowView(
                            icon: "circle.fill",
                            title: "Coin Store",
                            action: { showCoinStore = true },
                            iconColor: .yellow
                        )
                        .sheet(isPresented: $showCoinStore) {
                            CoinStorePaywallView()
                        }
                        Divider()

                        SettingsRowView(icon: "trophy.fill", title: "Leaderboard", action: onLeaderboard)
                        Divider()
                        SettingsRowView(icon: "rosette", title: "Achievements", action: onAchievements)
                        Divider()
                        SettingsRowView(icon: "star.fill", title: "Rate RiddleVerse", action: onRateApp, iconColor: .orange)
                    }
                    .background(Color(.systemGray6))
                    .cornerRadius(12)
                }
            }
        }



    // Add to your SettingsView
    struct AccountDeletionSection: View {
        @Binding var isSignedOut: Bool
        @State var showDeleteConfirmation = false
        @State var isDeleting = false
        @State var deleteError: String?
        
        var body: some View {
            VStack(alignment: .leading, spacing: 12) {
                Text("Account Management")
                    .font(.headline)
                
                Button("Delete Account") {
                    showDeleteConfirmation = true
                }
                .overlay(
                    Group {
                        if isDeleting {
                            ProgressView()
                                .scaleEffect(0.8)
                        }
                    }
                )
                .disabled(isDeleting)
                .foregroundColor(.red)
                .padding()
                .frame(maxWidth: .infinity)
                .background(Color(.systemGray6))
                .cornerRadius(12)
            }
            .alert("Delete Account", isPresented: $showDeleteConfirmation) {
                Button("Cancel", role: .cancel) { }
                Button("Delete", role: .destructive) {
                    deleteAccount()
                }
            } message: {
                Text("This will permanently delete your account and all associated data. This action cannot be undone.")
            }
        }
        
        private func deleteAccount() {
            // Do exactly what signout does - immediate and simple
            do {
                try Auth.auth().signOut()
                isSignedOut = true
                
                // Now do the async delete stuff in background
                if let user = Auth.auth().currentUser {
                    deleteUserAsync(userId: user.uid, email: user.email ?? "")
                }
            } catch {
                print("Signout failed: \(error.localizedDescription)")
            }
        }

        private func deleteUserAsync(userId: String, email: String) {
            Task {
                // Try Firebase delete (don't care if it fails since user is already signed out)
                try? await Auth.auth().currentUser?.delete()
                
                // Try backend delete (don't care if it fails)
                guard let url = URL(string: "https://puzzleverseai.com/delete-user-account") else { return }
                var request = URLRequest(url: url)
                request.httpMethod = "DELETE"
                request.setValue("application/json", forHTTPHeaderField: "Content-Type")
                request.httpBody = try? JSONSerialization.data(withJSONObject: ["userId": userId, "email": email])
                try? await URLSession.shared.data(for: request)
            }
        }
    }
        struct AccountSectionView: View {
            let onSignOut: () -> Void
            let onSignIn: () -> Void
            let onSignUp: () -> Void
            let onNotificationSettings: () -> Void
            
            var body: some View {
                VStack(alignment: .leading, spacing: 12) {
                    Text("Account")
                        .font(.headline)
                    
                    if Auth.auth().currentUser != nil {
                        Button(action: onSignOut) {
                            HStack {
                                Image(systemName: "arrow.backward.circle")
                                    .foregroundColor(.red)
                                Text("Sign Out")
                                    .foregroundColor(.red)
                                Spacer()
                            }
                            .padding()
                            .background(Color(.systemGray6))
                            .cornerRadius(12)
                        }
                    } else {
                        VStack(spacing: 0) {
                            Button(action: onSignIn) {
                                HStack {
                                    Image(systemName: "person.crop.circle.fill")
                                        .foregroundColor(.blue)
                                    Text("Sign In")
                                    Spacer()
                                }
                                .padding()
                            }
                            Divider()
                            Button(action: onSignUp) {
                                HStack {
                                    Image(systemName: "person.badge.plus")
                                        .foregroundColor(.green)
                                    Text("Create Account")
                                    Spacer()
                                }
                                .padding()
                            }
                            Divider()
                            Button(action: onNotificationSettings) {
                                HStack {
                                    Image(systemName: "person.badge.plus")
                                        .foregroundColor(.green)
                                    Text("Notification Settings")
                                    Spacer()
                                }
                                .padding()
                            }
                        }
                        .background(Color(.systemGray6))
                        .cornerRadius(12)
                    }
                }
            }
        }

        struct SettingsRowView: View {
            let icon: String
            let title: String
            let action: () -> Void
            var iconColor: Color = .blue
            
            var body: some View {
                Button(action: action) {
                    HStack {
                        Image(systemName: icon)
                            .foregroundColor(iconColor)
                            .frame(width: 30)
                        Text(title)
                            .foregroundColor(.primary)
                        Spacer()
                        Image(systemName: "chevron.right")
                            .foregroundColor(.gray)
                            .font(.caption)
                    }
                    .padding()
                }
            }
        }
        
    var bottomTabBar: some View {
        HStack {
            ForEach(TabSelection.allCases, id: \.self) { tab in
                Button(action: {
                    let previousTab = selectedTab
                    selectedTab = tab
                    
                    // Track tab selection
                    AnalyticsManager.shared.trackSafely(AnalyticsEvent("tab_selected", parameters: [
                        "tab_name": tab.title.lowercased(),
                        "previous_tab": previousTab.title.lowercased()
                    ]))
                }) {
                    VStack(spacing: 4) {
                        ZStack(alignment: .topTrailing) {
                            Image(systemName: tab.icon)
                                .font(.system(size: 20))
                                .foregroundColor(selectedTab == tab ? .orange : .white.opacity(0.6))

                            if tab == .createGame && gameGenerationManager.isGenerating && selectedTab != .createGame {
                                Circle()
                                    .fill(Color.orange)
                                    .frame(width: 8, height: 8)
                                    .offset(x: 4, y: -4)
                            } else if tab == .createGame {
                                Text("💰")
                                    .font(.system(size: 8))
                                    .offset(x: 6, y: -6)
                            }
                        }

                        Text(tab.title)
                            .font(.caption2)
                            .lineLimit(1)
                            .foregroundColor(selectedTab == tab ? .orange : .white.opacity(0.6))
                    }
                    .frame(maxWidth: .infinity)
                }
            }
        }
        .padding(.vertical, 8)
        .background(Color(red: 0.12, green: 0.12, blue: 0.18))
    }
    
    private func loadDailyTopics() {
        guard let userEmail = getUserEmail() else {
            dailyTopicsError = "Please sign in to view daily puzzles"
            return
        }
        
        isDailyTopicsLoading = true
        dailyTopicsError = nil
        
        guard let url = URL(string: "https://puzzleverseai.com/list-user-daily-topics?email=\(userEmail)") else {
            isDailyTopicsLoading = false
            dailyTopicsError = "Invalid URL"
            return
        }
        
        URLSession.shared.dataTask(with: url) { data, response, error in
            DispatchQueue.main.async {
                isDailyTopicsLoading = false
                
                if let error = error {
                    print("❌ Failed to fetch daily topics: \(error.localizedDescription)")
                    dailyTopicsError = "Failed to load daily topics"
                    return
                }
                
                guard let data = data,
                      let json = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
                      let topics = json["topics"] as? [String] else {
                    print("❌ Failed to parse daily topics response")
                    dailyTopicsError = "Failed to parse topics"
                    return
                }
                
                dailyTopics = topics
                print("✅ Loaded \(topics.count) daily topics: \(topics)")
                
                // Track analytics
                AnalyticsManager.shared.trackSafely(AnalyticsEvent("daily_topics_loaded", parameters: [
                    "topic_count": topics.count,
                    "topics": topics.joined(separator: ",")
                ]))
            }
        }.resume()
    }

    private func handleDailyPuzzleNavigation() {
        print("📅 Navigating to daily puzzles view")
        
        // Post notification for the app-level handler
        NotificationCenter.default.post(
            name: NSNotification.Name("NavigateToDailyPuzzles"),
            object: nil,
            userInfo: [
                "topics": dailyTopics.joined(separator: ", "),
                "puzzleCount": String(dailyTopics.count * 5) // Assuming 5 puzzles per topic
            ]
        )
        
        // Direct navigation
        isShowingDailyPuzzles = true
    }

    private func getUserEmail() -> String? {
        return UserDefaults.standard.string(forKey: "current_user_email")
    }
    
    func fetchPuzzleURL(for category: String, for difficulty: String = "Easy") -> URL? {
        guard let user = Auth.auth().currentUser else {
            return URL(string: "https://puzzleverseai.com/fetch-random-puzzle-ios/\(category)")
        }
        
        var components = URLComponents(string: "https://puzzleverseai.com/fetch-next-puzzle-ios/\(category)")
        components?.queryItems = [
            URLQueryItem(name: "difficulty", value: difficulty),
            URLQueryItem(name: "userId", value: user.uid),
            URLQueryItem(name: "email", value: user.email ?? "")
        ]
        
        return components?.url
    }
    
    
    func determineNavigationType(for category: String, options: [String]) -> PuzzleNavigationType {
        switch category {
        case "average":
            return .average
        case "division":
            return .division
        case "mathestimation":
            return .estimation
        case "percentages":
            return .percentage
        case "discounts":
            return .discounts
        case "purchasing":
            return .purchasing
        case "conversion":
            return .conversion
        case "connotationwords":
            return .swipeWord
        case "mathtipping":
            return .tipBubble
        case "anagram":
            return .anagram
        case "subtraction":
            return .subtraction
        case "crossword":
            return .crossword
        case "oddoneout":
            return .oddoneout
        case "flowpuzzle":
            return .flowpuzzle
        case "memorystory", "memory_story":
            return .memoryStory
        case "memory_retention", "memoryretention":
                return .memoryRetention
        case "memory_sequencing", "memorysequencing":
            return .memorySequencing
        case "wordprefix", "word_prefix":
            return .wordPrefix
        case "realorai", "real_or_ai", "whichisreal", "which_is_real":
            return .whichIsReal
        case "progressiverevelation":
            return .progressiveReveal
        case "mathexpression", "math_expression":
            return .mathExpression
        case "triangledotmemory", "triangle_dot_memory":
            return .triangleDotMemory
        case "imagequestion":
            return .imageQuestion
        case "symmetry":
            return .symmetry
        case "antonyms", "antonym":
            print("✅ Routing to antonym view (category: \(category))")
            return .antonym
        case "synonyms", "synonym":
            print("✅ Routing to synonym view (category: \(category))")
            return .synonym
        case "memorysquares", "memory_squares":
               return .memorySquares
        case "memorypreviouspair", "memory_previous_pair":
                return .memoryPreviousPair
        case "memoryprevioussingle", "memory_previous_single":
                return .memoryPreviousSingle
        case "pinballdeflector", "pinball_deflector":
                return .pinballDeflector
        case "colorshapematching", "color_shape_matching":
            return .colorshapematching
        case "imagevortex", "image_vortex":
            return .imagevortex
        case "mathcomparison", "math_comparison":
            return .mathComparison
        case "numbersequence", "number_sequence":
            return .numberSequence
        case "numbersum", "number_sum":
            return .numbersum
        case "symbolswipe", "symbol_swipe":
            return .symbolSwipe
        case "uniqueobject", "unique_object":
            return .uniqueObject
        case "wordsearch", "word_search":
            return .wordSearch
        case "geography_cities", "geographycities":
            return .geographyCities
        case "geography_countries", "geographycountries":
            return .geographyCountries
        case "wordsnake", "word_snake":
            return .wordSnake
        case "find_object":
            return .findObject
        case "crypto", "crypto_puzzle":
            return .crypto
        case "imagepuzzle":
            return .imagePuzzle
        case "musicidentification":
            return .musicTrack
        case "letterset", "letter_set":
            print("✅ Routing to letter set view")
            return .letterSet
        case "waldopuzzle", "waldo_puzzle":
            print("✅ Routing to waldo puzzle view")
            return .waldoPuzzle
        default:
            return options.isEmpty ? .qa : .multipleChoice
        }
    }
    
    private func fetchCustomPuzzleSet(puzzleId: String) {
        isLoadingCustomPuzzle = true
        errorMessage = nil
        isPuzzleLoading = true
        selectedPuzzle = nil
        selectedPuzzleSet = nil
        navigationState = .none
        errorMessage = nil
        
        print("🔵 Starting fetch for custom puzzle set with ID: \(puzzleId)")
        
        guard let url = URL(string: "https://puzzleverseai.com/fetch-custom-puzzle?puzzleId=\(puzzleId)") else {
            errorMessage = "Invalid URL"
            isLoadingCustomPuzzle = false
            isPuzzleLoading = false
            return
        }
        
        URLSession.shared.dataTask(with: url) { data, response, error in
            DispatchQueue.main.async {
                isLoadingCustomPuzzle = false
                isPuzzleLoading = false
                
                if let error = error {
                    errorMessage = "Network error: \(error.localizedDescription)"
                    print("🔴 Network error: \(error.localizedDescription)")
                    return
                }
                
                guard let data = data else {
                    errorMessage = "No data received"
                    print("🔴 No data received from server")
                    return
                }
                
                // Debug: Print raw response
                if let jsonString = String(data: data, encoding: .utf8) {
                    print("🔵 Raw response: \(jsonString)")
                }
                
                // First, check if this is a failed puzzle response
                do {
                    let failedResponse = try JSONDecoder().decode(FailedPuzzleResponse.self, from: data)
                    
                    if failedResponse.status == "failed" || failedResponse.status == "regenerating" || failedResponse.status == "generating" {
                        print("🟡 Puzzle failed - showing dialog")
                        print("🟡 Failed puzzle: \(failedResponse.puzzleSet.name)")
                        print("🟡 Reason: \(failedResponse.message)")
                        print("🟡 Action: \(failedResponse.action)")
                        
                        // Show the failed puzzle dialog
                        self.failedPuzzleInfo = failedResponse.puzzleSet
                        self.failedPuzzleMessage = failedResponse.message
                        self.failedPuzzleId = puzzleId
                        self.showFailedPuzzleDialog = true
                        
                        return
                    }
                } catch {
                    // Not a failed response, continue with normal parsing
                    print("🔵 Not a failed response, attempting normal decode")
                }
                
                // Try to decode as normal successful response
                do {
                    let decodedResponse = try JSONDecoder().decode(CustomPuzzleSetResponse.self, from: data)
                    print("🟢 Successfully decoded custom puzzle set response.")
                    
                    let puzzles = decodedResponse.puzzleData.puzzleData.puzzles
                    
                    guard !puzzles.isEmpty else {
                        errorMessage = "No puzzles found in the set."
                        print("🔴 Puzzles array is empty within the response.")
                        return
                    }
                    
                    // Get the first puzzle from the API response
                    let firstApiPuzzle = puzzles[0]
                    
                    // Use the format from the top-level puzzle set, not the individual puzzle
                    let puzzleSetFormat = decodedResponse.format
                    print("🔍 Top-level format: '\(puzzleSetFormat)' vs Individual puzzle format: '\(firstApiPuzzle.format)'")
                    
                    // DateFormatter to convert API's timestamp string to TimeInterval
                    let dateFormatter = ISO8601DateFormatter()
                    var createdAtTimeInterval: TimeInterval = Date().timeIntervalSince1970
                    if let date = dateFormatter.date(from: firstApiPuzzle.timestamp) {
                        createdAtTimeInterval = date.timeIntervalSince1970
                    }
                    
                    // Create your app's Puzzle object using the top-level format
                    let firstPuzzle = Puzzle(
                        question: firstApiPuzzle.question,
                        answer: firstApiPuzzle.answer,
                        hint: firstApiPuzzle.hint ?? "",
                        options: firstApiPuzzle.options,
                        format: puzzleSetFormat, // Use top-level format instead
                        puzzleType: puzzleSetFormat.lowercased(),
                        puzzleId: firstApiPuzzle.puzzleId,
                        id: firstApiPuzzle.puzzleId,
                        name: firstApiPuzzle.topic,
                        createdAt: createdAtTimeInterval * 1000,
                        status: "loaded",
                        difficulty: firstApiPuzzle.difficulty
                    )
                    
                    selectedPuzzle = firstPuzzle
                    selectedPuzzleSet = decodedResponse.asOuterPuzzleData
                    self.currentQuestionIndex = 0

                    // Use custom puzzle navigation determination with corrected format
                    navigationState = determineCustomPuzzleNavigationTypeFromFormat(puzzleSetFormat, topic: decodedResponse.topic)
                    print("🔵 Set navigation state to: \(navigationState) for top-level format: '\(puzzleSetFormat)'")
                    
                } catch let decodingError as DecodingError {
                    errorMessage = "Unable to load this puzzle. It may be processing or have issues."
                    print("🔴 Decoding failed:", decodingError)
                    
                    // Enhanced decoding error reporting (existing code)
                    switch decodingError {
                    case .typeMismatch(let type, let context):
                        print("🔴 Type mismatch: Expected \(type) at path '\(context.codingPathString)' – \(context.debugDescription)")
                    case .valueNotFound(let type, let context):
                        print("🔴 Value not found: Expected \(type) at path '\(context.codingPathString)' – \(context.debugDescription)")
                    case .keyNotFound(let key, let context):
                        print("🔴 Key not found: '\(key.stringValue)' at path '\(context.codingPathString)' – \(context.debugDescription)")
                    case .dataCorrupted(let context):
                        print("🔴 Data corrupted: \(context.debugDescription) at path '\(context.codingPathString)'")
                    @unknown default:
                        print("🔴 Unknown decoding error: \(decodingError)")
                    }
                } catch {
                    errorMessage = "Failed to load puzzle: \(error.localizedDescription)"
                    print("🔴 Generic Error:", error)
                }
            }
        }.resume()
    }
    
    private func requestPuzzleRegeneration(puzzleId: String, puzzleInfo: FailedPuzzleSetInfo) {
        print("🔵 Requesting regeneration for puzzle: \(puzzleInfo.name)")
        
        guard let url = URL(string: "https://puzzleverseai.com/api/request-puzzle-regeneration") else {
            print("🔴 Invalid regeneration URL")
            return
        }
        
        var request = URLRequest(url: url)
        request.httpMethod = "POST"
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        
        let requestBody: [String: Any] = [
            "puzzleSetId": puzzleId,  // Changed from "puzzleId"
            "userEmail": puzzleInfo.creator,  // Changed from "creator"
            "reason": "user_believes_content_appropriate"
            // Removed extra fields: puzzleName, format, requestedAt
        ]
        
        do {
            request.httpBody = try JSONSerialization.data(withJSONObject: requestBody)
        } catch {
            print("🔴 Failed to encode regeneration request: \(error)")
            return
        }
        
        URLSession.shared.dataTask(with: request) { data, response, error in
            DispatchQueue.main.async {
                if let error = error {
                    print("🔴 Regeneration request failed: \(error.localizedDescription)")
                    // You could show an error message to the user here
                    return
                }
                
                if let httpResponse = response as? HTTPURLResponse {
                    if httpResponse.statusCode == 202 {  // Changed from 200 to 202 (Accepted)
                        print("🟢 Regeneration request submitted successfully")
                        // You could show a success message to the user here
                        // Note: User will receive a push notification when regeneration completes
                    } else {
                        print("🔴 Regeneration request failed with status: \(httpResponse.statusCode)")
                        
                        // Try to parse error message from response
                        if let data = data,
                           let json = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
                           let errorMessage = json["error"] as? String {
                            print("🔴 Error details: \(errorMessage)")
                        }
                    }
                }
            }
        }.resume()
    }
    
    private func determineCustomPuzzleNavigationType(for apiPuzzle: ApiPuzzle) -> PuzzleNavigationType {
        let format = apiPuzzle.format.lowercased()
        let topic = apiPuzzle.topic.lowercased()
        
        print("🔍 Determining navigation for custom puzzle - Format: '\(format)', Topic: '\(topic)'")
        print("🔍 Original format: '\(apiPuzzle.format)', Original topic: '\(apiPuzzle.topic)'")
        
        // Use contains() to handle formats like "crossword (new!)" or "anagram (updated)"
        switch true {
        case format.contains("crossword"):
            print("✅ Routing to crossword view (format contains 'crossword')")
            return .crossword
        case format.contains("word search") || format.contains("Word Search"):
            print("✅ Routing to crossword view (format contains 'crossword')")
            return .crossword
        case format.contains("music"):
            return .musicTrack
        case format.contains("image"):
            return .imagePuzzle
        case format.contains("anagram"):
            print("✅ Routing to anagram view (format contains 'anagram')")
            return .anagram
        case format.contains("memory story") || format.contains("memorystory"):
            print("✅ Routing to memory story view (format contains 'memory story')")
            return .memoryStory
        case format.contains("memory retention") || format.contains("memoryretention"):
            print("✅ Routing to memory retention view (format contains 'memory retention')")
            return .memoryRetention
        case format.contains("synonyms") || format.contains("synonym"):
            print("✅ Routing to synonym view (format contains 'synonym')")
            return .synonym
        case format.contains("antonyms") || format.contains("antonym"):
            print("✅ Routing to antonym view (format contains 'antonym')")
            return .antonym
        case format.contains("word prefix") || format.contains("wordprefix"):
            print("✅ Routing to word prefix view (format contains 'word prefix')")
            return .wordPrefix
        case format.contains("memory sequencing") || format.contains("memorysequencing"):
            print("✅ Routing to memory sequencing view (format contains 'memory sequencing')")
            return .memorySequencing
        case format.contains("memory squares") || format.contains("memorysquares"):
            print("✅ Routing to memory squares view (format contains 'memory squares')")
            return .memorySquares
        case format.contains("average"):
            print("✅ Routing to average view (format contains 'average')")
            return .average
        case format.contains("division"):
            print("✅ Routing to division view (format contains 'division')")
            return .division
        case format.contains("percentage"):
            print("✅ Routing to percentage view (format contains 'percentage')")
            return .percentage
        case format.contains("discount"):
            print("✅ Routing to discounts view (format contains 'discount')")
            return .discounts
        case format.contains("purchasing") || format.contains("subscription"):
            print("✅ Routing to purchasing view (format contains 'purchasing')")
            return .purchasing
        case format.contains("conversion"):
            print("✅ Routing to conversion view (format contains 'conversion')")
            return .conversion
        case format.contains("subtraction"):
            print("✅ Routing to subtraction view (format contains 'subtraction')")
            return .subtraction
        case format.contains("estimation"):
            print("✅ Routing to estimation view (format contains 'estimation')")
            return .estimation
        default:
            // Check topic as fallback
            print("🔍 No format match found, checking topic...")
            switch true {
            case topic.contains("crossword"):
                print("✅ Routing to crossword view (topic contains 'crossword')")
                return .crossword
            case topic.contains("anagram"):
                print("✅ Routing to anagram view (topic contains 'anagram')")
                return .anagram
            case topic.contains("memory"):
                if topic.contains("story") {
                    print("✅ Routing to memory story view (topic contains 'memory story')")
                    return .memoryStory
                } else if topic.contains("retention") {
                    print("✅ Routing to memory retention view (topic contains 'memory retention')")
                    return .memoryRetention
                } else if topic.contains("sequencing") {
                    print("✅ Routing to memory sequencing view (topic contains 'memory sequencing')")
                    return .memorySequencing
                } else if topic.contains("squares") {
                    print("✅ Routing to memory squares view (topic contains 'memory squares')")
                    return .memorySquares
                }
                fallthrough
            default:
                // Default to multiple choice or Q&A based on options
                if apiPuzzle.options.isEmpty {
                    print("✅ Routing to QA view (default - no options)")
                    return .qa
                } else {
                    print("✅ Routing to multiple choice view (default - has options)")
                    return .multipleChoice
                }
            }
        }
    }

    private func greetingSection(isSignedOut: Binding<Bool>) -> some View {
        let user = Auth.auth().currentUser
        let hour = Calendar.current.component(.hour, from: Date())
        let greeting: String
        let displayName = user?.displayName ?? user?.email ?? "Guest"
        
        switch hour {
            case 5..<12: greeting = "Good Morning ☀️"
            case 12..<17: greeting = "Good Afternoon 🌤️"
            case 17..<21: greeting = "Good Evening 🌇"
            default: greeting = "Good Night 🌙"
        }

        return HStack {
            Image(systemName: "sparkle")
                .foregroundColor(.gray)

            VStack(alignment: .leading) {
                Text(greeting)
                    .font(.caption)
                    .foregroundColor(.gray)
                Text(displayName)
                    .font(.title3)
                    .fontWeight(.bold)
            }

            Spacer()

            Menu {
                if user != nil {
                    Button {
                            isShowingLeaderboard = true
                        } label: {
                            Label("Leaderboard", systemImage: "trophy.fill")
                        }
                        
                        Button {
                            isShowingTopics = true
                        } label: {
                            Label("Preferred Topics", systemImage: "tag.fill")
                                .foregroundColor(.blue)
                        }
                        
                        Button {
                            isShowingAchievements = true
                        } label: {
                            Label("Achievements", systemImage: "rosette")
                                .foregroundColor(.purple)
                        }
                        
                        Button(role: .destructive) {
                            do {
                                try Auth.auth().signOut()
                                isSignedOut.wrappedValue = true
                            } catch {
                                print("❌ Sign out failed: \(error.localizedDescription)")
                            }
                        } label: {
                            Label("Sign Out", systemImage: "arrow.backward.circle")
                        }
                        Button {
                            isShowingCustomePuzzleList = true
                        } label: {
                            Label("Search Custom Puzzles", systemImage: "rosette")
                                .foregroundColor(.purple)
                        }
                } else {
                    Button {
                        isShowingSignIn = true
                    } label: {
                        Label("Sign In", systemImage: "person.crop.circle.fill")
                    }
                    
                    Button {
                        isShowingSignUp = true
                    } label: {
                        Label("Create Account", systemImage: "person.badge.plus")
                    }
                }
            } label: {
                Image(systemName: user != nil ? "person.fill" : "person.crop.circle")
                    .font(.system(size: 20))
                    .padding(.trailing, 8)
            }
        }
        .padding(.horizontal)
    }

    
    
    
    private func createPuzzleFromCachedDataSync(_ puzzleType: String, data: [String: Any]) -> Puzzle {
        let puzzle = Puzzle(
            question: data["question"] as? String ?? "",
            answer: data["answer"] as? String ?? "",
            hint: data["hint"] as? String ?? "",
            options: data["options"] as? [String] ?? [],
            format: data["format"] as? String ?? "qa",
            puzzleType: puzzleType,
            puzzleId: data["puzzleId"] as? String ?? UUID().uuidString,
            id: UUID().uuidString,
            name: puzzleType,
            createdAt: Date().timeIntervalSince1970 * 1000,
            status: "ready",
            difficulty: data["difficulty"] as? String ?? selectedDifficulty
        )
        
        print("🔧 Created puzzle - question length: \(puzzle.question.count), format: \(puzzle.format)")
        return puzzle
    }

    private func getCategoryDisplayInfo(for category: String) -> (title: String, subtitle: String) {
        switch category {
        case "math":
            return ("Math", "Mathematical Challenges")
        case "storyPuzzle":
            return ("Story Puzzle", "Narrative Mysteries")
        case "anagram":
            return ("Anagram", "Word Scrambles")
        case "antonyms", "antonym":
            return ("Antonyms", "Match Opposite Words")
        case "synonyms", "synonym":
            return ("Synonyms", "Group Similar Words")
        case "trivia":
            return ("Trivia", "General Knowledge")
        case "average":
            return ("Average", "Calculate Averages")
        case "wordsnake", "word_snake":
            return ("Word Snake", "Find Connected Words")
        case "find_object":
            return ("Find Object", "Find Objects to Images")
        case "division":
            return ("Division", "Master Division")
        case "mathestimation":
            return ("Estimation", "Chart Estimation")
        case "percentages":
            return ("Percentage", "Percentage Calculations")
        case "discounts":
            return ("Discounts", "Calculate Discounts")
        case "purchasing":
            return ("Purchasing", "Subscription Calculations")
        case "connotationwords":
            return ("Word Connotations", "Sort words by positive/negative connotation")
        case "conversion":
            return ("Conversion", "Unit Comparisons")
        case "mathtipping":
            return ("Tip Calculation", "Calculate correct tip amounts")
        case "memory_retention", "memoryretention":
            return ("Memory Retention", "Audio & Categorization")
        case "memory_sequencing", "memorysequencing":
            return ("Memory Sequencing", "Audio & Sequence Events")
        case "memorystory", "memory_story":
            return ("Memory Story", "Audio Memory Challenges")
        case "wordprefix", "word_prefix":
            return ("Word Prefix", "Find words with prefix")
        case "crossword":
            return ("Crossword", "Word Puzzles")
        case "memorysquares", "memory_squares":
            return ("Memory Squares", "Pattern Recognition")
        case "contextswitch", "context_switch":
            return ("Context Switch", "Memory & Interference")
        case "mathcrossword":
            return ("Math Crossword", "Math Puzzles")
        case "dualtask", "dual_task":
            return ("Dual Task", "Switch Between Tasks")
        case "colortextmatching", "color_text_matching":
            return ("Color Text Match", "Match Meaning & Color")
        case "geography_cities", "geographycities":
            return ("Geography Cities", "Place Cities on World Map")
        case "geography_countries", "geographycountries":
            return ("Geography Countries", "Place Countries on World Map")
        default:
            return (category.capitalized, "Test your \(category.capitalized) skills")
        }
    }

    var createQuizButton: some View {
        Button(action: { showCreatePuzzle = true }) {
            Text("+ Create Custom Quiz")
                .fontWeight(.semibold)
                .frame(maxWidth: .infinity)
                .padding()
                .background(Color.orange)
                .foregroundColor(.white)
                .cornerRadius(12)
        }
        .sheet(item: $currentSheet) { sheetType in
            switch sheetType {
            case .startPuzzle(let category):
                let displayInfo = getCategoryDisplayInfo(for: category)
                StartPuzzleView(
                    quizTitle: displayInfo.title,
                    puzzleType: category,
                    selectedDifficulty: $selectedDifficulty,
                    onPlayNow: { puzzleCount in
                        print("🔵 StartPuzzleView onPlayNow called for category: \(category) with \(puzzleCount) puzzles")
                        
                        // Set target and reset session BEFORE closing sheet
                        targetPuzzleCount = puzzleCount
                        completedPuzzleCount = 0
                        sessionStats = []
                        
                        // Set session flag immediately for multi-puzzle sessions
                        if puzzleCount > 1 {
                            isInActiveSession = true
                            print("🚀 Set isInActiveSession = true for multi-puzzle session")
                        }
                        
                        // Close sheet
                        currentSheet = nil
                        
                        // Clear any existing completion state
                        showCompletionScreen = false
                        
                        // Immediate navigation with loading
                        isPuzzleLoading = true
                        navigationState = .qa // Default state
                        selectedPuzzle = nil
                    },
                    onBack: {
                        currentSheet = nil
                    }
                )
                .onAppear {
                    print("🔵 StartPuzzleView appeared for category: \(category)")
                    selectedDifficulty = difficultyManager.selectedDifficulty
                }
                
            case .createPuzzle:
                CreatePuzzleView()
            }
        }
        .padding(.horizontal)
    }

    var featuredPuzzlesSection: some View {
        VStack(alignment: .leading) {
            Text("Featured Custom Puzzles")
                .font(.subheadline)
                .fontWeight(.medium)
                .padding(.horizontal)
            
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 16) {
                    ForEach(viewModel.customPuzzles.indices, id: \.self) { index in
                        let puzzle = viewModel.customPuzzles[index]
                        
                        QuizCardView(
                            title: puzzle.name,
                            subtitle: "\(puzzle.format) \(puzzle.createdAtString) \n \(puzzle.creator)",
                            imageName: "puzzlepiece.fill",
                            onTap: {
                                fetchCustomPuzzleSet(puzzleId: puzzle.id)
                            }
                        )
                        .frame(width: 200)
                        
                        if index == viewModel.customPuzzles.count - 1 {
                            ProgressView()
                                .onAppear {
                                    viewModel.loadMoreCustomPuzzles()
                                }
                        }
                    }
                }
                .padding(.horizontal)
            }
        }
        .onAppear {
            if viewModel.customPuzzles.isEmpty {
                viewModel.fetchCustomPuzzles()
            }
        }
    }
    
    @ViewBuilder
    var completionDestination: some View {
        Group {
            if let puzzleSetData = selectedPuzzleSet {
                // Custom puzzle set completion
                PuzzleSetCompletionView(
                    puzzleSetData: puzzleSetData,
                    earnedPoints: completionEarnedPoints,
                    onReturnHome: {
                        print("🔵 PuzzleSetCompletion: Return Home tapped")
                        
                        // Clear completion screen first
                        showCompletionScreen = false
                        
                        // Clear all puzzle states
                        selectedPuzzleSet = nil
                        selectedPuzzle = nil
                        navigationState = .none
                        currentQuestionIndex = 0
                        currentSubtractionProblemIndex = 0
                        isPuzzleLoading = false
                        completedPuzzleCount = 0
                        sessionStats = []
                        
                        // Return to appropriate tab
                        selectedTab = .customPuzzles
                    }
                )
            } else {
                // Single puzzle session completion
                PuzzleCompletionView(
                    completionType: .success(
                        streakDays: completedPuzzleCount,
                        earnedPoints: sessionStats.reduce(0) { $0 + $1.correctAnswers * 10 }
                    ),
                    isCustomPuzzle: false,
                    customPuzzleId: nil,
                    sessionStats: SessionStatistics(
                        correctAnswers: sessionStats.reduce(0) { $0 + $1.correctAnswers },
                        totalAnswers: sessionStats.reduce(0) { $0 + $1.totalAnswers },
                        totalTimeSeconds: sessionStats.reduce(0) { $0 + $1.totalTimeSeconds },
                        bestStreak: sessionStats.map { $0.bestStreak }.max() ?? 1,
                        currentStreak: sessionStats.last?.bestStreak ?? 0, // Add this
                        totalScore: sessionStats.reduce(0) { $0 + $1.correctAnswers * 10 }, // Add this
                        individualTimes: sessionStats.map { $0.totalTimeSeconds }, // Add this
                        puzzleType: selectedPuzzle?.puzzleType ?? "unknown"
                    ),
                    puzzleType: sessionStats.first?.puzzleType ?? "unknown",
                    onReturnHome: {
                        print("🔵 PuzzleCompletion: Return Home tapped")
                        
                        // Clear completion screen first
                        showCompletionScreen = false
                        
                        // Clear all session data
                        selectedPuzzleSet = nil
                        selectedPuzzle = nil
                        navigationState = .none
                        currentQuestionIndex = 0
                        completedPuzzleCount = 0
                        sessionStats = []
                        
                        // Return to for you tab
                        selectedTab = .forYou
                    },
                    onViewLeaderboard: { _ in
                        showCompletionScreen = false
                        isShowingLeaderboard = true
                    },
                    onStartPuzzle: { puzzleType, difficulty in
                        print("🔵 PuzzleCompletion: Start new puzzle tapped")
                        
                        // Clear completion screen
                        showCompletionScreen = false
                        
                        // Reset session for new puzzle
                        completedPuzzleCount = 0
                        sessionStats = []
                        
                        // Start new puzzle
                        self.fetchPuzzle(for: puzzleType, for: difficulty)
                    }
                )
            }
        }
        .onAppear {
            print("🎉 Completion destination appeared - selectedPuzzleSet: \(selectedPuzzleSet != nil)")
        }
    }
    
    // MARK: - Navigation Destinations
    @ViewBuilder
        var puzzleDestination: some View {
            Group {
                if isPuzzleLoading {
                    loadingView
                        .onAppear { print("🔵 Showing loading view") }
                } else if let puzzle = selectedPuzzle {
                    switch navigationState {
                    case .qa:
                        qaPuzzleDestination
                    case .symmetry:
                        symmetryPuzzleDestination
                    case .multipleChoice:
                        mcPuzzleDestination
                    case .average:
                        averagePuzzleDestination
                    case .oddoneout:
                        oddOneOutPuzzleDestination
                    case .letterSet:
                        letterSetPuzzleDestination
                    case .waldoPuzzle:
                        waldoPuzzleDestination
                    case .flowpuzzle:
                        flowPuzzleDestination
                    case .division:
                        divisionPuzzleDestination
                    case .estimation:
                        estimationPuzzleDestination
                    case .percentage:
                        percentagePuzzleDestination
                    case .discounts:
                        discountsPuzzleDestination
                    case .purchasing:
                        purchasingPuzzleDestination
                    case .conversion:
                        conversionPuzzleDestination
                    case .anagram:
                        anagramPuzzleDestination
                    case .wordSnake:
                        wordSnakePuzzleDestination
                    case .findObject:
                        findObjectPuzzleDestination
                    case .imagePuzzle:
                        imagePuzzleDestination
                    case .swipeWord:
                        swipeWordPuzzleDestination
                    case .tipBubble:
                        tipBubblePuzzleDestination
                    case .subtraction:
                        subtractionPuzzleDestination
                    case .memoryRetention:
                        memoryRetentionPuzzleDestination
                    case .memorySequencing:
                        memorySequencingPuzzleDestination
                    case .wordPrefix:
                        wordPrefixPuzzleDestination
                    case .antonym:
                        antonymPuzzleDestination
                    case .synonym:
                        synonymPuzzleDestination
                    case .sentencetransitions:
                        mcPuzzleDestination
                    case .crossword:
                        crosswordPuzzleDestination
                    case .memoryStory:
                        memoryStoryPuzzleDestination
                    case .memorySquares:
                        memorySquaresPuzzleDestination
                    case .memoryPreviousPair:
                        memoryPreviousPairPuzzleDestination
                    case .memoryPreviousSingle:
                        memoryPreviousSinglePuzzleDestination
                    case .pinballDeflector:
                        pinballDeflectorPuzzleDestination
                    case.imagevortex:
                        imageVortexPuzzleDestination
                    case .colorshapematching:
                        colorShapeMatchingPuzzleDestination
                    case .mathComparison:
                        mathComparisonPuzzleDestination
                    case .numberSequence:
                        numberSequencePuzzleDestination
                    case .numbersum:
                        numberSumPuzzleDestination
                    case .symbolSwipe:
                        symbolSwipePuzzleDestination
                    case .uniqueObject:
                        uniqueObjectPuzzleDestination
                    case .wordSearch:
                        wordSearchPuzzleDestination
                    case .contextswitch:
                        contextSwitchPuzzleDestination
                    case .mathCrossword:
                        mathCrosswordPuzzleDestination
                    case .dualTask:
                        dualTaskPuzzleDestination
                    case .colorTextMatching:
                        colorTextMatchingPuzzleDestination
                    case .geographyCities:
                        geographyCitiesPuzzleDestination
                    case .geographyCountries:
                        geographyCountriesPuzzleDestination
                    case .crypto:
                        cryptoPuzzleDestination
                    case .musicTrack:
                        musicMatchPuzzleDestination
                    case .whichIsReal:
                        whichIsRealPuzzleDestination
                    case .progressiveReveal:
                        progressiveRevealPuzzleDestination
                    case .mathExpression:
                        mathExpressionPuzzleDestination
                    case .triangleDotMemory:
                        triangleDotMemoryPuzzleDestination
                    case .imageQuestion:
                        imageQuestionPuzzleDestination
                    case .none:
                        Text("Loading puzzle...")
                            .onAppear {
                                checkAppVersion()
                                print("🔴 Navigation state is .none with puzzle present")
                                // Try to recover by setting navigation state
                                let recoveryNavState = determineNavigationType(for: puzzle.puzzleType ?? "", options: puzzle.options)
                                navigationState = recoveryNavState
                            }
                    }
                } else {
                    Text("No puzzle selected")
                        .onAppear { print("🔴 puzzleDestination: selectedPuzzle is nil") }
                }
            }
        }
    
    private func isLocalPuzzleType(_ category: String) -> Bool {
        let localTypes: Set<String> = [
            "memoryprevioussingle", "memory_previous_single",
            "memorypreviouspair", "memory_previous_pair",
            "colorshapematching", "color_shape_matching",
            "imagevortex", "image_vortex",
            "mathcomparison", "math_comparison",
            "numbersequence", "number_sequence",
            "numbersum", "number_sum",
            "symbolswipe", "symbol_swipe",
            "uniqueobject", "unique_object",
            "contextswitch", "dualtask", "dual_task",
            "mathcrossword", "math_crossword",
            "colortextmatching", "color_text_matching",
            "geographycities", "geography_cities",
            "geographycountries", "geography_countries",
            "conversion", "discounts", "discount",
            "mathtipping", "math_tipping",
            "mathestimation", "math_estimation",
            "purchasing", "division", "average",
            "percentages", "percentage", "pinballdeflector", "triangledotmemory"
        ]
        return localTypes.contains(category)
    }
    
    func handlePuzzleCompletion(for puzzle: Puzzle) {
        print("🔵 handlePuzzleCompletion called for puzzle type: \(puzzle.puzzleType ?? "unknown")")
        print("🔵 selectedPuzzleSet is nil: \(selectedPuzzleSet == nil)")
        print("🔵 currentQuestionIndex: \(currentQuestionIndex)")
        print("🔵 completedPuzzleCount: \(completedPuzzleCount), targetPuzzleCount: \(targetPuzzleCount)")
        
        if let puzzleType = puzzle.puzzleType {
            UserDefaults.standard.addRecentPuzzleType(puzzleType)
        }
        
        // MARK: - Analytics and Session Stats (keep your existing code)
        let completionTime = Date()
        let timeSpent = sessionStartTime != nil ? completionTime.timeIntervalSince(sessionStartTime!) : 0
        
        userStatsManager.recordPuzzleCompletion(
            puzzleType: puzzle.puzzleType ?? "unknown",
            score: currentScore,
            timeSpentSeconds: Int(timeSpent),
            isCorrect: true,
            difficulty: puzzle.difficulty ?? selectedDifficulty
        )
        
        let puzzleSessionStats = SessionStatistics(
            correctAnswers: 1,
            totalAnswers: 1,
            totalTimeSeconds: Int(timeSpent),
            bestStreak: 1,
            currentStreak: 1,
            totalScore: currentScore,
            individualTimes: [Int(timeSpent)],
            puzzleType: puzzle.puzzleType ?? "unknown"
        )
        sessionStats.append(puzzleSessionStats)
        
        // Increment completed count
        completedPuzzleCount += 1
        
        // Track analytics
        AnalyticsManager.shared.trackFirstPuzzleConversion(
            puzzleType: puzzle.puzzleType ?? "unknown",
            score: currentScore
        )
        
        if completedPuzzleCount >= 2 && timeSpent > 300 {
            AnalyticsManager.shared.trackEngagementConversion(
                sessionDuration: timeSpent,
                puzzlesSolved: completedPuzzleCount
            )
        }
        
        AnalyticsManager.shared.trackPuzzleCompleted(
            puzzleType: puzzle.puzzleType ?? "unknown",
            difficulty: puzzle.difficulty,
            score: currentScore
        )
        
        if let groupId = currentGroupId,
           let puzzleType = puzzle.puzzleType,
           let userId = Auth.auth().currentUser?.email {
            
            GroupCompletionManager.shared.markTypeCompleted(
                userId: userId,
                groupId: groupId,
                puzzleType: puzzleType
            )
            
            // Check if this completes the puzzle type (5 puzzles)
            if completedPuzzleCount >= targetPuzzleCount {
                // Return to group detail instead of completion screen
                currentGroupId = nil
                selectedPuzzleGroup = GroupCompletionManager.shared.getAllGroups(userId: userId)
                    .first { $0.id == groupId }
                showPuzzleGroupDetail = true
                return
            }
        }
        
        if let set = selectedPuzzleSet {
            // CUSTOM PUZZLE SET MODE
            print("🔵 Custom puzzle set mode")
            let nextIndex = currentQuestionIndex + 1
            let totalPuzzles = set.puzzleData.puzzles.count
            
            if nextIndex < totalPuzzles {
                print("🔵 Loading next puzzle in set (\(nextIndex + 1)/\(totalPuzzles))")
                currentQuestionIndex = nextIndex
                
                let apiPuzzle = set.puzzleData.puzzles[nextIndex]
                let nextPuzzle = Puzzle(apiPuzzle: apiPuzzle, outerPuzzleData: set)
                let newNavigationState = determineCustomPuzzleNavigationTypeFromFormat(set.format, topic: set.topic)
                
                selectedPuzzle = nextPuzzle
                navigationState = newNavigationState
                print("🔵 Successfully updated to next puzzle in set")
                
            } else {
                print("🎉 CUSTOM PUZZLE SET COMPLETED!")
                completionEarnedPoints = totalPuzzles * 20
                isInActiveSession = false  // End session
                showCompletionScreen = true
            }
            
        } else {
            // SINGLE PUZZLE SESSION MODE
            print("🔵 Single puzzle session mode")
            print("🔵 Completed: \(completedPuzzleCount), Target: \(targetPuzzleCount)")
            
            if completedPuzzleCount >= targetPuzzleCount {
                print("🎉 SESSION COMPLETE! Completed \(completedPuzzleCount)/\(targetPuzzleCount) puzzles")
                isInActiveSession = false  // End session
                showCompletionScreen = true
                
            } else {
                // Continue session - generate next puzzle using reactive system
                print("🔵 Session continuing - generating next puzzle (\(completedPuzzleCount)/\(targetPuzzleCount))")
                
                if let puzzleType = puzzle.puzzleType {
                    // CRITICAL: Set both loading and session flags to protect state
                    keepCurrentViewWhileFetchingNext = true
                    isPuzzleLoading = true
                    isInActiveSession = true  // Keep session active
                    print("🔥 Set isPuzzleLoading = true and isInActiveSession = true")
                    
                    print("🔵 Fetching next puzzle in session immediately: \(puzzleType)")
                    
                    // Use simplified puzzle fetching
                    DispatchQueue.main.asyncAfter(deadline: .now() + 0.5) {
                        self.fetchPuzzle(for: puzzleType, for: self.selectedDifficulty)
                    }
                    
                } else {
                    print("🔵 No puzzle type available, ending session")
                    isInActiveSession = false
                    showCompletionScreen = true
                }
            }
        }
        
        // Update session stats
        AnalyticsManager.shared.puzzleCompleted(score: currentScore)
        trackRetentionSignals(puzzle: puzzle)
    }
    
    private func calculateEngagementLevel(timeSpent: TimeInterval, score: Int) -> String {
        let timeScore = min(timeSpent / 300.0, 1.0) // Normalize to 5 minutes max
        let scoreNormalized = min(Double(score) / 100.0, 1.0) // Normalize score
        let engagementScore = (timeScore + scoreNormalized) / 2.0
        
        switch engagementScore {
        case 0.8...1.0: return "very_high"
        case 0.6..<0.8: return "high"
        case 0.4..<0.6: return "medium"
        case 0.2..<0.4: return "low"
        default: return "very_low"
        }
    }

    private func calculateSkillLevel(score: Int, puzzles: Int) -> String {
        let averageScore = Double(score) / Double(puzzles)
        
        switch averageScore {
        case 80...Double.greatestFiniteMagnitude: return "expert"
        case 60..<80: return "advanced"
        case 40..<60: return "intermediate"
        case 20..<40: return "beginner"
        default: return "novice"
        }
    }

    private func updateUserPropertiesOnCompletion(set: OuterPuzzleData, score: Int, timeSpent: TimeInterval) {
        Task {
            await MainActor.run {
                // Update Firebase user properties for better ad targeting
                Analytics.setUserProperty(set.format, forName: "preferred_puzzle_format")
                Analytics.setUserProperty(set.topic, forName: "last_completed_topic")
                
                let skillLevel = calculateSkillLevel(score: score, puzzles: set.puzzleData.puzzles.count)
                Analytics.setUserProperty(skillLevel, forName: "skill_level")
                
                let engagementLevel = calculateEngagementLevel(timeSpent: timeSpent, score: score)
                Analytics.setUserProperty(engagementLevel, forName: "engagement_level")
                
                // Track completion count
                let completions = UserDefaults.standard.integer(forKey: "puzzle_sets_completed") + 1
                UserDefaults.standard.set(completions, forKey: "puzzle_sets_completed")
                Analytics.setUserProperty("\(completions)", forName: "total_completions")
                
                print("🔥 Updated user properties for Google Ads targeting")
            }
        }
    }

    private func trackRetentionSignals(puzzle: Puzzle) {
        // Track signals that indicate user retention
        let currentDate = Date()
        let lastActiveKey = "last_active_date"
        
        if let lastActive = UserDefaults.standard.object(forKey: lastActiveKey) as? Date {
            let daysSinceLastActive = Calendar.current.dateComponents([.day], from: lastActive, to: currentDate).day ?? 0
            
            if daysSinceLastActive >= 1 {
                // User returned after at least a day - good retention signal
                AnalyticsManager.shared.track(AnalyticsEvent("user_returned", parameters: [
                    "days_since_last_active": daysSinceLastActive,
                    "return_activity": "puzzle_completion",
                    "puzzle_type": puzzle.puzzleType ?? "unknown"
                ]))
                
                if daysSinceLastActive >= 7 {
                    // Weekly retention - very valuable
                    AnalyticsManager.shared.trackConversion(
                        AnalyticsEvent("weekly_retention", parameters: [
                            "days_since_last_active": daysSinceLastActive
                        ]),
                        value: 15.0
                    )
                }
            }
        }
        
        UserDefaults.standard.set(currentDate, forKey: lastActiveKey)
        
        // Track daily active user
        let todayKey = "active_date_\(Calendar.current.dateComponents([.year, .month, .day], from: currentDate))"
        if !UserDefaults.standard.bool(forKey: todayKey) {
            UserDefaults.standard.set(true, forKey: todayKey)
            
            AnalyticsManager.shared.track(AnalyticsEvent("daily_active_user", parameters: [
                "date": currentDate.timeIntervalSince1970,
                "activity_type": "puzzle_completion"
            ]))
        }
    }
    
    private func determineCustomPuzzleNavigationTypeFromFormat(_ format: String, topic: String) -> PuzzleNavigationType {
        // Clean the strings by removing common punctuation and trimming whitespace
        let lowercaseFormat = format.lowercased()
            .trimmingCharacters(in: .whitespacesAndNewlines)
            .replacingOccurrences(of: ".", with: "")
            .replacingOccurrences(of: "!", with: "")
            .replacingOccurrences(of: "(", with: "")
            .replacingOccurrences(of: ")", with: "")
        
        let lowercaseTopic = topic.lowercased()
            .trimmingCharacters(in: .whitespacesAndNewlines)
            .replacingOccurrences(of: ".", with: "")
            .replacingOccurrences(of: "!", with: "")
            .replacingOccurrences(of: "(", with: "")
            .replacingOccurrences(of: ")", with: "")
        
        print("🔍 Determining navigation from top-level - Format: '\(lowercaseFormat)', Topic: '\(lowercaseTopic)'")
        print("🔍 Original format: '\(format)', Original topic: '\(topic)'")
        
        // Use contains() to handle formats with extra characters, punctuation, etc.
        switch true {
        case lowercaseFormat.contains("word snake") || lowercaseFormat.contains("wordsnake"):
            print("✅ Routing to word snake view (format contains 'word snake')")
            return .wordSnake
        case lowercaseFormat.contains("imagequestion") || lowercaseFormat.contains("image question"):
            print("✅ Routing to image question view (format contains 'image question')")
            return .imageQuestion
        case lowercaseFormat.contains("find_object"):
            return .findObject
        case lowercaseFormat.contains("word search") || lowercaseFormat.contains("wordsearch"):
            print("✅ Routing to word search view (format contains 'word search')")
            return .wordSearch
        case lowercaseFormat.contains("music"):
            return .musicTrack
        case lowercaseFormat.contains("image"):
            return .imagePuzzle
        case lowercaseFormat.contains("crossword"):
            print("✅ Routing to crossword view (format contains 'crossword')")
            return .crossword
        case lowercaseTopic.contains("real or ai"):
            return .whichIsReal
        case lowercaseTopic.contains("Picture Guess"):
            return .progressiveReveal
        case lowercaseFormat.contains("anagram"):
            print("✅ Routing to anagram view (format contains 'anagram')")
            return .anagram
        case lowercaseFormat.contains("crypto") || lowercaseFormat.contains("cryptogram"):
            print("✅ Routing to crypto view (format contains 'crypto')")
            return .crypto
        case lowercaseFormat.contains("memory story") || lowercaseFormat.contains("memorystory"):
            print("✅ Routing to memory story view (format contains 'memory story')")
            return .memoryStory
        case lowercaseFormat.contains("memory retention") || lowercaseFormat.contains("memoryretention"):
            print("✅ Routing to memory retention view (format contains 'memory retention')")
            return .memoryRetention
        case lowercaseFormat.contains("synonyms") || lowercaseFormat.contains("synonym"):
            print("✅ Routing to synonym view (format contains 'synonym')")
            return .synonym
        case lowercaseFormat.contains("antonyms") || lowercaseFormat.contains("antonym"):
            print("✅ Routing to antonym view (format contains 'antonym')")
            return .antonym
        case lowercaseFormat.contains("word prefix") || lowercaseFormat.contains("wordprefix"):
            print("✅ Routing to word prefix view (format contains 'word prefix')")
            return .wordPrefix
        case lowercaseFormat.contains("memory sequencing") || lowercaseFormat.contains("memorysequencing"):
            print("✅ Routing to memory sequencing view (format contains 'memory sequencing')")
            return .memorySequencing
        case lowercaseFormat.contains("memory squares") || lowercaseFormat.contains("memorysquares"):
            print("✅ Routing to memory squares view (format contains 'memory squares')")
            return .memorySquares
        case lowercaseFormat.contains("memory previous pair") || lowercaseFormat.contains("memorypreviouspair"):
            print("✅ Routing to memory previous pair view (format contains 'memory previous pair')")
            return .memoryPreviousPair
        case lowercaseFormat.contains("memory previous single") || lowercaseFormat.contains("memoryprevioussingle"):
            print("✅ Routing to memory previous single view (format contains 'memory previous single')")
            return .memoryPreviousSingle
        case lowercaseFormat.contains("pinball deflector") || lowercaseFormat.contains("pinballdeflector"):
            print("✅ Routing to pinball deflector view (format contains 'pinball deflector')")
            return .pinballDeflector
        case lowercaseFormat.contains("average"):
            print("✅ Routing to average view (format contains 'average')")
            return .average
        case lowercaseFormat.contains("division"):
            print("✅ Routing to division view (format contains 'division')")
            return .division
        case lowercaseFormat.contains("percentage"):
            print("✅ Routing to percentage view (format contains 'percentage')")
            return .percentage
        case lowercaseFormat.contains("discount"):
            print("✅ Routing to discounts view (format contains 'discount')")
            return .discounts
        case lowercaseFormat.contains("purchasing") || lowercaseFormat.contains("subscription"):
            print("✅ Routing to purchasing view (format contains 'purchasing')")
            return .purchasing
        case lowercaseFormat.contains("conversion"):
            print("✅ Routing to conversion view (format contains 'conversion')")
            return .conversion
        case lowercaseFormat.contains("subtraction"):
            print("✅ Routing to subtraction view (format contains 'subtraction')")
            return .subtraction
        case lowercaseFormat.contains("estimation"):
            print("✅ Routing to estimation view (format contains 'estimation')")
            return .estimation
        default:
            // Check topic as fallback with contains logic
            print("🔍 No format match found, checking topic...")
            switch true {
            case lowercaseTopic.contains("word search") || lowercaseTopic.contains("wordsearch"):
                print("✅ Routing to word search view (topic contains 'word search')")
                return .wordSearch
            case lowercaseTopic.contains("music"):
                return .musicTrack
            case lowercaseTopic.contains("image"):
                return .imagePuzzle
            case lowercaseTopic.contains("crossword"):
                print("✅ Routing to crossword view (topic contains 'crossword')")
                return .crossword
            case lowercaseTopic.contains("anagram"):
                print("✅ Routing to anagram view (topic contains 'anagram')")
                return .anagram
            case lowercaseFormat.contains("crypto") || lowercaseFormat.contains("cryptogram"):
                print("✅ Routing to crypto view (format contains 'crypto')")
                return .crypto
            case lowercaseTopic.contains("memory"):
                if lowercaseTopic.contains("story") {
                    print("✅ Routing to memory story view (topic contains 'memory story')")
                    return .memoryStory
                } else if lowercaseTopic.contains("retention") {
                    print("✅ Routing to memory retention view (topic contains 'memory retention')")
                    return .memoryRetention
                } else if lowercaseTopic.contains("sequencing") {
                    print("✅ Routing to memory sequencing view (topic contains 'memory sequencing')")
                    return .memorySequencing
                } else if lowercaseTopic.contains("squares") {
                    print("✅ Routing to memory squares view (topic contains 'memory squares')")
                    return .memorySquares
                }
                fallthrough
            default:
                // For custom puzzles, default to multiple choice since they usually have options
                print("✅ Routing to multiple choice view (default for custom puzzles)")
                return .multipleChoice
            }
        }
    }
    
    private func handleDailyStreakOnAppOpen() {
            let (shouldReset, streakReward) = DailyStreakManager.shared.processAppOpen()
            
            if shouldReset {
                print("🔄 Resetting all puzzle group completion for new day")
                // Add your group reset logic here
            }
            
            if let reward = streakReward {
                dailyStreakReward = reward
                DispatchQueue.main.asyncAfter(deadline: .now() + 0.5) {
                    withAnimation(.spring(dampingFraction: 0.8)) {
                        showStreakDialog = true
                    }
                }
            }
        }
    
    var body: some View {
        NavigationView {
            ZStack {
                VStack {
                    HeaderSection()

                    
                    // Basic navigation links
                    NavigationLink(destination: LoginWelcomeView(), isActive: $isSignedOut) { EmptyView() }
                    NavigationLink(destination: LoginWelcomeView(), isActive: $isShowingSignIn) { EmptyView() }
                    NavigationLink(destination: LoginWelcomeView(), isActive: $isShowingSignUp) { EmptyView() }
                    NavigationLink(destination: LeaderboardView(currentUserId: Auth.auth().currentUser?.uid ?? "unknown"), isActive: $isShowingLeaderboard) { EmptyView() }
                    NavigationLink(destination: PreferredTopicsView(), isActive: $isShowingTopics) { EmptyView() }
                    NavigationLink(destination: AchievementDashboardView(userEmail: Auth.auth().currentUser?.email ?? "unknown"), isActive: $isShowingAchievements) { EmptyView() }
                    NavigationLink(destination: CustomRiddlesListView(), isActive: $isShowingCustomePuzzleList) { EmptyView() }
                    NavigationLink(destination: DailyPuzzlesView(), isActive: $isShowingDailyPuzzles) { EmptyView() }

                    // SINGLE PUZZLE NavigationLink
                    NavigationLink(
                        destination: Group {
                            if isPuzzleLoading {
                                loadingView
                                    .onAppear { print("🔵 Showing loading view") }
                            } else {
                                puzzleDestination
                                    .onAppear { print("🔵 Showing puzzle destination") }
                            }
                        },
                        isActive: isShowingPuzzle
                    ) {
                        EmptyView()
                    }
                    .onAppear {
                        sessionStartTime = Date()
                        
                        // Track app session start
                        AnalyticsSessionManager.shared.logAppOpenIfNeeded()
                        AnalyticsSessionManager.shared.logSessionStartIfNeeded()
                        
                        // Track user properties for ad targeting
                        if Auth.auth().currentUser != nil {
                            AnalyticsManager.shared.setUserProperties()
                        }
                        
                        // Track key conversions
                        CampaignTracker.shared.trackKeyConversions()
                        
                        // Track home view appearance
                        AnalyticsManager.shared.trackSafely(AnalyticsEvent("home_view_appeared", parameters: [
                            "selected_tab": selectedTab.title.lowercased(),
                            "user_signed_in": Auth.auth().currentUser != nil
                        ]))
                    }

                    NavigationLink(
                        destination: completionDestination,
                        isActive: Binding(
                            get: {
                                print("🎉 Completion NavigationLink get: \(showCompletionScreen)")
                                return showCompletionScreen
                            },
                            set: { newValue in
                                print("🎉 Completion NavigationLink set: \(newValue)")
                                showCompletionScreen = newValue
                                
                                // Only reset if we're NOT starting a new active session
                                if !newValue && !isInActiveSession {
                                    print("🧹 Clearing all puzzle state from completion NavigationLink")
                                    resetPuzzleSession()
                                } else if !newValue && isInActiveSession {
                                    print("⏭️ Skipping reset - new session starting")
                                }
                            }
                        )
                    ) {
                        EmptyView()
                    }
                    
                    // Tab Content
                    Group {
                        switch selectedTab {
                        case .categories: categoriesTab
                        case .createGame: GameCreationView(selectedSection: $gameTabSection)
                            .environmentObject(gameGenerationManager)
                            .onAppear { gameGenerationManager.isOnCreateTab = true }
                            .onDisappear { gameGenerationManager.isOnCreateTab = false }
                        case .customPuzzles: customPuzzlesTab
                        case .settings: settingsTab
                        case .forYou: forYouTab
                        }
                    }
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
                    
                    
                    bottomTabBar
                }

                // Game generation completion banner
                if gameGenerationManager.showCompletionBanner {
                    VStack {
                        GameCompletionBanner(
                            onTap: {
                                gameGenerationManager.openCompletedGame()
                                selectedTab = .createGame
                            },
                            onDismiss: {
                                gameGenerationManager.dismissBanner()
                            }
                        )
                        Spacer()
                    }
                    .transition(.move(edge: .top).combined(with: .opacity))
                    .animation(.spring(response: 0.4), value: gameGenerationManager.showCompletionBanner)
                    .zIndex(100)
                }

                if showStreakDialog, let reward = dailyStreakReward {
                    DailyStreakGiftDialog(
                        streakReward: reward,
                        onDismiss: {
                            withAnimation(.easeOut(duration: 0.3)) {
                                showStreakDialog = false
                            }
                        }
                    )
                    .transition(.opacity.combined(with: .scale(scale: 0.8)))
                    .zIndex(1000)
                }
                                
                // Coin earned toast (positioned at top)
                VStack {
                    CoinEarnedToast(amount: lastCoinAmount, isShowing: $showCoinToast)
                        .padding(.top, 100)
                    Spacer()
                }
                .zIndex(999)
                
            }
            
            
            
        }.sheet(item: $currentSheet) { sheetType in
            switch sheetType {
            case .startPuzzle(let category):
                let displayInfo = getCategoryDisplayInfo(for: category)
                StartPuzzleView(
                    quizTitle: displayInfo.title,
                    puzzleType: category,
                    selectedDifficulty: $selectedDifficulty,
                    onPlayNow: { puzzleCount in
                        print("🔵 StartPuzzleView onPlayNow called for category: \(category) with \(puzzleCount) puzzles")
                        
                        // IMPORTANT: Set target and reset session BEFORE closing sheet
                        targetPuzzleCount = puzzleCount
                        completedPuzzleCount = 0
                        sessionStats = []
                        
                        // CRITICAL: Set session flag immediately for multi-puzzle sessions
                        if puzzleCount > 1 {
                            isInActiveSession = true
                            print("🚀 Set isInActiveSession = true for multi-puzzle session")
                        }
                        
                        // Close sheet first
                        currentSheet = nil
                        
                        // Clear any existing completion state
                        showCompletionScreen = false
                        fetchPuzzle(for: category, for: selectedDifficulty)
                    },
                    onBack: {
                        currentSheet = nil
                    }
                )
                .onAppear {
                    print("🔵 StartPuzzleView appeared for category: \(category)")
                    selectedDifficulty = difficultyManager.selectedDifficulty
                }
                
            case .createPuzzle:
                CreatePuzzleView()
            }
        }.sheet(isPresented: $showLimitDialog) {
            if let limitInfo = currentLimitInfo {
                LimitReachedDialog(
                    limitInfo: limitInfo,
                    currentTier: subscriptionManager.currentTier,
                    onUpgrade: { tier in
                        selectedUpgradeTier = tier
                        showLimitDialog = false
                        showUpgradeFlow = true
                        
                        // Analytics
                        AnalyticsManager.shared.trackSafely(AnalyticsEvent("upgrade_initiated", parameters: [
                            "target_tier": tier.rawValue,
                            "trigger": "limit_reached",
                            "puzzle_type": selectedCategory ?? "unknown"
                        ]))
                    },
                    onDismiss: {
                        showLimitDialog = false
                        currentLimitInfo = nil
                    }
                )
            }
        }.sheet(isPresented: $showUpgradeFlow) {
            if let tier = selectedUpgradeTier {
                RemotePaywallView(
                    context: .limitReached,
                    targetTier: tier,
                    onSuccess: {
                        showUpgradeFlow = false
                        selectedUpgradeTier = nil

                        // Clear any cached limits
                        //RegenerationLimitManager.shared.()

                        // Analytics
                        AnalyticsManager.shared.trackSafely(AnalyticsEvent("subscription_purchased", parameters: [
                            "tier": tier.rawValue,
                            "source": "limit_upgrade_flow"
                        ]))

                        // Optionally retry the puzzle fetch
                        if let category = selectedCategory {
                            fetchPuzzle(for: category, for: selectedDifficulty)
                        }
                    },
                    onCancel: {
                        showUpgradeFlow = false
                        selectedUpgradeTier = nil
                    }
                )
            }
        }.sheet(isPresented: $showRatingDialog) {
            AggressiveRatingDialog(
                onDismiss: { showRatingDialog = false },
                onCompleted: { showRatingDialog = false }
            )
        }.fullScreenCover(isPresented: $showFreeLimitPaywall) {
            RemotePaywallView(
                context: .limitReached,
                targetTier: .premium,
                onSuccess: {
                    showFreeLimitPaywall = false
                    // Retry the puzzle fetch after subscribing
                    if let category = selectedCategory {
                        fetchPuzzle(for: category, for: selectedDifficulty)
                    }
                },
                onCancel: {
                    showFreeLimitPaywall = false
                }
            )
        }.fullScreenCover(isPresented: $showAppOpenPaywall) {
            RemotePaywallView(
                context: .default,
                targetTier: .premium,
                onSuccess: {
                    showAppOpenPaywall = false
                },
                onCancel: {
                    showAppOpenPaywall = false
                }
            )
        }.sheet(isPresented: $showPuzzleGroupDetail) {
            if let group = selectedPuzzleGroup {
                PuzzleGroupDetailView(group: group) { puzzleType, groupId in
                    // Store the group ID to return to after puzzle completion
                    currentGroupId = groupId
                    
                    // Set up the puzzle session
                    selectedCategory = puzzleType
                    selectedDifficulty = difficultyManager.selectedDifficulty
                    targetPuzzleCount = 5 // Groups always have 5 puzzles
                    completedPuzzleCount = 0
                    sessionStats = []
                    showPuzzleGroupDetail = false
                    
                    // Start the puzzle
                    fetchPuzzle(for: puzzleType, for: selectedDifficulty)
                }
            }
        }.overlay {
            if showUpdateDialog, let response = updateResponse {
                SimpleUpdateDialog(
                    response: response,
                    onUpdate: handleUpdateAction,
                    onDismiss: dismissUpdateDialog
                )
                .transition(.opacity)
                .zIndex(1000)
            }
        }.onAppear {
            PuzzleQueueManager.shared.removeDuplicatesFromAllQueues()
            handleDailyStreakOnAppOpen()
            if !hasShownRatingThisSession {
                checkAndShowRatingPromptImmediately()
                hasShownRatingThisSession = true
            }
            // App-open paywall for non-premium users
            checkAppOpenPaywall()
        }.onReceive(NotificationCenter.default.publisher(for: NSNotification.Name("CoinEarned"))) { notification in
            if let amount = notification.userInfo?["amount"] as? Int {
                lastCoinAmount = amount
                withAnimation(.spring(dampingFraction: 0.8)) {
                    showCoinToast = true
                }
            }
        }.onReceive(NotificationCenter.default.publisher(for: UIApplication.didEnterBackgroundNotification)) { _ in
            AnalyticsManager.shared.trackSafely(.appBackground())
        }
        .onReceive(NotificationCenter.default.publisher(for: UIApplication.willEnterForegroundNotification)) { _ in
            AnalyticsSessionManager.shared.logAppOpenIfNeeded()
            checkAppOpenPaywall()
        }.navigationViewStyle(StackNavigationViewStyle())
        .sheet(isPresented: $authStateManager.showSignInPrompt) {
            SignInPromptSheet()
        }
        }
}

// MARK: - Sign In Prompt Sheet (for guest users)
struct SignInPromptSheet: View {
    @Environment(\.dismiss) private var dismiss
    @StateObject private var viewModel = QAPuzzleViewModel()
    @EnvironmentObject var notificationManager: NotificationManager

    var body: some View {
        NavigationView {
            VStack(spacing: 24) {
                Spacer()

                Image(systemName: "person.crop.circle.badge.questionmark")
                    .font(.system(size: 60))
                    .foregroundColor(.purple)

                Text("Sign In Required")
                    .font(.title2)
                    .fontWeight(.bold)

                Text("Sign in to access leaderboards, track your progress, and save your scores.")
                    .font(.body)
                    .foregroundColor(.secondary)
                    .multilineTextAlignment(.center)
                    .padding(.horizontal, 30)

                Spacer()

                SignInView(viewModel: viewModel)

                Spacer()
            }
            .navigationTitle("Sign In")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .navigationBarLeading) {
                    Button("Cancel") { dismiss() }
                }
            }
        }
    }
}





struct RecommendedPuzzleCard: View {
    let title: String
    let subtitle: String
    let icon: String
    let backgroundColor: Color
    let onTap: () -> Void
    
    var body: some View {
        Button(action: onTap) {
            VStack(spacing: 8) {
                Image(systemName: icon)
                    .font(.system(size: 20, weight: .medium))
                    .foregroundColor(.white)
                
                VStack(spacing: 2) {
                    Text(title)
                        .font(.system(size: 12, weight: .semibold))
                        .foregroundColor(.white)
                        .multilineTextAlignment(.center)
                        .lineLimit(1)
                    
                    Text(subtitle)
                        .font(.system(size: 9))
                        .foregroundColor(.white.opacity(0.8))
                        .multilineTextAlignment(.center)
                        .lineLimit(2)
                }
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity)
            .padding(8)
            .background(
                RoundedRectangle(cornerRadius: 10)
                    .fill(backgroundColor)
                    .shadow(color: .black.opacity(0.15), radius: 3, x: 0, y: 2)
            )
        }
        .buttonStyle(PlainButtonStyle())
    }
}

// MARK: - Helper Functions

private func getRecommendedCategories(basedOn recentTypes: [String]) -> [CategoryData] {
    // If user has recent activity, recommend similar categories
    if !recentTypes.isEmpty {
        let recentCategories = CategoryData.allCategories.filter { category in
            recentTypes.contains(category.category)
        }
        
        // Get tags from recent categories to find similar ones
        var recommendedTags: Set<CategoryFilter> = []
        for category in recentCategories {
            recommendedTags.formUnion(Set(category.tags))
        }
        
        // Find categories with similar tags, excluding recent ones
        let recommendations = CategoryData.allCategories.filter { category in
            !recentTypes.contains(category.category) &&
            !Set(category.tags).isDisjoint(with: recommendedTags)
        }
        
        return Array(recommendations.prefix(6)) // Limit to 6 recommendations
    } else {
        // For new users, recommend popular/beginner-friendly categories
        let beginnerCategories = [
            CategoryData.allCategories.first { $0.category == "math" },
            CategoryData.allCategories.first { $0.category == "trivia" },
            CategoryData.allCategories.first { $0.category == "anagram" },
            CategoryData.allCategories.first { $0.category == "average" },
            CategoryData.allCategories.first { $0.category == "memorystory" },
            CategoryData.allCategories.first { $0.category == "synonyms" }
        ].compactMap { $0 }
        
        return beginnerCategories
    }
}



extension PuzzleNavigationType: CustomStringConvertible {
    var description: String {
        switch self {
        case .none: return "none"
        case .qa: return "qa"
        case .multipleChoice: return "multipleChoice"
        case .average: return "average"
        case .division: return "division"
        case .estimation: return "estimation"
        case .percentage: return "percentage"
        case .discounts: return "discounts"
        case .purchasing: return "purchasing"
        case .conversion: return "conversion"
        case .anagram: return "anagram"
        case .swipeWord: return "swipeWord"
        case .tipBubble: return "tipBubble"
        case .subtraction: return "subtraction"
        case .crossword: return "crossword"
        case .memoryRetention: return "memoryRetention"
        case .wordPrefix: return "wordPrefix"
        case .antonym: return "antonym"
        case .synonym: return "synonym"
        case .memoryStory: return "memoryStory"
        case .memorySequencing: return "memorySequencing"
        case .memorySquares: return "memorySquares"
        case .memoryPreviousPair: return "memoryPreviousPair"
        case .memoryPreviousSingle: return "memoryPreviousSingle"
        case .pinballDeflector: return "pinballDeflector"
        case.colorshapematching: return "colorshapematching"
        case.imagevortex: return "imagevortex"
        case .mathComparison: return "mathComparison"
        case .numberSequence: return "numberSequence"
        case .numbersum: return "numbersum"
        case .symbolSwipe: return "symbolSwipe"
        case .uniqueObject: return "uniqueObject"
        case .wordSearch: return "wordSearch"
        case .wordSnake: return "wordSnake"
        case .findObject: return "findObject"
        case .contextswitch: return "contextswitch"
        case .mathCrossword: return "mathcrossword"
        case .dualTask: return "dualtask"
        case .colorTextMatching: return "colortextmatching"
        case .geographyCities: return "geographycities"
        case .geographyCountries: return "geographycountries"
        case .crypto: return "crypto"
        case .imagePuzzle: return "imagePuzzle"
        case .whichIsReal: return "whichIsReal"
        case .progressiveReveal: return "progressiveReveal"
        case .mathExpression: return "mathExpression"
        case .musicTrack: return "musicTrack"
        case .triangleDotMemory: return "triangledotmemory"
        case .symmetry: return "symmetry"
        case .imageQuestion: return "imageQuestion"
        case .oddoneout: return "oddoneout"
        case .flowpuzzle: return "flowpuzzle"
        case .sentencetransitions: return "sentencetransitions"
        case .letterSet: return "letterset"
        case .waldoPuzzle: return "waldoPuzzle"
        }
    }
}


func generateCryptoPuzzleData(text: String, difficulty: String, answer: String) -> [String: Any] {
    let cleanText = text.uppercased().filter { $0.isLetter || $0.isWhitespace }

    // Step 1: Create frequency map for all letters (matches Android createFrequencyMap)
    let frequencyMap = createFrequencyMap(text: cleanText)
    
    // Step 2: Determine removal percentage based on difficulty (matches Android logic)
    let removalPercentage: Double
    switch difficulty.lowercased() {
    case "easy": removalPercentage = 0.5    // Remove 50% of high-frequency chars
    case "medium": removalPercentage = 0.6  // Remove 60% of high-frequency chars
    case "hard": removalPercentage = 0.7    // Remove 70% of high-frequency chars
    case "expert": removalPercentage = 0.8  // Remove 80% of high-frequency chars
    default: removalPercentage = 0.6
    }
    
    // Step 3: Select high-frequency characters to hide (matches Android selectCharactersToHide)
    let hiddenLetters = selectCharactersToHide(frequencyMap: frequencyMap, removalPercentage: removalPercentage)
    
    // Step 4: Create number mapping for hidden letters (matches Android createNumberMapping)
    let numberMapping = createNumberMapping(hiddenLetters: hiddenLetters)
    
    // Step 5: Determine revealed letters (matches Android selectRevealedLetters)
    let revealedLetters = selectRevealedLetters(hiddenLetters: hiddenLetters, difficulty: difficulty)
    
    // Calculate time limit
    let timeLimit = calculateCryptoTimeLimit(for: cleanText, difficulty: difficulty)
    
    // Find target word from answer
    let targetWord = answer.isEmpty ? nil : answer
    
    print("🔐 CRYPTO: Generated puzzle with Android logic:")
    print("📊 CRYPTO: Hidden letters: \(hiddenLetters.count)/\(frequencyMap.count) (\(Int(Double(hiddenLetters.count)/Double(frequencyMap.count)*100))%)")
    print("📊 CRYPTO: Revealed hints: \(revealedLetters.count)")
    print("📊 CRYPTO: Target word: \(targetWord ?? "none")")
    
    return [
        "originalText": cleanText,
        "numberMapping": numberMapping,
        "revealedLetters": Array(revealedLetters).map { String($0) },
        "hiddenLetters": Array(hiddenLetters).map { String($0) },
        "targetWord": targetWord ?? "",
        "difficulty": difficulty,
        "timeLimit": timeLimit,
        "instructions": "Decode the hidden message by figuring out which number represents which letter."
    ]
}

// Matches Android createFrequencyMap
private func createFrequencyMap(text: String) -> [Character: Int] {
    var frequencyMap: [Character: Int] = [:]
    
    for char in text {
        if char.isLetter {
            let upperChar = char.uppercased().first!
            frequencyMap[upperChar] = (frequencyMap[upperChar] ?? 0) + 1
        }
    }
    
    return frequencyMap
}

// Matches Android selectCharactersToHide
private func selectCharactersToHide(frequencyMap: [Character: Int], removalPercentage: Double) -> Set<Character> {
    // Sort characters by frequency (descending)
    let sortedByFrequency = frequencyMap.sorted { $0.value > $1.value }.map { $0.key }
    
    var hiddenLetters = Set<Character>()
    
    // Always include some common letters to make it challenging (matches Android guaranteedHidden)
    let guaranteedHidden: [Character] = ["E", "T", "A", "O", "I", "N", "S", "H", "R"]
    
    // Add guaranteed letters that exist in the text
    for letter in guaranteedHidden {
        if frequencyMap.keys.contains(letter) {
            hiddenLetters.insert(letter)
        }
    }
    
    // Calculate how many more characters to hide
    let totalUniqueLetters = frequencyMap.count
    let targetHiddenCount = Int(Double(totalUniqueLetters) * removalPercentage)
    let remainingToHide = max(0, targetHiddenCount - hiddenLetters.count)
    
    // Add additional high-frequency characters
    let additionalLetters = Array(sortedByFrequency.prefix(remainingToHide + hiddenLetters.count))
    for letter in additionalLetters {
        hiddenLetters.insert(letter)
    }
    
    // Randomize: sometimes remove ALL occurrences, sometimes leave some (matches Android randomization)
    var finalHiddenLetters = Set<Character>()
    for letter in hiddenLetters {
        let randomChance = Double.random(in: 0...1)
        switch randomChance {
        case 0..<0.7:
            // 70% chance to hide completely
            finalHiddenLetters.insert(letter)
        case 0.7..<0.9:
            // 20% chance to hide only if frequency > 2
            if (frequencyMap[letter] ?? 0) > 2 {
                finalHiddenLetters.insert(letter)
            }
        default:
            // 10% chance to not hide this letter at all
            break
        }
    }
    
    // Ensure we have at least some hidden letters (matches Android minimum check)
    if finalHiddenLetters.count < 3 {
        for letter in Array(sortedByFrequency.prefix(5)) {
            finalHiddenLetters.insert(letter)
        }
    }
    
    return finalHiddenLetters
}

// Matches Android createNumberMapping
private func createNumberMapping(hiddenLetters: Set<Character>) -> [String: Int] {
    var numberMapping: [String: Int] = [:]
    var availableNumbers = Array(1...26)
    availableNumbers.shuffle() // Randomize number assignments
    
    let hiddenLettersArray = Array(hiddenLetters)
    for (index, letter) in hiddenLettersArray.enumerated() {
        if index < availableNumbers.count {
            numberMapping[String(letter)] = availableNumbers[index]
        }
    }
    
    return numberMapping
}

// Matches Android selectRevealedLetters
private func selectRevealedLetters(hiddenLetters: Set<Character>, difficulty: String) -> Set<Character> {
    let revealPercentage: Double
    switch difficulty.lowercased() {
    case "easy": revealPercentage = 0.4    // Reveal 40% of hidden letters
    case "medium": revealPercentage = 0.25 // Reveal 25% of hidden letters
    case "hard": revealPercentage = 0.15   // Reveal 15% of hidden letters
    case "expert": revealPercentage = 0.1  // Reveal 10% of hidden letters
    default: revealPercentage = 0.25
    }
    
    let revealCount = Int(Double(hiddenLetters.count) * revealPercentage)
    if revealCount == 0 {
        return Set<Character>()
    }
    
    // Prioritize revealing less common letters to give strategic hints (matches Android)
    let sortedHiddenLetters = Array(hiddenLetters).shuffled()
    
    return Set(Array(sortedHiddenLetters.prefix(revealCount)))
}

private func calculateCryptoTimeLimit(for text: String, difficulty: String) -> Int {
    let baseTimePerWord = 30
    let wordCount = text.split(separator: " ").count
    let letterCount = text.filter { $0.isLetter }.count
    
    let difficultyMultiplier: Double
    switch difficulty.lowercased() {
    case "easy": difficultyMultiplier = 1.5
    case "medium": difficultyMultiplier = 1.2
    case "hard": difficultyMultiplier = 1.0
    case "expert": difficultyMultiplier = 0.8
    default: difficultyMultiplier = 1.2
    }
    
    let calculatedTime = Int(Double(wordCount * baseTimePerWord + letterCount * 2) * difficultyMultiplier)
    return max(calculatedTime, 180) // Minimum 3 minutes
}
