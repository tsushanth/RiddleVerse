//
//  WordSearchPuzzleView.swift
//  PuzzleForge
//
//  iOS implementation of Word Search puzzle - UPDATED with Give Up Feature and Better Layout
//

import SwiftUI
import Foundation

// MARK: - Supporting Types
struct GridPosition: Equatable {
    let row: Int
    let col: Int
}

extension Puzzle {
    /// Parsed word search puzzle data
    var wordSearchPuzzleData: WordSearchPuzzleData? {
        guard puzzleType?.lowercased().contains("wordsearch") == true ||
              puzzleType?.lowercased().contains("word_search") == true else {
            return nil
        }
        
        do {
            // Try to parse the question as word search JSON
            guard let data = question.data(using: .utf8) else { return nil }
            
            // First try direct parsing
            if let wordSearchJson = try JSONSerialization.jsonObject(with: data) as? [String: Any],
               let matrix = parseMatrix(from: wordSearchJson),
               let words = parseWords(from: wordSearchJson),
               let width = wordSearchJson["width"] as? Int,
               let height = wordSearchJson["height"] as? Int {
                
                let instructions = wordSearchJson["instructions"] as? String ?? "Find all hidden words"
                
                return WordSearchPuzzleData(
                    matrix: matrix,
                    words: words,
                    width: width,
                    height: height,
                    instructions: instructions
                )
            }
            
            // Try nested parsing as fallback
            if let outerJson = try JSONSerialization.jsonObject(with: data) as? [String: Any],
               let innerQuestionString = outerJson["question"] as? String,
               let innerData = innerQuestionString.data(using: .utf8),
               let innerJson = try JSONSerialization.jsonObject(with: innerData) as? [String: Any],
               let matrix = parseMatrix(from: innerJson),
               let words = parseWords(from: innerJson),
               let width = innerJson["width"] as? Int,
               let height = innerJson["height"] as? Int {
                
                let instructions = innerJson["instructions"] as? String ?? "Find all hidden words"
                
                return WordSearchPuzzleData(
                    matrix: matrix,
                    words: words,
                    width: width,
                    height: height,
                    instructions: instructions
                )
            }
            
        } catch {
            print("⚠️ Failed to parse word search data: \(error)")
        }
        
        return nil
    }
    
    private func parseMatrix(from json: [String: Any]) -> [[String]]? {
        guard let matrixArray = json["matrix"] as? [[Any]] else { return nil }
        
        return matrixArray.map { row in
            row.map { cell in
                if let stringCell = cell as? String {
                    return stringCell
                } else if let intCell = cell as? Int {
                    return String(intCell)
                } else {
                    return String(describing: cell)
                }
            }
        }
    }
    
    private func parseWords(from json: [String: Any]) -> [WordSearchWord]? {
        guard let wordsArray = json["words"] as? [[String: Any]] else { return nil }
        
        return wordsArray.compactMap { wordDict -> WordSearchWord? in
            guard let word = wordDict["word"] as? String,
                  let hint = wordDict["hint"] as? String,
                  let direction = wordDict["direction"] as? String,
                  let length = wordDict["length"] as? Int else {
                return nil
            }
            
            return WordSearchWord(
                word: word,
                hint: hint,
                direction: direction,
                length: length
            )
        }
    }
}

// MARK: - Data Models
struct WordSearchCell: Identifiable {
    let id = UUID()
    let x: Int
    let y: Int
    let letter: String
    var isSelected: Bool = false
    var isFound: Bool = false
    var foundWordId: String? = nil
    var isRevealed: Bool = false
    // Remove: var isCurrentPath: Bool = false
}

struct WordSearchWord: Identifiable {
    let id = UUID()
    let word: String
    let hint: String
    let direction: String
    let length: Int
    var isFound: Bool = false
    var isRevealed: Bool = false
}

struct WordSearchPath {
    let startX: Int
    let startY: Int
    let endX: Int
    let endY: Int
    let word: String
    let color: Color
}

struct WordSearchPuzzleData {
    let matrix: [[String]]
    let words: [WordSearchWord]
    let width: Int
    let height: Int
    let instructions: String
}

// MARK: - Grid Component Views
struct WsGridCellView: View {
    let cell: WordSearchCell
    let row: Int
    let col: Int
    let cellSize: CGFloat
    let onCellTap: (Int, Int) -> Void
    let getCellTextColor: (Int, Int) -> Color
    let getCellBackgroundColor: (Int, Int) -> Color
    let getCellOverlay: (Int, Int, CGFloat) -> AnyView
    
    var body: some View {
        Button(action: {
            onCellTap(row, col)
        }) {
            Text(cell.letter)
                .font(.system(size: cellSize * 0.5, weight: .bold))
                .foregroundColor(getCellTextColor(row, col))
                .frame(width: cellSize, height: cellSize)
                .background(getCellBackgroundColor(row, col))
                .overlay(getCellOverlay(row, col, cellSize))
                .cornerRadius(4)
        }
    }
}

struct GridRowView: View {
    let rowIndex: Int
    let rowData: [WordSearchCell]
    let dataWidth: Int
    let cellSize: CGFloat
    let onCellTap: (Int, Int) -> Void
    let getCellTextColor: (Int, Int) -> Color
    let getCellBackgroundColor: (Int, Int) -> Color
    let getCellOverlay: (Int, Int, CGFloat) -> AnyView
    
    var body: some View {
        HStack(spacing: 1) {
            ForEach(rowData.indices, id: \.self) { col in
                WsGridCellView(
                    cell: rowData[col],
                    row: rowIndex,
                    col: col,
                    cellSize: cellSize,
                    onCellTap: onCellTap,
                    getCellTextColor: getCellTextColor,
                    getCellBackgroundColor: getCellBackgroundColor,
                    getCellOverlay: getCellOverlay
                )
            }
        }
    }
}

struct WordSearchGridContent: View {
    let puzzleData: WordSearchPuzzleData
    let grid: [[WordSearchCell]]
    let geometry: GeometryProxy
    let onCellTap: (Int, Int) -> Void
    let getCellTextColor: (Int, Int) -> Color
    let getCellBackgroundColor: (Int, Int) -> Color
    let getCellOverlay: (Int, Int, CGFloat) -> AnyView
    
    var body: some View {
        let availableWidth = geometry.size.width
        let availableHeight = geometry.size.height
        
        let maxCellSizeForWidth = (availableWidth - CGFloat(puzzleData.width - 1)) / CGFloat(puzzleData.width)
        let maxCellSizeForHeight = (availableHeight - CGFloat(puzzleData.height - 1)) / CGFloat(puzzleData.height)
        let optimalCellSize = min(maxCellSizeForWidth, maxCellSizeForHeight, 60)
        
        VStack(spacing: 1) {
            ForEach(grid.indices, id: \.self) { row in
                GridRowView(
                    rowIndex: row,
                    rowData: grid[row],
                    dataWidth: puzzleData.width,
                    cellSize: optimalCellSize,
                    onCellTap: onCellTap,
                    getCellTextColor: getCellTextColor,
                    getCellBackgroundColor: getCellBackgroundColor,
                    getCellOverlay: getCellOverlay
                )
            }
        }
        .position(x: geometry.size.width / 2, y: geometry.size.height / 2)
    }
}

// MARK: - Main View
struct WordSearchPuzzleView: View {
    let puzzle: Puzzle
    let questionIndex: Int
    let totalQuestions: Int
    let onAnswerSubmitted: (Bool) -> Void
    let onNextPuzzle: () -> Void
    let onExit: () -> Void
    
    @State private var foundWords: Set<String> = []
    @State private var revealedWords: Set<String> = []
    @State private var foundWordPositions: [String: [(row: Int, col: Int)]] = [:]
    @State private var revealedWordPositions: [String: [(row: Int, col: Int)]] = [:]
    @State private var isCompleted = false
    @State private var showCompletionDialog = false
    @State private var showHintsDialog = false
    @State private var completionTime = ""
    @State private var foundPaths: [WordSearchPath] = []
    @State private var puzzleData: WordSearchPuzzleData?
    @State private var grid: [[WordSearchCell]] = []
    @State private var timeRemaining = 300
    @State private var timer: Timer?
    @State private var isViewActive = true
    @State private var showTipDialog = false
    
    enum SelectionStep {
        case waitingForStart
        case waitingForEnd
    }
    
    @State private var selectedStartPosition: GridPosition?
    @State private var selectedEndPosition: GridPosition?
    @State private var selectionStep: SelectionStep = .waitingForStart
    
    // Overtime support
    @State private var isOvertime = false
    @State private var completedWithinTime = false
    @State private var originalTimeLimit = 300
    
    
    private let cellSize: CGFloat = 28
    
    private let wordColors = [
        Color(red: 0.3, green: 0.69, blue: 0.31), // Green
        Color(red: 0.13, green: 0.59, blue: 0.95), // Blue
        Color(red: 1.0, green: 0.6, blue: 0.0),    // Orange
        Color(red: 0.61, green: 0.15, blue: 0.69), // Purple
        Color(red: 0.91, green: 0.12, blue: 0.39), // Pink
        Color(red: 0.0, green: 0.74, blue: 0.83),  // Cyan
        Color(red: 1.0, green: 0.92, blue: 0.23),  // Yellow
        Color(red: 0.47, green: 0.33, blue: 0.28), // Brown
        Color(red: 0.38, green: 0.49, blue: 0.55), // Blue Grey
        Color(red: 0.25, green: 0.32, blue: 0.71)  // Indigo
    ]
    
    var body: some View {
        ZStack {
            Color(red: 0.0, green: 0.74, blue: 0.83)
                .ignoresSafeArea()
            
            VStack(spacing: 0) {
                Spacer(minLength: 32)
                
                topBarSection
                instructionSection
                progressSection
                gameGridSection
                    .layoutPriority(1.0)
                
                enhancedHintsSection
                    .frame(minHeight: 140, maxHeight: 180)
                    .layoutPriority(0.5)
                
                resetButtonSection
                    .frame(height: 60)
            }
        }
        .navigationBarHidden(true)
        .onAppear {
            if isViewActive {
                setupPuzzle()
            }
        }
        .onDisappear {
            print("🚪 WordSearchPuzzleView disappeared")
            cleanup()
        }
        .sheet(isPresented: $showTipDialog) {
            WordSearchTipDialogView(
                onDismiss: { showTipDialog = false }
            )
        }
        .sheet(isPresented: $showCompletionDialog, onDismiss: {
            print("Dialog dismissed")
        }) {
            WordSearchCompletionDialogView(
                time: completionTime,
                wordsFound: foundWords.count,
                wordsRevealed: revealedWords.count,
                totalWords: puzzleData?.words.count ?? 0,
                completedWithinTime: completedWithinTime,
                isOvertime: isOvertime,
                onShare: {
                    print("Share word search completion")
                },
                onReset: resetGame,
                onNext: {
                    showCompletionDialog = false
                    cleanup()
                    onNextPuzzle()
                },
                onDismiss: { showCompletionDialog = false }
            )
        }
        .sheet(isPresented: $showHintsDialog) {
            EnhancedWordSearchHintsDialogView(
                words: puzzleData?.words ?? [],
                foundWords: foundWords,
                revealedWords: revealedWords,
                onRevealWord: revealWord,
                onDismiss: { showHintsDialog = false }
            )
        }
    }
    
    // MARK: - Game Grid Section (Simplified)
    private var gameGridSection: some View {
        VStack {
            if let data = puzzleData {
                GeometryReader { geometry in
                    WordSearchGridContent(
                        puzzleData: data,
                        grid: grid,
                        geometry: geometry,
                        onCellTap: handleCellTap,
                        getCellTextColor: getCellTextColor,
                        getCellBackgroundColor: getCellBackgroundColor,
                        getCellOverlay: { row, col, cellSize in
                            AnyView(getCellOverlay(row: row, col: col, cellSize: cellSize))
                        }
                    )
                }
                .background(Color.gray.opacity(0.2))
                .cornerRadius(8)
            } else {
                loadingStateView
            }
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
    }
    
    private var loadingStateView: some View {
        VStack(spacing: 16) {
            ProgressView()
                .progressViewStyle(CircularProgressViewStyle(tint: .white))
                .scaleEffect(1.5)
            
            Text("Loading word search...")
                .font(.system(size: 16))
                .foregroundColor(.white)
        }
        .frame(maxHeight: 200)
    }
    
    private func completeGameWithDebug() {
        print("🎉 COMPLETE GAME CALLED")
        
        guard isViewActive else {
            print("⚠️ View is not active, cannot complete")
            return
        }
        
        print("✅ View is active, proceeding with completion")
        
        isCompleted = true
        completedWithinTime = !isOvertime
        completionTime = formatCompletionTime()
        
        print("📊 Completion details:")
        print("   - Completed within time: \(completedWithinTime)")
        print("   - Is overtime: \(isOvertime)")
        print("   - Completion time: \(completionTime)")
        
        timer?.invalidate()
        timer = nil
        print("⏰ Timer stopped")
        
        onAnswerSubmitted(true)
        print("📤 onAnswerSubmitted(true) called")
        
        DispatchQueue.main.async {
            print("🎭 Setting showCompletionDialog = true")
            self.showCompletionDialog = true
            print("🎭 showCompletionDialog is now: \(self.showCompletionDialog)")
        }
        
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.1) {
            if !self.showCompletionDialog {
                print("🔄 Backup: Setting showCompletionDialog = true after delay")
                self.showCompletionDialog = true
            }
        }
    }
    
    private func revealWord(_ word: String) {
        guard let puzzleData = puzzleData,
              !foundWords.contains(word),
              !revealedWords.contains(word) else {
            print("🚫 Cannot reveal word: \(word) (already found or revealed)")
            return
        }
        
        print("👁️ Revealing word: \(word)")
        
        if let positions = findWordPositions(word: word, in: puzzleData.matrix) {
            revealedWords.insert(word)
            revealedWordPositions[word] = positions
            
            print("✅ Word revealed: \(word)")
            print("📊 Total revealed: \(revealedWords.count)")
            
            withAnimation(.easeInOut(duration: 0.5)) {
                for pos in positions {
                    if pos.row < grid.count && pos.col < grid[pos.row].count {
                        grid[pos.row][pos.col].isRevealed = true
                        grid[pos.row][pos.col].foundWordId = word
                    }
                }
            }
            
            let impactFeedback = UIImpactFeedbackGenerator(style: .medium)
            impactFeedback.impactOccurred()
            
            checkPuzzleCompletionAfterReveal()
        } else {
            print("⚠️ Could not find positions for word: \(word)")
        }
    }
    
    private func checkPuzzleCompletionAfterReveal() {
        guard let wordsCount = puzzleData?.words.count else {
            print("⚠️ No puzzle data for completion check")
            return
        }
        
        let totalDiscovered = foundWords.count + revealedWords.count
        
        print("🔍 Completion check after reveal:")
        print("   - Total words: \(wordsCount)")
        print("   - Found words: \(foundWords.count)")
        print("   - Revealed words: \(revealedWords.count)")
        print("   - Total discovered: \(totalDiscovered)")
        print("   - Is completed: \(isCompleted)")
        
        if totalDiscovered >= wordsCount && !isCompleted {
            print("🎉 ALL WORDS DISCOVERED (found + revealed) - TRIGGERING COMPLETION")
            completeGameWithDebug()
        } else {
            print("📊 Progress: \(totalDiscovered)/\(wordsCount) words discovered")
        }
    }

    private func checkPuzzleCompletionWithLogging() {
        guard let wordsCount = puzzleData?.words.count else {
            print("⚠️ No puzzle data available for completion check")
            return
        }
        
        let totalDiscovered = foundWords.count + revealedWords.count
        
        print("🔍 Completion check:")
        print("   - Total words: \(wordsCount)")
        print("   - Found words: \(foundWords.count) - \(Array(foundWords))")
        print("   - Revealed words: \(revealedWords.count) - \(Array(revealedWords))")
        print("   - Total discovered: \(totalDiscovered)")
        print("   - Is completed: \(isCompleted)")
        print("   - Is view active: \(isViewActive)")
        
        let allWordsFound = foundWords.count == wordsCount
        let allWordsDiscovered = totalDiscovered >= wordsCount
        
        if allWordsDiscovered && !isCompleted {
            print("🎉 ALL WORDS DISCOVERED - TRIGGERING COMPLETION")
            completeGameWithDebug()
        } else if allWordsFound && !isCompleted {
            print("🎉 ALL WORDS FOUND - TRIGGERING COMPLETION")
            completeGameWithDebug()
        } else if (allWordsFound || allWordsDiscovered) && isCompleted {
            print("⚠️ All words discovered but already completed")
        } else {
            print("📊 Progress: \(foundWords.count) found + \(revealedWords.count) revealed = \(totalDiscovered)/\(wordsCount)")
        }
    }
    
    private func findWordPositions(word: String, in matrix: [[String]]) -> [(row: Int, col: Int)]? {
        let wordUpper = word.uppercased()
        let height = matrix.count
        guard height > 0 else { return nil }
        let width = matrix[0].count
        
        let directions = [
            (0, 1),   // Right
            (0, -1),  // Left
            (1, 0),   // Down
            (-1, 0),  // Up
            (1, 1),   // Down-Right
            (1, -1),  // Down-Left
            (-1, 1),  // Up-Right
            (-1, -1)  // Up-Left
        ]
        
        for row in 0..<height {
            for col in 0..<width {
                for (deltaRow, deltaCol) in directions {
                    if let positions = checkWordAt(
                        startRow: row,
                        startCol: col,
                        deltaRow: deltaRow,
                        deltaCol: deltaCol,
                        word: wordUpper,
                        matrix: matrix
                    ) {
                        return positions
                    }
                }
            }
        }
        
        return nil
    }
    
    private func checkWordAt(
        startRow: Int,
        startCol: Int,
        deltaRow: Int,
        deltaCol: Int,
        word: String,
        matrix: [[String]]
    ) -> [(row: Int, col: Int)]? {
        let wordChars = Array(word)
        var positions: [(row: Int, col: Int)] = []
        
        for i in 0..<wordChars.count {
            let row = startRow + (deltaRow * i)
            let col = startCol + (deltaCol * i)
            
            guard row >= 0 && row < matrix.count && col >= 0 && col < matrix[row].count else {
                return nil
            }
            
            guard matrix[row][col].uppercased() == String(wordChars[i]) else {
                return nil
            }
            
            positions.append((row: row, col: col))
        }
        
        return positions
    }
    
    private func setupPuzzle() {
        guard isViewActive else { return }
        
        if parsePuzzleData() {
            createGrid()
            startTimer()
        } else {
            generateFallbackPuzzle()
            createGrid()
            startTimer()
        }
    }
    
    private func cleanup() {
        isViewActive = false
        timer?.invalidate()
        timer = nil
        isCompleted = true
    }
    
    // MARK: - Word Search Tip Dialog View
    struct WordSearchTipDialogView: View {
        let onDismiss: () -> Void
        
        var body: some View {
            NavigationView {
                VStack(spacing: 24) {
                    VStack(spacing: 8) {
                        Image(systemName: "lightbulb.fill")
                            .font(.system(size: 50))
                            .foregroundColor(.yellow)
                        
                        Text("How to Find Words")
                            .font(.title2)
                            .fontWeight(.bold)
                    }
                    
                    VStack(alignment: .leading, spacing: 20) {
                        TipRowView(
                            icon: "hand.draw.fill",
                            title: "Drag Method",
                            description: "Drag your finger from the first letter to the last letter of any word"
                        )
                        
                        TipRowView(
                            icon: "hand.tap.fill",
                            title: "Double Tap Method",
                            description: "Double tap the first letter, then double tap the last letter to select the word"
                        )
                        
                        TipRowView(
                            icon: "arrow.up.arrow.down",
                            title: "All Directions",
                            description: "Words can be horizontal, vertical, or diagonal in any direction"
                        )
                        
                        TipRowView(
                            icon: "checkmark.circle.fill",
                            title: "Found Words",
                            description: "Found words will be highlighted and crossed out with colored lines"
                        )
                        
                        TipRowView(
                            icon: "eye.fill",
                            title: "Need Help?",
                            description: "Tap the ? button to see all words and reveal any word you're stuck on"
                        )
                    }
                    
                    Spacer()
                    
                    Button("Got it!") {
                        onDismiss()
                    }
                    .font(.headline)
                    .foregroundColor(.white)
                    .frame(maxWidth: .infinity)
                    .padding()
                    .background(Color(red: 0.0, green: 0.74, blue: 0.83))
                    .cornerRadius(12)
                    .padding(.horizontal)
                }
                .padding(24)
                .navigationTitle("Tips")
                .navigationBarTitleDisplayMode(.inline)
                .toolbar {
                    ToolbarItem(placement: .navigationBarTrailing) {
                        Button("Done") { onDismiss() }
                    }
                }
            }
        }
    }

    struct TipRowView: View {
        let icon: String
        let title: String
        let description: String
        
        var body: some View {
            HStack(alignment: .top, spacing: 16) {
                Image(systemName: icon)
                    .font(.system(size: 24))
                    .foregroundColor(Color(red: 0.0, green: 0.74, blue: 0.83))
                    .frame(width: 30)
                
                VStack(alignment: .leading, spacing: 4) {
                    Text(title)
                        .font(.system(size: 16, weight: .semibold))
                        .foregroundColor(.primary)
                    
                    Text(description)
                        .font(.system(size: 14))
                        .foregroundColor(.secondary)
                        .fixedSize(horizontal: false, vertical: true)
                }
                
                Spacer()
            }
        }
    }
    
    private var topBarSection: some View {
        HStack {
            Button(action: {
                cleanup()
                onExit()
            }) {
                Image(systemName: "arrow.left")
                    .font(.title2)
                    .foregroundColor(.white)
                    .frame(width: 40, height: 40)
            }
            .padding(.leading, 16)
            
            Spacer()
            
            VStack {
                HStack(spacing: 4) {
                    Text("Time")
                        .font(.caption)
                        .foregroundColor(.white.opacity(0.8))
                    
                    if isOvertime {
                        Image(systemName: "clock.badge.exclamationmark")
                            .font(.caption)
                            .foregroundColor(.yellow)
                    }
                }
                
                Text(formatTime(timeRemaining))
                    .font(.system(size: 18, weight: .bold))
                    .foregroundColor(isOvertime ? .yellow : .white)
            }
            
            Spacer()
            
            HStack(spacing: 8) {
                Button(action: { showTipDialog = true }) {
                    Image(systemName: "lightbulb.fill")
                        .font(.title2)
                        .foregroundColor(.yellow)
                        .frame(width: 40, height: 40)
                }
                
                Button(action: { showHintsDialog = true }) {
                    Image(systemName: "questionmark.circle")
                        .font(.title2)
                        .foregroundColor(.white)
                        .frame(width: 40, height: 40)
                }
            }
            .padding(.trailing, 16)
        }
        .padding(.top, 8)
        .padding(.horizontal, 0)
    }
    
    private var instructionSection: some View {
        VStack(spacing: 8) {
            HStack(spacing: 8) {
                Image(systemName: "hand.point.up.left.fill")
                    .foregroundColor(.yellow)
                    .font(.system(size: 16))
                
                Text(getInstructionText())
                    .font(.system(size: 14, weight: .medium))
                    .foregroundColor(.white)
                    .multilineTextAlignment(.center)
                
                Spacer()
                
                Button(action: clearSelection) {
                    Text("Clear")
                        .font(.caption)
                        .foregroundColor(.white)
                        .padding(.horizontal, 8)
                        .padding(.vertical, 4)
                        .background(Color.white.opacity(0.2))
                        .cornerRadius(6)
                }
            }
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 8)
        .background(Color.black.opacity(0.2))
        .cornerRadius(8)
        .padding(.horizontal, 16)
    }

    private func getInstructionText() -> String {
        switch selectionStep {
        case .waitingForStart:
            return "Tap the FIRST letter of a word"
        case .waitingForEnd:
            return "Now tap the LAST letter of the same word"
        }
    }
    
    
    
    private var progressSection: some View {
        VStack(spacing: 8) {
            HStack {
                Text("Words Found: \(foundWords.count)/\(puzzleData?.words.count ?? 0)")
                    .font(.system(size: 16, weight: .bold))
                    .foregroundColor(Color(red: 0.0, green: 0.74, blue: 0.83))
                
                Spacer()
                
                if let wordsCount = puzzleData?.words.count, wordsCount > 0 {
                    ProgressView(value: Double(foundWords.count), total: Double(wordsCount))
                        .frame(width: 120, height: 8)
                        .progressViewStyle(LinearProgressViewStyle(tint: Color(red: 0.3, green: 0.69, blue: 0.31)))
                }
            }
            
            if !isCompleted {
                HStack {
                    if !isOvertime {
                        HStack(spacing: 4) {
                            Image(systemName: "star.fill")
                                .font(.caption)
                                .foregroundColor(.yellow)
                            Text("Bonus available!")
                                .font(.caption)
                                .foregroundColor(.orange)
                        }
                    } else {
                        HStack(spacing: 4) {
                            Image(systemName: "clock")
                                .font(.caption)
                                .foregroundColor(.orange)
                            Text("Overtime - Regular points")
                                .font(.caption)
                                .foregroundColor(.orange)
                        }
                    }
                    Spacer()
                }
            }
        }
        .padding(12)
        .background(Color.white.opacity(0.9))
        .cornerRadius(8)
        .padding(.horizontal, 16)
        .padding(.vertical, 8)
    }
    
    private var enhancedHintsSection: some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack {
                Text("Find these words:")
                    .font(.system(size: 18, weight: .bold))
                    .foregroundColor(Color(red: 0.0, green: 0.74, blue: 0.83))
                
                Spacer()
                
                Text("↕️ Scroll")
                    .font(.caption)
                    .foregroundColor(.gray)
                    .opacity(0.7)
            }
            .padding(.horizontal, 16)
            
            ScrollView {
                LazyVStack(spacing: 10) {
                    if let words = puzzleData?.words {
                        ForEach(words, id: \.id) { word in
                            HStack {
                                EnhancedWordHintItemView(
                                    word: word,
                                    isFound: foundWords.contains(word.word),
                                    isRevealed: revealedWords.contains(word.word),
                                    color: getWordColor(for: word.word)
                                )
                                
                                if !foundWords.contains(word.word) && !revealedWords.contains(word.word) {
                                    Button(action: {
                                        revealWord(word.word)
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
                .padding(.horizontal, 16)
                .padding(.vertical, 8)
            }
            .background(
                RoundedRectangle(cornerRadius: 8)
                    .fill(Color.white.opacity(0.1))
                    .overlay(
                        RoundedRectangle(cornerRadius: 8)
                            .stroke(Color.white.opacity(0.3), lineWidth: 1)
                    )
            )
            .padding(.horizontal, 16)
        }
        .padding(.vertical, 8)
        .background(Color.white.opacity(0.95))
        .cornerRadius(12)
        .padding(.horizontal, 16)
    }
    
    private func handleCellTap(row: Int, col: Int) {
        let position = GridPosition(row: row, col: col)
        
        let impactFeedback = UIImpactFeedbackGenerator(style: .medium)
        impactFeedback.impactOccurred()
        
        switch selectionStep {
        case .waitingForStart:
            selectedStartPosition = position
            selectedEndPosition = nil
            selectionStep = .waitingForEnd
            
        case .waitingForEnd:
            selectedEndPosition = position
            
            if let start = selectedStartPosition {
                let success = selectWord(from: start, to: position)
                if success {
                    clearSelection()
                } else {
                    selectedStartPosition = position
                    selectedEndPosition = nil
                    selectionStep = .waitingForEnd
                }
            }
        }
    }

    private func clearSelection() {
        selectedStartPosition = nil
        selectedEndPosition = nil
        selectionStep = .waitingForStart
    }
    
    private func selectWord(from start: GridPosition, to end: GridPosition) -> Bool {
        guard let puzzleData = puzzleData else { return false }
        
        let positions = getPositionsBetween(start: start, end: end)
        let selectedWord = positions.map { puzzleData.matrix[$0.row][$0.col] }.joined()
        
        let reverseWord = String(selectedWord.reversed())
        
        print("🔍 Trying to select: '\(selectedWord)' (reverse: '\(reverseWord)')")
        
        for word in puzzleData.words {
            let wordUpper = word.word.uppercased()
            if (wordUpper == selectedWord.uppercased() || wordUpper == reverseWord.uppercased()) && !foundWords.contains(word.word) {
                print("✅ Valid word found: \(word.word)")
                
                foundWordPositions[word.word] = positions
                
                withAnimation(.easeInOut(duration: 0.3)) {
                    for pos in positions {
                        if pos.row < grid.count && pos.col < grid[pos.row].count {
                            grid[pos.row][pos.col].isFound = true
                            grid[pos.row][pos.col].foundWordId = word.word
                        }
                    }
                }
                
                handleWordFound(word.word)
                return true
            }
        }
        
        print("⚠️ No valid word found for selection")
        return false
    }
    
    private func getPositionsBetween(start: GridPosition, end: GridPosition) -> [(row: Int, col: Int)] {
        var positions: [(row: Int, col: Int)] = []
        
        let rowDiff = end.row - start.row
        let colDiff = end.col - start.col
        let steps = max(abs(rowDiff), abs(colDiff))
        
        if steps == 0 {
            return [(start.row, start.col)]
        }
        
        let rowStep = rowDiff / steps
        let colStep = colDiff / steps
        
        for i in 0...steps {
            positions.append((
                row: start.row + (rowStep * i),
                col: start.col + (colStep * i)
            ))
        }
        
        return positions
    }
    
    private func getCellOverlay(row: Int, col: Int, cellSize: CGFloat) -> some View {
        ZStack {
            if let startPos = selectedStartPosition, startPos.row == row, startPos.col == col {
                Rectangle()
                    .fill(Color.blue.opacity(0.6))
            }
            
            if let endPos = selectedEndPosition, endPos.row == row, endPos.col == col {
                Rectangle()
                    .fill(Color.blue.opacity(0.4))
            }
            
            ForEach(Array(foundWordPositions.keys), id: \.self) { word in
                if let positions = foundWordPositions[word],
                   positions.contains(where: { $0.row == row && $0.col == col }) {
                    
                    Path { path in
                        path.move(to: CGPoint(x: 2, y: 2))
                        path.addLine(to: CGPoint(x: cellSize - 2, y: cellSize - 2))
                    }
                    .stroke(Color.green, lineWidth: 2)
                    
                    Path { path in
                        path.move(to: CGPoint(x: cellSize - 2, y: 2))
                        path.addLine(to: CGPoint(x: 2, y: cellSize - 2))
                    }
                    .stroke(Color.green, lineWidth: 2)
                }
            }
            
            ForEach(Array(revealedWordPositions.keys), id: \.self) { word in
                if let positions = revealedWordPositions[word],
                   positions.contains(where: { $0.row == row && $0.col == col }) {
                    
                    Path { path in
                        path.move(to: CGPoint(x: 2, y: cellSize / 2))
                        path.addLine(to: CGPoint(x: cellSize - 2, y: cellSize / 2))
                    }
                    .stroke(Color.orange, style: StrokeStyle(lineWidth: 3, dash: [4, 2]))
                }
            }
        }
    }
    
    private func getCellTextColor(row: Int, col: Int) -> Color {
        for positions in foundWordPositions.values {
            if positions.contains(where: { $0.row == row && $0.col == col }) {
                return .white
            }
        }
        
        for positions in revealedWordPositions.values {
            if positions.contains(where: { $0.row == row && $0.col == col }) {
                return .white
            }
        }
        
        return .black
    }
    
    private func getCellBackgroundColor(row: Int, col: Int) -> Color {
        for (index, positions) in foundWordPositions.values.enumerated() {
            if positions.contains(where: { $0.row == row && $0.col == col }) {
                let colorIndex = index % wordColors.count
                return wordColors[colorIndex].opacity(0.8)
            }
        }
        
        for positions in revealedWordPositions.values {
            if positions.contains(where: { $0.row == row && $0.col == col }) {
                return Color.orange.opacity(0.6)
            }
        }
        
        
        if let startPos = selectedStartPosition, startPos.row == row, startPos.col == col {
            return Color.blue.opacity(0.5)
        }
        
        if let endPos = selectedEndPosition, endPos.row == row, endPos.col == col {
            return Color.blue.opacity(0.3)
        }
        
        return Color.white.opacity(0.9)
    }
    
    private var resetButtonSection: some View {
        Button(action: resetGame) {
            HStack {
                Image(systemName: "arrow.clockwise")
                    .font(.system(size: 16))
                Text("RESET")
                    .font(.system(size: 14, weight: .bold))
            }
            .foregroundColor(.white)
            .padding(.horizontal, 20)
            .padding(.vertical, 10)
            .overlay(
                RoundedRectangle(cornerRadius: 20)
                    .stroke(Color.white, lineWidth: 2)
            )
        }
        .padding(16)
    }
    
    @discardableResult
    private func parsePuzzleData() -> Bool {
        print("🔍 WORDSEARCH: Parsing puzzle data from question: \(puzzle.question)")
        
        guard let data = puzzle.question.data(using: .utf8) else {
            print("⚠️ Failed to convert question to data")
            return false
        }
        
        do {
            let wordSearchJson = try JSONSerialization.jsonObject(with: data) as? [String: Any]
            
            if let matrixArray = wordSearchJson?["matrix"] as? [[Any]],
               let wordsArray = wordSearchJson?["words"] as? [[String: Any]],
               let width = wordSearchJson?["width"] as? Int,
               let height = wordSearchJson?["height"] as? Int {
                
                print("📊 Found direct word search JSON format")
                return parseWordSearchData(from: wordSearchJson!)
                
            } else {
                print("📊 Trying nested JSON format")
                let outerJson = wordSearchJson
                
                let innerQuestionString: String
                if let questionString = outerJson?["question"] as? String {
                    innerQuestionString = questionString
                    print("📊 Detected double-nested JSON format")
                } else {
                    innerQuestionString = puzzle.question
                    print("📊 Detected single JSON format")
                }
                
                guard let innerData = innerQuestionString.data(using: .utf8),
                      let innerWordSearchJson = try JSONSerialization.jsonObject(with: innerData) as? [String: Any] else {
                    print("⚠️ Failed to parse inner word search JSON")
                    return false
                }
                
                return parseWordSearchData(from: innerWordSearchJson)
            }
            
        } catch {
            print("⚠️ Failed to parse word search JSON: \(error)")
            return false
        }
    }
    
    private func parseWordSearchData(from wordSearchJson: [String: Any]) -> Bool {
        guard let matrixArray = wordSearchJson["matrix"] as? [[Any]],
              let wordsArray = wordSearchJson["words"] as? [[String: Any]] else {
            print("⚠️ Failed to parse word search fields")
            print("⚠️ Available keys: \(wordSearchJson.keys)")
            return false
        }

        // Derive dimensions from actual matrix, falling back to declared values
        let actualHeight = matrixArray.count
        let actualWidth = matrixArray.first?.count ?? 0
        let height = min(wordSearchJson["height"] as? Int ?? actualHeight, actualHeight)
        let width = min(wordSearchJson["width"] as? Int ?? actualWidth, actualWidth)

        guard height > 0 && width > 0 else {
            print("⚠️ Empty matrix: \(actualWidth)x\(actualHeight)")
            return false
        }

        let matrix = matrixArray.prefix(height).map { row in
            row.prefix(width).map { cell in
                if let stringCell = cell as? String {
                    return stringCell
                } else if let intCell = cell as? Int {
                    return String(intCell)
                } else {
                    return String(describing: cell)
                }
            }
        }

        let words = wordsArray.compactMap { wordDict -> WordSearchWord? in
            guard let word = wordDict["word"] as? String,
                  let hint = wordDict["hint"] as? String else {
                print("⚠️ Failed to parse word: \(wordDict)")
                return nil
            }

            let direction = wordDict["direction"] as? String ?? "horizontal"
            let length = wordDict["length"] as? Int ?? word.count

            return WordSearchWord(
                word: word,
                hint: hint,
                direction: direction,
                length: length
            )
        }

        guard !words.isEmpty else {
            print("⚠️ No valid words parsed from word search data")
            return false
        }

        let instructions = wordSearchJson["instructions"] as? String ?? "Find all hidden words"

        puzzleData = WordSearchPuzzleData(
            matrix: matrix,
            words: words,
            width: width,
            height: height,
            instructions: instructions
        )

        let wordCount = words.count
        let difficulty = puzzle.difficulty ?? "Medium"
        timeRemaining = calculateTimeLimit(difficulty: difficulty, wordCount: wordCount)
        originalTimeLimit = timeRemaining

        print("✅ Parsed word search data:")
        print("   Grid size: \(width)x\(height)")
        print("   Word count: \(words.count)")
        print("   Words: \(words.map { $0.word })")
        print("   Time limit: \(timeRemaining)s")
        print("   Instructions: \(instructions)")

        return true
    }
    
    private func generateFallbackPuzzle() {
        print("🔄 Generating fallback word search puzzle")
        
        let fallbackMatrix = [
            ["C", "A", "T", "X", "Z"],
            ["X", "D", "O", "G", "Y"],
            ["B", "I", "R", "D", "X"],
            ["F", "I", "S", "H", "Z"],
            ["X", "Y", "Z", "W", "Q"]
        ]
        
        let fallbackWords = [
            WordSearchWord(word: "CAT", hint: "Feline pet", direction: "horizontal", length: 3),
            WordSearchWord(word: "DOG", hint: "Canine pet", direction: "horizontal", length: 3),
            WordSearchWord(word: "BIRD", hint: "Flying animal", direction: "horizontal", length: 4),
            WordSearchWord(word: "FISH", hint: "Swimming animal", direction: "horizontal", length: 4)
        ]
        
        puzzleData = WordSearchPuzzleData(
            matrix: fallbackMatrix,
            words: fallbackWords,
            width: 5,
            height: 5,
            instructions: "Find all hidden words"
        )
        
        timeRemaining = 180
        originalTimeLimit = timeRemaining
        print("✅ Generated fallback puzzle with \(fallbackWords.count) words")
    }
    
    private func calculateTimeLimit(difficulty: String, wordCount: Int) -> Int {
        let baseTimePerWord: Int
        
        switch difficulty.lowercased() {
        case "easy":
            baseTimePerWord = 30
        case "medium":
            baseTimePerWord = 25
        case "hard":
            baseTimePerWord = 20
        default:
            baseTimePerWord = 25
        }
        
        let calculatedTime = wordCount * baseTimePerWord + 60
        return max(calculatedTime, 120)
    }
    
    private func createGrid() {
        guard let data = puzzleData else { return }

        grid = []
        let safeHeight = min(data.height, data.matrix.count)
        for y in 0..<safeHeight {
            var row: [WordSearchCell] = []
            let safeWidth = min(data.width, data.matrix[y].count)
            for x in 0..<safeWidth {
                let letter = data.matrix[y][x]
                row.append(WordSearchCell(x: x, y: y, letter: letter))
            }
            grid.append(row)
        }
    }
    
    private func handleWordFound(_ word: String) {
        guard !foundWords.contains(word) && isViewActive else {
            print("🚫 Word already found or view inactive: \(word)")
            return
        }
        
        print("✅ Word found manually: \(word)")
        print("📊 Before adding - Found: \(foundWords.count), Revealed: \(revealedWords.count)")
        
        foundWords.insert(word)
        print("📊 After adding - Found: \(foundWords.count), Revealed: \(revealedWords.count)")
        
        let impact = UIImpactFeedbackGenerator(style: .heavy)
        impact.impactOccurred()
        
        onAnswerSubmitted(true)
        
        checkPuzzleCompletionAfterReveal()
    }
    
    private func completeGame() {
        completeGameWithDebug()
    }
    
    private func resetGame() {
        foundWords.removeAll()
        revealedWords.removeAll()
        foundWordPositions.removeAll()
        revealedWordPositions.removeAll()
        foundPaths.removeAll()
        isCompleted = false
        
        isOvertime = false
        completedWithinTime = false
        timeRemaining = originalTimeLimit
        
        createGrid()
        
        startTimer()
        
        let impact = UIImpactFeedbackGenerator(style: .medium)
        impact.impactOccurred()
    }
    
    private func getWordColor(for word: String) -> Color {
        if foundWords.contains(word) {
            let sortedWords = Array(foundWords).sorted()
            let index = sortedWords.firstIndex(of: word) ?? 0
            return wordColors[index % wordColors.count]
        } else if revealedWords.contains(word) {
            return .orange
        }
        return .gray
    }
    
    private func startTimer() {
        guard isViewActive else { return }
        
        timer?.invalidate()
        timer = Timer.scheduledTimer(withTimeInterval: 1.0, repeats: true) { _ in
            if isViewActive && !isCompleted {
                if timeRemaining > 0 {
                    timeRemaining -= 1
                } else {
                    if !isOvertime {
                        isOvertime = true
                        let impact = UIImpactFeedbackGenerator(style: .heavy)
                        impact.impactOccurred()
                        print("⏰ Time's up! Entering overtime mode - regular points available")
                    }
                    timeRemaining -= 1
                }
            }
        }
    }
    
    private func formatTime(_ seconds: Int) -> String {
        if seconds >= 0 {
            let minutes = seconds / 60
            let remainingSeconds = seconds % 60
            return String(format: "%d:%02d", minutes, remainingSeconds)
        } else {
            let overtimeSeconds = abs(seconds)
            let minutes = overtimeSeconds / 60
            let remainingSeconds = overtimeSeconds % 60
            return String(format: "+%d:%02d", minutes, remainingSeconds)
        }
    }
    
    private func formatCompletionTime() -> String {
        if completedWithinTime {
            let timeUsed = originalTimeLimit - timeRemaining
            let minutes = timeUsed / 60
            let seconds = timeUsed % 60
            return String(format: "%d:%02d", minutes, seconds)
        } else {
            let overtimeUsed = abs(timeRemaining)
            let minutes = overtimeUsed / 60
            let seconds = overtimeUsed % 60
            return String(format: "+%d:%02d (Overtime)", minutes, seconds)
        }
    }
    
    private var timeString: String {
        formatTime(timeRemaining)
    }
}

struct EnhancedWordHintItemView: View {
    let word: WordSearchWord
    let isFound: Bool
    let isRevealed: Bool
    let color: Color
    
    var body: some View {
        HStack(spacing: 12) {
            Circle()
                .fill(getStatusColor())
                .frame(width: 20, height: 20)
                .overlay(
                    getStatusIcon()
                        .foregroundColor(.white)
                        .font(.system(size: 12, weight: .bold))
                )
            
            VStack(alignment: .leading, spacing: 4) {
                Text(word.word)
                    .font(.system(size: 16, weight: .bold))
                    .foregroundColor(getTextColor())
                    .strikethrough(isFound || isRevealed)
                
                Text(word.hint)
                    .font(.system(size: 14))
                    .foregroundColor(getHintColor())
                    .opacity(getTextOpacity())
                    .lineLimit(2)
            }
            
            Spacer()
            
            Text("\(word.length)")
                .font(.system(size: 12, weight: .semibold))
                .foregroundColor(.gray.opacity(0.6))
                .padding(.horizontal, 8)
                .padding(.vertical, 4)
                .background(Color.gray.opacity(0.1))
                .cornerRadius(8)
        }
        .padding(.vertical, 8)
        .padding(.horizontal, 12)
        .background(getBackgroundColor())
        .cornerRadius(10)
        .animation(.easeInOut(duration: 0.3), value: isFound)
        .animation(.easeInOut(duration: 0.3), value: isRevealed)
    }
    
    private func getStatusColor() -> Color {
        if isFound {
            return color
        } else if isRevealed {
            return .orange
        } else {
            return Color.gray.opacity(0.3)
        }
    }
    
    private func getStatusIcon() -> some View {
        Group {
            if isFound {
                Image(systemName: "checkmark")
            } else if isRevealed {
                Image(systemName: "eye")
            } else {
                Circle()
                    .fill(Color.clear)
            }
        }
    }
    
    private func getTextColor() -> Color {
        if isFound {
            return .gray
        } else if isRevealed {
            return .orange
        } else {
            return .black
        }
    }
    
    private func getHintColor() -> Color {
        if isFound || isRevealed {
            return Color.gray.opacity(0.7)
        } else {
            return .gray
        }
    }
    
    private func getTextOpacity() -> Double {
        if isFound || isRevealed {
            return 0.6
        } else {
            return 1.0
        }
    }
    
    private func getBackgroundColor() -> Color {
        if isFound {
            return color.opacity(0.1)
        } else if isRevealed {
            return Color.orange.opacity(0.1)
        } else {
            return Color.clear
        }
    }
}

struct EnhancedWordSearchHintsDialogView: View {
    let words: [WordSearchWord]
    let foundWords: Set<String>
    let revealedWords: Set<String>
    let onRevealWord: (String) -> Void
    let onDismiss: () -> Void
    
    var body: some View {
        NavigationView {
            VStack(spacing: 16) {
                VStack(spacing: 8) {
                    Text("Word List & Hints")
                        .font(.title2)
                        .fontWeight(.bold)
                    
                    HStack(spacing: 16) {
                        StatItemView(
                            icon: "checkmark.circle.fill",
                            color: .green,
                            count: foundWords.count,
                            label: "Found"
                        )
                        
                        StatItemView(
                            icon: "eye.fill",
                            color: .orange,
                            count: revealedWords.count,
                            label: "Revealed"
                        )
                        
                        StatItemView(
                            icon: "questionmark.circle",
                            color: .gray,
                            count: words.count - foundWords.count - revealedWords.count,
                            label: "Remaining"
                        )
                    }
                }
                .padding(.horizontal)
                
                ScrollView {
                    LazyVStack(spacing: 12) {
                        ForEach(words, id: \.id) { word in
                            HStack {
                                EnhancedWordHintItemView(
                                    word: word,
                                    isFound: foundWords.contains(word.word),
                                    isRevealed: revealedWords.contains(word.word),
                                    color: Color(red: 0.3, green: 0.69, blue: 0.31)
                                )
                                
                                if !foundWords.contains(word.word) && !revealedWords.contains(word.word) {
                                    Button(action: {
                                        onRevealWord(word.word)
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
                    .padding(.horizontal)
                }
                
                VStack(spacing: 8) {
                    HStack(spacing: 8) {
                        Image(systemName: "info.circle")
                            .foregroundColor(.blue)
                        Text("Tap 'Show' to reveal a word's location in the grid")
                            .font(.caption)
                            .foregroundColor(.secondary)
                    }
                    
                    HStack(spacing: 8) {
                        Image(systemName: "exclamationmark.triangle")
                            .foregroundColor(.orange)
                        Text("Revealed words don't count toward your score")
                            .font(.caption)
                            .foregroundColor(.secondary)
                    }
                }
                .padding(.horizontal)
                
                Button("Close") {
                    onDismiss()
                }
                .font(.headline)
                .foregroundColor(.white)
                .frame(maxWidth: .infinity)
                .padding()
                .background(Color(red: 0.0, green: 0.74, blue: 0.83))
                .cornerRadius(12)
                .padding(.horizontal)
            }
            .padding(.vertical)
            .navigationTitle("Word Search Help")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .navigationBarTrailing) {
                    Button("Done") { onDismiss() }
                }
            }
        }
    }
}

struct StatItemView: View {
    let icon: String
    let color: Color
    let count: Int
    let label: String
    
    var body: some View {
        VStack(spacing: 4) {
            HStack(spacing: 4) {
                Image(systemName: icon)
                    .foregroundColor(color)
                Text("\(count)")
                    .fontWeight(.bold)
            }
            .font(.system(size: 16))
            
            Text(label)
                .font(.caption)
                .foregroundColor(.secondary)
        }
    }
}

struct WordSearchCompletionDialogView: View {
    let time: String
    let wordsFound: Int
    let wordsRevealed: Int
    let totalWords: Int
    let completedWithinTime: Bool
    let isOvertime: Bool
    let onShare: () -> Void
    let onReset: () -> Void
    let onNext: () -> Void
    let onDismiss: () -> Void
    
    var completionMethod: String {
        if wordsRevealed == 0 {
            return "Perfect! All words found!"
        } else if wordsFound == 0 {
            return "All words revealed"
        } else {
            return "Puzzle completed"
        }
    }
    
    var scoreText: String {
        if wordsRevealed == 0 {
            return "Full Score!"
        } else {
            return "Practice Mode"
        }
    }
    
    var body: some View {
        VStack(spacing: 20) {
            VStack(spacing: 8) {
                Text("🎉 \(completionMethod)")
                    .font(.title2)
                    .fontWeight(.bold)
                    .foregroundColor(.white)
                    .multilineTextAlignment(.center)
                
                Text(scoreText)
                    .font(.headline)
                    .foregroundColor(wordsRevealed == 0 ? .yellow : .orange)
            }
            
            VStack(spacing: 8) {
                if wordsFound > 0 {
                    HStack {
                        Image(systemName: "checkmark.circle.fill")
                            .foregroundColor(.green)
                        Text("Words Found: \(wordsFound)")
                        Spacer()
                    }
                    .foregroundColor(.white)
                }
                
                if wordsRevealed > 0 {
                    HStack {
                        Image(systemName: "eye.fill")
                            .foregroundColor(.orange)
                        Text("Words Revealed: \(wordsRevealed)")
                        Spacer()
                    }
                    .foregroundColor(.white)
                }
                
                HStack {
                    Image(systemName: "clock")
                        .foregroundColor(.white)
                    Text("Time: \(time)")
                    Spacer()
                }
                .foregroundColor(.white)
            }
            .font(.subheadline)
            
            VStack(spacing: 12) {
                Button("Next Puzzle") {
                    print("✅ Next Puzzle button tapped")
                    onNext()
                }
                .font(.headline)
                .foregroundColor(.white)
                .frame(maxWidth: .infinity)
                .padding()
                .background(Color.green)
                .cornerRadius(12)
                
                HStack(spacing: 16) {
                    Button("Reset") {
                        print("🔄 Reset button tapped")
                        onReset()
                        onDismiss()
                    }
                    .font(.subheadline)
                    .foregroundColor(Color(red: 0.0, green: 0.74, blue: 0.83))
                    .padding(.horizontal, 20)
                    .padding(.vertical, 10)
                    .background(Color.white)
                    .cornerRadius(20)
                    
                    Button("Share") {
                        print("📤 Share button tapped")
                        onShare()
                    }
                    .font(.subheadline)
                    .foregroundColor(.white)
                    .padding(.horizontal, 20)
                    .padding(.vertical, 10)
                    .background(Color.white.opacity(0.2))
                    .cornerRadius(20)
                    .overlay(
                        RoundedRectangle(cornerRadius: 20)
                            .stroke(Color.white, lineWidth: 2)
                    )
                }
            }
        }
        .padding(24)
        .background(Color(red: 0.0, green: 0.74, blue: 0.83))
        .cornerRadius(16)
        .padding(.horizontal, 24)
        .onAppear {
            print("🎭 Completion dialog appeared")
        }
    }
}
