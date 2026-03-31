//
//  MultipleChoicePuzzleView.swift
//  PuzzleForge
//
//  Created by Sushanth Tiruvaipati on 5/23/25.
//

import AVFoundation
import SwiftUI

struct MultipleChoicePuzzleView: View {
    let puzzleId: String
    let question: String
    let options: [String]
    let correctAnswer: String
    let hint: String? // Add hint parameter
    let timerSeconds: Int
    let questionNumber: Int
    let totalQuestions: Int
    let onOptionSelected: (String) -> Void
    let onExit: (() -> Void)?
    
    // New leaderboard properties
    let leaderboardData: [LeaderboardEntry]?
    
    // Enhanced state management
    @StateObject private var feedbackManager = UnifiedFeedbackManager()
    @StateObject private var progressionManager = ProgressionManager()
    @StateObject private var timer = PuzzleTimer(totalTime: 60) // Default 60 seconds
    
    @State private var selectedOption: String?
    @State private var isAnswered = false
    @State private var showShareDialog = false
    @State private var showLeaderboard = false
    @State private var animateCorrect = false
    @State private var puzzleDifficulty: String = "Medium" // You can pass this from parent
    @State private var showHint = false
    @State private var hintUsed = false
    @State private var showExitConfirmation = false

    init(
        puzzleId: String,
        question: String,
        options: [String],
        selectedOption: String? = nil,
        correctAnswer: String,
        hint: String? = nil, // Add hint parameter
        timerSeconds: Int,
        questionNumber: Int,
        totalQuestions: Int,
        leaderboardData: [LeaderboardEntry]? = nil,
        puzzleDifficulty: String = "Medium",
        onOptionSelected: @escaping (String) -> Void,
        onExit: (() -> Void)? = nil
    ) {
        self.puzzleId = puzzleId
        self.question = question
        self.options = options
        self.correctAnswer = correctAnswer
        self.hint = hint // Store hint
        self.timerSeconds = timerSeconds
        self.questionNumber = questionNumber
        self.totalQuestions = totalQuestions
        self.leaderboardData = leaderboardData
        self.onOptionSelected = onOptionSelected
        self.onExit = onExit // Store exit callback
        self._puzzleDifficulty = State(initialValue: puzzleDifficulty)
        
        // Initialize timer with correct duration
        self._timer = StateObject(wrappedValue: PuzzleTimer(totalTime: timerSeconds))
    }

    var body: some View {
        GeometryReader { geometry in
            ZStack {
                // Background gradient similar to EstimationPuzzleView
                LinearGradient(
                    colors: [Color(red: 0.95, green: 0.95, blue: 0.98), Color(red: 0.90, green: 0.90, blue: 0.95)],
                    startPoint: .top,
                    endPoint: .bottom
                )
                .ignoresSafeArea()
                
                VStack(spacing: 0) {
                    // Enhanced top bar
                    topBar
                        .padding(.horizontal, 20)
                        .padding(.top, 10)
                    
                    Spacer(minLength: 20)
                    
                    // Question card
                    questionCard
                        .padding(.horizontal, 20)
                    
                    Spacer(minLength: 30)
                    
                    // Answer options
                    answerOptions
                        .padding(.horizontal, 20)
                    
                    Spacer()
                    
                    // Hint button
                    hintButton
                        .padding(.bottom, 20)
                    
                    // Banner ad
                    BannerAdView(adUnitId: "ca-app-pub-5764510017766009/7709321207")
                        .frame(width: 320, height: 50)
                        .padding(.bottom, 10)
                }
            }
        }
        .withUnifiedFeedback(feedbackManager)
        .sheet(isPresented: $showShareDialog) {
            SharePuzzleView(
                isPresented: $showShareDialog,
                puzzleId: puzzleId,
                onShare: sharePuzzle
            )
        }
       /* .sheet(isPresented: $showLeaderboard) {
            if let leaderboard = leaderboardData {
                LeaderboardView(entries: leaderboard)
                    .presentationDetents([.medium, .large])
            }
        }*/
        .trackPuzzleViewOnce(
                    puzzleId: "id",
                    puzzleType: "multipleChoice",
                    difficulty: "Easy",
                    questionIndex: 0
                )
        .onAppear {
            timer.start()
        }
        .alert("Exit Puzzle", isPresented: $showExitConfirmation) {
                    Button("Continue Playing", role: .cancel) {
                        showExitConfirmation = false
                    }
                    Button("Exit", role: .destructive) {
                        timer.stop()
                        onExit?()
                    }
                } message: {
                    Text("Are you sure you want to exit? Your progress will be lost.")
                }
        .onDisappear {
            timer.stop()
        }
        .onChange(of: timer.timeRemaining) { newTime in
            if newTime <= 0 && !isAnswered && !feedbackManager.isShowingFeedback {
                // Time's up - handle timeout
                handleTimeout()
            }
        }
        .navigationBarHidden(true)
    }
    
    // MARK: - Top Bar
    private var topBar: some View {
        EnhancedMultipleChoiceTopGameBar(
            level: progressionManager.currentLevel,
            streakInfo: progressionManager.streakInfo,
            timer: timer.formattedTime,
            questionIndex: questionNumber - 1, // Convert to 0-based index
            totalQuestions: totalQuestions,
            onExit: onExit != nil ? { showExitConfirmation = true } : nil,
            onShare: { showShareDialog = true },
            onLeaderboard: leaderboardData?.isEmpty == false ? { showLeaderboard = true } : nil
        )
    }
    
    // MARK: - Question Card
    private var questionCard: some View {
        VStack(spacing: 12) {
            Text("Question \(questionNumber)/\(totalQuestions)")
                .font(.system(size: 16, weight: .medium))
                .foregroundColor(.purple.opacity(0.8)) // This is fine - explicit color
            
            ScrollView {
                Text(question)
                    .font(.system(size: 20, weight: .semibold))
                    .multilineTextAlignment(.center)
                    .foregroundColor(.black) // FIXED: Changed from .primary to .black
                    .fixedSize(horizontal: false, vertical: true)
                    .padding(.horizontal)
            }
            .frame(maxHeight: 200)
        }
        .padding(20)
        .background(
            RoundedRectangle(cornerRadius: 16)
                .fill(Color.white)
                .shadow(color: .black.opacity(0.1), radius: 8, x: 0, y: 4)
        )
    }
    
    // MARK: - Answer Options
    private var answerOptions: some View {
        VStack(spacing: 12) {
            ForEach(options, id: \.self) { option in
                Button(action: {
                    selectOption(option)
                }) {
                    HStack {
                        Text(option)
                            .font(.system(size: 16, weight: .medium))
                            .multilineTextAlignment(.leading)
                            .padding(.leading)
                        
                        Spacer()
                        
                        if isAnswered {
                            if option == correctAnswer {
                                Image(systemName: "checkmark.circle.fill")
                                    .foregroundColor(.green)
                                    .font(.title2)
                                    .scaleEffect(animateCorrect ? 1.2 : 1.0)
                                    .animation(.spring(response: 0.5, dampingFraction: 0.6), value: animateCorrect)
                            } else if option == selectedOption {
                                Image(systemName: "xmark.circle.fill")
                                    .foregroundColor(.red)
                                    .font(.title2)
                            }
                        }
                    }
                    .padding()
                    .frame(maxWidth: .infinity, minHeight: 50)
                    .background(backgroundColor(for: option))
                    .foregroundColor(textColor(for: option))
                    .overlay(
                        RoundedRectangle(cornerRadius: 12)
                            .stroke(borderColor(for: option), lineWidth: 2)
                    )
                    .cornerRadius(12)
                    .shadow(color: .black.opacity(0.05), radius: 2, x: 0, y: 1)
                }
                .disabled(isAnswered || feedbackManager.isShowingFeedback)
                .scaleEffect(feedbackManager.isShowingFeedback ? 0.98 : 1.0)
                .animation(.easeInOut(duration: 0.1), value: feedbackManager.isShowingFeedback)
            }
        }
    }
    
    // MARK: - Hint Button
    private var hintButton: some View {
        Button(action: {
            showHint = true
            hintUsed = true
            hapticFeedback()
        }) {
            HStack(spacing: 8) {
                Image(systemName: hintUsed ? "lightbulb" : "lightbulb.fill")
                    .font(.system(size: 16))
                Text(hintUsed ? "Hint Used" : "Hint")
                    .font(.system(size: 16, weight: .medium))
            }
            .padding(.horizontal, 20)
            .padding(.vertical, 10)
            .background(
                RoundedRectangle(cornerRadius: 20)
                    .stroke(hintUsed ? Color.gray : Color.orange, lineWidth: 1.5)
            )
            .foregroundColor(hintUsed ? .gray : .orange)
        }
        .disabled(feedbackManager.isShowingFeedback || hintUsed)
        .alert("Hint", isPresented: $showHint) {
            Button("Got it!") {
                showHint = false
            }
        } message: {
            Text(generateHint())
        }
    }
    
    // MARK: - Helper Methods
    private func selectOption(_ option: String) {
        guard !isAnswered && !feedbackManager.isShowingFeedback else { return }
        
        hapticFeedback()
        selectedOption = option
        isAnswered = true
        timer.stop()
        
        let isCorrect = option == correctAnswer
        
        // Trigger animation for correct answers
        if isCorrect {
            withAnimation(.spring(response: 0.5, dampingFraction: 0.6)) {
                animateCorrect = true
            }
        }
        
        // Show enhanced feedback
        feedbackManager.showFeedback(
            puzzleType: "multipleChoice",
            isCorrect: isCorrect,
            userAnswer: option,
            correctAnswer: correctAnswer,
            timeSpent: Double(timer.totalTime - timer.timeRemaining),
            difficulty: puzzleDifficulty,
            hintsUsed: hintUsed ? 1 : 0,
            timeRemaining: timer.timeRemaining,
            totalTime: timer.totalTime,
            onComplete: {
                onOptionSelected(option)
            }
        )
    }
    
    private func handleTimeout() {
        guard !isAnswered && !feedbackManager.isShowingFeedback else { return }
        
        isAnswered = true
        // Don't set selectedOption for timeout, so no option gets marked as selected
        
        feedbackManager.showFeedback(
            puzzleType: "multipleChoice",
            isCorrect: false,               // ✅ Explicitly false for timeout
            userAnswer: "No answer (time's up)",
            correctAnswer: correctAnswer,
            timeSpent: Double(timer.totalTime - timer.timeRemaining),
            difficulty: puzzleDifficulty,
            hintsUsed: hintUsed ? 1 : 0,
            timeRemaining: timer.timeRemaining,
            totalTime: timer.totalTime,
            onComplete: {
                onOptionSelected("")        // ✅ Pass empty string for timeout
            }
        )
    }
    
    private func sharePuzzle(email: String, name: String, sender: String) {
        guard let url = URL(string: "https://puzzleverseai.com/share-riddle") else { return }
        
        var request = URLRequest(url: url)
        request.httpMethod = "POST"
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        
        let payload: [String: Any] = [
            "puzzleId": puzzleId,
            "recipientEmail": email,
            "recipientName": name,
            "senderId": sender
        ]
        
        do {
            request.httpBody = try JSONSerialization.data(withJSONObject: payload)
            
            URLSession.shared.dataTask(with: request) { _, response, error in
                if let error = error {
                    print("❌ Failed to share puzzle:", error.localizedDescription)
                    return
                }
                
                if let httpResponse = response as? HTTPURLResponse {
                    if httpResponse.statusCode == 200 {
                        print("✅ Puzzle shared successfully")
                    } else {
                        print("❌ Share failed with status code:", httpResponse.statusCode)
                    }
                }
            }.resume()
        } catch {
            print("❌ Failed to encode share payload:", error.localizedDescription)
        }
    }
    
    private func hapticFeedback() {
        let impactFeedback = UIImpactFeedbackGenerator(style: .medium)
        impactFeedback.impactOccurred()
    }
    
    // MARK: - Hint Generation
    private func generateHint() -> String {
        // If the puzzle has a hint property, use it
        if let puzzleHint = getHintFromPuzzle(), !puzzleHint.isEmpty {
            return puzzleHint
        }
        
        // Otherwise, generate a smart hint based on the options
        return generateSmartHint()
    }
    
    private func getHintFromPuzzle() -> String? {
        // Return the hint passed from the puzzle data
        return hint
    }
    
    private func generateSmartHint() -> String {
        let optionCount = options.count
        let correctAnswerLength = correctAnswer.count
        
        // Generic hints based on question analysis
        if question.lowercased().contains("capital") {
            return "Think about major cities and their countries. Consider which city is the political center."
        } else if question.lowercased().contains("year") || question.lowercased().contains("when") {
            return "Consider the historical timeline. Think about what period this event likely occurred."
        } else if question.lowercased().contains("who") {
            return "Think about famous people related to this topic. Consider their roles and contributions."
        } else if question.lowercased().contains("what") && question.lowercased().contains("formula") {
            return "Think about the mathematical relationship between the variables mentioned."
        } else if question.lowercased().contains("which") {
            return "Compare each option carefully. Look for the one that best fits the criteria mentioned."
        } else if correctAnswerLength > 15 {
            return "The correct answer is likely one of the longer, more detailed options."
        } else if correctAnswerLength < 8 {
            return "The correct answer is probably one of the shorter, more concise options."
        } else {
            // Elimination hint - remove one wrong option
            let wrongOptions = options.filter { $0 != correctAnswer }
            if let randomWrong = wrongOptions.randomElement() {
                return "You can eliminate '\(randomWrong)' - it's definitely not correct."
            }
        }
        
        return "Take your time and think through each option carefully. Use the process of elimination."
    }
    
    // MARK: - Styling Logic
    private func backgroundColor(for option: String) -> Color {
        if !isAnswered {
            return Color.white // White background when not answered
        }
        if option == correctAnswer {
            return Color.green.opacity(0.2) // Light green for correct
        } else if option == selectedOption {
            return Color.red.opacity(0.2) // Light red for wrong selection
        } else {
            return Color.white // White for unselected options
        }
    }

    private func borderColor(for option: String) -> Color {
        if !isAnswered {
            return Color.purple.opacity(0.3) // Purple border when not answered
        }
        if option == correctAnswer {
            return Color.green // Green border for correct
        } else if option == selectedOption {
            return Color.red // Red border for wrong selection
        } else {
            return Color.purple.opacity(0.3) // Keep purple for unselected
        }
    }

    private func textColor(for option: String) -> Color {
        // FIXED: Always use dark text that's visible on light backgrounds
        if isAnswered && option == selectedOption && option != correctAnswer {
            return .red // Red text for wrong selection
        } else if isAnswered && option == correctAnswer {
            return .green // Green text for correct answer
        }
        return .black // BLACK text instead of .primary (which can be white in dark mode)
    }
}

// MARK: - Enhanced Top Game Bar
struct EnhancedMultipleChoiceTopGameBar: View {
    let level: UserLevel
        let streakInfo: StreakInfo
        let timer: String
        let questionIndex: Int
        let totalQuestions: Int
        let onExit: (() -> Void)?
        let onShare: () -> Void
        let onLeaderboard: (() -> Void)?
        
        var body: some View {
            HStack {
                // Left side: Level info
                HStack(spacing: 12) {
                    // Exit/Back button
                    if let exitAction = onExit {
                        Button(action: exitAction) {
                            HStack(spacing: 6) {
                                Image(systemName: "xmark")
                                    .font(.system(size: 16, weight: .medium))
                                Text("Exit")
                                    .font(.system(size: 14, weight: .medium))
                            }
                            .foregroundColor(.white)
                            .padding(.horizontal, 12)
                            .padding(.vertical, 6)
                            .background(
                                RoundedRectangle(cornerRadius: 8)
                                    .fill(Color.red.opacity(0.8))
                            )
                        }
                    }
                    
                    VStack(alignment: .leading, spacing: 4) {
                        Text("Level \(level.level)")
                            .font(.system(size: 14, weight: .bold))
                            .foregroundColor(.white)
                        
                        LevelProgressBar(level: level)
                            .frame(width: 80)
                    }
                }
                
                Spacer()
                
                // Center: Question counter
                Text("\(questionIndex + 1)/\(totalQuestions)")
                    .font(.system(size: 18, weight: .semibold))
                    .foregroundColor(.white)
                
                Spacer()
                
                // Right side: Actions and info
                VStack(alignment: .trailing, spacing: 4) {
                    HStack(spacing: 12) {
                        // Share Button
                        Button(action: onShare) {
                            Image(systemName: "square.and.arrow.up")
                                .font(.system(size: 18))
                                .foregroundColor(.white)
                        }
                        
                        // Leaderboard Button (conditional)
                        if let leaderboardAction = onLeaderboard {
                            Button(action: leaderboardAction) {
                                Image(systemName: "trophy.fill")
                                    .font(.system(size: 18))
                                    .foregroundColor(.yellow)
                            }
                        }
                    }
                    
                    // Timer
                    Text(timer)
                        .font(.system(size: 16, weight: .bold))
                        .foregroundColor(.white)
                    
                    // Streak display
                    if streakInfo.currentStreak > 0 {
                        StreakDisplay(streakInfo: streakInfo)
                    }
                }
            }
            .padding(.horizontal, 16)
            .padding(.vertical, 12)
            .background(
                RoundedRectangle(cornerRadius: 12)
                    .fill(Color.black.opacity(0.7))
            )
        }
}


// MARK: - Preview
struct MultipleChoicePuzzleView_Previews: PreviewProvider {
    static var previews: some View {
        MultipleChoicePuzzleView(
            puzzleId: "sample-mc-puzzle",
            question: "What is the capital of France?",
            options: ["London", "Berlin", "Paris", "Madrid"],
            correctAnswer: "Paris",
            timerSeconds: 60,
            questionNumber: 1,
            totalQuestions: 5,
            leaderboardData: [
                LeaderboardEntry(userId: "Player1", score: 100, timeTaken: 15),
                LeaderboardEntry(userId: "Player2", score: 85, timeTaken: 20)
            ]
        ) { selectedOption in
            print("Selected: \(selectedOption)")
        }
    }
}
