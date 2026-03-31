//
//  WelcomeView.swift
//  PuzzleForge
//
//  Created by Sushanth Tiruvaipati on 5/23/25.
//

import SwiftUI

struct WelcomeView: View {
    @State private var navigateToLogin = false
    @EnvironmentObject var authStateManager: AuthStateManager
    
    var body: some View {
        NavigationStack {
            ZStack {
                // ✅ Background Gradient
                LinearGradient(
                    gradient: Gradient(colors: [Color.purple.opacity(0.9), Color.purple]),
                    startPoint: .topLeading,
                    endPoint: .bottomTrailing
                )
                .ignoresSafeArea()
                
                VStack(spacing: 40) {
                    // ✅ Decorative Question Marks
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
                    
                    // ✅ Text Card
                    VStack(spacing: 16) {
                        Text("PUZZLE VERSE")
                            .font(.title)
                            .fontWeight(.black)
                            .multilineTextAlignment(.center)
                            .foregroundColor(.black)
                        
                        Text("🧩 CREATE • SOLVE • CONQUER 🏆")
                            .font(.headline)
                            .fontWeight(.bold)
                            .foregroundColor(.purple)
                            .multilineTextAlignment(.center)
                        
                        Text("Craft mind-bending puzzles,\nchallenge friends worldwide &\ndominate the leaderboards!")
                            .font(.subheadline)
                            .foregroundColor(.gray)
                            .multilineTextAlignment(.center)
                            .lineSpacing(2)
                    }
                    .padding()
                    .background(Color.white)
                    .cornerRadius(30)
                    .shadow(radius: 8)
                    .padding(.horizontal, 24)
                    
                    // ✅ Continue Button
                    Button(action: {
                        FacebookAnalytics.shared.trackWelcomeContinue()
                        navigateToLogin = true
                    }) {
                        ZStack {
                            Circle()
                                .fill(Color.orange)
                                .frame(width: 60, height: 60)
                            Image(systemName: "arrow.right")
                                .foregroundColor(.white)
                                .font(.title2)
                        }
                    }

                    NavigationLink(destination: LoginWelcomeView(), isActive: $navigateToLogin) {
                        EmptyView()
                    }

                    Spacer()
                }
                .padding(.top, 80)
                .padding(.bottom, 40)
            }
        }
        // Check if user gets signed in while on welcome screen
        .onChange(of: authStateManager.isSignedIn) { isSignedIn in
            if isSignedIn {
                // User signed in, the main app will automatically navigate to HomeView
                // No need to do anything here as the app-level navigation will handle it
                print("User signed in from WelcomeView")
            }
        }
    }
}

#Preview {
    WelcomeView()
}
