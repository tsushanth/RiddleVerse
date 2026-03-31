import axios from 'axios';
import { supabase } from '../config/database.js';

export class WordPrefixGenerator {
  constructor() {
    this.dataMuseCache = new Map();
    this.cacheExpiry = 60 * 60 * 1000; // 1 hour
    
    // Track used prefixes in memory (for current session)
    this.usedPrefixes = new Set();
    
    // Minimum word thresholds by difficulty
    this.minWordThresholds = {
      'Easy': 15,
      'Medium': 20, 
      'Hard': 25
    };

    
    // HARDCODED PREFIXES - curated and tested
    this.hardcodedPrefixes = {
      'Easy': [
        // 2-3 letter prefixes with many common words
        'st', 'tr', 'pr', 'br', 'cr', 'dr', 'fr', 'gr',
        'ch', 'sh', 'th', 'wh', 'pl', 'cl', 'bl', 'fl',
        'sc', 'sp', 'sk', 'sm', 'sn', 'sw', 'tw',
        're', 'un', 'in', 'ex', 'be', 'de', 'an', 'en',
        'co', 'pa', 'ma', 'sa', 'bo', 'ca', 'do', 'ho',
      ],
      'Medium': [
        // 3-4 letter prefixes
        'pre', 'pro', 'dis', 'mis', 'con', 'com', 'sub', 'out',
        'str', 'spr', 'scr', 'thr', 'squ', 'res', 'und', 'int',
        'man', 'per', 'sup', 'tra', 'obs', 'exp', 'ref', 'def',
        'non', 'rev', 'inv', 'adv', 'rec', 'dep', 'app'
      ],
      'Hard': [
        // 4+ letter prefixes and complex ones
        'trans', 'inter', 'super', 'micro', 'macro', 'ultra',
        'auto', 'anti', 'multi', 'over', 'under', 'post',
        'fore', 'circ', 'meta', 'infra', 'hyper', 'tele',
        'tech', 'bio', 'chem', 'env', 'anthro', 'geo', 'ling'
      ]
    };
    
    // Letter frequency weights for fallback generation
    this.letterWeights = {
      'a': 8.2, 'b': 1.5, 'c': 2.8, 'd': 4.3, 'e': 12.0, 'f': 2.2,
      'g': 2.0, 'h': 6.1, 'i': 7.0, 'j': 0.15, 'k': 0.77, 'l': 4.0,
      'm': 2.4, 'n': 6.7, 'o': 7.5, 'p': 1.9, 'q': 0.095, 'r': 6.0,
      's': 6.3, 't': 9.1, 'u': 2.8, 'v': 0.98, 'w': 2.4, 'x': 0.15,
      'y': 2.0, 'z': 0.074
    };
  }

  /**
   * Generate complete word prefix puzzle with all possible words
   */
  async generatePrefixPuzzle(difficulty = 'Medium') {
    console.log(`🎯 Generating complete word prefix puzzle (${difficulty})`);
    
    try {
      // Select appropriate prefix (stats first, then hardcoded, then dynamic)
      const prefixResult = await this.selectPrefix(difficulty);
      const prefix = prefixResult.prefix || prefixResult; // Handle both formats
      
      let wordList;
      
      // If we have pre-calculated words from stats, use them
      if (prefixResult.preCalculatedWords) {
        console.log(`🚀 Using pre-calculated words from stats table`);
        wordList = prefixResult.preCalculatedWords.map(w => ({
          word: w,                 // ✅ w is a string
          frequency: 100,
          rarity: 'common',
          source: 'prefix_word_stats'
        }));
      } else {
        // Get words using existing method
        wordList = await this.getWordsForPrefix(prefix, 500);
      }
      
      if (!wordList || wordList.length < 10) {
        throw new Error(`Insufficient words found for prefix: ${prefix}`);
      }
  
      // Filter and validate words
      const validWords = wordList.filter(w => 
        w && w.word && typeof w.word === 'string' && w.word.length > 0
      );
  
      if (validWords.length < 10) {
        throw new Error(`Insufficient valid words for prefix: ${prefix} (only ${validWords.length} valid)`);
      }
  
      // Generate complete puzzle data
      const puzzleData = {
        prefix: prefix,
        difficulty: difficulty,
        timeLimit: this.getTimeLimit(difficulty),
        allWords: validWords.map(w => ({
          word: w.word,
          length: w.word.length,
          points: this.calculateWordPoints(w.word, w),
          rarity: w.rarity || 'common',
          frequency: w.frequency || 1
        })),
        scoring: {
          basePointsPerWord: difficulty === 'Easy' ? 10 : difficulty === 'Medium' ? 15 : 20,
          lengthMultiplier: {
            3: 1.0, 4: 1.2, 5: 1.5, 6: 2.0, 7: 2.5, 8: 3.0, 9: 4.0, 10: 5.0
          },
          rarityBonus: {
            common: 0,
            uncommon: 5,
            rare: 15
          },
          speedBonus: {
            maxBonus: 100,
            timeWindow: 30
          }
        },
        targets: {
          bronze: Math.ceil(validWords.length * 0.2),
          silver: Math.ceil(validWords.length * 0.4),
          gold: Math.ceil(validWords.length * 0.6)
        },
        hint: `Find words that start with "${prefix.toUpperCase()}" - ${validWords.length} possible words!`,
        metadata: {
          generatedAt: new Date().toISOString(),
          totalWords: validWords.length,
          source: prefixResult.source || 'unknown',
          expectedDifficulty: difficulty,
          prefixSource: prefixResult.source || 'fallback'
        }
      };
  
      return {
        success: true,
        puzzleData: puzzleData,
        generationMethod: `stats_first_${prefixResult.source || 'fallback'}`
      };
  
    } catch (error) {
      console.error(`❌ Word prefix generation error:`, error);
      return {
        success: false,
        message: error.message,
        fallbackSuggested: true
      };
    }
  }

  /**
   * Select prefix - HARDCODED FIRST, then dynamic fallback
   */
  async selectPrefix(difficulty) {
    console.log(`🎯 Selecting prefix for ${difficulty} difficulty`);
    
    // STEP 1: Try to get unused prefix from stats table (NEW - FIRST PRIORITY)
    try {
      const statsResult = await this.getUnusedPrefixFromStats(difficulty);
      if (statsResult) {
        console.log(`✅ Selected prefix from stats: "${statsResult.prefix}" with ${statsResult.words.length} pre-calculated words`);
        
        // Mark as used and return the full result
        await this.markPrefixAsUsed(statsResult.prefix, difficulty, statsResult.words.length);
        
        return {
          prefix: statsResult.prefix,
          preCalculatedWords: statsResult.words,
          source: 'prefix_word_stats'
        };
      }
    } catch (error) {
      console.warn(`⚠️ Failed to get prefix from stats, continuing to hardcoded: ${error.message}`);
    }
    
    // STEP 2: Try to get unused hardcoded prefix (SECOND PRIORITY)
    const unusedHardcoded = await this.getUnusedHardcodedPrefix(difficulty);
    if (unusedHardcoded) {
      console.log(`✅ Selected hardcoded prefix: "${unusedHardcoded}"`);
      
      // Mark as used and return
      this.usedPrefixes.add(unusedHardcoded);
      await this.markPrefixAsUsed(unusedHardcoded, difficulty, 0); // Will update word count later
      
      return {
        prefix: unusedHardcoded,
        source: 'hardcoded'
      };
    }
    
    console.log(`⚠️ No unused hardcoded prefixes for ${difficulty}, falling back to dynamic generation`);
    
    // STEP 3: Fallback to dynamic generation (THIRD PRIORITY)
    const dynamicPrefix = await this.selectDynamicPrefix(difficulty);
    return {
      prefix: dynamicPrefix,
      source: 'dynamic'
    };
  }

  async getUnusedPrefixFromStats(difficulty) {
    const { data, error } = await supabase
      .from('prefix_word_stats')
      .select('prefix, words')
      .eq('difficulty', difficulty);
    
    if (error) {
      console.error(`❌ Failed to fetch prefix_word_stats:`, error.message);
      return null;
    }
    
    const candidates = data.filter(p => !this.usedPrefixes.has(p.prefix));
    if (candidates.length === 0) return null;
    
    const selected = candidates[Math.floor(Math.random() * candidates.length)];
    this.usedPrefixes.add(selected.prefix);
    
    return {
      prefix: selected.prefix,
      words: selected.words
    };
  }

  /**
   * Get unused hardcoded prefix
   */
  async getUnusedHardcodedPrefix(difficulty) {
    const hardcodedList = this.hardcodedPrefixes[difficulty] || this.hardcodedPrefixes['Medium'];
    
    // Get all used prefixes from database
    const usedPrefixes = await this.getAllUsedPrefixes();
    
    // Filter out used ones
    const unusedPrefixes = hardcodedList.filter(prefix => 
      !usedPrefixes.has(prefix) && !this.usedPrefixes.has(prefix)
    );
    
    if (unusedPrefixes.length === 0) {
      console.log(`📝 All ${hardcodedList.length} hardcoded ${difficulty} prefixes have been used`);
      return null;
    }
    
    // Return random unused prefix
    const selectedPrefix = unusedPrefixes[Math.floor(Math.random() * unusedPrefixes.length)];
    console.log(`🎲 Selected from ${unusedPrefixes.length} unused hardcoded prefixes: "${selectedPrefix}"`);
    
    return selectedPrefix;
  }

  /**
   * Get all used prefixes from database
   */
  async getAllUsedPrefixes() {
    const usedSet = new Set();
    
    try {
      // Get from puzzles table
      const { data: puzzlePrefixes } = await supabase
        .from('puzzles')
        .select('question')
        .ilike('type', 'wordprefix');
      
      if (puzzlePrefixes) {
        puzzlePrefixes.forEach(p => usedSet.add(p.question));
      }
      
      // Get from used_prefixes table (if it exists)
      const { data: usedPrefixes } = await supabase
        .from('used_prefixes')
        .select('prefix');
      
      if (usedPrefixes) {
        usedPrefixes.forEach(p => usedSet.add(p.prefix));
      }
      
    } catch (error) {
      console.warn(`⚠️ Could not check used prefixes: ${error.message}`);
    }
    
    return usedSet;
  }

  /**
   * Dynamic prefix selection (fallback when hardcoded exhausted)
   */
  async selectDynamicPrefix(difficulty) {
    console.log(`🎲 Starting dynamic prefix generation for ${difficulty}`);
    
    const prefixLength = this.getPrefixLength(difficulty);
    const minThreshold = this.minWordThresholds[difficulty] || 15;
    const maxAttempts = 50;
    
    for (let attempt = 0; attempt < maxAttempts; attempt++) {
      const prefix = this.generateRandomPrefix(prefixLength);
      
      console.log(`🎲 Dynamic attempt ${attempt + 1}: Testing prefix "${prefix}"`);
      
      // Check if already used
      if (await this.isPrefixAlreadyUsed(prefix)) {
        console.log(`⏭️ Prefix "${prefix}" already used, trying another`);
        continue;
      }
      
      // Test quality and feasibility
      const qualityCheck = await this.testPrefixQualityAndFeasibility(prefix);
      
      if (qualityCheck.isViable && qualityCheck.wordCount >= minThreshold) {
        console.log(`✅ Dynamic prefix "${prefix}" is viable with ${qualityCheck.wordCount} words (${qualityCheck.commonPercentage}% common)`);
        
        this.usedPrefixes.add(prefix);
        await this.markPrefixAsUsed(prefix, difficulty, qualityCheck.wordCount);
        
        return prefix;
      } else {
        console.log(`❌ Dynamic prefix "${prefix}" rejected: ${qualityCheck.reason}`);
        await this.markPrefixAsRejected(prefix, difficulty, qualityCheck.wordCount || 0);
      }
    }
    
    // Ultimate fallback - use a known good prefix even if "used"
    console.log(`💥 Dynamic generation failed, using emergency fallback`);
    return this.getEmergencyFallbackPrefix(difficulty);
  }

  /**
   * Test prefix quality using common_words table
   */
  async testPrefixQualityAndFeasibility(prefix) {
    try {
      // Get words from DataMuse
      const response = await axios.get('https://api.datamuse.com/words', {
        params: {
          sp: `${prefix}*`,
          max: 50, // Smaller sample for testing
          md: 'f'
        },
        timeout: 5000
      });

      const validWords = response.data.filter(item => 
        this.isValidWord(item.word, prefix)
      );

      if (validWords.length < 5) {
        return {
          isViable: false,
          reason: `Only ${validWords.length} valid words found`,
          wordCount: validWords.length
        };
      }

      // Check against common_words table
      const wordList = validWords.map(w => w.word);
      const { data: commonWords, error } = await supabase
        .from('common_words')
        .select('word')
        .in('word', wordList);

      if (error) {
        console.warn(`⚠️ Could not check common_words table: ${error.message}`);
        // If we can't check, be more lenient
        return {
          isViable: validWords.length >= 10,
          reason: validWords.length >= 10 ? 'Could not verify commonality but enough words' : 'Too few words',
          wordCount: validWords.length,
          commonPercentage: 0
        };
      }

      const commonWordCount = commonWords ? commonWords.length : 0;
      const commonPercentage = (commonWordCount / wordList.length) * 100;
      
      // NEW LOGIC: Use minimum common word count + reasonable percentage
      const minCommonWords = 10; // At least 10 common words
      const minPercentage = 25;  // Reduced from 40% to 25%
      const minTotalWords = 10;  // At least 10 total words
      
      const hasEnoughCommonWords = commonWordCount >= minCommonWords;
      const hasReasonablePercentage = commonPercentage >= minPercentage;
      const hasEnoughTotalWords = validWords.length >= minTotalWords;
      
      const isViable = hasEnoughCommonWords && hasReasonablePercentage && hasEnoughTotalWords;
      
      // Better reason reporting
      let reason;
      if (isViable) {
        reason = `✅ ${commonWordCount} common words (${Math.round(commonPercentage)}% of ${validWords.length} total)`;
      } else {
        const issues = [];
        if (!hasEnoughCommonWords) issues.push(`only ${commonWordCount} common words (need ${minCommonWords})`);
        if (!hasReasonablePercentage) issues.push(`only ${Math.round(commonPercentage)}% common (need ${minPercentage}%)`);
        if (!hasEnoughTotalWords) issues.push(`only ${validWords.length} total words (need ${minTotalWords})`);
        reason = `❌ ${issues.join(', ')}`;
      }
      
      return {
        isViable,
        reason,
        wordCount: validWords.length,
        commonWordCount,
        commonPercentage: Math.round(commonPercentage)
      };

    } catch (error) {
      console.error(`❌ Quality test failed for "${prefix}":`, error);
      return {
        isViable: false,
        reason: 'Quality test failed',
        wordCount: 0
      };
    }
  }

  /**
   * Enhanced word fetching with common_words validation
   */
  async getWordsForPrefix(prefix, maxWords = 500) {
    const cacheKey = `prefix_${prefix}_complete`;
    
    if (this.dataMuseCache.has(cacheKey)) {
      const cached = this.dataMuseCache.get(cacheKey);
      if (Date.now() - cached.timestamp < this.cacheExpiry) {
        return cached.words;
      }
    }

    const allWords = new Map();

    // Get from DataMuse API
    try {
      const response = await axios.get('https://api.datamuse.com/words', {
        params: {
          sp: `${prefix}*`,
          max: maxWords,
          md: 'f'
        },
        timeout: 10000
      });

      response.data.forEach(item => {
        if (this.isValidWord(item.word, prefix)) {
          allWords.set(item.word, {
            word: item.word,
            frequency: this.parseFrequency(item.tags),
            rarity: this.calculateRarity(item.tags),
            source: 'datamuse'
          });
        }
      });

    } catch (error) {
      console.error(`❌ DataMuse API error: ${error.message}`);
    }

    // Enhance with common_words table
    try {
      const allWordsList = Array.from(allWords.keys());
      
      // Get common words that start with prefix
      const { data: commonDbWords } = await supabase
        .from('common_words')
        .select('word')
        .ilike('word', `${prefix}%`)
        .limit(200);

      if (commonDbWords) {
        commonDbWords.forEach(w => {
          if (this.isValidWord(w.word, prefix)) {
            allWords.set(w.word, {
              word: w.word,
              frequency: 100, // Assume common words have high frequency
              rarity: 'common',
              source: 'common_words_verified'
            });
          }
        });
      }

      // Verify DataMuse words against common_words
      if (allWordsList.length > 0) {
        const { data: verifiedCommon } = await supabase
          .from('common_words')
          .select('word')
          .in('word', allWordsList);

        if (verifiedCommon) {
          verifiedCommon.forEach(vw => {
            if (allWords.has(vw.word)) {
              const existing = allWords.get(vw.word);
              allWords.set(vw.word, {
                ...existing,
                frequency: Math.max(existing.frequency, 100),
                rarity: 'common',
                source: 'common_words_verified'
              });
            }
          });
        }
      }

    } catch (error) {
      console.error('❌ Common words validation failed:', error);
    }

    const wordsArray = Array.from(allWords.values())
      .sort((a, b) => b.frequency - a.frequency);

    this.dataMuseCache.set(cacheKey, {
      words: wordsArray,
      timestamp: Date.now()
    });

    return wordsArray;
  }

  /**
   * Emergency fallback prefixes
   */
  getEmergencyFallbackPrefix(difficulty) {
    const emergency = {
      'Easy': 'the',
      'Medium': 'and', 
      'Hard': 'with'
    };
    return emergency[difficulty] || 'the';
  }

  // Keep all the existing utility methods: getPrefixLength, generateRandomPrefix, 
  // isValidWord, calculateWordPoints, getTimeLimit, etc.
  
  getPrefixLength(difficulty) {
    switch (difficulty) {
      case 'Easy': return 2;
      case 'Medium': return 3;
      case 'Hard': return 4;
      default: return 3;
    }
  }

  getTimeLimit(difficulty) {
    const timeLimits = {
      'Easy': 90,
      'Medium': 120,
      'Hard': 150
    };
    return timeLimits[difficulty] || 120;
  }

  calculateWordPoints(word, wordData) {
    const basePoints = 10;
    const lengthBonus = Math.max(0, word.length - 3) * 2;
    const rarityBonus = wordData.rarity === 'rare' ? 15 : 
                      wordData.rarity === 'uncommon' ? 5 : 0;
    return basePoints + lengthBonus + rarityBonus;
  }
  
  
    /**
     * Generate random prefix using letter frequency weights
     */
    generateRandomPrefix(length) {
      let prefix = '';
      
      for (let i = 0; i < length; i++) {
        // Use weighted random for first letter, then normal random
        if (i === 0) {
          prefix += this.getWeightedRandomLetter();
        } else {
          // Avoid double letters and some bad combinations
          const lastChar = prefix[prefix.length - 1];
          let nextChar;
          
          do {
            nextChar = this.getRandomLetter();
          } while (
            nextChar === lastChar || // No double letters
            this.isBadCombination(prefix + nextChar)
          );
          
          prefix += nextChar;
        }
      }
      
      return prefix.toLowerCase();
    }
  
    /**
     * Get weighted random letter (favors common letters)
     */
    getWeightedRandomLetter() {
      const letters = Object.keys(this.letterWeights);
      const weights = Object.values(this.letterWeights);
      const totalWeight = weights.reduce((sum, weight) => sum + weight, 0);
      
      let random = Math.random() * totalWeight;
      
      for (let i = 0; i < letters.length; i++) {
        random -= weights[i];
        if (random <= 0) {
          return letters[i];
        }
      }
      
      return 'a'; // Fallback
    }
  
    /**
     * Get simple random letter
     */
    getRandomLetter() {
      const letters = 'abcdefghijklmnopqrstuvwxyz';
      return letters[Math.floor(Math.random() * letters.length)];
    }
  
    /**
     * Check for obviously bad letter combinations
     */
    isBadCombination(prefix) {
      const badCombos = [
        'qq', 'qx', 'qz', 'xq', 'xz', 'zq', 'zx',
        'bx', 'cx', 'dx', 'fx', 'gx', 'hx', 'jx',
        'kx', 'lx', 'mx', 'nx', 'px', 'rx', 'sx',
        'tx', 'vx', 'wx', 'yx'
      ];
      
      return badCombos.some(combo => prefix.includes(combo));
    }
  
    /**
     * Check if prefix already used (memory + database)
     */
    /**
 * Check if prefix already used (memory + database)
 */
async isPrefixAlreadyUsed(prefix) {
    // Check memory first (current session)
    if (this.usedPrefixes.has(prefix)) {
      return true;
    }
    
    // Check database - handle case where tables might be empty or not exist
    try {
      // Check if prefix exists in puzzles table
      const { data: existing, error: puzzleError } = await supabase
        .from('puzzles')
        .select('puzzleid')
        .eq('type', 'wordprefix')
        .eq('question', prefix)
        .limit(1)
        .timeout(5000);
        
      if (puzzleError) {
        console.log(`⚠️ Could not check puzzles table: ${puzzleError.message}`);
      } else if (existing && existing.length > 0) {
        this.usedPrefixes.add(prefix); // Cache in memory
        return true;
      }
      
      // Check rejected prefixes table
      const { data: rejected, error: rejectedError } = await supabase
        .from('rejected_prefixes')
        .select('prefix')
        .eq('prefix', prefix)
        .limit(1);
        
      if (rejectedError) {
        console.log(`⚠️ Could not check rejected_prefixes table: ${rejectedError.message}`);
        // If table doesn't exist, that's fine - just continue
        return false;
      }
        
      return rejected && rejected.length > 0;
      
    } catch (error) {
      console.log(`⚠️ Database check failed, assuming prefix not used: ${error.message}`);
      return false; // If anything fails, assume not used
    }
  }
  
    /**
     * Quick feasibility test with DataMuse API
     */
    async testPrefixFeasibility(prefix) {
      try {
        console.log(`🔍 Testing feasibility of prefix "${prefix}"`);
        
        const response = await axios.get('https://api.datamuse.com/words', {
          params: {
            sp: `${prefix}*`,
            max: 100, // Just enough to count
            md: 'f'
          },
          timeout: 5000
        });
  
        const validWords = response.data.filter(item => 
          this.isValidWord(item.word, prefix)
        );
  
        console.log(`📊 Prefix "${prefix}" has ${validWords.length} valid words`);
        return validWords.length;
  
      } catch (error) {
        console.error(`❌ DataMuse feasibility test failed for "${prefix}":`, error.message);
        
        // Fallback to database check
        try {
          const { data: dbWords } = await supabase
            .from('word_frequencies')
            .select('word')
            .ilike('word', `${prefix}%`)
            .gte('frequency', 1)
            .limit(100);
  
          const validDbWords = dbWords ? dbWords.filter(w => 
            this.isValidWord(w.word, prefix)
          ) : [];
  
          console.log(`📊 Database fallback: Prefix "${prefix}" has ${validDbWords.length} valid words`);
          return validDbWords.length;
  
        } catch (dbError) {
          console.error(`❌ Database fallback also failed:`, dbError);
          return 0;
        }
      }
    }
  
    /**
     * Mark prefix as used
     */
    async markPrefixAsUsed(prefix, difficulty, wordCount) {
        try {
          const { error } = await supabase
            .from('used_prefixes')
            .insert({
              prefix: prefix,
              difficulty: difficulty,
              word_count: wordCount,
              used_at: new Date().toISOString(),
              status: 'used'
            });
              
          if (error) {
            if (error.code === '23505') { // Duplicate key error
              console.log(`📝 Prefix "${prefix}" already marked as used`);
            } else {
              console.warn(`⚠️ Could not mark prefix as used: ${error.message}`);
            }
          } else {
            console.log(`✅ Marked prefix "${prefix}" as used`);
          }
        } catch (error) {
          console.warn(`⚠️ Error marking prefix as used: ${error.message}`);
          // Don't fail generation if this fails
        }
      }
  
    /**
     * Mark prefix as rejected (too few words)
     */
    async markPrefixAsRejected(prefix, difficulty, wordCount) {
        try {
          const { error } = await supabase
            .from('rejected_prefixes')
            .insert({
              prefix: prefix,
              difficulty: difficulty,
              word_count: wordCount,
              rejected_at: new Date().toISOString(),
              reason: `Too few words: ${wordCount}`
            });
              
          if (error) {
            if (error.code === '23505') { // Duplicate key error
              console.log(`📝 Prefix "${prefix}" already marked as rejected`);
            } else {
              console.warn(`⚠️ Could not mark prefix as rejected: ${error.message}`);
            }
          } else {
            console.log(`📝 Marked prefix "${prefix}" as rejected (${wordCount} words)`);
          }
        } catch (error) {
          console.warn(`⚠️ Error marking prefix as rejected: ${error.message}`);
          // Don't fail generation if this fails
        }
      }
  
    /**
     * Fallback to known good prefixes if random generation fails
     */
    getFallbackPrefix(difficulty) {
      const fallbackPrefixes = {
        'Easy': ['st', 'tr', 'pr', 'br', 'ch', 'sh', 'th'],
        'Medium': ['str', 'pre', 'con', 'dis', 'out', 'over'],
        'Hard': ['trans', 'inter', 'under', 'super', 'anti']
      };
      
      const candidates = fallbackPrefixes[difficulty] || fallbackPrefixes['Medium'];
      return candidates[Math.floor(Math.random() * candidates.length)];
    }
  
    // Keep existing methods: isValidWord, getWordsForPrefix, etc.
    isValidWord(word, prefix) {
      if (!word || typeof word !== 'string') return false;
      
      const normalizedWord = word.toLowerCase();
      const normalizedPrefix = prefix.toLowerCase();
      
      return (
        normalizedWord.startsWith(normalizedPrefix) &&
        normalizedWord.length > normalizedPrefix.length &&
        normalizedWord.length <= 15 &&
        /^[a-z]+$/.test(normalizedWord) &&
        !normalizedWord.includes('-') &&
        !normalizedWord.includes("'")
      );
    }

  /**
   * Fallback: Get words from database
   */
  async getDatabaseWordsForPrefix(prefix) {
    try {
      const { data: words } = await supabase
        .from('word_frequencies')
        .select('word, frequency')
        .ilike('word', `${prefix}%`)
        .gte('frequency', 1)
        .order('frequency', { ascending: false })
        .limit(50);

      if (words) {
        return words.map(w => ({
          word: w.word,
          frequency: w.frequency,
          length: w.word.length,
          rarity: w.frequency < 10 ? 'rare' : w.frequency < 100 ? 'uncommon' : 'common'
        }));
      }

      return [];
    } catch (error) {
      console.error('❌ Database fallback failed:', error);
      return [];
    }
  }

  /**
   * Calculate scoring parameters based on word list
   */
  calculateScoringParams(wordList, difficulty) {
    const totalWords = wordList.length;
    const avgWordLength = wordList.reduce((sum, w) => sum + w.length, 0) / totalWords;
    
    const rareWords = wordList.filter(w => w.rarity === 'rare').length;
    const commonWords = wordList.filter(w => w.rarity === 'common').length;

    return {
      basePointsPerWord: difficulty === 'Easy' ? 10 : difficulty === 'Medium' ? 15 : 20,
      lengthMultiplier: {
        3: 1.0,
        4: 1.2,
        5: 1.5,
        6: 2.0,
        7: 2.5,
        8: 3.0
      },
      rarityBonus: {
        common: 0,
        uncommon: 5,
        rare: 15
      },
      speedBonus: {
        maxBonus: 100,
        timeWindow: 30 // seconds for max bonus
      },
      streakMultiplier: {
        3: 1.1,
        5: 1.2,
        7: 1.3,
        10: 1.5
      }
    };
  }

  /**
   * Parse frequency from DataMuse tags
   */
  parseFrequency(tags) {
    if (!tags) return 1;
    
    const freqTag = tags.find(tag => tag.startsWith('f:'));
    if (freqTag) {
      return parseFloat(freqTag.substring(2)) || 1;
    }
    return 1;
  }

  /**
   * Calculate word rarity
   */
  calculateRarity(tags) {
    const frequency = this.parseFrequency(tags);
    
    if (frequency > 50) return 'common';
    if (frequency > 10) return 'uncommon';
    return 'rare';
  }

  /**
   * Validate a word for the prefix
   */
  async validateWord(prefix, word, userWords = []) {
    // Basic validation
    if (!word || typeof word !== 'string') {
      return { valid: false, reason: 'Invalid word format' };
    }

    const normalizedWord = word.toLowerCase().trim();
    const normalizedPrefix = prefix.toLowerCase();

    if (!normalizedWord.startsWith(normalizedPrefix)) {
      return { valid: false, reason: `Word must start with "${prefix}"` };
    }

    if (normalizedWord.length <= normalizedPrefix.length) {
      return { valid: false, reason: 'Word must be longer than the prefix' };
    }

    if (userWords.includes(normalizedWord)) {
      return { valid: false, reason: 'Word already used' };
    }

    // Check if word exists
    const wordData = await this.checkWordExists(normalizedWord);
    if (!wordData.exists) {
      return { valid: false, reason: 'Word not found in dictionary' };
    }

    return {
      valid: true,
      word: normalizedWord,
      points: this.calculateWordPoints(normalizedWord, wordData),
      rarity: wordData.rarity,
      frequency: wordData.frequency
    };
  }

  /**
   * Check if word exists in our systems
   */
  async checkWordExists(word) {
    // Check DataMuse API first
    try {
      const response = await axios.get('https://api.datamuse.com/words', {
        params: {
          sp: word,
          max: 1
        },
        timeout: 3000
      });

      if (response.data.length > 0 && response.data[0].word === word) {
        return {
          exists: true,
          frequency: this.parseFrequency(response.data[0].tags),
          rarity: this.calculateRarity(response.data[0].tags)
        };
      }
    } catch (error) {
      console.log('DataMuse validation failed, checking database');
    }

    // Fallback to database
    try {
      const { data: dbWord } = await supabase
        .from('word_frequencies')
        .select('frequency')
        .eq('word', word)
        .limit(1);

      if (dbWord && dbWord.length > 0) {
        const freq = dbWord[0].frequency;
        return {
          exists: true,
          frequency: freq,
          rarity: freq < 10 ? 'rare' : freq < 100 ? 'uncommon' : 'common'
        };
      }
    } catch (error) {
      console.error('Database word check failed:', error);
    }

    return { exists: false };
  }
}