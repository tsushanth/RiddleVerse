//
//  WinbackOfferView.swift
//  PuzzleForge (RiddleVerse)
//
//  Winback offer shown to users who dismissed the paywall 3+ times.
//  Puzzle/games theme — highlights riddle and gameplay value props.
//

import SwiftUI
import RevenueCat

struct WinbackOfferView: View {
    @Environment(\.dismiss) private var dismiss
    @StateObject private var subscriptionManager = SubscriptionManager.shared
    @State private var isPurchasing = false
    @State private var showError = false
    @State private var errorMessage = ""

    // Accent color matching RiddleVerse's purple brand
    private let accent = Color(red: 0.39, green: 0.40, blue: 0.95)
    /// Product IDs to try in order: yearly first (if ever added), then the
    /// monthly products that actually exist in App Store Connect today.
    private let preferredProductIds = [
        "com.puzzleforge.premium.yearly",          // future-proof, may not exist
        "com.kreativekoala.riddleverse.premium.yearly",
        "com.puzzleforge.premium.monthly",
        "com.puzzleforge.unlimited.monthly"
    ]

    var body: some View {
        ZStack {
            // Background gradient — deep purple game feel
            LinearGradient(
                colors: [
                    Color(red: 0.07, green: 0.06, blue: 0.15),
                    Color(red: 0.12, green: 0.08, blue: 0.25)
                ],
                startPoint: .top,
                endPoint: .bottom
            )
            .ignoresSafeArea()

            ScrollView {
                VStack(spacing: 28) {

                    // MARK: - Badge
                    Text("SPECIAL OFFER")
                        .font(.system(size: 12, weight: .bold))
                        .tracking(1.5)
                        .foregroundColor(.white)
                        .padding(.horizontal, 16)
                        .padding(.vertical, 6)
                        .background(
                            Capsule()
                                .fill(accent)
                        )
                        .padding(.top, 40)

                    // MARK: - Icon
                    ZStack {
                        Circle()
                            .fill(accent.opacity(0.15))
                            .frame(width: 90, height: 90)
                        Image(systemName: "puzzlepiece.fill")
                            .font(.system(size: 40))
                            .foregroundColor(accent)
                    }

                    // MARK: - Headline
                    VStack(spacing: 8) {
                        Text("The Puzzles Miss You!")
                            .font(.system(size: 30, weight: .bold))
                            .foregroundColor(.white)
                            .multilineTextAlignment(.center)

                        Text("Unlock unlimited riddles, hints, and daily challenges — all ad-free.")
                            .font(.system(size: 16))
                            .foregroundColor(.white.opacity(0.65))
                            .multilineTextAlignment(.center)
                            .padding(.horizontal, 24)
                    }

                    // MARK: - Value Props
                    VStack(spacing: 14) {
                        WinbackFeatureRow(
                            icon: "infinity",
                            title: "Unlimited Riddles",
                            subtitle: "No daily caps — play as long as you like",
                            accent: accent
                        )
                        WinbackFeatureRow(
                            icon: "slider.horizontal.3",
                            title: "All Difficulty Levels",
                            subtitle: "From easy fun to brain-bending hard",
                            accent: accent
                        )
                        WinbackFeatureRow(
                            icon: "lightbulb.fill",
                            title: "Hint System",
                            subtitle: "Get nudges when you're truly stuck",
                            accent: accent
                        )
                        WinbackFeatureRow(
                            icon: "calendar.badge.checkmark",
                            title: "Daily Challenges",
                            subtitle: "Fresh puzzles every day to keep your streak",
                            accent: accent
                        )
                    }
                    .padding(.horizontal, 24)

                    // MARK: - CTA Button
                    Button {
                        purchaseYearly()
                    } label: {
                        HStack {
                            if isPurchasing {
                                ProgressView()
                                    .tint(.white)
                            } else {
                                Text("Unlock Premium")
                                    .font(.system(size: 18, weight: .bold))
                            }
                        }
                        .foregroundColor(.white)
                        .frame(maxWidth: .infinity)
                        .frame(height: 60)
                        .background(
                            LinearGradient(
                                colors: [accent, Color(red: 0.55, green: 0.36, blue: 0.96)],
                                startPoint: .leading,
                                endPoint: .trailing
                            )
                        )
                        .cornerRadius(16)
                        .shadow(color: accent.opacity(0.4), radius: 12, y: 6)
                    }
                    .disabled(isPurchasing)
                    .padding(.horizontal, 24)

                    // MARK: - Dismiss
                    Button {
                        dismiss()
                    } label: {
                        Text("No thanks")
                            .font(.system(size: 15, weight: .medium))
                            .foregroundColor(.white.opacity(0.4))
                    }
                    .padding(.bottom, 40)
                }
            }
        }
        .alert("Purchase Failed", isPresented: $showError) {
            Button("OK", role: .cancel) {}
        } message: {
            Text(errorMessage)
        }
    }

    // MARK: - Purchase

    private func purchaseYearly() {
        // Pick the best package we can find: any annual first; otherwise the
        // first product in our preferred list that's actually available.
        let packages = subscriptionManager.availablePackages
        let package = packages.first { $0.packageType == .annual }
            ?? preferredProductIds.lazy
                .compactMap { id in packages.first { $0.storeProduct.productIdentifier == id } }
                .first
            ?? packages.first(where: { $0.packageType == .monthly })
            ?? packages.first
        guard let package else {
            errorMessage = "Premium plan unavailable right now. Please try again."
            showError = true
            return
        }

        isPurchasing = true
        Task {
            await subscriptionManager.purchasePackage(package)
            await MainActor.run {
                isPurchasing = false
                switch subscriptionManager.purchaseState {
                case .success:
                    dismiss()
                case .cancelled:
                    break
                case .failed(let err):
                    errorMessage = err.localizedDescription
                    showError = true
                default:
                    break
                }
            }
        }
    }
}

// MARK: - Feature Row

private struct WinbackFeatureRow: View {
    let icon: String
    let title: String
    let subtitle: String
    let accent: Color

    var body: some View {
        HStack(spacing: 16) {
            ZStack {
                Circle()
                    .fill(accent.opacity(0.15))
                    .frame(width: 48, height: 48)

                Image(systemName: icon)
                    .font(.system(size: 20))
                    .foregroundColor(accent)
            }

            VStack(alignment: .leading, spacing: 2) {
                Text(title)
                    .font(.system(size: 15, weight: .semibold))
                    .foregroundColor(.white)

                Text(subtitle)
                    .font(.system(size: 13))
                    .foregroundColor(.white.opacity(0.55))
            }

            Spacer()

            Image(systemName: "checkmark.circle.fill")
                .foregroundColor(accent)
                .font(.system(size: 20))
        }
        .padding(14)
        .background(
            RoundedRectangle(cornerRadius: 14)
                .fill(Color.white.opacity(0.06))
        )
    }
}

// MARK: - Preview

#Preview {
    WinbackOfferView()
}
