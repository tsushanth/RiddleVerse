//
//  WordSnakePuzzleTutorialManager.swift
//  PuzzleForge
//
//  Created by Assistant on [Current Date]
//

import SwiftUI

// MARK: - Tutorial Manager
class WordSnakePuzzleTutorialManager: ObservableObject {
    func getTutorialSteps() -> [TutorialStep] {
        return [
            TutorialStep(
                title: "Welcome to Word Snake! 🐍",
                description: "Learn how to find hidden words that snake through the letter grid in any direction - up, down, left, right, and diagonally!",
                targetComponent: "grid",
                id: "" // Informational
            ),
            TutorialStep(
                title: "The Letter Grid 📝",
                description: "Above you see a grid of letters. Hidden words snake through these letters, connecting adjacent cells in any direction.",
                targetComponent: "grid",
                id: "" // Informational
            ),
            TutorialStep(
                title: "Start Your Selection 🎯",
                description: "To find a word, tap any letter to start selecting. Try tapping on a letter now!",
                targetComponent: "grid",
                id: "select_first_letter" // Interactive
            ),
            TutorialStep(
                title: "Continue the Path 🔗",
                description: "Great! Now tap adjacent letters to continue your word. Letters can connect horizontally, vertically, or diagonally.",
                targetComponent: "grid",
                id: "select_second_letter" // Interactive
            ),
            TutorialStep(
                title: "Build Your Word ✨",
                description: "Keep tapping connected letters to spell out a word. You can see your current selection highlighted in blue.",
                targetComponent: "grid",
                id: "build_word" // Interactive
            ),
            TutorialStep(
                title: "Submit Your Find 🚀",
                description: "When you've spelled a word, tap SUBMIT to check if it matches one of the hidden words. Try submitting your selection!",
                targetComponent: "buttons",
                id: "submit_word" // Interactive
            ),
            TutorialStep(
                title: "Use the Word List 📋",
                description: "Below the grid, you'll see clues for all the hidden words. This helps you know what to look for!",
                targetComponent: "clues",
                id: "" // Informational
            ),
            TutorialStep(
                title: "Clear and Try Again 🔄",
                description: "Made a mistake? Use CLEAR to start over, or tap on your selection to modify it. You can also tap SHOW to reveal a word if you're stuck.",
                targetComponent: "buttons",
                id: "" // Interactive
            ),
            TutorialStep(
                title: "Words Can Overlap 🌟",
                description: "Remember: words can share letters! The same letter might be part of multiple words, creating interesting crossing patterns.",
                targetComponent: "grid",
                id: "" // Informational
            ),
            TutorialStep(
                title: "You're Ready to Snake! 🎉",
                description: "Perfect! Now you know how to find word snakes. Use the clues, think creatively about paths, and find all the hidden words!",
                targetComponent: "grid",
                id: "" // Informational
            )
        ]
    }
}

// MARK: - Main Tutorial View
struct WordSnakePuzzleTutorialView: View {
    let onTutorialComplete: () -> Void
    let onTutorialSkipped: () -> Void
    let onBack: () -> Void
    
    @StateObject private var tutorialManager = WordSnakePuzzleTutorialManager()
    @State private var currentStepIndex = 0
    @State private var tutorialState = TutorialState.active
    @State private var sampleWordSnakeData = createSampleWordSnakeData()
    
    private var steps: [TutorialStep] {
        tutorialManager.getTutorialSteps()
    }
    
    private var currentStep: TutorialStep? {
        guard currentStepIndex < steps.count else { return nil }
        return steps[currentStepIndex]
    }
    
    var body: some View {
        ZStack {
            // Tutorial version of the word snake screen
            TutorialWordSnakeContent(
                currentStep: currentStep,
                sampleWordSnakeData: sampleWordSnakeData,
                onTutorialAction: handleTutorialAction,
                onBack: onBack
            )
            
            // Tutorial overlay - positioned to avoid blocking grid and buttons
            if tutorialState == .active && currentStepIndex < steps.count {
                let step = steps[currentStepIndex]
                
                if step.id.isEmpty {
                    // Informational step
                    WordSnakeSmartTutorialOverlay(
                        currentStep: step,
                        totalSteps: steps.count,
                        currentStepNumber: currentStepIndex + 1,
                        onNext: advanceStep,
                        onSkip: skipTutorial
                    )
                } else {
                    // Interactive step
                    WordSnakeMinimalInteractiveOverlay(
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

// MARK: - Tutorial Word Snake Content
struct TutorialWordSnakeContent: View {
    let currentStep: TutorialStep?
    let sampleWordSnakeData: String
    let onTutorialAction: (String) -> Void
    let onBack: () -> Void
    
    @State private var wordSnakeData: TutorialWordSnakeData?
    @State private var cellGrid: [[TutorialWordSnakeCell]] = []
    @State private var selectedCells: [TutorialWSGridPosition] = []
    @State private var currentSelectionWord: String = ""
    @State private var foundWords: Set<String> = []
    @State private var foundPaths: [TutorialWordSnakePath] = []
    @State private var timeRemaining: String = "4:00"
    @State private var recompositionTrigger: Int = 0
    
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
                        tutorialTopBarView
                        
                        // Progress Card
                        tutorialProgressCardView(data: data)
                        
                        // Grid Container
                        tutorialGridContainerView(data: data, geometry: geometry)
                        
                        // Clues Section
                        tutorialCluesSection(data: data)
                        
                        // Control Buttons
                        tutorialControlButtonsView
                    }
                } else {
                    // Loading state
                    VStack(spacing: 20) {
                        ProgressView()
                            .scaleEffect(1.5)
                            .tint(.white)
                        
                        Text("Loading tutorial...")
                            .foregroundColor(.white)
                            .font(.headline)
                    }
                }
            }
        }
        .navigationBarHidden(true)
        .onAppear {
            setupTutorialPuzzle()
        }
    }
    
    // MARK: - UI Components
    private var tutorialTopBarView: some View {
        HStack {
            Button(action: onBack) {
                Image(systemName: "arrow.left")
                    .font(.title2)
                    .foregroundColor(.white)
            }
            
            Spacer()
            
            Text("🐍 Snake Tutorial")
                .font(.headline)
                .fontWeight(.bold)
                .foregroundColor(.white)
            
            Spacer()
            
            Text(timeRemaining)
                .font(.title2)
                .fontWeight(.bold)
                .foregroundColor(.white)
            
            Button(action: { onTutorialAction("help_clicked") }) {
                Image(systemName: "questionmark.circle")
                    .font(.title2)
                    .foregroundColor(.white)
            }
        }
        .padding(.horizontal)
        .padding(.vertical, 8)
    }
    
    private func tutorialProgressCardView(data: TutorialWordSnakeData) -> some View {
        VStack(spacing: 8) {
            HStack {
                Text("Words: \(foundWords.count)/\(data.words.count)")
                    .font(.subheadline)
                    .fontWeight(.semibold)
                
                Spacer()
                
                ProgressView(value: Double(foundWords.count), total: Double(data.words.count))
                    .frame(width: 100)
                    .tint(.green)
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
    
    private func tutorialGridContainerView(data: TutorialWordSnakeData, geometry: GeometryProxy) -> some View {
        let availableWidth = geometry.size.width - 32
        let availableHeight = geometry.size.height * 0.4
        let calculatedCellSize = min(availableWidth / CGFloat(data.gridSize), availableHeight / CGFloat(data.gridSize))
        let finalCellSize = min(calculatedCellSize, 45)
        
        return VStack {
            ZStack {
                // Snake Paths
                Canvas { context, size in
                    // Draw found paths
                    for snakePath in foundPaths {
                        drawTutorialSnakePath(
                            context: context,
                            path: snakePath,
                            cellSize: finalCellSize,
                            gridSize: data.gridSize,
                            canvasSize: size
                        )
                    }
                    
                    // Draw current selection path
                    if selectedCells.count >= 2 {
                        drawTutorialSelectionPath(
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
                .allowsHitTesting(false)
                
                // Grid
                VStack(spacing: gridSpacing) {
                    ForEach(0..<data.gridSize, id: \.self) { row in
                        HStack(spacing: gridSpacing) {
                            ForEach(0..<data.gridSize, id: \.self) { col in
                                if row < cellGrid.count && col < cellGrid[row].count {
                                    TutorialWordSnakeCellView(
                                        cell: cellGrid[row][col],
                                        size: finalCellSize,
                                        isSelected: selectedCells.contains(TutorialWSGridPosition(row: row, col: col)),
                                        selectionOrder: selectedCells.firstIndex(of: TutorialWSGridPosition(row: row, col: col)) ?? -1,
                                        onTap: {
                                            handleTutorialCellTap(TutorialWSGridPosition(row: row, col: col))
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
        .padding()
        .modifier(TutorialHighlight(isHighlighted: currentStep?.targetComponent == "grid"))
    }
    
    private func tutorialCluesSection(data: TutorialWordSnakeData) -> some View {
        VStack(alignment: .leading, spacing: 12) {
            Text("Find these snake words:")
                .font(.headline)
                .fontWeight(.bold)
                .foregroundColor(.white)
                .padding(.horizontal)
            
            ScrollView {
                LazyVStack(spacing: 8) {
                    ForEach(data.words) { word in
                        TutorialWordSnakeClueView(
                            word: word,
                            isFound: foundWords.contains(word.word),
                            color: Color(hex: word.color) ?? .blue
                        )
                    }
                }
                .padding(.horizontal)
            }
        }
        .frame(maxHeight: 150)
        .background(Color.white.opacity(0.1))
        .cornerRadius(12)
        .padding(.horizontal)
        .modifier(TutorialHighlight(isHighlighted: currentStep?.targetComponent == "clues"))
    }
    
    private var tutorialControlButtonsView: some View {
        HStack(spacing: 16) {
            Button("CLEAR") {
                clearTutorialSelection()
                onTutorialAction("clear_selection")
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
                submitTutorialSelection()
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
        .modifier(TutorialHighlight(isHighlighted: currentStep?.targetComponent == "buttons"))
    }
    
    // MARK: - Setup and Logic
    private func setupTutorialPuzzle() {
        guard let data = parseTutorialWordSnakeData(from: sampleWordSnakeData) else {
            print("❌ Failed to parse tutorial word snake data")
            return
        }
        
        self.wordSnakeData = data
        
        // Initialize cell grid
        cellGrid = []
        for row in 0..<data.gridSize {
            var cellRow: [TutorialWordSnakeCell] = []
            for col in 0..<data.gridSize {
                let letter = data.grid[row][col]
                cellRow.append(TutorialWordSnakeCell(row: row, col: col, letter: letter))
            }
            cellGrid.append(cellRow)
        }
        
        print("✅ Tutorial word snake puzzle setup complete")
    }
    
    private func handleTutorialCellTap(_ position: TutorialWSGridPosition) {
        guard position.row < cellGrid.count && position.col < cellGrid[position.row].count else {
            return
        }
        
        if let existingIndex = selectedCells.firstIndex(of: position) {
            // Cell already selected - remove it and subsequent cells
            selectedCells = Array(selectedCells[0..<existingIndex])
        } else {
            // Add cell to selection
            if selectedCells.isEmpty {
                selectedCells.append(position)
                onTutorialAction("select_first_letter")
            } else {
                // Check if adjacent to last selected cell
                let lastCell = selectedCells.last!
                if areAdjacent(lastCell, position) {
                    selectedCells.append(position)
                    
                    if selectedCells.count == 2 {
                        onTutorialAction("select_second_letter")
                    } else if selectedCells.count > 2 {
                        onTutorialAction("build_word")
                    }
                } else {
                    // Not adjacent - start new selection
                    selectedCells = [position]
                    onTutorialAction("select_first_letter")
                }
            }
        }
        
        updateTutorialSelectionStates()
        updateTutorialCurrentSelectionWord()
        
        recompositionTrigger += 1
    }
    
    private func areAdjacent(_ pos1: TutorialWSGridPosition, _ pos2: TutorialWSGridPosition) -> Bool {
        let rowDiff = abs(pos1.row - pos2.row)
        let colDiff = abs(pos1.col - pos2.col)
        return rowDiff <= 1 && colDiff <= 1 && !(rowDiff == 0 && colDiff == 0)
    }
    
    private func submitTutorialSelection() {
        guard selectedCells.count >= 2,
              let data = wordSnakeData else { return }
        
        let selectedWord = selectedCells.map { pos in
            cellGrid[pos.row][pos.col].letter
        }.joined().uppercased()
        
        // Check if it matches any tutorial word
        for word in data.words {
            if !foundWords.contains(word.word) &&
               (selectedWord == word.word.uppercased() || selectedWord == String(word.word.uppercased().reversed())) {
                
                // Found a word!
                foundWords.insert(word.word)
                
                let pathColor = Color(hex: word.color) ?? .blue
                let newPath = TutorialWordSnakePath(
                    word: word.word,
                    path: selectedCells,
                    color: pathColor
                )
                foundPaths.append(newPath)
                
                // Mark cells as found
                for position in selectedCells {
                    cellGrid[position.row][position.col].isFound = true
                    cellGrid[position.row][position.col].foundWordId = word.word
                }
                
                clearTutorialSelection()
                onTutorialAction("submit_word")
                return
            }
        }
        
        // Word not found - clear selection
        clearTutorialSelection()
        onTutorialAction("submit_word")
    }
    
    private func clearTutorialSelection() {
        selectedCells.removeAll()
        currentSelectionWord = ""
        updateTutorialSelectionStates()
    }
    
    private func updateTutorialSelectionStates() {
        for row in 0..<cellGrid.count {
            for col in 0..<cellGrid[row].count {
                cellGrid[row][col].isSelected = false
                cellGrid[row][col].selectionOrder = -1
            }
        }
        
        for (index, position) in selectedCells.enumerated() {
            cellGrid[position.row][position.col].isSelected = true
            cellGrid[position.row][position.col].selectionOrder = index
        }
    }
    
    private func updateTutorialCurrentSelectionWord() {
        currentSelectionWord = selectedCells.map { pos in
            cellGrid[pos.row][pos.col].letter
        }.joined()
    }
    
    // MARK: - Drawing Functions
    private func drawTutorialSnakePath(context: GraphicsContext, path: TutorialWordSnakePath, cellSize: CGFloat, gridSize: Int, canvasSize: CGSize) {
        guard path.path.count >= 2 else { return }
        
        let strokeWidth: CGFloat = 8
        let positions = path.path.map { pos in
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
                with: .color(path.color),
                lineWidth: strokeWidth
            )
        }
        
        // Draw endpoint circles
        context.fill(
            Path(ellipseIn: CGRect(
                x: positions.first!.x - strokeWidth / 2,
                y: positions.first!.y - strokeWidth / 2,
                width: strokeWidth,
                height: strokeWidth
            )),
            with: .color(path.color)
        )
        
        context.fill(
            Path(ellipseIn: CGRect(
                x: positions.last!.x - strokeWidth / 2,
                y: positions.last!.y - strokeWidth / 2,
                width: strokeWidth,
                height: strokeWidth
            )),
            with: .color(path.color)
        )
    }
    
    private func drawTutorialSelectionPath(context: GraphicsContext, path: [TutorialWSGridPosition], cellSize: CGFloat, gridSize: Int, canvasSize: CGSize) {
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

// MARK: - Tutorial Data Models
struct TutorialWordSnakeCell: Identifiable {
    let id = UUID()
    let row: Int
    let col: Int
    let letter: String
    var isHighlighted: Bool = false
    var isFound: Bool = false
    var foundWordId: String? = nil
    var isSelected: Bool = false
    var selectionOrder: Int = -1
}

struct TutorialWordSnakeWord: Identifiable {
    let id = UUID()
    let word: String
    let clue: String
    let path: [TutorialWSGridPosition]
    let color: String
}

struct TutorialWSGridPosition: Equatable, Hashable {
    let row: Int
    let col: Int
}

struct TutorialWordSnakePath {
    let word: String
    let path: [TutorialWSGridPosition]
    let color: Color
}

struct TutorialWordSnakeData {
    let grid: [[String]]
    let words: [TutorialWordSnakeWord]
    let gridSize: Int
}

// MARK: - Tutorial Cell View
struct TutorialWordSnakeCellView: View {
    let cell: TutorialWordSnakeCell
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
        } else {
            return Color.white
        }
    }
    
    private var borderColor: Color {
        if isSelected {
            return Color.blue
        } else if cell.isFound {
            return Color.green
        } else {
            return Color.gray.opacity(0.3)
        }
    }
    
    private var borderWidth: CGFloat {
        isSelected ? 3 : 2
    }
    
    private var textColor: Color {
        (cell.isFound || isSelected) ? .white : .black
    }
}

// MARK: - Tutorial Clue View
struct TutorialWordSnakeClueView: View {
    let word: TutorialWordSnakeWord
    let isFound: Bool
    let color: Color
    
    var body: some View {
        HStack(spacing: 12) {
            Circle()
                .fill(isFound ? color : Color.gray.opacity(0.3))
                .frame(width: 16, height: 16)
                .overlay(
                    Image(systemName: isFound ? "checkmark" : "")
                        .foregroundColor(.white)
                        .font(.system(size: 10, weight: .bold))
                )
            
            VStack(alignment: .leading, spacing: 2) {
                Text(word.word)
                    .font(.subheadline)
                    .fontWeight(.semibold)
                    .foregroundColor(isFound ? .gray : .primary)
                    .strikethrough(isFound)
                
                Text(word.clue)
                    .font(.caption)
                    .foregroundColor(isFound ? .gray.opacity(0.7) : .gray)
                    .opacity(isFound ? 0.6 : 1.0)
            }
            
            Spacer()
            
            if isFound {
                Image(systemName: "checkmark")
                    .font(.headline)
                    .foregroundColor(.green)
                    .fontWeight(.bold)
            }
        }
        .padding(.vertical, 4)
        .background(isFound ? color.opacity(0.1) : Color.clear)
        .cornerRadius(8)
        .padding(.horizontal, 4)
    }
}

// MARK: - Word Snake Specific Tutorial Overlay Views
struct WordSnakeSmartTutorialOverlay: View {
    let currentStep: TutorialStep
    let totalSteps: Int
    let currentStepNumber: Int
    let onNext: () -> Void
    let onSkip: () -> Void
    
    var body: some View {
        GeometryReader { geometry in
            VStack {
                Spacer()
                    .frame(height: 60) // Push content down from top
                
                // Position overlay based on target component to avoid blocking grid
                if currentStep.targetComponent == "grid" {
                    // Show at bottom when grid is target to avoid blocking it
                    Spacer()
                    tutorialCard
                        .padding(.bottom, 20)
                } else if currentStep.targetComponent == "buttons" {
                    // Show at top when buttons are target to avoid blocking them
                    tutorialCard
                        .padding(.top, 20)
                    Spacer()
                } else if currentStep.targetComponent == "clues" {
                    // Show at top when clues are target
                    tutorialCard
                        .padding(.top, 20)
                    Spacer()
                } else {
                    // Default: show at bottom
                    Spacer()
                    tutorialCard
                        .padding(.bottom, 20)
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
                        .foregroundColor(Color(red: 0.17, green: 0.24, blue: 0.31))
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
                .fill(Color.black.opacity(0.85))
        )
        .padding(.horizontal, 20)
    }
}

struct WordSnakeMinimalInteractiveOverlay: View {
    let currentStep: TutorialStep
    let totalSteps: Int
    let currentStepNumber: Int
    let onNext: () -> Void
    let onSkip: () -> Void
    
    var body: some View {
        GeometryReader { geometry in
            VStack {
                Spacer()
                    .frame(height: 60) // Push content down from top
                
                // Smart positioning to avoid blocking interactive elements
                if currentStep.targetComponent == "grid" {
                    // Show at very bottom when grid is target
                    Spacer()
                    tutorialCard
                        .padding(.bottom, 10)
                } else if currentStep.targetComponent == "buttons" {
                    // Show at top when buttons are target
                    tutorialCard
                        .padding(.top, 10)
                    Spacer()
                } else {
                    // Default: show at bottom but above buttons
                    Spacer()
                    tutorialCard
                        .padding(.bottom, 80) // Leave space for buttons
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
        .padding(20)
        .background(
            RoundedRectangle(cornerRadius: 16)
                .fill(Color.black.opacity(0.85))
        )
        .padding(.horizontal, 20)
    }
}
func createSampleWordSnakeData() -> String {
    return """
    {
        "gridSize": 4,
        "grid": [
            ["C", "A", "T", "S"],
            ["O", "R", "E", "U"],
            ["W", "D", "O", "N"],
            ["S", "U", "M", "E"]
        ],
        "words": [
            {
                "word": "CAT",
                "clue": "Furry pet that meows",
                "path": [{"row": 0, "col": 0}, {"row": 0, "col": 1}, {"row": 0, "col": 2}],
                "color": "FF6B6B"
            },
            {
                "word": "SUN",
                "clue": "Bright star in the sky",
                "path": [{"row": 0, "col": 3}, {"row": 1, "col": 3}, {"row": 2, "col": 3}],
                "color": "4ECDC4"
            },
            {
                "word": "COW",
                "clue": "Farm animal that gives milk",
                "path": [{"row": 1, "col": 0}, {"row": 2, "col": 0}, {"row": 3, "col": 0}],
                "color": "45B7D1"
            }
        ]
    }
    """
}

func parseTutorialWordSnakeData(from jsonString: String) -> TutorialWordSnakeData? {
    guard let data = jsonString.data(using: .utf8),
          let json = try? JSONSerialization.jsonObject(with: data) as? [String: Any] else {
        return nil
    }
    
    guard let gridArray = json["grid"] as? [[String]],
          let wordsArray = json["words"] as? [[String: Any]],
          let size = json["gridSize"] as? Int else {
        return nil
    }
    
    let words = wordsArray.compactMap { wordDict -> TutorialWordSnakeWord? in
        guard let word = wordDict["word"] as? String,
              let clue = wordDict["clue"] as? String,
              let pathArray = wordDict["path"] as? [[String: Int]],
              let colorString = wordDict["color"] as? String else {
            return nil
        }
        
        let path = pathArray.compactMap { posDict -> TutorialWSGridPosition? in
            guard let row = posDict["row"],
                  let col = posDict["col"] else { return nil }
            return TutorialWSGridPosition(row: row, col: col)
        }
        
        return TutorialWordSnakeWord(
            word: word,
            clue: clue,
            path: path,
            color: colorString
        )
    }
    
    return TutorialWordSnakeData(
        grid: gridArray,
        words: words,
        gridSize: size
    )
}
