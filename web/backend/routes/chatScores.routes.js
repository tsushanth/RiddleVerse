import express from 'express';
import { supabase } from '../config/database.js';

const router = express.Router();

// Optional webhook URL for score notifications (e.g., Telegram bot)
const SCORE_WEBHOOK_URL = process.env.SCORE_WEBHOOK_URL || '';

// POST /api/chat-scores
// Body: { gameId, chatId, platform, userId, username, score, avatarUrl? }
// Upserts score (keep highest per userId+gameId+chatId+platform)
// Returns: { success: true, leaderboard: [...top 10] }
router.post('/', async (req, res) => {
    try {
        const { gameId, chatId, platform = 'telegram', userId, username, score, avatarUrl } = req.body;

        if (!gameId || !chatId || !userId || score === undefined) {
            return res.status(400).json({ error: 'gameId, chatId, userId, and score are required' });
        }

        const parsedScore = parseInt(score);
        if (isNaN(parsedScore)) {
            return res.status(400).json({ error: 'score must be a number' });
        }

        // Check if entry already exists for this user+game+chat+platform
        const { data: existing, error: fetchError } = await supabase
            .from('chat_scores')
            .select('id, score')
            .eq('game_id', gameId)
            .eq('chat_id', chatId)
            .eq('platform', platform)
            .eq('user_id', userId)
            .single();

        if (fetchError && fetchError.code !== 'PGRST116') {
            // PGRST116 = no rows found, which is fine
            throw fetchError;
        }

        // Only update if new score is higher or no existing entry
        if (existing) {
            if (parsedScore > existing.score) {
                const { error: updateError } = await supabase
                    .from('chat_scores')
                    .update({
                        score: parsedScore,
                        username: username || null,
                        avatar_url: avatarUrl || null,
                        updated_at: new Date().toISOString()
                    })
                    .eq('id', existing.id);

                if (updateError) throw updateError;
                console.log(`[chat-scores] Updated score: ${userId} in ${chatId} (${platform}) - ${existing.score} -> ${parsedScore}`);
            }
        } else {
            // Insert new entry
            const { error: insertError } = await supabase
                .from('chat_scores')
                .insert({
                    game_id: gameId,
                    chat_id: chatId,
                    platform,
                    user_id: userId,
                    username: username || null,
                    score: parsedScore,
                    avatar_url: avatarUrl || null
                });

            if (insertError) throw insertError;
            console.log(`[chat-scores] New score: ${userId} in ${chatId} (${platform}) - ${parsedScore}`);
        }

        // Fetch updated leaderboard (top 10)
        const { data: leaderboard, error: lbError } = await supabase
            .from('chat_scores')
            .select('user_id, username, score, avatar_url, updated_at')
            .eq('game_id', gameId)
            .eq('chat_id', chatId)
            .eq('platform', platform)
            .order('score', { ascending: false })
            .limit(10);

        if (lbError) throw lbError;

        // Fire-and-forget webhook notification
        if (SCORE_WEBHOOK_URL) {
            // Get game name for the notification
            let gameName = 'Game';
            try {
                const { data: game } = await supabase
                    .from('custom_games')
                    .select('title')
                    .eq('id', gameId)
                    .single();
                if (game?.title) gameName = game.title;
            } catch {}

            fetch(`${SCORE_WEBHOOK_URL}/score-update`, {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({
                    gameId,
                    chatId,
                    platform,
                    leaderboard: leaderboard.map(s => ({
                        userId: s.user_id,
                        username: s.username,
                        score: s.score,
                        avatarUrl: s.avatar_url
                    })),
                    gameName
                })
            }).catch(err => console.warn('[chat-scores] Webhook failed:', err.message));
        }

        res.json({
            success: true,
            leaderboard: leaderboard.map(s => ({
                userId: s.user_id,
                username: s.username,
                score: s.score,
                avatarUrl: s.avatar_url,
                updatedAt: s.updated_at
            }))
        });

    } catch (error) {
        console.error('[chat-scores] POST error:', error.message);
        res.status(500).json({ error: 'Failed to submit score' });
    }
});

// GET /api/chat-scores/:gameId/:chatId
// Optional query: ?platform=telegram (defaults to telegram)
// Returns: { scores: [...top 10 sorted by score desc] }
router.get('/:gameId/:chatId', async (req, res) => {
    try {
        const { gameId, chatId } = req.params;
        const { platform = 'telegram' } = req.query;

        const { data: scores, error } = await supabase
            .from('chat_scores')
            .select('user_id, username, score, avatar_url, updated_at')
            .eq('game_id', gameId)
            .eq('chat_id', chatId)
            .eq('platform', platform)
            .order('score', { ascending: false })
            .limit(10);

        if (error) throw error;

        res.json({
            scores: (scores || []).map(s => ({
                userId: s.user_id,
                username: s.username,
                score: s.score,
                avatarUrl: s.avatar_url,
                updatedAt: s.updated_at
            }))
        });

    } catch (error) {
        console.error('[chat-scores] GET error:', error.message);
        res.status(500).json({ error: 'Failed to fetch scores' });
    }
});

export default router;
