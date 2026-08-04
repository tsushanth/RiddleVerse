import express from 'express';
import {
    getPuzzleLeaderboard,
    updatePuzzleLeaderboard,
    getFeaturedPuzzles
} from '../services/puzzleService.js';
import { supabase } from '../config/database.js';

const router = express.Router();

// Get global leaderboard
// Migrated off Firestore 2026-08-04 — was read on every request, a major
// contributor to the project's Firestore daily quota exhaustion. This
// simple global leaderboard is separate from the per-puzzle leaderboard
// (custom_leaderboard, /puzzle/:puzzleId routes below), which was already
// on Supabase.
router.get('/', async (req, res) => {
    try {
        const { data, error } = await supabase
            .from('leaderboard')
            .select('user_id, name, score, last_updated')
            .order('score', { ascending: false })
            .limit(10);

        if (error) throw error;

        const leaderboard = data.map(row => ({
            userId: row.user_id,
            name: row.name,
            score: row.score,
            lastUpdated: row.last_updated
        }));
        res.json({ success: true, leaderboard });
    } catch (error) {
        console.error('Error fetching leaderboard:', error);
        res.status(500).json({ error: 'Failed to fetch leaderboard' });
    }
});

// Get user score
router.get('/score', async (req, res) => {
    const { userId } = req.query;

    if (!userId) {
        return res.status(400).json({ error: 'Missing userId parameter' });
    }

    try {
        const { data, error } = await supabase
            .from('leaderboard')
            .select('name, score, last_updated')
            .eq('user_id', userId)
            .maybeSingle();

        if (error) throw error;
        if (!data) {
            return res.status(404).json({ message: 'User not found in leaderboard' });
        }

        res.json({
            userId: userId,
            name: data.name,
            score: data.score,
            lastUpdated: data.last_updated
        });
    } catch (error) {
        console.error('Error fetching score:', error);
        res.status(500).json({ error: 'Failed to retrieve score' });
    }
});

// Update user score
router.post('/update-score', async (req, res) => {
    const { userId, name, score } = req.body;

    if (!userId || !name || typeof score !== 'number') {
        return res.status(400).json({ error: 'Invalid request' });
    }

    try {
        const { data: existing, error: fetchError } = await supabase
            .from('leaderboard')
            .select('score')
            .eq('user_id', userId)
            .maybeSingle();

        if (fetchError) throw fetchError;

        const previousScore = existing?.score || 0;
        const newTotalScore = previousScore + score;

        const { error: upsertError } = await supabase
            .from('leaderboard')
            .upsert({
                user_id: userId,
                name,
                score: newTotalScore,
                last_updated: new Date().toISOString()
            });

        if (upsertError) throw upsertError;

        res.json({
            message: existing
                ? `Score added. Previous: ${previousScore}, Added: ${score}, New total: ${newTotalScore}`
                : `New score recorded: ${score}`,
            previousScore: previousScore,
            addedScore: score,
            newTotal: newTotalScore
        });
    } catch (error) {
        console.error('Error updating score:', error);
        res.status(500).json({ error: 'Failed to update score' });
    }
});

// Delete user score
router.post('/delete-score', async (req, res) => {
    const { name } = req.body;

    if (!name) {
        return res.status(400).json({ error: "Missing required parameter: name" });
    }

    try {
        const { data: existing, error: fetchError } = await supabase
            .from('leaderboard')
            .select('user_id')
            .eq('name', name);

        if (fetchError) throw fetchError;
        if (!existing || existing.length === 0) {
            return res.status(404).json({ error: "User not found in leaderboard." });
        }

        const { error: deleteError } = await supabase
            .from('leaderboard')
            .delete()
            .eq('name', name);

        if (deleteError) throw deleteError;

        res.json({ message: `User ${name} removed from leaderboard.` });

    } catch (error) {
        console.error("Error deleting user from leaderboard:", error);
        res.status(500).json({ error: "Failed to remove user from leaderboard." });
    }
});

// Get puzzle-specific leaderboard
router.get('/puzzle/:puzzleId', async (req, res) => {
    try {
        const { puzzleId } = req.params;
        const { userId } = req.query;

        console.log(`Fetching leaderboard for ${puzzleId}`);

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
        console.error("Error fetching leaderboard:", error);
        return res.status(500).json({ 
            success: false,
            error: "Internal server error",
            message: error.message 
        });
    }
});

// Update puzzle leaderboard
router.post('/puzzle/:puzzleId', async (req, res) => {
    try {
        const { puzzleId } = req.params;
        const { userId, timeTaken, score, eventId } = req.body;

        if (!userId || timeTaken === undefined || score === undefined) {
            return res.status(400).json({ 
                success: false,
                error: "Missing required parameters: userId, timeTaken, score" 
            });
        }

        // Validate puzzleId format. Accepts every format the puzzle generators
        // currently produce: `riddle-<digits>`, UUIDs (custom games via
        // crypto.randomUUID()), and prefixed formats from per-type generators
        // (`waldo_`, `music_puzzle_`, `ws_`, `flow_`, `img_puzzle_`,
        // `which_is_real_`, `find_object_`, `method3_diff_`, etc.). The single
        // permissive rule that covers all of them — and still rejects path
        // traversal / injection attempts — is "alphanumeric + dash/underscore,
        // starts with a letter, ≤128 chars".
        const validPuzzleIdPattern = /^[A-Za-z][A-Za-z0-9_-]{2,127}$/;
        if (!validPuzzleIdPattern.test(puzzleId)) {
            return res.status(400).json({
                success: false,
                error: "Invalid puzzleId format"
            });
        }

        console.log(`Updating leaderboard for ${puzzleId}`);

        const result = await updatePuzzleLeaderboard(puzzleId, userId, timeTaken, score);

        if (!result.success) {
            return res.status(500).json({
                success: false,
                error: "Failed to update leaderboard",
                message: result.error
            });
        }

        // Feature C: record/complete a play event for trending-score ranking.
        // If iOS passed an eventId from /play-events/start, UPDATE that row so
        // we get an honest completion_rate. Otherwise INSERT a new completed row
        // (legacy/non-iOS clients). UUID-gated to skip non-custom puzzleIds.
        const uuidPattern = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;
        if (uuidPattern.test(puzzleId)) {
            (async () => {
                try {
                    const ms = Number.isFinite(timeTaken) ? Math.max(0, Math.round(timeTaken)) : null;
                    const nowIso = new Date().toISOString();

                    if (eventId && uuidPattern.test(eventId)) {
                        const { error: updErr } = await supabase
                            .from('game_play_events')
                            .update({
                                ended_at: nowIso,
                                completed: true,
                                score: Number.isFinite(score) ? score : 0,
                                ms_played: ms
                            })
                            .eq('id', eventId)
                            .eq('user_id', userId);
                        if (updErr) {
                            console.error('[play_event update] non-fatal:', updErr.message);
                            return;
                        }
                        return;
                    }

                    // Legacy fallback — no event_id from client. Insert a fresh completed row.
                    const { data: cg } = await supabase
                        .from('custom_games')
                        .select('id, series_id')
                        .eq('id', puzzleId)
                        .maybeSingle();
                    if (!cg) return;
                    await supabase.from('game_play_events').insert({
                        game_id: cg.id,
                        series_id: cg.series_id,
                        user_id: userId,
                        started_at: ms ? new Date(Date.now() - ms).toISOString() : nowIso,
                        ended_at: nowIso,
                        completed: true,
                        score: Number.isFinite(score) ? score : 0,
                        ms_played: ms
                    });
                } catch (e) {
                    console.error('[play_event tracking] non-fatal:', e.message);
                }
            })();
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
        console.error("Error updating leaderboard:", error);
        return res.status(500).json({ 
            success: false,
            error: "Internal server error",
            message: error.message 
        });
    }
});

// Delete leaderboard entry
router.delete('/puzzle/:puzzleId/user/:userId', async (req, res) => {
    try {
        const { puzzleId, userId } = req.params;

        if (!puzzleId || !userId) {
            return res.status(400).json({ 
                success: false,
                error: "Missing required parameters: puzzleId, userId" 
            });
        }

        console.log(`Deleting leaderboard entry for ${userId} in ${puzzleId}`);

        // Check if entry exists
        const { data: existingEntry, error: checkError } = await supabase
            .from('custom_leaderboard')
            .select('*')
            .eq('parentsetid', puzzleId)
            .eq('userid', userId)
            .limit(1);

        if (checkError) {
            console.error("Error checking existing entry:", checkError);
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

        // Delete the entry
        const { error: deleteError } = await supabase
            .from('custom_leaderboard')
            .delete()
            .eq('parentsetid', puzzleId)
            .eq('userid', userId);

        if (deleteError) {
            console.error("Error deleting entry:", deleteError);
            return res.status(500).json({ 
                success: false,
                error: "Failed to delete leaderboard entry" 
            });
        }

        // Get updated leaderboard
        const updatedLeaderboard = await getPuzzleLeaderboard(puzzleId);

        return res.json({ 
            success: true,
            message: "Leaderboard entry deleted successfully", 
            deletedEntry: {
                userId: deletedEntry.userid,
                score: deletedEntry.score,
                timeTaken: deletedEntry.timetaken,
                formattedTime: formatTime(deletedEntry.timetaken)
            },
            updatedLeaderboard: updatedLeaderboard.success ? {
                topPlayers: updatedLeaderboard.topPlayers,
                totalPlayers: updatedLeaderboard.totalPlayers,
                statistics: updatedLeaderboard.statistics
            } : null
        });

    } catch (error) {
        console.error("Error deleting leaderboard entry:", error);
        return res.status(500).json({ 
            success: false,
            error: "Internal server error",
            message: error.message 
        });
    }
});

// Get featured puzzles
router.get('/featured', async (req, res) => {
    try {
        const { limit = 5 } = req.query;

        console.log(`Fetching featured puzzles`);

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
        console.error("Error fetching featured puzzles:", error);
        return res.status(500).json({ 
            success: false,
            error: "Internal server error",
            message: error.message 
        });
    }
});

// Helper function to format time
function formatTime(timeInMs) {
    if (!timeInMs || timeInMs < 0) return "0s";
    
    const seconds = Math.floor(timeInMs / 1000);
    const minutes = Math.floor(seconds / 60);
    const remainingSeconds = seconds % 60;
    
    if (minutes > 0) {
        return `${minutes}:${remainingSeconds.toString().padStart(2, '0')}`;
    } else {
        return `${remainingSeconds}s`;
    }
}

export default router;