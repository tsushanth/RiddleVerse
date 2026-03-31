//
//  OddOneOutView.swift
//  RiddleVerse
//
//  Created by Sushanth Tiruvaipati on 10/16/25.
//


//
//  OddOneOutView.swift
//  PuzzleForge
//

import SwiftUI
import Foundation

struct OddOneOutTopBar: View {
    let displayTimer: String
    let timeRemaining: Int
    let totalScore: Int
    let onBack: () -> Void
    let onShowHint: () -> Void
    
    var body: some View {
        HStack {
            Button(action: onBack) {
                Image(systemName: "arrow.left")
                    .font(.title2)
                    .foregroundColor(.white)
                    .frame(width: 40, height: 40)
            }
            
            Spacer()
            
            VStack(spacing: 4) {
                Text(displayTimer)
                    .font(.title3)
                    .fontWeight(.bold)
                    .foregroundColor(timeRemaining <= 10 ? .red : .white)
                
                if totalScore > 0 {
                    Text("Score: \(totalScore)")
                        .font(.caption)
                        .foregroundColor(Color(red: 0.42, green: 0.36, blue: 0.90))
                }
            }
            
            Spacer()
            
            Button(action: onShowHint) {
                Image(systemName: "questionmark.circle")
                    .font(.title2)
                    .foregroundColor(.white)
                    .frame(width: 40, height: 40)
            }
        }
        .padding()
    }
}

// MARK: - Challenge Card

struct OddOneOutChallengeCard: View {
    let puzzle: OddOneOutPuzzle
    let showHint: Bool
    
    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text("🔍 Odd One Out")
                .font(.title3)
                .fontWeight(.bold)
                .foregroundColor(.white)

            Text("Look at the images carefully and find the one that doesn't belong with the others.")
                .font(.subheadline)
                .foregroundColor(.white.opacity(0.8))
            
            if showHint {
                Divider()
                    .background(Color.white.opacity(0.2))
                
                VStack(alignment: .leading, spacing: 8) {
                    Text("💡 Hint:")
                        .font(.subheadline)
                        .fontWeight(.bold)
                        .foregroundColor(Color(red: 0.42, green: 0.36, blue: 0.90))
                    
                    if let hint = puzzle.questions.first?.hint {
                        Text(hint)
                            .font(.caption)
                            .foregroundColor(.white.opacity(0.7))
                    }
                }
            }
        }
        .padding()
        .background(
            RoundedRectangle(cornerRadius: 16)
                .fill(Color(red: 0.09, green: 0.13, blue: 0.24))
        )
    }
}

// MARK: - Images Grid

struct OddOneOutImagesGrid: View {
    let puzzle: OddOneOutPuzzle
    let selectedImage: String?
    let isAnswerRevealed: Bool
    let onImageSelected: (String) -> Void
    
    private let columns = [
        GridItem(.flexible(), spacing: 12),
        GridItem(.flexible(), spacing: 12)
    ]
    
    var body: some View {
        VStack(spacing: 16) {
            Text("Which one doesn't belong?")
                .font(.headline)
                .foregroundColor(.white)
            
            LazyVGrid(columns: columns, spacing: 12) {
                ForEach(puzzle.images) { image in
                    OddOneOutImageCard(
                        image: image,
                        isSelected: selectedImage == image.item.name,
                        isAnswerRevealed: isAnswerRevealed,
                        onSelect: {
                            let impact = UIImpactFeedbackGenerator(style: .light)
                            impact.impactOccurred()
                            onImageSelected(image.item.name)
                        }
                    )
                    .aspectRatio(1, contentMode: .fit)
                }
            }
        }
    }
}

// MARK: - Image Card

struct OddOneOutImageCard: View {
    let image: OddOneOutImageItem
    let isSelected: Bool
    let isAnswerRevealed: Bool
    let onSelect: () -> Void
    
    private var borderColor: Color {
        if isAnswerRevealed && image.isOddOneOut {
            return Color(red: 0.91, green: 0.30, blue: 0.24) // Red
        } else if isAnswerRevealed && !image.isOddOneOut {
            return Color(red: 0.15, green: 0.68, blue: 0.38) // Green
        } else if isSelected {
            return Color(red: 0.42, green: 0.36, blue: 0.90) // Purple
        } else {
            return Color.white.opacity(0.3)
        }
    }
    
    private var borderWidth: CGFloat {
        (isSelected || isAnswerRevealed) ? 4 : 2
    }
    
    var body: some View {
        Button(action: onSelect) {
            ZStack {
                AsyncImage(url: URL(string: image.url)) { phase in
                    switch phase {
                    case .empty:
                        ProgressView()
                            .frame(maxWidth: .infinity, maxHeight: .infinity)
                    case .success(let img):
                        img
                            .resizable()
                            .aspectRatio(contentMode: .fill)
                    case .failure:
                        Image(systemName: "photo")
                            .font(.largeTitle)
                            .foregroundColor(.gray)
                            .frame(maxWidth: .infinity, maxHeight: .infinity)
                    @unknown default:
                        EmptyView()
                    }
                }
                .clipped()
                
                // Label at bottom
                VStack {
                    Spacer()
                    Text(image.item.name)
                        .font(.subheadline)
                        .fontWeight(.bold)
                        .foregroundColor(.white)
                        .padding(8)
                        .frame(maxWidth: .infinity)
                        .background(Color.black.opacity(0.7))
                }
                
                // Result indicator
                if isAnswerRevealed {
                    VStack {
                        HStack {
                            Spacer()
                            Image(systemName: image.isOddOneOut ? "xmark" : "checkmark")
                                .font(.caption)
                                .foregroundColor(.white)
                                .padding(4)
                                .background(
                                    image.isOddOneOut ?
                                    Color(red: 0.91, green: 0.30, blue: 0.24) :
                                    Color(red: 0.15, green: 0.68, blue: 0.38)
                                )
                                .cornerRadius(8)
                                .padding(8)
                        }
                        Spacer()
                    }
                }
            }
        }
        .disabled(isAnswerRevealed)
        .overlay(
            RoundedRectangle(cornerRadius: 16)
                .stroke(borderColor, lineWidth: borderWidth)
        )
        .cornerRadius(16)
    }
}

// MARK: - Result Section

struct OddOneOutResultSection: View {
    let userWasCorrect: Bool
    let puzzle: OddOneOutPuzzle
    let totalScore: Int
    
    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            HStack(spacing: 12) {
                Image(systemName: userWasCorrect ? "checkmark.circle.fill" : "xmark.circle.fill")
                    .font(.title)
                    .foregroundColor(userWasCorrect ?
                        Color(red: 0.15, green: 0.68, blue: 0.38) :
                        Color(red: 0.91, green: 0.30, blue: 0.24)
                    )
                
                VStack(alignment: .leading) {
                    Text(userWasCorrect ? "Correct! 🎉" : "Not quite...")
                        .font(.title3)
                        .fontWeight(.bold)
                        .foregroundColor(.white)
                    
                    if userWasCorrect {
                        Text("Score: +\(totalScore)")
                            .font(.subheadline)
                            .foregroundColor(Color(red: 0.42, green: 0.36, blue: 0.90))
                    }
                }
            }
            
            Divider()
                .background(Color.white.opacity(0.2))
            
            Text("📚 Explanation:")
                .font(.headline)
                .foregroundColor(Color(red: 0.42, green: 0.36, blue: 0.90))
            
            Text(puzzle.scenario.explanation)
                .font(.subheadline)
                .foregroundColor(.white.opacity(0.9))
            
            if let oddImage = puzzle.images.first(where: { $0.isOddOneOut }) {
                VStack(alignment: .leading, spacing: 4) {
                    Text("The odd one out was: \(oddImage.item.name)")
                        .font(.subheadline)
                        .fontWeight(.bold)
                        .foregroundColor(Color(red: 0.91, green: 0.30, blue: 0.24))
                    
                    Text("Reason: \(oddImage.item.reason)")
                        .font(.caption)
                        .foregroundColor(.white.opacity(0.8))
                }
            }
        }
        .padding()
        .background(
            RoundedRectangle(cornerRadius: 16)
                .fill(userWasCorrect ?
                    Color(red: 0.15, green: 0.68, blue: 0.38).opacity(0.2) :
                    Color(red: 0.91, green: 0.30, blue: 0.24).opacity(0.2)
                )
        )
    }
}

struct OddOneOutWelcomeView: View {
    let onStart: () -> Void
    let onBack: () -> Void
    
    var body: some View {
        VStack(spacing: 24) {
            // Back button
            HStack {
                Button(action: onBack) {
                    Image(systemName: "arrow.left")
                        .font(.title2)
                        .foregroundColor(.white)
                        .frame(width: 44, height: 44)
                }
                Spacer()
            }
            .padding(.horizontal)
            
            Spacer()
            
            // Title
            Text("🔍 Odd One Out")
                .font(.system(size: 32, weight: .bold))
                .foregroundColor(.white)
            
            Spacer().frame(height: 16)
            
            // Instructions Card
            VStack(alignment: .leading, spacing: 16) {
                HStack(spacing: 12) {
                    Image(systemName: "info.circle.fill")
                        .font(.title)
                        .foregroundColor(Color(red: 0.42, green: 0.36, blue: 0.90))
                    
                    Text("How It Works")
                        .font(.title3)
                        .fontWeight(.bold)
                        .foregroundColor(.white)
                }
                
                Text("Look at the images and find the one that doesn't belong with the others. Train your pattern recognition and critical thinking skills!")
                    .font(.subheadline)
                    .foregroundColor(.white.opacity(0.9))
                    .fixedSize(horizontal: false, vertical: true)
                
                Divider()
                    .background(Color.white.opacity(0.2))
                
                Text("🎯 Your Challenge:")
                    .font(.headline)
                    .foregroundColor(Color(red: 0.42, green: 0.36, blue: 0.90))
                
                VStack(alignment: .leading, spacing: 8) {
                    BulletPoint(text: "Examine all the images carefully")
                    BulletPoint(text: "Find what 3 items have in common")
                    BulletPoint(text: "Select the one that breaks the pattern")
                    BulletPoint(text: "Answer quickly for bonus points!")
                }
            }
            .padding(20)
            .background(
                RoundedRectangle(cornerRadius: 16)
                    .fill(Color(red: 0.09, green: 0.13, blue: 0.24))
            )
            .padding(.horizontal)
            
            Spacer()
            
            // Start Button
            Button(action: onStart) {
                Text("Start Challenge")
                    .font(.headline)
                    .foregroundColor(.white)
                    .frame(maxWidth: .infinity)
                    .padding()
                    .background(Color(red: 0.42, green: 0.36, blue: 0.90))
                    .cornerRadius(12)
            }
            .padding(.horizontal)
            .padding(.bottom)
        }
    }
}

struct BulletPoint: View {
    let text: String
    
    var body: some View {
        HStack(alignment: .top, spacing: 8) {
            Text("•")
                .foregroundColor(.white)
            Text(text)
                .font(.subheadline)
                .foregroundColor(.white.opacity(0.9))
        }
    }
}


struct OddOneOutPuzzle: Codable {
    let puzzleId: String
    let type: String
    let theme: String
    let subTheme: String?
    let title: String
    let description: String
    let scenario: OddScenario
    let images: [OddOneOutImageItem]
    let questions: [OddQuestion]
    let timeLimit: Int
    let instructions: OddInstructions
    let difficulty: String
    let correctAnswer: String
    
    // Custom decoding to extract correctAnswer from questions
    enum CodingKeys: String, CodingKey {
        case puzzleId, type, theme, subTheme, title, description
        case scenario, images, questions, timeLimit, instructions, difficulty
        case correctAnswer
    }
    
    init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        
        puzzleId = try container.decode(String.self, forKey: .puzzleId)
        type = try container.decode(String.self, forKey: .type)
        theme = try container.decode(String.self, forKey: .theme)
        subTheme = try container.decodeIfPresent(String.self, forKey: .subTheme)
        title = try container.decode(String.self, forKey: .title)
        description = try container.decode(String.self, forKey: .description)
        scenario = try container.decode(OddScenario.self, forKey: .scenario)
        images = try container.decode([OddOneOutImageItem].self, forKey: .images)
        questions = try container.decode([OddQuestion].self, forKey: .questions)
        timeLimit = try container.decode(Int.self, forKey: .timeLimit)
        instructions = try container.decode(OddInstructions.self, forKey: .instructions)
        difficulty = try container.decode(String.self, forKey: .difficulty)
        
        // Try to get correctAnswer from root level first, then from first question
        if let rootCorrectAnswer = try? container.decode(String.self, forKey: .correctAnswer) {
            correctAnswer = rootCorrectAnswer
        } else if let firstQuestion = questions.first {
            correctAnswer = firstQuestion.correctAnswer
            print("ℹ️ ODD_ONE_OUT: Extracted correctAnswer from first question: \(correctAnswer)")
        } else {
            throw DecodingError.keyNotFound(
                CodingKeys.correctAnswer,
                DecodingError.Context(
                    codingPath: container.codingPath,
                    debugDescription: "correctAnswer not found at root level or in questions array"
                )
            )
        }
    }
    
    // Add custom encoding if needed
    func encode(to encoder: Encoder) throws {
        var container = encoder.container(keyedBy: CodingKeys.self)
        
        try container.encode(puzzleId, forKey: .puzzleId)
        try container.encode(type, forKey: .type)
        try container.encode(theme, forKey: .theme)
        try container.encodeIfPresent(subTheme, forKey: .subTheme)
        try container.encode(title, forKey: .title)
        try container.encode(description, forKey: .description)
        try container.encode(scenario, forKey: .scenario)
        try container.encode(images, forKey: .images)
        try container.encode(questions, forKey: .questions)
        try container.encode(timeLimit, forKey: .timeLimit)
        try container.encode(instructions, forKey: .instructions)
        try container.encode(difficulty, forKey: .difficulty)
        try container.encode(correctAnswer, forKey: .correctAnswer)
    }
}

struct OddScenario: Codable {
    let name: String
    let theme: String
    let explanation: String
    let difficulty: String
    let patternType: String
}

struct OddOneOutImageItem: Codable, Identifiable {
    let id: String
    let url: String
    let fileName: String?
    let source: String?
    let item: OddOneOutItem
    let isOddOneOut: Bool
    let photographer: String?
    let uploadedAt: String?
    let originalIndex: Int?
    
    // Custom decoding to handle missing fields
    enum CodingKeys: String, CodingKey {
        case id, url, fileName, source, item, isOddOneOut, photographer, uploadedAt, originalIndex
    }
    
    init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        
        id = try container.decode(String.self, forKey: .id)
        url = try container.decode(String.self, forKey: .url)
        fileName = try container.decodeIfPresent(String.self, forKey: .fileName)
        source = try container.decodeIfPresent(String.self, forKey: .source)
        item = try container.decode(OddOneOutItem.self, forKey: .item)
        isOddOneOut = try container.decode(Bool.self, forKey: .isOddOneOut)
        photographer = try container.decodeIfPresent(String.self, forKey: .photographer)
        uploadedAt = try container.decodeIfPresent(String.self, forKey: .uploadedAt)
        originalIndex = try container.decodeIfPresent(Int.self, forKey: .originalIndex)
    }
}

struct OddOneOutItem: Codable {
    let name: String
    let belongsToGroup: Bool
    let reason: String
}

struct OddQuestion: Codable {
    let question: String
    let type: String
    let options: [String]?
    let correctAnswer: String
    let explanation: String
    let points: Int
    let hint: String
}

struct OddInstructions: Codable {
    let task: String
    let method: String
    let scoring: String
}

// MARK: - Helper Extensions

extension OddOneOutPuzzle {
    static func parse(from jsonString: String) -> OddOneOutPuzzle? {
        guard let data = jsonString.data(using: .utf8) else {
            print("❌ ODD_ONE_OUT: Failed to convert JSON string to data")
            return nil
        }
        
        // Debug: Print the JSON string (first 500 chars)
        print("🔍 ODD_ONE_OUT: Attempting to parse JSON (first 500 chars):")
        print(String(jsonString.prefix(500)))
        
        do {
            let decoder = JSONDecoder()
            decoder.keyDecodingStrategy = .useDefaultKeys
            let puzzle = try decoder.decode(OddOneOutPuzzle.self, from: data)
            
            print("✅ ODD_ONE_OUT: Successfully parsed puzzle")
            print("   - Puzzle ID: \(puzzle.puzzleId)")
            print("   - Images count: \(puzzle.images.count)")
            print("   - Correct answer: \(puzzle.correctAnswer)")
            print("   - Time limit: \(puzzle.timeLimit)ms")
            print("   - Difficulty: \(puzzle.difficulty)")
            
            // Verify images
            for (index, image) in puzzle.images.enumerated() {
                print("   - Image \(index): \(image.item.name) (isOdd: \(image.isOddOneOut))")
            }
            
            return puzzle
        } catch let DecodingError.keyNotFound(key, context) {
            print("❌ ODD_ONE_OUT: Key '\(key.stringValue)' not found")
            print("   - Coding path: \(context.codingPath.map { $0.stringValue }.joined(separator: " -> "))")
            print("   - Debug description: \(context.debugDescription)")
            return nil
        } catch let DecodingError.typeMismatch(type, context) {
            print("❌ ODD_ONE_OUT: Type mismatch for type \(type)")
            print("   - Coding path: \(context.codingPath.map { $0.stringValue }.joined(separator: " -> "))")
            print("   - Debug description: \(context.debugDescription)")
            return nil
        } catch let DecodingError.valueNotFound(type, context) {
            print("❌ ODD_ONE_OUT: Value not found for type \(type)")
            print("   - Coding path: \(context.codingPath.map { $0.stringValue }.joined(separator: " -> "))")
            print("   - Debug description: \(context.debugDescription)")
            return nil
        } catch let DecodingError.dataCorrupted(context) {
            print("❌ ODD_ONE_OUT: Data corrupted")
            print("   - Coding path: \(context.codingPath.map { $0.stringValue }.joined(separator: " -> "))")
            print("   - Debug description: \(context.debugDescription)")
            return nil
        } catch {
            print("❌ ODD_ONE_OUT: Failed to parse puzzle: \(error)")
            return nil
        }
    }
}

// MARK: - Helper Extensions

struct OddOneOutView: View {
    let puzzle: OddOneOutPuzzle
    let onComplete: (Bool, Int) -> Void
    let onBack: () -> Void
    
    @State private var showWelcome = true
    @State private var gameStarted = false
    @State private var selectedImage: String?
    @State private var isAnswerRevealed = false
    @State private var userWasCorrect = false
    @State private var totalScore = 0
    @State private var showHint = false
    
    // Timer state
    @State private var timeRemaining: Int
    @State private var displayTimer: String
    @State private var gameStartTime: Date?
    @State private var timerTask: Task<Void, Never>?
    
    init(puzzle: OddOneOutPuzzle, onComplete: @escaping (Bool, Int) -> Void, onBack: @escaping () -> Void) {
        self.puzzle = puzzle
        self.onComplete = onComplete
        self.onBack = onBack
        
        let totalSeconds = puzzle.timeLimit / 1000
        _timeRemaining = State(initialValue: totalSeconds)
        _displayTimer = State(initialValue: OddOneOutView.formatTime(totalSeconds))
    }
    
    private func formatTime(_ seconds: Int) -> String {
        let minutes = seconds / 60
        let secs = seconds % 60
        return String(format: "%d:%02d", minutes, secs)
    }
    
    var body: some View {
        ZStack {
            Color(red: 0.1, green: 0.1, blue: 0.18)
                .ignoresSafeArea()
            
            if showWelcome {
                OddOneOutWelcomeView(
                    onStart: {
                        showWelcome = false
                        gameStarted = true
                        gameStartTime = Date()
                        startTimer()
                    },
                    onBack: onBack
                )
            } else {
                VStack(spacing: 0) {
                    // Top Bar
                    OddOneOutTopBar(
                        displayTimer: displayTimer,
                        timeRemaining: timeRemaining,
                        totalScore: totalScore,
                        onBack: {
                            timerTask?.cancel()
                            onBack()
                        },
                        onShowHint: { showHint.toggle() }
                    )
                    
                    // Main Content
                    ScrollView {
                        VStack(spacing: 16) {
                            // Challenge Card
                            OddOneOutChallengeCard(
                                puzzle: puzzle,
                                showHint: showHint
                            )
                            .padding(.horizontal)
                            
                            // Images Grid
                            OddOneOutImagesGrid(
                                puzzle: puzzle,
                                selectedImage: selectedImage,
                                isAnswerRevealed: isAnswerRevealed,
                                onImageSelected: { itemName in
                                    if !isAnswerRevealed {
                                        submitAnswer(itemName)
                                    }
                                }
                            )
                            .padding(.horizontal)
                            
                            // Result Section
                            if isAnswerRevealed {
                                OddOneOutResultSection(
                                    userWasCorrect: userWasCorrect,
                                    puzzle: puzzle,
                                    totalScore: totalScore
                                )
                                .padding(.horizontal)
                                
                                Button(action: {
                                    timerTask?.cancel()
                                    onComplete(userWasCorrect, totalScore)
                                }) {
                                    Text("Continue to Next Puzzle")
                                        .font(.headline)
                                        .foregroundColor(.white)
                                        .frame(maxWidth: .infinity)
                                        .padding()
                                        .background(Color(red: 0.42, green: 0.36, blue: 0.90))
                                        .cornerRadius(12)
                                }
                                .padding(.horizontal)
                                .padding(.bottom)
                            }
                        }
                        .padding(.vertical)
                    }
                }
            }
        }
        .navigationBarHidden(true)
        .onDisappear {
            timerTask?.cancel()
        }
    }
    
    // MARK: - Timer Functions
    
    private func startTimer() {
        timerTask = Task {
            while timeRemaining > 0 && !isAnswerRevealed {
                try? await Task.sleep(nanoseconds: 1_000_000_000)
                
                await MainActor.run {
                    if !isAnswerRevealed {
                        timeRemaining -= 1
                        displayTimer = formatTime(timeRemaining)
                        
                        if timeRemaining == 0 {
                            // Time's up
                            isAnswerRevealed = true
                            userWasCorrect = false
                            totalScore = 0
                        }
                    }
                }
            }
        }
    }
    
    private static func formatTime(_ seconds: Int) -> String {
        let minutes = seconds / 60
        let secs = seconds % 60
        return String(format: "%d:%02d", minutes, secs)
    }
    
    // MARK: - Answer Submission
    
    private func submitAnswer(_ selectedItemName: String) {
        let timeSpent = gameStartTime?.timeIntervalSinceNow ?? 0
        let correct = selectedItemName == puzzle.correctAnswer
        
        selectedImage = selectedItemName
        isAnswerRevealed = true
        userWasCorrect = correct
        
        // Haptic feedback
        let impact = UIImpactFeedbackGenerator(style: correct ? .medium : .light)
        impact.impactOccurred()
        
        totalScore = calculateScore(correct: correct, timeSpent: abs(timeSpent))
        
        timerTask?.cancel()
        
        print("🎯 Answer submitted: \(selectedItemName), Correct: \(correct), Score: \(totalScore)")
    }
    
    private func calculateScore(correct: Bool, timeSpent: TimeInterval) -> Int {
        guard correct else { return 0 }
        
        let baseScore: Int
        switch puzzle.difficulty.lowercased() {
        case "easy": baseScore = 100
        case "medium": baseScore = 150
        case "hard": baseScore = 200
        default: baseScore = 150
        }
        
        // Speed bonus
        let timeBonus: Int
        if timeSpent <= 10 {
            timeBonus = Int(Double(baseScore) * 0.3)
        } else if timeSpent <= 20 {
            timeBonus = Int(Double(baseScore) * 0.15)
        } else {
            timeBonus = 0
        }
        
        return baseScore + timeBonus
    }
}
