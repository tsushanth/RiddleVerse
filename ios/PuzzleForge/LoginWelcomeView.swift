//
//  LoginWelcomeView.swift
//  PuzzleForge
//
//  Created by Sushanth Tiruvaipati on 5/23/25.
//

import SwiftUI
import FirebaseAuth

struct LoginWelcomeView: View {
    @State private var navigateToNext = false
    @State private var hasRequestedTracking = false
    @StateObject private var viewModel = QAPuzzleViewModel()
    @EnvironmentObject var authStateManager: AuthStateManager
    @State private var showGuestPaywall = false

    var body: some View {
        ZStack {
            // ✅ Gradient Background
            LinearGradient(
                gradient: Gradient(colors: [Color.purple.opacity(0.9), Color.purple]),
                startPoint: .topLeading,
                endPoint: .bottomTrailing
            )
            .ignoresSafeArea()

            VStack(spacing: 40) {
                Spacer()

                // ✅ Question Marks
                HStack(spacing: 16) {
                    Image(systemName: "questionmark.circle.fill")
                        .resizable()
                        .frame(width: 60, height: 60)
                        .foregroundColor(.pink)

                    Image(systemName: "questionmark.circle.fill")
                        .resizable()
                        .frame(width: 80, height: 80)
                        .foregroundColor(.orange)

                    Image(systemName: "questionmark.circle.fill")
                        .resizable()
                        .frame(width: 60, height: 60)
                        .foregroundColor(.purple.opacity(0.7))
                }

                Spacer()

                // ✅ Get Started Button
                Button(action: {
                    handleGetStarted()
                }) {
                    Text("Get Started")
                        .foregroundColor(.white)
                        .frame(maxWidth: .infinity)
                        .padding()
                        .background(Color.orange)
                        .cornerRadius(30)
                        .padding(.horizontal, 40)
                        .shadow(radius: 4)
                }

                // ✅ Log in Text
                HStack(spacing: 4) {
                    Text("Already have an account?")
                        .foregroundColor(.white)

                    Button(action: {
                        handleGetStarted()
                    }) {
                        Text("Log in")
                            .foregroundColor(.orange)
                            .underline()
                    }
                }
                .font(.subheadline)

                // ✅ Continue as Guest — show paywall immediately
                Button(action: {
                    authStateManager.enterGuestMode()
                    // Show paywall immediately after entering guest mode
                    DispatchQueue.main.asyncAfter(deadline: .now() + 0.5) {
                        showGuestPaywall = true
                    }
                }) {
                    Text("Continue as Guest")
                        .foregroundColor(.white.opacity(0.8))
                        .font(.subheadline)
                        .underline()
                }

                // ✅ Navigate based on sign-in status
                NavigationLink(
                    destination: viewModel.isSignedIn
                        ? AnyView(HomeView())
                        : AnyView(SignInView(viewModel: viewModel)),
                    isActive: $navigateToNext
                ) {
                    EmptyView()
                }

                Spacer().frame(height: 30)
            }
            .padding()
        }
        .onAppear {
            checkUserSignInStatus()
        }
        .fullScreenCover(isPresented: $showGuestPaywall) {
            RemotePaywallView(
                context: .onboarding,
                onSuccess: {
                    showGuestPaywall = false
                },
                onCancel: {
                    showGuestPaywall = false
                }
            )
        }
    }
    
    // ✅ Check if user is already signed in
    private func checkUserSignInStatus() {
        if let user = Auth.auth().currentUser {
            viewModel.isSignedIn = true
            viewModel.userEmail = user.email ?? ""
            viewModel.userId = user.uid
        }
    }
    
    // ✅ Handle Get Started with ATT
    private func handleGetStarted() {
        // Request tracking permission first
        if !hasRequestedTracking {
            TrackingPermissionManager.shared.requestTrackingPermission { granted in
                hasRequestedTracking = true
                // Continue to next screen regardless of permission
                navigateToNext = true
            }
        } else {
            // Already requested, just navigate
            navigateToNext = true
        }
    }
}
