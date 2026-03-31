//
//  AntonymPair.swift
//  PuzzleForge
//
//  Created by Sushanth Tiruvaipati on 7/12/25.
//


//
//  AntonymPuzzleView.swift
//  PuzzleForge
//
//  Created by Assistant on 12/7/25.
//

import SwiftUI
import Combine

// MARK: - Data Models
struct AntonymPair: Identifiable, Hashable {
    let id: Int
    let word1: String
    let word2: String
    
    func contains(_ word: String) -> Bool {
        return word1 == word || word2 == word
    }
    
    func getPartner(for word: String) -> String? {
        if word1 == word { return word2 }
        if word2 == word { return word1 }
        return nil
    }
}

struct BalloonItem: Identifiable, Hashable {
    let id: String
    let word: String
    let pairId: Int
    let position: CGPoint
    let color: Color
    var isSelected: Bool = false
    var isBurst: Bool = false
    var isMatched: Bool = false
    
    static func == (lhs: BalloonItem, rhs: BalloonItem) -> Bool {
        lhs.id == rhs.id
    }
    
    func hash(into hasher: inout Hasher) {
        hasher.combine(id)
    }
}

// MARK: - View Model
class AntonymPuzzleViewModel: ObservableObject {
    @Published var balloons: [BalloonItem] = []
    @Published var selectedBalloon: BalloonItem?
    @Published var antonymPairs: [AntonymPair] = []
    @Published var matchedPairs = 0
    @Published var isGameComplete = false
    @Published var currentHearts = 3
    @Published var timeRemaining = 150 // 2:30 default
    @Published var gamePhase = 1
    @Published var showFeedback = false
    @Published var feedbackMessage = ""
    @Published var isCorrectFeedback = false
    
    private var timer: AnyCancellable?
    
    let balloonColors: [Color] = [
        .pink, .blue, .green, .orange, .purple, .cyan, .yellow, .red
    ]
    
    func setupPuzzle(puzzleData: String, screenSize: CGSize) {
        guard !puzzleData.isEmpty else {
            print("❌ ANTONYM: Empty puzzle data")
            return
        }
        
        do {
            let pairs = try parseAntonymData(puzzleData)
            self.antonymPairs = pairs
            self.matchedPairs = 0
            self.isGameComplete = false
            self.currentHearts = 3
            self.gamePhase = 1
            
            generateBalloons(from: pairs, screenSize: screenSize)
            startTimer()
            
            print("✅ ANTONYM: Successfully set up puzzle with \(pairs.count) pairs")
        } catch {
            print("❌ ANTONYM: Failed to parse puzzle data: \(error)")
        }
    }
    
    private func parseAntonymData(_ puzzleData: String) throws -> [AntonymPair] {
        let data = puzzleData.data(using: .utf8)!
        
        if puzzleData.hasPrefix("[") {
            // JSON array format: [["wet","dry"],["near","far"],...]
            let pairsArray = try JSONSerialization.jsonObject(with: data) as! [[String]]
            return pairsArray.enumerated().map { index, pair in
                AntonymPair(id: index, word1: pair[0], word2: pair[1])
            }
        } else if puzzleData.hasPrefix("{") {
            // JSON object format: {"pairs": [{"word1":"hot","word2":"cold"}]}
            let json = try JSONSerialization.jsonObject(with: data) as! [String: Any]
            let pairsArray = json["pairs"] as! [[String: String]]
            return pairsArray.enumerated().map { index, pair in
                AntonymPair(id: index, word1: pair["word1"]!, word2: pair["word2"]!)
            }
        } else {
            throw NSError(domain: "AntonymParsing", code: 1, userInfo: [NSLocalizedDescriptionKey: "Unknown format"])
        }
    }
    
    private func generateBalloons(from pairs: [AntonymPair], screenSize: CGSize) {
        var allWords: [String] = []
        pairs.forEach { pair in
            allWords.append(pair.word1)
            allWords.append(pair.word2)
        }
        allWords.shuffle()
        
        // Calculate positioning constraints
        let balloonSize: CGFloat = 80
        let padding: CGFloat = 20
        let minSpacing: CGFloat = 100
        
        let availableWidth = screenSize.width - padding * 2 - balloonSize
        let availableHeight = screenSize.height * 0.6 - balloonSize // Use 60% of screen height
        
        var usedPositions: [CGPoint] = []
        var balloonItems: [BalloonItem] = []
        
        for (index, word) in allWords.enumerated() {
            let pair = pairs.first { $0.contains(word) }!
            let colorIndex = pair.id % balloonColors.count
            
            // Find non-overlapping position
            var position: CGPoint
            var attempts = 0
            repeat {
                position = CGPoint(
                    x: CGFloat.random(in: 0...max(0, availableWidth)),
                    y: CGFloat.random(in: 0...max(0, availableHeight))
                )
                attempts += 1
            } while attempts < 50 && usedPositions.contains { abs($0.x - position.x) < minSpacing && abs($0.y - position.y) < minSpacing }
            
            usedPositions.append(position)
            
            let balloon = BalloonItem(
                id: "\(word)_\(index)",
                word: word,
                pairId: pair.id,
                position: position,
                color: balloonColors[colorIndex]
            )
            
            balloonItems.append(balloon)
        }
        
        self.balloons = balloonItems
    }
    
    func handleBalloonTap(_ balloon: BalloonItem) {
        guard !balloon.isBurst && !balloon.isMatched && !isGameComplete else { return }
        
        if selectedBalloon == nil {
            // First balloon selected
            selectBalloon(balloon)
        } else if selectedBalloon?.id == balloon.id {
            // Same balloon tapped - deselect
            deselectAllBalloons()
        } else {
            // Second balloon selected - check for match
            checkForMatch(balloon)
        }
    }
    
    private func selectBalloon(_ balloon: BalloonItem) {
        selectedBalloon = balloon
        updateBalloonSelection(balloon.id, isSelected: true)
    }
    
    private func deselectAllBalloons() {
        selectedBalloon = nil
        balloons = balloons.map { balloon in
            var updated = balloon
            updated.isSelected = false
            return updated
        }
    }
    
    private func updateBalloonSelection(_ balloonId: String, isSelected: Bool) {
        balloons = balloons.map { balloon in
            var updated = balloon
            if balloon.id == balloonId {
                updated.isSelected = isSelected
            } else {
                updated.isSelected = false
            }
            return updated
        }
    }
    
    private func checkForMatch(_ secondBalloon: BalloonItem) {
        guard let firstBalloon = selectedBalloon else { return }
        
        if firstBalloon.pairId == secondBalloon.pairId {
            // Correct match!
            handleCorrectMatch(firstBalloon, secondBalloon)
        } else {
            // Incorrect match
            handleIncorrectMatch(firstBalloon, secondBalloon)
        }
    }
    
    private func handleCorrectMatch(_ balloon1: BalloonItem, _ balloon2: BalloonItem) {
        let pair = antonymPairs.first { $0.id == balloon1.pairId }!
        
        // Mark balloons as burst/matched
        balloons = balloons.map { balloon in
            var updated = balloon
            if balloon.pairId == balloon1.pairId {
                updated.isBurst = true
                updated.isMatched = true
                updated.isSelected = false
            } else {
                updated.isSelected = false
            }
            return updated
        }
        
        matchedPairs += 1
        selectedBalloon = nil
        
        // Show positive feedback
        showCorrectFeedback(for: pair)
        
        // Check for completion
        if matchedPairs == antonymPairs.count {
            completeGame(success: true)
        }
    }
    
    private func handleIncorrectMatch(_ balloon1: BalloonItem, _ balloon2: BalloonItem) {
        currentHearts = max(0, currentHearts - 1)
        deselectAllBalloons()
        
        // Show negative feedback
        showIncorrectFeedback(balloon1.word, balloon2.word)
        
        // Check for game over
        if currentHearts <= 0 {
            completeGame(success: false)
        }
    }
    
    private func showCorrectFeedback(for pair: AntonymPair) {
        feedbackMessage = "✅ Perfect! \(pair.word1) ↔ \(pair.word2)"
        isCorrectFeedback = true
        showFeedback = true
        
        DispatchQueue.main.asyncAfter(deadline: .now() + 1.5) {
            self.showFeedback = false
        }
    }
    
    private func showIncorrectFeedback(_ word1: String, _ word2: String) {
        feedbackMessage = "❌ \(word1) and \(word2) are not antonyms"
        isCorrectFeedback = false
        showFeedback = true
        
        DispatchQueue.main.asyncAfter(deadline: .now() + 1.5) {
            self.showFeedback = false
        }
    }
    
    private func startTimer() {
        timer = Timer.publish(every: 1, on: .main, in: .common)
            .autoconnect()
            .sink { [weak self] _ in
                guard let self = self else { return }
                if self.timeRemaining > 0 && !self.isGameComplete {
                    self.timeRemaining -= 1
                } else if self.timeRemaining <= 0 && !self.isGameComplete {
                    self.completeGame(success: false)
                }
            }
    }
    
    private func completeGame(success: Bool) {
        isGameComplete = true
        timer?.cancel()
        
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.5) {
            // Game completion will be handled by the parent view
        }
    }
    
    deinit {
        timer?.cancel()
    }
}

// MARK: - Balloon View Component
struct BalloonView: View {
    let balloon: BalloonItem
    let onTap: () -> Void
    
    @State private var floatOffset: CGFloat = 0
    @State private var scale: CGFloat = 1.0
    
    var body: some View {
        VStack(spacing: 0) {
            // Balloon
            ZStack {
                Circle()
                    .fill(balloon.color)
                    .frame(width: 80, height: 80)
                    .overlay(
                        Circle()
                            .stroke(balloon.isSelected ? Color.white : Color.gray.opacity(0.3), 
                                   lineWidth: balloon.isSelected ? 3 : 1)
                    )
                    .scaleEffect(scale)
                    .offset(y: floatOffset)
                
                Text(balloon.word)
                    .font(.system(size: 12, weight: .bold))
                    .foregroundColor(.white)
                    .multilineTextAlignment(.center)
                    .offset(y: floatOffset)
            }
            
            // String
            Rectangle()
                .fill(Color.black.opacity(0.6))
                .frame(width: 2, height: 20)
        }
        .opacity(balloon.isBurst ? 0 : 1)
        .animation(.easeOut(duration: 0.3), value: balloon.isBurst)
        .onTapGesture {
            onTap()
        }
        .onAppear {
            startFloatingAnimation()
            scale = balloon.isSelected ? 1.1 : 1.0
        }
        .onChange(of: balloon.isSelected) { isSelected in
            withAnimation(.spring(response: 0.3, dampingFraction: 0.6)) {
                scale = isSelected ? 1.1 : 1.0
            }
        }
    }
    
    private func startFloatingAnimation() {
        withAnimation(.easeInOut(duration: 2.0).repeatForever(autoreverses: true)) {
            floatOffset = 10
        }
    }
}

// MARK: - Main Antonym Puzzle View
struct AntonymPuzzleView: View {
    let puzzle: Puzzle
    let questionIndex: Int
    let totalQuestions: Int
    let onAnswerSubmitted: (Bool, Bool) -> Void
    let onNextPuzzle: () -> Void
    let onExit: () -> Void
    
    @StateObject private var viewModel = AntonymPuzzleViewModel()
    @State private var showGameComplete = false
    
    var body: some View {
            GeometryReader { geometry in
                ZStack {
                    // Background gradient
                    LinearGradient(
                        colors: [Color(red: 0.89, green: 0.77, blue: 0.63), Color(red: 0.83, green: 0.65, blue: 0.46)],
                        startPoint: .top,
                        endPoint: .bottom
                    )
                    .ignoresSafeArea()
                    
                    VStack(spacing: 0) {
                        // Top bar
                        topBar
                        
                        // Game instructions
                        instructionsCard
                            .padding(.horizontal)
                            .padding(.top, 16)
                        
                        // Balloons area
                        ZStack {
                            ForEach(viewModel.balloons) { balloon in
                                BalloonView(balloon: balloon) {
                                    viewModel.handleBalloonTap(balloon)
                                }
                                .position(balloon.position)
                            }
                        }
                        .frame(maxWidth: .infinity, maxHeight: .infinity)
                        .padding(.top, 24)
                        
                        Spacer()
                    }
                    
                    // Feedback overlay
                    if viewModel.showFeedback {
                        feedbackOverlay
                    }
                    
                    // Game completion overlay
                    if showGameComplete {
                        gameCompletionOverlay
                    }
                }.onAppear {
                    viewModel.setupPuzzle(puzzleData: puzzle.question, screenSize: geometry.size)
                }
            }
            .navigationBarHidden(true)
            
            .onChange(of: viewModel.isGameComplete) { isComplete in
                if isComplete {
                    showGameComplete = true
                }
            }
        }
    
    private var topBar: some View {
        HStack {
            Button(action: onExit) {
                Image(systemName: "arrow.left")
                    .font(.title2)
                    .foregroundColor(.black)
            }
            
            Spacer()
            
            Text(puzzle.difficulty.uppercased())
                .font(.system(size: 14, weight: .bold))
                .foregroundColor(.black)
            
            Spacer()
            
            // Hearts
            HStack(spacing: 4) {
                ForEach(0..<3) { index in
                    Image(systemName: "heart.fill")
                        .foregroundColor(index < viewModel.currentHearts ? .red : .gray)
                        .font(.system(size: 16))
                }
            }
        }
        .padding(.horizontal)
        .padding(.top, 8)
    }
    
    private var timerAndProgress: some View {
        HStack {
            // Timer
            HStack(spacing: 4) {
                Image(systemName: "timer")
                    .foregroundColor(Color(red: 0.54, green: 0.30, blue: 1.0))
                Text("\(viewModel.timeRemaining / 60):\(String(format: "%02d", viewModel.timeRemaining % 60))")
                    .font(.system(size: 14, weight: .bold))
                    .foregroundColor(.black)
            }
            .padding(.horizontal, 12)
            .padding(.vertical, 6)
            .background(Color.white.opacity(0.9))
            .cornerRadius(20)
            
            Spacer()
            
            // Level
            Text("\(questionIndex + 1)/\(totalQuestions)")
                .font(.system(size: 14, weight: .bold))
                .foregroundColor(.black)
        }
        .padding(.horizontal)
        .padding(.top, 16)
    }
    
    private var instructionsCard: some View {
        VStack(spacing: 8) {
            Text("🎈 Match Antonym Pairs 🎈")
                .font(.system(size: 18, weight: .bold))
                .foregroundColor(Color(red: 0.54, green: 0.30, blue: 1.0))
            
            Text("Tap balloons to match opposite words")
                .font(.system(size: 14))
                .foregroundColor(.gray)
            
            Text("Progress: \(viewModel.matchedPairs) / \(viewModel.antonymPairs.count) pairs")
                .font(.system(size: 12))
                .foregroundColor(.gray)
        }
        .padding()
        .background(Color.white.opacity(0.9))
        .cornerRadius(12)
    }
    
    private var feedbackOverlay: some View {
        VStack {
            Spacer()
            
            Text(viewModel.feedbackMessage)
                .font(.system(size: 16, weight: .semibold))
                .foregroundColor(.white)
                .padding(.horizontal, 20)
                .padding(.vertical, 12)
                .background(
                    RoundedRectangle(cornerRadius: 25)
                        .fill(viewModel.isCorrectFeedback ? Color.green : Color.red)
                )
                .shadow(radius: 8)
            
            Spacer()
                .frame(height: 100)
        }
        .transition(.opacity.combined(with: .scale))
        .zIndex(1)
    }
    
    private var gameCompletionOverlay: some View {
        ZStack {
            Color.black.opacity(0.7)
                .ignoresSafeArea()
            
            VStack(spacing: 20) {
                let success = viewModel.matchedPairs == viewModel.antonymPairs.count
                
                Text(success ? "🎉 Puzzle Complete!" : "💔 Game Over")
                    .font(.system(size: 24, weight: .bold))
                    .foregroundColor(success ? .green : .red)
                
                Text(success ? "Great job matching all antonym pairs!" :
                     viewModel.currentHearts <= 0 ? "No hearts remaining" : "Time's up!")
                    .font(.system(size: 16))
                    .foregroundColor(.gray)
                    .multilineTextAlignment(.center)
                
                Button("Continue") {
                    onAnswerSubmitted(success, viewModel.timeRemaining > 0)
                    onNextPuzzle()
                }
                .font(.system(size: 18, weight: .semibold))
                .foregroundColor(.white)
                .padding(.horizontal, 30)
                .padding(.vertical, 12)
                .background(Color(red: 0.54, green: 0.30, blue: 1.0))
                .cornerRadius(25)
            }
            .padding(32)
            .background(
                RoundedRectangle(cornerRadius: 20)
                    .fill(Color(.systemBackground))
            )
            .padding(.horizontal, 32)
        }
    }
    
    private func setupPuzzle(_ screenSize: CGSize) {
        // Add timer and progress after top bar
        viewModel.setupPuzzle(puzzleData: puzzle.question, screenSize: screenSize)
    }
}

// MARK: - Preview
struct AntonymPuzzleView_Previews: PreviewProvider {
    static var previews: some View {
        let samplePuzzle = Puzzle(
            question: "[[\"hot\",\"cold\"],[\"big\",\"small\"],[\"happy\",\"sad\"],[\"fast\",\"slow\"]]",
            answer: "correct",
            hint: "Match opposite words",
            options: [],
            format: "antonym",
            puzzleType: "antonym",
            puzzleId: "sample",
            id: "sample",
            name: "Antonym Test",
            createdAt: Date().timeIntervalSince1970 * 1000,
            status: "ready",
            difficulty: "Medium"
        )
        
        AntonymPuzzleView(
            puzzle: samplePuzzle,
            questionIndex: 0,
            totalQuestions: 1,
            onAnswerSubmitted: { _, _ in },
            onNextPuzzle: { },
            onExit: { }
        )
    }
}
