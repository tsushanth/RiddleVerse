import SwiftUI

struct TipBubblePuzzleView: View {
    let puzzle: Puzzle
    let questionIndex: Int
    let totalQuestions: Int
    let onAnswerSubmitted: (Double, Bool) -> Void
    let onNextPuzzle: () -> Void
    let onExit: () -> Void
    
    @State private var bubbles: [TipBubble] = []
    @State private var gameActive = true
    @State private var hasAnswered = false
    @State private var selectedBubbleId: String?
    @State private var showCompletionDialog = false
    @StateObject private var feedbackManager = UnifiedFeedbackManager()
    @StateObject private var progressionManager = ProgressionManager()
    
    // Add state to track if analytics has been logged
    @State private var hasLoggedAnalytics = false
    
    private let totalTime = 90
    @State private var timeLeft = 90
    @State private var timer: Timer?
    
    var tipPuzzleData: TipPuzzleData? {
        //print("🔍 Parsing tip puzzle data from question: \(puzzle.question)")
        
        // Handle both JSON and simple formats
        if let questionData = puzzle.question.data(using: .utf8),
           let json = try? JSONSerialization.jsonObject(with: questionData) as? [String: Any] {
           // print("🔍 Successfully parsed JSON: \(json)")
            
            // Check if this is the API format with tipAmount
            if let billAmount = json["billAmount"] as? Double,
               let tipPercentage = json["tipPercentage"] as? Double,
               let tipAmount = json["tipAmount"] as? Double {
                //print("🟢 Found API format - billAmount: \(billAmount), tipPercentage: \(tipPercentage), tipAmount: \(tipAmount)")
                return TipPuzzleData(
                    billAmount: billAmount,
                    tipPercentage: tipPercentage,
                    correctTipAmount: tipAmount
                )
            }
            
            // Check if this is the old format with correctTipAmount
            if let billAmount = json["billAmount"] as? Double,
               let tipPercentage = json["tipPercentage"] as? Double,
               let correctTipAmount = json["correctTipAmount"] as? Double {
                print("🟢 Found old format - billAmount: \(billAmount), tipPercentage: \(tipPercentage), correctTipAmount: \(correctTipAmount)")
                return TipPuzzleData(
                    billAmount: billAmount,
                    tipPercentage: tipPercentage,
                    correctTipAmount: correctTipAmount
                )
            }
            
            print("🔴 JSON missing required fields: \(json.keys)")
        } else {
            print("🔴 Failed to parse question as JSON")
        }
        
        // Fallback: try to parse from answer field or create sample data
        if let correctTipAmount = Double(puzzle.answer), !puzzle.answer.isEmpty {
            print("🟡 Using answer field fallback: \(correctTipAmount)")
            // Estimate bill amount and percentage from tip amount
            let estimatedBillAmount = correctTipAmount * 5 // Rough estimate
            let estimatedTipPercentage = 18.0 // Default 18%
            
            return TipPuzzleData(
                billAmount: estimatedBillAmount,
                tipPercentage: estimatedTipPercentage,
                correctTipAmount: correctTipAmount
            )
        }
        
        // Last resort: create sample data for testing
        print("🔴 Using sample data fallback")
        return TipPuzzleData(
            billAmount: 45.50,
            tipPercentage: 18.0,
            correctTipAmount: 8.19
        )
    }
    
    var body: some View {
        GeometryReader { geometry in
            ZStack {
                // Background gradient
                LinearGradient(
                    colors: [
                        Color(red: 0.10, green: 0.10, blue: 0.18),
                        Color(red: 0.09, green: 0.13, blue: 0.24),
                        Color(red: 0.06, green: 0.20, blue: 0.38)
                    ],
                    startPoint: .top,
                    endPoint: .bottom
                )
                .ignoresSafeArea()
                
                VStack {
                    // Top Bar
                    topGameBar
                    
                    Spacer()
                        .frame(height: 20)
                    
                    // Title
                    Text("TAP THE CORRECT TIP")
                        .font(.system(size: 20, weight: .bold))
                        .foregroundColor(.white)
                        .frame(maxWidth: .infinity)
                        .multilineTextAlignment(.center)
                    
                    Spacer().frame(height: 16)
                    
                    // Bill info display
                    if let data = tipPuzzleData {
                        billInfoCard(data: data)
                    }
                    
                    Spacer()
                    
                    // Static Bubbles Grid
                    bubbleGrid
                    
                    Spacer()
                    
                    // Instructions
                    Text("Tap the bubble with the correct tip amount!")
                        .font(.system(size: 14))
                        .foregroundColor(.white.opacity(0.8))
                        .multilineTextAlignment(.center)
                        .padding(.horizontal, 40)
                        .padding(.bottom, 20)
                }
            }
        }
        .withUnifiedFeedback(feedbackManager)
        .onAppear {
            print("🔵 TipBubblePuzzleView appeared")
            print("🔵 Puzzle question: \(puzzle.question)")
            print("🔵 Puzzle answer: \(puzzle.answer)")
            setupGame()
            startTimer()
            
            // 🔥 FIX: Only log analytics once per puzzle
            if !hasLoggedAnalytics {
                AnalyticsManager.shared.track(.puzzleStart(
                    type: "tipbubble",
                    difficulty: puzzle.difficulty,
                    questionIndex: questionIndex
                ))
                hasLoggedAnalytics = true
            }
        }
        .onDisappear {
            stopAllTimers()
        }
        .alert("Puzzle Completed!", isPresented: $showCompletionDialog) {
            Button("Next Puzzle") {
                print("🔵 Next Puzzle button tapped in dialog")
                print("🔵 Current puzzle ID: \(puzzle.id)")
                print("🔵 Current question index: \(questionIndex)")
                showCompletionDialog = false
                onNextPuzzle()
            }
            Button("Back to Home") {
                print("🏠 Back to Home button tapped")
                onExit()
            }
        } message: {
            VStack {
                Text("Time remaining: \(timeLeft)s")
            }
        }
    }
    
    // MARK: - Top Game Bar
    private var topGameBar: some View {
        EnhancedTipBubbleTopGameBar(
            level: progressionManager.currentLevel,
            streakInfo: progressionManager.streakInfo,
            timer: "\(timeLeft)s",
            questionIndex: questionIndex,
            totalQuestions: totalQuestions,
            lives: 3, // Will be updated by progression
            onExit: onExit
        )
    }
    
    // MARK: - Bill Info Card
    private func billInfoCard(data: TipPuzzleData) -> some View {
        VStack(spacing: 8) {
            Text("Bill Amount")
                .font(.system(size: 14))
                .foregroundColor(.white.opacity(0.8))
            
            Text("$\(String(format: "%.2f", data.billAmount))")
                .font(.system(size: 24, weight: .bold))
                .foregroundColor(.white)
            
                    Spacer()
                        .frame(height: 8)
            
            Text("Tip: \(Int(data.tipPercentage.rounded()))%")
                .font(.system(size: 18, weight: .bold))
                .foregroundColor(Color(red: 1.0, green: 0.84, blue: 0.0)) // Gold color
        }
        .padding(20)
        .background(
            RoundedRectangle(cornerRadius: 16)
                .fill(Color.white.opacity(0.1))
        )
        .padding(.horizontal, 40)
    }
    
    // MARK: - Static Bubble Grid
    private var bubbleGrid: some View {
        LazyVGrid(columns: [
            GridItem(.flexible(), spacing: 20),
            GridItem(.flexible(), spacing: 20)
        ], spacing: 20) {
            ForEach(bubbles) { bubble in
                TipBubbleComponent(
                    bubble: bubble,
                    isSelected: selectedBubbleId == bubble.id,
                    gameActive: gameActive && !hasAnswered,
                    onBubbleTapped: { tappedBubble in
                        handleBubbleTap(tappedBubble)
                    }
                )
            }
        }
        .padding(.horizontal, 40)
    }
    
    // MARK: - Helper Methods
    private func setupGame() {
        guard let data = tipPuzzleData else {
            print("🔴 No tip puzzle data available")
            return
        }

        print("🔵 Setting up static bubble game with bill: \(data.billAmount), correct tip: \(data.correctTipAmount)")

        // 🔥 FIX: Create all bubble data first (amounts + correctness)
        var bubbleData: [(amount: Double, isCorrect: Bool)] = []
        
        // Add correct bubble
        bubbleData.append((amount: data.correctTipAmount, isCorrect: true))
        
        // Generate incorrect bubbles
        for _ in 0..<3 {
            let incorrectAmount = generateIncorrectTipAmount(
                correctAmount: data.correctTipAmount,
                tipPercentage: data.tipPercentage
            )
            bubbleData.append((amount: incorrectAmount, isCorrect: false))
        }
        
        // 🔥 FIX: Shuffle the data BEFORE creating bubbles
        bubbleData.shuffle()
        print("🔵 Shuffled bubble data: \(bubbleData)")

        var newBubbles: [TipBubble] = []
        
        // Create bubbles with shuffled data
        for bubbleInfo in bubbleData {
            let bubble = TipBubble(
                tipAmount: bubbleInfo.amount,
                billAmount: data.billAmount,
                isCorrect: bubbleInfo.isCorrect,
                positionX: 0, // Not used in static grid
                positionY: 0, // Not used in static grid
                speed: 0      // Not used in static
            )
            newBubbles.append(bubble)
            print("🔵 Created bubble with amount $\(String(format: "%.2f", bubbleInfo.amount)), correct: \(bubbleInfo.isCorrect)")
        }

        self.bubbles = newBubbles
        print("🔵 Created \(bubbles.count) static bubbles with shuffled positions")
    }
    
    private func startTimer() {
        timer = Timer.scheduledTimer(withTimeInterval: 1.0, repeats: true) { _ in
            if timeLeft > 0 && gameActive && !hasAnswered {
                timeLeft -= 1
            } else if timeLeft == 0 {
                handleTimeUp()
            }
        }
    }
    
    private func handleBubbleTap(_ tappedBubble: TipBubble) {
        guard !hasAnswered && gameActive else { return }
        
        hasAnswered = true
        gameActive = false
        selectedBubbleId = tappedBubble.id
        stopAllTimers()
        
        let impactFeedback = UIImpactFeedbackGenerator(style: .heavy)
        impactFeedback.impactOccurred()
        
        feedbackManager.showFeedback(
            puzzleType: "mathtipping",
            isCorrect: tappedBubble.isCorrect,
            userAnswer: String(format: "$%.2f", tappedBubble.tipAmount),
            correctAnswer: String(format: "$%.2f", tipPuzzleData?.correctTipAmount ?? 0),
            timeSpent: Double(totalTime - timeLeft),
            difficulty: puzzle.difficulty,
            timeRemaining: timeLeft,
            totalTime: totalTime,
            onComplete: {
                onNextPuzzle()
            }
        )
        
        onAnswerSubmitted(tappedBubble.tipAmount, tappedBubble.isCorrect)
    }
    
    private func handleTimeUp() {
        guard !hasAnswered else { return }
        
        hasAnswered = true
        gameActive = false
        stopAllTimers()
        
        feedbackManager.showFeedback(
            puzzleType: "mathtipping",
            isCorrect: false,
            userAnswer: "Time's up!",
            correctAnswer: String(format: "$%.2f", tipPuzzleData?.correctTipAmount ?? 0),
            timeSpent: Double(totalTime),
            difficulty: puzzle.difficulty,
            timeRemaining: 0,
            totalTime: totalTime,
            onComplete: {
                onNextPuzzle()
            }
        )
        
        onAnswerSubmitted(0.0, false)
    }
    
    private func stopAllTimers() {
        timer?.invalidate()
        timer = nil
        print("🔵 All timers stopped")
    }
    
    private func generateIncorrectTipAmount(correctAmount: Double, tipPercentage: Double) -> Double {
        let billAmount = correctAmount * 100 / tipPercentage // Calculate original bill
        
        let variations = [
            correctAmount * 0.5,        // 50% of correct
            correctAmount * 1.5,        // 150% of correct
            correctAmount * 0.75,       // 75% of correct
            correctAmount * 1.25,       // 125% of correct
            correctAmount + 2.0,        // Add $2
            max(correctAmount - 2.0, 0.01), // Subtract $2 (min $0.01)
            (tipPercentage + 5) / 100 * billAmount, // +5% tip
            max((tipPercentage - 5) / 100 * billAmount, 0.01), // -5% tip
            (tipPercentage + 10) / 100 * billAmount, // +10% tip
            max((tipPercentage - 10) / 100 * billAmount, 0.01) // -10% tip
        ]
        
        let result = variations.randomElement() ?? correctAmount * 0.8
        
        // Ensure the incorrect amount is different from correct (at least 50 cents difference)
        let difference = abs(result - correctAmount)
        if difference < 0.50 {
            return correctAmount + (Bool.random() ? 1.0 : -1.0)
        }
        
        return max(result, 0.01) // Ensure positive amount
    }
}

struct EnhancedTipBubbleTopGameBar: View {
    let level: UserLevel
    let streakInfo: StreakInfo
    let timer: String
    let questionIndex: Int
    let totalQuestions: Int
    let lives: Int
    let onExit: () -> Void
    
    var body: some View {
        HStack {
            // Left side: Back button and level info
            HStack(spacing: 10) {
                Button(action: onExit) {
                    Image(systemName: "arrow.left")
                        .font(.title2)
                        .foregroundColor(.white)
                        .frame(width: 44, height: 44)
                }
                
                VStack(alignment: .leading, spacing: 4) {
                    Text("Level \(level.level)")
                        .font(.system(size: 16, weight: .bold))
                        .foregroundColor(.white)
                    
                    LevelProgressBar(level: level)
                        .frame(width: 100)
                }
            }
            
            Spacer()
            
            // Right side: Hearts, question counter, timer, and streak
            VStack(alignment: .trailing, spacing: 4) {
                // Hearts
                HStack(spacing: 5) {
                    ForEach(0..<lives, id: \.self) { _ in
                        Image(systemName: "heart.fill")
                            .foregroundColor(Color(red: 1.0, green: 0.41, blue: 0.71))
                            .font(.system(size: 20))
                    }
                }
                
                // Question counter
                Text("\(questionIndex + 1)/\(totalQuestions)")
                    .font(.system(size: 16, weight: .medium))
                    .foregroundColor(.white)
                
                // Timer
                Text("Time: \(timer)")
                    .font(.system(size: 14))
                    .foregroundColor(.white.opacity(0.8))
                
                // Streak display
                if streakInfo.currentStreak > 0 {
                    StreakDisplay(streakInfo: streakInfo)
                }
            }
        }
        .padding(.top, 40)
        .padding(.horizontal, 16)
        .padding(.bottom, 8)
    }
}

// MARK: - Tip Bubble Component (Simplified for Static)
struct TipBubbleComponent: View {
    let bubble: TipBubble
    let isSelected: Bool
    let gameActive: Bool
    let onBubbleTapped: (TipBubble) -> Void
    
    @State private var scale: CGFloat = 1.0
    @State private var showPulse = false

    var body: some View {
        Button(action: {
            print("🎯 BUBBLE TAPPED! Amount: $\(String(format: "%.2f", bubble.tipAmount)), Correct: \(bubble.isCorrect)")
            guard gameActive else {
                print("🔴 Tap ignored - game not active")
                return
            }
            onBubbleTapped(bubble)
        }) {
            VStack(spacing: 4) {
                Text("$")
                    .font(.system(size: 16, weight: .bold))
                    .foregroundColor(textColor)

                Text(String(format: "%.2f", bubble.tipAmount))
                    .font(.system(size: 18, weight: .bold))
                    .foregroundColor(textColor)
            }
            .frame(width: 120, height: 120)
            .background(
                Circle()
                    .fill(backgroundColor)
                    .overlay(
                        Circle()
                            .stroke(borderColor, lineWidth: isSelected ? 4 : 2)
                    )
                    .overlay(
                        Circle()
                            .stroke(borderColor.opacity(0.5), lineWidth: 2)
                            .scaleEffect(showPulse ? 1.2 : 1.0)
                            .opacity(showPulse ? 0 : 1)
                            .animation(
                                isSelected ?
                                Animation.easeInOut(duration: 1.0).repeatForever(autoreverses: false) :
                                .default,
                                value: showPulse
                            )
                    )
            )
            .scaleEffect(scale)
            .shadow(color: .black.opacity(0.3), radius: 8, x: 0, y: 4)
            .opacity(gameActive ? 1.0 : 0.7)
        }
        .buttonStyle(PlainButtonStyle())
        .disabled(!gameActive)
        .onAppear {
            withAnimation(.easeInOut(duration: 0.3)) {
                scale = isSelected ? 1.1 : 1.0
            }
        }
        .onChange(of: isSelected) { newValue in
            withAnimation(.easeInOut(duration: 0.3)) {
                scale = newValue ? 1.1 : 1.0
                showPulse = newValue
            }
        }
    }

    private var backgroundColor: Color {
        switch (isSelected, bubble.isCorrect) {
        case (true, true): return Color(red: 0.30, green: 0.69, blue: 0.31).opacity(0.9)
        case (true, false): return Color(red: 0.90, green: 0.24, blue: 0.24).opacity(0.9)
        default: return Color.white.opacity(0.9)
    }}

    private var textColor: Color {
        isSelected ? .white : .black
    }

    private var borderColor: Color {
        switch (isSelected, bubble.isCorrect) {
        case (true, true): return Color(red: 1.0, green: 0.84, blue: 0.0)
        case (true, false): return Color.red
        default: return Color.gray.opacity(0.5)
    }}
}

// MARK: - Data Models
struct TipBubble: Identifiable {
    let id = UUID().uuidString
    let tipAmount: Double
    let billAmount: Double
    let isCorrect: Bool
    var positionX: CGFloat
    var positionY: CGFloat
    let speed: CGFloat
    
    init(id: String = UUID().uuidString, tipAmount: Double, billAmount: Double, isCorrect: Bool, positionX: CGFloat, positionY: CGFloat, speed: CGFloat) {
        self.tipAmount = tipAmount
        self.billAmount = billAmount
        self.isCorrect = isCorrect
        self.positionX = positionX
        self.positionY = positionY
        self.speed = speed
    }
}

struct TipPuzzleData {
    let billAmount: Double
    let tipPercentage: Double
    let correctTipAmount: Double
}

// MARK: - Preview
struct TipBubblePuzzleView_Previews: PreviewProvider {
    static var previews: some View {
        let sampleQuestion = """
        {
            "billAmount": 45.50,
            "tipPercentage": 18.0,
            "correctTipAmount": 8.19
        }
        """
        
        let samplePuzzle = Puzzle(
            question: sampleQuestion,
            answer: "8.19",
            hint: "",
            options: [],
            format: "bubble",
            puzzleType: "mathtipping",
            puzzleId: "sample",
            id: "sample",
            name: "Tip Calculation",
            createdAt: Date().timeIntervalSince1970 * 1000,
            status: "ready",
            difficulty: "Easy"
        )
        
        TipBubblePuzzleView(
            puzzle: samplePuzzle,
            questionIndex: 0,
            totalQuestions: 1,
            onAnswerSubmitted: { _, _ in },
            onNextPuzzle: { },
            onExit: { }
        )
    }
}
