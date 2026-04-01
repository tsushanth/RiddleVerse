/**
 * RiddleVerse Telegram Bot
 * Long polling bot for game creation and play-in-chat features
 */

import express from 'express';
import { createClient } from '@supabase/supabase-js';

// Environment configuration
const TELEGRAM_BOT_TOKEN = process.env.TELEGRAM_BOT_TOKEN;
const RIDDLEVERSE_API_URL = process.env.RIDDLEVERSE_API_URL || 'https://api.riddleverse.com';
const SUPABASE_URL = process.env.SUPABASE_URL;
const SUPABASE_SERVICE_KEY = process.env.SUPABASE_SERVICE_KEY;
const PORT = parseInt(process.env.PORT || '3463');

if (!TELEGRAM_BOT_TOKEN) {
    console.error('TELEGRAM_BOT_TOKEN is required');
    process.exit(1);
}

// Initialize Supabase client (optional, for future features)
let supabase = null;
if (SUPABASE_URL && SUPABASE_SERVICE_KEY) {
    supabase = createClient(SUPABASE_URL, SUPABASE_SERVICE_KEY);
}

// Express app for webhook endpoint
const app = express();
app.use(express.json());

// Session storage (in-memory for MVP)
const sessions = new Map(); // chatId -> { lastGameId }

// Telegram API helpers
const TG_API = `https://api.telegram.org/bot${TELEGRAM_BOT_TOKEN}`;

async function sendMessage(chatId, text, options = {}) {
    try {
        const body = {
            chat_id: chatId,
            text,
            parse_mode: 'HTML',
            ...options
        };

        const res = await fetch(`${TG_API}/sendMessage`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(body)
        });

        const data = await res.json();
        if (!data.ok) {
            console.error('[TG] sendMessage error:', data.description);
        }
        return data;
    } catch (err) {
        console.error('[TG] sendMessage failed:', err.message);
        return null;
    }
}

async function editMessage(chatId, messageId, text, options = {}) {
    try {
        const body = {
            chat_id: chatId,
            message_id: messageId,
            text,
            parse_mode: 'HTML',
            ...options
        };

        const res = await fetch(`${TG_API}/editMessageText`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(body)
        });

        return await res.json();
    } catch (err) {
        console.error('[TG] editMessage failed:', err.message);
        return null;
    }
}

// Build game URL for play button
function getGameUrl(gameId, chatId) {
    return `${RIDDLEVERSE_API_URL}/api/game-creation/${gameId}?chatId=${encodeURIComponent(chatId)}&platform=telegram`;
}

// Format leaderboard text
function formatLeaderboard(scores, gameName = 'Game') {
    const medals = ['🥇', '🥈', '🥉'];
    let text = `🏆 <b>${gameName}</b> — Group Leaderboard\n\n`;

    if (!scores || scores.length === 0) {
        text += '<i>No scores yet. Be the first to play!</i>';
        return text;
    }

    scores.slice(0, 10).forEach((s, i) => {
        const medal = i < 3 ? medals[i] : `${i + 1}.`;
        const name = s.username || 'Player';
        text += `${medal} ${name}  <b>${s.score}</b>pts\n`;
    });

    return text;
}

// Handle /start command
async function handleStart(chatId, from) {
    const name = from.first_name || 'there';
    const text = `🧩 Welcome to RiddleVerse, ${name}!

Build and play games with friends right here in chat.

<b>Commands:</b>
/create [description] - Build a custom game
/play [gameId] - Play an existing game
/leaderboard [gameId] - View group scores
/help - Show this message

<b>Example:</b>
<code>/create a word guessing game where you guess countries from flags</code>`;

    await sendMessage(chatId, text);
}

// Handle /help command
async function handleHelp(chatId) {
    const text = `🧩 <b>RiddleVerse Bot Commands</b>

/create [description]
Build a custom game from your description. Works in groups and DMs.

/play [gameId]
Play an existing game. Scores are tracked per-group!

/leaderboard [gameId]
View the leaderboard for a game in this chat.

<b>Tips:</b>
• Be specific in your game descriptions
• Games work best with touch controls
• Group scores are separate from other chats`;

    await sendMessage(chatId, text);
}

// Handle /create command or natural "create/build" messages
async function handleCreate(chatId, from, description) {
    if (!description || description.trim().length < 5) {
        await sendMessage(chatId, '❓ Please provide a game description.\n\n<b>Example:</b>\n<code>/create a snake game where you eat apples and grow longer</code>');
        return;
    }

    // Send initial "building" message
    const buildingMsg = await sendMessage(chatId, '🔨 Building your game...\n\n<i>This may take 1-2 minutes.</i>');
    const msgId = buildingMsg?.result?.message_id;

    try {
        // Call generate endpoint (non-streaming for simplicity)
        const userId = `tg-${from.id}`;
        const userName = from.first_name || 'Telegram User';

        const genRes = await fetch(`${RIDDLEVERSE_API_URL}/api/game-creation/generate`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({
                prompt: description.trim(),
                userId,
                userName,
                title: description.trim().substring(0, 50),
                stream: false
            }),
            signal: AbortSignal.timeout(300000) // 5 min timeout
        });

        if (!genRes.ok) {
            const errBody = await genRes.json().catch(() => ({}));
            throw new Error(errBody.error || `Generation failed (${genRes.status})`);
        }

        // For SSE response, we need to parse differently
        const contentType = genRes.headers.get('content-type') || '';

        let gameId = null;
        let gameTitle = description.trim().substring(0, 50);

        if (contentType.includes('text/event-stream')) {
            // Parse SSE stream
            const text = await genRes.text();
            const lines = text.split('\n');

            for (const line of lines) {
                if (line.startsWith('data: ')) {
                    try {
                        const event = JSON.parse(line.substring(6));
                        if (event.type === 'result' && event.gameId) {
                            gameId = event.gameId;
                            break;
                        } else if (event.type === 'error') {
                            throw new Error(event.error);
                        }
                    } catch {}
                }
            }
        } else {
            // JSON response
            const data = await genRes.json();
            if (data.gameId) {
                gameId = data.gameId;
            } else if (data.error) {
                throw new Error(data.error);
            }
        }

        if (!gameId) {
            throw new Error('No gameId in response');
        }

        // Store last game for this chat
        sessions.set(String(chatId), { lastGameId: gameId });

        // Update message with play button
        const gameUrl = getGameUrl(gameId, chatId);
        const successText = `✅ <b>Game Ready!</b>

🎮 <b>${gameTitle}</b>

Tap below to play. Scores will be tracked in this chat!`;

        if (msgId) {
            await editMessage(chatId, msgId, successText, {
                reply_markup: {
                    inline_keyboard: [[
                        { text: '▶️ Play Now', web_app: { url: gameUrl } }
                    ]]
                }
            });
        } else {
            await sendMessage(chatId, successText, {
                reply_markup: {
                    inline_keyboard: [[
                        { text: '▶️ Play Now', web_app: { url: gameUrl } }
                    ]]
                }
            });
        }

        console.log(`[create] Success: gameId=${gameId} chat=${chatId}`);

    } catch (err) {
        console.error('[create] Error:', err.message);

        const errorText = `❌ <b>Game creation failed</b>\n\n${err.message}\n\nPlease try a simpler description.`;

        if (msgId) {
            await editMessage(chatId, msgId, errorText);
        } else {
            await sendMessage(chatId, errorText);
        }
    }
}

// Handle /play command
async function handlePlay(chatId, gameIdArg) {
    // Use provided gameId or last game from session
    const session = sessions.get(String(chatId)) || {};
    const gameId = gameIdArg?.trim() || session.lastGameId;

    if (!gameId) {
        await sendMessage(chatId, '❓ Please provide a game ID.\n\n<b>Usage:</b>\n<code>/play abc123</code>');
        return;
    }

    const gameUrl = getGameUrl(gameId, chatId);

    await sendMessage(chatId, '🎮 <b>Ready to play!</b>\n\nTap below to start:', {
        reply_markup: {
            inline_keyboard: [[
                { text: '▶️ Play Now', web_app: { url: gameUrl } }
            ]]
        }
    });
}

// Handle /leaderboard command
async function handleLeaderboard(chatId, gameIdArg) {
    const session = sessions.get(String(chatId)) || {};
    const gameId = gameIdArg?.trim() || session.lastGameId;

    if (!gameId) {
        await sendMessage(chatId, '❓ Please provide a game ID.\n\n<b>Usage:</b>\n<code>/leaderboard abc123</code>');
        return;
    }

    try {
        // Fetch leaderboard
        const res = await fetch(`${RIDDLEVERSE_API_URL}/api/chat-scores/${gameId}/${encodeURIComponent(chatId)}?platform=telegram`);

        if (!res.ok) {
            throw new Error(`HTTP ${res.status}`);
        }

        const data = await res.json();
        const text = formatLeaderboard(data.scores || [], 'Game');

        const gameUrl = getGameUrl(gameId, chatId);

        await sendMessage(chatId, text, {
            reply_markup: {
                inline_keyboard: [[
                    { text: '▶️ Play Again', web_app: { url: gameUrl } }
                ]]
            }
        });

    } catch (err) {
        console.error('[leaderboard] Error:', err.message);
        await sendMessage(chatId, '❌ Could not fetch leaderboard. Please try again.');
    }
}

// Process incoming update
async function processUpdate(update) {
    const message = update.message;
    if (!message) return;

    const chatId = message.chat.id;
    const from = message.from || {};
    const text = (message.text || '').trim();

    console.log(`[update] chat=${chatId} from=${from.id} text="${text.substring(0, 50)}"`);

    // Command handling
    if (text.startsWith('/start')) {
        await handleStart(chatId, from);
    } else if (text.startsWith('/help')) {
        await handleHelp(chatId);
    } else if (text.startsWith('/create')) {
        const description = text.replace(/^\/create\s*/i, '');
        await handleCreate(chatId, from, description);
    } else if (text.startsWith('/play')) {
        const gameId = text.replace(/^\/play\s*/i, '');
        await handlePlay(chatId, gameId);
    } else if (text.startsWith('/leaderboard')) {
        const gameId = text.replace(/^\/leaderboard\s*/i, '');
        await handleLeaderboard(chatId, gameId);
    } else if (/^(build|create)\s+/i.test(text)) {
        // Natural language create
        const description = text.replace(/^(build|create)\s+/i, '');
        await handleCreate(chatId, from, description);
    }
}

// Long polling loop
let pollingOffset = 0;

async function poll() {
    try {
        const res = await fetch(`${TG_API}/getUpdates?offset=${pollingOffset}&timeout=30`);
        const data = await res.json();

        if (!data.ok) {
            console.error('[poll] Error:', data.description);
            await new Promise(r => setTimeout(r, 5000));
            return;
        }

        const updates = data.result || [];

        for (const update of updates) {
            pollingOffset = update.update_id + 1;
            try {
                await processUpdate(update);
            } catch (err) {
                console.error('[poll] Update processing error:', err.message);
            }
        }

    } catch (err) {
        console.error('[poll] Network error:', err.message);
        await new Promise(r => setTimeout(r, 5000));
    }
}

async function startPolling() {
    console.log('[bot] Starting long polling...');
    while (true) {
        await poll();
    }
}

// Score webhook endpoint (called by backend when scores are submitted)
app.post('/score-update', async (req, res) => {
    try {
        const { gameId, chatId, platform, leaderboard, gameName } = req.body;

        if (!chatId || !leaderboard) {
            return res.status(400).json({ error: 'chatId and leaderboard required' });
        }

        // Format and send leaderboard update
        const text = formatLeaderboard(leaderboard, gameName || 'Game');
        const gameUrl = getGameUrl(gameId, chatId);

        await sendMessage(chatId, text, {
            reply_markup: {
                inline_keyboard: [[
                    { text: '▶️ Play Again', web_app: { url: gameUrl } }
                ]]
            }
        });

        res.json({ success: true });

    } catch (err) {
        console.error('[score-update] Error:', err.message);
        res.status(500).json({ error: 'Failed to send update' });
    }
});

// Health check
app.get('/health', (req, res) => {
    res.json({ status: 'ok', service: 'riddleverse-telegram-bot' });
});

// Start server and polling
app.listen(PORT, () => {
    console.log(`[bot] Webhook server listening on port ${PORT}`);
    console.log(`[bot] API URL: ${RIDDLEVERSE_API_URL}`);

    // Start long polling in background
    startPolling().catch(err => {
        console.error('[bot] Polling crashed:', err.message);
        process.exit(1);
    });
});
