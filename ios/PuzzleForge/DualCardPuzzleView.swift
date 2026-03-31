//
//  DualCardPuzzleView.swift
//  PuzzleForge
//
//  Created by Assistant on 8/17/25.
//

import SwiftUI
import Foundation

extension LocalMemoryPuzzleGenerator {
    static func generateDualTask(difficulty: String) -> (String, String) {
            let totalRounds: Int = {
                switch difficulty.lowercased() {
                case "easy": return 10
                case "medium": return 15
                case "hard": return 20
                default: return 15
                }
            }()
            
            let instructions: [String] = [
                "You'll see two cards - one for numbers, one for letters",
                "The active card will be highlighted",
                "For numbers: decide if the number is EVEN",
                "For letters: decide if the letter is a VOWEL",
                "The task switches between rounds"
            ]
            
            let dualTaskData: [String: Any] = [
                "type": "dual_task",
                "difficulty": difficulty,
                "totalRounds": totalRounds,
                "description": "Switch between checking if numbers are even and if letters are vowels",
                "instructions": instructions
            ]
            
            guard let questionData = try? JSONSerialization.data(withJSONObject: dualTaskData),
                  let questionString = String(data: questionData, encoding: .utf8) else {
                print("âŒ Failed to serialize dual task data")
                return ("", "")
            }
            
            return (questionString, "dual_task_complete")
        }
}

// MARK: - Adaptive Configuration
struct AdaptiveDualCardConfig {
    let totalRounds: Int
    let complexityLevel: Int
    let switchingPattern: String // "alternating", "random", "predictable"
    let cardComplexity: Int
    let name: String
}

// MARK: - Card Data Model
struct AdaptiveCardData {
    let letter: Character
    let digit: Int
    let isLetterFirst: Bool
    let textColor: Color
    
    var displayText: String {
        return isLetterFirst ? "\(letter)\(digit)" : "\(digit)\(letter)"
    }
}

// MARK: - Main View
struct DualCardPuzzleView: View {
    let difficulty: String
    let timer: String
    let hearts: Int
    let level: String
    
    let onSubmitAnswer: (Bool) -> Void
    let fetchNextPuzzle: (Int) -> Void
    let onBack: () -> Void
    
    // Adaptive difficulty state
    @State private var currentDifficultyLevel: DifficultyLevel
    @State private var adaptationInfo: AdaptationInfo?
    @State private var showAdaptationNotification = false
    @State private var competitiveInsight: CompetitiveInsight?
    
    // Game state
    @State private var timeLeft: Int
    @State private var isPaused = false
    @State private var currentHearts: Int
    @State private var gameCompleted = false
    @State private var gameStarted = true
    @State private var currentStreak = 0
    @State private var gamesPlayedThisSession = 0
    
    // Score tracking state
    @State private var totalScore = 0
    @State private var correctAnswers = 0
    @State private var totalAttempts = 0
    @State private var gameStartTime = Date()
    @State private var roundStartTimes: [Int: Date] = [:]
    @State private var reactionTimes: [TimeInterval] = []
    @State private var bestStreak = 0
    @State private var taskSwitchCount = 0
    @State private var taskSwitchAccuracy: [String: Int] = ["even": 0, "vowel": 0]
    
    // Session tracking
    @State private var sessionStartTime = Date()
    
    // Game flow state
    @State private var currentRound = 1
    @State private var isCheckingTopCard = true
    @State private var currentCardData: AdaptiveCardData
    @State private var showFeedback = false
    @State private var isCorrectAnswer = false
    @State private var showHint = false
    
    // Adaptive configuration
    private var adaptiveConfig: AdaptiveDualCardConfig {
        DualCardPuzzleView.generateAdaptiveDualCardConfig(currentDifficultyLevel)
    }
    
    init(difficulty: String = "Medium", timer: String, hearts: Int, level: String, onSubmitAnswer: @escaping (Bool) -> Void, fetchNextPuzzle: @escaping (Int) -> Void, onBack: @escaping () -> Void) {
        self.difficulty = difficulty
        self.timer = timer
        self.hearts = hearts
        self.level = level
        self.onSubmitAnswer = onSubmitAnswer
        self.fetchNextPuzzle = fetchNextPuzzle
        self.onBack = onBack
        
        // Initialize difficulty level
        let difficultyLevel = Self.getDifficultyLevel(for: difficulty)
        self._currentDifficultyLevel = State(initialValue: difficultyLevel)
        self._timeLeft = State(initialValue: difficultyLevel.timeLimit)
        self._currentHearts = State(initialValue: difficultyLevel.livesAllowed)
        
        // Initialize card data
        let config = Self.generateAdaptiveDualCardConfig(difficultyLevel)
        self._currentCardData = State(initialValue: Self.generateAdaptiveCardData(config))
    }
    
    var body: some View {
        ZStack {
            // Background gradient
            LinearGradient(
                colors: [Color(red: 0.54, green: 0.35, blue: 0.42), Color(red: 0.47, green: 0.29, blue: 0.36)],
                startPoint: .topLeading,
                endPoint: .bottomTrailing
            )
            .ignoresSafeArea()
            
            VStack(spacing: 0) {
                Spacer(minLength: 32)
                
                // Adaptive Header
                adaptiveHeaderView
                
                Spacer(minLength: 24)
                
                // Adaptation Notification
                if showAdaptationNotification {
                    adaptationNotificationView
                        .padding(.horizontal, 16)
                        .transition(.opacity.combined(with: .scale))
                }
                
                Spacer(minLength: 16)
                
                if !gameCompleted {
                    gameContentView
                } else {
                    completionView
                }
                
                Spacer()
            }
            .padding(.horizontal, 16)
        }
        .onAppear {
            initializeGame()
        }
        .onReceive(Timer.publish(every: 1, on: .main, in: .common).autoconnect()) { _ in
            handleTimer()
        }
    }
    
    // MARK: - Header View
    private var adaptiveHeaderView: some View {
        VStack(spacing: 12) {
            // Top row: Back button, level, lives, timer
            HStack {
                // Back button
                Button(action: {
                    gameCompleted = true
                    onBack()
                }) {
                    HStack(spacing: 8) {
                        Image(systemName: "chevron.left")
                        Text("||")
                    }
                    .foregroundColor(.white)
                    .padding(.horizontal, 12)
                    .padding(.vertical, 6)
                    .background(Color.blue)
                    .cornerRadius(8)
                }
                
                // Level info
                VStack(alignment: .leading) {
                    Text("Level \(level)")
                        .font(.caption)
                        .fontWeight(.bold)
                        .foregroundColor(.white)
                    
                    // Simplified progress bar
                    Rectangle()
                        .fill(Color.white.opacity(0.3))
                        .frame(width: 60, height: 4)
                        .overlay(
                            Rectangle()
                                .fill(Color.yellow)
                                .frame(width: 30, height: 4),
                            alignment: .leading
                        )
                        .cornerRadius(2)
                }
                
                Spacer()
                
                // Lives
                HStack(spacing: 4) {
                    ForEach(0..<currentDifficultyLevel.livesAllowed, id: \.self) { index in
                        Image(systemName: "heart.fill")
                            .foregroundColor(index < currentHearts ? Color.yellow : Color.gray)
                            .font(.caption)
                    }
                }
                
                // Timer
                let displayTimer = String(format: "%02d:%02d", timeLeft / 60, timeLeft % 60)
                let isUrgent = timeLeft <= 30
                
                Text(displayTimer)
                    .font(.caption)
                    .fontWeight(.bold)
                    .foregroundColor(isUrgent ? .red : .white)
                    .padding(.horizontal, 8)
                    .padding(.vertical, 4)
                    .background(Color.white.opacity(0.2))
                    .cornerRadius(6)
            }
            
            // Score and progress row
            HStack {
                Text("Score: \(totalScore)")
                    .font(.caption)
                    .fontWeight(.bold)
                    .foregroundColor(.yellow)
                
                Spacer()
                
                Text("🧠 \(currentDifficultyLevel.name)")
                    .font(.caption)
                    .foregroundColor(.white.opacity(0.8))
                
                Spacer()
                
                Text("\(currentRound)/\(adaptiveConfig.totalRounds)")
                    .font(.caption)
                    .foregroundColor(.white)
                
                if currentStreak > 1 {
                    Text("🔥 \(currentStreak)")
                        .font(.caption)
                        .fontWeight(.bold)
                        .foregroundColor(.orange)
                }
            }
            
            // Competitive insight
            if let insight = competitiveInsight {
                competitiveInsightView(insight)
            }
        }
    }
    
    // MARK: - Game Content
    private var gameContentView: some View {
        VStack(spacing: 24) {
            // Task indicator with adaptive info
            VStack(spacing: 8) {
                Text(isCheckingTopCard ? "Check if number is EVEN" : "Check if letter is VOWEL")
                    .font(.headline)
                    .fontWeight(.bold)
                    .foregroundColor(.yellow)
                
                Text("Switching pattern: \(adaptiveConfig.switchingPattern)")
                    .font(.caption)
                    .foregroundColor(.white.opacity(0.7))
                
                if taskSwitchCount > 0 {
                    Text("⚡ Task switches: \(taskSwitchCount)")
                        .font(.caption)
                        .foregroundColor(.white.opacity(0.7))
                }
            }
            
            // Top Card - Number Even Check
            cardView(
                title: "Is the number even?",
                isActive: isCheckingTopCard,
                showContent: isCheckingTopCard
            )
            
            // Bottom Card - Vowel Check
            cardView(
                title: "Is the letter a vowel?",
                isActive: !isCheckingTopCard,
                showContent: !isCheckingTopCard
            )
            
            // Feedback display
            if showFeedback {
                feedbackView
            }
            
            // Answer buttons
            if !gameCompleted && !showFeedback {
                answerButtonsView
            }
        }
    }
    
    private func cardView(title: String, isActive: Bool, showContent: Bool) -> some View {
        VStack(spacing: 8) {
            Text(title)
                .font(.caption)
                .fontWeight(.semibold)
                .foregroundColor(isActive ? .black : .gray)
            
            RoundedRectangle(cornerRadius: 12)
                .fill(isActive ? Color.white : Color.gray.opacity(0.7))
                .frame(height: 100)
                .overlay(
                    Group {
                        if showContent {
                            Text(currentCardData.displayText)
                                .font(.system(size: 36, weight: .bold))
                                .foregroundColor(currentCardData.textColor)
                        } else {
                            Text("—")
                                .font(.system(size: 24))
                                .foregroundColor(.gray)
                        }
                    }
                )
        }
        .padding(.horizontal, 24)
    }
    
    private var feedbackView: some View {
        VStack(spacing: 8) {
            Text(isCorrectAnswer ? "Correct! ✓" : "Wrong! ✗")
                .font(.title2)
                .fontWeight(.bold)
                .foregroundColor(isCorrectAnswer ? .green : .red)
            
            if isCorrectAnswer && !reactionTimes.isEmpty {
                let reactionTime = reactionTimes.last! * 1000
                Text("⚡ \(String(format: "%.0f", reactionTime))ms")
                    .font(.subheadline)
                    .foregroundColor(.white)
            }
        }
        .padding(.vertical, 16)
    }
    
    private var answerButtonsView: some View {
        HStack(spacing: 16) {
            Button(action: { handleAnswer(false) }) {
                Text("NO")
                    .font(.title2)
                    .fontWeight(.bold)
                    .foregroundColor(.white)
                    .frame(maxWidth: .infinity)
                    .frame(height: 56)
                    .background(Color.blue)
                    .cornerRadius(28)
            }
            
            Button(action: { handleAnswer(true) }) {
                Text("YES")
                    .font(.title2)
                    .fontWeight(.bold)
                    .foregroundColor(.white)
                    .frame(maxWidth: .infinity)
                    .frame(height: 56)
                    .background(Color.blue)
                    .cornerRadius(28)
            }
        }
        .padding(.horizontal, 20)
    }
    
    // MARK: - Completion View
    private var completionView: some View {
        VStack(spacing: 24) {
            Text("Adaptive Dual-Task Complete!")
                .font(.title)
                .fontWeight(.bold)
                .foregroundColor(.yellow)
            
            VStack(spacing: 12) {
                Text("Final Score: \(totalScore)")
                    .font(.title2)
                    .foregroundColor(.white)
                
                Text("Accuracy: \(correctAnswers)/\(totalAttempts)")
                    .font(.subheadline)
                    .foregroundColor(.white.opacity(0.8))
                
                if !taskSwitchAccuracy.isEmpty {
                    let evenAcc = taskSwitchAccuracy["even"] ?? 0
                    let vowelAcc = taskSwitchAccuracy["vowel"] ?? 0
                    Text("Even: \(evenAcc) • Vowel: \(vowelAcc)")
                        .font(.caption)
                        .foregroundColor(.white.opacity(0.8))
                }
                
                Text("Difficulty: \(currentDifficultyLevel.name)")
                    .font(.caption)
                    .foregroundColor(.white.opacity(0.8))
            }
            
            Button("Continue") {
                let isSuccess = currentRound >= adaptiveConfig.totalRounds
                onSubmitAnswer(isSuccess)
                fetchNextPuzzle(totalScore)
            }
            .font(.headline)
            .fontWeight(.semibold)
            .foregroundColor(.white)
            .frame(maxWidth: .infinity)
            .frame(height: 50)
            .background(Color.green)
            .cornerRadius(25)
            .padding(.horizontal, 40)
        }
    }
    
    // MARK: - Adaptation Notification
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
                Text("Adjusted to \(info.level.name) based on your performance")
                    .font(.caption)
                    .foregroundColor(.gray)
            }
        }
        .padding()
        .background(Color.white)
        .cornerRadius(12)
        .shadow(radius: 4)
    }
    
    // MARK: - Competitive Insight
    private func competitiveInsightView(_ insight: CompetitiveInsight) -> some View {
        HStack {
            Image(systemName: "trophy.fill")
                .foregroundColor(.yellow)
                .font(.caption)
            
            Text(insight.ranking)
                .font(.caption)
                .foregroundColor(.white.opacity(0.9))
            
            Spacer()
        }
        .padding(.horizontal, 8)
        .padding(.vertical, 4)
        .background(Color.white.opacity(0.1))
        .cornerRadius(6)
    }
    
    // MARK: - Game Logic
    private func initializeGame() {
        gameStartTime = Date()
        sessionStartTime = Date()
        roundStartTimes[currentRound] = Date()
        
        // Load competitive insight
        loadCompetitiveInsight()
    }
    
    private func handleTimer() {
        if timeLeft > 0 && !gameCompleted && !isPaused {
            timeLeft -= 1
        } else if timeLeft == 0 && !gameCompleted {
            // Time's up
            recordAdaptivePerformance(
                isCorrect: false,
                timeSpent: TimeInterval(currentDifficultyLevel.timeLimit),
                streak: 0,
                livesRemaining: 0
            )
            
            gameCompleted = true
            onSubmitAnswer(false)
        }
    }
    
    private func handleAnswer(_ userAnsweredYes: Bool) {
        if gameCompleted || showFeedback { return }
        
        totalAttempts += 1
        let roundStartTime = roundStartTimes[currentRound] ?? Date()
        let reactionTime = Date().timeIntervalSince(roundStartTime)
        reactionTimes.append(reactionTime)
        
        let correct = checkAdaptiveAnswer(isCheckingTopCard, currentCardData, userAnsweredYes, adaptiveConfig)
        
        isCorrectAnswer = correct
        showFeedback = true
        gamesPlayedThisSession += 1
        
        if correct {
            correctAnswers += 1
            currentStreak += 1
            if currentStreak > bestStreak {
                bestStreak = currentStreak
            }
            
            // Track task-specific accuracy
            let taskType = isCheckingTopCard ? "even" : "vowel"
            taskSwitchAccuracy[taskType] = (taskSwitchAccuracy[taskType] ?? 0) + 1
            
            let score = calculateScore(
                isCorrect: true,
                reactionTime: reactionTime,
                currentStreak: currentStreak,
                totalSwitches: taskSwitchCount,
                roundsCompleted: currentRound
            )
            totalScore += score
        } else {
            currentHearts -= 1
            currentStreak = 0
        }
        
        // Record performance for adaptation
        recordAdaptivePerformance(
            isCorrect: correct,
            timeSpent: reactionTime,
            streak: currentStreak,
            livesRemaining: currentHearts
        )
        
        // Schedule next round
        DispatchQueue.main.asyncAfter(deadline: .now() + 1.0) {
            showFeedback = false
            
            if currentHearts <= 0 {
                gameCompleted = true
                onSubmitAnswer(false)
            } else if currentRound >= adaptiveConfig.totalRounds {
                gameCompleted = true
                onSubmitAnswer(true)
            } else {
                moveToNextRound()
            }
        }
    }
    
    private func moveToNextRound() {
        currentRound += 1
        let wasCheckingTopCard = isCheckingTopCard
        
        // Adaptive task switching based on config
        isCheckingTopCard = switch adaptiveConfig.switchingPattern {
        case "alternating": !isCheckingTopCard
        case "random": Bool.random()
        case "predictable": (currentRound % 2 == 1)
        default: !isCheckingTopCard
        }
        
        // Track task switching
        if wasCheckingTopCard != isCheckingTopCard {
            taskSwitchCount += 1
        }
        
        currentCardData = Self.generateAdaptiveCardData(adaptiveConfig)
        roundStartTimes[currentRound] = Date()
    }
    
    private func loadCompetitiveInsight() {
        // Simulate loading competitive insight
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.5) {
            competitiveInsight = CompetitiveInsight(
                percentile: 67,
                ranking: "Silver",
                improvement: "+12% this week",
                globalAverage: 72.3,
                userScore: 78.9
            )
        }
    }
    
    // MARK: - Adaptive Functions
    private func recordAdaptivePerformance(isCorrect: Bool, timeSpent: TimeInterval, streak: Int, livesRemaining: Int) {
        // Simulate adaptive performance recording
        let performanceScore = calculatePerformanceScore(isCorrect: isCorrect, timeSpent: timeSpent, streak: streak)
        
        // Determine if difficulty should change
        if gamesPlayedThisSession > 0 && gamesPlayedThisSession % 5 == 0 {
            let shouldIncrease = performanceScore > 0.8 && correctAnswers > totalAttempts * 3/4
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
        let timeTarget = 2.0 // Target 2 seconds per response
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
            timeLeft = currentDifficultyLevel.timeLimit
        }
    }
    
    private func calculateScore(isCorrect: Bool, reactionTime: TimeInterval, currentStreak: Int, totalSwitches: Int, roundsCompleted: Int) -> Int {
        if !isCorrect { return 0 }
        
        let basePoints = 50
        var score = basePoints
        
        // Time bonus
        if reactionTime < 1.5 {
            score += Int(Double(basePoints) * 0.3)
        } else if reactionTime < 2.0 {
            score += Int(Double(basePoints) * 0.15)
        }
        
        // Streak bonus
        score += min(currentStreak * 5, 50)
        
        // Difficulty multiplier
        let difficultyMultiplier = 1.0 + (Double(currentDifficultyLevel.index) * 0.2)
        score = Int(Double(score) * difficultyMultiplier)
        
        return score
    }
    
    // MARK: - Static Helper Functions
    static func getDifficultyLevel(for difficulty: String) -> DifficultyLevel {
        return DifficultyLevel.create(name: difficulty)
    }

    static func getDifficultyLevel(for index: Int) -> DifficultyLevel {
        return DifficultyLevel.create(index: index)
    }
    
    static func generateAdaptiveDualCardConfig(_ difficulty: DifficultyLevel) -> AdaptiveDualCardConfig {
        switch difficulty.index {
        case 0: // Beginner
            return AdaptiveDualCardConfig(
                totalRounds: 8,
                complexityLevel: 1,
                switchingPattern: "predictable",
                cardComplexity: 1,
                name: "Beginner"
            )
        case 1: // Easy
            return AdaptiveDualCardConfig(
                totalRounds: 10,
                complexityLevel: 2,
                switchingPattern: "alternating",
                cardComplexity: 2,
                name: "Easy"
            )
        case 2: // Medium
            return AdaptiveDualCardConfig(
                totalRounds: 15,
                complexityLevel: 3,
                switchingPattern: "alternating",
                cardComplexity: 3,
                name: "Medium"
            )
        case 3: // Hard
            return AdaptiveDualCardConfig(
                totalRounds: 20,
                complexityLevel: 4,
                switchingPattern: "random",
                cardComplexity: 4,
                name: "Hard"
            )
        case 4: // Expert
            return AdaptiveDualCardConfig(
                totalRounds: 25,
                complexityLevel: 5,
                switchingPattern: "random",
                cardComplexity: 5,
                name: "Expert"
            )
        default:
            return AdaptiveDualCardConfig(
                totalRounds: 15,
                complexityLevel: 3,
                switchingPattern: "alternating",
                cardComplexity: 3,
                name: "Medium"
            )
        }
    }
    
    static func generateAdaptiveCardData(_ config: AdaptiveDualCardConfig) -> AdaptiveCardData {
        let letters = switch config.cardComplexity {
        case 1: "AEIOU" // Only vowels for easier recognition
        case 2: "AEIOUBC" // Mix with some consonants
        case 3: "ABCDEFGHIJKLMNOPQRSTUVWXYZ" // Full alphabet
        case 4: "ABCDEFGHIJKLMNOPQRSTUVWXYZ" // Full alphabet with more variety
        case 5: "ABCDEFGHIJKLMNOPQRSTUVWXYZ" // Full alphabet maximum complexity
        default: "ABCDEFGHIJKLMNOPQRSTUVWXYZ"
        }
        
        let digitRange = switch config.cardComplexity {
        case 1: 0...5 // Smaller numbers
        case 2: 0...7
        case 3: 0...9 // Full range
        case 4: 0...9
        case 5: 0...9
        default: 0...9
        }
        
        let colors: [Color] = switch config.cardComplexity {
        case 1: [.black] // Single color
        case 2: [.black, .blue] // Two colors
        case 3: [.black, .blue, .red] // Three colors
        case 4: [.black, .blue, .red, .green] // Four colors
        case 5: [ // Full color variety
            .pink, .blue, .green, .orange, .purple, .red, .black
        ]
        default: [.black]
        }
        
        return AdaptiveCardData(
            letter: letters.randomElement()!,
            digit: digitRange.randomElement()!,
            isLetterFirst: Bool.random(),
            textColor: colors.randomElement()!
        )
    }
}

// MARK: - Helper Functions
func checkAdaptiveAnswer(_ isCheckingTopCard: Bool, _ currentCardData: AdaptiveCardData, _ userAnsweredYes: Bool, _ config: AdaptiveDualCardConfig) -> Bool {
    if isCheckingTopCard {
        // Check if digit is even
        let isEven = currentCardData.digit % 2 == 0
        return isEven == userAnsweredYes
    } else {
        // Check if letter is vowel
        let vowels: Set<Character> = ["A", "E", "I", "O", "U"]
        let isVowel = vowels.contains(currentCardData.letter)
        return isVowel == userAnsweredYes
    }
}

// MARK: - Supporting Data Models
struct AdaptationInfo {
    let level: DifficultyLevel
    let confidenceScore: Double
}

// MARK: - Preview
#Preview {
    DualCardPuzzleView(
        difficulty: "Medium",
        timer: "2:00",
        hearts: 3,
        level: "1",
        onSubmitAnswer: { success in
            print("Game completed: \(success)")
        },
        fetchNextPuzzle: { score in
            print("Fetch next puzzle with score: \(score)")
        },
        onBack: {
            print("Back pressed")
        }
    )
}
