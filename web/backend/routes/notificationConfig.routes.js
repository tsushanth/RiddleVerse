// routes/notificationConfig.routes.js - Notification configuration and sending endpoints
import express from 'express';
import { supabase } from '../config/database.js';
import { admin } from '../config/firebaseAdmin.js';
import { notificationLimiter } from '../config/middleware.js';
import { notificationService } from '../services/notificationService.js';
import { sendMulticastNotification, listUserDailyTopics } from '../services/dailyPuzzleService.js';

const router = express.Router();

/**
 * Create notification config
 */
router.post('/configs', notificationLimiter, async (req, res) => {
    try {
        const {
            name,
            message,
            title = 'Puzzle Notification',
            targetTime = '12:00',
            rules = { selectAllUsers: true },
            isActive = true,
            scheduleType = 'daily',
            createdBy = 'system',
            priority = 'normal'
        } = req.body;

        if (!name || !message) {
            return res.status(400).json({
                success: false,
                error: 'Name and message are required'
            });
        }

        const result = await notificationService.createNotificationConfig({
            name,
            message,
            title,
            targetTime,
            rules,
            isActive,
            scheduleType,
            createdBy,
            priority
        });

        if (result.success) {
            res.status(201).json({
                success: true,
                message: result.message,
                config: result.config,
                nextSteps: [
                    'Use POST /api/notifications/send/{configId} to send this notification',
                    'Use GET /api/notifications/configs to view all configurations',
                    'Use PUT /api/notifications/configs/{configId} to update this configuration'
                ]
            });
        } else {
            res.status(400).json({
                success: false,
                error: result.error
            });
        }

    } catch (error) {
        console.error('❌ Error creating notification config:', error);
        res.status(500).json({
            success: false,
            error: error.message
        });
    }
});

/**
 * Get notification stats
 */
router.get('/stats', notificationLimiter, async (req, res) => {
    try {
        const { configId, days = 30 } = req.query;

        const result = await notificationService.getNotificationStats(
            configId ? parseInt(configId) : null,
            parseInt(days)
        );

        if (result.success) {
            res.json({
                success: true,
                stats: result.stats,
                period: result.period,
                insights: {
                    successRate: result.stats.totalNotifications > 0
                        ? Math.round((result.stats.successfulNotifications / result.stats.totalNotifications) * 100)
                        : 0,
                    averagePerDay: Math.round(result.stats.totalNotifications / parseInt(days)),
                    topConfig: Object.entries(result.stats.configBreakdown)
                        .sort(([, a], [, b]) => (b.sent + b.failed) - (a.sent + a.failed))[0]?.[0] || 'None',
                    topTimezone: Object.entries(result.stats.timezoneBreakdown)
                        .sort(([, a], [, b]) => (b.sent + b.failed) - (a.sent + a.failed))[0]?.[0] || 'None'
                }
            });
        } else {
            res.status(500).json({
                success: false,
                error: result.error
            });
        }

    } catch (error) {
        console.error('❌ Error getting notification stats:', error);
        res.status(500).json({
            success: false,
            error: error.message
        });
    }
});

/**
 * Test notification endpoint
 */
router.post('/test-notification', async (req, res) => {
    try {
        const { userEmail, title, body } = req.body;

        if (!userEmail) {
            return res.status(400).json({ error: 'userEmail required' });
        }

        const { data: tokens } = await supabase
            .from('user_notification_tokens')
            .select('fcm_token')
            .eq('user_email', userEmail)
            .eq('is_active', true);

        if (!tokens || tokens.length === 0) {
            return res.json({
                success: false,
                message: 'No active tokens found for user'
            });
        }

        const notificationData = {
            title: title || '🧪 Test Notification',
            body: body || 'This is a test notification from your puzzle app!',
            data: {
                type: 'test',
                userEmail: userEmail,
                sentAt: new Date().toISOString()
            }
        };

        const result = await sendMulticastNotification(
            tokens.map(t => t.fcm_token),
            notificationData
        );

        res.json({
            success: result.success,
            message: `Test notification sent to ${tokens.length} devices`,
            result
        });

    } catch (error) {
        console.error('❌ Error sending test notification:', error);
        res.status(500).json({
            success: false,
            error: error.message
        });
    }
});

/**
 * Send notifications to all users
 */
router.post('/send-notifications', async (req, res) => {
    const { title, body } = req.body;

    if (!title || !body) {
        return res.status(400).json({ error: 'Missing title or body' });
    }

    try {
        if (!admin.apps.length) {
            throw new Error('Firebase Admin not initialized');
        }

        const { data: tokenUsers, error } = await supabase
            .from('user_notification_tokens')
            .select('fcm_token, user_email, platform')
            .eq('is_active', true);

        if (error) {
            console.error('❌ Supabase query error:', error);
            throw error;
        }

        const tokens = tokenUsers
            .map(user => user.fcm_token)
            .filter(token => token && typeof token === 'string' && token.trim().length > 0);

        if (tokens.length === 0) {
            return res.status(200).json({
                success: true,
                message: 'No valid FCM tokens found',
                totalRecords: tokenUsers?.length || 0,
                validTokens: 0
            });
        }

        const message = {
            notification: { title, body },
            tokens
        };

        const response = await admin.messaging().sendMulticast(message);

        if (response.failureCount > 0) {
            console.error('❌ FCM Failures:');
            response.responses.forEach((resp, idx) => {
                if (!resp.success) {
                    console.error(`  Token ${idx}: ${resp.error?.code} - ${resp.error?.message}`);
                }
            });
        }

        try {
            await supabase
                .from('notification_logs')
                .insert({
                    title,
                    message: body,
                    sent_count: response.successCount,
                    failed_count: response.failureCount,
                    total_tokens: tokens.length,
                    created_at: new Date().toISOString()
                });
        } catch (logError) {
            console.error('⚠️ Failed to log notification:', logError);
        }

        res.status(200).json({
            success: true,
            sent: response.successCount,
            failed: response.failureCount,
            totalTokens: tokens.length
        });

    } catch (error) {
        console.error("❌ Error sending notifications:", error);
        res.status(500).json({
            success: false,
            error: error.message
        });
    }
});

/**
 * List user daily topics
 */
router.get('/list-user-daily-topics', async (req, res) => {
    const { email } = req.query;

    if (!email) {
        return res.status(400).json({ error: "Missing email parameter" });
    }

    try {
        const result = await listUserDailyTopics(email);

        if (!result.success) {
            return res.status(500).json({
                error: "Failed to list topics",
                message: result.error
            });
        }

        return res.json({ topics: result.topics });

    } catch (error) {
        console.error("❌ Error listing daily topics:", error);
        return res.status(500).json({ error: "Failed to list user topics" });
    }
});

export default router;
