//
//  MemoryPreviousSinglePuzzleView.swift
//  PuzzleForge
//
//  Created by Assistant on 7/14/25.
//

import SwiftUI
import AVFoundation
import AudioToolbox

// MARK: - Puzzle Data Extension
extension Puzzle {
    var memoryPreviousSinglePuzzleData: MemoryPreviousSinglePuzzleData? {
        guard let data = question.data(using: .utf8),
              let json = try? JSONSerialization.jsonObject(with: data) as? [String: Any] else {
            print("❌ MEMORY_SINGLE: Failed to parse question JSON")
            return nil
        }
        
        guard let binaryArray = json["binarySequence"] as? [Int] else {
            print("❌ MEMORY_SINGLE: Failed to parse binarySequence")
            return nil
        }
        guard let questionArray = json["questionSequence"] as? [[String: Any]] else {
            print("❌ MEMORY_SINGLE: Failed to parse questionSequence")
            return nil
        }
        
        let questionSequence = questionArray.compactMap { stepDict -> MemoryStep? in
            guard let step = stepDict["step"] as? Int,
                  let value = stepDict["value"] as? Int,
                  let isFirstStep = stepDict["isFirstStep"] as? Bool else {
                return nil
            }
            return MemoryStep(step: step, value: value, isFirstStep: isFirstStep)
        }
        
        guard let instructionsDict = json["instructions"] as? [String: Any],
              let title = instructionsDict["title"] as? String,
              let description = instructionsDict["description"] as? String,
              let stepsArray = instructionsDict["steps"] as? [String],
              let tip = instructionsDict["tip"] as? String else {
            print("❌ MEMORY_SINGLE: Failed to parse instructions")
            return nil
        }
        
        let instructions = MemoryInstructions(
            title: title,
            description: description,
            steps: stepsArray,
            tip: tip
        )
        
        guard let totalSteps = json["totalSteps"] as? Int,
              let totalQuestions = json["totalQuestions"] as? Int,
              let difficulty = json["difficulty"] as? String else {
            print("❌ MEMORY_SINGLE: Failed to parse puzzle metadata")
            return nil
        }
        
        return MemoryPreviousSinglePuzzleData(
            binarySequence: binaryArray,
            questionSequence: questionSequence,
            instructions: instructions,
            totalSteps: totalSteps,
            totalQuestions: totalQuestions,
            difficulty: difficulty
        )
    }
}

// MARK: - Data Models
struct MemoryStep {
    let step: Int
    let value: Int
    let isFirstStep: Bool
}

struct MemoryInstructions {
    let title: String
    let description: String
    let steps: [String]
    let tip: String
}

struct MemoryPreviousSinglePuzzleData {
    let binarySequence: [Int]
    let questionSequence: [MemoryStep]
    let instructions: MemoryInstructions
    let totalSteps: Int
    let totalQuestions: Int
    let difficulty: String
}

struct MemoryAnswerData {
    let answerSequence: [Int]
    let correctAnswers: [Int]
    let totalQuestions: Int
    let maxScore: Int
}

// MARK: - Game State
enum MemoryPreviousSingleGameState {
    case instructions
    case playing
    case feedback
    case completed
}

// MARK: - Adaptive Models (from your adaptive view)
struct AdaptiveMemoryConfig {
    let visualDistractors: Bool
    let delayedReveal: Bool
    let memoryInterference: Bool
    let multiModalStimuli: Bool
    let cognitiveLoad: Int
    let name: String
    let description: String
}

struct AdaptiveMemoryStep {
    let step: Int
    let value: Int
    let isFirstStep: Bool
    let hasDistractor: Bool
    let delayMs: TimeInterval
    let interferencePattern: [Int]
    let modalityType: String
}

struct AdaptiveMemoryPuzzleData {
    let binarySequence: [Int]
    let questionSequence: [AdaptiveMemoryStep]
    let instructions: AdaptiveInstructions
    let totalSteps: Int
    let totalQuestions: Int
    let adaptiveConfig: AdaptiveMemoryConfig
    let difficulty: String
    
    struct AdaptiveInstructions {
        let title: String
        let description: String
        let steps: [String]
        let tip: String
    }
}

enum AdaptiveMemoryGameState {
    case instructions
    case playing
    case feedback
    case completed
}

// MARK: - Main Adaptive View
struct MemoryPreviousSinglePuzzleView: View {
    let puzzleData: String
    let correctAnswer: String
    let onSubmitAnswer: (Bool) -> Void
    let fetchNextPuzzle: () -> Void
    let onBack: () -> Void
    
    // Adaptive difficulty management
    @StateObject private var difficultyManager = DifficultyManager.shared
    @State private var currentDifficultyLevel: DifficultyManager.DifficultyLevel = .medium
    @State private var adaptationInfo: DifficultyManager.AdaptiveConfig? = nil
    @State private var showAdaptationNotification = false
    @State private var competitiveInsight: CompetitiveInsight? = nil
    
    @State private var gameState: AdaptiveMemoryGameState = .instructions
    /// Index into `questionSequence`:
    ///  - 0 = first (memorize-only) symbol
    ///  - 1..totalSymbols-1 = answerable steps
    @State private var currentQuestionIndex = 0
    @State private var currentScore = 0
    @State private var showFeedback = false
    @State private var lastAnswerCorrect = false
    @State private var userAnswers: [Int] = [] // length == totalQuestions
    @State private var timeRemaining = 0
    @State private var animationKey = 0
    @State private var currentStreak = 0
    @State private var currentHearts = 3
    
    // Adaptive visual states
    @State private var showInterference = false
    @State private var isRevealed = false
    @State private var showDistractors = false
    
    // Timer
    @State private var timer: Timer?
    
    // Audio feedback
    @State private var audioPlayer: AVAudioPlayer?
    
    // Parsed data
    @State private var parsedPuzzleData: AdaptiveMemoryPuzzleData?
    @State private var parsedAnswerData: MemoryAnswerData?
    
    // Enhanced symbol definitions with adaptive features
    @State private var symbols: [String: String] = [:]
    @State private var symbolColors: [String: Color] = [:]
    
    var body: some View {
        ZStack {
            adaptiveBackground.ignoresSafeArea()
            
            VStack(spacing: 0) {
                AdaptiveUnifiedHeader(
                    puzzleType: "memorySingle",
                    currentDifficulty: currentDifficultyLevel,
                    score: currentScore,
                    // FIX: show 1..totalQuestions, clamped
                    challengeNumber: displayQuestionNumber,
                    totalChallenges: parsedPuzzleData?.totalQuestions ?? 0,
                    timer: formatTime(timeRemaining),
                    lives: currentHearts,
                    competitiveInsight: competitiveInsight,
                    level: nil,
                    streakInfo: nil,
                    onBack: onBack,
                    onPause: { },
                    onHint: { }
                )
                
                UnifiedAdaptationNotification(
                    adaptationInfo: adaptationInfo,
                    puzzleType: "memorySingle",
                    visible: showAdaptationNotification,
                    onDismiss: { showAdaptationNotification = false }
                )
                
                Spacer(minLength: 32)
                
                switch gameState {
                case .instructions:
                    adaptiveInstructionsView
                case .playing:
                    adaptiveGameView
                case .feedback:
                    adaptiveGameView
                case .completed:
                    adaptiveCompletionView
                }
                
                Spacer()
            }
            .padding(.horizontal, 16)
        }
        .navigationBarHidden(true)
        .onAppear {
            setupAdaptivePuzzle()
            generateAdaptiveSymbols()
            currentDifficultyLevel = difficultyManager.getCurrentDifficulty(for: "memorySingle")
            Task {
                competitiveInsight = await CompetitiveRankingManager.shared.getCompetitiveInsight(
                    userId: "current_user",
                    puzzleType: "memorySingle",
                    difficulty: currentDifficultyLevel.name
                )
            }
        }
        .onDisappear { timer?.invalidate() }
    }
    
    // MARK: - Computed helpers
    private var totalSymbols: Int { parsedPuzzleData?.questionSequence.count ?? 0 }
    private var totalQuestions: Int { max(0, totalSymbols - 1) } // exclude first memorize-only
    private var displayQuestionNumber: Int {
        // 0 (memorize) => 1 for display; then clamp to totalQuestions
        guard totalQuestions > 0 else { return 0 }
        return min(max(1, currentQuestionIndex), totalQuestions)
    }
    private var currentAnswerIdx: Int? {
        // Map sequence index (1..totalSymbols-1) -> answer index (0..totalQuestions-1)
        guard currentQuestionIndex > 0 else { return nil }
        return currentQuestionIndex - 1
    }
    
    // MARK: - Adaptive Background
    private var adaptiveBackground: LinearGradient {
        switch currentDifficultyLevel {
        case .beginner:
            return LinearGradient(colors: [Color(red: 0.12, green: 0.23, blue: 0.54), Color(red: 0.18, green: 0.29, blue: 0.60)], startPoint: .top, endPoint: .bottom)
        case .easy:
            return LinearGradient(colors: [Color(red: 0.14, green: 0.21, blue: 0.52), Color(red: 0.20, green: 0.27, blue: 0.58)], startPoint: .top, endPoint: .bottom)
        case .medium:
            return LinearGradient(colors: [Color(red: 0.15, green: 0.20, blue: 0.52), Color(red: 0.21, green: 0.26, blue: 0.58)], startPoint: .top, endPoint: .bottom)
        case .hard:
            return LinearGradient(colors: [Color(red: 0.16, green: 0.18, blue: 0.50), Color(red: 0.22, green: 0.24, blue: 0.56)], startPoint: .top, endPoint: .bottom)
        case .expert:
            return LinearGradient(colors: [Color(red: 0.18, green: 0.15, blue: 0.50), Color(red: 0.24, green: 0.21, blue: 0.56)], startPoint: .top, endPoint: .bottom)
        }
    }
    
    // MARK: - Adaptive Instructions View with Floating Button
    // MARK: - Adaptive Instructions View with Floating Button
    // MARK: - Adaptive Instructions View with Floating Button
    private var adaptiveInstructionsView: some View {
        ZStack(alignment: .bottom) {
            // Main scrollable content
            ScrollView(.vertical, showsIndicators: false) {
                VStack(spacing: 24) {
                    if let instructions = parsedPuzzleData?.instructions,
                       let config = parsedPuzzleData?.adaptiveConfig {
                        VStack(spacing: 8) {
                            Text(instructions.title)
                                .font(.title2)
                                .fontWeight(.bold)
                                .foregroundColor(.white)
                                .multilineTextAlignment(.center)
                            Text(config.description)
                                .font(.body)
                                .foregroundColor(.white.opacity(0.9))
                                .multilineTextAlignment(.center)
                        }
                        .padding(.top, 20)
                        
                        AdaptiveFeaturesCard(config: config)
                        AdaptiveSymbolLegend(symbols: symbols, symbolColors: symbolColors, config: config)
                        AdaptiveInstructionsList(steps: instructions.steps, tip: instructions.tip, config: config)
                        
                        // Add spacing at bottom to account for floating button
                        Spacer(minLength: 100)
                    }
                }
                .padding(.horizontal, 16)
            }
            
            // Floating start button
            VStack {
                Spacer()
                Button(action: startAdaptiveGame) {
                    Text("START ADAPTIVE CHALLENGE")
                        .font(.system(size: 18, weight: .bold))
                        .foregroundColor(.white)
                        .frame(maxWidth: .infinity)
                        .frame(height: 56)
                        .background(Color(red: 0.06, green: 0.72, blue: 0.51))
                        .cornerRadius(12)
                        .shadow(color: .black.opacity(0.2), radius: 8, x: 0, y: 4)
                }
                .padding(.horizontal, 16)
                .padding(.bottom, 34) // Account for safe area
            }
            .background(
                // Subtle fade effect above the button
                VStack {
                    Spacer()
                    Rectangle()
                        .fill(
                            LinearGradient(
                                colors: [Color.clear, Color.black.opacity(0.2)],
                                startPoint: .top,
                                endPoint: .bottom
                            )
                        )
                        .frame(height: 60)
                }
            )
        }
    }
    
    // MARK: - Adaptive Game View
    private var adaptiveGameView: some View {
        VStack(spacing: 32) {
            AdaptiveProgressIndicator(
                currentQuestion: displayQuestionNumber,
                totalQuestions: totalQuestions,
                cognitiveLoad: parsedPuzzleData?.adaptiveConfig.cognitiveLoad ?? 1
            )
            
            if let questionSequence = parsedPuzzleData?.questionSequence,
               currentQuestionIndex < questionSequence.count {
                let currentStep = questionSequence[currentQuestionIndex]
                AdaptiveSymbolDisplay(
                    step: currentStep,
                    symbols: symbols,
                    symbolColors: symbolColors,
                    adaptiveConfig: parsedPuzzleData?.adaptiveConfig,
                    isRevealed: isRevealed,
                    showInterference: showInterference,
                    showDistractors: showDistractors,
                    animationKey: animationKey
                )
            }
            
            if currentQuestionIndex > 0 {
                AdaptiveQuestionText(
                    config: parsedPuzzleData?.adaptiveConfig,
                    currentQuestion: displayQuestionNumber
                )
            } else {
                Text("Remember this symbol")
                    .font(.system(size: 18))
                    .foregroundColor(.white)
                    .multilineTextAlignment(.center)
            }
            
            Spacer()
            
            if showFeedback {
                AdaptiveFeedbackOverlay(
                    isCorrect: lastAnswerCorrect,
                    cognitiveLoad: parsedPuzzleData?.adaptiveConfig.cognitiveLoad ?? 1
                )
            }
            
            if !showFeedback && currentQuestionIndex > 0 {
                AdaptiveAnswerButtons(
                    onAnswer: submitAdaptiveAnswer,
                    cognitiveLoad: parsedPuzzleData?.adaptiveConfig.cognitiveLoad ?? 1
                )
            } else if !showFeedback && currentQuestionIndex == 0 {
                Button(action: {
                    // Move from memorize step (0) -> first question (1)
                    currentQuestionIndex = 1
                    handleAdaptiveQuestionDisplay()
                    withAnimation(.easeInOut(duration: 0.6)) { animationKey += 1 }
                }) {
                    Text("NEXT")
                        .font(.system(size: 18, weight: .bold))
                        .foregroundColor(.white)
                        .frame(maxWidth: .infinity)
                        .frame(height: 56)
                        .background(Color(red: 0.06, green: 0.72, blue: 0.51))
                        .cornerRadius(12)
                }
            }
        }
    }
    
    // MARK: - Adaptive Completion View
    private var adaptiveCompletionView: some View {
        VStack(spacing: 32) {
            Text("Adaptive Challenge Complete!")
                .font(.title)
                .fontWeight(.bold)
                .foregroundColor(.white)
                .multilineTextAlignment(.center)
            
            AdaptiveStatsCard(
                score: min(currentScore, parsedAnswerData?.maxScore ?? currentScore),
                adaptiveConfig: parsedPuzzleData?.adaptiveConfig,
                userAnswers: userAnswers,
                correctAnswers: parsedAnswerData?.correctAnswers ?? [],
                totalQuestions: parsedAnswerData?.totalQuestions ?? 0,
                maxScore: parsedAnswerData?.maxScore ?? 0
            )
            
            Spacer()
            
            Button(action: {
                let totalQ = parsedAnswerData?.totalQuestions ?? 1
                let correctCount = zip(userAnswers, parsedAnswerData?.correctAnswers ?? []).filter { $0.0 == $0.1 }.count
                let finalScore = Double(correctCount) / Double(totalQ) * 100
                onSubmitAnswer(finalScore >= 60)
                fetchNextPuzzle()
            }) {
                Text("CONTINUE")
                    .font(.system(size: 18, weight: .bold))
                    .foregroundColor(.white)
                    .frame(maxWidth: .infinity)
                    .frame(height: 56)
                    .background(Color(red: 0.06, green: 0.72, blue: 0.51))
                    .cornerRadius(12)
            }
        }
    }
    
    // MARK: - Helper Methods
    private func setupAdaptivePuzzle() {
        guard let puzzleDataObj = parseAdaptiveMemoryPuzzleData(puzzleData),
              let answerDataObj = parseMemoryAnswerData(correctAnswer) else {
            print("Failed to parse adaptive memory puzzle data")
            createFallbackAdaptiveData()
            return
        }
        
        parsedPuzzleData = puzzleDataObj
        parsedAnswerData = answerDataObj
        
        // FIX: make timer based on number of answerable questions
        timeRemaining = (puzzleDataObj.totalQuestions) * 4
        
        print("Adaptive Memory Previous Single puzzle setup complete")
        print("Cognitive Load: \(puzzleDataObj.adaptiveConfig.cognitiveLoad)")
        print("Features: \(puzzleDataObj.adaptiveConfig)")
    }
    
    private func createFallbackAdaptiveData() {
        print("Using fallback adaptive data for memory single puzzle")
        
        parsedPuzzleData = AdaptiveMemoryPuzzleData(
            binarySequence: [0, 1, 1, 0, 1],
            questionSequence: [
                AdaptiveMemoryStep(step: 1, value: 0, isFirstStep: true, hasDistractor: false, delayMs: 0, interferencePattern: [], modalityType: "standard"),
                AdaptiveMemoryStep(step: 2, value: 1, isFirstStep: false, hasDistractor: false, delayMs: 500, interferencePattern: [], modalityType: "standard"),
                AdaptiveMemoryStep(step: 3, value: 1, isFirstStep: false, hasDistractor: true, delayMs: 750, interferencePattern: [0], modalityType: "color"),
                AdaptiveMemoryStep(step: 4, value: 0, isFirstStep: false, hasDistractor: false, delayMs: 500, interferencePattern: [], modalityType: "standard"),
                AdaptiveMemoryStep(step: 5, value: 1, isFirstStep: false, hasDistractor: true, delayMs: 1000, interferencePattern: [1, 0], modalityType: "shape")
            ],
            instructions: AdaptiveMemoryPuzzleData.AdaptiveInstructions(
                title: "Adaptive Memory Previous Single",
                description: "Medium: Visual distractors with multi-modal stimuli",
                steps: [
                    "1. Look at the first symbol and remember it",
                    "2. For each new symbol, decide if it's SAME or DIFFERENT from the previous one",
                    "3. Tap 'SAME' if it matches, 'DIFFERENT' if it doesn't",
                    "4. Continue through the entire sequence",
                    "5. Focus on the main symbol, ignore visual distractors"
                ],
                tip: "Filter out visual noise and focus on the central symbol"
            ),
            totalSteps: 5,
            totalQuestions: 4,
            adaptiveConfig: AdaptiveMemoryConfig(
                visualDistractors: true,
                delayedReveal: true,
                memoryInterference: false,
                multiModalStimuli: true,
                cognitiveLoad: 3,
                name: "Medium",
                description: "Visual distractors with multi-modal stimuli"
            ),
            difficulty: "Medium"
        )
        
        parsedAnswerData = MemoryAnswerData(
            answerSequence: [0, 1, 0, 1],
            correctAnswers: [0, 1, 0, 1],
            totalQuestions: 4,
            maxScore: 60
        )
        
        timeRemaining = 4 * 4
    }
    
    private func generateAdaptiveSymbols() {
        guard let config = parsedPuzzleData?.adaptiveConfig else {
            generateStandardSymbols()
            return
        }
        
        if config.multiModalStimuli {
            let symbolSets = [
                ("🔴", "🔵", Color.red, Color.blue),
                ("●", "○", Color.purple, Color.purple),
                ("◆", "◇", Color.orange, Color.orange),
                ("▲", "△", Color.green, Color.green)
            ]
            let selectedSet = symbolSets.randomElement() ?? symbolSets[0]
            symbols = ["0": selectedSet.0, "1": selectedSet.1]
            symbolColors = ["0": selectedSet.2, "1": selectedSet.3]
        } else if config.visualDistractors {
            let symbolPairs = [
                ("◉", "○", Color.cyan, Color.cyan),
                ("◆", "◇", Color.yellow, Color.yellow),
                ("▣", "▢", Color.green, Color.green)
            ]
            let selectedPair = symbolPairs.randomElement() ?? symbolPairs[0]
            symbols = ["0": selectedPair.0, "1": selectedPair.1]
            symbolColors = ["0": selectedPair.2, "1": selectedPair.3]
        } else {
            generateStandardSymbols()
        }
        
        print("Generated adaptive symbols: \(symbols)")
        print("Symbol colors: \(symbolColors)")
    }
    
    private func generateStandardSymbols() {
        let symbolPairs = [
            ("★", "☆", Color.purple, Color.purple),
            ("●", "○", Color.purple, Color.purple),
            ("▲", "△", Color.purple, Color.purple)
        ]
        let selectedPair = symbolPairs.randomElement() ?? symbolPairs[0]
        symbols = ["0": selectedPair.0, "1": selectedPair.1]
        symbolColors = ["0": selectedPair.2, "1": selectedPair.3]
    }
    
    private func startAdaptiveGame() {
        gameState = .playing
        currentQuestionIndex = 0           // 0 = memorize step
        currentScore = 0
        currentStreak = 0
        currentHearts = 3
        lastAnswerCorrect = false
        showFeedback = false

        // Pre-size to avoid alignment issues (e.g., last answer not counted)
        let tq = max(0, (parsedPuzzleData?.questionSequence.count ?? 0) - 1)
        userAnswers = Array(repeating: -1, count: tq)

        startAdaptiveTimer()
        isRevealed = true
        withAnimation(.easeInOut(duration: 0.6)) { animationKey += 1 }
    }
    
    private func startAdaptiveTimer() {
        timer?.invalidate()
        timer = Timer.scheduledTimer(withTimeInterval: 1.0, repeats: true) { _ in
            if gameState != .playing { return }
            if timeRemaining > 0 {
                timeRemaining -= 1
            } else {
                timer?.invalidate()
                gameState = .completed
            }
        }
    }
    
    // MARK: - Answer Flow (core fix)
    // MARK: - Fixed Answer Flow
    private func submitAdaptiveAnswer(_ isSame: Bool) {
        // Prevent double taps during feedback or outside playing state
        guard gameState == .playing, !showFeedback else { return }
        guard let answerData = parsedAnswerData, totalQuestions > 0 else { return }
        guard let qIdx = currentAnswerIdx, qIdx >= 0 && qIdx < totalQuestions else {
            print("⚠️ Answer index OOB: \(String(describing: currentAnswerIdx))")
            timer?.invalidate()
            gameState = .completed
            return
        }

        // ALTERNATIVE APPROACH: Direct comparison with explicit logic
        let correctAnswer = answerData.correctAnswers[qIdx]
        
        // Determine what the correct answer should be based on the puzzle logic
        // For memory previous single: compare current symbol with previous symbol
        guard let puzzleData = parsedPuzzleData,
              currentQuestionIndex > 0,
              currentQuestionIndex < puzzleData.questionSequence.count else {
            print("⚠️ Invalid question index or puzzle data")
            return
        }
        
        let currentSymbol = puzzleData.questionSequence[currentQuestionIndex].value
        let previousSymbol = puzzleData.questionSequence[currentQuestionIndex - 1].value
        let actuallyIsSame = (currentSymbol == previousSymbol)
        
        // Check if user's answer matches the actual relationship
        let isCorrect = (isSame == actuallyIsSame)
        
        // Debug logging
        print("🔍 DEBUG: Question \(qIdx+1)")
        print("🔍 Current symbol: \(currentSymbol), Previous symbol: \(previousSymbol)")
        print("🔍 Actually same? \(actuallyIsSame)")
        print("🔍 User said: \(isSame ? "SAME" : "DIFFERENT")")
        print("🔍 Expected answer from data: \(correctAnswer)")
        print("🔍 Result: \(isCorrect ? "✅ CORRECT" : "❌ INCORRECT")")
        
        // Store the user's answer as 1 for SAME, 0 for DIFFERENT
        let userAnswer = isSame ? 1 : 0

        // Write into the exact slot (don't append)
        userAnswers[qIdx] = userAnswer

        lastAnswerCorrect = isCorrect
        if isCorrect {
            currentScore += calculateAdaptiveScore()
            if let cap = parsedAnswerData?.maxScore {
                currentScore = min(currentScore, cap)
            }
            currentStreak += 1
        } else {
            currentStreak = 0
            currentHearts = max(0, currentHearts - 1)
        }

        adaptationInfo = difficultyManager.recordPerformance(
            puzzleType: "memorySingle",
            isCorrect: isCorrect,
            timeSpent: 4.0 - Double(timeRemaining % 4),
            difficulty: currentDifficultyLevel,
            streak: currentStreak,
            livesRemaining: currentHearts,
            gameScore: currentScore,
            challengesCompleted: 1
        )
        if let info = adaptationInfo, info.shouldNotify {
            currentDifficultyLevel = info.level
            showAdaptationNotification = true
        }

        playAdaptiveFeedbackSound(isCorrect: isCorrect)
        showFeedback = true

        DispatchQueue.main.asyncAfter(deadline: .now() + 1.0) {
            showFeedback = false
            withAnimation(.easeInOut(duration: 0.6)) { animationKey += 1 }

            if qIdx == totalQuestions - 1 {
                // Last question answered: finish now
                timer?.invalidate()
                gameState = .completed
            } else {
                currentQuestionIndex += 1
                handleAdaptiveQuestionDisplay()
            }
        }
    }
    
    private func handleAdaptiveQuestionDisplay() {
        guard let puzzleData = parsedPuzzleData,
              currentQuestionIndex < puzzleData.questionSequence.count else { return }
        
        let currentStep = puzzleData.questionSequence[currentQuestionIndex]
        
        // Reset visual states
        isRevealed = false
        showInterference = false
        showDistractors = false
        
        if puzzleData.adaptiveConfig.memoryInterference && !currentStep.interferencePattern.isEmpty {
            showInterference = true
            DispatchQueue.main.asyncAfter(deadline: .now() + 0.5) { showInterference = false }
        }
        if currentStep.hasDistractor && puzzleData.adaptiveConfig.visualDistractors {
            showDistractors = true
        }
        if puzzleData.adaptiveConfig.delayedReveal && currentStep.delayMs > 0 {
            DispatchQueue.main.asyncAfter(deadline: .now() + currentStep.delayMs / 1000.0) {
                isRevealed = true
            }
        } else {
            isRevealed = true
        }
    }
    
    // How many points max can any single correct answer be worth?
    private func perQuestionCap() -> Int {
        let tq = parsedAnswerData?.totalQuestions ?? max(1, totalQuestions)
        let declaredMax = parsedAnswerData?.maxScore ?? (tq * 10) // sane fallback
        return max(1, declaredMax / max(1, tq))
    }

    // Keep your adaptive multipliers, but budget them to the per-question cap
    private func calculateAdaptiveScore() -> Int {
        // Original adaptive logic (your weights)
        let rawAdaptive: Float = {
            guard let c = parsedPuzzleData?.adaptiveConfig else { return 10 }
            let base: Float = 10
            var s = base * (Float(c.cognitiveLoad) * 0.2 + 1.0)
            if c.visualDistractors   { s *= 1.2 }
            if c.memoryInterference  { s *= 1.3 }
            if c.delayedReveal       { s *= 1.1 }
            if c.multiModalStimuli   { s *= 1.2 }
            return s
        }()

        // Budget to avoid 75/60 situations
        let cap = Float(perQuestionCap())
        return max(1, Int(min(rawAdaptive, cap).rounded()))
    }

    
    private func formatTime(_ seconds: Int) -> String {
        let minutes = seconds / 60
        let remainingSeconds = seconds % 60
        return String(format: "%d:%02d", minutes, remainingSeconds)
    }
    
    private func playAdaptiveFeedbackSound(isCorrect: Bool) {
        let systemSoundID: SystemSoundID = isCorrect ? 1057 : 1053
        AudioServicesPlaySystemSound(systemSoundID)
        
        let impactStyle: UIImpactFeedbackGenerator.FeedbackStyle = {
            let cognitiveLoad = parsedPuzzleData?.adaptiveConfig.cognitiveLoad ?? 1
            if cognitiveLoad >= 4 {
                return isCorrect ? .medium : .heavy
            } else {
                return isCorrect ? .light : .medium
            }
        }()
        
        UIImpactFeedbackGenerator(style: impactStyle).impactOccurred()
    }
}

// MARK: - Adaptive Header/Components (unchanged UI, kept for drop-in)
struct AdaptiveMemoryHeader: View {
    let difficultyLevel: String
    let adaptiveConfig: AdaptiveMemoryConfig?
    let timer: String
    let score: Int
    let onBack: () -> Void
    
    var body: some View {
        HStack {
            Button(action: onBack) {
                HStack {
                    Text("||").font(.system(size: 16, weight: .bold)).foregroundColor(.white)
                }
                .frame(width: 48, height: 48)
                .background(Color(red: 0.23, green: 0.51, blue: 0.96))
                .cornerRadius(8)
            }
            Spacer()
            VStack {
                Text(difficultyLevel).font(.system(size: 16, weight: .bold)).foregroundColor(.white)
                if let config = adaptiveConfig {
                    HStack(spacing: 4) {
                        if config.visualDistractors { Text("👁️").font(.system(size: 10)) }
                        if config.memoryInterference { Text("🧠").font(.system(size: 10)) }
                        if config.delayedReveal { Text("ⱕ️").font(.system(size: 10)) }
                        if config.multiModalStimuli { Text("🎨").font(.system(size: 10)) }
                    }
                }
            }
            Spacer()
            HStack(spacing: 16) {
                HStack(spacing: 4) {
                    Text("TIME").font(.system(size: 12, weight: .bold))
                    Text(timer).font(.system(size: 12, weight: .bold))
                }
                .foregroundColor(Color(red: 0.12, green: 0.23, blue: 0.54))
                .padding(.horizontal, 12).padding(.vertical, 6)
                .background(Color.white.opacity(0.9)).cornerRadius(8)
                
                HStack(spacing: 4) {
                    Text("SCORE").font(.system(size: 12, weight: .bold))
                    Text("\(score)").font(.system(size: 12, weight: .bold))
                }
                .foregroundColor(Color(red: 0.12, green: 0.23, blue: 0.54))
                .padding(.horizontal, 12).padding(.vertical, 6)
                .background(Color.white.opacity(0.9)).cornerRadius(8)
            }
        }
        .padding(.top, 80).padding(.horizontal, 16)
    }
}

struct AdaptiveFeaturesCard: View {
    let config: AdaptiveMemoryConfig
    var body: some View {
        VStack(spacing: 16) {
            Text("Challenge Features").font(.headline).fontWeight(.bold)
                .foregroundColor(Color(red: 0.12, green: 0.23, blue: 0.54))
            VStack(alignment: .leading, spacing: 8) {
                if config.cognitiveLoad > 3 { MpsFeatureRow(icon: "🧠", text: "High Cognitive Load") }
                if config.visualDistractors { MpsFeatureRow(icon: "👁️", text: "Visual Distractors") }
                if config.memoryInterference { MpsFeatureRow(icon: "⚡", text: "Memory Interference") }
                if config.delayedReveal { MpsFeatureRow(icon: "ⱕ️", text: "Delayed Reveal") }
                if config.multiModalStimuli { MpsFeatureRow(icon: "🎨", text: "Multi-Modal Stimuli") }
            }
        }
        .padding(16).background(Color.white.opacity(0.9)).cornerRadius(12)
    }
}

struct MpsFeatureRow: View {
    let icon: String
    let text: String
    var body: some View {
        HStack {
            Text(icon).font(.system(size: 12))
            Text(text).font(.system(size: 12))
                .foregroundColor(Color(red: 0.12, green: 0.23, blue: 0.54))
        }
    }
}

struct AdaptiveSymbolLegend: View {
    let symbols: [String: String]
    let symbolColors: [String: Color]
    let config: AdaptiveMemoryConfig
    var body: some View {
        VStack(spacing: 16) {
            Text("Symbols").font(.headline).fontWeight(.bold)
                .foregroundColor(Color(red: 0.12, green: 0.23, blue: 0.54))
            HStack(spacing: 40) {
                ForEach(Array(symbols.keys.sorted()), id: \.self) { key in
                    VStack(spacing: 8) {
                        ZStack {
                            RoundedRectangle(cornerRadius: 8).fill(Color.white)
                                .frame(width: symbolSize, height: symbolSize)
                            Text(symbols[key] ?? "")
                                .font(.system(size: symbolFontSize))
                                .foregroundColor(symbolColors[key] ?? .purple)
                        }
                        Text("Value \(key)").font(.caption)
                            .foregroundColor(Color(red: 0.12, green: 0.23, blue: 0.54))
                    }
                }
            }
            if config.multiModalStimuli {
                Text("Note: Symbols may change appearance based on modality")
                    .font(.caption2)
                    .foregroundColor(Color(red: 0.12, green: 0.23, blue: 0.54).opacity(0.7))
            }
        }
        .padding(16).background(Color.white.opacity(0.9)).cornerRadius(12)
    }
    private var symbolSize: CGFloat { config.cognitiveLoad >= 4 ? 50 : 60 }
    private var symbolFontSize: CGFloat { config.cognitiveLoad >= 4 ? 24 : 32 }
}

struct AdaptiveInstructionsList: View {
    let steps: [String]
    let tip: String
    let config: AdaptiveMemoryConfig
    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            ForEach(Array(steps.enumerated()), id: \.offset) { _, step in
                Text(step).font(.system(size: 14))
                    .foregroundColor(Color(red: 0.12, green: 0.23, blue: 0.54))
            }
            Spacer(minLength: 12)
            Text("💡 \(tip)").font(.system(size: 14, weight: .medium))
                .foregroundColor(Color(red: 0.12, green: 0.23, blue: 0.54))
                .padding(.vertical, 8).padding(.horizontal, 12)
                .background(Color.yellow.opacity(0.2)).cornerRadius(8)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(16).background(Color.white.opacity(0.9)).cornerRadius(12)
    }
}

struct AdaptiveProgressIndicator: View {
    let currentQuestion: Int
    let totalQuestions: Int
    let cognitiveLoad: Int
    var body: some View {
        ZStack {
            Circle().fill(progressColor).frame(width: circleSize, height: circleSize)
            VStack(spacing: 2) {
                Text("\(min(max(1, currentQuestion), max(1, totalQuestions)))")
                    .font(.system(size: numberFontSize, weight: .bold)).foregroundColor(.white)
                if cognitiveLoad >= 4 {
                    Text("L\(cognitiveLoad)").font(.system(size: 8)).foregroundColor(.white.opacity(0.8))
                }
            }
        }
    }
    private var circleSize: CGFloat { cognitiveLoad >= 4 ? 40 : 48 }
    private var numberFontSize: CGFloat { cognitiveLoad >= 4 ? 14 : 18 }
    private var progressColor: Color {
        switch cognitiveLoad {
        case 1,2: return Color(red: 0.06, green: 0.72, blue: 0.51)
        case 3: return Color(red: 0.04, green: 0.62, blue: 0.41)
        case 4,5: return Color(red: 0.02, green: 0.52, blue: 0.31)
        default: return Color(red: 0.06, green: 0.72, blue: 0.51)
        }
    }
}

struct AdaptiveSymbolDisplay: View {
    let step: AdaptiveMemoryStep
    let symbols: [String: String]
    let symbolColors: [String: Color]
    let adaptiveConfig: AdaptiveMemoryConfig?
    let isRevealed: Bool
    let showInterference: Bool
    let showDistractors: Bool
    let animationKey: Int
    
    private var shouldShowSymbol: Bool { step.isFirstStep || isRevealed }
    
    var body: some View {
        ZStack {
            if showInterference && !step.interferencePattern.isEmpty {
                HStack(spacing: 8) {
                    ForEach(step.interferencePattern, id: \.self) { _ in
                        ZStack {
                            RoundedRectangle(cornerRadius: 16)
                                .fill(shouldShowSymbol ? Color.white : Color.gray.opacity(0.3))
                                .frame(width: symbolDisplaySize, height: symbolDisplaySize)
                                .shadow(color: .black.opacity(0.15), radius: 4, x: 0, y: 2)
                            if shouldShowSymbol {
                                let symbolKey = getAdaptiveSymbolKey()
                                Text(symbols[symbolKey] ?? symbols[String(step.value)] ?? "")
                                    .font(.system(size: symbolFontSize))
                                    .foregroundColor(symbolColors[symbolKey] ?? symbolColors[String(step.value)] ?? .black)
                            }
                            if showDistractors && adaptiveConfig?.visualDistractors == true {
                                DistractorOverlay()
                            }
                        }
                        .opacity(shouldShowSymbol ? 1.0 : 0.5)
                        .id(animationKey)
                        .transition(.asymmetric(
                            insertion: .move(edge: .trailing).combined(with: .opacity),
                            removal: .move(edge: .leading).combined(with: .opacity)
                        ))
                    }
                }
                .opacity(0.5)
            }
            
            ZStack {
                RoundedRectangle(cornerRadius: 16)
                    .fill(isRevealed ? Color.white : Color.gray.opacity(0.3))
                    .frame(width: symbolDisplaySize, height: symbolDisplaySize)
                    .shadow(color: .black.opacity(0.15), radius: 4, x: 0, y: 2)
                
                if isRevealed {
                    let symbolKey = getAdaptiveSymbolKey()
                    Text(symbols[symbolKey] ?? symbols[String(step.value)] ?? "")
                        .font(.system(size: symbolFontSize))
                        .foregroundColor(symbolColors[symbolKey] ?? symbolColors[String(step.value)] ?? .black)
                }
                if showDistractors && adaptiveConfig?.visualDistractors == true {
                    DistractorOverlay()
                }
            }
            .opacity(isRevealed ? 1.0 : 0.5)
            .id(animationKey)
            .transition(.asymmetric(
                insertion: .move(edge: .trailing).combined(with: .opacity),
                removal: .move(edge: .leading).combined(with: .opacity)
            ))
        }
    }
    private var symbolDisplaySize: CGFloat {
        let cl = adaptiveConfig?.cognitiveLoad ?? 1
        switch cl { case 1,2: return 200; case 3: return 180; case 4,5: return 160; default: return 200 }
    }
    private var symbolFontSize: CGFloat { symbolDisplaySize * 0.6 }
    private func getAdaptiveSymbolKey() -> String {
        guard let config = adaptiveConfig else { return String(step.value) }
        switch step.modalityType {
        case "color": return String(step.value)
        case "shape": return String(step.value + 2)
        case "texture": return String(step.value + 4)
        default: return String(step.value)
        }
    }
}

struct DistractorOverlay: View {
    var body: some View {
        ZStack {
            ForEach(0..<4, id: \.self) { index in
                let positions: [(CGFloat, CGFloat)] = [(-20,-20),(20,-20),(-20,20),(20,20)]
                let pos = positions[index]
                Circle().fill(Color.yellow.opacity(0.3)).frame(width: 20, height: 20).offset(x: pos.0, y: pos.1)
                    .overlay(Text("✦").font(.system(size: 12)).foregroundColor(.yellow).offset(x: pos.0, y: pos.1))
            }
        }
        .opacity(0.4)
    }
}

struct AdaptiveQuestionText: View {
    let config: AdaptiveMemoryConfig?
    let currentQuestion: Int
    var body: some View {
        VStack(spacing: 8) {
            if currentQuestion == 1 {
                Text("Is this symbol the SAME as the previous one?")
                    .font(.system(size: 18)).foregroundColor(.white).multilineTextAlignment(.center).lineSpacing(4)
            } else if currentQuestion > 1 {
                if config?.memoryInterference == true {
                    Text("Focus! Does this symbol match\nthe previous symbol?\n(Ignore distractions)")
                        .font(.system(size: 18)).foregroundColor(.white).multilineTextAlignment(.center).lineSpacing(4)
                } else {
                    Text("Does this symbol match the\nprevious symbol?")
                        .font(.system(size: 18)).foregroundColor(.white).multilineTextAlignment(.center).lineSpacing(4)
                }
                if let config = config, config.cognitiveLoad >= 4 {
                    Text("High Concentration Required")
                        .font(.system(size: 12, weight: .medium)).foregroundColor(.orange).padding(.top, 4)
                }
            }
        }
    }
}

struct AdaptiveFeedbackOverlay: View {
    let isCorrect: Bool
    let cognitiveLoad: Int
    @State private var scale: CGFloat = 0.3
    @State private var opacity: Double = 0.0
    var body: some View {
        ZStack {
            Circle().fill(isCorrect ? Color(red: 0.06, green: 0.72, blue: 0.51) : Color(red: 0.94, green: 0.27, blue: 0.27))
                .frame(width: feedbackSize, height: feedbackSize)
            VStack {
                Text(isCorrect ? "✓" : "✗").font(.system(size: iconSize, weight: .bold)).foregroundColor(.white)
                if cognitiveLoad >= 4 {
                    Text(isCorrect ? "EXCELLENT" : "FOCUS").font(.system(size: 8, weight: .bold)).foregroundColor(.white)
                }
            }
        }
        .scaleEffect(scale).opacity(opacity)
        .onAppear {
            withAnimation(.spring(response: 0.6, dampingFraction: 0.8)) {
                scale = 1.0; opacity = 1.0
            }
        }
    }
    private var feedbackSize: CGFloat { cognitiveLoad >= 4 ? 100 : 120 }
    private var iconSize: CGFloat { cognitiveLoad >= 4 ? 40 : 60 }
}

struct AdaptiveAnswerButtons: View {
    let onAnswer: (Bool) -> Void
    let cognitiveLoad: Int
    var body: some View {
        HStack(spacing: 16) {
            Button(action: { onAnswer(false) }) {
                Text("DIFFERENT").font(.system(size: buttonFontSize, weight: .bold)).foregroundColor(.white)
                    .frame(maxWidth: .infinity).frame(height: buttonHeight)
                    .background(buttonColor).cornerRadius(12)
            }
            Button(action: { onAnswer(true) }) {
                Text("SAME").font(.system(size: buttonFontSize, weight: .bold)).foregroundColor(.white)
                    .frame(maxWidth: .infinity).frame(height: buttonHeight)
                    .background(buttonColor).cornerRadius(12)
            }
        }
    }
    private var buttonHeight: CGFloat { cognitiveLoad >= 4 ? 56 : 64 }
    private var buttonFontSize: CGFloat { cognitiveLoad >= 4 ? 16 : 20 }
    private var buttonColor: Color {
        switch cognitiveLoad {
        case 1,2: return Color(red: 0.23, green: 0.51, blue: 0.96)
        case 3:   return Color(red: 0.20, green: 0.46, blue: 0.86)
        case 4,5: return Color(red: 0.17, green: 0.41, blue: 0.76)
        default:  return Color(red: 0.23, green: 0.51, blue: 0.96)
        }
    }
}

struct AdaptiveStatsCard: View {
    let score: Int
    let adaptiveConfig: AdaptiveMemoryConfig?
    let userAnswers: [Int]
    let correctAnswers: [Int]
    let totalQuestions: Int
    let maxScore: Int
    var body: some View {
        VStack(spacing: 16) {
            VStack(spacing: 8) {
                Text("Final Score").font(.system(size: 18, weight: .bold))
                    .foregroundColor(Color(red: 0.12, green: 0.23, blue: 0.54))
                if maxScore > 0 {
                    Text("\(score) / \(maxScore)").font(.system(size: 32, weight: .bold))
                        .foregroundColor(Color(red: 0.06, green: 0.72, blue: 0.51))
                }
            }
            let correctCount = zip(userAnswers, correctAnswers).filter { $0.0 == $0.1 }.count
            let percentage = totalQuestions > 0 ? Int((Double(correctCount) / Double(totalQuestions)) * 100) : 0
            VStack(spacing: 4) {
                Text("Correct Answers: \(correctCount) / \(totalQuestions)").font(.body)
                    .foregroundColor(Color(red: 0.12, green: 0.23, blue: 0.54))
                Text("Accuracy: \(percentage)%").font(.body)
                    .foregroundColor(Color(red: 0.12, green: 0.23, blue: 0.54))
                if let config = adaptiveConfig {
                    Divider()
                    Text("Adaptive Features Completed:").font(.system(size: 14, weight: .medium))
                        .foregroundColor(Color(red: 0.12, green: 0.23, blue: 0.54))
                    HStack(spacing: 8) {
                        if config.visualDistractors { Text("👁️").font(.system(size: 16)) }
                        if config.memoryInterference { Text("🧠").font(.system(size: 16)) }
                        if config.delayedReveal { Text("ⱕ️").font(.system(size: 16)) }
                        if config.multiModalStimuli { Text("🎨").font(.system(size: 16)) }
                    }.padding(.top, 4)
                    Text("Cognitive Load: \(config.cognitiveLoad)/5").font(.system(size: 12))
                        .foregroundColor(Color(red: 0.12, green: 0.23, blue: 0.54).opacity(0.8))
                }
            }
        }
        .padding(24).background(Color.white.opacity(0.9)).cornerRadius(12)
    }
}

// MARK: - Parsing (unchanged except kept together)
private func parseAdaptiveMemoryPuzzleData(_ jsonString: String) -> AdaptiveMemoryPuzzleData? {
    guard let data = jsonString.data(using: .utf8),
          let json = try? JSONSerialization.jsonObject(with: data) as? [String: Any] else {
        print("Failed to parse adaptive puzzle JSON")
        return nil
    }
    guard let binaryArray = json["binarySequence"] as? [Int] else {
        print("Failed to parse binarySequence")
        return nil
    }
    guard let questionArray = json["questionSequence"] as? [[String: Any]] else {
        print("Failed to parse questionSequence")
        return nil
    }
    let questionSequence = questionArray.compactMap { stepDict -> AdaptiveMemoryStep? in
        guard let step = stepDict["step"] as? Int,
              let value = stepDict["value"] as? Int,
              let isFirstStep = stepDict["isFirstStep"] as? Bool else { return nil }
        let hasDistractor = stepDict["hasDistractor"] as? Bool ?? false
        let delayMs = stepDict["delayMs"] as? TimeInterval ?? 0
        let interferencePattern = stepDict["interferencePattern"] as? [Int] ?? []
        let modalityType = stepDict["modalityType"] as? String ?? "standard"
        return AdaptiveMemoryStep(
            step: step,
            value: value,
            isFirstStep: isFirstStep,
            hasDistractor: hasDistractor,
            delayMs: delayMs,
            interferencePattern: interferencePattern,
            modalityType: modalityType
        )
    }
    guard let instructionsDict = json["instructions"] as? [String: Any],
          let title = instructionsDict["title"] as? String,
          let description = instructionsDict["description"] as? String,
          let stepsArray = instructionsDict["steps"] as? [String],
          let tip = instructionsDict["tip"] as? String else {
        print("Failed to parse instructions")
        return nil
    }
    let instructions = AdaptiveMemoryPuzzleData.AdaptiveInstructions(
        title: title, description: description, steps: stepsArray, tip: tip
    )
    guard let configDict = json["adaptiveConfig"] as? [String: Any],
          let name = configDict["name"] as? String,
          let configDescription = configDict["description"] as? String,
          let visualDistractors = configDict["visualDistractors"] as? Bool,
          let delayedReveal = configDict["delayedReveal"] as? Bool,
          let memoryInterference = configDict["memoryInterference"] as? Bool,
          let multiModalStimuli = configDict["multiModalStimuli"] as? Bool,
          let cognitiveLoad = configDict["cognitiveLoad"] as? Int else {
        print("Failed to parse adaptive config")
        return nil
    }
    let adaptiveConfig = AdaptiveMemoryConfig(
        visualDistractors: visualDistractors,
        delayedReveal: delayedReveal,
        memoryInterference: memoryInterference,
        multiModalStimuli: multiModalStimuli,
        cognitiveLoad: cognitiveLoad,
        name: name,
        description: configDescription
    )
    guard let totalSteps = json["totalSteps"] as? Int,
          let totalQuestions = json["totalQuestions"] as? Int,
          let difficulty = json["difficulty"] as? String else {
        print("Failed to parse puzzle metadata")
        return nil
    }
    return AdaptiveMemoryPuzzleData(
        binarySequence: binaryArray,
        questionSequence: questionSequence,
        instructions: instructions,
        totalSteps: totalSteps,
        totalQuestions: totalQuestions,
        adaptiveConfig: adaptiveConfig,
        difficulty: difficulty
    )
}

func parseMemoryAnswerData(_ jsonString: String) -> MemoryAnswerData? {
    guard let data = jsonString.data(using: .utf8),
          let json = try? JSONSerialization.jsonObject(with: data) as? [String: Any] else {
        print("❌ Failed to parse answer JSON")
        return nil
    }
    guard let answerArray = json["answerSequence"] as? [Int],
          let totalQuestions = json["totalQuestions"] as? Int,
          let maxScore = json["maxScore"] as? Int else {
        print("❌ Failed to parse answer data")
        return nil
    }
    return MemoryAnswerData(
        answerSequence: answerArray,
        correctAnswers: answerArray,
        totalQuestions: totalQuestions,
        maxScore: maxScore
    )
}

// MARK: - Puzzle Data Extensions for Adaptive Views
extension Puzzle {
    
    var adaptiveMemoryPreviousSinglePuzzleData: AdaptiveMemoryPuzzleData? {
        guard let data = question.data(using: .utf8),
              let json = try? JSONSerialization.jsonObject(with: data) as? [String: Any] else {
            print("❌ Failed to parse adaptive memory single JSON")
            return nil
        }
        guard let binaryArray = json["binarySequence"] as? [Int] else {
            print("❌ Failed to parse binarySequence")
            return nil
        }
        guard let questionArray = json["questionSequence"] as? [[String: Any]] else {
            print("❌ Failed to parse questionSequence")
            return nil
        }
        let questionSequence = questionArray.compactMap { stepDict -> AdaptiveMemoryStep? in
            guard let step = stepDict["step"] as? Int,
                  let value = stepDict["value"] as? Int,
                  let isFirstStep = stepDict["isFirstStep"] as? Bool else { return nil }
            let hasDistractor = stepDict["hasDistractor"] as? Bool ?? false
            let delayMs = stepDict["delayMs"] as? TimeInterval ?? 0
            let interferencePattern = stepDict["interferencePattern"] as? [Int] ?? []
            let modalityType = stepDict["modalityType"] as? String ?? "standard"
            return AdaptiveMemoryStep(
                step: step, value: value, isFirstStep: isFirstStep,
                hasDistractor: hasDistractor, delayMs: delayMs,
                interferencePattern: interferencePattern, modalityType: modalityType
            )
        }
        let instructions = AdaptiveMemoryPuzzleData.AdaptiveInstructions(
            title: "Adaptive Memory Previous Single",
            description: "Enhanced memory challenge with adaptive features",
            steps: [
                "1. Look at the first symbol and remember it",
                "2. For each new symbol, decide if it's SAME or DIFFERENT from the previous one",
                "3. Tap 'SAME' if it matches, 'DIFFERENT' if it doesn't",
                "4. Continue through the entire sequence"
            ],
            tip: "Focus on the immediate previous symbol"
        )
        var adaptiveConfig = AdaptiveMemoryConfig(
            visualDistractors: false, delayedReveal: false, memoryInterference: false, multiModalStimuli: false,
            cognitiveLoad: 1, name: "Medium", description: "Standard memory challenge"
        )
        if let configDict = json["adaptiveConfig"] as? [String: Any] {
            adaptiveConfig = AdaptiveMemoryConfig(
                visualDistractors: configDict["visualDistractors"] as? Bool ?? false,
                delayedReveal: configDict["delayedReveal"] as? Bool ?? false,
                memoryInterference: configDict["memoryInterference"] as? Bool ?? false,
                multiModalStimuli: configDict["multiModalStimuli"] as? Bool ?? false,
                cognitiveLoad: configDict["cognitiveLoad"] as? Int ?? 1,
                name: configDict["name"] as? String ?? "Medium",
                description: configDict["description"] as? String ?? "Standard memory challenge"
            )
        }
        return AdaptiveMemoryPuzzleData(
            binarySequence: binaryArray,
            questionSequence: questionSequence,
            instructions: instructions,
            totalSteps: json["totalSteps"] as? Int ?? binaryArray.count,
            totalQuestions: json["totalQuestions"] as? Int ?? (binaryArray.count - 1),
            adaptiveConfig: adaptiveConfig,
            difficulty: json["difficulty"] as? String ?? "Medium"
        )
    }
}

// MARK: - Misc Helper
private func generateDefaultOptions(for correctAnswer: Int) -> [Int] {
    var options = Set<Int>(); options.insert(correctAnswer)
    let variations = [0.7, 0.85, 1.15, 1.3]
    for v in variations {
        let option = Int(Double(correctAnswer) * v)
        if option > 0 && option != correctAnswer { options.insert(option) }
        if options.count >= 4 { break }
    }
    while options.count < 4 {
        let r = correctAnswer + Int.random(in: -100...100)
        if r > 0 && r != correctAnswer { options.insert(r) }
    }
    return Array(options).shuffled()
}
