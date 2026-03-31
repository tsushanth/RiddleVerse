import SwiftUI

struct AnagramPuzzleView: View {
    let puzzle: Puzzle
    let questionIndex: Int
    let totalQuestions: Int
    let onAnswerSubmitted: (String, Bool) -> Void
    let onNextPuzzle: () -> Void
    let onExit: () -> Void
    
    
    @State private var userAnswer: String = ""
    @State private var timeRemaining: Int = 67
    @StateObject private var progressionManager = ProgressionManager()
    @StateObject private var feedbackManager = UnifiedFeedbackManager()
    @State private var sessionStartTime = Date()
    
    // Get scrambled letters from the question
    private var scrambledLetters: [String] {
        let question = puzzle.question.trimmingCharacters(in: .whitespacesAndNewlines)
        return question.map { String($0) }
    }
    
    // Get correct answer
    private var correctAnswer: String {
        return puzzle.answer.lowercased().trimmingCharacters(in: .whitespacesAndNewlines)
    }
    
    var body: some View {
        ZStack {
            // Background gradient
            LinearGradient(
                gradient: Gradient(colors: [Color(red: 0, green: 0.74, blue: 0.83), Color(red: 0.2, green: 0.8, blue: 0.9)]),
                startPoint: .top,
                endPoint: .bottom
            )
            .ignoresSafeArea()
            
            VStack(spacing: 20) {
                // Top bar
                HStack {
                    Button(action: onExit) {
                        Image(systemName: "pause.fill")
                            .font(.title2)
                            .foregroundColor(.white)
                    }
                    
                    // Level and progress
                    VStack(alignment: .leading) {
                        Text("Level \(progressionManager.currentLevel.level)")
                            .font(.caption)
                            .fontWeight(.bold)
                            .foregroundColor(.white)
                        
                        LevelProgressBar(level: progressionManager.currentLevel)
                    }
                    
                    Spacer()
                    
                    // Lives (can be made dynamic)
                    HStack {
                        Image(systemName: "heart.fill")
                            .foregroundColor(.red)
                        Text("4")
                            .font(.title2)
                            .foregroundColor(.white)
                    }
                    
                    Spacer()
                    
                    // Timer and streak
                    VStack(alignment: .trailing) {
                        Text("1:07") // Make this dynamic
                            .font(.title2)
                            .foregroundColor(.white)
                        
                        if progressionManager.streakInfo.currentStreak > 0 {
                            StreakDisplay(streakInfo: progressionManager.streakInfo)
                        }
                    }
                }
                .padding(.horizontal)
                .padding(.top)
                
                Spacer()
                
                // Hint text
                if !puzzle.hint.isEmpty {
                    Text(puzzle.hint)
                        .font(.headline)
                        .foregroundColor(.white)
                        .multilineTextAlignment(.center)
                        .padding(.horizontal)
                }
                
                // User's answer box
                Text(userAnswer.isEmpty ? "Tap letters below" : userAnswer)
                    .font(.title)
                    .fontWeight(.bold)
                    .foregroundColor(.black)
                    .frame(maxWidth: .infinity)
                    .frame(height: 80)
                    .background(
                        RoundedRectangle(cornerRadius: 12)
                            .fill(Color.white.opacity(0.9))
                    )
                    .padding(.horizontal, 40)
                
                // Letter grid
                LazyVGrid(columns: Array(repeating: GridItem(.flexible(), spacing: 10), count: 3), spacing: 15) {
                    ForEach(scrambledLetters.indices, id: \.self) { index in
                        LetterButton(letter: scrambledLetters[index]) {
                            addLetter(scrambledLetters[index])
                        }
                        .disabled(feedbackManager.isShowingFeedback)
                    }
                }
                .padding(.horizontal, 60)
                
                Spacer()
                
                // Bottom buttons
                HStack(spacing: 20) {
                    Button("Skip") {
                        // Skip to next puzzle
                        onNextPuzzle()
                    }
                    .padding()
                    .frame(width: 100)
                    .background(Color.white.opacity(0.8))
                    .foregroundColor(.black)
                    .cornerRadius(8)
                    
                    Button("Submit") {
                        checkAnswer()
                    }
                    .padding()
                    .frame(maxWidth: .infinity)
                    .background(Color.white.opacity(0.8))
                    .foregroundColor(.black)
                    .cornerRadius(8)
                    .disabled(userAnswer.isEmpty)
                    
                    Button(action: clearAnswer) {
                        Image(systemName: "xmark")
                            .font(.title2)
                    }
                    .padding()
                    .frame(width: 60)
                    .background(Color.white.opacity(0.8))
                    .foregroundColor(.black)
                    .cornerRadius(8)
                    .disabled(feedbackManager.isShowingFeedback)
                }
                .padding(.horizontal)
                .padding(.bottom, 40)
                
            }
        }
        .navigationBarHidden(true)
        .withUnifiedFeedback(feedbackManager)
        .trackPuzzleViewOnce(
                    puzzleId: puzzle.id,
                    puzzleType: "anagram",
                    difficulty: puzzle.difficulty,
                    questionIndex: questionIndex
                )
        .onAppear {
            sessionStartTime = Date()
            let puzzleStartTime = Date()
            print("🔵 AnagramPuzzleView appeared")
            print("🔵 Question (scrambled): '\(puzzle.question)'")
            print("🔵 Answer: '\(puzzle.answer)'")
            print("🔵 Hint: '\(puzzle.hint)'")
            print("🔵 Scrambled letters: \(scrambledLetters)")
        }
    }
    
    private func addLetter(_ letter: String) {
        guard !feedbackManager.isShowingFeedback else { return } // ADD THIS
        userAnswer += letter.lowercased()
        print("🔵 Added letter: \(letter), Current answer: \(userAnswer)")
    }

    private func clearAnswer() {
        guard !feedbackManager.isShowingFeedback else { return } // ADD THIS
        userAnswer = ""
        print("🔵 Cleared answer")
    }
    
    private func checkAnswer() {
        guard !feedbackManager.isShowingFeedback else { return }
        let isAnswerCorrect = userAnswer.lowercased() == correctAnswer.lowercased()
        
        print("🔵 Checking answer: '\(userAnswer)' vs '\(correctAnswer)' = \(isAnswerCorrect)")
        
        onAnswerSubmitted(userAnswer, isAnswerCorrect)
        
        // Use unified feedback system
        feedbackManager.showFeedback(
            puzzleType: "anagram",
            isCorrect: isAnswerCorrect,
            userAnswer: userAnswer,
            correctAnswer: correctAnswer,
            timeSpent: Date().timeIntervalSince(sessionStartTime),
            difficulty: puzzle.difficulty,
            hintsUsed: 0, // Add hint tracking if needed
            timeRemaining: timeRemaining,
            totalTime: 67,
            onComplete: {
                // Reset state and advance
                self.userAnswer = ""
                self.onNextPuzzle()
            }
        )
    }
}

struct LetterButton: View {
    let letter: String
    let action: () -> Void
    
    var body: some View {
        Button(action: action) {
            Text(letter.lowercased())
                .font(.title)
                .fontWeight(.bold)
                .foregroundColor(.white)
                .frame(width: 60, height: 60)
                .background(
                    RoundedRectangle(cornerRadius: 8)
                        .fill(Color.black.opacity(0.6))
                )
        }
        .buttonStyle(PlainButtonStyle())
    }
}

// Preview
struct AnagramPuzzleView_Previews: PreviewProvider {
    static var previews: some View {
        let samplePuzzle = Puzzle(
            question: "MECLOWE", // scrambled letters for "WELCOME"
            answer: "welcome",
            hint: "to receive someone gladly",
            options: [],
            format: "anagram",
            puzzleType: "anagram",
            puzzleId: "sample-id",
            id: "sample-id",
            name: "Sample Anagram",
            createdAt: Date().timeIntervalSince1970 * 1000,
            status: "ready",
            difficulty: "Easy"
        )
        
        AnagramPuzzleView(
            puzzle: samplePuzzle,
            questionIndex: 0,
            totalQuestions: 5,
            onAnswerSubmitted: { answer, isCorrect in
                print("Answer: \(answer), Correct: \(isCorrect)")
            },
            onNextPuzzle: {
                print("Next puzzle requested")
            },
            onExit: {
                print("Exit requested")
            }
        )
    }
}
