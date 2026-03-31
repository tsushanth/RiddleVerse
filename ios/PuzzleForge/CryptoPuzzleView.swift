//
//  CryptoPuzzleView.swift
//  PuzzleForge
//
//  iOS implementation of Crypto Word puzzle with number-letter mapping
//

import SwiftUI
import Foundation

extension Puzzle {
    /// Parsed crypto puzzle data
    var cryptoPuzzleData: CryptoPuzzleData? {
        guard puzzleType?.lowercased().contains("crypto") == true else {
            return nil
        }
        
        return CryptoPuzzleData(from: question)
    }
}

// MARK: - Updated Data Models (matches Android CryptoPuzzleConfig)
struct CryptoPuzzleData {
    let originalText: String
    let numberMapping: [Character: Int] // letter to number mapping
    let revealedLetters: Set<Character> // letters that are shown initially
    let hiddenLetters: Set<Character> // letters that are encoded with numbers
    let frequencyMap: [Character: Int] // frequency analysis (for debugging)
    let targetWord: String? // optional target word for hints
    let difficulty: String
    let timeLimit: Int
    let instructions: String
    
    // Updated initializer to match Android CryptoPuzzleConfig structure
    init?(from jsonString: String) {
        print("🔐 CRYPTO: Attempting to parse crypto data from: \(jsonString.prefix(100))...")
        
        guard let data = jsonString.data(using: .utf8),
              let json = try? JSONSerialization.jsonObject(with: data) as? [String: Any] else {
            print("❌ CRYPTO: Failed to parse JSON from string")
            return nil
        }
        
        print("🔐 CRYPTO: JSON keys found: \(json.keys)")
        
        guard let originalText = json["originalText"] as? String,
              let numberMappingDict = json["numberMapping"] as? [String: Int] else {
            print("❌ CRYPTO: Missing required fields - originalText or numberMapping")
            print("🔐 CRYPTO: originalText type: \(type(of: json["originalText"]))")
            print("🔐 CRYPTO: numberMapping type: \(type(of: json["numberMapping"]))")
            return nil
        }
        
        self.originalText = originalText
        print("✅ CRYPTO: Original text: \(originalText)")
        
        // Convert string keys to Character keys for number mapping
        var charMapping: [Character: Int] = [:]
        for (key, value) in numberMappingDict {
            if let char = key.first {
                charMapping[char] = value
            }
        }
        self.numberMapping = charMapping
        print("✅ CRYPTO: Number mapping created with \(charMapping.count) entries")
        
        // Parse revealed letters
        if let revealedArray = json["revealedLetters"] as? [String] {
            self.revealedLetters = Set(revealedArray.compactMap { $0.first })
            print("✅ CRYPTO: Revealed letters: \(self.revealedLetters)")
        } else {
            self.revealedLetters = Set()
            print("⚠️ CRYPTO: No revealed letters found")
        }
        
        // Parse hidden letters (new field from Android preprocessor)
        if let hiddenArray = json["hiddenLetters"] as? [String] {
            self.hiddenLetters = Set(hiddenArray.compactMap { $0.first })
            print("✅ CRYPTO: Hidden letters: \(self.hiddenLetters)")
        } else {
            // Fallback: hidden letters are those with number mappings
            self.hiddenLetters = Set(charMapping.keys)
            print("⚠️ CRYPTO: Hidden letters derived from number mapping")
        }
        
        // Create frequency map for analysis (matches Android frequencyMap)
        var freqMap: [Character: Int] = [:]
        for char in originalText {
            if char.isLetter {
                let upperChar = char.uppercased().first!
                freqMap[upperChar] = (freqMap[upperChar] ?? 0) + 1
            }
        }
        self.frequencyMap = freqMap
        
        self.targetWord = json["targetWord"] as? String
        self.difficulty = json["difficulty"] as? String ?? "Medium"
        self.timeLimit = json["timeLimit"] as? Int ?? 300
        self.instructions = json["instructions"] as? String ?? "Decode the hidden message by figuring out which number represents which letter."
        
        print("✅ CRYPTO: Successfully initialized crypto puzzle data")
        print("📊 CRYPTO: Target word: \(self.targetWord ?? "none")")
        print("📊 CRYPTO: Time limit: \(self.timeLimit)")
        print("📊 CRYPTO: Difficulty: \(self.difficulty)")
        print("📊 CRYPTO: Hidden: \(self.hiddenLetters.count)/\(self.frequencyMap.count) letters")
        print("📊 CRYPTO: Revealed hints: \(self.revealedLetters.count)")
    }
}

struct CryptoCell {
    let letter: Character
    let number: Int?
    let isRevealed: Bool
    let isCrypto: Bool // true if this letter is encoded
}

// MARK: - Main Crypto Puzzle View
//
//  CryptoPuzzleView.swift
//  PuzzleForge
//
//  iOS implementation of Adaptive Crypto Word puzzle with number-letter mapping
//

import SwiftUI
import Foundation

// MARK: - Adaptive Configuration
struct AdaptiveCryptoConfig {
    let autoRevealAllInstances: Bool // Whether to reveal all instances of a letter when one is solved
    let lockedPositionsRatio: Float // Ratio of positions that remain locked until dependencies are met
    let dependencyChainLength: Int // How many adjacent letters must be revealed to unlock a position
    let hintRevealCount: Int // Number of initial hints given
    let name: String
    let description: String
}

// MARK: - Enhanced Data Models
struct AdaptiveCryptoPuzzleData {
    let originalText: String
    let numberMapping: [Character: Int] // letter to number mapping
    let revealedLetters: Set<Character> // letters that are shown initially
    let targetWord: String? // optional target word for hints
    let lockedPositions: Set<Int> // Position indices that are locked
    let positionDependencies: [Int: Set<Int>] // Position -> required positions to unlock
    let difficulty: String
    let timeLimit: Int
    let instructions: String
    
    init?(from jsonString: String, adaptiveConfig: AdaptiveCryptoConfig) {
        print("🔍 CRYPTO: Attempting to parse adaptive crypto data from: \(jsonString.prefix(100))...")
        
        guard let data = jsonString.data(using: .utf8),
              let json = try? JSONSerialization.jsonObject(with: data) as? [String: Any] else {
            print("❌ CRYPTO: Failed to parse JSON from string")
            return nil
        }
        
        guard let originalText = json["originalText"] as? String,
              let numberMappingDict = json["numberMapping"] as? [String: Int] else {
            print("❌ CRYPTO: Missing required fields - originalText or numberMapping")
            return nil
        }
        
        self.originalText = originalText
        
        // Convert string keys to Character keys for number mapping
        var charMapping: [Character: Int] = [:]
        for (key, value) in numberMappingDict {
            if let char = key.first {
                charMapping[char] = value
            }
        }
        self.numberMapping = charMapping
        
        // Parse revealed letters
        if let revealedArray = json["revealedLetters"] as? [String] {
            self.revealedLetters = Set(revealedArray.compactMap { $0.first })
        } else {
            self.revealedLetters = Set()
        }
        
        self.targetWord = json["targetWord"] as? String
        self.difficulty = json["difficulty"] as? String ?? "Medium"
        self.timeLimit = json["timeLimit"] as? Int ?? 300
        self.instructions = json["instructions"] as? String ?? "Decode the hidden message by figuring out which number represents which letter."
        
        // Generate adaptive locked positions and dependencies
        let (lockedPos, dependencies) = Self.generateAdaptiveConstraints(
            originalText: originalText,
            numberMapping: charMapping,
            config: adaptiveConfig
        )
        
        self.lockedPositions = lockedPos
        self.positionDependencies = dependencies
        
        print("✅ CRYPTO: Successfully initialized adaptive crypto puzzle data")
        print("📊 CRYPTO: Adaptive mode: \(adaptiveConfig.name)")
        print("📊 CRYPTO: Auto-reveal: \(adaptiveConfig.autoRevealAllInstances)")
        print("📊 CRYPTO: Locked positions: \(lockedPos.count)")
        print("📊 CRYPTO: Dependencies: \(dependencies.count)")
    }
    
    // Generate locked positions and dependencies based on adaptive config
    static func generateAdaptiveConstraints(
        originalText: String,
        numberMapping: [Character: Int],
        config: AdaptiveCryptoConfig
    ) -> (Set<Int>, [Int: Set<Int>]) {
        
        var positions: [Int] = []
        var currentPos = 0
        
        // Map character positions
        for char in originalText {
            if char.isLetter && numberMapping[char.uppercased().first!] != nil {
                positions.append(currentPos)
            }
            if char.isLetter || char.isWhitespace {
                currentPos += 1
            }
        }
        
        // Select positions to lock based on configuration
        let totalPositions = positions.count
        let numToLock = Int(Float(totalPositions) * config.lockedPositionsRatio)
        let lockedPositions: Set<Int> = numToLock > 0 ?
            Set(positions.shuffled().prefix(numToLock)) :
            Set()
        
        // Create dependencies - each locked position depends on nearby revealed positions
        var positionDependencies: [Int: Set<Int>] = [:]
        for lockedPos in lockedPositions {
            let dependencies = Set(positions.filter { pos in
                pos != lockedPos &&
                abs(pos - lockedPos) <= config.dependencyChainLength &&
                !lockedPositions.contains(pos)
            }.prefix(config.dependencyChainLength))
            
            if !dependencies.isEmpty {
                positionDependencies[lockedPos] = dependencies
            }
        }
        
        return (lockedPositions, positionDependencies)
    }
}


// MARK: - Main Adaptive Crypto Puzzle View
struct CryptoPuzzleView: View {
    let puzzle: Puzzle
    let questionIndex: Int
    let totalQuestions: Int
    let onAnswerSubmitted: (Bool) -> Void
    let onNextPuzzle: () -> Void
    let onExit: () -> Void
    
    // Adaptive difficulty state
    @State private var currentDifficultyLevel: DifficultyLevel
    @State private var adaptationInfo: AdaptationInfo?
    @State private var showAdaptationNotification = false
    @State private var competitiveInsight: CompetitiveInsight?
    
    // Game state
    @State private var cryptoPuzzleData: AdaptiveCryptoPuzzleData?
    @State private var userMapping: [Int: Character] = [:]
    @State private var selectedNumber: Int? = nil
    @State private var isCompleted = false
    @State private var showHint = false
    @State private var showCompletionDialog = false
    @State private var finalScore = 0
    @State private var currentHearts: Int
    @State private var showWrongFeedback = false
    @State private var gameOver = false
    @State private var timeRemaining: Int
    @State private var timer: Timer?
    @State private var isViewActive = true
    @State private var showTutorialDialog = false
    @State private var currentStreak = 0
    @State private var gamesPlayedThisSession = 0
    @State private var startTime = Date()
    @State private var sessionStartTime = Date()
    
    // Adaptive state
    @State private var currentlyLockedPositions: Set<Int> = Set()
    @State private var correctAnswers = 0
    @State private var totalAnswers = 0
    
    // Adaptive configuration
    private var adaptiveConfig: AdaptiveCryptoConfig {
        generateAdaptiveCryptoConfig(currentDifficultyLevel)
    }
    
    private let maxHearts = 3
    
    init(puzzle: Puzzle, questionIndex: Int, totalQuestions: Int, onAnswerSubmitted: @escaping (Bool) -> Void, onNextPuzzle: @escaping () -> Void, onExit: @escaping () -> Void) {
        self.puzzle = puzzle
        self.questionIndex = questionIndex
        self.totalQuestions = totalQuestions
        self.onAnswerSubmitted = onAnswerSubmitted
        self.onNextPuzzle = onNextPuzzle
        self.onExit = onExit
        
        // Initialize difficulty level - extract from puzzle or default to Medium
        let difficultyString = puzzle.difficulty ?? "Medium"
        let difficultyLevel = Self.getDifficultyLevel(for: difficultyString)
        self._currentDifficultyLevel = State(initialValue: difficultyLevel)
        self._currentHearts = State(initialValue: difficultyLevel.livesAllowed)
        self._timeRemaining = State(initialValue: difficultyLevel.timeLimit)
    }
    
    var body: some View {
        ZStack {
            // Background
            LinearGradient(
                colors: [Color(red: 0.05, green: 0.05, blue: 0.15), Color(red: 0.1, green: 0.1, blue: 0.2)],
                startPoint: .topLeading,
                endPoint: .bottomTrailing
            )
            .ignoresSafeArea()
            
            VStack(spacing: 0) {
                // Adaptive Header
                adaptiveHeaderView
                
                // Adaptation Notification
                if showAdaptationNotification {
                    adaptationNotificationView
                        .padding(.horizontal, 16)
                        .transition(.opacity.combined(with: .scale))
                }
                
                // Wrong feedback
                if showWrongFeedback {
                    wrongFeedbackView
                        .padding(.horizontal, 16)
                        .transition(.opacity)
                }
                
                // Success message
                if isCompleted {
                    successMessageView
                        .padding(.horizontal, 16)
                }
                
                // Main puzzle area
                if let data = cryptoPuzzleData {
                    VStack(spacing: 0) {
                        // Progress view
                        adaptiveProgressView(data: data)
                            .padding(.horizontal, 16)
                            .padding(.top, 8)
                        
                        // Crypto puzzle display
                        adaptiveCryptoPuzzleDisplayView(data: data)
                            .padding(.horizontal, 16)
                            .padding(.vertical, 8)
                    }
                } else {
                    // Error state
                    errorStateView
                }
                
                // Adaptive keyboard
                if let data = cryptoPuzzleData {
                    adaptiveLetterSelectionKeyboard(data: data)
                }
            }
        }
        .navigationBarHidden(true)
        .onAppear {
            setupAdaptivePuzzle()
            startTimer()
        }
        .onDisappear {
            cleanup()
        }
        .sheet(isPresented: $showTutorialDialog) {
            AdaptiveCryptoTutorialDialog(
                instructions: cryptoPuzzleData?.instructions ?? "Decode the message by figuring out which number represents which letter.",
                targetWord: cryptoPuzzleData?.targetWord,
                adaptiveConfig: adaptiveConfig,
                onDismiss: { showTutorialDialog = false }
            )
        }
        .sheet(isPresented: $showCompletionDialog) {
            AdaptiveCryptoCompletionDialog(
                originalText: cryptoPuzzleData?.originalText ?? "",
                finalScore: finalScore,
                timeUsed: (cryptoPuzzleData?.timeLimit ?? 300) - timeRemaining,
                difficulty: adaptiveConfig.name,
                adaptiveConfig: adaptiveConfig,
                attribution: puzzle.answer,
                onContinue: {
                    showCompletionDialog = false
                    onNextPuzzle()
                },
                onDismiss: { showCompletionDialog = false }
            )
        }
        .alert("Game Over", isPresented: $gameOver) {
            Button("Try Again") { resetGame() }
            Button("Exit") { onExit() }
        } message: {
            Text("You've run out of hearts! Crypto puzzles take practice.")
        }
        .alert("💡 Adaptive Hint", isPresented: $showHint) {
            Button("OK") { showHint = false }
        } message: {
            if let targetWord = cryptoPuzzleData?.targetWord {
                Text("The hidden message contains: \(targetWord)")
            } else {
                Text("Look for common patterns like 'THE', 'AND', or single letters like 'A' and 'I'.")
            }
        }
    }
    
    // MARK: - Adaptive Header View
    private var adaptiveHeaderView: some View {
        HStack {
            // Back button
            Button(action: onExit) {
                Image(systemName: "arrow.left")
                    .font(.title2)
                    .foregroundColor(.white)
                    .frame(width: 40, height: 40)
            }
            
            // Level and difficulty info
            VStack(alignment: .leading) {
                Text("🔒 Crypto")
                    .font(.headline)
                    .fontWeight(.bold)
                    .foregroundColor(.white)
                
                Text(adaptiveConfig.name)
                    .font(.caption)
                    .foregroundColor(adaptiveConfig.autoRevealAllInstances ? .green : .orange)
            }
            
            Spacer()
            
            // Competitive insight
            if let insight = competitiveInsight {
                competitiveInsightView(insight)
                    .padding(.trailing, 8)
            }
            
            // Timer and hearts
            VStack(spacing: 4) {
                Text(formatTime(timeRemaining))
                    .font(.system(size: 16, weight: .bold))
                    .foregroundColor(timeRemaining <= 30 ? .red : .white)
                    .padding(.horizontal, 8)
                    .padding(.vertical, 4)
                    .background(Color.black.opacity(0.3))
                    .cornerRadius(6)
                
                HStack(spacing: 2) {
                    ForEach(0..<maxHearts, id: \.self) { index in
                        Image(systemName: index < currentHearts ? "heart.fill" : "heart")
                            .font(.system(size: 12))
                            .foregroundColor(index < currentHearts ? .red : .gray)
                    }
                }
                
                if currentStreak > 0 {
                    Text("🔥 \(currentStreak)")
                        .font(.caption)
                        .fontWeight(.bold)
                        .foregroundColor(.orange)
                }
            }
            
            // Help button
            Button(action: { showTutorialDialog = true }) {
                Image(systemName: "questionmark.circle.fill")
                    .font(.title2)
                    .foregroundColor(.white)
                    .frame(width: 40, height: 40)
            }
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 8)
        .background(Color.black.opacity(0.2))
    }
    
    // MARK: - Adaptive Progress View
    private func adaptiveProgressView(data: AdaptiveCryptoPuzzleData) -> some View {
        let cryptoLetters = data.numberMapping.keys
        let decodedLetters = cryptoLetters.filter { letter in
            if let number = data.numberMapping[letter] {
                return userMapping[number] == letter
            }
            return false
        }
        
        return VStack(spacing: 6) {
            HStack {
                Text("Decoded: \(decodedLetters.count)/\(cryptoLetters.count)")
                    .font(.caption)
                    .fontWeight(.semibold)
                    .foregroundColor(.white)
                
                Spacer()
                
                if !adaptiveConfig.autoRevealAllInstances {
                    Text("🎯 Single Reveal Mode")
                        .font(.caption)
                        .foregroundColor(.orange)
                } else if adaptiveConfig.lockedPositionsRatio > 0 {
                    Text("🔒 \(currentlyLockedPositions.count) Locked")
                        .font(.caption)
                        .foregroundColor(.yellow)
                }
            }
            
            ProgressView(value: Double(decodedLetters.count), total: Double(cryptoLetters.count))
                .progressViewStyle(LinearProgressViewStyle(tint: .green))
                .frame(height: 4)
                .background(Color.white.opacity(0.2))
                .cornerRadius(2)
        }
    }
    
    // MARK: - Adaptive Crypto Puzzle Display
    private func adaptiveCryptoPuzzleDisplayView(data: AdaptiveCryptoPuzzleData) -> some View {
        let words = data.originalText.split(separator: " ").map { String($0) }
        
        return GeometryReader { geometry in
            let availableWidth = geometry.size.width - 32
            let wordRows = createDynamicWordRows(words: words, availableWidth: availableWidth)
            
            ScrollView {
                VStack(spacing: 16) {
                    ForEach(Array(wordRows.enumerated()), id: \.offset) { rowIndex, wordsInRow in
                        adaptiveWordRowView(words: wordsInRow, data: data, startPosition: calculateStartPosition(words: words, rowIndex: rowIndex, wordRows: wordRows))
                    }
                }
                .padding(.horizontal, 16)
                .padding(.vertical, 12)
            }
        }
        .background(Color.white.opacity(0.05))
        .cornerRadius(12)
    }
    
    private func adaptiveWordRowView(words: [String], data: AdaptiveCryptoPuzzleData, startPosition: Int) -> some View {
        VStack(spacing: 12) {
            // Letters row
            HStack(spacing: 8) {
                var currentPos = startPosition
                ForEach(Array(words.enumerated()), id: \.offset) { wordIndex, word in
                    HStack(spacing: 4) {
                        if wordIndex > 0 {
                            Spacer().frame(width: 16)
                            let _ = (currentPos += 1) // Account for space
                        }
                        
                        ForEach(Array(word.enumerated()), id: \.offset) { charIndex, char in
                            let cellView = adaptiveCryptoLetterCell(char: char, position: currentPos, data: data)
                            let _ = (currentPos += 1)
                            cellView
                        }
                    }
                }
                Spacer()
            }
            
            // Numbers row
            HStack(spacing: 8) {
                var currentPos = startPosition
                ForEach(Array(words.enumerated()), id: \.offset) { wordIndex, word in
                    HStack(spacing: 4) {
                        if wordIndex > 0 {
                            Spacer().frame(width: 16)
                            let _ = (currentPos += 1)
                        }
                        
                        ForEach(Array(word.enumerated()), id: \.offset) { charIndex, char in
                            let cellView = adaptiveCryptoNumberCell(char: char, position: currentPos, data: data)
                            let _ = (currentPos += 1)
                            cellView
                        }
                    }
                }
                Spacer()
            }
        }
    }
    
    private func adaptiveCryptoLetterCell(char: Character, position: Int, data: AdaptiveCryptoPuzzleData) -> some View {
        let upperChar = char.uppercased().first!
        let isCryptoLetter = data.numberMapping[upperChar] != nil
        let isLocked = currentlyLockedPositions.contains(position)
        let isRevealed = data.revealedLetters.contains(upperChar)
        let userLetter = data.numberMapping[upperChar].flatMap { userMapping[$0] }
        
        let displayText: String = {
            if !isCryptoLetter {
                return String(upperChar)
            } else if isLocked {
                return "🔒"
            } else if isRevealed {
                return String(upperChar)
            } else if let userLetter = userLetter {
                return String(userLetter)
            } else {
                return "_"
            }
        }()
        
        return Text(displayText)
            .font(.system(size: 20, weight: .bold))
            .foregroundColor(getAdaptiveLetterColor(char: upperChar, position: position, data: data))
            .frame(width: 28, height: 28)
            .background(getAdaptiveLetterBackground(char: upperChar, position: position, data: data))
            .cornerRadius(6)
    }
    
    private func adaptiveCryptoNumberCell(char: Character, position: Int, data: AdaptiveCryptoPuzzleData) -> some View {
        let upperChar = char.uppercased().first!
        let isCryptoLetter = data.numberMapping[upperChar] != nil
        let isLocked = currentlyLockedPositions.contains(position)
        
        if isCryptoLetter && !isLocked {
            if let number = data.numberMapping[upperChar] {
                return AnyView(
                    Button(action: {
                        selectedNumber = selectedNumber == number ? nil : number
                    }) {
                        Text("\(number)")
                            .font(.system(size: 14, weight: .semibold))
                            .foregroundColor(selectedNumber == number ? .white : .black)
                            .frame(width: 28, height: 22)
                            .background(
                                RoundedRectangle(cornerRadius: 6)
                                    .fill(selectedNumber == number ? Color.blue : Color.white)
                                    .overlay(
                                        RoundedRectangle(cornerRadius: 6)
                                            .stroke(selectedNumber == number ? Color.blue : Color.gray, lineWidth: 1)
                                    )
                            )
                    }
                    .buttonStyle(PlainButtonStyle())
                )
            }
        } else if isLocked {
            return AnyView(
                Image(systemName: "lock.fill")
                    .font(.system(size: 12))
                    .foregroundColor(.orange)
                    .frame(width: 28, height: 22)
            )
        }
        
        return AnyView(
            Spacer()
                .frame(width: 28, height: 22)
        )
    }
    
    // MARK: - Adaptive Letter Selection Keyboard
    private func adaptiveLetterSelectionKeyboard(data: AdaptiveCryptoPuzzleData) -> some View {
        VStack(spacing: 8) {
            // Instructions
            VStack(spacing: 4) {
                Text(selectedNumber != nil ? "Choose letter for \(selectedNumber!)" : "Select a number first")
                    .font(.caption)
                    .foregroundColor(.white.opacity(0.8))
                
                if !adaptiveConfig.autoRevealAllInstances {
                    Text("🎯 Advanced: Only reveals single instances")
                        .font(.caption2)
                        .foregroundColor(.orange)
                }
            }
            .padding(.top, 8)
            
            // Keyboard rows
            let keyboardRows = ["QWERTYUIOP", "ASDFGHJKL", "ZXCVBNM"]
            
            ForEach(keyboardRows, id: \.self) { row in
                HStack(spacing: 3) {
                    ForEach(Array(row), id: \.self) { letter in
                        adaptiveKeyboardButton(letter: letter, data: data)
                    }
                }
            }
            
            // Clear button
            Button(action: clearCurrentMapping) {
                HStack(spacing: 4) {
                    Image(systemName: "trash")
                        .font(.system(size: 14))
                    Text("Clear")
                        .font(.system(size: 14, weight: .semibold))
                }
                .foregroundColor(.white)
                .padding(.horizontal, 20)
                .padding(.vertical, 10)
                .background(Color.red.opacity(0.7))
                .cornerRadius(8)
            }
            .disabled(selectedNumber == nil)
            .padding(.bottom, 8)
        }
        .padding(.horizontal, 8)
        .background(Color.black.opacity(0.3))
    }
    
    private func adaptiveKeyboardButton(letter: Character, data: AdaptiveCryptoPuzzleData) -> some View {
        let isUsed = if adaptiveConfig.autoRevealAllInstances {
            userMapping.values.contains(letter)
        } else {
            selectedNumber.map { userMapping[$0] == letter } ?? false
        }
        let isCorrect = selectedNumber != nil && data.numberMapping[letter] == selectedNumber
        let isRevealed = data.revealedLetters.contains(letter)
        
        return Button(action: {
            handleAdaptiveLetterSelection(letter, data: data)
        }) {
            Text(String(letter))
                .font(.system(size: 16, weight: .semibold))
                .foregroundColor(getAdaptiveKeyboardTextColor(letter: letter, data: data))
                .frame(maxWidth: .infinity, minHeight: 44)
                .background(getAdaptiveKeyboardBackground(letter: letter, data: data))
                .cornerRadius(6)
        }
        .buttonStyle(PlainButtonStyle())
        .disabled(selectedNumber == nil || (isUsed && !isCorrect) || isRevealed)
    }
    
    // MARK: - Supporting Views
    private var adaptationNotificationView: some View {
        VStack(spacing: 8) {
            HStack {
                Image(systemName: "brain.head.profile")
                    .foregroundColor(.blue)
                Text("Difficulty Adapted!")
                    .font(.headline)
                    .fontWeight(.bold)
                    .foregroundColor(.blue)
                Spacer()
                Button("✕") {
                    showAdaptationNotification = false
                }
                .foregroundColor(.gray)
            }
            
            if let info = adaptationInfo {
                Text("Adjusted to \(info.level.name) - \(adaptiveConfig.description)")
                    .font(.caption)
                    .foregroundColor(.gray)
            }
        }
        .padding()
        .background(Color.white)
        .cornerRadius(12)
        .shadow(radius: 4)
    }
    
    private var wrongFeedbackView: some View {
        HStack {
            Image(systemName: "xmark.circle.fill")
                .foregroundColor(.red)
            Text("❌ Wrong letter! Try again.")
                .font(.subheadline)
                .fontWeight(.semibold)
                .foregroundColor(.red)
        }
        .padding()
        .background(Color.red.opacity(0.1))
        .cornerRadius(8)
    }
    
    private var successMessageView: some View {
        HStack {
            Image(systemName: "checkmark.circle.fill")
                .foregroundColor(.green)
            Text("🎉 Adaptive Puzzle Solved! Score: \(finalScore)")
                .font(.subheadline)
                .fontWeight(.semibold)
                .foregroundColor(.green)
        }
        .padding()
        .background(Color.green.opacity(0.1))
        .cornerRadius(8)
    }
    
    private var errorStateView: some View {
        VStack(spacing: 16) {
            Image(systemName: "exclamationmark.triangle")
                .font(.system(size: 40))
                .foregroundColor(.red)
            
            Text("Unable to load adaptive crypto puzzle")
                .font(.headline)
                .foregroundColor(.white)
            
            Button("Go Back") { onExit() }
                .font(.headline)
                .foregroundColor(.white)
                .padding(.horizontal, 24)
                .padding(.vertical, 12)
                .background(Color.blue)
                .cornerRadius(8)
        }
        .frame(maxHeight: .infinity)
    }
    
    private func competitiveInsightView(_ insight: CompetitiveInsight) -> some View {
        HStack {
            Image(systemName: "trophy.fill")
                .foregroundColor(.yellow)
                .font(.caption)
            
            Text("💪 You're performing better than \(insight.percentile)% of players!")
                .font(.caption)
                .foregroundColor(.white.opacity(0.9))
        }
        .padding(.horizontal, 8)
        .padding(.vertical, 4)
        .background(Color.white.opacity(0.1))
        .cornerRadius(6)
    }
    
    // MARK: - Game Logic
    private func setupAdaptivePuzzle() {
        print("🔍 CRYPTO: Setting up adaptive puzzle")
        
        let config = adaptiveConfig
        if let data = AdaptiveCryptoPuzzleData(from: puzzle.question, adaptiveConfig: config) {
            cryptoPuzzleData = data
            timeRemaining = data.timeLimit
            currentlyLockedPositions = data.lockedPositions
            
            // Initialize revealed letters in user mapping
            for letter in data.revealedLetters {
                if let number = data.numberMapping[letter] {
                    userMapping[number] = letter
                }
            }
            
            startTime = Date()
            sessionStartTime = Date()
            
            print("✅ CRYPTO: Adaptive puzzle setup complete")
            print("📊 CRYPTO: Config: \(config.name)")
            print("📊 CRYPTO: Auto-reveal: \(config.autoRevealAllInstances)")
            print("📊 CRYPTO: Locked positions: \(currentlyLockedPositions.count)")
            
            // Load competitive insight
            loadCompetitiveInsight()
        } else {
            print("❌ CRYPTO: Failed to parse adaptive crypto puzzle data")
            DispatchQueue.main.asyncAfter(deadline: .now() + 1.0) {
                onExit()
            }
        }
    }
    
    private func handleAdaptiveLetterSelection(_ letter: Character, data: AdaptiveCryptoPuzzleData) {
        guard let selectedNum = selectedNumber else { return }
        
        totalAnswers += 1
        let correct = placeLetter(selectedNum, letter, data: data)
        
        if correct {
            selectedNumber = nil
            currentStreak += 1
            correctAnswers += 1
            
            // Check for completion
            checkPuzzleCompletion(data: data)
            
            // Check if any locked positions can be unlocked
            checkAndUnlockPositions(data: data)
            
        } else {
            showWrongFeedback = true
            currentHearts = max(0, currentHearts - 1)
            currentStreak = 0
            selectedNumber = nil
            
            // Record performance for adaptation
            recordAdaptivePerformance(
                isCorrect: false,
                timeSpent: Date().timeIntervalSince(startTime),
                streak: 0,
                livesRemaining: currentHearts
            )
            
            DispatchQueue.main.asyncAfter(deadline: .now() + 1.5) {
                showWrongFeedback = false
            }
            
            if currentHearts <= 0 {
                gameOver = true
            }
        }
    }
    
    private func placeLetter(_ number: Int, _ letter: Character, data: AdaptiveCryptoPuzzleData) -> Bool {
        let correctLetter = data.numberMapping.first(where: { $0.value == number })?.key
        
        if correctLetter == letter {
            if adaptiveConfig.autoRevealAllInstances {
                // Easy mode: reveal all instances of this letter
                userMapping = userMapping.filter { $0.value != letter }
                userMapping[number] = letter
            } else {
                // Advanced mode: only reveal this specific instance
                userMapping[number] = letter
            }
            return true
        }
        return false
    }
    
    private func checkAndUnlockPositions(data: AdaptiveCryptoPuzzleData) {
        let positionsToCheck = Array(currentlyLockedPositions)
        
        for position in positionsToCheck {
            if let dependencies = data.positionDependencies[position] {
                let allDependenciesMet = dependencies.allSatisfy { depPos in
                    let charAtPos = getCharacterAtPosition(data.originalText, position: depPos)
                    if let number = data.numberMapping[charAtPos] {
                        return userMapping[number] != nil
                    }
                    return false
                }
                
                if allDependenciesMet {
                    currentlyLockedPositions.remove(position)
                    print("🔓 Position \(position) unlocked! Dependencies met.")
                }
            }
        }
    }
    
    private func checkPuzzleCompletion(data: AdaptiveCryptoPuzzleData) {
        let cryptoLetters = data.numberMapping.keys
        let allDecoded = cryptoLetters.allSatisfy { letter in
            if let number = data.numberMapping[letter] {
                return userMapping[number] == letter
            }
            return false
        }
        
        if allDecoded && !isCompleted {
            isCompleted = true
            timer?.invalidate()
            
            let timeSpent = Date().timeIntervalSince(startTime)
            currentStreak += 1
            gamesPlayedThisSession += 1
            correctAnswers += 1
            
            // Calculate adaptive score
            finalScore = calculateAdaptiveScore(
                timeSpent: timeSpent,
                difficulty: currentDifficultyLevel,
                livesRemaining: currentHearts,
                streak: currentStreak,
                puzzleComplexity: data.numberMapping.count,
                adaptiveConfig: adaptiveConfig
            )
            
            recordAdaptivePerformance(
                isCorrect: true,
                timeSpent: timeSpent,
                streak: currentStreak,
                livesRemaining: currentHearts
            )
            
            DispatchQueue.main.asyncAfter(deadline: .now() + 1.0) {
                showCompletionDialog = true
                onAnswerSubmitted(true)
            }
        }
    }
    
    private func calculateAdaptiveScore(
        timeSpent: TimeInterval,
        difficulty: DifficultyLevel,
        livesRemaining: Int,
        streak: Int,
        puzzleComplexity: Int,
        adaptiveConfig: AdaptiveCryptoConfig
    ) -> Int {
        let baseScore = difficulty.basePoints
        
        // Adaptive difficulty bonus
        let adaptiveMultiplier: Float = {
            if !adaptiveConfig.autoRevealAllInstances {
                return 2.0 // Major bonus for single-reveal mode
            } else if adaptiveConfig.lockedPositionsRatio > 0.2 {
                return 1.5 // Bonus for significant locking
            } else if adaptiveConfig.lockedPositionsRatio > 0.1 {
                return 1.3 // Medium bonus
            } else {
                return 1.0
            }
        }()
        
        // Time bonus
        let timeBonus = max(0, Int(300 - timeSpent)) * 2
        
        // Lives bonus
        let livesBonus = livesRemaining * 50
        
        // Streak bonus
        let streakBonus = streak * 25
        
        // Complexity bonus
        let complexityBonus = puzzleComplexity * 10
        
        let finalScore = Int(Float(baseScore) * adaptiveMultiplier) +
                        timeBonus + livesBonus + streakBonus + complexityBonus
        
        return max(finalScore, baseScore / 2)
    }
    
    private func recordAdaptivePerformance(isCorrect: Bool, timeSpent: TimeInterval, streak: Int, livesRemaining: Int) {
        // Simulate adaptive performance recording
        let performanceScore = calculatePerformanceScore(isCorrect: isCorrect, timeSpent: timeSpent, streak: streak)
        
        // Determine if difficulty should change
        if gamesPlayedThisSession > 0 && gamesPlayedThisSession % 3 == 0 {
            let shouldIncrease = performanceScore > 0.8 && correctAnswers > totalAnswers * 3/4
            let shouldDecrease = performanceScore < 0.4 || currentHearts <= 1
            
            if shouldIncrease && currentDifficultyLevel.index < 4 {
                adaptDifficulty(increase: true)
            } else if shouldDecrease && currentDifficultyLevel.index > 0 {
                adaptDifficulty(increase: false)
            }
        }
    }
    
    private func calculatePerformanceScore(isCorrect: Bool, timeSpent: TimeInterval, streak: Int) -> Double {
        var score = isCorrect ? 1.0 : 0.0
        
        // Time bonus/penalty
        let timeTarget = 120.0 // Target 2 minutes for crypto puzzles
        if timeSpent < timeTarget {
            score += (timeTarget - timeSpent) / timeTarget * 0.2
        } else {
            score -= min((timeSpent - timeTarget) / timeTarget * 0.2, 0.3)
        }
        
        // Streak bonus
        score += min(Double(streak) * 0.05, 0.3)
        
        return max(0.0, min(1.0, score))
    }
    
    private func adaptDifficulty(increase: Bool) {
        let newIndex = increase ?
            min(currentDifficultyLevel.index + 1, 4) :
            max(currentDifficultyLevel.index - 1, 0)
        
        if newIndex != currentDifficultyLevel.index {
            currentDifficultyLevel = Self.getDifficultyLevel(for: newIndex)
            adaptationInfo = AdaptationInfo(
                level: currentDifficultyLevel,
                confidenceScore: 0.8
            )
            showAdaptationNotification = true
            
            // Reset some game state for new difficulty
            currentHearts = currentDifficultyLevel.livesAllowed
            timeRemaining = currentDifficultyLevel.timeLimit
        }
    }
    
    // MARK: - Helper Functions
    private func getAdaptiveLetterColor(char: Character, position: Int, data: AdaptiveCryptoPuzzleData) -> Color {
        let isCrypto = data.numberMapping[char] != nil
        let isLocked = currentlyLockedPositions.contains(position)
        let isRevealed = data.revealedLetters.contains(char)
        let hasUserMapping = data.numberMapping[char].flatMap { userMapping[$0] } != nil
        
        if isLocked {
            return .orange
        } else if !isCrypto {
            return .white
        } else if isRevealed {
            return .green
        } else if hasUserMapping {
            return .blue
        } else {
            return .gray
        }
    }
    
    private func getAdaptiveLetterBackground(char: Character, position: Int, data: AdaptiveCryptoPuzzleData) -> Color {
        let isLocked = currentlyLockedPositions.contains(position)
        let isCrypto = data.numberMapping[char] != nil
        let isRevealed = data.revealedLetters.contains(char)
        
        if isLocked {
            return Color.orange.opacity(0.2)
        } else if !isCrypto {
            return .clear
        } else if isRevealed {
            return Color.green.opacity(0.2)
        } else {
            return Color.white.opacity(0.1)
        }
    }
    
    private func getAdaptiveKeyboardTextColor(letter: Character, data: AdaptiveCryptoPuzzleData) -> Color {
        let isUsed = if adaptiveConfig.autoRevealAllInstances {
            userMapping.values.contains(letter)
        } else {
            selectedNumber.map { userMapping[$0] == letter } ?? false
        }
        let isRevealed = data.revealedLetters.contains(letter)
        
        if isRevealed {
            return .green
        } else if isUsed {
            return .white
        } else if selectedNumber != nil {
            return .white
        } else {
            return .gray
        }
    }
    
    private func getAdaptiveKeyboardBackground(letter: Character, data: AdaptiveCryptoPuzzleData) -> Color {
        let isUsed = if adaptiveConfig.autoRevealAllInstances {
            userMapping.values.contains(letter)
        } else {
            selectedNumber.map { userMapping[$0] == letter } ?? false
        }
        let isRevealed = data.revealedLetters.contains(letter)
        
        if isRevealed {
            return Color.green.opacity(0.3)
        } else if isUsed {
            return Color.blue
        } else if selectedNumber != nil {
            return Color.white.opacity(0.2)
        } else {
            return Color.gray.opacity(0.3)
        }
    }
    
    private func clearCurrentMapping() {
        guard let selectedNum = selectedNumber else { return }
        
        if userMapping[selectedNum] != nil {
            userMapping.removeValue(forKey: selectedNum)
        }
        
        selectedNumber = nil
    }
    
    private func loadCompetitiveInsight() {
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.5) {
            competitiveInsight = CompetitiveInsight(
                percentile: 72,
                ranking: "Silver",
                improvement: "+8% this week",
                globalAverage: 75.5,
                userScore: 82.3
            )
        }
    }
    
    // MARK: - Utility Functions
    private func createDynamicWordRows(words: [String], availableWidth: CGFloat) -> [[String]] {
        var rows: [[String]] = []
        var currentRow: [String] = []
        var currentRowWidth: CGFloat = 0
        
        let letterWidth: CGFloat = 28
        let letterSpacing: CGFloat = 4
        let wordSpacing: CGFloat = 16
        
        for word in words {
            let wordWidth = CGFloat(word.count) * letterWidth + CGFloat(max(0, word.count - 1)) * letterSpacing
            let spaceNeeded = currentRowWidth + (currentRow.isEmpty ? 0 : wordSpacing) + wordWidth
            
            if spaceNeeded <= availableWidth || currentRow.isEmpty {
                currentRow.append(word)
                currentRowWidth = spaceNeeded
            } else {
                if !currentRow.isEmpty {
                    rows.append(currentRow)
                }
                currentRow = [word]
                currentRowWidth = wordWidth
            }
        }
        
        if !currentRow.isEmpty {
            rows.append(currentRow)
        }
        
        return rows
    }
    
    private func calculateStartPosition(words: [String], rowIndex: Int, wordRows: [[String]]) -> Int {
        var position = 0
        for i in 0..<rowIndex {
            for word in wordRows[i] {
                position += word.count + 1 // +1 for space
            }
        }
        return position
    }
    
    private func getCharacterAtPosition(_ text: String, position: Int) -> Character {
        var currentPos = 0
        for char in text {
            if char.isLetter || char.isWhitespace {
                if currentPos == position {
                    return char.uppercased().first!
                }
                currentPos += 1
            }
        }
        return " "
    }
    
    private func startTimer() {
        guard isViewActive else { return }
        
        timer?.invalidate()
        timer = Timer.scheduledTimer(withTimeInterval: 1.0, repeats: true) { _ in
            if isViewActive && !isCompleted && timeRemaining > 0 {
                timeRemaining -= 1
            } else if timeRemaining <= 0 && !gameOver && !isCompleted {
                gameOver = true
                recordAdaptivePerformance(
                    isCorrect: false,
                    timeSpent: Date().timeIntervalSince(startTime),
                    streak: 0,
                    livesRemaining: 0
                )
            }
        }
    }
    
    private func cleanup() {
        isViewActive = false
        timer?.invalidate()
        timer = nil
    }
    
    private func resetGame() {
        guard let data = cryptoPuzzleData else { return }
        
        userMapping.removeAll()
        selectedNumber = nil
        isCompleted = false
        gameOver = false
        showWrongFeedback = false
        currentHearts = maxHearts
        timeRemaining = data.timeLimit
        currentlyLockedPositions = data.lockedPositions
        
        // Re-initialize revealed letters
        for letter in data.revealedLetters {
            if let number = data.numberMapping[letter] {
                userMapping[number] = letter
            }
        }
        
        startTimer()
    }
    
    private func formatTime(_ seconds: Int) -> String {
        let minutes = seconds / 60
        let remainingSeconds = seconds % 60
        return String(format: "%d:%02d", minutes, remainingSeconds)
    }
    
    // MARK: - Static Helper Functions
    static func getDifficultyLevel(for difficulty: String) -> DifficultyLevel {
        return DifficultyLevel.create(name: difficulty)
    }

    static func getDifficultyLevel(for index: Int) -> DifficultyLevel {
        return DifficultyLevel.create(index: index)
    }
}

// MARK: - Adaptive Configuration Generator
func generateAdaptiveCryptoConfig(_ difficulty: DifficultyLevel) -> AdaptiveCryptoConfig {
    switch difficulty.index {
    case 0: // Beginner
        return AdaptiveCryptoConfig(
            autoRevealAllInstances: true,
            lockedPositionsRatio: 0.0,
            dependencyChainLength: 0,
            hintRevealCount: 40,
            name: "Beginner",
            description: "All instances of letters revealed automatically"
        )
    case 1: // Easy
        return AdaptiveCryptoConfig(
            autoRevealAllInstances: true,
            lockedPositionsRatio: 0.1,
            dependencyChainLength: 1,
            hintRevealCount: 30,
            name: "Easy",
            description: "Auto-reveal with some locked positions"
        )
    case 2: // Medium
        return AdaptiveCryptoConfig(
            autoRevealAllInstances: false,
            lockedPositionsRatio: 0.15,
            dependencyChainLength: 2,
            hintRevealCount: 25,
            name: "Medium",
            description: "Single letter reveal mode"
        )
    case 3: // Hard
        return AdaptiveCryptoConfig(
            autoRevealAllInstances: false,
            lockedPositionsRatio: 0.25,
            dependencyChainLength: 3,
            hintRevealCount: 15,
            name: "Hard",
            description: "Single reveal with dependency chains"
        )
    case 4: // Expert
        return AdaptiveCryptoConfig(
            autoRevealAllInstances: false,
            lockedPositionsRatio: 0.35,
            dependencyChainLength: 4,
            hintRevealCount: 10,
            name: "Expert",
            description: "Maximum challenge with complex dependencies"
        )
    default:
        return AdaptiveCryptoConfig(
            autoRevealAllInstances: false,
            lockedPositionsRatio: 0.15,
            dependencyChainLength: 2,
            hintRevealCount: 25,
            name: "Medium",
            description: "Single letter reveal mode"
        )
    }
}

// MARK: - Tutorial Dialog
struct AdaptiveCryptoTutorialDialog: View {
    let instructions: String
    let targetWord: String?
    let adaptiveConfig: AdaptiveCryptoConfig
    let onDismiss: () -> Void
    
    var body: some View {
        NavigationView {
            ScrollView {
                VStack(alignment: .leading, spacing: 20) {
                    VStack(spacing: 12) {
                        Text("🔒")
                            .font(.system(size: 50))
                        
                        Text("Adaptive Crypto Puzzle")
                            .font(.title2)
                            .fontWeight(.bold)
                            .multilineTextAlignment(.center)
                    }
                    .frame(maxWidth: .infinity)
                    
                    // Adaptive mode explanation
                    VStack(alignment: .leading, spacing: 8) {
                        Text("🧠 Adaptive Mode: \(adaptiveConfig.name)")
                            .font(.headline)
                            .fontWeight(.bold)
                        
                        Text(adaptiveConfig.description)
                            .font(.subheadline)
                            .foregroundColor(.secondary)
                    }
                    .padding()
                    .background(Color.blue.opacity(0.1))
                    .cornerRadius(12)
                    
                    // Show target word hint if available
                    if let targetWord = targetWord {
                        VStack(alignment: .leading, spacing: 8) {
                            Text("💡 Hint:")
                                .font(.headline)
                                .fontWeight(.bold)
                            
                            Text("The hidden message contains: \(targetWord)")
                                .font(.subheadline)
                                .foregroundColor(.secondary)
                        }
                        .padding()
                        .background(Color.yellow.opacity(0.1))
                        .cornerRadius(12)
                    }
                    
                    VStack(alignment: .leading, spacing: 16) {
                        AdaptiveTutorialStep(
                            number: "1",
                            title: "Select a Number",
                            description: "Tap any number in the puzzle to select it. Some positions may be locked!"
                        )
                        
                        AdaptiveTutorialStep(
                            number: "2",
                            title: "Choose a Letter",
                            description: "Use the keyboard to guess which letter that number represents."
                        )
                        
                        if adaptiveConfig.autoRevealAllInstances {
                            AdaptiveTutorialStep(
                                number: "3",
                                title: "Auto-Reveal Mode",
                                description: "When you guess correctly, ALL instances of that letter are revealed automatically."
                            )
                        } else {
                            AdaptiveTutorialStep(
                                number: "3",
                                title: "Single Reveal Mode",
                                description: "Advanced mode: Only the specific letter you selected is revealed, not all instances."
                            )
                        }
                        
                        if adaptiveConfig.lockedPositionsRatio > 0 {
                            AdaptiveTutorialStep(
                                number: "4",
                                title: "Locked Positions 🔒",
                                description: "Some letters are locked and can only be revealed when you solve nearby dependencies first."
                            )
                        }
                    }
                    
                    // Adaptive tips section
                    VStack(alignment: .leading, spacing: 8) {
                        Text("🎯 Adaptive Strategy Tips")
                            .font(.headline)
                            .fontWeight(.bold)
                        
                        if adaptiveConfig.autoRevealAllInstances {
                            Text("• Focus on common letters like 'E', 'T', 'A'")
                            Text("• Each correct guess reveals multiple positions")
                        } else {
                            Text("• Plan your guesses carefully - only one position reveals at a time")
                            Text("• Look for patterns and word structures")
                        }
                        
                        if adaptiveConfig.lockedPositionsRatio > 0 {
                            Text("• Unlock dependencies by solving nearby letters")
                        }
                        
                        Text("• The system adapts difficulty based on your performance")
                        Text("• Better performance unlocks harder challenges")
                    }
                    .padding()
                    .background(Color.green.opacity(0.1))
                    .cornerRadius(12)
                }
                .padding()
            }
            .navigationTitle("How to Play")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .navigationBarTrailing) {
                    Button("Got It!") {
                        onDismiss()
                    }
                }
            }
        }
    }
}

struct AdaptiveTutorialStep: View {
    let number: String
    let title: String
    let description: String
    
    var body: some View {
        HStack(alignment: .top, spacing: 12) {
            Text(number)
                .font(.headline)
                .fontWeight(.bold)
                .foregroundColor(.white)
                .frame(width: 30, height: 30)
                .background(Color.blue)
                .clipShape(Circle())
            
            VStack(alignment: .leading, spacing: 4) {
                Text(title)
                    .font(.headline)
                    .fontWeight(.semibold)
                
                Text(description)
                    .font(.subheadline)
                    .foregroundColor(.secondary)
                    .fixedSize(horizontal: false, vertical: true)
            }
        }
    }
}

// MARK: - Completion Dialog
// MARK: - Completion Dialog
struct AdaptiveCryptoCompletionDialog: View {
    let originalText: String
    let finalScore: Int
    let timeUsed: Int
    let difficulty: String
    let adaptiveConfig: AdaptiveCryptoConfig
    let attribution: String
    let onContinue: () -> Void
    let onDismiss: () -> Void
    
    var body: some View {
        ScrollView {
            VStack(spacing: 20) {
                // Success animation
                Text("🎉")
                    .font(.system(size: 60))
                
                Text("Adaptive Crypto Solved!")
                    .font(.title)
                    .fontWeight(.bold)
                
                // Adaptive achievement
                VStack(spacing: 8) {
                    Text("🧠 \(adaptiveConfig.name) Mastered!")
                        .font(.headline)
                        .foregroundColor(.blue)
                    
                    Text(adaptiveConfig.description)
                        .font(.caption)
                        .foregroundColor(.secondary)
                        .multilineTextAlignment(.center)
                }
                .padding()
                .background(Color.blue.opacity(0.1))
                .cornerRadius(12)
                
                // Decoded message with scrolling support
                VStack(spacing: 8) {
                    Text("Hidden Message:")
                        .font(.headline)
                        .foregroundColor(.secondary)
                    
                    VStack(spacing: 8) {
                        Text("\"\(originalText.capitalized)\"")
                            .font(.title3)
                            .fontWeight(.medium)
                            .multilineTextAlignment(.center)
                            .italic()
                            .fixedSize(horizontal: false, vertical: true)
                        
                        if !attribution.isEmpty {
                            Text("— \(attribution)")
                                .font(.subheadline)
                                .foregroundColor(.secondary)
                                .multilineTextAlignment(.center)
                                .fixedSize(horizontal: false, vertical: true)
                        }
                    }
                    .padding()
                    .frame(maxWidth: .infinity)
                    .background(Color.green.opacity(0.1))
                    .cornerRadius(12)
                }
                
                // Score and stats
                VStack(spacing: 8) {
                    Text("Final Score: \(finalScore)")
                        .font(.title2)
                        .fontWeight(.bold)
                        .foregroundColor(.green)
                    
                    HStack(spacing: 20) {
                        VStack {
                            Text("\(formatTime(timeUsed))")
                                .font(.headline)
                            Text("Time Used")
                                .font(.caption)
                                .foregroundColor(.secondary)
                        }
                        
                        VStack {
                            Text(difficulty)
                                .font(.headline)
                            Text("Difficulty")
                                .font(.caption)
                                .foregroundColor(.secondary)
                        }
                    }
                }
                .padding()
                .background(Color.gray.opacity(0.1))
                .cornerRadius(12)
                
                // Continue button
                Button(action: onContinue) {
                    Text("Continue Adaptive Journey")
                        .font(.headline)
                        .foregroundColor(.white)
                        .frame(maxWidth: .infinity)
                        .padding()
                        .background(Color.blue)
                        .cornerRadius(12)
                }
            }
            .padding()
        }
        .background(Color(.systemBackground))
        .cornerRadius(20)
        .shadow(radius: 10)
        .padding()
    }
    
    private func formatTime(_ seconds: Int) -> String {
        let minutes = seconds / 60
        let remainingSeconds = seconds % 60
        return String(format: "%d:%02d", minutes, remainingSeconds)
    }
}

// MARK: - Extensions
extension Puzzle {
    /// Parsed adaptive crypto puzzle data
    var adaptiveCryptoPuzzleData: AdaptiveCryptoPuzzleData? {
        guard puzzleType?.lowercased().contains("crypto") == true else {
            return nil
        }
        
        let config = generateAdaptiveCryptoConfig(CryptoPuzzleView.getDifficultyLevel(for: difficulty ?? "Medium"))
        return AdaptiveCryptoPuzzleData(from: question, adaptiveConfig: config)
    }
}
