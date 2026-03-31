// routes/generation.routes.js - Puzzle generation endpoints
import express from 'express';
import { generateCustomPuzzles } from '../services/puzzleService.js';
import { regenerationService } from '../services/regenerationService.js';

const router = express.Router();

/**
 * Helper function to generate puzzles with notification
 */
async function generateCustomPuzzlesWithNotification(topic, format, numPuzzles, userId, puzzleId) {
    try {
        const result = await generateCustomPuzzles(topic, format, numPuzzles, userId, puzzleId, false);

        if (result && result.puzzleId) {
            await regenerationService.sendCompletionNotification(userId, topic, numPuzzles);
        }

        return result;
    } catch (error) {
        console.error(`Puzzle generation error for ${puzzleId}:`, error.message);
        throw error;
    }
}

/**
 * Generate riddle endpoint
 */
router.post('/generate-riddle', async (req, res) => {
    try {
        const { topic, format, numPuzzles, userId } = req.body;

        const clientInfo = {
            ip: req.ip || req.connection.remoteAddress || req.socket.remoteAddress || 'unknown',
            userAgent: req.headers['user-agent'] || 'unknown',
            referer: req.headers.referer || req.headers.referrer || 'direct',
            origin: req.headers.origin || 'unknown',
            xForwardedFor: req.headers['x-forwarded-for'] || 'none',
            xRealIp: req.headers['x-real-ip'] || 'none',
            acceptLanguage: req.headers['accept-language'] || 'unknown',
            contentType: req.headers['content-type'] || 'unknown',
            host: req.headers.host || 'unknown',
            timestamp: new Date().toISOString()
        };

        const ua = clientInfo.userAgent.toLowerCase();
        const clientType = ua.includes('mobile') ? 'mobile' :
            ua.includes('tablet') ? 'tablet' :
                ua.includes('postman') ? 'postman' :
                    ua.includes('curl') ? 'curl' :
                        ua.includes('axios') ? 'axios' :
                            ua.includes('fetch') ? 'fetch' :
                                ua.includes('chrome') ? 'chrome-browser' :
                                    ua.includes('firefox') ? 'firefox-browser' :
                                        ua.includes('safari') ? 'safari-browser' : 'unknown-client';

        if (!topic || !format || !numPuzzles || !userId) {
            return res.status(400).json({ error: "Missing required parameters." });
        }

        const puzzleId = `riddle-${Date.now()}`;

        // Fire off puzzle generation with notification (async)
        generateCustomPuzzlesWithNotification(topic, format, numPuzzles, userId, puzzleId);

        return res.json({
            message: "success",
            jobId: puzzleId,
            clientType: clientType,
            timestamp: clientInfo.timestamp,
            notificationsEnabled: true
        });

    } catch (error) {
        console.error('Error starting puzzle generation:', error.message);
        res.status(500).json({ error: "Failed to start puzzle generation." });
    }
});

export default router;
