//
//  PinballDeflectorGenerator.swift
//  PuzzleForge
//
//  Created by Assistant on 1/15/25.
//

import Foundation

/**
 * Local generator for Pinball Deflector puzzles
 * Generates matrix with deflectors that change ball direction by 90 degrees
 * User must predict where ball will end up after deflections
 */
class PinballDeflectorGenerator {
    
    // Deflector orientation constants
    struct DeflectorTypes {
        static let empty = 0
        static let slash = 1      // / deflector
        static let backslash = 2  // \ deflector
    }
    
    // Direction data structure
    struct Direction {
        let dr: Int
        let dc: Int
        let name: String
        
        func copy() -> Direction {
            return Direction(dr: dr, dc: dc, name: name)
        }
    }
    
    // Predefined directions
    static let directions: [String: Direction] = [
        "UP": Direction(dr: -1, dc: 0, name: "UP"),
        "DOWN": Direction(dr: 1, dc: 0, name: "DOWN"),
        "LEFT": Direction(dr: 0, dc: -1, name: "LEFT"),
        "RIGHT": Direction(dr: 0, dc: 1, name: "RIGHT")
    ]
    
    struct DifficultyConfig {
        let matrixSize: Int
        let deflectorCount: Int
        let minActiveDeflectors: Int
        let memoryTime: Int
        let maxBounces: Int
    }
    
    struct BallPathStep {
        let position: [Int]
        let direction: Direction
        let deflected: Bool
        let deflectorType: Int?
    }
    
    struct ActiveDeflector {
        let position: [Int]
        let type: Int
        let oldDirection: Direction
        let newDirection: Direction
    }
    
    private static let difficultyConfig: [String: DifficultyConfig] = [
        "easy": DifficultyConfig(
            matrixSize: 5,
            deflectorCount: 3,
            minActiveDeflectors: 2,
            memoryTime: 4000,
            maxBounces: 8
        ),
        "medium": DifficultyConfig(
            matrixSize: 6,
            deflectorCount: 4,
            minActiveDeflectors: 3,
            memoryTime: 5000,
            maxBounces: 10
        ),
        "hard": DifficultyConfig(
            matrixSize: 7,
            deflectorCount: 5,
            minActiveDeflectors: 3,
            memoryTime: 6000,
            maxBounces: 12
        )
    ]
    
    /**
     * Generate a complete Pinball Deflector puzzle
     */
    static func generatePuzzle(difficulty: String = "easy") -> (String, String) {
        let config = difficultyConfig[difficulty.lowercased()] ?? difficultyConfig["easy"]!
        
        print("🎯 Generating \(difficulty) pinball deflector puzzle")
        
        // Generate puzzle with valid ball path
        let puzzleResult = generatePuzzleWithPath(config: config)
        
        guard puzzleResult.success,
              let matrix = puzzleResult.matrix,
              let startPosition = puzzleResult.startPosition,
              let startDirection = puzzleResult.startDirection,
              let ballPath = puzzleResult.ballPath,
              let endPosition = puzzleResult.endPosition,
              let activeDeflectors = puzzleResult.activeDeflectors else {
            fatalError(puzzleResult.error ?? "Failed to generate valid puzzle")
        }
        
        // Create instructions
        let instructions: [String: Any] = [
            "title": "Pinball Deflector Puzzle",
            "description": "Predict where the ball will end up after hitting deflectors!",
            "steps": [
                "1. Memorize the deflector positions and orientations",
                "2. Deflectors will disappear after the timer",
                "3. A ball will appear at the start position",
                "4. Predict where the ball will end up after all deflections",
                "5. Tap the predicted end position"
            ],
            "tip": "You have \(config.memoryTime / 1000) seconds to memorize the \(config.deflectorCount) deflectors"
        ]
        
        // Build question JSON
        let questionData: [String: Any] = [
            "matrix": matrix,
            "matrixSize": config.matrixSize,
            "startPosition": startPosition,
            "startDirection": [
                "dr": startDirection.dr,
                "dc": startDirection.dc,
                "name": startDirection.name
            ],
            "memoryTime": config.memoryTime,
            "instructions": instructions,
            "difficulty": difficulty
        ]
        
        // Build answer JSON
        let answerData: [String: Any] = [
            "endPosition": endPosition,
            "ballPath": ballPath.map { step in
                [
                    "position": step.position,
                    "direction": [
                        "dr": step.direction.dr,
                        "dc": step.direction.dc,
                        "name": step.direction.name
                    ],
                    "deflected": step.deflected,
                    "deflectorType": step.deflectorType as Any
                ]
            },
            "activeDeflectors": activeDeflectors.map { deflector in
                [
                    "position": deflector.position,
                    "type": deflector.type,
                    "oldDirection": [
                        "dr": deflector.oldDirection.dr,
                        "dc": deflector.oldDirection.dc,
                        "name": deflector.oldDirection.name
                    ],
                    "newDirection": [
                        "dr": deflector.newDirection.dr,
                        "dc": deflector.newDirection.dc,
                        "name": deflector.newDirection.name
                    ]
                ]
            },
            "scoring": [
                "correctEndBonus": 70,
                "pathAccuracyBonus": 30,
                "maxPoints": 100
            ]
        ]
        
        let questionJson = buildJsonString(data: questionData)
        let answerJson = buildJsonString(data: answerData)
        
        print("✅ Generated \(config.matrixSize)x\(config.matrixSize) matrix with \(countDeflectors(matrix: matrix)) deflectors")
        print("🎯 Ball path: \(ballPath.count) steps, \(ballPath.filter { $0.deflected }.count) bounces")
        print("📍 Start: [\(startPosition[0]),\(startPosition[1])] \(startDirection.name), End: [\(endPosition[0]),\(endPosition[1])]")
        
        return (questionJson, answerJson)
    }
    
    /**
     * Generate puzzle ensuring ball hits minimum deflectors
     */
    private static func generatePuzzleWithPath(config: DifficultyConfig) -> PuzzleResult {
        var attempts = 0
        let maxAttempts = 50
        
        while attempts < maxAttempts {
            attempts += 1
            
            // Create matrix with deflectors
            var matrix = createEmptyMatrix(size: config.matrixSize)
            let startResult = selectRandomStartPosition(matrixSize: config.matrixSize)
            
            guard startResult.success,
                  let startPosition = startResult.position,
                  let startDirection = startResult.direction else {
                continue
            }
            
            // Place deflectors strategically
            let deflectorResult = placeStrategicDeflectors(
                matrix: &matrix,
                startPosition: startPosition,
                startDirection: startDirection,
                config: config
            )
            
            guard deflectorResult.success else {
                continue
            }
            
            // Simulate ball path
            let pathResult = simulateBallPath(
                matrix: matrix,
                startPosition: startPosition,
                startDirection: startDirection,
                maxBounces: config.maxBounces
            )
            
            guard pathResult.success,
                  let ballPath = pathResult.path,
                  let endPosition = pathResult.endPosition,
                  let activeDeflectors = pathResult.activeDeflectors else {
                continue
            }
            
            // Check if minimum deflectors were hit
            if activeDeflectors.count >= config.minActiveDeflectors {
                print("✅ Valid puzzle generated on attempt \(attempts)")
                return PuzzleResult(
                    success: true,
                    matrix: matrix,
                    startPosition: startPosition,
                    startDirection: startDirection,
                    ballPath: ballPath,
                    endPosition: endPosition,
                    activeDeflectors: activeDeflectors
                )
            }
        }
        
        return PuzzleResult(
            success: false,
            error: "Could not generate valid puzzle after \(maxAttempts) attempts"
        )
    }
    
    /**
     * Create empty matrix
     */
    private static func createEmptyMatrix(size: Int) -> [[Int]] {
        return Array(repeating: Array(repeating: DeflectorTypes.empty, count: size), count: size)
    }
    
    /**
     * Select random start position on matrix edge
     */
    private static func selectRandomStartPosition(matrixSize: Int) -> StartPositionResult {
        var edges: [([Int], Direction)] = []
        
        // Top edge (going down)
        for i in 0..<matrixSize {
            edges.append(([0, i], directions["DOWN"]!))
        }
        
        // Bottom edge (going up)
        for i in 0..<matrixSize {
            edges.append(([matrixSize - 1, i], directions["UP"]!))
        }
        
        // Left edge (going right)
        for i in 0..<matrixSize {
            edges.append(([i, 0], directions["RIGHT"]!))
        }
        
        // Right edge (going left)
        for i in 0..<matrixSize {
            edges.append(([i, matrixSize - 1], directions["LEFT"]!))
        }
        
        let randomEdge = edges.randomElement()!
        return StartPositionResult(
            success: true,
            position: randomEdge.0,
            direction: randomEdge.1
        )
    }
    
    /**
     * Place deflectors strategically in ball's path
     */
    private static func placeStrategicDeflectors(
        matrix: inout [[Int]],
        startPosition: [Int],
        startDirection: Direction,
        config: DifficultyConfig
    ) -> PlacementResult {
        let matrixSize = config.matrixSize
        let deflectorCount = config.deflectorCount
        let minActiveDeflectors = config.minActiveDeflectors
        
        var currentPos = startPosition
        var currentDir = startDirection.copy()
        var deflatorsPlaced = 0
        
        // Simulate initial path to place first deflector
        while deflatorsPlaced < min(deflectorCount, minActiveDeflectors) {
            // Move in current direction to find placement spot
            let nextPos = [
                currentPos[0] + currentDir.dr,
                currentPos[1] + currentDir.dc
            ]
            
            // Check bounds
            if nextPos[0] < 0 || nextPos[0] >= matrixSize ||
               nextPos[1] < 0 || nextPos[1] >= matrixSize {
                break
            }
            
            // Skip if too close to start (first 1-2 cells)
            let distanceFromStart = abs(nextPos[0] - startPosition[0]) + abs(nextPos[1] - startPosition[1])
            if distanceFromStart <= 1 {
                currentPos = nextPos
                continue
            }
            
            // Place deflector with some probability
            if Double.random(in: 0...1) < 0.4 && matrix[nextPos[0]][nextPos[1]] == DeflectorTypes.empty {
                // Randomly choose deflector type
                let deflectorType = Bool.random() ? DeflectorTypes.slash : DeflectorTypes.backslash
                matrix[nextPos[0]][nextPos[1]] = deflectorType
                deflatorsPlaced += 1
                
                // Update direction based on deflector
                currentDir = getNewDirection(currentDirection: currentDir, deflectorType: deflectorType)
                print("🔧 Placed deflector \(deflectorType) at [\(nextPos[0]),\(nextPos[1])], new direction: \(currentDir.name)")
            }
            
            currentPos = nextPos
        }
        
        // Place remaining deflectors randomly
        while deflatorsPlaced < deflectorCount {
            let row = Int.random(in: 0..<matrixSize)
            let col = Int.random(in: 0..<matrixSize)
            
            if matrix[row][col] == DeflectorTypes.empty {
                let deflectorType = Bool.random() ? DeflectorTypes.slash : DeflectorTypes.backslash
                matrix[row][col] = deflectorType
                deflatorsPlaced += 1
            }
        }
        
        return PlacementResult(success: true)
    }
    
    /**
     * Simulate ball path through matrix
     */
    private static func simulateBallPath(
        matrix: [[Int]],
        startPosition: [Int],
        startDirection: Direction,
        maxBounces: Int
    ) -> PathResult {
        let matrixSize = matrix.count
        var path: [BallPathStep] = []
        var activeDeflectors: [ActiveDeflector] = []
        
        var currentPos = startPosition
        var currentDir = startDirection.copy()
        var bounces = 0
        
        // Add start position
        path.append(BallPathStep(
            position: currentPos,
            direction: currentDir.copy(),
            deflected: false,
            deflectorType: nil
        ))
        
        while bounces < maxBounces {
            // Calculate next position
            let nextPos = [
                currentPos[0] + currentDir.dr,
                currentPos[1] + currentDir.dc
            ]
            
            // Check if out of bounds
            if nextPos[0] < 0 || nextPos[0] >= matrixSize ||
               nextPos[1] < 0 || nextPos[1] >= matrixSize {
                break
            }
            
            currentPos = nextPos
            let cellValue = matrix[currentPos[0]][currentPos[1]]
            
            // Check for deflector
            if cellValue == DeflectorTypes.slash || cellValue == DeflectorTypes.backslash {
                // Ball hits deflector
                let newDirection = getNewDirection(currentDirection: currentDir, deflectorType: cellValue)
                
                path.append(BallPathStep(
                    position: currentPos,
                    direction: currentDir.copy(),
                    deflected: true,
                    deflectorType: cellValue
                ))
                
                activeDeflectors.append(ActiveDeflector(
                    position: currentPos,
                    type: cellValue,
                    oldDirection: currentDir.copy(),
                    newDirection: newDirection.copy()
                ))
                
                currentDir = newDirection
                bounces += 1
            } else {
                // Empty cell
                path.append(BallPathStep(
                    position: currentPos,
                    direction: currentDir.copy(),
                    deflected: false,
                    deflectorType: nil
                ))
            }
        }
        
        let endPosition = path.last?.position ?? currentPos
        
        return PathResult(
            success: true,
            path: path,
            endPosition: endPosition,
            activeDeflectors: activeDeflectors
        )
    }
    
    /**
     * Calculate new direction after hitting deflector - MATCHES ANDROID EXACTLY
     */
    private static func getNewDirection(currentDirection: Direction, deflectorType: Int) -> Direction {
        switch deflectorType {
        case DeflectorTypes.slash: // Slash / deflector - EXACTLY like Android
            switch currentDirection.name {
            case "UP":
                return directions["RIGHT"]!
            case "RIGHT":
                return directions["UP"]!
            case "DOWN":
                return directions["LEFT"]!
            case "LEFT":
                return directions["DOWN"]!
            default:
                return getDirectionFromComponents(dr: -currentDirection.dc, dc: -currentDirection.dr)
            }
        case DeflectorTypes.backslash: // Backslash \ deflector - EXACTLY like Android
            switch currentDirection.name {
            case "UP":
                return directions["LEFT"]!
            case "LEFT":
                return directions["UP"]!
            case "DOWN":
                return directions["RIGHT"]!
            case "RIGHT":
                return directions["DOWN"]!
            default:
                return getDirectionFromComponents(dr: currentDirection.dc, dc: currentDirection.dr)
            }
        default:
            return currentDirection // No change
        }
    }
    
    /**
     * Get direction object from components
     */
    private static func getDirectionFromComponents(dr: Int, dc: Int) -> Direction {
        switch (dr, dc) {
        case (-1, 0):
            return directions["UP"]!
        case (1, 0):
            return directions["DOWN"]!
        case (0, -1):
            return directions["LEFT"]!
        case (0, 1):
            return directions["RIGHT"]!
        default:
            return Direction(dr: dr, dc: dc, name: "CUSTOM_\(dr)_\(dc)")
        }
    }
    
    /**
     * Count deflectors in matrix
     */
    private static func countDeflectors(matrix: [[Int]]) -> Int {
        return matrix.flatMap { $0 }.filter { cell in
            cell == DeflectorTypes.slash || cell == DeflectorTypes.backslash
        }.count
    }
    
    /**
     * Build JSON string from nested data structures
     */
    private static func buildJsonString(data: Any) -> String {
        do {
            let jsonData = try JSONSerialization.data(withJSONObject: data, options: [])
            return String(data: jsonData, encoding: .utf8) ?? "{}"
        } catch {
            print("Error building JSON: \(error)")
            return "{}"
        }
    }
    
    // Helper data structures
    struct PuzzleResult {
        let success: Bool
        let matrix: [[Int]]?
        let startPosition: [Int]?
        let startDirection: Direction?
        let ballPath: [BallPathStep]?
        let endPosition: [Int]?
        let activeDeflectors: [ActiveDeflector]?
        let error: String?
        
        init(success: Bool, matrix: [[Int]]? = nil, startPosition: [Int]? = nil,
             startDirection: Direction? = nil, ballPath: [BallPathStep]? = nil,
             endPosition: [Int]? = nil, activeDeflectors: [ActiveDeflector]? = nil,
             error: String? = nil) {
            self.success = success
            self.matrix = matrix
            self.startPosition = startPosition
            self.startDirection = startDirection
            self.ballPath = ballPath
            self.endPosition = endPosition
            self.activeDeflectors = activeDeflectors
            self.error = error
        }
    }
    
    struct StartPositionResult {
        let success: Bool
        let position: [Int]?
        let direction: Direction?
    }
    
    struct PlacementResult {
        let success: Bool
    }
    
    struct PathResult {
        let success: Bool
        let path: [BallPathStep]?
        let endPosition: [Int]?
        let activeDeflectors: [ActiveDeflector]?
    }
}
