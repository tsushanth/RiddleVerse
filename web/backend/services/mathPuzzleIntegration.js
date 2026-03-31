// services/mathPuzzleIntegration.js
// Simple integration file to connect your existing mathPuzzleGenerators.js

import { MathPuzzleGenerators } from './mathPuzzleGenerators.js';
import { PUZZLE_TYPES } from '../config/puzzleConfig.js';

let mathGeneratorInstance = null;

function getMathGenerator() {
    if (!mathGeneratorInstance) {
        mathGeneratorInstance = new MathPuzzleGenerators();
    }
    return mathGeneratorInstance;
}


export async function generateRandomizedMathPuzzle(puzzleType, difficulty) {
    difficulty = difficulty?.charAt(0).toUpperCase() + difficulty?.slice(1).toLowerCase() || 'Medium';
    console.log(`🎲 Generating randomized ${difficulty} ${puzzleType} puzzle`);
    
    try {
        const generator = getMathGenerator();
        
        // Check if this puzzle type is supported by randomized generation
        if (!generator.isMathPuzzle(puzzleType)) {
            return {
                success: false,
                message: `${puzzleType} is not supported by randomized generation`,
                fallbackToAI: true
            };
        }
        
        // Generate puzzle using existing comprehensive generator
        const result = await generator.generatePuzzle(puzzleType, difficulty);
        
        if (!result.success) {
            console.error(`❌ Randomized generation failed: ${result.error}`);
            return {
                success: false,
                message: result.error || 'Randomized generation failed',
                attempts: result.attempts,
                fallbackToAI: true
            };
        }
        
        // Log successful generation
        console.log(`✅ Generated ${puzzleType} puzzle (attempt ${result.attempts})`);
        console.log(`📊 Puzzle data:`, JSON.stringify(result.puzzle, null, 2));
        
        return {
            success: true,
            puzzleData: result.puzzle,
            attempts: result.attempts,
            generationMethod: 'randomized'
        };
        
    } catch (error) {
        console.error(`💥 Error in randomized generation:`, error);
        return {
            success: false,
            message: `Randomized generation error: ${error.message}`,
            fallbackToAI: true
        };
    }
}

/**
 * Generate batch of puzzles using existing generator
 */
export async function generateRandomizedBatch(puzzleType, difficulty, count = 5) {
    console.log(`🎯 Starting batch generation: ${count} ${difficulty} ${puzzleType} puzzles`);
    
    try {
        const generator = getMathGenerator();
        
        if (!generator.isMathPuzzle(puzzleType)) {
            return {
                success: false,
                message: `${puzzleType} is not supported by randomized batch generation`
            };
        }
        
        const batchResult = await generator.generateBatch(puzzleType, difficulty, count);
        
        return {
            success: batchResult.successful > 0,
            puzzles: batchResult.puzzles,
            successful: batchResult.successful,
            failed: batchResult.failed,
            totalAttempts: batchResult.totalAttempts,
            message: `Generated ${batchResult.successful}/${count} puzzles successfully`
        };
        
    } catch (error) {
        console.error(`💥 Batch generation error:`, error);
        return {
            success: false,
            message: `Batch generation error: ${error.message}`
        };
    }
}

/**
 * Get statistics from the math generator
 */
export function getGeneratorStats() {
    const generator = getMathGenerator();
    return generator.getStats();
}

/**
 * Clear generator cache
 */
export function clearGeneratorCache() {
    const generator = getMathGenerator();
    generator.clearCache();
}

/**
 * Check if a puzzle type supports randomized generation
 */
export function supportsRandomizedGeneration(puzzleType) {
    const generator = getMathGenerator();
    return generator.isMathPuzzle(puzzleType);
}

/**
 * Get list of supported puzzle types for randomized generation
 */
export function getSupportedPuzzleTypes() {
    const generator = getMathGenerator();
    const stats = generator.getStats();
    return stats.supportedTypes;
}