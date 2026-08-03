//
//  PuzzleCompletionView.swift
//  PuzzleForge
//
//  Enhanced version with comprehensive recommendations
//

import SwiftUI
import Foundation

// MARK: - Completion Types
enum CompletionType {
    case success(streakDays: Int, earnedPoints: Int)
    case noPuzzlesFound(puzzleType: String, difficulty: String)
    case generationInProgress(puzzleType: String, difficulty: String)
}

// MARK: - Brain Celebration Modes (matching Android)
enum BrainCelebrateMode {
    case progressRing
    case pulsingGlow
    case radiatingWaves
    case sparkles
}

// MARK: - Brain Celebration View (Android-style)
struct BrainCelebration: View {
    let mode: BrainCelebrateMode
    let progress: Float
    let size: CGFloat
    
    @State private var animationPhase: CGFloat = 0
    @State private var pulseScale: CGFloat = 1.0
    @State private var sparkles: [Sparkle] = []
    
    var body: some View {
        ZStack {
            // Background effects
            switch mode {
            case .pulsingGlow:
                PulsingGlowEffect(pulseScale: pulseScale)
            case .radiatingWaves:
                RadiatingWavesEffect(animationPhase: animationPhase)
            case .sparkles:
                SparklesBackground(sparkles: sparkles)
            case .progressRing:
                EmptyView()
            }
            
            // Brain image
            Image(systemName: "brain.head.profile")
                .resizable()
                .scaledToFit()
                .frame(width: size * 0.82, height: size * 0.82)
                .foregroundColor(.white)
                .scaleEffect(pulseScale)
            
            // Foreground effects
            if mode == .progressRing {
                ProgressRingEffect(progress: progress, size: size)
            }
        }
        .frame(width: size, height: size)
        .onAppear {
            startAnimations()
        }
    }
    
    private func startAnimations() {
        // Pulse animation
        withAnimation(.easeInOut(duration: 1.4).repeatForever(autoreverses: true)) {
            pulseScale = 1.01
        }
        
        // Wave animation
        withAnimation(.linear(duration: 1.6).repeatForever(autoreverses: false)) {
            animationPhase = 1.0
        }
        
        // Sparkles generation
        if mode == .sparkles {
            Timer.scheduledTimer(withTimeInterval: 0.1, repeats: true) { _ in
                if sparkles.count < 18 {
                    sparkles.append(Sparkle.random(in: size))
                }
                sparkles = sparkles.filter { $0.age < $0.lifetime }
                for i in sparkles.indices {
                    sparkles[i].update()
                }
            }
        }
    }
}

// MARK: - Pulsing Glow Effect
struct PulsingGlowEffect: View {
    let pulseScale: CGFloat
    
    var body: some View {
        ZStack {
            ForEach(0..<3) { i in
                Circle()
                    .stroke(Color.cyan.opacity(0.28 - Double(i) * 0.07), lineWidth: 2)
                    .scaleEffect(pulseScale * (1.0 + CGFloat(i) * 0.2))
            }
        }
    }
}

// MARK: - Radiating Waves Effect
struct RadiatingWavesEffect: View {
    let animationPhase: CGFloat
    
    var body: some View {
        GeometryReader { geometry in
            Canvas { context, size in
                let center = CGPoint(x: size.width / 2, y: size.height / 2)
                let baseRadius = min(size.width, size.height) * 0.16
                
                for i in 0..<4 {
                    let phase = (animationPhase + CGFloat(i) / 4.0).truncatingRemainder(dividingBy: 1.0)
                    let radius = baseRadius + phase * min(size.width, size.height) * 0.35
                    let alpha = (1.0 - phase) * 0.55
                    
                    var path = Path()
                    path.addEllipse(in: CGRect(
                        x: center.x - radius,
                        y: center.y - radius,
                        width: radius * 2,
                        height: radius * 2
                    ))
                    
                    context.stroke(
                        path,
                        with: .color(Color.cyan.opacity(alpha)),
                        lineWidth: 2
                    )
                }
            }
        }
    }
}

// MARK: - Sparkles Background
struct SparklesBackground: View {
    let sparkles: [Sparkle]
    
    var body: some View {
        GeometryReader { geometry in
            Canvas { context, size in
                for sparkle in sparkles {
                    let center = CGPoint(
                        x: size.width * sparkle.position.x,
                        y: size.height * sparkle.position.y
                    )
                    let alpha = 1.0 - (sparkle.age / sparkle.lifetime)
                    let radius = (2.5 + 1.5 * (1.0 - alpha)) * 2
                    
                    var path = Path()
                    path.addEllipse(in: CGRect(
                        x: center.x - radius / 2,
                        y: center.y - radius / 2,
                        width: radius,
                        height: radius
                    ))
                    
                    context.fill(
                        path,
                        with: .color(Color.yellow.opacity(alpha))
                    )
                }
            }
        }
    }
}

// MARK: - Progress Ring Effect
struct ProgressRingEffect: View {
    let progress: Float
    let size: CGFloat
    
    @State private var animatedProgress: CGFloat = 0
    
    var body: some View {
        ZStack {
            // Background track
            Circle()
                .stroke(Color.white.opacity(0.18), lineWidth: 10)
                .frame(width: size - 8, height: size - 8)
            
            // Progress arc
            Circle()
                .trim(from: 0, to: animatedProgress)
                .stroke(
                    Color.green,
                    style: StrokeStyle(lineWidth: 10, lineCap: .round)
                )
                .frame(width: size - 8, height: size - 8)
                .rotationEffect(.degrees(-90))
        }
        .onAppear {
            withAnimation(.easeInOut(duration: 0.8)) {
                animatedProgress = CGFloat(progress)
            }
        }
    }
}

// MARK: - Sparkle Data Structure
struct Sparkle {
    var position: CGPoint
    var velocity: CGPoint
    var age: TimeInterval
    var lifetime: TimeInterval
    
    mutating func update() {
        age += 0.016 // ~60fps
        position.x += velocity.x
        position.y += velocity.y
    }
    
    static func random(in size: CGFloat) -> Sparkle {
        let angle = Double.random(in: 0...(2 * .pi))
        let distance = CGFloat.random(in: 0...(size * 0.32))
        let centerOffset = CGPoint(
            x: 0.5 + CGFloat(Darwin.cos(angle)) * distance / size,
            y: 0.5 + CGFloat(Darwin.sin(angle)) * distance / size
        )
        
        return Sparkle(
            position: centerOffset,
            velocity: CGPoint(
                x: (CGFloat.random(in: -1...1) * 0.0025),
                y: (-0.003 - CGFloat.random(in: 0...0.003))
            ),
            age: 0,
            lifetime: 1.2 + Double.random(in: 0...0.8)
        )
    }
}

// MARK: - Animated Brain Puzzle (Android-style)
struct AnimatedBrainPuzzle: View {
    @State private var selectedMode: BrainCelebrateMode = .progressRing
    @State private var showCelebration = false
    @State private var celebrationStage = 0
    @Binding var animationComplete: Bool
    
    let modes: [BrainCelebrateMode] = [.progressRing, .pulsingGlow, .radiatingWaves]
    
    var body: some View {
        ZStack {
            if showCelebration {
                CelebrationParticles(stage: celebrationStage)
            }
            
            BrainCelebration(
                mode: selectedMode,
                progress: 1.0,
                size: 220
            )
        }
        .frame(width: 240, height: 240)
        .onAppear {
            startAnimation()
        }
    }
    
    private func startAnimation() {
        // Pick random mode
        selectedMode = modes.randomElement() ?? .progressRing
        
        // Start celebration sequence
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.5) {
            withAnimation {
                showCelebration = true
            }
        }
        
        for step in 1...3 {
            DispatchQueue.main.asyncAfter(deadline: .now() + Double(step) * 0.5) {
                withAnimation {
                    celebrationStage = step
                }
            }
        }
        
        // Mark animation as complete after 2.5 seconds
        DispatchQueue.main.asyncAfter(deadline: .now() + 2.5) {
            withAnimation {
                animationComplete = true
            }
        }
    }
}

// MARK: - Celebration Particles
struct CelebrationParticles: View {
    let stage: Int
    
    var body: some View {
        Canvas { context, size in
            let centerX = size.width / 2
            let centerY = size.height / 2
            
            for i in 0..<24 {
                let angle = Double(i * 15) * .pi / 180.0
                let radius: CGFloat = {
                    switch stage {
                    case 1: return 60
                    case 2: return 80
                    case 3: return 100
                    default: return 40
                    }
                }()
                
                let x = centerX + CGFloat(Darwin.cos(angle)) * radius
                let y = centerY + CGFloat(Darwin.sin(angle)) * radius
                
                let particleColor: Color = {
                    switch i % 4 {
                    case 0: return .yellow
                    case 1: return .green
                    case 2: return .blue
                    default: return .orange
                    }
                }()
                
                let particleRadius: CGFloat = {
                    switch stage {
                    case 1: return 2
                    case 2: return 3
                    case 3: return 4
                    default: return 1
                    }
                }()
                
                var path = Path()
                path.addEllipse(in: CGRect(
                    x: x - particleRadius,
                    y: y - particleRadius,
                    width: particleRadius * 2,
                    height: particleRadius * 2
                ))
                
                context.fill(path, with: .color(particleColor.opacity(0.8)))
                
                // Add sparkle for stage 3
                if stage >= 3 && i % 2 == 0 {
                    var sparklePath = Path()
                    let sparkleRadius = particleRadius * 0.5
                    sparklePath.addEllipse(in: CGRect(
                        x: x - sparkleRadius,
                        y: y - sparkleRadius,
                        width: sparkleRadius * 2,
                        height: sparkleRadius * 2
                    ))
                    context.fill(sparklePath, with: .color(Color.white.opacity(0.6)))
                }
            }
        }
    }
}

// MARK: - Recommended Puzzle Card
struct RecommendedPuzzleCardOriginal: View {
    let puzzleType: String
    let icon: String
    let description: String
    let difficulty: String
    var neverPlayed: Bool = false
    let onTap: () -> Void
    
    @State private var isNavigating = false
    
    var body: some View {
        Button(action: {
            guard !isNavigating else { return }
            
            isNavigating = true
            
            DispatchQueue.main.asyncAfter(deadline: .now() + 0.15) {
                onTap()
                
                DispatchQueue.main.asyncAfter(deadline: .now() + 0.5) {
                    isNavigating = false
                }
            }
        }) {
            VStack(alignment: .leading, spacing: 12) {
                HStack {
                    ZStack {
                        Circle()
                            .fill(LinearGradient(
                                colors: [Color.blue.opacity(0.8), Color.purple.opacity(0.8)],
                                startPoint: .topLeading,
                                endPoint: .bottomTrailing
                            ))
                            .frame(width: 50, height: 50)
                        
                        Image(systemName: icon)
                            .font(.system(size: 24))
                            .foregroundColor(.white)
                    }
                    
                    VStack(alignment: .leading, spacing: 4) {
                        HStack(spacing: 6) {
                            Text(getPuzzleDisplayName(puzzleType))
                                .font(.headline)
                                .fontWeight(.bold)
                                .foregroundColor(.white)
                            if neverPlayed {
                                Text("✨ New to You")
                                    .font(.system(size: 10, weight: .bold))
                                    .foregroundColor(.white)
                                    .padding(.horizontal, 7)
                                    .padding(.vertical, 3)
                                    .background(Capsule().fill(Color(red: 0.49, green: 0.30, blue: 1.0)))
                            }
                        }
                        Text(difficulty.capitalized)
                            .font(.caption)
                            .foregroundColor(.gray)
                    }

                    Spacer()

                    Image(systemName: "arrow.right.circle.fill")
                        .font(.title2)
                        .foregroundColor(.white.opacity(0.8))
                }

                Text(neverPlayed ? "You've never tried this — give it a go!" : description)
                    .font(.subheadline)
                    .foregroundColor(.white.opacity(0.8))
                    .lineLimit(2)
            }
            .padding(16)
            .background(
                RoundedRectangle(cornerRadius: 12)
                    .fill(Color.white.opacity(0.1))
                    .overlay(
                        RoundedRectangle(cornerRadius: 12)
                            .stroke(Color.white.opacity(0.2), lineWidth: 1)
                    )
            )
            .opacity(isNavigating ? 0.6 : 1.0)
            .scaleEffect(isNavigating ? 0.98 : 1.0)
            .animation(.easeInOut(duration: 0.15), value: isNavigating)
        }
        .buttonStyle(PlainButtonStyle())
        .disabled(isNavigating)
    }
}

// MARK: - Main Puzzle Completion View
struct PuzzleCompletionView: View {
    let completionType: CompletionType
    let isCustomPuzzle: Bool
    let customPuzzleId: String?
    let sessionStats: SessionStatistics?
    let puzzleType: String
    
    let onReturnHome: () -> Void
    let onViewLeaderboard: (String) -> Void
    let onStartPuzzle: (String, String) -> Void
    
    @StateObject private var statsManager = UserStatsManager.shared
    @State private var animationComplete = false
    
    var body: some View {
        GeometryReader { geometry in
            ZStack {
                // Background gradient
                backgroundGradient
                
                switch completionType {
                case .success(let streakDays, let earnedPoints):
                    successContent(
                        streakDays: streakDays,
                        earnedPoints: earnedPoints,
                        geometry: geometry
                    )
                    
                case .noPuzzlesFound(let puzzleType, let difficulty):
                    noPuzzlesContent(
                        puzzleType: puzzleType,
                        difficulty: difficulty,
                        geometry: geometry
                    )
                    
                case .generationInProgress(let puzzleType, let difficulty):
                    generationInProgressContent(
                        puzzleType: puzzleType,
                        difficulty: difficulty,
                        geometry: geometry
                    )
                }
            }
        }
        .navigationBarHidden(true)
    }
    
    // MARK: - Background Gradient
    private var backgroundGradient: some View {
        LinearGradient(
            colors: gradientColors,
            startPoint: .topLeading,
            endPoint: .bottomTrailing
        )
        .ignoresSafeArea()
    }
    
    private var gradientColors: [Color] {
        switch completionType {
        case .success:
            return [Color(red: 0.1, green: 0.1, blue: 0.1), Color(red: 0.18, green: 0.18, blue: 0.18)]
        case .noPuzzlesFound:
            return [Color.orange, Color.orange.opacity(0.7)]
        case .generationInProgress:
            return [Color.blue, Color.blue.opacity(0.7)]
        }
    }
    
    // MARK: - Success Content
    @ViewBuilder
    private func successContent(streakDays: Int, earnedPoints: Int, geometry: GeometryProxy) -> some View {
        ScrollView {
            VStack(spacing: 20) {
                Spacer().frame(height: 40)
                
                // Animated brain puzzle (Android-style)
                AnimatedBrainPuzzle(animationComplete: $animationComplete)
                
                // Title
                Text(isCustomPuzzle ? "Custom Puzzle Complete!" : "Puzzle Complete!")
                    .font(.largeTitle)
                    .fontWeight(.bold)
                    .foregroundColor(.white)
                    .multilineTextAlignment(.center)
                
                // Recommended puzzles section - show after animation
                if animationComplete {
                    recommendedPuzzlesSection()
                        .transition(.opacity.combined(with: .move(edge: .bottom)))
                }
                
                // Points and session info
                pointsAndSessionCard(streakDays: streakDays, earnedPoints: earnedPoints)
                
                // Statistics card
                puzzleStatisticsCard()
                
                Spacer().frame(height: 100) // Space for floating buttons
            }
            .padding(.horizontal, 24)
        }
        
        // Floating action buttons
        floatingActionButtons(geometry: geometry)
    }
    
    // MARK: - Points and Session Card
    private func pointsAndSessionCard(streakDays: Int, earnedPoints: Int) -> some View {
        VStack(spacing: 16) {
            // Points earned
            HStack {
                Image(systemName: "star.fill")
                    .foregroundColor(.yellow)
                    .font(.title2)
                
                Text("Earned: \(earnedPoints) Points")
                    .font(.title3)
                    .fontWeight(.bold)
                    .foregroundColor(.primary)
            }
            
            if !isCustomPuzzle {
                HStack {
                    Image(systemName: "flame.fill")
                        .foregroundColor(.orange)
                        .font(.title2)
                    
                    Text("Streak: \(streakDays) Days")
                        .font(.title3)
                        .fontWeight(.medium)
                        .foregroundColor(.primary)
                }
            }
            
            // Session statistics if available
            if let stats = sessionStats {
                Divider()
                    .background(Color.gray.opacity(0.3))
                
                Text("📊 Session Performance")
                    .font(.headline)
                    .fontWeight(.bold)
                
                HStack(spacing: 20) {
                    SessionStatItem(
                        label: "Correct",
                        value: "\(stats.correctAnswers)/\(stats.totalAnswers)"
                    )
                    
                    SessionStatItem(
                        label: "Accuracy",
                        value: "\(Int(stats.winRate * 100))%"
                    )
                    
                    SessionStatItem(
                        label: "Time",
                        value: formatTime(stats.totalTimeSeconds)
                    )
                }
                
                if stats.bestStreak > 1 {
                    Text("⚡ Best streak this session: \(stats.bestStreak)")
                        .font(.subheadline)
                        .foregroundColor(.green)
                        .fontWeight(.medium)
                }
            }
        }
        .padding(24)
        .background(Color.white.opacity(0.9))
        .cornerRadius(16)
    }
    
    // MARK: - Session Stat Item
    private func SessionStatItem(label: String, value: String) -> some View {
        VStack(spacing: 4) {
            Text(value)
                .font(.headline)
                .fontWeight(.bold)
                .foregroundColor(.blue)
            
            Text(label)
                .font(.caption)
                .foregroundColor(.gray)
        }
    }
    
    // MARK: - Puzzle Statistics Card
    private func puzzleStatisticsCard() -> some View {
        VStack(spacing: 16) {
            Text("\(getPuzzleDisplayName(puzzleType)) Statistics")
                .font(.title2)
                .fontWeight(.bold)
                .foregroundColor(.white)
            
            let stats = statsManager.getPuzzleStats(puzzleType: puzzleType)
            
            if stats.totalPlays > 0 {
                VStack(spacing: 12) {
                    // Main stats
                    HStack(spacing: 12) {
                        CompletionStatCard(
                            title: "HIGH SCORE",
                            value: "\(stats.highScore)"
                        )
                        
                        CompletionStatCard(
                            title: "ACCURACY",
                            value: "\(Int(stats.winRate * 100))%"
                        )
                    }
                    
                    HStack(spacing: 12) {
                        CompletionStatCard(
                            title: "TIME TRAINED",
                            value: String(format: "%.1f hrs", stats.totalTimeSpentHours)
                        )
                        
                        CompletionStatCard(
                            title: "TOTAL WINS",
                            value: "\(stats.wins)"
                        )
                    }
                    
                    // Top scores if available
                    if !stats.topScores.isEmpty {
                        topScoresSection(topScores: stats.topScores)
                    }
                }
            } else {
                // First time playing message
                VStack(spacing: 8) {
                    Text("🚀 Start Your Journey!")
                        .font(.headline)
                        .fontWeight(.bold)
                        .foregroundColor(.white)
                    
                    Text("Play more puzzles to unlock detailed statistics")
                        .font(.caption)
                        .foregroundColor(.gray)
                        .multilineTextAlignment(.center)
                }
                .padding()
                .background(Color.black.opacity(0.3))
                .cornerRadius(12)
            }
        }
        .padding(20)
        .background(Color.black.opacity(0.4))
        .cornerRadius(16)
    }
    
    // MARK: - Completion Stat Card
    private func CompletionStatCard(title: String, value: String) -> some View {
        VStack(spacing: 4) {
            Text(value)
                .font(.title3)
                .fontWeight(.bold)
                .foregroundColor(.white)
                .multilineTextAlignment(.center)
            
            Text(title)
                .font(.caption2)
                .foregroundColor(.gray)
                .multilineTextAlignment(.center)
        }
        .frame(maxWidth: .infinity)
        .frame(height: 60)
        .background(Color.black.opacity(0.3))
        .cornerRadius(8)
    }
    
    // MARK: - Top Scores Section
    private func topScoresSection(topScores: [Int]) -> some View {
        VStack(alignment: .leading, spacing: 8) {
            Text("TOP 5 SCORES")
                .font(.caption)
                .fontWeight(.bold)
                .foregroundColor(.gray)
            
            LazyVGrid(columns: Array(repeating: GridItem(.flexible()), count: 1), spacing: 4) {
                ForEach(Array(topScores.prefix(5).enumerated()), id: \.offset) { index, score in
                    HStack {
                        Text("\(index + 1).")
                            .font(.caption)
                            .foregroundColor(.gray)
                            .frame(width: 20, alignment: .leading)
                        
                        Text("\(score)")
                            .font(.caption)
                            .fontWeight(.medium)
                            .foregroundColor(.white)
                        
                        Spacer()
                    }
                    .padding(.vertical, 2)
                    
                    if index < min(topScores.count - 1, 4) {
                        Divider()
                            .background(Color.gray.opacity(0.3))
                    }
                }
            }
            .padding(12)
            .background(Color.black.opacity(0.3))
            .cornerRadius(8)
        }
    }
    
    // MARK: - Recommended Puzzles Section
    @ViewBuilder
    private func recommendedPuzzlesSection() -> some View {
        VStack(alignment: .leading, spacing: 16) {
            HStack {
                Image(systemName: "sparkles")
                    .foregroundColor(.yellow)
                Text("Try These Next")
                    .font(.title2)
                    .fontWeight(.bold)
                    .foregroundColor(.white)
            }
            
            VStack(spacing: 12) {
                ForEach(getRecommendedPuzzles(), id: \.type) { recommendation in
                    let neverPlayed = statsManager.getPuzzleStats(puzzleType: recommendation.type).totalPlays == 0
                    RecommendedPuzzleCardOriginal(
                        puzzleType: recommendation.type,
                        icon: recommendation.icon,
                        description: recommendation.description,
                        difficulty: recommendation.difficulty,
                        neverPlayed: neverPlayed,
                        onTap: {
                            onStartPuzzle(recommendation.type, recommendation.difficulty)
                        }
                    )
                }
            }
        }
        .padding(20)
        .background(Color.black.opacity(0.3))
        .cornerRadius(16)
    }
    
    // MARK: - Get Recommended Puzzles (COMPREHENSIVE)
    private func getRecommendedPuzzles() -> [PuzzleRecommendation] {
        let currentType = puzzleType.lowercased()
        
        // Comprehensive puzzle recommendations covering ALL puzzles from CategoryData
        let recommendations: [String: [PuzzleRecommendation]] = [
            // Math puzzles
            "math": [
                PuzzleRecommendation(type: "mathestimation", icon: "chart.line.uptrend.xyaxis", description: "Practice estimation skills", difficulty: "medium"),
                PuzzleRecommendation(type: "mathcrossword", icon: "grid.circle.fill", description: "Solve number equation grids", difficulty: "medium")
            ],
            "mathexpression": [
                PuzzleRecommendation(type: "math", icon: "function", description: "Solve mathematical problems", difficulty: "medium"),
                PuzzleRecommendation(type: "mathcomparison", icon: "equal.circle.fill", description: "Compare mathematical values", difficulty: "medium")
            ],
            "mathestimation": [
                PuzzleRecommendation(type: "average", icon: "chart.bar.fill", description: "Calculate averages", difficulty: "medium"),
                PuzzleRecommendation(type: "percentages", icon: "percent", description: "Master percentages", difficulty: "medium")
            ],
            "mathcrossword": [
                PuzzleRecommendation(type: "math", icon: "function", description: "Practice more math", difficulty: "medium"),
                PuzzleRecommendation(type: "numbersequence", icon: "123.rectangle.fill", description: "Tap numbers in order", difficulty: "medium")
            ],
            "mathcomparison": [
                PuzzleRecommendation(type: "mathestimation", icon: "chart.line.uptrend.xyaxis", description: "Estimate values", difficulty: "medium"),
                PuzzleRecommendation(type: "math", icon: "function", description: "Solve more math puzzles", difficulty: "medium")
            ],
            "average": [
                PuzzleRecommendation(type: "division", icon: "divide.circle.fill", description: "Practice division", difficulty: "medium"),
                PuzzleRecommendation(type: "math", icon: "function", description: "General math challenges", difficulty: "medium")
            ],
            "division": [
                PuzzleRecommendation(type: "subtraction", icon: "minus.circle.fill", description: "Practice subtraction", difficulty: "medium"),
                PuzzleRecommendation(type: "math", icon: "function", description: "More math problems", difficulty: "medium")
            ],
            "percentages": [
                PuzzleRecommendation(type: "discounts", icon: "tag.fill", description: "Calculate discounted prices", difficulty: "medium"),
                PuzzleRecommendation(type: "mathtipping", icon: "dollarsign.circle.fill", description: "Calculate tips", difficulty: "medium")
            ],
            "discounts": [
                PuzzleRecommendation(type: "purchasing", icon: "creditcard.fill", description: "Compare subscription costs", difficulty: "medium"),
                PuzzleRecommendation(type: "percentages", icon: "percent", description: "Practice percentages", difficulty: "medium")
            ],
            "purchasing": [
                PuzzleRecommendation(type: "conversion", icon: "arrow.left.arrow.right", description: "Unit conversions", difficulty: "medium"),
                PuzzleRecommendation(type: "mathestimation", icon: "chart.line.uptrend.xyaxis", description: "Estimate values", difficulty: "medium")
            ],
            "conversion": [
                PuzzleRecommendation(type: "math", icon: "function", description: "General math practice", difficulty: "medium"),
                PuzzleRecommendation(type: "mathestimation", icon: "chart.line.uptrend.xyaxis", description: "Estimation challenges", difficulty: "medium")
            ],
            "subtraction": [
                PuzzleRecommendation(type: "division", icon: "divide.circle.fill", description: "Try division", difficulty: "medium"),
                PuzzleRecommendation(type: "math", icon: "function", description: "Mixed math problems", difficulty: "medium")
            ],
            "mathtipping": [
                PuzzleRecommendation(type: "percentages", icon: "percent", description: "Practice percentage calculations", difficulty: "medium"),
                PuzzleRecommendation(type: "purchasing", icon: "creditcard.fill", description: "Calculate costs", difficulty: "medium")
            ],
            "numbersequence": [
                PuzzleRecommendation(type: "numbersum", icon: "plus.circle.fill", description: "Find numbers that add up", difficulty: "medium"),
                PuzzleRecommendation(type: "math", icon: "function", description: "General math puzzles", difficulty: "medium")
            ],
            "numbersum": [
                PuzzleRecommendation(type: "mathcomparison", icon: "equal.circle.fill", description: "Compare values", difficulty: "medium"),
                PuzzleRecommendation(type: "math", icon: "function", description: "More math challenges", difficulty: "medium")
            ],
            
            // Word puzzles
            "crossword": [
                PuzzleRecommendation(type: "wordsearch", icon: "text.magnifyingglass", description: "Find hidden words", difficulty: "medium"),
                PuzzleRecommendation(type: "anagram", icon: "textformat.abc", description: "Unscramble words", difficulty: "medium")
            ],
            "anagram": [
                PuzzleRecommendation(type: "synonyms", icon: "link.circle.fill", description: "Group similar words", difficulty: "medium"),
                PuzzleRecommendation(type: "wordprefix", icon: "textformat.alt", description: "Find words with prefixes", difficulty: "medium")
            ],
            "synonyms": [
                PuzzleRecommendation(type: "antonyms", icon: "arrow.left.arrow.right.circle.fill", description: "Match opposite words", difficulty: "medium"),
                PuzzleRecommendation(type: "connotationwords", icon: "brain.head.profile", description: "Sort words by tone", difficulty: "medium")
            ],
            "antonyms": [
                PuzzleRecommendation(type: "synonyms", icon: "link.circle.fill", description: "Group similar words", difficulty: "medium"),
                PuzzleRecommendation(type: "wordsnake", icon: "link", description: "Connect word chains", difficulty: "medium")
            ],
            "wordsnake": [
                PuzzleRecommendation(type: "wordsearch", icon: "text.magnifyingglass", description: "Find hidden words", difficulty: "medium"),
                PuzzleRecommendation(type: "anagram", icon: "textformat.abc", description: "Unscramble words", difficulty: "medium")
            ],
            "wordsearch": [
                PuzzleRecommendation(type: "crossword", icon: "grid.circle.fill", description: "Solve word puzzles", difficulty: "medium"),
                PuzzleRecommendation(type: "wordprefix", icon: "textformat.alt", description: "Find prefixed words", difficulty: "medium")
            ],
            "wordprefix": [
                PuzzleRecommendation(type: "anagram", icon: "textformat.abc", description: "Unscramble words", difficulty: "medium"),
                PuzzleRecommendation(type: "synonyms", icon: "link.circle.fill", description: "Find similar words", difficulty: "medium")
            ],
            "connotationwords": [
                PuzzleRecommendation(type: "synonyms", icon: "link.circle.fill", description: "Group similar words", difficulty: "medium"),
                PuzzleRecommendation(type: "antonyms", icon: "arrow.left.arrow.right.circle.fill", description: "Match opposites", difficulty: "medium")
            ],
            
            // Memory puzzles
            "memorysquares": [
                PuzzleRecommendation(type: "triangledotmemory", icon: "triangle.fill", description: "Remember dot positions", difficulty: "medium"),
                PuzzleRecommendation(type: "memorypreviouspair", icon: "brain.head.profile", description: "Recall previous pairs", difficulty: "medium")
            ],
            "memorystory": [
                PuzzleRecommendation(type: "memoryretention", icon: "brain.head.profile", description: "Audio categorization", difficulty: "medium"),
                PuzzleRecommendation(type: "memorysequencing", icon: "brain.head.profile", description: "Sequence memory", difficulty: "medium")
            ],
            "memoryretention": [
                PuzzleRecommendation(type: "memorystory", icon: "brain.head.profile", description: "Story memory challenges", difficulty: "medium"),
                PuzzleRecommendation(type: "memorysequencing", icon: "brain.head.profile", description: "Sequencing puzzles", difficulty: "medium")
            ],
            "memorysequencing": [
                PuzzleRecommendation(type: "memorystory", icon: "brain.head.profile", description: "Audio memory", difficulty: "medium"),
                PuzzleRecommendation(type: "memorysquares", icon: "grid.circle.fill", description: "Pattern recognition", difficulty: "medium")
            ],
            "memorypreviouspair": [
                PuzzleRecommendation(type: "memoryprevioussingle", icon: "brain.head.profile", description: "Single symbol recall", difficulty: "medium"),
                PuzzleRecommendation(type: "contextswitch", icon: "brain.head.profile.fill", description: "Memory interference", difficulty: "medium")
            ],
            "memoryprevioussingle": [
                PuzzleRecommendation(type: "memorypreviouspair", icon: "brain.head.profile", description: "Pair recall", difficulty: "medium"),
                PuzzleRecommendation(type: "dualtask", icon: "brain.head.profile.fill", description: "Switch between tasks", difficulty: "medium")
            ],
            "triangledotmemory": [
                PuzzleRecommendation(type: "memorysquares", icon: "grid.circle.fill", description: "Square patterns", difficulty: "medium"),
                PuzzleRecommendation(type: "imagevortex", icon: "sparkles", description: "Find new images", difficulty: "medium")
            ],
            "imagequestion": [
                PuzzleRecommendation(type: "imagepuzzle", icon: "puzzlepiece.extension.fill", description: "Assemble image pieces", difficulty: "medium"),
                PuzzleRecommendation(type: "progressiverevelation", icon: "puzzlepiece.extension.fill", description: "Guess before reveal", difficulty: "medium")
            ],
            "imagepuzzle": [
                PuzzleRecommendation(type: "imagequestion", icon: "photo.on.rectangle.angled", description: "Study and answer", difficulty: "medium"),
                PuzzleRecommendation(type: "imagevortex", icon: "sparkles", description: "Spot new images", difficulty: "medium")
            ],
            "imagevortex": [
                PuzzleRecommendation(type: "colorshapematching", icon: "paintbrush.fill", description: "Visual recognition", difficulty: "medium"),
                PuzzleRecommendation(type: "oddoneout", icon: "eye.circle.fill", description: "Find differences", difficulty: "medium")
            ],
            "dualtask": [
                PuzzleRecommendation(type: "contextswitch", icon: "brain.head.profile.fill", description: "Memory and interference", difficulty: "medium"),
                PuzzleRecommendation(type: "colortextmatching", icon: "paintpalette.fill", description: "Match color and text", difficulty: "medium")
            ],
            "contextswitch": [
                PuzzleRecommendation(type: "dualtask", icon: "brain.head.profile.fill", description: "Task switching", difficulty: "medium"),
                PuzzleRecommendation(type: "symbolswipe", icon: "arrow.left.arrow.right.circle.fill", description: "Swipe by symbols", difficulty: "medium")
            ],
            
            // Logic puzzles
            "trivia": [
                PuzzleRecommendation(type: "storypuzzle", icon: "book.fill", description: "Solve narrative mysteries", difficulty: "medium"),
                PuzzleRecommendation(type: "progressiverevelation", icon: "puzzlepiece.extension.fill", description: "Guess before reveal", difficulty: "medium")
            ],
            "storypuzzle": [
                PuzzleRecommendation(type: "trivia", icon: "questionmark.circle.fill", description: "Test general knowledge", difficulty: "medium"),
                PuzzleRecommendation(type: "crypto", icon: "lock.fill", description: "Decode messages", difficulty: "medium")
            ],
            "crypto": [
                PuzzleRecommendation(type: "wordsnake", icon: "link", description: "Connect word chains", difficulty: "medium"),
                PuzzleRecommendation(type: "storypuzzle", icon: "book.fill", description: "Narrative puzzles", difficulty: "medium")
            ],
            "progressiverevelation": [
                PuzzleRecommendation(type: "realorai", icon: "photo.fill.on.rectangle.fill", description: "Identify AI vs real", difficulty: "medium"),
                PuzzleRecommendation(type: "find_object", icon: "eye.fill", description: "AI vision discovery", difficulty: "medium")
            ],
            "realorai": [
                PuzzleRecommendation(type: "progressiverevelation", icon: "puzzlepiece.extension.fill", description: "Guess progressively", difficulty: "medium"),
                PuzzleRecommendation(type: "imagequestion", icon: "photo.on.rectangle.angled", description: "Image analysis", difficulty: "medium")
            ],
            "find_object": [
                PuzzleRecommendation(type: "uniqueobject", icon: "eye.circle.fill", description: "Find differences", difficulty: "medium"),
                PuzzleRecommendation(type: "oddoneout", icon: "eye.circle.fill", description: "Spot the odd one", difficulty: "medium")
            ],
            
            // Visual puzzles
            "oddoneout": [
                PuzzleRecommendation(type: "uniqueobject", icon: "eye.circle.fill", description: "Find unique objects", difficulty: "medium"),
                PuzzleRecommendation(type: "colorshapematching", icon: "paintbrush.fill", description: "Match colors and shapes", difficulty: "medium")
            ],
            "flowpuzzle": [
                PuzzleRecommendation(type: "symmetry", icon: "arrow.left.arrow.right", description: "Mirror patterns", difficulty: "medium"),
                PuzzleRecommendation(type: "pinballdeflector", icon: "target", description: "Physics prediction", difficulty: "medium")
            ],
            "symmetry": [
                PuzzleRecommendation(type: "flowpuzzle", icon: "arrow.triangle.branch", description: "Connect colors", difficulty: "medium"),
                PuzzleRecommendation(type: "memorysquares", icon: "grid.circle.fill", description: "Pattern memory", difficulty: "medium")
            ],
            "colorshapematching": [
                PuzzleRecommendation(type: "colortextmatching", icon: "paintpalette.fill", description: "Color and text", difficulty: "medium"),
                PuzzleRecommendation(type: "oddoneout", icon: "eye.circle.fill", description: "Find differences", difficulty: "medium")
            ],
            "colortextmatching": [
                PuzzleRecommendation(type: "colorshapematching", icon: "paintbrush.fill", description: "Shape recognition", difficulty: "medium"),
                PuzzleRecommendation(type: "symbolswipe", icon: "arrow.left.arrow.right.circle.fill", description: "Symbol swiping", difficulty: "medium")
            ],
            "uniqueobject": [
                PuzzleRecommendation(type: "oddoneout", icon: "eye.circle.fill", description: "Spot differences", difficulty: "medium"),
                PuzzleRecommendation(type: "find_object", icon: "eye.fill", description: "Object discovery", difficulty: "medium")
            ],
            "symbolswipe": [
                PuzzleRecommendation(type: "colortextmatching", icon: "paintpalette.fill", description: "Color and text matching", difficulty: "medium"),
                PuzzleRecommendation(type: "dualtask", icon: "brain.head.profile.fill", description: "Task switching", difficulty: "medium")
            ],
            "pinballdeflector": [
                PuzzleRecommendation(type: "flowpuzzle", icon: "arrow.triangle.branch", description: "Path puzzles", difficulty: "medium"),
                PuzzleRecommendation(type: "mathexpression", icon: "function", description: "Math challenges", difficulty: "medium")
            ],
            
            // Geography puzzles
            "geography_cities": [
                PuzzleRecommendation(type: "geography_countries", icon: "map.fill", description: "Place countries", difficulty: "medium"),
                PuzzleRecommendation(type: "trivia", icon: "questionmark.circle.fill", description: "General knowledge", difficulty: "medium")
            ],
            "geography_countries": [
                PuzzleRecommendation(type: "geography_cities", icon: "building.2.fill", description: "Place cities", difficulty: "medium"),
                PuzzleRecommendation(type: "trivia", icon: "questionmark.circle.fill", description: "Test your knowledge", difficulty: "medium")
            ],
            
            // Music puzzle
            "musicidentification": [
                PuzzleRecommendation(type: "memorystory", icon: "brain.head.profile", description: "Audio memory", difficulty: "medium"),
                PuzzleRecommendation(type: "trivia", icon: "questionmark.circle.fill", description: "General knowledge", difficulty: "medium")
            ]
        ]
        
        let candidates = recommendations[currentType] ?? [
            PuzzleRecommendation(type: "trivia", icon: "questionmark.circle.fill", description: "Test your general knowledge", difficulty: "medium"),
            PuzzleRecommendation(type: "math", icon: "function", description: "Sharpen your calculation skills", difficulty: "medium")
        ]

        // Sort: never-played types bubble to the top
        return candidates.sorted { a, b in
            let aPlayed = statsManager.getPuzzleStats(puzzleType: a.type).totalPlays > 0
            let bPlayed = statsManager.getPuzzleStats(puzzleType: b.type).totalPlays > 0
            if aPlayed == bPlayed { return false }
            return !aPlayed // never-played first
        }
    }
    
    // MARK: - No Puzzles Content
    @ViewBuilder
    private func noPuzzlesContent(puzzleType: String, difficulty: String, geometry: GeometryProxy) -> some View {
        ScrollView {
            VStack(spacing: 24) {
                Spacer().frame(height: 60)
                
                Image(systemName: "exclamationmark.triangle.fill")
                    .font(.system(size: 80))
                    .foregroundColor(.white)
                
                VStack(spacing: 16) {
                    Text("No More Puzzles Available")
                        .font(.largeTitle)
                        .fontWeight(.bold)
                        .foregroundColor(.white)
                        .multilineTextAlignment(.center)
                    
                    Text("You've completed all available \(getPuzzleDisplayName(puzzleType)) puzzles at \(difficulty) difficulty!")
                        .font(.title3)
                        .foregroundColor(.white.opacity(0.9))
                        .multilineTextAlignment(.center)
                    
                    Text("Generate new puzzles to continue playing.")
                        .font(.subheadline)
                        .foregroundColor(.white.opacity(0.8))
                        .multilineTextAlignment(.center)
                        .fontWeight(.medium)
                }
                .padding(24)
                .background(Color.white.opacity(0.1))
                .cornerRadius(16)
                
                Button("Generate New Puzzles") {
                    // Handle puzzle generation
                }
                .font(.title3)
                .fontWeight(.bold)
                .foregroundColor(.orange)
                .frame(maxWidth: .infinity)
                .padding()
                .background(Color.white)
                .cornerRadius(12)
                
                Spacer().frame(height: 100)
            }
            .padding(.horizontal, 24)
        }
        
        // Return home button
        returnHomeButton(geometry: geometry)
    }
    
    // MARK: - Generation in Progress Content
    @ViewBuilder
    private func generationInProgressContent(puzzleType: String, difficulty: String, geometry: GeometryProxy) -> some View {
        VStack(spacing: 24) {
            Spacer()
            
            ProgressView()
                .scaleEffect(2.0)
                .progressViewStyle(CircularProgressViewStyle(tint: .white))
            
            VStack(spacing: 16) {
                Text("Generating New Puzzles")
                    .font(.largeTitle)
                    .fontWeight(.bold)
                    .foregroundColor(.white)
                    .multilineTextAlignment(.center)
                
                Text("We're creating fresh \(getPuzzleDisplayName(puzzleType)) puzzles at \(difficulty) difficulty just for you!")
                    .font(.title3)
                    .foregroundColor(.white.opacity(0.9))
                    .multilineTextAlignment(.center)
                
                Text("This usually takes 30-60 seconds...")
                    .font(.subheadline)
                    .foregroundColor(.white.opacity(0.8))
                    .multilineTextAlignment(.center)
            }
            .padding(24)
            .background(Color.white.opacity(0.1))
            .cornerRadius(16)
            
            Spacer()
        }
        .padding(.horizontal, 24)
        
        // Return home button
        returnHomeButton(geometry: geometry)
    }
    
    // MARK: - Floating Action Buttons (for success screen)
    private func floatingActionButtons(geometry: GeometryProxy) -> some View {
        VStack(spacing: 12) {
            HStack(spacing: 12) {
                // Share button
                Button(action: shareAchievement) {
                    HStack {
                        Image(systemName: "square.and.arrow.up")
                        Text("Share")
                            .fontWeight(.bold)
                    }
                    .font(.headline)
                    .foregroundColor(.white)
                    .frame(maxWidth: .infinity)
                    .padding()
                    .background(Color.green)
                    .cornerRadius(16)
                }
                
                // Home button
                Button(action: onReturnHome) {
                    HStack {
                        Image(systemName: "house.fill")
                        Text("Home")
                            .fontWeight(.bold)
                    }
                    .font(.headline)
                    .foregroundColor(.black)
                    .frame(maxWidth: .infinity)
                    .padding()
                    .background(Color.white)
                    .cornerRadius(16)
                }
            }
            
            // Leaderboard button (for custom puzzles)
            if isCustomPuzzle, let customPuzzleId = customPuzzleId {
                Button("🏆 View Leaderboard") {
                    onViewLeaderboard(customPuzzleId)
                }
                .font(.headline)
                .fontWeight(.bold)
                .foregroundColor(.white)
                .frame(maxWidth: .infinity)
                .padding()
                .background(Color.orange)
                .cornerRadius(16)
            }
        }
        .padding(16)
        .background(
            LinearGradient(
                colors: [Color.clear, Color.black.opacity(0.3)],
                startPoint: .top,
                endPoint: .bottom
            )
        )
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .bottom)
    }
    
    // MARK: - Return Home Button (for other screens)
    private func returnHomeButton(geometry: GeometryProxy) -> some View {
        Button(action: onReturnHome) {
            HStack {
                Image(systemName: "house.fill")
                Text("Return Home")
                    .fontWeight(.bold)
            }
            .font(.headline)
            .foregroundColor(.white)
            .frame(maxWidth: .infinity)
            .padding()
            .background(Color.black.opacity(0.3))
            .cornerRadius(16)
        }
        .padding(16)
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .bottom)
    }
    
    // MARK: - Helper Methods
    
    private func shareAchievement() {
        // Implement sharing logic
        guard case .success(let streakDays, let earnedPoints) = completionType else { return }
        
        let shareText: String
        if isCustomPuzzle {
            shareText = "🏆 I just completed a custom puzzle on RiddleVerse and earned \(earnedPoints) points!"
        } else {
            shareText = "🏆 I just completed a puzzle on RiddleVerse! 🔥 \(streakDays)-day streak and earned \(earnedPoints) points!"
        }
        
        // Add session stats if available
        let fullShareText: String
        if let stats = sessionStats {
            fullShareText = "\(shareText) Got \(stats.correctAnswers)/\(stats.totalAnswers) correct with \(Int(stats.winRate * 100))% accuracy!"
        } else {
            fullShareText = shareText
        }
        
        let activityVC = UIActivityViewController(
            activityItems: [fullShareText],
            applicationActivities: nil
        )
        
        if let windowScene = UIApplication.shared.connectedScenes.first as? UIWindowScene,
           let window = windowScene.windows.first {
            window.rootViewController?.present(activityVC, animated: true)
        }
    }
    
    private func formatTime(_ seconds: Int) -> String {
        let minutes = seconds / 60
        let remainingSeconds = seconds % 60
        return "\(minutes):\(String(format: "%02d", remainingSeconds))"
    }
}

// MARK: - Supporting Types

struct PuzzleRecommendation: Identifiable {
    let id = UUID()
    let type: String
    let icon: String
    let description: String
    let difficulty: String
}

// MARK: - Helper Functions
private func getPuzzleDisplayName(_ puzzleType: String) -> String {
    switch puzzleType.lowercased() {
    case "mathexpression": return "Math Expression"
    case "oddoneout": return "Odd One Out"
    case "flowpuzzle": return "Flow Puzzle"
    case "symmetry": return "Symmetry"
    case "imagequestion": return "Image Question"
    case "triangledotmemory": return "Triangle Dot Memory"
    case "progressiverevelation": return "Progressive Reveal"
    case "musicidentification": return "Music Match"
    case "find_object": return "Find Object"
    case "realorai": return "Real or AI"
    case "wordsnake": return "Word Snake"
    case "crypto": return "Crypto Puzzle"
    case "colorshapematching": return "Color Shape Match"
    case "imagevortex": return "Image Vortex"
    case "mathcrossword": return "Math Crossword"
    case "dualtask": return "Dual Task"
    case "colortextmatching": return "Color Text Match"
    case "geography_cities": return "Geography Cities"
    case "geography_countries": return "Geography Countries"
    case "contextswitch": return "Context Switch"
    case "crossword": return "Crossword"
    case "math": return "Math"
    case "trivia": return "Trivia"
    case "synonyms": return "Synonyms"
    case "antonyms": return "Antonyms"
    case "memorysquares": return "Memory Squares"
    case "mathestimation": return "Math Estimation"
    case "anagram": return "Anagram"
    case "storypuzzle": return "Story Puzzle"
    case "memorystory": return "Memory Story"
    case "average": return "Average"
    case "division": return "Division"
    case "percentages": return "Percentage"
    case "discounts": return "Discounts"
    case "memorypreviouspair": return "Memory Pairs"
    case "memoryprevioussingle": return "Memory Single"
    case "purchasing": return "Purchasing"
    case "memorysequencing": return "Memory Sequencing"
    case "wordprefix": return "Word Prefix"
    case "memoryretention": return "Memory Retention"
    case "conversion": return "Conversion"
    case "connotationwords": return "Word Connotations"
    case "subtraction": return "Subtraction"
    case "mathtipping": return "Tip Calculation"
    case "imagepuzzle": return "Image Puzzle"
    case "mathcomparison": return "Math Comparison"
    case "numbersequence": return "Number Sequence"
    case "numbersum": return "Number Sum"
    case "symbolswipe": return "Symbol Swipe"
    case "uniqueobject": return "Unique Object"
    case "wordsearch": return "Word Search"
    case "pinballdeflector": return "Pinball Deflector"
    default: return puzzleType.capitalized
    }
}
