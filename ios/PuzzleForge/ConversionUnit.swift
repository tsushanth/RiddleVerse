import Foundation

// MARK: - Conversion Data Models
struct ConversionUnit {
    let name: String
    let factor: Double
    let precision: Int
}

struct ConversionCategory {
    let name: String
    let units: [ConversionUnit]
    let conversions: [ConversionPair]
}

struct ConversionPair {
    let fromUnit: String
    let toUnit: String
    let factor: Double
    let precision: Int
    let convertFunction: ((Double) -> Double)?
    
    init(fromUnit: String, toUnit: String, factor: Double, precision: Int = 1) {
        self.fromUnit = fromUnit
        self.toUnit = toUnit
        self.factor = factor
        self.precision = precision
        self.convertFunction = nil
    }
    
    init(fromUnit: String, toUnit: String, precision: Int = 1, convertFunction: @escaping (Double) -> Double) {
        self.fromUnit = fromUnit
        self.toUnit = toUnit
        self.factor = 1.0
        self.precision = precision
        self.convertFunction = convertFunction
    }
}

struct DifficultyRange {
    let min: Double
    let max: Double
}

// MARK: - Local Conversion Puzzle Generator
class LocalConversionPuzzleGenerator {
    
    private let conversionCategories: [ConversionCategory]
    private let difficultyRanges: [String: DifficultyRange]
    
    init() {
        // Initialize conversion data
        self.conversionCategories = Self.setupConversionCategories()
        self.difficultyRanges = [
            "Easy": DifficultyRange(min: 1, max: 100),
            "Medium": DifficultyRange(min: 50, max: 500),
            "Hard": DifficultyRange(min: 100, max: 1000),
            "Expert": DifficultyRange(min: 500, max: 5000)
        ]
    }
    
    // MARK: - Main Generation Function
    static func generateConversionPuzzle(difficulty: String) -> (String, String) {
        let generator = LocalConversionPuzzleGenerator()
        let puzzleData = generator.generatePuzzle(difficulty: difficulty)
        
        print("🔧 LOCAL CONVERSION: Generated puzzle data: \(puzzleData)")
        
        do {
            let jsonData = try JSONSerialization.data(withJSONObject: puzzleData)
            let jsonString = String(data: jsonData, encoding: .utf8) ?? ""
            
            // Return puzzle data as question and comparison result as answer
            let answer = puzzleData["comparison"] as? String ?? "equal"
            
            print("🔧 LOCAL CONVERSION: Final JSON: \(jsonString)")
            print("🔧 LOCAL CONVERSION: Answer: \(answer)")
            
            return (jsonString, answer)
        } catch {
            print("❌ Failed to serialize conversion puzzle data: \(error)")
            return ("", "")
        }
    }
    
    private func generatePuzzle(difficulty: String) -> [String: Any] {
        let range = difficultyRanges[difficulty] ?? difficultyRanges["Medium"]!
        
        // FIXED: Use shuffled() for better randomization
        let shuffledCategories = conversionCategories.shuffled()
        let category = shuffledCategories.first!
        let shuffledConversions = category.conversions.shuffled()
        let conversion = shuffledConversions.first!
        
        print("🔧 Selected category: \(category.name)")
        print("🔧 Selected conversion: \(conversion.fromUnit) → \(conversion.toUnit)")
        
        // Generate value1 within difficulty range with more variation
        let value1 = generateValueInRange(range: range, isTemperature: category.name == "temperature")
        
        // Calculate correct conversion
        let correctValue2 = calculateConversion(value: value1, conversion: conversion)
        
        print("🔧 Generated value1: \(value1) \(conversion.fromUnit)")
        print("🔧 Calculated correct value2: \(correctValue2) \(conversion.toUnit)")
        
        // FIXED: More random decision making - vary the probability
        let randomValue = Double.random(in: 0...1)
        let equalProbability = difficulty == "Easy" ? 0.6 : 0.4 // Easier puzzles more likely to be equal
        let isEqual = randomValue < equalProbability
        
        let value2: Double
        let comparison: String
        
        if isEqual {
            value2 = correctValue2
            comparison = "equal"
            print("🔧 Making puzzle EQUAL: \(value1) \(conversion.fromUnit) = \(value2) \(conversion.toUnit)")
        } else {
            // FIXED: More varied error generation
            let errorPercents = [15.0, 20.0, 25.0, 30.0, 35.0, 40.0]
            let errorPercent = errorPercents.randomElement()!
            let direction = Bool.random() ? 1.0 : -1.0
            
            var incorrectValue2 = correctValue2 * (1 + (direction * errorPercent / 100))
            incorrectValue2 = roundToPrecision(incorrectValue2, precision: conversion.precision)
            
            // Ensure it's actually different
            if abs(incorrectValue2 - correctValue2) < 0.1 {
                incorrectValue2 = correctValue2 + (direction * max(1, correctValue2 * 0.3))
                incorrectValue2 = roundToPrecision(incorrectValue2, precision: conversion.precision)
            }
            
            value2 = incorrectValue2
            comparison = "not equal"
            print("🔧 Making puzzle NOT EQUAL: \(value1) \(conversion.fromUnit) ≠ \(value2) \(conversion.toUnit)")
            print("🔧 Correct would be: \(correctValue2) \(conversion.toUnit)")
        }
        
        // FIXED: Ensure proper integer conversion for display
        let finalValue1 = Int(value1)
        let finalValue2 = conversion.precision == 0 ? Double(value2) : value2
        
        let result: [String: Any] = [
            "value1": finalValue1,
            "unit1": conversion.fromUnit,
            "value2": finalValue2,
            "unit2": conversion.toUnit,
            "comparison": comparison,
            "difficulty": difficulty,
            "hint": generateHint(for: conversion, value1: finalValue1, value2: finalValue2, isEqual: isEqual)
        ]
        
        print("🔧 Final puzzle data: \(result)")
        return result
    }

    // FIXED: Add hint generation method
    private func generateHint(for conversion: ConversionPair, value1: Int, value2: Any, isEqual: Bool) -> String {
        if isEqual {
            return "Convert \(value1) \(conversion.fromUnit) to \(conversion.toUnit) and compare"
        } else {
            return "Check if \(value1) \(conversion.fromUnit) equals \(value2) \(conversion.toUnit)"
        }
    }

    // FIXED: Improve value generation for more variety
    private func generateValueInRange(range: DifficultyRange, isTemperature: Bool) -> Double {
        if isTemperature {
            // For temperature, use wider range of values
            let minTemp = Int(range.min)
            let maxTemp = Int(range.max)
            let temperatures = Array(stride(from: minTemp, through: maxTemp, by: 5))
            return Double(temperatures.randomElement() ?? minTemp)
        } else {
            // For other units, create more varied values
            let useDecimal = Double.random(in: 0...1) < 0.3
            if useDecimal {
                return Double.random(in: range.min...range.max).rounded(toPlaces: 1)
            } else {
                // Use specific "nice" numbers that are common in real life
                let niceNumbers = generateNiceNumbers(in: range)
                return Double(niceNumbers.randomElement() ?? Int(range.min))
            }
        }
    }

    // FIXED: Generate more realistic numbers
    private func generateNiceNumbers(in range: DifficultyRange) -> [Int] {
        let min = Int(range.min)
        let max = Int(range.max)
        
        var niceNumbers: [Int] = []
        
        // Add multiples of 5, 10, 25
        for multiplier in [5, 10, 25] {
            let start = (min / multiplier) * multiplier
            let end = ((max / multiplier) + 1) * multiplier
            
            for value in stride(from: start, through: end, by: multiplier) {
                if value >= min && value <= max {
                    niceNumbers.append(value)
                }
            }
        }
        
        // Add some random numbers for variety
        for _ in 0..<10 {
            niceNumbers.append(Int.random(in: min...max))
        }
        
        return Array(Set(niceNumbers)).sorted() // Remove duplicates
    }
    
    private func calculateConversion(value: Double, conversion: ConversionPair) -> Double {
        if let convertFn = conversion.convertFunction {
            return roundToPrecision(convertFn(value), precision: conversion.precision)
        } else {
            return roundToPrecision(value * conversion.factor, precision: conversion.precision)
        }
    }
    
    private func roundToPrecision(_ value: Double, precision: Int) -> Double {
        let factor = pow(10.0, Double(precision))
        return (value * factor).rounded() / factor
    }
    
    // MARK: - Conversion Categories Setup
    private static func setupConversionCategories() -> [ConversionCategory] {
        return [
            // Distance conversions
            ConversionCategory(
                name: "distance",
                units: [],
                conversions: [
                    ConversionPair(fromUnit: "miles", toUnit: "kilometers", factor: 1.60934, precision: 1),
                    ConversionPair(fromUnit: "kilometers", toUnit: "miles", factor: 0.621371, precision: 1),
                    ConversionPair(fromUnit: "feet", toUnit: "meters", factor: 0.3048, precision: 1),
                    ConversionPair(fromUnit: "meters", toUnit: "feet", factor: 3.28084, precision: 0),
                    ConversionPair(fromUnit: "inches", toUnit: "centimeters", factor: 2.54, precision: 1),
                    ConversionPair(fromUnit: "centimeters", toUnit: "inches", factor: 0.393701, precision: 1),
                    ConversionPair(fromUnit: "yards", toUnit: "meters", factor: 0.9144, precision: 1),
                    ConversionPair(fromUnit: "meters", toUnit: "yards", factor: 1.09361, precision: 1)
                ]
            ),
            
            // Weight conversions
            ConversionCategory(
                name: "weight",
                units: [],
                conversions: [
                    ConversionPair(fromUnit: "pounds", toUnit: "kilograms", factor: 0.453592, precision: 1),
                    ConversionPair(fromUnit: "kilograms", toUnit: "pounds", factor: 2.20462, precision: 1),
                    ConversionPair(fromUnit: "ounces", toUnit: "grams", factor: 28.3495, precision: 0),
                    ConversionPair(fromUnit: "grams", toUnit: "ounces", factor: 0.035274, precision: 2),
                    ConversionPair(fromUnit: "tons", toUnit: "kilograms", factor: 1000, precision: 0),
                    ConversionPair(fromUnit: "kilograms", toUnit: "tons", factor: 0.001, precision: 3)
                ]
            ),
            
            // Volume conversions
            ConversionCategory(
                name: "volume",
                units: [],
                conversions: [
                    ConversionPair(fromUnit: "gallons", toUnit: "liters", factor: 3.78541, precision: 1),
                    ConversionPair(fromUnit: "liters", toUnit: "gallons", factor: 0.264172, precision: 2),
                    ConversionPair(fromUnit: "cups", toUnit: "milliliters", factor: 236.588, precision: 0),
                    ConversionPair(fromUnit: "milliliters", toUnit: "cups", factor: 0.00422675, precision: 2),
                    ConversionPair(fromUnit: "quarts", toUnit: "liters", factor: 0.946353, precision: 2),
                    ConversionPair(fromUnit: "liters", toUnit: "quarts", factor: 1.05669, precision: 2)
                ]
            ),
            
            // Temperature conversions
            ConversionCategory(
                name: "temperature",
                units: [],
                conversions: [
                    ConversionPair(
                        fromUnit: "Fahrenheit",
                        toUnit: "Celsius",
                        precision: 1,
                        convertFunction: { fahrenheit in
                            return (fahrenheit - 32) * 5/9
                        }
                    ),
                    ConversionPair(
                        fromUnit: "Celsius",
                        toUnit: "Fahrenheit",
                        precision: 1,
                        convertFunction: { celsius in
                            return celsius * 9/5 + 32
                        }
                    )
                ]
            )
        ]
    }
}

// MARK: - Extension for Double rounding
extension Double {
    func rounded(toPlaces places: Int) -> Double {
        let divisor = pow(10.0, Double(places))
        return (self * divisor).rounded() / divisor
    }
}
