//
//  ImageVortexPuzzleView.swift
//  PuzzleForge
//
//  iOS implementation of Adaptive Image Vortex puzzle - FIXED VERSION
//

import SwiftUI
import Foundation

// MARK: - Adaptive Configuration
struct AdaptiveImageVortexConfig {
    let maxImages: Int // Maximum number of images on screen (4-20)
    let responseTimeLimit: Float // Time limit per level in seconds
    let showVisualHints: Bool // Whether to show visual hints for new items
    let imageMovement: Bool // Whether images move/animate
    let distractorComplexity: Int // Number of similar/confusing items (0-5)
    let sequenceLength: Int // Number of levels to complete (5-25)
    let imageScaling: Bool // Whether to use varied image sizes
    let spatialChallenges: Bool // Enable complex spatial arrangements
    let attentionDistractors: Bool // Add visual distractors
    let adaptiveSpacing: Bool // Dynamic spacing based on performance
    let name: String
    let description: String
}

// MARK: - Enhanced Image Item
struct AdaptiveImageItem: Identifiable, Equatable {
    let id: Int
    let emoji: String
    let isNew: Bool
    let difficulty: Float // Individual item difficulty (0.0-1.0)
    let isDistractor: Bool // Whether this is a distractor item
    let scaleFactor: Float // Size scaling factor
    let movementPattern: MovementPattern // Movement behavior
    let appearanceTime: Date // When this item appeared
    
    static func == (lhs: AdaptiveImageItem, rhs: AdaptiveImageItem) -> Bool {
        return lhs.id == rhs.id
    }
}

// MARK: - Movement Patterns
enum MovementPattern {
    case staticMovement
    case slowDrift
    case circular
    case randomWalk
    case pulsing
}

// MARK: - Game Phase
enum GamePhase {
    case showingInitialImages  // Show base images for memorization
    case waitingForUserReady  // Brief pause before adding new image
    case findingNewImage      // User needs to find the newly added image
    case showingFeedback      // Display correct/incorrect feedback
}

// MARK: - Challenge Types
enum AttentionChallengeType {
    case basicNewItem // Find the newly appeared item
    case categoryChange // Find item from different category
    case sizeVariant // Find differently sized item
    case movementTarget // Find moving item among static ones
    case colorDistraction // Similar emojis with subtle differences
    case spatialComplexity // Complex overlapping arrangements
}

// MARK: - Main Adaptive Image Vortex View
struct ImageVortexPuzzleView: View {
    let puzzle: Puzzle
    let questionIndex: Int
    let totalQuestions: Int
    let onAnswerSubmitted: (Bool, Bool) -> Void
    let onNextPuzzle: () -> Void
    let onExit: () -> Void
    
    // Adaptive difficulty state
    @State private var currentDifficultyLevel: DifficultyLevel
    @State private var adaptationInfo: AdaptationInfo?
    @State private var showAdaptationNotification = false
    @State private var competitiveInsight: CompetitiveInsight?
    
    // Enhanced emoji categories for better adaptive challenges
    private let emojiCategories: [String: [String]] = [
        "animals": ["🐶", "🐱", "🐭", "🐹", "🐰", "🦊", "🐻", "🐼", "🐨", "🐯", "🦁", "🐮", "🐷", "🐸", "🐵"],
        "food": ["🍎", "🍊", "🍋", "🍌", "🍉", "🍇", "🍓", "🍈", "🍑", "🍍", "🥭", "🍒", "🥥", "🥝", "🍅"],
        "sports": ["⚽", "🏀", "🏈", "⚾", "🎾", "🏐", "🏉", "🎱", "🏓", "🏸", "🥅", "🏆", "🥇", "🥈", "🥉"],
        "nature": ["🌲", "🌳", "🌴", "🌵", "🌶️", "🌷", "🌸", "🌹", "🌺", "🌻", "🌼", "🌽", "🥦", "🥒", "🍄"],
        "transport": ["🚗", "🚕", "🚙", "🚌", "🚎", "🏎️", "🚓", "🚑", "🚒", "🚐", "🚚", "🚛", "🚜", "🏍️", "🚲"],
        "space": ["🌍", "🌎", "🌏", "🌕", "🌖", "🌗", "🌘", "🌑", "🌒", "🌓", "🌔", "⭐", "🌟", "✨", "☄️"]
    ]
    
    private var availableEmojis: [String] {
        emojiCategories.values.flatMap { $0 }
    }
    
    // Adaptive configuration
    private var adaptiveConfig: AdaptiveImageVortexConfig {
        generateAdaptiveImageVortexConfig(currentDifficultyLevel)
    }
    
    // Game state
    @State private var currentPhase: GamePhase = .showingInitialImages
    @State private var baseImages: [AdaptiveImageItem] = []
    @State private var allImages: [AdaptiveImageItem] = []
    @State private var currentLevel = 1
    @State private var targetPuzzleCount: Int = 10
    @State private var feedbackCorrect = false
    @State private var selectedImageId: Int? = nil
    @State private var gameComplete = false
    @State private var usedEmojis: Set<String> = []
    @State private var nextImageId = 1
    @State private var currentChallengeType: AttentionChallengeType = .basicNewItem
    @State private var memorizeTimeRemaining: Float = 3.0
    @State private var newImageToFind: AdaptiveImageItem? = nil
    
    // Performance tracking
    @State private var totalScore = 0
    @State private var correctAnswers = 0
    @State private var totalAttempts = 0
    @State private var gameStartTime = Date()
    @State private var levelStartTimes: [Int: Date] = [:]
    @State private var reactionTimes: [TimeInterval] = []
    @State private var streak = 0
    @State private var bestStreak = 0
    @State private var incorrectSelections = 0
    @State private var currentHearts: Int
    @State private var gamesPlayedThisSession = 0
    @State private var showHint = false
    @State private var sessionStartTime = Date()
    
    // Timer tracking
    @State private var timeRemaining: Int
    @State private var levelTimeRemaining: Float = 5.0
    @State private var isGameActive = true
    
    // Timer objects
    @State private var mainTimer: Timer?
    @State private var levelTimer: Timer?
    @State private var memorizeTimer: Timer?
    
    init(puzzle: Puzzle, questionIndex: Int, totalQuestions: Int, onAnswerSubmitted: @escaping (Bool, Bool) -> Void, onNextPuzzle: @escaping () -> Void, onExit: @escaping () -> Void) {
        self.puzzle = puzzle
        self.questionIndex = questionIndex
        self.totalQuestions = totalQuestions
        self.onAnswerSubmitted = onAnswerSubmitted
        self.onNextPuzzle = onNextPuzzle
        self.onExit = onExit
        
        // Initialize difficulty level
        let difficultyString = puzzle.difficulty ?? "Medium"
        let difficultyLevel = Self.getDifficultyLevel(for: difficultyString)
        self._currentDifficultyLevel = State(initialValue: difficultyLevel)
        self._currentHearts = State(initialValue: difficultyLevel.livesAllowed)
        self._timeRemaining = State(initialValue: difficultyLevel.timeLimit)
    }
    
    var body: some View {
        ZStack {
            Color(red: 0.96, green: 0.96, blue: 0.96)
                .ignoresSafeArea()
            
            VStack(spacing: 16) {
                // Adaptive Header
                adaptiveHeaderView
                
                // Adaptation Notification
                if showAdaptationNotification {
                    adaptationNotificationView
                        .transition(.opacity.combined(with: .scale))
                }
                
                Spacer(minLength: 16)
                
                // Phase-specific instructions
                phaseInstructionsCard
                
                Spacer(minLength: 20)
                
                // Adaptive images display area
                GeometryReader { geometry in
                    ZStack {
                        // Background for image area
                        RoundedRectangle(cornerRadius: 16)
                            .fill(Color.white)
                            .shadow(color: .black.opacity(0.1), radius: 4, x: 0, y: 2)
                        
                        // Images positioned with adaptive logic
                        ForEach(getCurrentImages()) { imageItem in
                            AdaptiveRandomPositionedEmojiView(
                                imageItem: imageItem,
                                isSelected: selectedImageId == imageItem.id,
                                showFeedback: currentPhase == .showingFeedback,
                                feedbackCorrect: feedbackCorrect,
                                adaptiveConfig: adaptiveConfig,
                                levelProgress: Float(currentLevel) / Float(targetPuzzleCount),
                                containerSize: CGSize(
                                    width: geometry.size.width - 32,
                                    height: geometry.size.height
                                ),
                                isClickable: currentPhase == .findingNewImage,
                                showHints: false, // REMOVED VISUAL HINTS
                                onClick: { handleImageClick(imageItem.id) }
                            )
                        }
                        
                        // Enhanced feedback overlay
                        if currentPhase == .showingFeedback {
                            adaptiveFeedbackOverlay
                        }
                        
                        // Ready overlay only
                        if currentPhase == .waitingForUserReady {
                            readyOverlay
                        }
                    }
                }
                
                Spacer(minLength: 16)
                
                // Show memorization message below images during memorization phase
                if currentPhase == .showingInitialImages {
                    memorizeOverlay
                }
                
                // Enhanced progress tracking
                adaptiveProgressCard
            }
            .padding(.horizontal, 16)
        }
        .navigationBarHidden(true)
        .onAppear {
            initializeAdaptiveGame()
            startTimers()
        }
        .onDisappear {
            cleanup()
        }
    }
    
    // MARK: - Phase Instructions Card
    private var phaseInstructionsCard: some View {
        VStack(spacing: 12) {
            Text(getPhaseTitle())
                .font(.system(size: 18, weight: .bold))
                .foregroundColor(.blue)
                .multilineTextAlignment(.center)
            
            Text(getPhaseDescription())
                .font(.system(size: 14))
                .foregroundColor(.gray)
                .multilineTextAlignment(.center)
            
            // Performance coaching
            if !reactionTimes.isEmpty && currentPhase == .findingNewImage {
                let avgReaction = reactionTimes.suffix(3).reduce(0, +) / Double(min(3, reactionTimes.count))
                Text("⚡ Recent speed: \(String(format: "%.1f", avgReaction))s")
                    .font(.caption)
                    .foregroundColor(getPerformanceColor(avgReaction: avgReaction))
            }
            
            HStack {
                Text("Score: \(totalScore)")
                    .font(.caption)
                    .fontWeight(.bold)
                    .foregroundColor(.blue)
                
                Spacer()
                
                if bestStreak > 1 {
                    Text("Best Streak: \(bestStreak)")
                        .font(.caption)
                        .foregroundColor(.orange)
                }
                
                Spacer()
                
                Text("Level: \(currentLevel)/\(targetPuzzleCount)")
                    .font(.caption)
                    .foregroundColor(.purple)
            }
        }
        .padding(16)
        .background(Color.blue.opacity(0.1))
        .cornerRadius(12)
    }
    
    // MARK: - Phase Overlays
    private var memorizeOverlay: some View {
        VStack(spacing: 12) {
            Text("📖 Memorize these images")
                .font(.headline)
                .fontWeight(.bold)
                .foregroundColor(.white)
            
            HStack(spacing: 12) {
                Text("\(Int(memorizeTimeRemaining))")
                    .font(.system(size: 28, weight: .bold))
                    .foregroundColor(.white)
                
                ProgressView(value: Double(3.0 - memorizeTimeRemaining), total: 3.0)
                    .progressViewStyle(LinearProgressViewStyle(tint: .white))
                    .frame(width: 150)
            }
        }
        .padding(16)
        .background(Color.blue.opacity(0.9))
        .cornerRadius(12)
        .shadow(radius: 4)
    }
    
    private var readyOverlay: some View {
        VStack(spacing: 16) {
            Text("🎯 Get Ready!")
                .font(.title2)
                .fontWeight(.bold)
                .foregroundColor(.white)
            
            Text("Find the NEW image")
                .font(.headline)
                .foregroundColor(.white)
        }
        .padding(32)
        .background(Color.orange.opacity(0.9))
        .cornerRadius(16)
        .shadow(radius: 8)
    }
    
    // MARK: - Adaptive Header View (keeping existing implementation)
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
                Text("🎯 Image Vortex")
                    .font(.headline)
                    .fontWeight(.bold)
                    .foregroundColor(.white)
                
                Text(adaptiveConfig.name)
                    .font(.caption2)
                    .foregroundColor(.green)
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
                    ForEach(0..<3, id: \.self) { index in
                        Image(systemName: index < currentHearts ? "heart.fill" : "heart")
                            .font(.system(size: 12))
                            .foregroundColor(index < currentHearts ? .red : .gray)
                    }
                }
                
                if streak > 0 {
                    Text("🔥 \(streak)")
                        .font(.caption)
                        .fontWeight(.bold)
                        .foregroundColor(.orange)
                }
            }
            
            // Help button
            Button(action: { showHint = true }) {
                Image(systemName: "questionmark.circle.fill")
                    .font(.title2)
                    .foregroundColor(.white)
                    .frame(width: 40, height: 40)
            }
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 8)
        .background(
            LinearGradient(
                colors: [Color(red: 0.2, green: 0.3, blue: 0.4), Color(red: 0.1, green: 0.2, blue: 0.3)],
                startPoint: .leading,
                endPoint: .trailing
            )
        )
        .cornerRadius(12)
        .alert("💡 Adaptive Hint", isPresented: $showHint) {
            Button("OK") { showHint = false }
        } message: {
            Text(getChallengeHint(challengeType: currentChallengeType))
        }
    }
    
    // MARK: - Supporting Views (keeping existing implementation)
    private var adaptiveProgressCard: some View {
        VStack(spacing: 8) {
            HStack {
                Text("Progress: \(currentLevel) / \(targetPuzzleCount)")
                    .font(.system(size: 14))
                    .foregroundColor(.gray)
                
                Spacer()
                
                if currentPhase == .findingNewImage && levelTimeRemaining <= 2.0 {
                    Text("⚠️ Time Running Out!")
                        .font(.caption)
                        .foregroundColor(.red)
                        .fontWeight(.bold)
                }
            }
            
            ProgressView(value: Double(currentLevel), total: Double(targetPuzzleCount))
                .progressViewStyle(LinearProgressViewStyle(tint: .blue))
                .frame(height: 8)
                .background(Color.gray.opacity(0.2))
                .cornerRadius(4)
            
            // Level timer bar - only show during finding phase
            if currentPhase == .findingNewImage {
                ProgressView(value: Double(levelTimeRemaining), total: Double(adaptiveConfig.responseTimeLimit))
                    .progressViewStyle(LinearProgressViewStyle(tint: .orange))
                    .frame(height: 4)
                    .background(Color.gray.opacity(0.2))
                    .cornerRadius(2)
            }
        }
        .padding(16)
        .background(Color.white)
        .cornerRadius(12)
        .shadow(color: .black.opacity(0.1), radius: 4, x: 0, y: 2)
    }
    
    // MARK: - Adaptive Feedback Overlay
    private var adaptiveFeedbackOverlay: some View {
        VStack(spacing: 12) {
            Text(feedbackCorrect ? "✅" : "❌")
                .font(.system(size: 48))
            
            Text(feedbackCorrect ? "Excellent!" : "Try Again!")
                .font(.system(size: 24, weight: .bold))
                .foregroundColor(.white)
            
            if feedbackCorrect {
                if !reactionTimes.isEmpty {
                    let reactionTime = reactionTimes.last!
                    Text("⚡ \(String(format: "%.1f", reactionTime))s")
                        .font(.system(size: 16))
                        .foregroundColor(.white)
                }
                
                if streak > 1 {
                    Text("🔥 Streak: \(streak)")
                        .font(.system(size: 14))
                        .foregroundColor(.white)
                }
            } else {
                // Show which image was the correct one
                if let newImage = newImageToFind {
                    Text("The new image was: \(newImage.emoji)")
                        .font(.system(size: 16))
                        .foregroundColor(.white)
                }
            }
        }
        .padding(24)
        .background(feedbackCorrect ? Color.green : Color.red)
        .cornerRadius(16)
        .shadow(radius: 8)
    }
    
    // MARK: - Supporting Views (keeping competitive insight and notification views)
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
    
    private func competitiveInsightView(_ insight: CompetitiveInsight) -> some View {
        HStack(spacing: 4) {
            Image(systemName: "trophy.fill")
                .foregroundColor(.yellow)
                .font(.caption2)
            
            Text("> \(insight.percentile)%")
                .font(.caption)
                .fontWeight(.bold)
                .foregroundColor(.white)
        }
        .padding(.horizontal, 6)
        .padding(.vertical, 2)
        .background(Color.white.opacity(0.1))
        .cornerRadius(4)
    }
    
    // MARK: - Game Logic
    private func getCurrentImages() -> [AdaptiveImageItem] {
        switch currentPhase {
        case .showingInitialImages:
            return baseImages
        case .waitingForUserReady, .findingNewImage, .showingFeedback:
            return allImages
        }
    }
    
    private func initializeAdaptiveGame() {
        gameStartTime = Date()
        sessionStartTime = Date()
        targetPuzzleCount = adaptiveConfig.sequenceLength
        
        // Load competitive insight
        loadCompetitiveInsight()
        
        // Start with memorization phase
        startMemorizationPhase()
    }
    
    private func startMemorizationPhase() {
        currentPhase = .showingInitialImages
        memorizeTimeRemaining = 3.0
        
        // Generate base images (without the new one)
        let baseImageCount = min(adaptiveConfig.maxImages - 1, 3 + currentLevel - 1) // Reserve space for new image
        var images: [AdaptiveImageItem] = []
        var currentId = nextImageId
        var updatedUsedEmojis = usedEmojis
        
        let availableForBase = availableEmojis.filter { !usedEmojis.contains($0) }
        
        for _ in 0..<baseImageCount {
            if !availableForBase.isEmpty {
                let emoji = availableForBase.randomElement()!
                images.append(
                    AdaptiveImageItem(
                        id: currentId,
                        emoji: emoji,
                        isNew: false,
                        difficulty: 0.3,
                        isDistractor: false,
                        scaleFactor: adaptiveConfig.imageScaling ? Float.random(in: 0.8...1.2) : 1.0,
                        movementPattern: adaptiveConfig.imageMovement && Float.random(in: 0...1) < 0.3 ?
                            [MovementPattern.slowDrift, MovementPattern.pulsing].randomElement()! :
                            MovementPattern.staticMovement,
                        appearanceTime: Date()
                    )
                )
                updatedUsedEmojis.insert(emoji)
                currentId += 1
            }
        }
        
        baseImages = images
        usedEmojis = updatedUsedEmojis
        nextImageId = currentId
        
        // Start memorization timer
        memorizeTimer = Timer.scheduledTimer(withTimeInterval: 0.1, repeats: true) { _ in
            if memorizeTimeRemaining > 0 {
                memorizeTimeRemaining -= 0.1
            } else {
                memorizeTimer?.invalidate()
                memorizeTimer = nil
                startWaitingPhase()
            }
        }
    }
    
    private func startWaitingPhase() {
        // Generate the new image that will be added
        newImageToFind = generateTargetImage(
            challengeType: currentChallengeType,
            config: adaptiveConfig,
            usedEmojis: usedEmojis,
            availableEmojis: availableEmojis,
            emojiCategories: emojiCategories,
            currentId: nextImageId,
            level: currentLevel
        )
        
        usedEmojis.insert(newImageToFind!.emoji)
        nextImageId += 1
        
        // Combine base images with new image
        allImages = baseImages + [newImageToFind!]
        allImages.shuffle()
        
        // Show ready phase
        currentPhase = .waitingForUserReady
        
        // Wait 1.5 seconds then start finding phase
        DispatchQueue.main.asyncAfter(deadline: .now() + 1.5) {
            startFindingPhase()
        }
    }
    
    private func startFindingPhase() {
        currentPhase = .findingNewImage
        levelStartTimes[currentLevel] = Date()
        levelTimeRemaining = adaptiveConfig.responseTimeLimit
    }
    
    private func handleImageClick(_ imageId: Int) {
        guard currentPhase == .findingNewImage && isGameActive else { return }
        
        selectedImageId = imageId
        totalAttempts += 1
        
        let clickedImage = allImages.first { $0.id == imageId }
        let isCorrect = clickedImage?.id == newImageToFind?.id
        
        // Calculate reaction time
        let levelStartTime = levelStartTimes[currentLevel] ?? Date()
        let reactionTime = Date().timeIntervalSince(levelStartTime)
        reactionTimes.append(reactionTime)
        
        recordAdaptivePerformance(
            isCorrect: isCorrect,
            responseTime: reactionTime,
            challengeDifficulty: clickedImage?.difficulty ?? 0.5
        )
        
        feedbackCorrect = isCorrect
        currentPhase = .showingFeedback
        
        if isCorrect {
            correctAnswers += 1
            streak += 1
            if streak > bestStreak {
                bestStreak = streak
            }
            
            let scoreBonus = calculateAdaptiveScore(
                isCorrect: isCorrect,
                reactionTime: reactionTime,
                difficulty: clickedImage?.difficulty ?? 0.5
            )
            totalScore += scoreBonus
        } else {
            incorrectSelections += 1
            streak = 0
            currentHearts = max(0, currentHearts - 1)
        }
        
        onAnswerSubmitted(isCorrect, isCorrect)
        
        // Auto-advance after feedback
        DispatchQueue.main.asyncAfter(deadline: .now() + 2.0) {
            handleFeedbackComplete()
        }
    }
    
    private func handleFeedbackComplete() {
        selectedImageId = nil
        
        if currentHearts <= 0 {
            handleGameOver()
            return
        }
        
        if feedbackCorrect {
            if currentLevel >= targetPuzzleCount {
                handleGameComplete()
            } else {
                moveToNextLevel()
            }
        } else {
            retryCurrentLevel()
        }
    }
    
    private func moveToNextLevel() {
        currentLevel += 1
        gamesPlayedThisSession += 1
        
        // Determine next challenge type
        currentChallengeType = selectNextChallengeType(
            level: currentLevel,
            config: adaptiveConfig,
            recentPerformance: reactionTimes.suffix(3).reduce(0, +) / Double(min(3, reactionTimes.count)),
            streakLength: streak
        )
        
        startMemorizationPhase()
    }
    
    private func retryCurrentLevel() {
        startMemorizationPhase()
    }
    
    private func startTimers() {
        // Main game timer
        mainTimer = Timer.scheduledTimer(withTimeInterval: 1.0, repeats: true) { _ in
            if timeRemaining > 0 && !gameComplete && isGameActive {
                timeRemaining -= 1
            } else if timeRemaining == 0 && isGameActive {
                handleTimeUp()
            }
        }
        
        // Level timer (only during finding phase)
        levelTimer = Timer.scheduledTimer(withTimeInterval: 0.1, repeats: true) { _ in
            if isGameActive && !gameComplete && currentPhase == .findingNewImage && levelTimeRemaining > 0 {
                levelTimeRemaining -= 0.1
            } else if levelTimeRemaining <= 0 && isGameActive && currentPhase == .findingNewImage && !gameComplete {
                handleLevelTimeout()
            }
        }
    }
    
    private func handleLevelTimeout() {
        let responseTime = Date().timeIntervalSince(levelStartTimes[currentLevel] ?? Date())
        reactionTimes.append(responseTime)
        
        recordAdaptivePerformance(
            isCorrect: false,
            responseTime: responseTime,
            challengeDifficulty: 0.5
        )
        
        totalAttempts += 1
        incorrectSelections += 1
        streak = 0
        currentHearts = max(0, currentHearts - 1)
        
        feedbackCorrect = false
        currentPhase = .showingFeedback
        
        DispatchQueue.main.asyncAfter(deadline: .now() + 2.0) {
            handleFeedbackComplete()
        }
    }
    
    private func handleTimeUp() {
        isGameActive = false
        gameComplete = true
        cleanup()
        onAnswerSubmitted(false, false)
        onNextPuzzle()
    }
    
    private func handleGameOver() {
        gameComplete = true
        isGameActive = false
        cleanup()
        onAnswerSubmitted(false, false)
        onNextPuzzle()
    }
    
    private func handleGameComplete() {
        gameComplete = true
        isGameActive = false
        cleanup()
        
        totalScore = calculateFinalScore()
        onAnswerSubmitted(true, true)
        onNextPuzzle()
    }
    
    private func cleanup() {
        mainTimer?.invalidate()
        levelTimer?.invalidate()
        memorizeTimer?.invalidate()
        mainTimer = nil
        levelTimer = nil
        memorizeTimer = nil
    }
    
    // MARK: - Phase Helper Functions
    private func getPhaseTitle() -> String {
        switch currentPhase {
        case .showingInitialImages:
            return "📖 Memorize These Images"
        case .waitingForUserReady:
            return "🎯 Get Ready!"
        case .findingNewImage:
            return getChallengeTitle(challengeType: currentChallengeType)
        case .showingFeedback:
            return feedbackCorrect ? "✅ Correct!" : "❌ Incorrect"
        }
    }
    
    private func getPhaseDescription() -> String {
        switch currentPhase {
        case .showingInitialImages:
            return "Study these \(baseImages.count) images carefully - a new one will be added!"
        case .waitingForUserReady:
            return "A new image has been added to the set!"
        case .findingNewImage:
            return "Find the NEW image! (\(baseImages.count) → \(allImages.count) images)"
        case .showingFeedback:
            return feedbackCorrect ? "Great job spotting the new image!" : "The new image was highlighted above"
        }
    }
    
    // MARK: - Keep all existing helper functions unchanged
    private func recordAdaptivePerformance(isCorrect: Bool, responseTime: TimeInterval, challengeDifficulty: Float) {
        let performanceScore = calculatePerformanceScore(
            isCorrect: isCorrect,
            timeSpent: responseTime,
            streak: streak
        )
        
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
        
        let timeTarget = Double(adaptiveConfig.responseTimeLimit)
        if timeSpent < timeTarget {
            score += (timeTarget - timeSpent) / timeTarget * 0.2
        } else {
            score -= min((timeSpent - timeTarget) / timeTarget * 0.2, 0.3)
        }
        
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
            
            currentHearts = currentDifficultyLevel.livesAllowed
            timeRemaining = currentDifficultyLevel.timeLimit
        }
    }
    
    private func calculateAdaptiveScore(isCorrect: Bool, reactionTime: TimeInterval, difficulty: Float) -> Int {
        if !isCorrect { return 0 }
        
        let basePoints = currentDifficultyLevel.basePoints
        var score = basePoints
        
        // Time bonus
        let timeTarget = Double(adaptiveConfig.responseTimeLimit)
        if reactionTime < timeTarget * 0.5 {
            score += Int(Double(basePoints) * 0.3)
        } else if reactionTime < timeTarget * 0.75 {
            score += Int(Double(basePoints) * 0.15)
        }
        
        // Streak bonus
        score += min(streak * 5, 50)
        
        // Difficulty multiplier
        let difficultyMultiplier = 1.0 + (Double(difficulty) * 0.5)
        score = Int(Double(score) * difficultyMultiplier)
        
        return score
    }
    
    private func calculateFinalScore() -> Int {
        return calculateAdaptiveVisualAttentionScore(
            correctAnswers: correctAnswers,
            totalAttempts: totalAttempts,
            levelsCompleted: currentLevel,
            avgReactionTime: Int(reactionTimes.isEmpty ? 2000 : reactionTimes.reduce(0, +) * 1000 / Double(reactionTimes.count)),
            difficulty: currentDifficultyLevel,
            timeSpentMs: Int(Date().timeIntervalSince(gameStartTime) * 1000),
            bestStreak: bestStreak,
            incorrectSelections: incorrectSelections,
            adaptiveConfig: adaptiveConfig
        )
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
    
    // MARK: - Helper Functions
    private func formatTime(_ seconds: Int) -> String {
        let minutes = seconds / 60
        let remainingSeconds = seconds % 60
        return String(format: "%d:%02d", minutes, remainingSeconds)
    }
    
    private func getPerformanceColor(avgReaction: Double) -> Color {
        let timeLimit = Double(adaptiveConfig.responseTimeLimit)
        if avgReaction <= timeLimit * 0.5 {
            return .green
        } else if avgReaction <= timeLimit * 0.75 {
            return .orange
        } else {
            return .red
        }
    }
    
    // MARK: - Static Helper Functions
    static func getDifficultyLevel(for difficulty: String) -> DifficultyLevel {
        return DifficultyLevel.create(name: difficulty)
    }
    
    static func getDifficultyLevel(for index: Int) -> DifficultyLevel {
        return DifficultyLevel.create(index: index)
    }
}

// MARK: - Updated Adaptive Random Positioned Emoji View
struct AdaptiveRandomPositionedEmojiView: View {
    let imageItem: AdaptiveImageItem
    let isSelected: Bool
    let showFeedback: Bool
    let feedbackCorrect: Bool
    let adaptiveConfig: AdaptiveImageVortexConfig
    let levelProgress: Float
    let containerSize: CGSize
    let isClickable: Bool
    let showHints: Bool // REMOVED THIS FUNCTIONALITY
    let onClick: () -> Void
    
    @State private var scale: CGFloat = 1.0
    @State private var opacity: Double = 1.0
    @State private var rotation: Double = 0.0
    @State private var movementOffset: CGSize = .zero
    
    // Generate consistent random position based on item ID
    private var position: CGPoint {
        var random = Random(seed: imageItem.id)
        let xOffset = CGFloat(random.nextFloat()) * 0.75 + 0.1
        let yOffset = CGFloat(random.nextFloat()) * 0.75 + 0.1
        return CGPoint(x: xOffset, y: yOffset)
    }
    
    var body: some View {
        Button(action: onClick) {
            ZStack {
                // Main emoji
                Text(imageItem.emoji)
                    .font(.system(size: CGFloat(48 * imageItem.scaleFactor)))
                    .scaleEffect(scale)
                    .opacity(opacity)
                    .rotationEffect(.degrees(rotation))
                    .offset(movementOffset)
                
                // Movement pattern indicator (optional)
                if imageItem.movementPattern != .staticMovement && adaptiveConfig.imageMovement {
                    Circle()
                        .stroke(Color.green.opacity(0.2), lineWidth: 1)
                        .frame(width: 70, height: 70)
                }
                
                // Feedback overlay
                if isSelected && showFeedback {
                    Circle()
                        .fill((feedbackCorrect ? Color.green : Color.red).opacity(0.9))
                        .frame(width: 85, height: 85)
                        .overlay(
                            Text(feedbackCorrect ? "✅" : "❌")
                                .font(.system(size: 28))
                        )
                }
                
                // Show the correct answer when user got it wrong
                if showFeedback && !feedbackCorrect && imageItem.isNew {
                    Circle()
                        .stroke(Color.blue, lineWidth: 4)
                        .frame(width: 90, height: 90)
                        .overlay(
                            Text("NEW")
                                .font(.caption)
                                .fontWeight(.bold)
                                .foregroundColor(.blue)
                                .offset(y: -50)
                        )
                }
            }
        }
        .buttonStyle(PlainButtonStyle())
        .frame(width: 60, height: 60)
        .position(
            x: position.x * containerSize.width,
            y: position.y * containerSize.height
        )
        .disabled(!isClickable)
        .onAppear {
            startMovementAnimation()
        }
        .onChange(of: isSelected) { selected in
            if selected && showFeedback {
                withAnimation(.spring(response: 0.6, dampingFraction: 0.6)) {
                    scale = 1.3
                }
            }
        }
        .onChange(of: showFeedback) { feedback in
            if feedback && !isSelected {
                withAnimation(.easeOut(duration: 0.3)) {
                    opacity = 0.5
                }
            } else if !feedback {
                withAnimation(.easeIn(duration: 0.3)) {
                    opacity = 1.0
                    scale = 1.0
                }
            }
        }
    }
    
    private func startMovementAnimation() {
        guard adaptiveConfig.imageMovement else { return }
        
        switch imageItem.movementPattern {
        case .staticMovement:
            break
        case .slowDrift:
            withAnimation(.linear(duration: 4.0).repeatForever(autoreverses: true)) {
                movementOffset = CGSize(width: 10, height: 5)
            }
        case .circular:
            withAnimation(.linear(duration: 3.0).repeatForever()) {
                rotation = 360
            }
        case .pulsing:
            withAnimation(.easeInOut(duration: 1.0).repeatForever(autoreverses: true)) {
                scale = 1.2
            }
        case .randomWalk:
            Timer.scheduledTimer(withTimeInterval: 1.0, repeats: true) { _ in
                withAnimation(.easeInOut(duration: 0.5)) {
                    movementOffset = CGSize(
                        width: Double.random(in: -15...15),
                        height: Double.random(in: -15...15)
                    )
                }
            }
        }
    }
}

// MARK: - Updated Helper Functions
func generateAdaptiveLevel(
    level: Int,
    config: AdaptiveImageVortexConfig,
    usedEmojis: Set<String>,
    availableEmojis: [String],
    emojiCategories: [String: [String]],
    startId: Int,
    challengeType: AttentionChallengeType = .basicNewItem,
    isRetry: Bool = false,
    generateOnlyBase: Bool = false
) -> (Array<AdaptiveImageItem>, Set<String>) {
    let imageCount = min(config.maxImages, 3 + level)
    var images: [AdaptiveImageItem] = []
    var currentId = startId
    var updatedUsedEmojis = usedEmojis
    
    // Generate base images (the ones to memorize)
    let baseImageCount = generateOnlyBase ? imageCount : max(1, imageCount - 1)
    let availableForBase = availableEmojis.filter { !usedEmojis.contains($0) }
    
    for _ in 0..<baseImageCount {
        if !availableForBase.isEmpty {
            let emoji = availableForBase.randomElement()!
            images.append(
                AdaptiveImageItem(
                    id: currentId,
                    emoji: emoji,
                    isNew: false,
                    difficulty: 0.3,
                    isDistractor: false,
                    scaleFactor: config.imageScaling ? Float.random(in: 0.8...1.2) : 1.0,
                    movementPattern: config.imageMovement && Float.random(in: 0...1) < 0.3 ?
                        [MovementPattern.slowDrift, MovementPattern.pulsing].randomElement()! :
                        MovementPattern.staticMovement,
                    appearanceTime: Date()
                )
            )
            updatedUsedEmojis.insert(emoji)
            currentId += 1
        }
    }
    
    return (images, updatedUsedEmojis)
}

// MARK: - Keep all other existing helper functions unchanged
func generateAdaptiveImageVortexConfig(_ difficulty: DifficultyLevel) -> AdaptiveImageVortexConfig {
    switch difficulty.index {
    case 0: // Beginner
        return AdaptiveImageVortexConfig(
            maxImages: 4,
            responseTimeLimit: 8.0,
            showVisualHints: false, // REMOVED HINTS
            imageMovement: false,
            distractorComplexity: 0,
            sequenceLength: 8,
            imageScaling: false,
            spatialChallenges: false,
            attentionDistractors: false,
            adaptiveSpacing: true,
            name: "Beginner Mode",
            description: "Simple visual search with extended time"
        )
    case 1: // Easy
        return AdaptiveImageVortexConfig(
            maxImages: 6,
            responseTimeLimit: 6.0,
            showVisualHints: false, // REMOVED HINTS
            imageMovement: false,
            distractorComplexity: 1,
            sequenceLength: 10,
            imageScaling: false,
            spatialChallenges: false,
            attentionDistractors: true,
            adaptiveSpacing: true,
            name: "Easy Mode",
            description: "Moderate visual search with some distractors"
        )
    case 2: // Medium
        return AdaptiveImageVortexConfig(
            maxImages: 10,
            responseTimeLimit: 5.0,
            showVisualHints: false, // REMOVED HINTS
            imageMovement: true,
            distractorComplexity: 2,
            sequenceLength: 15,
            imageScaling: true,
            spatialChallenges: true,
            attentionDistractors: true,
            adaptiveSpacing: true,
            name: "Medium Mode",
            description: "Standard challenge with movement and scaling"
        )
    case 3: // Hard
        return AdaptiveImageVortexConfig(
            maxImages: 15,
            responseTimeLimit: 4.0,
            showVisualHints: false, // REMOVED HINTS
            imageMovement: true,
            distractorComplexity: 4,
            sequenceLength: 20,
            imageScaling: true,
            spatialChallenges: true,
            attentionDistractors: true,
            adaptiveSpacing: false,
            name: "Hard Mode",
            description: "Complex visual search with multiple challenges"
        )
    case 4: // Expert
        return AdaptiveImageVortexConfig(
            maxImages: 20,
            responseTimeLimit: 3.0,
            showVisualHints: false, // REMOVED HINTS
            imageMovement: true,
            distractorComplexity: 5,
            sequenceLength: 25,
            imageScaling: true,
            spatialChallenges: true,
            attentionDistractors: true,
            adaptiveSpacing: false,
            name: "Expert Mode",
            description: "Elite visual attention challenge with maximum complexity"
        )
    default:
        return AdaptiveImageVortexConfig(
            maxImages: 10,
            responseTimeLimit: 5.0,
            showVisualHints: false, // REMOVED HINTS
            imageMovement: true,
            distractorComplexity: 2,
            sequenceLength: 15,
            imageScaling: true,
            spatialChallenges: true,
            attentionDistractors: true,
            adaptiveSpacing: true,
            name: "Medium Mode",
            description: "Standard challenge with movement and scaling"
        )
    }
}

// Keep all other existing helper functions (generateTargetImage, selectNextChallengeType, etc.) unchanged...

func generateTargetImage(
    challengeType: AttentionChallengeType,
    config: AdaptiveImageVortexConfig,
    usedEmojis: Set<String>,
    availableEmojis: [String],
    emojiCategories: [String: [String]],
    currentId: Int,
    level: Int
) -> AdaptiveImageItem {
    let availableForNew = availableEmojis.filter { !usedEmojis.contains($0) }
    let emoji = availableForNew.randomElement() ?? availableEmojis.randomElement()!
    
    let difficulty: Float = {
        switch challengeType {
        case .basicNewItem: return 0.4
        case .categoryChange: return 0.6
        case .sizeVariant: return 0.7
        case .movementTarget: return 0.8
        case .colorDistraction: return 0.9
        case .spatialComplexity: return 1.0
        }
    }()
    
    let scaleFactor: Float = {
        switch challengeType {
        case .sizeVariant: return Float.random(in: 1.2...1.8)
        default: return config.imageScaling ? Float.random(in: 0.9...1.4) : 1.0
        }
    }()
    
    let movementPattern: MovementPattern = {
        switch challengeType {
        case .movementTarget: return [MovementPattern.slowDrift, MovementPattern.circular, MovementPattern.pulsing].randomElement()!
        default: return config.imageMovement && Float.random(in: 0...1) < 0.2 ? MovementPattern.slowDrift : MovementPattern.staticMovement
        }
    }()
    
    return AdaptiveImageItem(
        id: currentId,
        emoji: emoji,
        isNew: true,
        difficulty: difficulty,
        isDistractor: false,
        scaleFactor: scaleFactor,
        movementPattern: movementPattern,
        appearanceTime: Date()
    )
}

func selectNextChallengeType(
    level: Int,
    config: AdaptiveImageVortexConfig,
    recentPerformance: Double,
    streakLength: Int
) -> AttentionChallengeType {
    switch level {
    case 1...3: return .basicNewItem
    case 4...6: return config.distractorComplexity > 0 ? .categoryChange : .basicNewItem
    case 7...10: return config.imageScaling ? .sizeVariant : .categoryChange
    case 11...15: return config.imageMovement ? .movementTarget : .sizeVariant
    case 16...20: return config.attentionDistractors ? .colorDistraction : .movementTarget
    default: return config.spatialChallenges ? .spatialComplexity : [.basicNewItem, .categoryChange, .sizeVariant].randomElement()!
    }
}

func getChallengeTitle(challengeType: AttentionChallengeType) -> String {
    switch challengeType {
    case .basicNewItem: return "🎯 Find the NEW image!"
    case .categoryChange: return "🔍 Find the different category!"
    case .sizeVariant: return "📏 Find the different size!"
    case .movementTarget: return "🌊 Find the moving target!"
    case .colorDistraction: return "🌈 Look for subtle differences!"
    case .spatialComplexity: return "🎲 Navigate complex layout!"
    }
}

func getChallengeDescription(challengeType: AttentionChallengeType) -> String {
    switch challengeType {
    case .basicNewItem: return "Tap the image that just appeared"
    case .categoryChange: return "Find the item from a different category"
    case .sizeVariant: return "Look for the differently sized item"
    case .movementTarget: return "Find the item that's moving"
    case .colorDistraction: return "Focus through the visual distractions"
    case .spatialComplexity: return "Navigate the complex arrangement"
    }
}

func getChallengeHint(challengeType: AttentionChallengeType) -> String {
    switch challengeType {
    case .basicNewItem: return "Look for the newest item that just appeared on screen."
    case .categoryChange: return "One item belongs to a different category than the others."
    case .sizeVariant: return "One item is noticeably larger or smaller than the rest."
    case .movementTarget: return "Look for the item that's moving while others are still."
    case .colorDistraction: return "Focus on finding the true target despite visual distractions."
    case .spatialComplexity: return "Take your time to scan the complex arrangement systematically."
    }
}

func getChallengeCompletionMessage(challengeType: AttentionChallengeType) -> String {
    switch challengeType {
    case .basicNewItem: return "Great visual detection!"
    case .categoryChange: return "Excellent categorization!"
    case .sizeVariant: return "Perfect size discrimination!"
    case .movementTarget: return "Outstanding motion tracking!"
    case .colorDistraction: return "Fantastic focus through distractions!"
    case .spatialComplexity: return "Impressive spatial navigation!"
    }
}

func calculateAdaptiveVisualAttentionScore(
    correctAnswers: Int,
    totalAttempts: Int,
    levelsCompleted: Int,
    avgReactionTime: Int,
    difficulty: DifficultyLevel,
    timeSpentMs: Int,
    bestStreak: Int,
    incorrectSelections: Int,
    adaptiveConfig: AdaptiveImageVortexConfig
) -> Int {
    if correctAnswers == 0 { return 0 }
    
    let baseScore = correctAnswers * difficulty.basePoints
    
    // Adaptive complexity multiplier
    let complexityMultiplier: Float = {
        if adaptiveConfig.maxImages >= 15 { return 2.0 }
        if adaptiveConfig.maxImages >= 10 { return 1.5 }
        if adaptiveConfig.maxImages >= 6 { return 1.2 }
        return 1.0
    }()
    
    // Visual processing bonus
    let visualBonuses: Float = [
        adaptiveConfig.imageMovement ? 0.3 : 0.0,
        adaptiveConfig.spatialChallenges ? 0.2 : 0.0,
        adaptiveConfig.attentionDistractors ? 0.25 : 0.0,
        adaptiveConfig.imageScaling ? 0.15 : 0.0
    ].reduce(0, +)
    
    let adaptiveMultiplier = 1.0 + visualBonuses
    
    // Accuracy bonus
    let accuracy = totalAttempts > 0 ? Float(correctAnswers) / Float(totalAttempts) : 0.0
    let accuracyBonus: Int = {
        if accuracy >= 0.95 { return Int(Float(baseScore) * 0.5) }
        if accuracy >= 0.85 { return Int(Float(baseScore) * 0.3) }
        if accuracy >= 0.75 { return Int(Float(baseScore) * 0.15) }
        return 0
    }()
    
    // Speed bonus
    let avgReactionSeconds = Float(avgReactionTime) / 1000.0
    let speedBonus: Int = {
        if avgReactionSeconds <= adaptiveConfig.responseTimeLimit * 0.5 {
            return Int(Float(baseScore) * 0.4)
        }
        if avgReactionSeconds <= adaptiveConfig.responseTimeLimit * 0.75 {
            return Int(Float(baseScore) * 0.25)
        }
        if avgReactionSeconds <= adaptiveConfig.responseTimeLimit {
            return Int(Float(baseScore) * 0.1)
        }
        return 0
    }()
    
    // Streak bonus
    let streakBonus: Int = {
        if bestStreak >= levelsCompleted { return Int(Float(baseScore) * 0.3) }
        if bestStreak >= Int(Float(levelsCompleted) * 0.8) { return Int(Float(baseScore) * 0.2) }
        if bestStreak >= Int(Float(levelsCompleted) * 0.6) { return Int(Float(baseScore) * 0.1) }
        return 0
    }()
    
    // Completion bonus
    let completionBonus: Int = {
        if levelsCompleted >= adaptiveConfig.sequenceLength {
            return Int(Float(baseScore) * 0.4)
        }
        if levelsCompleted >= Int(Float(adaptiveConfig.sequenceLength) * 0.8) {
            return Int(Float(baseScore) * 0.25)
        }
        if levelsCompleted >= Int(Float(adaptiveConfig.sequenceLength) * 0.6) {
            return Int(Float(baseScore) * 0.1)
        }
        return 0
    }()
    
    let efficiencyPenalty = incorrectSelections * (difficulty.basePoints / 4)
    
    let finalScore = Int(Float(baseScore) * complexityMultiplier * adaptiveMultiplier) +
                    accuracyBonus + speedBonus + streakBonus + completionBonus - efficiencyPenalty
    
    return max(finalScore, baseScore / 3)
}

// MARK: - Random Number Generator (keep unchanged)
struct Random {
    private var state: UInt64
    
    init(seed: Int) {
        self.state = UInt64(seed)
    }
    
    mutating func nextFloat() -> Float {
        state = state &* 2862933555777941757 &+ 3037000493
        return Float(state >> 32) / Float(UInt32.max)
    }
}
