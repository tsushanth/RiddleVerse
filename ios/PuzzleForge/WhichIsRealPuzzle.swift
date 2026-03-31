//
//  WhichIsRealPuzzle.swift
//  RiddleVerse
//
//  Created by Sushanth Tiruvaipati on 10/11/25.
//

import SwiftUI

// MARK: - Data Models

struct WhichIsRealPuzzle: Codable {
    let puzzleId: String
    let type: String
    let category: String
    let displayCategory: String
    let subjectName: String
    let description: String
    let imageA: ImageData
    let imageB: ImageData
    let instructions: Instructions
    let timeLimit: Int
    let expertClues: ExpertClues
    let reasoningEnabled: Bool
    let communityLearning: Bool
    
    struct ImageData: Codable {
        let url: String
        let position: String
    }
    
    struct Instructions: Codable {
        let task: String
        let method: String
        let scoring: String
        let tips: [String]
    }
    
    struct ExpertClues: Codable {
        let real: [String]
        let ai: [String]
    }
}

struct WhichIsRealAnswer: Codable {
    let correctAnswer: String
    let imageDetails: ImageDetails
    let expertClues: ExpertClues
    let educationalValue: EducationalValue
    
    struct ImageDetails: Codable {
        let imageA: ImageInfo
        let imageB: ImageInfo
        
        enum CodingKeys: String, CodingKey {
            case imageA = "image_a"
            case imageB = "image_b"
        }
    }
    
    struct ImageInfo: Codable {
        let type: String
        let source: String
        let photographer: String?
    }
    
    struct ExpertClues: Codable {
        let real: [String]
        let ai: [String]
    }
    
    struct EducationalValue: Codable {
        let learningGoal: String
        let keySkills: [String]
    }
}

// MARK: - Main View

struct WhichIsRealView: View {
    let puzzle: Puzzle
    let onBack: () -> Void
    let onComplete: (Bool, Int) -> Void
    
    @State private var showWelcome = true
    @State private var gameStarted = false
    @State private var selectedImage: String?
    @State private var isAnswerRevealed = false
    @State private var userWasCorrect = false
    @State private var totalScore = 0
    @State private var showExpertClues = false
    @State private var showReasoningDialog = false
    @State private var userReasoning = ""
    @State private var showCommunityReasoningDialog = false
    
    // Timer state
    @State private var timeRemaining: Int = 90
    @State private var displayTimer = "1:30"
    @State private var gameStartTime: Date?
    @State private var timer: Timer?
    
    // Parsed data
    @State private var puzzleData: WhichIsRealPuzzle?
    @State private var answerData: WhichIsRealAnswer?
    
    var body: some View {
        ZStack {
            Color(red: 0.1, green: 0.1, blue: 0.18)
                .ignoresSafeArea()
            
            if showWelcome {
                WelcomeScreen(
                    onStart: {
                        showWelcome = false
                        gameStarted = true
                        startTimer()
                    },
                    onBack: onBack
                )
            } else {
                mainGameView
            }
            
            // Reasoning Dialog
            if showReasoningDialog {
                ReasoningDialogView(
                    onSubmit: { reasoning in
                        submitReasoning(reasoning)
                    },
                    onSkip: {
                        showReasoningDialog = false
                    }
                )
            }
            
            // Community Reasoning Dialog
            if showCommunityReasoningDialog {
                CommunityReasoningDialogView(
                    onDismiss: {
                        showCommunityReasoningDialog = false
                    }
                )
            }
        }
        .onAppear {
            parsePuzzleData()
        }
        .onDisappear {
            timer?.invalidate()
        }
    }
    
    private var mainGameView: some View {
        VStack(spacing: 0) {
            // Top Bar
            TopBarView(
                displayTimer: displayTimer,
                timeRemaining: timeRemaining,
                totalScore: totalScore,
                onBack: onBack,
                onShowClues: {
                    showExpertClues.toggle()
                }
            )
            
            ScrollView {
                VStack(spacing: 16) {
                    // Challenge Card
                    if let puzzleData = puzzleData {
                        ChallengeCardView(
                            puzzle: puzzleData,
                            showExpertClues: showExpertClues
                        )
                        .padding(.horizontal)
                    }
                    
                    // Image Comparison Section
                    if let puzzleData = puzzleData {
                        ImageComparisonView(
                            puzzle: puzzleData,
                            selectedImage: selectedImage,
                            isAnswerRevealed: isAnswerRevealed,
                            correctAnswer: answerData?.correctAnswer ?? "",
                            onImageSelected: { position in
                                if !isAnswerRevealed {
                                    submitAnswer(position)
                                }
                            }
                        )
                        .padding(.horizontal)
                    }
                    
                    // Result Section
                    if isAnswerRevealed {
                        ResultSectionView(
                            userWasCorrect: !userWasCorrect,
                            puzzleData: puzzleData,
                            answerData: answerData,
                            totalScore: totalScore,
                            onViewCommunityReasoning: {
                                showCommunityReasoningDialog = true
                            }
                        )
                        .padding(.horizontal)
                        
                        Button(action: {
                            onComplete(userWasCorrect, totalScore)
                        }) {
                            Text("Continue to Next Puzzle")
                                .font(.system(size: 16, weight: .bold))
                                .foregroundColor(.white)
                                .frame(maxWidth: .infinity)
                                .frame(height: 56)
                                .background(Color(red: 0.42, green: 0.36, blue: 0.90))
                                .cornerRadius(12)
                        }
                        .padding(.horizontal)
                        .padding(.top, 16)
                    }
                }
                .padding(.vertical, 16)
            }
        }
    }
    
    // MARK: - Helper Functions
    
    private func parsePuzzleData() {
        // Parse question JSON
        if let questionData = puzzle.question.data(using: .utf8),
           let decoded = try? JSONDecoder().decode(WhichIsRealPuzzle.self, from: questionData) {
            puzzleData = decoded
            timeRemaining = decoded.timeLimit / 1000
            displayTimer = formatTime(timeRemaining)
        }
        
        // Parse answer JSON
        if let answerData = puzzle.answer.data(using: .utf8),
           let decoded = try? JSONDecoder().decode(WhichIsRealAnswer.self, from: answerData) {
            self.answerData = decoded
        }
    }
    
    private func startTimer() {
        gameStartTime = Date()
        timer = Timer.scheduledTimer(withTimeInterval: 1.0, repeats: true) { _ in
            if timeRemaining > 0 && gameStarted && !isAnswerRevealed {
                timeRemaining -= 1
                displayTimer = formatTime(timeRemaining)
            } else if timeRemaining == 0 && !isAnswerRevealed {
                // Time's up
                isAnswerRevealed = true
                userWasCorrect = false
                totalScore = 0
            }
        }
    }
    
    private func formatTime(_ seconds: Int) -> String {
        let minutes = seconds / 60
        let secs = seconds % 60
        return String(format: "%d:%02d", minutes, secs)
    }
    
    private func submitAnswer(_ selectedPosition: String) {
        guard let startTime = gameStartTime,
              let correctAnswer = answerData?.correctAnswer else { return }
        
        let timeSpent = Date().timeIntervalSince(startTime)
        let correct = selectedPosition == correctAnswer
        
        selectedImage = selectedPosition
        isAnswerRevealed = true
        userWasCorrect = correct
        
        // Provide haptic feedback
        let generator = UIImpactFeedbackGenerator(style: correct ? .heavy : .light)
        generator.impactOccurred()
        
        // Calculate score
        totalScore = calculateScore(correct: correct, timeSpent: timeSpent, providedReasoning: false)
        
        // Show reasoning dialog (30% chance for correct answers)
        if correct && Double.random(in: 0...1) < 0.3 {
            DispatchQueue.main.asyncAfter(deadline: .now() + 0.5) {
                showReasoningDialog = true
            }
        }
    }
    
    private func calculateScore(correct: Bool, timeSpent: TimeInterval, providedReasoning: Bool) -> Int {
        if !correct { return 0 }
        
        let baseScore: Int
        switch puzzle.difficulty.lowercased() {
        case "easy": baseScore = 100
        case "medium": baseScore = 150
        case "hard": baseScore = 200
        default: baseScore = 150
        }
        
        // Speed bonus
        let timeBonus: Int
        switch timeSpent {
        case 0...15: timeBonus = Int(Double(baseScore) * 0.4)
        case 16...30: timeBonus = Int(Double(baseScore) * 0.2)
        case 31...60: timeBonus = Int(Double(baseScore) * 0.1)
        default: timeBonus = 0
        }
        
        // Reasoning bonus
        let reasoningBonus = providedReasoning ? Int(Double(baseScore) * 0.2) : 0
        
        return baseScore + timeBonus + reasoningBonus
    }
    
    private func submitReasoning(_ reasoning: String) {
        if !reasoning.isEmpty {
            userReasoning = reasoning
            // Recalculate score with reasoning bonus
            if let startTime = gameStartTime {
                let timeSpent = Date().timeIntervalSince(startTime)
                totalScore = calculateScore(correct: userWasCorrect, timeSpent: timeSpent, providedReasoning: true)
            }
            
            // TODO: Save reasoning to backend
            print("User reasoning submitted: \(reasoning)")
        }
        showReasoningDialog = false
    }
}

// MARK: - Welcome Screen

struct WelcomeScreen: View {
    let onStart: () -> Void
    let onBack: () -> Void
    
    var body: some View {
        VStack(spacing: 24) {
            // Back button
            HStack {
                Button(action: onBack) {
                    Image(systemName: "arrow.left")
                        .font(.system(size: 20))
                        .foregroundColor(.white)
                        .frame(width: 48, height: 48)
                }
                Spacer()
            }
            .padding(.horizontal)
            
            Spacer()
            
            // Title
            Text("🔍 Which Is Real?")
                .font(.system(size: 32, weight: .bold))
                .foregroundColor(.white)
                .multilineTextAlignment(.center)
            
            // Educational Card
            VStack(alignment: .leading, spacing: 16) {
                HStack(spacing: 12) {
                    Image(systemName: "info.circle.fill")
                        .font(.system(size: 32))
                        .foregroundColor(Color(red: 0.42, green: 0.36, blue: 0.90))
                    
                    Text("Why This Matters")
                        .font(.system(size: 20, weight: .bold))
                        .foregroundColor(.white)
                }
                
                Text("In today's world, AI can generate incredibly realistic images. Distinguishing between real and AI-generated content is becoming an essential skill for media literacy and critical thinking.")
                    .font(.system(size: 14))
                    .foregroundColor(.white.opacity(0.9))
                    .lineSpacing(4)
                
                Divider()
                    .background(Color.white.opacity(0.2))
                
                Text("🎯 Your Challenge:")
                    .font(.system(size: 16, weight: .bold))
                    .foregroundColor(Color(red: 0.42, green: 0.36, blue: 0.90))
                
                VStack(alignment: .leading, spacing: 8) {
                    BulletPointView(text: "Look at two similar images")
                    BulletPointView(text: "Identify which one is AI-generated")
                    BulletPointView(text: "Learn from expert clues and community insights")
                    BulletPointView(text: "Develop your AI detection skills!")
                }
            }
            .padding(20)
            .background(Color(red: 0.09, green: 0.13, blue: 0.24))
            .cornerRadius(16)
            .padding(.horizontal)
            
            Spacer()
            
            // Start Button
            Button(action: onStart) {
                Text("Start Challenge")
                    .font(.system(size: 18, weight: .bold))
                    .foregroundColor(.white)
                    .frame(maxWidth: .infinity)
                    .frame(height: 56)
                    .background(Color(red: 0.42, green: 0.36, blue: 0.90))
                    .cornerRadius(12)
            }
            .padding(.horizontal)
            .padding(.bottom, 32)
        }
    }
}

struct BulletPointView: View {
    let text: String
    
    var body: some View {
        HStack(alignment: .top, spacing: 8) {
            Text("•")
                .font(.system(size: 14))
                .foregroundColor(.white.opacity(0.8))
            
            Text(text)
                .font(.system(size: 14))
                .foregroundColor(.white.opacity(0.8))
        }
    }
}

// MARK: - Top Bar

struct TopBarView: View {
    let displayTimer: String
    let timeRemaining: Int
    let totalScore: Int
    let onBack: () -> Void
    let onShowClues: () -> Void
    
    var body: some View {
        HStack {
            Button(action: onBack) {
                Image(systemName: "arrow.left")
                    .font(.system(size: 20))
                    .foregroundColor(.white)
                    .frame(width: 40, height: 40)
            }
            
            Spacer()
            
            VStack(spacing: 4) {
                Text(displayTimer)
                    .font(.system(size: 20, weight: .bold))
                    .foregroundColor(timeRemaining <= 30 ? .red : .white)
                
                if totalScore > 0 {
                    Text("Score: \(totalScore)")
                        .font(.system(size: 12))
                        .foregroundColor(Color(red: 0.42, green: 0.36, blue: 0.90))
                }
            }
            
            Spacer()
            
            Button(action: onShowClues) {
                Image(systemName: "questionmark.circle")
                    .font(.system(size: 20))
                    .foregroundColor(.white)
                    .frame(width: 40, height: 40)
            }
        }
        .padding(.horizontal)
        .padding(.vertical, 8)
    }
}

// MARK: - Challenge Card

struct ChallengeCardView: View {
    let puzzle: WhichIsRealPuzzle
    let showExpertClues: Bool
    
    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text(puzzle.subjectName)
                .font(.system(size: 20, weight: .bold))
                .foregroundColor(.white)
            
            Text(puzzle.description)
                .font(.system(size: 14))
                .foregroundColor(.white.opacity(0.8))
            
            if showExpertClues {
                Divider()
                    .background(Color.white.opacity(0.2))
                
                Text("🎓 Expert Clues:")
                    .font(.system(size: 14, weight: .bold))
                    .foregroundColor(Color(red: 0.42, green: 0.36, blue: 0.90))
                
                VStack(alignment: .leading, spacing: 8) {
                    Text("Look for real photos:")
                        .font(.system(size: 12, weight: .bold))
                        .foregroundColor(Color(red: 0.15, green: 0.68, blue: 0.38))
                    
                    ForEach(puzzle.expertClues.real.prefix(2), id: \.self) { clue in
                        Text("• \(clue)")
                            .font(.system(size: 11))
                            .foregroundColor(.white.opacity(0.7))
                    }
                    
                    Text("AI tells:")
                        .font(.system(size: 12, weight: .bold))
                        .foregroundColor(Color(red: 0.90, green: 0.30, blue: 0.24))
                        .padding(.top, 4)
                    
                    ForEach(puzzle.expertClues.ai.prefix(2), id: \.self) { clue in
                        Text("• \(clue)")
                            .font(.system(size: 11))
                            .foregroundColor(.white.opacity(0.7))
                    }
                }
            }
        }
        .padding(16)
        .background(Color(red: 0.09, green: 0.13, blue: 0.24))
        .cornerRadius(16)
    }
}

// MARK: - Image Comparison

struct ImageComparisonView: View {
    let puzzle: WhichIsRealPuzzle
    let selectedImage: String?
    let isAnswerRevealed: Bool
    let correctAnswer: String
    let onImageSelected: (String) -> Void
    
    var body: some View {
        VStack(spacing: 16) {
            Text("Which image is AI-generated?")
                .font(.system(size: 18, weight: .bold))
                .foregroundColor(.white)
            
            // Image A
            ImageCardView(
                imageUrl: puzzle.imageA.url,
                position: puzzle.imageA.position,
                label: "Image A",
                isSelected: selectedImage == puzzle.imageA.position,
                isAnswerRevealed: isAnswerRevealed,
                isCorrectAnswer: correctAnswer != puzzle.imageA.position, // Inverted: correct = not AI
                onSelect: {
                    onImageSelected(puzzle.imageA.position)
                }
            )
            
            // Image B
            ImageCardView(
                imageUrl: puzzle.imageB.url,
                position: puzzle.imageB.position,
                label: "Image B",
                isSelected: selectedImage == puzzle.imageB.position,
                isAnswerRevealed: isAnswerRevealed,
                isCorrectAnswer: correctAnswer != puzzle.imageB.position, // Inverted: correct = not AI
                onSelect: {
                    onImageSelected(puzzle.imageB.position)
                }
            )
        }
    }
}

struct ImageCardView: View {
    let imageUrl: String
    let position: String
    let label: String
    let isSelected: Bool
    let isAnswerRevealed: Bool
    let isCorrectAnswer: Bool
    let onSelect: () -> Void
    
    private var borderColor: Color {
        if isAnswerRevealed && isCorrectAnswer {
            return Color(red: 0.90, green: 0.30, blue: 0.24) // Red for AI
        } else if isAnswerRevealed && !isCorrectAnswer {
            return Color(red: 0.15, green: 0.68, blue: 0.38) // Green for real
        } else if isSelected {
            return Color(red: 0.42, green: 0.36, blue: 0.90) // Purple for selected
        } else {
            return Color.white.opacity(0.3)
        }
    }
    
    private var borderWidth: CGFloat {
        (isSelected || isAnswerRevealed) ? 4 : 2
    }
    
    var body: some View {
        Button(action: {
            if !isAnswerRevealed {
                onSelect()
            }
        }) {
            ZStack(alignment: .topLeading) {
                AsyncImage(url: URL(string: imageUrl)) { image in
                    image
                        .resizable()
                        .aspectRatio(contentMode: .fill)
                } placeholder: {
                    ProgressView()
                }
                .frame(height: 250)
                .clipped()
                .cornerRadius(16)
                
                // Label
                Text(label)
                    .font(.system(size: 14, weight: .bold))
                    .foregroundColor(.white)
                    .padding(.horizontal, 12)
                    .padding(.vertical, 6)
                    .background(Color.black.opacity(0.7))
                    .cornerRadius(8)
                    .padding(12)
                
                // Result indicator
                if isAnswerRevealed {
                    HStack {
                        Spacer()
                        Text(isCorrectAnswer ? "🤖 AI" : "📷 Real")
                            .font(.system(size: 14, weight: .bold))
                            .foregroundColor(.white)
                            .padding(.horizontal, 12)
                            .padding(.vertical, 6)
                            .background(isCorrectAnswer ? Color(red: 0.90, green: 0.30, blue: 0.24) : Color(red: 0.15, green: 0.68, blue: 0.38))
                            .cornerRadius(8)
                            .padding(12)
                    }
                }
            }
        }
        .overlay(
            RoundedRectangle(cornerRadius: 16)
                .stroke(borderColor, lineWidth: borderWidth)
        )
        .disabled(isAnswerRevealed)
    }
}

// MARK: - Result Section

struct ResultSectionView: View {
    let userWasCorrect: Bool
    let puzzleData: WhichIsRealPuzzle?
    let answerData: WhichIsRealAnswer?
    let totalScore: Int
    let onViewCommunityReasoning: () -> Void
    
    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            HStack(spacing: 12) {
                Image(systemName: userWasCorrect ? "checkmark.circle.fill" : "xmark.circle.fill")
                    .font(.system(size: 32))
                    .foregroundColor(userWasCorrect ? Color(red: 0.15, green: 0.68, blue: 0.38) : Color(red: 0.90, green: 0.30, blue: 0.24))
                
                VStack(alignment: .leading) {
                    Text(userWasCorrect ? "Correct! 🎉" : "Not quite...")
                        .font(.system(size: 20, weight: .bold))
                        .foregroundColor(.white)
                    
                    if userWasCorrect {
                        Text("Score: +\(totalScore)")
                            .font(.system(size: 14))
                            .foregroundColor(Color(red: 0.42, green: 0.36, blue: 0.90))
                    }
                }
            }
            
            Divider()
                .background(Color.white.opacity(0.2))
            
            Text(userWasCorrect ?
                 "Great eye for detail! You correctly identified the AI-generated image." :
                 "The AI-generated image was \(answerData?.correctAnswer == "image_a" ? "Image A" : "Image B").")
                .font(.system(size: 14))
                .foregroundColor(.white.opacity(0.9))
            
            Button(action: onViewCommunityReasoning) {
                HStack {
                    Image(systemName: "person.3.fill")
                        .font(.system(size: 20))
                    
                    Text("Learn from Community Insights")
                        .font(.system(size: 14, weight: .medium))
                }
                .foregroundColor(.white)
                .frame(maxWidth: .infinity)
                .padding()
                .background(Color.clear)
                .overlay(
                    RoundedRectangle(cornerRadius: 12)
                        .stroke(Color.white.opacity(0.5), lineWidth: 2)
                )
            }
        }
        .padding(20)
        .background(userWasCorrect ?
                   Color(red: 0.15, green: 0.68, blue: 0.38).opacity(0.2) :
                   Color(red: 0.90, green: 0.30, blue: 0.24).opacity(0.2))
        .cornerRadius(16)
    }
}

// MARK: - Reasoning Dialog

struct ReasoningDialogView: View {
    @State private var reasoning = ""
    let onSubmit: (String) -> Void
    let onSkip: () -> Void
    
    var body: some View {
        ZStack {
            Color.black.opacity(0.4)
                .ignoresSafeArea()
                .onTapGesture {
                    onSkip()
                }
            
            VStack(spacing: 16) {
                Text("🎁 Bonus Question")
                    .font(.system(size: 20, weight: .bold))
                    .foregroundColor(.white)
                
                Text("Help others learn! Why did you think this was the AI image?")
                    .font(.system(size: 14))
                    .foregroundColor(.white.opacity(0.9))
                    .multilineTextAlignment(.center)
                
                TextEditor(text: $reasoning)
                    .frame(height: 120)
                    .padding(8)
                    .background(Color.white.opacity(0.1))
                    .cornerRadius(12)
                    .overlay(
                        RoundedRectangle(cornerRadius: 12)
                            .stroke(Color(red: 0.42, green: 0.36, blue: 0.90), lineWidth: 1)
                    )
                
                HStack(spacing: 12) {
                    Button(action: onSkip) {
                        Text("Skip")
                            .font(.system(size: 16, weight: .medium))
                            .foregroundColor(.white)
                            .frame(maxWidth: .infinity)
                            .padding()
                            .background(Color.clear)
                            .overlay(
                                RoundedRectangle(cornerRadius: 12)
                                    .stroke(Color.white.opacity(0.5), lineWidth: 2)
                            )
                    }
                    
                    Button(action: {
                        onSubmit(reasoning)
                    }) {
                        Text("Submit (+20% bonus)")
                            .font(.system(size: 16, weight: .medium))
                            .foregroundColor(.white)
                            .frame(maxWidth: .infinity)
                            .padding()
                            .background(Color(red: 0.42, green: 0.36, blue: 0.90))
                            .cornerRadius(12)
                    }
                    .disabled(reasoning.count < 10)
                    .opacity(reasoning.count < 10 ? 0.5 : 1.0)
                }
            }
            .padding(24)
            .background(Color(red: 0.09, green: 0.13, blue: 0.24))
            .cornerRadius(16)
            .padding(.horizontal, 32)
        }
    }
}

// MARK: - Community Reasoning Dialog

struct CommunityReasoningDialogView: View {
    let onDismiss: () -> Void
    
    // Mock data - replace with real data from backend
    private let mockReasonings = [
        ("The lighting was too perfect and even across the entire image", 42),
        ("Small details in the background looked slightly off", 38),
        ("The texture had an unnaturally smooth quality", 31)
    ]
    
    var body: some View {
        ZStack {
            Color.black.opacity(0.4)
                .ignoresSafeArea()
                .onTapGesture {
                    onDismiss()
                }
            
            VStack(alignment: .leading, spacing: 16) {
                Text("💡 Community Insights")
                    .font(.system(size: 20, weight: .bold))
                    .foregroundColor(.white)
                
                Text("Learn from others who identified the AI image correctly:")
                    .font(.system(size: 14))
                    .foregroundColor(.white.opacity(0.8))
                
                ScrollView {
                    VStack(spacing: 12) {
                        ForEach(mockReasonings, id: \.0) { reasoning in
                            CommunityReasoningItemView(
                                reasoning: reasoning.0,
                                helpfulVotes: reasoning.1
                            )
                        }
                    }
                }
                .frame(maxHeight: 300)
                
                Button(action: onDismiss) {
                    Text("Close")
                        .font(.system(size: 16, weight: .bold))
                        .foregroundColor(.white)
                        .frame(maxWidth: .infinity)
                        .padding()
                        .background(Color(red: 0.42, green: 0.36, blue: 0.90))
                        .cornerRadius(12)
                }
            }
            .padding(20)
            .background(Color(red: 0.09, green: 0.13, blue: 0.24))
            .cornerRadius(16)
            .padding(.horizontal, 32)
        }
    }
}

struct CommunityReasoningItemView: View {
    let reasoning: String
    let helpfulVotes: Int
    
    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text(reasoning)
                .font(.system(size: 13))
                .foregroundColor(.white)
            
            HStack(spacing: 8) {
                Image(systemName: "hand.thumbsup.fill")
                    .font(.system(size: 16))
                    .foregroundColor(Color(red: 0.42, green: 0.36, blue: 0.90))
                
                Text("\(helpfulVotes) found this helpful")
                    .font(.system(size: 11))
                    .foregroundColor(.white.opacity(0.7))
            }
        }
        .padding(12)
        .background(Color(red: 0.06, green: 0.20, blue: 0.38))
        .cornerRadius(12)
    }
}
