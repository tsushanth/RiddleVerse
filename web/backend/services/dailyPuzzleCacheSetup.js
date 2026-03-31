import { dailyPuzzleCache } from './dailyPuzzleCache.js';
import { cacheScheduler } from './cacheScheduler.js';
import { db } from '../config/firebaseAdmin.js';
import { PUZZLE_TYPES } from '../config/puzzleConfig.js';
// Complete puzzle types definition

// Convert to array for easier iteration
export const ALL_PUZZLE_TYPES = Object.values(PUZZLE_TYPES);

// Categorized puzzle types for prioritized loading
export const PUZZLE_CATEGORIES = {
  // High priority - most popular/fast to generate
  critical: [
    PUZZLE_TYPES.MATH,
    PUZZLE_TYPES.TRIVIA,
    PUZZLE_TYPES.ANAGRAM,
    PUZZLE_TYPES.WORD_SEARCH
  ],
  
  // Medium priority - moderately popular
  important: [
    PUZZLE_TYPES.CROSSWORD,
    PUZZLE_TYPES.MEMORY_STORY,
    PUZZLE_TYPES.IMAGE_PUZZLE,
    PUZZLE_TYPES.MATH_ESTIMATION,
    PUZZLE_TYPES.SYNONYMS,
    PUZZLE_TYPES.ANTONYMS,
    PUZZLE_TYPES.SUBTRACTION,
    PUZZLE_TYPES.DIVISION,
    PUZZLE_TYPES.PERCENTAGES
  ],
  
  // Lower priority - specialized or resource intensive
  deferred: [
    PUZZLE_TYPES.IMAGE_QUESTION,
    PUZZLE_TYPES.IMAGE_MATCH,
    PUZZLE_TYPES.FIND_DIFFERENCES,
    PUZZLE_TYPES.FIND_OBJECT,
    PUZZLE_TYPES.WALDO_PUZZLE,
    PUZZLE_TYPES.UNIQUE_OBJECT,
    PUZZLE_TYPES.FLOW_PUZZLE,
    PUZZLE_TYPES.PROGRESSIVE_REVELATION,
    PUZZLE_TYPES.PINBALL_DEFLECTOR,
    PUZZLE_TYPES.MUSIC_IDENTIFICATION,
    PUZZLE_TYPES.MEMORY_SQUARES,
    PUZZLE_TYPES.MEMORY_PREVIOUS_PAIR,
    PUZZLE_TYPES.MEMORY_PREVIOUS_SINGLE,
    PUZZLE_TYPES.MEMORY_MATRIX_PATH,
    PUZZLE_TYPES.MEMORY_SEQUENCING,
    PUZZLE_TYPES.MEMORY_RETENTION,
    PUZZLE_TYPES.WORD_SNAKE,
    PUZZLE_TYPES.WORD_PREFIX,
    PUZZLE_TYPES.LETTER_SET,
    PUZZLE_TYPES.MATH_TIPPING,
    PUZZLE_TYPES.MATH_COMPARISON,
    PUZZLE_TYPES.AVERAGE,
    PUZZLE_TYPES.PURCHASING,
    PUZZLE_TYPES.DISCOUNTS,
    PUZZLE_TYPES.CONVERSION,
    PUZZLE_TYPES.STORY_PUZZLE,
    PUZZLE_TYPES.CRYPTO,
    PUZZLE_TYPES.SENTENCE_TRANSITIONS,
    PUZZLE_TYPES.CONNOTATION_WORDS,
    PUZZLE_TYPES.REAL_OR_AI
  ]
};

export const DIFFICULTY_LEVELS = ['easy', 'medium', 'hard'];

/**
 * FAST initialization - only essential setup, defer heavy operations
 */
export async function initializeDailyPuzzleSystem() {
    const startTime = Date.now();

    try {
        await dailyPuzzleCache.redis.ping();
        await cacheScheduler.initializeLightweight();

        setTimeout(() => {
            performBackgroundInitialization();
        }, 5000);

        return { success: true, initTime: Date.now() - startTime };
    } catch (error) {
        console.error('Daily puzzle cache initialization failed:', error.message);
        return {
            success: false,
            error: error.message,
            initTime: Date.now() - startTime,
            nonBlocking: true
        };
    }
}

/**
 * Background initialization - runs after server is up
 */
async function performBackgroundInitialization() {
    try {
        await initializeFirebaseCollections();
        await dailyPuzzleCache.initializeCacheStartPositions();
        await warmupCriticalCaches();
        await cacheScheduler.startScheduledTasks();
    } catch (error) {
        console.error('Background initialization failed:', error.message);
    }
}

/**
 * Initialize Firebase collections - but only create documents that don't exist
 */
async function initializeFirebaseCollections() {
    try {
        const systemConfigDoc = await db.collection('system_config').doc('cache_scheduler').get();
        if (!systemConfigDoc.exists) {
            await db.collection('system_config').doc('cache_scheduler').set({
                schedules: {
                    dailyRefresh: '0 2 * * *',
                    healthCheck: '*/30 * * * *',
                    cleanupStale: '0 3 * * 0',
                    configRefresh: '0 1 * * *'
                },
                enabled: true,
                timezone: 'UTC',
                lastUpdated: new Date().toISOString(),
                createdAt: new Date().toISOString()
            });
        }
        await initializePuzzleTypeConfigs();
    } catch (error) {
        // Non-critical failure
    }
}

/**
 * Initialize default configurations for all puzzle types
 */
async function initializePuzzleTypeConfigs() {
    const batch = db.batch();
    let createdConfigs = 0;

    for (const puzzleType of ALL_PUZZLE_TYPES) {
        for (const difficulty of DIFFICULTY_LEVELS) {
            const configKey = `${puzzleType}_${difficulty}`;
            const configRef = db.collection('puzzle_config').doc(configKey);

            const existingConfig = await configRef.get();
            if (!existingConfig.exists) {
                batch.set(configRef, {
                    puzzleType,
                    difficulty,
                    enabled: true,
                    cacheSize: getDefaultCacheSize(puzzleType, difficulty),
                    refreshInterval: getDefaultRefreshInterval(puzzleType),
                    priority: getPuzzlePriority(puzzleType),
                    createdAt: new Date().toISOString(),
                    lastUpdated: new Date().toISOString()
                });
                createdConfigs++;
            }
        }
    }

    if (createdConfigs > 0) {
        await batch.commit();
    }
}

/**
 * Get default cache size based on puzzle type and difficulty
 */
function getDefaultCacheSize(puzzleType, difficulty) {
    // Larger cache for popular/fast puzzles, smaller for resource-intensive ones
    const baseSizes = {
        critical: { easy: 100, medium: 75, hard: 50 },
        important: { easy: 50, medium: 40, hard: 30 },
        deferred: { easy: 25, medium: 20, hard: 15 }
    };
    
    const category = getPuzzleCategory(puzzleType);
    return baseSizes[category][difficulty];
}

/**
 * Get default refresh interval based on puzzle type
 */
function getDefaultRefreshInterval(puzzleType) {
    const category = getPuzzleCategory(puzzleType);
    
    // More frequent refresh for popular puzzles
    const intervals = {
        critical: '0 */6 * * *',  // Every 6 hours
        important: '0 */12 * * *', // Every 12 hours
        deferred: '0 2 * * *'      // Daily at 2 AM
    };
    
    return intervals[category];
}

/**
 * Get puzzle priority for loading order
 */
function getPuzzlePriority(puzzleType) {
    if (PUZZLE_CATEGORIES.critical.includes(puzzleType)) return 1;
    if (PUZZLE_CATEGORIES.important.includes(puzzleType)) return 2;
    return 3;
}

/**
 * Get puzzle category
 */
function getPuzzleCategory(puzzleType) {
    if (PUZZLE_CATEGORIES.critical.includes(puzzleType)) return 'critical';
    if (PUZZLE_CATEGORIES.important.includes(puzzleType)) return 'important';
    return 'deferred';
}

/**
 * Warm up only the most critical caches (not all)
 */
async function warmupCriticalCaches() {
    try {
        const criticalCaches = PUZZLE_CATEGORIES.critical.map(puzzleType => ({
            puzzleType,
            difficulty: 'easy'
        }));

        for (const { puzzleType, difficulty } of criticalCaches) {
            try {
                await dailyPuzzleCache.buildDailyCache(puzzleType, difficulty);
            } catch {
                // Non-critical failure
            }
            await new Promise(resolve => setTimeout(resolve, 2000));
        }

        setTimeout(() => warmupImportantCaches(), 2 * 60 * 1000);
    } catch {
        // Non-critical failure
    }
}

/**
 * Warm up important caches after critical ones
 */
async function warmupImportantCaches() {
    try {
        for (const puzzleType of PUZZLE_CATEGORIES.critical) {
            for (const difficulty of ['medium', 'hard']) {
                try {
                    await dailyPuzzleCache.buildDailyCache(puzzleType, difficulty);
                    await new Promise(resolve => setTimeout(resolve, 10000));
                } catch {
                    // Non-critical failure
                }
            }
        }

        for (const puzzleType of PUZZLE_CATEGORIES.important) {
            try {
                await dailyPuzzleCache.buildDailyCache(puzzleType, 'easy');
                await new Promise(resolve => setTimeout(resolve, 15000));
            } catch {
                // Non-critical failure
            }
        }

        setTimeout(() => warmupDeferredCaches(), 10 * 60 * 1000);
    } catch {
        // Non-critical failure
    }
}

/**
 * Warm up deferred caches very gradually
 */
async function warmupDeferredCaches() {
    try {
        for (const puzzleType of PUZZLE_CATEGORIES.important) {
            for (const difficulty of ['medium', 'hard']) {
                try {
                    await dailyPuzzleCache.buildDailyCache(puzzleType, difficulty);
                    await new Promise(resolve => setTimeout(resolve, 30000));
                } catch {
                    // Non-critical failure
                }
            }
        }

        for (const puzzleType of PUZZLE_CATEGORIES.deferred) {
            for (const difficulty of DIFFICULTY_LEVELS) {
                try {
                    await dailyPuzzleCache.buildDailyCache(puzzleType, difficulty);
                    await new Promise(resolve => setTimeout(resolve, 60000));
                } catch {
                    // Non-critical failure
                }
            }
        }
    } catch {
        // Non-critical failure
    }
}

/**
 * FAST shutdown - only essential cleanup
 */
export async function shutdownDailyPuzzleSystem() {
    try {
        cacheScheduler.stopAllTasks();
        await dailyPuzzleCache.redis.quit();
        return { success: true };
    } catch (error) {
        console.error('Error during system shutdown:', error.message);
        return { success: false, error: error.message };
    }
}

/**
 * FAST health check - minimal operations
 */
export async function checkDailyPuzzleSystemHealth() {
    try {
        const health = {
            status: 'healthy',
            timestamp: new Date().toISOString(),
            components: {},
            supportedPuzzleTypes: ALL_PUZZLE_TYPES.length
        };
        
        // Quick Redis ping (fast)
        try {
            await dailyPuzzleCache.redis.ping();
            health.components.redis = 'healthy';
        } catch (error) {
            health.components.redis = 'unhealthy';
            health.status = 'degraded';
        }
        
        // Check scheduler status (memory only, fast)
        health.components.scheduler = cacheScheduler.isInitialized ? 'healthy' : 'unhealthy';
        if (!cacheScheduler.isInitialized) {
            health.status = 'degraded';
        }
        
        // Quick check of cache availability for critical puzzle types
        try {
            const criticalCacheStatus = {};
            for (const puzzleType of PUZZLE_CATEGORIES.critical.slice(0, 2)) { // Check first 2 only
                const cacheKey = `daily_cache:${puzzleType}_easy`;
                const exists = await dailyPuzzleCache.redis.exists(cacheKey);
                criticalCacheStatus[puzzleType] = exists ? 'cached' : 'missing';
            }
            health.components.criticalCaches = criticalCacheStatus;
        } catch (error) {
            health.components.criticalCaches = 'error';
        }
        
        return health;
        
    } catch (error) {
        return {
            status: 'unhealthy',
            timestamp: new Date().toISOString(),
            error: error.message,
            supportedPuzzleTypes: ALL_PUZZLE_TYPES.length
        };
    }
}

/**
 * Configuration update function for runtime changes
 */
export async function updateCacheConfiguration(puzzleType, difficulty, newConfig) {
    try {
        // Validate puzzle type
        if (!ALL_PUZZLE_TYPES.includes(puzzleType)) {
            throw new Error(`Unsupported puzzle type: ${puzzleType}`);
        }
        
        // Validate difficulty
        if (!DIFFICULTY_LEVELS.includes(difficulty)) {
            throw new Error(`Unsupported difficulty: ${difficulty}`);
        }
        
        const configKey = `${puzzleType}_${difficulty}`;
        
        // Update in Firebase
        await db.collection('puzzle_config').doc(configKey).update({
            ...newConfig,
            lastUpdated: new Date().toISOString()
        });
        
        // Clear Redis cache for this configuration
        await dailyPuzzleCache.redis.del(`puzzle_config:${configKey}`);
        
        
        return { success: true };
        
    } catch (error) {
        return { success: false, error: error.message };
    }
}

/**
 * Get all supported puzzle types and their status
 */
export async function getAllPuzzleTypesStatus() {
    try {
        const status = {
            timestamp: new Date().toISOString(),
            totalTypes: ALL_PUZZLE_TYPES.length,
            categories: {
                critical: PUZZLE_CATEGORIES.critical.length,
                important: PUZZLE_CATEGORIES.important.length,
                deferred: PUZZLE_CATEGORIES.deferred.length
            },
            puzzleTypes: {}
        };
        
        // Check cache status for each puzzle type
        for (const puzzleType of ALL_PUZZLE_TYPES) {
            const typeStatus = {
                category: getPuzzleCategory(puzzleType),
                priority: getPuzzlePriority(puzzleType),
                difficulties: {}
            };
            
            for (const difficulty of DIFFICULTY_LEVELS) {
                const cacheKey = `daily_cache:${puzzleType}_${difficulty}`;
                try {
                    const exists = await dailyPuzzleCache.redis.exists(cacheKey);
                    const ttl = exists ? await dailyPuzzleCache.redis.ttl(cacheKey) : -1;
                    typeStatus.difficulties[difficulty] = {
                        cached: !!exists,
                        ttl: ttl
                    };
                } catch (error) {
                    typeStatus.difficulties[difficulty] = {
                        cached: false,
                        error: error.message
                    };
                }
            }
            
            status.puzzleTypes[puzzleType] = typeStatus;
        }
        
        return status;
        
    } catch (error) {
        return {
            timestamp: new Date().toISOString(),
            error: error.message,
            success: false
        };
    }
}

// Simplified metrics function
export async function getDailyPuzzleSystemMetrics() {
    try {
        const metrics = {
            timestamp: new Date().toISOString(),
            supportedPuzzleTypes: ALL_PUZZLE_TYPES.length,
            cache: {
                stats: dailyPuzzleCache.getStats()
            },
            scheduler: {
                stats: cacheScheduler.getStats()
            }
        };
        
        // Get basic Redis info (keep it light)
        try {
            const keyCount = await dailyPuzzleCache.redis.dbsize();
            metrics.cache.redisKeyCount = keyCount;
            
            // Count cached puzzle types
            const cachedTypes = new Set();
            const keys = await dailyPuzzleCache.redis.keys('daily_cache:*');
            keys.forEach(key => {
                const match = key.match(/daily_cache:(.+)_(?:easy|medium|hard)$/);
                if (match) cachedTypes.add(match[1]);
            });
            metrics.cache.cachedPuzzleTypes = cachedTypes.size;
            metrics.cache.totalCacheKeys = keys.length;
            
        } catch (error) {
            metrics.cache.redisError = error.message;
        }
        
        return metrics;
        
    } catch (error) {
        return {
            timestamp: new Date().toISOString(),
            error: error.message,
            success: false
        };
    }
}