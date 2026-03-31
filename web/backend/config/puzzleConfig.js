// config/puzzleConfig.js - Enhanced with ImageMatch Integration

/**
 * Central configuration for all puzzle types, difficulties, and metadata
 * ENHANCED: Now includes IMAGE_MATCH puzzle type
 */

// ✅ ENHANCED PUZZLE_TYPES with IMAGE_MATCH
export const PUZZLE_TYPES = {
  // Word-based puzzles
  ANAGRAM: 'anagram',
  ANTONYMS: 'antonyms',
  SYNONYMS: 'synonyms',
  CROSSWORD: 'crossword',
  DAILY_CROSSWORD: 'dailycrossword',
  WORD_SEARCH: 'wordsearch',
  WORD_SNAKE: 'wordsnake',
  WORD_PREFIX: 'wordprefix',
  LETTER_SET: 'letterset',

  // Math puzzles
  MATH: 'math',
  MATH_ESTIMATION: 'mathestimation',
  MATH_TIPPING: 'mathtipping',
  MATH_COMPARISON: 'mathcomparison',
  PERCENTAGES: 'percentages',
  DIVISION: 'division',
  AVERAGE: 'average',
  SUBTRACTION: 'subtraction',
  PURCHASING: 'purchasing',
  DISCOUNTS: 'discounts',
  CONVERSION: 'conversion',

  // Memory puzzles
  MEMORY_SQUARES: 'memorysquares',
  MEMORY_PREVIOUS_PAIR: 'memorypreviouspair',
  MEMORY_PREVIOUS_SINGLE: 'memoryprevioussingle',
  MEMORY_MATRIX_PATH: 'memorymatrixpath',
  MEMORY_SEQUENCING: 'memorysequencing',
  MEMORY_RETENTION: 'memoryretention',
  MEMORY_STORY: 'memorystory',
  TRIVIA: 'trivia',
  STORY_PUZZLE: 'storypuzzle',

  // Visual puzzles
  IMAGE_PUZZLE: 'imagepuzzle',
  IMAGE_QUESTION: 'imagequestion',
  IMAGE_MATCH: 'imagematch',
  FIND_DIFFERENCES: 'find_differences',
  FIND_OBJECT: 'find_object',
  WALDO_PUZZLE: 'waldopuzzle',
  UNIQUE_OBJECT: 'uniqueobject',
  FLOW_PUZZLE: 'flowpuzzle',
  PROGRESSIVE_REVELATION: 'progressiverevelation',
  REAL_OR_AI: 'realorai',
  ODD_ONE_OUT: 'oddoneout',

  // Interactive puzzles
  PINBALL_DEFLECTOR: 'pinballdeflector',
  SNAKE_WORD_SEARCH: 'wordsnake',

  // Media puzzles
  MUSIC_IDENTIFICATION: 'musicidentification',

  // Logic puzzles
  CRYPTO: 'crypto',
  SENTENCE_TRANSITIONS: 'sentencetransitions',
  CONNOTATION_WORDS: 'connotationwords'
};


export const DIFFICULTY_LEVELS = {
  EASY: 'Easy',
  MEDIUM: 'Medium', 
  HARD: 'Hard'
};

// ✅ ADD MISSING DIFFICULTY_CRITERIA EXPORT
export const DIFFICULTY_CRITERIA = {
  EASY: {
    timeLimit: 120, // 2 minutes
    complexityLevel: 1,
    hintAvailability: true,
    optionCount: 3,
    description: 'Simple questions with clear answers'
  },
  MEDIUM: {
    timeLimit: 180, // 3 minutes
    complexityLevel: 2,
    hintAvailability: true,
    optionCount: 4,
    description: 'Moderate difficulty requiring some knowledge'
  },
  HARD: {
    timeLimit: 300, // 5 minutes
    complexityLevel: 3,
    hintAvailability: false,
    optionCount: 4,
    description: 'Challenging questions requiring deep knowledge'
  }
};

// ✅ ENHANCED PUZZLE_METADATA with IMAGE_MATCH
export const PUZZLE_METADATA = {
  // Word-based puzzles
  [PUZZLE_TYPES.ANAGRAM]: {
    displayName: 'Anagram',
    description: 'Unscramble letters to form words',
    category: 'Word Games',
    timeEstimate: '30-60s',
    storeFullJSONInQuestion: false,
    supportsBatch: true,
    requiresValidation: true,
    difficultyScaling: 'word_length'
  },

  [PUZZLE_TYPES.ANTONYMS]: {
    displayName: 'Antonyms',
    description: 'Match words with their opposite meanings',
    category: 'Word Games', 
    timeEstimate: '60-120s',
    storeFullJSONInQuestion: true,
    supportsBatch: true,
    requiresValidation: true,
    difficultyScaling: 'pair_count'
  },

  [PUZZLE_TYPES.ODD_ONE_OUT]: {
    name: 'Odd One Out',
    description: 'Find the item that doesn\'t belong',
    storeFullJSONInQuestion: true,
    requiresImages: true,
    interactive: true,
    category: 'visual_reasoning'
  },

  [PUZZLE_TYPES.SYNONYMS]: {
    displayName: 'Synonyms',
    description: 'Group words with similar meanings',
    category: 'Word Games',
    timeEstimate: '90-150s', 
    storeFullJSONInQuestion: true,
    supportsBatch: true,
    requiresValidation: true,
    difficultyScaling: 'group_complexity'
  },

  [PUZZLE_TYPES.CROSSWORD]: {
    displayName: '5x5 Crossword',
    description: 'Complete a compact crossword puzzle',
    category: 'Word Games',
    timeEstimate: '3-7min',
    storeFullJSONInQuestion: true,
    supportsBatch: true,
    requiresValidation: true,
    difficultyScaling: 'word_obscurity'
  },

  [PUZZLE_TYPES.WORD_SEARCH]: {
    displayName: 'Word Search',
    description: 'Find hidden words in a letter grid',
    category: 'Word Games',
    timeEstimate: '2-5min',
    storeFullJSONInQuestion: true, 
    supportsBatch: true,
    requiresValidation: false,
    difficultyScaling: 'grid_size_word_count'
  },

  [PUZZLE_TYPES.WORD_SNAKE]: {
    displayName: 'Snake Word Search', 
    description: 'Find words that snake through the grid',
    category: 'Word Games',
    timeEstimate: '3-6min',
    storeFullJSONInQuestion: true,
    supportsBatch: true,
    requiresValidation: false,
    difficultyScaling: 'path_complexity'
  },

  [PUZZLE_TYPES.LETTER_SET]: {
    displayName: 'Letter Set Challenge',
    description: 'Form as many words as possible from given letters',
    category: 'Word Games',
    timeEstimate: '3-5min',
    storeFullJSONInQuestion: true,
    supportsBatch: true,
    requiresValidation: false,
    difficultyScaling: 'letter_set_complexity'
  },

  [PUZZLE_TYPES.PROGRESSIVE_REVELATION]: {
    displayName: 'Progressive Revelation',
    description: 'Guess what\'s in the image using progressively easier clues',
    difficulty: ['easy', 'medium', 'hard'],
    timeLimit: 300000, // 5 minutes
    storeFullJSONInQuestion: true,
    requiresSpecialHandling: true,
    category: 'visual_interactive',
    features: ['client_side_blur', 'progressive_clues', 'scoring_system']
  },

  [PUZZLE_TYPES.WORD_PREFIX]: {
    displayName: 'Word Prefix Challenge',
    description: 'Find words starting with a given prefix',
    category: 'Word Games', 
    timeEstimate: '2-4min',
    storeFullJSONInQuestion: false,
    supportsBatch: true,
    requiresValidation: false,
    difficultyScaling: 'prefix_obscurity'
  },

  // Math puzzles
  [PUZZLE_TYPES.MATH_ESTIMATION]: {
    displayName: 'Math Estimation',
    description: 'Estimate sums through rounding',
    category: 'Math',
    timeEstimate: '30-90s',
    storeFullJSONInQuestion: true,
    supportsBatch: true,
    requiresValidation: true,
    difficultyScaling: 'number_complexity'
  },

  [PUZZLE_TYPES.MATH_TIPPING]: {
    displayName: 'Tip Calculator',
    description: 'Calculate correct tips for restaurant bills',
    category: 'Math',
    timeEstimate: '30-60s',
    storeFullJSONInQuestion: true,
    supportsBatch: true,
    requiresValidation: true,
    difficultyScaling: 'calculation_complexity'
  },

  [PUZZLE_TYPES.MATH_COMPARISON]: {
    displayName: 'Mathematical Comparison',
    description: 'Compare mathematical expressions',
    category: 'Math',
    timeEstimate: '60-120s',
    storeFullJSONInQuestion: true,
    supportsBatch: true,
    requiresValidation: false,
    difficultyScaling: 'expression_complexity'
  },

  [PUZZLE_TYPES.PERCENTAGES]: {
    displayName: 'Percentage Problems',
    description: 'Calculate percentages of given values',
    category: 'Math',
    timeEstimate: '30-90s',
    storeFullJSONInQuestion: true,
    supportsBatch: true,
    requiresValidation: true,
    difficultyScaling: 'percentage_complexity'
  },

  [PUZZLE_TYPES.DIVISION]: {
    displayName: 'Division Problems',
    description: 'Solve division calculations',
    category: 'Math',
    timeEstimate: '60-120s',
    storeFullJSONInQuestion: true,
    supportsBatch: true,
    requiresValidation: true,
    difficultyScaling: 'divisor_size'
  },

  [PUZZLE_TYPES.AVERAGE]: {
    displayName: 'Average Calculation',
    description: 'Calculate averages of number sets',
    category: 'Math',
    timeEstimate: '45-90s',
    storeFullJSONInQuestion: true,
    supportsBatch: true,
    requiresValidation: true,
    difficultyScaling: 'set_size_complexity'
  },
  [PUZZLE_TYPES.FLOW_PUZZLE]: {
    displayName: 'Average Calculation',
    description: 'Calculate averages of number sets',
      storeFullJSONInQuestion: true,
      category: 'Logic',
       timeLimit: 300000, // 5 minutes default
       description: 'Connect matching colored dots with non-crossing paths'
    },

  [PUZZLE_TYPES.SUBTRACTION]: {
    displayName: 'Subtraction Problems',
    description: 'Solve subtraction calculations',
    category: 'Math',
    timeEstimate: '30-90s',
    storeFullJSONInQuestion: true,
    supportsBatch: true, 
    requiresValidation: true,
    difficultyScaling: 'borrowing_required'
  },

  [PUZZLE_TYPES.PURCHASING]: {
    displayName: 'Purchase Planning',
    description: 'Calculate yearly costs from periodic payments',
    category: 'Math',
    timeEstimate: '60-120s',
    storeFullJSONInQuestion: true,
    supportsBatch: true,
    requiresValidation: true,
    difficultyScaling: 'frequency_complexity'
  },

  [PUZZLE_TYPES.DISCOUNTS]: {
    displayName: 'Discount Comparison',
    description: 'Order items by final price after discounts',
    category: 'Math',
    timeEstimate: '90-180s',
    storeFullJSONInQuestion: true,
    supportsBatch: true,
    requiresValidation: true,
    difficultyScaling: 'item_count'
  },

  [PUZZLE_TYPES.CONVERSION]: {
    displayName: 'Unit Conversion',
    description: 'Compare equivalent measurements',
    category: 'Math',
    timeEstimate: '45-120s',
    storeFullJSONInQuestion: true,
    supportsBatch: true,
    requiresValidation: false,
    difficultyScaling: 'conversion_complexity'
  },

  // Memory puzzles
  [PUZZLE_TYPES.MEMORY_SQUARES]: {
    displayName: 'Memory Squares',
    description: 'Memorize and recreate square patterns',
    category: 'Memory',
    timeEstimate: '60-180s',
    storeFullJSONInQuestion: true,
    supportsBatch: true,
    requiresValidation: false,
    difficultyScaling: 'grid_size_square_count'
  },

  [PUZZLE_TYPES.MEMORY_PREVIOUS_PAIR]: {
    displayName: 'Memory Previous Pair',
    description: 'Remember objects from previous screens',
    category: 'Memory',
    timeEstimate: '2-4min',
    storeFullJSONInQuestion: true,
    supportsBatch: true,
    requiresValidation: false,
    difficultyScaling: 'screen_count_objects'
  },

  [PUZZLE_TYPES.MEMORY_PREVIOUS_SINGLE]: {
    displayName: 'Memory Previous Single',
    description: 'Compare current with previous single object',
    category: 'Memory',
    timeEstimate: '90-180s',
    storeFullJSONInQuestion: true,
    supportsBatch: true,
    requiresValidation: false,
    difficultyScaling: 'sequence_length'
  },

  [PUZZLE_TYPES.MEMORY_MATRIX_PATH]: {
    displayName: 'Memory Matrix Path',
    description: 'Memorize obstacles and find path through matrix',
    category: 'Memory',
    timeEstimate: '2-5min',
    storeFullJSONInQuestion: true,
    supportsBatch: true,
    requiresValidation: false,
    difficultyScaling: 'matrix_size_obstacles'
  },

  [PUZZLE_TYPES.MEMORY_SEQUENCING]: {
    displayName: 'Memory Sequencing',
    description: 'Arrange events in chronological order',
    category: 'Memory',
    timeEstimate: '3-6min',
    storeFullJSONInQuestion: true,
    supportsBatch: false,
    requiresValidation: true,
    difficultyScaling: 'event_count_complexity'
  },

  [PUZZLE_TYPES.MEMORY_RETENTION]: {
    displayName: 'Memory Retention',
    description: 'Remember facts from audio essay by subject',
    category: 'Memory', 
    timeEstimate: '4-8min',
    storeFullJSONInQuestion: true,
    supportsBatch: false,
    requiresValidation: true,
    difficultyScaling: 'essay_length_fact_count'
  },

  [PUZZLE_TYPES.MEMORY_STORY]: {
    displayName: 'Memory Story',
    description: 'Remember story details and answer questions',
    category: 'Memory',
    timeEstimate: '3-5min',
    storeFullJSONInQuestion: true,
    supportsBatch: false,
    requiresValidation: true,
    difficultyScaling: 'story_complexity'
  },

  // Visual puzzles
  [PUZZLE_TYPES.IMAGE_PUZZLE]: {
    displayName: 'Image Jigsaw Puzzle',
    description: 'Assemble puzzle pieces to recreate an image',
    category: 'Visual',
    timeEstimate: '2-10min',
    storeFullJSONInQuestion: true,
    supportsBatch: false,
    requiresValidation: false,
    requiresImageGeneration: true,
    difficultyScaling: 'piece_count'
  },

  [PUZZLE_TYPES.IMAGE_QUESTION]: {
    displayName: 'Image Questions',
    description: 'Answer questions about objects in generated images',
    category: 'Visual',
    timeEstimate: '60-180s',
    storeFullJSONInQuestion: true,
    supportsBatch: false,
    requiresValidation: false,
    requiresImageGeneration: true,
    difficultyScaling: 'question_complexity'
  },

  // ✅ NEW: IMAGE_MATCH METADATA
  [PUZZLE_TYPES.IMAGE_MATCH]: {
    displayName: 'Image Location Match',
    description: 'Test your knowledge about locations, culture, and geography through images',
    category: 'Visual',
    timeEstimate: '90-300s',
    storeFullJSONInQuestion: true,
    supportsBatch: true,
    requiresValidation: false,
    requiresImageGeneration: false, // Uses Pixabay images
    requiresApiKey: 'PIXABAY_API_KEY',
    educationalValue: true,
    difficultyScaling: 'question_complexity_cultural_depth',
    themes: ['landmarks', 'animals', 'foods', 'traditional_clothing', 'currencies', 'car_brands', 'company_logos', 'flags', 'instruments', 'sports', 'architectural_styles', 'natural_wonders'],
    features: ['multiple_choice', 'geographical_knowledge', 'cultural_awareness', 'location_based', 'educational']
  },

  [PUZZLE_TYPES.FIND_DIFFERENCES]: {
    displayName: 'Find the Differences',
    description: 'Spot differences between two similar images',
    category: 'Visual',
    timeEstimate: '2-8min',
    storeFullJSONInQuestion: true,
    supportsBatch: false,
    requiresValidation: false,
    requiresImageGeneration: true,
    difficultyScaling: 'difference_count_subtlety'
  },

  [PUZZLE_TYPES.FIND_OBJECT]: {
    displayName: 'Find the Object',
    description: 'Locate specific objects hidden in complex images',
    category: 'Visual',
    timeEstimate: '30-120s',
    storeFullJSONInQuestion: true,
    supportsBatch: false,
    requiresValidation: false,
    requiresImageGeneration: true,
    difficultyScaling: 'object_complexity_concealment'
  },

  [PUZZLE_TYPES.WALDO_PUZZLE]: {
    displayName: 'Find Hidden Objects',
    description: 'Find multiple hidden objects in detailed scenes',
    category: 'Visual',
    timeEstimate: '2-6min',
    storeFullJSONInQuestion: true,
    supportsBatch: false,
    requiresValidation: false,
    requiresImageGeneration: true,
    difficultyScaling: 'object_count_scene_complexity'
  },

  [PUZZLE_TYPES.UNIQUE_OBJECT]: {
    displayName: 'Unique Object',
    description: 'Find the one object that appears only once',
    category: 'Visual',
    timeEstimate: '30-90s',
    storeFullJSONInQuestion: true,
    supportsBatch: true,
    requiresValidation: false,
    difficultyScaling: 'object_count_similarity'
  },

  // Interactive puzzles
  [PUZZLE_TYPES.PINBALL_DEFLECTOR]: {
    displayName: 'Pinball Deflector',
    description: 'Memorize deflector positions and predict ball path',
    category: 'Interactive',
    timeEstimate: '90-240s',
    storeFullJSONInQuestion: true,
    supportsBatch: true,
    requiresValidation: false,
    difficultyScaling: 'matrix_size_deflector_count'
  },

  [PUZZLE_TYPES.SNAKE_WORD_SEARCH]: {
    displayName: 'Snake Word Search',
    description: 'Alternative name for Word Snake puzzle',
    category: 'Word Games',
    timeEstimate: '3-6min',
    storeFullJSONInQuestion: true,
    supportsBatch: true,
    requiresValidation: false,
    difficultyScaling: 'path_complexity'
  },

  // Media puzzles
  [PUZZLE_TYPES.MUSIC_IDENTIFICATION]: {
    displayName: 'Music Identification',
    description: 'Identify songs from audio previews',
    category: 'Audio',
    timeEstimate: '2-5min',
    storeFullJSONInQuestion: true,
    supportsBatch: false,
    requiresValidation: false,
    requiresAudioGeneration: true,
    difficultyScaling: 'song_obscurity_preview_length'
  },

  // Logic puzzles
  [PUZZLE_TYPES.CRYPTO]: {
    displayName: 'Cryptogram',
    description: 'Decode encrypted quotes using substitution cipher',
    category: 'Logic',
    timeEstimate: '3-10min',
    storeFullJSONInQuestion: false,
    supportsBatch: true,
    requiresValidation: false,
    difficultyScaling: 'quote_length_cipher_complexity'
  },

  [PUZZLE_TYPES.SENTENCE_TRANSITIONS]: {
    displayName: 'Sentence Transitions',
    description: 'Choose the correct transition word between sentences',
    category: 'Language',
    timeEstimate: '30-60s',
    storeFullJSONInQuestion: false,
    supportsBatch: true,
    requiresValidation: true,
    difficultyScaling: 'transition_subtlety'
  }
};

/**
 * Get puzzle metadata by type
 */
export function getPuzzleMetadata(puzzleType) {
  const normalizedType = puzzleType?.toLowerCase();
  
  // Find matching metadata (case-insensitive)
  for (const [key, metadata] of Object.entries(PUZZLE_METADATA)) {
    if (key.toLowerCase() === normalizedType) {
      return {
        type: key,
        ...metadata
      };
    }
  }
  
  // Return default metadata if not found
  return {
    type: puzzleType,
    displayName: puzzleType || 'Unknown Puzzle',
    description: 'Puzzle description not available',
    category: 'General',
    timeEstimate: '60-120s',
    storeFullJSONInQuestion: false,
    supportsBatch: true,
    requiresValidation: true,
    difficultyScaling: 'standard'
  };
}

/**
 * Get difficulty criteria by level
 */
export function getDifficultyCriteria(level = 'MEDIUM') {
  return DIFFICULTY_CRITERIA[level.toUpperCase()] || DIFFICULTY_CRITERIA.MEDIUM;
}

/**
 * Get all puzzle types by category
 */
export function getPuzzlesByCategory() {
  const categories = {};
  
  Object.entries(PUZZLE_METADATA).forEach(([type, metadata]) => {
    const category = metadata.category;
    if (!categories[category]) {
      categories[category] = [];
    }
    categories[category].push({
      type,
      ...metadata
    });
  });
  
  return categories;
}

/**
 * Get puzzles that support batch generation
 */
export function getBatchSupportedPuzzles() {
  return Object.entries(PUZZLE_METADATA)
    .filter(([type, metadata]) => metadata.supportsBatch)
    .map(([type, metadata]) => ({
      type,
      ...metadata
    }));
}

/**
 * Get puzzles that require image generation
 */
export function getImageGenerationPuzzles() {
  return Object.entries(PUZZLE_METADATA)
    .filter(([type, metadata]) => metadata.requiresImageGeneration)
    .map(([type, metadata]) => ({
      type,
      ...metadata
    }));
}

/**
 * Get puzzles that require audio generation
 */
export function getAudioGenerationPuzzles() {
  return Object.entries(PUZZLE_METADATA)
    .filter(([type, metadata]) => metadata.requiresAudioGeneration)
    .map(([type, metadata]) => ({
      type,
      ...metadata
    }));
}

/**
 * ✅ NEW: Get puzzles that require external API keys
 */
export function getApiKeyRequiredPuzzles() {
  return Object.entries(PUZZLE_METADATA)
    .filter(([type, metadata]) => metadata.requiresApiKey)
    .map(([type, metadata]) => ({
      type,
      apiKey: metadata.requiresApiKey,
      ...metadata
    }));
}

/**
 * ✅ NEW: Get educational puzzles
 */
export function getEducationalPuzzles() {
  return Object.entries(PUZZLE_METADATA)
    .filter(([type, metadata]) => metadata.educationalValue)
    .map(([type, metadata]) => ({
      type,
      ...metadata
    }));
}

/**
 * Get difficulty scaling information
 */
export function getDifficultyScaling(puzzleType) {
  const metadata = getPuzzleMetadata(puzzleType);
  return metadata.difficultyScaling || 'standard';
}

/**
 * Check if puzzle type exists
 */
export function isValidPuzzleType(puzzleType) {
  const normalizedType = puzzleType?.toLowerCase();
  return Object.keys(PUZZLE_TYPES).some(key => 
    PUZZLE_TYPES[key].toLowerCase() === normalizedType
  ) || normalizedType === 'imagematch'; // ✅ Manual check for new type
}

/**
 * Get estimated time for puzzle
 */
export function getEstimatedTime(puzzleType, difficulty = 'Medium') {
  const metadata = getPuzzleMetadata(puzzleType);
  const baseTime = metadata.timeEstimate;
  
  // Apply difficulty multiplier
  const multipliers = {
    'Easy': 0.8,
    'Medium': 1.0,
    'Hard': 1.3
  };
  
  const multiplier = multipliers[difficulty] || 1.0;
  
  return {
    baseTime,
    adjustedTime: `Estimated: ${Math.round(multiplier * 100)}% of base time`,
    difficulty,
    multiplier
  };
}

/**
 * ✅ NEW: Get theme information for IMAGE_MATCH
 */
export function getImageMatchThemes() {
  const imageMatchMetadata = PUZZLE_METADATA[PUZZLE_TYPES.IMAGE_MATCH];
  return imageMatchMetadata?.themes || [];
}

/**
 * ✅ NEW: Get features for a puzzle type
 */
export function getPuzzleFeatures(puzzleType) {
  const metadata = getPuzzleMetadata(puzzleType);
  return metadata.features || [];
}

// Export all constants and functions
export default {
  PUZZLE_TYPES,
  DIFFICULTY_LEVELS,
  DIFFICULTY_CRITERIA,
  PUZZLE_METADATA,
  getPuzzleMetadata,
  getDifficultyCriteria,
  getPuzzlesByCategory,
  getBatchSupportedPuzzles,
  getImageGenerationPuzzles,
  getAudioGenerationPuzzles,
  getApiKeyRequiredPuzzles,
  getEducationalPuzzles,
  getDifficultyScaling,
  isValidPuzzleType,
  getEstimatedTime,
  getImageMatchThemes,
  getPuzzleFeatures
};