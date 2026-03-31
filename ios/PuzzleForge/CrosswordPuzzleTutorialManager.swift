//
//  CrosswordPuzzleTutorialView.swift
//  PuzzleForge
//
//  Created by Assistant on [Current Date]
//

import SwiftUI

// MARK: - Tutorial Models
struct TutorialStep {
    let title: String
    let description: String
    let targetComponent: String
    let id: String // Empty for informational steps, filled for interactive steps
}

enum TutorialState {
    case active, completed, skipped
}

// MARK: - Cell Position Helper
struct CellPosition: Equatable {
    let x: Int
    let y: Int
    
    init(_ x: Int, _ y: Int) {
        self.x = x
        self.y = y
    }
    
    var tuple: (Int, Int) {
        return (x, y)
    }
}

// MARK: - Tutorial Manager
class CrosswordPuzzleTutorialManager: ObservableObject {
    func getTutorialSteps() -> [TutorialStep] {
        return [
            TutorialStep(
                title: "Welcome to Crossword Puzzles! 🧩",
                description: "Learn how to solve crossword puzzles by filling in words based on clues. Let's start with the basics!",
                targetComponent: "grid",
                id: "" // Informational
            ),
            TutorialStep(
                title: "The Crossword Grid 📝",
                description: "This is your crossword grid. White squares need letters, black squares are blocked. Numbers show where words begin.",
                targetComponent: "grid",
                id: "" // Informational
            ),
            TutorialStep(
                title: "Tap a Cell to Start ✨",
                description: "Tap on any numbered white cell to select it and see the clue. Try tapping a cell!",
                targetComponent: "grid",
                id: "select_cell" // Interactive
            ),
            TutorialStep(
                title: "Reading Clues 💡",
                description: "Great! When you select a cell, the clue appears above the grid. This shows you what word to enter.",
                targetComponent: "hint",
                id: "" // Informational
            ),
            TutorialStep(
                title: "Use the Keyboard ⌨️",
                description: "Type letters using the keyboard below. Each letter will fill the current cell and move to the next one.",
                targetComponent: "keyboard",
                id: "type_letter" // Interactive
            ),
            TutorialStep(
                title: "Direction Matters ↔️",
                description: "Words can go across (horizontal) or down (vertical). Tap the same cell again to switch directions.",
                targetComponent: "grid",
                id: "switch_direction" // Interactive
            ),
            TutorialStep(
                title: "Completing Words ✅",
                description: "Keep typing to complete the word. Try finishing the current word by entering all letters!",
                targetComponent: "keyboard",
                id: "complete_word" // Interactive
            ),
            TutorialStep(
                title: "Reveal the Answer 👀",
                description: "Tap the REVEAL button to check your answer for the selected word. If it's wrong, the correct word will be shown and filled in automatically.",
                targetComponent: "buttons",
                id: "check_answers" // Interactive
            ),
            TutorialStep(
                title: "Get Help When Stuck 🆘",
                description: "Tap the help button (?) to see all clues, or use CLEAR to start over if needed.",
                targetComponent: "timer",
                id: "" // Informational
            ),
            TutorialStep(
                title: "You're Ready! 🎉",
                description: "Perfect! You now know how to solve crossword puzzles. Fill all words correctly to complete the puzzle!",
                targetComponent: "grid",
                id: "" // Informational
            )
        ]
    }
}

// MARK: - Main Tutorial View
struct CrosswordPuzzleTutorialView: View {
    let onTutorialComplete: () -> Void
    let onTutorialSkipped: () -> Void
    let onBack: () -> Void
    
    @StateObject private var tutorialManager = CrosswordPuzzleTutorialManager()
    @State private var currentStepIndex = 0
    @State private var tutorialState = TutorialState.active
    @State private var sampleCrosswordData = createSampleCrosswordData()
    
    private var steps: [TutorialStep] {
        tutorialManager.getTutorialSteps()
    }
    
    private var currentStep: TutorialStep? {
        guard currentStepIndex < steps.count else { return nil }
        return steps[currentStepIndex]
    }
    
    var body: some View {
        ZStack {
            // Tutorial version of the crossword screen
            TutorialCrosswordContent(
                currentStep: currentStep,
                sampleCrosswordData: sampleCrosswordData,
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

// MARK: - Tutorial Crossword Content
struct TutorialCrosswordContent: View {
    let currentStep: TutorialStep?
    let sampleCrosswordData: String
    let onTutorialAction: (String) -> Void
    let onBack: () -> Void
    
    @State private var grid: [[TutorialCrosswordCell]] = []
    @State private var words: [TutorialCrosswordWord] = []
    @State private var selectedCell: CellPosition? = nil
    @State private var currentDirection: CrosswordDirection = .horizontal
    @State private var currentHint: String = ""
    @State private var currentWord: TutorialCrosswordWord? = nil
    @State private var isCompleted: Bool = false
    @State private var showErrors: Bool = false
    @State private var recompositionTrigger: Int = 0
    
    private let gridWidth = 3
    private let gridHeight = 3
    
    var body: some View {
        ZStack {
            // Cyan background matching main screen
            Color(red: 0.0, green: 0.737, blue: 0.831)
                .ignoresSafeArea()
            
            VStack(spacing: 0) {
                Spacer().frame(height: 32)
                
                // Top Bar with highlighting
                topBar
                
                // Current Hint Display with highlighting
                if !currentHint.isEmpty {
                    currentHintCard
                }
                
                Spacer().frame(height: 20)
                
                // Crossword Grid with highlighting
                Spacer()
                crosswordGrid
                Spacer()
                
                // Bottom action buttons with highlighting
                actionButtons
                
                // Crossword Keyboard with highlighting
                crosswordKeyboard
            }
        }
        .onAppear {
            setupTutorialPuzzle()
        }
        .onChange(of: selectedCell) { _ in
            updateGridHighlighting()
            updateCurrentHint()
        }
        .onChange(of: currentDirection) { _ in
            updateGridHighlighting()
            updateCurrentHint()
        }
    }
    
    // MARK: - UI Components
    private var topBar: some View {
        HStack {
            Button(action: onBack) {
                Image(systemName: "arrow.left")
                    .font(.title2)
                    .foregroundColor(.white)
                    .frame(width: 40, height: 40)
            }
            
            Spacer()
            
            Text("2:00")
                .font(.title2)
                .fontWeight(.bold)
                .foregroundColor(.white)
            
            Spacer()
            
            Button(action: { onTutorialAction("help_clicked") }) {
                Image(systemName: "questionmark")
                    .font(.title2)
                    .foregroundColor(.white)
                    .frame(width: 40, height: 40)
            }
        }
        .padding(.horizontal, 16)
        .modifier(TutorialHighlight(isHighlighted: currentStep?.targetComponent == "timer"))
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
                .lineLimit(nil)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(12)
        .background(
            RoundedRectangle(cornerRadius: 8)
                .fill(Color.white.opacity(0.9))
        )
        .padding(.horizontal, 16)
        .padding(.vertical, 8)
        .modifier(TutorialHighlight(isHighlighted: currentStep?.targetComponent == "hint"))
    }
    
    private var crosswordGrid: some View {
        VStack(spacing: 0) {
            ForEach(0..<gridHeight, id: \.self) { y in
                HStack(spacing: 0) {
                    ForEach(0..<gridWidth, id: \.self) { x in
                        if y < grid.count && x < grid[y].count {
                            TutorialCrosswordCellView(
                                cell: grid[y][x],
                                size: 44, // fixed square size
                                onTap: {
                                    handleCellClick(x: x, y: y)
                                }
                            )
                        }
                    }
                }
            }
        }
        .frame(width: CGFloat(gridWidth) * 44, height: CGFloat(gridHeight) * 44)
    }
    
    private var actionButtons: some View {
        HStack(spacing: 16) {
            // Check button
            Button(action: handleCheckButton) {
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
        .modifier(TutorialHighlight(isHighlighted: currentStep?.targetComponent == "buttons"))
    }
    
    private var crosswordKeyboard: some View {
        TutorialCrosswordKeyboardView(
            onLetterClick: handleLetterInput,
            onBackspaceClick: handleBackspace,
            isHighlighted: currentStep?.targetComponent == "keyboard",
            recompositionTrigger: recompositionTrigger
        )
    }
    
    // MARK: - Setup and Logic
    private func setupTutorialPuzzle() {
        guard let puzzleData = parseTutorialCrosswordData(from: sampleCrosswordData) else {
            print("❌ Failed to parse tutorial crossword data")
            return
        }
        
        self.grid = puzzleData.grid
        self.words = puzzleData.words
        
        print("✅ Tutorial crossword puzzle setup complete")
    }
    
    private func handleCellClick(x: Int, y: Int) {
        if let current = selectedCell, current.x == x && current.y == y {
            currentDirection = currentDirection == .horizontal ? .vertical : .horizontal
            onTutorialAction("switch_direction")
        } else {
            selectedCell = CellPosition(x, y)
            currentDirection = getDefaultDirection(x: x, y: y)
            onTutorialAction("select_cell")
        }
    }
    
    private func handleLetterInput(_ letter: String) {
        guard let selectedPos = selectedCell else { return }
        
        fillCellAndAdvance(
            x: selectedPos.x,
            y: selectedPos.y,
            letter: letter,
            direction: currentDirection
        )
        
        recompositionTrigger += 1
        onTutorialAction("type_letter")
        
        checkCompletion()
    }
    
    private func handleBackspace() {
        guard let selectedPos = selectedCell else { return }
        
        if selectedPos.y < grid.count && selectedPos.x < grid[selectedPos.y].count && !grid[selectedPos.y][selectedPos.x].isBlocked {
            grid[selectedPos.y][selectedPos.x].userInput = ""
            grid[selectedPos.y][selectedPos.x].isError = false
        }
        
        recompositionTrigger += 1
        onTutorialAction("backspace")
    }
    
    private func handleCheckButton() {
        let allCorrect = validateAllAnswers()
        if !allCorrect {
            showErrors = true
            markErrors()
        } else {
            isCompleted = true
        }
        onTutorialAction("check_answers")
    }
    
    private func handleClearButton() {
        clearGrid()
        showErrors = false
        isCompleted = false
        selectedCell = nil
        currentHint = ""
        onTutorialAction("clear_grid")
    }
    
    private func fillCellAndAdvance(x: Int, y: Int, letter: String, direction: CrosswordDirection) {
        guard y < grid.count && x < grid[y].count && !grid[y][x].isBlocked else { return }
        
        grid[y][x].userInput = letter.uppercased()
        grid[y][x].isError = false
        
        // Advance to next cell
        let nextX = direction == .horizontal ? x + 1 : x
        let nextY = direction == .vertical ? y + 1 : y
        
        if let word = currentWord,
           isWithinWordBounds(x: nextX, y: nextY, word: word) &&
           nextX < gridWidth && nextY < gridHeight &&
           !grid[nextY][nextX].isBlocked {
            selectedCell = CellPosition(nextX, nextY)
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
        
        guard let selectedPos = selectedCell else { return }
        
        // Find current word
        currentWord = findWordContainingCell(x: selectedPos.x, y: selectedPos.y, direction: currentDirection)
        
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
        if selectedPos.y < grid.count && selectedPos.x < grid[selectedPos.y].count {
            grid[selectedPos.y][selectedPos.x].isSelected = true
        }
    }
    
    private func updateCurrentHint() {
        guard let selectedPos = selectedCell else {
            currentHint = ""
            return
        }
        
        if let word = findWordContainingCell(x: selectedPos.x, y: selectedPos.y, direction: currentDirection) {
            currentHint = word.hint
            currentWord = word
        } else {
            currentHint = ""
            currentWord = nil
        }
    }
    
    private func getDefaultDirection(x: Int, y: Int) -> CrosswordDirection {
        // Check if there's a horizontal word at this position
        let hasHorizontal = words.contains { word in
            word.direction == "horizontal" &&
            y == word.startY &&
            x >= word.startX &&
            x < word.startX + word.length
        }
        
        // Check if there's a vertical word at this position
        let hasVertical = words.contains { word in
            word.direction == "vertical" &&
            x == word.startX &&
            y >= word.startY &&
            y < word.startY + word.length
        }
        
        return hasHorizontal ? .horizontal : (hasVertical ? .vertical : .horizontal)
    }
    
    private func findWordContainingCell(x: Int, y: Int, direction: CrosswordDirection) -> TutorialCrosswordWord? {
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
    
    private func isWithinWordBounds(x: Int, y: Int, word: TutorialCrosswordWord) -> Bool {
        switch currentDirection {
        case .horizontal:
            return y == word.startY && x >= word.startX && x < word.startX + word.length
        case .vertical:
            return x == word.startX && y >= word.startY && y < word.startY + word.length
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
            onTutorialAction("complete_word")
        }
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
    
    private func markErrors() {
        for word in words {
            for i in 0..<word.length {
                let x = word.direction == "horizontal" ? word.startX + i : word.startX
                let y = word.direction == "vertical" ? word.startY + i : word.startY
                
                let expectedLetter = String(word.word[word.word.index(word.word.startIndex, offsetBy: i)])
                let userInput = grid[y][x].userInput
                
                if !userInput.isEmpty && userInput.uppercased() != expectedLetter.uppercased() {
                    grid[y][x].isError = true
                }
            }
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

// MARK: - Tutorial Models
struct TutorialCrosswordCell {
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

struct TutorialCrosswordWord {
    let word: String
    let hint: String
    let startX: Int
    let startY: Int
    let direction: String
    let length: Int
    let number: Int
}

// MARK: - Tutorial Cell View
struct TutorialCrosswordCellView: View {
    let cell: TutorialCrosswordCell
    let size: CGFloat
    let onTap: () -> Void
    
    var body: some View {
        Button(action: onTap) {
            ZStack {
                Rectangle()
                    .fill(backgroundColor)
                    .frame(width: size, height: size)
                    .border(Color.black, width: 1)

                if !cell.isBlocked {
                    VStack(alignment: .leading, spacing: 0) {
                        HStack {
                            if let number = cell.number {
                                Text("\(number)")
                                    .font(.system(size: 8))
                                    .foregroundColor(.black)
                            }
                            Spacer()
                        }
                        Spacer()
                        Text(cell.userInput)
                            .font(.system(size: 16, weight: .bold))
                            .foregroundColor(.black)
                        Spacer()
                    }
                    .padding(4)
                }
            }
            .frame(width: size, height: size)
        }
        .buttonStyle(PlainButtonStyle())
    }
    
    private var backgroundColor: Color {
        if cell.isBlocked {
            return .clear
        } else if cell.isError {
            return .red.opacity(0.3)
        } else if cell.isSelected {
            return Color(red: 0.298, green: 0.686, blue: 0.314) // Green
        } else if cell.isHighlighted {
            return Color(red: 0.506, green: 0.784, blue: 0.518) // Light green
        } else {
            return .white
        }
    }
}

// MARK: - Tutorial Keyboard
struct TutorialCrosswordKeyboardView: View {
    let onLetterClick: (String) -> Void
    let onBackspaceClick: () -> Void
    let isHighlighted: Bool
    let recompositionTrigger: Int
    
    @State private var pulseAnimation = false
    
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
                        TutorialKeyboardButton(
                            text: letter,
                            onTap: { onLetterClick(letter) },
                            isHighlighted: isHighlighted
                        )
                    }
                    
                    if rowIndex == keyboardRows.count - 1 {
                        TutorialKeyboardButton(
                            text: "⌫",
                            onTap: onBackspaceClick,
                            backgroundColor: Color(red: 0.374, green: 0.620, blue: 0.627),
                            isHighlighted: isHighlighted
                        )
                        .frame(width: 50)
                    }
                }
            }
        }
        .padding(8)
        .background(Color(red: 0.529, green: 0.808, blue: 0.922))
        .modifier(TutorialHighlight(isHighlighted: isHighlighted))
        .onAppear {
            if isHighlighted {
                withAnimation(.easeInOut(duration: 1.0).repeatForever(autoreverses: true)) {
                    pulseAnimation = true
                }
            }
        }
    }
}

struct TutorialKeyboardButton: View {
    let text: String
    let onTap: () -> Void
    var backgroundColor: Color = .white
    let isHighlighted: Bool
    
    @State private var scale = 1.0
    
    var body: some View {
        Button(action: onTap) {
            Text(text)
                .font(.system(size: 14, weight: .bold))
                .foregroundColor(.black)
                .frame(height: 40)
                .frame(maxWidth: .infinity)
                .background(backgroundColor)
                .cornerRadius(6)
                .scaleEffect(scale)
        }
        .buttonStyle(PlainButtonStyle())
        .onAppear {
            if isHighlighted {
                withAnimation(.spring(dampingFraction: 0.6).repeatForever(autoreverses: true)) {
                    scale = 1.05
                }
            }
        }
    }
}

// MARK: - Tutorial Overlay Views
struct SmartTutorialOverlay: View {
    let currentStep: TutorialStep
    let totalSteps: Int
    let currentStepNumber: Int
    let onNext: () -> Void
    let onSkip: () -> Void
    
    var body: some View {
        GeometryReader { geometry in
            VStack {
                if currentStep.targetComponent == "buttons" {
                    tutorialCard
                        .padding(.top, 50)
                    Spacer()
                } else if currentStep.targetComponent == "keyboard" {
                    tutorialCard
                    Spacer()
                } else {
                    Spacer()
                    tutorialCard
                        .padding(.bottom, 120)
                }
            }
        }
        .background(Color.clear)
    }
    
    private var tutorialCard: some View {
        VStack(spacing: 16) {
            // Progress indicator
            HStack {
                ForEach(1...totalSteps, id: \.self) { step in
                    Circle()
                        .fill(step <= currentStepNumber ? Color.white : Color.white.opacity(0.3))
                        .frame(width: 8, height: 8)
                }
            }

            VStack(spacing: 12) {
                Text(currentStep.title)
                    .font(.headline)
                    .fontWeight(.bold)
                    .foregroundColor(.white)
                    .multilineTextAlignment(.center)

                Text(currentStep.description)
                    .font(.subheadline)
                    .foregroundColor(.white.opacity(0.9))
                    .multilineTextAlignment(.center)

                Text("👆 Try it now!")
                    .font(.caption)
                    .fontWeight(.medium)
                    .foregroundColor(.yellow)
                    .padding(.top, 4)
            }

            HStack(spacing: 16) {
                Button(action: onSkip) {
                    Text("Skip Tutorial")
                        .foregroundColor(.white.opacity(0.7))
                        .padding(.horizontal, 20)
                        .padding(.vertical, 8)
                        .overlay(
                            RoundedRectangle(cornerRadius: 20)
                                .stroke(Color.white.opacity(0.3), lineWidth: 1)
                        )
                }

                Button(action: onNext) {
                    Text("Next")
                        .fontWeight(.semibold)
                        .foregroundColor(Color(red: 0.0, green: 0.737, blue: 0.831))
                        .padding(.horizontal, 24)
                        .padding(.vertical, 12)
                        .background(Color.white)
                        .cornerRadius(25)
                }
            }
        }
        .padding(24)
        .background(
            RoundedRectangle(cornerRadius: 16)
                .fill(Color.black.opacity(0.8))
        )
        .padding(.horizontal, 20)
    }

}

struct MinimalInteractiveOverlay: View {
    let currentStep: TutorialStep
    let totalSteps: Int
    let currentStepNumber: Int
    let onNext: () -> Void
    let onSkip: () -> Void
    
    var body: some View {
        GeometryReader { geometry in
            VStack {
                // Position overlay based on target component
                if currentStep.targetComponent == "keyboard" {
                    // Show at top when keyboard is target
                    tutorialCard
                    Spacer()
                } else {
                    Spacer()
                    tutorialCard
                        .padding(.bottom, 120) // Leave space for keyboard
                }
            }
        }
        .background(Color.clear)
    }
    
    private var tutorialCard: some View {
        VStack(spacing: 16) {
            // Progress indicator
            HStack {
                ForEach(1...totalSteps, id: \.self) { step in
                    Circle()
                        .fill(step <= currentStepNumber ? Color.white : Color.white.opacity(0.3))
                        .frame(width: 8, height: 8)
                }
            }
            
            VStack(spacing: 12) {
                Text(currentStep.title)
                    .font(.headline)
                    .fontWeight(.bold)
                    .foregroundColor(.white)
                    .multilineTextAlignment(.center)
                
                Text(currentStep.description)
                    .font(.subheadline)
                    .foregroundColor(.white.opacity(0.9))
                    .multilineTextAlignment(.center)
                    .lineLimit(nil)
                
                Text("👆 Try it now!")
                    .font(.caption)
                    .fontWeight(.medium)
                    .foregroundColor(.yellow)
                    .padding(.top, 4)
            }
            
            Button(action: onSkip) {
                Text("Skip Tutorial")
                    .foregroundColor(.white.opacity(0.7))
                    .padding(.horizontal, 20)
                    .padding(.vertical, 8)
                    .overlay(
                        RoundedRectangle(cornerRadius: 20)
                            .stroke(Color.white.opacity(0.3), lineWidth: 1)
                    )
            }
        }
        .padding(24)
        .background(
            RoundedRectangle(cornerRadius: 16)
                .fill(Color.black.opacity(0.8))
        )
        .padding(.horizontal, 20)
    }
}

// MARK: - Tutorial Highlight Modifier
struct TutorialHighlight: ViewModifier {
    let isHighlighted: Bool
    @State private var pulseAnimation = false
    
    func body(content: Content) -> some View {
        content
            .overlay(
                RoundedRectangle(cornerRadius: 8)
                    .stroke(
                        Color(red: 0.482, green: 0.122, blue: 0.635).opacity(isHighlighted ? (pulseAnimation ? 0.8 : 0.4) : 0),
                        lineWidth: 3
                    )
                    .animation(.easeInOut(duration: 1.0).repeatForever(autoreverses: true), value: pulseAnimation)
            )
            .onAppear {
                if isHighlighted {
                    pulseAnimation = true
                }
            }
            .onChange(of: isHighlighted) { highlighted in
                pulseAnimation = highlighted
            }
    }
}

// MARK: - Data Creation and Parsing
func createSampleCrosswordData() -> String {
    return """
    {
        "width": 3,
        "height": 3,
        "words": [
            {
                "word": "CAT",
                "hint": "Furry pet that meows",
                "startX": 0,
                "startY": 0,
                "direction": "horizontal",
                "length": 3
            },
            {
                "word": "COD",
                "hint": "Type of fish",
                "startX": 0,
                "startY": 0,
                "direction": "vertical",
                "length": 3
            },
            {
                "word": "DOG",
                "hint": "Loyal pet that barks",
                "startX": 0,
                "startY": 2,
                "direction": "horizontal",
                "length": 3
            }
        ]
    }
    """
}

struct TutorialCrosswordPuzzleData: Codable {
    let width: Int
    let height: Int
    let words: [TutorialCrosswordWordData]
    
    struct TutorialCrosswordWordData: Codable {
        let word: String
        let hint: String
        let startX: Int
        let startY: Int
        let direction: String
        let length: Int
    }
}

func parseTutorialCrosswordData(from jsonString: String) -> (grid: [[TutorialCrosswordCell]], words: [TutorialCrosswordWord])? {
    guard let data = jsonString.data(using: .utf8),
          let puzzleData = try? JSONDecoder().decode(TutorialCrosswordPuzzleData.self, from: data) else {
        print("❌ Failed to decode tutorial crossword JSON")
        return nil
    }
    
    let gridWidth = puzzleData.width
    let gridHeight = puzzleData.height
    
    // Create empty grid - initialize all as blocked
    var tempGrid: [[TutorialCrosswordCell]] = []
    for y in 0..<gridHeight {
        var gridRow: [TutorialCrosswordCell] = []
        for x in 0..<gridWidth {
            let cell = TutorialCrosswordCell(
                x: x,
                y: y,
                letter: "",
                isBlocked: true // Start with all blocked
            )
            gridRow.append(cell)
        }
        tempGrid.append(gridRow)
    }
    
    // Create words and mark only necessary cells as unblocked
    var tempWords: [TutorialCrosswordWord] = []
    for (index, wordData) in puzzleData.words.enumerated() {
        let word = TutorialCrosswordWord(
            word: wordData.word,
            hint: wordData.hint,
            startX: wordData.startX,
            startY: wordData.startY,
            direction: wordData.direction,
            length: wordData.length,
            number: index + 1
        )
        tempWords.append(word)
        
        // Mark cells for this word as unblocked
        for i in 0..<word.length {
            let x = word.direction == "horizontal" ? word.startX + i : word.startX
            let y = word.direction == "vertical" ? word.startY + i : word.startY
            
            if y < tempGrid.count && x < tempGrid[y].count {
                let expectedLetter = String(word.word[word.word.index(word.word.startIndex, offsetBy: i)])
                
                // If this cell already exists (intersection), keep existing number
                let existingNumber = tempGrid[y][x].number
                let cellNumber = (i == 0 && existingNumber == nil) ? word.number : existingNumber
                
                tempGrid[y][x] = TutorialCrosswordCell(
                    x: x,
                    y: y,
                    letter: expectedLetter,
                    isBlocked: false,
                    number: cellNumber
                )
            }
        }
    }
    
    return (grid: tempGrid, words: tempWords)
}

