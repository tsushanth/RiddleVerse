import { supabase } from '../config/database.js';
import { callAI } from '../utils/aiClient.js';
import { USAGE_CATEGORIES } from '../utils/usageTracker.js';
import { unifiedWordService } from './unifedWordProcurementService.js';
import express from 'express';

class WordSnakePuzzleGenerator {
  constructor() {
    this.debugMode = false;
    
    // Initialize unified word service session
    this.sessionId = null;
    
    // Predefined colors for word highlighting
    this.colors = [
      '#E74C3C', '#3498DB', '#2ECC71', '#F39C12', 
      '#9B59B6', '#1ABC9C', '#E67E22', '#34495E',
      '#F1C40F', '#E91E63', '#00BCD4', '#4CAF50'
    ];
    
    // Difficulty mappings - aligned with unified service
    this.difficultyConfig = {
      easy: {
        maxWords: 5,
        minWords: 3,
        gridSize: 6
      },
      medium: {
        maxWords: 6,
        minWords: 4,
        gridSize: 8
      },
      hard: {
        maxWords: 8,
        minWords: 5,
        gridSize: 10
      }
    };
    
    this.thresholds = {
      maxAttempts: 100,
      minWordLength: 3
    };
  }

  // ===========================================
  // SESSION MANAGEMENT (delegated to unified service)
  // ===========================================

  /**
   * Start a new generation session using unified word service
   */
  startGenerationSession() {
    this.sessionId = unifiedWordService.startSession('wordsnake');
    this.debugLog(`Started new word snake session: ${this.sessionId}`);
  }

  /**
   * Get session statistics from unified service
   */
  getSessionStats() {
    return {
      localSessionId: this.sessionId,
      unifiedServiceStats: unifiedWordService.getSessionStats()
    };
  }

  /**
   * Generate a complete puzzle using unified word service
   */
  async generatePuzzle(difficulty = 'easy', gridSize = 8) {
    this.debugLog(`🐍 Generating Word Snake puzzle: ${difficulty} difficulty, ${gridSize}x${gridSize} grid`);
    
    try {
      // Start a new session for this generation
      this.startGenerationSession();
      
      // Get words from unified service
      const words = await this.selectWordsFromUnifiedService(difficulty);
      
      const normalizedDifficulty = difficulty.toLowerCase();
      const config = this.difficultyConfig[normalizedDifficulty] || this.difficultyConfig.easy;

      if (!words || words.length < config.minWords) {
        throw new Error(`Insufficient words: got ${words?.length || 0}, need ${this.difficultyConfig[difficulty].minWords}`);
      }
      
      const grid = this.createGrid(gridSize);
      const placedWords = this.placeWordsInGrid(grid, words, gridSize);
      this.fillEmptySpaces(grid, gridSize);
      
      const puzzleData = {
        puzzleId: this.generateUUID(),
        puzzleType: 'wordsnake',
        grid: grid,
        words: placedWords.map((word, index) => ({
          word: word.word.toUpperCase(),
          clue: word.clue || `Find: ${word.word}`,
          path: word.path,
          color: this.colors[index % this.colors.length],
          found: false
        })),
        gridSize: gridSize,
        difficulty: difficulty,
        timestamp: new Date().toISOString(),
        sessionStats: this.getSessionStats()
      };
      
      this.debugLog(`✅ Generated puzzle with ${puzzleData.words.length} words from unified service`, 'success');
      return puzzleData;
      
    } catch (error) {
      this.debugLog(`❌ Puzzle generation failed: ${error.message}`, 'error');
      throw error;
    }
  }

  /**
   * Select words from unified word service
   */
  async selectWordsFromUnifiedService(difficulty, topic = null) {
    const normalizedDifficulty = difficulty.toLowerCase();
    const config = this.difficultyConfig[normalizedDifficulty] || this.difficultyConfig.easy;
    this.debugLog(`Getting ${config.maxWords} words for ${difficulty} difficulty from unified service`);
    
    try {
      const result = await unifiedWordService.getWords({
        count: config.maxWords,
        difficulty: difficulty,
        topic: topic,
        puzzleType: 'wordsnake',
        allowAI: true,
        requireTopicMatch: topic ? true : false
      });
      
      if (!result.success) {
        throw new Error(result.error || 'Unified word service failed');
      }
      
      this.debugLog(`Unified service returned ${result.words.length} words from ${result.source}`, 'success');
      
      // Convert to format expected by word snake generator
      return result.words.map(word => ({
        word: word.word.toUpperCase(),
        clue: word.hint
      }));
      
    } catch (error) {
      this.debugLog(`Unified service error: ${error.message}`, 'error');
      // REMOVED: Fallback to local fallback words
      throw new Error(`Failed to get words from unified service: ${error.message}`);
    }
  }  

  /**
   * Select topic words from unified word service
   */
  async selectTopicWordsFromUnifiedService(topic, difficulty) {
    const normalizedDifficulty = difficulty.toLowerCase(); 
    const config = this.difficultyConfig[normalizedDifficulty] || this.difficultyConfig.easy;
    this.debugLog(`Getting ${config.maxWords} words for topic "${topic}" (${difficulty}) from unified service`);
    
    try {
      const result = await unifiedWordService.getWords({
        count: config.maxWords,
        difficulty: difficulty,
        topic: topic,
        puzzleType: 'wordsnake',
        allowAI: true,
        requireTopicMatch: true
      });
      
      if (!result.success) {
        throw new Error(result.error || 'Unified word service failed for topic');
      }
      
      this.debugLog(`Unified service returned ${result.words.length} topic words from ${result.source}`, 'success');
      
      // Convert to format expected by word snake generator
      return result.words.map(word => ({
        word: word.word.toUpperCase(),
        clue: word.hint
      }));
      
    } catch (error) {
      this.debugLog(`Unified service error for topic: ${error.message}`, 'error');
      // REMOVED: Fallback to local topic words
      throw new Error(`Failed to get topic words from unified service: ${error.message}`);
    }
  }
  


  // ===========================================
  // GRID GENERATION AND WORD PLACEMENT
  // ===========================================

  /**
   * Create empty grid
   */
  createGrid(size) {
    return Array(size).fill().map(() => Array(size).fill(''));
  }

  /**
   * Place words in grid using snake-like paths
   */
  placeWordsInGrid(grid, words, gridSize) {
    const placedWords = [];
    const usedCells = new Set();

    // Sort words by length (longest first for better placement)
    const sortedWords = words.sort((a, b) => b.word.length - a.word.length);

    for (const wordObj of sortedWords) {
      const placement = this.findValidPlacement(grid, wordObj.word, gridSize, usedCells);
      if (placement) {
        // Place the word
        placement.path.forEach((pos, index) => {
          grid[pos.row][pos.col] = wordObj.word[index];
          usedCells.add(`${pos.row},${pos.col}`);
        });
        
        placedWords.push({
          ...wordObj,
          path: placement.path
        });
        
        this.debugLog(`📍 Placed word: ${wordObj.word} (${placement.path.length} cells)`);
      } else {
        this.debugLog(`⚠️ Could not place word: ${wordObj.word}`, 'warning');
      }
    }

    return placedWords;
  }

  /**
   * Find valid placement for a word
   */
  findValidPlacement(grid, word, gridSize, usedCells, maxAttempts = 100) {
    for (let attempt = 0; attempt < maxAttempts; attempt++) {
      const startRow = Math.floor(Math.random() * gridSize);
      const startCol = Math.floor(Math.random() * gridSize);
      
      const path = this.generateSnakePath(word.length, startRow, startCol, gridSize, usedCells);
      if (path && this.canPlaceWord(grid, word, path, usedCells)) {
        return { path };
      }
    }
    return null;
  }

  /**
   * Generate a snake-like path for the word (horizontal and vertical only)
   */
  generateSnakePath(length, startRow, startCol, gridSize, usedCells) {
    const path = [{ row: startRow, col: startCol }];
    // Only allow horizontal and vertical movements for better user experience
    const directions = [
      { row: -1, col: 0 },  // up
      { row: 1, col: 0 },   // down
      { row: 0, col: -1 },  // left
      { row: 0, col: 1 }    // right
    ];

    let currentRow = startRow;
    let currentCol = startCol;

    for (let i = 1; i < length; i++) {
      const validMoves = directions.filter(dir => {
        const newRow = currentRow + dir.row;
        const newCol = currentCol + dir.col;
        const cellKey = `${newRow},${newCol}`;
        
        return newRow >= 0 && newRow < gridSize &&
               newCol >= 0 && newCol < gridSize &&
               !usedCells.has(cellKey) &&
               !path.some(p => p.row === newRow && p.col === newCol);
      });

      if (validMoves.length === 0) {
        return null; // Can't continue path
      }

      // Prefer continuing in similar direction (more snake-like)
      const lastDir = i > 1 ? {
        row: currentRow - path[i-2].row,
        col: currentCol - path[i-2].col
      } : null;

      let chosenDir;
      if (lastDir && Math.random() < 0.6) { // 60% chance to continue in similar direction
        const similarMoves = validMoves.filter(dir => 
          Math.abs(dir.row - lastDir.row) <= 1 && Math.abs(dir.col - lastDir.col) <= 1
        );
        chosenDir = similarMoves.length > 0 ? 
          similarMoves[Math.floor(Math.random() * similarMoves.length)] :
          validMoves[Math.floor(Math.random() * validMoves.length)];
      } else {
        chosenDir = validMoves[Math.floor(Math.random() * validMoves.length)];
      }

      currentRow += chosenDir.row;
      currentCol += chosenDir.col;
      path.push({ row: currentRow, col: currentCol });
    }

    return path;
  }

  /**
   * Check if word can be placed at the given path
   */
  canPlaceWord(grid, word, path, usedCells) {
    if (path.length !== word.length) return false;

    for (let i = 0; i < path.length; i++) {
      const pos = path[i];
      const cellKey = `${pos.row},${pos.col}`;
      
      if (usedCells.has(cellKey)) return false;
      
      // Allow overwriting only if it's the same letter
      if (grid[pos.row][pos.col] !== '' && grid[pos.row][pos.col] !== word[i]) {
        return false;
      }
    }
    return true;
  }

  /**
   * Fill empty spaces with random letters
   */
  fillEmptySpaces(grid, gridSize) {
    const letters = 'ABCDEFGHIJKLMNOPQRSTUVWXYZ';
    for (let row = 0; row < gridSize; row++) {
      for (let col = 0; col < gridSize; col++) {
        if (grid[row][col] === '') {
          grid[row][col] = letters[Math.floor(Math.random() * letters.length)];
        }
      }
    }
  }

  // ===========================================
  // UTILITY METHODS
  // ===========================================

  /**
   * Generate UUID for puzzle ID
   */
  generateUUID() {
    return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, function(c) {
      const r = Math.random() * 16 | 0;
      const v = c == 'x' ? r : (r & 0x3 | 0x8);
      return v.toString(16);
    });
  }

  /**
   * Debug logging
   */
  debugLog(message, type = 'info') {
    if (this.debugMode) {
      const timestamp = new Date().toISOString();
      const emoji = type === 'error' ? '❌' : type === 'warning' ? '⚠️' : type === 'success' ? '✅' : '🐍';
      console.log(`${emoji} [${timestamp}] wordsnake: ${message}`);
    }
  }

  /**
   * Enable/disable debug mode for both this service and unified service
   */
  setDebugMode(enabled) {
    this.debugMode = enabled;
    unifiedWordService.setDebugMode(enabled);
    this.debugLog(`Debug mode ${enabled ? 'enabled' : 'disabled'} for WordSnake and UnifiedWordService`);
  }

  /**
   * Health check - verify service is working including unified service
   */
  async healthCheck() {
    try {
      // Check unified word service
      const unifiedHealth = await unifiedWordService.healthCheck();
      
      // Test unified service connectivity
      const testResult = await unifiedWordService.getWords({
        count: 1,
        difficulty: 'easy',
        puzzleType: 'wordsnake',
        allowAI: false
      });
      
      return {
        status: 'healthy',
        service: 'WordSnakePuzzleGenerator',
        unifiedWordService: unifiedHealth,
        unifiedServiceConnectivity: testResult.success,
        sessionId: this.sessionId,
        timestamp: new Date().toISOString()
      };
  
    } catch (error) {
      return {
        status: 'unhealthy',
        service: 'WordSnakePuzzleGenerator',
        error: error.message,
        sessionId: this.sessionId,
        timestamp: new Date().toISOString()
      };
    }
  }

  /**
   * Get generation statistics including unified service stats
   */
  async getGenerationStats(difficulty = null) {
    try {
      // Get stats from unified service
      const unifiedStats = unifiedWordService.getSessionStats();
      
      // Get local configuration info
      let config;
        if (difficulty) {
        const normalizedDifficulty = difficulty.toLowerCase();
        config = this.difficultyConfig[normalizedDifficulty] || this.difficultyConfig.easy;
        } else {
        config = this.difficultyConfig;
        }
      
      return {
        service: 'WordSnakePuzzleGenerator',
        difficulty: difficulty || 'all',
        localConfig: config,
        unifiedServiceStats: unifiedStats,
        sessionId: this.sessionId,
        timestamp: new Date().toISOString()
      };

    } catch (error) {
      this.debugLog(`❌ Stats error: ${error.message}`, 'error');
      return {
        service: 'WordSnakePuzzleGenerator',
        error: error.message,
        sessionId: this.sessionId,
        timestamp: new Date().toISOString()
      };
    }
  }
}

// Export singleton instance
export const wordSnakeGeneratorService = new WordSnakePuzzleGenerator();

/**
 * Enhanced Word Snake Generator for integration with existing puzzle system
 */
export class EnhancedWordSnakeGenerator {
  constructor() {
    this.service = wordSnakeGeneratorService;
  }

  /**
   * Generate word snake puzzle compatible with existing system
   */
  async generateWordSnakePuzzle(difficulty = 'medium', gridSize = 8, topic = null) {
    try {
      console.log(`🐍 Enhanced word snake generation for ${difficulty} difficulty${topic ? ` with topic: ${topic}` : ''}`);
      
      // Generate using the service with unified word procurement
      const puzzleData = await this.service.generatePuzzle(difficulty, gridSize);
      
      // Format for the existing puzzle system
      const result = {
        // Keep existing fields for compatibility
        puzzleId: puzzleData.puzzleId,
        puzzleType: 'wordsnake',
        grid: puzzleData.grid,
        words: puzzleData.words,
        gridSize: puzzleData.gridSize,
        difficulty: difficulty,
        instruction: `Find ${puzzleData.words.length} hidden words by tracing snake-like paths`,
        hint: `Look for: ${puzzleData.words.map(w => w.clue).join(', ')}`,
        source: 'unified_word_service',
        timestamp: puzzleData.timestamp,
        sessionStats: puzzleData.sessionStats,
        
        // ADD these fields for puzzle generation system:
        question: JSON.stringify({
          grid: puzzleData.grid,
          words: puzzleData.words,
          gridSize: puzzleData.gridSize,
          instruction: `Find ${puzzleData.words.length} hidden words by tracing snake-like paths`
        }),
        answer: puzzleData.words.map(w => w.word).join(',')
      };

      console.log(`✅ Enhanced word snake puzzle generated with ${result.words.length} words from unified service`);
      
      return {
        success: true,
        puzzleData: result
      };

    } catch (error) {
      console.error(`❌ Enhanced word snake generation failed: ${error.message}`);
      return {
        success: false,
        message: error.message
      };
    }
  }

  /**
   * Generate topic-specific word snake puzzle
   */
  async generateTopicWordSnakePuzzle(topic, difficulty = 'medium', gridSize = 8) {
    try {
      console.log(`🐍 Topic word snake generation for "${topic}" (${difficulty})`);
      
      // Start session
      this.service.startGenerationSession();
      
      // Get topic words from unified service
      const words = await this.service.selectTopicWordsFromUnifiedService(topic, difficulty);

      // Fix: Normalize difficulty and use fallback
      const normalizedDifficulty = difficulty.toLowerCase();
      const config = this.service.difficultyConfig[normalizedDifficulty] || this.service.difficultyConfig.easy;

      if (!words || words.length < config.minWords) {
        throw new Error(`Insufficient topic words: got ${words?.length || 0}`);
      }
      
      const grid = this.service.createGrid(gridSize);
      const placedWords = this.service.placeWordsInGrid(grid, words, gridSize);
      this.service.fillEmptySpaces(grid, gridSize);
      
      const puzzleData = {
        puzzleId: this.service.generateUUID(),
        puzzleType: 'wordsnake',
        grid: grid,
        words: placedWords.map((word, index) => ({
          word: word.word.toUpperCase(),
          clue: word.clue || `Find: ${word.word}`,
          path: word.path,
          color: this.service.colors[index % this.service.colors.length],
          found: false
        })),
        gridSize: gridSize,
        difficulty: difficulty,
        topic: topic,
        instruction: `Find ${placedWords.length} words related to ${topic} by tracing snake-like paths`,
        hint: `Topic: ${topic} - Look for: ${placedWords.map(w => w.clue).join(', ')}`,
        source: 'unified_word_service_topic',
        timestamp: new Date().toISOString(),
        sessionStats: this.service.getSessionStats()
      };

      console.log(`✅ Topic word snake puzzle generated with ${puzzleData.words.length} words for ${topic}`);
      
      return {
        success: true,
        puzzleData: puzzleData
      };

    } catch (error) {
      console.error(`❌ Topic word snake generation failed: ${error.message}`);
      return {
        success: false,
        message: error.message
      };
    }
  }

  /**
   * Set debug mode
   */
  setDebugMode(enabled) {
    this.service.setDebugMode(enabled);
  }

  /**
   * Get generation statistics
   */
  async getStats(difficulty = null) {
    return await this.service.getGenerationStats(difficulty);
  }

  /**
   * Health check
   */
  async healthCheck() {
    return await this.service.healthCheck();
  }
}

// Export enhanced generator instance
export const enhancedWordSnakeGenerator = new EnhancedWordSnakeGenerator();

// Express.js route handler with unified word service integration
const router = express.Router();

// API endpoint to match your existing structure
router.get('/fetch-next-puzzle-ios/wordsnake', async (req, res) => {
  try {
    const { userId, email, difficulty = 'easy', topic } = req.query;
    
    console.log(`🐍 Word Snake request: user=${userId}, difficulty=${difficulty}${topic ? `, topic=${topic}` : ''}`);
    
    // Generate new puzzle using enhanced generator with unified word service
    const result = topic ? 
      await enhancedWordSnakeGenerator.generateTopicWordSnakePuzzle(topic, difficulty) :
      await enhancedWordSnakeGenerator.generateWordSnakePuzzle(difficulty);
    
    if (!result.success) {
      throw new Error(result.message);
    }
    
    const puzzleData = result.puzzleData;
    
    // Format to match your existing API exactly
    const response = {
      success: true,
      puzzleData: {
        puzzleId: puzzleData.puzzleId,
        puzzleType: 'wordsnake',
        question: JSON.stringify({
          grid: puzzleData.grid,
          words: puzzleData.words,
          gridSize: puzzleData.gridSize,
          topic: puzzleData.topic
        }),
        answer: puzzleData.words.map(w => w.word).join(','),
        hint: puzzleData.hint,
        difficulty: difficulty,
        generatedBy: 'unified_word_service',
        validatedBy: 'system',
        timestamp: puzzleData.timestamp,
        options: [],
        correct_option: puzzleData.words.map(w => w.word).join(','),
        sessionStats: puzzleData.sessionStats
      },
      remainingPuzzles: 167,
      generatingMore: false
    };
    
    // Log puzzle details for debugging
    console.log('📊 Puzzle words:', puzzleData.words.map(w => `${w.word}: ${w.clue}`));
    console.log('📈 Session stats:', puzzleData.sessionStats);
    
    res.json(response);
  } catch (error) {
    console.error('❌ Error generating word snake puzzle:', error);
    res.status(500).json({
      success: false,
      error: 'Failed to generate puzzle',
      details: error.message
    });
  }
});

// Health check endpoint
router.get('/word-snake/health', async (req, res) => {
  try {
    const health = await enhancedWordSnakeGenerator.healthCheck();
    res.json(health);
  } catch (error) {
    res.status(500).json({
      status: 'unhealthy',
      error: error.message,
      timestamp: new Date().toISOString()
    });
  }
});

// Statistics endpoint for debugging
router.get('/word-snake/stats', async (req, res) => {
  try {
    const { difficulty } = req.query;
    const stats = await enhancedWordSnakeGenerator.getStats(difficulty);
    
    res.json({
      success: true,
      difficulty: difficulty || 'all',
      stats: stats
    });
  } catch (error) {
    console.error('Error getting word snake stats:', error);
    res.status(500).json({ 
      success: false, 
      error: error.message 
    });
  }
});

// Debug endpoint to test word selection from unified service
router.get('/word-snake/debug/:difficulty', async (req, res) => {
  try {
    const { difficulty } = req.params;
    const { topic } = req.query;
    
    const service = wordSnakeGeneratorService;
    
    // Enable debug mode temporarily
    service.setDebugMode(true);
    
    // Get sample words from unified service
    const words = topic ? 
      await service.selectTopicWordsFromUnifiedService(topic, difficulty) :
      await service.selectWordsFromUnifiedService(difficulty);
    
    service.setDebugMode(false);
    // Fix: Normalize difficulty for config lookup
    const normalizedDifficulty = difficulty.toLowerCase();
    
    res.json({
      difficulty,
      topic: topic || 'none',
      config: service.difficultyConfig[normalizedDifficulty] || service.difficultyConfig.easy,
      sampleWords: words,
      totalFound: words.length,
      sessionStats: service.getSessionStats(),
      source: 'unified_word_service'
    });
  } catch (error) {
    res.status(500).json({ 
      error: error.message,
      source: 'debug_endpoint'
    });
  }
});

export { WordSnakePuzzleGenerator, router };