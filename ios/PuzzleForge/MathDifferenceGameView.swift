// Add this extension to support local subtraction data parsing
import SwiftUI

extension Puzzle {
    var subtractionPuzzleData: SubtractionPuzzleData? {
        guard let questionData = question.data(using: .utf8) else {
            print("🔴 SUBTRACTION: Failed to convert question to data")
            return nil
        }
        
        do {
            if let json = try JSONSerialization.jsonObject(with: questionData) as? [String: Any] {
                print("🔍 SUBTRACTION: Parsing JSON keys: \(json.keys)")
                
                guard let problemsArray = json["problems"] as? [[Int]],
                      !problemsArray.isEmpty else {
                    print("🔴 SUBTRACTION: Missing or invalid problems array")
                    return nil
                }
                
                let difficulty = json["difficulty"] as? String ?? "Medium"
                let hint = json["hint"] as? String ?? "Work from right to left, borrowing when necessary"
                
                print("🟢 SUBTRACTION: Successfully parsed \(problemsArray.count) problems")
                
                return SubtractionPuzzleData(
                    allProblems: problemsArray,
                    difficulty: difficulty,
                    hint: hint,
                    currentProblemIndex: 0
                )
            }
        } catch {
            print("🔴 SUBTRACTION: JSON parsing error: \(error)")
        }
        
        // Fallback sample data
        print("🔴 SUBTRACTION: Using fallback sample data")
        return SubtractionPuzzleData(
            allProblems: [[3532, 1521, 2011]],
            difficulty: "Medium",
            hint: "Work from right to left, borrowing when necessary",
            currentProblemIndex: 0
        )
    }
}

struct AnimatedBackgroundView: View {
    @State private var gradientOffset: CGFloat = 0
    @State private var pulseScale: CGFloat = 1.0
    
    var body: some View {
        ZStack {
            // Base gradient background
            LinearGradient(
                colors: [
                    Color(red: 0.2, green: 0.3, blue: 0.5),
                    Color(red: 0.4, green: 0.2, blue: 0.6),
                    Color(red: 0.1, green: 0.4, blue: 0.7)
                ],
                startPoint: .topLeading,
                endPoint: .bottomTrailing
            )
            
            // Animated overlay for subtle movement
            RadialGradient(
                colors: [
                    Color.white.opacity(0.1),
                    Color.clear
                ],
                center: .center,
                startRadius: 50,
                endRadius: 300
            )
            .scaleEffect(pulseScale)
            .opacity(0.3)
            
            // Moving gradient overlay
            LinearGradient(
                colors: [
                    Color.clear,
                    Color.white.opacity(0.05),
                    Color.clear
                ],
                startPoint: UnitPoint(x: gradientOffset, y: 0),
                endPoint: UnitPoint(x: gradientOffset + 0.3, y: 1)
            )
        }
        .onAppear {
            startAnimations()
        }
    }
    
    private func startAnimations() {
        // Subtle pulsing animation
        withAnimation(.easeInOut(duration: 3).repeatForever(autoreverses: true)) {
            pulseScale = 1.2
        }
        
        // Slow moving gradient
        withAnimation(.linear(duration: 8).repeatForever(autoreverses: false)) {
            gradientOffset = 1.0
        }
    }
}

struct EnhancedMathDifferenceTopGameBar: View {
    let level: Int
    let streakInfo: StreakInfo
    let score: Int
    let hearts: Int
    let gameLevel: String
    
    var body: some View {
        HStack {
            // Level indicator
            VStack(alignment: .leading, spacing: 2) {
                Text("LEVEL \(level)")
                    .font(.caption)
                    .fontWeight(.bold)
                    .foregroundColor(.white.opacity(0.8))
                
                Text(gameLevel)
                    .font(.caption2)
                    .foregroundColor(.white.opacity(0.6))
            }
            
            Spacer()
            
            // Score
            HStack(spacing: 4) {
                Image(systemName: "star.fill")
                    .foregroundColor(.yellow)
                    .font(.caption)
                
                Text("\(score)")
                    .font(.caption)
                    .fontWeight(.bold)
                    .foregroundColor(.white)
            }
            
            Spacer()
            
            // Hearts
            HStack(spacing: 2) {
                ForEach(0..<5, id: \.self) { index in
                    Image(systemName: index < hearts ? "heart.fill" : "heart")
                        .foregroundColor(index < hearts ? .red : .white.opacity(0.3))
                        .font(.caption)
                }
            }
        }
        .padding(.vertical, 8)
    }
}

struct MathDifferenceGameView: View {
    let score: Int
    let hearts: Int
    let level: String // e.g., "1/7"
    let number1: Int // e.g., 3532
    let number2: Int // e.g., 1521
    let onSubmitAnswer: (Int) -> Void
    let fetchNextPuzzle: () -> Void
    @StateObject private var feedbackManager = UnifiedFeedbackManager()
    @StateObject private var progressionManager = ProgressionManager()
    
    @State private var currentInput = ""
    @State private var isAnswered = false
    @State private var crystalRotation: Double = 0
    
    private var correctAnswer: Int {
        abs(number1 - number2)
    }
    
    private var puzzleIdentifier: String {
        "\(number1)-\(number2)"
    }
    
    var body: some View {
        GeometryReader { geometry in
            ZStack {
                // Animated background
                AnimatedBackgroundView()
                    .ignoresSafeArea()
                
                // Background crystal - smaller and positioned in top-right
                backgroundCrystalView
                    .position(x: geometry.size.width * 0.85, y: geometry.size.height * 0.2)
                
                VStack(spacing: 0) {
                    // Top bar
                    topBar
                        .padding(.horizontal, 20)
                        .padding(.top, 10)
                    
                    Spacer(minLength: 20)
                    
                    // Title
                    Text("FIND THE DIFFERENCE")
                        .font(.title3)
                        .fontWeight(.bold)
                        .foregroundColor(.white)
                        .padding(.bottom, 20)
                    
                    // Numbers to compare - enhanced display
                    numbersDisplay
                        .padding(.bottom, 20)
                    
                    Spacer()
                    
                    // Calculator interface
                    calculatorGrid
                        .padding(.horizontal, 20)
                        .padding(.bottom, 30)
                }
            }
        }
        .onChange(of: number1) { newValue in
            print("🔢 number1 changed to: \(newValue)")
            resetState()
        }
        .onChange(of: number2) { newValue in
            print("🔢 number2 changed to: \(newValue)")
            resetState()
        }
        .withUnifiedFeedback(feedbackManager)
        .trackPuzzleViewOnce(
            puzzleId: "id",
            puzzleType: "subtraction",
            difficulty: "Easy",
            questionIndex: 0
        )
        .onAppear {
            print("🔵 MathDifferenceGameView appeared")
            print("🔵 Problem: \(number1) - \(number2) = \(correctAnswer)")
            startCrystalAnimation()
        }
    }
    
    // MARK: - Top Bar
    private var topBar: some View {
        EnhancedMathDifferenceTopGameBar(
            level: progressionManager.currentLevel.level,
            streakInfo: progressionManager.streakInfo,
            score: score,
            hearts: hearts,
            gameLevel: level
        )
    }
    
    // MARK: - Enhanced Numbers Display
    private var numbersDisplay: some View {
        VStack(spacing: 20) {
            // Display the subtraction problem more clearly
            HStack(spacing: 15) {
                Text("\(number1)")
                    .font(.system(size: 48, weight: .bold))
                    .foregroundColor(.white)
                
                Text("−")
                    .font(.system(size: 36, weight: .bold))
                    .foregroundColor(.white.opacity(0.8))
                
                Text("\(number2)")
                    .font(.system(size: 48, weight: .bold))
                    .foregroundColor(.white)
                
                Text("=")
                    .font(.system(size: 36, weight: .bold))
                    .foregroundColor(.white.opacity(0.8))
                
                Text("?")
                    .font(.system(size: 48, weight: .bold))
                    .foregroundColor(Color(red: 1.0, green: 0.84, blue: 0.0))
            }
            
            // Hint text
            Text("Enter the difference between these numbers")
                .font(.system(size: 14, weight: .medium))
                .foregroundColor(.white.opacity(0.7))
                .multilineTextAlignment(.center)
        }
    }
    
    // MARK: - Crystal View (unchanged)
    private var backgroundCrystalView: some View {
        CrystalShape()
            .fill(
                RadialGradient(
                    colors: [
                        Color(red: 0.53, green: 0.81, blue: 0.92).opacity(0.3),
                        Color(red: 0.9, green: 0.9, blue: 0.98).opacity(0.3),
                        Color(red: 0.87, green: 0.63, blue: 0.87).opacity(0.3),
                        Color(red: 0.53, green: 0.81, blue: 0.92).opacity(0.3)
                    ],
                    center: .center,
                    startRadius: 10,
                    endRadius: 30
                )
            )
            .overlay(
                CrystalShape()
                    .stroke(Color.white.opacity(0.4), lineWidth: 1)
            )
            .frame(width: 60, height: 60)
            .rotationEffect(.degrees(crystalRotation))
            .scaleEffect(1.0 + sin(crystalRotation * .pi / 180) * 0.03)
    }
    
    struct CrystalShape: Shape {
        func path(in rect: CGRect) -> Path {
            var path = Path()
            let width = rect.width
            let height = rect.height
            
            // Create a simple diamond/crystal shape
            path.move(to: CGPoint(x: width * 0.5, y: 0))
            path.addLine(to: CGPoint(x: width * 0.8, y: height * 0.3))
            path.addLine(to: CGPoint(x: width, y: height * 0.5))
            path.addLine(to: CGPoint(x: width * 0.8, y: height * 0.7))
            path.addLine(to: CGPoint(x: width * 0.5, y: height))
            path.addLine(to: CGPoint(x: width * 0.2, y: height * 0.7))
            path.addLine(to: CGPoint(x: 0, y: height * 0.5))
            path.addLine(to: CGPoint(x: width * 0.2, y: height * 0.3))
            path.closeSubpath()
            
            return path
        }
    }
    
    // MARK: - Calculator Grid (unchanged)
    private var calculatorGrid: some View {
        VStack(spacing: 16) {
            // Input display
            inputDisplay
            
            // Number grid (1-9)
            ForEach(0..<3, id: \.self) { row in
                HStack(spacing: 16) {
                    ForEach(0..<3, id: \.self) { col in
                        let number = row * 3 + col + 1
                        DifferenceCalculatorButton(
                            text: "\(number)",
                            action: {
                                if !isAnswered && currentInput.count < 6 && !feedbackManager.isShowingFeedback {
                                    hapticFeedback()
                                    currentInput += "\(number)"
                                }
                            },
                            isDisabled: feedbackManager.isShowingFeedback
                        )
                    }
                }
            }
            
            // Bottom row (Clear, 0, Submit)
            HStack(spacing: 16) {
                DifferenceCalculatorButton(
                    text: "✕",
                    action: {
                        if !isAnswered && !feedbackManager.isShowingFeedback {
                            hapticFeedback()
                            currentInput = ""
                        }
                    },
                    isSpecial: true,
                    isDisabled: feedbackManager.isShowingFeedback
                )
                
                DifferenceCalculatorButton(
                    text: "0",
                    action: {
                        if !isAnswered && currentInput.count < 6 && !feedbackManager.isShowingFeedback {
                            hapticFeedback()
                            currentInput += "0"
                        }
                    },
                    isDisabled: feedbackManager.isShowingFeedback
                )
                
                DifferenceCalculatorButton(
                    text: "SUBMIT",
                    action: submitAnswer,
                    isSpecial: true,
                    isDisabled: feedbackManager.isShowingFeedback || currentInput.isEmpty
                )
            }
        }
    }
    
    // MARK: - Input Display (unchanged)
    private var inputDisplay: some View {
        RoundedRectangle(cornerRadius: 8)
            .stroke(Color.white.opacity(0.3), lineWidth: 2)
            .frame(height: 60)
            .overlay(
                Text(currentInput.isEmpty ? "0" : currentInput)
                    .font(.title2)
                    .fontWeight(.bold)
                    .foregroundColor(.white)
            )
    }
    
    // MARK: - Helper Methods
    private func startCrystalAnimation() {
        withAnimation(.linear(duration: 8).repeatForever(autoreverses: false)) {
            crystalRotation = 360
        }
    }
    
    private func submitAnswer() {
        guard !isAnswered, !feedbackManager.isShowingFeedback, !currentInput.isEmpty, let answer = Int(currentInput) else {
            print("🔴 Submit blocked - answered: \(isAnswered), showing feedback: \(feedbackManager.isShowingFeedback), input: '\(currentInput)'")
            return
        }
        
        hapticFeedback()
        isAnswered = true
        let isAnswerCorrect = answer == correctAnswer
        
        print("🎯 SUBTRACTION: Submit answer: \(answer), correct: \(correctAnswer), isCorrect: \(isAnswerCorrect)")
        
        // Enhanced feedback with subtraction-specific messaging
        feedbackManager.showFeedback(
            puzzleType: "subtraction",
            isCorrect: isAnswerCorrect,
            userAnswer: currentInput,
            correctAnswer: "\(correctAnswer)",
            timeSpent: 15.0,
            difficulty: "Medium",
            timeRemaining: 45,
            totalTime: 60,
            onComplete: {
                print("🎯 SUBTRACTION: Feedback complete, calling fetchNextPuzzle")
                fetchNextPuzzle()
            }
        )
        
        onSubmitAnswer(answer)
    }
    
    private func resetState() {
        print("🔄 SUBTRACTION: resetState called")
        isAnswered = false
        currentInput = ""
        print("🔄 SUBTRACTION: resetState completed - ready for new problem")
    }

    private func hapticFeedback() {
        let impactFeedback = UIImpactFeedbackGenerator(style: .medium)
        impactFeedback.impactOccurred()
    }
}

// MARK: - Enhanced Calculator Button
struct DifferenceCalculatorButton: View {
    let text: String
    let action: () -> Void
    var isSpecial: Bool = false
    var isDisabled: Bool = false
    
    var body: some View {
        Button(action: action) {
            RoundedRectangle(cornerRadius: 8)
                .stroke(
                    Color.white.opacity(isDisabled ? 0.2 : (isSpecial ? 0.6 : 0.3)),
                    lineWidth: 2
                )
                .frame(height: 60)
                .overlay(
                    Text(text)
                        .font(text == "SUBMIT" ? .system(size: 14, weight: .bold) : .title2)
                        .fontWeight(.bold)
                        .foregroundColor(.white.opacity(isDisabled ? 0.5 : 1.0))
                        .lineLimit(1)
                        .minimumScaleFactor(0.6)
                )
        }
        .disabled(isDisabled)
        .buttonStyle(PlainButtonStyle())
    }
}
