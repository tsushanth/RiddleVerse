//
//  ShareableContent.swift
//  PuzzleForge
//
//  Created by Sushanth Tiruvaipati on 6/18/25.
//


import SwiftUI
import MessageUI

// MARK: - Data Classes
struct ShareableContent {
    let type: ShareType
    let title: String
    let message: String
    let hashtags: [String]
    let customData: [String: Any]
    
    init(type: ShareType, title: String, message: String, hashtags: [String] = [], customData: [String: Any] = [:]) {
        self.type = type
        self.title = title
        self.message = message
        self.hashtags = hashtags
        self.customData = customData
    }
}

enum ShareType {
    case puzzleResult
    case achievement
    case levelUp
    case streak
    case customPuzzle
    case weeklyRank
    case challengeCompletion
}

struct ShareOption {
    let title: String
    let iconName: String
    let color: Color
    let action: (ShareableContent) -> Void
}

// MARK: - Main Sharing Component
struct SocialShareDialog: View {
    let shareableContent: ShareableContent
    let showCustomPuzzleSharing: Bool
    let onDismiss: () -> Void
    
    @State private var showCustomDialog = false
    @State private var showShareSheet = false
    @State private var shareItems: [Any] = []
    
    var body: some View {
        VStack(spacing: 20) {
            // Title
            Text("Share Your Success!")
                .font(.title2)
                .fontWeight(.bold)
                .multilineTextAlignment(.center)
            
            // Preview of what will be shared
            VStack(spacing: 16) {
                RoundedRectangle(cornerRadius: 12)
                    .fill(Color(.systemGray6))
                    .frame(height: 120)
                    .overlay(
                        VStack(spacing: 8) {
                            Text(shareableContent.title)
                                .font(.headline)
                                .fontWeight(.bold)
                                .multilineTextAlignment(.center)
                            
                            Text(shareableContent.message)
                                .font(.subheadline)
                                .foregroundColor(.secondary)
                                .multilineTextAlignment(.center)
                        }
                        .padding(16)
                    )
                
                // Sharing options
                VStack(spacing: 8) {
                    ForEach(getShareOptions(), id: \.title) { option in
                        ShareOptionRow(
                            option: option,
                            content: shareableContent,
                            onClick: {
                                option.action(shareableContent)
                                onDismiss()
                            }
                        )
                    }
                    
                    // Custom puzzle sharing
                    if showCustomPuzzleSharing {
                        ShareOptionRow(
                            option: ShareOption(
                                title: "Send via Email",
                                iconName: "envelope.fill",
                                color: Color.blue,
                                action: { _ in }
                            ),
                            content: shareableContent,
                            onClick: {
                                showCustomDialog = true
                            }
                        )
                    }
                }
            }
            
            // Cancel button
            Button("Cancel") {
                onDismiss()
            }
            .foregroundColor(.secondary)
        }
        .padding(24)
        .background(Color(.systemBackground))
        .cornerRadius(20)
        .sheet(isPresented: $showShareSheet) {
            ShareSheet(activityItems: shareItems)
        }
        .sheet(isPresented: $showCustomDialog) {
            CustomPuzzleShareDialog(
                onDismiss: { showCustomDialog = false },
                onShare: { email, name in
                    let puzzleId = shareableContent.customData["puzzleId"] as? String ?? ""
                    sharePuzzle(puzzleId: puzzleId, recipientEmail: email, recipientName: name)
                    showCustomDialog = false
                    onDismiss()
                }
            )
        }
    }
    
    private func getShareOptions() -> [ShareOption] {
        return [
            ShareOption(
                title: "Share to Social Media",
                iconName: "square.and.arrow.up",
                color: Color.blue,
                action: { content in shareToSocialMedia(content) }
            ),
            ShareOption(
                title: "Copy to Clipboard",
                iconName: "doc.on.clipboard",
                color: Color.green,
                action: { content in copyToClipboard(content) }
            ),
            ShareOption(
                title: "Send via Messages",
                iconName: "message.fill",
                color: Color.blue,
                action: { content in shareViaMessages(content) }
            )
        ]
    }
    
    private func shareToSocialMedia(_ content: ShareableContent) {
        let shareText = buildShareText(content)
        shareItems = [shareText]
        showShareSheet = true
    }
    
    private func copyToClipboard(_ content: ShareableContent) {
        let shareText = buildShareText(content)
        UIPasteboard.general.string = shareText
        
        // Show toast-like feedback
        let impactFeedback = UIImpactFeedbackGenerator(style: .medium)
        impactFeedback.impactOccurred()
    }
    
    private func shareViaMessages(_ content: ShareableContent) {
        let shareText = buildShareText(content)
        
        if MFMessageComposeViewController.canSendText() {
            // Open Messages app directly
            let urlString = "sms:&body=\(shareText.addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed) ?? "")"
            if let url = URL(string: urlString) {
                UIApplication.shared.open(url)
            }
        } else {
            // Fallback to general sharing
            shareToSocialMedia(content)
        }
    }
    
    private func sharePuzzle(puzzleId: String, recipientEmail: String, recipientName: String) {
        // Implementation for sharing custom puzzle via email
        print("Sharing puzzle \(puzzleId) to \(recipientEmail)")
    }
}

struct ShareOptionRow: View {
    let option: ShareOption
    let content: ShareableContent
    let onClick: () -> Void
    
    var body: some View {
        Button(action: onClick) {
            HStack(spacing: 12) {
                RoundedRectangle(cornerRadius: 20)
                    .fill(option.color.opacity(0.1))
                    .frame(width: 40, height: 40)
                    .overlay(
                        Image(systemName: option.iconName)
                            .foregroundColor(option.color)
                            .font(.system(size: 20))
                    )
                
                Text(option.title)
                    .font(.body)
                    .fontWeight(.medium)
                    .foregroundColor(.primary)
                
                Spacer()
                
                Image(systemName: "chevron.right")
                    .foregroundColor(.secondary)
                    .font(.system(size: 20))
            }
            .padding(.vertical, 8)
            .padding(.horizontal, 4)
        }
        .buttonStyle(PlainButtonStyle())
    }
}

struct CustomPuzzleShareDialog: View {
    let onDismiss: () -> Void
    let onShare: (String, String) -> Void
    
    @State private var recipientEmail = ""
    @State private var recipientName = ""
    
    var body: some View {
        NavigationView {
            VStack(spacing: 20) {
                Text("Share Puzzle")
                    .font(.title2)
                    .fontWeight(.bold)
                
                VStack(spacing: 16) {
                    VStack(alignment: .leading, spacing: 8) {
                        Text("Recipient Email")
                            .font(.subheadline)
                            .foregroundColor(.secondary)
                        TextField("Enter email address", text: $recipientEmail)
                            .textFieldStyle(RoundedBorderTextFieldStyle())
                            .keyboardType(.emailAddress)
                            .autocapitalization(.none)
                    }
                    
                    VStack(alignment: .leading, spacing: 8) {
                        Text("Recipient Name")
                            .font(.subheadline)
                            .foregroundColor(.secondary)
                        TextField("Enter name", text: $recipientName)
                            .textFieldStyle(RoundedBorderTextFieldStyle())
                    }
                }
                
                Spacer()
                
                HStack(spacing: 16) {
                    Button("Cancel") {
                        onDismiss()
                    }
                    .foregroundColor(.secondary)
                    .frame(maxWidth: .infinity)
                    
                    Button("Share") {
                        onShare(recipientEmail, recipientName)
                    }
                    .disabled(recipientEmail.isEmpty || recipientName.isEmpty)
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 12)
                    .background(recipientEmail.isEmpty || recipientName.isEmpty ? Color.gray : Color.blue)
                    .foregroundColor(.white)
                    .cornerRadius(8)
                }
            }
            .padding(24)
            .navigationBarHidden(true)
        }
    }
}

// MARK: - Share Sheet for iOS
struct ShareSheet: UIViewControllerRepresentable {
    let activityItems: [Any]
    
    func makeUIViewController(context: Context) -> UIActivityViewController {
        let controller = UIActivityViewController(activityItems: activityItems, applicationActivities: nil)
        return controller
    }
    
    func updateUIViewController(_ uiViewController: UIActivityViewController, context: Context) {}
}

// MARK: - Content Builders
func buildShareText(_ content: ShareableContent) -> String {
    let hashtags = content.hashtags.isEmpty ? 
        " #PuzzleVerse #BrainTraining" : 
        " " + content.hashtags.map { "#\($0)" }.joined(separator: " ")
    
    switch content.type {
    case .puzzleResult:
        return "\(content.message)\(hashtags)"
    case .achievement:
        return "🏆 \(content.title)\n\(content.message)\(hashtags)"
    case .levelUp:
        return "📈 \(content.title)\n\(content.message)\(hashtags)"
    case .streak:
        return "🔥 \(content.title)\n\(content.message)\(hashtags)"
    case .weeklyRank:
        return "📊 \(content.title)\n\(content.message)\(hashtags)"
    case .challengeCompletion:
        return "✅ \(content.title)\n\(content.message)\(hashtags)"
    case .customPuzzle:
        return "🧩 \(content.title)\n\(content.message)\(hashtags)"
    }
}

// MARK: - Helper Functions for Creating Shareable Content
func createPuzzleResultShare(puzzleType: String, score: Int, streak: Int) -> ShareableContent {
    return ShareableContent(
        type: .puzzleResult,
        title: "Puzzle Completed!",
        message: "🧠 Just solved a \(puzzleType) puzzle and scored \(score) points! 🔥 Current streak: \(streak) days. Can you beat my score?",
        hashtags: ["PuzzleVerse", "BrainTraining", puzzleType.capitalized]
    )
}

func createAchievementShare(_ achievement: Achievement) -> ShareableContent {
    return ShareableContent(
        type: .achievement,
        title: "Achievement Unlocked!",
        message: "Just unlocked '\(achievement.title)' badge! \(achievement.description) 🎯",
        hashtags: ["PuzzleVerse", "Achievement", "BrainTraining"]
    )
}

func createLevelUpShare(oldLevel: Int, newLevel: Int) -> ShareableContent {
    return ShareableContent(
        type: .levelUp,
        title: "Level Up!",
        message: "🚀 Just reached Level \(newLevel) in PuzzleVerse! My brain training is paying off! 💪",
        hashtags: ["PuzzleVerse", "LevelUp", "BrainTraining"]
    )
}

func createStreakShare(streakDays: Int) -> ShareableContent {
    return ShareableContent(
        type: .streak,
        title: "Streak Achievement!",
        message: "🔥 \(streakDays) days of consistent brain training! Join me in daily puzzle solving! 🧠",
        hashtags: ["PuzzleVerse", "Streak", "DailyChallenge"]
    )
}

func createWeeklyRankShare(rank: Int, score: Int) -> ShareableContent {
    return ShareableContent(
        type: .weeklyRank,
        title: "Weekly Leaderboard!",
        message: "📊 This week I'm ranked #\(rank) on PuzzleVerse with \(score) points! 🧠 Join me in daily brain training!",
        hashtags: ["PuzzleVerse", "Leaderboard", "Competition"]
    )
}

func createCustomPuzzleShare(puzzleId: String, puzzleName: String) -> ShareableContent {
    return ShareableContent(
        type: .customPuzzle,
        title: "Custom Puzzle Created!",
        message: "🧩 I just created a custom puzzle '\(puzzleName)' on PuzzleVerse! Think you can solve it?",
        hashtags: ["PuzzleVerse", "CustomPuzzle", "Challenge"],
        customData: ["puzzleId": puzzleId]
    )
}
