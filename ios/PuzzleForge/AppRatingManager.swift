//
//  AppRatingManager.swift
//  PuzzleForge
//
//  Smart rating manager that prompts at the right moment
//

import SwiftUI
import StoreKit
import FirebaseAuth
import Foundation

// MARK: - Smart Rating Manager
class AppRatingManager: ObservableObject {
    static let shared = AppRatingManager()

    private let userDefaults = UserDefaults.standard

    private struct Constants {
        static let keyHasRated = "has_rated_app"
        static let keyDismissCount = "rating_dismiss_count"
        static let keyLastDismissalDate = "last_dismissal_date"
        static let keyLastPromptDate = "last_rating_prompt_date"
        static let keyLifetimeGamesAtPrompt = "lifetime_games_at_last_prompt"
        static let keyCoinRewardGiven = "rating_coin_reward_given"
        static let maxDismissals = 3
        static let cooldownDays = 14 // 2 weeks after max dismissals
        static let minGamesBeforePrompt = 3 // Need at least 3 games played
        static let gamesBetweeenPrompts = 5 // Wait 5 more games between prompts
        static let coinReward = 100
    }

    private init() {}

    /// Check if we should show rating prompt based on engagement
    func shouldShowRatingPrompt(totalGamesPlayed: Int) -> Bool {
        // Never show again if user has rated
        if userDefaults.bool(forKey: Constants.keyHasRated) {
            return false
        }

        // Need minimum games played
        if totalGamesPlayed < Constants.minGamesBeforePrompt {
            return false
        }

        // Check dismissal count and cooldown
        let dismissCount = userDefaults.integer(forKey: Constants.keyDismissCount)
        if dismissCount >= Constants.maxDismissals {
            if let lastDismissal = userDefaults.object(forKey: Constants.keyLastDismissalDate) as? Date {
                let daysSinceDismissal = Calendar.current.dateComponents([.day], from: lastDismissal, to: Date()).day ?? 0
                if daysSinceDismissal < Constants.cooldownDays {
                    return false
                }
                // Reset dismiss count after cooldown
                userDefaults.set(0, forKey: Constants.keyDismissCount)
            }
        }

        // Don't prompt too frequently — wait for more games between prompts
        let gamesAtLastPrompt = userDefaults.integer(forKey: Constants.keyLifetimeGamesAtPrompt)
        if gamesAtLastPrompt > 0 && totalGamesPlayed - gamesAtLastPrompt < Constants.gamesBetweeenPrompts {
            return false
        }

        // Don't prompt more than once per day
        if let lastPrompt = userDefaults.object(forKey: Constants.keyLastPromptDate) as? Date {
            let hoursSincePrompt = Calendar.current.dateComponents([.hour], from: lastPrompt, to: Date()).hour ?? 0
            if hoursSincePrompt < 24 {
                return false
            }
        }

        return true
    }

    /// Legacy method for backward compatibility
    func shouldShowRatingPrompt() -> Bool {
        return shouldShowRatingPrompt(totalGamesPlayed: Constants.minGamesBeforePrompt)
    }

    func markPromptShown(totalGamesPlayed: Int) {
        userDefaults.set(Date(), forKey: Constants.keyLastPromptDate)
        userDefaults.set(totalGamesPlayed, forKey: Constants.keyLifetimeGamesAtPrompt)
    }

    func markAsRated() {
        userDefaults.set(true, forKey: Constants.keyHasRated)
        print("User marked as rated")

        AnalyticsManager.shared.trackConversion(
            AnalyticsEvent("app_rating_completed", parameters: [
                "rating_source": "smart_prompt"
            ]),
            value: 25.0
        )
    }

    func markAsDismissed() {
        let currentCount = userDefaults.integer(forKey: Constants.keyDismissCount)
        userDefaults.set(currentCount + 1, forKey: Constants.keyDismissCount)
        userDefaults.set(Date(), forKey: Constants.keyLastDismissalDate)

        AnalyticsManager.shared.track(AnalyticsEvent("rating_prompt_dismissed", parameters: [
            "dismiss_count": currentCount + 1
        ]))
    }

    func hasUserRated() -> Bool {
        return userDefaults.bool(forKey: Constants.keyHasRated)
    }

    func hasCoinRewardBeenGiven() -> Bool {
        return userDefaults.bool(forKey: Constants.keyCoinRewardGiven)
    }

    func markCoinRewardGiven() {
        userDefaults.set(true, forKey: Constants.keyCoinRewardGiven)
    }

    static var coinRewardAmount: Int { Constants.coinReward }

    func resetRatingState() {
        userDefaults.removeObject(forKey: Constants.keyHasRated)
        userDefaults.removeObject(forKey: Constants.keyDismissCount)
        userDefaults.removeObject(forKey: Constants.keyLastDismissalDate)
        userDefaults.removeObject(forKey: Constants.keyLastPromptDate)
        userDefaults.removeObject(forKey: Constants.keyLifetimeGamesAtPrompt)
        userDefaults.removeObject(forKey: Constants.keyCoinRewardGiven)
    }
}

// MARK: - Rating Data Models
struct RatingFeedback: Codable {
    let userId: String
    let rating: Int
    let review: String?
    let timestamp: TimeInterval
    let appVersion: String
    let source: String
}

// MARK: - Network Helper
class RatingNetworkHelper {
    static func sendRating(_ rating: RatingFeedback, completion: @escaping (Bool) -> Void) {
        guard let url = URL(string: "https://puzzleverseai.com/submit-rating") else {
            completion(false)
            return
        }

        var request = URLRequest(url: url)
        request.httpMethod = "POST"
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")

        do {
            let jsonData = try JSONEncoder().encode(rating)
            request.httpBody = jsonData

            URLSession.shared.dataTask(with: request) { _, response, error in
                DispatchQueue.main.async {
                    let success = error == nil && (response as? HTTPURLResponse)?.statusCode == 200
                    completion(success)
                }
            }.resume()
        } catch {
            completion(false)
        }
    }
}

// MARK: - Smart Rating Dialog
struct AggressiveRatingDialog: View {
    @State private var selectedRating: Int = 0
    @State private var reviewText = ""
    @State private var isSubmitting = false
    @State private var showThankYou = false
    @State private var showFeedbackForm = false

    let onDismiss: () -> Void
    let onCompleted: () -> Void

    private let ratingManager = AppRatingManager.shared

    var body: some View {
        ZStack {
            Color.black.opacity(0.5)
                .ignoresSafeArea()

            if showThankYou {
                thankYouView
            } else if showFeedbackForm {
                feedbackFormView
            } else {
                ratingView
            }
        }
    }

    // MARK: - Main Rating View
    private var ratingView: some View {
        VStack(spacing: 24) {
            VStack(spacing: 12) {
                Text("⭐")
                    .font(.system(size: 60))

                Text("Enjoying RiddleVerse?")
                    .font(.title)
                    .fontWeight(.bold)

                Text("Rate us to earn \(AppRatingManager.coinRewardAmount) coins + early access to new puzzles!")
                    .font(.body)
                    .multilineTextAlignment(.center)
                    .foregroundColor(.secondary)
                    .padding(.horizontal, 8)
            }

            // Star Rating
            VStack(spacing: 16) {
                Text("Tap to rate:")
                    .font(.headline)

                HStack(spacing: 12) {
                    ForEach(1...5, id: \.self) { star in
                        Button(action: {
                            withAnimation(.spring(response: 0.3)) {
                                selectedRating = star
                            }
                        }) {
                            Image(systemName: star <= selectedRating ? "star.fill" : "star")
                                .font(.system(size: 40))
                                .foregroundColor(star <= selectedRating ? .yellow : .gray)
                                .scaleEffect(star <= selectedRating ? 1.1 : 1.0)
                        }
                    }
                }

                if selectedRating > 0 {
                    Text(ratingLabel)
                        .font(.subheadline)
                        .fontWeight(.medium)
                        .foregroundColor(selectedRating >= 4 ? .green : .orange)
                }
            }

            // Action Buttons
            VStack(spacing: 12) {
                if selectedRating > 0 {
                    Button(action: handleRatingSubmit) {
                        HStack {
                            if isSubmitting {
                                ProgressView()
                                    .progressViewStyle(CircularProgressViewStyle(tint: .white))
                                    .scaleEffect(0.8)
                            } else {
                                Image(systemName: "heart.fill")
                                Text(selectedRating >= 4 ? "Rate in App Store" : "Send Feedback")
                            }
                        }
                        .font(.headline)
                        .fontWeight(.semibold)
                        .foregroundColor(.white)
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 16)
                        .background(
                            LinearGradient(
                                colors: selectedRating >= 4 ? [.green, .mint] : [.orange, .red],
                                startPoint: .leading,
                                endPoint: .trailing
                            )
                        )
                        .cornerRadius(12)
                    }
                    .disabled(isSubmitting)

                    // Coin incentive reminder
                    HStack(spacing: 4) {
                        Image(systemName: "circle.fill")
                            .foregroundColor(.yellow)
                            .font(.caption2)
                        Text("Earn \(AppRatingManager.coinRewardAmount) coins")
                            .font(.caption)
                            .foregroundColor(.secondary)
                    }
                }

                Button(action: {
                    ratingManager.markAsDismissed()
                    onDismiss()
                }) {
                    Text("Maybe Later")
                        .font(.subheadline)
                        .foregroundColor(.gray)
                        .padding(.vertical, 12)
                }
            }
            .padding(.horizontal)
        }
        .padding(32)
        .background(
            RoundedRectangle(cornerRadius: 20)
                .fill(Color(.systemBackground))
                .shadow(color: .black.opacity(0.15), radius: 20, x: 0, y: 10)
        )
        .padding(.horizontal, 24)
    }

    // MARK: - Feedback Form (for 1-3 star ratings)
    private var feedbackFormView: some View {
        VStack(spacing: 20) {
            VStack(spacing: 8) {
                Text("Help Us Improve")
                    .font(.title2)
                    .fontWeight(.bold)

                Text("What can we do better? Your feedback helps us improve.")
                    .font(.body)
                    .multilineTextAlignment(.center)
                    .foregroundColor(.secondary)
            }

            TextEditor(text: $reviewText)
                .padding(12)
                .background(Color(.systemGray6))
                .cornerRadius(12)
                .frame(height: 120)
                .overlay(
                    RoundedRectangle(cornerRadius: 12)
                        .stroke(Color.gray.opacity(0.3), lineWidth: 1)
                )
                .overlay(
                    Group {
                        if reviewText.isEmpty {
                            Text("I wish the app had...")
                                .foregroundColor(.gray.opacity(0.5))
                                .padding(.leading, 16)
                                .padding(.top, 20)
                        }
                    },
                    alignment: .topLeading
                )

            Button(action: submitFeedback) {
                HStack {
                    if isSubmitting {
                        ProgressView()
                            .progressViewStyle(CircularProgressViewStyle(tint: .white))
                            .scaleEffect(0.8)
                    } else {
                        Image(systemName: "paperplane.fill")
                        Text("Send Feedback")
                    }
                }
                .font(.headline)
                .foregroundColor(.white)
                .frame(maxWidth: .infinity)
                .padding(.vertical, 14)
                .background(Color.blue)
                .cornerRadius(12)
            }
            .disabled(isSubmitting)

            Button(action: {
                ratingManager.markAsRated()
                giveCoinReward()
                onCompleted()
            }) {
                Text("Skip")
                    .font(.subheadline)
                    .foregroundColor(.gray)
            }
        }
        .padding(32)
        .background(
            RoundedRectangle(cornerRadius: 20)
                .fill(Color(.systemBackground))
                .shadow(color: .black.opacity(0.15), radius: 20, x: 0, y: 10)
        )
        .padding(.horizontal, 24)
    }

    // MARK: - Thank You View
    private var thankYouView: some View {
        VStack(spacing: 20) {
            Text("🎉")
                .font(.system(size: 60))

            Text("Thank You!")
                .font(.title)
                .fontWeight(.bold)

            if !ratingManager.hasCoinRewardBeenGiven() {
                HStack(spacing: 6) {
                    Image(systemName: "circle.fill")
                        .foregroundColor(.yellow)
                    Text("+\(AppRatingManager.coinRewardAmount) coins added!")
                        .fontWeight(.semibold)
                }
                .font(.title3)
                .foregroundColor(.orange)
            }

            Text("Your feedback means the world to us.")
                .font(.body)
                .foregroundColor(.secondary)

            Button(action: {
                onCompleted()
            }) {
                Text("Continue")
                    .font(.headline)
                    .foregroundColor(.white)
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 14)
                    .background(Color.green)
                    .cornerRadius(12)
            }
            .padding(.top, 8)
        }
        .padding(32)
        .background(
            RoundedRectangle(cornerRadius: 20)
                .fill(Color(.systemBackground))
                .shadow(color: .black.opacity(0.15), radius: 20, x: 0, y: 10)
        )
        .padding(.horizontal, 24)
    }

    // MARK: - Helpers

    private var ratingLabel: String {
        switch selectedRating {
        case 1: return "Poor"
        case 2: return "Fair"
        case 3: return "Good"
        case 4: return "Great!"
        case 5: return "Amazing!"
        default: return ""
        }
    }

    private func handleRatingSubmit() {
        isSubmitting = true

        let userId = Auth.auth().currentUser?.email ?? "anonymous"
        let ratingData = RatingFeedback(
            userId: userId,
            rating: selectedRating,
            review: nil,
            timestamp: Date().timeIntervalSince1970,
            appVersion: getAppVersion(),
            source: "smart_prompt"
        )

        AnalyticsManager.shared.track(AnalyticsEvent("rating_submitted", parameters: [
            "rating": selectedRating,
            "prompt_type": "smart",
            "routed_to": selectedRating >= 4 ? "app_store" : "feedback_form"
        ]))

        RatingNetworkHelper.sendRating(ratingData) { success in
            isSubmitting = false
            ratingManager.markAsRated()
            giveCoinReward()

            if selectedRating >= 4 {
                // High rating → App Store review, then thank you
                requestAppStoreRating()
                withAnimation {
                    showThankYou = true
                }
            } else {
                // Low rating → feedback form (don't send to App Store)
                withAnimation {
                    showFeedbackForm = true
                }
            }
        }
    }

    private func submitFeedback() {
        guard !reviewText.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty else {
            withAnimation { showThankYou = true }
            return
        }

        isSubmitting = true

        let userId = Auth.auth().currentUser?.email ?? "anonymous"
        let feedbackData = RatingFeedback(
            userId: userId,
            rating: selectedRating,
            review: reviewText,
            timestamp: Date().timeIntervalSince1970,
            appVersion: getAppVersion(),
            source: "feedback_form"
        )

        RatingNetworkHelper.sendRating(feedbackData) { _ in
            isSubmitting = false
            withAnimation {
                showThankYou = true
            }
        }
    }

    private func giveCoinReward() {
        guard !ratingManager.hasCoinRewardBeenGiven() else { return }
        ratingManager.markCoinRewardGiven()
        CoinManager.shared.fetchBalance() // Refresh balance after server-side reward

        // Award coins via API
        guard let userId = Auth.auth().currentUser?.uid,
              let url = URL(string: "https://puzzleverseai.com/api/coins/award") else { return }

        var request = URLRequest(url: url)
        request.httpMethod = "POST"
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")

        let body: [String: Any] = [
            "userId": userId,
            "amount": AppRatingManager.coinRewardAmount,
            "reason": "app_rating_reward",
            "platform": "ios"
        ]
        request.httpBody = try? JSONSerialization.data(withJSONObject: body)

        URLSession.shared.dataTask(with: request) { data, _, _ in
            DispatchQueue.main.async {
                CoinManager.shared.fetchBalance()
            }
        }.resume()
    }

    private func requestAppStoreRating() {
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.5) {
            if #available(iOS 14.0, *) {
                if let scene = UIApplication.shared.connectedScenes.first(where: { $0.activationState == .foregroundActive }) as? UIWindowScene {
                    SKStoreReviewController.requestReview(in: scene)
                }
            } else {
                SKStoreReviewController.requestReview()
            }
        }
    }
}

// MARK: - Settings Integration
struct RatingSettingsRow: View {
    @State public var showRatingDialog = false

    var body: some View {
        Button(action: {
            showRatingDialog = true
        }) {
            HStack {
                Image(systemName: "star.fill")
                    .foregroundColor(.orange)
                    .frame(width: 30)

                Text("Rate the App")
                    .font(.body)
                    .foregroundColor(.primary)

                Spacer()

                if !AppRatingManager.shared.hasUserRated() {
                    Text("+\(AppRatingManager.coinRewardAmount) coins")
                        .font(.caption)
                        .fontWeight(.medium)
                        .foregroundColor(.orange)
                        .padding(.horizontal, 8)
                        .padding(.vertical, 4)
                        .background(Color.orange.opacity(0.15))
                        .cornerRadius(8)
                }

                Image(systemName: "chevron.right")
                    .foregroundColor(.gray)
                    .font(.caption)
            }
            .padding()
            .background(Color(.systemGray6))
            .cornerRadius(12)
        }
        .sheet(isPresented: $showRatingDialog) {
            AggressiveRatingDialog(
                onDismiss: { showRatingDialog = false },
                onCompleted: { showRatingDialog = false }
            )
        }
    }
}

// MARK: - Helper Functions
private func getAppVersion() -> String {
    return Bundle.main.infoDictionary?["CFBundleShortVersionString"] as? String ?? "1.0.0"
}

// MARK: - Integration Extensions
extension HomeView {
    func checkAndShowRatingPromptImmediately() {
        let ratingManager = AppRatingManager.shared
        let statsManager = UserStatsManager.shared
        let totalGames = statsManager.getGlobalStats().totalGamesPlayed

        if ratingManager.shouldShowRatingPrompt(totalGamesPlayed: totalGames) && !showRatingDialog {
            // Show after a short delay to let the UI settle
            DispatchQueue.main.asyncAfter(deadline: .now() + 1.0) {
                if !self.showRatingDialog && ratingManager.shouldShowRatingPrompt(totalGamesPlayed: totalGames) {
                    self.showRatingDialog = true
                    ratingManager.markPromptShown(totalGamesPlayed: totalGames)

                    AnalyticsManager.shared.track(AnalyticsEvent("smart_rating_prompt_shown", parameters: [
                        "total_games_played": totalGames,
                        "prompt_timing": "post_engagement"
                    ]))
                }
            }
        }
    }
}

// MARK: - App Launch Integration
extension AppDelegate {
    func setupAggressiveRatingPrompt() {
        print("App launched - smart rating enabled")
    }
}
