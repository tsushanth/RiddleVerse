//
//  SubscriptionManager.swift
//  PuzzleForge
//
//  Created for handling subscription tiers and purchases with puzzle limits
//

import Foundation
import RevenueCat
import FirebaseAuth
import Combine
import SwiftUI

// MARK: - Subscription Tier Enum
enum SubscriptionTier: String, CaseIterable, Codable {
    case free = "free"
    case premium = "premium"
    case unlimited = "unlimited"

    var displayName: String {
        switch self {
        case .free: return "Free"
        case .premium: return "Premium"
        case .unlimited: return "Pro"
        }
    }

    var dailyLimit: Int {
        switch self {
        case .free: return 3  // Effectively unused; lifetime limit governs free tier
        case .premium: return 50
        case .unlimited: return -1 // Unlimited
        }
    }

    var monthlyLimit: Int {
        switch self {
        case .free: return 3  // Effectively unused; lifetime limit governs free tier
        case .premium: return 500
        case .unlimited: return -1 // Unlimited
        }
    }

    var price: String {
        switch self {
        case .free: return "Free"
        case .premium: return "$4.99/month"
        case .unlimited: return "$9.99/month"
        }
    }

    var productId: String {
        switch self {
        case .free: return ""
        case .premium: return "com.puzzleforge.premium.monthly"
        case .unlimited: return "com.puzzleforge.unlimited.monthly"
        }
    }

    // Monthly coins granted — intentionally above equivalent coin-pack cost
    // so subscribers always feel they're getting more than they paid for.
    var monthlyCoins: Int {
        switch self {
        case .free: return 0
        case .premium: return 150    // Small pack = 100 coins; subscribers get 50% more free
        case .unlimited: return 700  // Medium pack = 500 coins; subscribers get 40% more free
        }
    }

    // One-time signup bonus on first purchase (credited in addition to monthlyCoins)
    var signupBonus: Int {
        switch self {
        case .free: return 0
        case .premium: return 150    // First month effectively 300 coins total
        case .unlimited: return 500  // First month effectively 1,200 coins total
        }
    }

    var benefits: [String] {
        switch self {
        case .free:
            return [
                "Access to all puzzle types",
                "3 free puzzles to try",
                "Ad-supported experience",
                "Earn coins through gameplay"
            ]
        case .premium:
            return [
                "Ad-free experience",
                "150 free coins every month + 150 bonus on signup",
                "50 puzzle generations per day",
                "Priority puzzle generation",
                "Custom puzzle themes"
            ]
        case .unlimited:
            return [
                "Ad-free experience",
                "700 free coins every month + 500 bonus on signup",
                "Unlimited puzzle generations",
                "Priority support",
                "Early access to new features",
                "Premium puzzle themes"
            ]
        }
    }
}

// MARK: - Purchase State
enum PurchaseState: Equatable {
    case idle
    case purchasing
    case success
    case failed(Error)
    case cancelled

    static func == (lhs: PurchaseState, rhs: PurchaseState) -> Bool {
        switch (lhs, rhs) {
        case (.idle, .idle), (.purchasing, .purchasing), (.success, .success), (.cancelled, .cancelled):
            return true
        case (.failed(let lhsError), .failed(let rhsError)):
            return lhsError.localizedDescription == rhsError.localizedDescription
        default:
            return false
        }
    }
}

// MARK: - Subscription Manager (RevenueCat)
@MainActor
class SubscriptionManager: ObservableObject {
    static let shared = SubscriptionManager()

    @Published var currentTier: SubscriptionTier = .free
    @Published var purchaseState: PurchaseState = .idle
    @Published var availablePackages: [RevenueCat.Package] = []
    @Published var hasReachedDailyLimit = false
    @Published var hasReachedMonthlyLimit = false
    @Published var isLoadingProducts = false
    @Published var productsLoadError: String?

    private let userDefaults = UserDefaults.standard
    private let calendar = Calendar.current

    init() {
        loadCurrentSubscriptionStatus()
        cleanupOldData()
        Task {
            // Small delay to ensure proper initialization
            try? await Task.sleep(nanoseconds: 500_000_000) // 0.5 seconds
            await loadProducts()
            await refreshCustomerInfo()
        }
    }

    // MARK: - Lifetime Free Puzzle Limit (Hard Paywall)

    static let freeLifetimeLimit = 3

    func canPlayFreePuzzle() -> Bool {
        guard !hasActiveSubscription() else { return true }
        let played = UserDefaults.standard.integer(forKey: "com.riddleverse.totalFreePuzzles")
        return played < Self.freeLifetimeLimit
    }

    func recordFreePuzzlePlayed() {
        let count = UserDefaults.standard.integer(forKey: "com.riddleverse.totalFreePuzzles") + 1
        UserDefaults.standard.set(count, forKey: "com.riddleverse.totalFreePuzzles")
    }

    func totalFreePuzzlesPlayed() -> Int {
        return UserDefaults.standard.integer(forKey: "com.riddleverse.totalFreePuzzles")
    }

    // MARK: - Puzzle Limit Management

    private func getTodayKey() -> String {
        let dateFormatter = DateFormatter()
        dateFormatter.dateFormat = "yyyy-MM-dd"
        return dateFormatter.string(from: Date())
    }

    private func getMonthKey() -> String {
        let dateFormatter = DateFormatter()
        dateFormatter.dateFormat = "yyyy-MM"
        return dateFormatter.string(from: Date())
    }

    private func getDailyUsageKey(for puzzleType: String) -> String {
        return "daily_usage_\(puzzleType)_\(getTodayKey())"
    }

    private func getMonthlyUsageKey(for puzzleType: String) -> String {
        return "monthly_usage_\(puzzleType)_\(getMonthKey())"
    }

    func getDailyUsage(for puzzleType: String) -> Int {
        return userDefaults.integer(forKey: getDailyUsageKey(for: puzzleType))
    }

    func getMonthlyUsage(for puzzleType: String) -> Int {
        return userDefaults.integer(forKey: getMonthlyUsageKey(for: puzzleType))
    }

    func getTotalDailyUsage() -> Int {
        let puzzleTypes = getAllTrackedPuzzleTypes()
        return puzzleTypes.reduce(0) { total, type in
            total + getDailyUsage(for: type)
        }
    }

    func getTotalMonthlyUsage() -> Int {
        let puzzleTypes = getAllTrackedPuzzleTypes()
        return puzzleTypes.reduce(0) { total, type in
            total + getMonthlyUsage(for: type)
        }
    }

    func canFetchPuzzle(type: String) -> Bool {
        // Unlimited users have no limits
        if currentTier == .unlimited {
            return true
        }

        let dailyUsage = getDailyUsage(for: type)
        let monthlyUsage = getMonthlyUsage(for: type)

        let dailyLimitReached = currentTier.dailyLimit != -1 && dailyUsage >= currentTier.dailyLimit
        let monthlyLimitReached = currentTier.monthlyLimit != -1 && monthlyUsage >= currentTier.monthlyLimit

        return !dailyLimitReached && !monthlyLimitReached
    }

    func getRemainingGenerations(puzzleType: String) -> (daily: Int, monthly: Int) {
        if currentTier == .unlimited {
            return (daily: -1, monthly: -1) // Unlimited
        }

        let dailyUsage = getDailyUsage(for: puzzleType)
        let monthlyUsage = getMonthlyUsage(for: puzzleType)

        let dailyRemaining = currentTier.dailyLimit == -1 ? -1 : max(0, currentTier.dailyLimit - dailyUsage)
        let monthlyRemaining = currentTier.monthlyLimit == -1 ? -1 : max(0, currentTier.monthlyLimit - monthlyUsage)

        return (daily: dailyRemaining, monthly: monthlyRemaining)
    }

    func shouldShowLimitWarning(puzzleType: String) -> Bool {
        if currentTier == .unlimited {
            return false
        }

        let remaining = getRemainingGenerations(puzzleType: puzzleType)

        // Show warning when user has 3 or fewer generations left
        let dailyLowThreshold = remaining.daily != -1 && remaining.daily <= 3
        let monthlyLowThreshold = remaining.monthly != -1 && remaining.monthly <= 3

        return dailyLowThreshold || monthlyLowThreshold
    }

    func recordPuzzleFetch(for puzzleType: String) {
        let dailyKey = getDailyUsageKey(for: puzzleType)
        let monthlyKey = getMonthlyUsageKey(for: puzzleType)

        let currentDaily = userDefaults.integer(forKey: dailyKey)
        let currentMonthly = userDefaults.integer(forKey: monthlyKey)

        userDefaults.set(currentDaily + 1, forKey: dailyKey)
        userDefaults.set(currentMonthly + 1, forKey: monthlyKey)

        // Update tracked puzzle types
        addTrackedPuzzleType(puzzleType)

        // Update published properties
        DispatchQueue.main.async {
            self.hasReachedDailyLimit = self.getTotalDailyUsage() >= self.currentTier.dailyLimit
            self.hasReachedMonthlyLimit = self.getTotalMonthlyUsage() >= self.currentTier.monthlyLimit
        }

        print("LIMIT: Recorded fetch for \(puzzleType)")
        print("LIMIT: Daily: \(currentDaily + 1)/\(currentTier.dailyLimit)")
        print("LIMIT: Monthly: \(currentMonthly + 1)/\(currentTier.monthlyLimit)")
    }

    func getLimitInfo(for puzzleType: String) -> LimitInfo? {
        if !canFetchPuzzle(type: puzzleType) {
            let remaining = getRemainingGenerations(puzzleType: puzzleType)
            let dailyUsage = getDailyUsage(for: puzzleType)
            let monthlyUsage = getMonthlyUsage(for: puzzleType)

            let resetTime = getNextResetTime()
            let message = generateLimitMessage(puzzleType: puzzleType, remaining: remaining)
            let reason = determineLimitReason(remaining: remaining)

            return LimitInfo(
                reason: reason,
                dailyUsed: dailyUsage,
                dailyLimit: currentTier.dailyLimit,
                monthlyUsed: monthlyUsage,
                monthlyLimit: currentTier.monthlyLimit,
                resetTime: resetTime,
                upgradeUrl: "https://puzzleforge.app/upgrade",
                message: message
            )
        }
        return nil
    }

    private func getAllTrackedPuzzleTypes() -> [String] {
        return userDefaults.stringArray(forKey: "tracked_puzzle_types") ?? []
    }

    private func addTrackedPuzzleType(_ puzzleType: String) {
        var tracked = getAllTrackedPuzzleTypes()
        if !tracked.contains(puzzleType) {
            tracked.append(puzzleType)
            userDefaults.set(tracked, forKey: "tracked_puzzle_types")
        }
    }

    func clearAllLimits() {
        let puzzleTypes = getAllTrackedPuzzleTypes()
        let today = getTodayKey()
        let month = getMonthKey()

        for puzzleType in puzzleTypes {
            userDefaults.removeObject(forKey: "daily_usage_\(puzzleType)_\(today)")
            userDefaults.removeObject(forKey: "monthly_usage_\(puzzleType)_\(month)")
        }

        DispatchQueue.main.async {
            self.hasReachedDailyLimit = false
            self.hasReachedMonthlyLimit = false
        }

        print("LIMIT: Cleared all limits")
    }

    private func cleanupOldData() {
        // Remove data older than 60 days to prevent UserDefaults bloat
        let cutoffDate = calendar.date(byAdding: .day, value: -60, to: Date()) ?? Date()
        let dateFormatter = DateFormatter()
        dateFormatter.dateFormat = "yyyy-MM-dd"

        let keys = Array(userDefaults.dictionaryRepresentation().keys)
        for key in keys {
            if key.contains("daily_usage_") || key.contains("monthly_usage_") {
                // Extract date from key and remove if too old
                let components = key.split(separator: "_")
                if let dateString = components.last,
                   let date = dateFormatter.date(from: String(dateString)),
                   date < cutoffDate {
                    userDefaults.removeObject(forKey: key)
                }
            }
        }
    }

    private func getNextResetTime() -> String {
        let tomorrow = calendar.date(byAdding: .day, value: 1, to: Date())!
        let nextMidnight = calendar.startOfDay(for: tomorrow)

        let formatter = ISO8601DateFormatter()
        formatter.formatOptions = [.withInternetDateTime, .withFractionalSeconds]
        return formatter.string(from: nextMidnight)
    }

    private func determineLimitReason(remaining: (daily: Int, monthly: Int)) -> String {
        let dailyLimited = remaining.daily == 0
        let monthlyLimited = remaining.monthly == 0

        if dailyLimited && monthlyLimited {
            return "daily_and_monthly_limit_exceeded"
        } else if dailyLimited {
            return "daily_limit_exceeded"
        } else if monthlyLimited {
            return "monthly_limit_exceeded"
        } else {
            return "limit_approaching"
        }
    }

    private func generateLimitMessage(puzzleType: String, remaining: (daily: Int, monthly: Int)) -> String {
        if currentTier == .unlimited {
            return "Enjoy unlimited puzzle generation!"
        }

        let dailyLimited = remaining.daily == 0
        let monthlyLimited = remaining.monthly == 0

        if dailyLimited && monthlyLimited {
            return "You've reached both daily and monthly limits for \(puzzleType). Upgrade to continue!"
        } else if dailyLimited {
            return "Daily limit reached for \(puzzleType). Try again tomorrow or upgrade for more!"
        } else if monthlyLimited {
            return "Monthly limit reached for \(puzzleType). Upgrade for unlimited access!"
        } else if remaining.daily <= 3 {
            return "Only \(remaining.daily) \(puzzleType) puzzles left today. Consider upgrading!"
        } else {
            return "\(remaining.daily) \(puzzleType) puzzles remaining today."
        }
    }

    // MARK: - Subscription Management (RevenueCat)

    func loadProducts() async {
        print("REVENUECAT: Starting to load offerings...")

        isLoadingProducts = true
        productsLoadError = nil

        do {
            let offerings = try await Purchases.shared.offerings()
            let packages = offerings.current?.availablePackages ?? []

            print("REVENUECAT: Received \(packages.count) packages")
            for pkg in packages {
                print("REVENUECAT: - \(pkg.identifier): \(pkg.storeProduct.localizedTitle) - \(pkg.localizedPriceString)")
            }

            self.availablePackages = packages
            self.isLoadingProducts = false
            print("REVENUECAT: Successfully set \(self.availablePackages.count) packages")
        } catch {
            print("REVENUECAT: Error loading offerings: \(error)")
            self.productsLoadError = error.localizedDescription
            self.isLoadingProducts = false
        }
    }

    func purchasePackage(_ package: RevenueCat.Package) async {
        guard purchaseState != .purchasing else { return }

        let productId = package.storeProduct.productIdentifier
        let tierFromProduct = getTierFromProductId(productId)

        AnalyticsManager.shared.trackSafely(AnalyticsEvent("subscription_purchase_attempted", parameters: [
            "tier": tierFromProduct.rawValue,
            "product_id": productId,
            "price": package.localizedPriceString,
            "current_tier": currentTier.rawValue
        ]))

        purchaseState = .purchasing

        do {
            let result = try await Purchases.shared.purchase(package: package)

            if result.userCancelled {
                AnalyticsManager.shared.trackSafely(AnalyticsEvent("subscription_purchase_cancelled", parameters: [
                    "tier": tierFromProduct.rawValue,
                    "product_id": productId,
                    "current_tier": currentTier.rawValue
                ]))
                purchaseState = .cancelled
            } else {
                AnalyticsManager.shared.trackSafely(AnalyticsEvent("subscription_purchased", parameters: [
                    "tier": tierFromProduct.rawValue,
                    "product_id": productId,
                    "price": package.localizedPriceString,
                    "previous_tier": currentTier.rawValue
                ]))

                updateTierFromCustomerInfo(result.customerInfo)
                purchaseState = .success

                await notifyBackendOfPurchase(productId: productId, tier: tierFromProduct)
            }
        } catch {
            AnalyticsManager.shared.trackSafely(AnalyticsEvent("subscription_purchase_failed", parameters: [
                "tier": tierFromProduct.rawValue,
                "product_id": productId,
                "error": error.localizedDescription,
                "current_tier": currentTier.rawValue
            ]))
            print("Purchase failed: \(error)")
            purchaseState = .failed(error)
        }
    }

    func restorePurchases() async {
        do {
            let customerInfo = try await Purchases.shared.restorePurchases()
            updateTierFromCustomerInfo(customerInfo)
            print("Purchases restored successfully")
        } catch {
            print("Failed to restore purchases: \(error)")
            purchaseState = .failed(error)
        }
    }

    func checkSubscriptionStatus() async {
        await refreshCustomerInfo()
    }

    // MARK: - Private RevenueCat Methods

    private func refreshCustomerInfo() async {
        do {
            let customerInfo = try await Purchases.shared.customerInfo()
            updateTierFromCustomerInfo(customerInfo)
        } catch {
            print("Failed to get customer info: \(error)")
        }
    }

    func updateTierFromCustomerInfo(_ customerInfo: CustomerInfo) {
        let premiumEntitlement = customerInfo.entitlements["premium"]
        let oldTier = currentTier

        if premiumEntitlement?.isActive == true {
            let productId = premiumEntitlement?.productIdentifier ?? ""
            currentTier = getTierFromProductId(productId)
        } else {
            currentTier = .free
        }

        UserDefaults.standard.set(currentTier.rawValue, forKey: "current_subscription_tier")
        print("Updated subscription tier: \(currentTier)")

        // Clear limits when upgrading
        if currentTier.dailyLimit > oldTier.dailyLimit || currentTier == .unlimited {
            clearAllLimits()
        }
    }

    private func loadCurrentSubscriptionStatus() {
        // Load cached tier
        if let savedTier = UserDefaults.standard.string(forKey: "current_subscription_tier"),
           let tier = SubscriptionTier(rawValue: savedTier) {
            currentTier = tier
        }
    }

    func getTierFromProductId(_ productId: String) -> SubscriptionTier {
        if productId.contains("unlimited") {
            return .unlimited
        } else if productId.contains("premium") {
            return .premium
        } else {
            return .free
        }
    }

    func notifyBackendOfPurchase(productId: String, tier: SubscriptionTier) async {
        guard let user = Auth.auth().currentUser,
              let email = user.email else {
            print("No authenticated user for backend notification")
            return
        }

        guard let url = URL(string: "https://puzzleverseai.com/api/subscription-purchase") else {
            print("Invalid backend URL")
            return
        }

        var request = URLRequest(url: url)
        request.httpMethod = "POST"
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")

        let firstPurchaseKey = "first_purchase_\(productId)"
        let isFirstPurchase = !UserDefaults.standard.bool(forKey: firstPurchaseKey)
        if isFirstPurchase {
            UserDefaults.standard.set(true, forKey: firstPurchaseKey)
        }

        let purchaseData: [String: Any] = [
            "userId": user.uid,
            "email": email,
            "productId": productId,
            "tier": tier.rawValue,
            "monthlyCoins": tier.monthlyCoins,
            "signupBonus": isFirstPurchase ? tier.signupBonus : 0
        ]

        do {
            request.httpBody = try JSONSerialization.data(withJSONObject: purchaseData)

            let (data, response) = try await URLSession.shared.data(for: request)

            if let httpResponse = response as? HTTPURLResponse,
               httpResponse.statusCode == 200 {
                print("Successfully notified backend of purchase")
                // Refresh coin balance after subscription coins are credited
                if let json = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
                   let coinsGranted = json["coinsGranted"] as? Int, coinsGranted > 0 {
                    CoinManager.shared.fetchBalance()
                }
            } else {
                print("Backend notification failed")
            }
        } catch {
            print("Failed to notify backend: \(error)")
        }
    }

    // MARK: - Utility Methods

    func getPackageForTier(_ tier: SubscriptionTier) -> RevenueCat.Package? {
        // Match by RevenueCat package identifier (e.g. $rc_monthly, pro_monthly)
        let packageKey: String
        switch tier {
        case .premium:
            packageKey = "$rc_monthly"
        case .unlimited:
            packageKey = "pro_monthly"
        case .free:
            return nil
        }
        return availablePackages.first { $0.identifier == packageKey }
    }

    /// Fetch packages for a specific offering by identifier (for manual offering selection).
    func loadOffering(identifier: String) async -> [RevenueCat.Package]? {
        do {
            let offerings = try await Purchases.shared.offerings()
            return offerings.offering(identifier: identifier)?.availablePackages
        } catch {
            print("REVENUECAT: Failed to load offering \(identifier): \(error)")
            return nil
        }
    }

    func hasActiveSubscription() -> Bool {
        return currentTier != .free
    }

    func canGeneratePuzzles(puzzleType: String) -> Bool {
        return canFetchPuzzle(type: puzzleType)
    }
}

// MARK: - Subscription Errors
enum SubscriptionError: LocalizedError {
    case verificationFailed
    case unknownResult
    case networkError
    case invalidProduct

    var errorDescription: String? {
        switch self {
        case .verificationFailed:
            return "Could not verify purchase"
        case .unknownResult:
            return "Unknown purchase result"
        case .networkError:
            return "Network error during purchase"
        case .invalidProduct:
            return "Invalid product selected"
        }
    }
}

// MARK: - Utility Functions
func formatResetTime(_ resetTime: String) -> String {
    let formatter = ISO8601DateFormatter()
    formatter.formatOptions = [.withInternetDateTime, .withFractionalSeconds]

    guard let resetDate = formatter.date(from: resetTime) else {
        return "later"
    }

    let now = Date()
    let duration = resetDate.timeIntervalSince(now)

    guard duration > 0 else {
        return "now"
    }

    let days = Int(duration / (24 * 60 * 60))
    let hours = Int(duration / (60 * 60))
    let minutes = Int(duration / 60)

    switch duration {
    case let d where d >= 24 * 60 * 60:
        return days == 1 ? "in 1 day" : "in \(days) days"
    case let h where h >= 60 * 60:
        return hours == 1 ? "in 1 hour" : "in \(hours) hours"
    case let m where m >= 60:
        return minutes == 1 ? "in 1 minute" : "in \(minutes) minutes"
    default:
        return "soon"
    }
}
