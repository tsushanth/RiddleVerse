import SwiftUI
import RevenueCat
import RevenueCatUI
import FirebaseAuth

/// RC-managed coin store paywall.
/// Configure an offering with identifier "coins" in the RC dashboard and
/// add the three coin IAP packages there. Design & copy are controlled remotely.
struct CoinStorePaywallView: View {
    @ObservedObject private var coinManager = CoinManager.shared
    @Environment(\.dismiss) private var dismiss

    @State private var coinsOffering: Offering? = nil
    @State private var purchaseError: String? = nil

    var body: some View {
        Group {
            if let offering = coinsOffering {
                RevenueCatUI.PaywallView(offering: offering)
                    .onPurchaseCompleted { customerInfo in
                        handlePurchase(customerInfo: customerInfo)
                    }
                    .onRestoreCompleted { _ in
                        coinManager.fetchBalance()
                        dismiss()
                    }
            } else {
                RevenueCatUI.PaywallView()
                    .onPurchaseCompleted { customerInfo in
                        handlePurchase(customerInfo: customerInfo)
                    }
                    .onRestoreCompleted { _ in
                        coinManager.fetchBalance()
                        dismiss()
                    }
            }
        }
        .onAppear { loadCoinsOffering() }
    }

    private func loadCoinsOffering() {
        Purchases.shared.getOfferings { offerings, _ in
            coinsOffering = offerings?.offering(identifier: "coins")
        }
    }

    private func handlePurchase(customerInfo: CustomerInfo) {
        // Derive which coin product was just purchased from nonSubscriptions
        let coinProductIds = Set(CoinManager.CoinPack.allCases.map { $0.rawValue })
        let productId = customerInfo.nonSubscriptions
            .filter { coinProductIds.contains($0.productIdentifier) }
            .sorted { $0.purchaseDate > $1.purchaseDate }
            .first?.productIdentifier

        Task {
            guard let userId = Auth.auth().currentUser?.uid,
                  let pid = productId,
                  let url = URL(string: "https://puzzleverseai.com/api/coins/purchase") else {
                coinManager.fetchBalance()
                await MainActor.run { dismiss() }
                return
            }

            var request = URLRequest(url: url)
            request.httpMethod = "POST"
            request.setValue("application/json", forHTTPHeaderField: "Content-Type")

            let body: [String: Any] = [
                "userId": userId,
                "productId": pid,
                "transactionId": "",
                "platform": "ios"
            ]
            request.httpBody = try? JSONSerialization.data(withJSONObject: body)

            do {
                let (data, _) = try await URLSession.shared.data(for: request)
                if let json = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
                   let newBalance = json["newBalance"] as? Int {
                    await MainActor.run { coinManager.balance = newBalance }
                } else {
                    coinManager.fetchBalance()
                }
            } catch {
                coinManager.fetchBalance()
            }

            await MainActor.run { dismiss() }
        }
    }
}
