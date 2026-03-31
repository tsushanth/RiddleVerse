//
//  MemoryPreviousPairPuzzleView.swift
//  PuzzleForge
//

import SwiftUI
import AVFoundation

// MARK: - Theme System
enum PuzzleTheme: String, CaseIterable, Codable {
    case forest = "forest"
    case sea = "sea"
    case farm = "farm"
    case jungle = "jungle"
    case arctic = "arctic"
    case safari = "safari"
    
    var displayName: String {
        switch self {
        case .forest: return "Forest Animals"
        case .sea: return "Sea Creatures"
        case .farm: return "Farm Animals"
        case .jungle: return "Jungle Animals"
        case .arctic: return "Arctic Animals"
        case .safari: return "Safari Animals"
        }
    }
    
    var backgroundColors: [Color] {
        switch self {
        case .forest:
            return [Color(red: 0.53, green: 0.81, blue: 0.92), Color(red: 0.56, green: 0.93, blue: 0.56)]
        case .sea:
            return [Color(red: 0.0, green: 0.5, blue: 1.0), Color(red: 0.0, green: 0.8, blue: 0.8)]
        case .farm:
            return [Color(red: 0.98, green: 0.98, blue: 0.82), Color(red: 0.56, green: 0.93, blue: 0.56)]
        case .jungle:
            return [Color(red: 0.13, green: 0.55, blue: 0.13), Color(red: 0.0, green: 0.39, blue: 0.0)]
        case .arctic:
            return [Color(red: 0.88, green: 0.95, blue: 1.0), Color(red: 0.78, green: 0.92, blue: 0.98)]
        case .safari:
            return [Color(red: 0.96, green: 0.87, blue: 0.70), Color(red: 0.85, green: 0.65, blue: 0.13)]
        }
    }
}

// MARK: - Data Models
struct MemorySequence: Codable {
    let screenNumber: Int
    let numbers: [Int]
    let linkingNumber: Int?
    let isFirstScreen: Bool
}

struct MemoryPreviousPairPuzzleData: Codable {
    let sequence: [MemorySequence]
    let instructions: PuzzleInstructions
    let totalScreens: Int
    let objectsPerScreen: Int
    let maxObjectPool: Int
    let difficulty: String
    
    struct PuzzleInstructions: Codable {
        let title: String
        let description: String
        let steps: [String]
        let tip: String
    }
}

struct MemoryPreviousPairAnswerData: Codable {
    let linkingNumbers: [LinkingNumber]
    let totalCorrectAnswers: Int
    let maxScore: Int
    
    struct LinkingNumber: Codable {
        let screenNumber: Int
        let linkingNumber: Int
    }
}

// MARK: - Game State
enum MemoryGameState {
    case playing
    case feedback
    case complete
}

struct MemoryPreviousPairPuzzleView: View {
    let puzzleData: String
    let correctAnswer: String
    let onSubmitAnswer: (Bool) -> Void
    let fetchNextPuzzle: () -> Void
    let onBack: () -> Void
    
    // Game state
    @State private var currentScreenIndex = 0
    @State private var gameState: MemoryGameState = .playing
    @State private var selectedAnimal: Int? = nil
    @State private var showFeedback = false
    @State private var isCorrectAnswer = false
    @State private var userAnswers: [Int] = []
    @State private var totalScore = 0
    @State private var correctAnswers = 0
    @State private var currentStreak = 0
    @State private var currentHearts = 3
    
    // Timer
    @State private var timeRemaining = 180
    @State private var timer: Timer?
    
    // Parsed data
    @State private var sequenceData: [MemorySequence] = []
    @State private var answerData: MemoryPreviousPairAnswerData? = nil
    @State private var puzzleInstructions: MemoryPreviousPairPuzzleData.PuzzleInstructions?
    @State private var currentTheme: PuzzleTheme = .forest
    
    var body: some View {
        GeometryReader { geometry in
            ZStack {
                // Background
                LinearGradient(
                    colors: [
                        Color(red: 0.1, green: 0.2, blue: 0.3),
                        Color(red: 0.2, green: 0.3, blue: 0.4)
                    ],
                    startPoint: .top,
                    endPoint: .bottom
                )
                .ignoresSafeArea()
                
                VStack(spacing: 16) {
                    // Header
                    MpsHeaderView(
                        timer: formatTime(timeRemaining),
                        hearts: currentHearts,
                        score: totalScore,
                        currentScreen: currentScreenIndex + 1,
                        totalScreens: sequenceData.count,
                        onBack: onBack
                    )
                    
                    // Instructions Card
                    ThemedInstructionsCard(
                        currentScreenIndex: currentScreenIndex,
                        totalScreens: sequenceData.count,
                        isFirstScreen: isCurrentFirstScreen(),
                        theme: currentTheme
                    )
                    
                    Spacer()
                    
                    // Themed Scene
                    ThemedScene(
                        currentAnimals: getCurrentAnimals(),
                        isFirstScreen: isCurrentFirstScreen(),
                        selectedAnimal: selectedAnimal,
                        showFeedback: showFeedback,
                        isCorrectAnswer: isCorrectAnswer,
                        onAnimalTap: handleAnimalClick,
                        theme: currentTheme
                    )
                    .frame(height: min(geometry.size.height * 0.5, 400))
                    
                    Spacer()
                    
                    // Score Card
                    ScoreCard(
                        score: totalScore,
                        correctAnswers: correctAnswers,
                        totalQuestions: max(sequenceData.count - 1, 1),
                        theme: currentTheme
                    )
                }
                .padding(16)
            }
        }
        .onAppear {
            setupPuzzle()
            startTimer()
            // Force trigger the screen change logic
            DispatchQueue.main.asyncAfter(deadline: .now() + 0.1) {
                handleScreenChange()
            }
        }
        .onDisappear {
            timer?.invalidate()
        }
        .onChange(of: currentScreenIndex) { _ in
            handleScreenChange()
        }
    }
    
    // MARK: - Helper Methods
    private func setupPuzzle() {
        parseMemoryPairData()
        determineTheme()
    }
    
    private func determineTheme() {
        // Randomly select a theme or determine based on puzzle data
        currentTheme = PuzzleTheme.allCases.randomElement() ?? .forest
        print("🎨 Selected theme: \(currentTheme.displayName)")
    }
    
    private func parseMemoryPairData() {
        do {
            if let data = puzzleData.data(using: .utf8) {
                let puzzleDataObj = try JSONDecoder().decode(MemoryPreviousPairPuzzleData.self, from: data)
                sequenceData = puzzleDataObj.sequence
                puzzleInstructions = puzzleDataObj.instructions
                print("✅ Successfully parsed puzzle data. Sequences: \(sequenceData.count)")
                
                for (index, sequence) in sequenceData.enumerated() {
                    print("   Sequence \(index): screen \(sequence.screenNumber), animals \(sequence.numbers), linking: \(sequence.linkingNumber ?? -1), isFirst: \(sequence.isFirstScreen)")
                }
            }
            
            if let answerData = correctAnswer.data(using: .utf8) {
                self.answerData = try JSONDecoder().decode(MemoryPreviousPairAnswerData.self, from: answerData)
                print("✅ Successfully parsed answer data")
            }
        } catch {
            print("❌ Failed to parse memory pair data: \(error)")
            print("📄 Puzzle data: \(puzzleData)")
            setupFallbackData()
        }
    }
    
    private func setupFallbackData() {
        sequenceData = [
            MemorySequence(screenNumber: 1, numbers: [2, 5], linkingNumber: nil, isFirstScreen: true),
            MemorySequence(screenNumber: 2, numbers: [5, 6], linkingNumber: 5, isFirstScreen: false),
            MemorySequence(screenNumber: 3, numbers: [3, 6], linkingNumber: 6, isFirstScreen: false)
        ]
    }
    
    private func getCurrentAnimals() -> [Int] {
        guard currentScreenIndex < sequenceData.count else { return [] }
        return sequenceData[currentScreenIndex].numbers
    }
    
    private func isCurrentFirstScreen() -> Bool {
        guard currentScreenIndex < sequenceData.count else { return false }
        return sequenceData[currentScreenIndex].isFirstScreen
    }
    
    private func handleScreenChange() {
        print("🔄 handleScreenChange called - currentScreenIndex: \(currentScreenIndex), sequenceData.count: \(sequenceData.count)")
        
        guard currentScreenIndex < sequenceData.count else {
            print("❌ Screen index out of bounds")
            return
        }
        
        let currentSequence = sequenceData[currentScreenIndex]
        print("📱 Current sequence - screenNumber: \(currentSequence.screenNumber), isFirstScreen: \(currentSequence.isFirstScreen)")
        
        // Auto-advance for first screen
        if currentSequence.isFirstScreen {
            print("⏰ Setting timer for auto-advance...")
            DispatchQueue.main.asyncAfter(deadline: .now() + 4.0) {
                print("🚀 Auto-advance timer fired!")
                withAnimation(.easeInOut(duration: 0.3)) {
                    if currentScreenIndex + 1 < sequenceData.count {
                        print("➡️ Moving to next screen: \(currentScreenIndex + 1)")
                        currentScreenIndex += 1
                    } else {
                        print("🏁 Game complete!")
                        completeGame()
                    }
                }
            }
        } else {
            print("🎯 This is a selection screen - waiting for user input")
        }
    }
    
    private func handleAnimalClick(_ animalId: Int) {
        guard currentScreenIndex < sequenceData.count else { return }
        
        let currentSequence = sequenceData[currentScreenIndex]
        if currentSequence.isFirstScreen { return }
        
        selectedAnimal = animalId
        
        let expectedAnswer = currentSequence.linkingNumber ?? -1
        isCorrectAnswer = animalId == expectedAnswer
        
        if isCorrectAnswer {
            correctAnswers += 1
            currentStreak += 1
            totalScore += 15
            userAnswers.append(animalId)
        } else {
            currentStreak = 0
            currentHearts = max(0, currentHearts - 1)
        }
        
        showFeedback = true
        playFeedbackSound()
        
        // Auto-advance after feedback
        DispatchQueue.main.asyncAfter(deadline: .now() + 1.5) {
            showFeedback = false
            selectedAnimal = nil
            
            if currentScreenIndex + 1 < sequenceData.count {
                currentScreenIndex += 1
            } else {
                completeGame()
            }
        }
    }
    
    private func completeGame() {
        timer?.invalidate()
        let totalQuestions = sequenceData.filter { !$0.isFirstScreen }.count
        let isSuccess = correctAnswers >= Int(Float(totalQuestions) * 0.6)
        
        onSubmitAnswer(isSuccess)
        
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.5) {
            fetchNextPuzzle()
        }
    }
    
    private func startTimer() {
        timer = Timer.scheduledTimer(withTimeInterval: 1.0, repeats: true) { _ in
            if timeRemaining > 0 {
                timeRemaining -= 1
            } else {
                completeGame()
            }
        }
    }
    
    private func formatTime(_ seconds: Int) -> String {
        let minutes = seconds / 60
        let remainingSeconds = seconds % 60
        return String(format: "%d:%02d", minutes, remainingSeconds)
    }
    
    private func playFeedbackSound() {
        let systemSoundID: SystemSoundID = isCorrectAnswer ? 1057 : 1053
        AudioServicesPlaySystemSound(systemSoundID)
        
        let impactFeedback = UIImpactFeedbackGenerator(style: isCorrectAnswer ? .light : .heavy)
        impactFeedback.impactOccurred()
    }
}

// MARK: - Header Component
struct MpsHeaderView: View {
    let timer: String
    let hearts: Int
    let score: Int
    let currentScreen: Int
    let totalScreens: Int
    let onBack: () -> Void
    
    var body: some View {
        HStack {
            Button(action: onBack) {
                Image(systemName: "arrow.left")
                    .font(.title2)
                    .foregroundColor(.white)
                    .frame(width: 40, height: 40)
                    .background(Color(red: 0.25, green: 0.25, blue: 0.25))
                    .clipShape(Circle())
            }
            
            Spacer()
            
            VStack {
                Text("Memory Pairs")
                    .font(.system(size: 16, weight: .bold))
                    .foregroundColor(.white)
                
                Text("Screen \(currentScreen)/\(totalScreens)")
                    .font(.system(size: 12))
                    .foregroundColor(.gray)
            }
            
            Spacer()
            
            VStack(alignment: .trailing) {
                HStack {
                    Image(systemName: "timer")
                        .foregroundColor(.white)
                    Text(timer)
                        .font(.system(size: 16, weight: .bold))
                        .foregroundColor(.white)
                }
                
                HStack {
                    ForEach(0..<3, id: \.self) { index in
                        Image(systemName: index < hearts ? "heart.fill" : "heart")
                            .foregroundColor(index < hearts ? .red : .gray)
                            .font(.system(size: 14))
                    }
                }
                
                Text("Score: \(score)")
                    .font(.system(size: 12))
                    .foregroundColor(.white)
            }
        }
        .padding(16)
        .background(Color(red: 0.18, green: 0.18, blue: 0.18))
        .cornerRadius(12)
    }
}

// MARK: - Themed Instructions Card
struct ThemedInstructionsCard: View {
    let currentScreenIndex: Int
    let totalScreens: Int
    let isFirstScreen: Bool
    let theme: PuzzleTheme
    
    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack {
                Text(instructionText)
                    .font(.system(size: 16, weight: .bold))
                    .foregroundColor(.white)
                Spacer()
                Text(theme.displayName)
                    .font(.system(size: 12, weight: .medium))
                    .foregroundColor(Color(red: 0.69, green: 0.69, blue: 0.69))
                    .padding(.horizontal, 8)
                    .padding(.vertical, 4)
                    .background(Color.white.opacity(0.1))
                    .cornerRadius(6)
            }
            
            Text("Screen \(currentScreenIndex + 1) of \(totalScreens)")
                .font(.system(size: 14))
                .foregroundColor(Color(red: 0.69, green: 0.69, blue: 0.69))
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(16)
        .background(Color(red: 0.18, green: 0.18, blue: 0.18))
        .cornerRadius(12)
    }
    
    private var instructionText: String {
        let themeCreature = getThemeCreatureName()
        if isFirstScreen {
            return "Remember these \(themeCreature)!"
        } else {
            return "Tap the \(themeCreature.dropLast()) that appeared in the previous screen"
        }
    }
    
    private func getThemeCreatureName() -> String {
        switch theme {
        case .forest: return "forest animals"
        case .sea: return "sea creatures"
        case .farm: return "farm animals"
        case .jungle: return "jungle animals"
        case .arctic: return "arctic animals"
        case .safari: return "safari animals"
        }
    }
}

// MARK: - Themed Scene
struct ThemedScene: View {
    let currentAnimals: [Int]
    let isFirstScreen: Bool
    let selectedAnimal: Int?
    let showFeedback: Bool
    let isCorrectAnswer: Bool
    let onAnimalTap: (Int) -> Void
    let theme: PuzzleTheme
    
    var body: some View {
        ZStack {
            // Theme-based background
            LinearGradient(
                colors: theme.backgroundColors,
                startPoint: .top,
                endPoint: .bottom
            )
            .cornerRadius(16)
            
            // Scene title
            VStack {
                HStack {
                    Text(theme.displayName)
                        .font(.system(size: 14, weight: .semibold))
                        .foregroundColor(.white)
                        .padding(.horizontal, 12)
                        .padding(.vertical, 6)
                        .background(Color.black.opacity(0.3))
                        .cornerRadius(8)
                    Spacer()
                }
                .padding(.top, 12)
                .padding(.horizontal, 16)
                
                Spacer()
                
                // Animals
                HStack(spacing: 40) {
                    ForEach(currentAnimals, id: \.self) { animalId in
                        if let animal = ThemeAnimals.getAnimal(id: animalId, for: theme) {
                            AnimatedAnimalHead(
                                animal: animal,
                                isClickable: !isFirstScreen,
                                isSelected: selectedAnimal == animalId,
                                showFeedback: showFeedback && selectedAnimal == animalId,
                                isCorrect: isCorrectAnswer,
                                onClick: { onAnimalTap(animalId) }
                            )
                        }
                    }
                }
                .frame(maxWidth: .infinity, maxHeight: .infinity)
                
                Spacer()
            }
        }
    }
}

// MARK: - Animated Animal Head
struct AnimatedAnimalHead: View {
    let animal: ThemeAnimal
    let isClickable: Bool
    let isSelected: Bool
    let showFeedback: Bool
    let isCorrect: Bool
    let onClick: () -> Void
    
    @State private var bounceOffset: CGFloat = 0
    @State private var scale: CGFloat = 1.0
    @State private var rotation: Double = 0
    
    var body: some View {
        ZStack {
            Circle()
                .fill(backgroundColor)
                .frame(width: 80, height: 80)
                .shadow(color: .black.opacity(0.2), radius: 4, x: 0, y: 2)
            
            Text(animal.emoji)
                .font(.system(size: 40))
                .rotationEffect(.degrees(rotation))
            
            if showFeedback && isSelected {
                VStack {
                    HStack {
                        Spacer()
                        Text(isCorrect ? "✅" : "❌")
                            .font(.system(size: 24, weight: .bold))
                            .foregroundColor(.white)
                            .frame(width: 30, height: 30)
                            .background(isCorrect ? Color.green : Color.red)
                            .clipShape(Circle())
                            .shadow(radius: 4)
                    }
                    Spacer()
                }
                .frame(width: 80, height: 80)
            }
        }
        .offset(y: bounceOffset)
        .scaleEffect(scale)
        .onTapGesture {
            if isClickable {
                // Add tap animation
                withAnimation(.spring(response: 0.3, dampingFraction: 0.6)) {
                    rotation += 360
                }
                onClick()
            }
        }
        .onAppear {
            startBounceAnimation()
        }
        .onChange(of: isSelected) { selected in
            if selected {
                withAnimation(.spring(response: 0.6, dampingFraction: 0.6)) {
                    scale = 1.1
                }
            } else {
                withAnimation(.spring(response: 0.3)) {
                    scale = 1.0
                }
            }
        }
    }
    
    private var backgroundColor: Color {
        if showFeedback && isCorrect {
            return Color.green.opacity(0.8)
        } else if showFeedback && !isCorrect {
            return Color.red.opacity(0.8)
        } else if isSelected {
            return animal.color.opacity(0.4)
        } else {
            return animal.color.opacity(0.15)
        }
    }
    
    private func startBounceAnimation() {
        withAnimation(
            Animation.easeInOut(duration: Double.random(in: 0.6...1.0))
                .repeatForever(autoreverses: true)
        ) {
            bounceOffset = Double.random(in: -20...(-10))
        }
    }
}

// MARK: - Theme Animal Data Structure
struct ThemeAnimal {
    let id: Int
    let name: String
    let emoji: String
    let color: Color
}

// MARK: - Animal Collections by Theme
struct ThemeAnimals {
    private static let forestAnimals: [Int: ThemeAnimal] = [
        1: ThemeAnimal(id: 1, name: "Lion", emoji: "🦁", color: Color(red: 0.83, green: 0.65, blue: 0.46)),
        2: ThemeAnimal(id: 2, name: "Hippo", emoji: "🦛", color: Color(red: 0.55, green: 0.49, blue: 0.42)),
        3: ThemeAnimal(id: 3, name: "Elephant", emoji: "🐘", color: Color(red: 0.66, green: 0.66, blue: 0.66)),
        4: ThemeAnimal(id: 4, name: "Tiger", emoji: "🐅", color: Color(red: 1.0, green: 0.55, blue: 0.0)),
        5: ThemeAnimal(id: 5, name: "Giraffe", emoji: "🦒", color: Color(red: 0.85, green: 0.65, blue: 0.13)),
        6: ThemeAnimal(id: 6, name: "Monkey", emoji: "🐵", color: Color(red: 0.80, green: 0.52, blue: 0.25)),
        7: ThemeAnimal(id: 7, name: "Bear", emoji: "🐻", color: Color(red: 0.55, green: 0.27, blue: 0.07)),
        8: ThemeAnimal(id: 8, name: "Wolf", emoji: "🐺", color: Color(red: 0.41, green: 0.41, blue: 0.41)),
        9: ThemeAnimal(id: 9, name: "Fox", emoji: "🦊", color: Color(red: 0.82, green: 0.41, blue: 0.12)),
        10: ThemeAnimal(id: 10, name: "Panda", emoji: "🐼", color: Color(red: 0.0, green: 0.0, blue: 0.0)),
        11: ThemeAnimal(id: 11, name: "Zebra", emoji: "🦓", color: Color(red: 0.0, green: 0.0, blue: 0.0)),
        12: ThemeAnimal(id: 12, name: "Rhino", emoji: "🦏", color: Color(red: 0.50, green: 0.50, blue: 0.50))
    ]
    
    private static let seaAnimals: [Int: ThemeAnimal] = [
        1: ThemeAnimal(id: 1, name: "Whale", emoji: "🐋", color: Color(red: 0.0, green: 0.4, blue: 0.8)),
        2: ThemeAnimal(id: 2, name: "Dolphin", emoji: "🐬", color: Color(red: 0.4, green: 0.6, blue: 0.9)),
        3: ThemeAnimal(id: 3, name: "Shark", emoji: "🦈", color: Color(red: 0.3, green: 0.3, blue: 0.3)),
        4: ThemeAnimal(id: 4, name: "Octopus", emoji: "🐙", color: Color(red: 0.6, green: 0.3, blue: 0.8)),
        5: ThemeAnimal(id: 5, name: "Fish", emoji: "🐠", color: Color(red: 1.0, green: 0.6, blue: 0.0)),
        6: ThemeAnimal(id: 6, name: "Jellyfish", emoji: "🪼", color: Color(red: 0.8, green: 0.4, blue: 0.8)),
        7: ThemeAnimal(id: 7, name: "Seahorse", emoji: "🐡", color: Color(red: 0.9, green: 0.7, blue: 0.2)),
        8: ThemeAnimal(id: 8, name: "Crab", emoji: "🦀", color: Color(red: 0.8, green: 0.2, blue: 0.2)),
        9: ThemeAnimal(id: 9, name: "Lobster", emoji: "🦞", color: Color(red: 0.7, green: 0.1, blue: 0.1)),
        10: ThemeAnimal(id: 10, name: "Seal", emoji: "🦭", color: Color(red: 0.4, green: 0.4, blue: 0.4)),
        11: ThemeAnimal(id: 11, name: "Turtle", emoji: "🐢", color: Color(red: 0.2, green: 0.6, blue: 0.2)),
        12: ThemeAnimal(id: 12, name: "Starfish", emoji: "⭐", color: Color(red: 1.0, green: 0.8, blue: 0.0))
    ]
    
    private static let farmAnimals: [Int: ThemeAnimal] = [
        1: ThemeAnimal(id: 1, name: "Cow", emoji: "🐄", color: Color(red: 0.0, green: 0.0, blue: 0.0)),
        2: ThemeAnimal(id: 2, name: "Pig", emoji: "🐷", color: Color(red: 1.0, green: 0.7, blue: 0.8)),
        3: ThemeAnimal(id: 3, name: "Sheep", emoji: "🐑", color: Color(red: 0.9, green: 0.9, blue: 0.9)),
        4: ThemeAnimal(id: 4, name: "Horse", emoji: "🐴", color: Color(red: 0.6, green: 0.3, blue: 0.1)),
        5: ThemeAnimal(id: 5, name: "Chicken", emoji: "🐔", color: Color(red: 1.0, green: 0.9, blue: 0.7)),
        6: ThemeAnimal(id: 6, name: "Duck", emoji: "🦆", color: Color(red: 1.0, green: 0.8, blue: 0.0)),
        7: ThemeAnimal(id: 7, name: "Goat", emoji: "🐐", color: Color(red: 0.8, green: 0.8, blue: 0.8)),
        8: ThemeAnimal(id: 8, name: "Turkey", emoji: "🦃", color: Color(red: 0.6, green: 0.3, blue: 0.1)),
        9: ThemeAnimal(id: 9, name: "Rooster", emoji: "🐓", color: Color(red: 0.8, green: 0.2, blue: 0.0)),
        10: ThemeAnimal(id: 10, name: "Rabbit", emoji: "🐰", color: Color(red: 0.9, green: 0.9, blue: 0.9)),
        11: ThemeAnimal(id: 11, name: "Cat", emoji: "🐱", color: Color(red: 0.8, green: 0.6, blue: 0.4)),
        12: ThemeAnimal(id: 12, name: "Dog", emoji: "🐶", color: Color(red: 0.6, green: 0.4, blue: 0.2))
    ]
    
    private static let jungleAnimals: [Int: ThemeAnimal] = [
        1: ThemeAnimal(id: 1, name: "Gorilla", emoji: "🦍", color: Color(red: 0.2, green: 0.2, blue: 0.2)),
        2: ThemeAnimal(id: 2, name: "Leopard", emoji: "🐆", color: Color(red: 1.0, green: 0.8, blue: 0.4)),
        3: ThemeAnimal(id: 3, name: "Parrot", emoji: "🦜", color: Color(red: 0.0, green: 0.8, blue: 0.0)),
        4: ThemeAnimal(id: 4, name: "Snake", emoji: "🐍", color: Color(red: 0.4, green: 0.6, blue: 0.2)),
        5: ThemeAnimal(id: 5, name: "Toucan", emoji: "🦆", color: Color(red: 1.0, green: 0.6, blue: 0.0)),
        6: ThemeAnimal(id: 6, name: "Sloth", emoji: "🦥", color: Color(red: 0.6, green: 0.4, blue: 0.2)),
        7: ThemeAnimal(id: 7, name: "Jaguar", emoji: "🐆", color: Color(red: 1.0, green: 0.6, blue: 0.0)),
        8: ThemeAnimal(id: 8, name: "Frog", emoji: "🐸", color: Color(red: 0.2, green: 0.8, blue: 0.2)),
        9: ThemeAnimal(id: 9, name: "Butterfly", emoji: "🦋", color: Color(red: 0.8, green: 0.2, blue: 0.8)),
        10: ThemeAnimal(id: 10, name: "Chameleon", emoji: "🦎", color: Color(red: 0.4, green: 0.8, blue: 0.4)),
        11: ThemeAnimal(id: 11, name: "Orangutan", emoji: "🦧", color: Color(red: 0.8, green: 0.4, blue: 0.0)),
        12: ThemeAnimal(id: 12, name: "Crocodile", emoji: "🐊", color: Color(red: 0.2, green: 0.6, blue: 0.2))
    ]
    
    private static let arcticAnimals: [Int: ThemeAnimal] = [
        1: ThemeAnimal(id: 1, name: "Polar Bear", emoji: "🐻‍❄️", color: Color(red: 1.0, green: 1.0, blue: 1.0)),
        2: ThemeAnimal(id: 2, name: "Penguin", emoji: "🐧", color: Color(red: 0.0, green: 0.0, blue: 0.0)),
        3: ThemeAnimal(id: 3, name: "Walrus", emoji: "🦭", color: Color(red: 0.6, green: 0.4, blue: 0.2)),
        4: ThemeAnimal(id: 4, name: "Arctic Fox", emoji: "🦊", color: Color(red: 1.0, green: 1.0, blue: 1.0)),
        5: ThemeAnimal(id: 5, name: "Seal", emoji: "🦭", color: Color(red: 0.4, green: 0.4, blue: 0.4)),
        6: ThemeAnimal(id: 6, name: "Narwhal", emoji: "🦄", color: Color(red: 0.8, green: 0.9, blue: 1.0)),
        7: ThemeAnimal(id: 7, name: "Snowy Owl", emoji: "🦉", color: Color(red: 0.9, green: 0.9, blue: 0.9)),
        8: ThemeAnimal(id: 8, name: "Caribou", emoji: "🦌", color: Color(red: 0.6, green: 0.4, blue: 0.2)),
        9: ThemeAnimal(id: 9, name: "Arctic Hare", emoji: "🐰", color: Color(red: 1.0, green: 1.0, blue: 1.0)),
        10: ThemeAnimal(id: 10, name: "Musk Ox", emoji: "🦏", color: Color(red: 0.3, green: 0.2, blue: 0.1)),
        11: ThemeAnimal(id: 11, name: "Beluga Whale", emoji: "🐋", color: Color(red: 1.0, green: 1.0, blue: 1.0)),
        12: ThemeAnimal(id: 12, name: "Arctic Tern", emoji: "🐦", color: Color(red: 0.8, green: 0.8, blue: 0.8))
    ]
    
    private static let safariAnimals: [Int: ThemeAnimal] = [
        1: ThemeAnimal(id: 1, name: "Lion", emoji: "🦁", color: Color(red: 0.83, green: 0.65, blue: 0.46)),
        2: ThemeAnimal(id: 2, name: "Cheetah", emoji: "🐆", color: Color(red: 1.0, green: 0.8, blue: 0.4)),
        3: ThemeAnimal(id: 3, name: "Gazelle", emoji: "🦌", color: Color(red: 0.8, green: 0.6, blue: 0.4)),
        4: ThemeAnimal(id: 4, name: "Meerkat", emoji: "🦫", color: Color(red: 0.7, green: 0.5, blue: 0.3)),
        5: ThemeAnimal(id: 5, name: "Ostrich", emoji: "🦢", color: Color(red: 0.0, green: 0.0, blue: 0.0)),
        6: ThemeAnimal(id: 6, name: "Hyena", emoji: "🐺", color: Color(red: 0.6, green: 0.5, blue: 0.4)),
        7: ThemeAnimal(id: 7, name: "Warthog", emoji: "🐗", color: Color(red: 0.4, green: 0.3, blue: 0.2)),
        8: ThemeAnimal(id: 8, name: "Baboon", emoji: "🐵", color: Color(red: 0.6, green: 0.4, blue: 0.3)),
        9: ThemeAnimal(id: 9, name: "Antelope", emoji: "🦌", color: Color(red: 0.7, green: 0.5, blue: 0.3)),
        10: ThemeAnimal(id: 10, name: "Vulture", emoji: "🦅", color: Color(red: 0.3, green: 0.3, blue: 0.3)),
        11: ThemeAnimal(id: 11, name: "Flamingo", emoji: "🦩", color: Color(red: 1.0, green: 0.4, blue: 0.7)),
        12: ThemeAnimal(id: 12, name: "Giraffe", emoji: "🦒", color: Color(red: 0.85, green: 0.65, blue: 0.13))
    ]
    
    static func getAnimal(id: Int, for theme: PuzzleTheme) -> ThemeAnimal? {
        let animals = getAnimalsCollection(for: theme)
        return animals[id]
    }
    
    private static func getAnimalsCollection(for theme: PuzzleTheme) -> [Int: ThemeAnimal] {
        switch theme {
        case .forest: return forestAnimals
        case .sea: return seaAnimals
        case .farm: return farmAnimals
        case .jungle: return jungleAnimals
        case .arctic: return arcticAnimals
        case .safari: return safariAnimals
        }
    }
}

// MARK: - Enhanced Score Card
struct ScoreCard: View {
    let score: Int
    let correctAnswers: Int
    let totalQuestions: Int
    let theme: PuzzleTheme
    
    var body: some View {
        VStack(spacing: 8) {
            HStack {
                VStack(alignment: .leading) {
                    if score > 0 {
                        Text("Score: \(score)")
                            .font(.system(size: 18, weight: .bold))
                            .foregroundColor(Color.green)
                    } else {
                        Text("Memory Pair Challenge")
                            .font(.system(size: 18, weight: .bold))
                            .foregroundColor(.white)
                    }
                    
                    Text(theme.displayName)
                        .font(.system(size: 12, weight: .medium))
                        .foregroundColor(Color(red: 0.69, green: 0.69, blue: 0.69))
                }
                
                Spacer()
                
                VStack(alignment: .trailing) {
                    Text("Correct: \(correctAnswers)/\(totalQuestions)")
                        .font(.system(size: 14))
                        .foregroundColor(Color(red: 0.69, green: 0.69, blue: 0.69))
                    
                    let percentage = totalQuestions > 0 ? (correctAnswers * 100) / totalQuestions : 0
                    Text("\(percentage)% Accuracy")
                        .font(.system(size: 12))
                        .foregroundColor(percentage >= 60 ? Color.green : Color.orange)
                }
            }
        }
        .padding(16)
        .background(Color(red: 0.18, green: 0.18, blue: 0.18))
        .cornerRadius(12)
    }
}

// MARK: - Puzzle Extension
extension Puzzle {
    var memoryPreviousPairPuzzleData: MemoryPreviousPairPuzzleData? {
        guard let data = question.data(using: .utf8) else { return nil }
        return try? JSONDecoder().decode(MemoryPreviousPairPuzzleData.self, from: data)
    }
}
