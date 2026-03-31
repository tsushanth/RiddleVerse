import SwiftUI

struct SwipeWordView: View {
    let puzzle: Puzzle
    let questionIndex: Int
    let totalQuestions: Int
    let onAnswerSubmitted: (String, Bool) -> Void
    let onNextPuzzle: () -> Void
    let onExit: () -> Void
    
    @State private var currentWordIndex = 0
    @State private var allWords: [(String, Bool)] = []
    @State private var timeLeft = 60
    @State private var correctCount = 0
    @State private var showCompletionDialog = false
    @State private var timer: Timer?
    @State private var dragOffset: CGSize = .zero
    @State private var feedbackTimer: Timer?
    @StateObject private var feedbackManager = UnifiedFeedbackManager()
    @StateObject private var progressionManager = ProgressionManager()
    
    private let totalTime = 60
    
    // Swipe thresholds
    private let swipeThreshold: CGFloat = 150
    
    var currentWord: (String, Bool)? {
        guard currentWordIndex < allWords.count else { return nil }
        return allWords[currentWordIndex]
    }
    
    var wordConnotationData: WordConnotationData? {
        guard let questionData = puzzle.question.data(using: .utf8),
              let json = try? JSONSerialization.jsonObject(with: questionData) as? [String: Any],
              let topic = json["topic"] as? String,
              let positiveWords = json["positiveWords"] as? [String],
              let negativeWords = json["negativeWords"] as? [String],
              let hint = json["hint"] as? String else {
            return nil
        }
        
        return WordConnotationData(
            topic: topic,
            positiveWords: positiveWords,
            negativeWords: negativeWords,
            hint: hint
        )
    }
    
    var body: some View {
        ZStack {
            // Background gradient
            LinearGradient(
                colors: [Color(red: 0.97, green: 0.9, blue: 0.83), Color(red: 0.92, green: 0.83, blue: 0.75)],
                startPoint: .top,
                endPoint: .bottom
            )
            .ignoresSafeArea()
            
            VStack {
                // Top section
                headerSection
                
                Divider()
                    .background(Color(red: 0.43, green: 0.30, blue: 0.25))
                    .padding(.horizontal)
                
                Spacer()
                
                // Main content
                if let word = currentWord {
                    mainWordCard(word: word.0)
                } else {
                    completionMessage
                }
                
                Spacer()
                
                // Instructions
                swipeInstructions
                
                // Hint
                if let data = wordConnotationData {
                    hintSection(hint: data.hint)
                }
                
                Spacer()
            }
            .padding()
        }
        .withUnifiedFeedback(feedbackManager)
        .trackPuzzleViewOnce(
                    puzzleId: "id",
                    puzzleType: "swipeWord",
                    difficulty: "Easy",
                    questionIndex: 0
                )
        .onAppear {
            setupWords()
            startTimer()
        }
        .onDisappear {
            stopTimer()
        }
        .alert("Puzzle Completed!", isPresented: $showCompletionDialog) {
            Button("Next Puzzle") {
                onNextPuzzle()
            }
            Button("Back to Home") {
                onExit()
            }
        } message: {
            VStack {
                Text("Your score: \(correctCount)/\(allWords.count)")
                Text("Time remaining: \(timeLeft)s")
            }
        }
    }
    
    // MARK: - Header Section
    // MARK: - Enhanced Header Section
    private var headerSection: some View {
        EnhancedSwipeWordTopGameBar(
            level: progressionManager.currentLevel,
            streakInfo: progressionManager.streakInfo,
            timer: "\(timeLeft)s",
            topic: wordConnotationData?.topic ?? "Words",
            correctCount: correctCount,
            totalWords: allWords.count,
            onExit: onExit
        )
    }
    
    // MARK: - Main Word Card
    private func mainWordCard(word: String) -> some View {
        Text(word)
            .font(.system(size: 24, weight: .bold))
            .foregroundColor(.white)
            .frame(maxWidth: .infinity)
            .frame(height: 150)
            .background(
                RoundedRectangle(cornerRadius: 12)
                    .fill(Color(red: 0.30, green: 0.25, blue: 0.21))
            )
            .offset(dragOffset)
            .rotationEffect(.degrees(Double(dragOffset.width / 10)))
            .opacity(1 - Double(abs(dragOffset.width)) / 500)
            .scaleEffect(1 - Double(abs(dragOffset.width)) / 1000)
            .gesture(
                DragGesture()
                    .onChanged { value in
                                // Only allow dragging if feedback isn't showing
                                if !feedbackManager.isShowingFeedback{
                                    dragOffset = value.translation
                                }
                            }
                            .onEnded { value in
                                handleSwipeEnd(translation: value.translation)
                            }
            )
            .animation(.spring(response: 0.6, dampingFraction: 0.8), value: dragOffset)
    }
    
    // MARK: - Swipe Instructions
    private var swipeInstructions: some View {
        HStack {
            // Left swipe (Negative)
            VStack {
                Text("SWIPE LEFT")
                    .font(.system(size: 14, weight: .bold))
                    .foregroundColor(Color(red: 0.43, green: 0.30, blue: 0.25))
                
                Text("(Negative)")
                    .font(.system(size: 12))
                    .foregroundColor(Color(red: 0.43, green: 0.30, blue: 0.25))
                
                Image(systemName: "arrow.left")
                    .font(.title2)
                    .foregroundColor(Color(red: 0.43, green: 0.30, blue: 0.25))
            }
            
            Spacer()
            
            // Right swipe (Positive)
            VStack {
                Text("SWIPE RIGHT")
                    .font(.system(size: 14, weight: .bold))
                    .foregroundColor(Color(red: 0.43, green: 0.30, blue: 0.25))
                
                Text("(Positive)")
                    .font(.system(size: 12))
                    .foregroundColor(Color(red: 0.43, green: 0.30, blue: 0.25))
                
                Image(systemName: "arrow.right")
                    .font(.title2)
                    .foregroundColor(Color(red: 0.43, green: 0.30, blue: 0.25))
            }
        }
    }
    
    // MARK: - Hint Section
    private func hintSection(hint: String) -> some View {
        Text("💡 \(hint)")
            .font(.system(size: 14))
            .italic()
            .foregroundColor(Color(red: 0.43, green: 0.30, blue: 0.25))
            .multilineTextAlignment(.center)
            .padding(.horizontal)
    }
    
    // MARK: - Completion Message
    private var completionMessage: some View {
        Text("All words completed!")
            .font(.system(size: 24, weight: .bold))
            .foregroundColor(Color(red: 0.43, green: 0.30, blue: 0.25))
            .frame(maxWidth: .infinity, maxHeight: .infinity)
    }
    
    // MARK: - Feedback Overlay
    private func feedbackOverlay(isCorrect: Bool, message: String) -> some View {
        VStack {
            Text(message)
                .font(.title2)
                .fontWeight(.bold)
                .foregroundColor(.white)
                .padding()
                .background(
                    RoundedRectangle(cornerRadius: 12)
                        .fill(isCorrect ? Color.green : Color.red)
                )
        }
        .transition(.scale.combined(with: .opacity))
        .zIndex(1)
    }
    
    // MARK: - Helper Methods
    private func setupWords() {
        guard let data = wordConnotationData else { return }
        
        let positiveWords = data.positiveWords.map { ($0, true) }
        let negativeWords = data.negativeWords.map { ($0, false) }
        
        allWords = (positiveWords + negativeWords).shuffled()
        currentWordIndex = 0
    }
    
    private func startTimer() {
        timer = Timer.scheduledTimer(withTimeInterval: 1.0, repeats: true) { _ in
            if timeLeft > 0 && !allWords.isEmpty && currentWordIndex < allWords.count && !feedbackManager.isShowingFeedback {
                timeLeft -= 1
            } else if timeLeft == 0 {
                stopTimer()
                showTimeUpFeedback()
            }
        }
    }
    
    private func stopTimer() {
        timer?.invalidate()
        timer = nil
        feedbackTimer?.invalidate()
        feedbackTimer = nil
    }
    
    private func handleSwipeEnd(translation: CGSize) {
        // Don't process swipes if feedback is showing
        guard !feedbackManager.isShowingFeedback else {
            withAnimation(.spring()) {
                dragOffset = .zero
            }
            return
        }
        
        let swipeDistance = abs(translation.width)
        
        if swipeDistance > swipeThreshold {
            let isSwipeRight = translation.width > 0
            processSwipe(isPositiveSwipe: isSwipeRight)
        } else {
            // Reset position if swipe wasn't far enough
            withAnimation(.spring()) {
                dragOffset = .zero
            }
        }
    }
    
    private func processSwipe(isPositiveSwipe: Bool) {
        guard let word = currentWord, !feedbackManager.isShowingFeedback else { return }
        
        let isCorrect = isPositiveSwipe == word.1
        
        if isCorrect {
            correctCount += 1
        }
        
        onAnswerSubmitted(word.0, isCorrect)
        
        // Animate card off screen first
        withAnimation(.easeInOut(duration: 0.3)) {
            dragOffset = CGSize(width: isPositiveSwipe ? 500 : -500, height: 0)
        }
        
        // Small delay to let animation start, then show feedback
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.1) {
            self.feedbackManager.showFeedback(
                puzzleType: "connotationwords",
                isCorrect: isCorrect,
                userAnswer: isPositiveSwipe ? "Positive" : "Negative",
                correctAnswer: word.1 ? "Positive" : "Negative",
                timeSpent: Double(totalTime - timeLeft),
                difficulty: self.puzzle.difficulty,
                timeRemaining: self.timeLeft,
                totalTime: self.totalTime,
                onComplete: {
                    self.moveToNextWordAfterFeedback()
                }
            )
        }
    }
    
    
    
    private func moveToNextWordAfterFeedback() {
        
        currentWordIndex += 1
        
        // Reset the card position
        withAnimation(.spring()) {
            dragOffset = .zero
        }
        
        if currentWordIndex >= allWords.count {
            showCompletionDialog = true
        }
    }
    
    private func moveToNextWord() {
        moveToNextWordAfterFeedback()
    }
    
    private func showTimeUpFeedback() {
        feedbackManager.showFeedback(
            puzzleType: "connotationwords",
            isCorrect: false,
            userAnswer: "Time's up!",
            correctAnswer: "N/A",
            timeSpent: Double(totalTime),
            difficulty: puzzle.difficulty,
            timeRemaining: 0,
            totalTime: totalTime,
            onComplete: {
                showCompletionDialog = true
            }
        )
    }
}

struct EnhancedSwipeWordTopGameBar: View {
    let level: UserLevel
    let streakInfo: StreakInfo
    let timer: String
    let topic: String
    let correctCount: Int
    let totalWords: Int
    let onExit: () -> Void
    
    var body: some View {
        HStack {
            // Left side: Back button and level
            HStack(spacing: 12) {
                Button(action: onExit) {
                    Image(systemName: "arrow.left")
                        .font(.title2)
                        .foregroundColor(Color(red: 0.43, green: 0.30, blue: 0.25))
                }
                
                VStack(alignment: .leading, spacing: 4) {
                    Text("Level \(level.level)")
                        .font(.system(size: 16, weight: .bold))
                        .foregroundColor(Color(red: 0.43, green: 0.30, blue: 0.25))
                    
                    // Custom progress bar for this view's color scheme
                    VStack(alignment: .leading, spacing: 2) {
                        HStack(spacing: 4) {
                            Text("\(level.currentXP)/\(level.xpToNextLevel) XP")
                                .font(.system(size: 10))
                                .foregroundColor(Color(red: 0.43, green: 0.30, blue: 0.25).opacity(0.8))
                        }
                        
                        ProgressView(value: level.progressPercentage)
                            .progressViewStyle(LinearProgressViewStyle(tint: Color(red: 0.43, green: 0.30, blue: 0.25)))
                            .background(Color(red: 0.43, green: 0.30, blue: 0.25).opacity(0.3))
                            .cornerRadius(3)
                            .frame(width: 100, height: 6)
                    }
                }
            }
            
            Spacer()
            
            // Center: Topic
            VStack(spacing: 2) {
                Text(topic.uppercased())
                    .font(.system(size: 18, weight: .bold))
                    .foregroundColor(Color(red: 0.43, green: 0.30, blue: 0.25))
                
                Text("Swipe the word left or right")
                    .font(.system(size: 12))
                    .foregroundColor(Color(red: 0.43, green: 0.30, blue: 0.25).opacity(0.8))
            }
            
            Spacer()
            
            // Right side: Stats and streak
            VStack(alignment: .trailing, spacing: 4) {
                Text("Time: \(timer)")
                    .font(.system(size: 14, weight: .medium))
                    .foregroundColor(Color(red: 0.43, green: 0.30, blue: 0.25))
                
                Text("Words: \(correctCount)/\(totalWords)")
                    .font(.system(size: 12))
                    .foregroundColor(Color(red: 0.43, green: 0.30, blue: 0.25))
                
                // Streak display with custom colors
                if streakInfo.currentStreak > 0 {
                    HStack(spacing: 4) {
                        Text("🔥")
                            .font(.system(size: 14))
                        
                        Text("\(streakInfo.currentStreak)")
                            .font(.system(size: 12, weight: .bold))
                            .foregroundColor(Color(red: 0.43, green: 0.30, blue: 0.25))
                        
                        if streakInfo.hasStreakBonus {
                            Text("×\(String(format: "%.1f", streakInfo.streakMultiplier))")
                                .font(.system(size: 10, weight: .medium))
                                .foregroundColor(Color(red: 0.43, green: 0.30, blue: 0.25).opacity(0.8))
                        }
                    }
                }
            }
        }
    }
}

// MARK: - Data Models
struct WordConnotationData {
    let topic: String
    let positiveWords: [String]
    let negativeWords: [String]
    let hint: String
}

// MARK: - Preview
struct SwipeWordView_Previews: PreviewProvider {
    static var previews: some View {
        let sampleQuestion = """
        {
            "topic": "Emotions",
            "positiveWords": ["happy", "joyful", "excited", "cheerful"],
            "negativeWords": ["sad", "angry", "frustrated", "disappointed"],
            "hint": "Think about whether the word expresses a positive or negative emotion"
        }
        """
        
        let samplePuzzle = Puzzle(
            question: sampleQuestion,
            answer: "",
            hint: "",
            options: [],
            format: "swipe",
            puzzleType: "connotationwords",
            puzzleId: "sample",
            id: "sample",
            name: "Word Connotations",
            createdAt: Date().timeIntervalSince1970 * 1000,
            status: "ready",
            difficulty: "Easy"
        )
        
        SwipeWordView(
            puzzle: samplePuzzle,
            questionIndex: 0,
            totalQuestions: 1,
            onAnswerSubmitted: { _, _ in },
            onNextPuzzle: { },
            onExit: { }
        )
    }
}
