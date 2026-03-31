//
//  DifficultyLevel.swift
//  PuzzleForge
//
//  Created by Sushanth Tiruvaipati on 8/17/25.
//


// Unified DifficultyLevel.swift
// Shared difficulty system across all puzzle types

import Foundation

struct DifficultyLevel {
    let index: Int
    let name: String
    let timeLimit: Int
    let livesAllowed: Int
    let basePoints: Int
    
    // Puzzle-specific configurations
    let puzzleConfigs: PuzzleTypeConfigs
    
    struct PuzzleTypeConfigs {
        // Dual Card specific
        let dualCard: DualCardConfig
        
        // Crypto specific  
        let crypto: CryptoConfig
        
        // Pattern Memory specific
        let patternMemory: PatternMemoryConfig
        
        // Word Chain specific
        let wordChain: WordChainConfig
        
        // Logic Grid specific
        let logicGrid: LogicGridConfig
        
        // Spatial Reasoning specific
        let spatialReasoning: SpatialReasoningConfig
        
        struct DualCardConfig {
            let totalRounds: Int
            let complexityLevel: Int
            let switchingPattern: String // "alternating", "random", "predictable"
            let cardComplexity: Int
        }
        
        struct CryptoConfig {
            let autoRevealAllInstances: Bool
            let lockedPositionsRatio: Float
            let dependencyChainLength: Int
            let hintRevealCount: Int
            let description: String
        }
        
        struct PatternMemoryConfig {
            let gridSize: Int
            let sequenceLength: Int
            let showTime: TimeInterval
            let complexPatterns: Bool
            let multiColor: Bool
        }
        
        struct WordChainConfig {
            let minWordLength: Int
            let maxWordLength: Int
            let allowedCategories: [String]
            let timePerWord: TimeInterval
            let hintSystem: Bool
        }
        
        struct LogicGridConfig {
            let gridSize: (width: Int, height: Int)
            let maxClues: Int
            let allowDeduction: Bool
            let complexConstraints: Bool
        }
        
        struct SpatialReasoningConfig {
            let rotationSteps: Int
            let allowMirror: Bool
            let multipleShapes: Bool
            let dimensionality: Int // 2D vs 3D
        }
    }
}

// MARK: - Difficulty Level Factory
extension DifficultyLevel {
    
    static func create(index: Int) -> DifficultyLevel {
        switch index {
        case 0: return createBeginner()
        case 1: return createEasy()
        case 2: return createMedium()
        case 3: return createHard()
        case 4: return createExpert()
        default: return createMedium()
        }
    }
    
    static func create(name: String) -> DifficultyLevel {
        switch name.lowercased() {
        case "beginner": return createBeginner()
        case "easy": return createEasy()
        case "medium": return createMedium()
        case "hard": return createHard()
        case "expert": return createExpert()
        default: return createMedium()
        }
    }
    
    private static func createBeginner() -> DifficultyLevel {
        return DifficultyLevel(
            index: 0,
            name: "Beginner",
            timeLimit: 300, // 5 minutes
            livesAllowed: 5,
            basePoints: 100,
            puzzleConfigs: PuzzleTypeConfigs(
                dualCard: .init(
                    totalRounds: 8,
                    complexityLevel: 1,
                    switchingPattern: "predictable",
                    cardComplexity: 1
                ),
                crypto: .init(
                    autoRevealAllInstances: true,
                    lockedPositionsRatio: 0.0,
                    dependencyChainLength: 0,
                    hintRevealCount: 40,
                    description: "All instances revealed automatically"
                ),
                patternMemory: .init(
                    gridSize: 3,
                    sequenceLength: 3,
                    showTime: 2.0,
                    complexPatterns: false,
                    multiColor: false
                ),
                wordChain: .init(
                    minWordLength: 3,
                    maxWordLength: 5,
                    allowedCategories: ["animals", "colors"],
                    timePerWord: 10.0,
                    hintSystem: true
                ),
                logicGrid: .init(
                    gridSize: (width: 3, height: 3),
                    maxClues: 6,
                    allowDeduction: true,
                    complexConstraints: false
                ),
                spatialReasoning: .init(
                    rotationSteps: 2,
                    allowMirror: false,
                    multipleShapes: false,
                    dimensionality: 2
                )
            )
        )
    }
    
    private static func createEasy() -> DifficultyLevel {
        return DifficultyLevel(
            index: 1,
            name: "Easy",
            timeLimit: 240, // 4 minutes
            livesAllowed: 4,
            basePoints: 150,
            puzzleConfigs: PuzzleTypeConfigs(
                dualCard: .init(
                    totalRounds: 10,
                    complexityLevel: 2,
                    switchingPattern: "alternating",
                    cardComplexity: 2
                ),
                crypto: .init(
                    autoRevealAllInstances: true,
                    lockedPositionsRatio: 0.1,
                    dependencyChainLength: 1,
                    hintRevealCount: 30,
                    description: "Auto-reveal with some locked positions"
                ),
                patternMemory: .init(
                    gridSize: 3,
                    sequenceLength: 4,
                    showTime: 1.8,
                    complexPatterns: false,
                    multiColor: true
                ),
                wordChain: .init(
                    minWordLength: 3,
                    maxWordLength: 6,
                    allowedCategories: ["animals", "colors", "food"],
                    timePerWord: 8.0,
                    hintSystem: true
                ),
                logicGrid: .init(
                    gridSize: (width: 4, height: 3),
                    maxClues: 8,
                    allowDeduction: true,
                    complexConstraints: false
                ),
                spatialReasoning: .init(
                    rotationSteps: 4,
                    allowMirror: false,
                    multipleShapes: false,
                    dimensionality: 2
                )
            )
        )
    }
    
    private static func createMedium() -> DifficultyLevel {
        return DifficultyLevel(
            index: 2,
            name: "Medium",
            timeLimit: 180, // 3 minutes
            livesAllowed: 3,
            basePoints: 200,
            puzzleConfigs: PuzzleTypeConfigs(
                dualCard: .init(
                    totalRounds: 15,
                    complexityLevel: 3,
                    switchingPattern: "alternating",
                    cardComplexity: 3
                ),
                crypto: .init(
                    autoRevealAllInstances: false, // Major difficulty jump
                    lockedPositionsRatio: 0.15,
                    dependencyChainLength: 2,
                    hintRevealCount: 25,
                    description: "Single letter reveal mode"
                ),
                patternMemory: .init(
                    gridSize: 4,
                    sequenceLength: 5,
                    showTime: 1.5,
                    complexPatterns: true,
                    multiColor: true
                ),
                wordChain: .init(
                    minWordLength: 4,
                    maxWordLength: 7,
                    allowedCategories: ["animals", "colors", "food", "objects"],
                    timePerWord: 6.0,
                    hintSystem: false
                ),
                logicGrid: .init(
                    gridSize: (width: 4, height: 4),
                    maxClues: 10,
                    allowDeduction: false,
                    complexConstraints: true
                ),
                spatialReasoning: .init(
                    rotationSteps: 4,
                    allowMirror: true,
                    multipleShapes: true,
                    dimensionality: 2
                )
            )
        )
    }
    
    private static func createHard() -> DifficultyLevel {
        return DifficultyLevel(
            index: 3,
            name: "Hard",
            timeLimit: 120, // 2 minutes
            livesAllowed: 2,
            basePoints: 300,
            puzzleConfigs: PuzzleTypeConfigs(
                dualCard: .init(
                    totalRounds: 20,
                    complexityLevel: 4,
                    switchingPattern: "random",
                    cardComplexity: 4
                ),
                crypto: .init(
                    autoRevealAllInstances: false,
                    lockedPositionsRatio: 0.25,
                    dependencyChainLength: 3,
                    hintRevealCount: 15,
                    description: "Single reveal with dependency chains"
                ),
                patternMemory: .init(
                    gridSize: 5,
                    sequenceLength: 6,
                    showTime: 1.2,
                    complexPatterns: true,
                    multiColor: true
                ),
                wordChain: .init(
                    minWordLength: 5,
                    maxWordLength: 8,
                    allowedCategories: ["all"],
                    timePerWord: 4.0,
                    hintSystem: false
                ),
                logicGrid: .init(
                    gridSize: (width: 5, height: 5),
                    maxClues: 12,
                    allowDeduction: false,
                    complexConstraints: true
                ),
                spatialReasoning: .init(
                    rotationSteps: 8,
                    allowMirror: true,
                    multipleShapes: true,
                    dimensionality: 3
                )
            )
        )
    }
    
    private static func createExpert() -> DifficultyLevel {
        return DifficultyLevel(
            index: 4,
            name: "Expert",
            timeLimit: 90, // 1.5 minutes
            livesAllowed: 2,
            basePoints: 500,
            puzzleConfigs: PuzzleTypeConfigs(
                dualCard: .init(
                    totalRounds: 25,
                    complexityLevel: 5,
                    switchingPattern: "random",
                    cardComplexity: 5
                ),
                crypto: .init(
                    autoRevealAllInstances: false,
                    lockedPositionsRatio: 0.35,
                    dependencyChainLength: 4,
                    hintRevealCount: 10,
                    description: "Maximum challenge with complex dependencies"
                ),
                patternMemory: .init(
                    gridSize: 6,
                    sequenceLength: 8,
                    showTime: 1.0,
                    complexPatterns: true,
                    multiColor: true
                ),
                wordChain: .init(
                    minWordLength: 6,
                    maxWordLength: 10,
                    allowedCategories: ["all"],
                    timePerWord: 3.0,
                    hintSystem: false
                ),
                logicGrid: .init(
                    gridSize: (width: 6, height: 6),
                    maxClues: 15,
                    allowDeduction: false,
                    complexConstraints: true
                ),
                spatialReasoning: .init(
                    rotationSteps: 8,
                    allowMirror: true,
                    multipleShapes: true,
                    dimensionality: 3
                )
            )
        )
    }
}

// MARK: - Convenience Accessors
extension DifficultyLevel {
    
    // Dual Card specific accessors
    var dualCardConfig: PuzzleTypeConfigs.DualCardConfig {
        return puzzleConfigs.dualCard
    }
    
    // Crypto specific accessors
    var cryptoConfig: PuzzleTypeConfigs.CryptoConfig {
        return puzzleConfigs.crypto
    }
    
    // Pattern Memory specific accessors
    var patternMemoryConfig: PuzzleTypeConfigs.PatternMemoryConfig {
        return puzzleConfigs.patternMemory
    }
    
    // Word Chain specific accessors
    var wordChainConfig: PuzzleTypeConfigs.WordChainConfig {
        return puzzleConfigs.wordChain
    }
    
    // Logic Grid specific accessors
    var logicGridConfig: PuzzleTypeConfigs.LogicGridConfig {
        return puzzleConfigs.logicGrid
    }
    
    // Spatial Reasoning specific accessors
    var spatialReasoningConfig: PuzzleTypeConfigs.SpatialReasoningConfig {
        return puzzleConfigs.spatialReasoning
    }
}

// MARK: - Migration Helper for Existing Code
extension DifficultyLevel {
    
    // For backward compatibility with existing puzzle implementations
    static func getDifficultyLevel(for difficulty: String) -> DifficultyLevel {
        return create(name: difficulty)
    }
    
    static func getDifficultyLevel(for index: Int) -> DifficultyLevel {
        return create(index: index)
    }
}

// MARK: - Usage Examples
/*
 
// In DualCardPuzzleView:
let difficulty = DifficultyLevel.create(name: "Medium")
let config = difficulty.dualCardConfig
let totalRounds = config.totalRounds
let switchingPattern = config.switchingPattern

// In CryptoPuzzleView:
let difficulty = DifficultyLevel.create(index: 2)
let config = difficulty.cryptoConfig
let autoReveal = config.autoRevealAllInstances
let lockRatio = config.lockedPositionsRatio

// In future puzzle types:
let difficulty = DifficultyLevel.create(name: "Hard")
let patternConfig = difficulty.patternMemoryConfig
let gridSize = patternConfig.gridSize
let sequenceLength = patternConfig.sequenceLength

*/