// services/puzzle/index.js - Re-exports from refactored puzzle modules
// This maintains backward compatibility with existing imports from puzzleService.js

// Storage functions
export {
    hashQuestion,
    uploadAudioToSupabase,
    storePuzzle,
    getExistingQuestionsFromPrefix,
    getExistingQuestionsTopic,
    getExistingQuestionsFromSupabase,
    constructAudioUrl,
    addAudioUrlToMemoryPuzzle
} from '../puzzleStorage.js';

// Path management
export {
    logPathFailure,
    getPathFailureStats,
    diagnosePuzzlePathState,
    findPathEnd,
    insertIntoPuzzlePath
} from '../puzzlePath.js';

// Answer checking
export {
    loadModel,
    generateEmbedding,
    adjustEmbeddingSize,
    checkAnswer,
    generateAnswer
} from '../answerChecker.js';

// Leaderboard
export {
    formatTime,
    getPuzzleLeaderboard,
    getFeaturedPuzzles,
    updatePuzzleLeaderboard,
    deletePuzzleLeaderboardEntry
} from '../puzzleLeaderboard.js';

// Utilities
export {
    OPTION_CONSTRAINTS,
    HIGH_COUNT_PUZZLES,
    truncateOption,
    isDuplicateQuestion,
    isDuplicateCustomQuestion,
    shuffleWord,
    validateOptions,
    sanitizePuzzleText
} from '../puzzleUtils.service.js';
