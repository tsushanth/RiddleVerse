// services/puzzleUtils.service.js - Puzzle utility functions
import { deduplicationService } from './deduplicationService.js';

export const OPTION_CONSTRAINTS = {
    maxLength: 50,
    maxWords: 8,
    preferredLength: 35
};

export const HIGH_COUNT_PUZZLES = new Set([
    'riddle',
    'trivia',
    'anagram',
    'wordsearch',
    'math',
    'crypto',
    'wordsnake',
    'memorystory',
    'synonyms',
    'antonyms',
    'sentencetransitions'
]);

/**
 * Truncate an option to a maximum length
 */
export function truncateOption(option, maxLength = OPTION_CONSTRAINTS.maxLength) {
    if (!option || typeof option !== 'string') return option;

    const trimmed = option.trim();

    if (trimmed.length <= maxLength) {
        return trimmed;
    }

    const words = trimmed.split(' ');
    let truncated = '';

    for (const word of words) {
        const testLength = truncated ? truncated.length + 1 + word.length : word.length;
        if (testLength <= maxLength - 3) {
            truncated += truncated ? ' ' + word : word;
        } else {
            break;
        }
    }

    if (!truncated) {
        truncated = trimmed.substring(0, maxLength - 3);
    }

    return truncated + '...';
}

/**
 * Check if a question is a duplicate
 */
export async function isDuplicateQuestion(puzzleType, question) {
    console.log(`📌 Enhanced duplicate check for ${puzzleType}: ${question.substring(0, 50)}...`);

    try {
        const isDuplicate = await deduplicationService.isDuplicate(puzzleType, question);
        console.log(`✅ Deduplication service result: ${isDuplicate ? 'DUPLICATE' : 'UNIQUE'}`);
        return isDuplicate;
    } catch (error) {
        console.error(`❌ Deduplication service error: ${error.message}`);
        return false;
    }
}

/**
 * Check if a custom question is a duplicate
 */
export async function isDuplicateCustomQuestion(puzzleType, question, existingQuestions) {
    const normalizedQuestion = question.toLowerCase().trim();

    for (const existing of existingQuestions) {
        if (existing.toLowerCase().trim() === normalizedQuestion) {
            return true;
        }
    }

    return false;
}

/**
 * Shuffle letters in a word for anagram puzzles
 */
export function shuffleWord(word) {
    const letters = word.split('');
    for (let i = letters.length - 1; i > 0; i--) {
        const j = Math.floor(Math.random() * (i + 1));
        [letters[i], letters[j]] = [letters[j], letters[i]];
    }
    return letters.join('');
}

/**
 * Validate puzzle options
 */
export function validateOptions(options, expectedCount = 4) {
    if (!Array.isArray(options)) {
        return { valid: false, reason: 'Options must be an array' };
    }

    if (options.length !== expectedCount) {
        return { valid: false, reason: `Expected ${expectedCount} options, got ${options.length}` };
    }

    const uniqueOptions = new Set(options.map(o => o.toLowerCase().trim()));
    if (uniqueOptions.size !== options.length) {
        return { valid: false, reason: 'Options contain duplicates' };
    }

    return { valid: true };
}

/**
 * Sanitize puzzle text
 */
export function sanitizePuzzleText(text) {
    if (!text) return text;
    return text
        .replace(/[\x00-\x1F\x7F]/g, '') // Remove control characters
        .replace(/\s+/g, ' ') // Normalize whitespace
        .trim();
}
