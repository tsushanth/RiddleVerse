//
//  ImageQuestionPuzzleView.swift
//  PuzzleForge
//
//  Complete iOS implementation of Image Question puzzle
//

import SwiftUI

// MARK: - Data Models
struct ImageQuestionPuzzleData: Codable {
    let puzzleId: String
    let imageUrl: String
    let theme: String
    let description: String
    let questions: [ImageQuestionItem]
    let totalQuestions: Int
    let timeLimit: Int
    let difficulty: String
}

struct ImageQuestionItem: Codable, Identifiable {
    let id: Int
    let question: String
    let answer: String
    let type: String
    let difficulty: String
    let options: [String]
    let hint: String
}

struct ImageQuestionAnswer: Identifiable {
    let id = UUID()
    let questionId: Int
    let selectedAnswer: String
    let isCorrect: Bool
    let answeredAt: Date = Date()
}

enum ImageQuestionGamePhase {
    case studyingImage
    case answeringQuestions
    case completed
}

// MARK: - Constants
private let IMAGE_REVEAL_PENALTY = 20

// MARK: - Main View
struct ImageQuestionPuzzleView: View {
    let puzzle: Puzzle
    let difficulty: String
    let round: String
    let onComplete: (Int, Int) -> Void
    let onBack: () -> Void
    
    @State private var puzzleData: ImageQuestionPuzzleData?
    @State private var isLoading = true
    @State private var hasError = false
    @State private var errorMessage = ""
    
    var body: some View {
        Group {
            if isLoading {
                ImageQuestionLoadingView(message: "Loading image question puzzle...")
            } else if hasError {
                ImageQuestionErrorView(
                    message: errorMessage,
                    onRetry: { loadPuzzleData() },
                    onSkip: { onComplete(0, 0) }
                )
            } else if let data = puzzleData {
                ImageQuestionGameView(
                    puzzleData: data,
                    difficulty: difficulty,
                    round: round,
                    onComplete: onComplete,
                    onBack: onBack
                )
            }
        }
        .navigationBarHidden(true)
        .onAppear { loadPuzzleData() }
    }
    
    private func loadPuzzleData() {
        isLoading = true
        hasError = false
        
        guard let questionData = puzzle.question.data(using: .utf8) else {
            errorMessage = "Invalid puzzle data"
            hasError = true
            isLoading = false
            return
        }
        
        do {
            let decoded = try JSONDecoder().decode(ImageQuestionPuzzleData.self, from: questionData)
            
            guard !decoded.imageUrl.isEmpty,
                  decoded.imageUrl.hasPrefix("http"),
                  !decoded.imageUrl.contains("PLACEHOLDER") else {
                errorMessage = "Image is still being generated. Please try again in a moment."
                hasError = true
                isLoading = false
                return
            }
            
            guard !decoded.questions.isEmpty else {
                errorMessage = "No questions available for this puzzle."
                hasError = true
                isLoading = false
                return
            }
            
            puzzleData = decoded
            isLoading = false
            
        } catch {
            errorMessage = "Failed to load puzzle: \(error.localizedDescription)"
            hasError = true
            isLoading = false
        }
    }
}

// MARK: - Game View
struct ImageQuestionGameView: View {
    let puzzleData: ImageQuestionPuzzleData
    let difficulty: String
    let round: String
    let onComplete: (Int, Int) -> Void
    let onBack: () -> Void
    
    @State private var gamePhase: ImageQuestionGamePhase = .studyingImage
    @State private var studyTimeRemaining = 5
    @State private var gameTimeRemaining: Int = 0
    @State private var currentQuestionIndex = 0
    @State private var answers: [ImageQuestionAnswer] = []
    @State private var imageRevealed = false
    @State private var imageRevealCount = 0
    @State private var imageLoaded = false
    @State private var showGrid = false
    
    @State private var studyTimer: Timer?
    @State private var gameTimer: Timer?
    
    var body: some View {
        ZStack {
            Color.black.ignoresSafeArea()
            
            VStack(spacing: 0) {
                ImageQuestionHeaderView(
                    difficulty: difficulty,
                    round: round,
                    gamePhase: gamePhase,
                    studyTimeRemaining: studyTimeRemaining,
                    gameTimeRemaining: gameTimeRemaining,
                    currentQuestion: currentQuestionIndex + 1,
                    totalQuestions: puzzleData.totalQuestions,
                    correctCount: answers.filter { $0.isCorrect }.count,
                    onBack: onBack
                )
                
                switch gamePhase {
                case .studyingImage:
                    ImageQuestionStudyPhase(
                        imageUrl: puzzleData.imageUrl,
                        theme: puzzleData.theme,
                        timeRemaining: imageLoaded ? studyTimeRemaining : 0,
                        totalQuestions: puzzleData.totalQuestions,
                        showGrid: showGrid,
                        onToggleGrid: { showGrid.toggle() },
                        onImageLoaded: {
                            imageLoaded = true
                            startStudyTimer()
                        }
                    )
                    
                case .answeringQuestions:
                    if currentQuestionIndex < puzzleData.questions.count {
                        ImageQuestionAnsweringPhase(
                            question: puzzleData.questions[currentQuestionIndex],
                            imageUrl: puzzleData.imageUrl,
                            theme: puzzleData.theme,
                            imageRevealed: imageRevealed,
                            imageRevealCount: imageRevealCount,
                            showGrid: showGrid,
                            selectedAnswer: answers.first(where: {
                                $0.questionId == puzzleData.questions[currentQuestionIndex].id
                            })?.selectedAnswer,
                            onAnswerSelected: handleAnswerSelected,
                            onRevealImage: handleRevealImage,
                            onToggleGrid: { showGrid.toggle() }
                        )
                    }
                    
                case .completed:
                    ImageQuestionResultsPhase(
                        puzzleData: puzzleData,
                        answers: answers,
                        imageRevealCount: imageRevealCount,
                        showGrid: $showGrid,
                        onContinue: handleContinue
                    )
                }
                
                if gamePhase == .answeringQuestions {
                    ImageQuestionProgressIndicator(
                        answeredCount: answers.count,
                        totalQuestions: puzzleData.totalQuestions,
                        correctCount: answers.filter { $0.isCorrect }.count,
                        imageRevealCount: imageRevealCount
                    )
                    .padding()
                }
            }
        }
        .onAppear { gameTimeRemaining = puzzleData.timeLimit }
        .onDisappear { stopTimers() }
    }
    
    private func startStudyTimer() {
        studyTimer = Timer.scheduledTimer(withTimeInterval: 1, repeats: true) { _ in
            if studyTimeRemaining > 0 {
                studyTimeRemaining -= 1
            } else {
                studyTimer?.invalidate()
                gamePhase = .answeringQuestions
                startGameTimer()
            }
        }
    }
    
    private func startGameTimer() {
        gameTimer = Timer.scheduledTimer(withTimeInterval: 1, repeats: true) { _ in
            if gameTimeRemaining > 0 {
                gameTimeRemaining -= 1
            } else {
                gameTimer?.invalidate()
                gamePhase = .completed
            }
        }
    }
    
    private func stopTimers() {
        studyTimer?.invalidate()
        gameTimer?.invalidate()
    }
    
    private func handleAnswerSelected(_ answer: String) {
        let question = puzzleData.questions[currentQuestionIndex]
        let isCorrect = answer == question.answer
        
        answers.removeAll { $0.questionId == question.id }
        answers.append(ImageQuestionAnswer(
            questionId: question.id,
            selectedAnswer: answer,
            isCorrect: isCorrect
        ))
        
        if currentQuestionIndex < puzzleData.totalQuestions - 1 {
            currentQuestionIndex += 1
        } else if answers.count >= puzzleData.totalQuestions {
            gamePhase = .completed
            stopTimers()
        }
    }
    
    private func handleRevealImage() {
        imageRevealed = true
        imageRevealCount += 1
    }
    
    private func handleContinue() {
        let correctAnswers = answers.filter { $0.isCorrect }.count
        let baseScore = calculateScore(
            correctAnswers: correctAnswers,
            totalQuestions: puzzleData.totalQuestions,
            timeRemaining: gameTimeRemaining,
            totalTime: puzzleData.timeLimit
        )
        let penaltyDeduction = imageRevealCount * IMAGE_REVEAL_PENALTY
        let finalScore = max(0, baseScore - penaltyDeduction)
        
        onComplete(finalScore, correctAnswers)
    }
    
    private func calculateScore(correctAnswers: Int, totalQuestions: Int, timeRemaining: Int, totalTime: Int) -> Int {
        let baseScore = Int((Double(correctAnswers) / Double(totalQuestions)) * 100)
        let timeBonus = Int((Double(timeRemaining) / Double(totalTime)) * 20)
        return min(baseScore + timeBonus, 100)
    }
}

// MARK: - Header View
struct ImageQuestionHeaderView: View {
    let difficulty: String
    let round: String
    let gamePhase: ImageQuestionGamePhase
    let studyTimeRemaining: Int
    let gameTimeRemaining: Int
    let currentQuestion: Int
    let totalQuestions: Int
    let correctCount: Int
    let onBack: () -> Void
    
    var body: some View {
        HStack {
            Button(action: onBack) {
                Image(systemName: "arrow.left")
                    .foregroundColor(.white)
                    .font(.title2)
            }
            
            Spacer()
            
            VStack(spacing: 2) {
                Text(difficulty.uppercased())
                    .font(.caption)
                    .fontWeight(.bold)
                    .foregroundColor(.white)
                
                Text(round)
                    .font(.caption2)
                    .foregroundColor(.white)
                
                Text("Image Questions")
                    .font(.caption2)
                    .foregroundColor(.cyan)
            }
            
            Spacer()
            
            VStack(alignment: .trailing, spacing: 2) {
                switch gamePhase {
                case .studyingImage:
                    Text("Study: \(studyTimeRemaining)")
                        .font(.headline)
                        .fontWeight(.bold)
                        .foregroundColor(.yellow)
                    Text("Memorize the image!")
                        .font(.caption2)
                        .foregroundColor(.yellow)
                    
                case .answeringQuestions:
                    Text(formatTime(gameTimeRemaining))
                        .font(.subheadline)
                        .fontWeight(.bold)
                        .foregroundColor(gameTimeRemaining < 30 ? .red : .white)
                    Text("Q: \(currentQuestion)/\(totalQuestions) • ✓: \(correctCount)")
                        .font(.caption2)
                        .foregroundColor(.white)
                    
                case .completed:
                    Text("Complete!")
                        .font(.subheadline)
                        .fontWeight(.bold)
                        .foregroundColor(.green)
                }
            }
        }
        .padding()
    }
    
    private func formatTime(_ seconds: Int) -> String {
        let minutes = seconds / 60
        let remainingSeconds = seconds % 60
        return String(format: "%d:%02d", minutes, remainingSeconds)
    }
}

// MARK: - Study Phase
struct ImageQuestionStudyPhase: View {
    let imageUrl: String
    let theme: String
    let timeRemaining: Int
    let totalQuestions: Int
    let showGrid: Bool
    let onToggleGrid: () -> Void
    let onImageLoaded: () -> Void
    
    var body: some View {
        VStack(spacing: 16) {
            if timeRemaining <= 0 {
                VStack(spacing: 16) {
                    ProgressView()
                        .scaleEffect(1.5)
                        .tint(.yellow)
                    Text("Loading image...")
                        .font(.title3)
                        .fontWeight(.bold)
                        .foregroundColor(.yellow)
                    Text("Countdown will start when ready")
                        .font(.subheadline)
                        .foregroundColor(.white)
                }
                .frame(maxWidth: .infinity, maxHeight: .infinity)
            } else {
                VStack(spacing: 16) {
                    Text("\(timeRemaining)")
                        .font(.system(size: 72, weight: .bold))
                        .foregroundColor(.yellow)
                    Text("Study this image carefully!")
                        .font(.title3)
                        .fontWeight(.bold)
                        .foregroundColor(.white)
                    Text("You'll answer \(totalQuestions) questions about it")
                        .font(.subheadline)
                        .foregroundColor(.cyan)
                }
            }
            
            ZStack(alignment: .topTrailing) {
                AsyncImage(url: URL(string: imageUrl)) { phase in
                    switch phase {
                    case .success(let image):
                        image
                            .resizable()
                            .aspectRatio(contentMode: .fit)
                            .onAppear { onImageLoaded() }
                    case .failure:
                        Image(systemName: "photo")
                            .resizable()
                            .aspectRatio(contentMode: .fit)
                            .foregroundColor(.gray)
                    case .empty:
                        ProgressView()
                    @unknown default:
                        EmptyView()
                    }
                }
                .frame(maxWidth: .infinity, maxHeight: .infinity)
                .overlay(
                    Group {
                        if showGrid && timeRemaining > 0 {
                            ImageQuestionGridOverlay(showLabels: true)
                        }
                    }
                )
                .overlay(
                    Group {
                        if timeRemaining > 0 {
                            VStack {
                                Spacer()
                                HStack {
                                    Text(theme)
                                        .font(.subheadline)
                                        .fontWeight(.bold)
                                        .foregroundColor(.white)
                                        .padding(8)
                                        .background(Color.black.opacity(0.7))
                                        .cornerRadius(8)
                                    Spacer()
                                }
                                .padding()
                            }
                        }
                    }
                )
                
                if timeRemaining > 0 {
                    Button(action: onToggleGrid) {
                        Image(systemName: showGrid ? "grid" : "grid")
                            .font(.title3)
                            .foregroundColor(.black)
                            .frame(width: 40, height: 40)
                            .background(showGrid ? Color.yellow.opacity(0.8) : Color.gray.opacity(0.8))
                            .clipShape(Circle())
                    }
                    .padding(8)
                }
            }
            .background(Color.gray.opacity(0.1))
            .cornerRadius(12)
            .padding()
            
            if showGrid && timeRemaining > 0 {
                Text("🔢 Grid sections: Top (1,2,3), Middle (4,5,6), Bottom (7,8,9)\nLeft (1,4,7), Center (2,5,8), Right (3,6,9)")
                    .font(.caption)
                    .foregroundColor(.yellow)
                    .multilineTextAlignment(.center)
                    .padding(8)
                    .background(Color.yellow.opacity(0.2))
                    .cornerRadius(8)
                    .padding(.horizontal)
            }
        }
    }
}

// MARK: - Answering Phase
struct ImageQuestionAnsweringPhase: View {
    let question: ImageQuestionItem
    let imageUrl: String
    let theme: String
    let imageRevealed: Bool
    let imageRevealCount: Int
    let showGrid: Bool
    let selectedAnswer: String?
    let onAnswerSelected: (String) -> Void
    let onRevealImage: () -> Void
    let onToggleGrid: () -> Void
    
    private var isLocationQuestion: Bool {
        question.question.lowercased().contains("where") ||
        question.question.lowercased().contains("location") ||
        question.options.contains { option in
            option.lowercased().contains("top") ||
            option.lowercased().contains("bottom") ||
            option.lowercased().contains("left") ||
            option.lowercased().contains("right") ||
            option.lowercased().contains("center")
        }
    }
    
    var body: some View {
        ScrollView {
            VStack(spacing: 16) {
                ZStack {
                    if imageRevealed {
                        AsyncImage(url: URL(string: imageUrl)) { image in
                            image
                                .resizable()
                                .aspectRatio(contentMode: .fit)
                        } placeholder: {
                            ProgressView()
                        }
                        .overlay(
                            Group {
                                if showGrid || isLocationQuestion {
                                    ImageQuestionGridOverlay(showLabels: isLocationQuestion)
                                }
                            }
                        )
                        .overlay(
                            VStack {
                                HStack {
                                    Spacer()
                                    Text("-\(IMAGE_REVEAL_PENALTY * imageRevealCount) pts")
                                        .font(.caption)
                                        .fontWeight(.bold)
                                        .foregroundColor(.white)
                                        .padding(8)
                                        .background(Color.red.opacity(0.8))
                                        .cornerRadius(8)
                                }
                                Spacer()
                            }
                            .padding(8)
                        )
                        .overlay(
                            VStack {
                                Spacer()
                                HStack {
                                    Text(theme)
                                        .font(.caption)
                                        .fontWeight(.bold)
                                        .foregroundColor(.white)
                                        .padding(8)
                                        .background(Color.black.opacity(0.7))
                                        .cornerRadius(8)
                                    Spacer()
                                }
                            }
                            .padding(8)
                        )
                    } else {
                        VStack(spacing: 12) {
                            Image(systemName: "eye.slash")
                                .font(.largeTitle)
                                .foregroundColor(.white)
                            Text("Image Hidden")
                                .font(.headline)
                                .fontWeight(.bold)
                                .foregroundColor(.white)
                            Text(isLocationQuestion ? "Location Question - Use Your Memory!" : "Use Your Memory!")
                                .font(.caption)
                                .foregroundColor(isLocationQuestion ? .yellow : .cyan)
                            Button(action: onRevealImage) {
                                VStack(spacing: 4) {
                                    Image(systemName: "eye")
                                        .font(.title3)
                                    Text("Reveal Image")
                                        .font(.subheadline)
                                        .fontWeight(.semibold)
                                    Text("-\(IMAGE_REVEAL_PENALTY) points")
                                        .font(.caption)
                                        .foregroundColor(.yellow)
                                }
                                .foregroundColor(.white)
                                .padding()
                                .background(Color.red.opacity(0.8))
                                .cornerRadius(12)
                            }
                        }
                        .frame(maxWidth: .infinity)
                        .frame(height: 200)
                        .background(Color.gray.opacity(0.3))
                        .cornerRadius(12)
                    }
                }
                .frame(height: isLocationQuestion ? 250 : 200)
                .padding(.horizontal)
                
                if isLocationQuestion && !imageRevealed {
                    Text("📍 This is a location question. Try to remember where objects were positioned in the 3×3 grid!")
                        .font(.caption)
                        .foregroundColor(.yellow)
                        .padding(8)
                        .background(Color.yellow.opacity(0.1))
                        .cornerRadius(8)
                        .padding(.horizontal)
                }
                
                VStack(alignment: .leading, spacing: 12) {
                    HStack {
                        Text("Question \(question.id)")
                            .font(.caption)
                            .fontWeight(.bold)
                            .foregroundColor(.cyan)
                        Spacer()
                        if isLocationQuestion {
                            Text("LOCATION")
                                .font(.caption2)
                                .fontWeight(.bold)
                                .foregroundColor(.yellow)
                        } else {
                            Text(question.type.capitalized.replacingOccurrences(of: "_", with: " "))
                                .font(.caption2)
                                .foregroundColor(.yellow)
                        }
                        if imageRevealed {
                            Image(systemName: "eye")
                                .font(.caption2)
                                .foregroundColor(.red)
                        }
                    }
                    
                    Text(question.question)
                        .font(.headline)
                        .foregroundColor(.white)
                    
                    ForEach(question.options, id: \.self) { option in
                        Button(action: {
                            if selectedAnswer == nil {
                                onAnswerSelected(option)
                            }
                        }) {
                            Text(option)
                                .font(.subheadline)
                                .foregroundColor(.white)
                                .frame(maxWidth: .infinity, alignment: .leading)
                                .padding()
                                .background(selectedAnswer == option ? Color.blue : Color.gray.opacity(0.4))
                                .cornerRadius(8)
                        }
                        .disabled(selectedAnswer != nil)
                    }
                    
                    if selectedAnswer != nil {
                        HStack {
                            Image(systemName: "lightbulb")
                                .foregroundColor(.yellow)
                            Text(question.hint)
                                .font(.caption)
                                .italic()
                                .foregroundColor(.yellow)
                        }
                        .padding(8)
                        .background(Color.yellow.opacity(0.1))
                        .cornerRadius(8)
                    }
                }
                .padding()
                .background(Color.gray.opacity(0.2))
                .cornerRadius(12)
                .padding(.horizontal)
            }
        }
    }
}

// MARK: - Results Phase
struct ImageQuestionResultsPhase: View {
    let puzzleData: ImageQuestionPuzzleData
    let answers: [ImageQuestionAnswer]
    let imageRevealCount: Int
    @Binding var showGrid: Bool
    let onContinue: () -> Void
    
    private var correctAnswers: Int {
        answers.filter { $0.isCorrect }.count
    }
    
    private var baseScore: Int {
        Int((Double(correctAnswers) / Double(puzzleData.totalQuestions)) * 100)
    }
    
    private var penaltyDeduction: Int {
        imageRevealCount * IMAGE_REVEAL_PENALTY
    }
    
    private var finalScore: Int {
        max(0, baseScore - penaltyDeduction)
    }
    
    var body: some View {
        ScrollView {
            VStack(spacing: 20) {
                Text("🎉 Results")
                    .font(.title)
                    .fontWeight(.bold)
                    .foregroundColor(.white)
                
                VStack(spacing: 12) {
                    Text("Final Score: \(finalScore)")
                        .font(.system(size: 32, weight: .bold))
                        .foregroundColor(.cyan)
                    
                    if penaltyDeduction > 0 {
                        VStack(spacing: 4) {
                            Text("Base Score: \(baseScore)")
                                .font(.subheadline)
                                .foregroundColor(.white)
                            Text("Image Reveals: \(imageRevealCount) × \(IMAGE_REVEAL_PENALTY) = -\(penaltyDeduction)")
                                .font(.caption)
                                .foregroundColor(.red)
                            HStack {
                                Image(systemName: "eye")
                                    .foregroundColor(.red)
                                Text("Next time, try to rely more on your memory!")
                                    .font(.caption)
                                    .foregroundColor(.red)
                            }
                            .padding(8)
                            .background(Color.red.opacity(0.2))
                            .cornerRadius(8)
                        }
                    }
                    
                    Text("Correct: \(correctAnswers)/\(puzzleData.totalQuestions)")
                        .font(.headline)
                        .foregroundColor(.white)
                    
                    let percentage = Int((Double(correctAnswers) / Double(puzzleData.totalQuestions)) * 100)
                    Text("\(percentage)% Accuracy")
                        .font(.subheadline)
                        .foregroundColor(percentage >= 80 ? .green : percentage >= 60 ? .yellow : .red)
                    
                    Text(performanceText(percentage: percentage))
                        .font(.title3)
                        .foregroundColor(.white)
                }
                .padding()
                .background(Color.gray.opacity(0.2))
                .cornerRadius(12)
                
                VStack(alignment: .leading, spacing: 12) {
                    HStack {
                        Text("📸 Reference Image")
                            .font(.headline)
                            .foregroundColor(.white)
                        Spacer()
                        Button(action: { showGrid.toggle() }) {
                            HStack {
                                Image(systemName: showGrid ? "grid" : "grid")
                                Text(showGrid ? "Hide Grid" : "Show Grid")
                            }
                            .font(.caption)
                            .foregroundColor(.black)
                            .padding(.horizontal, 12)
                            .padding(.vertical, 6)
                            .background(showGrid ? Color.yellow.opacity(0.8) : Color.gray.opacity(0.6))
                            .cornerRadius(16)
                        }
                    }
                    
                    ZStack {
                        AsyncImage(url: URL(string: puzzleData.imageUrl)) { image in
                            image.resizable().aspectRatio(contentMode: .fit)
                        } placeholder: {
                            ProgressView()
                        }
                        .overlay(
                            Group {
                                if showGrid {
                                    ImageQuestionGridOverlay(showLabels: true)
                                }
                            }
                        )
                        .overlay(
                            VStack {
                                Spacer()
                                HStack {
                                    Text(puzzleData.theme)
                                        .font(.caption)
                                        .fontWeight(.bold)
                                        .foregroundColor(.white)
                                        .padding(8)
                                        .background(Color.black.opacity(0.7))
                                        .cornerRadius(6)
                                    Spacer()
                                }
                            }
                            .padding(8)
                        )
                    }
                    .frame(height: 280)
                    .background(Color.black)
                    .cornerRadius(12)
                    
                    if showGrid {
                        Text("🔢 Grid positions: Top (1,2,3), Middle (4,5,6), Bottom (7,8,9)\nLeft (1,4,7), Center (2,5,8), Right (3,6,9)")
                            .font(.caption)
                            .foregroundColor(.yellow)
                            .multilineTextAlignment(.center)
                            .padding(8)
                            .background(Color.yellow.opacity(0.15))
                            .cornerRadius(8)
                    }
                }
                .padding()
                .background(Color.gray.opacity(0.1))
                .cornerRadius(12)
                
                Text("📝 Question Review")
                    .font(.headline)
                    .foregroundColor(.white)
                    .frame(maxWidth: .infinity, alignment: .leading)
                
                ForEach(Array(puzzleData.questions.enumerated()), id: \.element.id) { index, question in
                    ImageQuestionReviewCard(
                        question: question,
                        index: index,
                        userAnswer: answers.first(where: { $0.questionId == question.id })
                    )
                }
                
                Button(action: onContinue) {
                    Text("Continue")
                        .font(.headline)
                        .fontWeight(.bold)
                        .foregroundColor(.black)
                        .frame(maxWidth: .infinity)
                        .padding()
                        .background(Color.cyan)
                        .cornerRadius(12)
                }
                .padding(.top)
            }
            .padding()
        }
    }
    
    private func performanceText(percentage: Int) -> String {
        if percentage >= 90 && penaltyDeduction == 0 {
            return "🌟 Perfect Memory!"
        } else if percentage >= 90 {
            return "⭐ Excellent!"
        } else if percentage >= 80 && penaltyDeduction == 0 {
            return "🧠 Great Memory!"
        } else if percentage >= 80 {
            return "👍 Great Work!"
        } else if percentage >= 70 {
            return "👌 Good Work!"
        } else if percentage >= 60 {
            return "✅ Passed!"
        } else {
            return "📚 Keep Practicing!"
        }
    }
}

// MARK: - Supporting Views
struct ImageQuestionGridOverlay: View {
    let showLabels: Bool
    
    var body: some View {
        GeometryReader { geometry in
            ZStack {
                Path { path in
                    let width = geometry.size.width
                    let height = geometry.size.height
                    
                    path.move(to: CGPoint(x: width / 3, y: 0))
                    path.addLine(to: CGPoint(x: width / 3, y: height))
                    path.move(to: CGPoint(x: 2 * width / 3, y: 0))
                    path.addLine(to: CGPoint(x: 2 * width / 3, y: height))
                    
                    path.move(to: CGPoint(x: 0, y: height / 3))
                    path.addLine(to: CGPoint(x: width, y: height / 3))
                    path.move(to: CGPoint(x: 0, y: 2 * height / 3))
                    path.addLine(to: CGPoint(x: width, y: 2 * height / 3))
                }
                .stroke(style: StrokeStyle(lineWidth: 2, dash: [10, 5]))
                .foregroundColor(.white.opacity(0.8))
                
                if showLabels {
                    ForEach(0..<9) { index in
                        let row = index / 3
                        let col = index % 3
                        
                        Text("\(index + 1)")
                            .font(.caption)
                            .fontWeight(.bold)
                            .foregroundColor(.white)
                            .frame(width: 20, height: 20)
                            .background(Circle().fill(Color.black.opacity(0.7)))
                            .position(
                                x: CGFloat(col) * geometry.size.width / 3 + (col == 0 ? 16 : col == 2 ? geometry.size.width / 3 - 16 : geometry.size.width / 6),
                                y: CGFloat(row) * geometry.size.height / 3 + (row == 0 ? 16 : row == 2 ? geometry.size.height / 3 - 16 : geometry.size.height / 6)
                            )
                    }
                }
            }
        }
    }
}

struct ImageQuestionReviewCard: View {
    let question: ImageQuestionItem
    let index: Int
    let userAnswer: ImageQuestionAnswer?
    
    private var isLocationQuestion: Bool {
        question.question.lowercased().contains("where") ||
        question.question.lowercased().contains("location")
    }
    
    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack {
                HStack {
                    Text("Question \(index + 1)")
                        .font(.subheadline)
                        .fontWeight(.bold)
                        .foregroundColor(.cyan)
                    if isLocationQuestion {
                        Text("LOCATION")
                            .font(.caption2)
                            .fontWeight(.bold)
                            .foregroundColor(.yellow)
                            .padding(.horizontal, 6)
                            .padding(.vertical, 2)
                            .background(Color.yellow.opacity(0.3))
                            .cornerRadius(4)
                    }
                }
                Spacer()
                Image(systemName: userAnswer?.isCorrect == true ? "checkmark" : "xmark")
                    .foregroundColor(userAnswer?.isCorrect == true ? .green : .red)
            }
            
            Text(question.question)
                .font(.subheadline)
                .foregroundColor(.white)
            
            HStack {
                VStack(alignment: .leading, spacing: 4) {
                    Text("Your answer:")
                        .font(.caption)
                        .foregroundColor(.gray)
                    Text(userAnswer?.selectedAnswer ?? "Not answered")
                        .font(.caption)
                        .fontWeight(.bold)
                        .foregroundColor(userAnswer?.isCorrect == true ? .green : .red)
                }
                Spacer()
                if userAnswer?.isCorrect == false {
                    VStack(alignment: .trailing, spacing: 4) {
                        Text("Correct answer:")
                            .font(.caption)
                            .foregroundColor(.gray)
                        Text(question.answer)
                            .font(.caption)
                            .fontWeight(.bold)
                            .foregroundColor(.green)
                    }
                }
            }
            
            if isLocationQuestion {
                HStack {
                    Image(systemName: "location")
                        .font(.caption2)
                        .foregroundColor(.blue)
                    Text("💡 Check the reference image above with the grid to see exact positions")
                        .font(.caption2)
                        .italic()
                        .foregroundColor(.blue)
                }
                .padding(6)
                .background(Color.blue.opacity(0.1))
                .cornerRadius(6)
            }
        }
        .padding()
        .background((userAnswer?.isCorrect == true ? Color.green : Color.red).opacity(0.2))
        .cornerRadius(12)
    }
}

struct ImageQuestionProgressIndicator: View {
    let answeredCount: Int
    let totalQuestions: Int
    let correctCount: Int
    let imageRevealCount: Int
    
    var body: some View {
        VStack(spacing: 8) {
            ProgressView(value: Double(answeredCount), total: Double(totalQuestions))
                .tint(.cyan)
            HStack {
                Text("Progress: \(answeredCount)/\(totalQuestions)")
                    .font(.caption)
                    .foregroundColor(.white)
                Spacer()
                Text("Correct: \(correctCount)")
                    .font(.caption)
                    .foregroundColor(.green)
                if imageRevealCount > 0 {
                    Text("Reveals: \(imageRevealCount) (-\(imageRevealCount * IMAGE_REVEAL_PENALTY))")
                        .font(.caption2)
                        .foregroundColor(.red)
                }
            }
        }
    }
}

struct ImageQuestionLoadingView: View {
    let message: String
    
    var body: some View {
        VStack(spacing: 16) {
            ProgressView()
                .scaleEffect(1.5)
                .tint(.cyan)
            Text(message)
                .font(.subheadline)
                .foregroundColor(.white)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(Color.black)
    }
}

struct ImageQuestionErrorView: View {
    let message: String
    let onRetry: () -> Void
    let onSkip: () -> Void
    
    var body: some View {
        VStack(spacing: 20) {
            Image(systemName: "exclamationmark.triangle")
                .font(.system(size: 48))
                .foregroundColor(.red)
            Text("⚠️ Error")
                .font(.title)
                .fontWeight(.bold)
                .foregroundColor(.red)
            Text(message)
                .font(.subheadline)
                .foregroundColor(.white)
                .multilineTextAlignment(.center)
                .padding(.horizontal, 32)
            HStack(spacing: 16) {
                Button(action: onRetry) {
                    Text("Retry")
                        .fontWeight(.semibold)
                        .padding(.horizontal, 24)
                        .padding(.vertical, 12)
                        .background(Color.blue)
                        .foregroundColor(.white)
                        .cornerRadius(8)
                }
                Button(action: onSkip) {
                    Text("Skip")
                        .fontWeight(.semibold)
                        .padding(.horizontal, 24)
                        .padding(.vertical, 12)
                        .background(Color.gray)
                        .foregroundColor(.white)
                        .cornerRadius(8)
                }
            }
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(Color.black)
    }
}
