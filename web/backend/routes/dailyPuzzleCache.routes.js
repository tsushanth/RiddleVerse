// routes/dailyPuzzleCache.routes.js
import express from 'express';
import rateLimit from 'express-rate-limit';
import { dailyPuzzleCache } from '../services/dailyPuzzleCache.js';
import {
    checkDailyPuzzleSystemHealth,
    getDailyPuzzleSystemMetrics,
    updateCacheConfiguration
} from '../services/dailyPuzzleCacheSetup.js';
import { triggerAutoGenerationSafe } from '../services/puzzleService.js';
import { cacheScheduler } from '../services/cacheScheduler.js';
import { PUZZLE_TYPES } from '../config/puzzleConfig.js';

const router = express.Router();

// Valid puzzle types for request validation
const VALID_PUZZLE_TYPES = new Set(Object.values(PUZZLE_TYPES));

// Rate limiters for different types of endpoints
const publicLimiter = rateLimit({
    windowMs: 15 * 60 * 1000, // 15 minutes
    max: 100, // 100 requests per 15 minutes for public endpoints
    message: { 
        error: "Too many requests for daily puzzles. Please try again later.",
        retryAfter: "15 minutes"
    }
});

const adminLimiter = rateLimit({
    windowMs: 60 * 60 * 1000, // 1 hour
    max: 20, // 20 admin requests per hour
    message: { 
        error: "Too many admin requests. Limit: 20 per hour.",
        retryAfter: "1 hour"
    }
});

// Middleware to validate admin access
const validateAdmin = (req, res, next) => {
    const adminKey = req.body?.adminKey || req.query?.adminKey;
    
    if (adminKey !== process.env.ADMIN_KEY) {
        return res.status(401).json({
            success: false,
            message: 'Unauthorized',
            error: 'invalid_admin_key'
        });
    }
    
    next();
};

// ============================================================================
// PUBLIC ENDPOINTS
// ============================================================================

// Configuration for auto-generation
const LOW_PUZZLE_THRESHOLD = 3; // Trigger generation when fewer than this many puzzles remain
const AUTO_GENERATION_COUNT = 10; // How many new puzzles to generate
const EMPTY_CACHE_GENERATION_COUNT = 20; // Generate more when completely empty


router.get("/popular-categories", publicLimiter, async (req, res) => {
    try {
        const cacheKey = 'popular_categories';
        const cachedData = await dailyPuzzleCache.redis.get(cacheKey);

        if (cachedData) {
            const parsed = JSON.parse(cachedData);
            const cacheAge = Math.floor((Date.now() - parsed.timestamp) / (60 * 1000));
            return res.json({
                success: true,
                categories: parsed.data,
                metadata: {
                    source: 'cache',
                    cacheAge,
                    lastUpdated: new Date(parsed.timestamp).toISOString(),
                    nextRefresh: new Date(Date.now() + (24 * 60 * 60 * 1000)).toISOString()
                }
            });
        }

        const popularCategories = await fetchPopularCategoriesFromFirebase();

        const cacheData = {
            data: popularCategories,
            timestamp: Date.now()
        };
        await dailyPuzzleCache.redis.setex(cacheKey, 24 * 60 * 60, JSON.stringify(cacheData));
        
        return res.json({
            success: true,
            categories: popularCategories,
            metadata: {
                source: 'fresh',
                cacheAge: 0,
                lastUpdated: new Date().toISOString(),
                nextRefresh: new Date(Date.now() + (24 * 60 * 60 * 1000)).toISOString()
            }
        });
        
    } catch (error) {
        console.error('Error fetching popular categories:', error.message);

        // Return fallback popular categories on error
        const fallbackCategories = [
            { puzzleType: "math", playCount: 15420, averageRating: 4.5 },
            { puzzleType: "trivia", playCount: 12850, averageRating: 4.6 },
            { puzzleType: "anagram", playCount: 11200, averageRating: 4.4 },
            { puzzleType: "crossword", playCount: 9800, averageRating: 4.7 },
            { puzzleType: "wordsearch", playCount: 8900, averageRating: 4.3 },
            { puzzleType: "memorystory", playCount: 7650, averageRating: 4.5 }
        ];
        
        return res.json({
            success: true,
            categories: fallbackCategories,
            metadata: {
                source: 'fallback',
                message: 'Using fallback data due to error',
                error: error.message
            }
        });
    }
});

/**
 * Get new puzzles (fetched from Supabase, cached daily)
 * Returns the 7 most recently added puzzle types
 */
router.get("/new-puzzles", publicLimiter, async (req, res) => {
    try {
        const cacheKey = 'new_puzzles';
        const cachedData = await dailyPuzzleCache.redis.get(cacheKey);

        if (cachedData) {
            const parsed = JSON.parse(cachedData);
            const cacheAge = Math.floor((Date.now() - parsed.timestamp) / (60 * 1000));
            return res.json({
                success: true,
                puzzles: parsed.data,
                metadata: {
                    source: 'cache',
                    cacheAge,
                    lastUpdated: new Date(parsed.timestamp).toISOString(),
                    nextRefresh: new Date(Date.now() + (24 * 60 * 60 * 1000)).toISOString()
                }
            });
        }

        const newPuzzles = await fetchNewPuzzlesFromSupabase();

        const cacheData = {
            data: newPuzzles,
            timestamp: Date.now()
        };
        await dailyPuzzleCache.redis.setex(cacheKey, 24 * 60 * 60, JSON.stringify(cacheData));
        
        return res.json({
            success: true,
            puzzles: newPuzzles,
            metadata: {
                total: newPuzzles.length,
                source: 'fresh',
                cacheAge: 0,
                lastUpdated: new Date().toISOString(),
                nextRefresh: new Date(Date.now() + (24 * 60 * 60 * 1000)).toISOString()
            }
        });
        
    } catch (error) {
        console.error('Error fetching new puzzles:', error.message);

        return res.json({
            success: true,
            puzzles: [],
            metadata: {
                error: error.message,
                message: "No new puzzles available"
            }
        });
    }
});

// ============================================================================
// HELPER FUNCTIONS
// ============================================================================

/**
 * Fetch new puzzles from Supabase
 * Returns the 7 most recently added puzzle types
 */
async function fetchNewPuzzlesFromSupabase() {
    const { supabase } = await import('../config/database.js');
    const { PUZZLE_TYPES } = await import('../config/puzzleConfig.js');

    try {
        const allPuzzleTypes = Object.values(PUZZLE_TYPES);
        const puzzleTypeData = [];

        for (const puzzleType of allPuzzleTypes) {
            try {
                const { data, error } = await supabase
                    .from('puzzles')
                    .select('type, timestamp')
                    .eq('type', puzzleType)
                    .order('timestamp', { ascending: false })
                    .limit(1);

                if (error) continue;

                if (data && data.length > 0) {
                    const latestPuzzle = data[0];
                    puzzleTypeData.push({
                        puzzleType: latestPuzzle.type,
                        lastCreatedAt: new Date(latestPuzzle.timestamp),
                        addedDate: latestPuzzle.timestamp
                    });
                }
            } catch (error) {
                // Skip this puzzle type on error
            }
        }

        puzzleTypeData.sort((a, b) => b.lastCreatedAt - a.lastCreatedAt);
        const top7NewPuzzles = puzzleTypeData.slice(0, 7);

        return top7NewPuzzles.map(puzzle => ({
            puzzleType: puzzle.puzzleType,
            addedDate: puzzle.addedDate,
            description: getPuzzleDescription(puzzle.puzzleType)
        }));

    } catch (error) {
        console.error("Error fetching new puzzles:", error.message);
        return [];
    }
}

/**
 * Fetch popular categories from Firebase based on actual user activity
 */
async function fetchPopularCategoriesFromFirebase() {
    const { db } = await import('../config/firebaseAdmin.js');
    const { PUZZLE_TYPES } = await import('../config/puzzleConfig.js');

    try {
        const puzzleTypes = Object.values(PUZZLE_TYPES);
        const popularityData = [];

        for (const puzzleType of puzzleTypes) {
            try {
                const analyticsSnapshot = await db.collection('puzzle_analytics')
                    .where('puzzle_type', '==', puzzleType)
                    .where('event_name', '==', 'puzzle_complete')
                    .orderBy('timestamp', 'desc')
                    .limit(1000)
                    .get();

                const playCount = analyticsSnapshot.size;
                let totalScore = 0;
                let ratingCount = 0;

                analyticsSnapshot.forEach(doc => {
                    const data = doc.data();
                    if (data.score !== undefined && data.score !== null) {
                        const normalizedRating = Math.min(5, Math.max(1, (data.score / 100) * 4 + 1));
                        totalScore += normalizedRating;
                        ratingCount++;
                    }
                });

                const averageRating = ratingCount > 0
                    ? Math.round((totalScore / ratingCount) * 10) / 10
                    : 4.0;

                if (playCount > 0) {
                    popularityData.push({ puzzleType, playCount, averageRating });
                }
            } catch (error) {
                // Continue to next puzzle type
            }
        }

        if (popularityData.length === 0) {
            throw new Error('No popularity data retrieved from Firebase');
        }

        popularityData.sort((a, b) => b.playCount - a.playCount);
        return popularityData.slice(0, 10);

    } catch (error) {
        console.error("Error fetching popular categories:", error.message);
        
        // Return fallback data on error using actual puzzle types from config
        const { PUZZLE_TYPES } = await import('../config/puzzleConfig.js');
        
        return [
            { puzzleType: PUZZLE_TYPES.MATH, playCount: 15420, averageRating: 4.5 },
            { puzzleType: PUZZLE_TYPES.TRIVIA, playCount: 12850, averageRating: 4.6 },
            { puzzleType: PUZZLE_TYPES.ANAGRAM, playCount: 11200, averageRating: 4.4 },
            { puzzleType: PUZZLE_TYPES.CROSSWORD, playCount: 9800, averageRating: 4.7 },
            { puzzleType: PUZZLE_TYPES.WORD_SEARCH, playCount: 8900, averageRating: 4.3 },
            { puzzleType: PUZZLE_TYPES.MEMORY_STORY, playCount: 7650, averageRating: 4.5 }
        ];
    }
}

/**
 * Get today's puzzles for a specific type and difficulty
 * Primary endpoint for serving cached daily puzzles
 */
router.get("/todays-puzzles/:puzzleType", publicLimiter, async (req, res) => {
    const requestId = `req-${Date.now()}-${Math.random().toString(36).substr(2, 9)}`;

    try {
        const { puzzleType } = req.params;
        const { difficulty = 'easy', count, userId } = req.query;
        const startTime = Date.now();

        // Validate puzzle type
        if (!VALID_PUZZLE_TYPES.has(puzzleType) && !VALID_PUZZLE_TYPES.has(puzzleType.toLowerCase())) {
            return res.status(400).json({
                success: false,
                message: `Invalid puzzle type: ${puzzleType}`,
                error: 'invalid_puzzle_type'
            });
        }

        // Validate difficulty
        const validDifficulties = ['easy', 'medium', 'hard'];
        const normalizedDifficulty = difficulty.toLowerCase();
        if (!validDifficulties.includes(normalizedDifficulty)) {
            return res.status(400).json({
                success: false,
                message: `Invalid difficulty. Must be one of: ${validDifficulties.join(', ')}`,
                error: 'invalid_difficulty'
            });
        }

        // Parse count parameter
        let requestedCount = null;
        if (count) {
            requestedCount = parseInt(count, 10);
            if (isNaN(requestedCount) || requestedCount < 1 || requestedCount > 100) {
                return res.status(400).json({
                    success: false,
                    message: 'Count must be a number between 1 and 100',
                    error: 'invalid_count'
                });
            }
        }

        const result = await dailyPuzzleCache.getTodaysPuzzles(
            puzzleType,
            normalizedDifficulty,
            requestedCount
        );

        const totalAvailable = result.totalAvailable || 0;
        const hasNoPuzzles = !result.success || !result.puzzles || result.puzzles.length === 0;
        const isLowOnPuzzles = totalAvailable > 0 && totalAvailable < LOW_PUZZLE_THRESHOLD;

        // ========== AUTO-GENERATION LOGIC ==========
        
        // Case 1: Completely out of puzzles - generate synchronously and wait
        if (hasNoPuzzles) {
            try {
                const genResult = await triggerAutoGenerationSafe(
                    puzzleType,
                    normalizedDifficulty,
                    EMPTY_CACHE_GENERATION_COUNT,
                    {
                        requestId,
                        trigger: 'empty_cache',
                        userId: userId || 'anonymous'
                    }
                );

                if (genResult.success) {
                    
                    // Refresh cache with newly generated puzzles
                    await dailyPuzzleCache.refreshCache(puzzleType, normalizedDifficulty);
                    
                    // Fetch again from refreshed cache
                    const newResult = await dailyPuzzleCache.getTodaysPuzzles(
                        puzzleType,
                        normalizedDifficulty,
                        requestedCount
                    );

                    if (newResult.success && newResult.puzzles.length > 0) {
                        const totalResponseTime = Date.now() - startTime;
                        
                        // Return the newly generated puzzle(s)
                        if (requestedCount && requestedCount > 1) {
                            return res.json({
                                success: true,
                                puzzles: newResult.puzzles,
                                remainingPuzzles: Math.max(0, newResult.totalAvailable - newResult.puzzles.length),
                                totalPuzzles: newResult.totalAvailable,
                                metadata: {
                                    puzzleType,
                                    difficulty: normalizedDifficulty,
                                    totalAvailable: newResult.totalAvailable,
                                    returned: newResult.puzzles.length,
                                    source: 'fresh_generation',
                                    cacheAge: 0,
                                    date: new Date().toISOString().split('T')[0],
                                    responseTime: totalResponseTime
                                },
                                message: `Fresh ${puzzleType} puzzles generated on demand!`,
                                wasGenerated: true,
                                generatedCount: genResult.count
                            });
                        } else {
                            // Single puzzle response
                            return res.json({
                                success: true,
                                puzzleData: newResult.puzzles[0],
                                remainingPuzzles: Math.max(0, newResult.totalAvailable - 1),
                                totalPuzzles: newResult.totalAvailable,
                                metadata: {
                                    source: 'fresh_generation',
                                    cacheAge: 0,
                                    date: new Date().toISOString().split('T')[0],
                                    responseTime: totalResponseTime
                                },
                                resetMessage: `Fresh ${puzzleType} puzzles generated on demand!`,
                                wasGenerated: true,
                                generatedCount: genResult.count
                            });
                        }
                    }
                }
                
            } catch (genError) {
                console.error(`Generation error for ${puzzleType}/${normalizedDifficulty}:`, genError.message);
            }

            // Fallback: Return empty state but with success=true and generation in progress message
            const totalResponseTime = Date.now() - startTime;
            return res.json({
                success: true,
                puzzleData: null,
                remainingPuzzles: 0,
                totalPuzzles: 0,
                message: `Puzzles are being generated for ${puzzleType}/${normalizedDifficulty}. Please try again in a moment.`,
                generationInProgress: true,
                retryAfter: 30, // seconds
                metadata: {
                    source: 'generation_attempted',
                    date: new Date().toISOString().split('T')[0],
                    responseTime: totalResponseTime
                }
            });
        }

        // Case 2: Low on puzzles - trigger async generation (don't wait)
        if (isLowOnPuzzles) {
            triggerAutoGenerationSafe(
                puzzleType,
                normalizedDifficulty,
                AUTO_GENERATION_COUNT,
                {
                    requestId,
                    trigger: 'low_puzzle_threshold',
                    remainingPuzzles: totalAvailable,
                    userId: userId || 'anonymous'
                }
            ).then(genResult => {
                if (genResult.success) {
                    return dailyPuzzleCache.refreshCache(puzzleType, normalizedDifficulty);
                }
            }).catch(() => {
                // Background generation failed silently
            });
        }

        // ========== END AUTO-GENERATION LOGIC ==========

        const totalResponseTime = Date.now() - startTime;
        
        // Return available puzzles (always array format for consistency)
        const response = {
            success: true,
            puzzles: result.puzzles,
            remainingPuzzles: Math.max(0, result.totalAvailable - result.puzzles.length),
            totalPuzzles: result.totalAvailable,
            metadata: {
                puzzleType,
                difficulty: normalizedDifficulty,
                totalAvailable: result.totalAvailable,
                returned: result.puzzles.length,
                requestedCount: requestedCount || 'all',
                source: result.source,
                cacheAge: result.cacheAge,
                date: new Date().toISOString().split('T')[0],
                responseTime: totalResponseTime
            }
        };

        if (result.source === 'fresh_build') {
            response.message = `Fresh ${puzzleType} puzzles prepared for today!`;
            response.wasReset = true;
        } else if (isLowOnPuzzles) {
            response.message = `Today's ${puzzleType} puzzles (${result.cacheAge} minutes old) - more puzzles being generated`;
            response.generationInProgress = true;
        } else {
            response.message = `Today's ${puzzleType} puzzles (${result.cacheAge} minutes old)`;
        }

        response.usage = {
            recommendation: 'Cache these puzzles locally for offline play',
            refreshInterval: '24 hours',
            nextRefresh: new Date(Date.now() + 24 * 60 * 60 * 1000).toISOString()
        };

        return res.json(response);

    } catch (error) {
        const errorTime = Date.now() - (req.startTime || Date.now());
        console.error(`[${requestId}] ERROR: ${error.message} (${errorTime}ms)`, error.stack);
        
        // Even on error, return success with null data
        res.status(200).json({
            success: true,
            message: "Temporary issue fetching puzzles. Please try again.",
            puzzleData: null,
            remainingPuzzles: 0,
            totalPuzzles: 0,
            error: error.message,
            responseTime: errorTime
        });
    }
});

/**
 * Get today's puzzles for all difficulties of a specific type
 */
router.get("/todays-puzzles-bulk/:puzzleType", publicLimiter, async (req, res) => {
    try {
        const { puzzleType } = req.params;
        const { count, userId, email } = req.query;

        const startTime = Date.now();

        // Validate puzzle type
        if (!VALID_PUZZLE_TYPES.has(puzzleType) && !VALID_PUZZLE_TYPES.has(puzzleType.toLowerCase())) {
            return res.status(400).json({
                success: false,
                message: `Invalid puzzle type: ${puzzleType}`,
                error: 'invalid_puzzle_type'
            });
        }

        // Parse count parameter
        let requestedCount = null;
        if (count) {
            requestedCount = parseInt(count, 10);
            if (isNaN(requestedCount) || requestedCount < 1 || requestedCount > 100) {
                return res.status(400).json({
                    success: false,
                    message: 'Count must be a number between 1 and 100',
                    error: 'invalid_count'
                });
            }
        }


        // Fetch all difficulties in parallel
        const difficulties = ['easy', 'medium', 'hard'];
        const fetchPromises = difficulties.map(async (difficulty) => {
            try {
                const result = await dailyPuzzleCache.getTodaysPuzzles(
                    puzzleType, 
                    difficulty, 
                    requestedCount
                );
                return {
                    difficulty,
                    ...result
                };
            } catch (error) {
                return {
                    difficulty,
                    success: false,
                    error: error.message,
                    puzzles: []
                };
            }
        });

        const results = await Promise.all(fetchPromises);
        
        // Organize results by difficulty
        const organizedResults = {};
        let totalPuzzles = 0;
        let hasErrors = false;
        
        results.forEach(result => {
            organizedResults[result.difficulty] = {
                success: result.success,
                puzzles: result.puzzles,
                totalAvailable: result.totalAvailable,
                source: result.source,
                cacheAge: result.cacheAge
            };
            
            if (result.success) {
                totalPuzzles += result.puzzles.length;
            } else {
                hasErrors = true;
                organizedResults[result.difficulty].error = result.error;
            }
        });

        const response = {
            success: !hasErrors || totalPuzzles > 0,
            puzzleType,
            difficulties: organizedResults,
            metadata: {
                totalPuzzles,
                requestedPerDifficulty: requestedCount,
                date: new Date().toISOString().split('T')[0],
                responseTime: Date.now() - startTime
            }
        };

        if (hasErrors) {
            response.message = 'Some difficulties had errors, but partial results returned';
        } else {
            response.message = `Today's ${puzzleType} puzzles for all difficulties`;
        }

        res.json(response);

    } catch (error) {
        console.error("❌ Error in todays-puzzles-bulk endpoint:", error);
        
        res.status(500).json({
            success: false,
            message: "Internal server error",
            error: error.message,
            responseTime: Date.now() - (req.startTime || Date.now())
        });
    }
});

// ============================================================================
// ADMIN ENDPOINTS
// ============================================================================

/**
 * Admin endpoint to manually refresh cache
 */
router.post("/admin/refresh-cache", adminLimiter, validateAdmin, async (req, res) => {
    try {
        const { puzzleType, difficulty } = req.body;
        

        const result = await dailyPuzzleCache.refreshCache(puzzleType, difficulty);
        
        if (result.success) {
            res.json({
                success: true,
                message: 'Cache refreshed successfully',
                refreshed: result.refreshed,
                total: result.total || 1,
                timestamp: new Date().toISOString()
            });
        } else {
            res.status(500).json({
                success: false,
                message: 'Cache refresh failed',
                error: result.error,
                errors: result.errors
            });
        }

    } catch (error) {
        console.error("❌ Error in admin cache refresh:", error);
        res.status(500).json({
            success: false,
            message: "Internal server error",
            error: error.message
        });
    }
});

/**
 * Get cache statistics
 */
router.get("/admin/cache-stats", adminLimiter, validateAdmin, async (req, res) => {
    try {
        const stats = dailyPuzzleCache.getStats();
        
        res.json({
            success: true,
            stats,
            timestamp: new Date().toISOString()
        });

    } catch (error) {
        console.error("❌ Error getting cache stats:", error);
        
        res.status(500).json({
            success: false,
            message: "Internal server error",
            error: error.message
        });
    }
});

/**
 * Get comprehensive system metrics
 */
router.get("/admin/system-metrics", adminLimiter, validateAdmin, async (req, res) => {
    try {
        const metrics = await getDailyPuzzleSystemMetrics();
        res.json(metrics);

    } catch (error) {
        console.error("❌ Error getting system metrics:", error);
        res.status(500).json({
            success: false,
            error: error.message
        });
    }
});

/**
 * Update cache configuration
 */
router.put("/admin/cache-config/:puzzleType/:difficulty", adminLimiter, validateAdmin, async (req, res) => {
    try {
        const { puzzleType, difficulty } = req.params;
        const { cacheSize, priority } = req.body;
        
        const result = await updateCacheConfiguration(puzzleType, difficulty, {
            cacheSize: parseInt(cacheSize),
            priority
        });

        res.json(result);

    } catch (error) {
        console.error("❌ Error updating cache configuration:", error);
        res.status(500).json({
            success: false,
            error: error.message
        });
    }
});

/**
 * Clear all caches
 */
router.delete("/admin/clear-caches", adminLimiter, validateAdmin, async (req, res) => {
    try {

        const result = await dailyPuzzleCache.clearAllCaches();
        
        res.json({
            success: result.success,
            message: result.success ? 'All caches cleared' : 'Failed to clear caches',
            clearedKeys: result.clearedKeys,
            error: result.error,
            timestamp: new Date().toISOString()
        });

    } catch (error) {
        console.error("❌ Error clearing caches:", error);
        
        res.status(500).json({
            success: false,
            message: "Internal server error",
            error: error.message
        });
    }
});

// ============================================================================
// HEALTH CHECK ENDPOINTS
// ============================================================================

/**
 * Health check for daily puzzle cache system
 */
router.get("/health", async (req, res) => {
    try {
        const health = await checkDailyPuzzleSystemHealth();
        const statusCode = health.status === 'healthy' ? 200 : 503;
        res.status(statusCode).json(health);
    } catch (error) {
        res.status(503).json({
            service: 'daily-puzzle-cache',
            status: 'unhealthy',
            error: error.message,
            timestamp: new Date().toISOString()
        });
    }
});

// ============================================================================
// CLOUD SCHEDULER ENDPOINTS (for Google Cloud Scheduler)
// ============================================================================

/**
 * Trigger daily puzzle cache refresh
 * This endpoint is designed to be called by Google Cloud Scheduler
 * It validates the request using a secret token in the header
 */
router.post("/scheduler/trigger-daily-refresh", async (req, res) => {
    const startTime = Date.now();

    // Validate scheduler secret (set this in Cloud Scheduler job headers)
    const schedulerSecret = req.headers['x-scheduler-secret'];
    const expectedSecret = process.env.SCHEDULER_SECRET;

    if (!expectedSecret) {
        console.error('[SCHEDULER-ENDPOINT] SCHEDULER_SECRET env var not configured');
        return res.status(500).json({
            success: false,
            error: 'Scheduler not configured'
        });
    }

    if (schedulerSecret !== expectedSecret) {
        console.warn('[SCHEDULER-ENDPOINT] Invalid scheduler secret');
        return res.status(403).json({
            success: false,
            error: 'Invalid scheduler secret'
        });
    }

    try {
        console.log('[SCHEDULER-ENDPOINT] Daily refresh triggered by Cloud Scheduler');

        // Run the daily refresh
        await cacheScheduler.performDailyRefresh();

        const duration = Date.now() - startTime;
        const stats = cacheScheduler.getStats();

        res.json({
            success: true,
            message: 'Daily refresh completed',
            duration: `${duration}ms`,
            stats: {
                lastRefresh: stats.lastRefresh,
                refreshCount: stats.refreshCount,
                errorCount: stats.errorCount,
                lastError: stats.lastError
            },
            timestamp: new Date().toISOString()
        });

    } catch (error) {
        console.error('[SCHEDULER-ENDPOINT] Daily refresh failed:', error.message);
        res.status(500).json({
            success: false,
            error: error.message,
            timestamp: new Date().toISOString()
        });
    }
});

/**
 * Trigger health check (can be used for monitoring)
 */
router.post("/scheduler/trigger-health-check", async (req, res) => {
    const schedulerSecret = req.headers['x-scheduler-secret'];
    const expectedSecret = process.env.SCHEDULER_SECRET;

    if (!expectedSecret || schedulerSecret !== expectedSecret) {
        return res.status(403).json({ success: false, error: 'Invalid scheduler secret' });
    }

    try {
        await cacheScheduler.performHealthCheck();
        res.json({
            success: true,
            message: 'Health check completed',
            timestamp: new Date().toISOString()
        });
    } catch (error) {
        res.status(500).json({
            success: false,
            error: error.message,
            timestamp: new Date().toISOString()
        });
    }
});

/**
 * Get scheduler status (for debugging)
 */
router.get("/scheduler/status", validateAdmin, async (req, res) => {
    try {
        const stats = cacheScheduler.getStats();
        res.json({
            success: true,
            scheduler: stats,
            timestamp: new Date().toISOString()
        });
    } catch (error) {
        res.status(500).json({
            success: false,
            error: error.message
        });
    }
});

export default router;