import express from 'express';
import crypto from 'crypto';
import zlib from 'zlib';
import { supabase } from '../config/database.js';
import { callAI } from '../utils/aiClient.js';
import { USAGE_CATEGORIES } from '../utils/usageTracker.js';
import { DIFFICULTY_COSTS, MAX_DIFFICULTY_LEVEL, getDifficultyCost } from '../services/difficultyService.js';

const router = express.Router();

const API_BASE_URL = process.env.API_BASE_URL || '';

const GAME_WORKER_URL = process.env.GAME_WORKER_URL || 'http://localhost:3456';
const GAME_WORKER_SECRET = process.env.GAME_WORKER_SECRET || 'game-worker-secret-2024';
const GAME_BUNDLES_BUCKET = 'game-bundles';
const SCREENSHOT_SERVICE_URL = process.env.SCREENSHOT_SERVICE_URL || 'http://178.156.231.255:3465';
const PLAY_BASE_URL = process.env.PLAY_BASE_URL || 'https://quiz-web-frontend-917362189743.us-central1.run.app';

// ============================================
// Auto-thumbnail: screenshot game after save
// ============================================
async function captureGameThumbnail(gameId) {
    try {
        const playUrl = `${PLAY_BASE_URL}/play/${gameId}`;
        console.log(`[thumbnail] Capturing: ${playUrl}`);

        const res = await fetch(
            `${SCREENSHOT_SERVICE_URL}/screenshot?url=${encodeURIComponent(playUrl)}&width=390&height=700`,
            { signal: AbortSignal.timeout(45000) }
        );
        if (!res.ok) throw new Error(`Screenshot service returned ${res.status}`);

        const buffer = Buffer.from(await res.arrayBuffer());
        const filename = `thumbnails/${gameId}.jpg`;

        const { error: uploadError } = await supabase.storage
            .from('game-bundles')
            .upload(filename, buffer, { contentType: 'image/jpeg', upsert: true });

        if (uploadError) throw uploadError;

        const { data: urlData } = supabase.storage
            .from('game-bundles')
            .getPublicUrl(filename);

        const thumbnailUrl = urlData.publicUrl;

        await supabase
            .from('custom_games')
            .update({ initial_screenshot_url: thumbnailUrl })
            .eq('id', gameId);

        console.log(`[thumbnail] Saved for ${gameId}: ${thumbnailUrl}`);
        return thumbnailUrl;
    } catch (err) {
        console.error(`[thumbnail] Failed for ${gameId}:`, err.message);
        return null;
    }
}

// Rate limit tracking (in-memory)
const generationLimits = new Map();
const RATE_LIMIT_WINDOW = 60 * 60 * 1000;
const MAX_GENERATIONS_PER_HOUR = 5;
const GENERATION_COIN_COST = 10; // coins to bypass rate limit
const FREE_LIFETIME_GENERATIONS = 5; // hard paywall after this many free generations

// Check if user has an active subscription
async function isSubscribedUser(userId) {
    if (!userId) return false;
    try {
        const { data } = await supabase
            .from('users')
            .select('subscription_tier')
            .eq('user_id', userId)
            .single();
        return data && data.subscription_tier && data.subscription_tier !== 'free';
    } catch {
        return false;
    }
}

// Get user's lifetime generation count
async function getLifetimeGenerations(userId) {
    if (!userId) return 999;
    try {
        const { data } = await supabase
            .from('users')
            .select('total_count')
            .eq('user_id', userId)
            .single();
        return data?.total_count || 0;
    } catch {
        return 0;
    }
}

// ============================================
// Browse Cache — read-only in-memory cache
// Updated periodically from DB. Writes (save) happen separately.
// ============================================

const CACHE_REFRESH_INTERVAL = 60 * 1000; // 60 seconds
let browseCache = {
    newest: [],   // sorted by created_at desc
    popular: [],  // sorted by play_count desc
    totalCount: 0,
    lastRefreshed: 0,
    isRefreshing: false,
};

// Track whether the status column exists (auto-detected on first cache refresh)
let statusColumnExists = null;

async function checkStatusColumn() {
    if (statusColumnExists !== null) return statusColumnExists;
    try {
        const { error } = await supabase
            .from('custom_games')
            .select('status')
            .limit(1);
        statusColumnExists = !error;
        if (!statusColumnExists) {
            console.warn('[cache] status column not found — run migration: web/backend/migrations/add_game_status.sql');
        } else {
            console.log('[cache] status column detected — filtering by published status');
        }
    } catch {
        statusColumnExists = false;
    }
    return statusColumnExists;
}

async function refreshBrowseCache() {
    if (browseCache.isRefreshing) return;
    browseCache.isRefreshing = true;
    try {
        const hasStatus = await checkStatusColumn();

        // Build queries — add status filter only if column exists
        let newestQuery = supabase
            .from('custom_games')
            .select('id, title, description, creator_id, creator_name, game_type, play_count, rating, created_at, initial_screenshot_url', { count: 'exact' })
            .eq('game_type', 'ai_generated')
            .eq('platform_type', 'webview');

        let popularQuery = supabase
            .from('custom_games')
            .select('id, title, description, creator_id, creator_name, game_type, play_count, rating, created_at, initial_screenshot_url')
            .eq('game_type', 'ai_generated')
            .eq('platform_type', 'webview');

        if (hasStatus) {
            newestQuery = newestQuery.eq('status', 'published');
            popularQuery = popularQuery.eq('status', 'published');
        }

        const [newestResult, popularResult] = await Promise.all([
            newestQuery.order('created_at', { ascending: false }).limit(200),
            popularQuery.order('play_count', { ascending: false }).limit(200),
        ]);

        if (newestResult.error) throw newestResult.error;
        if (popularResult.error) throw popularResult.error;

        // Deduplicate by (creator_id, title-prefix) — keep newest entry per creator+prompt pair
        // Titles may be truncated at different lengths by different clients, so match on first 50 chars
        const dedup = (rows) => {
            const seen = new Map();
            for (const row of rows) {
                const key = `${row.creator_id}::${(row.title || '').slice(0, 50)}`;
                if (!seen.has(key)) seen.set(key, row);
            }
            return Array.from(seen.values());
        };
        browseCache.newest = dedup(newestResult.data || []);
        browseCache.popular = dedup(popularResult.data || []);
        browseCache.totalCount = newestResult.count || browseCache.newest.length;
        browseCache.lastRefreshed = Date.now();

        console.log(`[cache] Browse cache refreshed: ${browseCache.totalCount} games`);
    } catch (error) {
        console.error('[cache] Refresh error:', error.message);
    } finally {
        browseCache.isRefreshing = false;
    }
}

// Initial cache load + periodic refresh
refreshBrowseCache();
setInterval(refreshBrowseCache, CACHE_REFRESH_INTERVAL);

// ============================================
// Suggestions Cache — DB-backed, in-memory cache of active game ideas
// Populated from game_suggestions table. Refreshed periodically and after replenishment.
// ============================================

const SUGGESTIONS_CACHE_REFRESH_INTERVAL = 5 * 60 * 1000; // 5 minutes
let suggestionsCache = {
    items: [],         // all active suggestions [{label, prompt}]
    lastRefreshed: 0,
    isRefreshing: false,
};

async function refreshSuggestionsCache() {
    if (suggestionsCache.isRefreshing) return;
    suggestionsCache.isRefreshing = true;
    try {
        const { data, error } = await supabase
            .from('game_suggestions')
            .select('label, prompt')
            .eq('status', 'active')
            .order('created_at', { ascending: false });

        if (error) throw error;

        suggestionsCache.items = data || [];
        suggestionsCache.lastRefreshed = Date.now();
        console.log(`[suggestions-cache] Refreshed: ${suggestionsCache.items.length} active suggestions`);
    } catch (error) {
        console.error('[suggestions-cache] Refresh error:', error.message);
    } finally {
        suggestionsCache.isRefreshing = false;
    }
}

function getRandomSuggestions(count = 6) {
    const pool = suggestionsCache.items;
    if (pool.length === 0) return [];
    const shuffled = [...pool].sort(() => Math.random() - 0.5);
    return shuffled.slice(0, Math.min(count, pool.length));
}

// Initial load + periodic refresh
refreshSuggestionsCache();
setInterval(refreshSuggestionsCache, SUGGESTIONS_CACHE_REFRESH_INTERVAL);

// ============================================
// Suggestion Lifecycle — mark used + AI replenishment
// ============================================

async function markSuggestionAsUsed(prompt) {
    try {
        const { data, error } = await supabase
            .from('game_suggestions')
            .update({ status: 'used', used_at: new Date().toISOString() })
            .eq('status', 'active')
            .eq('prompt', prompt)
            .select('id, label');

        if (error) {
            console.error('[suggestions] Error marking used:', error.message);
            return null;
        }

        if (data && data.length > 0) {
            console.log(`[suggestions] Marked as used: "${data[0].label}"`);
            return data[0];
        }

        return null; // user-typed prompt, no matching suggestion
    } catch (error) {
        console.error('[suggestions] markSuggestionAsUsed error:', error.message);
        return null;
    }
}

async function replenishSuggestions() {
    try {
        // Get all labels (active + used) to avoid regenerating used ones too
        const { data: existingData, error: fetchError } = await supabase
            .from('game_suggestions')
            .select('label');

        if (fetchError) throw fetchError;

        const existingLabels = (existingData || []).map(s => s.label.toLowerCase());

        const prompt = `You are a creative game designer. Generate 6 unique, fun mini-game ideas for a mobile game creation platform. Each game must be buildable as a single-file HTML5 game with touch controls.

Return a JSON array of exactly 6 objects with "label" (2-3 word catchy name) and "prompt" (detailed game description in 2-4 sentences covering: core mechanic, controls, scoring, win/lose condition, and one visual flair detail).

Do NOT suggest any of these existing games: ${existingLabels.join(', ')}

Requirements:
- Games must work on mobile (touch/tap/swipe/drag controls)
- Each game should be a different genre (e.g., puzzle, action, arcade, strategy, word, reflex)
- Descriptions should be specific enough that a developer could build the game from the description alone
- Keep labels short and catchy (2-3 words max)

Respond with ONLY the JSON array, no other text.`;

        const aiResponse = await callAI(prompt, null, 2, {
            category: USAGE_CATEGORIES.SUGGESTION_GENERATION,
            max_tokens: 2000,
            rawResponse: true
        });

        // Parse AI response
        let newSuggestions;
        try {
            let cleaned = aiResponse.trim();
            cleaned = cleaned.replace(/^```(?:json)?\s*/i, '');
            cleaned = cleaned.replace(/\s*```\s*$/i, '');
            newSuggestions = JSON.parse(cleaned);
        } catch (parseError) {
            console.error('[suggestions] Failed to parse AI response:', parseError.message);
            return;
        }

        if (!Array.isArray(newSuggestions) || newSuggestions.length === 0) {
            console.error('[suggestions] AI returned invalid format');
            return;
        }

        let inserted = 0;
        for (const suggestion of newSuggestions) {
            if (!suggestion.label || !suggestion.prompt) continue;

            if (existingLabels.includes(suggestion.label.toLowerCase())) {
                console.log(`[suggestions] Skipping duplicate: "${suggestion.label}"`);
                continue;
            }

            const { error: insertError } = await supabase
                .from('game_suggestions')
                .insert({
                    label: suggestion.label,
                    prompt: suggestion.prompt,
                    status: 'active',
                    source: 'ai_generated'
                });

            if (insertError) {
                if (insertError.code === '23505') {
                    console.log(`[suggestions] DB duplicate skipped: "${suggestion.label}"`);
                } else {
                    console.error(`[suggestions] Insert error for "${suggestion.label}":`, insertError.message);
                }
            } else {
                inserted++;
            }
        }

        console.log(`[suggestions] Replenished: ${inserted} new suggestions added`);

        if (inserted > 0) {
            await refreshSuggestionsCache();
        }
    } catch (error) {
        console.error('[suggestions] Replenishment failed:', error.message);
    }
}

function checkRateLimit(userId) {
    const now = Date.now();
    const key = userId || 'anonymous';
    const userLimits = generationLimits.get(key) || [];
    const recentRequests = userLimits.filter(t => now - t < RATE_LIMIT_WINDOW);
    generationLimits.set(key, recentRequests);
    return recentRequests.length < MAX_GENERATIONS_PER_HOUR;
}

function recordGeneration(userId) {
    const key = userId || 'anonymous';
    const userLimits = generationLimits.get(key) || [];
    userLimits.push(Date.now());
    generationLimits.set(key, userLimits);
}

// POST /api/game-creation/generate
// Streaming endpoint — proxies SSE status events from the worker to the client
// Client sees: status updates ("Building your game", "Fixing issues", "Polishing")
// then the final result with the zip bundle
router.post('/generate', async (req, res) => {
    try {
        const { prompt, userId, userName, title, referenceImage } = req.body;

        if (!prompt || typeof prompt !== 'string' || prompt.trim().length === 0) {
            return res.status(400).json({ error: 'Game description is required' });
        }

        if (prompt.length > 500) {
            return res.status(400).json({ error: 'Description must be under 500 characters' });
        }

        // Subscribers bypass all limits
        const isSubscriber = await isSubscribedUser(userId);

        if (!isSubscriber) {
            // Check lifetime hard paywall first
            const lifetimeCount = await getLifetimeGenerations(userId);
            if (lifetimeCount >= FREE_LIFETIME_GENERATIONS) {
                return res.status(403).json({
                    error: `You've used your ${FREE_LIFETIME_GENERATIONS} free generations. Subscribe to continue creating games!`,
                    requiresSubscription: true,
                    freeTrialAvailable: true,
                    lifetimeUsed: lifetimeCount,
                    lifetimeLimit: FREE_LIFETIME_GENERATIONS
                });
            }
        }

        if (!isSubscriber && !checkRateLimit(userId)) {
            // Allow coin bypass
            if (req.body.useCoins && userId) {
                const { data: spendResult, error: spendError } = await supabase.rpc('spend_coins', {
                    p_user_id: userId,
                    p_amount: GENERATION_COIN_COST,
                    p_reason: 'game_generation',
                    p_game_id: null,
                    p_creator_id: null,
                    p_platform: null
                });
                if (spendError || !spendResult.success) {
                    return res.status(402).json({
                        error: 'Insufficient coins',
                        balance: spendResult?.balance || 0,
                        coinCost: GENERATION_COIN_COST,
                        canUseCoins: true
                    });
                }
                console.log(`[generate] Rate limit bypassed with ${GENERATION_COIN_COST} coins for user ${userId}`);
            } else {
                return res.status(429).json({
                    error: `You've used all ${MAX_GENERATIONS_PER_HOUR} free generations this hour`,
                    canUseCoins: true,
                    coinCost: GENERATION_COIN_COST
                });
            }
        }

        console.log(`[generate] User ${userId}: "${prompt}"${referenceImage ? ' (with reference image)' : ''}`);

        // Set up SSE to stream progress to the client
        res.setHeader('Content-Type', 'text/event-stream');
        res.setHeader('Cache-Control', 'no-cache');
        res.setHeader('Connection', 'keep-alive');
        res.setHeader('X-Accel-Buffering', 'no'); // Disable nginx buffering
        res.flushHeaders();

        // Send keepalive comments every 15s to prevent idle connection timeouts
        const heartbeatInterval = setInterval(() => {
            try { res.write(':heartbeat\n\n'); } catch {}
        }, 15000);
        res.on('close', () => clearInterval(heartbeatInterval));

        // Build worker request body
        const workerBody = { prompt: prompt.trim(), userId, stream: true };
        if (referenceImage && typeof referenceImage === 'string') {
            workerBody.referenceImage = referenceImage;
        }

        // Request streaming from the worker
        const workerResponse = await fetch(`${GAME_WORKER_URL}/generate`, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
                'x-worker-secret': GAME_WORKER_SECRET
            },
            body: JSON.stringify(workerBody),
            signal: AbortSignal.timeout(600000) // 10 min timeout for full iterative build
        });

        if (!workerResponse.ok) {
            let errorMsg = `Build server error (${workerResponse.status})`;
            try {
                if (workerResponse.headers.get('content-type')?.includes('application/json')) {
                    const err = await workerResponse.json();
                    errorMsg = err.error || errorMsg;
                    // Forward quota info if present
                    if (err.quotaExhausted) {
                        res.write(`data: ${JSON.stringify({ type: 'error', error: errorMsg, quotaExhausted: true, resetTime: err.resetTime })}\n\n`);
                        res.end();
                        return;
                    }
                }
            } catch {}
            res.write(`data: ${JSON.stringify({ type: 'error', error: errorMsg })}\n\n`);
            res.end();
            return;
        }

        if (!workerResponse.body) {
            res.write(`data: ${JSON.stringify({ type: 'error', error: 'Build server connection failed' })}\n\n`);
            res.end();
            return;
        }

        // Stream SSE events from worker to client
        const reader = workerResponse.body.getReader();
        const decoder = new TextDecoder();
        let buffer = '';
        let resultReceived = false;

        try {
            while (true) {
                const { done, value } = await reader.read();
                if (done) break;

                buffer += decoder.decode(value, { stream: true });

                // Process complete SSE events from the buffer
                const lines = buffer.split('\n');
                buffer = lines.pop() || ''; // Keep incomplete line in buffer

                for (const line of lines) {
                    if (line.startsWith('data: ')) {
                        const eventData = line.substring(6);
                        try {
                            const parsed = JSON.parse(eventData);

                            if (parsed.type === 'status') {
                                // Forward status to client (including progress fields)
                                res.write(`data: ${JSON.stringify({
                                    type: 'status',
                                    phase: parsed.phase,
                                    message: parsed.message,
                                    detail: parsed.detail,
                                    progressPercent: parsed.progressPercent,
                                    progressEndPct: parsed.progressEndPct,
                                    phaseDurationSeconds: parsed.phaseDurationSeconds,
                                    estimatedSecondsRemaining: parsed.estimatedSecondsRemaining
                                })}\n\n`);
                            } else if (parsed.type === 'result') {
                                // Got the final result — auto-save and forward to client
                                resultReceived = true;
                                recordGeneration(userId);

                                const generationId = `gen-${Date.now()}-${crypto.randomBytes(4).toString('hex')}`;

                                console.log(`[generate] Complete: ${generationId} (${parsed.files?.length} files, ${(parsed.bundleSize / 1024).toFixed(1)}KB, ${parsed.generationTime}s)`);

                                // Auto-save the generated game (unified with remix flow)
                                const gameId = crypto.randomUUID();
                                const gameTitle = (title && title.trim()) || prompt.trim().substring(0, 100);
                                const creatorName = userName || 'Player';
                                const criticalCount = parsed.quality?.criticalIssues ?? 0;
                                const { error: dbError } = await supabase
                                    .from('custom_games')
                                    .insert({
                                        id: gameId,
                                        title: gameTitle,
                                        description: prompt.trim().substring(0, 500),
                                        html_content: parsed.bundle,
                                        creator_id: userId,
                                        creator_name: creatorName,
                                        game_type: 'ai_generated',
                                        platform_type: 'webview',
                                        initial_prompt: prompt.substring(0, 2000),
                                        play_count: 0,
                                        rating: 0,
                                        critical_issues: criticalCount
                                    });

                                if (dbError) {
                                    console.error(`[generate] DB save error:`, dbError.message);
                                } else {
                                    console.log(`[generate] Saved: ${gameId} ("${gameTitle}")`);
                                    refreshBrowseCache();
                                    // Auto-capture thumbnail (fire-and-forget)
                                    captureGameThumbnail(gameId)
                                        .then(() => refreshBrowseCache())
                                        .catch(err => console.error('[thumbnail] Background error:', err.message));
                                }

                                res.write(`data: ${JSON.stringify({
                                    type: 'result',
                                    success: !dbError,
                                    gameId: dbError ? undefined : gameId,
                                    generationId,
                                    bundle: parsed.bundle,
                                    bundleSize: parsed.bundleSize,
                                    files: parsed.files,
                                    generationTime: parsed.generationTime,
                                    quality: parsed.quality
                                })}\n\n`);
                            } else if (parsed.type === 'error') {
                                res.write(`data: ${JSON.stringify({
                                    type: 'error',
                                    error: parsed.error
                                })}\n\n`);
                            }
                        } catch {
                            // Malformed event, skip
                        }
                    }
                }
            }
        } catch (streamError) {
            if (!resultReceived) {
                res.write(`data: ${JSON.stringify({ type: 'error', error: 'Connection to build server lost' })}\n\n`);
            }
        }

        res.end();

    } catch (error) {
        console.error('[generate] Error:', error.message);

        // If headers already sent (SSE mode), send error event
        if (res.headersSent) {
            res.write(`data: ${JSON.stringify({ type: 'error', error: 'Server error. Please try again.' })}\n\n`);
            res.end();
        } else {
            if (error.name === 'TimeoutError') {
                return res.status(504).json({ error: 'Game generation timed out. Try a simpler game idea.' });
            }
            res.status(500).json({ error: 'Failed to generate game. Please try again.' });
        }
    }
});

// POST /api/game-creation/save
// Stores the game bundle (base64 zip) in the DB
// Optionally creates an initial leaderboard entry if score > 0
router.post('/save', async (req, res) => {
    try {
        const { title, bundle, creatorId, creatorName, initialPrompt, score, timePlayed } = req.body;

        if (!title || !bundle || !creatorId) {
            return res.status(400).json({ error: 'title, bundle, and creatorId are required' });
        }

        const bundleSizeKB = (Buffer.from(bundle, 'base64').length / 1024).toFixed(1);

        // Dedup: if creator already published same title in last 5 minutes, return existing
        const fiveMinutesAgo = new Date(Date.now() - 5 * 60 * 1000).toISOString();
        const { data: existing } = await supabase
            .from('custom_games')
            .select('id')
            .eq('creator_id', creatorId)
            .like('title', `${title.slice(0, 50)}%`)
            .gte('created_at', fiveMinutesAgo)
            .limit(1)
            .single();

        if (existing) {
            console.log(`[save] Duplicate save prevented for "${title}" by ${creatorId}, returning existing ${existing.id}`);
            return res.json({ success: true, gameId: existing.id });
        }

        const gameId = crypto.randomUUID();

        const { data, error: dbError } = await supabase
            .from('custom_games')
            .insert({
                id: gameId,
                title,
                description: initialPrompt || `AI-generated game: ${title}`,
                html_content: bundle, // base64 zip stored in existing column
                creator_id: creatorId,
                creator_name: creatorName || 'Anonymous',
                game_type: 'ai_generated',
                platform_type: 'webview',
                play_count: 0,
                rating: 0
            })
            .select()
            .single();

        if (dbError) throw dbError;

        console.log(`[save] Game saved: ${gameId} (${bundleSizeKB}KB)`);

        // Auto-create leaderboard entry if user played and scored
        if (score && parseInt(score) > 0 && creatorId !== 'anonymous') {
            try {
                await supabase
                    .from('custom_leaderboard')
                    .insert({
                        parentsetid: gameId,
                        userid: creatorId,
                        score: parseInt(score),
                        timetaken: parseInt(timePlayed) || 0,
                        createdat: new Date().toISOString()
                    });
                console.log(`[save] Leaderboard entry created for ${gameId}: score=${score}`);
            } catch (lbError) {
                console.warn('[save] Failed to create leaderboard entry:', lbError.message);
            }
        }

        // Refresh browse cache so the new game appears
        refreshBrowseCache();

        // Auto-capture thumbnail (fire-and-forget — does not block response)
        captureGameThumbnail(gameId)
            .then(() => refreshBrowseCache()) // Refresh again after thumbnail is ready
            .catch(err => console.error('[thumbnail] Background capture error:', err.message));

        // Mark suggestion as used and replenish (fire-and-forget, does not block response)
        if (initialPrompt) {
            markSuggestionAsUsed(initialPrompt)
                .then(markedSuggestion => {
                    if (markedSuggestion) {
                        replenishSuggestions();
                    }
                })
                .catch(err => console.error('[suggestions] Background task error:', err.message));
        }

        res.json({
            success: true,
            gameId
        });

    } catch (error) {
        console.error('[save] Error:', error.message);
        res.status(500).json({ error: 'Failed to save game' });
    }
});

// GET /api/game-creation/browse
// Served from in-memory cache — fast, no DB hit per request.
// Supports pagination: ?limit=20&offset=0&sort=newest|popular
router.get('/browse', async (req, res) => {
    try {
        const { limit = 20, offset = 0, sort = 'newest', creatorId } = req.query;
        const parsedLimit = Math.min(parseInt(limit) || 20, 50);
        const parsedOffset = parseInt(offset) || 0;

        // If creatorId provided, query DB directly (includes drafts so creators see their own broken games)
        if (creatorId) {
            const hasStatus = await checkStatusColumn();
            const selectFields = hasStatus
                ? 'id, title, description, creator_id, creator_name, game_type, play_count, rating, created_at, status, initial_screenshot_url'
                : 'id, title, description, creator_id, creator_name, game_type, play_count, rating, created_at, initial_screenshot_url';
            const query = supabase
                .from('custom_games')
                .select(selectFields, { count: 'exact' })
                .eq('game_type', 'ai_generated')
                .eq('platform_type', 'webview')
                .eq('creator_id', creatorId)
                .order(sort === 'popular' ? 'play_count' : 'created_at', { ascending: false })
                .range(parsedOffset, parsedOffset + parsedLimit - 1);

            const { data, count, error } = await query;
            if (error) throw error;

            return res.json({
                success: true,
                games: data || [],
                totalCount: count || 0,
                hasMore: (parsedOffset + parsedLimit) < (count || 0)
            });
        }

        // Default: serve from cache (only published games)
        const source = sort === 'popular' ? browseCache.popular : browseCache.newest;
        const page = source.slice(parsedOffset, parsedOffset + parsedLimit);
        const totalCount = source.length;

        res.json({
            success: true,
            games: page,
            totalCount,
            hasMore: (parsedOffset + parsedLimit) < totalCount
        });
    } catch (error) {
        console.error('[browse] Error:', error.message);
        res.status(500).json({ error: 'Failed to fetch games' });
    }
});

// GET /api/game-creation/suggestions
// Returns random creative game prompts from the pre-generated pool.
// No DB hit — served instantly from memory.
router.get('/suggestions', (req, res) => {
    const count = Math.min(parseInt(req.query.count) || 6, 10);
    res.json({
        success: true,
        suggestions: getRandomSuggestions(count)
    });
});

// ============================================
// Difficulty Escalation Endpoints
// ============================================

// GET /api/game-creation/:gameId/difficulty-info
// Returns which difficulty levels exist and their costs
router.get('/:gameId/difficulty-info', async (req, res) => {
    try {
        const { gameId } = req.params;

        const { data: variants, error } = await supabase
            .from('game_difficulty_variants')
            .select('difficulty_level, status')
            .eq('parent_game_id', gameId);

        if (error) throw error;

        const levels = [];
        for (let level = 2; level <= MAX_DIFFICULTY_LEVEL; level++) {
            const variant = (variants || []).find(v => v.difficulty_level === level);
            levels.push({
                level,
                cost: getDifficultyCost(level),
                status: variant ? variant.status : 'not_generated',
            });
        }

        res.json({ success: true, levels, costs: DIFFICULTY_COSTS });
    } catch (error) {
        console.error('[difficulty-info] Error:', error.message);
        res.status(500).json({ error: 'Failed to fetch difficulty info' });
    }
});

// GET /api/game-creation/:gameId/difficulty/:level
// Fetch a difficulty variant bundle if it exists and is ready
router.get('/:gameId/difficulty/:level', async (req, res) => {
    try {
        const { gameId, level } = req.params;
        const parsedLevel = parseInt(level);

        if (isNaN(parsedLevel) || parsedLevel < 2 || parsedLevel > MAX_DIFFICULTY_LEVEL) {
            return res.status(400).json({ error: 'Invalid difficulty level (must be 2-5)' });
        }

        const { data, error } = await supabase
            .from('game_difficulty_variants')
            .select('id, difficulty_level, html_content, status, play_count')
            .eq('parent_game_id', gameId)
            .eq('difficulty_level', parsedLevel)
            .single();

        if (error && error.code === 'PGRST116') {
            // No row — variant not generated
            return res.json({ success: true, exists: false, generating: false });
        }
        if (error) throw error;

        if (data.status === 'generating') {
            // Check for stale generation (>15 min)
            const age = Date.now() - new Date(data.created_at || 0).getTime();
            if (age > 15 * 60 * 1000) {
                // Stale — mark as failed so it can be retried
                await supabase
                    .from('game_difficulty_variants')
                    .update({ status: 'failed' })
                    .eq('id', data.id);
                return res.json({ success: true, exists: false, generating: false });
            }
            return res.json({ success: true, exists: false, generating: true });
        }

        if (data.status === 'failed') {
            return res.json({ success: true, exists: false, generating: false });
        }

        // status === 'ready' — serve the bundle
        // Increment play count
        await supabase
            .from('game_difficulty_variants')
            .update({ play_count: (data.play_count || 0) + 1 })
            .eq('id', data.id);

        res.json({
            success: true,
            exists: true,
            variant: {
                id: data.id,
                difficultyLevel: data.difficulty_level,
                bundle: data.html_content,
                playCount: data.play_count
            }
        });
    } catch (error) {
        console.error('[difficulty-fetch] Error:', error.message);
        res.status(500).json({ error: 'Failed to fetch difficulty variant' });
    }
});

// POST /api/game-creation/:gameId/difficulty/:level/generate
// Triggers generation of a harder variant via the game worker (SSE streaming)
router.post('/:gameId/difficulty/:level/generate', async (req, res) => {
    try {
        const { gameId, level } = req.params;
        const { userId } = req.body;
        const parsedLevel = parseInt(level);

        if (isNaN(parsedLevel) || parsedLevel < 2 || parsedLevel > MAX_DIFFICULTY_LEVEL) {
            return res.status(400).json({ error: 'Invalid difficulty level (must be 2-5)' });
        }

        // Check if variant already exists or is being generated
        const { data: existing } = await supabase
            .from('game_difficulty_variants')
            .select('id, status')
            .eq('parent_game_id', gameId)
            .eq('difficulty_level', parsedLevel)
            .single();

        if (existing && existing.status === 'ready') {
            return res.status(409).json({ error: 'Variant already exists', exists: true });
        }
        if (existing && existing.status === 'generating') {
            return res.status(409).json({ error: 'Variant is already being generated', generating: true });
        }

        // Fetch parent bundle: level 2 uses original, level 3+ uses previous level
        let parentBundle;
        let gameTitle;
        let repoName = null;

        if (parsedLevel === 2) {
            const { data: game, error: gameError } = await supabase
                .from('custom_games')
                .select('html_content, title, github_repo')
                .eq('id', gameId)
                .single();
            if (gameError || !game) {
                return res.status(404).json({ error: 'Parent game not found' });
            }
            parentBundle = game.html_content;
            gameTitle = game.title;
            repoName = game.github_repo || null;
        } else {
            // Fetch the previous level variant
            const { data: prevVariant, error: prevError } = await supabase
                .from('game_difficulty_variants')
                .select('html_content')
                .eq('parent_game_id', gameId)
                .eq('difficulty_level', parsedLevel - 1)
                .eq('status', 'ready')
                .single();

            if (prevError || !prevVariant) {
                return res.status(400).json({ error: `Level ${parsedLevel - 1} must be generated first` });
            }
            parentBundle = prevVariant.html_content;

            // Also get game title and repo name
            const { data: game } = await supabase
                .from('custom_games')
                .select('title, github_repo')
                .eq('id', gameId)
                .single();
            gameTitle = game?.title || 'Game';
            repoName = game?.github_repo || null;
        }

        // Insert or update the generating row
        if (existing && existing.status === 'failed') {
            await supabase
                .from('game_difficulty_variants')
                .update({ status: 'generating', generated_by: userId || null, html_content: '' })
                .eq('id', existing.id);
        } else {
            const { error: insertError } = await supabase
                .from('game_difficulty_variants')
                .insert({
                    parent_game_id: gameId,
                    difficulty_level: parsedLevel,
                    status: 'generating',
                    generated_by: userId || null,
                    html_content: ''
                });
            if (insertError) {
                if (insertError.code === '23505') {
                    return res.status(409).json({ error: 'Variant generation already in progress' });
                }
                throw insertError;
            }
        }

        console.log(`[difficulty] Generating level ${parsedLevel} for game ${gameId} (repo: ${repoName || 'none'})`);

        // Set up SSE streaming
        res.setHeader('Content-Type', 'text/event-stream');
        res.setHeader('Cache-Control', 'no-cache');
        res.setHeader('Connection', 'keep-alive');
        res.setHeader('X-Accel-Buffering', 'no');
        res.flushHeaders();

        // Send keepalive comments every 15s to prevent idle connection timeouts
        const heartbeatInterval = setInterval(() => {
            try { res.write(':heartbeat\n\n'); } catch {}
        }, 15000);
        res.on('close', () => clearInterval(heartbeatInterval));

        // Proxy to game worker
        const workerResponse = await fetch(`${GAME_WORKER_URL}/generate-harder`, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
                'x-worker-secret': GAME_WORKER_SECRET
            },
            body: JSON.stringify({
                parentBundle,
                difficultyLevel: parsedLevel,
                gameTitle,
                repoName,
                stream: true
            }),
            signal: AbortSignal.timeout(600000)
        });

        if (!workerResponse.ok) {
            let errorMsg = `Build server error (${workerResponse.status})`;
            try {
                if (workerResponse.headers.get('content-type')?.includes('application/json')) {
                    const err = await workerResponse.json();
                    errorMsg = err.error || errorMsg;
                }
            } catch {}

            // Mark as failed
            await supabase
                .from('game_difficulty_variants')
                .update({ status: 'failed' })
                .eq('parent_game_id', gameId)
                .eq('difficulty_level', parsedLevel);

            res.write(`data: ${JSON.stringify({ type: 'error', error: errorMsg })}\n\n`);
            res.end();
            return;
        }

        if (!workerResponse.body) {
            await supabase
                .from('game_difficulty_variants')
                .update({ status: 'failed' })
                .eq('parent_game_id', gameId)
                .eq('difficulty_level', parsedLevel);

            res.write(`data: ${JSON.stringify({ type: 'error', error: 'Build server connection failed' })}\n\n`);
            res.end();
            return;
        }

        // Stream SSE from worker to client
        const reader = workerResponse.body.getReader();
        const decoder = new TextDecoder();
        let buffer = '';
        let resultReceived = false;

        try {
            while (true) {
                const { done, value } = await reader.read();
                if (done) break;

                buffer += decoder.decode(value, { stream: true });
                const lines = buffer.split('\n');
                buffer = lines.pop() || '';

                for (const line of lines) {
                    if (line.startsWith('data: ')) {
                        const eventData = line.substring(6);
                        try {
                            const parsed = JSON.parse(eventData);

                            if (parsed.type === 'status') {
                                res.write(`data: ${JSON.stringify({
                                    type: 'status',
                                    phase: parsed.phase,
                                    message: parsed.message,
                                    detail: parsed.detail,
                                    progressPercent: parsed.progressPercent,
                                    progressEndPct: parsed.progressEndPct,
                                    phaseDurationSeconds: parsed.phaseDurationSeconds,
                                    estimatedSecondsRemaining: parsed.estimatedSecondsRemaining
                                })}\n\n`);
                            } else if (parsed.type === 'result') {
                                resultReceived = true;

                                // Store the variant bundle in DB
                                await supabase
                                    .from('game_difficulty_variants')
                                    .update({
                                        html_content: parsed.bundle,
                                        status: 'ready'
                                    })
                                    .eq('parent_game_id', gameId)
                                    .eq('difficulty_level', parsedLevel);

                                console.log(`[difficulty] Level ${parsedLevel} ready for game ${gameId} (${(parsed.bundleSize / 1024).toFixed(1)}KB)`);

                                res.write(`data: ${JSON.stringify({
                                    type: 'result',
                                    success: true,
                                    bundle: parsed.bundle,
                                    bundleSize: parsed.bundleSize,
                                    difficultyLevel: parsedLevel,
                                    generationTime: parsed.generationTime
                                })}\n\n`);
                            } else if (parsed.type === 'error') {
                                res.write(`data: ${JSON.stringify({
                                    type: 'error',
                                    error: parsed.error
                                })}\n\n`);
                            }
                        } catch {}
                    }
                }
            }
        } catch (streamError) {
            if (!resultReceived) {
                res.write(`data: ${JSON.stringify({ type: 'error', error: 'Connection to build server lost' })}\n\n`);
            }
        }

        // If no result received, mark as failed
        if (!resultReceived) {
            await supabase
                .from('game_difficulty_variants')
                .update({ status: 'failed' })
                .eq('parent_game_id', gameId)
                .eq('difficulty_level', parsedLevel);
        }

        res.end();

    } catch (error) {
        console.error('[difficulty-generate] Error:', error.message);

        if (res.headersSent) {
            res.write(`data: ${JSON.stringify({ type: 'error', error: 'Server error during difficulty generation' })}\n\n`);
            res.end();
        } else {
            res.status(500).json({ error: 'Failed to generate difficulty variant' });
        }
    }
});

// ============================================
// Game Tweak Editor Endpoints
// ============================================

const EDIT_COST = 10; // coins per edit after free edits exhausted
const FREE_TWEAKS_DEFAULT = 5;
const GITHUB_PAT_BACKEND = process.env.GITHUB_PAT || '';
const GITHUB_ORG_BACKEND = process.env.GITHUB_ORG || 'Kreative-Koala-LLC';

// POST /api/game-creation/:gameId/tweak
// Creator applies a tweak to their game. Manages coins, repo init, proxies to game worker.
router.post('/:gameId/tweak', async (req, res) => {
    try {
        const { gameId } = req.params;
        const { userId, tweakDescription } = req.body;

        console.log(`[tweak] Request: user=${userId}, game=${gameId}, desc="${(tweakDescription || '').substring(0, 80)}"`);

        if (!userId || !tweakDescription || tweakDescription.trim().length === 0) {
            return res.status(400).json({ error: 'userId and tweakDescription are required' });
        }

        if (tweakDescription.length > 500) {
            return res.status(400).json({ error: 'Tweak description must be under 500 characters' });
        }

        // 1. Verify user owns this game
        const { data: game, error: gameError } = await supabase
            .from('custom_games')
            .select('id, title, creator_id, html_content, github_repo, free_tweaks_remaining')
            .eq('id', gameId)
            .single();

        if (gameError || !game) {
            console.log(`[tweak] Game not found: ${gameId}`);
            return res.status(404).json({ error: 'Game not found' });
        }

        if (game.creator_id !== userId) {
            console.log(`[tweak] Not owner: user=${userId}, creator=${game.creator_id}`);
            return res.status(403).json({ error: 'Only the game creator can tweak this game' });
        }

        // 2. Check free tweaks or spend coins
        const freeTweaks = game.free_tweaks_remaining ?? FREE_TWEAKS_DEFAULT;
        let coinSpent = false;

        if (freeTweaks <= 0) {
            // Need to spend coins
            console.log(`[tweak] No free tweaks remaining, spending ${EDIT_COST} coins`);
            const { data: spendResult, error: spendError } = await supabase.rpc('spend_coins', {
                p_user_id: userId,
                p_amount: EDIT_COST,
                p_reason: 'customize',
                p_game_id: gameId,
                p_creator_id: null,
                p_platform: null
            });

            if (spendError) {
                console.error(`[tweak] Coin spend error:`, spendError.message);
                return res.status(500).json({ error: 'Failed to process payment' });
            }

            if (!spendResult.success) {
                console.log(`[tweak] Insufficient coins: balance=${spendResult.balance}`);
                return res.status(400).json({
                    error: 'Insufficient coins',
                    balance: spendResult.balance || 0,
                    tweakCost: EDIT_COST
                });
            }
            coinSpent = true;
            console.log(`[tweak] Coins spent: ${EDIT_COST}, new balance=${spendResult.new_balance}`);
        } else {
            console.log(`[tweak] Using free tweak (${freeTweaks} remaining)`);
        }

        // 3. Ensure game has a GitHub repo
        let repoName = game.github_repo;

        if (!repoName) {
            console.log(`[tweak] No repo exists, initializing for game ${gameId}`);

            try {
                const initResponse = await fetch(`${GAME_WORKER_URL}/init-repo`, {
                    method: 'POST',
                    headers: {
                        'Content-Type': 'application/json',
                        'x-worker-secret': GAME_WORKER_SECRET
                    },
                    body: JSON.stringify({
                        gameId,
                        bundle: game.html_content,
                    }),
                    signal: AbortSignal.timeout(120000)
                });

                if (!initResponse.ok) {
                    const err = await initResponse.json().catch(() => ({}));
                    console.error(`[tweak] Repo init failed: ${err.error || initResponse.status}`);
                    // Refund coins if we spent them
                    if (coinSpent) {
                        await supabase.rpc('add_purchased_coins', {
                            p_user_id: userId,
                            p_amount: EDIT_COST,
                            p_reason: 'tweak_refund',
                            p_platform: 'system',
                            p_iap_transaction_id: `refund-${Date.now()}`
                        });
                        console.log(`[tweak] Coins refunded: ${EDIT_COST}`);
                    }
                    return res.status(500).json({ error: 'Failed to initialize game repository' });
                }

                const initData = await initResponse.json();
                repoName = initData.repoName;

                // Save repo name to DB
                await supabase
                    .from('custom_games')
                    .update({ github_repo: repoName })
                    .eq('id', gameId);

                console.log(`[tweak] Repo initialized: ${repoName}`);
            } catch (initErr) {
                console.error(`[tweak] Repo init exception:`, initErr.message);
                return res.status(500).json({ error: 'Failed to initialize game repository' });
            }
        }

        // 4. Decrement free tweaks (if used)
        if (freeTweaks > 0) {
            await supabase
                .from('custom_games')
                .update({ free_tweaks_remaining: freeTweaks - 1 })
                .eq('id', gameId);
            console.log(`[tweak] Free tweaks decremented: ${freeTweaks} -> ${freeTweaks - 1}`);
        }

        // 5. Proxy to game worker with SSE streaming
        console.log(`[tweak] Proxying to game worker: repo=${repoName}`);

        // Set up SSE streaming to client
        res.setHeader('Content-Type', 'text/event-stream');
        res.setHeader('Cache-Control', 'no-cache');
        res.setHeader('Connection', 'keep-alive');
        res.setHeader('X-Accel-Buffering', 'no');
        res.flushHeaders();

        // Send keepalive comments every 15s to prevent idle connection timeouts
        const heartbeatInterval = setInterval(() => {
            try { res.write(':heartbeat\n\n'); } catch {}
        }, 15000);
        res.on('close', () => clearInterval(heartbeatInterval));

        const workerResponse = await fetch(`${GAME_WORKER_URL}/tweak`, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
                'x-worker-secret': GAME_WORKER_SECRET
            },
            body: JSON.stringify({
                gameId,
                repoName,
                tweakDescription: tweakDescription.trim(),
                stream: true
            }),
            signal: AbortSignal.timeout(600000)
        });

        if (!workerResponse.ok) {
            let errorMsg = `Build server error (${workerResponse.status})`;
            try {
                const err = await workerResponse.json();
                errorMsg = err.error || errorMsg;
            } catch {}

            // Refund if coins were spent
            if (coinSpent) {
                await supabase.rpc('add_purchased_coins', {
                    p_user_id: userId,
                    p_amount: EDIT_COST,
                    p_reason: 'tweak_refund',
                    p_platform: 'system',
                    p_iap_transaction_id: `refund-${Date.now()}`
                });
                console.log(`[tweak] Coins refunded due to worker error`);
            }
            // Restore free tweak
            if (freeTweaks > 0) {
                await supabase
                    .from('custom_games')
                    .update({ free_tweaks_remaining: freeTweaks })
                    .eq('id', gameId);
            }

            res.write(`data: ${JSON.stringify({ type: 'error', error: errorMsg })}\n\n`);
            res.end();
            return;
        }

        if (!workerResponse.body) {
            res.write(`data: ${JSON.stringify({ type: 'error', error: 'Build server connection failed' })}\n\n`);
            res.end();
            return;
        }

        // Stream SSE from worker to client
        const reader = workerResponse.body.getReader();
        const decoder = new TextDecoder();
        let buffer = '';
        let resultReceived = false;

        try {
            while (true) {
                const { done, value } = await reader.read();
                if (done) break;

                buffer += decoder.decode(value, { stream: true });
                const lines = buffer.split('\n');
                buffer = lines.pop() || '';

                for (const line of lines) {
                    if (line.startsWith('data: ')) {
                        const eventData = line.substring(6);
                        try {
                            const parsed = JSON.parse(eventData);

                            if (parsed.type === 'status') {
                                res.write(`data: ${JSON.stringify({
                                    type: 'status',
                                    phase: parsed.phase,
                                    message: parsed.message,
                                    detail: parsed.detail,
                                    progressPercent: parsed.progressPercent,
                                    progressEndPct: parsed.progressEndPct,
                                    phaseDurationSeconds: parsed.phaseDurationSeconds,
                                    estimatedSecondsRemaining: parsed.estimatedSecondsRemaining
                                })}\n\n`);
                            } else if (parsed.type === 'result') {
                                resultReceived = true;

                                // Update the game's bundle in DB
                                const updateData = { html_content: parsed.bundle };
                                if (parsed.commitSha) {
                                    updateData.published_commit = parsed.commitSha;
                                }

                                await supabase
                                    .from('custom_games')
                                    .update(updateData)
                                    .eq('id', gameId);

                                console.log(`[tweak] Bundle updated for game ${gameId} (commit: ${parsed.commitSha || 'none'})`);

                                // Invalidate difficulty variants
                                const { data: deletedVariants } = await supabase
                                    .from('game_difficulty_variants')
                                    .delete()
                                    .eq('parent_game_id', gameId)
                                    .select('difficulty_level');

                                if (deletedVariants && deletedVariants.length > 0) {
                                    console.log(`[tweak] Invalidated ${deletedVariants.length} difficulty variant(s) for game ${gameId}`);
                                }

                                res.write(`data: ${JSON.stringify({
                                    type: 'result',
                                    success: true,
                                    bundle: parsed.bundle,
                                    bundleSize: parsed.bundleSize,
                                    commitSha: parsed.commitSha,
                                    generationTime: parsed.generationTime,
                                    freeTweaksRemaining: freeTweaks > 0 ? freeTweaks - 1 : 0,
                                    coinsSpent: coinSpent ? EDIT_COST : 0
                                })}\n\n`);
                            } else if (parsed.type === 'error') {
                                res.write(`data: ${JSON.stringify({
                                    type: 'error',
                                    error: parsed.error
                                })}\n\n`);
                            }
                        } catch {}
                    }
                }
            }
        } catch (streamError) {
            console.error(`[tweak] Stream error:`, streamError.message);
            if (!resultReceived) {
                res.write(`data: ${JSON.stringify({ type: 'error', error: 'Connection to build server lost' })}\n\n`);
            }
        }

        res.end();

    } catch (error) {
        console.error('[tweak] Error:', error.message);

        if (res.headersSent) {
            res.write(`data: ${JSON.stringify({ type: 'error', error: 'Server error during tweak' })}\n\n`);
            res.end();
        } else {
            res.status(500).json({ error: 'Failed to apply tweak' });
        }
    }
});

// ============================================
// Game Customization (any player, creates new game)
// ============================================

const CUSTOMIZE_COST = 10; // coins per customization

// POST /api/game-creation/:gameId/customize
// Any player can customize a game — result is saved as a new game under their name.
router.post('/:gameId/customize', async (req, res) => {
    try {
        const { gameId } = req.params;
        const { userId, customizeDescription, newTitle } = req.body;

        console.log(`[customize] Request: user=${userId}, game=${gameId}, desc="${(customizeDescription || '').substring(0, 80)}"`);

        if (!userId || !customizeDescription || customizeDescription.trim().length === 0) {
            return res.status(400).json({ error: 'userId and customizeDescription are required' });
        }

        if (customizeDescription.length > 500) {
            return res.status(400).json({ error: 'Description must be under 500 characters' });
        }

        // 1. Fetch parent game bundle
        const { data: game, error: gameError } = await supabase
            .from('custom_games')
            .select('id, title, html_content, creator_id')
            .eq('id', gameId)
            .single();

        if (gameError || !game) {
            console.log(`[customize] Game not found: ${gameId}`);
            return res.status(404).json({ error: 'Game not found' });
        }

        if (!game.html_content) {
            return res.status(400).json({ error: 'Game has no bundle to customize' });
        }

        const effectiveTitle = (newTitle && newTitle.trim()) || `My ${game.title}`;

        // 2. No ownership check — any authenticated user can customize

        // 3. Set up SSE streaming
        res.setHeader('Content-Type', 'text/event-stream');
        res.setHeader('Cache-Control', 'no-cache');
        res.setHeader('Connection', 'keep-alive');
        res.setHeader('X-Accel-Buffering', 'no');
        res.flushHeaders();

        const heartbeatInterval = setInterval(() => {
            try { res.write(':heartbeat\n\n'); } catch {}
        }, 15000);
        res.on('close', () => clearInterval(heartbeatInterval));

        // 4. Proxy to game worker /customize
        console.log(`[customize] Proxying to game worker for "${effectiveTitle}"`);

        const workerResponse = await fetch(`${GAME_WORKER_URL}/customize`, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
                'x-worker-secret': GAME_WORKER_SECRET
            },
            body: JSON.stringify({
                parentBundle: game.html_content,
                customizeDescription: customizeDescription.trim(),
                gameTitle: game.title,
                newTitle: effectiveTitle,
                stream: true
            }),
            signal: AbortSignal.timeout(600000)
        });

        if (!workerResponse.ok) {
            let errorMsg = `Build server error (${workerResponse.status})`;
            try {
                const err = await workerResponse.json();
                errorMsg = err.error || errorMsg;
            } catch {}

            res.write(`data: ${JSON.stringify({ type: 'error', error: errorMsg })}\n\n`);
            res.end();
            return;
        }

        if (!workerResponse.body) {
            res.write(`data: ${JSON.stringify({ type: 'error', error: 'Build server connection failed' })}\n\n`);
            res.end();
            return;
        }

        // 5. Stream SSE from worker to client
        const reader = workerResponse.body.getReader();
        const decoder = new TextDecoder();
        let buffer = '';
        let resultReceived = false;

        try {
            while (true) {
                const { done, value } = await reader.read();
                if (done) break;

                buffer += decoder.decode(value, { stream: true });
                const lines = buffer.split('\n');
                buffer = lines.pop() || '';

                for (const line of lines) {
                    if (line.startsWith('data: ')) {
                        const eventData = line.substring(6);
                        try {
                            const parsed = JSON.parse(eventData);

                            if (parsed.type === 'status') {
                                res.write(`data: ${JSON.stringify({
                                    type: 'status',
                                    phase: parsed.phase,
                                    message: parsed.message,
                                    detail: parsed.detail,
                                    progressPercent: parsed.progressPercent,
                                    progressEndPct: parsed.progressEndPct,
                                    phaseDurationSeconds: parsed.phaseDurationSeconds,
                                    estimatedSecondsRemaining: parsed.estimatedSecondsRemaining
                                })}\n\n`);
                            } else if (parsed.type === 'result') {
                                resultReceived = true;

                                const isOwner = game.creator_id === userId;

                                if (isOwner) {
                                    // Owner fixing their own game — update in-place and promote to published
                                    const { error: updateError } = await supabase
                                        .from('custom_games')
                                        .update({
                                            html_content: parsed.bundle,
                                            title: effectiveTitle !== `My ${game.title}` ? effectiveTitle : game.title,
                                            status: 'published',
                                            critical_issues: 0
                                        })
                                        .eq('id', gameId);

                                    if (updateError) {
                                        console.error(`[customize] Failed to update game in-place:`, updateError.message);
                                        res.write(`data: ${JSON.stringify({ type: 'error', error: 'Failed to save fixed game' })}\n\n`);
                                    } else {
                                        console.log(`[customize] Game updated in-place: ${gameId} ("${game.title}")`);

                                        // Clear difficulty variants since the base game changed
                                        await supabase
                                            .from('game_difficulty_variants')
                                            .delete()
                                            .eq('parent_game_id', gameId);

                                        refreshBrowseCache();

                                        res.write(`data: ${JSON.stringify({
                                            type: 'result',
                                            success: true,
                                            gameId: gameId,
                                            title: game.title,
                                            bundle: parsed.bundle,
                                            bundleSize: parsed.bundleSize,
                                            generationTime: parsed.generationTime
                                        })}\n\n`);
                                    }
                                } else {
                                    // Different user customizing — create a new game under their name
                                    const newGameId = crypto.randomUUID();
                                    const { error: insertError } = await supabase
                                        .from('custom_games')
                                        .insert({
                                            id: newGameId,
                                            title: effectiveTitle,
                                            creator_id: userId,
                                            creator_name: 'Player',
                                            html_content: parsed.bundle,
                                            description: `Customized from: ${game.title}. ${customizeDescription.trim().substring(0, 150)}`,
                                            game_type: 'ai_generated',
                                            platform_type: 'webview',
                                            play_count: 0,
                                            rating: 0
                                        });

                                    if (insertError) {
                                        console.error(`[customize] Failed to save new game:`, insertError.message);
                                        res.write(`data: ${JSON.stringify({ type: 'error', error: 'Failed to save customized game' })}\n\n`);
                                    } else {
                                        console.log(`[customize] New game created: ${newGameId} ("${effectiveTitle}") from parent ${gameId}`);

                                        res.write(`data: ${JSON.stringify({
                                            type: 'result',
                                            success: true,
                                            gameId: newGameId,
                                            title: effectiveTitle,
                                            bundle: parsed.bundle,
                                            bundleSize: parsed.bundleSize,
                                            generationTime: parsed.generationTime
                                        })}\n\n`);
                                    }
                                }
                            } else if (parsed.type === 'error') {
                                res.write(`data: ${JSON.stringify({
                                    type: 'error',
                                    error: parsed.error
                                })}\n\n`);
                            }
                        } catch {}
                    }
                }
            }
        } catch (streamError) {
            console.error(`[customize] Stream error:`, streamError.message);
            if (!resultReceived) {
                res.write(`data: ${JSON.stringify({ type: 'error', error: 'Connection to build server lost' })}\n\n`);
            }
        }

        res.end();

    } catch (error) {
        console.error('[customize] Error:', error.message);

        if (res.headersSent) {
            res.write(`data: ${JSON.stringify({ type: 'error', error: 'Server error during customization' })}\n\n`);
            res.end();
        } else {
            res.status(500).json({ error: 'Failed to customize game' });
        }
    }
});

// GET /api/game-creation/:gameId/versions
// Returns git commit history (version history) for a game
router.get('/:gameId/versions', async (req, res) => {
    try {
        const { gameId } = req.params;
        const limit = parseInt(req.query.limit) || 20;

        console.log(`[versions] Fetching versions for game ${gameId}`);

        // Get repo name from DB
        const { data: game, error: gameError } = await supabase
            .from('custom_games')
            .select('github_repo, free_tweaks_remaining')
            .eq('id', gameId)
            .single();

        if (gameError || !game) {
            return res.status(404).json({ error: 'Game not found' });
        }

        if (!game.github_repo) {
            console.log(`[versions] No repo for game ${gameId}`);
            return res.json({
                success: true,
                versions: [],
                hasRepo: false,
                freeTweaksRemaining: game.free_tweaks_remaining ?? FREE_TWEAKS_DEFAULT,
                tweakCost: EDIT_COST
            });
        }

        // Fetch from game worker
        const workerResponse = await fetch(
            `${GAME_WORKER_URL}/versions/${game.github_repo}?limit=${limit}`,
            {
                headers: { 'x-worker-secret': GAME_WORKER_SECRET },
                signal: AbortSignal.timeout(15000)
            }
        );

        if (!workerResponse.ok) {
            throw new Error(`Worker error ${workerResponse.status}`);
        }

        const data = await workerResponse.json();
        console.log(`[versions] Got ${data.versions?.length || 0} versions for game ${gameId}`);

        res.json({
            success: true,
            versions: data.versions || [],
            hasRepo: true,
            freeTweaksRemaining: game.free_tweaks_remaining ?? FREE_TWEAKS_DEFAULT,
            tweakCost: EDIT_COST
        });

    } catch (error) {
        console.error('[versions] Error:', error.message);
        res.status(500).json({ error: 'Failed to fetch version history' });
    }
});

// POST /api/game-creation/:gameId/revert/:commitSha
// Revert a game to a specific version (commit)
router.post('/:gameId/revert/:commitSha', async (req, res) => {
    try {
        const { gameId, commitSha } = req.params;
        const { userId } = req.body;

        console.log(`[revert] User ${userId} reverting game ${gameId} to commit ${commitSha.substring(0, 7)}`);

        if (!userId) {
            return res.status(400).json({ error: 'userId is required' });
        }

        // Verify ownership
        const { data: game, error: gameError } = await supabase
            .from('custom_games')
            .select('id, creator_id, github_repo')
            .eq('id', gameId)
            .single();

        if (gameError || !game) {
            return res.status(404).json({ error: 'Game not found' });
        }

        if (game.creator_id !== userId) {
            return res.status(403).json({ error: 'Only the game creator can revert' });
        }

        if (!game.github_repo) {
            return res.status(400).json({ error: 'Game has no version history' });
        }

        // Download the repo at the specified commit via GitHub API
        if (!GITHUB_PAT_BACKEND) {
            return res.status(503).json({ error: 'Git integration not configured' });
        }

        console.log(`[revert] Downloading ${game.github_repo} at ${commitSha.substring(0, 7)}`);

        const ghResponse = await fetch(
            `https://api.github.com/repos/${GITHUB_ORG_BACKEND}/${game.github_repo}/zipball/${commitSha}`,
            {
                headers: {
                    'Authorization': `Bearer ${GITHUB_PAT_BACKEND}`,
                    'Accept': 'application/vnd.github+json',
                },
                redirect: 'follow',
            }
        );

        if (!ghResponse.ok) {
            console.error(`[revert] GitHub download error: ${ghResponse.status}`);
            return res.status(500).json({ error: 'Failed to download version from GitHub' });
        }

        // GitHub returns a zip with a top-level folder — we need to repackage it
        // For simplicity, download the raw files and re-zip
        // Actually, we can use the git archive or get the tree and rebuild
        // Simpler: just re-clone at specific commit on game worker

        // For now, use the GitHub-provided zip and re-encode as base64
        const zipBuffer = Buffer.from(await ghResponse.arrayBuffer());
        const bundle = zipBuffer.toString('base64');

        console.log(`[revert] Downloaded ${(zipBuffer.length / 1024).toFixed(1)}KB, updating DB`);

        // Update game bundle in DB
        await supabase
            .from('custom_games')
            .update({
                html_content: bundle,
                published_commit: commitSha
            })
            .eq('id', gameId);

        // Invalidate difficulty variants
        const { data: deletedVariants } = await supabase
            .from('game_difficulty_variants')
            .delete()
            .eq('parent_game_id', gameId)
            .select('difficulty_level');

        if (deletedVariants && deletedVariants.length > 0) {
            console.log(`[revert] Invalidated ${deletedVariants.length} difficulty variant(s)`);
        }

        console.log(`[revert] Game ${gameId} reverted to ${commitSha.substring(0, 7)}`);

        res.json({
            success: true,
            commitSha,
            bundle,
            bundleSize: zipBuffer.length
        });

    } catch (error) {
        console.error('[revert] Error:', error.message);
        res.status(500).json({ error: 'Failed to revert game' });
    }
});

// ============================================
// Game Remix — Create web version of native puzzle games
// ============================================

const PUZZLE_TYPE_PROMPTS = {
    // Word-based puzzles
    anagram: "An anagram word puzzle game. Players see jumbled letters and must unscramble them to form the correct word. UI Layout: Large letter tiles displayed horizontally at the top, an answer area below where tiles snap into place, and a submit button. Interaction: Tap a letter tile to move it to the answer area, tap again to return it. Include a shuffle button to rearrange remaining tiles. Game flow: 10+ rounds with words getting longer (4-letter to 8-letter). Scoring: Points based on speed — bonus multiplier for solving within 5 seconds. Include a hint button that reveals one correct letter position (costs 50 points). Show a streak counter for consecutive correct answers. Animations: Letter tiles should bounce when placed, shimmer effect on correct answer, shake animation on wrong answer. Sound: Click on tile placement, success chime, streak sound effect.",

    antonyms: "A word antonym matching game. Players are shown two columns — 5 words on the left and 5 words (their opposites) shuffled on the right. Interaction: Tap a word on the left, then tap its opposite on the right to create a match. Correct matches draw a connecting line and both words dim/fade. Wrong matches flash red and shake. Game flow: 6 rounds of 5 pairs each, getting progressively harder (common words like hot/cold, then abstract words like ephemeral/permanent). Scoring: 100 points per correct match, time bonus for finishing a round quickly, streak bonus. UI: Dark themed cards with glowing borders, connecting lines animate in with gradient colors. Include a lives system — 3 wrong matches and game over. Timer per round (30 seconds). Show total score prominently at top.",

    synonyms: "A word synonym grouping puzzle. Players see 12-16 word cards scattered on screen and must group them into 4 categories of words with similar meanings. Interaction: Tap words to select them (highlight with glow), then tap 'Submit Group' when 4 are selected. Correct groups collapse and stack at the top with a category label revealed. Wrong groups shake and deselect. Game flow: 4 groups per round, 3 rounds total. Categories get trickier — from obvious (happy/joyful/glad/cheerful) to subtle (meticulous/scrupulous/fastidious/painstaking). Scoring: Points per correct group, bonus for solving groups in fewer attempts. Include a 'One Away' hint if 3 of 4 selected words are correct. Animations: Cards float up when grouped, confetti on completing all 4 groups.",

    crossword: "A crossword puzzle game with a standard crossword grid. UI Layout: The grid takes up the top 60% of the screen with cells for letters and blocked-out squares. Below the grid, show the current clue prominently, with 'Across' and 'Down' clue lists in a scrollable panel. Interaction: Tap a cell to select it — the entire word highlights in blue (across) or orange (down). Tap again to toggle direction. On-screen keyboard appears at the bottom for letter input. Arrow keys or swipe to navigate between cells. Features: Auto-advance to next empty cell after typing, backspace removes and moves back. Highlight conflicting letters in red. 'Check' button to validate current answers. 'Reveal letter' hint (limited uses). Timer at top. Grid size: 7x7 for Easy, 10x10 for Medium, 13x13 for Hard. Generate themed clues and answers. Scoring: Points based on completion percentage and time. Animations: Smooth cell selection, word completion highlight.",

    dailycrossword: "A daily-themed crossword puzzle. Same mechanics as crossword — interactive grid with across/down clues, tap-to-select cells, keyboard input, auto-advance. Grid size 9x9. The theme changes daily — generate a topic (e.g., 'Ocean Life', 'Space', 'Movies') and create clues/answers around that theme. Show the theme prominently at the top. Include a completion timer that shows your solve time at the end. Features: Check button to validate, reveal letter hint (3 uses), progress auto-saves. Scoring: Time-based with bonus for no hints used. Show a completion stats screen with time, accuracy, hints used. Animations: Celebrate completion with confetti and a 'Come back tomorrow!' message.",

    wordsearch: "A word search puzzle game. UI Layout: A grid of letters (10x10 for Easy, 14x14 for Hard) fills most of the screen. A word list panel on the right (or below on mobile) shows 8-12 words to find. Interaction: Swipe/drag across letters to select a word — the selection highlights with a colored line. If the selected letters form a word from the list, the line stays permanently and the word gets crossed off. Words can be hidden horizontally, vertically, diagonally, and backwards. Features: Different colors for each found word, timer counting up, hint button that highlights the first letter of a random unfound word. Scoring: Points per word found, time bonus for finding all words. Generate themed word lists (e.g., animals, countries, food). Animations: Words glow when found, completion celebration. Grid letters should be large enough for touch (min 36px).",

    wordsnake: "A word snake puzzle. A grid of letters (6x6) is displayed with letters connected in a snake-like path. Players must trace a path through adjacent letters (horizontally, vertically, or diagonally) to spell out hidden words. Interaction: Tap the first letter, then tap adjacent letters to extend the path — a line connects the selected letters. Submit when a valid word is formed. Multiple words are hidden in the same grid. Features: Path highlighting with gradient color, word list showing found/remaining words, undo button to remove last letter, clear button. Scoring: Longer words = more points, bonus for finding all words. Generate 5-8 words per grid, with some letters shared between words. Animations: Path glows as you trace, found words fly to the word list. Timer adds urgency.",

    wordprefix: "A word prefix challenge game. Players are given a 2-3 letter prefix (like 'UN-', 'PRE-', 'RE-') and must type as many words starting with that prefix as they can within 60 seconds. UI: Large prefix display at top, text input field in the middle, list of found words below scrolling down as you add more. Validation happens instantly — valid words appear with a green check, invalid ones shake and disappear. Scoring: 10 points per 4-letter word, 20 for 5-letter, 30 for 6+, 50 for rare words. Show a 'Words remaining' count. Include 6 rounds with different prefixes. Bonus round: given a suffix instead. Leaderboard-style 'par' score to beat. Animations: Words stack up with satisfying pop animation, timer bar shrinks across the top.",

    letterset: "A letter set word-forming puzzle. Players are given 7-9 letter tiles and must form as many valid words as possible using those letters. UI: Letter tiles displayed in a circle or arc at the center, text input area above, found words list on the side. Interaction: Tap letters to add them to the word, tap submit or press enter to check. Include a shuffle button to rearrange the letter circle. Minimum word length: 3 letters. Features: Dictionary validation, duplicate word prevention, timer (3 minutes per round), hint showing first two letters of an unfound word. Scoring: 3-letter = 10pts, 4-letter = 20pts, 5-letter = 40pts, 6-letter = 80pts, 7+ letter (using all) = 200pts. 5 rounds with different letter sets. Animations: Letters bounce when tapped, found words slide into list, bonus animation for long words.",

    // Math puzzles
    math: "A fast-paced math quiz game. Players solve arithmetic problems (addition, subtraction, multiplication, division) against a timer. UI Layout: Problem displayed large and centered (e.g., '24 × 7 = ?'), with 4 multiple-choice answer buttons below arranged in a 2x2 grid. Timer bar at the top counting down from 10 seconds per question. Score and streak counter in the header. Game flow: 20 questions total, difficulty scales — starts with single-digit addition, progresses to multi-digit multiplication/division. Scoring: 100 points per correct answer, streak multiplier (2x at 3 streak, 3x at 5, 5x at 10). Wrong answer breaks streak and deducts 25 points. Animations: Correct = green flash + bounce, wrong = red shake, streak milestones trigger special effects. Include power-ups: 'Double Time' (20s timer), '50/50' (removes 2 wrong answers). End screen shows accuracy percentage, longest streak, and total score.",

    mathestimation: "A math estimation game with visual charts. Players see an animated bar chart or pie chart with some values labeled and must estimate an unlabeled value by dragging a slider. UI: Chart visualization takes up 60% of screen, slider control below with numerical readout. Question text above chart (e.g., 'Estimate the value of Bar C'). Scoring is based on accuracy — within 5% = 100pts, within 10% = 75pts, within 20% = 50pts, more = 25pts. 10 rounds with different chart types (bar, pie, line graph, scatter plot). Each round has a 15-second timer. Features: Visual feedback showing how close your estimate was with an animated indicator. Animations: Charts draw in with animation, accuracy meter fills up. Progressive difficulty — charts get more complex with more data points.",

    mathtipping: "A restaurant tipping calculation game. Players see a restaurant bill amount and must quickly calculate the correct tip. UI: Receipt-style card showing the bill total, with buttons for common tip percentages (15%, 18%, 20%, 25%) and a custom input option. The challenge: calculate the tip amount AND the total with tip. Game flow: 15 rounds with bills ranging from $12.50 to $247.80. Some rounds ask 'What's 18% of $86.40?' others ask 'Split $156 bill 4 ways with 20% tip — what does each person pay?' Scoring: Correct = 100pts + time bonus, close (within $0.50) = 50pts. Timer: 15 seconds per question. Include mental math tips between rounds. Animations: Bills flip in like cards, coins scatter on correct answer. Receipt prints out at game over showing total performance.",

    mathcomparison: "A rapid math comparison game. Two mathematical expressions appear side by side (e.g., '7 × 8' vs '52 + 3') and players must quickly tap which one is greater, or tap '=' if they're equal. UI: Two large cards side by side with expressions, colored differently (blue vs orange). Tap left or right, or a center '=' button. Timer: 5 seconds per comparison. Game flow: 30 rapid-fire rounds. Difficulty scales: starts with simple (15 vs 12), progresses to expressions (3² vs 2³), fractions (3/4 vs 5/7), percentages (40% of 80 vs 30% of 100). Scoring: 50 points per correct, 10-point speed bonus for under 2 seconds. Streak counter with multiplier. Wrong answer = -25 points. Animations: Winning expression pulses with glow, wrong choice shakes. Show running accuracy percentage.",

    percentages: "A percentage calculation quiz. Players solve real-world percentage problems. Question format varies: 'What is 35% of 240?', 'If a $80 item is 25% off, what's the sale price?', '15 is what percent of 60?'. UI: Problem displayed as a story card with visual context (shopping, grades, statistics), 4 multiple-choice answers below. Timer: 20 seconds per question. Game flow: 15 questions across 3 categories — basic percentages, discounts/markups, and percentage change. Scoring: 100 points per correct answer, time bonus. Include visual progress bars that fill to show the percentage being asked about. Animations: Progress bar fills to visualize the answer, correct/wrong feedback with colors. End screen shows performance breakdown by category.",

    division: "A division math challenge with visual aids. Players solve division problems presented with visual representations. UI: Show the division problem as an animation — objects being divided into groups (e.g., 24 apples into 6 baskets). Player taps the correct answer from 4 choices. Game flow: 15 rounds, progressing from simple (20 ÷ 4) to remainders (29 ÷ 5 = '5 remainder 4') to decimals (15 ÷ 4 = 3.75). Visual aids: Animated dot arrays, grouping circles, number line jumps. Scoring: 100 points per correct, bonus for speed. Timer: 15 seconds per problem. Include a 'show work' feature that animates the long division process step by step. Animations: Objects animate into groups, number line jumps visualize the division. Sound effects for correct grouping.",

    average: "A number averaging challenge. Players are shown a set of numbers and must calculate their average (mean). UI: Numbers displayed as floating bubbles or cards, a slider or number input for the answer. Some rounds show the numbers on a visual scale or bar chart. Game flow: 12 rounds — starts with 3 numbers (easy mental math), progresses to 5-7 numbers, then includes weighted averages and outlier detection ('Which number, if removed, would change the average the most?'). Scoring: Exact answer = 100pts, within 1 = 75pts, within 5% = 50pts. Timer: 20 seconds per round. Features: Visual number line showing where the average falls, animations showing numbers being 'balanced' on a scale. Fun facts about averages between rounds.",

    subtraction: "A subtraction math game with interactive number lines. Players solve subtraction problems using visual number line jumps. UI: A horizontal number line dominates the screen, with the subtraction problem above. Players drag a marker along the number line to find the answer, or tap from 4 multiple-choice options. Game flow: 15 rounds, progressing from simple (45 - 18) to borrowing problems (402 - 167) to negative results (25 - 38). The number line zooms and pans to fit the problem range. Scoring: 100 points per correct answer, bonus for speed. Timer: 15 seconds. Features: Animated jumps on the number line showing the subtraction step by step, regrouping visualization for borrowing. Include estimation rounds where players guess the approximate answer first.",

    purchasing: "A shopping math game. Players solve real-world purchasing scenarios. Scenarios include: calculating total for multiple items, figuring out change from a payment, comparing unit prices, staying within a budget. UI: Shopping cart interface with item cards showing prices, a running total, payment method (cash showing bills/coins). Game flow: 12 scenarios of increasing complexity — from simple totals to tax calculations to 'best deal' comparisons. Timer: 30 seconds per scenario. Scoring: 100 points per correct answer, partial credit for close answers. Features: Animated shopping cart, receipt generation, virtual money counting. Include scenarios with coupons, BOGO deals, and bulk discounts. Visual money display shows bills and coins being counted.",

    discounts: "A discount calculation shopping game. Players see original prices and discount percentages, then must calculate the final sale price. UI: Product cards with original price crossed out, discount badge (e.g., '35% OFF!'), and 4 price options. Some rounds show two stores with different discounts on the same item — pick the better deal. Game flow: 15 rounds — simple percentage off, then stacked discounts (20% off + extra 10% loyalty), then 'Buy 2 Get 1 Free' calculations. Scoring: 100 points per correct, time bonus. Timer: 15 seconds. Features: Animated price tag slashing, side-by-side comparison mode, running savings tracker showing total money saved. End screen shows 'You saved $X today!' with fun shopping stats.",

    conversion: "A unit conversion challenge game. Players convert between measurement systems — metric to imperial, temperature, time zones, currency. UI: Conversion card showing the source value and unit, with an input field or multiple choice for the target unit. Visual conversion aid (thermometer for temperature, ruler for length). Game flow: 15 rounds across categories — length (km to miles), weight (kg to lbs), temperature (C to F), volume (liters to gallons), speed (km/h to mph), time zones. Scoring: Exact answer = 100pts, close (within 5%) = 50pts. Timer: 20 seconds. Features: Animated conversion visualizer, reference chart that briefly flashes, formula hints. Include tricky conversions and everyday scenarios ('Your recipe calls for 2 cups — how many ml?').",

    // Memory puzzles
    memorysquares: "A memory squares pattern game. A grid of squares (starting 3x3, growing to 6x6) briefly highlights a pattern of colored squares. Players must recreate the pattern from memory by tapping the correct squares. UI: Grid of neutral-colored squares centered on screen, clear 'Memorize!' phase with countdown, then 'Recreate!' phase. Game flow: Pattern flashes for 2-3 seconds (shorter at higher levels), player taps to recreate. Start with 3 highlighted squares, add one more each successful round. Up to 15 levels. Lives system: 3 lives, lose one per wrong pattern. Scoring: 100 points per level completed, bonus for speed. Features: Color variations (some rounds use multiple colors that must match), sequential flash mode (squares light up one at a time in order, player recreates the sequence). Animations: Squares pulse with glow when highlighted, success sparkle effect, failure fade-out.",

    memorypreviouspair: "A card-matching memory game. A grid of face-down cards with hidden symbols/images. Players flip two cards at a time — if they match, the pair stays revealed. If not, both flip back. Goal: Find all pairs in minimum flips. UI: Grid of cards with decorative backs, flip animation showing the front. Grid sizes: 4x3 (6 pairs) for Easy, 4x4 (8 pairs) for Medium, 5x4 (10 pairs) for Hard. Features: Move counter, timer counting up, best-score tracking. Symbol themes: emojis, animals, space objects, food. Scoring: Base 1000 points minus 10 per extra flip beyond minimum. Bonus for completing under par time. Animations: Smooth 3D card flip, matched pairs glow and float up slightly, completion confetti. Include a 'power-up' round where one card stays face-up for 3 seconds.",

    memoryprevioussingle: "A 'What was the previous item?' memory game. Players see a sequence of items (shapes, colors, numbers, or images) one at a time. After each new item appears, they must identify what the PREVIOUS item was from 4 choices. UI: Large display area showing the current item, 4 choice buttons below (one being the previous item). Items change every 3 seconds. Game flow: 20 rounds. Difficulty increases: items become more similar, change speed increases to 2 seconds. Scoring: 100 points per correct recall, streak bonus. Some advanced rounds ask 'What was shown 2 items ago?' Features: Visual variety — mix of colors, shapes, numbers, and emoji. Animations: Items fade in/out with smooth transitions, correct answer glows green, wrong answer provides the correct one briefly.",

    memorymatrixpath: "A path memory game on a grid. A path lights up on a grid matrix, moving from cell to cell. Players must memorize the path and recreate it by tapping cells in the same order. UI: Grid (starting 4x4, growing to 7x7) with cells that light up sequentially to show the path. Clear 'Watch!' and 'Recreate!' phases. Game flow: Path starts at 4 steps, adds one step per successful round. Path can go horizontal, vertical, or diagonal. The path light-up speed gets faster at higher levels. Lives: 3 lives. Scoring: 100 points per step in the path × level multiplier. Features: Path shown with animated glowing trail, player's recreation shows in a different color for comparison. Some rounds add 'obstacles' — blocked cells the path routes around. Animations: Trail of light following the path, pulse effect on each step, success shows the full path in gold.",

    memorysequencing: "A sequence memory and ordering game. Players see a sequence of items (5-10 objects, words, or numbers) displayed briefly, then must recreate the exact order. UI: Items displayed in a row for the memorize phase, then scattered/shuffled — player drags them back into order or taps in sequence. Game flow: Start with 4 items, add one each round up to 10. Item types rotate: numbers, colors, animal names, shapes, playing cards. Timer for memorization: starts at 8 seconds, decreases by 0.5s each round. Scoring: Full sequence correct = 100 × item count points, partial credit for items in correct position. Features: 'Reverse order' bonus rounds (recreate in reverse), 'Missing item' rounds (one item removed, identify which). Animations: Items shuffle with card-dealing animation, correct placement snaps into place with glow.",

    memoryretention: "A memory retention and recall test. Players study a piece of information (a short paragraph, a data table, a list of facts, or an image with details) for a limited time, then answer questions about what they studied. UI: Study phase shows the content clearly with a countdown timer, then transitions to question phase with multiple choice answers. Game flow: 8 rounds with different content types — Round 1: remember 5 items from a grocery list, Round 2: study a character profile and answer detail questions, Round 3: memorize a schedule/timetable, etc. Study time: 15-30 seconds depending on complexity. Scoring: 100 points per correct detail recalled. Features: Difficulty scales by reducing study time and increasing detail questions. Include visual memory (remember positions of objects) and numerical memory (remember statistics). End screen shows memory score breakdown by category.",

    memorystory: "A story comprehension memory game. Players read a short story (3-5 paragraphs) about interesting characters and events, then answer detailed questions about what happened. UI: Story displayed as a storybook page with readable font, page-turn animation, timer showing remaining reading time. After reading: multiple-choice questions about characters, events, numbers, colors, and sequences mentioned. Game flow: 5 stories of increasing length and complexity. Stories are engaging mini-narratives (mystery, adventure, comedy). Question types: 'What color was the car?', 'How many people were at the party?', 'What happened after the storm?'. Scoring: 100 points per correct answer, bonus for perfect recall (all questions right). Features: Stories generated with rich, specific details that are testable. Include red herring details. Animations: Book page turns, question reveals with dramatic effect.",

    trivia: "A trivia quiz game with diverse categories. Players answer multiple-choice questions across categories: Science, History, Geography, Pop Culture, Sports, Nature, Technology, Food, Literature, Music. UI: Question card displayed prominently with category icon and color, 4 answer buttons arranged vertically, timer bar, score display, streak counter. Game flow: 20 questions, category rotates or player can choose. Timer: 15 seconds per question. Scoring: 100 points base + time bonus (faster = more), streak multiplier (2x at 3, 3x at 5, 5x at 10). Wrong answer breaks streak. Features: '50/50' lifeline (removes 2 wrong answers, usable 3 times), 'Skip' lifeline (2 uses). After answering, briefly show a 'Did you know?' fact related to the question. Difficulty adapts — get 3 right in a row, questions get harder. Animations: Answer buttons pulse on selection, correct = green + confetti, wrong = red + correct answer highlights.",

    storypuzzle: "An interactive story puzzle game. Players read through a branching narrative and solve puzzles/riddles at key decision points to progress. UI: Story text displayed with atmospheric background, character portraits, and choice buttons at decision points. Puzzles are integrated into the story — decode a message, solve a riddle, arrange clues, make deductions. Game flow: A complete mini-adventure with 3 acts, each containing 2-3 puzzle challenges. Story adapts based on choices. Puzzle types within the story: word riddles, logic deductions, pattern recognition, math-based clues. Scoring: Points for solving puzzles quickly + bonus for making optimal story choices. Features: Save-point system, multiple endings based on decisions, clue notebook that tracks discovered information. Animations: Text appears with typewriter effect, scene transitions with fade, puzzle elements animate in. Dark atmospheric theme with glowing accents.",

    // Visual puzzles
    imagepuzzle: "A sliding/swapping image puzzle game. An image is divided into a grid of tiles (3x3 for Easy, 4x4 for Medium, 5x5 for Hard) and scrambled. Players must rearrange the tiles to reconstruct the original image. UI: The scrambled grid takes center stage, with a small reference thumbnail of the complete image in the corner. Interaction: Tap two tiles to swap their positions, or use slide mechanics (one empty space, slide adjacent tiles). Move counter and timer displayed. Features: Preview button (shows complete image for 2 seconds, limited uses), auto-solve hint that places one tile correctly, grid lines to help alignment. Generate colorful abstract or geometric images that work well when tiled. Scoring: Base 1000 points minus 5 per move over par, time bonus. Animations: Tiles slide smoothly with slight bounce, completion triggers the image reassembling with a satisfying snap. 5 different images per game session.",

    imagequestion: "A visual observation quiz game. Players are shown detailed images and must answer questions about what they see. UI: Full-width image display with pinch-to-zoom capability, question overlay at bottom with 4 answer options. Image shows for 10 seconds, then partially blurs — player answers from memory. Questions: 'How many red objects are in the scene?', 'What was the person wearing?', 'Which direction was the arrow pointing?'. Game flow: 10 rounds with increasingly detailed scenes and trickier questions. Some questions are asked WHILE viewing, others AFTER the image hides. Scoring: 100 points per correct answer, bonus for consecutive correct. Features: Generate detailed scenes with countable objects, specific colors, directional elements, and hidden details. Animations: Image zoom-in reveal, blur transition, answer feedback.",

    imagematch: "A visual matching card game with themed image sets. Classic memory match mechanic but with beautiful themed image cards instead of simple symbols. Themes: space objects, ocean creatures, world landmarks, cute animals, food items, flowers. UI: Grid of face-down cards (4x3 to 6x5), each card has a unique illustrated image. Tap to flip, find matching pairs. Features: Themed card backs matching the category, 3D flip animation, matched pairs float and stack to the side. Bonus challenge: 'Speed Match' mode where pairs must be found within 30 seconds. Scoring: Points based on moves and time — fewer moves = higher score. Track personal bests. Animations: Smooth card flip with shadow, matched pair celebration sparkle, completion fireworks. Progressive difficulty adds more pairs and similar-looking images.",

    find_differences: "A spot-the-differences game. Two nearly identical images are displayed side by side (or stacked on mobile). Players must find and tap all the differences between them. UI: Two image panels taking up most of the screen, 'Differences Found: X/Y' counter, hint button, timer. Interaction: Tap on a difference in either image — it highlights in both with a circle. Game flow: 5 scenes with 5-7 differences each. Differences include: missing objects, color changes, size changes, position shifts, added elements. Timer: 60 seconds per scene. Scoring: 100 points per difference found, time bonus for completing early, hint costs 50 points. Generate scenes using CSS/SVG art with subtle differences. Features: Zoom capability for detailed inspection, pulsing hint that briefly highlights an area near an unfound difference. Animations: Circle animations around found differences, completion celebration.",

    find_object: "A hidden object search game. Players see a detailed, busy scene and must find specific objects listed on screen. UI: Full-screen zoomable scene with a scrollable object list at the bottom showing 8-10 items to find. Found items get checked off with a satisfying mark. Interaction: Tap on the scene where you spot the object — if correct within the target area, it highlights and checks off. Game flow: 4 scenes, each increasingly cluttered and complex. Themes: messy room, marketplace, garden, underwater. Timer: 90 seconds per scene. Scoring: 100 points per object found, time bonus, 50-point bonus for finding all objects. Features: Hint system that narrows down the search area, objects are cleverly camouflaged but findable. Generate scenes using layered CSS shapes and elements. Animations: Found objects glow and pulse, checklist items animate when marked, completion reveals all hidden objects.",

    waldopuzzle: "A 'Where's Waldo'-style search game. Players must find a specific target character or object hidden in a crowded, detailed scene. UI: Large pannable/zoomable scene showing many characters and objects, target image shown in a fixed corner panel. Timer and hint counter displayed. Interaction: Pan and zoom the scene, tap when you spot the target. Game flow: 5 scenes with the target hidden among similar-looking distractors. Scenes: crowded beach, busy city street, festival, space station, underwater kingdom. Timer: 60 seconds per scene. Scoring: 200 points per find, bonus for speed (under 15s = double), hint costs 100 points. Features: Zooming with smooth pinch controls, hint narrows search area progressively. Generate scenes with many similar but distinct elements — the target has 2-3 unique identifying features. Animations: Spotlight effect on found target, scene zoom-in reveal.",

    uniqueobject: "A spot-the-unique-item puzzle. Players see a grid of similar-looking items (4x4 to 6x6) and must identify the ONE item that is slightly different from all others. Differences can be: color shade, rotation, size, missing detail, extra element. UI: Grid of items centered on screen, tap to select the odd one out. Timer and round counter displayed. Game flow: 15 rounds with increasing subtlety — early rounds have obvious differences (different color), later rounds have pixel-level differences (one dot missing, slight rotation). Scoring: 100 points per correct identification, speed bonus. Timer: 10 seconds per round. Items: geometric shapes, emoji faces, patterns, icons, letters. Features: Zooming on tap-and-hold for close inspection. After answering, highlight the difference with a comparison view. Animations: Items pop in with stagger animation, correct selection circles with gold, wrong selection shows the correct answer.",

    flowpuzzle: "A flow connection puzzle game. A grid (5x5 to 8x8) has pairs of colored dots at various positions. Players must draw paths connecting matching-colored dot pairs without any paths crossing and filling every cell in the grid. Interaction: Touch a colored dot and drag to draw a path through adjacent cells to its matching dot. Paths cannot cross other paths. All cells must be filled. UI: Grid with colored endpoint dots, drawn paths show in matching colors with rounded connections. Undo button for last path, clear button for all paths. Game flow: 20 puzzles of increasing grid size and complexity. Scoring: 100 points per solved puzzle + time bonus + bonus for minimal path lengths. Timer: 90 seconds per puzzle. Features: Path validation (shows red if crossing detected), hint that solves one path, puzzle counter showing progress. Animations: Paths draw with smooth flowing animation, completed puzzle glows, grid fills with satisfying color spread.",

    progressiverevelation: "A progressive image revelation guessing game. An image starts completely hidden and slowly reveals itself. Players guess what the image depicts as early as possible for maximum points. UI: Blurred/pixelated image that progressively sharpens, or grid of tiles that flip to reveal portions. Multiple-choice guess buttons appear at intervals. Game flow: 10 rounds. Each round: image starts hidden, reveals 10% every 3 seconds. At each reveal step, player can guess from 4 options (which change each step). Earlier correct guess = more points: 500pts at 10% revealed, 400pts at 20%, down to 100pts at 90%. Wrong guess reduces options but costs 50 points. Images: landmarks, animals, food, vehicles, famous art. Features: 'Final Answer' mode where player can type a free-text guess for bonus points. Animations: Smooth pixelation-to-clarity transition, dramatic reveal on correct guess. Sound builds tension as image clarifies.",

    realorai: "A 'Real or AI?' image identification game. Players see images one at a time and must determine whether each is a real photograph or generated by AI. UI: Large image display, two buttons below: 'Real Photo' and 'AI Generated'. Timer per image. After answering, reveal the correct answer with an explanation of tells (AI artifacts, unnatural details, or conversely why a real photo looks fake). Game flow: 20 images, mix of 50/50 real and AI. Categories: landscapes, people, animals, food, architecture. Scoring: 100 points per correct identification, streak bonus. Difficulty: starts with obvious examples, progresses to very convincing AI images and unusual real photos. Features: Educational tip after each answer highlighting what to look for (hands, text, reflections, symmetry). Running accuracy percentage displayed. Generate comparisons using CSS art (labeled as 'AI') vs descriptions of real photo characteristics. Animations: Swipe-style card reveal, truth meter animation.",

    oddoneout: "An odd-one-out visual reasoning puzzle. Players see a group of 4-9 items and must identify which one doesn't belong based on a shared attribute. The attribute changes each round — could be color, shape, size, category, pattern, symmetry, or a more abstract property. UI: Items displayed in a clean grid, tap to select the odd one. Round counter, score, and timer shown. Game flow: 15 rounds of increasing abstraction. Early: 'All blue shapes except one red' (visual). Mid: 'All animals except one plant' (category). Late: 'All prime numbers except one composite' (abstract). Scoring: 100 points + speed bonus. Timer: 10 seconds. After selection, show the shared rule with explanation. Features: Some rounds have two possible answers — both are accepted with an explanation of why. Generate items using CSS shapes, icons, and text. Animations: Odd item shakes out of the group, rule text animates in.",

    // Interactive puzzles
    pinballdeflector: "A physics-based pinball deflector puzzle game. A ball launches from a starting position and players must place reflective deflector walls on the board to guide the ball to the goal target. UI: Top-down 2D board with ball start position (top), goal area (bottom/side), and obstacles (walls, bumpers). Players drag and rotate deflector pieces from a toolbar onto the board before pressing 'Launch'. Features: Trajectory preview line showing where the ball will go, gravity affecting ball speed, bouncy walls with angle reflection physics. Game flow: 15 levels of increasing complexity — add more obstacles, multiple balls, moving targets, portals. Scoring: Points based on number of deflectors used (fewer = better) + time bonus. 3-star rating per level. Physics: Ball bounces realistically off deflectors and walls, affected by gravity. Deflector types: flat wall, curved wall, one-way gate. Animations: Ball rolls with trail effect, bounces with spark animation, goal scored with explosion effect. Include a free-play sandbox mode.",

    // Media puzzles
    musicidentification: "A music identification quiz game. Players hear short audio clips and must identify the song, artist, or genre. Since Web Audio API is used (no external files), generate distinctive musical patterns using oscillators, drum patterns, and melodies that represent genres. UI: Audio waveform visualization while playing, 4 answer buttons, play/replay button, timer. Game flow: 15 rounds — 5 genre identification (is this Jazz, Rock, Classical, Electronic, or Hip-hop?), 5 instrument identification (Piano, Guitar, Drums, Violin, Trumpet), 5 rhythm matching (tap along with the beat). Scoring: 100 points per correct answer, time bonus. Features: Generated audio clips using Web Audio API — distinct sine wave melodies, drum patterns using noise generators, chord progressions representing genres. Replay button (2 free replays per round). Visual: Animated equalizer bars matching the audio, genre-themed background colors. Animations: Sound waves pulse with the beat, correct answer triggers celebratory jingle.",

    // Logic puzzles
    crypto: "A cryptogram puzzle game. Players decode encrypted text where each letter has been substituted with a different letter. The goal is to crack the code and reveal the original quote or phrase. UI: Encrypted text displayed prominently with a monospace font, each character in its own box. Below: an alphabet mapping grid showing which letters have been decoded. Interaction: Tap an encrypted letter, then tap the real letter to assign the mapping — all instances update instantly. Game flow: 5 cryptograms of increasing length (short phrase → full quote). Features: Frequency analysis sidebar showing letter occurrence counts, pattern hints (single-letter words are usually 'I' or 'A'), 'Reveal letter' hint (3 uses), undo button. Scoring: Points based on completion time and hints used. Generate interesting quotes and phrases. Animations: Letters transform when decoded with a decryption effect, all instances of a letter flash when mapped. Completion shows the decoded text with author attribution and a celebratory cipher-breaking animation.",

    sentencetransitions: "A sentence ordering and paragraph construction puzzle. Players see 5-8 sentences from a paragraph displayed in random order and must arrange them in the correct logical sequence. UI: Sentence cards stacked in random order, drag-and-drop to reorder, submit button to check. Game flow: 8 rounds with different paragraph types — narrative (story sequence), argumentative (thesis → evidence → conclusion), procedural (step-by-step instructions), chronological (historical events). Scoring: 100 points for perfect order, partial credit based on how many sentences are in correct position. Timer: 60 seconds per round. Features: Transition word highlighting (First, Then, However, Finally) to aid ordering. After submission, show the correct order with transition analysis explaining why each sentence follows the previous one. Drag interaction should be smooth with visual insertion indicators. Animations: Cards shuffle in, snap into place, correct order reveals with a satisfying cascade.",

    connotationwords: "A word connotation sorting game. Players see words and must sort them by their connotation — positive, negative, or neutral. UI: Word cards appear one at a time, with three labeled bins/columns (Positive ✓, Neutral ○, Negative ✗). Swipe or tap to sort each word. Example words: 'frugal' (positive) vs 'cheap' (negative) vs 'inexpensive' (neutral). Game flow: 30 words across 6 themed rounds (money words, appearance words, intelligence words, emotion words, personality words, strength words). Scoring: 100 points per correct categorization. Timer: 8 seconds per word. Features: After each word, briefly show an explanation of why it has that connotation, often comparing to synonym pairs. Include tricky words where connotation depends on context. Running accuracy displayed. Animations: Words slide in from the right, swipe animation to category, correct/wrong color feedback. End screen shows vocabulary analysis and 'Words to Watch' for commonly misidentified connotations."
};

// POST /api/game-creation/remix
// Creates a web version of a native puzzle game with user customizations
// Uses the same game worker /generate endpoint with a specialized prompt
router.post('/remix', async (req, res) => {
    try {
        const { userId, puzzleType, difficulty, remixDescription, title } = req.body;

        if (!userId || !puzzleType) {
            return res.status(400).json({ error: 'userId and puzzleType are required' });
        }

        if (!remixDescription || remixDescription.trim().length === 0) {
            return res.status(400).json({ error: 'remixDescription is required' });
        }

        if (remixDescription.length > 500) {
            return res.status(400).json({ error: 'Description must be under 500 characters' });
        }

        const typePrompt = PUZZLE_TYPE_PROMPTS[puzzleType] || PUZZLE_TYPE_PROMPTS[puzzleType.toLowerCase()]
            || `A ${puzzleType.replace(/_/g, ' ')} puzzle game. Create an engaging, fun version with score tracking, multiple rounds, and polished UI.`;

        // Subscribers bypass all limits
        const isSubscriber = await isSubscribedUser(userId);

        if (!isSubscriber) {
            const lifetimeCount = await getLifetimeGenerations(userId);
            if (lifetimeCount >= FREE_LIFETIME_GENERATIONS) {
                return res.status(403).json({
                    error: `You've used your ${FREE_LIFETIME_GENERATIONS} free generations. Subscribe to continue creating games!`,
                    requiresSubscription: true,
                    freeTrialAvailable: true,
                    lifetimeUsed: lifetimeCount,
                    lifetimeLimit: FREE_LIFETIME_GENERATIONS
                });
            }
        }

        if (!isSubscriber && !checkRateLimit(userId)) {
            // Allow coin bypass
            if (req.body.useCoins && userId) {
                const { data: spendResult, error: spendError } = await supabase.rpc('spend_coins', {
                    p_user_id: userId,
                    p_amount: GENERATION_COIN_COST,
                    p_reason: 'game_generation',
                    p_game_id: null,
                    p_creator_id: null,
                    p_platform: null
                });
                if (spendError || !spendResult.success) {
                    return res.status(402).json({
                        error: 'Insufficient coins',
                        balance: spendResult?.balance || 0,
                        coinCost: GENERATION_COIN_COST,
                        canUseCoins: true
                    });
                }
                console.log(`[remix] Rate limit bypassed with ${GENERATION_COIN_COST} coins for user ${userId}`);
            } else {
                return res.status(429).json({
                    error: `You've used all ${MAX_GENERATIONS_PER_HOUR} free generations this hour`,
                    canUseCoins: true,
                    coinCost: GENERATION_COIN_COST
                });
            }
        }

        const userName = req.body.userName || 'Player';
        const gameTitle = (title && title.trim()) || `My ${puzzleType.charAt(0).toUpperCase() + puzzleType.slice(1)} Game`;

        console.log(`[remix] User ${userId}: type=${puzzleType}, diff=${difficulty}, desc="${remixDescription.substring(0, 80)}"`);

        // Build the remix prompt for the game worker
        const prompt = `Create a standalone web game (HTML/JS/CSS in a single file) based on this game type:

GAME TYPE: ${typePrompt}
DIFFICULTY: ${difficulty || 'Medium'}
USER CUSTOMIZATIONS: ${remixDescription.trim()}
TITLE: ${gameTitle}

Requirements:
- Complete, playable game that works in a mobile WebView
- No external dependencies (everything inline)
- Responsive design using viewport units (100vh, 100vw)
- Dark theme with #1A1A2E background, white text, orange accents
- Touch-friendly controls (minimum 44px tap targets)
- Score tracking with points displayed prominently
- Game over detection — when the game ends, call:
  window.webkit?.messageHandlers?.gameHandler?.postMessage(JSON.stringify({type:'gameOver',score:finalScore}));
  if (window.AndroidBridge) window.AndroidBridge.gameOver(finalScore);
- Include at least 5 rounds/questions/levels
- Polish: animations, transitions, sound effects via Web Audio API
- The game should be fun, engaging, and feel complete`;

        // Set up SSE streaming
        res.setHeader('Content-Type', 'text/event-stream');
        res.setHeader('Cache-Control', 'no-cache');
        res.setHeader('Connection', 'keep-alive');
        res.setHeader('X-Accel-Buffering', 'no');
        res.flushHeaders();

        const heartbeatInterval = setInterval(() => {
            try { res.write(':heartbeat\n\n'); } catch {}
        }, 15000);
        res.on('close', () => clearInterval(heartbeatInterval));

        // Proxy to game worker
        const workerResponse = await fetch(`${GAME_WORKER_URL}/generate`, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
                'x-worker-secret': GAME_WORKER_SECRET
            },
            body: JSON.stringify({ prompt, userId, stream: true }),
            signal: AbortSignal.timeout(600000)
        });

        if (!workerResponse.ok) {
            let errorMsg = `Build server error (${workerResponse.status})`;
            try {
                if (workerResponse.headers.get('content-type')?.includes('application/json')) {
                    const err = await workerResponse.json();
                    errorMsg = err.error || errorMsg;
                    if (err.quotaExhausted) {
                        res.write(`data: ${JSON.stringify({ type: 'error', error: errorMsg, quotaExhausted: true, resetTime: err.resetTime })}\n\n`);
                        res.end();
                        return;
                    }
                }
            } catch {}
            res.write(`data: ${JSON.stringify({ type: 'error', error: errorMsg })}\n\n`);
            res.end();
            return;
        }

        if (!workerResponse.body) {
            res.write(`data: ${JSON.stringify({ type: 'error', error: 'Build server connection failed' })}\n\n`);
            res.end();
            return;
        }

        // Stream SSE events from worker to client
        const reader = workerResponse.body.getReader();
        const decoder = new TextDecoder();
        let buffer = '';
        let resultReceived = false;

        try {
            while (true) {
                const { done, value } = await reader.read();
                if (done) break;

                buffer += decoder.decode(value, { stream: true });
                const lines = buffer.split('\n');
                buffer = lines.pop() || '';

                for (const line of lines) {
                    if (line.startsWith('data: ')) {
                        const eventData = line.substring(6);
                        try {
                            const parsed = JSON.parse(eventData);

                            if (parsed.type === 'status') {
                                res.write(`data: ${JSON.stringify({
                                    type: 'status',
                                    phase: parsed.phase,
                                    message: parsed.message,
                                    detail: parsed.detail,
                                    progressPercent: parsed.progressPercent,
                                    estimatedSecondsRemaining: parsed.estimatedSecondsRemaining
                                })}\n\n`);
                            } else if (parsed.type === 'result') {
                                resultReceived = true;
                                recordGeneration(userId);

                                // Auto-save the remixed game
                                const gameId = crypto.randomUUID();
                                const remixCriticalCount = parsed.quality?.criticalIssues ?? 0;
                                const { error: dbError } = await supabase
                                    .from('custom_games')
                                    .insert({
                                        id: gameId,
                                        title: gameTitle,
                                        description: `Remixed ${puzzleType} game: ${remixDescription.trim().substring(0, 200)}`,
                                        html_content: parsed.bundle,
                                        creator_id: userId,
                                        creator_name: userName,
                                        game_type: 'ai_generated',
                                        platform_type: 'webview',
                                        initial_prompt: prompt.substring(0, 2000),
                                        play_count: 0,
                                        rating: 0,
                                        critical_issues: remixCriticalCount,
                                        status: remixCriticalCount === 0 ? 'published' : 'draft'
                                    });

                                if (dbError) {
                                    console.error(`[remix] DB save error:`, dbError.message);
                                }

                                console.log(`[remix] Complete: ${gameId} (type=${puzzleType})`);

                                // Refresh browse cache
                                refreshBrowseCache();

                                res.write(`data: ${JSON.stringify({
                                    type: 'result',
                                    success: true,
                                    gameId,
                                    bundle: parsed.bundle,
                                    bundleSize: parsed.bundleSize,
                                    generationTime: parsed.generationTime
                                })}\n\n`);
                            } else if (parsed.type === 'error') {
                                res.write(`data: ${JSON.stringify({ type: 'error', error: parsed.error })}\n\n`);
                            }
                        } catch {}
                    }
                }
            }
        } catch (streamError) {
            if (!resultReceived) {
                res.write(`data: ${JSON.stringify({ type: 'error', error: 'Connection to build server lost' })}\n\n`);
            }
        }

        res.end();

    } catch (error) {
        console.error('[remix] Error:', error.message);
        if (res.headersSent) {
            res.write(`data: ${JSON.stringify({ type: 'error', error: 'Server error during remix' })}\n\n`);
            res.end();
        } else {
            if (error.name === 'TimeoutError') {
                return res.status(504).json({ error: 'Game generation timed out. Try a simpler description.' });
            }
            res.status(500).json({ error: 'Failed to create remixed game' });
        }
    }
});

// Helper: Extract HTML from base64 content (may be raw HTML or zip)
function extractHtmlFromBundle(base64Content) {
    try {
        const buffer = Buffer.from(base64Content, 'base64');
        const str = buffer.toString('utf-8');

        // Check if it's raw HTML
        if (str.trim().startsWith('<!DOCTYPE') || str.trim().startsWith('<html') || str.trim().startsWith('<HTML')) {
            return str;
        }

        // Try to unzip - look for index.html in the zip
        // Simple zip parsing (local file headers)
        const files = {};
        let offset = 0;

        while (offset < buffer.length - 30) {
            // Local file header signature
            if (buffer.readUInt32LE(offset) !== 0x04034b50) break;

            const compMethod = buffer.readUInt16LE(offset + 8);
            const compSize = buffer.readUInt32LE(offset + 18);
            const uncompSize = buffer.readUInt32LE(offset + 22);
            const nameLen = buffer.readUInt16LE(offset + 26);
            const extraLen = buffer.readUInt16LE(offset + 28);
            const fileName = buffer.slice(offset + 30, offset + 30 + nameLen).toString('utf-8');
            const dataStart = offset + 30 + nameLen + extraLen;
            const compData = buffer.slice(dataStart, dataStart + compSize);

            if (!fileName.endsWith('/')) {
                let fileData;
                if (compMethod === 8) {
                    // Deflate
                    try {
                        fileData = zlib.inflateRawSync(compData);
                    } catch {
                        fileData = compData;
                    }
                } else {
                    fileData = compData;
                }

                // Normalize filename (strip common folder prefix)
                const normalizedName = fileName.replace(/^[^\/]+\//, '');
                if (normalizedName) {
                    files[normalizedName] = fileData.toString('utf-8');
                }
            }

            offset = dataStart + compSize;
        }

        // Look for index.html
        if (files['index.html']) return files['index.html'];

        // Try original filename
        const indexKey = Object.keys(files).find(k => k.endsWith('index.html'));
        if (indexKey) return files[indexKey];

        // Fallback: return the raw string if it looks like HTML
        if (str.includes('<body') || str.includes('<div')) return str;

        return null;
    } catch (err) {
        console.error('[extractHtml] Error:', err.message);
        return null;
    }
}

// Generate the leaderboard snippet to inject
function generateLeaderboardSnippet(gameId, chatId, apiBase) {
    return `
<script>
(function() {
  var GAME_ID = '${gameId}';
  var CHAT_ID = '${chatId}';
  var API_BASE = '${apiBase}';
  var tgUser = window.Telegram && window.Telegram.WebApp && window.Telegram.WebApp.initDataUnsafe && window.Telegram.WebApp.initDataUnsafe.user;
  var userId = tgUser ? String(tgUser.id) : ('guest_' + Math.random().toString(36).substr(2,8));
  var username = tgUser ? (tgUser.first_name || tgUser.username || 'Player') : 'Player';
  window.RV = {
    submitScore: function(score) {
      fetch(API_BASE + '/api/chat-scores', {
        method: 'POST',
        headers: {'Content-Type':'application/json'},
        body: JSON.stringify({gameId:GAME_ID,chatId:CHAT_ID,platform:'telegram',userId:userId,username:username,score:score})
      }).then(function(r){return r.json()}).then(function(d){
        if(d.leaderboard) window.RV._render(d.leaderboard);
      }).catch(function(){});
    },
    _render: function(scores) {
      var el = document.getElementById('rv-lb-list');
      if(!el) return;
      el.innerHTML = scores.map(function(s,i){
        return '<div style="display:flex;align-items:center;gap:8px;padding:5px 0;border-bottom:1px solid rgba(255,255,255,0.1)">'
          +'<span style="width:18px;font-weight:bold;color:'+(i===0?'#FFD700':i===1?'#C0C0C0':i===2?'#CD7F32':'#aaa')+'">'+(i+1)+'.</span>'
          +'<span style="flex:1">'+(s.username||'Player')+(s.userId===userId?' <b>(you)</b>':'')+'</span>'
          +'<span style="font-weight:bold">'+s.score+'</span></div>';
      }).join('');
    },
    _poll: function() {
      fetch(API_BASE+'/api/chat-scores/'+GAME_ID+'/'+CHAT_ID)
        .then(function(r){return r.json()})
        .then(function(d){if(d.scores)window.RV._render(d.scores)})
        .catch(function(){});
    }
  };
  document.addEventListener('DOMContentLoaded', function() {
    var lb = document.createElement('div');
    lb.id = 'rv-leaderboard';
    lb.style.cssText = 'position:fixed;top:10px;right:10px;background:rgba(20,20,30,0.92);backdrop-filter:blur(10px);color:white;border-radius:12px;padding:12px 16px;width:210px;font-family:system-ui,sans-serif;font-size:12px;z-index:99999;box-shadow:0 4px 20px rgba(0,0,0,0.5)';
    lb.innerHTML = '<div style="display:flex;justify-content:space-between;align-items:center;margin-bottom:8px"><span style="font-weight:bold;font-size:13px">\\u{1F3C6} Group</span><button onclick="document.getElementById(\\'rv-leaderboard\\').style.display=\\'none\\'" style="background:none;border:none;color:#aaa;font-size:16px;cursor:pointer;padding:0">\\u00D7</button></div><div id="rv-lb-list"><div style="color:#aaa;text-align:center;padding:8px 0">Be the first to score!</div></div>';
    document.body.appendChild(lb);
    window.RV._poll();
    setInterval(window.RV._poll, 15000);
  });
})();
</script>`;
}

// GET /api/game-creation/:gameId
// Returns game metadata + base64 bundle for play
// When chatId and platform query params are present, serves HTML directly with leaderboard injected
router.get('/:gameId', async (req, res) => {
    try {
        const { gameId } = req.params;
        const { chatId, platform } = req.query;

        const { data, error } = await supabase
            .from('custom_games')
            .select('id, title, description, html_content, creator_name, play_count, rating, created_at')
            .eq('id', gameId)
            .single();

        if (error) throw error;
        if (!data) return res.status(404).json({ error: 'Game not found' });

        // Increment play count
        await supabase
            .from('custom_games')
            .update({ play_count: (data.play_count || 0) + 1 })
            .eq('id', gameId);

        // If chatId and platform are provided, serve HTML directly with leaderboard
        if (chatId && platform) {
            const html = extractHtmlFromBundle(data.html_content);

            if (!html) {
                return res.status(500).send('<html><body style="background:#1a1a2e;color:#fff;display:flex;align-items:center;justify-content:center;height:100vh;font-family:system-ui"><h1>Could not load game</h1></body></html>');
            }

            // Inject leaderboard snippet before </body>
            const snippet = generateLeaderboardSnippet(gameId, chatId, API_BASE_URL);
            let modifiedHtml = html;

            if (html.includes('</body>')) {
                modifiedHtml = html.replace('</body>', snippet + '</body>');
            } else if (html.includes('</html>')) {
                modifiedHtml = html.replace('</html>', snippet + '</html>');
            } else {
                modifiedHtml = html + snippet;
            }

            res.setHeader('Content-Type', 'text/html; charset=utf-8');
            res.setHeader('Cache-Control', 'no-cache');
            return res.send(modifiedHtml);
        }

        // Standard JSON response
        res.json({
            success: true,
            game: {
                id: data.id,
                title: data.title,
                description: data.description,
                bundle: data.html_content, // base64 zip
                creatorName: data.creator_name,
                playCount: data.play_count,
                rating: data.rating,
                createdAt: data.created_at
            }
        });

    } catch (error) {
        console.error('[fetch] Error:', error.message);
        res.status(500).json({ error: 'Failed to fetch game' });
    }
});

// DELETE /:gameId — creator deletes their own game
router.delete('/:gameId', async (req, res) => {
    try {
        const { gameId } = req.params;
        const { userId } = req.query;

        if (!gameId || !userId) {
            return res.status(400).json({ error: 'gameId and userId are required' });
        }

        // Verify ownership
        const { data: game, error: fetchError } = await supabase
            .from('custom_games')
            .select('id, creator_id')
            .eq('id', gameId)
            .single();

        if (fetchError || !game) {
            return res.status(404).json({ error: 'Game not found' });
        }

        if (game.creator_id !== userId) {
            return res.status(403).json({ error: 'Not authorized to delete this game' });
        }

        const { error: deleteError } = await supabase
            .from('custom_games')
            .delete()
            .eq('id', gameId);

        if (deleteError) throw deleteError;

        console.log(`[delete] Game ${gameId} deleted by ${userId}`);
        res.json({ success: true });

    } catch (error) {
        console.error('[delete] Error:', error.message);
        res.status(500).json({ error: 'Failed to delete game' });
    }
});

export default router;
