// routes/puzzleEndpoints.routes.js - Puzzle fetching and generation endpoints
import express from 'express';
import { supabase } from '../config/database.js';
import { db } from '../config/firebaseAdmin.js';
import { riddleVerseRateLimit } from '../config/middleware.js';
import { normalizeDifficulty } from '../utils/puzzleUtils.js';
import { userLimitService } from '../services/userLimitService.js';
import { regenerationService } from '../services/regenerationService.js';
import {
    checkAnswer,
    listCustomPuzzles,
    fetchNextPuzzle,
    fetchRandomPuzzle,
    generateCustomPuzzles,
    fetchCustomPuzzle,
    resetPuzzleProgress,
    resetAllUsersPuzzleProgress,
    getPuzzleLeaderboard,
    getFeaturedPuzzles,
    triggerAutoGenerationSafe,
    updatePuzzleLeaderboard
} from '../services/puzzleService.js';
import { getNextDailyPuzzle, hasDailyPuzzles } from '../services/dailyPuzzles.js';

const router = express.Router();

const ANONYMOUS_USER_ID = "anon_sequential_user";

// RiddleVerse authentication middleware
const authenticateRiddleVerse = (req, res, next) => {
    const apiKey = req.headers['x-api-key'];
    const platform = req.headers['x-platform'];
    const bundleId = req.headers['x-bundle-id'];
    const userAgent = req.get('User-Agent') || '';

    if (apiKey && platform) {
        const API_KEYS = {
            'ios': process.env.IOS_API_KEY,
            'android': process.env.ANDROID_API_KEY
        };

        if (API_KEYS[platform] === apiKey) {
            req.authMethod = 'api_key';
            return next();
        } else {
            return res.status(403).json({
                success: false,
                message: "Invalid API credentials"
            });
        }
    }

    const RIDDLEVERSE_PATTERNS = [
        /RiddleVerse/i,
        /KreativeKoala\.PuzzleForge/i,
        /PuzzleForge/i,
        /CFNetwork.*Darwin/i,
        /com\.kreativekoala\.riddleverse/i,
        /riddleverse.*android/i,
        /okhttp/i,
        /riddleverse/i,
        /kreativekoala/i
    ];

    const matchedPattern = RIDDLEVERSE_PATTERNS.find(pattern => pattern.test(userAgent));

    if (matchedPattern) {
        req.authMethod = 'user_agent';
        return next();
    }

    if (bundleId) {
        const VALID_BUNDLE_IDS = [
            'com.kreativekoala.riddleverse',
            'KreativeKoala.PuzzleForge',
        ];

        if (VALID_BUNDLE_IDS.includes(bundleId)) {
            req.authMethod = 'bundle_id';
            return next();
        }
    }

    req.authMethod = 'unknown_allowed';
    next();
};

/**
 * Fetch next puzzle for iOS app
 * NOW: tries the in-memory daily cache first; falls back to legacy Firebase path.
 */
router.get("/fetch-next-puzzle-ios/:puzzleType", authenticateRiddleVerse, riddleVerseRateLimit, async (req, res) => {
    try {
        const { userId, email } = req.query;
        const puzzleType = req.params.puzzleType;
        const difficulty = normalizeDifficulty(req.query.difficulty || 'easy');

        // ---- Try daily cache first (no Firebase, no Redis) ----
        if (hasDailyPuzzles(puzzleType, difficulty)) {
            const puzzle = getNextDailyPuzzle(puzzleType, difficulty);
            if (puzzle) {
                return res.json({
                    success: true,
                    puzzleData: puzzle,
                    source: 'daily_cache',
                });
            }
        }

        // ---- Fallback: legacy per-user Firebase path ----
        if (!userId || !email || userId === "" || email === "") {
            const anonUserRef = db.collection("users").doc(ANONYMOUS_USER_ID);
            let anonUserDoc = await anonUserRef.get();

            if (!anonUserDoc.exists) {
                await anonUserRef.set({
                    email: "anonymous@sequential.user",
                    lastPuzzleTimestamp: null,
                    puzzlesAttempted: 0,
                    puzzlesSolved: 0,
                    lastPuzzleTimestamps: {},
                    puzzleProgress: {}
                });
            }

            const result = await fetchRandomPuzzle(puzzleType, difficulty);
            if (!result.success) {
                return res.status(404).json(result);
            }
            return res.json(result);
        }

        const userRef = db.collection("users").doc(userId);
        let userDoc = await userRef.get();

        if (!userDoc.exists) {
            await userRef.set({
                email,
                lastPuzzleTimestamp: null,
                puzzlesAttempted: 0,
                puzzlesSolved: 0,
                lastPuzzleTimestamps: {},
                puzzleProgress: {}
            });
        }

        const result = await fetchNextPuzzle(userId, puzzleType, difficulty);

        if (!result.success) {
            return res.status(404).json(result);
        }

        res.json(result);

    } catch (error) {
        console.error("Error fetching next puzzle:", error);
        res.status(500).json({
            success: false,
            message: "Error fetching puzzle",
            error: error.message
        });
    }
});

/**
 * Batch fetch puzzles
 */
router.post("/batch-fetch-puzzles", async (req, res) => {
    try {
        const { userId, email, puzzleTypes, puzzlesPerType = 5, difficulty = "medium" } = req.body;

        if (!userId || !Array.isArray(puzzleTypes) || puzzleTypes.length === 0) {
            return res.status(400).json({
                success: false,
                error: "userId and puzzleTypes array are required"
            });
        }

        const userRef = db.collection("users").doc(userId);
        let userDoc = await userRef.get();

        if (!userDoc.exists) {
            await userRef.set({
                email: email || "",
                lastPuzzleProgress: {},
                lastPuzzleTimestamps: {}
            });
            userDoc = await userRef.get();
        }

        const userData = userDoc.data() || {};
        const progress = userData.lastPuzzleProgress || {};

        const batchPromises = puzzleTypes.map(async (puzzleType) => {
            const progressKey = `${puzzleType}_${difficulty}`;
            const currentPuzzleId = progress[progressKey];
            return await getPuzzleSequence(puzzleType, difficulty, currentPuzzleId, puzzlesPerType);
        });

        const sequenceResults = await Promise.all(batchPromises);

        const allPuzzleIds = [];
        sequenceResults.forEach(result => {
            if (result.puzzleIds && result.puzzleIds.length > 0) {
                allPuzzleIds.push(...result.puzzleIds);
            }
        });

        let puzzleDataMap = {};
        if (allPuzzleIds.length > 0) {
            const { data: allPuzzles, error: puzzleError } = await supabase
                .from('puzzles')
                .select('*')
                .in('puzzleid', allPuzzleIds);

            if (!puzzleError && allPuzzles) {
                puzzleDataMap = allPuzzles.reduce((map, puzzle) => {
                    map[puzzle.puzzleid] = puzzle;
                    return map;
                }, {});
            }
        }

        const results = {};
        const newProgress = { ...progress };
        const resetTypes = [];

        puzzleTypes.forEach((puzzleType, index) => {
            const sequenceResult = sequenceResults[index];
            const progressKey = `${puzzleType}_${difficulty}`;

            if (sequenceResult.puzzleIds && sequenceResult.puzzleIds.length > 0) {
                results[puzzleType] = sequenceResult.puzzleIds.map(id => puzzleDataMap[id]).filter(Boolean);
                const lastPuzzleId = sequenceResult.puzzleIds[sequenceResult.puzzleIds.length - 1];
                newProgress[progressKey] = lastPuzzleId;

                if (sequenceResult.wasReset) {
                    resetTypes.push(puzzleType);
                }
            } else {
                results[puzzleType] = [];
            }
        });

        if (JSON.stringify(newProgress) !== JSON.stringify(progress)) {
            await userRef.set({ lastPuzzleProgress: newProgress }, { merge: true });
        }

        const response = {
            success: true,
            puzzleData: results,
            totalPuzzles: Object.values(results).reduce((sum, arr) => sum + arr.length, 0)
        };

        if (resetTypes.length > 0) {
            response.resetNotification = {
                resetTypes,
                message: `You've completed all puzzles for: ${resetTypes.join(', ')}. Starting over from the beginning!`
            };
        }

        res.json(response);

    } catch (error) {
        console.error("Error in batch puzzle fetch:", error);
        res.status(500).json({
            success: false,
            error: "Internal server error",
            message: error.message
        });
    }
});

/**
 * Get puzzle sequence helper
 */
async function getPuzzleSequence(puzzleType, difficulty, currentPuzzleId, count) {
    try {
        const puzzleIds = [];
        let nextPuzzleId = currentPuzzleId;
        let wasReset = false;

        if (!nextPuzzleId) {
            const { data: firstPuzzle } = await supabase
                .from('puzzles')
                .select('puzzleid')
                .ilike('type', puzzleType)
                .eq('difficulty', difficulty)
                .not('puzzleid', 'is', null)
                .order('timestamp', { ascending: true })
                .limit(1);

            nextPuzzleId = firstPuzzle?.[0]?.puzzleid;
        }

        for (let i = 0; i < count && nextPuzzleId; i++) {
            const { data: pathData } = await supabase
                .from('puzzle_path')
                .select('nextpuzzleid')
                .eq('puzzleid', nextPuzzleId)
                .limit(1);

            const nextInPath = pathData?.[0]?.nextpuzzleid;

            if (nextInPath) {
                puzzleIds.push(nextInPath);
                nextPuzzleId = nextInPath;
            } else {
                if (puzzleIds.length < count) {
                    wasReset = true;

                    const { data: firstPuzzle } = await supabase
                        .from('puzzles')
                        .select('puzzleid')
                        .ilike('type', puzzleType)
                        .eq('difficulty', difficulty)
                        .not('puzzleid', 'is', null)
                        .order('timestamp', { ascending: true })
                        .limit(1);

                    if (firstPuzzle?.[0]?.puzzleid) {
                        nextPuzzleId = firstPuzzle[0].puzzleid;

                        for (let j = puzzleIds.length; j < count && nextPuzzleId; j++) {
                            const { data: pathData } = await supabase
                                .from('puzzle_path')
                                .select('nextpuzzleid')
                                .eq('puzzleid', nextPuzzleId)
                                .limit(1);

                            const nextInPath = pathData?.[0]?.nextpuzzleid;

                            if (nextInPath) {
                                puzzleIds.push(nextInPath);
                                nextPuzzleId = nextInPath;
                            } else {
                                break;
                            }
                        }
                    }
                }
                break;
            }
        }

        return { puzzleIds, puzzleType, difficulty, wasReset };

    } catch (error) {
        console.error(`Error getting sequence for ${puzzleType}:`, error);
        return { puzzleIds: [], puzzleType, difficulty, error: error.message };
    }
}

/**
 * Check answer endpoint
 */
router.post("/check-answer", async (req, res) => {
    try {
        const { question, expected_answer, guessed_answer, modelName = "gpt-3.5-turbo" } = req.body;

        if (!question || !expected_answer || !guessed_answer) {
            return res.status(400).json({ success: false, message: "Missing required fields" });
        }

        const result = await checkAnswer(null, question, expected_answer, guessed_answer, modelName);
        res.json({ correct: result });

    } catch (error) {
        console.error("🔥 Error checking answer:", error);
        res.status(500).json({ success: false, correct: false, message: "Error checking answer" });
    }
});

/**
 * Reset puzzle progress endpoints
 */
router.get('/reset-puzzle-progress', (req, res) => {
    req.body = {
        userId: req.query.userId,
        puzzleType: req.query.puzzleType,
        difficulty: req.query.difficulty
    };
    resetPuzzleProgress(req, res);
});

router.get('/reset-all-users-puzzle-progress', (req, res) => {
    req.body = {
        puzzleType: req.query.puzzleType,
        difficulty: req.query.difficulty
    };
    resetAllUsersPuzzleProgress(req, res);
});

/**
 * Request puzzle regeneration
 */
router.post('/request-puzzle-regeneration', async (req, res) => {
    try {
        const { puzzleSetId, userEmail, reason } = req.body;

        if (!puzzleSetId || !userEmail) {
            return res.status(400).json({
                success: false,
                error: 'Missing required fields: puzzleSetId and userEmail'
            });
        }

        const result = await regenerationService.requestRegeneration(puzzleSetId, userEmail, reason);

        if (!result.success) {
            return res.status(result.status || 500).json(result);
        }

        res.status(202).json(result);

    } catch (error) {
        console.error('❌ Error in regeneration request:', error);
        res.status(500).json({
            success: false,
            error: error.message
        });
    }
});

/**
 * Generate puzzle endpoint
 */
router.get("/generate-puzzle", async (req, res) => {
    try {
        const { puzzleType, modelName, difficulty } = req.query;

        if (!puzzleType || !modelName) {
            return res.status(400).json({ success: false, message: "Missing puzzleType or modelName" });
        }

        let normalizedDifficulty = "Medium";
        if (difficulty) {
            normalizedDifficulty = difficulty.charAt(0).toUpperCase() + difficulty.slice(1).toLowerCase();
        }

        const generationResult = await triggerAutoGenerationSafe(
            puzzleType.toLowerCase(),
            normalizedDifficulty.toLowerCase(),
            5
        );

        await userLimitService.recordGeneration(
            req.query.userId || 'anonymous',
            puzzleType,
            difficulty,
            generationResult.success,
            req.ip,
            req.headers['user-agent'],
            req.sessionID || null,
            'ai'
        );

        res.json({
            success: generationResult.success,
            message: `${normalizedDifficulty} ${puzzleType} puzzle generation started with enhanced path management.`,
            puzzleType,
            difficulty: normalizedDifficulty,
            modelName,
            generationId: generationResult.generationId,
            targetCount: generationResult.targetCount,
            enhancement: "path_management_enabled"
        });

    } catch (error) {
        console.error("Error starting enhanced puzzle generation:", error);
        res.status(500).json({
            success: false,
            message: "Error starting puzzle generation",
            error: error.message
        });
    }
});

/**
 * Fetch custom puzzle
 */
router.get('/fetch-custom-puzzle', async (req, res) => {
    const { puzzleId } = req.query;

    if (!puzzleId) {
        return res.status(400).json({ error: "Missing puzzleId parameter" });
    }

    try {
        const result = await fetchCustomPuzzle(puzzleId);

        if (result.status) {
            return res.status(202).json({
                status: result.status,
                message: result.message,
                estimatedTimeRemaining: result.estimatedTimeRemaining,
                puzzleSet: result.puzzleSet,
                action: result.action,
                failedAt: result.failedAt
            });
        } else {
            return res.status(200).json(result);
        }

    } catch (error) {
        console.error("❌ Error fetching custom puzzle:", error);

        if (error.message === "Custom puzzle set not found.") {
            return res.status(404).json({
                error: "Custom puzzle set not found",
                message: "The requested puzzle set does not exist."
            });
        }

        return res.status(500).json({
            error: "Internal server error",
            message: "Failed to fetch custom puzzle set."
        });
    }
});

/**
 * List custom puzzles
 */
router.get('/list-custom-puzzles', async (req, res) => {
    try {
        const limit = parseInt(req.query.limit || "10", 10);
        const offset = parseInt(req.query.offset || "0", 10);
        const userEmail = req.query.userEmail;

        const result = await listCustomPuzzles(
            Math.floor(offset / limit) + 1,
            limit,
            offset,
            userEmail
        );

        if (!result.success) {
            return res.status(500).json({
                error: "Failed to fetch puzzles",
                message: result.error
            });
        }

        return res.json({
            puzzleData: result.puzzles,
            page: result.page,
            totalPages: result.totalPages,
            totalCount: result.totalCount
        });

    } catch (error) {
        console.error("❌ API Error listing puzzles:", error);
        return res.status(500).json({
            error: "Internal server error",
            message: error.message
        });
    }
});

/**
 * Get puzzle leaderboard
 */
router.get('/get-puzzle-leaderboard/:puzzleId', async (req, res) => {
    try {
        const { puzzleId } = req.params;
        const { userId } = req.query;

        const result = await getPuzzleLeaderboard(puzzleId, userId);

        if (!result.success) {
            return res.status(500).json({
                success: false,
                error: "Failed to fetch leaderboard",
                message: result.error
            });
        }

        return res.json({
            success: true,
            puzzleId: result.puzzleId,
            puzzleName: result.puzzleName,
            puzzleCreator: result.puzzleCreator,
            topPlayers: result.topPlayers,
            userRank: result.userRank,
            userScore: result.userScore,
            userTime: result.userTime,
            userPercentile: result.userPercentile,
            totalPlayers: result.totalPlayers,
            statistics: result.statistics,
            isEmpty: result.isEmpty
        });

    } catch (error) {
        console.error("❌ API Error fetching leaderboard:", error);
        return res.status(500).json({
            success: false,
            error: "Internal server error",
            message: error.message
        });
    }
});

/**
 * Get featured puzzles
 */
router.get('/get-featured-puzzles', async (req, res) => {
    try {
        const { limit = 5 } = req.query;

        const result = await getFeaturedPuzzles(limit);

        if (!result.success) {
            return res.status(500).json({
                success: false,
                error: "Failed to fetch featured puzzles",
                message: result.error
            });
        }

        return res.json({
            success: true,
            featuredPuzzles: result.featuredPuzzles,
            count: result.count
        });

    } catch (error) {
        console.error("❌ API Error fetching featured puzzles:", error);
        return res.status(500).json({
            success: false,
            error: "Internal server error",
            message: error.message
        });
    }
});

/**
 * Update puzzle leaderboard
 */
router.post("/update-puzzle-leaderboard", async (req, res) => {
    try {
        const { puzzleId, userId, timeTaken, score } = req.body;

        if (!puzzleId || !userId || timeTaken === undefined || score === undefined) {
            return res.status(400).json({
                success: false,
                error: "Missing required parameters: puzzleId, userId, timeTaken, score"
            });
        }

        const validPuzzleIdPattern = /^riddle-\d+$/;
        if (!validPuzzleIdPattern.test(puzzleId)) {
            return res.status(400).json({
                success: false,
                error: "Invalid puzzleId format. Expected: riddle-<timestamp>"
            });
        }

        const result = await updatePuzzleLeaderboard(puzzleId, userId, timeTaken, score);

        if (!result.success) {
            return res.status(500).json({
                success: false,
                error: "Failed to update leaderboard",
                message: result.error
            });
        }

        return res.json({
            success: true,
            message: "Leaderboard updated successfully",
            leaderboard: result.leaderboard,
            userStats: result.userStats,
            leaderboardStats: result.leaderboardStats,
            achievements: result.achievements
        });

    } catch (error) {
        console.error("❌ API Error updating leaderboard:", error);
        return res.status(500).json({
            success: false,
            error: "Internal server error",
            message: error.message
        });
    }
});

/**
 * Delete puzzle leaderboard entry
 */
router.post("/delete-puzzle-leaderboard-entry", async (req, res) => {
    try {
        const { puzzleId, userId } = req.body;

        if (!puzzleId || !userId) {
            return res.status(400).json({
                success: false,
                error: "Missing required parameters: puzzleId, userId"
            });
        }

        const { data: existingEntry, error: checkError } = await supabase
            .from('custom_leaderboard')
            .select('*')
            .eq('parentsetid', puzzleId)
            .eq('userid', userId)
            .limit(1);

        if (checkError) {
            return res.status(500).json({
                success: false,
                error: "Failed to check existing entry"
            });
        }

        if (!existingEntry || existingEntry.length === 0) {
            return res.status(404).json({
                success: false,
                error: "Leaderboard entry not found",
                message: `No entry found for user ${userId} in puzzle ${puzzleId}`
            });
        }

        const deletedEntry = existingEntry[0];

        const { error: deleteError } = await supabase
            .from('custom_leaderboard')
            .delete()
            .eq('parentsetid', puzzleId)
            .eq('userid', userId);

        if (deleteError) {
            return res.status(500).json({
                success: false,
                error: "Failed to delete leaderboard entry"
            });
        }

        const updatedLeaderboard = await getPuzzleLeaderboard(puzzleId);

        return res.json({
            success: true,
            message: "Leaderboard entry deleted successfully",
            deletedEntry: {
                userId: deletedEntry.userid,
                score: deletedEntry.score,
                timeTaken: deletedEntry.timetaken
            },
            updatedLeaderboard: updatedLeaderboard.success ? {
                topPlayers: updatedLeaderboard.topPlayers,
                totalPlayers: updatedLeaderboard.totalPlayers,
                statistics: updatedLeaderboard.statistics
            } : null
        });

    } catch (error) {
        console.error("❌ API Error deleting leaderboard entry:", error);
        return res.status(500).json({
            success: false,
            error: "Internal server error",
            message: error.message
        });
    }
});

export default router;
