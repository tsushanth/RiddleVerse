//
//  PuzzleSetCompletionView.swift
//  PuzzleForge
//
//  Created by Sushanth Tiruvaipati on 6/19/25.
//


import SwiftUI

struct PuzzleSetCompletionView: View {
    let puzzleSetData: OuterPuzzleData
    let earnedPoints: Int
    let onReturnHome: () -> Void

    @State private var showConfetti = false
    @State private var animateStats = false
    @State private var showRemixSheet = false
    
    var body: some View {
        ZStack {
            // Background gradient
            LinearGradient(
                colors: [
                    Color(red: 0.1, green: 0.1, blue: 0.3),
                    Color(red: 0.2, green: 0.1, blue: 0.4),
                    Color(red: 0.3, green: 0.2, blue: 0.5)
                ],
                startPoint: .topLeading,
                endPoint: .bottomTrailing
            )
            .ignoresSafeArea()
            
            // Confetti animation overlay
            if showConfetti {
                ConfettiView()
                    .allowsHitTesting(false)
            }
            
            VStack(spacing: 30) {
                Spacer()
                
                // Celebration Animation (using SF Symbols as placeholder)
                ZStack {
                    Circle()
                        .fill(Color.yellow.opacity(0.2))
                        .frame(width: 200, height: 200)
                        .scaleEffect(animateStats ? 1.2 : 1.0)
                        .animation(.easeInOut(duration: 1.5).repeatForever(autoreverses: true), value: animateStats)
                    
                    Image(systemName: "trophy.fill")
                        .font(.system(size: 80))
                        .foregroundColor(.yellow)
                        .scaleEffect(animateStats ? 1.1 : 1.0)
                        .animation(.easeInOut(duration: 1.0).repeatForever(autoreverses: true), value: animateStats)
                }
                
                // Title
                Text("Puzzle Set Complete!")
                    .font(.largeTitle)
                    .fontWeight(.bold)
                    .foregroundColor(.white)
                    .multilineTextAlignment(.center)
                    .scaleEffect(animateStats ? 1.0 : 0.8)
                    .opacity(animateStats ? 1.0 : 0.0)
                    .animation(.easeOut(duration: 0.8).delay(0.3), value: animateStats)
                
                // Stats Section
                VStack(spacing: 16) {
                    // Puzzles Completed
                    StatCard(
                        icon: "puzzlepiece.fill",
                        title: "Puzzles Completed",
                        value: "\(puzzleSetData.puzzleData.puzzles.count)",
                        color: .blue,
                        delay: 0.5
                    )
                    .scaleEffect(animateStats ? 1.0 : 0.8)
                    .opacity(animateStats ? 1.0 : 0.0)
                    .animation(.easeOut(duration: 0.8).delay(0.5), value: animateStats)
                    
                    // Topic
                    StatCard(
                        icon: "tag.fill",
                        title: "Topic",
                        value: puzzleSetData.topic,
                        color: .purple,
                        delay: 0.7
                    )
                    .scaleEffect(animateStats ? 1.0 : 0.8)
                    .opacity(animateStats ? 1.0 : 0.0)
                    .animation(.easeOut(duration: 0.8).delay(0.7), value: animateStats)
                    
                    // Points Earned
                    StatCard(
                        icon: "star.fill",
                        title: "Points Earned",
                        value: "+\(earnedPoints)",
                        color: .orange,
                        delay: 0.9
                    )
                    .scaleEffect(animateStats ? 1.0 : 0.8)
                    .opacity(animateStats ? 1.0 : 0.0)
                    .animation(.easeOut(duration: 0.8).delay(0.9), value: animateStats)
                }
                
                Spacer()
                
                // Action Buttons
                VStack(spacing: 16) {
                    // Share Button
                    Button(action: shareAchievement) {
                        HStack {
                            Image(systemName: "square.and.arrow.up")
                                .font(.title2)
                            Text("Share Achievement")
                                .font(.title3)
                                .fontWeight(.semibold)
                        }
                        .foregroundColor(.white)
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 16)
                        .background(
                            LinearGradient(
                                colors: [.blue, .purple],
                                startPoint: .leading,
                                endPoint: .trailing
                            )
                        )
                        .cornerRadius(25)
                        .shadow(color: .blue.opacity(0.3), radius: 10, x: 0, y: 5)
                    }
                    .scaleEffect(animateStats ? 1.0 : 0.8)
                    .opacity(animateStats ? 1.0 : 0.0)
                    .animation(.easeOut(duration: 0.8).delay(1.1), value: animateStats)
                    
                    // Create Your Version button
                    Button(action: { showRemixSheet = true }) {
                        HStack(spacing: 12) {
                            Image(systemName: "wand.and.stars")
                                .font(.title2)
                            VStack(alignment: .leading, spacing: 2) {
                                Text("Create Your Version")
                                    .font(.title3)
                                    .fontWeight(.semibold)
                                Text("Remix & earn real money when others play")
                                    .font(.caption)
                                    .foregroundColor(.white.opacity(0.7))
                            }
                            Spacer()
                            Image(systemName: "chevron.right")
                                .font(.caption)
                                .foregroundColor(.white.opacity(0.5))
                        }
                        .foregroundColor(.white)
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 14)
                        .padding(.horizontal, 20)
                        .background(
                            LinearGradient(
                                colors: [.purple, .blue],
                                startPoint: .leading,
                                endPoint: .trailing
                            )
                        )
                        .cornerRadius(25)
                        .shadow(color: .purple.opacity(0.3), radius: 10, x: 0, y: 5)
                    }
                    .scaleEffect(animateStats ? 1.0 : 0.8)
                    .opacity(animateStats ? 1.0 : 0.0)
                    .animation(.easeOut(duration: 0.8).delay(1.2), value: animateStats)

                    // Return Home Button
                    Button(action: onReturnHome) {
                        HStack {
                            Image(systemName: "house.fill")
                                .font(.title2)
                            Text("Return Home")
                                .font(.title3)
                                .fontWeight(.semibold)
                        }
                        .foregroundColor(.primary)
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 16)
                        .background(Color.white)
                        .cornerRadius(25)
                        .shadow(color: .black.opacity(0.1), radius: 10, x: 0, y: 5)
                    }
                    .scaleEffect(animateStats ? 1.0 : 0.8)
                    .opacity(animateStats ? 1.0 : 0.0)
                    .animation(.easeOut(duration: 0.8).delay(1.4), value: animateStats)
                }
                .padding(.horizontal, 20)
                
                Spacer(minLength: 30)
            }
        }
        .fullScreenCover(isPresented: $showRemixSheet) {
            GameRemixSheet(
                puzzleType: puzzleSetData.puzzleData.puzzles.first?.format ?? puzzleSetData.format,
                difficulty: puzzleSetData.puzzleData.puzzles.first?.difficulty ?? "Medium",
                onDismiss: { showRemixSheet = false }
            )
        }
        .navigationBarHidden(true)
        .onAppear {
            // Start animations
            withAnimation {
                showConfetti = true
                animateStats = true
            }
            
            // Stop confetti after 3 seconds
            DispatchQueue.main.asyncAfter(deadline: .now() + 3.0) {
                withAnimation {
                    showConfetti = false
                }
            }
        }
    }
    
    private func shareAchievement() {
        let shareText = "🏆 I just completed the '\(puzzleSetData.topic)' puzzle set on RiddleVerse! ✨ Solved \(puzzleSetData.puzzleData.puzzles.count) puzzles and earned \(earnedPoints) points! 🎯"
        
        let activityViewController = UIActivityViewController(
            activityItems: [shareText],
            applicationActivities: nil
        )
        
        if let windowScene = UIApplication.shared.connectedScenes.first as? UIWindowScene,
           let window = windowScene.windows.first {
            window.rootViewController?.present(activityViewController, animated: true)
        }
    }
}

struct StatCard: View {
    let icon: String
    let title: String
    let value: String
    let color: Color
    let delay: Double
    
    var body: some View {
        HStack(spacing: 16) {
            Image(systemName: icon)
                .font(.title2)
                .foregroundColor(color)
                .frame(width: 40, height: 40)
                .background(color.opacity(0.2))
                .cornerRadius(12)
            
            VStack(alignment: .leading, spacing: 4) {
                Text(title)
                    .font(.subheadline)
                    .foregroundColor(.white.opacity(0.8))
                Text(value)
                    .font(.title3)
                    .fontWeight(.bold)
                    .foregroundColor(.white)
            }
            
            Spacer()
        }
        .padding()
        .background(Color.white.opacity(0.1))
        .cornerRadius(16)
        .overlay(
            RoundedRectangle(cornerRadius: 16)
                .stroke(Color.white.opacity(0.2), lineWidth: 1)
        )
    }
}

struct PuzzleSetConfettiView: View {
    @State private var confettiPieces: [ConfettiPiece] = []
    
    var body: some View {
        GeometryReader { geometry in
            ZStack {
                ForEach(confettiPieces, id: \.id) { piece in
                    Rectangle()
                        .fill(piece.color)
                        .frame(width: piece.size, height: piece.size)
                        .rotationEffect(.degrees(piece.rotation))
                        .position(piece.position)
                        .animation(.linear(duration: piece.duration), value: piece.position)
                }
            }
        }
        .onAppear {
            generateConfetti()
        }
    }
    
    private func generateConfetti() {
        let screenWidth = UIScreen.main.bounds.width
        let screenHeight = UIScreen.main.bounds.height
        
        for _ in 0..<50 {
            let piece = ConfettiPiece(
                id: UUID(),
                color: [.red, .blue, .green, .yellow, .purple, .orange].randomElement() ?? .blue,
                size: Double.random(in: 4...8),
                position: CGPoint(
                    x: Double.random(in: 0...screenWidth),
                    y: -20
                ),
                rotation: Double.random(in: 0...360),
                duration: Double.random(in: 2...4)
            )
            confettiPieces.append(piece)
            
            // Animate falling
            DispatchQueue.main.asyncAfter(deadline: .now() + 0.1) {
                if let index = confettiPieces.firstIndex(where: { $0.id == piece.id }) {
                    confettiPieces[index].position = CGPoint(
                        x: piece.position.x + Double.random(in: -50...50),
                        y: screenHeight + 20
                    )
                }
            }
        }
    }
}

struct ConfettiPiece {
    let id: UUID
    let color: Color
    let size: Double
    var position: CGPoint
    let rotation: Double
    let duration: Double
}
