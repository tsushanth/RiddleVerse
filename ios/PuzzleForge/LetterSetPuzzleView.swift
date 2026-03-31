//
//  LetterSetPuzzleView.swift
//  PuzzleForge
//

import SwiftUI

// MARK: - Data Models
struct LetterSetPuzzle: Codable {
    let letterSet: String
    let letters: [String]
    let allWords: [LetterSetWord]
    let keyWord: String?
    let difficulty: String
    let timeLimit: Int
    let scoring: LetterSetScoring
    let targets: LetterSetTargets
    let metadata: LetterSetMetadata
}

struct LetterSetWord: Codable {
    let word: String
    let length: Int
    let points: Int
    let rarity: String
    let frequency: Int
    let usesAllLetters: Bool
}

struct LetterSetScoring: Codable {
    let basePointsPerWord: Int
    let lengthMultiplier: [String: Double]
    let rarityBonus: [String: Int]
    let allLettersBonus: Int
    let speedBonus: [String: Int]?
}

struct LetterSetTargets: Codable {
    let bronze: Int
    let silver: Int
    let gold: Int
}

struct LetterSetMetadata: Codable {
    let generatedAt: String
    let totalWords: Int
    let source: String
    let expectedDifficulty: String
    let letterSetSource: String
    let keyWordFound: Bool
}

struct FoundWord: Identifiable {
    let id = UUID()
    let word: String
    let points: Int
    let isDiscovery: Bool
}

// MARK: - Main View
struct LetterSetPuzzleView: View {
    let puzzle: Puzzle
    let onBack: () -> Void
    let onComplete: (Bool, Bool) -> Void
    
    @State private var puzzleData: LetterSetPuzzle?
    @State private var currentWord: String = ""
    @State private var foundWords: [FoundWord] = []
    @State private var score: Int = 0
    @State private var timeRemaining: Int = 180
    @State private var discoveredWords: Int = 0
    @State private var showCelebration: Bool = false
    @State private var showEarlyFinishDialog: Bool = false
    @State private var isLoading: Bool = true
    @State private var errorMessage: String?
    @State private var wordValidator: LocalWordValidator?
    @State private var hints: [String] = []
    @State private var showInvalidWordAlert: Bool = false
    @State private var invalidWordMessage: String = ""
    
    private let timer = Timer.publish(every: 1, on: .main, in: .common).autoconnect()
    
    var body: some View {
        ZStack {
            if isLoading {
                LoadingView(message: "Loading letter set puzzle...")
            } else if let error = errorMessage {
                ErrorView(message: error, retryAction: onBack)
            } else if let data = puzzleData {
                puzzleContent(data: data)
            }
            
            if showCelebration, let data = puzzleData {
                CelebrationOverlay(
                    score: score,
                    wordsFound: foundWords.count,
                    discoveredWords: discoveredWords,
                    timeBonus: timeRemaining > data.timeLimit / 2,
                    targets: data.targets
                )
            }
        }
        .onAppear {
            parsePuzzle()
            initializeValidator()
        }
        .onReceive(timer) { _ in
            if timeRemaining > 0 && !showCelebration {
                timeRemaining -= 1
            } else if timeRemaining == 0 {
                completeGame()
            }
        }
        .alert("Finish Early?", isPresented: $showEarlyFinishDialog) {
            Button("Keep Playing", role: .cancel) { }
            Button("Finish") { completeGame() }
        } message: {
            if let data = puzzleData {
                Text(getEarlyFinishMessage(data: data))
            }
        }
        .alert("Invalid Word", isPresented: $showInvalidWordAlert) {
            Button("OK", role: .cancel) { }
        } message: {
            Text(invalidWordMessage)
        }
    }
    
    private func hintsSection() -> some View {
        VStack(alignment: .leading, spacing: 8) {
            Text("💡 Possible Words")
                .font(.system(size: 14, weight: .bold))
                .foregroundColor(.yellow)
            
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 8) {
                    ForEach(hints.prefix(10), id: \.self) { hint in
                        Text(hint)
                            .font(.system(size: 12))
                            .foregroundColor(.white)
                            .padding(.horizontal, 8)
                            .padding(.vertical, 4)
                            .background(
                                RoundedRectangle(cornerRadius: 8)
                                    .fill(Color.yellow.opacity(0.3))
                            )
                    }
                }
            }
        }
    }
    
    private func puzzleContent(data: LetterSetPuzzle) -> some View {
        VStack(spacing: 0) {
            // Header
            headerSection(data: data)
            
            // Progress
            if foundWords.count > 0 {
                progressSection(data: data)
            }
            
            // Main content
            ScrollView {
                VStack(spacing: 20) {
                    // Current word display
                    currentWordDisplay()
                    
                    // Found words
                    foundWordsSection(data: data)
                    
                    // Show hints if available
                    if !hints.isEmpty {
                        hintsSection()
                    }
                    
                    Spacer()
                }
                .padding()
            }
            
            // Letter grid and controls
            VStack(spacing: 16) {
                letterGrid(data: data)
                actionButtons()
            }
            .padding()
            .background(Color(.systemGray6))
        }
    }
    
    private func headerSection(data: LetterSetPuzzle) -> some View {
        HStack {
            Button(action: {
                print("🔙 BACK: Letter set back button tapped")
                onBack()
            }) {
                Image(systemName: "pause.fill")
                    .foregroundColor(.white)
                    .frame(width: 40, height: 40)
                    .background(Color.white.opacity(0.2))
                    .cornerRadius(8)
            }
            
            Spacer()
            
            VStack {
                Text(data.letterSet)
                    .font(.system(size: 18, weight: .bold))
                    .foregroundColor(.white)
                
                Text(formatTime(timeRemaining))
                    .font(.system(size: 16, weight: .bold))
                    .foregroundColor(timeRemaining < 60 ? .red : Color(hex: "FFD700"))
            }
            
            Spacer()
            
            VStack(alignment: .trailing) {
                Text("Score: \(score)")
                    .font(.system(size: 14, weight: .bold))
                    .foregroundColor(.white)
                
                if discoveredWords > 0 {
                    Text("🎉 +\(discoveredWords) new")
                        .font(.system(size: 10))
                        .foregroundColor(Color(hex: "FFD700"))
                }
                
                if foundWords.count >= data.targets.bronze {
                    Button("Finish Early") {
                        showEarlyFinishDialog = true
                    }
                    .font(.system(size: 10))
                    .foregroundColor(.white.opacity(0.8))
                    .padding(.top, 4)
                }
            }
        }
        .padding()
        .background(
            LinearGradient(
                colors: [Color(hex: "4A4A6B") ?? Color(red: 0.29, green: 0.29, blue: 0.42),
                         Color(hex: "5A5A7A") ?? Color(red: 0.35, green: 0.35, blue: 0.48)],
                startPoint: .top,
                endPoint: .bottom
            )
        )
    }
    
    private func progressSection(data: LetterSetPuzzle) -> some View {
        VStack(spacing: 4) {
            GeometryReader { geometry in
                ZStack(alignment: .leading) {
                    Rectangle()
                        .fill(Color.gray.opacity(0.3))
                        .frame(height: 6)
                    
                    Rectangle()
                        .fill(progressColor(data: data))
                        .frame(width: geometry.size.width * CGFloat(foundWords.count) / CGFloat(data.targets.gold), height: 6)
                }
            }
            .frame(height: 6)
            
            HStack {
                Text("🥉 \(data.targets.bronze)")
                    .font(.system(size: 10))
                    .foregroundColor(foundWords.count >= data.targets.bronze ? Color(hex: "CD7F32") : .gray)
                    .fontWeight(foundWords.count >= data.targets.bronze ? .bold : .regular)
                
                Spacer()
                
                Text("🥈 \(data.targets.silver)")
                    .font(.system(size: 10))
                    .foregroundColor(foundWords.count >= data.targets.silver ? Color(hex: "C0C0C0") : .gray)
                    .fontWeight(foundWords.count >= data.targets.silver ? .bold : .regular)
                
                Spacer()
                
                Text("🥇 \(data.targets.gold)")
                    .font(.system(size: 10))
                    .foregroundColor(foundWords.count >= data.targets.gold ? Color(hex: "FFD700") : .gray)
                    .fontWeight(foundWords.count >= data.targets.gold ? .bold : .regular)
            }
            .padding(.horizontal)
        }
        .padding(.horizontal)
        .padding(.vertical, 8)
    }
    
    private func currentWordDisplay() -> some View {
        ZStack {
            RoundedRectangle(cornerRadius: 12)
                .fill(Color.white.opacity(0.1))
                .frame(height: 80)
            
            Text(currentWord.isEmpty ? "TAP LETTERS TO FORM WORDS" : currentWord.uppercased())
                .font(.system(size: currentWord.isEmpty ? 14 : 24, weight: .bold))
                .foregroundColor(.white)
        }
    }
    
    private func foundWordsSection(data: LetterSetPuzzle) -> some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack {
                Text("Found Words (\(foundWords.count))")
                    .font(.system(size: 14, weight: .bold))
                    .foregroundColor(.white.opacity(0.8))
                
                Spacer()
                
                Text("Target: \(data.targets.bronze)+")
                    .font(.system(size: 12))
                    .foregroundColor(.white.opacity(0.6))
            }
            
            if foundWords.isEmpty {
                VStack(spacing: 8) {
                    Text("No words found yet")
                        .font(.system(size: 14))
                        .foregroundColor(.white.opacity(0.6))
                        .frame(maxWidth: .infinity)
                    
                    if !hints.isEmpty {
                        Text("💡 Try: \(hints.prefix(3).joined(separator: ", "))")
                            .font(.system(size: 12))
                            .foregroundColor(.yellow.opacity(0.8))
                            .frame(maxWidth: .infinity)
                    }
                }
            } else {
                ScrollView(.horizontal, showsIndicators: false) {
                    HStack(spacing: 8) {
                        ForEach(foundWords) { word in
                            foundWordChip(word: word)
                        }
                    }
                }
            }
        }
    }
    
    private func foundWordChip(word: FoundWord) -> some View {
        let backgroundColor = word.isDiscovery ?
            (Color(hex: "FFD700") ?? Color.yellow).opacity(0.4) :
            (Color(hex: "4CAF50") ?? Color.green).opacity(0.4)
        
        return VStack(spacing: 2) {
            HStack(spacing: 2) {
                if word.isDiscovery {
                    Text("🎉")
                        .font(.system(size: 8))
                }
                Text(word.word)
                    .font(.system(size: 12, weight: .bold))
                    .foregroundColor(.white)
            }
            
            Text("\(word.points)pts")
                .font(.system(size: 10))
                .foregroundColor(.white.opacity(0.8))
        }
        .padding(.horizontal, 12)
        .padding(.vertical, 6)
        .background(
            RoundedRectangle(cornerRadius: 16)
                .fill(backgroundColor)
        )
    }
    
    private func letterGrid(data: LetterSetPuzzle) -> some View {
        let rows = data.letters.chunked(into: 3)
        
        return VStack(spacing: 8) {
            ForEach(0..<rows.count, id: \.self) { rowIndex in
                HStack(spacing: 8) {
                    ForEach(rows[rowIndex], id: \.self) { letter in
                        letterButton(letter: letter)
                    }
                    
                    // Add spacers if row is incomplete
                    ForEach(0..<(3 - rows[rowIndex].count), id: \.self) { _ in
                        Color.clear.frame(width: 80, height: 80)
                    }
                }
            }
        }
    }
    
    private func letterButton(letter: String) -> some View {
        Button(action: { addLetter(letter) }) {
            Text(letter.uppercased())
                .font(.system(size: 28, weight: .bold))
                .foregroundColor(.white)
                .frame(width: 80, height: 80)
                .background(Color(hex: "8B8BAE"))
                .cornerRadius(8)
        }
    }
    
    private func actionButtons() -> some View {
        let grayColor = Color(hex: "6C757D") ?? Color.gray
        let blueColor = Color(hex: "007AFF") ?? Color.blue
        let redColor = Color(hex: "DC3545") ?? Color.red
        
        return HStack(spacing: 16) {
            actionButton(icon: "arrow.clockwise", color: grayColor, action: resetWord)
                .disabled(currentWord.isEmpty)
            
            actionButton(icon: "return", color: blueColor, action: submitWord)
                .disabled(currentWord.count < 3)
            
            actionButton(icon: "delete.left", color: redColor, action: removeLetter)
                .disabled(currentWord.isEmpty)
        }
    }
    
    private func actionButton(icon: String, color: Color, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Image(systemName: icon)
                .foregroundColor(.white)
                .frame(width: 60, height: 60)
                .background(color)
                .cornerRadius(8)
        }
    }
    
    // MARK: - Helper Functions
    private func initializeValidator() {
        // Only initialize once
        guard wordValidator == nil else {
            print("ℹ️ INIT: Validator already assigned")
            return
        }
        
        Task {
            print("🔧 INIT: Starting validator initialization...")
            
            // Get the shared instance
            let validator = LocalWordValidator.shared
            
            // Initialize it (will skip if already initialized)
            await validator.initialize()
            
            // Assign to our state variable on main thread
            await MainActor.run {
                self.wordValidator = validator
                
                let stats = validator.getDictionaryStats()
                print("✅ INIT: Validator ready!")
                print("   - Total words: \(stats.totalWords)")
                print("   - Initialized: \(stats.isInitialized)")
                
                if stats.isInitialized {
                    // Test a few words
                    print("   - Test 'HELLO': \(validator.isValidWord("HELLO"))")
                    print("   - Test 'CAT': \(validator.isValidWord("CAT"))")
                    print("   - Test 'XYZABC': \(validator.isValidWord("XYZABC"))")
                    
                    // Generate hints
                    if let data = puzzleData {
                        hints = validator.getHints(letterSet: data.letterSet, count: 10)
                        print("💡 INIT: Generated \(hints.count) hints for '\(data.letterSet)'")
                        if !hints.isEmpty {
                            print("   - Sample hints: \(hints.prefix(5).joined(separator: ", "))")
                        }
                    }
                } else {
                    print("⚠️ INIT: Validator loaded but not initialized")
                }
            }
        }
    }
    
    private func parsePuzzle() {
        guard let jsonData = puzzle.question.data(using: .utf8) else {
            errorMessage = "Failed to parse puzzle data"
            isLoading = false
            return
        }
        
        do {
            let decoder = JSONDecoder()
            let data = try decoder.decode(LetterSetPuzzle.self, from: jsonData)
            puzzleData = data
            timeRemaining = data.timeLimit
            isLoading = false
            print("✅ Letter set puzzle loaded: \(data.letterSet)")
        } catch {
            errorMessage = "Failed to load puzzle: \(error.localizedDescription)"
            isLoading = false
            print("❌ Parse error: \(error)")
        }
    }
    
    private func addLetter(_ letter: String) {
        currentWord += letter
    }
    
    private func removeLetter() {
        if !currentWord.isEmpty {
            currentWord.removeLast()
        }
    }
    
    private func resetWord() {
        currentWord = ""
    }
    
    private func submitWord() {
        guard let data = puzzleData else {
            print("❌ SUBMIT: No puzzle data available")
            return
        }
        
        guard currentWord.count >= 3 else {
            invalidWordMessage = "Word must be at least 3 letters long"
            showInvalidWordAlert = true
            currentWord = ""
            print("❌ SUBMIT: Word too short: '\(currentWord)'")
            return
        }
        
        let normalizedWord = currentWord.uppercased()
        print("🎯 SUBMIT: Attempting to submit '\(normalizedWord)'")
        
        // Check if already found
        if foundWords.contains(where: { $0.word == normalizedWord }) {
            invalidWordMessage = "You already found '\(normalizedWord)'"
            showInvalidWordAlert = true
            currentWord = ""
            print("❌ SUBMIT: Word already found")
            return
        }
        
        // Create puzzle word map for validator
        let puzzleWordMap = Dictionary(uniqueKeysWithValues: data.allWords.map {
            ($0.word.uppercased(), $0)
        })
        
        print("📚 SUBMIT: Puzzle has \(puzzleWordMap.count) known words")
        print("📚 SUBMIT: Validator initialized: \(wordValidator != nil)")
        
        // Validate with local validator
        if let validator = wordValidator {
            print("✅ SUBMIT: Using LocalWordValidator")
            
            let result = validator.validateWord(
                word: normalizedWord,
                letterSet: data.letterSet,
                usedWords: Set(foundWords.map { $0.word }),
                puzzleWords: puzzleWordMap
            )
            
            print("📊 SUBMIT: Validation result:")
            print("   - Valid: \(result.valid)")
            print("   - Word: \(result.word ?? "nil")")
            print("   - Points: \(result.points)")
            print("   - Source: \(result.source)")
            print("   - Is Discovery: \(result.isNewDiscovery)")
            print("   - Reason: \(result.reason)")
            
            if result.valid, let validWord = result.word {
                // Word is valid!
                let foundWord = FoundWord(
                    word: validWord,
                    points: result.points,
                    isDiscovery: result.isNewDiscovery
                )
                
                foundWords.append(foundWord)
                score += result.points
                
                if result.isNewDiscovery {
                    discoveredWords += 1
                    print("🎉 NEW DISCOVERY: \(validWord) (+\(result.points) pts) from \(result.source)")
                } else {
                    print("✅ FOUND: \(validWord) (+\(result.points) pts) from \(result.source)")
                }
                
                currentWord = ""
                
                // Check for completion
                if foundWords.count >= data.targets.gold {
                    print("🎊 GOLD TARGET REACHED! Completing game...")
                    completeGame()
                }
            } else {
                // Invalid word
                invalidWordMessage = result.reason
                showInvalidWordAlert = true
                currentWord = ""
                print("❌ INVALID: \(normalizedWord) - \(result.reason)")
            }
        } else {
            // Fallback to simple check without validator
            print("⚠️ SUBMIT: Validator not available, using fallback")
            
            if let wordData = data.allWords.first(where: { $0.word.uppercased() == normalizedWord }) {
                let foundWord = FoundWord(word: normalizedWord, points: wordData.points, isDiscovery: false)
                foundWords.append(foundWord)
                score += wordData.points
                currentWord = ""
                
                print("✅ FOUND (fallback): \(normalizedWord) (+\(wordData.points) pts)")
                
                if foundWords.count >= data.targets.gold {
                    completeGame()
                }
            } else {
                invalidWordMessage = "'\(normalizedWord)' is not a valid word"
                showInvalidWordAlert = true
                currentWord = ""
                print("❌ INVALID (fallback): \(normalizedWord) - not in puzzle words")
            }
        }
    }
    
    private func completeGame() {
        guard let data = puzzleData else { return }
        showCelebration = true
        
        let success = foundWords.count >= data.targets.bronze
        let timeBonus = timeRemaining > data.timeLimit / 2
        
        DispatchQueue.main.asyncAfter(deadline: .now() + 2) {
            onComplete(success, timeBonus)
        }
    }
    
    private func progressColor(data: LetterSetPuzzle) -> Color {
        if foundWords.count >= data.targets.gold {
            return Color(hex: "FFD700") ?? Color(red: 1.0, green: 0.84, blue: 0.0)
        } else if foundWords.count >= data.targets.silver {
            return Color(hex: "C0C0C0") ?? Color(red: 0.75, green: 0.75, blue: 0.75)
        } else if foundWords.count >= data.targets.bronze {
            return Color(hex: "CD7F32") ?? Color(red: 0.80, green: 0.50, blue: 0.20)
        } else {
            return Color(hex: "2196F3") ?? Color(red: 0.13, green: 0.59, blue: 0.95)
        }
    }
    
    private func formatTime(_ seconds: Int) -> String {
        let minutes = seconds / 60
        let secs = seconds % 60
        return String(format: "%d:%02d", minutes, secs)
    }
    
    private func getEarlyFinishMessage(data: LetterSetPuzzle) -> String {
        var message = "You've found \(foundWords.count) words so far!\n"
        
        if foundWords.count >= data.targets.gold {
            message += "🥇 Gold level achieved! Amazing work!"
        } else if foundWords.count >= data.targets.silver {
            message += "🥈 Silver level achieved! Going for gold?"
        } else if foundWords.count >= data.targets.bronze {
            message += "🥉 Bronze level achieved! Want to continue?"
        }
        
        if timeRemaining > 60 {
            message += "\n\nYou still have \(formatTime(timeRemaining)) remaining!"
        }
        
        return message
    }
}

// MARK: - Celebration Overlay
struct CelebrationOverlay: View {
    let score: Int
    let wordsFound: Int
    let discoveredWords: Int
    let timeBonus: Bool
    let targets: LetterSetTargets
    
    var body: some View {
        ZStack {
            Color.black.opacity(0.8)
                .ignoresSafeArea()
            
            VStack(spacing: 16) {
                Text(medal)
                    .font(.system(size: 48))
                
                Text(title)
                    .font(.system(size: 24, weight: .bold))
                    .foregroundColor(Color(hex: "4CAF50") ?? Color(red: 0.30, green: 0.69, blue: 0.31))
                
                Text(message)
                    .font(.system(size: 16))
                    .multilineTextAlignment(.center)
                
                Text("Found \(wordsFound) words!")
                    .font(.system(size: 16, weight: .bold))
                
                if discoveredWords > 0 {
                    Text("🎉 Including \(discoveredWords) new discoveries!")
                        .font(.system(size: 14, weight: .bold))
                        .foregroundColor(Color(hex: "FFD700") ?? Color(red: 1.0, green: 0.84, blue: 0.0))
                }
                
                if timeBonus {
                    Text("⚡ Time Bonus Earned!")
                        .font(.system(size: 14, weight: .bold))
                        .foregroundColor(Color(hex: "2196F3") ?? Color(red: 0.13, green: 0.59, blue: 0.95))
                }
                
                Text("Final Score: \(score)")
                    .font(.system(size: 20, weight: .bold))
                    .foregroundColor(Color(hex: "333333") ?? Color(red: 0.20, green: 0.20, blue: 0.20))
            }
            .padding(32)
            .background(Color.white)
            .cornerRadius(20)
            .shadow(radius: 16)
        }
    }
    
    private var medal: String {
        if wordsFound >= targets.gold {
            return "🥇"
        } else if wordsFound >= targets.silver {
            return "🥈"
        } else if wordsFound >= targets.bronze {
            return "🥉"
        } else {
            return "🎯"
        }
    }
    
    private var title: String {
        if wordsFound >= targets.gold {
            return "GOLD ACHIEVED!"
        } else if wordsFound >= targets.silver {
            return "SILVER ACHIEVED!"
        } else if wordsFound >= targets.bronze {
            return "BRONZE ACHIEVED!"
        } else {
            return "TIME'S UP!"
        }
    }
    
    private var message: String {
        if wordsFound >= targets.gold {
            return "Outstanding performance!"
        } else if wordsFound >= targets.silver {
            return "Excellent work!"
        } else if wordsFound >= targets.bronze {
            return "Great job!"
        } else {
            return "Good effort!"
        }
    }
}

// MARK: - Helper Extensions
// Note: Array.chunked extension removed as it already exists in your project
