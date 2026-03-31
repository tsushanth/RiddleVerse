import SwiftUI



struct MathCrosswordGridPosition: Hashable, Equatable {
    let row: Int
    let col: Int
}

struct MathCrosswordPuzzleView: View {
    let difficulty: String
    let timer: String
    let hearts: Int
    let level: String
    let puzzleData: String
    let onSubmitAnswer: (Bool) -> Void
    let fetchNextPuzzle: (Int) -> Void
    let onBack: () -> Void
    
    @State private var currentGrid: [[MathCrosswordCell]] = []
    @State private var selectedCell: MathCrosswordGridPosition? = nil
    @State private var availableNumbers: [String: Int] = [:]
    @State private var score = 0
    @State private var showFeedback = false
    @State private var feedbackMessage = ""
    @State private var isComplete = false
    @State private var timeLeft: Int
    @State private var isTimerRunning = true
    
    init(difficulty: String, timer: String, hearts: Int = 3, level: String, puzzleData: String, onSubmitAnswer: @escaping (Bool) -> Void, fetchNextPuzzle: @escaping (Int) -> Void, onBack: @escaping () -> Void) {
        self.difficulty = difficulty
        self.timer = timer
        self.hearts = hearts
        self.level = level
        self.puzzleData = puzzleData
        self.onSubmitAnswer = onSubmitAnswer
        self.fetchNextPuzzle = fetchNextPuzzle
        self.onBack = onBack
        self._timeLeft = State(initialValue: Self.parseTimer(timer))
    }
    
    var body: some View {
        VStack(spacing: 0) {
            // Header
            headerView
            
            Spacer().frame(height: 16)
            
            // Level indicator
            Text(level)
                .font(.system(size: 14, weight: .medium))
                .foregroundColor(.secondary)
            
            Spacer().frame(height: 24)
            
            // Instructions
            instructionsCard
            
            Spacer().frame(height: 16)
            
            // Math Crossword Grid
            if !currentGrid.isEmpty {
                ScrollView([.horizontal, .vertical]) {
                    crosswordGridView
                        .padding()
                }
                .frame(maxHeight: 400)
            }
            
            Spacer().frame(height: 24)
            
            // Available Numbers Grid
            if !availableNumbers.isEmpty {
                VStack(alignment: .leading, spacing: 12) {
                    Text("Select a number:")
                        .font(.system(size: 16, weight: .medium))
                        .padding(.horizontal)
                    
                    numberSelectionGrid
                        .padding(.horizontal)
                }
            }
            
            Spacer()
            
            // Control Buttons
            controlButtons
                .padding(.horizontal)
                .padding(.bottom)
        }
        .background(Color(UIColor.systemGroupedBackground))
        .navigationBarHidden(true)
        .onAppear {
            parsePuzzleData()
        }
        .onReceive(Timer.publish(every: 1, on: .main, in: .common).autoconnect()) { _ in
            if isTimerRunning && timeLeft > 0 {
                timeLeft -= 1
            } else if timeLeft == 0 && isTimerRunning {
                isTimerRunning = false
                showFeedback = true
                feedbackMessage = "Time's up! ⏰"
                onSubmitAnswer(false)
            }
        }
        .alert("Result", isPresented: $showFeedback) {
            Button(isComplete ? "Continue" : "OK") {
                if isComplete {
                    fetchNextPuzzle(score)
                }
            }
        } message: {
            VStack {
                Text(feedbackMessage)
                if isComplete {
                    Text("Score: \(score)")
                        .font(.headline)
                        .foregroundColor(.green)
                }
            }
        }
    }
    
    private var headerView: some View {
        HStack {
            Button(action: onBack) {
                Image(systemName: "chevron.left")
                    .font(.system(size: 24, weight: .bold))
                    .foregroundColor(.primary)
            }
            
            Spacer()
            
            VStack {
                Text(difficulty)
                    .font(.system(size: 16, weight: .medium))
                    .foregroundColor(.secondary)
                
                Text(formatTime(timeLeft))
                    .font(.system(size: 20, weight: .bold))
                    .foregroundColor(timeLeft <= 30 ? .red : .primary)
            }
            
            Spacer()
            
            HStack(spacing: 4) {
                ForEach(0..<hearts, id: \.self) { _ in
                    Text("❤️")
                        .font(.system(size: 16))
                }
            }
        }
        .padding(.horizontal)
        .padding(.top, 12)
    }
    
    private var instructionsCard: some View {
        Text("Complete the math equations by placing the missing numbers")
            .font(.system(size: 14))
            .foregroundColor(.blue)
            .padding(12)
            .frame(maxWidth: .infinity)
            .background(Color.blue.opacity(0.1))
            .cornerRadius(8)
            .padding(.horizontal)
    }
    
    private var crosswordGridView: some View {
        let visibleCells = getVisibleCells()
        let (minRow, maxRow, minCol, maxCol) = getBounds(visibleCells: visibleCells)
        
        return VStack(spacing: 2) {
            ForEach(minRow...maxRow, id: \.self) { row in
                HStack(spacing: 2) {
                    ForEach(minCol...maxCol, id: \.self) { col in
                        let position = MathCrosswordGridPosition(row: row, col: col)
                        if let cell = visibleCells[position] {
                            MathCrosswordCellView(
                                cell: cell,
                                isSelected: selectedCell == position
                            ) {
                                if !cell.isFixed && cell.cellType != .blocked {
                                    selectedCell = position
                                }
                            }
                        } else {
                            Rectangle()
                                .fill(Color.clear)
                                .frame(width: 48, height: 48)
                        }
                    }
                }
            }
        }
    }
    
    private var numberSelectionGrid: some View {
        let sortedNumbers = availableNumbers.keys.sorted { Int($0) ?? 0 < Int($1) ?? 0 }
        let chunkedNumbers = sortedNumbers.chunked(into: 7)
        
        return VStack(spacing: 8) {
            ForEach(0..<chunkedNumbers.count, id: \.self) { rowIndex in
                HStack(spacing: 8) {
                    ForEach(chunkedNumbers[rowIndex], id: \.self) { number in
                        let count = availableNumbers[number] ?? 0
                        NumberSelectionButton(
                            number: number,
                            count: count,
                            isAvailable: count > 0
                        ) {
                            if count > 0 {
                                selectNumber(number)
                            }
                        }
                    }
                    
                    if chunkedNumbers[rowIndex].count < 7 {
                        ForEach(0..<(7 - chunkedNumbers[rowIndex].count), id: \.self) { _ in
                            Rectangle()
                                .fill(Color.clear)
                                .frame(width: 48, height: 48)
                        }
                    }
                }
            }
        }
    }
    
    private var controlButtons: some View {
        HStack(spacing: 12) {
            Button("Clear") {
                clearSelectedCell()
            }
            .padding()
            .frame(maxWidth: .infinity)
            .background(Color.gray.opacity(0.2))
            .foregroundColor(.primary)
            .cornerRadius(8)
            
            Button("Check") {
                checkSolution()
            }
            .padding()
            .frame(maxWidth: .infinity)
            .background(checkAllCellsFilled() ? Color.blue : Color.gray.opacity(0.3))
            .foregroundColor(.white)
            .cornerRadius(8)
            .disabled(!checkAllCellsFilled() || isComplete)
        }
    }
    
    // MARK: - Helper Methods
    
    private func parsePuzzleData() {
        guard let data = puzzleData.data(using: .utf8),
              let json = try? JSONSerialization.jsonObject(with: data) as? [String: Any] else {
            return
        }
        
        if let gridSize = json["gridSize"] as? Int,
           let gridJson = json["grid"] as? [[Any]],
           let numberCountsJson = json["numberCounts"] as? [String: Int] {
            
            var grid: [[MathCrosswordCell]] = []
            
            for (rowIndex, rowData) in gridJson.enumerated() {
                var rowCells: [MathCrosswordCell] = []
                
                for (colIndex, cellData) in rowData.enumerated() {
                    if let cellDict = cellData as? [String: Any] {
                        let value = cellDict["value"] as? String ?? ""
                        let isFixed = cellDict["isFixed"] as? Bool ?? false
                        let cellTypeString = cellDict["cellType"] as? String ?? "EMPTY"
                        let cellType = MathCrosswordCellType(rawValue: cellTypeString) ?? .empty
                        
                        rowCells.append(MathCrosswordCell(
                            row: rowIndex,
                            col: colIndex,
                            value: value,
                            isFixed: isFixed,
                            cellType: cellType
                        ))
                    }
                }
                grid.append(rowCells)
            }
            
            self.currentGrid = grid
            self.availableNumbers = numberCountsJson
        }
    }
    
    private func selectNumber(_ number: String) {
        guard let position = selectedCell,
              position.row < currentGrid.count,
              position.col < currentGrid[position.row].count else { return }
        
        let cell = currentGrid[position.row][position.col]
        
        if !cell.isFixed && cell.cellType != .blocked && availableNumbers[number, default: 0] > 0 {
            // Remove old value if exists
            let oldValue = cell.value
            if !oldValue.isEmpty {
                availableNumbers[oldValue, default: 0] += 1
            }
            
            // Place new value
            currentGrid[position.row][position.col] = MathCrosswordCell(
                row: cell.row,
                col: cell.col,
                value: number,
                isFixed: cell.isFixed,
                cellType: cell.cellType
            )
            
            // Update available numbers
            availableNumbers[number, default: 0] -= 1
            
            // Clear selection
            selectedCell = nil
            
            // Check completion
            if validateAllEquations() {
                isComplete = true
                isTimerRunning = false
                let timeBonus = timeLeft * 10
                score = 1000 + timeBonus
                showFeedback = true
                feedbackMessage = "Excellent! All equations solved! 🎉"
                onSubmitAnswer(true)
            }
        }
    }
    
    private func clearSelectedCell() {
        guard let position = selectedCell,
              position.row < currentGrid.count,
              position.col < currentGrid[position.row].count else { return }
        
        let cell = currentGrid[position.row][position.col]
        
        if !cell.isFixed && !cell.value.isEmpty {
            let oldValue = cell.value
            
            // Return number to available pool
            availableNumbers[oldValue, default: 0] += 1
            
            // Clear cell
            currentGrid[position.row][position.col] = MathCrosswordCell(
                row: cell.row,
                col: cell.col,
                value: "",
                isFixed: cell.isFixed,
                cellType: cell.cellType
            )
        }
    }
    
    private func checkSolution() {
        let isValid = validateAllEquations()
        if isValid {
            isComplete = true
            isTimerRunning = false
            let timeBonus = timeLeft * 10
            score = 1000 + timeBonus
            showFeedback = true
            feedbackMessage = "Perfect! All equations are correct! 🎉"
            onSubmitAnswer(true)
        } else {
            showFeedback = true
            feedbackMessage = "Some equations are incorrect. Keep trying! 🤔"
        }
    }
    
    private func validateAllEquations() -> Bool {
        let equations = findEquations()
        return equations.allSatisfy(validateEquation)
    }
    
    private func findEquations() -> [[MathCrosswordCell]] {
        var equations: [[MathCrosswordCell]] = []
        
        // Find horizontal equations
        for row in currentGrid {
            var currentSequence: [MathCrosswordCell] = []
            for cell in row {
                if cell.cellType != .blocked {
                    currentSequence.append(cell)
                } else {
                    if currentSequence.count == 5 {
                        equations.append(currentSequence)
                    }
                    currentSequence.removeAll()
                }
            }
            if currentSequence.count == 5 {
                equations.append(currentSequence)
            }
        }
        
        // Find vertical equations
        for col in 0..<(currentGrid.first?.count ?? 0) {
            var currentSequence: [MathCrosswordCell] = []
            for row in 0..<currentGrid.count {
                let cell = currentGrid[row][col]
                if cell.cellType != .blocked {
                    currentSequence.append(cell)
                } else {
                    if currentSequence.count == 5 {
                        equations.append(currentSequence)
                    }
                    currentSequence.removeAll()
                }
            }
            if currentSequence.count == 5 {
                equations.append(currentSequence)
            }
        }
        
        return equations
    }
    
    private func validateEquation(_ cells: [MathCrosswordCell]) -> Bool {
        guard cells.count == 5 else { return false }
        
        let operand1Str = cells[0].value.trimmingCharacters(in: .whitespaces)
        let mathOperator = cells[1].value.trimmingCharacters(in: .whitespaces)
        let operand2Str = cells[2].value.trimmingCharacters(in: .whitespaces)
        let equals = cells[3].value.trimmingCharacters(in: .whitespaces)
        let resultStr = cells[4].value.trimmingCharacters(in: .whitespaces)
        
        guard !operand1Str.isEmpty && !mathOperator.isEmpty && !operand2Str.isEmpty &&
              !equals.isEmpty && !resultStr.isEmpty else { return false }
        
        guard equals == "=" else { return false }
        guard ["+", "-", "*", "/"].contains(mathOperator) else { return false }
        
        guard let operand1 = Int(operand1Str),
              let operand2 = Int(operand2Str),
              let result = Int(resultStr) else { return false }
        
        let calculatedResult: Int
        switch mathOperator {
        case "+": calculatedResult = operand1 + operand2
        case "-": calculatedResult = operand1 - operand2
        case "*": calculatedResult = operand1 * operand2
        case "/":
            if operand2 != 0 && operand1 % operand2 == 0 {
                calculatedResult = operand1 / operand2
            } else {
                return false
            }
        default: return false
        }
        
        return calculatedResult == result
    }
    
    private func checkAllCellsFilled() -> Bool {
        for row in currentGrid {
            for cell in row {
                if cell.cellType != .blocked && !cell.isFixed && cell.value.isEmpty {
                    return false
                }
            }
        }
        return true
    }
    
    private func getVisibleCells() -> [MathCrosswordGridPosition: MathCrosswordCell] {
        var visibleCells: [MathCrosswordGridPosition: MathCrosswordCell] = [:]
        
        for (rowIndex, row) in currentGrid.enumerated() {
            for (colIndex, cell) in row.enumerated() {
                if cell.cellType != .blocked {
                    visibleCells[MathCrosswordGridPosition(row: rowIndex, col: colIndex)] = cell
                }
            }
        }
        
        return visibleCells
    }
    
    private func getBounds(visibleCells: [MathCrosswordGridPosition: MathCrosswordCell]) -> (Int, Int, Int, Int) {
        let positions = Array(visibleCells.keys)
        guard !positions.isEmpty else { return (0, 0, 0, 0) }
        
        let minRow = positions.map(\.row).min() ?? 0
        let maxRow = positions.map(\.row).max() ?? 0
        let minCol = positions.map(\.col).min() ?? 0
        let maxCol = positions.map(\.col).max() ?? 0
        
        return (minRow, maxRow, minCol, maxCol)
    }
    
    private static func parseTimer(_ timer: String) -> Int {
        let parts = timer.split(separator: ":")
        if parts.count == 2,
           let minutes = Int(parts[0]),
           let seconds = Int(parts[1]) {
            return minutes * 60 + seconds
        }
        return 120 // Default 2 minutes
    }
    
    private func formatTime(_ seconds: Int) -> String {
        let minutes = seconds / 60
        let remainingSeconds = seconds % 60
        return String(format: "%02d:%02d", minutes, remainingSeconds)
    }
}

struct MathCrosswordCellView: View {
    let cell: MathCrosswordCell
    let isSelected: Bool
    let onTap: () -> Void
    
    @Environment(\.colorScheme) var colorScheme
    
    var body: some View {
        Button(action: onTap) {
            Text(cell.value)
                .font(.system(size: 18, weight: cell.isFixed ? .bold : .regular))
                .foregroundColor(foregroundColor)
                .frame(width: 48, height: 48)
                .background(backgroundColor)
                .overlay(
                    RoundedRectangle(cornerRadius: 4)
                        .stroke(borderColor, lineWidth: borderWidth)
                )
                .cornerRadius(4)
        }
        .disabled(cell.cellType == .blocked)
    }
    
    private var backgroundColor: Color {
        switch true {
        case cell.cellType == .blocked:
            return .clear
        case isSelected:
            return Color.blue.opacity(0.3)
        case cell.isFixed:
            return colorScheme == .dark ? Color.gray.opacity(0.3) : Color.gray.opacity(0.2)
        default:
            // Use adaptive colors for better contrast
            return colorScheme == .dark ? Color(UIColor.systemBackground) : .white
        }
    }
    
    private var foregroundColor: Color {
        if cell.isFixed {
            return Color.green.opacity(0.8)
        } else {
            // Use high contrast text color that adapts to background
            return colorScheme == .dark ? .white : .black
        }
    }
    
    private var borderColor: Color {
        if isSelected {
            return .blue
        } else {
            return colorScheme == .dark ? Color.gray.opacity(0.8) : Color.gray.opacity(0.5)
        }
    }
    
    private var borderWidth: CGFloat {
        isSelected ? 2 : 1
    }
}

struct NumberSelectionButton: View {
    let number: String
    let count: Int
    let isAvailable: Bool
    let onTap: () -> Void
    
    var body: some View {
        Button(action: onTap) {
            VStack(spacing: 2) {
                Text(number)
                    .font(.system(size: 18, weight: .bold))
                    .foregroundColor(isAvailable ? .white : .gray)
                
                if count > 1 {
                    Text("×\(count)")
                        .font(.system(size: 10))
                        .foregroundColor(isAvailable ? .white.opacity(0.8) : .gray.opacity(0.8))
                }
            }
            .frame(width: 48, height: 48)
            .background(isAvailable ? Color.green : Color.gray.opacity(0.3))
            .overlay(
                RoundedRectangle(cornerRadius: 8)
                    .stroke(isAvailable ? Color.green.opacity(0.8) : Color.gray.opacity(0.5), lineWidth: 2)
            )
            .cornerRadius(8)
        }
        .disabled(!isAvailable)
    }
}

