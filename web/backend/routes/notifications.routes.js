import express from 'express';
import { notificationLimiter } from '../config/middleware.js';
import { notificationService } from '../services/notificationService.js';
import { firebaseStreakNotificationService } from '../services/firebaseStreakNotificationService.js';
import { supabase } from '../config/database.js';

const router = express.Router();

// Create notification configuration
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

        console.log(`Creating notification config: ${name}`);

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
        console.error('Error creating notification config:', error);
        res.status(500).json({
            success: false,
            error: error.message
        });
    }
});

// Send notification by config ID
router.post('/send/:configId', async (req, res) => {
    try {
        const { configId } = req.params;
        const { immediate = false } = req.body;

        console.log(`Sending timed notification for config: ${configId}`);

        // Validate config exists and is active
        const { data: config, error: configError } = await supabase
            .from('notification_configs')
            .select('*')
            .eq('id', configId)
            .eq('is_active', true)
            .single();

        if (configError || !config) {
            return res.status(404).json({
                success: false,
                error: 'Active notification configuration not found'
            });
        }

        if (immediate) {
            // Send immediately to all users regardless of time
            console.log('Sending immediate notification');
            
            const rules = JSON.parse(config.rules);
            const usersResult = await notificationService.getUsersForNotification(rules);
            
            if (!usersResult.success || usersResult.users.length === 0) {
                return res.status(400).json({
                    success: false,
                    error: 'No eligible users found for notification'
                });
            }

            // Send to all users immediately
            const allTokens = [];
            for (const user of usersResult.users) {
                allTokens.push(...user.tokens.map(t => t.fcm_token));
            }

            const notificationData = {
                title: config.title,
                body: config.message,
                data: {
                    type: 'immediate_notification',
                    configId: config.id.toString(),
                    configName: config.name,
                    sentAt: new Date().toISOString()
                }
            };

            const sendResult = await notificationService.sendMulticastNotification(allTokens, notificationData);

            // Log immediate notifications
            for (const user of usersResult.users) {
                await notificationService.logUserNotification(
                    user.email, 
                    config, 
                    sendResult.success, 
                    user.timezone || 'UTC', 
                    sendResult.error
                );
            }

            // Update config stats
            await notificationService.updateNotificationStats(configId, {
                sent: sendResult.success ? usersResult.users.length : 0,
                failed: sendResult.success ? 0 : usersResult.users.length
            });

            res.json({
                success: true,
                message: 'Immediate notification sent',
                configName: config.name,
                results: {
                    totalUsers: usersResult.users.length,
                    totalTokens: allTokens.length,
                    sent: sendResult.success ? usersResult.users.length : 0,
                    failed: sendResult.success ? 0 : usersResult.users.length,
                    fcmResult: sendResult
                }
            });

        } else {
            // Send at local time for each user's timezone
            const result = await notificationService.sendTimedNotification(configId);

            if (result.success) {
                res.json({
                    success: true,
                    message: `Timed notification sent successfully`,
                    jobId: result.jobId,
                    configName: result.configName,
                    results: result.results,
                    summary: {
                        totalUsers: result.results.totalUsers,
                        sent: result.results.sent,
                        failed: result.results.failed,
                        timezones: result.results.processedTimezones
                    }
                });
            } else {
                res.status(500).json({
                    success: false,
                    message: 'Timed notification failed',
                    jobId: result.jobId,
                    error: result.error
                });
            }
        }

    } catch (error) {
        console.error('Error sending notification:', error);
        res.status(500).json({
            success: false,
            error: error.message
        });
    }
});

// Get notification statistics
router.get('/stats', notificationLimiter, async (req, res) => {
    try {
        const { configId, days = 30 } = req.query;

        console.log(`Getting notification stats${configId ? ` for config ${configId}` : ' (all configs)'}`);

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
                        .sort(([,a], [,b]) => (b.sent + b.failed) - (a.sent + a.failed))[0]?.[0] || 'None',
                    topTimezone: Object.entries(result.stats.timezoneBreakdown)
                        .sort(([,a], [,b]) => (b.sent + b.failed) - (a.sent + a.failed))[0]?.[0] || 'None'
                }
            });
        } else {
            res.status(500).json({
                success: false,
                error: result.error
            });
        }

    } catch (error) {
        console.error('Error getting notification stats:', error);
        res.status(500).json({
            success: false,
            error: error.message
        });
    }
});

// Streak notification management routes
router.get('/streak-notifications/status', async (req, res) => {
    try {
        const status = firebaseStreakNotificationService.getServiceStatus();
        
        // Also get recent jobs from database
        const { data: recentJobs, error } = await supabase
            .from('streak_notification_jobs')
            .select('*')
            .order('started_at', { ascending: false })
            .limit(10);
            
        if (error) {
            console.warn('Could not fetch recent jobs from database:', error);
        }
        
        res.json({
            success: true,
            serviceStatus: status,
            recentJobsFromDB: recentJobs || [],
            timestamp: new Date().toISOString()
        });
        
    } catch (error) {
        console.error('Error getting streak service status:', error);
        res.status(500).json({
            success: false,
            error: error.message
        });
    }
});

// Manually trigger streak check
router.post('/streak-notifications/run-now', async (req, res) => {
    try {
        if (firebaseStreakNotificationService.isRunning) {
            return res.status(409).json({
                success: false,
                error: 'Streak notification check is already running'
            });
        }
        
        console.log('Manually triggering streak notification check...');
        
        // Run the check (this will happen async)
        firebaseStreakNotificationService.runPeriodicCheck();
        
        res.json({
            success: true,
            message: 'Streak notification check started',
            timestamp: new Date().toISOString()
        });
        
    } catch (error) {
        console.error('Error manually triggering streak check:', error);
        res.status(500).json({
            success: false,
            error: error.message
        });
    }
});

// Stop streak notification service
router.post('/streak-notifications/stop', async (req, res) => {
    try {
        firebaseStreakNotificationService.stopPeriodicService();
        
        res.json({
            success: true,
            message: 'Streak notification service stopped',
            timestamp: new Date().toISOString()
        });
        
    } catch (error) {
        console.error('Error stopping streak service:', error);
        res.status(500).json({
            success: false,
            error: error.message
        });
    }
});

// Start streak notification service
router.post('/streak-notifications/start', async (req, res) => {
    try {
        const { intervalMinutes = 60 } = req.body;
        
        firebaseStreakNotificationService.startPeriodicService(intervalMinutes);
        
        res.json({
            success: true,
            message: `Streak notification service started (${intervalMinutes} minute intervals)`,
            timestamp: new Date().toISOString()
        });
        
    } catch (error) {
        console.error('Error starting streak service:', error);
        res.status(500).json({
            success: false,
            error: error.message
        });
    }
});

// Test specific user's streak status
router.post('/streak-notifications/test-user', async (req, res) => {
    try {
        const { userEmail } = req.body;
        
        if (!userEmail) {
            return res.status(400).json({
                success: false,
                error: 'userEmail is required'
            });
        }
        
        const results = {
            totalUsers: 1,
            atRiskUsers: 0,
            comebackUsers: 0,
            celebrationUsers: 0,
            notificationsSent: 0,
            errors: []
        };
        
        await firebaseStreakNotificationService.checkUserStreakStatus(userEmail, results, `test_${Date.now()}`);
        
        res.json({
            success: true,
            message: `Tested streak notifications for ${userEmail}`,
            results,
            timestamp: new Date().toISOString()
        });
        
    } catch (error) {
        console.error('Error testing user streak:', error);
        res.status(500).json({
            success: false,
            error: error.message
        });
    }
});

export default router;