//
//  WordSnakeCell.swift
//  PuzzleForge
//
//  Created by Sushanth Tiruvaipati on 8/6/25.
//

import SwiftUI
import Foundation

// MARK: - Data Models
struct WordSnakeCell: Identifiable {
    let id = UUID()
    let row: Int
    let col: Int
    let letter: String
    var isHighlighted: Bool = false
    var isFound: Bool = false
    var isRevealed: Bool = false // NEW: For show button functionality
    var foundWordId: String? = nil
    var pathIndex: Int = -1
    var isSelected: Bool = false
    var selectionOrder: Int = -1
}

struct WordSnakeWord: Identifiable {
    let id = UUID()
    let word: String
    let clue: String
    let path: [WSGridPosition]
    let color: String
    var found: Bool = false
    var revealed: Bool = false // NEW: For show button functionality
}

struct WSGridPosition: Equatable, Hashable {
    let row: Int
    let col: Int
}

struct WordSnakePath {
    let word: String
    let path: [WSGridPosition]
    let color: Color
    let isFound: Bool
    let isRevealed: Bool // NEW: Track if path was revealed
}

struct WordSnakeCompletionEvent {
    let word: String
    let timestamp: Date
    let timeFromStart: TimeInterval
    let method: String
}

// MARK: - Word Snake Puzzle Data
struct WordSnakePuzzleData {
    let grid: [[String]]
    let words: [WordSnakeWord]
    let gridSize: Int
    let timeLimit: Int
    let difficulty: String
    
    init?(from jsonString: String) {
        guard let data = jsonString.data(using: .utf8),
              let json = try? JSONSerialization.jsonObject(with: data) as? [String: Any] else {
            return nil
        }
        
        guard let gridArray = json["grid"] as? [[String]],
              let wordsArray = json["words"] as? [[String: Any]],
              let size = json["gridSize"] as? Int else {
            return nil
        }
        
        self.grid = gridArray
        self.gridSize = size
        self.difficulty = json["difficulty"] as? String ?? "Medium"
        self.timeLimit = json["timeLimit"] as? Int ?? 240
        
        self.words = wordsArray.compactMap { wordDict in
            guard let word = wordDict["word"] as? String,
                  let clue = wordDict["clue"] as? String,
                  let pathArray = wordDict["path"] as? [[String: Int]],
                  let colorString = wordDict["color"] as? String else {
                return nil
            }
            
            let path = pathArray.compactMap { posDict -> WSGridPosition? in
                guard let row = posDict["row"],
                      let col = posDict["col"] else { return nil }
                return WSGridPosition(row: row, col: col)
            }
            
            return WordSnakeWord(
                word: word,
                clue: clue,
                path: path,
                color: colorString,
                found: wordDict["found"] as? Bool ?? false
            )
        }
    }
}

// MARK: - Main Word Snake Puzzle View
struct WordSnakePuzzleView: View {
    let puzzle: Puzzle
    let questionIndex: Int
    let totalQuestions: Int
    let onAnswerSubmitted: (Bool) -> Void
    let onNextPuzzle: () -> Void
    let onExit: () -> Void
    
    @State private var wordSnakeData: WordSnakePuzzleData?
    @State private var cellGrid: [[WordSnakeCell]] = []
    @State private var foundWords: Set<String> = []
    @State private var revealedWords: Set<String> = [] // NEW: Track revealed words
    @State private var foundPaths: [WordSnakePath] = []
    @State private var selectedCells: [WSGridPosition] = []
    @State private var currentSelectionWord: String = ""
    @State private var timeRemaining: Int = 240
    @State private var totalScore: Int = 0
    @State private var currentStreak: Int = 0
    @State private var bestStreak: Int = 0
    @State private var hintUsed: Bool = false
    @State private var isCompleted: Bool = false
    @State private var showCompletionDialog: Bool = false
    @State private var showHintsDialog: Bool = false
    @State private var gameStartTime: Date = Date()
    @State private var wordsFoundEvents: [WordSnakeCompletionEvent] = []
    @State private var displayTimer: String = "4:00"
    @State private var showClearWordDialog: Bool = false
    @State private var selectedWordToClear: String? = nil
    
    // Timer
    @State private var timer: Timer?
    
    private let cellSize: CGFloat = 40
    private let gridSpacing: CGFloat = 2
    
    var body: some View {
        GeometryReader { geometry in
            ZStack {
                // Background
                LinearGradient(
                    colors: [Color(red: 0.17, green: 0.24, blue: 0.31), Color(red: 0.20, green: 0.29, blue: 0.37)],
                    startPoint: .topLeading,
                    endPoint: .bottomTrailing
                )
                .ignoresSafeArea()
                
                if let data = wordSnakeData {
                    VStack(spacing: 0) {
                        // Top Bar
                        topBarView
                        
                        // Progress Card
                        progressCardView(data: data)
                        
                        // Grid Container
                        gridContainerView(data: data, geometry: geometry)
                        
                        // Clues Section
                        cluesSection(data: data)
                        
                        // Control Buttons
                        controlButtonsView
                    }
                } else {
                    loadingView
                }
                
                // Completion Dialog
                if showCompletionDialog {
                    completionDialogView
                }
                
                // Hints Dialog
                if showHintsDialog && wordSnakeData != nil {
                    hintsDialogView(data: wordSnakeData!)
                }
            }
        }
        .onAppear {
            setupPuzzle()
            startTimer()
        }
        .onDisappear {
            timer?.invalidate()
        }
        .navigationBarHidden(true)
    }
    
    // MARK: - Top Bar
    private var topBarView: some View {
        HStack {
            Button(action: onExit) {
                Image(systemName: "arrow.left")
                    .font(.title2)
                    .foregroundColor(.white)
            }
            
            Spacer()
            
            Text(displayTimer)
                .font(.title2)
                .fontWeight(.bold)
                .foregroundColor(timeRemaining <= 30 ? .red : .white)
            
            Spacer()
            
            Button(action: {
                showHintsDialog = true
                hintUsed = true
            }) {
                Image(systemName: "questionmark.circle")
                    .font(.title2)
                    .foregroundColor(.white)
            }
        }
        .padding(.horizontal)
        .padding(.vertical, 8)
    }
    
    // MARK: - Progress Card (UPDATED with revealed words)
    private func progressCardView(data: WordSnakePuzzleData) -> some View {
        VStack(spacing: 8) {
            HStack {
                Text("Words: \(foundWords.count + revealedWords.count)/\(data.words.count)")
                    .font(.subheadline)
                    .fontWeight(.semibold)
                
                Spacer()
                
                ProgressView(value: Double(foundWords.count + revealedWords.count), total: Double(data.words.count))
                    .frame(width: 100)
                    .tint(.green)
            }
            
            // Show breakdown if there are revealed words
            if revealedWords.count > 0 {
                HStack {
                    HStack(spacing: 4) {
                        Image(systemName: "checkmark.circle.fill")
                            .font(.caption)
                            .foregroundColor(.green)
                        Text("Found: \(foundWords.count)")
                            .font(.caption)
                            .fontWeight(.semibold)
                            .foregroundColor(.green)
                    }
                    
                    HStack(spacing: 4) {
                        Image(systemName: "eye.fill")
                            .font(.caption)
                            .foregroundColor(.orange)
                        Text("Shown: \(revealedWords.count)")
                            .font(.caption)
                            .fontWeight(.semibold)
                            .foregroundColor(.orange)
                    }
                    
                    Spacer()
                }
            }
            
            if totalScore > 0 || currentStreak > 0 {
                HStack {
                    if totalScore > 0 {
                        Text("Score: \(totalScore)")
                            .font(.caption)
                            .fontWeight(.semibold)
                            .foregroundColor(.green)
                    }
                    
                    Spacer()
                    
                    if currentStreak > 1 {
                        Text("🔥\(currentStreak)")
                            .font(.caption)
                            .fontWeight(.semibold)
                            .foregroundColor(.orange)
                    }
                }
            }
            
            if !currentSelectionWord.isEmpty {
                Text("Current: \(currentSelectionWord)")
                    .font(.caption)
                    .fontWeight(.semibold)
                    .foregroundColor(.blue)
            }
        }
        .padding()
        .background(Color.white.opacity(0.95))
        .cornerRadius(12)
        .padding(.horizontal)
    }
    
    // MARK: - Grid Container
    private func gridContainerView(data: WordSnakePuzzleData, geometry: GeometryProxy) -> some View {
        let availableWidth = geometry.size.width - 32
        let availableHeight = geometry.size.height * 0.4
        let calculatedCellSize = min(availableWidth / CGFloat(data.gridSize), availableHeight / CGFloat(data.gridSize))
        let finalCellSize = min(calculatedCellSize, 45)
        
        return VStack {
            ZStack {
                // Snake Paths - Draw BEHIND the grid
                Canvas { context, size in
                    // Draw found paths
                    for snakePath in foundPaths {
                        drawSnakePath(
                            context: context,
                            path: snakePath,
                            cellSize: finalCellSize,
                            gridSize: data.gridSize,
                            canvasSize: size
                        )
                    }
                    
                    // Draw current selection path
                    if selectedCells.count >= 2 {
                        drawSelectionPath(
                            context: context,
                            path: selectedCells,
                            cellSize: finalCellSize,
                            gridSize: data.gridSize,
                            canvasSize: size
                        )
                    }
                }
                .frame(
                    width: finalCellSize * CGFloat(data.gridSize) + gridSpacing * CGFloat(data.gridSize - 1),
                    height: finalCellSize * CGFloat(data.gridSize) + gridSpacing * CGFloat(data.gridSize - 1)
                )
                .allowsHitTesting(false) // Canvas doesn't intercept touches
                
                // Grid - Draw ON TOP so it can receive touches
                VStack(spacing: gridSpacing) {
                    ForEach(0..<data.gridSize, id: \.self) { row in
                        HStack(spacing: gridSpacing) {
                            ForEach(0..<data.gridSize, id: \.self) { col in
                                if row < cellGrid.count && col < cellGrid[row].count {
                                    WordSnakeCellView(
                                        cell: cellGrid[row][col],
                                        size: finalCellSize,
                                        isSelected: selectedCells.contains(WSGridPosition(row: row, col: col)),
                                        selectionOrder: selectedCells.firstIndex(of: WSGridPosition(row: row, col: col)) ?? -1,
                                        onTap: {
                                            handleCellTap(WSGridPosition(row: row, col: col))
                                        }
                                    )
                                } else {
                                    // Fallback cell if grid is malformed
                                    Rectangle()
                                        .fill(Color.red)
                                        .frame(width: finalCellSize, height: finalCellSize)
                                        .overlay(
                                            Text("!")
                                                .foregroundColor(.white)
                                                .font(.caption)
                                        )
                                }
                            }
                        }
                    }
                }
            }
        }
        .padding()
    }
    
    // MARK: - Clues Section (UPDATED with Show buttons)
    private func cluesSection(data: WordSnakePuzzleData) -> some View {
        VStack(alignment: .leading, spacing: 12) {
            Text("Find these snake words:")
                .font(.headline)
                .fontWeight(.bold)
                .foregroundColor(.white)
                .padding(.horizontal)
            
            ScrollView {
                LazyVStack(spacing: 8) {
                    ForEach(data.words) { word in
                        HStack {
                            WordSnakeClueView(
                                word: word,
                                isFound: foundWords.contains(word.word),
                                isRevealed: revealedWords.contains(word.word),
                                color: Color(hex: word.color) ?? .blue
                            )
                            
                            // Show button for words that haven't been found or revealed
                            if !foundWords.contains(word.word) && !revealedWords.contains(word.word) {
                                Button(action: {
                                    revealWord(word)
                                }) {
                                    VStack(spacing: 4) {
                                        Image(systemName: "eye")
                                            .font(.system(size: 16))
                                        Text("Show")
                                            .font(.caption)
                                    }
                                    .foregroundColor(.orange)
                                    .padding(.horizontal, 12)
                                    .padding(.vertical, 8)
                                    .background(Color.orange.opacity(0.1))
                                    .cornerRadius(8)
                                }
                            }
                            // Clear button for found words (not revealed ones)
                            else if foundWords.contains(word.word) && !revealedWords.contains(word.word) {
                                Button(action: {
                                    selectedWordToClear = word.word
                                    showClearWordDialog = true
                                }) {
                                    VStack(spacing: 4) {
                                        Image(systemName: "xmark.circle")
                                            .font(.system(size: 16))
                                        Text("Clear")
                                            .font(.caption)
                                    }
                                    .foregroundColor(.red)
                                    .padding(.horizontal, 12)
                                    .padding(.vertical, 8)
                                    .background(Color.red.opacity(0.1))
                                    .cornerRadius(8)
                                }
                            }
                        }
                    }
                }
                .padding(.horizontal)
            }
        }
        .frame(maxHeight: 200)
        .background(Color.white.opacity(0.1))
        .cornerRadius(12)
        .padding(.horizontal)
    }
    
    private var clearWordConfirmationDialog: some View {
        ZStack {
            Color.black.opacity(0.5)
                .ignoresSafeArea()
                .onTapGesture {
                    showClearWordDialog = false
                    selectedWordToClear = nil
                }
            
            VStack(spacing: 20) {
                Image(systemName: "exclamationmark.triangle")
                    .font(.system(size: 40))
                    .foregroundColor(.orange)
                
                Text("Clear Found Word?")
                    .font(.title2)
                    .fontWeight(.bold)
                    .foregroundColor(.white)
                
                if let wordToClear = selectedWordToClear {
                    Text("Clear '\(wordToClear)' and free up its cells for other words?")
                        .font(.subheadline)
                        .foregroundColor(.white.opacity(0.9))
                        .multilineTextAlignment(.center)
                        .padding(.horizontal, 8)
                }
                
                HStack(spacing: 16) {
                    Button("Cancel") {
                        showClearWordDialog = false
                        selectedWordToClear = nil
                    }
                    .font(.subheadline)
                    .fontWeight(.semibold)
                    .padding(.horizontal, 20)
                    .padding(.vertical, 10)
                    .background(Color.white.opacity(0.2))
                    .foregroundColor(.white)
                    .cornerRadius(8)
                    
                    Button("Clear Word") {
                        if let wordToClear = selectedWordToClear {
                            clearFoundWord(wordToClear)
                        }
                        showClearWordDialog = false
                        selectedWordToClear = nil
                    }
                    .font(.subheadline)
                    .fontWeight(.semibold)
                    .padding(.horizontal, 20)
                    .padding(.vertical, 10)
                    .background(Color.red)
                    .foregroundColor(.white)
                    .cornerRadius(8)
                }
            }
            .padding(30)
            .background(Color(red: 0.17, green: 0.24, blue: 0.31))
            .cornerRadius(20)
            .padding(.horizontal, 40)
        }
    }

    // MARK: - Clear Found Word Function
    private func clearFoundWord(_ wordToClear: String) {
        print("🗑️ Clearing found word: \(wordToClear)")
        
        // Remove from found words
        foundWords.remove(wordToClear)
        
        // Remove the path from found paths
        foundPaths.removeAll { $0.word == wordToClear }
        
        // Remove completion event
        wordsFoundEvents.removeAll { $0.word == wordToClear }
        
        // Clear cells that were exclusively used by this word
        for row in 0..<cellGrid.count {
            for col in 0..<cellGrid[row].count {
                if cellGrid[row][col].foundWordId == wordToClear {
                    // Check if this cell is used by other found words
                    let isUsedByOtherWords = foundPaths.contains { path in
                        path.word != wordToClear && path.path.contains(WSGridPosition(row: row, col: col))
                    }
                    
                    if !isUsedByOtherWords {
                        // Cell is only used by the word we're clearing, so free it up
                        cellGrid[row][col].isFound = false
                        cellGrid[row][col].foundWordId = nil
                    } else {
                        // Cell is used by other words, update the foundWordId to the first remaining word
                        if let otherWord = foundPaths.first(where: { path in
                            path.word != wordToClear && path.path.contains(WSGridPosition(row: row, col: col))
                        }) {
                            cellGrid[row][col].foundWordId = otherWord.word
                        }
                    }
                }
            }
        }
        
        // Recalculate score and streak
        totalScore = calculateWordSnakeScore()
        
        // Reset current streak (since clearing a word breaks the flow)
        currentStreak = 0
        
        print("✅ Successfully cleared word: \(wordToClear)")
    }
    
    // MARK: - Control Buttons
    private var controlButtonsView: some View {
        HStack(spacing: 16) {
            Button("CLEAR") {
                clearCurrentSelection()
            }
            .font(.caption)
            .fontWeight(.bold)
            .foregroundColor(.white)
            .padding(.horizontal, 20)
            .padding(.vertical, 8)
            .background(selectedCells.isEmpty ? Color.white.opacity(0.1) : Color.white.opacity(0.2))
            .cornerRadius(20)
            .disabled(selectedCells.isEmpty)
            
            Button("SUBMIT") {
                submitCurrentSelection()
            }
            .font(.caption)
            .fontWeight(.bold)
            .foregroundColor(.white)
            .padding(.horizontal, 20)
            .padding(.vertical, 8)
            .background(selectedCells.count < 2 ? Color.white.opacity(0.1) : Color.green.opacity(0.6))
            .cornerRadius(20)
            .disabled(selectedCells.count < 2)
        }
        .padding()
    }
    
    // MARK: - Loading View
    private var loadingView: some View {
        VStack(spacing: 20) {
            ProgressView()
                .scaleEffect(1.5)
                .tint(.white)
            
            Text("Loading Word Snake puzzle...")
                .foregroundColor(.white)
                .font(.headline)
        }
    }
    
    // MARK: - Completion Dialog (UPDATED with revealed words info)
    private var completionDialogView: some View {
        ZStack {
            Color.black.opacity(0.5)
                .ignoresSafeArea()
            
            VStack(spacing: 20) {
                Text("🐍 Snake Complete!")
                    .font(.title)
                    .fontWeight(.bold)
                    .foregroundColor(.white)
                
                Text("Final Score: \(totalScore)")
                    .font(.title2)
                    .fontWeight(.semibold)
                    .foregroundColor(.white)
                
                VStack(spacing: 8) {
                    Text("Performance:")
                        .font(.headline)
                        .foregroundColor(.white)
                    
                    HStack(spacing: 20) {
                        VStack {
                            Text("\(foundWords.count + revealedWords.count)/\(wordSnakeData?.words.count ?? 0)")
                                .font(.caption)
                                .foregroundColor(.white)
                            Text("Completed")
                                .font(.caption2)
                                .foregroundColor(.white.opacity(0.8))
                        }
                        
                        if revealedWords.count > 0 {
                            VStack {
                                Text("\(foundWords.count)")
                                    .font(.caption)
                                    .foregroundColor(.green)
                                Text("Found")
                                    .font(.caption2)
                                    .foregroundColor(.white.opacity(0.8))
                            }
                            
                            VStack {
                                Text("\(revealedWords.count)")
                                    .font(.caption)
                                    .foregroundColor(.orange)
                                Text("Shown")
                                    .font(.caption2)
                                    .foregroundColor(.white.opacity(0.8))
                            }
                        }
                        
                        VStack {
                            Text("\(bestStreak)")
                                .font(.caption)
                                .foregroundColor(.white)
                            Text("Best Streak")
                                .font(.caption2)
                                .foregroundColor(.white.opacity(0.8))
                        }
                        
                        VStack {
                            let avgTime = wordsFoundEvents.isEmpty ? 0 : wordsFoundEvents.reduce(0) { $0 + $1.timeFromStart } / TimeInterval(wordsFoundEvents.count)
                            Text("\(String(format: "%.1f", avgTime))s")
                                .font(.caption)
                                .foregroundColor(.white)
                            Text("Avg/Word")
                                .font(.caption2)
                                .foregroundColor(.white.opacity(0.8))
                        }
                    }
                }
                .padding()
                .background(Color.white.opacity(0.1))
                .cornerRadius(12)
                
                let timeUsed = (wordSnakeData?.timeLimit ?? 240) - timeRemaining
                Text("Time: \(timeUsed/60):\(String(format: "%02d", timeUsed%60))")
                    .font(.subheadline)
                    .foregroundColor(.white.opacity(0.9))
                
                HStack(spacing: 16) {
                    Button("Reset") {
                        resetGame()
                        showCompletionDialog = false
                    }
                    .padding(.horizontal, 20)
                    .padding(.vertical, 8)
                    .background(Color.white.opacity(0.2))
                    .foregroundColor(.white)
                    .cornerRadius(8)
                    
                    Button("Continue") {
                        showCompletionDialog = false
                        onNextPuzzle()
                    }
                    .padding(.horizontal, 20)
                    .padding(.vertical, 8)
                    .background(Color.white)
                    .foregroundColor(.black)
                    .cornerRadius(8)
                }
            }
            .padding(30)
            .background(Color(red: 0.17, green: 0.24, blue: 0.31))
            .cornerRadius(20)
            .padding(.horizontal, 40)
        }
    }
    
    // MARK: - Hints Dialog (UPDATED with Show buttons)
    private func hintsDialogView(data: WordSnakePuzzleData) -> some View {
        ZStack {
            Color.black.opacity(0.5)
                .ignoresSafeArea()
                .onTapGesture {
                    showHintsDialog = false
                }
            
            VStack(spacing: 16) {
                Text("Word Snake Hints")
                    .font(.title2)
                    .fontWeight(.bold)
                    .foregroundColor(.white)
                
                ScrollView {
                    LazyVStack(spacing: 8) {
                        ForEach(data.words) { word in
                            HStack {
                                WordSnakeClueView(
                                    word: word,
                                    isFound: foundWords.contains(word.word),
                                    isRevealed: revealedWords.contains(word.word),
                                    color: Color(hex: word.color) ?? .blue
                                )
                                
                                // Show button for words that haven't been found or revealed
                                if !foundWords.contains(word.word) && !revealedWords.contains(word.word) {
                                    Button(action: {
                                        revealWord(word)
                                    }) {
                                        VStack(spacing: 4) {
                                            Image(systemName: "eye")
                                                .font(.system(size: 16))
                                            Text("Show")
                                                .font(.caption)
                                        }
                                        .foregroundColor(.orange)
                                        .padding(.horizontal, 12)
                                        .padding(.vertical, 8)
                                        .background(Color.orange.opacity(0.1))
                                        .cornerRadius(8)
                                    }
                                }
                            }
                        }
                    }
                }
                .frame(maxHeight: 300)
                
                Button("Close") {
                    showHintsDialog = false
                }
                .padding(.horizontal, 30)
                .padding(.vertical, 10)
                .background(Color.white)
                .foregroundColor(.black)
                .cornerRadius(8)
            }
            .padding(20)
            .background(Color(red: 0.17, green: 0.24, blue: 0.31))
            .cornerRadius(16)
            .padding(.horizontal, 30)
        }
    }
    
    // MARK: - NEW: Reveal Word Functionality
    private func revealWord(_ word: WordSnakeWord) {
        guard !foundWords.contains(word.word) && !revealedWords.contains(word.word) else {
            print("🚫 Cannot reveal word: \(word.word) (already found or revealed)")
            return
        }
        
        print("👁️ Revealing word: \(word.word)")
        
        // Add to revealed words
        revealedWords.insert(word.word)
        
        // Create revealed path
        let pathColor = Color(hex: word.color) ?? .orange
        let revealedPath = WordSnakePath(
            word: word.word,
            path: word.path,
            color: pathColor,
            isFound: false,
            isRevealed: true
        )
        foundPaths.append(revealedPath)
        
        // Mark cells as revealed
        withAnimation(.easeInOut(duration: 0.5)) {
            for position in word.path {
                if position.row < cellGrid.count && position.col < cellGrid[position.row].count {
                    cellGrid[position.row][position.col].isRevealed = true
                    cellGrid[position.row][position.col].foundWordId = word.word
                }
            }
        }
        
        // Provide haptic feedback
        let impactFeedback = UIImpactFeedbackGenerator(style: .medium)
        impactFeedback.impactOccurred()
        
        // Check for completion
        checkPuzzleCompletion()
    }
    
    // MARK: - Setup and Game Logic
    private func setupPuzzle() {
        print("🐍 Setting up Word Snake puzzle...")
        print("🐍 Raw puzzle question: \(puzzle.question)")
        
        guard let data = WordSnakePuzzleData(from: puzzle.question) else {
            print("❌ Failed to parse Word Snake data")
            return
        }
        
        print("✅ Successfully parsed Word Snake data:")
        print("   - Grid size: \(data.gridSize)x\(data.gridSize)")
        print("   - Words count: \(data.words.count)")
        print("   - Time limit: \(data.timeLimit)")
        print("   - Difficulty: \(data.difficulty)")
        
        wordSnakeData = data
        timeRemaining = data.timeLimit
        displayTimer = formatTime(timeRemaining)
        
        // Initialize cell grid
        cellGrid = []
        for row in 0..<data.gridSize {
            var cellRow: [WordSnakeCell] = []
            for col in 0..<data.gridSize {
                let letter = data.grid[row][col]
                cellRow.append(WordSnakeCell(row: row, col: col, letter: letter))
            }
            cellGrid.append(cellRow)
        }
        
        gameStartTime = Date()
        print("✅ Word Snake puzzle setup complete")
    }
    
    private func startTimer() {
        timer?.invalidate()
        timer = Timer.scheduledTimer(withTimeInterval: 1.0, repeats: true) { _ in
            if timeRemaining > 0 && !isCompleted {
                timeRemaining -= 1
                displayTimer = formatTime(timeRemaining)
            } else if timeRemaining == 0 && !isCompleted {
                completeGame()
            }
        }
    }
    
    private func formatTime(_ seconds: Int) -> String {
        let minutes = seconds / 60
        let seconds = seconds % 60
        return "\(minutes):\(String(format: "%02d", seconds))"
    }
    
    // MARK: - NEW SELECTION LOGIC
    // MARK: - UPDATED SELECTION LOGIC (Fixed to allow overlapping cells)
    private func handleCellTap(_ position: WSGridPosition) {
        print("🐍 Cell tapped at (\(position.row), \(position.col))")
        
        // Validate position is within bounds
        guard position.row < cellGrid.count && position.col < cellGrid[position.row].count else {
            print("❌ Cell position out of bounds")
            return
        }
        
        let cell = cellGrid[position.row][position.col]
        
        // REMOVED: Don't block found cells - allow them to be reused
        // The old logic that prevented this:
        // if cell.isFound {
        //     print("❌ Cell is already found, ignoring tap")
        //     return
        // }
        
        // Allow revealed cells to be selected too (they can be part of other words)
        
        if let existingIndex = selectedCells.firstIndex(of: position) {
            // Cell is already selected - remove it and all cells after it
            selectedCells = Array(selectedCells[0..<existingIndex])
            print("🐍 Removed cell and subsequent selections. New selection count: \(selectedCells.count)")
        } else {
            // Cell is not selected - add it to selection
            if selectedCells.isEmpty {
                // First cell selection
                selectedCells.append(position)
                print("🐍 First cell selected: (\(position.row), \(position.col))")
            } else {
                // Fill in any gaps between last selected cell and this one
                let lastCell = selectedCells.last!
                let pathBetween = findPathBetween(from: lastCell, to: position)
                
                // Add the path (excluding the first cell since it's already selected)
                let newCells = Array(pathBetween.dropFirst())
                selectedCells.append(contentsOf: newCells)
                print("🐍 Added path from (\(lastCell.row), \(lastCell.col)) to (\(position.row), \(position.col)). Added \(newCells.count) cells")
            }
        }
        
        updateSelectionStates()
        updateCurrentSelectionWord()
        
        print("🐍 Current selection: \(currentSelectionWord) (path length: \(selectedCells.count))")
    }
    
    // Find the shortest path between two cells (straight lines only)
    private func findPathBetween(from start: WSGridPosition, to end: WSGridPosition) -> [WSGridPosition] {
        var path: [WSGridPosition] = [start]
        var current = start
        
        while current != end {
            var nextRow = current.row
            var nextCol = current.col
            
            // Move towards the target
            if current.row < end.row {
                nextRow += 1
            } else if current.row > end.row {
                nextRow -= 1
            }
            
            if current.col < end.col {
                nextCol += 1
            } else if current.col > end.col {
                nextCol -= 1
            }
            
            current = WSGridPosition(row: nextRow, col: nextCol)
            path.append(current)
            
            // Safety check to prevent infinite loops
            if path.count > 50 { break }
        }
        
        return path
    }
    
    private func submitCurrentSelection() {
        guard selectedCells.count >= 2 else { return }
        
        let selectedWord = selectedCells.map { pos in
            cellGrid[pos.row][pos.col].letter
        }.joined()
        
        print("🐍 Submitting selection: '\(selectedWord)'")
        
        if let matchedWord = checkWordMatch() {
            print("🎉 Word found: '\(matchedWord.word)'")
            handleWordFound(matchedWord)
        } else {
            // Wrong selection - show feedback and clear
            print("❌ No matching word found for selection: '\(selectedWord)'")
            
            clearCurrentSelection()
            currentStreak = 0
        }
    }
    
    private func updateSelectionStates() {
        // Clear all selection states
        for row in 0..<cellGrid.count {
            for col in 0..<cellGrid[row].count {
                cellGrid[row][col].isSelected = false
                cellGrid[row][col].selectionOrder = -1
            }
        }
        
        // Set selection states for selected cells
        for (index, position) in selectedCells.enumerated() {
            cellGrid[position.row][position.col].isSelected = true
            cellGrid[position.row][position.col].selectionOrder = index
        }
    }
    
    private func updateCurrentSelectionWord() {
        currentSelectionWord = selectedCells.map { pos in
            cellGrid[pos.row][pos.col].letter
        }.joined()
    }
    
    private func checkWordMatch() -> WordSnakeWord? {
        guard let data = wordSnakeData, selectedCells.count >= 2 else { return nil }
        
        // Get the word spelled by the current selection
        let selectedWord = selectedCells.map { pos in
            cellGrid[pos.row][pos.col].letter
        }.joined().uppercased()
        
        print("🔍 Checking word match for: '\(selectedWord)'")
        
        for word in data.words {
            // Skip words that have already been found or revealed
            if foundWords.contains(word.word) || revealedWords.contains(word.word) {
                print("   - Skipping '\(word.word)' (already found/revealed)")
                continue
            }
            
            let targetWord = word.word.uppercased()
            
            // Check if the selected word matches (forward or backward)
            if selectedWord == targetWord || selectedWord == String(targetWord.reversed()) {
                print("✅ Found match: '\(selectedWord)' matches '\(word.word)'")
                return word
            } else {
                print("   - '\(selectedWord)' != '\(targetWord)' (forward or backward)")
            }
        }
        
        print("❌ No match found for '\(selectedWord)'")
        return nil
    }
    
    private func handleWordFound(_ word: WordSnakeWord) {
        guard !foundWords.contains(word.word) && !revealedWords.contains(word.word) else {
            print("⚠️ Word '\(word.word)' already found/revealed")
            return
        }
        
        let currentTime = Date()
        let timeFromStart = currentTime.timeIntervalSince(gameStartTime)
        
        print("🎉 Processing found word: '\(word.word)'")
        
        foundWords.insert(word.word)
        
        let findEvent = WordSnakeCompletionEvent(
            word: word.word,
            timestamp: currentTime,
            timeFromStart: timeFromStart,
            method: "manual_submit"
        )
        wordsFoundEvents.append(findEvent)
        
        currentStreak += 1
        if currentStreak > bestStreak {
            bestStreak = currentStreak
        }
        
        // Add found path using the user's selected path
        let pathColor = Color(hex: word.color) ?? .blue
        let newPath = WordSnakePath(
            word: word.word,
            path: selectedCells,
            color: pathColor,
            isFound: true,
            isRevealed: false
        )
        foundPaths.append(newPath)
        
        // Mark the selected cells as associated with this word
        // BUT don't mark them as globally "found" to allow reuse in other words
        for position in selectedCells {
            // Instead of marking as found globally, we'll track this word association
            if cellGrid[position.row][position.col].foundWordId == nil {
                // This is the first word to use this cell
                cellGrid[position.row][position.col].isFound = true
                cellGrid[position.row][position.col].foundWordId = word.word
            } else {
                // This cell is already part of another word - that's okay!
                // We could maintain a list of all word IDs that use this cell
                // For now, we'll just let the visual representation handle the overlap
                print("📍 Cell (\(position.row), \(position.col)) is shared between words")
            }
        }
        
        // Clear selection
        clearCurrentSelection()
        
        // Calculate score
        totalScore = calculateWordSnakeScore()
        
        // Provide haptic feedback
        let impactFeedback = UIImpactFeedbackGenerator(style: .medium)
        impactFeedback.impactOccurred()
        
        // Check completion
        checkPuzzleCompletion()
        
        print("✅ Successfully processed word: '\(word.word)'. Score: \(totalScore)")
    }
    
    // MARK: - UPDATED: Check Puzzle Completion
    private func checkPuzzleCompletion() {
        guard let data = wordSnakeData else { return }
        
        let totalDiscovered = foundWords.count + revealedWords.count
        
        print("🔍 Completion check:")
        print("   - Total words: \(data.words.count)")
        print("   - Found words: \(foundWords.count)")
        print("   - Revealed words: \(revealedWords.count)")
        print("   - Total discovered: \(totalDiscovered)")
        
        if totalDiscovered >= data.words.count {
            completeGame()
        }
    }
    
    private func calculateWordSnakeScore() -> Int {
        guard let data = wordSnakeData else { return 0 }
        
        let basePointsPerWord: Int = {
            switch data.difficulty.lowercased() {
            case "easy": return 25
            case "medium": return 35
            case "hard": return 45
            case "expert": return 55
            default: return 35
            }
        }()
        
        // Only found words contribute to score, not revealed ones
        let baseScore = foundWords.count * basePointsPerWord
        
        let complexityMultiplier: Float = {
            switch data.gridSize {
            case 12...: return 2.2
            case 10..<12: return 1.8
            case 8..<10: return 1.5
            default: return 1.2
            }
        }()
        
        // Completion bonus only if all words were found (not revealed)
        let completionBonus = (foundWords.count == data.words.count) ? Int(Float(baseScore) * 0.6) : 0
        
        let avgTimePerWord = wordsFoundEvents.isEmpty ? Float.greatestFiniteMagnitude :
                           Float(wordsFoundEvents.reduce(0) { $0 + $1.timeFromStart }) / Float(wordsFoundEvents.count)
        
        let speedBonus: Int = {
            switch avgTimePerWord {
            case ...15: return Int(Float(baseScore) * 0.4)
            case 15..<30: return Int(Float(baseScore) * 0.2)
            case 30..<45: return Int(Float(baseScore) * 0.1)
            default: return 0
            }
        }()
        
        let streakBonus: Int = {
            switch bestStreak {
            case 4...: return Int(Float(baseScore) * 0.3)
            case 3: return Int(Float(baseScore) * 0.2)
            case 2: return Int(Float(baseScore) * 0.1)
            default: return 0
            }
        }()
        
        // Penalty for using hints (showing words)
        let hintPenalty = (hintUsed || revealedWords.count > 0) ? Int(Float(baseScore) * 0.15) : 0
        
        let finalScore = max(
            Int(Float(baseScore) * complexityMultiplier) + completionBonus + speedBonus + streakBonus - hintPenalty,
            baseScore / 3
        )
        
        return finalScore
    }
    
    private func clearCurrentSelection() {
        selectedCells.removeAll()
        currentSelectionWord = ""
        updateSelectionStates()
    }
    
    // MARK: - UPDATED: Reset Game
    private func resetGame() {
        foundWords.removeAll()
        revealedWords.removeAll() // Clear revealed words
        foundPaths.removeAll()
        selectedCells.removeAll()
        currentSelectionWord = ""
        totalScore = 0
        currentStreak = 0
        bestStreak = 0
        hintUsed = false
        wordsFoundEvents.removeAll()
        gameStartTime = Date()
        isCompleted = false
        
        if let data = wordSnakeData {
            timeRemaining = data.timeLimit
            displayTimer = formatTime(timeRemaining)
        }
        
        // Reset cell grid
        for row in 0..<cellGrid.count {
            for col in 0..<cellGrid[row].count {
                cellGrid[row][col].isFound = false
                cellGrid[row][col].isRevealed = false // Reset revealed state
                cellGrid[row][col].isSelected = false
                cellGrid[row][col].selectionOrder = -1
                cellGrid[row][col].foundWordId = nil
            }
        }
        
        startTimer()
    }
    
    private func completeGame() {
        isCompleted = true
        timer?.invalidate()
        
        let isSuccess = foundWords.count + revealedWords.count == (wordSnakeData?.words.count ?? 0)
        onAnswerSubmitted(isSuccess)
        
        showCompletionDialog = true
    }
    
    // MARK: - Drawing Functions
    private func drawSnakePath(context: GraphicsContext, path: WordSnakePath, cellSize: CGFloat, gridSize: Int, canvasSize: CGSize) {
        guard path.path.count >= 2 else { return }
        
        let strokeWidth: CGFloat = path.isRevealed ? 6 : 8 // Slightly thinner for revealed paths
        let positions = path.path.map { pos in
            CGPoint(
                x: CGFloat(pos.col) * (cellSize + gridSpacing) + cellSize / 2,
                y: CGFloat(pos.row) * (cellSize + gridSpacing) + cellSize / 2
            )
        }
        
        // Use different stroke style for revealed paths
        let strokeStyle: StrokeStyle = path.isRevealed ?
            StrokeStyle(lineWidth: strokeWidth, dash: [4, 2]) :
            StrokeStyle(lineWidth: strokeWidth)
        
        // Draw path segments
        for i in 0..<positions.count - 1 {
            var pathShape = Path()
            pathShape.move(to: positions[i])
            pathShape.addLine(to: positions[i + 1])
            
            context.stroke(
                pathShape,
                with: .color(path.isRevealed ? path.color.opacity(0.7) : path.color),
                style: strokeStyle
            )
        }
        
        // Draw endpoint circles
        let endpointColor = path.isRevealed ? path.color.opacity(0.7) : path.color
        
        context.fill(
            Path(ellipseIn: CGRect(
                x: positions.first!.x - strokeWidth / 2,
                y: positions.first!.y - strokeWidth / 2,
                width: strokeWidth,
                height: strokeWidth
            )),
            with: .color(endpointColor)
        )
        
        context.fill(
            Path(ellipseIn: CGRect(
                x: positions.last!.x - strokeWidth / 2,
                y: positions.last!.y - strokeWidth / 2,
                width: strokeWidth,
                height: strokeWidth
            )),
            with: .color(endpointColor)
        )
    }
    
    private func drawSelectionPath(context: GraphicsContext, path: [WSGridPosition], cellSize: CGFloat, gridSize: Int, canvasSize: CGSize) {
        guard path.count >= 2 else { return }
        
        let strokeWidth: CGFloat = 6
        let color = Color.blue.opacity(0.8)
        
        let positions = path.map { pos in
            CGPoint(
                x: CGFloat(pos.col) * (cellSize + gridSpacing) + cellSize / 2,
                y: CGFloat(pos.row) * (cellSize + gridSpacing) + cellSize / 2
            )
        }
        
        // Draw path segments
        for i in 0..<positions.count - 1 {
            var pathShape = Path()
            pathShape.move(to: positions[i])
            pathShape.addLine(to: positions[i + 1])
            
            context.stroke(
                pathShape,
                with: .color(color),
                lineWidth: strokeWidth
            )
        }
        
        // Draw selection indicators
        for position in positions {
            context.fill(
                Path(ellipseIn: CGRect(
                    x: position.x - strokeWidth / 2,
                    y: position.y - strokeWidth / 2,
                    width: strokeWidth,
                    height: strokeWidth
                )),
                with: .color(color)
            )
        }
    }
}

// MARK: - UPDATED: Word Snake Cell View (with revealed state)
struct WordSnakeCellView: View {
    let cell: WordSnakeCell
    let size: CGFloat
    let isSelected: Bool
    let selectionOrder: Int
    let onTap: () -> Void
    
    var body: some View {
        Button(action: onTap) {
            ZStack {
                Rectangle()
                    .fill(backgroundColor)
                    .frame(width: size, height: size)
                    .overlay(
                        Rectangle()
                            .stroke(borderColor, lineWidth: borderWidth)
                    )
                    .cornerRadius(6)
                
                // Selection order indicator
                if isSelected && selectionOrder >= 0 {
                    VStack {
                        HStack {
                            Spacer()
                            
                            Text("\(selectionOrder + 1)")
                                .font(.caption2)
                                .fontWeight(.bold)
                                .foregroundColor(.white)
                                .frame(width: size * 0.25, height: size * 0.25)
                                .background(Color.blue)
                                .clipShape(Circle())
                                .offset(x: size * 0.15, y: -size * 0.15)
                        }
                        Spacer()
                    }
                }
                
                // Letter
                Text(cell.letter)
                    .font(.system(size: size * 0.4, weight: .bold))
                    .foregroundColor(textColor)
            }
        }
        .buttonStyle(PlainButtonStyle())
        .frame(width: size, height: size)
    }
    
    private var backgroundColor: Color {
        if isSelected {
            return Color.blue.opacity(0.8)
        } else if cell.isFound {
            return Color.green.opacity(0.3)
        } else if cell.isRevealed {
            return Color.orange.opacity(0.3) // Orange background for revealed cells
        } else {
            return Color.white
        }
    }
    
    private var borderColor: Color {
        if isSelected {
            return Color.blue
        } else if cell.isFound {
            return Color.green
        } else if cell.isRevealed {
            return Color.orange
        } else {
            return Color.gray.opacity(0.3)
        }
    }
    
    private var borderWidth: CGFloat {
        (isSelected || cell.isRevealed) ? 3 : 2
    }
    
    private var textColor: Color {
        (cell.isFound || cell.isRevealed || isSelected) ? .white : .black
    }
}

// MARK: - UPDATED: Word Snake Clue View (with revealed state)
struct WordSnakeClueView: View {
    let word: WordSnakeWord
    let isFound: Bool
    let isRevealed: Bool
    let color: Color
    
    var body: some View {
        HStack(spacing: 12) {
            Circle()
                .fill(circleColor)
                .frame(width: 16, height: 16)
                .overlay(
                    statusIcon
                        .foregroundColor(.white)
                        .font(.system(size: 10, weight: .bold))
                )
            
            VStack(alignment: .leading, spacing: 2) {
                Text(word.word)
                    .font(.subheadline)
                    .fontWeight(.semibold)
                    .foregroundColor(textColor)
                    .strikethrough(isFound || isRevealed)
                
                Text(word.clue)
                    .font(.caption)
                    .foregroundColor(hintColor)
                    .opacity(textOpacity)
            }
            
            Spacer()
            
            if isFound {
                Image(systemName: "checkmark")
                    .font(.headline)
                    .foregroundColor(.green)
                    .fontWeight(.bold)
            } else if isRevealed {
                Image(systemName: "eye")
                    .font(.headline)
                    .foregroundColor(.orange)
                    .fontWeight(.bold)
            }
        }
        .padding(.vertical, 4)
        .background(backgroundColor)
        .cornerRadius(8)
        .padding(.horizontal, 4)
    }
    
    private var circleColor: Color {
        if isFound {
            return color
        } else if isRevealed {
            return .orange
        } else {
            return Color.gray.opacity(0.3)
        }
    }
    
    private var statusIcon: some View {
        Group {
            if isFound {
                Image(systemName: "checkmark")
            } else if isRevealed {
                Image(systemName: "eye")
            } else {
                Circle().fill(Color.clear)
            }
        }
    }
    
    private var textColor: Color {
        if isFound {
            return .gray
        } else if isRevealed {
            return .orange
        } else {
            return .primary
        }
    }
    
    private var hintColor: Color {
        if isFound || isRevealed {
            return Color.gray.opacity(0.7)
        } else {
            return .gray
        }
    }
    
    private var textOpacity: Double {
        (isFound || isRevealed) ? 0.6 : 1.0
    }
    
    private var backgroundColor: Color {
        if isFound {
            return color.opacity(0.1)
        } else if isRevealed {
            return Color.orange.opacity(0.1)
        } else {
            return Color.clear
        }
    }
}

// MARK: - Color Extension
extension Color {
    init?(hex: String) {
        let hex = hex.trimmingCharacters(in: CharacterSet.alphanumerics.inverted)
        var int: UInt64 = 0
        Scanner(string: hex).scanHexInt64(&int)
        let a, r, g, b: UInt64
        switch hex.count {
        case 3: // RGB (12-bit)
            (a, r, g, b) = (255, (int >> 8) * 17, (int >> 4 & 0xF) * 17, (int & 0xF) * 17)
        case 6: // RGB (24-bit)
            (a, r, g, b) = (255, int >> 16, int >> 8 & 0xFF, int & 0xFF)
        case 8: // ARGB (32-bit)
            (a, r, g, b) = (int >> 24, int >> 16 & 0xFF, int >> 8 & 0xFF, int & 0xFF)
        default:
            return nil
        }
        
        self.init(
            .sRGB,
            red: Double(r) / 255,
            green: Double(g) / 255,
            blue:  Double(b) / 255,
            opacity: Double(a) / 255
        )
    }
}
