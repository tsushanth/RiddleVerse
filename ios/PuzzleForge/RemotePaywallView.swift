import SwiftUI
import RevenueCat
import PaywallKit

// MARK: - Paywall Context

enum PaywallContext: String {
    case `default` = "default"
    case gameCreation = "game_creation"
    case limitReached = "limit_reached"
    case onboarding = "onboarding"
}

// MARK: - Remote Paywall View

/// PaywallKit-powered paywall with server-side A/B testing and event tracking.
/// Replaces the RevenueCat remote paywall UI.
struct RemotePaywallView: View {
    var context: PaywallContext = .default
    var targetTier: SubscriptionTier = .premium
    var onSuccess: () -> Void = {}
    var onCancel: () -> Void = {}

    @StateObject private var subscriptionManager = SubscriptionManager.shared
    @Environment(\.dismiss) private var dismiss

    @State private var paywallProducts: [PaywallProduct] = []

    var body: some View {
        PaywallKit.PaywallView(
            appId: "riddleverse",
            appName: "RiddleVerse Pro",
            features: [
                PaywallFeature(icon: "💰", title: "Earn Real Money", description: "Players spend coins on your games — you cash out via Stripe"),
                PaywallFeature(icon: "🪙", title: "700 Coins/Month", description: "Play harder versions, remixes & power-ups"),
                PaywallFeature(icon: "♾️", title: "Unlimited Games", description: "No daily limits — create and play as much as you want"),
                PaywallFeature(icon: "🚫", title: "Ad-Free", description: "No interruptions, ever"),
                PaywallFeature(icon: "🎨", title: "All Themes", description: "Every visual style unlocked from day one")
            ],
            products: paywallProducts,
            theme: PaywallTheme(accent: Color(red: 0.39, green: 0.4, blue: 0.95), accent2: Color(red: 0.55, green: 0.36, blue: 0.96)),
            showWinback: true,
            onPurchase: { productId in
                await purchaseProduct(productId: productId)
            },
            onRestore: {
                await restorePurchases()
            },
            onDismiss: {
                onCancel()
                dismiss()
            }
        )
        .task {
            await loadProducts()
        }
        .onAppear {
            Purchases.shared.attribution.setAttributes([
                "last_paywall_source": context.rawValue,
                "last_paywall_date": ISO8601DateFormatter().string(from: Date())
            ])
        }
    }

    // MARK: - Product Loading

    private func loadProducts() async {
        let mgr = subscriptionManager
        if mgr.availablePackages.isEmpty {
            await mgr.loadProducts()
        }

        paywallProducts = mgr.availablePackages.compactMap { pkg -> PaywallProduct? in
            let product = pkg.storeProduct
            let period: PaywallProduct.Period
            switch pkg.packageType {
            case .weekly: period = .weekly
            case .monthly: period = .monthly
            case .annual: period = .yearly
            case .lifetime: period = .lifetime
            default:
                if product.productIdentifier.contains("lifetime") { period = .lifetime }
                else if product.productIdentifier.contains("yearly") || product.productIdentifier.contains("annual") { period = .yearly }
                else if product.productIdentifier.contains("monthly") { period = .monthly }
                else if product.productIdentifier.contains("weekly") { period = .weekly }
                else { return nil }
            }

            var trialDays: Int? = nil
            if let intro = product.introductoryDiscount,
               intro.paymentMode == .freeTrial {
                let sub = intro.subscriptionPeriod
                switch sub.unit {
                case .day: trialDays = sub.value
                case .week: trialDays = sub.value * 7
                case .month: trialDays = sub.value * 30
                case .year: trialDays = sub.value * 365
                @unknown default: trialDays = sub.value
                }
            }

            return PaywallProduct(
                id: product.productIdentifier,
                localizedPrice: product.localizedPriceString,
                price: product.price,
                currencyCode: product.currencyCode ?? "USD",
                trialDays: trialDays,
                period: period
            )
        }
    }

    // MARK: - Purchase

    private func purchaseProduct(productId: String) async {
        guard let package = subscriptionManager.availablePackages.first(where: {
            $0.storeProduct.productIdentifier == productId
        }) else {
            print("[RiddleVersePaywall] No package found for \(productId)")
            return
        }

        await subscriptionManager.purchasePackage(package)

        if subscriptionManager.hasActiveSubscription() {
            await MainActor.run {
                onSuccess()
                dismiss()
            }
        }
    }

    // MARK: - Restore

    private func restorePurchases() async {
        await subscriptionManager.restorePurchases()
        if subscriptionManager.hasActiveSubscription() {
            await MainActor.run {
                onSuccess()
                dismiss()
            }
        }
    }
}
