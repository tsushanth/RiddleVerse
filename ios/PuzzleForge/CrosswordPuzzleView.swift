//
//  CrosswordPuzzleView.swift
//  PuzzleForge
//
//  Created by Assistant on [Current Date]
//

import SwiftUI

// MARK: - Data Models
struct CrosswordCell {
    let x: Int
    let y: Int
    let letter: String
    let isBlocked: Bool
    var number: Int?
    var userInput: String = ""
    var isHighlighted: Bool = false
    var isSelected: Bool = false
    var isError: Bool = false
}

struct CrosswordWord {
    let word: String
    let hint: String
    let startX: Int
    let startY: Int
    let direction: String // "horizontal" or "vertical"
    let length: Int
    let number: Int
}

enum CrosswordDirection {
    case horizontal, vertical
}

// MARK: - Crossword Puzzle Data
struct CrosswordPuzzleData: Codable {
    let words: [CrosswordWordData]
    let matrix: [[String]]
    
    struct CrosswordWordData: Codable {
        let word: String
        let hint: String
        let startX: Int
        let startY: Int
        let direction: String
        let length: Int
    }
}

// MARK: - Main Crossword View
struct CrosswordPuzzleView: View {
    let puzzle: Puzzle
    let questionIndex: Int
    let totalQuestions: Int
    let onAnswerSubmitted: (String, Bool) -> Void
    let onNextPuzzle: () -> Void
    let onExit: () -> Void
    
    @State private var grid: [[CrosswordCell]] = []
    @State private var words: [CrosswordWord] = []
    @State private var selectedCell: (Int, Int)? = nil
    @State private var currentDirection: CrosswordDirection = .horizontal
    @State private var currentHint: String = ""
    @State private var currentWord: CrosswordWord? = nil
    @State private var isCompleted: Bool = false
    @State private var showHintsDialog: Bool = false
    @State private var showCompletionDialog: Bool = false
    @State private var showIncorrectAnswersDialog: Bool = false
    @State private var timerString: String = "5:00"
    @State private var recompositionTrigger: Int = 0
    
    private let gridWidth = 5
    private let gridHeight = 5
    
    var body: some View {
        ZStack {
            // Cyan background matching Android
            Color(red: 0.0, green: 0.737, blue: 0.831)
                .ignoresSafeArea()
            
            VStack(spacing: 0) {
                // Top Bar
                topBar
                
                // Current Hint Display
                if !currentHint.isEmpty {
                    currentHintCard
                }
                
                // Crossword Grid
                Spacer()
                crosswordGrid
                Spacer()
                
                // Action Buttons
                actionButtons
                
                // Keyboard
                crosswordKeyboard
            }
        }
        .onAppear {
            setupPuzzle()
        }
        .sheet(isPresented: $showHintsDialog) {
            HintsDialogView(words: words, onDismiss: { showHintsDialog = false })
        }
        .sheet(isPresented: $showCompletionDialog) {
            CompletionDialogView(
                time: timerString,
                onShare: { /* Handle share */ },
                onReset: {
                    clearGrid()
                    showCompletionDialog = false
                },
                onNext: {
                    showCompletionDialog = false
                    onNextPuzzle()
                },
                onDismiss: { showCompletionDialog = false }
            )
        }
        .sheet(isPresented: $showIncorrectAnswersDialog) {
            IncorrectAnswersDialogView(
                onDismiss: { showIncorrectAnswersDialog = false },
                onTryAgain: { showIncorrectAnswersDialog = false }
            )
        }
    }
    
    // MARK: - UI Components
    private var topBar: some View {
        HStack {
            Spacer()
            
            Text(timerString)
                .font(.title2)
                .fontWeight(.bold)
                .foregroundColor(.white)
            
            Spacer()
            
            Button(action: { showHintsDialog = true }) {
                Image(systemName: "questionmark")
                    .font(.title2)
                    .foregroundColor(.white)
                    .frame(width: 40, height: 40)
            }
        }
        .padding(.horizontal, 16)
        .padding(.top, 10)
    }
    
    private var currentHintCard: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text("\(currentWord?.number ?? 0) \(currentDirection == .horizontal ? "Across" : "Down")")
                .font(.caption)
                .fontWeight(.bold)
                .foregroundColor(Color(red: 0.0, green: 0.737, blue: 0.831))
            
            Text(currentHint)
                .font(.subheadline)
                .foregroundColor(.black)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(12)
        .background(
            RoundedRectangle(cornerRadius: 8)
                .fill(Color.white.opacity(0.9))
        )
        .padding(.horizontal, 16)
        .padding(.vertical, 8)
    }
    
    private var crosswordGrid: some View {
        VStack(spacing: 2) {
            ForEach(0..<gridHeight, id: \.self) { y in
                HStack(spacing: 2) {
                    ForEach(0..<gridWidth, id: \.self) { x in
                        Group {
                            if y < grid.count && x < grid[y].count {
                                CrosswordCellView(
                                    cell: grid[y][x],
                                    size: 64,
                                    onTap: {
                                        handleCellTap(x: x, y: y)
                                    }
                                )
                            } else {
                                // Placeholder to keep spacing
                                Rectangle()
                                    .fill(Color(red: 0.2, green: 0.2, blue: 0.2))
                                    .frame(width: 64, height: 64)
                                    .overlay(
                                        Rectangle()
                                            .stroke(Color.black.opacity(0.2), lineWidth: 1)
                                    )
                            }
                        }
                    }
                }
                .frame(maxWidth: .infinity, alignment: .center)
            }
        }
        .padding(.horizontal, 16)
    }
    
    private var actionButtons: some View {
        HStack(spacing: 16) {
            // Reveal button
            Button(action: handleRevealButton) {
                Text("REVEAL")
                    .font(.subheadline)
                    .fontWeight(.bold)
                    .foregroundColor(.white)
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 12)
                    .overlay(
                        RoundedRectangle(cornerRadius: 8)
                            .stroke(Color.white, lineWidth: 2)
                    )
            }
            
            // Clear button
            Button(action: handleClearButton) {
                Text("CLEAR")
                    .font(.subheadline)
                    .fontWeight(.bold)
                    .foregroundColor(.white)
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 12)
                    .overlay(
                        RoundedRectangle(cornerRadius: 8)
                            .stroke(Color.white, lineWidth: 2)
                    )
            }
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 8)
    }
    
    private var crosswordKeyboard: some View {
        CrosswordKeyboardView(
            onLetterClick: handleLetterInput,
            onBackspaceClick: handleBackspace,
            recompositionTrigger: recompositionTrigger
        )
    }
    
    // MARK: - Setup and Data Parsing
    private func setupPuzzle() {
        guard let puzzleData = parseCrosswordData(from: puzzle.question) else {
            print("❌ Failed to parse crossword data")
            return
        }
        
        self.grid = puzzleData.grid
        self.words = puzzleData.words
        
        print("✅ Crossword puzzle setup complete")
        print("📊 Grid: \(gridWidth)x\(gridHeight), Words: \(words.count)")
    }
    
    private func parseCrosswordData(from jsonString: String) -> (grid: [[CrosswordCell]], words: [CrosswordWord])? {
        guard let data = jsonString.data(using: .utf8),
              let puzzleData = try? JSONDecoder().decode(CrosswordPuzzleData.self, from: data) else {
            print("❌ Failed to decode crossword JSON")
            return nil
        }
        
        // Create grid
        var tempGrid: [[CrosswordCell]] = []
        for (y, row) in puzzleData.matrix.enumerated() {
            var gridRow: [CrosswordCell] = []
            for (x, cellValue) in row.enumerated() {
                let cell = CrosswordCell(
                    x: x,
                    y: y,
                    letter: cellValue == "_" ? "" : cellValue,
                    isBlocked: cellValue == "_"
                )
                gridRow.append(cell)
            }
            tempGrid.append(gridRow)
        }
        
        // Create words and add numbers
        var tempWords: [CrosswordWord] = []
        for (index, wordData) in puzzleData.words.enumerated() {
            let word = CrosswordWord(
                word: wordData.word,
                hint: wordData.hint,
                startX: wordData.startX,
                startY: wordData.startY,
                direction: wordData.direction,
                length: wordData.length,
                number: index + 1
            )
            tempWords.append(word)
            
            // Add number to starting cell
            if word.startY < tempGrid.count && word.startX < tempGrid[word.startY].count {
                tempGrid[word.startY][word.startX].number = word.number
            }
        }
        
        return (grid: tempGrid, words: tempWords)
    }
    
    // MARK: - Game Logic
    private func handleCellTap(x: Int, y: Int) {
        if let current = selectedCell, current.0 == x && current.1 == y {
            // Toggle direction if same cell is tapped
            currentDirection = currentDirection == .horizontal ? .vertical : .horizontal
        } else {
            selectedCell = (x, y)
        }
        
        updateGridHighlighting()
        updateCurrentHint()
    }
    
    private func handleLetterInput(_ letter: String) {
        guard let (x, y) = selectedCell else { return }
        
        if y < grid.count && x < grid[y].count && !grid[y][x].isBlocked {
            grid[y][x].userInput = letter.uppercased()
            grid[y][x].isError = false
            
            // Advance to next cell
            advanceToNextCell()
            
            // Check completion
            checkCompletion()
        }
        
        recompositionTrigger += 1
    }
    
    private func handleBackspace() {
        guard let (x, y) = selectedCell else { return }
        
        if y < grid.count && x < grid[y].count && !grid[y][x].isBlocked {
            grid[y][x].userInput = ""
            grid[y][x].isError = false
        }
        
        recompositionTrigger += 1
    }
    
    private func handleRevealButton() {
        guard let word = currentWord else {
            print("⚠️ CROSSWORD: No current word selected for reveal")
            return
        }
        
        let userAnswer = getUserAnswerForCurrentWord()
        let isCorrect = userAnswer.uppercased() == word.word.uppercased()
        
        print("🔵 CROSSWORD: Revealing word '\(word.word)' - User had: '\(userAnswer)' - Correct: \(isCorrect)")
        
        if !isCorrect {
            // Fill in the correct word
            fillCurrentWordWithAnswer()
            print("✅ CROSSWORD: Filled correct answer for word: \(word.word)")
        }
        
        // Call the submission callback
        onAnswerSubmitted(userAnswer, isCorrect)
        
        // Check if puzzle is now complete
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.1) {
            checkCompletion()
        }
    }
    
    private func handleClearButton() {
        clearGrid()
        selectedCell = nil
        currentHint = ""
        currentWord = nil
        isCompleted = false
        showIncorrectAnswersDialog = false
        recompositionTrigger += 1
    }
    
    private func advanceToNextCell() {
        guard let (x, y) = selectedCell,
              let word = currentWord else { return }
        
        let nextX = currentDirection == .horizontal ? x + 1 : x
        let nextY = currentDirection == .vertical ? y + 1 : y
        
        let withinWordBounds = isWithinWordBounds(x: nextX, y: nextY, word: word)
        
        if withinWordBounds &&
           nextX < gridWidth && nextY < gridHeight &&
           nextX >= 0 && nextY >= 0 &&
           !grid[nextY][nextX].isBlocked {
            selectedCell = (nextX, nextY)
            updateGridHighlighting()
        }
    }
    
    private func isWithinWordBounds(x: Int, y: Int, word: CrosswordWord) -> Bool {
        switch currentDirection {
        case .horizontal:
            return y == word.startY && x >= word.startX && x < word.startX + word.length
        case .vertical:
            return x == word.startX && y >= word.startY && y < word.startY + word.length
        }
    }
    
    private func updateGridHighlighting() {
        // Clear all highlighting
        for y in 0..<grid.count {
            for x in 0..<grid[y].count {
                grid[y][x].isHighlighted = false
                grid[y][x].isSelected = false
            }
        }
        
        guard let (selectedX, selectedY) = selectedCell else { return }
        
        // Find current word
        currentWord = findWordContainingCell(x: selectedX, y: selectedY, direction: currentDirection)
        
        // Highlight current word
        if let word = currentWord {
            for i in 0..<word.length {
                let x = currentDirection == .horizontal ? word.startX + i : word.startX
                let y = currentDirection == .vertical ? word.startY + i : word.startY
                
                if y < grid.count && x < grid[y].count {
                    grid[y][x].isHighlighted = true
                }
            }
        }
        
        // Mark selected cell
        if selectedY < grid.count && selectedX < grid[selectedY].count {
            grid[selectedY][selectedX].isSelected = true
        }
    }
    
    private func updateCurrentHint() {
        guard let (x, y) = selectedCell else {
            currentHint = ""
            return
        }
        
        if let word = findWordContainingCell(x: x, y: y, direction: currentDirection) {
            currentHint = word.hint
            currentWord = word
        } else {
            currentHint = ""
            currentWord = nil
        }
    }
    
    private func findWordContainingCell(x: Int, y: Int, direction: CrosswordDirection) -> CrosswordWord? {
        return words.first { word in
            let wordDirection: CrosswordDirection = word.direction == "horizontal" ? .horizontal : .vertical
            guard wordDirection == direction else { return false }
            
            switch direction {
            case .horizontal:
                return y == word.startY && x >= word.startX && x < word.startX + word.length
            case .vertical:
                return x == word.startX && y >= word.startY && y < word.startY + word.length
            }
        }
    }
    
    private func getUserAnswerForCurrentWord() -> String {
        guard let word = currentWord else { return "" }
        
        var answer = ""
        for i in 0..<word.length {
            let x = currentDirection == .horizontal ? word.startX + i : word.startX
            let y = currentDirection == .vertical ? word.startY + i : word.startY
            answer += grid[y][x].userInput
        }
        return answer
    }
    
    private func fillCurrentWordWithAnswer() {
        guard let word = currentWord else { return }
        
        for i in 0..<word.length {
            let x = currentDirection == .horizontal ? word.startX + i : word.startX
            let y = currentDirection == .vertical ? word.startY + i : word.startY
            grid[y][x].userInput = String(word.word[word.word.index(word.word.startIndex, offsetBy: i)])
        }
    }
    
    private func checkCompletion() {
        let allFilled = words.allSatisfy { word in
            for i in 0..<word.length {
                let x = word.direction == "horizontal" ? word.startX + i : word.startX
                let y = word.direction == "vertical" ? word.startY + i : word.startY
                
                if grid[y][x].userInput.isEmpty {
                    return false
                }
            }
            return true
        }
        
        if allFilled && !isCompleted {
            isCompleted = true
            let allCorrect = validateAllAnswers()
            
            // Always call the completion callback with the results
            let userAnswers = getUserAnswersForAllWords()
            onAnswerSubmitted(userAnswers, allCorrect)
            
            if allCorrect {
                print("🎉 CROSSWORD: All answers correct! Showing completion dialog")
                showCompletionDialog = true
            } else {
                print("❌ CROSSWORD: Some answers incorrect. Showing error dialog")
                showIncorrectAnswersDialog = true
                isCompleted = false
            }
        }
    }
    
    private func getUserAnswersForAllWords() -> String {
        let answers = words.map { word in
            var answer = ""
            for i in 0..<word.length {
                let x = word.direction == "horizontal" ? word.startX + i : word.startX
                let y = word.direction == "vertical" ? word.startY + i : word.startY
                answer += grid[y][x].userInput
            }
            return "\(word.number):\(answer)"
        }
        return answers.joined(separator: ",")
    }
    
    private func validateAllAnswers() -> Bool {
        return words.allSatisfy { word in
            for i in 0..<word.length {
                let x = word.direction == "horizontal" ? word.startX + i : word.startX
                let y = word.direction == "vertical" ? word.startY + i : word.startY
                
                let expectedLetter = String(word.word[word.word.index(word.word.startIndex, offsetBy: i)])
                let userInput = grid[y][x].userInput
                
                if userInput.uppercased() != expectedLetter.uppercased() {
                    return false
                }
            }
            return true
        }
    }
    
    private func clearGrid() {
        for y in 0..<grid.count {
            for x in 0..<grid[y].count {
                grid[y][x].userInput = ""
                grid[y][x].isError = false
                grid[y][x].isHighlighted = false
                grid[y][x].isSelected = false
            }
        }
    }
}

// MARK: - Crossword Cell View
struct CrosswordCellView: View {
    let cell: CrosswordCell
    let size: CGFloat
    let onTap: () -> Void

    var body: some View {
        Button(action: onTap) {
            ZStack {
                Rectangle()
                    .fill(cell.isBlocked ? Color(red: 0.2, green: 0.2, blue: 0.2) : backgroundColor)
                    .frame(width: size, height: size)
                    .overlay(
                        Rectangle()
                            .stroke(Color.black.opacity(0.3), lineWidth: 1)
                    )

                
                VStack {
                    HStack {
                        if let number = cell.number, !cell.isBlocked {
                            Text("\(number)")
                                .font(.system(size: 10, weight: .bold))
                                .foregroundColor(.black)
                        }
                        Spacer()
                    }
                    Spacer()
                    if !cell.isBlocked {
                        Text(cell.userInput)
                            .font(.system(size: 24, weight: .bold))
                            .foregroundColor(.black)
                    }
                    Spacer()
                }
                .padding(2)
                
            }
        }
        .buttonStyle(PlainButtonStyle())
    }

    private var backgroundColor: Color {
        if cell.isError {
            return .red.opacity(0.3)
        } else if cell.isSelected {
            return Color(red: 0.298, green: 0.686, blue: 0.314)
        } else if cell.isHighlighted {
            return Color(red: 0.506, green: 0.784, blue: 0.518)
        } else {
            return .white
        }
    }
}

// MARK: - Crossword Keyboard
struct CrosswordKeyboardView: View {
    let onLetterClick: (String) -> Void
    let onBackspaceClick: () -> Void
    let recompositionTrigger: Int
    
    private let keyboardRows = [
        ["Q", "W", "E", "R", "T", "Y", "U", "I", "O", "P"],
        ["A", "S", "D", "F", "G", "H", "J", "K", "L"],
        ["Z", "X", "C", "V", "B", "N", "M"]
    ]
    
    var body: some View {
        VStack(spacing: 6) {
            ForEach(Array(keyboardRows.enumerated()), id: \.offset) { rowIndex, row in
                HStack(spacing: 4) {
                    ForEach(row, id: \.self) { letter in
                        KeyboardButton(
                            text: letter,
                            onTap: { onLetterClick(letter) }
                        )
                    }
                    
                    // Add backspace on the last row
                    if rowIndex == keyboardRows.count - 1 {
                        KeyboardButton(
                            text: "⌫",
                            onTap: onBackspaceClick,
                            backgroundColor: Color(red: 0.374, green: 0.620, blue: 0.627)
                        )
                        .frame(width: 50)
                    }
                }
            }
        }
        .padding(8)
        .background(Color(red: 0.529, green: 0.808, blue: 0.922)) // Light blue
    }
}

struct KeyboardButton: View {
    let text: String
    let onTap: () -> Void
    var backgroundColor: Color = .white
    
    var body: some View {
        Button(action: onTap) {
            Text(text)
                .font(.system(size: 16, weight: .bold))
                .foregroundColor(.black)
                .frame(height: 48)
                .frame(maxWidth: .infinity)
                .background(backgroundColor)
                .cornerRadius(6)
        }
        .buttonStyle(PlainButtonStyle())
    }
}

// MARK: - Dialog Views
struct HintsDialogView: View {
    let words: [CrosswordWord]
    let onDismiss: () -> Void
    
    var body: some View {
        NavigationView {
            ScrollView {
                LazyVStack(spacing: 12) {
                    ForEach(words, id: \.number) { word in
                        VStack(alignment: .leading, spacing: 8) {
                            Text("\(word.number) \(word.direction == "horizontal" ? "Across" : "Down")")
                                .font(.caption)
                                .fontWeight(.bold)
                                .foregroundColor(Color(red: 0.0, green: 0.737, blue: 0.831))
                            
                            Text(word.hint)
                                .font(.subheadline)
                                .foregroundColor(.black)
                        }
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .padding(12)
                        .background(Color(.systemGray6))
                        .cornerRadius(8)
                    }
                }
                .padding()
            }
            .navigationTitle("All Hints")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .navigationBarTrailing) {
                    Button("Close", action: onDismiss)
                }
            }
        }
    }
}

struct CompletionDialogView: View {
    let time: String
    let onShare: () -> Void
    let onReset: () -> Void
    let onNext: () -> Void
    let onDismiss: () -> Void
    
    var body: some View {
        VStack(spacing: 20) {
            Text("🎉")
                .font(.system(size: 60))
            
            Text("Puzzle solved in \(time)")
                .font(.title2)
                .fontWeight(.bold)
                .foregroundColor(.white)
                .multilineTextAlignment(.center)
            
            Text("\"Eureka!\"")
                .font(.headline)
                .foregroundColor(.white)
            
            HStack(spacing: 16) {
                Button(action: onShare) {
                    HStack {
                        Image(systemName: "square.and.arrow.up")
                        Text("Share")
                    }
                    .foregroundColor(.white)
                    .padding()
                    .overlay(
                        RoundedRectangle(cornerRadius: 8)
                            .stroke(Color.white, lineWidth: 2)
                    )
                }
                
                Button(action: onReset) {
                    HStack {
                        Image(systemName: "arrow.clockwise")
                        Text("Reset")
                    }
                    .foregroundColor(.white)
                    .padding()
                    .overlay(
                        RoundedRectangle(cornerRadius: 8)
                            .stroke(Color.white, lineWidth: 2)
                    )
                }
            }
            
            Button(action: {
                print("🔵 CROSSWORD: Next Puzzle button tapped")
                onDismiss() // Close dialog first
                DispatchQueue.main.asyncAfter(deadline: .now() + 0.2) {
                    onNext() // Then navigate to next puzzle
                }
            }) {
                Text("Next Puzzle")
                    .fontWeight(.semibold)
                    .foregroundColor(Color(red: 0.0, green: 0.737, blue: 0.831))
                    .frame(maxWidth: .infinity)
                    .padding()
                    .background(Color.white)
                    .cornerRadius(8)
            }
        }
        .padding(24)
        .background(Color(red: 0.0, green: 0.737, blue: 0.831))
        .cornerRadius(16)
        .padding()
    }
}

struct IncorrectAnswersDialogView: View {
    let onDismiss: () -> Void
    let onTryAgain: () -> Void
    
    var body: some View {
        VStack(spacing: 20) {
            Text("Some answers are incorrect")
                .font(.headline)
                .fontWeight(.bold)
                .multilineTextAlignment(.center)
            
            Text("Try again or use the REVEAL button to fix incorrect answers.")
                .font(.subheadline)
                .foregroundColor(.gray)
                .multilineTextAlignment(.center)
            
            Button(action: onTryAgain) {
                Text("Try Again")
                    .fontWeight(.semibold)
                    .foregroundColor(.white)
                    .frame(maxWidth: .infinity)
                    .padding()
                    .background(Color(red: 0.0, green: 0.737, blue: 0.831))
                    .cornerRadius(8)
            }
        }
        .padding(24)
        .background(Color.white)
        .cornerRadius(16)
        .padding()
    }
}
