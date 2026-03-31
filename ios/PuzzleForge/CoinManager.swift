import Foundation
import RevenueCat
import FirebaseAuth
import SwiftUI

class CoinManager: ObservableObject {
    static let shared = CoinManager()

    @Published var balance: Int = 0
    @Published var isLoading: Bool = false
    @Published var purchaseState: CoinPurchaseState = .idle
    @Published var creatorEarnings: CreatorEarningsData? = nil
    @Published var isLoadingEarnings: Bool = false
    @Published var payoutEligibility: PayoutEligibility? = nil
    @Published var isLoadingPayout: Bool = false
    @Published var payoutHistory: [PayoutRequest] = []
    @Published var payoutMessage: String? = nil

    static let continueCost = 10
    static let customizeCost = 10
    static let remixCost = 15
    static let maxDifficultyLevel = 5

    static func difficultyCost(_ level: Int) -> Int {
        switch level {
        case 2: return 15
        case 3: return 20
        case 4: return 25
        case 5: return 30
        default: return 10
        }
    }

    enum CoinPack: String, CaseIterable {
        case small = "com.kreativekoala.riddleverse.coins.100"
        case medium = "com.kreativekoala.riddleverse.coins.500"
        case large = "com.kreativekoala.riddleverse.coins.1200"

        var coins: Int {
            switch self {
            case .small: return 100
            case .medium: return 500
            case .large: return 1200
            }
        }

        var label: String {
            switch self {
            case .small: return "100 Coins"
            case .medium: return "500 Coins"
            case .large: return "1,200 Coins"
            }
        }
    }

    enum CoinPurchaseState {
        case idle
        case purchasing
        case success
        case failed(String)
    }

    struct GameEarning: Identifiable {
        let id = UUID()
        let gameId: String
        let gameTitle: String
        let totalCoinsEarned: Int
        let totalPlaysMonetized: Int
    }

    struct CreatorEarningsData {
        let totalCoinsEarned: Int
        let totalPlaysMonetized: Int
        let gameBreakdown: [GameEarning]
    }

    struct PayoutEligibility {
        let eligible: Bool
        let earnedBalance: Int
        let minThreshold: Int
        let conversionRate: Double
        let stripeConnected: Bool
        let stripePayoutsEnabled: Bool
        let detailsSubmitted: Bool
        let usdAmount: Double
    }

    struct PayoutRequest: Identifiable {
        let id: String
        let coinsAmount: Int
        let usdAmount: Double
        let status: String
        let createdAt: String
        let completedAt: String?
    }

    @Published private var storeProducts: [StoreProduct] = []

    private init() {}

    // MARK: - Balance

    func fetchBalance() {
        guard let userId = Auth.auth().currentUser?.uid else { return }
        guard let url = URL(string: "https://puzzleverseai.com/api/coins/balance?userId=\(userId)") else { return }

        isLoading = true
        URLSession.shared.dataTask(with: url) { [weak self] data, _, error in
            DispatchQueue.main.async {
                self?.isLoading = false
                guard let data = data,
                      let json = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
                      let balance = json["balance"] as? Int else { return }
                self?.balance = balance
            }
        }.resume()
    }

    var canContinue: Bool {
        balance >= CoinManager.continueCost
    }

    var canAffordRemix: Bool {
        balance >= CoinManager.remixCost
    }

    // MARK: - Spend Coins

    func spendForRemix(gameId: String, creatorId: String?, completion: @escaping (Bool) -> Void) {
        guard let userId = Auth.auth().currentUser?.uid else { completion(false); return }
        guard let url = URL(string: "https://puzzleverseai.com/api/coins/spend") else { completion(false); return }
        var request = URLRequest(url: url)
        request.httpMethod = "POST"
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        var body: [String: Any] = ["userId": userId, "reason": "remix", "gameId": gameId, "platform": "ios"]
        if let creatorId = creatorId { body["creatorId"] = creatorId }
        request.httpBody = try? JSONSerialization.data(withJSONObject: body)
        URLSession.shared.dataTask(with: request) { [weak self] data, _, _ in
            DispatchQueue.main.async {
                guard let data = data,
                      let json = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
                      let success = json["success"] as? Bool, success,
                      let newBalance = json["newBalance"] as? Int else { completion(false); return }
                self?.balance = newBalance
                completion(true)
            }
        }.resume()
    }

    func spendForContinue(gameId: String, creatorId: String?, completion: @escaping (Bool) -> Void) {
        guard let userId = Auth.auth().currentUser?.uid else {
            completion(false)
            return
        }
        guard let url = URL(string: "https://puzzleverseai.com/api/coins/spend") else {
            completion(false)
            return
        }

        var request = URLRequest(url: url)
        request.httpMethod = "POST"
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")

        var body: [String: Any] = [
            "userId": userId,
            "reason": "continue_play",
            "gameId": gameId,
            "platform": "ios"
        ]
        if let creatorId = creatorId {
            body["creatorId"] = creatorId
        }
        request.httpBody = try? JSONSerialization.data(withJSONObject: body)

        URLSession.shared.dataTask(with: request) { [weak self] data, _, _ in
            DispatchQueue.main.async {
                guard let data = data,
                      let json = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
                      let success = json["success"] as? Bool, success,
                      let newBalance = json["newBalance"] as? Int else {
                    completion(false)
                    return
                }
                self?.balance = newBalance
                completion(true)
            }
        }.resume()
    }

    func spendForHarderChallenge(gameId: String, difficultyLevel: Int, creatorId: String? = nil, completion: @escaping (Bool) -> Void) {
        guard let userId = Auth.auth().currentUser?.uid else {
            completion(false)
            return
        }
        guard let url = URL(string: "https://puzzleverseai.com/api/coins/spend") else {
            completion(false)
            return
        }

        var request = URLRequest(url: url)
        request.httpMethod = "POST"
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")

        var body: [String: Any] = [
            "userId": userId,
            "reason": "harder_challenge",
            "gameId": gameId,
            "difficultyLevel": difficultyLevel,
            "platform": "ios"
        ]
        if let creatorId = creatorId, creatorId != userId {
            body["creatorId"] = creatorId
        }
        request.httpBody = try? JSONSerialization.data(withJSONObject: body)

        URLSession.shared.dataTask(with: request) { [weak self] data, _, _ in
            DispatchQueue.main.async {
                guard let data = data,
                      let json = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
                      let success = json["success"] as? Bool, success,
                      let newBalance = json["newBalance"] as? Int else {
                    completion(false)
                    return
                }
                self?.balance = newBalance
                completion(true)
            }
        }.resume()
    }

    func canAffordDifficulty(_ level: Int) -> Bool {
        balance >= CoinManager.difficultyCost(level)
    }

    var canAffordCustomize: Bool {
        balance >= CoinManager.customizeCost
    }

    func spendForCustomize(gameId: String, creatorId: String? = nil, completion: @escaping (Bool) -> Void) {
        guard let userId = Auth.auth().currentUser?.uid else {
            completion(false)
            return
        }
        guard let url = URL(string: "https://puzzleverseai.com/api/coins/spend") else {
            completion(false)
            return
        }

        var request = URLRequest(url: url)
        request.httpMethod = "POST"
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")

        var body: [String: Any] = [
            "userId": userId,
            "reason": "customize",
            "gameId": gameId,
            "platform": "ios"
        ]
        if let creatorId = creatorId, creatorId != userId {
            body["creatorId"] = creatorId
        }
        request.httpBody = try? JSONSerialization.data(withJSONObject: body)

        URLSession.shared.dataTask(with: request) { [weak self] data, _, _ in
            DispatchQueue.main.async {
                guard let data = data,
                      let json = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
                      let success = json["success"] as? Bool, success,
                      let newBalance = json["newBalance"] as? Int else {
                    completion(false)
                    return
                }
                self?.balance = newBalance
                completion(true)
            }
        }.resume()
    }

    // MARK: - Has Played Check

    func checkHasPlayed(gameId: String, completion: @escaping (Bool) -> Void) {
        guard let userId = Auth.auth().currentUser?.uid else {
            completion(false)
            return
        }
        guard let url = URL(string: "https://puzzleverseai.com/api/games/\(gameId)/has-played?userId=\(userId)") else {
            completion(false)
            return
        }

        URLSession.shared.dataTask(with: url) { data, _, _ in
            DispatchQueue.main.async {
                guard let data = data,
                      let json = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
                      let hasPlayed = json["hasPlayed"] as? Bool else {
                    completion(false)
                    return
                }
                completion(hasPlayed)
            }
        }.resume()
    }

    // MARK: - Creator Earnings

    func fetchCreatorEarnings() {
        guard let userId = Auth.auth().currentUser?.uid else { return }
        guard let url = URL(string: "https://puzzleverseai.com/api/coins/earnings?creatorId=\(userId)") else { return }

        isLoadingEarnings = true
        URLSession.shared.dataTask(with: url) { [weak self] data, _, error in
            DispatchQueue.main.async {
                self?.isLoadingEarnings = false
                guard let data = data,
                      let json = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
                      let success = json["success"] as? Bool, success else {
                    return
                }

                let totalEarned = json["totalCoinsEarned"] as? Int ?? 0
                let totalPlays = json["totalPlaysMonetized"] as? Int ?? 0
                let breakdownArray = json["gameBreakdown"] as? [[String: Any]] ?? []

                let games = breakdownArray.compactMap { g -> GameEarning? in
                    guard let gameId = g["game_id"] as? String else { return nil }
                    return GameEarning(
                        gameId: gameId,
                        gameTitle: g["game_title"] as? String ?? "Untitled Game",
                        totalCoinsEarned: g["total_coins_earned"] as? Int ?? 0,
                        totalPlaysMonetized: g["total_plays_monetized"] as? Int ?? 0
                    )
                }

                self?.creatorEarnings = CreatorEarningsData(
                    totalCoinsEarned: totalEarned,
                    totalPlaysMonetized: totalPlays,
                    gameBreakdown: games
                )
            }
        }.resume()
    }

    // MARK: - Payouts

    func checkPayoutEligibility() {
        guard let userId = Auth.auth().currentUser?.uid else { return }
        guard let url = URL(string: "https://puzzleverseai.com/api/payouts/eligibility?creatorId=\(userId)") else { return }

        URLSession.shared.dataTask(with: url) { [weak self] data, _, error in
            DispatchQueue.main.async {
                guard let data = data,
                      let json = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
                      let success = json["success"] as? Bool, success else { return }

                self?.payoutEligibility = PayoutEligibility(
                    eligible: json["eligible"] as? Bool ?? false,
                    earnedBalance: json["earnedBalance"] as? Int ?? 0,
                    minThreshold: json["minThreshold"] as? Int ?? 1000,
                    conversionRate: json["conversionRate"] as? Double ?? 0.007,
                    stripeConnected: json["stripeConnected"] as? Bool ?? false,
                    stripePayoutsEnabled: json["stripePayoutsEnabled"] as? Bool ?? false,
                    detailsSubmitted: json["detailsSubmitted"] as? Bool ?? false,
                    usdAmount: json["usdAmount"] as? Double ?? 0.0
                )
            }
        }.resume()
    }

    func connectStripe(completion: @escaping (URL?) -> Void) {
        guard let user = Auth.auth().currentUser else {
            completion(nil)
            return
        }

        isLoadingPayout = true
        user.getIDToken { [weak self] idToken, error in
            guard let idToken = idToken else {
                DispatchQueue.main.async {
                    self?.isLoadingPayout = false
                    self?.payoutMessage = "Authentication failed"
                    completion(nil)
                }
                return
            }

            guard let url = URL(string: "https://puzzleverseai.com/api/payouts/connect-stripe") else {
                DispatchQueue.main.async { self?.isLoadingPayout = false }
                completion(nil)
                return
            }

            var request = URLRequest(url: url)
            request.httpMethod = "POST"
            request.setValue("application/json", forHTTPHeaderField: "Content-Type")
            request.setValue("Bearer \(idToken)", forHTTPHeaderField: "Authorization")

            let body: [String: Any] = ["creatorId": user.uid]
            request.httpBody = try? JSONSerialization.data(withJSONObject: body)

            URLSession.shared.dataTask(with: request) { data, _, _ in
                DispatchQueue.main.async {
                    self?.isLoadingPayout = false
                    guard let data = data,
                          let json = try? JSONSerialization.jsonObject(with: data) as? [String: Any] else {
                        self?.payoutMessage = "Failed to connect Stripe. Please try again."
                        completion(nil)
                        return
                    }

                    if let success = json["success"] as? Bool, success,
                       let urlString = json["url"] as? String,
                       let onboardingURL = URL(string: urlString) {
                        completion(onboardingURL)
                    } else {
                        let errorMsg = json["error"] as? String ?? "Failed to connect Stripe. Please try again."
                        self?.payoutMessage = errorMsg
                        completion(nil)
                    }
                }
            }.resume()
        }
    }

    func checkStripeStatus() {
        guard let userId = Auth.auth().currentUser?.uid else { return }
        guard let url = URL(string: "https://puzzleverseai.com/api/payouts/stripe-status?creatorId=\(userId)") else { return }

        URLSession.shared.dataTask(with: url) { [weak self] data, _, _ in
            DispatchQueue.main.async {
                guard let data = data,
                      let json = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
                      let success = json["success"] as? Bool, success else { return }

                // Refresh full eligibility
                self?.checkPayoutEligibility()
            }
        }.resume()
    }

    func requestPayout(coinsAmount: Int, completion: @escaping (Bool, String?) -> Void) {
        guard let user = Auth.auth().currentUser else {
            completion(false, "Not signed in")
            return
        }

        isLoadingPayout = true
        user.getIDToken { [weak self] idToken, error in
            guard let idToken = idToken else {
                DispatchQueue.main.async {
                    self?.isLoadingPayout = false
                    completion(false, "Authentication failed")
                }
                return
            }

            guard let url = URL(string: "https://puzzleverseai.com/api/payouts/request") else {
                DispatchQueue.main.async { self?.isLoadingPayout = false }
                completion(false, "Invalid URL")
                return
            }

            var request = URLRequest(url: url)
            request.httpMethod = "POST"
            request.setValue("application/json", forHTTPHeaderField: "Content-Type")
            request.setValue("Bearer \(idToken)", forHTTPHeaderField: "Authorization")

            let body: [String: Any] = ["creatorId": user.uid, "coinsAmount": coinsAmount]
            request.httpBody = try? JSONSerialization.data(withJSONObject: body)

            URLSession.shared.dataTask(with: request) { data, _, _ in
                DispatchQueue.main.async {
                    self?.isLoadingPayout = false
                    guard let data = data,
                          let json = try? JSONSerialization.jsonObject(with: data) as? [String: Any] else {
                        completion(false, "No response")
                        return
                    }

                    let success = json["success"] as? Bool ?? false
                    if success {
                        if let newBalance = json["newBalance"] as? Int {
                            self?.balance = newBalance
                        }
                        let usd = json["usdAmount"] as? Double ?? 0.0
                        self?.payoutMessage = String(format: "Payout requested! $%.2f is on the way.", usd)
                        self?.checkPayoutEligibility()
                        self?.fetchPayoutHistory()
                        completion(true, nil)
                    } else {
                        let error = json["error"] as? String ?? "Payout failed"
                        self?.payoutMessage = error
                        completion(false, error)
                    }
                }
            }.resume()
        }
    }

    func fetchPayoutHistory() {
        guard let userId = Auth.auth().currentUser?.uid else { return }
        guard let url = URL(string: "https://puzzleverseai.com/api/payouts/history?creatorId=\(userId)") else { return }

        URLSession.shared.dataTask(with: url) { [weak self] data, _, _ in
            DispatchQueue.main.async {
                guard let data = data,
                      let json = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
                      let success = json["success"] as? Bool, success,
                      let payoutsArray = json["payouts"] as? [[String: Any]] else { return }

                self?.payoutHistory = payoutsArray.compactMap { p in
                    guard let id = p["id"] as? String else { return nil }
                    return PayoutRequest(
                        id: id,
                        coinsAmount: p["coins_amount"] as? Int ?? 0,
                        usdAmount: p["usd_amount"] as? Double ?? 0.0,
                        status: p["status"] as? String ?? "unknown",
                        createdAt: p["created_at"] as? String ?? "",
                        completedAt: p["completed_at"] as? String
                    )
                }
            }
        }.resume()
    }

    // MARK: - RevenueCat Coin Purchases

    func loadProducts() async {
        let productIds = CoinPack.allCases.map { $0.rawValue }
        do {
            let products = try await Purchases.shared.products(productIds)
            await MainActor.run { storeProducts = products }
            NSLog("[CoinManager] Loaded %d coin products", products.count)
        } catch {
            NSLog("[CoinManager] Failed to load products: %@", error.localizedDescription)
        }
    }

    func product(for pack: CoinPack) -> StoreProduct? {
        storeProducts.first { $0.productIdentifier == pack.rawValue }
    }

    func purchase(_ pack: CoinPack) async {
        guard let storeProduct = product(for: pack) else {
            await MainActor.run { purchaseState = .failed("Product not available") }
            return
        }

        await MainActor.run { purchaseState = .purchasing }

        do {
            let (_, customerInfo, userCancelled) = try await Purchases.shared.purchase(product: storeProduct)

            if userCancelled {
                await MainActor.run { purchaseState = .idle }
            } else {
                await recordPurchase(pack: pack, transactionId: "")
                await MainActor.run { purchaseState = .success }
            }
        } catch {
            await MainActor.run { purchaseState = .failed(error.localizedDescription) }
        }
    }

    private func recordPurchase(pack: CoinPack, transactionId: String) async {
        guard let userId = Auth.auth().currentUser?.uid else { return }
        guard let url = URL(string: "https://puzzleverseai.com/api/coins/purchase") else { return }

        var request = URLRequest(url: url)
        request.httpMethod = "POST"
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")

        let body: [String: Any] = [
            "userId": userId,
            "productId": pack.rawValue,
            "transactionId": transactionId,
            "platform": "ios"
        ]
        request.httpBody = try? JSONSerialization.data(withJSONObject: body)

        do {
            let (data, _) = try await URLSession.shared.data(for: request)
            if let json = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
               let newBalance = json["newBalance"] as? Int {
                await MainActor.run { self.balance = newBalance }
            }
        } catch {
            NSLog("[CoinManager] Record purchase error: %@", error.localizedDescription)
        }
    }
}
