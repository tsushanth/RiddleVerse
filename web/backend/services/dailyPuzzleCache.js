// dailyPuzzleCache.js
import Redis from 'ioredis';
import { supabase } from '../config/database.js';
import { db } from '../config/firebaseAdmin.js';

class DailyPuzzleCache {
  constructor() {
    // Redis client (reusing same pattern as generation tracker)
    this.redis = new Redis(process.env.REDIS_URL || 'redis://localhost:6379');
    
    // Configuration
    this.config = {
      defaultCacheSize: 50, // Configurable cache size per puzzle type/difficulty
      cacheExpiryHours: 25, // Slightly more than 24h to handle timezone issues
      globalPathCollection: 'global_puzzle_paths', // Firebase collection
      cacheRefreshHour: 2 // 2 AM UTC for daily refresh
    };
    
    // Redis key prefixes
    this.prefixes = {
      dailyCache: 'daily_puzzles:',
      globalPath: 'global_path:',
      lastUpdate: 'last_update:',
      config: 'puzzle_config:'
    };
    
    // Cache statistics
    this.stats = {
      totalCacheHits: 0,
      totalCacheMisses: 0,
      lastRefreshTime: null,
      cacheSize: {},
      errors: []
    };
  }

  /**
   * Get cache key for daily puzzles
   */
  getDailyCacheKey(puzzleType, difficulty) {
    const today = new Date().toISOString().split('T')[0]; // YYYY-MM-DD
    return `${this.prefixes.dailyCache}${puzzleType}_${difficulty}_${today}`;
  }

  /**
   * Get global path key for Firebase tracking
   */
  getGlobalPathKey(puzzleType, difficulty) {
    return `${this.prefixes.globalPath}${puzzleType}_${difficulty}`;
  }

  /**
   * Get last update key for tracking refresh times
   */
  getLastUpdateKey(puzzleType, difficulty) {
    return `${this.prefixes.lastUpdate}${puzzleType}_${difficulty}`;
  }

  /**
   * Main endpoint: Get today's puzzles from cache
   */
  async getTodaysPuzzles(puzzleType, difficulty, count = null) {
    const startTime = Date.now();
    const normalizedDifficulty = difficulty.toLowerCase();
    const cacheKey = this.getDailyCacheKey(puzzleType, normalizedDifficulty);
    
    try {
      console.log(`Fetching today's puzzles: ${puzzleType}/${normalizedDifficulty}`);
      
      // Try to get from cache first
      const cachedData = await this.redis.get(cacheKey);
      
      if (cachedData) {
        this.stats.totalCacheHits++;
        const puzzles = JSON.parse(cachedData);
        
        // Return requested count or all cached puzzles
        const result = count ? puzzles.slice(0, count) : puzzles;
        
        console.log(`Cache HIT: Retrieved ${result.length} puzzles for ${puzzleType}/${normalizedDifficulty}`);
        
        return {
          success: true,
          puzzles: result,
          totalAvailable: puzzles.length,
          source: 'cache',
          cacheAge: await this.getCacheAge(cacheKey),
          responseTime: Date.now() - startTime
        };
      }
      
      // Cache miss - need to build cache
      this.stats.totalCacheMisses++;
      console.log(`Cache MISS: Building cache for ${puzzleType}/${normalizedDifficulty}`);
      
      // Build cache asynchronously and return what we can immediately
      const buildResult = await this.buildDailyCache(puzzleType, normalizedDifficulty);
      
      if (buildResult.success) {
        const result = count ? buildResult.puzzles.slice(0, count) : buildResult.puzzles;
        
        return {
          success: true,
          puzzles: result,
          totalAvailable: buildResult.puzzles.length,
          source: 'fresh_build',
          cacheAge: 0,
          responseTime: Date.now() - startTime
        };
      } else {
        return {
          success: false,
          message: 'Failed to build puzzle cache',
          error: buildResult.error,
          puzzles: [],
          responseTime: Date.now() - startTime
        };
      }
      
    } catch (error) {
      console.error(`Error getting today's puzzles for ${puzzleType}/${normalizedDifficulty}:`, error);
      this.stats.errors.push({
        timestamp: new Date().toISOString(),
        error: error.message,
        puzzleType,
        difficulty: normalizedDifficulty
      });
      
      return {
        success: false,
        message: 'Cache system error',
        error: error.message,
        puzzles: [],
        responseTime: Date.now() - startTime
      };
    }
  }

  /**
   * Build daily cache for a specific puzzle type/difficulty - UPDATED WITH BATCH-FETCH LOGIC
   */
  async buildDailyCache(puzzleType, difficulty) {
    console.log(`Building daily cache for ${puzzleType}/${difficulty}`);
    
    try {
      const cacheSize = await this.getCacheSize(puzzleType, difficulty);
      
      // Use batch-fetch sequence logic (start from beginning for cache)
      const sequenceResult = await this.getPuzzleSequence(puzzleType, difficulty, null, cacheSize);
      
      if (!sequenceResult.puzzleIds || sequenceResult.puzzleIds.length === 0) {
        throw new Error(`No puzzles found for ${puzzleType}/${difficulty}`);
      }
      
      // Fetch full puzzle data using batch query
      const { data: puzzles, error: puzzleError } = await supabase
        .from('puzzles')
        .select('*')
        .in('puzzleid', sequenceResult.puzzleIds);
        
      if (puzzleError) {
        throw new Error(`Failed to fetch puzzle data: ${puzzleError.message}`);
      }
      
      // Order puzzles according to the sequence (maintain order from getPuzzleSequence)
      const orderedPuzzles = sequenceResult.puzzleIds.map(id => 
        puzzles.find(p => p.puzzleid === id)
      ).filter(Boolean);
      
      if (orderedPuzzles.length === 0) {
        throw new Error(`No valid puzzles retrieved for ${puzzleType}/${difficulty}`);
      }
      
      // Cache the puzzles (same as before)
      const cacheKey = this.getDailyCacheKey(puzzleType, difficulty);
      const expirySeconds = this.config.cacheExpiryHours * 60 * 60;
      
      await this.redis.setex(
        cacheKey, 
        expirySeconds, 
        JSON.stringify(orderedPuzzles.map(p => this.buildOldPuzzleFormat(p)))
      );
      
      // Update last refresh time
      await this.redis.set(
        this.getLastUpdateKey(puzzleType, difficulty),
        new Date().toISOString()
      );
      
      // Update global path to the last puzzle in sequence for next refresh
      const lastPuzzleId = orderedPuzzles[orderedPuzzles.length - 1].puzzleid;
      await this.updateGlobalPath(puzzleType, difficulty, lastPuzzleId);
      
      // Update stats
      this.stats.cacheSize[`${puzzleType}_${difficulty}`] = orderedPuzzles.length;
      this.stats.lastRefreshTime = new Date().toISOString();
      
      console.log(`Successfully cached ${orderedPuzzles.length} puzzles for ${puzzleType}/${difficulty}`);
      
      return {
        success: true,
        puzzles: orderedPuzzles.map(p => this.buildOldPuzzleFormat(p)),
        cacheSize: orderedPuzzles.length
      };
      
    } catch (error) {
      console.error(`Error building cache for ${puzzleType}/${difficulty}:`, error);
      return {
        success: false,
        error: error.message
      };
    }
  }

  /**
   * Get puzzle sequence using batch-fetch logic (add this method to your class)
   */
  async getPuzzleSequence(puzzleType, difficulty, currentPuzzleId, count) {
    try {
      // If no current puzzle, start from beginning
      if (!currentPuzzleId) {
        const { data: firstPuzzles, error } = await supabase
          .from('puzzles')
          .select('puzzleid')
          .eq('type', puzzleType.toLowerCase())
          .eq('difficulty', difficulty)
          .order('timestamp', { ascending: true })
          .limit(count);

        if (error) throw error;
        
        const puzzleIds = (firstPuzzles || []).map(p => p.puzzleid);
        
        return {
          puzzleIds,
          wasReset: false
        };
      }

      // Get puzzles after current position
      const { data: currentPuzzle, error: currentError } = await supabase
        .from('puzzles')
        .select('timestamp')
        .eq('puzzleid', currentPuzzleId)
        .single();

      if (currentError || !currentPuzzle) {
        // Current puzzle not found, restart from beginning
        return await this.getPuzzleSequence(puzzleType, difficulty, null, count);
      }

      // Get next puzzles after current timestamp
      const { data: nextPuzzles, error: nextError } = await supabase
        .from('puzzles')
        .select('puzzleid')
        .eq('type', puzzleType.toLowerCase())
        .eq('difficulty', difficulty)
        .gt('timestamp', currentPuzzle.timestamp)
        .order('timestamp', { ascending: true })
        .limit(count);

      if (nextError) throw nextError;

      let puzzleIds = (nextPuzzles || []).map(p => p.puzzleid);
      let wasReset = false;

      // If we don't have enough puzzles, wrap around from beginning
      if (puzzleIds.length < count) {
        const remainingCount = count - puzzleIds.length;
        
        const { data: wrapAroundPuzzles, error: wrapError } = await supabase
          .from('puzzles')
          .select('puzzleid')
          .eq('type', puzzleType.toLowerCase())
          .eq('difficulty', difficulty)
          .order('timestamp', { ascending: true })
          .limit(remainingCount);

        if (wrapError) throw wrapError;
        
        const wrapAroundIds = (wrapAroundPuzzles || []).map(p => p.puzzleid);
        puzzleIds = [...puzzleIds, ...wrapAroundIds];
        wasReset = true;
      }

      return {
        puzzleIds,
        wasReset
      };

    } catch (error) {
      console.error(`Error getting puzzle sequence for ${puzzleType}/${difficulty}:`, error);
      throw error;
    }
  }

  /**
   * Get current global path position from Firebase
   */
  async getGlobalPath(puzzleType, difficulty) {
    const pathKey = `${puzzleType}_${difficulty}`;
    
    try {
      const doc = await db.collection(this.config.globalPathCollection).doc(pathKey).get();
      
      if (doc.exists) {
        const data = doc.data();
        return {
          currentPuzzleId: data.currentPuzzleId,
          lastUpdated: data.lastUpdated,
          totalPuzzles: data.totalPuzzles || 0,
          cycleCount: data.cycleCount || 0
        };
      } else {
        // Initialize new global path
        console.log(`Initializing new global path for ${puzzleType}/${difficulty}`);
        
        const firstPuzzle = await this.getFirstPuzzle(puzzleType, difficulty);
        const totalCount = await this.getTotalPuzzleCount(puzzleType, difficulty);
        
        const initialPath = {
          currentPuzzleId: firstPuzzle?.puzzleid || null,
          lastUpdated: new Date().toISOString(),
          totalPuzzles: totalCount,
          cycleCount: 0,
          puzzleType,
          difficulty
        };
        
        await db.collection(this.config.globalPathCollection).doc(pathKey).set(initialPath);
        
        return initialPath;
      }
    } catch (error) {
      console.error(`Error getting global path for ${puzzleType}/${difficulty}:`, error);
      throw error;
    }
  }

  /**
   * Update global path position in Firebase
   */
  async updateGlobalPath(puzzleType, difficulty, nextPuzzleId) {
    const pathKey = `${puzzleType}_${difficulty}`;
    
    try {
      // Prepare update data outside of transaction
      let updateData = {
        currentPuzzleId: nextPuzzleId,
        lastUpdated: new Date().toISOString()
      };
      
      // If nextPuzzleId is null, we've wrapped around - use start ID
      if (!nextPuzzleId) {
        const startId = await this.getCacheStartId(puzzleType, difficulty);
        updateData.currentPuzzleId = startId;
      }
      
      // Use Firebase transaction for atomic update
      await db.runTransaction(async (transaction) => {
        const pathRef = db.collection(this.config.globalPathCollection).doc(pathKey);
        const pathDoc = await transaction.get(pathRef);
        
        // Handle cycle count increment for wrap-around
        if (!nextPuzzleId) {
          updateData.cycleCount = (pathDoc.data()?.cycleCount || 0) + 1;
          console.log(`Global path wrapped around for ${puzzleType}/${difficulty}, starting cycle ${updateData.cycleCount} from ${updateData.currentPuzzleId}`);
        }
        
        if (pathDoc.exists) {
          transaction.update(pathRef, updateData);
        } else {
          transaction.set(pathRef, {
            ...updateData,
            puzzleType,
            difficulty,
            totalPuzzles: await this.getTotalPuzzleCount(puzzleType, difficulty),
            cycleCount: updateData.cycleCount || 0
          });
        }
      });
      
      console.log(`Updated global path for ${puzzleType}/${difficulty} to puzzle ${updateData.currentPuzzleId}`);
      
    } catch (error) {
      console.error(`Error updating global path for ${puzzleType}/${difficulty}:`, error);
      throw error;
    }
  }

  async getCacheStartId(puzzleType, difficulty) {
    try {
      const { data: startRecord } = await supabase
        .from('puzzle_first_cache')
        .select('first_puzzleid')
        .eq('type', puzzleType.toLowerCase())
        .eq('difficulty', difficulty)
        .single();
      
      return startRecord?.first_puzzleid || null;
    } catch (error) {
      console.error(`Error getting cache start ID for ${puzzleType}/${difficulty}:`, error);
      return null;
    }
  }
  

  /**
   * Fetch puzzles from current global position
   */
  async fetchPuzzlesFromCurrentPosition(puzzleType, difficulty, currentPuzzleId, count) {
    if (!currentPuzzleId) {
      // Get start puzzle from cache_puzzle_start_id
      const startId = await this.getCacheStartId(puzzleType, difficulty);
      if (!startId) {
        throw new Error(`No cache start ID found for ${puzzleType}/${difficulty}`);
      }
      currentPuzzleId = startId;
    }
    
    try {
      const puzzles = [];
      let nextPuzzleId = currentPuzzleId;
      
      // Follow the path chain to get the requested number of puzzles
      for (let i = 0; i < count && nextPuzzleId; i++) {
        // Get the puzzle data
        const { data: puzzle, error: puzzleError } = await supabase
          .from('puzzles')
          .select('*')
          .eq('puzzleid', nextPuzzleId)
          .eq('type', puzzleType.toLowerCase())
          .eq('difficulty', difficulty)
          .single();
        
        if (puzzleError || !puzzle) {
          console.warn(`Puzzle ${nextPuzzleId} not found, stopping chain traversal`);
          break;
        }
        
        puzzles.push(puzzle);
        
        // Get the next puzzle ID from puzzle_path
        const { data: pathRecord, error: pathError } = await supabase
          .from('puzzle_path')
          .select('nextpuzzleid')
          .eq('puzzleid', nextPuzzleId)
          .eq('type', puzzleType.toLowerCase())
          .eq('difficulty', difficulty)
          .single();
        
        if (pathError || !pathRecord) {
          console.warn(`Path record for ${nextPuzzleId} not found, stopping chain traversal`);
          break;
        }
        
        nextPuzzleId = pathRecord.nextpuzzleid;
        
        // If nextpuzzleid is null, we've reached the end of the chain
        if (!nextPuzzleId) {
          console.log(`Reached end of puzzle chain for ${puzzleType}/${difficulty} after ${puzzles.length} puzzles`);
          break;
        }
      }
      
      // If we don't have enough puzzles and haven't reached the end, something's wrong
      if (puzzles.length < count && nextPuzzleId) {
        console.warn(`Only retrieved ${puzzles.length}/${count} puzzles, but chain continues`);
      }
      
      // If we reached the end but still need more puzzles, wrap around from start
      if (puzzles.length < count && !nextPuzzleId) {
        console.log(`Wrapping around to beginning for remaining ${count - puzzles.length} puzzles`);
        
        const startId = await this.getCacheStartId(puzzleType, difficulty);
        if (startId) {
          const remainingPuzzles = await this.fetchPuzzlesFromCurrentPosition(
            puzzleType, 
            difficulty, 
            startId, 
            count - puzzles.length
          );
          puzzles.push(...remainingPuzzles);
        }
      }
      
      return puzzles;
      
    } catch (error) {
      console.error(`Error following puzzle path from ${currentPuzzleId}:`, error);
      throw error;
    }
  }

  async initializeCacheStartPositions() {
    try {
      const puzzleTypes = ['math', 'anagram', 'imagepuzzle', 'wordsearch', 'crossword', 'trivia', 'memorystory'];
      const difficulties = ['easy', 'medium', 'hard'];
      
      for (const puzzleType of puzzleTypes) {
        for (const difficulty of difficulties) {
          // Check if start position already exists
          const existingStart = await this.getCacheStartId(puzzleType, difficulty);
          
          if (!existingStart) {
            // Find the first puzzle in the chain (one that's not referenced as nextpuzzleid)
            const { data: firstPuzzle } = await supabase
              .from('puzzle_path')
              .select('puzzleid')
              .eq('type', puzzleType.toLowerCase())
              .eq('difficulty', difficulty)
              .not('puzzleid', 'in', 
                `(SELECT DISTINCT nextpuzzleid FROM puzzle_path WHERE nextpuzzleid IS NOT NULL AND type = '${puzzleType.toLowerCase()}' AND difficulty = '${difficulty}')`
              )
              .limit(1);
            
            if (firstPuzzle && firstPuzzle.length > 0) {
              // Set this as the cache start ID
              await supabase
                .from('puzzle_first_cache')
                .insert({
                  type: puzzleType.toLowerCase(),
                  difficulty,
                  first_puzzleid: firstPuzzle[0].puzzleid
                });
              
              console.log(`Initialized cache start ID for ${puzzleType}/${difficulty}: ${firstPuzzle[0].puzzleid}`);
            }
          }
        }
      }
      
    } catch (error) {
      console.error('Error initializing cache start positions:', error);
      throw error;
    }
  }

  /**
   * Fetch puzzles from the beginning (fallback)
   */
  async fetchPuzzlesFromBeginning(puzzleType, difficulty, count) {
    const { data: puzzles, error } = await supabase
      .from('puzzles')
      .select('*')
      .eq('type', puzzleType.toLowerCase())
      .eq('difficulty', difficulty)
      .order('timestamp', { ascending: true })
      .limit(count);
    
    if (error) throw error;
    return puzzles || [];
  }

  /**
   * Calculate next global position after caching
   */
  async calculateNextGlobalPosition(cachedPuzzles, puzzleType, difficulty) {
    if (cachedPuzzles.length === 0) {
      return null; // Will trigger wrap-around to start
    }
    
    try {
      const lastCachedPuzzle = cachedPuzzles[cachedPuzzles.length - 1];
      
      // Get the next puzzle ID from puzzle_path
      const { data: pathRecord } = await supabase
        .from('puzzle_path')
        .select('nextpuzzleid')
        .eq('puzzleid', lastCachedPuzzle.puzzleid)
        .eq('type', puzzleType.toLowerCase())
        .eq('difficulty', difficulty)
        .single();
      
      if (!pathRecord || !pathRecord.nextpuzzleid) {
        // Reached end of chain, wrap around to start
        console.log(`Reached end of puzzle chain after ${lastCachedPuzzle.puzzleid}, wrapping around`);
        return null;
      }
      
      return pathRecord.nextpuzzleid;
      
    } catch (error) {
      console.error('Error calculating next global position from puzzle path:', error);
      return null; // Fallback to wrap-around
    }
  }

  /**
   * Get first puzzle for initialization
   */
  async getFirstPuzzle(puzzleType, difficulty) {
    const { data: puzzle } = await supabase
      .from('puzzles')
      .select('*')
      .eq('type', puzzleType.toLowerCase())
      .eq('difficulty', difficulty)
      .order('timestamp', { ascending: true })
      .limit(1);
    
    return puzzle?.[0] || null;
  }

  /**
   * Get total puzzle count for a type/difficulty
   */
  async getTotalPuzzleCount(puzzleType, difficulty) {
    const { count } = await supabase
      .from('puzzles')
      .select('*', { count: 'exact', head: true })
      .eq('type', puzzleType.toLowerCase())
      .eq('difficulty', difficulty);
    
    return count || 0;
  }

  /**
   * Get cache size configuration (configurable per puzzle type)
   */
  async getCacheSize(puzzleType, difficulty) {
    const configKey = `${this.prefixes.config}${puzzleType}_${difficulty}`;
    
    try {
      const cachedSize = await this.redis.get(configKey);
      if (cachedSize) {
        return parseInt(cachedSize, 10);
      }
      
      // Check Firebase for custom configuration
      const configDoc = await db.collection('puzzle_config').doc(`${puzzleType}_${difficulty}`).get();
      
      if (configDoc.exists) {
        const config = configDoc.data();
        const size = config.cacheSize || this.config.defaultCacheSize;
        
        // Cache the configuration
        await this.redis.setex(configKey, 24 * 60 * 60, size.toString());
        
        return size;
      }
      
      // Use default and cache it
      await this.redis.setex(configKey, 24 * 60 * 60, this.config.defaultCacheSize.toString());
      return this.config.defaultCacheSize;
      
    } catch (error) {
      console.error(`Error getting cache size for ${puzzleType}/${difficulty}:`, error);
      return this.config.defaultCacheSize;
    }
  }

  /**
   * Get cache age in minutes
   */
  async getCacheAge(cacheKey) {
    try {
      const ttl = await this.redis.ttl(cacheKey);
      if (ttl > 0) {
        const maxAge = this.config.cacheExpiryHours * 60 * 60;
        const age = (maxAge - ttl) / 60; // Convert to minutes
        return Math.round(age);
      }
      return 0;
    } catch (error) {
      return 0;
    }
  }

  /**
   * Build old puzzle format (reusing from your existing code)
   */
  buildOldPuzzleFormat(puzzle) {
    return {
      id: puzzle.puzzleid,
      type: puzzle.type,
      difficulty: puzzle.difficulty,
      question: puzzle.question,
      answer: puzzle.answer,
      options: puzzle.options,
      explanation: puzzle.explanation,
      hints: puzzle.hints,
      timestamp: puzzle.timestamp,
      metadata: puzzle.metadata || {}
    };
  }

  /**
   * Manual cache refresh (for admin use)
   */
  async refreshCache(puzzleType = null, difficulty = null) {
    console.log('Starting manual cache refresh...');
    
    try {
      if (puzzleType && difficulty) {
        // Refresh specific cache
        const result = await this.buildDailyCache(puzzleType, difficulty);
        return {
          success: result.success,
          refreshed: [`${puzzleType}_${difficulty}`],
          details: result
        };
      } else {
        // Refresh all caches
        const puzzleTypes = ['math', 'anagram', 'imagepuzzle', 'wordsearch', 'crossword', 'trivia', 'memorystory'];
        const difficulties = ['easy', 'medium', 'hard'];
        const refreshed = [];
        const errors = [];
        
        for (const type of puzzleTypes) {
          for (const diff of difficulties) {
            try {
              await this.buildDailyCache(type, diff);
              refreshed.push(`${type}_${diff}`);
            } catch (error) {
              errors.push(`${type}_${diff}: ${error.message}`);
            }
          }
        }
        
        return {
          success: errors.length === 0,
          refreshed,
          errors,
          total: refreshed.length
        };
      }
    } catch (error) {
      console.error('Error during manual cache refresh:', error);
      return {
        success: false,
        error: error.message
      };
    }
  }

  /**
   * Get system statistics
   */
  getStats() {
    return {
      ...this.stats,
      hitRate: this.stats.totalCacheHits / (this.stats.totalCacheHits + this.stats.totalCacheMisses) * 100,
      totalRequests: this.stats.totalCacheHits + this.stats.totalCacheMisses
    };
  }

  /**
   * Clear all caches (for maintenance)
   */
  async clearAllCaches() {
    try {
      const keys = await this.redis.keys(`${this.prefixes.dailyCache}*`);
      if (keys.length > 0) {
        await this.redis.del(...keys);
      }
      
      console.log(`Cleared ${keys.length} puzzle caches`);
      return {
        success: true,
        clearedKeys: keys.length
      };
    } catch (error) {
      console.error('Error clearing caches:', error);
      return {
        success: false,
        error: error.message
      };
    }
  }
}

// Create singleton instance
const dailyPuzzleCache = new DailyPuzzleCache();

export { dailyPuzzleCache, DailyPuzzleCache };