//
//  SubscriptionUpgradeView.swift
//  PuzzleForge
//
//  Updated with App Store compliance requirements
//

import SwiftUI
import RevenueCat
import RevenueCatUI

// MARK: - Limit Info Data Structure
struct LimitInfo: Codable {
    let reason: String
    let dailyUsed: Int
    let dailyLimit: Int
    let monthlyUsed: Int
    let monthlyLimit: Int
    let resetTime: String
    let upgradeUrl: String
    let message: String
}

// MARK: - Limit Warning Banner
struct LimitWarningBanner: View {
    let remainingGenerations: Int
    let resetTime: String
    let onUpgrade: () -> Void
    
    var body: some View {
        HStack(spacing: 12) {
            Image(systemName: "exclamationmark.triangle.fill")
                .foregroundColor(.orange)
                .font(.title2)
            
            VStack(alignment: .leading, spacing: 4) {
                Text("Running Low on Puzzles!")
                    .font(.subheadline)
                    .fontWeight(.semibold)
                    .foregroundColor(.primary)
                
                Text("Only \(remainingGenerations) puzzles left today")
                    .font(.caption)
                    .foregroundColor(.secondary)
            }
            
            Spacer()
            
            Button("Upgrade") {
                onUpgrade()
            }
            .font(.caption)
            .fontWeight(.semibold)
            .foregroundColor(.white)
            .padding(.horizontal, 12)
            .padding(.vertical, 6)
            .background(
                LinearGradient(
                    colors: [.orange, .red],
                    startPoint: .leading,
                    endPoint: .trailing
                )
            )
            .cornerRadius(12)
        }
        .padding()
        .background(Color.orange.opacity(0.1))
        .cornerRadius(12)
        .overlay(
            RoundedRectangle(cornerRadius: 12)
                .stroke(Color.orange.opacity(0.3), lineWidth: 1)
        )
        .padding(.horizontal)
    }
}

// MARK: - Limit Reached Dialog
struct LimitReachedDialog: View {
    let limitInfo: LimitInfo
    let currentTier: SubscriptionTier
    let onUpgrade: (SubscriptionTier) -> Void
    let onDismiss: () -> Void
    
    var body: some View {
        VStack(spacing: 20) {
            Text("Generation Limit Reached")
                .font(.title2)
                .fontWeight(.bold)
            
            Text(limitInfo.message)
                .font(.subheadline)
                .multilineTextAlignment(.center)
            
            HStack {
                Button("Maybe Later") {
                    onDismiss()
                }
                .foregroundColor(.secondary)
                
                Spacer()
                
                Button("Upgrade to Premium") {
                    onUpgrade(.premium)
                }
                .foregroundColor(.white)
                .padding()
                .background(Color.orange)
                .cornerRadius(8)
            }
        }
        .padding()
        .background(Color(.systemBackground))
        .cornerRadius(16)
        .shadow(radius: 10)
    }
}

struct SubscriptionUpgradeView: View {
    let targetTier: SubscriptionTier
    let onSuccess: () -> Void
    let onCancel: () -> Void

    var body: some View {
        RemotePaywallView(
            context: .limitReached,
            targetTier: targetTier,
            onSuccess: onSuccess,
            onCancel: onCancel
        )
    }
}

// MARK: - Safari View for Legal Pages
import SafariServices

struct SafariView: UIViewControllerRepresentable {
    let url: URL
    
    func makeUIViewController(context: Context) -> SFSafariViewController {
        return SFSafariViewController(url: url)
    }
    
    func updateUIViewController(_ uiViewController: SFSafariViewController, context: Context) {}
}

