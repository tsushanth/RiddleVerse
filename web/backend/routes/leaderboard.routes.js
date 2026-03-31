import express from 'express';
import {
    getPuzzleLeaderboard,
    updatePuzzleLeaderboard,
    getFeaturedPuzzles
} from '../services/puzzleService.js';
import { db } from '../config/firebaseAdmin.js';
import { supabase } from '../config/database.js';

const router = express.Router();

// Get global leaderboard
router.get('/', async (req, res) => {
    try {
        const snapshot = await db.collection('leaderboard')
            .orderBy('score', 'desc')
            .limit(10)
            .get();

        const leaderboard = snapshot.docs.map(doc => doc.data());
        res.json({success: true, leaderboard});
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
        const userRef = db.collection('leaderboard').doc(userId);
        const userDoc = await userRef.get();

        if (!userDoc.exists) {
            return res.status(404).json({ message: 'User not found in leaderboard' });
        }

        const userData = userDoc.data();
        res.json({
            userId: userId,
            name: userData.name,
            score: userData.score,
            lastUpdated: userData.lastUpdated
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
        const userRef = db.collection('leaderboard').doc(userId);
        const userDoc = await userRef.get();

        if (userDoc.exists) {
            // User exists, add to existing score
            const existingData = userDoc.data();
            const previousScore = existingData?.score || 0;
            const newTotalScore = previousScore + score;

            await userRef.update({
                name,
                score: newTotalScore,
                lastUpdated: new Date()
            });
            res.json({ 
                message: `Score added. Previous: ${previousScore}, Added: ${score}, New total: ${newTotalScore}`,
                previousScore: previousScore,
                addedScore: score,
                newTotal: newTotalScore
            });
        } else {
            // First time entry, create new record with submitted score
            await userRef.set({
                name,
                score,
                lastUpdated: new Date()
            });
            res.json({ 
                message: `New score recorded: ${score}`,
                previousScore: 0,
                addedScore: score,
                newTotal: score
            });
        }
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
        const leaderboardRef = db.collection('leaderboard');
        
        // Find the document where `name` matches
        const querySnapshot = await leaderboardRef.where('name', '==', name).get();

        if (querySnapshot.empty) {
            return res.status(404).json({ error: "User not found in leaderboard." });
        }

        // Delete all documents matching the name
        querySnapshot.forEach(async (doc) => {
            await doc.ref.delete();
        });

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
        const { userId, timeTaken, score } = req.body;

        if (!userId || timeTaken === undefined || score === undefined) {
            return res.status(400).json({ 
                success: false,
                error: "Missing required parameters: userId, timeTaken, score" 
            });
        }

        // Validate puzzleId format (riddle-timestamp or UUID for AI games)
        const validPuzzleIdPattern = /^(riddle-\d+|[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12})$/i;
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