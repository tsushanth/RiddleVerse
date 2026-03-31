// services/generators/puzzleTypeHandlers.js - Puzzle type-specific generation handlers
// This module contains factory functions for generating different puzzle types

import { PUZZLE_TYPES } from '../../config/puzzleConfig.js';

/**
 * Get the appropriate generator for a puzzle type
 */
export async function getGeneratorForType(puzzleType, options = {}) {
    const type = puzzleType.toLowerCase();
    const { debug = false } = options;

    switch (type) {
        case 'imagematch':
            return await getImageMatchGenerator(options);
        case 'crossword':
            return await getCrosswordGenerator(options);
        case 'dailycrossword':
            return await getDailyCrosswordGenerator(options);
        case 'oddoneout':
            return await getOddOneOutGenerator(options);
        case 'finddifferences':
            return await getFindDifferencesGenerator(options);
        case 'findobject':
            return await getFindObjectGenerator(options);
        case 'imagequestion':
            return await getImageQuestionGenerator(options);
        case 'waldopuzzle':
            return await getWaldoGenerator(options);
        case 'realorai':
            return await getRealOrAiGenerator(options);
        case 'musicidentification':
            return await getMusicGenerator(options);
        case 'wordsearch':
            return await getWordSearchGenerator(options);
        case 'wordsnake':
            return await getWordSnakeGenerator(options);
        case 'antonyms':
            return await getAntonymGenerator(options);
        case 'anagram':
            return await getAnagramGenerator(options);
        case 'flowpuzzle':
            return await getFlowPuzzleGenerator(options);
        case 'progressiverevelation':
            return await getProgressiveRevelationGenerator(options);
        default:
            return null;
    }
}

async function getImageMatchGenerator(options) {
    const { EnhancedPixabayPuzzleSystem } = await import('../enhancedPixabayImageMatch.js');
    return new EnhancedPixabayPuzzleSystem({
        pixabayKey: process.env.PIXABAY_API_KEY,
        debug: options.debug
    });
}

async function getCrosswordGenerator(options) {
    const { BeamSearch5x5CrosswordGenerator } = await import('../crosswordGeneratorService.js');
    return new BeamSearch5x5CrosswordGenerator();
}

async function getDailyCrosswordGenerator(options) {
    const { DailyCrosswordGenerator } = await import('../dailyCrosswordGenerator.js');
    const { supabase } = await import('../../config/database.js');
    const generator = new DailyCrosswordGenerator(supabase);
    generator.setDebugMode(options.debug);
    return generator;
}

async function getOddOneOutGenerator(options) {
    const { OddOneOutPuzzleSystem } = await import('../oddOneOutPuzzle.js');
    return new OddOneOutPuzzleSystem({
        debug: options.debug,
        maxRetries: 3,
        useAIFallback: true
    });
}

async function getFindDifferencesGenerator(options) {
    const { FindDifferencesPuzzleGenerator } = await import('../findDifferencesGeneratorService.js');
    return new FindDifferencesPuzzleGenerator();
}

async function getFindObjectGenerator(options) {
    const { FindObjectPuzzleGenerator } = await import('../findObjectGenerator.js');
    return new FindObjectPuzzleGenerator();
}

async function getImageQuestionGenerator(options) {
    const { ImageQuestionGenerator } = await import('../imageQuestionGenerator.js');
    return new ImageQuestionGenerator();
}

async function getWaldoGenerator(options) {
    const { WaldoPuzzleGenerator } = await import('../waldoPuzzleGeneratorService.js');
    return new WaldoPuzzleGenerator();
}

async function getRealOrAiGenerator(options) {
    const { WhichIsRealGenerator } = await import('../whichIsRealGenerator.js');
    return new WhichIsRealGenerator();
}

async function getMusicGenerator(options) {
    const { musicPuzzleGenerator } = await import('../musicPuzzleGenerator.js');
    return musicPuzzleGenerator;
}

async function getWordSearchGenerator(options) {
    const { wordSearchService } = await import('../wordSearchGeneratorService.js');
    return wordSearchService;
}

async function getWordSnakeGenerator(options) {
    const { enhancedWordSnakeGenerator } = await import('../snakeWordSearch.js');
    return enhancedWordSnakeGenerator;
}

async function getAntonymGenerator(options) {
    const { enhancedAntonymGenerator } = await import('../antonymGeneratorService.js');
    return enhancedAntonymGenerator;
}

async function getAnagramGenerator(options) {
    const { AnagramPuzzleGenerator } = await import('../anagramGeneratorService.js');
    return new AnagramPuzzleGenerator();
}

async function getFlowPuzzleGenerator(options) {
    const { flowPuzzleService } = await import('../flowPuzzleService.js');
    return flowPuzzleService;
}

async function getProgressiveRevelationGenerator(options) {
    const { ProgressivePuzzleSystem } = await import('../progressivePuzzleSystem.js');
    return new ProgressivePuzzleSystem();
}

/**
 * List of puzzle types that use randomized generation
 */
export const RANDOMIZED_PUZZLE_TYPES = [
    PUZZLE_TYPES.LETTER_SET,
    PUZZLE_TYPES.MATH_ESTIMATION,
    PUZZLE_TYPES.MATH_TIPPING,
    PUZZLE_TYPES.PERCENTAGES,
    PUZZLE_TYPES.DIVISION,
    PUZZLE_TYPES.AVERAGE,
    PUZZLE_TYPES.SUBTRACTION,
    PUZZLE_TYPES.PURCHASING,
    PUZZLE_TYPES.DISCOUNTS,
    PUZZLE_TYPES.CONVERSION,
    PUZZLE_TYPES.MEMORY_SQUARES,
    PUZZLE_TYPES.CROSSWORD,
    PUZZLE_TYPES.DAILY_CROSSWORD,
    PUZZLE_TYPES.WORD_PREFIX,
    PUZZLE_TYPES.MEMORY_PREVIOUS_PAIR,
    PUZZLE_TYPES.MEMORY_PREVIOUS_SINGLE,
    PUZZLE_TYPES.MEMORY_MATRIX_PATH,
    PUZZLE_TYPES.PINBALL_DEFLECTOR,
    PUZZLE_TYPES.ANTONYMS,
    PUZZLE_TYPES.ANAGRAM,
    PUZZLE_TYPES.WORD_SEARCH,
    PUZZLE_TYPES.UNIQUE_OBJECT,
    PUZZLE_TYPES.MATH_COMPARISON,
    PUZZLE_TYPES.CRYPTO,
    PUZZLE_TYPES.WORD_SNAKE,
    PUZZLE_TYPES.FIND_DIFFERENCES,
    PUZZLE_TYPES.FIND_OBJECT,
    PUZZLE_TYPES.IMAGE_QUESTION,
    PUZZLE_TYPES.WALDO_PUZZLE,
    PUZZLE_TYPES.MUSIC_IDENTIFICATION,
    PUZZLE_TYPES.IMAGE_PUZZLE,
    PUZZLE_TYPES.IMAGE_MATCH,
    PUZZLE_TYPES.FLOW_PUZZLE,
    PUZZLE_TYPES.PROGRESSIVE_REVELATION,
    PUZZLE_TYPES.REAL_OR_AI,
    PUZZLE_TYPES.ODD_ONE_OUT
];

/**
 * Puzzle type aliases for normalization
 */
export const PUZZLE_TYPE_ALIASES = {
    'snakewordsearch': 'wordsnake',
    'pairmemory': 'memorypreviouspair',
    'singlemamory': 'memoryprevioussingle',
    'singlememory': 'memoryprevioussingle',
    'matrixpath': 'memorymatrixpath',
    'sequencing': 'memorysequencing',
    'retention': 'memoryretention',
    'squares': 'memorysquares'
};

/**
 * Normalize a puzzle type name
 */
export function normalizePuzzleType(puzzleType) {
    const normalized = puzzleType.toLowerCase();
    return PUZZLE_TYPE_ALIASES[normalized] || normalized;
}
