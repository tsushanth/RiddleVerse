//
//  WelcomeOnboardingView.swift
//  PuzzleForge
//
//  Created by Sushanth Tiruvaipati on 6/18/25.
//


import SwiftUI

struct WelcomeOnboardingView: View {
    let onComplete: () -> Void
    let onSkip: () -> Void
    
    @State private var currentPage = 0
    @State private var animationStates = [false, false, false, false]
    @State private var tutorialStartTime = Date()
    @State private var showOnboardingPaywall = false

    private let totalPages = 4

    private func completeTutorial() {
        let duration = Date().timeIntervalSince(tutorialStartTime)
        AnalyticsManager.shared.track(.tutorialComplete(duration: duration))

        // Track as conversion event
        AnalyticsManager.shared.trackConversion(.tutorialComplete(duration: duration), value: 10.0)

        showOnboardingPaywall = true
    }
    
    private func skipTutorial() {
        AnalyticsManager.shared.track(.tutorialSkip(step: currentPage + 1))
        // Instead of skipping past the paywall, show the paywall
        showOnboardingPaywall = true
    }
    
    var body: some View {
        GeometryReader { geometry in
            ZStack {
                // Background gradient
                LinearGradient(
                    gradient: Gradient(colors: [
                        Color(red: 0.4, green: 0.49, blue: 0.91),
                        Color(red: 0.46, green: 0.29, blue: 0.64)
                    ]),
                    startPoint: .top,
                    endPoint: .bottom
                )
                .ignoresSafeArea()
                
                VStack(spacing: 0) {
                    // Skip button (top right)
                    HStack {
                        Spacer()
                        
                        if currentPage < 3 {
                            Button("Skip") {
                                skipTutorial()
                            }
                            .foregroundColor(.white.opacity(0.8))
                            .font(.system(size: 16))
                        }
                    }
                    .padding(.horizontal, 16)
                    .padding(.top, 50)
                    
                    // Content pages
                    TabView(selection: $currentPage) {
                        ValuePropPage1(isVisible: $animationStates[0])
                            .tag(0)
                        ValuePropPage2(isVisible: $animationStates[1])
                            .tag(1)
                        ValuePropPage3(isVisible: $animationStates[2])
                            .tag(2)
                        GetStartedPage(isVisible: $animationStates[3], onComplete: onComplete)
                            .tag(3)
                    }
                    .onAppear {
                                tutorialStartTime = Date()
                                AnalyticsManager.shared.track(.tutorialStart())
                            }
                    .tabViewStyle(PageTabViewStyle(indexDisplayMode: .never))
                    .onAppear {
                        DispatchQueue.main.asyncAfter(deadline: .now()) {
                            animationStates[0] = true
                        }
                    }
                    .onChange(of: currentPage) { newPage in
                        let pageNames = ["brain_training", "progress_tracking", "compete_achieve", "get_started"]
                        DispatchQueue.main.asyncAfter(deadline: .now()) {
                            if newPage < animationStates.count {
                                AnalyticsManager.shared.track(.tutorialStep(
                                    step: newPage + 1,
                                    stepName: pageNames[newPage]
                                ))
                                animationStates[newPage] = true
                            }
                        }
                    }
                    
                    // Bottom navigation
                    OnboardingBottomBar(
                        currentPage: $currentPage,
                        totalPages: totalPages,
                        onNext: {
                            if currentPage < totalPages - 1 {
                                withAnimation(.easeInOut(duration: 0.5)) {
                                    currentPage += 1
                                }
                            } else {
                                completeTutorial()
                            }
                        },
                        onComplete: onComplete
                    )
                }
            }
        }
        .fullScreenCover(isPresented: $showOnboardingPaywall) {
            RemotePaywallView(
                context: .onboarding,
                onSuccess: {
                    showOnboardingPaywall = false
                    onComplete()
                },
                onCancel: {
                    // Paywall dismissed without purchase — still allow entry
                    // but they will hit the 3-puzzle hard paywall quickly
                    showOnboardingPaywall = false
                    onComplete()
                }
            )
        }
    }
}

struct ValuePropPage1: View {
    @Binding var isVisible: Bool
    @State private var brainScale: CGFloat = 0.8
    
    var body: some View {
        VStack(spacing: 32) {
            Spacer()
            
            // Animated brain emoji
            Text("🧠")
                .font(.system(size: 100))
                .scaleEffect(brainScale)
                .animation(
                    Animation.easeInOut(duration: 2.0).repeatForever(autoreverses: true),
                    value: brainScale
                )
                .onAppear {
                    brainScale = 1.0
                }
            
            VStack(spacing: 16) {
                Text("Train Your Brain Daily")
                    .font(.system(size: 32, weight: .bold))
                    .foregroundColor(.white)
                    .multilineTextAlignment(.center)
                
                Text("Sharpen your mind with math, word puzzles, and brain teasers designed to boost cognitive skills")
                    .font(.system(size: 18))
                    .foregroundColor(.white.opacity(0.9))
                    .multilineTextAlignment(.center)
                    .lineSpacing(6)
            }
            
            VStack(spacing: 12) {
                FeatureRow(icon: "🔢", text: "Math & Logic Puzzles")
                FeatureRow(icon: "📝", text: "Word & Language Games")
                FeatureRow(icon: "⚡", text: "Quick Daily Challenges")
            }
            
            Spacer()
        }
        .padding(.horizontal, 32)
        .opacity(isVisible ? 1 : 0)
        .offset(y: isVisible ? 0 : 100)
        .animation(.easeOut(duration: 0.8), value: isVisible)
    }
}

struct ValuePropPage2: View {
    @Binding var isVisible: Bool
    
    var body: some View {
        VStack(spacing: 32) {
            Spacer()
            
            Text("📈")
                .font(.system(size: 100))
            
            VStack(spacing: 16) {
                Text("Track Your Progress")
                    .font(.system(size: 32, weight: .bold))
                    .foregroundColor(.white)
                    .multilineTextAlignment(.center)
                
                Text("Level up, earn badges, and see your improvement over time with detailed analytics")
                    .font(.system(size: 18))
                    .foregroundColor(.white.opacity(0.9))
                    .multilineTextAlignment(.center)
                    .lineSpacing(6)
            }
            
            ProgressVisualizationCard()
            
            Spacer()
        }
        .padding(.horizontal, 32)
        .opacity(isVisible ? 1 : 0)
        .offset(y: isVisible ? 0 : 100)
        .animation(.easeOut(duration: 0.8), value: isVisible)
    }
}

struct ValuePropPage3: View {
    @Binding var isVisible: Bool
    
    var body: some View {
        VStack(spacing: 32) {
            Spacer()
            
            Text("🏆")
                .font(.system(size: 100))
            
            VStack(spacing: 16) {
                Text("Compete & Achieve")
                    .font(.system(size: 32, weight: .bold))
                    .foregroundColor(.white)
                    .multilineTextAlignment(.center)
                
                Text("Challenge friends, climb leaderboards, and unlock exclusive rewards as you master each puzzle type")
                    .font(.system(size: 18))
                    .foregroundColor(.white.opacity(0.9))
                    .multilineTextAlignment(.center)
                    .lineSpacing(6)
            }
            
            AchievementShowcase()
            
            Spacer()
        }
        .padding(.horizontal, 32)
        .opacity(isVisible ? 1 : 0)
        .offset(y: isVisible ? 0 : 100)
        .animation(.easeOut(duration: 0.8), value: isVisible)
    }
}

struct GetStartedPage: View {
    @Binding var isVisible: Bool
    let onComplete: () -> Void
    
    var body: some View {
        VStack(spacing: 32) {
            Spacer()
            
            Text("🚀")
                .font(.system(size: 100))
            
            VStack(spacing: 16) {
                Text("Ready to Start?")
                    .font(.system(size: 32, weight: .bold))
                    .foregroundColor(.white)
                    .multilineTextAlignment(.center)
                
                Text("Join thousands of puzzle enthusiasts and start your brain training journey today!")
                    .font(.system(size: 18))
                    .foregroundColor(.white.opacity(0.9))
                    .multilineTextAlignment(.center)
                    .lineSpacing(6)
            }
            
            Button(action: onComplete) {
                Text("Let's Get Started!")
                    .font(.system(size: 18, weight: .bold))
                    .foregroundColor(Color(red: 0.4, green: 0.49, blue: 0.91))
                    .frame(maxWidth: .infinity)
                    .frame(height: 56)
                    .background(Color.white)
                    .cornerRadius(28)
            }
            
            Text("Free to play • No ads during puzzles")
                .font(.system(size: 14))
                .foregroundColor(.white.opacity(0.7))
                .multilineTextAlignment(.center)
            
            Spacer()
        }
        .padding(.horizontal, 32)
        .opacity(isVisible ? 1 : 0)
        .offset(y: isVisible ? 0 : 100)
        .animation(.easeOut(duration: 0.8), value: isVisible)
    }
}

struct OnboardingBottomBar: View {
    @Binding var currentPage: Int
    let totalPages: Int
    let onNext: () -> Void
    let onComplete: () -> Void
    
    var body: some View {
        VStack(spacing: 24) {
            // Page indicators
            HStack(spacing: 8) {
                ForEach(0..<totalPages, id: \.self) { index in
                    Circle()
                        .fill(currentPage == index ? Color.white : Color.white.opacity(0.4))
                        .frame(width: currentPage == index ? 12 : 8, height: currentPage == index ? 12 : 8)
                        .animation(.easeInOut(duration: 0.3), value: currentPage)
                }
            }
            
            // Next/Get Started button
            if currentPage < totalPages - 1 {
                Button(action: onNext) {
                    HStack {
                        Text("Next")
                            .font(.system(size: 16, weight: .medium))
                            .foregroundColor(.white)
                        
                        Image(systemName: "arrow.right")
                            .foregroundColor(.white)
                            .font(.system(size: 20))
                    }
                    .frame(maxWidth: .infinity)
                    .frame(height: 56)
                    .background(Color.white.opacity(0.2))
                    .cornerRadius(28)
                }
            }
        }
        .padding(.horizontal, 32)
        .padding(.bottom, 40)
    }
}

struct FeatureRow: View {
    let icon: String
    let text: String
    
    var body: some View {
        HStack(spacing: 16) {
            Text(icon)
                .font(.system(size: 24))
            
            Text(text)
                .font(.system(size: 16, weight: .medium))
                .foregroundColor(.white.opacity(0.8))
            
            Spacer()
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }
}

struct ProgressVisualizationCard: View {
    var body: some View {
        RoundedRectangle(cornerRadius: 16)
            .fill(Color.white.opacity(0.1))
            .frame(height: 120)
            .overlay(
                VStack(spacing: 12) {
                    HStack {
                        Text("Level 5")
                            .font(.system(size: 16, weight: .bold))
                            .foregroundColor(.white)
                        
                        Spacer()
                        
                        Text("⭐ 1,250 XP")
                            .font(.system(size: 14))
                            .foregroundColor(.white)
                    }
                    
                    ProgressView(value: 0.65)
                        .progressViewStyle(LinearProgressViewStyle(tint: Color(red: 1.0, green: 0.84, blue: 0.0)))
                        .background(Color.white.opacity(0.3))
                        .cornerRadius(4)
                        .frame(height: 8)
                    
                    HStack {
                        Text("🔥 5 day streak")
                            .font(.system(size: 12))
                            .foregroundColor(.white)
                        
                        Spacer()
                        
                        Text("65% to Level 6")
                            .font(.system(size: 12))
                            .foregroundColor(.white)
                    }
                }
                .padding(20)
            )
    }
}

struct AchievementShowcase: View {
    private let achievements = [
        ("🥇", "Math Master", "Solved 100 math puzzles"),
        ("⚡", "Speed Demon", "5 puzzles under 30 seconds"),
        ("🔥", "Streak King", "10 day solving streak")
    ]
    
    var body: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: 16) {
                ForEach(0..<achievements.count, id: \.self) { index in
                    let achievement = achievements[index]
                    
                    RoundedRectangle(cornerRadius: 12)
                        .fill(Color.white.opacity(0.1))
                        .frame(width: 140, height: 120)
                        .overlay(
                            VStack(spacing: 8) {
                                Text(achievement.0)
                                    .font(.system(size: 32))
                                
                                Text(achievement.1)
                                    .font(.system(size: 12, weight: .bold))
                                    .foregroundColor(.white)
                                    .multilineTextAlignment(.center)
                                
                                Text(achievement.2)
                                    .font(.system(size: 10))
                                    .foregroundColor(.white.opacity(0.8))
                                    .multilineTextAlignment(.center)
                            }
                            .padding(12)
                        )
                }
            }
            .padding(.horizontal, 32)
        }
    }
}

// MARK: - View Extension for Easy Integration
extension View {
    func onboardingOverlay(
        showOnboarding: Binding<Bool>,
        onComplete: @escaping () -> Void,
        onSkip: @escaping () -> Void
    ) -> some View {
        self.fullScreenCover(isPresented: showOnboarding) {
            WelcomeOnboardingView(
                onComplete: {
                    UserDefaults.standard.set(true, forKey: "onboarding_completed")
                    showOnboarding.wrappedValue = false
                    onComplete()
                },
                onSkip: {
                    // Skip now goes through the paywall (handled inside WelcomeOnboardingView)
                    // so this callback fires after the paywall is dismissed
                    UserDefaults.standard.set(true, forKey: "onboarding_completed")
                    UserDefaults.standard.set(true, forKey: "onboarding_skipped")
                    showOnboarding.wrappedValue = false
                    onSkip()
                }
            )
        }
    }
}
