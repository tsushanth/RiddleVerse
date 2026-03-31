//
//  SubscriptionPuzzleView.swift
//  PuzzleForge
//
//  Created by Sushanth Tiruvaipati on 6/11/25.
//

import SwiftUI

struct SubscriptionPuzzleView: View {
    let puzzle: Puzzle
    let questionIndex: Int
    let totalQuestions: Int
    let onAnswerSubmitted: (Int, Bool) -> Void
    let onNextPuzzle: () -> Void
    let onExit: () -> Void
    
    @StateObject private var timer = PuzzleTimer(totalTime: 75)
    @State private var selectedAnswer: Int?
    @State private var isAnswered = false
    @State private var starAnimation: Double = 0
    @State private var score = 0
    @StateObject private var feedbackManager = UnifiedFeedbackManager()
    @StateObject private var progressionManager = ProgressionManager()
    
    // CHANGE: Use @State to ensure options don't shuffle constantly
    @State private var answerOptions: [Int] = []
    
    private var purchasingData: PurchasingPuzzleData? {
        puzzle.purchasingPuzzleData
    }
    
    var body: some View {
        GeometryReader { geometry in
            ZStack {
                // Animated background
                backgroundView
                    .ignoresSafeArea()
                
                VStack(spacing: 0) {
                    // Top bar
                    topBar
                        .padding(.horizontal, 20)
                        .padding(.top, 10)
                    
                    Spacer(minLength: 40)
                    
                    // Game type icons
                    gameTypeIconsRow
                        .padding(.horizontal, 40)
                        .padding(.bottom, 60)
                    
                    // Concert tickets icon
                    ticketsIcon
                        .frame(height: 120)
                        .padding(.bottom, 40)
                    
                    // Monthly price display
                    priceDisplay
                        .padding(.horizontal, 40)
                        .padding(.bottom, 40)
                    
                    // Calculation prompt
                    calculationPrompt
                        .padding(.horizontal, 20)
                        .padding(.bottom, 20)
                    
                    frequencyHint
                    
                    Text("IS ANNUALLY:")
                        .font(.system(size: 18, weight: .bold))
                        .foregroundColor(Color(red: 0.31, green: 0.8, blue: 0.77))
                        .padding(.bottom, 30)
                    
                    // Answer options
                    answerOptionsGrid
                        .padding(.horizontal, 20)
                    
                    Spacer()
                    
                    // Progress bar
                    progressBar
                        .padding(.horizontal, 40)
                        .padding(.bottom, 30)
                }
                
            }
        }
        .withUnifiedFeedback(feedbackManager)
        .trackPuzzleViewOnce(
                    puzzleId: "id",
                    puzzleType: "subscription",
                    difficulty: "Easy",
                    questionIndex: 0
                )
        .onAppear {
            startAnimations()
            timer.start()
            generateAnswerOptions() // CHANGE: Generate options once on appear
        }
        .onDisappear {
            timer.stop()
        }
        .onChange(of: timer.timeRemaining) { newTime in
            if newTime <= 0 && !isAnswered {
                // Time's up - auto submit first option
                selectAnswer(answerOptions[0])
            }
        }
        .navigationBarHidden(true)
    }
    
    // MARK: - Background View
    private var backgroundView: some View {
        ZStack {
            // Base gradient
            LinearGradient(
                colors: [
                    Color(red: 0.04, green: 0.04, blue: 0.18),
                    Color(red: 0.1, green: 0.1, blue: 0.29),
                    Color(red: 0.18, green: 0.18, blue: 0.37)
                ],
                startPoint: .top,
                endPoint: .bottom
            )
            
            // Animated stars
            AnimatedStarField(animationOffset: starAnimation)
        }
    }
    
    // MARK: - Top Bar
    private var topBar: some View {
        HStack {
            Button(action: onExit) {
                HStack(spacing: 10) {
                    Image(systemName: "xmark")
                        .foregroundColor(Color(red: 0.31, green: 0.8, blue: 0.77))
                        .font(.title2)
                    
                    Text("Exit")
                        .font(.title3)
                        .fontWeight(.medium)
                        .foregroundColor(Color(red: 0.31, green: 0.8, blue: 0.77))
                }
            }
            
            Spacer()
            
            // Question counter
            Text("\(questionIndex + 1)/\(totalQuestions)")
                .font(.title3)
                .fontWeight(.medium)
                .foregroundColor(Color(red: 0.31, green: 0.8, blue: 0.77).opacity(0.8))
            
            Spacer()
            
            // Timer
            Text(timer.formattedTime)
                .font(.title2)
                .fontWeight(.bold)
                .foregroundColor(timer.timeRemaining <= 15 ? .red : Color(red: 0.31, green: 0.8, blue: 0.77))
                .animation(.easeInOut(duration: 0.5), value: timer.timeRemaining <= 15)
        }
    }
    
    // MARK: - Game Type Icons
    private var gameTypeIconsRow: some View {
        HStack {
            Spacer()
            
            Text("🎴")
                .font(.system(size: 32))
                .opacity(0.6)
            
            Spacer()
            
            Text("✂️")
                .font(.system(size: 32))
                .opacity(0.6)
            
            Spacer()
            
            Text("🎭")
                .font(.system(size: 32))
                .opacity(0.6)
            
            Spacer()
            
            Text("💎")
                .font(.system(size: 32))
                .opacity(0.6)
            
            Spacer()
        }
    }
    
    // MARK: - Tickets Icon
    private var ticketsIcon: some View {
        VStack {
            Spacer()
            
            ZStack {
                // Multiple ticket layers for depth
                Image(systemName: "ticket")
                    .font(.system(size: 60, weight: .medium))
                    .foregroundColor(.white)
                    .rotationEffect(.degrees(-15))
                    .offset(x: -10, y: 5)
                    .opacity(0.7)
                
                Image(systemName: "ticket")
                    .font(.system(size: 60, weight: .medium))
                    .foregroundColor(.white)
                    .rotationEffect(.degrees(-5))
                    .offset(x: 5, y: -2)
                    .opacity(0.9)
                
                Image(systemName: "ticket")
                    .font(.system(size: 60, weight: .medium))
                    .foregroundColor(.white)
                    .rotationEffect(.degrees(10))
            }
            .scaleEffect(1.0 + sin(starAnimation * 2) * 0.05)
            
            Spacer()
        }
    }
    
    // MARK: - Price Display
    private var priceDisplay: some View {
        RoundedRectangle(cornerRadius: 12)
            .fill(Color(red: 0.18, green: 0.18, blue: 0.37).opacity(0.8))
            .frame(height: 80)
            .overlay(
                VStack(spacing: 4) {
                    if let data = purchasingData {
                        // Format payment to show decimal places only if needed
                        Text("$\(data.payment == floor(data.payment) ? String(format: "%.0f", data.payment) : String(format: "%.2f", data.payment))")
                            .font(.system(size: 36, weight: .bold))
                            .foregroundColor(Color(red: 0.31, green: 0.8, blue: 0.77))
                        
                        Text(data.purpose)
                            .font(.system(size: 12, weight: .medium))
                            .foregroundColor(Color(red: 0.31, green: 0.8, blue: 0.77).opacity(0.8))
                    }
                }
            )
    }
    
    // MARK: - Calculation Prompt
    private var calculationPrompt: some View {
        RoundedRectangle(cornerRadius: 12)
            .stroke(Color(red: 0.31, green: 0.8, blue: 0.77), lineWidth: 2)
            .frame(height: 80)
            .overlay(
                HStack(spacing: 0) {
                    if let data = purchasingData {
                        // Frequency section
                        VStack {
                            Spacer()
                            Text(data.frequency.uppercased())
                                .font(.system(size: 20, weight: .bold))
                                .foregroundColor(.white)
                            Spacer()
                        }
                        .frame(maxWidth: .infinity)
                        .background(
                            Color(red: 0.31, green: 0.8, blue: 0.77).opacity(0.1)
                        )
                        
                        // Period section
                        VStack {
                            Spacer()
                            Text("A YEAR")
                                .font(.system(size: 24, weight: .bold))
                                .foregroundColor(.white)
                            Spacer()
                        }
                        .frame(maxWidth: .infinity, maxHeight: .infinity)
                    }
                }
            )
    }
    
    private var frequencyHint: some View {
        Group {
            if let data = purchasingData {
                HStack {
                    Image(systemName: "lightbulb")
                        .foregroundColor(Color(red: 0.31, green: 0.8, blue: 0.77))
                        .font(.system(size: 16))
                    
                    Text("Hint: \(getFrequencyHint(data.frequency))")
                        .font(.system(size: 14, weight: .medium))
                        .foregroundColor(Color(red: 0.31, green: 0.8, blue: 0.77).opacity(0.9))
                }
                .padding(.horizontal, 20)
                .padding(.bottom, 15)
            } else {
                EmptyView()
            }
        }
    }
    
    private func getFrequencyHint(_ frequency: String) -> String {
        switch frequency.lowercased() {
        case "biweekly":
            return "Biweekly = 26 times per year (every 2 weeks)"
        case "monthly":
            return "Monthly = 12 times per year"
        case "weekly":
            return "Weekly = 52 times per year"
        case "quarterly":
            return "Quarterly = 4 times per year"
        default:
            return "Monthly = 12 times per year"
        }
    }

    
    // MARK: - Answer Options Grid
    private var answerOptionsGrid: some View {
        VStack(spacing: 16) {
            HStack(spacing: 16) {
                if answerOptions.count >= 2 {
                    AnswerOptionButton(
                        amount: answerOptions[0],
                        isSelected: selectedAnswer == answerOptions[0],
                        isAnswered: isAnswered,
                        action: { selectAnswer(answerOptions[0]) }
                    )
                    
                    AnswerOptionButton(
                        amount: answerOptions[1],
                        isSelected: selectedAnswer == answerOptions[1],
                        isAnswered: isAnswered,
                        action: { selectAnswer(answerOptions[1]) }
                    )
                }
            }
            
            HStack(spacing: 16) {
                if answerOptions.count >= 4 {
                    AnswerOptionButton(
                        amount: answerOptions[2],
                        isSelected: selectedAnswer == answerOptions[2],
                        isAnswered: isAnswered,
                        action: { selectAnswer(answerOptions[2]) }
                    )
                    
                    AnswerOptionButton(
                        amount: answerOptions[3],
                        isSelected: selectedAnswer == answerOptions[3],
                        isAnswered: isAnswered,
                        action: { selectAnswer(answerOptions[3]) }
                    )
                }
            }
        }
    }
    
    // MARK: - Progress Bar
    private var progressBar: some View {
        RoundedRectangle(cornerRadius: 2)
            .fill(Color.white.opacity(0.3))
            .frame(height: 4)
    }
    
    // MARK: - Helper Methods
    private func startAnimations() {
        // CHANGE: Slower star animation (20 seconds instead of 10)
        withAnimation(.linear(duration: 20).repeatForever(autoreverses: false)) {
            starAnimation = 2 * .pi
        }
    }
    
    // CHANGE: Generate answer options once and store in @State
    private func generateAnswerOptions() {
        guard let data = purchasingData else {
            answerOptions = [0, 0, 0, 0]
            return
        }
        let correct = Int(round(data.yearlyTotal)) // Round to nearest Int for display
        let options = [
            correct - 300,
            correct,
            correct + 450,
            correct + 800
        ].shuffled()
        answerOptions = Array(options.prefix(4))
    }
    
    private func getFrequencyDisplay(_ frequency: String) -> String {
        switch frequency.lowercased() {
        case "biweekly":
            return "26×"
        case "monthly":
            return "12×"
        case "weekly":
            return "52×"
        case "quarterly":
            return "4×"
        default:
            return "12×"
        }
    }
    
    private func selectAnswer(_ answer: Int) {
        guard !isAnswered else { return }
        guard let data = purchasingData else { return }
        
        hapticFeedback()
        timer.stop()
        selectedAnswer = answer
        isAnswered = true
        // Remove this line: isCorrect = answer == Int(round(data.yearlyTotal))
        
        let isCorrect = answer == Int(round(data.yearlyTotal)) // ✅ Define locally
        
        feedbackManager.showFeedback(
            puzzleType: "purchasing",
            isCorrect: isCorrect,
            userAnswer: "$\(answer)",
            correctAnswer: String(format: "$%.0f", data.yearlyTotal),
            timeSpent: Double(timer.totalTime - timer.timeRemaining),
            difficulty: puzzle.difficulty,
            timeRemaining: timer.timeRemaining,
            totalTime: timer.totalTime,
            onComplete: {
                onNextPuzzle()
            }
        )
        
        if isCorrect {
            score = calculateScore()
        }
        
        onAnswerSubmitted(answer, isCorrect)
    }
    
    // CHANGE: Removed resetState function since we always move to next puzzle
    
    private func calculateScore() -> Int {
        guard let data = purchasingData else { return 0 }
        return PuzzleScoring.calculateScore(
            for: "purchasing",
            timeRemaining: timer.timeRemaining,
            totalTime: 75,
            difficulty: data.difficulty
        )
    }
    
    private func hapticFeedback() {
        let impactFeedback = UIImpactFeedbackGenerator(style: .medium)
        impactFeedback.impactOccurred()
    }
}

// MARK: - Answer Option Button
struct AnswerOptionButton: View {
    let amount: Int
    let isSelected: Bool
    let isAnswered: Bool
    let action: () -> Void
    
    var body: some View {
        Button(action: action) {
            RoundedRectangle(cornerRadius: 12)
                .fill(
                    isSelected ?
                    Color(red: 0.31, green: 0.8, blue: 0.77).opacity(0.3) :
                    Color(red: 0.18, green: 0.18, blue: 0.37).opacity(0.8)
                )
                .overlay(
                    RoundedRectangle(cornerRadius: 12)
                        .stroke(
                            isSelected ?
                            Color(red: 0.31, green: 0.8, blue: 0.77) :
                            Color.white.opacity(0.3),
                            lineWidth: isSelected ? 3 : 1
                        )
                )
                .frame(height: 80)
                .overlay(
                    Text("$\(amount)")
                        .font(.title2)
                        .fontWeight(.bold)
                        .foregroundColor(.white)
                )
        }
        .disabled(isAnswered)
        .scaleEffect(isSelected ? 1.02 : 1.0)
        // CHANGE: Slower, smoother animation for button selection
        .animation(.easeInOut(duration: 0.4), value: isSelected)
        .buttonStyle(PlainButtonStyle())
    }
}

// MARK: - Animated Star Field
struct AnimatedStarField: View {
    let animationOffset: Double
    
    var body: some View {
        Canvas { context, size in
            // Draw animated stars
            for i in 0..<50 {
                let x = CGFloat(i * 37).truncatingRemainder(dividingBy: size.width)
                let y = CGFloat(i * 23).truncatingRemainder(dividingBy: size.height)
                let starSize = CGFloat(1.0 + Double(i % 3))
                let opacity = 0.3 + (sin(animationOffset + Double(i)) * 0.2)
                
                let star = Path { path in
                    path.addEllipse(in: CGRect(x: x, y: y, width: starSize, height: starSize))
                }
                
                context.fill(star, with: .color(.white.opacity(opacity)))
            }
        }
    }
}

// MARK: - Preview
struct SubscriptionPuzzleView_Previews: PreviewProvider {
    static var previews: some View {
        let samplePuzzle = Puzzle(
            question: """
            {"payment":75,"frequency":"biweekly","purpose":"Gym membership","yearlyTotal":1950,"difficulty":"Medium","hint":"Multiply the payment by the number of bi-weekly periods in a year (26)"}
            """,
            answer: "1950",
            hint: "Multiply the payment by the number of bi-weekly periods in a year (26)",
            options: [],
            format: "purchasing",
            puzzleType: "purchasing",
            puzzleId: "sample-purchasing",
            id: "sample-purchasing",
            name: "Purchasing Puzzle",
            createdAt: Date().timeIntervalSince1970 * 1000,
            status: "ready",
            difficulty: "Medium"
        )
        
        SubscriptionPuzzleView(
            puzzle: samplePuzzle,
            questionIndex: 0,
            totalQuestions: 1,
            onAnswerSubmitted: { answer, isCorrect in
                print("Answer: \(answer), Correct: \(isCorrect)")
            },
            onNextPuzzle: {
                print("Next puzzle")
            },
            onExit: {
                print("Exit")
            }
        )
    }
}
