package com.kreativekoala.riddleverse

import org.json.JSONArray
import org.json.JSONObject
import kotlin.random.Random

object MathCrosswordGenerator {

    data class CrosswordEquation(
        val startRow: Int,
        val startCol: Int,
        val isHorizontal: Boolean,
        val operand1: Int,
        val operator: String,
        val operand2: Int,
        val result: Int,
        val missingIndex: Int // 0 = operand1, 1 = operator, 2 = operand2, 4 = result
    ) {
        fun getValues(): List<String> {
            return listOf(
                operand1.toString(),    // Index 0
                operator,               // Index 1
                operand2.toString(),    // Index 2
                "=",                   // Index 3 (always equals)
                result.toString()      // Index 4
            )
        }

        fun getFixedFlags(): List<Boolean> {
            return listOf(
                missingIndex != 0, // operand1 is fixed if not missing
                missingIndex != 1, // operator is fixed if not missing
                missingIndex != 2, // operand2 is fixed if not missing
                true,              // equals is always fixed (index 3)
                missingIndex != 4  // result is fixed if not missing
            )
        }

        fun getMissingValue(): String {
            return when (missingIndex) {
                0 -> operand1.toString()
                1 -> operator
                2 -> operand2.toString()
                4 -> result.toString()
                else -> ""
            }
        }

        fun getMissingType(): String {
            return when (missingIndex) {
                0, 2, 4 -> "NUMBER"
                1 -> "OPERATOR"
                else -> "UNKNOWN"
            }
        }

        fun getCells(): List<Pair<Int, Int>> {
            return (0..4).map { index ->
                if (isHorizontal) {
                    Pair(startRow, startCol + index)
                } else {
                    Pair(startRow + index, startCol)
                }
            }
        }

        fun getValueAt(row: Int, col: Int): String? {
            val values = getValues()
            getCells().forEachIndexed { index, (r, c) ->
                if (r == row && c == col) {
                    return values[index]
                }
            }
            return null
        }

        fun isValid(): Boolean {
            val calculatedResult = when (operator) {
                "+" -> operand1 + operand2
                "-" -> operand1 - operand2
                "*" -> operand1 * operand2
                "/" -> if (operand2 != 0 && operand1 % operand2 == 0) operand1 / operand2 else return false
                else -> return false
            }
            return calculatedResult == result && result > 0 && operand1 > 0 && operand2 > 0
        }
    }

    data class CrosswordBounds(
        var minRow: Int = Int.MAX_VALUE,
        var maxRow: Int = Int.MIN_VALUE,
        var minCol: Int = Int.MAX_VALUE,
        var maxCol: Int = Int.MIN_VALUE
    ) {
        fun update(equation: CrosswordEquation) {
            val cells = equation.getCells()
            cells.forEach { (row, col) ->
                minRow = kotlin.math.min(minRow, row)
                maxRow = kotlin.math.max(maxRow, row)
                minCol = kotlin.math.min(minCol, col)
                maxCol = kotlin.math.max(maxCol, col)
            }
        }

        fun getWidth() = if (maxCol == Int.MIN_VALUE) 0 else maxCol - minCol + 1
        fun getHeight() = if (maxRow == Int.MIN_VALUE) 0 else maxRow - minRow + 1

        fun isWithinLimits(maxWidth: Int = 15, maxHeight: Int = 15): Boolean {
            return getWidth() <= maxWidth && getHeight() <= maxHeight
        }
    }

    enum class CellType {
        BLOCKED, NUMBER, OPERATOR, EQUALS, EMPTY
    }

    data class MathCrosswordCell(
        val row: Int,
        val col: Int,
        val value: String = "",
        val isFixed: Boolean = false,
        val cellType: CellType = CellType.EMPTY,
        val completeValue: String = "" // Store the actual value even if hidden
    )

    data class CellEquationMapping(
        val row: Int,
        val col: Int,
        val value: String,
        val valueIndex: Int,
        val cellKey: String,
        val equationIndex: Int,
        val isIntersection: Boolean = false
    )

    fun generatePuzzle(difficulty: String): Pair<String, String> {
        val (maxEquations, maxValue, distractorMultiplier, operatorRemovalChance) = when (difficulty.lowercase()) {
            "easy" -> Quadruple(5, 15, 2, 0.2f)
            "medium" -> Quadruple(8, 25, 3, 0.35f)
            "hard" -> Quadruple(12, 40, 4, 0.5f)
            else -> Quadruple(6, 20, 2, 0.3f)
        }

        val equations = generateDenseCrosswordWithIntersections(maxEquations, maxValue, operatorRemovalChance)
        val gridSize = calculateRequiredGridSize(equations)
        val grid = createGridWithSmartRemoval(gridSize, equations)

        // Collect missing values and their types
        val missingNumbers = mutableListOf<String>()
        val missingOperators = mutableListOf<String>()
        val allMissingCells = mutableListOf<Triple<Int, Int, String>>()

        grid.forEach { row ->
            row.forEach { cell ->
                if (!cell.isFixed && cell.completeValue.isNotEmpty()) {
                    val value = cell.completeValue
                    allMissingCells.add(Triple(cell.row, cell.col, value))

                    when (cell.cellType) {
                        CellType.NUMBER -> missingNumbers.add(value)
                        CellType.OPERATOR -> missingOperators.add(value)
                        else -> {}
                    }
                }
            }
        }

        // Generate distractors
        val numberDistractors = generateNumberDistractors(missingNumbers, distractorMultiplier, maxValue)
        val operatorDistractors = generateOperatorDistractors(missingOperators, distractorMultiplier)

        // Combine correct answers with distractors
        val allNumbers = (missingNumbers + numberDistractors).shuffled()
        val allOperators = (missingOperators + operatorDistractors).shuffled()

        // Count occurrences for selection interface
        val numberCounts = mutableMapOf<String, Int>()
        val operatorCounts = mutableMapOf<String, Int>()

        allNumbers.forEach { number ->
            numberCounts[number] = numberCounts.getOrDefault(number, 0) + 1
        }

        allOperators.forEach { operator ->
            operatorCounts[operator] = operatorCounts.getOrDefault(operator, 0) + 1
        }

        val puzzleData = JSONObject().apply {
            put("gridSize", gridSize)
            put("difficulty", difficulty)
            put("equations", JSONArray().apply {
                equations.forEach { eq ->
                    put(JSONObject().apply {
                        put("startRow", eq.startRow)
                        put("startCol", eq.startCol)
                        put("isHorizontal", eq.isHorizontal)
                        put("operand1", eq.operand1)
                        put("operator", eq.operator)
                        put("operand2", eq.operand2)
                        put("result", eq.result)
                        put("missingIndex", eq.missingIndex)
                        put("missingType", eq.getMissingType())
                    })
                }
            })
            put("grid", JSONArray().apply {
                grid.forEach { row ->
                    val rowArray = JSONArray()
                    row.forEach { cell ->
                        rowArray.put(JSONObject().apply {
                            put("value", cell.value)
                            put("isFixed", cell.isFixed)
                            put("cellType", cell.cellType.name)
                        })
                    }
                    put(rowArray)
                }
            })
            put("numberCounts", JSONObject().apply {
                numberCounts.forEach { (number, count) ->
                    put(number, count)
                }
            })
            put("operatorCounts", JSONObject().apply {
                operatorCounts.forEach { (operator, count) ->
                    put(operator, count)
                }
            })
            put("metadata", JSONObject().apply {
                put("equationCount", equations.size)
                put("totalMissingNumbers", missingNumbers.size)
                put("totalMissingOperators", missingOperators.size)
                put("intersectionCount", countIntersections(equations))
                put("gridUtilization", calculateGridUtilization(grid))
                put("distractorCount", numberDistractors.size + operatorDistractors.size)
            })
        }

        val answerData = JSONObject().apply {
            put("correctGrid", JSONArray().apply {
                grid.forEach { row ->
                    val rowArray = JSONArray()
                    row.forEach { cell ->
                        val correctValue = when {
                            cell.cellType == CellType.BLOCKED -> ""
                            cell.completeValue.isNotEmpty() -> cell.completeValue
                            else -> cell.value
                        }
                        rowArray.put(correctValue)
                    }
                    put(rowArray)
                }
            })
            put("equations", JSONArray().apply {
                equations.forEach { eq ->
                    put(JSONObject().apply {
                        put("equation", "${eq.operand1} ${eq.operator} ${eq.operand2} = ${eq.result}")
                        put("missingValue", eq.getMissingValue())
                        put("missingType", eq.getMissingType())
                        put("position", "${eq.startRow},${eq.startCol}")
                        put("isHorizontal", eq.isHorizontal)
                    })
                }
            })
        }

        println("📊 Generated dense puzzle: ${equations.size} equations, ${allMissingCells.size} missing cells, ${numberDistractors.size + operatorDistractors.size} distractors")

        return Pair(puzzleData.toString(), answerData.toString())
    }

    private fun generateNumberDistractors(correctNumbers: List<String>, multiplier: Int, maxValue: Int): List<String> {
        val distractors = mutableSetOf<String>()
        val correctSet = correctNumbers.toSet()

        // Strategy 1: Numbers close to correct answers (±1, ±2, ±3)
        correctNumbers.forEach { numStr ->
            val num = numStr.toIntOrNull() ?: return@forEach
            for (offset in listOf(-3, -2, -1, 1, 2, 3)) {
                val distractor = num + offset
                if (distractor > 0 && distractor <= maxValue && !correctSet.contains(distractor.toString())) {
                    distractors.add(distractor.toString())
                }
            }
        }

        // Strategy 2: Common calculation results from correct numbers
        correctNumbers.forEach { num1Str ->
            correctNumbers.forEach { num2Str ->
                val num1 = num1Str.toIntOrNull() ?: return@forEach
                val num2 = num2Str.toIntOrNull() ?: return@forEach

                listOf(
                    num1 + num2,
                    kotlin.math.abs(num1 - num2),
                    num1 * num2,
                    if (num2 != 0 && num1 % num2 == 0) num1 / num2 else null
                ).filterNotNull().forEach { result ->
                    if (result > 0 && result <= maxValue && !correctSet.contains(result.toString())) {
                        distractors.add(result.toString())
                    }
                }
            }
        }

        // Strategy 3: Random numbers in valid range
        repeat(multiplier * correctNumbers.size) {
            val randomNum = Random.nextInt(1, maxValue + 1)
            if (!correctSet.contains(randomNum.toString())) {
                distractors.add(randomNum.toString())
            }
        }

        return distractors.take(multiplier * correctNumbers.size)
    }

    private fun generateOperatorDistractors(correctOperators: List<String>, multiplier: Int): List<String> {
        val allOperators = listOf("+", "-", "*", "/")
        val correctSet = correctOperators.toSet()
        val distractors = allOperators.filter { !correctSet.contains(it) }

        val result = mutableListOf<String>()
        val targetCount = multiplier * correctOperators.size

        repeat(targetCount) {
            if (distractors.isNotEmpty()) {
                result.add(distractors.random())
            }
        }

        return result
    }

    private fun generateDenseCrosswordWithIntersections(maxEquations: Int, maxValue: Int, operatorRemovalChance: Float): List<CrosswordEquation> {
        val equations = mutableListOf<CrosswordEquation>()
        val bounds = CrosswordBounds()
        val occupiedCells = mutableMapOf<Pair<Int, Int>, String>()
        val maxAttempts = 100

        // Generate first equation at center
        val firstEquation = generateSimpleEquation(0, 0, true, maxValue, operatorRemovalChance)
        if (!firstEquation.isValid()) return equations

        equations.add(firstEquation)
        bounds.update(firstEquation)
        updateOccupiedCells(firstEquation, occupiedCells)

        println("🎯 Generated first equation: ${firstEquation.operand1} ${firstEquation.operator} ${firstEquation.operand2} = ${firstEquation.result}")

        // Generate more equations with higher intersection attempts
        var attempts = 0
        while (equations.size < maxEquations && attempts < maxAttempts) {
            attempts++

            val newEquation = generateIntersectingEquation(equations, bounds, occupiedCells, maxValue, operatorRemovalChance)
            if (newEquation != null) {
                equations.add(newEquation)
                bounds.update(newEquation)
                updateOccupiedCells(newEquation, occupiedCells)

                println("✅ Added equation ${equations.size}: ${newEquation.operand1} ${newEquation.operator} ${newEquation.operand2} = ${newEquation.result}")

                // Allow larger crosswords for more density
                if (!bounds.isWithinLimits(12, 12)) {
                    println("⚠️ Crossword getting too large, stopping generation")
                    break
                }

                // Reset attempt counter on success
                attempts = 0
            }
        }

        println("📊 Final dense crossword: ${equations.size} equations, bounds: ${bounds.getWidth()}x${bounds.getHeight()}")
        return equations
    }

    private fun generateIntersectingEquation(
        existingEquations: List<CrosswordEquation>,
        bounds: CrosswordBounds,
        occupiedCells: Map<Pair<Int, Int>, String>,
        maxValue: Int,
        operatorRemovalChance: Float
    ): CrosswordEquation? {

        val maxAttempts = 75
        for (attempt in 1..maxAttempts) {
            // Try multiple existing equations for intersection
            val shuffledEquations = existingEquations.shuffled()

            for (targetEquation in shuffledEquations) {
                // Try all possible intersection points (0, 1, 2, 4 - everything except =)
                val intersectionIndices = listOf(0, 1, 2, 4).shuffled()

                for (intersectionIndex in intersectionIndices) {
                    val intersectionValue = when (intersectionIndex) {
                        0 -> targetEquation.operand1.toString()
                        1 -> targetEquation.operator
                        2 -> targetEquation.operand2.toString()
                        4 -> targetEquation.result.toString()
                        else -> continue
                    }

                    val intersectionCell = if (targetEquation.isHorizontal) {
                        Pair(targetEquation.startRow, targetEquation.startCol + intersectionIndex)
                    } else {
                        Pair(targetEquation.startRow + intersectionIndex, targetEquation.startCol)
                    }

                    // Try both orientations
                    val newIsHorizontal = !targetEquation.isHorizontal

                    // Try different positions for the intersection in the new equation
                    for (newIntersectionIndex in listOf(0, 1, 2, 4).shuffled()) {
                        val newEquation = createEquationWithIntersection(
                            intersectionCell = intersectionCell,
                            intersectionValue = intersectionValue,
                            intersectionIndex = newIntersectionIndex,
                            isHorizontal = newIsHorizontal,
                            maxValue = maxValue,
                            occupiedCells = occupiedCells,
                            currentBounds = bounds,
                            operatorRemovalChance = operatorRemovalChance
                        )

                        if (newEquation != null && newEquation.isValid() &&
                            !wouldConflict(newEquation, occupiedCells) &&
                            wouldStayInBounds(newEquation, bounds)) {
                            return newEquation
                        }
                    }
                }
            }
        }

        return null
    }

    private fun createEquationWithIntersection(
        intersectionCell: Pair<Int, Int>,
        intersectionValue: String,
        intersectionIndex: Int,
        isHorizontal: Boolean,
        maxValue: Int,
        occupiedCells: Map<Pair<Int, Int>, String>,
        currentBounds: CrosswordBounds,
        operatorRemovalChance: Float
    ): CrosswordEquation? {

        val startRow = if (isHorizontal) {
            intersectionCell.first
        } else {
            intersectionCell.first - intersectionIndex
        }

        val startCol = if (isHorizontal) {
            intersectionCell.second - intersectionIndex
        } else {
            intersectionCell.second
        }

        return when (intersectionIndex) {
            0 -> { // intersectionValue is operand1
                val operand1 = intersectionValue.toIntOrNull() ?: return null
                createEquationWithOperand1(operand1, maxValue, startRow, startCol, isHorizontal, operatorRemovalChance)
            }
            1 -> { // intersectionValue is operator
                createEquationWithOperator(intersectionValue, maxValue, startRow, startCol, isHorizontal, operatorRemovalChance)
            }
            2 -> { // intersectionValue is operand2
                val operand2 = intersectionValue.toIntOrNull() ?: return null
                createEquationWithOperand2(operand2, maxValue, startRow, startCol, isHorizontal, operatorRemovalChance)
            }
            4 -> { // intersectionValue is result
                val result = intersectionValue.toIntOrNull() ?: return null
                createEquationWithResult(result, maxValue, startRow, startCol, isHorizontal, operatorRemovalChance)
            }
            else -> null
        }
    }

    private fun createEquationWithOperand1(operand1: Int, maxValue: Int, startRow: Int, startCol: Int, isHorizontal: Boolean, operatorRemovalChance: Float): CrosswordEquation? {
        val operators = listOf("+", "-", "*").shuffled()

        for (operator in operators) {
            try {
                val (operand2, result) = when (operator) {
                    "+" -> {
                        val maxOp2 = kotlin.math.max(1, kotlin.math.min(maxValue - operand1, maxValue / 2))
                        if (maxOp2 <= 1) continue
                        // Fix: Ensure we have valid range
                        val safeMaxOp2 = kotlin.math.max(2, maxOp2)
                        val op2 = Random.nextInt(1, safeMaxOp2 + 1)
                        Pair(op2, operand1 + op2)
                    }
                    "-" -> {
                        val maxOp2 = kotlin.math.max(1, kotlin.math.min(operand1, maxValue / 2))
                        if (maxOp2 <= 1) continue
                        // Fix: Ensure we have valid range
                        val safeMaxOp2 = kotlin.math.max(2, maxOp2)
                        val op2 = Random.nextInt(1, safeMaxOp2 + 1)
                        Pair(op2, operand1 - op2)
                    }
                    "*" -> {
                        if (operand1 == 0) continue
                        val maxOp2 = kotlin.math.max(1, kotlin.math.min(maxValue / operand1, 12))
                        if (maxOp2 <= 1) continue
                        // Fix: Ensure we have valid range
                        val safeMaxOp2 = kotlin.math.max(2, maxOp2)
                        val op2 = Random.nextInt(1, safeMaxOp2 + 1)
                        Pair(op2, operand1 * op2)
                    }
                    else -> continue
                }

                if (result <= 0 || result > maxValue) continue

                val possibleMissingIndices = mutableListOf(1, 2, 4) // Can't remove operand1 since it's fixed
                if (Random.nextFloat() > operatorRemovalChance) {
                    possibleMissingIndices.remove(1) // Less likely to remove operator
                }

                val missingIndex = possibleMissingIndices.randomOrNull() ?: 2

                return CrosswordEquation(startRow, startCol, isHorizontal, operand1, operator, operand2, result, missingIndex)
            } catch (e: Exception) {
                continue
            }
        }
        return null
    }

    private fun createEquationWithOperator(operator: String, maxValue: Int, startRow: Int, startCol: Int, isHorizontal: Boolean, operatorRemovalChance: Float): CrosswordEquation? {
        try {
            val (operand1, operand2, result) = when (operator) {
                "+" -> {
                    val maxOperand = kotlin.math.max(2, maxValue / 2)
                    // Fix: Ensure we have valid range
                    val safeMaxOperand = kotlin.math.max(2, maxOperand)
                    val op1 = Random.nextInt(1, safeMaxOperand + 1)
                    val op2 = Random.nextInt(1, safeMaxOperand + 1)
                    Triple(op1, op2, op1 + op2)
                }
                "-" -> {
                    val maxResult = kotlin.math.max(1, maxValue / 2)
                    // Fix: Ensure we have valid range
                    val safeMaxResult = kotlin.math.max(2, maxResult)
                    val result = Random.nextInt(1, safeMaxResult + 1)
                    val maxOp2 = kotlin.math.min(result, maxValue / 2)
                    val safeMaxOp2 = kotlin.math.max(1, maxOp2)
                    val op2 = Random.nextInt(1, safeMaxOp2 + 1)
                    Triple(result + op2, op2, result)
                }
                "*" -> {
                    val maxOperand = kotlin.math.max(2, kotlin.math.min(12, maxValue))
                    // Fix: Ensure we have valid range
                    val safeMaxOperand = kotlin.math.max(2, maxOperand)
                    val op1 = Random.nextInt(1, safeMaxOperand + 1)
                    val maxOp2 = kotlin.math.max(1, maxValue / op1)
                    val safeMaxOp2 = kotlin.math.max(1, maxOp2)
                    val op2 = Random.nextInt(1, safeMaxOp2 + 1)
                    Triple(op1, op2, op1 * op2)
                }
                "/" -> {
                    val maxResult = kotlin.math.max(2, maxValue)
                    val result = Random.nextInt(1, maxResult + 1)
                    val factors = getFactors(result)
                    if (factors.isEmpty()) return null
                    val op2 = factors.random()
                    Triple(result * op2, op2, result)
                }
                else -> return null
            }

            if (operand1 <= 0 || operand2 <= 0 || result <= 0 || result > maxValue) return null

            val possibleMissingIndices = mutableListOf(0, 2, 4) // Can't remove operator since it's fixed
            val missingIndex = possibleMissingIndices.random()

            return CrosswordEquation(startRow, startCol, isHorizontal, operand1, operator, operand2, result, missingIndex)
        } catch (e: Exception) {
            return null
        }
    }

    private fun createEquationWithOperand2(operand2: Int, maxValue: Int, startRow: Int, startCol: Int, isHorizontal: Boolean, operatorRemovalChance: Float): CrosswordEquation? {
        val operators = listOf("+", "-", "*").shuffled()

        for (operator in operators) {
            try {
                val (operand1, result) = when (operator) {
                    "+" -> {
                        val maxOp1 = kotlin.math.max(1, kotlin.math.min(maxValue - operand2, maxValue / 2))
                        if (maxOp1 <= 1) continue
                        // Fix: Ensure we have valid range
                        val safeMaxOp1 = kotlin.math.max(2, maxOp1)
                        val op1 = Random.nextInt(1, safeMaxOp1 + 1)
                        Pair(op1, op1 + operand2)
                    }
                    "-" -> {
                        val maxOp1 = kotlin.math.max(operand2 + 1, kotlin.math.min(maxValue, operand2 + maxValue / 2))
                        if (maxOp1 <= operand2) continue
                        // Fix: Ensure we have valid range
                        val safeMaxOp1 = kotlin.math.max(operand2 + 2, maxOp1)
                        val op1 = Random.nextInt(operand2 + 1, safeMaxOp1 + 1)
                        Pair(op1, op1 - operand2)
                    }
                    "*" -> {
                        if (operand2 == 0) continue
                        val maxOp1 = kotlin.math.max(1, kotlin.math.min(maxValue / operand2, 12))
                        if (maxOp1 <= 1) continue
                        // Fix: Ensure we have valid range
                        val safeMaxOp1 = kotlin.math.max(2, maxOp1)
                        val op1 = Random.nextInt(1, safeMaxOp1 + 1)
                        Pair(op1, op1 * operand2)
                    }
                    else -> continue
                }

                if (result <= 0 || result > maxValue || operand1 <= 0) continue

                val possibleMissingIndices = mutableListOf(0, 1, 4) // Can't remove operand2 since it's fixed
                if (Random.nextFloat() > operatorRemovalChance) {
                    possibleMissingIndices.remove(1) // Less likely to remove operator
                }

                val missingIndex = possibleMissingIndices.randomOrNull() ?: 0

                return CrosswordEquation(startRow, startCol, isHorizontal, operand1, operator, operand2, result, missingIndex)
            } catch (e: Exception) {
                continue
            }
        }
        return null
    }

    private fun createEquationWithResult(result: Int, maxValue: Int, startRow: Int, startCol: Int, isHorizontal: Boolean, operatorRemovalChance: Float): CrosswordEquation? {
        val operators = listOf("+", "-", "*").shuffled()

        for (operator in operators) {
            try {
                val (operand1, operand2) = when (operator) {
                    "+" -> {
                        if (result <= 1) continue
                        val maxOp1 = kotlin.math.min(result - 1, maxValue)
                        if (maxOp1 <= 1) continue
                        // Fix: Ensure we have valid range
                        val safeMaxOp1 = kotlin.math.max(2, maxOp1)
                        val op1 = Random.nextInt(1, safeMaxOp1 + 1)
                        Pair(op1, result - op1)
                    }
                    "-" -> {
                        val maxOp2 = kotlin.math.max(1, kotlin.math.min(maxValue - result, result))
                        if (maxOp2 <= 1) continue
                        // Fix: Ensure we have valid range
                        val safeMaxOp2 = kotlin.math.max(2, maxOp2)
                        val op2 = Random.nextInt(1, safeMaxOp2 + 1)
                        Pair(result + op2, op2)
                    }
                    "*" -> {
                        val factors = getFactors(result)
                        if (factors.isEmpty()) continue
                        val op1 = factors.random()
                        Pair(op1, result / op1)
                    }
                    else -> continue
                }

                if (operand1 <= 0 || operand2 <= 0 || operand1 > maxValue || operand2 > maxValue) continue

                val possibleMissingIndices = mutableListOf(0, 1, 2) // Can't remove result since it's fixed
                if (Random.nextFloat() > operatorRemovalChance) {
                    possibleMissingIndices.remove(1) // Less likely to remove operator
                }

                val missingIndex = possibleMissingIndices.randomOrNull() ?: 0

                return CrosswordEquation(startRow, startCol, isHorizontal, operand1, operator, operand2, result, missingIndex)
            } catch (e: Exception) {
                continue
            }
        }
        return null
    }


    private fun generateSimpleEquation(startRow: Int, startCol: Int, isHorizontal: Boolean, maxValue: Int, operatorRemovalChance: Float): CrosswordEquation {
        val operators = listOf("+", "-", "*")
        val operator = operators.random()

        val (operand1, operand2, result) = when (operator) {
            "+" -> {
                val maxOperand = kotlin.math.max(2, maxValue / 2)
                val op1 = Random.nextInt(1, maxOperand + 1)
                val op2 = Random.nextInt(1, maxOperand + 1)
                Triple(op1, op2, op1 + op2)
            }
            "-" -> {
                val maxOperand = kotlin.math.max(2, maxValue / 2)
                val result = Random.nextInt(1, maxOperand + 1)
                val op2 = Random.nextInt(1, kotlin.math.min(result, maxOperand) + 1)
                Triple(result + op2, op2, result)
            }
            "*" -> {
                val maxOperand = kotlin.math.max(2, kotlin.math.min(12, maxValue))
                val op1 = Random.nextInt(1, maxOperand + 1)
                val maxOp2 = kotlin.math.max(1, maxValue / op1)
                val op2 = Random.nextInt(1, maxOp2 + 1)
                Triple(op1, op2, op1 * op2)
            }
            else -> Triple(2, 3, 5)
        }

        // Determine what to remove (including operators)
        val possibleMissingIndices = mutableListOf(0, 2, 4) // operands and result
        if (Random.nextFloat() < operatorRemovalChance) {
            possibleMissingIndices.add(1) // operator
        }

        val missingIndex = possibleMissingIndices.random()

        return CrosswordEquation(startRow, startCol, isHorizontal, operand1, operator, operand2, result, missingIndex)
    }

    // Keep existing helper functions with minimal changes
    private fun createGridWithSmartRemoval(gridSize: Int, equations: List<CrosswordEquation>): List<List<MathCrosswordCell>> {
        if (equations.isEmpty()) {
            return List(gridSize) { row ->
                List(gridSize) { col ->
                    MathCrosswordCell(row = row, col = col, cellType = CellType.BLOCKED)
                }
            }
        }

        val bounds = CrosswordBounds()
        equations.forEach { bounds.update(it) }

        val totalWidth = bounds.getWidth()
        val totalHeight = bounds.getHeight()
        val rowOffset = -bounds.minRow + (gridSize - totalHeight) / 2
        val colOffset = -bounds.minCol + (gridSize - totalWidth) / 2

        val grid = Array(gridSize) { row ->
            Array(gridSize) { col ->
                MathCrosswordCell(
                    row = row,
                    col = col,
                    cellType = CellType.BLOCKED
                )
            }
        }

        equations.forEach { equation ->
            val values = equation.getValues()
            val fixedFlags = equation.getFixedFlags()

            values.forEachIndexed { index, value ->
                val originalRow = if (equation.isHorizontal) {
                    equation.startRow
                } else {
                    equation.startRow + index
                }
                val originalCol = if (equation.isHorizontal) {
                    equation.startCol + index
                } else {
                    equation.startCol
                }

                val row = originalRow + rowOffset
                val col = originalCol + colOffset

                if (row >= 1 && row < gridSize - 1 && col >= 1 && col < gridSize - 1) {
                    val cellType = when {
                        value == "+" || value == "-" || value == "*" || value == "/" -> CellType.OPERATOR
                        value == "=" -> CellType.EQUALS
                        value.toIntOrNull() != null -> CellType.NUMBER
                        else -> CellType.EMPTY
                    }

                    val displayValue = if (fixedFlags[index]) value else ""

                    grid[row][col] = MathCrosswordCell(
                        row = row,
                        col = col,
                        value = displayValue,
                        isFixed = fixedFlags[index],
                        cellType = cellType,
                        completeValue = value
                    )
                }
            }
        }

        applySmartRemoval(grid, equations, rowOffset, colOffset)

        return grid.map { it.toList() }
    }

    // Add data class for quadruple
    data class Quadruple<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)

    // Keep all other existing helper functions unchanged...
    private fun applySmartRemoval(grid: Array<Array<MathCrosswordCell>>, equations: List<CrosswordEquation>, rowOffset: Int, colOffset: Int) {
        println("🧠 Applying smart removal to hide more numbers...")

        val cellToEquations = mutableMapOf<String, MutableList<Pair<Int, Int>>>()
        val equationCells = mutableMapOf<Int, List<CellEquationMapping>>()

        equations.forEachIndexed { eqIndex, equation ->
            val cells = mutableListOf<CellEquationMapping>()
            val values = equation.getValues()

            values.forEachIndexed { index, value ->
                if (index == 3) return@forEachIndexed // Skip equals signs

                val originalRow = if (equation.isHorizontal) equation.startRow else equation.startRow + index
                val originalCol = if (equation.isHorizontal) equation.startCol + index else equation.startCol

                val row = originalRow + rowOffset
                val col = originalCol + colOffset
                val cellKey = "${row},${col}"

                cells.add(CellEquationMapping(row, col, value, index, cellKey, eqIndex))

                if (!cellToEquations.containsKey(cellKey)) {
                    cellToEquations[cellKey] = mutableListOf()
                }
                cellToEquations[cellKey]!!.add(Pair(eqIndex, index))
            }

            equationCells[eqIndex] = cells
        }

        val intersectionCells = cellToEquations.filter { it.value.size > 1 }.keys.toSet()
        println("🔗 Found ${intersectionCells.size} intersection cells")

        var hiddenCount = 0
        val maxAttempts = 50

        repeat(maxAttempts) {
            val visibleCells = mutableListOf<CellEquationMapping>()

            equationCells.forEach { (eqIndex, cells) ->
                cells.forEach { cell ->
                    val gridCell = grid[cell.row][cell.col]
                    if (gridCell.isFixed && (gridCell.cellType == CellType.NUMBER || gridCell.cellType == CellType.OPERATOR)) {
                        visibleCells.add(cell.copy(isIntersection = intersectionCells.contains(cell.cellKey)))
                    }
                }
            }

            if (visibleCells.isEmpty()) return@repeat

            visibleCells.sortWith { a, b ->
                when {
                    a.isIntersection != b.isIntersection -> if (a.isIntersection) 1 else -1
                    else -> {
                        val aVal = a.value.toIntOrNull() ?: 0
                        val bVal = b.value.toIntOrNull() ?: 0
                        aVal.compareTo(bVal)
                    }
                }
            }

            var foundRemovable = false
            for (cell in visibleCells) {
                if (canRemoveCell(cell, equations, equationCells, grid, intersectionCells)) {
                    val gridCell = grid[cell.row][cell.col]
                    grid[cell.row][cell.col] = gridCell.copy(
                        value = "",
                        isFixed = false
                    )
                    hiddenCount++
                    foundRemovable = true
                    println("📝 Hidden cell at (${cell.row},${cell.col}) with value ${cell.value}")
                    break
                }
            }

            if (!foundRemovable) {
                println("✅ No more cells can be safely removed")
                return@repeat
            }
        }

        println("🎯 Smart removal complete: hidden $hiddenCount additional values")
    }

    private fun canRemoveCell(
        targetCell: CellEquationMapping,
        equations: List<CrosswordEquation>,
        equationCells: Map<Int, List<CellEquationMapping>>,
        grid: Array<Array<MathCrosswordCell>>,
        intersectionCells: Set<String>
    ): Boolean {

        val originalCell = grid[targetCell.row][targetCell.col]
        grid[targetCell.row][targetCell.col] = originalCell.copy(value = "", isFixed = false)

        var allSolvable = true

        equations.forEachIndexed { eqIndex, _ ->
            val cells = equationCells[eqIndex] ?: emptyList()
            var visibleValues = 0

            cells.forEach { cell ->
                val gridCell = grid[cell.row][cell.col]
                if (gridCell.isFixed && (gridCell.cellType == CellType.NUMBER || gridCell.cellType == CellType.OPERATOR)) {
                    visibleValues++
                }
            }

            if (visibleValues < 2) {
                allSolvable = false
            }
        }

        if (intersectionCells.contains(targetCell.cellKey)) {
            val affectedEquations = mutableSetOf<Int>()
            equationCells.forEach { (eqIndex, cells) ->
                if (cells.any { it.cellKey == targetCell.cellKey }) {
                    affectedEquations.add(eqIndex)
                }
            }

            for (eqIndex in affectedEquations) {
                val cells = equationCells[eqIndex] ?: continue
                var visibleValues = 0

                cells.forEach { cell ->
                    val gridCell = grid[cell.row][cell.col]
                    if (gridCell.isFixed && (gridCell.cellType == CellType.NUMBER || gridCell.cellType == CellType.OPERATOR)) {
                        visibleValues++
                    }
                }

                if (visibleValues < 2) {
                    allSolvable = false
                    break
                }
            }
        }

        grid[targetCell.row][targetCell.col] = originalCell

        return allSolvable
    }

    private fun updateOccupiedCells(equation: CrosswordEquation, occupiedCells: MutableMap<Pair<Int, Int>, String>) {
        val values = equation.getValues()
        equation.getCells().forEachIndexed { index, cell ->
            occupiedCells[cell] = values[index]
        }
    }

    private fun wouldConflict(equation: CrosswordEquation, occupiedCells: Map<Pair<Int, Int>, String>): Boolean {
        val values = equation.getValues()
        equation.getCells().forEachIndexed { index, cell ->
            val existingValue = occupiedCells[cell]
            if (existingValue != null && existingValue != values[index]) {
                return true
            }
        }
        return false
    }

    private fun wouldStayInBounds(equation: CrosswordEquation, currentBounds: CrosswordBounds): Boolean {
        val tempBounds = currentBounds.copy()
        tempBounds.update(equation)
        return tempBounds.isWithinLimits(12, 12)
    }

    private fun getFactors(number: Int): List<Int> {
        val factors = mutableListOf<Int>()
        for (i in 1..kotlin.math.sqrt(number.toDouble()).toInt()) {
            if (number % i == 0) {
                factors.add(i)
                if (i != number / i && number / i <= 20) {
                    factors.add(number / i)
                }
            }
        }
        return factors.filter { it > 1 && it <= 20 }
    }

    private fun calculateRequiredGridSize(equations: List<CrosswordEquation>): Int {
        if (equations.isEmpty()) return 9

        val bounds = CrosswordBounds()
        equations.forEach { bounds.update(it) }

        val width = bounds.getWidth() + 8
        val height = bounds.getHeight() + 8

        return kotlin.math.min(kotlin.math.max(kotlin.math.max(width, height), 9), 15)
    }

    private fun countIntersections(equations: List<CrosswordEquation>): Int {
        val cellUsage = mutableMapOf<Pair<Int, Int>, Int>()

        equations.forEach { equation ->
            equation.getCells().forEach { cell ->
                cellUsage[cell] = cellUsage.getOrDefault(cell, 0) + 1
            }
        }

        return cellUsage.count { it.value > 1 }
    }

    private fun calculateGridUtilization(grid: List<List<MathCrosswordCell>>): Int {
        val totalCells = grid.size * grid.size
        val usedCells = grid.flatten().count { it.cellType != CellType.BLOCKED }
        return ((usedCells.toDouble() / totalCells) * 100).toInt()
    }

    fun validateSolution(userGrid: List<List<String>>, correctAnswer: String): Boolean {
        try {
            val answerData = JSONObject(correctAnswer)
            val correctGrid = answerData.getJSONArray("correctGrid")

            for (row in 0 until userGrid.size) {
                val correctRow = correctGrid.getJSONArray(row)
                for (col in 0 until userGrid[row].size) {
                    val userValue = userGrid[row][col]
                    val correctValue = correctRow.getString(col)

                    if (userValue != correctValue && correctValue.isNotEmpty()) {
                        return false
                    }
                }
            }

            return true
        } catch (e: Exception) {
            return false
        }
    }
}