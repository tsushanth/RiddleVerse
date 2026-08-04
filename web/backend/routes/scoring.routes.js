// routes/scoring.routes.js - Score and leaderboard endpoints
import express from 'express';
import { supabase } from '../config/database.js';

const router = express.Router();

/**
 * Update user score
 * Migrated off Firestore 2026-08-04 — see leaderboard.routes.js for why.
 * Same `leaderboard` table, this route duplicates that one under a
 * different path prefix for backward compat with older clients.
 */
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
                ? `✅ Score added. Previous: ${previousScore}, Added: ${score}, New total: ${newTotalScore}`
                : `🏁 New score recorded: ${score}`,
            previousScore: previousScore,
            addedScore: score,
            newTotal: newTotalScore
        });
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

export default router;
