import SwiftUI
import AVFoundation

// MARK: - Data Models
struct WordPrefixWord: Codable {
    let word: String
    let length: Int
    let points: Int
    let rarity: String
    let frequency: Double
    var found: Bool = false
}

struct WordPrefixGameData: Codable {
    let prefix: String
    let allWords: [WordPrefixWord]
    let timeLimit: Int
    let targets: [String: Int] // bronze, silver, gold
    let totalWords: Int
}

struct WordPrefixPuzzleData {
    let prefix: String
    let allWords: [WordPrefixWord]
    let timeLimit: Int
    let targets: [String: Int]
    let totalWords: Int
}

// MARK: - Word Prefix Puzzle View
struct WordPrefixPuzzleView: View {
    let puzzle: Puzzle
    let questionIndex: Int
    let totalQuestions: Int
    let onAnswerSubmitted: (String, Bool) -> Void
    let onNextPuzzle: () -> Void
    let onExit: () -> Void
    
    @State private var gameData: WordPrefixPuzzleData?
    @State private var currentInput: String = ""
    @State private var foundWords: Set<String> = []
    @State private var totalScore: Int = 0
    @State private var currentLevel: String = "bronze"
    @State private var isCompleted: Bool = false
    @State private var showHint: Bool = false
    @State private var lastWordAnimation: Bool = false
    @State private var timeRemaining: Int = 90
    @State private var displayTimer: String = "1:30"
    @State private var showCompletionAlert: Bool = false
    @State private var lastFoundWord: String = ""
    @State private var showWordFoundAnimation: Bool = false
    
    // Timer
    @State private var timer: Timer?
    
    // Feedback system
    @StateObject private var feedbackManager = UnifiedFeedbackManager()
    
    var body: some View {
        GeometryReader { geometry in
            ZStack {
                // Background gradient
                LinearGradient(
                    colors: [
                        Color(red: 0.42, green: 0.35, blue: 0.80), // #6A5ACD
                        Color(red: 0.28, green: 0.24, blue: 0.55), // #483D8B
                        Color(red: 0.18, green: 0.55, blue: 0.34)  // #2E8B57
                    ],
                    startPoint: .top,
                    endPoint: .bottom
                )
                .ignoresSafeArea()
                
                VStack(spacing: 0) {
                    // Top Bar
                    WordPrefixTopBar(
                        timer: displayTimer,
                        hearts: 3,
                        level: "\(questionIndex + 1)/\(totalQuestions)",
                        onBack: onExit,
                        onHint: { showHint = true }
                    )
                    .padding(.horizontal, 16)
                    .padding(.top, 8)
                    
                    if let data = gameData {
                        ScrollView {
                            VStack(spacing: 16) {
                                // Stats Row
                                HStack {
                                    Text("\(foundWords.count)/\(data.totalWords)")
                                        .font(.system(size: 14, weight: .bold))
                                        .foregroundColor(.white)
                                    
                                    Spacer()
                                    
                                    Text("Score: \(totalScore)")
                                        .font(.system(size: 14, weight: .bold))
                                        .foregroundColor(.yellow)
                                    
                                    Spacer()
                                    
                                    Text(currentLevelText(data: data))
                                        .font(.system(size: 12, weight: .bold))
                                        .foregroundColor(currentLevelColor())
                                }
                                .padding(.horizontal, 16)
                                
                                // Input Display
                                WordInputDisplay(
                                    prefix: data.prefix,
                                    currentInput: currentInput,
                                    onSubmit: submitWord
                                )
                                .padding(.horizontal, 16)
                                
                                // Word Tree Progress
                                WordTreeProgress(
                                    foundWordsCount: foundWords.count,
                                    targets: data.targets,
                                    currentLevel: currentLevel,
                                    lastWordAnimation: lastWordAnimation
                                )
                                .frame(height: 120)
                                .padding(.horizontal, 16)
                                
                                // Found Words Section
                                if !foundWords.isEmpty {
                                    VStack(alignment: .leading, spacing: 8) {
                                        Text("Found Words (\(foundWords.count)):")
                                            .font(.system(size: 12, weight: .bold))
                                            .foregroundColor(.white)
                                            .padding(.horizontal, 16)
                                        
                                        ScrollView(.horizontal, showsIndicators: false) {
                                            HStack(spacing: 8) {
                                                ForEach(foundWordsArray(data: data), id: \.word) { word in
                                                    FoundWordChip(word: word)
                                                        .scaleEffect(word.word == lastFoundWord && showWordFoundAnimation ? 1.1 : 1.0)
                                                        .animation(.spring(response: 0.3, dampingFraction: 0.6), value: showWordFoundAnimation)
                                                }
                                                
                                                Spacer(minLength: 16) // Padding at end
                                            }
                                            .padding(.horizontal, 16)
                                        }
                                    }
                                    
                                    // Stats Section
                                    StatsSection(
                                        foundWords: foundWords.count,
                                        totalWords: data.totalWords,
                                        score: totalScore,
                                        currentLevel: currentLevel,
                                        targets: data.targets
                                    )
                                    .padding(.horizontal, 16)
                                }
                                
                                Spacer(minLength: 20)
                            }
                        }
                        .frame(maxWidth: .infinity)
                        
                        // Custom Alphabet Keyboard
                        CustomAlphabetKeyboard(
                            onKeyPress: { letter in
                                if currentInput.count < 15 {
                                    currentInput += letter
                                }
                            },
                            onBackspace: {
                                if !currentInput.isEmpty {
                                    currentInput = String(currentInput.dropLast())
                                }
                            },
                            onSubmit: submitWord
                        )
                    } else {
                        Spacer()
                        ProgressView("Loading puzzle...")
                            .foregroundColor(.white)
                        Spacer()
                    }
                }
            }
        }
        .navigationBarHidden(true)
        .alert("💡 Hint", isPresented: $showHint) {
            Button("Got it!") { showHint = false }
        } message: {
            if let data = gameData {
                Text("Find words that start with \"\(data.prefix.uppercased())\". Try common words first!\n\nTargets:\n🥉 Bronze: \(data.targets["bronze"] ?? 0) words\n🥈 Silver: \(data.targets["silver"] ?? 0) words\n🥇 Gold: \(data.targets["gold"] ?? 0) words")
            }
        }
        .onAppear {
            parseGameData()
            startTimer()
        }
        .onDisappear {
            stopTimer()
        }
    }
    
    // MARK: - Helper Methods
    private func parseGameData() {
        print("🔍 WORDPREFIX: Parsing game data from puzzle question")
        print("🔍 WORDPREFIX: Question: \(puzzle.question)")
        
        guard let questionData = puzzle.question.data(using: .utf8) else {
            print("🔴 WORDPREFIX: Failed to create data from question string")
            return
        }
        
        do {
            // First try to parse as the answer JSON directly (for direct mode)
            if let answerData = try? JSONSerialization.jsonObject(with: questionData) as? [String: Any],
               let timeLimit = answerData["timeLimit"] as? Int {
                print("🟢 WORDPREFIX: Parsing as direct answer JSON")
                parseDirectAnswerData(answerData)
                return
            }
            
            // Otherwise, try the nested structure
            if let outerJson = try? JSONSerialization.jsonObject(with: questionData) as? [String: Any] {
                print("🔍 WORDPREFIX: Trying nested structure")
                
                let prefix: String
                let answerJsonString: String
                
                if let questionString = outerJson["question"] as? String,
                   let answerString = outerJson["answer"] as? String {
                    // Nested format: {"question": "com", "answer": "{...}"}
                    prefix = questionString
                    answerJsonString = answerString
                } else {
                    print("🔴 WORDPREFIX: Unsupported JSON structure")
                    return
                }
                
                guard let answerData = answerJsonString.data(using: .utf8),
                      let answerJson = try JSONSerialization.jsonObject(with: answerData) as? [String: Any] else {
                    print("🔴 WORDPREFIX: Failed to parse answer JSON")
                    return
                }
                
                parseAnswerData(answerJson, prefix: prefix)
            }
        } catch {
            print("🔴 WORDPREFIX: Error parsing game data: \(error)")
        }
    }
    
    private func parseDirectAnswerData(_ answerData: [String: Any]) {
        guard let timeLimit = answerData["timeLimit"] as? Int,
              let allWordsArray = answerData["allWords"] as? [[String: Any]],
              let targetsJson = answerData["targets"] as? [String: Int],
              let metadataJson = answerData["metadata"] as? [String: Any],
              let totalWords = metadataJson["totalWords"] as? Int else {
            print("🔴 WORDPREFIX: Missing required fields in direct answer data")
            return
        }
        
        // Extract prefix from first word (fallback method)
        let extractedPrefix: String
        if !allWordsArray.isEmpty,
           let firstWordData = allWordsArray.first,
           let firstWord = firstWordData["word"] as? String {
            extractedPrefix = String(firstWord.prefix(3)).lowercased()
        } else {
            extractedPrefix = "word"
        }
        
        parseCommonData(
            prefix: extractedPrefix,
            timeLimit: timeLimit,
            allWordsArray: allWordsArray,
            targetsJson: targetsJson,
            totalWords: totalWords
        )
    }
    
    private func parseAnswerData(_ answerData: [String: Any], prefix: String) {
        guard let timeLimit = answerData["timeLimit"] as? Int,
              let allWordsArray = answerData["allWords"] as? [[String: Any]],
              let targetsJson = answerData["targets"] as? [String: Int],
              let metadataJson = answerData["metadata"] as? [String: Any],
              let totalWords = metadataJson["totalWords"] as? Int else {
            print("🔴 WORDPREFIX: Missing required fields in answer data")
            return
        }
        
        parseCommonData(
            prefix: prefix,
            timeLimit: timeLimit,
            allWordsArray: allWordsArray,
            targetsJson: targetsJson,
            totalWords: totalWords
        )
    }
    
    private func parseCommonData(
        prefix: String,
        timeLimit: Int,
        allWordsArray: [[String: Any]],
        targetsJson: [String: Int],
        totalWords: Int
    ) {
        var words: [WordPrefixWord] = []
        
        for wordData in allWordsArray {
            guard let word = wordData["word"] as? String,
                  let length = wordData["length"] as? Int,
                  let points = wordData["points"] as? Int,
                  let rarity = wordData["rarity"] as? String,
                  let frequency = wordData["frequency"] as? Double else {
                continue
            }
            
            words.append(WordPrefixWord(
                word: word,
                length: length,
                points: points,
                rarity: rarity,
                frequency: frequency
            ))
        }
        
        self.gameData = WordPrefixPuzzleData(
            prefix: prefix,
            allWords: words,
            timeLimit: timeLimit,
            targets: targetsJson,
            totalWords: totalWords
        )
        
        self.timeRemaining = timeLimit
        updateDisplayTimer()
        
        print("🟢 WORDPREFIX: Successfully parsed game data")
        print("🟢 WORDPREFIX: Prefix: \(prefix)")
        print("🟢 WORDPREFIX: Total words: \(totalWords)")
        print("🟢 WORDPREFIX: Time limit: \(timeLimit)s")
    }
    
    private func startTimer() {
        timer = Timer.scheduledTimer(withTimeInterval: 1.0, repeats: true) { _ in
            if timeRemaining > 0 && !isCompleted {
                timeRemaining -= 1
                updateDisplayTimer()
            } else if timeRemaining <= 0 && !isCompleted {
                handleTimeUp()
            }
        }
    }
    
    private func stopTimer() {
        timer?.invalidate()
        timer = nil
    }
    
    private func updateDisplayTimer() {
        let minutes = timeRemaining / 60
        let seconds = timeRemaining % 60
        displayTimer = String(format: "%d:%02d", minutes, seconds)
    }
    
    private func handleTimeUp() {
        isCompleted = true
        stopTimer()
        
        if foundWords.isEmpty {
            // No words found - show negative feedback
            onAnswerSubmitted("No words found", false)
        } else {
            // Found some words - show positive feedback with score
            onAnswerSubmitted("\(foundWords.count) words found", true)
        }
        
        // Move to next puzzle after a delay
        DispatchQueue.main.asyncAfter(deadline: .now() + 2.0) {
            onNextPuzzle()
        }
    }
    
    private func submitWord() {
        guard let data = gameData else { return }
        
        let inputWord = (data.prefix + currentInput).lowercased().trimmingCharacters(in: .whitespaces)
        
        guard !inputWord.isEmpty else {
            currentInput = ""
            return
        }
        
        if let foundWord = data.allWords.first(where: { word in
            word.word.lowercased() == inputWord && !foundWords.contains(word.word.lowercased())
        }) {
            // Word found!
            foundWords.insert(foundWord.word.lowercased())
            totalScore += foundWord.points
            lastFoundWord = foundWord.word
            currentInput = ""
            
            // Trigger animations
            lastWordAnimation = true
            showWordFoundAnimation = true
            
            // Haptic feedback
            let impactFeedback = UIImpactFeedbackGenerator(style: .medium)
            impactFeedback.impactOccurred()
            
            // Reset animations
            DispatchQueue.main.asyncAfter(deadline: .now() + 0.5) {
                lastWordAnimation = false
                showWordFoundAnimation = false
            }
            
            // Check level progression
            updateCurrentLevel(data: data)
            
            // Check if gold target reached
            if foundWords.count >= (data.targets["gold"] ?? 0) {
                isCompleted = true
                stopTimer()
                onAnswerSubmitted("\(foundWords.count) words found", true)
                
                DispatchQueue.main.asyncAfter(deadline: .now() + 1.5) {
                    onNextPuzzle()
                }
            }
            
            print("🟢 WORDPREFIX: Word found: \(foundWord.word.uppercased()) (+\(foundWord.points) points)")
        } else {
            // Word not found or already found
            currentInput = ""
            
            // Error haptic feedback
            let errorFeedback = UINotificationFeedbackGenerator()
            errorFeedback.notificationOccurred(.error)
            
            print("🔴 WORDPREFIX: Word not in list or already found: \(inputWord.uppercased())")
        }
    }
    
    private func updateCurrentLevel(data: WordPrefixPuzzleData) {
        let newLevel: String
        if foundWords.count >= (data.targets["gold"] ?? 0) {
            newLevel = "gold"
        } else if foundWords.count >= (data.targets["silver"] ?? 0) {
            newLevel = "silver"
        } else if foundWords.count >= (data.targets["bronze"] ?? 0) {
            newLevel = "bronze"
        } else {
            newLevel = "none"
        }
        
        if newLevel != currentLevel {
            currentLevel = newLevel
            // Haptic feedback for level progression
            let successFeedback = UINotificationFeedbackGenerator()
            successFeedback.notificationOccurred(.success)
        }
    }
    
    private func currentLevelText(data: WordPrefixPuzzleData) -> String {
        switch currentLevel {
        case "gold": return "🥇 GOLD"
        case "silver": return "🥈 SILVER"
        case "bronze": return "🥉 BRONZE"
        default: return "🎯 \(data.targets["bronze"] ?? 0)"
        }
    }
    
    private func currentLevelColor() -> Color {
        switch currentLevel {
        case "gold": return .yellow
        case "silver": return Color(red: 0.75, green: 0.75, blue: 0.75)
        case "bronze": return Color(red: 0.80, green: 0.50, blue: 0.20)
        default: return .white.opacity(0.8)
        }
    }
    
    private func foundWordsArray(data: WordPrefixPuzzleData) -> [WordPrefixWord] {
        return data.allWords
            .filter { foundWords.contains($0.word.lowercased()) }
            .reversed()
    }
}

// MARK: - Supporting Views
struct WordPrefixTopBar: View {
    let timer: String
    let hearts: Int
    let level: String
    let onBack: () -> Void
    let onHint: () -> Void
    
    var body: some View {
        HStack {
            // Left side
            HStack(spacing: 12) {
                Button(action: onBack) {
                    Image(systemName: "chevron.left")
                        .font(.title2)
                        .foregroundColor(.white)
                }
                
                Button(action: onHint) {
                    Image(systemName: "lightbulb.fill")
                        .font(.title2)
                        .foregroundColor(.yellow)
                }
                
                Text("Level \(level)")
                    .font(.system(size: 16, weight: .bold))
                    .foregroundColor(.white)
            }
            
            Spacer()
            
            // Center: Hearts and Timer
            VStack(spacing: 4) {
                HStack(spacing: 4) {
                    ForEach(0..<hearts, id: \.self) { _ in
                        Image(systemName: "heart.fill")
                            .font(.system(size: 16))
                            .foregroundColor(.red)
                    }
                }
                
                Text(timer)
                    .font(.system(size: 18, weight: .bold))
                    .foregroundColor(.white)
            }
            
            Spacer()
            
            // Right side: placeholder for balance
            HStack(spacing: 12) {
                Text("")
                    .font(.title2)
                    .opacity(0)
                Text("")
                    .font(.title2)
                    .opacity(0)
                Text("")
                    .font(.system(size: 16, weight: .bold))
                    .opacity(0)
            }
        }
    }
}

struct WordInputDisplay: View {
    let prefix: String
    let currentInput: String
    let onSubmit: () -> Void
    
    var body: some View {
        RoundedRectangle(cornerRadius: 12)
            .fill(Color.white.opacity(0.9))
            .frame(height: 70)
            .overlay(
                HStack {
                    // Left side: Prefix + Input
                    HStack(spacing: 0) {
                        Text(prefix.uppercased())
                            .font(.system(size: 28, weight: .bold))
                            .foregroundColor(Color(red: 0.28, green: 0.24, blue: 0.55))
                        
                        Text(currentInput.uppercased())
                            .font(.system(size: 28, weight: .bold))
                            .foregroundColor(Color(red: 0.18, green: 0.55, blue: 0.34))
                        
                        if currentInput.isEmpty {
                            Text("|")
                                .font(.system(size: 28, weight: .bold))
                                .foregroundColor(Color(red: 0.18, green: 0.55, blue: 0.34).opacity(0.7))
                                .animation(.easeInOut(duration: 1.0).repeatForever(), value: true)
                        }
                    }
                    
                    Spacer()
                    
                    // Right side: Submit button
                    Button(action: onSubmit) {
                        Circle()
                            .fill(Color(red: 0.0, green: 0.78, blue: 0.32))
                            .frame(width: 44, height: 44)
                            .overlay(
                                Image(systemName: "paperplane.fill")
                                    .font(.system(size: 18))
                                    .foregroundColor(.white)
                            )
                    }
                }
                .padding(.horizontal, 20)
            )
    }
}

struct FoundWordChip: View {
    let word: WordPrefixWord
    
    var backgroundColor: Color {
        switch word.rarity {
        case "rare": return Color(red: 1.0, green: 0.42, blue: 0.21) // #FF6B35
        case "uncommon": return Color(red: 0.31, green: 0.80, blue: 0.77) // #4ECDC4
        default: return Color(red: 0.27, green: 0.72, blue: 0.82) // #45B7D1
        }
    }
    
    var body: some View {
        VStack(spacing: 4) {
            Text(word.word.uppercased())
                .font(.system(size: 10, weight: .bold))
                .foregroundColor(.white)
            
            Text("+\(word.points)")
                .font(.system(size: 9, weight: .bold))
                .foregroundColor(.white)
        }
        .padding(.horizontal, 8)
        .padding(.vertical, 6)
        .background(backgroundColor)
        .cornerRadius(8)
    }
}

struct WordTreeProgress: View {
    let foundWordsCount: Int
    let targets: [String: Int]
    let currentLevel: String
    let lastWordAnimation: Bool
    
    private var treeScale: CGFloat {
        lastWordAnimation ? 1.1 : 1.0
    }
    
    var body: some View {
        VStack(spacing: 16) {
            // Tree visualization
            ZStack {
                // Tree trunk
                RoundedRectangle(cornerRadius: 6)
                    .fill(Color(red: 0.55, green: 0.27, blue: 0.07))
                    .frame(width: 12, height: 40)
                    .offset(y: 20)
                
                // Tree crown - grows and changes color with progress
                let crownSize: CGFloat = 80 + CGFloat(foundWordsCount * 2).clamped(to: 0...40)
                let crownColor = currentLevelColor()
                
                Circle()
                    .fill(
                        RadialGradient(
                            colors: [crownColor.opacity(0.8), Color(red: 0.13, green: 0.55, blue: 0.13)],
                            center: .center,
                            startRadius: 0,
                            endRadius: crownSize / 2
                        )
                    )
                    .frame(width: crownSize, height: crownSize)
                    .scaleEffect(treeScale)
                    .animation(.spring(response: 0.3, dampingFraction: 0.6), value: treeScale)
                    .overlay(
                        // Fruits/leaves representing found words
                        ForEach(0..<min(foundWordsCount, 20), id: \.self) { index in
                            Circle()
                                .fill(Color.yellow)
                                .frame(width: 6, height: 6)
                                .offset(
                                    x: CGFloat.random(in: -crownSize/3...crownSize/3),
                                    y: CGFloat.random(in: -crownSize/3...crownSize/3)
                                )
                        }
                    )
            }
            .frame(height: 80)
            
            // Progress text
            VStack(spacing: 4) {
                Text("\(foundWordsCount) words found")
                    .font(.system(size: 16, weight: .bold))
                    .foregroundColor(.white)
                
                Text(currentLevelText())
                    .font(.system(size: 14, weight: .medium))
                    .foregroundColor(currentLevelColor())
            }
        }
    }
    
    private func currentLevelColor() -> Color {
        switch currentLevel {
        case "gold": return .yellow
        case "silver": return Color(red: 0.75, green: 0.75, blue: 0.75)
        case "bronze": return Color(red: 0.80, green: 0.50, blue: 0.20)
        default: return Color(red: 0.13, green: 0.55, blue: 0.13)
        }
    }
    
    private func currentLevelText() -> String {
        switch currentLevel {
        case "gold": return "🥇 GOLD LEVEL!"
        case "silver": return "🥈 Silver Level"
        case "bronze": return "🥉 Bronze Level"
        default: return "Keep finding words..."
        }
    }
}

struct StatsSection: View {
    let foundWords: Int
    let totalWords: Int
    let score: Int
    let currentLevel: String
    let targets: [String: Int]
    
    var body: some View {
        RoundedRectangle(cornerRadius: 12)
            .fill(Color.white.opacity(0.1))
            .frame(height: 80)
            .overlay(
                HStack {
                    WPStatItem(
                        label: "Found",
                        value: "\(foundWords)/\(totalWords)",
                        color: .white
                    )
                    
                    Spacer()
                    
                    WPStatItem(
                        label: "Score",
                        value: "\(score)",
                        color: .yellow
                    )
                    
                    Spacer()
                    
                    WPStatItem(
                        label: "Next Target",
                        value: nextTargetText(),
                        color: nextTargetColor()
                    )
                }
                .padding(.horizontal, 16)
            )
    }
    
    private func nextTargetText() -> String {
        switch currentLevel {
        case "gold": return "MAX!"
        case "silver": return "\(targets["gold"] ?? 0)"
        case "bronze": return "\(targets["silver"] ?? 0)"
        default: return "\(targets["bronze"] ?? 0)"
        }
    }
    
    private func nextTargetColor() -> Color {
        switch currentLevel {
        case "gold": return .yellow
        case "silver": return Color(red: 0.75, green: 0.75, blue: 0.75)
        default: return Color(red: 0.80, green: 0.50, blue: 0.20)
        }
    }
}

struct WPStatItem: View {
    let label: String
    let value: String
    let color: Color
    
    var body: some View {
        VStack(spacing: 4) {
            Text(value)
                .font(.system(size: 20, weight: .bold))
                .foregroundColor(color)
            
            Text(label)
                .font(.system(size: 12))
                .foregroundColor(.white.opacity(0.7))
        }
    }
}

struct CustomAlphabetKeyboard: View {
    let onKeyPress: (String) -> Void
    let onBackspace: () -> Void
    let onSubmit: () -> Void
    
    private let keyboardRows = [
        ["Q", "W", "E", "R", "T", "Y", "U", "I", "O", "P"],
        ["A", "S", "D", "F", "G", "H", "J", "K", "L"],
        ["Z", "X", "C", "V", "B", "N", "M"]
    ]
    
    var body: some View {
        VStack(spacing: 8) {
            // First two rows
            ForEach(0..<2, id: \.self) { rowIndex in
                HStack(spacing: 6) {
                    ForEach(keyboardRows[rowIndex], id: \.self) { letter in
                        KeyboardKey(
                            text: letter,
                            backgroundColor: Color(red: 0.42, green: 0.30, blue: 0.58),
                            onTap: { onKeyPress(letter.lowercased()) }
                        )
                        .frame(maxWidth: .infinity)
                    }
                }
                .frame(height: 56)
            }
            
            // Bottom row with letters, backspace, and enter
            HStack(spacing: 6) {
                ForEach(keyboardRows[2], id: \.self) { letter in
                    KeyboardKey(
                        text: letter,
                        backgroundColor: Color(red: 0.42, green: 0.30, blue: 0.58),
                        onTap: { onKeyPress(letter.lowercased()) }
                    )
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
                }
                
                KeyboardKey(
                    text: "⌫",
                    backgroundColor: Color(red: 1.0, green: 0.42, blue: 0.21),
                    onTap: onBackspace
                )
                .frame(width: 60)
                
                KeyboardKey(
                    text: "ENTER",
                    backgroundColor: Color(red: 0.0, green: 0.78, blue: 0.32),
                    fontSize: 12,
                    onTap: onSubmit
                )
                .frame(width: 80)
            }
            .frame(height: 56)
        }
        .padding(16)
        .background(
            RoundedRectangle(cornerRadius: 16)
                .fill(Color(red: 0.18, green: 0.11, blue: 0.41))
        )
    }
}

struct KeyboardKey: View {
    let text: String
    let backgroundColor: Color
    var fontSize: CGFloat = 16
    let onTap: () -> Void
    
    var body: some View {
        Button(action: {
            // Haptic feedback
            let impactFeedback = UIImpactFeedbackGenerator(style: .light)
            impactFeedback.impactOccurred()
            onTap()
        }) {
            Text(text)
                .font(.system(size: fontSize, weight: .bold))
                .foregroundColor(.white)
                .frame(maxWidth: .infinity, maxHeight: .infinity)
                .background(backgroundColor)
                .cornerRadius(12)
        }
        .buttonStyle(PlainButtonStyle())
    }
}

// MARK: - Extensions
extension Puzzle {
    var wordPrefixPuzzleData: WordPrefixPuzzleData? {
        guard let questionData = question.data(using: .utf8) else {
            print("🔴 WORDPREFIX: Failed to create data from question string")
            return nil
        }
        
        do {
            // Try parsing as direct answer JSON first
            if let answerData = try? JSONSerialization.jsonObject(with: questionData) as? [String: Any],
               let timeLimit = answerData["timeLimit"] as? Int,
               let allWordsArray = answerData["allWords"] as? [[String: Any]],
               let targetsJson = answerData["targets"] as? [String: Int],
               let metadataJson = answerData["metadata"] as? [String: Any],
               let totalWords = metadataJson["totalWords"] as? Int {
                
                // Extract prefix from first word (fallback method)
                let extractedPrefix: String
                if !allWordsArray.isEmpty,
                   let firstWordData = allWordsArray.first,
                   let firstWord = firstWordData["word"] as? String {
                    extractedPrefix = String(firstWord.prefix(3)).lowercased()
                } else {
                    extractedPrefix = "word"
                }
                
                return parseWordPrefixData(
                    prefix: extractedPrefix,
                    timeLimit: timeLimit,
                    allWordsArray: allWordsArray,
                    targetsJson: targetsJson,
                    totalWords: totalWords
                )
            }
            
            // Try nested structure
            if let outerJson = try JSONSerialization.jsonObject(with: questionData) as? [String: Any] {
                let prefix: String
                let answerJsonString: String
                
                if let questionString = outerJson["question"] as? String,
                   let answerString = outerJson["answer"] as? String {
                    // Nested format: {"question": "com", "answer": "{...}"}
                    prefix = questionString
                    answerJsonString = answerString
                } else {
                    print("🔴 WORDPREFIX: Unsupported JSON structure")
                    return nil
                }
                
                guard let answerData = answerJsonString.data(using: .utf8),
                      let answerJson = try JSONSerialization.jsonObject(with: answerData) as? [String: Any],
                      let timeLimit = answerJson["timeLimit"] as? Int,
                      let allWordsArray = answerJson["allWords"] as? [[String: Any]],
                      let targetsJson = answerJson["targets"] as? [String: Int],
                      let metadataJson = answerJson["metadata"] as? [String: Any],
                      let totalWords = metadataJson["totalWords"] as? Int else {
                    print("🔴 WORDPREFIX: Failed to parse nested answer JSON")
                    return nil
                }
                
                return parseWordPrefixData(
                    prefix: prefix,
                    timeLimit: timeLimit,
                    allWordsArray: allWordsArray,
                    targetsJson: targetsJson,
                    totalWords: totalWords
                )
            }
        } catch {
            print("🔴 WORDPREFIX: Error parsing data: \(error)")
        }
        
        return nil
    }
    
    private func parseWordPrefixData(
        prefix: String,
        timeLimit: Int,
        allWordsArray: [[String: Any]],
        targetsJson: [String: Int],
        totalWords: Int
    ) -> WordPrefixPuzzleData? {
        var words: [WordPrefixWord] = []
        
        for wordData in allWordsArray {
            guard let word = wordData["word"] as? String,
                  let length = wordData["length"] as? Int,
                  let points = wordData["points"] as? Int,
                  let rarity = wordData["rarity"] as? String,
                  let frequency = wordData["frequency"] as? Double else {
                continue
            }
            
            words.append(WordPrefixWord(
                word: word,
                length: length,
                points: points,
                rarity: rarity,
                frequency: frequency
            ))
        }
        
        return WordPrefixPuzzleData(
            prefix: prefix,
            allWords: words,
            timeLimit: timeLimit,
            targets: targetsJson,
            totalWords: totalWords
        )
    }
}

extension CGFloat {
    func clamped(to range: ClosedRange<CGFloat>) -> CGFloat {
        return Swift.min(Swift.max(self, range.lowerBound), range.upperBound)
    }
}
