// routes/scoring.routes.js - Score and leaderboard endpoints
import express from 'express';
import { db } from '../config/firebaseAdmin.js';

const router = express.Router();

/**
 * Update user score
 */
router.post('/update-score', async (req, res) => {
    const { userId, name, score } = req.body;

    if (!userId || !name || typeof score !== 'number') {
        return res.status(400).json({ error: 'Invalid request' });
    }

    try {
        const userRef = db.collection('leaderboard').doc(userId);
        const userDoc = await userRef.get();

        if (userDoc.exists) {
            const existingData = userDoc.data();
            const previousScore = existingData?.score || 0;
            const newTotalScore = previousScore + score;

            await userRef.update({
                name,
                score: newTotalScore,
                lastUpdated: new Date()
            });
            res.json({
                message: `✅ Score added. Previous: ${previousScore}, Added: ${score}, New total: ${newTotalScore}`,
                previousScore: previousScore,
                addedScore: score,
                newTotal: newTotalScore
            });
        } else {
            await userRef.set({
                name,
                score,
                lastUpdated: new Date()
            });
            res.json({
                message: `🏁 New score recorded: ${score}`,
                previousScore: 0,
                addedScore: score,
                newTotal: score
            });
        }
    } catch (error) {
        console.error('❌ Error updating score:', error);
        res.status(500).json({ error: 'Failed to update score' });
    }
});

/**
 * Get user score
 */
router.get('/get-score', async (req, res) => {
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
        console.error('❌ Error fetching score:', error);
        res.status(500).json({ error: 'Failed to retrieve score' });
    }
});

/**
 * Delete user score
 */
router.post('/delete-score', async (req, res) => {
    const { name } = req.body;

    if (!name) {
        return res.status(400).json({ error: "Missing required parameter: name" });
    }

    try {
        const leaderboardRef = db.collection('leaderboard');
        const querySnapshot = await leaderboardRef.where('name', '==', name).get();

        if (querySnapshot.empty) {
            return res.status(404).json({ error: "User not found in leaderboard." });
        }

        querySnapshot.forEach(async (doc) => {
            await doc.ref.delete();
        });

        res.json({ message: `✅ User ${name} removed from leaderboard.` });

    } catch (error) {
        console.error("❌ Error deleting user from leaderboard:", error);
        res.status(500).json({ error: "Failed to remove user from leaderboard." });
    }
});

/**
 * Get leaderboard
 */
router.get('/leaderboard', async (req, res) => {
    try {
        const snapshot = await db.collection('leaderboard')
            .orderBy('score', 'desc')
            .limit(10)
            .get();

        const leaderboard = snapshot.docs.map(doc => doc.data());
        res.json({ success: true, leaderboard });
    } catch (error) {
        console.error('Error fetching leaderboard:', error);
        res.status(500).json({ error: 'Failed to fetch leaderboard' });
    }
});

export default router;
