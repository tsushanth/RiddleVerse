// dailyCrosswordGenerator.js - Production crossword generator using proven approach
import { BeamSearch5x5CrosswordGenerator } from './crosswordGeneratorService.js';

export class DailyCrosswordGenerator {
  constructor(supabase = null) {
    this.supabase = supabase;
    this.generator = new BeamSearch5x5CrosswordGenerator();
    this.debugMode = false;
    
    // Optimize for better results
    this.generator.maxTimeMs = 60000; // 1 minute per attempt
    this.generator.maxBeamWidth = 5; // More beam width for better exploration
  }

  /**
   * Set debug mode
   */
  setDebugMode(enabled) {
    this.debugMode = enabled;
  }
  

  /**
   * Debug logging helper
   */
  debugLog(message, type = 'info') {
    if (this.debugMode) {
      const timestamp = new Date().toISOString();
      const emoji = type === 'error' ? '❌' : type === 'success' ? '✅' : type === 'warning' ? '⚠️' : '🔄';
      console.log(`${emoji} [${timestamp}] DAILY_CROSSWORD: ${message}`);
    }
  }

  /**
   * Initialize Supabase if not already set
   */
  async initSupabase() {
    if (!this.supabase) {
      try {
        const { supabase } = await import('../config/database.js');
        this.supabase = supabase;
        this.debugLog('Supabase initialized successfully', 'success');
      } catch (error) {
        throw new Error(`Failed to initialize Supabase: ${error.message}`);
      }
    }
  }

  /**
   * Generate a daily crossword with the proven multi-attempt strategy
   */
  async generateDailyCrossword(difficulty = 'medium', maxAttempts = 50) {
    this.debugLog(`🎯 Generating ${difficulty} daily crossword (max ${maxAttempts} attempts)...`);
    
    // Initialize Supabase if needed
    await this.initSupabase();
    
    const config = this.getDifficultyConfig(difficulty);
    let bestResult = null;
    let bestScore = -1;
    
    for (let attempt = 1; attempt <= maxAttempts; attempt++) {
      // Get fresh word set every 5 attempts (proven strategy from test)
      if (attempt === 1 || attempt % 5 === 0) {
        this.debugLog(`🎲 Getting new random words for attempt ${attempt}...`);
      }
      
      // Get words from database
      const words = await this.getRandomWords(config);
      
      if (!words || words.length < config.minWords) {
        this.debugLog(`⚠️ Insufficient words for attempt ${attempt}, skipping...`, 'warning');
        continue;
      }
      
      // Analyze intersection potential
      const intersectionScore = this.analyzeIntersectionPotential(words);
      
      // Skip word sets with very low intersection potential
      if (intersectionScore < 2.0 && attempt < maxAttempts - 5) {
        this.debugLog(`⚠️ Low intersection potential (${intersectionScore.toFixed(1)}), getting new words...`, 'warning');
        continue;
      }
      
      // Generate crossword
      const result = await this.generator.generate5x5Crossword(words);
      
      if (result) {
        const score = this.calculateQualityScore(result);
        
        if (score > bestScore) {
          bestResult = result;
          bestScore = score;
          this.debugLog(`✅ Attempt ${attempt}: ${result.words.length} words, ${result.emptySpaces} empty spaces (score: ${score})`, 'success');
        }
        
        // Early exit if we get an excellent result
        if (this.isExcellentResult(result, config)) {
          this.debugLog(`🎉 Excellent result at attempt ${attempt}! Stopping early.`, 'success');
          break;
        }
      }
      
      // Progress indicator
      if (attempt % 10 === 0) {
        this.debugLog(`📊 Progress: ${attempt}/${maxAttempts} attempts...`);
      }
    }
    
    if (bestResult) {
      this.debugLog(`\n🏆 FINAL RESULT: ${bestResult.words.length} words, ${bestResult.emptySpaces} empty spaces`, 'success');
      
      // Format for storage with stats
      const formattedData = this.generator.formatForStorage(bestResult, difficulty);
      
      return {
        success: true,
        puzzleData: formattedData,
        stats: {
          wordsPlaced: bestResult.words.length,
          emptySpaces: bestResult.emptySpaces,
          efficiency: `${((25 - bestResult.emptySpaces) / 25 * 100).toFixed(1)}%`
        }
      };
    }
    
    return {
      success: false,
      message: `Failed to generate crossword after ${maxAttempts} attempts`
    };
  }

  /**
   * Get difficulty-specific configuration
   */
  getDifficultyConfig(difficulty) {
    const configs = {
      easy: {
        minFrequency: 3000,
        maxLength: 6,
        targetWords: 8,
        minWords: 6,
        fetchMultiplier: 4
      },
      medium: {
        minFrequency: 1500,
        maxLength: 8,
        targetWords: 10,
        minWords: 7,
        fetchMultiplier: 4
      },
      hard: {
        minFrequency: 800,
        maxLength: 10,
        targetWords: 10,
        minWords: 8,
        fetchMultiplier: 5
      }
    };
    
    return configs[difficulty.toLowerCase()] || configs.medium;
  }

  /**
   * Get random words from database - using the SAME function as regular crosswords
   */
  async getRandomWords(config) {
    try {
      // Ensure Supabase is initialized
      await this.initSupabase();
      
      // Map difficulty for the database
      const dbDifficulty = this.mapDifficultyForDB(config);
      
      // Use get_random_crossword_words which returns COMMON, RECOGNIZABLE words
      const { data: words, error } = await this.supabase.rpc('get_random_crossword_words', {
        difficulty: dbDifficulty,
        count: config.targetWords * config.fetchMultiplier
      });
      
      if (error) {
        this.debugLog(`Database error: ${error.message}`, 'error');
        return null;
      }
      
      if (!words || words.length === 0) {
        this.debugLog('No words returned from database', 'error');
        return null;
      }
      
      // The function returns: word, length, part_of_speech, definition, overlap_score, pattern_score, definition_quality_score, crossword_score
      // Filter and format words - ensure they match the crossword format
      const validWords = words
        .filter(w => 
          w.word && 
          w.word.length >= 3 && 
          w.word.length <= config.maxLength &&
          /^[A-Z]+$/i.test(w.word) &&
          w.definition // Must have a definition
        )
        .slice(0, config.targetWords)
        .map(w => ({
          word: w.word.toUpperCase(),
          hint: w.definition || this.generateHint(w.word),
          crossword_score: w.crossword_score || 0  // Use crossword_score instead of frequency
        }));
      
      this.debugLog(`✅ Retrieved ${validWords.length} valid, common words from database`, 'success');
      
      // Log the words for verification
      if (this.debugMode) {
        this.debugLog(`📝 Words: ${validWords.map(w => w.word).join(', ')}`);
        this.debugLog(`📊 Avg crossword score: ${(validWords.reduce((sum, w) => sum + w.crossword_score, 0) / validWords.length).toFixed(2)}`);
      }
      
      return validWords;
      
    } catch (error) {
      this.debugLog(`Error fetching words: ${error.message}`, 'error');
      return null;
    }
  }

  /**
   * Map difficulty to database-friendly format
   */
  mapDifficultyForDB(config) {
    // Map our config back to difficulty string for the RPC
    if (config.minFrequency >= 3000) return 'easy';
    if (config.minFrequency >= 1500) return 'medium';
    return 'hard';
  }

  /**
   * Analyze intersection potential of word set
   */
  analyzeIntersectionPotential(words) {
    let totalIntersections = 0;
    const wordList = words.map(w => w.word);
    
    for (let i = 0; i < wordList.length; i++) {
      for (let j = i + 1; j < wordList.length; j++) {
        totalIntersections += this.countSharedLetters(wordList[i], wordList[j]);
      }
    }
    
    return words.length > 0 ? totalIntersections / words.length : 0;
  }

  /**
   * Count shared letters between two words
   */
  countSharedLetters(word1, word2) {
    let count = 0;
    for (let i = 0; i < word1.length; i++) {
      for (let j = 0; j < word2.length; j++) {
        if (word1[i] === word2[j]) {
          count++;
        }
      }
    }
    return count;
  }

  /**
   * Calculate quality score for a result
   */
  calculateQualityScore(result) {
    return result.words.length * 1000 - result.emptySpaces * 25;
  }

  /**
   * Check if result meets "excellent" criteria
   */
  isExcellentResult(result, config) {
    const wordRatio = result.words.length / config.targetWords;
    const efficiency = (25 - result.emptySpaces) / 25;
    
    return wordRatio >= 0.8 && efficiency >= 0.7 && result.emptySpaces <= 7;
  }

  /**
   * Generate a hint for a word
   */
  generateHint(word) {
    return `${word.length} letter word starting with ${word.charAt(0).toUpperCase()}`;
  }

  /**
   * Test the generator with static word set (for validation)
   */
  async testWithStaticWords() {
    const staticWords = [
      { word: 'FAD', hint: 'Temporary fashion or trend' },
      { word: 'BOLO', hint: 'Southwestern type of tie' },
      { word: 'CRIME', hint: 'Illegal act' },
      { word: 'GALS', hint: 'Informal term for girls' },
      { word: 'INS', hint: 'Those currently in power' },
      { word: 'CGI', hint: 'Computer-generated imagery' },
      { word: 'BRAN', hint: 'Cereal fiber' },
      { word: 'FOILS', hint: 'Thwarts or defeats' },
      { word: 'ALMS', hint: 'Charitable donations' },
      { word: 'DOE', hint: 'Female deer' }
    ];
    
    this.debugLog('🧪 Testing with proven static word set...');
    const result = await this.generator.generate5x5Crossword(staticWords);
    
    if (result && result.words.length >= 9) {
      this.debugLog('✅ Static word test PASSED', 'success');
      return true;
    } else {
      this.debugLog('❌ Static word test FAILED', 'error');
      return false;
    }
  }
}

// Example usage:
// import { createClient } from '@supabase/supabase-js';
// const supabase = createClient(url, key);
// const generator = new DailyCrosswordGenerator(supabase);
// const crossword = await generator.generateDailyCrossword('medium', 50);