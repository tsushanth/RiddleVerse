// server.js - Main entry point (refactored)
import express from "express";
import dotenv from "dotenv";
import cors from "cors";
import path from "path";

// Load environment variables first
dotenv.config();

// Startup validation and error notification services
import { runStartupValidation } from './services/startupValidation.js';
import {
    errorReportingMiddleware,
    silenceError,
    unsilenceError,
    getSilencedErrors,
    clearAllSilences
} from './services/errorNotificationService.js';

// Route imports
import puzzleRoutes from "./routes/puzzleRoutes.js";
import dailyPuzzleCacheRoutes from './routes/dailyPuzzleCache.routes.js';
import ltvRoutes from './routes/ltv.routes.js';
import gamesRoutes from './routes/games.routes.js';
import userConfigRoutes from './routes/simpleUserConfig.js';
import adminRoutes from './routes/admin.routes.js';
import analyticsRoutes from './routes/analytics.routes.js';
import authRoutes from './routes/auth.routes.js';
import dailyPuzzlesRoutes from './routes/dailyPuzzles.routes.js';

// New refactored routes
import accountRoutes from './routes/account.routes.js';
import subscriptionRoutes from './routes/subscription.routes.js';
import streakNotificationsRoutes from './routes/streakNotifications.routes.js';
import versionRoutes from './routes/version.routes.js';
import puzzleEndpointsRoutes from './routes/puzzleEndpoints.routes.js';
import llmRoutes from './routes/llm.routes.js';
import scoringRoutes from './routes/scoring.routes.js';
import sharingRoutes from './routes/sharing.routes.js';
import feedbackRoutes from './routes/feedback.routes.js';
import notificationConfigRoutes from './routes/notificationConfig.routes.js';
import generationRoutes from './routes/generation.routes.js';
import gameCreationRoutes from './routes/gameCreation.routes.js';
import chatScoresRoutes from './routes/chatScores.routes.js';
import leaderboardRoutes from './routes/leaderboard.routes.js';
import coinsRoutes from './routes/coins.routes.js';
import payoutRoutes from './routes/payout.routes.js';
import { handleStripeWebhook } from './services/payoutService.js';

// Service imports
import { initializeDailyPuzzleSystem } from './services/dailyPuzzleCacheSetup.js';
import { firebaseStreakNotificationService } from './services/firebaseStreakNotificationService.js';
import { remixDigestService } from './services/remixDigestService.js';
import { wordFrequencyManager } from './services/wordFrequencyManager.js';
import { errorHandler } from './middleware/errorHandler.js';

// New simple daily puzzle system (in-memory, no Redis)
import { refreshDailyPuzzles } from './services/dailyPuzzles.js';
import dailyPuzzlesBulkRoutes from './routes/dailyPuzzlesBulk.routes.js';
import cron from 'node-cron';

const __dirname = path.resolve();

// Health check cache
let lastHealthCheck = null;
let healthCheckInProgress = false;
const HEALTH_CHECK_CACHE_DURATION = 5 * 60 * 1000;

const app = express();
app.set('trust proxy', 1);
app.use(cors());

// Stripe webhook must be before express.json() to get raw body for signature verification
app.post('/api/payouts/webhook', express.raw({ type: 'application/json' }), async (req, res) => {
    await handleStripeWebhook(req, res);
});

app.use(express.json({ limit: '50mb' }));
app.use(express.urlencoded({ limit: '50mb', extended: true }));

// Security middleware
app.use((req, res, next) => {
    const userAgent = req.headers['user-agent'] || '';
    if (userAgent.includes('ALittle Client')) {
        return res.status(403).json({ error: 'Forbidden' });
    }

    const blockedPaths = [
        '/server/php/index.php',
        '/admin/server/php',
        '/sites/all/libraries/elfinder'
    ];
    if (blockedPaths.some(path => req.path.includes(path))) {
        return res.status(403).json({ error: 'Forbidden' });
    }

    res.setHeader('X-RateLimit-Limit', '100');
    res.setHeader('X-RateLimit-Remaining', '99');

    next();
});

// Initialize streak notification service
async function initializeStreakNotificationService() {
    try {
        firebaseStreakNotificationService.startPeriodicService(60);

        setTimeout(async () => {
            try {
                await firebaseStreakNotificationService.runPeriodicCheck();
            } catch (error) {
                console.error('Initial streak service check failed:', error.message);
            }
        }, 30000);
    } catch (error) {
        console.error('Failed to initialize streak notification service:', error.message);
    }
}

// Server initialization
async function initializeServer() {
    try {
        await wordFrequencyManager.load();
        await initializeStreakNotificationService();
        remixDigestService.start();
        const cacheInitResult = await initializeDailyPuzzleSystem();

        if (!cacheInitResult.success) {
            console.warn('Daily puzzle cache failed to initialize:', cacheInitResult.error);
        }

        // --- New in-memory daily puzzle cache ---
        // Warm cache on startup (run in background so server starts fast)
        setTimeout(async () => {
            try {
                console.log('[DailyPuzzles] Warming cache on startup...');
                await refreshDailyPuzzles();
            } catch (err) {
                console.error('[DailyPuzzles] Startup warm failed:', err.message);
            }
        }, 10000); // Wait 10s for other services to settle

        // Daily cron at 2:00 AM UTC
        cron.schedule('0 2 * * *', async () => {
            try {
                console.log('[DailyPuzzles] Running daily 2AM UTC refresh...');
                await refreshDailyPuzzles();
            } catch (err) {
                console.error('[DailyPuzzles] Cron refresh failed:', err.message);
            }
        }, { timezone: 'UTC' });

    } catch (error) {
        console.error('Server initialization error:', error.message);
    }
}

// Health check endpoints
app.get('/api/health', (req, res) => {
    if (lastHealthCheck && (Date.now() - lastHealthCheck.timestamp) < HEALTH_CHECK_CACHE_DURATION) {
        const statusCode = lastHealthCheck.healthy ? 200 : 503;
        return res.status(statusCode).json({
            healthy: lastHealthCheck.healthy,
            status: lastHealthCheck.status,
            message: lastHealthCheck.message,
            lastFullCheck: new Date(lastHealthCheck.timestamp).toISOString(),
            uptime: process.uptime()
        });
    }

    res.status(200).json({
        healthy: true,
        status: 'basic_healthy',
        message: 'Server is running (full check not completed)',
        uptime: process.uptime(),
        timestamp: new Date().toISOString()
    });
});

app.get('/api/health/detailed', async (req, res) => {
    try {
        const detailed = {
            server: {
                uptime: process.uptime(),
                memory: process.memoryUsage(),
                nodeVersion: process.version,
                platform: process.platform
            },
            lastHealthCheck: lastHealthCheck ? {
                timestamp: new Date(lastHealthCheck.timestamp).toISOString(),
                healthy: lastHealthCheck.healthy,
                status: lastHealthCheck.status,
                summary: lastHealthCheck.summary
            } : null,
            cache: {
                healthCheckInProgress,
                cacheAge: lastHealthCheck ? Date.now() - lastHealthCheck.timestamp : null
            }
        };

        res.json(detailed);
    } catch (error) {
        res.status(500).json({
            error: 'Failed to get detailed health status',
            message: error.message
        });
    }
});

// Mount route modules
app.use('/', accountRoutes);
app.use('/', subscriptionRoutes);
app.use('/api', subscriptionRoutes); // Also mount at /api for mobile app compatibility
app.use('/api/streak-notifications', streakNotificationsRoutes);
app.use('/api', versionRoutes);
app.use('/', puzzleEndpointsRoutes);
app.use('/api', puzzleEndpointsRoutes);
app.use('/', llmRoutes);
app.use('/', scoringRoutes);
app.use('/', sharingRoutes);
app.use('/', feedbackRoutes);
app.use('/api/notifications', notificationConfigRoutes);
app.use('/', notificationConfigRoutes);
app.use('/', generationRoutes);

// Existing route modules
app.use('/api/ltv', ltvRoutes);
app.use('/api/daily-puzzle-cache', dailyPuzzleCacheRoutes);
app.use('/api/games', gamesRoutes);
app.use('/api/game-creation', gameCreationRoutes);
app.use('/api/chat-scores', chatScoresRoutes);
app.use('/api/coins', coinsRoutes);
app.use('/api/payouts', payoutRoutes);
app.use('/api/leaderboard', leaderboardRoutes);
app.use('/api', userConfigRoutes);
app.use('/', adminRoutes);
app.use('/api/analytics', analyticsRoutes);
app.use('/auth', authRoutes);
app.use('/api/daily-puzzles', dailyPuzzlesRoutes);
app.use("/api/puzzles", puzzleRoutes);
app.use("/api/puzzles", dailyPuzzlesBulkRoutes); // GET /api/puzzles/daily

// Legacy endpoint (DO NOT REMOVE)
app.post("/api/puzzles/check-answer", async (req, res) => {
    const { checkAnswer } = await import('./services/puzzleService.js');
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

// Apple and Google app association files
app.get('/.well-known/apple-app-site-association', (req, res) => {
    res.setHeader('Content-Type', 'application/json');
    res.sendFile(path.join(__dirname, 'public', '.well-known', 'apple-app-site-association'));
});

app.get('/.well-known/assetlinks.json', (req, res) => {
    res.setHeader('Content-Type', 'application/json');
    res.setHeader('Access-Control-Allow-Origin', '*');
    res.setHeader('Access-Control-Allow-Methods', 'GET');
    res.setHeader('Access-Control-Allow-Headers', 'Content-Type');
    res.sendFile(path.join(__dirname, 'public', '.well-known', 'assetlinks.json'));
});

// Static files
app.use(express.static(path.join(__dirname, "public")));
app.use(cors({ origin: "https://quiz-web-frontend-917362189743.us-central1.run.app" }));

app.get('/download', (req, res) => {
    res.sendFile(path.join(__dirname, 'public', 'download.html'));
});

app.get("/app-ads.txt", (req, res) => {
    res.sendFile(path.join(__dirname, "public", "app-ads.txt"));
});

// Error reporting middleware (sends email notifications for 500 errors)
app.use(errorReportingMiddleware);

// Error handling
app.use(errorHandler);

// Admin endpoints for error silencing
app.post('/api/admin/silence-error', (req, res) => {
    const adminKey = req.headers['x-admin-key'];
    if (adminKey !== process.env.ADMIN_KEY) {
        return res.status(401).json({ error: 'Unauthorized' });
    }

    const { fingerprint, pattern, durationMinutes = 1440, isPattern = false } = req.body;
    const target = fingerprint || pattern;

    if (!target) {
        return res.status(400).json({ error: 'fingerprint or pattern required' });
    }

    silenceError(target, durationMinutes * 60 * 1000, isPattern);
    res.json({ success: true, silenced: target, durationMinutes });
});

app.post('/api/admin/unsilence-error', (req, res) => {
    const adminKey = req.headers['x-admin-key'];
    if (adminKey !== process.env.ADMIN_KEY) {
        return res.status(401).json({ error: 'Unauthorized' });
    }

    const { fingerprint, pattern } = req.body;
    const target = fingerprint || pattern;

    if (!target) {
        return res.status(400).json({ error: 'fingerprint or pattern required' });
    }

    const removed = unsilenceError(target);
    res.json({ success: removed, message: removed ? 'Unsilenced' : 'Pattern not found' });
});

app.get('/api/admin/silenced-errors', (req, res) => {
    const adminKey = req.headers['x-admin-key'];
    if (adminKey !== process.env.ADMIN_KEY) {
        return res.status(401).json({ error: 'Unauthorized' });
    }

    res.json({ silencedErrors: getSilencedErrors() });
});

app.post('/api/admin/clear-silences', (req, res) => {
    const adminKey = req.headers['x-admin-key'];
    if (adminKey !== process.env.ADMIN_KEY) {
        return res.status(401).json({ error: 'Unauthorized' });
    }

    clearAllSilences();
    res.json({ success: true, message: 'All error silences cleared' });
});

// ---------------------------------------------------------------------------
// GET /play/:gameId — Serve a game as a standalone playable page
// No auth required — anyone with the link can play
// ---------------------------------------------------------------------------
import { createRequire } from 'module';
import zlib from 'zlib';

// In-memory game file cache: gameId → { files: { 'js/game.js': Buffer, ... }, ts: number }
const gameFileCache = new Map();
const GAME_CACHE_TTL = 30 * 60 * 1000; // 30 minutes
const MAX_CACHED_GAMES = 50; // Evict oldest when exceeded

async function getGameFiles(gameId) {
    const cached = gameFileCache.get(gameId);
    if (cached && Date.now() - cached.ts < GAME_CACHE_TTL) return cached.files;

    const { supabase: sb } = await import('./config/database.js');
    const { data, error } = await sb
        .from('custom_games')
        .select('html_content')
        .eq('id', gameId)
        .single();

    if (error || !data?.html_content) return null;

    const zipBuffer = Buffer.from(data.html_content, 'base64');
    const files = {};

    let eocdOffset = -1;
    for (let i = zipBuffer.length - 22; i >= 0; i--) {
        if (zipBuffer.readUInt32LE(i) === 0x06054b50) { eocdOffset = i; break; }
    }
    if (eocdOffset === -1) return null;

    const centralDirOffset = zipBuffer.readUInt32LE(eocdOffset + 16);
    const totalEntries = zipBuffer.readUInt16LE(eocdOffset + 10);
    let offset = centralDirOffset;

    // Detect common root folder
    const allNames = [];
    let tempOffset = centralDirOffset;
    for (let i = 0; i < totalEntries; i++) {
        if (zipBuffer.readUInt32LE(tempOffset) !== 0x02014b50) break;
        const nl = zipBuffer.readUInt16LE(tempOffset + 28);
        const el = zipBuffer.readUInt16LE(tempOffset + 30);
        const cl = zipBuffer.readUInt16LE(tempOffset + 32);
        allNames.push(zipBuffer.slice(tempOffset + 46, tempOffset + 46 + nl).toString('utf-8'));
        tempOffset += 46 + nl + el + cl;
    }
    // If all files share a common folder prefix, strip it
    let stripPrefix = '';
    if (allNames.length > 0 && allNames.every(n => n.includes('/'))) {
        const firstFolder = allNames[0].split('/')[0] + '/';
        if (allNames.every(n => n.startsWith(firstFolder))) {
            stripPrefix = firstFolder;
        }
    }

    for (let i = 0; i < totalEntries; i++) {
        if (zipBuffer.readUInt32LE(offset) !== 0x02014b50) break;
        const nameLen = zipBuffer.readUInt16LE(offset + 28);
        const extraLen = zipBuffer.readUInt16LE(offset + 30);
        const commentLen = zipBuffer.readUInt16LE(offset + 32);
        const localHeaderOffset = zipBuffer.readUInt32LE(offset + 42);
        let fileName = zipBuffer.slice(offset + 46, offset + 46 + nameLen).toString('utf-8');

        if (!fileName.endsWith('/') && !fileName.includes('CLAUDE.md') && !fileName.startsWith('.claude')) {
            const lh = localHeaderOffset;
            const lhCompMethod = zipBuffer.readUInt16LE(lh + 8);
            const compSize = zipBuffer.readUInt32LE(lh + 18);
            const lhNameLen = zipBuffer.readUInt16LE(lh + 26);
            const lhExtraLen = zipBuffer.readUInt16LE(lh + 28);
            const dataStart = lh + 30 + lhNameLen + lhExtraLen;
            const compData = zipBuffer.slice(dataStart, dataStart + compSize);

            let fileData = lhCompMethod === 8 ? zlib.inflateRawSync(compData) : compData;

            // Strip common prefix
            if (stripPrefix && fileName.startsWith(stripPrefix)) {
                fileName = fileName.slice(stripPrefix.length);
            }
            if (fileName) files[fileName] = fileData;
        }
        offset += 46 + nameLen + extraLen + commentLen;
    }

    // Evict oldest if cache is full
    if (gameFileCache.size >= MAX_CACHED_GAMES) {
        let oldestKey = null, oldestTs = Infinity;
        for (const [k, v] of gameFileCache) {
            if (v.ts < oldestTs) { oldestTs = v.ts; oldestKey = k; }
        }
        if (oldestKey) gameFileCache.delete(oldestKey);
    }

    gameFileCache.set(gameId, { files, ts: Date.now() });
    console.log(`[play] Cached game ${gameId}: ${Object.keys(files).length} files (${Object.keys(files).join(', ')}) [cache: ${gameFileCache.size}/${MAX_CACHED_GAMES}]`);

    // Also persist to storage bucket for long-term serving (fire-and-forget)
    const { supabase: sb2 } = await import('./config/database.js');
    for (const [name, data] of Object.entries(files)) {
        const ext = name.split('.').pop().toLowerCase();
        const mime = { 'html': 'text/html', 'js': 'application/javascript', 'css': 'text/css', 'json': 'application/json', 'svg': 'image/svg+xml', 'png': 'image/png' };
        sb2.storage.from('game-bundles').upload(`games/${gameId}/${name}`, data, { contentType: mime[ext] || 'application/octet-stream', upsert: true }).catch(() => {});
    }
    return files;
}

// Serve game assets (JS, CSS, images) from in-memory cache
app.get('/play/:gameId/*', async (req, res) => {
    try {
        const { gameId } = req.params;
        const filePath = req.params[0];

        let files = await getGameFiles(gameId);
        if (!files || !files[filePath]) {
            // Fallback: try storage bucket
            try {
                const { supabase: sb } = await import('./config/database.js');
                const { data } = await sb.storage.from('game-bundles').download(`games/${gameId}/${filePath}`);
                if (data) {
                    const ext = filePath.split('.').pop().toLowerCase();
                    const mimeTypes = { 'js': 'application/javascript', 'css': 'text/css', 'html': 'text/html', 'json': 'application/json', 'svg': 'image/svg+xml', 'png': 'image/png' };
                    res.setHeader('Content-Type', mimeTypes[ext] || 'application/octet-stream');
                    res.setHeader('Cache-Control', 'public, max-age=3600');
                    return res.send(Buffer.from(await data.arrayBuffer()));
                }
            } catch {}
            return res.status(404).send('File not found');
        }

        const ext = filePath.split('.').pop().toLowerCase();
        const mimeTypes = {
            'js': 'application/javascript', 'css': 'text/css', 'html': 'text/html',
            'json': 'application/json', 'svg': 'image/svg+xml', 'png': 'image/png',
            'jpg': 'image/jpeg', 'jpeg': 'image/jpeg', 'gif': 'image/gif',
            'woff': 'font/woff', 'woff2': 'font/woff2', 'mp3': 'audio/mpeg'
        };
        res.setHeader('Content-Type', mimeTypes[ext] || 'application/octet-stream');
        res.setHeader('Cache-Control', 'public, max-age=3600');
        res.send(files[filePath]);
    } catch (error) {
        console.error('[play-asset] Error:', error.message);
        res.status(500).send('Error loading asset');
    }
});

app.get('/play/:gameId', async (req, res) => {
    try {
        const { gameId } = req.params;
        const userId = req.query.userId || 'anonymous';

        // Fetch game files + creator_id for revenue sharing
        const { supabase: sb } = await import('./config/database.js');
        const { data: gameMeta } = await sb
            .from('custom_games')
            .select('creator_id')
            .eq('id', gameId)
            .single();
        const creatorId = gameMeta?.creator_id || '';

        const files = await getGameFiles(gameId);
        if (!files || !files['index.html']) {
            return res.status(404).send('<html><body style="background:#1a1a2e;color:#fff;display:flex;align-items:center;justify-content:center;height:100vh;font-family:system-ui"><h1>Game not found</h1></body></html>');
        }

        let html = files['index.html'].toString('utf-8');

        // Inject <base> tag so relative asset paths resolve correctly regardless of trailing slash
        const baseTag = `<base href="/play/${gameId}/">`;
        if (html.includes('<head>')) {
            html = html.replace('<head>', '<head>' + baseTag);
        } else {
            html = baseTag + html;
        }

        if (!html) {
            return res.status(500).send('<html><body style="background:#1a1a2e;color:#fff;display:flex;align-items:center;justify-content:center;height:100vh;font-family:system-ui"><h1>Could not load game</h1></body></html>');
        }

        // Inject minimal score bridge in <head> — non-destructive, doesn't override game code
        const headScript = `<script>
window.__VB_GAME_ID='${gameId}';window.__VB_USER_ID='${userId}';window.__VB_CREATOR_ID='${creatorId}';window.__VB_ENDED=false;
window.AndroidBridge={postScore:function(){},gameOver:function(s){if(!window.__VB_ENDED){window.__VB_ENDED=true;window.__VB_SUBMIT(s)}},reportError:function(){}};
window.webkit=window.webkit||{};window.webkit.messageHandlers=window.webkit.messageHandlers||{};
window.webkit.messageHandlers.gameScore={postMessage:function(d){if(d.gameOver&&!window.__VB_ENDED){window.__VB_ENDED=true;window.__VB_SUBMIT(d.score)}}};
window.__VB_SUBMIT=function(score){
fetch('/api/game-score/'+window.__VB_GAME_ID,{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({userId:window.__VB_USER_ID,score:score})}).catch(function(){});
setTimeout(function(){
  var dl='riddleverse://game-complete?gameId='+window.__VB_GAME_ID+'&score='+score+'&userId='+window.__VB_USER_ID+'&creatorId='+window.__VB_CREATOR_ID;
  var o=document.createElement('div');
  o.id='__vb_game_over';
  o.style.cssText='position:fixed;inset:0;z-index:999999;display:flex;align-items:center;justify-content:center;background:rgba(0,0,0,0.75);font-family:system-ui';
  o.innerHTML='<div style="background:#1a1a2e;border-radius:24px;padding:32px 28px;text-align:center;max-width:340px;width:90%;box-shadow:0 8px 32px rgba(0,0,0,0.5)">'
    +'<div style="font-size:48px;margin-bottom:8px">🏆</div>'
    +'<div style="color:#fff;font-size:22px;font-weight:700;margin-bottom:4px">Game Complete!</div>'
    +'<div style="color:rgba(255,255,255,0.5);font-size:13px;margin-bottom:12px">Score</div>'
    +'<div style="color:#fff;font-size:52px;font-weight:800;margin-bottom:24px">'+score+'</div>'
    +'<a href="'+dl+'&action=playAgain" style="display:block;background:#6C63FF;color:#fff;padding:14px;border-radius:14px;text-decoration:none;font-weight:600;font-size:16px;margin-bottom:10px;box-shadow:0 4px 16px rgba(108,99,255,0.4)">🔄 Play Again <span style="background:rgba(255,255,255,0.2);padding:2px 8px;border-radius:8px;font-size:13px;margin-left:6px">🪙 10</span></a>'
    +'<a href="'+dl+'" style="display:block;color:rgba(255,255,255,0.6);padding:12px;text-decoration:none;font-size:15px">Back to Home</a>'
    +'</div>';
  document.body.appendChild(o);
},1500);
};
</script>`;

        // Inject in <head> so bridges exist before game code runs
        if (html.includes('</head>')) {
            html = html.replace('</head>', headScript + '</head>');
        } else if (html.includes('<body')) {
            html = html.replace('<body', headScript + '<body');
        } else {
            html = headScript + html;
        }

        res.setHeader('Content-Type', 'text/html; charset=utf-8');
        res.setHeader('Cache-Control', 'no-cache');
        res.send(html);
    } catch (error) {
        console.error('[play] Error:', error.message);
        res.status(500).send('<html><body style="background:#1a1a2e;color:#fff;display:flex;align-items:center;justify-content:center;height:100vh;font-family:system-ui"><h1>Error loading game</h1></body></html>');
    }
});

// POST /api/game-score/:gameId — Submit score from web game player
app.post('/api/game-score/:gameId', async (req, res) => {
    try {
        const { gameId } = req.params;
        const { userId, score } = req.body;

        if (!score && score !== 0) return res.status(400).json({ error: 'score is required' });

        const { supabase: sb } = await import('./config/database.js');

        // Upsert leaderboard entry
        await sb.from('custom_leaderboard').upsert({
            parentsetid: gameId,
            userid: userId || 'anonymous',
            score: parseInt(score),
            timetaken: 0,
            createdat: new Date().toISOString()
        }, { onConflict: 'parentsetid,userid' });

        console.log(`[game-score] ${gameId}: score=${score} user=${userId}`);
        res.json({ success: true });
    } catch (error) {
        console.error('[game-score] Error:', error.message);
        res.json({ success: true }); // Don't fail the game over a score submission error
    }
});

// Catch-all for SPA
app.get("*", (req, res) => {
    res.sendFile(path.join(__dirname, "public", "index.html"));
});

const PORT = process.env.PORT || 8080;

// Run startup validation then start server
runStartupValidation(false).then((isValid) => {
    if (!isValid) {
        console.warn('⚠️ Server starting with validation warnings - some features may not work');
    }

    app.listen(PORT, () => {
        console.log(`✅ Server running on http://localhost:${PORT}`);
    });

    initializeServer().catch(error => {
        console.error('Background initialization failed:', error.message);
    });
}).catch(error => {
    console.error('Startup validation error:', error.message);
    // Start server anyway but log the error
    app.listen(PORT, () => {
        console.log(`✅ Server running on http://localhost:${PORT} (validation skipped)`);
    });
});
