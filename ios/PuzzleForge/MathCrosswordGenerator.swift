//
//  MathCrosswordEquation.swift
//  PuzzleForge
//
//  Created by Sushanth Tiruvaipati on 7/27/25.
//

import Foundation

// MARK: - Data Models
struct MathCrosswordEquation {
    let startRow: Int
    let startCol: Int
    let isHorizontal: Bool
    let operand1: Int
    let mathOperator: String
    let operand2: Int
    let result: Int
    let missingIndex: Int // 0 = operand1, 2 = operand2, 4 = result
    
    func getValues() -> [String] {
        return [
            operand1.description,    // Index 0
            mathOperator,            // Index 1 (always operator)
            operand2.description,    // Index 2
            "=",                    // Index 3 (always equals)
            result.description      // Index 4
        ]
    }
    
    func getFixedFlags() -> [Bool] {
        return [
            missingIndex != 0, // operand1 is fixed if not missing
            true,              // operator is always fixed (index 1)
            missingIndex != 2, // operand2 is fixed if not missing
            true,              // equals is always fixed (index 3)
            missingIndex != 4  // result is fixed if not missing
        ]
    }
    
    func getMissingValue() -> String {
        switch missingIndex {
        case 0: return operand1.description
        case 2: return operand2.description
        case 4: return result.description
        default: return ""
        }
    }
    
    func getCells() -> [(Int, Int)] {
        return (0...4).map { index in
            if isHorizontal {
                return (startRow, startCol + index)
            } else {
                return (startRow + index, startCol)
            }
        }
    }
    
    func isValid() -> Bool {
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
        return calculatedResult == result && result > 0 && operand1 > 0 && operand2 > 0
    }
}

struct MathCrosswordBounds {
    var minRow: Int = Int.max
    var maxRow: Int = Int.min
    var minCol: Int = Int.max
    var maxCol: Int = Int.min
    
    mutating func update(with equation: MathCrosswordEquation) {
        let cells = equation.getCells()
        for (row, col) in cells {
            minRow = min(minRow, row)
            maxRow = max(maxRow, row)
            minCol = min(minCol, col)
            maxCol = max(maxCol, col)
        }
    }
    
    func getWidth() -> Int {
        return maxCol == Int.min ? 0 : maxCol - minCol + 1
    }
    
    func getHeight() -> Int {
        return maxRow == Int.min ? 0 : maxRow - minRow + 1
    }
    
    func isWithinLimits(maxWidth: Int = 12, maxHeight: Int = 12) -> Bool {
        return getWidth() <= maxWidth && getHeight() <= maxHeight
    }
}

enum MathCrosswordCellType: String, CaseIterable {
    case blocked = "BLOCKED"
    case number = "NUMBER"
    case mathOperator = "OPERATOR"
    case equals = "EQUALS"
    case empty = "EMPTY"
}

struct MathCrosswordCell {
    let row: Int
    let col: Int
    let value: String
    let isFixed: Bool
    let cellType: MathCrosswordCellType
    let completeValue: String // Store the actual value even if hidden
    
    init(row: Int, col: Int, value: String = "", isFixed: Bool = false, cellType: MathCrosswordCellType = .empty, completeValue: String? = nil) {
        self.row = row
        self.col = col
        self.value = value
        self.isFixed = isFixed
        self.cellType = cellType
        self.completeValue = completeValue ?? value
    }
}

// Smart removal helper struct
struct CellEquationMapping {
    let row: Int
    let col: Int
    let value: String
    let valueIndex: Int
    let cellKey: String
    let equationIndex: Int
    let isIntersection: Bool
    
    init(row: Int, col: Int, value: String, valueIndex: Int, equationIndex: Int, isIntersection: Bool = false) {
        self.row = row
        self.col = col
        self.value = value
        self.valueIndex = valueIndex
        self.equationIndex = equationIndex
        self.isIntersection = isIntersection
        self.cellKey = "\(row),\(col)"
    }
}

struct MathCrosswordPuzzleData {
    let grid: [[MathCrosswordCell]]
    let size: Int
    let equations: [MathCrosswordEquation]
    let numberCounts: [String: Int]
    let difficulty: String
}

// MARK: - Math Crossword Generator
class MathCrosswordGenerator {
    
    static func generatePuzzle(difficulty: String) -> (String, String) {
        let maxEquations = getMaxEquations(for: difficulty)
        let maxValue = getMaxValue(for: difficulty)
        
        let equations = generateCrosswordWithIntersections(maxEquations: maxEquations, maxValue: maxValue)
        let gridSize = calculateRequiredGridSize(equations: equations)
        let grid = createGridWithSmartRemoval(gridSize: gridSize, equations: equations)
        
        // Collect missing numbers after smart removal
        var numberCounts: [String: Int] = [:]
        var allMissingCells: [(Int, Int, String)] = []
        
        for row in grid {
            for cell in row {
                if cell.cellType == .number && !cell.isFixed && !cell.completeValue.isEmpty {
                    let value = cell.completeValue
                    numberCounts[value, default: 0] += 1
                    allMissingCells.append((cell.row, cell.col, value))
                }
            }
        }
        
        let puzzleData = createPuzzleDataJSON(
            gridSize: gridSize,
            difficulty: difficulty,
            equations: equations,
            grid: grid,
            numberCounts: numberCounts,
            totalMissingNumbers: allMissingCells.count
        )
        
        let answerData = createAnswerDataJSON(
            grid: grid,
            equations: equations
        )
        
        print("📊 Generated puzzle: \(equations.count) equations, \(allMissingCells.count) missing numbers")
        
        return (puzzleData, answerData)
    }
    
    private static func createGridWithSmartRemoval(gridSize: Int, equations: [MathCrosswordEquation]) -> [[MathCrosswordCell]] {
        if equations.isEmpty {
            return (0..<gridSize).map { row in
                (0..<gridSize).map { col in
                    MathCrosswordCell(row: row, col: col, cellType: .blocked)
                }
            }
        }
        
        var bounds = MathCrosswordBounds()
        for equation in equations {
            bounds.update(with: equation)
        }
        
        let totalWidth = bounds.getWidth()
        let totalHeight = bounds.getHeight()
        let rowOffset = -bounds.minRow + (gridSize - totalHeight) / 2
        let colOffset = -bounds.minCol + (gridSize - totalWidth) / 2
        
        // First pass: Create grid with original missing values
        var grid = (0..<gridSize).map { row in
            (0..<gridSize).map { col in
                MathCrosswordCell(row: row, col: col, cellType: .blocked)
            }
        }
        
        for equation in equations {
            let values = equation.getValues()
            let fixedFlags = equation.getFixedFlags()
            
            for (index, value) in values.enumerated() {
                let originalRow = equation.isHorizontal ? equation.startRow : equation.startRow + index
                let originalCol = equation.isHorizontal ? equation.startCol + index : equation.startCol
                
                let row = originalRow + rowOffset
                let col = originalCol + colOffset
                
                if row >= 1 && row < gridSize - 1 && col >= 1 && col < gridSize - 1 {
                    let cellType: MathCrosswordCellType
                    switch value {
                    case "+", "-", "*", "/": cellType = .mathOperator
                    case "=": cellType = .equals
                    case _ where Int(value) != nil: cellType = .number
                    default: cellType = .empty
                    }
                    
                    let displayValue = fixedFlags[index] ? value : ""
                    
                    grid[row][col] = MathCrosswordCell(
                        row: row,
                        col: col,
                        value: displayValue,
                        isFixed: fixedFlags[index],
                        cellType: cellType,
                        completeValue: value
                    )
                }
            }
        }
        
        // Second pass: Apply smart removal
        applySmartRemoval(grid: &grid, equations: equations, rowOffset: rowOffset, colOffset: colOffset)
        
        return grid
    }
    
    private static func applySmartRemoval(grid: inout [[MathCrosswordCell]], equations: [MathCrosswordEquation], rowOffset: Int, colOffset: Int) {
        print("🧠 Applying smart removal to hide more numbers...")
        
        // Build cell-to-equation mapping
        var cellToEquations: [String: [(Int, Int)]] = [:] // cellKey -> [(equationIndex, valueIndex)]
        var equationCells: [Int: [CellEquationMapping]] = [:]
        
        for (eqIndex, equation) in equations.enumerated() {
            var cells: [CellEquationMapping] = []
            let values = equation.getValues()
            
            for (index, value) in values.enumerated() {
                // Skip operators and equals signs (indices 1 and 3)
                if index == 1 || index == 3 { continue }
                
                let originalRow = equation.isHorizontal ? equation.startRow : equation.startRow + index
                let originalCol = equation.isHorizontal ? equation.startCol + index : equation.startCol
                
                let row = originalRow + rowOffset
                let col = originalCol + colOffset
                let cellKey = "\(row),\(col)"
                
                cells.append(CellEquationMapping(
                    row: row, col: col, value: value, valueIndex: index, equationIndex: eqIndex
                ))
                
                if cellToEquations[cellKey] == nil {
                    cellToEquations[cellKey] = []
                }
                cellToEquations[cellKey]!.append((eqIndex, index))
            }
            
            equationCells[eqIndex] = cells
        }
        
        // Identify intersection cells
        let intersectionCells = Set(cellToEquations.compactMap { key, value in
            value.count > 1 ? key : nil
        })
        print("🔗 Found \(intersectionCells.count) intersection cells")
        
        // Try to hide additional numbers
        var hiddenCount = 0
        let maxAttempts = 50
        
        for _ in 0..<maxAttempts {
            // Get all currently visible number cells
            var visibleCells: [CellEquationMapping] = []
            
            for (eqIndex, cells) in equationCells {
                for cell in cells {
                    let gridCell = grid[cell.row][cell.col]
                    if gridCell.isFixed && gridCell.cellType == .number {
                        let isIntersection = intersectionCells.contains(cell.cellKey)
                        visibleCells.append(CellEquationMapping(
                            row: cell.row, col: cell.col, value: cell.value,
                            valueIndex: cell.valueIndex, equationIndex: cell.equationIndex,
                            isIntersection: isIntersection
                        ))
                    }
                }
            }
            
            if visibleCells.isEmpty { break }
            
            // Sort cells by priority: non-intersections first, then by value size
            visibleCells.sort { a, b in
                if a.isIntersection != b.isIntersection {
                    return !a.isIntersection && b.isIntersection
                }
                let aVal = Int(a.value) ?? 0
                let bVal = Int(b.value) ?? 0
                return aVal < bVal
            }
            
            // Try to remove a cell
            var foundRemovable = false
            for cell in visibleCells {
                if canRemoveCell(
                    targetCell: cell,
                    equations: equations,
                    equationCells: equationCells,
                    grid: &grid,
                    intersectionCells: intersectionCells
                ) {
                    // Hide this cell
                    let originalCell = grid[cell.row][cell.col]
                    grid[cell.row][cell.col] = MathCrosswordCell(
                        row: originalCell.row,
                        col: originalCell.col,
                        value: "",
                        isFixed: false,
                        cellType: originalCell.cellType,
                        completeValue: originalCell.completeValue
                    )
                    hiddenCount += 1
                    foundRemovable = true
                    print("📝 Hidden cell at (\(cell.row),\(cell.col)) with value \(cell.value)")
                    break
                }
            }
            
            if !foundRemovable {
                print("✅ No more cells can be safely removed")
                break
            }
        }
        
        print("🎯 Smart removal complete: hidden \(hiddenCount) additional numbers")
    }
    
    private static func canRemoveCell(
        targetCell: CellEquationMapping,
        equations: [MathCrosswordEquation],
        equationCells: [Int: [CellEquationMapping]],
        grid: inout [[MathCrosswordCell]],
        intersectionCells: Set<String>
    ) -> Bool {
        
        // Temporarily hide the cell
        let originalCell = grid[targetCell.row][targetCell.col]
        grid[targetCell.row][targetCell.col] = MathCrosswordCell(
            row: originalCell.row,
            col: originalCell.col,
            value: "",
            isFixed: false,
            cellType: originalCell.cellType,
            completeValue: originalCell.completeValue
        )
        
        // Check if all equations remain solvable
        var allSolvable = true
        
        for (eqIndex, _) in equations.enumerated() {
            guard let cells = equationCells[eqIndex] else { continue }
            var visibleNumbers = 0
            
            for cell in cells {
                let gridCell = grid[cell.row][cell.col]
                if gridCell.isFixed && gridCell.cellType == .number {
                    visibleNumbers += 1
                }
            }
            
            // Each equation needs at least 2 visible numbers to be solvable
            if visibleNumbers < 2 {
                allSolvable = false
                break
            }
        }
        
        // Special validation for intersection cells
        if intersectionCells.contains(targetCell.cellKey) {
            // More conservative approach for intersection cells
            var affectedEquations: Set<Int> = []
            for (eqIndex, cells) in equationCells {
                if cells.contains(where: { $0.cellKey == targetCell.cellKey }) {
                    affectedEquations.insert(eqIndex)
                }
            }
            
            for eqIndex in affectedEquations {
                guard let cells = equationCells[eqIndex] else { continue }
                var visibleNumbers = 0
                
                for cell in cells {
                    let gridCell = grid[cell.row][cell.col]
                    if gridCell.isFixed && gridCell.cellType == .number {
                        visibleNumbers += 1
                    }
                }
                
                if visibleNumbers < 2 {
                    allSolvable = false
                    break
                }
            }
        }
        
        // Restore the cell
        grid[targetCell.row][targetCell.col] = originalCell
        
        return allSolvable
    }
    
    private static func countIntersections(equations: [MathCrosswordEquation]) -> Int {
        var cellUsage: [String: Int] = [:]
        
        for equation in equations {
            for cell in equation.getCells() {
                let key = "\(cell.0),\(cell.1)"
                cellUsage[key, default: 0] += 1
            }
        }
        
        return cellUsage.values.filter { $0 > 1 }.count
    }
    
    private static func calculateGridUtilization(grid: [[MathCrosswordCell]]) -> Int {
        let totalCells = grid.count * grid.count
        let usedCells = grid.flatMap { $0 }.filter { $0.cellType != .blocked }.count
        return Int((Double(usedCells) / Double(totalCells)) * 100)
    }
    
    private static func getMaxEquations(for difficulty: String) -> Int {
        switch difficulty.lowercased() {
        case "easy": return 4
        case "medium": return 6
        case "hard": return 8
        default: return 5
        }
    }
    
    private static func getMaxValue(for difficulty: String) -> Int {
        switch difficulty.lowercased() {
        case "easy": return 12
        case "medium": return 20
        case "hard": return 30
        default: return 15
        }
    }
    
    private static func generateCrosswordWithIntersections(maxEquations: Int, maxValue: Int) -> [MathCrosswordEquation] {
        var equations: [MathCrosswordEquation] = []
        var bounds = MathCrosswordBounds()
        var occupiedCells: [String: String] = [:]
        
        // Generate first equation at center
        let firstEquation = generateSimpleEquation(startRow: 0, startCol: 0, isHorizontal: true, maxValue: maxValue)
        if firstEquation.isValid() {
            equations.append(firstEquation)
            bounds.update(with: firstEquation)
            updateOccupiedCells(equation: firstEquation, occupiedCells: &occupiedCells)
            
            print("🎯 Generated first equation: \(firstEquation.operand1) \(firstEquation.mathOperator) \(firstEquation.operand2) = \(firstEquation.result)")
        }
        
        // Generate intersecting equations
        for i in 1..<maxEquations {
            if let newEquation = generateIntersectingEquation(
                existingEquations: equations,
                bounds: bounds,
                occupiedCells: occupiedCells,
                maxValue: maxValue
            ) {
                equations.append(newEquation)
                bounds.update(with: newEquation)
                updateOccupiedCells(equation: newEquation, occupiedCells: &occupiedCells)
                
                print("✅ Added equation \(equations.count): \(newEquation.operand1) \(newEquation.mathOperator) \(newEquation.operand2) = \(newEquation.result)")
                
                if !bounds.isWithinLimits(maxWidth: 8, maxHeight: 8) {
                    print("⚠️ Crossword getting too large, stopping generation")
                    break
                }
            } else {
                print("❌ Could not generate intersecting equation \(equations.count + 1)")
            }
        }
        
        print("📊 Final crossword: \(equations.count) equations, bounds: \(bounds.getWidth())x\(bounds.getHeight())")
        return equations
    }
    
    private static func generateSimpleEquation(startRow: Int, startCol: Int, isHorizontal: Bool, maxValue: Int) -> MathCrosswordEquation {
        let operators = ["+", "-", "*"]
        let mathOperator = operators.randomElement()!
        
        let (operand1, operand2, result) = generateOperands(mathOperator: mathOperator, maxValue: maxValue)
        let missingIndex = [0, 2, 4].randomElement()!
        
        return MathCrosswordEquation(
            startRow: startRow,
            startCol: startCol,
            isHorizontal: isHorizontal,
            operand1: operand1,
            mathOperator: mathOperator,
            operand2: operand2,
            result: result,
            missingIndex: missingIndex
        )
    }
    
    private static func generateOperands(mathOperator: String, maxValue: Int) -> (Int, Int, Int) {
        switch mathOperator {
        case "+":
            let maxOperand = max(2, maxValue / 2)
            let operand1 = Int.random(in: 1..<maxOperand)
            let operand2 = Int.random(in: 1..<maxOperand)
            return (operand1, operand2, operand1 + operand2)
        case "-":
            let maxOperand = max(2, maxValue / 2)
            let result = Int.random(in: 1..<maxOperand)
            let operand2 = Int.random(in: 1..<min(result, maxOperand))
            let operand1 = result + operand2
            return (operand1, operand2, result)
        case "*":
            let operand1 = Int.random(in: 1..<min(10, maxValue))
            let maxOperand2 = max(2, maxValue / operand1)
            let operand2 = Int.random(in: 1..<maxOperand2)
            return (operand1, operand2, operand1 * operand2)
        default:
            return (2, 3, 5)
        }
    }
    
    private static func generateIntersectingEquation(
        existingEquations: [MathCrosswordEquation],
        bounds: MathCrosswordBounds,
        occupiedCells: [String: String],
        maxValue: Int
    ) -> MathCrosswordEquation? {
        
        for _ in 0..<50 { // Max attempts
            let targetEquation = existingEquations.randomElement()!
            let intersectionIndex = [0, 2, 4].randomElement()! // Only number positions
            let intersectionValue = getValueAtIndex(equation: targetEquation, index: intersectionIndex)
            
            let intersectionCell = getCellAtIndex(equation: targetEquation, index: intersectionIndex)
            let newIsHorizontal = !targetEquation.isHorizontal
            
            for newIntersectionIndex in [0, 2, 4].shuffled() {
                if let newEquation = createEquationWithIntersection(
                    intersectionCell: intersectionCell,
                    intersectionValue: intersectionValue,
                    intersectionIndex: newIntersectionIndex,
                    isHorizontal: newIsHorizontal,
                    maxValue: maxValue,
                    occupiedCells: occupiedCells,
                    currentBounds: bounds
                ) {
                    if newEquation.isValid() &&
                       !wouldConflict(equation: newEquation, occupiedCells: occupiedCells) &&
                       wouldStayInBounds(equation: newEquation, bounds: bounds) {
                        return newEquation
                    }
                }
            }
        }
        
        return nil
    }
    
    private static func getValueAtIndex(equation: MathCrosswordEquation, index: Int) -> Int {
        switch index {
        case 0: return equation.operand1
        case 2: return equation.operand2
        case 4: return equation.result
        default: return 0
        }
    }
    
    private static func getCellAtIndex(equation: MathCrosswordEquation, index: Int) -> (Int, Int) {
        if equation.isHorizontal {
            return (equation.startRow, equation.startCol + index)
        } else {
            return (equation.startRow + index, equation.startCol)
        }
    }
    
    private static func createEquationWithIntersection(
        intersectionCell: (Int, Int),
        intersectionValue: Int,
        intersectionIndex: Int,
        isHorizontal: Bool,
        maxValue: Int,
        occupiedCells: [String: String],
        currentBounds: MathCrosswordBounds
    ) -> MathCrosswordEquation? {
        
        let startRow = isHorizontal ? intersectionCell.0 : intersectionCell.0 - intersectionIndex
        let startCol = isHorizontal ? intersectionCell.1 - intersectionIndex : intersectionCell.1
        
        let operators = ["+", "-", "*"].shuffled()
        
        for mathOperator in operators {
            if let equation = createEquationWithValue(
                intersectionValue: intersectionValue,
                intersectionIndex: intersectionIndex,
                mathOperator: mathOperator,
                maxValue: maxValue,
                startRow: startRow,
                startCol: startCol,
                isHorizontal: isHorizontal
            ) {
                if equation.isValid() {
                    return equation
                }
            }
        }
        
        return nil
    }
    
    private static func createEquationWithValue(
        intersectionValue: Int,
        intersectionIndex: Int,
        mathOperator: String,
        maxValue: Int,
        startRow: Int,
        startCol: Int,
        isHorizontal: Bool
    ) -> MathCrosswordEquation? {
        
        switch intersectionIndex {
        case 0: // intersectionValue is operand1
            return createEquationWithOperand1(
                operand1: intersectionValue,
                mathOperator: mathOperator,
                maxValue: maxValue,
                startRow: startRow,
                startCol: startCol,
                isHorizontal: isHorizontal
            )
        case 2: // intersectionValue is operand2
            return createEquationWithOperand2(
                operand2: intersectionValue,
                mathOperator: mathOperator,
                maxValue: maxValue,
                startRow: startRow,
                startCol: startCol,
                isHorizontal: isHorizontal
            )
        case 4: // intersectionValue is result
            return createEquationWithResult(
                result: intersectionValue,
                mathOperator: mathOperator,
                maxValue: maxValue,
                startRow: startRow,
                startCol: startCol,
                isHorizontal: isHorizontal
            )
        default:
            return nil
        }
    }
    
    private static func createEquationWithOperand1(
        operand1: Int,
        mathOperator: String,
        maxValue: Int,
        startRow: Int,
        startCol: Int,
        isHorizontal: Bool
    ) -> MathCrosswordEquation? {
        
        let (operand2, result): (Int, Int)
        
        switch mathOperator {
        case "+":
            let maxOperand2 = max(1, min(maxValue - operand1, maxValue / 2))
            if maxOperand2 <= 1 { return nil }
            operand2 = Int.random(in: 1..<maxOperand2)
            result = operand1 + operand2
        case "-":
            let maxOperand2 = max(1, min(operand1, maxValue / 2))
            if maxOperand2 <= 1 { return nil }
            operand2 = Int.random(in: 1..<maxOperand2)
            result = operand1 - operand2
        case "*":
            if operand1 == 0 { return nil }
            let maxOperand2 = max(1, min(maxValue / operand1, 10))
            if maxOperand2 <= 1 { return nil }
            operand2 = Int.random(in: 1..<maxOperand2)
            result = operand1 * operand2
        default:
            return nil
        }
        
        if result <= 0 || result > maxValue { return nil }
        
        let missingIndex = [2, 4].randomElement()!
        
        return MathCrosswordEquation(
            startRow: startRow,
            startCol: startCol,
            isHorizontal: isHorizontal,
            operand1: operand1,
            mathOperator: mathOperator,
            operand2: operand2,
            result: result,
            missingIndex: missingIndex
        )
    }
    
    private static func createEquationWithOperand2(
        operand2: Int,
        mathOperator: String,
        maxValue: Int,
        startRow: Int,
        startCol: Int,
        isHorizontal: Bool
    ) -> MathCrosswordEquation? {
        
        let (operand1, result): (Int, Int)
        
        switch mathOperator {
        case "+":
            let maxOperand1 = max(1, min(maxValue - operand2, maxValue / 2))
            if maxOperand1 <= 1 { return nil }
            operand1 = Int.random(in: 1..<maxOperand1)
            result = operand1 + operand2
        case "-":
            let maxOperand1 = max(operand2 + 1, min(maxValue, operand2 + maxValue / 2))
            if maxOperand1 <= operand2 { return nil }
            operand1 = Int.random(in: (operand2 + 1)..<maxOperand1)
            result = operand1 - operand2
        case "*":
            if operand2 == 0 { return nil }
            let maxOperand1 = max(1, min(maxValue / operand2, 10))
            if maxOperand1 <= 1 { return nil }
            operand1 = Int.random(in: 1..<maxOperand1)
            result = operand1 * operand2
        default:
            return nil
        }
        
        if result <= 0 || result > maxValue || operand1 <= 0 { return nil }
        
        let missingIndex = [0, 4].randomElement()!
        
        return MathCrosswordEquation(
            startRow: startRow,
            startCol: startCol,
            isHorizontal: isHorizontal,
            operand1: operand1,
            mathOperator: mathOperator,
            operand2: operand2,
            result: result,
            missingIndex: missingIndex
        )
    }
    
    private static func createEquationWithResult(
        result: Int,
        mathOperator: String,
        maxValue: Int,
        startRow: Int,
        startCol: Int,
        isHorizontal: Bool
    ) -> MathCrosswordEquation? {
        
        let (operand1, operand2): (Int, Int)
        
        switch mathOperator {
        case "+":
            if result <= 1 { return nil }
            let maxOperand1 = min(result - 1, maxValue)
            if maxOperand1 <= 1 { return nil }
            operand1 = Int.random(in: 1..<maxOperand1)
            operand2 = result - operand1
        case "-":
            let maxOperand2 = max(1, min(maxValue - result, result))
            if maxOperand2 <= 1 { return nil }
            operand2 = Int.random(in: 1..<maxOperand2)
            operand1 = result + operand2
        case "*":
            let factors = getFactors(of: result)
            if factors.isEmpty { return nil }
            let factor = factors.randomElement()!
            operand1 = factor
            operand2 = result / factor
        default:
            return nil
        }
        
        if operand1 <= 0 || operand2 <= 0 || operand1 > maxValue || operand2 > maxValue { return nil }
        
        let missingIndex = [0, 2].randomElement()!
        
        return MathCrosswordEquation(
            startRow: startRow,
            startCol: startCol,
            isHorizontal: isHorizontal,
            operand1: operand1,
            mathOperator: mathOperator,
            operand2: operand2,
            result: result,
            missingIndex: missingIndex
        )
    }
    
    private static func getFactors(of number: Int) -> [Int] {
        var factors: [Int] = []
        for i in 1...Int(sqrt(Double(number))) {
            if number % i == 0 {
                factors.append(i)
                if i != number / i && number / i <= 20 {
                    factors.append(number / i)
                }
            }
        }
        return factors.filter { $0 > 1 && $0 <= 20 }
    }
    
    private static func updateOccupiedCells(equation: MathCrosswordEquation, occupiedCells: inout [String: String]) {
        let values = equation.getValues()
        let cells = equation.getCells()
        
        for (index, cell) in cells.enumerated() {
            let key = "\(cell.0),\(cell.1)"
            occupiedCells[key] = values[index]
        }
    }
    
    private static func wouldConflict(equation: MathCrosswordEquation, occupiedCells: [String: String]) -> Bool {
        let values = equation.getValues()
        let cells = equation.getCells()
        
        for (index, cell) in cells.enumerated() {
            let key = "\(cell.0),\(cell.1)"
            if let existingValue = occupiedCells[key], existingValue != values[index] {
                return true
            }
        }
        
        return false
    }
    
    private static func wouldStayInBounds(equation: MathCrosswordEquation, bounds: MathCrosswordBounds) -> Bool {
        var tempBounds = bounds
        tempBounds.update(with: equation)
        return tempBounds.isWithinLimits(maxWidth: 8, maxHeight: 8)
    }
    
    private static func calculateRequiredGridSize(equations: [MathCrosswordEquation]) -> Int {
        if equations.isEmpty { return 9 }
        
        var bounds = MathCrosswordBounds()
        for equation in equations {
            bounds.update(with: equation)
        }
        
        let width = bounds.getWidth() + 8
        let height = bounds.getHeight() + 8
        
        return min(max(max(width, height), 9), 12)
    }
    
    private static func createPuzzleDataJSON(
        gridSize: Int,
        difficulty: String,
        equations: [MathCrosswordEquation],
        grid: [[MathCrosswordCell]],
        numberCounts: [String: Int],
        totalMissingNumbers: Int
    ) -> String {
        
        let puzzleData: [String: Any] = [
            "gridSize": gridSize,
            "difficulty": difficulty,
            "equations": equations.map { equation in
                [
                    "startRow": equation.startRow,
                    "startCol": equation.startCol,
                    "isHorizontal": equation.isHorizontal,
                    "operand1": equation.operand1,
                    "mathOperator": equation.mathOperator,
                    "operand2": equation.operand2,
                    "result": equation.result,
                    "missingIndex": equation.missingIndex
                ]
            },
            "grid": grid.map { row in
                row.map { cell in
                    [
                        "value": cell.value,
                        "isFixed": cell.isFixed,
                        "cellType": cell.cellType.rawValue
                    ]
                }
            },
            "numberCounts": numberCounts,
            "metadata": [
                "equationCount": equations.count,
                "totalMissingNumbers": totalMissingNumbers,
                "intersectionCount": countIntersections(equations: equations),
                "gridUtilization": calculateGridUtilization(grid: grid)
            ]
        ]
        
        guard let jsonData = try? JSONSerialization.data(withJSONObject: puzzleData),
              let jsonString = String(data: jsonData, encoding: .utf8) else {
            return "{}"
        }
        
        return jsonString
    }
    
    private static func createAnswerDataJSON(
        grid: [[MathCrosswordCell]],
        equations: [MathCrosswordEquation]
    ) -> String {
        
        let answerData: [String: Any] = [
            "correctGrid": grid.map { row in
                row.map { cell in
                    if cell.cellType == .blocked {
                        return ""
                    } else if !cell.completeValue.isEmpty {
                        return cell.completeValue
                    } else {
                        return cell.value
                    }
                }
            },
            "equations": equations.map { equation in
                [
                    "equation": "\(equation.operand1) \(equation.mathOperator) \(equation.operand2) = \(equation.result)",
                    "missingValue": equation.getMissingValue(),
                    "position": "\(equation.startRow),\(equation.startCol)",
                    "isHorizontal": equation.isHorizontal
                ]
            }
        ]
        
        guard let jsonData = try? JSONSerialization.data(withJSONObject: answerData),
              let jsonString = String(data: jsonData, encoding: .utf8) else {
            return "{}"
        }
        
        return jsonString
    }
}
