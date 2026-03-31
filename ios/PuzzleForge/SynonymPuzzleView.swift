//
//  SynonymSet.swift
//  PuzzleForge
//
//  Created by Sushanth Tiruvaipati on 7/12/25.
//


//
//  SynonymPuzzleView.swift
//  PuzzleForge
//
//  Created by Assistant on 7/12/25.
//

import SwiftUI

// MARK: - Data Models
struct SynonymSet {
    let id: Int
    let words: [String]
    let displayWord: String // The word shown at the bottom
    let category: String
    let color: Color
}

struct SynonymWord {
    let word: String
    let setId: Int
    let isDisplayed: Bool
}

// MARK: - Synonym Puzzle Data Extension
extension Puzzle {
    var synonymPuzzleData: SynonymPuzzleData? {
        guard let questionData = question.data(using: .utf8) else {
            print("❌ SYNONYM: Failed to convert question to data")
            return nil
        }
        
        do {
            // Try to parse the JSON structure
            if let json = try JSONSerialization.jsonObject(with: questionData) as? [String: Any] {
                print("📊 SYNONYM: Parsing JSON object format")
                
                // Check if there's a nested "question" field (double-nested case)
                let actualJson: [String: Any]
                if let nestedQuestion = json["question"] as? String,
                   let nestedData = nestedQuestion.data(using: .utf8),
                   let nestedJson = try JSONSerialization.jsonObject(with: nestedData) as? [String: Any] {
                    print("📊 SYNONYM: Found double-nested JSON format")
                    actualJson = nestedJson
                } else {
                    print("📊 SYNONYM: Using direct JSON format")
                    actualJson = json
                }
                
                // Parse wordSets array
                guard let wordSetsArray = actualJson["wordSets"] as? [[String]] else {
                    print("❌ SYNONYM: No wordSets array found")
                    return nil
                }
                
                let synonymSets = wordSetsArray
                let difficulty = actualJson["difficulty"] as? String ?? self.difficulty ?? "Medium"
                let hint = actualJson["hint"] as? String ?? self.hint ?? "Group words with similar meanings"
                
                print("✅ SYNONYM: Successfully parsed \(synonymSets.count) sets")
                synonymSets.enumerated().forEach { index, set in
                    print("   Set \(index): \(set)")
                }
                
                return SynonymPuzzleData(
                    synonymSets: synonymSets,
                    difficulty: difficulty,
                    hint: hint
                )
                
            } else if let jsonArray = try JSONSerialization.jsonObject(with: questionData) as? [[String]] {
                // Direct array format: [["word1","word2","word3"],["word4","word5","word6"]]
                print("📊 SYNONYM: Parsing direct array format")
                
                let synonymSets = jsonArray
                print("✅ SYNONYM: Successfully parsed \(synonymSets.count) sets from array")
                synonymSets.enumerated().forEach { index, set in
                    print("   Set \(index): \(set)")
                }
                
                return SynonymPuzzleData(
                    synonymSets: synonymSets,
                    difficulty: self.difficulty ?? "Medium",
                    hint: self.hint ?? "Group words with similar meanings"
                )
            } else {
                print("❌ SYNONYM: Unknown JSON format")
                return nil
            }
        } catch {
            print("❌ SYNONYM: JSON parsing error: \(error)")
            return nil
        }
    }
}

struct SynonymPuzzleData {
    let synonymSets: [[String]]
    let difficulty: String
    let hint: String
}

// MARK: - Synonym Puzzle View
struct SynonymPuzzleView: View {
    let puzzle: Puzzle
    let questionIndex: Int
    let totalQuestions: Int
    let onAnswerSubmitted: (String, Bool) -> Void
    let onNextPuzzle: () -> Void
    let onExit: () -> Void
    
    @State private var synonymSetData: [SynonymSet] = []
    @State private var wordQueue: [SynonymWord] = []
    @State private var currentWordIndex = 0
    @State private var selectedSetId = -1
    @State private var score = 0
    @State private var lives = 3
    @State private var gameComplete = false
    @State private var showCurrentWord = true
    @State private var lastAnswerCorrect = false
    @State private var timeRemaining = 150 // 2:30 default
    @State private var gameStartTime = Date()
    @State private var showFeedback = false
    @State private var feedbackMessage = ""
    @State private var feedbackIsCorrect = false
    
    private let colors: [Color] = [
        Color(red: 0.91, green: 0.12, blue: 0.39), // Pink
        Color(red: 0.13, green: 0.59, blue: 0.95), // Blue
        Color(red: 0.30, green: 0.69, blue: 0.31)  // Green
    ]
    
    var body: some View {
        ZStack {
            // Background
            Color(red: 0.10, green: 0.10, blue: 0.18)
                .ignoresSafeArea()
            
            VStack(spacing: 0) {
                // Header
                headerView
                
                Spacer(minLength: 24)
                
                // Instructions
                instructionsCard
                
                Spacer(minLength: 64)
                
                // Main Game Area
                VStack(spacing: 80) {
                    // Current word display
                    currentWordCard
                    
                    // Synonym set buttons
                    synonymSetButtons
                }
                .frame(maxWidth: .infinity, maxHeight: .infinity)
                
                Spacer()
                
                // Score
                Text("Score: \(score)")
                    .font(.title2)
                    .fontWeight(.bold)
                    .foregroundColor(.white)
                    .padding(.bottom, 16)
            }
            .padding(.horizontal, 48)
            .padding(.vertical, 20)
            
            // Feedback overlay
            if showFeedback {
                feedbackOverlay
            }
        }
        .onAppear {
            setupPuzzle()
            startTimer()
        }
        .navigationBarHidden(true)
    }
    
    // MARK: - Header View
    private var headerView: some View {
        HStack {
            Button(action: onExit) {
                Image(systemName: "arrow.left")
                    .font(.title2)
                    .foregroundColor(.white)
            }
            
            Spacer()
            
            Text(puzzle.difficulty.uppercased())
                .font(.system(size: 16, weight: .bold))
                .foregroundColor(.white)
            
            Spacer()
            
            HStack(spacing: 4) {
                Image(systemName: "timer")
                    .font(.system(size: 20))
                    .foregroundColor(.white)
                
                Text(timeString)
                    .font(.system(size: 16, weight: .bold))
                    .foregroundColor(.white)
            }
        }
    }
    
    // MARK: - Instructions Card
    private var instructionsCard: some View {
        VStack {
            Text("Group the synonyms! Tap the set that matches the word above.")
                .font(.system(size: 14))
                .foregroundColor(.white)
                .multilineTextAlignment(.center)
                .padding(16)
        }
        .background(Color(red: 0.16, green: 0.16, blue: 0.24))
        .cornerRadius(12)
    }
    
    // MARK: - Current Word Card
    private var currentWordCard: some View {
        Group {
            if currentWordIndex < wordQueue.count && !gameComplete {
                Text(wordQueue[currentWordIndex].word)
                    .font(.system(size: 28, weight: .bold))
                    .foregroundColor(.black)
                    .padding(32)
                    .background(Color.white)
                    .cornerRadius(20)
                    .shadow(color: .black.opacity(0.2), radius: 8, x: 0, y: 4)
                    .opacity(showCurrentWord ? 1.0 : 0.0)
                    .scaleEffect(showCurrentWord ? 1.0 : 0.8)
                    .animation(.easeInOut(duration: 0.3), value: showCurrentWord)
            } else {
                Text("🎉")
                    .font(.system(size: 28, weight: .bold))
                    .foregroundColor(Color(red: 0.30, green: 0.69, blue: 0.31))
                    .padding(32)
                    .background(Color.white.opacity(0.8))
                    .cornerRadius(20)
                    .shadow(color: .black.opacity(0.2), radius: 8, x: 0, y: 4)
            }
        }
    }
    
    // MARK: - Synonym Set Buttons
    private var synonymSetButtons: some View {
        HStack(spacing: 30) {
            ForEach(synonymSetData, id: \.id) { set in
                synonymSetButton(for: set)
            }
        }
    }
    
    private func synonymSetButton(for set: SynonymSet) -> some View {
        Button(action: { handleSetSelection(setId: set.id) }) {
            Text(set.displayWord)
                .font(.system(size: 14, weight: .bold))
                .foregroundColor(.white)
                .multilineTextAlignment(.center)
                .lineLimit(2)
                .frame(width: 100, height: 100)
                .background(set.color.opacity(0.2))
                .overlay(
                    Circle()
                        .stroke(set.color, lineWidth: 2)
                )
                .clipShape(Circle())
        }
        .scaleEffect(selectedSetId == set.id ? 1.1 : 1.0)
        .animation(.spring(response: 0.3, dampingFraction: 0.6), value: selectedSetId)
    }
    
    // MARK: - Feedback Overlay
    private var feedbackOverlay: some View {
        ZStack {
            Color.black.opacity(0.4)
                .ignoresSafeArea()
            
            VStack(spacing: 20) {
                Image(systemName: feedbackIsCorrect ? "checkmark.circle.fill" : "xmark.circle.fill")
                    .font(.system(size: 60))
                    .foregroundColor(feedbackIsCorrect ? .green : .red)
                
                Text(feedbackMessage)
                    .font(.title2)
                    .fontWeight(.semibold)
                    .foregroundColor(.white)
                    .multilineTextAlignment(.center)
                
                Button("Continue") {
                    hideFeedback()
                }
                .font(.headline)
                .foregroundColor(.white)
                .padding(.horizontal, 30)
                .padding(.vertical, 12)
                .background(Color.blue)
                .cornerRadius(25)
            }
            .padding(40)
            .background(Color(red: 0.16, green: 0.16, blue: 0.24))
            .cornerRadius(20)
            .padding(.horizontal, 40)
        }
    }
    
    // MARK: - Helper Properties
    private var timeString: String {
        let minutes = timeRemaining / 60
        let seconds = timeRemaining % 60
        return String(format: "%d:%02d", minutes, seconds)
    }
    
    // MARK: - Setup and Game Logic
    private func setupPuzzle() {
        guard let synonymData = puzzle.synonymPuzzleData else {
            print("❌ SYNONYM: Failed to parse synonym puzzle data")
            return
        }
        
        print("🎮 SYNONYM: Setting up puzzle with \(synonymData.synonymSets.count) sets")
        
        // Create synonym set data with colors
        synonymSetData = synonymData.synonymSets.enumerated().map { index, words in
            SynonymSet(
                id: index,
                words: words,
                displayWord: words.first ?? "",
                category: "",
                color: colors[index % colors.count]
            )
        }
        
        // Create word queue (all words except display words)
        wordQueue = synonymSetData.flatMap { set in
            set.words.dropFirst().map { word in
                SynonymWord(word: word, setId: set.id, isDisplayed: false)
            }
        }.shuffled()
        
        // Set timer based on difficulty
        timeRemaining = getTimerForDifficulty(synonymData.difficulty)
        
        print("🎮 SYNONYM: Created \(wordQueue.count) words to match")
        print("🎮 SYNONYM: Timer set to \(timeRemaining) seconds")
    }
    
    private func getTimerForDifficulty(_ difficulty: String) -> Int {
        switch difficulty.lowercased() {
        case "easy": return 180    // 3:00
        case "medium": return 150  // 2:30
        case "hard": return 120    // 2:00
        case "expert": return 90   // 1:30
        default: return 150        // 2:30
        }
    }
    
    private func startTimer() {
        Timer.scheduledTimer(withTimeInterval: 1.0, repeats: true) { timer in
            if timeRemaining > 0 && !gameComplete {
                timeRemaining -= 1
            } else {
                timer.invalidate()
                if timeRemaining == 0 && !gameComplete {
                    handleTimeUp()
                }
            }
        }
    }
    
    private func handleSetSelection(setId: Int) {
        guard !gameComplete && currentWordIndex < wordQueue.count else { return }
        
        selectedSetId = setId
        let currentWord = wordQueue[currentWordIndex]
        let isCorrect = currentWord.setId == setId
        lastAnswerCorrect = isCorrect
        
        if isCorrect {
            score += 10
        } else {
            lives -= 1
        }
        
        // Get feedback info
        let correctSet = synonymSetData.first { $0.id == currentWord.setId }
        let selectedSet = synonymSetData.first { $0.id == setId }
        
        let feedbackMsg: String
        if isCorrect {
            feedbackMsg = "Correct! '\(currentWord.word)' belongs to: \(correctSet?.displayWord ?? "Unknown")"
        } else {
            feedbackMsg = "Wrong! '\(currentWord.word)' belongs to: \(correctSet?.displayWord ?? "Unknown")\nYou selected: \(selectedSet?.displayWord ?? "Unknown")"
        }
        
        showFeedbackMessage(feedbackMsg, isCorrect: isCorrect)
        
        // Track answer
        onAnswerSubmitted(selectedSet?.displayWord ?? "", isCorrect)
    }
    
    private func showFeedbackMessage(_ message: String, isCorrect: Bool) {
        feedbackMessage = message
        feedbackIsCorrect = isCorrect
        showFeedback = true
    }
    
    private func hideFeedback() {
        showFeedback = false
        
        // Check if game should end
        if lives <= 0 {
            completeGame(success: false)
            return
        }
        
        // Move to next word or complete
        currentWordIndex += 1
        selectedSetId = -1
        
        if currentWordIndex >= wordQueue.count {
            completeGame(success: true)
        } else {
            showCurrentWord = true
        }
    }
    
    private func handleTimeUp() {
        gameComplete = true
        showFeedbackMessage("Time's up! Try to be faster next time.", isCorrect: false)
        
        // Auto-advance after showing feedback
        DispatchQueue.main.asyncAfter(deadline: .now() + 2.0) {
            completeGame(success: false)
        }
    }
    
    private func completeGame(success: Bool) {
        gameComplete = true
        
        if success {
            print("🎉 SYNONYM: All words completed successfully!")
        } else {
            print("💔 SYNONYM: Game ended - lives: \(lives), time: \(timeRemaining)")
        }
        
        // Small delay before transitioning
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.5) {
            onNextPuzzle()
        }
    }
}

// MARK: - Preview
#if DEBUG
struct SynonymPuzzleView_Previews: PreviewProvider {
    static var previews: some View {
        let samplePuzzle = Puzzle(
            question: """
            {
                "wordSets": [
                    ["happy", "joyful", "cheerful", "glad"],
                    ["big", "large", "huge", "enormous"],
                    ["fast", "quick", "rapid", "swift"]
                ],
                "difficulty": "Medium",
                "hint": "Group words with similar meanings"
            }
            """,
            answer: "completed",
            hint: "Group words with similar meanings",
            options: [],
            format: "synonym_grouping",
            puzzleType: "synonyms",
            puzzleId: "sample",
            id: "sample",
            name: "Sample Synonym Puzzle",
            createdAt: Date().timeIntervalSince1970,
            status: "ready",
            difficulty: "Medium"
        )
        
        SynonymPuzzleView(
            puzzle: samplePuzzle,
            questionIndex: 0,
            totalQuestions: 1,
            onAnswerSubmitted: { answer, isCorrect in
                print("Preview: Answer \(answer), Correct: \(isCorrect)")
            },
            onNextPuzzle: {
                print("Preview: Next puzzle")
            },
            onExit: {
                print("Preview: Exit")
            }
        )
    }
}
#endif
