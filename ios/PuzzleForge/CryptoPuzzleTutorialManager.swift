//
//  CryptoPuzzleTutorialManager.swift
//  PuzzleForge
//
//  Created by Sushanth Tiruvaipati on 8/7/25.
//


//
//  CryptoPuzzleTutorialManager.swift
//  PuzzleForge
//
//  Created by Assistant on [Current Date]
//

import SwiftUI

// MARK: - Tutorial Manager
class CryptoPuzzleTutorialManager: ObservableObject {
    func getTutorialSteps() -> [TutorialStep] {
        return [
            TutorialStep(
                title: "Welcome to Crypto Puzzles! 🔐",
                description: "Learn how to decode secret messages by figuring out which number represents which letter. Each number always represents the same letter!",
                targetComponent: "grid",
                id: "" // Informational
            ),
            TutorialStep(
                title: "Understanding the Code 📝",
                description: "Above you see letters and numbers. Green letters are revealed to help you start. Your job is to figure out what the other numbers represent.",
                targetComponent: "grid",
                id: "" // Informational
            ),
            TutorialStep(
                title: "Select a Number ✨",
                description: "First, tap any number in the puzzle to select it. Try tapping a number now!",
                targetComponent: "grid",
                id: "select_number" // Interactive
            ),
            TutorialStep(
                title: "Choose Your Letter 💡",
                description: "Great! Now the keyboard is ready. Choose which letter you think that number represents by tapping a letter on the keyboard.",
                targetComponent: "keyboard",
                id: "type_letter" // Interactive
            ),
            TutorialStep(
                title: "Look for Patterns 🧩",
                description: "Smart! Now look for common patterns. Short words might be 'THE', 'AND', or 'FOR'. Single letters are usually 'A' or 'I'.",
                targetComponent: "grid",
                id: "" // Informational
            ),
            TutorialStep(
                title: "Make Another Guess 🎯",
                description: "Select another number and make your next guess. Remember, each number always represents the same letter throughout the puzzle!",
                targetComponent: "grid",
                id: "select_another" // Interactive
            ),
            TutorialStep(
                title: "Use Logic and Frequency 📊",
                description: "The letter 'E' is most common in English. Look for the most frequent number - it's likely 'E'. Wrong guesses help you learn!",
                targetComponent: "grid",
                id: "" // Informational
            ),
            TutorialStep(
                title: "Clear if Needed 🗑️",
                description: "Made a mistake? Use the CLEAR button to remove your current guess and try again. Learning from errors is part of the fun!",
                targetComponent: "buttons",
                id: "" // Informational
            ),
            TutorialStep(
                title: "Get Help When Stuck 🆘",
                description: "Tap the help button (?) for hints about the hidden message, or look for the target word if one is provided.",
                targetComponent: "timer",
                id: "" // Informational
            ),
            TutorialStep(
                title: "You're Ready to Decode! 🎉",
                description: "Perfect! Now you know how to crack crypto puzzles. Use logic, patterns, and persistence to reveal the hidden message!",
                targetComponent: "grid",
                id: "" // Informational
            )
        ]
    }
}

// MARK: - Main Tutorial View
struct CryptoPuzzleTutorialView: View {
    let onTutorialComplete: () -> Void
    let onTutorialSkipped: () -> Void
    let onBack: () -> Void
    
    @StateObject private var tutorialManager = CryptoPuzzleTutorialManager()
    @State private var currentStepIndex = 0
    @State private var tutorialState = TutorialState.active
    @State private var sampleCryptoData = createSampleCryptoData()
    
    private var steps: [TutorialStep] {
        tutorialManager.getTutorialSteps()
    }
    
    private var currentStep: TutorialStep? {
        guard currentStepIndex < steps.count else { return nil }
        return steps[currentStepIndex]
    }
    
    var body: some View {
        ZStack {
            // Tutorial version of the crypto screen
            TutorialCryptoContent(
                currentStep: currentStep,
                sampleCryptoData: sampleCryptoData,
                onTutorialAction: handleTutorialAction,
                onBack: onBack
            )
            
            // Tutorial overlay
            if tutorialState == .active && currentStepIndex < steps.count {
                let step = steps[currentStepIndex]
                
                if step.id.isEmpty {
                    // Informational step
                    SmartTutorialOverlay(
                        currentStep: step,
                        totalSteps: steps.count,
                        currentStepNumber: currentStepIndex + 1,
                        onNext: advanceStep,
                        onSkip: skipTutorial
                    )
                } else {
                    // Interactive step
                    MinimalInteractiveOverlay(
                        currentStep: step,
                        totalSteps: steps.count,
                        currentStepNumber: currentStepIndex + 1,
                        onNext: advanceStep,
                        onSkip: skipTutorial
                    )
                }
            }
        }
        .onChange(of: tutorialState) { state in
            switch state {
            case .completed:
                onTutorialComplete()
            case .skipped:
                onTutorialSkipped()
            case .active:
                break
            }
        }
    }
    
    private func advanceStep() {
        if currentStepIndex < steps.count - 1 {
            currentStepIndex += 1
        } else {
            tutorialState = .completed
        }
    }
    
    private func skipTutorial() {
        tutorialState = .skipped
    }
    
    private func handleTutorialAction(_ action: String) {
        let step = steps[safe: currentStepIndex]
        if let step = step, !step.id.isEmpty && step.id == action {
            advanceStep()
        }
    }
}

// MARK: - Tutorial Crypto Content
struct TutorialCryptoContent: View {
    let currentStep: TutorialStep?
    let sampleCryptoData: String
    let onTutorialAction: (String) -> Void
    let onBack: () -> Void
    
    @State private var cryptoPuzzleData: TutorialCryptoPuzzleData?
    @State private var userMapping: [Int: Character] = [:]
    @State private var selectedNumber: Int? = nil
    @State private var timeRemaining = 300
    @State private var currentHearts = 3
    @State private var correctMappings = 0
    @State private var totalMappings = 0
    @State private var showWrongFeedback = false
    @State private var recompositionTrigger: Int = 0
    
    private let maxHearts = 3
    
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
                // Compact Header
                compactHeaderView
                
                // Main puzzle area
                if let data = cryptoPuzzleData {
                    VStack(spacing: 0) {
                        // Compact progress indicator
                        compactProgressView(data: data)
                            .padding(.horizontal, 16)
                            .padding(.top, 8)
                            .modifier(TutorialHighlight(isHighlighted: currentStep?.targetComponent == "progress"))
                        
                        // Crypto puzzle display
                        tutorialCryptoPuzzleDisplayView(data: data)
                            .padding(.horizontal, 16)
                            .padding(.vertical, 8)
                    }
                } else {
                    // Error state
                    VStack(spacing: 16) {
                        Image(systemName: "exclamationmark.triangle")
                            .font(.system(size: 40))
                            .foregroundColor(.red)
                        
                        Text("Unable to load tutorial puzzle")
                            .font(.headline)
                            .foregroundColor(.white)
                        
                        Button("Go Back") { onBack() }
                            .font(.headline)
                            .foregroundColor(.white)
                            .padding(.horizontal, 24)
                            .padding(.vertical, 12)
                            .background(Color.blue)
                            .cornerRadius(8)
                    }
                    .frame(maxHeight: .infinity)
                }
                
                // Control buttons
                if cryptoPuzzleData != nil {
                    controlButtonsView
                }
                
                // Fixed keyboard at bottom
                if cryptoPuzzleData != nil {
                    compactLetterSelectionKeyboard()
                }
            }
        }
        .navigationBarHidden(true)
        .onAppear {
            setupTutorialPuzzle()
        }
    }
    
    // MARK: - UI Components
    private var compactHeaderView: some View {
        HStack {
            // Back button
            Button(action: onBack) {
                Image(systemName: "arrow.left")
                    .font(.title2)
                    .foregroundColor(.white)
                    .frame(width: 40, height: 40)
            }
            
            // Title
            Text("🔐 Crypto Tutorial")
                .font(.headline)
                .fontWeight(.bold)
                .foregroundColor(.white)
            
            Spacer()
            
            // Timer
            Text(formatTime(timeRemaining))
                .font(.system(size: 16, weight: .bold))
                .foregroundColor(.white)
                .padding(.horizontal, 12)
                .padding(.vertical, 6)
                .background(Color.black.opacity(0.3))
                .cornerRadius(8)
                .modifier(TutorialHighlight(isHighlighted: currentStep?.targetComponent == "timer"))
            
            // Hearts
            HStack(spacing: 2) {
                ForEach(0..<maxHearts, id: \.self) { index in
                    Image(systemName: index < currentHearts ? "heart.fill" : "heart")
                        .font(.system(size: 14))
                        .foregroundColor(index < currentHearts ? .red : .gray)
                }
            }
            
            // Help button
            Button(action: { onTutorialAction("help_clicked") }) {
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
    
    private func compactProgressView(data: TutorialCryptoPuzzleData) -> some View {
        VStack(spacing: 6) {
            HStack {
                Text("Progress: \(correctMappings)/\(totalMappings)")
                    .font(.caption)
                    .fontWeight(.semibold)
                    .foregroundColor(.white)
                
                Spacer()
                
                if showWrongFeedback {
                    HStack(spacing: 4) {
                        Image(systemName: "xmark.circle.fill")
                            .font(.caption)
                            .foregroundColor(.red)
                        Text("Try again!")
                            .font(.caption)
                            .foregroundColor(.red)
                    }
                    .transition(.opacity)
                }
            }
            
            ProgressView(value: Double(correctMappings), total: Double(totalMappings))
                .progressViewStyle(LinearProgressViewStyle(tint: .green))
                .frame(height: 4)
                .background(Color.white.opacity(0.2))
                .cornerRadius(2)
        }
        .padding(.horizontal, 4)
    }
    
    private func tutorialCryptoPuzzleDisplayView(data: TutorialCryptoPuzzleData) -> some View {
        let words = data.originalText.split(separator: " ").map { String($0) }
        
        return VStack(spacing: 16) {
            ForEach(Array(words.enumerated()), id: \.offset) { wordIndex, word in
                VStack(spacing: 10) {
                    // Letters row
                    HStack(spacing: 2) {
                        ForEach(Array(word.enumerated()), id: \.offset) { charIndex, char in
                            tutorialCryptoLetterCell(char: char, data: data)
                        }
                    }
                    
                    // Numbers row
                    HStack(spacing: 2) {
                        ForEach(Array(word.enumerated()), id: \.offset) { charIndex, char in
                            tutorialCryptoNumberCell(char: char, data: data)
                        }
                    }
                }
            }
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 12)
        .background(Color.white.opacity(0.05))
        .cornerRadius(12)
        .modifier(TutorialHighlight(isHighlighted: currentStep?.targetComponent == "grid"))
    }
    
    private var controlButtonsView: some View {
        HStack(spacing: 16) {
            // Clear button
            Button(action: {
                clearCurrentMapping()
                onTutorialAction("clear_mapping")
            }) {
                Text("CLEAR")
                    .font(.system(size: 14, weight: .semibold))
                    .foregroundColor(.white)
                    .padding(.horizontal, 20)
                    .padding(.vertical, 10)
                    .background(Color.red.opacity(0.7))
                    .cornerRadius(8)
            }
            .disabled(selectedNumber == nil)
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 8)
        .modifier(TutorialHighlight(isHighlighted: currentStep?.targetComponent == "buttons"))
    }
    
    private func compactLetterSelectionKeyboard() -> some View {
        VStack(spacing: 8) {
            // Instructions
            Text(selectedNumber != nil ? "Choose letter for \(selectedNumber!)" : "Select a number first")
                .font(.caption)
                .foregroundColor(.white.opacity(0.8))
                .padding(.top, 8)
            
            // Keyboard rows
            let keyboardRows = ["QWERTYUIOP", "ASDFGHJKL", "ZXCVBNM"]
            
            ForEach(keyboardRows, id: \.self) { row in
                HStack(spacing: 3) {
                    ForEach(Array(row), id: \.self) { letter in
                        tutorialKeyboardButton(letter: letter)
                    }
                }
            }
        }
        .padding(.horizontal, 8)
        .padding(.bottom, 8)
        .background(Color.black.opacity(0.3))
        .modifier(TutorialHighlight(isHighlighted: currentStep?.targetComponent == "keyboard"))
    }
    
    // MARK: - Tutorial-specific components
    private func tutorialCryptoLetterCell(char: Character, data: TutorialCryptoPuzzleData) -> some View {
        let isRevealed = data.revealedLetters.contains(char)
        let userLetter = data.numberMapping[char].flatMap { userMapping[$0] }
        
        let displayText: String = {
            if data.hiddenLetters.contains(char) {
                if isRevealed {
                    return String(char) // Show revealed letters in green
                } else if let userLetter = userLetter {
                    return String(userLetter) // Show user's guess
                } else {
                    return "_" // Show underscore for unmapped
                }
            } else {
                return String(char) // Show non-encoded letters
            }
        }()
        
        return Text(displayText)
            .font(.system(size: 24, weight: .bold))
            .foregroundColor(getLetterColor(char: char, data: data))
            .frame(width: 32, height: 32)
            .background(getLetterBackground(char: char, data: data))
            .cornerRadius(6)
    }
    
    private func tutorialCryptoNumberCell(char: Character, data: TutorialCryptoPuzzleData) -> some View {
        if data.hiddenLetters.contains(char), let number = data.numberMapping[char] {
            return AnyView(
                Button(action: {
                    selectedNumber = selectedNumber == number ? nil : number
                    onTutorialAction(selectedNumber != nil ? "select_number" : "deselect_number")
                    
                    // Handle step progression
                    if selectedNumber != nil {
                        if currentStep?.id == "select_number" {
                            onTutorialAction("select_number")
                        } else if currentStep?.id == "select_another" {
                            onTutorialAction("select_another")
                        }
                    }
                }) {
                    Text("\(number)")
                        .font(.system(size: 16, weight: .semibold))
                        .foregroundColor(selectedNumber == number ? .white : .black)
                        .frame(width: 32, height: 24)
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
        } else {
            return AnyView(
                Spacer()
                    .frame(width: 32, height: 24)
            )
        }
    }
    
    private func tutorialKeyboardButton(letter: Character) -> some View {
        let isUsed = userMapping.values.contains(letter)
        let isRevealed = cryptoPuzzleData?.revealedLetters.contains(letter) ?? false
        
        return Button(action: {
            handleLetterSelection(letter)
        }) {
            Text(String(letter))
                .font(.system(size: 16, weight: .semibold))
                .foregroundColor(getTutorialKeyboardTextColor(letter: letter))
                .frame(maxWidth: .infinity, minHeight: 44)
                .background(getTutorialKeyboardBackground(letter: letter))
                .cornerRadius(6)
        }
        .buttonStyle(PlainButtonStyle())
        .disabled(selectedNumber == nil || isRevealed)
    }
    
    // MARK: - Setup and Logic
    private func setupTutorialPuzzle() {
        guard let data = parseTutorialCryptoData(from: sampleCryptoData) else {
            print("❌ Failed to parse tutorial crypto data")
            return
        }
        
        self.cryptoPuzzleData = data
        
        // Initialize revealed letters in user mapping
        for letter in data.revealedLetters {
            if let number = data.numberMapping[letter] {
                userMapping[number] = letter
            }
        }
        
        // Set progress tracking
        totalMappings = data.hiddenLetters.count
        correctMappings = data.revealedLetters.count
        
        print("✅ Tutorial crypto puzzle setup complete")
    }
    
    private func handleLetterSelection(_ letter: Character) {
        guard let selectedNum = selectedNumber,
              let data = cryptoPuzzleData else { return }
        
        // Check if this is the correct mapping
        let correctLetter = data.numberMapping.first(where: { $0.value == selectedNum })?.key
        
        if correctLetter == letter {
            // Correct mapping
            userMapping[selectedNum] = letter
            correctMappings += 1
            selectedNumber = nil
            
            // Provide haptic feedback
            let impactFeedback = UIImpactFeedbackGenerator(style: .light)
            impactFeedback.impactOccurred()
            
            onTutorialAction("type_letter")
        } else {
            // Wrong mapping - show feedback but don't penalize in tutorial
            showWrongFeedback = true
            selectedNumber = nil
            
            // Hide feedback after delay
            DispatchQueue.main.asyncAfter(deadline: .now() + 1.5) {
                showWrongFeedback = false
            }
            
            onTutorialAction("wrong_letter")
        }
        
        recompositionTrigger += 1
    }
    
    private func clearCurrentMapping() {
        guard let selectedNum = selectedNumber else { return }
        
        if userMapping[selectedNum] != nil {
            userMapping.removeValue(forKey: selectedNum)
            correctMappings = max(0, correctMappings - 1)
        }
        
        selectedNumber = nil
        recompositionTrigger += 1
    }
    
    // MARK: - Helper Functions
    private func getLetterColor(char: Character, data: TutorialCryptoPuzzleData) -> Color {
        let isRevealed = data.revealedLetters.contains(char)
        let hasUserMapping = data.numberMapping[char].flatMap { userMapping[$0] } != nil
        
        if !data.hiddenLetters.contains(char) {
            return .white
        } else if isRevealed {
            return .green
        } else if hasUserMapping {
            return .blue
        } else {
            return .gray
        }
    }
    
    private func getLetterBackground(char: Character, data: TutorialCryptoPuzzleData) -> Color {
        let isRevealed = data.revealedLetters.contains(char)
        
        if !data.hiddenLetters.contains(char) {
            return .clear
        } else if isRevealed {
            return Color.green.opacity(0.2)
        } else {
            return Color.white.opacity(0.1)
        }
    }
    
    private func getTutorialKeyboardTextColor(letter: Character) -> Color {
        let isUsed = userMapping.values.contains(letter)
        let isRevealed = cryptoPuzzleData?.revealedLetters.contains(letter) ?? false
        
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
    
    private func getTutorialKeyboardBackground(letter: Character) -> Color {
        let isUsed = userMapping.values.contains(letter)
        let isRevealed = cryptoPuzzleData?.revealedLetters.contains(letter) ?? false
        
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
    
    private func formatTime(_ seconds: Int) -> String {
        let minutes = seconds / 60
        let remainingSeconds = seconds % 60
        return String(format: "%d:%02d", minutes, remainingSeconds)
    }
}

// MARK: - Tutorial Data Models
struct TutorialCryptoPuzzleData {
    let originalText: String
    let numberMapping: [Character: Int]
    let revealedLetters: Set<Character>
    let hiddenLetters: Set<Character>
    let targetWord: String?
    let instructions: String
}

// MARK: - Sample Data and Parsing
func createSampleCryptoData() -> String {
    return """
    {
        "originalText": "THE CAT",
        "numberMapping": {
            "T": 1,
            "H": 2,
            "E": 3,
            "C": 4,
            "A": 5
        },
        "revealedLetters": ["T"],
        "hiddenLetters": ["H", "E", "C", "A"],
        "targetWord": "CAT",
        "instructions": "Decode this simple message to learn the basics!",
        "difficulty": "Easy",
        "timeLimit": 300
    }
    """
}

func parseTutorialCryptoData(from jsonString: String) -> TutorialCryptoPuzzleData? {
    guard let data = jsonString.data(using: .utf8),
          let json = try? JSONSerialization.jsonObject(with: data) as? [String: Any] else {
        return nil
    }
    
    guard let originalText = json["originalText"] as? String,
          let numberMappingDict = json["numberMapping"] as? [String: Int] else {
        return nil
    }
    
    // Convert string keys to Character keys
    var charMapping: [Character: Int] = [:]
    for (key, value) in numberMappingDict {
        if let char = key.first {
            charMapping[char] = value
        }
    }
    
    // Parse revealed and hidden letters
    let revealedArray = json["revealedLetters"] as? [String] ?? []
    let hiddenArray = json["hiddenLetters"] as? [String] ?? []
    
    let revealedLetters = Set(revealedArray.compactMap { $0.first })
    let hiddenLetters = Set(hiddenArray.compactMap { $0.first })
    
    return TutorialCryptoPuzzleData(
        originalText: originalText,
        numberMapping: charMapping,
        revealedLetters: revealedLetters,
        hiddenLetters: hiddenLetters,
        targetWord: json["targetWord"] as? String,
        instructions: json["instructions"] as? String ?? "Decode the hidden message!"
    )
}
