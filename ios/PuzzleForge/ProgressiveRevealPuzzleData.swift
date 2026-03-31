//
//  ProgressiveRevealModels.swift
//  PuzzleForge
//
//  Progressive Revelation Puzzle Data Models
//

import Foundation
import SwiftUI

struct ProgressiveRevealPuzzleView: View {
    let puzzleData: ProgressiveRevealPuzzleData
    let round: String
    let onPuzzleComplete: (Int, Bool) -> Void
    let onBack: () -> Void
    
    // Game state
    @State private var currentStep = 1
    @State private var userAnswer = ""
    @State private var gameCompleted = false
    @State private var score = 0
    @State private var timeRemaining: Int
    @State private var showHint = false
    @State private var feedback: String?
    @State private var feedbackColor: Color = .green
    @State private var isCorrect = false
    @State private var isSubmitting = false
    
    @FocusState private var isInputFocused: Bool
    
    init(puzzleData: ProgressiveRevealPuzzleData, round: String,
         onPuzzleComplete: @escaping (Int, Bool) -> Void, onBack: @escaping () -> Void) {
        self.puzzleData = puzzleData
        self.round = round
        self.onPuzzleComplete = onPuzzleComplete
        self.onBack = onBack
        _timeRemaining = State(initialValue: puzzleData.timeLimitSeconds)
    }
    
    // Computed properties
    private var currentClue: ProgressiveClue? {
        guard currentStep <= puzzleData.clues.count else { return nil }
        return puzzleData.clues[currentStep - 1]
    }
    
    private var currentBlurLevel: BlurLevel? {
        puzzleData.image.blurLevels.first { $0.step == currentStep }
    }
    
    private var currentBlurRadius: CGFloat {
        CGFloat(currentBlurLevel?.blur ?? 0)
    }
    
    var body: some View {
        GeometryReader { geometry in
            ZStack {
                Color.black.ignoresSafeArea()
                
                VStack(spacing: 0) {
                    // Header
                    headerSection
                    
                    // Main content
                    ScrollView {
                        VStack(spacing: 16) {
                            // Image section
                            imageSection
                            
                            // Clue section
                            if !gameCompleted, let clue = currentClue {
                                clueSection(clue: clue)
                            }
                            
                            // Answer input section
                            if !gameCompleted, currentClue != nil {
                                answerInputSection
                            }
                            
                            // Game completed section
                            if gameCompleted {
                                completionSection
                            }
                        }
                        .padding(16)
                    }
                    
                    // Feedback overlay
                    if let feedback = feedback {
                        feedbackOverlay(message: feedback)
                    }
                    
                    // Progress indicator
                    progressIndicator
                }
                .frame(width: geometry.size.width)
            }
        }
        .navigationBarHidden(true)
        .onAppear {
            startTimer()
        }
    }
    
    // MARK: - Header Section
    private var headerSection: some View {
        VStack(spacing: 8) {
            // Top row
            HStack {
                Button(action: onBack) {
                    Image(systemName: "arrow.left")
                        .foregroundColor(.white)
                        .font(.system(size: 20))
                }
                
                Spacer()
                
                VStack(spacing: 2) {
                    Text(puzzleData.difficulty.uppercased())
                        .font(.system(size: 12, weight: .bold))
                        .foregroundColor(.white)
                    
                    Text(round)
                        .font(.system(size: 10))
                        .foregroundColor(.white)
                }
                
                Spacer()
                
                Text(formatTime(timeRemaining))
                    .font(.system(size: 14, weight: .bold))
                    .foregroundColor(timeRemaining < 60 ? .red : .white)
            }
            
            // Title and score
            HStack {
                VStack(alignment: .leading, spacing: 2) {
                    Text("Progressive Reveal")
                        .font(.system(size: 18, weight: .bold))
                        .foregroundColor(.white)
                    
                    Text(puzzleData.category.uppercased())
                        .font(.system(size: 12))
                        .foregroundColor(.gray)
                }
                
                Spacer()
                
                VStack(alignment: .trailing, spacing: 2) {
                    Text("Score: \(score)")
                        .font(.system(size: 16, weight: .bold))
                        .foregroundColor(.white)
                    
                    Text("Step \(currentStep)/\(puzzleData.gameFlow.totalSteps)")
                        .font(.system(size: 12))
                        .foregroundColor(.gray)
                }
            }
        }
        .padding(16)
    }
    
    // MARK: - Image Section
    private var imageSection: some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack {
                Text("Mystery Image")
                    .font(.system(size: 16, weight: .bold))
                    .foregroundColor(.white)
                
                Spacer()
                
                Text(currentBlurLevel?.description ?? "Clear")
                    .font(.system(size: 12))
                    .foregroundColor(.gray)
            }
            
            ZStack(alignment: .topTrailing) {
                AsyncImage(url: URL(string: puzzleData.image.url)) { phase in
                    switch phase {
                    case .empty:
                        ProgressView()
                            .frame(height: 300)
                    case .success(let image):
                        image
                            .resizable()
                            .aspectRatio(contentMode: .fit)
                            .frame(maxWidth: .infinity)
                            .frame(height: 300)
                            .blur(radius: currentBlurRadius)
                    case .failure:
                        Image(systemName: "photo")
                            .font(.system(size: 60))
                            .foregroundColor(.gray)
                            .frame(height: 300)
                    @unknown default:
                        EmptyView()
                    }
                }
                .frame(maxWidth: .infinity)
                .frame(height: 300)
                .background(Color.gray.opacity(0.2))
                .cornerRadius(12)
                .clipped()
                
                // Step indicator overlay
                Text("Step \(currentStep)/\(puzzleData.gameFlow.totalSteps)")
                    .font(.system(size: 12))
                    .foregroundColor(.white)
                    .padding(.horizontal, 8)
                    .padding(.vertical, 4)
                    .background(Color.black.opacity(0.7))
                    .cornerRadius(12)
                    .padding(8)
            }
        }
        .padding(16)
        .background(Color.gray.opacity(0.2))
        .cornerRadius(12)
    }
    
    // MARK: - Clue Section
    private func clueSection(clue: ProgressiveClue) -> some View {
        VStack(alignment: .leading, spacing: 12) {
            // Clue header
            HStack {
                Text("Clue \(clue.level)")
                    .font(.system(size: 16, weight: .bold))
                    .foregroundColor(.white)
                
                Spacer()
                
                Text(clue.difficulty.uppercased())
                    .font(.system(size: 12, weight: .bold))
                    .foregroundColor(difficultyColor(for: clue.difficulty))
            }
            
            // Clue text
            Text(clue.text)
                .font(.system(size: 16))
                .foregroundColor(.white)
                .lineSpacing(8)
            
            // Hint section
            if let hint = clue.hint, !hint.isEmpty {
                Button(action: { showHint.toggle() }) {
                    HStack {
                        Image(systemName: "lightbulb")
                            .font(.system(size: 16))
                        Text(showHint ? "Hide Hint" : "Show Hint")
                            .font(.system(size: 14))
                    }
                    .foregroundColor(.white)
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 12)
                    .background(Color.blue.opacity(0.3))
                    .cornerRadius(8)
                }
                
                if showHint {
                    Text(hint)
                        .font(.system(size: 14))
                        .foregroundColor(.white)
                        .padding(12)
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .background(Color.blue.opacity(0.2))
                        .cornerRadius(8)
                        .overlay(
                            RoundedRectangle(cornerRadius: 8)
                                .stroke(Color.blue.opacity(0.5), lineWidth: 1)
                        )
                }
            }
        }
        .padding(16)
        .background(Color.gray.opacity(0.2))
        .cornerRadius(12)
    }
    
    // MARK: - Answer Input Section
    private var answerInputSection: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text("Your Answer:")
                .font(.system(size: 14, weight: .bold))
                .foregroundColor(.white)
            
            TextField("What do you think it is?", text: $userAnswer)
                .focused($isInputFocused)
                .textFieldStyle(PlainTextFieldStyle())
                .font(.system(size: 16))
                .foregroundColor(.white)
                .padding(12)
                .background(Color.white.opacity(0.1))
                .cornerRadius(8)
                .overlay(
                    RoundedRectangle(cornerRadius: 8)
                        .stroke(borderColor, lineWidth: 2)
                )
                .autocapitalization(.none)
                .disableAutocorrection(true)
                .onSubmit {
                    if !userAnswer.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
                        submitAnswer()
                    }
                }
            
            // Supporting text with similarity hint
            if userAnswer.count > 2 {
                Text(similarityHintText)
                    .font(.system(size: 12))
                    .foregroundColor(similarityHintColor)
            }
            
            // Action buttons
            HStack(spacing: 12) {
                Button(action: submitAnswer) {
                    Text("Submit")
                        .font(.system(size: 16, weight: .bold))
                        .foregroundColor(.white)
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 12)
                        .background(userAnswer.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty ? Color.gray : Color.green)
                        .cornerRadius(8)
                }
                .disabled(userAnswer.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty || isSubmitting)
                
                if currentStep < puzzleData.clues.count {
                    Button(action: skipToNextClue) {
                        Text("Next Clue")
                            .font(.system(size: 16, weight: .bold))
                            .foregroundColor(.white)
                            .frame(maxWidth: .infinity)
                            .padding(.vertical, 12)
                            .background(Color.blue)
                            .cornerRadius(8)
                    }
                } else {
                    Button(action: revealAnswer) {
                        Text("Reveal")
                            .font(.system(size: 16, weight: .bold))
                            .foregroundColor(.white)
                            .frame(maxWidth: .infinity)
                            .padding(.vertical, 12)
                            .background(Color.purple)
                            .cornerRadius(8)
                    }
                }
            }
        }
        .padding(16)
        .background(Color.gray.opacity(0.2))
        .cornerRadius(12)
    }
    
    // MARK: - Completion Section
    private var completionSection: some View {
        VStack(spacing: 16) {
            Image(systemName: isCorrect ? "checkmark.circle.fill" : "eye.fill")
                .font(.system(size: 48))
                .foregroundColor(isCorrect ? .green : .purple)
            
            Text(puzzleData.correctAnswer)
                .font(.system(size: 24, weight: .bold))
                .foregroundColor(.white)
                .multilineTextAlignment(.center)
            
            if isCorrect {
                Text("Congratulations!")
                    .font(.system(size: 16, weight: .bold))
                    .foregroundColor(.green)
                
                Text("You earned \(score) points!")
                    .font(.system(size: 14))
                    .foregroundColor(.white)
            } else {
                Text(score > 0 ? "You earned \(score) points" : "Better luck next time!")
                    .font(.system(size: 14))
                    .foregroundColor(.white)
            }
        }
        .padding(16)
        .frame(maxWidth: .infinity)
        .background(isCorrect ? Color.green.opacity(0.2) : Color.purple.opacity(0.2))
        .cornerRadius(12)
        .overlay(
            RoundedRectangle(cornerRadius: 12)
                .stroke(isCorrect ? Color.green : Color.purple, lineWidth: 1)
        )
    }
    
    // MARK: - Feedback Overlay
    private func feedbackOverlay(message: String) -> some View {
        VStack {
            Spacer()
            
            Text(message)
                .font(.system(size: 16, weight: .bold))
                .foregroundColor(.white)
                .multilineTextAlignment(.center)
                .padding(16)
                .frame(maxWidth: .infinity)
                .background(feedbackColor.opacity(0.9))
            
            Spacer()
        }
        .transition(.move(edge: .bottom).combined(with: .opacity))
    }
    
    // MARK: - Progress Indicator
    private var progressIndicator: some View {
        ProgressView(value: Double(currentStep), total: Double(puzzleData.gameFlow.totalSteps))
            .progressViewStyle(LinearProgressViewStyle(tint: .blue))
            .padding(.horizontal, 16)
            .padding(.vertical, 8)
    }
    
    // MARK: - Helper Properties
    private var borderColor: Color {
        guard userAnswer.count > 2 else { return .gray }
        
        let quickCheck = ProgressiveRevealAnswerMatcher.checkAnswer(
            userAnswer: userAnswer,
            correctAnswer: puzzleData.correctAnswer,
            puzzleData: puzzleData,
            currentStep: currentStep
        )
        
        switch quickCheck.confidence {
        case 0.6...1.0: return .green
        case 0.4..<0.6: return .yellow
        case 0.2..<0.4: return .cyan
        default: return .blue
        }
    }
    
    private var similarityHintText: String {
        let quickCheck = ProgressiveRevealAnswerMatcher.checkAnswer(
            userAnswer: userAnswer,
            correctAnswer: puzzleData.correctAnswer,
            puzzleData: puzzleData,
            currentStep: currentStep
        )
        
        switch quickCheck.confidence {
        case 0.6...1.0: return "🔥 Very close!"
        case 0.4..<0.6: return "🌡️ Getting warmer..."
        case 0.2..<0.4: return "🤔 Keep trying..."
        default: return "💡 Think about the clues"
        }
    }
    
    private var similarityHintColor: Color {
        let quickCheck = ProgressiveRevealAnswerMatcher.checkAnswer(
            userAnswer: userAnswer,
            correctAnswer: puzzleData.correctAnswer,
            puzzleData: puzzleData,
            currentStep: currentStep
        )
        
        switch quickCheck.confidence {
        case 0.6...1.0: return .green
        case 0.4..<0.6: return .yellow
        case 0.2..<0.4: return .cyan
        default: return .gray
        }
    }
    
    // MARK: - Actions
    private func submitAnswer() {
        guard !userAnswer.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty else { return }
        guard !isSubmitting else { return }
        
        isSubmitting = true
        isInputFocused = false
        
        let matchResult = ProgressiveRevealAnswerMatcher.checkAnswer(
            userAnswer: userAnswer,
            correctAnswer: puzzleData.correctAnswer,
            puzzleData: puzzleData,
            currentStep: currentStep
        )
        
        print("🔍 Answer check result: \(matchResult.explanation) (confidence: \(matchResult.confidence))")
        
        if matchResult.isMatch {
            // Correct answer!
            let baseScore = puzzleData.gameFlow.scoringSystem["correctAtStep\(currentStep)"] ?? 20
            
            let scoreMultiplier: Double
            switch matchResult.matchType {
            case .exact: scoreMultiplier = 1.0
            case .synonym: scoreMultiplier = 0.95
            case .partial: scoreMultiplier = 0.90
            case .pluralVariation: scoreMultiplier = 0.98
            case .fuzzy: scoreMultiplier = 0.85
            case .noMatch: scoreMultiplier = 0.0
            }
            
            let finalScore = Int(Double(baseScore) * scoreMultiplier * matchResult.confidence)
            score = finalScore
            isCorrect = true
            gameCompleted = true
            
            feedback = getFeedbackForMatchType(matchResult.matchType, score: finalScore)
            feedbackColor = .green
            
            // Reveal clear image
            currentStep = puzzleData.gameFlow.totalSteps
            
            DispatchQueue.main.asyncAfter(deadline: .now() + 3) {
                onPuzzleComplete(score, true)
            }
            
        } else {
            // Wrong answer
            let similarity = matchResult.confidence
            
            feedback = getSimilarityFeedback(similarity)
            feedbackColor = getSimilarityColor(similarity)
            
            // Auto-advance after showing feedback
            DispatchQueue.main.asyncAfter(deadline: .now() + 3.5) {
                if !isCorrect && !gameCompleted {
                    if currentStep < puzzleData.clues.count {
                        currentStep += 1
                        userAnswer = ""
                        showHint = false
                    } else {
                        currentStep = puzzleData.gameFlow.totalSteps
                        gameCompleted = true
                        feedback = "The answer was: \(puzzleData.correctAnswer)"
                        
                        DispatchQueue.main.asyncAfter(deadline: .now() + 3) {
                            onPuzzleComplete(0, false)
                        }
                        return
                    }
                }
                feedback = nil
                showHint = false
                isSubmitting = false
            }
        }
    }
    
    private func skipToNextClue() {
        if currentStep < puzzleData.clues.count {
            currentStep += 1
            userAnswer = ""
            showHint = false
        } else {
            currentStep = puzzleData.gameFlow.totalSteps
        }
    }
    
    private func revealAnswer() {
        currentStep = puzzleData.gameFlow.totalSteps
        gameCompleted = true
        feedback = "Answer revealed: \(puzzleData.correctAnswer)"
        feedbackColor = .blue
        
        DispatchQueue.main.asyncAfter(deadline: .now() + 3) {
            onPuzzleComplete(0, false)
        }
    }
    
    private func startTimer() {
        Timer.scheduledTimer(withTimeInterval: 1.0, repeats: true) { timer in
            if timeRemaining > 0 && !gameCompleted {
                timeRemaining -= 1
            } else if timeRemaining <= 0 && !gameCompleted {
                timer.invalidate()
                gameCompleted = true
                feedback = "Time's up! The answer was: \(puzzleData.correctAnswer)"
                feedbackColor = .red
                
                DispatchQueue.main.asyncAfter(deadline: .now() + 3) {
                    onPuzzleComplete(0, false)
                }
            }
            
            if gameCompleted {
                timer.invalidate()
            }
        }
    }
    
    // MARK: - Helper Functions
    private func formatTime(_ seconds: Int) -> String {
        let minutes = seconds / 60
        let remainingSeconds = seconds % 60
        return String(format: "%d:%02d", minutes, remainingSeconds)
    }
    
    private func difficultyColor(for difficulty: String) -> Color {
        switch difficulty.lowercased() {
        case "hardest": return .red
        case "hard": return .cyan
        case "medium": return .yellow
        case "easy": return .green
        default: return .gray
        }
    }
    
    private func getFeedbackForMatchType(_ matchType: MatchType, score: Int) -> String {
        switch matchType {
        case .exact: return "Perfect! You earned \(score) points!"
        case .synonym: return "Correct (good alternative)! You earned \(score) points!"
        case .partial: return "Close enough! You earned \(score) points!"
        case .fuzzy: return "Correct (watch the spelling)! You earned \(score) points!"
        case .pluralVariation: return "Correct! You earned \(score) points!"
        case .noMatch: return "Incorrect"
        }
    }
    
    private func getSimilarityFeedback(_ similarity: Double) -> String {
        switch similarity {
        case 0.65...1.0: return "So close! Check your spelling or try a different form of the word."
        case 0.45..<0.65: return "You're getting warmer! Think about similar or related words."
        case 0.25..<0.45: return "Not quite right, but you're thinking in the right direction."
        default: return "Not the right answer. Try the next clue for more help!"
        }
    }
    
    private func getSimilarityColor(_ similarity: Double) -> Color {
        switch similarity {
        case 0.65...1.0: return .yellow
        case 0.45..<0.65: return .cyan
        default: return .red
        }
    }
}

class ProgressiveRevealAnswerMatcher {
    
    // MARK: - Main Answer Checking Function
    static func checkAnswer(
        userAnswer: String,
        correctAnswer: String,
        puzzleData: ProgressiveRevealPuzzleData,
        currentStep: Int
    ) -> AnswerMatchResult {
        
        let normalizedUser = normalize(userAnswer)
        let normalizedCorrect = normalize(correctAnswer)
        
        print("🔍 PROGRESSIVE: Checking answer")
        print("🔍 User: '\(userAnswer)' -> '\(normalizedUser)'")
        print("🔍 Correct: '\(correctAnswer)' -> '\(normalizedCorrect)'")
        
        // 1. Exact match (highest priority)
        if normalizedUser == normalizedCorrect {
            print("✅ PROGRESSIVE: EXACT match")
            return AnswerMatchResult(
                isMatch: true,
                confidence: 1.0,
                matchType: .exact,
                explanation: "Perfect match!"
            )
        }
        
        // 2. Plural variations
        if checkPluralVariation(normalizedUser, normalizedCorrect) {
            print("✅ PROGRESSIVE: PLURAL variation match")
            return AnswerMatchResult(
                isMatch: true,
                confidence: 0.98,
                matchType: .pluralVariation,
                explanation: "Plural form accepted"
            )
        }
        
        // 3. Fuzzy match (typos, minor spelling errors)
        let levenshteinScore = levenshteinSimilarity(normalizedUser, normalizedCorrect)
        if levenshteinScore > 0.85 {
            print("✅ PROGRESSIVE: FUZZY match (score: \(levenshteinScore))")
            return AnswerMatchResult(
                isMatch: true,
                confidence: levenshteinScore,
                matchType: .fuzzy,
                explanation: "Close enough (minor spelling difference)"
            )
        }
        
        // 4. Partial match (answer contains or is contained in correct answer)
        if checkPartialMatch(normalizedUser, normalizedCorrect) {
            print("✅ PROGRESSIVE: PARTIAL match")
            return AnswerMatchResult(
                isMatch: true,
                confidence: 0.90,
                matchType: .partial,
                explanation: "Partial match accepted"
            )
        }
        
        // 5. Synonym/similar meaning check
        let synonymScore = checkSynonym(normalizedUser, normalizedCorrect)
        if synonymScore > 0.8 {
            print("✅ PROGRESSIVE: SYNONYM match (score: \(synonymScore))")
            return AnswerMatchResult(
                isMatch: true,
                confidence: synonymScore,
                matchType: .synonym,
                explanation: "Synonym or similar word accepted"
            )
        }
        
        // Not a match - return similarity score for feedback
        let overallSimilarity = max(levenshteinScore, synonymScore, 0.0)
        print("❌ PROGRESSIVE: NO match (similarity: \(overallSimilarity))")
        
        return AnswerMatchResult(
            isMatch: false,
            confidence: overallSimilarity,
            matchType: .noMatch,
            explanation: "Not the correct answer"
        )
    }
    
    // MARK: - Helper Functions
    
    private static func normalize(_ text: String) -> String {
        return text
            .lowercased()
            .trimmingCharacters(in: .whitespacesAndNewlines)
            .replacingOccurrences(of: "[^a-z0-9\\s]", with: "", options: .regularExpression)
            .replacingOccurrences(of: "\\s+", with: " ", options: .regularExpression)
    }
    
    private static func checkPluralVariation(_ user: String, _ correct: String) -> Bool {
        // Check if one is plural of the other
        let variations = [
            (user, correct + "s"),
            (user + "s", correct),
            (user, correct + "es"),
            (user + "es", correct),
            (user, String(correct.dropLast()) + "ies"), // y -> ies
            (user.replacingOccurrences(of: "ies$", with: "y", options: .regularExpression), correct)
        ]
        
        return variations.contains { $0.0 == $0.1 }
    }
    
    private static func checkPartialMatch(_ user: String, _ correct: String) -> Bool {
        // Check if user answer contains correct answer or vice versa
        // But require significant overlap
        if user.contains(correct) || correct.contains(user) {
            let shorter = min(user.count, correct.count)
            let longer = max(user.count, correct.count)
            let ratio = Double(shorter) / Double(longer)
            return ratio > 0.6 // At least 60% overlap
        }
        return false
    }
    
    private static func levenshteinSimilarity(_ s1: String, _ s2: String) -> Double {
        let distance = levenshteinDistance(s1, s2)
        let maxLength = max(s1.count, s2.count)
        guard maxLength > 0 else { return 1.0 }
        return 1.0 - (Double(distance) / Double(maxLength))
    }
    
    private static func levenshteinDistance(_ s1: String, _ s2: String) -> Int {
        let s1Array = Array(s1)
        let s2Array = Array(s2)
        var matrix = [[Int]](repeating: [Int](repeating: 0, count: s2.count + 1), count: s1.count + 1)
        
        for i in 0...s1.count {
            matrix[i][0] = i
        }
        for j in 0...s2.count {
            matrix[0][j] = j
        }
        
        for i in 1...s1.count {
            for j in 1...s2.count {
                let cost = s1Array[i-1] == s2Array[j-1] ? 0 : 1
                matrix[i][j] = min(
                    matrix[i-1][j] + 1,      // deletion
                    matrix[i][j-1] + 1,      // insertion
                    matrix[i-1][j-1] + cost  // substitution
                )
            }
        }
        
        return matrix[s1.count][s2.count]
    }
    
    private static func checkSynonym(_ user: String, _ correct: String) -> Double {
        // Simple synonym/similar word checking
        // This is a basic implementation - could be enhanced with a proper synonym database
        
        let synonymPairs: [Set<String>] = [
            ["car", "automobile", "vehicle", "auto"],
            ["dog", "canine", "puppy", "pup"],
            ["cat", "feline", "kitty", "kitten"],
            ["house", "home", "residence", "dwelling"],
            ["phone", "telephone", "cell", "mobile"],
            ["computer", "pc", "laptop", "desktop"],
            ["happy", "joyful", "glad", "cheerful"],
            ["sad", "unhappy", "gloomy", "depressed"],
            ["big", "large", "huge", "enormous"],
            ["small", "tiny", "little", "miniature"],
            ["beautiful", "pretty", "gorgeous", "lovely"],
            ["ugly", "unattractive", "hideous"],
            ["fast", "quick", "rapid", "swift"],
            ["slow", "sluggish", "gradual"],
            ["hot", "warm", "heated"],
            ["cold", "cool", "chilly", "freezing"],
            ["food", "meal", "cuisine", "dish"],
            ["drink", "beverage", "liquid"],
            ["book", "novel", "tome", "volume"],
            ["movie", "film", "picture", "flick"]
        ]
        
        // Check if both words are in the same synonym set
        for synonymSet in synonymPairs {
            if synonymSet.contains(user) && synonymSet.contains(correct) {
                return 0.95
            }
        }
        
        // Check for common word relationships
        if user.hasPrefix(correct) || correct.hasPrefix(user) {
            let shorter = min(user.count, correct.count)
            let longer = max(user.count, correct.count)
            let ratio = Double(shorter) / Double(longer)
            if ratio > 0.7 {
                return 0.85
            }
        }
        
        // No synonym match found
        return 0.0
    }
}

// MARK: - Main Progressive Reveal Puzzle Data
struct ProgressiveRevealPuzzleData: Codable {
    let puzzleId: String
    let type: String
    let category: String
    let correctAnswer: String
    let clues: [ProgressiveClue]
    let image: ProgressiveImageInfo
    let gameFlow: GameFlowConfig
    let clientConfig: ClientConfig
    let instructions: String
    let timeLimit: Int // milliseconds
    let difficulty: String
    
    var timeLimitSeconds: Int {
        return timeLimit / 1000
    }
}

// MARK: - Progressive Clue
struct ProgressiveClue: Codable {
    let level: Int
    let difficulty: String
    let text: String
    let hint: String?
}

// MARK: - Progressive Image Info
struct ProgressiveImageInfo: Codable {
    let url: String
    let fileName: String
    let blurLevels: [BlurLevel]
    let uploadedAt: String
}

// MARK: - Blur Level
struct BlurLevel: Codable {
    let step: Int
    let blur: Int
    let description: String
}

// MARK: - Game Flow Config
struct GameFlowConfig: Codable {
    let totalSteps: Int
    let timePerClue: Int // milliseconds
    let maxTime: Int // milliseconds
    let scoringSystem: [String: Int]
}

// MARK: - Client Config
struct ClientConfig: Codable {
    let blurProperty: String
    let blurFunction: String
    let blurUnit: String
    let transitionDuration: String
    let fallbackBlur: String
}

// MARK: - Answer Match Result
enum MatchType {
    case exact
    case synonym
    case partial
    case pluralVariation
    case fuzzy
    case noMatch
}

struct AnswerMatchResult {
    let isMatch: Bool
    let confidence: Double
    let matchType: MatchType
    let explanation: String
}

// MARK: - Progressive Reveal Parser
extension ProgressiveRevealPuzzleData {
    static func parse(from puzzle: Puzzle) throws -> ProgressiveRevealPuzzleData {
        print("📋 PROGRESSIVE: Parsing puzzle data")
        print("📋 Raw question: \(puzzle.question)")
        print("📋 Raw answer: \(puzzle.answer)")
        
        guard let questionData = puzzle.question.data(using: .utf8),
              let questionJson = try? JSONSerialization.jsonObject(with: questionData) as? [String: Any] else {
            throw NSError(domain: "ProgressiveReveal", code: 1, 
                         userInfo: [NSLocalizedDescriptionKey: "Failed to parse question JSON"])
        }
        
        guard let answerData = puzzle.answer.data(using: .utf8),
              let answerJson = try? JSONSerialization.jsonObject(with: answerData) as? [String: Any] else {
            throw NSError(domain: "ProgressiveReveal", code: 2,
                         userInfo: [NSLocalizedDescriptionKey: "Failed to parse answer JSON"])
        }
        
        // Extract basic info
        let puzzleId = questionJson["puzzleId"] as? String ?? puzzle.puzzleId
        let type = questionJson["type"] as? String ?? "progressive_revelation"
        let category = questionJson["category"] as? String ?? "general"
        let instructions = questionJson["instructions"] as? String ?? "Guess what's in the image!"
        let timeLimit = questionJson["timeLimit"] as? Int ?? 300000
        let difficulty = questionJson["difficulty"] as? String ?? "medium"
        
        // Extract correct answer
        guard let correctAnswer = answerJson["correctAnswer"] as? String, !correctAnswer.isEmpty else {
            throw NSError(domain: "ProgressiveReveal", code: 3,
                         userInfo: [NSLocalizedDescriptionKey: "No correct answer found"])
        }
        
        // Parse clues
        let cluesArray = questionJson["clues"] as? [[String: Any]] ?? []
        let clues = cluesArray.enumerated().map { index, clueDict -> ProgressiveClue in
            return ProgressiveClue(
                level: clueDict["level"] as? Int ?? (index + 1),
                difficulty: clueDict["difficulty"] as? String ?? "medium",
                text: clueDict["text"] as? String ?? "",
                hint: clueDict["hint"] as? String
            )
        }
        
        print("📋 PROGRESSIVE: Parsed \(clues.count) clues")
        
        // Parse image info
        let imageDict = questionJson["image"] as? [String: Any] ?? [:]
        let imageUrl = imageDict["url"] as? String ?? ""
        let fileName = imageDict["fileName"] as? String ?? "puzzle-image.jpg"
        let uploadedAt = imageDict["uploadedAt"] as? String ?? ""
        
        // Parse blur levels
        let blurLevelsArray = imageDict["blurLevels"] as? [[String: Any]] ?? []
        let blurLevels = blurLevelsArray.enumerated().map { index, blurDict -> BlurLevel in
            return BlurLevel(
                step: blurDict["step"] as? Int ?? (index + 1),
                blur: blurDict["blur"] as? Int ?? 0,
                description: blurDict["description"] as? String ?? ""
            )
        }
        
        print("📋 PROGRESSIVE: Parsed \(blurLevels.count) blur levels")
        
        let imageInfo = ProgressiveImageInfo(
            url: imageUrl,
            fileName: fileName,
            blurLevels: blurLevels,
            uploadedAt: uploadedAt
        )
        
        // Parse game flow
        let gameFlowDict = questionJson["gameFlow"] as? [String: Any] ?? [:]
        let scoringDict = gameFlowDict["scoringSystem"] as? [String: Int] ?? [:]
        
        let gameFlow = GameFlowConfig(
            totalSteps: gameFlowDict["totalSteps"] as? Int ?? 5,
            timePerClue: gameFlowDict["timePerClue"] as? Int ?? 60000,
            maxTime: gameFlowDict["maxTime"] as? Int ?? 300000,
            scoringSystem: scoringDict
        )
        
        // Parse client config
        let clientConfigDict = questionJson["clientConfig"] as? [String: Any] ?? [:]
        let clientConfig = ClientConfig(
            blurProperty: clientConfigDict["blurProperty"] as? String ?? "blur",
            blurFunction: clientConfigDict["blurFunction"] as? String ?? "blur",
            blurUnit: clientConfigDict["blurUnit"] as? String ?? "dp",
            transitionDuration: clientConfigDict["transitionDuration"] as? String ?? "0.5s",
            fallbackBlur: clientConfigDict["fallbackBlur"] as? String ?? "opacity: 0.3"
        )
        
        print("✅ PROGRESSIVE: Successfully parsed puzzle data")
        print("📋 Answer: \(correctAnswer)")
        print("📋 Image URL: \(imageUrl)")
        
        return ProgressiveRevealPuzzleData(
            puzzleId: puzzleId,
            type: type,
            category: category,
            correctAnswer: correctAnswer,
            clues: clues,
            image: imageInfo,
            gameFlow: gameFlow,
            clientConfig: clientConfig,
            instructions: instructions,
            timeLimit: timeLimit,
            difficulty: difficulty
        )
    }
}
