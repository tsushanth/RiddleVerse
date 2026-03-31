//
//  Fixed SignInView.swift
//  PuzzleForge
//

import SwiftUI
import AuthenticationServices

struct SignInView: View {
    @ObservedObject var viewModel: QAPuzzleViewModel
    @EnvironmentObject var notificationManager: NotificationManager
    @State private var email: String = ""
    @State private var password: String = ""
    @State private var errorMessage: String?
    @State private var showingNotificationPrompt = false

    var body: some View {
        GeometryReader { geometry in
            ScrollView {
                VStack(spacing: 20) {
                    Spacer()

                    Text("Sign in to track progress and scores")
                        .font(.headline)
                        .padding(.bottom, 10)

                    googleSignInButton
                    appleSignInButton(width: geometry.size.width)

                    emailPasswordFields

                    // Notification benefits section
                    notificationBenefitsSection

                    Spacer()
                }
            }
        }
        .alert("Enable Notifications?", isPresented: $showingNotificationPrompt) {
            Button("Yes, Notify Me") {
                notificationManager.requestNotificationPermission()
            }
            Button("Not Now") {
                // Do nothing
            }
        } message: {
            Text("Get notified when your daily puzzles are ready! We'll send you a quick alert so you never miss your daily challenge.")
        }
    }
    
    private var googleSignInButton: some View {
        Button(action: {
            viewModel.signInWithGoogle { success, userEmail, userId in
                if success, let email = userEmail, let id = userId {
                    // Handle successful sign-in with notification integration
                    viewModel.handleSuccessfulGoogleSignIn(userEmail: email, userId: id)
                    
                    // Analytics
                    AnalyticsManager.shared.track(.userLogin(method: "google"))
                    
                    // Prompt for notifications after successful sign-in
                    promptForNotificationsIfNeeded()
                }
            }
        }) {
            HStack {
                Image(systemName: "g.circle.fill")
                    .font(.title)
                    .foregroundColor(.white)
                Text("Sign in with Google")
                    .fontWeight(.medium)
                    .foregroundColor(.white)
            }
            .frame(maxWidth: .infinity)
            .padding()
            .background(Color.orange)
            .cornerRadius(8)
        }
        .frame(height: 50)
        .padding(.horizontal)
    }

    private func appleSignInButton(width: CGFloat) -> some View {
        SignInWithAppleButton(
            onRequest: { request in
                let nonce = viewModel.generateNonce()
                viewModel.currentNonce = nonce
                request.requestedScopes = [.fullName, .email]
                request.nonce = viewModel.sha256(nonce)
            },
            onCompletion: { result in
                viewModel.handleAppleSignIn(result: result) { success, userEmail, userId in
                    if success, let email = userEmail, let id = userId {
                        viewModel.handleSuccessfulAppleSignIn(userEmail: email, userId: id)
                        AnalyticsManager.shared.track(.userLogin(method: "apple"))
                        promptForNotificationsIfNeeded()
                    } else {
                        self.errorMessage = "Apple Sign In failed. Please try again."
                    }
                }
            }
        )
        .frame(width: width * 0.9, height: 50)
        .cornerRadius(8)
        .padding(.top, 5)
    }

    private var emailPasswordFields: some View {
        VStack(spacing: 15) {
            TextField("Email", text: $email)
                .textFieldStyle(RoundedBorderTextFieldStyle())
                .padding(.horizontal)
                .frame(height: 50)

            SecureField("Password", text: $password)
                .textFieldStyle(RoundedBorderTextFieldStyle())
                .padding(.horizontal)
                .frame(height: 50)

            if let errorMessage = errorMessage {
                Text(errorMessage)
                    .foregroundColor(.red)
                    .padding(.horizontal)
                    .multilineTextAlignment(.center)
            }

            HStack(spacing: 15) {
                Button(action: {
                    viewModel.signInWithEmail(email: email, password: password) { success, error in
                        if success {
                            // Handle successful email sign-in with notification integration
                            viewModel.handleSuccessfulEmailSignIn(userEmail: email, userId: email) // Using email as userId for simplicity
                            
                            print("✅ Signed in successfully!")
                            AnalyticsManager.shared.track(.userLogin(method: "email"))
                            
                            // Prompt for notifications after successful sign-in
                            promptForNotificationsIfNeeded()
                        } else {
                            self.errorMessage = error
                        }
                    }
                }) {
                    Text("Sign In")
                        .frame(maxWidth: .infinity)
                        .frame(height: 50)
                        .background(Color.blue)
                        .foregroundColor(.white)
                        .cornerRadius(8)
                }

                Button(action: {
                    viewModel.signUpWithEmail(email: email, password: password) { success, error in
                        if success {
                            // Handle successful email signup with notification integration
                            viewModel.handleSuccessfulEmailSignIn(userEmail: email, userId: email) // Using email as userId for simplicity
                            
                            print("✅ Account created successfully!")
                            AnalyticsManager.shared.track(.userRegistration(method: "email"))
                            
                            // Prompt for notifications after successful signup
                            promptForNotificationsIfNeeded()
                        } else {
                            self.errorMessage = error
                        }
                    }
                }) {
                    Text("Sign Up")
                        .frame(maxWidth: .infinity)
                        .frame(height: 50)
                        .background(Color.green)
                        .foregroundColor(.white)
                        .cornerRadius(8)
                }
            }
            .padding(.horizontal)
        }
        .padding()
        .background(Color.white)
        .cornerRadius(10)
        .shadow(radius: 5)
        .overlay(
            RoundedRectangle(cornerRadius: 10)
                .stroke(Color.gray.opacity(0.3), lineWidth: 1)
        )
        .padding(.horizontal)
    }

    // MARK: - Notification Benefits Section
    private var notificationBenefitsSection: some View {
        VStack(spacing: 12) {
            HStack {
                Image(systemName: "bell.badge")
                    .foregroundColor(.blue)
                    .font(.title2)
                
                Text("Daily Puzzle Notifications")
                    .font(.headline)
                    .foregroundColor(.primary)
                
                Spacer()
            }
            
            VStack(alignment: .leading, spacing: 8) {
                benefitRow(icon: "calendar.badge.clock", text: "Get notified when your daily puzzles are ready")
                benefitRow(icon: "trophy", text: "Never miss your daily challenge streak")
                benefitRow(icon: "brain.head.profile", text: "Stay sharp with consistent practice")
            }
            
            HStack {
                Text("Notification Status:")
                    .font(.caption)
                    .foregroundColor(.secondary)
                
                Spacer()
                
                if notificationManager.isNotificationPermissionGranted {
                    HStack(spacing: 4) {
                        Image(systemName: "checkmark.circle.fill")
                            .foregroundColor(.green)
                            .font(.caption)
                        Text("Enabled")
                            .font(.caption)
                            .foregroundColor(.green)
                    }
                } else {
                    HStack(spacing: 4) {
                        Image(systemName: "bell.slash")
                            .foregroundColor(.orange)
                            .font(.caption)
                        Text("Not enabled")
                            .font(.caption)
                            .foregroundColor(.orange)
                    }
                }
            }
        }
        .padding()
        .background(Color.blue.opacity(0.1))
        .cornerRadius(12)
        .padding(.horizontal)
    }
    
    private func benefitRow(icon: String, text: String) -> some View {
        HStack(spacing: 8) {
            Image(systemName: icon)
                .foregroundColor(.blue)
                .font(.caption)
                .frame(width: 16)
            
            Text(text)
                .font(.caption)
                .foregroundColor(.secondary)
            
            Spacer()
        }
    }
    
    // MARK: - Notification Prompt Logic
    private func promptForNotificationsIfNeeded() {
        // Only prompt if notifications aren't already enabled and user hasn't been asked recently
        if !notificationManager.isNotificationPermissionGranted && shouldPromptForNotifications() {
            DispatchQueue.main.asyncAfter(deadline: .now() + 1.0) {
                showingNotificationPrompt = true
            }
        } else {
            // If already enabled, just setup daily puzzle notifications
            viewModel.setupDailyPuzzleNotifications()
        }
    }
    
    private func shouldPromptForNotifications() -> Bool {
        let lastPromptDate = UserDefaults.standard.object(forKey: "last_notification_prompt_date") as? Date
        let daysSinceLastPrompt = lastPromptDate?.timeIntervalSinceNow ?? -TimeInterval.infinity
        
        // Don't prompt more than once per week
        if daysSinceLastPrompt > -7 * 24 * 60 * 60 {
            UserDefaults.standard.set(Date(), forKey: "last_notification_prompt_date")
            return true
        }
        
        return false
    }
}

// MARK: - Preview
struct SignInView_Previews: PreviewProvider {
    static var previews: some View {
        SignInView(viewModel: QAPuzzleViewModel())
            .environmentObject(NotificationManager.shared)
    }
}
