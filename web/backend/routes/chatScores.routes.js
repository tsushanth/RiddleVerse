import express from 'express';
import { supabase } from '../config/database.js';
import { createTelegramApi, escapeHtml } from '../services/telegram/telegramApi.js';
import { createLimiter } from '../services/puzzleMonitoring.js';
import { publicUserId, publicName } from '../utils/publicIdentity.js';

const router = express.Router();

const TELEGRAM_BOT_TOKEN = process.env.RIDDLEVERSE_BOT_TOKEN || process.env.TELEGRAM_BOT_TOKEN || '';
const MEDALS = ['🥇', '🥈', '🥉'];
let tgApi = null;
// This endpoint is unauthenticated (the generated games call it from the page), so cap what it can make the bot say per chat.
const notifyLimiter = createLimiter();

// Leaderboard post for AI-generated games played in a chat. HTML parse mode with every dynamic value escaped: the old
// MarkdownV2 text left the game title unescaped, so any title with "." or "-" made Telegram reject the message.
async function sendTelegramLeaderboard(chatId, gameName, leaderboard) {
    if (!TELEGRAM_BOT_TOKEN || !chatId) return;
    if (!/^-?\d{1,20}$/.test(String(chatId)) || !notifyLimiter.take(`chat:${chatId}`, 1, 3, 60e3)) return;
    try {
        if (!tgApi) tgApi = createTelegramApi({ token: TELEGRAM_BOT_TOKEN });
        const lines = [`🏆 <b>${escapeHtml(gameName)}</b>`, ''];
        leaderboard.forEach((s, i) => {
            lines.push(`${MEDALS[i] || `${i + 1}.`} ${escapeHtml(s.username || 'Player')} - <b>${Number(s.score) | 0}</b>`);
        });
        await tgApi.sendMessage(chatId, lines.join('\n'));
    } catch (err) {
        console.warn('[chat-scores] Telegram notify failed:', err.message);
    }
}

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

        // Fire-and-forget: send leaderboard to Telegram chat
        if (platform === 'telegram' && chatId) {
            let gameName = 'Game';
            try {
                const { data: game } = await supabase
                    .from('custom_games')
                    .select('title')
                    .eq('id', gameId)
                    .single();
                if (game?.title) gameName = game.title.split('\n')[0].trim().slice(0, 50);
            } catch {}
            sendTelegramLeaderboard(chatId, gameName, leaderboard.map(s => ({
                username: publicName(s.username),
                score: s.score
            })));
        }

        res.json({
            success: true,
            leaderboard: leaderboard.map(s => ({
                userId: publicUserId(s.user_id),
                username: publicName(s.username),
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
// Optional query: ?platform=telegram|app
// telegram: scoped to chatId — group leaderboard
// app: global leaderboard across all players for this game
router.get('/:gameId/:chatId', async (req, res) => {
    try {
        const { gameId, chatId } = req.params;
        const { platform = 'telegram' } = req.query;

        let query = supabase
            .from('chat_scores')
            .select('user_id, username, score, avatar_url, updated_at')
            .eq('game_id', gameId)
            .order('score', { ascending: false })
            .limit(10);

        if (platform === 'app') {
            // Global leaderboard — all platforms, all chats
            query = query.eq('platform', 'app');
        } else {
            // Chat-scoped leaderboard
            query = query.eq('chat_id', chatId).eq('platform', platform);
        }

        const { data: scores, error } = await query;

        if (error) throw error;

        res.json({
            scores: (scores || []).map(s => ({
                userId: publicUserId(s.user_id),
                username: publicName(s.username),
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
